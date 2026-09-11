<#
.SYNOPSIS
    Creates mock staff through SBP's own staff endpoint, so the staff welcome email
    (Req 3.1.S5, API Doc section 10.6) has real data behind it.

.DESCRIPTION
    SMT's service has no staff API yet, so SBP's mock SMP client stands in for it
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

    Exits 0 when every account was created, every email went out and every letter
    passed its checks; 1 otherwise.

.PARAMETER BaseUrl
    SBP's API root. Defaults to the dev profile's http://localhost:8443/api/v1.

.PARAMETER AdminEmail
    The administrator to sign in as. Defaults to VITE_DEMO_ADMIN_EMAIL from
    Frontend\.env, or from Frontend\.env.example when there is no .env - the account
    behind the login page's demo shortcut.

.PARAMETER AdminPassword
    That administrator's password. Same default source as -AdminEmail.

.PARAMETER OutboxDir
    Where the backend writes emails in file mode. Defaults to Backend\target\mail, or
    target\mail at the repository root when only that one exists (an IDE launch can
    write it there). The backend prints the real path at startup, on the line that
    begins "Email delivery: FILE mode".

.PARAMETER Verify
    Also read the stored records through mongosh, connecting with MONGO_URI_SOUTH and
    MONGO_URI_NORTH from the environment - the same variables the backend needs.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\seed-mock-staff.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\seed-mock-staff.ps1 -Verify
#>
[CmdletBinding()]
param(
    [string] $BaseUrl = 'http://localhost:8443/api/v1',
    [string] $AdminEmail,
    [string] $AdminPassword,
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

$BackendDir = Split-Path -Parent $PSScriptRoot
$RepoDir = Split-Path -Parent $BackendDir
$BaseUrl = $BaseUrl.TrimEnd('/')

# --- Helpers ---------------------------------------------------------------------------

function Read-DotEnvValue {
    # One KEY=value from a .env file, or $null. Blank lines and comments are skipped, and
    # surrounding quotes are dropped the way Vite drops them.
    param([string] $Path, [string] $Key)

    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
        $equals = $trimmed.IndexOf('=')
        if ($equals -lt 1) { continue }
        if ($trimmed.Substring(0, $equals).Trim() -ne $Key) { continue }
        return $trimmed.Substring($equals + 1).Trim().Trim('"').Trim("'")
    }
    return $null
}

function Resolve-DemoAdminSetting {
    # Frontend\.env first, because that is what the running login page actually offers.
    param([string] $Key)

    $frontendDir = Join-Path $RepoDir 'Frontend'
    foreach ($file in @('.env', '.env.example')) {
        $value = Read-DotEnvValue -Path (Join-Path $frontendDir $file) -Key $Key
        if ($value) { return $value }
    }
    return $null
}

function Invoke-SbpApi {
    # One call through curl.exe, returning the status code and the parsed body whatever the
    # status. Invoke-RestMethod throws on 4xx and 5xx and, on Windows PowerShell 5.1, makes
    # the error body awkward to reach - and the error body is exactly what this script needs
    # to report. The request body travels in a file, so no JSON is ever mangled by the way
    # PowerShell quotes arguments for native programs.
    param(
        [Parameter(Mandatory = $true)] [string] $Method,
        [Parameter(Mandatory = $true)] [string] $Path,
        [string] $JsonBody,
        [string] $Token
    )

    $bodyFile = [IO.Path]::GetTempFileName()
    $outFile = [IO.Path]::GetTempFileName()
    try {
        $curlArgs = @('-s', '-S', '--max-time', '60', '-X', $Method, "$BaseUrl$Path",
            '-o', $outFile, '-w', '%{http_code}', '-H', 'Accept: application/json')
        if ($JsonBody) {
            [IO.File]::WriteAllText($bodyFile, $JsonBody)
            $curlArgs += @('-H', 'Content-Type: application/json', '--data-binary', "@$bodyFile")
        }
        if ($Token) {
            $curlArgs += @('-H', "Authorization: Bearer $Token")
        }

        $status = & curl.exe @curlArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Could not reach $BaseUrl (curl exit code $LASTEXITCODE). Is the backend running?"
        }

        $raw = [IO.File]::ReadAllText($outFile, [Text.Encoding]::UTF8)
        $json = $null
        if ($raw.Trim()) {
            try { $json = $raw | ConvertFrom-Json } catch { $json = $null }
        }
        return [pscustomobject]@{ Status = [int]$status; Json = $json; Raw = $raw }
    }
    finally {
        Remove-Item -LiteralPath $bodyFile, $outFile -ErrorAction SilentlyContinue
    }
}

function Get-ErrorCode {
    # The error field of SBP's standard error body (API Doc section 2.7), or '' when absent.
    param($Response)

    if ($Response.Json -and $Response.Json.error) { return [string]$Response.Json.error }
    return ''
}

function New-StaffJson {
    # Built by hand rather than with ConvertTo-Json, which would escape the backslash of each
    # \u sequence and send the escape itself instead of the character. Every value here is
    # ASCII this script controls, with no quotes that would need escaping.
    param($Spec)

    return ('{{"fullName":"{0}","email":"{1}","phoneNumber":"{2}","staffRole":"{3}",' +
        '"address":{{"streetNumber":"{4}","streetName":"{5}","ward":"{6}","provinceCode":"{7}"}},' +
        '"branchProvinceCode":"{7}"}}') -f $Spec.NameJson, $Spec.Email, $Spec.Phone, $Spec.Role,
        $Spec.StreetNumber, $Spec.StreetJson, $Spec.WardJson, $Spec.Branch
}

function Resolve-Outbox {
    # $null when no outbox exists anywhere - smtp mode, or a backend started from elsewhere.
    if ($OutboxDir) {
        if (Test-Path -LiteralPath $OutboxDir) { return $OutboxDir }
        return $null
    }
    $candidates = @(
        (Join-Path (Join-Path $BackendDir 'target') 'mail'),
        (Join-Path (Join-Path $RepoDir 'target') 'mail')
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    return $null
}

function Write-Field {
    param([string] $Name, [string] $Value, [string] $Colour = 'Gray')
    Write-Host ('  {0,-20} {1}' -f $Name, $Value) -ForegroundColor $Colour
}

function Test-WelcomeEmail {
    # Finds the letter FileWritingEmailClient wrote for this address and checks its content.
    # Returns 'ok', 'failed' or 'skipped' - skipped when there is no outbox to look in, which
    # is not a failure: smtp mode sends instead of writing.
    param($Spec)

    $outbox = Resolve-Outbox
    if (-not $outbox) {
        Write-Field 'welcome email' 'no outbox found - skipped (smtp mode, or pass -OutboxDir)' 'Yellow'
        return 'skipped'
    }

    # The client names each file <timestamp>_<address with unsafe characters replaced>.eml.
    $suffix = '_' + ($Spec.Email -replace '[^A-Za-z0-9.-]', '_') + '.eml'
    $file = Get-ChildItem -LiteralPath $outbox -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name.EndsWith($suffix) } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $file) {
        Write-Field 'welcome email' "none for this address in $outbox" 'Red'
        return 'failed'
    }
    Write-Field 'welcome email' $file.FullName

    # Headers, one blank line, body. The body is where every check belongs: the To header
    # alone would pass the address check without proving the letter mentions it.
    $text = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
    $split = $text.IndexOf("`n`n")
    $body = ''
    if ($split -ge 0) { $body = $text.Substring($split + 2) }

    $name = [regex]::Unescape($Spec.NameJson)
    $url = [regex]::Match($body, 'Sign in at (\S+)').Groups[1].Value

    $checks = [ordered]@{
        'name, diacritics intact' = $body.Contains("Hello $name,")
        'role'                    = ($body -match "Role:\s+$($Spec.Role)\b")
        'sign-in email'           = $body.Contains($Spec.Email)
        'password line'           = ($body -match '(?m)^\s*Password:\s+\S+')
        'sign-in URL'             = [bool]$url
    }

    $result = 'ok'
    foreach ($check in $checks.GetEnumerator()) {
        if ($check.Value) {
            Write-Host ('    {0,-26} ok' -f $check.Key) -ForegroundColor Green
        }
        else {
            Write-Host ('    {0,-26} MISSING' -f $check.Key) -ForegroundColor Red
            $result = 'failed'
        }
    }
    if ($url -match 'localhost') {
        Write-Host "    The sign-in URL is still the placeholder $url." -ForegroundColor Yellow
        Write-Host '    Set EMAIL_STAFF_SIGN_IN_URL to SMP''s real sign-in page before a demo.' -ForegroundColor Yellow
    }
    return $result
}

function Show-StoredRecords {
    # The Notification row (looked up by address - its _id is an ObjectId the script never
    # sees) and the Shard Directory entry, read from the store this branch belongs to.
    param($Spec, [string] $StaffId)

    # Single quotes only inside the script, so PowerShell passes it to mongosh unchanged.
    $js = "const d=db.getSiblingDB('$($Spec.Database)');" +
        "printjson(d.notifications.find({recipientAddress:'$($Spec.Email)'}," +
        '{_id:0,recipientType:1,type:1,deliveryChannel:1,deliveryStatus:1,attemptCount:1,' +
        'sentAt:1,homeRegion:1}).toArray());'

    # A 502 carries no staff id, so there is no directory entry to name.
    if ($StaffId) { $js += "printjson(d.shardDirectory.findOne({_id:'STAFF:$StaffId'}));" }

    $uri = [Environment]::GetEnvironmentVariable($Spec.UriVariable)
    $mongosh = Get-Command mongosh -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1

    # A .cmd or .bat shim runs through cmd.exe, which would split an Atlas URI at its '&'.
    $runnable = $uri -and $mongosh -and ($mongosh.Path -notmatch '\.(cmd|bat)$')
    if (-not $runnable) {
        Write-Field 'stored records' "run this in mongosh against the $($Spec.Store) cluster:" 'Yellow'
        if (-not $uri) { Write-Host "    ($($Spec.UriVariable) is not set in this session)" -ForegroundColor Yellow }
        Write-Host "    $js"
        return
    }

    Write-Field 'stored records' "$($Spec.Database), via mongosh"
    & $mongosh.Path $uri --quiet --eval $js | ForEach-Object { Write-Host "    $_" }
    if ($LASTEXITCODE -ne 0) {
        Write-Host "    mongosh exited with $LASTEXITCODE - run the query above by hand." -ForegroundColor Yellow
    }
}

# --- Sign in -----------------------------------------------------------------------------

if (-not $AdminEmail) { $AdminEmail = Resolve-DemoAdminSetting 'VITE_DEMO_ADMIN_EMAIL' }
if (-not $AdminPassword) { $AdminPassword = Resolve-DemoAdminSetting 'VITE_DEMO_ADMIN_PASSWORD' }
if (-not $AdminEmail -or -not $AdminPassword) {
    Write-Host 'No administrator to sign in as. Pass -AdminEmail and -AdminPassword, or set' -ForegroundColor Red
    Write-Host 'VITE_DEMO_ADMIN_EMAIL and VITE_DEMO_ADMIN_PASSWORD in Frontend\.env.' -ForegroundColor Red
    exit 1
}

Write-Host "Signing in to $BaseUrl as $AdminEmail"
$loginBody = ConvertTo-Json -Compress -InputObject @{ identifier = $AdminEmail; password = $AdminPassword }
$login = Invoke-SbpApi -Method 'POST' -Path '/auth/login' -JsonBody $loginBody

if ($login.Status -ne 200 -or -not $login.Json -or -not $login.Json.accessToken) {
    Write-Host "Sign-in failed: HTTP $($login.Status) $(Get-ErrorCode $login)" -ForegroundColor Red
    if ($login.Status -eq 401) {
        Write-Host "  $AdminEmail is not an account on this cluster, or the password is wrong." -ForegroundColor Red
        Write-Host '  Pass -AdminEmail and -AdminPassword for an administrator that exists there.' -ForegroundColor Red
    }
    exit 1
}
if ($login.Json.user -and $login.Json.user.role -ne 'ADMIN') {
    Write-Host "Signed in, but $AdminEmail is a $($login.Json.user.role), not an administrator." -ForegroundColor Red
    exit 1
}
$token = $login.Json.accessToken

# --- The staff to create -----------------------------------------------------------------

# A second-resolution stamp makes every run's addresses new, so the mock never answers 409.
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$clock = $stamp.Substring(8)

# Names and streets are JSON \u escapes so this file stays ASCII: Windows PowerShell 5.1
# reads a file without a byte-order mark as Windows-1252. The server decodes the escapes,
# so the letter carries real Vietnamese text - which the name check below then confirms.
$specs = @(
    [pscustomobject]@{
        Label        = 'Receptionist, Ho Chi Minh City'
        Role         = 'RECEPTIONIST'
        Branch       = 'HCM'
        Store        = 'SOUTH'
        Database     = 'sbp_south'
        UriVariable  = 'MONGO_URI_SOUTH'
        Email        = "seed.rec.$stamp@fixinh.vn"
        Phone        = "090$($clock)1"
        NameJson     = 'Tr\u1ea7n Th\u1ecb Mai Anh'
        StreetNumber = '12'
        StreetJson   = 'Nguy\u1ec5n Hu\u1ec7'
        WardJson     = 'B\u1ebfn Ngh\u00e9'
    },
    [pscustomobject]@{
        Label        = 'Technician, Ha Noi'
        Role         = 'TECHNICIAN'
        Branch       = 'HN'
        Store        = 'NORTH'
        Database     = 'sbp_north'
        UriVariable  = 'MONGO_URI_NORTH'
        Email        = "seed.tec.$stamp@fixinh.vn"
        Phone        = "090$($clock)2"
        NameJson     = 'Nguy\u1ec5n V\u0103n H\u00f9ng'
        StreetNumber = '25'
        StreetJson   = 'Tr\u00e0ng Ti\u1ec1n'
        WardJson     = 'Ho\u00e0n Ki\u1ebfm'
    }
)

# --- Create, then check what each one produced -------------------------------------------

$created = 0
$sent = 0
$failures = 0

foreach ($spec in $specs) {
    Write-Host ''
    Write-Host "$($spec.Label) -> $($spec.Store.ToLower()) store" -ForegroundColor Cyan

    $response = Invoke-SbpApi -Method 'POST' -Path '/admin/staff' -JsonBody (New-StaffJson $spec) -Token $token
    $staffId = $null

    if ($response.Status -eq 201) {
        $created++
        $staffId = [string]$response.Json.staffId
        Write-Field 'POST /admin/staff' '201 Created' 'Green'
        Write-Field 'staffId' $staffId
        Write-Field 'email' $spec.Email
        Write-Field 'notificationSent' ([string]$response.Json.notificationSent)
        if ($response.Json.notificationSent -eq $true) { $sent++ } else { $failures++ }

        if ((Test-WelcomeEmail -Spec $spec) -eq 'failed') { $failures++ }
    }
    elseif ($response.Status -eq 502) {
        # Section 10.6: the account exists, only the email failed, and it is queued for retry.
        $created++
        $failures++
        Write-Field 'POST /admin/staff' "502 $(Get-ErrorCode $response)" 'Yellow'
        Write-Field 'email' $spec.Email
        Write-Host '  The account exists; the provider refused the email and the retry sweep owes it.' -ForegroundColor Yellow
    }
    else {
        $failures++
        Write-Field 'POST /admin/staff' "$($response.Status) $(Get-ErrorCode $response)" 'Red'
        Write-Host "  $($response.Raw)" -ForegroundColor Red
        if ($response.Status -eq 500) {
            Write-Host '  If SMP_STAFF_MODE is live, the live staff client is still a placeholder; leave it on mock.' -ForegroundColor Red
        }
        continue
    }

    if ($Verify) { Show-StoredRecords -Spec $spec -StaffId $staffId }
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
