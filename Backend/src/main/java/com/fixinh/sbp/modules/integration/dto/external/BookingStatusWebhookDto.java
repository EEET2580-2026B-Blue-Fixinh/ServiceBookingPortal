package com.fixinh.sbp.modules.integration.dto.external;

import java.time.Instant;
import java.util.List;

import com.fixinh.sbp.modules.booking.model.TechnicianRef;

/**
 * §11.7's request body, exactly as SMP sends it.
 *
 * <p>Every field is nullable and nothing is validated here, on purpose. §11.7 answers a
 * missing required field with {@code 400 EVENT_MALFORMED} and a specific message, and that is
 * only possible if the payload can be deserialised first — bean validation or a throwing
 * constructor would turn the same fault into a generic 400 that tells SMP nothing about which
 * field to fix. {@code BookingEventValidator} is where the rules live.
 *
 * @param eventId unique per event; what SBP recognises a replay by
 * @param eventType one of §11.7's four values, as text. Recorded rather than judged: SBP
 *        reacts to the payload's status, so an event type it has no special handling for is
 *        still a legitimate projection refresh
 * @param occurredAt when the change happened in SMP
 * @param bookingRef which booking changed
 * @param customerId whose booking it is — the routing key for both the store and the recipient
 * @param payload the changed booking, in §12.3's shape
 */
public record BookingStatusWebhookDto(
		String eventId,
		String eventType,
		Instant occurredAt,
		String bookingRef,
		String customerId,
		BookingPayload payload) {

	/**
	 * The subset of §12.3 that SMP sends on a change and SBP keeps.
	 *
	 * <p>§12.3's full shape is larger — it repeats what the customer wrote and where the work
	 * happens. Those are omitted because SBP authored them and SMP never rewrites them, so
	 * accepting them here would invite an event that silently rewrote a customer's own words.
	 *
	 * @param status the only field §11.7's example never omits, and the only one required
	 * @param totalCost SMP's own total. Kept so it can be compared against SBP's own sum
	 *        (§12.3 asks SBP to recompute rather than trust), never stored as sent
	 */
	public record BookingPayload(
			String status,
			Instant scheduledStartAt,
			Integer estimatedDurationMinutes,
			List<TechnicianRef> technicians,
			Long serviceFee,
			Long materialCost,
			Long totalCost,
			String billImageUrl,
			Instant billIssuedAt) {
	}
}