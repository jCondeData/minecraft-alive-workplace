#!/usr/bin/env python3
"""
The old-vs-new sheet (ROADMAP 49.1, tools/blueprints/REBUILD.md): one labeled image putting a family's old drawing
beside its rebuild, row by row (one row per view or shot), "OLD" on the left and "NEW" on the right, for the critic
and the owner's review package.

    python3 tools/blueprints/sheet.py --title "Well" --old a_front.png a_back.png --new b_front.png b_back.png \
        [--labels front back] -o build/rebuild/well_sheet.png

    # or from render folders: every <old>_<view>.png beside <new>_<view>.png (tools/blueprints/render/shoot.mjs names)
    python3 tools/blueprints/sheet.py --title "Well" --renders build/blueprint-renders --old-name well \
        --new-name well_new --views front,back,left,right,top -o build/rebuild/well_sheet.png

Images of different sizes are scaled to one cell height; a missing image leaves its cell gray and says "missing".
"""
import argparse
import os
import sys

from PIL import Image, ImageDraw, ImageFont

CELL_H = 360
PAD = 16
HEAD = 56
BG = (24, 26, 30)
INK = (235, 235, 235)
DIM = (150, 150, 150)
OLD_TINT = (190, 120, 90)
NEW_TINT = (110, 190, 120)


def font(size):
    for path in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
                 "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def cell(path, width):
    """The image at {@code path} scaled into a width x CELL_H box (kept in proportion), or a gray "missing" box."""
    box = Image.new("RGB", (width, CELL_H), (48, 50, 56))
    if path and os.path.exists(path):
        img = Image.open(path).convert("RGB")
        scale = min(width / img.width, CELL_H / img.height)
        img = img.resize((max(1, int(img.width * scale)), max(1, int(img.height * scale))), Image.LANCZOS)
        box.paste(img, ((width - img.width) // 2, (CELL_H - img.height) // 2))
    else:
        ImageDraw.Draw(box).text((12, CELL_H // 2 - 10), "missing: " + os.path.basename(path or "?"), fill=DIM, font=font(16))
    return box


def sheet(title, old, new, labels, out):
    rows = max(len(old), len(new))
    labels = list(labels or []) + [""] * rows
    width = int(CELL_H * 1.5)
    W = PAD * 3 + width * 2
    H = HEAD + PAD + rows * (CELL_H + PAD + 24)
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    d.text((PAD, 14), title, fill=INK, font=font(26))
    d.text((PAD + width // 2 - 20, HEAD - 8), "OLD", fill=OLD_TINT, font=font(18))
    d.text((PAD * 2 + width + width // 2 - 20, HEAD - 8), "NEW", fill=NEW_TINT, font=font(18))
    y = HEAD + PAD + 8
    for i in range(rows):
        if labels[i]:
            d.text((PAD, y), labels[i], fill=DIM, font=font(16))
        y += 22
        for j, (paths, tint) in enumerate(((old, OLD_TINT), (new, NEW_TINT))):
            x = PAD + j * (width + PAD)
            img.paste(cell(paths[i] if i < len(paths) else None, width), (x, y))
            d.rectangle((x - 2, y - 2, x + width + 1, y + CELL_H + 1), outline=tint, width=2)
        y += CELL_H + PAD + 2
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    img.save(out)
    return out


def main(argv=None):
    a = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    a.add_argument("--title", required=True)
    a.add_argument("--old", nargs="*", default=[])
    a.add_argument("--new", nargs="*", default=[])
    a.add_argument("--labels", nargs="*")
    a.add_argument("--renders", help="a folder of <name>_<view>.png renders")
    a.add_argument("--old-name")
    a.add_argument("--new-name")
    a.add_argument("--views", default="front,back,left,right,top")
    a.add_argument("-o", "--out", required=True)
    args = a.parse_args(argv)
    old, new, labels = list(args.old), list(args.new), args.labels
    if args.renders:
        views = args.views.split(",")
        old += [os.path.join(args.renders, f"{args.old_name}_{v}.png") for v in views]
        new += [os.path.join(args.renders, f"{args.new_name}_{v}.png") for v in views]
        labels = (labels or []) + views
    if not old and not new:
        a.error("nothing to put on the sheet")
    print(sheet(args.title, old, new, labels, args.out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
