package com.fixinh.sbp.modules.auth.model;

/**
 * How a user can sign in (Appendix A: Multi-Provider Login).
 *
 * <p>A {@link UserCredential} tracks a set of these: PASSWORD, GOOGLE, or both. PASSWORD means
 * the credential has a {@code passwordHash} and can sign in through /auth/login. GOOGLE means
 * the account was created via Google OAuth or linked later, and can sign in through
 * /auth/oauth/google. A credential with both can use either flow.
 */
public enum AuthProvider {
	/** Password-based authentication (email/phone + password). */
	PASSWORD,
	
	/** Google OAuth 2.0 authentication. */
	GOOGLE
}
