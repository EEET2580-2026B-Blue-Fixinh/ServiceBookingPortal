package com.fixinh.sbp.modules.notification.dto.internal;

import com.fixinh.sbp.modules.notification.model.DeliveryChannel;
import com.fixinh.sbp.modules.notification.model.NotificationType;
import com.fixinh.sbp.modules.notification.model.RecipientType;

/**
 * What another module hands the notification module to have a notification recorded.
 *
 * @param recipientType     who it is addressed to. Decides how the store is found: a customer
 *                          is located by their profile, a staff member by their branch
 * @param recipientIdentifier the customer id or SMP staff id
 * @param provinceCode      <b>only read for {@code STAFF}</b>, and required in that case —
 *                          there is no SBP profile to ask. Ignored for a customer, whose own
 *                          profile is authoritative and cannot disagree with itself
 * @param type              one of Appendix A's three values
 * @param deliveryChannel   {@code IN_APP} for anything the bell should show
 * @param message           the text the recipient reads, already composed
 * @param bookingRef        the booking this concerns, or null. Null is dropped from the §9.1
 *                          response rather than published as a null field
 */
public record NotificationCreateCommand(
		RecipientType recipientType,
		String recipientIdentifier,
		String provinceCode,
		NotificationType type,
		DeliveryChannel deliveryChannel,
		String message,
		String bookingRef) {

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
		if (deliveryChannel == null) {
			throw new IllegalArgumentException("A notification needs a delivery channel.");
		}
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException(
					"A notification with no message is one the recipient cannot act on.");
		}
	}


	public static NotificationCreateCommand forCustomerBooking(String customerId,
			NotificationType type, String message, String bookingRef) {

		return new NotificationCreateCommand(RecipientType.CUSTOMER, customerId, null, type,
				DeliveryChannel.IN_APP, message, bookingRef);
	}
}