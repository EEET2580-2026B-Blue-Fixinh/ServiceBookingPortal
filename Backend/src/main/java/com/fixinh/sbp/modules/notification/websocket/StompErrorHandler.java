package com.fixinh.sbp.modules.notification.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import com.fixinh.sbp.common.exception.ApiException;

/**
 * Shapes the ERROR frame a client receives when {@link StompAuthChannelInterceptor} rejects
 * something.
 *
 * <p>Section 2.7's rule for HTTP applies here too: clients branch on the error <i>code</i>,
 * never on a message written for people. So the frame's {@code message} header carries the
 * {@code ErrorCode} name — {@code AUTH_TOKEN_EXPIRED}, {@code ACCESS_DENIED} — and nothing
 * else. Without this handler the client would see Spring's wrapper text, "Failed to send
 * message to ExecutorSubscribableChannel[clientInboundChannel]", which says nothing about what
 * went wrong or whether reconnecting could help.
 *
 * <p>The interceptor's exception does not arrive bare. The inbound channel wraps anything an
 * interceptor throws in a {@code MessageDeliveryException}, so the cause chain is walked for
 * the {@link ApiException} inside. Anything that is not one falls through to Spring's default,
 * which is the right answer for a genuine fault.
 *
 * <p>Spring closes the session after any ERROR frame, so a rejected CONNECT never becomes a
 * half-open socket.
 */
public class StompErrorHandler extends StompSubProtocolErrorHandler {

	private static final byte[] EMPTY_PAYLOAD = new byte[0];

	@Override
	public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage,
			Throwable ex) {

		ApiException rejection = findApiException(ex);
		if (rejection == null) {
			return super.handleClientMessageProcessingError(clientMessage, ex);
		}

		StompHeaderAccessor error = StompHeaderAccessor.create(StompCommand.ERROR);
		error.setMessage(rejection.getErrorCode().name());
		error.setLeaveMutable(true);

		// Same courtesy as the default handler: echo the client's receipt id, if it asked for
		// one, so it can match this ERROR to the frame it sent.
		StompHeaderAccessor clientHeaders = null;
		if (clientMessage != null) {
			clientHeaders = MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);
			if (clientHeaders != null && clientHeaders.getReceipt() != null) {
				error.setReceiptId(clientHeaders.getReceipt());
			}
		}

		return handleInternal(error, EMPTY_PAYLOAD, rejection, clientHeaders);
	}

	/** The first {@link ApiException} in the cause chain, or null when there is none. */
	private static ApiException findApiException(Throwable ex) {
		Throwable current = ex;
		while (current != null) {
			if (current instanceof ApiException api) {
				return api;
			}
			if (current.getCause() == current) {
				break;
			}
			current = current.getCause();
		}
		return null;
	}
}
