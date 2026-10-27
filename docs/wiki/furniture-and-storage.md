# Furniture and storage mods

Builds furnished with the pack's furniture mods (Handcrafted, Lucky's Cozy Home, CobbleFurnies, Beautify) when the
server has them, and Sophisticated Storage and Tom's Simple Storage as village storage. Part of Furnished homes and
modded storage (2.1), behind its expansion gate (`Expansions.M37`), which stays closed until the release that finishes
it. The plan is the design note `docs/design/M37.md`.

Roadmap items: 37.1, 37.2, 37.3, 37.4, 37.5, 37.6, 37.7, 37.8, 37.9, 37.10, 37.11, 37.12, 37.13, 37.17, B238, B260, B262, B281, B300, B304, B305, B310, B339, B371, B402, B403

## 1. What a player sees

Each mod at a glance (every one optional; the player guide's page is `docs/guide/furniture-mods.md`):

| Mod | What it changes | Switch | Without it |
|---|---|---|---|
| Handcrafted | Chairs, tables, fancy beds (villagers sleep in them), nightstands, couches, shelves, crockery; the workplaces' counters and desks | `furnishedBuilds` | The next mod in `furnitureMods` with that kind, else the vanilla piece |
| Lucky's Cozy Home | Sofas, chairs, tables, desks, counters, lamps, grandfather clocks | `furnishedBuilds` | The same |
| CobbleFurnies | Chairs, tables, nightstands, sofas, lamps, curtains; the Poké Ball chair and desk (a Trainer's room, the Healing Center's and Schoolhouse's desks) | `furnishedBuilds` | The same; no Trainer's room, the desks follow the order |
| Beautify | Picture frames, blinds, candelabras, rose trellises, hanging pots | `furnishedBuilds` | The same |
| Sophisticated Storage | Its chests and barrels are stores (always); the board's room line; a controller is one store for what it links | `moddedStorageNetworks` (the controller) | Vanilla chests only; off, each linked block is its own store |
| Tom's Simple Storage | An inventory connector in the Storehouse is one store for its network; its terminal shows the village's goods | `moddedStorageNetworks` | Off, each chest on the network is its own store |

With none of them installed, or `furnishedBuilds` off, every build is exactly the vanilla build; a building keeps the
pieces it was built with whatever changes later, and a removed mod's pieces come back as vanilla pieces under upkeep.

Nothing yet for players: the expansion's gate stays closed until 2.1. Behind it, a build started on a server with
Handcrafted gets Handcrafted chairs, tables and fancy beds where the vanilla build had stair chairs, fence tables and
beds (37.3); the Blueprint Table's preview, its supply report and the material list book show those pieces and cost
their items, and so does the builder's shopping list.

Any mod's piece will do (37.13): a spruce chair is a spruce chair whichever furniture mod made it. The material list
book shows the piece once, "Spruce Chair (any furniture mod)", with each mod's piece on hover, and counts any of them
in the chests; the Builder builds whichever mod's piece its chests (or its bag) hold, the first in `furnitureMods` when
several are there, in the same place, footprint and facing. A spot already going up keeps its piece, a spot none of
whose pieces is in the chests keeps the planned piece (the builder waits for it, as before), and a piece no installed
mod has is the vanilla stand-in as before. Scene: `furniture_any_mod`.

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

Rooms that read as rooms (B260): nothing stands in a door's way (two blocks in and out of every door), every bed has
its head against a wall (or a window in it) and faces into the room, a lantern hangs or stands on a table or post,
never on the bare floor, and a chest stands against a wall or at the foot of a bed. Each spot records what it stands
on or against in the drawing (floor, wall, ceiling), and a modded piece goes only where what it needs is there: a
Beautify picture frame stands on a table, so a picture on a wall stays a banner; a Handcrafted shelf stands on the
floor, so a shelf on a wall stays a slab; a two-block-tall piece (a `half=lower|upper` block) is placed with its top
half or not at all. The generator refuses a furnished build that breaks these rules, and `FurnishedRoomsGameTests`
checks every shipped one. The Stone House was redone for it: the table by the west window and the clock by the bed
(not beside the door), the attic a bedroom (the bed's head to the gable, a nightstand, a chest at its foot, a rug and a
lamp hung from the ridge), and upstairs in II two beds with their heads to the back wall, a nightstand by each.

The workplaces are furnished too (37.5), at every level: the Supply Shop's counter boards either side of the Shop
Counter are counters, with a shelf of cups and a picture on the back wall, pots hanging from chains in the display
windows and a trellis either side of the door (open trapdoors flat on the wall); the Library's reading tables are
desks with their chairs, pictures by the door and hanging pots over the desks; the Schoolhouse's rows and the Village
Hall's are benches, the Healing Center has waiting benches and a nurse's desk, the Schoolhouse a teacher's desk by the
lectern, the Print Shop a printer's desk by the press and its buyers' counter, Village Hall II a council table with
seven chairs (none in front of its doorway), and the Kitchen crockery on the worktop (in place of its potted fern). With Handcrafted and Beautify those become
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

A bed up a ladder is a bed like any other (B304). The Terrace is two homes under one roof, each with its bedroom up a
ladder: a villager without a bed takes a free one there, even next door to where he stands, and at bedtime he goes in
by that home's door and climbs that home's ladder. Before, a villager could only claim a bed he could walk to, so the
Terrace's beds stood free while villagers spent the night standing about its rooms (on a chest, say), and a villager
coming in by the other home's door climbed the wrong ladder and gave his bed up.

When it is finished, a server with Handcrafted gets Handcrafted chairs round a Handcrafted table, fancy beds with nightstands and
shelves of crockery in its homes; Cozy Home adds sofas, clocks and lamps, CobbleFurnies Poké Ball chairs, Beautify
blinds, picture frames and candelabras; villagers sit on the chairs and claim and sleep in the fancy beds (a bed spot only takes a mod's bed when villagers can
sleep in it, as Handcrafted's; any other stays a vanilla bed). Without any of those
mods every build is exactly today's vanilla build. The Storehouse also reads Sophisticated chests and barrels of every
tier, and a Sophisticated controller or a Tom's inventory connector as one store for everything it links.

Sophisticated Storage's chests, barrels and limited barrels already work as supply chests, porters' chests and
Storehouse chests with the gate closed (they always did, through the same item storage every chest uses). Behind the
gate (37.8): where a slot holds more than a stack, a stack upgrade or a limited barrel, the Storehouse board's info adds
"Room for 6912 more items (some slots hold more than a stack)" under the usual "chests, items, empty slots" line ("1 chest", "1 item" and "1 empty slot" with one, B377 and B395), and
porters keep filling such a store while any of its slots has room for another stack, even when no slot is empty. With
only vanilla chests the board and the porters are exactly as before.

A Sophisticated Storage controller (37.9, behind the gate and `moddedStorageNetworks`): put one in the Storehouse's area
touching your Sophisticated barrels and chests, and the Storehouse treats everything it links as one store. The board
counts their goods once and adds "Linked through a storage controller: 3 blocks"; porters put deliveries in through the
controller, which files them into the linked blocks, and builders take what they need through it. Break the controller
and the blocks are separate stores again, with everything still in them. A "Sophisticated" style for the Storehouse
(I, II and III; offered only with the mod and the switch on) builds Sophisticated chests and barrels in place of the
vanilla ones and a controller on top of the Storehouse block, touching the chests on both sides.
A Sophisticated barrel or chest a builder adds to a controller's network joins it, even when it touches only another
linked barrel, as it would if you placed it yourself (B238).
Behind the gate (37.10), a Tom's Simple Storage inventory connector within the Storehouse's 8 blocks is one store for
every inventory it joins: the chests, barrels and filing cabinets touching it (and touching those), and the one each
cable connector on its cables faces. The board's first line counts the connector as one chest, and a second line says
"Linked through an inventory connector: 3 blocks". Porters put goods in through the connector and builders take what
they need through it, so a chest on the network counts once, however many lists it is near. Unplug a chest (break its
cable connector) and it is a store of its own again at once. Two connectors on one network are read as one: the
Storehouse goes through just one of them, so nothing counts twice. A storage or crafting terminal on the network is Tom's
own: a player opens it and sees, and takes, the village's goods (place the terminal against the connector or a cable,
its screen facing you, and click it from within Tom's reach of 6 blocks). With `moddedStorageNetworks` off, every chest on the
network is its own store and the connector is passed over, as before.

Carved Wood and Rechiseled (37.7, behind the gate and `furnishedBuilds`): the Blueprint Table offers a **Spruce** style
for every build. It builds the timber in spruce (oak and dark oak logs, planks, stairs, slabs, fences, doors and
trapdoors; birch infill becomes stripped spruce wood), and with Carved Wood installed its chests, trapped chests,
bookshelves, chiseled bookshelves, lecterns, ladders, lanterns and beehives are Carved Wood's spruce ones (crafting
tables stay vanilla: the vanilla one is the Carpenter's job site and Carved Wood's isn't, B388), so a spruce Library has spruce shelves and a spruce lectern its Librarian works at. Carved Wood has no spruce
barrel (the vanilla barrel is spruce already), so barrels stay. With Rechiseled installed the **Grand** style's floors
(spruce and oak planks) are `rechiseled:dark_oak_planks_flooring`, its wall panels (birch infill, white terracotta)
`rechiseled:andesite_squares` and its polished andesite `rechiseled:andesite_tiles`. Without the mods, or with the
switch off, the Spruce style is plain spruce (and isn't offered with the switch off) and Grand is exactly today's Grand.

Villagers walk round a Carved Wood lantern (B339). The game asks each block whether a path may go through it; vanilla's
lantern says no, Carved Wood's says yes, so a jobless villager's path to the spruce Library's lectern once ran up onto a
desk and through the lantern standing on it, and the villager stood on the desk's edge for the rest of the day. Now any
small block a villager's body can't pass above the height it steps up (such as a mod's lantern) is a wall
to its path, as vanilla's lantern is; carpets, beds and other blocks it steps onto, ladders and open doors stay
walkable (`work/PathObstacles`, from the path finder's `WalkNodeEvaluatorMixin`, beside B156's shut trapdoors).
A Carved Wood lantern goes up with the decoration, after the trapdoor or post it stands on, as vanilla's lantern does
(B433: it once went up with the walls, before the flower-box trapdoors by a cottage's door, couldn't stand on air and
was skipped). The builder knows it by its `hanging` property, which vanilla's lantern has too (`MaterialRules`).
Carved Wood's chests and barrels of every wood are supply chests, porters' chests and Storehouse chests (they are
vanilla chests and barrels underneath).

**Two build libraries** (37.12). Every blueprint as it ships is the **vanilla library**, never changed. The **modded
library** is a copy of every home and workshop (130 builds: every tier of the homes, workshops, job builds and the
Cobblemon workplaces; decorations, walls, the arenas and the Create builds have none, and the open-air Habitat Garden
has nothing to dress) with the same shape, doors, beds, job blocks and furniture spots, dressed with the pack's
building mods: the plaster and the birch infill of the timber frames are Supplementaries daub, with a brace in a
panel's bottom corner beside a post; covered wooden floors are Chipped herringbone boards of the same wood; polished
andesite is Rechiseled andesite tiles and covered cobblestone floors Rechiseled cobblestone brick paving; chests,
bookshelves and ladders are Carved Wood's in the build's own wood; and Handcrafted puts crockery on every table, stacks
of books on shelves in the rooms and pots beside barrels. The furniture spots are still furnished from the furniture
mods as above, so a modded cottage has Handcrafted chairs and beds too. A new build is built from the modded library
when `moddedLibrary` and `furnishedBuilds` are on and every block of its copy is installed (so the pack uses it); if any
of those mods is missing, or a switch is off, it is the vanilla build, furnished as before. The choice is made when the
build is planned and saved with it, so a config change or a removed mod never changes a build going up or standing
(a copy whose blocks are gone later is built as the vanilla build). The Blueprint Table, the preview and the materials
list show the library this server would build. Job blocks (lecterns, barrels, beehives, crafting tables) and doors are
never swapped, and nothing a copy adds holds items, so every job finds exactly the storage of the vanilla build.

**Storage builds** (37.17, behind the gate). Three new builds at the Blueprint Table, each a Storehouse (its
Storehouse block takes a porter, and the village and the Village Map count it as one): the **Storage Hall** (13 x 14 x
14: a long stone-and-birch hall with a porch, 27 chests and barrels in an island round an inventory connector, a
counter by the door with a storage terminal and a crafting terminal either side of the Storehouse and the Carpenter's
crafting table, two filing cabinets
for the paper and maps on the back wall), the **Terminal Nook** (9 x 10 x 9: a small room that fits beside a house, six
chests round a connector along its back wall, a storage terminal before it) and the **Warehouse** (19 x 16 x 16: two
floors under a long roof, four racks of chests and barrels below and two in the loft, 60 in all, a crafting terminal and a
crafting table at the packing counter by the loading doors, a lean-to loading bay with a handcart). With Tom's Simple Storage installed
they are built of Tom's blocks and every chest, barrel and cabinet is on the connector's network: the Storehouse reads
them through it (each item once) and the terminals show them in Tom's own screen. Without Tom's (or with
`moddedLibrary` or `furnishedBuilds` off) they are the vanilla builds: a chest where the connector stands, a counter
stair for the crafting terminal, a counter block (or nothing, in the Nook) for the storage terminal, boards and spruce
logs for the cables and trims, chests for the filing cabinets; every chest and barrel is then the Storehouse's own store. The crafting table is in
both versions at the same spot (B388: a crafting terminal is no job site, so the copies once took the Carpenter's).

Nothing a builder furnishes floats (B300). A pot, candle or lantern that stood on a piece the furniture mod's piece
doesn't reach up to (a fence-and-carpet table become a one-block table) is set down on the new piece. A small thing
on the build's ground layer gets a footing of dirt where the ground under it is lower (a potted flower before a
headstone on a hillside), as walls get theirs. And when the build is done (after the landscaping) the builder looks
over everything placed: a small thing left resting on nothing is set down on the first block under it, and anything
the game says can't stay where it is (a wall torch with no wall) is broken and dropped as an item for the chests.

## 2. How it works

Each piece of furniture our builds draw in vanilla blocks becomes a furniture spot (its kind, facing, wood or color
and blocks). When a build is planned, each spot becomes the piece of the first mod in `furnitureMods` that the server
has, found by block id in the registry; no class of any of those mods is used, so they stay optional. A spot whose
modded piece doesn't fit, or whose kind no installed mod makes, keeps its vanilla blocks. The full formats, the list
of kinds and each mod's block ids are in the design note.

**Any mod's piece will do (37.13).** `data/<namespace>/furniture_equivalents/*.json` lists, per kind (chair, couch,
table, desk, counter, cupboard, nightstand, drawer, lamp), whether its pieces match by wood or by color and each mod's
block id (`{wood}` or `{colour}` filled in); every file adds to the table (a datapack can add a mod), and a file with a
mistake is logged with its path and skipped. `furnish/FurnitureEquivalents` reads it the way `MaterialFamilies` reads
Chipped's tags: a block of a group stands for the group's other installed pieces of the mods in `furnitureMods`, in
that order, and nothing is furniture with `furnishedBuilds` off. The material list counts a piece by the first mod's
item and any of the group's items in the chests (`BlueprintSupplies.key`/`have`), and names it with
`book.aliveworkplace.material_list.any_mod` and a hover listing each piece (`.mod_options`, `.mod_option`, the mods'
names under `furniture_mod.aliveworkplace.<mod>`). When a builder goes for a furniture piece its chests don't hold
(`BuilderWork.fetch`), `build/FurnitureStock.restock` gives every spot not yet started (all its cells still air)
`FurnitureChoice.restocked`: the first mod in the order whose piece of that group is installed, has the planned piece's
footprint (and, for a bed, is a villager's bed) and is in the chests or the bag, each piece taken off what's left so two
spots never count one chair. The placed piece is the mod's own table piece for the kind with that block (its facing
rule, states and footprint), so it faces the way the planned one did. The new choice is saved on the site and the plan
is worked out again (its steps are sorted by position, so the order and the cursor hold). A spot with none of its
pieces in stock keeps its piece; a Trainer's-room Poké Ball piece belongs to no group and never changes; a repair, a
taking-down or a cellar keeps what its building has.

**Furniture spots (37.2).** Each of our blueprints with furniture has a file beside it,
`data/aliveworkplace/blueprint_furniture/<blueprint>.json` (223 of them), listing its spots: kind, position, facing,
wood or color, and the vanilla blocks of the piece. The blueprint generator (`tools/blueprints/furniture.py`) finds
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
Sophisticated Storage reports a stack-upgraded empty slot at four times what it really takes. The item tried is the
first of cobblestone, dirt, gravel, sand and other plain blocks that the store holds none of (B371): a controller puts an
item it already holds into the barrel holding it once for each way it looks (that stack, that item, any empty slot),
and inside one transaction the barrel takes the same room each time, so 100 cobblestone behind a controller made the
board say "Room for 22164 more items" where 8540 fit. For the same reason a put counts only what really landed in the
store's slots (unless that is nothing, as with a void upgrade): one put that overflowed the barrel holding the item was
reported whole while the overflow went nowhere; asked again, the controller files the rest into the next barrel. With M37's gate open the board shows the items line when a slot holds more than a stack, and `PorterWork` reads
the free stacks instead of the empty slots, so a limited barrel whose one slot holds some iron still takes the porter's
iron.

**Sophisticated controllers (37.9).** The mod's jar registers Fabric's `ItemStorage.SIDED` for the controller's block
entity, so the controller is one item storage over everything it links (checked in Sophisticated Storage 1.3.7.9 before
building). `store/StorageNetworks` goes by block ids only: from a `sophisticatedstorage:controller` it walks through
touching storage blocks (chests, barrels, limited barrels and shulker boxes of every tier) and the `storage_link`s
linked to it (the mod links a link only with its Storage Tool and saves the controller's position on it; an unlinked
link is passed over, so the storage behind it stays a store of its own, B257), within the controller's reach (15 blocks each way, the mod's `SEARCH_RANGE`). `SupplyContainers.find` around a Storehouse
block, with the switch on, folds those blocks into the controller: they leave the list and the controller joins it, so
the board, porters, builders and every job that reads the Storehouse see one store. Around any other workstation the
blocks are read one by one as before (the controller stays out of those lists, `ModdedBlocks.STORAGE_NETWORK`). The mod
links a controller's storage when a player places it; a builder sets blocks without that, so after a
builder places a controller, or a storage block joining one's network, `MaterialRules.afterPlaced` runs the block's
vanilla placing hook (the controller's, or the storage block's, which joins the controller it touches) so it links. A
link a builder places stays unlinked, as one a player places without the tool. Styles gained three optional fields for it:
`"blueprints"` (a regex on the base blueprint ids the style is for), `"requires_switch"` (a config switch it is offered
under) and a rule's `"near"` (the rule applies only to a block touching that block in the blueprint: the lantern on the
Storehouse becomes the controller).
**Tom's Simple Storage (37.10).** `store/StorageNetworks` finds a connector's inventories by block ids the way Tom's
connector does (read from its 2.3.0 jar): from the connector through the inventories touching it, its trims and its
inventory interface, within 16 blocks (Tom's connector scan range); and along its cable blocks (cables, framed
cables, cable connectors, terminals, hoppers, level emitters, other connectors; at most 2048), the block each cable
connector faces (`facing`). The connector exposes all of them through Fabric's transfer API, so `SupplyContainers.find`
around the Storehouse block lists the connector and drops its inventories. A connector that joins any inventory an
earlier one already joined is left out (B305: two connectors on one cable network both expose all of it), the ones
joining most going first, then the nearest; nothing is saved, the walk is done again on
every lookup. `Village.stashes` treats a connector as all its inventories: a chest of it listed in an earlier stash (a
builder's own chests) turns the connector into its remaining chests one by one, so nothing counts twice across stashes.
Every other job's lists still pass over Tom's blocks (`ModdedBlocks.STORAGE_NETWORK`) and read the chests themselves.

A style rule can also carry `"requires_switch"` (37.7): it applies only while that switch is on, as if its block didn't
exist otherwise, so Grand's Rechiseled rules sit in front of its vanilla ones under `furnishedBuilds`. The swaps are
cached per style and per state of its rules' switches. Job blocks: Carved Wood adds its lecterns to the Librarian's
point of interest, its barrels to the Fisherman's and its beehives to the beehive's (checked in the 1.9.7-B jar, class
`CWPoi`, which puts their block states into the vanilla point-of-interest table), so those are swapped; its crafting
tables are no job site, while the vanilla one is the Carpenter's (`aliveworkplace:crafting_table`), so no style swaps
the crafting table (B388: Spruce did, and a Carpenter couldn't take it). Its campfires are left out (a campfire cooks; nothing in our builds
needs a wooden one).

37.17: the storage builds are drawn by `tools/blueprints/storage_halls.py`, each function drawing both versions from
one plan so they have the same shape: the vanilla build to `structure/<name>.nbt` and the Tom's copy to
`structure/modded/<name>.nbt`, the modded library's folder, so `ModdedLibrary` builds the copy when every block of it
(Tom's and vanilla only) is installed, through the same choice and saved `library` entry as every other copy.
`modded_library.py` leaves these three alone (`OWN_COPIES`). How the network joins them is 37.10's walk: the Hall's
island touches its connector, and a framed cable under the floor runs to the counter's terminals (the crafting
terminal at the counter's end, where the vanilla build has a counter stair; the Warehouse's reads the connector from
the counter stair's cell under the Storehouse, so both keep the vanilla build's crafting table, B388) (set into it with
`pos=down`, reading the cable under them) and to a framed cable connector under each filing cabinet; the Nook's chests
touch the connector; the Warehouse's racks stand on Inventory Trim set in the floor, which the connector's scan passes
through, up a trim post to the loft. Every store stands within the Storehouse's reach (8 across, 4 up or down), so the
vanilla builds' chests are its stores too. They are in `StarterBlueprints.STORAGE_HALLS` (and `ONE_TIER`), offered
while `moddedStorageNetworks`'s expansion (M37) is open.

Beds up a ladder (B304): vanilla claims a bed (`AcquirePoi`) only when it finds a path to it, and its paths have no
ladders. When that path falls short, `BedLadders.pathUpALadder` (from `AcquirePoiMixin`) looks at the same free beds
for one with a ladder within 8 blocks whose top comes up in the bed's room (a walk on the bed's floor from the ladder's
top to the bed: nothing taller than a step underfoot, nothing at head height, doors open) and whose foot the villager
can walk to; that bed is claimed as vanilla claims any. At bedtime `BedLadders` takes the villager to the foot of a
ladder that comes up in his bed's room, before any nearer one that doesn't (when none does, the nearest, as before).
Nothing is saved.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| `furnishedBuilds` | on from 2.1 | Builds use the installed furniture mods. Off: every build is the vanilla build |
| `furnitureMods` | handcrafted, cozyhome, cobblefurnies, beautify | The order mods are tried in for each piece (file only); trimmed and lower-cased, blanks and repeats dropped, an empty list means vanilla |
| `moddedStorageNetworks` | on from 2.1 | Controllers and connectors in the Storehouse count as one store. Off: each linked chest is its own store |
| `moddedLibrary` | on from 2.1 | Homes and workshops are built from the modded library when all its mods are installed and `furnishedBuilds` is on (37.12). Off: the vanilla library |

With M37's gate closed (every release before 2.1) a config file can't turn `furnishedBuilds`, `moddedStorageNetworks`
or `moddedLibrary` on, and the settings screen doesn't list them.

## 4. Saved data

On each build site (`BuildSite.save`): `furniture`, the chosen pieces (per spot: its index, position and kind, the
mod, and each cell's block state), and `furniture_unchosen` on a new site not yet planned. On each finished building
(`BuildSiteManager`): `furniture`, the same. Absent, as in every save from before 37.3: vanilla. Spots and tables are
data files, not saved. 37.12: a build from the modded library has `{"library": "modded"}` in its `furniture` list (on
the site and on the finished building); absent: the vanilla library. A version before 37.12 reads that entry as
nothing (it has no kind), so the save still loads.

## 5. Items, blocks, jobs, commands

No new items or blocks. The pack test's `/workplace furnished [days]` (37.11; only with `-Daliveworkplace.benchmark=true`,
never on a normal server) lays flat ground, a builder on the Starter Cottage and another on the Inn, barrels of exactly
their materials, and logs a `Furnished:` line per build and one `Furnished result:` line. Config text: `aliveworkplace.config.furnishedBuilds`,
`aliveworkplace.config.moddedStorageNetworks`. Board text: `screen.aliveworkplace.storehouse.room` (37.8),
`screen.aliveworkplace.storehouse.linked` (37.9), `screen.aliveworkplace.storehouse.connector` (37.10). Block tag `#aliveworkplace:furniture_storage` (37.5): furniture pieces that hold items, never
read as a job's storage while `furnishedBuilds` is on. Block tag `#aliveworkplace:villager_seats` (37.6): the furniture
mods' chairs, stools, benches, dining benches, couches, armchairs and sofas (Handcrafted, Cozy Home, CobbleFurnies),
every entry `"required": false`. Entity `aliveworkplace:furniture_seat` (37.6): the unseen seat a sitting villager
rides, which can't be summoned.

## 6. Decisions

- 37.13: "neither (vanilla stand-in)" in the item is read as point (3): a piece no mod in `furnitureMods` has is the
  vanilla stand-in, whatever the chest holds. With an installed mod's piece planned and none of its equivalents in the
  chests, the builder waits for the planned piece as 37.4 decided (no stand-in, `theBuilderWaitsForAMissingCouch`),
  otherwise a server with the mods would build stairs whenever the chests ran short. Pieces switch only when the
  builder goes for one it lacks, so a piece already in the bag or the chests is never traded for another; a spot
  switched once can switch again while it isn't built. The equivalents are their own data file rather than read from
  the furnishing tables, since a table's fallback ids (CobbleFurnies' colored chair) aren't the same piece.

- 37.12: the modded copies are drawn by `tools/blueprints/modded_library.py` from the shipped vanilla templates (one
  pass over every home and workshop, by position, no randomness), written to `data/aliveworkplace/structure/modded/`,
  and read by `blueprint/ModdedLibrary` (a reload listener that keeps each file packed and its block ids). A copy keeps
  its vanilla build's id, spots, upgrades and basement, so nothing else in the mod (upgrades, the Steward's rules,
  homes, jobs) needs to know which library a building came from; the choice rides on the saved furniture choice.
  The copies aren't in the Blueprint Table as builds of their own (`modded/` is left out of the library list).
- 37.12: no Supplementaries doormats: a doormat holds items, so the Library's and the Print Shop's jobs counted it as a
  chest (the furnished-workplace compat tests caught it). `moddedLibrary` only acts with `furnishedBuilds` on, so
  turning furnished builds off still gives the plain vanilla build everywhere, as the design note says.

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
- A Tom's network is read through its connector only at the Storehouse (design note M37, rule 4): a worker's own
  chests on a network stay separate stores, as at 2.0 (37.10).
- Lanes don't make review packages (sessions.md, 2026-10-04): the digest sends the design note as 37.1's package.
- The pack test's furnished builds (37.11) are a mode of their own (`FURNISHED=true`), not part of the plain pack boot:
  they need M37's gate open (`-Daliveworkplace.shots=true`, which opens every gate) and take minutes of building, and
  the plain boot checks the pack as players get it. The nightly workflow runs both. The order is set in the config
  file before the boot, as a server owner would, and the file is put back afterwards.

- 37.17: the Tom's versions of the storage builds come through the modded library rather than a style, so they follow
  `moddedLibrary` and `furnishedBuilds` like every other modded copy, and a player doesn't pick them: a server with Tom's
  builds them, one without builds the vanilla ones. The Warehouse joins its racks with one connector and Inventory Trim
  rather than a connector per rack, because two connectors on one cable network would each be read as the whole store.

## 7. Known limits

- The M34 workshops other than the Print Shop (the Tailor's and Jeweler's) and the other workplaces keep their vanilla
  furniture for now.
- Villagers look for a seat within 8 blocks of where they stand, not only in their own home, and walk to it for at
  most 30 seconds before choosing again.
- A vanilla picture is a plain wall banner (a block, so a builder can place it): an item frame or a painting would be
  an entity, which a furniture spot can't hold.
- The headless blueprint renderer (`tools/blueprints/render`, the architect skill's) can't draw Handcrafted,
  CobbleFurnies or Beautify pieces (they come out empty), so 37.4's renders reviewed the rooms in vanilla and the
  modded look is judged in the real client, in the `furnished_home` scene; Cozy Home's pieces (facing included, through the plan's
  block check) are checked by the pack test only.
- A building finished before 37.3 is repaired as the vanilla build.
- The generator finds only chairs, tables and beds by itself; couches, shelves, lamps and the rest are spots only where
  a drawing records them (the homes' rooms since 37.4; the workplaces with 37.5).
- Lucky's Cozy Home can't load in the dev runtime, so it is tested by the pack test only (`FURNISHED=true`).
- Tom's connector's range, cable limit and blocked-block list are its config's defaults (16, 2048, none): a server that
  changes them in Tom's config isn't followed (37.10). Tom's inventory proxies and remote links are not walked.
- Sophisticated shulker boxes are stores too (they have item storage), but 37.8 tests only the chests and barrels.
- The Storehouse finds what a controller links by walking block ids, not by asking the mod: a block the walk reaches
  that the controller hasn't linked (placed by a command with no placing) drops out of the store list without being
  read through the controller. Placed by a player or a builder, the two agree.

- The headless renderer draws Carved Wood and Rechiseled (the architect skill's `mods.mjs add carved-wood rechiseled`),
  but its render of a hanging Carved Wood lantern is flagged as standing on air, which the game's block doesn't do (it
  keeps `hanging=true`).

## 8. Proof

B433 (compat): `QaSpruceCottageBugTests.theSpruceCottageIsBuiltWholeWithItsLanterns` (the Spruce Starter Cottage
built from its exact materials, nothing skipped), failing before the fix.
B304: `TerraceSleepGameTests.aVillagerOfTheWestHomeClimbsItsLadderNotTheEastOnesAndSleeps` (his bed in the west
home, he stands by the east home's ladder at bedtime), `TerraceSleepGameTests.aVillagerWithoutABedTakesAFreeOneNextDoorAndSleeps`
(the east home's beds someone else's: he claims a west one and sleeps in it), both failing before the fix; with
Handcrafted (compat) `TerraceSleepCompatTests.aVillagerWithoutABedTakesAFreeFancyBedNextDoorAndSleeps`. Showcase scene
`terrace_sleep` (B310: it moves the villagers in a server task after the one placing the Terrace, since the placed beds
count as beds only once the tasks the placing queued have run; looked up in the same task, it found no beds and nobody
moved in).
B300: `FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece` (every furnished blueprint, vanilla and
with one-block tables), `FloatingFurnishingsGameTests.aPotOnALowerTableSitsOnIt`,
`FloatingFurnishingsGameTests.aBuiltFurnishedHomeHasNothingFloating`, `FloatingFurnishingsGameTests.aPieceLeftOnNothingIsSetDown`.
GameTests: `FurnishingConfigGameTests.furnitureModsKeepTheirOrderAndAreCleaned` (the default order, cleaning, an empty
and a missing list, the list off the screen and in the saved file), `FurnishingConfigGameTests.theM37GateHoldsItsSwitches`
(a player's file can't turn them on with the gate closed; with it open they obey the file), and
`ExpansionGateGameTests` (both switches in its gated lists). 37.2:
`FurnitureSpotGameTests.starterCottageSpotsAreWhereItsChairsAndTablesAre`,
`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`, `FurnitureSpotGameTests.aBrokenSpotsFileIsSkipped`,
`FurnitureSpotGameTests.aStyleCarriesTheSpotsOver`, `FurnitureSpotGameTests.eachMistakeInASpotsFileIsNamed`,
`FurnitureSpotGameTests.aStyleSwappingPlanksForNoPlanksKeepsTheWood`. 37.3 (no mods, a test table of vanilla blocks):
`FurnishingTableGameTests.withoutFurnitureModsTheBuildIsVanilla`,
`FurnishingTableGameTests.theFirstModWithAFittingPieceIsChosen`, `FurnishingTableGameTests.switchedOffTheBuildIsVanilla`,
`FurnishingTableGameTests.aSiteSavedMidBuildKeepsItsChoice`, `FurnishingTableGameTests.aRemovedModsPieceTurnsBackToVanilla`,
`FurnishingTableGameTests.eachMistakeInAFurnishingTableIsNamed`;
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
`SophisticatedControllerCompatTests.switchOffKeepsThreeStores`, `SophisticatedControllerCompatTests.theSophisticatedStyleAddsAController`,
`SophisticatedControllerCompatTests.aLinkToolLinkedToAnotherControllerIsntThisOnes` (a link tied to another controller
isn't this one's way on: the barrel behind it isn't linked, its planks counted once),
`SophisticatedControllerCompatTests.theSixteenthBarrelEachWayIsOutOfTheControllersReach` (rows of 16 barrels east, south
and up: the controller links each 15th, not a 16th, as the mod's own controller does) and
`SophisticatedControllerCompatTests.aBarrelBuiltBehindAToolLinkedLinkIsLinked` (a barrel a builder adds behind a
tool-linked link is counted through the controller, once);
B371: `SophisticatedControllerCompatTests.theRoomBehindAControllerHoldingGoodsIsTheRealRoom` (a controller over a
stack-upgraded barrel and a plain one: the board says 8640 empty and 8540 after 100 cobblestone, exactly 8540 more go in
through the controller and land in the barrels, then not one more);
QA, B371: `QaVoidStoreGameTests` (2): a store that takes items and holds none (a void upgrade) counts what it took, once,
and a chest behind it gets none of them;
QA, 37.8 and 37.9 (compat): `QaSophisticatedStorageCompatTests.qaAStackUpgradedSlotIsMeasuredInItemsAtTheStackEdge` (a
stack-upgraded slot holding exactly 64 still has room, 65 one less, full none; the board agrees each time),
`QaSophisticatedStorageCompatTests.qaTheLinkedCountFollowsBlocksAddedAndBroken` (the board's linked count goes 2, 3, 2,
"1 block" as barrels are built on and broken off),
`QaSophisticatedStorageCompatTests.qaABuilderClearingLeavesAControllerAndItsBarrel`,
`QaSophisticatedStorageCompatTests.qaAControllerSavedAndLoadedCountsOnce` and
`QaSophisticatedStorageCompatTests.qaABarrelJustBeyondTheControllersReachIsItsOwnStore` (a 16th barrel stays a store of
its own beside the controller, counted once);
no mods: `StyleGameTests.aNearRuleForAMissingBlockIsPassedOver` (a near rule swaps only the block beside its block, and one
whose block isn't installed falls through to the next rule, not to air).
Showcase scene `modded_storehouse` part one (filmed with `-Pfurniture=true`, which now brings Sophisticated Storage).

37.6 (no mods): `FurnitureSeatsGameTests.theSeatsTagLoadsWithoutTheFurnitureMods`; with Handcrafted (compat):
`FurnitureSeatsCompatTests.aVillagerSitsOnAHandcraftedChairAtSupperAndGetsUpAtDawn`,
`FurnitureSeatsCompatTests.aSeatInUseIsntTakenTwice`, `FurnitureSeatsCompatTests.aVillagerStandsUpWhenHurtAndNobodySitsWithTheSwitchOff`,
`FurnitureSeatsCompatTests.aSeatLoadedFromASaveLetsItsVillagerGo`,
`FurnitureSeatsCompatTests.aVillagerClaimsAndSleepsInAFancyBedAndTheHouseIsTheirHome` (the fancy bed is a villager home
point of interest, the villager claims and sleeps in it, the Starter Cottage it stands in is their home, and its chair
adds the furnished home's beauty). Scene `seats_and_beds` (`-Pfurniture=true`).

37.7 (compat, with Carved Wood and Rechiseled): `CarvedStylesCompatTests.theStylesUseCarvedWoodAndRechiseled` (each
swap, the Carved Wood lectern the Librarian's job site and barrel the Fisherman's, a hanging lantern still hanging and a standing waterlogged one standing; with
the switch off no Spruce style and Grand as before), `CarvedStylesCompatTests.aCarvedWoodChestIsASupplyChest` (a
builder builds from a Carved Wood chest; a Carved Wood barrel is listed and filled),
`CarvedStylesCompatTests.aSpruceLibraryHasCarvedShelvesAndALibrarian` (built with nothing skipped, every shelf Carved
Wood's, and a villager without a job takes the Carved Wood lectern and becomes the Librarian; a failure prints the
villager's path and what it stands on), B339: `CarvedStylesCompatTests.aVillagersPathGoesRoundACarvedWoodLanternOnADesk`
(a wall's one gap a block with a Carved Wood lantern on it: the path goes round; it went over the desk without the fix)
and `aVillagersPathStillCrossesACarpet` (a carpet in the gap is still walked),
`CarvedStylesCompatTests.theGrandStyleWithRechiseledBuildsWithNothingSkipped` (the Grand Starter Cottage). No mods:
`StyleGameTests.stylesLoadAndIdsRoundTrip` lists Spruce. QA (compat): `QaCarvedStylesCompatTests.aRuleToAMissingBlockFallsThroughAndDropsNothing`
(a rule to an unregistered block falls through to the next rule or leaves the vanilla block; a Library in such a style keeps
every block), `QaCarvedStylesCompatTests.aRuleUnderASwitchThatIsOffIsPassedOver`,
`QaCarvedStylesCompatTests.everySwapKeepsTheBlocksFacingAndChestHalves` (chest facing and halves, ladder and lectern
facing, every shared property across every Spruce and Grand blueprint),
`QaCarvedStylesCompatTests.aBuilderClearingLeavesACarvedWoodChestAndBarrelFull`,
`QaCarvedStylesCompatTests.aSavedSpruceSiteLoadsWithItsCarvedWoodBlocks`. B388 (compat):
`QaCarvedLibraryBugTests.theSpruceStyleKeepsTheCarpentersCraftingTable`, and
`QaCarvedLibraryCompatTests.everyStyleKeepsEveryJobSite` and `everyModdedCopyKeepsItsJobSites` (the Tom's storage
copies included) no longer pass over the crafting table; QA (compat):
`QaSpruceCarpenterCompatTests.aCarpenterIsHiredAtTheSpruceCottagesCraftingTable` (a builder builds the Spruce Starter
Cottage; its crafting table is the vanilla one and a Carpenter's job site, and a jobless villager by it, sneak-clicked
with planks, becomes its Carpenter). Known (B433): the Spruce cottage's two lanterns on the flower boxes by the door
are skipped. Scene `carved_styles` (`-Pfurniture=true`, which now brings
Carved Wood and Rechiseled).
37.10: with Tom's Simple Storage (compat): `TomsStorageCompatTests.aConnectorIsOneStoreForItsChestsAndCabinet` (two
chests and a filing cabinet on the connector and its cable count once, the board line, in and out through the
connector, the switch off), `TomsStorageCompatTests.aChestUnpluggedIsItsOwnStoreAgain`,
`TomsStorageCompatTests.aPorterDeliversIntoTheNetwork`, `TomsStorageCompatTests.theBuilderTakesWhatItNeedsThroughTheConnector`,
and `PackModsCompatTests.packStorageWorksAsSupplyChests` (a connector by a builder's chest still doesn't count it twice).
B192: `TomsStorageCompatTests.aPlayerOpensTheTerminalBesideTheStorehouse` (a player right-clicks the terminal next to
the Storehouse and gets Tom's terminal menu, listing 10 cobblestone, 20 planks and 1 sword, each once).
`TomsStorageCompatTests.aDoubleChestOnACableCountsOnce` (a double chest half on a cable connector isn't also its own
store) and `TomsStorageCompatTests.aConnectorInsideThePlotIsPassedOver` (a connector inside the area being built isn't
read; its chests outside are each a store). `TomsStorageCompatTests.aBlockHoldingNoItemsIsntJoined` (an enchanting
table on the connector isn't joined, and a chest touching only the table is its own store).
`TomsStorageCompatTests.aCableRunsOnThroughACableConnector` (a cable on a cable connector's side leads on to another
cable connector, whose chest the network joins: the connector stays the only store, its glass counted once) and
`TomsStorageCompatTests.theSixteenthBarrelIsOutOfTheConnectorsRange` (in a row of 16 barrels from the connector, it
joins the 15 within its 16-block range, as Tom's own connector does, and not the 16th).
`TomsStorageCompatTests.anotherConnectorIsntJoined` (a second inventory connector touching a network chest isn't one
of its inventories, and the Storehouse reads the shared chest through the network's connector only, B305).
B305: `TomsStorageCompatTests.twoConnectorsOnOneNetworkCountEachChestOnce` (a second connector on the same cables: the
store is one connector, each item counted once, the board links 3 blocks, a withdrawal of 12 planks leaves 8). Both classes check the store list is nearest first.
Showcase scene `modded_storehouse` part two (`-Pfurniture=true`, which also loads Tom's Simple Storage): the network,
the board's line and Tom's terminal screen.
37.11, the real pack (Cozy Home installed): `FURNISHED=true tools/packtest/run.sh` writes `furnishedBuilds` on and
`furnitureMods` as cozyhome, handcrafted, cobblefurnies, beautify into the server's config, boots Cobbleverse with M37's
gate open and runs `/workplace furnished` at full speed. `tools/packtest/furnished.py` passes it only when the Starter
Cottage and the Inn both finish with 0 skipped, 0 blocks not as planned, every furniture piece standing, the order in
effect starting with Cozy Home, and Cozy Home pieces in each (its verdict's own tests: `python3 tools/packtest/test_furnished.py`).
`FurnishedPackTestGameTests.theBarrelsHoldExactlyWhatThePlanCosts` (no mods: the command's barrels hold exactly the
plan's materials, none empty).
37.12 (no mods): `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`,
`ModdedLibraryGameTests.withoutTheModsEveryBuildIsTheVanillaBuild` (every home and workshop exactly its vanilla build,
the cottage planned with vanilla blocks only), `ModdedLibraryGameTests.theModdedChoiceIsSavedAndRead`,
`ModdedLibraryGameTests.theCopiesAreNotBlueprintsOfTheirOwn`, `ModdedLibraryGameTests.theSwitchIsReadFromTheConfig`; with
the mods (compat): `ModdedLibraryCompatTests.everyModdedCopyCanBeBuiltWithThePacksMods` (every block of every copy is a
real block with the pack's mods, the same size and shape as its vanilla build, its doors, beds, job blocks and furniture
spots unchanged, and nothing added that holds items), `ModdedLibraryCompatTests.theStarterCottageIsBuiltFromTheModdedLibrary`,
`ModdedLibraryCompatTests.switchedOffItIsTheVanillaLibrary`, `ModdedLibraryCompatTests.aSavedSiteKeepsItsLibrary`; the
furnished-workplace compat tests above now build the modded Supply Shop, Library and Print Shop (the modded library is
the default with the mods), each with nothing skipped and exactly the vanilla build's storage. Showcase scene
`modded_library` (`-Pfurniture=true`, which now brings Chipped and Supplementaries too): the Starter Cottage II from
both libraries side by side, and the modded Inn II. Renders of the copies: `tools/blueprints/modded_library.py --out
<dir> <name>`, then the Minecraft Architect skill's `render.mjs` with the mods' textures added (`mods.mjs add --jar`).
37.13: `FurnitureAnyModGameTests` (no mods; two test "mods" and a test equivalents file):
`anyModsPieceIsListedOnceWithTheOthersOnHover`, `theBuildersChairFollowsTheChest` (only the other piece, both, neither,
only the planned one, not furniture, the other order, a save and reload), `aBuiltChairAndTheStandInStayAsTheyAre`
(a chair already standing, the bag counts, no mod in the order, switched off), `theMaterialListCountsAnyModsPiece`,
`theEquivalentsFileIsReadAndCheckedWhenBroken`; with the mods (compat): `FurnitureAnyModCompatTests.onlyHandcraftedsChairInTheChest`,
`onlyCobbleFurniesChairInTheChest` (same facing, nothing else changed), `bothInTheChestTheFirstInTheOrderIsUsed` (one of
each too, and CobbleFurnies first), `neitherGivesTheStandInOrTheWait`, `theMaterialListNamesTheChairOnce` and
`aBuilderBuildsTheChairInTheChest` (a builder planned with Handcrafted's chair builds CobbleFurnies' from the chest,
facing south, and the finished building keeps it). Showcase scene `furniture_any_mod` (`-Pfurniture=true -Pcobblemon=true`).
37.17 (no mods): `StorageHallGameTests.theStorageBuildsAreAtTheTableAsVanillaBuilds`,
`StorageHallGameTests.withMilestone37ClosedTheyAreHidden`, `StorageHallGameTests.aBuilderBuildsTheTerminalNookAsAStorehouse`,
`StorageHallGameTests.aBuilderBuildsTheStorageHallAsAStorehouse`, `StorageHallGameTests.aBuilderBuildsTheWarehouseAsAStorehouse`
(each built from barrels of exactly its materials, nothing skipped, its Storehouse a job site and every chest and barrel
of it in the store); with Tom's (compat): `StorageHallsCompatTests.withTomsTheStorageBuildsUseTheirTomsCopies`,
`StorageHallsCompatTests.aBuilderBuildsTheTerminalNookOnTomsNetwork`, `StorageHallsCompatTests.aBuilderBuildsTheStorageHallOnTomsNetwork`,
`StorageHallsCompatTests.aBuilderBuildsTheWarehouseOnTomsNetwork` (nothing skipped, the connector joins 6, 29 and 60
inventories, the Storehouse reads the connector and counts each item once, the terminal opens Tom's menu listing them;
the Warehouse's crafting terminal is at (8, 1, 6), its crafting table at (10, 1, 6), B388).
Showcase scene `storage_hall` (`-Pfurniture=true -Pcobblemon=true`).
