#!/usr/bin/env python3
"""Vanilla Minecraft textures as a local reference (never shipped, never copied into mods).

The client jar is downloaded once from Mojang and its textures cached under
~/.cache/mc-pixel-art/<version>/. Use them to *compare* your art against vanilla and to
sample palettes (colours are fine to reuse; drawings are not - draw your own shapes).

  vanilla.py list <regex>                 texture paths, e.g. 'item/.*_ingot', 'entity/villager/profession/'
  vanilla.py show <regex> [--scale 8]     contact sheet PNG of matching textures (prints its path)
  vanilla.py palette <name> [<name> ...]  colours of a texture, darkest -> lightest, with counts
  vanilla.py stats <regex>                colour count, value range, outline brightness, margins
  vanilla.py path <name>                  local file path (for preview.py --compare)
  vanilla.py json <path>                  vanilla JSON as a format reference: models/block/furnace,
                                          items/iron_ingot, blockstates/furnace, or a texture's .mcmeta

Regexes match the path without '.png' ('villager/profession/mason$' works).
Names may be short: 'iron_ingot' finds item/iron_ingot.png. Options: --mc <version> (default 1.21.1).
"""
import argparse
import colorsys
import hashlib
import io
import json
import os
import re
import sys
import urllib.request
import zipfile
from pathlib import Path

CACHE = Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache")) / "mc-pixel-art"
UA = {"User-Agent": "minecraft-pixel-art-skill/1.0"}


def _get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
        return r.read()


KEEP_JSON = ("models/", "items/", "blockstates/", "atlases/", "equipment/")
MARKER = ".cache-v2"


def textures_dir(mc="1.21.1"):
    """Return the cached textures dir for a version, downloading the client jar if needed.
    The cache keeps textures (+ .mcmeta) and the model/item/blockstate JSON (as format references)."""
    root = CACHE / mc
    tex = root / "assets" / "minecraft" / "textures"
    if (root / MARKER).exists():
        return tex
    man = json.loads(_get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
    entry = next((v for v in man["versions"] if v["id"] == mc), None)
    if entry is None:
        sys.exit(f"Unknown Minecraft version '{mc}'. Latest release: {man['latest']['release']}")
    meta = json.loads(_get(entry["url"]))
    dl = meta["downloads"]["client"]
    print(f"[vanilla] downloading Minecraft {mc} client ({dl['size'] // 1_000_000} MB; textures + model JSON are kept) ...",
          file=sys.stderr)
    data = _get(dl["url"])
    if hashlib.sha1(data).hexdigest() != dl["sha1"]:
        sys.exit("Download corrupted (sha1 mismatch); try again.")
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for n in z.namelist():
            keep = n.startswith("assets/minecraft/textures/") and (n.endswith(".png") or n.endswith(".mcmeta"))
            keep = keep or (n.endswith(".json") and any(n.startswith("assets/minecraft/" + k) for k in KEEP_JSON))
            if keep:
                out = root / n
                out.parent.mkdir(parents=True, exist_ok=True)
                out.write_bytes(z.read(n))
    (root / MARKER).write_text(mc + "\n")
    return tex


def assets_dir(mc="1.21.1"):
    return textures_dir(mc).parent


def all_textures(mc):
    tex = textures_dir(mc)
    return sorted(p.relative_to(tex).as_posix() for p in tex.rglob("*.png"))


def resolve(name, mc="1.21.1"):
    """'iron_ingot' -> item/iron_ingot.png path (prefers item/, then block/)."""
    tex = textures_dir(mc)
    if name.endswith(".png") and (tex / name).exists():
        return tex / name
    cands = [p for p in all_textures(mc) if p.endswith("/" + name + ".png") or p == name + ".png"]
    for pref in ("item/", "block/", "entity/"):
        for c in cands:
            if c.startswith(pref):
                return tex / c
    if cands:
        return tex / cands[0]
    sys.exit(f"No vanilla texture named '{name}' in Minecraft {mc}. Try: vanilla.py --mc {mc} list '{name}'"
             + ("" if mc.startswith("26.") else " (newer blocks/items, e.g. copper tools, need --mc 26.3)"))


def colours(img):
    from collections import Counter
    import numpy as np
    a = np.asarray(img.convert("RGBA")).reshape(-1, 4)
    return Counter(tuple(int(v) for v in p[:3]) for p in a if p[3] > 0)


def lum(c):
    r, g, b = (v / 255 for v in c[:3])
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def hexc(c):
    return "#%02x%02x%02x" % tuple(c[:3])


def cmd_list(a):
    rx = re.compile(a.regex)
    hits = [t for t in all_textures(a.mc) if rx.search(t[:-4] if t.endswith(".png") else t)]
    print("\n".join(hits[: a.limit]))
    if len(hits) > a.limit:
        print(f"... {len(hits) - a.limit} more")


def cmd_show(a):
    sys.path.insert(0, str(Path(__file__).parent))
    from preview import contact_sheet  # noqa: E402
    rx = re.compile(a.regex)
    tex = textures_dir(a.mc)
    hits = [t for t in all_textures(a.mc) if rx.search(t[:-4] if t.endswith(".png") else t)][: a.limit]
    if not hits:
        sys.exit(f"nothing matches in Minecraft {a.mc}" + ("" if a.mc.startswith("26.") else
                 " (newer textures, e.g. copper tools, need --mc 26.3)"))
    if a.out:
        out = Path(a.out)
    else:
        import tempfile
        d = Path(os.environ.get("MC_PIXEL_ART_PREVIEWS") or Path(tempfile.gettempdir()) / "mc-pixel-art-previews")
        d.mkdir(parents=True, exist_ok=True)
        out = d / f"vanilla_{a.mc}_{re.sub(r'[^a-z0-9]+', '_', a.regex.lower()).strip('_')[:40]}.png"
    # label with the last folders too, so villager/ and zombie_villager/ tiles are told apart
    labels = ["/".join(h[:-4].split("/")[-2:]) if h.count("/") < 3 else "/".join(h[:-4].split("/")[-3:]) for h in hits]
    contact_sheet([tex / h for h in hits], out, scale=a.scale, labels=labels)
    print(out)


def cmd_palette(a):
    from PIL import Image
    for name in a.names:
        p = resolve(name, a.mc)
        cnt = colours(Image.open(p))
        print(f"{p.relative_to(textures_dir(a.mc))}: {len(cnt)} colours, darkest -> lightest by perceived "
              f"lightness L (0-100); h/s/v are HSV")
        for c, n in sorted(cnt.items(), key=lambda kv: lum(kv[0])):
            h, s, v = colorsys.rgb_to_hsv(*(x / 255 for x in c))
            print(f"  {hexc(c)}  x{n:<3}  L{lum(c) * 100:4.0f}   h{h * 360:4.0f} s{s * 100:4.0f} v{v * 100:4.0f}")


def cmd_json(a):
    """Print a vanilla JSON file (model, item definition, blockstate) or texture .mcmeta as a format reference."""
    base = assets_dir(a.mc)
    rel = a.path.removeprefix("minecraft:").removesuffix(".json")
    cands = [base / (rel + ".json"), base / "textures" / (rel + ".png.mcmeta"), base / (rel + ".png.mcmeta")]
    for c in cands:
        if c.exists():
            print(f"# {c.relative_to(base)}  (Minecraft {a.mc})")
            print(c.read_text())
            return
    for png in (base / "textures" / (rel + ".png"), base / (rel + ".png")):
        if png.exists():
            sys.exit(f"{png.relative_to(base)} exists but has no .mcmeta in Minecraft {a.mc} "
                     "(no animation, GUI scaling or villager hat setting)")
    rx = re.compile(re.escape(rel.split("/")[-1]))
    hits = sorted(p.relative_to(base).as_posix() for p in base.rglob("*.json") if rx.search(p.stem))[:20]
    sys.exit(f"not found: {a.path}. Similar: " + (", ".join(hits) or "none") +
             "\nExamples: models/block/crafting_table, items/iron_ingot, blockstates/furnace, "
             "entity/villager/profession/farmer (its .mcmeta)")


def cmd_stats(a):
    sys.path.insert(0, str(Path(__file__).parent))
    from lint import measure  # noqa: E402
    rx = re.compile(a.regex)
    tex = textures_dir(a.mc)
    for t in [t for t in all_textures(a.mc) if rx.search(t[:-4] if t.endswith(".png") else t)][: a.limit]:
        m = measure(tex / t)
        print(f"{t:<60} {m['w']}x{m['h']}  colours {m['colours']:<3} value-range {m['value_range']:<3} "
              f"outline/inside {m.get('outline_ratio', '-')}  margins {m.get('margins', '-')}")


def cmd_path(a):
    print(resolve(a.name, a.mc))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--mc", default="1.21.1")
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("list"); p.add_argument("regex"); p.add_argument("--limit", type=int, default=200)
    p = sub.add_parser("show"); p.add_argument("regex"); p.add_argument("--scale", type=int, default=8)
    p.add_argument("--limit", type=int, default=48); p.add_argument("--out")
    p = sub.add_parser("palette"); p.add_argument("names", nargs="+")
    p = sub.add_parser("stats"); p.add_argument("regex"); p.add_argument("--limit", type=int, default=100)
    p = sub.add_parser("json"); p.add_argument("path")
    p = sub.add_parser("path"); p.add_argument("name")
    argv = sys.argv[1:]
    if "--mc" in argv[1:]:  # accept --mc after the subcommand too
        i = argv.index("--mc")
        argv = argv[i:i + 2] + argv[:i] + argv[i + 2:]
    a = ap.parse_args(argv)
    {"list": cmd_list, "show": cmd_show, "palette": cmd_palette, "stats": cmd_stats, "path": cmd_path, "json": cmd_json}[a.cmd](a)


if __name__ == "__main__":
    main()
