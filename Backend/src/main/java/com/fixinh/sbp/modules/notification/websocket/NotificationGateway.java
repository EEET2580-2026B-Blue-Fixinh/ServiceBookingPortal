package com.fixinh.sbp.modules.notification.websocket;

import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.fixinh.sbp.modules.notification.dto.external.NotificationPushDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The one place a message enters the real-time channel of §9.3: hands a notification that
 * has <b>already been saved</b> to the customer it belongs to, if they are connected.
 *
 * <p>Transport only. Which notifications are pushed — today {@code BOOKING_CONFIRMED} and
 * {@code BOOKING_SCHEDULED}, to customers, on the in-app channel — is the notification
 * service's decision; this class routes whatever it is given and knows nothing of the model.
 *
 * <p>{@code convertAndSendToUser} resolves {@code recipientId} against the name of the
 * {@code Principal} attached at CONNECT, which {@code StompAuthChannelInterceptor} sets to
 * the JWT subject — the customer id. A customer who is not connected simply has no session
 * to resolve to, and the message goes nowhere. That is the intended outcome, not a fault:
 * the notification is already in the store, and the §9.1 list is the durable channel the
 * client falls back to on its next load or reconnect.
 *
 * <p>For the same reason a push never throws. "Saved first, delivered second" (§9) means a
 * delivery failure must not undo the save, and must not fail whatever caused the
 * notification — an SMP event, an admin action — so a {@link MessagingException} is logged
 * and swallowed here rather than left for every caller to remember to catch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationGateway {

	/**
	 * The destination the client subscribed to, minus the {@code /user} prefix — Spring
	 * adds that back when it rewrites the destination for the recipient's session.
	 */
	public static final String DESTINATION = "/queue/notifications";

	private final SimpMessagingTemplate messagingTemplate;

	/**
	 * Delivers {@code payload} to every session {@code recipientId} currently holds.
	 *
	 * @param recipientId the customer id — the JWT subject the session was opened with
	 * @param payload the §9.3 message, built from the saved notification
	 * @throws IllegalArgumentException for a blank recipient: Spring would resolve an empty
	 *         name to no session and drop the message silently, which is a bug hidden as a
	 *         no-op
	 */
	public void push(String recipientId, NotificationPushDto payload) {
		if (recipientId == null || recipientId.isBlank()) {
			throw new IllegalArgumentException("A notification cannot be pushed to a blank recipient.");
		}
		if (payload == null) {
			throw new IllegalArgumentException("A notification cannot be pushed without a payload.");
		}

		try {
			messagingTemplate.convertAndSendToUser(recipientId, DESTINATION, payload);
			log.debug("Pushed {} {} to {}", payload.type(), payload.notificationId(), recipientId);
		}
		catch (MessagingException ex) {
			// Already saved; the client catches up through §9.1. Nothing for the caller to do.
			log.warn("Could not push {} {} to {} - the notification is saved and will be listed "
					+ "on the next load", payload.type(), payload.notificationId(), recipientId, ex);
		}
	}
}
