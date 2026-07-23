param(
    [string]$BaseUrl = "http://localhost:18080",
    [int]$Requests = 20
)

$body = '{"customerId":"d97f2e84-3d38-4b2e-9828-56e641c98b88","productId":1,"quantity":1}'
$jobs = 1..$Requests | ForEach-Object {
    Start-Job -ArgumentList $_, $BaseUrl, $body -ScriptBlock {
        param($index, $url, $json)
        $key = "k8s-concurrency-$index-$([guid]::NewGuid().ToString('N'))"
        curl.exe -s -o NUL -w "%{http_code}" -X POST "$url/api/v1/orders" -H "Content-Type: application/json" -H "Idempotency-Key: $key" -d $json
    }
}
$codes = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job
$accepted = @($codes | Where-Object { $_ -eq "201" }).Count
$rejected = @($codes | Where-Object { $_ -eq "409" }).Count
$other = $Requests - $accepted - $rejected
$inventory = Invoke-RestMethod "$BaseUrl/api/v1/inventories?size=10&page=0"

if ($other -ne 0 -or $inventory.content[0].availableQuantity -lt 0) {
    throw "Concurrency smoke test failed: accepted=$accepted rejected=$rejected other=$other available=$($inventory.content[0].availableQuantity)"
}

Write-Output "Concurrency smoke passed: accepted=$accepted rejected=$rejected available=$($inventory.content[0].availableQuantity)"
