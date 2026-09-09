package com.fixinh.sbp.modules.notification.websocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.fixinh.sbp.modules.notification.dto.external.NotificationPushDto;
import com.fixinh.sbp.modules.notification.model.NotificationType;

/**
 * The template is mocked: what matters is that the message is addressed to the recipient's
 * user destination, exactly, and that nothing the transport does can reach the caller.
 * Whether Spring then finds a session for that name is the round-trip test's business.
 */
class NotificationGatewayTest {

	private static final String CUSTOMER_ID = "66f1a2b3c4d5e6f708192a3b";

	private static final NotificationPushDto PAYLOAD = new NotificationPushDto(
			"6713cd45ef67ab8901234567",
			NotificationType.BOOKING_SCHEDULED,
			"Your order ORD-2026-1024 is scheduled for 24 Aug 2026, 18:00-20:00.",
			"ORD-2026-1024",
			Instant.parse("2026-08-11T03:20:00Z"));

	private SimpMessagingTemplate messagingTemplate;
	private NotificationGateway gateway;

	@BeforeEach
	void setUp() {
		messagingTemplate = mock(SimpMessagingTemplate.class);
		gateway = new NotificationGateway(messagingTemplate);
	}

	@Test
	void addressesTheRecipientsOwnQueue() {
		gateway.push(CUSTOMER_ID, PAYLOAD);

		verify(messagingTemplate).convertAndSendToUser(CUSTOMER_ID, "/queue/notifications", PAYLOAD);
	}

	@Test
	void refusesABlankRecipientRatherThanDroppingTheMessageSilently() {
		assertThrows(IllegalArgumentException.class, () -> gateway.push(null, PAYLOAD));
		assertThrows(IllegalArgumentException.class, () -> gateway.push("   ", PAYLOAD));

		verifyNoInteractions(messagingTemplate);
	}

	@Test
	void refusesAMissingPayload() {
		assertThrows(IllegalArgumentException.class, () -> gateway.push(CUSTOMER_ID, null));

		verifyNoInteractions(messagingTemplate);
	}

	@Test
	void aTransportFailureNeverReachesTheCaller() {
		doThrow(new MessageDeliveryException("broker channel closed"))
				.when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

		assertDoesNotThrow(() -> gateway.push(CUSTOMER_ID, PAYLOAD),
				"the notification is already saved; a failed push must not fail what raised it");
	}
}
