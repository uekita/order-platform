#!/usr/bin/env bash
# Single entry point for every CI invocation. Keeping argument construction in
# one script prevents the reusable workflow from drifting between environments.
set -euo pipefail
cd "$(dirname "$0")/.."

TEST_ENV="${TEST_ENV:-local}"
TAG_EXPRESSION="${TAG_EXPRESSION:-}"
EXCLUDED_GROUPS="${EXCLUDED_GROUPS-localonly}"
THREADS="${THREADS:-4}"
PARALLEL="${PARALLEL:-true}"

if ! [[ "$THREADS" =~ ^[0-9]+$ ]] || (( THREADS < 1 || THREADS > 16 )); then
  echo "::error::THREADS must be an integer between 1 and 16 (got: $THREADS)"
  exit 2
fi
if [[ "$PARALLEL" != "true" && "$PARALLEL" != "false" ]]; then
  echo "::error::PARALLEL must be true or false (got: $PARALLEL)"
  exit 2
fi
if [[ "$EXCLUDED_GROUPS" == "__none__" ]]; then
  EXCLUDED_GROUPS=""
fi
if [[ -n "$TAG_EXPRESSION" && ! "$TAG_EXPRESSION" =~ ^[A-Za-z0-9_\!\&\|\(\)\.\ -]+$ ]]; then
  echo "::error::TAG_EXPRESSION contains unsupported characters: $TAG_EXPRESSION"
  exit 2
fi

ARGS=(
  test
  "-Dthreads=$THREADS"
  "-Dparallel=$PARALLEL"
  "-Denv=$TEST_ENV"
  "-DexcludedGroups=$EXCLUDED_GROUPS"
)
if [[ -n "$TAG_EXPRESSION" ]]; then
  ARGS+=("-Dgroups=$TAG_EXPRESSION")
fi

echo "Environment: $TEST_ENV"
echo "Tag expression: ${TAG_EXPRESSION:-<all>}"
echo "Excluded tags: ${EXCLUDED_GROUPS:-<none>}"
echo "Parallel: $PARALLEL, threads: $THREADS"
echo "Command: mvn -B -f qa/backend-test-automation/pom.xml ${ARGS[*]}"

mvn -B -f qa/backend-test-automation/pom.xml "${ARGS[@]}"
