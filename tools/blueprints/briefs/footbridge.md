# Footbridge: the brief

Family id(s): `footbridge` (never renamed)   Variants: village (none drawn), modded (none: decorations are never
dressed by `modded_library.py`), looks (none)

One tier only: there is no `footbridge_2`, no upgrade, and nothing in the code asks for one (`more-decorations.md`,
"Known limits": "No upgrades: each is one tier").

## What it is for
A village decoration: a crossing over its own stretch of water (26.14, the owner: "a wider variety of builds that
aren't houses"). A finished Footbridge within a Village Hall's claimed area adds 2 beauty points, and so wellbeing,
to the village (`hall.Decorations.POINTS`). Nobody works, lives or meets at it. The player gets it from the Blueprint
Table, where it starts locked ("A market-day trader": a traveling trader on market day may sell it, a builder's
level-up unlocks it, or a found Footbridge blueprint used on the Table unlocks it). The Steward never builds one (no
`steward_rules` file names it); only a player places it. A builder builds it from the plan's materials and can take it
down again (sneak hand-over), scooping its water back up.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `footbridge` | 2 beauty points (`Decorations.POINTS`); marked on the village map as a decoration (`VillageMaps`, by id); a hidden `aliveworkplace:built/footbridge` advancement | Blueprint Table, locked with the hint `screen.aliveworkplace.table.hint.market` (`BlueprintUnlocks.hint`: every entry of `StarterBlueprints.DECORATIONS`); in the market-day traders' pool (`MarketDays`); in the creative tab (`ModItems`); never rebuilt by the Grand Rebuild (`GrandRebuild`) |

## Must have (each level)
- Job block(s): **none, and none allowed.** No block in the template may be a villager point of interest (no barrel,
  composter, cauldron, lectern, smoker, loom, bell, bed, ...): `MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock`
  and `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt` both refuse one.
- Beds and residents: none (a bed is a POI, so it is refused too).
- Storage: none read. (A barrel is a POI and refused; a chest is allowed by the tests but builders never break
  containers on a take-down, which would fail the take-down test below: so in practice no container.)
- Furniture kinds a job or test needs: none (there is no `blueprint_furniture/footbridge.json`, and none is needed).
- **Water: exactly 15 poured water cells**, every one a still **source**.
  - `QaDecorations1104fGameTests.footbridgeOverARealStreamFinishesWithWaterUnderIt` asserts the plan pours exactly
    15 water ("setup: the footbridge pours N water, expected 15"). It counts every planned block whose fluid is water,
    so **a waterlogged block counts as one of the 15**.
  - Three of them are pinned by position: **(3, 0, 2), (3, 0, 3) and (3, 0, 4)** must be water sources (the test calls
    them "the bridge's water under the deck"). Under exactly those three cells the test lays a real stream, and it must
    still be water after the build, so those cells must not be solid (a solid bottom-layer block would get a foundation
    column poured into the stream under it).
  - The water must be held in by the drawing itself on every side. y = 0 sits on the clicked block, so water at y = 0
    stands above the ground round it: no water cell may touch an outside edge of the footprint or a drawn-air cell
    beside or below it, or it flows out. After the build no water may flow anywhere, inside or outside the footprint
    (the stream test fails on any non-source water block).
  - Each water source costs one water bucket (the list asks for 15) and is poured from a bucket; taken down, each is
    scooped back with an empty bucket (B961).
- Anything else the code or a test reads: nothing. No bell, sign, lectern or door is read for this family.

## Fits
- Plot: at most **9 x 9 footprint (x by z) and 8 tall** (`MoreDecorationsGameTests.moreDecorationsAreDecorationsWithBeautyAndAName`:
  "too big for a decoration"). The three pinned water cells at x = 3 mean the footprint is at least 4 wide and 5 deep.
  y = 0 sits on the clicked block; the front is the z = 0 side.
- Declared size: `StarterBlueprints.FOOTBRIDGE` is `7, 4, 7` today. If the new drawing's size differs, the ship step
  changes that `Vec3i` to match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`) and the
  size in `docs/wiki/more-decorations.md`'s table.
- Front and way in: there is no door, so a village lane to it starts one block in front of the middle of the z = 0
  edge (`Paths.doorstep`'s fallback); that spot should be ground a villager can walk onto. The stream test lays its
  stream running side to side (along x) under rows z = 2..4, so the build is crossed from the front (z = 0) towards the
  back over water running across it.
- Neighbours: placed freely by the player as a decoration; walls and roads go round its footprint.

## Rules tests check
- `QaDecorations1104fGameTests.footbridgeOverARealStreamFinishesWithWaterUnderIt` (`big_area`, origin helper
  (6, 2, 6); a 3-wide stream of still water 1 deep at helper y 1, z 8..10, x 3..18, i.e. under the build's rows
  z = 2..4 at y = -1): built from exactly the held blueprint's Material List it **finishes, every block as drawn,
  0 skipped**; the builder ends **alive and with his eyes out of water**; the stream under it at (3, -1, 2..4) is still source
  water; the build's own water at (3, 0, 2..4) is source water; exactly 15 water cells planned; and **no other water**
  anywhere in the area (helper 0..21, y 1..12), and no flowing water at all.
- `QaDecorations1104fGameTests.footbridgeFromExactlyItsList` (`big_area`, origin (9, 2, 9), flat ground): built from
  exactly what the held blueprint's Material List asks for, it finishes with every block as drawn and **0 skipped
  steps**. Every list line must be an item a survival player can get (no dirt path, farmland, light, barrier,
  structure void, petrified slab, infested or creative-only blocks: the test's `UNOBTAINABLE` set), and with empty
  chests every line asks to bring all it needs.
- `QaDecorations1104fBugTests.footbridgeTakenDownLeavesNoWaterAndGivesBackItsCost` (`big_area`, origin (9, 2, 9);
  the shared `QaDecorations1104fGameTests.takeDown`): built from exactly its list (0 skipped), then sneak-handed back
  to the builder for a take-down; 100 ticks after it ends **no built block stands, no fluid (source or flowing) is left
  anywhere** in helper x/z 0..21, y 1..12, no fire, the builder's bag is empty, **every item of the list is back
  exactly** (in the chests, the bag or on the ground), and the water buckets plus empty buckets back equal the water
  buckets paid. So every block must come down as the item it cost, and the footprint must lie inside helper x/z 0..21
  from origin (9, 2, 9) (it does at 9 x 9).
- `MoreDecorationsGameTests.buildsFootbridge` (`big_area`, origin (9, 2, 9), 9,000 ticks): a builder stocked with
  exactly the plan's materials in barrels at helper (1..5, 2, 4) finishes it with no block wrong.
- `MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock`: no villager POI block in the template.
- `MoreDecorationsGameTests.moreDecorationsAreDecorationsWithBeautyAndAName`: in `DECORATIONS`, beauty >= 1, a lang
  name, size <= 9 x 8 x 9.
- `MoreDecorationsGameTests.flowerPlantersStartUnlockedTheRestComeFromMarketDay`: starts locked with the market hint
  (by id).
- `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt`: listed on the Table
  with a size and at least one block, named "Footbridge" (American English, unique), materials obtainable, no
  creative-only block (bedrock, barrier, light, structure/command blocks, spawners, vaults, jigsaw, portals, end portal
  frame, reinforced deepslate, budding amethyst, petrified slab, infested blocks, moving piston) and no job block in the
  template, and the `built/footbridge` advancement exists.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt`'s size equals the declared
  `Vec3i`, and every block in it is one a builder can place (none classified `SKIP`).
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, and `holes.py`
  passes.

## Free
Everything not listed above: what the crossing and the water's banks are, where the other 12 water cells go (as long
as there are exactly 15, all held in, with the three pinned at x = 3), size within 9 x 8 x 9, materials, lighting,
plants and style.
