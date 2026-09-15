package com.fixinh.mocksmp.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Every path except /actuator/** needs {@code Authorization: Bearer <token>}, matching the
 * shared-secret convention SBP's live SMP clients already use.
 */
@Component
public class ServiceTokenFilter extends OncePerRequestFilter {

	private final String expectedToken;

	public ServiceTokenFilter(@Value("${mocksmp.service-token:}") String expectedToken) {
		this.expectedToken = expectedToken;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		if (request.getRequestURI().startsWith("/actuator")) {
			chain.doFilter(request, response);
			return;
		}

		String header = request.getHeader("Authorization");
		if (expectedToken.isBlank() || !("Bearer " + expectedToken).equals(header)) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType("application/json");
			response.getWriter().write(
					"{\"error\":\"FORBIDDEN\",\"message\":\"Missing or invalid bearer token.\"}");
			return;
		}

		chain.doFilter(request, response);
	}
}
