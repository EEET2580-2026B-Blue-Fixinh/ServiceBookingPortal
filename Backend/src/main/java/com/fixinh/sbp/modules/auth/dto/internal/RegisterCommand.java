package com.fixinh.sbp.modules.auth.dto.internal;

/**
 * The registration request (API Documentation Section 4.1).
 *
 * <p>No bean-validation annotations on purpose: registration runs {@code RegistrationValidator}
 * so every failing field comes back at once with its own Appendix C code, cause and example
 * (Req 3.1.M4) — which generic constraint messages cannot express.
 */
public record RegisterCommand(
		String email,
		String phoneNumber,
		String password,
		String confirmPassword,
		String fullName,
		AddressCommand address) {

	public record AddressCommand(
			String streetNumber,
			String streetName,
			String ward,
			String provinceCode) {
	}
}
