package com.fixinh.sbp.modules.auth.interfaces.internal;

/**
 * What other modules may ask of the auth module.
 *
 * <p>Intentionally empty for now. Register and sign-in are driven entirely through
 * {@code AuthExternalService}, and no other module needs anything from auth yet — the caller's
 * identity travels on the SecurityContext, not through a service call. Logout, /auth/me and the
 * Google OAuth exchange will add methods here when those tasks land; inventing them now would
 * mean guessing at signatures nobody calls.
 */
public interface AuthInternalService {
}
