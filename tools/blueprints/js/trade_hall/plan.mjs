// Trade Hall, the plan (ROADMAP 49, brief tools/blueprints/briefs/trade_hall.md): drawn before any wall.
//
// Three concepts:
//   A. The Exchange Loggia: a long open arcade under one big hipped roof, the counter at the back of a covered trading
//      floor, the Trader's room in a mezzanine. Story: a market roof trainers meet under. (Open, but reads as a shed.)
//   B. The Ball Tower: a white-walled, dark-framed counting hall (45-degree slate roof, ridge across the front) with a
//      round tower at its front corner capped by a red-and-white ball, black band and white eye. The counter faces
//      the door; the Trader sleeps in the tower under the ball. Story: the trading post you find by its beacon.
//   C. The Counting House: a tall narrow gable to the street with a hoist beam and a bay shopfront, living rooms over
//      the shop. Story: a merchant's townhouse. (Handsome, but any shop could be it.)
// Strongest: B. It is the only one you'd recognise in outline from across a village as the place Pokemon change hands,
// the tower gives the low hall a vertical hero, and the ball turns a function (the Trader's bedroom) into the sign.
//
// Coordinates: x across the front (west 0 -> east 15), y up (0 = on the clicked block), z from the front (0) back.
// Masses (named parts):
//   Hall    frame x 1..10, z 2..12, walls y 1..4, beam ring y 5, a 45-degree slate roof (ridge along x, y 11) with
//           eaves over the front and back (z 1, 13) and rakes past the gables (x 0, 11). Tie beams at x 4 and 7.
//   Tower   a chamfered shaft x 10..14, z 1..5 (a block proud of the hall front), y 1..9, slate bands at y 5 and 9.
//   Ball    radius 3.1 at (12, 13, 3): white below, black band at y 13 with a white eye facing the street, red above.
//   Porch   the front eave over the door and two shop windows with sills, red banners either side of the door.

export const SIZE = [16, 18, 16] // w, h, d (plot limit 18 x 28 x 20)

// Palette 60/30/10: white terracotta walls on a stone-brick plinth (60) / dark oak frame and deepslate-tile roof (30) /
// the ball's red and white concrete, its black eye band and the red door banners (10). Why: a clean pale hall reads
// as public and trustworthy (a counter, not a home); the dark slate and frame keep it from looking plastic; the only
// saturated colour in the whole building is the ball, so the eye goes straight to it.
export const PALETTE = {
  stone: [['stone_bricks', 6], ['cracked_stone_bricks', 1], ['cobblestone', 2], ['mossy_stone_bricks', 1]],
  wall: 'white_terracotta',
  frame: 'dark_oak_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  band: 'polished_deepslate',
  floor: 'spruce_planks',
  towerFloor: 'polished_andesite',
  wood: 'spruce',
  trim: 'dark_oak',
  glass: 'glass_pane',
  ball: { red: 'red_terracotta', button: 'chiseled_quartz_block' },
  banner: 'red',
  counterTop: 'dark_oak_slab',
}

export const MASSES = {
  hall: {
    frame: { x0: 1, x1: 10, z0: 2, z1: 12 },
    room: { x0: 2, x1: 9, z0: 3, z1: 11 },
    wallTop: 4, beamY: 5,
    frontPosts: [1, 4, 6, 10], backPosts: [1, 4, 7, 10], westPosts: [2, 7, 12], eastPosts: [7, 12],
    ties: [4, 7],
    roof: { axis: 'x', across: [0, 14], along: [0, 11], eaveY: 5, steep: false }, // eaves two deep
  },
  tower: { x0: 10, z0: 1, x1: 14, z1: 5, top: 9, bands: [5, 9], room: { x0: 11, x1: 13, z0: 2, z1: 4 }, loftY: 6 },
  ball: { red: [6, 9], band: 5, button: [12, 5, 1], cap: [9, 0, 15, 6, 10] }, // the ball is the tower: white foot, black band and button, red top
  chimney: { x: 7, z: 13, top: 10 },
}

export const ROOMS = [
  { name: 'Trading floor', use: 'customers come in, wait and trade at the counter', box: [2, 1, 3, 9, 4, 6] },
  { name: 'Back counter', use: 'the Trader behind his counter, his ledgers and the clock', box: [2, 1, 8, 9, 4, 11] },
  { name: 'Tower room', use: 'the Trader sleeps under the ball, up a ladder from the trading floor', box: [11, 7, 2, 13, 9, 4] },
]

export const OPENINGS = {
  frontDoor: { at: [5, 1, 2], facing: 'south', step: [5, 0, 1] },
  towerDoorway: { at: [10, 1, 4], height: 2 },
  ladder: { x: 13, z: 2, y0: 1, y1: 6, facing: 'west' }, // up through the tower loft's floor
  windows: [
    { name: 'shop west', cells: [[2, 2, 2], [3, 2, 2], [2, 3, 2], [3, 3, 2]], out: 'north', dress: 'sill' },
    { name: 'shop east', cells: [[7, 2, 2], [8, 2, 2], [7, 3, 2], [8, 3, 2]], out: 'north', dress: 'sill' },
    { name: 'transom', cells: [[5, 3, 2]], out: 'north', dress: null },
    { name: 'west front', cells: [[1, 2, 5], [1, 3, 5]], out: 'west', dress: 'sill' },
    { name: 'west back', cells: [[1, 2, 9], [1, 3, 9]], out: 'west', dress: 'sill' },
    { name: 'east back', cells: [[10, 2, 9], [10, 3, 9]], out: 'east', dress: 'sill' },
    { name: 'back', cells: [[5, 2, 12], [5, 3, 12]], out: 'south', dress: 'sill' },
    { name: 'back west', cells: [[3, 2, 12], [3, 3, 12]], out: 'south', dress: 'sill' },
    { name: 'back east', cells: [[9, 2, 12], [9, 3, 12]], out: 'south', dress: 'sill' },
    { name: 'west gable', cells: [[1, 7, 7], [1, 8, 7]], out: 'west', dress: null },
    { name: 'east gable', cells: [[10, 7, 8], [10, 8, 8]], out: 'east', dress: null },
    { name: 'tower front low', cells: [[12, 2, 1], [12, 3, 1]], out: 'north', dress: 'sill' },
    { name: 'tower front high', cells: [[12, 7, 1], [12, 8, 1]], out: 'north', dress: null },
    { name: 'tower east', cells: [[14, 8, 3]], out: 'east', dress: null },
    { name: 'tower back', cells: [[12, 8, 5]], out: 'south', dress: null },
    { name: 'tower east low', cells: [[14, 2, 3], [14, 3, 3]], out: 'east', dress: 'sill' },
  ],
}

// The counter: the Shop Counter itself at (5, 1, 7) facing the door, dark top slabs either side make it one long bar.
export const JOB = { block: 'aliveworkplace:shop_counter', at: [5, 1, 7], facing: 'north', bar: [3, 4, 6, 7] }

export const FURNITURE = [
  // Trading floor: a waiting table by the west window, a lamp on a side table by the tower, a rug before the counter
  { room: 'Trading floor', kind: 'table', part: 'table', pos: [3, 1, 4], facing: 'north', wood: 'spruce', colour: 'red' },
  { room: 'Trading floor', kind: 'chair', part: 'chair', pos: [2, 1, 4], facing: 'east', wood: 'spruce' },
  { room: 'Trading floor', kind: 'chair', part: 'chair', pos: [3, 1, 5], facing: 'north', wood: 'spruce' },
  { room: 'Trading floor', kind: 'table', part: 'table', pos: [7, 1, 4], facing: 'north', wood: 'spruce', colour: 'white' },
  { room: 'Trading floor', kind: 'chair', part: 'chair', pos: [8, 1, 4], facing: 'west', wood: 'spruce' },
  { room: 'Trading floor', kind: 'lamp', part: 'tableLamp', pos: [9, 1, 3], facing: 'west', wood: 'spruce' },
  { room: 'Trading floor', kind: 'rug', part: 'rug', cellsAbs: [[5, 1, 5], [6, 1, 5], [5, 1, 6], [6, 1, 6]], colour: 'red' },
  { room: 'Trading floor', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 5], facing: 'north', wood: 'spruce' },
  // Back counter: ledgers and a clock on the back wall, a desk with its chair, a lamp over the bar
  { room: 'Back counter', kind: 'cupboard', part: 'cupboard', pos: [2, 1, 11], facing: 'north', wood: 'spruce' },
  { room: 'Back counter', kind: 'cupboard', part: 'cupboard', pos: [3, 1, 11], facing: 'north', wood: 'spruce' },
  { room: 'Back counter', kind: 'clock', part: 'clock', pos: [6, 1, 11], facing: 'north', wood: 'spruce' },
  { room: 'Back counter', kind: 'desk', part: 'desk', pos: [9, 1, 10], facing: 'east', wood: 'spruce' },
  { room: 'Back counter', kind: 'chair', part: 'chair', pos: [8, 1, 10], facing: 'east', wood: 'spruce' },
  { room: 'Back counter', kind: 'picture', part: 'picture', pos: [4, 3, 11], facing: 'north', wood: 'spruce', colour: 'white' },
  { room: 'Back counter', kind: 'picture', part: 'picture', pos: [8, 3, 11], facing: 'north', wood: 'spruce', colour: 'red' },
  { room: 'Back counter', kind: 'lamp', part: 'hangingLamp', pos: [7, 4, 8], facing: 'north', wood: 'spruce' },
  // Tower room: the Trader's bed under the ball, a nightstand, a lantern hung from the ball's floor
  { room: 'Tower room', kind: 'bed', part: 'bed', pos: [11, 7, 3], facing: 'north', colour: 'red' },
  { room: 'Tower room', kind: 'nightstand', part: 'nightstand', pos: [12, 7, 2], facing: 'south', wood: 'spruce' },
  { room: 'Tower room', kind: 'lamp', part: 'hangingLamp', pos: [12, 9, 4], facing: 'north', wood: 'spruce' },
]

// Walking line: the Trader waits at (6, 0, -3), steps up at (5, 0, 1), in the door, over the rug to the counter's
// front (5, 1, 6); round the bar's east end (8, 1, 7) to stand behind it; east to the tower doorway and up the ladder.
export const WALK = [
  [5, 0, 0], [5, 0, 1], [5, 1, 2], [5, 1, 3], [5, 1, 4], [5, 1, 5], [5, 1, 6], [6, 1, 6], [7, 1, 6], [8, 1, 6], [8, 1, 7],
  [8, 1, 8], [5, 1, 8], [8, 1, 5], [9, 1, 4], [10, 1, 4], [11, 1, 4], [12, 1, 3], [13, 1, 2], [13, 7, 2], [13, 7, 3],
]

export const OUTSIDE = {
  flowerBeds: [{ x0: 2, x1: 3, z: 0, plants: ['poppy', 'oxeye_daisy'] }, { x0: 7, x1: 8, z: 0, plants: ['oxeye_daisy', 'poppy'] }],
  path: [[5, 0, 0], [6, 0, 0], [4, 0, 0]],
}
