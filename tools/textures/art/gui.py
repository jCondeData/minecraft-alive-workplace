"""GUI textures: the Village Hall's screen (ROADMAP 30.4a), drawn like vanilla's workstation screens.

One 256x256 sheet, textures/gui/village_hall.png, read by the client's VillageHallMenuScreen:
- the window (196x160 at 0,0): the vanilla container panel (C6C6C6, black notched border, white top-left and
  555555 bottom-right bevel), a sunken header plaque for the village's figures (row 0), an etched toolbar band for
  the page's actions (row 1) and a sunken content panel for the page itself (rows 2-5);
- the buttons (18x18 at 0,176 and 18,176): a raised vanilla button, plain and hovered, drawn under every icon.
The layout numbers are VillageHallMenuScreen's: cell x = 8 + 20 * column, rows at y 18, 44, 70, 90, 110, 130.
"""
from artlib import *  # noqa: F401,F403
from artlib import ASSETS
from PIL import Image

W, H = 196, 160
BLACK = (0, 0, 0, 255)
WHITE = (255, 255, 255, 255)
PANEL = (198, 198, 198, 255)   # C6C6C6
SHADOW = (85, 85, 85, 255)     # 555555
SUNK = (139, 139, 139, 255)    # 8B8B8B, the slot fill
SUNK_EDGE = (55, 55, 55, 255)  # 373737
DEEP = (120, 120, 120, 255)    # the content panel, a shade under a slot
HOVER = (218, 218, 218, 255)


def _rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), c)


def _panel(img, x0, y0, x1, y1, fill=PANEL, edge=BLACK, light=WHITE, dark=SHADOW, bevel=2):
    """The vanilla raised panel: a 1 px border with notched corners, then a light top-left and dark bottom-right."""
    _rect(img, x0 + 1, y0, x1 - 1, y1, edge)
    _rect(img, x0, y0 + 1, x1, y1 - 1, edge)
    _rect(img, x0 + 1, y0 + 1, x1 - 1, y1 - 1, light)
    _rect(img, x0 + 1 + bevel, y0 + 1 + bevel, x1 - 1, y1 - 1, dark)
    _rect(img, x0 + 1 + bevel, y0 + 1 + bevel, x1 - 1 - bevel, y1 - 1 - bevel, fill)
    # the two off corners where light meets dark stay the panel's colour, as vanilla's do
    for i in range(bevel):
        img.putpixel((x1 - 2 - i, y0 + 1 + i), fill)
        img.putpixel((x0 + 1 + i, y1 - 2 - i), fill)


def _sunk(img, x0, y0, x1, y1, fill=SUNK):
    """A sunken area like a slot: dark top-left, white bottom-right (1 px)."""
    _rect(img, x0, y0, x1, y1, WHITE)
    _rect(img, x0, y0, x1 - 1, y1 - 1, SUNK_EDGE)
    _rect(img, x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill)
    img.putpixel((x1 - 1, y0), SUNK)
    img.putpixel((x0, y1 - 1), SUNK)


def _etch(img, x0, x1, y):
    """An etched line across the panel (dark over light), as vanilla separates parts of a screen."""
    _rect(img, x0, y, x1, y + 1, SHADOW)
    _rect(img, x0 + 1, y + 1, x1 + 1, y + 2, WHITE)


def _button(img, ox, oy, hovered):
    fill = HOVER if hovered else PANEL
    edge = WHITE if hovered else BLACK
    _rect(img, ox + 1, oy, ox + 17, oy + 18, edge)
    _rect(img, ox, oy + 1, ox + 18, oy + 17, edge)
    _rect(img, ox + 1, oy + 1, ox + 17, oy + 17, WHITE)
    _rect(img, ox + 2, oy + 2, ox + 17, oy + 17, SHADOW)
    _rect(img, ox + 2, oy + 2, ox + 16, oy + 16, fill)
    img.putpixel((ox + 16, oy + 1), fill)
    img.putpixel((ox + 1, oy + 16), fill)


def village_hall_gui():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    _panel(img, 0, 0, W, H)
    # row 0: the village's figures on a sunken plaque
    _sunk(img, 6, 16, 190, 38)
    # row 1: the page's actions between two etched lines
    _etch(img, 6, 189, 41)
    _etch(img, 6, 189, 65)
    # rows 2-5: the page, on a deeper sunken panel
    _sunk(img, 6, 68, 190, 152, DEEP)
    _button(img, 0, 176, False)
    _button(img, 18, 176, True)
    path = ASSETS / "textures" / "gui" / "village_hall.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    return [path]


# The Pokémon Trader's screen (ROADMAP 28.23): textures/gui/pokemon_trader.png, read by the client's PokemonTradeScreen.
# - the window (256x156 at 0,0): the same vanilla container panel, a sunken offers panel on the left (6..160, 16..150)
#   that holds up to four offer cards, a sunken party panel on the right (164..250, 28..80) for the party's two rows of
#   three buttons, and an etched line over the status text;
# - the buttons (18x18 at 0,176 / 18,176 / 36,176): plain, hovered, and pressed in (the Pokémon waiting for the second
#   click), the last drawn sunken like a slot, as vanilla shows a toggled button;
# - the offer cards (150x30 at 0,194 and 0,224): a raised card, plain and picked (lit, white-edged like a hovered
#   button). The card's own button and ball are drawn by the screen on top.
TW, TH = 256, 156


def _pressed(img, ox, oy):
    _rect(img, ox + 1, oy, ox + 17, oy + 18, BLACK)
    _rect(img, ox, oy + 1, ox + 18, oy + 17, BLACK)
    _sunk(img, ox + 1, oy + 1, ox + 17, oy + 17)


def _card(img, ox, oy, picked):
    fill = HOVER if picked else PANEL
    edge = WHITE if picked else BLACK
    _panel(img, ox, oy, ox + 150, oy + 30, fill=fill, edge=edge, bevel=1)


def pokemon_trader_gui():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    _panel(img, 0, 0, TW, TH)
    _sunk(img, 6, 16, 160, 150, DEEP)
    _sunk(img, 164, 28, 250, 80, DEEP)
    _etch(img, 165, 249, 84)
    _button(img, 0, 176, False)
    _button(img, 18, 176, True)
    _pressed(img, 36, 176)
    _card(img, 0, 194, False)
    _card(img, 0, 224, True)
    path = ASSETS / "textures" / "gui" / "pokemon_trader.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    return [path]


# The Trade page's marks (ROADMAP 33.4): textures/gui/trade_marks.png (64x16), drawn by the client's
# VillageHallMenuScreen over a button's icon. Five 8x8 marks in a row at y 0, each with its own dark outline so it
# reads on any item (vanilla's way for small overlays such as the bundle's and the map's markers):
# - the gold star (0,0): what the village is known for, in the icon's top-left corner (gold's ramp and outline);
# - the red mark (8,0): what it's short of, a "!" in redstone's reds, top-left too;
# - the arrows, in the bottom-right corner: up (16,0) in emerald's greens, down (24,0) in redstone's reds, and a grey
#   bar (32,0) for a steady price;
# - the open tab's bar (0,8, 16x2): emerald green, drawn under the tab's icon.
def trade_marks_gui():
    gold, em, red = P("gold"), P("emerald"), P("redstone")
    star = Sprite.from_ascii("""
        ...a....
        ..a3a...
        aaa3aaa.
        a33432b.
        .a232b..
        a32b23b.
        abb.bbb.
        ........
    """.strip(), {"a": gold.outline_light, "b": gold.outline_dark}, ramp=gold)
    short = Sprite.from_ascii("""
        .aaa....
        .a3a....
        .a3a....
        .a2b....
        .abb....
        .a3b....
        .bbb....
        ........
    """.strip(), {"a": red.outline_light, "b": red.outline_dark}, ramp=red)
    up = Sprite.from_ascii("""
        ........
        ...a....
        ..a4a...
        .a343b..
        a33332b.
        aab3bbb.
        ..a2b...
        ..bbb...
    """.strip(), {"a": em.outline_light, "b": em.outline_dark}, ramp=em)
    down = Sprite.from_ascii("""
        ........
        ..aaa...
        ..a3b...
        aaa3bbb.
        a33322b.
        .a232b..
        ..a2b...
        ...b....
    """.strip(), {"a": red.outline_light, "b": red.outline_dark}, ramp=red)
    steady = Sprite.from_ascii("""
        ........
        ........
        ........
        ........
        .aaaaaa.
        .acccdb.
        .abbbbb.
        ........
    """.strip(), {"a": "#555555", "b": "#373737", "c": "#c6c6c6", "d": "#8b8b8b"})
    bar = Sprite.from_ascii("""
        a333333333333332
        ab1111111111111b
    """.strip(), {"a": em.outline_light, "b": em.outline_dark}, ramp=em)
    img = Image.new("RGBA", (64, 16), (0, 0, 0, 0))
    for i, mark in enumerate((star, short, up, down, steady)):
        img.paste(Image.fromarray(mark.a.astype("uint8"), "RGBA"), (i * 8, 0))
    img.paste(Image.fromarray(bar.a.astype("uint8"), "RGBA"), (0, 8))
    path = ASSETS / "textures" / "gui" / "trade_marks.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    return [path]


DRAW = [village_hall_gui, pokemon_trader_gui, trade_marks_gui]

if __name__ == "__main__":
    run(DRAW)
