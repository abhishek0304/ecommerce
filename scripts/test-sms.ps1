param(
    [Parameter(Mandatory=$true)][string]$Recipient,
    [string]$Message = 'Ecommerce SMS verification: this is your requested test message. No action is required.',
    [switch]$TestMode
)
$ErrorActionPreference = 'Stop'
$settings = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $PSScriptRoot '../.env')) {
    if ($line -match '^([A-Z_]+)=(.*)$') {
        $settings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
    }
}
$account = if ($TestMode) { $settings['TWILIO_TEST_ACCOUNT_SID'] } else { $settings['TWILIO_ACCOUNT_SID'] }
$token = if ($TestMode) { $settings['TWILIO_TEST_AUTH_TOKEN'] } else { $settings['TWILIO_AUTH_TOKEN'] }
$sender = if ($TestMode) { '+15005550006' } else { $settings['TWILIO_SMS_FROM'] }
if ($account -cnotmatch '^AC[0-9a-fA-F]{32}$' -or [string]::IsNullOrWhiteSpace($token)) { throw 'Missing or invalid Twilio credentials for the selected mode. TestMode requires TWILIO_TEST_ACCOUNT_SID and TWILIO_TEST_AUTH_TOKEN; no live fallback.' }
if ($Recipient -notmatch '^\+[1-9][0-9]{6,14}$' -or $sender -notmatch '^\+[1-9][0-9]{6,14}$') { throw 'Use international phone numbers.' }
$authorization = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($account + ':' + $token))
$headers = @{Authorization = 'Basic ' + $authorization}
$uri = "https://api.twilio.com/2010-04-01/Accounts/$account/Messages.json"
try {
    $result = Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -ContentType 'application/x-www-form-urlencoded' -Body @{
        From = $sender; To = $Recipient
        Body = $Message
    } -TimeoutSec 20
    Write-Output ('Twilio accepted request. Status: ' + $result.status)
    if ($result.sid -cnotmatch '^SM[0-9a-fA-F]{32}$') { throw 'Unexpected message identifier.' }
    if ($TestMode) {
        Write-Output 'SIMULATED_ACCEPTANCE: Twilio test credentials do not deliver to a phone. No live receipt verified.'
        return
    }
    # Only read status after submission; never automatically repeat a send.
    for ($i=0; $i -lt 4; $i++) {
        Start-Sleep -Seconds 5
        $status = Invoke-RestMethod -Uri ("https://api.twilio.com/2010-04-01/Accounts/$account/Messages/" + $result.sid + '.json') -Headers $headers -TimeoutSec 15
        Write-Output ('Message status: ' + $status.status + '; error code: ' + $status.error_code)
        if ($status.status -in @('delivered','undelivered','failed','canceled')) { break }
    }
} catch {
    $providerCode = 'unavailable'
    try { $providerCode = ($_.ErrorDetails.Message | ConvertFrom-Json).code } catch {}
    Write-Output ('SMS request/status check failed. Provider error code: ' + $providerCode + '. No automatic resend performed.')
    exit 1
}
