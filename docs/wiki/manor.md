# The Noble's Manor

Milestone 49, item 49.7: the grade III home for Nobles (34.16), and where the Noble's Ball is held, rebuilt from
scratch with the rebuild kit (`tools/blueprints/REBUILD.md`): a blind brief (`tools/blueprints/briefs/manor.md`), a
blind design, and a critic who scored old and new side by side (2/2/2/2 against 4/4/4/4) and said SHIP
(`tools/blueprints/briefs/manor_critic.md`).
Roadmap items: 34.16, 34.7, 26.15, 49.7, 49.10f

## 1. What a player sees

- **Noble's Manor** (21 x 18 x 17, grade III; builders sell it once the village has Burghers): a brick "hall and
  cross-wing" house dressed in cut stone at every edge, under dark slate tiles. In the middle of the front a
  three-stage **porch tower** under a steep pyramid roof, the front door under a tiled hood, the family's blue banners
  and a datestone over the study window. To the west a tall **cross-wing** with crow-stepped gables front and back, a
  stone bay window on the parlor, the kitchen chimney stack up its side and two stone-framed oriels on the chambers. The
  long, lower **hall range** between them ends in a crow-stepped east gable. Long-and-short quoins, a stone plinth and
  a string course at the floor line all round; flower beds in front, a paved terrace behind with a rain butt.
  - Through the porch (two benches) and an arch under a dark oak beam, the **hall**: a black-and-white chequered
    floor, the grand stair rising along the front wall right inside the arch, the hearth in a stone surround on the
    back wall with two armchairs, and the dining table for nine at the east end.
  - In the west wing, the **parlor** (a couch, a tea table and two armchairs, the bay window to the road) and the
    **kitchen** (the cooking hearth in the west wall at the foot of the stack, a furnace, counters, a sink, shelves of
    crockery, the pantry of barrels and a chest, a work table for three).
  - Up the grand stair, the **Long Gallery** in the hall's roof (a couch, bookcases, a reading table, the clock), the
    **study** in the tower over the porch (a desk at the front window), the **front chamber** (a single bed between
    two nightstands, a wardrobe, a reading chair in the oriel) and the **great chamber** (the Nobles' double bed, two
    chests, a wardrobe, a desk at the back window, a reading chair and a bookcase by its oriel).
- **Manor II** (21 x 18 x 25, grade III): a **walled garden** behind the hall: brick walls on a stone foot with
  stone piers and lanterns on the corner piers, four box-edged knots of tulips round a lamp at the cross, clipped yews,
  and a bench at the end of the walk. **Its garden counts 3 beauty for the people who live there**, a Noble's beauty
  need on its own.
- **Manor III** (29 x 18 x 25, grade III): the **east cross-wing**, taller and wider than the west, completing the
  front: the **ballroom**, one double-height room open to a boarded ceiling under the roof, a parquet floor, a great
  stone-mullioned cross-window to the front and three tall windows between buttresses down the east side, couches
  along the walls, lanterns on stone brackets and two chandeliers, a jukebox and a candlelit sideboard; a **musicians'
  gallery** over its back end (three chairs, note blocks) reached from the Long Gallery; double doors from the hall
  and glass doors to the garden, which runs on behind the ballroom with two rose beds. **The Noble's Ball is held in
  the ballroom** (its floor's middle, `NobleBalls.BALLROOM`, is (23, 1, 8) from the origin).
- **Manor IV** (29 x 18 x 25, 4 deep below ground): a vaulted **wine cellar** under the kitchen and the hall's west
  end, down a hatch ladder from the kitchen: casks along both end walls (two high in the middle), the family's chests
  between decorated pots on the back wall, a spine wall with two doorways, two lanterns.
- Beds: 3 in every level. Every piece is a furniture spot, so a builder with the furniture mods builds the mods' pieces
  in their places (37.2); with the building mods its modded copy dresses the floors and chests (37.12).
- A Manor standing in an older drawing keeps it until its owner presses **Refresh** on the hall's Repairs page (36.21);
  with this redraw a refresh is close to a whole rebuild. The new drawing keeps the cells where the older Manors kept
  their chests, barrels, beds, the jukebox and the cellar's pots free of doors and stairs.

- **In its village's culture** (49.10f): every level is drawn again for each village culture. A desert village builds
  the Qasr of the Windcatcher (flat sandstone and mud-brick masses at three heights behind crenellated parapets, the
  porch tower a slotted windcatcher with a lattice oriel, a palm garden round a fountain at II, a clerestory lantern on
  the ballroom at III); a savanna village the Great House under One Thatch (one broad golden hip over the whole house,
  a colonnade of acacia posts under its eaves, red granite below packed mud, the tower rising through the thatch under
  its own steep pyramid); a snowy village the White Hall (pale calcite with gray dressed stone under one steep slate
  roof, the wings standing forward as tall pointed gables, snow on the ridges and eaves, a closed storm porch); a taiga
  village the Boyar's Lodge (dark spruce logs on a mossy fieldstone footing under broad slate roofs, verandas on log
  posts, two fieldstone stacks). The rooms, beds, doors, the grand stair, the ballroom, the cellar and every furniture
  spot are in the same cells, and each look is the same size as its level, so the Noble's Ball is held in any look's
  ballroom. Plains villages build the brick Manor. The Blueprint Table lists the looks.

## 2. How it works

- Drawn in JavaScript with the rebuild kit: `tools/blueprints/js/manor/` (`plan.mjs`: three concepts, story, palette,
  masses, rooms, openings, every furniture footprint, the walking line, the venues; `recipe.mjs`: the passes for levels
  1-4), on the shared parts library `tools/blueprints/js/parts/`. `build.sh` builds the four levels as candidates;
  `LIVE=1 build.sh` saves the live drawings through `tools/blueprints/rebuild.py manor <id>` (IV with `--basement 4
  --cellar`), which runs check.py's checks with the owner's rules, `finish()` and writes `blueprint_furniture/<id>.json`.
- The old Python drawing (`houses.py`, `basements.py`) is no longer saved by `generate.py`; `history.py` ships the old
  drawings in `blueprint_history/manor*/` (and `modded/manor*/`) for refreshes.
- `StarterBlueprints.MANOR` .. `MANOR_4` carry the new sizes; the ball's venue in a Manor I or II is still the middle
  of its declared size, (10, 1, 8) in I and (10, 1, 12) in II, both on the hall's chequer. Modded copies:
  `modded_library.py manor manor_2 manor_3 manor_4`. Village looks: the styles (`BlueprintStyles`) are applied at play
  time, as before; the two chimneys (a campfire on each stack's solid column) stay whole in every style.

- The culture looks (49.10f) are drawn in `tools/blueprints/js/manor_<culture>/` from the brief and the shipped plan
  (they draw its rooms and furniture in with `parts/noble_house.mjs`; `look_spots.py` says SAME BUILDING for all 16);
  `SHIP=1 build.sh` saves them as `structure/looks/<culture>/manor[_2.._4]`, IV with the same 4-deep cellar. The critic
  passed all four (`tools/blueprints/briefs/manor_looks_critic.md`: snowy in round 3, taiga in round 2).
  `BlueprintLooks` lists the Manor by culture; `StarterBlueprints.MANOR_DESERT`, `_SAVANNA`, `_SNOWY` and `_TAIGA` list
  each look's levels, and their Level IVs are among `WITH_BASEMENTS`. The Noble's Ball finds a look by its family
  (`StewardConditions.family`), and every look keeps the ballroom's middle at (23, 1, 8). Scene: `rebuild_manor_looks`.
- At the ship step the savanna hall's front windows got a lintel over their deep inner reveal: the hall's blinds hang
  there, and with the reveal open to the top they had nothing to hang on (furniture check).

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | `villageClasses`, `moddedLibrary` and the furniture switches apply as for every grander home. |

## 4. Saved data

Nothing new. The ids never change (`aliveworkplace:manor` .. `manor_4`); standing Manors and sites mid-build keep their
drawing until a refresh. Its grade and garden still come from `homes/manor.json`.

## 5. Items, blocks, jobs, commands

- Blueprints: Noble's Manor, Manor II, III and IV (`aliveworkplace:manor` .. `manor_4`), sold by builders only.

## 6. Decisions

- The critic's three optional notes, taken at the ship step: the inner partitions are dark oak paneling (a white
  terracotta one read as a stray peach slab); the plain west flank gets two stone-framed oriels on the chambers under
  the eave; each upstairs chamber gets a wardrobe and a reading chair (the great chamber a bookcase with a fern too).
- The ballroom's double doors from the hall are at z 7-8, not 8-9: at 8-9 the dining table's end chair stood in their
  way (check.py's door rule). The ballroom's wall lanterns hang from a stone block out of the wall, not a fence arm (a
  fence is no ceiling for a hanging lamp) and not a stair (the game drops a lantern under a stair's open half).
- The ballroom's sideboard is two candlelit desks, not tables: the modded library sets plates on every table, and plates
  go only on a kitchen or dining table. A stone threshold slab outside the garden door keeps the modded library's pot
  beside the rain butt out of the doorway.
- `NobleBalls.BALLROOM` moved from (20, 1, 11) to (23, 1, 8), the new ballroom floor's middle.

## 7. Known limits

- The showcase scene `rebuild_manor` is filmed on GitHub, not in this session.
- An older Manor refreshed keeps its own chest at (13, 6, 8) in the Long Gallery, its smoker at (15, 1, 8) in the hall
  and its barrels at (15, 6, 14) and, in III, (11, 5, 3), which now stand in the gallery, the hall, the back eave and the
  tower's wall: a refresh never moves a container or a job block (36.21).

## 8. Proof

`ManorGameTests` (a bed in the great chamber, 3, 6, 8, counts grade III, the garden 3 from II; the ball in the
ballroom's middle, turned too), `QaMirroredManorBallGameTests`, `DoorwayHeadroomGameTests` (in by the porch tower's
door and onto the grand stair's first step), `QaGranderHomes1107GameTests.everyBedOfTheManorGivesItsGrade`,
`QaGranderHomes1107bGameTests`, `BuilderGameTests.buildsManor`, `buildsManorII`, `buildsManorIII`,
`starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `StyleGameTests.chimneysStayWholeInEveryStyle`,
`QaChairFacingGameTests.turningOneOfTheManorsChairsRoundIsCaught`, `FurnishedHomesGameTests`, `QaBuildReachGameTests`,
`B648GameTests`, the basement and refresh tests. Scene `rebuild_manor`. `ManorLooksGameTests` (culture pick at every
level, fallbacks, base/upgrade/lock/basement and the ball's family per look, sizes, beds and job blocks, Level I of each
look built to DONE, a desert site saved mid-build reloads as desert). Scene `rebuild_manor_looks`.
