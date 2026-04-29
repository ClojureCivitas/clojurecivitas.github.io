#!/usr/bin/env bash
# jank-lifecycle.sh — manage the jank nREPL process.
#
# Usage:
#   jank-lifecycle.sh start   — start jank if not running, print port
#   jank-lifecycle.sh stop    — stop jank, clean up files
#   jank-lifecycle.sh status  — print port if running, exit 1 if not

set -euo pipefail

# Resolve project root.
#   1. JANQUA_PROJECT_ROOT env var — set by the Lua filter when it auto-starts
#      this script, so the script doesn't have to re-derive what Quarto
#      already told the filter (`quarto.project.directory`).
#   2. Walk up from the caller's cwd looking for an existing `.jank-pid` —
#      this is where a previous session anchored, so manual `stop`/`status`
#      finds the right session even from a subdirectory.
#   3. Fall back to cwd.
#
# We deliberately do NOT walk up from this script's own directory: under a
# Quarto extension symlink the script's own ancestry differs from the user's
# cwd, which used to cause PID/port mismatches.
#
# Filenames (.jank-pid, .jank-nrepl-port, .jank-repl.log) are private to
# Janqua, so the anchor only governs where OUR files live — we never touch
# user-authored files. The PROJECT_ROOT='/' check below is defensive against
# bugs that resolve to a pathological value.
PROJECT_ROOT="${JANQUA_PROJECT_ROOT:-}"

if [ -z "$PROJECT_ROOT" ]; then
    _dir="$(pwd)"
    while [ -n "$_dir" ] && [ "$_dir" != "/" ]; do
        if [ -f "$_dir/.jank-pid" ]; then
            PROJECT_ROOT="$_dir"
            break
        fi
        _dir="$(dirname "$_dir")"
    done
fi

if [ -z "$PROJECT_ROOT" ]; then
    PROJECT_ROOT="$(pwd)"
fi

# Defensive sanity checks before any rm -f operations downstream.
if [ "$PROJECT_ROOT" = "/" ] || [ -z "$PROJECT_ROOT" ]; then
    echo "[jank-lifecycle] ERROR: refusing to operate with PROJECT_ROOT='$PROJECT_ROOT'" >&2
    exit 1
fi

PID_FILE="$PROJECT_ROOT/.jank-pid"
PORT_FILE="$PROJECT_ROOT/.jank-nrepl-port"
LOG_FILE="$PROJECT_ROOT/.jank-repl.log"

# Validate that a string is a positive integer (PID or port).
is_number() {
    [[ "$1" =~ ^[0-9]+$ ]]
}

# Atomic write: write to a tmp file in the same directory, then mv.
# `> file` is non-atomic (truncate-then-write), so a concurrent reader
# can see an empty file mid-update and silently fall through.
atomic_write() {
    local file="$1"
    local content="$2"
    local tmp="${file}.tmp.$$"
    printf '%s\n' "$content" > "$tmp"
    mv -f "$tmp" "$file"
}

# Read and validate a PID from the PID file.
# Returns the PID via stdout, or returns 1 if invalid/missing.
read_pid() {
    if [ ! -f "$PID_FILE" ]; then
        return 1
    fi
    local pid
    pid=$(cat "$PID_FILE")
    if ! is_number "$pid"; then
        echo "WARNING: Corrupt PID file, removing." >&2
        rm -f "$PID_FILE" "$PORT_FILE"
        return 1
    fi
    echo "$pid"
}

# Check if a given PID belongs to a jank-related process.
# Prevents accidentally killing an unrelated process that reused the PID.
#
# The bash-wrapper match is pinned to the exact wrapper string in
# cmd_start: `bash -c 'jank repl < <(tail -f /dev/null)'`. The
# `< <(tail` substring is unique enough that PID recycling onto an
# arbitrary user bash with "jank repl" in its args won't pass.
is_jank_process() {
    local pid="$1"
    local comm
    comm=$(ps -o comm= -p "$pid" 2>/dev/null | tr -d ' ') || return 1
    if [[ "$comm" == "jank" ]]; then
        return 0
    fi
    if [[ "$comm" == "bash" ]]; then
        local args
        args=$(ps -o args= -p "$pid" 2>/dev/null) || return 1
        [[ "$args" == *"jank repl < <(tail"* ]]
        return $?
    fi
    return 1
}

# Check if the jank process recorded in PID_FILE is still alive.
is_running() {
    local pid
    pid=$(read_pid) || return 1
    if kill -0 "$pid" 2>/dev/null && is_jank_process "$pid"; then
        return 0
    fi
    # Stale PID file — process is dead or reused by another program
    rm -f "$PID_FILE" "$PORT_FILE"
    return 1
}

# Discover jank's nREPL port from a running process.
# Uses lsof (cross-platform) with ss as fallback (Linux).
# PID is required: every caller knows the jank PID, and the no-PID
# variants would have to scan every user's listeners (cross-user data
# leak in shared environments).
discover_port() {
    local pid="$1"
    if [ -z "$pid" ] || ! is_number "$pid"; then
        echo "[jank-lifecycle] BUG: discover_port called without a valid PID" >&2
        return 1
    fi

    if command -v lsof >/dev/null 2>&1; then
        local port
        port=$(lsof -a -iTCP -sTCP:LISTEN -nP -p "$pid" 2>/dev/null \
               | awk -F: '/LISTEN/ {print $NF}' | awk '{print $1}' | head -1)
        if [ -n "$port" ] && is_number "$port"; then
            echo "$port"
            return 0
        fi
    fi

    if command -v ss >/dev/null 2>&1; then
        local port
        port=$(ss -tlnp 2>/dev/null | grep "pid=$pid" \
               | awk '{print $4}' | awk -F: '{print $NF}' | head -1)
        if [ -n "$port" ] && is_number "$port"; then
            echo "$port"
            return 0
        fi
    fi

    return 1
}

cmd_start() {
    # Already running?
    if is_running; then
        if [ -f "$PORT_FILE" ]; then
            cat "$PORT_FILE"
            exit 0
        fi
        # PID alive but no port file — rediscover
        local pid
        pid=$(read_pid)
        local port
        port=$(discover_port "$pid") || true
        if [ -n "$port" ]; then
            atomic_write "$PORT_FILE" "$port"
            echo "$port"
            exit 0
        fi
        echo "ERROR: jank running (PID $pid) but cannot discover port" >&2
        exit 1
    fi

    echo "[jank-lifecycle] Starting jank repl..." >&2

    # Start jank repl with stdin held open.
    # tail -f /dev/null blocks forever (cross-platform, unlike sleep infinity).
    # The bash wrapper persists as parent of jank for the session's lifetime;
    # we never store the wrapper PID as the "jank PID".
    # setsid puts the wrapper in its own session with no controlling terminal,
    # so SIGHUP from the user closing their terminal cannot reach it.
    setsid bash -c 'jank repl < <(tail -f /dev/null)' > "$LOG_FILE" 2>&1 &
    local wrapper_pid=$!

    # Wait up to 5s for jank to be forked as a child of the bash wrapper.
    # This is separate from the 45s nREPL-port poll below: jank's process
    # exists long before its REPL is ready to accept connections.
    local jank_pid=""
    for i in $(seq 1 10); do
        jank_pid=$(pgrep -P "$wrapper_pid" -x jank 2>/dev/null || echo "")
        if [ -n "$jank_pid" ]; then
            break
        fi
        if ! kill -0 "$wrapper_pid" 2>/dev/null; then
            echo "[jank-lifecycle] ERROR: bash wrapper died before jank spawned. Check $LOG_FILE" >&2
            exit 1
        fi
        sleep 0.5
    done

    if [ -z "$jank_pid" ]; then
        echo "[jank-lifecycle] ERROR: jank process never spawned within 5s. Check $LOG_FILE" >&2
        kill "$wrapper_pid" 2>/dev/null || true
        pkill -P "$wrapper_pid" 2>/dev/null || true
        exit 1
    fi

    atomic_write "$PID_FILE" "$jank_pid"

    # Start background monitor: cleans up files when jank dies.
    # Only removes files if the PID inside still matches — prevents deleting
    # a newer instance's files if jank crashed and was restarted quickly.
    (
        while kill -0 "$jank_pid" 2>/dev/null; do
            sleep 10
        done
        if [ -f "$PID_FILE" ] && [ "$(cat "$PID_FILE")" = "$jank_pid" ]; then
            rm -f "$PID_FILE" "$PORT_FILE"
        fi
    ) &
    disown

    # Poll for port discovery. The loop is generous because Jank's boot
    # time varies across platforms and versions; a tight timeout would
    # spuriously fail on slower environments.
    local port=""
    for i in $(seq 1 45); do
        sleep 1
        port=$(discover_port "$jank_pid") || true
        if [ -n "$port" ]; then
            atomic_write "$PORT_FILE" "$port"
            echo "[jank-lifecycle] Jank started on port $port (PID $jank_pid)" >&2
            echo "$port"
            exit 0
        fi
        # Check jank hasn't died during startup
        if ! kill -0 "$jank_pid" 2>/dev/null; then
            echo "ERROR: jank process died during startup. Check $LOG_FILE" >&2
            rm -f "$PID_FILE"
            exit 1
        fi
    done

    echo "ERROR: Timed out waiting for jank nREPL to start (45s). Check $LOG_FILE" >&2
    kill "$jank_pid" 2>/dev/null || true
    rm -f "$PID_FILE"
    exit 1
}

# Wait briefly for a process to exit, then SIGKILL if still alive.
# `kill -0` is a no-op signal that only tests liveness — the canonical
# way to poll for process death without affecting the target.
# We use a short grace window so a wedged process (e.g. jank stuck in a
# JIT, signal-mask surprise) doesn't leave a phantom session holding
# the port; in normal operation the loop exits on the first iteration.
escalate_after_sigterm() {
    local pid="$1"
    local i
    for i in 1 2 3 4 5 6 7 8 9 10; do
        kill -0 "$pid" 2>/dev/null || return 0
        sleep 0.1
    done
    kill -9 "$pid" 2>/dev/null || true
}

# Kill a process and its children, with safety checks.
#
# We deliberately do NOT use blanket `pkill -P "$pid"` after killing the
# parent: between killing the parent and pkill running, a recycled PID
# could occupy the parent slot, and we'd kill an innocent process's
# children. Instead, snapshot the children up front, then validate each
# child's command name before killing it individually. Only known
# wrapper children (jank, tail) are eligible; anything else is skipped.
#
# After SIGTERM is sent, we escalate to SIGKILL for any process that
# didn't exit within the grace window. Empirically jank honors SIGTERM,
# but a wedged process would otherwise leave `cmd_stop` reporting
# success while the session lingered.
safe_kill_jank() {
    local pid="$1"

    if ! is_jank_process "$pid"; then
        echo "[jank-lifecycle] WARNING: PID $pid is no longer a jank process, skipping kill." >&2
        return 0
    fi

    # Snapshot children BEFORE killing the parent so the list can't grow
    # to include children of a recycled PID.
    local children
    children=$(pgrep -P "$pid" 2>/dev/null || true)

    kill "$pid" 2>/dev/null || true

    # Re-validate each child's command name immediately before killing.
    # Track which we actually SIGTERMed so escalation only targets
    # processes we initiated shutdown on.
    local killed=("$pid")
    local child cname
    for child in $children; do
        cname=$(ps -o comm= -p "$child" 2>/dev/null | tr -d ' ') || continue
        if [[ "$cname" == "jank" || "$cname" == "tail" ]]; then
            kill "$child" 2>/dev/null || true
            killed+=("$child")
        fi
    done

    local p
    for p in "${killed[@]}"; do
        escalate_after_sigterm "$p"
    done
}

cmd_stop() {
    if [ ! -f "$PID_FILE" ]; then
        echo "[jank-lifecycle] No PID file at $PID_FILE — no Jank session anchored here." >&2
        echo "[jank-lifecycle] If you started Jank from a different directory, run stop from there" >&2
        echo "[jank-lifecycle] (the auto-start announcement printed the exact path)." >&2
        rm -f "$PORT_FILE"
        return 0
    fi

    local pid
    pid=$(read_pid) || {
        rm -f "$PID_FILE" "$PORT_FILE"
        return 0
    }

    echo "[jank-lifecycle] Stopping jank (PID $pid)..." >&2
    safe_kill_jank "$pid"

    rm -f "$PID_FILE" "$PORT_FILE"
    echo "[jank-lifecycle] Jank stopped." >&2
}

cmd_status() {
    if is_running; then
        local pid
        pid=$(read_pid)
        if [ -f "$PORT_FILE" ]; then
            local port
            port=$(cat "$PORT_FILE")
            echo "running on port $port (PID $pid)"
        else
            echo "running (PID $pid, port unknown)"
        fi
        exit 0
    fi

    # Check for untracked jank processes (current user only).
    # `pgrep` returns one PID per line; if multiple untracked janks exist,
    # report the first — passing a multi-line value to discover_port would
    # trip its is_number guard and mangle the output line.
    local pid
    pid=$(pgrep -u "$(id -u)" -x jank 2>/dev/null | head -1 || echo "")
    if [ -n "$pid" ]; then
        local port
        port=$(discover_port "$pid") || true
        echo "running on port ${port:-unknown} (PID $pid, untracked)"
        exit 0
    fi

    echo "not running"
    exit 1
}

case "${1:-}" in
    start)  cmd_start ;;
    stop)   cmd_stop ;;
    status) cmd_status ;;
    *)
        echo "Usage: $0 {start|stop|status}" >&2
        exit 1
        ;;
esac
