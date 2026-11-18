# The Shift Bell: the brief

Family id(s): `story/shift_bell` (the template `aliveworkplace:story/shift_bell`, `ReformStories.BELL`; file
`src/main/resources/data/aliveworkplace/structure/story/shift_bell.nbt`; never renamed; one level, no `_2`)
Variants: village (none), modded (none), looks (none). It is never styled, never on the Blueprint Table and never built
by a builder (`BlueprintLibrary.isWorldgenPiece`: everything under `story/` is set down by a story arc).

## What it is for
The lasting reward of the Long Shifts reform told as a story (ROADMAP 31.22, `story/ReformStories.java`,
`arcs/shift_bell.json`, `edicts/long_shifts.json`). A smith (toolsmith, else weaponsmith or armorer) asks the player
for "a bell to ring the work in and out"; the village brings 24 clocks, 128 gold ingots and 300 bread over three days;
at the arc's ending the template is set down **at once, whole** (no builder, no materials) on natural ground beside the
Village Hall. From then on the code rings its bell twice a day, at the start (day time 2000) and the end (9000) of the
villagers' work day, while someone is near the hall. The chronicle says "The Shift Bell was raised beside the hall of
<village>"; the reform's line calls it "a bell in the yard [that] calls the shifts". It is a civic yard piece the
village sees every day next to its hall. It has no job, no residents, no storage.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `story/shift_bell` | One bell the code rings at day time 2000 and 9000 (`ReformStories.tick`, `RING_START`, `RING_END`); its position is saved per hall (`Entry.bell` in `aliveworkplace_reform_stories`) | No unlock and no cost of its own: placed by `ReformStories.placeBell` when the `shift_bell` reform ending runs (the arc's three `bring` steps are the edict's, not the template's). Not on the village map (`VillageMaps.kindOf` skips `story/`), no Table entry, no upgrade. |

## Must have
- **The bell: exactly at template cell (1, 3, 1).** `placeBell` does not search the template for it: it returns
  `origin.offset(1, 3, 1)` as the bell and saves that position. That cell must hold `minecraft:bell` (any
  `attachment` and `facing`; `BellBlock.attemptToRing` only needs the bell and its block entity there). If the bell is
  anywhere else the village saves a position with no bell, never rings, and the tests below fail. No other bell is
  read; more bells would never ring (and each is a vanilla villager meeting point), so one is all the code needs.
- **The bell must stay put.** The template is placed with `placeInWorld(..., flag 2)`, so nothing is checked for
  support when it lands; the first neighbour update afterwards pops any block that can't survive where it is. The
  bell must have the support its `attachment` needs (ceiling: a block above; floor: a block below; single/double wall:
  the block(s) beside it). Every other support-needing block (lanterns, torches, chains, buttons, carpets, plants)
  likewise needs what it stands on or hangs from inside the template.
- Job block(s): none. No code looks for a job block here.
- Beds and residents: none. No code counts beds.
- Storage: none. No code reads a container here.
- Furniture kinds a job or test needs: none. There is no `blueprint_furniture/story/shift_bell.json` and none is
  needed. (If the recipe records furniture spots anyway, `FurnishedRoomsGameTests`' every-template checks then apply
  to them; `story/` is exempt only from the light rule, `FurnishedRoomsGameTests.everyFurnishedRoomHasALight` /
  `furniture.py`'s `UNLIT`.)
- Anything else the code or a test reads: nothing. No sign, water, fire, light level, door or path.

## Fits
- **Plot: at most 3 wide (x) x 6 tall (y) x 3 deep (z). This is a hard limit set by code, not taste.**
  `ReformStories.fits` accepts a spot only when a 3 x 3 of natural ground (dirt, grass, sand, gravel or stone:
  `ReformStories.natural`) lies at the origin's y - 1 and the 3 x 3 x 6 above it is all air; then it places the whole
  template there. Anything outside that 3 x 3 x 6 box would be placed over blocks nobody checked (the hall, a
  house, a road). A bigger plot needs `fits` and `placeBell` changed and tested in the same commit; the designer
  treats 3 x 6 x 3 as fixed.
- y = 0 is the first air layer on top of the ground (`origin` is the `MOTION_BLOCKING_NO_LEAVES` heightmap). The
  template has **nothing below y = 0**: it must not carry its own ground, and the natural ground under it stays.
  Blocks at y = 0 sit on that ground.
- **No front, every side shows.** The template is placed with default `StructurePlaceSettings`: never rotated or
  mirrored. Its z = 0 side always faces north in the world, while the hall can be on any side of it (spots are tried
  in rings 3 to 12 blocks out round the hall, all directions). It must read well from all four sides and from above.
  (The showcase scene `reform`, step `03_bell`, films it from 3.5 east, 5.5 south and just above the bell, looking at
  the cell under it: the bell should be visible from that south-east view.)
- Way in: none needed. No villager walks to it; it is rung by code. A player may want to walk up and ring it by hand
  (vanilla bell), but no code checks that.
- Neighbours: the Village Hall 3 to 12 blocks away, whatever else stands round it. No village plan reserves room for
  it; it goes on the nearest free natural 3 x 3. With no such spot, no bell is placed and the story still ends
  (`docs/wiki/edicts.md`).
- No build time or material budget: it is never built step by step and has no material list.
- Old saves: villages that already have a Shift Bell keep the one they have (the saved position points at the old
  bell, which still sits at the old template's (1, 3, 1)). Only bells raised after the change use the new drawing.

## Rules tests check
- `ReformStoryGameTests.theShiftBellFromTheToolsmithsAskToTheBellRinging` (batch `reformStoryShiftBell`, `huge_area`,
  hall at (6, 2, 6), grass at x 9..13, z 3..9): after the story ends, `ReformStories.bell` is a **bell**; the block
  **4 below the bell** is natural ground (so the bell is exactly at y = 3 over a ground block); it does not ring at
  5000, rings once at 2000 and once at 9000; the chronicle line is written.
- `QaReformStoryGameTests.theShiftBellStandsOnlyOnNaturalGround` (batch `qaReformStoryBellGround`): on planks only,
  `placeBell` returns null and breaks nothing; with one 3 x 3 grass patch 8 out, the bell is a **bell** and stands
  **over that patch** (its x and z inside the 3 x 3), and exactly the 9 planks under the patch are gone from the count
  (the planks round it are not built over).
- `QaReformStoryGameTests` (storyArcs off): no bell is raised. `QaReformSavesGameTests`, `QaFarReform1114dGameTests`,
  `QaReformNearHall1114c2GameTests`, `QaFarOffline1114eGameTests`: the bell's saved position survives a restart and
  rings only when someone is near; these set their own bell block and don't read the template, but they rely on the
  saved position being a bell.
- `StationsSpecGameTests.noStructureOfOursUsesAGoneBlock`: the template loads and uses none of the mod's removed
  blocks (`StationsSpecGameTests.gone()`).
- Showcase `reform` (`JobScenes`, steps `03_bell` "the Shift Bell stands beside the hall" and `04_ringing` "the bell
  rings the work day in"): the saved bell position holds a bell and rings.
- By id only: the id stays `story/shift_bell` (`ReformStories.BELL`); the names stay "The Shift Bell"
  (`reform.aliveworkplace.shift_bell`, `arc.aliveworkplace.shift_bell`).
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, and `holes.py`
  passes. (`story/` is exempt from the "every furnished room has a light" rule.)
- Ship step (not the designer's): the live save is `python3 tools/blueprints/rebuild.py shift_bell story/shift_bell`;
  `generate.py` line `shift_bell().save(... "story", "shift_bell")` is unconditional and must stop saving the old
  drawing (it is not in a `REBUILT` loop); the recipe folder needs a `furniture.json` (`{"spots": []}` if none);
  `docs/wiki/edicts.md`'s reform table describes the old look ("a bell on two posts") and changes to match.

## Free
Everything not listed above: what holds the bell up and how, any roof or cover, the base, materials, colors, shapes,
height within 6, lighting and style, as long as the bell is at (1, 3, 1), supported, inside 3 x 6 x 3, and nothing goes
below y = 0.
