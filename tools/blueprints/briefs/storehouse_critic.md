# Storehouse (51.3, flagship: A and B): the critics' scorecards

Brief: `storehouse.md` (also `storage_hall.md` and `warehouse.md`, 51.3's other two families, not designed yet).
Designs, each drawn blind by its own designer: `tools/blueprints/js/storehouse_a/` ("the Hoist House": a steep brick
gable with its ridge hooded over the street, a hoist beam and chain over a loading hatch above a cart gate; II adds a
lower cart shed with its own gable, III a long back range; a 4-deep vault under a hatch; 11x15x13, 18x15x13,
18x15x19; 44, 60 and 84 chests) and `tools/blueprints/js/storehouse_b/` ("the Canal Warehouse": loading doors stacked
under a hoist arm, a crow-stepped gable, the Storehouse block as a counting desk between stacked chests; II a lower
twin gable with a cart arch, III a rack hall across the back; 11x15x13, 17x15x13, 17x15x19; 59, 91 and 127 stores).
Sheets (OLD left, NEW right): `storehouse_a_sheet.png`, `storehouse_b_sheet.png`; A and B side by side:
`storehouse_pick.png`. Eye-level shots: not filmed yet. Every round's critic was a fresh subagent. Candidates:
`build/rebuild/storehouse_{a,b}/`.

## Round 1: A BACK, B SHIP

| Score | OLD (A's critic) | A | OLD (B's critic) | B |
|---|---|---|---|---|
| Silhouette | 3 | 4 | 3 | 4 |
| Depth | 3 | 3 | 3 | 3 |
| Interior | 2 | 3 | 2 | 3 |
| Detail | 3 | 3 | 3 | 4 |

B's polish for the ship step: a dormer or gable on the long III back roof; set the side brick panels back from the
stone bands; a tally desk (a table, not a job block) by the Storehouse block. A's notes (applied): frame posts and
courses on the flat back and sides; a working corner by the Storehouse block and a landing under the hoist; dormers on
the long sides; break the roof sheet; a loading bay for the wing door.

## Round 2: A SHIP

| Score | OLD | A |
|---|---|---|
| Silhouette | 3 | 4 |
| Depth | 3 | 4 |
| Interior | 2 | 3 |
| Detail | 3 | 3 |

A's polish for the ship step: tidy the loose roof-edge stairs beside the front gable; one non-store piece by the
Storehouse block.

## Brief checks (both, on the candidate files)

One Storehouse block in the same cell at every level, one lantern standing on it touching two or more chests; every
store a plain chest within 8 across and 4 up or down of it (vault chests no lower than y -3); the vault 4 deep behind
closed walls, a closed hatch with 2 clear above, a ladder of 3 rungs, a lantern below y 0, open floor over the cellar
that isn't the way down; no job blocks or beds; each level keeps 96-99% of the one below; Level I about 34 stacks
(limit 108); check.py and holes.py clean. For the ship step: the five village variants (the village contract's door
at (4,1,2) in a 9-wide front: A's body is 9 wide with its door at x 5, so its village look is drawn narrower; B fits
as drawn) and the Sophisticated style (works for both). Not tested: the GameTests (the 9,000-tick build, walking to
every chest, the light check).
