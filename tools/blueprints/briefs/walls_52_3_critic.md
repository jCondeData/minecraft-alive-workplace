# Walls kit (52.3), round 1: the critic's scorecard

Critic: a fresh subagent that designed neither version (lane-d-1118-0408). Sheets `stone_wall_sheet.png`,
`stone_wall_2_sheet.png`, `wall_tower_sheet.png`, `wall_tower_2_sheet.png` (OLD left, NEW right) and the candidates'
cutaways in `build/rebuild/<family>/renders/`. No eye-level shots (no furnished rooms). Both `selfcheck.py` PASS,
`holes.py` 0, `check.check()` 0 on all four NEW drawings (OLD `wall_tower` has a floor lantern at (1,7,1)).

## stone_wall / stone_wall_2: SHIP (round 1)

Brief: nothing missed (plots 7x6x3 and 7x9x4, walk z=1 y=3 with y4-5 clear, outer row solid y0-3, ladder column
(3,0..4,2) solid and (3,5..6,2) clear, plain outer faces under 8, 100% of level I kept, stairs at y>=6, no gate, no POI).

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 2 | 3 | Front/left: a stair wedge climbing the village face and a lamp post at its foot; back still a near-flat face. |
| Depth | 2 | 3 | Front/left/top: the flight stands a block proud, cobble plinth to brick; II's gable roof has eaves over both faces. |
| Interior | 2 | 3 | Front/top: a real flight onto a polished-andesite walk; II cut5: two hanging lanterns under the ridge. |
| Detail | 2 | 3 | Front: lantern on a wall post, andesite walk band, sparing moss; II back: a steady bracket, board, post rhythm. |

Risks (not blocking): overlapping copies (`Walls` overlaps segments) cut earlier copies' flights into stubs (looks
only; each stretch keeps a whole flight); II's gallery boards carry posts at even x (as OLD).

## wall_tower / wall_tower_2: BACK (round 1)

Brief: nothing missed. Pins to move at ship: stations at template y 8 (stood on), 16 of them incl. crenel cells (e.g.
(1,7,4)); `onTheGroundAnArcherClosesInFirst` must fill all 16; the raiders' side station in
`anArcherTakesTheTowersTopAndShootsFromIt` moves.

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 2 | 2 | wall_tower_2 front/left/right/top: the same 5x5 shaft under the same two-step dark-oak pyramid as OLD II. |
| Depth | 2 | 2 | Front/back: andesite courses flush, a half-block notch ring at y 6, no overhang on II's roof. |
| Interior | 1 | 2 | Cutaways cut2/cut5: two storeys, hatch, lanterns, but both rooms empty. |
| Detail | 2 | 3 | Front: stair hood, plinth, courses, hanging lanterns, log caps carrying II's roof. |

Notes:
1. wall_tower_2 front/left/right/top (silhouette): give the upgrade its own crown in y 8-11 (merlons whole to y 9 with
   the roof seated on them and a raised centre cap, or a small gable over each side's two stations); II's top view must
   no longer match OLD's.
2. All four sides, both levels (depth): a battered foot (outward stairs at y 0 on the face middles, not under the door);
   the y 6 band as machicolations (upside-down stairs on the twelve edge-middle cells, corners full as piers); slits
   with a reveal (sill and head).
3. Cutaways cut2/cut5 (interior): furnish as a guardroom with no bed and no POI: ground room a stair bench and table
   (a chest if wanted), upper room an arrow store or rack under the slits; check.py's crockery and lamp rules at zero.
4. Left/right (kit join): the segment's walk (y 4) dead-ends on a blank x 0 / x 4 side; mark where it meets the tower
   (a string course at walk height) or say in the notes why it ends.
5. wall_tower_2 front/back (detail): light every side's pair of gallery stations, not only (2,9,3).

## wall_tower round 2: SHIP

Fresh critic. Brief: nothing missed (one door (2,1..2,0), no gate blocks or POI, 5x10x5 and 5x12x5, 16 stations all
stood on at y 8 with crenels on every side, II two roofed gallery stations per outer row, 166 of 174 kept). selfcheck
PASS, check.check/owner_problems 0, holes 0.

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 2 | 3 | Front/back/left (I): battered foot, shadow band at y 6, stepped crown of capped merlons; II front/top: a roofed gallery on piers unlike OLD's pyramid (its flat deck reads weaker than a roof). |
| Depth | 2 | 3 | Back: stair foot, machicolation shadow, slit sills, door hood, andesite quoins; no overhang possible in 5x5. |
| Interior | 1 | 3 | cut2: table, lantern, ladder; cut5: chest and lantern. Furnished and lit but thin. |
| Detail | 2 | 3 | II: a lantern on each side between the merlons; disciplined stone palette; OLD I breaks the lamp rule. |

Risks: the ship step moves the station pins (`onTheGroundAnArcherClosesInFirst` fills all 16, the raiders' side station
is a crenel at (1,7,4) or (3,7,4)); a corner tower's door faces the other run's segment; II's roof has no eaves.
