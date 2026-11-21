# Colossus: the critic (52.6, flagship A and B)

Sheet: `colossus_sheet.png` (OLD | A "Astride the Road" | B "the Wayfarer"; 12 rows: four sides, top, cutaways at
y <= 4 (top and front) and y <= 39 (top and front), stages 1-3 front). OLD is the live parts laid at their
`colossus.json` offsets (`build/rebuild/colossus_old/colossus_old_stage{1..4}.nbt`). Eye-level shots: not filmed. A
statue has no rooms: the y 4 cut shows all three plinths solid either side of the road arch, so the arch vault is the
only inside space, and no version scores above 2 on Interior.

| Score | OLD | A | B |
|---|---|---|---|
| Silhouette | 2 | **4** | **4** |
| Depth | 3 | **4** | 3 |
| Interior | 2 | 2 | 2 |
| Detail | 2 | **3** | **3** |

- Silhouette. OLD: a square pillar with a box head on a stepped plinth, the lantern arm a stub against the head, and
  the back a flat slab of copper stripes (front, back, left). A: feet planted wide astride the road, the right arm
  thrown up with the lantern, the cloak sweeping from the shoulders down to pool on the deck: a recognizable outline
  from every side (front, right, top). B: a striding figure with a staff on the east, a pointing arm on the west and the
  cloak behind, different from every side (front, left, right). But from the back the cloak is a flat rectangular panel
  with a fringe, and the legs are stiff posts (back).
- Depth. OLD: plinth pilasters, lit niches and two stepped courses, but the figure is flat-faced (front, left). A: a dark
  brick die between proud pilasters, a copper frieze, an overhanging cornice and balustrade, corner piers with
  braziers, stair flights each side of the arch, and folds in the cloak (front, back, right). B: pilasters one block
  proud and a stepped upper tier, about the same as OLD's plinth. The plaque sill and hood are shallow, and the figure
  and cloak are mostly flat planes (front, back).
- Interior (y 4 cut). OLD: a solid plinth, with lanterns in the arch's side bays. A: a solid plinth, with shroomlight
  in the vault. B: a solid plinth, with sea lanterns in the vault walls. All three are the same: a lit passage and no
  rooms.
- Detail. OLD: one gray stone, copper painted on in stripes, a dark bar for a brow (front, back, top). A: the yellow
  glass lantern reads as a flame, along with a copper belt and buckle, laurel, a white deck on dark brick, and a
  mossy/cracked mix low. But the copper frieze reads as spotty orange squares, and the cloak's train on the deck reads
  as a staircase (back). B: copper bands on a dark oak staff, a crook-hung lantern with a drip finial, dark boots, a belt
  and a satchel. But the pale stone plinth and the pale figure blend into one beige mass, and the hair frame reads as a
  helmet (front).

Brief check, on the stage .nbts with my own script (no misses in either):

| Rule | A | B |
|---|---|---|
| Height 47, top y 46 | yes | yes |
| Arch: 5 columns open (5 of 6 cells y 0-5), crown 6 high, no other open column, after every stage | x 12-16, full at 13-15 | x 11-15, full at 12-14 |
| Highest layer 13 or more wide (y 0-19) | y 13 | y 13 |
| Above y 13 | 11 wide | 12 wide |
| Wall signs | 7 dark oak at y 8, north, no other | 7 dark oak at y 8, north, no other |
| Campfires | 9, all lit | 9, all lit |
| Copper (cut, waxed or weathered) | 398 (335 cut, 0 waxed or weathered) | 394 (344 cut, 0) |
| Diorite + smooth stone | 1,788 | 2,111 |
| Emerald eyes | y 38, z 9 | y 40, z 9 |
| Nose blocks in front | 13 | 11 |
| Glowstone | 3, caged, 3 in the top five layers | 3, caged, 3 in the top five layers |
| Glass | 20 | 12 |
| Blocks | 8,786 | 8,709 |
| Grounded after every stage (face, edge or corner) | 0 floating | 0 floating |

Each stage raises what its title says, so no rename is needed. holes.py: two `open` lines each, both on purpose: the
road arch, and the space under the figure on the deck (A (9, 13, 13) between the legs under the tunic and cloak; B (7,
14, 18) under the cloak). OLD has the same arch line.

Verdicts: **A SHIP** (beats OLD on silhouette by 2, depth by 1 and detail by 1; ties interior). **B BACK** (beats OLD on
silhouette by 2 and detail by 1, ties depth and interior: two scores). B's notes, had it gone on:
1. Back view: the cloak is a flat panel hung from the shoulders. Curve and fold it so it moves with the stride, and
   bring its hem to the deck or the calves.
2. Front and right views: give the plinth real relief: a recessed arch mouth with voussoirs, a deeper cornice, and
   pilasters two proud at the corners.
3. Front view: the plinth and the figure are the same pale stone. Use a darker or warmer stone for the plinth so the
   figure stands off it.
4. Front view: the head reads as a helmet. Separate the hair from the face (a hairline or brow) and show the beard as
   its own mass.
5. Back and left views: the legs are straight posts. Bend the back knee and turn the back foot, so the stride reads.

**Ships: A.** It is the stronger build: the deeper plinth and the clearer idea, a gate the road passes through under
the Founder's stride and raised light.

Ship checklist (for A):
- Footprint 29 x 47 x 29, the same as the gallery frame (`WonderGalleryScene.WONDERS`), so the scene needs no change.
- holes.py `open`: (12, 1, 4) is the road arch and (9, 13, 13) is the space between the legs on the deck. Both are on
  purpose; Wonder parts are skipped, so this matters only if the whole Wonder is ever checked.
- Tight margins: copper 398 against a cap of 407, and the last wide layer at y 13 (the top of 13 +-1). Any touch-up
  must not add copper or raise the deck.
- The lantern cage is `yellow_stained_glass` blocks (their ids contain `glass`, so the cage passes). There are no
  panes, so the B648 pane-end rule doesn't apply.
- Optional polish (back view): the cloak train's stepped copper on the deck reads as a staircase. Break its edge or
  slope it as a pooled hem.
- From the brief: live saves need a fallback to the old drawing for begun or finished Colossi, or the owner's decision.
  The wiki, `docs/guide/` and `docs/design/M35.md` describe the old look, and the `wonder_colossus` scene's night checks
  (3 glowstone at y 43-45 with the top at 46) pass.
