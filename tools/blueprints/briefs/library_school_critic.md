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
