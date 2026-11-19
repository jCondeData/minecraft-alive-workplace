# Terrace biome looks: the critic's scorecards (49.10c, round 1, a fresh critic subagent, 2026-11-19)

OLD = the live plains Terrace; NEW = each look's candidate (sheets `terrace_looks/<culture>_<1|2>_vs_old.png`, single
renders `build/rebuild/tr_r1/shots/`). look_spots.py: SAME BUILDING for all four looks (24/36 shipped spots, 0 missing,
0 beds/containers/job blocks/doors moved; desert and savanna hang their bedroom lamps on another chain length, allowed).
holes.py: 0 findings (roof, meet, stray, peek, open) on all eight candidates.

OLD (plains): Silhouette 4, the stepped Dutch gables read in outline from every side (front, back); Depth 4, quoins,
string courses and the front bays (front); Interior 4, the shipped spots, lit kitchens; Detail 3, on the left and right
views the dark side roof ends in a jagged stair fringe beside the gables.

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| OLD (plains) | 4 | 4 | 4 | 3 | - | - |
| Desert | 4 | 4 | 4 | 4 | yes: flat decks, windcatchers, rawshan lattices, mud-brick upper storey | SHIP |
| Savanna | 4 | 4 | 4 | 4 | yes: one golden hip, veranda on acacia posts, mud stacks, painted fronts | SHIP |
| Snowy | 3 | 4 | 4 | 3 | yes: pale calcite, dark slate, snow ridges and drift; clearly not taiga | BACK (narrow) |
| Taiga | 4 | 4 | 4 | 4 | yes: spruce log range, notched corners, slate roof, mossy stacks; clearly not snowy | SHIP |

## Desert (SHIP)
- Silhouette 4: a saw of hollow windcatchers over a parapeted deck, one tower a household (L2 front/back); the end walls
  (left/right) are the plainest sides but are framed by proud piers.
- Depth 4: piers proud of every bay, the rawshans standing out over the ground floor, sills and viga ends (front, left).
- Interior 4: every shipped spot in place, the front rooms lit (front, through the ground-floor windows).
- Detail 4: three materials kept apart by storey (coursed sandstone, cut course, mud brick), cacti in pots on the sills,
  nothing floating (front, back).

Optional polish: L1/L2 left and right: one rawshan or a row of viga ends per storey on the end walls, which are the only
flat faces; L1 front: the small stepped notch at the right home's front corner (desert_1_front, beside the right window)
reads as a chipped block, a full block or a straight sill there.

## Savanna (SHIP)
- Silhouette 4: one long hip with no gable or valley, the veranda's pent along the street and the stacks through the back
  eave read cleanly from every side (L1/L2 front, back, top).
- Depth 4: eaves a block out all round, the veranda on posts, posts proud at every wall line, a terracotta plinth (front,
  left).
- Interior 4: every shipped spot in place; the veranda bays lit by hung lanterns (L2 front).
- Detail 4: hay bales and pots on the veranda, each front painted round its upper window, palette disciplined (L2 front).

Optional polish: L1/L2 left/right: the veranda pent runs one block past the corner post with nothing under it and reads
as a loose strip from the side (savanna_1_left): stop it at the post or bracket its end; the bamboo-mosaic hip is very
saturated next to the earth walls (top), a duller straw would sit better if a vanilla one fits.

## Taiga (SHIP)
- Silhouette 4: one broad 45-degree range with clean stair verges on both end gables and a mossy stack on every wall line
  (L1/L2 left, right, front); the plain roof is the right call after the Townhouse rounds.
- Depth 4: log ends at the notched corners, the veranda's slate pent on spruce posts, a fieldstone plinth (front, left).
- Interior 4: every shipped spot in place; lanterns in each veranda bay (front).
- Detail 4: ferns on spruce sills, fern beds in moss along the back, firewood under the rakes (back, right).

Optional polish: L1/L2 left and right: the firewood stack mixes pale birch ends and dark spruce in a checkerboard and reads
as crates (taiga_1_right, at the gable foot): one wood, logs laid lengthwise.

## Snowy (BACK, narrow)
- Silhouette 3: L2 front's row of steep gables reads as "homes in snow", but every gable ends in a blunt dark box, and
  L1 left/right are a tall plain box under one long slope.
- Depth 4: the front skin with deep reveals, piers on the party walls, the andesite band, quoins and the drift (front,
  back).
- Interior 4: every shipped spot in place, lanterns hung in the door reveals (front).
- Detail 3: palette and snow placement disciplined (top: snow only on ridges and outer eaves), but the apexes read as
  holes in the roof (front, back).

Notes:
1. L1/L2 front and back, every gable: the ridge cells (u 2..3, y 12..13, e.g. (3..4, 12..13, 0)) are full dark blocks
   two high with snow on top, so above the calcite course (y 11) each gable ends in a blunt dark square and the pale
   apex with its panes reads as a hole punched in the roof (snowy_1_front, snowy_1_back, snowy_2_front). Carry the
   calcite gable up through y 12 under the ridge and end each gable in one course of ridge stairs back to back at y 13
   (snow as layers on it), so each gable comes to a point.
2. L1/L2 front and back: once 1 is done, recheck the rake's foot at each row end (the flat eave lip at x 0 / x 12, y 8
   and the first verge stair at x 1 / x 11, y 9): from three-quarter views the left end reads as a flat shelf and the
   right end as a loose step (snowy_1_front); both ends should read as one verge running down onto the eave lip.
3. L1 left/right (optional once 1-2 are done): the end walls are a plain pale box under the slope; a second shuttered
   window or the band carried proud as a snow-capped string course would match the front's depth.

## Snowy, round 2 (a fresh critic subagent, 2026-11-19)

Sheets `terrace_looks/snowy_<1|2>_r2_vs_old.png`, single renders `build/rebuild/tr_r2/shots/`, blocks read from
`build/rebuild/terrace_snowy/terrace{,_2}.nbt`. look_spots.py SAME BUILDING (24/36, 0 missing, 0 moved); holes.py 0
findings on both levels.

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| OLD (plains) | 4 | 4 | 4 | 3 | - | - |
| Snowy r2 | 3 | 4 | 4 | 3 | yes: pale calcite, dark deepslate, snow on ridges and eaves, a white drift; clearly not taiga (no logs, no spruce range) | BACK |

- Silhouette 3 (snowy_1_front, snowy_2_front, both tops): the calcite now reaches y 12, but each gable still ends in a
  flat two-wide dark cap rather than a point: the ridge is still full deepslate two high (x 3..4, y 12..13, z 1..8),
  the back-to-back stairs sit at y 13 on the verges with **air under them at (3..4, 12, 0) and (3..4, 12, 9)**, so the
  cap floats one block above the rake (which tops out at x 2, y 11) and the top view shows each ridge as a flat white
  slab. From the front the roof reads as ragged dark hoods round pale chimneys, not a row of pointed gables.
- Depth 4 (snowy_1_front, snowy_1_back): unchanged and good: deep window reveals, party-wall piers, the andesite band,
  quoins, the drift.
- Interior 4 (snowy_1_front, snowy_2_front): every shipped spot in place, lit rooms behind the upper windows, lanterns in
  the door reveals.
- Detail 3 (snowy_1_front, snowy_2_front): palette and snow discipline still right, but the apex is now worse to look
  at than a plain box (the floating cap and the dark attic panes under it at y 10), and the left rake foot still reads
  as a shelf.

Round 1's notes: 1 is followed to the letter but not answered (the gables don't come to a point; the note's "stairs at
y 13" didn't match a rake that tops out at y 11, so the fix is below); 2 is half answered (right end now steps down
onto
the lip; left end still a shelf); 3 not touched (optional). Under REBUILD.md's rule NEW must beat OLD by a point on
three scores; it beats it on none and loses one on Silhouette.

Notes:
1. L1/L2, every home's ridge (x 3..4, 8..9, 13..14; z 0..9): replace the two-high full deepslate ridge (y 12..13) with
   one course of back-to-back deepslate stairs at **y 12** along the whole length (x 3 facing east, x 4 facing west,
   matching the rake stairs at x 2 / x 5, y 11), snow layers on them at y 13, and nothing at y 13 on the verges
   (z 0, z 9). The rake then runs x 2 y 11 -> x 3 y 12 in one step and each gable comes to a point; end the calcite
   field at z 1 one block under the stairs (y 11) so it is closed by them (check snowy_1_front, top).
2. L1/L2 front and back, left end (snowy_1_front, snowy_2_front): the snow layer on the side eave lip (x 0, y 9,
   z 1..8) sits level with the first verge stair (1, 9, 0), so the left foot still reads as a flat snowy shelf beside
   the gable. Stop the lip's snow one block in from each gable (z 2..7) or end the lip at z 1 / z 8 with a stair turned
   like the verge, so the verge runs down onto the lip as at the right end (x 11..12).
3. L1/L2, the rake column at x 2 / x 5 (and 7, 10, 12, 15 on L2): a full block at y 10 under the stair at y 11, with an
   upside-down stair at z 0, gives the verge a two-high step that reads ragged next to the one-high steps below it
   (snowy_1_front, between the attic panes). After note 1, recheck; if it still reads stepped, make the verge at z 0 a
   plain stair run (x 1 y 9, x 2 y 10/11 as stair over upside-down stair is fine only if every verge does the same).
4. Optional (round 1's note 3): one shuttered window or the band carried proud on the end walls (snowy_1_left/right).

## Snowy, round 3 (the last round; a fresh critic subagent, 2026-11-19)

Sheets `terrace_looks/snowy_<1|2>_r3_vs_old.png`, single renders `build/rebuild/tr_r3/shots/`, blocks read from
`build/rebuild/terrace_snowy/terrace.nbt`. look_spots.py SAME BUILDING (24/36, 0 missing, 0 moved); holes.py 0
findings on both levels; sizes 13x16x10 and 18x16x10.

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| OLD (plains) | 4 | 4 | 4 | 3 | - | - |
| Snowy r3 | 4 | 4 | 4 | 4 | yes: pale calcite gables under dark deepslate, snow on the side eave lips, round the stacks and in a white drift; clearly not taiga (no logs, no spruce, no notched corners, no moss) | SHIP |

- Silhouette 4 (snowy_1_front, snowy_2_front, snowy_1_back): every gable now comes to a point: the ridge is one course
  of back-to-back deepslate stairs at y 12 (x 3/4, z 0..9) closing the rake from x 2 y 11, with the calcite field
  carried to y 11 at z 1, so the front reads as a row of two (L1) and three (L2) pointed pale gables under dark hoods,
  each with its stack; the end walls (left, right) are still a plain box under one long slope, as the taiga's are.
- Depth 4 (snowy_1_front, snowy_1_left): unchanged: deep reveals, party-wall piers, the andesite band, quoins, the
  shuttered end windows, the drift.
- Interior 4 (snowy_1_front, snowy_2_front): every shipped spot in place, lit rooms behind the upper windows, lanterns
  in the door reveals.
- Detail 4 (snowy_1_front zoom, snowy_1_top): the floating cap and the "hole" apex are gone; the side lip's snow now
  stops at z 2..7 so the verge runs down past it; palette 60/30/10 holds and nothing floats. It beats OLD's jagged
  stair fringe beside the gables (old left/right).

Round 2's notes: 1 answered (pointed gables, calcite closed at y 11), except the snow layers on the ridge: only four
cells per home (z 3 and z 6, beside the stacks) instead of the whole ridge; 2 answered (lip snow z 2..7); 3 checked
after 1: the x 2 verge is still stair over block/upside-down stair, but every verge does the same and it reads as one
even rake in the zoomed front and back; 4 (optional) not taken.

Verdict and the rule: NEW beats OLD by a point on one score (Detail) and loses on none. Read literally, REBUILD.md's
"three or more scores" is not met; but round 1 of this file shipped desert, savanna and taiga on exactly this card
(4/4/4/4 against 4/4/4/3), treating a look of the same building (look_spots SAME BUILDING) as shipping when it matches
the plains Terrace on every score and beats it on at least one. Judged the same way, snowy round 3 **SHIPS**; the
lane should note that all four looks ship under that looks standard, not the rebuild rule.

Optional polish (not blocking): L1/L2 top and front: lay snow layers on the ridge stairs along the whole ridge (z 1..8,
y 13), not only beside the stacks; with the ridge bare, the snow reads mostly from the lips and the drift
(snowy_1_top, snowy_2_top).
