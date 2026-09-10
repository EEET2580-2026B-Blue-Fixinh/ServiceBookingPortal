package com.fixinh.sbp.common.smp.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The booking SBP forwards to SMP (API Documentation §12.1), in the shape SMT's
 * {@code POST /api/v1/bookings} actually accepts.
 *
 * <p>Every field here is one SMT rejects the request without. It is deliberately wider than
 * {@code BookingCreateCommand}: {@code customerId} and {@code customerFullName} come from the
 * token and the customer profile rather than the request body, and {@code provinceName} is
 * resolved from the stored code before the call — SMT stores the name it is given and has no
 * province list of its own to look one up in.
 *
 * @param originChannel always {@link #ORIGIN_SBP}; SMT refuses any other value on this endpoint,
 *        because it takes bookings from its own receptionists too
 */
public record SmpBookingCreateRequest(
		String customerId,
		String customerFullName,
		String serviceId,
		String issueDescription,
		LocalDate preferredServiceDate,
		LocalTime availableStartTime,
		LocalTime availableEndTime,
		Address serviceAddress,
		String originChannel) {

	/** The only channel a booking from SBP can carry. */
	public static final String ORIGIN_SBP = "SBP";

	public static SmpBookingCreateRequest fromSbp(
			String customerId,
			String customerFullName,
			String serviceId,
			String issueDescription,
			LocalDate preferredServiceDate,
			LocalTime availableStartTime,
			LocalTime availableEndTime,
			Address serviceAddress) {

		return new SmpBookingCreateRequest(customerId, customerFullName, serviceId,
				issueDescription, preferredServiceDate, availableStartTime, availableEndTime,
				serviceAddress, ORIGIN_SBP);
	}

	/**
	 * Separate from {@link SmpAddress}, which is the staff endpoints' shape and carries no
	 * province name. SMT requires a {@code provinceCode} or a {@code provinceName} and treats a
	 * blank {@code ward} as a validation failure.
	 */
	public record Address(
			String streetNumber,
			String streetName,
			String ward,
			String provinceCode,
			String provinceName) {
	}
}
