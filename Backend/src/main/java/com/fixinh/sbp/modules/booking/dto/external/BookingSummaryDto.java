package com.fixinh.sbp.modules.booking.dto.external;

import java.time.Instant;
import java.time.LocalDate;

import com.fixinh.sbp.modules.booking.model.BookingStatus;

/**
 * The confirmation returned by {@code POST /bookings} (API Documentation §7.1). The shape is
 * binding — no price field here; a booking's cost is only known once it is Completed (§3.3.S6,
 * §7.3), well after creation.
 */
public record BookingSummaryDto(
		String bookingRef,
		BookingStatus status,
		String serviceName,
		LocalDate preferredServiceDate,
		Instant createdAt,
		String message) {
}
