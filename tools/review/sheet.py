#!/usr/bin/env python3
"""Review sheet: several screenshots on one labelled image the owner can judge at a glance.

  python3 tools/review/sheet.py --title "22.3 What do you need? at a glance" \
      --out build/review/22.3/sheet.png shot1.png shot2.png before.png:Before after.png:After

Each input is PATH or PATH:Label. Images are scaled to the same height (wider-than-the-sheet images
are scaled down further to fit) and laid out in rows up to --width pixels wide. Labels longer than
their picture are shortened with "...". GIFs contribute their first frame (send the GIF itself too).
Pillow only.
"""
import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

PAD, CAP, HEAD = 12, 26, 56


def font(size, bold=False):
    for name in (("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"), "Arial.ttf"):
        for base in ("/usr/share/fonts/truetype/dejavu/", "/usr/share/fonts/", ""):
            try:
                return ImageFont.truetype(base + name, size)
            except OSError:
                continue
    return ImageFont.load_default()


def split_spec(spec):
    """PATH or PATH:Label. Tries the last colon first, so Windows paths (C:\\x.png:Label) work."""
    if Path(spec).exists():
        return spec, ""
    for path, _, label in (spec.rpartition(":"), spec.partition(":")):
        if path and Path(path).exists():
            return path, label
    raise SystemExit(f"sheet.py: no such image: {spec}")


def fit_label(draw, text, fnt, width):
    if draw.textlength(text, font=fnt) <= width:
        return text
    while text and draw.textlength(text + "...", font=fnt) > width:
        text = text[:-1]
    return text + "..."


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("images", nargs="+")
    ap.add_argument("--title", default="Review")
    ap.add_argument("--out", required=True)
    ap.add_argument("--height", type=int, default=360, help="height of each picture")
    ap.add_argument("--width", type=int, default=1600, help="maximum sheet width")
    a = ap.parse_args()

    max_w = a.width - 2 * PAD
    items = []
    for spec in a.images:
        path, label = split_spec(spec)
        im = Image.open(path)
        im.seek(0)
        im = im.convert("RGB")
        scale = min(a.height / im.height, max_w / im.width)
        w, h = max(1, round(im.width * scale)), max(1, round(im.height * scale))
        items.append((im.resize((w, h), Image.LANCZOS if scale < 1 else Image.NEAREST), label or Path(path).stem))

    rows, row, x = [], [], PAD
    for im, label in items:
        if row and x + im.width + PAD > a.width:
            rows.append(row)
            row, x = [], PAD
        row.append((im, label))
        x += im.width + PAD
    rows.append(row)

    width = min(a.width, max(sum(im.width + PAD for im, _ in r) + PAD for r in rows))
    heights = [max(im.height for im, _ in r) for r in rows]
    sheet = Image.new("RGB", (width, HEAD + sum(h + CAP + PAD for h in heights) + PAD), (36, 36, 42))
    d = ImageDraw.Draw(sheet)
    title_font, label_font = font(24, True), font(16)
    d.text((PAD, 14), fit_label(d, a.title, title_font, width - 2 * PAD), fill=(255, 220, 120), font=title_font)
    y = HEAD
    for r, h in zip(rows, heights):
        x = PAD
        for im, label in r:
            sheet.paste(im, (x, y + h - im.height))
            d.text((x, y + h + 4), fit_label(d, label, label_font, im.width), fill=(235, 235, 235), font=label_font)
            x += im.width + PAD
        y += h + CAP + PAD
    Path(a.out).parent.mkdir(parents=True, exist_ok=True)
    sheet.save(a.out)
    print(a.out)


if __name__ == "__main__":
    main()
