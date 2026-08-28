package com.fixinh.sbp.modules.auth.dto.internal;

import jakarta.validation.constraints.NotBlank;

/** The refresh request. Presence is all that is validated — an unknown or expired token is a
 * runtime failure (AUTH_REFRESH_TOKEN_INVALID / _EXPIRED), not a validation failure. */
public record RefreshCommand(
		@NotBlank(message = "A refresh token is required.")
		String refreshToken) {
}
