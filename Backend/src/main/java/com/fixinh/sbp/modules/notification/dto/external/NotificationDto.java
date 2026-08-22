package com.fixinh.sbp.modules.notification.dto.external;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.notification.model.NotificationType;

/**
 * One notification as §9.1 publishes it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationDto(
		String notificationId,
		NotificationType type,
		String message,
		String bookingRef,
		boolean readStatus,
		Instant createdAt) {

	/**
	 * <p>{@code bookingRef} is null for a notification that concerns no booking, and
	 * {@code @JsonInclude(NON_NULL)} then drops the field entirely rather than publishing
	 * {@code "bookingRef": null}. §9.1 says it is "present whenever the notification concerns
	 * a booking", which reads as absent otherwise — and an absent field is easier for the
	 * frontend to branch on than a present null.
	 */
	public static NotificationDto from(Notification notification) {
		return new NotificationDto(
				notification.getId(),
				notification.getType(),
				notification.getMessage(),
				notification.getBookingRef(),
				notification.isReadStatus(),
				notification.getCreatedAt());
	}
}