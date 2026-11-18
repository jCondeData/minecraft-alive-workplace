// Stone House, the TAIGA look (looks/taiga/stone_house[_n], ROADMAP 49.10b, "as 49.10a"): the same house as the shipped
// Stone House (../stone_house: its rooms, beds, job blocks, containers and every furniture spot in the same cell at
// every level, the same size per level and the same 4-deep cellar under IV), drawn again for a village in the cool
// spruce forest. The Starter Cottage's taiga look set the language: dark spruce logs notched at the corners on a mossy
// fieldstone course, slate roofs gable-on to the road, a porch, log ends, firewood stacked under the eaves. This is the
// same culture's stone house: a forester's lodge, its ground storey all mossy fieldstone, the log storey on top.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Log longhouse": one long low log range, the road side a long eave with a deep veranda. Lodge-like, but the
//     shipped plan is nearly square and two storeys, so a longhouse would bury the chamber in a low attic, and the
//     snowy house already shows the road its long side: the two would share a face.
//  2. "Stone saltbox in spruce": the shipped house with mossy cobble and spruce trim. Safe, and only a palette.
//  3. "The Forester's Lodge": the house turned GABLE-ON to the road under one broad 45-degree slate gable whose eaves
//     come down low at the sides (y 6); the ground storey of mossy fieldstone, the storey above of dark spruce logs
//     laid on a log sill whose ends cross out at the corners; a front pent roof across the whole gable on log posts
//     with brackets (the porch, a lantern by the door, ferns in podzol beds), a mossy fieldstone chimney climbing the
//     west eave, and firewood stacked under the west eave. II carries the east slope straight on down over the
//     parlour (a catslide: the front shows a long low wing of the same roof); III grows the lodge back: a log pen
//     built on behind, the gable moved back to it; IV the store below.
//  Built: 3. The broad dark gable to the road (the snowy house shows its long white-striped side) reads "lodge" and
//  tells the two cultures apart at a glance; stone below and logs above is a stone house the taiga way; and each
//  level grows out of the last the way a forester would add to it: the roof swept on over a side room, a log pen on
//  the back. Not the safest: the low side eaves leave the chamber only knee walls, so its light comes from the gables.
//
// --- Design notes (2026-11-18) ---------------------------------------------------------------------------------------
//  Coordinates as the shipped house: x across the front (west 0 -> east), y up, z from the front (z = 0) back.
//  Main     walls x 1..9, z 1..9: mossy fieldstone y 1..4; a spruce log sill ring at y 5 (the upper floor's line),
//           its ends crossing out at the corners; logs above: one course on the sides (y 6), the gables to the roof.
//           The roof: ridge along z at x 5, y 11; eaves at x 0 and 10 on y 6; rakes over z 0 and the back.
//  Porch    a pent of slate across the front at z 0 (y 5) on log posts at x 0 and 10, brackets, a lantern by the door.
//  Chimney  west, x 0: breast z 2..4 to y 5, the stack (z 3) up the eave to y 10, smoke at y 11.
//  Wing     II: walls x 9..12, z 2..8, all fieldstone; the main roof's east slope swept on down over it to y 4 at x 12.
//  Back     III: a log pen x 1..9, z 9..13 on a fieldstone course (y 1), the main roof run back over it to a log gable at
//           z 13; a ceiling at y 7 over the back room, a loft above.
//  Cellar   IV: the shipped store, cell for cell.
//  Lamps    the shipped furniture cell for cell; only the hanging lamps' chains change: the chamber's to the ridge (2),
//           the parlour's straight under the eave (0).
//  Done (2026-11-18): all four levels saved as candidates (build.sh -> build/rebuild/stone_house_taiga/), each with no CHECK
//           line, holes.py 0 (open included), check.py's owner rules and checks 0, and every furniture spot of the
//           shipped Stone House present in the same cell with the same kind and facing at every level (29/34/39/47).
//           Blocks per level 665/748/948/1158 (shipped 759/851/996/1206); II keeps 99 % of I, III 97 % of II, IV 100 % of III.
//           Not shipped (no SHIP=1): the critic goes first.
//  Round 2 (2026-11-18, the critic's optional polish; round 1 SHIPped it narrowly): one tidy firewood stack under
//           the west eave with a slab over it; II/III firewood stacked end-on in the parlour's east wall under the
//           catslide; III stripped spruce lintels over the back pen's windows and posts beside its loft window (the
//           plot's edge leaves no cell outside the back wall for a window box or brackets).
//  Left for the critic: the back gable stands flush at z 13 in III (the plot's edge leaves no cell for an overhang).
import { FURNITURE as SH_FURNITURE, OPENINGS as SH_OPENINGS, MASSES as SH_MASSES, BASEMENT, SIZES, IDS } from '../stone_house/plan.mjs'

export { BASEMENT, SIZES, IDS }
export const LEVELS = [1, 2, 3, 4]

// PALETTE 60/30/10: 60 dark spruce logs and mossy fieldstone (cobble, mossy cobble, stone); 30 dark slate roof
// (deepslate tiles); 10 dressed corner stones, spruce trim, ferns and firewood.
export const PALETTE = {
  stone: [['cobblestone', 5], ['mossy_cobblestone', 3], ['stone', 1], ['andesite', 1]],
  plinth: [['mossy_cobblestone', 2], ['cobblestone', 2], ['mossy_stone_bricks', 1]],
  chimney: [['cobblestone', 4], ['mossy_cobblestone', 3], ['stone', 1]],
  dressed: 'stone_bricks', dressedAlt: 'mossy_stone_bricks', cap: 'mossy_stone_bricks',
  log: 'spruce_log', post: 'spruce_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  trim: 'spruce_stairs', sill: 'cobblestone_stairs',
  wood: 'spruce', floor: 'spruce_planks', partition: 'spruce_planks', glass: 'glass_pane',
  firewood: ['spruce_log'], trimLog: 'stripped_spruce_log', woodCap: 'spruce_slab', bedSoil: 'moss_block', plants: ['fern'],
  cellarFloor: 'cobblestone', cellarWall: [['cobblestone', 3], ['stone', 2], ['mossy_cobblestone', 1]],
}

export const MASSES = {
  main: { x0: 1, z0: 1, x1: 9, z1: 9, upperY: 5, roof: { axis: 'z', across: [0, 10], along: [0, 10], eaveY: 6, steep: false } },
  porch: { x0: 0, x1: 10, z: 0, y: 5, posts: [0, 10], lamps: [5] },
  chimney: { x: 0, z: 3, breast: [2, 4], breastTop: 4, top: 10 },
  woodpile: { x: 0, zs: [5, 6], top: 3 }, // one tidy stack under the west eave, a slab over it (round 2)
  wing: { x0: 9, z0: 2, x1: 12, z1: 8, slope: [[11, 5], [12, 4]], along: [1, 9], // the catslide's columns [x, y]
    wood: { x: 12, zs: [6, 7], top: 3 } }, // round 2: firewood stacked end-on in the east wall under the catslide
  // round 2: stripped spruce lintels over the back pen's windows and posts either side of its loft window
  backTrim: { lintels: [[2, 4], [5, 7]], y: 4, z: 13, loftPosts: [4, 6], loftY: [8, 9] },
  back: { x0: 1, z0: 9, x1: 9, z1: 13, along: [0, 13], ceilingY: 7 },
  cellar: SH_MASSES.cellar,
}

export const OPENINGS = {
  frontDoor: SH_OPENINGS.frontDoor, stairs: SH_OPENINGS.stairs, partition: SH_OPENINGS.partition,
  wingDoorway: SH_OPENINGS.wingDoorway, backDoorway: SH_OPENINGS.backDoorway,
  windows: [
    { name: 'hall front west', cells: [[3, 2, 1], [3, 3, 1]], out: 'north', dress: 'shutters' },
    { name: 'hall front east', cells: [[7, 2, 1], [7, 3, 1]], out: 'north', dress: 'shutters' },
    { name: 'box room back', cells: [[3, 2, 9], [3, 3, 9]], out: 'south', dress: 'shutters', until: 2 },
    { name: 'box room west', cells: [[1, 2, 7], [1, 3, 7]], out: 'west', dress: null },
    { name: 'kitchen back', cells: [[7, 2, 9], [7, 3, 9]], out: 'south', dress: 'shutters', until: 2 },
    { name: 'hall east', cells: [[9, 2, 7], [9, 3, 7]], out: 'east', dress: 'shutters', until: 1 },
    { name: 'chamber front', cells: [[5, 6, 1], [5, 7, 1]], out: 'north', dress: 'shutters' },
    { name: 'front gable', cells: [[5, 9, 1]], out: 'north', dress: null },
    { name: 'chamber back', cells: [[5, 6, 9], [5, 7, 9]], out: 'south', dress: 'shutters', until: 2 },
    { name: 'parlour front', cells: [[11, 2, 2], [11, 3, 2]], out: 'north', dress: 'shutters', from: 2 },
    { name: 'parlour east', cells: [[12, 2, 5]], out: 'east', dress: null, from: 2 },
    { name: 'back room west', cells: [[1, 2, 11], [1, 3, 11]], out: 'west', dress: 'shutters', from: 3 },
    { name: 'back room back west', cells: [[3, 2, 13], [3, 3, 13]], out: 'south', dress: null, from: 3 },
    { name: 'back room back east', cells: [[6, 2, 13], [6, 3, 13]], out: 'south', dress: null, from: 3 },
    { name: 'loft back', cells: [[5, 8, 13], [5, 9, 13]], out: 'south', dress: null, from: 3 },
  ],
}

// The shipped furniture, cell for cell; only the hanging lamps' chains change.
const chains = { '5,8,5': 2, '10,5,5': 0 }
export const FURNITURE = SH_FURNITURE.map(f => (f.part === 'hangingLamp' && chains[f.pos.join()] !== undefined) ? { ...f, chain: chains[f.pos.join()] } : f)

export const OUTSIDE = {
  fernBeds: [{ x0: 1, x1: 2, z: 0 }, { x0: 8, x1: 9, z: 0 }], // under the porch, either side of the windows
}
