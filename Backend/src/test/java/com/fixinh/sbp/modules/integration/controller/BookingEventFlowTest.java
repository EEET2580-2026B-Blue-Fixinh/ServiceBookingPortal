package com.fixinh.sbp.modules.integration.controller;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;
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
import com.fixinh.sbp.common.sharding.Region;
import com.fixinh.sbp.common.sharding.ShardDirectoryRepository;
import com.fixinh.sbp.modules.auth.model.UserCredential;
import com.fixinh.sbp.modules.booking.interfaces.internal.BookingInternalService;
import com.fixinh.sbp.modules.booking.interfaces.internal.BookingProjectionView;
import com.fixinh.sbp.modules.booking.model.BookingStatus;
import com.fixinh.sbp.modules.booking.model.TechnicianRef;
import com.fixinh.sbp.modules.customer.service.CustomerServiceImpl;
import com.fixinh.sbp.modules.integration.model.IntegrationEvent;
import com.fixinh.sbp.modules.integration.repository.IntegrationEventRepository;
import com.fixinh.sbp.modules.notification.model.Notification;
import com.fixinh.sbp.modules.notification.repository.NotificationRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * §13's mock script, run for real: SMP's events go in over HTTP and the customer's
 * notifications come out over the socket, through every layer in between.
 *
 * <p>This is the automated form of the acceptance criteria — {@code CONFIRMED} then
 * {@code SCHEDULED} produces two notifications and two pushes, {@code WORKING} produces
 * none, and the same {@code eventId} twice produces one.
 *
 * <p><b>Nothing is written to the shared clusters.</b> The two repositories that would write
 * are replaced with in-memory stands-in that behave as the real ones do — including refusing
 * a repeated insert — so the replay assertions are real while the team's Atlas databases stay
 * untouched. The booking module is mocked for the same reason: refreshing a projection would
 * need a booking to exist, and this test is about what happens around that call, not inside
 * it. The context still needs the clusters to start, so the class is skipped without them,
 * exactly like the WebSocket round-trip suite.
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
class BookingEventFlowTest {

	private static final String CUSTOMER_ID = "66f1a2b3c4d5e6f708192a3b";
	private static final String BOOKING_REF = "ORD-2026-1024";
	private static final String OWN_QUEUE = "/user/queue/notifications";
	private static final String EVENTS = "/api/v1/integration/booking-events";

	/** 18:00 in Ho Chi Minh City, the zone the customer reads times in. */
	private static final Instant VISIT_AT = Instant.parse("2026-08-24T11:00:00Z");
	private static final TechnicianRef TECHNICIAN = new TechnicianRef("TEC-014", "John Tran");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private Environment environment;

	/**
	 * The JDK's own client rather than Spring's {@code TestRestTemplate}, which moved to a
	 * separate module in Spring Boot 4 and is not on this project's test classpath. Nothing
	 * here needs more than a POST with two headers, and this way the suite adds no dependency.
	 */
	private final HttpClient http = HttpClient.newHttpClient();

	@Autowired
	private JwtTokenProvider tokenProvider;

	@Autowired
	private SbpProperties properties;

	@Autowired
	private SimpUserRegistry userRegistry;

	@MockitoBean
	private TokenBlacklistRepository blacklist;

	/**
	 * Mocked as the implementation class rather than as {@code CustomerExternalService},
	 * because one bean implements both of the customer module's interfaces. Replacing it by
	 * either interface alone leaves the other unsatisfied, and the context fails to start
	 * with a bean-of-wrong-type error that has nothing to do with this suite.
	 */
	@MockitoBean
	private CustomerServiceImpl customerService;

	@MockitoBean
	private ShardDirectoryRepository shardDirectory;

	@MockitoBean
	private BookingInternalService bookingService;

	@MockitoBean
	private IntegrationEventRepository eventRepository;

	@MockitoBean
	private NotificationRepository notificationRepository;

	private WebSocketStompClient stompClient;
	private final List<StompSession> openSessions = Collections.synchronizedList(new ArrayList<>());

	@BeforeEach
	void setUp() {
		SockJsClient sockJs = new SockJsClient(
				List.of(new WebSocketTransport(new StandardWebSocketClient())));
		stompClient = new WebSocketStompClient(sockJs);
		stompClient.setMessageConverter(new RawTextConverter());
		stompClient.setDefaultHeartbeat(new long[] {0, 0});

		// The customer exists, in the south, as far as everything downstream is concerned.
		when(customerService.findHomeRegion(CUSTOMER_ID)).thenReturn(Optional.of(Region.SOUTH));
		when(shardDirectory.findRegion(any(), any())).thenReturn(Optional.of(Region.SOUTH));

		stubEventStore();
		stubNotificationStore();
		stubProjection(BookingStatus.SCHEDULED, List.of(TECHNICIAN));
	}

	@AfterEach
	void tearDown() {
		for (StompSession session : openSessions) {
			try {
				session.disconnect();
			}
			catch (RuntimeException alreadyClosed) {
				// Nothing to do: the server closes a session after any ERROR frame.
			}
		}
		openSessions.clear();
	}

	// --- The acceptance criteria ------------------------------------------------------------

	@Test
	void confirmedThenScheduledProducesTwoNotificationsInOrder() throws Exception {
		BlockingQueue<String> received = listenAsTheCustomer();

		stubProjection(BookingStatus.CONFIRMED, List.of());
		assertEquals(202, post(confirmed("evt-1")).statusCode());

		Map<?, ?> first = nextMessage(received);
		assertEquals("BOOKING_CONFIRMED", first.get("type"));
		assertEquals("Your order ORD-2026-1024 has been confirmed.", first.get("message"));
		assertEquals(BOOKING_REF, first.get("bookingRef"));
		assertNotNull(first.get("notificationId"), "the client marks this id read through §9.2");

		stubProjection(BookingStatus.SCHEDULED, List.of(TECHNICIAN));
		assertEquals(202, post(scheduled("evt-2")).statusCode());

		Map<?, ?> second = nextMessage(received);
		assertEquals("BOOKING_SCHEDULED", second.get("type"));
		assertEquals("Your order ORD-2026-1024 is scheduled for 24 Aug 2026, 18:00-20:00.",
				second.get("message"), "§9.1's own sentence, in the customer's time zone");

		assertEquals(2, savedNotifications.size(), "two notifications, as §13's script expects");
	}

	@Test
	void workingRefreshesTheProjectionAndNotifiesNobody() throws Exception {
		BlockingQueue<String> received = listenAsTheCustomer();

		stubProjection(BookingStatus.WORKING, List.of(TECHNICIAN));
		HttpResponse<String> accepted = post(event("evt-3", "WORKING", false));

		assertEquals(202, accepted.statusCode(), "the event is still handled");
		assertNull(received.poll(3, SECONDS), "§11.7: other statuses refresh silently");
		assertTrue(savedNotifications.isEmpty(), "and nothing is filed for the bell either");
	}

	@Test
	void theSameEventTwiceProducesOneNotification() throws Exception {
		BlockingQueue<String> received = listenAsTheCustomer();

		assertEquals(202, post(scheduled("evt-4")).statusCode());
		assertNotNull(nextMessage(received), "the first delivery notifies");

		HttpResponse<String> replay = post(scheduled("evt-4"));

		assertEquals(202, replay.statusCode());
		assertTrue(replay.body().contains("\"duplicate\":true"),
				"§11.7 answers a replay rather than refusing it");
		assertNull(received.poll(3, SECONDS), "and pushes nothing the second time");
		assertEquals(1, savedNotifications.size());
	}

	@Test
	void theProjectionIsRefreshedBeforeTheNotificationIsFiledOrSent() throws Exception {
		// Req 3.3.S5's guarantee. Asserted on the order of the calls rather than on clock
		// readings: the refresh returning before the notification is written is what makes the
		// technician's name available on the screen the toast opens, and that is true or false
		// regardless of how fast either happened.
		BlockingQueue<String> received = listenAsTheCustomer();

		assertEquals(202, post(scheduled("evt-5")).statusCode());
		assertNotNull(nextMessage(received), "the customer is told");

		InOrder refreshThenNotify = inOrder(bookingService, notificationRepository);
		refreshThenNotify.verify(bookingService).refreshProjection(any(), any());
		refreshThenNotify.verify(notificationRepository).save(any());
	}

	@Test
	void anEventForAnUnknownCustomerNotifiesNobody() throws Exception {
		BlockingQueue<String> received = listenAsTheCustomer();
		when(customerService.findHomeRegion(CUSTOMER_ID)).thenReturn(Optional.empty());

		HttpResponse<String> refused = post(scheduled("evt-6"));

		assertEquals(404, refused.statusCode());
		assertTrue(refused.body().contains("CUSTOMER_NOT_FOUND"));
		assertNull(received.poll(2, SECONDS));
	}

	@Test
	void anEventWithoutTheSharedSecretNotifiesNobody() throws Exception {
		BlockingQueue<String> received = listenAsTheCustomer();

		HttpResponse<String> refused = send(HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port() + EVENTS))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(scheduled("evt-7")))
				.build());

		assertEquals(403, refused.statusCode());
		assertTrue(refused.body().contains("INTEGRATION_FORBIDDEN"));
		assertNull(received.poll(2, SECONDS));
	}

	// --- Stores that behave like the real ones, without touching them -----------------------

	private final List<Notification> savedNotifications =
			Collections.synchronizedList(new ArrayList<>());
	private final Map<String, IntegrationEvent> events = new ConcurrentHashMap<>();

	private void stubEventStore() {
		events.clear();

		when(eventRepository.findInbound(any(), any())).thenAnswer(invocation ->
				Optional.ofNullable(events.get(invocation.<String>getArgument(1))));
		when(eventRepository.insert(any())).thenAnswer(invocation -> {
			IntegrationEvent event = invocation.getArgument(0);
			// putIfAbsent rather than put, so a repeated insert is refused as the unique _id
			// would refuse it. The service reads that refusal as "somebody else has this".
			if (events.putIfAbsent(event.getEventId(), event) != null) {
				throw new org.springframework.dao.DuplicateKeyException(event.getEventId());
			}
			return event;
		});
		when(eventRepository.save(any())).thenAnswer(invocation -> {
			IntegrationEvent event = invocation.getArgument(0);
			events.put(event.getEventId(), event);
			return event;
		});
	}

	private void stubNotificationStore() {
		savedNotifications.clear();

		when(notificationRepository.save(any())).thenAnswer(invocation -> {
			Notification notification = invocation.getArgument(0);
			if (notification.getId() == null) {
				notification.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 24));
			}
			savedNotifications.add(notification);
			return notification;
		});
		when(notificationRepository.findBySourceEventId(any(), any())).thenAnswer(invocation -> {
			String eventId = invocation.getArgument(1);
			synchronized (savedNotifications) {
				return savedNotifications.stream()
						.filter(saved -> eventId != null && eventId.equals(saved.getSourceEventId()))
						.findFirst();
			}
		});
	}

	/**
	 * The booking module's answer. Mocked because refreshing a projection needs a booking to
	 * exist, and what this suite is about is the sequence around that call.
	 */
	private void stubProjection(BookingStatus status, List<TechnicianRef> technicians) {
		boolean scheduled = status != BookingStatus.CONFIRMED;

		when(bookingService.refreshProjection(any(), any())).thenReturn(
				new BookingProjectionView(BOOKING_REF, CUSTOMER_ID, status,
						scheduled ? VISIT_AT : null, scheduled ? 120 : null, technicians,
						Instant.now(), true));
	}

	// --- Events, as SMP sends them ----------------------------------------------------------

	private static String confirmed(String eventId) {
		return event(eventId, "CONFIRMED", false);
	}

	private static String scheduled(String eventId) {
		return event(eventId, "SCHEDULED", true);
	}

	private static String event(String eventId, String status, boolean withSchedule) {
		String payload = withSchedule
				? """
					{
					  "status": "%s",
					  "scheduledStartAt": "2026-08-24T11:00:00Z",
					  "estimatedDurationMinutes": 120,
					  "technicians": [{ "technicianId": "TEC-014", "fullName": "John Tran" }]
					}""".formatted(status)
				: "{ \"status\": \"%s\" }".formatted(status);

		return """
				{
				  "eventId": "%s",
				  "eventType": "BOOKING_STATUS_CHANGED",
				  "occurredAt": "2026-08-11T03:19:58Z",
				  "bookingRef": "%s",
				  "customerId": "%s",
				  "payload": %s
				}""".formatted(eventId, BOOKING_REF, CUSTOMER_ID, payload);
	}

	/** A POST as SMP: the shared secret in the same header §11.1 describes. */
	private HttpResponse<String> post(String body) {
		return send(HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port() + EVENTS))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + properties.getSmp().getWebhookSharedSecret())
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build());
	}

	private HttpResponse<String> send(HttpRequest request) {
		try {
			return http.send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (java.io.IOException failed) {
			throw new AssertionError("The request never reached the server", failed);
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new AssertionError("Interrupted while waiting for the response", interrupted);
		}
	}

	// --- Client plumbing --------------------------------------------------------------------

	/** Connects as the customer and waits until the server has registered the subscription. */
	private BlockingQueue<String> listenAsTheCustomer() throws Exception {
		BlockingQueue<String> received = new LinkedBlockingQueue<>();

		StompHeaders connectHeaders = new StompHeaders();
		connectHeaders.add("Authorization",
				"Bearer " + tokenProvider.generate(credential(CUSTOMER_ID)));

		CompletableFuture<StompSession> connecting = stompClient.connectAsync(
				"ws://localhost:" + port() + "/ws", (WebSocketHttpHeaders) null, connectHeaders,
				new StompSessionHandlerAdapter() {
				});
		StompSession session = connecting.get(10, SECONDS);
		openSessions.add(session);

		session.subscribe(OWN_QUEUE, new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return String.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				received.add((String) payload);
			}
		});
		awaitSubscription();

		return received;
	}

	private Map<?, ?> nextMessage(BlockingQueue<String> received) throws Exception {
		String body = received.poll(10, SECONDS);
		assertNotNull(body, "no notification arrived on the customer's queue");
		return JSON.readValue(body, Map.class);
	}

	/** So an event cannot beat the SUBSCRIBE frame to the broker. */
	private void awaitSubscription() throws InterruptedException {
		long deadline = System.nanoTime() + SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			SimpUser user = userRegistry.getUser(CUSTOMER_ID);
			if (user != null && user.getSessions().stream()
					.flatMap(s -> s.getSubscriptions().stream())
					.anyMatch(sub -> OWN_QUEUE.equals(sub.getDestination()))) {
				return;
			}
			Thread.sleep(50);
		}
		fail("The server never registered the customer's subscription to " + OWN_QUEUE);
	}

	private int port() {
		Integer port = environment.getProperty("local.server.port", Integer.class);
		assertNotNull(port, "RANDOM_PORT should have published local.server.port");
		return port;
	}

	private static UserCredential credential(String customerId) {
		return UserCredential.builder()
				.id(customerId)
				.email(customerId + "@example.com")
				.accountRole(Role.CUSTOMER)
				.build();
	}

	/** Bytes in, text out, regardless of content-type. */
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
}