package com.fixinh.sbp.common.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.fixinh.sbp.modules.auth.model.UserCredential;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and validates the access tokens described in API Documentation Section 2.3
 * (Reqs 3.2.M1–3.2.M3).
 *
 * <p>Claims: {@code sub} (user id), {@code email}, {@code role}, {@code iat}/{@code exp}, and
 * {@code jti} so a token can be denylisted later without changing the modules.
 */
@Component
public class JwtTokenProvider {

	private static final String CLAIM_EMAIL = "email";
	private static final String CLAIM_ROLE = "role";

	private final SecretKey signingKey;
	private final String issuer;
	private final Duration accessTokenTtl;

	public JwtTokenProvider(SbpProperties properties) {
		SbpProperties.Jwt jwt = properties.getJwt();
		// HS256 needs >= 32 bytes; a short secret fails loudly here rather than at first login.
		this.signingKey = Keys.hmacShaKeyFor(jwt.getSecret().getBytes(StandardCharsets.UTF_8));
		this.issuer = jwt.getIssuer();
		this.accessTokenTtl = Duration.ofMinutes(jwt.getAccessTokenTtlMinutes());
	}

	/**
	 * Built from the credential rather than a profile: the credential is what authentication
	 * actually verified, and its discriminator is the authoritative role.
	 */
	public String generate(UserCredential credential) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plus(accessTokenTtl);

		return Jwts.builder()
				.subject(credential.getId())
				.issuer(issuer)
				.claim(CLAIM_EMAIL, credential.getEmail())
				.claim(CLAIM_ROLE, credential.getAccountRole().name())
				.id(UUID.randomUUID().toString())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(expiresAt))
				.signWith(signingKey)
				.compact();
	}

	/**
	 * @throws ApiException AUTH_TOKEN_EXPIRED when past its expiry, AUTH_TOKEN_INVALID when
	 *         malformed, wrongly signed, or issued by someone else
	 */
	public Claims parseAndValidate(String token) {
		try {
			return Jwts.parser()
					.verifyWith(signingKey)
					.requireIssuer(issuer)
					.build()
					.parseSignedClaims(token)
					.getPayload();
		} catch (ExpiredJwtException ex) {
			throw new ApiException(ErrorCode.AUTH_TOKEN_EXPIRED);
		} catch (JwtException | IllegalArgumentException ex) {
			throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
		}
	}

	/** What /auth/login reports as {@code expiresIn}. */
	public long getAccessTokenTtlSeconds() {
		return accessTokenTtl.toSeconds();
	}

	public static String emailOf(Claims claims) {
		return claims.get(CLAIM_EMAIL, String.class);
	}

	public static String roleOf(Claims claims) {
		return claims.get(CLAIM_ROLE, String.class);
	}
}
