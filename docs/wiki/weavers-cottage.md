# The Weaver's Cottage

Milestone 49, item 49.8: the Shepherd's workplace (27.14), rebuilt from scratch with the rebuild kit
(`tools/blueprints/REBUILD.md`): a blind brief (`tools/blueprints/briefs/weavers_cottage.md`), a blind design, and a
critic who scored old and new side by side (silhouette, depth, interior and detail 4/4/4/4 against 3/2/2/2) and said
SHIP (`tools/blueprints/briefs/weavers_cottage_critic.md`).
Roadmap items: 27.14, 46.2, 49.8

## 1. What a player sees

- **Weaver's Cottage** (18 x 14 x 12; in every new village's starting set; the Steward builds one for a Shepherd with
  no loom): a hill weaver's house. A long two-story range of gritstone rubble under a steep dark slate roof, the ridge
  along the street, mossy stone low down, dressed quoins, a wide chimney stack on the west gable. Across the front runs
  the **weaver's window**, three lights between stone mullions, and over it the loft's longer band of five, both on a
  molded sill course; a little gabled stone porch shelters the door, with flowers under the window.
  - Inside, the **workroom**: the loom in the window's west light (the Shepherd's job block), his chest at the window's
    east end, a carding table with two stools, the hearth in the west wall, fleeces piled by the walls, two hanging
    lamps.
  - Up the stair along the back wall, the **wool loft**: the spinners' bench at the long window, a cutting table with
    its stool and bolts of dyed cloth, the wool chest, fleeces under the back slope, two lamps hung on chains.
  - On the east gable, the **sheep fold**: fences with a gate to the street, a drystone back wall, an open slate
    lean-to over the fold door with hay at its back, grass for the flock. The Shepherd walks from his loom through that
    door straight in among his sheep (villagers open doors, not gates). High in the gable a glazed **taking-in hatch**
    with its leaf folded back, under a hoist beam whose lantern hook hangs over the fold.
- **Weaver's Cottage II** (18 x 14 x 17): a walled **dye garden** behind, through a back door from the workroom down a
  gravel walk: rows of the flowers dyes come from, one color a row (reds, oranges and yellows, blues, purples), white
  flowers under the eaves, and a **tenter frame** across the far end with cloth of those colors hung on it to dry.
- Nobody lives in it (no bed). Every piece is a furniture spot, so a builder with the furniture mods builds the mods'
  pieces in their places (37.2); with the building mods its modded copy dresses the floors and chests (37.12).
- A cottage standing in the older drawing keeps it until its owner presses **Refresh** on the hall's Repairs page
  (36.21); the loom and the chest stand in the same cells in the old and new drawings, so a refreshed cottage keeps
  exactly one loom.

## 2. How it works

- Drawn in JavaScript with the rebuild kit: `tools/blueprints/js/weavers_cottage/` (`plan.mjs`: three concepts, story,
  palette, masses, rooms, openings, every furniture footprint, the walking line; `recipe.mjs`: the passes for both
  levels), on the shared parts library `tools/blueprints/js/parts/` (`weavers.mjs`: the long mullioned window, the
  molded course, the taking-in door or hatch with its hoist, the tenter frame, rows of dye flowers, the fold's
  lean-to). `build.sh` builds both levels as candidates; `LIVE=1 build.sh` saves the live drawings through
  `tools/blueprints/rebuild.py`, which runs check.py's checks with the owner's rules, `finish()` and writes
  `blueprint_furniture/<id>.json`.
- The old Python drawing (`countryside.py`) is no longer saved by `generate.py` (the family is in its `REBUILT` set);
  `history.py` ships the old drawings in `blueprint_history/weavers_cottage*/` (and `modded/weavers_cottage*/`) for
  refreshes.
- `StarterBlueprints.WEAVERS_COTTAGE` and `WEAVERS_COTTAGE_2` carry the new sizes. Modded copies: `modded_library.py
  weavers_cottage weavers_cottage_2`. Village looks: the styles (`BlueprintStyles`) restyle the drawing at play time;
  there are no village pieces of this family.
- Level II is Level I with the garden added: 612 of Level I's 614 blocks stay as they are, so the upgrade builds only
  the garden, the back door and the tenter.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | `moddedLibrary`, `tailors` and the furniture switches apply as for every workplace. |

## 4. Saved data

Nothing new. The ids never change (`aliveworkplace:weavers_cottage`, `weavers_cottage_2`); standing cottages and sites
mid-build keep their drawing until a refresh.

## 5. Items, blocks, jobs, commands

- Blueprints: Weaver's Cottage and Weaver's Cottage II. Job block: one loom (Shepherd, or a Tailor with `tailors` on).

## 6. Decisions

- The critic's one risk: the loft's taking-in door opened onto a 4-block drop over the fold. It is now a glazed hatch
  with its single leaf (spruce trapdoors) folded back flat against the gable, so no villager can walk out of the loft.
- The kept chest at (6, 1, 3) faces west like every older drawing's; a pile of fleeces now stands at its back, so it
  stands against something (check.py's chest rule) without turning it.
- The loft's cutting table got a stool: a table with no chair took a set of crockery in the modded copy, which the
  owner's rules allow only by a kitchen or at a table with a chair.
- The footprint grew from 15 x 11 to 18 x 12: the Steward's renewal of an old Shepherd's house (`steward_renewal/
  shepherd.json`) only takes the new cottage where the old footprint plus 3 blocks a side fits it.

## 7. Known limits

- The showcase scene `rebuild_weavers_cottage` is filmed on GitHub, not in the session that shipped it.

## 8. Proof

`CountryWorkplacesGameTests.countryWorkplaceBuilt_weavers_cottage` and `_2` (sizes, no entity, one loom and no other job
block, built from its supply report, a Shepherd walks in and takes the loom), `QaNewWorkplacesGameTests` (the loom, the
pen's fences and gate, II's dye garden), `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` and
`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `ModdedLibraryGameTests`, `QaBuildReachGameTests`,
`QaFurnishedRooms1031GameTests`, `FurnitureSpotGameTests`, `QaChairFacingGameTests`, `QaRefreshHistory1028GameTests`.
Scene `rebuild_weavers_cottage`.
