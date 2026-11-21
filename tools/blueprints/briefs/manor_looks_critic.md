# Manor biome looks: the critic's scorecards (49.10f, round 1, a fresh critic subagent, 2026-11-19)

OLD = the live as-drawn Manor (the rebuilt plains Manor, scored 4/4/4/4 in `manor_critic.md`); NEW = each look's
candidate (sheets `manor_looks/<culture>_<1..4>_vs_old.png`, single renders and cutaways `build/rebuild/mn_r1/shots/`).
Designers' checks: look_spots.py SAME BUILDING for all 16 (every spot, bed, container, job block, door in place);
holes.py 0 findings on all 16. Standard (as for the Terrace and Townhouse looks): a look ships when each score is at
least the as-drawn level's and it clearly reads as its culture.

OLD: Silhouette 4, stepped Dutch gables and the taller middle roof read from every side (old_*_front, back); Depth 4,
quoins, string courses, window surrounds (front); Interior 4, the shipped spots, III's ballroom, IV's cellar; Detail 4,
disciplined palette, walled garden beds (back), though the top view shows orange slits where the roof courses meet.

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| OLD (as drawn) | 4 | 4 | 4 | 4 | - | - |
| Desert | 4 | 4 | 4 | 3 | yes: sandstone and mud brick, flat parapeted decks, windcatcher, rawshans, pool court | BACK |
| Savanna | 4 | 4 | 4 | 3 | yes: golden thatch hips, mud walls on acacia posts, painted bands, tall mud stacks | BACK (narrow) |
| Snowy | 3 | 4 | 4 | 3 | yes: pale calcite and stone, dark steep roofs, snow caps; clearly not taiga | BACK |
| Taiga | 4 | 4 | 4 | 3 | yes: dark spruce logs, cobble base, plank frame, fieldstone stacks; clearly not snowy | BACK |

Interior is judged from the cutaways and look_spots (the same building as OLD in every look): desert and savanna
(`*_4_cut4/cut9`) show the pool court, the furnished hall and the ballroom; snowy and taiga (`*_3_cut4/cut8`) every
room furnished. The desert and savanna cutaways are framed off to one corner; reframe them next round.

## Desert (BACK)
- Silhouette 4: the windcatcher tower over parapeted decks is a strong outline (desert_1/2 front, left); weakest L3/L4
  left, where the ballroom's parapet runs down in a diagonal.
- Depth 4: proud pilasters on every face, rawshans, a ground-floor plinth band (front, right), if anything too much.
- Interior 4: pool court and garden, furnished hall and ballroom (desert_4_cut4, cut9).
- Detail 3: the front reads as a cage of free-standing sandstone posts, the parapet is ragged, a slab floats.

Notes:
1. L1/L2 front (desert_1_front, desert_2_front): the entry tower stands behind a cage of sandstone posts and a cross
   beam at the parapet line, with sky between the posts, the beam and the mud-brick tower, and loose mud-brick steps in
   the tower face under the beam. Make the posts part of the tower's face (no gap behind them) or stop them at the
   parapet, and drop the cross beam; the tower should read as one solid shaft up to its windcatcher.
2. L3/L4 left and front (desert_3_left, desert_4_left, desert_3_front top left): the ballroom's parapet top steps down
   in a ragged diagonal at the back-left corner, which reads as a ruin. One level parapet with a straight coping (and
   crenels if wanted) all round the ballroom.
3. Every level, every face (desert_1_right, desert_3_right): the pilasters run past the parapet as free fins. End
   each one flush with the coping, or cap each with one block, the same on every face.
4. L1/L2 front (desert_1_front, left of the middle rawshan, about x 7-8, y 6): a single sandstone slab hangs on the
   wall by itself. Remove it or make it a full sill under the rawshan.
5. (optional) Windcatchers: the slots are only a block deep and read as painted stripes from the front; open them
   two deep or give them a lattice.

## Savanna (BACK, narrow)
- Silhouette 4: thatch hips, a pyramid-capped entry tower and tall mud stacks read from every side (savanna_3_right,
  savanna_1_back).
- Depth 4: acacia posts proud of the mud walls, eaves out all round, awnings over the ground windows (front).
- Interior 4: the court with its tree and pool, furnished rooms (savanna_4_cut4, cut9); the ballroom open to the roof,
  as the designer says, is acceptable for thatch but light it from the walls.
- Detail 3: the thatch is ragged where it meets the tower and at the west wing's front hip.

Notes:
1. L1-L4 top and front (savanna_1_top, savanna_3_top, savanna_1_front): where the side hips meet the entry tower the
   thatch stops in a stepped bite and the tower's painted wall tops show through it (the orange-and-white steps in the
   top view; the painted chip under the thatch at the tower's top left in the front view). Carry the hips into the
   tower in full courses, or give the tower its own eave all round above them, so no wall top shows.
2. L1-L4 left (savanna_1_left, front-left hip): the west wing's front hip end is a lumpy pile of stair steps with
   stairs hanging off its foot. One clean hip line down to the eave.
3. L1/L2 front (savanna_1_front, left bay): the two awnings over the left ground-floor window and door sit one above
   the other, offset, and read as a broken pent; one awning per opening at one height. Remove the two stray packed-mud
   blocks over the west veranda roof (x 8, y 6-7, z 2-3) the designer flagged.
4. L1-L4 front right (savanna_1_front): the upper windows are cut up into the right hip's eave with the thatch notched
   round them; drop them under the eave or give each a small thatched dormer.

## Snowy (BACK)
- Silhouette 3: steep dark roofs with snow caps read as snow country, but the tower is a pale box with a blunt 45-degree
  pyramid behind its flat top (snowy_1_front), and the front gables are jagged (snowy_3_front).
- Depth 4: stone quoins and window surrounds, deep shutters, the snow eave course (front, right).
- Interior 4: every room furnished (snowy_3_cut4_top, cut8_top), the walled garden (back).
- Detail 3: palette disciplined, snow only on ridges and eaves; the gables and the back rakes are ragged.

Notes:
1. L1-L4 front (snowy_1_front, snowy_3_front): the white gable walls step two blocks a column inside a single-step
   dark rake, so every front gable shows a jagged dark fringe of stair notches round a stepped pale triangle. Make the
   gable and the rake one slope: either a steep stair verge whose steps the wall follows exactly, or a stepped gable with
   a coping on every step (no dark stair pieces beside it).
2. L1-L4 front (snowy_1_front, snowy_2_front): the tower ends in a flat pale top with the pyramid rising behind it, so it
   reads as a box in front of a roof. Give the tower its own steep roof (a spire or a steep pyramid whose eaves sit on
   the tower's walls), snow on its tip.
3. L1-L4 back (snowy_1_back, between the two stacks): the back gable's rakes end in loose dark stairs hanging past the
   eave beside the left stack. One stair verge down each rake onto the eave course.
4. L3/L4 front (snowy_3_front, between the left gable and the tower): the valley steps down in single blocks; close it
   in full stair courses.

## Taiga (BACK)
- Silhouette 4: twin front gables, a pyramid-roofed tower and fieldstone stacks (taiga_3_front, taiga_1_back); weakest
  L1 front, where the tower is a tall blank log box.
- Depth 4: plank frame and porch pents on posts, a cobble plinth, outside stacks (front, right).
- Interior 4: every room furnished (taiga_3_cut4_top, cut8_top), the fenced garden beds (back).
- Detail 3: ragged gable rakes front and back; the all-dark walls under the dark roof flatten the shape.

Notes:
1. L1-L4 back (taiga_1_back): both rakes of the back gable step in single blocks with stair pieces hanging off at the
   eave beside each stack, and log ends show above the rake. A solid stair verge down each rake, bargeboard flush, the
   log wall stopping under it.
2. L3/L4 front (taiga_3_front, left gable's right rake and the valley into the tower): the log wall steps up past the
   stairs and the valley is a run of single-block notches. Close both in full courses.
3. L1-L4 front (taiga_1_front): the tower's upper face is a tall blank wall of dark logs. Carry the plank frame (a band
   at the floor line, corner posts) and a second window up it, as on the gables.
4. (optional) Corners are upright log posts, not notched log ends; notched ends at the main corners would say "log
   house" from twenty blocks, which the flat dark walls don't yet.

## Round 2 (a fresh critic subagent, 2026-11-19)

Sheets `manor_looks/<culture>_<1..4>_r2_vs_old.png`, singles `build/rebuild/mn_r2/shots/` (old: `mn_r1/shots/old_*`),
each round-1 note checked in zoomed crops of the same view r1 against r2. Designers' checks: look_spots SAME BUILDING
and holes.py 0 on all 16. The desert and savanna cutaways are now reframed (`*_4_cut4_0_89`, `cut9_0_89`): every room
furnished, pool court and ballroom in view, so Interior stays 4 everywhere.

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| OLD (as drawn) | 4 | 4 | 4 | 4 | - | - |
| Desert | 4 | 4 | 4 | 3 | yes | BACK |
| Savanna | 4 | 4 | 4 | 3 | yes | BACK (narrow) |
| Snowy | 4 | 4 | 4 | 3 | yes; clearly not taiga (pale walls, snow caps, copper spire) | BACK |
| Taiga | 4 | 4 | 4 | 4 | yes; clearly not snowy (log walls, stripped-log frame, fieldstone) | SHIP |

### Desert (BACK)
- Silhouette 4: windcatcher over level parapets (desert_3_left, desert_3_back). The ballroom parapet is now level with
  its coping; the diagonal left in desert_3_left is a stepped wedge behind the parapet, rising to the windcatcher's
  lip, not the parapet itself. It reads far less like a ruin.
- Depth 4: unchanged, pilasters and rawshans on every face (desert_1_right).
- Interior 4: desert_4_cut4_0_89 shows pool court, hall and ballroom furnished.
- Detail 3: the floating slab is gone (note 4 answered), but notes 1 and 3 are not.

Notes:
1. L1/L2 front (desert_1_front, the entry tower at the parapet line): unchanged from round 1. Sky still shows
   between the posts, the cross beam and the mud-brick shaft (two gaps, left and middle, just under the beam), and the
   loose mud-brick steps under the beam are still there. Fill behind the posts so the tower is one solid shaft up to its
   windcatcher, and drop the beam or make it a full lintel with wall behind it.
2. Every level (desert_1_right, desert_1_front corners): the pilasters still stand about a block above the merlons as
   bare fins, as in round 1. End each flush with the coping, or give each the same one-block cap on every face.
3. L3/L4 left (desert_3_left, behind the ballroom parapet next to the windcatcher): the stepped sandstone wedge that
   climbs to the windcatcher reads as a slope seen over a flat roof. Make the lantern or stair head a box with a level
   top (or hide it below the parapet).

### Savanna (BACK, narrow)
- Silhouette 4: thatch hips, pyramid-capped tower, tall stacks (savanna_3_left, savanna_1_back); the west wing's front
  hip is now one clean line (savanna_1_left, note 2 answered).
- Depth 4: posts proud of mud walls, deep eaves (savanna_1_front).
- Interior 4: savanna_4_cut4_0_89 / cut9: court with tree and pool, furnished hall and bedrooms.
- Detail 3: the left bay now has one awning and its upper window sits under the eave (note 3 answered); the tower's
  painted wall tops are almost all under thatch (note 1 mostly answered). But the right bay's fix added a new fault.

Notes:
1. L1-L4 front, right bay (savanna_1_front, savanna_3_front): two orange blocks stand on the awning, in front of the
   upper windows, and cover their lower half; only a sliver of glass shows above them. They read as stray lumps. Take
   them out (or make each a one-block sill below its window, at most a slab high) so both upper windows show whole.
   Do the same check on the left (west) face, where the same lumps sit in front of the upper windows (savanna_1_left).
2. L1-L4 (savanna_1_left, tower top left under its thatch; savanna_1_top, beside the tower): one pale painted block
   still shows below the tower's eave. Carry the thatch or a packed-mud course over it.

### Snowy (BACK)
- Silhouette 4: the tower now has its own roof with its eave on the tower walls (note 2 answered), so the front reads as
  a tower, not a box in front of a roof (snowy_1_front, snowy_3_front).
- Depth 4: quoins, surrounds, shutters, the snow eave course (snowy_1_right).
- Interior 4: unchanged, every room furnished (round 1's snowy_3 cutaways; same building per look_spots).
- Detail 3: notes 1, 3 and 4 are not answered.

The spire: waxed oxidized copper is right for a Nordic or Baltic manor and keeps the look clear of taiga. Its shape is
not: a stepped 45-degree pyramid with a 2x2 copper column on top reads as a bottle (snowy_3_front zoom).

Notes:
1. L1-L4 front and back gables (snowy_3_front left gable, snowy_1_back): still a pale stepped triangle inside a
   dark stepped rake, now with a one-block pale column going up into the apex, so the jagged dark fringe is still there
   and the column looks like a seam. Make the wall and the rake one line (the pale wall stops exactly under each stair,
   or a stepped gable with coping on every step and no dark stairs beside it).
2. L1-L4 tower (snowy_1_front): make the spire steeper and thinner: copper stairs in steeper courses up to a single
   1x1 finial (a lightning rod or a copper slab), not a 2x2 column on a ziggurat. Keep the snow on its tip.
3. L1-L4 back (snowy_1_back, right rake by the stack): the rake still ends in loose dark steps at the eave beside the
   stack. Run one stair verge onto the eave course.
4. L3/L4 front (snowy_3_front, between the left gable and the tower): the valley still steps in single blocks
   (unchanged, as the designer says). Close it in full stair courses.

### Taiga (SHIP)
- Silhouette 4: twin gables, the pyramid-roofed tower, fieldstone stacks (taiga_3_front, taiga_1_back).
- Depth 4: the tower now has a stripped-log frame (corner posts, a band at the floor line) and a second window
  (taiga_1_front, note 3 answered); stripped-log corner posts on the bays break up the dark walls.
- Interior 4: unchanged, every room furnished.
- Detail 4: the back rakes are full stair courses with no log ends above them and nothing hanging at the eave
  (taiga_1_back, note 1 answered); the left front gable's log wall stops under its rake (taiga_3_front).

Follow-up (does not hold the ship): the L3/L4 valley between the left gable and the tower still steps in single blocks
(taiga_3_front); close it in full courses when the snowy look's valley is fixed, since it is the same part.

## Round 3 (a fresh critic subagent, 2026-11-19, the last round)

Sheets `manor_looks/<culture>_<1..4>_r3_vs_old.png`, singles `build/rebuild/mn_r3/shots/` (old: `mn_r1/shots/old_*`;
the shipped taiga: `mn_r2/shots/taiga_*`). Every round-2 note checked in zoomed crops of the same view r2 against r3.
Designers' checks: look_spots SAME BUILDING and holes.py 0 on all 12. Interior stays 4 everywhere (same building;
desert and savanna `*_4_cut4_0_89`, `cut9_0_89`).

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| OLD (as drawn) | 4 | 4 | 4 | 4 | - | - |
| Desert | 4 | 4 | 4 | 4 | yes: sandstone and mud brick, level parapets, windcatcher, rawshans, pool court | SHIP |
| Savanna | 4 | 4 | 4 | 4 | yes: golden thatch hips, mud on acacia posts, painted bands, tall mud stacks | SHIP |
| Snowy | 4 | 4 | 4 | 4 | yes; clearly not taiga (calcite and stone walls, snow caps, copper spire vs taiga's log and plank) | SHIP |

### Desert (SHIP)
- Silhouette 4: the windcatcher is now one level-topped sandstone shaft with two slots (desert_1_right, desert_2_front);
  the L3/L4 lantern behind the ballroom parapet is a box with a level top (desert_3_left), note 3 answered. The
  one-course step from the coping (y15) to the lantern top (y16) reads as a step, not a slope.
- Depth 4: pilasters and rawshans on every face (desert_1_right).
- Interior 4: unchanged.
- Detail 4: the pilasters now stop at the coping with merlons on them on every face (desert_1_right, note 2 answered);
  the entry tower is sandstone faced with a full lintel where the cross beam was, and L2's front shows no sky
  (desert_2_front), note 1 answered in the main.

Follow-up (does not hold the ship): desert_1_front, under the coping left of the middle post: two small slits of sky and
two loose mud-brick steps still show behind the front wall at L1 (gone at L2). Fill them when the family is next touched.

### Savanna (SHIP)
- Silhouette 4: thatch hips, pyramid-capped tower with its pot, tall stacks (savanna_3_left, savanna_1_back).
- Depth 4: posts proud of mud walls, deep eaves, verandas and the bay hood (savanna_1_front).
- Interior 4: unchanged.
- Detail 4: the pale painted block under the tower's eave is now mud (savanna_1_left, savanna_1_top), note 2 answered;
  the mud fills on the tower's sides and back read as the tower wall carried out a block under its eave, which is fine.

Follow-up (does not hold the ship): round-2 note 1 is not answered. The two orange "lumps" in front of the west
chamber's upper windows (seen at the right of savanna_1_front) are the window sills, top-half acacia stairs at
x 3 and 5, y 6, z 0, sitting on the bay's thatch hood; from the camera they hide the lower half of each window. Swap
each for a bottom acacia slab (or drop it) so the glass shows whole.

### Snowy (SHIP)
- Silhouette 4: twin gables, the copper-roofed tower and the stacks still read from every side (snowy_3_front,
  snowy_1_back); every roof is now 45 degrees, but calcite walls, stone dressings, snow-capped ridges and the copper
  spire keep it clearly apart from the shipped taiga (taiga_3_front against snowy_3_front: log and plank against pale
  stone).
- Depth 4: quoins, surrounds, shutters, the snow eave course (snowy_1_right).
- Interior 4: unchanged.
- Detail 4: the front gables' pale wall now stops under each stair, so wall and rake read as one line (snowy_3_front
  left gable, note 1 answered); the back's right rake runs one stair verge onto the eave (snowy_1_back, note 3
  answered); the L3/L4 valley into the tower is now a smooth slope (snowy_3_front, note 4 answered). The spire now
  ends in a 1x1 lightning rod (note 2's finial), though it is still a 45-degree stepped pyramid, not steeper.

Follow-up (does not hold the ship): give the spire's top course snow or a steeper last course or two, so it reads less
like a ziggurat; the small dark eave step at the far left of snowy_1_back by the kitchen stack.
