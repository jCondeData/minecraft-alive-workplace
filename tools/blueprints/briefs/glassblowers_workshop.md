# Glassblower's Workshop: the brief

Family id(s): `glassblowers_workshop` only (never renamed; "Glassblower's Workshop" in `en_us.json`,
`blueprint.aliveworkplace.glassblowers_workshop`; hidden advancement `built/glassblowers_workshop`)
Variants: village (none), modded (none to draw: `modded_library.py` makes `structure/modded/glassblowers_workshop.nbt`
from the vanilla drawing and the ship step reruns it; `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`,
`withoutTheModsEveryBuildIsTheVanillaBuild`), looks (none). The Table's style buttons (`BlueprintStyles`) restyle the
drawing automatically. ROADMAP item: 50.7 (Milestone 50, the workshops, same method as 49).

## What it is for
The workplace of the **Glassblower** (`ModVillagers.GLASSBLOWER`, a worker on the long shift; wiki `glassblower.md`).
His only workstation is the mod's **Glass Kiln** (`aliveworkplace:glass_kiln`, POI `GLASS_KILN_POI`, in
`#acquirable_job_site`): a jobless villager takes a free kiln by himself. At it he fires sand into glass (a coal or
charcoal for every eight) and makes the panes, stained glass and panes, tinted glass and bottles the builders and the
Storehouse wait for (`craft/GlassblowerWork`, a `CrafterWork`). He takes sand, coal and dyes from the builder's chests,
the village's stores and **his own chests by his kiln** (B1156: `Village.takesPart`, his chests are a stash the
builder's requests draw on), and carries what he made to the builder's chest. The Steward builds this building in a
`workshops` zone in two cases: a wanted Glassblower has no workstation (`steward_rules/workplace_glassblowers_workshop.json`,
priority 62, cooldown 2 days), or the village has 8+ villagers, no Glassblower and none built yet
(`steward_rules/glassblowers_workshop.json`, priority 40, max 1). His renewal of an old Glassblower's house rebuilds it
as this one (`steward_renewal/glassblower.json`). On the Village Map it is a Workshops building (`VillageMaps.kindOf`,
by name). It is offered in the Blueprint Table as a one-tier starter build (`StarterBlueprints.ONE_TIER`); it is not in
`blueprint_unlocks/` (starting or rare) and has no expansion switch.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `glassblowers_workshop` | The Glassblower's Glass Kiln and his own chests by it | Blueprint Table (`ONE_TIER`); the two Steward rules; renewal list `glassblower.json` |

**Exactly one level.** No `glassblowers_workshop_2`: the entry is in `ONE_TIER` (asserted by
`GlassblowerGameTests.theGlassblowersWorkshopIsOfferedAndPlanned`), and a `_2` would become an upgrade by name
(`BlueprintUpgrades`), changing `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`'s count of exactly 64. That is a
new feature, not part of a rebuild.

## Must have
- **Job block: exactly one `aliveworkplace:glass_kiln`.** `GlassblowerGameTests.theGlassblowersWorkshopIsOfferedAndPlanned`
  counts the blueprint's blocks: "Glass Kilns in it" must be 1. Its facing is free.
- **No `minecraft:furnace` anywhere** (same test: "a furnace in it" must be 0; the kiln is his, a furnace never is).
- **No other trade's job block** (code, not a test: every acquirable job block is a free workstation that the Village
  Hall lists, `StewardJobs` hands to a jobless villager and vanilla villagers take by themselves, so another job would
  move in): no barrel (Fisherman), composter, lectern, loom, stonecutter, grindstone, cauldron, brewing stand, blast
  furnace, smoker, smithing table, fletching or cartography table, bell, crafting table (a free Carpenter's block in
  the hall's list), and none of the mod's workstations. A chest, campfire or anvil is not a job block.
- **The kiln is reachable on foot from outside**, within a player's reach (2 across, 1 up or down) of a cell a villager
  walks to (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`). The Glassblower works at it
  all day and walks out to the builder's chest and back, so it belongs on the ground floor or on a floor reached by
  stairs or a ladder from inside.
- **Storage: at least one chest within 8 blocks across (x and z) and 4 up or down of the kiln**
  (`SupplyContainers.RADIUS` / `VERTICAL`). These are his own chests: the sand and coal kept there are used for the
  builder's glass (`QaGlassblowerStash1110cBugTests`, `QaCrafterOwnChest1111cGameTests` test the rule with a kiln and
  chest they place themselves), and `CrafterWork` tidies his bag into them (with none, it is dropped on the ground).
  The old one has two single chests; any number from one up is fine. Drawn empty (builders never fill or copy
  containers; no loot table). Every chest stands against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`) and is reachable on foot (`QaBuildReachGameTests`).
  A double chest is allowed, not required (this id is not in `StarterBlueprints.ALL`, so
  `QaMirroredBuildsGameTests` does not check it).
- **Plain glass panes in the plan: at least 1 and at most 64 `minecraft:glass_pane`.** The showcase scene `glassblower`
  (`src/devclient/.../JobScenes.java`, "glassblower"; `tools/showcase/scenes.py`) starts a builder on this very
  blueprint with every material stocked **except plain glass panes**, adds 24 sand and 4 coal, and passes only when the
  Glassblower has made panes and carried them all to the builder's chest within 4,000 ticks. With no plain pane in the
  plan he has nothing to make and the scene fails on the nightly page; 24 sand fire 24 glass, which make at most 64
  panes, so more than 64 leaves him short. Stained panes and glass blocks are stocked normally and are free. (The old
  one has 6 plain panes.)
- **Beds and residents:** none required (the Glassblower is a worker, not a resident; no code or test counts a bed
  here). The old one has one bed; that is free. If the designer adds a bed: its head is reachable on foot
  (`QaBuildReachGameTests`), its foot has its head one block towards its facing
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`), and no bed stands right inside a doorway
  (`check.py`).
- **Furniture spots:** no kind is required by any test for this id (not a home). Every furniture piece drawn is
  recorded as a spot (`rebuild.py` regenerates `blueprint_furniture/glassblowers_workshop.json`; the old file holds a
  bed, two shelves and two crockery pots, none of them required). Each spot's blocks are exactly the template's
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`), nothing small stands on air
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`), no standing lantern on a room's floor
  (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`), every chair stair faces its table, turned and
  mirrored too (`QaChairFacingGameTests.everyListedBuildsChairsOpenTowardTheirTable` /
  `...StillFaceTheirTableTurnedAndMirrored`).
- **A way in that a villager 1.95 blocks tall walks through** (`QaDoorwayClearanceGameTests`; B170), on the ground
  floor. Any upper floor or loft holding a chest, bed or job block is reached by a ladder or stairs from inside (B149,
  `QaBuildReachGameTests`).
- Every block is one a builder can place, and the file holds no jigsaw, structure void or entity
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`). Every countable material has a plural
  in `en_us.json` (`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`).

## Fits
- **Plot: no GameTest builds it in an area, so no test area caps it.** The old one is 13 wide x 12 tall x 11 deep
  (458 non-air blocks). Keep it a modest workshop: the Steward must find a `workshops`-zone plot with 2 blocks of zone
  margin round it (`Plots.MARGIN`), 2 blocks clear of every other build (`Plots.CLEAR`) and ground that varies by at
  most 4 blocks under the footprint (`Plots.MAX_STEP`), and a renewal takes it only where it fits the old house's
  footprint plus 3 a side (`Renewals.MARGIN`), so every block wider or deeper makes the Steward's two rules and the
  renewal succeed less often. Its 50.7 siblings (Jeweler's, Crafter's Workshop) sit in the same footprint class (the
  Crafter's is 13 x 11 x 11). Treat about 15 x 15 footprint as the ceiling.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Front and way in:** a Steward plot turns the z = 0 side to the nearest road (or the hall) (`Plots`), and the
  `glassblower` scene places it facing south with the builder, his Blueprint Table, the barrels and the kiln station all
  in front of its z = 0 side (the anchor is the front's middle; the build grows away from them), so the way in belongs on
  the z = 0 side, and nothing of the plan (path, landscape) should reach in front of z = 0.
- **Neighbours:** a workplace among other workshops in a Steward `workshops` zone; no party walls, no rows.
- **Declared size:** `StarterBlueprints.GLASSBLOWERS_WORKSHOP` is `(13, 12, 11)` today; the ship step changes that line
  to the new size (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, and
  `GlassblowerGameTests.theGlassblowersWorkshopIsOfferedAndPlanned` asserts the loaded size equals the entry), together
  with its javadoc and the wiki's one-line description (both describe the old looks).
- **No build-to-DONE GameTest and no tick budget** name this blueprint; the only timed run is the scene above (4,000
  ticks, and it does not wait for the building to finish).

## Rules tests check
- `GlassblowerGameTests.theGlassblowersWorkshopIsOfferedAndPlanned`: in the Blueprint Table, in `ONE_TIER`, named,
  loaded size equals the entry's, **exactly one Glass Kiln, no furnace**, a Workshops building on the Village Map, at
  least two Steward rules build it.
- `QaGlassblower1110eGameTests.theGlassblowersSentencesReadRight`: the name reads "Glassblower's Workshop" (lang only).
- Scene `glassblower` (nightly showcase, a pass or fail on the owner's page): 1 to 64 plain glass panes in the plan;
  the front on the z = 0 side.
- Library-wide checks it must pass: `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`,
  `QaFurnishedRooms1031GameTests`, `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`,
  `BlueprintsStayGameTests`, `B648GameTests.b648NoShippedGlassEndsInTheAir` and
  `QaWindowsAndWards1028BugTests.qaNoShippedGlassEndsInTheAirAsTheGameJoinsIt` (no pane or bar joined on one side with
  air on the other), `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
  `StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a solid stack, as the old chimney's
  does), `ModdedLibraryGameTests`, `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.
- The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
  stove or on a table with a chair, no bed right inside a door; `open_panes`): the old drawing has 2 flagged in
  `owner_rules_baseline.json`; the new one must have none (the ship step drops its baseline line). STYLE.md's checks and
  `holes.py`: `rebuild.py --candidate` prints no CHECK line, and `holes.py` passes. Saves stay safe: the ship step keeps
  the old drawing in `blueprint_history/` (`history.py`) and regenerates the modded copy and the furniture spots.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, a kiln house or chimney or yard or none,
what the trade shows from the street, where the kiln and chests stand (within the rules above), beds, how many chests
beyond one, stained or tinted glass, lighting, and its size within the plot guidance.
