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
