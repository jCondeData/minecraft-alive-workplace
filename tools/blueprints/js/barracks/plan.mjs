// The Barracks, the plan: drawn before any wall. Three levels drawn together, each growing out of the last; recipe.mjs
// assembles them from the parts library (../parts) in passes.
//
// Three rough concepts (the design notes start here):
//   A. The Gate Ward. A square stone gate tower on the front line with a vaulted gate passage through it, and behind it
//      a walled training court with the dummy. A barrack range (one squad: grindstones at the door, bunks at the back,
//      a hearth) stands beside the tower, its gable to the street. Roofs: dark slate, the range a 45-degree gable
//      along its length, the tower a glazed watch lantern under a pyramid cap. Story: the town's first guards hold
//      the gate and drill behind it; Level II sets a second range on the tower's other side, the first one drawn again
//      as its mirror image, and the front becomes gable / tower / gable round a closed court: a ward.
//   B. The Drill Hall. One long stone longhouse, bunks down both sides, with a deep open loggia across its front where
//      the dummies stand and the grindstones turn under the eaves; Level II lengthens the hall with a cross-wing.
//      Roof: one big gable with a catslide over the loggia. Story: a militia hall, plain and practical.
//   C. Twin Keeps. A tall narrow tower-house (grindstone below, bunks above, a lookout on top) by a fenced lists;
//      Level II raises a second keep on the far side of the lists, joined by a covered bridge over the way in.
//      Roofs: two steep pyramid caps. Story: a frontier post.
// The strongest is A. It is the only one whose plan is the job: the guards come in under their own watch tower, whet
// at the range door, sleep at the back and spar in a court that the buildings themselves wall in, so the dummy is
// never just furniture in a yard. Its outline is a fort's (a tower between gables) and it reads from 20 blocks away,
// and its upgrade is the most natural one: Level I is visibly half of a symmetric whole, and Level II completes it.
// B is a box with a porch; C needs ladders or tall stairs to every bed and its bridge fights the 13-block height.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Every door and the gate face the z = 0 side; each step or path is at y 0 on z 0.
//
// Masses (named parts the owner's feedback can point at):
//   West range   (I)   stone barrack range: walls x 1..7, z 1..11 (inside x 2..6, z 2..10), floor y 0, room y 1..3,
//                      beam ring y 4, open roof above with tie beams; slate gable roof along z over x 0..8, eaves y 4,
//                      ridge y 8, front and back gables timber-framed. Front door at (4, 1, 1) under a slate hood, a
//                      hearth in the west wall with its chimney up the outside, buttresses either side of it.
//   Gate tower   (I)   x 8..12, z 1..5: the gate passage x 9..11 under a round-headed arch front and back (y 1..3),
//                      a watch room y 5..7, the lookout y 9..10 (a timber lantern of posts and glass), a slate pyramid
//                      cap y 11..12. A ladder up the passage's west wall to both floors.
//   Court        (I)   the training court behind the tower: x 8..11 (I; 8..12 in II), z 6..10, rammed earth and gravel,
//                      the Training Dummy in the middle, an archery butt and a spear rack; walled at z 11 (and at x 12
//                      in Level I) with a crenellated stone wall, lanterns on its merlons.
//   East range   (II)  the West range drawn again, mirrored about x = 10 (x' = 20 - x): walls x 13..19. Its roof's
//                      west eave closes the court's east side; Level I's east court wall goes.
//   Cellar       (III) the armoury and lock-up under the West range: walls x 1..7, z 1..11, floor y -4, down a ladder
//                      under a hatch in the guard room floor at (6, 0, 5).

export const SIZE = { 1: [13, 13, 13], 2: [21, 13, 13], 3: [21, 13, 13] }
export const BASEMENT = 4 // Level III's cellar depth (the file holds it at the bottom: floor at file y 0, ground floor y 4)
export const MIRROR = 20 // the East range is the West range at x' = MIRROR - x

// Palette (60/30/10): pale stone bricks (walls, tower, court wall) / dark deepslate-tile roofs over dark oak and lime
// plaster gables / red cloth (banners, beds) and iron (lanterns, bars). Why: a guard house must look like it can be held: stone
// to the eaves, darkest at the plinth (cobble with moss low down), a roof darker than any wall so the gable / tower /
// gable outline reads at a distance, warm spruce where people live (gables' frames, doors, floors), the plaster gables the lightest thing on it, and the town's red worn
// only where it means something: on the gate and on the guards' beds.
export const PALETTE = {
  plinth: [['cobblestone', 5], ['andesite', 2], ['mossy_cobblestone', 2]],
  wallLow: [['stone_bricks', 6], ['mossy_stone_bricks', 2], ['cracked_stone_bricks', 1]], // the course at y 1
  wall: [['stone_bricks', 10], ['cracked_stone_bricks', 1]],
  quoin: 'polished_andesite', // dressed corners and string courses: a shade darker than the wall
  quoinAlt: 'stone_bricks',
  stoneStairs: 'stone_brick_stairs',
  stoneSlab: 'stone_brick_slab',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  frame: 'dark_oak_log', // gable studs, ties, the lookout's posts
  beam: 'dark_oak_log', // the beam ring
  infill: [['white_concrete', 5], ['polished_diorite', 1]], // lime plaster in the gables, a little patchy
  floor: 'spruce_planks',
  paving: [['stone_bricks', 3], ['cobblestone', 2], ['andesite', 1]], // the gate passage
  court: [['coarse_dirt', 4], ['gravel', 2], ['packed_mud', 1]], // the court's rammed earth
  cellarFloor: [['cobblestone', 3], ['stone', 2]],
  wood: 'spruce',
  glass: 'glass_pane',
  cloth: 'red', // banners and beds: the guard's colour
}

export const MASSES = {
  range: { x0: 1, x1: 7, z0: 1, z1: 11, beamY: 4, ties: [3, 6, 9], roof: { axis: 'z', across: [0, 8], along: [0, 12], eaveY: 4, steep: false } },
  tower: { x0: 8, x1: 12, z0: 1, z1: 5, gate: { cx: 10, half: 1, top: 3 }, floors: [4, 8], lookout: [9, 10], cap: 11 },
  court: { x0: 8, z0: 6, z1: 10, x1: { 1: 11, 2: 12 }, wallZ: 11, wallX1: 12, wallTop: 3 },
  cellar: { x0: 1, z0: 1, x1: 7, z1: 11, depth: 4 },
}

export const ROOMS = [
  { name: 'Guard room', level: 1, use: 'the squad\'s two grindstones either side of the door, gear chests, a table for four', box: [2, 1, 2, 6, 3, 5] },
  { name: 'Bunk room', level: 1, use: 'two bunks with footlockers along the west wall, the hearth, the door to the court', box: [2, 1, 6, 6, 3, 10] },
  { name: 'Watch room', level: 1, use: 'the sergeant\'s table over the gate, the duty roster on the wall', box: [9, 5, 2, 11, 7, 4] },
  { name: 'Lookout', level: 1, use: 'the glazed lantern at the top of the tower', box: [9, 9, 2, 11, 10, 4] },
  { name: 'Court', level: 1, use: 'the Training Dummy, an archery butt and a spear rack', box: [8, 1, 6, 12, 3, 10] },
  { name: 'East guard room', level: 2, use: 'the West guard room mirrored', box: [14, 1, 2, 18, 3, 5] },
  { name: 'East bunk room', level: 2, use: 'the West bunk room mirrored', box: [14, 1, 6, 18, 3, 10] },
  { name: 'Armoury', level: 3, use: 'chests of arms and armour, a fitting table, spear racks', box: [2, -3, 2, 6, -1, 6] },
  { name: 'Lock-up', level: 3, use: 'a barred cell behind an iron door worked by a lever, straw to sleep on', box: [2, -3, 8, 6, -1, 10] },
]

// Openings of one range (the East range is these mirrored).
export const OPENINGS = {
  frontDoor: { at: [4, 1, 1], step: [4, 0, 0] },
  courtDoor: { at: [7, 1, 8] }, // the bunk room's door into the court
  windows: [ // 1 wide x 2 tall, an upside-down stone stair sill outside, every one alike
    { name: 'front west', cells: [[2, 2, 1], [2, 3, 1]], out: 'north' },
    { name: 'front east', cells: [[6, 2, 1], [6, 3, 1]], out: 'north' },
    { name: 'side table', cells: [[1, 2, 4], [1, 3, 4]], out: 'west' },
    { name: 'side bunks', cells: [[1, 2, 8], [1, 3, 8]], out: 'west' },
    { name: 'back west', cells: [[2, 2, 11], [2, 3, 11]], out: 'south' },
    { name: 'back east', cells: [[6, 2, 11], [6, 3, 11]], out: 'south' },
    { name: 'front gable', cells: [[4, 5, 1], [4, 6, 1]], out: 'north', bare: true },
    { name: 'back gable', cells: [[4, 5, 11], [4, 6, 11]], out: 'south', bare: true },
  ],
  buttresses: [[0, 2], [0, 10]], // [x, z] on the west wall, either end of the windows and the chimney
  hearth: { at: [1, 1, 6], chimney: [0, 6], top: 9 }, // the hearth in the west wall between the table and the bunks, its stack up the outside
  // the tower
  ladder: { x: 9, z: 4, y0: 1, y1: 8, facing: 'east' }, // up the gate passage's west wall, through both floors
  towerWindows: [
    { cells: [[10, 5, 1], [10, 6, 1]], out: 'north' },
    { cells: [[10, 5, 5], [10, 6, 5]], out: 'south' },
    { cells: [[8, 5, 3], [8, 6, 3]], out: 'west', bare: true }, // over the ranges' roofs: no sill
    { cells: [[12, 5, 3], [12, 6, 3]], out: 'east', bare: true },
  ],
  hatch: { x: 6, z: 5, facing: 'west' }, // III: the cellar ladder against the range's east wall, the hatch in the guard room floor
}

// Every piece of furniture, footprint first (furniture.mjs's parts; kind: a furniture.py kind, or a fixture). The
// grindstones and the court's dummy are job blocks, not spots. level: the level that adds it; mirror: drawn again in
// the East range at Level II.
export const FURNITURE = [
  // Guard room: a grindstone either side of the door with the squad's gear chest beside each, the table for four
  { room: 'Guard room', kind: 'fixture', part: 'grindstone', pos: [2, 1, 2], facing: 'east', mirror: true },
  { room: 'Guard room', kind: 'fixture', part: 'grindstone', pos: [6, 1, 2], facing: 'west', mirror: true },
  { room: 'Guard room', kind: 'fixture', part: 'chest', pos: [2, 1, 3], facing: 'east', block: 'chest', mirror: true },
  { room: 'Guard room', kind: 'fixture', part: 'chest', pos: [6, 1, 3], facing: 'west', block: 'chest', mirror: true },
  { room: 'Guard room', kind: 'table', part: 'table', pos: [3, 1, 4], facing: 'north', wood: 'spruce', colour: 'brown', mirror: true },
  { room: 'Guard room', kind: 'table', part: 'table', pos: [3, 1, 5], facing: 'north', wood: 'spruce', colour: 'brown', mirror: true },
  { room: 'Guard room', kind: 'chair', part: 'chair', pos: [2, 1, 4], facing: 'east', wood: 'spruce', mirror: true },
  { room: 'Guard room', kind: 'chair', part: 'chair', pos: [2, 1, 5], facing: 'east', wood: 'spruce', mirror: true },
  { room: 'Guard room', kind: 'chair', part: 'chair', pos: [4, 1, 4], facing: 'west', wood: 'spruce', mirror: true },
  { room: 'Guard room', kind: 'chair', part: 'chair', pos: [4, 1, 5], facing: 'west', wood: 'spruce', mirror: true },
  { room: 'Guard room', kind: 'picture', part: 'picture', pos: [2, 3, 3], facing: 'east', wood: 'spruce', colour: 'red', mirror: true },
  { room: 'Guard room', kind: 'picture', part: 'picture', pos: [6, 3, 3], facing: 'west', wood: 'spruce', colour: 'red', mirror: true },
  { room: 'Guard room', kind: 'lamp', part: 'hangingLamp', pos: [4, 3, 3], facing: 'north', wood: 'spruce', mirror: true },
  // Bunk room: two bunks heads to the west wall, a nightstand between, footlockers at their feet, the hearth
  { room: 'Bunk room', kind: 'bed', part: 'bed', pos: [3, 1, 7], facing: 'west', colour: 'red', mirror: true },
  { room: 'Bunk room', kind: 'bed', part: 'bed', pos: [3, 1, 9], facing: 'west', colour: 'red', mirror: true },
  { room: 'Bunk room', kind: 'nightstand', part: 'nightstand', pos: [2, 1, 8], facing: 'east', wood: 'spruce', mirror: true },
  { room: 'Bunk room', kind: 'fixture', part: 'footlocker', pos: [4, 1, 7], facing: 'east', mirror: true },
  { room: 'Bunk room', kind: 'fixture', part: 'footlocker', pos: [4, 1, 9], facing: 'east', mirror: true },
  { room: 'Bunk room', kind: 'clock', part: 'clock', pos: [2, 1, 10], facing: 'east', wood: 'spruce', mirror: true },
  { room: 'Bunk room', kind: 'cupboard', part: 'cupboard', pos: [6, 1, 10], facing: 'west', wood: 'spruce', mirror: true },
  { room: 'Bunk room', kind: 'rug', part: 'rug', cellsAbs: [[3, 1, 10], [4, 1, 10], [5, 1, 10]], colour: 'gray', mirror: true },
  { room: 'Bunk room', kind: 'fixture', part: 'hearth', pos: [1, 1, 6], facing: 'east', mirror: true },
  { room: 'Bunk room', kind: 'lamp', part: 'hangingLamp', pos: [3, 3, 6], facing: 'north', wood: 'spruce', mirror: true },
  { room: 'Bunk room', kind: 'lamp', part: 'hangingLamp', pos: [5, 3, 9], facing: 'north', wood: 'spruce', mirror: true },
  // Watch room: the sergeant's table and chair, the duty roster (a banner) on the front wall, a lamp
  { room: 'Watch room', kind: 'table', part: 'table', pos: [11, 5, 3], facing: 'north', wood: 'spruce', colour: 'gray' },
  { room: 'Watch room', kind: 'chair', part: 'chair', pos: [10, 5, 3], facing: 'east', wood: 'spruce' },
  { room: 'Watch room', kind: 'picture', part: 'picture', pos: [11, 6, 2], facing: 'south', wood: 'spruce', colour: 'white' },
  { room: 'Watch room', kind: 'lamp', part: 'hangingLamp', pos: [10, 7, 4], facing: 'north', wood: 'spruce' },
  // Lookout
  { room: 'Lookout', kind: 'lamp', part: 'hangingLamp', pos: [11, 10, 3], facing: 'north', wood: 'spruce' },
  // Armoury (III): two double chests of arms, the fitting table and its stool, spear racks, the lamp
  { room: 'Armoury', level: 3, kind: 'table', part: 'table', pos: [4, -3, 4], facing: 'north', wood: 'spruce', colour: 'gray' },
  { room: 'Armoury', level: 3, kind: 'chair', part: 'chair', pos: [4, -3, 3], facing: 'south', wood: 'spruce' },
  { room: 'Armoury', level: 3, kind: 'lamp', part: 'hangingLamp', pos: [4, -1, 5], facing: 'north', wood: 'spruce' },
  { room: 'Armoury', level: 3, kind: 'picture', part: 'picture', pos: [4, -2, 2], facing: 'south', wood: 'spruce', colour: 'red' },
  // Lock-up (III)
  { room: 'Lock-up', level: 3, kind: 'lamp', part: 'hangingLamp', pos: [4, -1, 9], facing: 'north', wood: 'spruce' },
]

// The court (job pieces and dressing): the Training Dummy faces the gate (north), so a guard walking in meets it.
export const COURT = {
  dummy: [10, 1, 8],
  butt: { hay: [10, 1, 10], target: [10, 2, 10] }, // an archery butt: a target on a hay bale against the back wall
  rack: [[8, 1, 10], [9, 1, 10]], // spears (lightning rods) in fence uprights against the back wall (II: again, mirrored)
  lamps: [[10, 4, 11], [12, 5, 8]], // in the back wall's middle crenel; on a merlon of Level I's east wall
}

// The walking line: the front step, through the door, past the grindstones and the table, to the bunks and out into the
// court; and through the gate passage straight to the dummy. II: the same mirrored; III: down the hatch ladder.
export const WALK = [
  [4, 0, 0], [4, 1, 1], [4, 1, 2], [4, 1, 3], [5, 1, 4], [5, 1, 5], [5, 1, 6], [5, 1, 7], [5, 1, 8], [6, 1, 8], [7, 1, 8], [8, 1, 8], [9, 1, 8],
  [10, 0, 0], [10, 1, 1], [10, 1, 2], [10, 1, 3], [10, 1, 4], [10, 1, 5], [10, 1, 6], [10, 1, 7],
  [6, 1, 5], [6, -1, 5], [6, -3, 5], [5, -3, 5], [4, -3, 6],
]
