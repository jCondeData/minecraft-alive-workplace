# Traveler's Camp: the brief

Family id(s): `legend/traveller_camp` (one drawing, never renamed; the id keeps its British spelling, player text says
"traveler")   Variants: none (no village, modded or style copies: `legend/` is left out of the library,
`BlueprintLibrary.isWorldgenPiece`, and off the Village Map, `VillageMaps.kindOf`), looks (none)

## What it is for
A Legend found in the world (ROADMAP 29.9, `legend/LegendSites`). Every 5 seconds the mod asks whether a player stands
in a ruined portal (the structure tag `#aliveworkplace:legend_sites/ruined_portal`). If that player owns, or is a
friend of the owner of, a qualifying Village Hall within 1,500 blocks with a Legend's slot free, this camp is **set down
at once** beside the portal (never built by a builder, never upgraded) with the Legend standing in it, waiting
(a villager with no AI). The player reads "Beside the ruined portal, a traveler has made camp: <name>. Talk to them.",
walks up and right-clicks the traveler; that one word frees them. They thank the player, walk off (the code sends them
16 blocks directly away from the player), vanish once out of sight (24 blocks) or after 30 seconds, and arrive at the
hall the next morning as a guest. ROADMAP 29.9 describes the camp as a wanderer's camp ("a bedroll, a campfire, a map
on a barrel"); of those, only the map on the barrel is read by code or a test (below). The camp stays in the world
afterwards as scenery; nothing reads it again.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `legend/traveller_camp` | The camp the found traveler waits in | None: placed by `LegendSites.place`, not sold, not in the Blueprint Table, no upgrade |

## Must have (each level)
- Job block(s): **none.** The traveler is a Nitwit with no AI while waiting; no workstation is read.
- **The Legend's standing spot: template cell (4, 1, 5)**, hard-coded in `LegendSites.Site` (`new BlockPos(4, 1, 5)`,
  `LegendSites.java` line 96) and in `legend_sites.py`'s `TRAVELLER_SPOT`. The villager is spawned there.
  - The cell below it, (4, 0, 5), is solid (`BlockState.isSolid()`: not carpet, a snow layer or anything thin); or
    left out of the template so the world's ground (or the dirt the code fills in) is there.
    `LegendSitesGameTests.campOnlyForAQualifyingPlayerAndOnce` and `travellerFreedByTalkAndGuestNextMorning` assert
    "stands on the camp's ground".
  - (4, 1, 5) and (4, 2, 5) are free of anything with collision: the villager is about 2 tall and takes suffocation
    damage even with no AI.
  - If the design needs the spot elsewhere, say so in the design notes: the ship step must then change both
    `LegendSites.java` and `TRAVELLER_SPOT` in the same commit. Simplest is to keep (4, 1, 5).
- **The traveler can be clicked by a player on foot**: a survival player must be able to walk to within arm's reach
  (about 3 blocks) of the spot and see the villager. Clicking is the only way to free them (`LegendSites.use`, through
  vanilla's entity click). Tests call `LegendSites.use` directly, so nothing tests the reach; it is still the whole
  point of the camp.
- **A way out for the freed traveler**: once freed, the code walks them 16 blocks directly away from the player who
  freed them (`villager.getNavigation().moveTo`). Nothing tests it (they vanish after 30 seconds regardless), but a
  traveler boxed in looks stuck.
- Beds and residents: **no bed is required**; the waiting traveler has no AI and never sleeps, and no code counts the
  camp's beds. One Legend at a time, the one at the spot.
- Storage: **exactly one barrel in the whole camp**, at template y = 1 (its top at y = 2). No code reads its contents;
  the tests find it by being the camp's only barrel. Any other container is free, but no second barrel.
- **The map on the barrel** (the one entity in the template):
  - a `minecraft:item_frame` lying on top of the barrel (facing up, `Facing: 1`), at the cell directly above it,
    holding `minecraft:map` (the empty map, count 1);
  - saved with `TileX`, `TileY`, `TileZ` (the frame's own cell, template-relative; `StructureTemplateMixin` moves them
    to the world when placed), and `TileY` must be **2**;
  - it must be the template's **only entity** (no armor stands, paintings, other frames or mobs).
  - `rebuild.py` carries blocks only: add the frame to the saved `.nbt` afterwards, as
    `tools/blueprints/js/guildhall/frames.py` does for the Guildhall's frames (that script writes wall frames; this one
    lies on top of a block: `Facing` 1, `pos` y + 0.03125).
- Furniture kinds a job or test needs: none. `blueprint_furniture/legend/traveller_camp.json` is not read when the camp
  is placed; if the recipe records furniture spots, each spot's blocks must be exactly the template's there
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate` reads every shipped spots file, this one included).

## Fits
- Plot: **at most 9 x 9** in both directions (the camp is turned at random, so width and depth both count), height at
  most 28. y = 0 is the ground layer and sits on the ground block the code picks.
  - 9 is a hard limit three ways: the camp's middle is set down 5 blocks out from the portal's edge
    (`LegendSites.beside`: half the portal's span + 5), and `place` **removes every block in the camp's box above
    y = 0**, so a camp more than 4 blocks from its middle to its edge eats into the ruined portal; the frame test puts
    four camps in the corners of a 30 x 30 area at x/z 2 and 20 plus one centred at 15, which overlap at 10 wide; and
    `QaLegendSitesGameTests.onlyTheCagesOwnBarsFreeThePrisoner` centres one at (6, 25) in that area.
  - The middle is `size / 2` (integer) along x and z: with an odd width it is the centre cell.
- **The y = 0 layer is the world's ground.** Before placing, the code clears the box above y = 0 and fills up to 4
  blocks of dirt under it where the ground is air or replaceable; then the template is placed. So a y = 0 cell may be
  drawn (it replaces the ground there) or left out of the template (the world's ground stays), but **never saved as
  air**: an air cell at y = 0 digs a hole in the ground (the old generator deleted them, `_keep_world_ground`).
  Everything above y = 0 is the camp; anything not drawn above y = 0 is cleared to air.
- Placed with block flags 2 (no neighbour updates): the drawing must stand as saved (no block that pops off or needs an
  update to look right; the frame must still be there 100+ ticks later).
- Front and way in: **no fixed front.** `LegendSites.place` picks one of the four rotations at random, and the camp
  is seen from every side. The player arrives from the portal's side or any other.
- Terrain: beside a ruined portal, in any biome it spawns in, on uneven ground (not on water: `beside` skips columns
  with fluid at or above the ground).
- Neighbours: a ruined portal on one side (about 1 block of gap at 9 wide); nothing else is placed beside it.

## Rules tests check
- `LegendSitesGameTests.travellersMapHangsOnItsBarrelEveryWayRound` (batch `legendSitesCampFrame`, `huge_area`):
  - the template `aliveworkplace:legend/traveller_camp` loads;
  - its saved `entities` list has **exactly 1** entry, whose `nbt` has `TileX` and `TileY == 2`;
  - placed four times (each rotation, origins at test cells (2, 2, 2), (20, 2, 2), (2, 2, 20), (20, 2, 20)) and once
    through `LegendSites.place` at (15, 1, 15): no "Block-attached entity at invalid position" in the log;
  - after 120 ticks there are **exactly 5** item frames in the test area (one per camp);
  - in each of the four rotated camps' boxes there is **exactly one barrel**, and a frame at the cell above it, facing
    UP, holding a `minecraft:map`, that `survives()`.
- `QaLegendCampGameTests.theTravellersCampHasItsMapOnTheBarrel` (batch `qaLegendCampMap`): the camp placed through
  `LegendSites.place` at (15, 1, 15); after 120 ticks some frame in the area still holds a map.
- `LegendSitesGameTests.campOnlyForAQualifyingPlayerAndOnce` and `travellerFreedByTalkAndGuestNextMorning`: the camp
  is set down (`place` returns the villager; it returns null only when the template is missing), and the block under the
  traveler is solid.
- `QaLegendSitesGameTests.onlyTheCagesOwnBarsFreeThePrisoner`: a traveler's camp centred at (6, 1, 25) and a prisoner's
  cage (5 x 5) centred at (15, 1, 15) **don't overlap** (neither camp's box holds the other's Legend). It also sets iron
  bars at the camp box's minimum corner, one block up, and breaks them; that must free nobody (it doesn't depend on
  the drawing).
- `QaLegendSitesGameTests.aFoundLegendWhoseSlotFillsMeanwhileDoesNotCome` and `aLegendWhoWalkedOffArrivesTheNextMorning`:
  a camp set down at (15, 1, 15) with its traveler clickable through `LegendSites.use`.
- The showcase scene `legend_sites` (`JobScenes`) sets the three camps down 11 blocks apart (x = -11, 0, 11), so the
  camp must stay within 9 x 9 there too.
- The owner's rules (`check.py`; this camp is not in `owner_rules_baseline.json`, so any problem is a CHECK line) and
  STYLE.md's checks.

## Free
Everything else: what the camp is made of, its shape, whether it has a shelter, a fire, a bedroll, seats or any other
container but a barrel, lighting, and its ground pattern, within the 9 x 9 plot.
