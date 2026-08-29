package com.fixinh.sbp.common.smp.dto;

import java.time.Instant;

/**
 * What SMP hands back after recording a cancellation (API Documentation §12.4).
 *
 * <p>The whole point of this type is {@code status}. §12.4 states it plainly — <i>"SBP does not
 * decide the resulting status. One implementation of the rule, on the side that owns the
 * record."</i> — so SBP sends the cancellation, reads the status back, and echoes it to the
 * customer rather than working out for itself whether the answer is
 * CANCELLED_BY_CUSTOMER or TERMINATED_BY_CUSTOMER.
 *
 * <p>A raw string for the same reason {@link SmpBookingDto#status()} is one: {@code common} does
 * not depend on the booking module's enum, and the service layer validates the value against it.
 */
public record SmpCancellationDto(String bookingRef, String status, Instant cancelledAt) {
}
