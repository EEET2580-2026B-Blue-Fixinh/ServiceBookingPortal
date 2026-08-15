package com.fixinh.sbp.modules.customer.dto.external;

import java.time.Instant;

import com.fixinh.sbp.modules.customer.model.AccountStatus;
import com.fixinh.sbp.modules.customer.model.Customer;
import com.fixinh.sbp.modules.customer.model.Role;

/**
 * A customer, shaped for other modules and the admin API (Req A.2.3, 3.5.S2).
 *
 * <p>Never carries {@code passwordHash}, {@code authProviders}, or {@code homeRegion} — those are
 * the customer module's own concerns, not something a caller needs.
 */
public record CustomerDto(
		String customerId,
		String email,
		String phoneNumber,
		String fullName,
		Role role,
		AccountStatus accountStatus,
		AddressDto address,
		Instant registeredAt) {

	public static CustomerDto from(Customer customer) {
		return new CustomerDto(
				customer.getId(),
				customer.getEmail(),
				customer.getPhoneNumber(),
				customer.getFullName(),
				customer.getRole(),
				customer.getAccountStatus(),
				AddressDto.from(customer.getAddress()),
				customer.getRegisteredAt());
	}
}