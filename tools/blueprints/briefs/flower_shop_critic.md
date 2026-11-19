# Flower Shop: the critic's scorecard

Critic, 2026-11-19. Sources: `build/rebuild/sheets/flower_shop_sheet.png` and `flower_shop_2_sheet.png` (OLD left, NEW
right: front, back, left, right, top), the NEW cutaways `build/rebuild/flower_shop/renders/flower-shop-cut3_*` and
`flower-shop-2-cut3_*`, the NEW single renders, and layer prints of all four `.nbt`s (`holes.py` `load`). Eye-level
shots: not filmed (so no Interior score goes above 4). No OLD cutaway was rendered; the OLD interior is judged from its
layer prints.

## Level I (`flower_shop`)

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 3 | 4 | OLD: sheet "front"/"left" read as one plain gable box whose only mark is the striped awning, and "back" is a bare brick gable. NEW: sheet "front" and "right" show a steep copper roof with a front cross gable, a glazed ridge and a chimney with a lit top that make an outline you'd know from every side; one point off for the lumpy copper box at the ridge and the stepped brick gable edge on "back". |
| Depth | 3 | 4 | OLD: depth only on the front (awning, window boxes, sheet "front"); "back" and "right" are flat brick and birch planes. NEW: every face has something proud or set in (overhang all round, front planter trough and lamp posts on "front", the chimney stack standing out of the back wall on "back", posts between thin glass bays on "left"); the timber posts sit in the wall plane, not proud of the brick, which stops it at 4. |
| Interior | 2 | 4 | OLD (layer print y 1-3): one 5 x 4 room with a chest and the composter in a corner and pots on the sills, nothing else. NEW ("flower-shop-cut3_top"): a planned glasshouse hall: front door onto a centre aisle between two raised grass beds of tall flowers, the composter and her chest at the aisle's end beside a spruce counter and a fireplace, display shelves of potted flowers, a side sales nook with its own chest, a door to a fenced back garden with a pond, hung lanterns and hung pots over every part. |
| Detail | 3 | 4 | OLD: awning, ten potted flowers and window trapdoors (sheet "front"), little else. NEW: potted and hung flowers, decorated pots, lamps on fence posts, tall flowers in real soil, a pond, waxed (non-ageing) copper ("front", "cut3_top"); a little over-busy (seven materials, glass blocks set into the roof slope read as holes on "front"). |

## Level II (`flower_shop_2`)

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 3 | 4 | OLD: the same gable box with a flat-topped glass lean-to on the left (sheet "front", "top"). NEW: the main house plus a second gabled glasshouse wing to the east, chimney between them (sheet "front", "back"); the two roofs don't meet (gap on "top"), so it reads as two buildings. |
| Depth | 3 | 4 | OLD: lean-to is flat glass walls ("right"); rest as Level I. NEW: as Level I, and the wing's timber posts, brick plinth and overhanging copper eaves carry it round ("back", "left"). |
| Interior | 2 | 4 | OLD: Level I's bare room plus a 4 x 2 glass flower bed with one lantern. NEW ("flower-shop-2-cut3_top"): Level I's hall unchanged, and a glasshouse wing with a centre aisle between two beds of small flowers, its own hung lantern and doors to the back garden and the yard. |
| Detail | 3 | 4 | As Level I; the end rod on the ridge (sheet "front") is a stray piece. |

## Brief check (NEW)

Nothing missed. Verified on the `.nbt`s:
- Exactly one job block in each level, a composter at (5, 1, 9); no other workstation (the furnace, campfire and
  decorated pots are not job blocks).
- Chests at (6, 1, 9) and (10, 1, 2), both within 8 / 4 of the composter, both against bricks or planks; same cells and
  blocks in II.
- 21 grass blocks with air above inside her 5 / 2 garden box; no small flower, short grass or fern inside that box (the
  wing's small flowers in II are 7+ blocks away; the beds near her hold only tall flowers).
- Walk line: front door (4, 1, 1) down the open aisle x = 4 to the composter (floor and 2 high head room at (4, 1, 9) and
  (5, 1, 8)); the side nook's chest is reached through the opening at (7, 1-2, 3).
- Sizes: I is 13 x 12 x 13 (plot 13 x 16 x 13), II 17 x 12 x 15 and contains I; 98 % of I's 562 non-air blocks kept in
  II (needs 60 %). About 48 stacks for I (limit 108); 562 / 760 blocks, well under the 1,000 risk line.
- No entities, jigsaw or structure void; no grass under a solid block; tall flowers on grass or dirt; pots hang from
  chains or stand on slabs or stairs; lanterns hang or stand on fence posts; the copper is waxed; the water is one
  enclosed source (pourable, `MaterialRules`).
- `check.check` and `holes.scan` / `open_walls` on both candidates: no problem.

Not verified by the critic (for the ship step, not reasons to send it back): the light-above-7 and chair rules in game,
the 30,000-tick build, and the declared sizes, which change from (9, 11, 9) and (15, 11, 9) to (13, 12, 13) and
(17, 12, 15) in `StarterBlueprints`. The candidate draws no village look; the five village variants keep today's.

## Verdict: SHIP

NEW beats OLD by at least one point on all four scores, on both levels (Interior by two), and loses on none.

Polish worth doing at the ship step if cheap (not conditions):
1. Sheet "back": the copper box at the ridge and the stepped brick gable edge make the roofline lumpy; finish the gable
   flush under the roof.
2. Sheet "front": the glass blocks set into the copper slope read as holes from the street; use a full glazed strip or
   drop them.
3. Level II sheet "front": remove the lone end rod on the ridge.
