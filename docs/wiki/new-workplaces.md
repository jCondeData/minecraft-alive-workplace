# New workplaces

Eight workplaces drawn for the trades that had none, each with a II upgrade where the spec gives one, and a Steward
rule that asks for it when a worker of its trade has no workplace (Villages that build themselves, 1.1).

Roadmap items: 27.13, 27.14

## What a player sees

The Blueprint Table lists the Smithy, Mason's Yard, Fletcher's Lodge, Map Room, Farmstead, Fisher's Hut, Weaver's
Cottage and Bandstand, and the upgrades Smithy II, Mason's Yard II, Fletcher's Lodge II, Farmstead II, Fisher's
Hut II and Weaver's Cottage II. A village with a Steward gets one proposed when a worker of its trade has lost his
job block: "1 job here has no workplace of its trade".

## How it works

**The buildings.**

- **Smithy**: a stone forge under a timber roof with an open front, an anvil, a quench trough and a chimney with a
  campfire; a blast furnace, a smithing table and a grindstone. Smithy II adds a coal and ore store and a second
  blast furnace.
- **Mason's Yard**: a fenced yard of cut stone with a lean-to over a stonecutter. Its II has a second stonecutter.
- **Fletcher's Lodge**: a log cabin with a log pile, a straw target and a fletching table. Its II has a second
  fletching table.
- **Map Room**: a narrow tower house with a cartography table and a lookout at the top (no upgrade).
- **Farmstead**: a farmhouse with one bed and a composter beside a 9x5 field round a water channel, with a scarecrow.
  Farmstead II adds a barn and a second field.
- **Fisher's Hut**: a shore hut with a jetty on log posts and a barrel. Its plot must be on a shore: water near its
  front, at most 3 deep under the jetty. Fisher's Hut II adds a smokehouse with a smoker.
- **Weaver's Cottage**: a cottage with a loom and a fenced sheep pen. Its II adds a dye garden.
- **Bandstand**: an open eight-sided bandstand with a jukebox, in the Gardens zone. It counts 3 for beauty, like the
  other decorations; the workplaces count nothing.

**The rules.** Each building has a Steward rule file (`data/aliveworkplace/steward_rules/workplace_<name>.json`,
priority 62, 2 days' cooldown) that holds while a worker of its trades has no job block of his own:

| Building | Trades | Zone |
|---|---|---|
| Smithy | Armorer, Miner | Workshops |
| Mason's Yard | Mason | Workshops |
| Fletcher's Lodge | Fletcher, Lumberjack | Workshops |
| Map Room | Cartographer | Civic |
| Farmstead | Farmer | Farms |
| Fisher's Hut | Fisherman | Farms |
| Weaver's Cottage | Shepherd | Workshops |
| Bandstand | Bard | Gardens |

A worker standing at his own block wants nothing, and trades with no building here (Toolsmith, Weaponsmith,
Librarian, Butcher) never set one of these rules off.

## Switches

none (the Steward's own switches: see [The Steward](steward.md))

## Saved data

none (a built workplace is an ordinary finished building)

## Items, blocks, jobs, commands

- Blueprints: `aliveworkplace:smithy`, `aliveworkplace:masons_yard`, `aliveworkplace:fletchers_lodge`,
  `aliveworkplace:map_room`, `aliveworkplace:farmstead`, `aliveworkplace:fishers_hut`,
  `aliveworkplace:weavers_cottage`, `aliveworkplace:bandstand`, and the `_2` upgrades of all but the Map Room and
  the Bandstand.
- Steward rules: `aliveworkplace:workplace_smithy`, `aliveworkplace:workplace_masons_yard`,
  `aliveworkplace:workplace_fletchers_lodge`, `aliveworkplace:workplace_map_room`,
  `aliveworkplace:workplace_farmstead`, `aliveworkplace:workplace_fishers_hut`,
  `aliveworkplace:workplace_weavers_cottage`, `aliveworkplace:workplace_bandstand`.

## Decisions

- The Miner and the Lumberjack count for the Smithy and the Fletcher's Lodge whatever tool they hold (27.13 names
  them "with a pickaxe" and "with an axe"; the rule counts the job).

## Known limits

- The rules ask only when a worker has lost his block, not when the village wants a job nobody can take.
- Mason's Yard II's hoist is two chains.

## Proof

GameTests: `CraftWorkplacesGameTests` (4), `CountryWorkplacesGameTests` (4) and `QaNewWorkplacesGameTests` (21: each
rule fires for its own trades only and only while that worker lacks his block; each building holds the blocks the
spec promises, its II more; the names and reasons as the spec spells them; only the Bandstand counts for beauty;
Fisher's Hut II refused on dry land and over water 4 deep, taken over water 2 deep).

Showcase scenes: `gallery`.
