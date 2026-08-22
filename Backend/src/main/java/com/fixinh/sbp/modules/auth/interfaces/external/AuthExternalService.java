package com.fixinh.sbp.modules.auth.interfaces.external;

import com.fixinh.sbp.modules.auth.dto.external.AuthTokenDto;
import com.fixinh.sbp.modules.auth.dto.external.RegisteredCustomerDto;
import com.fixinh.sbp.modules.auth.dto.internal.GoogleLoginCommand;
import com.fixinh.sbp.modules.auth.dto.internal.LoginCommand;
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
	 * Completes a Google OAuth user's profile by adding their address (Req 3.2.U3).
	 *
	 * <p>Google users start without an address. This endpoint lets them provide it so they can
	 * be assigned to a region and book services from nearby providers.
	 *
	 * @param userId The authenticated user's ID (from JWT token)
	 * @throws com.fixinh.sbp.common.exception.ApiException VALIDATION_FAILED or
	 *         PROVINCE_CODE_INVALID
	 */
}
