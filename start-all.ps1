# ==============================================================================
# GoRide DS03 - One-Click Full Stack Startup Script (Backend + Infra + Frontend)
# ==============================================================================
$ErrorActionPreference = "Stop"
$RepoRoot = $PSScriptRoot
Set-Location $RepoRoot

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "         GoRide DS03 // KHOI DONG TOAN BO HE THONG FULL         " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# 1. Kiem tra Docker Daemon
Write-Host "`n>>> [1/4] Kiem tra Docker Engine..." -ForegroundColor Yellow
try {
    docker info 2>&1 | Out-Null
    Write-Host "    [OK] Docker Engine dang hoat dong san sang." -ForegroundColor Green
} catch {
    Write-Host "    [!] Docker Desktop chua bat! Dang mo Docker Desktop tren Windows..." -ForegroundColor Yellow
    if (Test-Path "C:\Program Files\Docker\Docker\Docker Desktop.exe") {
        Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
        Write-Host "    Dang cho Docker Engine khoi dong (khoang 20-30 giay)..." -ForegroundColor Yellow
        $dockerReady = $false
        for ($i = 0; $i -lt 15; $i++) {
            Start-Sleep -Seconds 3
            try {
                docker info 2>&1 | Out-Null
                $dockerReady = $true
                break
            } catch {}
        }
        if (-not $dockerReady) {
            Write-Host "    [!] Vui long click mo ung dung Docker Desktop tren man hinh roi chay lai script nay!" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "    [!] Khong tim thay Docker Desktop tai C:\Program Files\Docker\Docker. Vui long bat thu cong." -ForegroundColor Red
        exit 1
    }
}

# 2. Build & Khoi dong Backend Containers qua Docker Compose
Write-Host "`n>>> [2/4] Khoi dong 7 Microservices + 3 Ha tang (Postgres, RabbitMQ, Redis)..." -ForegroundColor Yellow
docker compose -f deploy/docker-compose.yml up --build -d

# 3. Cho API Gateway san sang va Seed Demo Data
Write-Host "`n>>> [3/4] Cho API Gateway khoi dong va nap du lieu mau (Seed Data)..." -ForegroundColor Yellow
$gatewayReady = $false
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Seconds 2
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 2
        if ($resp.StatusCode -eq 200) {
            $gatewayReady = $true
            break
        }
    } catch {}
}

if ($gatewayReady) {
    Write-Host "    [OK] API Gateway da hoat dong tai http://localhost:8080." -ForegroundColor Green
    & "$RepoRoot\scripts\seed-demo-data.ps1"
} else {
    Write-Host "    [CANH BAO] API Gateway van dang khoi dong, bo qua auto-seed." -ForegroundColor Yellow
}

# 4. Khoi dong Frontend Demo UI
Write-Host "`n>>> [4/4] Mo Frontend Demo Client tai http://localhost:3000..." -ForegroundColor Yellow
Write-Host "=================================================================" -ForegroundColor Green
Write-Host " HE THONG DA HOAN TAT KHOI DONG THANH CONG!" -ForegroundColor Green
Write-Host " - Web UI Flow Simulator:  http://localhost:3000" -ForegroundColor White
Write-Host " - API Gateway:            http://localhost:8080" -ForegroundColor White
Write-Host " - RabbitMQ Management:    http://localhost:15672 (user: goride_admin / pass: goride_rabbit_password)" -ForegroundColor White
Write-Host " - Postgres Database:      localhost:5432" -ForegroundColor White
Write-Host "=================================================================" -ForegroundColor Green

Set-Location "$RepoRoot\frontend\demo-client"
npm run dev
