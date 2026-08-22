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
    REGISTER: "/auth/register",
    LOGIN: "/auth/login",
    GOOGLE_LOGIN: "/auth/oauth/google", // Section 4.3: Google OAuth sign-in
  },

  CUSTOMERS: {
    MY_ADDRESS: "/customers/me/address",
  },

  // Dropdown data, cacheable for the length of a session. Also public: the
  // registration form needs the province list before an account exists.
  REFERENCE: {
    PROVINCES: "/reference/provinces",
  },
  // The signed-in caller's own notifications (API Documentation Section 9). Every
  // one of these acts on whoever the token names; none of them takes a recipient.
  //
  // READ is a function rather than a string because its path has an identifier in
  // the middle of it. That is still only the path — the rule this file states is
  // that the method and the body live with the caller, not that the value has to
  // be a literal. Building '/read' onto a bare '/notifications' inside the
  // endpoint module would be the actual break, because then no single line here
  // would tell you the whole URL. Section 2.6 calls identifiers opaque, so the
  // segment is encoded rather than trusted to be URL-safe.
  NOTIFICATIONS: {
    LIST: "/notifications",
    READ: (notificationId) =>
      `/notifications/${encodeURIComponent(notificationId)}/read`,
    READ_ALL: "/notifications/read-all",
  },
};
