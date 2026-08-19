package com.fixinh.sbp.modules.booking.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.smp.client.SmpBookingClient;
import com.fixinh.sbp.common.smp.dto.SmpBookingDto;
import com.fixinh.sbp.common.smp.dto.SmpRepairServiceDto;
import com.fixinh.sbp.modules.booking.dto.external.BookingSummaryDto;
import com.fixinh.sbp.modules.booking.dto.internal.BookingCreateCommand;
import com.fixinh.sbp.modules.booking.interfaces.external.BookingExternalService;
import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.booking.model.ServiceBooking;
import com.fixinh.sbp.modules.booking.repository.ServiceBookingRepository;
import com.fixinh.sbp.modules.customer.interfaces.external.CustomerExternalService;
import com.fixinh.sbp.modules.customer.model.Address;

import lombok.RequiredArgsConstructor;

/** Booking creation (API Documentation §7.1, Reqs 3.3.S1–S3). */
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingExternalService {

	/** API Doc §7.1: the window must be at least this many minutes wide. */
	private static final int MINIMUM_WINDOW_MINUTES = 60;

	private static final String CONFIRMATION_MESSAGE =
			"Your booking has been created. A receptionist will confirm it shortly.";

	private final BookingCreateValidator validator;
	private final SmpBookingClient smpBookingClient;
	private final CustomerExternalService customerService;
	private final ServiceBookingRepository bookingRepository;

	@Override
	public BookingSummaryDto createBooking(String customerId, BookingCreateCommand command) {
		validator.validate(command);
		validateScheduleWindow(command);

		SmpRepairServiceDto service = findActiveService(command.serviceId());

		// A booking is filed in the same store as the customer who made it, not the store its
		// own serviceAddress would resolve to — the two can differ (§7.1's serviceAddress note).
		Region homeRegion = customerService.findHomeRegion(customerId)
				.orElseThrow(() -> new IllegalStateException(
						"Authenticated customer " + customerId + " has no profile."));

		// SMP owns the booking record and generates the reference; this call never fails today
		// because the mock client always succeeds (§2.8, §13).
		SmpBookingDto ack = smpBookingClient.createBooking(customerId, service.serviceId());
		BookingStatus status = BookingStatus.valueOf(ack.status());

		ServiceBooking booking = ServiceBooking.builder()
				.bookingRef(ack.bookingRef())
				.customerId(customerId)
				.serviceId(service.serviceId())
				.serviceName(service.serviceName())
				.baseServiceFee(service.baseServiceFee())
				.issueDescription(command.issueDescription().trim())
				.preferredServiceDate(command.preferredServiceDate())
				.availableStartTime(command.availableStartTime())
				.availableEndTime(command.availableEndTime())
				.serviceAddress(toAddress(command.serviceAddress()))
				.status(status)
				.createdAt(ack.createdAt())
				.homeRegion(homeRegion)
				.build();

		bookingRepository.save(booking);

		return new BookingSummaryDto(
				booking.getBookingRef(),
				booking.getStatus(),
				booking.getServiceName(),
				booking.getPreferredServiceDate(),
				booking.getCreatedAt(),
				CONFIRMATION_MESSAGE);
	}

	/** Req: "validate service exists". Inactive counts as not found (§5.2's activeOnly note). */
	private SmpRepairServiceDto findActiveService(String serviceId) {
		return smpBookingClient.listRepairServices().stream()
				.filter(candidate -> candidate.serviceId().equals(serviceId) && candidate.active())
				.findFirst()
				.orElseThrow(() -> new ApiException(ErrorCode.SERVICE_NOT_FOUND));
	}

	/** Req: "add validation for schedule time (must be future)", plus the §7.1 window rule. */
	private void validateScheduleWindow(BookingCreateCommand command) {
		LocalDate preferredServiceDate = command.preferredServiceDate();
		if (preferredServiceDate.isBefore(LocalDate.now())) {
			throw new ApiException(ErrorCode.PREFERRED_DATE_IN_PAST);
		}

		LocalTime start = command.availableStartTime();
		LocalTime end = command.availableEndTime();
		if (!end.isAfter(start) || Duration.between(start, end).toMinutes() < MINIMUM_WINDOW_MINUTES) {
			throw new ApiException(ErrorCode.TIME_WINDOW_INVALID);
		}
	}

	private Address toAddress(BookingCreateCommand.AddressCommand address) {
		return Address.builder()
				.streetNumber(address.streetNumber().trim())
				.streetName(address.streetName().trim())
				.ward(address.ward().trim())
				.provinceCode(address.provinceCode().trim().toUpperCase(Locale.ROOT))
				.build();
	}
}
