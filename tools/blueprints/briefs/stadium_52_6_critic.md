# 52.6 critic (lane-d-1119-0708): the Stadium ("the Stadium Bowl", 8aee03d02)

Sheet: `tools/blueprints/briefs/stadium_sheet.png` (OLD left, NEW right; five views, no cutaways on the sheet, no
eye-level shots). NEW cutaway y 4 rendered separately. Fresh critic subagent; brief checked on the candidate stages.

## Round 1: BACK
| Score | OLD | NEW |
|---|---|---|
| Silhouette | 2 | 4 |
| Depth | 3 | 3 |
| Interior | 2 | 3 |
| Detail | 3 | 2 |
Brief: nothing missed (2 posts at (17,1,24)/(41,1,24), ring (29,1,24) and box spots reachable, 1 Healing Machine,
1 brewing stand, 4 inverted daylight detectors, 32 lamps, 746 seats in the Cup's 40/16 box, stage II 176 red + 212
white carpets, stage I 135 white concrete, 59x20x49, stages only add). Not checked: lamp wiring, grounding, doorways.
Ship step: the front opening is x 27-31, so `StadiumDays.GATE` (26,1,-4) faces wall at x 26; check visitors reach it
at dusk or move it to x 29.

Notes, most important first:
1. Concourse (cutaway y 4) is an empty gray ring: put the nurse's booth there (Healing Machine, brewing stand) and
   add lit refreshment and changing rooms off it. (The designer filled the concourse solid because an open one set
   off holes.py's open-room check; solve that, e.g. close the rooms with walls and doors.)
2. Nurse's booth (top view, front-left (5-6,1,6)): the critic saw the machine and stand bare on the paving; the
   designer reports a copper-roofed kiosk there. Make it read clearly as the nurse's booth from above and the front.
3. Gate (front/low views): a bare 5-wide cut under a small tower. A real entrance: recessed arch, a lintel that
   stands out, flags and lettering on its face.
4. Walls (front/back views): pilasters, string course and windows flush with the brick. Set pilasters out one
   block or recess the brick, sills/reveals on windows, carry the roof edge all round or end it deliberately.
5. Story detail (right/top views): a scoreboard that reads from both sides (the brief's B141 rule) and a covered
   stand on one side; the 29x21 field inside the black wall is crowded by the tiers.
