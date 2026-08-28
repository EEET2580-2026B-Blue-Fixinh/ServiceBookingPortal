package com.fixinh.sbp.common.security.jwt;

import java.time.Instant;

import com.fixinh.sbp.common.security.Role;

/**
 * The authenticated caller, rebuilt from the token's claims on every request.
 *
 * <p>{@code id} is the JWT {@code sub} — the customer identifier used everywhere else in the
 * API. Ownership checks read it from here, never from a request parameter (Req A.2.a).
 *
 * <p>{@code tokenId} and {@code expiresAt} are the token's own {@code jti} and {@code exp} —
 * logout needs both to blacklist exactly this token, not just to know who the caller is.
 */
public record UserPrincipal(String id, String email, Role role, String tokenId, Instant expiresAt) {

	/** Spring Security's convention: authorities are the role name with a ROLE_ prefix. */
	public String authority() {
		return "ROLE_" + role.name();
	}
}
