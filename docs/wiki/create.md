# Create

Create integration brings the Create mod (the owner's Fabric port, in `libs/`) into village life: a Mechanic who keeps
the machines turning, villagers who feed Create's millstone, press, mixer and crushing wheels, Create-powered builds,
contraptions built from a blueprint, Create schematics at the Blueprint Table and trains between villages.
Part of 2.3, behind its gate (`Expansions.M39`). Built so far: the plan, the gate and the port in the test runtime
(39.1), the Mechanic (39.2), villagers working the machines (39.3), the Create-powered builds (39.4), contraptions
built from a blueprint (39.5), Create schematics at the Blueprint Table (39.6), trains between villages (39.7) and their
Conductor (39.7d) and six more Create builds (39.8); the full plan is the design note, docs/design/M39.md.

Roadmap items: 39.1, 39.2, 39.3, 39.4, 39.5, 39.6, 39.7, 39.7a, 39.7b, 39.7c, 39.7d, 39.8, B189, B193, B231, B243, B244, B483, B484, B486, B598

## What a player sees

Nothing yet: the gate is shut until 2.3 ships. Then, and only with Create installed:

- **The Mechanic** (39.2). A jobless villager next to a Create Toolbox (any of its sixteen colors) becomes the
  village's Mechanic, in a gray canvas work apron with a cogwheel stitched on it, brass goggles pushed up on the brow
  and a brass-headed wrench at the belt. When a shaft, gearbox, cogwheel or belt goes missing from a finished build
  within 32 blocks of the toolbox (measured to the build's nearest block, so a big build next to the toolbox counts
  however far off its corner lies, B598), the Mechanic fetches the part from the chest by the toolbox, carries it over and
  puts it back as the blueprint has it (the same axis and facing): shafts first, then the cogwheels on them, then
  belts. Without the part in the chest the hole waits, and the status over the Mechanic says what's needed ("needs
  Shaft to mend the Drive Line"). Between repairs the Mechanic walks a round of the village's millstones: what's been
  milled goes to the chest, and a millstone that turns and is empty gets up to 16 wheat from the chest. A millstone
  that doesn't turn is fed nothing ("the Millstone has stopped turning"). The Mechanic trades Create parts and tools
  (shafts, cogwheels, a wrench, goggles, belt connectors) for emeralds.

- **Villagers work the machines** (39.3). The village's own workers feed Create's machines near their workstation
  and bring what they make to the chest by it: the **farmer** carries wheat to a millstone and brings back wheat flour;
  the **armorer** lays iron or copper ingots on the depot under a mechanical press and brings back the sheets; the
  **chef** pours water into the basin under a mechanical mixer, adds wheat flour and takes out dough; the **miner**
  drops raw iron, copper or gold between two crushing wheels and gathers the crushed ore that falls out below. A
  machine is only fed while it turns ("the Millstone isn't turning" shows over the worker otherwise), and nothing is
  fed once the chest holds enough of what it makes (64 flour, sheets or crushed ore, 32 dough) or has no free slot
  ("the store is full: the Millstone waits"). A trip is short, and the job's own work picks up again after it. The
  Mechanic's round now covers the press, the mixer and the crushing wheels too. Only the village's own machines and
  chests are used (B243): a machine or a chest counts when it stands inside a building the village's builders
  finished (in a turned or flipped building, exactly the blocks its builders placed: one block past its walls, floor or
  roof is a player's; a building finished or taken down counts at once; one in another dimension doesn't count here,
  `QaVillageMachinesGameTests`). A player's own millstone, press, mixer or crushing wheels, and a player's chests by a toolbox or a
  workstation, are left alone: nothing of theirs is fed in, taken out or carried off.

- **Create-powered builds** (39.4). The Blueprint Table offers three new workplaces, only with Create installed: the
  **Watermill**, a stone-and-timber mill house whose water wheel on the west wall is turned by water falling from a
  header tank into a walled mill race, its shaft running through the wall to a gearbox and a millstone inside, with
  the farmer's composter and the store chest; the **Windmill**, a seven-square stone tower with a windmill bearing and
  24 sails on its front and a shaft down the middle to the millstone; and the **Workshop**, a brick shop whose water
  wheel drives a belt to a depot under a mechanical press (driven up a column of cogwheels from the belt's far
  pulley), with the armorer's blast furnace and the store chest. The water is placed last, so the wheels turn as
  soon as the builder is done; the builder starts the windmill's bearing the way a player does, with an empty hand.

- **More Create builds** (39.8). Six more workplaces at the Blueprint Table, again only with Create, each driven by a
  water wheel from a header tank like the Watermill's and turning as soon as it's built: the **Brass Foundry**, a brick
  hall under a deepslate roof whose wheel turns a belt out to a depot and, geared up from 8 to 32 RPM through two
  large-to-small cogwheel steps, a mechanical mixer over a basin and a blaze burner (the chef's mixer; a smoker as the
  job block); the **Sawmill**, a timber mill with a log store whose belt runs through two mechanical saws to a depot
  (fletching table); the **Crushing Mill**, an andesite-and-cobble mill whose two crushing wheels turn the opposite
  ways through a gearbox train, Create's crusher working in the gap under a feed belt, a pit and an item drain below
  (blast furnace); the **Greenhouse**, a glass house on a brick knee wall with a stepped glass roof on spruce
  rafters, farmland beds watered by two channels, and a belt from the wheel down the aisle to a depot, a little cart
  of andesite casing on it carrying a mechanical harvester and a mechanical plough (ready to be fixed to a
  contraption: Create's harvester and plough only work moving) (composter); the **Clock Tower**, a stone tower with a
  plastered belfry under a dark oak hip roof and a cuckoo clock in its front, turned by a shaft up the middle of the
  tower from the wheel at its foot (cartography table; purely for show); and the **Pump House**, a small brick house
  where the wheel's cogwheel turns a mechanical pump between an intake pipe over a four-source sump (it refills) and a
  two-high fluid tank (cauldron). `createBuilds` lists and hides all nine. Turned or flipped at the table before it's
  handed over, a Create build is built whole and works the same: its belts run the other way, the crushing wheels
  still turn into the gap. Switching `createBuilds` off while one is going up only takes them off the table: the build
  under way is finished and turns.

- **Contraptions from a blueprint** (39.5). Any blueprint with Create's moving parts in it (one of the three above, or
  a player's own machine saved with a Structure Block) is built in an order that keeps it whole:
  everything that holds it up first (floors, walls, frames, the water wheel), then the shafts and gearboxes, then the
  cogwheels, then the belts. Each part keeps its axis and facing, turned with the build; a belt keeps its length and
  finds its controller segment in the new place. A part the builder already put in that's broken before the build is
  done is put back before it's called done. About five seconds after the build is finished, its moving parts are
  checked and the owner reads, in chat: "Bob checked the Drive Line: all 8 moving parts turn." or "Bob checked the
  Drive Line: 1 part doesn't turn: Shaft at 12 64 -30." (up to four parts named, then "and 3 more"). With a Mechanic
  near the build the line names the Mechanic, who leads on contraptions.

- **Create schematics at the Blueprint Table** (39.6). A schematic a player uploaded with Create's Schematic Table is
  at the Blueprint Table the next time anyone opens it, under its file name, ready to take as a blueprint and hand to a
  builder; whoever opens the table reads a line for each new one: "Create schematic Riverside Kiosk.nbt (uploaded by
  Jesse): Imported aliveworkplace:create_schematics/jesse/riverside_kiosk (5 × 5 × 5)". A written Schematic used on
  the table imports its own file there and then (the same line), then the table opens. Blocks of mods the server
  doesn't have are counted and named, as for every import ("... 1 block from a mod that isn't installed became air:
  steamworks:brass_gauge."). A schematic uploaded again under the same name replaces its blueprint. A Schematic whose
  file is gone says so ("The schematic file hut.nbt isn't among Jesse's uploads on this server. Upload it again at a
  Schematic Table."), a blank one says to write it first, and one naming a file outside the uploads is refused.
  A Schematic written without its size (by a command or another mod) gets it from its file when it is used on the
  table, so holding it afterwards can't crash Create (B189).

- **Trains between villages** (39.7). The Blueprint Table offers a **Train Station** (13 x 9 x 8): a Create station
  on the edge of a platform beside a straight run of Create track, under a timber shelter with benches and lanterns.
  When two villages with a caravan route both have a Create station within their hall's reach (the Train Station's, or
  any a player placed) and a village's half of the road between them is built (27.17), one of its idle builders takes
  a "Railway" site and lays Create track down the middle of that half, from the village's store like any material;
  the chronicle reads "Bob laid the track along the road to Ashford", and when both halves are tracked, in both
  villages, "The railway to Ashford is open: caravans go by train". From then on a caravan between them goes by
  train: a third of the walk on the finished road (at least 400 ticks, never longer than walking), never lost on the
  way (31.20), and seen as steam and a bell at the station instead of a carter and llamas ("A train left for Ashford
  with 32 × Oak Log", "A train from Thornholm came in with ..."). Its goods, sale and unloading are a caravan's. If the
  station at the far end is broken while it's on its way, the caravan gets off at the end of the track and walks the
  rest ("The station was gone when the train from Thornholm came: its caravan walks the rest of the way"), arriving
  with all its goods a quarter of the walk later (never sooner than the train would have). With a station at one end
  only, an unfinished road, or the switch off, caravans walk as before.
- **Real Create trains** (39.7a). Once Create's railway knows both stations on one track network, the village whose
  hall comes first puts its own train together at its station, the way a player does: a small bogey on the track, a
  carriage of train casing with the caravan's chest, a seat between two train controls (one facing each way, so it can
  be driven both ways), all glued with super glue, and a jobless villager of the village in the seat as conductor
  ("Bob took the seat of the village's new train at the station: it runs to Ashford and back"). The station assembles
  it and the train gets a Create schedule: to the other village's station, wait 10 seconds, back, wait, round and
  round; Create drives it from then on. It is named "Thornholm - Ashford Caravan Train". A caravan on that line waits
  at the station until the train stands there, boards it (steam and the bell) and is unloaded the moment the train
  stands at the other village's station. A station the village points at its track faces the other village (Create's
  stations are stopped at from one side). Without a free villager, before the train is put together, or after a player
  breaks it up, caravans take the timed trip above.
- **One line, a built train, goods in its chest** (39.7b). The track a builder lays along a road follows
  `CaravanTrains.plan`: a straight piece (`xo`, `zo`) on each level node, an ascending piece (`an`, `as`, `ae`, `aw`) on
  the lower node of each one-block step, rising towards the higher one (its high end meets the next straight piece), and
  round each turn with three (or two) straight, level nodes either side a Create curve between the straight pieces that
  far from the turn, joined once the site is done (`CreateTrains.curve`, a `BezierConnection` as Create's own track
  placement makes one). A dip of one block, a step on a turn and a turn with no room stay gaps (the railway then never
  joins and caravans take the timed trip). The train's six blocks are a generated blueprint
  (`aliveworkplace:roads/<hall>/train_<other>`, "Village Train") at the first village's station, built by an idle
  builder of the village from the bench's chests; the bogey is placed with railway casing. When they all stand and the
  builder has finished the train's site, the station's next look glues and assembles them (B483: never while the site is
  still open, so its builder finishes and the chronicle says "Bob built the village's train at the station, ready to be
  put on the rails"; a builder killed before then takes the site with him, and the standing blocks are assembled all
  the same). At the departing station the caravan's goods go into the carriage's chest (Create's mounted storage, all or
  nothing; arrival `IN_CHEST`; no room: they ride as the caravan's record as before), and at the other station what is
  still in the chest of them comes out and is delivered. Train gone or the far station gone while they're in the chest:
  what a derailed train still holds comes back out and the caravan takes the timed trip; a train broken up keeps them
  in its chest (a player breaking it up at a station gets the chest back with them), the caravan is taken off the road
  and the chronicle says "The train to Ashford was broken up on the way: the caravan's goods stayed in its chest".
  Trains stopped altogether (`villageTrains` switched off, or Create taken out of the pack) while a caravan waited at the
  station, rode the train or had its goods in its chest (B359): at its hall's next round it takes the timed trip and
  arrives with its goods. With the switch off, goods in the chest come back out of it (a train no longer there keeps
  them, as above); with Create gone the train and its chest went with Create, so the caravan's own record of its goods
  is delivered.
- **The Conductor** (39.7d). A jobless villager next to a Create Train Station becomes the village's Conductor, in a
  maroon railwayman's cap with a gold braid band and badge, a maroon tunic with two rows of brass buttons, a gold watch
  chain, a ticket punch at the belt and black gloves. When the village puts its train together, the Conductor by its
  station takes the seat rather than a jobless villager ("Ada took the seat of the village's new train..."); a village
  without one seats a jobless villager as before, and a villager with another job is never taken. As the train comes
  in at a station, the Conductor riding it calls the stop once, over his head and in the chat of players within 24
  blocks of the station: "Ada: Now arriving at Ashford! All change, mind the gap." (a train driven by a jobless
  villager comes in quietly). Off the train he stands at his station like any villager at their workstation. He
  trades coal and emeralds for rails, Create track, powered rails, train schedules, track signals, railway casing and,
  as a Master, train controls.

- **Every station, every road, a drawn train** (39.7c). After its first pass, `CaravanTrains.plan` joins what is left
  unjoined: the plan's pieces make lines (straight pieces meeting level and in line, or a curve's two ends); short lines
  of one or two pieces between gaps (a zig-zag's straight bits) are taken out, and each gap gets one Create curve from
  the last piece of a line to the first of the next, leaving each along its own axis (a turn, an S-bend, a slope or all
  three: at most 20 blocks, two along for each block up, a turn a quarter circle with legs of one length, an S-bend
  with as much way along as across or half as much again, since in between Create's curve kinks at its ends and trains
  never pass it), the lines shortened by up to four pieces where it needs the room; a longer gap gets a straight piece
  about halfway along the way's main axis and two curves. So a one-block dip is a slope down and one up, a step on a
  turn one climbing curve, and a diagonal road one S-bend (or two). A Train Station off the road
  (`CaravanTrains.spur`, when the track site is done and before the stations are aimed) gets one curve from an end of
  its own straight track to the nearest straight piece of the road's that it can reach (32 blocks at most), joining it
  heading for the other village; a station on a spur faces the way out (`CreateTrains.aim`: the side whose track runs
  on rather than ending). The train is drawn: `aliveworkplace:train/village_train` (4 x 4 x 1, `village_train` in
  `tools/blueprints/create_builds.py`, render-checked), running east from its bogey and turned to the track past the
  station (`CreateTrains.parts`): bogey, railway casing, chest and casing below; controls facing out, the brown seat and
  controls facing back on the deck (Create's conductor sits between them), a spruce rail at the tail; spruce fence posts at both ends, a hanging lantern and a spruce
  slab canopy. The glue covers all of it. Its parts: three railway casings (the bogey is one), two controls, a seat, a
  chest, three spruce fences, four spruce slabs and a lantern.

## How it works

**Trains.** `city/CaravanTrains`, by block id only (`create:track_station`, `create:track` with its `shape` state,
read from the port's block states: `xo`, `zo`, `pd`, `nd`). Each hall's caravan round (`Caravans.round`) notes the
stations within `VillageHalls.RADIUS` from the loaded chunks' block entities (nearest first, at most 4) on the caravans'
list. The road tick (`Roads.tick`) opens, every 100 ticks, one track site at a time per village for a half that is due
(both villages have a station, the half is built, it isn't tracked yet), with any idle builder of the village (as for
a far road segment). Its blueprint is `aliveworkplace:roads/<hall>/track_<other>_<road key>`, so it is a road segment
to everything that treats them specially (not a building, not at the table, no blueprint handed back); a track piece
goes on each node of the half's way at feet height, straight or diagonal by its neighbors, but not where the road
steps a block (its stairs) or where something already stands (the hall it starts from). The finished site
(`Roads.segmentBuilt`) marks the half tracked. `Caravans.send` asks `CaravanTrains.rides` (switch, Create, roads on,
a station at both ends, the road finished, both halves tracked) and saves the answer on the shipment.

**Real trains (39.7a).** The only code that uses Create's classes is `compat/create/CreateTrains`, behind the extension
point `CaravanTrains.RAILWAY` (`work/Extension`; `compat/Compat` fills it when Create is loaded). The mod compiles
against the port in `libs/` (`tests.compat_compile_files`, modCompileOnly, with its nested Ponder and Porting Lib
jars); it is never a dependency. Every 10 ticks a hall's road tick calls `CaravanTrains.serve`: per partner, with no
village train it re-times waiting caravans to the timed trip and (every 100 ticks, the village whose hall key sorts
first, `rides` true) puts the train together: both stations aimed at a straight level track beside them or a step away
(their block entity's `TargetTrack` and `TargetDirection`, facing each other) and named after their villages, the two on
one `TrackGraph` (`Create.RAILWAYS.trackNetworks`), three straight clear track blocks past the station in its assembly
direction, a jobless adult villager within 24 blocks; then the station's enterAssemblyMode, assemble,
exitAssemblyMode, and the train's schedule runtime with two `DestinationInstruction`s and `ScheduledDelay`s, cyclic.
The train's owner is `CaravanTrains.railwayId` (a name-based UUID of the two halls), which is how the village finds its
own train and never touches a player's. A shipment's `arrives` holds `BOARDING` (`Long.MAX_VALUE`) while it waits and
`ABOARD` while it rides; the train standing at the village's nearest station (`Train.currentStation`) moves it on, and
at the other end sets `arrives` to now and unloads (`Caravans.trainIn`). Saved as before: nothing new in the save.


**Schematics.** Create keeps uploads in the game folder's `schematics/uploaded/<player>/<name>.nbt` (its
`CreatePaths`; here `MinecraftServer.getServerDirectory()`), vanilla structure files. `table/CreateSchematics.sync`
runs whenever a table opens (`TableServer.open`): every `.nbt` there that isn't a blueprint yet, or changed since,
goes through `BlueprintImporter.importAs` as `aliveworkplace:create_schematics/<player>/<name>` (names cleaned as
uploads are), replacing an older import of it, at most 16 at one opening. What was imported since the start is
remembered by the file's time; after a restart a file is current when its saved blueprint is at least as new. A file
that won't import is said once and tried again only when it changes. A written `create:schematic` used on the table
(`BlueprintTableBlock.useItemOn`, before the Blueprint unlock) is read by component id, `create:schematic_file` and
`create:schematic_owner` (strings, as the port's `SchematicItem.loadSchematic` reads them, checked in the jar), and
imports `uploaded/<owner>/<file>` the same way. After a good import, `CreateSchematics.complete` fills in whatever
of Create's Schematic Table components the item lacks (`schematic_deployed` false, `schematic_anchor` 0 0 0,
`schematic_rotation`/`schematic_mirror` none, `schematic_bounds` the blueprint's size), keeping those already set:
Create's client `SchematicHandler` reads the bounds of a written Schematic in hand every tick and crashed the game
("getBounds() is null") on an item with only its file and owner, as the showcase scene used to make one (B189).
`CreateSchematics.written` makes a whole one, as the port's `SchematicItem.create` does. As in Create, the file must end in `.nbt` and the path must stay in
the uploaded folder; we also refuse any name with a slash, a backslash, a colon or a leading dot. Files over the
importer's 8 MB limit are refused before they're read.

**The Mechanic.** The profession `aliveworkplace:mechanic` is registered at start-up only when Create is loaded, so
without Create there's no such job at all. Its workstation is a point of interest of ours, `aliveworkplace:toolbox`,
registered once Create has registered all sixteen `create:<colour>_toolbox` blocks (mods start in no fixed order, so
the mod waits for Create's blocks rather than assuming them). The toolbox is an optional entry in vanilla's
`acquirable_job_site` tag, which is how a jobless villager finds it by themselves. The work (`mechanic/MechanicWork`)
runs in the villager's WORK activity: every 5 seconds it looks for a missing part, and every 10 seconds between
repairs it sees to the next machine on the round. Missing parts are found the way builders find holes to mend: each
finished build near the toolbox is laid over its blueprint, and a drive-train block whose place is open (air, water,
plants) is missing. A build a builder is repairing is left to the builder. A belt's segments get their data back
(`Index`, `Length`, `IsController`, `Casing`), with `Controller` moved from where the blueprint was saved to where the
build stands, so each segment finds its controller; one belt connector is used per belt. The round lists the millstones
within 32 blocks, nearest first, and is looked up again each time it comes back to its start or a machine on it is
gone. A millstone "turns" at 1 RPM or more, read by name (`Speed`) from its saved data. Create's machines, belts and
toolboxes are no longer counted as anyone's supply chest (a millstone's wheat, a player's tools).

**Machine work.** Each machine is a data file, `data/aliveworkplace/machine_work/<id>.json` (`millstone`, `press`,
`mixer`, `crushing`), read on every load and `/reload`; a datapack can add or replace them. A file names the machine's
block, the job that works it, Create's recipe type (`create:milling`, `create:pressing`, `create:mixing`,
`create:crushing`), the feed and collect spots as offsets from the machine (the depot or basin two below the press or
mixer, the crushing wheels' gap, the floor under it), what the village wants made with how many to keep, the most
carried per trip (`batch`) and the speed that counts as turning (`min_speed`, compared with the machine's saved
`Speed`: 1 RPM for the millstone and press; 30 for the mixer, which Create only lets work from medium speed; 0.02 for
the crushing wheels, whose gap block saves the wheels' RPM divided by 50). Files for a mod that isn't loaded, or
naming a block, job or recipe type that doesn't exist, are skipped quietly. What a machine is fed is read from
Create's recipes of that type through the recipe manager: a recipe whose result the chest wants and whose ingredients
it holds, so a datapack that changes Create's recipes changes the village's too. The worker (`mechanic/MachineWork`)
looks every 5 seconds, only at machines and chests inside the outline of a finished build (`BuildSiteManager`'s
finished buildings laid over their blueprints' size, `mechanic/VillageMachines`, read again when one is added or
forgotten); the Mechanic's round lists only those machines too, and a trip saved before this rule to a machine outside
them brings its load back to the store (B243). A trip goes to the chest for the input, to the machine (anything
made there comes along) and back to the chest. A trip that can't go on any more ends at once, checked every tick by the
worker (`MachineWork.tickTrip`, B244): machine work switched off (`machineWork` or the Create expansion), Create gone,
or the worker no longer in the file's job (their workstation broken at Novice, or another job taken). What they
carried goes straight into the village's store by their workstation (or by where they stand, when it's gone); what
doesn't fit, or finds no store, drops at their feet. A broken machine, or a file gone, still runs the trip, which
brings the load back on foot: a worker whose machine is broken on the way turns back at once and shows "the Millstone
is gone: bringing the load back to the store" (the machine is named from its file, never from what now stands at its
spot, B506), and a Mechanic whose machine is broken while she walks to it says "the Millstone is gone" and goes on
with her round, and a worker killed mid-trip drops it where they fell. The chef's water is poured straight into the basin's tank (a bucket's
worth, 1000 mB), the way villages draw water anywhere; no bucket is used up. Items go in and out through Fabric's item
and fluid transfer, which Create's port exposes; a depot, basin or crusher is never anyone's supply chest. The
Mechanic's round uses the same files and the same work done on the spot.

**Contraptions.** The kinetic parts are a data file, `data/aliveworkplace/kinetic_parts/create.json` (`requires`
`create`): three groups of block ids, shafts, gearboxes, encased shafts, clutches and gearshifts; the cogwheels, plain
and encased; and the belt, plus the blocks whose data names a belt controller. A datapack can add an add-on's parts;
ids that don't exist are skipped. A build site made while `createBuilds` is on (and the parts are known) saves
`kinetic_order`, and its plan's structure list is sorted stably by group, so each group still goes layer by layer and
everything not in a group comes first (`BuildPlan.kineticOrdered`). The order is fixed when the site is made, so a
switch flipped mid-build never moves the saved cursor onto other steps; a site saved before 39.5 builds in today's
order. A crew's helpers stay in the lead's group (`BuildSite.ahead`, `Contraptions.notYet`). When the structure or
decoration list runs out and a kinetic part put in earlier is gone with its spot free, the lead goes back to the first
kinetic part and over them again (`Contraptions.goBack`, at most twice a build, counted in `kinetic_passes`); what
still stands is passed straight over. Block-entity data is made right for where the part is built
(`Contraptions.blockData`): a kinetic block's `Speed`, `Source`, `Network` and `NeedsSpeedUpdate` are never copied
(Create works them out again), and a belt segment gets its `Index`, `Length`, `IsController`, `Casing`, `Covered` and
`Dye`, with `Controller` moved by the build's offset and rotation from where the blueprint was saved (read off the
controller segment, whose `Controller` is its own position there). Without a controller segment in the blueprint, or
in a mirrored build (which turns a belt end for end), the place on the belt is left out and Create works it out from
the blocks, as for the Workshop. A belt is laid in one go (39.4), each segment with its data. A belt is no store, so
its data is copied (never the items on it). The data fix runs whenever the parts are known and the gate is open,
whatever `createBuilds` says: a belt copied with its old controller breaks itself. When a build in a contraption's
order is finished, the positions of its blocks with a `Speed` in their saved data are kept in a turning check
(`turn_checks`), looked at 100 ticks later; while something stands still it's looked at twice more, 100 ticks apart
(a wheel's water takes a moment to fall), then the parts that don't turn (`Speed` 0, or gone) are kept with the
finished building (`not_turning`) and the owner is told.

Everything goes by Create's ids alone: block and item ids, block state names, block entity data read by name (a
machine's `Speed`, a belt's length), recipe types such as `create:milling`, and the schematic item's data components.
The mod has no Create classes and compiles without it, so Create stays optional: it's in the mod's `suggests`, never
its `depends`.

In the tests, the port's jar goes into the compat test server's `mods/` folder, where Fabric Loader treats it as in a
real install: it remaps the jar and picks one version of each library the jar nests (it nests three versions of
Porting Lib's core). The other test mods are put on the classpath instead; that doesn't work for this jar, and a
library they nest isn't put there when the port nests a newer one (Sophisticated Core nests an old Porting Lib that
Create can't run on). The copy leaves out Create's and Porting Lib's own GameTests (they need their own test
structures), so the compat suite holds only ours, and it keeps vanilla's item stack methods that Porting Lib would
displace in our dev runtime, where they clash with owo-lib; a real server never has that clash.
The screenshot client loads the port with `-Pcreate=true` (`CREATE=true`, or a scene marked `create=True` in the
showcase catalog).

## Switches

- `mechanics` (39.2): the Mechanic job. Off: a jobless villager doesn't see a toolbox as a workstation, and
  Mechanics already hired stand idle. Off for players until 2.3 ships (the gate), and does nothing without Create.
- `machineWork` (39.3): villagers (and the Mechanic) feed Create's machines. Off: nobody feeds a machine; a trip
  already under way waits, saved, until it's on again. Off for players until 2.3 ships, and does nothing without Create.

- `createBuilds` (39.4, 39.5, 39.8): the Watermill, the Windmill, the Workshop and 39.8's six builds at the Blueprint Table and in the creative
  tab, and the contraption order and turning check for builds started while it's on. Off: they aren't offered, and new
  builds go up layer by layer, unchecked; a blueprint already handed out and a site already started still build, in the
  order the site was made with. Off for players until 2.3 ships, and does nothing without Create.

- `createSchematics` (39.6): the table lists the uploaded schematics and imports written ones. Off: the table leaves
  Create's schematics alone and a Schematic used on it just opens it; blueprints already imported stay. Off for
  players until 2.3 ships, and does nothing without Create.

- `villageTrains` (39.7): the Train Station at the table and in the creative tab, track laid along caravan roads,
  caravans by train, and the Conductor job (39.7d). Off: no station offered, no track site opened, a jobless villager
  doesn't see a station as a workstation (Conductors already hired keep the job), new caravans walk; a train already on its way
  still comes in, and stations and track already noted stay on the list. Off for players until 2.3 ships, and does
  nothing without Create (or with `caravanRoads` off: no road, no track).

## Saved data

- `mechanic_round`, an attachment on a Mechanic (39.2): the machines on their round and the next one to visit. Both
  fields are optional; a villager without it starts a fresh round. A villager saved as a Mechanic in a world Create
  was later removed from loads as a jobless villager (vanilla's own fallback for an unknown profession).

- `machine_job`, an attachment on a worker (39.3): a trip to a machine under way: the machine's position, its file,
  the stage (`fetch`, `feed`, `return`) and what's carried. Every field is optional; without it the worker has no trip.
  A trip saved mid-way goes on after a load, so nothing carried is lost. In a world opened again after Create was taken
  out of the pack the trip can't go on: what was carried goes back into the store by the workstation, or drops at the
  worker's feet when there's none, and the record is cleared. (The design note's per-dimension `machines`
  list wasn't needed: workers find machines near their workstation in loaded chunks.)

- On a build site (39.5): `kinetic_order` (false when absent: a site saved before 39.5 builds in today's order) and
  `kinetic_passes` (0). In the dimension's build sites: `turn_checks` (none when absent), each with the build, its
  placement, owner, the builder's name, the parts' positions, when it's due and how often it has looked; and on a
  finished building's entry `not_turning` (absent: not checked; empty: everything turned). The design note put
  `notTurning` on the site; the site is gone once the build is done, so it lives with the finished building.

- On the caravans' list (`aliveworkplace_caravans`, 39.7): each village's `stations` (positions) and `tracks` (the
  villages it has laid track towards), both absent in older saves (none), and a shipment's `byTrain` (absent: false,
  it walks). A village taken off the list takes its stations and the track towards it with it.

Nothing is saved for schematics (39.6): imports are ordinary blueprints in the world's generated structures, and what's
been imported is found again from the files' times.

The planned fields of later items, each with its default, are listed in the design note.

## Items, blocks, jobs, commands

- Job: the **Mechanic** (`aliveworkplace:mechanic`, only with Create), at a Create Toolbox of any color (POI
  `aliveworkplace:toolbox`). In the Village Hall's list a Mechanic shows with the toolbox's own item, as every job shows
  with its workstation.

- Job: the **Conductor** (`aliveworkplace:conductor`, only with Create, 39.7d), at Create's Train Station (POI
  `aliveworkplace:track_station`, registered once Create has registered `create:track_station`, and an optional entry
  of the `acquirable_job_site` tag). No new block: the station the village already has is his workstation, as the
  Toolbox is the Mechanic's. He works his workstation the vanilla way (no work package of ours); the seat is his
  through `CaravanTrains.conductorFor` (the nearest grown-up Conductor within 24 blocks of the station not already
  riding, else the nearest jobless villager as before). The stop is called from `CaravanTrains.serve` when a train it
  saw away from the station stands there (`CaravanTrains.calls`, kept in memory only: a train first seen standing, as
  after a restart, is not called). Riders are found by id: Create may save a carriage's riders and put them back as
  new entities on the way. Trades in `ModTrades.conductorTrades`, Create's items looked up by id when offered.
  Nothing new in the save; a villager saved as a Conductor in a world Create was removed from loads jobless.
- **Taking Create off a server** (B542). A villager who was a Mechanic or Conductor loads as a jobless Novice: her
  name and clothes stay, her old trades, level and xp go, so a right click gets a shake of the head as from any jobless
  villager, and she takes a new job by herself (`ModVillagers.forgetVanishedJob`, after vanilla reads the save; any job
  whose mod is gone, the Furnicrafter without CobbleFurnies too). A villager whose job still exists is left as she was.

- Blueprints (39.6): `aliveworkplace:create_schematics/<player>/<name>`, one for each uploaded schematic.

- Blueprint (39.7): `aliveworkplace:train_station` (13 x 9 x 8), drawn in `tools/blueprints/create_builds.py`,
  listed only with Create and `villageTrains` (`city/CaravanTrains.listed`). Track sites are named "Railway".

- Blueprints (39.4): `aliveworkplace:watermill` (11 x 11 x 9), `aliveworkplace:windmill` (9 x 17 x 9) and
  `aliveworkplace:workshop` (14 x 10 x 9), drawn in `tools/blueprints/create_builds.py`, unlocked from the start and
  listed only with Create (`mechanic/CreateBuilds`).

No items or blocks of our own. The ids the milestone reads from Create (the machines, the drive-train parts, the sails, the
toolboxes, the schematic items and components, the recipe types) are listed in the design note and checked by
`CreateCompatTests`. The Mechanic's job block will be Create's Toolbox, in any of its sixteen colors.

## Decisions

- The Mechanic's job block is Create's Toolbox: a mechanic's thing, cheap early, and never a machine, so placing
  machines never makes job sites by accident (39.1, design note M39).
- The belt's item is `create:belt_connector`; `create:belt` is only the placed block. The roadmap's list said
  `belt` (39.1).
- The farmer, armorer, chef and miner work the machines that make their goods; there's no new Miller job (39.1).
- What a machine makes is read from Create's own recipes, never our own list (39.1).
- The windmill is started the way a player starts it, an empty-hand use of its bearing, by a fake player; a sign
  tells the player how if that fails (39.1, for 39.4).
- The Mechanic waits for all sixteen toolbox colors before registering its workstation, and its profession exists
  only with Create; a hired Mechanic stays one when the switch is off, standing idle (39.2).
- The Mechanic mends only the village's own finished builds near the toolbox, as written in the design note; the
  owner's question about players' own factories is still open (39.2).
- A millstone is only fed while it turns, so wheat never sits in a dead mill; emptying happens either way (39.2).
- The "job icon" is the Toolbox's own item, which the Village Hall already shows for each job's workstation; the
  Mechanic's look is the outfit (39.2).
- The four machines are worked by the jobs that make those goods (farmer, armorer, chef, miner), each from their own
  workstation's chest, within 32 blocks of it (39.3).
- The chef's mixer water is poured into the basin's tank for free rather than from a bucket in the chest, so a
  kitchen needs no bucket supply; the dough recipe takes a bucket's worth (39.3).
- A worker looks for machine work every 5 seconds whatever their job's own work is doing (most jobs' own work never
  ends, a smelter waits at the furnace all day), and their job's own work waits while a trip is on and picks up after
  it (39.3).
- The windmill's bearing is started through vanilla's block use with an empty hand by the loader's stand-in player
  (`Platform.fakePlayer`), as the design note says; no Create class is used (39.4).
- The three builds start unlocked: they're workplaces for jobs a village already has, and they're hidden without
  Create anyway (39.4).
- The Workshop's belt is placed by its block states alone, and a builder lays it in one go (a segment alone breaks
  itself); Create rebuilds the belt's controller and length from its blocks. Each segment costs a belt connector
  (39.4), and still does after 39.5: Create's own count (a connector for the belt, a shaft for each pulley, nothing for
  the middle) would make the middle segments free, and a block that costs nothing is never placed by a plan.
- Contraptions (39.5): the order lives in a data file, by block id, not in Create's classes; the water wheel, machines
  and anything not listed are "what holds it up" and go first. The order is chosen when the site is made. The turning
  check is a line in chat for the owner (the design note's "build's tooltip": a finished build has no tooltip of its
  own), kept with the finished building. "Leading the crew" means the Mechanic near the build is the one who checks
  it and says so (`MechanicWork.crewLead`); the builders keep their own order.
- A started windmill's sails leave the world as blocks (the bearing carries them), so its finished-build check leaves
  out the sail plane (39.4).
- The port's jar runs from the test server's `mods/` folder, without its own GameTests; Porting Lib's clash with
  owo-lib, which happens only in the Mojang-named dev runtime, is fixed in the test copy, not in the mod (39.1).
- Schematics (39.6): "the table lists the files" means each file becomes a blueprint when a table opens, so it has a
  preview, materials and styles like any build, rather than a second kind of entry. Create's own upload rules already
  decided who may upload, so the Table's `workplaceAllowUploads` rule doesn't apply; the `createSchematics` switch is
  the admin's say. Imports are server-wide, as uploads at the table are. A deleted file's blueprint stays (a build
  under way may use it).

- Trains (39.7a, the owner, 2026-10-18): real Create trains, assembled at a station and run on a Create schedule. The
  caravan's goods stay the caravan's record while it rides (boarding when the train stands at its station, unloaded
  when it stands at the other), and the train's chest is the caravan's trunk, not a store the goods are moved into:
  Create's mounted storage would need much more of its API and a train broken up mid-trip would spill them. The
  timed trip (39.7) stays as the fallback.

## Known limits

Nothing is built for players yet (the gate). Only one machine of each kind per trip, and a worker
looks only near their own workstation; a stopped machine shows on the Mechanic's status, not yet in the chronicle.
Putting back a belt has no test with a real belt yet (shafts and cogwheels do). A part broken after a contraption is
finished is the Mechanic's to mend, not the builder's; one broken while the ground is leveled is listed by the check.
Trains (39.7): the track is
straight pieces node by node, so at a bend or a step of the road Create sees separate pieces, not one line a real
Create train could run along; the Train Station's station isn't bound to its track (a player re-places it to make a
real stop); track broken after it's laid isn't noticed (the caravans still ride); and no spur is laid from the
station to the road. A windmill whose bearing can't be started (fewer than 8 sails left, the sails
blocked) gets no sign yet: the player starts it with an empty hand (39.4). A schematic still being uploaded when a
table opens may be read cut short and said so once; it's imported when the upload finishes and the table opens again
(39.6). Whether Create's client keeps a written Schematic's right-click on the table for itself in a real game is not
tested (the GameTests use the table as the server does).

## Proof

- B231 (client, with Create): the `create_builds` scene writes one quad vertex by vertex into Ponder's
  `ShadedBlockSbbBuilder` (as Sodium does with shaders on, when Flywheel is off) and checks the mesh holds it;
  `client/mixin/CreateShadedSbbBuilderMixin` hands each per-vertex call to the builder's own `BufferBuilder` (named
  by both Mojang and intermediary names, `@Pseudo`, so nothing happens without Create).
- `CreateBuildsCompatTests` (39.4, with Create): `theCreateBuildsAreInTheTable`, and
  `aBuilderBuildsTheWatermillAndItTurns`, `aBuilderBuildsTheWindmillAndStartsIt` and
  `aBuilderBuildsTheWorkshopAndItTurns`: built from barrels holding exactly their materials, nothing skipped, the job
  block a job site, and the millstone or press turning (its `Speed` read from its block entity data by name).
- `CreateBuildsCompatTests` (39.8, with Create): `aBuilderBuildsTheBrassFoundryAndItTurns` (the mixer at 30 RPM or
  more), `aBuilderBuildsTheSawmillAndItTurns` (both saws), `aBuilderBuildsTheCrushingMillAndItTurns` (the wheels the
  opposite ways, the crusher valid), `aBuilderBuildsTheGreenhouseAndItTurns` (both belt pulleys; farmland, wheat, the
  harvester and plough on their cart), `aBuilderBuildsTheClockTowerAndItTurns` (the cuckoo clock) and
  `aBuilderBuildsThePumpHouseAndItTurns` (the pump; pipes and both tank blocks in place): each built from exact
  materials, nothing skipped, the job block a job site, the machine's `Speed` read by name. Scene `create_builds_more`:
  the six set down in two rows, every machine turning. Renders: `tools/blueprints/render/preview.sh greenhouse ...`.
- `QaCreateBuildsTurnedCompatTests` (39.4, 39.5, 39.8, with Create; lane E's QA): the Workshop flipped and turned round,
  the Watermill, the Sawmill and the Crushing Mill flipped, the Brass Foundry and the Pump House turned a quarter
  (either way): each built from exact materials, nothing skipped, the job block a job site, its machines turning, and
  every belt segment naming a controller segment of its own belt, numbered 0 to its length once each; the flipped
  Crushing Mill's crusher valid, the turned Foundry's mixer at 30 RPM or more; and a Watermill finished and turning
  after `createBuilds` went off half way up its walls. `QaCreateSchematicFilesCompatTests` (39.6): a schematic seen cut
  short mid-upload is imported, and said, once the upload is complete; a written Schematic of a file cut short says so,
  naming it, and makes no blueprint. `QaMechanicMendSaysCompatTests` (39.2): "Mechanic: fetching Shaft for the ..."
  and "Mechanic: putting back a Shaft in the ..." while a broken shaft goes back. `QaCreateItemsHeldCompatTests` (lane E's QA): a depot, a
  deployer and a basin, each holding a diamond, scanned into a blueprint and built from exactly its material list, are
  built holding nothing. `QaCreateFluidsCompatTests`, `QaCreateDrainFluidsCompatTests` and
  `QaCreateSchematicFluidsCompatTests` (B486): a fluid tank, spout, basin and item drain holding lava, scanned or
  uploaded as a schematic, are built empty. `QaCreateTankKeptCompatTests` (lane E's QA of B486): a two-tall tank of lava
  is built empty but still one tall tank (it takes more than one tank block holds), and a player's bucket of lava in a
  built tank stays when the same blueprint is built over it again. `QaMachineGoneSaveCompatTests` (lane E's QA of
  B506): a trip saved on the farmer and loaded again (a restart) keeps its machine, file, stage and load, and when the
  millstone is then broken reads "the Millstone is gone: bringing the load back to the store" and returns all 8 wheat;
  a Mechanic says "the Millstone is gone".
  QA's `QaBuilderFixes1027CompatTests` (1, B486): a two-block-tall tank
  holding two buckets of Create's honey and a spout holding a bucket of water, scanned and built turned a quarter and
  mirrored from exactly the material list, go up whole and empty.
  `QaCreateStoresHeldCompatTests` (lane E's QA): the same for a millstone (holding wheat), a chute, an item vault, a
  mechanical crafter and a toolbox.
- `CreateBuildsGameTests` (39.4, 39.8, without Create): `hiddenWithoutCreate` and `theSwitchHoldsThemBack`; (39.6)
  `noSchematicsWithoutCreate`: a file in the uploaded folder stays out of the library when a table opens.
- `CreateSchematicCompatTests` (39.6, with Create): `anUploadedSchematicAppearsAtTheTable` (opened with an empty hand,
  listed and built as drawn, not imported again), `aWrittenSchematicImportsItsFile` (the item used on the table; the
  line names the file, the uploader and the unknown block), `aMissingFileSaysSo` (and a blank Schematic; every line
  has text), `namesOutsideTheUploadsAreRefused` (eight names with `..`, slashes, backslashes, an absolute path or
  another extension), `anUploadAgainReplacesItsBlueprint` (no `_2`; a broken file said once) and
  `theSwitchOffLeavesThemAlone`. Scene `create_schematic`: the written Schematic used on the table, then a builder puts
  the kiosk up.
- `VillageTrainCompatTests` (39.7, with Create; two villages 140 blocks apart):
  `aBuilderLaysTrackAlongTheRoadBetweenTwoStations` (the road between them laid north of the area; no track due
  before the stations; the idle builder takes the site and lays it from a barrel, straight track down the road,
  nothing skipped; the railway opens in both chronicles). The caravan tests put the two villages up in the test's own
  column (100 and 240 blocks over its floor, the road noted finished and tracked), out of reach of other tests'
  storehouses, which in the full run stand within a hall's reach and can take a caravan's goods: `aCaravanBetweenTwoStationsGoesByTrainAndArrivesWithItsGoods` (a third of the walk, on time, 32 logs in
  the other chest), `withoutStationsCaravansWalkAsToday` (and with one station), `withVillageTrainsOffCaravansWalkAndNoTrackIsLaid`,
  `aTrainSavedAndReloadedMidTripStillArrives` and `aStationBrokenMidTripTheCaravanWalksTheRest`; and
  `aBuilderBuildsTheTrainStation` (exact materials, nothing skipped, the station beside the track). Without Create,
  `VillageTrainGameTests`: `noTrainsWithoutCreate` and `trainsAreSavedAndOldSavesWalk`. Scene `village_train`.
- `CreateCompatTests`: `createIsLoaded` (Create and its libraries loaded, its own GameTests left out),
  `createBlockIdsExist`, `createItemIdsExist` and `createRecipeTypesExist` list every id the milestone reads and fail
  naming any that's missing.
- `ExpansionGateGameTests.createIntegrationIsGated`: the gate is shut for players and open in GameTests, for each of
  the five switches.
- `MechanicCompatTests` (with Create): a jobless villager takes a red toolbox by themselves, and every color is the
  job's block; a shaft and a cogwheel gone from a finished build go back from its blueprint, shaft first, with the
  chest's parts (and the toolbox isn't a supply chest); without the part the hole waits and the Mechanic says what's
  needed; a millstone turned by a creative motor gets the chest's wheat and its flour comes back, one that doesn't
  turn gets nothing; `mechanics` off, the toolbox is hidden from the jobless and nothing is mended; the round and the
  job survive a save and load.
- `MechanicGameTests` (without Create): no profession, no POI, no crew lead; a saved Mechanic loads jobless; the saved
  round's defaults; the `mechanics` switch.
- `ConductorCompatTests` (39.7d, with Create): a jobless villager takes a Train Station by themselves (and the POI is
  the station's); `villageTrains` off, the station is hidden from the jobless and nobody takes it; the trades at every
  level, Create's track, schedule, signal and controls among them; two villages joined by track: the Conductor (further
  from the station than a jobless villager and a Librarian) rides the train and calls the stop at the other village,
  naming it; without a Conductor the jobless villager rides it and nobody calls. `ConductorGameTests` (without Create):
  no profession, no POI; a saved Conductor loads jobless; the seat goes to the nearest jobless grown-up, never to a
  villager with a job or a baby. Scene `village_train` (the Conductor rides and calls the stop).
  `QaConductorCompatTests` (QA, from the guide): two jobless villagers by one station, one Conductor, still one 600
  ticks later; a hired Conductor keeps the job for 1200 ticks with `villageTrains` off; a Conductor saved and loaded
  keeps the job and the same trades; the trades by rank (Novices buy coal and sell rails, no schedule, signal, casing or
  controls at Novice, controls only at Master, powered rails and casing sold); the stop called once while the train
  stands there, and the chronicle naming the Conductor who took the seat.
- `QaVillageTrainRulesCompatTests` (QA, from the guide, with Create): the train trip is a third of the walk, never under
  `TRAIN_MIN` (400 ticks) nor longer than the walk, both sides of each boundary; halves of the road `HALVES_MEET` apart
  make a railway, one block more or a half stopped at a milestone don't; the railway over a save, and a save with one
  village's station and track gone loads as none.
- `QaTrainSiteRaceCompatTests` (QA, 39.7b, with Create): with both halls looking at their trains every tick, the
  builder's train site is finished before the station assembles the train, the chronicle says a builder built it, and
  no second set of its blocks is put up.
- `QaTrainSiteHeldCompatTests` (QA, B483, with Create): the train's blocks standing with the builder's site still open,
  the sites and the builder saved and loaded: the station still waits, then the builder finishes, the chronicle says so
  and the train is assembled with no site left open; the builder killed in that gap: the standing blocks become the
  train, with no second train site.
- `QaTrainsOffCompatTests` (QA, B359, with Create): `villageTrains` switched off while the real village train runs with
  the caravan's 32 logs in its chest: they come out of the chest, the caravan takes the timed trip and comes in, and
  Ashford's 64 logs always add up (none left in the train, none twice); a world saved with a caravan waiting to board and
  one in the chest, opened with the switch off and no train on the line: the first comes in with its 16 logs, the other is
  written off with one "broken up" line and its logs never arrive.

- `MachineWorkCompatTests` (with Create): for each of the millstone, the press over a depot, the mixer over a basin
  and the crushing wheels, with the job's own worker: turned by a creative motor, the chest's input goes to the machine
  and what it makes comes back to the chest; standing still, nothing is taken; a full chest, nothing is taken; a worker
  saved and loaded mid-trip with the input in hand finishes the job; `machineWork` off, nothing is fed.
- `QaMachineTripCompatTests` (QA, B244, with Create): a miner carrying 10 raw iron to the crushing wheels when
  `machineWork` is switched off puts them back in the store, and after a save and load with the switch on again the iron
  still comes to 10; a Novice armorer who loses the job mid-trip puts the 8 iron back, and killed afterwards drops none.
- `QaCreateGoneGameTests` (without Create, a world Create was taken out of): a farmer saved mid-trip with 12 wheat
  gives all 12 back after a real save and load; a Watermill site started with Create is finished (or let go) by its builder, never
  left standing. `QaCreateCompatTests` and `QaMechanicSaysCompatTests` (with Create): the lines over the worker and the
  Mechanic, as a player reads them: "Machine work: the Millstone isn't turning", "Machine work: the store is full: the
  Millstone waits", "Mechanic: every machine is in order", "Mechanic: seeing to the Millstone" and "Mechanic: the
  Millstone has stopped turning".
- `SavedDataGameTests`: `machine_job` survives a save and load; `ConfigGameTests` and `ExpansionGateGameTests`: the
  `machineWork` switch on the screen and behind the gate.

- `ContraptionCompatTests` (39.5, with Create): a contraption (a water wheel under a header tank, two shafts, a
  three-block belt, two cogwheels) built and running, saved with a Structure Block's own call, taken away and built
  turned a quarter elsewhere from barrels holding exactly its materials: every block as drawn, nothing skipped, each
  group after the one before it, the belt's end naming its new controller, no running data copied, and the check finds
  all of it turning; a cogwheel broken right after it went in is put back (one pass back); a save and reload of the
  sites and the builder among the kinetic parts finishes it in order; a shaft joined to nothing is the one part
  listed, with the line the owner reads; `createBuilds` off when the build starts, it goes layer by layer and isn't
  checked.
- `CreateBuildsGameTests.noContraptionsWithoutCreate` (without Create): no parts, no order, nothing checked; the new
  saved fields' defaults and round trips; the lines' text.

Showcase scenes: `mechanic`, `create_mill`, `create_builds`, `create_contraption` (all filmed with Create).
