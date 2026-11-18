// Artisan's House I-III (Milestone 49, rebuilt from its brief), the plan: drawn before any wall. Rooms, every furniture
// footprint (kind, cells, facing) and the walking line come first; recipe.mjs then assembles the three levels from the
// parts library (../parts).
//
// Three concepts, roughed out before choosing:
//   A. "The Kiln House". A narrow, tall, two-storey town house with its gable to the street, its timber upper storey
//      jettied out over a stone ground floor, and beside it a round brick bottle kiln whose neck is the house's chimney;
//      a lean-to fuel shed behind the kiln. Roof: one steep tiled gable (ridge along z), the kiln's bottle shoulder and
//      flared neck, the shed's lean-to. Plan: workroom at the front (the kiln's fire mouth opens into it), hall and
//      kitchen behind, a bedroom and a sitting room upstairs. Story: a potter and tile-maker, who fires the village's
//      pots and roof tiles, lives in the house her own kiln made the tiles for.
//   B. "The Long Workshop". A single long low house under one catslide roof, the workshop at one end and the living
//      end at the other, beds in a loft; II lengthens it. Story: a craftsman's longhouse. Calm, but one box with one
//      roof: from the street it is any cottage.
//   C. "The Courtyard L". Two equal wings round a working yard, 45-degree roofs crossing at the corner, a pergola over
//      the yard. Story: a family workshop round its yard. Readable from above, but at grade I it is two small boxes
//      and nothing says who lives there.
// Strongest: A. The bottle kiln is a silhouette no other house in the village has (a round, shouldered, flared shape
// against a sharp tall gable), it is the brief's chimney in a form that tells the story (the house's fire is the
// workshop's fire), and the jettied gable-to-street front gives the narrow house depth on the side the player sees
// first. B is the safest and C the tidiest plan; A is the one you could pick out of a village from a hill.
//
// Levels grow out of each other:
//   I    the Kiln House: the house, the kiln and the fuel shed (2 beds, the front bedroom upstairs).
//   II   + the journeyman's room: a lower framed wing on the back (x 2..6, z 13..16) with the third bed, its steep
//        roof under the house's back gable (same pitch, ridge 6 lower), and a pot-drying yard behind the shed.
//   III  + the clay cellar under the house (4 deep), down a ladder under a hatch in the workroom floor.
//
// Coordinates: x across the front (west 0 -> east 15), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The front door is in the ground floor's front wall (z = 2), under the jetty, a step at z 0.
//
// Masses (named parts the owner's feedback can point at):
//   House     walls x 1..7. Ground floor: rubble-stone walls (z 2..12, y 1..4) with dressed quoins. Upper floor: a
//             dark oak frame with plaster panels (z 1..12, y 5..9), jettied one block out over the street front on
//             carved brackets with a moulding under its sills. Steep tiled roof, ridge along z at x 4, y 16, its eaves
//             at y 9 (the front's pentice runs on as the eave line under the gable).
//   Dormer    a gabled dormer on the long west slope (z 6..8) so that side isn't one dark plane of tiles.
//   Kiln      a brick bottle kiln on the house's east side (x 8..14, z 1..7): a round 7-wide hovel (y 0..3), a stair
//             shoulder into a 5-wide body (y 4..6), a second shoulder into a rounded neck (y 7..10), a flared lip
//             (y 11) and the smoking rim (y 12); two dark hoops bind it. A brick breast bonds it to the house wall.
//   Shed      the fuel and clay shed behind the kiln (x 8..10, z 8..12), a lean-to off the house wall on two posts, its back
//             bay stacked to the roof with split logs for the kiln.
//   Wing      (II) the journeyman's room, framed in dark oak and plaster on a stone plinth, a steep gable at z 16.
//   Yard      (II) the drying yard behind the shed: a rack of fired pots, clay and a quench cauldron.
//   Cellar    (III) the clay cellar under the house (x 1..7, z 2..12, floor y -4).

export const SIZE = { 1: [16, 17, 14], 2: [16, 17, 18], 3: [16, 17, 18] }
export const BASEMENT = 4 // Level III's cellar depth (the file holds it at the bottom: floor at file y 0, ground floor y 4)

// Palette (60/30/10): rubble stone (ground walls) and lime plaster (upper panels) / spruce frame and deepslate tile
// roof / brick kiln, spruce joinery and floors. Why: the plaster is the lightest thing on the house and the tiles the
// darkest, so the jettied upper storey reads as a pale band between a grey base and a dark roof; the red kiln is the
// one warm mass beside it, the 10 % accent the eye goes to, and its dark hoops repeat the roof's tile.
export const PALETTE = {
  stone: [['cobblestone', 6], ['andesite', 2], ['stone', 2], ['mossy_cobblestone', 1]],
  quoin: 'stone_bricks', quoinAlt: 'polished_andesite',
  frame: 'spruce_log',
  infill: [['white_concrete', 5], ['polished_diorite', 1]], // lime plaster, a little patchy
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  trim: 'spruce', // brackets, moulding, barge boards
  floor: 'spruce_planks',
  workFloor: 'polished_andesite',
  lining: 'birch_planks',
  wood: 'spruce', // doors, stairs, furniture, fences
  glass: 'glass_pane',
  kiln: { base: 'cobblestone', body: 'bricks', band: 'deepslate_bricks', stairs: 'brick_stairs', wall: 'brick_wall' },
  cellarFloor: 'cobblestone',
}

export const MASSES = {
  house: {
    walls: { x0: 1, x1: 7, z0: 2, z1: 12 }, // the ground floor's walls
    upper: { x0: 1, x1: 7, z0: 1, z1: 12 }, // the jettied upper storey's frame
    groundTop: 4, sillY: 5, upperTop: 8, beamY: 9,
    frontPosts: [1, 3, 5, 7], backPosts: [1, 3, 5, 7], sidePosts: [1, 5, 8, 12],
    ties: [4, 9], // tie beams across the upper room at y 9 (the lamps hang from them)
    dormerZ: 7, // a gabled dormer on the west slope (z 6..8), between the bedroom and the sitting room
    roof: { axis: 'z', across: [0, 8], along: [0, 13], eaveY: 9, steep: true },
  },
  // the bottle: a 7-wide round hovel (y 0..3), a shoulder into a 5-wide body (y 4..6), a shoulder into the neck
  // (y 7..10), the lip at y 11 and the smoking rim at y 12
  kiln: { cx: 11, cz: 4, stages: [{ r: 3, top: 3 }, { r: 2, top: 6 }, { r: 1, top: 10 }], bands: [2, 6] },
  shed: { x0: 8, x1: 10, z0: 8, z1: 12, posts: [[9, 9], [9, 12]] }, // floor x 8..10; the roof x 8..9 and its eave over x 10
  wing: {
    walls: { x0: 2, x1: 6, z0: 13, z1: 16 },
    roof: { axis: 'z', across: [1, 7], along: [13, 17], eaveY: 5, steep: true },
    posts: [[2, 16], [6, 16], [2, 13], [6, 13]],
  },
  cellar: { x0: 1, z0: 2, x1: 7, z1: 12, depth: 4 },
}

export const ROOMS = [
  { name: 'Workroom', level: 1, use: 'the potter\'s bench and the kiln\'s mouth, wares by the door', box: [2, 1, 3, 6, 4, 5] },
  { name: 'Hall', level: 1, use: 'dining table for four, the kitchen corner, the stair up', box: [2, 1, 6, 5, 4, 11] },
  { name: 'Bedroom', level: 1, use: 'the couple\'s two beds under the street windows, the chest between them', box: [2, 6, 2, 6, 9, 5] },
  { name: 'Sitting room', level: 1, use: 'a settle on a rug, a writing desk at the back window, books', box: [2, 6, 6, 6, 9, 11] },
  { name: 'Journeyman\'s room', level: 2, use: 'the third bed, a nightstand, a chest and a cupboard', box: [3, 1, 13, 5, 4, 15] },
  { name: 'Clay cellar', level: 3, use: 'the family\'s chests along the front wall, casks of slip, a sorting table', box: [2, -3, 3, 6, -1, 11] },
]

export const OPENINGS = {
  frontDoor: { at: [4, 1, 2], facing: 'south', step: [4, 0, 0] },
  doorLamp: [4, 4, 1], // hung from the jetty's sill beam over the door
  // a straight flight up the east wall, entered from the workroom, arriving at the sitting room's back
  stairs: { flight: [[6, 1, 6], [6, 2, 7], [6, 3, 8], [6, 4, 9], [6, 5, 10]], rises: 'south', well: [[6, 5, 7], [6, 5, 8], [6, 5, 9]] },
  rail: [[6, 6, 6], [5, 6, 6], [5, 6, 9]],
  kilnMouth: { at: [7, 1, 4], facing: 'west' }, // the kiln's fire mouth, opening into the workroom
  stokeHole: { at: [14, 1, 4], facing: 'east' }, // its stoking furnace on the yard side
  wingDoorway: { at: [4, 1, 12], height: 2 }, // II: through the house's back wall into the wing
  hatch: { x: 2, z: 5, facing: 'east' }, // III: ladder against the cellar's west wall, the hatch in the workroom floor
  windows: [
    // ground floor: sills on the stoop and the skirt
    { name: 'shop west', cells: [[2, 2, 2], [2, 3, 2]], out: 'north', dress: 'sill', pots: ['potted_red_tulip'] },
    { name: 'shop east', cells: [[6, 2, 2], [6, 3, 2]], out: 'north', dress: 'sill', pots: ['potted_cornflower'] },
    { name: 'workroom west', cells: [[1, 2, 3], [1, 3, 3]], out: 'west', dress: 'sill', pots: ['potted_fern'] },
    { name: 'kitchen west', cells: [[1, 2, 10], [1, 3, 10]], out: 'west', dress: 'sill', pots: ['potted_oxeye_daisy'] },
    { name: 'hall back', cells: [[4, 2, 12], [4, 3, 12]], out: 'south', dress: 'sill', pots: ['potted_allium'], level1Only: true },
    // upper floor: the street front's three on the jetty's moulding, the sides and back plain
    { name: 'bedroom front west', cells: [[2, 7, 1], [2, 8, 1]], out: 'north', dress: 'sill', pots: ['potted_red_tulip'] },
    { name: 'bedroom front middle', cells: [[4, 7, 1], [4, 8, 1]], out: 'north', dress: 'sill', pots: ['potted_azure_bluet'] },
    { name: 'bedroom front east', cells: [[6, 7, 1], [6, 8, 1]], out: 'north', dress: 'sill', pots: ['potted_cornflower'] },
    { name: 'bedroom west', cells: [[1, 7, 3], [1, 8, 3]], out: 'west', dress: null },
    { name: 'bedroom east', cells: [[7, 7, 3], [7, 8, 3]], out: 'east', dress: null },
    { name: 'sitting west', cells: [[1, 7, 10], [1, 8, 10]], out: 'west', dress: null },
    { name: 'sitting east', cells: [[7, 7, 10], [7, 8, 10]], out: 'east', dress: null },
    { name: 'sitting back west', cells: [[2, 7, 12], [2, 8, 12]], out: 'south', dress: null },
    { name: 'sitting back middle', cells: [[4, 7, 12], [4, 8, 12]], out: 'south', dress: null },
    { name: 'sitting back east', cells: [[6, 7, 12], [6, 8, 12]], out: 'south', dress: null },
    // gables
    { name: 'front gable', cells: [[4, 11, 1], [4, 12, 1]], out: 'north', dress: null },
    { name: 'back gable', cells: [[4, 11, 12], [4, 12, 12]], out: 'south', dress: null },
  ],
  wingWindows: [
    { name: 'wing west', cells: [[2, 2, 14], [2, 3, 14], [2, 2, 15], [2, 3, 15]], out: 'west', dress: 'shutters' },
    { name: 'wing east', cells: [[6, 2, 14], [6, 3, 14], [6, 2, 15], [6, 3, 15]], out: 'east', dress: 'shutters' },
    { name: 'wing back', cells: [[4, 2, 16], [4, 3, 16]], out: 'south', dress: 'sill', pots: ['potted_blue_orchid'] },
    { name: 'wing gable', cells: [[4, 6, 16], [4, 7, 16]], out: 'south', dress: null },
  ],
}

// Every piece of furniture, footprint first (kind: a furniture.py kind; 'fixture' is a vanilla block used as itself).
// cells are relative to pos. Kept cells (the brief's refresh list) are marked "kept".
export const FURNITURE = {
  1: [
    // Workroom: the potter's bench and stool under the west window, the worktop, wares on the kept barrel
    { room: 'Workroom', kind: 'fixture', part: 'worktop', pos: [2, 1, 3], facing: 'east' },
    { room: 'Workroom', kind: 'table', part: 'table', pos: [3, 1, 3], facing: 'north', wood: 'spruce', colour: 'brown' },
    { room: 'Workroom', kind: 'chair', part: 'chair', pos: [3, 1, 4], facing: 'north', wood: 'spruce' },
    { room: 'Workroom', kind: 'fixture', part: 'pantry', pos: [6, 1, 3], facing: 'west', block: 'barrel' }, // kept
    { room: 'Workroom', kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 4], facing: 'north', wood: 'spruce' },
    { room: 'Workroom', kind: 'picture', part: 'picture', pos: [2, 3, 5], facing: 'east', wood: 'spruce', colour: 'orange' },
    // Hall: the kept barrel and chest on the west wall, a table for four, the kitchen corner at the back
    { room: 'Hall', kind: 'fixture', part: 'pantry', pos: [2, 1, 6], facing: 'east', block: 'barrel' }, // kept
    { room: 'Hall', kind: 'fixture', part: 'chest', pos: [2, 1, 7], facing: 'east', block: 'chest' }, // kept
    { room: 'Hall', kind: 'table', part: 'table', pos: [3, 1, 8], facing: 'north', wood: 'spruce', colour: 'white' },
    { room: 'Hall', kind: 'table', part: 'table', pos: [3, 1, 9], facing: 'north', wood: 'spruce', colour: 'white' },
    { room: 'Hall', kind: 'chair', part: 'chair', pos: [2, 1, 8], facing: 'east', wood: 'spruce' },
    { room: 'Hall', kind: 'chair', part: 'chair', pos: [2, 1, 9], facing: 'east', wood: 'spruce' },
    { room: 'Hall', kind: 'chair', part: 'chair', pos: [4, 1, 8], facing: 'west', wood: 'spruce' },
    { room: 'Hall', kind: 'chair', part: 'chair', pos: [4, 1, 9], facing: 'west', wood: 'spruce' },
    { room: 'Hall', kind: 'counter', part: 'counter', pos: [2, 1, 10], facing: 'east', wood: 'spruce' },
    { room: 'Hall', kind: 'fixture', part: 'stove', pos: [2, 1, 11], facing: 'east', block: 'furnace' },
    { room: 'Hall', kind: 'counter', part: 'counter', pos: [3, 1, 11], facing: 'north', wood: 'spruce' },
    { room: 'Hall', kind: 'counter', part: 'counter', pos: [5, 1, 11], facing: 'north', topSlab: true, wood: 'spruce' }, // II's kept loom cell
    { room: 'Hall', kind: 'crockery', part: 'plates', pos: [5, 3, 11], facing: 'north', wood: 'spruce', block: 'flower_pot' },
    { room: 'Hall', kind: 'shelf', part: 'shelf', pos: [3, 3, 11], facing: 'north', wood: 'spruce', crockery: 'potted_brown_mushroom' },
    { room: 'Hall', kind: 'fixture', part: 'sink', pos: [6, 1, 11], facing: 'north' },
    { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 9], facing: 'north', wood: 'spruce' },
    { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 6], facing: 'north', wood: 'spruce' },
    { room: 'Hall', kind: 'rug', part: 'rug', cellsAbs: [[3, 1, 6], [4, 1, 6], [3, 1, 7], [4, 1, 7]], colour: 'orange' },
    // Bedroom (front, upstairs): the two kept beds under the street windows, nightstands, the kept chest between them
    { room: 'Bedroom', kind: 'bed', part: 'bed', pos: [2, 6, 3], facing: 'north', colour: 'orange' }, // kept
    { room: 'Bedroom', kind: 'bed', part: 'bed', pos: [6, 6, 3], facing: 'north', colour: 'cyan' }, // kept
    { room: 'Bedroom', kind: 'nightstand', part: 'nightstand', pos: [3, 6, 2], facing: 'south', wood: 'spruce' },
    { room: 'Bedroom', kind: 'nightstand', part: 'nightstand', pos: [5, 6, 2], facing: 'south', wood: 'spruce' },
    { room: 'Bedroom', kind: 'fixture', part: 'chest', pos: [4, 6, 2], facing: 'south', block: 'chest' }, // kept
    { room: 'Bedroom', kind: 'rug', part: 'rug', cellsAbs: [[3, 6, 3], [4, 6, 3], [5, 6, 3], [3, 6, 4], [4, 6, 4], [5, 6, 4]], colour: 'light_gray' },
    { room: 'Bedroom', kind: 'cupboard', part: 'cupboard', pos: [2, 6, 5], facing: 'east', wood: 'spruce' },
    { room: 'Bedroom', kind: 'lamp', part: 'hangingLamp', pos: [4, 8, 4], facing: 'north', wood: 'spruce' },
    // Sitting room (back, upstairs): a settle with arms on a rug (no table before it: a seat at a table reads as a chair), a desk under the back window, a bookcase
    { room: 'Sitting room', kind: 'couch', part: 'couch', pos: [2, 6, 8], facing: 'east', length: 2, arms: true, wood: 'spruce', colour: 'orange' },
    { room: 'Sitting room', kind: 'rug', part: 'rug', cellsAbs: [[3, 6, 7], [3, 6, 8], [3, 6, 9], [3, 6, 10]], colour: 'brown' },
    { room: 'Sitting room', kind: 'desk', part: 'desk', pos: [2, 6, 11], facing: 'west', wood: 'spruce' },
    { room: 'Sitting room', kind: 'chair', part: 'chair', pos: [3, 6, 11], facing: 'west', wood: 'spruce' },
    { room: 'Sitting room', kind: 'fixture', part: 'bookcase', pos: [2, 6, 6], facing: 'east', plant: 'potted_fern' },
    { room: 'Sitting room', kind: 'cupboard', part: 'cupboard', pos: [4, 6, 11], facing: 'north', wood: 'spruce' },
    { room: 'Sitting room', kind: 'picture', part: 'picture', pos: [2, 8, 9], facing: 'east', wood: 'spruce', colour: 'cyan' },
    { room: 'Sitting room', kind: 'lamp', part: 'hangingLamp', pos: [3, 8, 9], facing: 'north', wood: 'spruce' },
  ],
  2: [
    // Journeyman's room (the wing): the third bed in the kept cells, head to the back wall
    { room: 'Journeyman\'s room', kind: 'bed', part: 'bed', pos: [3, 1, 14], facing: 'south', colour: 'brown' }, // kept
    { room: 'Journeyman\'s room', kind: 'nightstand', part: 'nightstand', pos: [4, 1, 15], facing: 'north', wood: 'spruce' },
    { room: 'Journeyman\'s room', kind: 'fixture', part: 'chest', pos: [5, 1, 15], facing: 'west', block: 'chest' }, // kept
    { room: 'Journeyman\'s room', kind: 'cupboard', part: 'cupboard', pos: [5, 1, 13], facing: 'west', wood: 'spruce' },
    { room: 'Journeyman\'s room', kind: 'rug', part: 'rug', cellsAbs: [[4, 1, 14], [5, 1, 14]], colour: 'brown' },
    { room: 'Journeyman\'s room', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 14], facing: 'north', chain: 5, wood: 'spruce' },
  ],
  3: [],
}

// The walking line: the step -> the door -> the workroom -> up the east-wall flight to the sitting room -> the
// bedroom; and (II) down the hall past the table to the wing; and (III) down the hatch ladder to the cellar.
export const WALK = [
  [4, 0, 0], [4, 1, 1], [4, 1, 2], [4, 1, 3], [4, 1, 4], [5, 1, 5], [6, 1, 5],
  [6, 1, 6], [6, 2, 7], [6, 3, 8], [6, 4, 9], [6, 5, 10], [6, 6, 11], [5, 6, 11], [5, 6, 10], [4, 6, 10], [4, 6, 6], [4, 6, 4],
  // II
  [5, 1, 6], [5, 1, 10], [4, 1, 10], [4, 1, 11], [4, 1, 12], [4, 1, 13], [4, 1, 14],
  // III
  [3, 1, 5], [2, 1, 5], [2, -3, 5], [3, -3, 5], [3, -3, 10],
]

// Furnishings outside the rooms (not furniture spots).
export const OUTSIDE = {
  stoop: { x0: 1, x1: 7, z: 1 }, // the paved strip under the jetty
  flowerBeds: [{ x0: 1, x1: 3, z: 0, plants: ['poppy', 'cornflower', 'oxeye_daisy'] }, { x0: 5, x1: 7, z: 0, plants: ['allium', 'poppy', 'azure_bluet'] }],
  wares: [5, 1, 1], // a kept barrel of the potter's wares beside the door
  woodpiles: [{ x: 15, z0: 1, z1: 3 }, { x: 15, z0: 5, z1: 7 }],
  shed: { logs: { x: 8, z0: 7, z1: 12, high: 3 }, clay: [[9, 1, 10], [9, 1, 11], [9, 2, 11]], cauldron: [10, 1, 8] }, // kiln fuel stacked to the roof
  rack: { x0: 10, x1: 12, z: 15 }, // II: the drying rack of fired pots
}
