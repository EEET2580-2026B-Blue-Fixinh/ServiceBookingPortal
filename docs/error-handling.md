# Error handling and token expiry

How a failed request travels from the backend to the user, and what happens when an
access token runs out. Covers Req 3.2.M1–M3 (JWT issuance and validation) and the
error contract in API Documentation Section 2.7.

## The error contract

Every failure returns the same JSON body, whatever produced it (Section 2.7):

```json
{
  "timestamp": "2026-08-17T09:15:42Z",
  "path": "/api/v1/customers/me",
  "status": 401,
  "error": "AUTH_TOKEN_EXPIRED",
  "message": "The access token has expired. Please sign in again.",
  "traceId": "3f2a91c4-8d5e-4b17-9c0a-11e6b2d7a840"
}
```

`fieldErrors` is present only on a 400 `VALIDATION_FAILED`, carrying one entry per
rejected field with its own Appendix C `code`. It is omitted entirely otherwise.

**Branch on `error`, never on `message`.** The message is written for people and will
be reworded; the code is the contract. The frontend surfaces it as `ApiError.code`
(`Frontend/src/services/api/apiClient.js`), so client code compares codes and shows
`message` only when it has nothing better of its own to say.

**Never branch on the status alone either.** Four different situations share 401, and
one of them is an ordinary wrong password. See the table below.

## Auth error codes

Defined in `Backend/src/main/java/com/fixinh/sbp/common/exception/ErrorCode.java`.

| Code | Status | Means | Client response |
|---|---|---|---|
| `AUTH_INVALID_CREDENTIALS` | 401 | Wrong identifier or password at sign-in | Show the message on the login form. **Not** a session ending — there was no session |
| `AUTH_TOKEN_MISSING` | 401 | No `Authorization` header arrived | Nothing to sign out of; storage was already empty |
| `AUTH_TOKEN_INVALID` | 401 | Malformed, wrongly signed, or wrong issuer | End the session |
| `AUTH_TOKEN_EXPIRED` | 401 | Genuinely issued, now past its `exp` | End the session |
| `AUTH_ACCOUNT_DEACTIVATED` | **403** | Token is valid; the account is switched off | **Not** a session ending — the token still works. Whoever wires the first authenticated page decides how to present it |

The two that end a session are listed in `SESSION_ENDING_ERROR_CODES`
(`Frontend/src/config/constants.js`). `AUTH_TOKEN_INVALID` is grouped with expiry
because from the user's side the two are indistinguishable: the client believed it
held a session, the server disagrees, and neither is recoverable without signing in
again. A second message for a malformed token would be the first one reworded.

## Which component answers

Two different paths produce a 401, and they are easy to confuse when debugging:

- **A token was sent but is broken or expired.** `JwtAuthenticationFilter` catches the
  `ApiException` from `JwtTokenProvider.parseAndValidate`, clears the security
  context, and hands it to `handlerExceptionResolver` → `GlobalExceptionHandler`. The
  body carries the real code (`AUTH_TOKEN_EXPIRED` or `AUTH_TOKEN_INVALID`). The
  filter runs before the `DispatcherServlet`, which is why it needs the resolver
  explicitly — a plain `@RestControllerAdvice` would never see the exception.
- **No token was sent.** The filter passes the request through, Spring Security's
  `.anyRequest().authenticated()` denies it, and `JsonAuthenticationEntryPoint`
  answers with a body hardcoded to `AUTH_TOKEN_MISSING`.

Either way the client gets parseable JSON with a code — never a bare 401, never HTML.
Neither path sets a `WWW-Authenticate` header.

## Token expiry, end to end

There is **no refresh token**, and none is planned — the spec never asked for one. The
access token's lifetime is the whole of the session, so expiry means signing in again.

1. `POST /auth/login` returns `expiresIn`, the lifetime in **seconds** (3600 by
   default). It comes from `JwtTokenProvider.getAccessTokenTtlSeconds()`.
2. `AuthContext.login()` converts it to a wall-clock deadline,
   `Date.now() + expiresIn * 1000`, and stores it under `sb_auth_expires_at`.
3. A `setTimeout` for the remaining lifetime signs the user out **the moment the
   deadline passes**, with no API call involved. This is what makes expiry immediate
   rather than discovered on the next request.
4. The forced sign-out clears all three `sb_auth_*` keys and flags the session as
   expired, so `ProtectedRoute` redirects to `/login` and the page explains why via
   `SESSION_EXPIRED_MESSAGE`.
5. Because `ProtectedRoute` already passes `from`, signing back in returns the user to
   the page they were reading.

### Why the deadline, not the token's own `exp`

The deadline is derived from the *duration* the server reported, measured against the
browser's own clock. A constant offset between browser and server therefore cancels
out. Decoding the JWT's `exp` claim (server time) and comparing it to `Date.now()`
(browser time) would sign a user whose clock is ten minutes slow out ten minutes
early, every single session.

### The three things that enforce it

The timer makes expiry instant, but it is not trusted alone — `setTimeout` is
suspended while a machine sleeps and throttled in a tab hidden for more than a few
minutes, so it can run late:

- **On mount**, `readStoredSession` checks the stored deadline in the `useState`
  initialiser, before the first render. A tab opened after the token died never paints
  a signed-in frame.
- **On return to a hidden tab**, a `visibilitychange` listener re-checks the deadline
  and fires ahead of the throttled timer.
- **On any authenticated request**, a 401 carrying a session-ending code calls the
  handler registered through `setUnauthorizedHandler`. This is the backstop for cases
  the client's own clock cannot know about.

`apiClient` is plain functions and cannot use hooks, so it cannot end a session
itself. `AuthProvider` registers its sign-out callback into a single module-scope slot
— one owner, matching the way one place already owns the base URL and the error shape.
An event bus would invite a second listener that also navigates.

Repeat calls are harmless: the provider stores the same frozen object every time, so
`setState` bails out and a burst of concurrent 401s produces exactly one redirect.

### Sessions with no stored deadline

`sb_auth_expires_at` missing (or non-numeric) means **expiry unknown**, not expired.
No timer is armed and the session behaves as it did before this feature existed, with
the 401 backstop as the only enforcement. This is what stops everyone who was signed
in when this shipped from being kicked out on deploy, and it needs no migration code.

### Why login and register must keep `{ auth: false }`

`JwtAuthenticationFilter` does not override `shouldNotFilter` and is registered for
the whole chain, so **any** request carrying an expired `Authorization` header is
rejected 401 — including endpoints that are otherwise public. `authEndpoints.js` sends
both `/auth/login` and `/auth/register` with `{ auth: false }`, which:

- stops a dead token poisoning the one request that would fix the situation, and
- guarantees a 401 from `/auth/login` is always `AUTH_INVALID_CREDENTIALS` and never a
  token code, which is exactly why gating on the code needs no companion check.

Any new **public** endpoint added under `endpoints/` should think hard before omitting
that option: a signed-in user holding a stale token will 401 on it.

## Testing expiry without waiting an hour

The TTL is env-overridable, so no tracked file needs editing:

```powershell
# Backend — 1 minute instead of 60. The property is in minutes, so 1 is the shortest
# useful value; 0 makes tokens dead on arrival.
$env:JWT_ACCESS_TOKEN_TTL_MINUTES = "1"; .\mvnw.cmd spring-boot:run

# Frontend — must stay on port 3000, the only origin the backend's CORS policy admits
npm run dev
```

Keep DevTools open on **Application → Local Storage** and **Network**.

| Case | Steps | Expected |
|---|---|---|
| The timer fires | Sign in, land on `/booking`, idle ~60s | Flips to `/login` on its own with the expiry notice. **Zero requests in the Network tab at the moment of redirect** — that is what proves it was the timer, not a 401 |
| Storage | Inspect after sign-in, then after expiry | `sb_auth_expires_at` ≈ login time + 60000; afterwards all three `sb_auth_*` keys are gone |
| No flash on reload | `localStorage.setItem('sb_auth_expires_at', String(Date.now() - 1000))`, reload | `/login` with the notice and no glimpse of the booking page |
| Unknown deadline | `localStorage.removeItem('sb_auth_expires_at')`, reload, wait >60s | Still signed in, no timer — the legacy path |
| The 401 backstop | From the previous case, in the console:<br>`const { apiClient } = await import('/src/services/api/apiClient.js')`<br>`await apiClient.get('/reference/provinces')` | 401 `AUTH_TOKEN_EXPIRED` → immediate sign-out. Needs the console because `bookingEndpoints.js` is still empty, so no page makes an authenticated call yet |
| Wrong password is not expiry | Submit a valid email with a wrong password | The normal "…password is incorrect." No redirect, no loop, storage untouched |
| Ordinary visit | Clear storage, go straight to `/booking` | `/login` with **no** notice |
| Manual sign-out | Sign in, click Sign out, wait >60s | `/login` with **no** notice, and nothing fires later — the timer was cleared |
| Backend contract | `curl -i http://localhost:8443/api/v1/reference/provinces -H "Authorization: Bearer <expired>"` | 401 with `"error":"AUTH_TOKEN_EXPIRED"` |

Restart without the env var afterwards to restore the 60-minute TTL.

## Not yet wired

Deliberate follow-ups, not oversights:

- **No refresh token.** Expiry means signing in again, by design.
- **No cross-tab sync of *manual* sign-out.** Expiry already propagates everywhere for
  free — the deadline lives in `localStorage`, so every tab arms its own timer. But
  signing out in one tab leaves another tab's shell rendered until its next render or
  request. Closing that needs a `storage` listener and a cross-tab test matrix.
- **A clock wound *backwards* mid-session** keeps a client-side session alive past the
  server's deadline. The next authenticated call ends it through the 401 backstop.
- **`RATE_LIMITED`** exists in `ErrorCode` so the catalogue matches Appendix B, but no
  limiter is wired; login is not rate limited.
- **`AUTH_ACCOUNT_DEACTIVATED` (403) has no client handling.** The `ApiError` reaches
  the calling page unchanged. Whoever wires the first authenticated page decides how
  it should look.
