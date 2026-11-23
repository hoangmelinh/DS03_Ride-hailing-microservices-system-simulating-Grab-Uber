# ==============================================================================
# GoRide DS03 - Seed Demo Passenger and Driver Data via REST API
# ==============================================================================
$GatewayUrl = "http://localhost:8080"

Write-Host ">>> [1/4] Dang ky tai khoan Passenger (u_hoang_01)..." -ForegroundColor Cyan
$passengerPayload = @{
    phone = "0901234567"
    email = "passenger@goride.com"
    password = "Password123!"
    fullName = "Hoang Thanh Vien A"
    role = "PASSENGER"
} | ConvertTo-Json

try {
    $pRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/register" -Method Post -Body $passengerPayload -ContentType "application/json"
    Write-Host "    [OK] Passenger ID: $($pRes.data.user.id)" -ForegroundColor Green
} catch {
    Write-Host "    [INFO] Passenger da ton tai trong he thong." -ForegroundColor Yellow
}

Write-Host ">>> [2/4] Dang ky & Dang nhap tai khoan Driver (d_dang_01)..." -ForegroundColor Cyan
$driverUserPayload = @{
    phone = "0907654321"
    email = "driver@goride.com"
    password = "Password123!"
    fullName = "Le Hai Dang"
    role = "DRIVER"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/register" -Method Post -Body $driverUserPayload -ContentType "application/json" | Out-Null
    Write-Host "    [OK] Dang ky Driver thanh cong." -ForegroundColor Green
} catch {
    Write-Host "    [INFO] Driver User da ton tai." -ForegroundColor Yellow
}

# Dang nhap lay Token
$loginRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/login" -Method Post -Body (@{ email = "driver@goride.com"; password = "Password123!" } | ConvertTo-Json) -ContentType "application/json"
$token = $loginRes.data.accessToken
$driverUserId = $loginRes.data.user.id
$authHeaders = @{
    "Authorization" = "Bearer $token"
    "Content-Type"  = "application/json"
}

Write-Host ">>> [3/4] Dang ky thong tin xe & Ho so Driver..." -ForegroundColor Cyan
$registerDriverPayload = @{
    userId = $driverUserId
    licenseNumber = "GPLX-998877"
    vehiclePlate = "29A-888.88"
    vehicleModel = "Honda City"
} | ConvertTo-Json

$driverId = $null
try {
    $regRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/drivers" -Method Post -Headers $authHeaders -Body $registerDriverPayload
    $driverId = $regRes.data.id
    Write-Host "    [OK] Driver Profile ID: $driverId" -ForegroundColor Green
} catch {
    # Lay driverId tu DB neu da tao
    $driverId = docker exec goride-postgres psql -U goride_admin -d goride_driver -t -A -c "SELECT id FROM drivers WHERE user_id = '$driverUserId' LIMIT 1"
    Write-Host "    [INFO] Ho so Driver da co san (ID: $driverId)." -ForegroundColor Yellow
}

Write-Host ">>> [4/4] Bat trang thai ONLINE va cap nhat toa do gan DH Bach Khoa..." -ForegroundColor Cyan
if ($driverId) {
    try {
        Invoke-RestMethod -Uri "$GatewayUrl/api/v1/drivers/$driverId/status" -Method Patch -Headers $authHeaders -Body (@{ status = "ONLINE" } | ConvertTo-Json) | Out-Null
        Invoke-RestMethod -Uri "$GatewayUrl/api/v1/drivers/$driverId/location" -Method Put -Headers $authHeaders -Body (@{ latitude = 21.0060; longitude = 105.8440 } | ConvertTo-Json) | Out-Null
        Write-Host "    [OK] Driver $driverId da ONLINE tai toa do (21.0060, 105.8440)!" -ForegroundColor Green
    } catch {
        Write-Host "    [ERROR] Khong the cap nhat status/location: $_" -ForegroundColor Red
    }
}

Write-Host ">>> Seed du lieu demo thanh cong 100%!" -ForegroundColor Green
