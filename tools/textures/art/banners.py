"""Banner patterns of our own (ROADMAP 28.21), drawn the way vanilla draws its emblem patterns (the globe, the flower,
the skull): a pale grey mask on a transparent 64x64 sheet that the game tints with the pattern's dye. The banner's
front face is x 1-20, y 1-40 (the back, x 22-41, repeats it); the shield's face is x 1-12, y 1-22. Emblems sit in
the middle of the face, like vanilla's (banner x 3-18, y 12-30; shield x 3-10, y 8-16). Three greys give the tinted
emblem a lit side, a body and a shadow side, as vanilla's 225-241 greys do."""
from artlib import *  # noqa: F401,F403
from artlib import ASSETS
from PIL import Image

# lit rim and left side, body, shadow side and foot (all opaque: the dye colour shows through the grey)
TONES = {"L": (246, 246, 246, 255), "M": (226, 226, 226, 255), "D": (198, 198, 198, 255)}

# The Festival Cup: a two-handled trophy on a stepped foot, 16 x 19
CUP_BANNER = """
..LLLLLLLLLLLL..
.LLMMMMMMMMMMDD.
LL.LMMMMMMMMD.DD
L..LMMMMMMMMD..D
L..LMMMMMMMMD..D
L...LMMMMMMD...D
.L..LMMMMMMD..D.
..L.LMMMMMMD.D..
...LLMMMMMMDD...
.....LMMMMD.....
......LMMD......
.......MD.......
.......MD.......
.......MD.......
......LMMD......
.....LMMMMD.....
....LLLLLLLL....
....LMMMMMMD....
....DDDDDDDD....
"""

# the same trophy on the shield, 8 x 9
CUP_SHIELD = """
.LLLLLL.
LLMMMMDD
L.LMMD.D
.LLMMDD.
...MD...
...MD...
..LMMD..
.LLLLLL.
.DDDDDD.
"""


def _paint(img, art, x0, y0):
    for y, row in enumerate(art.strip().splitlines()):
        for x, ch in enumerate(row.strip()):
            if ch in TONES:
                img.putpixel((x0 + x, y0 + y), TONES[ch])


def cup_banner_pattern():
    """The Cup banner pattern `aliveworkplace:cup`: a trophy, on the banner (front and back) and on the shield."""
    banner = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    _paint(banner, CUP_BANNER, 3, 12)
    _paint(banner, CUP_BANNER, 24, 12)  # the back face, x 22-41
    shield = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    _paint(shield, CUP_SHIELD, 3, 8)
    out = []
    for kind, img in (("banner", banner), ("shield", shield)):
        path = ASSETS / "textures" / "entity" / kind / "cup.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        out.append(path)
    return out


DRAW = [cup_banner_pattern]

if __name__ == "__main__":
    run(DRAW)
