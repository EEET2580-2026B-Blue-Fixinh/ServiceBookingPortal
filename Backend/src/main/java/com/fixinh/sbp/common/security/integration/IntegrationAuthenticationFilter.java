package com.fixinh.sbp.common.security.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Admits the Service Management Portal to {@code /api/v1/integration/**} and nobody else
 * (Req 4.M2, §11.1).
 *
 * <p>SMP presents {@code Authorization: Bearer <shared secret>} — the mirror image of the
 * {@code service-token} SBP sends on every call to SMP. §11.1 describes a token exchange
 * that would put a signed JWT in that header instead; when that arrives it replaces the
 * comparison below and nothing else, because everything downstream sees only the
 * {@link ServicePrincipal} and the {@code SERVICE} role this filter sets.
 *
 * <p>Scoped to the integration paths and to nothing else, and {@code JwtAuthenticationFilter}
 * is scoped to their complement, so the two never read the same header. A customer JWT on an
 * integration path is therefore not "a valid token in the wrong place" — it is simply not the
 * secret, and gets the 403 §11.1 promises rather than a 401 that would confirm the token was
 * recognised.
 *
 * <p>Every refusal is {@code 403 INTEGRATION_FORBIDDEN}, whether the header is missing, is not
 * a bearer, or carries the wrong value. One answer, so the response cannot be used to tell
 * "no secret" from "a wrong secret" — and the comparison is constant-time, so its duration
 * cannot be used to tell a nearly right one from a wrong one.
 */
@Component
@Slf4j
public class IntegrationAuthenticationFilter extends OncePerRequestFilter {

	/** Spring Security's convention for a role authority, applied to Appendix A's SERVICE. */
	public static final String SERVICE_AUTHORITY = "ROLE_SERVICE";

	private static final String INTEGRATION_PATH = "/api/v1/integration";
	private static final String BEARER_PREFIX = "Bearer ";

	private final SbpProperties properties;
	private final HandlerExceptionResolver handlerExceptionResolver;

	/**
	 * @param handlerExceptionResolver filters run before the DispatcherServlet, so a thrown
	 *        {@code ApiException} would never reach {@code GlobalExceptionHandler}; handing it
	 *        to the resolver routes it back through, and the 403 carries §2.7's body
	 */
	public IntegrationAuthenticationFilter(SbpProperties properties,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {

		this.properties = properties;
		this.handlerExceptionResolver = handlerExceptionResolver;

		if (isBlank(properties.getSmp().getWebhookSharedSecret())) {
			// Not fatal, on purpose: the rest of the application is unaffected. But a blank
			// secret matches nothing, so this is the moment to say every call will be refused,
			// rather than leaving it to be discovered from the first 403.
			log.warn("fixinh.sbp.smp.webhook-shared-secret is not set; every call to {} will "
					+ "be refused", INTEGRATION_PATH);
		}
	}

	/**
	 * Whether a request is one this filter decides — and therefore one the JWT filter must
	 * leave alone. Defined once, here, so the two cannot disagree about the boundary.
	 */
	public static boolean covers(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return path.equals(INTEGRATION_PATH) || path.startsWith(INTEGRATION_PATH + "/");
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !covers(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		if (!presentsTheSecret(request)) {
			SecurityContextHolder.clearContext();
			// The path and the origin, never the value that was presented: a wrong secret is
			// often a nearly right one, and the application log is not where it belongs.
			log.warn("Refused {} {} from {}: the caller is not SMP", request.getMethod(),
					request.getRequestURI(), request.getRemoteAddr());
			handlerExceptionResolver.resolveException(request, response, null,
					new ApiException(ErrorCode.INTEGRATION_FORBIDDEN));
			return;
		}

		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
				ServicePrincipal.SMP, null, List.of(new SimpleGrantedAuthority(SERVICE_AUTHORITY)));
		authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
		SecurityContextHolder.getContext().setAuthentication(authentication);

		filterChain.doFilter(request, response);
	}

	private boolean presentsTheSecret(HttpServletRequest request) {
		String expected = properties.getSmp().getWebhookSharedSecret();
		if (isBlank(expected)) {
			// A blank expectation would match a blank presentation. Refusing first is what
			// stops "not configured" from meaning "open".
			return false;
		}

		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return false;
		}
		String presented = header.substring(BEARER_PREFIX.length());

		// Constant-time, so a mismatch in the first byte takes as long to refuse as one in
		// the last. String.equals stops at the first difference, and that difference in
		// timing is measurable from outside.
		return MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8),
				presented.getBytes(StandardCharsets.UTF_8));
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
