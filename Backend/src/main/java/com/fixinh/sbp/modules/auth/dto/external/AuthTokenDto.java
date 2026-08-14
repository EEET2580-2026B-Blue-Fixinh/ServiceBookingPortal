package com.fixinh.sbp.modules.auth.dto.external;

import com.fixinh.sbp.modules.customer.model.Role;

/**
 * What a successful sign-in returns (API Documentation Section 4.2).
 *
 * <p>{@code role} is what decides where the frontend sends the user next and which routes
 * ProtectedRoute admits (Req A.2.a).
 */
public record AuthTokenDto(
		String accessToken,
		String tokenType,
		/** Seconds until the token expires. */
		long expiresIn,
		AuthUserDto user) {

	public record AuthUserDto(
			String userId,
			String fullName,
			String email,
			Role role) {
	}
}
