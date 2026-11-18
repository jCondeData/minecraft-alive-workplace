// Noble's Manor (Milestone 49), the plan: drawn before any wall. Rooms, every furniture footprint (kind, cells, facing)
// and the walking line come first; recipe.mjs then assembles the four levels from the parts library (../parts).
//
// Three concepts, roughed out before choosing:
//   A. "Hall and cross-wings" (H-plan). A low great-hall range between two tall gabled cross-wings that stand forward
//      and back of it, a porch tower on the hall's middle. Roofs: steep-looking crow-stepped parapet gables on the
//      wings, a long low roof on the hall, a pyramid cap on the tower. Plan: hall in the middle (the ball's floor), the
//      service wing on one side, the parlour and chambers above it. Story: the house of a family that rose from
//      Burghers to Nobles, built the old way in brick with stone dressings, and finished by its grandest room.
//   B. "Tower house". A tall stone keep with a stair turret and a low hall range against it; a pyramid roof, a
//      battlemented parapet. Plan: one room a floor, stacked. Story: a fortified family seat. Strong outline, but the
//      18-block ceiling squeezes the keep and a ballroom has nowhere to go but a lean-to.
//   C. "Courtyard house". Four ranges round a small court with a gatehouse; low hipped roofs. Story: a closed,
//      inward-looking estate. Within 25 x 24 the court is a light well, the ball's centre cell would fall in the open
//      court instead of a hall, and from outside it reads as one big block.
// Strongest: A. It gives every side a recognisable outline (two crow-stepped gables and a tower from the front, a
// stepped H from above), it explains the upgrades instead of bolting them on, and its asymmetry at Level I is honest:
// the family builds the hall, the service wing and the porch tower first (an L with a tower: a real "hall and
// cross-wing" house), walls in a garden behind the hall (II), and only at III builds the east cross-wing, taller and
// wider than the first, as the ballroom: the H, and the symmetry of the front, are completed by the room the Nobles'
// ball is held in. B is the safest silhouette and C the most private; A is the one with a story in its growth.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block: floors and plinths),
// z from the front (z = 0, north) to the back. Front door in the porch tower's north face, a stone step before it.
//
// Masses (named parts the owner's feedback can point at):
//   West wing   the service cross-wing: walls x 1..7, z 1..15, two storeys (floor y 5), crow-stepped parapet gables at
//               front and back, a 45-degree tile roof (ridge along z at x 4, y 13), its kitchen stack up the west side.
//               Ground: Parlour (front, with a stone bay window) and Kitchen (back). Upper: two bedchambers.
//   Hall range  walls x 7..19, z 5..13, one tall storey (y 1..4) with the Long Gallery in its roof (floor y 5), a low
//               long roof (ridge along x at z 9, y 11), a crow-stepped east gable; the hall stack on the back wall.
//   Porch tower x 11..15, z 1..5, three stages to y 10 and a steep tiled pyramid to y 17: the hero, the house's axis.
//   Garden (II) a brick-walled parterre behind the hall (x 8..18, z 14..23): four box-edged knots, a lamp at the cross.
//   Ballroom (III) the east cross-wing: walls x 19..27, z 1..15, one double-height room open to its roof (ridge y 14),
//               a great cross-window to the front, a musicians' gallery at the back, glass doors to the garden.
//   Cellar (IV) a 4-deep vaulted store under the kitchen and the hall's west end, down a hatch from the kitchen.

export const SIZE = { 1: [21, 18, 17], 2: [21, 18, 25], 3: [29, 18, 25], 4: [29, 18, 25] }
export const BASEMENT = 4 // Level IV's cellar depth (the file holds it at the bottom: floor at file y 0, ground floor y 4)

// Palette (60/30/10): red brick walls / dark slate-tile roofs / stone dressings (plinth, quoins, mullions, copings)
// and dark oak joinery. Why: the grand homes of the village so far are timber and plaster; a Noble's house is the one
// built of fired brick, dressed in cut stone at every edge, under a dark roof that makes the brick glow. The styles
// (sandstone, cherry, dark oak, grand...) restyle the stone, the roof and the wood and keep the brick.
export const PALETTE = {
  brick: 'bricks',
  stone: [['stone_bricks', 7], ['cracked_stone_bricks', 2], ['mossy_stone_bricks', 1]], // the plinth (weathered low)
  dress: 'stone_bricks', // quoins, mullions, lintels, kneelers, piers
  dressStairs: 'stone_brick_stairs',
  dressSlab: 'stone_brick_slab', // copings
  datestone: 'chiseled_stone_bricks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  wood: 'dark_oak', // doors, the grand stair, railings, chairs and tables
    beam: 'dark_oak_log',
  floor: 'spruce_planks', // chambers, gallery, parlour
  hallFloor: ['polished_andesite', 'polished_diorite'], // the hall's black-and-white chequer (the ball's floor in I and II)
  ballFloor: ['dark_oak_planks', 'spruce_planks'], // the ballroom's parquet
  plaster: 'dark_oak_planks', // inner partitions: dark oak panelling (the critic: a white terracotta one read as a stray peach slab)
  lining: 'spruce_planks', // ceilings and the boards under the roofs
  glass: 'glass_pane',
  hearthSlab: 'smooth_stone_slab', // a hearth's fire stands on a slab, never on a full block: only the stacks smoke
  garden: { soil: 'grass_block', box: 'azalea_leaves', boxFlower: 'flowering_azalea_leaves', yew: 'spruce_leaves' },
}

// Heights shared by the masses
export const Y = {
  ground: 0, // floors and the plinth
  upper: 5, // the upper floor (wing chambers, Long Gallery, tower chamber), and the string course outside
  wingWallTop: 9, wingEave: 9, // wing roof: ridge 13
  hallWallTop: 6, hallEave: 6, // hall roof: ridge 11
  towerTop: 10, towerRoof: 11, // tower: pyramid to 17
  ceiling: 10, // the wing chambers' boarded ceiling
}

export const MASSES = {
  westWing: {
    walls: { x0: 1, x1: 7, z0: 1, z1: 15 },
    roof: { axis: 'z', across: [0, 8], along: [2, 14], eaveY: 9, steep: false },
    crow: { 1: 10, 2: 12, 3: 12, 4: 14, 5: 12, 6: 12, 7: 10 }, // parapet tops per column (slab-capped)
    partitionGround: 8, partitionUpper: 7,
  },
  hall: {
    walls: { x0: 7, x1: 19, z0: 5, z1: 13 },
    room: { x0: 8, x1: 18, z0: 6, z1: 12 },
    roof: { axis: 'x', across: [4, 14], along: [8, 18], eaveY: 6, steep: false },
    crow: { 5: 8, 6: 10, 7: 10, 8: 12, 9: 12, 10: 12, 11: 10, 12: 10, 13: 8 }, // the east gable (x 19)
  },
  tower: { walls: { x0: 11, x1: 15, z0: 1, z1: 5 }, room: { x0: 12, x1: 14, z0: 2, z1: 4 }, cx: 13, cz: 3, r: 3 },
  ballroom: {
    walls: { x0: 19, x1: 27, z0: 1, z1: 15 },
    room: { x0: 20, x1: 26, z0: 2, z1: 14 },
    roof: { axis: 'z', across: [18, 28], along: [2, 14], eaveY: 9, steep: false },
    crow: { 19: 10, 20: 12, 21: 12, 22: 14, 23: 14, 24: 14, 25: 12, 26: 12, 27: 10 },
    gallery: { x0: 20, x1: 26, z0: 11, z1: 14, y: 5 }, // the musicians' gallery floor
    buttresses: [6, 10], // on the east wall (x 28), between the tall windows
  },
  garden: { x0: 7, x1: 19, z0: 14, z1: 24, wallTop: 3, piers: [7, 11, 15, 19] }, // walls on x 7, x 19 and z 24
  gardenEast: { x0: 19, x1: 27, z0: 16, z1: 24 }, // III: the garden runs on behind the ballroom
  cellar: { x0: 1, z0: 8, x1: 13, z1: 15, depth: 4 }, // walls; inside x 2..12, z 9..14
  stacks: {
    kitchen: { x: 0, z: 11, top: 14, breastTop: 6 }, // up the west wing's side wall, past its ridge (13)
    hall: { x: 10, z: 14, top: 12, breastTop: 7 }, // on the hall's back wall, past its ridge (11)
  },
}

export const ROOMS = [
  { level: 1, name: 'Lobby', use: 'the porch: benches either side, the arch into the hall', box: [12, 1, 2, 14, 4, 4] },
  { level: 1, name: 'Hall', use: 'dining at the dais end, the hearth, the black-and-white floor the guests gather on', box: [8, 1, 6, 18, 4, 12] },
  { level: 1, name: 'Parlour', use: 'sitting room in the west wing front, its bay window to the road', box: [2, 1, 2, 6, 4, 7] },
  { level: 1, name: 'Kitchen', use: 'cooking hearth, counters, the pantry along the back wall', box: [2, 1, 9, 6, 4, 14] },
  { level: 1, name: 'Long Gallery', use: 'upper hall in the roof: books, a couch, the landing of the grand stair', box: [8, 6, 6, 18, 10, 12] },
  { level: 1, name: 'Study', use: 'the tower chamber over the porch', box: [12, 6, 2, 14, 9, 4] },
  { level: 1, name: 'Front chamber', use: 'a single bed under the gable window', box: [2, 6, 2, 6, 9, 6] },
  { level: 1, name: 'Great chamber', use: 'the Nobles\' double bed, chests, a writing desk', box: [2, 6, 8, 6, 9, 14] },
  { level: 2, name: 'Garden', use: 'four knots round a lamp, a bench at the end of the walk', box: [8, 1, 14, 18, 3, 23] },
  { level: 3, name: 'Ballroom', use: 'the Noble\'s Ball: a dance floor 7 x 13 open to the roof, seats along the walls', box: [20, 1, 2, 26, 9, 14] },
  { level: 3, name: 'Musicians\' gallery', use: 'over the ballroom\'s back end, reached from the Long Gallery', box: [20, 6, 11, 26, 9, 14] },
  { level: 4, name: 'Cellar', use: 'wine casks, the family\'s chests and pots, under a vault', box: [2, -3, 9, 12, -1, 14] },
]

// The cells the code reads (NobleBalls and the tests; the ship step moves the constants to these)
export const VENUES = {
  1: [10, 1, 8], // (width / 2, 1, depth / 2) of 21 x 17: the hall's chequer floor
  2: [10, 1, 12], // of 21 x 25: still the hall, in front of the hearth
  ballroom: [23, 1, 8], // the ballroom floor's middle (NobleBalls.BALLROOM)
}
export const DOOR_TEST = { from: [13, 1, -1], inside: 5, goal: [14, 1, 6] } // the grand stair's first step, right inside the arch

export const OPENINGS = {
  frontDoor: { at: [13, 1, 1], facing: 'south', step: [13, 0, 0] },
  arch: { x0: 12, x1: 14, z: 5, y0: 1, y1: 3 }, // three wide, three high, a dark oak beam over it
  parlourDoor: { at: [7, 1, 7], facing: 'east' },
  kitchenDoor: { at: [7, 1, 11], facing: 'east' },
  gardenDoor: { at: [14, 1, 13], facing: 'north' }, // the rain butt beside it outside (13, 1, 14)
  frontChamberDoor: { at: [7, 6, 6], facing: 'east' },
  greatChamberDoor: { at: [7, 6, 10], facing: 'east' },
  studyDoor: { at: [12, 6, 5], facing: 'north' },
  // the grand stair: right inside the arch, along the hall's front wall, rising east to the Long Gallery
  grandStair: { flight: [[14, 1, 6], [15, 2, 6], [16, 3, 6], [17, 4, 6], [18, 5, 6]], rises: 'east', well: [[14, 5, 6], [15, 5, 6], [16, 5, 6], [17, 5, 6]] },
  railing: [[13, 6, 6], [13, 6, 7], [17, 6, 7]],
  // III
  // (z 7-8: at z 8-9 the hall table's east chair (17, 1, 9) stood in the doors' way)
  ballroomDoors: [{ at: [19, 1, 7], facing: 'west', hinge: 'left' }, { at: [19, 1, 8], facing: 'west', hinge: 'right' }],
  galleryDoor: { at: [19, 6, 12], facing: 'west' },
  ballroomGardenDoor: { at: [23, 1, 15], facing: 'north' },
  gardenGate: { x: 19, z: 19 }, // III opens the old garden's east wall onto the new part
  // IV
  hatch: { x: 5, z: 9, facing: 'south' },
  // windows: mullioned (lights: the glazed columns; between them stone), axis 'x' = a wall z = line
  windows: {
    1: [
      { name: 'front chamber', axis: 'x', line: 1, lights: [3, 5], y: [7, 8], out: 'north' },
      { name: 'great chamber', axis: 'x', line: 15, lights: [3, 5], y: [7, 8], out: 'south' },
      { name: 'kitchen back', axis: 'x', line: 15, lights: [3, 5], y: [2, 3], out: 'south' },
      { name: 'kitchen west', axis: 'z', line: 1, lights: [13], y: [2, 3], out: 'west' },
      { name: 'parlour west', axis: 'z', line: 1, lights: [4, 6], y: [2, 4], out: 'west' },
      { name: 'front chamber west', axis: 'z', line: 1, lights: [4], y: [7, 8], out: 'west' },
      { name: 'great chamber west', axis: 'z', line: 1, lights: [13], y: [7, 8], out: 'west' },
      { name: 'wing attic front', axis: 'x', line: 1, lights: [4], y: [11, 11], out: 'north' },
      { name: 'wing attic back', axis: 'x', line: 15, lights: [4], y: [11, 11], out: 'south' },
      { name: 'hall front', axis: 'x', line: 5, lights: [8, 10], y: [2, 4], out: 'north' },
      { name: 'hall back', axis: 'x', line: 13, lights: [15, 17], y: [2, 4], out: 'south' },
      { name: 'hall back west', axis: 'x', line: 13, lights: [12], y: [2, 4], out: 'south' },
      { name: 'hall east', axis: 'z', line: 19, lights: [7], y: [2, 4], out: 'east' },
      { name: 'hall east back', axis: 'z', line: 19, lights: [11], y: [2, 4], out: 'east' },
      { name: 'gallery east', axis: 'z', line: 19, lights: [8, 10], y: [7, 8], out: 'east' },
      { name: 'study front', axis: 'x', line: 1, lights: [13], y: [6, 8], out: 'north' },
      { name: 'study west', axis: 'z', line: 11, lights: [3], y: [7, 7], out: 'west' },
      { name: 'study east', axis: 'z', line: 15, lights: [3], y: [7, 7], out: 'east' },
    ],
    3: [
      { name: 'ballroom great window', axis: 'x', line: 1, lights: [21, 22, 24, 25], y: [2, 7], out: 'north', transom: 5 },
      { name: 'ballroom east 1', axis: 'z', line: 27, lights: [4], y: [2, 7], out: 'east', transom: 5 },
      { name: 'ballroom east 2', axis: 'z', line: 27, lights: [8], y: [2, 7], out: 'east', transom: 5 },
      { name: 'ballroom east 3', axis: 'z', line: 27, lights: [12], y: [2, 7], out: 'east', transom: 5 },
      { name: 'ballroom back west', axis: 'x', line: 15, lights: [21], y: [2, 4], out: 'south' },
      { name: 'ballroom back east', axis: 'x', line: 15, lights: [25], y: [2, 4], out: 'south' },
      { name: 'gallery back', axis: 'x', line: 15, lights: [22, 24], y: [6, 8], out: 'south' },
      { name: 'ballroom gable', axis: 'x', line: 1, lights: [23], y: [10, 11], out: 'north' },
      { name: 'ballroom front side', axis: 'z', line: 19, lights: [3], y: [2, 4], out: 'west' },
    ],
  },
}

// Every piece of furniture, footprint first, per level (each level also has the ones below it). kind: a furniture.py
// kind (recorded as a furniture spot) or a fixture (a vanilla block the game uses as itself). part: the furnitureParts
// part that draws it. facing: the way a person using it faces. Chairs face their table. Kept cells (the live server's
// older Manors): the kitchen's pantry barrels and chest (3..5, 1, 14), the Great chamber's chests (3, 6, 10) facing
// south and (5, 6, 8) facing west, its bed heads (3, 6, 8) and (4, 6, 8), the rain butt (13, 1, 14), the ballroom's
// jukebox (20, 1, 14), the cellar's chests and pots; the hall's (15, 1, 8) is left as floor.
const W = 'dark_oak', K = 'dark_oak' // one joinery wood throughout (fewer kinds of block for the builder to carry)
export const FURNITURE = {
  1: [
    // Lobby: two benches facing across the porch, a lantern from the study floor
    { room: 'Lobby', kind: 'chair', part: 'chair', pos: [12, 1, 3], facing: 'east', wood: W },
    { room: 'Lobby', kind: 'chair', part: 'chair', pos: [14, 1, 3], facing: 'west', wood: W },
    { room: 'Lobby', kind: 'lamp', part: 'hangingLamp', pos: [13, 4, 3], facing: 'north', wood: W },
    // Hall: the dais table for nine at the east end, two armchairs at the hearth, a clock, a cupboard, three lamps
    ...[9, 10, 11, 12].map(z => ({ room: 'Hall', kind: 'table', part: 'table', pos: [16, 1, z], facing: 'north', wood: W, colour: 'white' })),
    ...[9, 10, 11, 12].map(z => ({ room: 'Hall', kind: 'chair', part: 'chair', pos: [15, 1, z], facing: 'east', wood: W })),
    ...[9, 10, 11, 12].map(z => ({ room: 'Hall', kind: 'chair', part: 'chair', pos: [17, 1, z], facing: 'west', wood: W })),
    { room: 'Hall', kind: 'chair', part: 'chair', pos: [16, 1, 8], facing: 'south', wood: W },
    { room: 'Hall', kind: 'chair', part: 'armchair', pos: [8, 1, 12], facing: 'east', wood: W, colour: 'red' },
    { room: 'Hall', kind: 'chair', part: 'armchair', pos: [12, 1, 12], facing: 'west', wood: W, colour: 'red' },
    { room: 'Hall', kind: 'clock', part: 'clock', pos: [8, 1, 9], facing: 'east', wood: W },
    { room: 'Hall', kind: 'picture', part: 'picture', pos: [8, 3, 9], facing: 'east', wood: W, colour: 'red' },
    { room: 'Hall', kind: 'cupboard', part: 'cupboard', pos: [8, 1, 10], facing: 'east', wood: W },
    { room: 'Hall', kind: 'blinds', part: 'blinds', pos: [8, 4, 6], facing: 'south', wood: W },
    { room: 'Hall', kind: 'blinds', part: 'blinds', pos: [10, 4, 6], facing: 'south', wood: W },
    { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [10, 4, 8], facing: 'north', wood: W },
    { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [10, 4, 11], facing: 'north', wood: W },
    { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [16, 4, 10], facing: 'north', wood: W },
    { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [13, 4, 8], facing: 'north', wood: W },
    // Parlour: a couch with arms along the west wall, a low table and two armchairs at it, clock, books, a picture,
    // blinds in the bay
    { room: 'Parlour', kind: 'couch', part: 'couch', pos: [2, 1, 3], facing: 'east', length: 3, arms: true, wood: W, colour: 'red' },
    { room: 'Parlour', kind: 'table', part: 'table', pos: [4, 1, 4], facing: 'north', wood: W, colour: 'white' },
    { room: 'Parlour', kind: 'chair', part: 'armchair', pos: [5, 1, 4], facing: 'west', wood: W, colour: 'red' },
    { room: 'Parlour', kind: 'chair', part: 'armchair', pos: [4, 1, 3], facing: 'south', wood: W, colour: 'red' },
    { room: 'Parlour', kind: 'rug', part: 'rug', cellsAbs: [[3, 1, 3], [3, 1, 4], [3, 1, 5], [4, 1, 5]], colour: 'red' },
    { room: 'Parlour', kind: 'clock', part: 'clock', pos: [6, 1, 2], facing: 'west', wood: W },
    { room: 'Parlour', kind: 'cupboard', part: 'cupboard', pos: [6, 1, 6], facing: 'west', wood: W },
    { room: 'Parlour', kind: 'picture', part: 'picture', pos: [6, 3, 4], facing: 'west', wood: W, colour: 'blue' },
    { room: 'Parlour', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 4], facing: 'north', wood: W },
    ...[3, 4, 5].map(x => ({ room: 'Parlour', kind: 'blinds', part: 'blinds', pos: [x, 3, 1], facing: 'south', wood: W })),
    // Kitchen: the cooking hearth in the west wall, the stove and counters either side of it, the pantry along the back
    // wall (barrel, chest, barrel), a sink and counter on the east wall, shelves of crockery, a work table for three
    { room: 'Kitchen', kind: 'counter', part: 'counter', pos: [2, 1, 12], facing: 'west', wood: K },
    { room: 'Kitchen', kind: 'fixture', part: 'stove', pos: [2, 1, 13], facing: 'east', block: 'furnace' },
    { room: 'Kitchen', kind: 'counter', part: 'counter', pos: [2, 1, 14], facing: 'south', wood: K },
    { room: 'Kitchen', kind: 'fixture', part: 'pantry', pos: [3, 1, 14], facing: 'north', block: 'barrel' },
    { room: 'Kitchen', kind: 'fixture', part: 'pantry', pos: [4, 1, 14], facing: 'north', block: 'chest' },
    { room: 'Kitchen', kind: 'fixture', part: 'pantry', pos: [5, 1, 14], facing: 'north', block: 'barrel' },
    { room: 'Kitchen', kind: 'counter', part: 'counter', pos: [6, 1, 14], facing: 'south', wood: K },
    { room: 'Kitchen', kind: 'counter', part: 'counter', pos: [6, 1, 13], facing: 'east', wood: K },
    { room: 'Kitchen', kind: 'fixture', part: 'sink', pos: [6, 1, 12] },
    { room: 'Kitchen', kind: 'shelf', part: 'shelf', pos: [2, 3, 14], facing: 'north', wood: K, crockery: 'flower_pot' },
    { room: 'Kitchen', kind: 'shelf', part: 'shelf', pos: [6, 3, 14], facing: 'north', wood: K, crockery: 'flower_pot' },
    { room: 'Kitchen', kind: 'shelf', part: 'shelf', pos: [6, 3, 13], facing: 'west', wood: K, crockery: 'flower_pot' },
    { room: 'Kitchen', kind: 'table', part: 'table', pos: [4, 1, 11], facing: 'north', wood: K, colour: 'white' },
    { room: 'Kitchen', kind: 'table', part: 'table', pos: [4, 1, 12], facing: 'north', wood: K, colour: 'white' },
    { room: 'Kitchen', kind: 'chair', part: 'chair', pos: [3, 1, 12], facing: 'east', wood: K },
    { room: 'Kitchen', kind: 'chair', part: 'chair', pos: [5, 1, 12], facing: 'west', wood: K },
    { room: 'Kitchen', kind: 'chair', part: 'chair', pos: [4, 1, 10], facing: 'south', wood: K },
    { room: 'Kitchen', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 12], facing: 'north', wood: K },
    { room: 'Kitchen', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 9], facing: 'north', wood: K },
    // Study (the tower chamber): a desk at the window, its chair, books, a picture
    { room: 'Study', kind: 'desk', part: 'desk', pos: [14, 6, 2], facing: 'north', wood: W },
    { room: 'Study', kind: 'chair', part: 'chair', pos: [14, 6, 3], facing: 'north', wood: W },
    { room: 'Study', kind: 'cupboard', part: 'cupboard', pos: [12, 6, 2], facing: 'south', wood: W },
    { room: 'Study', kind: 'picture', part: 'picture', pos: [12, 8, 4], facing: 'east', wood: W, colour: 'blue' },
    { room: 'Study', kind: 'blinds', part: 'blinds', pos: [13, 8, 2], facing: 'south', wood: W },
    { room: 'Study', kind: 'lamp', part: 'hangingLamp', pos: [13, 9, 3], facing: 'north', wood: W },
    // Long Gallery (the hall's roof storey): a couch under the back eave, bookcases, a reading table, the clock, lamps
    // hung from the ridge, blinds at the gable window
    { room: 'Long Gallery', kind: 'couch', part: 'couch', pos: [9, 6, 12], facing: 'north', length: 3, arms: true, wood: W, colour: 'red' },
    ...[14, 15, 16].map(x => ({ room: 'Long Gallery', kind: 'cupboard', part: 'cupboard', pos: [x, 6, 12], facing: 'north', wood: W })),
    { room: 'Long Gallery', kind: 'table', part: 'table', pos: [10, 6, 9], facing: 'north', wood: W, colour: 'red' },
    { room: 'Long Gallery', kind: 'chair', part: 'chair', pos: [9, 6, 9], facing: 'east', wood: W },
    { room: 'Long Gallery', kind: 'chair', part: 'chair', pos: [11, 6, 9], facing: 'west', wood: W },
    { room: 'Long Gallery', kind: 'clock', part: 'clock', pos: [8, 6, 8], facing: 'east', wood: W },
    { room: 'Long Gallery', kind: 'picture', part: 'picture', pos: [8, 8, 9], facing: 'east', wood: W, colour: 'blue' },
    { room: 'Long Gallery', kind: 'blinds', part: 'blinds', pos: [18, 8, 8], facing: 'west', wood: W },
    { room: 'Long Gallery', kind: 'blinds', part: 'blinds', pos: [18, 8, 10], facing: 'west', wood: W },
    { room: 'Long Gallery', kind: 'lamp', part: 'hangingLamp', pos: [10, 8, 9], facing: 'north', chain: 2, wood: W },
    { room: 'Long Gallery', kind: 'lamp', part: 'hangingLamp', pos: [15, 8, 9], facing: 'north', chain: 2, wood: W },
    // Front chamber: a single bed, its head to the gable wall between two nightstands, a chest, a rug, a picture
    { room: 'Front chamber', kind: 'bed', part: 'bed', pos: [4, 6, 3], facing: 'north', colour: 'blue' },
    { room: 'Front chamber', kind: 'nightstand', part: 'nightstand', pos: [3, 6, 2], facing: 'south', wood: W },
    { room: 'Front chamber', kind: 'nightstand', part: 'nightstand', pos: [5, 6, 2], facing: 'south', wood: W },
    { room: 'Front chamber', kind: 'fixture', part: 'chest', pos: [2, 6, 6], facing: 'east', block: 'chest' },
    { room: 'Front chamber', kind: 'rug', part: 'rug', cellsAbs: [[3, 6, 4], [4, 6, 4], [5, 6, 4]], colour: 'red' },
    { room: 'Front chamber', kind: 'picture', part: 'picture', pos: [2, 8, 5], facing: 'east', wood: W, colour: 'blue' },
    { room: 'Front chamber', kind: 'blinds', part: 'blinds', pos: [3, 8, 2], facing: 'south', wood: W },
    { room: 'Front chamber', kind: 'blinds', part: 'blinds', pos: [5, 8, 2], facing: 'south', wood: W },
    { room: 'Front chamber', kind: 'lamp', part: 'hangingLamp', pos: [4, 9, 4], facing: 'north', wood: W },
    // (ship polish) a wardrobe in the corner and a reading chair in the oriel
    { room: 'Front chamber', kind: 'cupboard', part: 'cupboard', pos: [6, 6, 3], facing: 'west', wood: W },
    { room: 'Front chamber', kind: 'chair', part: 'armchair', pos: [2, 6, 4], facing: 'east', wood: W, colour: 'red' },
    // Great chamber: the double bed against the partition, a nightstand and the bedside chest, the chest at its foot, a
    // desk at the back window, books, lamps
    { room: 'Great chamber', kind: 'bed', part: 'bed', pos: [3, 6, 9], facing: 'north', colour: 'red' },
    { room: 'Great chamber', kind: 'bed', part: 'bed', pos: [4, 6, 9], facing: 'north', colour: 'red' },
    { room: 'Great chamber', kind: 'nightstand', part: 'nightstand', pos: [2, 6, 8], facing: 'south', wood: W },
    { room: 'Great chamber', kind: 'fixture', part: 'chest', pos: [5, 6, 8], facing: 'west', block: 'chest' },
    { room: 'Great chamber', kind: 'fixture', part: 'chest', pos: [3, 6, 10], facing: 'south', block: 'chest' },
    { room: 'Great chamber', kind: 'rug', part: 'rug', cellsAbs: [[4, 6, 10], [3, 6, 11], [4, 6, 11], [5, 6, 11]], colour: 'red' },
    { room: 'Great chamber', kind: 'desk', part: 'desk', pos: [6, 6, 14], facing: 'south', wood: W },
    { room: 'Great chamber', kind: 'chair', part: 'chair', pos: [6, 6, 13], facing: 'south', wood: W },
    { room: 'Great chamber', kind: 'cupboard', part: 'cupboard', pos: [2, 6, 14], facing: 'north', wood: W },
    { room: 'Great chamber', kind: 'picture', part: 'picture', pos: [6, 8, 12], facing: 'west', wood: W, colour: 'red' },
    { room: 'Great chamber', kind: 'blinds', part: 'blinds', pos: [3, 8, 14], facing: 'north', wood: W },
    { room: 'Great chamber', kind: 'blinds', part: 'blinds', pos: [5, 8, 14], facing: 'north', wood: W },
    { room: 'Great chamber', kind: 'lamp', part: 'hangingLamp', pos: [4, 9, 9], facing: 'north', wood: W },
    { room: 'Great chamber', kind: 'lamp', part: 'hangingLamp', pos: [4, 9, 13], facing: 'north', wood: W },
    // (ship polish) a wardrobe by the bed, a reading chair and a low bookcase with a fern by the oriel
    { room: 'Great chamber', kind: 'cupboard', part: 'cupboard', pos: [6, 6, 8], facing: 'west', wood: W },
    { room: 'Great chamber', kind: 'chair', part: 'armchair', pos: [2, 6, 12], facing: 'east', wood: W, colour: 'red' },
    { room: 'Great chamber', kind: 'fixture', part: 'bookcase', pos: [2, 6, 13], plant: 'potted_fern' },
  ],
  2: [],
  3: [
    // Ballroom: couches along both walls at the front end, lamps on brackets, chandeliers from the ridge, the jukebox
    // under the gallery, a sideboard, pictures between the tall windows
    { room: 'Ballroom', kind: 'couch', part: 'couch', pos: [20, 1, 3], facing: 'east', length: 3, arms: true, wood: W, colour: 'red' },
    { room: 'Ballroom', kind: 'couch', part: 'couch', pos: [26, 1, 5], facing: 'west', length: 3, arms: true, wood: W, colour: 'red' },
    { room: 'Ballroom', kind: 'fixture', part: 'jukebox', pos: [20, 1, 14] },
    // the sideboard: two desks with candles, not tables (the modded library sets plates on every table, and plates go
    // only on a kitchen or a dining table)
    { room: 'Ballroom', kind: 'desk', part: 'desk', pos: [25, 1, 14], facing: 'south', wood: W },
    { room: 'Ballroom', kind: 'desk', part: 'desk', pos: [26, 1, 14], facing: 'south', wood: W },
    { room: 'Ballroom', kind: 'picture', part: 'picture', pos: [26, 3, 6], facing: 'west', wood: W, colour: 'blue' },
    { room: 'Ballroom', kind: 'picture', part: 'picture', pos: [26, 3, 10], facing: 'west', wood: W, colour: 'blue' },
    { room: 'Ballroom', kind: 'picture', part: 'picture', pos: [20, 3, 6], facing: 'east', wood: W, colour: 'blue' },
    { room: 'Ballroom', kind: 'lamp', part: 'hangingLamp', pos: [23, 6, 5], facing: 'north', chain: 6, wood: W },
    { room: 'Ballroom', kind: 'lamp', part: 'hangingLamp', pos: [23, 6, 9], facing: 'north', chain: 6, wood: W },
    { room: 'Ballroom', kind: 'lamp', part: 'hangingLamp', pos: [21, 4, 13], facing: 'north', wood: W },
    { room: 'Ballroom', kind: 'lamp', part: 'hangingLamp', pos: [25, 4, 13], facing: 'north', wood: W },
    // Musicians' gallery: three chairs facing the room over the railing, a lamp from the roof
    ...[21, 23, 25].map(x => ({ room: 'Musicians\' gallery', kind: 'chair', part: 'chair', pos: [x, 6, 13], facing: 'north', wood: W })),
    { room: 'Musicians\' gallery', kind: 'lamp', part: 'hangingLamp', pos: [23, 9, 13], facing: 'north', chain: 3, wood: W },
  ],
  4: [
    { room: 'Cellar', kind: 'fixture', part: 'pantry', pos: [5, -3, 14], facing: 'north', block: 'chest' },
    { room: 'Cellar', kind: 'fixture', part: 'pantry', pos: [9, -3, 14], facing: 'north', block: 'chest' },
    { room: 'Cellar', kind: 'lamp', part: 'hangingLamp', pos: [4, -1, 11], facing: 'north', wood: W },
    { room: 'Cellar', kind: 'lamp', part: 'hangingLamp', pos: [10, -1, 11], facing: 'north', wood: W },
  ],
}
// Wall lamps (lanterns hung under a stone block out of the wall) light the ballroom's seats: [x, y, z, the wall's side]
// Oriels on the west flank (ship polish: the critic's plain left side): the chambers' west windows pushed out between
// stone piers, on the string course, under the wing's eave: [z of the glass]
export const ORIELS = [4, 13]
export const BRACKET_LAMPS = { 3: [[20, 4, 4, 'west'], [26, 4, 4, 'east'], [20, 4, 9, 'west'], [26, 4, 9, 'east']] }

// The walking lines: in by the front door, through the porch and the arch, onto the grand stair (the doorway test's
// step), up to the Long Gallery and through to the chambers and the study; across the hall to the parlour, the
// kitchen and the garden; at III through the double doors into the ballroom and up to its gallery.
export const WALK = [
  [13, 0, -1], [13, 0, 0], [13, 1, 1], [13, 1, 2], [13, 1, 3], [13, 1, 4], [13, 1, 5], [13, 1, 6], [14, 1, 6],
  [15, 2, 6], [16, 3, 6], [17, 4, 6], [18, 5, 6], [18, 6, 7], [18, 6, 8], [12, 6, 8], [12, 6, 6], [12, 6, 5], [12, 6, 4],
  [8, 6, 8], [8, 6, 10], [7, 6, 10], [6, 6, 10], [8, 6, 6], [7, 6, 6], [6, 6, 6],
  [13, 1, 7], [8, 1, 7], [7, 1, 7], [6, 1, 7], [8, 1, 11], [7, 1, 11], [6, 1, 11], [14, 1, 12], [14, 1, 13], [14, 1, 14],
  [18, 1, 7], [19, 1, 7], [20, 1, 7], [23, 1, 8], [18, 6, 12], [19, 6, 12], [20, 6, 12],
]

// The ground: flower beds of their own soil along the hall's front, the terrace and rain butt behind it, a lamp post
// by the step; the garden at II.
export const OUTSIDE = {
  frontBeds: [{ x0: 8, x1: 10, z: 3, plants: ['peony', 'rose_bush', 'peony'] }, { x0: 16, x1: 17, z: 3, plants: ['rose_bush', 'peony'] }],
  terrace: { x0: 8, x1: 18, z0: 14, z1: 15 },
  rainButt: [13, 1, 14],
  eastBed: { x: 20, z0: 6, z1: 12, plants: ['lilac', 'cornflower', 'lilac', 'cornflower', 'lilac', 'cornflower', 'lilac'] }, // gone at III
  banners: [[12, 7, 0, 'blue'], [14, 7, 0, 'blue']], // the family's colours on the tower, either side of the study window
  // II: the parterre (box hedges round flowers), its walks, the lamp at the cross, the bench at the end of the walk
  knots: [
    { x0: 8, x1: 12, z0: 16, z1: 18, flowers: ['red_tulip', 'white_tulip', 'red_tulip'] },
    { x0: 14, x1: 18, z0: 16, z1: 18, flowers: ['white_tulip', 'red_tulip', 'white_tulip'] },
    { x0: 8, x1: 12, z0: 20, z1: 22, flowers: ['red_tulip', 'white_tulip', 'red_tulip'] },
    { x0: 14, x1: 18, z0: 20, z1: 22, flowers: ['white_tulip', 'red_tulip', 'white_tulip'] },
  ],
  yews: [[12, 18], [14, 18], [12, 20], [14, 20]], // clipped yew columns at the cross's four corners
  crossLamp: [13, 19],
  bench: { x0: 12, x1: 14, z: 23 },
  // III: the garden's east half behind the ballroom (terrace, rose beds, a lamp)
  eastKnots: [
    { x0: 20, x1: 22, z0: 20, z1: 23, flowers: ['rose_bush', 'peony'] },
    { x0: 24, x1: 26, z0: 20, z1: 23, flowers: ['peony', 'rose_bush'] },
  ],
}
