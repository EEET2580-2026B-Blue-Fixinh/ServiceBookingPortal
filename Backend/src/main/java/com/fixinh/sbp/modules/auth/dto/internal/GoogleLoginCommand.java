package com.fixinh.sbp.modules.auth.dto.internal;

import jakarta.validation.constraints.NotBlank;

/**
 * Input for Google OAuth sign-in (API Documentation Section 4.3, Req 3.2.U1).
 *
 * <p>The frontend receives an authorization code from Google's consent screen, then posts it
 * here. The backend exchanges the code for an ID token, verifies it, extracts the user's profile,
 * and either finds the existing account or registers a new one.
 */
public record GoogleLoginCommand(
		/** The authorization code Google redirected the user with. */
		@NotBlank(message = "Authorization code is required")
		String authorizationCode,
		@NotBlank(message = "Redirect URI is required") String redirectUri
) {}
