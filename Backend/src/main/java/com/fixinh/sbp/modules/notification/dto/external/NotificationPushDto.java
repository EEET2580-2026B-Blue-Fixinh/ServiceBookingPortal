package com.fixinh.sbp.modules.notification.dto.external;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.notification.model.NotificationType;

/**
 * One notification as §9.3 pushes it over the real-time channel.
 *
 * <p>Deliberately not {@link NotificationDto}. The pushed message carries no
 * {@code readStatus}: a notification is unread by definition at the moment it arrives, and
 * read state is the customer's to change through §9.2 afterwards. The client supplies
 * {@code readStatus: false} itself when it merges a push into the §9.1 list, so the two
 * shapes meet there and nowhere else. Keeping the field out of the wire format is what stops
 * a future edit from "helpfully" pushing a read flag that could never be anything but false.
 *
 * <p>{@code bookingRef} is absent, not null, when the notification concerns no booking —
 * the same rule as §9.1, for the same reason: an absent field is easier for the client to
 * branch on than a present null. In practice only {@code BOOKING_CONFIRMED} and
 * {@code BOOKING_SCHEDULED} are pushed, and both always name a booking.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationPushDto(
		String notificationId,
		NotificationType type,
		String message,
		String bookingRef,
		Instant createdAt) {

	/** The wire shape of a notification that has already been saved. */
	public static NotificationPushDto from(Notification notification) {
		return new NotificationPushDto(
				notification.getId(),
				notification.getType(),
				notification.getMessage(),
				notification.getBookingRef(),
				notification.getCreatedAt());
	}
}
