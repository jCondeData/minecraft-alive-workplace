# Great Cathedral: the brief

Family id(s): the Wonder `aliveworkplace:great_cathedral` (never renamed); its drawing is the stage parts
`wonder/great_cathedral/stage_<n>_<k>` (today 9 parts: `stage_1_0..1_3`, `2_0`, `3_0..3_1`, `4_0..4_1`), listed with
their offsets in `data/aliveworkplace/wonders/great_cathedral.json`, and the whole blueprint
`aliveworkplace:wonder/great_cathedral` the code lays together from them (`WonderBlueprints.merge`). Variants: village
(none), modded (none), looks (none: a Wonder keeps its own blocks; its plan can only be mirrored, `WonderPlanItem`; no
`village_colours`).

A flagship family (ROADMAP 52.6): two designers (A and B) work from this one brief.

## What it is for
A Wonder (Milestone 35), the village's great church. A player draws up its plans at the Village Hall's Wonders page
once the village is a **Town** with the Seer (stand-in: a Master Cleric), **3 finished Chapels** and the Master
Architect, places the Wonder Plan (its front, z = 0, faces the player) and hands it to a builder: a **Foundation Stone**
goes down just outside the front corner (template (-1, 0, -1), else (-2, 0, -1), (-1, 0, -2), (-2, 0, -2), (-1, 1, -1);
`WonderStages.STONE_SPOTS`), the chests within 8 of it are the stockpile, and a **crew of up to 12 builders** raises it
in **four stages**. Finished and standing (`wonderStanding`, 90%), its powers (35.10) are:
- **Weddings at its altar** (`WeddingsPower`, `"altar": [19, 2, 49]`): every wedding of the village is held at the altar
  spot (`Couples.venue`, ahead of a Chapel or the bell) and blessed (the village 15 happier for 3 days, the couple's
  first baby the next day a bed is free, their children twice as likely Gifted). The wedding's fireworks go up from
  the top surface within 3 blocks round the altar spot.
- **Bells** (`WonderBells`): every bell in its drawing that still stands rings for a wedding, a festival's feast and a
  raid beginning (raiders near it glow, as with a village bell).
- **Undertakers** search the whole village and bring the dead back by morning; **8 beauty**. Neither reads a block.
Nobody lives or works in it (vanilla villagers may claim a lectern or brewing stand drawn in it as a job site; nothing
of ours does).

## Levels (its stages)
| Stage | Parts | What it adds (function, not looks) | Unlock / goods (data file, unchanged by the drawing) |
|---|---|---|---|
| I *The Foundations and the West Front* | `stage_1_*` | Clears the site (stage I is saved whole, air included, so the builders clear it; only stage I may have parts of air alone) and lays the whole floor and the front | Town, the Seer, 3 Chapels; goods: Prize Pottery Sherd, Ghast Tear, 2 Fine Clothes |
| II *The Nave* | `stage_2_*` | The body of the church (the nave and its seating) | music disc otherside, Echo Shard, Heart of the Sea, 2 Illuminated Books |
| III *The Transepts and the Choir* | `stage_3_*` | The crossing arms and the east end, with the altar and the side chapels | Totem, Enchanted Golden Apple, Eye trim, 2 Noble Robes |
| IV *The Towers and the Rose Window* | `stage_4_*` | The towers' tops with the bells and spires, and the rose window | Elytra, Nether Star, music disc relic, 2 Gold Circlets |

The four titles are player text (`wonder.aliveworkplace.great_cathedral.stage.1..4`) and are tested word for word
(`QaWonderDesignsGameTests.qaWonderNames_great_cathedral`, `QaWonders1101GameTests`). Draw each stage so it matches its
title; a design whose stages raise something else means renaming the titles in `en_us.json` and both tests together at
the ship step, a decision to note, not a default.

## Must have
- Job blocks: none of ours. Beds and residents: none. Storage: none (the stockpile is the chests round the Foundation
  Stone, outside the drawing; no container in the drawing may hold anything).
- Furniture kinds a job or test needs: none (no `blueprint_furniture/` file).
- **The altar spot, pinned at template (19, 2, 49)** (`great_cathedral.json` "altar"; `WonderCathedralGameTests.
  theCathedralsPowersAltarAndBells`): a solid block **under** it at (19, 1, 49), **nothing** drawn at (19, 2, 49) and
  (19, 3, 49) (the couple stands there), and a **`minecraft:gold_block` at (19, 2, 50)**, directly behind it toward
  +z (the couple faces +z, away from the front). `aWeddingGoesToTheCathedralWithItsMoodAndBaby` and
  `aDamagedCathedralTurnsBothOff` place the Wonder so this spot lands at a fixed test cell, and the turned check expects
  (-19, 2, -49) at 180 degrees. Moving the altar means changing the data file's `altar` and these tests together: don't.
- **The wedding as the showcase films it** (scene `wonder_great_cathedral`, `WonderTimelapse.wedding`): the groom stands
  beside the bride at (20, 2, 49), so that cell and the one over it are open too, on a floor; six guests are stood at
  (16, 1, 36), (17, 1, 36), (21, 1, 36), (22, 1, 36), (15, 1, 34), (23, 1, 34) facing the altar, so those cells and the
  ones over them are open, standing room; the camera hovers at (19, 5, 29) looking at the altar, so the line from there
  to (19, ~3, 49) is open. The scene's numbers can be moved at the ship step, but the function stays: a congregation
  floor in front of the altar with a clear view to it.
- **At least 3 `minecraft:bell`, every one more than 30 above y = 0 (y >= 31)** (`theCathedralsPowersAltarAndBells`:
  "the belfries' bells"; `qaWonderFeatures_great_cathedral`: three bells). Every bell must hang so it survives (on a
  ceiling, wall or floor attachment that has its support) and stays in place: the showcase checks each still stands.
  No bell below y = 31 (the test asks that *all* bells are that high).
- **Blocks the tests name, each at least as many as shown** (`QaWonderDesignsGameTests.qaWonderFeatures_great_cathedral`):
  `white`, `light_blue`, `yellow` and `red` stained glass (pane or block) at least 1 of each (the rose window's four
  glasses); `dark_oak_stairs` 10 (pews); `gold_block` 1 (the altar's); `white_candle` or `candle` 1;
  `red_wall_banner`/`white_wall_banner`/`blue_wall_banner` 2; `lectern` 3 (three side chapels); `brewing_stand` 1 (a
  sacristy's); `polished_andesite` 50; `deepslate_tile_stairs` + `deepslate_tiles` 200; `cracked_stone_bricks` +
  `mossy_stone_bricks` 50. These are requirements only because the test reads them; how they are used and where is free
  (the gold block and bells above aside). If a design truly needs a different set, the ship step changes that test, the
  wiki and `docs/guide/great-cathedral.md` together: a decision to note, not a default.
- Lecterns hold no book and the brewing stand no bottles (`qaWonderNothingFree_great_cathedral`).

## Fits
- Plot (`QaWonderDesignsGameTests.qaWonderSizes_great_cathedral`, from the spec "about 27 wide (39 across the
  transepts) and 55 long, its two west towers 44 high under stone spires reaching 52"):
  - width along x **37 to 41**; length along z **53 to 57**; the top block at **y 51 to 53**; the bottom at y = 0;
  - **two front towers**: the highest block in the front strip (z <= 12) left of the middle x, and the highest right of
    it, each at **y 51 to 53** (the middle x is half way between the outermost blocks);
  - **the body at z = 25**: the blocks in the row z = 25, y = 2 span **25 to 29** along x (the nave and what flanks it,
    outside walls included).
- Each saved part is at most 48 a side (`WonderBlueprintGameTests.wonderPartsLoad_great_cathedral`); a Wonder this size
  is cut into several parts a stage (`kit.wonder_stages` does it), each in `wonder/great_cathedral/`.
- Placement (`WonderPlans.placeRefusal`): the footprint's centre within 64 of the hall, every corner within 96, over no
  building, build site or other Wonder. The Steward's roads and walls keep off the whole footprint.
- Front and way in: the front is the z = 0 side, toward the player who placed the plan; the altar is at the far end
  (z 49 of 0..54), so the way in is from the front, through the body to the altar. Every door must be walkable (below).
- Neighbours: the Foundation Stone and its stockpile chests stand just outside the front-left corner (template -1..-2,
  0..1, -1..-2): leave that corner's outside reachable (it is outside the drawing). At a dedication the village gathers
  round the stone and the guards line up outside the Wonder's corner (`Dedications`).

## Rules tests check
- `WonderBlueprintGameTests` (generated per Wonder): `wonderFileLoads_great_cathedral` (the data file loads, every
  stage has goods); `wonderPartsLoad_great_cathedral` (every part loads, at most 48 a side, none empty but stage-I
  clearing parts, all in `wonder/great_cathedral/`, out of the Blueprint Table, every name has its line);
  `wonderStagesOnlyAdd_great_cathedral` (no part sets air where an earlier stage put a block); **`wonderBlockCount_
  great_cathedral`: the sum of every part's non-air blocks within 10% of 14,000 (12,600 to 15,400)**;
  `wonderMaterials_great_cathedral` (no retired job block, `StationsSpecGameTests.gone()`; no calcite; nothing a player
  can't get).
- `QaWonderDesignsGameTests` (per Wonder): `qaWonderSizes_*` and `qaWonderFeatures_*` (above); **`qaWonderStagesGrounded_
  great_cathedral`: after every stage, every block joins y = 0 through blocks touching by face, edge or corner** (no
  roof, vault or spire held up only by a later stage); **`qaWonderStagesStand_great_cathedral`: built for real stage by
  stage on stone, every block can stay by vanilla's rules** (bells and lanterns hung from something, banners, ladders and
  signs on a wall, candles and plants on a fitting top, nothing that falls over air); **`qaWonderDoorways_great_
  cathedral`: every door whole (upper half over the lower, same door and facing) with two clear blocks (feet and head;
  a carpet underfoot is fine) on both its sides**; `qaWonderNothingFree_great_cathedral`; `qaWonderBedsWhole_great_
  cathedral`; `qaWonderNames_great_cathedral` (the four titles).
- `WonderCathedralGameTests.theCathedralsPowersAltarAndBells` (the altar cells, the gold, the bells; above).
- `WonderPlanGameTests`: `wonder/great_cathedral/stage_1_0` is a hidden (worldgen) piece, so **keep a part named
  `stage_1_0`** (and one `stage_<n>_0` per stage, as every Wonder has).
- Whole-Wonder glass rule, `QaWindowsAndWards1028BugTests` (B648): every pane or bar, joined as the game joins it in
  the whole Wonder, meets a block at both ends (a rose or lancet's glass set in a later stage's wall is judged with it).
- `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`: per part, a standing lantern, candle or torch stands
  on a full top (not a lectern or a bottom slab); no dirt path or farmland under a solid block.
- The daily upkeep (`WonderUpkeep`, `wonderStanding` 90%): every drawn block is counted against the world once a day;
  under 90% standing its powers stop. So **everything drawn must stay as placed with nobody tending it** (no leaves
  that decay, crops, fire, snow layers, falling or flowing blocks); neighbour-shaped properties and `lit` are forgiven.
- The owner's rules (`check.py`, run per stage on the stages before it, upper floors reachable from the ground on the
  last stage: a belfry or gallery with places to stand needs a way up) and STYLE.md's checks; `holes.py` skips a
  Wonder's stage parts on their own. The Cathedral is not in `owner_rules_baseline.json`: the rebuild must stay clean.

## For the ship step (not the designer)
- A Wonder is drawn as four whole builds of one size, stage n being stage n-1 with more on it; `kit.wonder_stages`
  splits and saves the parts and `wonders.py`'s `save_wonder` rewrites `great_cathedral.json`'s "parts" (ids and
  offsets) and keeps every other field (requirements, goods, crew 12, powers with the altar at [19, 2, 49]) as it is.
  A candidate must not overwrite the live parts.
- **Saves**: a Cathedral begun or finished on a live server is counted against the *current* drawing (`WonderUpkeep.
  blocks`, `WonderBells.bells`), with no `blueprint_history` fallback, so a new drawing would read an old finished
  Cathedral as damaged (its powers off) and an unfinished one's later stages would not fit its built ones. Shipping
  needs a fallback to the drawing a site was begun with (as `Refreshes` does for buildings), or the owner's decision.
- The wiki (`docs/wiki/wonders.md`), `docs/guide/great-cathedral.md`, `docs/design/M35.md`, the gallery scene's size
  (`WonderGalleryScene`, 39 x 53 x 55) and the `wonder_great_cathedral` scene's guest and camera cells describe the old one.

## Free
Everything else: the plan, the massing of body, arms, east end and towers within the sizes above, the roofs, vaults,
windows and doors' forms, every material beyond the listed ids, the interior's layout round the pinned altar, and how
the four stages divide the work, as long as every rule above holds after every stage.
