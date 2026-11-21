# Artisan's House biome looks: the critic's scorecards (49.10g, round 1, a fresh critic subagent, 2026-11-21)

OLD = the live as-drawn Artisan's House (the rebuilt plains house with the bottle kiln, scored 4/4/4/4 as NEW in
`artisans_house_critic.md`); NEW = each look's candidate (sheets `artisans_house_looks/<culture>_<1|2>_vs_old.png`, OLD
left, NEW right; single renders for every level in `build/rebuild/ah_looks/r/`, the designers' own renders and cellar
cutaways in `build/rebuild/artisans_house_<culture>/renders/`). Rerun here: `look_spots.py artisans_house_<culture>
artisans_house` says SAME BUILDING for all four (29, 33, 34 shipped spots, 0 missing, 0 beds, containers, job blocks or
doors moved). Blocks read from `build/rebuild/artisans_house_<culture>/artisans_house*.nbt`. Every look's chimney
campfire stands on a full solid block (desert (11, 9, 4) on mud bricks, savanna (11, 15, 4), snowy (11, 13, 4), taiga
(11, 11, 3)); sizes 16 x 17 x 14 / 19, inside the 21 x 18 x 22 plot. Standard (49.10c): each score at least OLD's, none
worse, reads as its culture (snowy clearly not taiga), nothing in the brief missed.

OLD: Silhouette 4 (steep dark roof and the bottle kiln, from every side); Depth 4 (jetties, posts proud of infill, verge
trim); Interior 4 (workroom, hall and kitchen, two-bed bedroom, sitting room, II's journeyman room, lit cellar);
Detail 4 (flower bed, window boxes, the kiln's firebox).

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| OLD (as drawn) | 4 | 4 | 4 | 4 | - | - |
| Desert | 4 | 3 | 4 | 3 | yes: coursed sandstone under mud brick, flat coped roof, windcatcher, domed kiln, palm-rib shade | BACK |
| Savanna | 4 | 4 | 4 | 3 | yes: one golden hip with eaves all round, grey acacia posts, red granite and packed mud, the termite-mound kiln | BACK (narrow) |
| Snowy | 4 | 4 | 4 | 3 | yes: pale calcite on stone brick, steep dark slate, snow on ridges and lips, closed porch; clearly not taiga | BACK |
| Taiga | 4 | 4 | 4 | 3 | yes: notched dark logs on mossy fieldstone, 45-degree slate, jetty on corbels, konek; clearly not snowy | BACK (narrow) |

Brief: no look misses a requirement (same building by look_spots, chimney on solid blocks, front door at z 0, II's three
beds, III's lit 4-deep cellar with its ladder and chests: `l3-cut-1_top`, `cut-3-1_top`, `ah3-cut-1_top`,
`artisans-house-3-cut-1_top`). Interior is the drawn house's furniture cell for cell in all four and scores 4 in each
(`<culture>-2-cut3_top`, `<culture>-2-cut7_top`): workroom with bench and kiln mouth, hall and kitchen, bedroom over the
street, sitting room, the journeyman's room.

## Desert (BACK)
- Silhouette 4: a flat coped block, the stepped dome with its smoke beside it, the windcatcher on the back corner and the
  roof shade make a skyline unlike any other desert house (desert_1_front, desert_1_back, desert_1_right).
- Depth 3: the front has only a one-deep porch (the iwan's opening x 2..6, y 1..3 at z 1 with the door at z 2, the upper
  storey in the same plane as the piers, no overhang), the oriel is flat, and the back is a plain sandstone face with a
  row of beam ends (desert_1_front, desert_1_back); OLD's jetties and proud posts gave more.
- Interior 4: the drawn rooms cell for cell, lit, the bedroom and sitting room furnished (desert-2-cut3_top,
  desert-2-cut7_top), a lit cellar (l3-cut-1_top).
- Detail 3: drying jars on the roof and pots in the iwan, the windcatcher's slots; but the oriel reads as a dark door on
  the upper wall (the Weaver's Cottage desert fault again), the dome steps like a ziggurat, the brushwood piles by the
  stoke hole read as loose crates, and II's palm is a round oak blob (desert_1_front, desert_1_right, desert_2_back).

Notes:
1. L1/L2 front, the oriel (desert_1_front, artisans_house_low; x 3..5, z 0, y 7..8): dark-oak logs at (3, 7..8, 0) and
   (5, 7..8, 0) with two trapdoors at (4, 7..8, 0) between, flush, under the slabs at y 9: a 3 x 2 dark door stuck on
   the mud wall. Make it a bay: lattice on its two side faces (fences or trapdoors at x 3 and x 5, not logs), the front
   lattice set between fence corner posts, the cornice at y 9 a block out over corbels, so light shows through it.
2. L1/L2 front, the iwan (desert_1_front; z 1, x 1..7, y 1..4): give it depth and an arch that reads. Either step the
   door bay one block deeper, or carry the upper storey a block out over the street (z 0) on the cut-sandstone piers so
   the iwan is two deep; and make the arch's haunch stairs at (2, 3, 1) and (6, 3, 1) plus a second pair at (3, 4, 1) /
   (5, 4, 1) under the lintel a contrasting stone (cut or chiseled red sandstone, or the terracotta of the drum) so the
   curve shows against the pale wall.
3. L1/L2 right, the stoke-hole woodpiles (desert_1_right; stripped jungle logs at (14..15, 1..2, 1..2) and
   (14..15, 1..2, 6..7)): four small heaps of pale log ends either side of the hood read as crates on the sand. Lay them
   lengthwise along the kiln (axis z on the x 15 face, bark outward) in one low row each, or move the brushwood under
   the fuel shade with the fronds.
4. L1/L2 all views, the dome (desert_1_right, desert_1_top; mud-brick stairs y 6..8 round centre (11, 4)): three full
   step courses make a stepped pyramid. Soften it: the bottom course stairs turned upside-down or set as a smooth
   sandstone cornice ring flush with the band at y 5, and the top course a single ring of slabs round the crown, so the
   outline curves instead of stepping.
5. L2 back and right, the date palm (desert_2_back, desert_2_right): a round leafy crown on a dark trunk reads as a bush,
   as the Weaver's Cottage's first palm did. Thin the crown to a few long drooping fronds (leaves off a jungle-log tip,
   one block thick), and drop the dark coarse-dirt checker squares on the yard floor round it (desert_2_back).

## Savanna (BACK, narrow)
- Silhouette 4: one golden hip with eaves on every side beside a red spire with a smoking flue is the strongest outline
  of the four, readable from every side (savanna_1_front, savanna_1_back, savanna_1_right).
- Depth 4: eaves a block out all round, the upper storey carried on acacia posts over the stoep, the beam rings and corner
  posts proud of the granite, the plinth (savanna_1_front, savanna_1_left).
- Interior 4: the drawn rooms cell for cell, lit (savanna-2-cut3_top, savanna-2-cut7_top), the cellar (cut-3-1_top).
- Detail 3: the painted red-and-white band, shutters, the pots on the stoep, the wing's half-hip in II
  (savanna_2_back); but the four acacia log stacks by the stoke hole read as crates with thatch lids, the mound's
  colours are a random checker of red, orange and plain terracotta, and the turrets are single brown cubes stuck on.

Notes:
1. L1/L2 right and front, the fuel stacks (savanna_1_right, savanna_1_front; acacia logs axis x at (13..15, 1..2, 1..2)
   and (13..15, 1..2, 6..7), thatch slabs at y 3): from the yard they are four orange end-grain faces in a box under a
   yellow lid, the clearest crates on any of the four sheets. Turn the logs to axis z (bark to the yard) and stack them
   one high less at the outer end (y 1 only at x 15), the thatch lid only over the inner two.
2. L1/L2 front and right, the mound's surface (savanna_1_front, savanna_1_right): the red, orange and plain terracotta are
   scattered cell by cell, so it reads as noise. Band it: plain terracotta for the body, red terracotta only in one or two
   level courses (e.g. y 3 and y 6, the steps' lips), orange only at the neck, the brown foot kept.
3. L1/L2 front and right, the turrets (savanna_1_front, savanna_1_right): single mud-brick cubes on the flanks at about
   y 6 and y 10 read as blocks left on the mound. Make each a 2-high little cone (a block and a wall or a stair on top)
   standing on a step, or drop them.

Optional polish: the bright terracotta plinth a block out all round reads as an orange kerb on the grass
(savanna_1_back): sink it to grade or pull it flush; the fuel shelter's lone post on the back (savanna_1_back).

## Snowy (BACK)
- Silhouette 4: a tall white 2:1 gable to the road, the brick kiln and its broad stack above the eaves, the shed and the
  II wing's small gable behind (snowy_1_front, snowy_1_back, snowy_2_back).
- Depth 4: long-and-short quoins, the stone-brick plinth and floor band, deep front reveals behind the skin, sills, the
  eave snow lips, the porch (snowy_1_front, snowy_1_left).
- Interior 4: the drawn rooms cell for cell, lit (snowy-2-cut3_top, snowy-2-cut7_top), the cellar (ah3-cut-1_top).
- Detail 3: snow placed with discipline on ridges and lips, shutters on the side windows; but the porch hood is a white
  column under a dark box, the gables step in a dark sawtooth, the woodpiles read as crates and the kiln as a stacked
  cake (snowy_1_front, snowy_1_back, snowy_1_right).

Notes:
1. L1/L2 front, the storm porch hood (snowy_1_front; x 2..6, z 0, y 5..7): deepslate-tile stairs at (3, 5, 0) and
   (5, 5, 0), calcite at (4, 5, 0) between them and a full deepslate-tile block at (4, 6, 0) with snow on it, flanked by
   stone-brick stairs with snow at (2, 6, 0) and (6, 6, 0): the same white-column-under-a-dark-box that held back the
   Weaver's Cottage snowy look. Make it a hood: (4, 5, 0) deepslate tiles, the ridge at (4, 6, 0) a pair of stairs back
   to back or a bottom slab with a snow layer, and drop the stone-brick stair "ears" and their snow at x 2 and x 6.
2. L1/L2 front and back, and the II wing's back gable, the rakes (snowy_1_front, snowy_1_back, snowy_2_back): two stair
   courses a column behind a one-block verge make every gable a white stepped triangle inside a dark sawtooth (the
   designer's question; the manor's fault). Either go to 45 degrees as the manor did, or make each column's verge a full
   deepslate tile under its stair at z 0 and z 1 (and z 13, z 18) so the edge is a clean stepped coping and the calcite
   meets it square.
3. L1/L2 front and right, the woodpiles (snowy_1_right; spruce logs axis x at (13..15, 0..1, 1..2) and
   (13..15, 0..1, 6..7), snow on top): end grain to the yard in two boxes either side of the stoke hole: crates. Logs
   along z (bark out), one stack each, snow kept; and they stand from y 0, a course below the house's floor: start them at
   y 1 on the drift.
4. L1/L2 right and front, the kiln (snowy_1_right, snowy_1_front): three brick tiers with a full snow ring on each and the
   3 x 3 stone-brick stack as wide as the crown read as a tiered cake with a tower on it. Make the hovel's walls batter
   in (brick stairs, not a full ring step, at the shoulder) so the dome rounds, and set the stack 2 x 2 or with a one-block
   setback on the crown so the crown shows round it.

Optional polish: the drift is a one-wide snow ring hugging every wall foot, which reads as a white kerb from the back
(snowy_1_back); break it into drifts on the windward side and at corners.

## Taiga (BACK, narrow)
- Silhouette 4: twin slate gables to the road, the tall jettied log house and the lower kiln barn, the fieldstone stack
  through the barn's ridge, the konek (taiga_1_front, artisans_house_low, taiga_1_back); the right side is the weakest,
  a long plain barn wall under a broad slope (taiga_1_right).
- Depth 4: the log storey jettied on stripped corbels over the stoop, window boxes, crossing log ends, the pale sill
  beam, the fieldstone footing, the barn's posts and plate (artisans_house_low, taiga_1_left).
- Interior 4: the drawn rooms cell for cell, lit, the kiln glowing in the barn (taiga-2-cut3_top, taiga-2-cut7_top), the
  cellar (artisans-house-3-cut-1_top).
- Detail 3: the house's front is the best piece on any of the four sheets (flower boxes, ferns, a pot, the lantern); but
  the barn that should show the kiln is a closed log box with a strip of glass, so the kiln, the story, is invisible from
  every side, and the stoke hole faces a wall.

Notes:
1. L1/L2 front, the kiln barn's gable (artisans_house_low, taiga_1_front; z 1, x 9..14, y 1..3: spruce logs at y 1 and
   y 3, glass panes at y 2): plan.mjs says "an open front", but it is a closed wall with a glazed slot, which reads as a
   garage or a glasshouse. Open the bay under the plate (y 1..3, x 9..14) between the corner posts and a middle stripped
   post, with a spruce-fence rail at y 1 if holes.py's open check needs one, so the clay vault and its glow show from the
   road; no glass in a kiln barn.
2. L1/L2 right, the stoke hole (taiga_1_right; furnace (14, 1, 4) facing east into the spruce log at (15, 1, 4)): the
   stoking side plan.mjs calls open is a closed log wall with one three-pane window. Open x 15 at z 2..6, y 1..3 (posts at
   z 1 and z 7 under the plate) so the stoke hole faces the yard, with the firewood stacked lengthwise under the eave
   beside it as the plan says; it also gives the right side the life it lacks.
3. L1/L2 front, the konek (taiga_1_front, artisans_house_low; dark-oak stairs (4, 14, 0..1) and a trapdoor (4, 15, 0)):
   red-brown against the slate, it reads as a stub chimney on the ridge. Make it spruce (log or stripped log, the frame's
   wood) and carry it out a block past the verge so it reads as the ridge beam's carved end.

Optional polish: the back log gable steps under the 45-degree verge in an orange sawtooth (taiga_1_back), as OLD's
gables do, so not worse.

## Round 2 (fresh critic, 2026-11-21)

A fresh critic with no stake in any version. It looked at every picture on `artisans_house_looks/<culture>_<1|2>_r2_vs_old.png`,
the single renders in `build/rebuild/ah_r2/r/` (Levels 1-3, cutaways at y 3 and y 7), and read the woodpile and kiln
cells from `build/rebuild/artisans_house_<culture>/artisans_house.nbt`. All four scored fresh. Already checked by the
lane on the final builds: look_spots SAME BUILDING, holes.py 0 at every level, no CHECK line. Level III above the ground
is II in all four (`<culture>_3_front`, `_3_back`), with the cellar under it.

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| OLD (as drawn) | 4 | 4 | 4 | 4 | - | - |
| Desert | 4 | 4 | 4 | 4 | yes: mud brick over coursed sandstone, flat coped roof, windcatcher, iwan, mashrabiya bay, domed kiln, palm | SHIP |
| Savanna | 4 | 4 | 4 | 4 | yes: golden thatch hip on acacia posts, red granite and packed mud, painted band, the termite-mound kiln | SHIP |
| Snowy | 4 | 4 | 4 | 4 | yes: white calcite on stone brick, 45-degree dark slate under snow, closed storm porch; clearly not taiga (no logs, no moss, no barn) | SHIP |
| Taiga | 4 | 4 | 4 | 4 | yes: dark notched logs on mossy fieldstone, jetty on corbels, open timber kiln barn, konek; clearly not snowy | SHIP |

Brief: no look misses anything (the same building as the drawn house by look_spots; front door at z 0; chimney campfire on
solid blocks; II's third bed in the back room; III's cellar under the house).

### Desert (SHIP)
- Silhouette 4: the flat coped block, the domed kiln smoking beside it, the windcatcher on the back corner and the roof
  shade give an outline unlike the other three from every side (desert_1_front, desert_1_back, desert_1_right).
- Depth 4: the iwan is now two deep under a red-sandstone arch, and the lattice bay stands out on corbels over it. Beam
  ends, the coping and the drum's pilasters give the sides some depth too (desert_1_front, desert_1_right). The back is
  still the flattest face of the four looks (desert_1_back).
- Interior 4: the drawn rooms cell for cell, furnished and lit (desert-2-cut3_top, desert-2-cut7_top).
- Detail 4: the oriel reads as a mashrabiya with light through it now, and the arch shows in red against the pale stone.
  The crate-like brushwood is gone, II's palm has fronds rather than a blob, and the yard is plain sand. The drying jars
  and pots are still on the roof (desert_1_front, desert_2_right, desert_2_top).

Optional polish:
1. The kiln dome and II's journeyman cell still step like a ziggurat (desert_1_right, desert_2_back). The slab ring
   helps; a ring of upside-down mud-brick stairs under the y 7 course, so each course's underside slopes, would round it
   further.
2. The back face (desert_1_back): the upper mud-brick storey is flush with the sandstone below. A one-block mud-brick
   string course at y 5 with slab drip, or two small vent grilles in the ground storey, would break it up.

### Savanna (SHIP)
- Silhouette 4: the great golden hip beside the red spire with its smoking flue is the strongest outline of the four,
  readable from every side (savanna_1_front, savanna_1_back, savanna_1_right).
- Depth 4: the eaves are a block out all round, and the upper storey stands on acacia posts over the stoep. The beam
  rings and corner posts stand proud of the granite (savanna_1_front, savanna_1_left).
- Interior 4: the drawn rooms cell for cell, lit (savanna-2-cut3_top, savanna-2-cut7_top).
- Detail 4: the mound now reads in bands: plain body, red lips at y 3 and y 6, orange neck and spire. The turrets are
  little cones standing on the steps, and the painted band, shutters and stoep pots stay (savanna_1_front,
  savanna_1_right, savanna_2_back).

Optional polish:
1. The front fuel stack (savanna_1_front; acacia logs axis z at (13..15, 1..2, 1)) still shows a grid of five orange
   log ends under a thatch lid to the road, at the corner every passer sees. That is a crate, as OLD's front woodbox was.
   Drop the z 1 row, so the stack is (14..15, 1..2, 2) with the lid over it, or set cut sandstone or terracotta at z 1 as
   a low kerb in front of it.
2. The bright orange plinth a block out all round still reads as a kerb on the grass (savanna_1_back, savanna_2_left).

### Snowy (SHIP)
- Silhouette 4: the tall white gable to the road, the brick kiln with its stone stack above the eaves, the shed and II's
  small gable behind make a clear shape from every side (snowy_1_front, snowy_1_right, snowy_2_back).
- Depth 4: long-and-short quoins, the plinth and floor band, deep front reveals, eaves with snow lips, and a porch hood
  that is now a proper little dark gable (snowy_1_front, snowy_1_left).
- Interior 4: the drawn rooms cell for cell, lit (snowy-2-cut3_top, snowy-2-cut7_top).
- Detail 4: both roofs are now at 45 degrees, so each white gable meets one clean stair verge with no sawtooth
  (snowy_1_back, snowy_2_back). The porch hood is all dark, shutters and snow are placed with discipline, and the kiln's
  crown shows round a set-back stack (snowy_1_right).

Optional polish:
1. The woodpiles (snowy_1_front, snowy_1_right; spruce logs axis z at (13..15, 1..2, 1) and (13..15, 1..2, 7)): the
   outer rows show 2 x 3 dark log ends under a snow lid to the road and to the back, which reads like a crate. Drop the
   z 1 and z 7 rows, so the stacks are (14..15, 1..2, 2) and (14..15, 1..2, 6), or face the outer row with stone-brick
   walls as a log rack's end posts.
2. The snow ring hugging every wall foot still reads as a white kerb (snowy_1_back, snowy_2_back). Break it into drifts
   on one side and at the corners.
3. The stack (snowy_1_front) is a tall plain 3 x 3 stone-brick tower. A brick course or a stone-brick wall band at
   about y 8 would make it read as the kiln's chimney rather than a separate tower.

### Taiga (SHIP)
- Silhouette 4: twin slate gables to the road, the jettied log house and the lower kiln barn with the fieldstone stack
  through its ridge, and the konek (taiga_1_front, taiga_1_back, taiga_1_left). The right side now has the open stoke
  bay to give it life (taiga_1_right).
- Depth 4: the log storey on corbels over the stoop, window boxes, crossing log ends, the pale sill beam and the
  fieldstone footing. The barn's front and side bays now open between posts under the plate (taiga_1_front,
  taiga_1_right).
- Interior 4: the drawn rooms cell for cell, lit, the kiln in the barn (taiga-2-cut3_top, taiga-2-cut7_top).
- Detail 4: the house front is still the best piece on any sheet. The kiln now faces the road through an open bay with a
  lantern, and the stoke hole faces the yard between lengthwise firewood. No glass is left in the barn (taiga_1_front,
  taiga_1_right).

Optional polish:
1. Through the open front bay only fieldstone shows (taiga_1_front). A packed-mud course or two of the clay vault at the
   front (z 2, y 2..3), or a mud-brick stair lip, would show that it is a kiln and not a stone wall.
2. The konek (taiga_1_front) is still the warm plank color on the dark slate, so it reads a little as a stub. Stripped
   spruce or dark spruce would sit better; it is already much better than round 1.
3. The back log gable's orange sawtooth under the verge (taiga_1_back) is the same as OLD's, so it is not worse.
