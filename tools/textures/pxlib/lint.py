#!/usr/bin/env python3
"""Check textures against measured vanilla conventions.

  lint.py <png> [<png> ...] [--kind item|block|villager|gui|auto]
  lint.py audit <textures dir>      rank every texture in a mod by problems (worst first)

Kinds are guessed from the path (textures/item, textures/block, entity/villager) or size.
ERROR = will look broken in-game; WARN = off-style compared with vanilla; INFO = numbers.
Thresholds come from measuring vanilla 1.21 textures (see references/style.md).
"""
import argparse
import colorsys
import sys
from collections import Counter
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))


def _lum(rgb):
    return (0.2126 * rgb[..., 0] + 0.7152 * rgb[..., 1] + 0.0722 * rgb[..., 2]) / 2.55  # 0..100


def load(path):
    return np.asarray(Image.open(path).convert("RGBA")).astype(int)


def guess_kind(path, a):
    p = str(path).replace("\\", "/")
    if "/entity/" in p and "villager" in p:
        return "villager"
    if "/entity/" in p:
        return "entity"
    if "/item/" in p:
        return "item"
    if "/block/" in p:
        return "block"
    if "/gui/" in p or "/mob_effect/" in p:
        return "gui"
    if a.shape[:2] == (64, 64):
        return "villager"
    alpha = a[..., 3] > 0
    return "block" if alpha.all() else "item"


def ring_of(alpha):
    inner = alpha.copy()
    inner[1:, :] &= alpha[:-1, :]
    inner[:-1, :] &= alpha[1:, :]
    inner[:, 1:] &= alpha[:, :-1]
    inner[:, :-1] &= alpha[:, 1:]
    inner[0, :] = inner[-1, :] = False
    inner[:, 0] = inner[:, -1] = False
    return alpha & ~inner, alpha & inner


def measure(path):
    a = load(path)
    h, w = a.shape[:2]
    alpha = a[..., 3] > 0
    cols = Counter(tuple(c[:3]) for c in a[alpha])
    L = _lum(a[..., :3].astype(float))
    m = {"w": w, "h": h, "colours": len(cols), "opaque": int(alpha.sum())}
    if alpha.any():
        m["value_range"] = int(round(np.percentile(L[alpha], 98) - np.percentile(L[alpha], 2)))
    else:
        m["value_range"] = 0
    if not alpha.all() and alpha.any():
        ring, inside = ring_of(alpha)
        if inside.any():
            m["outline_ratio"] = round(float(L[ring].mean() / max(1, L[inside].mean())), 2)
        ys, xs = np.nonzero(alpha)
        m["margins"] = (int(xs.min()), int(ys.min()), int(w - 1 - xs.max()), int(h - 1 - ys.max()))
    return m


def issues_item(a, path):
    out = []
    h, w = a.shape[:2]
    alpha = a[..., 3] > 0
    L = _lum(a[..., :3].astype(float))
    if (w, h) != (16, 16):
        out.append(("WARN", f"size {w}x{h}: vanilla items are 16x16 (use 32x32 only with every texture in the set)"))
    semi = ((a[..., 3] > 0) & (a[..., 3] < 255)).sum()
    if semi:
        out.append(("ERROR", f"{semi} semi-transparent pixels: item alpha must be fully on or off"))
    if not alpha.any():
        return out + [("ERROR", "texture is empty")]
    black = ((a[..., :3] == 0).all(-1) & alpha).sum()
    if black:
        out.append(("WARN", f"{black} pure-black pixels: vanilla items never use #000000; outline with the material's darkest shade"))
    cols = Counter(tuple(c[:3]) for c in a[alpha])
    if len(cols) > 17:
        out.append(("WARN", f"{len(cols)} colours: vanilla items use 3-17 (median 8); merge near-duplicate shades"))
    elif len(cols) < 4:
        out.append(("WARN", f"only {len(cols)} colours: probably unshaded - add at least one shadow and one highlight"))
    ring, inside = ring_of(alpha)
    if inside.any():
        # medians: a few bright details on the rim (gold spikes, glints) shouldn't hide a dark outline
        ratio = np.median(L[ring]) / max(1, np.median(L[inside]))
        if ratio > 1.0:
            out.append(("WARN", f"outline no darker than the inside (ratio {ratio:.2f}; most vanilla items ~0.5-0.8): "
                                "add a 1 px outline in the material's own dark shades"))
        # light direction on the outline: top-left brighter than bottom-right
        ys, xs = np.nonzero(ring)
        cx, cy = xs.mean(), ys.mean()
        tl = ring & (np.add.outer(np.arange(h) - cy, np.arange(w) - cx) < 0)
        br = ring & (np.add.outer(np.arange(h) - cy, np.arange(w) - cx) > 0)
        # margin 10: flat one-colour outlines, bright spikes on the rim and a darker grip on one
        # side are fine; a reversed lit/shadow pair (~40 apart in vanilla) is not
        if tl.any() and br.any() and L[tl].mean() + 10 < L[br].mean():
            out.append(("WARN", "outline is lighter at the bottom-right than the top-left: light comes from the top-left"))
        # flat fills: large single-colour regions INSIDE the outline (a one-colour outline is fine)
        inner = Counter(tuple(c[:3]) for c in a[inside])
        big = [(c, n) for c, n in inner.items() if n >= 30]
        if big and len(cols) <= 6:
            out.append(("WARN", f"large flat area ({big[0][1]} px of {'#%02x%02x%02x' % big[0][0]}): shade the form (shadow + highlight)"))
    ys, xs = np.nonzero(alpha)
    margins = (xs.min(), ys.min(), w - 1 - xs.max(), h - 1 - ys.max())
    if min(margins) == 0:
        out.append(("INFO", f"touches the edge (margins l,t,r,b = {tuple(int(v) for v in margins)}); fine for tools/ingots, otherwise leave 1-2 px"))
    whites = ((a[..., :3] == 255).all(-1) & alpha).sum()
    if whites > 16:
        out.append(("WARN", f"{whites} pure-white pixels: white is for small specular highlights on shiny materials only"))
    s = _saturation(a, alpha)
    if (s > 0.92).sum() > 40:
        out.append(("WARN", f"{int((s > 0.92).sum())} px near full saturation: keep large areas moderately saturated"))
    out += _orphans(a, alpha, "item")
    out += _jaggies(alpha)
    out.append(("INFO", f"{len(cols)} colours (vanilla items 3-17, median 8), {int(whites)} white px, "
                        f"margins l,t,r,b = {tuple(int(v) for v in margins)}"))
    return out


def _saturation(a, alpha):
    rgb = a[..., :3] / 255.0
    mx, mn = rgb.max(-1), rgb.min(-1)
    s = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    return np.where(alpha, s, 0)


def _orphans(a, alpha, kind):
    """Single pixels unlike all 8 neighbours (noise)."""
    h, w = a.shape[:2]
    n = 0
    for y in range(h):
        for x in range(w):
            if not alpha[y, x]:
                continue
            c = tuple(a[y, x, :3])
            nbs = []
            for yy in range(y - 1, y + 2):
                for xx in range(x - 1, x + 2):
                    if (yy, xx) == (y, x):
                        continue
                    if kind == "block":
                        yy2, xx2 = yy % h, xx % w
                    else:
                        if not (0 <= yy < h and 0 <= xx < w):
                            continue
                        yy2, xx2 = yy, xx
                    if alpha[yy2, xx2]:
                        nbs.append(tuple(a[yy2, xx2, :3]))
            if len(nbs) >= 7 and c not in nbs:
                n += 1
    frac = n / max(1, alpha.sum())
    if kind == "item" and n > 6:
        return [("WARN", f"{n} isolated single pixels (noise): items should read as clean clusters")]
    if kind == "block" and frac > 0.45:
        return [("WARN", f"{frac:.0%} of pixels are isolated singles (vanilla natural blocks ~30%): too noisy, use small clusters")]
    return []


def _jaggies(alpha):
    """2x2 fully-filled blocks on the outer 1 px edge = doubled line corners ('L' jaggies)."""
    ring, _ = ring_of(alpha)
    n = 0
    h, w = alpha.shape
    for y in range(h - 1):
        for x in range(w - 1):
            if ring[y:y + 2, x:x + 2].all():
                n += 1
    return [("INFO", f"{n} 2x2 clumps in the outline: check for doubled/'L' corners on diagonals")] if n > 3 else []


def issues_block(a, path):
    out = []
    h, w = a.shape[:2]
    alpha = a[..., 3] > 0
    L = _lum(a[..., :3].astype(float))
    if (w, h) != (16, 16) and not (w == 16 and h % 16 == 0):
        out.append(("WARN", f"size {w}x{h}: blocks are 16x16 (animated: 16 x 16n with a .mcmeta)"))
    if h > w:
        a = a[:w]
        alpha = alpha[:w]
        L = L[:w]
        h = w
    semi = int(((a[..., 3] > 0) & (a[..., 3] < 255)).sum())
    if semi:
        out.append(("WARN", f"{semi} semi-transparent pixels: the block becomes TRANSLUCENT (sorted, slower, blended). "
                            "Intended only for glass/ice/slime-like blocks; otherwise make alpha 0 or 255"))
    if not alpha.all():
        out.append(("INFO", f"{int((~alpha).sum())} transparent pixels: cutout block. 1.21.1-1.21.11: register a cutout "
                            "render layer in client code or they render black; 26.1+: picked automatically from the texture"))
    cols = Counter(tuple(c[:3]) for c in a[alpha])
    vr = np.percentile(L[alpha], 98) - np.percentile(L[alpha], 2) if alpha.any() else 0
    out.append(("INFO", f"{len(cols)} colours, value range {vr:.0f} (natural blocks 15-40, workstation faces up to ~95)"))
    if len(cols) > 32:
        out.append(("WARN", f"{len(cols)} colours: too many shades make tiles blurry; vanilla natural 4-9, workstations 10-29"))
    top, bottom = L[: h // 2][alpha[: h // 2]].mean(), L[h // 2:][alpha[h // 2:]].mean()
    left, right = L[:, : w // 2][alpha[:, : w // 2]].mean(), L[:, w // 2:][alpha[:, w // 2:]].mean()
    if abs(top - bottom) > 6 or abs(left - right) > 6:
        out.append(("WARN", f"baked-in light (top-bottom {top - bottom:+.0f}, left-right {left - right:+.0f}): "
                            "the engine shades faces; keep halves within ~3"))
    from px import seam_check
    ratio, patterned = seam_check(a)
    ring, inside = ring_of(np.ones_like(alpha))
    frame = L[inside].mean() - L[ring].mean()
    if ratio > 1.6 and frame < 10 and not patterned:
        out.append(("WARN", f"tile edges show as lines when repeated (edge jump {ratio:.1f}x the inner average): "
                            "a seam on natural tiles (fix: shift(8, 8), repair, shift back); intended only on "
                            "built/polished blocks meant to show a grid. Check the 3x3 preview"))
    elif ratio > 1.6 and patterned:
        out.append(("INFO", f"edge line {ratio:.1f}x the inner average, repeated inside the tile: brick/tile mortar, fine"))
    if frame > 12:
        out.append(("INFO", f"has a darker frame ({frame:.0f} darker): right for built blocks (machines, storage), wrong for natural tiles"))
    out += _orphans(a, alpha, "block")
    return out


def issues_villager(a, path):
    import villager as V
    out = []
    if a.shape[:2] != (64, 64):
        return [("ERROR", f"size {a.shape[1]}x{a.shape[0]}: villager layers are 64x64")]
    alpha = a[..., 3] > 0
    semi = ((a[..., 3] > 0) & (a[..., 3] < 255)).sum()
    if semi:
        out.append(("WARN", f"{semi} semi-transparent pixels: entity cutout rendering makes them fully on/off"))
    stray = V.check_layout(a)
    if stray:
        out.append(("WARN", f"{len(stray)} pixels outside every model face (never rendered), e.g. {stray[:4]}"))
    # flat garment faces
    flat = []
    for part, faces in V.UV.items():
        for side, (u0, v0, u1, v1) in faces.items():
            if (part, side) in V.HIDDEN_FACES:
                continue
            reg = a[v0:v1, u0:u1]
            m = reg[..., 3] > 0
            if m.sum() >= 24:
                cs = Counter(tuple(c[:3]) for c in reg[m])
                if cs.most_common(1)[0][1] / m.sum() > 0.85 and len(cs) <= 2:
                    flat.append(f"{part}.{side}")
    if flat:
        out.append(("WARN", f"flat, unshaded faces: {', '.join(flat[:8])}: add folds/top light/hem"))
    painted = {p for p, faces in V.UV.items() for (u0, v0, u1, v1) in faces.values() if (a[v0:v1, u0:u1, 3] > 0).any()}
    rim_f, rim_b = V.UV["hat_rim"]["front"], V.UV["hat_rim"]["back"]
    both = (a[rim_f[1]:rim_f[3], rim_f[0]:rim_f[2], 3] > 0) & np.fliplr(a[rim_b[1]:rim_b[3], rim_b[0]:rim_b[2], 3] > 0)
    if both.sum() > 8:
        out.append(("WARN", f"brim painted on both rim faces ({int(both.sum())} px overlap): two planes 1 px apart read "
                            "as a double brim from the side. Paint only the front face (hat_rim front, 31,48-47,64)"))
    # headwear = something covering the crown: the rim plane, or most of the hat's top face.
    # Face accessories (goggles, monocles, straps) don't need the .mcmeta: vanilla armorer,
    # cartographer and cleric paint the hat layer and ship none, so biome hats still show with them.
    u0, v0, u1, v1 = V.UV["hat"]["top"]
    top_cover = (a[v0:v1, u0:u1, 3] > 0).mean()
    headwear = "hat_rim" in painted or top_cover >= 0.5
    p = Path(path)
    meta = p.with_suffix(".png.mcmeta")
    is_prof = "/profession/" in str(p).replace("\\", "/")
    if is_prof and headwear and not meta.exists():
        out.append(("WARN", "paints headwear but has no .png.mcmeta: biome hats will draw through it "
                            "(write {\"villager\":{\"hat\":\"full\"}}, or \"partial\")"))
    elif is_prof and "hat" in painted and not headwear and not meta.exists():
        out.append(("INFO", "hat layer used for a face accessory only: no .mcmeta is fine (biome hats still show, "
                            "like vanilla armorer/cartographer)"))
    elif is_prof and meta.exists() and not headwear and '"full"' in meta.read_text():
        out.append(("INFO", "hat .mcmeta is \"full\" but no headwear is painted: the biome's hat is hidden and "
                            "nothing replaces it"))
    if "head" in painted or "nose" in painted:
        out.append(("INFO", "paints the head/nose itself: profession layers normally paint only the hat layer (glasses etc.)"))
    if "/villager/profession/" in str(p).replace("\\", "/"):
        z = Path(str(p).replace("/villager/profession/", "/zombie_villager/profession/"))
        if not z.exists():
            out.append(("WARN", f"no zombie copy at {z}: zombified villagers of this profession render the missing-texture checkerboard"))
    cols = Counter(tuple(c[:3]) for c in a[alpha])
    out.append(("INFO", f"{int(alpha.sum())} painted px, {len(cols)} colours, parts: {', '.join(sorted(painted))}"))
    return out


def issues_gui(a, path):
    out = []
    semi = ((a[..., 3] > 0) & (a[..., 3] < 255)).sum()
    if semi:
        out.append(("WARN", f"{semi} semi-transparent pixels"))
    return out


def lint(path, kind="auto"):
    a = load(path)
    k = guess_kind(path, a) if kind == "auto" else kind
    fn = {"item": issues_item, "block": issues_block, "villager": issues_villager, "gui": issues_gui}.get(k)
    return k, (fn(a, path) if fn else [("INFO", f"no rules for kind '{k}'")])


def score(issues):
    return sum({"ERROR": 10, "WARN": 3, "INFO": 0}[lvl] for lvl, _ in issues)


def main():
    if len(sys.argv) > 2 and sys.argv[1] == "audit":
        root = Path(sys.argv[2])
        rows = []
        for p in sorted(root.rglob("*.png")):
            if p.stem.endswith("_preview"):
                continue  # an old preview sheet saved next to the textures, not a texture
            try:
                k, iss = lint(p)
            except Exception as e:  # noqa: BLE001
                rows.append((99, p, "?", [("ERROR", str(e))]))
                continue
            rows.append((score(iss), p, k, iss))
        rows.sort(key=lambda r: -r[0])
        for sc, p, k, iss in rows:
            probs = [m for lvl, m in iss if lvl != "INFO"]
            print(f"{sc:>3}  {k:<8} {p.relative_to(root)}" + (f"\n       - " + "\n       - ".join(probs) if probs else ""))
        print(f"\n{len(rows)} textures; {sum(1 for r in rows if r[0] == 0)} clean")
        return
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--kind", default="auto")
    a = ap.parse_args()
    worst = 0
    for p in a.paths:
        k, iss = lint(p, a.kind)
        print(f"{p}  [{k}]")
        for lvl, msg in iss:
            print(f"  {lvl:<5} {msg}")
        if not any(l != "INFO" for l, _ in iss):
            print("  OK")
        worst = max(worst, max((10 if l == "ERROR" else 0) for l, _ in iss) if iss else 0)
    sys.exit(1 if worst >= 10 else 0)


if __name__ == "__main__":
    main()
