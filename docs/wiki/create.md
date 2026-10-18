# Create

Create integration brings the Create mod (the owner's Fabric port, in `libs/`) into village life: a Mechanic who keeps
the machines turning, villagers who feed Create's millstone, press, mixer and crushing wheels, Create-powered builds,
contraptions built from a blueprint, Create schematics at the Blueprint Table and, later, trains between villages.
Part of 2.3, behind its gate (`Expansions.M39`). Built so far: the plan, the gate and the port in the test runtime
(39.1), and the Mechanic (39.2); the full plan is the design note, docs/design/M39.md.

Roadmap items: 39.1, 39.2

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

Later in 2.3, villagers carry wheat to the millstone and iron to the press and bring back flour and sheets, the
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

machineWork, createBuilds, createSchematics and villageTrains are mapped to the gate and come into the config with 39.3
to 39.7.

## Saved data

- `mechanic_round`, an attachment on a Mechanic (39.2): the machines on their round and the next one to visit. Both
  fields are optional; a villager without it starts a fresh round. A villager saved as a Mechanic in a world Create
  was later removed from loads as a jobless villager (vanilla's own fallback for an unknown profession).

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
- The port's jar runs from the test server's `mods/` folder, without its own GameTests; Porting Lib's clash with
  owo-lib, which happens only in the Mojang-named dev runtime, is fixed in the test copy, not in the mod (39.1).

## Known limits

Nothing is built for players yet (the gate). The Mechanic's round has only millstones until 39.3's machine files add
the press, mixer and crushing wheels; a stopped millstone shows on the Mechanic's status, not yet in the chronicle.
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

Showcase scenes: `mechanic` (filmed with Create).
