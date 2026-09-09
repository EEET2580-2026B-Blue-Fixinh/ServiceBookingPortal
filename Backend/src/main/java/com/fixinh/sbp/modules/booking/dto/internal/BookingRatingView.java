package com.fixinh.sbp.modules.booking.dto.internal;

import java.util.List;

import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.booking.model.TechnicianRef;

/** Local booking projection used by the rating module before SMP verification. */
public record BookingRatingView(
		String bookingRef,
		String customerId,
		String serviceId,
		String serviceName,
		BookingStatus status,
		List<TechnicianRef> technicians,
		Region homeRegion) {
}
