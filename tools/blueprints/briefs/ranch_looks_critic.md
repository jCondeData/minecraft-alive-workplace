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

### Round 3 designer notes: Snowy (2026-11-21)
Sheets `ranch_looks/snowy_1_r3_vs_old.png`, `snowy_2_r3_vs_old.png` (OLD left, NEW round 3 right, five views); renders
in `build/rebuild/ranch_snowy/renders/`. Note 1 (dormers): each is now a closed little gable flush with the side wall,
birch cheeks either side of the panes, slate stairs from the eaves (z +- 2) to stairs at z +- 1 meeting a full ridge
block over the panes, flush with their tops, two rows deep into the slope; the snow lies on that ridge as two layers
(no slab a block above, no open overhang row), and the stray snow tile on the eave between the dormers is gone. Note 2
(drift): regrouped into runs along the walls, each line cut into pieces of four and three with one bare block between,
two layers in the middle and one at the ends, corners joined to the longer run, broken at the doors and walks, never a
single tile; none inside the paddock. Note 3 (II's run-in shelter): open on its back and both ends (a fieldstone kerb
and two spruce rails between the stone-brick piers and the posts) under a real little slate gable whose point faces the
back like the barn's (ridge along z with snow), its back gable boarded; the hay shows through the rails. Nothing else
changed (silhouette, depth, interior as round 2). Sizes 13 x 16 x 13 and 21 x 16 x 21 (873 and 1423 blocks). Rerun:
`look_spots.py ranch_snowy ranch` SAME BUILDING (8/8 and 13/13 spots, 0 beds, containers, job blocks or doors moved;
only the allowed lamp chains differ); `holes.py` 0 at both levels (open included); no CHECK line.

### Round 3 designer notes: Taiga (2026-11-21)

Sheets `ranch_looks/taiga_<1|2>_r3_vs_old.png` (OLD as drawn left, NEW round 3 right, five views); renders and cutaways
`build/rebuild/ranch_taiga/renders/taiga_<1|2>_<view>.png`, `taiga_<1|2>_cut<3|7>_<top|front>.png`. Note 1 (gable and
rakes): the hay barn's corners above the loft band are now upright logs on both gables, so its gable face is plain dark
logs round the window and the hoist lamp with no log end in it; the barge under every rake (barn, pens, bunkhouse) is
dark oak stairs instead of spruce, so the rakes no longer show a pale tooth at each course. The only pale squares left
on the front are the pens' and bunkhouse's notched corners (every other course), the wicket door's stripped frame and
the yard's rails. Note 2 (konek): the fence post is gone; it is now dark oak and one block wide, turned along the ridge:
a stair rising toward the road on the ridge's front end is the neck, and an upside-down stair out over the road is the
head (the notch under it is the jaw), with a trapdoor laid on top for the forelock. From the left and the front it reads
as a curved neck and a head looking out. Nothing else changed (silhouette, depth, interior, the yard, II's bunkhouse and
paddock). Sizes: I 13 x 16 x 13 (828 blocks), II 21 x 16 x 21 (1299 blocks). `look_spots.py ranch_taiga ranch`: SAME
BUILDING (8/8 and 13/13 shipped spots, 0 beds, containers, job blocks or doors moved). `holes.py`: 0 at both levels
(`open` included). The build printed no CHECK line.

### Round 3 designer notes: Savanna (2026-11-21)

Sheets `ranch_looks/savanna_<1|2>_r3_vs_old.png` (OLD as drawn left, the same OLD cells as the r2 sheets; NEW round 3
right, five views); renders `build/rebuild/ranch_savanna/renders/savanna_<1|2>_<view>.png`. Note 1 (the skirt): it is
a real thatch slope again, half a block a ring and 2.5 blocks of fall from the loft wall to the eave: stairs at the eave
(y 4, hip corners shaped by finish()), a bottom slab and then a full thatch block at y 5, stairs against the loft at y 6
(the stalls' lamps still hang from them). No top slabs: the ring of top slabs in round 2 rendered level with the slabs
beside it, which is what read as a flat tray with a lip, and it let the kit's seal_slots carry the loft wall out under
the crown's eave as a ledge. From the sides and back the lower tier now reads as a second hip of thatch under the crown,
its hips clean diagonals with no stepped corner. The stable's band moved down a course to y 3 (granite y 1..2), right
under the new eave stairs, so it still shows all round. Note 2 (the band at the hoist): the hoist beam now comes out
through the hay hood at y 7 (between its two stairs, the chain and lantern a block lower), so the loft's red-and-white
course at y 8 runs unbroken across the front over the hay door. Nothing else changed (the crown, the kraal gate and
yard, II's bunk house and kraal). Sizes: I 13 x 14 x 13 (737 blocks), II 21 x 14 x 21 (1181 blocks); II keeps 99 % of I
(728 of 737). `look_spots.py ranch_savanna ranch`: SAME BUILDING (8/8 and 13/13 shipped spots, 0 missing, 0 beds,
containers, job blocks or doors moved). `holes.py`: 0 at both levels (`open` included). The build printed no CHECK line.

## Round 3 (fresh critic, 2026-11-21)

The last round. Sheets `ranch_looks/<culture>_<1|2>_r3_vs_old.png` (OLD as drawn left, NEW round 3 right, five views;
savanna's OLD cells cut unchanged from the r2 sheets) and the renders in `build/rebuild/ranch_<culture>/renders/`
(several looked at full size, named below). Rerun here: `look_spots.py ranch_<culture> ranch` says SAME BUILDING for
all three (8/8 and 13/13 shipped spots, 0 missing, 0 beds, containers, job blocks or doors moved; snowy's lamps on other
chains, allowed); `holes.py` on the six candidate `.nbt`s: TOTAL 0 (0 roof, 0 meet, 0 stray, 0 peek, 0 open).
Interior unchanged (the shipped furniture cell for cell; no eye-level shots filmed), so it stays 4.

| Look | Level | Silhouette | Depth | Interior | Detail | Reads as its culture | Brief misses | Verdict |
|---|---|---|---|---|---|---|---|---|
| OLD (as drawn) | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 3 / 4 | - | - | - |
| Savanna r3 | I / II | 4 / 4 (I was 3) | 4 / 4 | 4 / 4 | 4 / 4 | yes: two tiers of golden hip thatch, acacia, granite and packed mud, red-and-white band, pots on the points, a kraal | none | **SHIP** |
| Snowy r3 | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 4 / 4 (was 3 / 3) | yes: one steep slate A under snow, pale calcite, a storm forebay; clearly not taiga | none | **SHIP** |
| Taiga r3 | I / II | 4 / 4 | 4 / 4 | 4 / 4 | 4 / 4 (was 3 / 3) | yes: dark spruce logs notched at the corners, mossy fieldstone, stepped gables, svalgang, konek; clearly not snowy | none | **SHIP** |

### Savanna (SHIP)
- Silhouette 4 / 4 (round 2: 3 / 4): the lower skirt falls from the loft wall to a stair eave, so from the back and
  sides it reads as a second hip of thatch under the crown again, not a tray (savanna_1_back, savanna_1_left,
  savanna_1_right); II's two pyramid huts beside it (savanna_2_back).
- Depth 4 / 4: two eaves of shadow on every face, acacia posts, the band under each eave (savanna_1_back).
- Detail 4 / 4: the red-and-white course now runs unbroken across the loft's front over the hay door (savanna_1_front);
  kerb gone, fence gate, dull plinth held from round 2 (savanna_2_back).
- Round-2 notes: 1 answered (savanna_1_back), 2 answered (savanna_1_front).
- Open, not blocking: the hoist's terracotta pieces beside the hay door read as two loose red cubes from the road, and
  a small stepped hump of thatch sits where the skirt meets the loft at its west corner (savanna_1_front,
  savanna_1_back); the bamboo-mosaic thatch is very saturated.

### Snowy (SHIP)
- Silhouette 4 / 4, Depth 4 / 4: unchanged from round 2 (snowy_1_left, snowy_1_right, snowy_2_front).
- Detail 4 / 4 (round 2: 3 / 3): each dormer is a closed little slate gable with birch cheeks and its snow lying on its
  ridge, not a slab above a notch (render snowy_1_right.png, snowy_1_left.png); the drift lies in runs of three and four
  along the walls, broken at doors (snowy_1_left, snowy_1_right); II's run-in shelter is open on its back and ends,
  rails and hay showing, under its own snowed gable (render snowy_2_back.png).
- Round-2 notes: 1 answered, 2 answered, 3 answered.
- Open, not blocking: one single snow layer stands alone at II (cell 20, 0, 4, the paddock side); the dormer snow and the
  shelter's ridge snow read as thick white caps.

### Taiga (SHIP)
- Silhouette 4 / 4, Depth 4 / 4: unchanged from round 2 (taiga_1_front, taiga_2_front, taiga_2_left).
- Detail 4 / 4 (round 2: 3 / 3): the hay barn's gable face is plain dark logs round the window and hoist lamp, and the
  rakes are dark oak stairs with no pale tooth (render taiga_1_front.png, taiga_2_front.png); the pale squares left are
  the pens' and bunkhouse's notched corners; the konek reads from the left as a neck and a head looking out (render
  taiga_1_left.png, taiga_2_left).
- Round-2 notes: 1 answered, 2 answered from the sides; from the front the konek is still more a dark stepped bracket
  than a head (taiga_1_front).
- Open, not blocking: the stall ranges' sides carry many proud log-end blocks that read as boxes stuck on the wall
  (render taiga_1_left.png); the yard's crates and logs crowd the front (taiga_2_front).
