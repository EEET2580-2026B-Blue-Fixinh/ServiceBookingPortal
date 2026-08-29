package com.fixinh.sbp.modules.booking.interfaces.external;

import com.fixinh.sbp.modules.booking.dto.external.BookingListResponse;
import com.fixinh.sbp.modules.booking.dto.external.BookingSummaryDto;
import com.fixinh.sbp.modules.booking.dto.internal.BookingCreateCommand;
import com.fixinh.sbp.modules.booking.dto.internal.BookingSearchFilter;

/** What the booking module's controller may call (API Documentation §7). */
public interface BookingExternalService {
	/**
	 * Creates a service booking for exactly one repair service (Req 3.3.S1–S3).
	 *
	 * @param customerId the owner, read from the token — never from the request body
	 * @throws com.fixinh.sbp.common.exception.ApiException 400 VALIDATION_FAILED, 404
	 *         SERVICE_NOT_FOUND, or 422 PREFERRED_DATE_IN_PAST / TIME_WINDOW_INVALID
	 */
	BookingSummaryDto createBooking(String customerId, BookingCreateCommand command);

	/**
	 * The customer's own bookings, grouped into active and past, with search, filter and sort
	 * applied server-side (API Documentation §7.2, Reqs 3.3.S4, 3.3.M2–M6).
	 *
	 * @param customerId the owner, read from the token — a booking list is never requested on
	 *                    another customer's behalf
	 * @throws com.fixinh.sbp.common.exception.ApiException 400 INVALID_SORT_FIELD or
	 *         INVALID_DATE_RANGE
	 */
	BookingListResponse listBookings(String customerId, BookingSearchFilter filter);
}
