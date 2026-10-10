#!/usr/bin/env bash
# Boots a real Cobbleverse server (every mod in the pack) with this mod's jar, generates a vanilla and a
# Repurposed Structures village, and looks for our houses and workstations. Catches crashes and mod
# clashes the dev environment can't (it only loads a few mods, with Mojang names).
#   tools/packtest/run.sh                  # uses the newest jar the 1.21.1 node built (versions/1.21.1/build/libs)
#   PACK_VERSION_URL=... tools/packtest/run.sh
#   PERF=true PLOTS=20 tools/packtest/run.sh   # performance mode: /workplace benchmark fills an area with busy
#                                              # workers; tick times before/after, heap after GC, a CPU profile of our code
#   PERF=true VILLAGES=3 PLOTS=25 ...          # 25.1's benchmark: 150 workers over three villages (docs/performance.md)
#   JAR=old.jar PERF=true PLOTS=10 ...; KEEP_WORLD=true SITES_ONLY=true ...   # 21.2: a world saved by an older jar,
#                                              # opened with this one: its sites listed after a minute of work
#   SOAK=true tools/packtest/run.sh            # the builder soak (23.1; SOAK_DAYS=n for longer): /workplace soak, then 2 in-game days at
#                                              # full speed (/tick sprint); prints the "Soak result:" line and the stalls
#   SOAK=true DEBUG=true ...                   # the same, with the builders' [builder N] lines in the log
#   CITY=true [PERF=true] tools/packtest/run.sh   # 27.22, the 1.1 yardstick: a village from a plan, its Steward running it for
#                                              # 6 in-game days (CITY_DAYS=n); prints the "City result:" line, PERF=true adds a profile
#   SEASON=true tools/packtest/run.sh          # 30.22, a season under the edicts: a City of 35 with farms, a kitchen and a store,
#                                              # 4 in-game days (SEASON_DAYS=n) under Long Shifts, Free Bread, Large Families and
#                                              # Festival Season, then 4 with all four reformed; a "Season day" line a day, the
#                                              # "Season result:" line, and our share of each day's tick from a profile
#   SEASON=true SEASON_PART=edicts ...         # one half on its own in a fresh village (edicts | reformed): half the time,
#                                              # for a night job; its costs aren't judged (nothing to compare with)
#   SOAK=true SOAK_SPLIT=true ...              # 23.5: each builder's materials split between at most 3 chests by its
#                                              # bench and the village storehouses (with porters) it must find itself
# Needs ~6 GB of RAM and ~1 GB of disk; takes ~5 minutes. Output: build/packtest/server/server.log
set -euo pipefail
cd "$(dirname "$0")/../.."
PACK_URL="${PACK_VERSION_URL:-https://cdn.modrinth.com/data/Jkb29YJU/versions/4SKGla61/COBBLEVERSE%201.7.42.mrpack}"
LOADER="${LOADER:-0.18.4}"
JAR="${JAR:-$(ls -t versions/1.21.1/build/libs/alive-workplace-*+1.21.1.jar | grep -v sources | head -1)}"
DIR=build/packtest
SERVER=$DIR/server
mkdir -p "$SERVER/mods"

# 1. The pack's mods and configs (downloaded once).
if [ ! -f "$DIR/modrinth.index.json" ]; then
  curl -sfL -o "$DIR/pack.mrpack" "$PACK_URL"
  (cd "$DIR" && unzip -o -q pack.mrpack modrinth.index.json 'overrides/*' && rm pack.mrpack)
  # The pack stores every override with Unix mode 000. Root reads them anyway, a normal user (GitHub's runner)
  # can't, and the server then ran with no pack configs, datapacks or bundled mods (B14).
  chmod -R u+rwX "$DIR/overrides"
  python3 - "$DIR" <<'EOF'
import json, sys
d = json.load(open(sys.argv[1] + "/modrinth.index.json"))
urls = [f["downloads"][0] for f in d["files"] if f["path"].startswith("mods/") and f.get("env", {}).get("server", "required") != "unsupported"]
open(sys.argv[1] + "/urls.txt", "w").write("\n".join(urls) + "\n")
EOF
  (cd "$SERVER/mods" && xargs -P 8 -n 1 curl -sfLO < ../../urls.txt)
  if ls "$DIR"/overrides/mods/*.jar > /dev/null 2>&1; then cp "$DIR"/overrides/mods/*.jar "$SERVER/mods/"; fi
  cp -r "$DIR/overrides/config" "$DIR/overrides/datapacks" "$SERVER/"
fi
# Without the pack's own configs the server isn't the pack (B14): stop rather than measure something else.
PACK_CONFIGS=$(find "$DIR/overrides/config" -type f | wc -l)
SERVER_CONFIGS=$(find "$SERVER/config" -type f -readable 2>/dev/null | wc -l)
if [ "$SERVER_CONFIGS" -lt "$PACK_CONFIGS" ]; then
  echo "Only $SERVER_CONFIGS of the pack's $PACK_CONFIGS config files are readable in $SERVER/config"; exit 1
fi
if [ ! -f "$SERVER/fabric-server-launch.jar" ]; then
  INSTALLER=$(curl -s https://meta.fabricmc.net/v2/versions/installer | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['version'])")
  curl -sfL -o "$SERVER/fabric-server-launch.jar" "https://meta.fabricmc.net/v2/versions/loader/1.21.1/$LOADER/$INSTALLER/server/jar"
fi
# Installing, as on a real server: take out the old jar (whatever its name), put in the new one.
rm -f "$SERVER"/mods/alive-workplace-*.jar
cp "$JAR" "$SERVER/mods/"
echo "eula=true" > "$SERVER/eula.txt"
printf 'online-mode=false\nview-distance=6\nsimulation-distance=5\nmax-tick-time=-1\nlevel-seed=alive\n' > "$SERVER/server.properties"
if [ "${KEEP_WORLD:-false}" != "true" ]; then rm -rf "$SERVER/world"; fi

# 2. Boot it with a console we can type into.
cd "$SERVER"
rm -f console && mkfifo console
sleep 100000 > console &
KEEP=$!
JAVA_OPTS=""
if [ "${PERF:-false}" = "true" ] || [ "${SOAK:-false}" = "true" ] || [ "${CITY:-false}" = "true" ] || [ "${SEASON:-false}" = "true" ]; then JAVA_OPTS="-Daliveworkplace.benchmark=true"; fi
# DEBUG=true: the builders log what they are doing every 2 seconds ([builder N] lines), to look into a stall.
if [ "${DEBUG:-false}" = "true" ]; then JAVA_OPTS="$JAVA_OPTS -Daliveworkplace.debug=true"; fi
java -Xmx5G -Xms1G $JAVA_OPTS -jar fabric-server-launch.jar nogui < console > server.log 2>&1 &
PID=$!
for _ in $(seq 1 90); do
  sleep 10
  grep -qE "Done \(" server.log && break
  kill -0 $PID 2>/dev/null || { echo "The server stopped while starting:"; tail -40 server.log; kill $KEEP; exit 1; }
done
grep -q "Alive Workplace ready" server.log || { echo "Alive Workplace didn't load"; }

say() { echo "$1" > console; sleep "${2:-3}"; }

# Sites mode (21.2's old-world check): open the world as it was left (KEEP_WORLD=true), let the workers carry on for a
# minute, list the sites and stop. Run PERF=true with JAR=<an older release's jar> first to make that world.
if [ "${SITES_ONLY:-false}" = "true" ]; then
  say "forceload add 1000 1000 1190 1190" 60
  say "workplace sites" 5
  say "stop" 30
  kill $KEEP 2>/dev/null || true
  wait $PID 2>/dev/null || true
  echo "--- sites working: $(grep -c " · working" server.log || true) of $(grep -cE "\] \[Server thread/INFO\]: (Builder|Miner) — " server.log || true)"
  grep -E "Crash|Exception|ERROR" server.log | grep -i "aliveworkplace\|alive workplace" | head -20 || true
  exit 0
fi

# Performance mode: the same area idle, then full of workers; tick times and a profile of the server thread.
if [ "${PERF:-false}" = "true" ] && [ "${CITY:-false}" != "true" ]; then
  PLOTS="${PLOTS:-20}"            # per village; two workers a plot
  VILLAGES="${VILLAGES:-1}"       # 25.1's benchmark: VILLAGES=3 PLOTS=25 is 150 workers in three villages 400 blocks apart
  ROWS=$(( (PLOTS + 4) / 5 ))
  for v in $(seq 0 $(( VILLAGES - 1 ))); do
    X=$(( 1000 + v * 400 ))
    say "forceload add $X 1000 $(( X + 190 )) $(( 1000 + ROWS * 32 ))" 60   # at most 256 chunks: up to 40 plots
  done
  say "time set 1500"
  say "gamerule doDaylightCycle false"
  say "tick query" 30
  for v in $(seq 0 $(( VILLAGES - 1 ))); do
    say "execute positioned $(( 1000 + v * 400 )) 100 1000 run workplace benchmark $PLOTS" 50
  done
  # Starting the recording pauses the server for a moment: let that tick pass before measuring.
  jcmd $PID JFR.start name=perf settings=profile duration=90s filename="$PWD/perf.jfr" > /dev/null
  sleep 15
  say "tick query" 40
  say "tick query" 45
  # Heap after a full GC, with everything still loaded and working (25.1).
  jcmd $PID GC.run > /dev/null 2>&1 || true
  sleep 5
  jcmd $PID GC.heap_info > heap.txt 2>/dev/null || true
  HEAP=$(grep -iE "heap +total" heap.txt | grep -oE "used [0-9]+K" | head -1 | grep -oE "[0-9]+" || echo 0)
  say "workplace sites" 5
  say "stop" 30
  kill $KEEP 2>/dev/null || true
  wait $PID 2>/dev/null || true
  echo "--- tick times: idle, then twice with the workers (full log: $SERVER/server.log)"
  grep -E "Benchmark:|Average time per tick|Percentiles|Crash|Exception" server.log | grep -v "No data fixer" || true
  echo "Heap after GC: $(( HEAP / 1024 )) MB"
  python3 ../../../tools/packtest/perf.py perf.jfr
  # The numbers only mean something if the workers were working: each site's status says "working" with its
  # villager's level under it. None working means the soak measured an idle server (B14), so it fails.
  WORKING=$(grep -c " · working" server.log || true)
  SITES=$(grep -cE "\] \[Server thread/INFO\]: (Builder|Miner) — " server.log || true)
  echo "--- sites working when measured: $WORKING of $SITES"
  if [ "$WORKING" -eq 0 ]; then
    echo "No worker was working: these tick times are an idle server's, not a busy one's."
    grep -E -A1 "\] \[Server thread/INFO\]: Builder — " server.log | head -6
    exit 1
  fi
  exit 0
fi

# Soak mode (23.1): 10 builders on the whole starter set, materials only in chests, 2 in-game days at full speed.
# Passes only when every build finished, no item count is off and no builder stalled for 30 s.
if [ "${SOAK:-false}" = "true" ]; then
  if [ "${SOAK_SPLIT:-false}" = "true" ]; then
    say "forceload add 960 990 1200 1150" 60
    say "execute positioned 1000 80 1000 run workplace soak ${SOAK_DAYS:-2} split" 60
  else
    say "forceload add 990 990 1160 1150" 60
    say "execute positioned 1000 80 1000 run workplace soak ${SOAK_DAYS:-2}" 60
  fi
  say "tick sprint $(( ${SOAK_DAYS:-2} * 24000 ))" 5
  for _ in $(seq 1 ${SOAK_MINUTES:-40}); do
    sleep 60
    grep -q "Soak result:" server.log && break
    kill -0 $PID 2>/dev/null || break
  done
  say "workplace sites" 5
  say "stop" 30
  kill $KEEP 2>/dev/null || true
  wait $PID 2>/dev/null || true
  echo "--- soak (full log: $SERVER/server.log)"
  grep -E "Soak:|Soak result:|Builder stalled|Sprint completed|Crash|Exception" server.log | grep -v "No data fixer" || true
  echo "--- stalls: $(grep -c "Builder stalled" server.log || true)"
  grep -q "Soak result: \([0-9]*\)/\1 builds finished.*; 0 stalls; items off: none" server.log || { echo "The soak did not pass."; exit 1; }
  exit 0
fi

# City mode (27.22, the 1.1 yardstick): a plains village with a Steward in Run the village, 3 builders, a stocked
# storehouse, 12 villagers and a full plan, run for 6 in-game days at full speed (/tick sprint), then up to 2 more for the
# builders to finish what he started. Passes only when every build he started finished, none outside its zone or in
# Keep Clear, no stall, no item off, and the Steward's p95 cost a tick is under 0.5 ms. With PERF=true it also records
# a 120-second profile of the server thread while he works (tools/packtest/perf.py).
if [ "${CITY:-false}" = "true" ]; then
  CITY_DAYS="${CITY_DAYS:-6}"
  say "forceload add 920 920 1080 1080" 60
  say "execute positioned 1000 75 1000 run workplace city $CITY_DAYS" 40
  say "tick sprint $(( (CITY_DAYS + 2) * 24000 ))" 5
  if [ "${PERF:-false}" = "true" ]; then
    jcmd $PID JFR.start name=city settings=profile duration=120s filename="$PWD/city.jfr" > /dev/null || true
  fi
  for _ in $(seq 1 ${CITY_MINUTES:-60}); do
    sleep 60
    grep -q "City result:" server.log && break
    kill -0 $PID 2>/dev/null || break
  done
  say "tick sprint stop" 3
  say "stop" 30
  kill $KEEP 2>/dev/null || true
  wait $PID 2>/dev/null || true
  echo "--- city (full log: $SERVER/server.log)"
  grep -E "City:|City result:|Builder stalled|Sprint completed|Crash|Exception" server.log | grep -v "No data fixer" || true
  if [ "${PERF:-false}" = "true" ] && [ -f city.jfr ]; then python3 ../../../tools/packtest/perf.py city.jfr; fi
  python3 - server.log <<'EOF'
import re, sys
line = next((l for l in open(sys.argv[1], errors="replace") if "City result:" in l), None)
if line is None:
    sys.exit("No City result line: the run didn't end.")
m = re.search(r"City result: (\d+)/(\d+) builds", line)
p95 = float(re.search(r"p95 ([\d.]+) ms", line).group(1))
ok = (m and m.group(1) == m.group(2) and int(m.group(2)) > 0 and "Keep Clear: none;" in line
      and re.search(r"; 0 stalls; items off: none;", line) and p95 < 0.5)
print("City passed." if ok else "The city run did not pass.")
sys.exit(0 if ok else 1)
EOF
  exit $?
fi

# Season mode (30.22): a City of 35 villagers in harvest season with farms, a kitchen and a store, a Cradle, a Harvest
# Idol, a founded guild and a rush a day; SEASON_DAYS (4) in-game days under four edicts, then as many with all four
# reformed, at full speed (/tick sprint). SEASON_PART=edicts or reformed runs one half on its own. Passes only when the
# result says the costs show (both halves) and nothing ran away, and our code took under 15% of the tick every day
# (25.2's target, from a profile of the whole run: tools/packtest/perf.py --days).
if [ "${SEASON:-false}" = "true" ]; then
  SEASON_DAYS="${SEASON_DAYS:-4}"
  SEASON_PART="${SEASON_PART:-both}"
  TOTAL=$SEASON_DAYS
  if [ "$SEASON_PART" = "both" ]; then TOTAL=$(( 2 * SEASON_DAYS )); fi
  say "forceload add 920 920 1080 1080" 60
  say "execute positioned 1000 75 1000 run workplace season $SEASON_DAYS $SEASON_PART" 30
  jcmd $PID JFR.start name=season settings=profile filename="$PWD/season.jfr" > /dev/null || true
  sleep 5
  say "tick sprint $(( TOTAL * 24000 + 1200 ))" 5
  for _ in $(seq 1 $(( ${SEASON_MINUTES:-60} * 4 ))); do
    sleep 15
    grep -q "Season result:" server.log && break
    kill -0 $PID 2>/dev/null || break
  done
  jcmd $PID JFR.stop name=season > /dev/null 2>&1 || true
  say "tick sprint stop" 3
  say "stop" 30
  kill $KEEP 2>/dev/null || true
  wait $PID 2>/dev/null || true
  echo "--- season (full log: $SERVER/server.log)"
  grep -E "Season: |Season day |Season result:|Sprint completed|Crash|Exception" server.log | cut -c1-1500 | grep -v "No data fixer" || true
  rm -f season-profile.txt
  if [ -f season.jfr ]; then python3 ../../../tools/packtest/perf.py season.jfr --days server.log | tee season-profile.txt; fi
  python3 - server.log season-profile.txt <<'EOF'
import os, re, sys
line = next((l for l in open(sys.argv[1], errors="replace") if "Season result:" in l), None)
if line is None:
    sys.exit("No Season result line: the run didn't end.")
ok = "DON'T SHOW" not in line and "ran away: nothing" in line
profile = open(sys.argv[2]).read() if os.path.exists(sys.argv[2]) else ""
shares = [float(x) for x in re.findall(r"our share of the tick, day \d+ \(\w+\): ([\d.]+)%", profile)]
ours_over = re.search(r"ticks with over 50 ms of our code: (\d+)", profile)
if ours_over and int(ours_over.group(1)) > 0:
    print(f"{ours_over.group(1)} tick(s) had over 50 ms of our code (25.2: none).")
    ok = False
if not shares:
    print("No profile: our share of the tick wasn't measured.")
    ok = False
elif max(shares) >= 15:
    print(f"Our code took {max(shares):.1f}% of the tick on its worst day: over the 15% target (25.2).")
    ok = False
else:
    print(f"Our share of the tick: {max(shares):.1f}% on the worst day (target: under 15%).")
print("Season passed." if ok else "The season run did not pass.")
sys.exit(0 if ok else 1)
EOF
  exit $?
fi

# 3. Our content in the pack: blueprints, templates, villages with our houses.
say "workplace blueprints"
say "forceload add 320 320 480 480" 20
say "forceload add 720 320 880 480" 20
say "place structure repurposed_structures:village_birch 400 70 400" 40
say "place structure minecraft:village_plains 800 70 400" 40
for x in 400 800; do
  # Our houses' own blocks (the others share vanilla blocks since ROADMAP 21.1a, so a hit wouldn't tell ours apart).
  for poi in blueprint_table training_post mailbox shop_counter storehouse travel_post; do
    say "execute positioned $x 70 400 run locate poi aliveworkplace:$poi" 2
  done
done
say "stop" 30
kill $KEEP 2>/dev/null || true
wait $PID 2>/dev/null || true
echo "--- results (full log: $SERVER/server.log)"
grep -E "Alive Workplace ready|blueprint\(s\) available|Generated structure|nearest aliveworkplace|Could not find a point of interest|Crash|Exception" server.log | grep -v "No data fixer" || true
