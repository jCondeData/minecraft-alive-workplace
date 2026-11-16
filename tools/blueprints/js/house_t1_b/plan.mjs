// House T1 (Version B), the plan: drawn before any wall. Rooms, every furniture footprint (kind, cells, facing) and
// the walking line come first; recipe.mjs then assembles the house from parts.mjs's named parts to fit this plan.
//
// Story: the "Daub House". A first family raises it from what clearing the land gives them: cobble from the field,
// mud from the riverbank pressed into bricks for the wall panels, dark oak for a frame that stands proud of the
// daub, and fired clay tiles (bricks) for a steep roof that sheds rain. One tall hall-and-loft under a steep hero
// gable, a low stone kitchen lean on its east side, a chimney up the back gable, a hooded door at the front.
//
// Coordinates: x across the front (west 0 -> east 12), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Front door on the north side with a stair step at y = 0 before it.
//
// Masses (named parts the owner's feedback can point at):
//   Hall      the main mass: frame posts x 0..8, z 1..11 (a block proud of the daub), daub panels x 1..7, z 2..10;
//             ground room y 1..3, beam ring y 4, steep tiled roof (ridge along z) up to y 12, gables jettied out
//             to the post line above the beam.
//   Kitchen   the low stone wing on the east: walls x 8..11, z 3..9, a 45-degree tiled roof (ridge along x, y 8).
//   Chimney   a stone stack up the back gable (x 4, z 12), the hearth inside at (4, 1, 10).
//   Dormer    a gabled window on the long west slope, over the hall's west window (z 6), lighting the loft.
//   Hood      a little tiled hood over the front door, a lantern under it, the loft window sitting on it.
//   Loft      the bedroom floor in the Hall's roof (floor y 4), up a straight flight along the east wall.

export const SIZE = [13, 14, 13] // w, h, d (limit 13 x 16 x 13)

// Palette (60/30/10): mud-brick daub (walls) / dark oak frame + brick roof / cobble plinth, spruce floors and
// joinery. Why: warm earth tones a first family can gather themselves, unlike the village's spruce-and-birch
// houses; the red clay roof contrasts with the tan daub, the dark frame draws the bays, the cobble roots it.
export const PALETTE = {
  stone: [['cobblestone', 6], ['stone', 2], ['andesite', 1], ['mossy_cobblestone', 2]],
  frame: 'dark_oak_log',
  infill: 'mud_bricks',
  roof: { full: 'bricks', stairs: 'brick_stairs', slab: 'brick_slab' },
  floor: 'birch_planks', // pale boards underfoot and overhead (the loft floor is the hall's ceiling): light in a brown room
  lining: 'birch_planks', // the loft's ceiling boards under the tiles
  kitchenFloor: 'stone_bricks',
  wood: 'spruce', // doors, trapdoors, stairs as chairs, fences
  trim: 'dark_oak',
  glass: 'glass_pane',
}

export const MASSES = {
  hall: {
    posts: { x0: 0, x1: 8, z0: 1, z1: 11 },
    daub: { x0: 1, x1: 7, z0: 2, z1: 10 },
    room: { x0: 2, x1: 6, z0: 3, z1: 9 },
    wallTop: 3, beamY: 4,
    // frame posts along each wall (bays between them get daub or glass)
    frontPosts: [0, 3, 5, 8], backPosts: [0, 3, 5, 8],
    westPosts: [1, 4, 8, 11], eastPosts: [1, 3, 9, 11],
    roof: { axis: 'z', across: [0, 8], along: [0, 12], eaveY: 5, steep: true },
  },
  kitchen: {
    walls: { x0: 8, x1: 11, z0: 3, z1: 9 },
    room: { x0: 8, x1: 10, z0: 4, z1: 8 },
    wallTop: 3, beamY: 4,
    roof: { axis: 'x', across: [2, 10], along: [7, 12], eaveY: 4, steep: false },
  },
}

export const ROOMS = [
  { name: 'Hall', use: 'living, dining and hearth', box: [2, 1, 3, 6, 3, 9] },
  { name: 'Kitchen', use: 'cooking and pantry', box: [8, 1, 4, 10, 3, 8] },
  { name: 'Loft', use: 'two bedrooms under one roof: the parents at the back, the children at the front', box: [2, 5, 2, 6, 7, 10] },
]

export const OPENINGS = {
  frontDoor: { at: [4, 1, 2], facing: 'south', step: [4, 0, 1] },
  kitchenDoorway: { at: [7, 1, 5], height: 2 },
  stairs: { flight: [[6, 1, 6], [6, 2, 7], [6, 3, 8], [6, 4, 9]], rises: 'south', well: [[6, 4, 6], [6, 4, 7], [6, 4, 8]] },
  // windows: cells of glass, the side they look out of, and their dressing
  windows: [
    { name: 'hall front west', cells: [[1, 2, 2], [2, 2, 2], [1, 3, 2], [2, 3, 2]], out: 'north', dress: 'sill' },
    { name: 'hall front east', cells: [[6, 2, 2], [7, 2, 2], [6, 3, 2], [7, 3, 2]], out: 'north', dress: 'sill' },
    { name: 'hall west', cells: [[1, 2, 6], [1, 3, 6]], out: 'west', dress: 'sill' },
    { name: 'loft front', cells: [[4, 5, 1], [4, 6, 1]], out: 'north', dress: 'shutters' },
    { name: 'loft attic', cells: [[4, 8, 1]], out: 'north', dress: null },
    { name: 'loft back west', cells: [[3, 6, 11]], out: 'south', dress: null },
    { name: 'loft back east', cells: [[5, 6, 11]], out: 'south', dress: null },
    { name: 'kitchen north', cells: [[10, 2, 3], [10, 3, 3]], out: 'north', dress: 'shutters' },
    { name: 'kitchen south', cells: [[10, 2, 9], [10, 3, 9]], out: 'south', dress: 'shutters' },
    { name: 'kitchen east', cells: [[11, 2, 6], [11, 3, 6]], out: 'east', dress: 'shutters' },
    { name: 'kitchen gable', cells: [[11, 6, 6]], out: 'east', dress: null },
  ],
}

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a furniture spot) or a fixture
// (a vanilla block the game uses as itself: chest, furnace, crafting table, campfire). part: the parts.mjs part that
// draws it. facing: the way a person using it faces. cells: relative to pos.
export const FURNITURE = [
  // Hall: a dining table for four under the west window, two armchairs at the hearth, a clock and a lamp by the door
  { room: 'Hall', kind: 'table', part: 'table', pos: [3, 1, 6], facing: 'north', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce', colour: 'white' },
  { room: 'Hall', kind: 'table', part: 'table', pos: [3, 1, 7], facing: 'north', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce', colour: 'white' },
  { room: 'Hall', kind: 'chair', part: 'chair', pos: [2, 1, 6], facing: 'east', wood: 'spruce' },
  { room: 'Hall', kind: 'chair', part: 'chair', pos: [2, 1, 7], facing: 'east', wood: 'spruce' },
  { room: 'Hall', kind: 'chair', part: 'chair', pos: [4, 1, 6], facing: 'west', wood: 'spruce' },
  { room: 'Hall', kind: 'chair', part: 'chair', pos: [4, 1, 7], facing: 'west', wood: 'spruce' },
  { room: 'Hall', kind: 'chair', part: 'armchair', pos: [3, 1, 9], facing: 'east', wood: 'dark_oak', colour: 'red' },
  { room: 'Hall', kind: 'chair', part: 'armchair', pos: [5, 1, 9], facing: 'west', wood: 'dark_oak', colour: 'red' },
  { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [3, 3, 7], facing: 'north', wood: 'spruce' },
  { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [5, 3, 5], facing: 'north', wood: 'spruce' },
  { room: 'Hall', kind: 'lamp', part: 'floorLamp', pos: [6, 1, 3], facing: 'west', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Hall', kind: 'clock', part: 'clock', pos: [2, 1, 3], facing: 'east', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Hall', kind: 'shelf', part: 'shelf', pos: [3, 2, 3], facing: 'south', wood: 'spruce', crockery: 'flower_pot' },
  { room: 'Hall', kind: 'shelf', part: 'shelf', pos: [5, 2, 3], facing: 'south', wood: 'spruce', crockery: 'potted_red_tulip' },
  { room: 'Hall', kind: 'cupboard', part: 'cupboard', pos: [2, 1, 9], facing: 'east', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Hall', kind: 'picture', part: 'picture', pos: [2, 3, 4], facing: 'east', wood: 'spruce', colour: 'green' },
  { room: 'Hall', kind: 'rug', part: 'rug', cellsAbs: [[3, 1, 8], [4, 1, 8], [5, 1, 8], [4, 1, 9]], colour: 'red' },
  { room: 'Hall', kind: 'fixture', part: 'hearth', pos: [4, 1, 10], facing: 'north', block: 'campfire' },
  // Kitchen: counters round three walls under the windows, the stove and a worktop on the south wall, the pantry
  { room: 'Kitchen', kind: 'counter', part: 'counter', pos: [9, 1, 4], facing: 'north', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Kitchen', kind: 'counter', part: 'counter', pos: [10, 1, 4], facing: 'north', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Kitchen', kind: 'counter', part: 'counter', pos: [10, 1, 5], facing: 'east', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Kitchen', kind: 'counter', part: 'counter', pos: [10, 1, 6], facing: 'east', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Kitchen', kind: 'counter', part: 'counter', pos: [10, 1, 7], facing: 'east', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Kitchen', kind: 'counter', part: 'counter', pos: [10, 1, 8], facing: 'south', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Kitchen', kind: 'fixture', part: 'stove', pos: [9, 1, 8], facing: 'north', block: 'furnace' },
  { room: 'Kitchen', kind: 'fixture', part: 'worktop', pos: [8, 1, 8], facing: 'north', block: 'crafting_table' },
  { room: 'Kitchen', kind: 'fixture', part: 'pantry', pos: [8, 1, 4], facing: 'south', block: 'chest' },
  { room: 'Kitchen', kind: 'shelf', part: 'shelf', pos: [9, 3, 4], facing: 'south', wood: 'spruce', crockery: 'flower_pot' },
  { room: 'Kitchen', kind: 'shelf', part: 'shelf', pos: [10, 3, 8], facing: 'north', wood: 'spruce', crockery: 'potted_brown_mushroom' },
  { room: 'Kitchen', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 6], facing: 'north', chain: 3, wood: 'spruce' },
  // Loft, the children's end (front): two beds either side of the gable window, nightstands with candles, a rug
  { room: 'Loft', kind: 'bed', part: 'bed', pos: [2, 5, 3], facing: 'north', colour: 'light_blue' },
  { room: 'Loft', kind: 'bed', part: 'bed', pos: [6, 5, 3], facing: 'north', colour: 'yellow' },
  { room: 'Loft', kind: 'nightstand', part: 'nightstand', pos: [3, 5, 2], facing: 'south', wood: 'spruce' },
  { room: 'Loft', kind: 'nightstand', part: 'nightstand', pos: [5, 5, 2], facing: 'south', wood: 'spruce' },
  { room: 'Loft', kind: 'rug', part: 'rug', cellsAbs: [[3, 5, 3], [4, 5, 3], [5, 5, 3], [3, 5, 4], [4, 5, 4], [5, 5, 4]], colour: 'light_gray' },
  { room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [4, 7, 4], facing: 'north', chain: 2, wood: 'spruce' },
  // Loft, the parents' end (back): a double bed against the chimney, nightstands, a desk and its chair, a chest
  { room: 'Loft', kind: 'bed', part: 'bed', pos: [3, 5, 9], facing: 'south', colour: 'red' },
  { room: 'Loft', kind: 'bed', part: 'bed', pos: [4, 5, 9], facing: 'south', colour: 'red' },
  { room: 'Loft', kind: 'nightstand', part: 'nightstand', pos: [2, 5, 10], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'nightstand', part: 'nightstand', pos: [5, 5, 10], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'desk', part: 'desk', pos: [2, 5, 8], facing: 'west', wood: 'spruce' },
  { room: 'Loft', kind: 'chair', part: 'chair', pos: [3, 5, 8], facing: 'west', wood: 'spruce' },
  { room: 'Loft', kind: 'fixture', part: 'chest', pos: [2, 5, 6], facing: 'east', block: 'chest' },
  { room: 'Loft', kind: 'rug', part: 'rug', cellsAbs: [[3, 5, 6], [4, 5, 6], [3, 5, 7], [4, 5, 7]], colour: 'brown' },
  { room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [4, 7, 8], facing: 'north', chain: 2, wood: 'spruce' },
]

// The walking line: front step -> door -> hall -> kitchen doorway, and up the flight to the loft.
export const WALK = [
  [4, 0, 0], [4, 0, 1], [4, 1, 2], [4, 1, 3], [4, 1, 4], [5, 1, 4], [5, 1, 5], [6, 1, 5], [7, 1, 5], [8, 1, 5], [9, 1, 6],
  [6, 1, 6], [6, 2, 7], [6, 3, 8], [6, 4, 9], [5, 4, 9], [5, 4, 8],
]

// Furnishings outside the rooms (not furniture spots): beds of flowers in their own soil under the front windows,
// a woodpile under the kitchen gable.
export const OUTSIDE = {
  flowerBeds: [{ x0: 1, x1: 2, z: 0, plants: ['poppy', 'cornflower'] }, { x0: 6, x1: 7, z: 0, plants: ['oxeye_daisy', 'allium'] }],
  woodpile: { x: 12, z0: 4, z1: 8 },
}
