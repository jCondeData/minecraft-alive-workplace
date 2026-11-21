# Topiary Garden: the brief

Family id(s): `topiary_garden` (never renamed)   Variants: village (none drawn), modded (none: decorations are never
dressed by `modded_library.py`), looks (none)

One tier only: there is no `topiary_garden_2`, no upgrade, and nothing in the code asks for one
(`docs/wiki/more-decorations.md`, "Known limits": each of the 26.14 decorations is one tier).

## What it is for
A village decoration: a small formal garden of clipped plants (26.14, the owner: "a wider variety of builds that
aren't houses"). Nobody works, lives or meets at it. A finished Topiary Garden within a Village Hall's claimed area
adds **3 beauty points** (and so 3 % wellbeing, up to the cap) to the village, the most of the fourteen 26.14
decorations and as much as the Fountain, and counts as one of the village's buildings for its rank. It also counts
towards the beauty need of villagers whose bed is near it (decorations are counted by id). The player gets it from
the Blueprint Table, where it starts locked ("A market-day trader": a traveling trader on market day may sell it, a
builder's level-up unlocks it, or a found Topiary Garden blueprint used on the Table unlocks it). The Steward never
builds one (no `steward_rules` file names it) and no loot table holds it; only a player places it. A builder builds
it from the plan's materials and can take it down again (sneak hand-over).

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `topiary_garden` | 3 beauty points (`hall.Decorations.POINTS`); marked on the village map as a decoration (`VillageMaps`, by id); counts for the village's rank (`VillageRanks`); a hidden `aliveworkplace:built/topiary_garden` advancement (by id) | Blueprint Table, locked with the hint `screen.aliveworkplace.table.hint.market` (`BlueprintUnlocks.hint`: every entry of `StarterBlueprints.DECORATIONS` except the Flower Planters); in the market-day traders' pool (`MarketDays`, all `DECORATIONS`); in the creative tab (`ModItems`, all `DECORATIONS`). No upgrade exists or is asked for. |

## Must have (each level)
- Job block(s): **none, and none allowed.** No block in the template may be a villager point of interest (no barrel,
  composter, cauldron, lectern, smoker, loom, bell, bed, beehive, ...):
  `MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock` and
  `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt` both refuse one.
  (A garden is where a designer is most tempted to add a composter or a beehive: both are POIs and fail.)
- Beds and residents: none (a bed is a POI, so it is refused too).
- Storage: none read. (A barrel is a POI and refused; a chest is allowed by the tests, but builders never break
  containers on a take-down, so it would stay behind: in practice no container.)
- Furniture kinds a job or test needs: none (there is no `blueprint_furniture/topiary_garden.json`, and none is needed).
- Plants: every block must be one a builder can place and a survival player can get.
  - Leaves are fine: the builder places every leaf block as **persistent** (`MaterialRules`, "no decaying leaves"), so
    clipped shapes need no log near them to survive, and the list asks for the leaves item (shears in survival).
  - No `dirt_path` or `farmland` (the material-list tests' `UNOBTAINABLE` set refuses them), and no flowing water
    or other block `MaterialRules.classify` gives `SKIP` (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
  - Every block must still be **as drawn** after the build finishes (`assertExactlyBuilt` compares the plan to the
    world, ignoring neighbour-dependent properties such as `distance`, `persistent` and `snowy`). So nothing that
    changes by itself once placed: no crop or sapling that grows, nothing that needs support it lacks (a plant on a
    block it can't stand on, a hanging thing with nothing above), no water a plant would need flowing.
  - A potted plant costs a flower pot and the plant (`BuilderGameTests.pottedPlantsCostAPotAndThePlant`); both must
    be obtainable.
- Anything else the code or a test reads: nothing. No bell, sign, lectern, door, water or light is read for this
  family.

## Fits
- Plot: at most **9 x 9 footprint (x by z) and 8 tall** (`MoreDecorationsGameTests.moreDecorationsAreDecorationsWithBeautyAndAName`:
  "too big for a decoration"). y = 0 sits on the clicked block; the front is the z = 0 side. The build tests place it
  at helper origin (9, 2, 9) in the 22 x 18 x 22 `big_area`, with the bench and supply chests at helper x/z 1..5, y 2,
  outside the footprint.
- Declared size: `StarterBlueprints.TOPIARY_GARDEN` is `new Vec3i(9, 5, 9)` today. If the new drawing's size differs,
  the ship step changes that `Vec3i` to match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`),
  the size in `docs/wiki/more-decorations.md`'s table, and `decor.py`'s `("topiary_garden", topiary_garden)` entry in
  `MORE_DECORATIONS` stops overwriting the new `.nbt`.
- Materials budget (test-enforced): the material list, as whole stacks, must fit **four chests (108 stacks)** in
  `QaDecorations1104fGameTests` ("more stacks than chests") and **four barrels (108 stacks)** in
  `BuilderGameTests.buildStarter`. Leaves, logs and plants stack to 64; flower pots and potted plants count by kind.
- Build time (test-enforced): from bare flat ground, with build delay 2, a builder must finish within **12,000 ticks**
  in both build tests. The old one, 9 x 5 x 9, fits that comfortably; a much denser drawing may not.
- Front and way in: there is no door, so a village lane to it starts one block in front of the middle of the z = 0
  edge (`Paths.doorstep`'s fallback); that spot should be ground a villager can walk onto. (A door drawn in would move
  the lane's start to that door.)
- Neighbours: placed freely by the player as a decoration; walls and roads go round its footprint. No test places
  blocks round it by coordinates.

## Rules tests check
- `MoreDecorationsGameTests.buildsTopiaryGarden` (`big_area`, origin (9, 2, 9), 12,000 ticks, batch
  `decorations_2614b`): a builder stocked with exactly the plan's materials in four barrels finishes it with no block
  wrong.
- `QaDecorations1104fGameTests.topiaryGardenFromExactlyItsList` (`big_area`, origin (9, 2, 9), 12,000 ticks, batch
  `qaDeco1104fB`): built from exactly what the held blueprint's Material List asks for (four chests), it **finishes
  with every block as drawn and 0 skipped steps**; no list line is an item a survival player can't get (the test's
  `UNOBTAINABLE` set: air, bedrock, barrier, light, structure void/block, command block, spawner, petrified oak slab,
  budding amethyst, reinforced deepslate, end portal frame, farmland, dirt path, jigsaw, debug stick, knowledge book,
  chorus plant, frogspawn, trial spawner, vault, infested stone blocks); the list isn't empty.
- `MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock`: no villager POI block in the template.
- `MoreDecorationsGameTests.moreDecorationsAreDecorationsWithBeautyAndAName`: in `DECORATIONS`, a lang name, size
  <= 9 x 8 x 9, and **the Topiary Garden's beauty is exactly 3** (by id).
- `MoreDecorationsGameTests.flowerPlantersStartUnlockedTheRestComeFromMarketDay`: starts locked with the market hint
  (by id).
- `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt`: listed on the Table
  with a size and at least one block, locked with a readable hint, named **"Topiary Garden"** (lang key
  `blueprint.aliveworkplace.topiary_garden`; American English, unique among the decorations, capitalised, no `.` or
  `_`), at least one material on the Table and none unobtainable, no creative-only block (bedrock, barrier, light,
  structure/command blocks, spawners, vaults, jigsaw, portals, end portal frame, reinforced deepslate, budding
  amethyst, petrified slab, infested blocks, moving piston) and no job block in the template, and the
  `built/topiary_garden` advancement exists.
- `ZoneVillageBuildingsGameTests` (the zone test that records a Topiary Garden, a Market Square and a Chapel): by id
  and origin only: one finished outside the village's claimed area adds no beauty and no rank; one in a painted zone
  adds its beauty (> 0) and one building to the rank; forgotten (torn down) it stops counting at once. The drawing
  doesn't matter here beyond the footprint's middle.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt`'s size equals the declared
  `Vec3i`, and every block in it is one a builder can place (none classified `SKIP`).
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, and `holes.py`
  passes.

## Free
Everything not listed above: which plants and shapes, how many, the walks, any seat, urn, statue or light, the edge,
height and footprint within 9 x 8 x 9, materials and style, as long as it holds no job block or container, every
block is placeable, obtainable and stays as drawn, and the list fits 108 stacks and builds within 12,000 ticks.
