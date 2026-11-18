// Stone House, the SNOWY look (looks/snowy/stone_house[_n], ROADMAP 49.10b, "as 49.10a"): the same house as the shipped
// Stone House (../stone_house: its rooms, beds, job blocks, containers and every furniture spot in the same cell at
// every level, the same size per level and the same 4-deep cellar under IV), drawn again for a village in deep snow.
// The Starter Cottage's snowy look set the village's language: pale calcite and diorite stone, white birch trim,
// steep dark slate roofs carrying snow, a drift round the walls, a closed entry, a heavy chimney. This is the same
// culture's stone house: bigger, all stone, and the roof is most of it.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Snow chalet": the shipped house turned gable-on to the road under a steep gable, a balcony across the gable.
//     Striking, but the chamber's 9-wide floor needs a 45-degree roof to keep its walls, a balcony has no cell (the
//     front is the plot's edge), and gable-on is the taiga lodge's face: the two looks would read as one.
//  2. "Stone saltbox": the shipped shape in pale stone with snow on it. Safe, and only a palette: its 45-degree slopes
//     are the very roof that holds a snow load.
//  3. "The Snowbound House": the long side to the road and a STEEP slate roof (two up per one across) coming down to
//     the top of the ground storey front and back, so the roof is two thirds of the house; every course of slate a
//     step with snow lying on it, snow on the ridge, a drift round the walls. Pale calcite-and-diorite rubble walls
//     with grey dressed quoins, white birch posts and a calcite dormer face. The chamber lives inside the roof, lit by a
//     steep gabled dormer over the desk and a small gable window. A closed storm porch (stone cheeks under the eave)
//     before the door, firewood stacked to the eaves either side of it, and a massive stone stack on the west gable,
//     its breast three wide. II: a steep cross-gable wing on the east end (lower, its gable to the east); III: a
//     steep back wing as tall as the house, a T of gables that sheds snow every way; IV: the store under it.
//  Built: 3, the strongest and least safe: the roof turned and steepened changes the house's whole read (a white-
//  striped slate mass with a pale stone foot, unlike the taiga lodge's broad dark gable), each level adds a gable,
//  and every cell of the shipped rooms holds: the chamber's furniture all sits where a steep roof still clears it.
//
// --- Design notes (2026-11-18) ---------------------------------------------------------------------------------------
//  Coordinates as the shipped house: x across the front (west 0 -> east), y up, z from the front (z = 0) back.
//  Main     walls x 1..9, z 1..9; ground storey y 1..4, upper floor y 5 (a grey polished andesite band in the walls);
//           the steep roof: ridge along x at z 5, y 14, eaves at z 0 and z 10 on y 5, rakes at x 0 and 10.
//  Chimney  west, x 0: breast z 2..4 to y 9, the stack (z 3) to y 14 past the ridge, a cap and smoke at y 15.
//  Wing     II: walls x 9..12, z 2..8, a steep roof (ridge along x at z 5, y 12; eaves z 1 and 9 at y 5), its gable
//           at x 12; a tie beam at y 7 carries the parlour's lamp.
//  Back     III: walls x 1..9, z 9..13, a steep roof (ridge along z at x 5, y 14) meeting the main roof's back slope
//           (a cross gable), its gable at z 13; a ceiling at y 6 over the back room.
//  Cellar   IV: the shipped store, cell for cell (x 1..9, z 1..9, 4 deep).
//  Snow     snow layers on every roof tread and ridge, on the window sills, and a drift skirt on the ground, never on
//           the doorstep (5, 0, 0) or the walks to the doors. Snow layers sit on full blocks only (the treads are full
//           tiles), so the builder places each on a solid top.
//  Lamps    the furniture of the shipped house cell for cell; only the hanging lamps' chains change to reach what is
//           above them here: the chamber's to the ridge (5), the back room's to its ceiling (0).
//  Done (2026-11-18): all four levels saved as candidates (build.sh -> build/rebuild/stone_house_snowy/), each with no CHECK
//           line, holes.py 0 (open included), check.py's owner rules and checks 0, and every furniture spot of the
//           shipped Stone House present in the same cell with the same kind and facing at every level (29/34/39/47).
//           Blocks per level 907/1066/1371/1581 (shipped 759/851/996/1206); II keeps 98 % of I, III 95 % of II, IV 100 % of III.
//           Not shipped (no SHIP=1): the critic goes first.
//  Left for the critic: the back gable stands flush at z 13 (the plot's edge leaves no cell for an overhang), and
//           so does the wing's at x 12.
import { FURNITURE as SH_FURNITURE, OPENINGS as SH_OPENINGS, MASSES as SH_MASSES, BASEMENT, SIZES, IDS } from '../stone_house/plan.mjs'

export { BASEMENT, SIZES, IDS }
export const LEVELS = [1, 2, 3, 4]

// PALETTE 60/30/10: 60 pale rubble (calcite, diorite, stone) on a darker stone-brick plinth; 30 dark slate roof
// (deepslate tiles) under white snow; 10 dressed grey quoins, white birch boards and trim, spruce doors and shutters.
export const PALETTE = {
  rubble: [['calcite', 3], ['diorite', 3], ['stone', 2], ['polished_diorite', 1], ['andesite', 1]],
  plinth: [['stone_bricks', 3], ['cobblestone', 2], ['andesite', 1]],
  chimney: [['stone_bricks', 4], ['calcite', 2], ['stone', 2], ['cobblestone', 1]],
  course: 'polished_andesite', // the upper floor's line in the walls: a grey band round the pale stone
  dressed: 'stone_bricks', dressedAlt: 'polished_andesite', // quoins
  sill: 'stone_brick_stairs', cap: 'stone_bricks',
  gable: 'calcite', trim: 'stripped_birch_log', post: 'birch_log', // the dormer's white face and birch posts
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  snow: 'snow',
  firewood: ['spruce_log', 'birch_log', 'oak_log'], // split rounds end-on, mixed woods
  wood: 'spruce', floor: 'spruce_planks', partition: 'spruce_planks', log: 'spruce_log', glass: 'glass_pane',
  cellarFloor: 'cobblestone', cellarWall: [['cobblestone', 3], ['stone', 2], ['diorite', 1]],
}

export const MASSES = {
  main: { x0: 1, z0: 1, x1: 9, z1: 9, upperY: 5, roof: { axis: 'x', across: [0, 10], along: [0, 10], eaveY: 5, steep: true } },
  chimney: { x: 0, z: 3, breast: [2, 4], breastTop: 9, top: 14 },
  dormer: { cx: 5, face: 1, y0: 6 },
  wing: { x0: 9, z0: 2, x1: 12, z1: 8, roof: { axis: 'x', across: [1, 9], along: [10, 12], eaveY: 5, steep: true }, tie: { y: 7, z: 5, x0: 10, x1: 11 } },
  back: { x0: 1, z0: 9, x1: 9, z1: 13, roof: { axis: 'z', across: [0, 10], along: [5, 13], eaveY: 5, steep: true }, ceilingY: 6 },
  cellar: SH_MASSES.cellar,
  porch: { x: 5, z: 0, top: 4 }, // the storm porch's cheeks at x 4 and 6 on the front row
}

export const OPENINGS = {
  frontDoor: SH_OPENINGS.frontDoor, stairs: SH_OPENINGS.stairs, partition: SH_OPENINGS.partition,
  wingDoorway: SH_OPENINGS.wingDoorway, backDoorway: SH_OPENINGS.backDoorway,
  // small windows: two-high on the ground storey with snow on their stone sills, one or two panes in the gables
  windows: [
    { name: 'hall front west', cells: [[3, 2, 1], [3, 3, 1]], out: 'north', dress: 'sill', snow: true },
    { name: 'hall front east', cells: [[7, 2, 1], [7, 3, 1]], out: 'north', dress: 'sill', snow: true },
    { name: 'box room back', cells: [[3, 2, 9], [3, 3, 9]], out: 'south', dress: 'sill', snow: true, until: 2 },
    { name: 'box room west', cells: [[1, 2, 7], [1, 3, 7]], out: 'west', dress: 'shutters' },
    { name: 'kitchen back', cells: [[7, 2, 9], [7, 3, 9]], out: 'south', dress: 'sill', snow: true, until: 2 },
    { name: 'hall east', cells: [[9, 2, 7], [9, 3, 7]], out: 'east', dress: 'shutters', until: 1 },
    { name: 'chamber west', cells: [[1, 7, 6], [1, 8, 6]], out: 'west', dress: 'shutters' },
    { name: 'chamber east', cells: [[9, 7, 5], [9, 8, 5]], out: 'east', dress: 'shutters', until: 1 },
    { name: 'west gable', cells: [[1, 11, 5]], out: 'west', dress: null },
    { name: 'parlour front', cells: [[11, 2, 2], [11, 3, 2]], out: 'north', dress: 'sill', snow: true, from: 2 },
    { name: 'parlour east', cells: [[12, 2, 5], [12, 3, 5]], out: 'east', dress: null, from: 2 },
    { name: 'wing gable', cells: [[12, 8, 5], [12, 9, 5]], out: 'east', dress: null, from: 2 },
    { name: 'back gable', cells: [[5, 8, 13], [5, 9, 13]], out: 'south', dress: null, from: 3 },
    { name: 'back room west', cells: [[1, 2, 11], [1, 3, 11]], out: 'west', dress: 'shutters', from: 3 },
    { name: 'back room back west', cells: [[3, 2, 13], [3, 3, 13]], out: 'south', dress: null, from: 3 },
    { name: 'back room back east', cells: [[6, 2, 13], [6, 3, 13]], out: 'south', dress: null, from: 3 },
  ],
}

// The shipped furniture, cell for cell; only the hanging lamps' chains change.
const chains = { '5,8,5': 5, '5,5,11': 0 }
export const FURNITURE = SH_FURNITURE.map(f => (f.part === 'hangingLamp' && chains[f.pos.join()] !== undefined) ? { ...f, chain: chains[f.pos.join()] } : f)

export const OUTSIDE = {
  // firewood stacked to the eaves either side of the storm porch (out of the snow, under the front eave)
  firewood: [[1, 0], [2, 0], [8, 0], [9, 0]], firewoodTop: 3,
  // where the drift stays clear: the doorstep and its approach
  bare: [[5, 0], [4, 0], [6, 0]],
}
