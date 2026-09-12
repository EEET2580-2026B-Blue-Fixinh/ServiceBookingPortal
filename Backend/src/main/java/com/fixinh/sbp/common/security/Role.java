package com.fixinh.sbp.common.security;

/**
 * Account roles (API Documentation Appendix A).
 *
 * <p>{@code SERVICE} — the SMP integration identity — is deliberately absent: it is not a
 * user account, is never issued by /auth/login, and never appears in a JWT this application
 * signs. It exists only as the authority {@code IntegrationAuthenticationFilter} grants once
 * the shared secret has matched, which is also why a token whose role claim says SERVICE
 * still fails {@code Role.valueOf} and is refused as a token SBP did not issue.
 */
public enum Role {
	CUSTOMER,
	ADMIN
}