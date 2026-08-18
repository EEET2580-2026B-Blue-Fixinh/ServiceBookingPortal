package com.fixinh.sbp.modules.auth.service;

import java.io.IOException;

import org.springframework.stereotype.Service;

import com.fixinh.sbp.common.config.SbpProperties;
import com.fixinh.sbp.common.exception.ApiException;
import com.fixinh.sbp.common.exception.ErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
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

	private final SbpProperties sbpProperties;

	/**
	 * Exchanges the authorization code for a verified Google user profile.
	 *
	 * @param code Authorization code from Google's redirect.
	 * @return Verified payload containing email, name, and picture.
	 * @throws ApiException GOOGLE_OAUTH_FAILED if the code is invalid or verification fails.
	 */
	public GoogleUserInfo verifyCodeAndGetUser(String code) {
		try {
			// Exchange authorization code for tokens
			GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
					new NetHttpTransport(),
					GsonFactory.getDefaultInstance(),
					"https://oauth2.googleapis.com/token",
					sbpProperties.getOauth().getGoogle().getClientId(),
					sbpProperties.getOauth().getGoogle().getClientSecret(),
					code,
					sbpProperties.getOauth().getGoogle().getRedirectUri()
			).execute();

			// Verify ID token
			GoogleIdToken idToken = tokenResponse.parseIdToken();
			GoogleIdToken.Payload payload = idToken.getPayload();

			// Verify token issuer and audience
			if (!verifyIdToken(payload)) {
				log.error("Google ID token verification failed for code: {}", code);
				throw new ApiException(ErrorCode.GOOGLE_OAUTH_FAILED);
			}

			// Extract user information
			String email = payload.getEmail();
			String name = (String) payload.get("name");
			String picture = (String) payload.get("picture");

			log.info("Google OAuth successful for email: {}", email);

			return new GoogleUserInfo(email, name, picture);

		} catch (IOException e) {
			log.error("Failed to exchange Google authorization code", e);
			throw new ApiException(ErrorCode.GOOGLE_OAUTH_FAILED);
		}
	}

	/**
	 * Verifies the ID token's issuer and audience.
	 */
	private boolean verifyIdToken(Payload payload) {
		String clientId = sbpProperties.getOauth().getGoogle().getClientId();
		
		// Check issuer (should be accounts.google.com or https://accounts.google.com)
		String issuer = (String) payload.get("iss");
		boolean validIssuer = "accounts.google.com".equals(issuer) 
				|| "https://accounts.google.com".equals(issuer);
		
		// Check audience (should match our client ID)
		boolean validAudience = payload.getAudience().equals(clientId)
				|| payload.getAudienceAsList().contains(clientId);
		
		return validIssuer && validAudience;
	}

	/**
	 * Google user information extracted from verified ID token.
	 */
	public record GoogleUserInfo(
			String email,
			String name,
			String pictureUrl
	) {}
}
