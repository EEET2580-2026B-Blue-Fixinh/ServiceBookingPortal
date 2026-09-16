# Fixinh — Service Booking Portal (SBP)

<!-- Test comment to verify the commit and push workflow. -->

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
| Admin | ✅ | ✅ | Customers: list, activate and deactivate. Staff: list, search, create, update — relayed to SMP, which is mocked (`SMP_STAFF_MODE`) |
| Staff welcome email | ✅ | ✅ | Req 3.1.S5, §10.6: a letter on every staff creation, retried in the background. Written to `target/mail` on `dev`; sent for real with the `smtp` profile |
| Ratings | WIP | WIP | Package scaffolding only |
| Notifications / WebSocket | ✅ | ✅ | §9.1–9.3: list, mark read, real-time push over STOMP/SockJS at `/ws`. A `CONFIRMED` or `SCHEDULED` booking event raises one; opening the toast or the bell row goes to the booking |
| SMP integration — inbound | ✅ | — | §11.7 `POST /integration/booking-events`: shared-secret auth, replay handling by `eventId`, projection refresh, then the notification |
| SMP integration — outbound | WIP | — | Bookings reach SMP synchronously on creation; the outbox that makes that durable is not written. Staff: mock only — SMT publishes no staff API ([contract](docs/smp-staff-contract.md)) |
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
| `GET` | `/api/v1/notifications` | `CUSTOMER`, `ADMIN` — own notifications only |
| `PATCH` | `/api/v1/notifications/{id}/read`, `/api/v1/notifications/read-all` | `CUSTOMER`, `ADMIN` |
| `GET`, `POST` | `/api/v1/admin/staff` | `ADMIN` — relayed to SMP; a create also sends the welcome email (§10.6) |
| `GET`, `PUT` | `/api/v1/admin/staff/{staffId}` | `ADMIN` — relayed to SMP |
| `POST` | `/api/v1/integration/booking-events` | SMP only — `Authorization: Bearer <shared secret>`, never a JWT |
| `GET` | `/ws` (+ SockJS transport paths under it) | public handshake; the JWT is checked on the STOMP CONNECT frame |
| `GET` | `/actuator/health`, `/actuator/info` | public |

Each branch adds its own rows here; the table is not exhaustive.

Everything else is closed by default — `.anyRequest().authenticated()`, so an endpoint added
later is protected unless it is deliberately opened in
[SecurityConfig.java](Backend/src/main/java/com/fixinh/sbp/common/config/SecurityConfig.java).

---

## Running it

### Entire SBP + SMT stack with Docker

The root Compose file runs the SBP web app and API, the SMT API, PostgreSQL, and both SBP
MongoDB shards locally. It does not use Render or Atlas. By default, the separately checked-out
SMT repository resolves to `/Users/hoquanghuy/ServiceManagementPortal` from this checkout. If it
lives elsewhere, set `SMT_REPO_PATH` to that directory before running Compose.

```bash
docker compose up --build
```

Open `http://localhost:3000`. The host can reach SBP at `http://localhost:8443` and SMT at
`http://localhost:9443`. Inside the Docker network SBP calls `http://smt-api:9443/api/v1`;
using `localhost:9443` there would incorrectly point back to the SBP container.

Import
[`docker-local-sbp-smt-status-flow.postman_collection.json`](docs/postman/docker-local-sbp-smt-status-flow.postman_collection.json)
into Postman to drive a newly created booking through confirmation, technician scheduling,
working, and completion while checking both systems after each step.

Stop the stack without deleting database data:

```bash
docker compose down
```

To also reset every local test database, run `docker compose down -v` deliberately.

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
| `MONGO_URI_NORTH` / `MONGO_URI_SOUTH` | **none — required** | The two regional Atlas clusters. The only variables with no default: startup fails without them; the STOMP round-trip test skips itself and the context-load test fails |
| `SMP_MODE` | `mock` | `mock` = local fixtures, `live` = real SMP |
| `SMP_STAFF_MODE` | `mock` | Staff only, independent of `SMP_MODE`: SMT has no staff API yet, so staff stay mocked when bookings go live |
| `SMP_WEBHOOK_SECRET` | dev placeholder | What SMP presents on `/integration/**`. Blank refuses every integration call rather than admitting them all |
| `CORS_ALLOWED_ORIGINS` | localhost:3000 | No wildcards. The same list gates the `/ws` handshake |
| `EMAIL_MODE` | `file` | Leave it alone: `dev` pins `file`, and the `smtp` profile sets `smtp` — see below |
| `EMAIL_FROM_ADDRESS` / `EMAIL_FROM_NAME` | `no-reply@fixinh.vn` / `Fixinh` | Envelope sender of the staff welcome email |
| `EMAIL_OUTBOX_DIR` | `target/mail` | Where `file` mode writes. Git-ignored; `mvn clean` clears it |
| `EMAIL_STAFF_SIGN_IN_URL` | placeholder | SMP's sign-in page — staff cannot sign in to SBP |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | none | Read by the `smtp` profile (and so by `integration`, which brings it). No defaults: a missing one fails startup by name |
| `EMAIL_RETRY_ENABLED` | `true` | The background retry of refused emails. Turn it off for a one-off real send on the shared clusters |
| `EMAIL_RETRY_BASE_DELAY` / `EMAIL_RETRY_SWEEP_INTERVAL` | `PT2M` / `PT1M` | First wait after a refusal (it doubles after that), and how often the sweep runs. `PT20S` / `PT10S` for demos |
| `LOGGING_FILE_NAME` | none | Spring Boot's own. `target\sbp.log` gives the acceptance script a log to search |
| `CLOUDINARY_*`, `GOOGLE_*` | empty | Not yet used |

**Email.** `dev` is pinned to `file` mode: each staff welcome email is written to `target/mail`
as an `.eml` file and nothing leaves the machine (Req 3.1.S5's acceptance criterion). Sending
for real is opted into by name, with the `smtp` profile, which holds the provider settings;
`integration` brings it automatically through a profile group. A real send from a development
machine, with a Gmail account and an app password:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev,smtp'
$env:MAIL_HOST = 'smtp.gmail.com'; $env:MAIL_PORT = '587'
$env:MAIL_USERNAME = 'you@gmail.com'; $env:MAIL_PASSWORD = '<16-character app password>'
$env:EMAIL_RETRY_ENABLED = 'false'
.\mvnw.cmd clean spring-boot:run
```

Startup then reports `Email delivery: SMTP mode`. Turning the retry sweep off matters on the
shared clusters: it sends every outstanding email row it finds, teammates' included, and a
one-off send should send only what you trigger. Mailtrap's sandbox works the same way
(`sandbox.smtp.mailtrap.io`, port `2525`) and captures mail instead of delivering it. The app
password lives in that terminal only; no credential belongs in a committed file.

**Mock staff.** SMT's service covers booking creation and status changes only, so staff accounts
come from SBP's mock SMP client — `SMP_STAFF_MODE=mock`, the default even when `SMP_MODE=live`.
What a real or stand-in SMP staff API has to provide is in
[docs/smp-staff-contract.md](docs/smp-staff-contract.md). Two scripts drive the welcome email
through the real endpoint; run them from `Backend` with the backend up:

```powershell
# One receptionist (south) and one technician (north); checks each letter in target/mail.
# -Verify also reads the Notification row and Shard Directory entry back through mongosh.
powershell -ExecutionPolicy Bypass -File .\scripts\seed-mock-staff.ps1 -Verify

# Against a backend on dev,smtp: both letters land in your inbox, as plus-addresses of it.
powershell -ExecutionPolicy Bypass -File .\scripts\seed-mock-staff.ps1 -Mailbox you@gmail.com

# The issue's five acceptance criteria, PASS / FAIL / SKIP each. Start the backend with
# LOGGING_FILE_NAME=target\sbp.log, and PT20S / PT10S retry timings for a quick run.
powershell -ExecutionPolicy Bypass -File .\scripts\accept-staff-welcome-email.ps1 -Verify
```

Both sign in as the demo administrator from `Frontend/.env` (or `.env.example`), and every run
uses fresh addresses. Mock staff are forgotten when the backend stops; their notifications are
not, which is why mock staff ids are random rather than sequential. Run the acceptance script
while no teammate's backend is up against the same clusters: every backend sweeps the same
rows, and the retried letter can land in their outbox instead of yours.

Frontend config lives in [env.js](Frontend/src/config/env.js), read from `.env` — API base URL
(`VITE_API_URL`), the real-time channel's URL (`VITE_WS_URL`, `http://…/ws` — SockJS negotiates
the transport from an HTTP handshake, so not `ws://`) and the login page's demo-account shortcut
(`VITE_SHOW_DEMO_ACCOUNT`).

Profiles: `dev` (single PC, SSL off, SMP mocked), `integration` (multi-VM, SSL on, live SMP
bookings) and `smtp` (real email: stack it on `dev`; `integration` brings it itself).

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
components/  auth, booking, shared (form, icons, notifications, ToastStack)
config/      env.js, constants.js
context/     AuthContext — owns the session and its deadline
             NotificationContext — the notification list, and the real-time channel's lifetime
             ToastContext — the app-wide toast queue that ToastStack draws
hooks/       useNotifications (re-exported from the Context), useMyBookings, useVietnamAddress
pages/       auth, booking, admin, routes (guards)
services/    api/apiClient.js + endpoints/
             webSocketService.js — the only module that imports @stomp/stompjs or sockjs-client
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

**Real-time notifications (§9.3).** A notification is *saved first and delivered second*: the
store is the record, the socket is a courtesy. `NotificationGateway.push` hands a saved
notification to `convertAndSendToUser(recipientId, "/queue/notifications", …)`; a customer who
is not connected simply has no session to reach, and catches up from `GET /notifications` on
their next load or reconnect. The channel is outbound-only (Appendix E #1 settled as REST push):
clients never send, and the backend refuses SEND frames and any subscription other than the
caller's own queue.

Authentication is at the STOMP layer, not the HTTP one. SockJS cannot put a header on the
handshake, so `/ws` is open in `SecurityConfig` and the bearer token travels in the CONNECT
frame, where `StompAuthChannelInterceptor` applies the same checks as the HTTP filter (parse,
expiry, blacklist). A rejected frame gets an ERROR whose `message` header is the error code, as
§2.7 does for HTTP.

**What raises one (§11.7, Req 4.M3).** SMP owns the booking record, so a status change arrives
at `POST /integration/booking-events` rather than starting inside SBP. The integration module
validates it, resolves the customer's store, refreshes the booking projection, and only then
publishes an in-process event that the notification module listens for. That order is the
contract, not an accident of timing: §9.3 and Milestone 1 both require the projection to have
caught up before the customer is told, so that tapping a scheduled-booking toast opens a screen
that can already name the technician (Req 3.3.S5).

Only `CONFIRMED` and `SCHEDULED` produce a notification. Every other status refreshes the
projection silently — there is no `BOOKING_CREATED` or `BOOKING_CANCELLED` type, and booking
creation returns its own confirmation in the `POST` response (Req 3.3.S3).

A replayed `eventId` is answered, not repeated: `202 { "duplicate": true }`, no second row and
no second push. That holds at two levels — the inbound event is recorded under SMP's own id, and
the notification carries the `sourceEventId` it came from behind a sparse unique index in each
shard. An event is marked handled only after every listener has returned, so a delivery that
failed part-way is re-run on SMP's retry instead of being brushed off as a duplicate.

On the client, `NotificationProvider` opens the socket for exactly as long as a customer is
signed in — administrators receive no notifications and get no socket — and turns a pushed
message into the newest unread row and a toast: one push, one row, one toast, deduplicated by
id against the list. Reconnection backs off from 1 s to 30 s and re-reads the list once it is
back.

---

## Known gaps

- No refresh token, by design — expiry means signing in again.
- `RATE_LIMITED` exists so the catalogue matches the API doc, but no limiter is wired.
- `AUTH_ACCOUNT_DEACTIVATED` (403) has no client handling; the `ApiError` reaches the calling
  page unchanged.
- Manual sign-out does not sync across tabs (expiry does — every tab arms its own timer).
- The real-time channel is stateful: a customer is bound to the instance holding their
  socket. Fine for one instance; a second needs sticky sessions or a shared broker
  (Appendix E #9).
- An open socket is not re-validated when its token rotates or is revoked elsewhere; it
  re-authenticates on its next connect, and a revoked session ends through the HTTP path.
- No STOMP heartbeats. SockJS's own keep-alive frames cover idle proxies, and a backend restart
  closes the socket outright, which is what triggers the reconnect.
- The page-level `Toast` (top-centre, per page) predates the design mockup; the shared
  `ToastStack` is the mockup's. Pages should migrate their success messages to `useToast`.
- `/integration/**` is guarded by a shared secret, not by §11.1's token exchange. The filter
  grants the `SERVICE` role once the secret matches, so swapping in a signed service JWT later
  changes that one comparison and nothing downstream.
- An inbound event for a `bookingRef` SBP has no projection of is refused with
  `BOOKING_NOT_FOUND`. SBP does not build a booking from an event: every booking it knows was
  created through it, and a silent insert would hide a genuine disagreement between the two
  systems.
- Backend tests: `./mvnw test` runs the unit suites anywhere; the tests that bring up the
  application (`SbpApplicationTests`, the STOMP round trip, the booking-event flow) need the two
  Atlas URIs and are skipped or fail without them. The frontend has no test runner yet.
- `CustomerServiceImpl` implements both the internal and the external customer interface, so a
  test cannot substitute the module by either interface alone — it has to mock the class. Worth
  splitting into two beans.

---

## Working on it

For the split between outbound outbox work (#55) and the inbound booking-event receiver
(§11.7, Req 3.3.U2), see [SMP integration scope](docs/smp-integration-scope.md).

Repo: [EEET2580-2026B-Blue-Fixinh/Service-Booking](https://github.com/EEET2580-2026B-Blue-Fixinh/Service-Booking).
Feature branches (`dev/auth`, `dev/booking`, `dev/admin`, `setup/mongodb`) merge into `main`
through pull requests. Commits follow `type(scope): summary`.
