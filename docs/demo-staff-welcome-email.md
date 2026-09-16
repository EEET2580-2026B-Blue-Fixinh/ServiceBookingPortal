# Demoing the staff welcome email (Req 3.1.S5)

When an administrator creates a receptionist or technician, the new staff member receives a
welcome email with their initial password and a sign-in link (API Doc §10.6).

## Pick a path

| I want to… | Go to | Setup |
|---|---|---|
| See the letter quickly, send nothing | [A. File mode](#a-file-mode--the-letter-as-a-file) | None |
| Receive the letter in my own Gmail | [B. Real inbox](#b-real-inbox--gmail) | A Gmail app password |
| Prove the acceptance criteria | [C. Scripts](#c-scripts--seed-and-acceptance) | `mongosh` (optional) |
| Run everything in Docker | [D. Docker Compose](#d-docker-compose) | Docker |

Staff stay **mocked** in every path (`SMP_STAFF_MODE=mock`, the default). Email delivery is a
separate switch, so a mocked staff member can still receive a real email.

All commands are PowerShell, run from `Backend` unless a step says otherwise.

## How the email switch works

| Profiles | Delivery | Where the letter goes |
|---|---|---|
| `dev` (default) | File | `Backend\target\mail\*.eml`. Nothing leaves the machine |
| `dev,smtp` | SMTP | A real inbox, through `MAIL_*` |

The `dev` profile always forces file mode, so setting `EMAIL_MODE=smtp` on its own does nothing
useful. To send for real, add the `smtp` profile.

The backend reads `Backend\.env` at startup. A variable set in the terminal with `$env:` wins over
the same name in `.env`.

## Before you start

1. **Mongo.** `MONGO_URI_SOUTH` and `MONGO_URI_NORTH` are set in `Backend\.env`.
2. **An administrator account exists** in that database. The login page's **Demo accounts** box and
   the scripts use `VITE_DEMO_ADMIN_EMAIL` / `VITE_DEMO_ADMIN_PASSWORD` from `Frontend\.env`. If
   that account is not in your database, sign in with one that is, or pass `-AdminEmail` and
   `-AdminPassword` to the scripts.
3. **Only one backend on port 8443.** Check with:

   ```powershell
   Get-NetTCPConnection -LocalPort 8443 -State Listen
   ```

4. **Only one backend per shared cluster** for sections B and C. Every backend's retry sweep reads
   the same rows, so a teammate's backend can deliver your letter, or send their pending emails
   for real.

---

## A. File mode: the letter as a file

**1. Start the backend.**

```powershell
.\mvnw.cmd clean spring-boot:run
```

Wait for both lines:

```
Email delivery: FILE mode. Nothing is sent - messages are written to ...\Backend\target\mail
SMP staff: MOCK mode. 14 staff loaded from mocks/smp/staff.json; ...
```

**2. Start the frontend** in a second terminal:

```powershell
cd ..\Frontend
pnpm dev
```

Open `http://localhost:3000` and sign in as an administrator.

**3. Create a staff member.** Go to **Staff & technicians** → **New staff account**, fill in the
form, and choose **Create account**. The page shows *Staff account created. Welcome email sent.*

**4. Open the letter.**

```powershell
Get-ChildItem target\mail\*.eml | Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 | Get-Content -Encoding UTF8
```

You can also double-click the `.eml` file to open it in Outlook or Thunderbird. The letter shows the
staff member's name, role, sign-in email, initial password, and sign-in link.

<details>
<summary><b>Optional: demo a failed send and its retry</b></summary>

Start the backend with fast retries:

```powershell
$env:EMAIL_RETRY_BASE_DELAY = 'PT20S'; $env:EMAIL_RETRY_SWEEP_INTERVAL = 'PT10S'
.\mvnw.cmd clean spring-boot:run
```

Block the outbox by putting a file where the folder should be:

```powershell
if (Test-Path target\mail) { Move-Item target\mail target\mail.hold }
New-Item -ItemType File target\mail | Out-Null
```

Create another staff member. The page shows a **yellow warning**: *Account created. Welcome email
hasn't gone out yet; we'll keep retrying.* The account exists; only the email failed (the §10.6
502).

Unblock the outbox **right away**. Each retry that fails doubles the wait before the next one.

```powershell
Remove-Item target\mail
if (Test-Path target\mail.hold) { Move-Item target\mail.hold target\mail }
```

Within about a minute, the retry sweep writes the letter.

</details>

---

## B. Real inbox: Gmail

**1. Get a Gmail app password.** You only do this once.

1. Use a **personal** Gmail account. School and Workspace accounts often block app passwords.
2. Turn on **2-Step Verification** in Google Account → **Security**.
3. Open <https://myaccount.google.com/apppasswords>, create one named `SBP demo`, and copy the 16
   characters **without spaces**.

**2. Add the mail settings to `Backend\.env`.** This file is gitignored: never commit it.

```properties
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=you@gmail.com
MAIL_PASSWORD=abcdabcdabcdabcd
EMAIL_FROM_ADDRESS=you@gmail.com
EMAIL_RETRY_ENABLED=false
```

`EMAIL_RETRY_ENABLED=false` makes the backend send only the letters you trigger, and never old
failed letters from a shared database.

**3. Start the backend with the `smtp` profile.**

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev,smtp'
.\mvnw.cmd clean spring-boot:run
```

Wait for both lines:

```
Email delivery: SMTP mode. Messages are sent for real, from you@gmail.com
SMP staff: MOCK mode. ...
```

**4. Send yourself a letter.** Choose one:

- **From the admin page:** create a staff member with your own address. The mock refuses a repeated
  email with 409, so give each account its own plus-address. All of them land in `you@gmail.com`:
  - `you+rec1@gmail.com`
  - `you+tech1@gmail.com`
- **From the seed script:** this creates one receptionist and one technician, both addressed to
  your inbox.

  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\seed-mock-staff.ps1 -Mailbox you@gmail.com
  ```

**5. Check your inbox, including Spam and Promotions.** Search for "Welcome to Fixinh". Mark the
first letter as *Not spam* so later letters arrive normally.

**6. Switch back to file mode** when you are done:

1. Close the terminal, so `SPRING_PROFILES_ACTIVE` goes away.
2. Restart the backend normally. It is back in file mode.
3. To fully tear down, remove the `MAIL_*` lines from `.env` and revoke the app password.

---

## C. Scripts: seed and acceptance

Both scripts need a backend in **file mode** (section A), unless you pass `-Mailbox` to the seed
script. Before creating anything, each script checks `/actuator/health`. If the backend sends real
email:

- the seed script refuses to run without `-Mailbox`, so its default `@fixinh.vn` addresses are never
  really sent to;
- the acceptance script refuses to run at all.

The `-Verify` switch also reads MongoDB. It needs `mongosh.exe` from the **Windows MSI** on `PATH`,
because the npm shim breaks Atlas URIs at `&`. Without it, the script prints the query for you to
run by hand.

### Seed two staff members

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\seed-mock-staff.ps1 -Verify
```

This creates a receptionist in Ho Chi Minh City (the south shard) and a technician in Ha Noi (the
north shard). Expect, for each one:

- `201 Created`, a `REC-xxxxxx` or `TEC-xxxxxx` id, and `notificationSent True`;
- five `ok` lines checking the letter;
- with `-Verify`: a Notification row (`STAFF`, `STAFF_ACCOUNT_CREATED`, `EMAIL`, `SENT`,
  `attemptCount: 1`, the right `homeRegion`) and a matching `STAFF:` Shard Directory entry.

### Run the five acceptance criteria

**1. Start the backend** with a log file and fast retries:

```powershell
$env:LOGGING_FILE_NAME = 'target\sbp.log'
$env:EMAIL_RETRY_BASE_DELAY = 'PT20S'; $env:EMAIL_RETRY_SWEEP_INTERVAL = 'PT10S'
.\mvnw.cmd clean spring-boot:run
```

**2. Run the script** in another terminal:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\accept-staff-welcome-email.ps1 -Verify
$LASTEXITCODE    # 0 means every criterion passed
```

After about 40 seconds, it prints **PASS** for:

1. One email per creation.
2. Nothing sent externally on `dev`.
3. A 502 on a provider failure, with the account kept and the email retried.
4. The Notification row.
5. No password in the log.

Without `-Verify` or the log file, criteria 4 and 5 show SKIP. The script always restores the
outbox, even if you press Ctrl+C.

---

## D. Docker Compose

`docker compose up --build` runs the backend in file mode, against **local** MongoDB containers
rather than Atlas.

- **The admin account.** The local databases start empty, so a Demo account from Atlas does not
  exist there. Create an administrator in `mongo-south` first.
- **Reading letters.** Letters are written inside the container, not to `Backend\target\mail`:

  ```powershell
  docker exec fixinh-local-sbp-api-1 sh -c 'ls -t /app/data/mail | head -1'
  docker exec fixinh-local-sbp-api-1 sh -c 'cat "/app/data/mail/$(ls -t /app/data/mail | head -1)"'
  ```

- **Sending real email.** Compose already loads `Backend\.env`, so the `MAIL_*` lines from
  section B are picked up. The only other change is in `docker-compose.yml`, under `sbp-api` →
  `environment`:

  ```yaml
  SPRING_PROFILES_ACTIVE: dev,smtp
  ```

  Then rebuild the API with `docker compose up -d --build sbp-api`. Do not commit that change.

---

## Troubleshooting

| What you see | Cause and fix |
|---|---|
| `Port 8443 was already in use` | Another backend is running, often from VS Code. Find it with `Get-NetTCPConnection -LocalPort 8443 -State Listen` and stop that process. |
| `Could not resolve placeholder 'MAIL_…'` | The `smtp` profile is on, but a `MAIL_*` variable is missing. The error names it; add it to `Backend\.env`. |
| `mode is 'smtp' but no mail sender exists` | The mode was forced to `smtp` without the profile. Use `SPRING_PROFILES_ACTIVE=dev,smtp` instead. |
| Startup still says `FILE mode` after adding `MAIL_*` | The `smtp` profile is not active. Set `$env:SPRING_PROFILES_ACTIVE = 'dev,smtp'` in the **same** terminal, then restart. |
| Yellow "hasn't gone out yet" warning, log shows `535 … Username and Password not accepted` | Wrong app password, spaces left in it, or 2-Step Verification is off. |
| Log shows `530 … Must issue a STARTTLS command first` | Mail was configured by hand instead of through the `smtp` profile. Use the profile. |
| `notificationSent True`, but nothing in the inbox | Gmail accepted it. Check Spam and Promotions, and search for "Welcome to Fixinh". |
| `409` when creating staff | That email is already used in the mock. Use a new plus-address. |
| `Sign-in failed: HTTP 401` | That administrator is not in this database. Pass `-AdminEmail` and `-AdminPassword`. |
| A script says `This backend sends real email` | The backend has the `smtp` profile. Add `-Mailbox you@gmail.com` for the seed script, or restart in file mode for the acceptance script. |
| `-Verify` prints a query instead of rows | `MONGO_URI_*` is not visible to that terminal, or `mongosh` is missing or is the npm shim. Paste the printed query into mongosh. |
| Acceptance criterion 3 times out | The backend lacks the fast-retry settings, another backend swept the letter, or the outbox is elsewhere. Pass `-OutboxDir` with the path from the `Email delivery: FILE mode` line. |
| The letter still shows `Role: TECHNICIAN` or `Sign in:` | The backend predates the latest pull. Restart with `clean spring-boot:run`. |
| `target\mail is a file, not the outbox directory` | A failure demo was interrupted. Delete the file; the folder comes back on the next send. |
