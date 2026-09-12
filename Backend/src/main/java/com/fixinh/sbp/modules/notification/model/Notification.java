package com.fixinh.sbp.modules.notification.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fixinh.sbp.common.sharding.Region;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

	/** Mongo's {@code _id}, published by §9.1 as {@code notificationId}. */
	@Id
	private String id;

	private RecipientType recipientType;

	/**
	 * The customer id, or SMP's staff id. For STAFF this points at a record SBP does not hold
	 * and cannot join to — deliberately, since staff live in SMP.
	 */
	private String recipientIdentifier;

	/**
	 * Where an {@code EMAIL} goes. Null for {@code IN_APP}.
	 *
	 * <p>Kept after a successful send, unlike {@link #undeliveredBody}: knowing which address
	 * was written to is the audit trail, and a work email is not a secret.
	 */
	private String recipientAddress;

	private NotificationType type;

	private DeliveryChannel deliveryChannel;

	/**
	 * The human-readable line, and what §9.1 returns as {@code message}.
	 *
	 * <p><b>For an email this is a summary, not the body.</b> "Welcome email sent to
	 * david.nguyen@fixinh.vn" — not the letter containing the initial password. The body is a
	 * separate field with a separate lifetime, below.
	 */
	private String message;

	/**
	 * Present whenever the notification concerns a booking (§9.1), absent otherwise —
	 * {@code STAFF_ACCOUNT_CREATED} has no booking.
	 */
	private String bookingRef;

	/**
	 * The SMP event this was raised from — §11.7's {@code eventId} — or null for a
	 * notification raised by anything else: an administrator's action, the staff welcome
	 * email.
	 *
	 * <p>What makes a replay harmless. SMP resends an event it got no 2xx for, and the same
	 * {@code eventId} arriving twice must produce one notification, not two. The service asks
	 * for this before it writes, and a sparse unique index on it in each store closes the
	 * window between the ask and the write. Sparse, so the nulls do not collide with one
	 * another — a unique index that counted nulls would allow exactly one notification that
	 * came from no event.
	 */
	private String sourceEventId;

	/** §9.1 publishes this as {@code readStatus}. Meaningless for email; left false. */
	private boolean readStatus;

	private Instant createdAt;

	@Builder.Default
	private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

	/** When the provider accepted it. Null until then, and null forever if it never does. */
	private Instant sentAt;

	/** How many times we have handed this to a provider. Drives the retry cap. */
	private int attemptCount;

	/** When we last tried, successfully or not. What the sweeper spaces retries against. */
	private Instant lastAttemptAt;

	/**
	 * The composed email body, held <b>only while delivery is still outstanding</b>.
	 *
	 * <p>A retry has to resend the same letter, so the letter has to survive the failure. But
	 * that letter contains the staff member's initial password, and a plaintext password
	 * sitting in Mongo forever is a worse outcome than the one the retry was protecting
	 * against. So it is cleared the moment the message is accepted — or abandoned — by
	 * {@link #recordSuccess} and {@link #abandon}. Exposure is bounded by the retry window
	 * instead of being permanent.
	 *
	 * <p>This is the field that would disappear entirely if SMT agrees to return a single-use
	 * activation link instead of a password 
	 */
	private String undeliveredBody;

	/**
	 * Which store owns this row. Set from the recipient's region before the first save;
	 * {@code ShardedNotificationRepository} refuses to guess.
	 */
	private Region homeRegion;

	// --- Lifecycle -------------------------------------------------------------------------
	// State changes live here rather than as loose setter calls in the service, so the rules
	// that must hold together -- bump the count, stamp the time, drop the body -- cannot be
	// half-applied by a caller who forgets one.

	/** The provider accepted the message. Records the send and discards the sensitive body. */
	public void recordSuccess(Instant when) {
		this.attemptCount++;
		this.lastAttemptAt = when;
		this.sentAt = when;
		this.deliveryStatus = DeliveryStatus.SENT;
		this.undeliveredBody = null;
	}

	/**
	 * An attempt failed. Stays {@link DeliveryStatus#FAILED} and keeps the body, because the
	 * sweeper will come back for it. Whether that has happened too often is the sweeper's
	 * judgement, not this object's — the cap is policy and lives in configuration.
	 */
	public void recordFailedAttempt(Instant when) {
		this.attemptCount++;
		this.lastAttemptAt = when;
		this.deliveryStatus = DeliveryStatus.FAILED;
	}

	/** No more retries. Terminal, and the body goes with it. */
	public void abandon() {
		this.deliveryStatus = DeliveryStatus.ABANDONED;
		this.undeliveredBody = null;
	}

	/** True while a further attempt is still worth making. */
	public boolean isAwaitingDelivery() {
		return deliveryStatus == DeliveryStatus.PENDING || deliveryStatus == DeliveryStatus.FAILED;
	}
}