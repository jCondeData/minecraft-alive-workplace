#!/usr/bin/env python3
"""Stitch run/screenshots/screenshots/frame_*.png into a timelapse GIF.

    python3 tools/screenshots/make_gif.py [out.gif] [--crop x0,y0,x1,y1] [--width 640] [--every 1]
"""
import argparse
import glob
import os
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
p = argparse.ArgumentParser()
p.add_argument("out", nargs="?", default=os.path.join(ROOT, "run/screenshots/timelapse.gif"))
p.add_argument("--crop", default="190,70,790,370")
p.add_argument("--width", type=int, default=600)
p.add_argument("--every", type=int, default=1)
p.add_argument("--ms", type=int, default=70)
a = p.parse_args()

frames = sorted(glob.glob(os.path.join(ROOT, "run/screenshots/screenshots/frame_*.png")))[:: a.every]
extra = [os.path.join(ROOT, "run/screenshots/screenshots/03_finished_wide.png")]
frames += [f for f in extra if os.path.exists(f)] * 15  # hold the finished shot
box = tuple(int(v) for v in a.crop.split(","))
images = []
for f in frames:
    im = Image.open(f).convert("RGB").crop(box)
    h = round(im.height * a.width / im.width)
    images.append(im.resize((a.width, h), Image.LANCZOS).quantize(colors=128, method=Image.Quantize.MEDIANCUT))
images[0].save(a.out, save_all=True, append_images=images[1:], duration=a.ms, loop=0, optimize=True)
print(f"{len(images)} frames -> {a.out} ({os.path.getsize(a.out) // 1024} KB)")
