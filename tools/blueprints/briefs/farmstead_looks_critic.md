# Farmstead biome looks: the critic's scorecards (49.10e, as 49.10a, round 1, a fresh critic subagent, 2026-11-21)

OLD = the live as-drawn Farmstead (the rebuilt one, scored 4/4/4/4 in `farmstead_critic.md` round 2); NEW = each look's
candidate (sheets `farmstead_looks/<culture>_<1|2|3>_vs_old.png`, OLD left, NEW right, five views; renders and
cutaways `build/rebuild/farmstead_<culture>/renders/`). Rerun here: `look_spots.py farmstead_<culture> farmstead` says
SAME BUILDING for all four (11 / 17 / 19 shipped spots, 0 missing, no bed, container, job block or door moved). Counted
here from the .nbt files: composters 1 / 2 / 2, bed heads 1 / 2 / 2, chests 2 / 4 / 7, no barrel, furnace, crafting
table or other counted job block, one carved pumpkin, I's farmland in a 5 x 9 box on one level (36, taiga 40), II's 74
(taiga 80) on one level, at least 3 carrots and 1 wheat in I, III = II's size plus 4. Not rerun (designers' claims):
holes.py 0 and no CHECK line. Snowy is 3 blocks taller than the as-drawn sizes (15 x 16 x 12, 15 x 18 x 22), as several
shipped looks are; the snow layers sit on deepslate tiles, top slabs, logs or the ground. Standard (49.10c): a look ships
when each score is at least the as-drawn level's, none is worse, it reads as its culture (snowy clearly not taiga) and it
misses nothing in the brief.

OLD: Silhouette 4, the house's gable to the road with the taller red barn and its cupola stepping up behind it at II
(old_2_front, old_2_left); Depth 4, frame proud of the infill, the deep gallery, the stone plinth (old_1_front);
Interior 4 (hall, barn with chamber, stalls and loft, cellar); Detail 4.

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| OLD (as drawn) | 4 | 4 | 4 | 4 | - | - |
| Desert | 4 | 4 | 4 | 3 | yes: mud brick on a sandstone foot, flat coped roofs, a dovecote tower, a palm-rib shade along the field | BACK (narrow) |
| Savanna | 4 | 4 | 4 | 4 | yes: two golden thatch hips with no gable, the taller grain house behind, acacia posts, terracotta plinth, limed and red-banded walls | SHIP |
| Snowy | 3 | 4 | 4 | 3 | yes: calcite on stone brick, steep dark slate, snow on ridges, lips and the drift; clearly not taiga | BACK |
| Taiga | 4 | 4 | 4 | 4 | yes: spruce logs notched at the corners on mossy fieldstone, a slate gable to the road, a svalgang down the field; clearly not snowy | SHIP |

Brief: no look misses a requirement (counts above; every kept cell held, per look_spots). Interior is judged from the
cutaways (`*_cut3*` hall and barn floor, `*_cut7*` the loft, `*_3_cut-*` the cellar) and look_spots: every look holds
the shipped furniture in the shipped cells, so interiors are equal by construction; the looks differ only in walls and
materials round them.

## Desert (BACK, narrow)
- Silhouette 4: at I a stepped skyline of shade, parapet, door crown and the dovecote over all of it, a shape no other
  look has (desert_1_front, desert_1_right); at II the granary steps up behind, but its back is one tall flat mud box
  (desert_2_back).
- Depth 4: the shade on its posts along the field, the sandstone plinth and batter, the door hood, vigas, the drip
  moulding on the granary (desert_1_front, desert_2_left).
- Interior 4: the hall with bed, table, hearth and lamps, the barn floor with chamber and stalls, the loft with hay and
  grain jars, the cool store with its three chests (desert_1_cut3_top, desert_2_cut3_top, desert_2_cut7_top,
  desert_3_cut-1_front).
- Detail 3: the palette holds, but the farm's three story props each read as something else: the shaduf as a broken
  mud-brick arch beside the field (desert_1_back), the date palm as a pole with a small leaf blob (desert_1_front,
  desert_2_back), the granary's "domed bins" as stepped crenels on the parapet (desert_2_left, desert_2_back).

Notes:
1. L1/L2 back and right, the shaduf (desert_1_back, desert_1_right; pillars (10, 11) and (12, 11) y 1..3, crossbar y 4):
   two chunky mud-brick pillars with a mud crossbar read as a ruined gateway, and the counterweight as a loose block.
   Make the uprights thin (walls or fence posts in wood), the beam a single thin pole (fence or trapdoor-rail) tilted
   with a bucket-sized end on the chain, so it reads as a lever, not masonry.
2. L2 left and back, the grain bins (desert_2_left, desert_2_back; x 2..4, z 11..13 and 17..19): the stair-ring domes
   read as stepped merlons on the parapet line. Raise each on a round drum one or two blocks above the coping (walls or
   a ring of full blocks) and round the top with a single cap (a slab or a smooth-stone button), so they stand as bins,
   not battlements.
3. L2 back, the granary's back face (desert_2_back): above the wagon doors it is a 7 x 5 plain mud panel with one hay
   door. Carry the vigas through this face at the upper ceiling (y 8) as they are on the sides, and set the hoist beam
   out from it with its lantern, so the face has the same shadow line as the others.
4. L1 front and L2 back, the date palm (desert_1_front, desert_2_back; (14, 0, 0)): a straight trunk with a small round
   crown. Give it a crown of long drooping fronds (leaves out 2-3 blocks on each side, dropping at the tips) and a
   slight lean, so it reads as a palm from the road.
5. L1 back, the dovecote cap (desert_1_back): the dome reads as a square block. Narrow the cap a step (stairs round a
   one-block top) so it reads as a dome, matching what the bins get in note 2.

## Savanna (SHIP)
- Silhouette 4: one squat golden hip with a pot on the point and the tall mud stack beside it at I, and at II the
  taller grain-house hip stepping up behind with the veranda eave running along the field under both (savanna_1_front,
  savanna_2_left, savanna_2_right); the back is the plainest side but the thatch hood over the wagon doors breaks it
  (savanna_2_back).
- Depth 4: eaves a block out all round, the veranda on posts, acacia corner posts proud of the mud, the beam ring and the
  terracotta plinth (savanna_1_front, savanna_2_back).
- Interior 4: the hall, the barn floor and chamber, the loft with hay and its painted band, the granite cellar
  (savanna_1_cut3_front, savanna_2_cut3_top, savanna_2_cut7_top, savanna_3_cut-2_top).
- Detail 4: the limed face and red dado round the door, pots by the step, the scarecrow on its footing, the fenced
  field in acacia, palette disciplined (savanna_1_front, savanna_2_right).

Optional polish: the bamboo-mosaic thatch is very saturated (kept for the village, as earlier looks); the clay pots on
the points render as plain red blocks (a renderer quirk); the field fence in orange acacia is the loudest line at the
front corner (savanna_1_front): a darker rail would let the house lead.

## Snowy (BACK)
- Silhouette 3: at I the roof is the whole building: from the front the 2:1 slope with its ridge at y 12 over walls 3
  high reads as a dark wall stood on a stone plinth, with the small gablet and one short snow bar on it (snowy_1_front);
  from the left the stack and the west rake make a tower with the roof hung off it (snowy_1_left). It is better at II,
  where the crossing barn's tall birch gable and louvre make a T that reads from every side (snowy_2_back,
  snowy_2_right), but a score is the worst level a player sees, and Level I is the one every new village builds.
- Depth 4: the storm niche, the snow-laden eave lips, quoins, the andesite course, the gablet and the deep porch gable
  on the field side (snowy_1_right, snowy_2_back).
- Interior 4: the hall with bed, hearth and table, the barn floor and loft, the packed-ice root cellar with its three
  chests and lamps (snowy_1_cut3_top, snowy_2_cut7_top, snowy_3_cut-1_top).
- Detail 3: the barn's back gable with its hoods, hoist and lantern is good (snowy_2_back), but the snow lines on the
  hall's slopes are short bars that stop in mid-roof (snowy_1_front, snowy_1_back), and the drift skirt along the kerb
  reads as a white pavement strip on the grass (snowy_1_front, snowy_1_top).

Notes:
1. L1 front and left, the hall roof (snowy_1_front, snowy_1_left): a 9-high slope over a 3-high wall reads as a wall,
   not a roof. Bring the ridge down (the hall's own ridge at y 10 or lower, keeping the barn's louvre over it at II) or
   lift the wall another course with the windows, so the roof is at most about two thirds of the front as seen from
   the road.
2. L1 left, the west gable (snowy_1_left): the stack's breast rises flat to y 8 and the rake's stairs hang loose
   beside it, so the end reads as a tower with slate draped off it. Give the west rake a verge (a closed barge line
   running down to the eave lip on both sides of the stack) and step the breast in once (a weathering at y 5) so the
   stack stands against a gable, not in place of one.
3. L1 front and back, the snow bars (snowy_1_front, snowy_1_back): each snow line on a slope runs the slope's whole
   width, from rake to rake, or goes; a bar that stops short reads as a stray white plank. The gablet's ridge snow sits
   two courses above its face (as the designer noted): bring it down onto the gablet's ridge.
4. L1/L2 front and top, the drift (snowy_1_front, snowy_1_top, snowy_2_front): one even line of snow along the kerb's
   front reads as a paved strip. Vary it (one and two layers, gaps at the step and the door, piled in the corners
   against the walls) so it reads as drift.

## Taiga (SHIP)
- Silhouette 4: at I a log lodge gable-on to the road with the stack up its slope, at II the tall cross barn behind it
  with its big log gables west and east and the long svalgang pent down the field (taiga_2_left, taiga_2_right); Level
  I alone is close to the as-drawn outline (taiga_1_front against old_1_front), not better.
- Depth 4: notched corners with their log ends proud, the slate pent on the crossing logs, the svalgang on log posts,
  the mossy fieldstone byre under the log hay storey with the band between (taiga_1_front, taiga_2_left,
  taiga_2_right).
- Interior 4: the hall with bed, hearth and table, the byre with its chamber and stalls, the loft, the root cellar with
  its three chests (taiga_2_cut3_top, taiga_3_cut-2_top).
- Detail 4: ferns in moss by the door, firewood under the eaves, lanterns on the field fence posts, the stack in mossy
  fieldstone, one palette (taiga_1_front, taiga_2_left).

Optional polish: the hoist gablet at the back reads as a dark box on the long slope, its ridge a block proud
(taiga_2_back; the designer's own note); the single spruce posts on every ridge end read as sticks from 20 blocks
(taiga_2_back, taiga_2_right): crossed finials or none; the woodstore by the barn's west wall is a block of log ends
that reads as a crate stack (taiga_2_left).

## Round 2 (2026-11-21, a fresh critic)

Sheets `farmstead_looks/<culture>_<1|2|3>_r2_vs_old.png` (OLD as drawn left, NEW right, five views); renders and
cutaways `build/rebuild/farmstead_<culture>/renders/` (both looks re-rendered for round 2; desert's plan says its renders
were not taken, but the files postdate that note and show every round-2 change). Rerun here: `look_spots.py` SAME
BUILDING for both (11 / 17 / 19 spots, 0 missing, nothing moved). Counted here from the .nbt files, both looks: composters
1 / 2 / 2, bed heads 1 / 2 / 2, chests 2 / 4 / 7, no barrel, furnace or crafting table, one carved pumpkin, I's 36
farmland in a 5 x 9 box on y 0, II's 74 on one level, carrots 9 and wheat 18 in I, III = II's height plus 4 (desert
15 x 15 x 22 to 19, snowy 15 x 18 x 22 to 22). Not rerun (designers' claims): holes.py 0 and no CHECK line. Interiors
did not change (equal by construction, as round 1).

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Brief misses | Verdict |
|---|---|---|---|---|---|---|---|
| OLD (as drawn) | 4 | 4 | 4 | 4 | - | - | - |
| Desert r2 | 4 | 4 | 4 | 4 (was 3) | yes: mud brick on sandstone, flat coped roofs, dovecote and domed bins, palm and shaduf by the field | none | SHIP |
| Snowy r2 | 3 | 4 | 4 | 3 | yes: calcite, dark slate under snow lines, stone stack; clearly not taiga | none | BACK |

### Desert (SHIP)
- Silhouette 4 (OLD 4): the stepped skyline of shade, parapet, door crown and the dovecote, now capped with a stepped
  dome, reads from the road (desert_1_front); at II the two domed bins on their drums over the granary give it a
  skyline no other side lacks (desert_2_left, desert_2_right).
- Depth 4 (OLD 4): shade on posts, plinth and batter, door hood, vigas on the sides and now through the granary's back
  with the hoist beam out over the hay door (desert_2_back).
- Interior 4 (OLD 4): unchanged from round 1 (desert_2_cut3_top, desert_2_cut7_top, desert_3_cut-1_front).
- Detail 4 (OLD 4): the three props now read as what they are: the shaduf as a thin fence-post lever with its chain and
  clay bucket over the channel (desert_1_back), the palm leaning over the kerb with drooping fronds (desert_1_front,
  desert_2_right), the bins as drums with domes, not merlons (desert_2_left).
- Round-1 notes: 1 answered (desert_1_back); 2 answered (desert_2_left); 3 answered (desert_2_back); 4 answered, the
  crown is still blocky from the front but reads as a palm (desert_1_front); 5 answered (desert_1_back).
- Optional polish: the shaduf's sweep and uprights in jungle and spruce fence read close to a gallows from the back
  (desert_1_back); the front face above the door hood is still a plain mud panel (the designer's own open point).

### Snowy (BACK)
- Silhouette 3 (OLD 4): the extra wall course helped little: from the front the slate still fills about two thirds of
  the face over a short calcite storey, and the narrow 7-wide hall with its ridge at y 13 stands as a tall dark wedge,
  taller than it is long (snowy_1_front); from the left the stack, now rising alone above the weathering, still makes
  the end a tower with the roof hung off both sides of it (snowy_1_left). II's T with the barn is good (snowy_2_back,
  snowy_2_right), but Level I scores the look.
- Depth 4 (OLD 4): the niche with its lintel and lantern, the eave lips, quoins, the andesite course, the gablet and
  the porch gable (snowy_1_front, snowy_2_back).
- Interior 4 (OLD 4): unchanged from round 1 (snowy_1_cut3_top, snowy_2_cut7_top, snowy_3_cut-1_top).
- Detail 3 (OLD 4): the back slope's snow lines now run rake to rake (snowy_1_back), and the drift is broken up, but on
  the front the gablet's packed-snow ridge shows as a short white bar floating mid-slope above the gablet face
  (snowy_1_front), and the west barge boards read as an orange sawtooth under the slate steps (snowy_1_left); the drift
  pieces at the front corner are flat white tiles on the grass (snowy_1_front, snowy_1_top).
- Round-1 notes: 1 partly (walls up a course, but the roof still dominates, snowy_1_front); 2 partly (weathering done,
  the barge reads as stripes, not a closed verge, snowy_1_left); 3 back answered, front not (snowy_1_front); 4 partly
  (varied, still reads as tiles where it stands alone on grass, snowy_1_front).

Notes:
1. L1 front, the hall's proportion (snowy_1_front): the hall reads taller than it is long. Bring the main ridge down to
   y 11 or lower (a shallower or half-hipped end on the road side, the barn's louvre still over it at II) rather than
   raising the walls again, so the slate is at most half the front and the house sits wider than it stands.
2. L1 left, the west end (snowy_1_left): the stack still stands in place of a gable. Close the gable behind it with a
   solid calcite or boarded face up to the rake on both sides of the stack (not stepped boards under each stair), so
   the slate ends on a flat barge line and the stack reads as standing against a wall.
3. L1 front, the gablet's snow (snowy_1_front): the white bar above the gablet face floats mid-slope. Either cap the
   gablet's own short ridge so the snow meets the main slope at its valley, or leave the gablet's ridge in slate; no
   white piece on the front slope that does not run to an edge.
4. L1 front and top, the loose drift tiles (snowy_1_front, snowy_1_top): the single layers alone on the grass away from
   any wall read as paving slabs. Keep drift only where it leans on a wall, kerb or step.

### Round 3 designer notes: Snowy (2026-11-21)
Sheets `farmstead_looks/snowy_<1|2|3>_r3_vs_old.png`; renders and cutaways in `build/rebuild/farmstead_snowy/renders/`.
1 (proportion): the hall's roof is now kicked, 45 degrees off both eaves with one steep course at the ridge. The ridge
drops from y 13 to y 10 and the walls are unchanged (calcite y 1..4, andesite course y 5), so the slate is about half
the front. The barn's ridge (14) and louvre still stand over the hall at II. 2 (west end): the west rake now sits on
the gable wall x 1, which is solid calcite up to the slate. The stack and its stone breast stand one block proud of it
at x 0, with the weathering at y 5. The stepped barge boards are gone, and the stack tops at y 12. 3 (gablet snow): the
gablet (face y 5..8) breaks the eave down to the wall head and its ridge stays slate. The only snow on the front slope
is the eave lip on each side of the gablet, running from rake to gablet, and the ridge from rake to rake. The back's
mid-slope snow line went with the steep courses. 4 (drift): snow only on cells against a wall (a full block at y 1
beside it): two or three layers, three where two walls meet, a few gaps. There is none beside kerbs or steps and none
out on the grass. Also: the porch gable's beam drops to y 5 under the whole east rake, so the birch gable with its two
studs fills the rake. Sizes: I 15 x 14 x 12 (was 17 tall), II 15 x 18 x 22, III 15 x 18 x 22 + 4 below. Rerun:
look_spots SAME BUILDING (11 / 17 / 19 spots, 0 missing, nothing moved); holes.py 0 at all three levels (roof, meet,
stray, peek, open); no CHECK line. Interiors unchanged.
