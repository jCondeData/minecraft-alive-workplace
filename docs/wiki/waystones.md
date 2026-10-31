# Waystones

The Waystones mod in village life: each village raises a waystone by its Village Hall, named after the village (41.1);
the hall's Map lists the waystone network (41.2), and caravans and settlers travel by waystone (41.3). Waystones is
optional like every other mod: without it nothing here is offered and nothing runs.

Roadmap items: 41.1, 41.2, 41.3

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
  **renamed with the village**: at once when the hall is renamed (a name tag used on the hall), and at each hall round (every 600 ticks) in case the
  name changed while the stone's chunk was unloaded.
- **The waystone network on the hall's Map** (41.2). The Village Hall's Map button lists, under "Waystone network (3):",
  every village of the hall's owner (in this dimension) that has raised its waystone: this village first ("Ashford, this
  village"), then the others nearest first, each with its distance and direction from this hall to that village's stone
  ("Brookmere, 178 blocks north-east", in eight directions, from "4 blocks north" to thousands of blocks off), and how it stands for the player looking: **discovered** (green), **not discovered yet** (yellow: use
  the stone once to discover it) or **its waystone is broken** (red: the plaza is there but its stone isn't, until the
  builder's upkeep puts it back). Six villages are listed, the rest counted ("and 2 more"). A village with no stone yet
  makes the line "Waystone network: no village has a waystone yet". Without Waystones the Map lists nothing of this.
- **Caravans travel by waystone** (41.3). Between two villages whose waystones both stand, a trade caravan walks from
  the Storehouse to its village's stone and steps into it, with Waystones' own teleport sound and burst of portal
  particles; it steps out of the other village's stone, comes in to that Storehouse to unload, and walks back into the
  stone. The trip takes half the shortest trip (30 seconds) instead of the road's time. The chronicles say "A caravan
  left for Brookmere by waystone with 32 × Oak Log" and "A caravan from Ashford stepped out of the waystone with ...".
  If either stone is broken or missing when the caravan sets out, it walks the road as before; if one is broken by the
  time it steps out, it walks the whole road from then ("The caravan from Ashford found a waystone broken and is walking
  the road instead"). Trains (39.7) come first: between two villages with a train line the caravan rides the train.
- **Settlers travel by waystone** (41.3). Settlers leaving for a new colony go by waystone when another village's
  waystone stands nearer the colony's spot than their own village does, and their own stands too: after the bell they
  walk to their village's stone and step in, and the journey counts only from the other village's stone to the spot
  (3 minutes plus a tick a block from there). When their time is up they step out of that stone; if it (or their own) is
  broken by then, they walk the whole road from their village instead, and its chronicle says "The settlers for Newbrook
  found a waystone broken and are walking the road instead".
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
long arrays), so villages whose chunks aren't loaded are listed too; a hall round that finds none of the village's own plazas
(finished or going up, whose nearest hall is this one; a neighbor's plaza within the radius doesn't count, B528)
takes the village off. `network(level, hall, viewer)` takes the villages of the hall's owner
from the realm's record of villages seen (`RealmData.Known`, kept by every hall round; the viewer's own when the hall
has no owner), plus this one, and keeps those with a kept stone; `VillageWaystones.mapLines` makes the Map's lore. `stoneOf(level, hall)`
and `standing(level, hall)` are there for 41.3 too. Two calls were added to the extension: `standing(level, pos)` (a
working waystone of the mod's stands there) and `discovered(player, level, pos)` (`WaystonesAPI.isWaystoneActivated`).
The compat answers from the block (`WaystonesAPI.getWaystoneAt`) where the chunk is loaded, and from Waystones' saved list
(`WaystoneManagerImpl.getWaystones()`, by dimension and position) where it isn't; Waystones takes a broken stone off that
list (`WaystoneBlockBase.onRemove`). Unfilled, both answer false (and nothing is listed, since `network` is empty
without the mod).

Travel by waystone (41.3): `VillageWaystones.linked(level, from, to)` is true when the plaza is offered (the switch on,
the mod installed) and the stone each village raised stands now (`standing`). The extension gained a default
`warp(level, pos)`, a no-op unfilled; the compat does what Waystones' own `WaystoneTeleportManager.performTeleport` does
for a teleport: the vanilla `PORTAL_TRAVEL` sound at the mod's volume (0.05) and the mod's `TeleportEffectMessage(pos)`
to the players tracking the spot (its client draws 128 portal particles), sent as a plain `ClientboundCustomPayloadPacket`
so Balm isn't needed on the compile classpath (portal particles from the server if that fails).
- Caravans: `Caravans.send` checks trains first, then `linked`: a caravan by waystone is a `Shipment` marked
  by waystone (its byWaystone flag, saved, false in older saves), arriving in `Caravans.waystoneTrip` (half `MIN_TRAVEL`,
  never longer than the road), never lost on the way (31.20). At the receiving hall's round, `Data.offTheWaystones`
  turns a due caravan by waystone whose link is gone into a walking one, arriving a whole `trip` later, before
  `unload`; an unloaded caravan by waystone plays `warp` at the receiving stone. `CaravanSights.start` with a waystone
  makes the party's edge the open spot beside the stone (`VillageWaystones.beside`), and plays `warp` when the party
  leaves at it (`Party.waystone()`).
- Settlers: `RealmData.Order` gained `via` (the hall of the village they go through; saved as `via`, empty in older
  saves). At the bell (`MUSTER` to `LEAVING`) `Settlers.via` picks, of the villages kept in `VillageWaystones.Data`,
  the one linked to the mother whose stone is nearest the spot, if nearer than the mother's hall. While leaving,
  `Settlers.edge` is the spot beside the mother's stone, and they depart when all stand there (or the usual way:
  unseen, or after `LEAVE_TICKS`). `depart` plays `warp` and sets the journey from the via village's stone
  (`Settlers.journey(order, level)`). When the time is up, `ColonyArrival.tick` asks `Settlers.stepOut`: both stones
  standing, `warp` at the via stone and the arrival goes on; else the order walks (`via` cleared, a whole `journey`
  from now) and the mother's chronicle notes it.

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
| `villageWaystones` | on | Off: the plaza isn't proposed; stones already standing stay working waystones, are still renamed with the village and stay on the hall's Map (a broken one as broken); caravans and settlers walk (41.3), and a caravan already on its way by waystone walks the whole road when it's due. Without Waystones it does nothing. |

## Saved data

The plaza is an ordinary build site and finished build; the waystone and its name and who discovered it are saved by
Waystones. New in 41.2: `aliveworkplace_village_waystones` (per dimension), the stone each village raised. Older
worlds start with it empty, and each hall's next round (every 600 ticks) fills it.
New in 41.3: a caravan on the road keeps `byWaystone` (in `aliveworkplace_caravans`, false when missing) and a
colony order keeps `via` (in `aliveworkplace_realms`, empty when missing): older saves walk, as before.

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
- (41.3) A caravan by waystone takes half the shortest trip (30 seconds: the walk to the stone and from the other),
  whatever the distance, and is never lost on the way. A train line between the two villages wins over the waystones.
- (41.3) Whether the stones stand is asked when the caravan sets out and again when it steps out; a stone broken in
  between makes it walk the whole road from then (not a share of it): the simplest rule a player can follow, and the
  goods are never lost. Caravans walking back home (the other village's hall gone) walk.
- (41.3) Settlers go to a new spot, where no village stands yet, so "the other village" is the waystone village nearest
  the spot, used only when it is nearer than home; any village of the network in the dimension counts (Waystones'
  stones are public), not only the owner's. The journey's 3 minutes stay (making camp); only the distance counts from
  the other stone. Their step out is when their time is up, so the broken-stone check is then.
- (41.3) The sound is Waystones' own (vanilla's portal travel at the mod's very low volume) and the particles are the
  mod's own message: no new sound or particle of ours. No new switch: `villageWaystones` covers it.

## Known limits

- The stone's upper half shares the waystone with the lower one (Waystones' placeWaystone), and when its block entity
  loads Waystones moves the waystone's position to the upper half. The compat accepts either half and sets the
  position back to the foot at the next round, rename or placement.

- An unloaded village is listed by its last kept stone and the name its last hall round saw; whether its stone stands
  is asked of Waystones' saved list (QA's `WaystoneMapQaCompatTests` checks a village 1,500 blocks off whose chunks
  are unloaded: discovered for one player, not yet for another, never broken).

- (41.3) The visible caravan party (33.7) only shows with a player near the village; the stones' sound and particles
  play where the stone's chunk is loaded. The scene `waystone_caravan` films it; the compat tests check the trip, not
  the party's walk.

- (41.3) A village whose hall is gone while its plaza and stone stand is off the Map, but its stone is still kept (no hall
  round runs to forget it), so settlers for a spot beyond it may still go through that stone (QA, 2026-10-26).

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
- `WaystoneTravelCompatTests` (compat suite, 41.3): a caravan through the waystones (by waystone, half the shortest
  trip, the chronicle lines at both ends); Brookmere's stone broken mid-trip, the caravan walks the whole road and
  arrives walking, and the next one walks from the start; a trip by waystone saved and reloaded mid-trip still steps
  out at Brookmere, an older save's caravan walks, and a colony order keeps `via` (an older one has none); settlers
  called to the hall go through Ashford's stone toward Brookmere's (journey from Brookmere's stone), step out with both
  standing, and walk the whole road with Brookmere's broken, as the chronicle says.
- `WaystoneQaCompatTests` (compat suite, QA): the switch turned off after both stones stand (the stones stay and are
  still renamed, a caravan under way by waystone walks the whole road with its goods, new caravans and settlers walk);
  only one village with a stone (they walk); a name tag on the hall with a long name with an accent, an apostrophe and an
  ampersand, and the hall's name cleared (the made-up name again); the Map's words for a village due north ("24 blocks
  north") and one south-east; a village whose hall is gone leaves the Map; the network file through a save and reload,
  a broken stone listed as broken, and the village off once the plazas are gone.
- `WaystoneMapQaCompatTests` (compat suite, QA, 41.2): the Map's distance and direction for twelve villages, each of
  the eight directions, both sides of the east/north-east edge, one 4 blocks off and one 5,000 blocks off, nearest
  first, six listed with "and 6 more" and the next six once the nearest leave; a village 1,500 blocks off whose chunks
  are unloaded, discovered by one player and not by another; the switch off after the stones stand (both stay listed,
  a broken one as broken). Every line read through `en_us.json`, no raw keys.
- Scene `waystone_caravan` (showcase, with Waystones): Thornholm's caravan walks to its stone and steps in; Ashford's
  steps out of it, unloads and walks back in.
- `VillageWaystonesGameTests` (main suite, no Waystones): the rule stands aside for the missing mod and no wish is the
  plaza; the hall's Map lists no network.
- Showcase scenes `village_waystone` and `hall_waystones` (41.2: the Map's network, one village of each state).
