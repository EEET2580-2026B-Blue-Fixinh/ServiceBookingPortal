/**
 * Every API path the frontend calls, in one place.
 *
 * Each value is appended to API_BASE_URL, which already carries the host and the
 * /api/v1 prefix — so '/auth/login' below is requested as
 * http://localhost:8443/api/v1/auth/login. The value is the complete path from
 * that base and nothing further is joined onto it, so one line here tells you
 * the whole URL without going to look for where a prefix gets added.
 *
 * Adding an endpoint: put the path here, then call it from the matching module
 * in services/api/endpoints/. Only the path lives in this file — the HTTP
 * method, whether the request carries the Authorization header, and the shape
 * of the body all belong to the endpoint function, because those are the things
 * a caller has to get right and a bare string cannot express.
 *
 * Keep this to paths the backend actually serves. One entry that 404s and the
 * table stops being something anyone trusts to read instead of grepping.
 */

export const API_ENDPOINTS = {
  // Both are reachable without a token — they have to be, since this is where a
  // user goes to get one.
  AUTH: {
    REGISTER: '/auth/register',
    LOGIN: '/auth/login',
    GOOGLE_LOGIN: '/auth/oauth/google',  // Section 4.3: Google OAuth sign-in
    COMPLETE_PROFILE: '/auth/profile/complete',  // Req 3.2.U3: Profile completion
  },

  // Dropdown data, cacheable for the length of a session. Also public: the
  // registration form needs the province list before an account exists.
  REFERENCE: {
    PROVINCES: '/reference/provinces',
  },
}
