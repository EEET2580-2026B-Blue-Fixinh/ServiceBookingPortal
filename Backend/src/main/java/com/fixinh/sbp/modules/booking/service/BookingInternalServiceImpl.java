package com.fixinh.sbp.modules.booking.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.modules.booking.dto.internal.BookingProjectionRefresh;
import com.fixinh.sbp.modules.booking.dto.internal.BookingRatingView;
import com.fixinh.sbp.modules.booking.interfaces.internal.BookingInternalService;
import com.fixinh.sbp.modules.booking.interfaces.internal.BookingProjectionView;
import com.fixinh.sbp.modules.booking.model.Bill;
import com.fixinh.sbp.modules.booking.model.ServiceBooking;
import com.fixinh.sbp.modules.booking.model.TechnicianRef;
import com.fixinh.sbp.modules.booking.repository.ServiceBookingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
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

	@Override
	public BookingProjectionView refreshProjection(String bookingRef,
			BookingProjectionRefresh refresh) {

		ServiceBooking booking = repository.findByBookingRef(bookingRef)
				.orElseThrow(() -> new ApiException(ErrorCode.BOOKING_NOT_FOUND));

		if (isStale(booking, refresh)) {
			// Two changes crossing on the wire, or a retry of an old one arriving after a
			// newer change was already applied. Writing this would move the booking
			// backwards: a customer who has been told their booking is scheduled would find
			// it merely confirmed again. The event is still handled -- there is simply
			// nothing about it left to apply.
			log.info("Ignored a stale refresh for {}: SMP observed it at {}, and the "
					+ "projection already reflects {}", bookingRef, refresh.observedAt(),
					booking.getLastSyncedAt());
			return viewOf(booking, false);
		}

		apply(booking, refresh);
		ServiceBooking refreshed = repository.save(booking);

		log.debug("Refreshed {} to {} (observed {})", bookingRef, refreshed.getStatus(),
				refresh.observedAt());

		return viewOf(refreshed, true);
	}

	/**
	 * Whether this refresh describes a moment SBP has already moved past.
	 *
	 * <p>Compared on SMP's own clock, since both values come from it. Equal timestamps are not
	 * stale: SMP may report two changes within the same second, and refusing the second would
	 * lose it — applying a refresh twice is harmless, because a merge of the same values is
	 * the same state.
	 */
	private static boolean isStale(ServiceBooking booking, BookingProjectionRefresh refresh) {
		Instant lastSynced = booking.getLastSyncedAt();
		return lastSynced != null && refresh.observedAt().isBefore(lastSynced);
	}

	/**
	 * Writes what SMP sent, and only what SMP sent.
	 *
	 * <p><b>Absence is not erasure.</b> §11.7's payload carries the fields relevant to one
	 * change, so a WORKING event that mentions no technicians is not SMP saying the
	 * technicians are gone. Blanking them would break Req 3.3.S5 on the very screen a
	 * scheduled-booking notification leads to, and it would do so one status change after the
	 * notification was sent -- the hardest kind of fault to trace back to here.
	 *
	 * <p>The status is the exception: it is always present, so it is always written.
	 */
	private static void apply(ServiceBooking booking, BookingProjectionRefresh refresh) {
		booking.setStatus(refresh.status());

		if (refresh.scheduledStartAt() != null) {
			booking.setScheduledStartAt(refresh.scheduledStartAt());
		}
		if (refresh.estimatedDurationMinutes() != null) {
			booking.setEstimatedDurationMinutes(refresh.estimatedDurationMinutes());
		}
		if (refresh.technicians() != null && !refresh.technicians().isEmpty()) {
			// Copied, not referenced: the caller's list came from a deserialised payload and
			// nothing here should leave a document holding something another layer can still
			// change.
			booking.setTechnicians(List.copyOf(refresh.technicians()));
		}
		if (refresh.bill() != null) {
			booking.setBill(billFrom(refresh.bill(), booking.getBookingRef()));
		}

		booking.setLastSyncedAt(refresh.observedAt());
	}

	/**
	 * SBP's own bill, from SMP's numbers.
	 *
	 * <p>§12.3 asks SBP to recompute the total rather than trust the one SMP sends, so the
	 * stored {@link Bill} derives it from the two components as Req 3.3.S6 requires. SMP's
	 * total is compared against that and never stored; a disagreement is logged and the
	 * booking keeps SBP's arithmetic, because a total that does not equal the sum of its parts
	 * is the one number a customer can check for themselves.
	 */
	private static Bill billFrom(BookingProjectionRefresh.BillRefresh sent, String bookingRef) {
		Bill bill = new Bill(sent.serviceFee(), sent.materialCost(), sent.billImageUrl(),
				sent.issuedAt());

		if (sent.reportedTotalCost() != null && sent.reportedTotalCost() != bill.totalCost()) {
			log.warn("Bill total for {} disagrees with SMP: SMP reported {}, service fee {} plus "
					+ "material cost {} is {}. Stored SBP's own figure",
					bookingRef, sent.reportedTotalCost(), sent.serviceFee(), sent.materialCost(),
					bill.totalCost());
		}
		return bill;
	}

	private static BookingProjectionView viewOf(ServiceBooking booking, boolean applied) {
		List<TechnicianRef> assigned = booking.getTechnicians();
		return new BookingProjectionView(
				booking.getBookingRef(),
				booking.getCustomerId(),
				booking.getStatus(),
				booking.getScheduledStartAt(),
				booking.getEstimatedDurationMinutes(),
				assigned == null ? List.of() : List.copyOf(assigned),
				booking.getLastSyncedAt(),
				applied);
	}
}