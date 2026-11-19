# Burgher's Townhouse biome looks: the critic's scorecards (49.10b, round 1, a fresh critic subagent, 2026-11-18)

OLD = the live plains Townhouse (4/4/4/4); NEW = each look's candidate. look_spots.py: SAME BUILDING for all four, every
level (38/54/65/71 spots); every palette block exists in vanilla 1.21.1. Lone block: taiga III/IV's deepslate_tiles cap
over air at the tower top.

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| Desert | 4 | 4 | 4 | 3 | yes | BACK |
| Savanna | 5 | 4 | 4 | 4 | yes, the savanna cottage's family | SHIP |
| Snowy | 3 | 4 | 4 | 3 | yes, distinct from taiga | BACK |
| Taiga | 4 | 4 | 4 | 3 | yes | BACK (narrow) |

## Desert notes
1. L1 back: the kitchen stack one clean breast to the ground, no stair pieces stepping out; back awnings stop short of it.
2. L3 left: the court wall ends in a stepped, ruin-like stub: a capped pier, or run it whole to the tower.
3. L3 right: the tower's right face is a bare sandstone cliff: lattice, a mud-brick band or recessed panels per storey.
4. L3 back/front: the palms read as round oak blobs: a taller trunk and drooping fronds, or one clear palm.
5. Every level: pots off the bare plot corners onto the walls, door bay or arcade.

## Snowy notes
1. L1/L3 back, L3 left: each dormer its own small gabled roof joined to the slope, flush cheeks; snow on its ridge, not a flat cap floating above.
2. L1/L3 back: no short snow strips at three heights: a snow line runs a whole course or isn't there.
3. L1 right/top: the ridge carries two parallel snow strips: keep one.
4. L1 right, L3 back: rake edges step in single blocks: a clean stair verge.
5. L1 right, L3 left plinth: one continuous plinth course, not cobbled-deepslate patches.

## Taiga notes
1. L3 back: a clean valley and a whole back eave where the wing roof meets the main roof.
2. L1/L3 left: the dormer's ragged spruce cheeks: a clean small gable with flush cheeks.
3. L1/L3 back: no loose stair pieces at the chimney's foot along the jetty line.
4. L3 tower top: the deepslate_tiles cap at (15,21,15) sits over air: fill under it or end on a solid cell.
5. L3 right: the tower's blank middle storey: a shuttered window or a hoist beam.

## Savanna (SHIP), optional polish
- L1 back: the chimney breast steps out with stair pieces at the hall floor; L3 right: a shutter on the tower's blank middle storey; L3: corner pots at (18,0,18), (0,0,18) against the walls.

# Round 2 (a second fresh critic, 2026-11-18): desert, snowy, taiga

| Look | Silhouette | Depth | Interior | Detail | Verdict |
|---|---|---|---|---|---|
| Desert | 4 | 4 | 4 | 4 | SHIP (all five notes answered; optional: a visible cap on the court wall's end pier) |
| Snowy | 4 | 4 | 4 | 3 | BACK |
| Taiga | 4 | 4 | 4 | 3 | BACK |

Snowy: 1. L1/L3 left, L1 back: the dormers still read as holes punched in the slope with ragged stair notches: a small gabled roof of their own, flush cheeks. 2. L3 back: the roof between the chimney and the tower ends in a broken stepped edge: one whole back eave, a clean valley into the tower. 3. L1 back: the back gable's top and right rake step in single blocks: a clean stair verge.
Taiga: 1. L3 back: the wing roof meets the main roof and tower in a jagged run of stepped stairs: a clean valley, one continuous eave. 2. L1/L3 left: the dormer is a window cut into the slope with ragged stair cheeks: a small gable roof, flush cheeks. 3. L1 back: the back gable's edges step in single blocks between the chimneys: a clean stair verge.

# Round 3 (a third fresh critic, 2026-11-19): snowy, taiga (the last round)

look_spots.py: SAME BUILDING for both looks, every level (38/54/65/71 spots, 0 beds/containers/job blocks/doors moved;
three lamps on another chain length, allowed). holes.py: 0 findings on all eight candidates.

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| Snowy | 4 | 4 | 4 | 3 | yes: pale stone, dark slate roof with snow caps, white plinth; clearly not taiga | BACK |
| Taiga | 4 | 4 | 4 | 2 | yes: dark spruce frame, mossy cobble base, outside stone stacks; clearly not snowy | BACK |

Evidence. Snowy: Silhouette 4, L3 front's twin stepped gables and the spire read in outline, but L1 left/right are a
plain tall box under one roof (OLD has its jetty and dormers there); Depth 4, white plinth, snow eaves and stone
string courses on every face, flatter sides than OLD's jetty; Interior 4, every shipped spot in place, L1 front's lit
bay; Detail 3, palette disciplined, but L3 back's broken eave and L1's stepped back rake stay. Taiga: Silhouette 4, L3
front's gabled porch, cross wing and tower read well, L1 right is plain; Depth 4, jettied frame over a cobble base,
porch posts, proud outside chimneys; Interior 4, same spots as OLD; Detail 2, L1 front and back gables end in a
see-through lattice of loose stairs, which reads as a broken roof from twenty blocks.

Round-2 notes. Snowy: 1 answered by removing the dormers (L1/L3 left now a clean slope); 2 NOT answered (L3 back); 3
NOT answered (L1 back, right rake). Taiga: 1 NOT answered (L3 back); 2 answered by removing the dormer (L1/L3 left); 3
worse (L1 back and front: the rakes are now open stair lattices).

Snowy: 1. L3 back: between the chimney and the tower the main roof still ends in a notched, crenellated run of dark
stairs over the pale wall: carry the back eave whole and straight from the chimney to the tower and close the valley
into the tower in full stair courses. 2. L1 back (and its profile in L1 left/right): the back gable's right rake and
the eave corner below it step out in single loose blocks: one stair verge down each rake, ending on the eave course.
3. L1 left/right: the long sides are flat under the roof; a snow-capped string course or the upper floor's frame proud
by one would bring them to OLD's depth (optional once 1-2 are done).

Taiga: 1. L1 front and back: the right rake of both gables is a lattice of stairs with sky through it, and the left
rake has loose stairs hanging off the eave: a solid stair verge on both rakes of both gables, bargeboard flush, no gaps.
2. L3 back: the wing roof still meets the main roof and the tower in a jagged run of stepped stairs: a clean valley in
full courses and one continuous back eave. 3. L3 front: check the same rakes on the main gable (right rake shows the
same open lattice at a distance).
