#!/usr/bin/env bash
# sbx-saas.sh — sbx lifecycle wrapper for kotlin-saas-template.
#
# Bakes in the project's sbx defaults so you don't retype them every time:
# name prefix, memory, CPUs, sibling starter mount, and STARTER_PATH.
#
# Usage: ./sbx-saas.sh [--dry-run|-n] <command> <short-name> [args…]
#
# Commands:
#   up <short>          create sandbox then attach Claude Code
#   run <short>         re-attach Claude Code to existing sandbox
#   shell <short>       open interactive bash shell
#   check <short>       ./gradlew check (full verification gate)
#   bootrun <short>     ./bootrun.sh inside sandbox (app launch)
#   exec <short> -- …  arbitrary command (STARTER_PATH injected)
#   ports <short>       publish 8080/8089/3000 to host (OIDC flow)
#   down <short>        destroy sandbox + branch (confirmed; irreversible)
#
# Overridable env:
#   SBX_NAME_PREFIX   sandbox name prefix            [kt-saas-]
#   SBX_MEM           memory                         [16g]
#   SBX_CPUS          CPU count                      [8]
#   STARTER_PATH      composite build path           [/var/home/serandel/Projects/kotlin-saas-starter]
#   SBX_ASSUME_YES    skip 'down' confirmation       [0]
#   SBX_DRY_RUN       print instead of executing     [0]
set -euo pipefail
cd "$(dirname "$0")"

SBX_NAME_PREFIX="${SBX_NAME_PREFIX:-kt-saas-}"
SBX_MEM="${SBX_MEM:-16g}"
SBX_CPUS="${SBX_CPUS:-8}"
STARTER_PATH="${STARTER_PATH:-/var/home/serandel/Projects/kotlin-saas-starter}"
SBX_ASSUME_YES="${SBX_ASSUME_YES:-0}"
DRY_RUN="${SBX_DRY_RUN:-0}"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

usage() {
    cat <<'EOF'
Usage: ./sbx-saas.sh [--dry-run|-n] <command> <short-name> [args…]

Commands:
  up <short>          create sandbox then attach Claude Code
  run <short>         re-attach Claude Code to existing sandbox
  shell <short>       open interactive bash shell
  check <short>       ./gradlew check (full verification gate)
  bootrun <short>     ./bootrun.sh inside sandbox (app launch)
  exec <short> -- …  arbitrary command (STARTER_PATH injected)
  ports <short>       publish 8080/8089/3000 to host
  down <short>        destroy sandbox + branch (confirmed; irreversible)
  help                show this message

Env overrides:
  SBX_NAME_PREFIX   name prefix            [kt-saas-]
  SBX_MEM           memory                 [16g]
  SBX_CPUS          CPU count              [8]
  STARTER_PATH      composite build path   [/var/home/serandel/Projects/kotlin-saas-starter]
  SBX_ASSUME_YES    skip down prompt       [0]
  SBX_DRY_RUN       print instead of run   [0]
EOF
}

die() {
    echo "error: $*" >&2
    exit 1
}

# Wraps every sbx call: dry-run prints; live run executes.
run_sbx() {
    if [[ "$DRY_RUN" -eq 1 ]]; then
        echo "[dry-run] sbx $*"
    else
        sbx "$@"
    fi
}

# ---------------------------------------------------------------------------
# Parse global flags (before <command>)
# ---------------------------------------------------------------------------

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run|-n) DRY_RUN=1; shift ;;
        -h|--help)    usage; exit 0 ;;
        --)           shift; break ;;
        -*)           die "unknown flag: $1" ;;
        *)            break ;;
    esac
done

COMMAND="${1:-}"
if [[ -z "$COMMAND" ]]; then
    usage
    exit 1
fi
shift

# ---------------------------------------------------------------------------
# Require <short-name> for every command except 'help'
# ---------------------------------------------------------------------------

FULL=""
if [[ "$COMMAND" != "help" ]]; then
    SHORT="${1:-}"
    if [[ -z "$SHORT" ]]; then
        echo "error: <short-name> is required" >&2
        usage
        exit 1
    fi
    shift
    FULL="${SBX_NAME_PREFIX}${SHORT}"
fi

# ---------------------------------------------------------------------------
# Subcommands
# ---------------------------------------------------------------------------

case "$COMMAND" in

    up)
        run_sbx create claude . ../kotlin-saas-starter \
            --branch auto \
            --name "$FULL" \
            -m "$SBX_MEM" \
            --cpus "$SBX_CPUS"
        run_sbx run "$FULL"
        ;;

    run)
        run_sbx run "$FULL" "$@"
        ;;

    shell)
        run_sbx exec "$FULL" bash "$@"
        ;;

    check)
        run_sbx exec \
            -e "STARTER_PATH=$STARTER_PATH" \
            "$FULL" ./gradlew check "$@"
        ;;

    bootrun)
        run_sbx exec \
            -e "STARTER_PATH=$STARTER_PATH" \
            "$FULL" ./bootrun.sh "$@"
        ;;

    exec)
        # Accept optional -- separator before the inner command.
        if [[ "${1:-}" == "--" ]]; then shift; fi
        [[ $# -gt 0 ]] || die "'exec' requires a command after <short-name> [-- cmd…]"
        run_sbx exec \
            -e "STARTER_PATH=$STARTER_PATH" \
            "$FULL" "$@"
        ;;

    ports)
        run_sbx ports "$FULL" \
            --publish 8080:8080/tcp \
            --publish 8089:8089/tcp \
            --publish 3000:3000/tcp \
            "$@"
        ;;

    down)
        echo "Sandbox:  $FULL"
        echo "WARNING:  'sbx rm' permanently deletes the sandbox AND its branch."
        echo "          Only proceed after the PR for this branch has been merged."
        if [[ "${SBX_ASSUME_YES}" -eq 1 ]]; then
            REPLY="y"
        else
            read -r -p "PR merged? Tear down (deletes the branch)? [y/N] " REPLY
        fi
        case "$REPLY" in
            y|Y|yes|YES)
                run_sbx rm "$FULL"
                ;;
            *)
                echo "Aborted. Sandbox left intact."
                exit 0
                ;;
        esac
        ;;

    help)
        usage
        exit 0
        ;;

    *)
        echo "error: unknown command: $COMMAND" >&2
        usage
        exit 1
        ;;
esac
