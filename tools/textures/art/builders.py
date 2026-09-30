"""The Builder's things: the Builder's Bench (block) and the Builder's outfit. Approved by the owner as the style
for the rest (2026-09-29)."""
from artlib import *  # noqa: F401,F403

OAK = P("oak_planks")                     # 7 shades, darkest first
BLUEPRINT = Ramp(["#1f467f", "#2d5fa6", "#3a6fb8", "#4f86cc"], name="blueprint")
INK = "#dce9f7"
STEEL = Ramp(["#5a5a5a", "#8c8c8c", "#b4b4b4", "#d2d2d2"], name="steel")


def bench_face(seed, wood=OAK):
    """A workstation face: the planks tile with a 1 px frame in the darkest shade, like the crafting table."""
    s = planks_tile(wood[1:6], boards=4, seed=seed)
    frame(s, wood[0])
    return s


def builders_bench():
    """Top: a blueprint pinned to the boards (a house drawn on it) with a pencil and a ruler; front: rolled
    blueprints standing in a cubby above a drawer; side: a carpenter's square and a hammer on the wall."""
    top = bench_face(7)
    top.paste(grid("""
        r1111111111r
        122222222221
        12222ww22221
        1222w22w2221
        122w2222w221
        12wwwwwwww21
        122w2222w221
        122w2ww2w221
        122w2ww2w221
        12wwwwwwww21
        122222222221
        000000000000
    """, {"r": "#c4412f", "w": INK}, ramp=BLUEPRINT), 2, 1)
    top.paste(grid("""
        ...........p
        ..........y.
        .........y..
        kkkkkkd.t...
    """, {"p": "#d98a8a", "y": "#e2b33c", "t": "#3a2a1a", "k": "#d9c9a0", "d": "#a8966a"}), 2, 11)

    front = bench_face(11)
    front.fill(rect(2, 2, 13, 7), OAK[0])                                 # the cubby
    for x in (3, 7, 11):                                                   # three rolled blueprints standing in it
        front.fill(rect(x, 3, x, 7), BLUEPRINT[2])
        front.fill(rect(x + 1, 3, x + 1, 7), BLUEPRINT[1])
        front.put(x, 3, INK)
        front.put(x + 1, 3, BLUEPRINT[3])
    front.fill(rect(2, 8, 13, 8), OAK[1])                                  # shelf edge
    front.paste(grid("""
        0000000000
        0555555540
        0544444430
        0544hj4430
        0433333320
        0000000000
    """, {"h": STEEL[3], "j": STEEL[1]}, ramp=OAK), 3, 9)

    side = bench_face(3)
    side.paste(grid("""
        c.....
        c.....
        c.....
        c.....
        c.....
        c.....
        cccccc
    """, {"c": STEEL[3]}), 3, 4)
    side.paste(grid("""
        lhhhh
        ggggg
        ..w..
        ..v..
        ..w..
        ..v..
        ..w..
        ..v..
    """, {"l": STEEL[3], "h": STEEL[2], "g": STEEL[0], "w": "#7a5a34", "v": "#553a1f"}), 8, 3)
    return [top.save(block("builders_bench_top")), front.save(block("builders_bench_front")),
            side.save(block("builders_bench_side"))]


def builder():
    """A yellow hard hat (a short lip all round, a peak at the front, a ridge over the top), a hi-vis orange vest with
    two reflective bands, a leather tool belt with a tape measure at the front and a hammer at the side. Keeps the
    biome's own sleeves."""
    t = vg.VillagerTexture()
    helmet = ramp("#eab31d", n=5, spread=0.45)          # hard plastic: a px.ramp, not cloth
    hivis = vg.cloth("#ef7d1c")
    tape = "#e4e6d6"                                     # reflective tape: pale, not pure white
    wood = ["#553a1f", "#7a5a34"]

    vg.hat(t, helmet, style="cap", visor=2, noise=0)
    rim = t.face("hat_rim", "front")                     # a 1 px lip round the sides and back (the visor is the peak)
    for i in range(3, 13):
        for x, y in ((3, i), (12, i), (i, 3)):
            if rim.get(x, y)[3] == 0:
                rim.put(x, y, helmet[1])
    top = t.face("hat", "top")
    for y in range(top.h):                               # the ridge from front to back, lit; a darker ring = a dome
        for x in range(top.w):
            if x in (3, 4):
                top.put(x, y, helmet[4] if x == 3 else helmet[3])
            elif x in (0, top.w - 1) or y in (0, top.h - 1):
                top.put(x, y, helmet[1])
    for side in ("front", "back"):
        f = t.face("hat", side)
        for y in range(3):
            f.put(3, y, helmet[4] if side == "front" else helmet[3])
            f.put(4, y, helmet[3] if side == "front" else helmet[2])

    vg.vest(t, hivis, length=10, open_front=True, noise=0)
    for side in ("front", "back", "west", "east"):
        f = t.face("jacket", side)
        for x in range(f.w):
            for y in (4, 7):
                if f.get(x, y)[3]:
                    f.put(x, y, tape)

    vg.belt(t, P("leather"), row=10, buckle="#c8a24a")
    front = t.face("jacket", "front")                    # tape measure on the belt (x 0..3: the badge is at x 4..7)
    for x, y, c in ((1, 11, helmet[3]), (2, 11, helmet[2]), (1, 12, helmet[1]), (2, 12, STEEL[1])):
        front.put(x, y, c)
    side = t.face("jacket", "west")                      # a hammer hanging at the villager's right side
    side.put(2, 11, STEEL[2])
    side.put(3, 11, STEEL[1])
    side.put(4, 11, STEEL[0])
    for y in (12, 13, 14):
        side.put(3, y, wood[1] if y % 2 else wood[0])
    return t.save_profession(ASSETS, "builder", hat="full")


DRAW = [builders_bench, builder]

if __name__ == "__main__":
    run(DRAW)
