#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

java_version="$({ java -XshowSettings:properties -version; } 2>&1 \
  | awk -F= '/java.specification.version/ {gsub(/[[:space:]]/, "", $2); print $2; exit}')"
if [[ "$java_version" != "21" ]]; then
  echo "Expected Java 21 to match GitHub Pages, found Java ${java_version:-unknown}." >&2
  exit 1
fi

# GitHub's Ubuntu runner uses a 1 MiB JVM thread stack. macOS commonly uses
# 2 MiB, which can hide recursive parser/regex failures until publication.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+${JAVA_TOOL_OPTIONS} }-Xss1m"

log_file="$(mktemp "${TMPDIR:-/tmp}/clay-render.XXXXXX.log")"
trap 'rm -f "$log_file"' EXIT

set +e
clojure -M:clay -A:markdown "$@" 2>&1 | tee "$log_file"
clay_status="${PIPESTATUS[0]}"
set -e

if [[ "$clay_status" -ne 0 ]]; then
  exit "$clay_status"
fi

# Clay reports individual source failures but can still return success.
if grep -q 'Clay FAILED:' "$log_file"; then
  echo "Clay reported source failures despite exiting successfully:" >&2
  grep 'Clay FAILED:' "$log_file" >&2
  if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
    while IFS= read -r failure; do
      echo "::error title=Clay source render failed::${failure}" >&2
    done < <(grep 'Clay FAILED:' "$log_file")
  fi
  exit 1
fi
