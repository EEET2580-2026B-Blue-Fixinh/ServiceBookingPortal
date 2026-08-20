package com.fixinh.sbp.modules.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.email.EmailClient;
import com.fixinh.sbp.common.email.EmailDeliveryException;
import com.fixinh.sbp.common.email.EmailMessage;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.notification.model.DeliveryChannel;
import com.fixinh.sbp.modules.notification.model.DeliveryStatus;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.notification.model.NotificationType;
import com.fixinh.sbp.modules.notification.model.RecipientType;
import com.fixinh.sbp.modules.notification.repository.NotificationRepository;

/**
 * The sweeper, with the clock supplied by the fixtures rather than mocked.
 *
 * <p>Each notification is built with a {@code lastAttemptAt} far enough in the past — or not —
 * to put it either side of its backoff window, so "is this due yet" is exercised against real
 * arithmetic instead of a frozen clock.
 */
class NotificationRetrySchedulerTest {

	private static final Duration BASE = Duration.ofMinutes(2);
	private static final int MAX_ATTEMPTS = 5;

	private NotificationRepository repository;
	private EmailClient emailClient;
	private NotificationRetryScheduler scheduler;

	@BeforeEach
	void setUp() {
		repository = mock(NotificationRepository.class);
		emailClient = mock(EmailClient.class);

		SbpProperties properties = new SbpProperties();
		properties.getEmail().setStaffSignInUrl("https://smp.fixinh.vn/login");
		properties.getEmail().getRetry().setBaseDelay(BASE);
		properties.getEmail().getRetry().setMaxAttempts(MAX_ATTEMPTS);
		properties.getEmail().getRetry().setBatchSize(50);

		scheduler = new NotificationRetryScheduler(repository,
				new StaffWelcomeEmailComposer(properties), emailClient, properties);
	}

	/** A failed email whose last attempt was {@code minutesAgo} minutes ago. */
	private Notification failed(int attemptCount, long minutesAgo) {
		return Notification.builder()
				.id("NOTIF-" + attemptCount)
				.recipientType(RecipientType.STAFF)
				.recipientIdentifier("STF-001")
				.recipientAddress("tuan@fixinh.vn")
				.type(NotificationType.STAFF_ACCOUNT_CREATED)
				.deliveryChannel(DeliveryChannel.EMAIL)
				.message("Welcome email for Nguyen Minh Tuan (TECHNICIAN) to tuan@fixinh.vn")
				.deliveryStatus(DeliveryStatus.FAILED)
				.attemptCount(attemptCount)
				.lastAttemptAt(Instant.now().minus(Duration.ofMinutes(minutesAgo)))
				.undeliveredBody("Hello ... Password: Tmp!Passw0rd ...")
				.homeRegion(Region.SOUTH)
				.build();
	}

	@Test
	void resendsOnceTheBackoffHasElapsed() {
		// One attempt made, so the wait is the 2-minute base. Three minutes have passed.
		Notification due = failed(1, 3);
		when(repository.findOutstandingEmails(anyInt())).thenReturn(List.of(due));

		scheduler.retryOutstandingEmails();

		verify(emailClient).send(any(EmailMessage.class));
		assertEquals(DeliveryStatus.SENT, due.getDeliveryStatus());
		assertEquals(2, due.getAttemptCount(), "the retry counts as an attempt");
		assertNull(due.getUndeliveredBody(), "success discards the body that held the password");
		verify(repository).save(due);
	}

	@Test
	void leavesAlonePrizesStillInsideTheirBackoffWindow() {
		// Two attempts made, so the wait has doubled to 4 minutes. Only one has passed.
		Notification notYet = failed(2, 1);
		when(repository.findOutstandingEmails(anyInt())).thenReturn(List.of(notYet));

		scheduler.retryOutstandingEmails();

		verify(emailClient, never()).send(any(EmailMessage.class));
		verify(repository, never()).save(any());
		assertEquals(DeliveryStatus.FAILED, notYet.getDeliveryStatus());
	}

	@Test
	void backoffDoublesSoAnOlderFailureWaitsLonger() {
		// Three attempts made: the wait is 2 x 2 x 2 = 8 minutes, and 5 have passed.
		Notification notYet = failed(3, 5);
		when(repository.findOutstandingEmails(anyInt())).thenReturn(List.of(notYet));

		scheduler.retryOutstandingEmails();

		verify(emailClient, never()).send(any(EmailMessage.class));

		// The same row nine minutes later is past the window.
		Notification due = failed(3, 9);
		when(repository.findOutstandingEmails(anyInt())).thenReturn(List.of(due));

		scheduler.retryOutstandingEmails();

		verify(emailClient).send(any(EmailMessage.class));
	}

	@Test
	void anotherFailureJustIncrementsWhenAttemptsRemain() {
		doThrow(new EmailDeliveryException("still refusing"))
				.when(emailClient).send(any(EmailMessage.class));
		Notification due = failed(1, 3);
		when(repository.findOutstandingEmails(anyInt())).thenReturn(List.of(due));

		scheduler.retryOutstandingEmails();

		assertEquals(DeliveryStatus.FAILED, due.getDeliveryStatus());
		assertEquals(2, due.getAttemptCount());
		assertTrue(due.getUndeliveredBody() != null, "still owed, so the letter is still kept");
	}

	@Test
	void abandonsOnTheFailureThatReachesTheCap() {
		doThrow(new EmailDeliveryException("still refusing"))
				.when(emailClient).send(any(EmailMessage.class));
		// Four attempts made; the fifth is the last the cap allows.
		Notification lastChance = failed(MAX_ATTEMPTS - 1, 60);
		when(repository.findOutstandingEmails(anyInt())).thenReturn(List.of(lastChance));

		scheduler.retryOutstandingEmails();

		assertEquals(DeliveryStatus.ABANDONED, lastChance.getDeliveryStatus());
		assertEquals(MAX_ATTEMPTS, lastChance.getAttemptCount());
		assertNull(lastChance.getUndeliveredBody(),
				"a password we have stopped trying to send has no reason to remain");
	}

	@Test
	void marksRowsThatAreAlreadyPastTheCapWithoutRetryingThem() {
		// The repository deliberately does not filter on the cap, so exhausted rows arrive here
		// to be marked. Without this they would sit at FAILED and nobody would ever be told.
		Notification exhausted = failed(MAX_ATTEMPTS, 120);
		when(repository.findOutstandingEmails(anyInt())).thenReturn(List.of(exhausted));

		scheduler.retryOutstandingEmails();

		verify(emailClient, never()).send(any(EmailMessage.class));
		assertEquals(DeliveryStatus.ABANDONED, exhausted.getDeliveryStatus());
		verify(repository).save(exhausted);
	}

	@Test
	void sendsImmediatelyForARowThatWasNeverAttempted() {
		// A crash between the first save and the provider call leaves PENDING with no
		// lastAttemptAt. Writing the row first is only worth doing if this case works.
		Notification neverTried = failed(0, 0);
		neverTried.setDeliveryStatus(DeliveryStatus.PENDING);
		neverTried.setLastAttemptAt(null);
		when(repository.findOutstandingEmails(anyInt())).thenReturn(List.of(neverTried));

		scheduler.retryOutstandingEmails();

		verify(emailClient).send(any(EmailMessage.class));
		assertEquals(DeliveryStatus.SENT, neverTried.getDeliveryStatus());
		assertEquals(1, neverTried.getAttemptCount());
	}

	@Test
	void abandonsARowWithNothingLeftToSendRatherThanLoopingOnIt() {
		Notification bodyless = failed(1, 60);
		bodyless.setUndeliveredBody(null);
		when(repository.findOutstandingEmails(anyInt())).thenReturn(List.of(bodyless));

		scheduler.retryOutstandingEmails();

		verify(emailClient, never()).send(any(EmailMessage.class));
		assertEquals(DeliveryStatus.ABANDONED, bodyless.getDeliveryStatus());
	}

	@Test
	void doesNothingWhenThereIsNothingOutstanding() {
		when(repository.findOutstandingEmails(anyInt())).thenReturn(List.of());

		scheduler.retryOutstandingEmails();

		verify(emailClient, never()).send(any(EmailMessage.class));
		verify(repository, never()).save(any());
	}
}