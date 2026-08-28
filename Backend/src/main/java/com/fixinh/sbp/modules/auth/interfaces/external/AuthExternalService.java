package com.fixinh.sbp.modules.auth.interfaces.external;

import com.fixinh.sbp.modules.auth.dto.external.AuthTokenDto;
import com.fixinh.sbp.modules.auth.dto.external.RegisteredCustomerDto;
import com.fixinh.sbp.modules.auth.dto.internal.GoogleLoginCommand;
import com.fixinh.sbp.modules.auth.dto.internal.LoginCommand;
import com.fixinh.sbp.modules.auth.dto.internal.LogoutCommand;
import com.fixinh.sbp.modules.auth.dto.internal.RefreshCommand;
import com.fixinh.sbp.modules.auth.dto.internal.RegisterCommand;

/** What the auth controller may ask of the auth module (API Documentation Sections 4.1, 4.2). */
public interface AuthExternalService {

	/**
	 * Creates a customer account. Returns no token — registration ends at the login screen.
	 *
	 * @throws com.fixinh.sbp.common.exception.ApiException VALIDATION_FAILED,
	 *         EMAIL_ALREADY_REGISTERED or PHONE_ALREADY_REGISTERED
	 */
	RegisteredCustomerDto register(RegisterCommand command);

	/**
	 * Authenticates by email or phone number plus password, and issues a JWT (Reqs 3.2.S1,
	 * 3.2.M1).
	 *
	 * @throws com.fixinh.sbp.common.exception.ApiException AUTH_INVALID_CREDENTIALS or
	 *         AUTH_ACCOUNT_DEACTIVATED
	 */
	AuthTokenDto login(LoginCommand command);

	/**
	 * Authenticates via Google OAuth 2.0 (Req 3.2.U1-U3). Exchanges the authorization code for
	 * a verified Google user profile, then either finds the existing account or creates a new one.
	 *
	 * @throws com.fixinh.sbp.common.exception.ApiException OAUTH_CODE_INVALID,
	 *         OAUTH_PROVIDER_UNAVAILABLE or
	 *         AUTH_ACCOUNT_DEACTIVATED
	 */
	AuthTokenDto loginWithGoogle(GoogleLoginCommand command);

	/**
	 * Exchanges a refresh token for a new access token and a new refresh token (rotation).
	 *
	 * @throws com.fixinh.sbp.common.exception.ApiException AUTH_REFRESH_TOKEN_INVALID,
	 *         AUTH_REFRESH_TOKEN_EXPIRED, or AUTH_ACCOUNT_DEACTIVATED
	 */
	AuthTokenDto refresh(RefreshCommand command);

	/** Revokes the access token that authenticated this request and clears the account's
	 * refresh token. Idempotent: calling it twice is not an error. */
	void logout(LogoutCommand command);
}
