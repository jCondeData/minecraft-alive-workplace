# The Terrace

Milestone 49, item 49.6: the village's cheap way to many beds, several households under one roof, rebuilt from scratch
with the rebuild kit (`tools/blueprints/REBUILD.md`): a blind brief (`tools/blueprints/briefs/terrace.md`), a blind
design, and a critic who scored old and new side by side, sent it back once and then said SHIP
(`tools/blueprints/briefs/terrace_critic.md`).
Roadmap items: 49.6, B281, B304, B640, B641, B925

## 1. What a player sees

- **Terrace** (13 x 16 x 10, a tier I home; in the Blueprint Table from the start): the Crow-Step Row. Two narrow red
  brick houses stand shoulder to shoulder, each under its own steep slate roof running back from the street between two
  crow-stepped gables with white stone copings, so the row reads as a saw of gables. Brick piers mark the party walls
  front and back, white quoins mark the row's corners, and a white belt course runs all the way round at the bedroom
  floor. Every door has a slate canopy with a lantern under it and a stone step. The front-room windows have white
  sills with flowers. The end walls have shuttered windows and a slate eave on corbels. Each house's chimney rises in
  the top of its back gable, with its own smoke.
  - Downstairs, the **front room**: a low bookcase under the window with a pot plant, a rug at the door. Behind it the
    **kitchen**: the table and its chair under a hanging lantern, the stove, a stone sink under the back window, the
    barrel, a shelf of crockery, and the ladder up the party wall.
  - Up the ladder, the **bedroom**, open to its own steep roof and lit from the front and the back gable: two beds heads
    to the back wall with a nightstand, and in the front half a desk in the window, the chest, a window seat under the
    window over the door, and a rug.
  - Each household keeps its own bed and rug colors.
- **Terrace II** (18 x 16 x 10, a tier II home; sold by a builder once the Terrace stands, or a questbook reward, or
  found as a blueprint in village chests): a **third house** on the east end, the same house again, with its own gable,
  door and chimney.
- Beds: 4 and 6, two per home. Every piece is a furniture spot, so a builder with the furniture mods builds the mods'
  pieces in their places (37.2); the modded copy dresses the floors with herringbone boards, the chests, ladders and
  bookshelves in spruce, and sets crockery on each kitchen table (37.12).
- A Terrace standing in an older drawing keeps it until its owner presses **Refresh** on the hall's Repairs page
  (36.21). The chests, barrels and stoves of the old drawing stand in the same cells in the new one, so a refresh never
  has to move one.

- **In its village's culture** (49.10c): a desert village builds both levels as the Windcatcher Row (sandstone below
  mud brick, a dark oak lattice over each door, flat roofs behind a parapet, one slotted windcatcher per home); a
  savanna village as the Veranda Row (one long golden thatch hip, red granite below packed mud, a veranda on acacia
  posts, each home's window trim painted); a taiga village as the Logging Row (dark spruce logs on a mossy cobble
  footing under one slate roof, a veranda on log posts, a stack on every party wall). The homes, beds, doors, ladders
  and furniture are the same. Plains and snowy villages build the Crow-Step Row. The Blueprint Table lists the looks.

## 2. How it works

- Drawn in JavaScript with the rebuild kit: `tools/blueprints/js/terrace/` (`plan.mjs`: three concepts, story, palette,
  masses, rooms, openings, every furniture footprint, the walking line, and the critic's round-2 notes; `recipe.mjs`: the
  passes for levels 1 and 2), on the shared parts library `tools/blueprints/js/parts/`, which it grew with
  evenGableRoof (a steep roof over an even span, its ridge two stairs back to back, gutters over party walls),
  crowGable (a stepped parapet gable with copings), doorCanopy, a cauldron `sink`, a low `bookcase` and slab sills.
  `build.sh` builds both levels as candidates; the live drawings are saved with `tools/blueprints/rebuild.py terrace
  <id>`, which runs check.py's checks with the owner's rules, `finish()` and writes `blueprint_furniture/<id>.json`.
- Each home is drawn in its own frame between party walls on a 5-block pitch (x 1, 6, 11, 16): the brief's kept cells
  (the old drawings' stoves, barrels and chests) fix that pitch. Every home has the same hand (window west, door east,
  ladder on the east party wall), so the front repeats evenly.
- The old Python drawing (`starter.py`'s `terrace`, `terrace_2`) is no longer saved by `generate.py`; `history.py` ships
  the old drawings in `blueprint_history/terrace*/` (and `modded/terrace*/`) for refreshes.
- `StarterBlueprints.TERRACE` and `TERRACE_2` keep their sizes. Modded copies: `modded_library.py terrace terrace_2`.
  Village looks: the styles (`BlueprintStyles`) are applied at play time, as before; the campfires sit on stone-brick
  caps that stay solid in every style.

- The culture looks (49.10c) are drawn in `tools/blueprints/js/terrace_<culture>/` from the brief and the shipped
  plan (they import its homes, furniture, doors and ladders; `look_spots.py` says SAME BUILDING); `SHIP=1 build.sh`
  saves them as `structure/looks/<culture>/terrace[_2]`. The critic passed desert, savanna and taiga in round 1
  (`tools/blueprints/briefs/terrace_looks_critic.md`); snowy went back (its gables ended in a blunt dark square) and is
  redrawn, not yet shipped. `BlueprintLooks` lists the three looks; `StarterBlueprints.TERRACE_DESERT`, `_SAVANNA` and
  `_TAIGA` list each look's levels. Scene: `rebuild_terrace_looks`.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | `moddedLibrary` and the furniture switches apply as for every home. |

## 4. Saved data

Nothing new. The ids never change (`aliveworkplace:terrace`, `terrace_2`); standing Terraces and sites mid-build keep
their drawing until a refresh.

## 5. Items, blocks, jobs, commands

- Blueprints: Terrace, Terrace II (`aliveworkplace:terrace`, `terrace_2`). The Steward builds a Terrace in a homes zone
  when a Village or bigger is three or more beds short (`steward_rules/homes_terrace.json`).

## 6. Decisions

- Concept C of three (the Weavers' Row under one long roof, the Catslide Cottages, the Crow-Step Row): one gable per
  household says "several homes" from 20 blocks, and II grows the way a real terrace does, one more house on the end.
- No hero gable: a terrace's strength is the repeat.
- A ladder, not a stair: a 4-wide house has no room for a flight, and `BedLadders` takes villagers up a ladder to their
  own bed.
- The critic's round-1 notes: the back gables are now finished like the front with a chimney in each apex (no loose
  stacks); the belt course, plinth and quoins go round the ends with corbels under the side eaves; a kneeler closes each
  end eave behind the parapets; the bedrooms' front halves are furnished; one door wood and one hand for every home.
  Round 2's notes: the west bedroom's rug is white (gray read as a hole from above), and the kneeler is two courses deep
  so the eave's last stair butts flush against it.
- The bedroom's floor at z 5, beside the beds, stays bare boards: the tests stand a villager on the open floor nearest
  a bed, and that floor must be nearer his own beds than any floor over the party wall.

## 7. Known limits

- The showcase scene `rebuild_terrace` is filmed on GitHub, not in this session.
- check.py's "upper floor with no stairs or ladder" lines for `modded/terrace` and `modded/terrace_2` are the known
  false alarm for modded copies (their ladders are Carved Wood's spruce ladders); the vanilla drawings pass.
- A refresh keeps slept-in beds, and the old drawings' beds stood in what is now the bedroom's open floor (z 3-4); a
  refreshed Terrace whose old beds were slept in keeps them there until they are broken.

- No snowy look yet (critic round 1 BACK; round 2 drawn, not yet judged).

## 8. Proof

`TerraceSleepGameTests`, `QaHallBeds1103cGameTests` (turned a quarter; save and reload), `QaBedsHauler1105cGameTests`,
`QaTerraceUpstairs1104c2BugTests`, `TerraceSleepCompatTests` (Handcrafted's beds), `FurnishedRoomsGameTests`
(`everyTerraceHomeHasItsTableAndChairSpots`, lights, beds to a wall), `FloatingFurnishingsGameTests`,
`TerraceFurnitureCompatTests`, `QaClinicTerrace1028GameTests` (both levels built turned and mirrored, every kitchen
alike), `B640GameTests` (the front panes, `FRONT`), `B648GameTests`, `BuilderGameTests.buildsTerrace` and
`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `B280LandscapeGradeGameTests`, `QaReleaseRefresh1028GameTests`
and `RefreshMatchesFreshBuildGameTests`, `QaRefreshHistory1028GameTests`, `StyleGameTests.chimneysStayWholeInEveryStyle`.
Scenes `rebuild_terrace`, `terrace_sleep`, `furnished_home`. `TerraceLooksGameTests` (culture pick, fallbacks, base/upgrade/lock per look, sizes, beds and job blocks, Level I of
each look built to DONE, a desert site saved mid-build reloads as desert). Scene `rebuild_terrace_looks`.
