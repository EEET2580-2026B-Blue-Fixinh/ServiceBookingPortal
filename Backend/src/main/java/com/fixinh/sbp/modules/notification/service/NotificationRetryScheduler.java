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

		for (Notification notification : outstanding) {
			if (notification.getAttemptCount() >= retry.getMaxAttempts()) {
				giveUp(notification);
				abandoned++;
			}
			else if (isDue(notification, now)) {
				attemptAgain(notification, now);
				attempted++;
			}
			// Otherwise it is still inside its backoff window. Left alone, picked up later.
		}

		if (attempted > 0 || abandoned > 0) {
			log.info("Retry sweep: {} outstanding, {} attempted, {} abandoned",
					outstanding.size(), attempted, abandoned);
		}
	}

	/**
	 * Whether enough time has passed since the last try.
	 *
	 * <p>A notification with no {@code lastAttemptAt} has never been attempted at all — the
	 * process died between writing the row and calling the provider. Those are due immediately,
	 * which is the entire reason the row is written first.
	 */
	private boolean isDue(Notification notification, Instant now) {
		Instant lastAttempt = notification.getLastAttemptAt();
		if (lastAttempt == null) {
			return true;
		}
		return !now.isBefore(lastAttempt.plus(backoffAfter(notification.getAttemptCount())));
	}

	/** Base delay, doubled once per attempt already made, and capped. */
	private Duration backoffAfter(int attemptCount) {
		int doublings = Math.min(Math.max(attemptCount - 1, 0), MAX_DOUBLINGS);
		return retry.getBaseDelay().multipliedBy(1L << doublings);
	}

	private void attemptAgain(Notification notification, Instant now) {
		String body = notification.getUndeliveredBody();
		if (body == null || notification.getRecipientAddress() == null) {
			// Nothing left to resend. Should not happen -- recordSuccess and abandon are the
			// only things that clear the body, and both are terminal -- but a row that can
			// never be sent must not be reconsidered on every sweep forever.
			log.error("Notification {} is outstanding but has no message left to send; "
					+ "abandoning it", notification.getId());
			giveUp(notification);
			return;
		}

		EmailMessage message = new EmailMessage(notification.getRecipientAddress(),
				composer.subjectFor(notification.getType()), body);

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

	private void giveUp(Notification notification) {
		log.error("Abandoning notification {} to {} - it is already past the {}-attempt cap",
				notification.getId(), notification.getRecipientAddress(), retry.getMaxAttempts());
		notification.abandon();
		notificationRepository.save(notification);
	}
}