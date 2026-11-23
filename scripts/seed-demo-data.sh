#!/usr/bin/env bash
set -e

GATEWAY_URL="http://localhost:8080"

echo ">>> [1/4] Dang ky tai khoan Passenger (u_hoang_01)..."
curl -s -X POST "$GATEWAY_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "0901234567",
    "email": "passenger@goride.com",
    "password": "Password123!",
    "fullName": "Hoang Thanh Vien A",
    "role": "PASSENGER"
  }' > /dev/null 2>&1 || true

echo ">>> [2/4] Dang ky & Dang nhap tai khoan Driver (d_dang_01)..."
curl -s -X POST "$GATEWAY_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "0907654321",
    "email": "driver@goride.com",
    "password": "Password123!",
    "fullName": "Le Hai Dang",
    "role": "DRIVER"
  }' > /dev/null 2>&1 || true

LOGIN_RES=$(curl -s -X POST "$GATEWAY_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "driver@goride.com",
    "password": "Password123!"
  }')

if command -v node >/dev/null 2>&1; then
    TOKEN=$(node -e "try { const d = JSON.parse(process.argv[1]); console.log(d.data?.accessToken || ''); } catch(e) {}" "$LOGIN_RES")
    DRIVER_USER_ID=$(node -e "try { const d = JSON.parse(process.argv[1]); console.log(d.data?.user?.id || ''); } catch(e) {}" "$LOGIN_RES")
fi

# Fallback regex if node is not available
if [ -z "$TOKEN" ]; then
    TOKEN=$(echo "$LOGIN_RES" | grep -oE '"accessToken"\s*:\s*"[^"]+"' | sed -E 's/.*"accessToken"\s*:\s*"([^"]+)".*/\1/')
fi
if [ -z "$DRIVER_USER_ID" ]; then
    DRIVER_USER_ID=$(echo "$LOGIN_RES" | grep -oE '"id"\s*:\s*"[^"]+"' | head -n 1 | sed -E 's/.*"id"\s*:\s*"([^"]+)".*/\1/')
fi

if [ -z "$TOKEN" ]; then
    echo "[!] Khong the dang nhap Driver de lay Token."
    exit 1
fi

echo ">>> [3/4] Dang ky ho so Driver & Phuong tien..."
DRIVER_PROFILE_RES=$(curl -s -X POST "$GATEWAY_URL/api/v1/drivers" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"$DRIVER_USER_ID\",
    \"licenseNumber\": \"GPLX-998877\",
    \"vehicleMake\": \"Honda\",
    \"vehicleModel\": \"City\",
    \"vehicleYear\": 2023,
    \"vehiclePlate\": \"29A-888.88\"
  }")

DRIVER_ID=""
if command -v node >/dev/null 2>&1; then
    DRIVER_ID=$(node -e "try { const d = JSON.parse(process.argv[1]); console.log(d.data?.id || ''); } catch(e) {}" "$DRIVER_PROFILE_RES")
fi
if [ -z "$DRIVER_ID" ]; then
    DRIVER_ID=$(echo "$DRIVER_PROFILE_RES" | grep -oE '"id"\s*:\s*"[^"]+"' | head -n 1 | sed -E 's/.*"id"\s*:\s*"([^"]+)".*/\1/')
fi

if [ -z "$DRIVER_ID" ]; then
    GET_DRIVER_RES=$(curl -s -X GET "$GATEWAY_URL/api/v1/drivers/me" \
      -H "Authorization: Bearer $TOKEN")
    if command -v node >/dev/null 2>&1; then
        DRIVER_ID=$(node -e "try { const d = JSON.parse(process.argv[1]); console.log(d.data?.id || ''); } catch(e) {}" "$GET_DRIVER_RES")
    fi
    if [ -z "$DRIVER_ID" ]; then
        DRIVER_ID=$(echo "$GET_DRIVER_RES" | grep -oE '"id"\s*:\s*"[^"]+"' | head -n 1 | sed -E 's/.*"id"\s*:\s*"([^"]+)".*/\1/')
    fi
fi

echo ">>> [4/4] Bat trang thai AVAILABLE va toa do tai Ha Noi..."
if [ -n "$DRIVER_ID" ]; then
    curl -s -X PUT "$GATEWAY_URL/api/v1/drivers/$DRIVER_ID/status" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d '{
        "status": "AVAILABLE",
        "latitude": 21.0060,
        "longitude": 105.8440
      }' > /dev/null 2>&1 || true
    echo ">>> [HOAN TAT] Driver $DRIVER_ID da SAN SANG (AVAILABLE) tai toa do (21.0060, 105.8440)!"
fi
