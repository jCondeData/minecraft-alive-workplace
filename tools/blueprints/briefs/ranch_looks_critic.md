# Ranch biome looks: the critic's scorecards (49.10e, round 1, a fresh critic subagent, 2026-11-21)

OLD = the live as-drawn Ranch (the rebuilt monitor barn, scored 4/4/4/3 at I and 4/4/4/4 at II in `ranch_critic.md`);
NEW = each look's candidate (sheets `ranch_looks/<culture>_<1|2>_vs_old.png`, OLD left, NEW right, five views; renders
and cutaways `build/rebuild/ranch_<culture>/renders/`, `<culture>_<L>_cut3_*` for the aisle, stalls and bunk room,
`cut8` (desert, savanna) or `cut7` (snowy, taiga) for the loft). Rerun here: `look_spots.py ranch_<culture> ranch`
says SAME BUILDING for all four (8/8 and 13/13 shipped spots, 0 beds, containers, job blocks or doors moved; desert's
bunk lamp and snowy's six lamps on other chains, allowed). Checked here in the `.nbt`s: one smoker (6, 1, 10), the chest
(5, 1, 10), (7, 1, 10), (9, 0, 2), (10, 0, 2) air, II's barrel (14, 1, 10); no cauldron, composter, lectern or other job
block; 2 chests at I and 4 at II. Not rerun (designers' claims): holes.py 0 and no CHECK line at both levels, stacks
35 to 42 at I (limit 108), II keeping 94 to 99 % of I. Standard (49.10c): a look ships when each score is at least the
as-drawn level's, none is worse, it reads as its culture (snowy clearly not taiga) and it misses nothing in the brief.

OLD: Silhouette 4, the monitor barn: a tall loft over two lean-to stall ranges, the cupola, the back stack, the
gateway frame (OLD front/back); Depth 4, eaves on both tiers, the loft's frame proud of the boards, the gateway beams
(OLD left); Interior 4, the aisle with the smoker and two chests at its end, four box stalls, the loft up its ladder,
II's bunk room; Detail 3 at I (palette wider than it needs) and 4 at II (the paddock, the run-in shelter, the trough
and the hitch, OLD II back).

| Look | Level | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|---|
| OLD (as drawn) | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 3 / 4 | - | - |
| Desert | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 3 / 3 | yes: coursed sandstone under mud brick, a crenellated hay tower, windcatcher, arched gateway, vigas, date palms, adobe-pier corral | BACK (narrow: II's detail) |
| Savanna | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 3 / 3 | yes: two tiers of golden hip thatch with no gable, acacia posts, granite and packed mud, pots on the peaks, a kraal | BACK (narrow: II's detail) |
| Snowy | I / II | 3 / 3 | 3 / 3 | 4 / 4 | 3 / 3 | yes: one steep dark slate A, snow on ridges and lips, pale calcite, a closed storm porch; clearly not taiga | BACK |
| Taiga | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 3 / 3 | yes: dark spruce logs notched at the corners on mossy fieldstone, three stepped gables, a svalgang, a konek; clearly not snowy | BACK (narrow: II's detail) |

Brief: no look misses a requirement (job block and storage in the kept cells, the cauldron cells free, no gate on the
way in (desert's open arch, savanna's pole gate, snowy's forebay steps, taiga's path and step), II at least I's size
each way and keeping far over 60 % of I, no extra job block, no entity, inside the 13 x 16 x 13 plot at I). Savanna is
13 x 14 x 13 and 21 x 14 x 21, so its ship step must declare those sizes if it ever replaces the as-drawn files.
Interior is judged from the cutaways and look_spots: every look holds the as-drawn aisle, smoker, chests, stalls,
ladder, loft and II's bunk room cell for cell, so Interior is 4 everywhere; eye-level shots were not filmed.

## Desert (BACK, narrow)
- Silhouette 4: a three-step skyline from every side, low stall ranges, the crenellated tower, the windcatcher, the flue
  up the back (desert_1_front, desert_1_back, desert_1_left).
- Depth 4: the gateway and portal hood, beam ends under a proud cornice, the cut-sandstone course, pilasters at each
  stall partition, merlons throwing shadow (desert_1_front, desert_1_right).
- Interior 4: the aisle, stalls and loft as drawn (desert_1_cut3_top, desert_1_cut8_top), II's bunk room with bed, desk
  and chests (desert_2_cut3_top).
- Detail 3 (I and II): the palette is held, lamps stand on wall posts, the trough, banner and palms tell the story; but
  the windcatcher reads as a blank smooth-sandstone cube from the front (its one slot a face is lost at 20 blocks), the
  yard palm stands right before the portal and hides half the front, and at II the run-in shed is a plain mud-brick box
  with a dark lid that reads as a crate, and the corral's piers read as scattered blocks rather than one fence line
  (desert_2_back, desert_2_right). II's detail falls a point below the as-drawn II's 4.

Notes:
1. II back and right, the run-in shed (desert_2_back, desert_2_right): a closed mud-brick box under a flat dark-oak lid
   at the corral's corner, the biggest thing on the back. Open its face to the corral on posts or piers, give its roof
   a palm-rib or pole edge that overhangs, so it reads as a shelter, not a crate.
2. II back and right, the corral (desert_2_back, desert_2_right): the mud-brick piers with sandstone caps are taller
   than the rails and spaced unevenly, so the line breaks up into blocks. Make the piers one height, evenly spaced,
   with the rails running continuously between them (two courses), lamps only on the corner piers.
3. I and II front, the windcatcher (desert_1_front, desert_2_front): a plain smooth cube on the tower. Give each face
   two or three slots that read at 20 blocks (or a darker grille) and a projecting cap course, so it reads as a vent.
4. I front, the yard palm (desert_1_front): its trunk and fronds stand right before the portal and the hay door and
   hide half the stable's face from the road. Move it to the yard's west corner by the trough, clear of the portal.
5. I and II top (desert_1_top, desert_2_top): the bunk house's roof deck is bare; a parapet coping and a water spout or
   two would bring it to the tower's level of finish.

## Savanna (BACK, narrow)
- Silhouette 4: a tiered golden roof, a wide skirt with a tall crown and a pot on top, every side the same strong shape,
  II's pyramid beside it (savanna_1_front, savanna_2_front, savanna_2_back).
- Depth 4: two eaves of shadow on every face, acacia posts proud of the mud, the painted band, the hood over the hay
  door (savanna_1_back, savanna_1_left).
- Interior 4: as drawn (savanna_2_cut3_top, savanna_1_cut8_top).
- Detail 3 (I and II): the pots on the peaks, the trough, the shade tree and the thatched kraal shelter tell the story;
  but the thatch is full blocks stepped like a ziggurat with a jagged rake on every corner (savanna_1_back), the red
  of the painted band reads as random red patches at the loft's corners (savanna_1_front), the kraal gate's thick posts
  and thatch cap stand right before the wicket door and hide it (savanna_1_front), and at II the terracotta kerb is a
  continuous bright orange low wall round the whole kraal and the stable (savanna_2_back), louder than the buildings.

Notes:
1. II back and left, the kraal's kerb (savanna_2_back, savanna_2_left): a solid bright terracotta course under every
   rail, so the kraal reads as an orange-walled pen and the plinth as a slab. Drop the kerb under the rails (rails on
   the ground or on acacia posts) and dull the stable's plinth to a darker terracotta or packed mud.
2. I front, the kraal gate (savanna_1_front, savanna_2_front): its two log posts, beam and thatch cap stand a block in
   front of the wicket door and cover it and the stall windows from the road. Move the gate forward to the yard rail's
   line (z 0) or make its posts fences, so the door reads behind it.
3. I and II back and sides, the skirt's corners (savanna_1_back, savanna_1_right): the lower hip is full blocks in
   steps, so the hip lines are a staircase of block corners. Use stairs or slabs at the hips and the eave edge so the
   skirt reads as a slope, not a ziggurat.
4. I front and left, the painted band (savanna_1_front, savanna_1_left): red blocks sit only at the loft's corners and
   between posts, read as patches. Run it as one continuous course (red and white alternating) across each face.

## Snowy (BACK)
- Silhouette 3: from the front a strong snow-capped A with a deep porch mouth (snowy_1_front), but both long sides are
  one flat sheet of slate with a single dormer, a box of roof from 20 blocks (snowy_1_left, snowy_1_right); at II the
  twin gables help the front and back (snowy_2_front), the sides are still the one plane. As-drawn: 4.
- Depth 3: the forebay, the hay gallery and the stepped entry give the front real depth (snowy_1_front); the sides have
  only the white eave lip and the dormer, flat between them, and the back is a calcite wall with a stack (snowy_1_back).
  As-drawn: 4.
- Interior 4: as drawn, the stalls open to the slope with their lamps on short chains (snowy_2_cut3_top,
  snowy_1_cut7_front).
- Detail 3: the snow is kept off walks and steps, the firewood, the trough and the gallery are good; but the snow lips
  read as floating white boards a block off the slate (snowy_1_left), the drift round the walls reads as a pale slab
  pavement and runs inside the paddock fence too (snowy_2_cut3_top, snowy_2_right), the hay stack stands outside the
  eave at the front's west corner (snowy_1_front), and II's lee wall is a tall blank grey block that hides the back
  gable (snowy_2_back).

Notes:
1. I and II left and right, the long sides (snowy_1_left, snowy_1_right, snowy_2_left): one plain slate plane from
   ridge to lip with one dormer. Break each side: a second dormer at z 4 or z 10 (or a long shed dormer over the
   stalls), a kick at the eave that shows a course of stairs, and log pilasters at the stall partitions carried up to
   the eave so the ground storey reads in bays.
2. I and II left and right, the snow lips and the drift (snowy_1_left, snowy_2_right): the white lip stands a block out
   from the slate like a board, and the drift is a flat white ring that reads as paving. Lay the snow on the slate's
   last course (snow layers on the eave stairs), and make the drift uneven (layers of 1 and 2, broken at the door and
   the corners), not inside the paddock fence.
3. II back, the lee wall (snowy_2_back): a tall plain fieldstone block that covers the barn's back gable and stack.
   Lower it to the paddock fence's height or open it as a three-sided shelter on posts, and dress its back with a coping
   and piers.
4. I front, the hay stack (snowy_1_front): a tall column of hay at the west corner stands out past the eave and the
   forebay. Keep it under the forebay's roof, or lower it to two blocks.
5. I back (snowy_1_back): the back gable is a plain calcite wall with the stone breast; give it the front gable's
   birch boards and spruce studs above the floor beam and a window either side of the breast.

## Taiga (BACK, narrow)
- Silhouette 4: three stepped gables, the tall hay barn between two stall pens, the stack up the back, the bunkhouse
  beside it at II (taiga_1_front, taiga_1_back, taiga_2_front).
- Depth 4: notched log corners, the svalgang on log posts with brackets, the rakes two deep over the gallery, the
  mossy footing (taiga_1_front, taiga_1_left).
- Interior 4: as drawn (taiga_2_cut3_top, taiga_1_cut7_top).
- Detail 3 (I and II): the hesje, the hitching rail, the log troughs, the firewood and the ferns tell the story and the
  palette is held; but every rake is a sawtooth of stripped log ends and the front gable a checkerboard of them, so
  the front is noisy (taiga_1_front, taiga_2_front); the konek reads as a second chimney stub from the front and the
  left (taiga_1_front, taiga_1_left); the moss blocks with ferns stand as green cubes at the corners. II's detail falls a
  point below the as-drawn II's 4.

Notes:
1. I and II front, the log ends (taiga_1_front, taiga_2_front): stripped log ends stick out at every course of each
   rake and of the front walls, so the three gables are a checkerboard of pale squares. Keep log ends only at the
   notched corners (every other course) and make the rakes one clean barge line of spruce stairs.
2. I front and left, the konek (taiga_1_front, taiga_1_left): two stacked log blocks at the ridge's end read as a
   chimney stub. Make it a head that reads in outline: a thinner neck (a fence or a wall post) under a turned stair
   head, or a pair of crossed horse heads (stairs) at the apex, kept to one block wide.
3. I and II front and sides, the moss planters (taiga_1_front, taiga_1_left): full moss blocks with a fern on top read
   as green crates at the corners. Sink the moss into the ground (y 0) and plant the ferns on it at grade.
4. II front, the bunkhouse porch (taiga_2_front): a heavy log hood on two thick log posts crowds the door. Use fence or
   thinner posts with stair brackets, the same language as the svalgang.

## Round 2 (2026-11-21, a fresh critic)

Sheets `ranch_looks/<culture>_<1|2>_r2_vs_old.png` (OLD as drawn left, NEW round 2 right, five views), renders and
cutaways in `build/rebuild/ranch_<culture>/renders/`, each plan's "Round 2" paragraph. Rerun here: `look_spots.py
ranch_<culture> ranch` says SAME BUILDING for all four (8/8 and 13/13 shipped spots, 0 beds, containers, job blocks or
doors moved). Not rerun (designers' claims): holes.py 0, no CHECK line, stacks and the share of I kept. Interior is
unchanged from round 1 (the shipped furniture cell for cell; no eye-level shots filmed), so it stays 4. Same standard
as round 1: each score at least the as-drawn level's, none worse, reads as its culture, no brief miss.

| Look | Level | Silhouette | Depth | Interior | Detail | Reads as its culture | Brief misses | Verdict |
|---|---|---|---|---|---|---|---|---|
| OLD (as drawn) | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 3 / 4 | - | - | - |
| Desert | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 4 / 4 | yes | none | **SHIP** |
| Savanna | I / II | 3 / 4 | 4 / 4 | 4 / 4 | 4 / 4 | yes | none | **BACK** (I's silhouette) |
| Snowy | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 3 / 3 | yes, clearly not taiga | none | **BACK** (II's detail) |
| Taiga | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 3 / 3 | yes, clearly not snowy | none | **BACK** (II's detail) |

### Desert (SHIP)
- Silhouette 4 / 4 (round 1: 4 / 4): the three-step skyline holds; the full-width windcatcher makes the tower's top a
  little heavy but it reads as a vent from every side (desert_1_front, desert_1_left, desert_2_back).
- Depth 4 / 4: unchanged courses, cornices, beam ends and merlons; the new cap course of upside-down stairs throws a
  shadow line round the catcher (desert_1_right).
- Interior 4 / 4: as drawn (round-1 cutaways, look_spots SAME BUILDING).
- Detail 4 / 4 (was 3 / 3): the run-in shed is now an open pole shelter under a dark slatted roof with hay under it,
  plainly a shelter (desert_2_back); the corral piers are one height, evenly spaced, two rails between them, lamps on
  the corners only (desert_2_back, desert_2_right); the bunk house's deck has merlons, pinnacles and spouts like the
  tower (desert_2_front, desert_2_top); the palm stands clear of the portal (desert_1_front).
- Round-1 notes: 1 answered, 2 answered, 3 answered (two dark grilles a face and a projecting cap; the grilles read at
  20 blocks), 4 answered, 5 answered.
- Brief: no miss. Every score at least the as-drawn level's, none worse.

### Savanna (BACK)
- Silhouette 3 / 4 (round 1: 4 / 4): the new low skirt of half-slab rings reads from the sides and back as a flat tray
  with a raised lip round the loft, a deck you could walk on, not a second tier of roof (savanna_1_left,
  savanna_1_back, render savanna_1_left.png); the strong two-tier thatch of round 1 is gone at I. At II the two pyramid
  huts carry the outline (savanna_2_back).
- Depth 4 / 4: two eaves of shadow still on every face, acacia posts proud of the mud, the band (savanna_1_front).
- Interior 4 / 4: as drawn.
- Detail 4 / 4 (was 3 / 3): the kerb is gone, rails stand on the ground and the plinth is dull brown (savanna_2_back);
  the kraal gate is fences and the wicket door reads behind it (savanna_1_front); the red-and-white band runs round the
  loft and the stable (savanna_1_left), broken only where the hoist and hay door sit (savanna_1_front).
- Round-1 notes: 1 answered, 2 answered, 4 answered; 3 answered in letter (no stair corners at the hips) but the fix
  cost the silhouette.
- Brief: no miss. Savanna is 13 x 14 x 13 and 21 x 14 x 21 (the ship step must declare those sizes).

Notes:
1. I left, back and right, the skirt (savanna_1_left, savanna_1_back, savanna_1_right): the half-slab rings read as a
   flat ledge with a lip, a balcony round the loft, not a roof. Give it a real fall from the loft wall to the eave
   (stairs at the eave and against the loft, slabs between, at least two blocks of drop over its width) and keep the
   hips clean, so the lower tier reads as a second thatch slope again from 20 blocks.
2. I front, the band at the hoist (savanna_1_front): a mossy-stone block and a red block with a spiral break the band
   by the hay door; carry the red-and-white course through or stop it cleanly at the door's frame.

### Snowy (BACK)
- Silhouette 4 / 4 (round 1: 3 / 3): two dormers a slope and posts under a kicked eave break the long sides into bays;
  the A and II's twin gables read from the front and back (snowy_1_left, snowy_1_right, snowy_2_front, snowy_2_back).
- Depth 4 / 4 (was 3 / 3): spruce posts proud under the eave at every bay, the kick, dormer cheeks, the back windows
  with shutters either side of the breast (snowy_1_right, snowy_1_back).
- Interior 4 / 4: as drawn.
- Detail 3 / 3 (was 3 / 3): the dormers are ragged: stepped stair cheeks with a floating white slab a block over each
  and the east dormer open like a hole in the slate (render snowy_1_right.png, snowy_1_left); the drift is now loose
  single white tiles scattered at the foot of the walls, read as litter, not drifted snow (snowy_1_left,
  snowy_1_right, snowy_2_right); II's run-in shelter seen from the back is still a closed fieldstone box with a flat
  white lid before the barn's back (snowy_2_back), though now low enough to show the gable and stack.
  II stays a point below the as-drawn II's 4.
- Round-1 notes: 1 answered (dormers, kick, pilasters, posts), 2 half answered (the lip is gone, the drift is now
  scattered tiles), 3 half answered (lowered and opened at the front, its back still a box), 4 answered, 5 answered.
- Brief: no miss. Clearly not taiga (pale calcite and slate, snow, one steep A).

Notes:
1. I and II left and right, the dormers (snowy_1_left, snowy_1_right, snowy_2_left): each reads as a notch cut in the
   slate with a white board floating over it. Give each a closed little gable roof of slate stairs meeting at a ridge,
   cheeks filled flush to the slope, and the snow on its ridge stairs (layers), not a slab a block above it.
2. I and II all sides, the drift (snowy_1_left, snowy_1_right, snowy_2_right): single white tiles a block apart read as
   litter. Lay it in runs two to four long against the walls and in the corners, one and two layers, broken at the door.
3. II back, the run-in shelter (snowy_2_back): from the back it is a stone box with a white lid. Open its back face
   between the stone-brick piers (rails or a half-height board wall under the coping) so it reads as a shelter from
   the back too.

### Taiga (BACK)
- Silhouette 4 / 4 (round 1: 4 / 4): three stepped gables, the stack up the back, the bunkhouse beside it at II
  (taiga_1_front, taiga_1_back, taiga_2_front); the new konek reads as a small signboard on a post.
- Depth 4 / 4: notched corners, the svalgang on posts with brackets, the rakes over the gallery (taiga_1_front,
  taiga_1_left).
- Interior 4 / 4: as drawn.
- Detail 3 / 3 (was 3 / 3): the moss cubes are gone and the bunkhouse porch now speaks the svalgang's language
  (taiga_2_front); but the front is still a field of pale squares: log ends across the hay barn's gable face round the
  hoist lamp and window, and a sawtooth of pale barge-stair ends under every rake (render taiga_1_front.png,
  taiga_2_front); the konek, a turned stair on a fence, reads from the front and sides as a sign or a weathervane
  board, not a horse head (taiga_1_front, taiga_1_left). II stays a point below the as-drawn II's 4.
- Round-1 notes: 1 partly answered (corner ends thinned at the pens, but the gable face and rakes still checker),
  2 partly (no longer a chimney stub, now a sign), 3 answered, 4 answered.
- Brief: no miss. Clearly not snowy (warm spruce logs, mossy fieldstone, stepped gables).

Notes:
1. I and II front, the hay barn's gable and rakes (taiga_1_front, taiga_2_front): pale log ends still dot the gable
   face round the window and every rake shows a pale stair end at each course. Fill the gable face with plain dark
   logs or boards (no ends inside the face), and make the barge a run of dark spruce stairs flush under the slate, so
   the only pale squares on the front are the notched corners.
2. I front and left, the konek (taiga_1_front, taiga_1_left): a flat board on a post reads as a sign. Make the head
   read as a head in outline: two stairs facing each other (crossed heads) at the apex, or a stair head turned along
   the ridge with a trapdoor or button ear, dark spruce, one block wide.

### Round 3 designer notes: taiga

Renders and cutaways overwritten in `build/rebuild/ranch_taiga/renders/` (`taiga_<1|2>_<front|back|left|right|top>`,
`taiga_<1|2>_cut3_*`, `taiga_<1|2>_cut7_*`); `js/ranch_taiga/plan.mjs` "Round 3".
1. The hay barn's gable and rakes: the barn's corners over the pens' roofs (x 4 and 8, y 5..9, front and back) were
   notched, which put a log end every other course on the gable face beside the window and the hoist lamp; they are
   upright corner posts now, so the gable face is plain dark bark with no ends in it. The barge under every rake (hay
   barn, both pens, bunkhouse) is dark oak stairs flush under the slate, one dark line instead of pale spruce teeth.
   At II the bunkhouse's two west corner ends, which crowded the east pen's into one cluster in the valley, are gone.
   The only pale squares left on the front are the notched outer corners (taiga_1_front, taiga_2_front).
2. The konek: turned along the ridge, looking out over the road, dark oak, one block wide: a fence neck (6, 13, 1), the
   skull an upside-down stair with its cheek hanging at the back over the neck, the muzzle a top slab out over the
   rake (6, 14, 0) and a button ear on the crown. From the sides a hooked horse's head on its neck (taiga_1_left,
   taiga_1_right); from the road a narrow head end-on.
Checked: `look_spots.py ranch_taiga ranch` SAME BUILDING (8/8 and 13/13, 0 moved); `holes.py` 0 at both levels (open
included); no CHECK line; 829 and 1296 blocks; 13 x 16 x 13 and 21 x 16 x 21. Not shipped.

### Round 3 designer notes: savanna

Plan: `js/ranch_savanna/plan.mjs`, "Round 3". Renders and cutaways overwritten in `build/rebuild/ranch_savanna/renders/`
(same names). Rerun: `look_spots.py ranch_savanna ranch` SAME BUILDING (8/8 and 13/13 shipped spots, 0 beds,
containers, job blocks or doors moved); `holes.py` 0 (incl. `open`) at both levels; no CHECK line; 13 x 14 x 13 and
21 x 14 x 21 as before. Not rerun: stacks and the share of I that II keeps (the skirt's cells are the same cells).
1. The skirt (I left, back and right): it falls again from the loft to the eave over its four rings, a thatch stair at
   the eave (y 4), a bottom slab over the stalls' band (y 5), a stair rising off it (y 5), a stair against the loft
   (y 6; the stalls' lamps hang from its bottom face as shipped): y 7 at the loft to y 4 at the eave, three blocks of
   drop, the hip lines stair corners. Why round 2 read as a ledge: a top slab sat beside a bottom slab at the same
   level, so from the side one looked in under it at the stalls' lamps, a sunken tray with a lip. Every piece now
   closes the cell under it. Tried first: stair, slab, top slab, stair (slabs between, as the note says): the same open
   tray from the side, so the third ring is a stair.
2. The band at the hoist (I front): the hoist beam ran through the loft's band at y 8 (its log end was the red block
   with a spiral, its bark the mossy block). It now comes out of the beam ring at y 9, over the band and the hay hood, two
   blocks out through the crown's eave, with two links of chain to the lantern; the red-and-white course runs unbroken
   over the hay door.

### Round 3 designer notes: snowy

Rechecked here: no CHECK line at either level; `look_spots.py ranch_snowy ranch` SAME BUILDING (8/8 and 13/13 shipped
spots, 0 beds, containers, job blocks or doors moved; only the lamps' chains, as before); `holes.py` 0 at both levels
(open included); 899 and 1416 blocks, 13 x 16 x 13 and 21 x 16 x 21; about 35 and 49 stacks; II keeps 94 % of I.
Renders and cutaways overwritten in `build/rebuild/ranch_snowy/renders/` (same names: `snowy_<1|2>_<front|back|left|
right|top>`, `snowy_<L>_cut3_*`, `snowy_<L>_cut7_*`).
1. The dormers (`js/parts/ranch_snowy_dormer.mjs`): each is now a closed 45-degree gable of slate stairs, eave stairs
   at z +- 2 and a course at z +- 1, meeting at one full ridge block that runs from a one-block hood over the birch face
   straight back into the slope where the slope reaches its height; the cheeks are the main slope's own full blocks,
   flush. The old steep roof of full blocks and top stairs (the stepped cheeks) and the 3-layer cap a block over its
   stairs are gone: the snow lies on the ridge itself, two layers along its three blocks (snowy_1_left, snowy_1_right,
   snowy_2_left). Tried and dropped in renders: a flush face with no hood (a birch hatch under a chimney) and snow on
   every course (steps).
2. The drift: no more scattered tiles. `plan.mjs` DRIFT lays it in runs two to four long: the west side between the
   eave posts, into both back corners, against the flue's breast, the east side (I), the wing's front corner turning
   down its side, under its window and into its back corner; two layers where it piles against a foot or a corner, one
   where it tails off; gaps at the posts; none at the doors, under the forebay or in the paddock (snowy_1_back,
   snowy_1_left, snowy_2_right).
3. II's run-in shelter: its back and sides are now a half-height spruce board wall with a spruce rail over it between
   the stone-brick piers, open above to the eave, so from the back the hay, the posts and the open front show through;
   its ridge snow is two layers, not three (snowy_2_back).

## Round 3 (2026-11-21, a fresh critic)

Sheets `ranch_looks/<culture>_<1|2>_r3_vs_old.png` (OLD as drawn left, NEW round 3 right, five views). Made here: the
live `structure/ranch.nbt` and `ranch_2.nbt` and the six candidates in `build/rebuild/ranch_<culture>/` rendered again
with `render/shoot.mjs --views front,back,left,right,top` (so the sheets show the `.nbt`s as they stand, not the
designers' renders), then `sheet.py --renders <dir> --old-name old_<L> --new-name <culture>_<L>`. Close renders at
2400 x 1600 for the snowy dormers and the taiga konek and front. Rerun here: `look_spots.py ranch_<culture> ranch` SAME
BUILDING for all three (8/8 and 13/13 shipped spots, 0 beds, containers, job blocks or doors moved); `holes.py` on the
six candidate files: TOTAL 0 (roof, meet, stray, peek and open); sizes and blocks read from the `.nbt`s: savanna
13 x 14 x 13 (770) and 21 x 14 x 21 (1214), snowy 13 x 16 x 13 (899) and 21 x 16 x 21 (1416), taiga 13 x 16 x 13 (829)
and 21 x 16 x 21 (1296). Not rerun (designers' claims): no CHECK line, stacks, the share of I kept. Interior stays 4
(the shipped furniture cell for cell; no eye-level shots filmed). Same standard as rounds 1 and 2.

| Look | Level | Silhouette | Depth | Interior | Detail | Reads as its culture | Brief misses | Verdict |
|---|---|---|---|---|---|---|---|---|
| OLD (as drawn) | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 3 / 4 | - | - | - |
| Savanna | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 4 / 4 | yes | none | **SHIP** |
| Snowy | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 4 / 4 | yes, clearly not taiga | none | **SHIP** |
| Taiga | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 4 / 4 | yes, clearly not snowy | none | **SHIP** |

None of the three beats OLD by a point on three scores (REBUILD.md's family rule); like desert in round 2 they ship on
this file's looks standard (each score at least the as-drawn level's, none worse, reads as its culture, no brief miss).

### Savanna (SHIP)
- Silhouette 4 / 4 (round 2: 3 / 4): the skirt falls again, stair at the loft, stair, slab, stair at the eave, three
  blocks of drop; from the left, back and right it reads as a second golden thatch slope under the crown, the two-tier
  hip of round 1 back without its ziggurat corners (savanna_1_left, savanna_1_back, savanna_1_right). II's two pyramid
  huts unchanged (savanna_2_back).
- Depth 4 / 4: two eaves of shadow on every face, posts proud of the mud, the hood over the hay door (savanna_1_front).
- Interior 4 / 4: as drawn.
- Detail 4 / 4: the red-and-white band now runs unbroken over the hay door; the hoist beam comes out of the beam ring
  above it with its chain and lantern (savanna_1_front). Round-2 kraal, gate and plinth fixes kept (savanna_2_back).
- Round-2 notes: 1 answered, 2 answered.
- Brief: no miss. The ship step must declare 13 x 14 x 13 and 21 x 14 x 21.

### Snowy (SHIP)
- Silhouette 4 / 4: the steep A, two dormers a side, II's twin gables (snowy_1_front, snowy_1_left, snowy_2_back).
- Depth 4 / 4: posts proud under the kicked eave, the dormers' hoods one block out, the back windows and shutters
  (snowy_1_right, snowy_1_back).
- Interior 4 / 4: as drawn.
- Detail 4 / 4 (round 2: 3 / 3): each dormer is now a closed slate-stair gable on a full ridge block with two layers of
  snow lying on the ridge itself, birch gable over the panes; no floating board (close render of snowy_1_left and
  snowy_1_right, checked in the `.nbt`: snow at y 8 on the ridge at y 7). The drift lies in runs against the walls and
  into the corners, gaps at the posts and doors, none in the paddock (snowy_1_right, snowy_2_right). II's shelter shows
  a board wall and rail from the back with the hay above it, plainly a shelter (snowy_2_back).
- Round-2 notes: 1 answered, 2 answered, 3 answered.
- Brief: no miss. Clearly not taiga.
- Left as it is (not a hole, not a reason to send it back): between each pair of dormers the eave cell (east side
  x 11, y 4, z 7) is a full block with two snow layers on it, a small valley the two dormers' eave stairs drain into; up
  close it reads as a white tile in a notch (snowy_1_right close). The stepped cheeks of the dormers' stairs are busy
  at arm's length but read as little gables at 20 blocks.

### Taiga (SHIP)
- Silhouette 4 / 4: three stepped gables, the stack, the bunkhouse at II (taiga_1_front, taiga_2_front).
- Depth 4 / 4: notched outer corners, the svalgang on posts with brackets, the rakes over the gallery (taiga_1_front).
- Interior 4 / 4: as drawn.
- Detail 4 / 4 (round 2: 3 / 3): the hay barn's gable face is plain dark bark round the window, the hoist lamp and the
  door; the barge under every rake is one dark stair line under the slate; the only pale squares on the front are the
  notched outer corners of the pens and the bunkhouse (close taiga_1_front, taiga_2_front). The front reads calm. The
  konek from the sides is a hooked head on a thin neck, muzzle out over the rake (close taiga_1_left).
- Round-2 notes: 1 answered, 2 answered from the sides; from the road (taiga_1_front) the head end-on is a flat cap on
  a post and still reads as a sign. Not worth a round on its own: the konek is one block on a 16-tall barn and the
  silhouette and detail hold without it.
- Brief: no miss. Clearly not snowy.
