package com.fixinh.sbp.modules.auth.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The account's one active refresh token — see the refresh-token README under docs/ for why
 * an account holds only one at a time rather than one per device. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRecord {

	/** SHA-256 hex digest of the full raw refresh token — never the raw value itself. */
	private String tokenHash;

	private Instant issuedAt;

	private Instant expiresAt;
}
