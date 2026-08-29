package com.fixinh.sbp.modules.booking.dto.external;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.booking.model.TechnicianRef;

/**
 * One row of {@code GET /bookings} (API Documentation §7.2).
 *
 * <p>{@code scheduledStartAt}, {@code estimatedDurationMinutes} and {@code technicians} are
 * {@code null} on anything other than a SCHEDULED booking; {@code totalCost} is {@code null} on
 * anything other than a COMPLETED one. {@link JsonInclude} drops a {@code null} field from the
 * response entirely, matching §7.2's own note: "absent fields are omitted, not returned as
 * null".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingListItemDto(
		String bookingRef,
		BookingStatus status,
		String serviceId,
		String serviceName,
		LocalDate preferredServiceDate,
		Instant createdAt,
		Instant scheduledStartAt,
		Integer estimatedDurationMinutes,
		List<TechnicianRef> technicians,
		Long totalCost,
		boolean hasRating,
		Instant lastSyncedAt) {
}
