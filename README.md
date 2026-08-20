# Fixinh — Service Booking Portal (SBP)

Customer-facing half of the Fixinh repair platform: customers register, sign in, and manage
their repair bookings. The Service Management Portal (SMP) owns bookings, technicians and
staff; SBP consumes them over REST and is currently mocked so it runs standalone.

**Stack:** Spring Boot 4.1 / Java 17 · MongoDB (two regional shards) · React + Vite +
Tailwind.

---

## Where the project stands

| Slice | Backend | Frontend | Notes |
|---|---|---|---|
| API error contract | ✅ | ✅ | Codes in `ErrorCode`, surfaced as `ApiError.code` |
| Regional sharding | ✅ | — | North/south stores, routed by province code |
| Registration | ✅ | ✅ | Field-level validation on both sides |
| Login + JWT | ✅ | ✅ | Bearer token, no refresh token |
| Session expiry | ✅ | ✅ | Timer, mount check, visibility check, 401 backstop |
| Route guards | — | ✅ | `ProtectedRoute` + `AdminRoute` (role from the JWT) |
| Reference data | ✅ | ✅ | `GET /reference/provinces` from an in-code catalogue |
| My Bookings page | WIP | WIP | UI complete, still rendering `mockBookings.js` |
| Customer profile | WIP | WIP | `GET`/`PUT /customers/me` not written |
| Admin | WIP | WIP | Route guard only; no admin pages, no endpoints |
| Ratings | WIP | WIP | Package scaffolding only |
| Notifications / WebSocket | WIP | WIP | Config bean exists; no broadcaster logic |
| SMP integration | WIP | — | Client interfaces exist; mock and live are both empty |
| Cloudinary image relay | WIP | WIP | Config bound, service not implemented |
| Google OAuth | WIP | WIP | Properties bound only |

WIP files are empty class/interface stubs, deliberately committed so each teammate's branch has
a place to land without conflicting on package creation.

### Endpoints live today

| Method | Path | Auth |
|---|---|---|
| `POST` | `/api/v1/auth/register` | public |
| `POST` | `/api/v1/auth/login` | public |
| `GET` | `/api/v1/reference/provinces` | public |
| `GET` | `/actuator/health`, `/actuator/info` | public |

Everything else is closed by default — `.anyRequest().authenticated()`, so an endpoint added
later is protected unless it is deliberately opened in
[SecurityConfig.java](Backend/src/main/java/com/fixinh/sbp/common/config/SecurityConfig.java).

---

## Running it

### Backend

```powershell
cd Backend
.\mvnw.cmd spring-boot:run     # Windows
./mvnw spring-boot:run         # macOS / Linux
```

Serves `http://localhost:8443` on the `dev` profile (SSL off, SMP mocked, debug logging).

Both shards are hosted MongoDB Atlas clusters shared by the team, reached through
`MONGO_URI_NORTH` and `MONGO_URI_SOUTH`. There is nothing to install and no database to run
locally — set those two env vars and the app connects.

### Frontend

```powershell
cd Frontend
cp .env.example .env
pnpm install
pnpm dev
```

Must stay on **port 3000** — it is the only origin the backend's CORS policy admits.

---

## Configuration

App config is namespaced under `fixinh.sbp` in
[application.yml](Backend/src/main/resources/application.yml) and bound to typed
`@ConfigurationProperties`. Every value falls back to an env var with a dev-only default, so
no secret is committed.

| Env var | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | dev placeholder | Signing key, 32 bytes minimum |
| `JWT_ISSUER` | `fixinh-sbp` | Rejected if it does not match on validation |
| `JWT_ACCESS_TOKEN_TTL_MINUTES` | `1` | Also what `/auth/login` reports as `expiresIn` |
| `MONGO_URI_NORTH` / `MONGO_URI_SOUTH` | localhost | The two regional Atlas clusters |
| `SMP_MODE` | `mock` | `mock` = local fixtures, `live` = real SMP |
| `CORS_ALLOWED_ORIGINS` | localhost:3000 | No wildcards |
| `EMAIL_MODE` | `file` | `file` = write to disk, send nothing; `smtp` = send for real. Inert on `dev`, which pins the mode — see below |
| `EMAIL_FROM_ADDRESS` / `EMAIL_FROM_NAME` | `no-reply@fixinh.vn` / `Fixinh` | Envelope sender of the staff welcome email |
| `EMAIL_OUTBOX_DIR` | `target/mail` | Where `file` mode writes. Git-ignored; `mvn clean` clears it |
| `EMAIL_STAFF_SIGN_IN_URL` | placeholder | SMP's sign-in page — staff cannot sign in to SBP |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | none | `integration` profile only. No defaults: a missing one fails startup by name |
| `CLOUDINARY_*`, `GOOGLE_*` | empty | Not yet used |

**Email.** `dev` is pinned to `file` mode, so no message can leave the machine during
development (Req 3.1.S5's acceptance criterion).

Because `application-dev.yml` sets the mode literally, `EMAIL_MODE` cannot lift the pin — a
profile file outranks the placeholder in `application.yml`. An OS environment variable named
for the property itself does outrank both, so that is the escape hatch for the one real send
needed to verify a provider end to end:

```powershell
$env:SPRING_MAIL_HOST="sandbox.smtp.mailtrap.io"; $env:SPRING_MAIL_PORT="2525"
$env:SPRING_MAIL_USERNAME="..."; $env:SPRING_MAIL_PASSWORD="..."
$env:FIXINH_SBP_EMAIL_MODE="smtp"; .\mvnw.cmd spring-boot:run
```

Use a sandbox provider that captures mail rather than delivering it. No credential belongs in
`application.yml`, and none is committed.

Frontend config lives in [env.js](Frontend/src/config/env.js), read from `.env` — API base URL
and the login page's demo-account shortcut (`VITE_SHOW_DEMO_ACCOUNT`).

Profiles: `dev` (single PC, SSL off, SMP mocked) and `integration` (multi-VM, SSL on, live SMP).

---

## How it is put together

### Backend

```
common/     config, error contract, JWT security, sharding, SMP clients, storage
modules/    admin, auth, booking, customer, integration, notification, rating, reference
```

Each module follows the same shape — `controller/` → `interfaces/external` → `service/` →
`repository/`, with `dto/external` for wire types and `dto/internal` for commands. External
interfaces are what a controller calls; internal ones are what another module may call.

**Sharding.** Customer data is partitioned north/south by the province in the address, with
Hue and south going to the south store and Quang Tri and north to the north store. Each account
lives in exactly one shard — nothing is duplicated. The client never supplies a region; it sends
a province code and `RegionResolver` maps it, using the same `Province` enum that
`/reference/provinces` publishes, so a province can never be offered in the dropdown but be
unroutable. The cost is paid at sign-in: an email or phone number carries no region, so
`ShardedCustomerRepository` checks south then north and stops at the first hit.

**Errors.** Every failure returns the same JSON — `timestamp`, `path`, `status`, `error`,
`message`, `traceId`, plus `fieldErrors` on a 400 `VALIDATION_FAILED`. Clients branch on
`error`, never on `message` (written for people) and never on the status alone — four different
situations share 401, one of which is an ordinary wrong password. Two paths produce a 401:
`JwtAuthenticationFilter` routes a broken or expired token through `GlobalExceptionHandler`,
while a request with no token at all is answered by `JsonAuthenticationEntryPoint` with
`AUTH_TOKEN_MISSING`.

### Frontend

```
components/  auth, booking, shared (form, icons)
config/      env.js, constants.js
context/     AuthContext — owns the session and its deadline
pages/       auth, booking, routes (guards)
services/    api/apiClient.js + endpoints/
```

Components call endpoint modules, never `fetch` — the base URL, the `Authorization` header and
the error shape are each defined in exactly one place.

**Sessions.** There is no refresh token, so the access token's lifetime is the whole session.
`AuthContext` converts the `expiresIn` the server reports into a wall-clock deadline in
`localStorage` and signs the user out the moment it passes, so a browser clock offset from the
server's cancels out. The timer is not trusted alone — it is checked again on mount, on return
to a hidden tab, and by a 401 backstop that `apiClient` fires for session-ending error codes.

New **public** endpoints under `endpoints/` should pass `{ auth: false }`, as
`authEndpoints.js` does: the JWT filter runs for the whole chain, so a stale token attached to
an otherwise-public request still 401s it.

---

## Known gaps

- No refresh token, by design — expiry means signing in again.
- `RATE_LIMITED` exists so the catalogue matches the API doc, but no limiter is wired.
- `AUTH_ACCOUNT_DEACTIVATED` (403) has no client handling; the `ApiError` reaches the calling
  page unchanged.
- Manual sign-out does not sync across tabs (expiry does — every tab arms its own timer).
- No tests beyond the generated context-load test.

---

## Working on it

Repo: [EEET2580-2026B-Blue-Fixinh/Service-Booking](https://github.com/EEET2580-2026B-Blue-Fixinh/Service-Booking).
Feature branches (`dev/auth`, `dev/booking`, `dev/admin`, `setup/mongodb`) merge into `main`
through pull requests. Commits follow `type(scope): summary`.
