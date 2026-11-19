# Daub House biome looks: the critic's scorecards (49.10d, round 1, a fresh critic subagent, 2026-11-19)

OLD = the live as-drawn Daub House (house_t1_b, one level; `build/rebuild/old_looks/old_daub_*.png`); NEW = each look's
candidate (sheets `house_t1_b_looks/<culture>_1_vs_old.png`, OLD left and NEW right, five views; single renders and
cutaways `build/rebuild/house_t1_b_<culture>/renders/`). No brief was written for this family; its function is the
header of `js/house_t1_b/plan.mjs`: 4 beds, one job block, a kitchen, the joined loft railing. Designers' checks (their
design notes): look_spots.py SAME BUILDING for all four (40 shipped spots, 0 missing, 0 beds, containers, job blocks or
doors moved; only lamp chains differ), holes.py 0 findings (open included), the loft railing's five spruce fences joined
at (5, 5, 5). Blocks quoted below were read from `build/rebuild/house_t1_b_<culture>/house_t1_b.nbt`. Standard (49.10c):
a look ships when each of its four scores is at least OLD's, it clearly reads as its culture and it misses nothing in
the function brief.

OLD: Silhouette 4, the steep hero gable jettied over the front, the low kitchen lean and the back stack read from every
side (old_daub_front, back); Depth 4, a dark frame a block proud of the daub, the jetty, the door hood (front, left);
Interior 4, the shipped spots the owner approved (the same building in every look); Detail 3, the woodpile by the
kitchen is a 2 x 3 of log ends that reads as crates, and the kitchen gable's beam ends make a ragged fringe under the
rake (old_daub_right).

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| OLD (as drawn) | 4 | 4 | 4 | 3 | - | - |
| Desert | 4 | 4 | 4 | 3 | yes: sandstone and mud brick, four corner towers, a parapeted deck, lattice oriel, cacti in sand | SHIP |
| Savanna | 4 | 4 | 4 | 2 | yes: golden thatch hip, mud walls in a grey acacia frame, terracotta plinth, veranda, tall mud stack | BACK |
| Snowy | 4 | 4 | 4 | 2 | yes: calcite and stone under dark deepslate, snow on the ridges and eaves, a drift; clearly not taiga (no logs, no spruce walls, no moss) | BACK |
| Taiga | 4 | 4 | 4 | 3 | yes: spruce log pen with notched corners, slate roof, mossy fieldstone stack and footing, ferns; clearly not snowy | SHIP |

Interior is judged from the cutaways (`*_cut3_top` the hall and kitchen, `*_cut7_top` the loft) and look_spots: in every
look the kitchen's counters, stove and pantry, the hall's table and rug, and the loft's beds and railing are in place
and lit.

## Desert (SHIP)
- Silhouette 4: four corner towers with stepped crowns over a level parapet, the barrel-vaulted kitchen low beside them
  (desert_front, right, top); the strongest outline of the four, if more fort than cottage.
- Depth 4: the towers a block proud at every corner, the arched door niche with the lattice oriel over it, cut courses
  at y 4 and 9, a batter skirt (desert_front, left); the west face is the flattest (desert_left).
- Interior 4: kitchen, hall and loft furnished and lit (desert_cut3_top, desert_cut7_top).
- Detail 3: palette disciplined (sandstone foot, mud brick, one cut course), cacti in sand, jars at the door; but the
  back is ragged (desert_back): the 1-wide slots beside the stack breast (x 2 and 6, z 12) hold a stair at y 1 and a
  loose top slab at y 4, and the breast carries one lone cut-sandstone block at (4, 4, 12).

Optional polish: desert_back: fill the slots at x 2 / x 6, z 12 up to the cut course (or drop the stair and the slab
in them) and run the y 4 course across the whole breast (x 3..5) or not at all; desert_left: one rawshan or a pair of
toron per storey would lift the plainest face.

## Taiga (SHIP)
- Silhouette 4: the front gable over a slate-roofed porch, the wing's roof running into the hall's east slope, the mossy
  stack up the back rake (taiga_front, back, top); closest of the four to OLD's massing.
- Depth 4: log ends at the notched corners, the porch pent on log posts with brackets, barge boards, stripped studs in
  the gables, a fieldstone footing (taiga_front, left, right).
- Interior 4: every room furnished and lit (taiga_cut3_top, taiga_cut7_top and the side cutaways).
- Detail 3: ferns in moss either side of the porch, a lantern under the pent, disciplined palette; but it repeats
  OLD's two faults: the firewood in the back recess is a 2 x 3 grid of log ends either side of the breast that reads
  as crates (taiga_back), and where the wing's gable meets the hall's east rake the barge stairs and a jutting plank
  block make a ragged cluster (taiga_right, above the kitchen window).

Optional polish: taiga_back: lay the firewood with its logs along x (bark out) or as a lower, longer stack under the
eave, so it reads as split wood, not cupboard doors; taiga_right: tidy the junction of the wing's ridge with the hall's
east rake to one clean barge line.

## Savanna (BACK)
- Silhouette 4: one thatch hip with the veranda pent across the front, the wing's lower hip and the tall mud stack
  through the back eave (savanna_front, right, back).
- Depth 4: acacia posts proud of the mud panels, a beam ring at y 4, eaves a block out, the veranda on posts, a
  terracotta plinth (savanna_front, left).
- Interior 4: kitchen, hall and loft furnished and lit (savanna_cut3_top, savanna_cut7_top).
- Detail 2: on every face the window dressing is a stack of acacia stairs and open trapdoors with nothing in it, which
  reads as crates nailed round the windows (the upper window's sill sits right on the lower window's head on the west
  face); the veranda pent leaves a loose end round the west corner; the "paint band" is lone white squares, one per
  panel; the thatch is the saturated bamboo mosaic the Terrace critic already flagged.

Notes:
1. savanna_left, savanna_back, savanna_front (every window): the dressing round each window (open acacia trapdoors
   either side, an upside-down acacia stair under it, e.g. (0, 2..3, 5/7) and (0, 1, 6) / (0, 4, 6) on the west face)
   reads as crates. Keep one: shutters (trapdoors) or a plain sill, not both; and on the side walls drop the lower
   window's head stair so the upper window's sill doesn't sit on it.
2. savanna_left, front-left corner: the veranda pent turns the corner and stops after one block, (0, 4, 1), with
   nothing under it, a loose pent end beside the side wall. Stop the pent at the corner post (0, 3, 0) with a corner
   stair, or carry it along the west wall to the next post with a post under its end.
3. savanna_left (every panel at y 3): the paint band shows as one white square at the top of each mud panel ((1, 3, 3),
   (1, 3, 5), (1, 3, 7), ...) and one orange at (1, 3, 10): it reads as patches, not a band. Run it as a continuous
   course across the posts too, or drop it on the side walls and keep it only on the front under the veranda.
4. (optional) savanna_top, front: the bamboo-mosaic thatch is very saturated next to the earth walls; if no duller
   vanilla stair fits, a hay-bale ridge or a darker eave course would calm it.

## Snowy (BACK)
- Silhouette 4: the calcite side gables, the hall's long ridge and the wing's lower one, the front dormer and the broad
  stepped stack read from every side (snowy_left, right, back, top); the back is a plain wall under the stack.
- Depth 4: deep front windows, the stone storm porch, the polished-andesite band, quoins and sills on the side gables,
  eaves out all round (snowy_front, left).
- Interior 4: kitchen, hall and loft furnished and lit, the dormer pocket open over the children's rug
  (snowy_cut3_top, snowy_cut7_top).
- Detail 2: snow kept to the ridges and eaves and one drift, as it should be; but the main view has three faults: a pale
  stepped seam down the roof between the hall and the wing, an orange wooden patch on the dark roof (the dormer's
  spruce verges), and a firewood stack of mixed log ends that reads as crates beside the kitchen window (snowy_front).

Notes:
1. snowy_front and snowy_right: the hall's east gable is carried out to x 8 in calcite under each rake stair
   ((8, 7, 3), (8, 8, 4), (8, 8, 8), (8, 7, 9) and up), so above the wing's roof the rake shows a pale tooth under every
   step: from the front it reads as a stepped white seam down the roof, from the right as a ragged rake. Make the x 8
   cells above the wing's roof deepslate tiles (or upside-down deepslate stairs) so the rake is one dark band.
2. snowy_front, snowy_left: the dormer's verges and cheeks are spruce stairs and planks (`verge: 'spruce_stairs'`),
   the one warm patch on a deepslate roof; give the dormer the main roof's deepslate verge and keep the birch only in
   its face.
3. snowy_front (x 8, z 2, y 1..3, and the twin at z 10): the firewood is spruce, birch, spruce, oak log ends stacked
   upright beside the kitchen window and reads as crates (the Terrace taiga's fault). One wood, logs laid along the
   wall (bark out), lower than the window sill.
4. (optional) snowy_back: the back wall has no opening; one shuttered loft window either side of the stack, or the
   andesite band carried proud round the stack, would give it the side gables' depth.

Verdict: desert and taiga SHIP (4/4/4/3 against OLD's 4/4/4/3, each with optional polish); savanna and snowy go BACK on
Detail (2 against 3), each with notes 1-3 to answer. Under REBUILD.md's literal rule none beats OLD on three scores;
they are judged, as the Terrace and Manor looks were, by the looks standard (at least OLD's on every score).

## Round 2 (date 2026-11-19, a fresh critic)

Renders `build/rebuild/r2_1910d/renders/` (`old_daub_1_*` the drawn house, `daub_<culture>_1_*` the candidates, the
`house-t1-b-<culture>-1-cut3/cut7_*` cutaways); blocks read from `build/rebuild/r2_1910d/in/*.nbt`. Sheets:
`house_t1_b_looks/<culture>_1_r2_vs_old.png` (OLD left, NEW right: front, back, left, right, top, low). Standard: each
score at least OLD's (4/4/4/3), it reads as its culture, nothing in the function brief missed. The cutaways show the
same kitchen, hall and loft as round 1 (four beds, the stove and pantry, the loft railing).

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| OLD (as drawn) | 4 | 4 | 4 | 3 | - | - |
| Savanna | 4 | 4 | 4 | 3 | yes (unchanged) | SHIP |
| Snowy | 4 | 4 | 4 | 3 | yes, clearly not taiga (unchanged) | SHIP |

### Savanna (SHIP)
Round-1 notes checked:
1. Window dressing: answered. The side and back windows have open acacia trapdoor shutters and no stair under them; the
   upper west window no longer sits on the lower one's head (daub_savanna_1_left). The two hall front windows keep
   only a sill, under the veranda (daub_savanna_1_front).
2. Veranda pent: answered. It runs x 0..8 at (x, 4, 0) and stops on the corner posts (0, 2..3, 0) and (8, 2..3, 0),
   with no return round the corner (daub_savanna_1_left).
3. Paint band: answered as offered: off the side walls, kept across the front at y 3, z 2 (orange at x 1, 3, 5, 7,
   white at x 4) under the veranda.
4. (optional) thatch colour: not done; kept as the village's thatch, as the Terrace looks were allowed.
Detail 3: equal to OLD. The crates are gone; what is left is small orange spots on the side walls.

Optional polish: daub_savanna_1_left, daub_savanna_1_right: the beam ring's corner logs (1, 4, 1), (1, 4, 11) and their
twins at x 7 are axis x, so their orange end grain shows as squares on the side walls; turn those corners' logs to
axis z (bark out on the sides). The band's west end (1, 3, 2) shows from the side as one lone orange square: make it
white or plain packed mud.

### Snowy (SHIP)
Round-1 notes checked:
1. East rake: answered. The x 8 cells over the wing's roof are deepslate tiles, so the rake is one dark band from the
   front and right (daub_snowy_1_front, daub_snowy_1_right); no pale teeth.
2. Dormer verge: answered. Deepslate-tile stairs and a tile ridge with snow; birch only in the face.
3. Firewood: answered. Spruce alone, logs along x at (8..9, 0..1, 2) and (8..9, 0..1, 10), below the sill
   (daub_snowy_1_front).
4. (optional) back wall: not done; the back is still a plain wall either side of the stack (daub_snowy_1_back).
New: the dormer's apex has a notch. The verge stairs at (3, 8, 0) and (5, 8, 0) leave (4, 8, 0) empty, so the birch
plank behind at (4, 8, 1) shows as a thin yellow stick above the dormer (daub_snowy_1_front, daub_snowy_1_low).
Detail 3: equal to OLD (OLD carries crates and a ragged fringe; this carries one notch).

Polish before it ships (one block, no new critic needed): fill (4, 8, 0) with `deepslate_tiles`. Optional: one
shuttered loft window either side of the stack on the back (round 1's note 4).
