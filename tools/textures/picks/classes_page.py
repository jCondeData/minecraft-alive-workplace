#!/usr/bin/env python3
"""The owner's picker page for the class outfits (ROADMAP 34.17), made the way 21.1b's icon page was: every class's
versions side by side, one pick and a note per row, saved in the published page's own store (the `db` capability,
collection `classes`, one document per class: {"pick": "v2", "note": "...", "round": 1}), where the chat reads them
back. 34.18 swaps his picks in (tools/textures/art/classes.py, PICKED).

    python3 tools/textures/picks/classes_page.py     # -> build/texture-picks/classes/index.html (publish it as an Artifact)

Each version is drawn from its recipe in tools/textures/art/classes.py into build/texture-picks/classes/<class>/v<n>.png,
linted, and rendered on the real villager model: a tile (front, three-quarter and back, over a job of that class) and
a sheet over six jobs in all seven biomes (sheet_<class>_v<n>.png), both on the page.
"""
import base64
import html
import io
import json
import math
import os
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = ROOT / "build" / "texture-picks" / "classes"
os.environ.setdefault("ALIVE_ASSETS", str(OUT / "assets"))      # the recipes' own DRAW never writes from here
sys.path.insert(0, str(HERE.parent / "art"))
sys.path.insert(0, str(HERE.parent / "pxlib"))

import classes  # noqa: E402
import lint  # noqa: E402
import preview  # noqa: E402
import render3d  # noqa: E402
import vanilla  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

MC = "1.21.1"
COLLECTION = "classes"
BIOMES = ["plains", "desert", "jungle", "savanna", "snow", "swamp", "taiga"]
OURS = ROOT / "src/main/resources/assets/aliveworkplace/textures/entity/villager/profession"
VANILLA = vanilla.textures_dir(MC) / "entity" / "villager" / "profession"
# Six jobs, three of vanilla's and three of ours, with their hats and aprons: the job must still read under every layer.
JOBS = [("farmer", VANILLA / "farmer.png"), ("librarian", VANILLA / "librarian.png"), ("cleric", VANILLA / "cleric.png"),
        ("builder", OURS / "builder.png"), ("scholar", OURS / "scholar.png"), ("jeweller", OURS / "jeweller.png")]
# The job each class's tile is shown over: one the class holds (data/aliveworkplace/classes/*.json; nobles hold any).
TILE_JOB = {"artisan": OURS / "tinkerer.png", "burgher": OURS / "scholar.png", "noble": VANILLA / "librarian.png",
            "elder": VANILLA / "farmer.png"}

ROWS = [
    ("artisan", "Artisan", "Skilled hands: craftsmen and workshop folk, a step above the peasants."),
    ("burgher", "Burgher", "Townsfolk of standing: scholars, jewellers, the market's own."),
    ("noble", "Noble", "The great families: velvet, fur and gold."),
    ("elder", "Elder", "Anyone grown for 120 days (34.19): a face layer, worn with any class and any job."),
]
INTRO = ("Round 1: what each social class wears over the job's outfit, and the elder's face. Every version is shown on "
         "the villager model over a job that class holds, from the front, three-quarter and back; open “Every biome, "
         "six jobs” under a row to see it on all of them. The job's hat and apron always stay readable, nothing covers "
         "the level badge, and peasants keep the outfits you approved. Version 1 of each is in the game until you "
         "pick. Tap your favourite per row and add a note if you have one; tell me in the chat when you're done.")


def layered(job_png, layer, biome="plains", level="stone"):
    """The villager's texture as the game stacks it: base, biome, job (with the hat rule), level badge, then the class
    layer on top (34.18 draws it as one more layer over the job's)."""
    tex = preview.composite("villager", biome, job_png, level, MC)
    tex.alpha_composite(layer)
    return tex


def render(tex, yaw, scale, pitch=6):
    return render3d.render_model(render3d.villager_model(), tex, yaw=math.radians(yaw), pitch=math.radians(pitch), scale=scale)


def strip(images, gap=6):
    w = sum(i.width for i in images) + gap * (len(images) - 1)
    h = max(i.height for i in images)
    out = Image.new("RGBA", (w, h))
    x = 0
    for i in images:
        out.alpha_composite(i, (x, h - i.height))
        x += i.width + gap
    return out


def tile(name, layer):
    tex = layered(TILE_JOB[name], layer)
    views = (0, -40, 180) if name != "elder" else (0, -40, -90)
    return strip([render(tex, yaw, 5) for yaw in views])


def sheet(name, version, layer):
    """Six jobs down, seven biomes across (three-quarter view), and the back on plains at the end of each row."""
    scale = 4
    cells = []
    for job, png in JOBS:
        row = [render(layered(png, layer, b), -30, scale) for b in BIOMES]
        row.append(render(layered(png, layer), 180 if name != "elder" else -90, scale))
        cells.append((job, row))
    cw = max(c.width for _, r in cells for c in r) + 8
    ch = max(c.height for _, r in cells for c in r) + 8
    label = 70
    out = Image.new("RGBA", (label + cw * 8, 22 + ch * len(cells)), (58, 60, 64, 255))
    d = ImageDraw.Draw(out)
    for i, b in enumerate(BIOMES + ["back" if name != "elder" else "side"]):
        d.text((label + i * cw + 4, 6), b, fill=(230, 230, 220, 255))
    for j, (job, row) in enumerate(cells):
        d.text((4, 22 + j * ch + ch // 2), job, fill=(230, 230, 220, 255))
        for i, im in enumerate(row):
            out.alpha_composite(im, (label + i * cw + 4, 22 + j * ch + 4))
    return out


def uri(im):
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def build():
    OUT.mkdir(parents=True, exist_ok=True)
    rows, problems = [], []
    for name, title, what in ROWS:
        versions = []
        for version, caption, _ in classes.VERSIONS[name]:
            layer = classes.draw_version(name, version)
            png = OUT / name / f"{version}.png"
            png.parent.mkdir(parents=True, exist_ok=True)
            layer.save(png)
            report = [m for lvl, m in lint.lint(str(png), "villager")[1] if lvl != "INFO"]
            t = tile(name, Image.open(png).convert("RGBA"))
            s = sheet(name, version, Image.open(png).convert("RGBA"))
            t.save(OUT / f"tile_{name}_{version}.png")
            s.save(OUT / f"sheet_{name}_{version}.png")
            versions.append({"id": version, "caption": caption, "tile": uri(t), "sheet": uri(s),
                             "current": classes.PICKED[name] == version})
            if report:
                problems.append((name, version, report))
        rows.append((name, title, what, versions))
    return rows, problems


def card(name, title, what, versions):
    tiles, sheets = [], []
    for v in versions:
        label = f"Version {v['id'][1:]}" + (" (in the game now)" if v["current"] else "")
        alt = html.escape(f"{title}, {v['caption']}")
        tiles.append(f"""
        <button type="button" class="tile" id="{name}-{v['id']}" data-item="{name}" data-version="{v['id']}" aria-pressed="false">
          <span class="big"><img src="{v['tile']}" alt="{alt}, on the villager model"></span>
          <span class="row"><span class="label"><span class="vname">{label}</span><span class="cap">{html.escape(v['caption'])}</span></span></span>
          <span class="check" aria-hidden="true">Picked</span>
        </button>""")
        sheets.append(f"""<figure><figcaption>Version {v['id'][1:]}: {html.escape(v['caption'])}</figcaption>
          <img src="{v['sheet']}" alt="{alt}, over six jobs in every biome" loading="lazy"></figure>""")
    return f"""
    <article class="item" id="item-{name}" data-item="{name}">
      <header class="item-head"><h3>{html.escape(title)}</h3><p>{html.escape(what)}</p></header>
      <div class="tiles n{len(tiles)}" role="group" aria-label="{html.escape(title)} versions">{''.join(tiles)}
      </div>
      <details class="every"><summary>Every biome, six jobs</summary>{''.join(sheets)}</details>
      <label class="note-label" for="note-{name}">Guidance for this one <span>(optional)</span></label>
      <textarea class="note" id="note-{name}" data-item="{name}" rows="2" placeholder="What you like, what to change, ideas for another round"></textarea>
      <p class="saved" id="saved-{name}" aria-live="polite"></p>
    </article>"""


EXTRA_CSS = """
.big { aspect-ratio: auto; padding: 10px 4px; }
.big img { width: 100%; height: auto; }
.every summary { cursor: pointer; font-weight: 700; }
.every figure { margin: 10px 0 0; display: grid; gap: 4px; }
.every figcaption { font-size: 14px; color: var(--muted); }
.every img { width: 100%; height: auto; }
.hotbar img { width: 92%; height: auto; }
"""


def page(rows):
    template = (HERE / "page_template.html").read_text()
    groups = '<section class="group"><h2>Dressed by class</h2>' + "".join(card(*r) for r in rows) + "</section>"
    names = {r[0]: r[1] for r in rows}
    return (template.replace("Item Icon Picks", "Class Outfit Picks")
            .replace("</style>", EXTRA_CSS + "</style>")
            .replace("Guidance for all the icons", "Guidance for all the outfits")
            .replace("A style you want across all of them, things you'd like to see in the next round",
                     "Colours, how rich each class should look, anything for the next round")
            .replace("%%GROUPS%%", groups)
            .replace("%%INTRO%%", html.escape(INTRO))
            .replace("%%COLLECTION%%", COLLECTION)
            .replace("%%NAMES%%", json.dumps(names))
            .replace("%%ROUND%%", "1")
            .replace("%%COUNT%%", str(len(names))))


if __name__ == "__main__":
    rows, problems = build()
    out = OUT / "index.html"
    out.write_text(page(rows))
    print(out.relative_to(ROOT), f"{out.stat().st_size // 1024} KB")
    for name, version, report in problems:
        print(f"lint {name} {version}:", "; ".join(report))
    sys.exit(1 if problems else 0)
