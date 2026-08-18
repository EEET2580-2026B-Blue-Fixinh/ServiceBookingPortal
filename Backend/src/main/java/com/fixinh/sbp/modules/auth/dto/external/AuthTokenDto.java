package com.fixinh.sbp.modules.auth.dto.external;

import com.fixinh.sbp.common.security.Role;

/**
 * What a successful sign-in returns (API Documentation Sections 4.2, 4.3).
 *
 * <p>{@code role} is what decides where the frontend sends the user next and which routes
 * ProtectedRoute admits (Req A.2.a).
 *
 * <p>{@code accountCreated} is true for Google sign-ins that just registered a new account.
 * {@code profileCompletionRequired} is true when a Google user has no address yet and must
 * fill one in before booking (Req 3.2.U3).
 */
public record AuthTokenDto(
		String accessToken,
		String tokenType,
		/** Seconds until the token expires. */
		long expiresIn,
		AuthUserDto user,
		/** True if this sign-in just created a new account (Google OAuth only). */
		Boolean accountCreated,
		/** True if the user must complete their profile before booking (Google OAuth only). */
		Boolean profileCompletionRequired) {

	public record AuthUserDto(
			String userId,
			String fullName,
			String email,
			Role role) {
	}

	/** Convenience constructors for password-based login (no Google-specific flags). */
	public AuthTokenDto(
			String accessToken,
			String tokenType,
			long expiresIn,
			AuthUserDto user) {
		this(accessToken, tokenType, expiresIn, user, null, null);
	}
}
