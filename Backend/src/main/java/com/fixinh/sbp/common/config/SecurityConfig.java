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
						.requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/oauth/google").permitAll()
						// The address dropdown is needed to fill in the registration form, so it
						// cannot require the token registration has not issued yet (Req 3.1.S4).
						.requestMatchers("/api/v1/reference/provinces").permitAll()
						.requestMatchers("/actuator/health", "/actuator/info").permitAll()
						// Req A.2.a: staff/customer must not reach admin resources.
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						// /bookings/* is CUSTOMER-only (API Doc §2.4 authorization matrix); an
						// admin token is authenticated but not permitted, so it gets 403.
						.requestMatchers("/api/v1/bookings/**").hasRole("CUSTOMER")
						.requestMatchers("/api/v1/notifications/**")
						.hasAnyRole("CUSTOMER", "ADMIN")
						// Everything else is closed by default, so an endpoint added later is
						// protected unless it is deliberately opened.
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
