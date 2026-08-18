package com.fixinh.sbp.common.smp.dto;

import java.time.Instant;

/**
 * What SMP hands back after SBP forwards a booking it owns creating (API Documentation §12.1) —
 * just enough for SBP to show the customer their reference.
 *
 * <p>{@code status} is a raw string rather than the booking module's {@code BookingStatus}:
 * {@code common} must not depend on a module's types, and a value this load-bearing (Req 3.3.M2's
 * active/past grouping breaks on an unrecognised one) is validated against the enum by the
 * service layer, not trusted blind.
 */
public record SmpBookingDto(String bookingRef, String status, Instant createdAt) {
}
