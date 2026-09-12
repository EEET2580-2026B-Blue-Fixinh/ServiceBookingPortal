package com.fixinh.sbp.modules.integration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.modules.booking.dto.internal.BookingProjectionRefresh;
import com.fixinh.sbp.modules.booking.interfaces.internal.BookingInternalService;
import com.fixinh.sbp.modules.booking.interfaces.internal.BookingProjectionView;
import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.booking.model.TechnicianRef;
import com.fixinh.sbp.modules.customer.interfaces.external.CustomerExternalService;
import com.fixinh.sbp.modules.integration.dto.external.BookingStatusChangedEvent;
import com.fixinh.sbp.modules.integration.dto.external.BookingStatusWebhookDto;
import com.fixinh.sbp.modules.integration.dto.external.BookingStatusWebhookDto.BookingPayload;
import com.fixinh.sbp.modules.integration.dto.external.EventReceiptDto;
import com.fixinh.sbp.modules.integration.model.EventDirection;
import com.fixinh.sbp.modules.integration.model.IntegrationEvent;
import com.fixinh.sbp.modules.integration.repository.IntegrationEventRepository;

/**
 * §11.7 end to end within the module: what gets refreshed, what gets published, in which
 * order, and what a replay does. The ordering assertions are the ones that matter most —
 * they are the code's only guarantee of §11.7's own promise about the toast and the screen
 * behind it.
 */
class IntegrationServiceImplTest {

	private static final String EVENT_ID = "evt-8f2c14a9";
	private static final String BOOKING_REF = "ORD-2026-1024";
	private static final String CUSTOMER_ID = "66f1a2b3c4d5e6f708192a3b";
	private static final Instant OCCURRED_AT = Instant.parse("2026-08-11T03:19:58Z");
	private static final Instant VISIT_AT = Instant.parse("2026-08-24T11:00:00Z");
	private static final TechnicianRef TECHNICIAN = new TechnicianRef("TEC-014", "John Tran");

	private CustomerExternalService customerService;
	private BookingInternalService bookingService;
	private IntegrationEventRepository eventRepository;
	private ApplicationEventPublisher eventPublisher;
	private IntegrationServiceImpl service;

	private final List<IntegrationEvent> inserted = new ArrayList<>();
	private final List<IntegrationEvent> saved = new ArrayList<>();

	@BeforeEach
	void setUp() {
		customerService = mock(CustomerExternalService.class);
		bookingService = mock(BookingInternalService.class);
		eventRepository = mock(IntegrationEventRepository.class);
		eventPublisher = mock(ApplicationEventPublisher.class);

		when(customerService.findHomeRegion(CUSTOMER_ID)).thenReturn(Optional.of(Region.SOUTH));
		when(eventRepository.findInbound(any(), any())).thenReturn(Optional.empty());
		when(eventRepository.insert(any())).thenAnswer(invocation -> {
			IntegrationEvent event = invocation.getArgument(0);
			inserted.add(event);
			return event;
		});
		when(eventRepository.save(any())).thenAnswer(invocation -> {
			IntegrationEvent event = invocation.getArgument(0);
			saved.add(event);
			return event;
		});
		when(bookingService.refreshProjection(any(), any()))
				.thenReturn(view(BookingStatus.SCHEDULED, VISIT_AT, 120, List.of(TECHNICIAN)));

		service = new IntegrationServiceImpl(new BookingEventValidator(), customerService,
				bookingService, eventRepository, eventPublisher);
	}

	// --- A first delivery -------------------------------------------------------------------

	@Test
	void acceptsAnEventAndReportsItHandled() {
		EventReceiptDto receipt = service.receiveBookingEvent(scheduledEvent());

		assertEquals(EVENT_ID, receipt.eventId());
		assertTrue(receipt.accepted());
		assertFalse(receipt.duplicate());
	}

	@Test
	void refreshesTheProjectionFromThePayload() {
		service.receiveBookingEvent(scheduledEvent());

		ArgumentCaptor<BookingProjectionRefresh> captor =
				ArgumentCaptor.forClass(BookingProjectionRefresh.class);
		verify(bookingService).refreshProjection(eq(BOOKING_REF), captor.capture());

		BookingProjectionRefresh refresh = captor.getValue();
		assertEquals(BookingStatus.SCHEDULED, refresh.status());
		assertEquals(VISIT_AT, refresh.scheduledStartAt());
		assertEquals(120, refresh.estimatedDurationMinutes());
		assertEquals(List.of(TECHNICIAN), refresh.technicians());
		assertEquals(OCCURRED_AT, refresh.observedAt(), "SMP's clock, for the staleness check");
	}

	@Test
	void refreshesBeforeItPublishes() {
		// §11.7's promise, and the whole reason the two calls are in this order: a listener
		// that ran first would notify a customer about a booking SBP could not yet display.
		service.receiveBookingEvent(scheduledEvent());

		InOrder refreshThenPublish = inOrder(bookingService, eventPublisher);
		refreshThenPublish.verify(bookingService).refreshProjection(any(), any());
		refreshThenPublish.verify(eventPublisher).publishEvent(any(Object.class));
	}

	@Test
	void marksTheEventHandledOnlyAfterPublishing() {
		// Marked before, a retry of a delivery whose notification failed would be answered
		// with duplicate: true and the notification would be lost for good.
		service.receiveBookingEvent(scheduledEvent());

		InOrder publishThenMark = inOrder(eventPublisher, eventRepository);
		publishThenMark.verify(eventPublisher).publishEvent(any(Object.class));
		publishThenMark.verify(eventRepository).save(any());

		assertEquals(1, saved.size());
		assertTrue(saved.get(0).isHandled());
		assertNotNull(saved.get(0).getHandledAt());
	}

	@Test
	void recordsTheEventBeforeActingOnIt() {
		service.receiveBookingEvent(scheduledEvent());

		InOrder recordThenRefresh = inOrder(eventRepository, bookingService);
		recordThenRefresh.verify(eventRepository).insert(any());
		recordThenRefresh.verify(bookingService).refreshProjection(any(), any());

		IntegrationEvent recorded = inserted.get(0);
		assertEquals(EVENT_ID, recorded.getEventId());
		assertEquals(EventDirection.IN, recorded.getDirection());
		assertEquals("BOOKING_STATUS_CHANGED", recorded.getEventType());
		assertEquals(Region.SOUTH, recorded.getHomeRegion());
		assertEquals(OCCURRED_AT, recorded.getOccurredAt());
	}

	@Test
	void publishesTheRefreshedStateNotThePayload() {
		// On a replay of an older change the projection keeps the newer state, and a listener
		// told the payload's state would describe the booking as it no longer is.
		when(bookingService.refreshProjection(any(), any()))
				.thenReturn(view(BookingStatus.WORKING, VISIT_AT, 120, List.of(TECHNICIAN)));

		service.receiveBookingEvent(scheduledEvent());

		ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(published.capture());

		BookingStatusChangedEvent internal = (BookingStatusChangedEvent) published.getValue();
		assertEquals(BookingStatus.WORKING, internal.status(), "what was stored, not what was sent");
		assertEquals(EVENT_ID, internal.eventId());
		assertEquals(BOOKING_REF, internal.bookingRef());
		assertEquals(CUSTOMER_ID, internal.customerId());
		assertEquals(OCCURRED_AT, internal.occurredAt());
	}

	// --- Replays ----------------------------------------------------------------------------

	@Test
	void answersAHandledEventAsADuplicateAndChangesNothing() {
		IntegrationEvent handled = recorded();
		handled.markHandled(OCCURRED_AT.plusSeconds(1));
		when(eventRepository.findInbound(Region.SOUTH, EVENT_ID)).thenReturn(Optional.of(handled));

		EventReceiptDto receipt = service.receiveBookingEvent(scheduledEvent());

		assertTrue(receipt.duplicate());
		verifyNoInteractions(bookingService, eventPublisher);
		verify(eventRepository, never()).insert(any());
		verify(eventRepository, never()).save(any());
	}

	@Test
	void reRunsAnEventThatWasRecordedButNeverFinished() {
		// The process died, or a listener threw and SMP was told 5xx. The work is still owed,
		// so the retry does it -- and is not brushed off with a duplicate receipt.
		when(eventRepository.findInbound(Region.SOUTH, EVENT_ID))
				.thenReturn(Optional.of(recorded()));

		EventReceiptDto receipt = service.receiveBookingEvent(scheduledEvent());

		assertFalse(receipt.duplicate());
		verify(bookingService).refreshProjection(any(), any());
		verify(eventPublisher).publishEvent(any(Object.class));
		verify(eventRepository, never()).insert(any());
		assertTrue(saved.get(0).isHandled());
	}

	@Test
	void standsDownWhenTheSameEventIsAlreadyBeingHandled() {
		// Two deliveries racing. The insert loses, and doing the work twice concurrently is
		// what the eventId key exists to prevent, so this one does nothing.
		doThrow(new DuplicateKeyException("_id")).when(eventRepository).insert(any());

		EventReceiptDto receipt = service.receiveBookingEvent(scheduledEvent());

		assertTrue(receipt.duplicate());
		verifyNoInteractions(bookingService, eventPublisher);
	}

	// --- Refusals ---------------------------------------------------------------------------

	@Test
	void refusesAnUnknownCustomerWithoutTouchingAnything() {
		when(customerService.findHomeRegion(CUSTOMER_ID)).thenReturn(Optional.empty());

		ApiException refused = assertThrows(ApiException.class,
				() -> service.receiveBookingEvent(scheduledEvent()));

		assertEquals(ErrorCode.CUSTOMER_NOT_FOUND, refused.getErrorCode());
		verifyNoInteractions(bookingService, eventPublisher);
		verify(eventRepository, never()).insert(any());
	}

	@Test
	void refusesAMalformedEventBeforeLookingAnythingUp() {
		ApiException refused = assertThrows(ApiException.class, () -> service.receiveBookingEvent(
				new BookingStatusWebhookDto(null, "BOOKING_STATUS_CHANGED", OCCURRED_AT,
						BOOKING_REF, CUSTOMER_ID, payload())));

		assertEquals(ErrorCode.EVENT_MALFORMED, refused.getErrorCode());
		verifyNoInteractions(customerService, bookingService, eventPublisher, eventRepository);
	}

	@Test
	void refusesAnEventWhoseBookingBelongsToSomeoneElse() {
		// A notification sent to the wrong customer is a privacy fault, not a glitch. The
		// booking's own owner decides who hears about it.
		when(bookingService.refreshProjection(any(), any())).thenReturn(
				new BookingProjectionView(BOOKING_REF, "someone-else", BookingStatus.SCHEDULED,
						VISIT_AT, 120, List.of(TECHNICIAN), OCCURRED_AT, true));

		ApiException refused = assertThrows(ApiException.class,
				() -> service.receiveBookingEvent(scheduledEvent()));

		assertEquals(ErrorCode.EVENT_MALFORMED, refused.getErrorCode());
		verifyNoInteractions(eventPublisher);
		verify(eventRepository, never()).save(any());
	}

	@Test
	void leavesTheEventUnmarkedWhenAListenerFails() {
		// Unmarked is what makes SMP's retry meaningful: the notification is still owed, and
		// the 5xx is what asks for it to be delivered again.
		doThrow(new IllegalStateException("no store")).when(eventPublisher)
				.publishEvent(any(Object.class));

		assertThrows(IllegalStateException.class,
				() -> service.receiveBookingEvent(scheduledEvent()));

		verify(eventRepository, never()).save(any());
		assertFalse(inserted.get(0).isHandled());
	}

	private static BookingProjectionView view(BookingStatus status, Instant startAt,
			Integer minutes, List<TechnicianRef> technicians) {
		return new BookingProjectionView(BOOKING_REF, CUSTOMER_ID, status, startAt, minutes,
				technicians, OCCURRED_AT, true);
	}

	private static IntegrationEvent recorded() {
		return IntegrationEvent.builder()
				.eventId(EVENT_ID)
				.direction(EventDirection.IN)
				.eventType("BOOKING_STATUS_CHANGED")
				.bookingRef(BOOKING_REF)
				.customerId(CUSTOMER_ID)
				.occurredAt(OCCURRED_AT)
				.receivedAt(OCCURRED_AT.plusSeconds(2))
				.homeRegion(Region.SOUTH)
				.build();
	}

	private static BookingPayload payload() {
		return new BookingPayload("SCHEDULED", VISIT_AT, 120, List.of(TECHNICIAN),
				null, null, null, null, null);
	}

	private static BookingStatusWebhookDto scheduledEvent() {
		return new BookingStatusWebhookDto(EVENT_ID, "BOOKING_STATUS_CHANGED", OCCURRED_AT,
				BOOKING_REF, CUSTOMER_ID, payload());
	}
}
