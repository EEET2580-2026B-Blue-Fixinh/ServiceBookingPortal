<#
.SYNOPSIS
    Creates mock staff through SBP's own staff endpoint, so the staff welcome email
    (Req 3.1.S5, API Doc section 10.6) has real data behind it.

.DESCRIPTION
    SMT's service has no staff API, so SBP's mock SMP client stands in for it
    (fixinh.sbp.smp.staff-mode=mock, the default even when bookings are live). This
    script signs in as an administrator and creates one receptionist in Ho Chi Minh
    City (south store) and one technician in Ha Noi (north store) through
    POST /api/v1/admin/staff - the same request the admin page sends - so each one
    runs the real send path.

    For each staff member it reports the staff id and notificationSent, then opens the
    welcome email the backend wrote to its outbox and checks that the letter carries
    the name with its Vietnamese characters intact, the role, the sign-in email, a
    password line and the sign-in URL. With -Verify it also reads the Notification row
    and the Shard Directory entry back from that region's store through mongosh.

    Every run uses new addresses, so it can be repeated. Nothing it prints contains the
    initial password; open the .eml file to read the letter itself.

    Against a backend running the smtp profile, pass -Mailbox: without it the letters
    would really be sent to @fixinh.vn addresses.

    Exits 0 when every account was created, every email went out and every letter
    checked passed; 1 otherwise.

.PARAMETER BaseUrl
    SBP's API root. Defaults to the dev profile's http://localhost:8443/api/v1.

.PARAMETER AdminEmail
    The administrator to sign in as. Defaults to VITE_DEMO_ADMIN_EMAIL from
    Frontend\.env, or from Frontend\.env.example when there is no .env - the account
    behind the login page's demo shortcut.

.PARAMETER AdminPassword
    That administrator's password. Same default source as -AdminEmail.

.PARAMETER Mailbox
    A real address to receive the letters, for a backend running the smtp profile. Each
    staff member gets a plus-address of it (you+seed.rec.<stamp>@gmail.com), so both
    letters arrive in that one inbox while the mock still sees two accounts. The outbox
    check is skipped, because a real send writes no file.

.PARAMETER OutboxDir
    Where the backend writes emails in file mode. Defaults to Backend\target\mail, or
    target\mail at the repository root when only that one exists.

.PARAMETER Verify
    Also read the stored records through mongosh, connecting with MONGO_URI_SOUTH and
    MONGO_URI_NORTH from the environment - the same variables the backend needs.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\seed-mock-staff.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\seed-mock-staff.ps1 -Verify

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\seed-mock-staff.ps1 -Mailbox you@gmail.com
#>
[CmdletBinding()]
param(
    [string] $BaseUrl = 'http://localhost:8443/api/v1',
    [string] $AdminEmail,
    [string] $AdminPassword,
    [string] $Mailbox,
    [string] $OutboxDir,
    [switch] $Verify
)

$ErrorActionPreference = 'Stop'

# Any terminating error - most often the backend not being up - ends the run with one
# readable line and exit code 1, instead of a screen of PowerShell error formatting.
trap {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}

Import-Module (Join-Path $PSScriptRoot 'SbpScriptSupport.psm1') -Force

$BackendDir = Split-Path -Parent $PSScriptRoot
$RepoDir = Split-Path -Parent $BackendDir

# --- Sign in -----------------------------------------------------------------------------

$credential = Resolve-SbpAdminCredential -RepoDir $RepoDir -AdminEmail $AdminEmail -AdminPassword $AdminPassword
Write-Host "Signing in to $BaseUrl as $($credential.Email)"
$token = Connect-SbpAdmin -BaseUrl $BaseUrl -Credential $credential

# --- Create, then check what each one produced -------------------------------------------

$specs = New-SbpStaffSpecs -Stamp (Get-Date -Format 'yyyyMMddHHmmss') -Purpose 'seed' -Mailbox $Mailbox
$created = 0
$sent = 0
$failures = 0

foreach ($spec in $specs) {
    Write-Host ''
    Write-Host "$($spec.Label) -> $($spec.Store.ToLower()) store" -ForegroundColor Cyan

    $response = Invoke-SbpApi -BaseUrl $BaseUrl -Method 'POST' -Path '/admin/staff' `
        -JsonBody (New-SbpStaffJson $spec) -Token $token
    $staffId = $null

    if ($response.Status -eq 201) {
        $created++
        $staffId = [string]$response.Json.staffId
        Write-SbpField 'POST /admin/staff' '201 Created' 'Green'
        Write-SbpField 'staffId' $staffId
        Write-SbpField 'email' $spec.Email
        Write-SbpField 'notificationSent' ([string]$response.Json.notificationSent)
        if ($response.Json.notificationSent -eq $true) { $sent++ } else { $failures++ }

        if ($Mailbox) {
            Write-SbpField 'welcome email' "sent for real - look in $Mailbox (and its spam folder)"
        }
        else {
            $outbox = Resolve-SbpOutbox -BackendDir $BackendDir -RepoDir $RepoDir -OutboxDir $OutboxDir
            $letters = Find-SbpWelcomeLetters -Outbox $outbox -Email $spec.Email
            if (-not $outbox) {
                Write-SbpField 'welcome email' 'no outbox found - skipped (pass -OutboxDir if it is elsewhere)' 'Yellow'
            }
            elseif ($letters.Count -eq 0) {
                Write-SbpField 'welcome email' "none for this address in $outbox" 'Red'
                $failures++
            }
            else {
                Write-SbpField 'welcome email' $letters[0].FullName
                if (-not (Test-SbpWelcomeLetter -File $letters[0] -Spec $spec)) { $failures++ }
            }
        }
    }
    elseif ($response.Status -eq 502) {
        # Section 10.6: the account exists, only the email failed, and it is queued for retry.
        $created++
        $failures++
        Write-SbpField 'POST /admin/staff' "502 $(Get-SbpErrorCode $response)" 'Yellow'
        Write-SbpField 'email' $spec.Email
        Write-Host '  The account exists; the provider refused the email and the retry sweep owes it.' -ForegroundColor Yellow
    }
    else {
        $failures++
        Write-SbpField 'POST /admin/staff' "$($response.Status) $(Get-SbpErrorCode $response)" 'Red'
        Write-Host "  $($response.Raw)" -ForegroundColor Red
        if ($response.Status -eq 503) {
            Write-Host '  If SMP_STAFF_MODE is live, check MockSMP is running and SMP_STAFF_SERVICE_TOKEN matches its own SMP_SERVICE_TOKEN.' -ForegroundColor Red
        }
        continue
    }

    if ($Verify) { Show-SbpStoredRecords -Spec $spec -StaffId $staffId }
}

# --- Summary -----------------------------------------------------------------------------

$total = $specs.Count
Write-Host ''
$summary = "Created $created of $total; welcome emails sent $sent of $total."
if ($failures -eq 0) {
    Write-Host $summary -ForegroundColor Green
}
else {
    Write-Host "$summary $failures problem(s) above." -ForegroundColor Red
}
if (-not $Verify) {
    Write-Host 'Add -Verify to read the Notification rows and Shard Directory entries back through mongosh.'
}

if ($failures -eq 0) { exit 0 }
exit 1
