package com.fixinh.sbp.modules.booking.dto.internal;

import java.time.Instant;
import java.util.List;

import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.booking.model.TechnicianRef;

/**
 * What SMP says a booking now looks like (§11.7, §12.3) — the input to refreshing SBP's
 * projection of it.
 *
 * <p>Only the fields SMP owns and can change after creation. What the customer wrote — the
 * issue description, their preferred date and window, the address they chose — is not here,
 * because SMP never rewrites it and a field that cannot change does not belong in an update.
 *
 * <p>Every field except {@code status} may legitimately be null: a confirmed booking has no
 * visit time yet, an unassigned one no technicians, an unfinished one no bill. Null therefore
 * has to mean "SMP did not send this", never "SMP cleared this", and it is the merge in
 * {@code BookingInternalServiceImpl} that draws that distinction — see its own note on why
 * absence is not erasure.
 *
 * @param status the booking's status now. The one field always present
 * @param scheduledStartAt when technicians will arrive, once SMP has fixed it
 * @param estimatedDurationMinutes how long the visit should take
 * @param technicians who is assigned. Needed on a SCHEDULED booking for Req 3.3.S5, which is
 *        the reason §11.7 requires the refresh to precede the notification
 * @param bill what the job cost, once the technician has issued it (Req 3.3.S6)
 * @param observedAt when SMP says this state was true — §11.7's {@code occurredAt}. What lets
 *        the merge recognise an event that arrived out of order
 */
public record BookingProjectionRefresh(
		BookingStatus status,
		Instant scheduledStartAt,
		Integer estimatedDurationMinutes,
		List<TechnicianRef> technicians,
		BillRefresh bill,
		Instant observedAt) {

	public BookingProjectionRefresh {
		if (status == null) {
			throw new IllegalArgumentException(
					"A projection refresh needs the status the booking is now in.");
		}
		if (observedAt == null) {
			throw new IllegalArgumentException(
					"A projection refresh needs the time SMP observed this state.");
		}
	}

	/**
	 * A bill as SMP sends it — including the total SMP computed.
	 *
	 * <p>{@code Bill} deliberately has no {@code totalCost} field: Req 3.3.S6 derives it, so a
	 * stored copy could drift. SMP's total is carried here, separately, for one purpose — §12.3
	 * asks SBP to recompute rather than trust it, and a recomputation with nothing to compare
	 * against is not a check. The comparison happens in the merge and its only effect is a log
	 * line; SBP's own arithmetic is what is stored either way.
	 *
	 * @param reportedTotalCost SMP's own total, or null when SMP did not send one
	 */
	public record BillRefresh(
			long serviceFee,
			long materialCost,
			String billImageUrl,
			Instant issuedAt,
			Long reportedTotalCost) {
	}
}
