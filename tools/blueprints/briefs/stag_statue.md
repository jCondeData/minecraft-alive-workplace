# Stag Statue: the brief

Family id(s): `stag_statue` (never renamed; one level, no `stag_statue_2`)   Variants: village (none drawn: a Steward
zone's style is applied to the drawing automatically by `BlueprintStyles`), modded (none: decorations are never
dressed by `modded_library.py`), looks (none)

Not in this family: the Founder's statue (`legend/Founder`, 29.23), which adds its own beauty while it stands, and the
Mega Shrine. They are other builds; nothing here changes them.

## What it is for
A village decoration: a statue of a stag that a player has a Builder build to make a village prettier. That it shows a
stag is its name ("Stag Statue"), so a player must read it as a stag. A finished Stag Statue within a Village Hall's
claimed area adds 2 beauty (and so 2 % wellbeing, up to the 10 % cap) to the village, and counts toward the beauty
need of villagers whose bed is near it. It hands out no job and no meeting place, and nobody lives or works in it. The
player gets it from the Blueprint Table (locked at first, "A market-day trader": a market-day trader may sell it, a
builder's level-up can unlock it, or a found Stag Statue blueprint used on the Table unlocks it). It is marked on the
village map as a decoration, has a hidden `aliveworkplace:built/stag_statue` advancement for quests, is in the creative
tab, and the Grand Rebuild never rebuilds it. The Steward has no rule that builds it: only a player places it.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `stag_statue` | 2 beauty points (`hall/Decorations.POINTS`); a decoration mark on the village map (`hall/VillageMaps`, kind DECORATIONS, by id); its "built" advancement (`advancement/built/stag_statue.json`, by id) | In `StarterBlueprints.MORE_DECORATIONS` (so in `DECORATIONS`: the Table, the creative tab, the market-day traders' pool in `hall/MarketDays`, excluded from `legend/GrandRebuild`). Starts locked with the hint `screen.aliveworkplace.table.hint.market`. No loot table or Steward rule names it. |

One level only: there is no `stag_statue_2` and none may be added (an upgrade file would make `BlueprintUpgrades` offer
one, and the 26.14 decorations are one tier each).

## Must have
- Job block(s): **none, and none allowed.** No block in the drawing may be a villager point of interest (no job block,
  bed, bell, beehive, barrel, composter, cauldron, lectern, smoker, loom, grindstone...):
  `MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock` and
  `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt` check every block with
  `PoiTypes.forState`.
- Beds and residents: none. No code counts beds here.
- Storage: **none.** No job reads a container here, and builders never break containers on a take-down, so a drawn
  chest or barrel would be left standing.
- Furniture kinds a job or test needs: none (there is no `blueprint_furniture/stag_statue.json`, and none is needed).
- Anything else the code or a test reads: nothing. No water, bell, sign, lectern, door or light level is read for this
  family. (If water is drawn in, the Fountain's and Lily Pond's water rules apply: still sources only, one bucket each,
  held in on every side; nothing asks for any.)

## Every block must be one a survival builder can place and keep in place
- No block a builder can't place (`MaterialRules.classify` == SKIP: flowing water or lava, fire, technical blocks):
  `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.
- No creative-only block and no material a survival player can't get (bedrock, barrier, light, structure or command
  blocks, jigsaw, spawners, budding amethyst, reinforced deepslate, end portal frame, farmland, dirt path, chorus plant,
  frogspawn, petrified slab, infested blocks, vault, trial spawner): the `UNOBTAINABLE` and `CREATIVE_BLOCKS` lists in
  `QaDecorations1104fGameTests` and `QaDecorations1108GameTests`.
- Built from **exactly** its Material List it must finish **as drawn with 0 skipped steps**
  (`QaDecorations1104fGameTests.stagStatueFromExactlyItsList`). So nothing may change by itself once placed: no dirt
  beside grass (it turns to grass), no crops or saplings that grow, no concrete powder near water, no ice, nothing that
  needs a block a later pass puts down to stand (a plant stands in its own soil, a hanging or wall-mounted block on
  something drawn). Every block in the drawing must also be reachable for a builder to place it.
- Stocking: the tests put the whole list into 4 containers of 27 slots, so the material list must fit **108 stacks**
  (`BuilderGameTests.buildStarter`, "test needs more barrels"; `QaDecorations1104fGameTests.stock`, "more stacks than
  chests").

## Fits
- Plot: at most **9 (x) by 9 (z)** footprint and at most **8 tall**, including everything that rises above the plinth
  (`MoreDecorationsGameTests.moreDecorationsAreDecorationsWithBeautyAndAName` refuses a declared size over 9 x 8 x 9 for
  any of the 26.14 decorations). The tests build it at origin (9, 2, 9) in `big_area` (22 x 18 x 22) with the bench,
  barrels and chests at helper x 1-5, z 1-5, so anything inside 9 x 8 x 9 fits. The old one was 7 x 8 x 5, already at
  the height limit.
- Declared size: `StarterBlueprints.STAG_STATUE` is `new Vec3i(7, 8, 5)` today. If the new size differs, the ship step
  changes that `Vec3i` to match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), the
  wiki's size column (`docs/wiki/more-decorations.md`), and `decor.py`'s `("stag_statue", stag_statue)` entry in
  `MORE_DECORATIONS` stops overwriting the new `.nbt`.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- Front and way in: no door is needed. When the builder finishes, his path back to his bench starts one block in front
  of the middle of the z = 0 edge (`build/Paths.doorstep`'s fallback when there is no door), so that spot should be
  ground a villager can walk onto from the front. (A door drawn in would move the path's start to that door.)
- Neighbours: placed freely by the player; nothing is placed beside it by a village plan. Walls and roads go round its
  footprint.

## Rules tests check
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt`'s size equals
  `StarterBlueprints.STAG_STATUE`'s, and no block in it is SKIP.
- `MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock`: no POI block.
- `MoreDecorationsGameTests.moreDecorationsAreDecorationsWithBeautyAndAName` (in `DECORATIONS`, beauty >= 1, a lang name,
  size within 9 x 8 x 9) and `flowerPlantersStartUnlockedTheRestComeFromMarketDay` (locked, market hint): by id.
- `MoreDecorationsGameTests.buildsStagStatue` (batch `decorations_2614a`, `big_area`, origin (9, 2, 9), no rotation):
  a builder with exactly the plan's materials in 4 barrels builds it from bare ground within **9,000 ticks** (build
  delay 2), every block as drawn. This is its tightest time budget: keep the number of blocks modest (the old one
  placed 86).
- `QaDecorations1104fGameTests.stagStatueFromExactlyItsList` (batch `qaDeco1104fA`, origin (9, 2, 9), 12,000 ticks):
  built from exactly the held blueprint's Material List (every line obtainable, the bench found beside it), finished
  as drawn, **0 skipped**.
- `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt`: listed on the Table
  with a size and at least one block, locked with a readable hint, its name "Stag Statue" (lang key
  `blueprint.aliveworkplace.stag_statue`: capitalized, American English, unlike every other decoration's name),
  materials all obtainable, no creative or POI block in the template, its `built/stag_statue` advancement exists.
- The showcase scene `decor` places every decoration side by side (by id; no test reads the drawing there).
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, and `holes.py`
  passes.

## Free
Everything not listed above: the stag's pose and which way it faces, what it stands on, everything round it (planting,
paving, seats, lights), materials, footprint and height within 9 x 9 x 8, and style, as long as a player reads it as a
stag.
