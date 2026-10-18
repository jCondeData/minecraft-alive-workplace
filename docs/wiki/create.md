# Create

Create integration brings the Create mod (the owner's Fabric port, in `libs/`) into village life: a Mechanic who keeps
the machines turning, villagers who feed Create's millstone, press, mixer and crushing wheels, Create-powered builds,
contraptions built from a blueprint, Create schematics at the Blueprint Table and, later, trains between villages.
Part of 2.3, behind its gate (`Expansions.M39`). Built so far: the plan, the gate and the port in the test runtime
(39.1), the Mechanic (39.2) and villagers working the machines (39.3); the full plan is the design note, docs/design/M39.md.

Roadmap items: 39.1, 39.2, 39.3

## What a player sees

Nothing yet: the gate is shut until 2.3 ships. Then, and only with Create installed:

- **The Mechanic** (39.2). A jobless villager next to a Create Toolbox (any of its sixteen colours) becomes the
  village's Mechanic, in a grey canvas work apron with a cogwheel stitched on it, brass goggles pushed up on the brow
  and a brass-headed wrench at the belt. When a shaft, gearbox, cogwheel or belt goes missing from a finished build
  within 32 blocks of the toolbox, the Mechanic fetches the part from the chest by the toolbox, carries it over and
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
  Mechanic's round now covers the press, the mixer and the crushing wheels too.

Later in 2.3, the
Blueprint Table offers a Watermill, a Windmill and a Workshop, and schematics uploaded with Create's Schematic Table
show up at the table.

## How it works

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
looks every 5 seconds; a trip goes to the chest for the input, to the machine (anything
made there comes along) and back to the chest. The chef's water is poured straight into the basin's tank (a bucket's
worth, 1000 mB), the way villages draw water anywhere; no bucket is used up. Items go in and out through Fabric's item
and fluid transfer, which Create's port exposes; a depot, basin or crusher is never anyone's supply chest. The
Mechanic's round uses the same files and the same work done on the spot.

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

createBuilds, createSchematics and villageTrains are mapped to the gate and come into the config with 39.4
to 39.7.

## Saved data

- `mechanic_round`, an attachment on a Mechanic (39.2): the machines on their round and the next one to visit. Both
  fields are optional; a villager without it starts a fresh round. A villager saved as a Mechanic in a world Create
  was later removed from loads as a jobless villager (vanilla's own fallback for an unknown profession).

- `machine_job`, an attachment on a worker (39.3): a trip to a machine under way: the machine's position, its file,
  the stage (`fetch`, `feed`, `return`) and what's carried. Every field is optional; without it the worker has no trip.
  A trip saved mid-way goes on after a load, so nothing carried is lost. (The design note's per-dimension `machines`
  list wasn't needed: workers find machines near their workstation in loaded chunks.)

The planned fields of later items, each with its default, are listed in the design note.

## Items, blocks, jobs, commands

- Job: the **Mechanic** (`aliveworkplace:mechanic`, only with Create), at a Create Toolbox of any colour (POI
  `aliveworkplace:toolbox`). In the Village Hall's list a Mechanic shows with the toolbox's own item, as every job shows
  with its workstation.

No items or blocks of our own. The ids the milestone reads from Create (the machines, the drive-train parts, the sails, the
toolboxes, the schematic items and components, the recipe types) are listed in the design note and checked by
`CreateCompatTests`. The Mechanic's job block will be Create's Toolbox, in any of its sixteen colours.

## Decisions

- The Mechanic's job block is Create's Toolbox: a mechanic's thing, cheap early, and never a machine, so placing
  machines never makes job sites by accident (39.1, design note M39).
- The belt's item is `create:belt_connector`; `create:belt` is only the placed block. The roadmap's list said
  `belt` (39.1).
- The farmer, armorer, chef and miner work the machines that make their goods; there's no new Miller job (39.1).
- What a machine makes is read from Create's own recipes, never our own list (39.1).
- The windmill is started the way a player starts it, an empty-hand use of its bearing, by a fake player; a sign
  tells the player how if that fails (39.1, for 39.4).
- The Mechanic waits for all sixteen toolbox colours before registering its workstation, and its profession exists
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
- The port's jar runs from the test server's `mods/` folder, without its own GameTests; Porting Lib's clash with
  owo-lib, which happens only in the Mojang-named dev runtime, is fixed in the test copy, not in the mod (39.1).

## Known limits

Nothing is built for players yet (the gate). Only one machine of each kind per trip, and a worker
looks only near their own workstation; a stopped machine shows on the Mechanic's status, not yet in the chronicle.
Putting back a belt has no test with a real belt yet (shafts and cogwheels do). Leading the crew on a contraption is
39.5's: `MechanicWork.crewLead` finds the Mechanic for a site, and nothing calls it yet. Trains (39.7) wait on the
owner's answer in the design note.

## Proof

- `CreateCompatTests`: `createIsLoaded` (Create and its libraries loaded, its own GameTests left out),
  `createBlockIdsExist`, `createItemIdsExist` and `createRecipeTypesExist` list every id the milestone reads and fail
  naming any that's missing.
- `ExpansionGateGameTests.createIntegrationIsGated`: the gate is shut for players and open in GameTests, for each of
  the five switches.
- `MechanicCompatTests` (with Create): a jobless villager takes a red toolbox by themselves, and every colour is the
  job's block; a shaft and a cogwheel gone from a finished build go back from its blueprint, shaft first, with the
  chest's parts (and the toolbox isn't a supply chest); without the part the hole waits and the Mechanic says what's
  needed; a millstone turned by a creative motor gets the chest's wheat and its flour comes back, one that doesn't
  turn gets nothing; `mechanics` off, the toolbox is hidden from the jobless and nothing is mended; the round and the
  job survive a save and load.
- `MechanicGameTests` (without Create): no profession, no POI, no crew lead; a saved Mechanic loads jobless; the saved
  round's defaults; the `mechanics` switch.

- `MachineWorkCompatTests` (with Create): for each of the millstone, the press over a depot, the mixer over a basin
  and the crushing wheels, with the job's own worker: turned by a creative motor, the chest's input goes to the machine
  and what it makes comes back to the chest; standing still, nothing is taken; a full chest, nothing is taken; a worker
  saved and loaded mid-trip with the input in hand finishes the job; `machineWork` off, nothing is fed.
- `SavedDataGameTests`: `machine_job` survives a save and load; `ConfigGameTests` and `ExpansionGateGameTests`: the
  `machineWork` switch on the screen and behind the gate.

Showcase scenes: `mechanic`, `create_mill` (both filmed with Create).
