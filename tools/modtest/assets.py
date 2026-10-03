#!/usr/bin/env python3
"""
Checks the mod's client assets without starting Minecraft. Exit code 1 on any FAIL.

    python3 tools/modtest/assets.py                 # every check
    python3 tools/modtest/assets.py refs held       # only these

refs   Every reference resolves: each blockstate's models, each model's parent and textures, and every registered
       item (Reg.item and Reg.block ids in the Java sources) has models/item/<id>.json. Vanilla (minecraft:) ids are
       checked against the pixel-art skill's vanilla cache when it is there (tools/textures/pxlib/vanilla.py fills it),
       and skipped otherwise.
drift  tools/textures/generate.py, run into a temporary folder (ALIVE_ASSETS), reproduces every texture and icon.png
       byte for byte, and every shipped texture comes from a recipe. A texture edited by hand, or a recipe changed
       without regenerating, fails here.
held   An item model with parent item/handheld is drawn like vanilla's handheld items: its grip in the bottom-left
       4x4 corner and its shape along the / diagonal (pixel correlation at most -0.3). All 38 vanilla 1.21.1 handheld
       icons pass this; a flat icon (a map, a sheet, an upright banner) fails it and wants item/generated.
icon   icon.png is the Blueprint item at 8x (what generate.py's icon() draws).

Written by the tester for ROADMAP 21.1b (the item icon picks).
"""
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/aliveworkplace"
JAVA = ROOT / "src/main/java"
VANILLA = Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache")) / "mc-pixel-art/1.21.1/assets/minecraft"
FAILS = []


def fail(msg):
    FAILS.append(msg)
    print("FAIL", msg)


def split(ref, default="minecraft"):
    ns, _, path = ref.partition(":") if ":" in ref else (default, None, ref)
    return ns, path


def exists(ns, kind, path, ext):
    if ns == "aliveworkplace":
        return (ASSETS / kind / f"{path}{ext}").exists()
    if ns == "minecraft":
        return (VANILLA / kind / f"{path}{ext}").exists() if VANILLA.exists() else True
    return True  # another mod's asset: not ours to check


def refs():
    models = sorted((ASSETS / "models").rglob("*.json"))
    for bs in sorted((ASSETS / "blockstates").glob("*.json")):
        text = bs.read_text()
        for ref in set(re.findall(r'"model"\s*:\s*"([^"]+)"', text)):
            ns, path = split(ref)
            if not exists(ns, "models", path, ".json"):
                fail(f"blockstates/{bs.name}: model {ref} not found")
    for m in models:
        rel = m.relative_to(ASSETS / "models")
        try:
            j = json.loads(m.read_text())
        except json.JSONDecodeError as e:
            fail(f"models/{rel}: not valid JSON ({e})")
            continue
        parent = j.get("parent")
        if parent and parent not in ("builtin/generated", "builtin/entity"):
            ns, path = split(parent)
            if not exists(ns, "models", path, ".json"):
                fail(f"models/{rel}: parent {parent} not found")
        for key, ref in (j.get("textures") or {}).items():
            if ref.startswith("#"):
                continue
            ns, path = split(ref)
            if not exists(ns, "textures", path, ".png"):
                fail(f"models/{rel}: texture {key} = {ref} not found")
    ids = set()
    for src in JAVA.rglob("*.java"):
        ids.update(re.findall(r'\bReg\.(?:item|block)\(\s*"([a-z0-9_/]+)"', src.read_text()))
    if not ids:
        fail("found no Reg.item / Reg.block ids in the Java sources (did the registration helper move?)")
    for i in sorted(ids):
        if not (ASSETS / "models/item" / f"{i}.json").exists():
            fail(f"item {i} is registered but has no models/item/{i}.json (purple-black cube in game)")
    print(f"refs: {len(models)} models, {len(list((ASSETS / 'blockstates').glob('*.json')))} blockstates, "
          f"{len(ids)} registered items" + ("" if VANILLA.exists() else " (vanilla cache missing: minecraft: ids not checked)"))


def drift():
    shipped = {p.relative_to(ASSETS) for p in (ASSETS / "textures").rglob("*") if p.is_file()} | {Path("icon.png")}
    with tempfile.TemporaryDirectory() as tmp:
        run = subprocess.run([sys.executable, str(ROOT / "tools/textures/generate.py")], cwd=ROOT, capture_output=True,
                             text=True, env={**os.environ, "ALIVE_ASSETS": tmp})
        if run.returncode != 0:
            fail(f"generate.py failed:\n{run.stderr[-2000:]}")
            return
        made = {p.relative_to(tmp) for p in Path(tmp).rglob("*") if p.is_file()}
        for rel in sorted(made):
            ours = ASSETS / rel
            if not ours.exists():
                fail(f"generate.py draws {rel}, which the mod doesn't ship (run generate.py and commit it)")
            elif ours.read_bytes() != (Path(tmp) / rel).read_bytes():
                fail(f"{rel} differs from what its recipe draws (edited by hand, or the recipe changed without regenerating)")
        for rel in sorted(shipped - made):
            fail(f"{rel} is shipped but no recipe in tools/textures/art draws it")
    print(f"drift: generate.py drew {len(made)} files, the mod ships {len(shipped)}")


def held():
    n = 0
    for m in sorted((ASSETS / "models/item").glob("*.json")):
        j = json.loads(m.read_text())
        if split(j.get("parent", ""))[1] not in ("item/handheld", "item/handheld_rod"):
            continue
        n += 1
        ns, path = split(j["textures"]["layer0"])
        im = Image.open(ASSETS / "textures" / f"{path}.png").convert("RGBA")
        w, h = im.size
        pts = [(x, y) for y in range(h) for x in range(w) if im.getpixel((x, y))[3] > 0]
        grip = any(x < w // 4 and y >= h - h // 4 for x, y in pts)
        mx, my = sum(x for x, _ in pts) / len(pts), sum(y for _, y in pts) / len(pts)
        sxy = sum((x - mx) * (y - my) for x, y in pts)
        sxx, syy = sum((x - mx) ** 2 for x, _ in pts), sum((y - my) ** 2 for _, y in pts)
        corr = sxy / (sxx * syy) ** 0.5 if sxx and syy else 0
        if not grip or corr > -0.3:
            fail(f"models/item/{m.name} is handheld but its icon isn't held like a tool (grip in the bottom-left corner: "
                 f"{grip}, diagonal {corr:+.2f}): a flat or upright icon wants minecraft:item/generated")
    print(f"held: {n} handheld item models checked")


def icon():
    i = Image.open(ASSETS / "icon.png").convert("RGBA")
    b = Image.open(ASSETS / "textures/item/blueprint.png").convert("RGBA")
    if i.size != (b.width * 8, b.height * 8) or i.tobytes() != b.resize(i.size, Image.NEAREST).tobytes():
        fail("icon.png is not the Blueprint item at 8x")
    print("icon: checked against the Blueprint item")


CHECKS = {"refs": refs, "drift": drift, "held": held, "icon": icon}

if __name__ == "__main__":
    wanted = sys.argv[1:] or list(CHECKS)
    unknown = [w for w in wanted if w not in CHECKS]
    if unknown:
        sys.exit(f"no such check: {', '.join(unknown)} (have: {', '.join(CHECKS)})")
    for w in wanted:
        CHECKS[w]()
    print(f"{len(FAILS)} failure(s)" if FAILS else "all asset checks passed")
    sys.exit(1 if FAILS else 0)
