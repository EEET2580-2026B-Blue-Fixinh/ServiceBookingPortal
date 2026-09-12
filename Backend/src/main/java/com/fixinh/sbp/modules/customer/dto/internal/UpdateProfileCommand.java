package com.fixinh.sbp.modules.customer.dto.internal;

/**
 * PUT /customers/me (API Documentation Section 6.2). No bean-validation annotations on purpose:
 * validated by {@code CustomerProfileValidator} so every failing field comes back at once with
 * its own Appendix C code, cause and example (Req 3.1.M4) — the same reason {@code
 * RegisterCommand} skips them.
 *
 * <p>Email is deliberately absent — it is the sign-in identifier and not editable here (Section
 * 6.2 notes).
 */
public record UpdateProfileCommand(
		String fullName,
		String phoneNumber,
		AddressCommand address) {

	public record AddressCommand(
			String streetNumber,
			String streetName,
			String ward,
			String provinceCode) {
	}
}
