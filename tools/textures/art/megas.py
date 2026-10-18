"""Mega Evolution's look in the village (ROADMAP 38.5): the Mega Bracelet a villager trainer wears when their team can
Mega Evolve, drawn over their job's outfit at textures/entity/villager/mega_bracelet.png. The client draws it only with
Mega Showdown installed.

A gold band round the villager's left forearm (the crossed arms' middle box: on its front face x 0 is the villager's
right, so the left wrist is x 5..6, clear of the seam at x 4 where the forearms cross), with the rainbow Key Stone set
in its front: a 2x2 stone, its glint on the top left as light falls, the colours swirling round it as Mega Showdown's
Key Stone does. Nothing else on the body, so the job's outfit, hat and badge stay as they are."""
from artlib import *  # noqa: F401,F403

GOLD = Ramp(["#7a5010", "#b8841c", "#e0b03a", "#f6dc78"], name="gold")
KEYSTONE = {"glint": "#f4f0ff", "magenta": "#d04ab8", "cyan": "#3cc4d8", "amber": "#e88a2c"}
BAND = (5, 6)                         # arms_middle columns: the villager's left wrist


def mega_bracelet_texture():
    t = vg.VillagerTexture()
    front = t.face("arms_middle", "front")
    for x in BAND:                                 # the band: lit at the top, its edge dark below
        front.put(x, 0, rgba(GOLD[3] if x == BAND[0] else GOLD[2]))
        front.put(x, 3, rgba(GOLD[0]))
    front.put(BAND[0], 1, rgba(KEYSTONE["glint"]))  # the Key Stone in the band's front
    front.put(BAND[1], 1, rgba(KEYSTONE["magenta"]))
    front.put(BAND[0], 2, rgba(KEYSTONE["cyan"]))
    front.put(BAND[1], 2, rgba(KEYSTONE["amber"]))
    for side, shade in (("top", 2), ("bottom", 1)):  # round the forearm, seen from above and below
        f = t.face("arms_middle", side)
        for y in range(f.h):
            for x in BAND:
                f.put(x, y, rgba(GOLD[shade + (1 if x == BAND[0] and side == "top" else 0)]))
    return t


def mega_bracelet():
    """The Mega Bracelet layer, at textures/entity/villager/mega_bracelet.png."""
    path = ASSETS / "textures" / "entity" / "villager" / "mega_bracelet.png"
    mega_bracelet_texture().save(path)
    return [path]


DRAW = [mega_bracelet]

if __name__ == "__main__":
    run(DRAW)
