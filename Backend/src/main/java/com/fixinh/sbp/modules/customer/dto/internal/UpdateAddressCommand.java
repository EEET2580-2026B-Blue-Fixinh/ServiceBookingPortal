package com.fixinh.sbp.modules.customer.dto.internal;

import com.fixinh.sbp.modules.customer.model.Address;

/**
 * Command to update a customer's address (Req 3.2.U3: Google OAuth profile completion).
 *
 * <p>Used when a Google-authenticated user completes their profile by providing an address.
 * This triggers a region assignment and potentially moves the credential and profile between
 * database shards if the province resolves to the north region.
 */
public record UpdateAddressCommand(
		String customerId,
		Address address
) {}
