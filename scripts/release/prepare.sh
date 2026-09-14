#!/usr/bin/env bash
# Prepare the complete, reviewable contents of a release PR.
set -euo pipefail
cd "$(dirname "$0")/../.."

BASELINE_REF="v11.0.0"
BASELINE_DIR=""
BASELINE_EVIDENCE=""
SOURCE_SHA=$(git rev-parse HEAD)
GENERATED_AT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --baseline-ref) BASELINE_REF="$2"; shift 2 ;;
    --baseline-dir) BASELINE_DIR="$2"; shift 2 ;;
    --baseline-evidence) BASELINE_EVIDENCE="$2"; shift 2 ;;
    --source-sha) SOURCE_SHA="$2"; shift 2 ;;
    --generated-at) GENERATED_AT="$2"; shift 2 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

if [ -z "$GENERATED_AT" ]; then
  echo "--generated-at is required so release generation is reproducible" >&2
  exit 2
fi
case "$SOURCE_SHA" in
  *[!0-9a-f]*|'') echo "--source-sha must be a full Git SHA" >&2; exit 2 ;;
esac
if [ "${#SOURCE_SHA}" -ne 40 ]; then
  echo "--source-sha must be a full Git SHA" >&2
  exit 2
fi
if [ "$SOURCE_SHA" != "$(git rev-parse HEAD)" ]; then
  echo "--source-sha must equal the checked-out HEAD" >&2
  exit 2
fi
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "tracked release inputs must be clean before preparation" >&2
  exit 2
fi

BASELINE_COMMIT=$(git rev-parse "${BASELINE_REF}^{commit}")
if [ -z "$BASELINE_DIR" ] || [ -z "$BASELINE_EVIDENCE" ]; then
  echo "--baseline-dir and --baseline-evidence are required; use the authenticated release asset" >&2
  exit 2
fi
BASELINE_VERSION=$(jq -er '.release_version.semantic' "$BASELINE_DIR/manifest.json")
DATA_VERSION=$(jq -er '.data' release/versions.json)

python3 scripts/release/archive_baseline.py \
  --baseline "$BASELINE_DIR" \
  --evidence "$BASELINE_EVIDENCE"
cp "$BASELINE_EVIDENCE" release/baseline-evidence.json
BASELINE_EVIDENCE=release/baseline-evidence.json

scripts/bless.sh \
  --version "$DATA_VERSION" \
  --sha "$SOURCE_SHA" \
  --generated-at "$GENERATED_AT"

set +e
python3 scripts/release/compare.py \
  --baseline "$BASELINE_DIR" \
  --candidate blessed \
  --baseline-sources "$BASELINE_DIR/sources" \
  --candidate-sources sources \
  --output release/impact.json
COMPARE_EXIT=$?
set -e
if [ "$COMPARE_EXIT" -eq 0 ]; then
  echo "No publishable change compared with $BASELINE_REF"
  exit 10
fi
if [ "$COMPARE_EXIT" -gt 2 ]; then
  exit "$COMPARE_EXIT"
fi

SEVERITY=$(jq -r '.severity' release/impact.json)
EXPECTED_VERSION=$(python3 scripts/release/version_policy.py \
  --baseline "$BASELINE_VERSION" \
  --candidate "$DATA_VERSION" \
  --severity "$SEVERITY" \
  --print)
if [ "$DATA_VERSION" != "$EXPECTED_VERSION" ]; then
  DATA_VERSION="$EXPECTED_VERSION"
  jq --arg version "$DATA_VERSION" '.data = $version' release/versions.json > release/versions.json.tmp
  mv release/versions.json.tmp release/versions.json
  scripts/bless.sh \
    --version "$DATA_VERSION" \
    --sha "$SOURCE_SHA" \
    --generated-at "$GENERATED_AT"
  set +e
  python3 scripts/release/compare.py \
    --baseline "$BASELINE_DIR" \
    --candidate blessed \
    --baseline-sources "$BASELINE_DIR/sources" \
    --candidate-sources sources \
    --output release/impact.json
  COMPARE_EXIT=$?
  set -e
  test "$COMPARE_EXIT" -le 2
fi

python3 scripts/release/version_policy.py \
  --baseline "$BASELINE_VERSION" \
  --candidate "$DATA_VERSION" \
  --severity "$SEVERITY"

# This browser fixture is generated from JointDateStream and must describe the
# exact candidate data, including unresolved-day failures.
./gradlew :tools:test \
  --tests 'com.bdc.site.SettlementParityTest' \
  -DupdateGoldens=true \
  --rerun-tasks
./gradlew :tools:test \
  --tests 'com.bdc.site.SettlementParityTest' \
  --rerun-tasks

python3 scripts/release/descriptor.py \
  --baseline-ref "$BASELINE_REF" \
  --baseline-commit "$BASELINE_COMMIT" \
  --baseline-version "$BASELINE_VERSION" \
  --baseline-evidence "$BASELINE_EVIDENCE" \
  --source-sha "$SOURCE_SHA" \
  --generated-at "$GENERATED_AT" \
  --data-version "$DATA_VERSION"

echo "Prepared data v$DATA_VERSION from $SOURCE_SHA against $BASELINE_REF ($BASELINE_COMMIT)"
