# Drives POST /api/v1/integration/booking-events through §11.7's own sequence.
#
# Signs in as a customer, picks one of their bookings, then pushes CONFIRMED,
# SCHEDULED, WORKING, a replay, a malformed event and an unknown customer.
#
# Written for Windows PowerShell 5.1: no -SkipHttpErrorCheck, no ?? operator.
#
# Run the app first, and have the frontend open and signed in as the same
# customer in a browser so the toasts are visible.

$ErrorActionPreference = 'Stop'

$BaseUrl = 'http://localhost:8443/api/v1'
$Secret  = 'dev-only-shared-secret'   # fixinh.sbp.smp.webhook-shared-secret

# --- A request helper that prints the status even when it is an error --------

function Send-Probe {
    param(
        [string] $Method,
        [string] $Uri,
        [hashtable] $Headers = @{},
        $Body = $null,
        [switch] $Quiet
    )

    $params = @{
        Method          = $Method
        Uri             = $Uri
        Headers         = $Headers
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body        = $Body
    }

    try {
        $response = Invoke-WebRequest @params
        $status  = [int] $response.StatusCode
        $content = $response.Content
    }
    catch {
        $webResponse = $_.Exception.Response
        if ($null -eq $webResponse) { throw }
        $status = [int] $webResponse.StatusCode
        $reader = New-Object System.IO.StreamReader($webResponse.GetResponseStream())
        $content = $reader.ReadToEnd()
        $reader.Close()
    }

    if (-not $Quiet) { Write-Host "  $status  $content" }
    return [pscustomobject]@{ Status = $status; Content = $content }
}

# --- Sign in, and find a booking to push events about -----------------------

Write-Host "`nSigning in..." -ForegroundColor Cyan

$identifier = Read-Host 'Customer email or phone'
$password   = Read-Host 'Password'

$loginBody = @{ identifier = $identifier; password = $password } | ConvertTo-Json
$login = Send-Probe -Method Post -Uri "$BaseUrl/auth/login" -Body $loginBody -Quiet

if ($login.Status -ne 200) {
    Write-Host "  Sign-in failed: $($login.Status)  $($login.Content)" -ForegroundColor Red
    return
}

$auth       = $login.Content | ConvertFrom-Json
$customerId = $auth.user.userId
$customer   = @{ Authorization = "Bearer $($auth.accessToken)" }

Write-Host "  Signed in as $($auth.user.email)" -ForegroundColor Green
Write-Host "  customerId: $customerId"

$bookings = Send-Probe -Method Get -Uri "$BaseUrl/bookings" -Headers $customer -Quiet
if ($bookings.Status -ne 200) {
    Write-Host "  Could not list bookings: $($bookings.Status)  $($bookings.Content)" -ForegroundColor Red
    return
}

$list = $bookings.Content | ConvertFrom-Json
$all  = @($list.active) + @($list.past) | Where-Object { $null -ne $_ }

if ($all.Count -eq 0) {
    Write-Host "  This customer has no bookings. Make one in the app first," -ForegroundColor Yellow
    Write-Host "  since SBP refreshes a projection it already has and will not invent one." -ForegroundColor Yellow
    return
}

$bookingRef = $all[0].bookingRef
Write-Host "  bookingRef: $bookingRef  (currently $($all[0].status))"

# --- The events -------------------------------------------------------------

$smp = @{ Authorization = "Bearer $Secret" }
$run = Get-Date -Format 'HHmmss'   # so a re-run is not mistaken for a replay

function Send-Event {
    param([string] $EventId, [hashtable] $Payload, [string] $OccurredAt, [string] $CustomerId)

    $body = @{
        eventId    = $EventId
        eventType  = 'BOOKING_STATUS_CHANGED'
        occurredAt = $OccurredAt
        bookingRef = $bookingRef
        customerId = $CustomerId
        payload    = $Payload
    } | ConvertTo-Json -Depth 5

    Send-Probe -Method Post -Uri "$BaseUrl/integration/booking-events" -Headers $smp -Body $body
}

Write-Host "`n1. CONFIRMED  -> 202, one notification, one toast" -ForegroundColor Cyan
Send-Event -EventId "evt-$run-1" -OccurredAt '2026-09-11T10:00:00Z' -CustomerId $customerId `
    -Payload @{ status = 'CONFIRMED' } | Out-Null

Start-Sleep -Milliseconds 800

Write-Host "`n2. SCHEDULED  -> 202, second notification, badge 2, technician assigned" -ForegroundColor Cyan
Send-Event -EventId "evt-$run-2" -OccurredAt '2026-09-11T10:05:00Z' -CustomerId $customerId `
    -Payload @{
        status                   = 'SCHEDULED'
        scheduledStartAt         = '2026-08-24T11:00:00Z'
        estimatedDurationMinutes = 120
        technicians              = @(@{ technicianId = 'TEC-014'; fullName = 'John Tran' })
    } | Out-Null

Start-Sleep -Milliseconds 800

Write-Host "`n3. WORKING    -> 202, projection refreshes, NO notification and NO toast" -ForegroundColor Cyan
Send-Event -EventId "evt-$run-3" -OccurredAt '2026-09-11T10:10:00Z' -CustomerId $customerId `
    -Payload @{ status = 'WORKING' } | Out-Null

Write-Host "`n4. Replay of 2 -> 202 with duplicate: true, and nothing changes" -ForegroundColor Cyan
Send-Event -EventId "evt-$run-2" -OccurredAt '2026-09-11T10:05:00Z' -CustomerId $customerId `
    -Payload @{ status = 'SCHEDULED' } | Out-Null

Write-Host "`n5. Unknown status -> 400 EVENT_MALFORMED naming payload.status" -ForegroundColor Cyan
Send-Event -EventId "evt-$run-5" -OccurredAt '2026-09-11T10:15:00Z' -CustomerId $customerId `
    -Payload @{ status = 'PENDING' } | Out-Null

Write-Host "`n6. Unknown customer -> 404 CUSTOMER_NOT_FOUND" -ForegroundColor Cyan
Send-Event -EventId "evt-$run-6" -OccurredAt '2026-09-11T10:20:00Z' `
    -CustomerId '000000000000000000000000' -Payload @{ status = 'CONFIRMED' } | Out-Null

Write-Host "`n7. No secret -> 403 INTEGRATION_FORBIDDEN" -ForegroundColor Cyan
$body = @{ eventId = "evt-$run-7"; eventType = 'BOOKING_STATUS_CHANGED'
           occurredAt = '2026-09-11T10:25:00Z'; bookingRef = $bookingRef
           customerId = $customerId; payload = @{ status = 'CONFIRMED' } } | ConvertTo-Json -Depth 5
Send-Probe -Method Post -Uri "$BaseUrl/integration/booking-events" -Body $body | Out-Null

Write-Host "`nIn the browser: two toasts from 1 and 2, none from 3, bell badge 2." -ForegroundColor Green
Write-Host "Open $bookingRef and it should show John Tran and the scheduled window.`n" -ForegroundColor Green
