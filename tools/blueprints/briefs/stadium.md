# Stadium: the brief

Family id(s): `stadium` (a Wonder, `aliveworkplace:stadium`; never renamed). Its drawing is four stages saved as parts,
`structure/wonder/stadium/stage_<n>_<k>.nbt` (today seven: two parts a stage but stage II, because it is wider than 48),
laid at the offsets its data file `data/aliveworkplace/wonders/stadium.json` lists, which `tools/blueprints/wonders.py`
(`save_wonder`, the `WONDERS["stadium"]` entry) writes from the drawing so the two always agree. The levels below are
its stages. **Only with Cobblemon**: the data file has `fabric:load_conditions` on `cobblemon`, so without it the
Stadium isn't on the Wonders page (`StadiumGameTests.theStadiumNeedsCobblemon`), and its full checks are compat tests.
Variants: village (none), modded (none), looks (none: a Wonder Plan can only be mirrored). It does have **village
colors** (below). Not in this family: M28's Arena builds (`arena`, `arena_2`, `arena_3`, `Arenas`), which are ordinary
Blueprint Table builds.

ROADMAP 52.6 (one item per Wonder). The Wonder system itself (`wonder/`) does not change.

## What it is for
A **Wonder** (Milestone 35, 35.23/35.24): the village's great Pokémon battle ground. A Town draws up the plans on the
Village Hall's Wonders page (needs the Pokémon Professor Legend or 25 pastured Pokémon of 10 types, and the Master
Architect or a Master builder), places the Wonder Plan and hands it to a builder; a crew of up to 12 builds it stage by
stage from the stockpile round the Foundation Stone, each stage after its goods are dedicated. Finished and standing
(at least 90% of its blocks, `WonderUpkeep`), it grants two powers, and **both read the drawing itself**
(`StadiumDays.spots`, from the blocks as placed, turned and mirrored with the Wonder):

- **Stadium Days** (`stadium_days`: every 2nd day, 2 to 4 visitors). From noon (6000) to dusk (12000), 2 to 4
  visiting trainers stand in the two **trainer boxes**, two to a box at most, facing the field; players challenge them;
  from 7000 the village's own trainers fight them in bouts every 1000 ticks. The villagers off work walk to the
  **seats** and sit to watch (5 mood until the next Stadium Day). At dusk the visitors leave, walking out to the
  **gate** spot when a player can see them.
- **Tournament host** (`tournament_host`): the Stadium is the village's Festival Cup Arena (tier 3) before any Arena
  build (`Arenas.find`): the Cup's bouts are called at the field's centre (the **ring**) with the two box spots, its
  fans fill the seats, rockets go up from the ring, and the Cup's fair (traders) and notice board stand at the gate spot.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| Stage I | `wonder/stadium/stage_1_*` | The ground stage: clears the whole site (stage I is saved whole, air included); today the field, its white lines and the two Training Posts | Plans: Town, Pokémon Professor (or 25 pastured Pokémon of 10 types), Master Architect; goods: Diamond Horse Armor, Coast Armor Trim (found), 2 Great Balls (Ball Smith) |
| Stage II | `wonder/stadium/stage_2_*` | More of it; **holds more than 100 red and more than 100 white carpets** (a test reads them, below) | Trident, music disc Pigstep, Netherite Upgrade (found), 2 Gold Circlets (Jeweler) |
| Stage III | `wonder/stadium/stage_3_*` | More, built on I and II | Totem of Undying, Ominous Trial Key, Flow Armor Trim (found), 2 Ultra Balls (Ball Smith) |
| Stage IV | `wonder/stadium/stage_4_*` | The last of it; when done the Wonder is finished and its powers come on | Nether Star, Heavy Core, Enchanted Golden Apple (found), 2 Noble Robes (Tailor) |

**Exactly four stages** (`StadiumCompatTests.stadiumFileLoads` asserts 4, and those goods, requirements and powers word
for word; `save_wonder` asserts one stage per goods line in `wonder_goods.py`). None of that changes in the rebuild.

**Stage titles are player text** (`wonder.aliveworkplace.stadium.stage.1`..`4`, keys never renamed), checked word for
word by `QaWonderDesignsGameTests.qaWonderNames_stadium` (`TITLES`): today "The Field", "The Lower Stands", "The Upper
Stands and the Covered Stand", "The Gate and the Floodlights". Each title must name what its stage builds; new stages
get new titles at ship (American English), changed in `en_us.json` and the test together. The description line
(`...stadium.description`, which describes the old look) is rewritten too and ends in a full stop.

## Must have (each level)
- **Two Training Posts (`aliveworkplace:training_post`), exactly two** (`StadiumCompatTests.stadiumMaterials`). They
  are how the code finds the arena (`StadiumDays.spots`): with fewer than two it has no Stadium Days and hosts no Cup.
  - **Both at the same height, one at each end of the field.** The posts are sorted by x, then z; the field's centre
    (the ring) is the cell halfway between the first and the last, **at the first post's y**.
  - **Each post's box:** spot 1 is the cell next to the post on the side toward the ring (the nearest of the four
    directions); spot 2 is the cell clockwise of spot 1 (seen from above, turning right from the post-to-ring direction).
    **All four spots must be room to stand**: no collision in the cell and the one above, solid ground below
    (`StadiumPowerCompatTests.theStadiumsDrawingHasItsBoxesAndSeats`). The two spot 1s are the Cup's trainer spots.
  - Each post is also a real job site (`ModVillagers.TRAINING_POST_POI`: the Trainer's, and a stand-in for several
    other trades'), which the village's villagers may claim.
- **The field and the ring:** villagers walk to the ring for the Cup's feast and bouts, Pokémon come out beside it,
  rockets go up from it, and the showcase scene sets two trainers on the field 6 blocks from it toward the back (+z).
  The ring and the field round it are open, walkable floor reached on foot from the gate spot.
- **Seats: every carpet in the whole Wonder, of any color, is a seat** (`StadiumDays.spots`: `CarpetBlock`).
  - **More than 300 of them** (`theStadiumsDrawingHasItsBoxesAndSeats`; the old one has 601).
  - So **no carpet anywhere a spectator shouldn't sit** (a rug in a room, a runner in a corridor would draw villagers
    to sit there on a Stadium Day and a Cup day).
  - Each seat is walked to and sat on: reachable on foot from outside, with head room over it (the villager comes
    within 2.5 blocks, then sits on the carpet, `StandSeat`); seats are filled nearest the ring first. The showcase
    scene seats 30 of 48 villagers set at the seats.
  - Keep every seat within 40 blocks across and 16 up of the ring: the Cup's stands only see a seat as taken inside that
    box (`CupDays`, `AABB(ring).inflate(40, 16, 40)`); the old seats reach y 16 over a ring at y 1. (From reading the
    code; no test.)
- **The gate spot is (26, 1, -4) in the drawing** (`StadiumDays.GATE`, a code constant; the test asserts it): four
  blocks out in front of the z = 0 side, at x 26. Visitors walk out to it at dusk, and the Cup's fair and notice board
  stand there. So the **way in is on the front and leads on foot from the boxes, the field and the stands out to that
  spot**, and the drawing leaves it open ground. A gateway elsewhere means the ship step moves `StadiumDays.GATE` and
  the test (code, a decision to note).
- **A Healing Machine (`cobblemon:healing_machine`), exactly one, and a brewing stand, exactly one**
  (`stadiumMaterials`, "the nurse's booth"). The Healing Machine is the Nurse's job site (`ModVillagers.HEALING_MACHINE_POI`),
  the brewing stand the Cleric's (a Nurse also takes one). Both must be reachable on foot from outside.
  - Without Cobblemon the Healing Machine reads as **air** (the main suite's design checks run that way), so **nothing
    may rest on it, hang from it, or join the ground only through it**, and no doorway may count on it.
- **Floodlights: exactly 4 daylight detectors and exactly 32 redstone lamps** (`stadiumMaterials`).
  - Every daylight detector **inverted** (it lights at night).
  - Built for real with every detector at full signal, **every** redstone lamp lights; with them at 0, **none** is lit
    (`QaWonderDesignsGameTests.qaWonderLampsLightAtNight_stadium`). So each lamp is wired to a detector and nothing
    else powers them.
  - The detectors must see the sky to switch in game (not tested).
- **Village colors** (the data file's `"village_colours": true`, `StadiumCompatTests.stadiumFileLoads`): in a village
  with a Village Banner, every stage is built in its two colors (`WonderStages`, `BlueprintStyles.colours`): every
  **red** wool, carpet, terracotta, banner and wall banner becomes the banner's base color, every **white** one the
  first other color. White concrete and everything else stay as drawn. So:
  - draw in red and white wool/carpet/terracotta/banners exactly what should wear the village's colors, and nothing
    that must keep its own color (a first-aid cross, a sign's lettering) in those kinds;
  - `StadiumCompatTests.stadiumSeatsTakeTheVillagesColours`: **stage II's parts hold more than 100 red carpets and
    more than 100 white carpets** (built as more than 100 of each village color, no white carpet left), and **stage I's
    parts hold more than 50 white concrete** (lines that stay white).
- **Job blocks otherwise: none required.** Any other job block drawn is a real job site the village will claim (the old
  one has a lectern, barrels, furnaces). Never a retired job block (`StationsSpecGameTests.gone()`; the compat copy in
  `StadiumCompatTests.retired()`).
- **Beds and residents: none.** No code counts a resident; a visitor never takes a bed. A bed drawn must be whole
  (`qaWonderBedsWhole_stadium`).
- **Storage: none required.** The stockpile is the player's chests by the Foundation Stone. Any container drawn is empty
  (`qaWonderNothingFree_stadium`).
- **Furniture kinds: none** (no `blueprint_furniture/stadium.json`; Wonder parts record no furniture spots).
- **Doors:** every door whole, with two clear blocks on both sides, feet and head (`qaWonderDoorways_stadium`).
- **Every block a player can get, no calcite** (`StadiumCompatTests.stadiumMaterials`, `wonderMaterials` rules): no
  bedrock, barrier, command or structure block, structure void, jigsaw, spawner, reinforced deepslate, budding
  amethyst, light, infested blocks, player head, dragon egg, nor any block without an item. No entity.
- **Nothing that changes by itself** enough to drop the daily count under 90%: fire, flowing water, falling blocks,
  growing crops; and no carved pumpkin or jack o'lantern that completes an iron or snow golem shape (it would turn into
  a golem and take the blocks; B559 notes the old drawing's carved pumpkin).

## Fits
- **Plot: 47 to 60 wide (x) by 37 to 50 deep (z) by 16 to 26 tall**, the whole Wonder's extent
  (`QaWonderDesignsGameTests.qaWonderSizes_stadium`; the old one is 53 x 43 x 24). Wider than 48 is cut into more parts
  by the kit, nothing more.
- **y = 0 is the clicked block's top, and the Wonder starts there** (lowest block y 0). The front is the z = 0 side.
- **The field's floor is at y = 0** today (posts and ring at y 1): `qaWonderSizes_stadium` reads the field at y 0 (see
  the pins below). Seats, boxes and gate are all read relative to the posts and the drawing's corner.
- **Parts:** each at most 48 a side, named `wonder/stadium/stage_<n>_<k>`, none empty (`StadiumCompatTests.stadiumPartsLoad`;
  the kit cuts them, `kit.wonder_stages`).
- **Stages only add** (`StadiumCompatTests.stadiumStagesOnlyAddAndCount`): a later stage never sets air or a different
  block where an earlier stage put one.
- **Every stage ends tidy, standing and grounded** (the village lives beside it for days):
  `qaWonderStagesGrounded_stadium` (after every stage every block joins y = 0 through touching blocks, edges and corners
  counted) and `qaWonderStagesStand_stadium` (built for real stage by stage on stone, every block can stay by the game's
  rules: lanterns and banners hung or on a wall, plants on soil, nothing that falls over air). Both run **without
  Cobblemon** (the Healing Machine is air there).
- **Block count: 10,890 to 13,310 solid blocks** (12,100 plus or minus 10%, `stadiumStagesOnlyAddAndCount`; the old one
  has 11,758). A different budget means changing `BLOCKS` in that test at ship, a decision to note.
- **Ground levelling:** only the last stage with a part on the Wonder's floor levels the ground round it
  (`WonderStages.levelling`, 35.8a); today every stage starts at y 0 or 1, so stage IV does.
- **Placing:** centre within 64 of the hall, every corner within 96, over no building, build in progress or other
  Wonder (`WonderPlans.placeRefusal`); any of four turns, or mirrored, so the arena logic must hold turned and mirrored
  (it does: the spots are read from the placed blocks).
- **Outside the front-left corner:** the Foundation Stone at template (-1, 0, -1) (or the next free of (-2, 0, -1),
  (-1, 0, -2), (-2, 0, -2), (-1, 1, -1)), the stockpile chests within 8 of it, the dedication's bard and guards beside
  it. All outside the plot: the drawing never reaches x or z below 0.
- **Front and way in:** the z = 0 side, toward the gate spot (26, 1, -4).
- **Neighbours:** none fixed; the player chooses the spot. Arena builds are separate and never needed beside it.

## Rules tests check
Drawing-independent: `StadiumGameTests.theStadiumNeedsCobblemon`, `theColoursStyleSwapsRedAndWhite`,
`theBannerPicksTheColours`; `StadiumCompatTests.stadiumFileLoads`; `StadiumPowerCompatTests` (all but the one named
below: they stage their own posts and seats with `setSpotsForTest`), `QaStadium1028CompatTests`,
`QaStadiumDays1104CompatTests`, `QaStadiumVisitorsCompatTests`, `QaStadiumThesisBugCompatTests`,
`QaWorldSavesMoreGameTests.aStadiumDayHeldTodayIsRememberedAfterARestart`, the Mega trainer tests.

Function rules on the drawing (each must pass as written; coordinates are the drawing's, from its corner):
- `StadiumPowerCompatTests.theStadiumsDrawingHasItsBoxesAndSeats` (compat): placed at a far spot unturned, the spots
  found are **posts at (8, 1, 21) and (44, 1, 21), ring (26, 1, 21), box spots (9, 1, 21), (43, 1, 21), (9, 1, 22),
  (43, 1, 20)**, each room to stand; **more than 300 seats**; gate (26, 1, -4). The pinned posts, ring and boxes move
  with the new drawing at ship (the test keeps its checks); the gate stays unless the code constant moves.
- `StadiumCompatTests.stadiumMaterials`: exactly 1 Healing Machine, 1 brewing stand, 2 Training Posts, 4 daylight
  detectors, 32 redstone lamps; no retired job block, calcite or unobtainable block.
- `StadiumCompatTests.stadiumPartsLoad`, `stadiumStagesOnlyAddAndCount`, `stadiumSeatsTakeTheVillagesColours` (above).
- `QaWonderDesignsGameTests` (main suite, Cobblemon blocks as air): `qaWonderStagesGrounded_stadium`,
  `qaWonderStagesStand_stadium`, `qaWonderDoorways_stadium`, `qaWonderNothingFree_stadium`, `qaWonderBedsWhole_stadium`,
  `qaWonderNames_stadium`, `qaWonderLampsLightAtNight_stadium` (at least 4 detectors, all inverted; every lamp lit at
  night, none by day).
- `QaWindowsAndWards1028BugTests.qaNoShippedGlassEndsInTheAirAsTheGameJoinsIt` (the whole Wonder, joined as the game
  joins panes and bars: none with air on one side) and `B648GameTests.b648NoShippedGlassEndsInTheAir` (each part as
  stored).
- `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`: lanterns, candles and torches on a top that holds
  them; no dirt path or farmland under a solid block.

**Old-spec pins** (written from the 35.23 text; they describe the old look, not a function. Don't design to them; the
ship step rewrites them to the new design, keeping the kind of check, and notes it):
- `qaWonderSizes_stadium` beyond the plot: the grass blocks and white concrete at y 0 span at least 31 by 21 (the
  "battle field 31 x 21"), with at least 40 white concrete at y 0.
- `qaWonderFeatures_stadium`, each at least: 2 Training Posts, 1 brewing stand, 4 daylight detectors, 4 redstone lamps,
  1 iron block or iron bars, 20 red carpet/wool/terracotta, 20 white carpet/wool/terracotta, 1 red banner, red wall
  banner or white wall banner, 300 grass blocks, 20 spruce slabs/logs/planks.
- `StadiumGameTests.theScoreboardReadsFromBothSides` (B141): the part `wonder/stadium/stage_3_1` at offset (48, 0, 8)
  has white terracotta spelling "2 - 1" at x 49 (read from the stands) and x 50 (read from the road), y 13 to 17, z 17
  to 25. The rule behind it stays: **any lettering, number or picture must read the right way round from every side it
  is seen from** (a one-block-thick sign reads mirrored from behind). The pinned cells go with the old scoreboard; the
  ship step points the test at the new drawing's lettering or retires it if there is none.

The owner's rules (`check.py`, per stage via `save_wonder`), STYLE.md's checks and `holes.py`. The old stage III has 6
baselined problems (`owner_rules_baseline.json`, `wonder/stadium/stage_3_0`); the rebuild has none and the ship step
removes that line.

Ship-step updates (not the designer's): `wonders.py`'s `stadium` drawing (or its replacement feeding `save_wonder`), the
data file's part offsets, `WonderGalleryScene`'s size (`new Vec3i(53, 24, 43)`), `WonderStadiumScene` (home trainers at
ring + (-2..2, 0, 6), the camera over the east box), the pins above, the titles and description if they change,
`docs/wiki/wonders.md` (its 35.23 paragraph describes the old look) and `docs/guide/stadium.md`. A Stadium site begun on
the old drawing must still finish as begun (REBUILD.md).

## Free
Everything not listed above: the shape of the bowl and the stands, how many tiers, roofs and canopies, rooms under the
stands or none, the materials and colors other than the village-color kinds, the field's surface and markings beyond
the pinned counts, where the Healing Machine, the floodlights and the posts stand (within the rules), and what each
stage builds (four stages, each ending grounded and tidy, titled for what it builds).
