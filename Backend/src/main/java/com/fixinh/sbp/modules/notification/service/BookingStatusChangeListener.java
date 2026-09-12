package com.fixinh.sbp.modules.notification.service;

import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.integration.dto.external.BookingStatusChangedEvent;
import com.fixinh.sbp.modules.notification.dto.internal.NotificationCreateCommand;
import com.fixinh.sbp.modules.notification.interfaces.internal.NotificationInternalService;
import com.fixinh.sbp.modules.notification.model.NotificationType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a booking status change reported by SMP into the customer's notification
 * (Req 3.3.U2, 4.M3) — for the two statuses that warrant one.
 *
 * <p>Synchronous, and not by omission. The integration module publishes the event after it
 * has written the refreshed projection, and a listener that runs in the same thread runs
 * <em>after</em> that write by construction — which is the ordering §11.7 and §9.3 both
 * promise, so that a customer who taps a scheduled-booking toast lands on a screen that can
 * already show the technician's name (Req 3.3.S5). An {@code @Async} here would trade that
 * guarantee for nothing.
 *
 * <p>Exceptions are not caught. A failure here fails the inbound request, SMP sees a 5xx
 * and retries with the same {@code eventId}, and the retry comes back through this listener
 * — where {@code raise} recognises the event and raises the notification once. Swallowing
 * the failure would return 202 for a notification that was never saved, and nothing would
 * ever come back for it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingStatusChangeListener {

	private final BookingNotificationComposer composer;
	private final NotificationInternalService notificationService;

	@EventListener
	public void onBookingStatusChanged(BookingStatusChangedEvent event) {
		Optional<NotificationType> type = typeFor(event.status());

		if (type.isEmpty()) {
			// §11.7: "Other statuses still refresh the projection silently." The refresh has
			// already happened; there is simply nothing to say about it.
			log.debug("Booking {} is now {}; no notification for that status (event {})",
					event.bookingRef(), event.status(), event.eventId());
			return;
		}

		notificationService.raise(NotificationCreateCommand.forBookingEvent(
				event.eventId(),
				event.customerId(),
				type.get(),
				composer.compose(type.get(), event),
				event.bookingRef()));
	}

	/**
	 * Which statuses produce a notification (Req 3.3.U2, Appendix A): the two marked
	 * "Triggers a notification", and no others.
	 *
	 * <p>Every status is named rather than left to a {@code default}, so a ninth one added to
	 * {@code BookingStatus} fails to compile here and has to be decided on, instead of being
	 * silently filed with the six that say nothing.
	 */
	private static Optional<NotificationType> typeFor(BookingStatus status) {
		return switch (status) {
			case CONFIRMED -> Optional.of(NotificationType.BOOKING_CONFIRMED);
			case SCHEDULED -> Optional.of(NotificationType.BOOKING_SCHEDULED);
			case CREATED, WORKING, COMPLETED, CANCELLED_BY_CUSTOMER, TERMINATED_BY_CUSTOMER,
					ABORTED_BY_TECHNICIAN -> Optional.empty();
		};
	}
}
