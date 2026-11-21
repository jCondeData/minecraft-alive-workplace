# The Farmstead

Milestone 49, item 49.9: the Farmer's workplace (27.14) and its cellared Level III (36.7), rebuilt from scratch with the
rebuild kit (`tools/blueprints/REBUILD.md`): a blind brief (`tools/blueprints/briefs/farmstead.md`), a blind design, and
a critic who scored old and new side by side (silhouette, depth, interior and detail 4/4/4/4 against 3/3/2/3) and,
after one slip in the code was fixed, said SHIP (`tools/blueprints/briefs/farmstead_critic.md`).
Roadmap items: 27.14, 36.7, 49.9, 49.10e

## 1. What a player sees

- **Farmstead** (15 x 13 x 12; in every new village's starting set; the Steward builds one for a Farmer with no
  composter): "the connected farm". A limewashed house in a dark oak frame that stands a block proud of the walls, on a
  cobble plinth, under **one catslide roof** of dark slate: short and steep on the west, where a cobble chimney stack
  climbs the tall wall, and a long low sweep down to head height on the east, over a boarded **gallery** along the
  field. A little pent roof runs across the front over the door and the two front windows, with flowers under them and
  a lantern at the door.
  - Inside, the **hall**: the hearth in the west wall on a hearthstone, a table for two by it, the box bed in the back
    corner with its nightstand, the harvest chest, a cupboard and a clock, a lamp on a side table by the door, two lamps
    hung from the tie beams, a rug. The field side has a window in each bay between its posts.
  - Under the eave, the **gallery**: the Farmer's composter (his job block) and his harvest chest, a step from the
    farmland, so he works from under his own roof.
  - The **field**: 9 x 5 of farmland round a water channel, rows of wheat, carrots and potatoes, a scarecrow standing in
    the channel, a fence on a cobble curb with a lantern on its corner posts.
- **Farmstead II** (15 x 15 x 22): the same roof line carried on, two blocks higher, over a **cross passage** (a door
  each end) into a **red barn**: the second Farmer's chamber under the hay loft (bed, nightstand, his own chest), two
  stalls with gates, troughs and hay down the east wall, a tool wall, the loft up a ladder, wagon doors in the back
  gable with a hay door and hoist over them, a louvered cupola on the ridge. A steep little cross gable looks out over
  the gallery at the second composter, and a second field runs beside the barn.
- **Farmstead III** (15 x 15 x 22, 4 deep below): a **root cellar** under the house, down a ladder under a hatch just
  inside the front door: three chests on the west wall, racks of pumpkins and melons under board shelves, hay at the
  back, two lamps.
- Every piece of furniture is a furniture spot, so a builder with the furniture mods builds the mods' pieces in their
  places (37.2); with the building mods its modded copy dresses the floors and chests (37.12).
- A Farmstead standing in the older drawing keeps it until its owner presses **Refresh** on the hall's Repairs page
  (36.21). The composters, the chests and the bed stand in the same cells in the old and new drawings, so a refreshed
  Farmstead keeps exactly one composter (two in II).
- **In its village's culture** (49.10e): a desert village builds every level in mud brick on a sandstone foot under
  flat coped roofs, with a dovecote tower and domed grain bins, a palm-rib shade along the field and a shaduf by its
  water; a savanna village under two golden thatch hips with no gable on acacia posts, the walls limed with a red band
  over a terracotta plinth, the barn a taller thatched grain house behind; a snowy village in pale calcite on stone
  brick under a half-hipped dark slate roof with snow on its lips and ridge and a stone stack against a white gable
  (its roof stands taller: 15 x 15 x 12, then 15 x 18 x 22); a taiga village in spruce logs notched at the corners on
  mossy fieldstone, a slate gable to the road and a covered gallery down the field. The rooms, beds, composters, chests,
  doors, Level III's root cellar (its ladder, chests and lamps in the same cells) and every furniture spot are the
  same, and each field is crops on watered farmland (the taiga look lays its channel differently: 40 farmland at
  Level I, 80 at II). Plains villages build the Farmstead as drawn. The Blueprint Table lists the looks.

## 2. How it works

- Drawn in JavaScript with the rebuild kit: `tools/blueprints/js/farmstead/` (`plan.mjs`: three concepts, story,
  palette, masses, rooms, openings, every furniture footprint, the walking lines; `recipe.mjs`: the passes for all three
  levels), on the shared parts library `tools/blueprints/js/parts/` (the catslide roof, the field plot, the curb fence,
  the scarecrow, the cellar and its hatch ladder). `build.sh` builds the three levels as candidates; `LIVE=1 build.sh`
  saves the live drawings through `tools/blueprints/rebuild.py` (III with `--basement 4` and its cellar box), which
  runs check.py's checks with the owner's rules, `finish()` and writes `blueprint_furniture/<id>.json`.
- The old Python drawings (`countryside.py`'s `farmstead`, `farmstead_2`, `cellars.py`'s `farmstead_3`) are no longer
  saved by `generate.py` (the family is in its `REBUILT` set); `history.py` ships the old drawings in
  `blueprint_history/farmstead*/` (and `modded/farmstead*/`) for refreshes.
- `StarterBlueprints.FARMSTEAD`, `FARMSTEAD_2` and `FARMSTEAD_3` carry the new sizes. Modded copies:
  `modded_library.py farmstead farmstead_2 farmstead_3`. Village looks: the styles (`BlueprintStyles`) restyle the
  drawing at play time; there are no village pieces of this family.
- The field keeps its old cells (farmland x 9 to 13, z 2 to 10), so the Farmer's field, the tests' pins and the
  Steward's renewal of an old Farmer's house (`steward_renewal/farmer.json`) work as before; Level I is one block
  shallower than the old drawing, so it fits every old plot the old one did.
- Level III is Level II with the cellar: the only change above ground is the hatch over the ladder.
- The culture looks (49.10e) are drawn in `tools/blueprints/js/farmstead_<culture>/` from the brief and the shipped
  plan (`look_spots.py` says SAME BUILDING: every furniture spot, the beds, chests, composters, doors and ladder in the
  same cells at every level); `SHIP=1 build.sh` saves them as `structure/looks/<culture>/farmstead[_2|_3]` with their
  furniture spots, Level III with `--basement 4` and its cellar box. `StarterBlueprints.FARMSTEAD_CULTURES` lists the
  cultures that have a look (`BlueprintLooks` reads it; take a culture out to hold its look back),
  `farmsteadLook(culture)` each look's three levels (the snowy look's heights 15, 18, 18) and `FARMSTEAD_LOOKS` all of
  them; the Level III looks are in `WITH_BASEMENTS`. Critic: `tools/blueprints/briefs/farmstead_looks_critic.md`
  (savanna and taiga in round 1, desert in round 2, snowy in round 3). Scene: `rebuild_farmstead_looks`.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | `moddedLibrary` and the furniture switches apply as for every workplace. |

## 4. Saved data

Nothing new. The ids never change (`aliveworkplace:farmstead`, `farmstead_2`, `farmstead_3`); standing Farmsteads and
sites mid-build keep their drawing until a refresh.

## 5. Items, blocks, jobs, commands

- Blueprints: Farmstead, Farmstead II and Farmstead III. Job blocks: one composter (Farmer) in I, two in II and III.

## 6. Decisions

- The critic's round 2 found the house's wall plates saved as a block name no builder can place (a code slip in
  `recipe.mjs`); the lane fixed it at all six places and counted the scores as SHIP.
- The critic's optional note, more rhythm on Level I's field side: a window now stands in each bay of the east wall,
  with dark oak posts between them in the wall plane (the gallery's deck leaves no room for posts a block out). No kept
  cell moved.
- The lamp by the door stands on a side table (a slab) beside the kept chest at (6, 1, 3), not on the chest.

## 7. Known limits

- The showcase scenes `rebuild_farmstead` and `rebuild_farmstead_looks` are filmed on GitHub, not in the sessions that
  shipped them.

## 8. Proof

`CountryWorkplacesGameTests` (its generated tests for `farmstead` and `farmstead_2`: sizes, no entity, the composters,
built from its supply report, the Farmers walk in and take their composters, and I's Farmer takes on the field,
harvests and stores), `QaNewWorkplacesGameTests.qaFarmsteadHoldsItsFieldAndBed`, `CellarGameTests`,
`BasementGameTests`, `QaBasementGameTests`, the build reserve tests, `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`,
`ModdedLibraryGameTests`, `QaFurnishedRooms1031GameTests`, `QaRefreshHistory1028GameTests`. Scene `rebuild_farmstead`.
`FarmsteadLooksGameTests` (culture pick, fallbacks, base/upgrade/lock/basement and map kind per look, sizes, beds,
chests, composters and every furniture spot of the drawing, the watered field of crops, Level III's cellar under its own
Level II, Level I of each look built to DONE, the snowy Level III built over the snowy Level II, a desert site saved
mid-build reloads as desert). Scene `rebuild_farmstead_looks`.
