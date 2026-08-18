package com.fixinh.sbp.common.security;

/**
 * Whether the account may sign in (API Documentation Appendix A, Req 3.5.S2).
 *
 * <p>This is the switch that gates login: a DEACTIVATED account fails sign-in with
 * 403 AUTH_ACCOUNT_DEACTIVATED even when the password is correct.
 */
public enum AccountStatus {
	ACTIVE,
	DEACTIVATED
}
