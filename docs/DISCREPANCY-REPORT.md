# Notification module — discrepancy report

**Branch:** `dev-notification-model-store-and-history-apis`
**Scope:** Req 3.3.U2 and 3.1.S5 — API doc §9.1, §9.2, §10.6, Appendix A; Milestone 1 CDM
**Raised by:** steps 1–12 of the notification module

Everything here was found while building the notification module and verified
against the tree on this branch. Nothing is speculative; each entry says how it was
observed and what it costs if left alone.

Items are grouped by who has to act, because that is the only grouping that
determines whether anything happens. **Nothing in section B is ours to change on
this branch.**

> Seventeen items. Fifteen were established across two handoffs and the sessions
> that followed; A2 and A3 are new here. One of the fifteen has since been fixed
> and is recorded as closed rather than deleted, so a reader of the earlier handoff
> can see what became of it. Merge against `NOTIFICATION-HANDOFF.md` (steps 1–7)
> before circulating — that document was not available when this was compiled, and
> it may list items this does not.

---

## A. Documentation — for the CDM and API doc owners

### A1. `bookingRef` is missing from the CDM · **the item `task.md` mandates**

`task.md` states it outright: *"`bookingRef` appears in the §9.1 response but is
not listed in the CDM — add it to the model and raise the discrepancy for the CDM
to be updated."*

The CDM's SBP-side `Notification` entity lists ten attributes: recipient type,
recipient identifier, notification type, delivery channel, message, created
date-time, read status, delivery status, sent date-time, attempt count.
`bookingRef` is not among them, but §9.1 publishes it and the frontend routes on
it.

- **Fix:** additive. `Booking Projection --triggers--> Notification` is already
  drawn, so the relationship needs no change — only the attribute.
- **Status:** on the model and in the DTO already. The CDM is the thing that is out
  of date, not the code.
- **Owner:** whoever holds the Milestone 1 CDM.

### A2. §9.2's `read-all` endpoint is documented only in a note

`PATCH /notifications/read-all` appears as a bullet under §9.2's Notes rather than
as an endpoint entry with its own method, path, auth and response table. It is a
real endpoint, it is implemented, and the frontend calls it. Every other endpoint
in the document gets a full entry.

- **Cost:** a reader scanning §3.1's endpoint index does not find it. It is absent
  from that index too.
- **Fix:** promote to §9.3 and renumber the WebSocket section, or add a short entry
  under §9.2.

### A3. §4.5 `GET /auth/me` is documented but not implemented

`AuthController` serves `register`, `login` and `oauth/google` only. §4.5 describes
`GET /auth/me`.

- **Cost:** minor now, but it is the natural way to recover a caller's identity, and
  its absence is why the acceptance script reads customer records straight out of
  Mongo.
- **Fix:** implement it, or mark it as planned in §1.5's change log.

---

## B. Application code — outside this branch

### B1. `/error` is not permitted in `SecurityConfig`, so it masks real failures

**Reproduced live, twice — it cost a debugging round in step 6 and again in step 8.**

`JwtAuthenticationFilter extends OncePerRequestFilter`, which skips ERROR
dispatches by default and does not override `shouldNotFilterErrorDispatch()`. The
SecurityContext is therefore empty on the forward, and `/error` is not in the
permit list. The result: a 404 with a perfectly valid bearer token attached comes
back as **401 `AUTH_TOKEN_MISSING`**, with `"path":"/error"` in the body.

Easiest way to see it: an empty id builds `/notifications//read`, which matches no
handler.

- **Cost:** any unmatched path lies about why it failed, and the lie points at
  authentication. Two sessions lost time to it.
- **Two candidate fixes:** `.requestMatchers("/error").permitAll()`, or override
  `shouldNotFilterErrorDispatch()` to return false.
- **Not ours to change on this branch.** `sbp-test.ps1`'s `Set-NotificationRead`
  refuses an empty id so the harness cannot trigger it, which is a workaround and
  not a fix.

### B2. An unrecognised `notificationType` takes down the whole list

`NotificationType` is a plain enum with no `@JsonCreator` and no unknown-value
fallback. One document carrying a type this build does not know — written by a
newer deployment, or by hand — fails Mongo-to-Java conversion and **500s
`GET /notifications` for that recipient entirely**, not just for the offending row.

The panel has a fallback presentation for an unknown type. It can never be reached,
because the response never arrives.

- **Cost:** none today, because one service writes notifications and it is this
  one. It becomes real the moment 5.2 adds a second writer, or the first time the
  two portals are on different versions.
- **Fix:** a read converter that maps unknown values to a sentinel, or filtering
  at the repository. Worth a decision before 5.2 lands, not a fix for this branch.

### B3. `adminEndpoints.js` hardcodes its paths

`config/api.js` states the rule in its own doc comment: paths live in that file,
and the endpoint module owns the method and body. Six calls in
`adminEndpoints.js` build `/admin/...` inline instead.

Notifications follow the rule, so **the codebase now disagrees with itself** —
which is worse than either convention applied consistently.

- **Fix:** move the six paths into `API_ENDPOINTS.ADMIN`.
- **Flagged rather than silently refactored:** it is another branch's file.

### B4. `bookingEndpoints.js` is 0 bytes

An empty committed stub. Harmless, but `MyBookingsPage` still renders
`mockBookings.js`, so the booking module has no service layer at all.

---

## C. Ours, and now closed

### C1. Dead code: `NotificationServiceImpl.deliveryStatusOnArrival` · **fixed, step 10.5**

Became unreachable when review fix R1 hardcoded `IN_APP`/`SENT` in `raise()`.
Nothing called it.

Deleted. Noted here rather than silently dropped because **step 15 (§10.6) is the
step that un-hardcodes the channel**, and whoever picks that up should know the
shape existed and why it went — dead code that no caller reaches is not
documentation, but its absence should not be a surprise either.

### C2. Indentation drift · **fixed, step 10.5**

Five sites, not the two the earlier handoff recorded. All were member-level
declarations sitting at two tabs instead of one:

| File | Line | What |
|---|---|---|
| `NotificationServiceImpl.java` | 85 | `// --- Raising one` section header |
| `NotificationServiceImpl.java` | 87 | `@Override` above `raise()` |
| `NotificationServiceImpl.java` | 131 | `// --- The recipient's own history` header |
| `NotificationRegionResolver.java` | 76 | javadoc opener above `regionForCustomer` |
| `NotificationRetryScheduler.java` | 155 | `private void giveUp(...)` |

The last three were not in the earlier list. Whitespace only.

### C3. `sbp-test.ps1` was documented as gitignored and was not · **fixed**

`Backend/.gitignore:36` now has `sbp-test.ps1`. Closed. Recorded rather than
deleted so a reader of the earlier handoff can see what became of it.

---

## D. Deliberate, and recorded so nobody "fixes" them

### D1. `unreadCount()` on the external interface has no controller caller

Implemented and tested, called by nothing. This is intentional: the badge reads its
count off the §9.1 list body, so one request feeds both.

- **Do not delete it as dead code** — it exists for 5.1.
- **Do not expose it as `GET /notifications/unread-count`** — that would be a second
  request for a number the first one already returned.

### D2. The panel's confirmed-booking icon differs from `StatusBadge`

Three sources disagreed, so this is a decision rather than an oversight:

| Source | Confirmed icon |
|---|---|
| Mockup (`Fixinh Portal.dc.html`, view-model line 1849) | `task_alt` |
| `StatusBadge.jsx` | `verified` |
| Handoff-2's token mapping table | `check_circle` — matches **neither** |

The panel follows the mockup. A notification *about* a booking and the booking's
own status badge are different objects in different surfaces, and the mockup is the
visual source of truth for the panel. Handoff-2's table is simply wrong on this row.

### D3. `NotificationBroadcaster` is an empty class

Not annotated, no interface. 5.1's landing spot, untouched by steps 1–12.

---

## E. Tooling — costs time, no product impact

### E1. `$script:RegionByProvince` in `sbp-test.ps1` silently mis-shards

`New-TestCustomer` reads `$script:RegionByProvince`. In a function defined by
**dot-sourcing** a plain script (not a module), `$script:` resolves at *call* time
against whichever script is running — not against the file the function came from.
From the prompt it finds the global copy and works. From inside another executed
script it finds nothing and returns null:

```
Cannot index into a null array.
At sbp-test.ps1:140 char:5
```

Every account then falls back to SOUTH. Registration itself is unaffected —
`provinceCode` is sent before the lookup — but `Add-SeedNotifications` and
`Remove-SeedNotifications` both pick a shard from `$Customer.Region`, so **a
northern customer's seed goes into the southern store**, silently.

`$Base` is immune only because the same file reads it as plain `$Base`, with no
modifier, so ordinary lookup walks up to global. The two are declared identically
and read differently.

- **Fix:** two characters. Read it as `$RegionByProvince`, the way `$Base` already is.

### E2. Spring stores `Customer`'s `String @Id` as a BSON ObjectId

`@Id private String id` on a 24-hex value is converted to `ObjectId` on write. A
mongosh filter of `{_id:'68a…'}` compares a string against an ObjectId, matches
nothing, and **returns 0 rather than erroring** — a count of zero looks like a real
answer, which is what makes this expensive.

Two consequences, both hit during step 11:

- Shard-placement assertions read 0 in both stores and looked like a failed
  criterion. They were a broken query.
- Cleanup deleted customers by `_id` and so deleted none of them, silently, for
  several runs.

A bare `print(c._id)` compounds it: mongosh renders the literal text
`ObjectId('68a…')`, and quoting that for a `$in` list nests single quotes and
fails the whole script with *"Identifier directly after number"*.

- **Rules:** query `customers` by `email` from mongosh, never `_id`. Read ids with
  `c._id.toString()`. `notifications._id` has the same problem;
  `recipientIdentifier` and `shardDirectory._id` (`CUSTOMER:<id>`, not valid hex)
  are ordinary strings and are safe.

### E3. Downloaded `.ps1` files will not load

Mark of the Web: under `RemoteSigned`, a downloaded script must be signed even
though a locally-typed one need not be. A script pasted into a new file runs; the
same script downloaded does not, with a *"not digitally signed"* error that reads
as a problem with the script.

- **Fix:** `Unblock-File .\<script>.ps1`. Never change machine or user execution
  policy for it; `Set-ExecutionPolicy -Scope Process Bypass` if more is needed.

### E4. No frontend test runner

`pnpm lint` plus a scripted browser walkthrough is the whole gate. Step 10 shipped
open/close behaviour, keyboard dismissal and two optimistic state paths with no way
to re-run assertions on any of them.

- **Decision taken:** deferred. Adding vitest and react-testing-library is a
  dependency change and belongs in its own step, not folded into a feature step.
- **Standing cost:** every future change to `useNotifications` or the panel is
  verified by hand.

---

## Summary

| # | Item | Owner | State |
|---|---|---|---|
| A1 | `bookingRef` missing from the CDM | CDM owner | **Open — mandated by `task.md`** |
| A2 | `read-all` documented only in a note | API doc owner | Open |
| A3 | `GET /auth/me` documented, not implemented | Auth branch | Open |
| B1 | `/error` masks failures as 401 | Security/config owner | **Open — cost two sessions** |
| B2 | Unknown enum value 500s the list | Backend, before 5.2 | Open |
| B3 | `adminEndpoints.js` hardcodes paths | Admin branch | Open |
| B4 | `bookingEndpoints.js` is empty | Booking branch | Open |
| C1 | Dead `deliveryStatusOnArrival` | This branch | **Fixed, 10.5** |
| C2 | Indentation drift, five sites | This branch | **Fixed, 10.5** |
| C3 | `sbp-test.ps1` not gitignored | This branch | **Fixed** |
| D1 | `unreadCount()` has no caller | — | Deliberate, for 5.1 |
| D2 | Panel vs `StatusBadge` icon | — | Deliberate |
| D3 | `NotificationBroadcaster` is empty | — | Deliberate, for 5.1 |
| E1 | `$script:` scoping mis-shards seeds | Tooling | Open, two characters |
| E2 | `String @Id` becomes an ObjectId | Tooling | Documented |
| E3 | Mark of the Web blocks `.ps1` | Tooling | Documented |
| E4 | No frontend test runner | Team decision | Deferred |
