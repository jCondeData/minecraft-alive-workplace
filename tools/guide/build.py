#!/usr/bin/env python3
"""Makes the Guide Book's pictures (ROADMAP 26.2a) from in-game screenshots.

    python3 tools/guide/build.py            # fetch the stills (cached in build/guide/src), write the textures
    python3 tools/guide/build.py --sheet    # also a contact sheet of every page, build/guide/sheet.png
    python3 tools/guide/build.py --local DIR   # take stills from DIR/<scene>/still-<n>.jpg|png instead of the web

Each page's still is cropped as tools/guide/pages.py says and scaled to 384 x 216, with a small palette so the jar
stays small: src/main/resources/assets/aliveworkplace/textures/gui/guide/<page>.png.
"""
import argparse
import io
import sys
import urllib.request
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from pages import PAGES  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
SHOWCASE = "https://jcondedata.github.io/minecraft-alive-workplace"
CACHE = ROOT / "build" / "guide" / "src"
OUT = ROOT / "src/main/resources/assets/aliveworkplace/textures/gui/guide"
W, H = 384, 216


def still(scene, n, local):
    if local:
        for ext in ("png", "jpg"):
            p = Path(local) / scene / f"still-{n}.{ext}"
            if p.exists():
                return Image.open(p).convert("RGB")
        raise SystemExit(f"no still {scene}/still-{n} in {local}")
    cached = CACHE / scene / f"still-{n}.jpg"
    if not cached.exists():
        cached.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(f"{SHOWCASE}/{scene}/still-{n}.jpg", timeout=60) as r:
            cached.write_bytes(r.read())
    return Image.open(cached).convert("RGB")


def picture(img, crop):
    if crop:
        x, y, w = crop
        img = img.crop((x, y, x + w, y + round(w * 9 / 16)))
    img = img.resize((W, H), Image.LANCZOS)
    return img.quantize(colors=192, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--local")
    p.add_argument("--sheet", action="store_true")
    a = p.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    made = []
    for page, (scene, n, crop) in PAGES.items():
        pic = picture(still(scene, n, a.local), crop)
        path = OUT / f"{page}.png"
        pic.save(path, optimize=True)
        made.append((page, path))
    total = sum(path.stat().st_size for _, path in made)
    print(f"{len(made)} pictures, {total // 1024} KB, in {OUT.relative_to(ROOT)}")
    if a.sheet:
        cols = 5
        rows = -(-len(made) // cols)
        sheet = Image.new("RGB", (cols * (W + 8) + 8, rows * (H + 24) + 8), (40, 40, 44))
        from PIL import ImageDraw
        d = ImageDraw.Draw(sheet)
        for i, (page, path) in enumerate(made):
            x, y = 8 + (i % cols) * (W + 8), 8 + (i // cols) * (H + 24)
            sheet.paste(Image.open(path).convert("RGB"), (x, y))
            d.text((x, y + H + 4), page, fill=(230, 230, 230))
        out = ROOT / "build" / "guide" / "sheet.png"
        sheet.save(out)
        print(out.relative_to(ROOT))


if __name__ == "__main__":
    main()
