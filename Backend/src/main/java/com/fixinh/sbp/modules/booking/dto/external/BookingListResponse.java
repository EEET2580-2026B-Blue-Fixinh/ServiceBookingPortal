package com.fixinh.sbp.modules.booking.dto.external;
import java.util.List;
import com.fixinh.sbp.common.dto.PageResponse;
/**
 * {@code GET /bookings}' top-level shape (API Documentation §7.2): two arrays rather than one,
 * because Req 3.3.M2 asks for active bookings above past ones — the frontend renders them in
 * this order and does not re-sort. {@code page} covers the combined, filtered set both arrays
 * were sliced from, not either array alone.
 */
public record BookingListResponse(
		List<BookingListItemDto> active,
		List<BookingListItemDto> past,
		PageResponse.Metadata page) {
}
