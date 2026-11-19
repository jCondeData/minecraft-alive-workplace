# Supply Shop: the critic's scorecard

Critic for Milestone 49 (REBUILD.md, "The scorecard"), 2026-11-19. Views: the four sheets in `build/rebuild/sheets/`
(`supply_shop*_sheet.png`, OLD left, NEW right), the NEW cutaways `build/rebuild/supply_shop/renders/cut-*`, and the
layers of the old and new `.nbt`s printed with `holes.py`'s `load`. Eye-level shots: not filmed.

## Scores

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 3 | 3 | OLD (sheet I, front/left/right): the flared green roof, the striped awning and the raised porch round three sides read as a shop from any angle, and from II on a chimney and a cross wing give it an outline. NEW (sheet I, back/right): a plain gabled box with no porch, awning or bay that reads as a cottage, not a shop. Its outline only gets better at III and IV (sheet III front: two gables with copper awnings). |
| Depth | 4 | 3 | OLD (sheet I, left/right): a stone plinth with a stepped edge, fence posts, a porch rail and benches, trapdoor shutters and a flared roof edge give it layers on every face. NEW (sheet I, front) has a recessed door, window boxes, a copper strip and a one-block side eave, but its back (sheet I, back) is a flat calcite wall with one window and no sill or shutter. The `y 1` layer shows the log frame flush with the calcite infill, and the roof stops flush with both gable walls. |
| Interior | 3 | 3 | OLD (layers): the barrels flank the counter row with a carpet and lanterns, II adds a bedroom and III a mail annex, so the plan is sensible but sparse. NEW (cut-l1-y3_top, cut-l4-y3_top) has a clear walking line (door, customer floor, counter with copper-topped side counters, stock wall, and a door through it to a furnished bedroom in II) and a hay-and-chest feed annex in III. But the customer half of the shop (z 3-5) is empty, and the IV cellar (cut-l4-y-1_top/front) is a bare deepslate box with six chests on two walls and two lanterns. |
| Detail | 3 | 4 | OLD (sheet I, front) uses white concrete, prismarine and blue wool, which is busier than the palette rules want. NEW (sheet I, front and left) sticks to deepslate, spruce, calcite and copper, with flowers growing in grass-block window boxes, potted plants on the sills, a banner, and hanging lanterns. It loses a point for the gable door at (5, 6, 1) (sheet I, front): the door opens onto a 5-block drop, has a lantern hung directly in front of it at (5, 6, 0), and leads into an empty loft with no way up. |

NEW beats OLD by one point on one score (detail), ties on two and loses depth by one.

## Brief check (NEW)

Everything I could check from the files passes:
- **Shop Counter:** at (5, 1, 6) in I-III and at (5, 5, 6) in IV (basement offset).
- **Level I stock:** exactly 8 containers (4 chests, 4 barrels), all within the counter's reach, and nothing else in Level I
  holds items.
- **Containers in II-IV:** every one is within reach of the counter.
- **Front door:** at (5, 1, 2) with a log lintel at (5, 3, 2). The cells before, in and behind it are clear, and (5, 1, 5)
  is a floor cell.
- **Furniture spots:** every level has all six required kinds.
- **Sizes:** 11x11x10, 11x11x16, 17x11x16, and 17x15x16 for IV.
- **Kept cells:** 98.8 %, 97.8 % and 99.9 % of each level below stay the same, and no block-entity block moves.
- **IV's basement:** floor at file y 0, a ladder at (14, 3, 4) with clear cells above it, and hanging lanterns under a
  planked ceiling.

One finding is a risk, not a proven miss: `supply_shop_2/3/4.furniture.json` contain a **`bed` spot** at (3, 1, 13). The
brief says the shop has no bed spot (`FurnishedWorkplacesGameTests.noSpotCoversAJobBlockABedOrAContainer` fails one in
a workplace). `furniture.py` line 397 drops bed spots for workplaces when it writes the shipped file, so this probably
won't ship, but the candidate files break the rule as written.

Not checked here: `rebuild.py --candidate` CHECK lines, the material budget, build time and reach. I didn't run the
GameTests.

## Verdict: BACK

NEW is not better on three scores; it is different. Fix these, most important first:

1. **Depth (sheet I, back and right; `y 1` layer):** move the calcite infill one block in from the log frame, or bring
   the posts and the sill beam one block out, on the back and both sides as well as the front. Give the back window and
   the side windows a sill (a stair or trapdoor under each) and shutters. Run the roof one block past both gable walls,
   front and back, with a trim course under the eave.
2. **Silhouette (sheet I, all four sides):** z 0 in front of the façade is free, so Level I needs something there that
   makes it a shop from 20 blocks away: a full-width awning over z 0 at the eave line, or a shallow display bay either
   side of the door. Keep the door pins: the door at (5, 1, 2), clear cells at (5, 1, 1) and (5, 1, 3), and the walk-in
   from (5, 0, -1). Also give the back gable something other than a blank wall, such as a jettied upper floor, a
   window or a lean-to.
3. **Detail (sheet I, front gable):** the loft door at (5, 6, 1) opens onto a 5-block drop with a lantern hung right in
   front of it. Replace the door with a window, or make it a hoist door with a beam above and the lantern moved off its
   swing.
4. **Interior (cut-l4-y-1_top and cut-l4-y-1_front):** furnish the IV cellar as a stock cellar: shelving or racks, a
   table, crates or sacks across the empty half of the floor, beyond the six chests. No barrels (they count as a
   Fisherman's block). In Level I (cut-l1-y3_top), give the empty customer floor between the door and the counter a
   display table or a rack. Keep the Level I container count at 8 and keep the furniture spots clear of the door.
5. **Brief hygiene (`supply_shop_2/3/4.furniture.json`):** remove the `bed` spot at (3, 1, 13) so the bed stays plain
   vanilla, and the candidate and the shipped file agree with the brief.

## Round 2

Critic, 2026-11-19 (a different critic from round 1). Views: the four round-2 sheets in `build/rebuild/sheets/`
(`supply_shop*_sheet.png`, OLD left, NEW right), the NEW cutaways `build/rebuild/supply_shop/renders/cut-*`, and the
layers of the candidate `.nbt`s printed with `holes.py`'s `load`. Eye-level shots: not filmed.

### Scores

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 3 | 3 | NEW's front now reads as a shop: two glazed display bays under copper awnings either side of a recessed door (sheet I, front), and III/IV add a second gable (sheet III, front). But seen from the left, right and back (sheet I, left/right/back) it is still an unbroken gabled box: one long roof slope, no chimney, dormer or bay, and the back gable is flat. OLD has the same kind of weak back but gets an outline from its flared roof, wraparound porch and, from II, a chimney (sheet II, back/right). |
| Depth | 4 | 4 | Round 1's note is fixed. Log posts now stand one block proud of the calcite on the back and both sides, with deep shadows (sheet I, back/right; `y 2` layer: posts at x 0/10 and z 10). The windows have trapdoor shutters, the roof runs one block past both gables, and the front bays sit on a deepslate plinth. OLD matches it with its stepped plinth, porch rail and flared eaves (sheet I, left/right). Neither has a trim course under the eave. |
| Interior | 3 | 4 | NEW I (cut-l1-y3_top) has a clear walking line. You go in at the door, past two copper display tables and carpets on the customer floor, to the counter flanked by copper-topped counters, with the stock wall of barrels and chests behind it. Lanterns hang inside, and the bays hold potted plants. In IV (cut-l4-y3_top, cut-l4-y-1_top), the II bedroom and the III hay annex are furnished, and the cellar is now a real stockroom: log racks along a wall, a fence-and-slab table, hay sacks, six chests and two hanging lanterns. OLD's rooms are sensible but sparse. |
| Detail | 3 | 4 | NEW keeps a disciplined palette of deepslate, spruce, calcite and copper, with window boxes, potted plants and hung lanterns (sheet I, front). The round-1 loft door over a drop is now a glass pane at (5, 6, 1). OLD mixes white concrete, prismarine and blue wool (sheet I, front). |

NEW beats OLD by one point on two scores (interior, detail) and ties on two (silhouette, depth). It loses on none.

### Brief check (NEW)

I found no miss in what I could check from the files:
- **Shop Counter:** at (5, 1, 6) in I-III and at (5, 5, 6) in IV.
- **Level I stock:** exactly 8 containers (4 chests, 4 barrels), all within reach of the counter. Nothing else in Level I holds items.
- **Containers in II-IV:** 9, 11 and 15, every one within reach of the counter.
- **Front door:** a spruce door at (5, 1, 2) with a log lintel at (5, 3, 2). The cells at (5, 1, 1) and (5, 1, 3) are clear, and (5, 1, 5) is a floor cell.
- **Furniture spots:** all six required kinds at every level. The round-1 `bed` spot is gone.
- **Kept cells:** 95.4 %, 95.5 % and 99.9 % of each level below stay the same, and no chest, barrel, counter, bed or banner moves.
- **IV's basement:** 4 deep, with a ladder at (14, 1-4, 4) coming up to the ground floor and two lanterns below ground.
- **Sizes:** 11x11x11 for I (excused), 11x11x16, 17x11x16, and 17x15x16 for IV.

Not checked: `rebuild.py --candidate` CHECK lines, the 108-stack budget and the 9,000-tick build time. Level I now has 627 blocks against the old 472, so watch the build time. I also did not check reach, dark cells or the GameTests.

### Verdict: BACK

The interior and detail are clearly better. But NEW wins only two scores and needs three, and silhouette is the one
within reach:

1. **Silhouette (sheet I, left and right):** the long sides are one unbroken roof slope over a flat wall. Break each
   side's outline with something above the eave line, such as a chimney stack rising past the ridge on one side wall
   or a dormer over a side window. Level I may grow to 13 wide if a stack needs the room. Keep the 8 containers and the
   front door pins.
2. **Silhouette (sheet I, back; sheets II-IV, back):** the back gable, and from II on the wing's back gable, is a flat
   calcite triangle with one small window. Give it a shape: a jettied upper gable, a lean-to stock shed, or a hoist
   beam and loading door with a ledge under it.
3. **Depth (sheet I, right; sheet III, left):** the roof sits straight on the top beam along both sides. Add a trim
   course or a row of upside-down stairs under the eave so the side eaves cast a shadow line like the front awnings do.

## Round 3

Critic, 2026-11-19 (a third critic, not the designer). Views: the four round-3 sheets in `build/rebuild/sheets/`
(`supply_shop*_sheet.png`, OLD left, NEW right), the NEW renders and cutaways in `build/rebuild/supply_shop/renders/`
(`supply_shop_{front,back,left,right}.png`, `cut-l1-y3_top`, `cut-l4-y3_top`, `cut-l4-y-1_top`), and the candidate
`.nbt`s printed with `holes.py`'s `load`. Eye-level shots: not filmed.

### Scores

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 3 | 4 | Every side of NEW I now has a shape (sheet I): two glazed bays under copper awnings on the front, a gabled dormer breaking each long roof slope (sheet I, left/right), and a jettied back gable with a loading door (sheet I, back). From II on, a cross gable adds a second ridge (sheet II, right; sheet III, front). It isn't a 5: there's no chimney or anything else above the ridge (sheet I, top), and the front-left roof verge reads ragged where the side eave steps down (`supply_shop_front.png`). OLD's flared roof and porch read as a shop and its chimney helps from II, but its I back is a plain gable (sheet I, back). |
| Depth | 4 | 4 | NEW has log posts proud of the calcite on all four faces, a deepslate plinth, trapdoor shutters and sill boxes, a spruce trim course under both side eaves (`supply_shop_left.png`, `_right.png`), the roof run past both gables, and a back gable jettied out over a beam (`supply_shop_back.png`). That's depth on every face, but the gable verges are flush single stairs with no barge board (`supply_shop_front.png`, upper left). OLD matches it with its stepped plinth, porch rail and flared eaves (sheet I, left/right). |
| Interior | 3 | 4 | NEW I (`cut-l1-y3_top`) has a walking line that works: door, display tables and carpets on the customer floor, then the counter between copper-topped counters, with the stock wall of 4 chests and 4 barrels behind it and three hung lanterns down the middle. IV (`cut-l4-y3_top`) adds a furnished bedroom and a hay-and-chest feed annex, and the cellar (`cut-l4-y-1_top`) is a lit stockroom with racks, a table, hay and chests. OLD's rooms are sensible but sparse. NEW isn't a 5 because the I loft behind the back loading door has no way up. |
| Detail | 3 | 4 | NEW keeps to deepslate, spruce, calcite and copper (sheet I, front), with flowers in soil boxes, potted plants, a banner and lanterns hung from beams, and it breaks no owner rule I could find. OLD mixes white concrete, prismarine and blue wool (sheet I, front). NEW isn't a 5 because the back loading door at (5, 6, 9) has only a one-slab ledge over a 5-block drop and no hoist beam to explain it (`supply_shop_back.png`). |

NEW beats OLD by one point on three scores (silhouette, interior, detail), ties on depth and loses on none.

### Brief check (NEW)

I found no miss (each checked on the candidate files):
- **Shop Counter:** at (5, 1, 6) in I-III and at (5, 5, 6) in IV.
- **Level I stock:** exactly 8 containers, all within 8 across and 4 up or down of the counter. Nothing else in I holds items.
- **Containers in II-IV:** 9, 11 and 15, all within the counter's reach.
- **Front door:** a spruce door at (5, 1-2, 2) facing south, with a log lintel at (5, 3, 2). The cells at (5, 1, 1) and (5, 1, 3) are air, and (5, 1, 5) is air over planks.
- **Kept cells:** 94.9 %, 95.5 % and 99.9 %. No chest, barrel, counter, bed or banner moves.
- **Furniture spots:** counter, shelf, crockery, picture, hanging_pot and trellis in every level, and no `bed` spot.
- **Sizes:** 11x11x11 for I (excused), 11x11x16, 17x11x16, and 17x15x16 for IV.
- **IV's basement:** two lanterns below ground.
- **Forbidden blocks:** no jigsaw, structure void or void cell.

Not checked: `rebuild.py --candidate` CHECK lines, the 108-stack budget, reach, dark cells and the GameTests. The 9,000-tick build time isn't checked either, and it's the main risk: Level I is now 665 blocks against the old 472 (well under the roughly 1,100 that has timed out elsewhere).

### Verdict: SHIP

Not blocking; the ship step or a later pass may take these:
1. **Detail (`supply_shop_back.png`):** give the back loading door at (5, 6, 9) a hoist beam above it (a log or fence
   arm with a chain), or make it a window like the front gable's.
2. **Silhouette (`supply_shop_front.png`, upper left):** tidy the front-left verge where the side eave steps down, so
   both gable edges run as one clean line.
3. **Risk:** run `buildsSupplyShop` early in the ship step. Level I grew by 41 % in blocks.
