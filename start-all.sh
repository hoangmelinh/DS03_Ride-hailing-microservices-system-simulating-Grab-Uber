#!/usr/bin/env bash
set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

echo "================================================================="
echo "         GoRide DS03 // KHOI DONG TOAN BO HE THONG FULL         "
echo "================================================================="

# 1. Kiem tra Docker Daemon
echo ""
echo ">>> [1/4] Kiem tra Docker Engine..."
if ! docker info >/dev/null 2>&1; then
    echo "[!] Docker Engine chua hoat dong! Vui long bat ung dung Docker Desktop tren may cua ban truoc."
    exit 1
fi
echo "    [OK] Docker Engine dang hoat dong san sang."

# 2. Build & Khoi dong Backend Containers qua Docker Compose
echo ""
echo ">>> [2/4] Khoi dong 7 Microservices + 3 Ha tang (Postgres, RabbitMQ, Redis)..."
docker compose -f deploy/docker-compose.yml up --build -d

# 3. Cho API Gateway san sang va Seed Demo Data
echo ""
echo ">>> [3/4] Cho API Gateway khoi dong va nap du lieu mau (Seed Data)..."
GATEWAY_READY=false
for i in {1..20}; do
    sleep 2
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health | grep -q "200"; then
        GATEWAY_READY=true
        break
    fi
done

if [ "$GATEWAY_READY" = true ]; then
    echo "    [OK] API Gateway da hoat dong tai http://localhost:8080."
    chmod +x "$REPO_ROOT/scripts/seed-demo-data.sh" || true
    "$REPO_ROOT/scripts/seed-demo-data.sh"
else
    echo "    [CANH BAO] API Gateway van dang khoi dong, bo qua auto-seed."
fi

# 4. Khoi dong Frontend Demo UI
echo ""
echo ">>> [4/4] Mo Frontend Demo Client tai http://localhost:3000..."
echo "================================================================="
echo " HE THONG DA HOAN TAT KHOI DONG THANH CONG!"
echo " - Web UI Flow Simulator:  http://localhost:3000"
echo " - API Gateway:            http://localhost:8080"
echo " - RabbitMQ Management:    http://localhost:15672 (goride_admin / goride_rabbit_password)"
echo " - Postgres Database:      localhost:5432"
echo "================================================================="

cd "$REPO_ROOT/frontend/demo-client"
npm run dev
