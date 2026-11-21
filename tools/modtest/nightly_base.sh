#!/usr/bin/env bash
# B693: the commit the nightly mutants and repeats (ROADMAP 22.7) diff against. Prints one sha, or nothing when
# there is nothing new to test.
#
#   tools/modtest/nightly_base.sh last-run   # scheduled nightlies: the commit the last nightly's mutation-plan tested
#   tools/modtest/nightly_base.sh 24h        # the newest commit at least 24 hours older than HEAD's own commit time
#
# "last-run" needs GH_TOKEN, GITHUB_REPOSITORY and GITHUB_RUN_ID (an Actions job with actions: read). The old window,
# "24 hours before now", depended on when GitHub started the run (often 7 hours late, at a different time each
# night): a late run after an early one skipped the hours between them, and when nothing had been pushed for a day
# the window was empty although the last nightly never saw the newest commits. Counting from the last nightly's
# commit covers every commit exactly once. Falls back to "24h" when no earlier nightly is found, and never looks
# back more than 72 hours (MAX_HOURS) before HEAD, so a broken week does not turn into a thousand mutants.
set -euo pipefail
mode="${1:-24h}"
branch="${BRANCH:-main}"
max_hours="${MAX_HOURS:-72}"
head=$(git rev-parse HEAD)
head_t=$(git log -1 --format=%ct HEAD)

before() { git rev-list -1 --before="@$(( head_t - $1 * 3600 ))" HEAD; }

base=""
if [ "$mode" = "last-run" ] && command -v gh > /dev/null; then
  runs=$(gh api "repos/$GITHUB_REPOSITORY/actions/workflows/nightly.yml/runs?branch=$branch&per_page=15" \
    --jq ".workflow_runs[] | select(.id != ${GITHUB_RUN_ID:-0}) | \"\(.id) \(.head_sha)\"" 2>/dev/null || true)
  tmp=$(mktemp -d)
  while read -r id sha; do
    [ -n "$id" ] || continue
    # A run whose mutation-plan finished: the sha it recorded (nightly-plan artifact), else the run's own sha (runs
    # from before B693's fix checked out exactly that). Sunday's benchmark-only run has no mutation-plan: skipped.
    ok=$(gh api "repos/$GITHUB_REPOSITORY/actions/runs/$id/jobs?per_page=100" \
      --jq '[.jobs[] | select(.name == "mutation-plan" and .conclusion == "success")] | length' 2>/dev/null || echo 0)
    [ "$ok" -gt 0 ] || continue
    if gh run download "$id" -R "$GITHUB_REPOSITORY" -n nightly-plan -D "$tmp/$id" > /dev/null 2>&1 \
       && [ -s "$tmp/$id/head.txt" ]; then
      sha=$(tr -d '[:space:]' < "$tmp/$id/head.txt")
    fi
    if git merge-base --is-ancestor "$sha" "$head" 2>/dev/null; then
      base="$sha"
      echo "Last nightly: run $id tested ${sha:0:9}." >&2
      break
    fi
  done <<< "$runs"
  rm -rf "$tmp"
fi
[ -n "$base" ] || { base=$(before 24); echo "No earlier nightly found: 24 hours before HEAD's commit." >&2; }
[ -n "$base" ] || exit 0                      # a history shorter than a day: nothing to diff against
oldest=$(before "$max_hours")
if [ -n "$oldest" ] && ! git merge-base --is-ancestor "$oldest" "$base"; then
  echo "Window capped at ${max_hours} h before HEAD." >&2
  base="$oldest"
fi
[ "$base" = "$head" ] && { echo "Nothing new since ${head:0:9}." >&2; exit 0; }
echo "$base"
