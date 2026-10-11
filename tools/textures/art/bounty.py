"""The bounty board and bandit captains (ROADMAP 31.13): the Bounty Board block, the Wanted Poster and the Captain's
Insignia.

- The board's texture is one sheet its model takes parts of, as vanilla's lectern and campfire do: the board's face
  (u 0-13, v 0-11: oak planks in a frame of the darkest oak, like a sign), the posts' grain (u 14-15, the full height:
  oak, a shade darker in runs), a pinned poster (u 0-3, v 12-15: vanilla map paper with a face sketched on it and a red pin) and
  plain boards for the board's edges (u 4-13, v 12-15).
- The Wanted Poster is a sheet on vanilla's exact map outline and pale map paper, as the patrol map and City Plan
  are: a charcoal face sketched on it and a red line for the reward under it.
- The Captain's Insignia hangs from a short red-and-black ribbon like a medal: a dark iron shield with a gold rim and
  a gold boss, lit from the top-left.
"""
from artlib import *  # noqa: F401,F403
from items import MAP_PALE, VANILLA_MAP, vmap, on, GOLD

OAK = P("oak_planks")
POST = [OAK[3], OAK[4], OAK[2], OAK[1]]
PAPER = {"P": "#fcfcf2", "p": "#eaeac7", "q": "#daddaa", "k": "#4a4038", "r": "#c02a22", "R": "#8e1f1a"}


def bounty_board():
    """The board's sheet: framed oak boards (the face), oak posts, a pinned poster and plain edge boards."""
    s = planks_tile(OAK[1:6], boards=4, seed=311)
    face = planks_tile(OAK[1:6], boards=4, seed=312)
    # the face: 14 x 12 framed in the darkest oak
    for y in range(12):
        for x in range(14):
            edge = x in (0, 13) or y in (0, 11)
            s.put(x, y, OAK[0] if edge else face.get(x + 1, y + 2))
    # the posts: bark in vertical runs, two columns
    for y in range(16):
        s.put(14, y, POST[1] if y % 5 in (1, 2) else POST[2])
        s.put(15, y, POST[0] if y % 4 == 3 else POST[1])
    # the poster, 4 x 4: a red pin, the face, the reward line
    s.paste(grid("""
        pppr
        pkkp
        pqkp
        qRRq
    """, PAPER), 0, 12)
    return [s.save(block("bounty_board"))]


# A face sketched in charcoal in the middle of the sheet (hair, two eyes, a scowl and a scar), with a red reward line.
WANTED = """
    ................
    ................
    ................
    ................
    ........kkk.....
    .......kkkkk....
    .......k.k.k....
    .......k...k....
    ........kmk.....
    .........k......
    .....rrrr.......
    ................
"""


def wanted_poster():
    """The Wanted Poster: vanilla's map paper and outline with a captain's face in charcoal and a red reward line."""
    return [on(vmap(VANILLA_MAP, MAP_PALE), WANTED, {"k": "#4a4038", "m": "#7d6c55", "r": "#c02a22"}).save(item("wanted_poster"))]


def captains_insignia():
    """The Captain's Insignia: a dark iron shield with a gold rim and boss, hanging from a red-and-black ribbon."""
    s = grid("""
        ................
        .....oRRBo......
        .....oRRBo......
        ......oRBo......
        ......oRBo......
        ...yyyyyyyyy....
        ...yYWWWWWWyz...
        ...yW1112211z...
        ...yW11gg011z...
        ...yW1gGGg01z...
        ....z11gg001z...
        ....z1100000z...
        .....z10000z....
        ......z000z.....
        .......zzz......
        ................
    """, {"o": "#3a0f0c", "R": "#b0302a", "B": "#2a2a30", "y": GOLD.outline_light, "z": GOLD.outline_dark,
          "Y": GOLD[3], "W": GOLD[2], "g": GOLD[2], "G": GOLD[3]},
        ramp=Ramp(["#2c2d36", "#3b3d4a", "#4c4f60"], name="dark iron"))
    return [s.save(item("captains_insignia"))]


DRAW = [bounty_board, wanted_poster, captains_insignia]

if __name__ == "__main__":
    run(DRAW)
