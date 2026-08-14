package com.fixinh.sbp.common.security.jwt;

import java.io.IOException;
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

import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.modules.customer.model.Role;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Turns a {@code Authorization: Bearer <token>} header into an authenticated SecurityContext
 * (Req 3.2.M2).
 *
 * <p>A request with no token passes straight through unauthenticated — whether that is allowed
 * is the filter chain's decision, not this filter's, so public endpoints stay public. A request
 * with a <em>broken</em> token is rejected here, through the same error body as everything else.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider tokenProvider;

	/**
	 * Filters run before the DispatcherServlet, so @RestControllerAdvice never sees exceptions
	 * thrown here. Handing them to the resolver routes them back through it.
	 */
	@Qualifier("handlerExceptionResolver")
	private final HandlerExceptionResolver handlerExceptionResolver;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			Claims claims = tokenProvider.parseAndValidate(header.substring(BEARER_PREFIX.length()));

			UserPrincipal principal = new UserPrincipal(
					claims.getSubject(),
					JwtTokenProvider.emailOf(claims),
					Role.valueOf(JwtTokenProvider.roleOf(claims)));

			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
					principal, null, List.of(new SimpleGrantedAuthority(principal.authority())));
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

			SecurityContextHolder.getContext().setAuthentication(authentication);
		} catch (ApiException ex) {
			SecurityContextHolder.clearContext();
			handlerExceptionResolver.resolveException(request, response, null, ex);
			return;
		} catch (IllegalArgumentException ex) {
			// A role claim naming something outside the enum — a token we did not issue.
			SecurityContextHolder.clearContext();
			handlerExceptionResolver.resolveException(request, response, null,
					new ApiException(ErrorCode.AUTH_TOKEN_INVALID));
			return;
		}

		filterChain.doFilter(request, response);
	}
}
