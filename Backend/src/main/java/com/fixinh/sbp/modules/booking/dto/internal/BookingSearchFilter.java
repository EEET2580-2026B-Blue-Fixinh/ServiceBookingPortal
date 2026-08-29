package com.fixinh.sbp.modules.booking.dto.internal;
import java.time.LocalDate;
import java.util.List;

/**
 * The raw query parameters of {@code GET /bookings} (API Documentation §7.2), carried from the
 * controller into the service layer unvalidated — {@code sort} and the date range are checked
 * once there, where the ApiException codes (INVALID_SORT_FIELD, INVALID_DATE_RANGE) belong.
 *
 * @param sort raw {@code field,direction}, e.g. {@code createdAt,desc}; null means the default.
 */
public record BookingSearchFilter(
		String q,
		List<String> serviceIds,
		LocalDate createdFrom,
		LocalDate createdTo,
		String group,
		String sort,
		Integer page,
		Integer size) {
}
