# Waystones

The Waystones mod in village life: each village raises a waystone by its Village Hall, named after the village (41.1);
next, the hall's Map lists the waystone network (41.2) and caravans and settlers travel by waystone (41.3). Waystones is
optional like every other mod: without it nothing here is offered and nothing runs.

Roadmap items: 41.1

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

Nothing new of ours: the plaza is an ordinary build site and finished build. The waystone and its name are saved by
Waystones.

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

## Known limits

- The stone's upper half shares the waystone with the lower one (Waystones' placeWaystone), and when its block entity
  loads Waystones moves the waystone's position to the upper half. The compat accepts either half and sets the
  position back to the foot at the next round, rename or placement.

- The plaza blueprint's render (tools/blueprints/render) draws only vanilla blocks: the waystone itself is left out
  of the renders.

## Proof

- `WaystonesCompatTests` (compat suite): the rule holds with a hall and the culture's look comes first, each look
  carries its culture's waystone, the builder builds the plaza and its stone is a working, named waystone a player
  discovers; renaming the hall renames it; a broken stone is repaired by upkeep and named again; the switch off, no
  plaza is offered.
- `VillageWaystonesGameTests` (main suite, no Waystones): the rule stands aside for the missing mod and no wish is the
  plaza.
- Showcase scene `village_waystone`.
