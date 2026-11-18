// The School (the Move Tutor's, Cobblemon), the plan: drawn before any wall. Rooms, every furniture footprint and the
// walking line come first; recipe.mjs then assembles it from the parts library (../parts) in passes.
//
// Round 3 (back to the designer, lane-c-1118-0654): round 2's court passed the critic but failed in game. It was
// 1,410 blocks (the brief's build time is 12,000 ticks; the old School is 759) and the builder stood 2,800 ticks under
// the gatehouse's back barge stair, boxed in over the west cloister's lean-to. Added to the brief for this round: at
// most about 900 placed blocks, and every block reachable by a builder from a standing spot or a column of open air
// (no overhang, barge or eave over another roof). Three rough concepts again, all keeping the school's character (a
// gate, a sky-lit sparring court round the Training Post, the Tutor's hall):
//   1. "The Half Court": the gatehouse stays the hero (a moon window, the steepest, highest roof), but only ONE cloister
//      (west, spectators' benches) and on the east a low stone yard wall with the straw targets against it. The hall
//      across the back keeps its lecture room and quarters under one broad roof, its eaves a storey lower. Story: the
//      Tutor built the side where people watch; the other side is a practice wall. Built: 903 blocks (round 2: 1,410).
//   2. "The Gate Hall": the hall itself is the gatehouse (the road walks through the hall's middle into a court
//      behind it). Fewest blocks, but the post ends up behind the building, out of sight from the road.
//   3. "The Ring Pavilion": an open pavilion roof on posts over the ring, the hall beside it. A pavilion over the ring
//      is a second roof over a big span: as many blocks as round 2, and the court loses its sky.
// Strongest: 1. It keeps everything round 2 won (the gate framing the ring on the road's axis, a cloister to watch
// from, the hall closing the back) and pays for the budget with the least-seen part, the second cloister, turning it
// into the yard wall the targets need anyway. The asymmetry (a tall cloister west, a low wall east) gives the front an
// L-shaped silhouette instead of a mirror, and every roof now stands apart from the others: the gate roof stops at
// its own back gable over open ground, the cloister's lean-to ends against the hall's wall (not under its eave), and
// the hall's eave over the court sits above open paving.
//
// Coordinates: x across the front (west 0 -> east 16), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The way in is the gate passage on the north side, three wide, a stair step before it.
//
// Reach (every block a builder can stand by or scaffold to): the gate roof's back barge (z 4) hangs over the court's
// threshold and the two flower-bed strips, open to the ground; the cloister lean-to's eave (x 0) over open ground;
// the hall's barges (x 0, x 16) over open ground and its front eave (z 11) over the court's paving; the chimney stands
// outside the back wall on the ground.
//
// Masses (named parts):
//   Gate      the hero: stone piers x 5..6 and 10..11 (z 1..3), a passage x 7..9 three high under a dark lintel, a
//             timber storey on a beam ring, a 45-degree tiled roof (ridge along z, y 11) overhanging the front and the
//             back (z 0 and z 4) with barge boards; a moon window in each gable.
//   Court     open paving x 5..11, z 4..11, the sparring ring (r 2) inlaid round the Training Post (8, 1, 8).
//   Cloister  west, x 1..4, z 5..11: a framed white outer wall with shuttered windows, a colonnade of three posts
//             (x 4) open to the court, a lean-to roof from the outer wall (y 7) down to the colonnade (y 4).
//   Yard wall east, x 12..15, z 4..11: a two-high stone wall with a slab coping, lantern piers at its corners, the
//             straw targets against it on a paved strip.
//   Hall      the Tutor's Hall across the back: walls x 1..15, z 12..16 (posts in bays of 3 and 2), a 45-degree roof
//             (ridge along x, y 7) overhanging every side, barge boards on both gables; a lecture room (west) and the
//             Tutor's quarters (east) behind a bookshelf screen; a stone chimney up the back from the lecture hearth.

export const SIZE = [17, 12, 18] // w, h, d (limit 18 x 18 x 20)

// Palette (60/30/10): white plaster (walls, gables) / dark oak frame and red clay-tile (brick) roofs / stone-brick plinth,
// piers and yard wall, the ring's packed earth, red cloth. Why: a training hall should read clean and disciplined from
// the road (white panels ruled by dark posts under warm tiles), the stone gate gives the hero mass weight, and the warm
// earth ring and the red banners and bed are the only colour, all of it where the lessons happen.
export const PALETTE = {
  stone: [['stone_bricks', 7], ['cracked_stone_bricks', 1], ['andesite', 1], ['mossy_stone_bricks', 1]],
  frame: 'dark_oak_log',
  infill: 'white_concrete',
  roof: { full: 'bricks', stairs: 'brick_stairs', slab: 'brick_slab' },
  floor: 'spruce_planks',
  ceiling: 'dark_oak_planks',
  paving: [['stone_bricks', 5], ['polished_andesite', 2], ['cracked_stone_bricks', 1]],
  ring: { inner: 'coarse_dirt', border: 'polished_andesite' },
  coping: 'stone_brick_slab',
  wood: 'dark_oak', // doors, trapdoors, fences, stairs as seats
  furniture: 'spruce', // tables, chairs, shelves
  glass: 'glass_pane',
  accent: 'red',
}

export const MASSES = {
  gate: {
    piers: [[5, 6], [10, 11]], z0: 1, z1: 3, passage: [7, 9], pierTop: 4,
    roof: { axis: 'z', across: [4, 12], along: [0, 4], eaveY: 7, steep: false },
    moon: [8, 8],
  },
  court: { x0: 5, x1: 11, z0: 4, z1: 11, ring: { cx: 8, cz: 8, r: 2, z0: 5 } },
  cloister: {
    wall: 1, colonnade: 4, walk: [2, 3], z0: 5, z1: 11, posts: [5, 8, 11], wallPosts: [5, 8, 11],
    roof: { axis: 'z', across: [0, 4], ridge: 1, along: [5, 11], ridgeY: 7 },
  },
  yardWall: { x: 15, z0: 4, z1: 11, front: [12, 15], top: 1, piers: [[12, 4], [15, 4]] },
  hall: {
    walls: { x0: 1, x1: 15, z0: 12, z1: 16 }, room: { x0: 2, x1: 14, z0: 13, z1: 15 },
    wallTop: 3, beamY: 4,
    frontPosts: [1, 4, 7, 9, 12, 15], backPosts: [1, 4, 7, 9, 12, 15], sidePosts: [12, 16],
    roof: { axis: 'x', across: [11, 17], along: [0, 16], eaveY: 4, steep: false },
    gableStuds: [13, 15],
    screen: { x: 12, z0: 13, z1: 15, doorway: 13 },
    hearth: [8, 1, 15], chimney: { x: 8, z: 17 },
  },
}

export const ROOMS = [
  { name: 'Court', use: 'lessons: the Training Post in the sparring ring, open to the sky', box: [5, 1, 4, 11, 4, 11] },
  { name: 'Cloister', use: "spectators' bench facing the ring", box: [2, 1, 6, 3, 3, 11] },
  { name: 'Practice yard', use: 'straw targets against the yard wall', box: [12, 1, 5, 14, 3, 11] },
  { name: 'Lecture room', use: 'a reading table, a wall of move books, the Tutor\'s desk by the hearth', box: [2, 1, 13, 11, 4, 15] },
  { name: 'Quarters', use: "the Tutor's bed, nightstand and chest", box: [13, 1, 13, 14, 4, 15] },
]

export const JOB = { block: 'aliveworkplace:training_post', at: [8, 1, 8], facing: 'north' }

export const OPENINGS = {
  gateway: { x0: 7, x1: 9, z0: 1, z1: 3, y0: 1, y1: 3, steps: [[7, 0, 0], [8, 0, 0], [9, 0, 0]] },
  hallDoor: { at: [8, 1, 12], facing: 'south', out: 'north' },
  screenDoorway: { at: [12, 1, 13], height: 2 },
  windows: [
    { name: 'cloister west north', cells: [[1, 2, 6], [1, 3, 6], [1, 2, 7], [1, 3, 7]], out: 'west', dress: 'shutters' },
    { name: 'cloister west south', cells: [[1, 2, 9], [1, 3, 9], [1, 2, 10], [1, 3, 10]], out: 'west', dress: 'shutters' },
    { name: 'cloister front', cells: [[2, 2, 5], [3, 2, 5], [2, 3, 5], [3, 3, 5]], out: 'north', dress: null },
    { name: 'hall court west', cells: [[5, 2, 12], [6, 2, 12], [5, 3, 12], [6, 3, 12]], out: 'north', dress: 'sill', pots: true },
    { name: 'hall court east', cells: [[10, 2, 12], [11, 2, 12], [10, 3, 12], [11, 3, 12]], out: 'north', dress: 'sill', pots: true },
    { name: 'hall yard', cells: [[13, 2, 12], [14, 2, 12], [13, 3, 12], [14, 3, 12]], out: 'north', dress: 'sill' },
    { name: 'hall back 1', cells: [[2, 2, 16], [3, 2, 16], [2, 3, 16], [3, 3, 16]], out: 'south', dress: 'sill' },
    { name: 'hall back 2', cells: [[5, 2, 16], [6, 2, 16], [5, 3, 16], [6, 3, 16]], out: 'south', dress: 'sill' },
    { name: 'hall back 3', cells: [[10, 2, 16], [11, 2, 16], [10, 3, 16], [11, 3, 16]], out: 'south', dress: 'sill' },
    { name: 'hall back 4', cells: [[13, 2, 16], [14, 2, 16], [13, 3, 16], [14, 3, 16]], out: 'south', dress: 'sill' },
    { name: 'hall west', cells: [[1, 2, 14], [1, 3, 14]], out: 'west', dress: 'sill' },
    { name: 'hall east', cells: [[15, 2, 14], [15, 3, 14]], out: 'east', dress: 'sill' },
    { name: 'hall west gable', cells: [[1, 6, 14]], out: 'west', dress: null },
    { name: 'hall east gable', cells: [[15, 6, 14]], out: 'east', dress: null },
  ],
}

// Every piece of furniture, footprint first (kind: furniture.py's; part: furnitureParts' or the school parts').
const W = 'spruce'
export const FURNITURE = [
  // Cloister: a long bench facing the ring, lanterns hung from the lean-to
  { room: 'Cloister', kind: 'couch', part: 'couch', pos: [2, 1, 6], facing: 'east', length: 3, wood: 'dark_oak', colour: 'red' },
  { room: 'Cloister', kind: 'lamp', part: 'hangingLamp', pos: [2, 4, 7], facing: 'east', chain: 1, wood: W },
  { room: 'Cloister', kind: 'lamp', part: 'hangingLamp', pos: [2, 4, 10], facing: 'east', chain: 1, wood: W },
  // Practice yard: straw targets against the yard wall
  { room: 'Practice yard', kind: 'fixture', part: 'target', pos: [14, 1, 6], facing: 'west' },
  { room: 'Practice yard', kind: 'fixture', part: 'target', pos: [14, 1, 9], facing: 'west' },
  // Lecture room: the wall of move books on the west gable, a reading table for four, the Tutor's desk by the hearth
  ...[13, 15].map(z => ({ room: 'Lecture room', kind: 'cupboard', part: 'cupboard', pos: [2, 1, z], facing: 'east', cells: [[0, 0, 0], [0, 1, 0]], wood: W })),
  ...[5, 6].map(x => ({ room: 'Lecture room', kind: 'table', part: 'table', pos: [x, 1, 14], facing: 'north', cells: [[0, 0, 0], [0, 1, 0]], wood: W, colour: 'white' })),
  { room: 'Lecture room', kind: 'chair', part: 'chair', pos: [5, 1, 13], facing: 'south', wood: W },
  { room: 'Lecture room', kind: 'chair', part: 'chair', pos: [6, 1, 13], facing: 'south', wood: W },
  { room: 'Lecture room', kind: 'chair', part: 'chair', pos: [5, 1, 15], facing: 'north', wood: W },
  { room: 'Lecture room', kind: 'chair', part: 'chair', pos: [6, 1, 15], facing: 'north', wood: W },
  { room: 'Lecture room', kind: 'desk', part: 'desk', pos: [10, 1, 15], facing: 'south', wood: W },
  { room: 'Lecture room', kind: 'chair', part: 'chair', pos: [10, 1, 14], facing: 'south', wood: W },
  { room: 'Lecture room', kind: 'fixture', part: 'hearth', pos: [8, 1, 15], facing: 'north' },
  { room: 'Lecture room', kind: 'picture', part: 'picture', pos: [8, 3, 15], facing: 'north', wood: W, colour: 'red' },
  { room: 'Lecture room', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 14], facing: 'north', chain: 2, wood: W },
  { room: 'Lecture room', kind: 'lamp', part: 'hangingLamp', pos: [10, 4, 13], facing: 'north', chain: 1, wood: W },
  // Quarters: the Tutor's bed against the back wall, a nightstand, his chest, a rug
  { room: 'Quarters', kind: 'bed', part: 'bed', pos: [13, 1, 14], facing: 'south', colour: 'red' },
  { room: 'Quarters', kind: 'nightstand', part: 'nightstand', pos: [14, 1, 15], facing: 'north', wood: W },
  { room: 'Quarters', kind: 'fixture', part: 'chest', pos: [14, 1, 13], facing: 'west', block: 'chest' },
  { room: 'Quarters', kind: 'rug', part: 'rug', cellsAbs: [[14, 1, 14]], colour: 'red' },
  { room: 'Quarters', kind: 'lamp', part: 'hangingLamp', pos: [13, 4, 13], facing: 'north', chain: 1, wood: W },
]

// The walking line: the road -> the step -> the gate passage -> round the ring to the post (stand at (8, 1, 6), the
// player anywhere in the court) -> the hall door -> the lecture room -> the screen doorway -> the bed.
export const WALK = [
  [8, 0, -3], [8, 0, 0], [8, 1, 1], [8, 1, 4], [8, 1, 6], [6, 1, 8], [8, 1, 10], [8, 1, 12], [8, 1, 13], [9, 1, 13],
  [11, 1, 13], [12, 1, 13], [13, 1, 13],
]

// Outside the rooms: a flower bed before the cloister's front window and one before the yard wall, banners on the
// gate piers, lanterns on the yard-wall piers and the cloister's corner pier, one in the gate passage.
export const OUTSIDE = {
  flowerBeds: [{ x0: 1, x1: 3, z: 4, plants: ['red_tulip', 'oxeye_daisy', 'red_tulip'] }],
  corner: [4, 4], // the cloister's corner pier beside the gate, closing the court
}
