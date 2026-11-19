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
//      width round the straights and the curved ends), a lower rake of red and white seats all round, a walk, an upper
//      rake inside a red-brick outer wall with an arcade of arches, a copper canopy over the
//      back straight, a gate tower over the one cut through the bowl at the front, and four floodlight masts standing
//      in the four corners the round ends leave free. Story: the whole village sits round the two trainers.
//   Strongest: C. It reads as a stadium from any side (the rings are the shape), every seat faces the ring, the four
//   corners outside the curves are exactly where floodlights stand on a real ground, and its four stages are four
//   different things: the field, the lower bowl, the upper bowl and its concourse, then the canopy, gate and lights.
//   Built: C.
//
// Coordinates: x across the front (west 0 -> east 58), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Plot 59 x 49, 20 high. The ring is (29, 1, 24); the posts (17, 1, 24) and (41, 1, 24).

// Stage titles (player text, set at ship with the test): I "The Field", II "The Lower Stands", III "The Upper Stands and
// the Arcade Wall", IV "The Canopy, the Gate and the Floodlights".
// Description line (at ship): "A brick bowl of red and white seats round a chalked field, with a copper canopy, a gate
// tower and four floodlights."

export const SIZE = [59, 20, 49]
export const STAGES = 4

// The bowl: rings round the spine (29 +-4, z 24); ring r holds the cells r..r+1 from it.
export const BOWL = { cx: 29, cz: 24, half: 4 }
export const RINGS = {
  field: 9, // r <= 9: the grass
  touch: 10, // the white touchline
  barrier: 11, // the pitch wall
  lower: [12, 19], // four rows, two cells deep each: the seat, then the tread behind it
  walk: 20, // the walk between the rakes (the concourse's inner wall below it)
  upper: [21, 23], // three rows, one cell each, over the concourse
  wall: 24, // the outer wall
}
export const GAP = { x0: 27, x1: 31 } // the cut through the front of the bowl (z < 24): the way in from the gate spot
export const WALL_TOP = 12

export const POSTS = [[17, 1, 24], [41, 1, 24]]
export const RING = [29, 1, 24]
export const BOX_SPOTS = [[18, 1, 24], [18, 1, 25], [40, 1, 24], [40, 1, 23]]
export const GATE = [26, 1, -4]

export const MASTS = [[2, 2], [56, 2], [2, 46], [56, 46]]
export const MAST_TOP = 18
// the nurse's booth: an open kiosk outside the bowl in the front-left corner, its back corner on the outer wall
export const BOOTH = {
  x0: 4, x1: 8, z0: 4, z1: 7, posts: [[4, 4], [8, 4], [4, 7]],
  healing: [6, 1, 6], brewing: [5, 1, 6], lamp: [6, 3, 5], counter: [[7, 6], [7, 5]],
}

// Palette (60/30/10): red brick and stone brick (the outer wall, risers) / smooth stone treads and andesite paving /
// quartz courses, copper canopy and the seats' red and white (the village's colours). Why: a brick bowl reads warm and
// civic from outside; the seats are the only bright field inside it.
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
  course: 'smooth_quartz',
  panel: 'chiseled_stone_bricks',
  cornice: 'smooth_quartz_stairs',
  coping: 'smooth_quartz_slab',
  pier: 'quartz_pillar',
  pierCap: 'chiseled_quartz_block',
  arch: 'brick_stairs',
  canopy: 'waxed_cut_copper',
  canopySlab: 'waxed_cut_copper_slab',
  canopyEdge: 'waxed_cut_copper_stairs',
  red: 'red_carpet',
  banner: 'red_banner',
  white: 'white_carpet',
  mastShaft: 'iron_block',
  core: 'iron_block',
  lamp: 'redstone_lamp',
  detector: 'daylight_detector',
  wire: 'redstone_wire',
  collar: 'polished_blackstone_bricks',
  bracket: 'polished_blackstone_brick_stairs',
  aisle: 'stone_brick_stairs',
  boothPost: 'stripped_spruce_log',
  counter: 'smooth_stone_slab',
  post: 'aliveworkplace:training_post',
  healing: 'cobblemon:healing_machine',
  brewing: 'brewing_stand',
}
