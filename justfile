# Thin wrappers around the Gradle commands documented in README.md and CLAUDE.md.
# All recipes run from the repository root. Requires https://github.com/casey/just
# Optional: the project works fully without it, using ./gradlew directly.

# Build everything
build:
    ./gradlew :tools:build

# Run all tests
test:
    ./gradlew :tools:test

# Run the fast test suite. A sibling change is adding a :tools:fastTest Gradle task;
# just has no built-in way to fall back to another recipe when a Gradle task is
# missing, so this calls fastTest directly. It will fail with "task not found"
# until that task lands; use `just test` until then.
fast-test:
    ./gradlew :tools:fastTest

# Validate a single calendar
validate ID:
    ./gradlew :tools:run --args="validate {{ID}}"

# Validate every calendar, treating warnings as errors
validate-all:
    ./gradlew :tools:run --args="validate --all --strict"

# Generate events for a calendar over a date range
generate ID FROM TO:
    ./gradlew :tools:run --args="generate {{ID}} --from {{FROM}} --to {{TO}} --out generated/{{ID}}"

# Query a calendar; pass any additional CLI flags after the id
query ID +ARGS:
    ./gradlew :tools:run --args="query {{ID}} {{ARGS}}"

# Regenerate blessed/ from the current specs (no-op leaves git clean)
bless:
    scripts/bless.sh

# Export third-party cross-validation reference CSVs (never run in CI)
reference-export:
    test -d .venv || python3 -m venv .venv
    .venv/bin/pip install -q -r scripts/reference/requirements.txt
    .venv/bin/python scripts/reference/export_reference_calendars.py

# Format Java code (Google Java Format via spotless)
format:
    ./gradlew :tools:spotlessApply
