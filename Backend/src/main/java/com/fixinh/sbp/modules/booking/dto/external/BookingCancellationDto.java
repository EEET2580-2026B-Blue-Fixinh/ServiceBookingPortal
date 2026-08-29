package com.fixinh.sbp.modules.booking.dto.external;

import java.time.Instant;
import java.util.List;

import com.fixinh.sbp.modules.booking.model.BookingStatus;

/**
 * What {@code POST /bookings/{bookingRef}/cancellation} returns (API Documentation §7.4).
 *
 * <p>{@code status} is the outcome SMP decided, echoed rather than computed here:
 * CANCELLED_BY_CUSTOMER from Created, Confirmed or Scheduled, TERMINATED_BY_CUSTOMER from
 * Working. The request never names it, which is why the response must (Req 3.3.M1).
 *
 * <p>{@code evidenceUrls} is empty rather than absent when no images were attached — an empty
 * array is the honest answer to "what evidence is there", and the frontend renders nothing for
 * it either way.
 */
public record BookingCancellationDto(
		String bookingRef,
		BookingStatus status,
		String reason,
		List<String> evidenceUrls,
		Instant cancelledAt) {
}
