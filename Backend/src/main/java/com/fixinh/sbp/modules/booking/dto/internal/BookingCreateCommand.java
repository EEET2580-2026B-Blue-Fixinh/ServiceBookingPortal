package com.fixinh.sbp.modules.booking.dto.internal;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The booking creation request (API Documentation §7.1), before the customer id from the token
 * is attached — {@code customerId} is never part of this command, since a booking is always
 * filed for whoever the token names, never for an id in the body (Req A.2.a).
 *
 * <p>No bean-validation annotations, for the same reason {@code RegisterCommand} has none:
 * {@code BookingCreateValidator} collects every failing field at once rather than stopping at
 * the first.
 */
public record BookingCreateCommand(
		String serviceId,
		String issueDescription,
		LocalDate preferredServiceDate,
		LocalTime availableStartTime,
		LocalTime availableEndTime,
		AddressCommand serviceAddress) {

	public record AddressCommand(
			String streetNumber,
			String streetName,
			String ward,
			String provinceCode) {
	}
}
