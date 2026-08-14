package com.fixinh.sbp.modules.customer.dto.internal;

import com.fixinh.sbp.modules.customer.model.Address;

/**
 * Everything the customer module needs to create an account. Passed service-to-service, never
 * bound from HTTP.
 *
 * <p>The password arrives raw and is hashed inside the customer module, so the credential store
 * is the only place that ever handles it.
 */
public record CustomerCreateCommand(
		String email,
		String phoneNumber,
		String rawPassword,
		String fullName,
		Address address) {
}
