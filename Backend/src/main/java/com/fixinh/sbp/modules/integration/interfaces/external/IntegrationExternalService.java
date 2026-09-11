package com.fixinh.sbp.modules.integration.interfaces.external;

import com.fixinh.sbp.modules.integration.dto.external.BookingStatusWebhookDto;
import com.fixinh.sbp.modules.integration.dto.external.EventReceiptDto;

/** What the integration module's own controller may ask of it (§11.7, Req 4.M3). */
public interface IntegrationExternalService {

	/**
	 * Takes a booking change from SMP: refreshes SBP's projection, then lets the rest of the
	 * application react to it (§11.7).
	 *
	 * <p><b>Refresh first, react second.</b> §11.7 promises the projection has caught up
	 * before the customer is told, so that a scheduled-booking toast leads to a screen that
	 * can already show the technician's name (Req 3.3.S5). The event is published after the
	 * refresh has returned and is handled synchronously, which is what makes that ordering a
	 * property of the code rather than a hope about timing.
	 *
	 * <p><b>Safe to repeat.</b> The same {@code eventId} twice is answered with
	 * {@code duplicate: true} and changes nothing — but only once the first delivery finished.
	 * A delivery that failed part-way is not recorded as handled, so SMP's retry runs it
	 * again rather than being brushed off with a duplicate receipt for work that never
	 * completed.
	 *
	 * @throws com.fixinh.sbp.common.exception.ApiException 400 {@code EVENT_MALFORMED} for a
	 *         missing or unusable field, 404 {@code CUSTOMER_NOT_FOUND} for a customer SBP
	 *         does not know, 404 {@code BOOKING_NOT_FOUND} for a booking it has no projection
	 *         of
	 */
	EventReceiptDto receiveBookingEvent(BookingStatusWebhookDto event);
}
