import { apiClient } from "../apiClient";
import { API_ENDPOINTS } from "../../../config/api";

// API Documentation Section 4. Both endpoints are public — a user must reach
// registration and sign-in before holding a token — so they opt out of the
// Authorization header rather than sending a stale one.

/**
 * POST /auth/register (Section 4.1). Returns the created customer; deliberately
 * no token, because registration ends at the login screen.
 *
 * @param {{email: string, phoneNumber: string, password: string,
 *          confirmPassword: string, fullName: string,
 *          address: {streetNumber: string, streetName: string,
 *                    ward: string, provinceCode: string}}} payload
 */
export function register(payload) {
  return apiClient.post(API_ENDPOINTS.AUTH.REGISTER, payload, { auth: false });
}

/**
 * POST /auth/login (Section 4.2). Returns `{ accessToken, tokenType, expiresIn, user }`.
 *
 * @param {{identifier: string, password: string}} credentials  identifier is an
 *        email address or a phone number; the server works out which.
 */
export function login(credentials) {
  return apiClient.post(API_ENDPOINTS.AUTH.LOGIN, credentials, { auth: false });
}

/**
 * POST /auth/oauth/google (Section 4.3). Exchanges Google authorization code
 * for a JWT. Returns `{ accessToken, tokenType, expiresIn, user, accountCreated,
 * profileCompletionRequired }`.
 *
 * @param {{code: string}} payload  Authorization code from Google's OAuth redirect.
 */
export function googleLogin(payload) {
  return apiClient.post(API_ENDPOINTS.AUTH.GOOGLE_LOGIN, payload, {
    auth: false,
  });
}

/**
 * POST /auth/refresh. No stored access token is sent — it has likely already
 * expired, which is why this is being called — only the refresh token, in the
 * body. Returns a new `{ accessToken, expiresIn, refreshToken, ... }` pair; the
 * refresh token rotates on every call, so the old one stops working immediately.
 *
 * @param {string} refreshToken
 */
export function refresh(refreshToken) {
  return apiClient.post(
    API_ENDPOINTS.AUTH.REFRESH,
    { refreshToken },
    { auth: false },
  );
}

/**
 * POST /auth/logout. Requires the current access token — that token is the one
 * thing being revoked, so there is no other way to tell the server which one to
 * blacklist.
 */
export function logout() {
  return apiClient.post(API_ENDPOINTS.AUTH.LOGOUT, undefined);
}
