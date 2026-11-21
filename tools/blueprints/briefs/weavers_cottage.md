# Weaver's Cottage: the brief

Family id(s): `weavers_cottage`, `weavers_cottage_2` (never renamed; "Weaver's Cottage" and "Weaver's Cottage II" in
`en_us.json`, `blueprint.aliveworkplace.weavers_cottage` / `_2`, spelled exactly so:
`QaNewWorkplacesGameTests.qaNewWorkplaceNamesAndText`; hidden advancements `built/weavers_cottage` / `_2`)
Variants: village (none: no `structure/village/*weavers_cottage*`, no village pool names it), modded (none to draw:
`modded_library.py` makes `structure/modded/weavers_cottage.nbt` and `_2` from the vanilla drawings and the ship step
reruns it; `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`),
looks (none). The Table's style buttons (`BlueprintStyles`) restyle the drawing automatically; no test names a styled
cottage.

## What it is for
The Shepherd's workplace (ROADMAP 27.14). A Shepherd's job block is the vanilla **loom** (`PoiTypes.SHEPHERD`; a
jobless villager by a loom becomes a Shepherd; with config `tailors` on, string picks a Tailor there instead,
`Stations`, `Tailors`). In this mod the Shepherd does two jobs from that loom:
- **The flock** (`ranch/ShepherdWork` on `ranch/RanchWork`): every sheep within **16 blocks across and 6 up or down of
  his loom** is his (`RanchWork.RADIUS` 16, the `AABB(station).inflate(RADIUS, 6, RADIUS)`). He takes shears from his
  chests, walks to a sheep until he is within **2.5 blocks** of it (`RanchWork.REACH`) and shears it, picks up the wool
  and mutton dropped in that range, breeds pairs with wheat from the chests up to 8 a kind (`RanchWork.CAP`, more with a
  Herders' Guild), and carries it all to his chests. With no chest he stands idle and his status reads "no chest"
  (`RanchWork.tick`: `own.isEmpty()`).
- **Weaving for builders** (`craft/WeaverWork`, 46.2): wool, carpets, beds, banners from the `crafts/shepherd` list,
  made at the loom from his own chests and the village's, carried to the builder's chests.

His "own chests" are every storage block within **8 blocks across and 4 up or down of his loom**
(`SupplyContainers.find(level, station, ...)`, `RADIUS` 8, `VERTICAL` 4). A player picks the cottage at the Blueprint
Table (in every new village's starting set, `blueprint_unlocks/starting.json`) and hands it to a builder; the Steward
builds it by himself in a `workshops` zone when a Shepherd has no workstation (`steward_rules/workplace_weavers_cottage.json`:
`worker_without_workstation` for `minecraft:shepherd`, `wanted: true`; priority 62, cooldown 2 days), and his renewal
of an old Shepherd's house rebuilds it as this one (`steward_renewal/shepherd.json`: it must fit the old footprint plus
`Renewals.MARGIN` = 3 blocks a side, front where the old door was). On the Village Map it is a Workshops building
(`VillageMaps.kindOf`, by name). It counts nothing for beauty (`Decorations.points` 0,
`QaNewWorkplacesGameTests.qaOnlyTheBandstandIsADecoration`). Nobody lives in it.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `weavers_cottage` | One Shepherd's workstation (one loom), his chest by it, a sheep pen (at least 8 fences and a fence gate) | Starting set (`starting.json`); Steward rule `workplace_weavers_cottage`; renewal list `shepherd.json` |
| II | `weavers_cottage_2` | A **dye garden**: at least 8 more flower blocks than Level I (the dyes' flowers); still one loom, still the pen | By name (`BlueprintUpgrades`: `<name>_2` upgrades `<name>`). A builder who finishes Level I sells Level II (`UpgradeOffers.offer`, price `4 + solid blocks / 40`, clamped 6-32 emeralds) |

**Exactly two levels.** No `weavers_cottage_3`: a third level would change the counted upgrades in
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64) and is a new feature.

**Upgrade rules the code reads** (`BlueprintUpgrades`, `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`):
- Level II is built **over a finished Level I at the same origin and the same front** (only what changed is built), so
  Level I's cells must sit at the same template coordinates in Level II.
- Level II is **at least as big as Level I in x, y and z**.
- Level II **keeps at least 60 % of Level I's non-air blocks unchanged** (same block, same state, same cell). Draw the
  two together, Level II growing out of Level I.
- Level I's loom and chest stay in their cells in Level II (a Shepherd already working there keeps his block; a builder
  never breaks a container, `MaterialRules.isProtected`).

## Must have (each level)
- **Job block: exactly one `minecraft:loom` in each level** (`QaNewWorkplacesGameTests.qaHutCottageAndBandstandHoldTheirBlocks`:
  "looms ... the spec says 1"; `CountryWorkplacesGameTests.countryWorkplaceBuilt_weavers_cottage` / `_2`: one per
  worker). **No other job block**: that test counts each of blast furnace, smithing table, grindstone, stonecutter,
  fletching table, cartography table, **barrel**, lectern, **composter**, **cauldron**, smoker, brewing stand,
  **crafting table**, **jukebox** and **furnace** and wants 0 of every one. So no barrel for storage, no compost bin in
  the dye garden, no dye vat of a cauldron (a filled cauldron is a Leatherworker's job site too), no workbench, no
  furnace. None of the mod's workstations either (Blueprint Table, Builder's Bench, Flower Stand, ...): each takes a
  villager for another job.
- **The loom is reachable on foot from the street**: a real Shepherd spawned in front of the build walks in and claims
  it as his job site (`countryWorkplaceBuilt_*`, see "Rules tests check"), and it is within a player's reach (2
  across, 1 up or down) of a cell a villager walks to (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`).
- **The sheep pen: at least 8 blocks in `#minecraft:fences` and at least 1 in `#minecraft:fence_gates`, in BOTH
  levels** (`qaHutCottageAndBandstandHoldTheirBlocks`: "fences (the sheep pen)", "fence gates (into the pen)"). Walls
  don't count, and gates don't count as fences. Every fence run joins at its corners (`check.py` owner rule).
- **The pen within the Shepherd's range**: the code finds sheep only within 16 across and 6 up or down of the loom,
  and he shears from 2.5 blocks. Vanilla villagers don't open fence gates, so a sheep he can never get within 2.5
  blocks of is never shorn (the code reads this; no test builds a flock).
- **No gate or other barrier a villager can't pass on the way from the street to the loom.** Villagers open wooden
  doors but not fence gates or trapdoors; the reach test treats a gate as open, but the built test needs a Shepherd to
  walk from the street to his loom. A gate is fine only beside an open way in.
- **No entity in either file**: no sheep, villager, frame or painting drawn in (`countryWorkplaceBuilt_*`: "comes with
  a villager" asserts `blueprint.entities().isEmpty()`). Sheep come from the player or the village.
- **Storage.** At least **one chest within 8 across and 4 up or down of the loom** (the Shepherd's own chests: shears,
  wheat, wool, his weaving ingredients and what he brings back; with none he idles, "no chest"). No test counts it; the
  code reads it. Chests are drawn empty (builders never fill or copy containers). Every chest is reachable on foot
  (`QaBuildReachGameTests`) and an indoor chest stands against a wall, a solid block, a bed or another chest
  (`QaFurnishedRooms1031GameTests`). Not a barrel (see above).
- **Level II's dye garden: at least 8 more flower blocks than Level I** (`qaHutCottageAndBandstandHoldTheirBlocks`:
  counts blocks in `#minecraft:small_flowers` or `#minecraft:tall_flowers`, II >= I + 8). Potted flowers are not in
  those tags and don't count; each block of a two-block flower counts. Every flower I draws raises II's bar.
  Every flower stands on a block that keeps it (`BlueprintsStayGameTests`).
- **Beds and residents.** None required: no test or code asks this family for a bed, and the old cottage has none. A
  bed is free; if drawn, it counts as a village bed for the Steward (`StewardConditions.beds`), its head must be
  reachable on foot (`QaBuildReachGameTests`), its foot has its head one block towards its facing
  (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`) and it stays whole turned and mirrored
  (`QaMirroredBuildsGameTests.everyBuildsDoorsBedsAndDoubleChestsStayWholeTurnedAndMirrored`).
- **Furniture spots.** No kind is required (no `blueprint_furniture/weavers_cottage*.json` today; not a home,
  `FurnishedHomesGameTests` doesn't list it). Every furniture piece the drawing has is recorded as a spot
  (`rebuild.py`), and each spot's blocks are exactly the template's blocks there
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing small stands on air
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); every chair stair faces its table, turned and
  mirrored too (`QaChairFacingGameTests`).
- **A way in on the z = 0 side** (the front), at ground level, that a villager 1.95 blocks tall walks through; if it is
  a wooden doorway it has room for him (`QaDoorwayClearanceGameTests`; B170: a carpet just inside a 2-tall doorway
  stopped a worker for good). Any upper floor or loft holding the loom or a chest is reached by a ladder or stairs from
  inside (B149, `QaBuildReachGameTests`).
- No jigsaw or structure void; every block is one a builder can place
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Kept cells (every upgrade, and every refresh)
- **A refresh (36.21) never moves a container or a job block** (`Refreshes.kept`: protected block-entity blocks such
  as chests, slept-in beds, and every acquirable job site, which the loom is). Every older drawing has these, in
  template cells (x, y, z), in all of: the live `weavers_cottage.nbt` and `_2.nbt`, their modded copies, and
  `blueprint_history/weavers_cottage/` and `weavers_cottage_2/` `15acdea62.nbt` and `ac8dc5225.nbt`:
  - **loom (2, 1, 3)**, facing east;
  - **chest (6, 1, 3)**, single, facing west.
  - (The wall banners in the old Level II are not kept: banners are not protected and not job sites. No older drawing
    has a bed, barrel or furnace.)
- An old cottage refreshed to the new drawing keeps that loom and chest where they stand. So the new drawing must not
  put a door, a walkway, a wall or anything else on (2, 1, 3) or (6, 1, 3) that a kept loom or chest would block or
  break, and should not leave a refreshed build with **two looms** (the kept old one plus a new one elsewhere: a second
  loom takes a second villager, and the one-loom rule above). The cleanest answer is a loom at (2, 1, 3) and a chest at
  (6, 1, 3) in the new drawing; otherwise those two cells must be ones where a leftover loom and chest still stand on a
  floor, reachable, and the ship step must show the refresh of an old cottage ends with exactly one working loom.
  (`RefreshMatchesFreshBuildGameTests.differences` skips chests and job-site blocks, so the refresh tests won't catch a
  bad placement; nothing but this brief will.)
- `QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh`: every older drawing in
  `blueprint_history/weavers_cottage*/` that differs from the new one must be offered a refresh (automatic once the
  ship step keeps the old drawings there with `history.py`).

## Fits
- **Plot: at most 18 wide x 26 deep x 28 tall**, both levels (`CountryWorkplacesGameTests.countryWorkplaceBuilt_*`
  builds it at helper `HOUSE` (12, 2, 4) in the 30 x 30 x 30 `huge_area`, so x 12..29, y 2..29, z 4..29; the builder's
  bench at (3, 2, 3), the builder at (5, 2, 3) and his chests at x = 3, z 6/8/10 stand west of it). y = 0 sits on the
  clicked block; the front is the z = 0 side.
- **Material budget: at most 81 stacks per level** (that test stocks three chests of 27 with exactly what the supply
  report says is missing, each item rounded up to whole stacks; Level II is built from nothing in its own test, not
  over Level I). The old drawings are 448 and 572 blocks.
- **Build time:** the whole build, then the Shepherd walking in and claiming the loom, within **14,000 ticks** (build
  delay 2, builders' help off). A much bigger build risks it.
- **Declared size:** `StarterBlueprints.WEAVERS_COTTAGE` is `new Vec3i(15, 11, 11)` and `WEAVERS_COTTAGE_2`
  `new Vec3i(15, 11, 16)` today (no basement); if a size changes the ship step changes those lines (both
  `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` and `countryWorkplaceBuilt_*` assert the file's size equals
  the entry), and `modded_library.py` reads the sizes from that file. A basement is allowed only with the `Entry`'s
  basement field set (and then `QaBasementGameTests` applies).
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward plot; the way in is
  there. The arriving Shepherd waits **2 blocks in front of the front edge**, at template **(1, 0, -2)** (helper
  (13, 2, 2)), on the level of the build's y = 0, and must walk in from there to the loom.
- **Neighbours:** a workplace in a Steward `workshops` zone; the Steward keeps 2 blocks clear round its plot
  (`Plots.CLEAR`) and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). A renewal takes it only
  where it fits the old Shepherd's house's footprint plus 3 a side, so a smaller footprint renews more old houses
  (free).

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing if needed, keeping what each checks, dropping none):
- `CountryWorkplacesGameTests.countryWorkplaceBuilt_weavers_cottage` and `countryWorkplaceBuilt_weavers_cottage_2`
  (the `builds()` generator, `build(helper, w)`; `HOUSE` (12, 2, 4), `BENCH` (3, 2, 3), `CHESTS`, `BUILDER` (5, 2, 3)):
  the file's size equals the `StarterBlueprints` entry; no entity; job-block counts as above (exactly one loom, zero of
  the 15 others); built from its supply report (at most 81 stacks); then one Shepherd spawned at helper (13, 2, 2) with
  the trade's brain must hold a loom **inside the build's box** as his `JOB_SITE`, still a Shepherd, within the 14,000
  ticks.
- No test pins a cell inside the drawing (no loom, chest, pen or door coordinate is read by any test or Java class);
  where things stand is free within the rules above and the kept cells.

Content checks: `QaNewWorkplacesGameTests.qaHutCottageAndBandstandHoldTheirBlocks` (looms exactly 1 in each level;
fences at least 8 and fence gates at least 1 in each level; II's small or tall flowers at least I's + 8);
`BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (the upgrade rules above).

By id only (the drawing doesn't matter beyond the rules above): `CountryWorkplacesGameTests.countryWorkplacesAreInTheBlueprintTable`
(both in the Table, starter blueprints, on the Village Map as Workshops), the `rules()` generator's
`countryWorkplaceRule_weavers_cottage_shepherd`; `QaNewWorkplacesGameTests.rulesForTheirTradesOnly`
(`qaNewWorkplaceRules_shepherd`), `qaNewWorkplaceNamesAndText`, `qaOnlyTheBandstandIsADecoration`;
`QaBlueprintLibraryVerify1108bGameTests.aNewVillageStartsWithTheWikisSetAndTheRestLockedOrRare` (starting set);
`ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`, `withoutTheModsEveryBuildIsTheVanillaBuild`.

Library-wide checks it must pass: `B648GameTests` and `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests`
(no pane or bar joined on one side with air on the other, as stored and as the game joins it),
`QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every indoor chest against a wall, a solid
block, a bed or another chest), `BlueprintsStayGameTests` (lamps on a block that holds them; no dirt path or farmland
under a solid block; beds whole), `QaMirroredBuildsGameTests` (doors, beds and double chests whole, turned and
mirrored), `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaBuildReachGameTests`,
`QaLedgerFramesHaulGameTests` / `QaTurnedFramesGameTests` (frames and paintings hang turned and mirrored),
`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle`
(only if a campfire stands on a solid stack: it must stay solid in every style),
`QaRefreshHistory1028GameTests.qaEveryShippedOlderDrawingIsOfferedARefresh`.

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line for either level, and `holes.py` passes. The old `weavers_cottage_2` (and its modded copy) is listed in
`owner_rules_baseline.json` with 2 owner-rule findings each; the rebuild must come out clean and the ship step removes
those two baseline entries. Saves stay safe: the ship step keeps the old drawings in `blueprint_history/`
(`history.py`; a site begun on the old drawing finishes as drawn) and regenerates the modded copies with the family.

## Free
Everything not listed above: shape, plan, roof, materials, colors, how many storeys, a bed or none, where the loom,
chest, pen, gate, way in and garden stand (within the rules and kept cells above), the pen's size and shape, how many
chests beyond the minimum, what flowers and how the garden is laid out, a drying frame, banners or none, lighting, and
what Level II adds beyond its dye garden. (The wiki's one-line description in `docs/wiki/new-workplaces.md` is updated
by the ship step to match.)
