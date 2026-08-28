package com.fixinh.sbp.modules.auth.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fixinh.sbp.common.security.jwt.UserPrincipal;
import com.fixinh.sbp.modules.auth.dto.external.AuthTokenDto;
import com.fixinh.sbp.modules.auth.dto.external.RegisteredCustomerDto;
import com.fixinh.sbp.modules.auth.dto.internal.GoogleLoginCommand;
import com.fixinh.sbp.modules.auth.dto.internal.LoginCommand;
import com.fixinh.sbp.modules.auth.dto.internal.LogoutCommand;
import com.fixinh.sbp.modules.auth.dto.internal.RefreshCommand;
import com.fixinh.sbp.modules.auth.dto.internal.RegisterCommand;
import com.fixinh.sbp.modules.auth.interfaces.external.AuthExternalService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Base path /auth (API Documentation Section 4).
 *
 * <p>Registration, sign-in and refresh are public: a user must reach them before holding a
 * token, and refresh exists specifically for when the token they already had has expired.
 * Logout is the one exception — it requires the token it revokes. Everything else in the API
 * requires a token these endpoints issued.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthExternalService authService;

	/** Section 4.1. Creates a customer account and sends the customer to sign in. */
	@PostMapping("/register")
	public ResponseEntity<RegisteredCustomerDto> register(@RequestBody RegisterCommand command) {
		RegisteredCustomerDto registered = authService.register(command);

		return ResponseEntity
				.created(URI.create("/api/v1/customers/" + registered.customerId()))
				.body(registered);
	}

	/** Section 4.2. Authenticates a customer or administrator and issues a JWT. */
	@PostMapping("/login")
	public AuthTokenDto login(@Valid @RequestBody LoginCommand command) {
		return authService.login(command);
	}

	/** Section 4.3. Authenticates via Google OAuth 2.0 and issues a JWT (Req 3.2.U1-U3). */
	@PostMapping("/oauth/google")
	public AuthTokenDto loginWithGoogle(@Valid @RequestBody GoogleLoginCommand command) {
		return authService.loginWithGoogle(command);
	}

	/** Exchanges a refresh token for a new access/refresh pair. Public: the client has no
	 * valid access token to send by the time it needs this. */
	@PostMapping("/refresh")
	public AuthTokenDto refresh(@Valid @RequestBody RefreshCommand command) {
		return authService.refresh(command);
	}

	/**
	 * Revokes the caller's own access token immediately, rather than waiting for it to expire.
	 *
	 * <p>Deliberately requires a valid Bearer token — that token is the thing being revoked, so
	 * there is no other way to know which {@code jti} to blacklist. An already-expired or
	 * already-revoked token is rejected by the filter before this runs, which is fine: a dead
	 * token needs no further revoking.
	 */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
		authService.logout(new LogoutCommand(principal.id(), principal.tokenId(), principal.expiresAt()));
		return ResponseEntity.noContent().build();
	}
}
