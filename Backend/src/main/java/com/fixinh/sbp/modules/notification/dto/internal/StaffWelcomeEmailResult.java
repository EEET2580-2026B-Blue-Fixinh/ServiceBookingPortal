package com.fixinh.sbp.modules.notification.dto.internal;

/**
 * What the notification module reports back after trying to send a welcome email.
 *
 * @param notificationId the row that records the attempt, whichever way it went. Always
 *                       present: the {@code Notification} is written before the provider is
 *                       called, so there is something for the retry sweeper to find even if
 *                       the process dies mid-send
 * @param sent           whether the provider accepted the message. This is what §10.6 puts in
 *                       {@code notificationSent}. Accepted is not delivered — it is the
 *                       furthest anything here can honestly claim
 */
public record StaffWelcomeEmailResult(String notificationId, boolean sent) {
}