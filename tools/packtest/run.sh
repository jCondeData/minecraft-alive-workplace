#!/usr/bin/env bash
# Boots a real Cobbleverse server (every mod in the pack) with this mod's jar, generates a vanilla and a
# Repurposed Structures village, and looks for our houses and workstations. Catches crashes and mod
# clashes the dev environment can't (it only loads a few mods, with Mojang names).
#   tools/packtest/run.sh                  # uses the newest build/libs jar
#   PACK_VERSION_URL=... tools/packtest/run.sh
# Needs ~6 GB of RAM and ~1 GB of disk; takes ~5 minutes. Output: build/packtest/server/server.log
set -euo pipefail
cd "$(dirname "$0")/../.."
PACK_URL="${PACK_VERSION_URL:-https://cdn.modrinth.com/data/Jkb29YJU/versions/4SKGla61/COBBLEVERSE%201.7.42.mrpack}"
LOADER="${LOADER:-0.18.4}"
JAR=$(ls -t build/libs/alive-workplace-*.jar | grep -v sources | head -1)
DIR=build/packtest
SERVER=$DIR/server
mkdir -p "$SERVER/mods"

# 1. The pack's mods and configs (downloaded once).
if [ ! -f "$DIR/modrinth.index.json" ]; then
  curl -sfL -o "$DIR/pack.mrpack" "$PACK_URL"
  (cd "$DIR" && unzip -o -q pack.mrpack modrinth.index.json 'overrides/*' && rm pack.mrpack)
  python3 - "$DIR" <<'EOF'
import json, sys
d = json.load(open(sys.argv[1] + "/modrinth.index.json"))
urls = [f["downloads"][0] for f in d["files"] if f["path"].startswith("mods/") and f.get("env", {}).get("server", "required") != "unsupported"]
open(sys.argv[1] + "/urls.txt", "w").write("\n".join(urls) + "\n")
EOF
  (cd "$SERVER/mods" && xargs -P 8 -n 1 curl -sfLO < ../../urls.txt)
  cp "$DIR"/overrides/mods/*.jar "$SERVER/mods/" 2>/dev/null || true
  cp -r "$DIR/overrides/config" "$DIR/overrides/datapacks" "$SERVER/"
fi
if [ ! -f "$SERVER/fabric-server-launch.jar" ]; then
  INSTALLER=$(curl -s https://meta.fabricmc.net/v2/versions/installer | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['version'])")
  curl -sfL -o "$SERVER/fabric-server-launch.jar" "https://meta.fabricmc.net/v2/versions/loader/1.21.1/$LOADER/$INSTALLER/server/jar"
fi
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
java -Xmx5G -Xms1G -jar fabric-server-launch.jar nogui < console > server.log 2>&1 &
PID=$!
for _ in $(seq 1 90); do
  sleep 10
  grep -qE "Done \(" server.log && break
  kill -0 $PID 2>/dev/null || { echo "The server stopped while starting:"; tail -40 server.log; kill $KEEP; exit 1; }
done
grep -q "Alive Workplace ready" server.log || { echo "Alive Workplace didn't load"; }

# 3. Our content in the pack: blueprints, templates, villages with our houses.
say() { echo "$1" > console; sleep "${2:-3}"; }
say "workplace blueprints"
say "forceload add 320 320 480 480" 20
say "forceload add 720 320 880 480" 20
say "place structure repurposed_structures:village_birch 400 70 400" 40
say "place structure minecraft:village_plains 800 70 400" 40
for x in 400 800; do
  for poi in builders_bench training_post guard_post nurse_station postal_desk leaders_podium tutors_desk trade_board fruit_basket ball_workbench storehouse travel_post; do
    say "execute positioned $x 70 400 run locate poi aliveworkplace:$poi" 2
  done
done
say "stop" 30
kill $KEEP 2>/dev/null || true
wait $PID 2>/dev/null || true
echo "--- results (full log: $SERVER/server.log)"
grep -E "Alive Workplace ready|blueprint\(s\) available|Generated structure|nearest aliveworkplace|Could not find a point of interest|Crash|Exception" server.log | grep -v "No data fixer" || true
