# The Artisan's House

Milestone 49, item 49.8: the grade I home for Artisans (34.15), rebuilt from scratch with the rebuild kit
(`tools/blueprints/REBUILD.md`): a blind brief (`tools/blueprints/briefs/artisans_house.md`), a blind design, and a
critic who scored old and new side by side and said SHIP (`tools/blueprints/briefs/artisans_house_critic.md`: 4/4/4/4
against 3/3/2/3).
Roadmap items: 34.15, 36.7, 49.8, 49.10g

## 1. What a player sees

- **Artisan's House** (16 x 17 x 14, grade I; builders from Apprentice on sell it for 10 emeralds): the Kiln House, a
  potter's house standing gable-end to the street. A rubble-stone ground floor with dressed quoins; over it a white
  plaster floor between spruce posts, jettied a block out over the street on carved brackets, three windows with
  flower boxes on its molding and a lantern hung over the door; a steep dark-tiled roof with barge boards, a gabled
  dormer on the west slope. Beside the house stands a round brick **bottle kiln** (a wide hovel, two shoulders, a neck
  with a flared lip and two dark hoops): it is the house's chimney, its fire mouth opens into the workroom, its stoke
  hole into the yard, and a tidy log stack under a board lid leans on it either side of the stoke hole. A lean-to fuel
  shed behind it holds split logs stacked to the roof, clay and a cauldron. Flower beds either side of the step, a
  barrel of wares by the door.
  - Downstairs, the **workroom** behind the door: the potter's bench and stool under the west window, a crafting table,
    a barrel with a pot on it, the kiln's mouth in the east wall. Behind it the **hall**: a barrel and a chest on the west
    wall, a table for four on a rug, and the kitchen corner at the back (a stove, counters, a sink, a shelf of
    crockery). A straight stair up the east wall.
  - Upstairs, the **bedroom** over the street: the couple's two beds under the front windows, a nightstand each and a
    chest between them, a cupboard. Behind it the **sitting room**: a settle with arms on a rug, a writing desk and chair
    under the back window, a bookcase and a cupboard, a picture.
- **Artisan's House II** (16 x 17 x 19, grade II): the **journeyman's room**, a lower framed wing on a stone plinth at
  the back under its own steep gable, through a doorway in the hall's back wall: the third bed with a cupboard at its
  head, a nightstand, a chest, a rug and a hung lamp. Behind the shed, a **drying yard** on gravel: a rack of fired pots
  on barrels and boards, and a quench cauldron.
- **Artisan's House III** (16 x 17 x 19, 4 deep below ground, grade III): the **clay cellar** under the house, down a
  ladder under a hatch in the workroom floor: the family's chests along the front wall with barrels on them, casks of
  slip along the east wall, clay stock in the back corner, a sorting table, two lanterns. Above the ground it is II
  block for block.
- Beds: 2, 3 and 3. Every piece of furniture is a furniture spot, so a builder with the furniture mods builds the
  mods' pieces in their places (37.2); with the building mods its modded copy dresses the floors and chests (37.12).
- A house standing in an older drawing keeps it until its owner presses **Refresh** on the hall's Repairs page
  (36.21). The new drawing keeps every container, bed and job site cell of the older drawings free or the same, so a
  refresh never has to move one (the old loom's cell in the hall is plain floor).

### Looks by village

Every level is drawn again for each village culture (49.10g), and a village builds the look of its own culture
(plains as drawn); where that look doesn't fit the plot, the others are tried after it. The rooms, the beds, the job
blocks, the chests and barrels, the doors, every furniture spot, the size and Level III's clay cellar are the same in
every look; only the walls, roofs, kiln and yard change. The Blueprint Table lists the looks.

| Village | Look |
|---|---|
| Plains | As drawn: the plaster floor jettied over the stone workroom, the steep dark gable, the brick bottle kiln. |
| Desert | A flat coped block of mud brick over coursed sandstone; the door two deep in an iwan under a red sandstone arch, a lattice bay on corbels over it, a windcatcher on the back corner, drying jars on the roof and a stepped mud-brick dome over the kiln, its smoke beside the house; a palm in II's drying yard. |
| Savanna | One great golden thatch hip with its eaves a block out all round, the upper floor on gray acacia posts over a stoep, red granite and packed mud under a painted band; the kiln a red termite mound in bands with a smoking spire. |
| Snowy | A tall white calcite gable to the road under 45-degree dark slate with snow on the ridges and lips, long-and-short quoins, a closed storm porch under its own dark gable, and a brick kiln with a stone stack above the eaves; II's small gable behind. |
| Taiga | Twin slate gables to the road: the dark notched-log house jettied on corbels over mossy fieldstone, and beside it a lower open timber barn round the kiln with a fieldstone stack; a carved horse's head on the ridge. |

## 2. How it works

- Drawn in JavaScript with the rebuild kit: `tools/blueprints/js/artisans_house/` (`plan.mjs`: three concepts, story,
  palette, masses, rooms, openings, every furniture footprint, the walking line; `recipe.mjs`: the passes for levels
  1-3), on the shared parts library `tools/blueprints/js/parts/` (the kiln is `parts/kilns.mjs`). `build.sh` builds the
  three levels as candidates; `LIVE=1 build.sh` saves the live drawings through `tools/blueprints/rebuild.py
  artisans_house <id>` (III with `--basement 4 --cellar`), which runs check.py's checks with the owner's rules,
  `finish()` and writes `blueprint_furniture/<id>.json`.
- The old Python drawing (`houses.py`, `cellars.py`) is no longer saved by `generate.py`; the old drawings ship in
  `blueprint_history/artisans_house*/` (and `modded/artisans_house*/`) for refreshes.
- `StarterBlueprints.ARTISANS_HOUSE` .. `ARTISANS_HOUSE_3` carry the new sizes. Modded copies: `modded_library.py
  artisans_house artisans_house_2 artisans_house_3`. Styles (`BlueprintStyles`, the Table's style buttons) are applied
  to the drawing at play time, as before; the kiln's chimney (a campfire on its solid brick column) stays whole in
  every style.
- The culture looks (49.10g) are drawn in `tools/blueprints/js/artisans_house_<culture>/` from the brief and the shipped
  plan (`look_spots.py` says SAME BUILDING for all four: every furniture spot, bed, container, job block and door in the
  same cells at every level; the critic passed all four in round 2, `tools/blueprints/briefs/artisans_house_looks_critic.md`).
  `SHIP=1 build.sh` saves them as `structure/looks/<culture>/artisans_house[_2, _3]` with their furniture spots (III with
  `--basement 4 --cellar`). `StarterBlueprints.ARTISANS_HOUSE_CULTURES` lists the cultures that have a look
  (`BlueprintLooks` reads it; take a culture out to hold its look back), `artisansHouseLook(culture)` each look's three
  levels (the sizes of the levels drawn) and `ARTISANS_HOUSE_LOOKS` all of them; the Level IIIs are among the basement
  builds (`WITH_BASEMENTS`). Scene: `rebuild_artisans_house_looks`.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | `villageClasses`, `moddedLibrary` and the furniture switches apply as for every grander home. |

## 4. Saved data

Nothing new. The ids never change (`aliveworkplace:artisans_house`, `artisans_house_2`, `artisans_house_3`); standing
houses and sites mid-build keep their drawing until a refresh. Its grade comes from its tier.

## 5. Items, blocks, jobs, commands

- Blueprints: Artisan's House, Artisan's House II and III (`aliveworkplace:artisans_house` .. `artisans_house_3`), sold
  by builders only.

## 6. Decisions

- The critic's notes, taken at the ship: the solid counter in the old loom's cell (5, 1, 11) is gone, so a refreshed
  older II keeps its loom there on plain floor; the logs at the kiln's foot, which read as broken steps, are two tidy
  stacks two high under a board lid, hugging the kiln's curve; the journeyman's room is a block deeper (the wing runs
  to z 17), the bed's head against a cupboard on the back wall, so the room is 3 x 4 instead of a closet.
- Left as drawn: the kiln hides part of the front gable from the east (the critic's fourth note); the kiln beside the
  gable is the house's silhouette.
- The bed in the wing stays in the cells (3, 1, 14) and (3, 1, 15), and the chest at (5, 1, 15): older drawings keep a
  bed and a chest there, and a refresh never moves those.

## 7. Known limits

- The showcase scene `rebuild_artisans_house` is filmed on GitHub, not in this session.

## 8. Proof

`BuilderGameTests.buildsArtisansHouse`, `buildsArtisansHouseII`, `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`,
`starterUpgradesKeepMostOfTheirBase`; `HomeGradeGameTests` (a bed at 5, 6, 10 counts grade I, in II grade II);
`QaGranderHomesGameTests`; `QaGranderHomes1107GameTests` (`everyBedOfTheArtisansHouseGivesItsGrade`,
`anArtisansHouseBuiltAcrossASaveAndReloadEndsFinishedWithExactlyItsMaterials`); `QaGranderHomes1107bGameTests` (the
cherry Artisan's House); `StyleGameTests.chimneysStayWholeInEveryStyle`; `CellarGameTests`, `BasementGameTests`,
`QaBasementGameTests`; `FurnishedHomesGameTests`, `FurnishedRoomsGameTests`, `FurnitureSpotGameTests`,
`FloatingFurnishingsGameTests`, `QaFurnishedRooms1031GameTests`; `B648GameTests`; `QaReleaseRefresh1028GameTests`
(`qaReleaseRefresh_artisans_house_2`, `_3`), `QaRefreshHistory1028GameTests`, `RefreshMatchesFreshBuildGameTests`.
Scene `rebuild_artisans_house`. `ArtisansHouseLooksGameTests` (culture pick at every level, fallbacks,
base/upgrade/lock, cellar and map kind per look, sizes, beds, job blocks and furniture spots per level, each look's III
its II over the cellar, Level I of each look built to DONE, the taiga III's cellar dug under a finished taiga II, a desert
site saved mid-build reloads as desert). Scene `rebuild_artisans_house_looks`.
