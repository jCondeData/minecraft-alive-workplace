# Supply Shop: the brief

Family id(s): `supply_shop`, `supply_shop_2`, `supply_shop_3`, `supply_shop_4` (never renamed; "Supply Shop", "Supply Shop
II", "III", "IV" in `en_us.json`, `blueprint.aliveworkplace.supply_shop*`; hidden advancements `built/supply_shop*.json`)
Variants: village (none: no `village/<style>_supply_shop`, the family is not in `village.py` or `village_looks.py`),
modded (none to draw: `modded_library.py` makes `structure/modded/supply_shop*.nbt`, all four levels, from the vanilla
drawings and the ship step reruns it; `QaCarvedLibraryCompatTests.theHomesAndWorkshopsHaveCopies` needs a complete modded
copy), looks (none: no biome look; the Table's style buttons, `BlueprintStyles`, restyle the drawing automatically).

## What it is for
The player-run shop. Its job block is the mod's **Shop Counter** (`aliveworkplace:shop_counter`, POI `shop_counter`),
the **Shopkeeper**'s workstation (`ModVillagers.SHOPKEEPER`); the Innkeeper and, with Cobblemon, the Pokemon Trader may
also take it (`work/Stations`: handing a villager an emerald at the counter makes a Shopkeeper; a jobless villager who
finds it by himself becomes a Shopkeeper or an Innkeeper, `docs/wiki/random-jobs.md`). The first player to open the
counter owns the shop and sets its price list; the shop **sells what the containers within 8 blocks across and 4 up or
down of the counter hold and puts the payment back into them** (`shop/Shops.stock` -> `SupplyContainers.find(level,
counter, null)`, `RADIUS` 8, `VERTICAL` 4; chests and barrels both count, and a container nearer another owner's counter
is that shop's). The Shopkeeper walks to the counter and minds it, greeting players (`ShopkeeperPackages`). Players buy
Level I from a **Master (level 5) Builder** for 20 emeralds (`ModTrades`; `BlueprintUnlocks`: locked in a new village,
"Sold by a Master Builder"). The **Steward** builds it by himself in a **`market`** zone when a Shopkeeper is wanted and
has no workstation (`steward_rules/workplace_supply_shop.json`, priority 62, cooldown 2 days, Hamlet and up) and renews
an old Shopkeeper's workplace with it (`steward_renewal/shopkeeper.json`). The Village Map shows it as TRADE
(`VillageMaps.kindOf`, by name). Each level is an upgrade on the same spot, origin and front.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `supply_shop` | The working shop: the Shop Counter, **exactly 8 storage containers** in its reach, the shop's furnishings, a front door a villager walks in by | Master Builder trade (20 emeralds); Steward rule `workplace_supply_shop` (market zone, Hamlet+) and renewal `shopkeeper` |
| II | `supply_shop_2` | Nothing the code reads (the old one added the shopkeeper's room with a bed, a chest and more stock behind; no code counts them) | Upgrade of I (`BlueprintUpgrades`: id + `_2`); a builder who finished I sells it (`UpgradeOffers`) |
| III | `supply_shop_3` | Nothing the code reads (the old one added an annex with a Mailbox and a chest; no code or test needs a mailbox here) | Upgrade of II |
| IV | `supply_shop_4` | A **basement exactly 4 deep** under III, reached from inside | Upgrade of III (`BasementGameTests.basementsLoadUnderTheGround`: a builder who finished III sells IV); one of the eight `StarterBlueprints.LEVEL_4` |

No fifth level and no other id: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts exactly 64 upgrades library-wide.

## Must have (each level)
- **Job block: one `aliveworkplace:shop_counter`** in every level, reached on foot from the front door. One counter is
  what the shop needs (a second counter of the same owner shares the chests; of another owner it takes the nearer ones).
  Its `facing` is free (it turns with the build, `QaTurnedModBlocksGameTests.everyFacingBlockTurnsWithItsBuild`; the
  builder takes any facing as standing, `MaterialRules.facingFree`).
- **Storage (the stock):**
  - **Level I has exactly 8 item-holding blocks besides the Shop Counter, and every one is within 8 across and 4 up or
    down of the counter** (`FurnishedWorkplacesCompatTests.theSupplyShopWithHandcraftedAndBeautify` -> `build(..., 8)`:
    "has N containers, not 8", then the counter's `SupplyContainers.find` must list every one; showcase
    `FurnishedWorkplacesScene.storage` expects 8 for the shop). Count every block whose block entity is a `Container`:
    chests, barrels, and also decorated pots, chiseled bookshelves, furnaces, smokers, hoppers, jukeboxes, brewing stands,
    crafters, dispensers. So Level I's 8 are its chests and barrels and **nothing else in Level I may hold items**.
    Chests and barrels both work as stock; a barrel is also the vanilla Fisherman's job block (no test forbids it).
  - Later levels: any storage you add that should be stock must be within the counter's reach (8 across, 4 up or down);
    storage beyond it is not the shop's.
  - Every indoor chest stands against a wall, a solid block, a bed or another chest
    (`QaFurnishedRooms1031GameTests.everyChestStandsAgainstAWallOrABed`); double chests stay whole turned and mirrored
    (`QaMirroredBuildsGameTests`). Containers are drawn empty (builders never fill or copy them).
- **Furniture spots (every level, I to IV): at least one `counter`, `shelf`, `crockery`, `picture`, `hanging_pot` and
  `trellis`** spot (`FurnishedWorkplacesGameTests.everyWorkplaceIsFurnished`; written through `rebuild.py` into
  `blueprint_furniture/supply_shop*.json`). With Handcrafted and Beautify the counter, shelf and crockery become
  Handcrafted's and the picture, trellis and hanging pot Beautify's wherever the piece can stand
  (`FurnishedWorkplacesCompatTests.theSupplyShopWithHandcraftedAndBeautify`, Level I: built with nothing skipped, every
  spot the mod the order picks, no piece counted as storage). No spot covers a job block, a container or a bed, and the
  shop has **no `bed` spot** (`FurnishedWorkplacesGameTests.noSpotCoversAJobBlockABedOrAContainer`; `furniture.py`
  `WORKPLACES`). Crockery only by a stove or counter (within 4 across, 3 up or down) or on a table with a chair
  (`check.py` owner rule 3). Every spot's blocks equal the template's (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`);
  nothing floats (`FloatingFurnishingsGameTests`); chair stairs face their table (`QaChairFacingGameTests`); every furnished
  room has its own light (`FurnishedRoomsGameTests.everyFurnishedRoomHasALight`) and no lantern stands on a room's floor
  (`QaFurnishedRooms1031GameTests.noLanternStandsOnARoomsFloor`); no furniture in a door's way
  (`FurnishedRoomsGameTests.noFurnitureStandsInADoorsWay`).
- **Level I's front door** (`QaDoorwayClearanceGameTests`, pinned below): a door on the ground floor (template y 1) in the
  z = 0 face, **a 2-tall doorway with a solid block directly over the door's top half** (the test puts a carpet in the cell
  just inside and must see the villager's head room drop under 1.95: a taller opening or no lintel fails it), no carpet
  or slab in the cells right before, in or right behind the door, and an inside floor cell a villager from the street
  walks to through the shut door, turned a quarter too.
- **Reach:** the Shop Counter, every chest and barrel and every bed head are within 2 across and 1 up or down of a cell
  walked to from the ground outside (levels, one-block steps up, drops of up to 3, ladders, doors, trapdoors)
  (`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo`). Every wooden doorway leaves a 1.95-tall
  villager room over its step (`QaDoorwayClearanceGameTests`, `check.py`).
- **Beds and residents: none required.** No code counts the shop's beds. Any bed drawn is a plain vanilla bed with no bed
  spot, its head reachable, whole when turned and mirrored, not right inside a doorway (`check.py`), and at home in the
  build in every turn (`HomesSpecGameTests.everyBedOfEveryStarterHouseIsAtHomeTurnedAnyWay`).
- **IV's basement** (`BasementGameTests.basementsLoadUnderTheGround`, `QaBasementGameTests`,
  `QaBlueprintTableGameTests.everyBasementBuildPreviewsItsWholeDepth`): drawn at the bottom of the file, floor at file
  y 0, ground floor at file y 4 (file height = declared height + 4; `StarterBlueprints.SUPPLY_SHOP_4` basement 4); a
  ladder or stairs at y -1 (on the basement's top layer) with 2 passable cells above it
  (`everyBasementsWayDownComesUpThroughTheGroundFloor`); at least one `minecraft:lantern` below ground; no dark standable
  cell (`noBasementLeavesADarkSpotForMonsters`); walkable from outside down and back up, every chest down there within 2
  across / 1 up-down of a walked cell (`everyBasementAndItsChestsCanBeWalkedToFromOutside`). No job block down there
  but the shop's own (`basements.py` header): stock chests yes, barrels count as a Fisherman's block.
- No entity, jigsaw or structure void in any level; every block one a builder can place
  (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Kept cells (every upgrade)
- Each level has the same origin and front as the one below and is at least its size in x, y and z; **at least 60 % of the
  level below's non-air blocks stay the same block state in the same cell** (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`).
  Levels grow out of each other (REBUILD.md).
- **The Shop Counter, every chest and barrel and every other block-entity block stay in the same cell as the same block**
  in every later level (builders never break them, `MaterialRules.isProtected`); IV is III with its basement dug under it,
  nothing above ground moved.

## Fits
- **Plot per level (the current declared sizes, pinned in `StarterBlueprints`; design within them):**
  - I `supply_shop`: **11 wide x 11 tall x 10 deep**
  - II `supply_shop_2`: **11 x 11 x 16**
  - III `supply_shop_3`: **17 x 11 x 16**
  - IV `supply_shop_4`: **17 x 11 x 16 above the ground, plus the 4-deep basement**
  `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable` and `BasementGameTests` compare each file with these `Vec3i`s
  (and the trade's blueprint item carries the size). If a level truly needs more, say so in the design notes: the ship
  step then changes the `Vec3i`, and these test ceilings are hard: **Level I at most 13 wide x 16 tall x 13 deep**
  (`BuilderGameTests.buildsSupplyShop` at (9, 2, 9) in the 22 x 18 x 22 `big_area`, the builder's barrels at x 1-5, z 4,
  bench (2, 2, 2)); **II-IV at most 23 wide x 18 tall x 28 deep above the ground** (`QaReleaseRefresh1028GameTests`
  places III and IV at (7, 2, 2) in the 30 x 20 x 30 `huge_area`, hall at (1, 2, 1), bench at (3, 2, 27)). A smaller
  footprint fits more market plots and renewals.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Material budget: Level I at most 108 stacks** from bare ground (`buildsSupplyShop` stocks four barrels of 27 with
  exactly the plan's materials, "test needs more barrels").
- **Build time: Level I to DONE inside 9,000 ticks** (`buildsSupplyShop`: day time 2,000, build delay 2, builders' help
  off; the builder stops at dusk, about day time 9,000-11,000). The old Level I (472 placed blocks) passes; builds over
  about 1,100 blocks have timed out elsewhere. Keep Level I lean. The furnished builds
  (`FurnishedWorkplacesGameTests.aBuilderBuildsTheFurnishedSupplyShopInVanilla`, compat `theSupplyShopWithHandcraftedAndBeautify`:
  Level I at (9, 2, 3) in `huge_area`, 30,000 ticks, build delay 1) must finish with **nothing skipped**.
- **Front and way in:** the z = 0 side faces the market street (the player who places it, the road of a Steward plot);
  the front door is there on the ground floor and every upgrade keeps it.
- **Neighbours:** a workplace in a Steward `market` zone; the Steward keeps 2 blocks clear round its plot (`Plots.CLEAR`)
  and wants the ground under it within 4 blocks of level (`Plots.MAX_STEP`). No party walls.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `QaDoorwayClearanceGameTests.aCarpetMovedUnderTheVillageHallsAndSupplyShopsLintelIsCaught`: Level I's front door at
  template **(5, 1, 2)** is a door; its doorway passes as drawn and fails with a carpet in the cell one step along the
  door's facing.
- `QaDoorwayClearanceGameTests.aVillagerWalksInByTheSupplyShopsShutFrontDoor` and `...TurnedSupplyShopsShutFrontDoor`
  (Level I placed as a structure at (8, 2, 8), and turned clockwise at (20, 2, 8), in `huge_area`, day 6,000, 500 ticks):
  a villager spawned at template **(5, 0, -1)** walks in through the shut front door to the floor cell **(5, 1, 5)**.
- `FurnishedWorkplacesGameTests.aBuilderBuildsTheFurnishedSupplyShopInVanilla`: the Shop Counter at template **(5, 1, 6)**
  after the build; every block right, nothing skipped, every spot's blocks standing.
- `BuilderGameTests.buildsSupplyShop` (above: size, budget, time; every block right after the build).
- Showcase (not a test, but pinned): `FurnishedWorkplacesScene` still `01_shop_counter`, camera eye at (5.5, 2.9, 3.2)
  inside the shop looking at (5.5, 2.2, 7) toward the counter, and its caption in `tools/showcase/scenes.py` ("Handcrafted
  counters either side of the Shop Counter, a shelf of cups, a picture, hanging pots"); `storage` expects the shop's 8.
  The ship step moves the eye into the new shop room and rewrites the caption.
- `QaReleaseRefresh1028GameTests.qaReleaseRefresh_supply_shop_3` / `_4`: III and IV built as 0.151.0 drew them
  (`blueprint_history/supply_shop_3|4/cd1557d8c.nbt`) refresh to the new drawing and end equal to a fresh build (chests
  and job blocks left over are excused; any other block-entity block the old drawing had where the new wants something
  else must still come out right). The new drawings must differ from those (they will).

By id only (the drawing doesn't matter beyond the rules above): `WorkplacesGameTests` (`workplace_supply_shop`, Shopkeeper,
market, Hamlet); `QaBlueprintLibraryVerify1108bGameTests` (locked in a new village, listed, "Sold by a Master Builder");
`QaBuiltAdvancements1116bGameTests.repairsAndRefreshesAwardNothing` (`built/supply_shop`); `BasementGameTests` (IV upgrades
III, sold by its builder); `QaCarvedLibraryCompatTests.theHomesAndWorkshopsHaveCopies` and `ModdedLibraryCompatTests`
(the modded copies: same size, doors, beds, job blocks and spots, nothing added that holds items);
`TableGameTests`, `QaTableJobsGameTests`, `TablePreviewGameTests` (the Table's jobs and beds lines come from the drawing).

Library-wide checks it must pass: `B648GameTests` and `QaWindowsAndWards1028BugTests` / `QaBuilders1028GameTests` (no pane
or bar run ends against open air), `QaFurnishedRooms1031GameTests`, `FurnishedRoomsGameTests`, `BlueprintsStayGameTests`
(lamps on a block that holds them; no dirt path or farmland under a solid block; beds whole), `QaDoorwayClearanceGameTests`,
`QaChairFacingGameTests`, `QaBuildReachGameTests`, `QaMirroredBuildsGameTests`, `QaTurnedFramesGameTests`,
`QaLedgerFramesHaulGameTests`, `QaTurnedModBlocksGameTests`, `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`,
`HomesSpecGameTests`, `QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`, `StyleGameTests.chimneysStayWholeInEveryStyle` (only
if a campfire stands on a solid stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a stove
or counter or on a table with a chair, no bed right inside a door, `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line for any level. Today `owner_rules_baseline.json` excuses one finding on
`modded/supply_shop_4`; the new drawings must come out clean and the ship step removes that entry. The ship step also keeps
the old drawings in `blueprint_history/` (`history.py`) so a site begun on them finishes as drawn, and regenerates the
modded copies and the furniture spots.

## Free
Everything not listed above: shape, plan, roof, materials, colors, style, storeys and rooms; where the Shop Counter, the
stock, the door and the furniture stand (within the rules and pins above); chests or barrels for Level I's eight; what II
and III add (a home for the shopkeeper, a stockroom, a mail annex or a Mailbox, beds, more stock, or none of these); and
what IV's basement holds beyond its way down and its light.
