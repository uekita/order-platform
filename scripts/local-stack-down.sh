#!/usr/bin/env sh
# Stop the local stack and remove its data volumes.
set -eu
cd "$(dirname "$0")/.."
docker compose down -v --remove-orphans
