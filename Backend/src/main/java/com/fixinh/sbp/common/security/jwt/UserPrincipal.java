package com.fixinh.sbp.common.security.jwt;

import java.security.Principal;
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
 *
 * <p>A {@link Principal} whose {@link #getName() name} is the {@code id}, because the STOMP
 * layer routes by name: {@code convertAndSendToUser(recipientId, ...)} (§9.3) is resolved
 * against {@code Principal.getName()} of whoever CONNECTed. Before this, a Spring Security
 * {@code Authentication} holding one of these answered {@code getName()} with the record's
 * {@code toString()}, which no customer identifier would ever match — a push would go nowhere,
 * silently. Nothing on the HTTP side reads the name, so nothing there changes.
 */
public record UserPrincipal(String id, String email, Role role, String tokenId, Instant expiresAt)
		implements Principal {

	/** The routing key for user-scoped messaging, and the same id the REST API uses. */
	@Override
	public String getName() {
		return id;
	}

	/** Spring Security's convention: authorities are the role name with a ROLE_ prefix. */
	public String authority() {
		return "ROLE_" + role.name();
	}
}
