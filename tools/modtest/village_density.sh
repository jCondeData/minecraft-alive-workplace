#!/usr/bin/env bash
# ROADMAP 36.8: measures how many houses villages grow per village type (VillageDensityMeasure, 50 village starts
# per type at fixed seeds and chunks). Not part of `build`: this registers the measurement alone in the test mod,
# runs runGameTest, restores fabric.mod.json and prints the VILLAGE-DENSITY lines (also in build/village-density.txt).
#
#   tools/modtest/village_density.sh            (JAVA_HOME as for any Gradle run)
set -euo pipefail
cd "$(dirname "$0")/../.."
MOD=src/gametest/resources/fabric.mod.json
mkdir -p build
cp "$MOD" build/fabric.mod.json.density-backup
trap 'cp build/fabric.mod.json.density-backup "$MOD"' EXIT
python3 - "$MOD" <<'PY'
import json, sys
path = sys.argv[1]
mod = json.load(open(path, encoding="utf-8"))
mod["entrypoints"]["fabric-gametest"] = ["io.github.jcondedata.aliveworkplace.test.VillageDensityMeasure"]
with open(path, "w", encoding="utf-8") as f:
    json.dump(mod, f, indent="\t")
    f.write("\n")
PY
OUT="$(pwd)/build/village-density.txt"
rm -f "$OUT"
./gradlew --max-workers=1 runGameTest > build/village-density.log 2>&1 || { tail -40 build/village-density.log; exit 1; }
grep -h "VILLAGE-DENSITY" build/village-density.log | sed 's/.*VILLAGE-DENSITY/VILLAGE-DENSITY/' | tee "$OUT"
