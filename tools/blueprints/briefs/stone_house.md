# Stone House: the brief

Family id(s): `stone_house`, `stone_house_2`, `stone_house_3`, `stone_house_4` (never renamed)
Variants: village (none drawn: a village's style, `dark_oak`, `sandstone`, `cherry`, ..., is applied to the drawing
automatically by `BlueprintStyles`; `BuilderGameTests.buildsAStyledStoneHouse` builds the dark-oak one whole), modded
(none to draw: `modded_library.py` makes `structure/modded/stone_house*.nbt` from the vanilla drawing), looks (none).

## What it is for
The village's second kind of home: a family house for two, growing to a house for five. A player takes it from the
Blueprint Table (it is in every Table's starting set, `blueprint_unlocks/starting.json`) or finds it in village chest
loot (builders', carpenters', inn, kitchen and storehouse chests), hands it to a Builder, and villagers move in: each
bed is a home (`Homes.of` gives a bed's villager this house and its tier). The Steward builds one by himself in a
`homes` zone when the village is **two or more beds short** and no finished home's upgrade adds beds
(`steward_rules/homes_stone_house.json`, priority 89); the Daub House (`homes_house_t1_b`, priority 90) and the Terrace
come before it when a hamlet is three beds short, and the Starter Cottage when only one bed is short. Renewal proposes
it as the home to rebuild (`steward_renewal/homes.json`). Its rooms are furnished (37.4): with a furniture mod each spot
becomes that mod's piece, without one the vanilla stand-in. Each level is an upgrade on the same spot and origin.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `stone_house` | A home of **exactly 2 beds on two different floors** (one downstairs, one upstairs), a barrel downstairs, every furnished-room kind but the couch | Starting set; loot; Steward rule `homes_stone_house`; grade I with no `homes/` data file (`HomeGradeGameTests`) |
| II | `stone_house_2` | **Exactly 3 beds** (more than I, so the Steward's `homes_upgrade` and the Hall's Upgrades page pick it when beds are short: `StewardConditions.addsBeds`); adds the couch | Upgrade of I (`BlueprintUpgrades`: id + `_2`, tier 2); questbook reward (Builder chapter) or sold once I stands; locked until earned (`QaLibraryUnlocks1107cGameTests`) |
| III | `stone_house_3` | More beds than II (today 5) | Upgrade of II (tier 3); questbook reward |
| IV | `stone_house_4` | A **cellar exactly 4 blocks deep** under the house (`StarterBlueprints.Entry` basement 4), reached from inside; **no more beds than III** | Upgrade of III; a builder who finished a III sells it (`UpgradeOffers`) |

## Must have (each level)
- **Beds and residents.** Count is bed heads (`StewardConditions.beds`). I: exactly **2**; II: exactly **3**
  (`StewardHomesRulesGameTests.bedsShortUpgradeAStoneHouseBeforeANewHouse` asserts 2 and 3); III: more than II;
  IV: **not more than III** (the same test builds two IIIs and expects no `homes_upgrade` wish, so IV must not add
  beds). Every bed lies inside the build's footprint.
- **Level I's two beds are at different heights, and the upper one is reached from inside** (`AtticBedGameTests`,
  `DoorJamGameTests`: the two `HOME` POIs sorted, the second strictly higher; a villager upstairs at dusk must walk to
  and sleep in the upper bed; two villagers on the doorstep at bedtime both get through the door to their beds within
  20 seconds without giving them up).
- **Job blocks.** Level I has a **barrel on the ground floor** (`AtticBedGameTests` picks it as a Fisherman's
  workstation from upstairs). No other job block is required by code or test. Every block with block-entity data
  (barrel, chest, furnace, smoker, ...) stays in its cell in every later level (see "Kept cells").
- **Storage.** The upstairs bedroom of Level I holds **exactly one chest** on its floor (`FurnishedRoomsGameTests.theStoneHouseReadsAsAHome`).
  No job reads container contents; more storage elsewhere is free.
- **Furniture spots** (`blueprint_furniture/<id>.json`, written by the recipe's `furniture.json` through `rebuild.py`):
  - Level I has at least one spot of each kind **bed, chair, table, nightstand, lamp, shelf, crockery, clock, picture,
    blinds**; a couch is not required in I. II, III and IV have every kind including **couch**
    (`FurnishedHomesGameTests.everyHomeHasItsFurnishedRooms`).
  - Level I's upstairs bedroom (`theStoneHouseReadsAsAHome`, pinned at template y = 6 today): exactly **one bed spot and
    one nightstand spot** on that floor, exactly **one hanging lantern** at or above that floor and **no standing
    lantern** on it.
  - Level I's clock spot stands on the floor (its support includes FLOOR).
  - Level I: **no furniture spot in the way in**: none in the cells just inside the front door (today x 4..6, z <= 3,
    y <= 2), so the walk from the door to the stairs/ladder is clear (same test).
  - Each spot's blocks are exactly the template's blocks there (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`),
    and nothing small stands on air in any level (`FloatingFurnishingsGameTests`, which uses `stone_house_4`, furnished
    vanilla and with a lower-pieced table).
- **Level IV's cellar** (`BasementGameTests`, `QaBasement*` tests, all of `StarterBlueprints.WITH_BASEMENTS`): drawn at the
  bottom of the file, its floor at file y = 0 and the ground floor at y = 4, exactly 4 deep; at least one ladder or
  stairs and at least one `minecraft:lantern` below ground; a closed, dry room whose every air cell is dug out; the way
  down opens from inside the house. Its above-ground part is III's.
- **A front door** on the ground floor at the z = 0 side, with a doorstep a villager can stand on outside it
  (`DoorJamGameTests` pins the door and step; `Paths.doorstep`).

## Kept cells (every upgrade)
- Each level has the same origin and front as the one below and is at least its size in x, y and z.
- Every block with block-entity data in a level stays in the same cell as the same block in every later level (a
  builder never breaks those while clearing, `MaterialRules.isProtected`), so an upgrade that moves one can't finish.
- An upgrade builds only what changed, so keep most of the level below in place.

## Fits
- Plot (from the tests that place each level by coordinates; the ship step moves coordinates, not areas):
  - Level I: **13 x 13 x 16** (`BuilderGameTests.buildsStoneHouse` at (9, 2, 9) in the 22 x 18 x 22 `big_area`, the
    builder's barrels and bench at x <= 5).
  - Level III (and so II): **13 x 14 x 16** (`BuilderGameTests.buildsStoneHouseIII` at (9, 2, 8) in `big_area`).
  - Level IV: III above the ground plus the 4-deep cellar.
  - Level II: under 20 blocks wide (`HomesSpecGameTests` puts a bed at x 20+ as "outside any building").
- Declared sizes: `StarterBlueprints.STONE_HOUSE` (`11, 11, 9`), `_2` (`11, 16, 9`), `_3` (`11, 16, 14`), `_4`
  (`11, 16, 14`, basement 4) today; if a size changes the ship step changes those `Vec3i`s and any loot table that carries
  the size (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- Material budget: Level I's whole list fits the four barrels `buildStarter` stocks (**at most 108 stacks**, each item
  rounded up to whole stacks); Level III's too (same helper).
- Build time: I within 30,000 ticks and III within 40,000 (`buildsStoneHouse`, `buildsStoneHouseIII`); I from exactly its
  material list, alone and with a helper, within 40,000 (`QaB21B27GameTests`).
- Front and way in: the z = 0 side faces the player who places it and the road of a Steward plot; the front door goes
  there on the ground floor; every upgrade keeps that front.
- Neighbours: a home in a Steward `homes` zone (`VillageMaps` counts `stone_house` as HOMES); the Steward finds it a plot
  (`PlotGameTests`) and keeps ground clear round it.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `AtticBedGameTests.aVillagerWalkingToAWorkstationAtDuskGoesToBedInstead` (I): barrel today at (2, 1, 2); a villager on
  the upper floor (today at (2, 6, 3)) goes to the upper bed at dusk instead of the barrel and sleeps in it.
- `DoorJamGameTests.twoVillagersThroughOneDoorBothGetToBed` (I): door today (5, 1, 1), doorstep (5, 0, 0); both villagers
  reach z >= 2 inside the box and keep their beds.
- `FurnishedRoomsGameTests.theStoneHouseReadsAsAHome` (I): the door cells, upstairs y, one bed, one nightstand, one chest,
  one hung lantern, no floor lantern, clock on the floor (above).
- `HomesSpecGameTests` (II): a bed inside II's footprint gives a tier-2 home; the Hall lists "Lives in the Stone House II".

Built whole (every block right, 0 skipped, supplies exact): `BuilderGameTests.buildsStoneHouse`, `buildsStoneHouseIII`,
`buildsAStyledStoneHouse` (dark oak); `QaB21B27GameTests.qaB22...NeverWaits` and `qaB21...WithAHelper...NeverWaits`;
`BuilderCrewGameTests` and `QaCrewBusy1110bGameTests` (a crew builds I from exactly its materials); the basement tests
(IV); `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` (every block one a builder can place).

Refresh (36.21, B642): `RefreshGameTests.aStoneHouseFromAnOlderDrawingIsRefreshedToTheCurrentOne` (12,000 ticks) and every
`RefreshMatchesFreshBuildGameTests` / `QaRefreshFurnished*GameTests` / `QaRefreshOldHouseGameTests` /
`RefreshFurnishedCompatTests` case: a Stone House built from each older drawing in `blueprint_history/stone_house/`
(also turned, mirrored, furnished) refreshed through the Hall's Repairs page ends equal to a fresh build of the new
drawing (chests, slept-in beds, job blocks and players' blocks aside), nothing floating, and offers no second refresh.
A new drawing makes this a near-whole rebuild, so it must fit those tests' time and the 30 x 30 `huge_area` at (9, 2, 9).

By id only (the drawing doesn't matter beyond the bed counts above): `StewardHomesRulesGameTests`, `DaubHouseGameTests`
(Daub House ranks ahead for three short; Stone House for two), `StewardRulesGameTests`, `HomesGameTests` (tiers 1-3),
`HomeGradeGameTests`, `QaHomeGradeFilesLaneC1107GameTests`, `QaGranderHomesGameTests`, `ManorGameTests`,
`RenewalGameTests`, `QaRenewalGameTests`, `CrafterWorkshopGameTests`, `StewardSafetyGameTests`,
`QaStewardSafetyGameTests`, `QaSteward1031GameTests`, `QaShoppingPlurals1031GameTests`, `QaSelfBuild1114cGameTests`,
`BlueprintUnlocksGameTests`, `QaLibraryUnlocks1107cGameTests`, `QaBlueprintLibraryVerify1108bGameTests`,
`QaQuestbookClaims1116eGameTests`, `QaBlueprintHandlingGameTests`, `QaHallListsOneBadEntry1102BugTests`,
`QaHallLenientLoad1102GameTests`, `StyleGameTests`, `PlotGameTests`.

The owner's rules (`check.py`: posts at fence corners, lamps on tables or posts, crockery only by a stove, furnace,
smoker or counter or on a table with a chair), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line for any level, and `holes.py` passes.

## Free
Everything not listed above: shape, plan, rooms, roofs, materials, colors, style, which floor is an attic, where the
furniture and job blocks stand (within the rules above), how II adds its bed and III its beds, what the cellar holds,
lighting.
