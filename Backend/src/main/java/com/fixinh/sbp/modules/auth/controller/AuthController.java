package com.fixinh.sbp.modules.auth.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fixinh.sbp.common.security.jwt.UserPrincipal;
import com.fixinh.sbp.modules.auth.dto.external.AuthTokenDto;
import com.fixinh.sbp.modules.auth.dto.external.RegisteredCustomerDto;
import com.fixinh.sbp.modules.auth.dto.internal.CompleteProfileCommand;
import com.fixinh.sbp.modules.auth.dto.internal.GoogleLoginCommand;
import com.fixinh.sbp.modules.auth.dto.internal.LoginCommand;
import com.fixinh.sbp.modules.auth.dto.internal.RegisterCommand;
import com.fixinh.sbp.modules.auth.interfaces.external.AuthExternalService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Base path /auth (API Documentation Section 4).
 *
 * <p>These two endpoints are public because a user must reach registration and sign-in before
 * holding a token. Everything else in the API requires the token they issue.
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

	/**
	 * Completes a Google OAuth user's profile by adding their address (Req 3.2.U3).
	 *
	 * <p>Google users can sign in without an address, but they must provide one before booking
	 * a service so the system can assign them to a region and match them with nearby providers.
	 */
	@PutMapping("/profile/complete")
	public ResponseEntity<Void> completeProfile(
			@AuthenticationPrincipal UserPrincipal principal,
			@Valid @RequestBody CompleteProfileCommand command) {
		authService.completeProfile(principal.id(), command);
		return ResponseEntity.noContent().build();
	}
}
