package com.fixinh.sbp.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import com.fixinh.sbp.modules.notification.websocket.StompAuthChannelInterceptor;
import com.fixinh.sbp.modules.notification.websocket.StompErrorHandler;

/**
 * Pins the decisions in {@link WebSocketConfig} that a later edit could quietly undo: the
 * path, the origin list, the fallback, the error handler, the broker layout and the
 * interceptor. Whether the resulting channel actually works end to end is a question for a
 * running server, not for mocks of fluent registries.
 */
class WebSocketConfigTest {

	private static final List<String> ORIGINS =
			List.of("https://localhost:3000", "http://localhost:3000");

	private SbpProperties properties;
	private StompAuthChannelInterceptor interceptor;
	private WebSocketConfig config;

	@BeforeEach
	void setUp() {
		properties = new SbpProperties();
		properties.getCors().setAllowedOrigins(ORIGINS);
		interceptor = mock(StompAuthChannelInterceptor.class);
		config = new WebSocketConfig(properties, interceptor);
	}

	@Test
	void registersTheEndpointAtWsWithTheConfiguredOriginsAndSockJs() {
		StompEndpointRegistry registry = mock(StompEndpointRegistry.class, RETURNS_SELF);
		StompWebSocketEndpointRegistration registration =
				mock(StompWebSocketEndpointRegistration.class, RETURNS_SELF);
		when(registry.addEndpoint(WebSocketConfig.ENDPOINT)).thenReturn(registration);

		config.registerStompEndpoints(registry);

		assertEquals("/ws", WebSocketConfig.ENDPOINT,
				"Section 2.1 says /ws, at the root; the subscription destination is a different layer");
		verify(registry).addEndpoint("/ws");
		verify(registration).setAllowedOrigins("https://localhost:3000", "http://localhost:3000");
		verify(registration).withSockJS();
	}

	@Test
	void installsTheErrorHandlerThatPutsCodesOnErrorFrames() {
		StompEndpointRegistry registry = mock(StompEndpointRegistry.class, RETURNS_SELF);
		when(registry.addEndpoint(any())).thenReturn(
				mock(StompWebSocketEndpointRegistration.class, RETURNS_SELF));

		config.registerStompEndpoints(registry);

		verify(registry).setErrorHandler(any(StompErrorHandler.class));
	}

	@Test
	void refusesToStartWithNoAllowedOrigins() {
		properties.getCors().setAllowedOrigins(List.of());
		StompEndpointRegistry registry = mock(StompEndpointRegistry.class, RETURNS_SELF);

		assertThrows(IllegalStateException.class, () -> config.registerStompEndpoints(registry),
				"an empty origin list is a misconfiguration, not a permissive default");
		verifyNoInteractions(registry);
	}

	@Test
	void brokerServesQueueUnderUserAndAcceptsNothingInbound() {
		MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

		config.configureMessageBroker(registry);

		verify(registry).enableSimpleBroker("/queue");
		verify(registry).setUserDestinationPrefix("/user");
		// In particular no setApplicationDestinationPrefixes: there is nothing to route a
		// client's SEND to, because clients never send.
		verifyNoMoreInteractions(registry);
	}

	@Test
	void putsTheAuthInterceptorOnTheInboundChannel() {
		ChannelRegistration registration = mock(ChannelRegistration.class);

		config.configureClientInboundChannel(registration);

		verify(registration).interceptors(interceptor);
	}
}
