# Stone House (49.4): the critics' scorecards

Brief: `stone_house.md`. Design: `tools/blueprints/js/stone_house/` ("the Mason's House": a square two-storey rubble
house with dressed corners, a floor band and an end chimney; II adds a lower parlour wing on the east gable, III carries
the back roof down over a rear extension, IV digs a store cellar), drawn blind. Sheet (round 2, OLD left, NEW right):
`stone_house_sheet.png`. Eye-level shots: not filmed.

| Score | OLD | Round 1 | Round 2 |
|---|---|---|---|
| Silhouette | 3 | 4 | 4 |
| Depth | 3 | 3 | 4 |
| Interior | 2-3 | 3 | 3 |
| Detail | 3 | 3 | 4 |

Round 1: BACK (no table on the ground floor and a bed in the kitchen; flat back and right faces; a bare upstairs and
cellar; moss scattered evenly). Round 2 (a fresh critic): **SHIP** (three wins, no loss), no brief requirement missed
in the drawings. Optional at ship: a dormer or step in the long rear roof of III-IV; something in the cellar's middle;
film eye-level shots.

Ship step (next lane B run): replace the four drawings (`rebuild.py stone_house <id>`, `--basement 4 --cellar` for IV,
see `js/stone_house/build.sh`), new declared sizes 11x16x11, 13x16x11, 13x16x14, 13x16x14 + 4 in `StarterBlueprints`,
move the GameTests pinned to old cells (door 5,1,1; barrel 8,1,8; upper bed 3,6,7-8; upstairs chest 3,6,6; hearth
1,1,3; cellar ladder x 2 z 5), watch `RefreshGameTests`' 12,000-tick limit, film `rebuild_stone_house`.
