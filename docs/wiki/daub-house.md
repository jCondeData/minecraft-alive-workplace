# The Daub House

Milestone 49, item 49.2 (the owner's pick of 36.23's two new Level I houses, 2026-11-16: "I much prefer the Daub
House"), with his three changes (36.23a-c): the loft railing joined at its corners, the short lamp on a side table, the
plates in the kitchen. A new home for a village's first families, the first building made with the rebuild kit
(`tools/blueprints/REBUILD.md`).
Roadmap items: 36.23, 36.23a, 36.23b, 36.23c, 49.2, B1367

## 1. What a player sees

- **Daub House** in the Blueprint Table (13 x 14 x 13, one tier, a home): mud-brick daub panels in a dark oak frame on
  a cobble plinth, a steep brick roof with a dormer on the west slope, a little tiled hood and lantern over the front
  door, a chimney up the back gable, and a low stone kitchen under its own roof on the east side. Like most builds it
  starts locked; a builder's level-up unlocks it (or a Blueprint of it used on the Table).
- Inside, the **hall**: a table with chairs by the hearth, a cupboard, a clock, a banner, the one standing lamp on its
  side table by the door. The **kitchen** behind an opening: counters with a furnace, plates on the counters and the
  shelf above them (none by the front door). Up a straight stair, the **loft**: four beds with nightstands and candles,
  a desk, two hanging lanterns, and a railing along the stairwell that joins at its corner.
- Every piece is a furniture spot, so a builder with the furniture mods (Handcrafted, CobbleFurnies...) builds the
  mods' chairs, table, beds, counters, lamps and crockery in their places (37.2); with the building mods its modded
  copy dresses the daub and the floors (37.12).
- **In villages:** vanilla villages of all five styles now and then grow a Daub House among their houses (weight 2), in
  their own materials, with a jobless villager moving in:
  - plains: as drawn (mud-brick daub, dark oak, brick roof);
  - desert: sandstone on a sandstone plinth, jungle wood, a red sandstone roof;
  - savanna: white terracotta in an acacia frame, an acacia roof;
  - snowy: calcite between stripped spruce, a dark oak roof on stone bricks;
  - taiga: spruce boards in a spruce frame, a dark oak roof on mossy cobble.
- **The Steward** plans one: a Hamlet three or more beds short asks for a Daub House (four beds) in a Homes zone, before
  a Stone House. Two short still asks for a Stone House; a Village three short still asks for a Terrace.
- On the Village Map it is a home.

## 2. How it works

- Drawn in JavaScript with the rebuild kit: `tools/blueprints/js/house_t1_b/` (`plan.mjs`: story, palette, masses,
  rooms, every furniture footprint; `recipe.mjs`: the passes) on the shared parts library `tools/blueprints/js/parts/`,
  run with the minecraft-builder skill, then saved by `tools/blueprints/rebuild.py house_t1_b house_t1_b` (check.py's
  checks with the owner's rules, `finish()`, the furniture spots in `blueprint_furniture/house_t1_b.json`).
- `StarterBlueprints.DAUB_HOUSE` (in `ONE_TIER`: the Table and the creative tab), `VillageMaps.kindOf` (a home).
- Village pieces: `village.py`'s `daub_house_village(style)` (also run by `generate.py`, or alone:
  `python3 tools/blueprints/village.py`) loads the blueprint, swaps its materials per style (`DAUB_STYLES`), leaves the
  front row as the ground it lands on (its two flower beds left out) with the street's jigsaw before the door (4, 1, 0), and a worker-pool jigsaw on the
  hall floor (4, 0, 5) for a jobless villager. `world/VillageHouses.HOMES` adds them to the five house pools (and Repurposed
  Structures' villages) when the server starts; they are not staffed houses (no job block).
- Modded copy: `structure/modded/house_t1_b.nbt` (`modded_library.py house_t1_b`).
- Steward rule `steward_rules/homes_house_t1_b.json` (priority 90: beds short 3+, below Village rank, no upgrade adds
  beds). Built advancement `advancement/built/house_t1_b.json` (`tools/questbook/generate.py`).

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | `moddedLibrary` and the furniture switches apply as for every home. |

## 4. Saved data

Nothing new. A new blueprint id (`aliveworkplace:house_t1_b`) and new village template ids
(`aliveworkplace:village/<style>_house_t1_b`); old saves load unchanged.

## 5. Items, blocks, jobs, commands

- Blueprint: Daub House (`aliveworkplace:house_t1_b`).
- Text: `blueprint.aliveworkplace.house_t1_b` ("Daub House").

## 6. Decisions

- The id stays `house_t1_b` (36.23's), the name the player reads is "Daub House". The Hearthstead (36.23 A) stays unused
  on `wip/house-t1`.
- Not in the starting set: `BlueprintUnlocksGameTests` keeps the starting set at 15 to 20 builds and it has 20, so the
  Daub House unlocks like most builds (a builder's level-up, a Blueprint). Putting it in the starting set is the owner's call.
- Not in the Steward's renewal list for old homes: that list takes the first house with enough beds, and placing the
  Daub House before the Terrace would change which house replaces an old one.
- One villager moves into the village piece (as into every village house); the other beds are for the village to fill.

## 7. Known limits

- The village pieces are the blueprint in other materials, not separate designs; the biome looks of 49.10 may redraw
  them.
- The showcase scene `rebuild_house_t1_b` is filmed on GitHub, not in this session.

## 8. Proof

`DaubHouseGameTests`: the blueprint's size, door, four beds and hearth; every bed and lamp a furniture spot, the joined
railing corner, the standing lamp on a side table, the plates only in the kitchen; in the Blueprint Table, named, a home
on the map, its modded copy and built advancement; in every village style's house pool with the street's jigsaw, a
jobless villager's spawn, four beds and at most one job block; the Steward's plan for a Hamlet three beds short (not two,
not a Village); a builder building it to DONE with the railing joined. Scene `rebuild_house_t1_b`.
