// Daub House, the TAIGA look (looks/taiga/house_t1_b, ROADMAP 49.10d, as 49.10a): the same building as the shipped
// Daub House (../house_t1_b/plan.mjs: its hall, kitchen and loft, the four beds, the kitchen's chest, furnace and
// crafting table, the loft's chest, the front door, the joined loft railing and every furniture spot in the same cell,
// the same 13 x 14 x 13 plot), drawn again for a village in the cool spruce forest. It speaks the taiga village's
// language (the Starter Cottage, Stone House and Terrace looks): dark spruce logs crossing at notched corners on a
// mossy fieldstone footing, broad 45-degree slate gables to the road with deep rakes, porches on log posts, log ends,
// fieldstone chimneys outside the gable, firewood under the eaves, ferns in moss.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Dogtrot": the hall and the kitchen as two log pens under one roof with an open breezeway between. A true forest
//     plan, but the shipped kitchen opens straight off the hall (the doorway at (7, 1..2, 5)): a breezeway would put
//     the door outside, and the hall's 13-deep pen leaves no room for a second one beside it at the same size.
//  2. "Log saltbox": the shipped hall in logs, its back slope run down in a long catslide over the kitchen. Calm, but
//     the kitchen stands on the east, not the back, so the catslide falls the wrong way: it would be a palette swap
//     of the drawing's lean.
//  3. "The Log Lodge": a one-and-a-half-storey pen of spruce logs, its broad 45-degree slate GABLE TO THE ROAD (the
//     loft lives inside it, boarded gables in pale stripped-log frames), the front wall set back a block between the
//     side walls' log ends so the gable rides over a recessed porch on a beam, a pent roof on log posts across the
//     front under the gable; the kitchen a lower log CROSS-WING on the east, its own gable to the east, its ridge
//     running into the hall's slope (a T of roofs); a fieldstone stack up the back gable past the ridge, firewood
//     stacked in the back recess either side of it. Story: a forester's family squares spruce from the clearing and
//     lays it up the way their grandparents did: logs that cross at the corners, a deep porch for boots and axes, a
//     roof broad enough to keep the wood dry.
//  Built: 3. It is the strongest: gable-on to the road with a pent across its foot, log ends at every corner and a T
//  of roofs, it says "log lodge" from 20 blocks and is nothing like the snowy look (pale stone, its long side to the
//  road under one sweeping roof) or the as-drawn daub frame.
//
// --- Design notes: what is done, what is left ------------------------------------------------------------------------
//  Done (2026-11-19): saved as a candidate (build.sh -> build/rebuild/house_t1_b_taiga/house_t1_b.nbt, 838 blocks).
//  Checked: look_spots.py house_t1_b_taiga house_t1_b: SAME BUILDING (40 shipped spots, 0 missing, 0 beds, containers,
//    job blocks or doors moved; only the kitchen lamp's chain differs: it hangs from the wing's ceiling; the loft's keep
//    their 2 links to the ridge); rebuild.py: no CHECK line; holes.py (open included): 0; every block id vanilla 1.21.1;
//    the only fences at y 4 or above are the loft railing's five (spruce, its corner (5, 5, 5) joined east and south).
//  Renders: build/rebuild/house_t1_b_taiga/renders/ (taiga_<front|back|left|right|top>.png; cutaways taiga_cut3_* for
//    the hall and kitchen, taiga_cut7_* for the loft).
//  Tried and changed: the stack's breast was drawn at x 3 and 5 only, leaving the hearth open to the back recess
//    (holes.py open): the breast is three wide; the pocket between the hall's east slope and the wing's roof left a
//    stray cell (holes.py): the valley is closed solid; a woodpile of log ends against the wing's gable read as a
//    stack of crates: dropped (the firewood stands in the back recess).
//  Open for the critic: the porch's recess under the front gable is a block deep, the pent before it a block more; the
//    renderer draws the logs' bark warm, so the walls read lighter in the renders than in the game.
//
// --- Named parts (x across the front, west 0 -> east 12; z from the front, z = 0, to the back) ---------------------
//   Pen       spruce logs: side walls x 1 and 7 from z 1 to 11 (y 1..3, the knee walls y 5..6), the front wall z 2 and
//             the back wall z 10 between them (the door at (4, 1, 2), the hearth at (4, 1, 10)), their logs crossing
//             the side walls and poking out at x 0 / 8 on every other course (notched corners).
//   Band      the loft floor's beam ring at y 4 in stripped spruce: the lintels at z 1 and 11 that carry the gables over
//             the porch and the back recess, the side walls' top course.
//   Gables    z 1 and z 11, y 5 up to the roof: spruce boards between stripped-log studs, a shuttered window, a little
//             attic pane at the front.
//   Roof      45 degrees, the ridge along z at x 4 (y 10), eaves on x 0 and 8 at y 6, rakes at z 0 and 12 with spruce
//             barge boards.
//   Porch     the recess x 2..6 on z 1 under the front gable, a pent roof across the front (x 1..7, z 0, y 4) on log
//             posts at x 1 and 7 with brackets, a plank deck, a lantern hung under the pent.
//   Wing      the kitchen: logs x 8..11, z 3..9 (y 1..5), a ceiling at y 5, its roof 45 degrees with the ridge along x at
//             z 6 (y 9) running into the hall's east slope, its gable on x 11 boarded like the hall's.
//   Stack     mossy fieldstone: a breast x 3..5 in the back recess (z 11..12, y 0..3), the flue x 4 on z 12 up through
//             the back rake to y 11, smoking.
//   Ground    a mossy fieldstone footing (y 0) under every log wall, firewood in the back recess (x 2 and 6), moss beds
//             of ferns either side of the porch.
export { DAUB_FURNITURE as FURNITURE } from '../parts/daub_looks.mjs'

export const SIZE = [13, 14, 13] // as shipped

// Palette 60/30/10: 60 dark spruce logs; 30 dark slate roof (deepslate tiles) and mossy fieldstone; 10 pale stripped
// spruce (the band, the gable studs), spruce boards, ferns and firewood. The pale band and studs keep the dark walls
// from running into the dark roof.
export const PALETTE = {
  log: 'spruce_log', band: 'stripped_spruce_log', stud: 'stripped_spruce_log', boards: 'spruce_planks',
  footing: [['cobblestone', 4], ['mossy_cobblestone', 3], ['stone', 1]],
  stack: [['cobblestone', 3], ['mossy_cobblestone', 3], ['stone', 1]], shoulder: 'mossy_cobblestone_stairs',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  barge: 'spruce_stairs', post: 'spruce_log', bracket: 'spruce_stairs', deck: 'spruce_planks', sill: 'spruce_stairs',
  floor: 'spruce_planks', upperFloor: 'spruce_planks', kitchenFloor: 'cobblestone', under: 'spruce_planks',
  ceiling: 'spruce_planks', wood: 'spruce', glass: 'glass_pane', soil: 'moss_block', plant: 'fern',
  firewood: ['spruce_log', 'spruce_log', 'birch_log', 'oak_log'],
}

export const MASSES = {
  hall: { x0: 1, x1: 7, z0: 1, z1: 11, front: 2, back: 10, groundTop: 3, bandY: 4, kneeTop: 6,
    roof: { axis: 'z', across: [0, 8], along: [0, 12], eaveY: 6, steep: false } },
  kitchen: { x0: 8, x1: 11, z0: 3, z1: 9, wallTop: 5, ceilingY: 5,
    roof: { axis: 'x', across: [2, 10], along: [5, 12], eaveY: 5, steep: false } },
  porch: { x0: 1, x1: 7, z: 0, y: 4, posts: [1, 7], lamps: [4] },
  stack: { x: 4, z: 12, breast: [3, 5], breastZ: [11, 12], breastTop: 3, top: 11 },
  studs: [2, 6], // the gables' stripped-log studs
}

// Windows: glass with spruce shutters and a spruce sill, every one alike (the porch's two under the pent have sills
// only: the side walls' log ends stand where their shutters would).
export const WINDOWS = [
  { name: 'hall front west', cells: [[2, 2, 2], [2, 3, 2]], out: 'north', dress: 'sill' },
  { name: 'hall front east', cells: [[6, 2, 2], [6, 3, 2]], out: 'north', dress: 'sill' },
  { name: 'hall west', cells: [[1, 2, 6], [1, 3, 6]], out: 'west', dress: 'shutters' },
  { name: 'loft front', cells: [[4, 5, 1], [4, 6, 1]], out: 'north', dress: 'shutters' },
  { name: 'loft attic', cells: [[4, 8, 1]], out: 'north', dress: null },
  { name: 'loft west front', cells: [[1, 5, 4]], out: 'west', dress: null },
  { name: 'loft west back', cells: [[1, 5, 8]], out: 'west', dress: null },
  { name: 'loft back west', cells: [[2, 6, 11]], out: 'south', dress: null },
  { name: 'loft back east', cells: [[6, 6, 11]], out: 'south', dress: null },
  { name: 'kitchen front', cells: [[10, 2, 3], [10, 3, 3]], out: 'north', dress: 'shutters' },
  { name: 'kitchen east', cells: [[11, 2, 6], [11, 3, 6]], out: 'east', dress: 'shutters' },
  { name: 'kitchen back', cells: [[10, 2, 9], [10, 3, 9]], out: 'south', dress: 'shutters' },
  { name: 'kitchen gable', cells: [[11, 7, 6]], out: 'east', dress: null },
]

export const OUTSIDE = {
  step: { at: [4, 0, 1], id: 'spruce_stairs' },
  firewood: [[2, 11], [6, 11]],
  ferns: [[0, 0], [8, 0], [12, 2]],
}
