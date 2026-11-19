// The Pokémon Center's Mountain Lodge look (looks/lodge/pokemon_center, _2), the plan: drawn before any wall (ROADMAP
// 49, tools/blueprints/briefs/pokemon_center.md, "The looks"). Same contract as the Red Dome (../pokemon_center):
// machine (6, 1, 7), the Nurse's cell (6, 1, 8), the PC (10, 1..2, 7), the camera eye (8.5, 2.7, 4.2) with a clear line
// to the machine, a chest by the machine, II adds a Pasture Block and a Shop Counter. Another shape, not a palette.
//
// Story: "The Mountain Lodge". On the pass road a clinic has to keep a trainer warm: a stone socle against the snow,
// round spruce logs laid up above it, a steep red roof whose front gable reaches out two blocks over a porch on log posts,
// a fire in the west wall with its stack standing free beside the roof, red shutters on every window. Inside, one tall
// hall open to the roof's boards: armchairs at the fire, the desk across the hall, tie beams with lanterns. Later (II)
// a lower log wing with its own gable to the east holds the Trader's counter, and a fenced paddock behind holds the
// Pasture Block by a trough and a hay stack.
//
// Named parts:
//   Socle     stone y 0..1 round the hall (x 1..11, z 2..11); log walls y 2..4, corner posts, a wall plate at y 5.
//   Roof      red, 45 degrees, ridge along z (y 11 at x 6), from z 0 (two out over the porch) to z 12.
//   Porch     z 0..1: steps at z 0, a deck, two log posts with brackets, a beam and a boarded ceiling at y 5.
//   Gables    the front gable at z 0 (timbered, white, a window into the hall), the back gable at z 11.
//   Hearth    the fire in the west wall at (1, 1, 4), the stack outside at x 0 up past the ridge.
//   Hall      the fire corner (armchairs, a lamp table), the bench by the east wall, the desk across z 7, tie beams.
//   Back room the Nurse's side z 8..10: chests, cupboards, her lamp.
//   Wing (II) the Trader's wing x 11..19, z 3..9, log walls, a lower gable east (ridge along x, y 8), its own door.
//   Paddock (II) z 13..24: a spruce fence with a gap to walk in by, the Pasture Block on a stone pad, a trough, hay.

export const SIZE = { 1: [13, 15, 13], 2: [21, 15, 26] }

// Palette (60/30/10): spruce logs (walls) / a red nether-brick roof (the clinic's red, darkened for the mountains) and
// mossy rubble socle / white gables and red shutters. Why: a log house on stone reads as built for weather; the red
// roof and shutters and the white gable keep the clinic's two colors.
export const PALETTE = {
  stone: [['cobblestone', 4], ['stone', 2], ['mossy_cobblestone', 2], ['andesite', 1]],
  log: 'spruce_log',
  frame: 'stripped_spruce_log',
  infill: 'white_concrete', // whitewashed gables: the clinic's white
  roof: { full: 'red_nether_bricks', stairs: 'red_nether_brick_stairs', slab: 'red_nether_brick_slab' },
  floor: 'spruce_planks',
  lining: 'spruce_planks',
  wood: 'spruce',
  shutter: 'mangrove',
  glass: 'glass_pane',
  fence: 'spruce_fence',
}

export const HALL = { x0: 1, x1: 11, z0: 2, z1: 11, plate: 5, ties: [5, 9], roof: { axis: 'z', across: [0, 12], along: [0, 12], eaveY: 5, steep: false } }
export const WING = { x0: 11, x1: 19, z0: 3, z1: 9, plate: 4, roof: { axis: 'x', across: [2, 10], along: [12, 20], eaveY: 4, steep: false } }

export const FIXED = { machine: [6, 1, 7], nurse: [6, 1, 8], pc: [10, 1, 7], camera: [8.5, 2.7, 4.2], shopCounter: [15, 1, 6], pasture: [10, 1, 19] }

export const OPENINGS = {
  frontDoor: [6, 1, 2],
  hearth: [1, 1, 4],
  windows: [ // [x, y0, z, out]: two-high windows with red shutters
    [3, 2, 2, 'north'], [9, 2, 2, 'north'], [1, 2, 7, 'west'], [11, 2, 9, 'east'], [4, 2, 11, 'south'], [8, 2, 11, 'south'],
  ],
  eastWindowI: [11, 2, 4, 'east'], // I only: the wing covers this wall in II
  backWindow: [6, 2, 11, 'south'], // I: a window; II: the door to the paddock
  arch: [11, 6], // II: through the hall's east wall into the wing
  wingDoor: { at: [15, 1, 3], step: [15, 0, 2] },
  wingWindows: [[13, 2, 3, 'north'], [17, 2, 3, 'north'], [19, 2, 6, 'east'], [13, 2, 9, 'south'], [17, 2, 9, 'south']],
}

export const FURNITURE = [
  // the fire corner: two armchairs facing the fire, a lamp table by the first; a rug before the hearth
  { level: 1, room: 'Hall', kind: 'chair', part: 'armchair', pos: [3, 1, 3], facing: 'west', wood: 'spruce', colour: 'red' },
  { level: 1, room: 'Hall', kind: 'chair', part: 'armchair', pos: [3, 1, 5], facing: 'west', wood: 'spruce', colour: 'red' },
  { level: 1, room: 'Hall', kind: 'lamp', part: 'tableLamp', pos: [2, 1, 3], facing: 'east', wood: 'spruce' },
  { level: 1, room: 'Hall', kind: 'rug', part: 'rug', cellsAbs: [[2, 1, 4], [2, 1, 5], [3, 1, 4]], colour: 'red' },
  // the bench by the east wall for the waiting trainers
  { level: 1, room: 'Hall', kind: 'couch', part: 'couch', pos: [10, 1, 4], facing: 'west', length: 2, wood: 'spruce', colour: 'white' },
  // the desk
  { level: 1, room: 'Desk', kind: 'counter', part: 'desk', cells: [[3, 1, 7], [4, 1, 7], [5, 1, 7], [7, 1, 7], [8, 1, 7], [9, 1, 7]], facing: 'north', wood: 'spruce' },
  // the Nurse's side
  { level: 1, room: 'Back room', kind: 'fixture', part: 'chest', pos: [2, 1, 9], facing: 'east', block: 'chest' },
  { level: 1, room: 'Back room', kind: 'fixture', part: 'chest', pos: [10, 1, 10], facing: 'west', block: 'chest' },
  { level: 1, room: 'Back room', kind: 'cupboard', part: 'cupboard', pos: [5, 1, 10], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Back room', kind: 'cupboard', part: 'cupboard', pos: [7, 1, 10], facing: 'north', wood: 'spruce' },
  // lanterns under the tie beams
  { level: 1, room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 5], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [8, 4, 5], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Back room', kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 9], facing: 'north', wood: 'spruce' },

  // II, the Trader's wing: the Shop Counter between two counters, a chest and a cupboard behind, a table and chairs
  { level: 2, room: 'Wing', kind: 'counter', part: 'desk', cells: [[14, 1, 6], [16, 1, 6]], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Wing', kind: 'fixture', part: 'chest', pos: [13, 1, 8], facing: 'north', block: 'chest' },
  { level: 2, room: 'Wing', kind: 'cupboard', part: 'cupboard', pos: [17, 1, 8], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Wing', kind: 'table', part: 'table', pos: [18, 1, 4], facing: 'north', wood: 'spruce', colour: 'red' },
  { level: 2, room: 'Wing', kind: 'chair', part: 'chair', pos: [17, 1, 4], facing: 'east', wood: 'spruce' },
  { level: 2, room: 'Wing', kind: 'chair', part: 'chair', pos: [18, 1, 5], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Wing', kind: 'lamp', part: 'hangingLamp', pos: [15, 3, 5], facing: 'north', wood: 'spruce' },
]

// The walking line: up the porch steps to the door, past the fire to the desk's front (6, 1, 6), round its west end to
// the Nurse's cell; II: through the arch into the wing to the Shop Counter's front (15, 1, 5); out the back door to the
// paddock gate and the Pasture Block.
export const WALK = [
  [6, 0, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [2, 1, 6], [2, 1, 7], [2, 1, 8], [6, 1, 8],
  [10, 1, 6], [11, 1, 6], [12, 1, 6], [13, 1, 6], [13, 1, 5], [15, 1, 5], [6, 1, 11], [6, 0, 12], [6, 0, 13], [10, 0, 18],
]
