"""Pick-sheet variants (v2, v3, v4) for the held items and the settler's wagon, for the owner to choose from. The
current icons (tools/textures/art/items.py) are v1 and are not touched here.

Each version is built around a different idea, not a recolour:

- field_marker: a wheat sheaf tied to a stake, a sapling tied to a stake, a garden sign with a sprout;
- quarry_marker: a striped surveyor's ranging pole, a levelling rod with a bullseye target, a paper card with a red X;
- rally_banner: a spear with a swallowtail pennon, a red shield with a sword on a pole, a tall upright standard with
  crossed swords;
- scan_tool: a blueprint rolled into a spyglass, a brass magnifier with a blueprint-blue lens, a drafting pencil;
- settlers_wagon: a covered wagon in side view, a handcart with a bundle and a bedroll, an open cart with hay and a
  chest.

Held items keep the tool convention (handle at the bottom-left, head at the top-right, the vanilla stick's pattern:
A lit outline, B/C wood alternating, E shadow outline, an EE cap at the bottom). Colours come from vanilla ramps
(wheat, oak, iron tool, gold, wool, paper) and from items.py.

    python3 tools/textures/picks/tools.py      # writes build/texture-picks/<item>/v2..v4.png + captions.json
"""
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / "tools/textures/art"))

from artlib import *  # noqa: E402,F401,F403
from items import GOLD, PAPER, WOOD, BLUE, INK  # noqa: E402

OUT = REPO / "build/texture-picks"


def draw(art, legend):
    """A 16x16 sprite from an ASCII block; missing rows at the bottom are transparent."""
    rows = [r.strip() for r in art.strip().split("\n")]
    rows = [r.ljust(16, ".") for r in rows] + ["." * 16] * (16 - len(rows))
    bad = [i for i, r in enumerate(rows) if len(r) != 16]
    assert not bad and len(rows) == 16, f"rows {bad} are wider than 16, or more than 16 rows"
    return Sprite.from_ascii("\n".join(rows), legend)


# --- Field marker -----------------------------------------------------------------------------------------------------
GREEN = dict(g="#7fcc4a", f="#5aa832", s="#3f8425", o="#1f4a14")            # the current marker's flag green
WHEAT = dict(e="#dcbb65", d="#cdb159", c="#a69553", k="#7f6a33", K="#565138")  # vanilla wheat
OAK = dict(P="#c29d62", p="#af8f55", q="#967441", O="#5a4524", Q="#3a2a12")    # oak planks, darker outline


def fm_sheaf():
    """A sheaf of wheat lashed to the top of the stake with green twine: kernels in a checker like vanilla's wheat,
    ear tips poking out towards the top-right."""
    return draw("""
        ...........e....
        ..........kdke..
        .........kedkdke
        ........kedededK
        ........kdedcdeK
        .........kcdcdK.
        .........kcccK..
        ........kccK....
        .......kgsK.....
        ......AgsE......
        .....ACE........
        ....ABE.........
        ...ACE..........
        ..ABE...........
        ..EE............
    """, {**WOOD, **WHEAT, **GREEN})


def fm_sapling():
    """A young tree tied to the stake with a white string, the way saplings are staked: for tree farms and orchards."""
    return draw("""
        ..........lll...
        .........lgggl..
        ........lggflfll
        .......lgfflgffl
        .......lfslfsfsl
        ........llTlssl.
        .........AETll..
        ........AwwT....
        .......ACE......
        ......ABE.......
        .....ACE........
        ....ABE.........
        ...ACE..........
        ..ABE...........
        ..EE............
    """, {**WOOD, "l": "#1f5216", "g": "#6fbf4a", "f": "#4f9a33", "s": "#357a26", "T": "#70532e",
          "w": "#f2f2ec"})


def fm_sign():
    """A little oak board on the stake, like a garden label, with a green sprout painted on it."""
    return draw("""
        ................
        ......OOOOOOOOO.
        ......OPPPPPPPpQ
        ......OPpgpgPpqQ
        ......OPpsfspqqQ
        ......OppqsqqqqQ
        ......OqqqsqqqqQ
        ......QQQACEQQQ.
        ........ABE.....
        .......ACE......
        ......ABE.......
        .....ACE........
        ....ABE.........
        ...ACE..........
        ..ABE...........
        ..EE............
    """, {**WOOD, **OAK, **GREEN})


FIELD = [("v2", fm_sheaf, "Stake with a wheat sheaf tied on"),
         ("v3", fm_sapling, "Stake with a sapling tied on"),
         ("v4", fm_sign, "Garden sign with a sprout")]


# --- Quarry marker ----------------------------------------------------------------------------------------------------
RED = dict(r="#d9362b", R="#a3241c", a="#7a1a14", b="#4a0f0c")      # the current marker's red, outlined in its shades
WHITE = dict(w="#f2f2ec", W="#c9c9c0", c="#8e8e86", d="#55554e")
IRON = dict(i="#d8d8d8", I="#a8a8a8", j="#727272", o="#444444", O="#181818")   # vanilla iron tool ramp


def qm_pole():
    """A surveyor's ranging pole corner to corner: 2-row red and white bands, each band outlined in its own shades,
    with an iron shoe at the foot."""
    return draw("""
        ................
        .............aa.
        ............arRb
        ...........arRb.
        ..........cwWd..
        .........cwWd...
        ........arRb....
        .......arRb.....
        ......cwWd......
        .....cwWd.......
        ....arRb........
        ...arRb.........
        ..oiIO..........
        .oIjO...........
        .oO.............
    """, {**RED, **WHITE, **IRON})


def qm_target():
    """A levelling rod with a bullseye target on it (it marks a height as well as a spot): the rod runs behind the
    disc and out past its top-right."""
    return draw("""
        ................
        ..............AE
        ........aaaaaACE
        .......arrrrrRb.
        .......arwwwrRb.
        .......arwRwrRb.
        .......arwwwRRb.
        .......arRRRRRb.
        .......Abbbbbb..
        ......ABE.......
        .....ACE........
        ....ABE.........
        ...ACE..........
        ..ABE...........
        ..ACE...........
        ..EE............
    """, {**WOOD, **RED, **WHITE})


def qm_sign():
    """A card of vanilla paper on the stake with a red X: X marks the spot."""
    return draw("""
        ................
        .......cccccccd.
        .......cwrwwrWd.
        .......cwwrrWWd.
        .......cwwrRWWd.
        .......cwrWWRWd.
        .......cWWWWWWd.
        .......ddACEddd.
        ........ABE.....
        .......ACE......
        ......ABE.......
        .....ACE........
        ....ABE.........
        ...ACE..........
        ..ABE...........
        ..EE............
    """, {**WOOD, **RED, "w": PAPER[3], "W": PAPER[2], "c": PAPER.outline_light, "d": PAPER.outline_dark})


QUARRY = [("v2", qm_pole, "Red-and-white striped surveyor's pole"),
          ("v3", qm_target, "Levelling rod with a bullseye target"),
          ("v4", qm_sign, "Paper card with a red X")]


# --- Rally banner -----------------------------------------------------------------------------------------------------
NAVY = dict(n="#1f2f6a", N="#141d45", u="#3e4db2", U="#2d3a90", v="#c9c9c0", V="#f2f2ec")   # blue wool, silver
CLOTH = dict(r="#c8302c", R="#a8201f")                                                        # the current banner red


def rb_spear():
    """A spear with an iron leaf head and a blue-and-silver swallowtail pennon tied under it."""
    return draw("""
        ..............oo
        .............oio
        ............oiIo
        ...........oiIjO
        ..........oIjjO.
        .........oOjOO..
        .........ACEnnn.
        ........ABEnuuuN
        .......ACE.nUUN.
        ......ABE..nVVvN
        .....ACE...NNNN.
        ....ABE.........
        ...ACE..........
        ..ABE...........
        ..EE............
    """, {**WOOD, **IRON, **NAVY})


def rb_shield():
    """A red heater shield with a silver sword and a gold crossguard, iron-rimmed, carried on a pole."""
    return draw("""
        ................
        .......ooooooo..
        .......orrVrrO..
        .......orrVrRO..
        .......orrVRRO..
        .......orzzzRO..
        .......orRtRRO..
        ........orRRO...
        ........AoRO....
        .......ABEO.....
        ......ACE.......
        .....ABE........
        ....ACE.........
        ...ABE..........
        ..ACE...........
        ..EE............
    """, {**WOOD, **CLOTH, "V": "#f2f2ec", "o": "#727272", "O": "#353535", "z": GOLD[2], "t": "#5a3a18"})


def rb_standard():
    """A tall standard held upright, the way vanilla shows its banners: an iron crossbar with a gold finial, a blue
    cloth with two crossed swords (gold guards, wooden grips) and a tattered hem."""
    return draw("""
        .......yY.......
        ...xxxxxxxxxx...
        ...nVuuuuuuVN...
        ...nuVuuuuVUN...
        ...nuuVuuVUUN...
        ...nuuuVVUUUN...
        ...nuuuVVUUUN...
        ...nuuVuuVUUN...
        ...nuVuuuuVUN...
        ...nyyyuuyyyN...
        ...nutuuuuUtN...
        ...nuuuuUUUUN...
        ...nunuuNUNUN...
        ...nn.nNN.NNN...
        .......AE.......
        .......EE.......
    """, {**WOOD, **NAVY, "y": GOLD[2], "Y": GOLD[0], "x": "#6b6b6b", "t": "#5a3a18"})


RALLY = [("v2", rb_spear, "Spear with a blue-and-silver pennon"),
         ("v3", rb_shield, "Red shield with a sword on a pole"),
         ("v4", rb_standard, "Tall standard with crossed swords")]


# --- Scan tool --------------------------------------------------------------------------------------------------------
BRASS = dict(h=GOLD[4], y=GOLD[2], Y=GOLD[1], z=GOLD[0], Z=GOLD.outline_light)    # the current wand's brass
LENS = dict(L=BLUE[3], l=BLUE[1], m="#ffffff")                                     # blueprint-blue glass, one glint
BLUEPRINT = dict(n=BLUE.outline_light, N=BLUE.outline_dark, u=BLUE[2], w=INK)


def st_roll():
    """The recipe in one object: a sheet of blueprint paper rolled into a spyglass, in vanilla's spyglass pose, with
    a brass lens end at the top-right, a brass eyepiece at the bottom-left and the paper's white edge spiralling
    round the tube."""
    return draw("""
        ................
        ...........ZZZ..
        ..........ZhyyZ.
        .........ZhLmlZ.
        ........ZyLLlYZ.
        .......ZyYllYZ..
        ......nZYYYZ....
        .....nuwZZZ.....
        ....nuuuwN......
        ...nwuuuN.......
        ..ZZwuuN........
        .ZhyZwN.........
        .ZyYZN..........
        ..ZZ............
    """, {**BRASS, **LENS, **BLUEPRINT})


def st_lens():
    """A magnifying glass on a wooden handle: a brass ring round a blueprint-blue lens with a glint."""
    return draw("""
        .........ZZZZZ..
        ........ZhyyyYZ.
        .......ZhmLLLLYZ
        .......ZyLLLLlYZ
        .......ZyLLLlLYZ
        .......ZyLLlLlzZ
        .......ZyLlLllzZ
        .......AZYzzzzZ.
        ......ABEZZZZZ..
        .....ACE........
        ....ABE.........
        ...ACE..........
        ..ABE...........
        ..ACE...........
        ..EE............
    """, {**WOOD, **BRASS, **LENS})


def st_pencil():
    """A blueprint-blue drafting pencil: graphite point and sharpened wood at the top-right, white lettering flecks
    on the body, an iron ferrule and a pink eraser at the bottom-left."""
    return draw("""
        ................
        .............Gg.
        ............tTGk
        ...........tTTk.
        ..........nLuN..
        .........nLuN...
        ........nwuN....
        .......nLuN.....
        ......nLuN......
        .....nwuN.......
        ....nLuN........
        ...oiIo.........
        ..oiIo..........
        .qPqQ...........
        .QQ.............
    """, {**BLUEPRINT, "L": BLUE[3], "G": "#2e2e2e", "g": "#565656", "t": "#9c7a44", "T": "#e3c896",
          "k": "#6b5328", "o": "#727272", "i": "#d8d8d8", "I": "#a8a8a8", "P": "#f4a7c0", "q": "#da6a92",
          "Q": "#9c3c5c"})


SCAN = [("v2", st_roll, "Blueprint rolled into a spyglass"),
        ("v3", st_lens, "Brass magnifier with a blueprint lens"),
        ("v4", st_pencil, "Blueprint-blue drafting pencil")]


# --- Settler's wagon --------------------------------------------------------------------------------------------------
CANVAS = dict(W="#f6f2e6", w="#e4ddc9", v="#c8bfa6", V="#8f866e", X="#5f5846")   # the current wagon's canvas
BED = dict(P="#af8f55", p="#967441", q="#7e6237", O="#4a3a18", Q="#2e260c")        # oak
WHEEL = dict(k="#3a2712", h="#c29d62")                                            # dark rim, light spokes


def sw_side():
    """A covered wagon in side view, facing right: the canvas bonnet with its hoops, a plank bed, two spoked wheels
    and the stub of the pole."""
    return draw("""
        ................
        ................
        ....VVVVVVVV....
        ...VWWWWWWWWV...
        ..VWWvWWvWWvwV..
        ..VWwvwwvwwvwV..
        ..VwwvwwvwwvwV..
        ..VwwvwwvwwvvX..
        .OPPPPPPPPPPPPO.
        .OppppppppppppQQ
        ..kkkQQQQQkkkQ..
        .kkhkk...kkhkk..
        .khhhk...khhhk..
        .kkhkk...kkhkk..
        ..kkk.....kkk...
    """, {**CANVAS, **BED, **WHEEL})


def sw_handcart():
    """A settler's handcart: one wheel, the shafts reaching to the bottom-left, a canvas bundle and a red bedroll
    tied on top."""
    return draw("""
        ................
        ................
        .........VVVV...
        .......VVWWWWV..
        ......VWWWvWWwV.
        ...ffffWWWvWwwV.
        ..fRRfRfVvvvvVV.
        ..frrfrfPPPPPPQ.
        ..QPPPPppppppQ..
        .AQpppppqqqqQ...
        AB.QqqqkkkqQ....
        BE....kkhkk.....
        E.....khhhk.....
        ......kkhkk.....
        .......kkk......
    """, {**CANVAS, **BED, **WHEEL, **WOOD, "f": "#5a1210", "R": "#c8302c", "r": "#a8201f"})


HAY = dict(H="#cbb630", G="#ab9225", F="#8a7320", R="#a5492c")                    # vanilla hay bale, red band
CHEST = dict(c="#af8f55", C="#7e6237", e="#4a3a18", l="#d8d8d8", L="#727272")     # oak chest, iron latch


def sw_supply():
    """An open cart in side view loaded with the recipe's supplies: a hay bale and a chest."""
    return draw("""
        ................
        ................
        ................
        ........eeeee...
        ..FFFFF.eccce...
        .FHHHHHFecccce..
        .FHHHHHFeeLeee..
        .FRRRRRFeClcCe..
        .FGGGGGFeCCCCe..
        .OPPPPPPPPPPPPO.
        .OppppppppppppQQ
        ..kkkQQQQQkkkQ..
        .kkhkk...kkhkk..
        .khhhk...khhhk..
        .kkhkk...kkhkk..
        ..kkk.....kkk...
    """, {**BED, **WHEEL, **HAY, **CHEST})


WAGON = [("v2", sw_side, "Covered wagon in side view"),
         ("v3", sw_handcart, "Handcart with a bundle and bedroll"),
         ("v4", sw_supply, "Open cart with hay and a chest")]


ITEMS = {"field_marker": FIELD, "quarry_marker": QUARRY, "rally_banner": RALLY, "scan_tool": SCAN,
         "settlers_wagon": WAGON}


def main():
    for name, versions in ITEMS.items():
        d = OUT / name
        d.mkdir(parents=True, exist_ok=True)
        captions = {}
        for version, fn, caption in versions:
            fn().save(d / f"{version}.png")
            captions[version] = caption
        (d / "captions.json").write_text(json.dumps(captions, indent=2) + "\n")
        print("wrote", d.relative_to(REPO))


if __name__ == "__main__":
    main()
