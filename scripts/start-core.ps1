# Start the backend sequentially on development machines with limited RAM.
param([switch]$WithTracing)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
function Run-Compose([string[]] $ComposeArgs) {
  & docker compose --profile app --profile observability @ComposeArgs
  if ($LASTEXITCODE -ne 0) { throw "Compose failed: $($ComposeArgs -join ' ')" }
}
# Preserve containers and volumes. Tracing can be enabled explicitly on smaller machines.
Run-Compose -ComposeArgs @('stop','--timeout','20','config-server')
if ($WithTracing) {
  $env:MANAGEMENT_TRACING_ENABLED = 'true'
  Run-Compose -ComposeArgs @('up','-d','tempo','grafana')
} else {
  $env:MANAGEMENT_TRACING_ENABLED = 'false'
  Run-Compose -ComposeArgs @('stop','--timeout','20','grafana','tempo')
}
foreach ($service in @('user-db','product-db','cart-db','order-db','notification-db','redis','kafka','service-registry','product-service','user-service','cart-service','order-service','notification-service','api-gateway')) {
  Write-Host "Starting $service..."
  Run-Compose -ComposeArgs @('up','-d','--no-deps','--wait','--wait-timeout','600',$service)
}
Write-Host 'Backend is healthy. Gateway: http://localhost:8081.'
if ($WithTracing) { Write-Host 'Traces: http://localhost:3000/explore (Tempo).' }
else { Write-Host 'Tracing is disabled. Use -WithTracing to enable Tempo and Grafana.' }
