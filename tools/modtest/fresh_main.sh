#!/usr/bin/env bash
# B693: fail loudly when a nightly job is about to test an old commit instead of its branch's newest one.
#
#   tools/modtest/fresh_main.sh [BRANCH] [MAX_HOURS]     (defaults: main, 26; REV=<commit> checks that instead of HEAD)
#
# Fetches origin/BRANCH and compares it with the checked-out HEAD:
# - HEAD is the branch's newest commit: passes, even when that commit is days old (nobody pushed; the run says so).
# - HEAD is behind it but no more than MAX_HOURS older (a push landed while the run was going): passes with a notice.
# - HEAD is more than MAX_HOURS older than the newest commit, or not on the branch at all: fails with an error.
set -euo pipefail
branch="${1:-main}"
max_hours="${2:-26}"
say() { echo "$1"; [ -n "${GITHUB_STEP_SUMMARY:-}" ] && echo "$2" >> "$GITHUB_STEP_SUMMARY" || true; }

if [ "$(git rev-parse --is-shallow-repository)" = "true" ]; then
  git fetch --quiet --depth=300 origin "+refs/heads/$branch:refs/remotes/origin/$branch"
else
  git fetch --quiet origin "+refs/heads/$branch:refs/remotes/origin/$branch"
fi
head=$(git rev-parse "${REV:-HEAD}")
tip=$(git rev-parse "origin/$branch")
head_t=$(git log -1 --format=%ct "$head")
tip_t=$(git log -1 --format=%ct "$tip")
now=$(date +%s)

if [ "$head" = "$tip" ]; then
  age_h=$(( (now - head_t) / 3600 ))
  if [ "$age_h" -gt "$max_hours" ]; then
    say "::notice::Testing ${head:0:9}, $branch's newest commit; it is ${age_h} h old because nothing was pushed since." \
        "Testing \`${head:0:9}\`, $branch's newest commit (${age_h} h old: nothing was pushed since)."
  else
    say "Testing ${head:0:9}, $branch's newest commit." "Testing \`${head:0:9}\`, $branch's newest commit."
  fi
  exit 0
fi

behind_s=$(( tip_t - head_t ))
behind_h=$(( behind_s / 3600 ))
if ! git merge-base --is-ancestor "$head" "$tip" 2>/dev/null; then
  if [ "$(git rev-parse --is-shallow-repository)" != "true" ]; then
    echo "::error::Stale checkout (B693): HEAD ${head:0:9} is not on origin/$branch (newest ${tip:0:9}). This nightly would test the wrong code."
    exit 1
  fi
  echo "Shallow clone: could not check that ${head:0:9} is on origin/$branch; checking its age only."
fi
if [ "$behind_s" -gt $(( max_hours * 3600 )) ]; then
  echo "::error::Stale checkout (B693): HEAD ${head:0:9} ($(git log -1 --format=%cI "$head")) is ${behind_h} h older than origin/$branch's newest commit ${tip:0:9} ($(git log -1 --format=%cI "$tip")), over the ${max_hours} h limit. This nightly would test old code; check the workflow's checkout ref."
  exit 1
fi
say "::notice::Testing ${head:0:9}; origin/$branch moved on to ${tip:0:9} (${behind_h} h newer) while this run was going." \
    "Testing \`${head:0:9}\`; $branch moved on to \`${tip:0:9}\` (${behind_h} h newer) while this run was going."
