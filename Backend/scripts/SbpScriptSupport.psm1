<#
.SYNOPSIS
    Shared helpers for the scripts in Backend\scripts that drive a running SBP backend.

.DESCRIPTION
    Imported by seed-mock-staff.ps1 and accept-staff-welcome-email.ps1.

    A module rather than a file to dot-source, on purpose. A dot-sourced file runs in its
    caller's scope, so a $script: variable inside it quietly means the caller's variable,
    not its own. Here every function takes what it needs as parameters and keeps nothing
    between calls, so a helper behaves the same whichever script imports it.

    Pure ASCII, like the scripts: Windows PowerShell 5.1 reads a file without a byte-order
    mark as Windows-1252, so one accented character would change what the code means.
#>

# Module scope does not inherit the caller's preference, so it is set here as well.
$ErrorActionPreference = 'Stop'

# --- Configuration --------------------------------------------------------------------

function Read-SbpDotEnvValue {
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

function Resolve-SbpAdminCredential {
    # The administrator to sign in as: what the caller passed, otherwise the demo account the
    # login page's shortcut uses - from Frontend\.env first, then Frontend\.env.example.
    param([string] $RepoDir, [string] $AdminEmail, [string] $AdminPassword)

    $frontendDir = Join-Path $RepoDir 'Frontend'
    foreach ($file in @('.env', '.env.example')) {
        $path = Join-Path $frontendDir $file
        if (-not $AdminEmail) { $AdminEmail = Read-SbpDotEnvValue -Path $path -Key 'VITE_DEMO_ADMIN_EMAIL' }
        if (-not $AdminPassword) { $AdminPassword = Read-SbpDotEnvValue -Path $path -Key 'VITE_DEMO_ADMIN_PASSWORD' }
    }
    if (-not $AdminEmail -or -not $AdminPassword) {
        throw ("No administrator to sign in as. Pass -AdminEmail and -AdminPassword, or set`n" +
            'VITE_DEMO_ADMIN_EMAIL and VITE_DEMO_ADMIN_PASSWORD in Frontend\.env.')
    }
    return [pscustomobject]@{ Email = $AdminEmail; Password = $AdminPassword }
}

function Resolve-SbpOutbox {
    # The directory the backend writes letters into in file mode.
    #
    # -OutboxDir wins when given. Otherwise Backend\target\mail, or target\mail at the
    # repository root when only that one exists - an IDE launch can run the backend from
    # there. The backend prints the real path at startup, on the "Email delivery: FILE mode"
    # line. Returns $null when no outbox exists anywhere, unless -AllowMissing asks for the
    # default location regardless.
    param([string] $BackendDir, [string] $RepoDir, [string] $OutboxDir, [switch] $AllowMissing)

    if ($OutboxDir) {
        if ($AllowMissing -or (Test-Path -LiteralPath $OutboxDir)) { return $OutboxDir }
        return $null
    }
    $candidates = @(
        (Join-Path (Join-Path $BackendDir 'target') 'mail'),
        (Join-Path (Join-Path $RepoDir 'target') 'mail')
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    if ($AllowMissing) { return $candidates[0] }
    return $null
}

# --- The API ---------------------------------------------------------------------------

function Invoke-SbpApi {
    # One call through curl.exe, returning the status code and the parsed body whatever the
    # status. Invoke-RestMethod throws on 4xx and 5xx and, on Windows PowerShell 5.1, makes the
    # error body awkward to reach - and the error body is what these scripts report. The
    # request body travels in a file, so no JSON is mangled by the way PowerShell quotes
    # arguments for native programs.
    param(
        [Parameter(Mandatory = $true)] [string] $BaseUrl,
        [Parameter(Mandatory = $true)] [string] $Method,
        [Parameter(Mandatory = $true)] [string] $Path,
        [string] $JsonBody,
        [string] $Token
    )

    $bodyFile = [IO.Path]::GetTempFileName()
    $outFile = [IO.Path]::GetTempFileName()
    try {
        $curlArgs = @('-s', '-S', '--max-time', '60', '-X', $Method, "$($BaseUrl.TrimEnd('/'))$Path",
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

function Get-SbpErrorCode {
    # The error field of SBP's standard error body (API Doc section 2.7), or '' when absent.
    param($Response)

    if ($Response.Json -and $Response.Json.error) { return [string]$Response.Json.error }
    return ''
}

function Connect-SbpAdmin {
    # Signs in and returns the access token, or throws a message a person can act on.
    param([string] $BaseUrl, $Credential)

    $body = ConvertTo-Json -Compress -InputObject @{
        identifier = $Credential.Email
        password   = $Credential.Password
    }
    $login = Invoke-SbpApi -BaseUrl $BaseUrl -Method 'POST' -Path '/auth/login' -JsonBody $body

    if ($login.Status -ne 200 -or -not $login.Json -or -not $login.Json.accessToken) {
        $message = "Sign-in failed: HTTP $($login.Status) $(Get-SbpErrorCode $login)"
        if ($login.Status -eq 401) {
            $message += "`n  $($Credential.Email) is not an account on this cluster, or the password is wrong." +
                "`n  Pass -AdminEmail and -AdminPassword for an administrator that exists there."
        }
        throw $message
    }
    if ($login.Json.user -and $login.Json.user.role -ne 'ADMIN') {
        throw "Signed in, but $($Credential.Email) is a $($login.Json.user.role), not an administrator."
    }
    return [string]$login.Json.accessToken
}

# --- The staff both scripts create -----------------------------------------------------

function New-SbpStaffAddress {
    # A fresh address for one run. Without -Mailbox it is <tag>@fixinh.vn, which must never be
    # sent for real. With it, a plus-address of that mailbox - you+<tag>@gmail.com - so real
    # letters all land in one inbox while the mock still sees distinct accounts.
    param([string] $Tag, [string] $Mailbox)

    if (-not $Mailbox) { return "$Tag@fixinh.vn" }

    $parts = $Mailbox.Split('@')
    if ($parts.Count -ne 2 -or -not $parts[0] -or -not $parts[1].Contains('.')) {
        throw "-Mailbox must be a single address such as you@gmail.com; got '$Mailbox'."
    }
    # Everything after the first + is ignored by the mailbox, so a second one is not needed.
    $separator = '+'
    if ($parts[0].Contains('+')) { $separator = '.' }
    return "$($parts[0])$separator$Tag@$($parts[1])"
}

function New-SbpStaffSpecs {
    # One receptionist in Ho Chi Minh City (south store) and one technician in Ha Noi (north
    # store), so every run touches both regions.
    #
    # Names and streets are JSON \u escapes so this file stays ASCII. The server decodes them,
    # so the letter carries real Vietnamese text - which Test-SbpWelcomeLetter then confirms.
    param([string] $Stamp, [string] $Purpose = 'seed', [string] $Mailbox)

    # HHmmss from the stamp keeps each run's phone numbers distinct as well.
    $clock = $Stamp.Substring(8)
    return @(
        [pscustomobject]@{
            Label        = 'Receptionist, Ho Chi Minh City'
            Role         = 'RECEPTIONIST'
            Branch       = 'HCM'
            Store        = 'SOUTH'
            Database     = 'sbp_south'
            UriVariable  = 'MONGO_URI_SOUTH'
            Email        = New-SbpStaffAddress -Tag "$Purpose.rec.$Stamp" -Mailbox $Mailbox
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
            Email        = New-SbpStaffAddress -Tag "$Purpose.tec.$Stamp" -Mailbox $Mailbox
            Phone        = "090$($clock)2"
            NameJson     = 'Nguy\u1ec5n V\u0103n H\u00f9ng'
            StreetNumber = '25'
            StreetJson   = 'Tr\u00e0ng Ti\u1ec1n'
            WardJson     = 'Ho\u00e0n Ki\u1ebfm'
        }
    )
}

function New-SbpStaffJson {
    # Built by hand rather than with ConvertTo-Json, which would escape the backslash of each
    # \u sequence and send the escape itself instead of the character. Every value here is
    # ASCII these scripts control, with no quotes that would need escaping.
    param($Spec)

    return ('{{"fullName":"{0}","email":"{1}","phoneNumber":"{2}","staffRole":"{3}",' +
        '"address":{{"streetNumber":"{4}","streetName":"{5}","ward":"{6}","provinceCode":"{7}"}},' +
        '"branchProvinceCode":"{7}"}}') -f $Spec.NameJson, $Spec.Email, $Spec.Phone, $Spec.Role,
        $Spec.StreetNumber, $Spec.StreetJson, $Spec.WardJson, $Spec.Branch
}

# --- Letters ---------------------------------------------------------------------------

function Find-SbpWelcomeLetters {
    # Every letter FileWritingEmailClient wrote for this address, newest first. It names each
    # file <timestamp>_<address with unsafe characters replaced>.eml.
    param([string] $Outbox, [string] $Email)

    if (-not $Outbox -or -not (Test-Path -LiteralPath $Outbox -PathType Container)) { return ,@() }
    $suffix = '_' + ($Email -replace '[^A-Za-z0-9.-]', '_') + '.eml'
    return ,@(Get-ChildItem -LiteralPath $Outbox -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name.EndsWith($suffix) } |
        Sort-Object LastWriteTime -Descending)
}

function Write-SbpField {
    param([string] $Name, [string] $Value, [string] $Colour = 'Gray')
    Write-Host ('  {0,-20} {1}' -f $Name, $Value) -ForegroundColor $Colour
}

function Test-SbpWelcomeLetter {
    # Checks one letter carries the five things Req 3.1.S5 asks for, printing a line each, and
    # returns whether all five are there. Nothing printed contains the password.
    param($File, $Spec)

    # Headers, one blank line, body. The body is where every check belongs: the To header
    # alone would pass the address check without proving the letter mentions it.
    $text = [IO.File]::ReadAllText($File.FullName, [Text.Encoding]::UTF8)
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

    $allPresent = $true
    foreach ($check in $checks.GetEnumerator()) {
        if ($check.Value) {
            Write-Host ('    {0,-26} ok' -f $check.Key) -ForegroundColor Green
        }
        else {
            Write-Host ('    {0,-26} MISSING' -f $check.Key) -ForegroundColor Red
            $allPresent = $false
        }
    }
    if ($url -match 'localhost') {
        Write-Host "    The sign-in URL is still the placeholder $url." -ForegroundColor Yellow
        Write-Host '    Set EMAIL_STAFF_SIGN_IN_URL to SMP''s real sign-in page before a demo.' -ForegroundColor Yellow
    }
    return $allPresent
}

function Read-SbpSharedText {
    # A whole text file read as UTF-8 while another process may still be writing it - the
    # running backend's log. File.ReadAllText opens with a read-only share, which Windows
    # refuses outright while a writer holds the file open.
    param([string] $Path)

    $stream = New-Object IO.FileStream($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read,
        ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
    try {
        $reader = New-Object IO.StreamReader($stream, [Text.Encoding]::UTF8)
        return $reader.ReadToEnd()
    }
    finally {
        $stream.Dispose()
    }
}

function Get-SbpLetterPassword {
    # The initial password a letter carries, so a script can search logs for it. Never print it.
    param($File)

    $text = [IO.File]::ReadAllText($File.FullName, [Text.Encoding]::UTF8)
    $match = [regex]::Match($text, '(?m)^\s*Password:\s+(\S+)')
    if ($match.Success) { return $match.Groups[1].Value }
    return $null
}

# --- The stores, through mongosh -------------------------------------------------------

function Invoke-SbpMongo {
    # Runs one mongosh --eval against the store behind UriVariable and returns its output
    # lines - or $null when it cannot run here: mongosh missing, the URI variable unset in this
    # session, or a .cmd/.bat shim, which runs through cmd.exe and would split an Atlas URI at
    # its '&'. The script must use single quotes only, so it reaches mongosh unchanged.
    param([string] $UriVariable, [string] $Script)

    $uri = [Environment]::GetEnvironmentVariable($UriVariable)
    $mongosh = Get-Command mongosh -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $uri -or -not $mongosh -or $mongosh.Path -match '\.(cmd|bat)$') { return $null }

    $output = @(& $mongosh.Path $uri --quiet --eval $Script)
    if ($LASTEXITCODE -ne 0) {
        throw "mongosh exited with $LASTEXITCODE while reading the store behind $UriVariable."
    }
    return ,$output
}

function Get-SbpNotificationRow {
    # The notification row for one staff address, as a parsed object. Available is false when
    # mongosh cannot run here; Row is $null when it ran and found nothing. The row is looked up
    # by address: its _id is an ObjectId these scripts never see.
    param($Spec)

    $js = "const d=db.getSiblingDB('$($Spec.Database)');" +
        "print(JSON.stringify(d.notifications.findOne({recipientAddress:'$($Spec.Email)'}," +
        '{_id:0,recipientType:1,type:1,deliveryChannel:1,deliveryStatus:1,attemptCount:1,' +
        'homeRegion:1})))'

    $lines = Invoke-SbpMongo -UriVariable $Spec.UriVariable -Script $js
    if ($null -eq $lines) { return [pscustomobject]@{ Available = $false; Row = $null; Query = $js } }

    $json = ($lines -join "`n").Trim()
    $row = $null
    if ($json -and $json -ne 'null') { $row = $json | ConvertFrom-Json }
    return [pscustomobject]@{ Available = $true; Row = $row; Query = $js }
}

function Show-SbpStoredRecords {
    # Prints the Notification row and the Shard Directory entry for one staff member, read
    # from the store its branch belongs to - or the query to run by hand when mongosh cannot.
    param($Spec, [string] $StaffId)

    $js = "const d=db.getSiblingDB('$($Spec.Database)');" +
        "printjson(d.notifications.find({recipientAddress:'$($Spec.Email)'}," +
        '{_id:0,recipientType:1,type:1,deliveryChannel:1,deliveryStatus:1,attemptCount:1,' +
        'sentAt:1,homeRegion:1}).toArray());'
    # A 502 carries no staff id, so there is no directory entry to name.
    if ($StaffId) { $js += "printjson(d.shardDirectory.findOne({_id:'STAFF:$StaffId'}));" }

    try {
        $lines = Invoke-SbpMongo -UriVariable $Spec.UriVariable -Script $js
    }
    catch {
        Write-Host "    $($_.Exception.Message) Run the query below by hand." -ForegroundColor Yellow
        $lines = $null
    }

    if ($null -eq $lines) {
        Write-SbpField 'stored records' "run this in mongosh against the $($Spec.Store) cluster:" 'Yellow'
        if (-not [Environment]::GetEnvironmentVariable($Spec.UriVariable)) {
            Write-Host "    ($($Spec.UriVariable) is not set in this session)" -ForegroundColor Yellow
        }
        Write-Host "    $js"
        return
    }
    Write-SbpField 'stored records' "$($Spec.Database), via mongosh"
    foreach ($line in $lines) { Write-Host "    $line" }
}

Export-ModuleMember -Function Read-SbpDotEnvValue, Resolve-SbpAdminCredential, Resolve-SbpOutbox,
    Invoke-SbpApi, Get-SbpErrorCode, Connect-SbpAdmin, New-SbpStaffAddress, New-SbpStaffSpecs,
    New-SbpStaffJson, Find-SbpWelcomeLetters, Write-SbpField, Test-SbpWelcomeLetter,
    Read-SbpSharedText, Get-SbpLetterPassword, Invoke-SbpMongo, Get-SbpNotificationRow,
    Show-SbpStoredRecords
