# Demoing the staff welcome email (Req 3.1.S5)

When an administrator creates a receptionist or technician, the new staff member receives a
welcome email carrying their initial password (API Doc §10.6). This page walks through showing
that three ways, from least to most setup:

1. **File mode** — the default on `dev`. No credentials, nothing leaves the machine; the letter
   lands in `target\mail` as a file you can open.
2. **Proof** — two scripts that create staff through the real endpoint and check what happened,
   including the issue's five acceptance criteria.
3. **A real inbox** — the same letter delivered to your own Gmail.

All commands are PowerShell, run from `Backend` unless a step says otherwise.

## Before you start

- **Mongo variables.** `MONGO_URI_SOUTH` and `MONGO_URI_NORTH`, as for any backend run. Every
  terminal that uses `-Verify` needs them too. Variables set with `$env:` exist only in the
  terminal that set them.
- **The demo administrator.** The scripts sign in with `VITE_DEMO_ADMIN_EMAIL` and
  `VITE_DEMO_ADMIN_PASSWORD` from `Frontend\.env`, or `Frontend\.env.example` when there is no
  `.env`. If that account does not exist on your cluster, pass `-AdminEmail` and
  `-AdminPassword` to either script.
- **`mongosh`, for `-Verify` only.** Install the Windows MSI so `mongosh.exe` is on `PATH`. The
  npm package installs a `.cmd` shim that runs through `cmd.exe`, which splits an Atlas URI at
  its `&`; the scripts detect the shim and print the query for you to run instead.
- **Running the scripts.** Use `powershell -ExecutionPolicy Bypass -File …` as shown, which
  needs no policy change. A script that arrived as a browser download rather than through git
  may also need `Unblock-File` first.
- **One backend per cluster.** Every running backend's retry sweep reads the same rows. For the
  acceptance run and the real-inbox demo, make sure no teammate's backend is up against the
  same clusters. Otherwise their sweep can deliver your retried letter to their outbox or, on
  `smtp`, send their outstanding emails for real.

## 1. File mode: the letter as a file

1. Start the backend:

   ```powershell
   .\mvnw.cmd clean spring-boot:run
   ```

   Two startup lines confirm the setup:

   ```
   Email delivery: FILE mode. Nothing is sent - messages are written to ...\Backend\target\mail
   SMP staff: MOCK mode. 14 staff loaded from mocks/smp/staff.json; ...
   ```

2. In a second terminal, start the frontend from `Frontend` with `pnpm dev`, and open
   `http://localhost:3000`. Sign in with the administrator from the login page's
   **Demo accounts** box.

3. Open **Staff & technicians** → **New staff account**, fill in the form, and choose
   **Create account**. The page answers *Staff account created. Welcome email sent.* On `dev`,
   "sent" means the file client accepted the letter, which is what `notificationSent: true`
   reports.

4. Open the newest letter:

   ```powershell
   Get-ChildItem target\mail\*.eml | Sort-Object LastWriteTime -Descending |
       Select-Object -First 1 | Get-Content -Encoding UTF8
   ```

   Double-clicking the `.eml` file also works: Outlook and Thunderbird open it as an email. It greets the staff member by name, then lists their role, the email they sign in
   with, their initial password, and the sign-in link.

5. *Optional: the failure path, live.* A file sitting where the outbox directory should be makes
   the next send fail as a provider outage would. For a quick recovery, start the backend with
   `EMAIL_RETRY_BASE_DELAY=PT20S` and `EMAIL_RETRY_SWEEP_INTERVAL=PT10S`, then:

   ```powershell
   if (Test-Path target\mail) { Move-Item target\mail target\mail.hold }
   New-Item -ItemType File target\mail | Out-Null
   ```

   Create another staff member. The page now shows a yellow warning, not an error: *Account
   created. Welcome email hasn't gone out yet; we'll keep retrying.* The account exists; only
   the email failed (the §10.6 502). Put the outbox back:

   ```powershell
   Remove-Item target\mail
   if (Test-Path target\mail.hold) { Move-Item target\mail.hold target\mail }
   ```

   Within a minute, the retry sweep writes the letter it owed. Put the outbox back promptly: a
   retry that runs while it is still blocked fails too, and doubles the wait before the next.

## 2. Proof: the scripts

**Seed two staff members and check each letter.**

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\seed-mock-staff.ps1 -Verify
```

This creates a receptionist in Ho Chi Minh City (the south store) and a technician in Ha Noi
(the north store), each with a Vietnamese name. For both, expect:

- `201 Created`, a `REC-xxxxxx` or `TEC-xxxxxx` id, and `notificationSent True`;
- five `ok` lines for the letter;
- from `-Verify`: the Notification row (`STAFF`, `STAFF_ACCOUNT_CREATED`, `EMAIL`, `SENT`,
  `attemptCount: 1`, the right `homeRegion`) and the matching `STAFF:` Shard Directory entry.

**Run the acceptance criteria.** Restart the backend with a log file and quick retries:

```powershell
$env:LOGGING_FILE_NAME = 'target\sbp.log'
$env:EMAIL_RETRY_BASE_DELAY = 'PT20S'; $env:EMAIL_RETRY_SWEEP_INTERVAL = 'PT10S'
.\mvnw.cmd clean spring-boot:run
```

Then, in another terminal:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\accept-staff-welcome-email.ps1 -Verify
$LASTEXITCODE    # 0
```

About forty seconds later it prints PASS for all five criteria:

1. One email per creation.
2. Nothing sent externally on `dev`.
3. The 502 on a provider failure, with the account intact and the attempt retried.
4. The Notification row.
5. No password in the log.

Without `-Verify` or without the log file, criteria 4 and 5 say SKIP rather than PASS. The
script restores the outbox whatever happens, even on Ctrl+C.

## 3. A real inbox: Gmail

**Get an app password (once).**

1. Use a personal Gmail account. School and Workspace accounts often block app passwords.
2. Turn on 2-Step Verification: Google Account → **Security**.
3. Open `https://myaccount.google.com/apppasswords`, name it `SBP demo`, and copy the
   16 characters it shows. Drop the spaces.

**Start the backend sending for real.** Use a fresh terminal, so the credential disappears
when you close it:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev,smtp'
$env:MAIL_HOST = 'smtp.gmail.com'; $env:MAIL_PORT = '587'
$env:MAIL_USERNAME = 'you@gmail.com'; $env:MAIL_PASSWORD = 'yourapppassword16'
$env:EMAIL_FROM_ADDRESS = 'you@gmail.com'   # Gmail rewrites the sender to you anyway
$env:EMAIL_RETRY_ENABLED = 'false'          # sends only what you trigger - see "Before you start"
.\mvnw.cmd clean spring-boot:run
```

Startup must now say `Email delivery: SMTP mode. Messages are sent for real, from you@gmail.com`.

**Send yourself the letters.** Either:

- **Seed script:**

  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\seed-mock-staff.ps1 -Mailbox you@gmail.com
  ```

  Each staff member gets a plus-address (`you+seed.rec.<time>@gmail.com`), so both letters land
  in your inbox while the mock still sees two separate accounts.
- **Admin page:** use your own address. For several accounts, use plus-addresses
  (`you+demo1@gmail.com`, `you+demo2@gmail.com`); the mock refuses a repeated email with 409.

**Both scripts check first.** Before creating anything, each reads the backend's
`/actuator/health`. Spring Boot lists a `mail` component there exactly when a real mail sender
exists. On such a backend, the seed script refuses to run without `-Mailbox`, because its
default `@fixinh.vn` addresses would really be sent to. The acceptance script refuses outright,
because it needs file mode. Either way, nothing is created.

**Check the inbox, and Spam and Promotions too.** A new sender and the word "password" are a
classic filter trigger. Mark the first letter as not spam, and later ones arrive normally.

**Tear down.** Stop the backend, close that terminal, and revoke the app password at
`https://myaccount.google.com/apppasswords`. The next plain `spring-boot:run` is back in file
mode.

## When something goes wrong

| What you see | Why, and what to do |
|---|---|
| `Port 8443 was already in use` | Another backend is still running, often from VS Code. `Get-NetTCPConnection -LocalPort 8443 -State Listen` gives its process id; stop it. |
| The letter still says `Role: TECHNICIAN` or `Sign in:` | A backend started before the latest pull. Restart it with `clean spring-boot:run`. |
| Startup fails: `Could not resolve placeholder 'MAIL_…'` | One of the four `MAIL_*` variables is missing from that terminal. The message names it. |
| Startup fails: `mode is 'smtp' but no mail sender exists` | The mode was switched to `smtp` by hand, without the profile that brings the transport. Use `SPRING_PROFILES_ACTIVE=dev,smtp` instead. |
| Yellow "hasn't gone out yet" on `smtp` | Gmail refused the letter. The backend log's warning gives the reason, and never the password: `535 … Username and Password not accepted` is a wrong app password or 2-Step Verification off. `530 … Must issue a STARTTLS command first` means STARTTLS is off: mail was configured by hand rather than through the `smtp` profile. |
| `notificationSent True`, but nothing in the inbox | Gmail accepted it. Look in Spam and Promotions, and search for "Welcome to Fixinh". |
| `-Verify` prints a query instead of rows | `MONGO_URI_SOUTH` / `MONGO_URI_NORTH` are not set in that terminal, or `mongosh` is missing or is the npm shim. The printed query can be pasted into mongosh by hand. |
| `Sign-in failed: HTTP 401` | The demo administrator is not on this cluster. Pass `-AdminEmail` and `-AdminPassword`. |
| A script says `This backend sends real email` | It found a mail server in the backend's health. For the seed, add `-Mailbox you@gmail.com`. For the acceptance run, restart the backend without the `smtp` profile. |
| Acceptance criterion 3 times out | The backend was started without the quick retry settings, a teammate's backend swept the letter, or the backend writes to another outbox. Pass `-OutboxDir` with the path from its `Email delivery: FILE mode` line. |
| `target\mail is a file, not the outbox directory` | An interrupted manual failure demo. Delete the file; the backend recreates the directory on the next send. |
