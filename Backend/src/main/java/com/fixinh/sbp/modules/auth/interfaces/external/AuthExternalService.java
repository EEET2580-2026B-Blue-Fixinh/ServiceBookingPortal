package com.fixinh.sbp.modules.auth.interfaces.external;

import com.fixinh.sbp.modules.auth.dto.external.AuthTokenDto;
import com.fixinh.sbp.modules.auth.dto.external.RegisteredCustomerDto;
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
}
