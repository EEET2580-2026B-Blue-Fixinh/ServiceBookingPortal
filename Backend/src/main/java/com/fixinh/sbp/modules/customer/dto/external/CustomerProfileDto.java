package com.fixinh.sbp.modules.customer.dto.external;

import java.time.Instant;
import java.util.List;

import com.fixinh.sbp.common.security.AccountStatus;
import com.fixinh.sbp.modules.customer.model.Customer;

/**
 * The signed-in customer's own profile (API Documentation Section 6.1, GET /customers/me).
 *
 * <p>{@code phoneNumber}, {@code accountStatus} and {@code authProviders} live on the credential,
 * not the profile — so building this needs both, which is why {@link #from} takes them
 * separately rather than a single {@code Customer}. Never carries {@code passwordHash}.
 *
 * <p>Deliberately carries no shard region, the same principle Section 5.1 states for
 * {@code /reference/provinces}: routing is a server concern, and publishing it would invite
 * clients to depend on the split.
 */
public record CustomerProfileDto(
		String customerId,
		String fullName,
		String email,
		String phoneNumber,
		AccountStatus accountStatus,
		Instant registeredAt,
		List<String> authProviders,
		AddressDto address) {

	public static CustomerProfileDto from(
			Customer profile, String phoneNumber, AccountStatus accountStatus,
			List<String> authProviders, String provinceName) {
		return new CustomerProfileDto(
				profile.getId(),
				profile.getFullName(),
				profile.getEmail(),
				phoneNumber,
				accountStatus,
				profile.getRegisteredAt(),
				authProviders,
				AddressDto.from(profile.getAddress(), provinceName));
	}
}
