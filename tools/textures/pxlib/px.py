"""px: a small pixel-art library for vanilla-style Minecraft textures (numpy + Pillow only).

Import from a recipe:
    import sys; sys.path.insert(0, "<skill>/scripts")
    from px import *

Core ideas
- Draw the SHAPE as a boolean mask (primitives, ASCII art), then let `shade()` light it from
  the top-left with a material Ramp, then `outline()` it. That gives consistent vanilla-style
  shading instead of hand-placed colours.
- For small precise details, paint pixels directly (`put`, `line`, ASCII with a legend).
- Blocks: start from a tileable base (`stone_tile`, `planks_tile`, `bricks_tile`, ...) and
  check seams with preview.py.
"""
import colorsys
import json
import math
import random
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent

# Light comes from the top-left, slightly more from the top than the side, so 45-degree edges
# (tool heads, blades) still get distinct lit/shadow sides instead of ties.
LIGHT = (-0.7, -1.0)

# =========================================================================== colours

def rgba(c):
    """'#rrggbb' / '#rrggbbaa' / (r,g,b) / (r,g,b,a) -> (r,g,b,a) ints."""
    if c is None:
        return (0, 0, 0, 0)
    if isinstance(c, str):
        h = c.lstrip("#")
        if len(h) == 3:
            h = "".join(ch * 2 for ch in h)
        vals = [int(h[i:i + 2], 16) for i in range(0, len(h), 2)]
        return tuple(vals[:3]) + ((vals[3],) if len(vals) > 3 else (255,))
    c = tuple(int(v) for v in c)
    return c if len(c) == 4 else c + (255,)


def hexc(c):
    c = rgba(c)
    return "#%02x%02x%02x" % c[:3]


def hsv(c):
    r, g, b, _ = rgba(c)
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return h * 360, s, v


def from_hsv(h, s, v):
    r, g, b = colorsys.hsv_to_rgb((h % 360) / 360, max(0, min(1, s)), max(0, min(1, v)))
    return (round(r * 255), round(g * 255), round(b * 255), 255)


def luminance(c):
    r, g, b, _ = rgba(c)
    return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255


def mix(a, b, t):
    a, b = rgba(a), rgba(b)
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(4))


class Ramp(list):
    """Colours darkest -> lightest, plus outline colours (lit side, shadow side)."""

    def __init__(self, colours, outline=None, name=""):
        super().__init__(rgba(c) for c in colours)
        self.name = name
        if outline is None:
            d = self[0]
            h, s, v = hsv(d)
            outline = (from_hsv(h + 4, min(1, s * 1.1 + 0.05), v * 0.72), from_hsv(h + 8, min(1, s * 1.15 + 0.08), v * 0.45))
        self.outline_light, self.outline_dark = rgba(outline[0]), rgba(outline[1])

    def with_outline(self, lit, dark):
        """Copy with other outline colours (e.g. iron TOOLS use darker #444444 / #181818 than the ingot)."""
        return Ramp(list(self), outline=(lit, dark), name=self.name)

    def lighter(self, k=1):
        """Colours shifted k steps toward the light end (clamped)."""
        return Ramp([self[min(len(self) - 1, i + k)] for i in range(len(self))], (self.outline_light, self.outline_dark), self.name)

    @property
    def mid(self):
        return self[len(self) // 2]

    def __repr__(self):
        return f"Ramp({self.name or ''}: {' '.join(hexc(c) for c in self)} | outline {hexc(self.outline_light)} {hexc(self.outline_dark)})"


def ramp(base, n=5, spread=0.55, hue_shift=4.0, name="", precious=False):
    """Hue-shifted ramp around a mid colour. Vanilla shifts subtly (median ~3 deg darkest->lightest);
    precious/glowing materials shift ~25-35 deg (gold, emerald). Highlights desaturate, shadows cool."""
    h, s, v = hsv(base)
    shift = 30.0 if precious else hue_shift
    warm = 1 if (h < 60 or h > 300) else -1  # shadows move toward blue/purple, lights toward yellow
    out = []
    for i in range(n):
        t = (i / (n - 1)) * 2 - 1 if n > 1 else 0  # -1 darkest .. +1 lightest
        vv = v * (1 + spread * t) if t < 0 else v + (1 - v) * spread * t * 1.05
        ss = s * (1 + 0.15 * -t) if t < 0 else s * (1 - 0.55 * t * t)
        hh = h + warm * (shift / 2) * t if (h < 60 or h > 300) else h - (shift / 2) * t * (1 if h > 180 else -1)
        out.append(from_hsv(hh, ss, vv))
    return Ramp(out, name=name)


_PALETTES = None


def palettes():
    """Named material ramps measured from vanilla textures (colours only). List them with scripts/palette.py list."""
    global _PALETTES
    if _PALETTES is None:
        data = json.loads((HERE / "palettes.json").read_text())
        _PALETTES = {k: Ramp(v["ramp"], outline=v.get("outline"), name=k) for k, v in data.items()}
    return _PALETTES


def P(name):
    """P('iron') -> Ramp. Raises with suggestions if unknown."""
    p = palettes()
    if name in p:
        return p[name]
    near = [k for k in p if name.split("_")[0] in k][:12]
    raise KeyError(f"No palette '{name}'. Similar: {near}. All: python3 scripts/palette.py list")


# =========================================================================== masks

def blank(w=16, h=16):
    return np.zeros((h, w), bool)


def rect(x0, y0, x1, y1, w=16, h=16):
    """Inclusive rectangle mask."""
    m = blank(w, h)
    m[max(0, y0):min(h, y1 + 1), max(0, x0):min(w, x1 + 1)] = True
    return m


def ellipse(cx, cy, rx, ry, w=16, h=16):
    yy, xx = np.mgrid[0:h, 0:w]
    return ((xx + 0.5 - cx) / rx) ** 2 + ((yy + 0.5 - cy) / ry) ** 2 <= 1.0


def polygon(points, w=16, h=16):
    """Filled polygon (pixel centres inside, even-odd rule). Points in pixel coordinates."""
    yy, xx = np.mgrid[0:h, 0:w]
    px, py = xx + 0.5, yy + 0.5
    inside = np.zeros((h, w), bool)
    n = len(points)
    for i in range(n):
        x1, y1 = points[i]
        x2, y2 = points[(i + 1) % n]
        cond = ((y1 > py) != (y2 > py)) & (px < (x2 - x1) * (py - y1) / ((y2 - y1) or 1e-9) + x1)
        inside ^= cond
    return inside


def line(p0, p1, w=16, h=16):
    """Pixel-perfect 1px Bresenham line (no doubled corners)."""
    m = blank(w, h)
    x0, y0 = p0
    x1, y1 = p1
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        if 0 <= x0 < w and 0 <= y0 < h:
            m[y0, x0] = True
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy
    return m


def diagonal(p0, p1, width=2, w=16, h=16):
    """A 45-degree staircase stroke `width` px wide per row (a vanilla tool handle is 1 px of wood per
    row between its two outline pixels: draw it with width=1 and let outline() add the rest)."""
    m = line(p0, p1, w, h)
    out = m.copy()
    for k in range(1, width):
        out |= np.roll(m, k, axis=1) & ~_wrapped_cols(k, w, h)
    return out


def stroke(p0, p1, width=3, w=16, h=16):
    """A straight band of any angle: pixels whose centre lies within width/2 of the segment p0-p1.
    For tool heads, blades and beams that aren't at 45 degrees (points are pixel coordinates;
    use .5 to centre on a pixel). Clean the ends by hand in the ASCII pass if needed."""
    (x0, y0), (x1, y1) = p0, p1
    yy, xx = np.mgrid[0:h, 0:w]
    px_, py_ = xx + 0.5, yy + 0.5
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy or 1e-9
    t = np.clip(((px_ - x0) * dx + (py_ - y0) * dy) / L2, 0, 1)
    d = np.hypot(px_ - (x0 + t * dx), py_ - (y0 + t * dy))
    return d <= width / 2


def _wrapped_cols(k, w, h):
    z = blank(w, h)
    z[:, :k] = True
    return z


def from_ascii_mask(art, on="#"):
    rows = [r for r in art.strip("\n").split("\n")]
    rows = [r.strip() for r in rows]
    h, w = len(rows), max(len(r) for r in rows)
    m = blank(w, h)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            m[y, x] = ch in on
    return m


def grow(mask, n=1, diagonal=False):
    m = mask.copy()
    for _ in range(n):
        g = m.copy()
        g[1:, :] |= m[:-1, :]
        g[:-1, :] |= m[1:, :]
        g[:, 1:] |= m[:, :-1]
        g[:, :-1] |= m[:, 1:]
        if diagonal:
            g[1:, 1:] |= m[:-1, :-1]
            g[:-1, :-1] |= m[1:, 1:]
            g[1:, :-1] |= m[:-1, 1:]
            g[:-1, 1:] |= m[1:, :-1]
        m = g
    return m


def shrink(mask, n=1):
    return ~grow(~mask, n)


def edge_distance(mask):
    """Chessboard-ish distance (in px) from each mask pixel to the nearest outside pixel (4-neighbour BFS)."""
    h, w = mask.shape
    d = np.where(mask, 10 ** 6, 0).astype(float)
    # outside the canvas counts as outside
    frontier = [(y, x) for y in range(h) for x in range(w) if mask[y, x] and (
        y == 0 or x == 0 or y == h - 1 or x == w - 1 or not mask[y - 1, x] or not mask[y + 1, x]
        or not mask[y, x - 1] or not mask[y, x + 1])]
    for y, x in frontier:
        d[y, x] = 1
    q = list(frontier)
    i = 0
    while i < len(q):
        y, x = q[i]
        i += 1
        for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
            if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and d[ny, nx] > d[y, x] + 1:
                d[ny, nx] = d[y, x] + 1
                q.append((ny, nx))
    return d


# =========================================================================== sprite

class Sprite:
    """RGBA pixel canvas. Coordinates: x right, y down, (0,0) top-left."""

    def __init__(self, w=16, h=16, fill=None):
        self.a = np.zeros((h, w, 4), np.uint8)
        if fill is not None:
            self.a[:] = rgba(fill)

    # ---- construction / io
    @classmethod
    def load(cls, path):
        s = cls(1, 1)
        s.a = np.asarray(Image.open(path).convert("RGBA")).copy()
        return s

    @classmethod
    def from_ascii(cls, art, legend=None, ramp=None):
        """Rows of characters. '.' or ' ' = transparent. Digits 0-9 index `ramp` (0 darkest);
        any other character must be in `legend` {char: colour}. Rows may be indented."""
        rows = [r.strip() for r in art.strip("\n").split("\n")]
        h, w = len(rows), max(len(r) for r in rows)
        bad = [i for i, r in enumerate(rows) if len(r) != w]
        if bad:
            raise ValueError(f"ASCII rows {bad} are not {w} characters wide: every row must be the same width")
        s = cls(w, h)
        legend = {k: rgba(v) for k, v in (legend or {}).items()}
        for y, r in enumerate(rows):
            for x, ch in enumerate(r):
                if ch in ". ":
                    continue
                if ch in legend:
                    s.a[y, x] = legend[ch]
                elif ch.isdigit() and ramp is not None:
                    s.a[y, x] = rgba(ramp[int(ch)])
                else:
                    raise ValueError(f"Character {ch!r} at ({x},{y}) is not in the legend")
        return s

    def copy(self):
        s = Sprite(1, 1)
        s.a = self.a.copy()
        return s

    @property
    def w(self):
        return self.a.shape[1]

    @property
    def h(self):
        return self.a.shape[0]

    def image(self):
        return Image.fromarray(self.a, "RGBA")

    def save(self, path):
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        self.image().save(path)
        return path

    def zoom(self, path, scale=16, bg=(139, 139, 139, 255)):
        """Quick nearest-neighbour enlargement on a grey background (for a fast look)."""
        im = Image.new("RGBA", (self.w * scale, self.h * scale), bg)
        im.alpha_composite(self.image().resize((self.w * scale, self.h * scale), Image.NEAREST))
        im.save(path)
        return path

    # ---- pixel access
    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x] = rgba(c)
        return self

    def get(self, x, y):
        return tuple(int(v) for v in self.a[y, x])

    def puts(self, pts, c):
        for x, y in pts:
            self.put(x, y, c)
        return self

    def fill(self, mask, c):
        self.a[mask] = rgba(c)
        return self

    def erase(self, mask):
        self.a[mask] = (0, 0, 0, 0)
        return self

    def mask(self):
        """Opaque pixels."""
        return self.a[..., 3] > 0

    def paste(self, other, x=0, y=0):
        """Alpha-over another sprite at (x, y)."""
        src = other.a
        h, w = src.shape[:2]
        for yy in range(h):
            for xx in range(w):
                tx, ty = x + xx, y + yy
                if 0 <= tx < self.w and 0 <= ty < self.h and src[yy, xx, 3] > 0:
                    self.a[ty, tx] = src[yy, xx]
        return self

    def region(self, x, y, w, h):
        s = Sprite(w, h)
        s.a = self.a[y:y + h, x:x + w].copy()
        return s

    # ---- transforms
    def flip_x(self):
        self.a = self.a[:, ::-1].copy()
        return self

    def flip_y(self):
        self.a = self.a[::-1].copy()
        return self

    def rot90(self, k=1):
        self.a = np.rot90(self.a, k).copy()
        return self

    def shift(self, dx, dy, wrap=True):
        """Move content; wrap=True keeps a tile seamless (use shift(8, 8) to inspect seams)."""
        if wrap:
            self.a = np.roll(np.roll(self.a, dy, 0), dx, 1)
        else:
            out = np.zeros_like(self.a)
            h, w = self.h, self.w
            ys, yd = (slice(0, h - dy), slice(dy, h)) if dy >= 0 else (slice(-dy, h), slice(0, h + dy))
            xs, xd = (slice(0, w - dx), slice(dx, w)) if dx >= 0 else (slice(-dx, w), slice(0, w + dx))
            out[yd, xd] = self.a[ys, xs]
            self.a = out
        return self

    def replace(self, old, new):
        m = (self.a == np.array(rgba(old), np.uint8)).all(-1)
        self.a[m] = rgba(new)
        return self

    def remap(self, src_ramp, dst_ramp):
        """Swap colours of one of YOUR sprites from one ramp to another (material variants)."""
        for a, b in zip(src_ramp, dst_ramp):
            self.replace(a, b)
        for a, b in ((src_ramp.outline_light, dst_ramp.outline_light), (src_ramp.outline_dark, dst_ramp.outline_dark)):
            self.replace(a, b)
        return self

    def colours(self):
        """Distinct opaque colours as RGBA tuples, darkest first (a list, not a Counter)."""
        m = self.mask()
        return sorted({tuple(int(v) for v in c) for c in self.a[m]}, key=luminance)

    # ---- drawing with light
    def shade(self, mask, ramp, form="bevel", light=LIGHT, gain=1.0, depth=2, base=None, keep=None,
              lo=None, hi=None):
        """Fill `mask` with `ramp`, lit from `light` (default top-left).
        form: 'flat'     one colour (ramp[base])
              'plate'    crisp 1 px faceted bevel (lit edges +1/+2, shadow edges -1/-2): metal heads, ingots, boxes, planks.
                         depth=1 caps it at +1/-1: use for small parts (tool heads, buttons) that go too dark
              'bevel'    smoother bevel `depth` px deep (bigger shapes)
              'dome'     rounded: gems, coins, fruit, pouches, heads
              'cyl-v' / 'cyl-h'  cylinder along the vertical / horizontal axis: handles, logs, bottles, scrolls
        base: ramp index of an unlit flat face (default: middle). gain: lighting contrast (1 = one or
        two steps either way). lo/hi: clamp to ramp[lo..hi] (default: whole ramp).
        keep: mask of pixels not to overwrite."""
        n = len(ramp)
        base = n // 2 if base is None else base
        lo = 0 if lo is None else lo
        hi = n - 1 if hi is None else hi
        m = mask & (~keep if keep is not None else True)
        if form == "flat":
            self.a[m] = rgba(ramp[base])
            return self
        cols = np.array([rgba(c) for c in ramp], np.uint8)
        lx, ly = light
        if form == "plate":
            # crisp 1 px faceted bevel: each edge pixel is lit by its outward direction
            idx = np.full(mask.shape, base, int)
            h_, w_ = mask.shape
            for y, x in zip(*np.nonzero(mask)):
                ox = oy = 0
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if not (0 <= xx < w_ and 0 <= yy < h_ and mask[yy, xx]):
                        ox += dx
                        oy += dy
                if ox or oy:
                    d = (ox * lx + oy * ly) / math.hypot(ox, oy) / math.hypot(lx, ly)
                    k = 2 if d > 0.6 else 1 if d > 0.05 else -2 if d < -0.6 else -1 if d < -0.05 else 0
                    idx[y, x] = base + max(-depth, min(depth, k))
            idx = np.clip(idx, lo, hi)
            self.a[m] = cols[idx[m]]
            return self
        if form == "dome":
            # pixel-art sphere: bands by distance from a highlight point up-left of centre
            ys, xs = np.nonzero(mask)
            cx, cy = xs.mean() + 0.5, ys.mean() + 0.5
            r = max(1.0, math.sqrt(mask.sum() / math.pi))
            hx, hy = cx + lx * r * 0.38, cy + ly * r * 0.38
            yy, xx = np.mgrid[0:mask.shape[0], 0:mask.shape[1]]
            dist = np.hypot(xx + 0.5 - hx, yy + 0.5 - hy) / (r * 1.45)
            steps = (0.55 - dist) / 0.22 * gain
            idx = np.clip(np.round(base + steps), lo, hi).astype(int)
            self.a[m] = cols[idx[m]]
            return self
        h = self._height(mask, form, depth)
        gy, gx = np.gradient(h)
        L = np.array([lx, ly, 1.3], float)
        L /= np.linalg.norm(L)
        nx, ny, nz = -gx * 1.6, -gy * 1.6, np.ones_like(h)
        norm = np.sqrt(nx ** 2 + ny ** 2 + nz ** 2)
        I = (nx * L[0] + ny * L[1] + nz * L[2]) / norm
        I0 = L[2]
        steps = (I - I0) / 0.25 * gain  # ~one ramp step per 0.25 of light change
        idx = np.clip(np.round(base + steps), lo, hi).astype(int)
        self.a[m] = cols[idx[m]]
        return self

    def to_ascii(self, ramps=None, legend=None):
        """Print the sprite as ASCII for hand refinement (then paste back into Sprite.from_ascii).
        Colours in ramps[0] become digits 0-9; everything else gets letters. Returns (art, legend)."""
        legend = dict(legend or {})
        rev = {rgba(v): k for k, v in legend.items()}
        if ramps:
            for i, c in enumerate(ramps[0]):
                rev.setdefault(rgba(c), str(i))
        letters = iter("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@$%&*+=?")
        rows = []
        for y in range(self.h):
            row = ""
            for x in range(self.w):
                c = tuple(int(v) for v in self.a[y, x])
                if c[3] == 0:
                    row += "."
                    continue
                if c not in rev:
                    k = next(letters)
                    while k in legend:
                        k = next(letters)
                    rev[c] = k
                    legend[k] = hexc(c)
                row += rev[c]
            rows.append(row)
        for c, k in rev.items():
            if not k.isdigit():
                legend.setdefault(k, hexc(c))
        return "\n".join(rows), legend

    @staticmethod
    def _height(mask, form, depth):
        hgt = np.zeros(mask.shape, float)
        if form == "bevel":
            d = edge_distance(mask)
            hgt = np.minimum(d, depth) / depth
        elif form == "dome":
            d = edge_distance(mask)
            dm = max(1.0, d.max())
            t = np.clip(d / dm, 0, 1)
            hgt = np.sqrt(np.clip(1 - (1 - t) ** 2, 0, 1)) * dm * 0.9
        elif form in ("cyl-v", "cyl-h"):
            axis = 1 if form == "cyl-v" else 0
            for i in range(mask.shape[1 - axis]):
                line_ = mask[:, i] if axis == 0 else mask[i, :]
                idx = np.nonzero(line_)[0]
                if len(idx) == 0:
                    continue
                # contiguous runs
                runs = np.split(idx, np.where(np.diff(idx) != 1)[0] + 1)
                for r in runs:
                    c = (r[0] + r[-1]) / 2
                    half = max(0.5, (r[-1] - r[0]) / 2 + 0.5)
                    prof = np.sqrt(np.clip(1 - ((r - c) / (half + 0.5)) ** 2, 0, 1)) * half
                    if axis == 0:
                        hgt[r, i] = prof
                    else:
                        hgt[i, r] = prof
        hgt[~mask] = 0
        return hgt

    def outline(self, ramps=None, light=LIGHT, mask=None, inner=False):
        """1 px outline around opaque pixels (or `mask`), coloured per neighbouring material:
        the darkest shade of the ramp the neighbour belongs to (Ramp.outline_light on the lit
        top-left side, Ramp.outline_dark on the shadow side). Pass the ramps you used."""
        ramps = ramps or []
        src = self.mask() if mask is None else mask
        ring = grow(src, 1) & ~src & ~(self.mask() if not inner else False)
        lookup = {}
        for r in ramps:
            for c in r:
                lookup[rgba(c)] = r
        cols = {}
        for y, x in zip(*np.nonzero(ring)):
            # neighbour inside the shape, prefer the one towards the light's opposite
            nb = None
            for dx, dy in ((0, 1), (1, 0), (0, -1), (-1, 0), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < self.w and 0 <= yy < self.h and src[yy, xx]:
                    nb = tuple(int(v) for v in self.a[yy, xx])
                    if nb[3]:
                        break
            if nb is None:
                continue
            r = lookup.get(nb)
            # lit side = the outline pixel is up/left of the shape (its shape neighbour is right/below)
            neighbour_dir = self._nb_dir(src, x, y)
            lit_side = (neighbour_dir[0] * light[0] + neighbour_dir[1] * light[1]) < 0
            if r is not None:
                c = r.outline_light if lit_side else r.outline_dark
            else:
                hh, ss, vv = hsv(nb)
                c = from_hsv(hh + 6, min(1, ss * 1.1 + 0.05), vv * (0.55 if lit_side else 0.35))
            cols[(x, y)] = c
        for (x, y), c in cols.items():
            self.a[y, x] = rgba(c)
        return self

    @staticmethod
    def _nb_dir(src, x, y):
        h, w = src.shape
        sx = sy = 0
        for dx, dy in ((0, 1), (1, 0), (0, -1), (-1, 0)):
            xx, yy = x + dx, y + dy
            if 0 <= xx < w and 0 <= yy < h and src[yy, xx]:
                sx += dx
                sy += dy
        return sx, sy

    def highlight(self, pts, c="#ffffff"):
        """Specular dots (pure white only on shiny materials: metals, gems)."""
        return self.puts(pts, c)

    def cleanup(self, max_size=1, protect=None):
        """Replace isolated single pixels whose colour matches none of their 8 neighbours with the
        most common neighbour colour (removes accidental noise; don't use on intentional speckle)."""
        a = self.a
        h, w = self.h, self.w
        changed = 0
        for y in range(h):
            for x in range(w):
                if a[y, x, 3] == 0 or (protect is not None and protect[y, x]):
                    continue
                c = tuple(a[y, x])
                nbs = [tuple(a[yy, xx]) for yy in range(max(0, y - 1), min(h, y + 2))
                       for xx in range(max(0, x - 1), min(w, x + 2)) if (yy, xx) != (y, x) and a[yy, xx, 3]]
                if len(nbs) >= 6 and c not in nbs:
                    best = max(set(nbs), key=nbs.count)
                    a[y, x] = best
                    changed += 1
        return changed


# =========================================================================== blocks (tileable)

def _rng(seed):
    return random.Random(seed)


def clusters(ramp_or_colours, weights=None, seed=0, w=16, h=16, size=(1, 3), base=None, density=0.45):
    """Tileable cluster noise: a base colour with small same-colour clusters of other shades
    (vanilla natural blocks: ~2 px clusters, 4-9 colours, low contrast). Wraps at the edges."""
    cols = [rgba(c) for c in ramp_or_colours]
    rnd = _rng(seed)
    base = cols[len(cols) // 2] if base is None else rgba(base)
    s = Sprite(w, h, base)
    weights = weights or [1] * len(cols)
    n = int(w * h * density / ((size[0] + size[1]) / 2 + 0.5))
    for _ in range(n):
        c = rnd.choices(cols, weights)[0]
        x, y = rnd.randrange(w), rnd.randrange(h)
        for _ in range(rnd.randint(*size)):
            s.a[y % h, x % w] = c
            if rnd.random() < 0.5:
                x += rnd.choice((-1, 1))
            else:
                y += rnd.choice((-1, 1))
    return s


def stone_tile(ramp, seed=1):
    """Low-contrast natural stone (use a ramp of 4-6 close greys/browns)."""
    r = list(ramp)
    mid = len(r) // 2
    return clusters(r, weights=[1 if i != mid else 3 for i in range(len(r))], seed=seed, base=r[mid], density=0.55)


def planks_tile(ramp, boards=4, seed=1, grain=0.18):
    """Planks: `boards` boards; each = lighter top row, body with streaked grain, darkest seam row;
    staggered end-joints. ramp needs >= 5 colours (0 = seam)."""
    r = [rgba(c) for c in ramp]
    rnd = _rng(seed)
    s = Sprite(16, 16, r[3])
    bh = 16 // boards
    for b in range(boards):
        y0 = b * bh
        for y in range(y0, y0 + bh):
            for x in range(16):
                row = y - y0
                if row == bh - 1:
                    c = r[0]
                elif row == 0:
                    c = r[4] if len(r) > 4 else r[-1]
                else:
                    c = r[3] if rnd.random() > grain else r[2]
                    if rnd.random() < grain / 3:
                        c = r[-1]
                s.a[y, x] = c
        # grain streaks: short horizontal runs
        for _ in range(2):
            y = y0 + rnd.randint(1, max(1, bh - 2))
            x = rnd.randrange(16)
            for k in range(rnd.randint(3, 6)):
                s.a[y, (x + k) % 16] = r[2]
        # end joint
        jx = (b * 7 + rnd.randint(0, 3)) % 16
        for y in range(y0, y0 + bh - 1):
            s.a[y, jx] = r[1]
    return s


def bricks_tile(brick_ramp, mortar, courses=4, seed=1):
    """Bricks: `courses` rows (vanilla bricks: 4 courses of 3 px + 1 px mortar), staggered joints,
    each brick lit on top, darkest on its bottom row."""
    br = [rgba(c) for c in brick_ramp]
    rnd = _rng(seed)
    s = Sprite(16, 16, rgba(mortar))
    ch = 16 // courses
    for c in range(courses):
        y0 = c * ch
        offset = 0 if c % 2 == 0 else 4
        for x0 in range(-8, 16, 8):
            bx = x0 + offset
            for y in range(y0, y0 + ch - 1):
                for x in range(bx, bx + 7):
                    row = y - y0
                    col = br[-1] if row == 0 else (br[1] if row == ch - 2 else br[2 + (rnd.random() < 0.25)])
                    if x == bx and row > 0:
                        col = br[min(len(br) - 1, 3)]
                    s.a[y % 16, x % 16] = col
    return s


def stone_bricks_tile(ramp, courses=2, seed=1):
    """Stone bricks the vanilla way: `courses` courses (vanilla: 2 of 8 px), each one brick the full
    tile width with a 1 px vertical joint, joints offset half a tile between courses. Per course:
    lit top row, lit column after the joint, clustered face, dark bottom row, mortar row.
    ramp: >= 5 colours darkest first (0-1 = mortar, 1 = shadow row, 2..-3 = face, -2..-1 = lit),
    e.g. P('stone_bricks') or a recoloured ramp of the same length. The edge mortar lines are part
    of the pattern (lint knows)."""
    r = [rgba(c) for c in ramp]
    if len(r) < 5:
        raise ValueError("stone_bricks_tile needs a ramp of at least 5 colours (darkest first)")
    rnd = _rng(seed)
    face = clusters(r[2:-1], weights=[2] * (len(r) - 4) + [1], seed=seed, base=r[len(r) // 2], size=(1, 3),
                    density=0.9)
    s = Sprite(16, 16)
    ch = 16 // courses
    for c in range(courses):
        y0 = c * ch
        joint = (15 + (c % 2) * 8) % 16
        for y in range(y0, y0 + ch):
            row = y - y0
            for x in range(16):
                if row == ch - 1:                                   # mortar
                    col = r[1] if rnd.random() < 0.3 else r[0]
                elif x == joint:                                    # vertical joint
                    col = r[0]
                elif row == ch - 2:                                 # shadow row under the brick
                    col = r[1] if rnd.random() < 0.85 else r[2]
                elif row == 0 or x == (joint + 1) % 16:             # lit top row and left edge
                    col = r[-1] if rnd.random() < 0.75 else r[-2]
                else:
                    col = tuple(int(v) for v in face.a[y, x])
                s.a[y, x] = col
    return s


def frame(sprite, colour, inset=0):
    """1 px darker frame (only for BUILT blocks: machines, storage blocks, log tops)."""
    c = rgba(colour)
    w, h = sprite.w, sprite.h
    for i in range(w):
        sprite.a[inset, i] = c
        sprite.a[h - 1 - inset, i] = c
    for j in range(h):
        sprite.a[j, inset] = c
        sprite.a[j, w - 1 - inset] = c
    return sprite


def ore(base, ore_ramp, clusters_n=4, seed=3, size=(3, 5)):
    """Ore: keep the base stone and add a few ore clusters with a darker rim (vanilla: 25-36% coverage)."""
    s = base.copy()
    r = [rgba(c) for c in ore_ramp]
    rnd = _rng(seed)
    for _ in range(clusters_n):
        cx, cy = rnd.randrange(2, 14), rnd.randrange(2, 14)
        pts = {(cx, cy)}
        for _ in range(rnd.randint(*size) - 1):
            x, y = rnd.choice(sorted(pts))
            pts.add((x + rnd.choice((-1, 0, 1)), y + rnd.choice((-1, 0, 1))))
        m = blank()
        for x, y in pts:
            m[y % 16, x % 16] = True
        rim = grow(m) & ~m
        s.fill(rim, r[0])
        s.shade(m, r[1:], form="dome")
    return s


def seam_check(a):
    """(ratio, patterned) for an RGBA array. ratio = colour jump across the wrap-around edges divided
    by the average jump inside the tile (both axes; > ~1.6 means the tile edges show as lines when
    repeated). patterned = True when each axis with a strong edge line also has an equally strong
    line inside on the same 4 px grid (brick and tile mortar): then the edge line is the pattern."""
    a = np.asarray(a)[..., :3].astype(float)
    h, w = a.shape[:2]
    axes = []
    for axis, n in ((1, w), (0, h)):
        j = np.abs(a - np.roll(a, -1, axis)).mean(axis=tuple(k for k in (0, 1, 2) if k != axis))
        wrap, inner = j[-1], j[:-1]
        grid = [inner[i] for i in range(3, n - 1, 4)]
        axes.append((wrap, inner.mean(), max(grid) if grid else 0.0))
    ratio = sum(x[0] for x in axes) / max(sum(x[1] for x in axes), 1e-6)
    patterned = all(wrap <= 1.6 * mean or partner >= 0.75 * wrap for wrap, mean, partner in axes)
    return round(float(ratio), 2), bool(patterned)


def seam_score(sprite):
    """How visible the tile edges are when repeated (0 = invisible, > ~1.6 shows). Brick-like tiles
    whose edge is a mortar line score high by design: see seam_check(...)[1]."""
    return seam_check(sprite.a)[0]