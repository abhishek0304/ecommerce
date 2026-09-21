# Disposable local smoke test. Does not use the main Compose project's data.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$compose = @('compose','-f','infra/compose.smoke.json')
try {
  & docker @compose up -d --wait --wait-timeout 240
  if ($LASTEXITCODE -ne 0) { throw 'Smoke stack did not become healthy' }
  $products = Invoke-RestMethod 'http://localhost:18083/api/v1/products'
  $id = $products.content[0].id
  if (-not $id) { throw 'Default products were not seeded' }
  $first = Invoke-RestMethod "http://localhost:18083/api/v1/products/$id"
  $second = Invoke-RestMethod "http://localhost:18083/api/v1/products/$id"
  $keys = & docker @compose exec -T -e REDISCLI_AUTH=smoke-only redis redis-cli DBSIZE
  if ($LASTEXITCODE -ne 0 -or [int]$keys -lt 1) { throw 'Product was not stored in Redis' }
  # Simulate a committed inventory version change in the disposable database.
  & docker @compose exec -T -e MYSQL_PWD=smoke-only database mysql -uproduct productdb -e "UPDATE products SET stock_quantity=stock_quantity-1, version=version+1 WHERE id=$id"
  if ($LASTEXITCODE -ne 0) { throw 'Could not update smoke inventory' }
  $updated = Invoke-RestMethod "http://localhost:18083/api/v1/products/$id"
  if ($updated.stockQuantity -ne ($first.stockQuantity - 1)) { throw 'Cache returned old inventory' }
  & docker @compose stop redis
  $fallback = Invoke-RestMethod "http://localhost:18083/api/v1/products/$id"
  if ($fallback.stockQuantity -ne $updated.stockQuantity) { throw 'Redis failure did not fall back to MySQL' }
  foreach ($port in @(19090,19091)) {
    $health = Invoke-RestMethod "http://localhost:$port/actuator/health/readiness"
    if ($health.status -ne 'UP') { throw "Readiness failed on $port" }
  }
  Write-Host 'PASS: real MySQL, Redis caching/version changes/outage fallback, product/cart startup, readiness endpoints.'
} finally {
  & docker @compose down --volumes
}
