package com.fixinh.sbp.modules.booking.service;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.fixinh.sbp.common.dto.PageResponse;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.smp.client.SmpBookingClient;
import com.fixinh.sbp.common.smp.dto.SmpBookingDto;
import com.fixinh.sbp.common.smp.dto.SmpCancellationDto;
import com.fixinh.sbp.common.smp.dto.SmpCancellationRequest;
import com.fixinh.sbp.common.smp.dto.SmpRepairServiceDto;
import com.fixinh.sbp.common.storage.ImageStorageService;
import com.fixinh.sbp.common.storage.ImageUploadException;
import com.fixinh.sbp.modules.booking.dto.external.BookingCancellationDto;
import com.fixinh.sbp.modules.booking.dto.external.BookingListItemDto;
import com.fixinh.sbp.modules.booking.dto.external.BookingListResponse;
import com.fixinh.sbp.modules.booking.dto.external.BookingSummaryDto;
import com.fixinh.sbp.modules.booking.dto.internal.BookingCancelCommand;
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
import lombok.extern.slf4j.Slf4j;

/** Booking creation (API Documentation §7.1, Reqs 3.3.S1–S3). */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingExternalService {
	/** API Doc §7.1: the window must be at least this many minutes wide. */
	private static final int MINIMUM_WINDOW_MINUTES = 60;
	private static final String CONFIRMATION_MESSAGE =
			"Your booking has been created. A receptionist will confirm it shortly.";
	/** §7.4: evidence images are grouped under this folder, giving cancel/ord-2026-1024-1.jpg. */
	private static final String EVIDENCE_FOLDER = "cancel";

	/**
	 * How many references SMP may offer before the request is refused. Generous enough to walk
	 * past a handful of stale numbers after a restart, small enough that a genuinely exhausted
	 * sequence fails fast instead of hammering both stores.
	 */
	private static final int MAX_REFERENCE_ATTEMPTS = 10;

	private final BookingCreateValidator validator;
	private final BookingCancelValidator cancelValidator;
	private final SmpBookingClient smpBookingClient;
	private final CustomerExternalService customerService;
	private final ServiceBookingRepository bookingRepository;
	private final ImageStorageService imageStorage;
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

		// SMP owns the booking record and generates the reference (§2.8, §13) — but a reference
		// is not taken on trust, because a mock's sequence is not durable the way SMP's is.
		SmpBookingDto ack = createWithUnusedReference(customerId, service.serviceId());
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

		try {
			bookingRepository.save(booking);
		}
		catch (DuplicateKeyException ex) {
			// The reference was free when it was checked and taken by the time it was written.
			// Rare, and the unique index is what makes it rare rather than silent — but a lost
			// race is not a fault, so it comes back as the retryable answer §7.1 already defines
			// rather than as a 500 naming a traceId the customer cannot act on.
			log.warn("Booking reference {} was taken between the check and the write.",
					booking.getBookingRef(), ex);
			throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
		}

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
	 * §7.4, Req 3.3.M1. The order of the steps below is the contract, not a preference: §7.4
	 * promises that a failed upload records no status change and that an unreachable SMP changes
	 * nothing, so the local write is last and every way of failing happens before it.
	 */
	@Override
	public BookingCancellationDto cancelBooking(
			String customerId, String bookingRef, BookingCancelCommand command) {

		// 1. Cheapest first, and before a single byte is uploaded.
		cancelValidator.validate(command);

		// 2. A booking belonging to someone else is reported as missing, not as forbidden (§7.3).
		ServiceBooking booking = bookingRepository.findByBookingRef(bookingRef)
				.filter(candidate -> customerId.equals(candidate.getCustomerId()))
				.orElseThrow(() -> new ApiException(ErrorCode.BOOKING_NOT_FOUND));

		// 3. Fail fast on a booking that has already finished. This is Appendix A's own
		// active/past grouping, not a second copy of §12.4's rule — which of the two past
		// statuses results is still SMP's decision at step 5.
		if (!booking.getStatus().isActive()) {
			throw new ApiException(ErrorCode.BOOKING_NOT_CANCELLABLE);
		}

		// 4. Images reach the store; only their URLs go any further (§7.4).
		Instant cancelledAt = Instant.now();
		List<String> evidenceUrls = storeEvidence(bookingRef, command);

		// 5. SMP owns the record and therefore owns the resulting status. Today that is the mock
		// applying §13.2's scenarios; the call site does not change when the real SMP arrives.
		SmpCancellationDto ack = smpBookingClient.cancelBooking(
				bookingRef,
				booking.getStatus().name(),
				SmpCancellationRequest.byCustomer(
						command.reason().trim(), evidenceUrls, cancelledAt));

		// 6. Only now is anything written locally.
		booking.setStatus(BookingStatus.valueOf(ack.status()));
		booking.setCancellationReason(command.reason().trim());
		booking.setEvidenceUrls(evidenceUrls);
		booking.setCancelledAt(ack.cancelledAt());
		bookingRepository.save(booking);

		return new BookingCancellationDto(
				booking.getBookingRef(),
				booking.getStatus(),
				booking.getCancellationReason(),
				booking.getEvidenceUrls(),
				booking.getCancelledAt());
	}

	/**
	 * @throws ApiException 502 UPLOAD_FAILED — the store refused, and because this runs before
	 *         the booking is touched, "no status change was recorded" is true by construction
	 *         rather than by unwinding one
	 */
	private List<String> storeEvidence(String bookingRef, BookingCancelCommand command) {
		try {
			return imageStorage.store(EVIDENCE_FOLDER, bookingRef,
					cancelValidator.presentFiles(command.evidence()));
		}
		catch (ImageUploadException ex) {
			throw new ApiException(ErrorCode.UPLOAD_FAILED);
		}
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

	/**
	 * Asks SMP for a booking until it comes back with a reference no store already holds.
	 *
	 * <p><b>Why this loop exists.</b> {@code bookingRef} is uniquely indexed on both shards, and
	 * SBP's copy of a booking outlives the process that created it. A mock SMP numbers from a
	 * counter it keeps in memory, so a restart hands out references a previous run already used —
	 * and the save died on a duplicate key, which is a 500 for a customer who did nothing wrong.
	 * {@code existsByBookingRef} was written for exactly this check; nothing had been calling it.
	 *
	 * <p>The check is not atomic with the save that follows, so the unique index remains the real
	 * guarantee. It narrows the window from "every restart" to "two customers booking in the same
	 * instant on a reference that also happens to be stale", and the {@link DuplicateKeyException}
	 * catch below turns even that into a retryable answer rather than a fault.
	 *
	 * @throws ApiException 503 SMP_UNAVAILABLE once the attempts are spent — §7.1's own wording
	 *         fits: nothing was created, nothing is half-written, and the customer may retry
	 */
	private SmpBookingDto createWithUnusedReference(String customerId, String serviceId) {
		for (int attempt = 1; attempt <= MAX_REFERENCE_ATTEMPTS; attempt++) {
			SmpBookingDto ack = smpBookingClient.createBooking(customerId, serviceId);
			if (!bookingRepository.existsByBookingRef(ack.bookingRef())) {
				return ack;
			}
			log.warn("SMP returned booking reference {}, which is already in use. Attempt {} of {}.",
					ack.bookingRef(), attempt, MAX_REFERENCE_ATTEMPTS);
		}
		throw new ApiException(ErrorCode.SMP_UNAVAILABLE);
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
