#!/usr/bin/env python3
"""Material colour ramps measured from vanilla textures (colours only; shapes are never copied).

  palette.py list [regex]            named ramps in palettes.json (use in recipes: P('iron'))
  palette.py show <name> [<name>..]  swatch image of ramps (prints path)
  palette.py from <vanilla texture>  measure a ramp from any vanilla texture, e.g. 'item/blaze_rod'
  palette.py build [--mc 1.21.1]     (maintainers) regenerate palettes.json from the vanilla cache

A ramp is darkest -> lightest interior colours; items also carry their measured outline pair
(lit top-left side, shadow bottom-right side).
"""
import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import vanilla  # noqa: E402

# name -> (texture, kind, [optional (x0,y0,x1,y1) region or None], [optional Minecraft version])
HANDLE = {(0x49, 0x36, 0x15), (0x28, 0x1e, 0x0b), (0x68, 0x4e, 0x1e), (0x89, 0x67, 0x27),   # wooden stick
          (0x2f, 0x21, 0x22), (0x73, 0x45, 0x43), (0x43, 0x40, 0x43), (0x3b, 0x39, 0x3b)}   # netherite handle
# ramps that aren't a whole texture: added as-is by `build`
MANUAL = {
    "netherite_handle": {"ramp": ["#3b393b", "#434043", "#734543"], "outline": ["#2f2122", "#231012"],
                         "source": "item/netherite_axe handle pixels (netherite tools don't use the stick)"},
}
SOURCES = {
    # item materials (have outlines)
    "iron": ("item/iron_ingot", "item"), "gold": ("item/gold_ingot", "item"), "copper": ("item/copper_ingot", "item"),
    "netherite": ("item/netherite_ingot", "item"), "diamond": ("item/diamond", "item"), "emerald": ("item/emerald", "item"),
    "amethyst": ("item/amethyst_shard", "item"), "redstone": ("item/redstone", "item"), "lapis": ("item/lapis_lazuli", "item"),
    "quartz": ("item/quartz", "item"), "coal": ("item/coal", "item"), "stick": ("item/stick", "item"),
    "leather": ("item/leather", "item"), "paper": ("item/paper", "item"), "string": ("item/string", "item"),
    "bone": ("item/bone", "item"), "flint": ("item/flint", "item"), "brick_item": ("item/brick", "item"),
    "clay_ball": ("item/clay_ball", "item"), "slime": ("item/slime_ball", "item"), "honeycomb": ("item/honeycomb", "item"),
    "feather": ("item/feather", "item"), "gunpowder": ("item/gunpowder", "item"), "bread": ("item/bread", "item"),
    "apple": ("item/apple", "item"), "carrot": ("item/carrot", "item"), "wheat": ("item/wheat", "item"),
    "blaze": ("item/blaze_rod", "item"), "ender_pearl": ("item/ender_pearl", "item"), "prismarine": ("item/prismarine_shard", "item"),
    "glowstone": ("item/glowstone_dust", "item"), "book": ("item/book", "item"), "map": ("item/map", "item"),
"bowl": ("item/bowl", "item"), "wool_item": ("item/white_dye", "item"),
    "rabbit_hide": ("item/rabbit_hide", "item"), "ink": ("item/ink_sac", "item"), "echo": ("item/echo_shard", "item"),
    # tool heads (handle colours excluded; the handle itself is P('stick')). Tools use other shades
    # and darker outlines than the ingots of the same material
    **{f"{m}_tool": ([f"item/{p}_{t}" for t in ("pickaxe", "axe", "shovel", "hoe", "sword")], "tool")
       for m, p in (("wood", "wooden"), ("stone", "stone"), ("iron", "iron"), ("gold", "golden"),
                    ("diamond", "diamond"), ("netherite", "netherite"))},
    "copper_tool": ([f"item/copper_{t}" for t in ("pickaxe", "axe", "shovel", "hoe", "sword")], "tool", None, "26.3"),
    # natural / built blocks (no outlines)
    "stone": ("block/stone", "block"), "cobblestone": ("block/cobblestone", "block"), "deepslate": ("block/deepslate", "block"),
    "andesite": ("block/andesite", "block"), "granite": ("block/granite", "block"), "diorite": ("block/diorite", "block"),
    "tuff": ("block/tuff", "block"), "calcite": ("block/calcite", "block"), "sandstone": ("block/sandstone_top", "block"),
    "red_sandstone": ("block/red_sandstone_top", "block"), "sand": ("block/sand", "block"), "red_sand": ("block/red_sand", "block"),
    "gravel": ("block/gravel", "block"), "dirt": ("block/dirt", "block"), "clay": ("block/clay", "block"),
    "mud_bricks": ("block/mud_bricks", "block"), "bricks": ("block/bricks", "block"), "stone_bricks": ("block/stone_bricks", "block"),
    "smooth_stone": ("block/smooth_stone", "block"), "terracotta": ("block/terracotta", "block"),
    "netherrack": ("block/netherrack", "block"), "blackstone": ("block/blackstone", "block"), "obsidian": ("block/obsidian", "block"),
    "snow": ("block/snow", "block"), "ice": ("block/ice", "block"), "glass": ("block/glass", "block"), "moss": ("block/moss_block", "block"),
    "hay": ("block/hay_block_side", "block"), "iron_block": ("block/iron_block", "block"), "gold_block": ("block/gold_block", "block"),
    "copper_block": ("block/copper_block", "block"), "prismarine_block": ("block/prismarine", "block"),
    "oak_planks": ("block/oak_planks", "block"), "spruce_planks": ("block/spruce_planks", "block"),
    "birch_planks": ("block/birch_planks", "block"), "jungle_planks": ("block/jungle_planks", "block"),
    "acacia_planks": ("block/acacia_planks", "block"), "dark_oak_planks": ("block/dark_oak_planks", "block"),
    "mangrove_planks": ("block/mangrove_planks", "block"), "cherry_planks": ("block/cherry_planks", "block"),
    "bamboo_planks": ("block/bamboo_planks", "block"), "crimson_planks": ("block/crimson_planks", "block"),
    "warped_planks": ("block/warped_planks", "block"), "oak_log": ("block/oak_log", "block"),
    "spruce_log": ("block/spruce_log", "block"), "birch_log": ("block/birch_log", "block"), "dark_oak_log": ("block/dark_oak_log", "block"),
    "bookshelf_books": ("block/bookshelf", "block"),
    # villager clothing (robe = jacket front face 8..16 x 44..64)
    "villager_skin": ("entity/villager/villager", "region", (0, 8, 8, 18)),
    "robe_plains": ("entity/villager/type/plains", "region", (6, 44, 14, 64)),
    "robe_desert": ("entity/villager/type/desert", "region", (6, 44, 14, 64)),
    "robe_jungle": ("entity/villager/type/jungle", "region", (6, 44, 14, 64)),
    "robe_savanna": ("entity/villager/type/savanna", "region", (6, 44, 14, 64)),
    "robe_snow": ("entity/villager/type/snow", "region", (6, 44, 14, 64)),
    "robe_swamp": ("entity/villager/type/swamp", "region", (6, 44, 14, 64)),
    "robe_taiga": ("entity/villager/type/taiga", "region", (6, 44, 14, 64)),
    "straw": ("entity/villager/profession/farmer", "region", (30, 47, 64, 64)),
}
for _c in ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
           "purple", "blue", "brown", "green", "red", "black"):
    SOURCES[f"wool_{_c}"] = (f"block/{_c}_wool", "block")
    SOURCES[f"concrete_{_c}"] = (f"block/{_c}_concrete", "block")


def _lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def _load(path):
    """One texture, or several side by side (with a transparent gap) measured as one."""
    if isinstance(path, (list, tuple)):
        ims = [np.asarray(Image.open(p).convert("RGBA")) for p in path]
        h = max(i.shape[0] for i in ims)
        gap = np.zeros((h, 1, 4), np.uint8)
        return np.concatenate(sum(([np.pad(i, ((0, h - i.shape[0]), (0, 0), (0, 0))), gap] for i in ims), []), axis=1)
    return np.asarray(Image.open(path).convert("RGBA")).copy()


def measure(path, kind, region=None, max_colours=8):
    a = _load(path).copy()
    if region:
        x0, y0, x1, y1 = region
        a = a[y0:y1, x0:x1]
    if kind == "tool":  # drop the handle: measure the head only, as an item
        for c in HANDLE:
            a[(a[..., 0] == c[0]) & (a[..., 1] == c[1]) & (a[..., 2] == c[2])] = 0
        kind = "item"
    alpha = a[..., 3] > 0
    outline = None
    interior = alpha
    if kind == "item":
        ring = alpha & ~(np.roll(alpha, 1, 0) & np.roll(alpha, -1, 0) & np.roll(alpha, 1, 1) & np.roll(alpha, -1, 1))
        interior = alpha & ~ring
        h, w = alpha.shape
        yy, xx = np.mgrid[0:h, 0:w]
        # lit side: ring pixels whose inside neighbour is to the right/below
        lit = ring & ((np.roll(alpha, -1, 1) & ~np.roll(alpha, 1, 1)) | (np.roll(alpha, -1, 0) & ~np.roll(alpha, 1, 0)))
        dark = ring & ~lit
        def common(mask):
            cs = Counter(tuple(int(v) for v in c[:3]) for c in a[mask])
            return "#%02x%02x%02x" % min(cs, key=_lum) if cs else None  # the dominant dark tone
        def dominant(mask):
            cs = Counter(tuple(int(v) for v in c[:3]) for c in a[mask])
            return "#%02x%02x%02x" % cs.most_common(1)[0][0] if cs else None
        outline = [dominant(lit) or common(ring), dominant(dark) or common(ring)]
        if not interior.any():
            interior = alpha
    cs = Counter(tuple(int(v) for v in c[:3]) for c in a[interior])
    if kind == "item":
        # colours used (almost) only on the outer ring are outline colours, not body shades
        ring_cs = Counter(tuple(int(v) for v in c[:3]) for c in a[alpha & ~interior])
        for c in list(cs):
            if ring_cs.get(c, 0) >= 2 * cs[c]:
                del cs[c]
    # merge near-identical shades (keep the more frequent)
    merged = Counter()
    for c, n in cs.most_common():
        near = next((m for m in merged if abs(_lum(m) - _lum(c)) < 4 and max(abs(m[i] - c[i]) for i in range(3)) < 14), None)
        if near:
            merged[near] += n
        else:
            merged[c] = n
    top = [c for c, _ in merged.most_common(max_colours)]
    top.sort(key=_lum)
    src = [str(p).split("textures/")[-1] for p in path] if isinstance(path, (list, tuple)) else str(path).split("textures/")[-1]
    return {"ramp": ["#%02x%02x%02x" % c for c in top], "outline": outline, "source": src}


def build(mc):
    out = {}
    for name, spec in SOURCES.items():
        tex = vanilla.textures_dir(spec[3] if len(spec) > 3 and spec[3] else mc)
        names = spec[0] if isinstance(spec[0], list) else [spec[0]]
        paths = [tex / (n + ".png") for n in names]
        missing = [q for q in paths if not q.exists()]
        if missing:
            print("missing", *missing, file=sys.stderr)
            continue
        p = paths if isinstance(spec[0], list) else paths[0]
        kind = spec[1]
        region = spec[2] if len(spec) > 2 else None
        out[name] = measure(p, kind if kind in ("item", "tool") else "block", region)
        if len(spec) > 3 and spec[3]:
            out[name]["mc"] = spec[3]
        if out[name]["outline"] is None:
            out[name].pop("outline")
    out.update(MANUAL)
    (HERE / "palettes.json").write_text(json.dumps(out, indent=1))
    print(f"wrote {len(out)} ramps to {HERE / 'palettes.json'}")


def swatches(names, path):
    import px
    rows = [(n, px.P(n)) for n in names]
    cell = 22
    W = 140 + cell * max(len(r) + 3 for _, r in rows)
    im = Image.new("RGBA", (W, 8 + len(rows) * (cell + 6)), (45, 45, 45, 255))
    d = ImageDraw.Draw(im)
    for i, (n, r) in enumerate(rows):
        y = 4 + i * (cell + 6)
        d.text((6, y + 5), n[:20], fill=(230, 230, 230, 255))
        for j, c in enumerate(r):
            d.rectangle([140 + j * cell, y, 140 + j * cell + cell - 2, y + cell], fill=tuple(c))
        x = 140 + (len(r) + 1) * cell
        d.rectangle([x, y, x + cell - 2, y + cell], fill=tuple(r.outline_light))
        d.rectangle([x + cell, y, x + 2 * cell - 2, y + cell], fill=tuple(r.outline_dark))
    im.save(path)
    return path


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--mc", default="1.21.1")
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("list"); p.add_argument("regex", nargs="?", default=".")
    p = sub.add_parser("show"); p.add_argument("names", nargs="+"); p.add_argument("--out", default="palette_swatches.png")
    p = sub.add_parser("from"); p.add_argument("texture"); p.add_argument("--kind", default="auto")
    p = sub.add_parser("build")
    argv = sys.argv[1:]
    if "--mc" in argv[1:]:  # accept --mc after the subcommand too
        i = argv.index("--mc")
        argv = argv[i:i + 2] + argv[:i] + argv[i + 2:]
    a = ap.parse_args(argv)
    if a.cmd == "build":
        return build(a.mc)
    if a.cmd == "from":
        path = vanilla.resolve(a.texture, a.mc)
        kind = a.kind if a.kind != "auto" else ("item" if "/item/" in str(path) else "block")
        print(json.dumps(measure(path, kind), indent=1))
        return
    data = json.loads((HERE / "palettes.json").read_text())
    if a.cmd == "list":
        rx = re.compile(a.regex)
        for k, v in data.items():
            if rx.search(k):
                print(f"{k:<22} {' '.join(v['ramp'])}" + (f"   outline {' '.join(v['outline'])}" if v.get("outline") else ""))
    elif a.cmd == "show":
        print(swatches(a.names, a.out))


if __name__ == "__main__":
    main()
