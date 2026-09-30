$ErrorActionPreference = 'Stop'
$destination = Join-Path $PSScriptRoot '../.env'
if (Test-Path -LiteralPath $destination) { throw '.env exists; preserve existing credentials. Edit it manually if needed.' }
$names = @('JWT_SECRET','INTERNAL_SERVICE_KEY','REDIS_PASSWORD','USER_DB_PASSWORD','PRODUCT_DB_PASSWORD','CART_DB_PASSWORD','ORDER_DB_PASSWORD','NOTIFICATION_DB_PASSWORD')
$lines = foreach ($name in $names) {
  $bytes = New-Object byte[] 32
  $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
  $rng.GetBytes($bytes)
  $rng.Dispose()
  "$name=$([BitConverter]::ToString($bytes).Replace('-','').ToLowerInvariant())"
}
$lines += @('RAZORPAY_KEY_ID=','RAZORPAY_KEY_SECRET=','RAZORPAY_WEBHOOK_SECRET=')
$lines += Get-Content (Join-Path $PSScriptRoot '../notification-service/providers.env.example')
$lines += Get-Content (Join-Path $PSScriptRoot '../order-service/shipping.env.example')
[IO.File]::WriteAllLines($destination, $lines)
Write-Host 'Created .env with random local credentials. Add Razorpay test keys for online checkout.'

