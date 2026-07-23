param([string]$BaseUrl = "http://localhost:8080")

$products = Invoke-RestMethod "$BaseUrl/api/v1/products?size=10&page=0"
$before = Invoke-RestMethod "$BaseUrl/api/v1/inventories?size=10&page=0"
$key = "container-smoke-" + [guid]::NewGuid().ToString("N")
$body = @{ customerId = "d97f2e84-3d38-4b2e-9828-56e641c98b88"; productId = 1; quantity = 1 } | ConvertTo-Json
$order = Invoke-RestMethod "$BaseUrl/api/v1/orders" -Method Post -Headers @{ "Idempotency-Key" = $key } -ContentType "application/json" -Body $body
Start-Sleep -Seconds 6
$after = Invoke-RestMethod "$BaseUrl/api/v1/inventories?size=10&page=0"

if ($products.content.Count -lt 1 -or $order.id -lt 1 -or $after.content[0].availableQuantity -ne ($before.content[0].availableQuantity - 1)) {
    throw "Container smoke test failed"
}

Write-Output "Smoke test passed: order $($order.id), inventory $($before.content[0].availableQuantity) -> $($after.content[0].availableQuantity)"
