# Chapel, Graveyard, Clock Tower and Post Office (51.6): the critics' scorecards

Briefs: `chapel.md`, `graveyard.md`, `clock_tower.md`, `post_office.md`. One version each, drawn blind, each critic a
fresh subagent (lane-c-1118-1253). Eye-level shots: not filmed (scored from cutaways).

| Score | Post Office r1 OLD | NEW | r2 OLD | NEW | Graveyard OLD | NEW | Clock Tower OLD | NEW | Chapel r1 OLD | NEW | r2 OLD | NEW |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Silhouette | 3 | 4 | 3 | 4 | 2 | 4 | 3 | 4 | 3 | 3 | 3 | 3 |
| Depth | 3 | 4 | 3 | 4 | 2 | 4 | 2 | 3 | 3 | 3 | 3 | 3 |
| Interior | 2 | 4 | 2 | 4 | 2 | 3 | 2 | 3 | 3 | 3 | 3 | 4 |
| Detail | 3 | 4 | 3 | 4 | 2 | 3 | 2 | 3 | 3 | 4 | 2 | 3 |
| Verdict | | BACK (brief miss) | | SHIP | | SHIP | | SHIP | | BACK | | BACK |

- **Post Office ("the Signal Tower"): SHIP** in round 2 (17x15x15, 885 blocks). Round 1 went back only for the tower
  cornice over the door hood (roof over roof). Shipped by lane-c-1118-1253 (`workplaceBuilt_post_office` passes).
  Optional notes: the red pillar box by the path may be taken for the Mailbox; the back is one long roof field.
- **Graveyard ("the Lantern Yard"): SHIP** in round 1 (13x16x13, 570 / 652 blocks; the old 32 grave slots kept).
  The ship step swapped the designer's `undertakers_table` (no longer craftable) for the brewing stand and made
  Level II's cross-walk benches plain stairs (furniture spots with no floor under them). Optional: some sign of graves
  in Level I's bare plots (off the slots); the nave is cramped.
- **Clock Tower ("the Water Clock"): SHIP** in round 1 (15x26x15, 896 blocks incl. 7 water). Not shipped yet: the
  ship step moves `aBuilderBuildsTheClockTowerAndItTurns` to `TALL_AREA`, adds `clock_tower` to the tall list in
  `QaReleaseRefresh1028GameTests`, moves pinned spots (cartography table (7,1,9), north cuckoo clock (7,15,3), a
  gearbox (5,2,5)/(5,15,5)/(7,15,7), a shaft (5,8,5)), size (11,19,9) -> (15,26,15). Risks: copper collars over air in
  the spire (5..9,20,·) and (6..8,22,·), closest to B1384; whether a cuckoo clock links to a gearbox; the wheel's speed
  with one falling side. Optional: sills or a string course on the back and right walls; furnish the 7-tall study.
- **Chapel ("the Blossom Court"): BACK twice.** Round 1: a cornice over the door hood (brief miss), an open-air court
  splitting it into two buildings, a flat back gable, cherry crowns on the tower, 8 seats. Round 2 (one roof, four pew
  rows, recessed rose): reads as a cottage (walls 3 tall), flat side walls, cherries crowding the door, trapdoor
  belfry, a ridge cap over air (B1384's shape). **Round 3 is drawn** (narrower, taller nave to y6, stepped buttresses
  at z 8/11, set-back windows with sills, blossom bushes at the corners, barred belfry over a corbel course, the ridge
  flush at y10; 13x16x17, 879 blocks): its critic (the last round) is next.

**lane-c-1118-1553:** the Clock Tower shipped (round 1's SHIP scores above): live `clock_tower.nbt` 15x26x15, 896
blocks, 12 furniture spots; `aBuilderBuildsTheClockTowerAndItTurns` in TALL_AREA with the new pinned spots (cartography
table (7,1,9), north cuckoo clock (7,15,3) as the machine, gearboxes (5,2,5)/(5,15,5)/(7,15,7), shaft (5,8,5)) passed,
as did `qaReleaseRefresh_clock_tower` (tall area). The copper collars over air did not hang the builder (no B1384 here).
**Chapel round 3 ("the Blossom Court"): SHIP** (fresh critic, lane-c-1118-1553; from `build/rebuild/chapel_r3_sheet.png`
and `chapel_r3_cuts.png`, eye-level shots not filmed; not shipped yet: the ship is the next lane-c run's first job).

| Score | Chapel r3 OLD | NEW |
|---|---|---|
| Silhouette | 3 | 4 |
| Depth | 3 | 4 |
| Interior | 3 | 3 |
| Detail | 3 | 4 |
| Verdict | | SHIP |

Evidence: NEW reads as a chapel from three sides (central bell tower, cornice, belfry, pyramid cap over a long nave;
the back is a plain gable); stepped buttresses with stair caps between every bay; lanterns on posts at the door, a rose
window, an altar with candles, palette held. Interior ties: the pews are stubs two stairs wide (rows z=7 and z=12 one
stair), the tower entry room is bare. Brief check: nothing missed (venue middle (6,1,8) open with three air cells and
reachable; columns at (4,·,8)/(8,·,8) within 3 for the fireworks; wooden doors at z=1 and z=5, no carpet in them; no
job blocks, beds, containers, calcite or entities; leaves persistent; no roof over a roof; 13x16x17 in the 14x16x17 plot).
Ship steps: `StarterBlueprints.CHAPEL` size 11x16x16 -> 13x16x17; keep generate.py from overwriting it (REBUILT); the
venue middle moves to (6,1,8); 879 blocks, so run `BuilderGameTests.buildsChapel` (and the festival/venue tests) before
pushing; history, baseline, `RebuildScene.FILMED`, scene `rebuild_chapel`, CHANGELOG, wiki. Optional polish (not for the
ship): break the tower front's tall glass strip into two or three windows; a recessed frame or pilasters on the back
gable; full-width pews (three stairs a side) in every row; something in the tower entry room (a bench or a banner).
