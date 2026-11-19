# Weaver's Cottage biome looks: the critic's scorecards (49.10d, round 1, a fresh critic subagent, 2026-11-19)

OLD = the live as-drawn Weaver's Cottage (the rebuilt plains cottage, scored 4/4/4/4 in `weavers_cottage_critic.md`);
NEW = each look's candidate (sheets `weavers_cottage_looks/<culture>_<1|2>_vs_old.png`, OLD left, NEW right, five views;
desert and savanna renders and cutaways `build/rebuild/weavers_cottage_<culture>/renders/`, snowy and taiga
`build/rebuild/wc_r3/`, with `wc_r3/big/` for the zoomed taiga front). Designers' checks (plan.mjs notes, not rerun
here): look_spots.py SAME BUILDING for all four (14/14 shipped spots at each level, no bed, container, job block or door
moved; desert and taiga hang the loft lamps on shorter chains, allowed); holes.py 0 (with open) at both levels; rebuild.py
no CHECK line; one loom at (2, 1, 3) and the chest at (6, 1, 3) in the kept cells; fences and a gate in every fold; II's
flowers 44/45 (desert, savanna), 23 (snowy), 25 (taiga) against I's 0; 40 to 69 stacks of the 81. Standard (49.10c): a
look ships when each score is at least the as-drawn level's, none is worse, it reads as its culture (snowy clearly not
taiga) and it misses nothing in the brief.

OLD: Silhouette 4, a side-gabled cottage with an end stack, a porch gable and the hoist beam (old_wc_1_front, left);
Depth 4, the recessed porch, stone band, window box and gable overhang (front); Interior 4, the workroom with hearth and
table, stairs to a loft with chest and bench; Detail 4, one palette, the woodpile, hay, the hoist lantern and II's dye
garden and tenter (old_wc_2_back), though the gables carry an orange stair fringe under the rakes (old_wc_2_left).

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| OLD (as drawn) | 4 | 4 | 4 | 4 | - | - |
| Desert | 4 | 4 | 4 | 3 | yes: coursed sandstone under mud brick, flat coped roof, windcatcher, arcade, vigas, cloth drying on the roof | BACK (narrow) |
| Savanna | 4 | 4 | 4 | 4 | yes: one golden hip with no gable, acacia posts and beam ring, granite and packed mud, a veranda, a kraal with its own thatched shelter | SHIP |
| Snowy | 4 | 4 | 4 | 3 | yes: pale calcite under a steep dark slate roof, snow on ridges, lips and sills, a closed storm porch; clearly not taiga | BACK |
| Taiga | 4 | 4 | 4 | 4 | yes: dark spruce logs notched at the corners on mossy fieldstone, a broad slate range, a veranda on log posts; clearly not snowy | SHIP |

Brief: no look misses a requirement (one loom, the kept loom and chest cells, a fold of at least 8 fences and a gate by
the loom, a front way in with no gate, II >= I + 8 flowers, no barrel, composter, cauldron or other job block, inside the
18 x 14 x 12/17 plot). Interior is judged from the cutaways (`*_cut3*` the workroom, `*_cut7*` the loft) and look_spots:
in every look the workroom holds the loom by the long window, the chest, the table and the flight; the loft its chest,
tables and wool.

## Desert (BACK, narrow)
- Silhouette 4: a flat coped block with the slotted windcatcher flush with the back wall and the bright cloth racks over
  the coping, a skyline no other look has (desert_1_front, desert_1_back); the arcade and the fold's shade work the
  front and east side; the west end is the plainest side but has vigas and a window on both storeys (desert_1_left).
- Depth 4: the two-bay arcade with its little terrace, the portal's chiseled jambs one block back, vigas a block out,
  the cut-sandstone course at the loft floor, the coping (desert_1_low, desert_2_front).
- Interior 4: workroom with loom, chest, table and hung lamps; the loft with its wool, tables and chest
  (desert_1_cut3_top, desert_1_cut7_top, desert_2_cut7_top).
- Detail 3: palette held by storey, pots and lanterns on posts by the portal (allowed), the rill and dye beds of II
  (desert_2_top); but the oriel over the portal is the loudest thing on the front and reads as a big dark door stuck on
  the mud wall, the racks' ends read as crates, and the palm reads as a round bush.

Notes:
1. L1/L2 front, the oriel (desert_1_low, desert_2_front; x 8..10, z 1, y 5..8): three dark-oak logs either side of a
   column of open trapdoors, flush, under a slab, so it reads as a 3 x 3 dark door on the upper wall, a plain flat face.
   Make it a bay that hangs out: carry it on corbels at y 4 (upside-down stairs under it), give it lattice on its two
   side faces as well as the front (not full logs at x 8 and x 10), and cap it with a projecting cornice at y 8, so
   light shows through it and it throws a shadow on the wall.
2. L1/L2 left and right, the roof racks (desert_1_left, desert_2_left): the spruce rails end in log ends turned to the
   street, so from the sides each rack reads as two crates with cloth between. End each rail on a post (fence or wall)
   with the log turned along the rail, so only bark shows.
3. L2 back and top, the date palm (desert_2_back, desert_2_top): it reads as a round leafy bush on a trunk. Thin the
   crown to a few long drooping fronds (leaves on a jungle or stripped trunk) so it reads as a palm from the road.

Optional polish: L1 left: the cut-sandstone course at y 4 ends as a stair at x 0 and x 12 and reads as a shelf from the
corner; run it round the corner or stop it flush.

## Savanna (SHIP)
- Silhouette 4: one great golden hip with eaves on every side and no gable, the small pyramid of the kraal shelter
  beside it, a pot on the ridge (savanna_1_front, savanna_1_right, savanna_2_right); the back is the plainest side but
  its pairs of shutters and the beam ring break it up (savanna_1_back).
- Depth 4: eaves a block out all round, the veranda on four acacia posts with its pent stopping clean at the end posts,
  the beam ring at the loft floor, corner posts proud of the walls, the terracotta plinth (savanna_1_low, savanna_1_left).
- Interior 4: workroom with loom, chest and table lit by hung lanterns, the loft with wool and its chest, the painted
  band round its walls (savanna_1_cut3_top, savanna_1_cut7_top, savanna_2_cut7_top).
- Detail 4: decorated pots by the veranda posts, hay in the kraal, shutters on every window, II's garden walls with a
  thatched coping and the drying frame of cloth over it, palette disciplined (savanna_2_back, savanna_2_top).

Optional polish: the renders (06:57) show two pots on the ridge (savanna_1_left, savanna_1_top) while the saved file
(06:58) has one at (6, 12, 5): re-render before the sheet goes to the owner; the young acacia in II reads as an oak blob
(savanna_2_back); the bamboo-mosaic thatch is very saturated (kept for the village, as the Terrace critic allowed).

## Snowy (BACK)
- Silhouette 4: a steep (2:1) dark roof that is two thirds of the house, the birch dormer, the broad stack astride the
  ridge and the stepped white gables read as "snow country" from the front, left and right (snowy_1_front,
  snowy_1_left, snowy_1_right); weakest at L2's back, where the four-high drying wall hides the ground storey
  (snowy_2_back).
- Depth 4: the weaver's window between stone mullions on a deep sill, the dormer, long-and-short quoins, the andesite
  course, the eave lips, the fold's lee wall with its hood (snowy_1_front, snowy_1_right).
- Interior 4: workroom with loom, chest, table and hearth; the loft under the steep roof lit by the dormer and the gable
  lights (snowy-1-cut3_top, snowy-1-cut7_top, snowy-2-cut7_top).
- Detail 3: snow placed with discipline on ridges, lips and sills, the dyed cloth under its hood in II (snowy_2_top);
  but the storm porch's gable is a white column under a blunt dark cap, the woodpile reads as crates, and loose snow
  slabs lie on the grass.

Notes:
1. L1/L2 front, the storm porch (snowy_1_front, snowy_2_front; x 8..10, z 0..1): its rakes are two stairs stacked
   straight up at x 8 and x 10 (y 4 and y 5) with calcite at (9, 4..5, 0) between them and a full deepslate-tile block
   at (9, 6, 0..1) with snow on top, so it reads as a one-wide white column under a dark box, not a gable (the
   designer's question). Bring it to a point: rake stairs at x 8 / x 10 on y 4 only, the ridge one course at y 5 over
   x 9 along z 0..1 (stairs back to back or a slab), snow layers on it, calcite only at (9, 4, 0) under it; rehang the
   porch lantern under the new ridge, keeping the doorway's two clear blocks.
2. L1/L2 left, the woodpile under the west rake (snowy_1_left, snowy_2_left; x 0, z 5..7, y 0..1): birch logs under
   spruce logs, both laid along x, so their end grain faces the road in a pale and dark checker and reads as two rows of
   crates. One wood, logs laid lengthwise along the wall (axis z), the snow on top kept.
3. L1/L2 front and top, the ground snow (snowy_1_front bottom, snowy_1_top): the layers at (11..12, 0, 0..1) beside the
   porch step and by the fold's corner read as loose white slabs on the grass, apart from the drift. Join them to one
   drift along the wall foot or drop them.
4. L2 back (snowy_2_back): from the back road the drying wall is a four-high panelled box that hides the whole ground
   storey (the designer's question). Step its top down at the ends or open it with two or three slits under the hood so
   the cloth's colours and the house's ground storey read through.

Optional polish: L1/L2 front, the dormer's verge is dark-oak stairs that render red-brown, a third roof material beside
the deepslate and the birch (snowy_1_front): give it the main roof's deepslate tiles.

## Taiga (SHIP)
- Silhouette 4: a long low log range under one broad 45-degree slate roof swept two blocks past both long walls, the
  slate lean-to over the fold door, the fieldstone chimney on the west gable (taiga_1_front, taiga_1_left,
  taiga_1_right); clearly the opposite of the snowy look's steep white mass.
- Depth 4: the veranda on log posts with brackets on a log plate, log ends crossing at every corner, the stripped band
  at the loft floor, boarded soffits, the fieldstone plinth (big/taiga_1_low_zoom, taiga_1_left).
- Interior 4: workroom with loom, chest, table and the hearth under its breast (seen through the long window from the
  veranda), the loft with wool and its chest under the roof (taiga-1-cut3_top, taiga-1-cut7_top, taiga-2-cut7_top).
- Detail 4: lanterns hung in the veranda bays, potted ferns by the door, ferns in moss under the back windows, II's
  log-kerbed dye beds and dirt walk inside a rail fence (big/taiga_1_low_zoom, taiga_2_back).

Optional polish: L1/L2 left, the flue above the weathering is one block wide for five courses and reads as a pole
(taiga_1_left): two wide to the rake, or a corbelled cap course; L1/L2 back, the firewood along x 7..10, z 9 reads as a
dark box with a step (taiga_1_back): show the log ends or split it in two short stacks; L2 right, the hesje's dark posts
with cloth between read as a wall of pillars (taiga_2_right): thinner posts (fences) under the rail; L1/L2 right, the log
gable steps under the rake in an orange spruce sawtooth (taiga_1_right), as OLD's gables do, so not worse.

## Round 2 (date 2026-11-19, a fresh critic)

Renders `build/rebuild/r2_1910d/renders/` (`old_wc_<L>_*` the drawn levels, `wc_<culture>_<L>_*` the candidates, the
`weavers-cottage-<culture>-<L>-cut3/cut7_*` cutaways); blocks read from `build/rebuild/r2_1910d/in/*.nbt`. Sheets:
`weavers_cottage_looks/<culture>_<L>_r2_vs_old.png` (OLD left, NEW right: front, back, left, right, top, low). Same
standard as round 1: each score at least OLD's (4/4/4/4), it reads as its culture, nothing in the brief missed. Interior
is unchanged from round 1 (the cutaways show the same workroom and loft; the designers report look_spots SAME BUILDING).

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| OLD (as drawn) | 4 | 4 | 4 | 4 | - | - |
| Desert | 4 | 4 | 4 | 4 | yes (unchanged) | SHIP |
| Snowy | 4 | 4 | 4 | 3 | yes, clearly not taiga (unchanged) | BACK (narrow) |

### Desert (SHIP)
Round-1 notes checked:
1. Oriel: answered. It now hangs a block out (z 0) on a row of sandstone corbels at y 4, fence corner posts, lattice on
   the front and both sides, a dark-oak slab cornice a block out at y 8; it throws a shadow and reads as a bay, not a
   door (wc_desert_1_low, wc_desert_2_front). Depth gained.
2. Racks: half answered. Each rail now stops on a fence post, but the rail is `spruce_log` axis x, so its end grain at
   (4, 12, 3) / (6, 12, 3) and (4, 12, 6) / (5, 12, 6) still shows behind the slim posts from the sides
   (wc_desert_1_left). It reads now as a post with a board behind it, much smaller than round 1's crates.
3. Palm: answered well enough. A 6-high bare jungle trunk with drooping fronds reads as a palm from the right
   (wc_desert_2_right); from the back and top the diagonal leaves at top and top-1 still fill it into a broad umbrella
   (wc_desert_2_back, wc_desert_2_top).
Detail 4: the oriel was the fault that held it at 3, and it is now the front's best piece; what is left is small.

Optional polish (no new critic needed): the rack rails as `stripped_spruce_log` or a fence rail so no ring shows from
the sides; drop the four diagonal leaves at (palm ± 1, top - 1, ± 1) to thin the crown from the back; the y 4 course's
stair ends at x 0 / x 12 (round 1's polish) are still there.

### Snowy (BACK, narrow)
Round-1 notes checked:
1. Storm porch: answered. Rake stairs at (8, 4, 0..1) and (10, 4, 0..1), a deepslate-tile ridge at (9, 5, 0..1) with
   snow, calcite only at (9, 4, 0), the lantern under the ridge at (9, 4, 1): it reads as a small pointed gable now
   (wc_snowy_1_low).
2. Woodpile: answered. One wood (spruce), logs along z at x 0, z 5..7, y 0..1, snow on top; from the left it reads as
   a dark stack, not crates (wc_snowy_1_left).
3. Ground snow: half answered. The cells by the step and at x 11..15 are bare, but two loose layers remain at
   (16, 0, 1) and (17, 0, 1), in front of the fold's kerb and joined to nothing: the white slab is still on the grass in
   both front views (wc_snowy_1_front, wc_snowy_2_front, bottom left).
4. Drying wall: half answered. Slits at x 2, 5..6 and 9, y 1..2 open the wall, but they are bare 2-high holes with no
   head or sill in the calcite panels, and through them the back road sees gravel, not the cloth: the banners hang at
   (1..10, 3, 14), above the slits' sight line (wc_snowy_2_back). The wall is still four high and hides the ground
   storey.
New: the dormer's apex has a notch. The verge stairs at (3, 8, 1) and (5, 8, 1) leave (4, 8, 1) empty, so the birch
plank behind it at (4, 8, 2) shows as a thin yellow stick standing up out of the slate (wc_snowy_1_front,
wc_snowy_1_low).
Detail 3: snow placed with discipline and the porch and woodpile fixed, but the front still carries the loose slab and
the stick, and the back wall's slits read as holes. Still below OLD's 4.

Notes (all small, all named cells):
1. wc_snowy_1_front, wc_snowy_2_front: remove the snow layers at (16, 0, 1) and (17, 0, 1) (or join them to a drift along
   the fold's whole kerb, x 11..17, z 1). No loose white cell left on the grass.
2. wc_snowy_1_front, wc_snowy_1_low: fill the dormer's apex at (4, 8, 1) with `deepslate_tiles` (or an upside-down
   deepslate-tile stair) so the verge closes and no birch shows above the face.
3. wc_snowy_2_back: make the drying wall read from the back road. Either drop its top to y 3 between the piers
   (x 1..3, 5..6, 8..10, keeping the hood at y 4 on the piers), or frame each slit (a stone-brick head at y 3 and a
   slab sill at y 0) and hang the cloth low enough to fill them (banners at y 2 on the rail's posts, or wool at y 1..2,
   z 15, behind the slits), so colour, not gravel, shows through.

Under REBUILD.md's "twice at most" this is the snowy look's second BACK: if the lane answers these three, the look goes
to the owner as it stands after round 3 (or to a third critic, as the lane decides).

## Round 3 (2026-11-19, a fresh critic)

Renders `build/rebuild/r3_wc_snowy/renders/` (`wc_snowy_r3_<L>_*`, cutaways `wc-snowy-<L>-cut3/cut7_*`), OLD from
`build/rebuild/r2_1910d/renders/old_wc_<L>_*`; blocks read from `build/rebuild/r3_wc_snowy/in/*.nbt`. Sheets:
`weavers_cottage_looks/snowy_<L>_r3_vs_old.png` (OLD left, NEW right: front, back, left, right, top, low). Same standard.

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| OLD (as drawn) | 4 | 4 | 4 | 4 | - | - |
| Snowy | 4 | 4 | 4 | 4 | yes: pale calcite under a steep deepslate roof, snow on ridge, lips, sills and wall copings, the closed storm porch; clearly not taiga | SHIP |

Round-2 notes checked:
1. Loose ground snow: answered. No snow at (16, 0, 1) / (17, 0, 1) or anywhere along the fold's kerb; the only ground
   snow is the drift along the wall foot (x 0..7, z 0..2). The white slab at the front's bottom left is gone
   (wc_snowy_r3_1_low against round 2's wc_snowy_1_low).
2. Dormer apex: answered. (4, 8, 1) is `deepslate_tiles` (and (4, 9, 1..2) above it), so the verge closes to a point
   and the birch stick is gone; what shows is the dormer's birch-plank pediment at y 7, which reads as its board face
   (wc_snowy_r3_1_front, wc_snowy_r3_1_low).
3. Drying wall: answered (the note's second option). Each slit at x 2, 5..6 and 9 now has a stone-brick head at y 3
   and a stone-brick sill at y 0, and wool hangs behind it at z 15, y 1..2 (yellow; light blue and red; magenta), so the
   back road sees colour, not gravel (wc_snowy_r3_2_back). The wall stays four high, but it now reads as a dye yard
   wall with windows on the cloth, not a blank box.

Scores:
- Silhouette 4: the steep two-to-one deepslate roof, the stack astride the ridge, the dormer and the pointed porch gable
  (wc_snowy_r3_1_front, left, right); L2's back is still mostly wall, now with something to look at.
- Depth 4: mullioned weaver's window on a deep sill, quoins, the andesite course, eave lips, the porch, the framed slits
  (wc_snowy_r3_1_front, wc_snowy_r3_2_back).
- Interior 4: unchanged; workroom with loom, chest, table, hearth and flight, the loft with chest, tables and wool under
  the steep roof (wc-snowy-2-cut3_top, wc-snowy-1-cut7_top).
- Detail 4: one palette, snow with discipline, the woodpile as one wood, dyed cloth in the yard and through the slits;
  nothing left on the front that breaks the eye and no breach of the owner's furniture rules seen.

Verdict: SHIP. Equal to OLD on every score, none worse, clearly snowy and not taiga, and all three round-2 notes fixed.

Optional polish (no critic needed): the wool behind the slits stands on bare gravel; a stripped-spruce rail or fence
post under it would show it as cloth hung to dry rather than a stacked block.
