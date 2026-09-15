package com.fixinh.sbp.modules.customer.interfaces.internal;

import java.util.Optional;

import com.fixinh.sbp.modules.customer.dto.external.CustomerProfileDto;
import com.fixinh.sbp.modules.customer.dto.internal.CustomerCreateCommand;
import com.fixinh.sbp.modules.customer.dto.internal.CompleteAddressCommand;
import com.fixinh.sbp.modules.customer.dto.internal.UpdateProfileCommand;
import com.fixinh.sbp.modules.customer.model.Customer;

/**
 * What other modules may ask of the customer module.
 *
 * <p>The auth module uses this to create a profile once it has written the credential, and to
 * load a profile for an already-authenticated caller.
 */
public interface CustomerInternalService {

	/**
	 * Files the profile in the shard its address province belongs to, under the id of the
	 * credential that identifies it.
	 *
	 * <p>Runs no uniqueness check: the credential holds the unique email and phone, and it is
	 * written first, so a duplicate has already been rejected before this is reached.
	 */
	Customer createProfile(CustomerCreateCommand command);

	/** The profile behind a token's {@code sub}, or empty if this id is not a customer's. */
	Optional<Customer> findById(String id);

	/**
	 * Completes the address needed to place a customer in a regional shard (Req 3.1.U1-U2).
	 *
	 * <p>Sets the customer's address and resolves their home region. Returns the resolved
	 * region so the caller can update the credential's homeRegion accordingly.
	 *
	 * @return The home region determined by the address province
	 */
	void completeAddress(String customerId, CompleteAddressCommand command);

	/**
	 * The signed-in caller's own profile (GET /customers/me), combining this module's profile
	 * with the credential's phone number and sign-in providers.
	 *
	 * @throws com.fixinh.sbp.common.exception.ApiException CUSTOMER_NOT_FOUND if no profile or
	 *         no credential has this id
	 */
	CustomerProfileDto getMyProfile(String customerId);

	/**
	 * Updates the signed-in caller's own profile (PUT /customers/me, API Documentation Section
	 * 6.2): full name, phone number and address, moving the record to a new regional shard if
	 * the province changes (Req 3.1.U2). Email and password are not touched here.
	 *
	 * @throws com.fixinh.sbp.common.exception.ApiException CUSTOMER_NOT_FOUND if no profile has
	 *         this id, VALIDATION_FAILED if a field breaks an Appendix C rule,
	 *         PHONE_ALREADY_REGISTERED if another account already holds the phone number
	 */
	CustomerProfileDto updateMyProfile(String customerId, UpdateProfileCommand command);
}
