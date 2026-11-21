// Carpenter's Workshop (49, the rebuild), the plan: drawn before any wall. Rooms, every furniture footprint and the
// walking line come first; recipe.mjs assembles the building from the parts library (../parts) to fit this plan.
//
// Three rough concepts (massing, roof, plan; the story in one line each):
//   A. The joiner's shopfront. A two-storey shop, its upper floor jettied over the street on brackets, a 45-degree
//      gable roof with the ridge along x, a wide glazed front, the bed upstairs. Story: a tradesman in a market row
//      shows his work in the window. Handsome but generic: any shop could be this; the trade is only in the window.
//   B. The saw shed. A long low open-sided shed on posts (a 45-degree roof, ridge along z) with a saw pit and log
//      piles, and a small closed room at the back for the table, chest and bed. Story: where the timber is cut. Reads
//      as a yard, not a home; the closed room is a box tacked on, and the open shed is most of the blocks and no room.
//   C. The cruck hall. One tall hall raised on a pair of cruck trusses (curved blades from the ground to the apex,
//      their tips crossing over the ridge), its slate roof sweeping almost to the ground, the front bay left
//      open as a work porch under the gable (sawhorse, boards), a lean-to timber shed down the east side, a loft under
//      the roof for the bed and the seasoning stock, a hoist beam out of the gable. Story: the carpenter's own house is
//      his masterpiece: he raised its frame himself, and the frame is what you see from the road.
// C is the strongest: the building *is* the trade (the joinery is the facade, not a sign on it), it has one hero
// gable (a 45-degree A to y 10, the flue to y 11) over two low masses (the porch recess and the lean-to), and its silhouette (the truss's dark
// blades framing a steep A) says "carpenter" from across a village where every other house has a plain gable. Built: C.
//
// Coordinates: x across the front (west 0 -> east 13), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The way in is on the z = 0 side.
//
// Named parts (for the owner's feedback):
//   Hall       the main mass: walls x 2..10, z 2..9 (posts a block proud of nothing: the frame IS the wall line,
//              stripped-oak boards between dark oak posts), ground room y 1..4, beam ring and loft floor y 5, a 45-degree
//              slate roof of stairs on every course (ridge along z, y 10) overhanging the front by two rows and the back by one.
//   Crucks     the front truss standing proud in front of the recessed front gable (z 2, boards at z 3), the inner
//              truss across the loft (z 5), the back truss in the back gable (z 9); (the blades meet under the
//              ridge; round 2 dropped the crossed tips over it: they read as loose blocks). The porch arch: two curved braces under the tie beam.
//   Porch      the open front bay under the front gable (x 3..9, z 1..2): the sawhorse and a stack of boards, a
//              lantern either side of the door.
//   Shop       the ground room (x 3..9, z 4..8): workbench under the west window, the crafting table and the
//              carpenter's chest beside it, the assembly table in the middle, the ladder in the back corner.
//   Loft       under the roof (floor y 5): the carpenter's bed at the back, the seasoning stock at the front under
//              the hoist window, the inner truss in between.
//   Shed       the lean-to timber store down the east side (x 11..12, z 3..8), open to the road and the east, logs
//              racked along the hall's wall, a doorway from the shop.
//   Dormer     a window in the west slope over the loft's bed.
//   Flue       the glue stove's stone stack up the east wall, through the east slope, smoking.
//   Hoist      a beam out of the front gable at y 11 with a chain and lantern: how boards go up to the loft.
//
// Village variant (the ship step; ideas only): keep the room box (x 2-6, z 3-7) as the carpenter's bench room with the
// contract's cells, and give each style its own cruck front: plains (as here, spruce and stripped oak), desert
// (acacia blades, smooth sandstone infill, a flat-roofed shed), savanna (acacia blades, a thatch-coloured hay roof
// over the shed), snowy (spruce, a steeper snow-shedding gable, the shed walled in), taiga (dark spruce logs, mossy
// plinth, spruce blades). The proud front truss stays the family's mark in every style.

export const SIZE = [14, 12, 11] // w, h, d (limit 18 x 18 x 16)

// Palette (60/30/10): pale stripped-oak boards laid along each wall (walls and gables, 60) / a grey stone-slate roof
// (deepslate tiles) and the dark oak frame and crucks (30) / a rubble plinth, spruce floors and joinery, glass and
// lanterns (10). Why: a carpenter builds in wood, so the wood carries the walls: planed pale boards between dark
// structural timber; the roof is the one thing he did not make, cool grey slates that make the warm timber read and
// keep the steep mass from going one brown block. The rubble keeps it off the wet ground. One glass (panes).
export const PALETTE = {
  stone: [['cobblestone', 5], ['stone', 2], ['andesite', 1], ['mossy_cobblestone', 1]],
  stoneStairs: 'cobblestone_stairs',
  frame: 'dark_oak_log',
  infill: 'stripped_oak_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' }, // stone slates
  trim: 'dark_oak', // barge boards, braces
  floor: 'spruce_planks',
  loftFloor: 'oak_planks', // pale boards: the shop's ceiling
  wood: 'spruce', // doors, shutters, ladder side, chairs, fences
  stock: 'oak_planks', // boards for sale and seasoning
  logs: 'oak_log', // the racked timber
  beam: 'stripped_spruce_log', // the sawhorse's beam
  glass: 'glass_pane',
}

export const MASSES = {
  hall: {
    posts: { x0: 2, x1: 10, z0: 2, z1: 9 },
    room: { x0: 3, x1: 9, z0: 4, z1: 8 },
    frontWallZ: 3, // the shop's front wall and the front gable's boards, a block behind the front truss
    beamY: 5, // walls y 1..4 (the eaves above head height), the beam ring and loft floor at y 5
    sidePosts: [2, 6, 9], frontPosts: [5, 7], backPosts: [2, 6, 10],
    // a 45-degree pitch the stairs alone make (round 3: the steep pitch's alternating tiles read jagged), the ridge a row of full tiles
    roof: { axis: 'z', across: [1, 11], along: [1, 10], eaveY: 5, steep: false },
    gableY: 6,
    trusses: { front: { line: 2, infill: 3 }, inner: { line: 5 }, back: { line: 9 } },
    dormer: { face: 2, zc: 7, y0: 6, dir: -1 }, // the west slope, the loft's window over the bed
    flue: { x: 10, z: 4, top: 10 }, // the glue stove's stack up the east wall and out through the east slope
  },
  shed: {
    floor: { x0: 11, x1: 12, z0: 3, z1: 8 },
    posts: [[12, 3], [12, 8]], // the eave on two posts, open between them to the east and the road
    roof: { axis: 'z', across: [11, 12], ridge: 10, along: [2, 9], ridgeY: 5 }, // x 11 at y 4, under the hall's eave; x 12 at y 3
  },
}

export const ROOMS = [
  { name: 'Porch', use: 'sawing under the front truss, logs and boards for sale', box: [3, 1, 1, 9, 4, 2] },
  { name: 'Shop', use: 'the crafting table, workbenches, glue stove, tool rack, timber stock and the carpenter\'s chest', box: [3, 1, 4, 9, 4, 8] },
  { name: 'Loft', use: 'the carpenter\'s bed and the seasoning stock', box: [3, 6, 3, 9, 8, 8] },
  { name: 'Shed', use: 'log piles', box: [11, 1, 3, 12, 2, 8] },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 3], facing: 'south', step: [6, 0, 0] },
  porchFloor: { x0: 3, x1: 9, z: 1 }, // the apron under the eave, in front of the hall's plinth
  shedDoor: { at: [10, 1, 7], facing: 'west' }, // from the shop into the timber shed
  ladder: { x: 7, z: 8, y0: 1, top: 5, facing: 'north' }, // up the back wall, through the loft floor under the ridge
  windows: [
    { name: 'shop front west', cells: [[3, 2, 3], [4, 2, 3], [3, 3, 3], [4, 3, 3]], out: 'north', dress: null }, // under the porch
    { name: 'shop front east', cells: [[8, 2, 3], [9, 2, 3], [8, 3, 3], [9, 3, 3]], out: 'north', dress: null },
    { name: 'shop west front', cells: [[2, 2, 4], [2, 3, 4]], out: 'west', dress: 'shutters' },
    { name: 'shop west back', cells: [[2, 2, 7], [2, 2, 8], [2, 3, 7], [2, 3, 8]], out: 'west', dress: 'shutters' },
    { name: 'shop back west', cells: [[4, 2, 9], [4, 3, 9]], out: 'south', dress: 'shutters' },
    { name: 'shop back east', cells: [[8, 2, 9], [8, 3, 9]], out: 'south', dress: 'shutters' },
    { name: 'loft front (hoist)', cells: [[6, 6, 3]], out: 'north', dress: null },
    { name: 'loft back', cells: [[6, 6, 9], [6, 7, 9]], out: 'south', dress: null },
  ],
}

// Every piece of furniture, footprint first (kind: furniture.py's; fixtures are vanilla blocks the game uses as
// themselves). facing: the way a person using it faces.
const bench = (x, z, facing) => ({ room: 'Shop', kind: 'counter', part: 'counter', pos: [x, 1, z], facing, wood: 'spruce', top: 'spruce_slab' })
export const FURNITURE = [
  // Shop: an L of workbench along the west and north walls either side of the door, the crafting table (the job
  // block) and the carpenter's chest on the west wall, the joiner's bench in the middle, the glue stove in the corner
  bench(3, 4, 'west'), bench(3, 5, 'west'), bench(4, 4, 'north'), bench(5, 4, 'north'), bench(7, 4, 'north'), bench(8, 4, 'north'),
  { room: 'Shop', kind: 'fixture', part: 'worktop', pos: [3, 1, 6], facing: 'west', block: 'crafting_table' },
  { room: 'Shop', kind: 'fixture', part: 'pantry', pos: [3, 1, 7], facing: 'east', block: 'chest' },
  { room: 'Shop', kind: 'fixture', part: 'stove', pos: [9, 1, 4], facing: 'west', block: 'furnace' },
  { room: 'Shop', kind: 'cupboard', part: 'cupboard', pos: [5, 1, 8], facing: 'north', wood: 'spruce' },
  bench(6, 6, 'north'), bench(7, 6, 'north'),
  { room: 'Shop', kind: 'shelf', part: 'stockShelf', pos: [6, 3, 8], facing: 'north', wood: 'spruce', on: 'candle' },
  { room: 'Shop', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 6], facing: 'north', wood: 'spruce' },
  { room: 'Shop', kind: 'lamp', part: 'hangingLamp', pos: [8, 4, 7], facing: 'north', wood: 'spruce' },
  // Porch: lanterns either side of the door, hung from the tie beam
  { room: 'Porch', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 2], facing: 'north', wood: 'spruce' },
  { room: 'Porch', kind: 'lamp', part: 'hangingLamp', pos: [7, 4, 2], facing: 'north', wood: 'spruce' },
  // Loft: the bed under the dormer window, a candle on a nightstand at its head, a chest at its foot, a rug, a lamp
  { room: 'Loft', kind: 'bed', part: 'bed', pos: [4, 6, 7], facing: 'south', colour: 'green' },
  { room: 'Loft', kind: 'nightstand', part: 'nightstand', pos: [5, 6, 8], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'fixture', part: 'chest', pos: [4, 6, 6], facing: 'east', block: 'chest' },
  { room: 'Loft', kind: 'rug', part: 'rug', cellsAbs: [[5, 6, 7], [6, 6, 7]], colour: 'brown' },
  { room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [6, 8, 7], facing: 'north', chain: 1, wood: 'spruce' },
]

// The walking line: road -> step -> porch -> door -> shop -> the table; -> the shed door; -> up the ladder -> bed.
export const WALK = [
  [6, 0, -1], [6, 0, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [5, 1, 5], [4, 1, 5], [4, 1, 6],
  [4, 1, 7], [5, 1, 7], [6, 1, 7], [7, 1, 7], [8, 1, 7], [9, 1, 7], [10, 1, 7], [11, 1, 7],
  [7, 1, 8], [7, 5, 8], [7, 6, 7], [6, 6, 6], [5, 6, 6],
]

// Outside the rooms (not furniture spots): the porch's log pile, sawhorse (a board on it) and plank stacks, the shop's
// saw stock and tool rack, the log piles in the shed, the seasoning stock in the loft, the cruck feet and the porch
// arch's braces, a flower bed by the west wall.
export const OUTSIDE = {
  porchLogs: { cells: [[3, 1, 1], [3, 1, 2], [3, 2, 1], [3, 2, 2]] },
  sawhorse: { a: [4, 1, 1], b: [4, 1, 2], board: [4, 3, 2] },
  boards: { cells: [[8, 1, 1], [9, 1, 1], [8, 1, 2], [9, 1, 2], [9, 2, 2]], top: [[8, 2, 2], [9, 2, 1]] },
  shopLogs: { cells: [[8, 1, 5], [9, 1, 5], [8, 2, 5]] },
  shopBoards: { cells: [[4, 1, 8], [9, 1, 8]], top: [[4, 2, 8], [9, 2, 8]] },
  toolRack: { cells: [[9, 3, 8], [9, 4, 8]], facing: 'north' }, // hooks up the back wall's post over the board stack (B1451: not on the pane at x 8)
  rack: { cells: [[11, 1, 3], [11, 1, 4], [11, 1, 5], [11, 2, 3], [11, 2, 4], [11, 2, 5], [11, 3, 3], [11, 3, 4], [11, 3, 5],
    [12, 1, 4], [12, 1, 5], [11, 1, 8], [11, 2, 8]] },
  shedPile: { cells: [], top: [[12, 2, 5]] },
  loftStock: { cells: [[5, 6, 4], [5, 7, 4]], logs: [[7, 6, 3], [7, 6, 4], [7, 7, 3], [7, 7, 4]] },
  feet: [[1, 0, 2, 'east'], [11, 0, 2, 'west']],
  braces: [[3, 4, 2, 'west'], [9, 4, 2, 'east']],
  // the front truss's inner curve: upside-down stairs in the elbows beside the loft window, under the blades
  elbows: [[5, 6, 2, 'west'], [7, 6, 2, 'east']],
  flowerBed: { x: 1, z0: 5, z1: 6, plants: ['poppy', 'cornflower'] },
}
