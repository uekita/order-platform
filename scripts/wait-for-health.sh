#!/usr/bin/env sh
# Wait for an HTTP health endpoint. Used by local scripts and CI before starting tests.
set -eu

HEALTH_URL="${HEALTH_URL:-http://localhost:8080/actuator/health}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-120}"
SLEEP_SECONDS="${SLEEP_SECONDS:-2}"

attempt=1
while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
  if [ -n "${HEALTH_AUTHORIZATION:-}" ]; then
    body=$(curl -fsS --max-time 3 -H "Authorization: $HEALTH_AUTHORIZATION" "$HEALTH_URL" 2>/dev/null) || body=""
  else
    body=$(curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null) || body=""
  fi
  case "$body" in
    *'"status":"UP"'*|*'"status": "UP"'*)
      echo "Health endpoint is UP after $attempt attempt(s): $HEALTH_URL"
      exit 0
      ;;
  esac
  echo "Waiting for $HEALTH_URL (attempt $attempt/$MAX_ATTEMPTS)..."
  attempt=$((attempt + 1))
  sleep "$SLEEP_SECONDS"
done

echo "Health endpoint did not become UP: $HEALTH_URL" >&2
exit 1
