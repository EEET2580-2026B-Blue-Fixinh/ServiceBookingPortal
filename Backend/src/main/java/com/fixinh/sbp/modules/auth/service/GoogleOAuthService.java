package com.fixinh.sbp.modules.auth.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Google OAuth 2.0 code exchange and ID token verification (Req 3.2.U1-U2).
 *
 * <p>The frontend receives an authorization code from Google's OAuth consent screen, then posts
 * it to /auth/oauth/google. This service swaps that code for an ID token, verifies it, and
 * extracts the user's Google profile (email, name, picture).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService {
	private static final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

	private final SbpProperties sbpProperties;

	/**
	 * Exchanges the authorization code for a verified Google user profile.
	 *
	 * @param code Authorization code from Google's redirect.
	 * @param redirectUri Redirect URI used to request the code.
	 * @return Verified payload containing email, name, and picture.
	 * @throws ApiException OAUTH_CODE_INVALID or OAUTH_PROVIDER_UNAVAILABLE.
	 */
	public GoogleUserInfo verifyCodeAndGetUser(String code, String redirectUri) {
		String configuredRedirectUri = sbpProperties.getOauth().getGoogle().getRedirectUri();
		if (configuredRedirectUri == null || !configuredRedirectUri.equals(redirectUri)) {
			throw new ApiException(ErrorCode.OAUTH_CODE_INVALID);
		}
		try {
			// Exchange authorization code for tokens
			GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
					HTTP_TRANSPORT,
					JSON_FACTORY,
					"https://oauth2.googleapis.com/token",
					sbpProperties.getOauth().getGoogle().getClientId(),
					sbpProperties.getOauth().getGoogle().getClientSecret(),
					code,
					redirectUri
			).execute();

			// Verify ID token
			GoogleIdToken idToken = tokenResponse.parseIdToken();
			GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
					HTTP_TRANSPORT, JSON_FACTORY)
					.setAudience(List.of(sbpProperties.getOauth().getGoogle().getClientId()))
					.build();
			if (idToken == null || !verifier.verify(idToken)) {
				log.warn("Google returned an invalid ID token");
				throw new ApiException(ErrorCode.OAUTH_CODE_INVALID);
			}
			GoogleIdToken.Payload payload = idToken.getPayload();
			if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
				throw new ApiException(ErrorCode.OAUTH_CODE_INVALID);
			}

			// Extract user information
			String email = payload.getEmail();
			String name = (String) payload.get("name");
			String picture = (String) payload.get("picture");

			log.info("Google OAuth successful for email: {}", email);

			return new GoogleUserInfo(payload.getSubject(), email, name, picture);

		} catch (TokenResponseException e) {
			log.warn("Google rejected the authorization code");
			throw new ApiException(ErrorCode.OAUTH_CODE_INVALID);
		} catch (GeneralSecurityException e) {
			log.warn("Google ID token verification failed", e);
			throw new ApiException(ErrorCode.OAUTH_CODE_INVALID);
		} catch (IOException e) {
			log.error("Failed to exchange Google authorization code", e);
			throw new ApiException(ErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
		}
	}

	/**
	 * Google user information extracted from verified ID token.
	 */
	public record GoogleUserInfo(
			String subject,
			String email,
			String name,
			String pictureUrl
	) {}
}
