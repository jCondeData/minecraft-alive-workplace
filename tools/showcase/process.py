#!/usr/bin/env python3
"""Turns one scene's raw output into the showcase's pictures and its verdict.

    python3 tools/showcase/process.py SCENE RAW_DIR OUT_DIR

RAW_DIR holds what tools/screenshots/run.sh left: screenshots/*.png (the stills), screenshots/gif/*.png (small frames
every half second, from the harness's Showcase class), showcase.json (the scene's checks and problems) and client.log.
OUT_DIR gets anim.gif, still-N.jpg, more-N.jpg and result.json.

A scene passes only if the harness recorded at least one check, every check passed, and nothing looks broken:
- the harness's problems (villagers stuck in walls, text shown as a raw translation key);
- magenta-and-black "missing texture" pixels in a still or a frame;
- a blank picture, a missing still, no GIF frames, a crash, or running over the time limit.
Missing textures or models in the client log are the same for every scene; they're returned as "assets" problems
and reported once for the whole run.
"""
import fnmatch
import glob
import json
import os
import re
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import scenes  # noqa: E402

try:
    import numpy as np
except ImportError:  # pixel checks need numpy; without it they're skipped (and say so)
    np = None

GIF_FRAMES = 120
GIF_WIDTH = 480
MAGENTA_STILL = 300  # pixels of missing-texture magenta in a 960x540 still that count as broken
MAGENTA_FRAME = 90  # ... in a 480x270 GIF frame
ASSET_LINES = re.compile(r"(Missing model|Unable to load model|Missing textures? in model|Unable to resolve texture|"
                         r"Using missing texture|Couldn't load texture|Failed to load texture|Missing sprite)")
CRASH = re.compile(r"---- Minecraft Crash Report ----|#@!@# Game crashed|Exception in server tick loop")


def magenta_pixels(im):
    """Pixels of the missing texture's magenta (#F800F8), however lit: red and blue alike, almost no green."""
    a = np.asarray(im.convert("RGB"), dtype=np.int16)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    lo, hi = np.minimum(r, b), np.maximum(r, b)
    return int(np.count_nonzero((lo >= 70) & (g * 4 <= lo) & ((hi - lo) * 4 <= hi)))


def blank(im):
    """A picture with (almost) nothing in it: one colour all over."""
    a = np.asarray(im.convert("L"), dtype=np.float32)
    return float(a.std()) < 2.5


def still_specs(name):
    return scenes.BY_NAME[name]["stills"] if name in scenes.BY_NAME else []


def process(name, raw, out, seconds=None, exit_code=0):
    os.makedirs(out, exist_ok=True)
    for old in glob.glob(os.path.join(out, "*")):
        os.remove(old)
    meta = scenes.BY_NAME.get(name, {"title": name, "group": "Other", "what": ""})
    problems, warnings, assets = [], [], []
    shots_dir = os.path.join(raw, "screenshots")
    stems = sorted(os.path.splitext(os.path.basename(f))[0] for f in glob.glob(os.path.join(shots_dir, "*.png")))

    # The harness's own record.
    report = None
    path = os.path.join(raw, "showcase.json")
    if os.path.exists(path):
        try:
            with open(path) as f:
                report = json.load(f)
        except ValueError:
            problems.append("showcase.json is unreadable")
    checks = report.get("checks", []) if report else []
    if report:
        problems += report.get("problems", [])
        for key in report.get("missingKeys", []):
            problems.append(f"raw text key on screen: {key}")
        for key in report.get("otherMissingKeys", []):
            warnings.append(f"another mod's text key has no translation: {key}")
    else:
        problems.append("the scene left no showcase.json (it crashed or never started)")
    if report and not checks:
        problems.append("the scene never checked its work")

    # The client log.
    log = ""
    if os.path.exists(os.path.join(raw, "client.log")):
        with open(os.path.join(raw, "client.log"), errors="replace") as f:
            log = f.read()
    if CRASH.search(log):
        problems.append("the game crashed (see client.log in the run's artifacts)")
    for line in log.splitlines():
        if ASSET_LINES.search(line) and "aliveworkplace" in line:
            assets.append(re.sub(r"^.*?\]: ", "", line.strip())[:300])
    if exit_code == "timeout":
        problems.append("ran over its time limit")

    # Stills, 2-4, as labelled.
    stills, more = [], []
    for spec, label in still_specs(name):
        picked = scenes.pick(stems, spec)
        if not picked:
            problems.append(f"picture missing: {spec} ({label or 'gallery'})")
        for stem in picked:
            stills.append((stem, label or scenes.label_from(stem)))
        if spec.endswith("@spread"):
            pattern = spec.split("@")[0]
            more += [(s, scenes.label_from(s)) for s in stems if fnmatch.fnmatch(s, pattern) and s not in picked]
    if "99_timeout" in stems:
        stills = (stills[:3] if len(stills) >= 4 else stills) + [("99_timeout", "Where it gave up")]
    seen = set()
    stills = [s for s in stills if not (s[0] in seen or seen.add(s[0]))][:4]

    def save(stem, file):
        im = Image.open(os.path.join(shots_dir, stem + ".png")).convert("RGB")
        if np is not None:
            m = magenta_pixels(im)
            if m >= MAGENTA_STILL:
                problems.append(f"missing-texture magenta in '{stem}' ({m} pixels)")
            if blank(im):
                problems.append(f"blank picture: '{stem}'")
        im.save(os.path.join(out, file), "JPEG", quality=82, optimize=True, progressive=True)
        return im

    still_out = []
    for i, (stem, label) in enumerate(stills, 1):
        save(stem, f"still-{i}.jpg")
        still_out.append({"file": f"still-{i}.jpg", "label": label})
    more_out = []
    for i, (stem, label) in enumerate(more, 1):
        save(stem, f"more-{i}.jpg")
        more_out.append({"file": f"more-{i}.jpg", "label": label})
    if len(still_out) < 2 and not any(p.startswith("picture missing") for p in problems):
        problems.append(f"only {len(still_out)} still(s)")

    # The GIF, start to end.
    frames = sorted(glob.glob(os.path.join(shots_dir, "gif", "*.png")))
    gif = None
    if frames:
        step = max(1, -(-len(frames) // GIF_FRAMES))
        picked = frames[::step]
        if picked[-1] != frames[-1]:
            picked.append(frames[-1])
        images = []
        for i, f in enumerate(picked):
            im = Image.open(f).convert("RGB")
            if im.width != GIF_WIDTH:
                im = im.resize((GIF_WIDTH, round(im.height * GIF_WIDTH / im.width)), Image.LANCZOS)
            if np is not None and i % 5 == 0:
                m = magenta_pixels(im)
                if m >= MAGENTA_FRAME:
                    problems.append(f"missing-texture magenta in a GIF frame ({os.path.basename(f)}, {m} pixels)")
            images.append(im.quantize(colors=96, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE))
        per = max(60, min(200, 12000 // len(images)))
        durations = [per] * (len(images) - 1) + [1500]
        gif = "anim.gif"
        images[0].save(os.path.join(out, gif), save_all=True, append_images=images[1:], duration=durations, loop=0,
                       optimize=True, disposal=1)
    else:
        problems.append("no GIF frames")
    if np is None:
        warnings.append("pixel checks skipped (no numpy)")

    # One entry per problem, in the order found.
    problems = list(dict.fromkeys(problems))
    failed_checks = [c["what"] for c in checks if not c.get("pass")]
    result = {
        "scene": name, "title": meta["title"], "group": meta["group"], "what": meta.get("what", ""),
        "pass": bool(report) and bool(checks) and not failed_checks and not problems,
        "reasons": [f"not done: {w}" for w in failed_checks] + problems,
        "checks": checks, "problems": problems, "warnings": warnings, "assets": sorted(set(assets)),
        "stills": still_out, "more": more_out, "gif": gif, "frames": len(frames),
        "seconds": seconds, "ticks": report.get("ticks") if report else None,
    }
    with open(os.path.join(out, "result.json"), "w") as f:
        json.dump(result, f, indent=1)
    return result


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    r = process(sys.argv[1], sys.argv[2], sys.argv[3])
    print(json.dumps({k: r[k] for k in ("scene", "pass", "reasons")}, indent=1))
