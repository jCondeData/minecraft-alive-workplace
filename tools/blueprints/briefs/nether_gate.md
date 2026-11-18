# Nether Gate: the brief

Family id(s): `nether_gate`, `nether_gate_2` (never renamed)   Variants: village (none drawn: a Steward zone's style is
applied by `BlueprintStyles`), modded (`structure/modded/nether_gate.nbt` and `modded/nether_gate_2.nbt`, made from
the vanilla drawings by `tools/blueprints/modded_library.py`; regenerate them after the new drawings, never draw them by
hand), looks (none)

## What it is for
The village's way to the Nether and the **Netherworker**'s workplace. A netherworker works at a workstation here,
packs rations from the chests beside it, steps into a lit Nether portal near the workstation and is away on an
expedition, then comes back out of that portal with the Nether's goods (`nether/Netherworkers`, `NetherworkerWork`).
When a netherworker is wanted and has no workstation, the Steward proposes this build in the Workshops zone
(`steward_rules/workplace_nether_gate.json`, priority 62). When the builder finishes it, he **lights the portal frame**
with a flint and steel (or a fire charge) from his chests (`Builders.lightPortals`). A finished Nether Gate (or any
lit portal in the hall's area) is the village's **Nether link** (`threat/Conditions.netherLink`): Nether raiders
(piglins, `Culture.Arrival.PORTAL`) can only come to a village that has one, and they step out of the lit portal, or
gather at the gate's origin corner when the portal isn't lit. Nobody lives here.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I | `nether_gate` | The portal frame (lit by the builder), the netherworker's workstation and its storage | **Rare** (`blueprint_unlocks/rare.json`): a dark "???" at the Blueprint Table until the village unlocks it; found as a blueprint in six raid-lair loot tables (`piglin_hoard`, `war_camp`, `pirate_ship`, `coven_hut`, `bandit_camp`, `pharaohs_tomb`). Proposed by the Steward for a netherworker without a workstation |
| II | `nether_gate_2` | Level I grown: more storage for the netherworker's haul, and something that grows a Nether crop (the old one had a nether wart garden; no code reads it, so it's free, but II must add something a player sees as more than I) | By name (`BlueprintUpgrades`: `<name>_2` upgrades `<name>`), sold by the builder who finished Level I; also in `rare.json` |

**Exactly two levels.** No `nether_gate_3`: `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` counts exactly 64
starter upgrades.

## Must have (each level)
- **The portal frame:** a vanilla Nether portal frame of **obsidian** that vanilla's `PortalShape.findEmptyPortalShape`
  accepts (a rectangle of obsidian round an empty inside, inside at least 2 wide and 3 tall; corners optional), drawn
  **empty** (air inside, not `nether_portal` blocks: the builder lights it). `Builders.lightPortals` scans every
  obsidian block in the build's box whose block above is air and asks `PortalShape` on both axes, so:
  - **Exactly one** portal frame (a second obsidian frame would be lit too and use a second flint and steel).
  - Obsidian used anywhere else in the build must not have air above it where it could form a second frame.
  - **Keep it where it is: the inside's bottom-left cell at (4, 2, 3), the frame in the x-y plane (axis X), facing the
    front.** The screenshot harness lights every Nether Gate's frame by calling
    `PortalShape.findEmptyPortalShape(level, origin.offset(4, 2, 3), Axis.X)` (`ScreenshotHarness`, about line 4412,
    for the `workshops` scene and the nightly showcase); if the frame moves, that line moves with it in the ship step.
    The old frame: obsidian bottom row at y = 1, inside x 4-5, y 2-4, z = 3 (a 2 x 3 inside), so a frame of the same
    inside size and place needs no harness change.
  - The way in: the portal is walked into from the front, so the cells in front of the inside must be open floor a
    villager can stand on and walk from (the netherworker's return spot is the nearest standing spot within 3 blocks of
    the portal, `Walker.standingSpot(level, portal, portal, 3.0)`).
- **Job block: exactly one netherworker workstation.** The Netherworker's job site is the **Nether Brazier**
  (`aliveworkplace:nether_brazier`, its own POI) **or a cartography table** (the profession accepts either,
  `ModVillagers.NETHERWORKER`, line ~420). The old drawing has a cartography table (beware: a vanilla cartographer can
  also claim a cartography table; the brazier is the netherworker's alone, and the `StarterBlueprints.NETHER_GATE`
  javadoc already says "with the Nether Brazier"). The workstation must be:
  - within **32 blocks** of the portal (`Netherworkers.PORTAL_RANGE`), easily: put it by the portal;
  - reachable on foot from the build's front (a villager walks to it and stands beside it).
- **Storage:** at least one chest or barrel **within 8 blocks across and 4 up or down of the workstation**
  (`SupplyContainers.RADIUS = 8`, `VERTICAL = 4`): the netherworker packs rations from there and unpacks the haul
  there. The old Level I had a chest and a barrel; Level II added two chests and three barrels in a storehouse.
- Beds and residents: none.
- Furniture kinds a job or test needs: none (there is no `blueprint_furniture/nether_gate*.json`).
- Lights: none read by code. (Soul fire, lanterns and campfires are free; a lit campfire is fine for a builder.)

## Every block must be one a survival builder can place and keep in place
- No block a builder can't place (flowing liquids, fire, `nether_portal`, technical blocks):
  `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`.
- Built from **exactly** its material list it must finish as drawn (`BuilderGameTests.buildsNetherGate`,
  `buildsNetherGateII`: a builder with exactly `plan.materials()` in 4 barrels, every block as drawn when done). So no
  crop that grows past its drawn age (nether wart drawn at `age=3`, its last, is fine), no dirt beside grass, nothing
  that needs a later block to stand on.
- Stocking: the test fits the whole list into **4 barrels of 27 stacks = 108 stacks**; keep the material list under that
  ("test needs more barrels").

## Fits
- Plot: Level I **11 (x) x 9 (y) x 7 (z)**, Level II **15 x 11 x 11** today (`StarterBlueprints.NETHER_GATE` and
  `NETHER_GATE_2`, checked against the `.nbt`s by `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`). A
  different size is allowed if the ship step changes the `Vec3i` to match, but mind the test areas:
  - `buildsNetherGate` builds Level I at origin **(9, 2, 9)** in `big_area` (22 x 18 x 22), with the barrels and bench at
    helper x 1-5, z 1-5: Level I may be at most **13 wide (x), 13 deep (z) and 16 tall**.
  - `buildsNetherGateII` builds Level II at origin **(7, 2, 8)** ("15 wide: further left"): at most **15 wide, 14 deep
    and 16 tall** there, or the ship step moves the origin (inside the area, clear of x 1-5, z 1-5).
- Level II grows out of Level I: II is **at least I's size on every axis** and **keeps at least 60 % of I's non-air
  blocks** exactly in place (`BuilderGameTests.starterUpgradesKeepMostOfTheirBase`), since II is built over a
  standing I at the same origin. So the portal frame, the workstation and I's storage stay where they are in II.
- y = 0 sits on the clicked block; the front is the z = 0 side, where the portal is walked into.
- Neighbours: placed in the Workshops zone (`VillageMaps`: kind WORKSHOPS, by id) by the Steward's plot planner, or
  anywhere by a player. Nothing is placed against it.

## Rules tests check
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: size equals the declared `Vec3i`; every
  block buildable.
- `BuilderGameTests.buildsNetherGate` (batch `starter_builds_6`, 30,000 ticks) and `buildsNetherGateII` (batch
  `starter_builds_7`, 40,000 ticks, origin (7, 2, 8)): built from exactly its materials, finished as drawn within the
  time. **These pin the origin and the area, so they pin the maximum size above.**
- `BuilderGameTests.starterUpgradesKeepMostOfTheirBase`: II >= I on every axis, >= 60 % of I's blocks kept, 64 upgrades.
- `BuilderGameTests.builderLightsThePortalFrame`: `lightPortals` on a hand-set 4 x 5 obsidian frame (not the drawing):
  pins that the gate's frame is ordinary obsidian that `PortalShape` lights.
- `ThreatGameTests` (the `nether_link` condition, about line 494): a finished `nether_gate_2` counts as the village's
  Nether link and the link is the build's origin; reads the id only.
- `WorkplacesGameTests` (`Case("workplace_nether_gate", "aliveworkplace:netherworker", "nether_gate", "workshops", HAMLET)`):
  the Steward's rule builds `nether_gate` in `workshops` and the blueprint loads; reads the id.
- `QaBlueprintLibraryVerify1108bGameTests`: `nether_gate` is rare (hidden, "???") in a new village; reads the id.
- `ModdedLibraryGameTests.everyHomeAndWorkshopHasAModdedCopy`: every workshop has a `modded/` copy, so regenerate
  `modded/nether_gate*.nbt` with `modded_library.py` after the new drawings.
- `ScreenshotHarness` (not a test, but the nightly showcase): lights the frame at (4, 2, 3), axis X (see above).
- The owner's rules (`check.py`) and STYLE.md's checks: `rebuild.py --candidate` prints no CHECK line, `holes.py` passes.

## Free
Everything not listed above: materials (Nether stone, wood, anything), the shape round the portal (arch, gatehouse,
shrine, ruin), the court, braziers, lights, roofs, what Level II adds besides more storage, and where the workstation
and chests stand, as long as they are near the portal, reachable, and the frame stays one lightable obsidian frame at
(4, 2, 3) facing the front.
