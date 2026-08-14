package com.fixinh.sbp.common.security;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.dto.ErrorResponse;
import com.fixinh.sbp.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Answers unauthenticated requests with the standard error body instead of Spring Security's
 * default page.
 *
 * <p>Section 2.7 requires <em>every</em> failure to share one shape — an auth rejection that
 * returned HTML would be the one response the frontend could not parse.
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {

		ErrorResponse body = new ErrorResponse(
				Instant.now().truncatedTo(ChronoUnit.SECONDS),
				request.getRequestURI(),
				ErrorCode.AUTH_TOKEN_MISSING.getHttpStatus().value(),
				ErrorCode.AUTH_TOKEN_MISSING.name(),
				ErrorCode.AUTH_TOKEN_MISSING.getMessage(),
				null,
				UUID.randomUUID().toString());

		response.setStatus(ErrorCode.AUTH_TOKEN_MISSING.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), body);
	}
}
