#!/usr/bin/env python3
"""Build galleries (ROADMAP 23.10): every shipped build, front and back, one review sheet per build family.

    python3 tools/review/gallery.py [family...]      # all families when none are named
    python3 tools/review/gallery.py --list           # which build goes in which family

Renders src/main/resources/data/aliveworkplace/structure/**.nbt with tools/blueprints/render (headless Chromium, the
same renders the architect skill checks builds with) and lays each family out with tools/review/sheet.py as
build/review/23.10/<family>.jpg. A shipped build in no family stops the script: every build must be shown.
Needs `(cd tools/blueprints/render && npm install)` once.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STRUCTURES = ROOT / "src/main/resources/data/aliveworkplace/structure"
RENDERS = ROOT / "build/gallery"
OUT = ROOT / "build/review/23.10"
STYLES = ("plains", "desert", "savanna", "snowy", "taiga")

# Family -> (title, builds by their path under structure/ without .nbt; a trailing * takes the numbered variants too).
FAMILIES = {
    "houses": ("Houses", ["starter_cottage*", "stone_house*", "terrace*", "inn*", "camp/settlers_camp"]),
    "workshops": ("Workshops", ["tinkers_workshop*", "research_lab*", "sifting_shed*", "compost_yard*", "storehouse*",
                                "nether_gate*", "graveyard*", "research/town_hall"]),
    "shops": ("Shops, farms and services", ["supply_shop*", "market_stall*", "berry_farm*", "apiary_garden*", "ranch*",
                                            "flower_shop*", "healing_center*", "library*", "schoolhouse*"]),
    "defences": ("Defences", ["palisade", "palisade_gate", "palisade_tower", "stone_wall", "wall_tower", "gatehouse", "barracks*",
                              "lookout_tower*", "camp/bandit_camp"]),
    "decorations": ("Decorations", ["well*", "street_lamp", "park_bench", "fountain", "gazebo", "market_square", "chapel"]),
}
for style in STYLES:
    FAMILIES["village_" + style] = (f"Village pieces: {style}", [f"village/{style}_*"])


def shipped():
    return sorted(str(p.relative_to(STRUCTURES))[:-4] for p in STRUCTURES.rglob("*.nbt"))


def members(patterns, builds):
    out = []
    for pattern in patterns:
        if pattern.endswith("*"):
            stem = pattern[:-1]
            # "well*" takes well and well_2, not a build that merely starts with "well".
            out += [b for b in builds if b == stem.rstrip("_") or (b.startswith(stem) and (stem.endswith("_")
                    or b[len(stem):].lstrip("_").isdigit()))]
        elif pattern in builds:
            out.append(pattern)
        else:
            raise SystemExit(f"gallery.py: no shipped build {pattern}")
    return out


def assign():
    builds = shipped()
    families = {name: members(patterns, builds) for name, (_, patterns) in FAMILIES.items()}
    placed = [b for m in families.values() for b in m]
    missing = sorted(set(builds) - set(placed))
    twice = sorted({b for b in placed if placed.count(b) > 1})
    if missing or twice:
        raise SystemExit(f"gallery.py: builds in no family: {missing}; in two: {twice}")
    return families


def label(build):
    name = build.split("/")[-1]
    for style in STYLES:
        if name.startswith(style + "_"):
            name = name[len(style) + 1:]
    return name.replace("_", " ")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("families", nargs="*")
    ap.add_argument("--list", action="store_true")
    a = ap.parse_args()
    families = assign()
    if a.list:
        for name, builds in families.items():
            print(f"{name} ({len(builds)}): {', '.join(builds)}")
        return
    OUT.mkdir(parents=True, exist_ok=True)
    for name in a.families or families:
        builds = families[name]
        out = RENDERS / name
        files = [str(STRUCTURES / (b + ".nbt")) for b in builds]
        subprocess.run(["node", "shoot.mjs", str(out), *files, "--views", "front,back", "--size", "720x480"],
                       cwd=ROOT / "tools/blueprints/render", check=True, stdout=subprocess.DEVNULL)
        shots = []
        for b in builds:
            base = b.split("/")[-1]
            shots += [f"{out / (base + '_front.png')}:{label(b)}, front", f"{out / (base + '_back.png')}:{label(b)}, back"]
        title = f"23.10 Build gallery: {FAMILIES[name][0]} ({len(builds)} builds)"
        sheet = OUT / f"{name}.jpg"
        subprocess.run([sys.executable, str(ROOT / "tools/review/sheet.py"), "--title", title, "--out", str(sheet),
                        "--height", "240", "--width", "1500", *shots], check=True)
        print(f"{sheet.relative_to(ROOT)}: {len(builds)} builds, {sheet.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
