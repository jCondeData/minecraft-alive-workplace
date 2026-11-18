# Guildhall, Trade Hall, Leader's Hall and Map Room (51.5): the critics' scorecards so far

Briefs: `guildhall.md`, `trade_hall.md`, `leaders_hall.md`, `map_room.md`. One version each, drawn blind:
`tools/blueprints/js/leaders_hall/` ("the Challenge Hall": a gate-porch, benches facing a sparring ring, a dais with
the Training Post; 15x15x17), `tools/blueprints/js/map_room/` ("the Lantern Tower": a slim shaft with a corbelled,
glazed lookout and balcony, a compass rose round a lodestone under the table; 9x23x9),
`tools/blueprints/js/trade_hall/` ("the Ball Tower": a timber hall with a round corner tower capped in the colors of a
Poke Ball, the Trader's bed in the tower room; 16x18x16) and `tools/blueprints/js/guildhall/` ("the Market
Guildhall": an open market arcade with the guild's hall jettied over it; II a counting-house cross-wing, III a
strongroom under a hatch). Sheets (OLD left, NEW right): `leaders_hall_sheet.png`, `map_room_sheet.png`,
`trade_hall_sheet.png`. Eye-level shots: not filmed. Every critic was a fresh subagent.

| Score | Leader's Hall OLD | NEW | Map Room r1 OLD | NEW | r2 OLD | NEW | Trade Hall r1 OLD | NEW | r2 OLD | NEW |
|---|---|---|---|---|---|---|---|---|---|---|
| Silhouette | 3 | 3 | 3 | 4 | 4 | 4 | 3 | 3 | 3 | 4 |
| Depth | 3 | 4 | 4 | 3 | 4 | 3 | 4 | 3 | 4 | 3 |
| Interior | 2 | 3 | 2 | 3 | 2 | 3 | 2 | 3 | 2 | 3 |
| Detail | 2 | 4 | 4 | 3 | 3 | 3 | 3 | 3 | 3 | 3 |
| Verdict | | SHIP | | BACK | | BACK | | BACK | | BACK |

- **Leader's Hall: SHIP** (round 1). Polish: carry the side posts round the flat back wall; one straight trim line in
  the front gable instead of the zigzag of stairs. Ship step: `StarterBlueprints` size (13,14,14) -> (15,15,17).
- **Map Room: BACK twice; one return left** (then to the owner as it stands). Round 2's notes: give the shaft depth
  (a string course or set-back at each floor line, corner piers proud by 1, deep sills or recesses); make the
  entrance a feature (a porch and a stair plinth at least 2 wide); furnish each floor for the cartographer (a map wall
  of banners or terracotta, a seat facing the table, shelves); a table spot at the lookout and brackets under the
  overhang. Ship step: size (9,16,9) -> (9,23,9).
- **Trade Hall: BACK twice; one return left.** Round 2's notes: the dark-oak frame one block proud and sills and a
  1-block eave on the back and left; a dormer or cross-gable on the long back roof; the tower top in the walls' or
  roof's palette, not red terracotta; the tower's plinth all round.
- **Guildhall: not yet clean, not yet judged.** The stair-door CHECK is fixed and the calcite replaced; `holes.py`
  still reports one "open" per level: the open market arcade under the hall, which is the design's idea. Next run:
  either an ALLOW line for the arcade (as for the Gazebo and the School's cloisters) or wall it, then the sheet
  (render the old guildhall) and the critic. `rebuild.py` drops entities, so the charter frames are added by
  `js/guildhall/frames.py` after each save.

Brief checks done on the candidates: Leader's Hall one Training Post, no trapdoor in the front walk row, inside its
plot; Map Room one cartography table, 23 tall (needs 13), a lookout in the top quarter, 13 ladders; Trade Hall one
Shop Counter, one bed (up a ladder: whether the reach check counts ladders is untested), no barrel, about 47 stacks
(budget 81). Not tested: the GameTests.

## Round 3 (lane-c-1118-0353; each critic a fresh subagent)

| Score | Map Room r3 OLD | NEW | Trade Hall r3 OLD | NEW | Guildhall r1 OLD | NEW |
|---|---|---|---|---|---|---|
| Silhouette | 4 | 4 | 3 | 4 | 4 | 3 |
| Depth | 4 | 4 | 4 | 4 | 3 | 4 |
| Interior | 2 | 3 | 2 | 3 | 3 | 2 |
| Detail | 3 | 3 | 3 | 4 | 3 | 3 |
| Verdict | | BACK (3rd: to the owner as it stands) | | SHIP | | BACK |

- **Map Room: BACK a third time; goes to the owner as it stands** (REBUILD.md: twice at most). Round 3 added corner
  piers, belt courses, a stair-plinth porch, banner map wall and lookout brackets (11x25x14). The critic's remaining
  notes: furnish the 3rd floor and the lookout deck; a window on the bare back of the shaft; drop the two dark-blue
  banners halfway up the front piers; set the lookout windows back with sills; one roof feature of its own.
- **Trade Hall: SHIP** (round 3). Proud dark-oak frame all round, sills and eaves on every side, a cross-gabled ledger
  bay on the back, the tower top in the walls' palette, a stone skirt round the tower. Optional polish: a second window
  per tower face; furnish or close the loft under the hall roof; something on the back gable. Ship step: size
  (11,17,11) -> (18,18,19).
- **Guildhall: BACK** (round 1). Arcade allowed in holes.py (one ALLOW line, the hall above closed). Notes: (1) furnish
  II's two wing rooms (records room, guild master's chamber, lamps); (2) a dormer or cross gable and windows on the
  long back roof and blank plaster; (3) a real gable on the wing's east end (II-right) instead of a bare hip;
  (4) a second roof shape on Level I (a cross gable over the arcade's middle bay, or a real lantern tower);
  (5) candles on the hall table, a focal wall round the charter frames, window boxes, stalls in the arcade.

**Guildhall round 2: BACK** (OLD 4/4/2/3, NEW 3/4/3/3; one return left). Round 2 furnished the wing rooms (counting
house, records room), added a rear gablet, an east cross gable on II, window boxes and arcade market counters.
**It misses the brief:** the arcade counters' stock barrels (I (2,1,4), (14,1,7); II/III also (23,1,7)) are forbidden
(a barrel pulls a jobless villager into the fisherman job): replace with empty chests against a wall. Other notes:
(2) give II a mass of its own (a taller wing, a tower or a projecting entrance bay), not I made longer; (3) the
back gable above the plinth is a blank dark-wood panel: framed windows; (4) the hall's upper floor (cut9) is bare:
furnish the meeting room by the charter frames; (5) film eye-level shots.

**Guildhall round 3 (designer).** (1) No barrel or job block anywhere (checked on every level's palette: no barrel,
lectern, loom, composter, cartography table or other workstation, no bed; the bell stays): the two market stalls keep
their stock in empty chests set against the arcade pier beside each counter (I (2,1,6) and (14,1,6)), the counting
house's barrel (23,1,7) is an empty chest against the wing's east wall; the plan's stray "lectern" note is gone. The
west chest moved the arcade's reported opening one up, so holes.py's ALLOW line is now (2,2,6) (the arcade itself has
not moved). (2) II's wing is a mass of its own: it rises a third storey, jettied a second time over the street (z 0)
on its own corbels, a three-light window in the Master's chamber, a steep front gable with collar, king post and a
lightning-rod finial at y 23 (ridge y 22, six over the hall's ridge), the east cross gable lifted with it; II is now
25x24x13 (III the same, plus 4 below). A stair up the wing's back wall climbs from the records room to the chamber.
(3) The back charter wall is plaster and posts with a dark mid-rail and two framed windows on flowered sills either
side of the frames (they light the charter); no board panel left. (4) The hall's upper floor by the charter: the
guild's chest (empty, against the wall) under the middle frame, low bookcases under the other two, a candle stand,
a red runner from the table to the charter, banners beside the windows and behind the Master's chair, and a clerk's
table with four seats at the west end; the new Master's chamber has his table and armchair under a fourth charter
frame, two coffers (empty chests), shelves and lamps. (5) Eye-level shots: not filmed (no client in this run).
Checks: `rebuild.py --candidate` printed no CHECK line, holes.py 0 (3 allowed), II keeps 97.6% of I's blocks, III
differs from II only by the hatch at (13,0,10). Not tested: the GameTests.

**Guildhall round 3: BACK a third time; goes to the owner as it stands** (critic lane-c-1118-0953; OLD 4/3/3/3,
NEW 3/4/4/3; nothing missed in the brief: no job block or bed on any level, 4/7/8 empty frames on solid blocks, II keeps
97.6% of I, III changes only the hatch). Remaining notes for the ship step: (1) the wing's rear slope ends as a ragged
half-gable on the hall's ridge (II back, right, top): a full rear gable or a hip into the hall roof; (2) Level I reads
as a plain gable box (I front, top): raise the front dormer into a cross gable over the arcade's middle bays; (3) the
stone ground floor's back has no opening (I and II back): windows between the buttresses; (4) no entrance reads from
the front (I and II front): a door on the arcade's back wall with lanterns, windows on the wing's blind front wall;
(5) the cellar strongroom is bare (III cut -1): chests on both long walls, a table with a lantern.
