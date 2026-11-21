// The Stadium (a Wonder, ROADMAP 52.6), the plan: drawn before any wall. Four stages, each only adding to the last and
// each standing, grounded and tidy on its own (tools/blueprints/briefs/stadium.md). recipe.mjs draws it from the parts
// library (../parts, with ../parts/stadium_bowl.mjs grown for it) in passes per stage.
//
// Three rough concepts (massing, roof, plan, one line of story each):
//   A. The Grandstand Field: a flat pitch with one tall covered grandstand along the back and low banks on the other
//      three sides. Story: a county ground that grew one proud stand. The safe one: from the front it is a shed with
//      seats, and three of its four sides are nearly nothing.
//   B. The Amphitheatre: a half bowl dug round a round sand floor, open to the front like a Greek theatre, with a stage
//      wall (the gate) closing it. Story: the old way of watching. Strong outline, but the posts sit at the two ends of
//      the field, so a half-round faces neither trainer, and half its seats look at the gate's back.
//   C. The Stadium Bowl: one continuous bowl drawn on the stadium shape itself (a rounded rectangle: every ring the same
//      width round the straights and the curved ends), red and white seats all round, a brick outer wall, a covered
//      stand along the back, a gate front on the one cut through the bowl, and four corner pavilions where the round
//      ends leave the plot's corners free. Story: the whole village sits round the two trainers.
//   Strongest: C. Built: C.
//
// Round 2 (the critic's notes, briefs/stadium_52_6_critic.md), each answered by a named part below:
//   1. The concourse: a real hall now, hollow under the walk and the upper rows (rings 19-22, three high), closed by the
//      outer wall with doors and windows, lit by hanging lanterns, with doors out at both ends, into the gate's cut and
//      into the four corner pavilions: the nurse's booth, the refreshment room and the two changing rooms.
//   2. The nurse's booth: the front-right pavilion (the corner the front and top views see whole), a closed room off the concourse with its own door to the front, a
//      white panel with a red cross over that door and a red cross on its roof (seen from the front and from above).
//   3. The gate: a frontispiece across the front: two banner pylons, a recessed arch (a stepped reveal), a quartz lintel
//      with a keystone standing proud of the brick, a niche with a Poke Ball crest, and STADIUM in blackstone letters
//      on a quartz attic (read the right way round from the road; backed by brick, nothing to read from inside).
//   4. The walls: the brick wall now stands one ring in (ring 23) and everything on its face stands a block proud on
//      ring 24: a plinth, quartz pilasters every five blocks, a string course, a cornice and a coping carried right
//      round (the gate's own order takes over across the front); the windows sit back in the brick with quartz sills.
//   5. Story: a scoreboard on the east end, two blocks thick, "2 - 1" drawn on each face so it reads from the stands
//      and from the road, with the two teams' colours over the digits; a covered stand along the back under a copper
//      lean-to roof; and a field one ring wider (31 x 23 inside the black wall).
//
// Named parts: the Field, the Lower Stands, the Concourse, the Upper Stands, the Outer Wall, the Corner Pavilions (the
// Nurse's Booth, the Refreshment Room, the Home and the Away Changing Rooms), the Covered Stand, the Gate, the
// Scoreboard, the Floodlights.
//
// Coordinates: x across the front (west 0 -> east 58), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Plot 59 x 49, 26 high. The ring is (29, 1, 24); the posts (17, 1, 24) and (41, 1, 24).

// Stage titles (player text, as QaWonderDesignsGameTests.TITLES already reads them): I "The Field", II "The Lower
// Stands", III "The Upper Stands and the Covered Stand", IV "The Gate and the Floodlights".
// Description line (at ship): "A brick bowl of red and white seats round a chalked field, with a covered stand, a
// two-sided scoreboard, a lettered gate and four floodlights on corner pavilions."

export const SIZE = [59, 26, 49]
export const STAGES = 4

// The bowl: rings round the spine (29 +-4, z 24); ring r holds the cells r..r+1 from it.
export const BOWL = { cx: 29, cz: 24, half: 4 }
export const RINGS = {
  field: 10, // r <= 10: the grass (29 x 21; 31 x 23 with the touchline)
  touch: 11, // the white touchline
  barrier: 12, // the pitch wall
  lower: [13, 18], // three rows, two cells deep each: the seat, then the tread behind it
  walk: 19, // the walk between the rakes, the concourse's ceiling under it
  upper: [20, 22], // three rows, one cell each, over the concourse
  wall: 23, // the brick outer wall
  face: 24, // what stands proud of it: plinth, pilasters, string course, cornice
}
export const HALL_TOP = 3 // the concourse and the pavilions: y 1-3, the ceiling at 4
export const GAP = { x0: 27, x1: 31 } // the cut through the front of the bowl (z < 24): the way in from the gate spot
export const WALL_TOP = 12 // brick to 12, the cornice at 12 on the face ring, the coping at 13
export const BAY = 5 // a pilaster every five blocks round the face ring

export const POSTS = [[17, 1, 24], [41, 1, 24]]
export const RING = [29, 1, 24]
export const BOX_SPOTS = [[18, 1, 24], [18, 1, 25], [40, 1, 24], [40, 1, 23]]
export const GATE = [26, 1, -4]

// The corner pavilions (front-left and front-right are the front views' corners): a box each, its cells those outside the wall; the outer corner a pier that the mast stands on.
export const PAVILIONS = [
  { name: 'refresh', x0: 2, x1: 10, z0: 2, z1: 10, corner: [2, 2], door: [6, 2], facing: 'north' },
  { name: 'nurse', x0: 48, x1: 56, z0: 2, z1: 10, corner: [56, 2], door: [52, 2], facing: 'north' },
  { name: 'home', x0: 2, x1: 10, z0: 38, z1: 46, corner: [2, 46], door: [6, 46], facing: 'south' },
  { name: 'away', x0: 48, x1: 56, z0: 38, z1: 46, corner: [56, 46], door: [52, 46], facing: 'south' },
]
export const MAST_FOOT = 6 // on the pier's top (piers y 1-5)
export const MAST_TOP = 18

// The gate's span across the front (stage III leaves the face ring bare there; the gate draws its own order)
export const FRONT = { x0: 12, x1: 46, z1: 12, pylons: [[12, 14], [44, 46]], screen: [15, 43], pilasters: [15, 20, 25, 33, 38, 43] }
export const COVERED = { x0: 17, x1: 41 } // the covered stand along the back straight
export const BOARD = { x: [56, 57], z0: 18, z1: 30, y0: 14, y1: 22 } // the scoreboard on the east end

// Palette (60/30/10): red brick and stone brick (the outer wall, risers) / smooth stone treads and andesite paving /
// quartz dressings, green copper roofs and the seats' red and white (the village's colours). Why: a brick bowl reads warm
// and civic from outside; the seats are the only bright field inside it.
export const PALETTE = {
  grass: 'grass_block',
  chalk: 'white_concrete',
  paving: [['polished_andesite', 6], ['andesite', 2], ['stone', 1]],
  path: 'smooth_stone',
  riser: [['stone_bricks', 8], ['cracked_stone_bricks', 1]],
  tread: 'smooth_stone',
  barrier: 'polished_blackstone_bricks',
  barrierCap: 'polished_blackstone_brick_slab',
  brick: 'bricks',
  plinth: 'stone_bricks',
  course: 'smooth_quartz',
  cornice: 'smooth_quartz_stairs',
  coping: 'smooth_quartz_slab',
  sill: 'smooth_quartz_slab',
  pier: 'quartz_pillar',
  pierCap: 'chiseled_quartz_block',
  arch: 'brick_stairs',
  archQuartz: 'smooth_quartz_stairs',
  glass: 'glass_pane',
  door: 'spruce_door',
  canopy: 'waxed_oxidized_cut_copper', // green with age: the roofs read against the red brick
  canopySlab: 'waxed_oxidized_cut_copper_slab',
  canopyStair: 'waxed_oxidized_cut_copper_stairs',
  red: 'red_carpet',
  white: 'white_carpet',
  banner: 'red_banner',
  wallBanner: 'red_wall_banner',
  awayBanner: 'white_wall_banner',
  awningRed: 'red_wool',
  awningWhite: 'white_wool',
  cross: 'red_concrete',
  crossField: 'white_concrete',
  crestRed: 'red_concrete',
  crestBand: 'black_concrete',
  crestWhite: 'white_concrete',
  attic: 'smooth_quartz', // the lettered field (its back is brick)
  letter: 'polished_blackstone_bricks',
  crestGround: 'polished_blackstone_bricks',
  board: 'black_concrete',
  boardFrame: 'polished_blackstone_bricks',
  boardCap: 'polished_blackstone_brick_slab',
  digit: 'white_concrete',
  mastShaft: 'iron_block',
  core: 'iron_block',
  lamp: 'redstone_lamp',
  detector: 'daylight_detector',
  wire: 'redstone_wire',
  collar: 'polished_blackstone_bricks',
  bracket: 'polished_blackstone_brick_stairs',
  aisle: 'stone_brick_stairs',
  counter: 'smooth_stone_slab',
  nurseCounter: 'smooth_quartz_slab',
  bench: 'spruce_slab',
  locker: 'spruce_trapdoor',
  pot: 'potted_red_tulip',
  fern: 'potted_fern',
  tableLeg: 'spruce_fence',
  tableTop: 'spruce_pressure_plate',
  stool: 'spruce_stairs',
  cake: 'cake',
  candle: 'white_candle',
  post: 'aliveworkplace:training_post',
  healing: 'cobblemon:healing_machine',
  brewing: 'brewing_stand',
}

// 3 x 5 letters and digits, M five wide (rows top to bottom, as read)
export const GLYPHS = {
  S: ['XXX', 'X..', 'XXX', '..X', 'XXX'],
  T: ['XXX', '.X.', '.X.', '.X.', '.X.'],
  A: ['.X.', 'X.X', 'XXX', 'X.X', 'X.X'],
  D: ['XX.', 'X.X', 'X.X', 'X.X', 'XX.'],
  I: ['X', 'X', 'X', 'X', 'X'],
  U: ['X.X', 'X.X', 'X.X', 'X.X', 'XXX'],
  M: ['X...X', 'XX.XX', 'X.X.X', 'X...X', 'X...X'],
  2: ['XXX', '..X', 'XXX', 'X..', 'XXX'],
  1: ['.X.', 'XX.', '.X.', '.X.', 'XXX'],
  '-': ['...', '...', 'XXX', '...', '...'],
}
