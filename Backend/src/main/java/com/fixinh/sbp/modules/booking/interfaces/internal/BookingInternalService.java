package com.fixinh.sbp.modules.booking.interfaces.internal;

import com.fixinh.sbp.modules.booking.dto.internal.BookingRatingView;

/** Booking facts another SBP module may consume without accessing booking persistence. */
public interface BookingInternalService {
	BookingRatingView findOwnedForRating(String customerId, String bookingRef);
}
