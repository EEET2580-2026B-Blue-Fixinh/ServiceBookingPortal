package com.fixinh.sbp.modules.booking.service;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import com.fixinh.sbp.common.dto.PageResponse;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.smp.client.SmpBookingClient;
import com.fixinh.sbp.common.smp.dto.SmpBookingDto;
import com.fixinh.sbp.common.smp.dto.SmpRepairServiceDto;
import com.fixinh.sbp.modules.booking.dto.external.BookingListItemDto;
import com.fixinh.sbp.modules.booking.dto.external.BookingListResponse;
import com.fixinh.sbp.modules.booking.dto.external.BookingSummaryDto;
import com.fixinh.sbp.modules.booking.dto.internal.BookingCreateCommand;
import com.fixinh.sbp.modules.booking.dto.internal.BookingSearchFilter;
import com.fixinh.sbp.modules.booking.interfaces.external.BookingExternalService;
import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.booking.model.ServiceBooking;
import com.fixinh.sbp.modules.booking.model.TechnicianRef;
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

	@Override
	public BookingListResponse listBookings(String customerId, BookingSearchFilter filter) {
		boolean sortAscending = parseSortDirection(filter.sort());
		validateDateRange(filter.createdFrom(), filter.createdTo());
		Region homeRegion = customerService.findHomeRegion(customerId)
				.orElseThrow(() -> new IllegalStateException(
						"Authenticated customer " + customerId + " has no profile."));
		List<ServiceBooking> filtered = bookingRepository.findByCustomerId(customerId, homeRegion)
				.stream()
				.filter(booking -> matchesQuery(booking, filter.q()))
				.filter(booking -> matchesService(booking, filter.serviceIds()))
				.filter(booking -> matchesDateRange(booking, filter.createdFrom(), filter.createdTo()))
				.filter(booking -> matchesGroup(booking, filter.group()))
				.sorted(bookingComparator(sortAscending))
				.toList();
		int size = PageResponse.normaliseSize(filter.size());
		int pageNumber = PageResponse.normalisePage(filter.page());
		int fromIndex = Math.min(pageNumber * size, filtered.size());
		int toIndex = Math.min(fromIndex + size, filtered.size());
		List<ServiceBooking> pageSlice = filtered.subList(fromIndex, toIndex);
		List<BookingListItemDto> active = pageSlice.stream()
				.filter(booking -> booking.getStatus().isActive())
				.map(this::toListItem)
				.toList();
		List<BookingListItemDto> past = pageSlice.stream()
				.filter(booking -> !booking.getStatus().isActive())
				.map(this::toListItem)
				.toList();
		return new BookingListResponse(active, past,
				PageResponse.Metadata.of(pageNumber, size, filtered.size()));
	}
	/**
	 * @return true for {@code createdAt,asc}, false for the default {@code createdAt,desc}
	 * @throws ApiException 400 INVALID_SORT_FIELD when the field named is anything but createdAt,
	 *         or the direction is neither asc nor desc
	 */
	private boolean parseSortDirection(String sort) {
		if (sort == null || sort.isBlank()) {
			return false;
		}
		String[] parts = sort.split(",", 2);
		if (!"createdAt".equals(parts[0].trim())) {
			throw new ApiException(ErrorCode.INVALID_SORT_FIELD);
		}
		String direction = parts.length > 1 ? parts[1].trim() : "desc";
		if (direction.equalsIgnoreCase("asc")) {
			return true;
		}
		if (direction.equalsIgnoreCase("desc")) {
			return false;
		}
		throw new ApiException(ErrorCode.INVALID_SORT_FIELD);
	}

	private void validateDateRange(LocalDate from, LocalDate to) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new ApiException(ErrorCode.INVALID_DATE_RANGE);
		}
	}

	/**
	 * Active-first (Req 3.3.M2), createdAt within each group per the requested direction
	 * (Req 3.3.M6) — one comparator produces both, since sorting the whole set by (group, then
	 * createdAt) is the same ordering as sorting each group separately and concatenating them.
	 */
	private Comparator<ServiceBooking> bookingComparator(boolean ascending) {
		Comparator<ServiceBooking> byGroup =
				Comparator.comparing(booking -> booking.getStatus().isActive() ? 0 : 1);
		Comparator<Instant> byCreatedAt = ascending
				? Comparator.naturalOrder()
				: Comparator.reverseOrder();
		return byGroup.thenComparing(ServiceBooking::getCreatedAt, byCreatedAt);
	}

	/** Req 3.3.M3–M4: booking reference or an assigned technician's name, case-insensitive. */
	private boolean matchesQuery(ServiceBooking booking, String q) {
		if (q == null || q.isBlank()) {
			return true;
		}
		String needle = q.trim().toLowerCase(Locale.ROOT);
		if (booking.getBookingRef() != null
				&& booking.getBookingRef().toLowerCase(Locale.ROOT).contains(needle)) {
			return true;
		}
		return booking.getTechnicians() != null && booking.getTechnicians().stream()
				.anyMatch(technician -> technician.fullName() != null
						&& technician.fullName().toLowerCase(Locale.ROOT).contains(needle));
	}

	private boolean matchesService(ServiceBooking booking, List<String> serviceIds) {
		return serviceIds == null || serviceIds.isEmpty() || serviceIds.contains(booking.getServiceId());
	}

	/** Req 3.3.M5: both bounds inclusive, against the booking's creation date. */
	private boolean matchesDateRange(ServiceBooking booking, LocalDate from, LocalDate to) {
		LocalDate createdDate = booking.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
		if (from != null && createdDate.isBefore(from)) {
			return false;
		}
		return to == null || !createdDate.isAfter(to);
	}

	/** Unrecognised values are ignored rather than rejected — §7.2 documents no error for one. */
	private boolean matchesGroup(ServiceBooking booking, String group) {
		if (group == null) {
			return true;
		}
		if ("ACTIVE".equalsIgnoreCase(group)) {
			return booking.getStatus().isActive();
		}
		if ("PAST".equalsIgnoreCase(group)) {
			return !booking.getStatus().isActive();
		}
		return true;
	}

	private BookingListItemDto toListItem(ServiceBooking booking) {
		boolean scheduled = booking.getStatus() == BookingStatus.SCHEDULED;
		boolean completed = booking.getStatus() == BookingStatus.COMPLETED;
		List<TechnicianRef> technicians = booking.getTechnicians();
		return new BookingListItemDto(
				booking.getBookingRef(),
				booking.getStatus(),
				booking.getServiceId(),
				booking.getServiceName(),
				booking.getPreferredServiceDate(),
				booking.getCreatedAt(),
				scheduled ? booking.getScheduledStartAt() : null,
				scheduled ? booking.getEstimatedDurationMinutes() : null,
				scheduled && technicians != null && !technicians.isEmpty() ? technicians : null,
				completed ? booking.getTotalCost() : null,
				// No rating module yet — every booking genuinely has none, so false is accurate
				// today, not a placeholder. Revisit once ratings exist to check for real.
				false,
				// No sync pipeline updates a booking after creation yet (no §11.7 receiver), so
				// createdAt is the last moment this projection is known to have been correct.
				booking.getCreatedAt());
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
