package com.fixinh.sbp.modules.booking.interfaces.external;

import com.fixinh.sbp.modules.booking.dto.external.BookingCancellationDto;
import com.fixinh.sbp.modules.booking.dto.external.BookingDetailDto;
import com.fixinh.sbp.modules.booking.dto.external.BookingListResponse;
import com.fixinh.sbp.modules.booking.dto.external.BookingSummaryDto;
import com.fixinh.sbp.modules.booking.dto.internal.BookingCancelCommand;
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

	/**
	 * Cancels or terminates one of the customer's own bookings, with a written reason and up to
	 * three evidence images (API Documentation §7.4, Req 3.3.M1).
	 *
	 * <p>The resulting status is not an argument and not a return of this module's reasoning:
	 * SMP owns the booking record and applies the rule, and this call echoes its answer.
	 *
	 * @param customerId the owner, read from the token. A booking belonging to anyone else is
	 *        reported as 404, not 403 — §7.3 makes "not yours" and "does not exist" the same
	 *        answer so references cannot be probed
	 * @throws com.fixinh.sbp.common.exception.ApiException 400 VALIDATION_FAILED /
	 *         TOO_MANY_IMAGES, 404 BOOKING_NOT_FOUND, 409 BOOKING_NOT_CANCELLABLE,
	 *         413 FILE_TOO_LARGE, 415 UNSUPPORTED_FILE_TYPE, 502 UPLOAD_FAILED or
	 *         503 SMP_UNAVAILABLE
	 */
	BookingCancellationDto cancelBooking(
			String customerId, String bookingRef, BookingCancelCommand command);

	/**
	 * One of the customer's own bookings in full (API Documentation §7.3, Reqs 3.3.S4, 3.3.S5,
	 * 3.3.S6) — the issue they described, the window they offered, the schedule once there is
	 * one, and the bill once the technician has issued it.
	 *
	 * @param customerId the owner, read from the token
	 * @throws com.fixinh.sbp.common.exception.ApiException 404 BOOKING_NOT_FOUND when the
	 *         reference is unknown <b>or belongs to someone else</b>. §7.3 makes those the same
	 *         answer deliberately, so references cannot be probed for existence
	 */
	BookingDetailDto findBooking(String customerId, String bookingRef);
}
