package com.fixinh.sbp.modules.admin.interfaces.internal;

import java.util.Optional;

import com.fixinh.sbp.modules.admin.model.Administrator;

/**
 * What other modules may ask of the admin module.
 *
 * <p>Only the profile lookup is here today — the auth module needs it after a credential whose
 * discriminator says ADMIN has authenticated. Staff management and the admin-facing customer
 * endpoints will add methods when those tasks land.
 */
public interface AdminInternalService {

	/** The profile behind a token's {@code sub}, or empty if this id is not an administrator's. */
	Optional<Administrator> findById(String id);
}
