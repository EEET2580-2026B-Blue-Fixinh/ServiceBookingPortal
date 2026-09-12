package com.fixinh.sbp.modules.auth.interfaces.internal;

import java.util.List;
import java.util.Optional;

import com.fixinh.sbp.common.security.AccountStatus;
import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.modules.auth.model.UserCredential;

/**
 * What other modules may ask of the auth module.
 *
 * <p>The admin module uses this to list and gate accounts by role (Req 3.5.S2) — credentials
 * are the only place {@code accountStatus} lives now that it moved out of the profile
 * collections.
 */
public interface AuthInternalService {

	/** Every credential system-wide with this discriminator. */
	List<UserCredential> listByRole(Role role);

	/**
	 * Flips whether the account may sign in.
	 *
	 * @throws com.fixinh.sbp.common.exception.ApiException CUSTOMER_NOT_FOUND if no credential
	 *         has this id
	 */
	UserCredential setAccountStatus(String id, AccountStatus status);

	/**
	 * The credential behind a token's {@code sub} — phone number, sign-in providers and
	 * account status all live here rather than on the profile. Empty if this id names no
	 * credential.
	 */
	Optional<UserCredential> findById(String id);

	/**
	 * Changes the phone number on an existing credential, enforcing the same system-wide
	 * uniqueness registration does (API Documentation Section 6.2).
	 *
	 * @throws com.fixinh.sbp.common.exception.ApiException CUSTOMER_NOT_FOUND if no credential
	 *         has this id, PHONE_ALREADY_REGISTERED if another credential already holds it
	 */
	UserCredential updatePhoneNumber(String id, String phoneNumber);
}
