package com.fixinh.sbp.common.security.blacklist;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A revoked access token, keyed by its {@code jti}. Present in both regional stores — see
 * {@link TokenBlacklistRepository} — so a request can be checked without knowing which shard
 * the caller's account lives in. {@link #expiresAt} is copied from the access token's own
 * {@code exp} claim; a TTL index (see {@code ShardIndexInitializer}) removes the document the
 * moment the token would have expired anyway, so revocation never outlives the token it revokes
 * and the collection never grows without bound.
 */
@Document(collection = "revokedAccessTokens")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistedToken {

	@Id
	private String id; // the jti

	private Instant expiresAt;
}
