package com.fixinh.sbp.modules.notification.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.email.EmailClient;
import com.fixinh.sbp.common.email.EmailDeliveryException;
import com.fixinh.sbp.common.email.EmailMessage;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.notification.repository.NotificationRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Tries again, later, for emails a provider would not take.
 */
@Component
@ConditionalOnProperty(prefix = "fixinh.sbp.email.retry", name = "enabled",
		havingValue = "true", matchIfMissing = true)
@Slf4j
public class NotificationRetryScheduler {

	/** Beyond this many doublings the wait stops growing, so the schedule stays predictable. */
	private static final int MAX_DOUBLINGS = 6;

	private final NotificationRepository notificationRepository;
	private final StaffWelcomeEmailComposer composer;
	private final EmailClient emailClient;
	private final SbpProperties.Email.Retry retry;

	public NotificationRetryScheduler(NotificationRepository notificationRepository,
			StaffWelcomeEmailComposer composer, EmailClient emailClient,
			SbpProperties properties) {

		this.notificationRepository = notificationRepository;
		this.composer = composer;
		this.emailClient = emailClient;
		this.retry = properties.getEmail().getRetry();
	}

	/**
	 * One pass over what is still outstanding.
	 *
	 * <p>{@code fixedDelay} rather than {@code fixedRate}: the gap is measured from the end of
	 * the last sweep, so a slow provider cannot cause sweeps to pile up on each other.
	 *
	 * <p>The initial delay keeps this away from startup. Nothing is urgent in the first half
	 * minute, and a sweep firing while the application is still wiring itself together only
	 * makes the logs harder to read — and every {@code @SpringBootTest} slower.
	 *
	 * <p><b>Each row stands alone.</b> A fault while handling one notification — most likely
	 * its store refusing the save — is logged and the sweep moves on, because every row after
	 * it is still owed an attempt. The failed row is left exactly as it was, so the next sweep
	 * finds it again: the right answer to a transient fault. If its send had already gone
	 * through when the save failed, that next sweep sends it a second time; at-least-once is
	 * the price of never silently losing a welcome email.
	 *
	 * <p>A store that cannot be read at all is handled one level down, by the repository, which
	 * returns the other store's rows. Only when neither answers does this throw, and Spring's
	 * scheduler logs it and runs the next sweep on time.
	 */
	@Scheduled(
			fixedDelayString = "${fixinh.sbp.email.retry.sweep-interval:PT1M}",
			initialDelayString = "${fixinh.sbp.email.retry.initial-delay:PT30S}")
	public void retryOutstandingEmails() {
		List<Notification> outstanding =
				notificationRepository.findOutstandingEmails(retry.getBatchSize());
		if (outstanding.isEmpty()) {
			return;
		}

		Instant now = Instant.now();
		int attempted = 0;
		int abandoned = 0;
		int failed = 0;

		for (Notification notification : outstanding) {
			try {
				if (notification.getAttemptCount() >= retry.getMaxAttempts()) {
					giveUp(notification, "it has already had all " + retry.getMaxAttempts()
							+ " attempts");
					abandoned++;
				}
				else if (isDue(notification, now)) {
					attemptAgain(notification, now);
					attempted++;
				}
				// Otherwise it is still inside its backoff window. Left alone, picked up later.
			}
			catch (RuntimeException ex) {
				failed++;
				log.error("Retry sweep could not finish notification {}; it stays outstanding "
						+ "for the next sweep", notification.getId(), ex);
			}
		}

		if (attempted > 0 || abandoned > 0 || failed > 0) {
			log.info("Retry sweep: {} outstanding, {} attempted, {} abandoned, {} failed",
					outstanding.size(), attempted, abandoned, failed);
		}
	}

	/**
	 * Whether enough time has passed since the last try.
	 *
	 * <p><b>A row with no {@code lastAttemptAt} is one of two things, and the store cannot
	 * tell them apart.</b> Either the process died between writing the row and calling the
	 * provider, or the request that wrote it is calling the provider right now. Resending the
	 * first is the reason the row is written before the send. Resending the second delivers
	 * the welcome email twice — and whichever thread saves last wins, so a stale
	 * {@code FAILED} can overwrite this sweep's {@code SENT} and queue a third.
	 *
	 * <p>Age separates them. The request's own send is bounded by the SMTP timeouts under
	 * {@code spring.mail.properties} and records its outcome long before one base delay has
	 * passed. So a never-attempted row younger than that is left to the thread still working
	 * on it, and one older than that was left behind by a crash and is due. That keeps the
	 * sweeper off rows it did not write, without a lock or a claim field.
	 *
	 * <p>A row with no {@code createdAt} was not written by the send path, which always stamps
	 * one, so nothing can be mid-send on it. It is due.
	 */
	private boolean isDue(Notification notification, Instant now) {
		Instant lastAttempt = notification.getLastAttemptAt();
		if (lastAttempt == null) {
			Instant created = notification.getCreatedAt();
			return created == null || !now.isBefore(created.plus(retry.getBaseDelay()));
		}
		return !now.isBefore(lastAttempt.plus(backoffAfter(notification.getAttemptCount())));
	}

	/** Base delay, doubled once per attempt already made, and capped. */
	private Duration backoffAfter(int attemptCount) {
		int doublings = Math.min(Math.max(attemptCount - 1, 0), MAX_DOUBLINGS);
		return retry.getBaseDelay().multipliedBy(1L << doublings);
	}

	private void attemptAgain(Notification notification, Instant now) {
		EmailMessage message;
		try {
			message = rebuildMessage(notification);
		}
		catch (IllegalArgumentException | IllegalStateException unsendable) {
			// Nothing a later sweep could change: the stored row itself cannot become an email.
			// Ending it here, rather than letting the sweep's own catch leave it outstanding,
			// is what stops a broken row failing again every minute forever.
			giveUp(notification, unsendable.getMessage());
			return;
		}

		try {
			emailClient.send(message);
			notification.recordSuccess(now);
			log.info("Retry succeeded for notification {} to {} on attempt {}",
					notification.getId(), notification.getRecipientAddress(),
					notification.getAttemptCount());
		}
		catch (EmailDeliveryException ex) {
			notification.recordFailedAttempt(now);

			if (notification.getAttemptCount() >= retry.getMaxAttempts()) {
				notification.abandon();
				// Loud, because this is the end of the line: the staff account exists and its
				// owner has never been told how to sign in. Somebody has to do something.
				log.error("Giving up on notification {} to {} after {} attempts. The account "
						+ "exists but its owner has not received their sign-in details",
						notification.getId(), notification.getRecipientAddress(),
						notification.getAttemptCount());
			}
			else {
				// Recipient and count only -- the body holds an initial password.
				log.warn("Retry {} of {} failed for notification {} to {}",
						notification.getAttemptCount(), retry.getMaxAttempts(),
						notification.getId(), notification.getRecipientAddress());
			}
		}

		notificationRepository.save(notification);
	}

	/**
	 * The message a row still owes, rebuilt from what was stored with it.
	 *
	 * <p>The subject is derived from the type rather than stored — see
	 * {@link StaffWelcomeEmailComposer#subjectFor} — so only the address and the body had to
	 * survive the failed attempt.
	 *
	 * @throws IllegalStateException when there is nothing left to rebuild from. Should not
	 *         happen: {@code recordSuccess} and {@code abandon} are the only things that clear
	 *         the body, and both are terminal
	 * @throws IllegalArgumentException when what is left cannot form an email — a blank
	 *         address, or a type that is never emailed. {@code EmailMessage} and the composer
	 *         raise these, and their messages name the field, never the body
	 */
	private EmailMessage rebuildMessage(Notification notification) {
		if (notification.getUndeliveredBody() == null) {
			throw new IllegalStateException("it is outstanding but has no message left to send");
		}
		if (notification.getType() == null) {
			throw new IllegalStateException("it has no type, so there is no subject to send under");
		}
		return new EmailMessage(notification.getRecipientAddress(),
				composer.subjectFor(notification.getType()), notification.getUndeliveredBody());
	}

	/**
	 * Ends a row for good. Loud, because every abandoned welcome email is a staff account
	 * whose owner was never told how to sign in, and a person has to act on it.
	 *
	 * @param reason why, for the log. Never includes the body — it holds an initial password
	 */
	private void giveUp(Notification notification, String reason) {
		log.error("Abandoning notification {} to {}: {}", notification.getId(),
				notification.getRecipientAddress(), reason);
		notification.abandon();
		notificationRepository.save(notification);
	}
}