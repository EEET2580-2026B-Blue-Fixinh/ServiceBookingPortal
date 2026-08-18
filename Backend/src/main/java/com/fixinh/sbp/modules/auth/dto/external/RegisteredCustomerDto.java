package com.fixinh.sbp.modules.auth.dto.external;

import java.time.Instant;

import com.fixinh.sbp.common.security.AccountStatus;
import com.fixinh.sbp.common.security.Role;

/**
 * What a successful registration returns (API Documentation Section 4.1).
 *
 * <p>No token: registration ends at the login screen, which keeps one path for issuing tokens.
 * The password hash and the shard region are never exposed (Req A.2.3).
 */
public record RegisteredCustomerDto(
		String customerId,
		String email,
		String fullName,
		Role role,
		AccountStatus accountStatus,
		Instant registeredAt) {
}
