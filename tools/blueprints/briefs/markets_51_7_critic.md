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
