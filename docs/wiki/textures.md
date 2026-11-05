# Textures and item icons

How the mod's pictures are made: every block, outfit, screen and item icon is drawn by a recipe in
`tools/textures/art/`, in vanilla's own style, and the thirteen item icons of 0.138.0 are the ones the owner picked from
versions drawn for him. Part of the 0.138.0 finish (Milestone 21).

Roadmap items: 21.1, 21.1b

## What a player sees

The mod's workstations, villager outfits and items look like they belong in vanilla Minecraft: a book is drawn like
vanilla's books, a map on vanilla's map paper, a tool held at a tool's angle. Every profession of ours wears its own
outfit, as a villager and as a zombie villager.

The item icons are the owner's picks: the Blueprint clipped to an oak drawing board with a house sketched on it, the
Blank Blueprint as two blue sheets, the Shape Planner as a brass drafting compass, the Village Ledger lying open, the
Delivery Note's list on a clipboard, the Travel Ticket with a sailing boat printed on it, the Price Tag with a dollar
sign, the Rally Banner as an upright standard with crossed swords, the Settler's Wagon from the side. The Patrol Map and
the Field Marker are drawn on vanilla's exact map outline (a dashed patrol loop; a field plan inside four red pegs),
the Quarry Marker is a red-and-white swallowtail flag on a stake and the Scan Tool a blueprint-blue drafting pencil
sharpened to a point.

## How it works

`python3 tools/textures/generate.py` draws every texture from its recipe, or only the recipes named on its command line,
and writes the PNGs to `src/main/resources/assets/aliveworkplace/textures/` (plus the mod's `icon.png`, the Blueprint
icon at 8x). Nothing is copied from vanilla: its textures are only measured for palettes and outlines. The recipes are
in one module per set: `builders.py` (the Builder's Bench and outfit, the style reference), `workstations_*.py`,
`villagers_*.py` (each outfit and its zombie copy), `items.py` (the item icons), `gui.py` and later sets added by other
features. They use the pixel-art skill's library, copied into `tools/textures/pxlib/`.

`tools/textures/pxlib/lint.py` checks textures against measured vanilla conventions (outlines, saturation, baked-in
light, tile seams); `lint.py audit <textures dir>` ranks every texture. When the rebuild was shown (21.1, 2026-10-02),
181 of 182 textures passed. The one warning, `block/mailbox_flag.png` ("tile edges show as lines when repeated"), is
expected: that texture paints only the mailbox's 1-pixel-wide flag and is never tiled. Today the mod has 339 textures
and 324 pass clean (an audit run on 2026-11-04); the rest are warnings from later features.

Each icon is drawn as a member of its vanilla family, from the owner's notes on 21.1b: boards held level (Blueprint,
Delivery Note); sheets on the diagonal like the map (Blank Blueprint, Patrol Map, Field Marker); the stick convention
of tools, handle at the bottom left and head at the top right (Quarry Marker, Scan Tool); a banner held upright (Rally
Banner). Colors are sampled from vanilla's neighbors: the stick, paper, gold, iron and oak, and the blueprint blue of the
Builder's Bench top. The Scan Tool and Quarry Marker use the `handheld` item model, held like a tool; the others use
`generated`.

The picker for 21.1b: `tools/textures/picks/` draws three new versions of each item beside its old icon
(`sheets.py`, `papers.py`, `tools.py`, then `round2.py` for the second round), and `page.py` builds one page from them
(`build/texture-picks/index.html`) where the owner chose one per item and left a note. His picks were moved into
`tools/textures/art/items.py`, which is now the icons' only source.

## Switches

none

## Saved data

none

## Items, blocks, jobs, commands

The 13 icons the owner picked in 21.1b:

- `aliveworkplace:blueprint`, `aliveworkplace:blank_blueprint`, `aliveworkplace:shape_planner`,
  `aliveworkplace:village_ledger`, `aliveworkplace:delivery_note`, `aliveworkplace:travel_ticket`,
  `aliveworkplace:price_tag`, `aliveworkplace:rally_banner`, `aliveworkplace:settlers_wagon` (round 1, 2026-10-01);
- `aliveworkplace:patrol_map`, `aliveworkplace:quarry_marker`, `aliveworkplace:scan_tool`,
  `aliveworkplace:field_marker` (round 2, 2026-10-02).

Files: `textures/item/<id>.png`, `textures/block/`, `textures/entity/villager/profession/<job>.png` and
`textures/entity/zombie_villager/profession/<job>.png` (48 jobs each today), `textures/gui/`.

## Decisions

- 21.1: every texture is redrawn with the pixel-art skill and checked with its lint; a warning that is expected gets
  its reason in the ROADMAP Notes (only `mailbox_flag`, 2026-10-02).
- 21.1b (owner, 2026-10-01): every item that isn't a block or a villager skin got versions to choose from; he picked
  on a picker page, one item at a time, with a note for any item to redraw.
- 21.1b (owner, 2026-10-01): "follow the exact outlines minecraft has in place for certain items": the Patrol Map and
  Field Marker use vanilla's exact map outline with different content on it, and the Scan Tool is "more obviously a
  pencil with the tip coming to a point". Round 2 redrew those four, plus the Quarry Marker's flag; he picked all four
  round 2 versions with no notes (2026-10-02).
- 21.1b: the Rally Banner and the Field Marker moved to the flat `generated` item model with their new icons.
- 21.1: no changelog line of its own; players see nothing new beyond the existing "A new look" entry.

## Known limits

- The lint is a style check run by hand; the build doesn't run it, so later textures can carry warnings (15 do today,
  mostly baked-in light on flowers and seams on built blocks).
- The picker page reads its versions from `build/`, so it has to be drawn again (the `picks` scripts) to be seen;
  the versions not picked are not kept as textures.
- Item icons made after 21.1b (the City Plan, the Guide Book and the rest of `items.py`) were not on the picker page.

## Proof

- GameTests: `OutfitGameTests.everyProfessionOfOursHasAVillagerAndAZombieVillagerOutfit` (every profession of ours
  ships a 64x64 villager and zombie villager outfit with something drawn, and any hat it names is one vanilla knows).
- Showcase scenes: `items` (the icons in a chest, in the hotbar and five of them in hand), `outfits` (every job as a
  villager and as a zombie villager, five to a shot, named), `staff` (every workstation with its worker).
