// Daub House, the SNOWY look (looks/snowy/house_t1_b, ROADMAP 49.10d, as 49.10a): the same building as the shipped
// Daub House (../house_t1_b/plan.mjs: its hall, kitchen and loft, the four beds, the kitchen's chest, furnace and
// crafting table, the loft's chest, the front door, the joined loft railing and every furniture spot in the same cell,
// the same 13 x 14 x 13 plot), drawn again for a village in deep snow. It speaks the snowy village's language (the
// Starter Cottage, Stone House and Terrace looks): one pale stone (calcite) on a grey stone-brick foot, grey dressed
// quoins and a polished-andesite band, steep dark slate (deepslate tiles) carrying snow on its ridges and eaves, deep-set
// windows, a closed storm porch, a broad stack, one continuous drift round the walls, white birch trim.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Snow chalet": the hall gable-on to the road under a steep gable with a balcony across it. Striking, but the
//     balcony has no cell (the loft's beds stand right behind the front gable), and gable-on to the road is the taiga
//     lodge's face: the two looks would read as one.
//  2. "The longhouse under one roof": hall, kitchen and a woodshed in a single long range, one ridge from end to end.
//     Calm, but one roof over a 7-wide hall and a 3-wide kitchen leaves the kitchen a tall empty attic and the plot's
//     14-block height takes only a 45-degree pitch over the 13-deep hall: a long flat-looking box.
//  3. "The Snow-Roof House": the hall turned to show the road its LONG side, a 45-degree slate roof sweeping down to an
//     eave right over the ground storey (y 5) on both sides, so the roof is most of the house and the loft lives inside
//     it; a steep little dormer for the children's beds, a stone storm porch on the door, deep windows in a two-thick
//     front wall; a lower, narrower kitchen roof on the east (its ridge 2 below the hall's), its gable to the east; a
//     broad three-flue stack up the back slope past the ridge; firewood under the kitchen's eaves. Story: a family that
//     has learned that in deep snow the roof is the house: low walls the drifts can lean on, a roof that sheds, one
//     warm stack in the middle of it all.
//  Built: 3. It is the strongest: from 20 blocks it is a big white-striped slate roof on a pale stone foot with a stack
//  standing out of it, nothing like the taiga lodge's dark log gable to the road; the kitchen's lower gable nested
//  against the hall's gives the east a stepped outline, the dormer and porch give the front its depth.
//
// --- Design notes: what is done, what is left ------------------------------------------------------------------------
//  Done (2026-11-19): saved as a candidate (build.sh -> build/rebuild/house_t1_b_snowy/house_t1_b.nbt, 921 blocks).
//  Checked: look_spots.py house_t1_b_snowy house_t1_b: SAME BUILDING (40 shipped spots, 0 missing, 0 beds, containers,
//    job blocks or doors moved; only the lamps' chains differ: the loft's reach the slate, the kitchen's hangs from its
//    ceiling); rebuild.py: no CHECK line; holes.py (open included): 0; every block id vanilla 1.21.1; the only fences
//    at y 4 or above are the loft railing's five (spruce, its corner (5, 5, 5) joined east and south).
//  Renders: build/rebuild/house_t1_b_snowy/renders/ (snowy_<front|back|left|right|top>.png; cutaways snowy_cut3_* for
//    the hall and kitchen, snowy_cut7_* for the loft).
//  Tried and changed: snowLoad (made for steep roofs, a full block under every stair) read this 45-degree roof's every
//    stair as an eave and laid snow on each course (stripes): snow now only on the ridges and the true eave rows; the
//    dormer's boards closing its pocket left the children's rug 1.94 under the ceiling (rebuild.py's CHECK): the pocket
//    opens into the loft; the three-flue stack ran up as one flat slab: it steps in to its middle flue at y 10; the
//    hall's east rake rides over the kitchen's roof, the hall's gable carried out under it (x 8) so no slot shows.
//  Round 2 (2026-11-19, briefs/house_t1_b_looks_critic.md): 1 the hall's east rake over the wing is carried out at x 8
//    in deepslate tiles, not calcite, so it reads as one dark band; 2 the dormer's verge is the main roof's deepslate
//    (stairs and its apex tile), the birch only in its face; 3 the firewood is spruce alone, two logs laid along the
//    wing's wall (bark out) at y 1, under the kitchen windows' sills, front and back; 4 (optional) the andesite band
//    carried round the stack's breast (y 4). Rechecked: look_spots SAME BUILDING (40/40, 0 moved), holes.py 0, no CHECK
//    line (919 blocks). Renders: build/rebuild/house_t1_b_snowy/renders_r2/.
//  Open for the critic: the gables' rakes are plain deepslate stairs (no barge board), the walls stopping under each
//    stair; the back wall has no windows (the hearth, the cupboard and the flight stand against it inside).
//
// --- Named parts (x across the front, west 0 -> east 12; z from the front, z = 0, to the back) ---------------------
//   Hall      walls x 1..7, z 1..11; the ground storey y 1..3 two thick at the front (z 1..2) and back (z 10..11), the
//             band y 4 (polished andesite) round it, knee walls y 5 at z 1 and 11, calcite gables on x 1 and 7.
//   Roof      45 degrees, the ridge along x at z 6 (y 11), eaves at z 0 and 12 (y 5), rakes at x 0 and 8, snow along
//             its ridge and both eaves.
//   Dormer    a three-wide gabled dormer at x 4 on the front slope (face z 1, panes y 5..6), its own slate roof, birch
//             boards in its gable, snow along its ridge.
//   Porch     a storm porch at the door: stone cheeks at x 3 and 5 on z 0, a birch lintel, a lantern under it.
//   Kitchen   walls x 8..11, z 3..9 (y 1..5), a ceiling at y 5; its roof 45 degrees, ridge along x at z 6 (y 9), eaves
//             z 2 and 10 (y 5), its gable on x 11 with the rake at x 12.
//   Stack     the hearth's stack x 3..5 in the back wall's plane (z 11) up through the back slope to y 12, smoking on
//             its middle flue, a breast under the back eave (z 12).
//   Ground    firewood under the kitchen's front and back eaves, one drift of snow round it all, the doorstep bare.
export { DAUB_FURNITURE as FURNITURE } from '../parts/daub_looks.mjs'

export const SIZE = [13, 14, 13] // as shipped

// Palette 60/30/10: 60 white calcite walls; 30 dark slate roof (deepslate tiles) under white snow; 10 grey stone-brick
// foot, quoins and stack, the polished-andesite band, birch lintels and dormer boards, spruce door and shutters.
export const PALETTE = {
  wall: 'calcite',
  plinth: [['stone_bricks', 3], ['cobblestone', 1]],
  stack: [['stone_bricks', 5], ['cracked_stone_bricks', 1]], cap: 'stone_brick_slab',
  band: 'polished_andesite', quoin: 'stone_bricks', quoinAlt: 'polished_andesite',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  snow: 'snow', lintel: 'stripped_birch_log', post: 'stripped_birch_log', dormerFace: 'birch_planks',
  sill: 'stone_brick_stairs', step: 'stone_brick_stairs',
  floor: 'spruce_planks', upperFloor: 'spruce_planks', kitchenFloor: 'stone_bricks', under: 'spruce_planks',
  ceiling: 'spruce_planks', wood: 'spruce', glass: 'glass_pane',
  firewood: 'spruce_log',
}

export const MASSES = {
  hall: { x0: 1, x1: 7, z0: 1, z1: 11, groundTop: 3, bandY: 4,
    roof: { axis: 'x', across: [0, 12], along: [0, 8], eaveY: 5, steep: false } },
  kitchen: { x0: 8, x1: 11, z0: 3, z1: 9, wallTop: 5, ceilingY: 5,
    roof: { axis: 'x', across: [2, 10], along: [8, 12], eaveY: 5, steep: false } },
  dormer: { cx: 4, face: 1, y0: 5 },
  porch: { x: 4, z: 0, top: 4 },
  stack: { x0: 3, x1: 5, z: 11, shoulder: 10, top: 12, flues: [4], breastZ: 12, breastTop: 4 },
}

// Windows: deep ones in the two-thick front (glass in its inner face, a birch lintel over the reveal, snow on its
// sill); shuttered ones with a stone sill everywhere else (every one dressed alike on its face).
export const WINDOWS = [
  { name: 'hall front west', cells: [[2, 2, 2], [2, 3, 2]], out: 'north', deep: true },
  { name: 'hall front east', cells: [[6, 2, 2], [6, 3, 2]], out: 'north', deep: true },
  { name: 'hall west', cells: [[1, 2, 6], [1, 3, 6]], out: 'west', dress: 'shutters' },
  { name: 'loft west front', cells: [[1, 5, 4], [1, 6, 4]], out: 'west', dress: 'shutters' },
  { name: 'loft west back', cells: [[1, 5, 8], [1, 6, 8]], out: 'west', dress: 'shutters' },
  { name: 'loft gable', cells: [[1, 8, 6]], out: 'west', dress: null },
  { name: 'kitchen front', cells: [[10, 2, 3], [10, 3, 3]], out: 'north', dress: 'shutters' },
  { name: 'kitchen east', cells: [[11, 2, 6], [11, 3, 6]], out: 'east', dress: 'shutters' },
  { name: 'kitchen back', cells: [[10, 2, 9], [10, 3, 9]], out: 'south', dress: 'shutters' },
  { name: 'kitchen gable', cells: [[11, 7, 6]], out: 'east', dress: null },
]

export const OUTSIDE = {
  step: { at: [4, 0, 0], id: 'stone_brick_stairs' },
  firewood: [{ x0: 8, x1: 9, z: 2, top: 1 }, { x0: 8, x1: 9, z: 10, top: 1 }],
  bare: [[4, 0], [4, 1]],
}
