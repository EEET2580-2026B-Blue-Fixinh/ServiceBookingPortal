export const AUTH_TOKEN_KEY = "sb_auth_token";
export const AUTH_USER_KEY = "sb_auth_user";

// When the access token dies, as a millisecond epoch. The session itself outlives
// this — see AUTH_REFRESH_TOKEN_KEY — but it is the deadline the access token's own
// timer enforces.
export const AUTH_EXPIRES_AT_KEY = "sb_auth_expires_at";

// Opaque, single-use-until-rotated. Exchanged at /auth/refresh for a new access
// token without asking the user to sign in again.
export const AUTH_REFRESH_TOKEN_KEY = "sb_auth_refresh_token";

// Roles the backend issues in the JWT (API Documentation Appendix A).
export const ROLES = {
  CUSTOMER: "CUSTOMER",
  ADMIN: "ADMIN",
};

// Every path the router serves. Named so a rename is one edit rather than a grep,
// and so "where unauthenticated users go" is a concept rather than a repeated string.
export const ROUTES = {
  ROOT: "/",
  LOGIN: "/login",
  REGISTER: "/register",
  BOOKING: "/booking",
  NEW_BOOKING: "/booking/new",
  ADMIN: "/admin",
  PROFILE_COMPLETION: "/profile/complete", // Address-based shard placement (Req 3.1.U1-U2)
};

/**
 * The backend codes (Appendix B) that mean "this token will never work again", so
 * the only answer is to sign in afresh.
 *
 * Gated on the code, never the status — `AUTH_INVALID_CREDENTIALS` is also a 401,
 * so branching on the status alone would read a mistyped password on the login
 * form as an expired session.
 *
 * Deliberately absent:
 * - `AUTH_TOKEN_MISSING` (401) — no token was sent, so there is no session to end.
 * - `AUTH_INVALID_CREDENTIALS` (401) — a wrong password, shown in place on the form.
 * - `AUTH_ACCOUNT_DEACTIVATED` (403) — the token is valid; the account is switched
 *   off. A different conversation with the user, and not one this owns.
 */
export const SESSION_ENDING_ERROR_CODES = [
  "AUTH_TOKEN_EXPIRED",
  "AUTH_TOKEN_INVALID",
  "AUTH_TOKEN_REVOKED",
];

// Shown on the login page after a forced sign-out. Client-side because the timer
// path has no server response to quote; one constant so both paths always agree.
export const SESSION_EXPIRED_MESSAGE =
  "Your session has expired. Please sign in again.";
