"""
The Waystone Plaza (ROADMAP 41.1): with the Waystones mod installed, the Steward proposes one by the Village Hall. A
round of paving in the village's own stone, a two-step dais in the middle with the mod's waystone on it (its style
matching the culture: plain stone for plains, sandy for the desert, mud brick for the savanna, deepslate for the snowy
villages, mossy for the taiga), a lamp post on each corner, a bench on each side facing the stone and potted plants by
the steps. Plains is drawn as `waystone_plaza`; the other four cultures are looks (`looks/<culture>/waystone_plaza`,
BlueprintLooks), so the Steward builds the one the village's biome grows and the family counts once.

7 x 5 x 7, the front (z = 0, north) open with steps up the dais. Only the waystone is a Waystones block; the plaza is
offered only with the mod installed.

python3 tools/blueprints/waystones.py writes the five files (generate.py calls save_all too).
"""
import os

from kit import *
from decor import disc, pave

WAYSTONES = "waystones:"

# culture: (paving, border, dais block, dais stairs/slab family, post, bench family, pot, waystone)
CULTURES = {
    "plains": dict(paving=Mix((5, "cobblestone"), (3, "stone"), (2, "andesite"), seed=41), border="polished_andesite",
                   dais="stone_bricks", fam=STONE_BRICK, post="oak_fence", bench=wood("oak"), pot="potted_poppy",
                   stone="waystone"),
    "desert": dict(paving=Mix((5, "smooth_sandstone"), (3, "sandstone"), (1, "cut_sandstone"), seed=42), border="cut_sandstone",
                   dais="chiseled_sandstone", fam=SANDSTONE, post="birch_fence", bench=SMOOTH_SANDSTONE,
                   pot="potted_cactus", stone="sandy_waystone"),
    "savanna": dict(paving=Mix((4, "packed_mud"), (3, "terracotta"), (1, "orange_terracotta"), seed=43), border="mud_bricks",
                    dais="mud_bricks", fam=Family("mud_bricks", "mud_brick_stairs", "mud_brick_slab"), post="acacia_fence", bench=wood("acacia"),
                    pot="potted_acacia_sapling", stone="mud_bricks_waystone"),
    "snowy": dict(paving=Mix((4, "stone_bricks"), (2, "polished_diorite"), (1, "cracked_stone_bricks"), seed=44),
                  border="polished_diorite", dais="polished_deepslate", fam=Family("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab"), post="spruce_fence",
                  bench=wood("spruce"), pot="potted_spruce_sapling", stone="deepslate_waystone"),
    "taiga": dict(paving=Mix((4, "cobblestone"), (3, "mossy_cobblestone"), (2, "stone"), seed=45), border="mossy_stone_bricks",
                  dais="mossy_stone_bricks", fam=Family("mossy_stone_bricks", "mossy_stone_brick_stairs", "mossy_stone_brick_slab"), post="spruce_fence", bench=wood("spruce"),
                  pot="potted_fern", stone="mossy_waystone"),
}


def waystone_plaza(culture="plains"):
    """7 x 5 x 7: the Waystone Plaza in `culture`'s style."""
    c = CULTURES[culture]
    b = Build(7, 5, 7)
    pave(b, [(x, z) for x in range(7) for z in range(7) if (x, z) not in ((0, 0), (6, 0), (0, 6), (6, 6))],
         name=c["paving"], border=c["border"])
    # The dais: a 3 x 3 step of the culture's stone, its edge stairs on three sides (the back a full block), the stone on top
    for x in range(2, 5):
        for z in range(2, 5):
            b.set(x, 1, z, c["dais"])
    for x in range(2, 5):
        stairs(b, x, 1, 1, c["fam"], "south")       # the steps up from the front
    for z in range(2, 5):
        stairs(b, 1, 1, z, c["fam"], "east")
        stairs(b, 5, 1, z, c["fam"], "west")
    for x in (1, 5):
        slab(b, x, 1, 1, c["fam"])                   # the front corners
        slab(b, x, 1, 5, c["fam"])
    for x in range(2, 5):
        slab(b, x, 1, 5, c["fam"])
    b.set(3, 2, 3, WAYSTONES + c["stone"], facing="north", half="lower", origin="village", waterlogged=False)
    b.set(3, 3, 3, WAYSTONES + c["stone"], facing="north", half="upper", origin="village", waterlogged=False)
    # Lamp posts on the four corners of the paving's inner round
    for x, z in ((0, 1), (6, 1), (0, 5), (6, 5)):
        lamp_post(b, x, 1, z, post=c["post"], height=2)
    # A bench on each side, facing the stone
    stairs(b, 0, 1, 3, c["bench"], "east")
    stairs(b, 6, 1, 3, c["bench"], "west")
    # Potted plants on the dais' front corners
    b.set(2, 2, 2, c["pot"])
    b.set(4, 2, 2, c["pot"])
    b.fill_air()
    return b


def save_all():
    waystone_plaza("plains").save(MAIN_STRUCTURES, "waystone_plaza")
    for culture in ("desert", "savanna", "snowy", "taiga"):
        waystone_plaza(culture).save(os.path.join(MAIN_STRUCTURES, "looks", culture), "waystone_plaza")


if __name__ == "__main__":
    save_all()
