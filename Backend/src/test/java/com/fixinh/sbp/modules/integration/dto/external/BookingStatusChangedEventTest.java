package com.fixinh.sbp.modules.integration.dto.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fixinh.sbp.modules.booking.model.BookingStatus;

/**
 * The event is a contract between two modules, so what it refuses is as much a part of it as
 * what it carries. Every rule here is one §11.7 already imposes on the wire; the record just
 * makes it impossible to forget in-process.
 */
class BookingStatusChangedEventTest {

	private static final String EVENT_ID = "evt-8f2c14a9";
	private static final String BOOKING_REF = "ORD-2026-1024";
	private static final String CUSTOMER_ID = "66f1a2b3c4d5e6f708192a3b";
	private static final Instant SCHEDULED_START = Instant.parse("2026-08-24T18:00:00Z");
	private static final Instant OCCURRED_AT = Instant.parse("2026-08-11T03:19:58Z");

	@Test
	void carriesTheScheduleWhenSmpHasFixedOne() {
		BookingStatusChangedEvent event = new BookingStatusChangedEvent(
				EVENT_ID, BOOKING_REF, CUSTOMER_ID, BookingStatus.SCHEDULED,
				SCHEDULED_START, 120, OCCURRED_AT);

		assertEquals(EVENT_ID, event.eventId());
		assertEquals(BOOKING_REF, event.bookingRef());
		assertEquals(CUSTOMER_ID, event.customerId());
		assertEquals(BookingStatus.SCHEDULED, event.status());
		assertEquals(SCHEDULED_START, event.scheduledStartAt());
		assertEquals(120, event.estimatedDurationMinutes());
		assertEquals(OCCURRED_AT, event.occurredAt());
	}

	@Test
	void acceptsEveryStatusWithoutASchedule() {
		// A confirmation has no visit time yet, and a cancellation never will. The schedule
		// fields are optional for every status, not only the ones that precede scheduling,
		// because SMP's payload decides what is present and this record does not second-guess
		// it.
		for (BookingStatus status : BookingStatus.values()) {
			BookingStatusChangedEvent event = new BookingStatusChangedEvent(
					EVENT_ID, BOOKING_REF, CUSTOMER_ID, status, null, null, OCCURRED_AT);

			assertEquals(status, event.status());
			assertNull(event.scheduledStartAt());
			assertNull(event.estimatedDurationMinutes());
		}
	}

	@Test
	void refusesAnEventWithoutAnId() {
		// The id is what every consumer keys its replay handling on. An event without one
		// could be neither deduplicated nor retried, so it is not allowed to exist.
		for (String eventId : new String[] {null, "", "   "}) {
			IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
					() -> new BookingStatusChangedEvent(eventId, BOOKING_REF, CUSTOMER_ID,
							BookingStatus.CONFIRMED, null, null, OCCURRED_AT));

			assertTrue(refused.getMessage().contains("eventId"));
		}
	}

	@Test
	void refusesAnEventWithoutABooking() {
		for (String bookingRef : new String[] {null, "", "   "}) {
			IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
					() -> new BookingStatusChangedEvent(EVENT_ID, bookingRef, CUSTOMER_ID,
							BookingStatus.CONFIRMED, null, null, OCCURRED_AT));

			assertTrue(refused.getMessage().contains("bookingRef"));
		}
	}

	@Test
	void refusesAnEventWithoutACustomer() {
		// No customer means no recipient and no regional store: nothing downstream can act.
		for (String customerId : new String[] {null, "", "   "}) {
			IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
					() -> new BookingStatusChangedEvent(EVENT_ID, BOOKING_REF, customerId,
							BookingStatus.CONFIRMED, null, null, OCCURRED_AT));

			assertTrue(refused.getMessage().contains("customerId"));
		}
	}

	@Test
	void refusesAnEventWithoutAStatus() {
		assertThrows(IllegalArgumentException.class,
				() -> new BookingStatusChangedEvent(EVENT_ID, BOOKING_REF, CUSTOMER_ID,
						null, null, null, OCCURRED_AT));
	}

	@Test
	void refusesAnEventWithoutATime() {
		assertThrows(IllegalArgumentException.class,
				() -> new BookingStatusChangedEvent(EVENT_ID, BOOKING_REF, CUSTOMER_ID,
						BookingStatus.CONFIRMED, null, null, null));
	}
}
