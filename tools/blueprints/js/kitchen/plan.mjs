// The Kitchen (ROADMAP 49, tools/blueprints/briefs/kitchen.md), the plan: drawn before any wall. Rooms, every
// furniture footprint (kind, cells, facing) and the walking line come first; recipe.mjs then assembles the building
// from the parts library (../parts) to fit this plan.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. "Cookhouse and Smoke Tower": a long low timber cookhouse (gable roof, ridge along x) with a square stone smoke
//      tower at one end where hams hang over the smoker. Story: the village smokes its meat and eats under one roof.
//      A good silhouette, but the tower is a second job-like hero (it reads as the Butcher's) and the hall is a box.
//   B. "Bakehouse with an Oven Apse": a single steep gable over one room, a round brick oven bulging from the back
//      wall under its own catslide, an open serving hatch onto a front terrace of tables. Story: the baker-cook feeds
//      the square through a hatch. Friendly, but from the front it is a cottage with a hatch: the safest of the three.
//   C. "The Lantern Kitchen": a square stone great-kitchen (9 x 9) under a 45-degree hipped roof that rises to a smoke
//      louvre on its crown, the room open to the roof inside; a gabled timber porch on the front, the Chef's own
//      timber room as a little gabled wing on the east, a brick hearth and a tall stack up the back. Story: medieval
//      abbeys built their kitchens apart and of stone, square, fireproof and tall, venting smoke through a lantern in
//      the roof; a village with a Chef builds one the same way, and the whole village eats at its long table.
// Strongest: C. It is the only one you would know from its outline alone (a stone cube, a pyramid, a lantern on top,
// a stack beside it), it tells what happens inside (fire, smoke, a crowd at the table) from 20 blocks away, and its
// pieces stay small: one room does the cooking, the dining and the hearth, so the build stays cheap. A and B are
// ordinary long houses with a kitchen in them. Built: C.
//
// Coordinates: x across the front (west 0 -> east 15), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back (14). The cellar (the cold larder) is y -4..-1 under the great kitchen.
//
// Masses (named parts the owner's feedback can point at):
//   Great Kitchen  the main mass: stone walls x 2..10, z 4..12, y 1..6 (a dark course at y 1), the room x 3..9,
//                  z 5..11 open to the roof. Corner piers proud at the four corners, a battered skirt at y 1 and a
//                  a block out from the walls; the hipped brick roof's eave ring at y 6 a block
//                  out (x 1..11, z 3..13) and a kicked eave a block further and lower (y 5, x 0..12, z 2..14) that
//                  throws its shadow past the piers and pilasters; rings rising to the louvre's sill at y 10, posts
//                  y 11, cap y 12. The four hips are rolls of dark stone blocks; the louvre's cap is dark stone too.
//   Back Gablet    the back wall rising into a little gable over the back window (x 7..9, z 12, a window at y 7),
//                  its roof (ridge y 8, along z) running out over the eave, which breaks round it.
//   Porch          a low gabled timber porch over the front door: deck x 5..7, z 0..3, log posts on stone feet at the
//                  front corners, a beam at y 3, rails down its sides, a boarded ceiling, its ridge (y 5, along z)
//                  well under the great roof's eave, so it reads as its own small gable in front of the big roof.
//   Chef's Room    the east wing: spruce posts and stone walls x 11..14, z 6..10 on the plinth, the room x 11..13,
//                  z 7..9, a 45-degree gable roof (ridge along x at z 8, y 7) tucked under the great roof's eave,
//                  its gable east with a window.
//   Hearth         the smoker in a brick breast in the back wall (x 4, z 12), a hood over it inside, the chimney
//                  breast outside (x 3..5, z 13) and the stack (x 4, z 13) up past the roof to y 11, in the walls' stone with a
//                  dark cap, smoke on top.
//   Cold Larder    the cellar under the great kitchen's room: walls x 3..9, z 5..11, floor y -4, a spruce post holding
//                  the middle, shelves and heaps of stores round its walls, a ladder down under a hatch in the kitchen's
//                  floor (8, 0, 10).

export const SIZE = [16, 13, 15] // w, h, d above the ground (limit 18 x 13 x 16)
export const BASEMENT = 4

// Palette (60/30/10): pale stone-brick walls (60) / fired-brick roof, hearth and kitchen floor (30) / one dark
// stone, deepslate brick, for the footing, the first course and the piers, and spruce for the porch, the Chef's room's
// frame, the louvre and the joinery (10). Two grey stones only. Why: a kitchen is the one building a village makes
// fireproof, so it is stone with a clay-tile roof; the brick hearth and floor inside are the same clay as the roof;
// the dark footing roots the pale walls and the piers carry the eave's corners; the timber is the soft, lived-in part.
export const PALETTE = {
  walls: 'stone_bricks',
  base: 'deepslate_bricks', // the apron, the walls' first course, the piers, the porch posts' feet
  skirt: 'deepslate_brick_stairs',
  trim: 'deepslate_bricks', trimStairs: 'deepslate_brick_stairs', // the hip rolls, the louvre's cap, the chimney's cap
  roof: { full: 'bricks', stairs: 'brick_stairs', slab: 'brick_slab' },
  hearth: 'bricks', hearthStairs: 'brick_stairs',
  floor: 'bricks', // a brick floor in the kitchen, the hearth's clay underfoot
  wingFloor: 'spruce_planks',
  frame: 'spruce_log',
  infill: 'stone_bricks', // the Chef's room: the kitchen's stone between spruce posts
  wood: 'spruce', // doors, trapdoors, stairs as chairs, fences, slabs
  glass: 'glass_pane',
  larderWall: 'stone_bricks',
  larderFloor: 'deepslate_bricks',
}

export const MASSES = {
  kitchen: {
    walls: { x0: 2, x1: 10, z0: 4, z1: 12 }, room: { x0: 3, x1: 9, z0: 5, z1: 11 },
    ring: { x0: 1, x1: 11, z0: 3, z1: 13 }, // one block out: plinth apron, skirt (y 1), corbels (y 5), eaves (y 6)
    centre: [6, 8], wallTop: 6, eaveY: 6, half: 5, tieY: 7, pierTop: 5, kickY: 5, // the kick: ring half + 1 at y 5
    pilasters: [], // none: the kicked eave's shadow and the piers frame the west wall
  },
  porch: {
    deck: { x0: 5, x1: 7, z0: 0, z1: 3 }, posts: [[5, 0], [7, 0]], beamY: 3,
    roof: { axis: 'z', across: [4, 8], along: [0, 3], eaveY: 3, steep: false },
    lamps: [[4, 2, 0], [8, 2, 0]], // hung under the porch's eaves either side of its mouth
  },
  wing: {
    walls: { x0: 10, x1: 14, z0: 6, z1: 10 }, room: { x0: 11, x1: 13, z0: 7, z1: 9 }, posts: [[11, 6], [14, 6], [11, 10], [14, 10]],
    beamY: 4, roof: { axis: 'x', across: [5, 11], along: [11, 15], eaveY: 4, steep: false },
  },
  hearth: { smoker: [4, 1, 12], stack: [4, 13], stackTop: 11, breastTop: 3 },
  gablet: { roof: { axis: 'z', across: [6, 10], along: [12, 14], eaveY: 6, steep: false }, face: 12, window: [8, 7, 12] },
  larder: { walls: { x0: 3, x1: 9, z0: 5, z1: 11 }, depth: 4, post: [6, 8] },
}

export const ROOMS = [
  { name: 'Great Kitchen', use: 'the Chef cooks at the hearth, the village eats at the long table', box: [3, 1, 5, 9, 9, 11] },
  { name: "Chef's Room", use: "the Chef's bed, his own chest", box: [11, 1, 7, 13, 5, 9] },
  { name: 'Cold Larder', use: 'the stores, cool under the floor', box: [4, -3, 6, 8, -1, 10] },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 4], facing: 'south', out: 'north', step: [6, 0, 0] },
  wingDoorway: { at: [10, 1, 8], height: 2 },
  hatch: { at: [8, 10], facing: 'west', ladderFrom: -3 }, // ladder against the east larder wall, hatch at y 0
  windows: [
    { name: 'front west', cells: [[3, 2, 4], [3, 3, 4], [3, 4, 4]], out: 'north' },
    { name: 'front east', cells: [[9, 2, 4], [9, 3, 4], [9, 4, 4]], out: 'north' },
    { name: 'west', cells: [[2, 2, 8], [2, 3, 8], [2, 4, 8]], out: 'west' },
    { name: 'back', cells: [[8, 2, 12], [8, 3, 12], [8, 4, 12]], out: 'south' },
    { name: 'wing front', cells: [[12, 2, 6], [12, 3, 6]], out: 'north', dress: 'shutters' },
    { name: 'wing east', cells: [[14, 2, 8], [14, 3, 8]], out: 'east', dress: 'shutters' },
    { name: 'wing gable', cells: [[14, 5, 8]], out: 'east' },
  ],
}

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a furniture spot) or a fixture
// (a vanilla block the game uses as itself: the smoker, chests). part: the furnitureParts part that draws it.
// facing: the way a person using it faces.
export const FURNITURE = [
  // Great Kitchen. The back wall: the hearth (the smoker, drawn with its breast), the pantry chest beside it, the
  // dresser counters with plates on them; the walk-in cell in front of the smoker (4, 1, 11) stays clear.
  { room: 'Great Kitchen', kind: 'counter', part: 'counter', pos: [3, 1, 11], facing: 'south', wood: 'spruce' },
  { room: 'Great Kitchen', kind: 'fixture', part: 'doubleChest', pos: [5, 1, 11], facing: 'north', along: 'east' },
  { room: 'Great Kitchen', kind: 'counter', part: 'counter', pos: [7, 1, 11], facing: 'south', topSlab: true, wood: 'spruce' },
  { room: 'Great Kitchen', kind: 'counter', part: 'counter', pos: [8, 1, 11], facing: 'south', topSlab: true, wood: 'spruce' },
  { room: 'Great Kitchen', kind: 'crockery', part: 'plates', pos: [7, 3, 11], facing: 'south', wood: 'spruce', block: 'flower_pot' },
  // The west wall: a run of worktops under the window, shelves of crockery either side of it
  ...[6, 7, 8, 9, 10].map(z => ({ room: 'Great Kitchen', kind: 'counter', part: 'counter', pos: [3, 1, z], facing: 'west', topSlab: z === 7 || z === 9, wood: 'spruce' })),
  { room: 'Great Kitchen', kind: 'crockery', part: 'plates', pos: [3, 3, 7], facing: 'west', wood: 'spruce', block: 'potted_red_mushroom' },
  { room: 'Great Kitchen', kind: 'crockery', part: 'plates', pos: [3, 3, 9], facing: 'west', wood: 'spruce', block: 'flower_pot' },
  { room: 'Great Kitchen', kind: 'shelf', part: 'shelf', pos: [3, 3, 6], facing: 'east', wood: 'spruce', crockery: 'flower_pot' },
  { room: 'Great Kitchen', kind: 'shelf', part: 'shelf', pos: [3, 3, 10], facing: 'east', wood: 'spruce', crockery: 'potted_brown_mushroom' },
  { room: 'Great Kitchen', kind: 'lamp', part: 'tableLamp', pos: [3, 1, 5], facing: 'east', wood: 'spruce' },
  // The long table in the middle, under the lamps on the tie beam: a cloth over three legs, two chairs each side
  ...[5, 6, 7].map(x => ({ room: 'Great Kitchen', kind: 'table', part: 'table', pos: [x, 1, 8], facing: 'north', wood: 'spruce', colour: 'white' })),
  { room: 'Great Kitchen', kind: 'chair', part: 'chair', pos: [5, 1, 7], facing: 'south', wood: 'spruce' },
  { room: 'Great Kitchen', kind: 'chair', part: 'chair', pos: [7, 1, 7], facing: 'south', wood: 'spruce' },
  { room: 'Great Kitchen', kind: 'chair', part: 'chair', pos: [5, 1, 9], facing: 'north', wood: 'spruce' },
  { room: 'Great Kitchen', kind: 'chair', part: 'chair', pos: [7, 1, 9], facing: 'north', wood: 'spruce' },
  // The east wall: a clock, a cloth on the wall, the larder hatch in the back corner
  { room: 'Great Kitchen', kind: 'clock', part: 'clock', pos: [9, 1, 6], facing: 'west', wood: 'spruce' },
  { room: 'Great Kitchen', kind: 'picture', part: 'picture', pos: [9, 3, 7], facing: 'west', wood: 'spruce', colour: 'red' },
  // Lamps on chains from the tie beams, over the table and the hearth's work
  { room: 'Great Kitchen', kind: 'lamp', part: 'hangingLamp', pos: [6, 5, 8], facing: 'north', chain: 1, wood: 'spruce' },
  { room: 'Great Kitchen', kind: 'lamp', part: 'hangingLamp', pos: [4, 5, 8], facing: 'north', chain: 1, wood: 'spruce' },
  { room: 'Great Kitchen', kind: 'lamp', part: 'hangingLamp', pos: [8, 5, 8], facing: 'north', chain: 1, wood: 'spruce' },
  // Chef's Room: his bed along the back wall, head to the east (a vanilla bed, no furniture spot: a workplace's beds
  // stay vanilla), a nightstand at its head, his own chest
  { room: "Chef's Room", kind: 'fixture', part: 'plainBed', pos: [12, 1, 9], facing: 'east', colour: 'white' },
  { room: "Chef's Room", kind: 'nightstand', part: 'nightstand', pos: [13, 1, 8], facing: 'west', wood: 'spruce' },
  { room: "Chef's Room", kind: 'fixture', part: 'chest', pos: [13, 1, 7], facing: 'west', block: 'chest' },
  { room: "Chef's Room", kind: 'lamp', part: 'hangingLamp', pos: [12, 5, 8], facing: 'north', chain: 1, wood: 'spruce' },
  // Cold Larder: the stores in two double chests, shelves of pots and candles over heaps of produce, two lamps
  { room: 'Cold Larder', kind: 'fixture', part: 'doubleChest', pos: [4, -3, 7], facing: 'east', along: 'south' },
  { room: 'Cold Larder', kind: 'fixture', part: 'doubleChest', pos: [5, -3, 10], facing: 'north', along: 'east' },
  ...[5, 6, 7].map(x => ({ room: 'Cold Larder', kind: 'shelf', part: 'shelf', pos: [x, -2, 6], facing: 'north', wood: 'spruce' })),
  ...[7, 8].map(z => ({ room: 'Cold Larder', kind: 'shelf', part: 'shelf', pos: [8, -2, z], facing: 'east', wood: 'spruce' })),
  { room: 'Cold Larder', kind: 'lamp', part: 'hangingLamp', pos: [5, -1, 8], facing: 'north', wood: 'spruce' },
  { room: 'Cold Larder', kind: 'lamp', part: 'hangingLamp', pos: [7, -1, 9], facing: 'north', wood: 'spruce' },
]

// The larder's produce, heaped against its walls (not furniture spots)
export const PRODUCE = [
  // under the shelves and against the walls
  [5, -3, 6, 'pumpkin'], [6, -3, 6, 'melon'], [7, -3, 6, 'pumpkin'], [8, -3, 7, 'hay_block'], [8, -3, 8, 'melon'],
  [4, -3, 6, 'hay_block'], [4, -2, 6, 'pumpkin'], [4, -3, 9, 'pumpkin'], [7, -3, 10, 'hay_block'],
  // on the shelves: potted mushrooms (they like a cellar) and candles
  [5, -1, 6, 'potted_brown_mushroom'], [6, -1, 6, 'candle', { candles: '3', lit: 'false', waterlogged: 'false' }],
  [7, -1, 6, 'potted_red_mushroom'], [8, -1, 7, 'candle', { candles: '2', lit: 'false', waterlogged: 'false' }],
  [8, -1, 8, 'flower_pot'],
]

// The walking line: the Chef waits at (6, 0, -3), walks up the porch step to the door, along the west aisle (x 4)
// past the table to the walk-in cell before the smoker (4, 1, 11); the east aisle (x 8) leads to the Chef's Room
// doorway (10, 1, 8) and the larder hatch (8, 0, 10), down the ladder to the larder's floor (8, -3, 10).
export const WALK = [
  [6, 0, -3], [6, 0, -1], [6, 0, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [5, 1, 6],
  [4, 1, 6], [4, 1, 7], [4, 1, 8], [4, 1, 9], [4, 1, 10], [4, 1, 11],
  [7, 1, 6], [8, 1, 6], [8, 1, 7], [8, 1, 8], [9, 1, 8], [10, 1, 8], [11, 1, 8], [12, 1, 8],
  [8, 1, 9], [8, 0, 10], [8, -1, 10], [8, -2, 10], [8, -3, 10], [8, -3, 9], [7, -3, 9], [5, -3, 9], [5, -3, 7],
]

// Outside (not furniture spots): herb beds in their own soil either side of the porch.
export const OUTSIDE = {
  herbs: [{ x0: 2, x1: 4, z: 1, plants: ['fern', 'oxeye_daisy', 'fern'] }, { x0: 8, x1: 10, z: 1, plants: ['fern', 'allium', 'fern'] }],
}
