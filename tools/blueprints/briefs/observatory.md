# Observatory: the brief

Family id(s): `observatory` (a Wonder, `aliveworkplace:observatory`; never renamed). Its drawing is four stage files,
`structure/wonder/observatory/stage_<n>_<k>.nbt`, laid at the offsets its data file
`data/aliveworkplace/wonders/observatory.json` lists, which `tools/blueprints/wonders.py` (`save_wonder`, the
`WONDERS["observatory"]` entry) writes from the drawing so the two always agree. The levels below are its stages.
Variants: village (none), modded (none), looks (none: a Wonder Plan can only be mirrored, `WonderPlanItem`; it keeps its
own palette). Not in this family: the Sundial build (`briefs/sundial.md`) and the Map Room (`briefs/map_room.md`).

ROADMAP 52.6 (one item per Wonder). The Wonder system itself (`wonder/`) does not change.

## What it is for
A **Wonder** (Milestone 35): a village-scale monument a Town builds once, stage by stage, over many play sessions. The
player draws up the plans on the Village Hall's Wonders page (32 emeralds; needs Town rank, the Seer Legend or a
Master Cleric stand-in, and the Master Architect or a Master builder, `QaWonders1101GameTests`), places the Wonder Plan
(see-through preview of the whole Wonder), and hands it to a builder. A Foundation Stone goes down outside the front
corner; the chests round it are the stockpile. Each stage's parts become ordinary build sites worked by a crew of up to
12 builders, each on its own column; a stage starts only when the one before is finished and its goods (rare found
things and workshop luxuries, `tools/blueprints/wonder_goods.py`) are dedicated at the stone with a ceremony. When the
last stage is done the server is told, the village holds a festival with fireworks from the Wonder, and its powers
come on:

- **Sky Chart** (`sky_chart`, size 6,144): the hall searches the land round the village for structures, and the hall's
  map button draws a chart of them (`ObservatoryPowers`, `SkyChart`).
- **Coming days** (`forecast`): the hall's calendar page lists what is ahead (festivals, market, weddings, caravans,
  travelers, the full moon, raid odds).

Neither power reads a single block of the drawing: they need only that the Wonder is **finished and standing**. Once a
day `WonderUpkeep` counts every block of every stage; under 90% standing (`wonderStanding`) the Wonder is damaged, its
powers stop and the builders repair it first.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| Stage I | `wonder/observatory/stage_1_*` | The ground stage: clears the whole site (stage I is saved whole, air included) and puts up the first part of the Wonder, tidy and usable on its own | Plans: Town, Seer (or a Master Cleric), Master Architect; goods: Heart of the Sea, Ghast Tear (found), 2 Spyglasses (Toolsmith) |
| Stage II | `wonder/observatory/stage_2_*` | More of the Wonder, built on stage I | Echo Shard, Eye Armor Trim, music disc Precipice (found), 2 Illuminated Books (Printer) |
| Stage III | `wonder/observatory/stage_3_*` | More, built on I and II | Ominous Trial Key, Totem of Undying, Wither Skeleton Skull (found), 2 Gold Circlets (Jeweler) |
| Stage IV | `wonder/observatory/stage_4_*` | The last of it; when done the Wonder is finished and its powers come on | Nether Star, Elytra, Shulker Shell (found), 2 Noble Robes (Tailor) |

**Exactly four stages.** The goods are four lines in `wonder_goods.py` and `save_wonder` asserts one stage drawn per
goods line; the goods, requirements and powers don't change in the rebuild.

**Stage titles are player text** (`wonder.aliveworkplace.observatory.stage.1`..`4` in `en_us.json`, keys never
renamed), checked word for word by `QaWonderDesignsGameTests.qaWonderNames_observatory` (its `TITLES`) and
`QaWonders1101GameTests.qaRequirementsAsTheItemAsks1101_observatory`: today "The Terrace and the Courtyard", "The Tower",
"The Dome", "The Great Telescope and the Orrery". Each title must name what that stage builds. If the new stages build
something else, the ship step writes new titles (American English, no `%`, no double spaces) and changes the lang lines
and both tests together; the description line (`...observatory.description`, which today describes the old look) is
rewritten too and must end in a full stop.

## Must have (each level)
- **Job blocks: none required.** No code gives this Wonder a worker. Every vanilla or mod job block drawn in it is a
  real job site the village's villagers will claim (a lectern for a Librarian, a cartography table for a Cartographer,
  a brewing stand for a Cleric, a barrel for a Fisherman, and so on). That is allowed; choose them knowing it.
  - Test pin (old spec, see "Rules tests check"): `qaWonderFeatures_observatory` today asks for at least one
    `minecraft:cartography_table` and at least one `minecraft:lectern`.
  - Never a retired job block (`StationsSpecGameTests.gone()`: Builder's Bench, Miner's Bench, Chopping Block, Fruit
    Basket, Apiary, Flower Stand, Scholar's Desk, Sieve, Tinker's Bench, Compost Bin, Nether Brazier, Undertaker's Table,
    Inn Counter, Teacher's Desk, Feed Trough, Carpenter's Bench, Kitchen Stove, Postal Desk, Guard Post, Nurse Station,
    Music Stand, Leader's Podium, Tutor's Desk, Ball Workbench, Trade Board, Fossil Lab).
- **Beds and residents: none required.** No code counts a resident here. A bed drawn is a vanilla village bed and must
  be whole (foot and head, same bed, same facing: `qaWonderBedsWhole_observatory`, B537; the old one has one).
- **Storage: none required.** No job reads a chest in a Wonder; the stockpile is the chests the player sets round the
  Foundation Stone, outside the drawing. Any container drawn is empty (`qaWonderNothingFree_observatory`).
- **Furniture kinds: none.** There is no `blueprint_furniture/observatory.json`, and Wonder parts record no furniture
  spots.
- **Doors:** every door whole (lower and upper half, same door, same facing) with two clear blocks on **both** sides
  (the cell in front and behind, feet and head: no collision, a carpet or pressure plate underfoot allowed),
  `qaWonderDoorways_observatory`.
- **A way in from the front (z = 0).** Not a test, but the plan, the stone, the ceremony and the festival all happen in
  front, and the village walks up to it from there.
- **Every block a player can get, no calcite** (`wonderMaterials_observatory`): no bedrock, barrier, command or
  structure block, structure void, jigsaw, spawner, trial spawner, vault, end portal frame, reinforced deepslate,
  budding amethyst, petrified oak slab, light, infested blocks, player head, dragon egg, nor any block without an item
  (still water is fine: a builder pours it from a bucket). No entity, no jigsaw, no structure void.
- **Nothing for free** (`qaWonderNothingFree_observatory`): no container, lectern, chiseled bookshelf, hive,
  composter or brewing stand holds anything (no book on a lectern, no bottles in a stand, no honey, no compost).
- **Nothing that changes by itself** enough to drop the daily count under 90%: `WonderUpkeep` matches every block as
  drawn (neighbour-dependent properties and copper weathering or waxing excepted). Fire, flowing water, falling blocks,
  growing crops and the like would read as damage.

## Fits
- **Plot: about 31 x 31 (30 to 32 each way: `qaWonderSizes_observatory` measures the whole Wonder's x and z extent, 31
  plus or minus 1).** Height: no test caps the whole (the old one is 42 tall); see the tower pin below.
- **y = 0 is the clicked block's top, and the Wonder starts there** (`qaWonderSizes_observatory`: lowest block y 0).
  The front is the z = 0 side.
- **Parts:** each stage is cut into parts of at most 48 a side (`wonderPartsLoad_observatory`; the kit does it,
  `kit.wonder_stages`), named `wonder/observatory/stage_<n>_<k>`, none empty (only stage I may hold a part of air
  alone). At 31 x 31 each stage is one part.
- **Stages only add** (`wonderStagesOnlyAdd_observatory`, `kit.StagedBuild`): a later stage never sets air, or a
  different block, where an earlier stage put one. The builders would have to break finished work.
- **Every stage ends tidy, standing and grounded**, because the village lives beside each stage for days:
  - `qaWonderStagesGrounded_observatory`: after every stage, every block joins the ground (y = 0) through blocks that
    touch it, edges and corners counted. Nothing waits in the air for a later stage to hold it up.
  - `qaWonderStagesStand_observatory`: built for real stage by stage on stone, every block of every stage so far can
    stay by the game's own rules (plants on soil, lanterns, bells and chains hung from something, banners and ladders on
    a wall, nothing that falls over air).
- **Ground levelling:** only the last stage with a part standing on the Wonder's floor (its bottom at y = 0) levels the
  ground round the Wonder (`WonderStages.levelling`, 35.8a). Today that is stage I (II and IV start at y 4, III at
  y 34).
- **Placing:** the plan's centre must be within 64 blocks of the hall, every corner within 96, and it may not cover a
  finished building, a build in progress or another Wonder (`WonderPlans.placeRefusal`). It goes down in all four turns
  and mirrored (`qaPlanGoesDownCentredOnTheHall1101_observatory`), so nothing may depend on one compass direction.
- **Outside the front-left corner:** the Foundation Stone goes at template (-1, 0, -1), else (-2, 0, -1), (-1, 0, -2),
  (-2, 0, -2), (-1, 1, -1) (`WonderStages.STONE_SPOTS`); the stockpile chests stand within 8 of it, the bard plays two
  out from it and the guards line up three out. All of that is outside the plot; the drawing must not reach x or z
  below 0.
- **Build time:** no test builds the whole Wonder; the `wonder_observatory` showcase scene films its time-lapse. A
  crew of 12 works it, so size is limited by the block count below, not by one builder's day.
- **Neighbours:** none fixed. No village plan, Steward rule or renewal places it; the player chooses the spot.

## Rules tests check
Drawing-independent (pass as long as the file loads and the four stages and goods stay):
`WonderBlueprintGameTests.wonderFileLoads_observatory`, `ObservatoryPowerGameTests` (all five),
`SkyChartGameTests.theObservatoryChartsSixThousandBlocks`, `QaWonderPowersGameTests.theLibraryAndObservatoryStayInTheirVillages`,
`QaWonders1114cGameTests` (powers, names, config), `QaConfigIds1116dGameTests`, `StadiumGameTests.theStadiumNeedsCobblemon`
(only that the Observatory loads), `QaWonders1101GameTests.qaWholePlanIsTheStagesStanding1101_observatory` (the plan's
whole blueprint is exactly the stages standing: automatic).

Function rules on the drawing (every one must pass as written):
- `WonderBlueprintGameTests.wonderPartsLoad_observatory`: every part loads, holds blocks, at most 48 a side, in
  `wonder/observatory/`, never in the Blueprint Table; the name, description and every stage title have a lang line.
- `WonderBlueprintGameTests.wonderStagesOnlyAdd_observatory`: no part sets air over an earlier stage's block.
- `WonderBlueprintGameTests.wonderBlockCount_observatory`: **8,100 to 9,900 solid blocks** in all (9,000 plus or minus
  10%; the old one has 8,438). A different budget needs the `Design("observatory", 9_000)` figure changed at ship, a
  decision to note.
- `WonderBlueprintGameTests.wonderMaterials_observatory`: the material rules above.
- `QaWonderDesignsGameTests`: `qaWonderStagesGrounded_observatory`, `qaWonderStagesStand_observatory`,
  `qaWonderDoorways_observatory`, `qaWonderNothingFree_observatory`, `qaWonderBedsWhole_observatory`,
  `qaWonderNames_observatory` (all described above).
- `QaWindowsAndWards1028BugTests.qaNoShippedGlassEndsInTheAirAsTheGameJoinsIt`: in the whole Wonder, joined as the game
  joins them, no glass pane or iron bars joined on one side with air on the other. `B648GameTests.b648NoShippedGlassEndsInTheAir`
  reads each part as stored, by the same rule.
- `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds` (reads every part): a standing lantern, candle or
  torch stands on a top that holds its middle (not a lectern, chest or bottom slab); no dirt path or farmland under a
  block the game counts as solid.

**Old-spec pins** (`QaWonderDesignsGameTests`, written from the 35.17 text; they describe the old look, not a function.
Don't design to them. The ship step rewrites the `case "observatory"` lines of `sizes` and `features` to state the new
design's own sizes and named features, keeping the kind of check, and notes it in the ROADMAP):
- `qaWonderSizes_observatory`: width and length 31 (plus or minus 1, kept above as the plot); every block at y 20 within
  a 15 (plus or minus 1) square box (the "tower 15 across"); the lowest door inside that box sets a floor, the highest
  block named `*blackstone*` above y 10 in it is 30 (plus or minus 2) over that floor; the lowest `tinted_glass` is
  above that top minus 2.
- `qaWonderFeatures_observatory`, each at least: 3 `tinted_glass`, 12 `cut_copper`, 1 `cartography_table`, 1
  `lectern`, 1 `gold_block`, 3 `chain`, 4 `amethyst_block`, 500 `deepslate_bricks`/`deepslate_tiles`, 100
  `polished_blackstone`/`polished_blackstone_bricks`, 4 `smooth_stone_slab`/`smooth_stone`.

The owner's rules (`check.py`, run per stage by `save_wonder` on the stages before it: posts at fence and wall corners,
lamps on tables or posts or hung, crockery only by a stove or a table with a chair, no bed right inside a door,
`open_panes`), STYLE.md's checks and `holes.py`. The old stages have 2, 18 and 3 baselined problems
(`owner_rules_baseline.json`, `wonder/observatory/stage_1_0` to `stage_3_0`); the rebuild has none and the ship step
removes those lines.

Ship-step updates (not the designer's): `wonders.py`'s `observatory` drawing (or its replacement feeding `save_wonder`),
the data file's part offsets (written by `save_wonder`), the size in `WonderGalleryScene` (`new Vec3i(31, 43, 31)`),
the old-spec pins above, the stage titles and description if they change, `docs/wiki/wonders.md` (its 35.17 paragraph
describes the old look) and `docs/guide/observatory.md`. A Wonder site already begun keeps building the parts it saved;
check that one begun on the old drawing still finishes (REBUILD.md: sites mid-build keep their old drawing).

## Free
Everything not listed above: the plan, the massing, what each stage builds (within four stages that each end grounded
and tidy, titled for what they build), the rooms and whether it has any, the materials, colors, roof and whether there
is a telescope, a dome, a tower or a courtyard at all, job blocks and beds or none, lighting, and its height.
