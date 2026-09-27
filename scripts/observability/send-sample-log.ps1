param(
    [string]$TraceId = "local-demo-trace"
)

$event = @{
    "@timestamp" = (Get-Date).ToUniversalTime().ToString("o")
    "log.level" = "INFO"
    "message" = "order accepted"
    "event.action" = "order.accepted"
    "trace.id" = $TraceId
    "http.response.status_code" = 201
    "order.id" = 42
    "product.id" = 7
    "order.quantity" = 1
    "duration.ms" = 18
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081" -Method Post -ContentType "application/json" -Body $event
Write-Host "Sample log sent. Open http://localhost:5601 and create a data view for logs-flashsale-*"
