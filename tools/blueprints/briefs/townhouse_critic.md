# Burgher's Townhouse: the critic's scorecard

Critic: a blind reviewer (did not design either version), 2026-11-17. Evidence: `build/rebuild/townhouse_{1,2,3,4}_sheet.png`
(OLD left, NEW right: front, back, left, right, top), `build/rebuild/townhouse_cutaways.png` (NEW only, both columns),
`build/rebuild/townhouse/renders/*-cut*` and the candidate `.nbt` / `furniture.json` files read with `holes.load`.
Eye-level furnished shots: **not filmed**, so no score below rests on an eye-level view; Interior is judged from
cutaways and the furniture plan and is capped at 4.

## Scores

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 3 | 4 | OLD: I and II are a stone-and-plaster box under a flared hip "hat" with a detached brick chimney pipe (sheet 1, front/left/right); the back is a flat slab (sheet 1, back); III-IV add a tall square tower that reads well (sheet 3, front). NEW: a steep front gable over a jettied timber storey, dormers down the left slope, a stepped chimney breast up the back (sheet 1, front/back/left); II adds a second cross-gable wing (sheet 2, front, top), III a round-capped stone tower with string courses (sheet 3, back/right). It loses a point for the right side of I and II: one unbroken roof plane down to the jetty, no dormer or break (sheet 1 and 2, right). |
| Depth | 2 | 4 | OLD: flat ashlar and plaster walls, one-block eave, windows flush; the only relief is the window boxes on the front (sheet 1, front vs back). NEW: timber storey jettied out over the stone ground floor, dark-oak frame proud of the infill, shutters, sills, flower boxes, bargeboards on the gables, eaves with a lip, chimney breast stepping out from the back wall (sheet 1, front/back; cut13_back). Not 5: the ground floor's right and left faces are flat stone with shutters only (sheet 1, right). |
| Interior | 2 | 4 | OLD: its furniture plan has 1 table and 2 chairs for 3 beds, and standing lanterns on a spruce slab, a bookshelf and a barrel (block data; no OLD cutaway on the sheet, lit windows only on sheet 1, front). NEW: ground floor split into kitchen with counters and a dining room (cutaways, I y4), bedrooms upstairs (I y8), II's wing adds two beds and a parlour, III adds a tower room, IV's cellar has barrels, a chest, a workbench, ladder and three lanterns (cutaways, IV cellar); 3/5/6/6 beds, 6/10/11/11 chairs, every bed reached from the door (`check.reachable`). Capped at 4: no eye-level shots. |
| Detail | 2 | 4 | OLD: four materials fighting (brick chimney and brick yard wall against grey stone, cream plaster and dark-oak roof, sheet 2, back), lanterns standing on slabs and furniture (owner rule 2, baselined). NEW: one palette held across all four levels (stone-brick base with cobble plinth and mossy/cracked accents, dark-oak frame, white-terracotta infill, polished-granite roof), flower boxes with soil, chains for hung lamps, `check.check` and `owner_problems` return nothing on all four levels. Not 5: the kitchen hearth (see note 1). |

NEW beats OLD by at least one point on all four scores and loses on none.

## Brief check (NEW)

All met, checked on the block data where the brief allows it:

- Beds: I 3, II 5, III 6, IV 6; no bed moves between levels; every bed head reached from the ground floor.
- Furniture: every level has every FULL kind (bed, chair, table, nightstand, lamp, couch, shelf, crockery, clock,
  picture, blinds).
- Chimney: Townhouse I has a campfire at (5, 21, 13) on 21 solid stone-brick blocks.
- Glass: 60 panes in I; `check.check` (incl. open panes) clean on all four.
- Cellar (IV): exactly 4 deep (IV is III's height + 4, III kept 100 % one layer up), 3 ladders, 3 lanterns, reachable.
- Front door on the ground floor at the front (5, 1, 2), same in every level.
- Kept cells: II keeps 98.4 % of I, III 99.1 % of II, IV 100 % of III shifted by 4; no block-entity block or bed moves.
- Fits: I 11x22x14 (limit 15x28x16), II 18x22x14, III 19x26x19 (limit 21x28x22), IV 19x30x19.
- Material: about 57 / 71 / 83 stacks for I / II / III (limit 108).
- Not checkable here (no Gradle): build times, styles leading to the next tier, and the pinned bed cell
  (4, 6, 8) of `HomeGradeGameTests`, which the ship step moves. Declared sizes in `StarterBlueprints` must change to
  (11, 22, 14), (18, 22, 14), (19, 26, 19), (19, 26, 19) basement 4.

## Verdict: SHIP

Notes to take along (not blocking):

1. **Cutaways, I y4 / block data:** the ground-floor campfire at (5, 1, 11) is an open fire under a plank ceiling
   with the red bed directly above at (5, 6, 11) and no flue; the chimney stack at z = 13 is not connected to it. Put a
   hood or flue over it (or move the stove into the chimney breast) so the hearth and the chimney are one thing.
2. **Sheets 1 and 2, right:** the right roof slope of I and II is one unbroken plane; a dormer or a second chimney
   there would give that side its own shape.
3. **Eye-level shots:** film `SCENE=rebuild_townhouse` before the owner judges the interior; the Interior score above
   is from cutaways only.
