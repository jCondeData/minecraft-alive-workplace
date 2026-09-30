#!/usr/bin/env python3
"""Preview sheets for judging textures the way players see them. Each command prints the PNG path.

  preview.py item <png> [--compare iron_ingot,stick,path/to/ref.png]
      inventory slots at GUI scale 1-3 next to vanilla items (or any PNG you pass), 16x zoom,
      silhouettes, light/dark/grass backgrounds, lint summary
  preview.py block <png> [--side s.png --bottom b.png --front f.png] [--compare stone]
      3D cube, 3x3 tiled wall (seams!), a wider field beside a vanilla block, zoom
  preview.py villager <profession.png> [--types plains,desert,snow] [--level stone] [--compare farmer]
      the profession on the real villager model (front, 3/4, side, back) over several biome
      outfits, the zombie version, and the flat texture with UV faces marked
  preview.py sheet <png> [<png> ...]      plain contact sheet
  preview.py blind <png> [<png> ...]      unlabelled shuffled inventory row for a name test
  preview.py pick <png> [<png> ...] [--refs a.png,b.png] [--title "..."]
      numbered pick sheet of variants (10x plus slots) for the user to choose from
  preview.py audit <textures dir>         contact sheet of a whole mod, worst lint first

Options: --out <file>, --mc <version for vanilla references> (default 1.21.1).
"""
import argparse
import json
import math
import sys
import os
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
# previews never go next to the textures (they would ship in the mod jar): a temp folder by default
PREVIEW_DIR = Path(os.environ.get("MC_PIXEL_ART_PREVIEWS") or Path(tempfile.gettempdir()) / "mc-pixel-art-previews")


def _default_out(png, kind):
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    return PREVIEW_DIR / f"{Path(png).stem}_{kind}.png"

import render3d  # noqa: E402
import lint as L  # noqa: E402

BG = (48, 48, 52, 255)
FG = (235, 235, 235, 255)
SLOT = (139, 139, 139, 255)
PANEL = (198, 198, 198, 255)


def _img(p):
    return Image.open(p).convert("RGBA")


def _up(im, s):
    return im.resize((im.width * s, im.height * s), Image.NEAREST)


def _label(d, xy, text, fill=FG):
    d.text(xy, text, fill=fill)


class Canvas:
    """Simple flow layout: add images left to right, wrap by rows."""

    def __init__(self, width=1400):
        self.items, self.width = [], width

    def row(self, title=None):
        self.items.append(("row", title))

    def add(self, im, caption=""):
        self.items.append(("img", im, caption))

    def text(self, lines):
        self.items.append(("text", lines))

    def render(self, out):
        x, y, rowh = 12, 12, 0
        placed = []
        line_no = 0          # images on one visual line share a caption baseline
        line_h = {}
        for it in self.items:
            if it[0] == "row":
                y += rowh + (26 if placed else 0)
                x, rowh = 12, 0
                line_no += 1
                if it[1]:
                    placed.append(("t", (12, y), it[1]))
                    y += 18
            elif it[0] == "img":
                im, cap = it[1], it[2]
                if x + im.width > self.width - 12 and x > 12:
                    y += rowh + 26
                    x, rowh = 12, 0
                    line_no += 1
                placed.append(("i", (x, y), im, cap, line_no))
                line_h[line_no] = max(line_h.get(line_no, 0), im.height)
                x += im.width + 14
                rowh = max(rowh, im.height + 14)
            elif it[0] == "text":
                y += rowh + 20
                x, rowh = 12, 0
                line_no += 1
                for line in it[1]:
                    placed.append(("t", (12, y), line))
                    y += 14
        H = y + rowh + 30
        W = self.width
        S = Image.new("RGBA", (W, H), BG)
        d = ImageDraw.Draw(S)
        for p in placed:
            if p[0] == "t":
                _label(d, p[1], p[2])
            else:
                (x, y), im, cap, ln = p[1], p[2], p[3], p[4]
                S.alpha_composite(im, (x, y))
                if cap:
                    _label(d, (x, y + line_h[ln] + 2), cap[: max(8, im.width // 6)])
        Path(out).parent.mkdir(parents=True, exist_ok=True)
        S.save(out)
        return out


def grid_zoom(im, s=16, bg=(120, 120, 124, 255)):
    z = Image.new("RGBA", (im.width * s, im.height * s), bg)
    # checker to show transparency
    d = ImageDraw.Draw(z)
    for y in range(im.height):
        for x in range(im.width):
            if (x + y) % 2:
                d.rectangle([x * s, y * s, x * s + s - 1, y * s + s - 1], fill=(132, 132, 136, 255))
    z.alpha_composite(_up(im, s))
    d = ImageDraw.Draw(z)
    for i in range(0, im.width + 1):
        d.line([(i * s, 0), (i * s, im.height * s)], fill=(0, 0, 0, 40))
    for j in range(0, im.height + 1):
        d.line([(0, j * s), (im.width * s, j * s)], fill=(0, 0, 0, 40))
    return z


def slots(ims, scale):
    """A row of inventory slots (vanilla slot colours) holding the icons at a GUI scale."""
    n = len(ims)
    W = (18 * n + 14) * scale
    H = (18 + 14) * scale
    p = Image.new("RGBA", (W, H), PANEL)
    d = ImageDraw.Draw(p)
    for i, im in enumerate(ims):
        x0, y0 = (7 + 18 * i) * scale, 7 * scale
        d.rectangle([x0, y0, x0 + 18 * scale - 1, y0 + 18 * scale - 1], fill=(55, 55, 55, 255))
        d.rectangle([x0 + scale, y0 + scale, x0 + 18 * scale - 1, y0 + 18 * scale - 1], fill=(255, 255, 255, 255))
        d.rectangle([x0 + scale, y0 + scale, x0 + 17 * scale - 1, y0 + 17 * scale - 1], fill=SLOT)
        icon = _up(im.resize((16, 16), Image.NEAREST) if im.size != (16, 16) else im, scale)
        p.alpha_composite(icon, (x0 + scale, y0 + scale))
    return p


def contact_sheet(paths, out, scale=8, labels=None, bg=SLOT):
    c = Canvas()
    for i, p in enumerate(paths):
        im = _img(p)
        tile = Image.new("RGBA", (im.width * scale, im.height * scale), bg)
        tile.alpha_composite(_up(im, scale))
        c.add(tile, (labels[i] if labels else Path(p).stem))
    return c.render(out)


def _lint_lines(path, kind):
    k, iss = L.lint(path, kind)
    return [f"lint [{k}] {lvl}: {msg}" for lvl, msg in iss] or ["lint: OK"]


# --------------------------------------------------------------------------- item

def _ref(name, mc):
    """A comparison texture: a file path (another mod's texture, a reference you were given) or a
    vanilla texture name."""
    import vanilla
    return Path(name) if Path(name).is_file() else vanilla.resolve(name, mc)


def cmd_item(a):
    import vanilla
    im = _img(a.png)
    comps = [_img(_ref(n, a.mc)) for n in a.compare] if a.compare else []
    c = Canvas()
    c.row("In the inventory (GUI scale 3, 2, 1) - yours first, then the comparisons")
    for s in (3, 2, 1):
        c.add(slots([im] + comps, s), f"x{s}")
    c.row("16x zoom")
    c.add(grid_zoom(im), Path(a.png).stem)
    for n, ci in zip(a.compare or [], comps):
        c.add(grid_zoom(ci), Path(n).stem)
    c.row("Silhouette only (x4): the shape alone must say what it is")
    for n, ci in [(Path(a.png).stem, im)] + list(zip(a.compare or [], comps)):
        sil = Image.new("RGBA", ci.size, (0, 0, 0, 0))
        sil.paste((34, 34, 38, 255), mask=ci.split()[3])
        t = Image.new("RGBA", (ci.width * 4 + 16, ci.height * 4 + 16), (205, 205, 205, 255))
        t.alpha_composite(_up(sil, 4), (8, 8))
        c.add(t, Path(n).stem)
    c.row("On light and dark backgrounds (x4)")
    for bgc in ((230, 230, 230, 255), (40, 40, 40, 255), (96, 140, 70, 255)):
        t = Image.new("RGBA", (im.width * 4 + 16, im.height * 4 + 16), bgc)
        t.alpha_composite(_up(im, 4), (8, 8))
        c.add(t)
    c.text(_lint_lines(a.png, "item"))
    print(c.render(a.out or _default_out(a.png, a.cmd)))


# --------------------------------------------------------------------------- block

def tiled(im, n=3, s=4):
    t = Image.new("RGBA", (im.width * n, im.height * n))
    for y in range(n):
        for x in range(n):
            t.paste(im, (x * im.width, y * im.height))
    return _up(t, s)


def cmd_block(a):
    import vanilla
    top = _img(a.png)
    side = _img(a.side) if a.side else top
    bottom = _img(a.bottom) if a.bottom else top
    front = _img(a.front) if a.front else side
    c = Canvas()
    c.row("3D (engine-like face shading)")
    c.add(render3d.render_block(top, side, bottom, front, scale=7), "yours")
    for n in a.compare or []:
        v = _img(_ref(n, a.mc))
        c.add(render3d.render_block(v, v, v, v, scale=7), n)
    faces = [("top", top)] + ([("side", side)] if a.side else []) + ([("front", front)] if a.front else [])
    c.row("3x3 tiled (seams and repeating features show up here)")
    for n, f in faces:
        c.add(tiled(f.crop((0, 0, 16, 16)), 3, 4), n)
    if a.compare:
        v = _img(_ref(a.compare[0], a.mc)).crop((0, 0, 16, 16))
        c.row(f"In a field next to vanilla {a.compare[0]} (contrast and colour in context)")
        field = Image.new("RGBA", (16 * 10, 16 * 5))
        for yy in range(5):
            for xx in range(10):
                field.paste(v if xx < 5 else top.crop((0, 0, 16, 16)), (xx * 16, yy * 16))
        c.add(_up(field, 3))
    c.row("16x zoom")
    for n, f in faces:
        c.add(grid_zoom(f), n)
    lines = []
    for n, p in [("top", a.png), ("side", a.side), ("bottom", a.bottom), ("front", a.front)]:
        if p:
            lines += [f"{n}: " + ln for ln in _lint_lines(p, "block")]
    c.text(lines)
    print(c.render(a.out or _default_out(a.png, a.cmd)))


# --------------------------------------------------------------------------- villager

def _hat_meta(png_path):
    m = Path(str(png_path) + ".mcmeta")
    if m.exists():
        try:
            return json.loads(m.read_text()).get("villager", {}).get("hat", "none")
        except ValueError:
            return "none"
    return "none"


def composite(kind, vtype, profession_png, level, mc, zombie_png=None):
    """Stack villager layers like VillagerProfessionLayer does (incl. the hat visibility rule)."""
    import vanilla
    tex = vanilla.textures_dir(mc) / "entity" / kind
    out = Image.new("RGBA", (64, 64))
    out.alpha_composite(_img(tex / f"{kind}.png"))
    tpath = tex / "type" / f"{vtype}.png"
    t = _img(tpath)
    prof_hat = _hat_meta(profession_png) if profession_png else "none"
    type_hat = _hat_meta(tpath)
    if not (prof_hat == "none" or (prof_hat == "partial" and type_hat != "full")):
        t = t.copy()
        d = ImageDraw.Draw(t)
        for part in ("hat", "hat_rim"):
            for (u0, v0, u1, v1) in render3d.villager_uv_regions()[part].values():
                d.rectangle([u0, v0, u1 - 1, v1 - 1], fill=(0, 0, 0, 0))
    out.alpha_composite(t)
    if profession_png:
        out.alpha_composite(_img(zombie_png if (kind == "zombie_villager" and zombie_png) else profession_png))
        if level:
            out.alpha_composite(_img(tex / "profession_level" / f"{level}.png"))
    return out


def uv_overlay(png, s=8):
    """The 64x64 texture at 8x on a plain dark ground, every model face boxed, front faces labelled."""
    im = _img(png)
    z = Image.new("RGBA", (im.width * s, im.height * s), (58, 58, 64, 255))
    d = ImageDraw.Draw(z)
    for part, faces in render3d.villager_uv_regions().items():
        for side, (u0, v0, u1, v1) in faces.items():
            d.rectangle([u0 * s, v0 * s, u1 * s - 1, v1 * s - 1], fill=(82, 82, 90, 255))
    z.alpha_composite(_up(im, s))
    d = ImageDraw.Draw(z)
    for part, faces in render3d.villager_uv_regions().items():
        for side, (u0, v0, u1, v1) in faces.items():
            d.rectangle([u0 * s, v0 * s, u1 * s - 1, v1 * s - 1], outline=(255, 200, 0, 255))
            if side == "front":
                d.text((u0 * s + 2, v0 * s + 1), part[:7], fill=(255, 235, 140, 255))
    return z


def cmd_villager(a):
    import vanilla
    prof = a.png
    zombie = a.zombie or str(prof).replace("/villager/profession/", "/zombie_villager/profession/")
    zombie = zombie if Path(zombie).exists() else prof
    m = render3d.villager_model()
    zm = render3d.zombie_villager_model()
    c = Canvas(1500)
    c.row(f"On the villager model - {a.types[0]} outfit, level {a.level} (front, 3/4, side, back, from above)")
    tex = composite("villager", a.types[0], prof, a.level, a.mc)
    for yaw, pitch, cap in ((0, 6, "front"), (-40, 10, "3/4"), (-90, 6, "side"), (180, 6, "back"), (-30, 45, "above")):
        c.add(render3d.render_model(m, tex, yaw=math.radians(yaw), pitch=math.radians(pitch), scale=9), cap)
    c.row("Other biome outfits (the profession layer must read on all of them)")
    for vt in a.types[1:]:
        tex = composite("villager", vt, prof, a.level, a.mc)
        c.add(render3d.render_model(m, tex, yaw=math.radians(-25), pitch=math.radians(6), scale=7), vt)
    ztex = composite("zombie_villager", a.types[0], prof, a.level, a.mc, zombie)
    c.add(render3d.render_model(zm, ztex, yaw=math.radians(-25), pitch=math.radians(6), scale=7), "zombie")
    if a.compare:
        c.row(f"Vanilla for comparison: {', '.join(a.compare)}")
        for n in a.compare:
            vp = vanilla.textures_dir(a.mc) / "entity" / "villager" / "profession" / f"{n}.png"
            tex = composite("villager", a.types[0], vp, a.level, a.mc)
            c.add(render3d.render_model(m, tex, yaw=math.radians(-25), pitch=math.radians(6), scale=7), n)
    c.row("The texture with model faces outlined (paint only inside boxes)")
    c.add(uv_overlay(prof), Path(prof).name)
    c.text(_lint_lines(prof, "villager") + [f"hat mcmeta: {_hat_meta(prof)}"])
    print(c.render(a.out or _default_out(prof, "villager")))


# --------------------------------------------------------------------------- sheet / audit

def cmd_sheet(a):
    print(contact_sheet(a.pngs, a.out or _default_out("sheet", "contact"), scale=a.scale))


def cmd_blind(a):
    """Unlabelled, shuffled inventory row (GUI scale 2 and 3) for a name test: give the image to
    someone who hasn't seen the work (a fresh agent, a friend) and ask what each icon is."""
    import random
    paths = list(a.pngs)
    random.Random(a.seed).shuffle(paths)
    ims = [_img(p) for p in paths]
    rows = [slots(ims, s) for s in (2, 3)]
    W = max(r.width for r in rows) + 24
    out = Image.new("RGBA", (W, sum(r.height for r in rows) + 36), BG)
    y = 12
    for r in rows:
        out.alpha_composite(r, (12, y))
        y += r.height + 12
    dest = Path(a.out or _default_out("blind", "test"))
    dest.parent.mkdir(parents=True, exist_ok=True)
    out.save(dest)
    key = dest.with_suffix(".key.txt")
    key.write_text("\n".join(f"{i + 1}: {Path(p).stem}" for i, p in enumerate(paths)) + "\n")
    print(dest)
    print(f"answer key (don't show it to the reviewer): {key}")


def cmd_pick(a):
    """Numbered pick sheet: each variant large (10x) with inventory slots at GUI scale 3/2/1 under
    it, optional reference icons at the end, and the whole set in one row at GUI scale 2. Show it
    to the user and ask for numbers; draw the next round around their picks."""
    c = Canvas(1300)
    c.row(a.title or "Pick the ones that read best (reply with numbers)")
    entries = [(f"#{i + 1} {Path(p).stem}", _img(p)) for i, p in enumerate(a.pngs)]
    entries += [(f"ref {Path(p).stem}", _img(p)) for p in (a.refs or [])]
    for cap, im in entries:
        big = Image.new("RGBA", (160, 160), SLOT)
        big.alpha_composite(_up(im.resize((16, 16), Image.NEAREST) if im.size != (16, 16) else im, 10))
        row = [slots([im], s) for s in (3, 2, 1)]
        tile = Image.new("RGBA", (max(160, sum(r.width for r in row) + 8), 160 + 6 + row[0].height), BG)
        tile.alpha_composite(big, (0, 0))
        x = 0
        for r in row:
            tile.alpha_composite(r, (x, 166))
            x += r.width + 4
        c.add(tile, cap)
    c.row("all at GUI scale 2 (in-game size)")
    c.add(slots([im for _, im in entries], 2))
    print(c.render(a.out or _default_out("pick", "sheet")))


def cmd_audit(a):
    root = Path(a.dir)
    rows = []
    for p in sorted(root.rglob("*.png")):
        if p.stem.endswith("_preview"):
            continue
        k, iss = L.lint(p)
        rows.append((L.score(iss), p, k))
    rows.sort(key=lambda r: -r[0])
    c = Canvas(1500)
    c.row(f"{root} - worst first (number = lint score, 0 is clean)")
    for sc, p, k in rows:
        im = _img(p)
        s = 6 if im.width <= 16 else 2
        tile = Image.new("RGBA", (max(96, im.width * s), im.height * s), SLOT)
        tile.alpha_composite(_up(im, s))
        c.add(tile, f"{sc} {p.stem}")
    print(c.render(a.out or _default_out(Path(a.dir).name or "textures", "audit")))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--mc", default="1.21.1")
    ap.add_argument("--out")
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("item"); p.add_argument("png"); p.add_argument("--compare", type=lambda s: s.split(","))
    p = sub.add_parser("block"); p.add_argument("png"); p.add_argument("--side"); p.add_argument("--bottom")
    p.add_argument("--front"); p.add_argument("--compare", type=lambda s: s.split(","))
    p = sub.add_parser("villager"); p.add_argument("png"); p.add_argument("--zombie")
    p.add_argument("--types", type=lambda s: s.split(","), default=["plains", "desert", "jungle", "savanna", "snow", "swamp", "taiga"])
    p.add_argument("--level", default="stone"); p.add_argument("--compare", type=lambda s: s.split(","))
    p = sub.add_parser("blind"); p.add_argument("pngs", nargs="+"); p.add_argument("--seed", type=int, default=1)
    p = sub.add_parser("sheet"); p.add_argument("pngs", nargs="+"); p.add_argument("--scale", type=int, default=8)
    p = sub.add_parser("pick"); p.add_argument("pngs", nargs="+"); p.add_argument("--title")
    p.add_argument("--refs", type=lambda s: s.split(","))
    p = sub.add_parser("audit"); p.add_argument("dir")
    for sp in sub.choices.values():
        sp.add_argument("--out", default=argparse.SUPPRESS)
        sp.add_argument("--mc", default=argparse.SUPPRESS)
    a = ap.parse_args()
    {"item": cmd_item, "block": cmd_block, "villager": cmd_villager, "sheet": cmd_sheet, "blind": cmd_blind, "pick": cmd_pick, "audit": cmd_audit}[a.cmd](a)


if __name__ == "__main__":
    main()
