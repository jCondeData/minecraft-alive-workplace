#!/usr/bin/env bash
# Boots a real Cobbleverse server (every mod in the pack) with this mod's jar, generates a vanilla and a
# Repurposed Structures village, and looks for our houses and workstations. Catches crashes and mod
# clashes the dev environment can't (it only loads a few mods, with Mojang names).
#   tools/packtest/run.sh                  # uses the newest jar the 1.21.1 node built (versions/1.21.1/build/libs)
#   PACK_VERSION_URL=... tools/packtest/run.sh
#   PERF=true PLOTS=20 tools/packtest/run.sh   # performance mode: /workplace benchmark fills an area with busy
#                                              # workers; tick times before/after and a CPU profile of our code
#   SOAK=true tools/packtest/run.sh            # the builder soak (23.1): /workplace soak, then 2 in-game days at
#                                              # full speed (/tick sprint); prints the "Soak result:" line and the stalls
# Needs ~6 GB of RAM and ~1 GB of disk; takes ~5 minutes. Output: build/packtest/server/server.log
set -euo pipefail
cd "$(dirname "$0")/../.."
PACK_URL="${PACK_VERSION_URL:-https://cdn.modrinth.com/data/Jkb29YJU/versions/4SKGla61/COBBLEVERSE%201.7.42.mrpack}"
LOADER="${LOADER:-0.18.4}"
JAR=$(ls -t versions/1.21.1/build/libs/alive-workplace-*+1.21.1.jar | grep -v sources | head -1)
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
rm -rf "$SERVER/world"

# 2. Boot it with a console we can type into.
cd "$SERVER"
rm -f console && mkfifo console
sleep 100000 > console &
KEEP=$!
JAVA_OPTS=""
if [ "${PERF:-false}" = "true" ] || [ "${SOAK:-false}" = "true" ]; then JAVA_OPTS="-Daliveworkplace.benchmark=true"; fi
java -Xmx5G -Xms1G $JAVA_OPTS -jar fabric-server-launch.jar nogui < console > server.log 2>&1 &
PID=$!
for _ in $(seq 1 90); do
  sleep 10
  grep -qE "Done \(" server.log && break
  kill -0 $PID 2>/dev/null || { echo "The server stopped while starting:"; tail -40 server.log; kill $KEEP; exit 1; }
done
grep -q "Alive Workplace ready" server.log || { echo "Alive Workplace didn't load"; }

say() { echo "$1" > console; sleep "${2:-3}"; }

# Performance mode: the same area idle, then full of workers; tick times and a profile of the server thread.
if [ "${PERF:-false}" = "true" ]; then
  PLOTS="${PLOTS:-20}"
  ROWS=$(( (PLOTS + 4) / 5 ))
  say "forceload add 1000 1000 1190 $(( 1000 + ROWS * 32 ))" 60   # at most 256 chunks: up to 40 plots
  say "time set 1500"
  say "gamerule doDaylightCycle false"
  say "tick query" 30
  say "execute positioned 1000 100 1000 run workplace benchmark $PLOTS" 50
  # Starting the recording pauses the server for a moment: let that tick pass before measuring.
  jcmd $PID JFR.start name=perf settings=profile duration=90s filename="$PWD/perf.jfr" > /dev/null
  sleep 15
  say "tick query" 40
  say "tick query" 45
  say "workplace sites" 5
  say "stop" 30
  kill $KEEP 2>/dev/null || true
  wait $PID 2>/dev/null || true
  echo "--- tick times: idle, then twice with the workers (full log: $SERVER/server.log)"
  grep -E "Benchmark:|Average time per tick|Percentiles|Crash|Exception" server.log | grep -v "No data fixer" || true
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
  say "forceload add 990 990 1160 1150" 60
  say "execute positioned 1000 80 1000 run workplace soak" 60
  say "tick sprint 48000" 5
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
