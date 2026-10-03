"""Round 2 of the icon picks: new versions of the four items the owner left notes on in round 1 (the round-1 recipes
are sheets.py, papers.py and tools.py next to this file; their images are build/texture-picks/<item>/v1..v4.png).

His note for every icon: "try to follow the exact outlines minecraft has in place for certain items, for example if
minecraft already has a map design then follow that exact outline, just drawing different content atop of it". So each
version below keeps the opaque silhouette of its vanilla relative pixel for pixel (measured from vanilla's texture:
the map, the stick, the wooden hoe) and draws its own shading and content inside it, in vanilla's sampled colours.

- patrol_map (he picked v2: a guard's shield starting a dashed red loop): v2's content on the vanilla map's outline,
  in our parchment (r2a) and in vanilla's paler map paper and its edge shading (r2b).
- quarry_marker (he picked v1's chequered flag, "use the stick pattern from the paper card with a red x"): the flag
  mounted on top of v4's stick the way v4's card is, square (r2a) or with a swallowtail (r2b). The stick enters the
  flag's bottom edge, so the swallowtail is cut into the fly end, where it shows.
- scan_tool (he picked v4, the blueprint-blue pencil: "straighten out line, make it more obviously a pencil with the
  tip coming to a point"): a pencil on a clean 45-degree line, every row the same 4 px band from the eraser to the
  wood, then a cone that narrows 4-3-2-1 to a graphite point; blue (r2a) or blue with a white stripe (r2b).
- field_marker (he rejected all four): r2a the quarry marker's post and square flag in farm green and yellow, a
  matching pair; r2b a field plan on the vanilla map: crop rows inside four red corner pegs; r2c a surveyor's line,
  the vanilla stick and a short peg with a taut twine between their heads; r2d the vanilla wooden hoe with a little
  green-and-yellow pennant tied to its handle.

    python3 tools/textures/picks/round2.py     writes build/texture-picks/round2/<item>/r2a.png .. + captions.json
"""
import importlib.util
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / "tools/textures/art"))

from artlib import *  # noqa: E402,F401,F403
from items import BLUE, INK, PARCHMENT, ROUTE, WOOD, GOLD, on  # noqa: E402


def _round1(name):
    """A round-1 module of this folder, loaded by path ('tools' would otherwise find pxlib/tools.py)."""
    spec = importlib.util.spec_from_file_location(f"picks_{name}", Path(__file__).with_name(f"{name}.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


R1 = _round1("tools")

OUT = REPO / "build" / "texture-picks" / "round2"


def draw(art, legend, ramp=None):
    """A 16x16 sprite from an ASCII block; missing rows at the bottom are transparent."""
    rows = [r.strip() for r in art.strip().split("\n")]
    rows = [r.ljust(16, ".") for r in rows] + ["." * 16] * (16 - len(rows))
    bad = [i for i, r in enumerate(rows) if len(r) != 16]
    assert not bad and len(rows) == 16, f"rows {bad} are wider than 16, or more than 16 rows"
    return Sprite.from_ascii("\n".join(rows), legend, ramp)


# --- The vanilla map's outline --------------------------------------------------------------------------------------
# The opaque pixels are exactly vanilla's map.png (and paper.png: the same sheet): a square sheet lying on the
# diagonal, its top corner at x 9-10, its left corner at (1, 8), its right corner at (15, 7). The shading is ours, in
# the mod's sheet convention: a = lit outline (the top edges and the left corner), d = shadow outline (the bottom
# edges and the right corner), 3 = the lit band under the top edges, 2 = the face, 1 = the band over the bottom edges.
MAP = """
    ................
    ................
    .........aa.....
    ........a33a....
    ......aa3223a...
    .....a3322223a..
    ...aa322222223a.
    ..a332222222221d
    .a122222222221d.
    ..d1222222211d..
    ...d1222221dd...
    ....d12211d.....
    .....d11dd......
    ......dd........
    ................
    ................
"""

# Vanilla's map paper, sampled from map.png: a pale cream face with a yellow-green cast and olive outlines. Its sheet
# is lightest in the middle and a little darker towards every edge (as if the paper curled up), darkest at the bottom
# corner. MAP_PALE puts that on the outline above, our own way: the face 2 lightest, the bands along the edges a step
# darker, the bottom corner (0) darkest.
MAP_PALE = """
    ................
    ................
    .........aa.....
    ........a22a....
    ......aa2332a...
    .....a2233332a..
    ...aa233333332a.
    ..a223333333321d
    .a123333333321d.
    ..d1233333321d..
    ...d1233321dd...
    ....d11221d.....
    .....d10dd......
    ......dd........
    ................
    ................
"""
VANILLA_MAP = Ramp(["#bbc177", "#daddaa", "#eaeac7", "#fcfcf2"], outline=("#a7a848", "#878839"), name="map paper")


def vmap(ramp, art=MAP):
    """The vanilla map's sheet in a ramp of four (darkest .. lightest) with its outline pair."""
    return grid(art, {"a": ramp.outline_light, "d": ramp.outline_dark}, ramp=Ramp(list(ramp)[-4:]))


# --- Patrol map -----------------------------------------------------------------------------------------------------
# v2's content: a blue guard's shield with a gold boss (the Guard Post's colours) at the start of a dashed red loop.
# The shield sits wholly inside the sheet's lower-left half; the loop leaves its top, runs round the wider right half
# of the vanilla sheet and comes back to its side, in dashes of two with gaps of one along the path.
SHIELD = {"k": "#1f3352", "b": BLUE[2], "B": BLUE[3], "Y": GOLD[2], "R": ROUTE}
PATROL = """
    ................
    ................
    ................
    ................
    .........RR.....
    .......R....R...
    ......R......R..
    ....kkkkk.......
    ....kBbbk...R...
    ....kbYbk..R....
    ....kbbbkR......
    .....kbk........
    ......k.........
"""


def patrol_map_a():
    """v2 on the vanilla map's outline, in the mod's parchment."""
    return on(vmap(PARCHMENT), PATROL, SHIELD)


def patrol_map_b():
    """v2 on the vanilla map's outline, in vanilla's map paper and its edge shading."""
    return on(vmap(VANILLA_MAP, MAP_PALE), PATROL, SHIELD)


# --- Quarry marker --------------------------------------------------------------------------------------------------
# v4's stick exactly (the vanilla stick one row lower, its foot in the bottom-left corner): A lit outline, B/C the
# wood alternating, E shadow outline, EE the cap. Its top enters the bottom edge of the flag (row 7), as it enters
# v4's card.
STICK_BOTTOM = """
    ........ABE.....
    .......ACE......
    ......ABE.......
    .....ACE........
    ....ABE.........
    ...ACE..........
    ..ABE...........
    ..EE............
""".lstrip("\n")

# v1's flag: 2x2 squares of white and red cloth, lit from the top-left (w, r: the shaded right column and hem),
# outlined in dark red; v1's colours.
QUARRY = dict(WOOD, W=R1.WHITE["w"], w=R1.WHITE["W"], R=R1.RED["r"], r=R1.RED["R"], a="#7a2a22", d="#4a1a16")

FLAG_SQUARE = """
    ................
    .......aaaaaaad.
    .......aWWRRWWd.
    .......aWWRRWwd.
    .......aRRWWRrd.
    .......aRRWWRrd.
    .......awwrrwwd.
    .......ddACEddd.
""" + STICK_BOTTOM

# The same flag a column wider, with a swallowtail cut into its fly end: two tails at the top and bottom right, a
# V-shaped notch between them.
FLAG_SWALLOWTAIL = """
    ................
    .......aaaaaaaad
    .......aWWRRWWRd
    .......aWWRRWWd.
    .......aRRWWRd..
    .......aRRWWRRd.
    .......awwrrwwrd
    .......ddACEdddd
""" + STICK_BOTTOM


def quarry_marker_a():
    """v1's chequered flag, square like v4's card, on v4's stick."""
    return draw(FLAG_SQUARE, QUARRY)


def quarry_marker_b():
    """v1's chequered flag with a swallowtail, on v4's stick."""
    return draw(FLAG_SWALLOWTAIL, QUARRY)


# --- Scan tool ------------------------------------------------------------------------------------------------------
# A pencil on a clean 45-degree line, from the eraser in the bottom-left to the point in the top-right like vanilla's
# tools. Every row of eraser, ferrule and body is the same 4 px band one pixel further along (f lit outline, g lit
# face, h shaded face, i shadow outline), so both edges are perfect staircases. The sharpened wood (t W w T: lit edge,
# light, shade, dark edge) narrows 4-3-2 px to a graphite point (K, its lit side in the ferrule's grey).
PENCIL = """
    ................
    ................
    ..............K.
    .............oT.
    ...........tWT..
    ..........tWwT..
    .........fghi...
    ........fghi....
    .......fghi.....
    ......fghi......
    .....fghi.......
    ....oImo........
    ...oImo.........
    ..pPpq..........
    ..qq............
"""
PENCIL_COLOURS = {
    "f": BLUE.outline_light, "g": BLUE[3], "h": BLUE[2], "i": BLUE.outline_dark, "x": INK,   # blueprint blue
    "W": "#ecd5a4", "w": "#cfab6c", "t": "#9c7a44", "T": "#6b5328",                           # cedar, sharpened
    "K": "#2e2e36",                                                                           # graphite
    "o": "#727272", "I": "#d8d8d8", "m": "#a8a8a8",                                           # iron ferrule
    "P": "#f4a7c0", "p": "#e07a9e", "q": "#9c3c5c",                                           # pink eraser
}


def scan_tool_a():
    """A blueprint-blue drafting pencil, sharpened to a point."""
    return draw(PENCIL, PENCIL_COLOURS)


def scan_tool_b():
    """The same pencil with a white blueprint line down its shaded face."""
    return draw(PENCIL.replace("fghi", "fgxi"), PENCIL_COLOURS)


# --- Field marker ---------------------------------------------------------------------------------------------------
# Farm colours: wheat-yellow and leaf-green cloth (yellow wool, the round-1 marker's green), dark green outline.
FARM = dict(WOOD, W="#f9cf3a", w="#d9a91f", R=R1.GREEN["f"], r=R1.GREEN["s"], a="#2f6a1c", d=R1.GREEN["o"])


def field_marker_a():
    """The quarry marker's post and square flag in green and yellow: the two markers as a pair."""
    return draw(FLAG_SQUARE, FARM)


# A field plan on the vanilla map: a square plot, rows of green crops on tilled soil (farmland brown), a red peg at
# each of its four corners.
FIELD_PLAN = """
    ................
    ................
    ................
    ................
    .......p........
    ......sGs.......
    .....GsGsG......
    ....sGsGsGs.....
    ...pGsGsGsGp....
    ....sGsGsGs.....
    .....GsGsG......
    ......sGs.......
    .......p........
"""


def field_marker_b():
    """A field plan on the vanilla map: crop rows inside four red corner pegs."""
    return on(vmap(PARCHMENT), FIELD_PLAN, {"G": "#5a9a2a", "s": "#8a5a2b", "p": ROUTE})


# A surveyor's line: the vanilla stick as the stake (rows 2-14 exactly, with its own light/dark wood alternation), a
# short peg of the same stick at the top-left, and a taut twine between them, tied just under both heads so the two
# pegs show above it: twisted in the farm flag's yellow and green (w, v), wound once round each peg.
SURVEY = """
    ................
    ................
    ...AE........AE.
    ..ABE.......ABE.
    .AwEwvwvwvwAwE..
    .EE.......ACE...
    .........ABE....
    ........ACE.....
    .......ABE......
    ......ACE.......
    .....ABE........
    ....ACE.........
    ...ABE..........
    ..ABE...........
    ..EE............
"""


def field_marker_c():
    """Two pegs with a taut twine line between them, on the vanilla stick."""
    return draw(SURVEY, dict(WOOD, w=FARM["W"], v=FARM["R"]))


# The vanilla wooden hoe's outline (its opaque pixels exactly), our own shading of its oak head (H lit, h face,
# j shade, O and Q outline), the vanilla stick's pattern for its handle, and a little green-and-yellow pennant tied
# below the head, pointing away from the handle.
HOE = """
    ................
    .......OOO......
    ......OHHhO.....
    .......QQhjOAC..
    .........QhjBQ..
    ..........AjjQ..
    .........ACQQ...
    ........ABE.....
    .......ACEaa....
    ......ABEaRWaa..
    .....ACE.aRWWWd.
    ....ABE..aRWdd..
    ...ACE...dd.....
    ..ABE...........
    ..EE............
"""


def field_marker_d():
    """The vanilla wooden hoe with a little green-and-yellow pennant tied to its handle."""
    oak = P("wood_tool")
    return draw(HOE, dict(FARM, O=oak.outline_light, Q=oak.outline_dark, H=oak[4], h=oak[3], j=oak[1]))


ITEMS = {
    "patrol_map": [("r2a", patrol_map_a, "Shield patrol map, parchment, vanilla outline"),
                   ("r2b", patrol_map_b, "Shield patrol map, vanilla map paper")],
    "quarry_marker": [("r2a", quarry_marker_a, "Square chequered flag on the card's stick"),
                      ("r2b", quarry_marker_b, "Swallowtail chequered flag on the card's stick")],
    "scan_tool": [("r2a", scan_tool_a, "Straight blue pencil sharpened to a point"),
                  ("r2b", scan_tool_b, "Blue pencil with a white blueprint stripe")],
    "field_marker": [("r2a", field_marker_a, "Green-and-yellow flag, quarry marker's twin"),
                     ("r2b", field_marker_b, "Field plan: crop rows, corner pegs"),
                     ("r2c", field_marker_c, "Two pegs with a taut string line"),
                     ("r2d", field_marker_d, "Wooden hoe with a farm pennant")],
}


def main():
    for name, versions in ITEMS.items():
        folder = OUT / name
        folder.mkdir(parents=True, exist_ok=True)
        for version, fn, _ in versions:
            path = fn().save(folder / f"{version}.png")
            print("wrote", path.relative_to(REPO))
        captions = {version: caption for version, _, caption in versions}
        (folder / "captions.json").write_text(json.dumps(captions, indent=2) + "\n")


if __name__ == "__main__":
    main()
