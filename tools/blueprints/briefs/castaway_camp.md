# Castaway Camp: the brief

Family id(s): `legend/castaway_camp` (one template, never renamed)   Variants: village (none), modded (none), looks
(none). One drawing, set down turned any of the four ways at random.

## What it is for
A **found Legend's camp** (ROADMAP 29.9, `legend/LegendSites`). It is never built by a builder and never offered at
the Table (`BlueprintLibrary.isWorldgenPiece`: everything under `legend/` is set down at once). When a player who owns
(or is a friend of the owner of) a qualifying Village Hall within 1,500 blocks stands inside a shipwreck (the structure
tag `#aliveworkplace:legend_sites/shipwreck`), the code finds **the nearest dry beach** to the wreck and sets this camp
down there in one go, with a Legend standing in it as a **castaway**: a named villager with no AI (they wait, frozen,
at their spot), tagged as a captive. The player is told: "On the beach near the wreck, a castaway has made camp: %s.
They look starved: bring them a cooked meal." Right-clicking the castaway empty-handed (or with anything that isn't a
cooked meal) gets "%s is too weak to walk: "Food... something cooked, please."". Right-clicking with a cooked meal (the
item tag `#aliveworkplace:cooked_meals`, from either hand) eats one, frees them ("A hot meal, at last! I owe you my
life..."), turns their AI back on and sends them **walking 16 blocks away from the player**; once no player is within
24 blocks, or after 600 ticks, they vanish and arrive at the village's hall next morning as a guest. The Merchant
Prince (29.17) is one Legend found this way. So the camp is the stage for one scene: a starving shipwreck survivor who
has made camp on the shore, found, fed and set walking. The story it must tell is the one in the player's text and
the ROADMAP: a castaway's camp on the beach by a wreck. How it tells it is free.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| (one) | `legend/castaway_camp` | The castaway's camp and the castaway's standing spot | None: set down by `LegendSites.place`, never built, upgraded or sold. `legendSites` in the config turns finding off |

## Must have
- **The castaway's spot: template (4, 1, 4)** today (`LegendSites.site("shipwreck")`, `new BlockPos(4, 1, 4)`;
  `legend_sites.py` `CASTAWAY_SPOT` mirrors it). The villager is spawned at the middle of that cell, feet at y = 1.
  - **Feet cell (4, 1, 4) and head cell (4, 2, 4) free**: no block with collision and no fluid. A villager inside a
    block suffocates, and a castaway killed at the camp is forgotten (`LegendSites.walk`): the scene is lost. No slab,
    carpet-on-slab, stair or seat in the spot either; the villager has no AI and won't step out of anything.
  - **A floor under it at (4, 0, 4)**: a block with a solid top (the ground layer), so the castaway stands, not hangs.
  - The designer may move the spot; the ship step then changes `LegendSites.site("shipwreck")`'s `BlockPos` and
    `CASTAWAY_SPOT` to the new cell. It must be inside the footprint.
- **The player can reach and click the castaway**: from a cell a player can stand on (inside or at the edge of the
  camp), within 3 blocks of the castaway, with a clear line of sight to their body (the client's click is a ray; the
  tests stand the player 1.5 blocks beside them). A castaway walled in or roofed so low a player can't get beside them
  can't be fed in survival.
- **A way out on foot**: freed, the castaway walks 16 blocks straight away from the player
  (`LegendSites.free` → `getNavigation().moveTo`). Leave them an open walk out of the camp from the spot (no fence,
  wall or ring of blocks closing the spot in on every side). No test checks it; without one they stand there until
  the 600-tick timeout and then vanish, which reads as broken.
- **No villager or other mob in the file**: the code spawns the castaway itself; a villager in the template would be
  a second, unrelated person. Any entity hung on a block (an item frame, a painting) must be saved with the block it
  hangs on (`TileX/Y/Z`), or vanilla logs "Block-attached entity at invalid position" and drops it (B68, the
  Traveller Camp's map: `LegendSitesGameTests.travellersMapHangsOnItsBarrelEveryWayRound`).
- **Job blocks, beds, storage, furniture: none required.** No code reads a chest, barrel, bed, campfire, bell or sign
  in this camp (the "exactly one barrel with a map on top" rule belongs to the Traveller Camp only). No
  `blueprint_furniture/castaway_camp.json` exists and no test needs one. The castaway is set to Nitwit, so job blocks
  don't hire them; a container's contents are never filled (no loot table is read).

## Fits
- **How it is set down** (`LegendSites.place`, `beach`): the ground column chosen is dry, solid, from sea level - 1 to
  sea level + 3, the first found in rings 8 to 64 blocks out from the wreck. The template is **turned a random way**
  (any of four rotations) and placed with **its middle** (`size / 2` on x and z) on that column. **Template y = 0 is the
  ground layer itself**: it replaces the world's top block, and y = 1 is the first block above the ground. Where the
  drawing leaves a y = 0 cell out (no block saved there, as `legend_sites._keep_world_ground` does), the world's ground
  stays; then every replaceable cell from y = 0 down to 3 below is filled with sand, so water or air under the
  footprint becomes beach. **Everything inside the footprint from y = 1 up is cleared first** (grass, a bush, the side
  of a dune). The site can be any beach in any biome, flat or sloping, with sea water at its edge.
- **Plot: at most 10 x 10 on the ground, at most 19 tall** (y 0 to 18), any rotation:
  - `QaLegendSitesGameTests.everyCampSentenceInPlainAmericanEnglish` sets the camp's middle at helper (25, 1, 6) in the
    30 x 20 x 30 `huge_area`: a footprint of 11 or more on either axis runs past the area's x = 29 edge in some
    rotation (into the next test, 5 blocks away), and the area is 20 tall with its floor at y = 1. The other camp tests
    set it at (15, 1, 15) beside a hall at (2, 2, 2) (`LegendSitesGameTests`, `QaStageSevenGameTests`,
    `QaThreatCobbleVerify1101GameTests`), which a 10 x 10 clears.
  - The showcase scene `legend_sites` (`JobScenes`) sets the three Legend camps side by side, middles 11 blocks apart
    (traveller at x -11, prisoner's 5 x 5 cage at 0, castaway at 11): a castaway camp wider than 17 would clear the cage
    it overlaps before the cage's bar is broken in step 2. 10 x 10 keeps it apart.
- Front and way in: no front. It is turned at random, so it must read and work from every side; the way to the
  castaway (above) must exist whichever way it lands.
- Neighbours: the wreck itself, somewhere 8 to 64 blocks off, and the sea. Nothing else is placed beside it.

## Rules tests check
- `LegendSitesGameTests.castawayFreedByACookedMeal`: the camp is set down (`place` returns the castaway; it returns
  null if the template doesn't load); a word and raw beef don't free them; a cooked cod does, and one is eaten.
- `QaStageSevenGameTests.theCastawayTakesAMealFromTheOffHand`, `theCastawayIsFedOnceFromTheMainHand`,
  `aCreativeMealFreesTheCastawayAndIsKept`, `aMealFromFarAwayFeedsNobody`: the camp set down at helper (15, 1, 15) of
  `huge_area`; the player is moved 1.5 blocks beside the castaway and clicks through real packets (12 blocks away does
  nothing).
- `QaThreatCobbleVerify1101GameTests.qaAWrongMainHandWinsOverTheOffHandsMealThenTheMealIsTaken`,
  `qaAStewInTheOffHandFreesTheCastawayOnce`: the same camp at (15, 1, 15).
- `QaLegendSitesGameTests.everyCampSentenceInPlainAmericanEnglish`: the camp's middle at (25, 1, 6) of `huge_area`
  (the 10 x 10 limit above); the hungry and thanks lines read in American English.
- These tests finish within 80 to 140 ticks, so they don't catch a castaway slowly suffocating in a block: the free
  feet and head cells are a rule of the code, not of a test.
- Every shipped template (all of `structure/`, `legend/` included):
  - `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`: a standing lantern, candle or torch on a block
    whose top holds its middle; no dirt path or farmland under a solid block; no half bed.
  - `B648GameTests.b648NoShippedGlassEndsInTheAir`, `QaBuilders1028GameTests.qaB648NoShippedGlassEndsInTheAirAsTheGameJoinsIt`,
    `QaWindowsAndWards1028BugTests.qaNoShippedGlassEndsInTheAirAsTheGameJoinsIt`: a glass pane or iron bar joined on
    only one side doesn't end in air.
  - `StationsSpecGameTests.noStructureOfOursUsesAGoneBlock`: the template loads and uses no removed block.
- By id only (the drawing doesn't matter): `MerchantPrinceGameTests` (found at a shipwreck), `QaLegendSitesGameTests`'
  other tests (qualifying, tags), `LegendSitesGameTests.noLookupsWithNobodyOnlineOrSwitchedOff`.
- The owner's rules (`check.py`, which walks every `.nbt` under `structure/`; `legend/castaway_camp` is not in
  `owner_rules_baseline.json`, so it must stay clean: posts at fence, wall and pane corners; a lantern on a table,
  slab, post or hanging, never on the floor; plates and crockery only by a stove, furnace, smoker or counter or on a
  table with a chair), STYLE.md's checks, and `rebuild.py --candidate` printing no CHECK line.
- Ship step (not a design rule): `generate.py` stops drawing `castaway_camp` from `legend_sites.py` (the `REBUILT`
  set), the spot in `LegendSites.site("shipwreck")` and `CASTAWAY_SPOT` follow the new drawing, and the
  `legend_sites` showcase scene still shows all three camps.

## Free
Everything not listed above: what the camp is made of and holds, its shape, plan, height and footprint within
10 x 10 x 19, which y = 0 cells it draws and which it leaves to the world's ground, where the spot stands in it,
shelter, fire, props, lighting and planting.
