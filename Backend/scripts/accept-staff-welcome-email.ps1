<#
.SYNOPSIS
    Acceptance run for the staff welcome email (Req 3.1.S5, API Doc section 10.6), against
    a running backend on the dev profile.

.DESCRIPTION
    Checks the issue's five acceptance criteria end to end, through the real API:

      1. Creating a staff member through section 10.6 sends one email to the new staff
         member's address.
      2. The dev profile sends nothing externally.
      3. A provider failure leaves the staff account intact, returns 502 with
         EMAIL_DELIVERY_FAILED, and records an attempt for retry.
      4. A Notification row exists with recipientType STAFF, type STAFF_ACCOUNT_CREATED
         and deliveryChannel EMAIL.
      5. No credential or password appears in the application logs.

    How the failure is caused: in file mode the backend writes each letter into its outbox
    directory. The script moves that directory aside and puts an empty file in its place,
    so the next send cannot write and fails exactly as a provider outage would. It puts the
    directory back afterwards, whatever happens, then waits for the retry sweep to deliver
    the letter it owes.

    Start the backend first, from Backend, with a log file and a quick retry schedule:

        $env:LOGGING_FILE_NAME = 'target\sbp.log'
        $env:EMAIL_RETRY_BASE_DELAY = 'PT20S'
        $env:EMAIL_RETRY_SWEEP_INTERVAL = 'PT10S'
        .\mvnw.cmd clean spring-boot:run

    Without the retry settings, the retry step waits up to about three minutes instead of
    about forty seconds. Criterion 4 needs -Verify (mongosh, plus MONGO_URI_SOUTH and
    MONGO_URI_NORTH in this session); criterion 5 needs the log file. Without them those
    criteria report SKIP, never PASS.

    Every backend pointed at the same clusters sweeps the same rows. Run this while no
    teammate's backend is up against them, or the retried letter can land in their outbox.

    Nothing printed contains a password. Exits 0 when no criterion failed, 1 otherwise.

.PARAMETER BaseUrl
    SBP's API root. Defaults to the dev profile's http://localhost:8443/api/v1.

.PARAMETER AdminEmail
    The administrator to sign in as. Defaults to the demo account in Frontend\.env, or in
    Frontend\.env.example when there is no .env.

.PARAMETER AdminPassword
    That administrator's password. Same default source as -AdminEmail.

.PARAMETER OutboxDir
    The backend's outbox, as printed on its "Email delivery: FILE mode" startup line.
    Defaults to Backend\target\mail.

.PARAMETER LogFile
    The backend's log file - whatever LOGGING_FILE_NAME was when it started, resolved from
    Backend. Defaults to Backend\target\sbp.log.

.PARAMETER RecoveryTimeoutSeconds
    How long to wait for the retry sweep to deliver the refused letter. Default 240.

.PARAMETER Verify
    Also read both Notification rows back through mongosh and check every field
    criterion 4 names.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\accept-staff-welcome-email.ps1 -Verify
#>
[CmdletBinding()]
param(
    [string] $BaseUrl = 'http://localhost:8443/api/v1',
    [string] $AdminEmail,
    [string] $AdminPassword,
    [string] $OutboxDir,
    [string] $LogFile,
    [int] $RecoveryTimeoutSeconds = 240,
    [switch] $Verify
)

$ErrorActionPreference = 'Stop'

trap {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}

Import-Module (Join-Path $PSScriptRoot 'SbpScriptSupport.psm1') -Force

$BackendDir = Split-Path -Parent $PSScriptRoot
$RepoDir = Split-Path -Parent $BackendDir
if (-not $LogFile) { $LogFile = Join-Path (Join-Path $BackendDir 'target') 'sbp.log' }
elseif (-not [IO.Path]::IsPathRooted($LogFile)) { $LogFile = Join-Path $BackendDir $LogFile }

# --- Helpers local to this script ------------------------------------------------------

# One verdict per criterion. A hashtable is a reference, so the functions below update it
# in place without needing a $script: variable. A plain hashtable, not [ordered]: an
# ordered dictionary treats an integer index as a position, not a key.
$verdicts = @{}
foreach ($number in 1..5) { $verdicts[$number] = [pscustomobject]@{ Outcome = 'SKIP'; Why = 'not reached' } }

function Set-Verdict {
    param([int] $Number, [string] $Outcome, [string] $Why)
    $verdicts[$Number] = [pscustomobject]@{ Outcome = $Outcome; Why = $Why }
    $colour = @{ PASS = 'Green'; FAIL = 'Red'; SKIP = 'Yellow' }[$Outcome]
    Write-Host ('  => criterion {0}: {1} - {2}' -f $Number, $Outcome, $Why) -ForegroundColor $colour
}

function Write-Step {
    param([string] $Title)
    Write-Host ''
    Write-Host $Title -ForegroundColor Cyan
}

function Block-Outbox {
    # Moves the outbox aside and puts an empty file where it was. FileWritingEmailClient then
    # cannot create the directory it writes into, and every send fails the way a provider
    # outage would - the section 10.6 502 path, reached without touching the backend.
    param([string] $Outbox)

    $aside = $null
    if (Test-Path -LiteralPath $Outbox -PathType Container) {
        $aside = "$Outbox.held-$(Get-Date -Format 'yyyyMMddHHmmss')"
        Move-Item -LiteralPath $Outbox -Destination $aside
    }
    New-Item -ItemType File -Path $Outbox -Force | Out-Null
    return [pscustomobject]@{ Outbox = $Outbox; Aside = $aside }
}

function Restore-Outbox {
    param($Held)

    if (Test-Path -LiteralPath $Held.Outbox -PathType Leaf) {
        Remove-Item -LiteralPath $Held.Outbox -Force
    }
    if ($Held.Aside -and (Test-Path -LiteralPath $Held.Aside)) {
        Move-Item -LiteralPath $Held.Aside -Destination $Held.Outbox
    }
}

function Test-Row {
    # Compares a stored row with what criterion 4 and the delivery state require. Returns a
    # list of mismatches, empty when the row is right.
    param($Row, [string] $Status, [int] $Attempts, [string] $Region)

    $expected = [ordered]@{
        recipientType   = 'STAFF'
        type            = 'STAFF_ACCOUNT_CREATED'
        deliveryChannel = 'EMAIL'
        deliveryStatus  = $Status
        attemptCount    = $Attempts
        homeRegion      = $Region
    }
    $wrong = @()
    foreach ($field in $expected.Keys) {
        if ([string]$Row.$field -ne [string]$expected[$field]) {
            $wrong += "$field is '$($Row.$field)', expected '$($expected[$field])'"
        }
    }
    return ,$wrong
}

# --- Before anything else ----------------------------------------------------------------

$credential = Resolve-SbpAdminCredential -RepoDir $RepoDir -AdminEmail $AdminEmail -AdminPassword $AdminPassword
Write-Host "Signing in to $BaseUrl as $($credential.Email)"
$token = Connect-SbpAdmin -BaseUrl $BaseUrl -Credential $credential

$outbox = Resolve-SbpOutbox -BackendDir $BackendDir -RepoDir $RepoDir -OutboxDir $OutboxDir -AllowMissing
if (Test-Path -LiteralPath $outbox -PathType Leaf) {
    throw "$outbox is a file, not the outbox directory - left by an interrupted run. Delete it and run again."
}
Write-Host "Outbox:   $outbox"
Write-Host "Log file: $LogFile"

$specs = New-SbpStaffSpecs -Stamp (Get-Date -Format 'yyyyMMddHHmmss') -Purpose 'accept'
$first = $specs[0]
$second = $specs[1]
$firstCreatedAt = Get-Date

# --- 1 and 2: an ordinary creation -------------------------------------------------------

Write-Step "Creating the $($first.Label): $($first.Email)"
$created = Invoke-SbpApi -BaseUrl $BaseUrl -Method 'POST' -Path '/admin/staff' `
    -JsonBody (New-SbpStaffJson $first) -Token $token
Write-SbpField 'POST /admin/staff' ("$($created.Status) $(Get-SbpErrorCode $created)".Trim())

$firstLetters = Find-SbpWelcomeLetters -Outbox $outbox -Email $first.Email
if ($created.Status -ne 201 -or $created.Json.notificationSent -ne $true) {
    Set-Verdict 1 'FAIL' "expected 201 with notificationSent true, got $($created.Status)"
    Set-Verdict 2 'FAIL' 'no successful send to judge'
}
elseif ($firstLetters.Count -eq 0) {
    # Accepted, yet no letter written: something other than the file client took it.
    Set-Verdict 2 'FAIL' 'the email was accepted but no letter reached the outbox - is the smtp profile active?'
}
else {
    Write-SbpField 'staffId' ([string]$created.Json.staffId)
    Write-SbpField 'welcome email' $firstLetters[0].FullName
    Set-Verdict 2 'PASS' 'the letter was written to the outbox by the file client; nothing was sent'
}

# --- 3: a provider failure ---------------------------------------------------------------

Write-Step "Blocking the outbox, then creating the $($second.Label): $($second.Email)"
$refused = $null
$accountIntact = $false
$failedRowOk = $null
$held = Block-Outbox -Outbox $outbox
try {
    $refused = Invoke-SbpApi -BaseUrl $BaseUrl -Method 'POST' -Path '/admin/staff' `
        -JsonBody (New-SbpStaffJson $second) -Token $token
    Write-SbpField 'POST /admin/staff' ("$($refused.Status) $(Get-SbpErrorCode $refused)".Trim())

    # The account must still exist: look it up the way the admin page's search does.
    $search = Invoke-SbpApi -BaseUrl $BaseUrl -Method 'GET' `
        -Path "/admin/staff?q=$([uri]::EscapeDataString($second.Email))" -Token $token
    $found = @($search.Json.items | Where-Object { $_.email -eq $second.Email })
    $accountIntact = ($search.Status -eq 200 -and $found.Count -eq 1)
    if ($accountIntact) { Write-SbpField 'account' "intact - $($found[0].staffId)" 'Green' }
    else { Write-SbpField 'account' "not found (HTTP $($search.Status))" 'Red' }

    if ($Verify) {
        $read = Get-SbpNotificationRow -Spec $second
        if ($read.Available -and $read.Row) {
            $wrong = Test-Row -Row $read.Row -Status 'FAILED' -Attempts 1 -Region $second.Store
            $failedRowOk = ($wrong.Count -eq 0)
            if ($failedRowOk) { Write-SbpField 'attempt recorded' 'FAILED, attemptCount 1' 'Green' }
            else { Write-SbpField 'attempt recorded' ($wrong -join '; ') 'Red' }
        }
        elseif ($read.Available) {
            $failedRowOk = $false
            Write-SbpField 'attempt recorded' 'no notification row for this address' 'Red'
        }
    }
}
finally {
    Restore-Outbox -Held $held
    Write-SbpField 'outbox' 'restored'
}

Write-Host ("  waiting up to {0}s for the retry sweep to deliver it" -f $RecoveryTimeoutSeconds) -NoNewline
$deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
$secondLetters = Find-SbpWelcomeLetters -Outbox $outbox -Email $second.Email
while ($secondLetters.Count -eq 0 -and (Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    Write-Host '.' -NoNewline
    $secondLetters = Find-SbpWelcomeLetters -Outbox $outbox -Email $second.Email
}
Write-Host ''
if ($secondLetters.Count -gt 0) { Write-SbpField 'retried letter' $secondLetters[0].FullName 'Green' }
else { Write-SbpField 'retried letter' 'did not arrive in time' 'Red' }

$refusedRight = ($refused -and $refused.Status -eq 502 -and (Get-SbpErrorCode $refused) -eq 'EMAIL_DELIVERY_FAILED')
if (-not $refusedRight) {
    $what = 'no response'
    if ($refused) { $what = "$($refused.Status) $(Get-SbpErrorCode $refused)" }
    Set-Verdict 3 'FAIL' "expected 502 EMAIL_DELIVERY_FAILED, got $what - does the backend write to $outbox?"
}
elseif (-not $accountIntact) {
    Set-Verdict 3 'FAIL' 'the 502 came back, but the account was not found afterwards'
}
elseif ($failedRowOk -eq $false) {
    Set-Verdict 3 'FAIL' 'the refused attempt was not recorded as FAILED with attemptCount 1'
}
elseif ($secondLetters.Count -eq 0) {
    Set-Verdict 3 'FAIL' (("the letter was not retried within {0}s - check the backend's retry " +
        "settings, and that no teammate's backend swept it") -f $RecoveryTimeoutSeconds)
}
else {
    Set-Verdict 3 'PASS' '502 EMAIL_DELIVERY_FAILED, account intact, attempt recorded and retried'
}

# --- 1, now that sweeps have run: still exactly one letter -------------------------------

Write-Step 'Counting letters now that the retry sweep has run'
$firstLetters = Find-SbpWelcomeLetters -Outbox $outbox -Email $first.Email
$secondLetters = Find-SbpWelcomeLetters -Outbox $outbox -Email $second.Email
Write-SbpField $first.Email "$($firstLetters.Count) letter(s)"
Write-SbpField $second.Email "$($secondLetters.Count) letter(s)"

if ($verdicts[1].Outcome -ne 'FAIL') {
    $sinceCreated = [int]((Get-Date) - $firstCreatedAt).TotalSeconds
    if ($firstLetters.Count -ne 1) {
        Set-Verdict 1 'FAIL' "expected exactly one letter for $($first.Email), found $($firstLetters.Count)"
    }
    elseif ($secondLetters.Count -gt 1) {
        Set-Verdict 1 'FAIL' "the retried address got $($secondLetters.Count) letters, not one"
    }
    elseif (-not (Test-SbpWelcomeLetter -File $firstLetters[0] -Spec $first)) {
        Set-Verdict 1 'FAIL' 'one letter, but it is missing something the issue requires'
    }
    else {
        Set-Verdict 1 'PASS' ("one complete letter to the new address, still one after {0}s of sweeps" -f $sinceCreated)
    }
}

# --- 4: the stored rows ------------------------------------------------------------------

Write-Step 'Reading the Notification rows'
if (-not $Verify) {
    Set-Verdict 4 'SKIP' 'run with -Verify to read the rows through mongosh'
}
else {
    $problems = @()
    $readable = $true
    foreach ($pair in @(@($first, 1), @($second, 2))) {
        $spec = $pair[0]
        $read = Get-SbpNotificationRow -Spec $spec
        if (-not $read.Available) { $readable = $false; continue }
        if (-not $read.Row) { $problems += "no row for $($spec.Email)"; continue }
        $wrong = Test-Row -Row $read.Row -Status 'SENT' -Attempts $pair[1] -Region $spec.Store
        if ($wrong.Count -eq 0) {
            Write-SbpField $spec.Database ("STAFF, STAFF_ACCOUNT_CREATED, EMAIL, SENT, attempts {0}, {1}" -f $pair[1], $spec.Store) 'Green'
        }
        else {
            $problems += "$($spec.Email): $($wrong -join '; ')"
        }
    }
    if (-not $readable) {
        Set-Verdict 4 'SKIP' 'mongosh.exe, MONGO_URI_SOUTH and MONGO_URI_NORTH are needed in this session'
    }
    elseif ($problems.Count -gt 0) {
        Set-Verdict 4 'FAIL' ($problems -join ' | ')
    }
    else {
        Set-Verdict 4 'PASS' 'one row per staff member, each in its branch region, each SENT'
    }
}

# --- 5: the log --------------------------------------------------------------------------

Write-Step "Searching $LogFile for credentials"
if (-not (Test-Path -LiteralPath $LogFile -PathType Leaf)) {
    Set-Verdict 5 'SKIP' 'no log file - start the backend with LOGGING_FILE_NAME set, as the help shows'
}
else {
    $log = Read-SbpSharedText -Path $LogFile
    if (-not $log.Contains($first.Email)) {
        # An empty search proves nothing if the log never saw this run.
        Set-Verdict 5 'SKIP' "the log never mentions $($first.Email) - is it this backend's log?"
    }
    else {
        # Held in memory only: the point is to prove these never reach a log, not to print them.
        $secrets = [ordered]@{ 'administrator password' = $credential.Password; 'access token' = $token }
        foreach ($letter in @($firstLetters | Select-Object -First 1) + @($secondLetters | Select-Object -First 1)) {
            $password = Get-SbpLetterPassword -File $letter
            if ($password) { $secrets["initial password in $($letter.Name)"] = $password }
        }
        $leaked = @($secrets.Keys | Where-Object { $log.Contains($secrets[$_]) })
        if ($leaked.Count -gt 0) {
            Set-Verdict 5 'FAIL' ('found in the log: ' + ($leaked -join ', '))
        }
        else {
            Set-Verdict 5 'PASS' ("searched for {0} secret(s); none appear" -f $secrets.Count)
        }
    }
}

# --- Summary -----------------------------------------------------------------------------

Write-Host ''
Write-Host 'Acceptance criteria' -ForegroundColor Cyan
foreach ($number in 1..5) {
    $verdict = $verdicts[$number]
    $colour = @{ PASS = 'Green'; FAIL = 'Red'; SKIP = 'Yellow' }[$verdict.Outcome]
    Write-Host ('  {0}. {1}  {2}' -f $number, $verdict.Outcome, $verdict.Why) -ForegroundColor $colour
}

$failed = @($verdicts.Values | Where-Object { $_.Outcome -eq 'FAIL' }).Count
if ($failed -gt 0) { exit 1 }
exit 0
