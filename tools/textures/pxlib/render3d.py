"""Tiny software renderer for Minecraft box models (entities) and block cubes.

Geometry, UV layout, mirroring, inflation and part poses follow the game's ModelPart/Cube
code (verified against decompiled 1.21.1 VillagerModel / ZombieVillagerModel), so what you
see is where the pixels land in-game. Orthographic projection, z-buffer, no back-face
culling (villager layers render with entityCutoutNoCull), alpha cut-out at < 0.1.
"""
import math

import numpy as np
from PIL import Image


# --------------------------------------------------------------------------- model definition

class Box:
    def __init__(self, u, v, x, y, z, w, h, d, inflate=0.0, mirror=False):
        self.u, self.v = u, v
        self.x, self.y, self.z, self.w, self.h, self.d = x, y, z, w, h, d
        self.inflate, self.mirror = inflate, mirror

    def uv_rects(self):
        """Texture rectangles (u0, v0, u1, v1) per face, as Cube lays them out."""
        u, v, w, h, d = self.u, self.v, self.w, self.h, self.d
        return {
            "top": (u + d, v, u + d + w, v + d),              # Direction.DOWN in code: visual top (model y is down)
            "bottom": (u + d + w, v, u + d + 2 * w, v + d),
            "west": (u, v + d, u + d, v + d + h),             # -x side (the entity's right)
            "front": (u + d, v + d, u + d + w, v + d + h),    # -z side (north): faces, fronts
            "east": (u + d + w, v + d, u + 2 * d + w, v + d + h),
            "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
        }

    def faces(self):
        """[(name, [4 xyz vertices], [4 uv])] matching ModelPart.Polygon vertex/uv pairing."""
        g = self.inflate
        x0, y0, z0 = self.x - g, self.y - g, self.z - g
        x1, y1, z1 = self.x + self.w + g, self.y + self.h + g, self.z + self.d + g
        if self.mirror:
            x0, x1 = x1, x0
        V = {
            0: (x0, y0, z0), 1: (x1, y0, z0), 2: (x1, y1, z0), 3: (x0, y1, z0),
            4: (x0, y0, z1), 5: (x1, y0, z1), 6: (x1, y1, z1), 7: (x0, y1, z1),
        }
        r = self.uv_rects()

        def poly(ids, rect, flipv=False):
            u1, v1, u2, v2 = rect
            if flipv:
                v1, v2 = v2, v1
            uvs = [(u2, v1), (u1, v1), (u1, v2), (u2, v2)]  # Polygon(u1, v1, u2, v2) remap order
            if self.mirror:
                uvs = [(u1 + u2 - a, b) for a, b in uvs]
            return [V[i] for i in ids], uvs

        out = []
        for name, ids, rect, flip in (
            ("top", (5, 4, 0, 1), r["top"], False),
            ("bottom", (2, 3, 7, 6), r["bottom"], True),
            ("west", (0, 4, 7, 3), r["west"], False),
            ("front", (1, 0, 3, 2), r["front"], False),
            ("east", (5, 1, 2, 6), r["east"], False),
            ("back", (4, 5, 6, 7), r["back"], False),
        ):
            if self.mirror and name in ("west", "east"):
                rect = r["east"] if name == "west" else r["west"]
            verts, uvs = poly(ids, rect, flip)
            out.append((name, verts, uvs))
        return out


class Part:
    def __init__(self, boxes=(), offset=(0, 0, 0), rot=(0, 0, 0), children=(), name=""):
        self.boxes, self.offset, self.rot, self.children, self.name = list(boxes), offset, rot, list(children), name


def _rot_matrix(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx  # ModelPart: rotationZYX(zRot, yRot, xRot)


def flatten(part, parent=np.eye(4), hidden=()):
    """Yield (face name, 4x3 world verts, uvs) for every box of a part tree."""
    if part.name in hidden:
        return
    T = np.eye(4)
    T[:3, 3] = part.offset
    R = np.eye(4)
    R[:3, :3] = _rot_matrix(*part.rot)
    M = parent @ T @ R
    for box in part.boxes:
        for name, verts, uvs in box.faces():
            vv = (M @ np.c_[np.array(verts, float), np.ones(4)].T).T[:, :3]
            yield name, vv, uvs
    for c in part.children:
        yield from flatten(c, M, hidden)


# --------------------------------------------------------------------------- villager models

def villager_model():
    head = Part([Box(0, 0, -4, -10, -4, 8, 10, 8)], name="head", children=[
        Part([Box(32, 0, -4, -10, -4, 8, 10, 8, inflate=0.51)], name="hat", children=[
            Part([Box(30, 47, -8, -8, -6, 16, 16, 1)], rot=(-math.pi / 2, 0, 0), name="hat_rim")]),
        Part([Box(24, 0, -1, -1, -6, 2, 4, 2)], offset=(0, -2, 0), name="nose"),
    ])
    body = Part([Box(16, 20, -4, 0, -3, 8, 12, 6)], name="body", children=[
        Part([Box(0, 38, -4, 0, -3, 8, 20, 6, inflate=0.5)], name="jacket")])
    arms = Part([Box(44, 22, -8, -2, -2, 4, 8, 4), Box(44, 22, 4, -2, -2, 4, 8, 4, mirror=True),
                 Box(40, 38, -4, 2, -2, 8, 4, 4)], offset=(0, 3, -1), rot=(-0.75, 0, 0), name="arms")
    rl = Part([Box(0, 22, -2, 0, -2, 4, 12, 4)], offset=(-2, 12, 0), name="right_leg")
    ll = Part([Box(0, 22, -2, 0, -2, 4, 12, 4, mirror=True)], offset=(2, 12, 0), name="left_leg")
    return Part(children=[head, body, arms, rl, ll], name="root")


def zombie_villager_model(arm_pitch=-1.45):
    head = Part([Box(0, 0, -4, -10, -4, 8, 10, 8), Box(24, 0, -1, -3, -6, 2, 4, 2)], name="head", children=[
        Part([Box(32, 0, -4, -10, -4, 8, 10, 8, inflate=0.5)], name="hat", children=[
            Part([Box(30, 47, -8, -8, -6, 16, 16, 1)], rot=(-math.pi / 2, 0, 0), name="hat_rim")])])
    body = Part([Box(16, 20, -4, 0, -3, 8, 12, 6), Box(0, 38, -4, 0, -3, 8, 20, 6, inflate=0.05)], name="body")
    ra = Part([Box(44, 22, -3, -2, -2, 4, 12, 4)], offset=(-5, 2, 0), rot=(arm_pitch, 0, 0), name="right_arm")
    la = Part([Box(44, 22, -1, -2, -2, 4, 12, 4, mirror=True)], offset=(5, 2, 0), rot=(arm_pitch, 0, 0), name="left_arm")
    rl = Part([Box(0, 22, -2, 0, -2, 4, 12, 4)], offset=(-2, 12, 0), name="right_leg")
    ll = Part([Box(0, 22, -2, 0, -2, 4, 12, 4, mirror=True)], offset=(2, 12, 0), name="left_leg")
    return Part(children=[head, body, ra, la, rl, ll], name="root")


def villager_uv_regions():
    """{part: {face: (u0, v0, u1, v1)}} for the villager model (the 64x64 texture layout)."""
    names = {"head": Box(0, 0, -4, -10, -4, 8, 10, 8), "hat": Box(32, 0, -4, -10, -4, 8, 10, 8),
             "hat_rim": Box(30, 47, -8, -8, -6, 16, 16, 1), "nose": Box(24, 0, -1, -1, -6, 2, 4, 2),
             "body": Box(16, 20, -4, 0, -3, 8, 12, 6), "jacket": Box(0, 38, -4, 0, -3, 8, 20, 6),
             "arm": Box(44, 22, -8, -2, -2, 4, 8, 4), "arms_middle": Box(40, 38, -4, 2, -2, 8, 4, 4),
             "leg": Box(0, 22, -2, 0, -2, 4, 12, 4)}
    return {k: b.uv_rects() for k, b in names.items()}


# --------------------------------------------------------------------------- rasteriser

def render(faces, tex, yaw=0.0, pitch=0.0, scale=10, size=None, bg=(0, 0, 0, 0), light=True):
    """faces: iterable of (name, verts(4x3), uvs(4x2)) with uvs in texel units of `tex`
    (or tex may be a dict face-key -> image for blocks: then uvs must be in that image's units).
    Camera looks along +z from the front (entity faces -z); yaw turns the model (radians),
    positive pitch tilts the top toward the camera."""
    faces = list(faces)
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rp = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    C = Rp @ Ry
    proj = [(f[0], (C @ np.asarray(f[1], float).T).T, f[2], f[3] if len(f) > 3 else None) for f in faces]
    allv = np.concatenate([p[1] for p in proj])
    mn, mx = allv.min(0), allv.max(0)
    pad = 2
    W = int(math.ceil((mx[0] - mn[0]) * scale)) + 2 * pad if size is None else size[0]
    H = int(math.ceil((mx[1] - mn[1]) * scale)) + 2 * pad if size is None else size[1]
    ox = pad - mn[0] * scale if size is None else (W - (mx[0] + mn[0]) * scale) / 2
    oy = pad - mn[1] * scale if size is None else (H - (mx[1] + mn[1]) * scale) / 2
    out = np.zeros((H, W, 4), np.float32)
    out[:] = np.array(bg, np.float32) / 255.0
    zbuf = np.full((H, W), np.inf)
    texarr = {}

    def tex_for(key):
        img = tex if key is None else tex[key]
        if id(img) not in texarr:
            texarr[id(img)] = np.asarray(img.convert("RGBA"), np.float32) / 255.0
        return texarr[id(img)]

    L = np.array([-0.35, -0.8, -0.5])
    L /= np.linalg.norm(L)
    for name, v, uv, key in proj:
        T = tex_for(key)
        p = np.c_[v[:, 0] * scale + ox, v[:, 1] * scale + oy]
        e1, e2 = p[1] - p[0], p[3] - p[0]
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-6:
            continue
        x0, x1 = int(max(0, math.floor(p[:, 0].min()))), int(min(W, math.ceil(p[:, 0].max())))
        y0, y1 = int(max(0, math.floor(p[:, 1].min()))), int(min(H, math.ceil(p[:, 1].max())))
        if x1 <= x0 or y1 <= y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        dx, dy = gx - p[0, 0], gy - p[0, 1]
        s = (dx * e2[1] - dy * e2[0]) / det
        t = (e1[0] * dy - e1[1] * dx) / det
        inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        if not inside.any():
            continue
        uv = np.asarray(uv, float)
        u = uv[0, 0] + s * (uv[1, 0] - uv[0, 0]) + t * (uv[3, 0] - uv[0, 0])
        w = uv[0, 1] + s * (uv[1, 1] - uv[0, 1]) + t * (uv[3, 1] - uv[0, 1])
        ulo, uhi = uv[:, 0].min(), uv[:, 0].max() - 1e-4
        vlo, vhi = uv[:, 1].min(), uv[:, 1].max() - 1e-4
        ui = np.clip(np.floor(np.clip(u, ulo, uhi)).astype(int), 0, T.shape[1] - 1)
        vi = np.clip(np.floor(np.clip(w, vlo, vhi)).astype(int), 0, T.shape[0] - 1)
        z = v[0, 2] + s * (v[1, 2] - v[0, 2]) + t * (v[3, 2] - v[0, 2])
        col = T[vi, ui]
        ok = inside & (col[..., 3] >= 0.1)
        zb = zbuf[y0:y1, x0:x1]
        ok &= z < zb - 1e-4
        if not ok.any():
            continue
        if light:
            n = np.cross(v[1] - v[0], v[3] - v[0])
            nn = np.linalg.norm(n)
            n = n / nn if nn else n
            shade = 0.62 + 0.38 * abs(float(n @ L))
            if name in ("top",):
                shade = max(shade, 0.98)
            col = col.copy()
            col[..., :3] *= shade
        region = out[y0:y1, x0:x1]
        region[ok] = np.c_[col[ok][:, :3], np.ones(ok.sum())]
        zb[ok] = z[ok]
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), "RGBA")


def render_model(model, tex, yaw=0.0, pitch=0.0, scale=10, hidden=(), bg=(0, 0, 0, 0)):
    return render(flatten(model, hidden=hidden), tex, yaw=yaw, pitch=pitch, scale=scale, bg=bg)


def block_faces(top, side, bottom=None, front=None):
    """Faces of a 16x16x16 cube with per-face images, for an isometric block preview."""
    bottom = bottom or top
    front = front or side
    faces = []
    b = Box(0, 0, -8, -8, -8, 16, 16, 16)
    imgs = {"top": top, "bottom": bottom, "front": front, "west": side, "east": side, "back": side}
    for name, verts, _ in b.faces():
        img = imgs[name]
        w, h = img.size
        uvs = [(w, 0), (0, 0), (0, h), (w, h)]
        if name == "bottom":
            uvs = [(w, h), (0, h), (0, 0), (w, 0)]
        faces.append((name, verts, uvs, name))
    return faces, imgs


def render_block(top, side, bottom=None, front=None, scale=6, yaw=math.radians(-45), pitch=math.radians(30)):
    faces, imgs = block_faces(top, side, bottom, front)
    return render(faces, imgs, yaw=yaw, pitch=pitch, scale=scale)
