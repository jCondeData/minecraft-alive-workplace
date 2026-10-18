# Furniture and storage mods

Builds furnished with the pack's furniture mods (Handcrafted, Lucky's Cozy Home, CobbleFurnies, Beautify) when the
server has them, and Sophisticated Storage and Tom's Simple Storage as village storage. Part of Furnished homes and
modded storage (2.1), behind its expansion gate (`Expansions.M37`), which stays closed until the release that finishes
it. The plan is the design note `docs/design/M37.md`.

Roadmap items: 37.1, 37.2, 37.3, 37.4, 37.5, 37.6, 37.7, 37.8, 37.9

## 1. What a player sees

Nothing yet for players: the expansion's gate stays closed until 2.1. Behind it, a build started on a server with
Handcrafted gets Handcrafted chairs, tables and fancy beds where the vanilla build had stair chairs, fence tables and
beds (37.3); the Blueprint Table's preview, its supply report and the material list book show those pieces and cost
their items, and so does the builder's shopping list.

The homes are furnished room by room (37.4): the Starter Cottage, Stone House, Townhouse and Manor at every level and
in every style, the Artisan's House and the Inn. In plain vanilla the rooms read as rooms already: a dining corner (a
fence-and-plate table with two stair chairs), a bedroom (a slab nightstand at the bed's head, a standing lamp of a
lantern on a fence post), a sitting corner (a couch of stair seats against the wall, some with trapdoor arms), a shelf
of cups (an upside-down slab with a flower pot on it), a grandfather clock (a stripped log case under a trapdoor
hood), pictures (plain wall banners) and blinds rolled up over the windows (closed trapdoors at the window head). The
Inn has shelves of cups behind the bar, a clock by the door, a nightstand, lamp and picture in each guest room, and a
couch and lamp on the landing. With Handcrafted those become Handcrafted chairs, tables, fancy beds, nightstands,
couches, shelves and crockery; Beautify adds its candelabras, picture frames and blinds; with CobbleFurnies first its
chairs, tables, nightstands, sofas, lamps and curtains. Cozy Home's clocks and sofas come in on the real pack. A home
planned with a Pokémon partner (a Pokémon kept in a Pasture Block within 16 blocks of the plot) gets a Trainer's room:
with CobbleFurnies installed, its first chair becomes the Poké Ball chair and the table beside it the Poké Ball desk,
whatever the order (the owner's answer); the rest of the house follows the order.

The workplaces are furnished too (37.5), at every level: the Supply Shop's counter boards either side of the Shop
Counter are counters, with a shelf of cups and a picture on the back wall, pots hanging from chains in the display
windows and a trellis either side of the door (open trapdoors flat on the wall); the Library's reading tables are
desks with their chairs, pictures by the door and hanging pots over the desks; the Schoolhouse's rows and the Village
Hall's are benches, the Healing Center has waiting benches and a nurse's desk, the Schoolhouse a teacher's desk by the
lectern, the Print Shop a printer's desk by the press and its buyers' counter, Village Hall II a council table with
eight chairs, and the Kitchen crockery on the worktop (in place of its potted fern). With Handcrafted and Beautify those become
Handcrafted counters, desks, chairs, benches, shelves and crockery and Beautify picture frames, rose trellises and
hanging pots of vines. The Healing Center's and Schoolhouse's desks are CobbleFurnies' Poké Ball desks whenever
CobbleFurnies is in the list (the owner's answer). No piece ever stands on a job block, a bed or a chest: the
workplaces' beds (the Healing Center's ward, the shopkeeper's, the cook's) stay vanilla beds, and every job still finds
its block and exactly its own chests and barrels, because the furniture mods' pieces that hold items (Handcrafted's
counters, desks, shelves, drawers, cupboards, nightstands and side tables, Cozy Home's storage counters, CobbleFurnies'
cabinets) are never read as chests while furnished builds are on.

Villagers use the furniture (37.6). From supper (time 10500) until bed (12000), a villager who is idle or at the
evening meeting walks to the nearest free chair, stool, bench, couch or sofa of the furniture mods within 8 blocks and
sits on it, as spectators sit in the Stadium's stands. They stand up for work, for bed, for danger (hurt, a hostile
mob near, panicking or a raid), when the seat is broken, or with `furnishedBuilds` off. One seat holds one villager:
a seat with anyone on it, or that another villager is already walking to, is passed over. Handcrafted's fancy beds are
real beds: villagers claim and sleep in them, and the house they stand in counts as their home (its grade, the Village
Hall's list, free beds for a new baby). A home with a furniture mod's seat in it is a furnished home, worth 1 beauty
to the people who sleep there (the "beauty near home" a Burgher or a Noble needs, M34). Vanilla stair chairs are not
seats.

When it is finished, a server with Handcrafted gets Handcrafted chairs round a Handcrafted table, fancy beds with nightstands and
shelves of crockery in its homes; Cozy Home adds sofas, clocks and lamps, CobbleFurnies Poké Ball chairs, Beautify
blinds, picture frames and candelabras; villagers sit on the chairs and claim and sleep in the fancy beds (a bed spot only takes a mod's bed when villagers can
sleep in it, as Handcrafted's; any other stays a vanilla bed). Without any of those
mods every build is exactly today's vanilla build. The Storehouse also reads Sophisticated chests and barrels of every
tier, and a Sophisticated controller or a Tom's inventory connector as one store for everything it links.

Sophisticated Storage's chests, barrels and limited barrels already work as supply chests, porters' chests and
Storehouse chests with the gate closed (they always did, through the same item storage every chest uses). Behind the
gate (37.8): where a slot holds more than a stack, a stack upgrade or a limited barrel, the Storehouse board's info adds
"Room for 6912 more items (some slots hold more than a stack)" under the usual "chests, items, empty slots" line, and
porters keep filling such a store while any of its slots has room for another stack, even when no slot is empty. With
only vanilla chests the board and the porters are exactly as before.

A Sophisticated Storage controller (37.9, behind the gate and `moddedStorageNetworks`): put one in the Storehouse's area
touching your Sophisticated barrels and chests, and the Storehouse treats everything it links as one store. The board
counts their goods once and adds "Linked through a storage controller: 3 blocks"; porters put deliveries in through the
controller, which files them into the linked blocks, and builders take what they need through it. Break the controller
and the blocks are separate stores again, with everything still in them. A "Sophisticated" style for the Storehouse
(I, II and III; offered only with the mod and the switch on) builds Sophisticated chests and barrels in place of the
vanilla ones and a controller on top of the Storehouse block, touching the chests on both sides.

Carved Wood and Rechiseled (37.7, behind the gate and `furnishedBuilds`): the Blueprint Table offers a **Spruce** style
for every build. It builds the timber in spruce (oak and dark oak logs, planks, stairs, slabs, fences, doors and
trapdoors; birch infill becomes stripped spruce wood), and with Carved Wood installed its chests, trapped chests,
bookshelves, chiseled bookshelves, lecterns, crafting tables, ladders, lanterns and beehives are Carved Wood's spruce
ones, so a spruce Library has spruce shelves and a spruce lectern its Librarian works at. Carved Wood has no spruce
barrel (the vanilla barrel is spruce already), so barrels stay. With Rechiseled installed the **Grand** style's floors
(spruce and oak planks) are `rechiseled:dark_oak_planks_flooring`, its wall panels (birch infill, white terracotta)
`rechiseled:andesite_squares` and its polished andesite `rechiseled:andesite_tiles`. Without the mods, or with the
switch off, the Spruce style is plain spruce (and isn't offered with the switch off) and Grand is exactly today's Grand.
Carved Wood's chests and barrels of every wood are supply chests, porters' chests and Storehouse chests (they are
vanilla chests and barrels underneath).

## 2. How it works

Each piece of furniture our builds draw in vanilla blocks becomes a furniture spot (its kind, facing, wood or colour
and blocks). When a build is planned, each spot becomes the piece of the first mod in `furnitureMods` that the server
has, found by block id in the registry; no class of any of those mods is used, so they stay optional. A spot whose
modded piece doesn't fit, or whose kind no installed mod makes, keeps its vanilla blocks. The full formats, the list
of kinds and each mod's block ids are in the design note.

**Furniture spots (37.2).** Each of our blueprints with furniture has a file beside it,
`data/aliveworkplace/blueprint_furniture/<blueprint>.json` (223 of them), listing its spots: kind, position, facing,
wood or colour, and the vanilla blocks of the piece. The blueprint generator (`tools/blueprints/furniture.py`) finds
the chairs (a stair seat facing a table), tables (a lone post, or a row of posts, with a cloth or plate on it, or a top
slab on a lone post) and beds in every finished drawing, and a drawing records other kinds itself
(`Build.furniture`); before writing, it checks each spot's blocks are the template's blocks there. A spot with no wood
of its own takes the wood most of the build is made of. The game reads the files at start-up and on `/reload`
(a datapack can add its own); a file with a mistake is logged with its path and skipped. A styled blueprint's spots are
its base's, with the style's swaps: a spruce chair in the Dark Oak style is a dark oak chair.

**Furnished rooms (37.4).** The drawings (`tools/blueprints/starter.py`, `houses.py`) draw each piece with a helper
in `tools/blueprints/kit.py` (`nightstand`, `floor_lamp`, `couch`, `shelf`, `picture`, `blinds`, `clock`) that places
the vanilla blocks and records the spot, refusing a cell already drawn, so no piece lands on a bed, a job block or a
chest. A piece an upgrade builds over later (a cell no longer holds what the piece was drawn with) loses its spot in
that upgrade's file. Every piece stays clear of doorways, ladders and the paths to them, including the doorways the
upgrades cut (the Manor's ballroom door, the Cottage's kitchen wing). Facing: a piece against a wall faces into the
room; a couch's sitters face away from the wall it stands on.

**Furnishing tables (37.3).** `data/aliveworkplace/furnishings/<mod>.json` (`handcrafted`, `cozyhome`,
`cobblefurnies`, `beautify`; a datapack can add others, named by file) give each kind's block ids, with `{wood}` and
`{colour}` filled from the spot, how the piece's `facing` relates to the spot's, other states, and the footprint. When a
build is first planned, each spot becomes the piece of the first mod in `furnitureMods` whose block is in the block
registry and whose footprint fits inside the spot's cells (its other cells become air); otherwise it stays vanilla.
The choice is saved on the build site and, once the build is finished, with the finished building, so a restart, a
changed order or a newly added mod never changes a build going up or standing, and upkeep repairs it as it was built.
A saved piece whose block no longer exists (the mod was removed) is dropped when it is read, so that spot is built, and
put back by upkeep, as its vanilla piece. A repair or a taking-down chooses nothing new: it uses what the building was built with.

**Sophisticated Storage (37.8).** Every block that stores items through Fabric's item transfer API is storage to our
jobs (`ItemStores`, `SupplyContainers`), so all 36 of Sophisticated Storage's chests, barrels and limited barrels
(`chest`, `barrel`, the `copper_` to `netherite_` tiers of each, and `limited_[tier_]barrel_1` to `_4`) are supply chests,
porters' chests and Storehouse chests with no code of their own; its controller, links and I/O blocks stay out of the
lists (`ModdedBlocks.STORAGE_NETWORK`) so nothing counts twice. Sophisticated Storage hands out at most a stack from a slot
each time it's asked, so counting, taking and putting ask a store again while it gives or takes (up to 256 times, for
an infinity or void upgrade): a limited barrel's slot of 110 raw iron counts as 110, not 64. Builders never break a block that has a block entity
while clearing (`MaterialRules.isProtected`), so a Sophisticated chest on a site stays standing with what's in it, and a
blueprint's Sophisticated chest is built empty (`BlockEntityData`, as any store). New is the room count:
`ItemStores.room` reads every slot's capacity and what's in it, giving the empty slots (as before), the stacks that
still fit (an empty slot holding four stacks counts four; for vanilla chests this is the empty slot count, since a slot
with something in it has less than a stack of room), the items that still fit, and whether any slot holds more than a
stack. An empty slot's room is tried with cobblestone and undone where the slot says it holds more than a stack, because
Sophisticated Storage reports a stack-upgraded empty slot at four times what it really takes. With M37's gate open the board shows the items line when a slot holds more than a stack, and `PorterWork` reads
the free stacks instead of the empty slots, so a limited barrel whose one slot holds some iron still takes the porter's
iron.

**Sophisticated controllers (37.9).** The mod's jar registers Fabric's `ItemStorage.SIDED` for the controller's block
entity, so the controller is one item storage over everything it links (checked in Sophisticated Storage 1.3.7.9 before
building). `store/StorageNetworks` goes by block ids only: from a `sophisticatedstorage:controller` it walks through
touching storage blocks (chests, barrels, limited barrels and shulker boxes of every tier) and `storage_link`s, within
the controller's reach (15 blocks each way, the mod's `SEARCH_RANGE`). `SupplyContainers.find` around a Storehouse
block, with the switch on, folds those blocks into the controller: they leave the list and the controller joins it, so
the board, porters, builders and every job that reads the Storehouse see one store. Around any other workstation the
blocks are read one by one as before (the controller stays out of those lists, `ModdedBlocks.STORAGE_NETWORK`). The mod
links a controller's storage when a player places it; a builder sets blocks without that, so after a
builder places a controller, or a storage block or link joining one's network, `MaterialRules.afterPlaced` has the
controller look again (the block's vanilla placing hook) so it links. Styles gained three optional fields for it:
`"blueprints"` (a regex on the base blueprint ids the style is for), `"requires_switch"` (a config switch it is offered
under) and a rule's `"near"` (the rule applies only to a block touching that block in the blueprint: the lantern on the
Storehouse becomes the controller).

A style rule can also carry `"requires_switch"` (37.7): it applies only while that switch is on, as if its block didn't
exist otherwise, so Grand's Rechiseled rules sit in front of its vanilla ones under `furnishedBuilds`. The swaps are
cached per style and per state of its rules' switches. Job blocks: Carved Wood adds its lecterns to the Librarian's
point of interest, its barrels to the Fisherman's and its beehives to the beehive's (checked in the 1.9.7-B jar, class
`CWPoi`, which puts their block states into the vanilla point-of-interest table), so those are swapped; its crafting
tables are no job site, as the vanilla one isn't. Its campfires are left out (a campfire cooks; nothing in our builds
needs a wooden one).

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| `furnishedBuilds` | on from 2.1 | Builds use the installed furniture mods. Off: every build is the vanilla build |
| `furnitureMods` | handcrafted, cozyhome, cobblefurnies, beautify | The order mods are tried in for each piece (file only); trimmed and lower-cased, blanks and repeats dropped, an empty list means vanilla |
| `moddedStorageNetworks` | on from 2.1 | Controllers and connectors in the Storehouse count as one store. Off: each linked chest is its own store |

With M37's gate closed (every release before 2.1) a config file can't turn `furnishedBuilds` or
`moddedStorageNetworks` on, and the settings screen doesn't list them.

## 4. Saved data

On each build site (`BuildSite.save`): `furniture`, the chosen pieces (per spot: its index, position and kind, the
mod, and each cell's block state), and `furniture_unchosen` on a new site not yet planned. On each finished building
(`BuildSiteManager`): `furniture`, the same. Absent, as in every save from before 37.3: vanilla. Spots and tables are
data files, not saved.

## 5. Items, blocks, jobs, commands

No new items, blocks or commands. Config text: `aliveworkplace.config.furnishedBuilds`,
`aliveworkplace.config.moddedStorageNetworks`. Board text: `screen.aliveworkplace.storehouse.room` (37.8). Block tag `#aliveworkplace:furniture_storage` (37.5): furniture pieces that hold items, never
read as a job's storage while `furnishedBuilds` is on. Block tag `#aliveworkplace:villager_seats` (37.6): the furniture
mods' chairs, stools, benches, dining benches, couches, armchairs and sofas (Handcrafted, Cozy Home, CobbleFurnies),
every entry `"required": false`. Entity `aliveworkplace:furniture_seat` (37.6): the unseen seat a sitting villager
rides, which can't be summoned.

## 6. Decisions

- Support goes by block and item ids, tags and data files only, never the mods' classes, so it is all core code with
  no `compat/` package; a piece is used when the registry has its id (ROADMAP Milestone 37).
- Beautify is last in the default order because it makes decorations only (design note M37).
- The Trainer's room (owner, 2026-10-18): homes are planned before anyone lives in them, so "a home with a Pokémon
  partner" is read when the build is planned, as the partners' own rule reads it for jobs: a Pokémon kept in a Pasture
  Block within 16 blocks of the plot (asked through the Pokémon extension, so it is false without Cobblemon). The room is
  the home's first chair and the table or desk nearest it (within 3 blocks); only in a build with a bed, only when
  CobbleFurnies' Poké Ball chair is in the registry and `cobblefurnies` is in `furnitureMods` at all. The choice is
  saved with the site like every other piece, so a partner leaving later doesn't change a standing home.
- Workplaces (37.5): a piece is a spot only where it fits around the job: never on a job block (a villager point of
  interest), a bed or a container, which the generator refuses (`kit.furnish`) and a GameTest checks for every build.
  A workplace's beds get no bed spot at all, since a Handcrafted fancy bed isn't one the game's villagers claim.
  Handcrafted's counters and desks hold items, so with furnished builds on the block tag
  `#aliveworkplace:furniture_storage` keeps every such piece out of the jobs' and the Storehouse's storage (the design
  note's "decoration only"); with the switch off nothing changes. New kinds `trellis` and `hanging_pot` (Beautify's
  `{wood}_trellis` with roses and `hanging_pot` with vines); vanilla, two open trapdoors flat on a wall and a potted
  plant on a chain from a beam. The Kitchen is too small for a bench beside its table, oven, bed and larder hatch, and a
  block more in it made the chef's job test fail (B168), so its crockery takes the potted fern's place on the worktop.
- The room in items is a line of its own, shown only where a slot holds more than a stack, so the board with vanilla
  chests doesn't change (37.8). The porter's free stacks count a slot's room for more of what it holds; a load of
  something else that doesn't fit stays in his bag and he waits, as with any full store.
- Seats (37.6): supper is the Village Hall's banquet hour (10500), and the evening runs till the villagers' schedule
  turns to rest (12000); vanilla villagers have no other meal hour, so "for meals" is supper. A villager's seat isn't
  saved: a villager sitting when the world is saved stands up after the load (the design note), so the seat is saved
  only to carry its passenger and lets them go on its first tick. "A furnished room" is a home whose building holds a
  seat from `#aliveworkplace:villager_seats` (only the mods' pieces are in it), worth 1 point (`FurnitureSeats.FURNISHED_BEAUTY`)
  to its people's beauty need; off with `furnishedBuilds`.
- The Grand style keeps its name and gains the Rechiseled rules rather than a new style, as the roadmap item names it
  (37.7); the rules are behind `furnishedBuilds`, so Grand on servers before 2.1 is unchanged. A Grand building half
  built when 2.1 turns the switch on gets its remaining floors and panels in Rechiseled
  (not tested: whether a repair of a finished Grand building then swaps its floors).
- Lanes don't make review packages (sessions.md, 2026-10-04): the digest sends the design note as 37.1's package.

## 7. Known limits

- The M34 workshops other than the Print Shop (the Tailor's and Jeweler's) and the other workplaces keep their vanilla
  furniture for now; storage networks come with 37.9 and 37.10.
- Villagers look for a seat within 8 blocks of where they stand, not only in their own home, and walk to it for at
  most 30 seconds before choosing again.
- A vanilla picture is a plain wall banner (a block, so a builder can place it): an item frame or a painting would be
  an entity, which a furniture spot can't hold.
- The headless blueprint renderer (`tools/blueprints/render`, the architect skill's) can't draw Handcrafted,
  CobbleFurnies or Beautify pieces (they come out empty), so 37.4's renders reviewed the rooms in vanilla and the
  modded look is judged in the real client, in the `furnished_home` scene; Cozy Home's facing is checked by the pack
  test only.
- A building finished before 37.3 is repaired as the vanilla build.
- The generator finds only chairs, tables and beds by itself; couches, shelves, lamps and the rest are spots only where
  a drawing records them (the homes' rooms since 37.4; the workplaces with 37.5).
- Lucky's Cozy Home can't load in the dev runtime, so it is tested by the pack test only.
- Sophisticated shulker boxes are stores too (they have item storage), but 37.8 tests only the chests and barrels.
- The Storehouse finds what a controller links by walking block ids, not by asking the mod: a block the walk reaches
  that the controller hasn't linked (placed by a command with no placing) drops out of the store list without being
  read through the controller. Placed by a player or a builder, the two agree.

- The headless renderer draws Carved Wood and Rechiseled (the architect skill's `mods.mjs add carved-wood rechiseled`),
  but its render of a hanging Carved Wood lantern is flagged as standing on air, which the game's block doesn't do (it
  keeps `hanging=true`).

## 8. Proof

GameTests: `FurnishingConfigGameTests.furnitureModsKeepTheirOrderAndAreCleaned` (the default order, cleaning, an empty
and a missing list, the list off the screen and in the saved file), `FurnishingConfigGameTests.theM37GateHoldsItsSwitches`
(a player's file can't turn them on with the gate closed; with it open they obey the file), and
`ExpansionGateGameTests` (both switches in its gated lists). 37.2:
`FurnitureSpotGameTests.starterCottageSpotsAreWhereItsChairsAndTablesAre`,
`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`, `FurnitureSpotGameTests.aBrokenSpotsFileIsSkipped`,
`FurnitureSpotGameTests.aStyleCarriesTheSpotsOver`. 37.3 (no mods, a test table of vanilla blocks):
`FurnishingTableGameTests.withoutFurnitureModsTheBuildIsVanilla`,
`FurnishingTableGameTests.theFirstModWithAFittingPieceIsChosen`, `FurnishingTableGameTests.switchedOffTheBuildIsVanilla`,
`FurnishingTableGameTests.aSiteSavedMidBuildKeepsItsChoice`, `FurnishingTableGameTests.aRemovedModsPieceTurnsBackToVanilla`;
with the mods (compat): `FurnishedBuildCompatTests.handcraftedFurnishesTheStarterCottage`,
`FurnishedBuildCompatTests.cobbleFurniesFirstGetsItsPieces`, `FurnishedBuildCompatTests.switchedOffItIsTheVanillaBuild`,
`FurnishedBuildCompatTests.aSavedSiteKeepsItsHandcraftedPieces`.
37.4 (no mods): `FurnishedHomesGameTests.everyHomeHasItsFurnishedRooms`,
`FurnishedHomesGameTests.withoutFurnitureModsEveryHomeIsVanilla`, `FurnishedHomesGameTests.aBuilderBuildsTheFurnishedInnInVanilla`,
`FurnishedHomesGameTests.theBuilderWaitsForAMissingPicture`, `FurnishedHomesGameTests.withoutCobbleFurniesThereIsNoTrainersRoom`; with the mods (compat):
`FurnishedHomesCompatTests.aBuilderBuildsTheCottageWithHandcrafted`, `FurnishedHomesCompatTests.aBuilderBuildsTheCottageWithCobbleFurnies`,
`FurnishedHomesCompatTests.aBuilderBuildsTheInnWithHandcrafted`, `FurnishedHomesCompatTests.aBuilderBuildsTheInnWithCobbleFurnies`
(each built from chests of exactly its materials, nothing skipped, each spot the piece of the mod the order picks for its
kind), `FurnishedHomesCompatTests.theBuilderWaitsForAMissingCouch`, `FurnishedHomesCompatTests.aSavedFurnishedSiteKeepsEveryPiece`,
`FurnishedHomesCompatTests.switchedOffTheRoomsAreVanilla`, `TrainersRoomCompatTests.aPartneredHomeGetsThePokeBallChairAndDesk`
(a pastured Pikachu by the plot: the Poké Ball chair and desk with Handcrafted first; far from the pasture, or with
CobbleFurnies left out of the list, Handcrafted's). Showcase scene `furnished_home` (filmed with `-Pfurniture=true`,
the pack's furniture mods): Starter Cottage II furnished, room by room, beside the vanilla one.
37.5 (no mods): `FurnishedWorkplacesGameTests.noSpotCoversAJobBlockABedOrAContainer` (every shipped spot of every build),
`FurnishedWorkplacesGameTests.everyWorkplaceIsFurnished`, `FurnishedWorkplacesGameTests.withoutCobbleFurniesTheDesksFollowTheOrder`,
`FurnishedWorkplacesGameTests.aBuilderBuildsTheFurnishedSupplyShopInVanilla` (hanging pots and trellises included,
nothing skipped); with the mods (compat): `FurnishedWorkplacesCompatTests.theSupplyShopWithHandcraftedAndBeautify`,
`FurnishedWorkplacesCompatTests.theLibraryWithHandcraftedAndBeautify`, `FurnishedWorkplacesCompatTests.thePrintShopWithHandcraftedAndBeautify`
(each built with nothing skipped, its job block standing and a job site, and its storage exactly the vanilla chests and
barrels: before the tag the Print Shop's Handcrafted desk and counters counted as chests),
`FurnishedWorkplacesCompatTests.theHealingCenterAndSchoolhouseGetPokeBallDesks`. Showcase scene `furnished_workplaces`.
37.8: `StorageRoomGameTests.vanillaRoomIsCountedAsBefore`,
`StorageRoomGameTests.aFullVanillaStoreIsFullForPorters` (no mods: the vanilla counts and board unchanged);
with Sophisticated Storage (compat): `SophisticatedStorageCompatTests.everyTierStoresAndGivesItems` (all 36 blocks),
`SophisticatedStorageCompatTests.porterFillsALimitedBarrelFromASophisticatedBarrel`,
`SophisticatedStorageCompatTests.builderUsesAndLeavesSophisticatedStorage`,
`SophisticatedStorageCompatTests.aStackUpgradeShowsItsRealRoomOnTheBoard`, and the older
`PackModsCompatTests.packStorageWorksAsSupplyChests`.
37.9 (compat, Sophisticated Storage): `SophisticatedControllerCompatTests.aControllerMakesThreeBarrelsOneStore` (counted
once, a delivery put away and a builder's take through the controller, the board's line, three stores again once it
breaks), `SophisticatedControllerCompatTests.aPorterDeliversThroughTheController`,
`SophisticatedControllerCompatTests.switchOffKeepsThreeStores`, `SophisticatedControllerCompatTests.theSophisticatedStyleAddsAController`.
Showcase scene `modded_storehouse` part one (filmed with `-Pfurniture=true`, which now brings Sophisticated Storage).

37.6 (no mods): `FurnitureSeatsGameTests.theSeatsTagLoadsWithoutTheFurnitureMods`; with Handcrafted (compat):
`FurnitureSeatsCompatTests.aVillagerSitsOnAHandcraftedChairAtSupperAndGetsUpAtDawn`,
`FurnitureSeatsCompatTests.aSeatInUseIsntTakenTwice`, `FurnitureSeatsCompatTests.aVillagerStandsUpWhenHurtAndNobodySitsWithTheSwitchOff`,
`FurnitureSeatsCompatTests.aSeatLoadedFromASaveLetsItsVillagerGo`,
`FurnitureSeatsCompatTests.aVillagerClaimsAndSleepsInAFancyBedAndTheHouseIsTheirHome` (the fancy bed is a villager home
point of interest, the villager claims and sleeps in it, the Starter Cottage it stands in is their home, and its chair
adds the furnished home's beauty). Scene `seats_and_beds` (`-Pfurniture=true`).

37.7 (compat, with Carved Wood and Rechiseled): `CarvedStylesCompatTests.theStylesUseCarvedWoodAndRechiseled` (each
swap, the Carved Wood lectern the Librarian's job site and barrel the Fisherman's, a hanging lantern still hanging; with
the switch off no Spruce style and Grand as before), `CarvedStylesCompatTests.aCarvedWoodChestIsASupplyChest` (a
builder builds from a Carved Wood chest; a Carved Wood barrel is listed and filled),
`CarvedStylesCompatTests.aSpruceLibraryHasCarvedShelvesAndALibrarian` (built with nothing skipped, every shelf Carved
Wood's, and a villager without a job takes the Carved Wood lectern and becomes the Librarian),
`CarvedStylesCompatTests.theGrandStyleWithRechiseledBuildsWithNothingSkipped` (the Grand Starter Cottage). No mods:
`StyleGameTests.stylesLoadAndIdsRoundTrip` lists Spruce. Scene `carved_styles` (`-Pfurniture=true`, which now brings
Carved Wood and Rechiseled).
