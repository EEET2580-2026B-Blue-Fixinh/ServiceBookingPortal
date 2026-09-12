package com.fixinh.sbp.modules.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.integration.dto.external.BookingStatusChangedEvent;
import com.fixinh.sbp.modules.notification.model.NotificationType;

/**
 * The exact sentences, because they are what the customer reads and §9.1 prints one of them
 * verbatim. Instants are given in UTC, as SMP sends them, and expected in Ho Chi Minh time,
 * as the customer reads them — the conversion is the point of half these cases.
 */
class BookingNotificationComposerTest {

	private static final String BOOKING_REF = "ORD-2026-1024";

	/** 18:00 in Ho Chi Minh City, which is UTC+7. */
	private static final Instant SIX_PM_LOCAL = Instant.parse("2026-08-24T11:00:00Z");

	private final BookingNotificationComposer composer = new BookingNotificationComposer();

	@Test
	void writesTheScheduledVisitAsSection91Does() {
		String message = composer.compose(NotificationType.BOOKING_SCHEDULED,
				scheduled(SIX_PM_LOCAL, 120));

		assertEquals("Your order ORD-2026-1024 is scheduled for 24 Aug 2026, 18:00-20:00.",
				message);
	}

	@Test
	void convertsFromUtcToTheCustomersClock() {
		// §9.1's example instant, taken literally, is 18:00 UTC -- which is one in the
		// morning of the next day for the customer. The customer's clock wins: the booking
		// screen this notification opens shows the same conversion.
		String message = composer.compose(NotificationType.BOOKING_SCHEDULED,
				scheduled(Instant.parse("2026-08-24T18:00:00Z"), 120));

		assertEquals("Your order ORD-2026-1024 is scheduled for 25 Aug 2026, 01:00-03:00.",
				message);
	}

	@Test
	void datesTheEndWhenTheVisitRunsPastMidnight() {
		// 23:00 local plus two hours. Written as "23:00-01:00" the window would appear to end
		// before it began.
		String message = composer.compose(NotificationType.BOOKING_SCHEDULED,
				scheduled(Instant.parse("2026-08-24T16:00:00Z"), 120));

		assertEquals(
				"Your order ORD-2026-1024 is scheduled for 24 Aug 2026, 23:00 - 25 Aug 2026, 01:00.",
				message);
	}

	@Test
	void namesOnlyTheStartWhenThereIsNoEstimate() {
		assertEquals("Your order ORD-2026-1024 is scheduled for 24 Aug 2026, 18:00.",
				composer.compose(NotificationType.BOOKING_SCHEDULED, scheduled(SIX_PM_LOCAL, null)));
	}

	@Test
	void treatsAnEstimateOfNothingAsNoEstimate() {
		// A zero or negative duration is a payload defect. Rendering "18:00-18:00" would pass
		// it on to the customer; naming the start alone is the honest reading.
		assertEquals("Your order ORD-2026-1024 is scheduled for 24 Aug 2026, 18:00.",
				composer.compose(NotificationType.BOOKING_SCHEDULED, scheduled(SIX_PM_LOCAL, 0)));
		assertEquals("Your order ORD-2026-1024 is scheduled for 24 Aug 2026, 18:00.",
				composer.compose(NotificationType.BOOKING_SCHEDULED, scheduled(SIX_PM_LOCAL, -30)));
	}

	@Test
	void saysLessRatherThanFailingWhenTheVisitTimeIsMissing() {
		// The projection is already refreshed by the time this runs. Failing here would lose
		// the notification over a field the customer can read from the booking anyway.
		assertEquals("Your order ORD-2026-1024 has been scheduled.",
				composer.compose(NotificationType.BOOKING_SCHEDULED, scheduled(null, null)));
	}

	@Test
	void writesAConfirmationWithoutATime() {
		BookingStatusChangedEvent confirmed = new BookingStatusChangedEvent(
				"evt-1", BOOKING_REF, "66f1a2b3c4d5e6f708192a3b", BookingStatus.CONFIRMED,
				null, null, Instant.parse("2026-08-11T03:19:58Z"));

		assertEquals("Your order ORD-2026-1024 has been confirmed.",
				composer.compose(NotificationType.BOOKING_CONFIRMED, confirmed));
	}

	@Test
	void refusesToWriteAStaffMessage() {
		// That one has its own composer, and a bookingRef-shaped sentence would be nonsense
		// for it. A wiring mistake should fail loudly, not send a sentence.
		assertThrows(IllegalArgumentException.class, () -> composer.compose(
				NotificationType.STAFF_ACCOUNT_CREATED, scheduled(SIX_PM_LOCAL, 120)));
	}

	private static BookingStatusChangedEvent scheduled(Instant startAt, Integer minutes) {
		return new BookingStatusChangedEvent("evt-1", BOOKING_REF, "66f1a2b3c4d5e6f708192a3b",
				BookingStatus.SCHEDULED, startAt, minutes, Instant.parse("2026-08-11T03:19:58Z"));
	}
}
