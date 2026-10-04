#!/usr/bin/env python3
"""One sheet of every profession's outfit on the real villager model (ROADMAP 24.1).

    python3 tools/textures/outfits_sheet.py [--out build/review/outfits.png] [--columns 2]

Per profession: the plains villager from the front and the back, the outfit over the other six biome outfits, and the
zombie villager in its own zombie outfit; with each texture's lint verdict. Uses the pixel-art library in pxlib
(preview.py's model renderer and layer stacking) and the vanilla textures it downloads once.
"""
import argparse
import math
import os
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "pxlib"))
import lint  # noqa: E402
import preview  # noqa: E402
import render3d  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

ROOT = HERE.parent.parent
TEX = ROOT / "src/main/resources/assets/aliveworkplace/textures/entity"
BIOMES = ["desert", "jungle", "savanna", "snow", "swamp", "taiga"]
BG, FG, MUTED, BAD = (44, 44, 50, 255), (235, 235, 230, 255), (160, 160, 165, 255), (240, 110, 100, 255)


def font(size):
    for name in ("DejaVuSans.ttf", "DejaVuSans-Bold.ttf"):
        for base in ("/usr/share/fonts/truetype/dejavu/", ""):
            try:
                return ImageFont.truetype(base + name, size)
            except OSError:
                pass
    return ImageFont.load_default()


def verdict(path):
    """'OK' or the first ERROR/WARN line of the pixel-art lint."""
    _, found = lint.lint(str(path), "villager")
    issues = [i for i in found if i[0] in ("ERROR", "WARN")]
    return "OK" if not issues else f"{issues[0][0]}: {issues[0][1]}"


def cell(name, mc):
    prof = TEX / "villager/profession" / f"{name}.png"
    zombie = TEX / "zombie_villager/profession" / f"{name}.png"
    m, zm = render3d.villager_model(), render3d.zombie_villager_model()
    shots = []
    plains = preview.composite("villager", "plains", str(prof), "stone", mc)
    shots.append(render3d.render_model(m, plains, yaw=0, pitch=math.radians(6), scale=4))
    shots.append(render3d.render_model(m, plains, yaw=math.radians(180), pitch=math.radians(6), scale=4))
    for biome in BIOMES:
        tex = preview.composite("villager", biome, str(prof), "stone", mc)
        shots.append(render3d.render_model(m, tex, yaw=math.radians(-25), pitch=math.radians(6), scale=4))
    ztex = preview.composite("zombie_villager", "plains", str(prof), "stone", mc, str(zombie) if zombie.exists() else None)
    shots.append(render3d.render_model(zm, ztex, yaw=math.radians(-25), pitch=math.radians(6), scale=4))
    w = sum(s.width for s in shots) + 4 * (len(shots) - 1)
    h = max(s.height for s in shots)
    out = Image.new("RGBA", (w + 16, h + 44), BG)
    d = ImageDraw.Draw(out)
    title = name.replace("_", " ").title()
    lints = [verdict(prof), verdict(zombie) if zombie.exists() else "no zombie outfit"]
    ok = all(v == "OK" for v in lints)
    d.text((8, 4), title, fill=FG, font=font(15))
    d.text((8 + d.textlength(title, font=font(15)) + 10, 7), "lint OK (villager, zombie)" if ok else " / ".join(lints),
           fill=MUTED if ok else BAD, font=font(11))
    x = 8
    for s in shots:
        out.alpha_composite(s, (x, 28 + h - s.height))
        x += s.width + 4
    return out, ok


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--out", default=str(ROOT / "build/review/outfits.png"))
    p.add_argument("--columns", type=int, default=2)
    p.add_argument("--mc", default="1.21.1")
    a = p.parse_args()
    names = sorted(f.stem for f in (TEX / "villager/profession").glob("*.png"))
    cells, bad = [], []
    for n in names:
        im, ok = cell(n, a.mc)
        cells.append(im)
        if not ok:
            bad.append(n)
    cw, ch = max(c.width for c in cells), max(c.height for c in cells)
    rows = math.ceil(len(cells) / a.columns)
    head = 64
    sheet = Image.new("RGBA", (cw * a.columns + 12 * (a.columns + 1), head + rows * (ch + 10) + 10), (30, 30, 34, 255))
    d = ImageDraw.Draw(sheet)
    d.text((12, 10), f"Every profession's outfit: {len(names)} jobs", fill=(255, 215, 120, 255), font=font(22))
    d.text((12, 40), "front and back (plains) · over the desert, jungle, savanna, snow, swamp and taiga outfits · "
           "the zombie villager", fill=MUTED, font=font(13))
    for i, c in enumerate(cells):
        x = 12 + (i % a.columns) * (cw + 12)
        y = head + (i // a.columns) * (ch + 10)
        sheet.alpha_composite(c, (x, y))
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    sheet.convert("RGB").save(a.out)
    print(a.out, f"({len(names)} professions, {len(names) - len(bad)} pass lint" + (f"; not: {', '.join(bad)})" if bad else ")"))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
