# Great Library: the brief

Family id(s): the Wonder `aliveworkplace:great_library` (data file `data/aliveworkplace/wonders/great_library.json`;
lang keys `wonder.aliveworkplace.great_library.name`, `.description`, `.stage.1` to `.stage.4`: never renamed). Its
drawing is four stages saved as parts `structure/wonder/great_library/stage_<n>_<i>.nbt`, merged by
`WonderBlueprints` into the whole `aliveworkplace:wonder/great_library` that the Wonder Plan previews. Variants: none
(no village styles, no modded dressing; Wonders are never in the Blueprint Table).

## What it is for
A Wonder (Milestone 35, 35.13 and 35.14). A Town draws up the plans (a Wonder Plan) once it has the Old Sage (without
Legends, a Master Scholar stands in), 20 research levels and the Master Architect; the player places the plan with its
centre within the hall's 64 blocks (`WonderPlans.placeRefusal`) and gives it to a builder. The Foundation Stone goes
just outside the front-left corner (template (-1, 0, -1), `WonderStages.STONE_SPOTS`) and the chests round it are the
stockpile. A crew of up to 12 builders (`crew: 12`) builds it one stage at a time; each stage first waits for its goods
(found things and workshop goods, `tools/blueprints/wonder_goods.py`, not the designer's). Nobody lives or works in it.
While it is finished and **at least 90% of its blocks stand** (`WonderUpkeep`, counted daily over every block of every
part), its power `research_tier` (tier 2) opens the scholars' second research row (`LibraryPowers`): the power reads
no block of the drawing. Below 90% the builders repair it first.

## Levels
The levels are its four stages, each a whole snapshot of the Wonder that adds to the one before.

| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| Stage I | `stage.1` "The Stacks" | the site cleared and the first of the building | Town, Old Sage / Master Scholar, 20 research levels; goods in the data file |
| Stage II | `stage.2` "The Galleries" | more of it | goods only |
| Stage III | `stage.3` "The Rotunda" | more of it | goods only |
| Stage IV | `stage.4` "The Dome and the Portico" | the rest: finished, the power on | goods only |

Exactly four stages (four goods lists; `wonders.py save_wonder` asserts it). The titles are fixed by tests (below). The
code doesn't read what each stage holds, but the player reads the title on the stone while the stage is built, so each
stage should build what its title names; a different split means new titles in `en_us.json`, the guide and three tests.

## Must have (each level)
- Job block(s): none the code reads. No villager is assigned here.
- **One enchanting table, exactly one, with at least 15 bookshelves that power it** by vanilla's rule (shelves two
  out from the table, at its level or one above, with the cell between table and shelf free):
  `QaWonderDesignsGameTests.qaWonderEnchantingPower_great_library` builds the whole for real and counts them.
- **The table at (20, 3, 22)**, standing on the floor at y 2, with standing room on a floor at (21, 3, 18) and
  (21, 4, 18) and a clear line from (20, 4.6, 18) to the table: the showcase scene `wonder_great_library`
  (`WonderLibraryScene`, `CX = 20`, `CZ = 22`, `FLOOR = 3`) puts a scholar there and films his research screen over
  the table. Moving the table means changing those constants in the same commit.
- **At least 5 lecterns**, none holding a book, and **at least 1 cartography table** (`qaWonderFeatures`: "a lectern at
  the centre and four in the wings", "a map room's cartography table").
- Beds and residents: none. Any bed must be whole (`qaWonderBedsWhole`).
- Storage: none needed. Any container, lectern, composter or shelf must hold nothing (`qaWonderNothingFree`).
- Furniture kinds a job or test needs: none (no `blueprint_furniture/great_library.json`).
- **Block counts `qaWonderFeatures_great_library` reads** (the old spec carried into a test; meet them, or the ship
  step changes the test, the description line and the guide together as a decision written in the Notes):
  `minecraft:bookshelf` 200 or more; `copper_block` / `cut_copper` / `cut_copper_stairs` 100 or more (these exact ids:
  weathered or waxed ids don't count in the drawing, though the world may weather them); `quartz_block` /
  `quartz_pillar` / `chiseled_quartz_block` / `smooth_quartz` 20 or more; `polished_diorite` + `smooth_stone` 500 or
  more; `dark_oak_planks` 200 or more; `glass` or `glass_pane` 1 or more.
- **Doors**: every door whole (both halves, same facing) with two clear blocks (feet and head) on both sides
  (`qaWonderDoorways`).
- No retired job block (`StationsSpecGameTests.gone()`), no calcite, nothing a survival player can't get
  (`wonderMaterials`).

## Fits
- Plot: the whole is **41 x 41 (each within 1) and 34 high (within 1)** (`qaWonderSizes_great_library`, over all its
  blocks); its lowest block at y = 0, the clicked block's top. The guide's infobox says "41 by 41, 34 high".
- **Blocks: 16,000 within 10%, so 14,400 to 17,600** solid blocks over all four stages (`wonderBlockCount`). The old
  one has 14,574, close to the floor.
- Front and way in: the front is the z = 0 side, where the player stands placing it and the Foundation Stone goes (just
  outside its left corner). The scene's camera stands between the way in and the table, so the way in is from the
  front.
- Neighbours: none placed by a plan; it may not overlap a finished building or a build in progress
  (`WonderPlans.placeRefusal`).
- How it is saved: four whole Builds of the same size, each stage the one before with more drawn on it, never a block
  taken away (stage N may not set air, or another block, where an earlier stage put one); `kit.wonder_stages` saves
  stage I whole with its air (the builders clear the site) and every stage cut into parts of at most 48 a side. The
  ship step goes through `wonders.py` (`WONDERS["great_library"]` keeps its icon, crew, requirements, goods and
  powers); `rebuild.py --candidate` saves a single `.nbt`, so the candidate is the four stage snapshots.

## Rules tests check
- `WonderBlueprintGameTests` (generated per Wonder): `wonderFileLoads_great_library` (the file loads, as many stages as
  part stages, every stage has goods); `wonderPartsLoad_great_library` (at least 2 stages, every part loads, at most
  48 a side, not empty after stage I, under `wonder/great_library/`, the names have lang lines, no part in the
  Blueprint Table); `wonderStagesOnlyAdd_great_library`; `wonderBlockCount_great_library` (16,000 within 10%);
  `wonderMaterials_great_library`.
- `QaWonderDesignsGameTests` (generated): `qaWonderSizes_great_library` (41 x 41 x 34, each within 1, from y 0);
  `qaWonderFeatures_great_library` (the counts above); `qaWonderStagesGrounded_great_library` (after **every** stage,
  every block joins the ground at y 0 through blocks touching by face, edge or corner: nothing waits in the air for a
  later stage); `qaWonderStagesStand_great_library` (built for real on stone after every stage, every block can stay by
  vanilla's rules: lanterns hung or standing on something, banners and ladders on a wall, plants on soil, nothing that
  falls over air); `qaWonderDoorways_great_library`; `qaWonderNothingFree_great_library`;
  `qaWonderNames_great_library` (name "Great Library", stages "The Stacks", "The Galleries", "The Rotunda", "The Dome
  and the Portico", American English, the description ends a sentence); `qaWonderBedsWhole_great_library`;
  `qaWonderEnchantingPower_great_library`.
- `QaWonders1101GameTests` (generated): the whole blueprint holds exactly the blocks the stages leave and the plan
  carries its size; centred on the hall it is accepted in all four turns and mirrored; its requirements and stage
  titles.
- `QaWonders1114cGameTests`: `research_tier` tier 2, four requirements, the name. `LibraryPowerGameTests`,
  `WonderFindsGameTests`, `QaWonderPowersGameTests`, `QaItemComponentSavesGameTests`, `QaConfigIds1116dGameTests`: by
  id only, none places the drawing.
- Showcase: `wonder_great_library` (the table spot above), `wonder_gallery` (places every stage's parts), `wonder_finds`
  (by id).
- The owner's rules (`check.py`) and STYLE.md's checks, run on each stage as it will stand. The old parts have
  baselined owner-rule problems (`owner_rules_baseline.json`: stage_1_0 3, stage_2_0 8, stage_3_0 2, stage_4_0 2); the
  rebuild must have none, and the ship step removes those lines.
- Player text that describes the old look and changes with a new design (not requirements; the ship step rewrites
  them): the description line `wonder.aliveworkplace.great_library.description`, `docs/guide/great-library.md` and the
  Great Library paragraphs of `docs/wiki/wonders.md`.

## Free
Everything else: plan, massing, roofs, materials beyond the counts above, rooms, how the stages divide the building
(within their titles), lighting and furnishing, as long as the table with its 15 shelves stands at (20, 3, 22) and
every rule above holds after every stage.
