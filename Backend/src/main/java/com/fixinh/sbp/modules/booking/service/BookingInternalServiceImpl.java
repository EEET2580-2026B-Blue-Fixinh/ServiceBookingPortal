package com.fixinh.sbp.modules.booking.service;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.modules.booking.dto.internal.BookingRatingView;
import com.fixinh.sbp.modules.booking.interfaces.internal.BookingInternalService;
import com.fixinh.sbp.modules.booking.model.ServiceBooking;
import com.fixinh.sbp.modules.booking.repository.ServiceBookingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingInternalServiceImpl implements BookingInternalService {
	private final ServiceBookingRepository repository;

	@Override
	public BookingRatingView findOwnedForRating(String customerId, String bookingRef) {
		ServiceBooking booking = repository.findByBookingRef(bookingRef)
				.filter(found -> customerId.equals(found.getCustomerId()))
				.orElseThrow(() -> new ApiException(ErrorCode.BOOKING_NOT_FOUND));
		return new BookingRatingView(booking.getBookingRef(), booking.getCustomerId(),
				booking.getServiceId(), booking.getServiceName(), booking.getStatus(),
				booking.getTechnicians(), booking.getHomeRegion());
	}
}
