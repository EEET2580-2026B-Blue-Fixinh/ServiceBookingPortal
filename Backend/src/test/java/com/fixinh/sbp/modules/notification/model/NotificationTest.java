package com.fixinh.sbp.modules.notification.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * The delivery lifecycle, tested as plain objects. No Spring, no Mongo — these transitions are
 * pure state changes, and that is exactly why they live on the model rather than being spread
 * across a service where they could only be tested through a database.
 */
class NotificationTest {

	private static final Instant WHEN = Instant.parse("2026-08-19T04:00:00Z");

	private Notification pendingEmail() {
		return Notification.builder()
				.recipientType(RecipientType.STAFF)
				.recipientIdentifier("STF-001")
				.recipientAddress("david.nguyen@fixinh.vn")
				.type(NotificationType.STAFF_ACCOUNT_CREATED)
				.deliveryChannel(DeliveryChannel.EMAIL)
				.message("Welcome email sent to david.nguyen@fixinh.vn")
				.undeliveredBody("Your temporary password is ...")
				.createdAt(WHEN)
				.build();
	}

	@Test
	void startsPendingWithNoAttempts() {
		Notification notification = pendingEmail();

		assertEquals(DeliveryStatus.PENDING, notification.getDeliveryStatus(),
				"@Builder.Default should apply when the caller does not set a status");
		assertEquals(0, notification.getAttemptCount());
		assertNull(notification.getSentAt());
		assertTrue(notification.isAwaitingDelivery());
	}

	@Test
	void successStampsTheSendAndDiscardsTheBody() {
		Notification notification = pendingEmail();

		notification.recordSuccess(WHEN);

		assertEquals(DeliveryStatus.SENT, notification.getDeliveryStatus());
		assertEquals(WHEN, notification.getSentAt());
		assertEquals(WHEN, notification.getLastAttemptAt());
		assertEquals(1, notification.getAttemptCount());
		assertFalse(notification.isAwaitingDelivery());

		// The security rule this whole design exists for: the initial password must not
		// outlive the delivery it was needed for.
		assertNull(notification.getUndeliveredBody(),
				"the body carries an initial password and must not survive a successful send");
	}

	@Test
	void failureKeepsTheBodySoARetryCanResendIt() {
		Notification notification = pendingEmail();

		notification.recordFailedAttempt(WHEN);

		assertEquals(DeliveryStatus.FAILED, notification.getDeliveryStatus());
		assertEquals(1, notification.getAttemptCount());
		assertEquals(WHEN, notification.getLastAttemptAt());
		assertNull(notification.getSentAt(), "a failed attempt never sent anything");
		assertTrue(notification.isAwaitingDelivery(), "a failure is still work to do");
		assertTrue(notification.getUndeliveredBody() != null,
				"the retry has to resend the same letter, so the letter must survive");
	}

	@Test
	void attemptsAccumulateAcrossFailures() {
		Notification notification = pendingEmail();

		notification.recordFailedAttempt(WHEN);
		notification.recordFailedAttempt(WHEN.plusSeconds(60));
		notification.recordFailedAttempt(WHEN.plusSeconds(180));

		assertEquals(3, notification.getAttemptCount(), "the retry cap depends on this counting");
		assertEquals(WHEN.plusSeconds(180), notification.getLastAttemptAt());
	}

	@Test
	void aFailureFollowedBySuccessEndsClean() {
		Notification notification = pendingEmail();

		notification.recordFailedAttempt(WHEN);
		notification.recordSuccess(WHEN.plusSeconds(300));

		assertEquals(DeliveryStatus.SENT, notification.getDeliveryStatus());
		assertEquals(2, notification.getAttemptCount(), "the earlier failure still counted");
		assertEquals(WHEN.plusSeconds(300), notification.getSentAt());
		assertNull(notification.getUndeliveredBody());
	}

	@Test
	void abandoningIsTerminalAndAlsoDropsTheBody() {
		Notification notification = pendingEmail();

		notification.recordFailedAttempt(WHEN);
		notification.abandon();

		assertEquals(DeliveryStatus.ABANDONED, notification.getDeliveryStatus());
		assertFalse(notification.isAwaitingDelivery(), "nothing more will be attempted");
		assertNull(notification.getUndeliveredBody(),
				"a password we have given up sending is a password with no reason to exist");
		assertNull(notification.getSentAt());
	}
}