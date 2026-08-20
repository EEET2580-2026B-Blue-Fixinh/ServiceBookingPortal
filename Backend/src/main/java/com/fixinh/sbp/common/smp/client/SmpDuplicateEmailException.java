package com.fixinh.sbp.common.smp.client;

/**
 * SMP already has a staff account with this email (API Documentation Section 10.6, 409
 * EMAIL_ALREADY_REGISTERED). Left for the calling module to translate into its own error
 * vocabulary — {@code common.smp} does not depend on any module's {@code ApiException}.
 */
public class SmpDuplicateEmailException extends RuntimeException {

	public SmpDuplicateEmailException(String email) {
		super("SMP already has a staff account with email " + email);
	}
}
