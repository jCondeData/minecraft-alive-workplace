#!/usr/bin/env bash
# The update rehearsal (ROADMAP 34.23): a world made with the last release before 1.8, opened with this jar.
#   tools/packtest/rehearsal.sh            # OLD=0.142.0 by default; DAYS=2 in-game days with the new jar
# 1. Builds the old release's jar from a git worktree of its tag, with one file added: command/Rehearsal.java (the
#    seeding and the report, benchmark-only; the save code is the release's own). The published jar can't seed.
# 2. Boots the Cobbleverse pack with that jar (run.sh REHEARSAL=old): three villages with halls (a Hamlet of 6, a Town of
#    22 with research and couples, a City of 36 with every class-gated job, children, a build in progress, daycare
#    boarders, a fossil reviving and a parcel in the post), one in-game day, the "before" report, saved.
# 3. Boots it again with this jar and every expansion's gate open, as 1.8 will ship (run.sh REHEARSAL=new): the containers
#    as first loaded, the class seeding's tick times, a report a minute in, then DAYS days and the "after" report.
#    Then the control: the same saved world on the old jar for the same days, for the takings to be compared like for like.
# 4. tools/packtest/rehearsal.py writes the report (REPORT, default docs/owner/rehearsal-1.8.md) and the log audit.
# Needs ~6 GB of RAM; takes about 30 minutes. Run it on Java 21 (the system's), not the JDK 25 Gradle uses.
set -euo pipefail
cd "$(dirname "$0")/../.."
ROOT=$PWD
OLD="${OLD:-0.142.0}"
WT="${WT:-$ROOT/build/rehearsal/src-$OLD}"
OUT=build/rehearsal
REPORT="${REPORT:-docs/owner/rehearsal-1.8.md}"
mkdir -p "$OUT"

# 1. The old jar.
OLD_JAR="$OUT/alive-workplace-$OLD-rehearsal.jar"
if [ ! -f "$OLD_JAR" ]; then
  [ -d "$WT" ] || git worktree add -f "$WT" "v$OLD"
  cp src/main/java/io/github/jcondedata/aliveworkplace/command/Rehearsal.java "$WT/src/main/java/io/github/jcondedata/aliveworkplace/command/"
  MAIN="$WT/src/main/java/io/github/jcondedata/aliveworkplace/AliveWorkplace.java"
  grep -q "Rehearsal.init" "$MAIN" || sed -i 's|^\(\t\tio.github.jcondedata.aliveworkplace.command.SeasonSoak.init();.*\)$|\1\n\t\tio.github.jcondedata.aliveworkplace.command.Rehearsal.init();|' "$MAIN"
  grep -q "Rehearsal.init" "$MAIN" || { echo "Couldn't add Rehearsal.init to $MAIN"; exit 1; }
  (cd "$WT" && ./gradlew --max-workers=1 -q :1.21.1:assemble)
  cp "$(ls -t "$WT"/versions/1.21.1/build/libs/alive-workplace-*+1.21.1.jar | grep -v sources | head -1)" "$OLD_JAR"
fi
NEW_JAR="${JAR:-$(ls -t versions/1.21.1/build/libs/alive-workplace-*+1.21.1.jar | grep -v sources | head -1)}"
echo "Old jar: $OLD_JAR; new jar: $NEW_JAR"

# 2 and 3. The two halves on the pack (run.sh keeps the world between them).
# The mod's config as the old release leaves it (a file from an earlier rehearsal would say otherwise).
rm -f build/packtest/server/config/aliveworkplace.json
JAR="$ROOT/$OLD_JAR" REHEARSAL=old tools/packtest/run.sh | tee "$OUT/old.txt"
cp build/packtest/server/server.log "$OUT/old-server.log"
rm -rf "$OUT/world-before" "$OUT/config-before.json"
cp -r build/packtest/server/world "$OUT/world-before"
cp build/packtest/server/config/aliveworkplace.json "$OUT/config-before.json"
JAR="$ROOT/$NEW_JAR" REHEARSAL=new KEEP_WORLD=true DAYS="${DAYS:-2}" tools/packtest/run.sh | tee "$OUT/new.txt"
cp build/packtest/server/server.log "$OUT/new-server.log"
rm -rf "$OUT/reports" && cp -r build/packtest/server/world/rehearsal "$OUT/reports"
# 3b. The control: the saved world on the old jar for the same days (the takings move from day to day by themselves).
rm -rf build/packtest/server/world && cp -r "$OUT/world-before" build/packtest/server/world
cp "$OUT/config-before.json" build/packtest/server/config/aliveworkplace.json
JAR="$ROOT/$OLD_JAR" REHEARSAL=control KEEP_WORLD=true DAYS="${DAYS:-2}" tools/packtest/run.sh | tee "$OUT/control.txt"
cp build/packtest/server/server.log "$OUT/control-server.log"
cp build/packtest/server/world/rehearsal/control.json "$OUT/reports/"

# 4. The report and the log audit (all three runs).
python3 tools/modtest/logaudit.py "$OUT/old-server.log" "$OUT/new-server.log" "$OUT/control-server.log" --mod aliveworkplace --allow tools/modtest/allow.txt \
  > "$OUT/logaudit.txt" 2>&1 && AUDIT=0 || AUDIT=$?
python3 tools/packtest/rehearsal.py "$OUT/reports" "$OUT/new-server.log" "$OUT/logaudit.txt" "$AUDIT" "$OLD" "$(basename "$NEW_JAR")" "$REPORT"
