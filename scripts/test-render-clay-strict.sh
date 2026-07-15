#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture_bin="$(mktemp -d "${TMPDIR:-/tmp}/clay-strict-fixture.XXXXXX")"
output_file="$(mktemp "${TMPDIR:-/tmp}/clay-strict-output.XXXXXX.log")"
trap 'rm -rf "$fixture_bin"; rm -f "$output_file"' EXIT

cat >"$fixture_bin/java" <<'EOF'
#!/usr/bin/env bash
echo '    java.specification.version = 21' >&2
EOF

cat >"$fixture_bin/clojure" <<'EOF'
#!/usr/bin/env bash
echo 'Clay FAILED: src/language_learning/vocabulary_estimation/beta_binomial_first_pass.clj'
echo 'java.lang.StackOverflowError'
exit 0
EOF

chmod +x "$fixture_bin/java" "$fixture_bin/clojure"

set +e
GITHUB_ACTIONS=true PATH="$fixture_bin:$PATH" \
  "$repo_root/scripts/render-clay-strict.sh" >"$output_file" 2>&1
result_status=$?
set -e

if [[ "$result_status" -ne 1 ]]; then
  sed -n '1,120p' "$output_file" >&2
  echo "Expected the strict Clay renderer to reject the historical failure; got status $result_status." >&2
  exit 1
fi

grep -Fq 'java.lang.StackOverflowError' "$output_file"
grep -Fq 'Clay reported source failures despite exiting successfully:' "$output_file"
grep -Fq '::error title=Clay source render failed::Clay FAILED: src/language_learning/vocabulary_estimation/beta_binomial_first_pass.clj' "$output_file"

echo 'Strict Clay regression passed: the historical stack-overflow signal was rejected with a GitHub error annotation.'
