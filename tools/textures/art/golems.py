"""The Golem Smith's golems (29.15): what each wears over the iron golem's own skin, drawn on the iron golem's 128x128
UV layout (the mod draws it as a layer over vanilla's texture, so only the gear is painted; the rest stays clear).

- hauler:   a wooden crate pack with iron bands on its back, leather straps over the shoulders and across the chest;
- farmhand: a straw hat with a red band, a red kerchief at the neck;
- sentry:   a gunmetal helm with a nasal, cheek guards, a neck guard and a red crest, and iron pauldrons.

Faces follow the box UV of IronGolemModel: a box (w, h, d) at (u, v) has its top at (u+d, v), and below it, at row
v+d, four strips: the side whose left edge meets the back (d wide), the front (w), the other side (d), the back (w).
Light comes from the top-left, as on the golem."""
from artlib import *  # noqa: F401,F403
from PIL import Image

GOLEM = ASSETS / "textures" / "entity" / "iron_golem"
WOOD = Ramp(["#4f3a1f", "#6e5130", "#8f6c40", "#a8834f"], name="crate")
STRAP = Ramp(["#3e2617", "#5a3822", "#77502f", "#90663e"], name="strap")
BRASS = Ramp(["#6e4a1c", "#9a6e2c", "#c4913e", "#dcb466"], name="brass")
BAND = Ramp(["#4a4d52", "#6a6e74", "#8d9197"], name="band")
STRAW = Ramp(["#8a6a26", "#b38f3a", "#d4b25a", "#e8cf82"], name="straw")
RED = Ramp(["#6e1c18", "#9a2a22", "#bf3b2e"], name="red")
HELM = Ramp(["#3a3d44", "#53575f", "#70757d", "#959aa1", "#b9bec4"], name="helm")


class Box:
    """One box of the model on the texture: its faces as rectangles to paint in face-local coordinates."""

    def __init__(self, img, u, v, w, h, d):
        self.img, self.u, self.v, self.w, self.h, self.d = img, u, v, w, h, d

    def rect(self, face):
        u, v, w, h, d = self.u, self.v, self.w, self.h, self.d
        return {
            "top": (u + d, v, w, d),
            "bottom": (u + d + w, v, w, d),
            "side_a": (u, v + d, d, h),          # its x 0 meets the back
            "front": (u + d, v + d, w, h),
            "side_b": (u + d + w, v + d, d, h),  # its last x meets the back
            "back": (u + 2 * d + w, v + d, w, h),
        }[face]

    def size(self, face):
        return self.rect(face)[2:]

    def put(self, face, x, y, c):
        x0, y0, w, h = self.rect(face)
        if 0 <= x < w and 0 <= y < h:
            self.img.putpixel((x0 + x, y0 + y), rgba(c))

    def fill(self, face, c, xs=None, ys=None):
        w, h = self.size(face)
        for y in (ys if ys is not None else range(h)):
            for x in (xs if xs is not None else range(w)):
                self.put(face, x, y, c(x, y) if callable(c) else c)


def canvas():
    return Image.new("RGBA", (128, 128), (0, 0, 0, 0))


def head(img):
    return Box(img, 0, 0, 8, 10, 8)


def nose(img):
    return Box(img, 24, 0, 2, 4, 2)


def body(img):
    return Box(img, 0, 40, 18, 12, 11)


def arms(img):
    return Box(img, 60, 21, 4, 30, 6), Box(img, 60, 58, 4, 30, 6)


def save(img, name):
    path = GOLEM / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    return [path]


def hauler():
    """A crate pack on the back: oak boards with a dark frame, two iron bands and a brass latch; leather straps over
    the shoulders and down the chest to a strap across it with a brass buckle; the crate's edge seen on the sides."""
    img = canvas()
    b = body(img)

    def crate(x, y):
        if x in (1, 16) or y in (0, 11):
            return WOOD[0]                                  # the frame
        if x in (5, 12):
            return BAND[2] if y < 3 else BAND[1]            # iron bands
        if y in (3, 7):
            return WOOD[1]                                  # the seams between boards
        return WOOD[3] if x < 5 and y < 3 else WOOD[2]
    b.fill("back", crate, xs=range(1, 17), ys=range(0, 12))
    for x, c in ((8, BRASS[3]), (9, BRASS[2])):           # the latch
        b.put("back", x, 1, c)
        b.put("back", x, 2, BRASS[1])
    for face, xs in (("side_a", (0, 1)), ("side_b", (9, 10))):  # the crate's depth at the back edges
        b.fill(face, lambda x, y: WOOD[0] if y in (0, 11) else WOOD[2] if x in (0, 10) else WOOD[1], xs=xs, ys=range(0, 12))
    for x in (3, 4, 13, 14):                              # straps over the shoulders
        b.fill("top", STRAP[3] if x in (3, 13) else STRAP[2], xs=[x])
        b.fill("front", STRAP[2] if x in (3, 13) else STRAP[1], xs=[x], ys=range(0, 6))
    b.fill("front", lambda x, y: STRAP[2] if y == 5 else STRAP[1], xs=range(3, 15), ys=(5, 6))
    for x, y, c in ((8, 5, BRASS[3]), (9, 5, BRASS[2]), (8, 6, BRASS[1]), (9, 6, BRASS[1])):
        b.put("front", x, y, c)                           # the buckle
    return save(img, "hauler")


def farmhand():
    """A straw hat: a woven crown on top, straw down the head's sides to the brim edge, a red band; a red kerchief
    knotted at the neck."""
    img = canvas()
    h = head(img)
    h.fill("top", lambda x, y: STRAW[3] if (x + y) % 3 == 0 else STRAW[2] if x < 6 else STRAW[1])
    for face in ("side_a", "front", "side_b", "back"):
        lit = face in ("front", "side_b")

        def hat(x, y, lit=lit):
            if y == 2:
                return RED[2] if lit else RED[1]          # the band
            if y == 3:
                return STRAW[1] if lit else STRAW[0]      # the brim's edge
            return (STRAW[3] if lit else STRAW[2]) if (x + 2 * y) % 4 else STRAW[1]
        h.fill(face, hat, ys=range(0, 4))
    b = body(img)
    for y in range(0, 4):                                 # the kerchief's point under the chin
        for x in range(5 + y, 13 - y):
            b.put("front", x, y, RED[2] if y == 0 or x == 5 + y else RED[1])
    b.fill("top", RED[1], xs=range(5, 13), ys=range(9, 11))  # round the neck, at the top face's front edge
    b.put("front", 9, 0, RED[0])                          # the knot
    b.put("front", 8, 0, RED[0])
    return save(img, "farmhand")


def sentry():
    """A gunmetal helm: a crested top, a brow, a nasal over the golem's nose, cheek guards and a neck guard at the back;
    iron pauldrons on both shoulders."""
    img = canvas()
    h = head(img)
    h.fill("top", lambda x, y: RED[2] if x in (3, 4) and y < 7 else HELM[4] if y == 0 else HELM[3])
    for face in ("side_a", "side_b"):
        lit = face == "side_b"
        h.fill(face, lambda x, y, lit=lit: (HELM[3] if lit else HELM[2]) if y < 4 else HELM[1] if y == 4 else HELM[2], ys=range(0, 9))
    h.fill("back", lambda x, y: HELM[0] if y == 9 else HELM[2] if y % 4 else HELM[1])
    front = lambda x, y: HELM[4] if y == 0 else HELM[3] if y < 4 else HELM[1]  # noqa: E731
    h.fill("front", front, ys=range(0, 5))
    h.fill("front", lambda x, y: HELM[2] if x in (0, 7) else HELM[1], xs=(0, 1, 6, 7), ys=range(5, 9))
    h.put("front", 3, 4, HELM[2])
    h.put("front", 4, 4, HELM[2])
    n = nose(img)
    for face in ("front", "side_a", "side_b"):
        n.fill(face, lambda x, y: HELM[3] if y == 0 else HELM[2])
    n.fill("top", HELM[4])
    for arm in arms(img):
        arm.fill("top", lambda x, y: HELM[4] if x == 0 or y == 0 else HELM[3])
        for face in ("side_a", "front", "side_b", "back"):
            arm.fill(face, lambda x, y: HELM[0] if y == 4 else HELM[3] if y == 0 else HELM[2], ys=range(0, 5))
    return save(img, "sentry")


DRAW = [hauler, farmhand, sentry]

if __name__ == "__main__":
    run(DRAW)
