package com.fixinh.sbp.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.fixinh.sbp.common.security.JsonAuthenticationEntryPoint;
import com.fixinh.sbp.common.security.integration.IntegrationAuthenticationFilter;
import com.fixinh.sbp.common.security.jwt.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/**
 * The filter chain (Reqs 3.2.M2, 3.2.M3, A.2.5, A.2.6).
 *
 * <p>Stateless: there is no session to fix, so CSRF protection is disabled and every request
 * carries its own bearer token.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final IntegrationAuthenticationFilter integrationAuthenticationFilter;
	private final JsonAuthenticationEntryPoint authenticationEntryPoint;
	private final CorsConfigurationSource corsConfigurationSource;

	/** Req 3.1: passwords are hashed before storage and compared by this encoder on sign-in. */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
				.authorizeHttpRequests(auth -> auth
						// Public: a user must reach registration and sign-in before holding a token.
						// /auth/refresh is public too — it exists precisely for when the access
						// token has already expired, so there is no token left to send.
						// /auth/logout is deliberately NOT here: it must go through
						// JwtAuthenticationFilter so UserPrincipal (and its tokenId/expiresAt) is
						// populated, since logout blacklists exactly the token that authenticated it.
						.requestMatchers("/api/v1/auth/register", "/api/v1/auth/login",
								"/api/v1/auth/oauth/google", "/api/v1/auth/refresh").permitAll()
						// The address dropdown is needed to fill in the registration form, so it
						// cannot require the token registration has not issued yet (Req 3.1.S4).
						.requestMatchers("/api/v1/reference/provinces").permitAll()
						.requestMatchers("/actuator/health", "/actuator/info").permitAll()
						// The WebSocket handshake is unauthenticated by design (API Doc §9.3):
						// SockJS cannot put a bearer token on it. The token travels in the STOMP
						// CONNECT frame instead and is checked there by
						// StompAuthChannelInterceptor, so nothing reaches a customer's queue
						// without the same JWT the REST API demands. /ws/** covers SockJS's
						// /ws/info and per-session transport URLs.
						.requestMatchers("/ws", "/ws/**").permitAll()
						// Req A.2.a: staff/customer must not reach admin resources.
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						// /bookings/* is CUSTOMER-only (API Doc §2.4 authorization matrix); an
						// admin token is authenticated but not permitted, so it gets 403.
						.requestMatchers("/api/v1/bookings/**").hasRole("CUSTOMER")
						.requestMatchers("/api/v1/ratings/**").hasRole("CUSTOMER")
						.requestMatchers("/api/v1/notifications/**")
						.hasAnyRole("CUSTOMER", "ADMIN")
						// Req 4.M2, API Doc §11.1: only SMP reaches /integration, and it does so
						// with the shared secret rather than a JWT. IntegrationAuthenticationFilter
						// authenticates it and answers everything else with 403 before this
						// matcher is consulted; the matcher is still here so that the rule is
						// visible where every other path's rule is, and holds even if the
						// filter's scope ever drifted.
						.requestMatchers("/api/v1/integration/**").hasRole("SERVICE")
						// Everything else is closed by default, so an endpoint added later is
						// protected unless it is deliberately opened.
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				// Ahead of the JWT filter, though the two are scoped to disjoint paths and the
				// order is not what keeps them apart -- IntegrationAuthenticationFilter.covers
				// is. Listed first because it is the more restrictive of the two.
				.addFilterBefore(integrationAuthenticationFilter, JwtAuthenticationFilter.class);

		return http.build();
	}
}