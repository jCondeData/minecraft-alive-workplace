// Trade Hall, the plan (ROADMAP 49, brief tools/blueprints/briefs/trade_hall.md): drawn before any wall.
//
// Three concepts:
//   A. The Exchange Loggia: a long open arcade under one big hipped roof, the counter at the back of a covered trading
//      floor, the Trader's room in a mezzanine. Story: a market roof trainers meet under. (Open, but reads as a shed.)
//   B. The Ball Tower: a white-walled counting hall in a dark-oak frame standing a block proud of its panels, a
//      45-degree slate roof with a gabled ledger bay pushed out of its back slope, and a round tower at the front
//      corner whose dark band carries one white boss: the ball's button, the sign of the trade. The counter faces the
//      door; the Trader sleeps up in the tower. Story: the trading post you find by its tower.
//   C. The Counting House: a tall narrow gable to the street with a hoist beam and a bay shopfront, living rooms over
//      the shop. Story: a merchant's townhouse. (Handsome, but any shop could be it.)
// Strongest: B. It is the only one you'd know in outline from across a village as the place Pokemon change hands:
// the tower is the low hall's vertical hero, and the band and button turn the Trader's bedroom into the sign.
//
// Round 3 (the critic's notes, halls_51_5_critic.md): (1) the whole frame stands one block proud of the white panels
// on all four sides, with sills under every window and a one-block eave or rake past the frame all round; (2) a gabled
// ledger bay breaks the long back roof (a cross-gable, the desk in its alcove); (3) the tower's top is the walls' and
// roof's palette: white panels between dark-oak posts under a stepped slate cap, the only dark ring the band with its
// boss; (4) the tower stands on a stone plinth and a battered foot all round.
//
// Coordinates: x across the front (west 0 -> east 17), y up (0 = on the clicked block), z from the front (0) back.
// Masses (named parts):
//   Hall    white panels x 2..12, z 3..13 (room x 3..11, z 4..12), walls y 1..4; the frame one out (x 1 and 13,
//           z 2 and 14) with posts and a beam ring at y 5; a 45-degree slate roof, ridge along x at y 12 (z 8), eaves
//           at z 1 and 15, rakes at x 0 and 14 with barge boards. Ties across at x 5 and 9.
//   Bay     a gabled ledger bay out of the back: panels x 5..9, z 13..16, frame one out (x 4, 10, z 17), its roof ridge
//           along z at x 7 (y 9) running out of the main slope, its rake at z 18.
//   Tower   a chamfered shaft x 12..16, z 1..5, y 1..12, on a stone foot and a battered stair skirt all round; a
//           corbelled dark band at y 6 with the white boss facing the street; white panels between dark-oak posts
//           above; a stepped slate cap x 11..17, z 0..6 from y 13 with a lightning-rod finial.
//   Chimney a stone stack up the east gable (x 13..14, z 9..10), out past the rake.

export const SIZE = [18, 18, 19] // w, h, d (plot limit 18 x 28 x 20)

// Palette 60/30/10: white terracotta panels on a stone-brick plinth (60) / dark oak frame and deepslate-tile roof (30) /
// the tower's polished-deepslate band with its quartz boss and the red door banners (10). Why: a clean pale hall reads
// as public and trustworthy (a counter, not a home); the dark slate and proud frame give every face shadow; the one
// accent the eye finds from afar is the band and its white button.
export const PALETTE = {
  stone: [['stone_bricks', 6], ['cracked_stone_bricks', 1], ['cobblestone', 2], ['mossy_stone_bricks', 1]],
  stoneStairs: 'stone_brick_stairs',
  wall: 'white_terracotta',
  frame: 'dark_oak_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  band: 'polished_deepslate',
  bandStairs: 'polished_deepslate_stairs',
  floor: 'spruce_planks',
  towerFloor: 'polished_andesite',
  wood: 'spruce',
  trim: 'dark_oak',
  glass: 'glass_pane',
  button: 'chiseled_quartz_block',
  banner: 'red',
  counterTop: 'dark_oak_slab',
}

export const MASSES = {
  hall: {
    panels: { x0: 2, x1: 12, z0: 3, z1: 13 },
    room: { x0: 3, x1: 11, z0: 4, z1: 12 },
    frame: { x0: 1, x1: 13, z0: 2, z1: 14 }, // the proud plane
    wallTop: 4, beamY: 5,
    frontPosts: [1, 5, 7, 11], backPosts: [1, 4, 10, 13], westPosts: [2, 6, 10, 14], eastPosts: [6, 14],
    ties: [5, 9],
    roof: { axis: 'x', across: [1, 15], along: [0, 14], eaveY: 5, steep: false },
    gablePosts: [5, 11], collarY: 9, kingZ: 8,
  },
  bay: {
    panels: { x0: 5, x1: 9, z0: 13, z1: 16 }, room: { x0: 6, x1: 8, z0: 14, z1: 15 },
    frame: { x0: 4, x1: 10, z1: 17 },
    roof: { axis: 'z', across: [3, 11], along: [11, 18], eaveY: 5, steep: false },
  },
  tower: { x0: 12, z0: 1, x1: 16, z1: 5, top: 12, bandY: 6, loftY: 6, room: { x0: 13, x1: 15, z0: 2, z1: 4 },
    button: [14, 6, 0], cap: [11, 0, 17, 6, 13], postsFrom: 7 },
  chimney: { x: 13, z: 9, top: 13 },
}

export const ROOMS = [
  { name: 'Trading floor', use: 'customers come in, wait and trade at the counter', box: [3, 1, 4, 11, 4, 7] },
  { name: 'Back counter', use: 'the Trader behind his counter, his ledgers, the clock and the ledger bay', box: [3, 1, 9, 11, 4, 15] },
  { name: 'Tower room', use: 'the Trader sleeps up the tower, up a ladder from the trading floor', box: [13, 7, 2, 15, 12, 4] },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 3], facing: 'south', step: [6, 0, 2] },
  towerDoorway: { at: [12, 1, 4], height: 2 },
  bayArch: { x0: 6, x1: 8, z: 13, y1: 3 },
  ladder: { x: 15, z: 2, y0: 1, y1: 6, facing: 'west' }, // up through the tower loft's floor
  windows: [
    { name: 'shop west', cells: [[2, 2, 3], [3, 2, 3], [4, 2, 3], [2, 3, 3], [3, 3, 3], [4, 3, 3]], out: 'north', dress: 'sill' },
    { name: 'shop east', cells: [[8, 2, 3], [9, 2, 3], [10, 2, 3], [8, 3, 3], [9, 3, 3], [10, 3, 3]], out: 'north', dress: 'sill' },
    { name: 'transom', cells: [[6, 3, 3]], out: 'north', dress: null },
    { name: 'west 1', cells: [[2, 2, 4], [2, 3, 4]], out: 'west', dress: 'sill' },
    { name: 'west 2', cells: [[2, 2, 8], [2, 3, 8]], out: 'west', dress: 'sill' },
    { name: 'west 3', cells: [[2, 2, 12], [2, 3, 12]], out: 'west', dress: 'sill' },
    { name: 'east 1', cells: [[12, 2, 7], [12, 3, 7]], out: 'east', dress: 'sill' },
    { name: 'east 2', cells: [[12, 2, 12], [12, 3, 12]], out: 'east', dress: 'sill' },
    { name: 'back west', cells: [[2, 2, 13], [3, 2, 13], [2, 3, 13], [3, 3, 13]], out: 'south', dress: 'sill' },
    { name: 'back east', cells: [[11, 2, 13], [12, 2, 13], [11, 3, 13], [12, 3, 13]], out: 'south', dress: 'sill' },
    { name: 'bay back', cells: [[6, 2, 16], [7, 2, 16], [8, 2, 16], [6, 3, 16], [7, 3, 16], [8, 3, 16]], out: 'south', dress: 'sill' },
    { name: 'bay west', cells: [[5, 2, 15], [5, 3, 15]], out: 'west', dress: 'sill' },
    { name: 'bay east', cells: [[9, 2, 15], [9, 3, 15]], out: 'east', dress: 'sill' },
    { name: 'bay gable', cells: [[7, 6, 16], [7, 7, 16]], out: 'south', dress: null },
    { name: 'west gable', cells: [[2, 6, 7], [2, 7, 7], [2, 6, 9], [2, 7, 9]], out: 'west', dress: null },
    { name: 'east gable', cells: [[12, 6, 7], [12, 7, 7]], out: 'east', dress: null },
    { name: 'tower front low', cells: [[14, 2, 1], [14, 3, 1]], out: 'north', dress: 'sill' },
    { name: 'tower east low', cells: [[16, 2, 3], [16, 3, 3]], out: 'east', dress: 'sill' },
    { name: 'tower front high', cells: [[14, 8, 1], [14, 9, 1]], out: 'north', dress: 'sill' },
    { name: 'tower east high', cells: [[16, 8, 3], [16, 9, 3]], out: 'east', dress: 'sill' },
  ],
}

// The counter: the Shop Counter at (6, 1, 8) facing the door, dark top slabs either side make it one long bar.
export const JOB = { block: 'aliveworkplace:shop_counter', at: [6, 1, 8], facing: 'north', bar: [4, 5, 7, 8] }

export const FURNITURE = [
  // Trading floor: a waiting table by each shop window, a lamp on a side table by the tower, a rug before the counter
  { room: 'Trading floor', kind: 'table', part: 'table', pos: [3, 1, 5], facing: 'north', wood: 'spruce', colour: 'red' },
  { room: 'Trading floor', kind: 'chair', part: 'chair', pos: [3, 1, 6], facing: 'north', wood: 'spruce' },
  { room: 'Trading floor', kind: 'chair', part: 'chair', pos: [4, 1, 5], facing: 'west', wood: 'spruce' },
  { room: 'Trading floor', kind: 'table', part: 'table', pos: [9, 1, 5], facing: 'north', wood: 'spruce', colour: 'white' },
  { room: 'Trading floor', kind: 'chair', part: 'chair', pos: [10, 1, 5], facing: 'west', wood: 'spruce' },
  { room: 'Trading floor', kind: 'chair', part: 'chair', pos: [9, 1, 6], facing: 'north', wood: 'spruce' },
  { room: 'Trading floor', kind: 'lamp', part: 'tableLamp', pos: [11, 1, 6], facing: 'west', wood: 'spruce' },
  { room: 'Trading floor', kind: 'rug', part: 'rug', cellsAbs: [[5, 1, 5], [6, 1, 5], [7, 1, 5], [5, 1, 6], [6, 1, 6], [7, 1, 6]], colour: 'red' },
  { room: 'Trading floor', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 5], facing: 'north', wood: 'spruce' },
  // Back counter: ledgers and a clock on the back wall, the desk in the ledger bay, a lamp over the bar
  { room: 'Back counter', kind: 'cupboard', part: 'cupboard', pos: [3, 1, 11], facing: 'east', wood: 'spruce' },
  { room: 'Back counter', kind: 'cupboard', part: 'cupboard', pos: [3, 1, 10], facing: 'east', wood: 'spruce' },
  { room: 'Back counter', kind: 'clock', part: 'clock', pos: [11, 1, 11], facing: 'west', wood: 'spruce' },
  { room: 'Back counter', kind: 'desk', part: 'desk', pos: [7, 1, 15], facing: 'south', wood: 'spruce' },
  { room: 'Back counter', kind: 'chair', part: 'chair', pos: [7, 1, 14], facing: 'south', wood: 'spruce' },
  { room: 'Back counter', kind: 'picture', part: 'picture', pos: [4, 3, 12], facing: 'north', wood: 'spruce', colour: 'white' },
  { room: 'Back counter', kind: 'picture', part: 'picture', pos: [10, 3, 12], facing: 'north', wood: 'spruce', colour: 'red' },
  { room: 'Back counter', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 10], facing: 'north', wood: 'spruce' },
  // the ledger shelves and the bay's desk each get a lantern of their own, from the tie at x 5 and the bay's tie
  { room: 'Back counter', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 11], facing: 'north', wood: 'spruce' },
  { room: 'Back counter', kind: 'lamp', part: 'hangingLamp', pos: [7, 4, 14], facing: 'north', wood: 'spruce' },
  // Tower room: the Trader's bed with its head to the front wall (FurnishedRoomsGameTests.everyBedHasItsHeadToAWall), a
  // nightstand beside its head, a lantern hung from the cap, his chest, a pennant and a rug
  { room: 'Tower room', kind: 'bed', part: 'bed', pos: [13, 7, 3], facing: 'north', colour: 'red' },
  { room: 'Tower room', kind: 'nightstand', part: 'nightstand', pos: [14, 7, 2], facing: 'south', wood: 'spruce' },
  { room: 'Tower room', kind: 'lamp', part: 'hangingLamp', pos: [14, 10, 3], facing: 'north', chain: 2, wood: 'spruce' }, // on a chain from the cap, 3 over the floor
  { room: 'Tower room', kind: 'chest', part: 'chest', pos: [15, 7, 4], facing: 'west', wood: 'spruce' },
  { room: 'Tower room', kind: 'picture', part: 'picture', pos: [14, 9, 4], facing: 'north', wood: 'spruce', colour: 'red' },
  { room: 'Tower room', kind: 'rug', part: 'rug', cellsAbs: [[14, 7, 3], [14, 7, 4]], colour: 'white' },
]

// Walking line: the Trader waits at (6, 0, -3), steps up at (6, 0, 2), in the door, over the rug to the counter's
// front (6, 1, 7); round the bar's east end (9, 1, 8) to stand behind it (6, 1, 9); into the ledger bay (7, 1, 13);
// back east to the tower doorway (12, 1, 4) and up the ladder (15, 1..6, 2) to the bed room (15, 7, 2).
export const WALK = [
  [6, 0, 1], [6, 0, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [6, 1, 7], [7, 1, 7], [8, 1, 7], [9, 1, 7], [9, 1, 8],
  [9, 1, 9], [6, 1, 9], [7, 1, 10], [7, 1, 13], [10, 1, 7], [11, 1, 4], [12, 1, 4], [13, 1, 3], [14, 1, 2], [15, 1, 2],
  [15, 7, 2], [15, 7, 3], [14, 7, 3],
]

export const OUTSIDE = {
  flowerBeds: [{ x0: 2, x1: 4, z: 1, plants: ['poppy', 'oxeye_daisy', 'poppy'] }, { x0: 8, x1: 10, z: 1, plants: ['oxeye_daisy', 'poppy', 'oxeye_daisy'] }],
  path: [[5, 0, 0], [6, 0, 0], [7, 0, 0], [6, 0, 1]],
}
