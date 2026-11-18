// The Print Shop (print_shop, print_shop_2), the plan: drawn before any wall (ROADMAP 49, tools/blueprints/briefs/print_shop.md).
// recipe.mjs assembles it from the parts library (../parts) for LEVEL 1 or 2.
//
// Three rough concepts
//   A. The Lantern Press: one tall brick hall, its gable to the street, a steep slate roof, and on its ridge a square
//      brick light lantern whose glazed top is the press's skylight. Plan: shop counter by the door, the press in the
//      middle under the lantern's shaft, desk and paper tables round it. II sets a lower reading room beside it,
//      its own gable set back from the street, the two roofs meeting in a valley. Story: the printer needs daylight
//      on the forme more than anything, so the whole house is built round a hole in the roof.
//   B. The Corner Press: an L of brick with a jettied timber upper storey (the paper loft) over the shop, the press in
//      a glazed lean-to in the angle of the L. II puts the reading room in the loft. Story: a printer squeezed onto a
//      street corner, building up because he can't build out.
//   C. The Sawtooth Works: a long low brick shed under north-light sawtooth roofs, the press under a glazed ridge
//      cap at the end of the last tooth. II adds one more tooth for the reading room. Story: a small print works,
//      more factory than shop.
// The strongest is A. The one thing the code insists on (a single glass block over the press and nothing else above
// it) becomes the building's hero: a lantern on the ridge you can pick out from across the village, lit from inside
// at night, and the shaft under it throws daylight straight down onto the table. B spends most of its blocks on a
// loft the Printer doesn't need, and II can't put a floor over the press anyway; C's teeth make a strong outline but
// their glazing is vertical, so the press's skylight would be an afterthought, and a shed doesn't invite buyers in.
//
// Story (A): "The Lantern Press". The village's printer builds in fired brick (paper and ink burn; brick doesn't)
// under a dark slate roof, with a dark-oak shopfront: two display windows either side of the door, a fascia over
// them with the shop's lamps on brackets. Buyers step up onto the stone apron and in to the counter; behind it the
// press stands alone in the middle of the room in a column of light from the lantern above. Level II adds the
// Reading Room: a lower brick range beside the hall, its gable set two blocks back from the street, lined with
// shelves of books, reached through an arch in the hall's side wall.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The front door is on the north side with a stair step at y = 0 before it.
//
// Masses (named parts the owner's feedback can point at):
//   Hall          the main mass: brick walls x 1..9, z 1..9, y 1..4 on a stone-brick plinth (y 0); a 45-degree slate
//                 roof, ridge along z at x 5 (y 9), overhanging one block all round (x 0..10, z 0..10).
//   Lantern       the hero: a 5 x 5 brick lantern astride the ridge (x 3..7, z 3..7) glazed in its faces above a brick
//                 curb, a cap of slate stairs round a 3 x 3 glazed light at y 11. Its inner 3 x 3 is an open shaft
//                 down to the press, which stands in its middle column (5, 1, 5): the glass at (5, 11, 5) is the only
//                 block above the press.
//   Shopfront     the hall's street gable: two display windows and the door between them, a dark-oak fascia at y 4,
//                 bracket lamps either side of the door, a stone apron along the front (z 0) the sills stand on.
//   Reading Room  (II) a lower brick range east of the hall (walls x 9..15, z 3..9, the hall's east wall shared),
//                 its roof (ridge along z at x 12, y 8) meeting the hall's in a valley, its gable set back 2.
export const SIZES = { 1: [11, 12, 11], 2: [17, 12, 11] }

// Palette (60/30/10): red brick walls (60) / slate roof of deepslate tiles and a dark-oak shopfront and joinery (30) /
// stone-brick plinth, quoins and lintels, spruce floor (10). Why: brick is the printer's fire-safe trade material
// and the test's 40 bricks come free; the dark slate roof contrasts with the warm walls and makes the lantern's
// glass read; dark oak ties the counter (the test's dark oak slabs) to the shopfront outside.
export const PALETTE = {
  wall: 'bricks',
  stone: 'stone_bricks',
  plinth: [['stone_bricks', 5], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1]],
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'spruce_planks',
  wood: 'dark_oak',
  beam: 'dark_oak_log',
  fascia: 'stripped_dark_oak_log',
  glass: 'glass_pane',
  skylight: 'glass', // the one glass block type the press's test pins; the windows are panes
  ink: 'black_candle',
  paper: 'white',
}

export const MASSES = {
  hall: {
    walls: { x0: 1, x1: 9, z0: 1, z1: 9 }, wallTop: 4,
    roof: { axis: 'z', across: [0, 10], along: [0, 10], eaveY: 4 },
    tieBeams: [3, 7], // dark-oak tie beams across the room at y 4, either side of the shaft
  },
  lantern: { x0: 3, z0: 3, x1: 7, z1: 7, top: 11, glazed: [9, 10] }, // panes in its faces y 9..10, a brick curb below
  reading: { // Level II
    walls: { x0: 9, x1: 15, z0: 3, z1: 9 }, wallTop: 4,
    roof: { axis: 'z', across: [8, 16], along: [2, 10], eaveY: 4 }, // its west eave lies under the hall's slope
  },
}

export const PRESS = [5, 1, 5] // the cartography table, under the lantern's middle column
export const CHEST = [8, 1, 8] // the Printer's one chest, in the back corner, 4 from the press

export const ROOMS = [
  { name: 'Shop', use: 'buyers at the counter, the press under its light, the printer\'s desk, paper and ink', box: [2, 1, 2, 8, 3, 8] },
  { name: 'Reading Room', use: '(II) books on every wall and a reading table', box: [10, 1, 4, 15, 3, 8] },
]

export const OPENINGS = {
  frontDoor: { at: [5, 1, 1], facing: 'south', step: [5, 0, 0] },
  // display windows either side of the door (y 1..3 behind a low sill), side and back windows with shutters
  shopWindows: [[[2, 2, 1], [3, 2, 1], [2, 3, 1], [3, 3, 1]], [[7, 2, 1], [8, 2, 1], [7, 3, 1], [8, 3, 1]]],
  sideWindows: [
    { cells: [[1, 2, 3], [1, 3, 3]], out: 'west' }, { cells: [[1, 2, 7], [1, 3, 7]], out: 'west' },
    { cells: [[9, 2, 3], [9, 3, 3]], out: 'east', level: 1 }, { cells: [[9, 2, 7], [9, 3, 7]], out: 'east', level: 1 },
    { cells: [[3, 2, 9], [3, 3, 9]], out: 'south', dress: null }, { cells: [[7, 2, 9], [7, 3, 9]], out: 'south', dress: null }, // the yard side: plain
  ],
  frontGable: [[5, 6, 1], [5, 7, 1]],
  backGable: [[5, 6, 9], [5, 7, 9]],
  // Level II
  arch: { axis: 'z', line: 9, span: [5, 7], y0: 1, y1: 3 },
  readingWindows: [
    { cells: [[11, 2, 3], [12, 2, 3], [13, 2, 3], [11, 3, 3], [12, 3, 3], [13, 3, 3]], out: 'north', dress: 'sill' },
    // high library windows over the bookcases on the east wall
    { cells: [[15, 3, 5]], out: 'east', dress: 'shutters' }, { cells: [[15, 3, 7]], out: 'east', dress: 'shutters' },
  ],
  readingGables: [[[12, 5, 3], [12, 6, 3]], [[12, 5, 9], [12, 6, 9]]],
}

// Every piece of furniture, footprint first (kind: furniture.py's; part: the parts library's).
export const FURNITURE = [
  // Shop: the buyers' counter across the east side of the way in (paper and ink on it), the desk on the west wall
  { level: 1, room: 'Shop', kind: 'counter', part: 'slabCounter', pos: [6, 1, 3], facing: 'north', along: 'east', length: 3, wood: 'dark_oak', slab: 'dark_oak_slab', on: [['black_candle', { candles: '3', lit: 'true', waterlogged: 'false' }], 'white_carpet', 'white_carpet'] },
  { level: 1, room: 'Shop', kind: 'desk', part: 'inkDesk', pos: [2, 1, 5], facing: 'west', wood: 'dark_oak', candle: 'black_candle', candles: 2 },
  { level: 1, room: 'Shop', kind: 'chair', part: 'chair', pos: [3, 1, 5], facing: 'west', wood: 'dark_oak' },
  // paper laid out to dry on two tables at the back, ink on shelves on the back wall above them
  { level: 1, room: 'Shop', kind: 'table', part: 'table', pos: [3, 1, 8], facing: 'north', wood: 'dark_oak', colour: 'white' },
  { level: 1, room: 'Shop', kind: 'table', part: 'table', pos: [4, 1, 8], facing: 'north', wood: 'dark_oak', colour: 'white' },
  { level: 1, room: 'Shop', kind: 'shelf', part: 'inkShelf', pos: [3, 3, 8], facing: 'north', wood: 'dark_oak', candle: 'black_candle', candles: 1 },
  { level: 1, room: 'Shop', kind: 'shelf', part: 'inkShelf', pos: [6, 2, 8], facing: 'north', wood: 'dark_oak', candle: 'black_candle', candles: 4 },
  { level: 1, room: 'Shop', kind: 'fixture', part: 'chest', pos: CHEST, facing: 'west', block: 'chest' },
  // lamps hung from the tie beams, one over each working corner
  { level: 1, room: 'Shop', kind: 'lamp', part: 'hangingLamp', pos: [3, 3, 3], facing: 'north', wood: 'dark_oak' },
  { level: 1, room: 'Shop', kind: 'lamp', part: 'hangingLamp', pos: [7, 3, 3], facing: 'north', wood: 'dark_oak' },
  { level: 1, room: 'Shop', kind: 'lamp', part: 'hangingLamp', pos: [3, 3, 7], facing: 'north', wood: 'dark_oak' },
  { level: 1, room: 'Shop', kind: 'lamp', part: 'hangingLamp', pos: [7, 3, 7], facing: 'north', wood: 'dark_oak' },
  // Reading Room (II): a reading table for four, books on the east and back walls
  { level: 2, room: 'Reading Room', kind: 'table', part: 'table', pos: [12, 1, 5], facing: 'north', wood: 'dark_oak', colour: 'green' },
  { level: 2, room: 'Reading Room', kind: 'table', part: 'table', pos: [12, 1, 6], facing: 'north', wood: 'dark_oak', colour: 'green' },
  { level: 2, room: 'Reading Room', kind: 'chair', part: 'chair', pos: [11, 1, 5], facing: 'east', wood: 'dark_oak' },
  { level: 2, room: 'Reading Room', kind: 'chair', part: 'chair', pos: [11, 1, 6], facing: 'east', wood: 'dark_oak' },
  { level: 2, room: 'Reading Room', kind: 'chair', part: 'chair', pos: [13, 1, 5], facing: 'west', wood: 'dark_oak' },
  { level: 2, room: 'Reading Room', kind: 'chair', part: 'chair', pos: [13, 1, 6], facing: 'west', wood: 'dark_oak' },
  { level: 2, room: 'Reading Room', kind: 'lamp', part: 'hangingLamp', pos: [12, 3, 4], facing: 'north', chain: 3, wood: 'dark_oak' },
  { level: 2, room: 'Reading Room', kind: 'lamp', part: 'hangingLamp', pos: [12, 3, 7], facing: 'north', chain: 3, wood: 'dark_oak' },
]

// The shelves of books (Level II), [x, z, y0, y1] columns: 2 high under the east wall's high windows and 3 high
// between them, 3 high along the back wall (25 bookshelves; the test wants 24)
export const BOOKS = [
  [14, 4, 1, 3], [14, 5, 1, 2], [14, 6, 1, 3], [14, 7, 1, 2], [14, 8, 1, 3],
  [10, 8, 1, 3], [11, 8, 1, 3], [12, 8, 1, 3], [13, 8, 1, 3],
]

// The walking line: street -> apron step -> door -> past the counter -> the press (worked from (5, 1, 4)); in II on
// through the arch to the reading table.
export const WALK = [
  [1, 0, -3], [5, 0, -1], [5, 0, 0], [5, 1, 1], [5, 1, 2], [5, 1, 3], [5, 1, 4],
  [6, 1, 5], [7, 1, 6], [8, 1, 6], [9, 1, 6], [10, 1, 6], [10, 1, 5],
]
