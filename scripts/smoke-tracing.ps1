param(
  [string]$GatewayUrl = 'http://localhost:8081',
  [string]$TempoUrl = 'http://localhost:3200'
)
$ErrorActionPreference = 'Stop'
# Read-only probe against an already running stack; creates no product or order data.
$response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 "$GatewayUrl/api/v1/products?page=0&size=1"
$traceId = [string]($response.Headers['X-Trace-Id'] | Select-Object -First 1)
if ($traceId -notmatch '^[0-9a-f]{32}$') { throw 'Gateway did not return a valid X-Trace-Id' }
$deadline = (Get-Date).AddSeconds(60)
do {
  try {
    $trace = Invoke-RestMethod -TimeoutSec 5 -Headers @{Accept='application/json'} "$TempoUrl/api/traces/$traceId"
    $names = @()
    foreach ($batch in @($trace.batches) + @($trace.resourceSpans)) {
      foreach ($attribute in $batch.resource.attributes) {
        if ($attribute.key -eq 'service.name') { $names += $attribute.value.stringValue }
      }
    }
    if ($names -contains 'api-gateway' -and $names -contains 'product-service') {
      Write-Host "PASS: gateway and product-service share trace $traceId"
      Write-Host 'Open http://localhost:3000/explore, select Tempo, and search for this trace ID.'
      exit 0
    }
  } catch { $lastError = $_.Exception.Message }
  Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
throw "Trace $traceId did not contain both services within 60 seconds. Check sampling/export configuration. Last error: $lastError"
