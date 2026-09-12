package com.fixinh.sbp.modules.notification.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fixinh.sbp.modules.integration.dto.external.BookingStatusChangedEvent;
import com.fixinh.sbp.modules.notification.model.NotificationType;

/**
 * The one place the text of a booking notification is written (Req 3.3.U2, §9.1).
 *
 * <p>§9.1's own example is the template: {@code Your order ORD-2026-1024 is scheduled for
 * 24 Aug 2026, 18:00-20:00.} Everything a customer reads about a confirmed or scheduled
 * booking — in the toast, the bell, the §9.1 list — comes through here, so a change of
 * wording is one edit and the three surfaces cannot drift apart.
 *
 * <p>Degrades rather than fails. By the time a message is composed the projection has
 * already been refreshed, so a payload missing its visit time is not a reason to fail the
 * whole event and lose the notification; it is a reason to say less. A scheduled booking
 * with no time reads {@code has been scheduled}, one with a start but no duration names the
 * start only.
 */
@Component
public class BookingNotificationComposer {

	/**
	 * The zone the customer reads times in — the same one the frontend converts every
	 * timestamp to for display ({@code bookingPresentation.js}), so the time in the toast and
	 * the time on the booking screen it opens are the same clock. The API itself stays in UTC
	 * (§2.6); only this sentence is localised.
	 */
	static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	// en-GB rather than a bare English locale, to match the frontend's own formatter: the
	// two differ on exactly one month's short name, and a toast that disagrees with the page
	// under it is the kind of thing a customer notices.
	private static final DateTimeFormatter DATE =
			DateTimeFormatter.ofPattern("d MMM uuuu", Locale.UK);
	private static final DateTimeFormatter TIME =
			DateTimeFormatter.ofPattern("HH:mm", Locale.UK);

	/**
	 * @param type which of the two booking notifications this is
	 * @throws IllegalArgumentException for a type that is not about a booking. The staff
	 *         welcome email has its own composer; routing it here would be a wiring mistake,
	 *         not a message to make the best of
	 */
	public String compose(NotificationType type, BookingStatusChangedEvent event) {
		// No default branch, on purpose: a fourth type should break this build rather than
		// be sent out with a sentence written for a different one.
		return switch (type) {
			case BOOKING_CONFIRMED -> "Your order " + event.bookingRef() + " has been confirmed.";
			case BOOKING_SCHEDULED -> scheduled(event);
			case STAFF_ACCOUNT_CREATED -> throw new IllegalArgumentException(
					"A staff welcome message is composed by StaffWelcomeEmailComposer, not here.");
		};
	}

	private String scheduled(BookingStatusChangedEvent event) {
		if (event.scheduledStartAt() == null) {
			return "Your order " + event.bookingRef() + " has been scheduled.";
		}

		ZonedDateTime start = event.scheduledStartAt().atZone(DISPLAY_ZONE);
		return "Your order " + event.bookingRef() + " is scheduled for "
				+ visit(start, event.estimatedDurationMinutes()) + ".";
	}

	/**
	 * {@code 24 Aug 2026, 18:00-20:00} in the ordinary case. The end is the start plus the
	 * estimate; when that lands on the next day its date is written too, because
	 * {@code 23:00-01:00} on its own reads as a window that ends before it starts.
	 */
	private static String visit(ZonedDateTime start, Integer durationMinutes) {
		String from = DATE.format(start) + ", " + TIME.format(start);

		if (durationMinutes == null || durationMinutes <= 0) {
			return from;
		}

		ZonedDateTime end = start.plusMinutes(durationMinutes);
		if (end.toLocalDate().equals(start.toLocalDate())) {
			return from + "-" + TIME.format(end);
		}
		return from + " - " + DATE.format(end) + ", " + TIME.format(end);
	}
}
