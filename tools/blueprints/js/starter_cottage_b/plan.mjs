// Starter Cottage (Version B), the plan: drawn before any wall (brief: tools/blueprints/briefs/starter_cottage.md).
// Rooms, every furniture footprint and the walking line come first; recipe.mjs then assembles each level from the
// parts library (../parts) in passes. Levels I-IV are one family: each level is the one below plus what it adds.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Longhouse": one long low box (11 x 7), ridge along the front, door in the middle, a lean-to at each end added
//     by the upgrades. Roof: plain 45-degree gable. Story: the farmhand's bunkhouse that just gets longer. Safe, reads
//     as a cottage, but every level is the same shape a little longer: no level ever gets a silhouette of its own.
//  2. "Gable-front tower cottage": a narrow two-storey house, gable to the road, steep roof, the bed upstairs from
//     Level I. Roof: one steep gable. Story: a lookout cottage by the water. Strong outline, but Level I's single bed
//     upstairs wastes a whole storey, the upgrades can only bolt boxes on its sides, and it is tall for a first build
//     (the 9,000-tick limit).
//  3. "The Saltbox and its cross-wing": Level I is a one-room hall house whose roof is a SALTBOX: a short steep-looking
//     front slope over a tall front wall and a long CATSLIDE sweeping down at the back over an outshot (the bed nook and
//     the work corner) to a low back wall, with a stone chimney up the west gable. Level II raises a two-storey
//     CROSS-WING across the east end, gable to the road: stone below, timber above, a pentice roof over its ground
//     window, its ridge half again as high as the hall's, the bedrooms upstairs (and the hall's chimney goes up two
//     courses to clear it). Level III leans a stone KITCHEN against the wing's east side under a single lean-to slope,
//     with its own door and a little flue; Level IV digs a stone cellar under the hall, down a ladder through a hatch.
//     Story: a fisherman who is also the village's first carpenter builds what he can afford; the house grows the way
//     real cottages grew, a wing and a lean-to at a time.
//  Strongest: 3. Every level has a profile of its own you can name from 20 blocks away (a saltbox; an L with a hero
//  gable; a stepped three-roof run from a lean-to up to the wing's ridge), each grows out of the last on its own
//  ground (the builder adds, never rebuilds), and the catslide puts the rooms where a family uses them: the tall
//  front for the fire and the table, the low back for sleeping and storing. It is the least safe of the three: an
//  asymmetric roof and a narrower, taller wing than the hall.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front (z = 0,
// north) to the back. Front door on the north side, a stair step at y = 0 before it.
//
// Masses and named parts (the owner's and the critic's notes can point at these):
//   Hall       Level I's one room: walls x 1..7, z 2..10, a spruce frame with birch infill on a cobble plinth.
//   Saltbox    the hall's roof, ridge along x over z 5 at y 8: front slope z 1..5 (eave y 4 over the tall front wall),
//              catslide z 5..11 down to y 2 over the low back wall (y 1..2).
//   Outshot    the low back of the hall under the catslide (z 8..9): the bed nook (west) and the work corner (east).
//   Chimney    a cobble stack on the west gable (x 0, z 5), breast either side, the hearth in the wall behind it.
//   Cross-wing Level II: x 7..11, z 1..10, two storeys (ground y 1..3 rubble, floor beam y 4, upper y 5..7 timber,
//              plate y 8), its own gable roof (ridge along z, y 11) and a pentice over the front ground window.
//   Kitchen    Level III: a stone lean-to x 12..16, z 4..9, roof rising from y 2 (x 17) to y 7 under the wing's eave,
//              its own oak door on the front, a doorway knocked through the wing's east wall, a flue in its east wall (x 16).
//   Cellar     Level IV: a stone room under the hall (walls x 1..7, z 2..10, floor 4 below ground), a ladder down
//              through a hatch in the hall floor, a lantern.

export const SIZE = { 1: [9, 11, 12], 2: [13, 12, 12], 3: [18, 12, 12], 4: [18, 12, 12] }
export const BASEMENT = 4 // Level IV's cellar depth (StarterBlueprints.Entry basement 4)

// Palette (60/30/10): birch-plank infill (the walls' light 60) / spruce frame, floors and shingle roof (the 30) /
// cobble rubble for the plinth, the chimney, the wing's ground storey and the kitchen (the 10, darkest low down).
// Why: the village's own woods, nothing a first settler can't cut; pale panels between dark posts draw the bays, the
// brown shingle roof sits over them without merging, the cobble roots it. Dark oak slabs trim the verges.
export const PALETTE = {
  stone: [['cobblestone', 6], ['stone', 2], ['andesite', 1], ['mossy_cobblestone', 2]],
  frame: 'spruce_log',
  infill: 'birch_planks',
  roof: { full: 'spruce_planks', stairs: 'spruce_stairs', slab: 'spruce_slab' },
  floor: 'spruce_planks',
  kitchenFloor: 'stone_bricks',
  cellarFloor: 'cobblestone',
  wood: 'spruce', // trapdoors, fences, chairs, furniture
  door: 'oak', // the brief names oak doors (Level I's and the kitchen's own)
  trim: 'dark_oak_slab',
  glass: 'glass_pane',
}

export const MASSES = {
  hall: {
    walls: { x0: 1, x1: 7, z0: 2, z1: 10 },
    room: { x0: 2, x1: 6, z0: 3, z1: 9 },
    frontPosts: [1, 3, 5, 7], backPosts: [1, 4, 7], westPosts: [2, 7, 10], eastPosts: [2, 6, 10],
    beamY: 4,
    roof: { axis: 'x', across: [1, 11], ridge: 5, along: [0, 8], ridgeY: 8 },
  },
  chimney: { x: 0, z: 5, top: { 1: 9, 2: 10 }, breastTop: 3 },
  wing: {
    walls: { x0: 7, x1: 11, z0: 1, z1: 10 },
    room: { x0: 8, x1: 10, z0: 2, z1: 9 },
    floorY: 4, plateY: 8,
    eastPosts: [1, 3, 6, 10],
    roof: { axis: 'z', across: [6, 12], along: [0, 11], eaveY: 8, steep: false },
  },
  kitchen: {
    walls: { x0: 12, x1: 16, z0: 4, z1: 9 },
    room: { x0: 12, x1: 15, z0: 5, z1: 8 },
    roof: { axis: 'z', across: [12, 17], ridge: 12, along: [3, 10], ridgeY: 7 },
    flue: { x: 16, z: 6, top: 5 },
  },
  cellar: { x0: 1, z0: 2, x1: 7, z1: 10, depth: 4 },
}

export const ROOMS = [
  { level: 1, name: 'Hall', use: 'fire, table and couch under the tall front of the saltbox', box: [2, 1, 3, 6, 7, 7] },
  { level: 1, name: 'Outshot', use: 'the bed nook (west) and the work corner: barrel, bench, stove (east)', box: [2, 1, 8, 6, 3, 9] },
  { level: 2, name: 'Parlour', use: "the wing's ground floor: a second table, the stair up", box: [8, 1, 2, 10, 3, 9] },
  { level: 2, name: 'Chamber', use: "the wing's upper floor: two beds under the hero gable", box: [8, 5, 2, 10, 7, 9] },
  { level: 3, name: 'Kitchen', use: 'smoker, counters, shelf; its own door to the yard', box: [12, 1, 5, 15, 5, 8] },
  { level: 4, name: 'Cellar', use: 'stores under the hall: chests, kelp, hay', box: [2, -3, 3, 6, -1, 9] },
]

export const OPENINGS = {
  frontDoor: { at: [4, 1, 2], facing: 'south', step: [4, 0, 1] },
  // windows: glass cells, the side they look out of, dressing; ground-floor front windows also get a sill and a pot
  windows: {
    1: [
      { name: 'hall front west', cells: [[2, 2, 2], [2, 3, 2]], out: 'north', dress: 'shutters', sill: 'potted_cornflower' },
      { name: 'hall front east', cells: [[6, 2, 2], [6, 3, 2]], out: 'north', dress: 'shutters', sill: 'potted_poppy' },
      { name: 'hall east', cells: [[7, 2, 4], [7, 3, 4]], out: 'east', dress: null },
      { name: 'outshot east', cells: [[7, 2, 8], [7, 3, 8]], out: 'east', dress: null },
      { name: 'bed nook west', cells: [[1, 2, 9], [1, 3, 9]], out: 'west', dress: 'shutters' },
      { name: 'outshot back west', cells: [[2, 2, 10]], out: 'south', dress: 'sill', sill: 'potted_fern' },
      { name: 'outshot back east', cells: [[6, 2, 10]], out: 'south', dress: 'sill', sill: 'potted_red_tulip' },
      { name: 'west gable lights', cells: [[1, 5, 4], [1, 6, 4]], out: 'west', dress: null },
      { name: 'west gable lights 2', cells: [[1, 5, 6], [1, 6, 6]], out: 'west', dress: null },
      { name: 'east gable lights', cells: [[7, 5, 4], [7, 6, 4]], out: 'east', dress: null },
      { name: 'east gable lights 2', cells: [[7, 5, 6], [7, 6, 6]], out: 'east', dress: null },
    ],
    2: [
      { name: 'parlour front', cells: [[9, 2, 1], [9, 3, 1]], out: 'north', dress: 'shutters', sill: 'potted_oxeye_daisy' },
      { name: 'parlour east', cells: [[11, 2, 2], [11, 3, 2]], out: 'east', dress: null },
      { name: 'chamber front', cells: [[9, 6, 1], [9, 7, 1]], out: 'north', dress: 'shutters' },
      { name: 'chamber gable', cells: [[9, 9, 1]], out: 'north', dress: null },
      { name: 'chamber back', cells: [[9, 6, 10], [9, 7, 10]], out: 'south', dress: 'shutters' },
      { name: 'chamber back gable', cells: [[9, 9, 10]], out: 'south', dress: null },
      { name: 'chamber east front', cells: [[11, 5, 2], [11, 6, 2]], out: 'east', dress: 'shutters' }, // clear of Level III's roof
      { name: 'chamber east back', cells: [[11, 5, 8], [11, 6, 8]], out: 'east', dress: 'shutters' }, // closed in Level III
    ],
    3: [
      { name: 'kitchen back', cells: [[13, 2, 9], [13, 3, 9]], out: 'south', dress: 'shutters', sill: 'potted_red_tulip' },
      { name: 'kitchen east', cells: [[16, 2, 7]], out: 'east', dress: null },
      { name: 'kitchen front high', cells: [[12, 4, 4], [12, 5, 4]], out: 'north', dress: null },
    ],
  },
  hallToWing: { at: [7, 1, 7], height: 2 }, // Level II: through the hall's east wall into the parlour
  wingToKitchen: { at: [11, 1, 5], height: 2 }, // Level III: knocked through the wing's east (outside) wall
  kitchenDoor: { at: [14, 1, 4], facing: 'south', step: [14, 0, 3] },
  wingStair: { flight: [[10, 1, 6], [10, 2, 7], [10, 3, 8], [10, 4, 9]], rises: 'south', well: [[10, 4, 6], [10, 4, 7], [10, 4, 8]] },
  cellarLadder: { x: 6, z: 7, facing: 'west' }, // against the cellar's east wall, under a hatch in the hall floor
}

// Every piece of furniture, footprint first, by the level that adds it (later levels keep every earlier piece in its
// cell). kind: the furniture spot it records (furniture.py) or a fixture (a block the game uses as itself). Level I's
// two chairs face the table between them, the first under the front east window; every Level I spot is spruce.
export const FURNITURE = {
  1: [
    // Hall: the hearth in the west wall with the couch and rug before it, the table for two under the front window
    { room: 'Hall', kind: 'fixture', part: 'hearth', pos: [1, 1, 5], facing: 'east', block: 'campfire' },
    { room: 'Hall', kind: 'chair', part: 'chair', pos: [6, 1, 3], facing: 'south', wood: 'spruce' },
    { room: 'Hall', kind: 'table', part: 'table', pos: [6, 1, 4], facing: 'north', wood: 'spruce', colour: 'white' },
    { room: 'Hall', kind: 'chair', part: 'chair', pos: [6, 1, 5], facing: 'north', wood: 'spruce' },
    { room: 'Hall', kind: 'blinds', part: 'blinds', pos: [6, 3, 4], facing: 'west', wood: 'spruce' },
    { room: 'Hall', kind: 'couch', part: 'couch', pos: [3, 1, 6], facing: 'west', length: 2, arms: true, wood: 'spruce' },
    { room: 'Hall', kind: 'rug', part: 'rug', cellsAbs: [[2, 1, 5], [2, 1, 6]], colour: 'red' },
    { room: 'Hall', kind: 'clock', part: 'clock', pos: [2, 1, 3], facing: 'east', wood: 'spruce' },
    { room: 'Hall', kind: 'picture', part: 'picture', pos: [5, 3, 3], facing: 'south', wood: 'spruce', colour: 'cyan' },
    { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [4, 5, 5], facing: 'north', chain: 2, wood: 'spruce' },
    // Outshot: the bed nook (west), the work corner (east): barrel and bench, chest, counter and stove, a shelf
    { room: 'Outshot', kind: 'bed', part: 'bed', pos: [2, 1, 8], facing: 'south', colour: 'red' },
    { room: 'Outshot', kind: 'nightstand', part: 'nightstand', pos: [3, 1, 9], facing: 'north', wood: 'spruce' },
    { room: 'Outshot', kind: 'fixture', part: 'pantry', pos: [5, 1, 8], facing: 'up', block: 'barrel' }, // Fisherman
    { room: 'Outshot', kind: 'fixture', part: 'worktop', pos: [6, 1, 8], facing: 'north', block: 'crafting_table' }, // Carpenter
    { room: 'Outshot', kind: 'fixture', part: 'pantry', pos: [4, 1, 9], facing: 'north', block: 'chest' },
    { room: 'Outshot', kind: 'counter', part: 'counter', pos: [5, 1, 9], facing: 'north', topSlab: true, wood: 'spruce' },
    { room: 'Outshot', kind: 'crockery', part: 'plates', pos: [5, 3, 9], facing: 'north', wood: 'spruce', block: 'flower_pot' },
    { room: 'Outshot', kind: 'fixture', part: 'stove', pos: [6, 1, 9], facing: 'north', block: 'furnace' },
    { room: 'Outshot', kind: 'shelf', part: 'shelf', pos: [6, 3, 7], facing: 'west', wood: 'spruce', crockery: 'potted_brown_mushroom' },
    { room: 'Outshot', kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 8], facing: 'north', chain: 0, wood: 'spruce' },
  ],
  2: [
    // Parlour (wing, ground): a table for two by the window, a cupboard, the stair up along the east wall
    { room: 'Parlour', kind: 'chair', part: 'chair', pos: [8, 1, 3], facing: 'south', wood: 'spruce' },
    { room: 'Parlour', kind: 'table', part: 'table', pos: [8, 1, 4], facing: 'north', wood: 'spruce', colour: 'white' },
    { room: 'Parlour', kind: 'chair', part: 'chair', pos: [8, 1, 5], facing: 'north', wood: 'spruce' },
    { room: 'Parlour', kind: 'cupboard', part: 'cupboard', pos: [8, 1, 9], facing: 'north', wood: 'spruce' },
    { room: 'Parlour', kind: 'picture', part: 'picture', pos: [9, 3, 9], facing: 'north', wood: 'spruce', colour: 'green' },
    { room: 'Parlour', kind: 'lamp', part: 'hangingLamp', pos: [9, 3, 6], facing: 'north', chain: 0, wood: 'spruce' },
    // Chamber (wing, upper): two beds under the front gable, a nightstand between, a wardrobe, lamps from the roof
    { room: 'Chamber', kind: 'bed', part: 'bed', pos: [8, 5, 3], facing: 'north', colour: 'light_blue' },
    { room: 'Chamber', kind: 'bed', part: 'bed', pos: [10, 5, 3], facing: 'north', colour: 'light_blue' },
    { room: 'Chamber', kind: 'nightstand', part: 'nightstand', pos: [9, 5, 2], facing: 'south', wood: 'spruce' },
    { room: 'Chamber', kind: 'cupboard', part: 'cupboard', pos: [10, 5, 4], facing: 'west', wood: 'spruce' },
    { room: 'Chamber', kind: 'picture', part: 'picture', pos: [10, 7, 4], facing: 'west', wood: 'spruce', colour: 'light_blue' },
    { room: 'Chamber', kind: 'rug', part: 'rug', cellsAbs: [[9, 5, 3], [9, 5, 4]], colour: 'light_gray' },
    { room: 'Chamber', kind: 'lamp', part: 'hangingLamp', pos: [9, 7, 3], facing: 'north', chain: 3, wood: 'spruce' },
    { room: 'Chamber', kind: 'lamp', part: 'hangingLamp', pos: [8, 7, 8], facing: 'north', chain: 2, wood: 'spruce' },
  ],
  3: [
    // Kitchen: the smoker between two counters on the east wall, a shelf of crockery by the back window, the pantry
    { room: 'Kitchen', kind: 'fixture', part: 'stove', pos: [15, 1, 6], facing: 'west', block: 'smoker' },
    { room: 'Kitchen', kind: 'counter', part: 'counter', pos: [15, 1, 5], facing: 'west', topSlab: true, wood: 'spruce' },
    { room: 'Kitchen', kind: 'counter', part: 'counter', pos: [15, 1, 7], facing: 'west', topSlab: true, wood: 'spruce' },
    { room: 'Kitchen', kind: 'crockery', part: 'plates', pos: [15, 3, 5], facing: 'west', wood: 'spruce', block: 'flower_pot' },
    { room: 'Kitchen', kind: 'crockery', part: 'plates', pos: [15, 3, 7], facing: 'west', wood: 'spruce', block: 'potted_brown_mushroom' },
    { room: 'Kitchen', kind: 'shelf', part: 'shelf', pos: [14, 2, 8], facing: 'north', wood: 'spruce', crockery: 'flower_pot' },
    { room: 'Kitchen', kind: 'fixture', part: 'pantry', pos: [12, 1, 8], facing: 'east', block: 'chest' },
    { room: 'Kitchen', kind: 'lamp', part: 'hangingLamp', pos: [13, 4, 6], facing: 'north', chain: 1, wood: 'spruce' },
  ],
  4: [
    // Cellar: chests along the back, kelp and hay stacked at the front, a lantern from the hall's floor
    { room: 'Cellar', kind: 'fixture', part: 'pantry', pos: [3, -3, 9], facing: 'north', block: 'chest' },
    { room: 'Cellar', kind: 'fixture', part: 'pantry', pos: [4, -3, 9], facing: 'north', block: 'chest' },
    { room: 'Cellar', kind: 'lamp', part: 'hangingLamp', pos: [4, -1, 5], facing: 'north', chain: 0, wood: 'spruce' },
  ],
}

// The walking lines: step -> door -> hall -> outshot; Level II through the east doorway, up the wing's stair to the
// chamber; Level III from the kitchen's own door to the wing; Level IV down the ladder.
export const WALK = {
  1: [[4, 0, 0], [4, 0, 1], [4, 1, 2], [4, 1, 3], [4, 1, 4], [4, 1, 5], [4, 1, 6], [4, 1, 7], [4, 1, 8], [3, 1, 8]],
  2: [[5, 1, 7], [6, 1, 7], [7, 1, 7], [8, 1, 7], [9, 1, 6], [9, 1, 5], [10, 1, 5], [10, 1, 6], [10, 2, 7], [10, 3, 8],
    [10, 4, 9], [9, 5, 9], [8, 5, 9], [8, 5, 8], [8, 5, 5], [8, 5, 4]],
  3: [[14, 0, 2], [14, 0, 3], [14, 1, 4], [14, 1, 5], [13, 1, 5], [12, 1, 5], [11, 1, 5], [10, 1, 5]],
  4: [[6, 1, 7], [6, 0, 7], [6, -3, 7], [5, -3, 7], [4, -3, 6]],
}

// Outside (not furniture spots): flower beds in their own soil either side of the path, the door hood, a lamp post at
// the kitchen door, cellar stores.
export const OUTSIDE = {
  path: [4, 0, 0],
  flowerBeds: [{ x0: 1, x1: 3, z: 0, plants: ['poppy', 'cornflower', 'oxeye_daisy'] }, { x0: 5, x1: 7, z: 0, plants: ['allium', 'poppy', 'cornflower'] }],
  doorHood: { posts: [3, 5], z: 0, y: 4 }, // a gabled hood on two spruce posts over the path, under the eave's lantern
  doorLamp: [4, 3, 1], // hangs from the front eave over the step
  kitchenLamp: [15, 1, 3],
  cellarStores: { kelp: [[2, -3, 3], [2, -2, 3], [3, -3, 3], [2, -3, 4]], hay: [[2, -3, 9], [2, -2, 9], [2, -3, 8]] },
}
