# Prisoner Cage: the brief

Family id(s): `legend/prisoner_cage` (one structure, never renamed; `LegendSites.site("outpost")` loads it by this id)
Variants: village (none), modded (none), looks (none)

Not in this family: the Traveller Camp (`legend/traveller_camp`), the Castaway Camp (`legend/castaway_camp`) and the
Hermit Hut, the other found-Legend camps (52.5 rebuilds them separately), and the Raiders' Stockade's cage
(`story/raiders_stockade`, its own lever and iron door).

## What it is for
A camp set down at once by the game (not built by a builder, not on the Blueprint Table: `BlueprintLibrary.isWorldgenPiece`
hides every `legend/` id, and `VillageMaps` leaves it off the map). When a player who owns (or is a friend of the owner
of) a qualifying Village Hall within 1,500 blocks stands in a pillager outpost, and a Legend whose `found` way names the
`outpost` site (today the Beastmaster, `legends/beastmaster.json`) may come, `LegendSites.place` sets this cage down
beside the outpost's tower with the Legend standing in it, and the player reads "At the foot of the outpost's tower, a
prisoner rattles an iron cage: %s. Break the bars to free them." Clicking the prisoner only gets "%s grips the bars:
'Break them, quickly!'"; a cooked meal does nothing. **Breaking any iron bar of the cage frees them**: they thank the
player, walk off, and come to the village hall the next morning as a guest. Each outpost is used once.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| (only) | `legend/prisoner_cage` | Holds the found Legend until a bar is broken | None: placed whole by `StructureTemplate.placeInWorld` (no materials list, no build time, no builder). No upgrade exists or is asked for. |

## Must have
- Job block(s): none. No code looks for a workstation in it.
- Beds and residents: one resident, the prisoner, spawned by code (a NoAI nitwit with a Legend's name). No bed is
  counted or needed.
- **The prisoner's spot: template cell (2, 1, 2)**, fixed in code (`LegendSites.site("outpost")` →
  `new BlockPos(2, 1, 2)`), rotated with the template. The villager is spawned standing there, so:
  - (2, 1, 2) and (2, 2, 2) must be free to stand in (air or a non-suffocating block: a villager in a block suffocates);
  - (2, 0, 2) must be a solid block to stand on (the prisoner has no AI and won't step anywhere else).
  - If the design wants the prisoner elsewhere, the ship step changes that one `BlockPos` with it (and nothing else
    moves); the camp is centred on its footprint regardless (see Fits).
- **Iron bars: plain `minecraft:iron_bars`** (the code tests `state.is(Blocks.IRON_BARS)`; no other bar, pane, fence or
  modded bar frees the prisoner). At least one, inside the template's box (a bar outside the box frees nobody,
  `LegendRecord.Captive.holds`). **Every iron bar within x/z ±3 and y −1..+3 of the prisoner's cell must be less than 3
  blocks from the prisoner** (centre of the bar to the villager's feet at (2.5, 1, 2.5)): the tests take the first bar
  they meet in that box and assert it is under 3 blocks away, in whatever rotation the camp lands. In practice: the
  bars that close the prisoner in stand within 2 blocks of (2, 1, 2) sideways at foot level, or within 1 sideways at
  head height (a bar 2 out sideways and 1 up, (4, 2, 4), is 3.2 away and fails); no bar further out within 3 blocks.
- The cage must close the prisoner in so that breaking a bar is the way out the story tells (nothing in code checks
  enclosure: the prisoner has no AI until freed; free how).
- Storage: none. No barrel, chest or map frame is read in this camp (the "one barrel with a map on it" test is the
  Traveller Camp's only).
- Furniture kinds a job or test needs: none. No `blueprint_furniture/legend/prisoner_cage.json` exists today. If the
  rebuild's plan records furniture spots (`rebuild.py` writes them), every rule of `FurnishedRoomsGameTests` then
  applies to them (a bed's head to a wall, each piece on what it stands on, a light in a furnished room, nothing in a
  door's way).
- Anything else the code or a test reads: nothing. No bell, sign, lectern, door or lever is read.

## Fits
- How it lands (`LegendSites.place`): turned a random quarter (`Rotation.getRandom`), so everything must work in all
  four rotations; **centred** on a ground block (its middle column at `size / 2`); **template y = 0 is the ground layer
  itself** (unlike blueprints, it replaces the top ground block, not the air on it); every non-air block in the box above
  y = 0 is removed first (grass, bushes, anything there); under each column, up to 4 blocks of air or replaceable blocks
  below y = 0 are filled with dirt.
- Plot: **at most 9 x 9 (x by z)**. The camp's middle is set 5 blocks out from the edge of the outpost tower's
  bounding box (`LegendSites.beside`: half the tower's span + 5), on whichever side's ground is nearest the tower's foot,
  and the box is cleared before placing, so a footprint wider than 9 in either axis (rotation swaps them) would cut
  into the tower. Height: at most **18** (the tests place it on the floor of `huge_area`, which is 20 tall: y 1 to 19
  of the helper).
- Front and way in: none read. The camp is turned at random, so there is no front; no path or step is expected. A
  freed prisoner walks 16 blocks away from the player (`villager.getNavigation().moveTo`) and is removed after 600
  ticks or once no player is within 24 blocks, so whether they can walk out is not tested.
- Neighbours: the outpost (tower, pillagers) on one side; nothing of the village. In the tests a Traveller Camp (7 x 7)
  is set down with its middle 9 blocks west and 10 south of the cage's middle, and the two must not overlap
  (`QaLegendSitesGameTests.onlyTheCagesOwnBarsFreeThePrisoner`): 9 x 9 keeps clear.

## Rules tests check
- `LegendSitesGameTests.prisonerFreedByBreakingTheBarsNotByAMeal` (`huge_area`, camp middle at helper (15, 1, 15),
  `RandomSource.create(29)`): the camp is set down; a meal and an empty-hand click don't free; the first iron bar in
  the box ±3 sideways, −1..+3 up round the prisoner exists ("the cage has iron bars") and is **< 3 blocks** from the
  prisoner ("the prisoner is in the cage"); a survival player breaking it leaves air, frees the prisoner, and the
  prisoner has AI again.
- `QaLegendSitesGameTests.onlyTheCagesOwnBarsFreeThePrisoner` (`huge_area`, middle (15, 1, 15), seed 299, Traveller
  Camp at (6, 1, 25)): the two camps' boxes don't hold each other's Legend; a bar placed just outside the cage's box
  (max x + 1, min y + 1, min z) and a bar in the Traveller Camp free nobody; the cage's box holds an iron bar; the
  first non-air, non-bar block of the cage above y = 0 (other than under the prisoner) is broken and frees nobody;
  the cage's own bar frees the prisoner with the "thanks.outpost" sentence. So any non-bar block may be in the cage,
  and the cell right outside the box's max-x side at y = 1 is overwritten by the test.
- `QaLegendSitesGameTests.everyCampSentenceInPlainAmericanEnglish`: the cage is found through `LegendSites.offer` with
  a 3 x 5 x 3 anchor at helper (6, 2, 22), so it lands 6 blocks east (or another side) of it on the flat floor; the
  found sentence is said. (Needs only that the template loads and places.)
- Every shipped structure (this one is read too):
  - `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`: standing lanterns, candles and torches on a block
    whose top holds their middle; no dirt path or farmland under a solid block; every bed with its other half.
  - `B648GameTests.b648NoShippedGlassEndsInTheAir`: **no iron bar (or glass pane) joined on exactly one side with air on
    the opposite side** (a bar run must end against a block, not stick out into the air).
  - `BlueprintListCacheGameTests` and `BlueprintsStayGameTests` count every `.nbt` (≥ 300): the file stays.
- The owner's rules (check.py) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line (it runs on save;
  `legend/prisoner_cage` comes off `owner_rules_baseline.json` once rebuilt), and `holes.py` passes (a cage's open bar
  walls may need an ALLOW line, as the School's cloisters got).
- Ship step, not the designer's: the id has a folder (`legend/`), so the live save is
  `rebuild.py prisoner_cage legend/prisoner_cage`, and `generate.py` must stop redrawing the old cage (its `legend`
  loop, as `REBUILT` does for others).

## Free
Everything not listed above: what the cage is made of and how it is shaped, roofed, floored and lit, what else stands
in or round it inside the 9 x 9 x 18 box, whether it has a door, and its whole look.
