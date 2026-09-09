package com.fixinh.sbp.modules.notification.websocket;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.security.blacklist.TokenBlacklistRepository;
import com.fixinh.sbp.common.security.jwt.JwtTokenProvider;
import com.fixinh.sbp.common.security.jwt.UserPrincipal;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authentication and authorisation for the STOMP channel (§9.3), applied to every frame a
 * client sends inbound.
 *
 * <p>The HTTP handshake at {@code /ws} is unauthenticated by design — SockJS cannot attach an
 * {@code Authorization} header to it — so the token travels in the CONNECT frame instead, and
 * this is the only place it is checked. The rules, per frame:
 *
 * <ul>
 *   <li><b>CONNECT / STOMP</b> — the bearer token must parse, must not be expired, and must
 *       not be blacklisted; the same three checks {@code JwtAuthenticationFilter} makes, with
 *       the same error codes. The resulting {@link UserPrincipal} becomes the session's user.
 *       Without one, {@code /user/queue/...} has nothing to resolve against and pushes go
 *       nowhere, silently.</li>
 *   <li><b>SUBSCRIBE</b> — allowed only for exactly {@value #SUBSCRIBABLE_DESTINATION}. The
 *       simple broker would otherwise honour a subscription to the internal per-session
 *       queue another user's messages are rewritten to, so "customer A cannot receive
 *       customer B's messages" holds by this rule, not by hoping session ids stay secret.</li>
 *   <li><b>SEND</b> — never. The channel is outbound-only (Appendix E #1 settled as REST
 *       push). With no application destination prefix a SEND would reach the broker directly,
 *       where it could forge a notification into someone else's queue.</li>
 *   <li>Everything else — DISCONNECT, UNSUBSCRIBE, ACK, heartbeats — passes untouched.</li>
 * </ul>
 *
 * <p>A rejection is an {@link ApiException}; {@link StompErrorHandler} turns it into an ERROR
 * frame carrying the code, and Spring closes the session.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	/** The one destination a client may subscribe to — its own queue, and only its own. */
	public static final String SUBSCRIBABLE_DESTINATION = "/user/queue/notifications";

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider tokenProvider;
	private final TokenBlacklistRepository tokenBlacklistRepository;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor =
				MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

		// A heartbeat has an accessor but no command; a non-STOMP message has neither.
		// Neither carries anything to check.
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}

		switch (accessor.getCommand()) {
			case CONNECT, STOMP -> authenticate(accessor);
			case SUBSCRIBE -> requireOwnQueue(accessor);
			case SEND -> throw new ApiException(ErrorCode.ACCESS_DENIED,
					"This channel is outbound-only; clients do not send messages on it.");
			default -> { }
		}

		return message;
	}

	/**
	 * Turns the CONNECT frame's bearer token into the session's {@link Authentication}.
	 *
	 * <p>Set on the accessor in place rather than on a rebuilt message, because that is the
	 * accessor {@code StompSubProtocolHandler} reads the user back from after the send. It is
	 * mutable here only because Spring appends its {@code ImmutableMessageChannelInterceptor}
	 * <i>after</i> the ones registered in configuration — which is why this refuses an immutable
	 * one outright: a copy would authenticate the frame and lose the user, and the failure
	 * would surface as pushes that never arrive.
	 */
	private void authenticate(StompHeaderAccessor accessor) {
		String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			throw new ApiException(ErrorCode.AUTH_TOKEN_MISSING);
		}

		// Throws AUTH_TOKEN_EXPIRED or AUTH_TOKEN_INVALID itself; nothing to translate.
		Claims claims = tokenProvider.parseAndValidate(header.substring(BEARER_PREFIX.length()));

		if (tokenBlacklistRepository.isBlacklisted(claims.getId())) {
			throw new ApiException(ErrorCode.AUTH_TOKEN_REVOKED);
		}

		UserPrincipal principal = principalFrom(claims);
		Authentication authentication = new UsernamePasswordAuthenticationToken(
				principal, null, List.of(new SimpleGrantedAuthority(principal.authority())));

		if (!accessor.isMutable()) {
			throw new IllegalStateException(
					"The CONNECT frame's headers are immutable, so the authenticated user cannot "
							+ "be attached to the session. " + getClass().getSimpleName()
							+ " must be registered on the client inbound channel before Spring's "
							+ "ImmutableMessageChannelInterceptor.");
		}
		accessor.setUser(authentication);

		log.debug("STOMP session authenticated for {} {}", principal.role(), principal.id());
	}

	/**
	 * A token we issued always names a subject and a known role. One that does not is not
	 * ours, whatever its signature says — and an empty name would be an empty routing key.
	 */
	private UserPrincipal principalFrom(Claims claims) {
		String subject = claims.getSubject();
		String role = JwtTokenProvider.roleOf(claims);
		if (subject == null || subject.isBlank() || role == null) {
			throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
		}

		try {
			return new UserPrincipal(subject, JwtTokenProvider.emailOf(claims), Role.valueOf(role),
					claims.getId(), claims.getExpiration().toInstant());
		}
		catch (IllegalArgumentException ex) {
			// A role claim naming something outside the enum -- the same reading
			// JwtAuthenticationFilter gives it.
			throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
		}
	}

	/**
	 * A SUBSCRIBE from a session that never authenticated has no user on it (Spring copies the
	 * CONNECT's user onto every later frame), and a SUBSCRIBE from one that did may only name
	 * its own queue.
	 */
	private void requireOwnQueue(StompHeaderAccessor accessor) {
		if (accessor.getUser() == null) {
			throw new ApiException(ErrorCode.AUTH_TOKEN_MISSING);
		}
		if (!SUBSCRIBABLE_DESTINATION.equals(accessor.getDestination())) {
			throw new ApiException(ErrorCode.ACCESS_DENIED,
					"Only " + SUBSCRIBABLE_DESTINATION + " can be subscribed to.");
		}
	}
}
