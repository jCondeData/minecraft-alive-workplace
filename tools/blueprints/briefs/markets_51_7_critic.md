# Market Square, Market Cross and Market Corner (51.7): the critics' scorecards

Briefs: `market_square.md`, `market_cross.md`, `market_corner.md` (lane-c-1118-1553). One version each, drawn blind,
each critic a fresh subagent. Eye-level shots: not filmed (scored from cutaways). Sheets: `market_cross_sheet.png`,
`market_corner_sheet.png` (OLD = the live .nbt, NEW = the candidate; regenerate the candidate with `npm run build` in
`js/<family>/` and `rebuild.py <family> <family> --candidate`).

| Score | Cross OLD | NEW | Corner OLD | NEW |
|---|---|---|---|---|
| Silhouette | 3 | 4 | 3 | 3 |
| Depth | 3 | 4 | 3 | 3 |
| Interior | 2 | 4 | 2 | 4 |
| Detail | 3 | 4 | 3 | 3 |
| Verdict | | SHIP | | BACK |

- **Market Cross ("the Mercat Drum"): SHIP** in round 1 (9x8x9, 219 blocks; an octagonal drum with a stair cut into
  the front, a walled platform round a quartz shaft and armed head, benches on three sides; no CHECK line, holes.py 0).
  No brief miss. Not shipped yet. Ship steps: `StarterBlueprints.MARKET_CROSS` (5,8,5) -> (9,8,9); `market_cross` into
  generate.py's REBUILT; history; the size row in `docs/wiki/more-decorations.md`; `export * from './market_cross.mjs'`
  in `parts/index.mjs`; run MoreDecorationsGameTests, QaDecorations1104f/1108, BuilderGameTests' size test, QaLaneC1101's
  plurals. Optional notes (not blocking): (1) front/left: a see-through gap under the front-left azalea at (1,1,1) in
  the render, check in the client and fill or slab the corner (0,0,0) if real; (2) the quartz shaft reads as a blank
  white tube, band it with a chiseled course or use a textured block; (3) left/right: the head barely clears the
  parapet and banners, make the cross, not the flag, the top of the outline.
- **Market Corner (8x8x8, 229 blocks, a striped L awning over counters on a paved court): BACK** in round 1 (one
  better score of three needed; most of the gain is size). Notes for round 2: (1) back/right views are flat walls: set
  the posts proud of the infill or the infill back, run the awning past the back and east walls as an eave, put
  something on the back face; (2) the awning's low step stops over the back counters and leaves the front and west
  counters under open sky: bring it out over them on the corner fence posts, and thin the three-deep wool wedge (y3-5);
  (3) drop the lone log with a lantern at (7,6-7,7) or make the corner post the awning's mast; (4) cut1_top: one paving
  and one base material all round, fill or shrink the empty middle, lift the melon (1,1,4) and hay (0,1,3) onto the
  counters; (5) hang the lanterns at (6,2,2)/(3,2,6) from the awning over the walk, and film `SCENE=rebuild_market_corner`.
- **Market Square:** brief only; no design yet.

## Market Corner round 2 (critic, fresh eyes, 2026-11-18)

OLD = the live `structure/market_corner.nbt` (5x5x5, 73 blocks). NEW = `build/rebuild/market_corner/market_corner.nbt`
(9x8x9, 317 blocks; identical to `js/market_corner/out/` as of 19:30 UTC). Sheet (overwritten):
`market_corner_sheet.png`, rows front, back, left, right, top, low, cutaway front and cutaway top (OLD cut at y<=2,
NEW at y<=3). Eye-level shots: not filmed (scored from the cutaways). Checks run on NEW: `check.check` (all checks
incl. the owner's rules) empty, `holes.py` TOTAL 0; no POI block in the palette (no barrel, bell, bed); 22 materials.

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 3 | 4 | top/left: two mitred L canvases stepping down to the court and a banner mast over the street corner read from far off; back/right are now a posted eave line with the mast, not a box, though still the plainer sides. |
| Depth | 3 | 4 | back/right: dark oak posts stand proud of the board screens over a cobble-stair splash course, open hatches with folded shutters; front/low: two canvas layers with a banner valance cast shadow over the counters. |
| Interior | 2 | 4 | cutaway top: an L of counters behind a 3-deep aisle with lanterns hung over it (2,3,5)/(5,3,5)/(5,3,2), a second booth in front-west, a clear court entered from the front middle; every good on a counter. |
| Detail | 3 | 4 | cutaway top/left: pots, cake, potted flowers, cloth carpets, melons and pumpkins, mossy and cracked pavers, one paving and one base; nothing breaks the owner's rules. |

**Verdict: SHIP.** NEW beats OLD by at least one point on all four scores and loses on none. No brief miss found: no
POI block, 9x8x9 within the limit, nothing that grows, falls or flows, lamps hang or stand on solid blocks, the front
middle (x 4, z -1) opens onto the court. All five round-1 notes are answered (proud posts and eave on the street
faces; the booth canvas over the front/west goods and a thinner main canvas; the mast; one paving and base, goods on
counters; lanterns hung over the aisle).

Ship steps: `StarterBlueprints.MARKET_CORNER` (5,5,5) -> (9,8,9) and its javadoc line; `market_corner` into
`generate.py`'s REBUILT; the old drawing into `blueprint_history/market_corner/` (`history.py`); copy the candidate over
`structure/market_corner.nbt` with `rebuild.py market_corner market_corner` (furniture file: no spots); the size row in
`docs/wiki/more-decorations.md`; run `MoreDecorationsGameTests.buildsMarketCorner` (9,000 ticks for 317 blocks) and
`QaDecorations1104fGameTests.marketCornerFromExactlyItsList` (0 skipped; watch the wall banners, decorated pots and the
cake), plus BuilderGameTests' size test, QaMaterialList, QaLaneC1101's plurals (cake, decorated pot, banners) and
BlueprintsStay; add `rebuild_market_corner` to `RebuildScene.FILMED` and `tools/showcase/scenes.py` and film it.

Optional polish (not blocking): (1) front/low: the east screen's end shows a tall blank dark-oak and board column with
one cobble block at its foot; a banner or a crate face there would carry the stall's colours round the corner;
(2) the lantern at (1,2,1) under the booth canvas hangs at head height for anyone standing behind the booth counters;
(3) the booth's inner canvas row (y 3) leaves 2 blocks clear: fine for a player, check it in the eye-level film.

## Market Square round 1 (critic, fresh subagent, 2026-11-18)

The "Market Square: brief only; no design yet" line above is history: round 1 is "The Market House" (13x16x13, 751
blocks vs OLD's 305; a paved square with a jettied, timber-framed Market Court on an open stone undercroft at the back,
a steep brick-tile roof, cross gable, cupola, balcony over the middle, outside stair on the east, a striped stall on
the west). Sheet: `market_square_sheet.png` (OLD = the live .nbt, NEW = `build/rebuild/market_square/market_square.nbt`;
cutaways OLD y<=2, NEW y<=3 and y<=7). Eye-level shots: not filmed.

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 2 | 4 | front/back/left/right: OLD is a flat plinth with two stall humps; NEW reads from afar (tile roof, cross gable, cupola), but the back is one plain roof slope and the east is a solid stone stair mass |
| Depth | 2 | 4 | front/back: NEW's jetty on corbels, posts proud of the plaster, eaves and rakes a block out, the balcony; OLD has only the plinth edge and fountain tiers |
| Interior | 2 | 4 | cut3/cut7: NEW's undercroft (benches, hung lanterns) and a furnished, lit Market Court reached by the stair; OLD has no rooms, stalls hold job-site barrels |
| Detail | 3 | 4 | front/left: one palette, azaleas and flowers in moss beds, banners on the sill, produce on the stall counter; nits below |

Brief check (measured on the .nbt, not only the renders): no POI block (OLD had 2 barrels and a bell, which the brief
forbids); middle cell (6,1..3,6) open, the balcony above it at y 4; 79 standing cells at y 1 inside the traders' 9x9
(OLD about 44 at y 1), the open ground reachable from the z 0 front; lane step: a stone brick slab row at z 0, x 2..10
(a half step, better than OLD's full block); 13x13, 16 tall (fits `big_area` from (9,2,9) to the top layer exactly);
about 44 stacks (budget 108). No brief miss.

holes.py: one `open` at (2,1,8), the undercroft arcade. **ALLOW is right**: it is the market's covered trading floor,
cells z 8..10 are trader and banquet ground inside the 9x9, and glazing it would cut them off; the same call as the
Guildhall's open arcade (2,2,6).

Build-time risk: 751 blocks in 20,000 ticks is 26.6 ticks a block, between the passing Pokemon Center Lodge (18.2) and
Inn (28.8), so likely fine; the cupola cap at y 15 is the highest reach. If `buildsMarketSquare` times out, give it
30,000 like the other 13x16x13 starters (Town Hall, Graveyard, Inn), never fewer blocks.

**Verdict: SHIP** (NEW +2/+2/+2/+1; loses on none). Ship steps:
1. `StarterBlueprints.MARKET_SQUARE` `new Vec3i(13, 5, 13)` -> `new Vec3i(13, 16, 13)` (the middle MarketDays reads stays (6,1,6)).
2. `market_square` into generate.py's `REBUILT`; `rebuild.py market_square market_square` to the live structure; `history.py` keeps the old drawing.
3. holes.py: `ALLOW.update({(n, (2, 1, 8)): "the Market Square's open undercroft, the market's covered trading floor under the Market Court, not a walled room" for n in ("market_square",)})`.
4. Run the tests that name it: `BuilderGameTests.buildsMarketSquare` and `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `ZoneVillageBuildingsGameTests.buildingsInAZonePastTheRadiusCountForTheVillage`, `QaMarketDays1117cGameTests`, `QaMarketDays1117cBugTests`, `VillageHallGameTests` (marketDayBringsTraders, decorationsMakeAVillagePrettier), `SeerGameTests.theFestivalMarketAndGuestToldAreRight`, `ClassGameTests`, `ClassMigrationGameTests`, `QaClassPerksGameTests`, `ServiceGameTests`, `StewardCivicRulesGameTests`, `QaBlueprintLibraryVerify1108bGameTests`, `ArenaGameTests`, `ServerControlsGameTests`.
5. Scene `rebuild_market_square` in the harness and `tools/showcase/scenes.py`; the size row in the wiki if one lists it.

Optional polish (not blocking): (1) back: the long roof slope is blank; a dormer or a second, smaller cupola light
would work it; (2) right: the stair's base is a solid stone block, step it back or arch it; (3) the east edge is
stair and landing up to y 4, so the trade fair's bunting finds no air cell there; (4) hay at (0,1,3..4) sits loose on
the paving behind the stall; (5) front/low: the cross gable's peak is a cluster of dark-oak stair ends, tidy it.
