// The Inn (Version B, ROADMAP 49), the plan: drawn before any wall. Rooms, every furniture footprint (kind, cells,
// facing) and the walking line first; recipe.mjs then assembles it from the parts library (../parts) in passes.
//
// Three rough concepts (massing, roof, plan, story):
//   A. The Long Roadhouse: one broad two-storey range along the street, a catslide roof sweeping down the back over a
//      lean-to taproom, the guest rooms in three dormers. Story: a farmhouse that took in travellers. Calm, but it is
//      what every inn already looks like: a house with a sign.
//   B. The Arcade Inn: a tall gable turned to the street, its timbered guest storey jettied out over a stone taproom
//      and a covered arcade on posts where arrivals wait out of the rain; a stone chimney climbing the side wall.
//      II grows it into a coaching inn: a timber coach gate beside it, a smaller stable gable beyond, a walled yard
//      behind. III digs the beer cellar under the taproom, reached through a hatch behind the bar. Story: a market
//      town's inn that kept its street arcade when the coaches came.
//   C. The Waymark Tower: a squat stone tower of guest rooms over a low timber hall, a beacon lantern on the roof for
//      travellers at night. Story: an old watch tower turned inn. Strong silhouette, but the six beds must share one
//      floor, so the tower is mostly empty above them, and a tower leaves little to grow in II.
//   Strongest: B. Its one idea does three jobs: the arcade is the inn's porch, its arrival room and its signboard
//   (a deep shadowed band at the foot of a bright gable reads "public house" from down the street), the jetty gives the
//   front real depth, and II grows out of it the way real coaching inns did (a gate, a stable, a yard) instead of
//   adding a random wing. The gable-to-street front is the opposite of the broad-fronted house (A) the obvious inn is.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Front door on the front wall (z = 2) at x = 6, on the counter's floor (y = 1).
//
// Masses (named parts the owner's feedback can point at):
//   Taproom     stone ground storey, walls x 1..11, z 2..11, room y 1..4 (a tall public room), floor y 0.
//   Arcade      the covered walk along the front, z 0..1: posts on z 0 at x 1, 5, 7, 11, a beam at y 4 with arch
//               brackets in the two wide bays, a pentice of tiles at y 5, a step row on z 0, benches under the windows.
//   Guest storey the timbered upper floor (floor y 5, rooms y 6..8, ceiling y 9), jettied one block out over the
//               arcade to z = 1 on brackets: three twin rooms and a landing.
//   Gable       the hero: a 45-degree tiled roof, ridge along z at y 15, its studded gable to the street.
//   Chimney     a stone stack up the west wall (x 1, z 5..7) from the inglenook to y 14, a campfire on top.
//   Coach gate  (II) a timber gateway across the passage x 12..18 to the yard, its beam at y 4, a slab cap.
//   Stable      (II) a smaller gable to the street, walls x 18..24, z 2..11 (its eave two blocks clear of the inn's), boards in a dark frame, a hayloft door
//               with a hoist lantern, three stalls inside.
//   Yard        (II) a walled coach yard behind, z 12..18, a woodpile, hay, a trough, a cart; a back door from the bar.
//   Cellar      (III) the beer cellar under the taproom, 4 deep, casks round the walls, a hatch behind the bar.
//
// The brief's pinned cells, all met as drawn: the Shop Counter at (8, 1, 7) facing west, the walk-in cell (9, 1, 7)
// and (9, 2, 7) air, (7, 1, 7) air, (8, 1, 6) and (8, 1, 8) dark oak planks; the front door at (6, 1, 2) = (min x + 6,
// counter y, min z + 2); the six beds on the guest floor at y 6 = counter y + 5; no campfire east of x = 11.

export const IDS = { 1: 'inn', 2: 'inn_2', 3: 'inn_3' }
export const SIZES = { 1: [13, 16, 13], 2: [26, 16, 19], 3: [26, 16, 19] }
export const BASEMENT = { 3: 4 }

// Palette (60/30/10): grey stone and white lime plaster (the walls) / dark oak frame and red clay tiles (the timbers
// and the roof) / spruce joinery and red cloth (doors, shutters, furniture, the banner, the armchairs). Why: the stone
// storey and the shadowed arcade are the dark base, the white gable the bright hero above them, and the red tile roof
// is the inn's colour seen from across the village (a slate roof was tried: on a 13-wide gable it read as one dark
// mass). Red comes back small inside: the banner, the rugs, the hearth chairs.
export const PALETTE = {
  stone: [['stone_bricks', 6], ['cobblestone', 3], ['andesite', 1], ['cracked_stone_bricks', 1]],
  plinth: [['cobblestone', 4], ['mossy_cobblestone', 2], ['stone', 1]],
  steps: [['cobblestone_stairs', 3], ['mossy_cobblestone_stairs', 1]],
  quoin: 'polished_andesite',
  dressed: 'stone_bricks',
  paving: 'polished_andesite',
  arcadeStep: 'stone_brick_stairs',
  frame: 'dark_oak_log',
  infill: [['white_concrete', 6], ['polished_diorite', 1]], // lime plaster
  roof: { full: 'bricks', stairs: 'brick_stairs', slab: 'brick_slab' },
  floor: 'spruce_planks',
  ceiling: 'spruce_planks',
  bar: 'dark_oak_planks', // pinned by the brief: the bar either side of the Shop Counter
  wood: 'spruce', // doors, shutters, benches, chairs, partitions' doors
  trim: 'dark_oak', // brackets, mouldings, barge boards
  partition: [['white_concrete', 6], ['polished_diorite', 1]],
  boards: 'spruce_planks', // the stable's walls
  glass: 'glass_pane',
  cellarWall: [['cobblestone', 3], ['stone_bricks', 3], ['mossy_stone_bricks', 1]],
  cellarFloor: 'polished_andesite',
}

export const MASSES = {
  taproom: { x0: 1, x1: 11, z0: 2, z1: 11, top: 4 },
  upper: { x0: 1, x1: 11, z0: 1, z1: 11, floorY: 5, top: 8, beamY: 9 },
  arcade: { x0: 1, x1: 11, z: 0, posts: [1, 5, 7, 11], beamY: 4 },
  roof: { axis: 'z', across: [0, 12], along: [0, 12], eaveY: 9, steep: false },
  chimney: { x: 1, zs: [5, 6, 7], fire: [1, 1, 6], top: 14 },
  // Level II
  gate: { x0: 12, x1: 18, z: 1 },
  stable: { x0: 18, x1: 24, z0: 2, z1: 11, top: 4, beamY: 5, roof: { axis: 'z', across: [17, 25], along: [1, 12], eaveY: 5, steep: false } },
  yard: { x0: 0, x1: 25, z0: 12, z1: 18 },
  // Level III
  cellar: { x0: 1, z0: 2, x1: 11, z1: 11, depth: 4, ladder: { x: 10, z: 4, facing: 'west' } },
}

export const ROOMS = [
  { name: 'Taproom', use: 'the bar, the inglenook, three tables', box: [2, 1, 3, 10, 4, 10] },
  { name: 'Bar', use: 'the Shop Counter, the walk-in, casks and the kitchen corner behind', box: [8, 1, 4, 10, 4, 10] },
  { name: 'Street Room West', use: 'twin room over the arcade', box: [2, 6, 2, 4, 8, 5] },
  { name: 'Street Room East', use: 'twin room over the arcade', box: [8, 6, 2, 10, 8, 5] },
  { name: 'Yard Room', use: 'twin room at the back', box: [8, 6, 7, 10, 8, 10] },
  { name: 'Landing', use: 'the stair head, a settle by the chimney', box: [2, 6, 7, 6, 8, 10] },
  { name: 'Stable', use: '(II) three stalls and a hayloft', box: [19, 1, 3, 23, 4, 10] },
  { name: 'Cellar', use: '(III) casks and barrels under the taproom', box: [2, -3, 3, 10, -1, 10] },
]

const W = (name, cells, out, from = 1) => ({ name, cells, out, dress: 'shutters', from })
const G = (name, cells, out, from = 1) => ({ name, cells, out, dress: null, from }) // the gable ends: broad glazed bays
const col = (x, ys, z) => ys.map(y => [x, y, z])
const row = (xs, ys, z) => xs.flatMap(x => ys.map(y => [x, y, z]))
export const OPENINGS = {
  frontDoor: { at: [6, 1, 2], facing: 'south' },
  backDoor: { at: [7, 1, 11], facing: 'north', from: 2 },
  stableDoor: { at: [21, 1, 2], facing: 'south' },
  stableBackDoor: { at: [21, 1, 11], facing: 'north' },
  hayDoor: { at: [21, 6, 2], facing: 'south' },
  // the stair: along the back wall rising east, its top step in the corridor; the well over steps y 2..4
  stairs: { flight: [[2, 1, 10], [3, 2, 10], [4, 3, 10], [5, 4, 10], [6, 5, 10]], rises: 'east', well: [[3, 5, 10], [4, 5, 10], [5, 5, 10]] },
  railing: [[2, 6, 9], [5, 6, 9]],
  partitions: [ // upper floor: plaster panels y 6..7 in a dark frame (partitionPosts), a beam at y 8; doors in them
    { from: [5, 2], to: [5, 6] }, { from: [2, 6], to: [4, 6] },
    { from: [7, 2], to: [7, 10] }, { from: [8, 6], to: [10, 6] },
  ],
  roomDoors: [{ at: [5, 6, 3], facing: 'east' }, { at: [7, 6, 3], facing: 'west' }, { at: [7, 6, 8], facing: 'west' }],
  // the partitions' timber frame: posts at their ends, corners and door jambs (y 6..7), a beam along their top (y 8)
  partitionPosts: [[5, 2], [5, 4], [5, 6], [7, 2], [7, 4], [7, 6], [7, 7], [7, 9], [7, 10]],
  // windows: the gable ends (front and back) glazed across each bay, a window box on the pentice in front; the long
  // sides shuttered (the shutters stand on the skirt and the moulding)
  windows: [
    G('taproom front west', row([2, 3, 4], [2, 3], 2), 'north'),
    G('taproom front east', row([8, 9, 10], [2, 3], 2), 'north'),
    W('taproom west front', [[1, 2, 3], [1, 2, 4], [1, 3, 3], [1, 3, 4]], 'west'),
    W('taproom west back', [[1, 2, 8], [1, 2, 9], [1, 3, 8], [1, 3, 9]], 'west'),
    W('taproom east front', [[11, 2, 3], [11, 3, 3]], 'east'),
    W('taproom east sink', [[11, 2, 8], [11, 2, 9], [11, 3, 8], [11, 3, 9]], 'east'),
    W('taproom back west', col(2, [2, 3], 11), 'south'),
    W('taproom back east', col(9, [2, 3], 11), 'south'),
    G('street room west', row([2, 3, 4], [6, 7], 1), 'north'),
    G('corridor end', col(6, [6, 7], 1), 'north'),
    G('street room east', row([8, 9, 10], [6, 7], 1), 'north'),
    W('street room west side', [[1, 6, 3], [1, 6, 4], [1, 7, 3], [1, 7, 4]], 'west'),
    W('landing side', [[1, 6, 8], [1, 6, 9], [1, 7, 8], [1, 7, 9]], 'west'),
    W('street room east side', [[11, 6, 3], [11, 6, 4], [11, 7, 3], [11, 7, 4]], 'east'),
    W('yard room side', [[11, 6, 8], [11, 6, 9], [11, 7, 8], [11, 7, 9]], 'east'),
    G('landing back', row([2, 3, 4], [6, 7], 11), 'south'),
    G('corridor back', col(6, [6, 7], 11), 'south'),
    G('yard room back', row([8, 9, 10], [6, 7], 11), 'south'),
  ],
  gableWindows: [[5, 11, 1], [7, 11, 1]], // two small attic lights beside the king post
  sills: [[2, 1, 12, 'potted_cornflower'], [9, 1, 12, 'potted_poppy']], // the back windows' sills, in the skirt
  dormer: { face: 11, zc: 6, dir: 1 }, // on the east slope, between the guest floor's two side windows
  hatch: { at: [10, 0, 4], from: 3 }, // III only: the trapdoor over the cellar ladder (in II a floor board)
}

// Every piece of furniture, footprint first (kind: a furniture.py kind, or a fixture the game uses as itself).
// facing: the way a person using it faces. Chairs face their table.
const F = []
const add = (room, part, kind, pos, facing, extra = {}) => F.push({ room, part, kind, pos, facing, wood: 'spruce', ...extra })
// Taproom: two tables for two and a long one for four; the inglenook's two red armchairs either side of the fire
add('Taproom', 'table', 'table', [3, 1, 4], 'north', { colour: 'red' })
add('Taproom', 'chair', 'chair', [2, 1, 4], 'east')
add('Taproom', 'chair', 'chair', [4, 1, 4], 'west')
add('Taproom', 'table', 'table', [5, 1, 5], 'north', { colour: 'white' })
add('Taproom', 'table', 'table', [5, 1, 6], 'north', { colour: 'white' })
add('Taproom', 'chair', 'chair', [4, 1, 5], 'east')
add('Taproom', 'chair', 'chair', [4, 1, 6], 'east')
add('Taproom', 'chair', 'chair', [6, 1, 5], 'west')
add('Taproom', 'chair', 'chair', [6, 1, 6], 'west')
add('Taproom', 'table', 'table', [5, 1, 8], 'north', { colour: 'red' })
add('Taproom', 'chair', 'chair', [4, 1, 8], 'east')
add('Taproom', 'chair', 'chair', [6, 1, 8], 'west')
add('Taproom', 'armchair', 'chair', [2, 1, 5], 'south', { wood: 'dark_oak', colour: 'red' })
add('Taproom', 'armchair', 'chair', [2, 1, 7], 'north', { wood: 'dark_oak', colour: 'red' })
add('Taproom', 'rug', 'rug', null, null, { cellsAbs: [[2, 1, 6], [3, 1, 6]], colour: 'red' })
add('Taproom', 'hearth', 'fixture', [1, 1, 6], 'east')
add('Taproom', 'clock', 'clock', [5, 1, 3], 'south')
add('Taproom', 'picture', 'picture', [4, 3, 3], 'south', { colour: 'green' })
add('Taproom', 'hangingLamp', 'lamp', [3, 4, 4], 'north')
add('Taproom', 'hangingLamp', 'lamp', [5, 4, 6], 'north')
add('Taproom', 'hangingLamp', 'lamp', [3, 4, 8], 'north')
// Bar: the counter (pinned), casks on the back wall, the kitchen corner (counters, a sink, the smoker, crockery shelves)
add('Bar', 'hangingLamp', 'lamp', [8, 4, 7], 'west')
add('Bar', 'counter', 'counter', [10, 1, 7], 'east')
add('Bar', 'counter', 'counter', [10, 1, 9], 'east')
add('Bar', 'stove', 'fixture', [10, 1, 10], 'west', { block: 'smoker' })
add('Bar', 'shelf', 'shelf', [10, 3, 7], 'west', { crockery: 'flower_pot' })
add('Bar', 'shelf', 'shelf', [10, 3, 10], 'west', { crockery: 'potted_red_mushroom' })
add('Bar', 'hangingLamp', 'lamp', [9, 4, 9], 'west')
// Street Room West (over the arcade): one bed head to the front window, one head to the partition, each with its own
// nightstand; a chest under a picture, a rug between the beds
add('Street Room West', 'bed', 'bed', [2, 6, 3], 'north', { colour: 'red' })
add('Street Room West', 'bed', 'bed', [4, 6, 4], 'south', { colour: 'red' })
add('Street Room West', 'nightstand', 'nightstand', [3, 6, 2], 'south')
add('Street Room West', 'nightstand', 'nightstand', [3, 6, 5], 'north')
add('Street Room West', 'chest', 'fixture', [2, 6, 5], 'north')
add('Street Room West', 'blinds', 'blinds', [3, 8, 2], 'south')
add('Street Room West', 'picture', 'picture', [2, 7, 5], 'north', { colour: 'light_blue' })
add('Street Room West', 'hangingLamp', 'lamp', [3, 8, 4], 'north')
add('Street Room West', 'rug', 'rug', null, null, { cellsAbs: [[3, 6, 3], [3, 6, 4]], colour: 'light_gray' })
// Street Room East: the same, mirrored
add('Street Room East', 'bed', 'bed', [10, 6, 3], 'north', { colour: 'blue' })
add('Street Room East', 'bed', 'bed', [8, 6, 4], 'south', { colour: 'blue' })
add('Street Room East', 'nightstand', 'nightstand', [9, 6, 2], 'south')
add('Street Room East', 'nightstand', 'nightstand', [9, 6, 5], 'north')
add('Street Room East', 'chest', 'fixture', [10, 6, 5], 'north')
add('Street Room East', 'blinds', 'blinds', [9, 8, 2], 'south')
add('Street Room East', 'picture', 'picture', [10, 7, 5], 'north', { colour: 'yellow' })
add('Street Room East', 'hangingLamp', 'lamp', [9, 8, 4], 'north')
add('Street Room East', 'rug', 'rug', null, null, { cellsAbs: [[9, 6, 3], [9, 6, 4]], colour: 'light_gray' })
// Yard Room (back east): beds heads to the east wall, each with its nightstand under the window, a desk and its chair,
// a chest by the door
add('Yard Room', 'bed', 'bed', [9, 6, 7], 'east', { colour: 'green' })
add('Yard Room', 'bed', 'bed', [9, 6, 10], 'east', { colour: 'green' })
add('Yard Room', 'nightstand', 'nightstand', [10, 6, 8], 'west')
add('Yard Room', 'nightstand', 'nightstand', [10, 6, 9], 'west')
add('Yard Room', 'blinds', 'blinds', [10, 8, 9], 'west')
add('Yard Room', 'desk', 'desk', [8, 6, 10], 'south')
add('Yard Room', 'chair', 'chair', [8, 6, 9], 'south')
add('Yard Room', 'chest', 'fixture', [8, 6, 7], 'east')
add('Yard Room', 'hangingLamp', 'lamp', [9, 8, 8], 'north')
// Landing: a settle against the chimney breast, a little table, the long-case clock, a picture, a shelf
add('Landing', 'couch', 'couch', [2, 6, 7], 'east', { length: 2, colour: 'red' })
add('Landing', 'table', 'table', [3, 6, 7], 'north', { colour: 'brown' })
add('Landing', 'clock', 'clock', [4, 6, 7], 'south')
add('Landing', 'picture', 'picture', [5, 7, 7], 'south', { colour: 'red' })
add('Landing', 'shelf', 'shelf', [2, 7, 7], 'east', { candle: true })
add('Landing', 'hangingLamp', 'lamp', [4, 8, 8], 'north')
add('Landing', 'hangingLamp', 'lamp', [6, 8, 5], 'north')
export const FURNITURE = F

// The walking line: the arcade step -> front door -> along the bar front -> round the bar's north end into the walk-in
// (9, 1, 7); and from the door down the aisle (x 7, then z 9) to the stair foot (2, 1, 9), up to the landing, along the
// corridor (x 6) to the three room doors. II: the back door (7, 1, 11) to the yard, the coach gate (x 13..17) from the
// street. III: behind the bar to the hatch (10, 0, 4) and down the ladder.
export const WALK = [
  [6, 0, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [7, 1, 3], [7, 1, 4], [8, 1, 4], [9, 1, 4], [9, 1, 5], [9, 1, 6], [9, 1, 7],
  [7, 1, 5], [7, 1, 9], [6, 1, 9], [3, 1, 9], [2, 1, 9], [2, 1, 10], [3, 2, 10], [4, 3, 10], [5, 4, 10], [6, 5, 10],
  [6, 6, 9], [6, 6, 3], [5, 6, 3], [7, 6, 3], [6, 6, 8], [7, 6, 8],
]

// Outside (not furniture spots): the arcade's benches and lanterns, the banner on its post by the corner, II's yard.
export const OUTSIDE = {
  benches: [[[2, 1, 1], [3, 1, 1], [4, 1, 1]], [[8, 1, 1], [9, 1, 1], [10, 1, 1]]],
  arcadeLamps: [[3, 4, 1], [9, 4, 1], [6, 3, 0]],
  banner: { post: [12, 1, 0], height: 2, colour: 'red', rotation: 8 },
  pots: { 'street room west': ['potted_poppy', 'potted_azure_bluet', 'potted_poppy'], 'corridor end': ['potted_red_tulip'], 'street room east': ['potted_poppy', 'potted_azure_bluet', 'potted_poppy'] },
  // II
  yardWall: [[0, 18], [23, 18]],
  woodpile: { x: 1, z0: 13, z1: 16 },
  hay: [[23, 1, 13], [24, 1, 13], [24, 1, 14], [24, 2, 13]],
  trough: [[19, 1, 13], [20, 1, 13]],
  cart: { x: 9, z: 15 },
  yardLamp: [11, 0, 17],
}
