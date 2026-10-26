# Waystones

The Waystones mod in village life: each village raises a waystone by its Village Hall, named after the village (41.1);
next, the hall's Map lists the waystone network (41.2) and caravans and settlers travel by waystone (41.3). Waystones is
optional like every other mod: without it nothing here is offered and nothing runs.

Roadmap items: 41.1, 41.2

## What a player sees

- **A waystone by every Village Hall** (41.1). With Waystones installed, the Steward of a village with a hall proposes
  the **Waystone Plaza** once ("Travelers could find the village by a waystone"), in a Civic zone, nearest the hall
  first. It is a normal build: approved, the village's builder builds it from the store, the waystone included (one of
  Waystones' own waystone blocks, 1 per plaza). The plaza is 7 by 5 by 7: paving in the village's stone, a two-step
  dais with the waystone on top, a lamp post on each corner, a bench on each side facing the stone and two potted
  plants. It is drawn in each village culture's style, the one the hall's biome grows a vanilla village of: plains
  (cobbles and stone, the plain `waystone`), desert (sandstone, `sandy_waystone`), savanna (packed mud, terracotta and
  mud bricks, `mud_bricks_waystone`), snowy (stone bricks, diorite and polished deepslate, `deepslate_waystone`) and
  taiga (mossy cobbles and mossy stone bricks, `mossy_waystone`). Another culture's plaza is tried only where the
  village's own doesn't fit.
- The stone is a **working waystone** of the mod's: a player discovers it by using it and travels to it the usual way.
  It is **named after the village** (the hall's own name, or the made-up one) the moment the builder sets it down, and
  **renamed with the village**: at once when the hall is renamed, and at each hall round (every 600 ticks) in case the
  name changed while the stone's chunk was unloaded.
- **The waystone network on the hall's Map** (41.2). The Village Hall's Map button lists, under "Waystone network (3):",
  every village of the hall's owner (in this dimension) that has raised its waystone: this village first ("Ashford, this
  village"), then the others nearest first, each with its distance and direction from this hall ("Brookmere, 178 blocks
  north-east"), and how it stands for the player looking: **discovered** (green), **not discovered yet** (yellow: use
  the stone once to discover it) or **its waystone is broken** (red: the plaza is there but its stone isn't, until the
  builder's upkeep puts it back). Six villages are listed, the rest counted ("and 2 more"). A village with no stone yet
  makes the line "Waystone network: no village has a waystone yet". Without Waystones the Map lists nothing of this.
- **A broken stone** is a hole in a finished build: the builder's upkeep puts it back like any other block (asking the
  owner first, as with every repair, under `repairAsk`), from a waystone in the chest; the new stone is registered and
  named again.

## How it works

`hall/VillageWaystones` (core) holds the plaza's ids, the culture pick (`culture`: the biome tags
`has_structure/village_<culture>`), the stone spots of a village's plazas (`stones`: its finished plazas and plazas
going up within the hall's radius whose nearest hall is this one, the blueprint's waystone lower halves that hold a
waystone now) and three triggers: `placed` (from `MaterialRules.afterPlaced`, when a builder sets a block down),
`renamed` (from `VillageHallBlockEntity.setCustomName`) and `round` (the hall's round). Waystone blocks are known by id
(`waystones:*waystone`, lower half), so the core never loads a Waystones class.

What touches Waystones is the extension `VillageWaystones.EXTENSION` (`Stones`: raise and nameAt), filled by
`compat/waystones/WaystonesCompat` (behind `//? if waystones`) only when the mod is installed. It uses Waystones'
public API: `WaystonesAPI.getWaystoneAt(level, pos)` to find the registered waystone, and for a stone set down as a
plain block (the builder places blocks as plain blocks, which registers nothing) `WaystonesAPI.placeWaystone(level, pos,
WaystoneStyles.getStyle(block))`, which sets both halves and initializes the block entity's waystone; the blueprint's own
states are then put back (same block, so the block entity and its waystone stay). A name is set with
`MutableWaystone.setName`, then saved and sent to players as Waystones' own rename does
(`WaystoneManagerImpl.get(server).setDirty()`, `WaystoneSyncManager.sendWaystoneUpdateToAll`).

The network (41.2): each time a stone is raised (`raise` returns true), the hall's position and the stone's are kept in
`VillageWaystones.Data` (saved data `aliveworkplace_village_waystones`, one per dimension: `halls` and `stones`, two
long arrays), so villages whose chunks aren't loaded are listed too; a hall round that finds no plaza round the hall at
all (finished or going up) takes the village off. `network(level, hall, viewer)` takes the villages of the hall's owner
from the realm's record of villages seen (`RealmData.Known`, kept by every hall round; the viewer's own when the hall
has no owner), plus this one, and keeps those with a kept stone; `VillageWaystones.mapLines` makes the Map's lore. `stoneOf(level, hall)`
and `standing(level, hall)` are there for 41.3 too. Two calls were added to the extension: `standing(level, pos)` (a
working waystone of the mod's stands there) and `discovered(player, level, pos)` (`WaystonesAPI.isWaystoneActivated`).
The compat answers from the block (`WaystonesAPI.getWaystoneAt`) where the chunk is loaded, and from Waystones' saved list
(`WaystoneManagerImpl.getWaystones()`, by dimension and position) where it isn't; Waystones takes a broken stone off that
list (`WaystoneBlockBase.onRemove`). Unfilled, both answer false (and nothing is listed, since `network` is empty
without the mod).

The Steward's rule is data: `data/aliveworkplace/steward_rules/waystone_plaza.json` (`mod_loaded waystones`, the new
condition `waystones_offered`, `built_count_below 1`, `requires: ["waystones"]`, at most once). The culture's look comes
first in the plot request (`StewardWishes.plotFor`); the looks are registered in `BlueprintLooks`, so the family counts
once whatever look was built.

The build: `tools/blueprints/waystones.py` (`waystone_plaza(culture)`), saved as `structure/waystone_plaza.nbt` and
`structure/looks/<culture>/waystone_plaza.nbt`.

Gradle: `deps.compat.waystones = "maven.modrinth:waystones:Ub2AL65v"` (Waystones 21.1.37, compile only, which makes
`waystones` a Stonecutter constant), Waystones and Balm 21.0.63 (`MBAkmtvl:R6U3hZRZ`) in `tests.compat_mods` and
`tests.screenshot_waystones` (`-Pwaystones=true`, the catalog's `waystones=True`), `waystones` in `suggests`.

## Switches

| Key | Default | What it does |
|---|---|---|
| `villageWaystones` | on | Off: the plaza isn't proposed; stones already standing stay, and are no longer renamed. Without Waystones it does nothing. |

## Saved data

The plaza is an ordinary build site and finished build; the waystone and its name and who discovered it are saved by
Waystones. New in 41.2: `aliveworkplace_village_waystones` (per dimension), the stone each village raised. Older
worlds start with it empty, and each hall's next round (every 600 ticks) fills it.

## Items, blocks, jobs, commands

The Waystone Plaza blueprint (five looks). No new items or blocks: the stone is Waystones' own.

## Decisions

- The plaza is a Steward build only, not listed at the Blueprint Table: the spec asks for the build the Steward
  proposes, and a player who wants a waystone can place Waystones' own.
- The stone costs a real waystone from the store, like every block of a build: no free waystone.
- Plains is drawn as `waystone_plaza`, the other four cultures as looks; the culture is the hall's biome's vanilla
  village (plains when it grows none).
- The village's name wins: a player who renames the stone with Waystones' own menu sees it take the village's name
  again at the next hall round. Renaming the village is how to rename the stone.
- No expansion gate (Milestone 41 has none): the switch and the mod being installed are the only conditions.
- Visibility is Waystones' default (discovered by activation); the stone has no owner.
- The network on the Map is the villages of the **hall's owner** (the viewer's own when the hall has none), so a
  visitor sees the owner's network; "discovered" is always the player looking. Only villages in the hall's dimension are
  listed: distance and direction mean nothing across dimensions.
- The list is lines on the Map button (the hall's map "page" today), after the map's own lines; 40.1's Map section
  takes them along.
- A broken stone keeps its village on the list, marked broken, while the plaza stands: the builder's upkeep puts it
  back, and the line turns green or yellow again. A village whose plaza is gone (demolished, forgotten) leaves the list
  at its next hall round. A village whose hall is gone is never listed.
- At most six villages are listed, then "and N more": the Map button's lore has to fit on the screen.

## Known limits

- The stone's upper half shares the waystone with the lower one (Waystones' placeWaystone), and when its block entity
  loads Waystones moves the waystone's position to the upper half. The compat accepts either half and sets the
  position back to the foot at the next round, rename or placement.

- An unloaded village is listed by its last kept stone and the name its last hall round saw; whether its stone stands
  is asked of Waystones' saved list. The compat tests cover loaded villages only (both halls in one test area); the
  unloaded path is not tested in a GameTest.

- The plaza blueprint's render (tools/blueprints/render) draws only vanilla blocks: the waystone itself is left out
  of the renders.

## Proof

- `WaystonesCompatTests` (compat suite): the rule holds with a hall and the culture's look comes first, each look
  carries its culture's waystone, the builder builds the plaza and its stone is a working, named waystone a player
  discovers; renaming the hall renames it; a broken stone is repaired by upkeep and named again; the switch off, no
  plaza is offered.
- `WaystoneNetworkCompatTests` (compat suite, 41.2): two villages of one owner, each with a plaza: the Map lists both,
  this one first and discovered, the other "31 blocks south-east: not discovered yet"; another player has discovered
  neither; the other's stone broken, it's listed as broken. A village with no stone isn't listed ("no village has a
  waystone yet"), another owner's village isn't either, and the kept stones survive a save and reload.
- `VillageWaystonesGameTests` (main suite, no Waystones): the rule stands aside for the missing mod and no wish is the
  plaza; the hall's Map lists no network.
- Showcase scenes `village_waystone` and `hall_waystones` (41.2: the Map's network, one village of each state).
