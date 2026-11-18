# Farmstead: the critic's scorecards (49.9, fresh critic subagents, 2026-11-18)

## Round 1: BACK
OLD/NEW: silhouette 3/3, depth 3/3, interior 2/3, detail 3/4 (two better, three needed). Notes sent back: frame flush
with the infill (set the infill a block behind the posts, windows on the bare back wall and right gable); one
unbroken ridge from house to barn (step the barn up); an empty barn floor with a loose second bed (a chamber, stalls,
a tool rack); a flat field-side gable; the second Farmer's walk to composter (8,1,13).

## Round 2: BACK on one brief miss, looks SHIP
OLD/NEW: silhouette 3/4, depth 3/4, interior 2/4, detail 3/4. Brief miss: the house's two long wall plates were saved
as `minecraft:dark_oak_log,` (recipe.mjs passed `beam('z')` unspread to `c.set`), a block no builder can place.

**The lane's decision (lane-b-1118-1538):** the miss is a one-line code slip, not a design note, and round 2 was the
last; fixed (`...(cond ? beam('z') : [P.wall])` at all six call sites), the three candidates rebuilt, every palette
name checked (no comma, all `minecraft:`), holes.py clean, no CHECK line. With the miss gone the round-2 scores
(better on all four, worse on none) make it **SHIP**. Optional notes left: film the eye-level shots (house room, barn,
loft, cellar; check the lantern on the chest at (6,2,3)); give Level I's field-side wall more frame-and-window rhythm.
