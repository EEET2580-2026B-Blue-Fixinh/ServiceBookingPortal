package com.fixinh.sbp.modules.auth.dto.internal;

import jakarta.validation.constraints.NotBlank;

/**
 * The sign-in request (API Documentation Section 4.2).
 *
 * <p>Presence is all that is validated here — the rules that shape a valid email or password
 * belong to registration. Rejecting a malformed identifier differently from an unknown one
 * would leak which accounts exist.
 */
public record LoginCommand(

		/** Either the email address or the phone number; the server works out which. */
		@NotBlank(message = "Enter your email or phone number.")
		String identifier,

		@NotBlank(message = "Enter your password.")
		String password) {
}
