package com.fixinh.sbp.modules.customer.interfaces.internal;

import java.util.Optional;

import com.fixinh.sbp.modules.customer.dto.internal.CustomerCreateCommand;
import com.fixinh.sbp.modules.customer.model.Customer;

/**
 * What other modules may ask of the customer module. The auth module uses this to create
 * accounts and to resolve a sign-in identifier.
 */
public interface CustomerInternalService {

	/**
	 * Hashes the password, files the customer in the shard its province belongs to, and mirrors
	 * the sign-in fields to the other shard.
	 *
	 * @throws com.fixinh.sbp.common.exception.ApiException EMAIL_ALREADY_REGISTERED or
	 *         PHONE_ALREADY_REGISTERED if either is taken (Req 3.1.S2)
	 */
	Customer create(CustomerCreateCommand command);

	/** Resolves an email <em>or</em> phone number to an account — the /auth/login lookup. */
	Optional<Customer> findByIdentifier(String identifier);

	boolean emailExists(String email);

	boolean phoneExists(String phoneNumber);
}
