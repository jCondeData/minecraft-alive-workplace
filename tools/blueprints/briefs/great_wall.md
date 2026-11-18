# Great Wall: the brief

Family id(s): the Wonder `aliveworkplace:great_wall` (data file `data/aliveworkplace/wonders/great_wall.json`; lang
keys `wonder.aliveworkplace.great_wall.name`, `.description`, `.stage.1` to `.stage.4`, and the chronicle lines
`chronicle.aliveworkplace.great_wall.*`: never renamed). Its drawing is four stages saved as parts
`structure/wonder/great_wall/stage_<n>_<i>.nbt`, merged by `WonderBlueprints` into the whole
`aliveworkplace:wonder/great_wall` that the Wonder Plan previews. Variants: none (no village styles, no modded
dressing; Wonders are never in the Blueprint Table).

## What it is for
A Wonder (Milestone 35, 35.15, 35.15a and 35.16): a defensive wall the player places at the village's edge, facing
out. A Town draws up the plans once it has a finished Gatehouse, the Golem Smith (without Legends, 4 iron golems and a
Master Tinkerer stand in) and the Master Architect; the plan's centre must be within the hall's 64 blocks
(`WonderPlans.placeRefusal`). The Foundation Stone goes just outside the front-left corner (template (-1, 0, -1),
`WonderStages.STONE_SPOTS`) and the chests round it are the stockpile. A crew of up to 12 builders builds it one stage
at a time, each stage waiting first for its goods (`tools/blueprints/wonder_goods.py`, not the designer's). Nobody lives
or works in it. While it is finished and **at least 90% of its blocks stand** (`WonderUpkeep`, counted daily over
every block of every part) it has two powers:
- `raid_barrier` (`GreatWallPowers`): seen from the hall, the Wall's **footprint** (the box of all its parts) covers an
  arc of the compass, widened 10 degrees either side; no raid (ours, the bandits', a lair's) gathers inside that arc,
  no siege ladder goes up within a block of the footprint, and siege rams never pick it. **Its Great Gate** (every fence
  gate in the drawing) shuts at nightfall and opens in the morning with the village's gates (`shutGates`).
- `sentries` (`GreatWallSentries`): four iron golem Wall Sentries come when it is finished, one at each **post** listed
  in the data file. A sentry fights any foe within 16 blocks of the wall (the line from the first post to the last),
  then goes back to its post; while foes are further out (within 80 of the hall) it steps out to its **walk spot** and
  watches. Knocked off the wall it walks back up, or with no way up climbs the face. A lost one is remade the next
  morning from 4 iron blocks and a pumpkin in the stockpile.

## Levels
The levels are its four stages, each a whole snapshot of the Wonder that adds to the one before.

| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| Stage I | `stage.1` "The Great Gate" | the whole site cleared; the Great Gate | Town, finished Gatehouse, Golem Smith / 4 golems + Master Tinkerer; goods in the data file |
| Stage II | `stage.2` "The West Run" | the west half | goods only |
| Stage III | `stage.3` "The East Run" | the east half | goods only |
| Stage IV | `stage.4` "The Walk and the Towers' Crowns" | the rest: finished, both powers on, the sentries arrive | goods only |

Exactly four stages (four goods lists; `wonders.py save_wonder` asserts it). The titles are fixed by tests (below) and
the player reads them on the stone while each stage is built, so each stage should build what its title names (the
gate first, then the west half, the east half, the walk and the towers' tops); a different split means new titles in
`en_us.json`, the guide and four tests.

## Must have (each level)
- Job block(s): none the code reads; no villager is assigned here.
- **The Great Gate**: at least 4 fence gates (`GreatWallPowerGameTests.noRaidGathersBehindTheGreatWall`; at least 2 of
  them spruce, oak or dark oak for `qaWonderFeatures`), **every fence gate in the whole drawing within x 46 to 50**
  (the test: each within 2 of the middle, x 48), their middle at x 48 within 1 (`qaWonderSizes`). The code shuts and
  opens every fence gate it finds in the drawing, so a fence gate anywhere else (a pen, a stair landing, a tower) is
  read as part of the gate and fails the test.
- **Four sentry posts**, written into the data file as `powers` `{"type": "sentries", "count": 4, "range": 16,
  "posts": [[x, y, z] x 4]}` next to `{"type": "raid_barrier", "margin_degrees": 10}` (these numbers are checked:
  `QaWonders1114cGameTests`, `GreatWallPowerGameTests.aDamagedGreatWallTurnsBothOff`). The posts are template cells
  the designer chooses (today `wonders.py`'s `WONDERS["great_wall"]` works them out from its own towers; the rebuild
  writes its own), listed **west to east**, all at the same height and on one line along the wall, because the first
  and last post make the line the 16-block range is measured from. For each post:
  - the post cell: solid floor under it, and room for an iron golem (1.4 wide, 2.7 tall: three cells of headroom and
    clear cells either side) — the golem is put there when the Wall is finished;
  - its walk spot: **2 blocks from the post along the wall, toward the middle of the four posts**
    (`GreatWallSentries.posts`): the same room for a golem, on solid floor, and a golem-wide way between it and the
    post. The showcase checks all four golems stand within 4 blocks of their walk spots at post height at midnight.
- Beds and residents: none. Any bed must be whole (`qaWonderBedsWhole`).
- Storage: none needed (the stockpile is the chests by the Foundation Stone, outside the drawing). Any container in the
  drawing must hold nothing (`qaWonderNothingFree`).
- Furniture kinds a job or test needs: none (no `blueprint_furniture/great_wall.json`).
- **Block counts `qaWonderFeatures_great_wall` reads** (the old spec carried into a test; meet them, or the ship step
  changes the test, the description line and the guide together as a decision written in the Notes):
  `iron_bars` 6 or more; fence gates as above; `campfire` 4 or more; `grindstone` 4 or more; `blue_wall_banner` /
  `red_wall_banner` / `white_wall_banner` 4 or more; `deepslate_bricks` / `cracked_deepslate_bricks` /
  `deepslate_brick_stairs` 100 or more; `mossy_stone_bricks` / `cracked_stone_bricks` / `mossy_cobblestone` 50 or more;
  `spruce_stairs` 100 or more; `stone_brick_stairs` / `spruce_stairs` / `cobblestone_stairs` 1 or more.
- **Four towers at fixed places** (`WonderBlueprintGameTests.WALL_TOWERS` = x 2, 20, 68 and 86; each x t to t + 8,
  z 3 to 11). Two tests read them (meet them, or the ship step changes both tests):
  - `greatWallTowerBasesSolid`: inside each tower (x t + 1 to t + 7, z 4 to 10) full blocks from y 0 to 8, no air;
    the tower's outer ring (x t to t + 8, z 3 to 11) full blocks from y 0 to 2; and a block (any) at y 0 everywhere in
    x t - 1 to t + 9, z 2 to 12.
  - `qaWonderSizes_great_wall`: within x t to t + 8, the highest block whose id contains "stone" or "cobble" is at y 16
    to 18 (17 to 19 high), such blocks reach both x t and x t + 8, and at least 20 `spruce_stairs` stand above that
    highest one in those columns. (So no "stone"-named block, a lantern post of stone bricks, a lodestone or a stone
    button, above y 18 over a tower.)
- **The curtain at its runs** (`qaWonderSizes_great_wall`): in the columns x 31 to 36 and x 79 to 84 the highest block
  of any kind is at y 10 to 12, and at y 6 every one of those columns spans (from its lowest z block to its highest)
  at least 7 and the thinnest exactly 7.
- **Doors**: every door whole with two clear blocks (feet and head) on both sides (`qaWonderDoorways`).
- No retired job block, no calcite, nothing a survival player can't get (`wonderMaterials`).

## Fits
- Plot: **exactly 97 long in x** (the footprint, from the parts' offsets and sizes: `noRaidGathersBehindTheGreatWall`
  asserts a span of 97; `qaWonderSizes` the blocks' length, 97 with no tolerance), its front row at z = 0 (the test
  also asserts the footprint's front edge is the placement's z = 0), lowest block at y = 0. Depth and overall height
  are free beyond the towers' and runs' numbers above (the old one is 15 deep, 25 high).
- **Blocks: 12,300 within 10%, so 11,070 to 13,530** solid blocks over all four stages (`wonderBlockCount`). The old one
  has 12,338.
- Front and way in: the front (z = 0) is **the village side**: the player stands in the village and places it facing
  out, the hall in front of it, the field (where raiders come from) behind it at the back. The barrier arc comes out at
  about 195 degrees with the hall 2 blocks in front of its middle (the test accepts 175 to 200), which any 97-long
  footprint meets. The gate is the way through; how a villager or the player gets up onto the wall is free (the
  guide's "two stairs on the village side by the gate" is player text the ship step updates if it changes).
- Neighbours: none placed by a plan; it may not overlap a finished building or a build in progress.
- How it is saved: four whole Builds of the same size, each stage the one before with more drawn on it, never a block
  taken away; `kit.wonder_stages` saves stage I whole with its air (so stage I's parts span the whole footprint) and
  cuts every stage into parts of at most 48 a side, so the 97-long Wall is several parts a stage. The first part of
  stage I is always `stage_1_0`, which five test walls borrow by name (`GreatWallPowerGameTests`, `QaGreatWall1104d4`,
  `QaRisingsWall1108d`, `QaThreatSpawnsPeace1101`, `QaLairBlipSentryIron1101d`). The ship step goes through
  `wonders.py` (`WONDERS["great_wall"]` keeps its icon, crew, requirements and goods; its `posts` become the new
  ones); `rebuild.py --candidate` saves a single `.nbt`, so the candidate is the four stage snapshots.

## Rules tests check
- `WonderBlueprintGameTests`: `wonderFileLoads_great_wall`, `wonderPartsLoad_great_wall` (at most 48 a side, none empty
  after stage I, under `wonder/great_wall/`, lang lines, none in the Blueprint Table), `wonderStagesOnlyAdd_great_wall`,
  `wonderBlockCount_great_wall` (12,300 within 10%), `wonderMaterials_great_wall`, and `greatWallTowerBasesSolid`.
- `QaWonderDesignsGameTests`: `qaWonderSizes_great_wall` (97 long, the runs 12 high within 1 and 7 thick at y 6, the
  towers 18 high within 1 and 9 wide with spruce-stair roofs, the gate's middle at 48 within 1);
  `qaWonderFeatures_great_wall` (the counts above); `qaWonderStagesGrounded_great_wall` (after **every** stage every
  block joins the ground at y 0 through blocks touching by face, edge or corner: the west and east runs must stand on
  their own before the walk joins them); `qaWonderStagesStand_great_wall` (built for real on stone after every stage,
  every block can stay by vanilla's rules: banners on a wall, lanterns hung or standing on something, vines on a face,
  nothing that falls over air); `qaWonderDoorways_great_wall`; `qaWonderNothingFree_great_wall`;
  `qaWonderNames_great_wall` (name "Great Wall", stages "The Great Gate", "The West Run", "The East Run", "The Walk and
  the Towers' Crowns", American English); `qaWonderBedsWhole_great_wall`.
- `GreatWallPowerGameTests.noRaidGathersBehindTheGreatWall`: the real Wall turned 180 degrees in front of a hall; its
  footprint spans 97 with its front at the placement's z; one arc of 175 to 200 degrees; 50 raids gather outside it;
  at least 4 fence gates, all inside the footprint within 2 of the middle, shut at night and open by day; the gate is
  shielded from ladders; no part is a siege's wall or gate (by id). `aDamagedGreatWallTurnsBothOff`: four posts, count
  4, range 16, margin 10.
- `QaWonders1101GameTests` (the whole equals the stages; centred on the hall it is accepted in all four turns and
  mirrored; requirements and stage titles), `QaWonders1114cGameTests` (powers and numbers), `QaGreatWall1104d4GameTests`
  (stage titles and goods), `QaConfigIds1116dGameTests`, `QaThreatsLateGameTests`: by id, the data or test walls.
- Showcase: `wonder_great_wall` (`WonderGreatWallScene`: the time-lapse, then at midnight the Great Gate shut, the
  raid on the open side and all four sentries on the walk by their walk spots), `wonder_gallery`.
- The owner's rules (`check.py`) and STYLE.md's checks, run on each stage as it will stand. The old parts have
  baselined owner-rule problems (`owner_rules_baseline.json`: stage_1_0, stage_1_1, stage_2_0 and stage_3_0, 2 each);
  the rebuild must have none, and the ship step removes those lines.
- Player text that describes the old look and changes with a new design (not requirements; the ship step rewrites
  them): the description line `wonder.aliveworkplace.great_wall.description` (it names "97 long and 12 high", four
  towers, a portcullis over fence gates), `docs/guide/great-wall.md` ("stand in the alcoves of the four towers") and
  the Great Wall paragraphs of `docs/wiki/wonders.md`.

## Free
Everything else: the curtain's profile, the towers' and gatehouse's shape above their tested numbers, the way up, rooms,
materials beyond the counts above, lighting and dressing, as long as the footprint is 97 long from the village-side
front, the only fence gates are the Great Gate's in x 46 to 50, the four posts and their walk spots hold a golem, and
every rule above holds after every stage.
