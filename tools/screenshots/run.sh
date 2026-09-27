#!/usr/bin/env bash
# Renders the game headless and saves screenshots + a timelapse GIF of builders at work.
#   tools/screenshots/run.sh
# Needs: xvfb-run, Mesa (software OpenGL), Python 3 + Pillow. Takes ~10 minutes on 2 CPUs.
# Output: run/screenshots/screenshots/*.png and run/screenshots/timelapse.gif
set -euo pipefail
cd "$(dirname "$0")/../.."
SCRATCH=$(mktemp -d)

# 1. A flat world named "shots", generated once by a dev server.
if [ ! -f run/shots/level.dat ]; then
  mkdir -p run
  echo "eula=true" > run/eula.txt
  printf 'level-name=shots\nlevel-type=minecraft\\:flat\nonline-mode=false\ngenerate-structures=false\nspawn-monsters=false\nspawn-animals=false\nspawn-npcs=false\n' > run/server.properties
  ./gradlew runServer --no-daemon --args=nogui > "$SCRATCH/server.log" 2>&1 &
  for _ in $(seq 1 120); do sleep 5; grep -q "Done (" "$SCRATCH/server.log" && break; done
  pkill -TERM -f "fabric.dli.env=server" || pkill -TERM -f "launch.cfg nogui" || true
  sleep 15
  rm -f run/shots/session.lock
fi

# 2. Fresh copy of the world + client options that skip first-launch screens.
mkdir -p run/screenshots/saves
rm -rf run/screenshots/saves/shots run/screenshots/screenshots
cp -r run/shots run/screenshots/saves/shots
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'tutorialStep:none' 'joinedFirstServer:true' \
  'skipMultiplayerWarning:true' 'narrator:0' 'renderDistance:6' 'simulationDistance:6' 'graphicsMode:0' \
  'renderClouds:"false"' 'maxFps:15' 'enableVsync:false' 'guiScale:2' 'soundCategory_master:0.0' \
  'pauseOnLostFocus:false' > run/screenshots/options.txt

mkdir -p run/screenshots/blueprints && cp src/gametest/resources/fixtures/hut.litematic "run/screenshots/blueprints/Cozy Hut.litematic"

# 3. Run the client (the dev-only harness stages the scene, takes shots and quits).
LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe xvfb-run -a -s "-screen 0 1280x720x24" \
  ./gradlew runScreenshots --no-daemon -Pscene="${SCENE:-builders}" -PworkshopWeight="${WORKSHOP_WEIGHT:-3}" > "$SCRATCH/client.log" 2>&1 || true
grep -E "finished building|Stopping!" "$SCRATCH/client.log" || true

# 4. Timelapse.
if [ "${SCENE:-builders}" = "builders" ]; then python3 tools/screenshots/make_gif.py --every 2; fi
