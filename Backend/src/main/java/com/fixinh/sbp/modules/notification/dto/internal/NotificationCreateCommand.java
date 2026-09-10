package com.fixinh.sbp.modules.notification.dto.internal;

import com.fixinh.sbp.modules.notification.model.NotificationType;
import com.fixinh.sbp.modules.notification.model.RecipientType;

/**
 * What another module hands the notification module to have an <b>in-app</b> notification
 * recorded.
 *
 * @param recipientType     who it is addressed to. Decides how the store is found: a customer
 *                          is located by their profile, a staff member by their branch
 * @param recipientIdentifier the customer id or SMP staff id
 * @param provinceCode      <b>only read for {@code STAFF}</b>, and required in that case —
 *                          there is no SBP profile to ask. Ignored for a customer, whose own
 *                          profile is authoritative and cannot disagree with itself
 * @param type              one of Appendix A's three values
 * @param message           the text the recipient reads, already composed
 * @param bookingRef        the booking this concerns, or null. Null is dropped from the §9.1
 *                          response rather than published as a null field
 * @param sourceEventId     the SMP event this comes from (§11.7's {@code eventId}), or null
 *                          when it comes from anything else. <b>Naming it is what makes the
 *                          command safe to repeat:</b> the service raises one notification per
 *                          event, however many times the event is delivered. Without it every
 *                          call is a new notification, because there is nothing to recognise a
 *                          repeat by
 */
public record NotificationCreateCommand(
		RecipientType recipientType,
		String recipientIdentifier,
		String provinceCode,
		NotificationType type,
		String message,
		String bookingRef,
		String sourceEventId) {

	public NotificationCreateCommand {
		if (recipientType == null) {
			throw new IllegalArgumentException(
					"A notification needs a recipient type before it can be filed.");
		}
		if (recipientIdentifier == null || recipientIdentifier.isBlank()) {
			throw new IllegalArgumentException("A notification needs a recipient identifier.");
		}
		if (type == null) {
			throw new IllegalArgumentException(
					"A notification needs a type; §9.1 publishes it and the frontend picks an "
							+ "icon and a route from it.");
		}
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException(
					"A notification with no message is one the recipient cannot act on.");
		}
	}

	/** A notification that comes from no SMP event, and so has no id to be repeated under. */
	public NotificationCreateCommand(RecipientType recipientType, String recipientIdentifier,
			String provinceCode, NotificationType type, String message, String bookingRef) {
		this(recipientType, recipientIdentifier, provinceCode, type, message, bookingRef, null);
	}

	/** Something happened to a customer's booking, and their bell should show it (Req 3.3.U2). */
	public static NotificationCreateCommand forCustomerBooking(String customerId,
			NotificationType type, String message, String bookingRef) {

		return new NotificationCreateCommand(RecipientType.CUSTOMER, customerId, null, type,
				message, bookingRef);
	}

	/**
	 * SMP reported a change to a customer's booking (§11.7), and their bell should show it —
	 * once, however many times SMP reports it.
	 *
	 * @throws IllegalArgumentException for a blank event id. A command that claims to come
	 *         from an event but cannot say which one would be filed as a new notification on
	 *         every replay, which is the exact outcome this factory exists to prevent
	 */
	public static NotificationCreateCommand forBookingEvent(String eventId, String customerId,
			NotificationType type, String message, String bookingRef) {

		if (eventId == null || eventId.isBlank()) {
			throw new IllegalArgumentException(
					"A notification raised from an SMP event needs that event's id.");
		}
		return new NotificationCreateCommand(RecipientType.CUSTOMER, customerId, null, type,
				message, bookingRef, eventId);
	}
}