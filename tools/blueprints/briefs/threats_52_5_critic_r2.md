# 52.5 critics, round 2 (lane-d-1118-1909): Raiders' Stockade, Bandit Fort, Bandit Camp, Caravan Wreck

Sheets (old left, new right, five views): `build/rebuild/sheets/<id>_sheet.png`, not committed (built with a scratch copy
of `render/shoot.mjs` + `sheet.py`); the designers' cutaways in `build/rebuild/<family>/renders/`. No eye-level shots.
Each critic was a fresh subagent and checked the brief on the candidate .nbt with `holes.load` / `check.check`.

| Family | Silhouette | Depth | Interior | Detail | Verdict |
|---|---|---|---|---|---|
| raiders_stockade (round 2) | 2 → 4 | 2 → 4 | 3 → 4 | 3 → 4 | SHIP |
| bandit_fort, _2, _3 (round 2) | 3 → 4 | 2 → 4 | 3 → 4 | 3 → 4 | SHIP |
| camp/bandit_camp (round 1) | 2 → 4 | 2 → 3 | 2 → 3 | 2 → 3 | SHIP |
| caravan_wreck (round 1) | 3 → 3 | 2 → 3 | 2 → 3 | 2 → 3 | SHIP |

Brief: nothing missed on any of the four.

Later-pass notes (not blocking):
- stockade: now 14 tall (old 9), so it fits fewer places: watch CaravanTrail.fits / the pack test. A lectern in the
  raiders' tent is odd. A guard can still spawn on the pinned campfire (9,1,8) (the arc's spawn could skip it).
- fort: (I back) the back wall between posts is still one plane: a hoarding or raised post pair. (keep-cut12_top) the
  loft is bare: a lamp and stores. Film the throne hall, chamber, a tent and a tower platform.
- camp: (front) the cart wheel is a flat 3x3 square; (right) the tower's east face is flat; (left) the white bunk tents
  are plain wedges; the cloth palette is busy.
- wreck: (top) the edge rows z=12 and x=12 are a straight dirt-path L that stops at the footprint; (right) the plank bed
  wall reads as a shed: an axle or broken plank; (front/right) the upper wheel needs a rim.

Ship-step facts: camp's loot chest is (8,1,11) (needs `LootTable aliveworkplace:chests/bandit_camp`), (13,1,10) has
none; camp's y 0 cells that aren't drawn must be left out of the file, not saved as air; CampCleanup needs the old
drawing fallback first (brief); holes.py's ALLOW ("camp/bandit_camp", (5,1,11)) can go. Wreck is 13x13 (was 13x12):
the test's `lies(..., 13, 12)` and the wiki change. Shared `parts/canvas.mjs` `mix()` reportedly returns only the
first block at small weights (wreck designer): not fixed, filed in the Notes.
