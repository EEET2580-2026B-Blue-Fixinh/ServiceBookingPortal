package com.fixinh.sbp.common.smp.dto;

import java.util.List;

/** The subset of SMP's §12.3 booking response needed to validate and credit a rating. */
public record SmpRatingBookingDto(
		String bookingRef,
		String customerId,
		String serviceId,
		String serviceName,
		String status,
		List<SmpRatingTechnicianDto> technicians) {
}
