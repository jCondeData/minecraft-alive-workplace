# Create

Create integration brings the Create mod (the owner's Fabric port, in `libs/`) into village life: a Mechanic who keeps
the machines turning, villagers who feed Create's millstone, press, mixer and crushing wheels, Create-powered builds,
contraptions built from a blueprint, Create schematics at the Blueprint Table and, later, trains between villages.
Part of 2.3, behind its gate (`Expansions.M39`). Built so far: the plan, the gate and the port in the test runtime
(39.1); the full plan is the design note, docs/design/M39.md.

Roadmap items: 39.1

## What a player sees

Nothing yet. Once 2.3 ships, and only with Create installed, a jobless villager next to a Create Toolbox becomes the
village's Mechanic, villagers carry wheat to the millstone and iron to the press and bring back flour and sheets, the
Blueprint Table offers a Watermill, a Windmill and a Workshop, and schematics uploaded with Create's Schematic Table
show up at the table.

## How it works

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

None yet: mechanics, machineWork, createBuilds, createSchematics and villageTrains are mapped to the gate and come
into the config with 39.2 to 39.7.

## Saved data

None yet. The planned fields of later items, each with its default, are listed in the design note.

## Items, blocks, jobs, commands

None of our own yet. The ids the milestone reads from Create (the machines, the drive-train parts, the sails, the
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
- The port's jar runs from the test server's `mods/` folder, without its own GameTests; Porting Lib's clash with
  owo-lib, which happens only in the Mojang-named dev runtime, is fixed in the test copy, not in the mod (39.1).

## Known limits

Nothing is built for players yet. The port is only checked to load next to the pack's other test mods and to hold
every id the milestone needs. Trains (39.7) wait on the owner's answer in the design note.

## Proof

- `CreateCompatTests`: `createIsLoaded` (Create and its libraries loaded, its own GameTests left out),
  `createBlockIdsExist`, `createItemIdsExist` and `createRecipeTypesExist` list every id the milestone reads and fail
  naming any that's missing.
- `ExpansionGateGameTests.createIntegrationIsGated`: the gate is shut for players and open in GameTests, for each of
  the five switches.

Showcase scenes: none yet.
