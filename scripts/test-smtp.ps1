param(
    [Parameter(Mandatory = $true)][string]$Recipient
)
$ErrorActionPreference = 'Stop'
$settings = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $PSScriptRoot '../.env')) {
    if ($line -match '^([A-Z_]+)=(.*)$') {
        $value = $matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith("'") -and $value.EndsWith("'")) -or ($value.StartsWith('"') -and $value.EndsWith('"')))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $settings[$matches[1]] = $value
    }
}
foreach ($key in @('SMTP_HOST','SMTP_PORT','SMTP_USERNAME','SMTP_PASSWORD','EMAIL_FROM')) {
    if ([string]::IsNullOrWhiteSpace($settings[$key])) { throw "Missing $key in .env" }
}
if ($settings['SMTP_SSL'] -eq 'true') { throw 'This diagnostic supports STARTTLS, not implicit TLS.' }
$client = New-Object System.Net.Mail.SmtpClient($settings['SMTP_HOST'], [int]$settings['SMTP_PORT'])
$message = New-Object System.Net.Mail.MailMessage($settings['EMAIL_FROM'], $Recipient)
try {
    $client.EnableSsl = $settings['SMTP_STARTTLS'] -eq 'true'
    $client.Timeout = 20000
    $client.UseDefaultCredentials = $false
    $client.Credentials = New-Object System.Net.NetworkCredential($settings['SMTP_USERNAME'], $settings['SMTP_PASSWORD'])
    $message.Subject = 'Ecommerce SMTP verification'
    $message.Body = 'This is the requested SMTP verification email from your ecommerce project. If you received this message, Gmail accepted and delivered the test email. No account password or verification code is included.'
    $client.Send($message)
    Write-Output 'SMTP_ACCEPTED: Check the recipient inbox and spam folder to confirm receipt.'
} catch {
    # Do not print exception details that could include server or credential data.
    $failure = $_.Exception
    while ($failure.InnerException) { $failure = $failure.InnerException }
    Write-Output ('SMTP test failed: ' + $failure.GetType().Name + '. Check connectivity and Gmail app-password configuration. No automatic retry was performed.')
    exit 1
} finally {
    $message.Dispose()
    $client.Dispose()
}
