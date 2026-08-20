package com.fixinh.sbp.modules.customer.dto.external;

import java.time.Instant;

import com.fixinh.sbp.common.security.AccountStatus;
import com.fixinh.sbp.common.security.Role;
import com.fixinh.sbp.modules.customer.model.Customer;

/**
 * A customer, shaped for other modules and the admin API (Req A.2.3, 3.5.S2).
 *
 * <p>{@code phoneNumber} and {@code accountStatus} live on the credential now, not the profile —
 * so building this needs both, which is why {@link #from} takes them separately rather than a
 * single {@code Customer}. Never carries {@code passwordHash}.
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

	public static CustomerDto from(Customer profile, String phoneNumber, AccountStatus accountStatus) {
		return new CustomerDto(
				profile.getId(),
				profile.getEmail(),
				phoneNumber,
				profile.getFullName(),
				profile.getRole(),
				accountStatus,
				AddressDto.from(profile.getAddress()),
				profile.getRegisteredAt());
	}
}
