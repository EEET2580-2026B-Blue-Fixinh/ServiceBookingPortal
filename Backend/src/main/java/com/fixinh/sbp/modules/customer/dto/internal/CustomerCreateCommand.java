package com.fixinh.sbp.modules.customer.dto.internal;

import com.fixinh.sbp.modules.customer.model.Address;

/**
 * What the auth module hands the customer module to create a profile (API Documentation
 * Section 4.1).
 *
 * <p>No password: hashing and uniqueness belong to the credential, which auth writes first. The
 * {@code id} comes from that credential, so the profile and the credential share it.
 *
 * <p>{@code email} is carried only to be denormalised onto the profile as a search copy — the
 * credential remains the authoritative one.
 */
public record CustomerCreateCommand(
		String id,
		String email,
		String fullName,
		Address address) {
}
