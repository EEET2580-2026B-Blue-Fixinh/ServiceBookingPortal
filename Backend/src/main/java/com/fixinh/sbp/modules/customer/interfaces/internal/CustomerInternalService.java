package com.fixinh.sbp.modules.customer.interfaces.internal;

import java.util.Optional;

import com.fixinh.sbp.modules.customer.dto.internal.CustomerCreateCommand;
import com.fixinh.sbp.modules.customer.dto.internal.UpdateAddressCommand;
import com.fixinh.sbp.modules.customer.model.Address;
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
	 * Updates a customer's address (Req 3.2.U3: Google OAuth profile completion).
	 *
	 * <p>Sets the customer's address and resolves their home region. Returns the resolved
	 * region so the caller can update the credential's homeRegion accordingly.
	 *
	 * @return The home region determined by the address province
	 */
	com.fixinh.sbp.common.sharding.Region updateAddress(UpdateAddressCommand command);

	/** Restores a profile when the matching credential relocation could not be completed. */
	void restoreLocation(String customerId, Address address,
			com.fixinh.sbp.common.sharding.Region region);
}
