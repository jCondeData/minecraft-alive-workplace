# Flower Planters: the brief

Family id(s): `flower_planters` (never renamed)   Variants: village (none drawn), modded (none: decorations are never
dressed by `modded_library.py`), looks (none)

## What it is for
A small village decoration: flowers a player has a Builder build to make a village prettier, the one cheap decoration
every new village can build from day one. A finished set of Flower Planters within a Village Hall's reach adds beauty
(and so wellbeing) to the village. It hands out no job and no meeting place, and nobody lives or works in it. It is the
**only** 26.14 decoration that starts **unlocked** at the Blueprint Table (part of the wiki's starting set of 20); it is
in the creative tab and the market-day traders' pool like every decoration, has a hidden
`aliveworkplace:built/flower_planters` advancement for quests, is **not** marked on the village map ("too small to
mark", `hall/VillageMaps`), and the Grand Rebuild never rebuilds it.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `flower_planters` | 1 beauty point (`hall/Decorations.POINTS`) | In `StarterBlueprints.MORE_DECORATIONS` (so in `DECORATIONS`). Unlocked from the start: listed in `data/aliveworkplace/blueprint_unlocks/starting.json` (`MoreDecorationsGameTests.flowerPlantersStartUnlockedTheRestComeFromMarketDay`, `QaBlueprintLibraryVerify1108bGameTests.aNewVillageStartsWithTheWikisSetAndTheRestLockedOrRare`). |

One level only: there is no `flower_planters_2` and none may be added (an upgrade file would make `BlueprintUpgrades`
offer one, and the 26.14 decorations are one tier each).

## Must have
- Job block(s): **none, and none allowed.** No block in the drawing may be a villager point of interest (no job block,
  bed, bell, beehive, barrel, composter, cauldron, lectern...): `MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock`
  and `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt` check every block with
  `PoiTypes.forState`.
- Beds and residents: none. No code counts beds here.
- Storage: none. No job reads a container here (and builders never break or fill containers, so a drawn one only
  complicates its build and take-down).
- Furniture kinds a job or test needs: none (there is no `blueprint_furniture/flower_planters.json`, and none is needed).
- **At least one plain lantern** (`minecraft:lantern`, not a soul lantern), so `Items.LANTERN` is on its Material List.
  `QaDecorations1108GameTests.flowerPlantersWithoutTheirLanternsWaitThenFinishWhenTheyArrive` fails at once if the plan
  places no `Blocks.LANTERN` or the list has no lantern. In that test:
  - stocked with everything **but** the lanterns, the builder must build all the rest, then **wait** (status waiting for
    materials, missing exactly the plan's lantern count) with **0 skipped** steps the whole time;
  - every lantern spot must stay **air** until the lanterns come: nothing else in the drawing may be placed at a lantern
    spot, and nothing the lantern needs (the block it stands on or hangs from) may itself be a lantern;
  - all the lanterns are then put in **one slot** of one chest as a single stack, so the lantern count must be
    **at most 64**;
  - it must finish with every block as drawn before **tick 8,800**.
- **Flowers.** Not read by any code, but it is the family's name: it should hold flowers. Every plant stands in its own
  soil (the owner's rule and `check.py`), and see the "nothing changes by itself" rule below.
- Anything else the code or a test reads: nothing. No bell, sign, lectern, door or water is read for this family.

## Every block must be one a survival builder can place and take back
- No block a builder can't place (`MaterialRules.classify` == SKIP, e.g. flowing water, fire, air-like technical
  blocks): `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.
- No creative-only block and no material a survival player can't get (bedrock, barrier, light, structure or command
  blocks, spawners, budding amethyst, **farmland, dirt path**, infested blocks, petrified slab...):
  `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt` (its `UNOBTAINABLE` and
  `CREATIVE_BLOCKS` lists).
- Built from **exactly** its Material List it must finish **as drawn with 0 skipped steps**
  (`QaDecorations1104fGameTests.flowerPlantersFromExactlyItsList`). So nothing may change by itself after it is placed:
  no dirt beside grass (it turns to grass), no crops or saplings that grow, no concrete powder near water, no ice,
  nothing that needs a block a later pass puts down to survive, nothing that falls.
- Stocking: the tests put the whole list into 4 containers of 27 slots, so **(stacks of every item) <= 108**
  (`BuilderGameTests.buildStarter`, `QaDecorations1104fGameTests.exact`, `QaDecorations1108GameTests.start`: "more
  stacks than chests").
- It is the cheap starter decoration (wiki, 26.14: "a cheap pretty one lets a new village start decorating at once"):
  keep its Material List small enough for a brand-new village to afford. No code measures this; the tight time budgets
  below do.

## Fits
- Plot: at most **9 (x) by 9 (z)** footprint, at most **8** tall
  (`MoreDecorationsGameTests.moreDecorationsAreDecorationsWithBeautyAndAName`). Every build test places it at origin
  (9, 2, 9) in `big_area`, with the builder's bench and chests at helper x/z 1..4, so it never meets them.
- Declared size: `StarterBlueprints.FLOWER_PLANTERS` is `7, 3, 3` today. If the new drawing's size differs, the ship
  step changes that `Vec3i` to match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`), and
  the size column of `docs/wiki/more-decorations.md`.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- Front and way in: no door is needed. When the builder finishes, his path back to his bench starts one block in front
  of the middle of the z = 0 edge (`Paths.doorstep`'s fallback), so that spot should be ground a villager can walk
  onto from the front.
- Neighbours: placed freely by the player (no village plan builds it or places anything beside it). It is meant to
  sit beside a path or a house, so a drawing that also reads well against a wall is a plus, not a rule.

## Rules tests check
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt`'s size equals
  `StarterBlueprints.FLOWER_PLANTERS`'s, and no block in it is SKIP.
- `MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock`: no POI block.
- `MoreDecorationsGameTests.moreDecorationsAreDecorationsWithBeautyAndAName` (in `DECORATIONS`, beauty >= 1, a lang
  name, size within 9 x 8 x 9) and `flowerPlantersStartUnlockedTheRestComeFromMarketDay` (starts unlocked; by id).
- `QaBlueprintLibraryVerify1108bGameTests.aNewVillageStartsWithTheWikisSetAndTheRestLockedOrRare`: it is in a new
  village's unlocked set (by id).
- `MoreDecorationsGameTests.buildsFlowerPlanters`: a builder with the plan's materials builds it at origin (9, 2, 9) in
  `big_area`, every block as drawn, within **6,000 ticks** (the test's timeout, shared with setup).
- `QaDecorations1104fGameTests.flowerPlantersFromExactlyItsList`: built from exactly the held blueprint's Material
  List, finished as drawn, **0 skipped**, within 12,000 ticks.
- `QaDecorations1108GameTests.flowerPlantersWithoutTheirLanternsWaitThenFinishWhenTheyArrive`: see "At least one plain
  lantern" above (waits, never puts anything else in a lantern spot, 0 skipped, at most 64 lanterns, finished as drawn
  before tick 8,800 including the wait).
- `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt`: listed on the Table
  with a size and at least one block, unlocked, named "Flower Planters" (by id, American English, unique among the
  decorations), materials all obtainable, no creative or POI block, its `built/flower_planters` advancement exists.
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, and `holes.py`
  passes. The old drawing is baselined in `owner_rules_baseline.json` with **2** owner-rule problems (its lanterns
  stand on bare 2-high pillars a person can stand beside: "a short lamp goes on a table, side table or slab, or hangs",
  `check.py` `lamp_problems`); the rebuild must clear that, so the ship step drops `flower_planters` from the baseline.

## Free
Everything not listed above: how many planters and what shape, which flowers and how many, the soil, what holds and
frames them, where the lanterns go (standing on a post, slab or table, or hanging), seats or paths beside them,
materials, height up to 8, footprint up to 9 x 9, and style.
