# 50.12 critics (lane-c-1119-0653)

Fresh critic subagents (REBUILD.md prompt 3), one per family, each seeing only the brief, the old-vs-new sheet
(`<id>_sheet.png`), the designer's cutaways and the `.nbt`s. No eye-level shots were filmed (scenes `rebuild_<family>`
film after the push), so no interior score goes above 4.

## Trainer's House: SHIP
| Score | OLD | NEW |
|---|---|---|
| Silhouette | 2 | 4 |
| Depth | 2 | 4 |
| Interior | 2 | 4 |
| Detail | 2 | 4 |

Brief: no miss (one Training Post at (8,1,10), one bed, two chests, 859 blocks, ~52 stacks). Notes for the ship step:
the lit campfire hearth at (4,1,14) burns walkers (make it unlit/soul-less or move it off the walking line); the post sits
two cells out from the door; the declared size changes to 17x15x19.

## Ball Workshop: SHIP
| Score | OLD | NEW |
|---|---|---|
| Silhouette | 3 | 4 |
| Depth | 3 | 4 |
| Interior | 2 | 4 |
| Detail | 3 | 3 |

Brief: no miss (one smithing table, chests within reach, bed upstairs, door in line with the worker's spot). Advisory:
the open-trapdoor louvre grid covers the whole upper storey on every face (make it an accent); nothing at street level
shows the trade; the left roof slope is one plane; 1,284 blocks against the old 558.

## Research Lab (I, II, III): SHIP
| Score | I | II | III |
|---|---|---|---|
| Silhouette | 2 -> 4 | 2 -> 4 | 2 -> 4 |
| Depth | 2 -> 4 | 2 -> 4 | 2 -> 4 |
| Interior | 2 -> 4 | 2 -> 4 | 2 -> 4 |
| Detail | 2 -> 3 | 2 -> 3 | 2 -> 3 |

Brief: no miss (one Fossil Analyzer at (4,1,8) in every level, III = II above ground but the hatch, cellar ladder and
lanterns). Polish: the arrow-shaped sandstone infill on the back gables; loose sandstone stubs on the plinth; Level I has
622 blocks against 384, so run `buildsResearchLab` (9,000 ticks) early.

## Habitat Garden (I, II): SHIP
| Score | I | II |
|---|---|---|
| Silhouette | 1 -> 3 | 2 -> 4 |
| Depth | 1 -> 3 | 2 -> 4 |
| Interior | 1 -> 3 | 2 -> 4 |
| Detail | 3 -> 4 | 3 -> 4 |

Brief: no miss (one pasture at (14,1,6), a chest in the keeper's reach for the first time, centre stone (9,0,8), all
leaves persistent, ponds held in). The three new `holes.py` ALLOW lines are honest openings. Polish: the bower's flat back
wall (right view), its flat slab roof, one or two leaf kinds on the crown. Build time: 777 / 1,236 blocks against
528 / 959.

## Arena (I, II, III): SHIP
| Score | I | II | III |
|---|---|---|---|
| Silhouette | 2 -> 3 | 2 -> 3 | 3 -> 4 |
| Depth | 2 -> 3 | 2 -> 4 | 3 -> 4 |
| Interior | 2 -> 3 | 2 -> 3 | 2 -> 3 |
| Detail | 2 -> 3 | 2 -> 3 | 3 -> 3 |

Brief: no miss. Arena III has 54 seats (the brief allows a new count, never fewer than II's 30); every spot moved (the
brief allows it; the ship step writes them into `Arenas`, `CupChampionGameTests.POLE`, `ArenaCompatTests.MACHINES`).
Polish: the champion's mast crosses the Leader's gable (III front); the tower windows read blank (III back).

## Daycare (I, II): BACK (round 1)
| Score | I | II |
|---|---|---|
| Silhouette | 3 -> 4 | 3 -> 4 |
| Depth | 3 -> 2 | 3 -> 2 |
| Interior | 2 -> 2 | 2 -> 2 |
| Detail | 3 -> 3 | 3 -> 2 |

Notes: 1 windows set into the shell, a cracked-shell lip; 2 furnish both eggs (table, chair, lamp on a table, nests);
3 drop the light-blue concrete and brown glass, 60/30/10; 4 a feature on the back; 5 Level II under 1,000 blocks, the
dirt-path L made a yard or dropped. Round 2 redrawn by the same designer (sheets `daycare_r2_sheet.png`,
`daycare_2_r2_sheet.png`).

## Daycare (I, II): BACK (round 2), to the owner as it stands
| Score | I | II |
|---|---|---|
| Silhouette | 3 -> 4 | 3 -> 4 |
| Depth | 3 -> 3 | 3 -> 3 |
| Interior | 3 -> 3 | 3 -> 3 |
| Detail | 3 -> 3 | 3 -> 3 |

Brief: no miss. Notes: 1 push the log bands out and set windows in with sills (every face flat); 2 furnish the plank
ring round each nest (seat, table with lamp, storage; egg II too); 3 a few large deliberate spots per face, one base
course instead of hay bands on the back; 4 a path to egg II's east door, tidy loose planks and fences on the lawn;
5 round the stepped crown. Two rounds spent: the old drawing stays live.

## Ship note
Arena and Habitat Garden are held on `wip/lane-c-50.12`: both move spots the code reads at fixed positions, which would
break standing old buildings on the live server (ROADMAP Notes, 2026-11-19 lane-c-1119-0653).

## Pokémon Center (default, Lodge, Plaza; I, II): BACK, BACK, BACK (round 1)
Sheets `pokemon_center{,_2,_lodge,_lodge_2,_plaza,_plaza_2}_sheet.png` (five views and cutaways at y <= 3 and y <= 7,
candidates from each look's `build.sh`); eye level not filmed. Cobblemon's blocks (machine, PC, Pasture Block) render
as air in these shots.

| Score | default I | default II | Lodge I | Lodge II | Plaza I | Plaza II |
|---|---|---|---|---|---|---|
| Silhouette | 2 -> 4 | 2 -> 4 | 3 -> 3 | 3 -> 3 | 3 -> 3 | 2 -> 4 |
| Depth | 2 -> 3 | 2 -> 3 | 3 -> 3 | 3 -> 3 | 3 -> 3 | 3 -> 3 |
| Interior | 3 -> 3 | 3 -> 3 | 3 -> 3 | 3 -> 3 | 3 -> 3 | 2 -> 3 |
| Detail | 3 -> 3 | 2 -> 3 | 3 -> 3 | 3 -> 3 | 3 -> 2 | 3 -> 3 |

Evidence: default, the red dome and its eye are known in outline from every side where the old one is a hip-roofed box
with a flat white cap (front, top); its drum walls are flat between the quoins (right, back); the I desk, carpet aisle and
barrels match the old hall's furnishing, not beat it (cutaway y <= 3); II's trade hall, pavilion and garden beat the old
jammed boxes and fenced lawn (left, back). Lodge, the new 45-degree gable and porch is a strong front, but the sides are
a plain roof slope over a flat log wall (right), against an old cross-gabled, half-timbered lodge of the same strength
(left); II's wing roof runs into the main slope as a lump (top) and the paddock is a bare fenced lawn (back). Plaza, I's
arcade and bell tower front is good, but the roof reads ragged and patchy from above (top, cutaway y <= 7) and the back
and sides are flat pale walls (back); II's L round a fountain court is a real shape and a used court (top, cutaway y <= 3).

Brief: no hard miss in any look (checked on the `.nbt`s): one Healing Machine at (6, 1, 7), PC at (10, 1, 7)-(10, 2, 7),
(6, 1, 8) open, 2-3 chests or barrels within reach, a ground-floor front door on the z = 0 side, I 649-782 blocks, II keeps
98-100 % of its I by block, sizes within the plots. The Pasture Block and Shop Counter move (default II (9, 1, 19),
(15, 1, 7); Lodge II (10, 1, 19), (15, 1, 6); Plaza II (4, 1, 21), (16, 1, 18)), which the brief allows the ship step,
with the same live-server caveat as the Arena below. Risk: default and Lodge I are 762 and 782 blocks against the brief's
"keep Level I lean (the old one is 560)" for the 12,000-tick build; confirm `buildsPokemonCenter(Lodge)` timings.

Notes, default (II passes; I does not):
1. I right and back views: the drum's walls are flat white between the quoins; set the windows a block back with sills
   and stand a pilaster proud at each octagon vertex up to the eave.
2. I left and right views: the lowest dome rings step unevenly at the eave; make the bottom two rings one even overhang.
3. I cutaway y <= 3: the lining ring eats the edge cells; give the lobby a row of waiting seats and the room behind the
   desk a lit, furnished Nurse's corner.
4. II back view: the garden is mostly bare lawn round the pavilion; plant beds in soil and lead a path to the Pasture Block.
5. I's 762 blocks: check the 12,000-tick build; trim lining the eye never sees before trimming detail.

Notes, Lodge:
1. I right view (and II left): the long sides are a flat log wall under an unbroken roof slope; add a dormer or a side
   porch and stand the frame posts proud of the infill.
2. II top and cutaway y <= 7: the wing's roof runs into the main slope as a lump; give the wing its own gable end and a
   clean valley.
3. II back and right views: the paddock is a fenced lawn with a trough and hay; add a lean-to shelter and a path from the
   wing's door.
4. I back view: the rear gable is blank below its window; a balcony, beam ends or a back porch.
5. I front and right views: the same red shutter pair on every window; add sills or window boxes and vary them.

Notes, Plaza (II passes on silhouette and interior only):
1. I top and cutaway y <= 7: the terracotta roof reads ragged, with stepped patches and gaps; make one clean hip with a
   continuous eave.
2. I back, left and right views: flat pale walls with flush windows; carry the front's arcade or pilasters round the back.
3. I and II left views: white walls beside sandstone break the palette; one wall material, sandstone with a single trim.
4. II right view: the market hall's long side is a plain wall under its roof; open it as an arcade onto the court.
5. II cutaway y <= 3: the fountain stands alone on sand; benches and planters in soil round it.

## Pokémon Center (default, Lodge, Plaza; I, II): round 2
Verdict: **default SHIP, Lodge SHIP, Plaza BACK** (Plaza II passes; Plaza I does not, and a look ships only with both
levels). Sheets `pokemon_center{,_2,_lodge,_lodge_2,_plaza,_plaza_2}_r2_sheet.png` (front, back, left, right, top) and
`..._r2_cut_sheet.png` (y <= 3 and y <= 7, top and front; OLD cut from the shipped `.nbt`s the same way), all in this
folder. Cobblemon's blocks render as air; eye level not filmed. Judged fresh, without round 1's numbers.

| Score | default I | default II | Lodge I | Lodge II | Plaza I | Plaza II |
|---|---|---|---|---|---|---|
| Silhouette | 2 -> 4 | 2 -> 4 | 3 -> 4 | 3 -> 4 | 3 -> 4 | 2 -> 4 |
| Depth | 2 -> 4 | 2 -> 4 | 3 -> 4 | 3 -> 4 | 3 -> 4 | 3 -> 4 |
| Interior | 3 -> 4 | 3 -> 4 | 3 -> 4 | 3 -> 4 | 3 -> 3 | 2 -> 4 |
| Detail | 3 -> 3 | 2 -> 4 | 3 -> 4 | 3 -> 4 | 3 -> 2 | 3 -> 3 |

Evidence, default:
- I silhouette: the dome and lantern read in outline from all four sides, where the old one is a hip-roofed box with a flat cream cap (top).
- I depth: stone pilasters now stand proud at the corners, with windows set back over sills (back, right); the old walls are flat render (back).
- I interior: waiting benches line both sides of the red aisle, with a stocked corner of barrels, chests and shelves behind the desk (cutaway y <= 3 top); the old hall is a desk and a carpet.
- I detail: even. The new I lost the old flower boxes and lamp posts by the door (front), and the dome's lower rings still step a little unevenly (back).
- II silhouette and depth: the dome, the gabled trade hall and the garden pavilion make one composition (front, left), where the old one is two boxes jammed together (left).
- II interior: the trade hall and the hall are furnished, and the garden is laid out (cutaway y <= 3 top).
- II detail: planted beds in soil, paths to the Pasture Block and a roofed pavilion (back), against a bare fenced lawn (old back).

Evidence, Lodge:
- I silhouette: steep front gable and porch deck, a cross gable on the right, a stone chimney and dormer on the left, and a balcony porch at the back. Every side is worked (front, right, left, back); the old one has a good front but a plain back slope (old back).
- I depth: a stone plinth, beam ends under a jettied gable and window boxes (right); the old one has proud posts on the front only.
- I interior: desk, red carpet, benches and a storage corner (cutaway y <= 3 top), against a sparser old hall.
- I detail: window boxes with flowers, vines on the plinth and a lamp on the chimney (right); the red shutter pair still repeats (front).
- II silhouette: the wing now meets the hall in a clean L valley (top); the old two-storey jettied front is strong, but its back is one long slope (old back).
- II depth: the wing keeps the plinth and beam work (right).
- II interior and detail: a roofed shelter in the paddock, a trough and a gravel path from the wing (back, top), where the old paddock is a bare fenced lawn with hay.

Evidence, Plaza:
- I silhouette and depth: the bell tower over an arcade front is recognisable. Deep arched window recesses now run round the back and sides under one hip roof (back, right). The old one is a crenellated block with a red cap.
- I interior: rooms with seats and chests, but cut up and no clearer than the old desk-and-shelves hall (cutaway y <= 3 top).
- I detail: it loses. The old one has planters, lamp posts and striped awnings at the door (old front); the new I stands on bare grass with one hanging lantern on the tower, and the arches read as raw holes (front, right).
- II silhouette: the L round a fountain court with a gabled market hall reads clearly (top, back), where the old one is a stacked block.
- II interior: the court has a fountain, planters and seats, and the rooms are furnished (cutaway y <= 3 top).
- II detail: even. The court is good, but the outside is bare grass and the market hall's long side is a plain wall of window holes (right).

Brief (checked on the `.nbt`s), no miss:
- One Healing Machine at (6, 1, 7) with (6, 1, 8) and (6, 2, 8) open, and the PC at (10, 1, 7)-(10, 2, 7), in all six.
- 2-3 chests or barrels within 8 across and 4 up or down of the machine.
- A ground-floor front door facing the z = 0 side: default (6, 1, 1), Lodge (6, 1, 2), Plaza (6, 1, 3) behind its open arcade.
- Only vanilla, ours and Cobblemon's blocks.
- Sizes: I 13 x 16/15/13 x 13; II 20 x 16 x 26, 21 x 15 x 26 and 20 x 13 x 26, all within the plots.
- II keeps its I: 99 %, 98 % and 100 % of I's blocks unchanged, and no block entity moves.
- Pasture Block and Shop Counter, each moved from the pinned cell, which the brief lets the ship step re-aim:

| Look | Pasture Block (pinned today) | Shop Counter (pinned today) |
|---|---|---|
| default II | (9, 1, 19) (was (6, 1, 22)) | (15, 1, 7) (was (6, 1, 14)) |
| Lodge II | (10, 1, 19) (was (6, 1, 15)) | (15, 1, 7) (was (16, 1, 6)) |
| Plaza II | (3, 1, 22) (was (6, 1, 21)) | (16, 1, 18) (was (6, 1, 14)) |

Plaza's QaLonePanes arch check and the Lodge doorway walk tests also need re-aiming at the ship step.

Level I block counts, against the brief's lean-Level-I wish (old 560): default 718 (wish 718), Lodge 762 (wish 762), Plaza 742 (wish 742). Each is exactly at the wish. Confirm the 12,000-tick `buildsPokemonCenter(Lodge|Plaza)` runs at the ship step.

Notes, Plaza (to its designer; II is accepted as it stands, keep it):
1. I front and right views: the outside is bare. Put the old one's kind of street life back at the door: planters in soil either side of the steps, lamps on posts, and an awning or shade over the arcade.
2. I right and back views: the side arches read as raw holes. Give each a sill, a keystone or trim course and a filled lower part (a bench or panes), so they read as windows, not damage.
3. I cutaway y <= 3 top: the hall is cut into small pockets. Open one clear waiting room from the door to the machine, with seats facing the desk and the storage corner behind it.
4. I top and right views: the low stepped hip has almost no eave. Run one continuous overhang with a cornice under it all round.
5. II right view: the market hall's long side is still a plain wall of openings. Open it as an arcade onto the court, or give it pilasters between the windows.

## Pokémon Center, Sunny Plaza (I, II): round 3, BACK, to the owner as it stands
Fresh critic (lane-c-1121-0653), sheets `pokemon_center_plaza{,_2}_r3_sheet.png` and `..._r3_cut_sheet.png` in this
folder; eye level not filmed. Brief: no miss (machine, PC, chests, door, sizes, Pasture Block and Shop Counter, II
keeps 99.2 % of I). Three rounds spent: the old Plaza drawing stays live; the round 3 candidate stays in
`js/pokemon_center_plaza/` for the owner to judge.

| Score | I | II |
|---|---|---|
| Silhouette | 3 -> 4 | 3 -> 4 |
| Depth | 3 -> 3 | 3 -> 3 |
| Interior | 3 -> 3 | 3 -> 3 |
| Detail | 3 -> 4 | 3 -> 4 |

Notes (if the owner wants another round): 1 left, right and back (both levels): flat sandstone with one red band; a
plinth, proud corner piers and sills under the side and back windows; 2 I back: a worked feature (a bay, a porch or a
chimney); 3 II y <= 3 top: the 4 x 11 shop wing is nearly empty, furnish both long walls and close the dead end past
the counter; 4 film eye level before judging interior; 5 I y <= 7: the hall is one low room under a tall roof void,
raise the ceiling or trim the roof (also keeps I lean, 785 blocks against the old 560).
