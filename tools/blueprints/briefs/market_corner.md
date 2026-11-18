# Market Corner: the brief

Family id(s): `market_corner` (never renamed; one level, no `market_corner_2`; "Market Corner" in `en_us.json`,
`blueprint.aliveworkplace.market_corner`; hidden advancement `built/market_corner`)   Variants: village (none drawn: a
market-day trader may sell it in a style, and `BlueprintStyles` applies that style to the drawing automatically),
modded (none: `modded_library.py` skips everything in `MORE_DECORATIONS`, `NOT_BUILDINGS`), looks (none)

ROADMAP 51.7 (with the Market Square and the Market Cross, which are separate families with their own ids, beauty and
tests; nothing here changes them).

## What it is for
A small village decoration from ROADMAP 26.14: a corner of market goods a player sets out by a street or square to
make the village feel lived in and traded in. It has no job, no shop, no trading, no storage and no residents: nothing
in the game reads what the goods are. A finished Market Corner within a Village Hall's claimed area adds **1 beauty**
(1 % wellbeing, within the 10 % cap shared by all decorations: `hall/Decorations.POINTS`, `bonus`), and counts for the
beauty need and mood of villagers whose beds are near it (`ClassNeeds.beauty`, `Moods`: by the finished build's
record, not by any block in it). The player builds it from the Blueprint Table once it is unlocked; the Steward never
builds it on his own.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `market_corner` | 1 beauty point (`Decorations.POINTS`); **no mark** on the village map (`VillageMaps.kindOf`: "too small to mark", by id); its own "built" advancement (`advancement/built/market_corner.json`, by id); never redrawn by a Legend's Grand Rebuild (`GrandRebuild.never`, every decoration); in the creative tab with the decorations (`ModItems`) | Starts **locked** at the Table with the hint `screen.aliveworkplace.table.hint.market` (`BlueprintUnlocks.hint`, every decoration). Unlocked by buying it from a market-day trader (`MarketDays.addBlueprintOffer` draws from every decoration, plain or in a style), by using a found Market Corner blueprint on the Table, or by a builder's level-up. No Steward rule, loot table or upgrade exists or is asked for. |

## Must have (each level)
- Job block(s): **none, and no villager POI block of any kind.** `MoreDecorationsGameTests.moreDecorationsHoldNoJobBlock`
  and `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt` fail on any block
  `PoiTypes.forState` knows: every vanilla workstation (**barrel**, composter, smoker, lectern, grindstone, cartography,
  fletching or smithing table, stonecutter, loom, any cauldron, brewing stand, blast furnace), a **bell**, a **bed**, a
  beehive or bee nest, and the mod's own job blocks (Blueprint Table, Builder's Bench, mailbox, shop counter, ...).
  A barrel is a POI (the Fisherman's); a chest, a crate drawn from planks or a hay bale is not.
- Beds and residents: none (a bed is a POI, so none may be drawn).
- Storage: none read. A container drawn in is free but discouraged: builders never fill or copy containers, and on a
  take-down they never break one, so it would stay behind empty.
- Furniture kinds a job or test needs: none (there is no `blueprint_furniture/market_corner.json` today, and none is
  needed). If the drawing has seats, tables or other furniture pieces, `rebuild.py` records them as spots and writes a
  furniture file; then `FurnitureSpotGameTests` (each spot's blocks exactly the template's),
  `FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece` (nothing small on air) and
  `QaChairFacingGameTests` (every chair stair opens toward its table, turned and mirrored too) apply to it.
- Anything else the code or a test reads: nothing. No sign, water, fire, light level, door, goods or produce is read
  for this family. If water or fire is drawn anyway, every water cell must be a source held in so nothing flows
  (flowing water classifies as `SKIP` and fails the buildable test), and a campfire on a stack of blocks must keep that
  stack full and solid in every style (`StyleGameTests.chimneysStayWholeInEveryStyle`).
- **Nothing that changes after it is placed.** The build must end with every block exactly as drawn (see "Rules
  tests check"): no crops, saplings, berry bushes, stems or other plants that grow, nothing that needs support it
  doesn't have, nothing that falls (sand, gravel, concrete powder, anvils) without a block under it, nothing that
  flows, melts or burns out. A standing lantern, candle or torch stands on a block whose top holds its middle (not a
  bottom slab, a fence gate, a chest): `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`; no dirt path
  or farmland under a solid block (same test; and both are unobtainable items anyway, below).
- Every block one a survival player can get and a builder can place (below).

## Fits
- Plot: hard limit **at most 9 wide (x) x 8 tall (y) x 9 deep (z)**:
  `MoreDecorationsGameTests.moreDecorationsAreDecorationsWithBeautyAndAName` asserts it for every 26.14 decoration
  (tighter than the 22 x 18 x 22 `big_area` the build tests use, origin helper (9, 2, 9)). It is a "corner" piece
  for a street side and small villages: every block of footprint costs room, so smaller is better within the
  limit, but no code asks for a size. y = 0 sits on the clicked block; the front is the z = 0 side.
- Declared size: `StarterBlueprints.MARKET_CORNER`'s `Vec3i` must equal the new file's size
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`). The ship step changes that `Vec3i`
  (and its javadoc line) to match, adds `market_corner` to `generate.py`'s `REBUILT` so the old `decor.market_corner`
  stops overwriting the new `.nbt`, and fixes the row in `docs/wiki/more-decorations.md`'s table.
- Materials budget (test-enforced): the material list, as stacks, must fit **108 stacks** (four barrels of 27 in
  `BuilderGameTests.buildStarter`, four chests of 27 in `QaDecorations1104fGameTests.stock`). The Material List book
  puts 7 lines a page and must stay a book the game can save (`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`).
- Build time (test-enforced): `MoreDecorationsGameTests.buildsMarketCorner` gives the builder **9,000 ticks** and
  `QaDecorations1104fGameTests.marketCornerFromExactlyItsList` **12,000 ticks** (build delay 2, builders' help off)
  from bare ground. Keep the block count in line with that: a solid-filled 9 x 8 x 9 risks a timeout.
- Front and way in: no door is needed, and none is expected. A lane to a build without a door starts one block in
  front of the middle of the z = 0 edge (`Paths.doorstep`'s fallback: `walkable` at the box's centre x, min z - 1), so
  that spot should be ground a villager can walk onto from the front. If a door is drawn anyway, `Paths.doorstep`
  uses it instead and `QaDoorwayClearanceGameTests` (a villager 1.95 tall walks through it, nothing under the lintel)
  applies. A player or villager should be able to step up to the goods from the front (it is a market piece people
  browse), but no code checks that.
- Neighbours: placed by the player anywhere; walls and roads go round its footprint. No village plan, Steward rule or
  zone places it, and no test puts blocks round it by coordinates.

## Rules tests check
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the `.nbt`'s size equals
  `StarterBlueprints.MARKET_CORNER`'s size, and every block is one a builder can place (none classified `SKIP` by
  `MaterialRules.classify`: no flowing liquids, no technical blocks).
- `MoreDecorationsGameTests`:
  - `moreDecorationsHoldNoJobBlock`: no POI block (above).
  - `moreDecorationsAreDecorationsWithBeautyAndAName`: in `StarterBlueprints.DECORATIONS`, beauty >= 1, a name in the
    lang file, size <= 9 x 8 x 9.
  - `flowerPlantersStartUnlockedTheRestComeFromMarketDay`: starts locked, hint `...table.hint.market`.
  - `buildsMarketCorner` (batch `decorations_2614b`, `big_area`, origin (9, 2, 9), no rotation, 9,000 ticks): a builder
    with exactly the plan's materials in four barrels (at helper (1..5, 2, 4), west of and outside the footprint) builds
    it from bare ground and finishes (`assertBuilt`).
- `QaDecorations1104fGameTests.marketCornerFromExactlyItsList` (batch `qaDeco1104fB`, `big_area`, origin (9, 2, 9),
  12,000 ticks; bench at (2, 2, 2), supply chests at (2, 2, 4), (4, 2, 4), (1, 2, 5), (4, 2, 1)): the Material List the
  player reads on the held blueprint is non-empty, every line an item a survival player can get (no air, bedrock,
  barrier, light, structure void/block, command block, spawner, petrified oak slab, budding amethyst, reinforced
  deepslate, end portal frame, **farmland, dirt path**, jigsaw, debug stick, knowledge book, chorus plant, frogspawn,
  trial spawner, vault, infested stone of any kind); built from exactly that list it **finishes with every block as
  drawn (`plan.unfinished` empty) and 0 skipped steps**.
- `QaDecorations1108GameTests.theTableListsEveryNewDecorationWithANameMaterialsAndAWayToGetIt`: listed on a new
  village's Table by id with a size and blocks, locked with a hint that reads as words; its name has no `.` or `_`,
  starts with a capital, is American English and differs from the other thirteen; the Table shows materials, none
  unobtainable; the template holds no creative-only block (bedrock, barrier, light, structure/command blocks, spawner,
  trial spawner, vault, jigsaw, portals and portal frames, reinforced deepslate, budding amethyst, petrified oak slab,
  infested blocks, moving piston) and no POI block; the advancement `aliveworkplace:built/market_corner` exists.
- Library-wide checks it must pass like every shipped blueprint: `BlueprintsStayGameTests` (lamps on a block that
  holds them; no dirt path or farmland under a solid block; beds whole), `B648GameTests.b648NoShippedGlassEndsInTheAir`
  and `QaWindowsAndWards1028BugTests` (no pane or bars joined on one side with air on the other, as stored and as the
  game joins it), `QaBuildReachGameTests` (any chest, bed or job block reachable on foot: none expected here),
  `QaChairFacingGameTests`, `QaMaterialListGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`
  (every countable vanilla material it uses reads in the plural), `StyleGameTests.chimneysStayWholeInEveryStyle`.
- By id only (the drawing doesn't matter, the id and the name do): the name stays **Market Corner**
  (`blueprint.aliveworkplace.market_corner`), the id stays `market_corner` (the advancement, `VillageMaps`,
  `Decorations`, `tools/review/gallery.py` and the `decor` showcase scene name it).
- The owner's rules (`check.py`: posts at fence and wall corners; lamps on a table, side table, slab or post, or hung,
  never a lantern standing on a floor; plates and crockery only by a stove or on a table with a chair at it;
  `open_panes`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, and `holes.py` passes. The old
  drawing is not in `owner_rules_baseline.json`; the new one must come out clean too.

Saves (for the ship step, not the designer): the id never changes; a site begun on the old drawing must still finish,
so the old drawing goes to `blueprint_history/market_corner/` (`history.py`).

## Free
Everything not listed above: what goods are shown and how (crates, sacks, produce, a stall, an awning or none),
seating, lighting, planting, paving, materials, colors, shapes, height and footprint within 9 x 8 x 9, and style.
