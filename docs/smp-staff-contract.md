# SMP staff API, as SBP consumes it

SMT's service covers booking creation and booking status changes; everything else, staff
included, is mocked for this release. This page is the contract any stand-in has to meet — a
mock server, or SMT's own API should one arrive — for the administrator's staff screens and the
staff welcome email (Req 3.1.S5) to work against it.

It is derived from API Documentation §12.7–12.8 and from the types SBP already exchanges
(`Backend/src/main/java/com/fixinh/sbp/common/smp/dto`). Where the two disagree, the code is
what runs.

## Switching SBP over

| Setting | Effect |
|---|---|
| `SMP_STAFF_MODE=mock` (default) | `MockSmpStaffClient`: seeded from `Backend/src/main/resources/mocks/smp/staff.json`, created staff held in memory until the backend stops |
| `SMP_STAFF_MODE=live` | `LiveSmpStaffClient` against `SMP_STAFF_BASE_URL`, normally MockSMP (see `MockSMP/README.md`). Never `SMP_BASE_URL`: SMT's service has no staff API |
| `SMP_MODE` | Bookings only. It no longer moves staff, so live bookings and mocked staff can run together |

Every call carries `Authorization: Bearer <SMP_STAFF_SERVICE_TOKEN>`, a separate credential
from the booking client's `SMP_SERVICE_TOKEN`.

## Endpoints

| Method | Path | Backs | Success |
|---|---|---|---|
| `GET` | `{SMP}/staff?q=` | Admin list and search (§10.4) | 200, a list of staff |
| `GET` | `{SMP}/staff/{staffId}` | Admin detail (§10.5) | 200, one staff member |
| `POST` | `{SMP}/staff` | Admin create (§10.6) | 201, the new staff member **with `initialPassword`** |
| `PUT` | `{SMP}/staff/{staffId}` | Admin update (§10.7) | 200, the updated staff member, `initialPassword` null |

- `q` matches name, staff id or email: case-insensitive and partial (§12.7). No `q` means
  everyone. §12.7 also defines `role`, `page` and `size`; SBP does not send them yet.
- §12 lists no single-staff `GET`, but SBP's `SmpStaffClient.findStaff` needs one, so a
  stand-in should provide it.
- The list's wrapper is not fixed by the API document. `{ "items": [ … ] }` is recommended: it
  is what SBP's own list endpoints return and what §12's technician lookup uses.

## Shapes

A staff member, as SBP reads it (`SmpStaffDto`):

```json
{
  "staffId": "TEC-7F3A2C",
  "fullName": "Nguyễn Văn Hùng",
  "email": "hung.nguyen@fixinh.vn",
  "phoneNumber": "0901112223",
  "staffRole": "TECHNICIAN",
  "employmentStatus": "ACTIVE",
  "address": {
    "streetNumber": "25",
    "streetName": "Tràng Tiền",
    "ward": "Hoàn Kiếm",
    "provinceCode": "HN"
  },
  "branchProvinceCode": "HN",
  "initialPassword": null
}
```

- **Create request** (`SmpStaffCreateRequest`): `fullName`, `email`, `phoneNumber`,
  `staffRole`, `address`, `branchProvinceCode`. SBP has already validated every field against
  Appendix C; SMP is expected to validate them again (§12.8).
- **Update request** (`SmpStaffUpdateRequest`): `fullName`, `phoneNumber`, `address`,
  `employmentStatus` — null leaves the status unchanged. Email and role cannot be changed.
- **Enumerations** (Appendix A): `staffRole` is `RECEPTIONIST` or `TECHNICIAN`;
  `employmentStatus` is `ACTIVE` or `INACTIVE`.

## What the welcome email depends on

SBP sends the welcome email and SMP sets the initial password (§12.8, settled for this
release). Three things in the create response make that work:

- **`initialPassword`, on the create response and nowhere else.** SBP puts it into the letter
  and nowhere else: never into its own API responses, never into a log, and out of storage the
  moment the letter is accepted. Return `null` on every other response. A create response
  without it yields a letter with no usable password.
- **`branchProvinceCode`.** It decides which regional store holds the staff member's
  notification. If it is missing SBP falls back to the code the administrator sent, so a
  stand-in may omit it — but must never return a code outside `GET /reference/provinces`.
- **A `staffId` that is never reused.** SBP's Shard Directory remembers each id's region
  permanently, so an id handed to a second person files that person's notifications in the
  first one's store. The mock uses the role prefix and six random hex digits for this reason.

## Errors SBP tells apart

| Situation | SMP answers | The administrator sees |
|---|---|---|
| The email already belongs to a staff member | `409` | `409 EMAIL_ALREADY_REGISTERED` |
| Unknown `staffId` on update or detail | `404` | `404 STAFF_NOT_FOUND` |
| Unreachable, timed out, or any `5xx` | — | `503 SMP_UNAVAILABLE`, and no account was created (§10.6) |

The first two are what the mock does today. The third is §10.6's contract, and `LiveSmpStaffClient`
maps it the same way `LiveSmpBookingClient` does: any non-2xx other than 409/404 becomes
`SMP_UNAVAILABLE`, same as an unreachable host or a timeout (5 s to connect, 10 s to read).

§13.2 asks the staff mock for both roles plus a duplicate-email conflict on create, and the
fixture and mock client already cover both. A stand-in server should keep them, and should
mock its failures as carefully as its successes.

## Checking a stand-in end to end

Point SBP at the stand-in: `SMP_STAFF_MODE=live`, `SMP_STAFF_BASE_URL`,
`SMP_STAFF_SERVICE_TOKEN`. The scripts in `Backend/scripts` then exercise it through SBP,
unchanged:

- `seed-mock-staff.ps1` creates a receptionist and a technician and checks each welcome letter.
- `accept-staff-welcome-email.ps1` runs the issue's five acceptance criteria.
