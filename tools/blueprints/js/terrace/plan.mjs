// Terrace (ROADMAP 49, brief tools/blueprints/briefs/terrace.md): the plan, written before any block.
//
// THREE CONCEPTS
//  A. The Weavers' Row. One long two-storey timber range, ridge along the street, a cross gable over each home's door
//     and a shared chimney stack on every party wall. Story: a landlord built the row in one go for his weavers.
//     Calm and believable, but it is the safe answer: from 20 blocks it reads as one long house with bumps, the homes
//     only told apart by their doors, and II is the same roof made longer.
//  B. The Catslide Cottages. A single storey at the front and a long catslide roof sweeping down at the back, a
//     dormer per home lighting its loft bedroom, the ladders under the slope. Story: labourers' cottages built cheap
//     and low. Cosy, but a 4-wide home under a catslide leaves the loft beds hard against the slope, and a low roof
//     is the least striking shape a 13-wide plot can hold.
//  C. The Crow-Step Row. Tall, narrow-fronted brick houses shoulder to shoulder, each home its own steep roof running
//     back from the street between two crow-stepped gables, front and back, so the row reads as a saw of gables;
//     brick piers mark the party walls, every door has its slate canopy and lantern, and each household's chimney
//     rises in the apex of its own back gable. Story: a growing village builds like a market town: narrow plots,
//     deep houses, every household its own gable.
//  Strongest: C. Its outline says "several homes" before you see a single door (one gable per household is exactly
//  what the building is for), it has the strongest silhouette from every side (stepped gables front and back, the
//  saw of ridges between them), and II grows the clearest way a terrace can: one more house, one more gable on the
//  end of the row. The brief's kept cells (furnaces at x 2, 8, 12; barrels at 4, 10, 14; party walls on a 5-block
//  pitch) fit a row of narrow deep houses naturally. Built: C.
//
// STORY: the Crow-Step Row. Three households under one roofline, each its own house, all alike: a front room and a
// kitchen behind on the ground, the bedroom up a ladder on the party wall (a narrow house has no room for a stair),
// open to its own steep roof and lit front and back. The third house, added later (II), is the same house again. One
// door wood for the row; inside, each household keeps its own bed and rug colors.
//
// ROUND 2 (the critic's notes, 2026-11-17): back gables finished like the front (crow steps, white copings, cornice),
// one chimney per home in its back gable's apex (no loose stacks); the belt course, plinth and white quoins carried
// round the end walls, corbels under the side eaves, a kneeler step closing each end eave behind the parapets; a
// window seat, the chest and a rug filling the bedroom's front half; one door wood and one window rhythm (every home
// the same hand: door east, window west).
//
// ON PURPOSE: no hero gable. STYLE.md asks for one mass 25% taller than the rest; a terrace's strength is the repeat,
// so the row's rhythm (gable, pier, gable) carries it. The frontages are 6 wide (an even span) because the kept cells
// fix the party walls on a 5-block pitch: the ridge is two stairs back to back (evenGableRoof) and each crow-stepped
// gable rises in pairs to a two-block top. Ground-floor spots are only each kitchen's table and chair (the brief: the
// east home's ground spots are exactly those two), so the front room's bookcase and the sink are fixtures.
//
// PALETTE 60/30/10: 60 red brick (walls, piers, gables, chimneys) on a stone-brick plinth and stoop (darkest at the
// foot); 30 dark slate (deepslate-tile roofs, canopies, gutters, eave corbels) and spruce (doors, floors, ladders,
// furniture); 10 white dressed stone (polished diorite: copings, cornices, the belt course, quoins, sills). One
// glass: panes.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Each home is drawn in its own frame u = 0..5 across (u 0 and 5 its side walls, u 1..4
// inside, x = L + u).
export const LEVELS = [1, 2]
export const IDS = { 1: 'terrace', 2: 'terrace_2' }
export const SIZES = { 1: [13, 16, 10], 2: [18, 16, 10] }

export const PALETTE = {
  brick: 'bricks', brickStairs: 'brick_stairs',
  plinth: [['stone_bricks', 5], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1]],
  stoop: 'stone_bricks', step: 'stone_brick_stairs', cap: 'stone_bricks', capSlab: 'stone_brick_slab',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  dressed: 'polished_diorite', coping: 'polished_diorite_slab', sill: 'polished_diorite_slab',
  floor: 'spruce_planks', joist: 'dark_oak_log', wood: 'spruce', door: 'spruce', glass: 'glass_pane',
}

// MASSES. Every house: walls u 0..5 x z 1..8 (party walls shared), ground floor y 1..4 on a floor at y 0, the
// bedroom floor at y 5, walls up to y 8, its roof running along z between a front parapet gable (z 1) and a back one
// (z 8), eaves or gutters at y 9 over its side walls, the ridge two stairs at y 13.
export const HOUSE = {
  walls: { z0: 1, z1: 8 }, upperFloor: 5, wallTop: 8, eaveY: 9,
  belt: 5, // a white course round the house at the bedroom floor (the upper windows sit on it)
  cornice: 9, // a white course across each gable where it begins, level with the piers' caps
  corbels: [2, 7], // under the end eaves' overhang (y 7), clear of the end windows' shutters (z 3..5)
  piers: [0, 9], // every wall line stands a block proud of the front (z 0) and the back (z 9)
  roofAlong: [2, 7],
  // the crow steps: brick up to these heights in both gables, a coping slab on each
  steps: { 0: 10, 1: 12, 2: 14, 3: 14, 4: 12, 5: 10 },
  // the chimney: the back gable's apex (u 2..3) capped in stone at y 14, the household's smoke over u 2
  chimney: { us: [2, 3], cap: 14, flue: 2 },
}

// The homes, west to east. L: the x of u = 0. furnace, barrel, chest: the kept containers' u (the brief's cells
// (2|8|12, 1, 7), (4|10|14, 1, 7) and (4|8|14, 6, 2)); sink: the kitchen's cauldron, the back row's free cell next to
// the window; level: the first level with it.
export const HOMES = [
  { name: 'West house', L: 1, furnace: 1, barrel: 3, chest: 3, sink: 2, beds: 'light_blue', rug: 'white', level: 1 },
  { name: 'Middle house', L: 6, furnace: 2, barrel: 4, chest: 2, sink: 3, beds: 'red', rug: 'brown', level: 1 },
  { name: 'East house', L: 11, furnace: 1, barrel: 3, chest: 3, sink: 2, beds: 'yellow', rug: 'green', level: 2 },
]
/** x of a home's column u. */
export const at = (h, u) => h.L + u

export const ROOMS = [
  { name: 'Front room', use: 'the way in from the door, a low bookcase under the window', box: 'u 1..4, y 1..4, z 2..3' },
  { name: 'Kitchen', use: 'table and chair, the stove (the kept furnace), the sink (a cauldron), the barrel, a shelf of crockery, the ladder', box: 'u 1..4, y 1..4, z 4..7' },
  { name: 'Bedroom', use: 'two beds heads to the back wall, a nightstand; in front a desk, the kept chest, a window seat and a rug; open to the roof', box: 'u 1..4, y 6..12, z 2..7' },
]

// OPENINGS (in a home's frame u, absolute y and z)
export const OPENINGS = {
  door: { u: 4, y: 1, z: 1, transom: 3 }, // and its stoop step at (4, 0, 0) and canopy at y 4, z 0
  ladder: { u: 4, z: 5, y0: 1, y1: 5, facing: 'west' }, // on the party wall (u 5), through the bedroom floor
  windows: [
    { name: 'front room', us: [1, 2], ys: [2, 3], z: 1, out: 'north', dress: 'sill' },
    { name: 'bedroom front', us: [1, 2], ys: [6, 7], z: 1, out: 'north', dress: null }, // on the belt course
    { name: 'bedroom over the door', us: [4], ys: [6, 7], z: 1, out: 'north', dress: null },
    { name: 'front gable', us: [2, 3], ys: [10, 11], z: 1, out: 'north', dress: null, lintel: 12 },
    { name: 'kitchen back', us: [2], ys: [2, 3], z: 8, out: 'south', dress: null },
    { name: 'bedroom back', us: [1, 2], ys: [7, 8], z: 8, out: 'south', dress: null },
    { name: 'back gable', us: [2, 3], ys: [10, 11], z: 8, out: 'south', dress: null, lintel: 12 },
  ],
  // the end walls (only where a house's side is the row's end): one shuttered window per floor
  endWindows: [{ z: 4, ys: [2, 3] }, { z: 4, ys: [6, 7] }],
}

// Every furniture piece in a home's frame: pos [u, y, z] (u may name a HOMES field), facing a compass word.
// Ground floor: only the table and the chair are furniture spots (the brief: exactly one of each per home, and the
// east home's ground spots are exactly those two); the rest is fixtures. The walking line keeps u 4 clear from the
// door (z 1) to the ladder (z 5).
const W = 'spruce'
export const FURNITURE = [
  // Kitchen
  { kind: 'table', part: 'table', pos: [2, 1, 4], facing: 'north', wood: W, colour: 'white' },
  { kind: 'chair', part: 'chair', pos: [1, 1, 4], facing: 'east', wood: W },
  { kind: 'fixture', part: 'stove', pos: ['furnace', 1, 7], facing: 'north', block: 'furnace' },
  { kind: 'fixture', part: 'pantry', pos: ['barrel', 1, 7], facing: 'north', block: 'barrel' },
  { kind: 'fixture', part: 'sink', pos: ['sink', 1, 7] }, // a cauldron by the back window
  { kind: 'shelf', part: 'shelf', pos: [1, 3, 6], facing: 'east', wood: W, crockery: 'flower_pot' },
  { kind: 'lamp', part: 'hangingLamp', pos: [2, 4, 5], facing: 'north', wood: W },
  // Front room: a low bookcase under the window with a pot plant on it, a rug at the door, a lamp
  { kind: 'fixture', part: 'bookcase', pos: [1, 1, 2], plant: 'potted_red_tulip' },
  { kind: 'fixture', part: 'bookcase', pos: [2, 1, 2], plant: null },
  { kind: 'rug', part: 'rug', cells: [[3, 1, 2], [3, 1, 3], [2, 1, 3]], colour: 'home' },
  { kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 2], facing: 'north', wood: W },
  // Bedroom, the back half: the beds heads to the back wall, the nightstand
  { kind: 'bed', part: 'bed', pos: [1, 6, 6], facing: 'south', colour: 'home' },
  { kind: 'bed', part: 'bed', pos: [2, 6, 6], facing: 'south', colour: 'home' },
  { kind: 'nightstand', part: 'nightstand', pos: [3, 6, 7], facing: 'north', wood: W },
  // the front half: the desk in the west window, the kept chest, a window seat under the window over the door, a rug
  // over the floor between (the old bed-head cells (u 1..2 or 3..4, z 3..4) stay open floor, carpet only)
  { kind: 'desk', part: 'desk', pos: [1, 6, 2], facing: 'north', wood: W },
  { kind: 'fixture', part: 'chest', pos: ['chest', 6, 2], facing: 'south', block: 'chest' },
  { kind: 'couch', part: 'couch', pos: [4, 6, 2], facing: 'south', length: 1, wood: W, colour: 'home' },
  // (z 5 stays bare boards: the open floor beside the beds where the tests stand a villager, nearer his own beds
  // than any floor over the party wall)
  { kind: 'rug', part: 'rug', cells: [[1, 6, 3], [2, 6, 3], [3, 6, 3], [4, 6, 3], [1, 6, 4], [2, 6, 4], [3, 6, 4], [4, 6, 4]], colour: 'home' },
  { kind: 'lamp', part: 'hangingLamp', pos: [2, 9, 4], facing: 'north', chain: 2, wood: W },
]

// The walking line (home frame): stoop step (4, 0, 0) -> door (4, 1, 1) -> u 4 z 2..4 -> ladder (4, 1..5, 5) -> off
// at (4, 6, 6) -> (3, 6, 6) by the beds; or over the rug to the desk, chest and window seat.
export const WALK = [[4, 0, 0], [4, 1, 1], [4, 1, 2], [4, 1, 3], [4, 1, 4], [4, 1, 5], [4, 5, 5], [4, 6, 5], [4, 6, 6], [3, 6, 6]]

// Outside: the stoop (a stone strip at y 0 along the front between the piers), a rain barrel at the west end (the
// brief's kept barrel (0, 0, 1)), pots on the ground-floor sills.
export const OUTSIDE = {
  rainBarrel: [0, 0, 1],
  pots: { 'front room': ['potted_red_tulip', 'potted_azure_bluet'] },
}
