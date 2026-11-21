// The Pokémon Center's Mountain Lodge look (looks/lodge/pokemon_center, _2), the plan: drawn before any wall (ROADMAP
// 49, 50.12, tools/blueprints/briefs/pokemon_center.md, "The looks"). Same contract as the Red Dome (../pokemon_center):
// machine (6, 1, 7), the Nurse's cell (6, 1, 8), the PC (10, 1..2, 7), the camera eye (8.5, 2.7, 4.2) with a clear line
// to the machine, a chest by the machine, II adds a Pasture Block and a Shop Counter. Another shape, not a palette.
//
// Story: "The Mountain Lodge". On the pass road a clinic has to keep a trainer warm: a stone socle against the snow,
// round spruce logs laid up above it, a steep red roof whose front gable reaches out two blocks over a porch on log posts,
// a fire in the west wall with its stack standing free beside the roof and the winter's wood stacked under the eave.
// Its east side already carries a cross gable (a window high over the PC) where the lodge means to grow, a dormer
// lights the desk from the west, and the Nurse has her own back door under a little pent porch. Inside, one tall hall
// open to its ridge beam: armchairs at the fire, a waiting bench and a couch facing the machine, the desk across the
// hall, the Nurse's corner behind it with a writing desk, a chair and her cupboards. Later (II) the cross gable runs on
// east as the Trader's wing, the same roof carried to its own gable end, with a shop door to the street and a back door
// to the paddock; the paddock has a field shelter for the Pokémon, a trough, and stepping-stone paths from both back
// doors to the Pasture Block.
//
// Round 2 (critic, 2026-11-21, briefs/cobblemon_50_12_critic.md): each note's answer is named in the parts below.
//
// Named parts:
//   Socle      stone y 0..1 round the hall (x 1..11, z 2..11); log walls y 2..4, corner posts, a wall plate at y 5.
//   Roof       red, 45 degrees, ridge along z (y 11 at x 6), from z 0 (two out over the porch) to z 12, no ceiling
//              lining (round 1's 117 boards; Level I stays lean): a ridge beam (y 10) runs the length, and purlin ends
//              (x 3 and 9, y 7) show through both gables (note 4's beam ends).
//   Cross gable (note 1) on the east slope: ridge along x at z 7 (y 9), eaves at z 3 and 11, a whitewashed timbered
//              face jettied out over the east wall (x 12, on a sill beam and three brackets) with a window, its roof
//              dying into the main slope in two valleys. II's wing continues it.
//   Dormer     (note 1) a pitched dormer on the west slope at z 8, face at x 2, lighting the desk.
//   Porch      z 0..1: steps at z 0, a deck, two log posts with brackets, a beam and a boarded ceiling at y 5.
//   Gables     the front gable at z 0 (timbered, white, a big window), the back gable at z 11 with the purlin ends.
//   Back porch (note 4) the Nurse's back door (6, 1, 11) under a pent on two log posts, brackets, a slab deck.
//   Hearth     the fire in the west wall at (1, 1, 4), the stack outside at x 0 up past the ridge; a woodpile under the
//              west eave (z 9..11).
//   Windows    (note 5) three kinds, never the same pair twice on a face: red shutters at the front and west, window
//              boxes (a sill with potted plants) at the back and under the cross gable, glass in the gables and dormer.
//   Hall       the fire corner (armchairs, a lamp table, a rug), the waiting nook (a couch on the east wall, a coffee
//              table, a bench facing the machine, a rug), the desk across z 7, tie beams with lanterns.
//   Back room  the Nurse's side z 8..10: a writing desk with candles and its chair, two chests, cupboards either side of
//              the back door, a bookcase with a fern, her lantern.
//   Wing (II)  (note 2) x 12..19, z 4..10, log walls on a socle, the cross gable's roof run on to its own jettied
//              gable end at x 20 (ridge y 9, below the hall's 11): the valley is the cross gable's, already clean in I. The shop door
//              on the street side, a back door to the paddock, the arch from the hall at z 5.
//   Paddock (II) (note 3) z 13..25 behind both: a spruce fence with two open gaps (villagers can't work gates), lamp
//              posts by them, stepping-stone paths from the hall's and the wing's back doors to the Pasture Block on a
//              stone pad, a lean-to field shelter on the back fence with hay in it, a log trough.

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
  path: ['cobblestone_slab', 'mossy_cobblestone_slab'],
  pots: ['potted_red_tulip', 'potted_fern', 'potted_azure_bluet', 'potted_spruce_sapling', 'potted_oxeye_daisy'],
}

export const HALL = { x0: 1, x1: 11, z0: 2, z1: 11, plate: 5, ties: [5, 9], purlins: [3, 9], roof: { axis: 'z', across: [0, 12], along: [0, 12], eaveY: 5, steep: false } }
// the east slope's height over column x (x 6..12), the west slope's (x 0..6)
export const mainEast = x => 5 + (12 - x)
export const mainWest = x => 5 + x
// the cross gable (I) and the wing that continues it (II): ridge along x at z 7
export const CROSS = { cz: 7, half: 4, eaveY: 5, from: 8, to: 12 } // `to`: the jettied face's line
export const WING = { x0: 12, x1: 19, z0: 4, z1: 10, plate: 5, ties: [14], overhang: 20 }
export const DORMER = { face: 2, zc: 8 }

export const FIXED = { machine: [6, 1, 7], nurse: [6, 1, 8], pc: [10, 1, 7], camera: [8.5, 2.7, 4.2], shopCounter: [15, 1, 7], pasture: [10, 1, 19] }

const POT = PALETTE.pots
export const OPENINGS = {
  frontDoor: [6, 1, 2],
  backDoor: { at: [6, 1, 11], step: [6, 0, 12] },
  hearth: [1, 1, 4],
  // [cells, out, dress, pots]: never the same dressing twice in a row on a face
  windows: [
    [[[3, 2, 2], [3, 3, 2]], 'north', 'shutters'], [[[9, 2, 2], [9, 3, 2]], 'north', 'shutters'], // the porch pair
    [[[1, 2, 7], [1, 3, 7]], 'west', 'shutters'],
    [[[11, 2, 8], [11, 3, 8], [11, 2, 9], [11, 3, 9]], 'east', 'sill', [POT[0], POT[1]]], // the window box under the cross gable
    [[[3, 2, 11], [3, 3, 11]], 'south', 'sill', [POT[2]]], [[[9, 2, 11], [9, 3, 11]], 'south', 'sill', [POT[3]]],
  ],
  eastWindowI: [[[11, 2, 5], [11, 3, 5]], 'east', 'shutters'], // I only: the arch to the wing in II
  arch: [11, 5],
  wingDoor: { at: [16, 1, 4], step: [16, 0, 3] },
  wingBackDoor: { at: [15, 1, 10], step: [15, 0, 11] },
  wingWindows: [
    [[[13, 2, 4], [13, 3, 4]], 'north', 'shutters'], [[[18, 2, 4], [18, 3, 4]], 'north', 'sill', [POT[4]]],
    [[[19, 2, 7], [19, 3, 7]], 'east', 'shutters'],
    [[[13, 2, 10], [13, 3, 10]], 'south', 'sill', [POT[1]]], [[[18, 2, 10], [18, 3, 10]], 'south', 'shutters'],
  ],
}

export const FURNITURE = [
  // the fire corner: two armchairs facing the fire, a lamp table by the first; a rug before the hearth
  { level: 1, room: 'Hall', kind: 'chair', part: 'armchair', pos: [3, 1, 3], facing: 'west', wood: 'spruce', colour: 'red' },
  { level: 1, room: 'Hall', kind: 'chair', part: 'armchair', pos: [3, 1, 5], facing: 'west', wood: 'spruce', colour: 'red' },
  { level: 1, room: 'Hall', kind: 'lamp', part: 'tableLamp', pos: [2, 1, 3], facing: 'east', wood: 'spruce' },
  { level: 1, room: 'Hall', kind: 'rug', part: 'rug', cellsAbs: [[2, 1, 4], [2, 1, 5], [3, 1, 4]], colour: 'red' },
  // the waiting nook: a couch on the east wall with a coffee table at its end, a bench facing the machine, a rug
  { level: 1, room: 'Hall', kind: 'couch', part: 'couch', pos: [10, 1, 4], facing: 'west', length: 2, wood: 'spruce', colour: 'white' },
  { level: 1, room: 'Hall', kind: 'table', part: 'table', pos: [9, 1, 3], facing: 'west', wood: 'spruce', colour: 'white' },
  { level: 1, room: 'Hall', kind: 'couch', part: 'couch', pos: [8, 1, 4], facing: 'south', length: 2, wood: 'spruce', colour: 'white' },
  { level: 1, room: 'Hall', kind: 'rug', part: 'rug', cellsAbs: [[7, 1, 5], [8, 1, 5]], colour: 'white' },
  // the desk
  { level: 1, room: 'Desk', kind: 'counter', part: 'desk', cells: [[3, 1, 7], [4, 1, 7], [5, 1, 7], [7, 1, 7], [8, 1, 7], [9, 1, 7]], facing: 'north', wood: 'spruce' },
  // the Nurse's side: her writing desk and chair, two chests, cupboards either side of the back door, a bookcase
  { level: 1, room: 'Back room', kind: 'fixture', part: 'chest', pos: [2, 1, 9], facing: 'east', block: 'chest' },
  { level: 1, room: 'Back room', kind: 'desk', part: 'desk', pos: [2, 1, 10], facing: 'east', wood: 'spruce' },
  { level: 1, room: 'Back room', kind: 'chair', part: 'chair', pos: [3, 1, 10], facing: 'west', wood: 'spruce' },
  { level: 1, room: 'Back room', kind: 'fixture', part: 'chest', pos: [10, 1, 10], facing: 'west', block: 'chest' },
  { level: 1, room: 'Back room', kind: 'fixture', part: 'bookcase', pos: [10, 1, 9], plant: 'potted_fern' },
  { level: 1, room: 'Back room', kind: 'cupboard', part: 'cupboard', pos: [5, 1, 10], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Back room', kind: 'cupboard', part: 'cupboard', pos: [7, 1, 10], facing: 'north', wood: 'spruce' },
  // lanterns under the tie beams
  { level: 1, room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 5], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [8, 4, 5], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Back room', kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 9], facing: 'north', wood: 'spruce' },

  // II, the Trader's wing: the Shop Counter between two counters, a chest and a cupboard behind, a table with two
  // chairs and a lamp table by the gable window, a rug for the customers, a lantern from the tie beam
  { level: 2, room: 'Wing', kind: 'counter', part: 'desk', cells: [[14, 1, 7], [16, 1, 7]], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Wing', kind: 'fixture', part: 'chest', pos: [13, 1, 9], facing: 'north', block: 'chest' },
  { level: 2, room: 'Wing', kind: 'cupboard', part: 'cupboard', pos: [17, 1, 9], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Wing', kind: 'fixture', part: 'bookcase', pos: [18, 1, 9], plant: 'potted_spruce_sapling' },
  { level: 2, room: 'Wing', kind: 'table', part: 'table', pos: [18, 1, 6], facing: 'west', wood: 'spruce', colour: 'red' },
  { level: 2, room: 'Wing', kind: 'chair', part: 'chair', pos: [17, 1, 6], facing: 'east', wood: 'spruce' },
  { level: 2, room: 'Wing', kind: 'chair', part: 'chair', pos: [18, 1, 5], facing: 'south', wood: 'spruce' },
  { level: 2, room: 'Wing', kind: 'lamp', part: 'tableLamp', pos: [18, 1, 7], facing: 'west', wood: 'spruce' },
  { level: 2, room: 'Wing', kind: 'rug', part: 'rug', cellsAbs: [[13, 1, 5], [14, 1, 5], [13, 1, 6], [14, 1, 6], [15, 1, 6]], colour: 'red' },
  { level: 2, room: 'Wing', kind: 'lamp', part: 'hangingLamp', pos: [14, 4, 6], facing: 'north', wood: 'spruce' },
]

// The walking line: up the porch steps to the door, past the waiting bench to the desk's front (6, 1, 6), round its
// west end to the Nurse's cell, on to her back door; II: through the arch into the wing to the Shop Counter's front
// (15, 1, 6), round behind it to the wing's back door; out either back door along the stepping stones to the Pasture
// Block.
export const WALK = [
  [6, 0, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [2, 1, 6], [2, 1, 7], [2, 1, 8], [6, 1, 8],
  [6, 1, 10], [6, 1, 11], [6, 0, 12],
  [10, 1, 5], [11, 1, 5], [12, 1, 5], [15, 1, 6], [17, 1, 8], [15, 1, 9], [15, 1, 10], [15, 0, 11],
  [6, 0, 13], [6, 0, 18], [9, 0, 19], [15, 0, 13], [15, 0, 18], [11, 0, 19],
]
