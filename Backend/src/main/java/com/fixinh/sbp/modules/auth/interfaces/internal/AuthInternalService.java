package com.fixinh.sbp.modules.auth.interfaces.internal;

import java.util.List;

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
}
