package com.fixinh.sbp.modules.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.booking.dto.internal.BookingProjectionRefresh;
import com.fixinh.sbp.modules.booking.dto.internal.BookingProjectionRefresh.BillRefresh;
import com.fixinh.sbp.modules.booking.interfaces.internal.BookingProjectionView;
import com.fixinh.sbp.modules.booking.model.Bill;
import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.booking.model.ServiceBooking;
import com.fixinh.sbp.modules.booking.model.TechnicianRef;
import com.fixinh.sbp.modules.booking.repository.ServiceBookingRepository;

/**
 * Refreshing the projection from what SMP reports. The recurring theme is that a field SMP
 * did not send is one SBP keeps — the alternative silently empties a scheduled booking one
 * status change after the customer was told about it.
 */
class BookingInternalServiceImplRefreshTest {

	private static final String BOOKING_REF = "ORD-2026-1024";
	private static final String CUSTOMER_ID = "66f1a2b3c4d5e6f708192a3b";
	private static final Instant CREATED_AT = Instant.parse("2026-08-10T09:40:12Z");
	private static final Instant CONFIRMED_AT = Instant.parse("2026-08-11T03:19:58Z");
	private static final Instant SCHEDULED_AT = Instant.parse("2026-08-12T02:00:00Z");
	private static final Instant VISIT_AT = Instant.parse("2026-08-24T11:00:00Z");
	private static final TechnicianRef TECHNICIAN = new TechnicianRef("TEC-014", "John Tran");

	private ServiceBookingRepository repository;
	private BookingInternalServiceImpl service;

	@BeforeEach
	void setUp() {
		repository = mock(ServiceBookingRepository.class);
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		service = new BookingInternalServiceImpl(repository);
	}

	// --- Applying what SMP sent --------------------------------------------------------------

	@Test
	void appliesTheStatusAndStampsSmpsClock() {
		ServiceBooking booking = created();
		when(repository.findByBookingRef(BOOKING_REF)).thenReturn(Optional.of(booking));

		BookingProjectionView view = service.refreshProjection(BOOKING_REF,
				refresh(BookingStatus.CONFIRMED, CONFIRMED_AT, null, null, null, null));

		assertTrue(view.applied());
		assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
		assertEquals(CONFIRMED_AT, booking.getLastSyncedAt(),
				"SMP's occurredAt, not the moment SBP wrote it");
		verify(repository).save(booking);
	}

	@Test
	void appliesTheScheduleAndTheTechnicians() {
		ServiceBooking booking = created();
		when(repository.findByBookingRef(BOOKING_REF)).thenReturn(Optional.of(booking));

		BookingProjectionView view = service.refreshProjection(BOOKING_REF,
				refresh(BookingStatus.SCHEDULED, SCHEDULED_AT, VISIT_AT, 120,
						List.of(TECHNICIAN), null));

		assertEquals(VISIT_AT, booking.getScheduledStartAt());
		assertEquals(120, booking.getEstimatedDurationMinutes());
		assertEquals(List.of(TECHNICIAN), booking.getTechnicians(),
				"Req 3.3.S5 needs this on the screen the notification opens");
		assertEquals(List.of(TECHNICIAN), view.technicians());
	}

	@Test
	void reportsNoTechniciansAsAnEmptyListNotNull() {
		ServiceBooking booking = created();
		when(repository.findByBookingRef(BOOKING_REF)).thenReturn(Optional.of(booking));

		BookingProjectionView view = service.refreshProjection(BOOKING_REF,
				refresh(BookingStatus.CONFIRMED, CONFIRMED_AT, null, null, null, null));

		assertNotNull(view.technicians());
		assertTrue(view.technicians().isEmpty(),
				"a caller should not have to tell 'none' from 'not sent'");
	}

	// --- Keeping what SMP did not send -------------------------------------------------------

	@Test
	void keepsTheScheduleWhenALaterEventOmitsIt() {
		// The WORKING event of §11.7's own example mentions no schedule and no technicians.
		// Reading that as an erasure is the fault this test exists to prevent.
		ServiceBooking booking = created();
		booking.setStatus(BookingStatus.SCHEDULED);
		booking.setScheduledStartAt(VISIT_AT);
		booking.setEstimatedDurationMinutes(120);
		booking.setTechnicians(List.of(TECHNICIAN));
		booking.setLastSyncedAt(SCHEDULED_AT);
		when(repository.findByBookingRef(BOOKING_REF)).thenReturn(Optional.of(booking));

		service.refreshProjection(BOOKING_REF, refresh(BookingStatus.WORKING,
				SCHEDULED_AT.plusSeconds(3600), null, null, null, null));

		assertEquals(BookingStatus.WORKING, booking.getStatus());
		assertEquals(VISIT_AT, booking.getScheduledStartAt());
		assertEquals(120, booking.getEstimatedDurationMinutes());
		assertEquals(List.of(TECHNICIAN), booking.getTechnicians());
	}

	@Test
	void keepsTheBillWhenALaterEventOmitsIt() {
		ServiceBooking booking = created();
		Bill issued = new Bill(300_000L, 150_000L, "https://res.cloudinary.com/bill.jpg",
				Instant.parse("2026-08-24T13:30:00Z"));
		booking.setBill(issued);
		booking.setLastSyncedAt(SCHEDULED_AT);
		when(repository.findByBookingRef(BOOKING_REF)).thenReturn(Optional.of(booking));

		service.refreshProjection(BOOKING_REF, refresh(BookingStatus.COMPLETED,
				SCHEDULED_AT.plusSeconds(7200), null, null, null, null));

		assertEquals(issued, booking.getBill());
	}

	@Test
	void treatsAnEmptyTechnicianListAsNothingSent() {
		// An empty array and an absent field mean the same thing on the wire, and neither is
		// SMP saying the technicians have gone away.
		ServiceBooking booking = created();
		booking.setTechnicians(List.of(TECHNICIAN));
		when(repository.findByBookingRef(BOOKING_REF)).thenReturn(Optional.of(booking));

		service.refreshProjection(BOOKING_REF, refresh(BookingStatus.WORKING, CONFIRMED_AT,
				null, null, List.of(), null));

		assertEquals(List.of(TECHNICIAN), booking.getTechnicians());
	}

	// --- The bill, recomputed ----------------------------------------------------------------

	@Test
	void storesItsOwnTotalRatherThanSmps() {
		ServiceBooking booking = created();
		when(repository.findByBookingRef(BOOKING_REF)).thenReturn(Optional.of(booking));

		service.refreshProjection(BOOKING_REF, refresh(BookingStatus.COMPLETED, CONFIRMED_AT,
				null, null, null,
				new BillRefresh(300_000L, 150_000L, null, CONFIRMED_AT, 999_000L)));

		// §12.3: recompute rather than trust. Req 3.3.S6's sum is what the customer can check
		// for themselves, so it is what is stored -- SMP's figure only earns a warning.
		assertEquals(450_000L, booking.getBill().totalCost());
		assertEquals(300_000L, booking.getBill().serviceFee());
		assertEquals(150_000L, booking.getBill().materialCost());
	}

	@Test
	void acceptsABillWithNoReportedTotal() {
		ServiceBooking booking = created();
		when(repository.findByBookingRef(BOOKING_REF)).thenReturn(Optional.of(booking));

		service.refreshProjection(BOOKING_REF, refresh(BookingStatus.COMPLETED, CONFIRMED_AT,
				null, null, null, new BillRefresh(300_000L, 150_000L, null, CONFIRMED_AT, null)));

		assertEquals(450_000L, booking.getBill().totalCost());
	}

	// --- Out of order, and unknown -----------------------------------------------------------

	@Test
	void ignoresARefreshOlderThanWhatIsAlreadyHeld() {
		// A retry of the CONFIRMED event arriving after SCHEDULED was applied. Writing it
		// would tell a customer their scheduled booking is merely confirmed again.
		ServiceBooking booking = created();
		booking.setStatus(BookingStatus.SCHEDULED);
		booking.setScheduledStartAt(VISIT_AT);
		booking.setLastSyncedAt(SCHEDULED_AT);
		when(repository.findByBookingRef(BOOKING_REF)).thenReturn(Optional.of(booking));

		BookingProjectionView view = service.refreshProjection(BOOKING_REF,
				refresh(BookingStatus.CONFIRMED, CONFIRMED_AT, null, null, null, null));

		assertFalse(view.applied());
		assertEquals(BookingStatus.SCHEDULED, view.status(), "the state that was kept");
		assertEquals(BookingStatus.SCHEDULED, booking.getStatus());
		assertEquals(SCHEDULED_AT, booking.getLastSyncedAt());
		verify(repository, never()).save(any());
	}

	@Test
	void appliesARefreshObservedAtTheSameInstant() {
		// Two changes inside one second is SMP's business, not a reason to lose the second.
		// Applying the same values twice is the same state, so equal is not stale.
		ServiceBooking booking = created();
		booking.setLastSyncedAt(CONFIRMED_AT);
		when(repository.findByBookingRef(BOOKING_REF)).thenReturn(Optional.of(booking));

		BookingProjectionView view = service.refreshProjection(BOOKING_REF,
				refresh(BookingStatus.SCHEDULED, CONFIRMED_AT, VISIT_AT, 120,
						List.of(TECHNICIAN), null));

		assertTrue(view.applied());
		assertEquals(BookingStatus.SCHEDULED, booking.getStatus());
	}

	@Test
	void refusesToInventAProjectionForAnUnknownBooking() {
		// Every booking SBP knows was created through SBP. An unknown reference means the two
		// systems disagree about something a silent insert would hide.
		when(repository.findByBookingRef("ORD-2026-9999")).thenReturn(Optional.empty());

		ApiException refused = assertThrows(ApiException.class, () -> service.refreshProjection(
				"ORD-2026-9999", refresh(BookingStatus.CONFIRMED, CONFIRMED_AT, null, null,
						null, null)));

		assertEquals(ErrorCode.BOOKING_NOT_FOUND, refused.getErrorCode());
		verify(repository, never()).save(any());
	}

	@Test
	void refusesARefreshWithNoStatusOrNoObservationTime() {
		assertThrows(IllegalArgumentException.class, () -> new BookingProjectionRefresh(
				null, null, null, null, null, CONFIRMED_AT));
		assertThrows(IllegalArgumentException.class, () -> new BookingProjectionRefresh(
				BookingStatus.CONFIRMED, null, null, null, null, null));
	}

	private static BookingProjectionRefresh refresh(BookingStatus status, Instant observedAt,
			Instant startAt, Integer minutes, List<TechnicianRef> technicians, BillRefresh bill) {
		return new BookingProjectionRefresh(status, startAt, minutes, technicians, bill, observedAt);
	}

	private static ServiceBooking created() {
		return ServiceBooking.builder()
				.id("507f1f77bcf86cd799439011")
				.bookingRef(BOOKING_REF)
				.customerId(CUSTOMER_ID)
				.serviceId("SVC-PLUMB-01")
				.serviceName("Plumbing Repair")
				.status(BookingStatus.CREATED)
				.createdAt(CREATED_AT)
				.homeRegion(Region.SOUTH)
				.build();
	}
}
