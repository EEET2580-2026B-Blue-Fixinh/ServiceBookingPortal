package com.fixinh.sbp.modules.booking.interfaces.internal;

import java.time.Instant;
import java.util.List;

import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.booking.model.TechnicianRef;

/**
 * What a refreshed projection now holds, for the module that asked for the refresh.
 *
 * <p>Deliberately not {@code ServiceBooking}: handing another module the document would hand
 * it a mutable copy of booking state and a setter for every field on it. This is a read of the
 * outcome — the status that was applied, the schedule as it now stands, and whether the
 * refresh was applied at all.
 *
 * @param applied false when the refresh was older than what SBP already had, in which case
 *        every other field describes the state that was kept rather than the one offered
 * @param technicians who is assigned now. Never null — an unassigned booking is an empty list
 *        here, so a caller need not distinguish "none" from "not sent"
 */
public record BookingProjectionView(
		String bookingRef,
		String customerId,
		BookingStatus status,
		Instant scheduledStartAt,
		Integer estimatedDurationMinutes,
		List<TechnicianRef> technicians,
		Instant lastSyncedAt,
		boolean applied) {
}
