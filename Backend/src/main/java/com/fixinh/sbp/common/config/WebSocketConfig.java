package com.fixinh.sbp.common.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.fixinh.sbp.modules.notification.websocket.StompAuthChannelInterceptor;
import com.fixinh.sbp.modules.notification.websocket.StompErrorHandler;

import lombok.RequiredArgsConstructor;

/**
 * The real-time channel of API Documentation §9.3 (Reqs 3.3.U2, 4.M3): STOMP over WebSocket,
 * with a SockJS fallback, used only to push notifications to the customer they belong to.
 *
 * <p>Two decisions shape everything here:
 *
 * <ul>
 *   <li><b>Outbound only.</b> Appendix E #1 is settled as REST push — SMP calls the §11.7
 *       endpoint, and this channel never receives anything from SMP or from a client. So
 *       there is no application destination prefix: nothing is routed to a
 *       {@code @MessageMapping}, and a SEND has nowhere legitimate to go. The interceptor
 *       refuses SENDs outright for the same reason.</li>
 *   <li><b>Authentication happens at the STOMP layer, not the HTTP one.</b> SockJS cannot
 *       attach an {@code Authorization} header to the handshake, so {@code /ws} is open in
 *       {@link SecurityConfig} and the bearer token travels in the CONNECT frame instead,
 *       where {@link StompAuthChannelInterceptor} checks it. A session without an accepted
 *       CONNECT has no user, and a user-scoped push to it resolves to nothing.</li>
 * </ul>
 *
 * <p>The path is {@code /ws} (§2.1), at the root rather than under {@code /api/v1}: it is a
 * transport endpoint, not a resource. {@code /user/queue/notifications} is the STOMP
 * destination clients subscribe to, a different layer from the URL entirely.
 *
 * <p>Allowed origins are the same explicit list HTTP CORS uses (Req A.2.6), applied
 * separately because the SockJS handshake is served by Spring's WebSocket support rather
 * than through the MVC CORS filter. Heartbeats are left at their default (off): SockJS
 * already keeps idle connections alive with its own 25-second frames, and a backend restart
 * closes the socket outright, which is what triggers the client's reconnect.
 *
 * <p>This is a stateful channel — a customer is bound to the instance holding their socket.
 * Fine for a single instance; a second one needs sticky sessions or a shared broker
 * (Appendix E #9).
 *
 * <p>Messages enter this channel in exactly one place: the notification module's gateway,
 * which calls {@code convertAndSendToUser} with the recipient's identifier.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	/** §2.1: the WebSocket endpoint, at the root. */
	public static final String ENDPOINT = "/ws";

	/** The broker prefix; every destination a client can subscribe to lives under it. */
	public static final String QUEUE_PREFIX = "/queue";

	/** The prefix that turns {@code /queue/...} into a per-user destination. */
	public static final String USER_PREFIX = "/user";

	private final SbpProperties properties;
	private final StompAuthChannelInterceptor authInterceptor;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		List<String> origins = properties.getCors().getAllowedOrigins();
		if (origins.isEmpty()) {
			// Spring would reject this too, but with a message that says nothing about where
			// the list comes from. An empty list is not a permissive default; it is a
			// misconfiguration that would refuse every browser.
			throw new IllegalStateException("fixinh.sbp.cors.allowed-origins is empty, so the "
					+ "STOMP endpoint at " + ENDPOINT + " would refuse every browser origin.");
		}

		registry.setErrorHandler(new StompErrorHandler());
		registry.addEndpoint(ENDPOINT)
				.setAllowedOrigins(origins.toArray(String[]::new))
				.withSockJS();
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		// The simple in-memory broker is enough for one instance; see the class comment for
		// what a second instance would need.
		registry.enableSimpleBroker(QUEUE_PREFIX);
		registry.setUserDestinationPrefix(USER_PREFIX);
		// Deliberately no setApplicationDestinationPrefixes: outbound only.
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		// Registered here so it runs before the ImmutableMessageChannelInterceptor Spring
		// appends afterwards; the interceptor depends on the CONNECT frame still being
		// mutable when it attaches the user.
		registration.interceptors(authInterceptor);
	}
}
