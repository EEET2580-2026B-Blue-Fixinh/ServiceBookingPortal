# Mock SMP (staff)

A standalone stand-in for SMP's staff API, so the staff mock doesn't live inside the SBP
backend. Implements exactly the contract in
[`../docs/smp-staff-contract.md`](../docs/smp-staff-contract.md) — read that first for the
endpoint list, request/response shapes, and error codes.

Ported from SBP's `MockSmpStaffClient`: same in-memory store, same seed data
(`src/main/resources/staff.json`, a copy of `Backend/src/main/resources/mocks/smp/staff.json`
— if one changes to fix a data bug, check whether the other needs the same fix), same id
scheme, same duplicate-email and not-found behaviour. The only thing that changed is that it's
now reachable over HTTP instead of called in-process.

## Running it

```bash
./mvnw spring-boot:run
```

Listens on `:8090` by default (`MOCKSMP_PORT` to change it). Every path except
`/actuator/**` requires `Authorization: Bearer <token>`, checked against
`SMP_SERVICE_TOKEN` (defaults to `dev-only-shared-secret` — fine for local dev, never for
anything reachable outside your machine).

## Pointing SBP at it

```
SMP_STAFF_MODE=live
SMP_STAFF_BASE_URL=http://localhost:8090
SMP_STAFF_SERVICE_TOKEN=dev-only-shared-secret   # must match this app's SMP_SERVICE_TOKEN
```

`SMP_STAFF_BASE_URL`/`SMP_STAFF_SERVICE_TOKEN` are separate from `SMP_BASE_URL`/
`SMP_SERVICE_TOKEN`, which point bookings at Team SMT's real service. Staff needs its own
pair specifically so it doesn't end up pointed at SMT's service too — SMT publishes no staff
API, so that would just 404.

## Quick check

```bash
curl http://localhost:8090/actuator/health

curl -H "Authorization: Bearer dev-only-shared-secret" http://localhost:8090/staff
```

Once SBP is switched over, `Backend/scripts/seed-mock-staff.ps1` and
`accept-staff-welcome-email.ps1` exercise this service through SBP, unchanged.
