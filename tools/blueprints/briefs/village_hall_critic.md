# Village Hall (51.1, flagship: A and B): the critics' scorecards

Brief: `village_hall.md`. Designs, each drawn blind by its own designer: `tools/blueprints/js/village_hall_a/` ("the
Bell Porch": brick hall with pews either side of an aisle, the bell under a gabled stone porch; II adds a brick
council wing, III raises the porch into a clock-and-belfry tower with a spire; 13x15x13, 20x15x13, 20x25x13) and
`tools/blueprints/js/village_hall_b/` ("the Moot House": a stone octagon under a tent roof and cupola, benches round
the wall facing each other, the bell in a timber porch gateway; II adds an east council wing with an eleven-chair
table, III a belfry tower with a copper spire; 13x16x13, 21x16x13, 21x23x13). Sheets (OLD left, NEW right, five views
per level and cutaways): `village_hall_a_sheet.png`, `village_hall_b_sheet.png`; A and B side by side:
`village_hall_pick.png`. Eye-level shots: not filmed yet (interiors judged from cutaways and the .nbt read cell by
cell). Every round's critic was a fresh subagent. Candidates: `build/rebuild/village_hall_{a,b}/` (rebuilt by each
family's recipe).

## Round 1: A SHIP, B BACK

| Score | OLD (A's critic) | A | OLD (B's critic) | B |
|---|---|---|---|---|
| Silhouette | 3 | 4 | 3 | 4 |
| Depth | 3 | 4 | 4 | 3 |
| Interior | 3 | 4 | 3 | 4 |
| Detail | 3 | 3 | 4 | 3 |

A's notes (polish, applied): no bell in the Level III belfry; the clock face read as a pink square; a long unbroken
east roof slope on Level I. B's notes (applied): flat octagon facets with few windows; the tower adds a shape but no
use; tower east and west faces don't match; flower beds as dirt cubes; the cupola a flat slab.

## Round 2: A SHIP, B BACK

| Score | OLD (A's critic) | A | OLD (B's critic) | B |
|---|---|---|---|---|
| Silhouette | 3 | 4 | 3 | 4 |
| Depth | 3 | 4 | 3 | 3 |
| Interior | 2 | 3 | ? | 3 |
| Detail | 3 | 4 | 3 | 3 |

A's optional polish for the ship step: end the spire in a point, not a 1x1 stack; a ladder to the belfry bell, or a
note why it needs none (guards rally to the porch bell, the nearest); swap the campfire on the II chimney for a cap.
B's notes (applied): sills and set-in windows, proud corner columns, more back windows; the porch roof riding up the
main roof; a hole in the cupola cap; a belfry stage on the III tower; a richer hall.

## Round 3: B SHIP

| Score | OLD | B |
|---|---|---|
| Silhouette | 3 | 4 |
| Depth | 3 | 4 |
| Interior | 3 | 4 |
| Detail | 3 | 3 |

B's polish for the ship step: the III tower adds blocks and little usable space (weaker than OLD's spire); settle on
one main wall stone (stone brick, andesite and diorite patchwork in the I cutaway); film the hall and council room.

## Brief checks (both, every round, on the candidate files)

One hall block at (6,1,10) facing north with no data; (6,1,9) open floor; the meeting bell at (6,4,0) hung from a
beam, A's second bell (III, (6,16,2)) farther from the hall block; the door at (6,1,3) with a solid lintel and clear
cells either side; no job blocks, beds or chests; bench and hanging_pot spots in I, table and chair from II; each
level at least as big and keeping over 89% of the one below; Level I about 30-38 stacks (limit 108); check.py and
holes.py clean. Not tested: the GameTests (build to DONE, pathing, build time), which run at the ship step.
