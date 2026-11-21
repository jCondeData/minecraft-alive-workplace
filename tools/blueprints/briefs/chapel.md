# Chapel: the brief

Family id(s): `chapel` (never renamed; one level, no `chapel_2`; "Chapel" in `en_us.json`,
`blueprint.aliveworkplace.chapel` and `service.aliveworkplace.chapel`, and quoted word for word by the wedding tests)
Variants: village (none: no `village/<style>_chapel` exists; a Steward zone's style is applied to the drawing
automatically by `BlueprintStyles`, and `ServiceGameTests.aStoneworkChapelCounts` relies on a styled Chapel still being
a Chapel), modded (none: `modded_library.py` lists it in `EXTRA_DECORATIONS` and never dresses it), looks (none).
Not this family: the Mortuary look (`village_looks.py`, "a narrow chapel"), the Great Cathedral's side chapels
(`wonders.py`) and the test Wonder's `aliveworkplace_test:wonder_chapel`. Nothing here changes them.

## What it is for
The village's wedding venue and one of its services. It is a **decoration** (`StarterBlueprints.DECORATIONS`), not a
workplace: nobody works or lives in it.
- **Weddings.** While a finished Chapel stands in a Village Hall's claimed area (zones past the radius count), every
  wedding in that village is held at it instead of at the bell (`Couples.venue`, unless a Great Cathedral is finished):
  hearts, three fireworks launched from the tops of columns within 3 blocks of the venue, the bell sound and a chat
  line. A courting villager may ask the player for a **chapel wedding** (`quests/personal/chapel_wedding.json`): the
  couple's wedding waits up to 5 days for a Chapel, and the moment one is finished they marry in it with a feast,
  the player is paid 6 emeralds and the chronicle reads "... were married in the new Chapel ..."
  (`RequestErrands.CHAPEL_WAIT_DAYS`).
- **The Seer** (a Rare Legend, `legends/seer.json`) may come as a guest at a full-moon midnight to the village's finished
  Chapel (1 in 2), appearing on a free standing spot near its middle (`LegendGuests.chapel`, `standingSpot`). Settled,
  by day the Seer walks back to the Chapel whenever more than 6 blocks from its middle (`Seer.KEEP_NEAR`), and blesses
  weddings held at the Chapel while within 32 blocks of it (`Seer.CHAPEL_REACH`).
- **Service:** homes within **48 blocks of its origin** get the **chapel** service (`services/chapel.json`), which the
  Artisan, Burgher and Noble class needs read (`classes/*.json`).
- **Beauty:** 4 points (`Decorations.POINTS`), 1 % wellbeing each, up to the 10 % cap.
- **Wonder condition:** the Great Cathedral needs **3 finished Chapels** in the village
  (`wonders/great_cathedral.json`).
- **How a village gets one:** a *rare* build (`blueprint_unlocks/rare.json`): a ??? at the Blueprint Table until a found
  Chapel blueprint (chest loot of the bandit camp, war camp, pirate ship, coven hut, piglin hoard and pharaoh's tomb)
  is used on it or the village otherwise unlocks it (`BlueprintUnlocksGameTests`). The Steward builds one in a
  **civic** zone when at least one couple is courting and none is built (`steward_rules/chapel.json`, priority 38,
  cooldown 2 days, at most one). Finishing it grants the hidden advancement `built/chapel`. On the village map it is a
  Decorations-kind build (`VillageMaps`, by id).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `chapel` | The wedding venue, the Seer's place, the chapel service (48 blocks), 4 beauty, one of the Great Cathedral's three | Rare: locked as ??? until found or a level-up (`rare.json`); Steward rule `chapel.json` (civic, a courting couple) |

**One level only.** No `chapel_2` may be drawn: an upgrade is a new feature (`BlueprintUpgrades`, `StarterBlueprints`)
and no code asks for one.

## Must have
- **Job blocks: none.** No code looks for a workstation in it, and a workstation drawn in would be claimed as a job
  site by any jobless villager nearby. No lectern, barrel, composter, smoker, brewing stand, cauldron, loom,
  stonecutter, grindstone, crafting-family table or any of the mod's workstations.
- **Beds and residents: none.** No code counts beds here (a bed would be claimed as a home by vanilla villagers).
- **Storage: none read.** A container is free, but builders never break containers on a take-down.
- **Furniture kinds a job or test needs: none.** There is no `blueprint_furniture/chapel.json` today. If the design
  records spots (`rebuild.py` records every furniture piece of the plan), each spot's blocks must be exactly the
  template's (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`) and nothing may float
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`).
- **The middle is where people stand (code-read, no test pins the drawing).** The venue, the Seer's arrival point and
  the point the Seer walks back to are all the **middle of the declared size box at y = 1**: for an unturned placement
  template **(W / 2, 1, D / 2)**, integer division (`BoundingBox.getCenter` of `BlueprintOutline.bounds` with
  `StarterBlueprints.CHAPEL.size()`; today 11 x 16 x 16 gives (5, 1, 8)). So:
  - a guest appears on the first cell, searching outward ring by ring up to 6 blocks from that middle and
    y -1 to 3, that has a block with a full sturdy top under it and two cells with **no collision** above it (no
    carpet, slab, stair, pane, fence or candle in them) and no fluid. Keep a plain open floor cell with head room at or
    right beside the middle, inside the building, or the Seer turns up on the lawn or a ledge outside;
  - the Seer walks to within 2 blocks of the middle by day, so it must be reachable **on foot from outside**, through
    the way in, at ground level (not up a ladder or in a closed tower);
  - fireworks rise from the top surface of the columns within 3 blocks of the middle (anything is fine; nothing breaks).
- **A way in on the z = 0 side** at ground level that a villager 1.95 tall walks through (doorway rule in `check.py`,
  B170: no carpet just inside a 2-tall doorway; the full sweep is `QaDoorwayClearanceGameTests.tightDoorways`). Wooden
  doors only on the walking route (no villager opens an iron door).
- **No calcite, jigsaw, structure void, flowing liquid or entity**, and every block one a builder can place
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- **A bell is optional** (no code reads a bell in the Chapel). Note what one does: a bell is a vanilla meeting point,
  villagers gather at it, and if it is the bell nearest the Village Hall it becomes the village's festival square
  (`Festivals.square`). Weddings go to the Chapel's middle either way.

## Fits
- **Plot: at most 14 wide (x) x 16 tall x 17 deep (z).** `BuilderGameTests.buildsChapel` builds it in the
  22 x 18 x 22 `big_area` at helper origin **(8, 2, 5)** (x 8..21, y 2..17, z 5..21), with the builder's bench at
  (2, 2, 2), its chest at (2, 2, 4), the barrels at x 1..5, z 4 and the builder at (3, 2, 3). Taller than 16 puts blocks
  above the area (never cleared between batches); wider or deeper leaves the area. y = 0 sits on the clicked block;
  the front is the z = 0 side.
- **Declared size:** `StarterBlueprints.CHAPEL` is `new Vec3i(11, 16, 16)` today. If the size changes, the ship step
  changes that line (sizes must match), which also moves the venue middle (above), and `generate.py`'s
  `chapel().save(MAIN_STRUCTURES, "chapel")` must stop overwriting the new `.nbt`.
- **Material budget: at most 108 stacks** (four barrels of 27 in `BuilderGameTests.buildStarter`: "test needs more
  barrels"). The old drawing is 773 blocks in about 32 stacks.
- **Front and way in:** the Steward turns a civic plot so the z = 0 side faces the road (or else the hall); a lane is
  laid to the door. The way in is on that side.
- **Neighbours:** a decoration in a Steward civic zone; the Steward keeps 2 blocks clear round its plot (`Plots.MARGIN`,
  `Plots.CLEAR`, reading the blueprint's size) and wants the ground within 4 blocks of level (`Plots.MAX_STEP`), so every
  block of footprint costs plots in small villages (free, but a smaller footprint places more easily). No test places
  blocks round it by coordinates: the wedding, Seer, service and zone tests record a finished Chapel by id, origin and
  declared size only (the Seer tests in the 30 x 30 `huge_area` at (16, 1, 2) and (2, 1, 2); the middle must stay
  inside that area, which any size within the plot above does).

## Rules tests check
- `BuilderGameTests.buildsChapel` (batch `starter_builds_9`, `big_area`, origin (8, 2, 5), no rotation, build delay 2,
  builders' help off): a builder with exactly the plan's materials in four barrels builds it from bare ground to
  **DONE within the test's 40,000 ticks**, nothing left unfinished. Keep placed blocks to what that allows: stay within
  about **900 placed blocks** (a 1,410-block School timed out at 12,000 ticks; about 900 passed; the old Chapel's 773
  pass here), and put **no roof, barge or eave over another roof** (that School's builder stuck on a barge stair above
  a lower roof).
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt`'s size equals
  `StarterBlueprints.CHAPEL`'s, every block buildable.
- Library-wide, over every listed build: `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds` (a standing
  lantern, candle or torch stands on a top that holds its middle; no path block under a solid block),
  `B648GameTests.b648NoShippedGlassEndsInTheAir` and `QaWindowsAndWards1028BugTests.qaNoShippedGlassEndsInTheAirAsTheGameJoinsIt`
  (no pane or bar run ending against air), `QaChairFacingGameTests.everyListedBuildsChairsOpenTowardTheirTable` and
  `...StillFaceTheirTableTurnedAndMirrored` (any seat stair beside a table opens toward it, turned and mirrored too),
  `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo` (any chest drawn in must be reachable
  on foot), `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
  `StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a solid stack).
- By id only (the drawing doesn't matter, the id and the name "Chapel" do):
  `PersonalErrandGameTests.aChapelWeddingAndItsFeast`, `QaRequestFixes1116cGameTests` (the bell then the Chapel; the
  Chapel on day five wins), `QaRequestKinds1116cGameTests.theChapelWeddingWaitsFourDaysButNotFive`,
  `QaPersonalRequestsGameTests` (a Chapel outside the village doesn't count), `QaSageSeerGameTests`
  (`seerArrivalAtItsEdges`, `seerMidnightWindowEdges`, `seerBlessesOnlyNearTheChapel`: within 31 blocks of the middle
  blesses, 33 doesn't), `ZoneVillageBuildingsGameTests` (a Chapel in a zone past the radius is a service, beauty and the
  guests' place; one outside isn't), `ServiceGameTests` (`eachServiceIsFoundByItsBuild`, `aStoneworkChapelCounts`, 40
  blocks reaches and 60 doesn't), `ClassGameTests`, `StewardCivicRulesGameTests.aCourtingCoupleGetsAChapel` (wished
  for in `civic`), `BlueprintUnlocksGameTests` (a ??? at first, never named to the client; a found Chapel unlocks it),
  `WonderCathedralGameTests` / `QaWonders1101GameTests` ("finished chapel x3"), `LegendsHallGameTests`,
  `ArticleGameTests` ("a Chapel").
- The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
  stove or on a table with a chair, the doorway rule; `open_panes`), STYLE.md's checks and `holes.py`:
  `rebuild.py chapel chapel --candidate` prints no CHECK line and `holes.py` passes.
- Saves: build sites begun on the old drawing keep it through `blueprint_history/chapel/` (one entry today,
  `4c929e1a2.nbt`); the ship step saves the current drawing there (`history.py`) before replacing `structure/chapel.nbt`.

## Free
Everything not listed above: shape, plan, roof, tower or none, materials, colors, seating, an altar or none, a bell or
none, lighting, planting, height and footprint within 14 x 16 x 17, and style.
