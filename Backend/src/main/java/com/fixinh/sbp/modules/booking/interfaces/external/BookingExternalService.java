package com.fixinh.sbp.modules.booking.interfaces.external;

import com.fixinh.sbp.modules.booking.dto.external.BookingSummaryDto;
import com.fixinh.sbp.modules.booking.dto.internal.BookingCreateCommand;

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
}
