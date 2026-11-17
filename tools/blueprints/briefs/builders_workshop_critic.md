# Builder's Workshop (50.1, flagship: A and B): the critics' scorecards

Brief: `builders_workshop.md`. Designs: `tools/blueprints/js/builders_workshop_a/` (stone ground floor, brick-and-timber
loft jettied over the street, hoist beam at the gable peak, open yard shed down the east side; 14x14x13, 808 blocks)
and `tools/blueprints/js/builders_workshop_b/` (stone work room, jettied timber-and-brick bedroom loft, a long catslide
roof over a timber store, hoist beams, a fenced materials yard; 16x16x11, 851 blocks). Each drawn blind by its own
designer. Sheets (round 2, OLD left, NEW right: five views and cutaways): `builders_workshop_a_sheet.png`,
`builders_workshop_b_sheet.png`; A and B side by side: `builders_workshop_pick.png`. Eye-level shots: not filmed yet
(interiors judged from cutaways and the .nbt read cell by cell). Each round's critic was a fresh subagent.

## Round 1: A SHIP, B BACK

| Score | OLD (A's critic) | A | OLD (B's critic) | B |
|---|---|---|---|---|
| Silhouette | 3 | 4 | 4 | 4 |
| Depth | 2 | 4 | 4 | 3 |
| Interior | 3 | 4 | 2 | 4 |
| Detail | 3 | 3 | 4 | 3 |

A's notes (polish, applied): the builder's trade barely reads from the street; stray two-log piles on the lawn; a 1x1
chimney like a pole; two bed colors and a saturated blue rug; seven stones on the ground floor.
B's notes (applied): upper-storey frame flush with its infill on the left and back; nothing outside says builder; a
bare supply bay and a flush hearth; a stray brick lump by the shed doors; a plain plank east wall.

## Round 2 (fresh critics): both SHIP

| Score | OLD (A's critic) | A | OLD (B's critic) | B |
|---|---|---|---|---|
| Silhouette | 4 | 4 | 3 | 4 |
| Depth | 2 | 4 | 3 | 4 |
| Interior | 2 | 4 | 3 | 4 |
| Detail | 3 | 4 | 3 | 4 |

No brief requirement missed by either (one Blueprint Table and no other job block, no calcite, jigsaw, void or entity;
door on the z=0 side with no trapdoor at z=1; beds reachable; chests within supply reach; inside the 18x18x20 plot and
well under 81 stacks).

## Left to fix at the ship step (both critics)

1. Neither new front says "builder" from 20 blocks the way OLD's crane did: give the chosen one a real hoist arm or
   gantry (A's is a short ridge beam with a lantern; B's is a slab with a chain).
2. `StarterBlueprints.BUILDERS_WORKSHOP` (13,13,11) becomes A (14,14,13) or B (16,16,11); B's file is shifted one block
   east of the brief's village-contract coordinates (file x = plan x + 1).
3. The village variant keeps its fixed cells (door (4,1,2), table (2,1,7), chest (2,1,6), bed head (6,1,7) facing
   south, front row z=0 empty for x 0-8): B's ladder stands on (6,1,7) and its store apron starts one block too far
   west; A's room is wider than the variant's 7x7.
4. A: the chimney still reads tall and thin from the left and back.
5. Not tested: build to DONE within 12,000 ticks (both are ~1.8x the old 465 blocks), walk-in and reach GameTests,
   eye-level shots (`SCENE=rebuild_builders_workshop`).
