// The Arena (ROADMAP 49 rebuild), the plan: drawn before any wall. Every spot the Festival Cup reads, the seats, the
// rooms, every furniture footprint and the walking line come first; recipe.mjs then draws the three levels from the
// parts library (../parts) to fit this plan. The brief: tools/blueprints/briefs/arena.md.
//
// Three rough concepts (massing, roof, plan, story):
//   A. "The Bowl": an oval of stepped stone tiers wrapped round the pitch on every side, entered by a vaulted tunnel,
//      a colonnade crowning III. Story: a little Roman amphitheatre. Weak here: 25 blocks wide leaves no room for a
//      curve round a 15-long pitch, the trainer's boxes at the ends would cut the oval, and it reads as one ring of
//      grey steps from every side.
//   B. "The Tourney Yard": an open field with a timber grandstand only along the back, the boxes as tented pavilions.
//      Story: a fair's jousting ground. Weak: one-sided; II's seats on both long sides would be an afterthought, and a
//      tent roof can't be built from a builder's barrels.
//   C. "The Market Stadium": the village's Cup ground grows like a real town stadium. I is a chalk-marked pitch with a
//      red box and a blue box under little hip canopies, a flag at each, one row of seats along the back wall and a
//      low front wall whose gate pier carries the notice board. II raises a colonnaded stand along the front: the
//      seats ride on a deck over an arcade, and under it runs the Cup's fair lane, traders under the seats as in a
//      Roman stadium's shops, with a judges' loggia over the gate. III roofs the back: a brick-roofed grandstand on
//      stepped rows with the Leader's box under a tall central gable, closed at each end by a trainers' tower (the
//      red and the blue team's rooms, a Healing Machine in each), and the champion's mast before the gate.
// C is the strongest: every level adds a real building to the last (wall -> colonnade -> grandstand and towers), the
// front, the street side, is the busiest face (fair, gate, board, mast), the outline climbs from a low front over the
// open pitch to a tall back with towers, and the red/blue corners tell the whole story of a bout at a glance. Built: C.
//
// Coordinates: x across the front (west 0 -> east 24), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, the street) to the back. Front = the north face; the pitch's long axis runs along x.
//
// Named parts (for review and the owner's notes):
//   Pitch        the 15 x 9 battle floor x 5..19, z 7..15 at y 0 (coarse earth, chalk halfway line and circle, a
//                chalk boundary one outside it). Nothing ever stands on it.
//   Boxes        the red box (west) and the blue box (east): a 3 x 3 dais under a hip canopy at each end of the long
//                axis, a lantern over the trainer, the step at the back.
//   Front wall   (I) a low coped wall along z 2 with piers, the gate x 11..13; the notice board on the west gate pier.
//   Back stand   (I) one row of seats along z 17 against a coped back wall (z 18), a podium in the middle.
//   Flags        (I) a red flag (west) and a blue one (east) on tall poles behind the boxes.
//   Colonnade    (II) the front stand: arcades on both faces of z 2..5, a deck at y 4, 18 seats on it facing the
//                pitch, a parapet to the street, a landing at each end on a solid bay and a flight up to it.
//   Fair lane    (II) the covered walk under the colonnade (z 3..4) and the forecourt in front of it.
//   Loggia       (II) the judges' loggia over the gate: posts, a beam ring and a 45-degree brick gable to the street.
//   Grandstand   (III) rows z 18, 19 on solid tiers, a promenade z 20, a back wall z 21 with windows and piers, a
//                45-degree brick roof (ridge along x) on a front beam, the Leader's box under a steep hero gable.
//   Towers       (III) the trainers' rooms x 0..4 and 20..24, z 16..22: stone below, white plaster and timber above,
//                hip roofs; a Healing Machine, a bench, a lamp, a rug in the team's colour.
//   Mast         (III) the champion's mast before the gate, the Cup banner hung on both faces of its head.

export const SIZES = { 1: [25, 9, 19], 2: [25, 13, 19], 3: [25, 14, 23] } // w, h, d (limits 25 x 14 x 29; I 25 x 19)

// Palette (60/30/10): stone-brick masonry (piers, walls, tiers, tower bases) / spruce timber (decks, seats, posts,
// beams) with white plaster on the towers / red clay-brick roofs, with the red and blue of the two corners and white
// chalk as the accents. Why: grey masonry over a cobbled ground reads as a civic ground a village built, warm spruce
// seats are what people touch, the brick roofs stand out red against grey from the street, and the team colours make
// the two ends of the pitch readable from anywhere.
export const PALETTE = {
  ground: [['cobblestone', 5], ['andesite', 3], ['stone', 2], ['mossy_cobblestone', 1]],
  masonry: [['stone_bricks', 8], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1]],
  dressed: 'polished_andesite', // dais, coping courses, the Leader's box front
  carved: 'chiseled_stone_bricks',
  coping: 'stone_brick_slab',
  springer: 'stone_brick_stairs',
  parapet: 'stone_brick_wall',
  frame: 'spruce_log',
  deck: 'spruce_planks',
  wood: 'spruce', // seats (stairs), fences, doors, trapdoors
  plaster: [['white_concrete', 5], ['polished_diorite', 1]],
  roof: { full: 'bricks', stairs: 'brick_stairs', slab: 'brick_slab' },
  pitch: { field: 'coarse_dirt', edge: 'polished_diorite', line: 'polished_diorite', spot: 'chiseled_stone_bricks' },
  board: 'dark_oak_planks', // the notice board's panel (the board spot) under its sign
  red: 'red', blue: 'blue', cup: 'yellow', // the corners' colours; the Cup's gold over the gate
  glass: 'glass_pane',
}

// The spots the Festival Cup reads (Arenas, template coordinates; the ship step writes them into the code)
export const SPOTS = {
  ringCentre: [12, 1, 11], // air over the pitch's centre spot (12, 0, 11)
  ring: { x0: 5, x1: 19, z0: 7, z1: 15, y: 0 },
  boxes: [[2, 2, 11], [22, 2, 11]], // both 10 from the centre (Manhattan); the red box west, the blue box east
  noticeBoard: [10, 2, 2], // the board's panel in the west gate pier; its sign faces the street from (10, 2, 1)
  fairLane: [8, 1, 4], // II, III: the walk under the colonnade (paving under it)
  championPole: [12, 12, 0], // III: the mast's head; banners go at (12, 12, -1) and (12, 12, 1)
  machines: [[2, 1, 21], [22, 1, 21]], // III: a Healing Machine in each trainers' room
}

// Seats: [x, y, z] of each stair, with the way the sitter faces (the stair's high back is the other side)
const row = (xs, y, z, faces) => xs.map(x => ({ at: [x, y, z], faces }))
const span = (a, b) => Array.from({ length: b - a + 1 }, (_, i) => a + i)
const backRow = [...span(5, 10), ...span(14, 19)] // 12 a row, the middle x 11..13 is the podium / Leader's box
export const SEATS = {
  back1: row(backRow, 1, 17, 'north'), // I
  front: row([...span(2, 10), ...span(14, 22)], 5, 5, 'south'), // II: on the colonnade's deck
  back2: row(backRow, 2, 18, 'north'), // III
  back3: row(backRow, 3, 19, 'north'), // III
}
export const seatsOf = level => [
  ...SEATS.back1, ...(level >= 2 ? SEATS.front : []), ...(level >= 3 ? [...SEATS.back2, ...SEATS.back3] : []),
] // 12, 30, 54 (III: 54, not 52: the ship step updates Arenas.seats and the two tests that count them)

export const MASSES = {
  frontWall: { z: 2, x0: 2, x1: 22, piers: [2, 6, 10, 14, 18, 22], gate: [11, 13] },
  backWall: { z: 18, x0: 4, x1: 20, piers: [4, 8, 12, 16, 20] },
  colonnade: { x0: 2, x1: 22, z0: 2, z1: 5, deckY: 4, piers: [2, 6, 10, 14, 18, 22] },
  loggia: { x0: 10, x1: 14, z0: 2, z1: 4, beamY: 8, roof: { axis: 'z', across: [9, 15], along: [2, 5], eaveY: 9, steep: false } },
  grandstand: { x0: 5, x1: 19, rows: [17, 18, 19], promenade: 20, backWall: 21, eave: 22, beamY: 7,
    roof: { axis: 'x', across: [16, 22], along: [5, 19], eaveY: 7, steep: false },
    hero: { axis: 'z', across: [10, 14], along: [16, 21], eaveY: 9, steep: true } },
  towers: [{ x0: 0, x1: 4, z0: 16, z1: 22, team: 'red' }, { x0: 20, x1: 24, z0: 16, z1: 22, team: 'blue' }],
  towerBeamY: 5, towerTop: 9,
}

export const OPENINGS = {
  flights: [ // up to a landing at each end of the colonnade's deck, rising from the pitch side toward the street
    { steps: [[1, 1, 8], [1, 2, 7], [1, 3, 6], [1, 4, 5]], rises: 'north' },
    { steps: [[23, 1, 8], [23, 2, 7], [23, 3, 6], [23, 4, 5]], rises: 'north' },
  ],
  towerDoors: [{ at: [2, 1, 16] }, { at: [22, 1, 16] }], // north walls, facing the boxes
}

// Furniture: only the trainers' rooms (III) have any. The east room mirrors the west (x -> 24 - x).
const westRoom = [
  { room: 'Red room', kind: 'fixture', part: 'machine', pos: [2, 1, 21], facing: 'north', block: 'cobblemon:healing_machine' },
  { room: 'Red room', kind: 'chair', part: 'chair', pos: [3, 1, 18], facing: 'west', wood: 'spruce' },
  { room: 'Red room', kind: 'chair', part: 'chair', pos: [3, 1, 19], facing: 'west', wood: 'spruce' },
  { room: 'Red room', kind: 'lamp', part: 'tableLamp', pos: [1, 1, 17], facing: 'east', wood: 'spruce' },
  { room: 'Red room', kind: 'lamp', part: 'hangingLamp', pos: [2, 4, 19], facing: 'north', wood: 'spruce' },
  { room: 'Red room', kind: 'picture', part: 'picture', pos: [1, 3, 18], facing: 'east', wood: 'spruce', colour: 'red' },
  { room: 'Red room', kind: 'rug', part: 'rug', cellsAbs: [[1, 1, 18], [2, 1, 18], [1, 1, 19], [2, 1, 19]], colour: 'red' },
]
const flip = { east: 'west', west: 'east', north: 'north', south: 'south' }
export const FURNITURE = [
  ...westRoom,
  ...westRoom.map(f => ({
    ...f, room: 'Blue room', facing: flip[f.facing],
    ...(f.pos ? { pos: [24 - f.pos[0], f.pos[1], f.pos[2]] } : {}),
    ...(f.cellsAbs ? { cellsAbs: f.cellsAbs.map(([x, y, z]) => [24 - x, y, z]) } : {}),
    ...(f.colour ? { colour: 'blue' } : {}),
  })),
]

// The walking line: street -> forecourt (fair, board) -> gate -> pitch -> back seats; up the west flight to the
// colonnade's seats; round the west end past the red box to the red room's door and the Healing Machine.
export const WALK = [
  [12, 1, 0], [12, 1, 1], [12, 1, 2], [12, 1, 4], [12, 1, 6], [12, 1, 11], [12, 1, 16], [9, 1, 16], [9, 2, 17],
  [3, 1, 6], [2, 1, 9], [1, 1, 8], [1, 4, 5], [1, 5, 4], [3, 5, 4], [5, 5, 4],
  [1, 1, 2], [0, 1, 6], [4, 1, 9], [4, 1, 14], [2, 1, 15], [2, 1, 16], [2, 1, 20],
]
