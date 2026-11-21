# Hanging Gardens: the brief

Family id(s): Wonder `aliveworkplace:hanging_gardens` (never renamed; "Hanging Gardens" in `en_us.json`), drawn as its
stage parts `structure/wonder/hanging_gardens/stage_<n>_<k>.nbt` and listed in `data/aliveworkplace/wonders/hanging_gardens.json`
(the whole plan is `aliveworkplace:wonder/hanging_gardens`, put together from the parts by `WonderBlueprints`).
Variants: village (none), modded (none), looks (none: only the Stadium takes village colors; no `blueprint_furniture/`
file, no style is applied to it).

## What it is for
A Wonder (ROADMAP Milestone 35): the village's great garden, raised by a crew of up to 12 builders over four stages, each
stage starting once the one before is built and its goods are handed in at the Foundation Stone (`WonderStages`,
`WonderGoods`). Asked for by a Town with the Grand Chef (stand-in: a Master Chef and 8 kinds of meal in the store).
Once finished and standing (at least 90 % of its drawn blocks in place, `WonderUpkeep`, config `wonderStanding`) it
gives two powers (`GardensPowers`): **harvest** (every farmer and Orchard Keeper in its village and its realm brings in
one more of each crop for every four picked) and **births** (in its own village a baby needs 12 meals in the store, not
16, and up to two are born a day). Below 90 % it is damaged: both powers stop and the village's builders repair it
first. The drawing itself does one job the code reads: **its farms are village farms** (`farm/Fields`): a farmer whose
job site is one of its composters takes on the field by that composter by themselves and harvests it into the chest
beside it, with the Gardens' extra. Players walk it from the ground to its top.

## Levels
The levels are the Wonder's four stages, drawn together as one building that grows: each stage adds to the stages before
it and never takes a block away.

| Level | Part ids | What it adds (function, not looks) | Cost / unlock the code reads |
|---|---|---|---|
| I | `stage_1_<k>` | Clears the whole site (stage I is saved whole, air included: the only part allowed to be air alone) and builds the first of the farm corners (today: one farm per stage, see "Rules") | The plan: 32 emeralds, a Town and the Grand Chef (`hanging_gardens.json` requirements; the loader adds the Master Architect). Goods (`tools/blueprints/wonder_goods.py`): a Sniffer Egg, a Wild Armor Trim, 2 Berry Wine |
| II | `stage_2_<k>` | The next farm corner and whatever rises on stage I | Goods: a Pitcher Pod, Torchflower Seeds, a Heart Pottery Sherd, 2 Vintage Wine |
| III | `stage_3_<k>` | The next farm corner | Goods: an Enchanted Golden Apple, a Heart of the Sea, a Sponge, 2 Gold Circlets |
| IV | `stage_4_<k>` | The last farm corner and the top garden; the Wonder is finished, so the powers start | Goods: a Totem of Undying, a Nether Star, a Conduit, 2 Noble Robes |

Exactly **four** stages: `wonders.py save_wonder` asserts as many stages as goods lists (four in `wonder_goods.py`), and
the goods, requirements and powers in the data file are not the designer's to change.

## Must have (each level)
- **Job blocks: exactly 4 `minecraft:composter`** in the whole Wonder, no more (`WonderGardensGameTests.aVillageFarmerTakesUpATerracesFarm`
  asserts `== 4`; the showcase puts a farmer at each). A composter is the vanilla Farmer's job site: a jobless villager
  takes it by itself. Each is a **farm corner**:
  - **a `minecraft:chest` within 2 blocks (Manhattan) of it**, in the drawing (same test). The code's own reach for the
    harvest chest is 8 across and 4 up or down (`SupplyContainers`), so 2 is the binding one.
  - **farmland within 16 blocks across and 4 up or down** of it (`Fields.farmNear`). The farmer takes on the farmland
    nearest the composter and everything joined to it (side by side, one up or down, or across a water cell at the same
    level), at most 32 a side. So the nearest farmland to each composter must be the field that composter is meant to
    work, and the four fields should not join into one.
  - Each composter empty (no compost level), each chest empty (`QaWonderDesignsGameTests.qaWonderNothingFree_hanging_gardens`).
- **At least 4 chests** (`qaWonderFeatures_hanging_gardens`), and **every chest in the drawing reachable on foot** from
  the ground outside (`QaWonderDesigns1105GameTests.qaGardensRampReachesEveryFarmAndTheCrown`; see "Rules"). Any extra
  chest must be reachable too.
- **Crops**: the fields hold `wheat`, `carrots` and `beetroots`, at least one of each (`qaWonderFeatures_hanging_gardens`).
  Builders plant them young (B493); a crop's age, a berry bush's berries and the like don't count against upkeep
  (`MaterialRules` ignores `age`, `berries`), so harvesting never damages the Wonder.
- **Water for the fields**: each field's farmland needs water near it (vanilla: within 4 across, same level or one up)
  or it dries. Put a field's water in the **same stage** as its farmland: B139 was farmland laid in one stage drying to
  dirt before its water came (`WonderCrewGameTests.aBlockThatChangesByItselfIsNotLaidAgainAndAgain`). No farmland or dirt
  path under a solid block (`BlueprintsStayGameTests`: the game turns it to dirt; it was this Wonder's paths, B539).
- **At least 20 still water blocks** in the drawing (`qaWonderFeatures_hanging_gardens`), poured from buckets by the
  builders (still water counts as obtainable). Every drawn water block must stay water when it runs: the showcase checks
  at least 90 % of them are still water the next morning (`WonderGardensScene.checks`). Water that can spill sideways
  onto a walk or a field floods the garden: any water that leaves its cell must only fall, and land in water.
- **No beds, no other job block.** Nothing in the drawing pulls a villager to another job (barrels, lecterns, bells,
  smokers, the mod's job blocks); no retired job block (`StationsSpecGameTests.gone()`). Beehives are allowed (no job
  takes one by itself, `Stations.ONLY_BY_ITEM`), empty of honey and bees (`qaWonderNothingFree`).
- **Furniture kinds**: none (a Wonder has no `blueprint_furniture` file).
- Nothing on the Foundation Stone's spot: the stone goes **just outside the front-left corner, template (-1, 0, -1)**
  (then (-2, 0, -1), (-1, 0, -2), (-2, 0, -2), (-1, 1, -1)), on replaceable, dry ground (`WonderStages.stoneSpot`), and
  the stockpile is the chests players set within 8 of it. Keep the front-left corner's outside clear and walkable.

## Fits
- **Plot: at most 47 (x) wide, 44 (z) deep, 33 tall** (the stage parts are all drawn at that size today; the whole's
  blocks fill 47 x 43, 31 high). Smaller is fine (see the block count). Every **part at most 48 a side**
  (`WonderBlueprintGameTests.wonderPartsLoad_hanging_gardens`): `kit.wonder_stages` cuts each stage into parts itself.
- **Block count: more than 15,000 and at most 16,500** non-air blocks over all stages. Two tests fence it:
  `WonderBlueprintGameTests.wonderBlockCount_hanging_gardens` (within 10 % of 15,000) and
  `WonderGardensGameTests.aVillageFarmerTakesUpATerracesFarm` (more than 15,000 placed blocks). It also keeps the
  `wonder_hanging_gardens` time-lapse inside its time on GitHub (376 s today).
- y = 0 sits on the clicked block; the front is the z = 0 side, the side the player faces when placing the plan.
  Placement: the plan's centre within 64 of the Village Hall and every corner within 96 (`WonderPlans`), never over a
  finished building, a build going on or another Wonder; `QaWonders1101GameTests.qaPlanGoesDownCentredOnTheHall1101_*`
  places it centred on a hall in four turns and mirrored, so it must work turned and mirrored.
- **Front and way in**: a player and the villagers walk in from the ground round the site (any side may do; the front is
  where the stone and its stockpile chests stand, so a way in from the front-left is the natural one).
- **Neighbours**: none placed by a planner; the village's own buildings stand round it on the hall's land. The last
  stage with a part on the floor (y 0) levels the ground round the Wonder, with those parts only (35.8a).

## Rules tests check
Each stage, as it stands on the stages before it (no builder; the parts put together from the data file):
- `WonderBlueprintGameTests` (`wonderFileLoads_`, `wonderPartsLoad_`, `wonderStagesOnlyAdd_`, `wonderBlockCount_`,
  `wonderMaterials_hanging_gardens`): the file loads, 4 stages each with goods; every part loads, holds blocks (only
  stage I may be air alone), is at most 48 a side and lives in `wonder/hanging_gardens/`; no part sets air where an
  earlier stage put a block; the block count above; no retired job block, **no calcite**, nothing a player can't get.
- `QaWonderDesignsGameTests`: `qaWonderStagesGrounded_` (after **every** stage each block joins the ground at y 0
  through blocks touching it, edges and corners counted: nothing waits in the air for a later stage),
  `qaWonderStagesStand_` (built for real stage by stage on stone, every block can stay by the game's rules: plants on
  soil, crops on farmland, hanging plants hung, lanterns hung or standing on a full top, nothing that falls over air),
  `qaWonderDoorways_` (every door whole, two clear blocks on both sides), `qaWonderNothingFree_`, `qaWonderBedsWhole_`.
- `QaWonderDesigns1105GameTests.qaGardensRampReachesEveryFarmAndTheCrown`: walking in from the ground round the site
  (steps up one block onto a stair or slab, or onto a full block with headroom; drops of up to three; doors, gates and
  trapdoors pass), a player gets within 2 across and 1 up or down of **every chest**, and stands somewhere at **y 24 or
  higher**.
- `WonderGardensGameTests.aVillageFarmerTakesUpATerracesFarm` (the whole built in `colony_area` at helper (0, 2, 0)):
  4 composters, each with a chest within 2 and a field in reach; a farmer at the first farm's composter takes on its
  field by himself, harvests at least 4 and brings in an extra. **Pinned to the old drawing:** the composter at
  (3, 7, 5) and the farmland at (5, 6, 5); the ship step moves both to the new first farm.
- The glass rules over the whole Wonder (`QaWindowsAndWards1028BugTests`: no pane or bar ending in open air, no lone
  glass post) and `BlueprintsStayGameTests` (lantern tops, farmland and paths). The owner's rules (`check.py`, run by
  `save_wonder` on each stage), STYLE.md's checks and `holes.py`. `owner_rules_baseline.json` lists
  `wonder/hanging_gardens/stage_1_0` to `stage_4_0` (3, 3, 5, 7): the new drawing has none and the ship step removes them.
- **Pinned to the old design's spec, rewritten by the ship step to the new design (with the wiki's "Hanging Gardens"
  paragraph and the description line in `en_us.json`), keeping each check's kind:**
  - `QaWonderDesignsGameTests.qaWonderSizes_hanging_gardens`: the four levels' widths measured at fixed heights in one
    building material, and **exactly one chest and one composter on each level's top** at fixed heights (one farm per
    level). Keep one farm corner per stage unless the design has a reason not to; the test is rewritten to the new
    heights either way.
  - `qaWonderFeatures_hanging_gardens`: the chests, composters, crops and water above are function; the rest of its
    list names the old design's plants, trees, hives and two building materials.
  - `QaWonderDesigns1105GameTests.qaGardensFallsRunFromTheCisternToThePool` (water at the top, on each level between,
    and in a pool at the foot) and `qaGardensFootAndHeightAsTheWikiSays` (the old footprint and height).
  - `qaWonderNames_hanging_gardens`: the stage titles, word for word. The designer proposes four stage titles that
    name what each new stage builds; the ship step puts them in `en_us.json` and the tests.
  - Scene `wonder_hanging_gardens` (`WonderGardensScene`: camera at template (64, 26, 44) looking at (25, 12, 22), a
    farmer at each composter, all four must take on a farm) and `wonder_gallery` (`WonderGalleryScene`, size
    47 x 33 x 44): re-aimed at ship.
- By id or data only (the drawing doesn't matter): `WonderGardensGameTests` (other three), `QaWonderPowersGameTests`,
  `QaWonders1114cGameTests`, `QaConfigIds1116dGameTests`, `QaBuilderFixes1107d/1111b` (no stage handed out at the Table).

## Saves and shipping (not the designer's, but they bound the design)
- `WonderUpkeep.blocks` counts a finished Wonder against **today's** stage parts, and a site mid-build takes its next
  stage from today's parts. A Hanging Gardens already standing on the owner's live server would read as under 90 %
  (damaged, powers off, builders "repairing" it into the new design), and a half-built one would get new stages on its
  old stage I. Before shipping, the lane must keep standing and half-built Gardens on their old parts (old parts in
  `blueprint_history/wonder/hanging_gardens/`, as `Refreshes.olderDrawing` does for the Lighthouse's tower, B656), or
  file it as a bug (same root as B1374).
- `generate.py` saves every Wonder through `wonders.save_wonder`: the ship step stops it redrawing this one. Saving uses
  `kit.wonder_stages`, which takes **one drawing per stage at one size, each the whole Wonder as it stands after that
  stage**: draw the recipe so it can emit each stage's state.

## Free
Everything not listed above: the shape and plan, how the four stages grow (up, out or both), materials, colors, what
grows where beyond the three crops, whether there is water beyond the fields' and how it moves, the way up, rooms or
halls inside, lighting, and where the four farms sit (within their reaches). The look the roadmap item (35.19) once
described is not binding: the ship step rewrites the wiki, the description and the pinned tests to the new design.
