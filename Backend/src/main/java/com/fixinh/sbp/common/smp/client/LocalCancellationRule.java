package com.fixinh.sbp.common.smp.client;

import java.time.Instant;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.smp.dto.SmpCancellationDto;

/**
 * §12.4's rule — which past status a cancellation lands on — applied inside SBP.
 *
 * <p><b>This is a stand-in, and §12.4 says the rule belongs to whoever owns the record.</b> It
 * exists here because SMT's service has no cancellation endpoint: its API is
 * {@code POST /bookings}, {@code GET /bookings} and {@code GET /bookings/{ref}} and nothing
 * else. Rather than let cancellation stop working the moment {@code fixinh.sbp.smp.mode=live} is
 * set, both the mock and the live client answer from this one copy of the rule.
 *
 * <p>The cost of that, stated plainly: in {@code live} mode SMT's copy of a cancelled booking
 * <b>stays at CREATED</b>. SBP and SMT disagree about that booking until Team SMT publishes the
 * endpoint, at which point {@link com.fixinh.sbp.common.smp.client.live.LiveSmpBookingClient}
 * should call it and this class should survive only for the mock.
 */
public final class LocalCancellationRule {

	private LocalCancellationRule() {
	}

	/**
	 * @param currentStatus the booking's status as SBP's projection has it
	 * @throws ApiException 409 {@code BOOKING_NOT_CANCELLABLE} for COMPLETED and for anything
	 *         already cancelled, terminated or aborted
	 */
	public static SmpCancellationDto apply(
			String bookingRef, String currentStatus, Instant cancelledAt) {

		String resulting = switch (currentStatus) {
			case "CREATED", "CONFIRMED", "SCHEDULED" -> "CANCELLED_BY_CUSTOMER";
			case "WORKING" -> "TERMINATED_BY_CUSTOMER";
			// COMPLETED, and anything already cancelled or aborted. §12.4 asks for a
			// distinguishable 409 so SBP can say why rather than showing a generic failure.
			default -> throw new ApiException(ErrorCode.BOOKING_NOT_CANCELLABLE);
		};

		return new SmpCancellationDto(bookingRef, resulting, cancelledAt);
	}
}
