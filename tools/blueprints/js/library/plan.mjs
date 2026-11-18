// Library, the plan (ROADMAP 49, brief: tools/blueprints/briefs/library.md): drawn before any wall.
//
// Three rough concepts:
//   1. The Book Tower: a square three-storey stone tower, a pyramid cap, a reading room per floor up a stair. Story: a
//      scholar's keep. Strong silhouette, but the lecterns end up on separate floors, the 13 x 13 plot leaves rooms
//      the size of closets, and III's enchanting ring cannot sit within reach of a lectern two floors up.
//   2. The Cloister: a low ring of reading cells round a garden court, a lean-to roof all round. Story: a quiet
//      monastic school. Lovely in plan, flat from the road; the court eats Level I's 13 x 13 and the shelves face air.
//   3. The Lamplit Nave: a tall stone reading hall like a little church of books, buttressed between high lancet
//      windows that throw light over the shelves onto the tables, a steep slate-dark roof with a rose window in its
//      front gable. II grows a lower cross-wing aisle off the east side; III raises a square tented tower behind the
//      nave (the enchanting room: the table in the middle, ringed by shelves, under a lantern on a long chain); IV
//      digs the archive under the nave.
// Strongest: 3. It reads as a library from the road (a hall of light, not a house), every level adds a mass with its
// own roof (gable, cross gable, pyramid) so the silhouette climbs from front to back, and every lectern stands on one
// floor within 8 of the enchanting table. Built below.
//
// Story: the Lamplit Nave. The village raises a hall of dressed stone for its books, props its long walls with
// buttresses so the windows can be tall, and roofs it in dark slate tiles. Inside, the shelves run under the windows,
// the reading tables sit under lanterns hung from the tie beams, and the lectern closes the aisle like an altar.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back.

export const IDS = { 1: 'library', 2: 'library_2', 3: 'library_3', 4: 'library_4' }
export const SIZES = { 1: [13, 15, 12], 2: [18, 15, 12], 3: [18, 18, 22], 4: [18, 18, 22] }
export const BASEMENT = { 1: 0, 2: 0, 3: 0, 4: 4 }

// Palette (60/30/10): stone bricks (walls, buttresses) / deepslate tile roof and dark oak beams, floors and doors /
// blue: the LEARNING banner's color in the pictures and the aisle's runner, and the lanterns' warm light.
export const PALETTE = {
  wall: [['stone_bricks', 8], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1]],
  plinth: [['cobbled_deepslate', 3], ['deepslate_bricks', 2]],
  pier: 'deepslate_bricks', pierCap: 'deepslate_brick_stairs',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  sill: 'stone_brick_stairs',
  dressing: 'chiseled_stone_bricks',
  beam: 'dark_oak_log', floor: 'dark_oak_planks', archiveFloor: 'deepslate_tiles', wood: 'dark_oak',
  glass: 'glass_pane', shelf: 'bookshelf', job: 'lectern', table: 'enchanting_table', store: 'chest',
  accent: 'blue', band: 'polished_deepslate', post: 'deepslate_brick_wall',
}

export const MASSES = {
  nave: { walls: { x0: 1, x1: 11, z0: 1, z1: 10 }, room: { x0: 2, x1: 10, z0: 2, z1: 9 }, wallTop: 6,
    roof: { axis: 'z', across: [0, 12], along: [0, 11], eaveY: 6, steep: false }, tieBeams: [4, 7], beamY: 6,
    buttresses: [1, 4, 7, 10] },
  aisle: { walls: { x0: 11, x1: 17, z0: 2, z1: 8 }, room: { x0: 12, x1: 16, z0: 3, z1: 7 }, wallTop: 5,
    roof: { axis: 'x', across: [1, 9], along: [12, 17], eaveY: 5, steep: false }, arch: { x: 11, z0: 5, z1: 6, top: 3 } },
  tower: { walls: { x0: 2, x1: 10, z0: 11, z1: 19 }, room: { x0: 3, x1: 9, z0: 12, z1: 18 }, wallTop: 10,
    roof: { x0: 1, z0: 10, x1: 11, z1: 20, y: 11 }, ways: [3, 9], tieBeams: [13, 17], beamY: 6 },
  archive: { x0: 1, z0: 1, x1: 11, z1: 10, depth: 4, ladder: { x: 10, z: 9, facing: 'west' } },
}

export const ROOMS = [
  { name: 'Nave', level: 1, use: 'the reading hall: shelves under the windows, two reading desks, the first lectern', box: [2, 1, 2, 10, 5, 9] },
  { name: 'Aisle', level: 2, use: 'the east reading aisle through a wide arch: the second lectern, shelves, a desk', box: [12, 1, 3, 16, 4, 7] },
  { name: 'Enchanting tower', level: 3, use: 'the enchanting table ringed by shelves, the third lectern facing it, the lapis chest', box: [3, 1, 12, 9, 8, 18] },
  { name: 'Archive', level: 4, use: 'the stacks under the nave: shelves and chests of records, down a ladder', box: [2, -3, 2, 10, -1, 9] },
]

export const JOBS = [
  { level: 1, at: [6, 1, 8], facing: 'north', stand: [6, 1, 7] }, // closes the nave's aisle
  { level: 2, at: [14, 1, 7], facing: 'north', stand: [14, 1, 6] }, // in the aisle, against its back wall
  { level: 3, at: [6, 1, 12], facing: 'south', stand: [6, 1, 13] }, // facing the enchanting table on the axis
]
export const ENCHANT = { at: [6, 1, 15], gap: [[-1, -2], [0, -2], [1, -2]] } // the way in from the lectern's side

// Book stacks ([x, z] cells, y range): 42 in Level I (west 18, east 12, front 12).
export const SHELVES = [
  { level: 1, y: [1, 3], cells: [[2, 3], [2, 4], [2, 5], [2, 6], [2, 7], [2, 8]] },
  { level: 1, y: [1, 3], cells: [[10, 3], [10, 4], [10, 7], [10, 8]] },
  { level: 1, y: [1, 2], cells: [[2, 2], [3, 2], [4, 2], [8, 2], [9, 2], [10, 2]] },
  { level: 2, y: [1, 3], cells: [[16, 3], [16, 4], [16, 5], [16, 6]] },
  { level: 4, y: [-3, -2], cells: [[2, 3], [2, 4], [2, 5], [2, 6], [2, 7], [2, 8], [4, 4], [4, 5], [4, 6], [8, 4], [8, 5], [8, 6]] },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 1], facing: 'south', step: [6, 0, 0] },
  portal: { cols: [4, 8], z: 0, top: 3 },
  backButtresses: [3, 9], backWindows: [5, 7],
  rose: [[6, 8], [5, 9], [6, 9], [7, 9], [6, 10]], // [x, y] in the front gable (z = 1)
  frontLancets: { cells: [[3, 1], [9, 1]], y: [3, 4] },
  sideWindows: { zs: [[2, 3], [5, 6], [8, 9]], y: [4, 5] }, // pairs between the buttresses, both long walls
  backWindow: { cells: [[6, 10]], y: [7, 9] },
  aisleFront: { cells: [[14, 2], [15, 2]], y: [2, 3] },
  aisleGable: { cells: [[17, 4], [17, 5], [17, 6]], y: [4, 5], frame: [[17, 6, 4], [17, 6, 6], [17, 7, 5], [17, 3, 4], [17, 3, 5], [17, 3, 6]] },
  towerLancets: [[[2, 15]], [[10, 15]], [[2, 13]], [[10, 13]], [[2, 17]], [[10, 17]], [[4, 19]], [[8, 19]]],
}

// Every piece, footprint first (kind: a furniture.py kind; part: the furnitureParts part, or hangingPot).
export const FURNITURE = [
  // Nave: two reading desks with their chairs, lamps over them, pots and a banner
  { level: 1, kind: 'desk', part: 'desk', pos: [4, 1, 6], facing: 'south', wood: 'dark_oak' },
  { level: 1, kind: 'chair', part: 'chair', pos: [4, 1, 5], facing: 'south', wood: 'dark_oak' },
  { level: 1, kind: 'desk', part: 'desk', pos: [8, 1, 6], facing: 'south', wood: 'dark_oak' },
  { level: 1, kind: 'chair', part: 'chair', pos: [8, 1, 5], facing: 'south', wood: 'dark_oak' },
  { level: 1, kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 4], facing: 'north', chain: 1, wood: 'dark_oak' },
  { level: 1, kind: 'lamp', part: 'hangingLamp', pos: [8, 4, 4], facing: 'north', chain: 1, wood: 'dark_oak' },
  { level: 1, kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 7], facing: 'north', chain: 1, wood: 'dark_oak' },
  { level: 1, kind: 'lamp', part: 'hangingLamp', pos: [8, 4, 7], facing: 'north', chain: 1, wood: 'dark_oak' },
  { level: 1, kind: 'hanging_pot', part: 'hangingPot', pos: [6, 5, 4], plant: 'potted_fern', wood: 'dark_oak' },
  { level: 1, kind: 'hanging_pot', part: 'hangingPot', pos: [6, 5, 7], plant: 'potted_azure_bluet', wood: 'dark_oak' },
  { level: 1, kind: 'picture', part: 'picture', pos: [6, 3, 9], facing: 'north', colour: 'blue', wood: 'dark_oak' },
  { level: 1, kind: 'picture', part: 'picture', pos: [10, 3, 5], facing: 'west', colour: 'light_blue', wood: 'dark_oak', until: 1 },
  { level: 1, kind: 'rug', part: 'rug', cellsAbs: [[6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6]], colour: 'blue' },
  // Aisle: a desk under the front window, its lamp and pot from the ridge, a banner
  { level: 2, kind: 'desk', part: 'desk', pos: [13, 1, 3], facing: 'north', wood: 'dark_oak' },
  { level: 2, kind: 'chair', part: 'chair', pos: [13, 1, 4], facing: 'north', wood: 'dark_oak' },
  { level: 2, kind: 'lamp', part: 'hangingLamp', pos: [14, 4, 5], facing: 'north', chain: 4, wood: 'dark_oak' },
  { level: 2, kind: 'hanging_pot', part: 'hangingPot', pos: [16, 8, 5], plant: 'potted_lily_of_the_valley', wood: 'dark_oak' },
  { level: 2, kind: 'picture', part: 'picture', pos: [12, 3, 7], facing: 'north', colour: 'blue', wood: 'dark_oak' },
  // Enchanting tower: lamps on the tie beams, the chandelier over the table, pots, a banner
  { level: 3, kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 13], facing: 'north', chain: 1, wood: 'dark_oak' },
  { level: 3, kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 13], facing: 'north', chain: 1, wood: 'dark_oak' },
  { level: 3, kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 17], facing: 'north', chain: 1, wood: 'dark_oak' },
  { level: 3, kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 17], facing: 'north', chain: 1, wood: 'dark_oak' },
  { level: 3, kind: 'lamp', part: 'hangingLamp', pos: [6, 6, 15], facing: 'north', chain: 9, wood: 'dark_oak' },
  { level: 3, kind: 'hanging_pot', part: 'hangingPot', pos: [5, 5, 17], plant: 'potted_blue_orchid', wood: 'dark_oak' },
  { level: 3, kind: 'picture', part: 'picture', pos: [6, 4, 18], facing: 'north', colour: 'blue', wood: 'dark_oak' },
]
export const STORES = [
  { level: 3, at: [3, 1, 18], facing: 'north' }, // the Librarian's lapis, in reach of the tower's lectern
  { level: 4, at: [3, -3, 9], facing: 'north' }, { level: 4, at: [5, -3, 9], facing: 'north' },
  { level: 4, at: [7, -3, 9], facing: 'north' }, { level: 4, at: [9, -3, 2], facing: 'south' },
]
export const ARCHIVE_LAMPS = [[4, -1, 3], [8, -1, 3], [6, -1, 7], [3, -1, 8], [9, -1, 6]]

// The walking line: step -> door -> the blue runner -> the first lectern; through the arch to the aisle (II); round
// the back of the nave through the tower ways to the third lectern and into the ring (III); down the ladder (IV).
export const WALK = [
  [6, 0, 0], [6, 1, 1], [6, 1, 2], [6, 1, 6], [6, 1, 7],
  [7, 1, 6], [10, 1, 6], [11, 1, 6], [13, 1, 6], [14, 1, 6],
  [9, 1, 9], [9, 1, 10], [9, 1, 11], [9, 1, 12], [7, 1, 12], [7, 1, 13], [6, 1, 13], [6, 1, 14],
  [10, 1, 9], [10, -3, 9],
]
