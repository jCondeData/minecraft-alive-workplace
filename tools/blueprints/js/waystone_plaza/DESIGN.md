# Waystone Plaza: design notes (blind rebuild, ROADMAP Milestone 52)

Brief: `tools/blueprints/briefs/waystone_plaza.md`. One level, five looks (plains, desert, savanna, snowy, taiga), all
exactly 7 x 5 x 7 (x, y, z), the culture's waystone at (3, 2, 3) lower and (3, 3, 3) upper, open standing room beside it
on every side, nothing above y = 4. The plan and palettes are in `plan.mjs`; `recipe.mjs` draws them in passes.

## Three rough concepts

**A. The Stepped Dais.** Open paving on all 49 cells, a two-step square dais in the middle with the stone on top, a lamp
post at each corner and a bench on each side facing in. Massing: one low slab, the stone the only thing that stands up;
roof: none; plan: four benches round a dais. Story: the village set its stone on a plinth in the square. Clear and
safe, but from 20 blocks it is a flat pad with four sticks and a pillar, and from the map's top view it is a gray square.

**B. The Wayfarers' Screen.** A stone screen across the back (z = 6) rising the full 5 blocks, a sheltered bench along
its foot under a short pent roof, the stone on a low dais in front of it, the front open to the road. Massing: one tall
wall and a low dais; roof: a one-block pent over the bench; plan: stage and backdrop. Story: a reredos for the stone,
seen from the road. It reads well from the front only: from the back and sides it is a blank wall 7 wide (a flat panel
the style rules forbid), and a pent one layer high can't pitch.

**C. The Wayshrine (built).** A square pavilion roof on four timber posts with its middle left open to the sky, the
stone standing up through the opening on a stepped collar, a U of real benches under the eaves facing the stone, a
lantern hanging from each of the roof's four corners. Massing: a 7 x 7 two-step hipped roof (eaves at y 3 on the edge,
the upper course at y 4 over the post ring) with a 3 x 3 hole over the stone; the posts stand on stone footings at the
corners of the inner ring. Plan: edge = benches (west, back, east) and flower beds in the corners, inner ring = a walk
all round the stone, centre = the collar and the stone, front = open, the road's way in. Story: a roadside shrine where
travelers sit out of the sun and rain until the stone takes them; the open roof lets the sky (and the mod's travel
light) down onto the stone, so it is never in shadow.

**Strongest: C.** It is the only one with a silhouette: a low hipped roof reads as a building from across the village,
and its open middle makes a square ring on the hall's map with the stone dot in it (the top view says "waystone"). It is
worked on every side (the same eaves, corner lanterns and bench backs all round; the front is the one open face, as a
way in should be). It gives the brief's "seat for travelers to wait" a real meaning (shelter, benches facing the stone),
and it keeps every brief rule free of fiddling: the posts stand on the inner ring's corners and the roof stays above
head height (nothing at y 3 over the walk, so a villager stepping on or off the collar never meets a lintel), so all eight cells round the stone (the collar) are open with two blocks of sky over them, and the inner ring
(2 out) is an open walk on all four sides. The risk is that a roof could hide the stone, so the roof is cut away over it
and stays a block under the stone's crown height on the eaves.

## How the brief's pins are met

| Pin | Where |
|---|---|
| Waystone lower (3, 2, 3), upper (3, 3, 3), culture's kind, `origin=village`, `waterlogged=false` | `recipe.mjs` pass 4, `LOOKS[look].stone` |
| Exactly one `waystones:` block per look | only pass 4 sets one (checked on the .nbt) |
| 7 x 5 x 7, nothing above y 4 | `SIZE`, the canvas refuses out-of-bounds cells |
| Open spot beside the stone, every side | the collar's 8 cells: a stair at y 1, air at y 2 and y 3 (sky over); the inner ring at y 1: air at y 1 and y 2 over paving |
| Toward +x, +z | (4, 2, 4): the collar's south-east corner, open |
| Builder and walking villager reach the stone | front edge (z 0) is a slab step, the inner ring and collar are open walks |
| Front middle (3, 0, 0) walkable, (3, 1, 0) and (3, 2, 0) clear | the path slab; the eave at y 3 is over head height |
| No job block, POI, bed or storage | none drawn |
| Benches are not single stairs | three three-seat runs with stone arms and slab caps |
| Survival-safe blocks only | no saplings, cactus, snow layers, ice, mushrooms or berry bushes; flowers in their own soil |

## Per culture

The geometry is one; each look's palette swaps every material and the desert roof's form:

- **plains**: cobble and stone field, stone-brick walk ring and collar, oak posts, spruce roof with dark oak hips, oak benches,
  poppies and cornflowers. Stone: `waystones:waystone`.
- **desert**: sandstone field, cut-sandstone walk, stripped jungle posts, a flat sandstone sunshade (a cut-sandstone
  fascia with chiseled corners and a slab lid, no pitch: the desert's flat roofs), jungle benches on sandstone arms, dead bushes in sand. Stone: `waystones:sandy_waystone`.
- **savanna**: packed mud and mud-brick field, mud-brick walk and collar, acacia posts, orange acacia roof with dark oak hips, acacia
  benches, tulips and grass. Stone: `waystones:mud_bricks_waystone`.
- **snowy**: stone-brick field, polished diorite walk ring and collar (pale, under the dark deepslate stone), spruce
  posts, deepslate-tile roof with polished deepslate hips, spruce benches, bluets and lilies of the valley. Stone: `waystones:deepslate_waystone`.
- **taiga**: cobble and mossy cobble field, mossy stone-brick walk and collar, stripped spruce posts, spruce roof with dark oak hips, spruce benches, ferns in podzol. Stone: `waystones:mossy_waystone`.

## Iterations (renders in build/rebuild/waystone_plaza/render/)

1. A ring of log beams at y 3 over the walk, the roof's upper course on it: check.py read every walk cell as a doorway
   (a lintel 2 up, posts at the ends) with the collar's stairs beside it (a villager stepping up hits its head). The
   beams moved up to y 4 as a crown.
2. The crown read as a box sitting on the roof from every side. Replaced by the roof's own upper course (stairs climbing
   inward at y 4) carried on the posts' heads: a true two-course hipped roof. Desert: the lanterns can't hang from top
   slabs (check.py), so the fascia's corners are full chiseled blocks.
3. The roof read as one flat colour from 20 blocks: the four hips (diagonals) in a darker stair now draw its shape.
