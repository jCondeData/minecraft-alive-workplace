// The Market Square (`market_square`, one level), the plan: drawn before any block. ROADMAP 49 (rebuild); the brief is
// tools/blueprints/briefs/market_square.md. recipe.mjs assembles it from the parts library (../parts).
//
// Three rough concepts (massing, roof/canopy, plan; one line of story each):
//   A. The Market House. A paved square whose back third is a timber Market House standing on an open stone
//      undercroft: arcaded piers carry a jettied, white-plastered hall under a steep red-tile roof with a lantern
//      cupola on its ridge; a little proclamation balcony juts from the hall's front right over the square's middle,
//      reached by an outside stone stair up the east side; a striped stall leans on the west edge. Story: the English
//      market house (Ledbury, Thaxted): the butter market trades in the shade underneath, the reeve keeps the weights
//      and the market court upstairs, and on market morning he steps out on the balcony to open the market.
//   B. Four Stalls and a Plane Tree. An open paved square with a striped stall in each corner and a big tree in a
//      round seat in the middle. Story: the village green on market day. It reads as a market at once, but it is the
//      square every village has; nothing taller than a stall, and the tree's trunk sits on the meeting point.
//   C. The Cloister Market. A U of low lean-to arcades round three sides (stalls rented by the arch), open to the road,
//      a pump in the middle. Story: a monastery's market court. Calm and orderly, but in a 2-block ring the arcade is a
//      tunnel, and from outside it is three low walls: a weak silhouette from the back and sides.
// Strongest: A. It is the only one with a shape you'd recognise from across the village (a tall tiled roof and a
// cupola on stilts over open ground), and it keeps the square open: the whole hall stands on piers, so the traders'
// 9 x 9 loses only two pier cells. The balcony puts the market's "opening" right over its middle (the bell's sound,
// the banquet's smoke). B is the safe one; A is the strong one.
//
// Coordinates: x across the front (west 0 -> east 12), y up (0 = the paving, on the clicked block; people stand at
// y = 1), z from the front (z = 0, north, the road) to the back. The middle (what MarketDays.square reads): (6, 1, 6).
// The traders' 9 x 9: x 2..10, z 2..10, ground at y = 0, open to y = 3 (the hall's floor is at y = 4).
//
// Masses (named parts the owner's feedback can point at):
//   Paving      y 0, the whole 13 x 13: gathered stone, a ring of stone brick at 4 out (the traders' square), a
//               rosette round the middle; the front row a half step (slabs) from the road; grass beds in the two
//               front corners, each with a flowering azalea and flowers.
//   Undercroft  x 1..11, z 8..11: stone piers at x 1, 4, 8, 11 on the front (z 8) and back (z 11) lines, y 1..3, a
//               dark oak girder over each line at y 4, an arch (upside-down stairs) in each 3-wide middle opening;
//               benches in the back's side bays; lanterns hung from the floor.
//   Hall        the upper floor, y 4 floor, walls y 5..7, x 1..11, z 7..11: jettied out over the front piers on corbels,
//               its sill a block proud of the walls (front z 6, back z 12, west x 0; corbels under it at the
//               pier lines) so every shutter and the bunting stand on it; dark oak posts at x 1, 4, 8, 11 and every corner, plaster panels, a beam ring at y 8.
//               Front: two shuttered windows and a glazed door to the balcony; back: three shuttered windows; west
//               gable a window, east gable the door from the outside stair.
//   Roof        a steep red-tile gable (bricks), ridge along x at z 9 (y 13), eaves at y 8 a block out over z 6 and
//               z 12, the rakes a block out at x 0 and x 12 with barge boards; plastered gables with a king post and a
//               slit window.
//   Front gable a cross gable over the middle bay (x 3..9), the same steep pitch, its face on the front wall (z 7)
//               over the balcony door with a king post and a slit window, its rake a block out over the balcony
//               (z 6), its ridge (x 6) running back into the main ridge at y 13: the hero face of the square.
//   Cupola      on the ridge's middle: a 3 x 3 base, four posts, a hanging lantern, a tile cap (y 13..15): the hero.
//   Balcony     x 5..7, z 4..5 out from the sill, floor at y 4 on two corbels, a fence railing; the market banner hangs from its
//               front, a lantern hangs under it over the middle.
//   Stair       the outside stair up the east edge: x 12, steps at z 5..7 (y 1..3), a landing at y 4 over z 8..10
//               on a stone base, a lamp post at its end, the east door at (11, 5, 9).
//   Stall       the west edge, x 0..1, z 2..5: four fence posts, a counter with produce, hay behind, a striped
//               canvas sloping down toward the square.
//   Lamps       two lamp standards at the way in (2, 1, 1) and (10, 1, 1).
//   Bunting     red and yellow wall banners hung from the jetty sill's front (z 5).
//
// Rooms: the Market Court (the hall, x 2..10, z 8..10, y 5..7): the reeve's table with three chairs facing the
// balcony door, two cupboards (the standard weights) either side of the west window, a desk and a table lamp by
// the east door, a runner rug along the front aisle, two lamps hung from the tie beams, two cloths on the back posts.
// Openings: the balcony door (6, 5, 7), the east door (11, 5, 9); windows in every bay but the middle front.
// Walking line: road (6, 0, -1) -> the slab step (6, 0, 0) -> across the square to the middle (6, 1, 6) -> under the
// hall to the benches; or east to (12, 1, 4) -> up the stair (12, 1..3, 5..7) -> landing (12, 4, 8) -> east door ->
// the aisle (z 8) -> the balcony door -> the balcony (6, 5, 5).

export const SIZE = [13, 16, 13] // w, h, d (brief: at most 13 x 16 x 13). Ship step: StarterBlueprints.MARKET_SQUARE
// (13, 5, 13) becomes new Vec3i(13, 16, 13); the middle MarketDays reads stays (6, 1, 6) (the centre in x and z, y 1).

// Palette (60/30/10): gathered gray stone (paving, piers, the stair) / dark oak frame over white plaster and spruce
// floors (the hall) / a red-tile brick roof and the market's red and yellow (canvas, banners), lanterns, azaleas.
// Why: the stone of the square runs up into the piers so the hall seems to stand on the square itself; the roof is
// the one warm mass, seen from across the village.
export const PALETTE = {
  paving: [['cobblestone', 4], ['andesite', 3], ['stone', 2], ['mossy_cobblestone', 1]],
  ring: 'stone_bricks',
  rosette: 'polished_andesite',
  heart: 'chiseled_stone_bricks',
  step: 'stone_brick_slab',
  bed: 'moss_block', // the corner beds' soil (flowers and the azalea root in it)
  bush: 'flowering_azalea',
  flowers: ['cornflower', 'poppy', 'dandelion', 'oxeye_daisy'],
  pier: [['stone_bricks', 6], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1]],
  arch: 'stone_brick_stairs',
  girder: 'stripped_dark_oak_log',
  frame: 'dark_oak_log',
  corbel: 'dark_oak_stairs',
  floor: 'spruce_planks',
  plaster: [['white_concrete', 5], ['polished_diorite', 1]],
  glass: 'glass_pane',
  shutter: 'spruce',
  door: 'spruce',
  roof: { full: 'bricks', stairs: 'brick_stairs', slab: 'brick_slab' },
  barge: 'dark_oak_stairs',
  cupola: { base: 'dark_oak_planks', post: 'dark_oak_fence' },
  rail: 'dark_oak_fence',
  stairStone: 'stone_brick_stairs',
  stairBase: [['stone_bricks', 5], ['cobblestone', 2]],
  lampPost: 'dark_oak_fence',
  canvas: ['red_wool', 'yellow_wool'],
  counter: 'stripped_spruce_log',
  produce: ['pumpkin', 'melon'],
  hay: 'hay_block',
  bench: 'spruce_stairs',
  banners: ['red_wall_banner', 'yellow_wall_banner'],
  flag: 'red_wall_banner',
  wood: 'spruce', // furniture
}

export const MASSES = {
  middle: [6, 6],
  paving: { ringAt: 4, frontStep: { z: 0, x0: 2, x1: 10 }, beds: [{ x0: 0, x1: 1, z0: 0, z1: 1, bush: [0, 0] }, { x0: 11, x1: 12, z0: 0, z1: 1, bush: [12, 0] }] },
  undercroft: { piers: [1, 4, 8, 11], lines: [8, 11], top: 4, lamps: [[3, 3, 9], [9, 3, 9], [6, 3, 10]], benches: [[2, 11], [3, 11], [9, 11], [10, 11]] },
  hall: { x0: 1, x1: 11, z0: 7, z1: 11, floorY: 4, y0: 5, y1: 7, beamY: 8, posts: [1, 4, 8, 11], corbels: [1, 4, 8, 11], tieBeams: [4, 8] },
  roof: { across: [6, 12], along: [0, 12], eaveY: 8 },
  cupola: { x: 6, z: 9 },
  frontGable: { across: [3, 9], along: [6, 9] }, // the cross gable over the balcony bay: same pitch, its ridge meets the main one
  balcony: { x0: 5, x1: 7, z0: 4, z1: 6, y: 4, corbels: [5, 7] },
  stair: { x: 12, steps: [[1, 5], [2, 6], [3, 7], [4, 8]], landing: { y: 4, z0: 9, z1: 10 }, lamp: [12, 5, 10], door: [11, 5, 9] },
  stall: { x0: 0, x1: 1, z0: 2, z1: 5 },
  lamps: [[2, 1], [10, 1]],
  bunting: [2, 3, 9, 10], // x on the jetty sill's front face (z 5, y 4)
}

export const OPENINGS = {
  front: { windows: [[2, 3], [9, 10]], door: 6, panes: [5, 7] }, // z 7, y 5..6
  back: { windows: [[2, 3], [5, 7], [9, 10]] }, // z 11
  west: { window: 9 }, // x 1
  east: { door: 9 }, // x 11
}

// Every furniture piece of the Market Court (furniture.mjs parts; furniture.py kinds)
const W = PALETTE.wood
export const FURNITURE = [
  ...[5, 6, 7].map(x => ({ room: 'court', part: 'table', pos: [x, 5, 9], facing: 'north', wood: W, colour: 'green' })),
  ...[5, 6, 7].map(x => ({ room: 'court', part: 'chair', pos: [x, 5, 10], facing: 'north', wood: W })),
  { room: 'court', part: 'cupboard', pos: [2, 5, 8], facing: 'east', wood: W },
  { room: 'court', part: 'cupboard', pos: [2, 5, 10], facing: 'east', wood: W },
  { room: 'court', part: 'desk', pos: [10, 5, 10], facing: 'west', wood: W },
  { room: 'court', part: 'tableLamp', pos: [10, 5, 8], facing: 'west', wood: W },
  { room: 'court', part: 'rug', cellsAbs: [3, 4, 5, 7, 8, 9].map(x => [x, 5, 8]), colour: 'red' },
  { room: 'court', part: 'hangingLamp', pos: [4, 7, 9], facing: 'north', wood: W },
  { room: 'court', part: 'hangingLamp', pos: [8, 7, 9], facing: 'north', wood: W },
  { room: 'court', part: 'picture', pos: [4, 6, 10], facing: 'north', wood: W, colour: 'yellow' },
  { room: 'court', part: 'picture', pos: [8, 6, 10], facing: 'north', wood: W, colour: 'red' },
]
