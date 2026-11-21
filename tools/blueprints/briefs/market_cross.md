# Market Cross: the brief

Family id(s): `market_cross` (never renamed; one level, no `market_cross_2`)   Variants: village (none drawn: a
market-day trader may sell it in a style, and `BlueprintStyles` applies that style to the drawing automatically),
modded (none: `modded_library.py` skips everything in `MORE_DECORATIONS`), looks (none)

Not in this family: the **Market Square** (`market_square`, beauty 5, a TRADE build with the village bell) and the
**Market Corner** (`market_corner`, beauty 1, too small for a map mark). They are rebuilt in the same ROADMAP item
(51.7) but are separate families with their own briefs; nothing here changes them, and the Market Cross must not
borrow their jobs (no bell, no stall, no POI block of any kind).

## What it is for
A small village decoration from ROADMAP 26.14: the market cross a village raises where people meet and trade, a
landmark for its square or crossroads. It has no job and no residents. A finished Market Cross within a Village
Hall's claimed area adds **2 beauty** (2 % wellbeing, within the 10 % cap shared by all decorations) and counts for
the beauty need of villagers whose beds are near it. The player builds it from the Blueprint Table once it is
unlocked; it is never built by the Steward on his own.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `market_cross` | 2 beauty points (`hall/Decorations.POINTS`); a Decorations-kind mark on the village map (`hall/VillageMaps`, by id); its own "built" advancement (`advancement/built/market_cross.json`, by id); never redrawn by a Legend's Grand Rebuild (`legend/GrandRebuild.never`, every decoration); in the creative tab (`ModItems`, every decoration) | Starts **locked** at the Table with the hint `screen.aliveworkplace.table.hint.market` (`table/BlueprintUnlocks.hint`, every decoration). Unlocked by buying it from a market-day trader (`hall/MarketDays.addBlueprintOffer` draws from every decoration, plain or in a style), by using a found Market Cross blueprint on the Table, or by a builder's level-up (tier I). No Steward rule, no loot table and no upgrade exists or is asked for: adding a `market_cross_2` would break `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`'s count of 64 upgrades. |

## Must have
- Job block(s): **none, and no villager POI block of any kind**. `MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock`
  and `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt` fail on any block
  that `PoiTypes.forState` knows: every vanilla workstation (lectern, composter, **barrel**, smoker, grindstone,
  cartography, fletching or smithing table, stonecutter, loom, cauldron, brewing stand, blast furnace), a **bell**, a
  **bed**, a beehive or bee nest, and the mod's own job blocks. A market piece invites barrels and a bell: both are
  POIs and fail. (A chest is not a POI.)
- Beds and residents: none. No code counts beds here.
- Storage: none read. A container drawn in is free, but builders never break containers on a take-down, so it would
  stay behind; and its contents are never copied.
- Furniture kinds a job or test needs: none (there is no `blueprint_furniture/market_cross.json`, and none is needed).
- Anything else the code or a test reads: nothing. No sign, water, fire, light level, lectern or door is read for this
  family. If water or fire is drawn anyway, every water cell must be a source held in so nothing flows (flowing water
  is `SKIP` and fails the buildable test; anything that changes cells after building fails "as drawn").

## Fits
- Plot: hard limit **at most 9 wide (x) x 8 tall (y) x 9 deep (z)**:
  `MoreDecorationsGameTests.moreDecorationsAreDecorationsWithBeautyAndAName` asserts it for every 26.14 decoration
  (tighter than the 22 x 18 x 22 `big_area` the build tests use, origin helper (9, 2, 9)). Height 8 is the ceiling,
  not a target; every block of width also costs room in small villages. y = 0 sits on the clicked block; the front
  is the z = 0 side.
- Declared size: `StarterBlueprints.MARKET_CROSS` is `new Vec3i(5, 8, 5)` today. If the new size differs, the ship
  step changes that `Vec3i` to match (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`),
  adds `market_cross` to `generate.py`'s `REBUILT` so the old `decor.market_cross` stops overwriting the new `.nbt`,
  and fixes the size in `docs/wiki/more-decorations.md`'s table.
- Materials budget (test-enforced): the material list, as stacks, must fit **108 stacks** (four barrels in
  `BuilderGameTests.buildStarter`, four chests in `QaDecorations1104fGameTests`).
- Build time (test-enforced): `MoreDecorationsGameTests.buildsMarketCross` gives the builder **9,000 ticks** and
  `QaDecorations1104fGameTests.marketCrossFromExactlyItsList` **12,000 ticks** (build delay 2) from bare ground. Keep
  the block count in line with that: a solid-filled 9 x 8 x 9 risks a timeout.
- Front and way in: no door is needed. A lane to a build without a door starts one block in front of the middle of the
  z = 0 edge (`Paths.doorstep`'s fallback), so that spot should be ground a villager can walk onto from the front.
  It is a meeting place, so villagers should be able to walk up to it, but no code checks that.
- Neighbours: placed by the player anywhere; walls and roads go round its footprint. No test places blocks round it
  by coordinates (the build tests keep x 0..5, z 0..5 of the test area for the bench, chests and barrels, outside any
  footprint at origin (9, 2, 9)).

## Rules tests check
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt`'s size equals
  `StarterBlueprints.MARKET_CROSS`'s size, and every block is one a builder can place (none classified `SKIP`: no
  flowing liquids, no technical blocks).
- `MoreDecorationsGameTests`:
  - `moreDecorationsHoldNoJobBlock`: no POI block (above).
  - `moreDecorationsAreDecorationsWithBeautyAndAName`: in `StarterBlueprints.DECORATIONS`, beauty >= 1, a name in the
    lang file, size <= 9 x 8 x 9.
  - `flowerPlantersStartUnlockedTheRestComeFromMarketDay`: starts locked, hint `...table.hint.market`.
  - `buildsMarketCross` (batch `decorations_2614a`, `big_area`, origin (9, 2, 9), no rotation, 9,000 ticks): a builder
    with exactly the plan's materials in four barrels builds it from bare ground and finishes.
- `QaDecorations1104fGameTests.marketCrossFromExactlyItsList` (batch `qaDeco1104fA`, `big_area`, origin (9, 2, 9),
  12,000 ticks): the Material List the player reads on the held blueprint is non-empty, every line an item a survival
  player can get (no air, bedrock, barrier, light, structure void/block, command block, spawner, petrified oak slab,
  budding amethyst, reinforced deepslate, end portal frame, **farmland, dirt path**, jigsaw, chorus plant, frogspawn,
  trial spawner, vault, infested stone); built from exactly that list it **finishes with every block as drawn and 0
  skipped steps**. So nothing in it may change after it is placed: no crops that grow, no hanging lantern or torch
  without the block it hangs from, nothing that needs support it doesn't have, nothing that drops or flows.
- `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt`: listed on a new
  village's Table by id with a size and blocks, locked with a hint that reads as words; its name has no `.` or `_`,
  starts with a capital, is American English and differs from the other thirteen; the Table shows materials, none
  unobtainable; the template holds no creative-only block (bedrock, barrier, light, structure/command blocks, spawner,
  trial spawner, vault, jigsaw, portals and portal frames, reinforced deepslate, budding amethyst, petrified oak slab,
  infested blocks, moving piston) and no POI block; the advancement `aliveworkplace:built/market_cross` exists.
- By id only (the drawing doesn't matter, the id and the name do): the name stays **Market Cross**
  (`blueprint.aliveworkplace.market_cross`), the id stays `market_cross` (the advancement, `VillageMaps`,
  `Decorations`, `tools/review/gallery.py` and the showcase name it).
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, and `holes.py`
  passes. (Owner's rule 2 matters for a lantern-lit piece: a lantern never stands on a floor; it hangs, or sits on a
  post, slab or table.)

## Free
Everything not listed above: what the cross, its base and its head are made of and how they are shown, any steps,
seating, lighting, planting, paving or market dressing (no barrel, no bell), materials, shapes, height and footprint
within 9 x 8 x 9, and style.
