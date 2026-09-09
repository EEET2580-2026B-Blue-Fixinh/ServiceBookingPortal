package com.fixinh.sbp.modules.notification.websocket;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.common.security.blacklist.TokenBlacklistRepository;
import com.fixinh.sbp.common.security.jwt.JwtTokenProvider;
import com.fixinh.sbp.modules.auth.model.UserCredential;
import com.fixinh.sbp.modules.notification.dto.external.NotificationPushDto;
import com.fixinh.sbp.modules.notification.model.NotificationType;

import tools.jackson.databind.json.JsonMapper;

/**
 * The real channel, end to end: a STOMP client connects over SockJS to the running server,
 * exactly as the browser will, and the assertions are on what comes back over the wire.
 *
 * <p>Needs the shared Atlas clusters, like {@code SbpApplicationTests} — the whole context
 * comes up, indexes and all. Without {@code MONGO_URI_SOUTH} and {@code MONGO_URI_NORTH}
 * the class is skipped, not failed, so a machine without credentials still gets a green
 * unit-test run; check the surefire summary for "Skipped" if you expected it to execute.
 *
 * <p>The blacklist is the one bean replaced: the revoked-token case needs
 * {@code isBlacklisted} to answer true for a token this test minted, and writing that into
 * the team's shared store to prove it would be the tail wagging the dog.
 *
 * <p>Tokens are minted with the application's own {@code JwtTokenProvider} and secret, so
 * what the server validates is a token it would itself have issued.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"fixinh.sbp.email.retry.enabled=false",
				"spring.main.banner-mode=off"
		})
@EnabledIfEnvironmentVariable(named = "MONGO_URI_SOUTH", matches = ".+",
		disabledReason = "Needs the shared Atlas clusters: set MONGO_URI_SOUTH and MONGO_URI_NORTH")
@EnabledIfEnvironmentVariable(named = "MONGO_URI_NORTH", matches = ".+",
		disabledReason = "Needs the shared Atlas clusters: set MONGO_URI_SOUTH and MONGO_URI_NORTH")
class NotificationWebSocketRoundTripTest {

	private static final String CUSTOMER_A = "66f1a2b3c4d5e6f708192a3b";
	private static final String CUSTOMER_B = "66f1a2b3c4d5e6f708192a3c";
	private static final String OWN_QUEUE = "/user/queue/notifications";

	private static final NotificationPushDto SCHEDULED = new NotificationPushDto(
			"6713cd45ef67ab8901234567",
			NotificationType.BOOKING_SCHEDULED,
			"Your order ORD-2026-1024 is scheduled for 24 Aug 2026, 18:00-20:00.",
			"ORD-2026-1024",
			Instant.parse("2026-08-11T03:20:00Z"));

	/** §9.3's example message, field for field. */
	private static final Map<String, Object> SECTION_9_3_MESSAGE = Map.of(
			"notificationId", "6713cd45ef67ab8901234567",
			"type", "BOOKING_SCHEDULED",
			"message", "Your order ORD-2026-1024 is scheduled for 24 Aug 2026, 18:00-20:00.",
			"bookingRef", "ORD-2026-1024",
			"createdAt", "2026-08-11T03:20:00Z");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private Environment environment;

	@Autowired
	private JwtTokenProvider tokenProvider;

	@Autowired
	private SbpProperties properties;

	@Autowired
	private NotificationGateway gateway;

	@Autowired
	private SimpUserRegistry userRegistry;

	@MockitoBean
	private TokenBlacklistRepository blacklist;

	private WebSocketStompClient stompClient;
	private final List<StompSession> openSessions = Collections.synchronizedList(new ArrayList<>());

	@BeforeEach
	void setUp() {
		// SockJS on the client too, so the info request and the transport negotiation are
		// exercised the way a browser exercises them, not bypassed.
		SockJsClient sockJs = new SockJsClient(
				List.of(new WebSocketTransport(new StandardWebSocketClient())));
		stompClient = new WebSocketStompClient(sockJs);
		// The assertions are on the raw JSON text, whatever content-type the server labels
		// it with; a typed converter would either parse it or refuse it.
		stompClient.setMessageConverter(new RawTextConverter());
		// The server does not send heartbeats; asking for none keeps the client from
		// needing a scheduler it would otherwise demand.
		stompClient.setDefaultHeartbeat(new long[] {0, 0});
	}

	@AfterEach
	void tearDown() {
		for (StompSession session : openSessions) {
			try {
				session.disconnect();
			}
			catch (RuntimeException alreadyClosed) {
				// The server closes a session after any ERROR frame; nothing to do.
			}
		}
		openSessions.clear();
	}

	// --- CONNECT ---------------------------------------------------------------------------

	@Test
	void rejectsAConnectWithoutAToken() throws Exception {
		Connection connection = connect(null);

		assertEquals("AUTH_TOKEN_MISSING", connection.errorCode().get(5, SECONDS));
		assertNoSession(connection);
	}

	@Test
	void rejectsAConnectWithAnExpiredToken() throws Exception {
		Connection connection = connect(expiredBearerFor(CUSTOMER_A));

		assertEquals("AUTH_TOKEN_EXPIRED", connection.errorCode().get(5, SECONDS));
		assertNoSession(connection);
	}

	@Test
	void rejectsAConnectWithARevokedToken() throws Exception {
		String token = tokenProvider.generate(credential(CUSTOMER_A));
		when(blacklist.isBlacklisted(tokenProvider.parseAndValidate(token).getId())).thenReturn(true);

		Connection connection = connect("Bearer " + token);

		assertEquals("AUTH_TOKEN_REVOKED", connection.errorCode().get(5, SECONDS));
		assertNoSession(connection);
	}

	@Test
	void rejectsAConnectWithSomethingThatIsNotAToken() throws Exception {
		Connection connection = connect("Bearer this.is.not-a-jwt");

		assertEquals("AUTH_TOKEN_INVALID", connection.errorCode().get(5, SECONDS));
		assertNoSession(connection);
	}

	// --- Delivery --------------------------------------------------------------------------

	@Test
	void deliversTheSection93PayloadToItsRecipientAndToNobodyElse() throws Exception {
		Connection a = connect(bearerFor(CUSTOMER_A));
		StompSession session = a.session().get(5, SECONDS);
		subscribe(session, OWN_QUEUE, a.received());
		awaitSubscription(CUSTOMER_A, OWN_QUEUE);

		// First, the channel works at all, and the wire format is the one §9.3 shows.
		String body = pushAndAwait(CUSTOMER_A, a.received());
		assertNotNull(body, "A is subscribed to its own queue and should receive its own push");
		Map<?, ?> json = JSON.readValue(body, Map.class);
		assertEquals(SECTION_9_3_MESSAGE, json,
				"exactly the §9.3 fields, createdAt as ISO-8601, no readStatus");

		// Then, with A demonstrably listening, a push to B must not reach A.
		gateway.push(CUSTOMER_B, SCHEDULED);
		assertNull(a.received().poll(2, SECONDS),
				"a push addressed to B must not arrive on A's queue");

		// And A is still live afterwards, so the silence above was routing, not a dead socket.
		assertNotNull(pushAndAwait(CUSTOMER_A, a.received()),
				"A's own queue keeps working after somebody else's push");
	}

	// --- Forbidden frames ------------------------------------------------------------------

	@Test
	void refusesSubscribingToAnotherCustomersQueue() throws Exception {
		Connection a = connect(bearerFor(CUSTOMER_A));
		StompSession session = a.session().get(5, SECONDS);

		subscribe(session, "/user/" + CUSTOMER_B + "/queue/notifications", a.received());

		assertEquals("ACCESS_DENIED", a.errorCode().get(5, SECONDS),
				"knowing B's identifier must not be enough to subscribe to B's queue");
	}

	@Test
	void refusesSubscribingToTheBrokersInternalQueueName() throws Exception {
		Connection a = connect(bearerFor(CUSTOMER_A));
		StompSession session = a.session().get(5, SECONDS);

		// The name the simple broker rewrites a user destination to. Guessing a session id
		// would otherwise be all it takes.
		subscribe(session, "/queue/notifications-user" + "0v5k2xzp", a.received());

		assertEquals("ACCESS_DENIED", a.errorCode().get(5, SECONDS));
	}

	@Test
	void refusesAnySendFromAClient() throws Exception {
		Connection a = connect(bearerFor(CUSTOMER_A));
		StompSession session = a.session().get(5, SECONDS);

		session.send("/queue/notifications", "{\"forged\":true}");

		assertEquals("ACCESS_DENIED", a.errorCode().get(5, SECONDS),
				"the channel is outbound-only; a client cannot inject a message for anyone");
	}

	// --- Client plumbing -------------------------------------------------------------------

	/** One client connection: the session future, the first ERROR code seen, and any MESSAGE bodies. */
	private record Connection(
			CompletableFuture<StompSession> session,
			CompletableFuture<String> errorCode,
			BlockingQueue<String> received) {
	}

	private Connection connect(String bearer) {
		StompHeaders connectHeaders = new StompHeaders();
		if (bearer != null) {
			connectHeaders.add("Authorization", bearer);
		}

		CompletableFuture<String> errorCode = new CompletableFuture<>();
		StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				// ERROR frames land here; subscription MESSAGEs go to the subscription's
				// own handler. The code is in the frame's message header.
				errorCode.complete(headers.getFirst("message"));
			}
		};

		CompletableFuture<StompSession> session = stompClient.connectAsync(
				"ws://localhost:" + port() + "/ws", (WebSocketHttpHeaders) null, connectHeaders, handler);
		session.thenAccept(openSessions::add);
		return new Connection(session, errorCode, new LinkedBlockingQueue<>());
	}

	private static void subscribe(StompSession session, String destination, BlockingQueue<String> into) {
		session.subscribe(destination, new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return String.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				into.add((String) payload);
			}
		});
	}

	/** A rejected CONNECT never becomes a session: the future fails, or never completes. */
	private static void assertNoSession(Connection connection) {
		assertThrows(Exception.class, () -> connection.session().get(2, SECONDS),
				"no StompSession may be established after an ERROR frame");
	}

	/**
	 * Waits until the server-side user registry shows the subscription, so the push that
	 * follows cannot beat the SUBSCRIBE frame to the broker.
	 */
	private void awaitSubscription(String userId, String destination) throws InterruptedException {
		long deadline = System.nanoTime() + SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			SimpUser user = userRegistry.getUser(userId);
			if (user != null && user.getSessions().stream()
					.flatMap(s -> s.getSubscriptions().stream())
					.anyMatch(sub -> destination.equals(sub.getDestination()))) {
				return;
			}
			Thread.sleep(50);
		}
		fail("The server never registered " + userId + "'s subscription to " + destination);
	}

	/**
	 * The registry is updated from the subscribe event, which is published as the frame is
	 * handed to the broker rather than after the broker has filed it. That window is
	 * microseconds wide; a second push closes it without hiding a real failure, because a
	 * channel that does not work fails both attempts.
	 */
	private String pushAndAwait(String recipient, BlockingQueue<String> queue) throws InterruptedException {
		gateway.push(recipient, SCHEDULED);
		String body = queue.poll(2, SECONDS);
		if (body == null) {
			gateway.push(recipient, SCHEDULED);
			body = queue.poll(3, SECONDS);
		}
		return body;
	}

	/** Bytes in, text out, regardless of content-type; text in, bytes out. Nothing more. */
	private static final class RawTextConverter implements MessageConverter {
		@Override
		public Object fromMessage(Message<?> message, Class<?> targetClass) {
			return new String((byte[]) message.getPayload(), StandardCharsets.UTF_8);
		}

		@Override
		public Message<?> toMessage(Object payload, MessageHeaders headers) {
			return MessageBuilder.createMessage(
					payload.toString().getBytes(StandardCharsets.UTF_8), headers);
		}
	}

	// --- Fixtures --------------------------------------------------------------------------

	private int port() {
		Integer port = environment.getProperty("local.server.port", Integer.class);
		assertNotNull(port, "RANDOM_PORT should have published local.server.port");
		return port;
	}

	private String bearerFor(String customerId) {
		return "Bearer " + tokenProvider.generate(credential(customerId));
	}

	/** Signed with the real secret and issuer, but already past its expiry. */
	private String expiredBearerFor(String customerId) {
		SbpProperties expiring = new SbpProperties();
		expiring.getJwt().setSecret(properties.getJwt().getSecret());
		expiring.getJwt().setIssuer(properties.getJwt().getIssuer());
		expiring.getJwt().setAccessTokenTtlMinutes(-1);
		return "Bearer " + new JwtTokenProvider(expiring).generate(credential(customerId));
	}

	private static UserCredential credential(String customerId) {
		return UserCredential.builder()
				.id(customerId)
				.email(customerId + "@example.com")
				.accountRole(Role.CUSTOMER)
				.build();
	}
}
