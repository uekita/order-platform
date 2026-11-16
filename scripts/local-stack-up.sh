#!/usr/bin/env sh
# Bring up the local Postgres + Redpanda + order-service stack and wait for readiness.
set -eu
cd "$(dirname "$0")/.."

docker compose up -d --build --wait
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/actuator/health}" ./scripts/wait-for-health.sh
echo "Local stack is ready. API: http://localhost:8080  Redpanda Console: http://localhost:8081"
