# Clinic: the brief

Family id(s): `clinic` (one level, never renamed; "Clinic" in `en_us.json`, `QaWorkplaceCopiesGameTests` reads it;
advancement `built/clinic`). Variants: village (`village/<style>_clinic` for `plains`, `desert`, `savanna`, `snowy`,
`taiga`: `village_looks.py` `DESIGNS["clinic"]` plus `village.py`'s `clinic` fit-out, see "The village variant"),
modded (none to draw: `modded_library.py` makes `structure/modded/clinic.nbt` from the vanilla drawing; the ship step
reruns it), looks (none: `BlueprintStyles` restyles it). Not in this family: the Healing Center (`healing_center*`).

## What it is for
The **Nurse's** workplace and the village's sick ward. A Nurse (`ModVillagers.NURSE`) works at a **brewing stand**
(the vanilla `CLERIC` POI, which her job holds; also a Nurse Station or, with Cobblemon, a Healing Machine). From it she
cures the village's ill within 32 blocks (`NurseWork.CURE_RANGE`, 48 with the Healers' Guild) with a remedy (honey
bottle, milk, potion) **taken from chests within 8 across and 4 up or down of her brewing stand**
(`SupplyContainers.find(site)`, `RADIUS` 8, `VERTICAL` 4), and heals players nearby. A player makes a Nurse by standing a
villager right next to the brewing stand with a honey bottle and sneak-right-clicking (`work/Stations`, reach 4.5); a
glass bottle there makes a Cleric. The questbook ("A Nurse: the Clinic") promises "honey bottles from the chest by the
brewing stand". A finished Clinic is the **clinic service** (`services/clinic.json`, range 48, with `healing_center`),
which Artisan, Burgher and Noble households need (`classes/`). It is in every new village's Table
(`blueprint_unlocks/starting.json`, `StarterBlueprints.WORKPLACES`); the Steward builds it in `civic` below Village rank
when a Nurse or Cleric lacks a workstation (`steward_rules/workplace_clinic.json`) or 2+ villagers are ill with no Nurse
(`clinic_for_the_ill.json`), and renews a Nurse's or Cleric's old house as it (`steward_renewal/`).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `clinic` | The Nurse's brewing stand, a remedy chest by it, a ward of at least 4 white beds | starting set; Steward rules above; renewals; questbook |

**One level only.** No `clinic_2`: it would change `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64).

## Must have
- **Job block: exactly one `minecraft:brewing_stand`** in the file and in the built box (`WorkplacesGameTests.workplaceBuilt_clinic`:
  "hasn't one brewing stand"). No Nurse Station, no Healing Machine, no other job block of any job (barrel, lectern,
  composter, cauldron, smoker, crafting/fletching/smithing/cartography table, bell, the mod's workstations): each would
  pull a villager to another job (`workplaces.py` contract). A chest, bookshelf or flower pot is not a job block.
- **The brewing stand reached on foot from the front door**, with a free standing cell beside it (feet and head clear,
  floor under) for the Nurse and for a villager a player stands there (`QaBuildReachGameTests`; `workplaceBuilt_clinic`:
  the Nurse walks in and must hold it as her `JOB_SITE`).
- **Storage: at least one plain `minecraft:chest` within 8 across and 4 up or down of the brewing stand** (the remedy
  chest; code and questbook, no test pins it). Drawn empty, openable, reachable on foot, indoors against a wall, solid
  block, bed or chest (`QaFurnishedRooms1031GameTests`). No barrel (the Fisherman's job block).
- **Beds: at least 4 whole `minecraft:white_bed`s** (head and foot, same facing), each **head against a sturdy full
  face** (a solid block; glass or bars pass the template test but **not** the built test, so use a solid wall)
  (`ClinicWardGameTests.everyClinicBedIsWholeWithItsHeadToAWall`, `QaClinicTerrace1028GameTests.qaEveryClinicLookHasTheSpecsCountOfWholeBeds`:
  >= 4 whole, >= 4 white). Each bed is a free home (`PoiTypes.HOME`) a villager can claim and lie in when built turned
  and mirrored (`qaABuiltTurnedMirroredClinicHasFourBedsVillagersCanSleepIn`). One may be the Nurse's own. Bed heads
  reachable on foot (`QaBuildReachGameTests`); no bed right inside a doorway (`check.py`).
- **No slab-and-carpet cots**, and in the Clinic **no carpet on any slab at all**, drawn or built
  (`QaClinicTerrace1028GameTests.qaNoBuildHasASingleSlabAndCarpetStandInBed`, `ClinicWardGameTests.noBuildHasCotsOfSlabAndCarpet`,
  `QaWindowsAndWards1028GameTests.qaABuiltClinicHasWholeBedsAndAClosedRidge`, `check.py` `cot_problems`).
- **Ridge cells, closed: at least 6.** A "ridge cell" is a cell under a **bottom slab** with a **bottom stair** on each
  side climbing up toward it (E-W or N-S). The tests need **>= 5 in the drawing** (`qaABuiltClinicHasWholeBedsAndAClosedRidge`)
  and **> 5 in the built, turned and mirrored Clinic** (`qaABuiltTurnedMirroredClinicHasFourBeds...`), and **> 20 over all
  seven Clinics** together (`ClinicWardGameTests.theClinicRoofsHaveNoGapUnderTheRidge`); **every** such cell must be a
  solid block, never air. So some roof run must end in a slab ridge over stairs on both sides, six cells long or more.
- **Furniture spots** (`blueprint_furniture/clinic.json`, written by `rebuild.py`): no kind required (`clinic` is not in
  `FurnishedWorkplacesGameTests.workplaces()`); today it has only the 4 bed spots. Spots must not cover the brewing stand,
  a chest or another spot's bed (`noSpotCoversAJobBlockABedOrAContainer`); blocks match the template
  (`FurnitureSpotGameTests`); nothing floats (`FloatingFurnishingsGameTests`); chairs face their tables
  (`QaChairFacingGameTests`). Any kind other than bed/chair/table makes it "furnished" for `QaFurnishedRooms1031GameTests`
  (indoor chests against something, no lantern standing on a room floor, every furnished room lit above 7).
- **No calcite, jigsaw, structure void or entity** in the file (`workplaceBuilt_clinic`); every block one a builder can
  place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- **No trapdoor in the front walk row** (z = 1, y 0-1; `VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow`
  reads `structure/clinic.nbt`). Any upper floor holding a bed or chest is reached by stairs or ladder (B149).

## Fits
- **Plot: at most 18 wide (x) x 16 deep (z) x 18 tall.** All in the 30 x 20 x 30 `huge_area` (barrier roof at y 20):
  `workplaceBuilt_clinic` at helper (12, 2, 10) (x <= 18, z <= 20, y <= 18); the turned-mirrored build at (14, 2, 6),
  `CLOCKWISE_90` + `LEFT_RIGHT`, puts template z along world x 14..29 (**depth <= 16**) and template x along z 6..29;
  `qaABuiltClinicHasWholeBedsAndAClosedRidge` at (8, 2, 8); the refresh tests at (9, 2, 9) (bench (3, 2, 26)).
- **Material budget: at most 81 stacks** (three chests of 27; `workplaceBuilt_clinic`).
- **Build time, one working day.** Builder tests start at day time 2,000 and the builder stops at dusk (~tick 9,000-11,000).
  The turned-mirrored test (build delay 1, free materials, 24,000 ticks) does **not** hold the clock, so the Clinic must
  finish in one day; `workplaceBuilt_clinic` (delay 2, 12,000 ticks) holds it. Earlier rebuilds over ~1,100 blocks timed
  out; ~900 passed at delay 2 in 12,000. **Tightest:** `QaModdedRefresh1115cCompatTests.aRealBuilderRefreshesTheModdedClinicToWhiteBeds`
  (delay 2, **9,000 ticks**, no clock hold) refreshes a standing 0.151.0 Clinic (509 blocks) to the new one: every cell
  that differs is cleared and placed. Keep the drawing lean (aim at or under ~600 placed blocks; the old one is 509).
- **Declared size:** `StarterBlueprints.CLINIC = workplace("clinic", 13, 11, 12)` (w, h, d); the ship step sets the new one.
- y = 0 sits on the clicked block; **the front is the z = 0 side**, the way in on it, walkable by a villager 1.95 tall
  (`QaDoorwayClearanceGameTests`; no carpet just inside a 2-tall doorway). The waiting Nurse stands at template
  **(6, 0, -3)** (helper (18, 2, 7)) and must walk in by the front.
- **Neighbours:** a Steward `civic` plot: 2 clear round it (`Plots.CLEAR`/`MARGIN`), ground within 4 of level
  (`Plots.MAX_STEP`); a renewal fits the old footprint plus 3 a side (`Renewals.MARGIN`), so smaller renews more.

## The village variant (`village/<style>_clinic`, five styles, pool weight 2 in `VillageHouses`)
The shared village-house contract (`village_looks.py` header, `VillagePieces`): street jigsaw at **(4, 1, 0)** north,
front row z = 0 left as ground; door at **(4, 1, 2)** facing south with a step at (4, 0, 1); the room **x 2-6, z 3-7**,
west wall x 1; the home bed **foot (6, 1, 6), head (6, 1, 7)** facing south; the Nurse entity at **(5, 1, 5)**
(`VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: one brewing stand, one job block, a Nurse); fit-out (`village.py`
`clinic`) puts the brewing stand at (2, 1, 7). Its ward: **>= 3 whole beds counting the home bed, >= 2 white**, heads to
a solid, glass or bars face (`ClinicWardGameTests`, `QaClinicTerrace1028GameTests`); no carpet on a slab; ridge cells
closed. A ladder to every furnished upper floor. No test pins its outer size (today 13 x 11 x 13).

## Rules tests check
Pinned to the drawing (the ship step moves them, keeping what each checks):
- `WorkplacesGameTests.workplaceBuilt_clinic` (above); `QaClinicTerrace1028GameTests.qaABuiltTurnedMirroredClinicHasFourBedsVillagersCanSleepIn`;
  `QaWindowsAndWards1028GameTests.qaABuiltClinicHasWholeBedsAndAClosedRidge`; `ClinicWardGameTests` (3);
  `QaClinicTerrace1028GameTests.qaNoBuildHasASingleSlabAndCarpetStandInBed`, `qaEveryClinicLookHasTheSpecsCountOfWholeBeds`.
- Refresh from old drawings: `QaRefreshHistory1028GameTests` (pre-B639 Clinic and village Clinics refreshed to today's:
  today's has **more bed blocks** than the old, every new bed cell gets a real bed), `QaClinicTerrace1028BugTests`,
  `QaReleaseRefresh1028GameTests` (history key `2f10c0ccc`), compat `ModdedClinicRefreshCompatTests` and
  `QaModdedRefresh1115cCompatTests` (**>= 6 white-bed cells where 0.151.0 had none**, more white-bed blocks than it; the
  refreshed modded Clinic keeps modded blocks; the 9,000-tick builder refresh). `history.py` must save the old drawings
  under `blueprint_history/clinic/`, `modded/clinic/`, `village/<style>_clinic/` (they exist).
- Scene `FurnishedHomeScene` (`11_clinic_ward`, `12_clinic_ceiling` cameras; it checks >= 4 beds and the old ridge cells
  (x 1-11, 8, 8) and (6, 9, z 2-6)): the ship step re-aims and re-points them; `tools/showcase/scenes.py` `furnished_home` text.
By id only: `WorkplacesGameTests.workplaceRule_clinic_nurse` / `_cleric` (civic, Hamlet), `StewardCivicRulesGameTests.twoIllAndNoNurseGetAClinic...`,
`ServiceGameTests`, `ClassGameTests`, `QaClassPerksGameTests`, `VillageGameTests` (pool, house names), `PieceLooksGameTests`,
`QaHouseLooks1108c`/`QaHouseLooksVerify1108b` (`outsideId("snowy", "clinic")`), `QaBlueprintLibraryVerify1108bGameTests`,
`QaQuestbookClaims1116eGameTests`, `QaWorkplaceCopiesGameTests`, `ModdedLibraryGameTests`, `QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites`.
Library-wide: `QaBuildReachGameTests`, `QaDoorwayClearanceGameTests`, `QaMirroredBuildsGameTests`, `QaTurnedFramesGameTests`,
`B648GameTests` / `QaWindowsAndWards1028BugTests` (no pane or bar run ending in air), `BlueprintsStayGameTests`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `QaMaterialListGameTests`, `StyleGameTests.chimneysStayWholeInEveryStyle`
(only with a campfire on a stack). The owner's rules (`check.py`) and STYLE.md: `rebuild.py --candidate` prints no CHECK
line and `holes.py` passes (`clinic` has no `owner_rules_baseline.json` entry; keep it that way).

## Free
Everything not listed above: shape, plan, roof form (beyond one slab-over-stairs ridge run of 6+ closed cells), materials,
colors, storeys, rooms, where the stand, chest and beds go (within the reaches above), extra beds and chests, lighting.
