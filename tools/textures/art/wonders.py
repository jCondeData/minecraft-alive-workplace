"""A Wonder's things (M35): the Foundation Stone (35.4), a carved block of smooth grey stone like vanilla's chiseled
stone and polished stones (bevelled edge lit top-left, a recessed panel), with a riveted brass plaque set into each
side and a carved square on top."""
from artlib import *  # noqa: F401,F403

STONE = P("stone")                         # 0 #686868 .. 3 #8f8f8f
LIT = "#a3a3a3"                            # the bevel's lit top and left edges
SHADE = "#555353"                          # the bevel's shaded bottom and right edges, the panel's inner shadow
BRASS = Ramp(["#6b4a1a", "#93692a", "#b98d3c", "#d6b25a", "#ecd690"], name="brass")


def carved_face(seed):
    """Smooth stone with a 1 px bevel: lit on the top and left, shaded on the bottom and right."""
    s = stone_tile(STONE[0:4], seed=seed)
    s.fill(rect(0, 0, 15, 0), LIT)
    s.fill(rect(0, 0, 0, 15), LIT)
    s.fill(rect(0, 15, 15, 15), SHADE)
    s.fill(rect(15, 1, 15, 15), SHADE)
    return s


def foundation_stone():
    """Side: a recessed panel holding a brass plaque (engraved lines, four rivets). Top: a carved square, recessed."""
    side = carved_face(5)
    side.paste(grid("""
        ssssssssssss
        sqqqqqqqqqqk
        sqqeddddddek
        sqqdcccccbak
        sqqdbbbbbcak
        sqqdcccccbak
        sqqdbbbbccak
        sqqeaaaaaaek
        sqqqqqqqqqqk
        skkkkkkkkkkk
    """, {"s": SHADE, "k": LIT, "q": STONE[1], "a": BRASS[0], "b": BRASS[1], "c": BRASS[2], "d": BRASS[3], "e": BRASS[4]}), 2, 3)

    top = carved_face(9)
    top.paste(grid("""
        ssssssssss
        s32222221l
        s21111110l
        s21333310l
        s21300310l
        s21300310l
        s21333310l
        s21111110l
        s10000000l
        slllllllll
    """, {"s": SHADE, "l": LIT}, ramp=STONE), 3, 3)
    return [side.save(block("foundation_stone_side")), top.save(block("foundation_stone_top"))]


DRAW = [foundation_stone]

if __name__ == "__main__":
    run(DRAW)
