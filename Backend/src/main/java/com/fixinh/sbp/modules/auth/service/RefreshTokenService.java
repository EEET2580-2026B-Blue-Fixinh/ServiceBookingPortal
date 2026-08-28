package com.fixinh.sbp.modules.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.common.security.AccountStatus;
import com.fixinh.sbp.modules.auth.model.RefreshTokenRecord;
import com.fixinh.sbp.modules.auth.model.UserCredential;
import com.fixinh.sbp.modules.auth.repository.UserCredentialRepository;

/**
 * Issues, verifies and rotates the one refresh token an account may hold at a time.
 *
 * <p>The raw token is {@code "<credentialId>.<32 random bytes, base64url>"}. The prefix is what
 * lets {@link #verifyAndRotate} find the account without knowing which regional shard it lives
 * in, reusing {@link UserCredentialRepository#findById} rather than adding a second cross-shard
 * lookup — this is called from {@code /auth/refresh}, which has no access token to route by.
 * Only the SHA-256 hash of the whole string is ever stored.
 */
@Service
public class RefreshTokenService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final UserCredentialRepository credentialRepository;
	private final Duration refreshTokenTtl;

	public RefreshTokenService(UserCredentialRepository credentialRepository, SbpProperties properties) {
		this.credentialRepository = credentialRepository;
		this.refreshTokenTtl = Duration.ofDays(properties.getJwt().getRefreshTokenTtlDays());
	}

	/** Mutates {@code credential} in place; the caller is responsible for saving it. */
	public String issue(UserCredential credential) {
		byte[] secret = new byte[32];
		RANDOM.nextBytes(secret);
		String raw = credential.getId() + "." + ENCODER.encodeToString(secret);

		Instant now = Instant.now();
		credential.setRefreshToken(new RefreshTokenRecord(sha256Hex(raw), now, now.plus(refreshTokenTtl)));
		return raw;
	}

	/** Clears the account's active refresh token. The caller is responsible for saving it. */
	public void revoke(UserCredential credential) {
		credential.setRefreshToken(null);
	}

	/**
	 * Verifies {@code rawToken}, rotates it, and returns the account plus the new raw value.
	 * The account is saved before returning; callers do not need to save again.
	 *
	 * @throws ApiException AUTH_REFRESH_TOKEN_INVALID (unknown, malformed, or already
	 *         superseded by a later rotation — treated as possible theft, so the active token
	 *         is revoked too), AUTH_REFRESH_TOKEN_EXPIRED, or AUTH_ACCOUNT_DEACTIVATED
	 */
	public RotationResult verifyAndRotate(String rawToken) {
		int separator = rawToken == null ? -1 : rawToken.indexOf('.');
		if (separator <= 0) {
			throw new ApiException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
		}

		String credentialId = rawToken.substring(0, separator);
		UserCredential credential = credentialRepository.findById(credentialId)
				.orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID));

		RefreshTokenRecord stored = credential.getRefreshToken();
		if (stored == null || !constantTimeEquals(sha256Hex(rawToken), stored.getTokenHash())) {
			// Either never issued, or this is an already-rotated (or forged) token being
			// replayed. Revoking the active one too turns a stolen-and-reused token into a
			// forced re-login instead of a silent compromise.
			revoke(credential);
			credentialRepository.save(credential);
			throw new ApiException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
		}

		if (Instant.now().isAfter(stored.getExpiresAt())) {
			revoke(credential);
			credentialRepository.save(credential);
			throw new ApiException(ErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
		}

		if (credential.getAccountStatus() == AccountStatus.DEACTIVATED) {
			throw new ApiException(ErrorCode.AUTH_ACCOUNT_DEACTIVATED);
		}

		String rotated = issue(credential);
		credentialRepository.save(credential);
		return new RotationResult(credential, rotated);
	}

	private String sha256Hex(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException ex) {
			// SHA-256 is guaranteed present on every JVM; this cannot happen.
			throw new IllegalStateException(ex);
		}
	}

	/** Timing-safe comparison — matches this codebase's existing care in AuthServiceImpl.matches(). */
	private boolean constantTimeEquals(String a, String b) {
		return MessageDigest.isEqual(
				a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

	public record RotationResult(UserCredential credential, String rawRefreshToken) {
	}
}
