package com.fixinh.sbp.modules.notification.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.security.blacklist.TokenBlacklistRepository;
import com.fixinh.sbp.common.security.jwt.JwtTokenProvider;
import com.fixinh.sbp.common.security.jwt.UserPrincipal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

/**
 * The token provider and the blacklist are mocked; the frames are real.
 *
 * <p>Frames are built the way {@code StompDecoder} builds them — a mutable accessor whose
 * headers are handed to {@code MessageBuilder.createMessage} — because the one thing this
 * class must get right is attaching the user to <i>that</i> accessor, in place. A test that
 * constructed frames any other way would pass while the real pipeline lost the user.
 *
 * <p>The provider is mocked rather than run for real because its verdicts are already its
 * own tests' business; what matters here is that each verdict, and the blacklist's, reaches
 * the client as the code Section 2.7 says it should.
 */
class StompAuthChannelInterceptorTest {

	private static final String CUSTOMER_ID = "66f1a2b3c4d5e6f708192a3b";
	private static final String EMAIL = "an.nguyen@gmail.com";
	private static final String JTI = "b7c1f0a2-6e2d-4c9b-9c1e-2f0a7d4e11c3";
	private static final String TOKEN = "header.payload.signature";
	private static final String BEARER = "Bearer " + TOKEN;

	private JwtTokenProvider tokenProvider;
	private TokenBlacklistRepository blacklist;
	private MessageChannel channel;
	private StompAuthChannelInterceptor interceptor;

	@BeforeEach
	void setUp() {
		tokenProvider = mock(JwtTokenProvider.class);
		blacklist = mock(TokenBlacklistRepository.class);
		channel = mock(MessageChannel.class);
		interceptor = new StompAuthChannelInterceptor(tokenProvider, blacklist);
	}

	// --- CONNECT ---------------------------------------------------------------------------

	@Test
	void aConnectWithoutAnAuthorizationHeaderIsRejectedAsMissing() {
		Message<byte[]> connect = frame(StompCommand.CONNECT, headers -> { });

		ApiException ex = assertThrows(ApiException.class, () -> interceptor.preSend(connect, channel));

		assertEquals(ErrorCode.AUTH_TOKEN_MISSING, ex.getErrorCode());
		verifyNoInteractions(tokenProvider, blacklist);
	}

	@Test
	void aConnectWithANonBearerSchemeIsRejectedAsMissing() {
		Message<byte[]> connect = frame(StompCommand.CONNECT,
				headers -> headers.addNativeHeader("Authorization", "Basic dXNlcjpwYXNz"));

		ApiException ex = assertThrows(ApiException.class, () -> interceptor.preSend(connect, channel));

		assertEquals(ErrorCode.AUTH_TOKEN_MISSING, ex.getErrorCode(),
				"the header exists but carries no bearer token, which is the same as none");
		verifyNoInteractions(tokenProvider);
	}

	@Test
	void anExpiredTokenIsRejectedWithTheProvidersVerdict() {
		when(tokenProvider.parseAndValidate(TOKEN))
				.thenThrow(new ApiException(ErrorCode.AUTH_TOKEN_EXPIRED));
		Message<byte[]> connect = authenticatedConnect();

		ApiException ex = assertThrows(ApiException.class, () -> interceptor.preSend(connect, channel));

		assertEquals(ErrorCode.AUTH_TOKEN_EXPIRED, ex.getErrorCode());
		verifyNoInteractions(blacklist);
	}

	@Test
	void aMalformedTokenIsRejectedWithTheProvidersVerdict() {
		when(tokenProvider.parseAndValidate(TOKEN))
				.thenThrow(new ApiException(ErrorCode.AUTH_TOKEN_INVALID));
		Message<byte[]> connect = authenticatedConnect();

		ApiException ex = assertThrows(ApiException.class, () -> interceptor.preSend(connect, channel));

		assertEquals(ErrorCode.AUTH_TOKEN_INVALID, ex.getErrorCode());
	}

	@Test
	void aRevokedTokenIsRejectedEvenThoughItStillParses() {
		when(tokenProvider.parseAndValidate(TOKEN)).thenReturn(customerClaims("CUSTOMER"));
		when(blacklist.isBlacklisted(JTI)).thenReturn(true);
		Message<byte[]> connect = authenticatedConnect();

		ApiException ex = assertThrows(ApiException.class, () -> interceptor.preSend(connect, channel));

		assertEquals(ErrorCode.AUTH_TOKEN_REVOKED, ex.getErrorCode(),
				"a signed-out token must not open a socket, exactly as it must not open an HTTP call");
		assertNull(StompHeaderAccessor.wrap(connect).getUser(), "nothing was attached to the frame");
	}

	@Test
	void aTokenNamingAnUnknownRoleIsRejectedAsInvalid() {
		when(tokenProvider.parseAndValidate(TOKEN)).thenReturn(customerClaims("SERVICE"));
		Message<byte[]> connect = authenticatedConnect();

		ApiException ex = assertThrows(ApiException.class, () -> interceptor.preSend(connect, channel));

		assertEquals(ErrorCode.AUTH_TOKEN_INVALID, ex.getErrorCode(),
				"Role has no SERVICE; a token claiming one was not issued by us");
	}

	@Test
	void aTokenWithNoSubjectIsRejectedAsInvalid() {
		Claims noSubject = Jwts.claims().id(JTI).expiration(inAnHour())
				.add("email", EMAIL).add("role", "CUSTOMER").build();
		when(tokenProvider.parseAndValidate(TOKEN)).thenReturn(noSubject);
		Message<byte[]> connect = authenticatedConnect();

		ApiException ex = assertThrows(ApiException.class, () -> interceptor.preSend(connect, channel));

		assertEquals(ErrorCode.AUTH_TOKEN_INVALID, ex.getErrorCode(),
				"the subject is the routing key; a session with an empty one could never be pushed to");
	}

	@Test
	void aValidTokenBecomesTheSessionUserNamedByTheSubject() {
		when(tokenProvider.parseAndValidate(TOKEN)).thenReturn(customerClaims("CUSTOMER"));
		when(blacklist.isBlacklisted(JTI)).thenReturn(false);
		Message<byte[]> connect = authenticatedConnect();

		Message<?> result = interceptor.preSend(connect, channel);

		assertSame(connect, result, "the same message goes on, with the user attached in place");

		Principal user = StompHeaderAccessor.wrap(connect).getUser();
		assertNotNull(user, "the CONNECT frame now carries a user for the session to keep");
		assertEquals(CUSTOMER_ID, user.getName(),
				"convertAndSendToUser resolves by this name, so it must be the customer id");

		Authentication authentication = assertInstanceOf(Authentication.class, user);
		UserPrincipal principal = assertInstanceOf(UserPrincipal.class, authentication.getPrincipal());
		assertEquals(CUSTOMER_ID, principal.id());
		assertEquals(EMAIL, principal.email());
		assertEquals(Role.CUSTOMER, principal.role());
		assertEquals(JTI, principal.tokenId());
		assertTrue(authentication.isAuthenticated());
		assertEquals(List.of("ROLE_CUSTOMER"),
				authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
	}

	@Test
	void aStompFrameIsAuthenticatedExactlyLikeConnect() {
		when(tokenProvider.parseAndValidate(TOKEN)).thenReturn(customerClaims("CUSTOMER"));
		Message<byte[]> stomp = frame(StompCommand.STOMP,
				headers -> headers.addNativeHeader("Authorization", BEARER));

		interceptor.preSend(stomp, channel);

		assertEquals(CUSTOMER_ID, StompHeaderAccessor.wrap(stomp).getUser().getName(),
				"STOMP 1.2 allows STOMP in place of CONNECT; it opens a session just the same");
	}

	@Test
	void refusesAnImmutableConnectFrameRatherThanLosingTheUser() {
		when(tokenProvider.parseAndValidate(TOKEN)).thenReturn(customerClaims("CUSTOMER"));
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.addNativeHeader("Authorization", BEARER);
		accessor.setImmutable();
		Message<byte[]> connect = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		assertThrows(IllegalStateException.class, () -> interceptor.preSend(connect, channel),
				"a copy would look authenticated and route nothing; failing loudly is the safer wrong");
	}

	// --- SUBSCRIBE ---------------------------------------------------------------------------

	@Test
	void subscribingToTheCallersOwnQueuePasses() {
		Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, headers -> {
			headers.setUser(signedIn());
			headers.setDestination(StompAuthChannelInterceptor.SUBSCRIBABLE_DESTINATION);
		});

		assertSame(subscribe, interceptor.preSend(subscribe, channel));
		verifyNoInteractions(tokenProvider, blacklist);
	}

	@Test
	void subscribingAnywhereElseIsDenied() {
		List<String> elsewhere = List.of(
				"/user/" + CUSTOMER_ID + "/queue/notifications", // own id, spelled out -- still not the form
				"/user/someone-else/queue/notifications",
				"/queue/notifications-user0v5k2xzp", // the broker's internal per-session name
				"/queue/notifications",
				"/topic/notifications");

		for (String destination : elsewhere) {
			Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, headers -> {
				headers.setUser(signedIn());
				headers.setDestination(destination);
			});

			ApiException ex = assertThrows(ApiException.class,
					() -> interceptor.preSend(subscribe, channel), destination);
			assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode(), destination);
		}
	}

	@Test
	void subscribingFromASessionThatNeverAuthenticatedIsRejected() {
		Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE,
				headers -> headers.setDestination(StompAuthChannelInterceptor.SUBSCRIBABLE_DESTINATION));

		ApiException ex = assertThrows(ApiException.class, () -> interceptor.preSend(subscribe, channel));

		assertEquals(ErrorCode.AUTH_TOKEN_MISSING, ex.getErrorCode(),
				"no user on the frame means no CONNECT was accepted for this session");
	}

	// --- SEND and the rest ----------------------------------------------------------------

	@Test
	void sendIsAlwaysDenied() {
		Message<byte[]> send = frame(StompCommand.SEND, headers -> {
			headers.setUser(signedIn());
			headers.setDestination(StompAuthChannelInterceptor.SUBSCRIBABLE_DESTINATION);
		});

		ApiException ex = assertThrows(ApiException.class, () -> interceptor.preSend(send, channel));

		assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode(),
				"an authenticated user still may not send: the channel is outbound-only");
	}

	@Test
	void housekeepingFramesPassUntouched() {
		for (StompCommand command : List.of(StompCommand.DISCONNECT, StompCommand.UNSUBSCRIBE,
				StompCommand.ACK, StompCommand.NACK)) {
			Message<byte[]> housekeeping = frame(command, headers -> headers.setUser(signedIn()));

			assertSame(housekeeping, interceptor.preSend(housekeeping, channel), command.name());
		}
		verifyNoInteractions(tokenProvider, blacklist);
	}

	@Test
	void heartbeatsAndNonStompMessagesPassUntouched() {
		StompHeaderAccessor heartbeat = StompHeaderAccessor.createForHeartbeat();
		Message<byte[]> heartbeatMessage =
				MessageBuilder.createMessage(new byte[] {'\n'}, heartbeat.getMessageHeaders());
		Message<String> plain = new GenericMessage<>("not a STOMP frame at all");

		assertSame(heartbeatMessage, interceptor.preSend(heartbeatMessage, channel));
		assertSame(plain, interceptor.preSend(plain, channel));
		verifyNoInteractions(tokenProvider, blacklist);
	}

	// --- Fixtures --------------------------------------------------------------------------

	/**
	 * A frame as {@code StompDecoder} hands it to the inbound channel: the accessor left
	 * mutable, its headers the message's headers.
	 */
	private static Message<byte[]> frame(StompCommand command, Consumer<StompHeaderAccessor> customise) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		customise.accept(accessor);
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

	private static Message<byte[]> authenticatedConnect() {
		return frame(StompCommand.CONNECT, headers -> headers.addNativeHeader("Authorization", BEARER));
	}

	private static Claims customerClaims(String role) {
		return Jwts.claims().subject(CUSTOMER_ID).id(JTI).expiration(inAnHour())
				.add("email", EMAIL).add("role", role).build();
	}

	private static Date inAnHour() {
		return Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
	}

	/** What Spring copies onto every frame after an accepted CONNECT. */
	private static Principal signedIn() {
		return new UserPrincipal(CUSTOMER_ID, EMAIL, Role.CUSTOMER, JTI,
				Instant.now().plus(1, ChronoUnit.HOURS));
	}
}
