#!/usr/bin/env bash
# Renders the game headless and saves screenshots + a timelapse GIF of builders at work.
#   tools/screenshots/run.sh
# Needs: xvfb-run, Mesa (software OpenGL), Python 3 + Pillow. Takes ~10 minutes on 2 CPUs.
# Output: versions/1.21.1/run/screenshots/screenshots/*.png and versions/1.21.1/run/screenshots/timelapse.gif, plus
# screenshots/gif/*.png (small frames every half second) and showcase.json (the scene's checks: tools/showcase).
# COBBLEMON=true / MEGA=true add Cobblemon (and Mega Showdown) for scenes not in the list below.
# COBBLEMON18=true: those Pokémon scenes with Cobblemon 1.8.1 instead of the pack's 1.7.3 (ROADMAP 28.2).
# GUI_SCALE=4 films any scene at GUI scale 4 (a 1920x1080 window; scale 3 also gets it; default 2 at 960x540).
# (the harness is written for the Minecraft 1.21.1 node; each node runs in versions/<mc>/run).
set -euo pipefail
cd "$(dirname "$0")/../.."
SCRATCH=$(mktemp -d)
MC=1.21.1
RUN=versions/$MC/run

# 1. A flat world named "shots", generated once by a dev server.
if [ ! -f $RUN/shots/level.dat ]; then
  mkdir -p $RUN
  echo "eula=true" > $RUN/eula.txt
  printf 'level-name=shots\nlevel-type=minecraft\\:flat\nonline-mode=false\ngenerate-structures=false\nspawn-monsters=false\nspawn-animals=false\nspawn-npcs=false\n' > $RUN/server.properties
  ./gradlew :$MC:runServer --no-daemon --args=nogui > "$SCRATCH/server.log" 2>&1 &
  for _ in $(seq 1 120); do sleep 5; grep -q "Done (" "$SCRATCH/server.log" && break; done
  pkill -TERM -f "fabric.dli.env=server" || pkill -TERM -f "launch.cfg nogui" || true
  sleep 15
  rm -f $RUN/shots/session.lock
fi

# 2. Fresh copy of the world + client options that skip first-launch screens.
mkdir -p $RUN/screenshots/saves
rm -rf $RUN/screenshots/saves/shots $RUN/screenshots/screenshots $RUN/screenshots/showcase.json
cp -r $RUN/shots $RUN/screenshots/saves/shots
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'tutorialStep:none' 'joinedFirstServer:true' \
  'skipMultiplayerWarning:true' 'narrator:0' 'renderDistance:6' 'simulationDistance:6' 'graphicsMode:0' \
  'renderClouds:"false"' 'maxFps:15' 'enableVsync:false' "guiScale:${GUI_SCALE:-2}" 'soundCategory_master:0.0' \
  'pauseOnLostFocus:false' > $RUN/screenshots/options.txt

mkdir -p $RUN/screenshots/blueprints && cp src/gametest/resources/fixtures/hut.litematic "$RUN/screenshots/blueprints/Cozy Hut.litematic"

# Whether the scene loads Cobblemon (and Mega Showdown): from its entry in the showcase catalog, so a new scene needs no
# edit here (one edit here would film every scene on the next push, ROADMAP 22.8). Not in the catalog: false.
catalog() { python3 tools/showcase/scenes.py env "${SCENE:-builders}" 2>/dev/null | sed -n "s/^$1=//p" | grep . || echo false; }

# 3. Run the client (the dev-only harness stages the scene, takes shots and quits).
LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe xvfb-run -a -s "-screen 0 1920x1080x24" \
  ./gradlew :$MC:runScreenshots --no-daemon -Pscene="${SCENE:-builders}" -PguiScale="${GUI_SCALE:-2}" -PworkshopWeight="${WORKSHOP_WEIGHT:-3}" ${HOUSE_WEIGHT:+-PhouseWeight=$HOUSE_WEIGHT} \
    -Pcobblemon="${COBBLEMON:-$(catalog COBBLEMON)}" -Pmega="${MEGA:-$(catalog MEGA)}" -PbuilderDebug="${DEBUG:-false}" -Pcobblemon18="${COBBLEMON18:-false}" > "$SCRATCH/client.log" 2>&1 || true
cp "$SCRATCH/client.log" $RUN/screenshots/client.log 2>/dev/null || true
grep -E "finished building|Stopping!" "$SCRATCH/client.log" || true

# 4. Timelapse.
if [ "${SCENE:-builders}" = "builders" ]; then python3 tools/screenshots/make_gif.py --every 2; fi
