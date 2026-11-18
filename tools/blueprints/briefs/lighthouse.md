# Lighthouse: the brief

Family id(s): Wonder `aliveworkplace:lighthouse` (never renamed; "Lighthouse" in `en_us.json`), drawn as its stage
parts `structure/wonder/lighthouse/stage_<n>_<k>.nbt` and listed in `data/aliveworkplace/wonders/lighthouse.json` (the
whole plan is `aliveworkplace:wonder/lighthouse`, put together from the parts by `WonderBlueprints`).
Variants: village (none), modded (none), looks (none: only the Stadium takes village colors; no `blueprint_furniture/`
file, no style is applied to it).

## What it is for
A Wonder (ROADMAP Milestone 35) for a village on the coast: a light that shines over the sea at night, a ferry stop, a
place for a fisherman and a keeper. It is raised by a crew of up to 12 builders over four stages, each stage starting
once the one before is built and its goods are handed in at the Foundation Stone (`WonderStages`, `WonderGoods`). It is
asked for by a Town with the Merchant Prince (stand-in: 3 caravan routes running) whose hall has an ocean or beach biome
within 48 blocks (`coastal`; the Wonder itself may be placed anywhere the plan allows). Once finished and standing (at
least 90 % of its drawn blocks in place, `WonderUpkeep`, config `wonderStanding`) it gives four powers
(`LighthousePowers`): caravans reach twice as far and travel in half the time; ferry fares to and from the village's
Travel Posts are halved and **the Wonder's own Travel Post joins the ferry network as "<Village> Lighthouse"**; and the
keeper watches the sea: the day before a raid from the sea the village is told and at dusk the guards gather on the
village's beach (found from the world, `Ships.beach`, nearest the raiders' lair or else the Wonder's corner). Below 90 %
it is damaged: all four stop and the village's builders repair it first. The drawing's own jobs the code reads: the
Travel Post, and lamps that light by themselves at night.

## Levels
The levels are the Wonder's four stages, drawn together as one building that grows: each stage adds to the stages before
it and never takes a block away.

| Level | Part ids | What it adds (function, not looks) | Cost / unlock the code reads |
|---|---|---|---|
| I | `stage_1_<k>` | Clears the whole site (stage I is saved whole, air included: the only part allowed to be air alone) and builds the first stage | The plan: 32 emeralds, a Town, the Merchant Prince, `coastal` (`lighthouse.json`; the loader adds the Master Architect). Goods (`tools/blueprints/wonder_goods.py`): a Heart of the Sea, a Coast Armor Trim, 2 Emerald Brooches |
| II | `stage_2_<k>` | Free | Goods: a Trident, a Sponge, an Angler Pottery Sherd, 2 Gold Circlets |
| III | `stage_3_<k>` | Free | Goods: a Tide Armor Trim, a Conduit, a Totem of Undying, 2 Illuminated Books |
| IV | `stage_4_<k>` | Whatever completes the Must haves below (they must all stand once IV is done); the Wonder is finished, so the powers start | Goods: a Nether Star, an Elytra, a Ghast Tear, 2 Vintage Wine |

Exactly **four** stages: `wonders.py save_wonder` asserts as many stages as goods lists (four in `wonder_goods.py`), and
`QaWonders1101GameTests.qaRequirementsAsTheItemAsks1101_lighthouse` reads four stage titles. The goods, requirements
and powers in the data file are not the designer's to change. Which stage brings which Must have is free, but each
stage must end standing on its own (see "Rules").

## Must have (each level: by the end of stage IV)
- **The ferry stop: exactly one `aliveworkplace:travel_post`.** `LighthousePowers.jetty` takes the first Travel Post in
  the drawing and puts it on the network under the village's name (`LighthousePowerGameTests.theJettysPostJoinsTheNetwork`;
  the showcase checks it at midnight). The Travel Post is also the **Ferryman's job site**: a jobless villager takes it
  by himself, so it must be **reachable on foot** from the land. A player arriving by ferry is put on **a cell he can
  stand in within 3 blocks of the post** (3 across, 2 down to 3 up, `Ferrymen.landing`; else on top of it), and a
  ticket is used within 16 of a post. A ride sets off by boat where the player stands and is rowed only on water
  (`FerryRides`): put the post at the edge of the sea side, so the water a boat needs is beside it once the Wonder
  stands by the sea.
- **A light that comes on by itself at night**, the way `QaWonderDesignsGameTests.qaWonderLampsLightAtNight_lighthouse`
  reads it: **at least 1 `minecraft:daylight_detector`, every one inverted**, and **at least 8 `minecraft:redstone_lamp`**;
  built for real, every lamp is **off** by day, and **every** lamp is **on** once the detectors give their night signal
  (so each lamp is powered from a detector and from nothing else). In game a detector must see the sky. The lamps' lit
  state and the detector's power don't count against upkeep (`MaterialRules`). The `wonder_lighthouse` showcase checks
  at least half the lamps within 40 blocks are lit at midnight. At least 1 `minecraft:sea_lantern` (the features test
  counts it, and the sizes test finds the light by it; see "Rules").
- **A fisherman's job site: at least 1 `minecraft:barrel`** (the spec and the wiki promise it; `qaWonderFeatures_lighthouse`).
  A jobless villager takes it by himself and fishes in water **within 16 blocks of it** (`FisherWork.RADIUS`; with a
  boat in a chest by it he rows out within 24): put it near the sea side. Empty (`qaWonderNothingFree_lighthouse`).
- **The keeper's bed: one whole bed** (`qaWonderFeatures_lighthouse` counts 2 bed blocks of red, white, blue or brown;
  `qaWonderBedsWhole_lighthouse`: foot and head together, the same bed, the same way). A bed near the hall is a home
  a villager may claim, as any bed.
- **No other job block**: nothing that pulls a villager to another job (lecterns, bells, composters, smokers, the mod's
  job blocks), no retired job block (`StationsSpecGameTests.gone()`). Any chest drawn is empty and reachable on foot.
- **Doors**: every door whole with two clear blocks on both sides (`qaWonderDoorways_lighthouse`; the old drawing has
  eight). Every room or level a player is meant to reach is reachable by stairs or ladders.
- **Furniture kinds**: none (a Wonder has no `blueprint_furniture` file).
- **It must stand complete on dry ground.** The tests build it on bare stone with no sea (`qaWonderStagesStand_`,
  `qaWonderStagesGrounded_`), and a player may place it inland of the beach: nothing in the drawing may need water it
  doesn't bring itself (no lily pads, kelp, seagrass or coral leaning on the world's sea). It must also make sense with
  its sea side standing in water: the showcase floods the sea round it before building. Water drawn in the parts is
  allowed (poured from buckets, still water only).
- Nothing on the Foundation Stone's spot: the stone goes **just outside the front-left corner, template (-1, 0, -1)**
  (then (-2, 0, -1), (-1, 0, -2), (-2, 0, -2), (-1, 1, -1)), only on replaceable ground with no water in it
  (`WonderStages.stoneSpot`; none free: the Wonder can't be begun), and the stockpile is the chests players set within 8
  of it. So the **front-left corner must be the land side**.

## Fits
- **Plot: at most 25 (x) wide, 33 (z) deep, 46 tall** (the stage parts are all drawn at that size today, and the whole
  fills it). Every **part at most 48 a side** (`WonderBlueprintGameTests.wonderPartsLoad_lighthouse`; `kit.wonder_stages`
  cuts each stage into parts itself).
- **Block count: 4,500 to 5,500** non-air blocks over all stages (`WonderBlueprintGameTests.wonderBlockCount_lighthouse`,
  within 10 % of 5,000). It keeps the `wonder_lighthouse` time-lapse in its time on GitHub (172 s today).
- y = 0 sits on the clicked block; **the front is the z = 0 side, the land side, where the village is** (the wiki says
  so; the stone and its stockpile are there). The sea is behind it and to its sides: the showcase scenes dig and flood
  it from some depth back (re-aimed to the new drawing at ship, see "Rules").
- Placement: the plan's centre within 64 of the Village Hall and every corner within 96 (`WonderPlans`), never over a
  finished building, a build going on or another Wonder; `QaWonders1101GameTests.qaPlanGoesDownCentredOnTheHall1101_lighthouse`
  places it centred on a hall in four turns and mirrored, so it must work turned and mirrored.
- **Front and way in**: from the land at the front; the Travel Post, the barrel, the bed and the light reached on foot
  from there.
- **Neighbours**: none placed by a planner. The last stage with a part on the floor (y 0) levels the ground round the
  Wonder, with those parts only (35.8a).

## Rules tests check
Each stage, as it stands on the stages before it (no builder; the parts put together from the data file):
- `WonderBlueprintGameTests` (`wonderFileLoads_`, `wonderPartsLoad_`, `wonderStagesOnlyAdd_`, `wonderBlockCount_`,
  `wonderMaterials_lighthouse`): the file loads, 4 stages each with goods; every part loads, holds blocks (only stage I
  may be air alone), is at most 48 a side and lives in `wonder/lighthouse/`; no part sets air where an earlier stage put
  a block; the block count above; no retired job block, **no calcite**, nothing a player can't get.
- `QaWonderDesignsGameTests`: `qaWonderStagesGrounded_` (after **every** stage each block joins the ground at y 0
  through blocks touching it, edges and corners counted: nothing waits in the air for a later stage, a walk out over
  the sea included), `qaWonderStagesStand_` (built for real stage by stage on stone, every block can stay by the game's
  rules: redstone dust on a full top, lanterns hung or standing on a full top, plants on soil, nothing that falls over
  air), `qaWonderDoorways_`, `qaWonderNothingFree_`, `qaWonderBedsWhole_`, `qaWonderLampsLightAtNight_lighthouse` (above).
- `LighthousePowerGameTests.theJettysPostJoinsTheNetwork`: a Travel Post in the drawing (placed at hall + (8, 0, 8));
  built, it joins as "<Village> Lighthouse", not while damaged.
- The glass rules over the whole Wonder (`QaWindowsAndWards1028BugTests`: no pane or bar ending in open air, no lone
  glass post) and `BlueprintsStayGameTests` (a standing lantern, candle or torch on a top that holds it). The owner's
  rules (`check.py`, run by `save_wonder` on each stage), STYLE.md's checks and `holes.py`. `owner_rules_baseline.json`
  lists `wonder/lighthouse/stage_2_0`, `_3_0`, `_4_0` (8, 30, 12): the new drawing has none and the ship step removes them.
- **Pinned to the old drawing or the old design's spec, rewritten by the ship step to the new design (with the wiki's
  "Lighthouse" paragraphs and notes, and the description line in `en_us.json`), keeping each check's kind:**
  - `QaWonderDesignsGameTests.qaWonderSizes_lighthouse`: the whole's width, length and height within 1 of the old
    figures, the tower's widest and narrowest measured by one building material round the sea lantern, and a gallery
    level. Rewritten to the new design's stated sizes.
  - `qaWonderFeatures_lighthouse`: the sea lantern, lamps, detector, bed, barrel and Travel Post above are function; the
    rest of its list names the old design's materials and fittings.
  - `QaLonePanes1031GameTests.qaEveryLighthouseWindowStandsInTheWallInOneColumn` (the old tower's window rows and
    centre) and `qaTheRedrawnLighthouseTowerKeepsItsSizeAndPlace` (the old stage II part's offset (5, 3, 14), size and
    its `blueprint_history` v0.151.0 copy): both guard B656's window fix in the old tower. Keep their intent on the new
    drawing (no two-tall window with a pane standing out of its wall); the ship step rewrites or retires them.
  - `qaWonderNames_lighthouse` and `QaWonders1101GameTests.qaRequirementsAsTheItemAsks1101_lighthouse`: the stage titles,
    word for word. The designer proposes four stage titles that name what each new stage builds; the ship step puts them
    in `en_us.json` and both tests.
  - Scenes `wonder_lighthouse` (`WonderLighthouseScene`: template 25 x 33, sea dug from z 11 at x 15 on and past the
    back) and `wonder_gallery` (`WonderGalleryScene`: size 25 x 46 x 33, sea from z 11): re-aimed at ship, so the new
    sea side stands in water.
- By id or data only (the drawing doesn't matter): `LighthousePowerGameTests` (other tests), `QaWonderPowersGameTests`,
  `QaWonders1114cGameTests`, `QaVillagerVerify1104c3GameTests`, `QaThreatFixes1104dGameTests`, `PactOfferFixesGameTests`,
  `QaConfigIds1116dGameTests`, `ConfigHandWrittenGameTests`, `QaConfigNewFields1116e*`, the compat ferry menus
  (`ShopListMenusCompatTests`, `CobblemonCompatTests`, `QaJobShopLists1109cCompatTests`: a post merely named "Lighthouse").

## Saves and shipping (not the designer's, but they bound the design)
- `WonderUpkeep.blocks` counts a finished Wonder against **today's** stage parts, and a site mid-build takes its next
  stage from today's parts. A Lighthouse already standing on the owner's live server would read as under 90 % (damaged,
  all four powers off, builders "repairing" it into the new design), and a half-built one would get new stages on its
  old stage I. Before shipping, the lane must keep standing and half-built Lighthouses on their old parts (old parts in
  `blueprint_history/wonder/lighthouse/`; `Refreshes.olderDrawing` already reads that folder for B656's tower), or file
  it as a bug (same root as B1374). `LighthousePowers` caches each site's Travel Post position once per site.
- `generate.py` saves every Wonder through `wonders.save_wonder`: the ship step stops it redrawing this one. Saving uses
  `kit.wonder_stages`, which takes **one drawing per stage at one size, each the whole Wonder as it stands after that
  stage**: draw the recipe so it can emit each stage's state.

## Free
Everything not listed above: the shape and plan, how the four stages grow, what the light stands on and how high within
the plot, materials, colors, how the land side meets the sea side, what else stands at its foot, rooms inside, the way
up, and lighting beyond the night light. The look the roadmap item (35.11) once described is not binding: the ship step
rewrites the wiki, the description and the pinned tests to the new design.
