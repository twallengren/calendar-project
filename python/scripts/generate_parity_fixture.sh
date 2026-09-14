#!/usr/bin/env bash
# Regenerates the parity fixtures in python/tests/fixtures/ from the Java toolchain.
#
# The fixtures record what `tools query --as-of blessed` answers for every
# non-weekend event and for a deterministic sample of dates across each
# calendar's covered range. python/tests/test_parity.py replays them against the
# Python package, so the two implementations are held to the same answers
# without needing a JVM at test time.
#
# Run after re-syncing the bundled data (python/scripts/sync_data.py), and
# commit the result:
#
#     python/scripts/generate_parity_fixture.sh
#
# Optional: BDC_SAMPLES (dates sampled per calendar, default 1000) and a list of
# calendar ids as positional arguments (default: every bundled market).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FIXTURE_DIR="$REPO_ROOT/python/tests/fixtures"

CALENDARS=("$@")
if [ ${#CALENDARS[@]} -eq 0 ]; then
  while IFS= read -r calendar; do
    CALENDARS+=("$calendar")
  done < <(python3 -c 'import json,sys; m=json.load(open(sys.argv[1])); print("\n".join(sorted(key for key,c in m["calendars"].items() if c.get("kind", "market") == "market")))' "$REPO_ROOT/blessed/manifest.json")
fi

echo "Building the Java toolchain..."
(cd "$REPO_ROOT" && ./gradlew :tools:installDist --quiet)

LIB_DIR="$REPO_ROOT/tools/build/install/tools/lib"
if [ ! -d "$LIB_DIR" ]; then
  echo "Expected $LIB_DIR after :tools:installDist" >&2
  exit 1
fi

mkdir -p "$FIXTURE_DIR"
for CAL in "${CALENDARS[@]}"; do
  echo "Dumping $CAL..."
  BDC_CALENDAR="$CAL" \
  BDC_REPO_ROOT="$REPO_ROOT" \
  BDC_OUT="$FIXTURE_DIR/parity_${CAL}.json" \
  BDC_SAMPLES="${BDC_SAMPLES:-1000}" \
    jshell --class-path "$LIB_DIR/*" --feedback concise "$SCRIPT_DIR/generate_parity_fixture.jsh"
  python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); assert d["fixture_schema"] == 2 and d["queries"]' "$FIXTURE_DIR/parity_${CAL}.json"
done

echo
du -h "$FIXTURE_DIR"/parity_*.json
