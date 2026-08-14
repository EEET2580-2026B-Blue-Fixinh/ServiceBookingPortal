package com.fixinh.sbp.modules.customer.model;

/**
 * Account roles (API Documentation Appendix A).
 *
 * <p>{@code SERVICE} — the SMP integration identity — is deliberately absent: it is not a
 * user account and is never issued by /auth/login. It belongs to the integration task.
 */
public enum Role {
	CUSTOMER,
	ADMIN
}
