#!/usr/bin/env bash
# Regenerates blessed/ from the current YAML specs using the ranges and timestamp
# recorded in blessed/manifest.json. With unchanged specs the result is byte-identical,
# so `git status --short blessed/` is empty after a no-op run.
#
# Usage: scripts/bless.sh [--version X.Y.Z] [--sha <git-sha>]
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(jq -r '.release_version.semantic' blessed/manifest.json)
SHA=$(jq -r '.release_version.git_sha' blessed/manifest.json)
GENERATED_AT=$(jq -r '.blessed_at' blessed/manifest.json)
while [ $# -gt 0 ]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --sha) SHA="$2"; shift 2 ;;
    --generated-at) GENERATED_AT="$2"; shift 2 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

./gradlew :tools:installDist --quiet
TOOLS=tools/build/install/tools/bin/tools

for CAL_ID in $(jq -r '.calendars | keys[]' blessed/manifest.json); do
  RANGE_START=$(jq -r ".calendars[\"$CAL_ID\"].range_start" blessed/manifest.json)
  RANGE_END=$(jq -r ".calendars[\"$CAL_ID\"].range_end" blessed/manifest.json)
  echo "Generating $CAL_ID ($RANGE_START to $RANGE_END)"
  "$TOOLS" generate "$CAL_ID" \
    --from "$RANGE_START" --to "$RANGE_END" \
    --out "blessed/$CAL_ID" \
    --source-version "$SHA" --release-version "$VERSION" \
    --generated-at "$GENERATED_AT" \
    --include-specs > /dev/null
  if command -v sha256sum > /dev/null; then
    CHECKSUM=$(sha256sum "blessed/$CAL_ID/events.csv" | cut -d' ' -f1)
  else
    CHECKSUM=$(shasum -a 256 "blessed/$CAL_ID/events.csv" | cut -d' ' -f1)
  fi
  EVENT_COUNT=$(tail -n +2 "blessed/$CAL_ID/events.csv" | grep -c . || echo 0)
  jq --arg cal "$CAL_ID" --arg checksum "sha256:$CHECKSUM" --arg count "$EVENT_COUNT" \
     '.calendars[$cal].checksum = $checksum | .calendars[$cal].event_count = ($count | tonumber)' \
     blessed/manifest.json > blessed/manifest.json.tmp
  mv blessed/manifest.json.tmp blessed/manifest.json
done
echo "Done. Review with: git status --short blessed/"
