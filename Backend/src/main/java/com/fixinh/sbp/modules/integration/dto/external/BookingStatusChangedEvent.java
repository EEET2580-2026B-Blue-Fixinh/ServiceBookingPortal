package com.fixinh.sbp.modules.integration.dto.external;

import java.time.Instant;

import com.fixinh.sbp.modules.booking.model.BookingStatus;

/**
 * A booking's status changed in SMP, and SBP's projection of it has been refreshed.
 *
 * <p>Published by the integration module once it has accepted a {@code POST
 * /integration/booking-events} (§11.7) and written the change into the local booking
 * projection. It is the in-process half of Req 4.M3: the endpoint receives, this event fans
 * out, and the notification module turns it into the real-time message of Req 3.3.U2.
 *
 * <p><b>Ordering is part of the contract, not a detail of it.</b> §11.7 and §9.3 both promise
 * that the projection is refreshed before the customer is told, because Req 3.3.S5 needs the
 * technician's name on the screen a scheduled-booking toast leads to. That holds only while
 * two things stay true:
 * <ul>
 *   <li>the publisher raises this <em>after</em> the projection write has succeeded — never
 *       before it, and never for a write that failed;</li>
 *   <li>listeners run synchronously in the publisher's thread, which is Spring's default.
 *       No listener of this event is {@code @Async}, and the application's event
 *       multicaster is not given an executor.</li>
 * </ul>
 *
 * <p>A listener that throws therefore fails the inbound request, and SMP retries with the
 * same {@code eventId}. That is the intended recovery path, and it has two consequences: the
 * publisher must not record the event as handled until every listener has returned, and each
 * listener must make its own work idempotent on {@code eventId} — a replay is a promise that
 * the listeners run again, not that they ran once.
 *
 * <p>Every status change is published, not only the two that produce a notification. Which
 * statuses matter is each consumer's decision, so a consumer interested in a third status
 * later does not need this contract to change.
 *
 * @param eventId SMP's identifier for the change, unique per event (§11.7). What every
 *        consumer keys its idempotency on
 * @param bookingRef the booking that changed, as the customer knows it
 * @param customerId the booking's owner, and the recipient of anything raised from this.
 *        Also what resolves the regional store: SMP names a customer, never a shard
 *        (Req 4.U1)
 * @param status the booking's status after the change — one of Appendix A's eight values
 * @param scheduledStartAt when the technicians will arrive. Present once SMP has scheduled
 *        the job; null for any status before that
 * @param estimatedDurationMinutes how long the visit is expected to take. Set alongside
 *        {@code scheduledStartAt}, and null whenever that is
 * @param occurredAt when the change happened in SMP, not when SBP received it
 */
public record BookingStatusChangedEvent(
		String eventId,
		String bookingRef,
		String customerId,
		BookingStatus status,
		Instant scheduledStartAt,
		Integer estimatedDurationMinutes,
		Instant occurredAt) {

	/**
	 * The four fields §11.7 marks required are required here too. The endpoint answers a
	 * missing one with 400 {@code EVENT_MALFORMED}; this check is what stops an event that
	 * somehow got past it from reaching a listener that would then have nothing to route,
	 * nothing to refresh and nobody to tell.
	 */
	public BookingStatusChangedEvent {
		requireText(eventId, "eventId");
		requireText(bookingRef, "bookingRef");
		requireText(customerId, "customerId");
		if (status == null) {
			throw new IllegalArgumentException(
					"A booking status change event needs the status the booking changed to.");
		}
		if (occurredAt == null) {
			throw new IllegalArgumentException(
					"A booking status change event needs the time the change happened in SMP.");
		}
	}

	private static void requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					"A booking status change event needs a " + field + ".");
		}
	}
}
