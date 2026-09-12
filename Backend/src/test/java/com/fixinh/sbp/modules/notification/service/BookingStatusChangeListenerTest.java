package com.fixinh.sbp.modules.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.integration.dto.external.BookingStatusChangedEvent;
import com.fixinh.sbp.modules.notification.dto.internal.NotificationCreateCommand;
import com.fixinh.sbp.modules.notification.interfaces.internal.NotificationInternalService;
import com.fixinh.sbp.modules.notification.model.NotificationType;
import com.fixinh.sbp.modules.notification.model.RecipientType;

/**
 * The filter and the hand-off, with the service mocked and the composer real. What the
 * listener decides is <em>whether</em> to raise and <em>what to say</em>; whether a raise is
 * a replay is the service's decision and is tested there.
 */
class BookingStatusChangeListenerTest {

	private static final String EVENT_ID = "evt-8f2c14a9";
	private static final String BOOKING_REF = "ORD-2026-1024";
	private static final String CUSTOMER_ID = "66f1a2b3c4d5e6f708192a3b";
	private static final Instant OCCURRED_AT = Instant.parse("2026-08-11T03:19:58Z");

	private final NotificationInternalService service = mock(NotificationInternalService.class);
	private final BookingStatusChangeListener listener =
			new BookingStatusChangeListener(new BookingNotificationComposer(), service);

	@Test
	void raisesAConfirmationForACustomerFromTheEvent() {
		listener.onBookingStatusChanged(event(BookingStatus.CONFIRMED, null, null));

		NotificationCreateCommand raised = captureRaise();
		assertEquals(RecipientType.CUSTOMER, raised.recipientType());
		assertEquals(CUSTOMER_ID, raised.recipientIdentifier());
		assertEquals(NotificationType.BOOKING_CONFIRMED, raised.type());
		assertEquals("Your order ORD-2026-1024 has been confirmed.", raised.message());
		assertEquals(BOOKING_REF, raised.bookingRef());
		assertEquals(EVENT_ID, raised.sourceEventId(),
				"the id is what lets the service recognise a replay of this event");
	}

	@Test
	void raisesAScheduleWithTheVisitTime() {
		listener.onBookingStatusChanged(event(BookingStatus.SCHEDULED,
				Instant.parse("2026-08-24T11:00:00Z"), 120));

		NotificationCreateCommand raised = captureRaise();
		assertEquals(NotificationType.BOOKING_SCHEDULED, raised.type());
		assertEquals("Your order ORD-2026-1024 is scheduled for 24 Aug 2026, 18:00-20:00.",
				raised.message());
	}

	@Test
	void staysSilentForEveryOtherStatus() {
		// §11.7: the projection still refreshes, and that is all that happens. Six statuses,
		// checked by name rather than "everything that is not the two", so the list is the
		// same one Appendix A prints.
		EnumSet<BookingStatus> silent = EnumSet.of(
				BookingStatus.CREATED,
				BookingStatus.WORKING,
				BookingStatus.COMPLETED,
				BookingStatus.CANCELLED_BY_CUSTOMER,
				BookingStatus.TERMINATED_BY_CUSTOMER,
				BookingStatus.ABORTED_BY_TECHNICIAN);
		assertEquals(BookingStatus.values().length, silent.size() + 2,
				"every status is either silent or one of the two that notify");

		for (BookingStatus status : silent) {
			listener.onBookingStatusChanged(event(status, null, null));
		}

		verifyNoInteractions(service);
	}

	@Test
	void handsAReplayToTheServiceUnchanged() {
		// Recognising a repeat is the service's job, keyed on the id the listener passes
		// through. The listener does not keep state of its own -- there is one of it per
		// process, and a restart would forget everything it remembered.
		BookingStatusChangedEvent scheduled = event(BookingStatus.SCHEDULED,
				Instant.parse("2026-08-24T11:00:00Z"), 120);

		listener.onBookingStatusChanged(scheduled);
		listener.onBookingStatusChanged(scheduled);

		ArgumentCaptor<NotificationCreateCommand> commands =
				ArgumentCaptor.forClass(NotificationCreateCommand.class);
		verify(service, times(2)).raise(commands.capture());
		assertEquals(EVENT_ID, commands.getAllValues().get(0).sourceEventId());
		assertEquals(EVENT_ID, commands.getAllValues().get(1).sourceEventId());
	}

	@Test
	void letsAFailureReachTheInboundRequest() {
		// A 202 for a notification that was never saved would be a lie SMP could not undo.
		// Propagating turns it into a 5xx, a retry, and a second pass through this listener.
		when(service.raise(any())).thenThrow(new IllegalStateException("no store"));

		assertThrows(IllegalStateException.class, () -> listener.onBookingStatusChanged(
				event(BookingStatus.CONFIRMED, null, null)));
	}

	private NotificationCreateCommand captureRaise() {
		ArgumentCaptor<NotificationCreateCommand> captor =
				ArgumentCaptor.forClass(NotificationCreateCommand.class);
		verify(service).raise(captor.capture());
		return captor.getValue();
	}

	private static BookingStatusChangedEvent event(BookingStatus status, Instant startAt,
			Integer minutes) {
		return new BookingStatusChangedEvent(EVENT_ID, BOOKING_REF, CUSTOMER_ID, status,
				startAt, minutes, OCCURRED_AT);
	}
}
