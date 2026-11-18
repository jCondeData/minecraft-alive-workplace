// The School (the Move Tutor's, Cobblemon), the plan: drawn before any wall. Rooms, every furniture footprint and the
// walking line come first; recipe.mjs then assembles it from the parts library (../parts) in passes.
//
// Three rough concepts (49 A, drawn from nothing):
//   1. "The Sparring Court": a dojo round an open yard. A tall, steep-gabled GATEHOUSE on the front (the hero, a round
//      moon window in its gable) opens on a paved court with a sparring ring inlaid in it and the Training Post at the
//      ring's centre; two low CLOISTERS with lean-to roofs run down the sides (spectators' benches on one, straw
//      targets on the other); the TUTOR'S HALL closes the back under a broad 45-degree roof: lecture room and his
//      quarters. Story: moves are learned in the open, in front of everyone, and the theory waits in the hall.
//   2. "The Lecture Tower": a square three-storey tower with a pyramid roof, classroom on top, the post on the ground
//      floor by the door and the Tutor's bed in between. Story: a scholar's tower. Compact, but the post is indoors
//      and a Pokemon lesson in a cupboard-sized room reads wrong; tall, narrow and like every wizard tower.
//   3. "The Long Hall": one long timber hall end-on to the road, a porch on posts at the front with the post under it,
//      a catslide over the quarters at the back. Story: a village schoolroom. Reads well but is the generic school
//      (and the Schoolhouse's job), nothing in it says Pokemon training.
// Strongest: 1. It is the only one where the building's shape is the job: the gate frames the ring, the post stands in
// the middle of the sky-lit court on the axis from the road, the cloisters give the player room to stand within the
// Tutor's 8-block reach and the Pokemon room to move. It has four masses with different eaves and ridges, a hero
// gable 30 % above the hall, and a court a player walks into rather than a box he walks past. Built: concept 1.
//
// Coordinates: x across the front (west 0 -> east 16), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The way in is the gate passage on the north side, three wide, a stair step before it.
//
// Masses (named parts):
//   Gate      the hero: stone piers x 5..6 and 10..11 (z 1..4) carrying a timber gable; open passage x 7..9, four high;
//             steep tiled roof, ridge along z, up to y 13, overhanging front and back; a moon window in the front gable.
//   Court     the open yard x 5..11, z 5..11, paved, the sparring ring (r 2) inlaid round the Training Post (8, 1, 8).
//   Cloisters west (x 1..4) and east (x 12..15), z 4..11: a white outer wall with windows, a colonnade of posts on the
//             court side, a lean-to roof falling from the outer wall (y 7) to the colonnade (y 4): no eave past the posts, so the walk stays
//             an open loggia three deep (holes.py's open check) and the court a full 7 x 7 under the sky.
//   Frame     (round 2) the outer walls' posts and beams stand a block proud of the white panels (x 0, x 16, z 19), on
//             a stone kerb; a chimney stack up the middle of the back wall (x 8, z 19) from the lecture room's hearth.
//   Hall      the Tutor's Hall across the back: walls x 1..15, z 12..18, a 45-degree roof (ridge along x, y 9) with
//             barge boards on both gables; a lecture room (west) and the Tutor's quarters (east) behind a shelf screen.

export const SIZE = [17, 14, 20] // w, h, d (limit 18 x 18 x 20)

// Palette (60/30/10): white plaster (walls, gables) / dark oak frame and red clay-tile (brick) roofs / stone-brick plinth and
// piers, the ring's packed earth, red cloth. Why: a training hall should read clean and disciplined from the road (white
// panels ruled by dark posts under warm tiles), the stone gate gives the hero mass weight, and the warm earth ring and
// red banners and beds are the only colour, all of it where the lessons happen.
export const PALETTE = {
  stone: [['stone_bricks', 7], ['cracked_stone_bricks', 1], ['andesite', 1], ['mossy_stone_bricks', 1]],
  frame: 'dark_oak_log',
  infill: 'white_concrete',
  roof: { full: 'bricks', stairs: 'brick_stairs', slab: 'brick_slab' },
  floor: 'spruce_planks',
  ceiling: 'dark_oak_planks',
  paving: [['stone_bricks', 5], ['polished_andesite', 2], ['cracked_stone_bricks', 1]],
  ring: { inner: 'coarse_dirt', border: 'polished_andesite' },
  wood: 'dark_oak', // doors, trapdoors, fences, stairs as seats
  furniture: 'spruce', // tables, chairs, shelves
  glass: 'glass_pane',
  accent: 'red',
}

export const MASSES = {
  gate: {
    piers: [[5, 6], [10, 11]], z0: 1, z1: 4, passage: [7, 9], pierTop: 5,
    roof: { axis: 'z', across: [4, 12], along: [0, 5], eaveY: 8, steep: false }, // one broad 45-degree gable over the whole tower
    moon: [8, 10],
  },
  court: { x0: 5, x1: 11, z0: 5, z1: 11, ring: { cx: 8, cz: 8, r: 2 } },
  cloisters: [
    { side: 'west', wall: 1, colonnade: 4, walk: [2, 3], z0: 4, z1: 11, roof: { axis: 'z', across: [0, 4], ridge: 1, along: [4, 11], ridgeY: 7 } },
    { side: 'east', wall: 15, colonnade: 12, walk: [13, 14], z0: 4, z1: 11, roof: { axis: 'z', across: [12, 16], ridge: 15, along: [4, 11], ridgeY: 7 } },
  ],
  hall: {
    walls: { x0: 1, x1: 15, z0: 12, z1: 18 }, room: { x0: 2, x1: 14, z0: 13, z1: 17 },
    wallTop: 3, beamY: 4, knee: 5,
    frontPosts: [1, 5, 7, 9, 11, 15], backPosts: [1, 4, 8, 12, 15], sidePosts: [12, 15, 18],
    roof: { axis: 'x', across: [11, 19], along: [0, 16], eaveY: 5, steep: false },
    screen: { x: 11, z0: 13, z1: 17, doorway: 14 },
  },
}

export const ROOMS = [
  { name: 'Court', use: 'lessons: the Training Post in the sparring ring, open to the sky', box: [5, 1, 5, 11, 4, 11] },
  { name: 'West cloister', use: "spectators' benches facing the ring", box: [2, 1, 5, 3, 3, 11] },
  { name: 'East cloister', use: 'straw practice targets and the Tutor\'s chest', box: [13, 1, 5, 14, 3, 11] },
  { name: 'Lecture room', use: 'a reading table, a wall of move books, the Tutor\'s desk', box: [2, 1, 13, 10, 4, 17] },
  { name: 'Quarters', use: "the Tutor's bed, nightstand and wardrobe", box: [12, 1, 13, 14, 4, 17] },
]

export const JOB = { block: 'aliveworkplace:training_post', at: [8, 1, 8], facing: 'north' }

export const OPENINGS = {
  gateway: { x0: 7, x1: 9, z0: 1, z1: 4, y0: 1, y1: 4, steps: [[7, 0, 0], [8, 0, 0], [9, 0, 0]] },
  hallDoor: { at: [8, 1, 12], facing: 'south', out: 'north' },
  screenDoorway: { at: [11, 1, 14], height: 2 },
  windows: [
    { name: 'west cloister south', cells: [[1, 2, 10], [1, 3, 10]], out: 'west', dress: 'shutters' },
    { name: 'west cloister north', cells: [[1, 2, 6], [1, 3, 6]], out: 'west', dress: 'shutters' },
    { name: 'east cloister south', cells: [[15, 2, 10], [15, 3, 10]], out: 'east', dress: 'shutters' },
    { name: 'east cloister north', cells: [[15, 2, 6], [15, 3, 6]], out: 'east', dress: 'shutters' },
    { name: 'west cloister front', cells: [[2, 2, 4], [3, 2, 4], [2, 3, 4], [3, 3, 4]], out: 'north', dress: 'sill' },
    { name: 'east cloister front', cells: [[13, 2, 4], [14, 2, 4], [13, 3, 4], [14, 3, 4]], out: 'north', dress: 'sill' },
    { name: 'hall court west', cells: [[6, 2, 12], [6, 3, 12]], out: 'north', dress: null },
    { name: 'hall court east', cells: [[10, 2, 12], [10, 3, 12]], out: 'north', dress: null },
    { name: 'hall back west', cells: [[2, 2, 18], [3, 2, 18], [2, 3, 18], [3, 3, 18]], out: 'south', dress: 'sill' },
    { name: 'hall back middle', cells: [[6, 2, 18], [6, 3, 18], [10, 2, 18], [10, 3, 18]], out: 'south', dress: null },
    { name: 'hall back east', cells: [[13, 2, 18], [14, 2, 18], [13, 3, 18], [14, 3, 18]], out: 'south', dress: 'sill' },
    { name: 'hall west', cells: [[1, 2, 15], [1, 3, 15]], out: 'west', dress: 'sill' },
    { name: 'hall east', cells: [[15, 2, 15], [15, 3, 15]], out: 'east', dress: 'sill' },
    { name: 'hall west gable', cells: [[1, 6, 15], [1, 7, 15]], out: 'west', dress: null },
    { name: 'hall east gable', cells: [[15, 6, 15], [15, 7, 15]], out: 'east', dress: null },
  ],
}

// Every piece of furniture, footprint first (kind: furniture.py's; part: furnitureParts' or the school parts').
const W = 'spruce'
export const FURNITURE = [
  // West cloister: two benches facing the ring, a red rug before each, lanterns hung under the eave
  { room: 'West cloister', kind: 'couch', part: 'couch', pos: [2, 1, 5], facing: 'east', length: 3, wood: 'dark_oak', colour: 'red' },
  { room: 'West cloister', kind: 'couch', part: 'couch', pos: [2, 1, 9], facing: 'east', length: 3, wood: 'dark_oak', colour: 'red' },
  { room: 'West cloister', kind: 'lamp', part: 'hangingLamp', pos: [3, 3, 6], facing: 'east', wood: W },
  { room: 'West cloister', kind: 'lamp', part: 'hangingLamp', pos: [3, 3, 10], facing: 'east', wood: W },
  // East cloister: straw targets on the outer wall, the Tutor's chest at the hall end, a lamp
  { room: 'East cloister', kind: 'fixture', part: 'target', pos: [14, 1, 5], facing: 'west' },
  { room: 'East cloister', kind: 'fixture', part: 'target', pos: [14, 1, 8], facing: 'west' },
  { room: 'East cloister', kind: 'fixture', part: 'chest', pos: [14, 1, 11], facing: 'west', block: 'chest' },
  { room: 'East cloister', kind: 'lamp', part: 'hangingLamp', pos: [13, 3, 6], facing: 'west', wood: W },
  { room: 'East cloister', kind: 'lamp', part: 'hangingLamp', pos: [13, 3, 10], facing: 'west', wood: W },
  // Lecture room: the wall of move books on the west gable, a reading table for four, the Tutor's desk at the back
  ...[13, 14, 16, 17].map(z => ({ room: 'Lecture room', kind: 'cupboard', part: 'cupboard', pos: [2, 1, z], facing: 'east', cells: [[0, 0, 0], [0, 1, 0]], wood: W })),
  ...[4, 5, 6].map(x => ({ room: 'Lecture room', kind: 'table', part: 'table', pos: [x, 1, 15], facing: 'north', cells: [[0, 0, 0], [0, 1, 0]], wood: W, colour: 'white' })),
  { room: 'Lecture room', kind: 'chair', part: 'chair', pos: [4, 1, 14], facing: 'south', wood: W },
  { room: 'Lecture room', kind: 'chair', part: 'chair', pos: [6, 1, 14], facing: 'south', wood: W },
  { room: 'Lecture room', kind: 'chair', part: 'chair', pos: [4, 1, 16], facing: 'north', wood: W },
  { room: 'Lecture room', kind: 'chair', part: 'chair', pos: [6, 1, 16], facing: 'north', wood: W },
  { room: 'Lecture room', kind: 'desk', part: 'desk', pos: [9, 1, 17], facing: 'south', wood: W },
  { room: 'Lecture room', kind: 'chair', part: 'chair', pos: [9, 1, 16], facing: 'south', wood: W },
  { room: 'Lecture room', kind: 'fixture', part: 'hearth', pos: [8, 1, 17], facing: 'north' },
  { room: 'Lecture room', kind: 'picture', part: 'picture', pos: [8, 3, 17], facing: 'north', wood: W, colour: 'red' },
  { room: 'Lecture room', kind: 'picture', part: 'picture', pos: [4, 3, 13], facing: 'south', wood: W, colour: 'white' },
  { room: 'Lecture room', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 15], facing: 'north', chain: 4, wood: W },
  { room: 'Lecture room', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 14], facing: 'north', chain: 3, wood: W },
  // Quarters: the Tutor's bed against the back wall, a nightstand, a wardrobe, a rug
  { room: 'Quarters', kind: 'bed', part: 'bed', pos: [13, 1, 16], facing: 'south', colour: 'red' },
  { room: 'Quarters', kind: 'nightstand', part: 'nightstand', pos: [14, 1, 17], facing: 'north', wood: W },
  { room: 'Quarters', kind: 'cupboard', part: 'cupboard', pos: [14, 1, 13], facing: 'west', cells: [[0, 0, 0], [0, 1, 0]], wood: W },
  { room: 'Quarters', kind: 'rug', part: 'rug', cellsAbs: [[12, 1, 15], [13, 1, 15], [12, 1, 16]], colour: 'red' },
  { room: 'Quarters', kind: 'lamp', part: 'hangingLamp', pos: [13, 4, 15], facing: 'north', chain: 4, wood: W },
]

// The walking line: the road -> the step -> the gate passage -> round the ring to the post (stand at (8, 1, 6), the
// player anywhere in the court) -> the hall door -> the lecture room -> the screen doorway -> the bed.
export const WALK = [
  [8, 0, -3], [8, 0, 0], [8, 1, 1], [8, 1, 4], [8, 1, 6], [6, 1, 8], [8, 1, 10], [8, 1, 12], [8, 1, 13], [10, 1, 14],
  [11, 1, 14], [12, 1, 14], [12, 1, 16],
]

// Outside the rooms: flower beds under the cloisters' front windows, lamp posts at the gate, a lantern in the passage.
export const OUTSIDE = {
  flowerBeds: [{ x0: 1, x1: 3, z: 3, plants: ['red_tulip', 'oxeye_daisy', 'red_tulip'] }, { x0: 13, x1: 15, z: 3, plants: ['red_tulip', 'oxeye_daisy', 'red_tulip'] }],
  lampPosts: [[4, 0, 1], [12, 0, 1]],
}
