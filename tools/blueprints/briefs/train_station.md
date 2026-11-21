# Train Station: the brief

Family id(s): `train_station` (one level, never renamed; "Train Station" in `en_us.json`,
`blueprint.aliveworkplace.train_station`; hidden advancement `built/train_station`)
Variants: village (none), modded (none: `modded_library.py` skips `TRAINS_ONLY`), looks (none). The Table's style
buttons (`BlueprintStyles`) restyle it automatically.

ROADMAP 50.13 (with the Village Train, the Pump House, the Terminal Nook and the Create builds). One version (not a
flagship). The Village Train (`structure/train/village_train.nbt`) has its own brief; the cells the station must keep
for it are under "Must have" below.

## What it is for
A village's stop on the railway between villages (ROADMAP 39.7, `city/CaravanTrains`, `compat/create/CreateTrains`).
It is at the Blueprint Table **only with Create installed and `villageTrains` on** (`StarterBlueprints.TRAINS_ONLY`,
`CaravanTrains.listed`), and still loads by id without them. Once built, the village hall's round finds its Create
station (`CaravanTrains.find`: any `create:track_station` block entity within `VillageHalls.RADIUS`, 64 blocks, of
the hall) and notes it. When two villages with a caravan route both have a station and their road is built, their
builders lay Create track down the road; a station standing off the road gets a **spur**: one Create curve from an
**end of the station's own straight track** to the road's track (`CaravanTrains.spur`, at most 32 blocks). The hall
then **aims** the station at its track (`CreateTrains`: it writes the station's target track and direction), a
builder puts the **village train** together on the track **just past the station** (`CreateTrains.parts`), Create
assembles it there, and the caravans ride it. Create's station block is also the **Conductor's job block**
(`ModVillagers.TRACK_STATION_POI`, in the acquirable job site tag): a jobless villager near it takes it by himself and
drives the train (`CaravanTrains.conductorFor`, within 24 blocks). A second neighbor's train may later need a second
station put up beside the same track (B898, `CaravanTrains.secondStation`). Nothing else is read: no chest, bed or
other job block.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `train_station` | A Create station beside a straight run of Create track that runs the whole plot; room past the station for the village train; the Conductor's job block | At the Table with Create and `villageTrains` (expansion M39) |

**One level only.** A `train_station_2` would be a new feature (a `StarterBlueprints` entry, upgrade offers).

## Must have
- **Exactly one `create:track_station`** (drawn `assembling=false, waterlogged=false`). It is the station the hall
  finds, the stop the train is built at and the Conductor's job block. A second station in the drawing would be found
  as a second station of the village (`MAX_STATIONS` 4; the nearest to the hall is "ours"), so draw one.
- **The track (Create blocks, by id; checked in `libs/create-fabric-6.0.10.0+mc1.21.1.jar`):** `create:track`, every
  piece **straight and level**, shape `xo` (along x) or `zo` (along z), `turn=false` (no diagonal, slope or curve: the
  station only binds to one straight, level axis, and the spur starts from a straight end).
  - **One straight line across the whole plot, edge to edge** (the test asserts track at every x of the width), so it
    runs on past both sides of the build: the road's track and the spur's curve join it at an end, outside the plot.
  - **The station beside it, at the same height (same y as the track pieces), one or two blocks away square to the
    track, never at its end** (`CreateTrains.NEAR` / `CaravanTrains.stationTrack`: offsets of 1 or 2 across the
    track's axis, same y; along the axis is refused). If it is two away, the cell between is the platform edge (any
    block or air; the station doesn't need it).
  - **Room for the village train past the station, both ways.** The station faces towards the other village (or, on a
    spur, the way out), so either direction can be the one used. In that direction, from the track cell beside the
    station, the next **4 cells of track must be the same straight track** (the train's length, read from the drawn
    train: `village_train.nbt` is 4 long), and the train's cells, **those 4 cells along, from 1 to 4 blocks above the
    track** (the train is 1 wide and 4 tall, drawn on the track line itself), must be air or replaceable when it is
    put up. So: at least 4 track cells on each side of the station's track cell, and nothing solid over the track
    within 4 blocks above it for those 4 cells each way. If the Village Train's brief changes its length or height,
    these numbers follow it. Not tested with this build, but `CreateTrains.parts` logs "no room for the village train"
    and no train is ever built otherwise. A train also arrives and leaves along the whole track: a low roof or beam
    over the track elsewhere is not checked by any code or test (treat it as a risk, not a rule).
  - **Room for a second station (B898).** `secondStation` puts one in the station's own row (same side, same distance
    from the track), **2, 3 or 1 blocks along the track** from the first, towards the second neighbor: at least one of
    those cells on each side must be air or replaceable and dry, with the same straight track beside it. Not tested
    with this build; otherwise the second neighbor's train never gets a stop.
- **The Conductor can walk to the station.** A villager takes a job block only by walking to it (vanilla's
  `AcquirePoi` path to the POI), so the station needs an open, walkable cell next to it, reachable on foot from
  outside. No test checks this with the build (`QaBuildReachGameTests` runs without Create, where the station is air).
- **Beds and residents.** None required (no code or test counts a resident; the Conductor lives elsewhere). A bed, if
  drawn, counts as a village bed and must pass the bed checks below.
- **Storage.** None read (the caravan's goods ride in the train's own chest, `IN_CHEST`). A chest, if drawn, must be
  reachable and stand against a wall, solid block, bed or chest (checks below), drawn empty.
- **Job blocks.** Only the station. Every vanilla or mod job block drawn takes a villager for its own job (a barrel is a
  Fisherman's, a lectern a Librarian's, a composter a Farmer's, and so on): prefer none.
- **Furniture spots.** No kind is required; `blueprint_furniture/train_station.json` (four `bench` spots today) is
  recorded by `rebuild.py` from whatever is drawn and must pass `FurnitureSpotGameTests`,
  `FloatingFurnishingsGameTests`, `QaFurnishedRooms1031GameTests` and `QaChairFacingGameTests`.
- **A way in on the z = 0 side** (the street side) that a villager 1.95 blocks tall walks through
  (`QaDoorwayClearanceGameTests`), and anything upstairs reached by stairs or a ladder from inside.
- **No entity, no jigsaw and no structure void** in the file; no water (none is needed, and loose water near track or
  the station would be a repair the builders can't make once Create is gone).
- **Built whole, nothing skipped**: every block placeable by a builder from the materials list and standing as drawn
  when done (`plan.unfinished` empty, `site.skipped() == 0`).
- **Nothing rests on a Create block**: with Create gone the track and station turn to air (see
  `QaCreateGoneUpkeepGameTests` below), so no lantern, carpet, rail, button or plant may stand on or hang from them.

## Fits
- **Plot: at most 19 wide x 19 deep x 18 tall.** The build test places it at helper (9, 2, 9) in the compat
  `huge_area` (30 x 20 x 30) with the bench at (2, 2, 2), the builder at (3, 2, 3) and the material barrels at
  x = 1..6, z = 5 (y from 2 up): 21 blocks to the area's edge each way, 19 keeping the builder's 2-block landscaping
  inside it; 18 tall because the area is 20 tall from y = 2 (blocks above it are never cleared). The hall position the
  test uses is helper (14, 2, 26), plot (5, 0, 17): only a position, so a deeper plot is fine up to 19.
  `QaCreateGoneUpkeepGameTests` places it at (2, 2, 2) in a 30 x 30 x 30 area (clears y 2..28): fits.
- y = 0 sits on the clicked block; the front is the z = 0 side.
- **Build time: within 30,000 ticks** (the test's timeout, build delay 1, builders' help off). The old drawing is 279
  blocks.
- **Declared size:** `StarterBlueprints.TRAIN_STATION` is `new Vec3i(13, 9, 8)`; the Table hands blueprints out with it
  and the wiki quotes it (`docs/wiki/create.md`, "13 x 9 x 8", twice), so the ship step sets it to the new size.
- **Front and way in:** the z = 0 side faces the player who places it; the way in is there. The track may run along
  either axis, but the line must reach two opposite edges of the plot.
- **Neighbours:** none fixed (no Steward rule, no village plan places it, not in the starting set). In a village it
  usually stands off the road, so its own track ends are where the spur leaves: keep them clear to the plot's edge.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `VillageTrainCompatTests.aBuilderBuildsTheTrainStation` (compat suite, with Create; batch `trainStationBuild`): built
  unturned at helper (9, 2, 9) from barrels holding exactly its materials; when done the site is gone, every block as
  drawn, nothing skipped; `CaravanTrains.isStation` at **(6, 1, 4)**; `CaravanTrains.isTrack` at **(x, 1, 6) for every
  x from 0 to 12** (the whole width: the ship step changes the loop's bound to the new width, or to the new depth if
  the track runs along z); and `CaravanTrains.find` from a hall at helper (14, 2, 26) contains the station.

Not reading this drawing, but built to mirror it (keep them true of the new one, or the ship step updates them):
- `CreateTrackCompatTests.aTrainStationOffTheRoadGetsASpurAndTheTrainRuns`: hand-placed track along x at z = 6,
  x = 1..12, the station beside it one block away (6, 2, 7); the spur joins the station's track to the road's and a
  real train runs out of it and back. Its javadoc describes "a Train Station built off the road".
- `ConductorCompatTests` (with Create): a jobless villager takes a hand-placed Create station as his job; it needs a
  station he can walk to.

By id only (the drawing doesn't matter): `VillageTrainGameTests.noTrainsWithoutCreate` (not listed without Create,
loads by id, behind `villageTrains`, its name in `en_us.json`); `VillageTrainCompatTests` (the switch off: not at the
Table); `QaBuilderSwitchesGameTests` (hidden while M39 is closed); `QaVillageTrainCompatTests` (the name reads);
`QaSecondStationWords1104eGameTests` ("Second Train Station", the B898 site's name).

- `QaCreateGoneUpkeepGameTests.aCreateBuildStandingWithoutCreateNeedsNoRepair` (main suite, without Create): the build
  read without Create (track and station gone to air) placed at helper (2, 2, 2) needs **no repair**, as placed and
  after 30 ticks. So nothing drawn rests on, or is held up only by, a Create block, and nothing breaks by itself.

Library-wide checks that read it (Create's blocks as air in the main suite; it is not in `StarterBlueprints.ALL`, so
the turned and mirrored checks `QaMirroredBuildsGameTests` and `QaTurnedFramesGameTests` skip it, but a player can still
turn or flip it at the Table: no part may depend on one way round):
`QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo` (every chest, barrel, bed head and
vanilla job block within 2 across and 1 up or down of a cell reached on foot from outside),
`QaDoorwayClearanceGameTests` (every wooden door: 1.95 blocks of headroom through it), `QaChairFacingGameTests`,
`QaLedgerFramesHaulGameTests` (frames and paintings), `FurnitureSpotGameTests`, `FloatingFurnishingsGameTests`,
`QaFurnishedRooms1031GameTests` (no standing lantern on a room's floor; every indoor chest against a wall, a solid
block, a bed or another chest), and the other library-wide drawing checks listed in `briefs/brass_foundry.md`
(`QaBuilders1028GameTests`, `BlueprintsStayGameTests`, `QaWindowsAndWards1028BugTests`, `B648GameTests`,
`StyleGameTests.chimneysStayWholeInEveryStyle`). `QaMaterialListGameTests` lists only builds at the Table, so it skips
the station without Create; its material list must still fit a book.

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair; `open_panes`), STYLE.md's checks and `holes.py`: `rebuild.py --candidate` prints no
CHECK line, and `holes.py` passes. `train_station` has no entry in `owner_rules_baseline.json` today: the new one must
add none. The ship step keeps the old drawing in `blueprint_history/` (`history.py`; a site begun on the old drawing
finishes as drawn), regenerates through `generate.py` (`TRAIN_STATION`), and updates `create_builds.py`'s entry, the
declared size, the wiki (`docs/wiki/create.md`) and the pinned coordinates above. There is no showcase scene for it
today.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, which axis the track runs along, which side
of the track the station stands on and whether it is one or two blocks from it, platforms, shelters, benches or none,
lighting, beds, chests, and what the station shows from the street.
