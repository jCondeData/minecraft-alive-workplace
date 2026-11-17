// Starter Cottage (Version A), the plan: drawn before any wall (brief: tools/blueprints/briefs/starter_cottage.md).
// Rooms, every furniture footprint (kind, cells, facing) and the walking line first; recipe.mjs then assembles each
// level from the parts library (../parts) to fit this plan, every level growing out of the one before.
//
// Three rough concepts
//   1. Gable-front cottage. A narrow steep gable to the street over a deep porch; II runs a second bay back along the
//      same ridge, III a side lean-to. Story: the village's first house turns its face to the road. Massing: one long
//      box; roof: one steep gable, ridge along z; plan: rooms in a row front to back.
//   2. The bastle. A stone ground floor (store and byre) under a timber upper room reached by an outside stair; II
//      adds a second timber room on a jetty, III a kitchen lean-to. Story: the first settler builds against raids.
//      Massing: a tall block; roof: a steep gable over the jetty; plan: one room per floor.
//   3. The half-Cape that grows. A one-and-a-half-storey timber cottage with its long eave to the street, its door at
//      one end of the front and a big stone chimney on the end gable. II builds the other half on the far side of the
//      chimney (a full Cape: the door in the middle, the chimney rising through the ridge behind it, a hearth in each
//      room); III adds a lower stone kitchen ell, set back from the front, with its own door; IV digs a root cellar
//      under the old hall. Story: the way real Cape houses grew, "half house, whole house, little house behind",
//      each upgrade a builder's honest next job on the same footprint.
// The strongest is 3. Its silhouette changes on purpose at every level and each level is the one before plus a part
// you can name (the second half, the kitchen ell, the cellar): the half-Cape's lopsided front (door, chimney, then
// the gable) is a shape nobody mistakes for a generic box, and the chimney is the story's spine, an end chimney in I
// that ends up central and taller in II. 1 is the safe one (a box with a porch); 2 needs an outside stair the brief's
// first bed can't use and a stone mass the tests' wooden styles fight.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block; the cellar below 0),
// z from the front (z = 0, north) to the back. Front door on the north side with a stair step at y = 0 before it.
//
// Named parts (the owner's feedback can point at them):
//   Hall        I's timber room: posts x 1..7, z 1..7 (7 x 7, odd so the ridge has a true middle); walls y 1..3, beam
//               ring y 4, a knee wall y 5 under a 45-degree dark shingle roof (ridge along x at z 4, y 9) overhanging
//               a block all round (x 0..8, z 0..8). The loft floor (y 4) is the hall's ceiling.
//   Chimney     a cobble stack outside the hall's east gable (x 8, z 4) on a stepped breast (x 8, z 3..5), the hearth
//               inside (x 6, z 4) in a stone surround; campfire on top at y 11 (I), raised to y 13 in II.
//   Hood        a little shingled hood over the front door with a lantern under it.
//   Parlour     II's half: posts x 7..11 (the hall's east gable becomes the partition), the roof carried on to x 12, the
//               old breast becomes the parlour's own hearth (back to back with the hall's). Light blue bed.
//   Kitchen ell III's lower wing, rubble stone, posts x 11..17, z 3..7 (set back two from the front), its roof 45
//               degrees with the ridge at y 6 (three under the house's), its own oak door, a lantern by it.
//   Root cellar IV's room under the hall: walls x 1..7, z 1..7, floor y -4, down a ladder under a hatch by the door.

export const SIZES = { 1: [9, 12, 10], 2: [13, 14, 10], 3: [19, 14, 10], 4: [19, 14, 10] }
export const BASEMENT = { 4: 4 }
export const IDS = { 1: 'starter_cottage', 2: 'starter_cottage_2', 3: 'starter_cottage_3', 4: 'starter_cottage_4' }

// Palette (60/30/10): birch infill (the pale walls, 60) / dark oak shingles and the spruce frame (30) / cobble plinth,
// chimney and the kitchen ell's rubble (10). Why: a timber cottage a first settler raises from one stand of spruce and
// birch, roofed in the darkest wood so the roof reads apart from a pale wall; the grey stone roots it and carries the
// fire. Spruce is the main wood (the tests' styles swap it).
export const PALETTE = {
  stone: [['cobblestone', 7], ['stone', 2], ['andesite', 2], ['mossy_cobblestone', 1]],
  cap: 'stone_bricks',
  frame: 'spruce_log',
  infill: 'birch_planks',
  roof: { full: 'dark_oak_planks', stairs: 'dark_oak_stairs', slab: 'dark_oak_slab' },
  floor: 'spruce_planks',
  kitchenFloor: 'stone_bricks',
  cellarFloor: 'cobblestone',
  wood: 'spruce', // chairs, trapdoors, fences, shelves
  door: 'oak',
  trim: 'spruce_slab',
  step: 'cobblestone_stairs',
  glass: 'glass_pane',
}

export const MASSES = {
  hall: { posts: { x0: 1, x1: 7, z0: 1, z1: 7 }, room: { x0: 2, x1: 6, z0: 2, z1: 6 }, wallTop: 3, beamY: 4,
    frontPosts: [1, 5, 7], backPosts: [1, 4, 7], westPosts: [1, 3, 5, 7], eastPosts: [1, 7],
    roof: { axis: 'x', across: [0, 8], along: [0, 8], eaveY: 5, steep: false } },
  chimney: { x: 8, z: 4, breast: [3, 5], top: { 1: 10, 2: 12 } },
  parlour: { posts: { x0: 7, x1: 11, z0: 1, z1: 7 }, room: { x0: 8, x1: 10, z0: 2, z1: 6 },
    frontPosts: [11], backPosts: [11], eastPosts: [1, 3, 5, 7],
    roof: { axis: 'x', across: [0, 8], along: [0, 12], eaveY: 5, steep: false } },
  kitchen: { walls: { x0: 11, x1: 17, z0: 3, z1: 7 }, room: { x0: 12, x1: 16, z0: 4, z1: 6 },
    roof: { axis: 'x', across: [2, 8], along: [12, 18], eaveY: 3, steep: false } },
  cellar: { walls: { x0: 1, x1: 7, z0: 1, z1: 7 }, depth: 4 },
}

export const ROOMS = [
  { level: 1, name: 'Hall', use: 'living, eating, sleeping and the two trades by the hearth', box: [2, 1, 2, 6, 3, 6] },
  { level: 2, name: 'Parlour', use: 'the second bedroom, with its own hearth', box: [8, 1, 2, 10, 3, 6] },
  { level: 3, name: 'Kitchen', use: 'cooking (the smoker), a counter run and the pantry', box: [12, 1, 4, 16, 4, 6] },
  { level: 4, name: 'Root cellar', use: 'stores kept cool under the hall', box: [2, -3, 2, 6, -1, 6] },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 1], facing: 'south', step: [6, 0, 0] },
  partition: { at: [7, 1, 2], height: 2, level: 2 }, // the entry opens into both rooms once the Parlour stands
  kitchenDoorway: { at: [11, 1, 4], height: 2, level: 3 }, // knocked through the Parlour's east wall
  kitchenDoor: { at: [15, 1, 3], facing: 'south', step: [15, 0, 2], level: 3 },
  hatch: { at: [5, 2], facing: 'south', level: 4 },
  windows: [
    { level: 1, name: 'hall front', cells: [[2, 2, 1], [3, 2, 1], [4, 2, 1], [2, 3, 1], [3, 3, 1], [4, 3, 1]], out: 'north', dress: 'sill', pots: ['potted_poppy', 'potted_azure_bluet', 'potted_cornflower'] },
    { level: 1, name: 'hall west', cells: [[1, 2, 4], [1, 3, 4]], out: 'west', dress: 'sill', pots: ['potted_dandelion'] },
    { level: 1, name: 'hall back', cells: [[2, 2, 7], [3, 2, 7], [2, 3, 7], [3, 3, 7]], out: 'south', dress: 'sill', pots: ['potted_fern', 'potted_red_tulip'] },
    { level: 1, name: 'entry', cells: [[7, 2, 2], [7, 3, 2]], out: 'east', dress: null, until: 2 },
    { level: 1, name: 'loft west', cells: [[1, 6, 4], [1, 7, 4]], out: 'west', dress: null },
    { level: 1, name: 'loft east north', cells: [[7, 6, 2]], out: 'east', dress: null, until: 2 },
    { level: 1, name: 'loft east south', cells: [[7, 6, 6]], out: 'east', dress: null, until: 2 },
    { level: 2, name: 'parlour front', cells: [[8, 2, 1], [9, 2, 1], [10, 2, 1], [8, 3, 1], [9, 3, 1], [10, 3, 1]], out: 'north', dress: 'sill', pots: ['potted_oxeye_daisy', 'potted_allium', 'potted_poppy'] },
    { level: 2, name: 'parlour back', cells: [[9, 2, 7], [10, 2, 7], [9, 3, 7], [10, 3, 7]], out: 'south', dress: 'sill', pots: ['potted_red_tulip', 'potted_fern'] },
    { level: 2, name: 'parlour east', cells: [[11, 2, 2], [11, 3, 2]], out: 'east', dress: 'sill', pots: ['potted_blue_orchid'] },
    { level: 2, name: 'loft east', cells: [[11, 6, 4], [11, 7, 4]], out: 'east', dress: null },
    { level: 3, name: 'kitchen front', cells: [[13, 2, 3]], out: 'north', dress: 'shutters' },
    { level: 3, name: 'kitchen back', cells: [[14, 2, 7]], out: 'south', dress: 'shutters' },
    { level: 3, name: 'kitchen east', cells: [[17, 2, 5], [17, 3, 5]], out: 'east', dress: 'shutters' },
  ],
}

// Every piece of furniture, footprint first, with the level it arrives in. kind: a furniture.py kind or a fixture (a
// vanilla block the game uses as itself). part: the furnitureParts part that draws it. facing: the way a person using it
// faces. Level I's job blocks: the barrel (x 5) comes before the crafting table (x 6) at the same y, so the Table reads
// "Fisherman (1), Carpenter (1)".
export const FURNITURE = [
  // Hall (I): a table for two under the front window, a couch facing the hearth, the bed in the back corner under its
  // own window, the two trades' blocks and a counter with crockery along the back wall, the clock by the west window.
  { level: 1, room: 'Hall', kind: 'table', part: 'table', pos: [3, 1, 2], facing: 'north', wood: 'spruce', colour: 'white' },
  { level: 1, room: 'Hall', kind: 'chair', part: 'chair', pos: [2, 1, 2], facing: 'east', wood: 'spruce' },
  { level: 1, room: 'Hall', kind: 'chair', part: 'chair', pos: [4, 1, 2], facing: 'west', wood: 'spruce' },
  { level: 1, room: 'Hall', kind: 'blinds', part: 'blinds', pos: [3, 3, 2], facing: 'south', wood: 'spruce' },
  { level: 1, room: 'Hall', kind: 'clock', part: 'clock', pos: [2, 1, 3], facing: 'east', wood: 'spruce' },
  { level: 1, room: 'Hall', kind: 'fixture', part: 'chest', pos: [2, 1, 4], facing: 'east', block: 'chest' },
  { level: 1, room: 'Hall', kind: 'nightstand', part: 'nightstand', pos: [2, 1, 5], facing: 'east', wood: 'spruce' },
  { level: 1, room: 'Hall', kind: 'bed', part: 'bed', pos: [3, 1, 6], facing: 'west', colour: 'red' },
  { level: 1, room: 'Hall', kind: 'picture', part: 'picture', pos: [5, 3, 2], facing: 'south', wood: 'spruce', colour: 'green' },
  { level: 1, room: 'Hall', kind: 'couch', part: 'couch', pos: [4, 1, 4], facing: 'east', length: 2, wood: 'spruce', colour: 'red' },
  { level: 1, room: 'Hall', kind: 'counter', part: 'counter', pos: [4, 1, 6], facing: 'north', wood: 'spruce', topSlab: true },
  { level: 1, room: 'Hall', kind: 'crockery', part: 'plates', pos: [4, 3, 6], facing: 'north', wood: 'spruce', block: 'flower_pot' },
  { level: 1, room: 'Hall', kind: 'fixture', part: 'pantry', pos: [5, 1, 6], facing: 'north', block: 'barrel' },
  { level: 1, room: 'Hall', kind: 'shelf', part: 'shelf', pos: [5, 2, 6], facing: 'north', wood: 'spruce', crockery: 'potted_brown_mushroom' },
  { level: 1, room: 'Hall', kind: 'fixture', part: 'worktop', pos: [6, 1, 6], facing: 'north', block: 'crafting_table' },
  { level: 1, room: 'Hall', kind: 'rug', part: 'rug', cellsAbs: [[5, 1, 4], [5, 1, 5]], colour: 'red' },
  { level: 1, room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [3, 3, 4], facing: 'north', wood: 'spruce' },
  // Loft (I): the store under the roof, up a ladder against the hearth's cheek, lit through the front dormer and the
  // west gable: a chest, a straw pallet, shelves, a lamp from the ridge
  { level: 1, room: 'Loft', kind: 'fixture', part: 'chest', pos: [2, 5, 3], facing: 'east', block: 'chest' },
  { level: 1, room: 'Loft', kind: 'shelf', part: 'shelf', pos: [2, 6, 5], facing: 'east', wood: 'spruce' },
  { level: 1, room: 'Loft', kind: 'shelf', part: 'shelf', pos: [6, 6, 5], facing: 'west', wood: 'spruce' },
  { level: 1, room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [4, 7, 4], facing: 'north', chain: 1, wood: 'spruce' },
  { level: 1, room: 'Hall', kind: 'fixture', part: 'hearth', pos: [6, 1, 4], facing: 'west', block: 'campfire' },
  // Parlour (II): the light blue bed against the east wall, its nightstand, a cupboard behind the chimney, a lamp, a
  // picture over the bed, a rug before the second hearth (kept clear for III's doorway at z 4).
  { level: 2, room: 'Parlour', kind: 'bed', part: 'bed', pos: [9, 1, 6], facing: 'east', colour: 'light_blue' },
  { level: 2, room: 'Parlour', kind: 'nightstand', part: 'nightstand', pos: [10, 1, 5], facing: 'west', wood: 'spruce' },
  { level: 2, room: 'Parlour', kind: 'cupboard', part: 'cupboard', pos: [8, 1, 6], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Parlour', kind: 'picture', part: 'picture', pos: [9, 3, 6], facing: 'north', wood: 'spruce', colour: 'light_blue' },
  { level: 2, room: 'Parlour', kind: 'lamp', part: 'hangingLamp', pos: [9, 3, 3], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Parlour', kind: 'rug', part: 'rug', cellsAbs: [[9, 1, 3], [9, 1, 4], [9, 1, 5]], colour: 'light_blue' },
  { level: 2, room: 'Parlour', kind: 'fixture', part: 'hearth', pos: [8, 1, 4], facing: 'east', block: 'campfire' },
  // Kitchen (III): a counter run along the back wall round the smoker, crockery on shelves over it, the pantry chest
  // at the east end, a lamp from the ridge.
  { level: 3, room: 'Kitchen', kind: 'counter', part: 'counter', pos: [12, 1, 6], facing: 'north', wood: 'spruce' },
  { level: 3, room: 'Kitchen', kind: 'counter', part: 'counter', pos: [13, 1, 6], facing: 'north', wood: 'spruce' },
  { level: 3, room: 'Kitchen', kind: 'fixture', part: 'stove', pos: [14, 1, 6], facing: 'north', block: 'smoker' },
  { level: 3, room: 'Kitchen', kind: 'counter', part: 'counter', pos: [15, 1, 6], facing: 'north', wood: 'spruce' },
  { level: 3, room: 'Kitchen', kind: 'counter', part: 'counter', pos: [16, 1, 6], facing: 'north', wood: 'spruce', topSlab: true },
  { level: 3, room: 'Kitchen', kind: 'shelf', part: 'shelf', pos: [12, 3, 6], facing: 'north', wood: 'spruce', crockery: 'flower_pot' },
  { level: 3, room: 'Kitchen', kind: 'shelf', part: 'shelf', pos: [16, 3, 6], facing: 'north', wood: 'spruce', crockery: 'potted_red_mushroom' },
  { level: 3, room: 'Kitchen', kind: 'fixture', part: 'pantry', pos: [16, 1, 4], facing: 'west', block: 'chest' },
  // Root cellar (IV): crockery and preserves on slab shelves along both side walls
  { level: 4, room: 'Root cellar', kind: 'shelf', part: 'shelf', pos: [6, -2, 3], facing: 'west', wood: 'spruce', crockery: 'flower_pot' },
  { level: 4, room: 'Root cellar', kind: 'shelf', part: 'shelf', pos: [6, -2, 4], facing: 'west', wood: 'spruce', crockery: 'potted_brown_mushroom' },
  { level: 4, room: 'Root cellar', kind: 'shelf', part: 'shelf', pos: [2, -2, 3], facing: 'east', wood: 'spruce', crockery: 'flower_pot' },
  { level: 4, room: 'Root cellar', kind: 'shelf', part: 'shelf', pos: [2, -2, 4], facing: 'east', wood: 'spruce', crockery: 'potted_red_mushroom' },
  { level: 3, room: 'Kitchen', kind: 'lamp', part: 'hangingLamp', pos: [14, 5, 5], facing: 'north', wood: 'spruce' },
]

// The walking line: step -> door -> entry -> round the couch to the bed and the trades; in II through the partition
// to the parlour; in III across the parlour to the kitchen and out of its own door; in IV down the hatch.
export const WALK = [
  [6, 0, 0], [6, 1, 1], [6, 1, 2], [5, 1, 2], [5, 1, 3], [5, 4, 3], [5, 5, 4], [5, 1, 4], [5, 1, 5], [4, 1, 3], [3, 1, 3], [3, 1, 4], [3, 1, 5],
  [7, 1, 2], [8, 1, 2], [9, 1, 3], [9, 1, 4], [10, 1, 4], [11, 1, 4], [12, 1, 4], [13, 1, 4], [14, 1, 5], [15, 1, 4], [15, 1, 3], [15, 0, 2],
  [5, 0, 2], [5, -3, 2],
]

// The root cellar's stores (IV): hay and pumpkins on the floor, a slab shelf of candles, a lantern from the ceiling.
export const CELLAR = {
  ladder: { x: 5, z: 2, facing: 'south' },
  lamps: [[4, -1, 4], [4, -1, 2]], // the middle, and by the ladder
  stores: [['hay_block', 2, -3, 6], ['hay_block', 3, -3, 6], ['hay_block', 2, -2, 6], ['pumpkin', 6, -3, 6], ['pumpkin', 6, -3, 5],
    ['melon', 6, -3, 4], ['hay_block', 2, -3, 5]],
}

// The back: a low lean-to woodstore behind the hearth end (x 4..8, z 8..9), under the eave, logs stacked under it.
export const OUTSIDE = { woodstore: { x0: 4, x1: 8, z0: 8, z1: 9 } }
// The front dormer over the hall window (I): a gabled window lighting the loft, centred on x 3.
export const DORMER = { x: 3, face: 1 }
// The loft ladder: against the hearth's north cheek, through the loft floor, onto a bookcase above it.
export const LADDER = { x: 5, z: 3, facing: 'west', top: 5 }
