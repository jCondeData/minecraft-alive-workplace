# Healing Center: the brief

Family id(s): `healing_center`, `healing_center_2`, `healing_center_3`, `healing_center_4` (never renamed; "Healing Center",
"Healing Center II", "III", "IV" in `en_us.json`). Variants: village (none), modded (none to draw: `modded_library.py`
makes `structure/modded/healing_center*.nbt` from the vanilla drawing; `QaModdedRefresh1115cCompatTests.redrawnModdedCopiesAreOfferedFromTheirShippedDrawingOnly`
needs the modded III buildable with the pack's mods), looks (exactly one: `PokemonCenterGameTests` asserts
`BlueprintLooks.of(healing_center) == [healing_center]`; `BlueprintStyles` restyles the drawing, so add no look).

## What it is for
The village clinic. Its job block is a **brewing stand**, the workstation the **Nurse** (`aliveworkplace:nurse`) shares
with the Cleric, the Undertaker and the Physician (`Stations`, `RandomJobs`: a jobless villager who walks in takes it as one
of them). The Steward builds one in a **civic** zone from Village rank when a Nurse or Cleric has no workstation
(`steward_rules/workplace_healing_center.json`) or when 2+ villagers are ill, there is no Nurse and none stands yet
(`healing_center_for_the_ill.json`, at most one); it renews an old Nurse's or Cleric's house (`steward_renewal/nurse.json`,
`cleric.json`: "the first that fits"). A finished one, any level or style, gives homes within 48 blocks the **clinic**
service (`services/clinic.json`). The village map marks it CARE (`VillageMaps`). Players buy Level I from an **Expert
(level 4) Builder** for 16 emeralds (`ModTrades`, `BlueprintUnlocks`: locked, "Sold by an Expert Builder"). Its desks are
CobbleFurnies' **Poke Ball desks** at every level (`FurnitureChoice.hasPokeBallDesks`, by id). Each level is an upgrade on
the same spot, origin and front.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `healing_center` | The working clinic: the brewing stand reached through the front door, its one chest, waiting benches, a desk | Expert Builder trade; Steward rules above |
| II | `healing_center_2` | Nothing the code reads (the old one added a ward of 4 beds; no code counts them) | Upgrade of I (id + `_2`, `BlueprintUpgrades`); a builder who finished I sells it (`UpgradeOffers`) |
| III | `healing_center_3` | A **second, back door** a villager walks in by from outside the back (see pinned tests); the rest is free | Upgrade of II |
| IV | `healing_center_4` | A **basement exactly 4 deep** under III, reached from inside | Upgrade of III (`BasementGameTests.basementsLoadUnderTheGround`: a builder who finished III sells IV) |

## Must have (each level)
- **Job block: one `minecraft:brewing_stand`**, reached on foot from the front door. In Level I it must be the **only**
  workstation a jobless villager could take (`DoorwayHeadroomGameTests.aJoblessVillagerOpensTheHealingCentersShutDoorAndTakesItsBrewingStand`
  passes only if the job taken is a brewing-stand job; a composter, lectern, etc. nearer the door would break it). Level I's
  materials must include that brewing stand (scene `cleric_crafts`, `JobScenes`: the builder's site waits for one).
- **Storage: a chest within 8 across and 4 up or down of the brewing stand** (`SupplyContainers.RADIUS` reach used by job
  blocks). **Level I has exactly one chest or barrel** inside its box (scene check `FurnishedWorkplacesScene.storage`
  expects 1). Every chest stands against a wall, a solid block or a bed (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`).
- **Beds and residents: none required.** No code counts beds here. Any beds you add are plain vanilla beds with **no bed
  furniture spot** (`FurnishedWorkplacesGameTests.noSpotCoversAJobBlockABedOrAContainer`: a workplace has no bed spot).
- **Furniture spots** (`blueprint_furniture/<id>.json`, written through `rebuild.py`):
  - **I, II, III: at least one `bench`, `picture` and `desk` spot; IV: at least one `bench` and `picture`** (desk optional)
    (`FurnishedWorkplacesGameTests.everyWorkplaceIsFurnished`).
  - Every bench is one Handcrafted fills, every desk one the CobbleFurnies Poke Ball desk fills, every spot gets a piece
    except a wall picture (`FurnishedWorkplacesCompatTests.theHealingCenterAndSchoolhouseGetPokeBallDesks`, I and II, desks > 0);
    every styled I and II plans a Poke Ball desk (`QaStyledPokeDesks1104dCompatTests.aStyledHealingCenterIsPlannedWithPokeBallDesks`,
    `QaBuildViews1103gBugTests.aStyledHealingCenterOrSchoolhouseKeepsItsPokeBallDesks`, `QaFurnishedViews1104cGameTests`).
  - No spot covers a job block, a container or a bed; without a furniture mod the desks plan vanilla
    (`withoutCobbleFurniesTheDesksFollowTheOrder`); each spot's blocks equal the template's (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`);
    nothing floats (`FloatingFurnishingsGameTests`); no standing lantern on a room's floor (`QaFurnishedRooms1031GameTests`).
- **A front door** on the ground floor (template y 1) on the z = 0 side, with no carpet directly under its lintel (B173).
- **III and IV: a back door** on the ground floor, entered from an outside space the villager can stand in (pinned below).
- **IV's basement** (`BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests`, `QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth`):
  drawn at the bottom of the file, floor at file y 0, ground floor at file y 4 (file height = III's height + 4); a ladder
  or stairs on the basement's top layer with 2 passable cells above it; at least one `minecraft:lantern` below ground; no
  dark standable cell; walkable from outside down and back up; every chest down there within 2 across / 1 up-down of a
  walked cell.
- **The `cobblemon:healing_machine` is allowed, not required** (it loads as air without Cobblemon and the builder skips it).

## Kept cells (every upgrade)
- Each level has the same origin and front as the one below and is at least its size in x, y and z; **at least 60 % of the
  level below's non-air blocks stay the same block in the same cell** (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`).
- **The brewing stand, every chest and every other block-entity block stays in the same cell as the same block** in every
  later level (builders never break them, `MaterialRules.isProtected`), and IV's basement goes under III without moving them.

## Fits
- Plot (y = 0 sits on the clicked block; the front is the z = 0 side), from where tests place each level:
  - Level I: **at most 13 wide x 16 tall x 13 deep** (`BuilderGameTests.buildsHealingCenter` at (9, 2, 9) in the 22 x 18 x 22
    `big_area`, builder's barrels at x 1-5, z 4; also `DoorwayHeadroomGameTests` at (8, 1, 6) in `huge_area`).
  - Levels II-IV: **at most 21 wide x 18 tall x 26 deep** above the ground (III at (9, 2, 4) in the 30 x 20 x 30 `huge_area`,
    `QaDoorwayBugGameTests`; IV turned a quarter at (25, -2, 4), `QaBuilderFixes1027bGameTests`; barrier roof at y 20), plus IV's 4-deep basement.
  - A smaller footprint fits more civic plots and renewals (`StewardWishes.plotFor`, `Renewals`).
- Declared sizes: `StarterBlueprints.HEALING_CENTER` (11, 10, 10), `_2` (11, 10, 16), `_3` (11, 10, 22), `_4` (11, 10, 22, basement 4)
  today; the ship step updates them (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: sizes match, every block placeable;
  the trade's blueprint item carries the size).
- Material budget: Level I fits the four barrels `buildStarter` stocks, **at most 108 stacks**.
- **Build time: Level I with exactly its materials inside 9,000 ticks** (`buildsHealingCenter` timeout; the builder starts at
  day time 2,000 and stops at dusk, about tick 9,000-11,000). The old Level I (about 470 placed blocks) passes; earlier
  rebuilds over ~1,100 blocks timed out. Keep Level I lean.
- Front and way in: the z = 0 side faces the civic street; the front door there on the ground floor; every upgrade keeps it.
- Neighbours: none; placed by the Steward in a civic zone or anywhere by a player.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `DoorwayHeadroomGameTests.aVillagerWalksInByTheHealingCentersFrontDoor` and `...OpensTheHealingCentersShutFrontDoorAndWalksIn`
  (I at (8, 1, 6) in `huge_area`): a villager from template (5, 1, 0), outside the front door at (5, 1, 2), walks to the
  standable cell (5, 1, 5) inside, feet on the floor, past template z 4.
- `DoorwayHeadroomGameTests.aJoblessVillagerOpensTheHealingCentersShutDoorAndTakesItsBrewingStand` (day 2,000, 1,200 ticks):
  a jobless villager spawned at template (5, 1, -2) opens the shut door, gets past z 4 and takes the brewing stand.
- `QaDoorwayBugGameTests.aVillagerWalksInFromTheHealingCenterIIIsGarden` (III): every door but the back door at (5, 1, 14) walled
  up, a villager walks from (5, 1, 18) to (5, 1, 12) inside. `QaBuilderFixes1027bGameTests.aVillagerWalksInByTheTurnedHealingCenterIVsGardenDoor`:
  the same in IV turned a quarter, cells y + 4 for the basement ((5, 5, 18) -> (5, 5, 12) via (5, 5, 14)).
- `FurnishedWorkplacesScene` (showcase `furnished_workplaces`, still `04_clinic_benches`): Level I at a site, camera eye at
  (7.5, 1.0 feet, 3.2) inside the hall looking at a bench near (2.5, 4.5); the eye cell must be open room.
- `BuilderGameTests.buildsHealingCenter` (above); `QaDoorwayBugGameTests.everyListedBuildsDoorwaysLeaveAVillagerRoomOverTheStep`
  (headroom of 1.95 over every door's step); a door never opens onto a wall (B487).

By id only (the drawing doesn't matter beyond the rules above): `WorkplacesGameTests` (`workplace_healing_center`, Nurse at
Village, Cleric at Town), `StewardCivicRulesGameTests.twoIllAndNoNurseGetAClinicThenAHealingCenterFromAVillage`,
`ServiceGameTests.eachServiceIsFoundByItsBuild`, `QaClassPerksGameTests.aStyledUpgradeCountsAsItsBase` (IV gives clinic),
`PokemonCenterGameTests`, `QaBlueprintLibraryVerify1108bGameTests` (locked, "Sold by an Expert Builder"),
`QaFurnishedViews1104cGameTests` (Table list = builder's ask in every style; a styled build has the plain one's spots; >= 30 styled
Healing Centers and Schoolhouses), `QaRefreshHistory1028GameTests` (older drawings in `blueprint_history/healing_center*/` are
offered a refresh; the ship step keeps them), advancements `built/healing_center*.json`.

Shared checks over every shipped blueprint: `StyleGameTests.chimneysStayWholeInEveryStyle` (no chimney required);
`B648GameTests` (no pane or bar run ends against open air). The owner's rules (`check.py`: posts at fence corners, lamps on
tables or posts, crockery only by a stove or on a table with a chair, `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line for any level, and `holes.py` passes.

## Free
Everything not listed above: shape, plan, rooms, roofs, materials, colors, style, storeys, where the brewing stand, chest,
benches, desk and doors stand within the rules, whether there are beds, a ward, a garden, a Nurse Station, a Healing Machine
or a herb store, what II and III add beyond the back door, and what IV's basement holds.
