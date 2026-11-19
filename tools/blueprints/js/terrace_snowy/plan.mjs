// Terrace, the SNOWY look (looks/snowy/terrace[_2], ROADMAP 49.10c, "as 49.10a"): the same row as the shipped Terrace
// (../terrace: its homes, rooms, beds, doors, containers, ladders and every furniture spot in the same cell at both
// levels, the same 13x?x10 and 18x?x10 plots), drawn again for a village in deep snow. The snowy cottage, stone house
// and townhouse set the language: pale calcite walls with grey dressed stone, steep dark slate roofs carrying snow on
// their ridges and outer eaves, deep-set windows, closed entries, broad stacks, one continuous drift round the walls.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "One long snow roof": the ridge turned along the row, a steep roof over all the homes, the end walls the only
//     gables. Calm, but a steep roof across the 10-deep plot rises past the 16-high plot (the ridge at y 16 before its
//     snow) unless the eaves come down to the bedroom floor, which blinds the bedrooms' front windows; and a long ridge
//     along the street is the taiga look's shape (see ../terrace_taiga), so the two would blur.
//  2. "Storm-porch row": the shipped gables kept, a little gabled storm porch built out before each door. Each porch
//     roof would meet its house's front gable in two small valleys per home (the Townhouse critic's commonest note:
//     jagged valleys and porch roofs cut into slopes), and the 1-deep front row leaves the porches only one cell deep.
//  3. "The Snowbound Row": every household its own steep white gable to the street, a row of them shoulder to shoulder
//     with a straight lead gutter over each party wall (no other valley anywhere); the ground storey's front built out a
//     second skin thick, so the door and the front-room window sit deep in reveals under birch lintels, snow drifted on
//     the sills, the skin capped by a grey weathering; a pale pier on every party wall carrying the gutters' ends; each
//     home's broad two-flue stone stack astride its ridge, smoking; snow lying along
//     every ridge and the row's two outer eaves and nowhere else, a drift skirt round the walls. II adds one more gable.
//  Built: 3. Its outline says "several homes in snow" from 20 blocks (a saw of steep snow-capped gables, one stack a
//  home), it is the opposite of the taiga look's single long log range under one broad roof, and its roof plan has only
//  straight gutters, so every rake can be one clean verge. Not the safest: the thick skin hides the front room's window
//  in a reveal and costs the shipped row's stoop; the front's depth must come from the reveals and the gutters' piers.
//
// --- Design notes (2026-11-19) ---------------------------------------------------------------------------------------
//  Coordinates as the shipped Terrace: x across the front (west 0 -> east), y up, z from the front (z = 0) back. Each
//  home in its own frame u = 0..5 across (x = L + u; L 1, 6, 11; u 0 and 5 its side walls), as ../terrace/plan.mjs.
//  Walls    pale calcite x L..L+5, z 1..8, y 1..8, on a stone-brick plinth; a grey andesite
//           band at the bedroom floor (y 5) round the row; grey long-and-short quoins at the row's corners.
//  Skin     z 0, y 1..4 across the row, its cap a row of stone-brick stairs at y 5 rising to the wall; the door's reveal
//           (u 4, y 1..3) under a grey andesite lintel, a lantern hung in it, a stone step; the front room's window
//           (u 1..2, y 2..3) deep in its reveal under an andesite lintel, snow on the sill.
//  Roofs    one steep gable per home (evenGableRoof: eaves y 9; round 3: the ridge one course at y 12), running z 0..9
//           (a rake one block over each gable), a gutter (full block and slab) over every party wall, the row's ends
//           eaves with their overhang at x L-1. Every rake row (z 0 and 9) a verge: the block under each stair an
//           upside-down stair facing the eave (rakeVerge), so each rake is one clean edge.
//  Gables   plain pale calcite to the peak (round 3: no panes). Bedroom
//           windows on the front (u 1..2 and u 4, y 6..7) on the band, an andesite lintel in the wall over each (y 8).
//  Piers    every party wall stands a pale pier at z 0 (y 5..8, over the skin) and at z 9 (y 1..8), the gutter's end on it.
//  Stacks   each home's broad stack astride its ridge: u 2..3 x z 4..5, y 12..14 (stone bricks), the bedroom lamp's
//           chain hanging from its foot; a campfire on u 2 (y 15), a stone-brick slab on u 3.
//  Windows  the back: the kitchen (u 2, y 2..3) and the bedroom (u 1..2, y 7..8, an andesite lintel at y 9): the
//           shipped cells; the row's end walls: two shuttered windows a storey (z 3, 6), dark spruce shutters.
//  Snow     the ridges (behind the rake rows the two ridge stairs made a full block with two layers), the row's two outer eaves (their
//           overhang lip, one line), the window reveals' sills; never on a tread, a gutter, the skin's weathering or a
//           step. One continuous drift (two layers) round the walls.
//  Inside   the shipped furniture cell for cell (../terrace/plan.mjs FURNITURE); the bedroom lamps keep their 2-link
//           chains (the ridge is where the shipped ridge was).
//  Outside  the kept rain barrel at (0, 0, 1).
//  Own render rounds (2026-11-19): 1 the first stacks stood on the back gables from the ground, two wide: from the back
//           they read as two detached stone towers over the roof, so each now sits astride its ridge (broad, low, the
//           cold country's stack); the birch lintels rendered as glowing peach panels in the white wall (now grey
//           andesite, one with the band); 2 the gables' two-high panes ran into the rake and read as crosses stuck in
//           the roof (now one row under a calcite course); the bedroom's back panes back on the shipped cells (y 7..8,
//           clear of the bed heads).
//  Done (2026-11-19): both levels saved as candidates (build.sh -> build/rebuild/terrace_snowy/terrace[_2].nbt), no
//           CHECK line from rebuild.py, holes.py 0 (open included), look_spots.py SAME BUILDING (24/36 shipped spots, no
//           bed, container, door or ladder moved, no lamp chain changed). Blocks 974/1389; II keeps 93 % of I. Sizes as
//           shipped (13x16x10, 18x16x10; the stacks' campfires at y 15). Every block a vanilla 1.21.1 id. Not shipped.
//  Round 2 (2026-11-19, the critic's notes, briefs/terrace_looks_critic.md): 1 every gable comes to a point: on the rake
//           rows (z 0 and 9) the ridge is one course of stairs back to back at y 13 over air, and the calcite gable is
//           carried up through y 12 under it (z 1 and 8), so no blunt dark square ends a gable; the ridge's snow lies on
//           the full ridge behind (z 1..8: a snow layer can't rest on a stair's sloped top in the game); 2 the eave's
//           overhang at each row end stays a stair on the rake rows (x 0 / x 12, y 8, no snowed full block there), so each
//           end's verge runs x 0 y 8, x 1 y 9, x 2 y 10..11, x 3 y 13 down onto the lip; 3 the end walls carry two
//           shuttered windows a storey (z 3 and 6) instead of one. Blocks 970/1377; look_spots SAME BUILDING, holes.py 0,
//           no CHECK line.
//  Round 3 (2026-11-19, the critic's round-2 notes): 1 the ridge is one course at y 12 (the two-high y 12..13 ridge is
//           gone): two deepslate stairs back to back over u 2..3 on the gable rows (z 0..2 and 7..9), so each gable's pale
//           calcite (to y 11) ends in a dark peak closed by them; full blocks only at z 3..6 round the stack, carrying the
//           ridge's snow (2 layers at y 13; a snow layer can't rest on a stair in the game), set back so the snow reads as
//           lying on the ridge behind the peak, not as a cap on it; the gable panes (y 10) dropped, which read as dark
//           holes under the peak; 2 each row end's eave lip stays a stair at z 0..1 and 8..9 (x 0 / x 12, y 8), its snow
//           only z 2..7, so the verge runs down onto a stair foot, not a snowy shelf; 3 rechecked after 1: the rake reads
//           x 0 y 8, x 1 y 9, x 2 y 10..11, x 3 y 12, every home the same, an even verge to the peak. Blocks 926/1313;
//           look_spots SAME BUILDING, holes.py 0. 4 (optional) not taken: the band already rings the end walls.
//  Open for the critic: a steep 2:1 rake is a stair over an upside-down stair per step (rakeVerge), the Townhouse's
//           accepted verge; from three-quarter views it still shows the 2:1 rhythm. The front room's window sits in a
//           reveal, so the front's glass is set back a block from the street.
import { HOMES, at, OPENINGS as T_OPENINGS, FURNITURE, SIZES, IDS } from '../terrace/plan.mjs'

export { HOMES, at, FURNITURE, SIZES, IDS }
export const LEVELS = [1, 2]

// PALETTE 60/30/10: 60 pale calcite walls on a grey stone-brick plinth; 30 dark slate roofs (deepslate tiles) under
// white snow; 10 grey dressed stone (the andesite band and lintels, quoins, the skin's weathering), dark spruce doors
// and shutters.
export const PALETTE = {
  wall: [['calcite', 1]],
  plinth: 'stone_bricks', step: 'stone_brick_stairs', weathering: 'stone_brick_stairs',
  quoin: 'stone_bricks', quoinAlt: 'polished_andesite', band: 'polished_andesite',
  stack: [['stone_bricks', 1]], capSlab: 'stone_brick_slab',
  lintel: 'polished_andesite',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  snow: 'snow',
  floor: 'spruce_planks', joist: 'spruce_log', wood: 'spruce', door: 'spruce', glass: 'glass_pane',
}

export const HOUSE = {
  walls: { z0: 1, z1: 8 }, upperFloor: 5, wallTop: 8, eaveY: 9, band: 5,
  roofAlong: [0, 9], // a rake one block over each gable
  skin: { z: 0, top: 4 },
  stack: { us: [2, 3], zs: [4, 5], y0: 12, y1: 14 }, // astride the ridge, on the bedroom's ridge (its lamp hangs from it)
  piers: { front: [5, 8], back: [1, 8] }, // y ranges at z 0 and z 9
}

export const OPENINGS = {
  door: T_OPENINGS.door, ladder: T_OPENINGS.ladder,
  // round 2 (critic note 3): two shuttered windows a storey on the row's end walls (the shipped one was at z 4)
  endWindows: [{ z: 3, ys: [2, 3] }, { z: 6, ys: [2, 3] }, { z: 3, ys: [6, 7] }, { z: 6, ys: [6, 7] }],
  frontRoom: { us: [1, 2], ys: [2, 3] }, // deep in the skin: panes at z 1, the reveal at z 0
  windows: [
    { name: 'bedroom front', us: [1, 2], ys: [6, 7], z: 1, lintel: 8 },
    { name: 'bedroom over the door', us: [4], ys: [6, 7], z: 1, lintel: 8 },
    { name: 'kitchen back', us: [2], ys: [2, 3], z: 8 },
    { name: 'bedroom back', us: [1, 2], ys: [7, 8], z: 8, lintel: 9 },
  ],
}

export const OUTSIDE = { rainBarrel: [0, 0, 1] }
