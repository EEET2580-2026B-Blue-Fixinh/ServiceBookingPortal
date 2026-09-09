package com.fixinh.sbp.common.smp.client;

import java.util.List;

import com.fixinh.sbp.common.smp.dto.SmpBookingDto;
import com.fixinh.sbp.common.smp.dto.SmpCancellationDto;
import com.fixinh.sbp.common.smp.dto.SmpCancellationRequest;
import com.fixinh.sbp.common.smp.dto.SmpRepairServiceDto;
import com.fixinh.sbp.common.smp.dto.SmpRatingBookingDto;

/**
 * What SBP needs from SMP for the booking domain (API Documentation §12.1 create a booking,
 * §12.5 repair-service catalogue) — both provisional until Team SMT publishes its own contract.
 *
 * <p>Mock and live implementations sit behind this one interface (§13.1); nothing else in the
 * codebase knows which is active. Only
 * {@link com.fixinh.sbp.common.smp.client.mock.MockSmpBookingClient} exists today — §12 is
 * explicitly "provisional, do not treat as agreed", so there is nothing yet for a live
 * implementation to call.
 */
public interface SmpBookingClient {

	/** The current catalogue, active and inactive services alike. */
	List<SmpRepairServiceDto> listRepairServices();

	/**
	 * Forwards a booking a customer just submitted. SMP owns the record and generates
	 * {@code bookingRef}; SBP keeps only a projection of it (§7, "Behaviour when SMP is
	 * unavailable").
	 */
	SmpBookingDto createBooking(String customerId, String serviceId);

	/**
	 * Records a cancellation the customer made in SBP (§12.4, Req 3.3.M1).
	 *
	 * <p><b>The resulting status is SMP's answer, not SBP's.</b> §12.4 puts the rule — cancelled
	 * from Created, Confirmed or Scheduled; terminated from Working — on the side that owns the
	 * record, so this call returns the new status rather than accepting one.
	 *
	 * @param currentStatus what SBP's projection believes the booking is. The real SMP will look
	 *        this up for itself and ignore what it is told; the mock has no store of its own, so
	 *        this is how it learns where the booking stood. Either way the <i>rule</i> stays
	 *        behind this interface and never leaks into the booking service.
	 * @throws com.fixinh.sbp.common.exception.ApiException 409 {@code BOOKING_NOT_CANCELLABLE}
	 *         when the booking has already finished, been cancelled or been aborted — §12.4 asks
	 *         for "a 409 with a distinguishable code ... so SBP can explain why"
	 */
	SmpCancellationDto cancelBooking(
			String bookingRef, String currentStatus, SmpCancellationRequest request);

	/**
	 * Reads the authoritative fields needed before accepting a rating (§12.3). Until SMT
	 * publishes that endpoint, the mock echoes the supplied SBP projection. A live client must
	 * ignore {@code localProjection} and fetch the booking from SMP.
	 */
	SmpRatingBookingDto findBookingForRating(SmpRatingBookingDto localProjection);
}
