"""
The reforms as stories (ROADMAP 31.22): the builds an ending leaves behind, set down by story/ReformStories.

  story/shift_bell  3 x 6 x 3: The Shift Bell's bell post beside the hall: a spruce post on a stone-brick footing, a
                    stripped spruce beam out over the yard with a knee brace under it, and the bell hanging from its end,
                    a lantern at its foot for the dark mornings. Its bottom layer is the ground: only the footing is
                    drawn there, so the post stands on whatever the land is.

Design on paper: the main mass is the post (1 x 4, on its footing), the secondary mass the beam and the bell; spruce and
stone brick, as the village's own work yards are.
"""
from kit import *
from caravan import keep_ground


def shift_bell():
    W, H, D = 3, 6, 3
    b = Build(W, H, D)
    # the footing, sunk into the ground: a stone-brick pad under the post, cobbles round its foot
    b.set(0, 0, 1, "stone_bricks")
    for x, z in ((0, 0), (0, 2), (1, 1)):
        b.set(x, 0, z, "cobblestone")
    # the post: a stone-brick plinth, three logs of spruce
    b.set(0, 1, 1, "stone_bricks")
    for y in range(2, 5):
        log(b, 0, y, 1, "spruce_log")
    # the beam out over the yard, braced, and the bell at its end
    for x in range(0, 3):
        log(b, x, 5, 1, "stripped_spruce_log", axis="x")
    stairs(b, 1, 4, 1, SPRUCE, "west", top=True)
    b.set(2, 4, 1, "bell", attachment="ceiling", facing="east", powered=False)
    lantern(b, 1, 1, 1)
    return keep_ground(b)
