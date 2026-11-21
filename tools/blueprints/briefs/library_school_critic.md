# Library, School and Schoolhouse (51.4): the critics' scorecards

Briefs: `library.md`, `school.md`, `schoolhouse.md`. One version each, drawn blind by its own designer:
`tools/blueprints/js/library/` ("the Lamplit Nave": a buttressed nave with a rose-windowed gable and a gabled porch;
II adds an aisle wing, III a spired tower holding the enchanting room ringed by 26 shelves, IV an archive basement;
13x15x12, 18x15x12, 18x18x22, IV the same plus 4 deep), `tools/blueprints/js/school/` (the Move Tutor's court: a
gatehouse onto a sky-lit court with a sparring ring round the Training Post, two open cloisters, the Tutor's hall and
quarters behind; 17x14x20) and `tools/blueprints/js/schoolhouse/` (a bell-cote schoolroom open to the roof timbers,
bench rows facing the lectern and blackboard; II adds an infants' reading wing and a fenced schoolyard; 11x16x13,
17x16x13). Sheets (OLD left, NEW right): `library_sheet.png`, `school_sheet.png`, `schoolhouse_sheet.png`. Eye-level
shots: not filmed yet. Every round's critic was a fresh subagent. Candidates: `build/rebuild/<family>/`.

## Round 1: all three BACK

| Score | Library OLD | NEW | School OLD | NEW | Schoolhouse OLD | NEW |
|---|---|---|---|---|---|---|
| Silhouette | 3 | 3 | 3 | 3 | 4 | 4 |
| Depth | 3 | 4 | 3 | 3 | 3 | 4 |
| Interior | 2 | 4 | 2 | 3 | 3 | 3 |
| Detail | 3 | 3 | 3 | 4 | 3 | 4 |

Notes applied: Library: buttresses and windows on the bare back gable, a front porch, a real window in the aisle
gable, lamps at the door, a stone band (the gable overhangs not done: the plot is full). School: one broad gatehouse
gable, eaves on the wings, the frame proud of the plaster, a chimney on the flat back, lamps over the arcade benches.
Schoolhouse: a front of class (desks, blackboard, pictures), a dormer, a pointed bell-cote cap, a fenced schoolyard
for II.

## Round 2: all three SHIP

| Score | Library OLD | NEW | School OLD | NEW | Schoolhouse OLD | NEW |
|---|---|---|---|---|---|---|
| Silhouette | 3 | 4 | 3 | 4 | 3 | 4 |
| Depth | 2 | 4 | 3 | 4 | 3 | 4 |
| Interior | 2 | 3 | 2 | 4 | 3 | 4 |
| Detail | 3 | 3 | 3 | 4 | 3 | 4 |

Polish and risks for the ship step: Library: film the eye-level shots; II's front is plainer than the old II tower;
the gable's cross window reads as a white outline. School: even the stepped brick roof edges on the porch gable;
join the gatehouse gable to the wing roof; `holes.py` gains two ALLOW lines for the open cloisters. Schoolhouse: II
adds little from outside; the back is the plainest side.

## Brief checks (on the candidate files)

Library: lecterns 1/2/3/3 with free standing spots; 42 plain bookshelves and no container in Level I; III's
enchanting table with 26 shelves counted the vanilla way and all three lecterns within reach; nothing below y 0 before
IV; each level keeps 95-100% of the one below; desk, chair, picture and hanging_pot spots on every level. School:
exactly one Training Post and no other job block, one bed, one chest; 17x14x20 in an 18x18x20 plot; about 51 stacks
(budget 81). Schoolhouse: one lectern in the same cell on both levels, no chest, barrel or bed in Level I; class floor
within 5 blocks of the lectern; bench, desk and picture spots on every level. All: check.py and holes.py clean.
Ship step: `StarterBlueprints` sizes change (SCHOOL 17x14x20; SCHOOLHOUSE 11x16x13 and 17x16x13; LIBRARY per level),
the village variants (`village/<style>_school`, `_schoolhouse`) and modded copies are regenerated, `school` gets a
`blueprint_history/` entry first, the schoolhouse's `NOT_A_ROOM` belfry entries and the "03_schoolroom" camera move.
Not tested: the GameTests (build to DONE, the build-time and stack limits, the furnished compat tests).

## School round 3 (back to the designer after the in-game failure, lane-c-1118-0654): critic NOT run yet

Round 2's School shipped nowhere: 1,410 blocks, `WorkplacesGameTests.workplaceBuilt_school` timed out in STRUCTURE
at 12,000 ticks, the builder stuck under the gate roof's back barge (4,7,5) over the west cloister's lean-to. Added to
the brief for this round: at most about 900 placed blocks, no overhang, barge or eave over another roof.

The redesign ("the Half Court", `js/school/plan.mjs` notes): the gatehouse with a moon window in both gables stays the
hero, the court and its sparring ring round the Training Post stay; one cloister (west, the spectators' bench) instead
of two, a low stone yard wall with lantern piers and the straw targets on the east; the hall is two blocks shallower
(walls z 12..16, ridge y 7) with the lecture room (table for four, move-book cupboards, desk by the hearth) and the
Tutor's quarters (bed, nightstand, chest) behind a bookshelf screen. Every roof stands apart: the gate roof's back
barge (z 4) hangs over open paving, the lean-to runs to the hall's wall and the hall's front eave stops at it.

Candidate `build/rebuild/school/school.nbt`: **17 x 12 x 18, 903 blocks** (round 2: 1,410; the old School 759);
rebuild.py prints no CHECK line; holes.py clean with one ALLOW line (the open cloister, (2, 1, 9)); exactly one
Training Post, no other job block, one bed, one chest, no calcite/jigsaw/void; about 45 stacks. A scratch check that
flags a roof or trim block whose air column lands on another roof's tile found 4 in round 2 (the stuck barge among
them) and 0 here. Sheet: `school_sheet.png` (OLD left = the live 759-block School, NEW right; five views and two
y <= 3 cutaways).

**Left to do:** the critic step. This designer session had no tool to start a fresh subagent, so no blind critic has
scored round 3: the lane runs REBUILD.md's critic prompt with `briefs/school.md` and `briefs/school_sheet.png` (eye-level
shots: not filmed) and records the verdict here. Round 2 was SHIP, so this is the first return of two. Then the ship
step (the declared size changes to 17 x 12 x 18; the GameTest decides whether 903 blocks fit 12,000 ticks).

## School round 3: critic (lane-c-1118-0953): SHIP

Blind critic on `school_sheet.png` (no eye-level shots). OLD / NEW: silhouette 3 / 4 (OLD's back is a long box under
one roof; NEW's gate gable rises over the hall roof from the back, the right side with the low yard wall is its thinnest);
depth 3 / 4 (timber storey on stone piers over the passage, barge boards and eaves on every side of the hall; the east
yard wall is flat); interior 2 / 4 (OLD a bare plank U round a porch; NEW the post in a sky-lit ring, targets, cloister
bench, reading table for four, desk, hearth, quarters behind a screen, all lit; the hall is a 3-deep strip with posts
standing in it); detail 3 / 4 (banners on the gate piers, window boxes, lantern piers, a smoking chimney; palette held).
NEW beats OLD by one on all four, loses on none. Brief check: 903 blocks, one Training Post (8,1,8) and no other job
block, one bed, one chest, the gate passage clear, no trapdoor in the front rows, no roof over another roof, holes.py 0.
Risks the ship step settles: 903 blocks against "about 900" (the GameTest decides); check.py's bed-by-door rule for
the bed foot (13,1,14) diagonal to the screen door (12,1,13); eye-level shots in the client.
