# Fixinh — Service Booking Portal (SBP)

The customer-facing half of **Fixinh**, a home-repair platform. Customers register, book a repair
service, follow the booking until the work is done, are notified the moment it is confirmed or
scheduled, and rate the result. Administrators manage customer accounts and, through the partner
system, staff accounts.

The **Service Management Portal (SMP)**, built by the partner team, owns bookings, technicians and
staff. SBP creates bookings in SMP over REST, keeps its own copy (a *projection*) of each
customer's bookings, and receives status changes back from SMP over REST.

Built for **EEET2580 Enterprise Application Development** (RMIT University, Semester 2026B) by
team **SB**, squad **Blue**.

**Stack:** Spring Boot 4.1 on Java 17 · MongoDB split into two regional stores · React 19, Vite 8
and Tailwind CSS 4 · STOMP over SockJS · Docker

## Contents

- [Team](#team)
- [Features](#features)
- [Tech stack](#tech-stack)
- [Repository layout](#repository-layout)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [Architecture](#architecture)
- [UX design](#ux-design)
- [API overview](#api-overview)
- [Testing](#testing)
- [Deployment](#deployment)
- [Documentation](#documentation)
- [Known limitations](#known-limitations)
- [Contributing](#contributing)

---

## Team

| Name | Student ID |
|---|---|
| Huy Ho Quang | s3924729 |
| Anh Nguyen Hong | s3924711 |
| Nam Le Tran Nhat | s3911298 |
| Nhat Vo Phuc Duy | s3868763 |
| Khanh Vo Hoang | s3926310 |

---

## Features

Requirement IDs refer to the Service Booking Team SRS v1.1. Status: ✅ implemented ·
⚠️ partly implemented · ❌ not implemented.

| Requirement | What SBP does | Status |
|---|---|---|
| 3.1.S1–S4 | Sign-up form with email, phone, password and confirmation, and an address made of street number, street name, ward and a city dropdown of Vietnam's 34 provinces and municipalities | ✅ |
| 3.1.S5 | Welcome email to every new receptionist or technician, retried in the background if the provider refuses it | ✅ |
| 3.1.M1–M4 | Password strength, email syntax and phone rules checked in the browser and again on the server; the server's `fieldErrors` carry the rule, the cause and a valid example | ⚠️ The browser shows shorter messages without the example, and its email and phone checks don't match the SRS rules exactly |
| 3.1.U1–U2 | Customer data split by province into a south store and a north store; the client never names a store | ✅ See [known limitations](#known-limitations) for moving between regions |
| 3.2.S1 | Sign in with email or phone number and password; passwords stored as BCrypt hashes | ✅ |
| 3.2.M1–M3 | Signed JWT carrying the user id, email and role; a missing, malformed, expired or revoked token is refused with its own error code | ✅ |
| 3.2.M4 | Sign-out clears the browser session and revokes the tokens on the server | ✅ |
| 3.2.U1–U3 | Google sign-in (OAuth 2.0 authorization code): a new email creates a customer account and asks for an address; a known email signs in to that account and links Google to it | ✅ |
| 3.3.S1–S3 | Book exactly one service with an address, preferred date, time window and issue description; the booking starts as Created and a confirmation screen follows | ✅ |
| 3.3.S4–S6 | Booking list with status, service, preferred date and creation date; scheduled bookings highlight the visit time, duration and technicians; completed bookings show service fee, material cost and total | ✅ |
| 3.3.M1 | Cancel with a reason and up to three photos: Created, Confirmed and Scheduled become Cancelled by Customer, Working becomes Terminated by Customer | ✅ |
| 3.3.M2–M6 | Active bookings above past ones; case-insensitive partial search by booking reference or technician name; filter by service and an inclusive creation-date range; sort by creation date either way | ✅ |
| 3.3.U1 | Responsive layout: sidebar on wide screens, bottom tab bar on phones | ✅ |
| 3.3.U2 | Live toast and bell entry when a booking becomes Confirmed or Scheduled, with a history, an unread count and mark-as-read | ✅ |
| 3.4.S1–S3 | One rating per completed booking (1–5 stars and a review), shown again on that booking | ✅ |
| 3.4.M1–M3 | Up to three photos, Integrity / Quality / Friendliness sub-scores, and deletion | ✅ |
| 3.4.S4, 3.4.U1–U2 | Ratings and aggregated rating statistics served to SMP | ❌ |
| 3.5.S1, 3.5.M2 | Create, update, view and search receptionists and technicians through the SMP staff client | ✅ Served by a mock, because SMT publishes no staff API |
| 3.5.S2, 3.5.M1 | View customers, search them by name, email or ID, and activate or deactivate them | ✅ |
| 4.S1, 4.M2 | Customer and rating APIs for SMP, reachable by SMP only | ⚠️ The SMP-only guard is in place; the APIs behind it are not |
| 4.S2 | Mock SMP clients, so SBP runs on its own | ✅ |
| 4.M1 | Live SMP client for the service catalogue, booking creation and the rating eligibility check | ⚠️ No technician client, and cancellation is still decided locally |
| 4.M3 | SMP pushes booking changes to `POST /api/v1/integration/booking-events` | ✅ |
| 4.M4 | Customer listing, search and activation; staff listing, creation, update and search | ✅ Staff through the mock, as above |
| 4.U1 | SMP never names a store; SBP routes by customer | ⚠️ True for inbound events; there are no read APIs yet |
| 5.S1 | Single-PC development setup (`dev` profile) | ✅ |
| 5.M1 | Multi-machine setup against the live SMP (`integration` profile) | ⚠️ Needs a keystore first — see [More than one machine](#more-than-one-machine) |
| 5.U1 | Web app, API, SMT and databases as Docker containers under Compose | ✅ |

---

## Tech stack

| Layer | Technologies |
|---|---|
| Backend | Java 17, Spring Boot 4.1 (Web MVC, Security, Data MongoDB, WebSocket, Validation, Mail, Actuator), JJWT 0.12, Google API Client 2.2, Cloudinary SDK 2.3, Lombok, Maven wrapper |
| Frontend | React 19, React Router 7, Vite 8, Tailwind CSS 4, `@stomp/stompjs` 7, `sockjs-client` 1.6, ESLint 10, pnpm |
| Data | MongoDB: `sbp_south` and `sbp_north` (Atlas in development, `mongo:7.0` containers in Docker) |
| Delivery | Docker, Docker Compose, nginx, Render (API), Vercel (web app) |

---

## Repository layout

```
Backend/             Spring Boot API (modular monolith)
  scripts/           PowerShell seed and acceptance scripts for the staff welcome email
Frontend/            React single-page app
MockSMP/             Standalone stand-in for SMP's staff API
docs/                Integration notes, demo guides, Postman collections, sample photos
docker-compose.yml   Local stack: SBP web and API, SMT API, PostgreSQL, both MongoDB stores
render.yaml          Render blueprint for the API
```

---

## Getting started

### Prerequisites

- JDK 17 or newer. The Maven wrapper downloads Maven itself.
- Node.js 20.19+ or 22.13+, and pnpm (`corepack enable`, or `npm install -g pnpm`).
- For a local run: the connection strings of the team's two MongoDB Atlas clusters, with your IP
  address on the Atlas access list.
- For the Docker stack: Docker Desktop and a checkout of the
  [SMT repository](https://github.com/EEET2580-2026B-Blue-Fixinh/ServiceManagementPortal).
- Optional: Postman, to play SMP's part, and `mongosh`.

Commands are shown for PowerShell first, then macOS/Linux.

### Run locally

**Backend**, from `Backend/`:

```powershell
Copy-Item .env.example .env      # then fill in MONGO_URI_SOUTH and MONGO_URI_NORTH
.\mvnw.cmd spring-boot:run
```

```bash
cp .env.example .env             # then fill in MONGO_URI_SOUTH and MONGO_URI_NORTH
./mvnw spring-boot:run
```

The API starts on <http://localhost:8443> with the `dev` profile: plain HTTP, SMP mocked, emails
written to `target/mail` and uploaded photos to `target/uploads`.
<http://localhost:8443/actuator/health> should report `UP`. `.env` holds `KEY=value` lines
without quotes, and a variable set in the shell overrides the same name there.

**Frontend**, from `Frontend/`:

```powershell
Copy-Item .env.example .env
pnpm install
pnpm dev
```

```bash
cp .env.example .env
pnpm install
pnpm dev
```

Open <http://localhost:3000>. The dev server must stay on port 3000: it is the only origin the
backend's default CORS policy admits.

**MockSMP** (optional) is only needed to exercise the admin staff screens over real HTTP instead of
the in-process mock. From `MockSMP/`, run `.\mvnw.cmd spring-boot:run` (or
`./mvnw spring-boot:run`); it listens on <http://localhost:8090>. Then restart the backend with:

```
SMP_STAFF_MODE=live
SMP_STAFF_BASE_URL=http://localhost:8090
SMP_STAFF_SERVICE_TOKEN=dev-only-shared-secret
```

See [MockSMP/README.md](MockSMP/README.md) for details.

### Run the full stack with Docker

The root Compose file runs the SBP web app and API, Team SMT's API with its PostgreSQL database,
and two local MongoDB containers in place of Atlas.

1. Check out the SMT repository. Compose looks for it at `../../../ServiceManagementPortal`,
   relative to this repository; point `SMT_REPO_PATH` at it if yours is elsewhere:

   ```powershell
   $env:SMT_REPO_PATH = 'C:\path\to\ServiceManagementPortal'
   ```

   ```bash
   export SMT_REPO_PATH=/path/to/ServiceManagementPortal
   ```

2. Create `Backend/.env` from `Backend/.env.example`. Compose refuses to start without the file,
   and overrides its database, JWT, SMP and email settings. Fill in `GOOGLE_CLIENT_ID` and
   `GOOGLE_CLIENT_SECRET` if you want Google sign-in.
3. Build and start everything:

   ```
   docker compose up --build
   ```

4. Open <http://localhost:3000>. From your machine, SBP's API is on <http://localhost:8443> and
   SMT's on <http://localhost:9443>. Inside the Compose network SBP reaches SMT at
   `http://smt-api:9443/api/v1`; `localhost` there would point back at the SBP container.
5. The local databases start empty. Register a customer on the sign-up page, and
   [create an administrator](#creating-an-administrator) before using the admin portal.

Stop the stack with `docker compose down`. Add `-v` only if you mean to delete every local database
and stored file.

In this stack SBP talks to the real SMT service (`SMP_MODE=live`), staff stay mocked, and SMT's
events are accepted with the secret `local-webhook-secret`.
[docker-local-sbp-smt-status-flow.postman_collection.json](docs/postman/docker-local-sbp-smt-status-flow.postman_collection.json)
takes a new booking through confirmation, scheduling, work and completion in both systems. Welcome
letters are written inside the API container under `/app/data/mail`;
[the email demo guide](docs/demo-staff-welcome-email.md#d-docker-compose) shows how to read them.

### Accounts

Two demo accounts exist on the team's shared Atlas clusters. With `VITE_SHOW_DEMO_ACCOUNT=true`
(as in `Frontend/.env.example`), the sign-in page offers them in a click-to-fill box.

| Role | Email | Password |
|---|---|---|
| Customer | `an.nguyen@gmail.com` | `Fixinh@2025` |
| Administrator | `admin@fixinh.vn` | `Admin@2025` |

A new database, including the Docker stack's, has neither. Customers sign up through the app.
There is no sign-up for administrators, so the first one is inserted by hand. The Docker build of
the web app leaves `.env` out, so the click-to-fill box is hidden there; type the credentials
instead.

#### Creating an administrator

Open a shell on the **south** store:

```
docker compose exec mongo-south mongosh sbp_south     # Docker stack
mongosh "<your MONGO_URI_SOUTH>"                       # any other database
```

Then paste:

```js
const id = new ObjectId();
db.credentials.insertOne({
  _id: id,
  email: "admin@fixinh.vn",
  passwordHash: "$2a$10$NAK7r0jq7h2tKKnWqYR5RuhBsPOfraXbqPd48Iys0vC9jlQqwZWmS",
  accountStatus: "ACTIVE",
  accountRole: "ADMIN",
  authProviders: ["PASSWORD"],
  authenticationIdentities: [],
  registeredAt: new Date()
});
db.administrators.insertOne({
  _id: id,
  email: "admin@fixinh.vn",
  fullName: "Fixinh Administrator",
  role: "ADMIN",
  masterAdmin: true,
  registeredAt: new Date()
});
```

The hash is the BCrypt hash of `Admin@2025`, so the demo credentials above work. Keep the email
lower-case, and keep `_id` an `ObjectId`: the backend stores these ids as ObjectIds, and a string
id breaks sign-in.

### Try the whole flow

1. Sign in as the customer, choose **New booking**, book a service and note its
   `ORD-YYYY-NNNN` reference.
2. Play SMP's part. Import
   [sbp-booking-status-flow.postman_collection.json](docs/postman/sbp-booking-status-flow.postman_collection.json)
   into Postman and set its variables: `sbpBaseUrl` to `http://localhost:8443/api/v1`,
   `smpSecret` to `dev-only-shared-secret`, and the customer's email, password and `bookingRef`.
   Send the requests from the top. With the app open, **Confirmed** and **Scheduled** arrive as
   toasts and bell entries, and **Completed** adds the bill.
3. Open the completed booking and rate it; the photos in [docs/demo-images](docs/demo-images) work
   as evidence. Cancel a different booking to see the cancellation flow.
4. Sign in as the administrator. Search the customer list, deactivate and reactivate an account,
   then create a technician and open the welcome letter in `Backend/target/mail`.

<details>
<summary>Sending a booking event without Postman</summary>

```http
POST /api/v1/integration/booking-events
Host: localhost:8443
Authorization: Bearer dev-only-shared-secret
Content-Type: application/json

{
  "eventId": "evt-0001",
  "eventType": "TECHNICIAN_ASSIGNED",
  "occurredAt": "2026-09-17T09:00:00Z",
  "bookingRef": "ORD-2026-1234",
  "customerId": "<user.userId from POST /api/v1/auth/login>",
  "payload": {
    "status": "SCHEDULED",
    "scheduledStartAt": "2026-09-18T02:00:00Z",
    "estimatedDurationMinutes": 120,
    "technicians": [{ "technicianId": "TECH-002", "fullName": "Tran Quoc Bao" }]
  }
}
```

The answer is `202` with `{"eventId":"evt-0001","accepted":true,"duplicate":false}`. Sending the
same `eventId` again returns `"duplicate": true` and changes nothing. Give each new event a later
`occurredAt` than the last one for that booking; an older event never overwrites a newer state. A
bill needs both `serviceFee` and `materialCost`, and SBP computes the total itself.

</details>

---

## Configuration

Backend settings live in
[application.yml](Backend/src/main/resources/application.yml), under `fixinh.sbp`, and are bound
to the typed `SbpProperties` class. Every value can come from an environment variable or
`Backend/.env`. Everything except the MongoDB URIs and the SMTP settings has a default that is
safe for development, so no secret is committed.

### Profiles

| Profile | For | What it changes |
|---|---|---|
| `dev` (default) | One machine | Plain HTTP, SMP mocked unless `SMP_MODE=live`, email always written to files, debug logging |
| `smtp` | Real email | Sends through the `MAIL_*` settings; combine as `dev,smtp` |
| `integration` | Several machines, live SMP | HTTPS on, live SMP bookings, and the `smtp` profile |
| `production` | Render | Has no profile file: the base configuration plus the variables in `render.yaml` |

### Backend variables

| Variable | Default | Purpose |
|---|---|---|
| `MONGO_URI_SOUTH`, `MONGO_URI_NORTH` | **required** | The two regional stores. Each URI must end with its database name (`…/sbp_south`, `…/sbp_north`) before any query string |
| `MONGO_SERVER_SELECTION_TIMEOUT_SECONDS` | `30` | How long to wait for a store before failing |
| `SPRING_PROFILES_ACTIVE` | `dev` | See [Profiles](#profiles) |
| `SERVER_PORT` | `8443` | Port the API listens on |
| `SSL_ENABLED`, `SSL_KEYSTORE`, `SSL_KEYSTORE_PASSWORD`, `SSL_KEY_ALIAS` | `false`, `classpath:keystore.p12`, `changeit`, `sbp` | HTTPS. No keystore is committed; see [More than one machine](#more-than-one-machine) |
| `JWT_SECRET` | development placeholder | HMAC signing key, at least 32 bytes |
| `JWT_ISSUER` | `fixinh-sbp` | Checked on every token |
| `JWT_ACCESS_TOKEN_TTL_MINUTES` | `60` | Access-token lifetime, reported to the client as `expiresIn` |
| `JWT_REFRESH_TOKEN_TTL_DAYS` | `30` | Refresh-token lifetime |
| `CORS_ALLOWED_ORIGINS` | `https://localhost:3000,http://localhost:3000` | Comma-separated, no wildcards. Also applied to the `/ws` handshake |
| `SMP_MODE` | `mock` | Bookings and catalogue: `mock` or `live` |
| `SMP_BASE_URL`, `SMP_SERVICE_TOKEN` | `http://localhost:9443/api/v1`, `local-integration-token` | Where `live` mode calls SMT, and the bearer token it sends |
| `SMP_STAFF_MODE` | `mock` | Staff: `mock`, or `live` for a MockSMP instance. Never SMT, which has no staff API |
| `SMP_STAFF_BASE_URL`, `SMP_STAFF_SERVICE_TOKEN` | `http://localhost:8090`, `dev-only-shared-secret` | Where `SMP_STAFF_MODE=live` calls |
| `SMP_WEBHOOK_SECRET` | `dev-only-shared-secret` | The bearer token SMP must present on `/api/v1/integration/**`. A blank value refuses every call |
| `EMAIL_MODE` | `file` | Leave it alone: `dev` forces `file`, and the `smtp` profile sets `smtp` |
| `EMAIL_FROM_ADDRESS`, `EMAIL_FROM_NAME` | `no-reply@fixinh.vn`, `Fixinh` | Sender of the welcome email |
| `EMAIL_OUTBOX_DIR` | `target/mail` | Where `file` mode writes `.eml` files |
| `EMAIL_STAFF_SIGN_IN_URL` | `http://localhost:8081/login` | SMP's sign-in page, linked in the letter (staff cannot sign in to SBP) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | none | SMTP transport. Required by the `smtp` profile, which fails at startup naming any that is missing |
| `EMAIL_RETRY_ENABLED` | `true` | Background retry of refused letters |
| `EMAIL_RETRY_INITIAL_DELAY`, `EMAIL_RETRY_SWEEP_INTERVAL` | `PT30S`, `PT1M` | When the retry sweep first runs after startup, and how often after that |
| `EMAIL_RETRY_BASE_DELAY` | `PT2M` | Wait after the first refusal; it doubles after each further one |
| `EMAIL_RETRY_MAX_ATTEMPTS`, `EMAIL_RETRY_BATCH_SIZE` | `5`, `50` | After the last attempt a letter is marked abandoned |
| `IMAGE_STORAGE_MODE` | `local` | `local` writes photos to disk; `cloudinary` uploads them |
| `IMAGE_LOCAL_DIR` | `target/uploads` | Where `local` mode writes |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | empty | Read in `cloudinary` mode only |
| `MULTIPART_MAX_FILE_SIZE`, `MULTIPART_MAX_REQUEST_SIZE` | `8MB`, `26MB` | Server upload ceilings. The 5 MB-per-photo rule is enforced by the validators, with a clearer message |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | empty | Google OAuth client |
| `GOOGLE_REDIRECT_URI` | `http://localhost:3000/login` | Must equal the redirect URI the web app sends, and be registered with Google |
| `LOGGING_FILE_NAME` | none | Spring Boot's log file, which the acceptance script searches |

Durations use ISO-8601 (`PT30S`, `PT2M`).

### Frontend variables

Read from `Frontend/.env` at build time by [env.js](Frontend/src/config/env.js):

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8443/api/v1` | REST base URL |
| `VITE_WS_URL` | `http://localhost:8443/ws` | Real-time channel. Use `http(s)://`, not `ws://`: SockJS starts with an HTTP request |
| `VITE_GOOGLE_CLIENT_ID` | empty | Required for **Continue with Google** |
| `VITE_GOOGLE_REDIRECT_URI` | current origin + `/login` | Must match the backend's `GOOGLE_REDIRECT_URI` |
| `VITE_SHOW_DEMO_ACCOUNT` | off (`true` in `.env.example`) | Shows the click-to-fill demo accounts |
| `VITE_DEMO_CUSTOMER_EMAIL`, `VITE_DEMO_CUSTOMER_PASSWORD`, `VITE_DEMO_ADMIN_EMAIL`, `VITE_DEMO_ADMIN_PASSWORD` | empty | The accounts that box fills in |

### Sending real email from a development machine

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev,smtp'
$env:MAIL_HOST = 'smtp.gmail.com'; $env:MAIL_PORT = '587'
$env:MAIL_USERNAME = 'you@gmail.com'; $env:MAIL_PASSWORD = '<16-character app password>'
$env:EMAIL_RETRY_ENABLED = 'false'
.\mvnw.cmd clean spring-boot:run
```

Startup then logs `Email delivery: SMTP mode`. Keep the retry sweep off against the shared
clusters: every running backend sweeps the same rows, teammates' letters included.
[docs/demo-staff-welcome-email.md](docs/demo-staff-welcome-email.md) covers Gmail app passwords,
Mailtrap and troubleshooting.

---

## Architecture

### System context

```mermaid
flowchart LR
    Web["SBP web app<br/>React + Vite"]
    API["SBP API<br/>Spring Boot modular monolith"]
    South[("MongoDB south store<br/>Huế and further south")]
    North[("MongoDB north store<br/>Quảng Trị and further north")]
    SMP["Service Management Portal<br/>partner team"]
    Google["Google OAuth 2.0"]
    Cloud["Cloudinary"]
    Mail["SMTP provider"]

    Web -->|"REST /api/v1 with JWT"| API
    API -.->|"live notifications, STOMP over SockJS"| Web
    API --> South
    API --> North
    API -->|"catalogue, bookings, staff"| SMP
    SMP -->|"booking events, shared secret"| API
    Web -->|"consent redirect"| Google
    API -->|"code exchange"| Google
    API -->|"evidence photos"| Cloud
    API -->|"staff welcome email"| Mail
```

### Backend

A modular monolith (A.2.1). `com.fixinh.sbp` has two top-level packages: `modules/`, with one
package per business capability, and `common/`, with what they share.

| Module | Owns |
|---|---|
| `auth` | Sign-up, password and Google sign-in, token refresh and sign-out |
| `customer` | Customer profiles, the signed-in customer's profile and address, store placement |
| `booking` | Booking creation, list, detail and cancellation, and the local projection of SMP's bookings |
| `rating` | Ratings with sub-scores and photos |
| `notification` | Notification history, the real-time gateway, the staff welcome email and its retry |
| `admin` | Administrator profiles, customer activation, staff management relayed to SMP |
| `reference` | Provinces, the local copy of SMP's service catalogue, rating categories |
| `integration` | The inbound booking-event endpoint and replay tracking |

| `common` package | Contents |
|---|---|
| `config` | `SecurityConfig`, `CorsConfig`, `WebSocketConfig`, and the typed `SbpProperties` |
| `security` | JWT issuing and checking, the revoked-token list, the SMP shared-secret filter |
| `sharding` | The two MongoDB templates, `RegionResolver`, index creation, the shard directory |
| `smp` | `SmpBookingClient` and `SmpStaffClient`, each with a mock and a live implementation |
| `exception`, `dto` | `ErrorCode`, `GlobalExceptionHandler`, and the shared list and page shapes |
| `email`, `storage`, `validation` | Email clients (file or SMTP), image storage (local or Cloudinary), shared profile field rules |

**Inside a module** (A.1.2, A.2.2–A.2.4, A.3.1–A.3.2), with booking as the example:

```
modules/booking/
├── controller/              HTTP only: binds the request, calls an interface, returns a DTO
├── interfaces/external/     BookingExternalService: what this module's controller calls
├── interfaces/internal/     BookingInternalService: what other modules may call
├── service/                 BookingServiceImpl, BookingInternalServiceImpl and the validators
├── repository/              ShardedServiceBookingRepository: sends each query to the right store
├── model/                   ServiceBooking, BookingStatus, Bill, TechnicianRef
└── dto/
    ├── external/            response bodies such as BookingDetailDto and BookingListResponse
    └── internal/            commands, filters and in-process views such as BookingCreateCommand
```

- A request goes controller → interface → service → repository. Controllers never touch a
  repository.
- A module reaches another only through that module's interfaces; no module imports another
  module's `service` or `repository` package. The rating module, for example, checks ownership
  through `BookingInternalService.findOwnedForRating`.
- The integration module never calls the notification module. After refreshing a booking it
  publishes an in-process `BookingStatusChangedEvent`, and the notification module's
  `BookingStatusChangeListener` reacts to it.
- Collections are always wrapped (`{"items": [...]}`) or paged, never returned as bare arrays.

**Regional sharding** (3.1.U1–U2). Customer data lives in two MongoDB databases, `sbp_south` and
`sbp_north`. The `Province` enum lists all 34 provinces and municipalities of the 2025 structure
with the store each belongs to: Huế and everything south of it go to the south store, Quảng Trị and
everything north of it to the north store. The same enum feeds `GET /reference/provinces`, so the
dropdown and the router can never disagree. The client only sends a province code, and
`RegionResolver` picks the store. An account's credential and profile live together in one store,
and so do its bookings, ratings and notifications; nothing is copied to the other. Sign-in by email
or phone cannot know the store in advance, so the credential lookup asks the south store first,
then the north. Administrators have no address and live in the south store. Email and phone
uniqueness across both stores is checked before insert, since each store's unique index sees only
itself.

**Security** (3.2, A.2.6). `SecurityConfig` builds a stateless filter chain with BCrypt passwords
and everything closed unless deliberately opened:

| Path | Who may call it |
|---|---|
| `/api/v1/auth/register`, `/login`, `/oauth/google`, `/refresh` | Anyone |
| `/api/v1/reference/provinces` | Anyone (the sign-up form needs it) |
| `/actuator/health`, `/actuator/info` | Anyone |
| `/ws/**` | Anyone for the handshake; the token is checked on the STOMP `CONNECT` frame |
| `/api/v1/admin/**` | `ADMIN` |
| `/api/v1/bookings/**`, `/api/v1/ratings/**` | `CUSTOMER` |
| `/api/v1/notifications/**` | `CUSTOMER` or `ADMIN` |
| `/api/v1/integration/**` | SMP only, by shared secret |
| Everything else, including `/auth/logout` | Any signed-in user |

- Access tokens are HMAC-signed JWTs carrying `sub`, `email`, `role` and a unique `jti`, valid for
  60 minutes by default.
- The refresh token is an opaque random value, stored only as a SHA-256 hash. Every use replaces it, and
  presenting an old one ends the session, which is how a stolen token gets noticed. Signing in on
  a second device replaces the first device's refresh token.
- Sign-out puts the access token's `jti` on a revoked list until the token would have expired
  (a TTL index cleans the list up) and deletes the refresh token.
- `IntegrationAuthenticationFilter` compares `Authorization: Bearer <SMP_WEBHOOK_SECRET>` in
  constant time and answers every mismatch with `403 INTEGRATION_FORBIDDEN`. The JWT filter never
  reads those paths.
- CORS admits only the configured origins, and the same list gates the WebSocket handshake.

**Errors.** Every failure has the same JSON shape, and clients branch on `error`, never on the
message or the status alone (several situations share 401, one of them an ordinary wrong
password):

```json
{
  "timestamp": "2026-09-17T09:00:00Z",
  "path": "/api/v1/auth/register",
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "...",
  "fieldErrors": [
    { "field": "password", "code": "PASSWORD_MISSING_DIGIT", "message": "...", "cause": "...", "example": "..." }
  ],
  "traceId": "..."
}
```

`ErrorCode` holds the whole catalogue. A booking or notification that belongs to someone else is
reported as not found, so ids cannot be probed.

**Real-time notifications** (3.3.U2, 4.M3). A notification is saved first and delivered second:
the store is the record, and the socket is a courtesy.

1. SMP posts a change to `POST /api/v1/integration/booking-events`.
2. `BookingEventValidator` checks it. A replayed `eventId` is answered with
   `202 {"duplicate": true}` and nothing else happens.
3. The booking projection in the customer's store is refreshed. An event older than the last one
   applied leaves it unchanged, and a bill's total is recomputed from its parts.
4. `BookingStatusChangedEvent` is published in process. For Confirmed and Scheduled only, the
   notification module stores a notification tagged with the event id, behind a sparse unique
   index, so a retried event can never notify twice.
5. `NotificationGateway` pushes it to the customer's `/user/queue/notifications`. The browser
   shows a toast and a new bell entry, and either one opens the booking.

The channel is STOMP over SockJS at `/ws`. SockJS cannot add headers to the handshake, so the JWT
travels in the `CONNECT` frame and `StompAuthChannelInterceptor` checks it the same way the HTTP
filter does. The channel is outbound only: the server refuses `SEND` frames and any subscription
other than the caller's own queue. The browser reconnects with backoff from 1 s up to 30 s and
re-reads the list once it is back. A customer who was offline catches up from
`GET /notifications`. Administrators receive no notifications and open no socket.

**SMP integration** (4.S2, 4.M1, 4.M4). `SmpBookingClient` and `SmpStaffClient` each have a mock
and a live implementation, chosen by `SmpClientConfig`; nothing else knows which one is active.
The service catalogue is copied into MongoDB at startup (`RepairServiceCatalogSeeder`), so the
booking form reads SBP's own copy. Staff have their own switch because SMT publishes no staff API:
`SMP_STAFF_MODE=live` points at [MockSMP](MockSMP/README.md), which implements
[the staff contract](docs/smp-staff-contract.md) over HTTP.

**Staff welcome email** (3.1.S5). Creating a receptionist or technician relays the request to SMP,
then sends a letter with the initial password and SMP's sign-in link. `file` mode, the default and
always on `dev`, writes `.eml` files; the `smtp` profile sends them. If the provider refuses, the
request returns `502 EMAIL_DELIVERY_FAILED` but the account stands, and a scheduled sweep retries
with doubling delays until the letter is sent or abandoned. The stored copy of the letter, which
contains the password, is cleared once either happens.

**Evidence photos** (3.3.M1, 3.4.M1). Up to three JPEG or PNG images of at most 5 MB each,
validated before anything is uploaded. They are relayed through the backend, so the Cloudinary
secret never reaches the browser. A failed upload changes nothing and returns
`502 UPLOAD_FAILED`.

### Frontend

The app is organised in tiers (A.1.1), with business components grouped by capability (A.2.c,
A.3.a):

```
Frontend/src/
├── pages/                 route-level screens: auth, booking, profile, rating, admin; route guards
├── components/
│   ├── admin/customers/   ui/  hooks/  services/
│   ├── admin/staff/       ui/  hooks/  services/
│   ├── auth/              sign-in and sign-up layout, with its own CSS
│   ├── booking/           cards, status badge, cancel dialog, rating card; hooks/
│   └── shared/            reusable parts: AppShell, form fields, dialogs, pagination,
│                          notifications, toasts, icons
├── hooks/                 cross-page hooks: useMyBookings, usePagedList, useVietnamAddress
├── context/               AuthContext, NotificationContext, ToastContext
├── services/              api/apiClient.js and api/endpoints/, webSocketService.js,
│                          googleAuthService.js, vietnamAddressApi.js
├── config/                env.js, api.js, constants.js
└── utils/                 formatting, validators, sort comparators
```

- **Separation of concerns.** Pages compose components and hooks. Components render, hooks own
  interaction state, and services own every request. The admin packages go furthest: each keeps
  its markup in `ui/`, its state and handlers in `hooks/` and its calls in `services/`.
- **One API client** (A.1.3). No component calls `fetch`. `apiClient` adds the base URL and the
  `Authorization` header, sends JSON or multipart bodies, turns every failure into an `ApiError`
  with `code`, `fieldErrors` and `traceId`, and ends the session when a token is rejected.
- **One route table** (A.1.4). [config/api.js](Frontend/src/config/api.js) lists every backend
  path, grouped by domain.
- **Global state only where it is shared** (A.2.b). The session, the notification list and the
  toast queue live in the three contexts; everything else is local component state.
- **Route guards** (A.2.a). `ProtectedRoute` requires a session. `AdminRoute` also requires the
  `ADMIN` role and sends a customer back to their bookings. The backend enforces the same rules.
- **Sessions.** `AuthContext` stores the tokens and the access token's deadline. It refreshes 30
  seconds before expiry, checks again when a hidden tab returns, and never runs two refreshes at
  once, since the backend would treat the second as a stolen token.
- **Real-time.** `webSocketService.js` is the only module that imports the STOMP and SockJS
  libraries; `NotificationContext` owns the connection's lifetime.
- **Addresses.** Provinces come from SBP's own `/reference/provinces`; wards come from the public
  [provinces.open-api.vn](https://provinces.open-api.vn) v2 API, which follows the 2025 structure.
- **Styling.** Tailwind CSS utilities on a small colour palette defined in `index.css`. The auth
  screens and `SelectField` keep their CSS in a file beside the component.

---

## UX design

**Patterns**

- **Navigation.** One `AppShell` frames both portals: a sticky sidebar from 1024 px up, a bottom
  tab bar below that, a breadcrumb and title in the header, and `aria-current` on the active item.
  Route guards send people to the page that fits their role.
- **Page layout.** Card lists lead to a detail page per booking. Active and past bookings sit
  under tabs. A new booking takes three screens: choose a service, fill in the details, see the
  confirmation.
- **Interaction.** Modal dialogs for cancelling a booking and for activating or deactivating a
  customer; stacked toasts; search as you type; filters and sort beside the list; pagination; a
  notification bell with an unread badge and **Mark all read**.

**Nielsen's heuristics**

| Heuristic | In SBP |
|---|---|
| Visibility of system status | Status badges on every booking, a "Last synced" time on booking details, live toasts, an unread count on the bell, busy labels such as "Deleting…" |
| Match with the real world | Vietnam's 2025 address structure (province, then ward), prices in VND (`500.000 ₫`), family name entered first, notification times in Ho Chi Minh City time |
| User control and freedom | Dialogs can be dismissed and keep what was typed after a failed request; sign-out is in the sidebar, and on the Profile screen on phones |
| Consistency and standards | One shell and one set of form fields, dialogs, pagination and toasts across both portals; one error format from the API |
| Error prevention | A live password checklist, date and time-window checks before submitting, Cancel offered only on active bookings and rating only on completed ones, confirmation before changing a customer's status |
| Recognition rather than recall | City and ward dropdowns, service cards with a category filter, click-to-fill demo accounts |
| Flexibility and efficiency | Search, service and date filters, sorting, page sizes on admin lists, and a toast that opens its booking directly |
| Aesthetic and minimalist design | A small colour palette, Material Symbols icons, animations that respect reduced-motion settings |
| Recognising and recovering from errors | Messages beside the field that failed, from the browser's checks and from the server's `fieldErrors`; **Try again** on failed loads; after a forced sign-out, a notice and a return to the page you were on |
| Help and documentation | Short hints on the forms, such as why the address is needed |

---

## API overview

Base URL `http://localhost:8443/api/v1`, JSON unless noted, and a bearer JWT on everything not
marked public. § numbers refer to the SBP API Documentation v1.0.

| Method | Path | Access | Notes |
|---|---|---|---|
| `POST` | `/auth/register` | public | Create a customer account (§4.1) |
| `POST` | `/auth/login` | public | Email or phone plus password; returns both tokens (§4.2) |
| `POST` | `/auth/oauth/google` | public | Google authorization code in, tokens out (§4.3) |
| `POST` | `/auth/refresh` | public | Swap a refresh token for a new pair |
| `POST` | `/auth/logout` | signed in | Revoke the current tokens (§4.4) |
| `GET` | `/reference/provinces` | public | Province dropdown (§5.1) |
| `GET` | `/reference/repair-services` | signed in | Service catalogue; `activeOnly=false` includes retired services (§5.2) |
| `GET` | `/reference/rating-categories` | signed in | Integrity, Quality, Friendliness (§5.3) |
| `GET`, `PUT` | `/customers/me` | customer | Read or update the signed-in customer's profile (§6.1, §6.2) |
| `PUT` | `/customers/me/address` | customer | First address for an account created through Google |
| `POST` | `/bookings` | customer | Create a booking (§7.1) |
| `GET` | `/bookings` | customer | `q`, `serviceId` (repeatable), `createdFrom`, `createdTo`, `group=ACTIVE\|PAST`, `sort=createdAt,asc\|desc`, `page` (from 0), `size` (default 20, max 100) (§7.2) |
| `GET` | `/bookings/{bookingRef}` | customer | One booking, with schedule and bill when present (§7.3) |
| `POST` | `/bookings/{bookingRef}/cancellation` | customer | Multipart: `reason` and up to three `evidence` files (§7.4) |
| `GET` | `/bookings/{bookingRef}/rating` | customer | The rating on a booking (§8.2) |
| `POST` | `/ratings` | customer | Multipart: `bookingRef`, `overallScore`, `reviewComment`, `subcategoryScores` (a JSON array), `evidence` (§8.1) |
| `GET` | `/ratings`, `/ratings/{ratingId}` | customer | The caller's ratings |
| `DELETE` | `/ratings/{ratingId}` | customer | Delete a rating (§8.3) |
| `GET` | `/notifications` | customer, admin | `unreadOnly`, `page`, `size` (§9.1) |
| `PATCH` | `/notifications/{notificationId}/read`, `/notifications/read-all` | customer, admin | Mark as read (§9.2) |
| STOMP | `/ws`, subscribe to `/user/queue/notifications` | JWT in `CONNECT` | Live notifications (§9.3) |
| `GET` | `/admin/customers` | admin | List customers (§10.1) |
| `PATCH` | `/admin/customers/{customerId}/status` | admin | `{"status": "ACTIVE"}` or `DEACTIVATED` (§10.3) |
| `GET`, `POST` | `/admin/staff` | admin | List and search (`q`), or create; creating also sends the welcome email (§10.4, §10.6) |
| `GET`, `PUT` | `/admin/staff/{staffId}` | admin | Read or update (§10.5, §10.7) |
| `POST` | `/integration/booking-events` | SMP only | Booking status change (§11.7) |
| `GET` | `/actuator/health`, `/actuator/info` (at the server root) | public | Health check and application info |

---

## Testing

**Backend.** From `Backend/`:

```powershell
.\mvnw.cmd test
```

```bash
./mvnw test
```

There are more than 330 JUnit tests, and most are unit tests that run anywhere. Three use the real
databases: `SbpApplicationTests` needs the two MongoDB URIs (from `Backend/.env` or the shell) and
fails without them, while the STOMP round trip and the booking-event flow test run only when
`MONGO_URI_SOUTH` and `MONGO_URI_NORTH` are set in the environment, and skip otherwise. Test
logging is quiet by default; add `"-Dsbp.test.log.level=DEBUG"` to see everything (the quotes stop
PowerShell from splitting the argument).

**MockSMP.** `.\mvnw.cmd test` (or `./mvnw test`) from `MockSMP/`.

**Frontend.** From `Frontend/`, `pnpm lint` and `pnpm build` must both pass. There is no unit-test
runner.

**End to end.**

- The two [Postman collections](docs/postman) take a booking through confirmation, scheduling, work
  and completion, against a mocked or a live SMP.
- `Backend/scripts/seed-mock-staff.ps1` creates a south receptionist and a north technician through
  the real endpoint and checks each letter.
- `Backend/scripts/accept-staff-welcome-email.ps1` checks the welcome email's acceptance criteria
  and reports PASS, FAIL or SKIP for each.

Run the scripts from `Backend/` with the backend up:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\seed-mock-staff.ps1 -Verify
```

[docs/demo-staff-welcome-email.md](docs/demo-staff-welcome-email.md) explains their options and
how to set up the backend for them.

---

## Deployment

### Containers

- `Backend/Dockerfile` builds the jar with the Maven wrapper on a Temurin 17 JDK, then runs it on a
  Temurin 17 JRE as the unprivileged `spring` user, with `/app/data` for mail and uploads.
- `Frontend/Dockerfile` builds with Node 22 and pnpm, then serves `dist/` from nginx 1.27, falling
  back to `index.html` for client-side routes. `VITE_*` values are build arguments, fixed at build
  time.

### Render and Vercel

- **API.** [render.yaml](render.yaml) describes the Docker web service `fixinh-sbp-api`: Singapore
  region, free plan, manual deploys, health check `/actuator/health`, port 10000. It runs the base
  configuration (`SPRING_PROFILES_ACTIVE=production` has no profile file) with SMP and staff
  mocked, file email and local image storage. Set `MONGO_URI_NORTH`, `MONGO_URI_SOUTH` and your
  own `SMP_WEBHOOK_SECRET` in the Render dashboard; Render generates `JWT_SECRET` and provides
  HTTPS.
- **Web app.** Deployed on Vercel from `Frontend/`, where [vercel.json](Frontend/vercel.json)
  sends every path to `index.html`. Set `VITE_API_URL` and `VITE_WS_URL` to the Render service,
  plus the Google variables if used. The API's CORS list admits the production and preview
  domains.

Live at <https://fixinh-service-booking-portal.vercel.app>, with the API at
<https://fixinh-sbp-api.onrender.com>. Render's free plan sleeps when idle, so the first request
after a quiet spell is slow.
[sbp-booking-status-flow.postman_collection.json](docs/postman/sbp-booking-status-flow.postman_collection.json)
defaults to this API.

### More than one machine

The `integration` profile turns HTTPS on and calls the live SMP. No keystore is committed, so
generate a self-signed one first (A.2.5), outside the repository, and include the API machine's
hostname or IP address in `SAN`:

```
keytool -genkeypair -alias sbp -keyalg RSA -keysize 2048 -validity 365 -storetype PKCS12 -keystore sbp-keystore.p12 -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1"
```

Then start the backend with:

```
SPRING_PROFILES_ACTIVE=integration
SSL_KEYSTORE=file:/absolute/path/to/sbp-keystore.p12
SSL_KEYSTORE_PASSWORD=<the password you chose>
SMP_BASE_URL=<SMT's /api/v1 URL>
SMP_SERVICE_TOKEN=<SMT's service token>
SMP_WEBHOOK_SECRET=<the secret SMT sends>
CORS_ALLOWED_ORIGINS=<the web app's origin>
MAIL_HOST=<SMTP host>
MAIL_PORT=<SMTP port>
MAIL_USERNAME=<SMTP user>
MAIL_PASSWORD=<SMTP password>
```

`integration` includes the `smtp` profile, so the mail settings are required. Build the web app
with `VITE_API_URL=https://<api-host>:8443/api/v1` and `VITE_WS_URL=https://<api-host>:8443/ws`,
and open `https://<api-host>:8443/actuator/health` once in each browser to accept the
certificate.

---

## Documentation

| Document | What it covers |
|---|---|
| [docs/demo-staff-welcome-email.md](docs/demo-staff-welcome-email.md) | Four ways to demo the welcome email (file, Gmail, scripts, Docker) and troubleshooting |
| [docs/smp-staff-contract.md](docs/smp-staff-contract.md) | The staff API contract that MockSMP implements |
| [docs/smp-integration-scope.md](docs/smp-integration-scope.md) | Inbound events versus outbound delivery to SMP, and what safe retries of booking creation would need from SMP |
| [docs/postman/](docs/postman) | Booking status walkthroughs: mocked SMP against a local or deployed API, and the live SMT inside Docker |
| [docs/demo-images/](docs/demo-images) | Sample photos for cancellation and rating evidence |
| [MockSMP/README.md](MockSMP/README.md) | Running MockSMP and pointing SBP at it |

The SRS, the SBP API Documentation v1.0 and the Milestone 1 system design report are kept outside
this repository. Code comments refer to their section and requirement numbers.

---

## Known limitations

- **SMP-facing read APIs are missing.** Customer lookup, ratings and rating statistics
  (API Documentation §11.2–§11.6) are not implemented; `/integration/booking-events` is the only
  integration endpoint, and `RatingStatsDto` is a placeholder. Req 3.4.S4, 3.4.U1–U2, 4.S1 and
  4.U1 therefore remain open.
- **HTTPS is configuration only.** No keystore is committed, so `dev` and the Docker stack serve
  plain HTTP, the `integration` profile will not start until one is generated, and the Vite dev
  server has no HTTPS setup.
- **Moving between regions.** A customer who signed up with a password cannot change their address
  to a province in the other region from the profile page; `CredentialRegionService` only moves
  Google-linked credentials, so the change is refused with `400 VALIDATION_FAILED`. Changes within
  a region work. When a Google-linked account does move, its existing bookings stay in the old
  store and drop out of **My bookings**, although each still opens by its reference.
- **Live SMP mode.** Cancellation is still decided inside SBP (`LocalCancellationRule`), because
  SMT publishes no cancellation endpoint, so SMT's copy of a cancelled booking stays at Created.
  Booking creation calls SMT directly, with no outbox to retry it. Technician details arrive only
  through booking events. The backend will not start while SMT is unreachable, because the
  catalogue is synced at startup.
- **Unfinished auth controls.** **Forgot?** on the sign-in page and **Continue with Google** on
  the sign-up page do nothing yet. Google sign-in works from the sign-in page and creates the
  account on first use.
- **Endpoints from the API document that are not implemented:** `GET /auth/me` (§4.5) and
  `GET /admin/customers/{customerId}` (§10.2). Admin customer search filters the full list in the
  browser.
- **Local image mode.** Stored photo links are `file://` paths that a browser will not open, and
  the Render deployment uses this mode. Use `IMAGE_STORAGE_MODE=cloudinary` to view evidence.
- **One API instance.** The real-time channel uses Spring's in-memory broker, so a second instance
  would need sticky sessions or a shared broker. An open socket is not re-checked when its token is
  revoked; it re-authenticates on its next connection.
- **Token refresh across tabs.** Each open tab runs its own refresh timer against the same stored
  refresh token. When two tabs refresh at the same moment, the backend's reuse check treats the
  second request as a stolen token and ends the session.
- **No rate limiting.** `RATE_LIMITED` is in the error catalogue, but no limiter is wired in.
- **Uneven module boundaries.** The admin and customer controllers call their module's `internal`
  interface; `AdminExternalService` and `ReferenceInternalService` are empty;
  `CustomerServiceImpl` implements both customer interfaces; and a few cross-module calls return
  documents (`Customer`, `UserCredential`) rather than DTOs.

---

## Contributing

- Branch from `main` and merge back through a pull request.
- Write commit messages as Conventional Commits, `type(scope): summary`, for example
  `feat(booking): filter by creation date`.
- Before opening a pull request, run the backend tests, `pnpm lint` and `pnpm build`.
- A new backend endpoint is closed by default (`anyRequest().authenticated()`). Open it
  deliberately in `SecurityConfig` if it must be public, and add its path to
  `Frontend/src/config/api.js`. A public call from the web app passes `{ auth: false }`: the JWT
  filter runs on every request, so a stale token would still make a public request fail.
- Keep secrets in `.env` files, which git ignores, or in the environment, never in
  `application.yml`.
