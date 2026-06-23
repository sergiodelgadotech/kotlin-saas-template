#!/usr/bin/env bash
# test/test-sbx-saas.sh — plain-bash test harness for sbx-saas.sh.
#
# Uses --dry-run so no real sbx installation or sandbox is needed.
# Runs every test case, reports PASS/FAIL per assertion, exits non-zero if any fail.
set -euo pipefail
cd "$(dirname "$0")/.."

SCRIPT="./sbx-saas.sh"
PASS=0
FAIL=0

# ---------------------------------------------------------------------------
# Assertion helpers
# ---------------------------------------------------------------------------

assert_contains() {
    local label="$1" expected="$2" actual="$3"
    if echo "$actual" | grep -qF -- "$expected"; then
        echo "PASS  $label"
        PASS=$((PASS + 1))
    else
        echo "FAIL  $label"
        echo "      expected substring: $expected"
        echo "      actual output:"
        # shellcheck disable=SC2001
        echo "$actual" | sed 's/^/        /'
        FAIL=$((FAIL + 1))
    fi
}

assert_not_contains() {
    local label="$1" unexpected="$2" actual="$3"
    if echo "$actual" | grep -qF -- "$unexpected"; then
        echo "FAIL  $label (found unexpected substring)"
        echo "      unexpected: $unexpected"
        echo "      actual output:"
        # shellcheck disable=SC2001
        echo "$actual" | sed 's/^/        /'
        FAIL=$((FAIL + 1))
    else
        echo "PASS  $label"
        PASS=$((PASS + 1))
    fi
}

assert_exits_nonzero() {
    local label="$1"; shift
    if "$@" >/dev/null 2>&1; then
        echo "FAIL  $label (expected non-zero exit)"
        FAIL=$((FAIL + 1))
    else
        echo "PASS  $label"
        PASS=$((PASS + 1))
    fi
}

# ---------------------------------------------------------------------------
# up
# ---------------------------------------------------------------------------

echo "--- up ---"
OUT=$(bash "$SCRIPT" --dry-run up demo 2>&1)
assert_contains "up: sbx create" "[dry-run] sbx create claude . ../kotlin-saas-starter" "$OUT"
assert_contains "up: --name kt-saas-demo" "--name kt-saas-demo" "$OUT"
assert_contains "up: --branch auto" "--branch auto" "$OUT"
assert_contains "up: -m 16g" "-m 16g" "$OUT"
assert_contains "up: --cpus 8" "--cpus 8" "$OUT"
assert_contains "up: sbx run kt-saas-demo" "[dry-run] sbx run kt-saas-demo" "$OUT"

# ---------------------------------------------------------------------------
# run
# ---------------------------------------------------------------------------

echo "--- run ---"
OUT=$(bash "$SCRIPT" --dry-run run demo 2>&1)
assert_contains "run: sbx run kt-saas-demo" "[dry-run] sbx run kt-saas-demo" "$OUT"

# ---------------------------------------------------------------------------
# shell
# ---------------------------------------------------------------------------

echo "--- shell ---"
OUT=$(bash "$SCRIPT" --dry-run shell demo 2>&1)
assert_contains "shell: sbx exec ... bash" "[dry-run] sbx exec kt-saas-demo bash" "$OUT"

# ---------------------------------------------------------------------------
# check (STARTER_PATH injected)
# ---------------------------------------------------------------------------

echo "--- check ---"
OUT=$(bash "$SCRIPT" --dry-run check demo 2>&1)
assert_contains "check: -e STARTER_PATH injected" "-e STARTER_PATH=" "$OUT"
assert_contains "check: ./gradlew check" "./gradlew check" "$OUT"
assert_contains "check: sandbox name" "kt-saas-demo" "$OUT"

# ---------------------------------------------------------------------------
# bootrun (STARTER_PATH injected)
# ---------------------------------------------------------------------------

echo "--- bootrun ---"
OUT=$(bash "$SCRIPT" --dry-run bootrun demo 2>&1)
assert_contains "bootrun: -e STARTER_PATH injected" "-e STARTER_PATH=" "$OUT"
assert_contains "bootrun: ./bootrun.sh" "./bootrun.sh" "$OUT"

OUT=$(bash "$SCRIPT" --dry-run bootrun demo --args='--spring.profiles.active=local' 2>&1)
assert_contains "bootrun: extra args pass through" "--args=" "$OUT"

# ---------------------------------------------------------------------------
# the 'exec' subcommand (STARTER_PATH injected, optional -- separator)
# ---------------------------------------------------------------------------

echo "--- sbx-exec ---"
OUT=$(bash "$SCRIPT" --dry-run exec demo -- ./gradlew :app:test 2>&1)
assert_contains "sbx-exec(--): STARTER_PATH injected" "-e STARTER_PATH=" "$OUT"
assert_contains "sbx-exec(--): command arg" "./gradlew" "$OUT"
assert_contains "sbx-exec(--): sandbox name present" "kt-saas-demo" "$OUT"
assert_not_contains "sbx-exec(--): -- not forwarded" "-- ./gradlew" "$OUT"

OUT=$(bash "$SCRIPT" --dry-run exec demo ./gradlew :app:test 2>&1)
assert_contains "sbx-exec(no-sep): STARTER_PATH injected" "-e STARTER_PATH=" "$OUT"
assert_contains "sbx-exec(no-sep): command arg" "./gradlew" "$OUT"

# ---------------------------------------------------------------------------
# ports
# ---------------------------------------------------------------------------

echo "--- ports ---"
OUT=$(bash "$SCRIPT" --dry-run ports demo 2>&1)
assert_contains "ports: 8080" "--publish 8080:8080/tcp" "$OUT"
assert_contains "ports: 8089" "--publish 8089:8089/tcp" "$OUT"
assert_contains "ports: 3000" "--publish 3000:3000/tcp" "$OUT"
assert_contains "ports: sandbox name" "kt-saas-demo" "$OUT"

# ---------------------------------------------------------------------------
# down — confirmation guards
# ---------------------------------------------------------------------------

echo "--- down ---"

OUT=$(echo "y" | bash "$SCRIPT" --dry-run down demo 2>&1)
assert_contains "down(y): sbx rm issued" "[dry-run] sbx rm kt-saas-demo" "$OUT"

OUT=$(echo "n" | bash "$SCRIPT" --dry-run down demo 2>&1)
assert_contains "down(n): aborted message" "Aborted" "$OUT"
assert_not_contains "down(n): no rm issued" "[dry-run] sbx rm" "$OUT"

OUT=$(echo "" | bash "$SCRIPT" --dry-run down demo 2>&1)
assert_contains "down(empty reply): aborted" "Aborted" "$OUT"

OUT=$(SBX_ASSUME_YES=1 bash "$SCRIPT" --dry-run down demo 2>&1)
assert_contains "down(assume-yes): sbx rm issued" "[dry-run] sbx rm kt-saas-demo" "$OUT"

# ---------------------------------------------------------------------------
# Env overrides
# ---------------------------------------------------------------------------

echo "--- env overrides ---"
OUT=$(SBX_NAME_PREFIX="myapp-" bash "$SCRIPT" --dry-run up foo 2>&1)
assert_contains "prefix override: myapp-foo" "myapp-foo" "$OUT"

OUT=$(SBX_MEM="8g" SBX_CPUS="4" bash "$SCRIPT" --dry-run up bar 2>&1)
assert_contains "mem override: -m 8g" "-m 8g" "$OUT"
assert_contains "cpu override: --cpus 4" "--cpus 4" "$OUT"

OUT=$(STARTER_PATH="/custom/path" bash "$SCRIPT" --dry-run check demo 2>&1)
assert_contains "STARTER_PATH override: custom path" "STARTER_PATH=/custom/path" "$OUT"

OUT=$(SBX_DRY_RUN=1 bash "$SCRIPT" run demo 2>&1)
assert_contains "SBX_DRY_RUN env var: run" "[dry-run] sbx run kt-saas-demo" "$OUT"

# ---------------------------------------------------------------------------
# Error cases — must exit non-zero
# ---------------------------------------------------------------------------

echo "--- error cases ---"
assert_exits_nonzero "missing command (no args)" bash "$SCRIPT"
assert_exits_nonzero "missing short-name on up" bash "$SCRIPT" --dry-run up
assert_exits_nonzero "missing short-name on check" bash "$SCRIPT" --dry-run check
assert_exits_nonzero "missing short-name on down" bash "$SCRIPT" --dry-run down
assert_exits_nonzero "exec subcommand missing inner command" bash "$SCRIPT" --dry-run exec demo
assert_exits_nonzero "exec subcommand with only -- separator" bash "$SCRIPT" --dry-run exec demo --
assert_exits_nonzero "unknown command" bash "$SCRIPT" --dry-run bogus demo
assert_exits_nonzero "unknown flag" bash "$SCRIPT" --unknown up demo

# ---------------------------------------------------------------------------
# Help / usage — must exit 0 and print "Commands:"
# ---------------------------------------------------------------------------

echo "--- help ---"
OUT=$(bash "$SCRIPT" help 2>&1) && assert_contains "help cmd: usage shown" "Commands:" "$OUT"
OUT=$(bash "$SCRIPT" --help 2>&1) && assert_contains "--help flag: usage shown" "Commands:" "$OUT"
OUT=$(bash "$SCRIPT" -h 2>&1) && assert_contains "-h flag: usage shown" "Commands:" "$OUT"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

echo ""
echo "Results: $PASS passed, $FAIL failed"
[[ "$FAIL" -eq 0 ]]
