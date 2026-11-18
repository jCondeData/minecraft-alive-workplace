# Market Square: the brief

Family id(s): `market_square` (never renamed; one level)   Variants: village styles only (`blueprint_styles/*.json`
swap blocks at load time into styled ids such as `market_square` in a style; nothing to draw for them). Looks: none.
Modded: none (`modded_library.py` lists it in `EXTRA_DECORATIONS`, so no modded copy is dressed). One drawing,
`structure/market_square.nbt`.

## What it is for
The village's market place, a civic decoration with no worker and no residents. Once a week, on the village's market
morning, wandering traders appear on open ground round the **middle of the square** and wander about it
(`hall/MarketDays.java`, wiki `market-days.md`); a bell sound plays at the middle and players are told "traders have
come to the Market Square". Without a finished square there is no market. The square's middle is also where the
village gathers for a Grand Chef's **banquet** (`Banquets.square`: off-work villagers walk to random spots within 4
blocks of the middle), where a Merchant Prince's **trade fair** brings six traders and hangs bunting on a ring 6 blocks
out (`TradeFairs.hold`, `putUpBunting`), and the second of the two places a Legend with "keeps to" spends the day
(`KeepsToPower.place`). A Legend may arrive with the market's traders (`LegendGuests`, place `market`). It is a
**Burgher class need** (`classes/burgher.json`, `{"type": "building", "blueprint": "aliveworkplace:market_square"}`),
the village-wide **market service** (`services/market.json`, `range: -1`), worth **5 beauty** (`Decorations`), and a
Trade building on the village map (`VillageMaps`: `Kind.TRADE`).

How it is got: the Steward wishes for one when the village reaches **Town** rank and has none (`steward_rules/
market_square.json`: priority 36, zone `market`, max 1, cooldown 2 days); it is a **rare** blueprint at the Table
(`blueprint_unlocks/rare.json`: hidden as "???", never listed by id), found in six loot tables (bandit camp, coven hut,
pharaoh's tomb, piglin hoard, pirate ship, war camp), unlocked by the hamlet questbook chapter's reward, and sold by
market traders like any decoration. Its built advancement is `built/market_square`.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `market_square` | The village's market place: market days, banquets, trade fairs, the Burgher need, the market service, 5 beauty | Steward rule at Town rank; rare unlock (loot, questbook, a trader). No upgrade exists or may be added |

## Must have
- **Open, walkable ground round the middle: the traders' 9 x 9.** The code knows the square only as its declared
  outline: its middle is the footprint's centre (`BoundingBox.getCenter`, so (6, ., 6) at 13 x 13) at **y = 1**, one
  above the bottom layer (`MarketDays.square`). Traders spawn on standing cells within **4 blocks of the middle
  horizontally and from y = -1 to y = 4** (`MarketDays.spot`: `Walker.canStand`: a sturdy top below, two passable cells
  of room, no deep water, nothing that hurts), sorted nearest first, trader *n* taking the (5n)th cell. **No standing
  cell there means no traders** (`hold` returns none, and the market day is spent). A village can draw 2 + 1 (Town)
  + Commerce levels + Open Gates + Burghers traders, plus 6 at a fair, so leave **plenty** of standing ground in that
  9 x 9, enough that the traders don't pile onto one cell (several dozen cells; the old one had about 70). Banquet
  guests walk to random cells of that same 9 x 9 at **y = 1**, so most of it should be ground at y = 1 that a villager
  can walk onto from the square's edge.
- **The middle is a meeting point, not a wall.** Sounds, particles (banquet smoke at the middle, y = 1 + 1.2) and the
  traders' wander target are the middle cell; a solid block there is allowed, but the area round it must be open.
- **Bunting ring (trade fairs):** banners go up on a ring **6 blocks out from the middle** (at 13 x 13, the outer edge
  cells), one every 3 blocks, each on the highest air cell from y = -1 to y = 4 that has solid ground under it; it never
  replaces a block. Not tested on the drawing, but an edge that is all air with nothing to stand a banner on, or
  roofed solid, loses the fair's decoration there.
- Job blocks: **none needed**. Nothing reads a job block here, and a vanilla job-site block (a barrel is the
  fisherman's, a composter, lectern, smoker...) would be claimed by village workers as their workplace. Beds: none.
  Residents: none. Storage: none needed (no code reads a container in it).
- A bell is **optional**: no code needs one here (the market's bell is a sound). A bell block is a vanilla meeting
  point, and the mod picks the nearest one to the hall for festivals (`Festivals`), the fire warden's alarm
  (`VillageFire`) and Wonder dedications, so one here may become the village's.
- Furniture kinds a job or test needs: none (there is no `blueprint_furniture/market_square.json`).

## Every block must be one a survival builder can place and keep in place
- `BuilderGameTests.buildsMarketSquare` (batch `decorations`, `big_area` 22 x 18 x 22, origin helper (9, 2, 9), no
  rotation, **20,000 ticks**): a builder with **exactly** the plan's materials in four barrels builds it from bare
  ground and finishes it as drawn (`assertBuilt`).
- Materials budget: the whole material list, as stacks, must fit **108 stacks** (`buildStarter` asserts
  `stock.size() <= 4 * 27`). Water buckets don't stack: every water source costs a stack of its own.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: no block classified `SKIP` (no flowing
  water, no technical blocks).

## Fits
- Plot: **at most 13 (x) x 13 (z)** and **at most 16 tall** (the build test's origin (9, 2, 9) in `big_area`).
  y = 0 sits on the clicked block; the front is the z = 0 side.
- **Not too small:** `ZoneVillageBuildingsGameTests.buildingsInAZonePastTheRadiusCountForTheVillage` records a square
  turned 180 degrees with its middle at hall + (11, 13) and asserts its corner (the origin) lies **outside** a zone painted
  over hall + 9..15; that holds only if the footprint is **at least 7 deep (z)** or **at least 11 wide (x)**.
- Declared size: `StarterBlueprints.MARKET_SQUARE` is `new Vec3i(13, 5, 13)` today, and **`MarketDays.square` uses that
  declared size (not the `.nbt`) to find the middle**. If the new size differs, the ship step changes that `Vec3i` to
  match (`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` asserts they are equal), and `generate.py`'s
  `("market_square", market_square)` entry (line 189) stops overwriting the new `.nbt` (add it to `REBUILT`).
- The Steward places it in a **Market** zone (`city/Plots.java`: the footprint plus a 2-block margin in the zone's cells,
  off the roads), so every block of width costs plots; the plot is turned so the z = 0 side faces the road, else the
  hall (`PlotGameTests.plotOnFlatGroundFacesTheRoad`).
- Front and way in: no door is needed; the lane to it starts one block in front of the middle of the z = 0 edge
  (`Paths.doorstep`'s fallback, a door drawn in would move it), so that spot should be ground a villager can step onto,
  and villagers and traders must be able to walk from the edges into the middle 9 x 9.
- Neighbours: other Market-zone builds (storehouses, market stalls). No test places blocks round it by coordinates.

## Rules tests check
- `BuilderGameTests.buildsMarketSquare`: built from exactly its list within 20,000 ticks, nothing unfinished.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: `.nbt` size = declared size; every block
  placeable.
- `ZoneVillageBuildingsGameTests.buildingsInAZonePastTheRadiusCountForTheVillage`: the footprint size rule above (via
  `Builders.spot`, the library's size); its middle in the zone makes it the village's market, its beauty and rank count.
- By id and declared size only (the drawing doesn't matter, the id and the size do; traders spawn on the test area's
  floor round the declared middle):
  - `QaMarketDays1117cGameTests` (all nine: `noSquareNoMarketAndASquareFinishedThatMorningBringsIt`,
    `aStyledMarketSquareHoldsTheMarket` (a styled id counts, `BlueprintStyles.base`),
    `theTradersCountGrowsWithRankAndCommerce` (every trader the village's count finds a spot), ...),
    `QaMarketDays1117cBugTests` (Gazette, nightfall, foretold, dusk), `VillageHallGameTests.marketDayBringsTraders`
    (2 traders, each with a blueprint) and `decorationsMakeAVillagePrettier` (5 beauty each),
    `SeerGameTests.theFestivalMarketAndGuestToldAreRight`;
  - `ClassGameTests.theFourClassFilesAreTheLadder`, `buildingNeedLooksForAFinishedBuildInTheVillage`,
    `sixtyHouseholdsTakeUnderTwoMsARound`, `ClassMigrationGameTests`, `QaClassPerksGameTests.theMarketReachesTheWholeVillageButOnlyFromInsideIt`;
  - `ServiceGameTests.eachServiceIsFoundByItsBuild` and `aServiceSixtyBlocksAwayDoesNotReach`;
  - `StewardCivicRulesGameTests.aTownWithoutAMarketSquareGetsOne` (none at Village rank; wished at Town in zone
    `market`; not again once built);
  - `QaBlueprintLibraryVerify1108bGameTests.aNewVillageStartsWithTheWikisSetAndTheRestLockedOrRare` (rare: hidden,
    never listed by id); `ArenaGameTests.theVillagesArenaIsItsHighestTierWithinReach`,
    `ServerControlsGameTests.everyNeedsSystemSwitchedOffStaysOff` (records one).
  - The name stays **Market Square** (lang `blueprint.aliveworkplace.market_square`; the market-day chat line quotes
    it, `QaMarketDays1117cGameTests` checks "Market Square" in it).
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, `holes.py` passes.

## Free
Everything else: what stands in the square (a fountain, a cross, stalls, trees, seating, lamps, a bell or none),
paving, levels and steps, materials and style, as long as it fits 13 x 13 x 16 (at least 7 deep or 11 wide), is
buildable from a 108-stack list in 20,000 ticks, has no job-site block, and leaves plenty of open, walkable ground at
y = 1 within 4 blocks of its middle, reachable from the z = 0 front.
