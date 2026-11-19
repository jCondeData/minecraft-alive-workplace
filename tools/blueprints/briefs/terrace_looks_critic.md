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
