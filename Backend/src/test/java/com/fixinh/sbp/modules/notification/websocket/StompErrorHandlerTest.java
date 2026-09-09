package com.fixinh.sbp.modules.notification.websocket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;

/**
 * The exception reaches the handler the way the inbound channel delivers it — wrapped in a
 * {@code MessageDeliveryException} — because that wrapping is the whole reason the handler
 * exists. A test that passed the {@code ApiException} bare would prove nothing about the
 * production path.
 */
class StompErrorHandlerTest {

	private final StompErrorHandler handler = new StompErrorHandler();

	@Test
	void putsTheErrorCodeNameInTheMessageHeader() {
		Message<byte[]> connect = clientFrame(StompCommand.CONNECT, null);
		ApiException rejection = new ApiException(ErrorCode.AUTH_TOKEN_EXPIRED);

		Message<byte[]> error = handler.handleClientMessageProcessingError(connect,
				wrappedByTheChannel(connect, rejection));

		StompHeaderAccessor headers = StompHeaderAccessor.wrap(error);
		assertEquals(StompCommand.ERROR, headers.getCommand());
		assertEquals("AUTH_TOKEN_EXPIRED", headers.getMessage(),
				"the client branches on the code, as it does for HTTP (Section 2.7)");
		assertNotEquals(rejection.getMessage(), headers.getMessage(),
				"the human-readable text stays out of the frame");
		assertArrayEquals(new byte[0], error.getPayload());
	}

	@Test
	void findsTheRejectionHoweverDeeplyItIsWrapped() {
		Message<byte[]> subscribe = clientFrame(StompCommand.SUBSCRIBE, null);
		RuntimeException nested = new RuntimeException("outer",
				wrappedByTheChannel(subscribe, new ApiException(ErrorCode.ACCESS_DENIED)));

		Message<byte[]> error = handler.handleClientMessageProcessingError(subscribe, nested);

		assertEquals("ACCESS_DENIED", StompHeaderAccessor.wrap(error).getMessage());
	}

	@Test
	void echoesTheClientsReceiptSoItCanCorrelateTheError() {
		Message<byte[]> subscribe = clientFrame(StompCommand.SUBSCRIBE, "sub-0-receipt");

		Message<byte[]> error = handler.handleClientMessageProcessingError(subscribe,
				wrappedByTheChannel(subscribe, new ApiException(ErrorCode.ACCESS_DENIED)));

		assertEquals("sub-0-receipt", StompHeaderAccessor.wrap(error).getReceiptId());
	}

	@Test
	void copesWithNoClientMessageAtAll() {
		Message<byte[]> error = handler.handleClientMessageProcessingError(null,
				new MessageDeliveryException(null, "no frame",
						new ApiException(ErrorCode.AUTH_TOKEN_MISSING)));

		StompHeaderAccessor headers = StompHeaderAccessor.wrap(error);
		assertEquals("AUTH_TOKEN_MISSING", headers.getMessage());
		assertNull(headers.getReceiptId());
	}

	@Test
	void leavesGenuineFaultsToSpringsDefault() {
		Message<byte[]> connect = clientFrame(StompCommand.CONNECT, null);
		IllegalStateException fault = new IllegalStateException("interceptor misregistered");

		Message<byte[]> error = handler.handleClientMessageProcessingError(connect,
				wrappedByTheChannel(connect, fault));

		StompHeaderAccessor headers = StompHeaderAccessor.wrap(error);
		assertNotNull(error, "a fault still produces an ERROR frame, so the client is not left hanging");
		assertEquals(StompCommand.ERROR, headers.getCommand());
		assertNotEquals("ACCESS_DENIED", headers.getMessage(),
				"a fault is not dressed up as a rejection the client might act on");
	}

	// --- Fixtures --------------------------------------------------------------------------

	private static Message<byte[]> clientFrame(StompCommand command, String receipt) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		if (receipt != null) {
			accessor.setReceipt(receipt);
		}
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

	/** What {@code AbstractMessageChannel.send} throws when an interceptor's preSend throws. */
	private static MessageDeliveryException wrappedByTheChannel(Message<?> frame, Throwable cause) {
		return new MessageDeliveryException(frame, "Failed to send message to clientInboundChannel", cause);
	}
}
