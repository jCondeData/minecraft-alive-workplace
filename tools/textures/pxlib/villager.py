"""Villager profession textures: paint garments onto the real 64x64 villager UV layout.

    from villager import *
    t = VillagerTexture()
    robe(t, P('wool_blue'))                 # or apron(), vest(), sleeves() ...
    belt(t, P('leather'))
    hat(t, P('straw'), style='brim')
    t.save_profession('src/main/resources/assets/<modid>', '<profession_id>', hat='full')

Every garment paints the right faces of the right boxes (front/back/sides/top), with top-lit
shading, 1 px folds and a darker hem, so it reads on the 3D model. Check with
    python3 scripts/preview.py villager <png>
UV layout (texture px): see references/villager.md.
"""
import json
import random
from pathlib import Path

import numpy as np

from px import Sprite, Ramp, ramp, rgba, hexc, mix, luminance, clusters, P, hsv, from_hsv  # noqa: F401  (re-exported)
import render3d

UV = render3d.villager_uv_regions()  # part -> face -> (u0, v0, u1, v1)
SIDES = ("front", "west", "back", "east")
# faces the villager model (almost) never shows: under the head, inside the crossed arms, the
# body box (always enclosed by the biome layer's jacket), the underside of the robe
HIDDEN_FACES = {("jacket", "top"), ("jacket", "bottom"), ("leg", "top"), ("arms_middle", "back"),
                ("arms_middle", "west"), ("arms_middle", "east"), ("head", "bottom"), ("nose", "back")}
HIDDEN_FACES |= {("body", side) for side in ("top", "bottom", "front", "back", "west", "east")}


class Face:
    """One box face as its own little canvas: local (x, y) from the face's top-left as it appears on
    the model. On front faces x = 0 is on the viewer's left (the villager's right)."""

    def __init__(self, tex, part, side):
        self.t, self.part, self.side = tex, part, side
        self.u0, self.v0, self.u1, self.v1 = UV[part][side]
        self.w, self.h = self.u1 - self.u0, self.v1 - self.v0

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.t.a[self.v0 + y, self.u0 + x] = rgba(c)

    def get(self, x, y):
        return tuple(int(v) for v in self.t.a[self.v0 + y, self.u0 + x])

    def fill(self, c, rows=None, cols=None):
        ys = range(self.h) if rows is None else rows
        xs = range(self.w) if cols is None else cols
        for y in ys:
            for x in xs:
                self.put(x, y, c)

    def clear(self, rows=None, cols=None):
        self.fill((0, 0, 0, 0), rows, cols)

    def paste(self, sprite, x=0, y=0):
        for yy in range(sprite.h):
            for xx in range(sprite.w):
                if sprite.a[yy, xx, 3]:
                    self.put(x + xx, y + yy, tuple(sprite.a[yy, xx]))


class VillagerTexture(Sprite):
    def __init__(self):
        super().__init__(64, 64)

    def face(self, part, side):
        return Face(self, part, side)

    def save_profession(self, assets_dir, profession, hat=None, zombie=True):
        """Write textures/entity/villager/profession/<id>.png (+ .mcmeta if hat) and the zombie copy.
        assets_dir = .../src/main/resources/assets/<namespace>. hat: 'full' hides the biome hat,
        'partial' hides it unless the biome hat is 'full', None keeps both."""
        base = Path(assets_dir) / "textures" / "entity"
        written = []
        for kind in (("villager", "zombie_villager") if zombie else ("villager",)):
            p = base / kind / "profession" / f"{profession}.png"
            self.save(p)
            written.append(p)
            meta = p.with_suffix(".png.mcmeta")
            if hat:
                meta.write_text(json.dumps({"villager": {"hat": hat}}) + "\n")
                written.append(meta)
            elif meta.exists():
                meta.unlink()
        return written


# =========================================================================== shading helpers

def cloth(base, n=5, spread=0.28, hue_shift=6.0, name=""):
    """Fabric ramp around a mid colour: close value steps (vanilla robes step ~8-12 luminance) and
    highlights that stay saturated, so the weave texture reads as cloth, not chalky speckle.
    Use for every garment; px.ramp() is tuned for hard materials (metal, gems)."""
    h, s_, v = hsv(base)
    warm = 1 if (h < 60 or h > 300) else -1
    out = []
    for i in range(n):
        t = (i / (n - 1)) * 2 - 1 if n > 1 else 0
        vv = v * (1 + spread * t) if t < 0 else v + (1 - v) * spread * t * 0.9 + v * 0.06 * t
        ss = s_ * (1 + 0.12 * -t) if t < 0 else s_ * (1 - 0.18 * t)
        hh = (h + warm * (hue_shift / 2) * t) % 360
        out.append(from_hsv(hh, min(1.0, ss), min(1.0, vv)))
    return Ramp(out, name=name)


CLOTH_NOISE = 0.06  # default weave density for every garment helper; noise=0 gives clean cloth


def _cloth(face, ramp, rows=None, seed=0, base_index=None, noise=None, top_light=1, hem=True):
    """Fill a face with cloth: base colour, a lit edge, sparse weave, darker hem.
    The weave is short 2 px horizontal runs one shade darker or lighter (never lone pixels, which
    read as dirt on pale cloth). noise = density of runs (None: CLOTH_NOISE; 0: none).
    On a top face the lit edge is its front row (where it meets the front face)."""
    r = [rgba(c) for c in ramp]
    n = len(r)
    b = (n // 2) if base_index is None else base_index
    noise = CLOTH_NOISE if noise is None else noise
    rnd = random.Random(seed * 7919 + sum(map(ord, face.part + face.side)))
    ys = list(range(face.h)) if rows is None else list(rows)
    if not ys:
        return
    lit = set(ys[-top_light:]) if (face.side == "top" and top_light) else set(ys[:top_light])
    grid = {}
    for y in ys:
        for x in range(face.w):
            grid[(x, y)] = r[min(n - 1, b + 1)] if y in lit else r[b]
    for y in ys:
        if y in lit:
            continue
        x = 0
        while x < face.w - 1:
            if rnd.random() < noise:
                c = r[max(0, b - 1)] if rnd.random() < 0.6 else r[min(n - 1, b + 1)]
                grid[(x, y)] = grid[(x + 1, y)] = c
                x += 3  # keep runs apart
            else:
                x += 1
    for (x, y), c in grid.items():
        face.put(x, y, c)
    if hem:
        for x in range(face.w):
            face.put(x, ys[-1], r[max(0, b - 2)])


def _folds(face, ramp, rows, xs, seed=0, base_index=None):
    """Vertical 1 px folds: a darker column with a lighter ridge beside it, varying lengths."""
    r = [rgba(c) for c in ramp]
    n = len(r)
    b = (n // 2) if base_index is None else base_index
    rnd = random.Random(seed + 31 * len(xs))
    rows = list(rows)
    for x in xs:
        start = rows[0] + rnd.randint(1, max(1, len(rows) // 3))
        end = rows[-1] - rnd.randint(0, max(0, len(rows) // 5))
        for y in range(start, end):
            face.put(x, y, r[max(0, b - 1)])            # the fold's shadow: one continuous column
        for y in range(start + 2, end - 1):
            face.put(x + 1, y, r[min(n - 1, b + 1)])    # lit ridge beside it, a little shorter


# =========================================================================== garments

def robe(t, ramp, length=20, folds=True, seed=1, sleeves_too=True, body_too=True, noise=None):
    """Full-length robe (like the librarian/cleric): jacket all sides + top, optional body & sleeves."""
    rows = range(0, length)
    for side in SIDES:
        f = t.face("jacket", side)
        _cloth(f, ramp, rows, seed, noise=noise)
        if folds and side in ("front", "back"):
            _folds(f, ramp, range(2, length), (1, 5) if side == "front" else (2, 5), seed + (side == "back"))
    _cloth(t.face("jacket", "top"), ramp, seed=seed, hem=False, noise=noise)
    _cloth(t.face("jacket", "bottom"), ramp, seed=seed, hem=False, top_light=0, base_index=1, noise=noise)
    if body_too:
        for side in SIDES + ("top",):
            _cloth(t.face("body", side), ramp, seed=seed, hem=False, noise=noise)
    if sleeves_too:
        sleeves(t, ramp, seed=seed, noise=noise)


def vest(t, ramp, length=9, open_front=True, seed=2, noise=None):
    """Short vest/jerkin over the robe's upper part; open front shows the robe underneath."""
    rows = range(0, length)
    for side in SIDES:
        f = t.face("jacket", side)
        _cloth(f, ramp, rows, seed, noise=noise)
        if side == "front" and open_front:
            for y in rows:
                f.put(3, y, (0, 0, 0, 0))
                f.put(4, y, (0, 0, 0, 0))
    _cloth(t.face("jacket", "top"), ramp, seed=seed, hem=False, noise=noise)


def apron(t, ramp, top=2, bottom=17, left=1, right=6, pocket=None, ties=True, seed=3, bib=True):
    """Front apron on the jacket (smiths, butchers, cooks). Columns left..right, rows top..bottom
    (jacket front is 8 x 20). bib=False starts it at the waist. pocket: a Ramp for a pocket patch."""
    f = t.face("jacket", "front")
    y0 = top if bib else 9
    for y in range(y0, bottom + 1):
        for x in range(left, right + 1):
            f.put(x, y, rgba(ramp[len(ramp) // 2]))
    _folds(f, ramp, range(y0 + 2, bottom + 1), (left + 1, right - 2), seed)
    for x in range(left, right + 1):
        f.put(x, y0, rgba(ramp[min(len(ramp) - 1, len(ramp) // 2 + 1)]))  # lit top edge
        f.put(x, bottom, rgba(ramp[max(0, len(ramp) // 2 - 2)]))        # hem
    if bib:
        for y in range(0, y0):  # neck straps
            f.put(left, y, rgba(ramp[1]))
            f.put(right, y, rgba(ramp[1]))
    if ties:
        waist = 9
        for side in ("west", "east"):
            s = t.face("jacket", side)
            for x in range(s.w):
                s.put(x, waist, rgba(ramp[1]))
        b = t.face("jacket", "back")
        for x in range(b.w):
            b.put(x, waist, rgba(ramp[1]))
        b.put(3, waist + 1, rgba(ramp[1]))
        b.put(4, waist + 1, rgba(ramp[1]))
    if pocket is not None:
        p = [rgba(c) for c in pocket]
        for y in range(12, 15):
            for x in range(left + 1, left + 4):
                f.put(x, y, p[len(p) // 2])
        for x in range(left + 1, left + 4):
            f.put(x, 12, p[min(len(p) - 1, len(p) // 2 + 1)])


def belt(t, ramp, row=10, buckle=None, height=1):
    """Belt around the jacket at `row` (jacket-front coordinates; vanilla belt row = 10, where the
    level badge sits on the villager's left-front: keep x 4..7 of that row readable)."""
    r = [rgba(c) for c in ramp]
    for side in SIDES:
        f = t.face("jacket", side)
        for y in range(row, row + height):
            for x in range(f.w):
                f.put(x, y, r[len(r) // 2 - (1 if side in ("west", "east") else 0)])
    if buckle is not None:
        f = t.face("jacket", "front")
        f.put(2, row, buckle)
        f.put(3, row, buckle)


def sleeves(t, ramp, cuff=None, seed=4, gloves=None, noise=None):
    """Sleeves on the crossed arms. Vanilla villagers have no bare hands: the biome layer sleeves
    every arm face, so a profession that paints sleeves must paint all of them, including the
    middle box front (the crossed forearms, the most visible arm area from the front).
    gloves= a colour/Ramp paints that middle front as two gloved hands meeting instead."""
    for side in SIDES + ("top", "bottom"):
        f = t.face("arm", side)
        # side faces: lit top row, darker cuff row at the hand end
        _cloth(f, ramp, seed=seed, hem=side in SIDES, top_light=1 if side != "bottom" else 0, noise=noise)
        if side in ("west", "east", "back"):  # the front faces point up at the viewer: keep them calm
            _folds(f, ramp, range(2, f.h), (1,), seed + len(side))
        if cuff is not None and side in SIDES:
            for x in range(f.w):
                f.put(x, f.h - 1, rgba(cuff))
    for side in ("top", "bottom", "back", "west", "east", "front"):
        _cloth(t.face("arms_middle", side), ramp, seed=seed, hem=False,
               top_light=1 if side in ("front", "top", "bottom") else 0, noise=noise)
    r = [rgba(c) for c in ramp]
    mid = t.face("arms_middle", "front")
    for y in range(mid.h):                     # where the two forearms cross: a 1 px shadow seam
        mid.put(4, y, r[max(0, len(r) // 2 - 1)])
    if gloves is not None:
        # two 4 px gloved hands meeting in the middle, not one flat block
        g = t.face("arms_middle", "front")
        gr = [rgba(c) for c in (gloves if isinstance(gloves, (list, Ramp)) else [gloves])]
        m = len(gr) // 2
        for y in range(g.h):
            for x in range(g.w):
                c = gr[min(len(gr) - 1, m + 1)] if y == 0 else gr[m]
                if y == g.h - 1 or x in (3,):
                    c = gr[max(0, m - 1)]  # knuckle row + the seam between the hands
                g.put(x, y, c)


def trousers(t, ramp, boots=None, boot_height=3, seed=5, noise=None):
    """Legs (mostly hidden by the robe; visible below it and from behind)."""
    for side in SIDES + ("top",):
        f = t.face("leg", side)
        _cloth(f, ramp, seed=seed, hem=False, noise=noise)
        if boots is not None and side in SIDES:
            br = [rgba(c) for c in boots]
            for y in range(f.h - boot_height, f.h):
                for x in range(f.w):
                    f.put(x, y, br[len(br) // 2 - (1 if y == f.h - 1 else 0)])
    if boots is not None:
        t.face("leg", "bottom").fill(rgba(boots[1]))


def collar(t, ramp, rows=2, noise=None):
    """Collar/scarf: top rows of the jacket all round + jacket top face."""
    r = [rgba(c) for c in ramp]
    for side in SIDES:
        f = t.face("jacket", side)
        for y in range(rows):
            for x in range(f.w):
                f.put(x, y, r[len(r) // 2 + (1 if y == 0 else 0)])
    _cloth(t.face("jacket", "top"), ramp, hem=False, noise=noise)


def hat(t, ramp, style="brim", crown=4, brim_ramp=None, band=None, seed=6, visor=3, noise=None):
    """Headwear on the hat layer (renders slightly outside the head).
    style: 'brim'  crown + wide brim ring on the rim plane (farmer/fisherman style)
           'cap'   crown + a front visor on the rim plane (visor = how far it sticks out, 1-4 px)
           'band'  a headband row at the forehead (crown = its row)
           'hood'  hood covering the head, face opening on the front
           'beanie' soft cap: crown rows + top, no brim
    Use hat='full' when saving if the biome's own headwear must not show through."""
    r = [rgba(c) for c in ramp]
    n = len(r)
    if style in ("brim", "cap") and crown != 4:
        crown = 4  # the rim plane sits exactly 4 px below the head top; any other crown leaves a gap
    if style in ("brim", "cap", "beanie"):
        for side in SIDES:
            f = t.face("hat", side)
            _cloth(f, r, range(0, crown), seed, hem=False, noise=noise)
            for x in range(f.w):  # bottom crown row: a band if given, else a slightly darker shadow row
                f.put(x, crown - 1, rgba(band) if band is not None else r[max(0, n // 2 - 1)])
        _cloth(t.face("hat", "top"), r, seed=seed, hem=False, top_light=0, base_index=min(n - 1, n // 2 + 1),
               noise=max(0.1, CLOTH_NOISE if noise is None else noise))
    if style == "brim":
        _brim(t, brim_ramp or r, full=True)
    elif style == "cap":
        _brim(t, brim_ramp or r, full=False, visor=visor)
    elif style == "band":
        for side in SIDES:
            f = t.face("hat", side)
            for x in range(f.w):
                f.put(x, crown, r[n // 2])
                f.put(x, crown + 1, r[max(0, n // 2 - 1)])
    elif style == "hood":
        for side in SIDES:
            f = t.face("hat", side)
            _cloth(f, r, range(0, f.h), seed, hem=False, noise=noise)
            if side == "front":
                for y in range(2, f.h):
                    for x in range(1, f.w - 1):
                        f.put(x, y, (0, 0, 0, 0))
                for x in range(1, f.w - 1):
                    f.put(x, 1, r[max(0, n // 2 - 1)])  # shadowed inner edge
        _cloth(t.face("hat", "top"), r, seed=seed, hem=False, top_light=0, noise=max(0.1, CLOTH_NOISE if noise is None else noise))


def _brim(t, ramp, full=True, visor=3):
    """Brim on the 16x16 rim plane, painted on ONE face only: the front face (the brim's top).
    The rim box is 1 px thick and villager layers render without back-face culling, so one painted
    face already shows from above and below. Painting the back face (underside) too makes two
    planes 1 px apart, which reads as a double brim from the side. Vanilla farmer and fisherman
    paint only the front face (the shepherd only the back one), never both.
    The plane lies flat at head row 4. Plane rows 4-11 / cols 4-11 are inside the head (never
    visible), rows 12-15 stick out over the face, rows 0-3 behind the head, cols 0-3 / 12-15 the sides."""
    r = [rgba(c) for c in ramp]
    n = len(r)
    top = t.face("hat_rim", "front")
    t.face("hat_rim", "back").clear()
    for y in range(16):
        for x in range(16):
            inside_head = 4 <= x < 12 and 4 <= y < 12
            cx, cy = x - 7.5, y - 7.5
            if full:
                if inside_head or abs(cx) + abs(cy) > 13:  # round the corners
                    continue
                edge = x in (0, 15) or y in (0, 15) or abs(cx) + abs(cy) > 12
            else:
                # visor: in front of the face only, head width + 1 px each side, rounded front corners
                if not (12 <= y < 12 + visor and 3 <= x <= 12):
                    continue
                if y == 11 + visor and x in (3, 12):
                    continue
                edge = y == 11 + visor or x in (3, 12)
            c = r[max(0, n // 2 - 1)] if edge else r[min(n - 1, n // 2 + (1 if (x + 2 * y) % 7 == 0 else 0))]
            top.put(x, y, c)


VANILLA_EYES = {1: "#ffffff", 2: "#009611", 5: "#009611", 6: "#ffffff"}  # head front row 6: x -> colour


def glasses(t, frame="#3a2a1a", lens=None, row=6, strap=None, tint=0.45):
    """Glasses or goggles on the hat layer over the eyes. Vanilla face (head front, 8 x 10): unibrow
    row 5, eyes row 6 (white at x 1 and 6, green at x 2 and 5), nose box between them.
    Frame pixels go round each eye: rows row-1 and row+1 over x 1-2 and 5-6, plus x 0, 3, 4, 7 on
    the eye row. lens=None leaves the eyes visible through empty frames; lens=<colour> paints the
    eye pixels as the vanilla eyes tinted toward that colour (they still read as eyes).
    strap=<colour> runs a band round the hat sides and back at the eye row: goggles."""
    f = t.face("hat", "front")
    for x in (0, 3, 4, 7):
        f.put(x, row, frame)
    for x in (1, 2, 5, 6):
        f.put(x, row - 1, frame)
        f.put(x, row + 1, frame)
        if lens is not None:
            f.put(x, row, mix(rgba(VANILLA_EYES[x]), rgba(lens), tint))
    if strap is not None:
        for side in ("west", "east", "back"):
            g = t.face("hat", side)
            for x in range(g.w):
                g.put(x, row, strap)


def emblem(t, sprite, part="jacket", side="front", x=2, y=3):
    """Paste a tiny icon (e.g. a 3x3 gear) onto a face."""
    t.face(part, side).paste(sprite, x, y)


def check_layout(tex):
    """Pixels painted outside every UV face (they never render): list of (x, y)."""
    used = np.zeros((64, 64), bool)
    for part, faces in UV.items():
        if part in ("head", "nose"):  # profession layers may paint the face (glasses etc.) but usually shouldn't
            pass
        for u0, v0, u1, v1 in faces.values():
            used[v0:v1, u0:u1] = True
    a = tex.a if hasattr(tex, "a") else np.asarray(tex)
    stray = (a[..., 3] > 0) & ~used
    return [(int(x), int(y)) for y, x in zip(*np.nonzero(stray))]
