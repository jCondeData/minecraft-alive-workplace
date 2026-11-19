// Ball Workshop, the plan (ROADMAP 50.12, the method of Milestone 49): drawn before any wall. Story, palette, masses,
// rooms, openings, every furniture footprint and the walking line come first; recipe.mjs then assembles it from the
// parts library (../parts) in passes.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. "Apricorn Yard": a low L of white workshop round a walled drying court, a long 45-degree roof on each arm,
//      the table at the inner corner, chests along the court. Story: the smith works where he can see his apricorns
//      drying in the sun. Weak: the court eats the plot, the chests end up as a row, and a low L reads as any farm.
//   B. "The Drying Loft": a tall gable-fronted house in two halves, a white plaster workshop below, a red-clad loft
//      jettied out over it on brackets and slatted with louvres where the apricorns dry on trays, a dark band (the
//      jetty's beam) between them with a white boss and a button set in it over the door; a brick forge stack up
//      the east side; a low stone-footed store lean-to behind. Story: Kurt-style ball-making takes a day of drying,
//      so the house is built round its loft, and from the street it reads as a Poke Ball: red over a black band over
//      white, the button in the middle.
//   C. "Round Smithy": an octagonal workshop under a conical roof with a ball on the tip. Story: a sign you can't
//      miss. Weak: the ball-on-a-tower is the Trade Hall's (a copy of a neighbour), and a round plan wastes the
//      corners the chests need.
// Strongest: B. It has a real reason to look the way it does (the loft is the trade's second room, not decoration),
// its silhouette is one strong gable with a jetty, a stack and a lean-to (three masses, one hero), and its colours
// say "ball maker" from twenty blocks away without a single literal ball. It is the harder build (a jetty on
// brackets, louvres, a stack through the eave), so it's the one built.
//
// Coordinates: x across the front (west 0 -> east 14), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The Ball Smith waits at (6, 0, -3): the door is on that axis, x = 6.
//
// Masses (named parts the owner's feedback can point at):
//   Workshop  the white ground storey: dark oak posts x 1..11, z 2..12 (a block proud of the plaster), plaster
//             x 2..10, z 3..11, the room x 3..9, z 4..10 (7 x 7), walls y 1..4 (a tall forge room, and a white
//             half as tall as the red one), the beam ring (the black band) y 5.
//   Loft      the red drying loft: jettied a block out over the front on a sill beam and stair brackets (z 1),
//             walls on the post line at the sides, y 6..8, plate y 9; louvres in its wide bays, tie beams at y 9.
//   Roof      one 45-degree gable, ridge along z (the gable to the street), across x 0..12, along z 0..13, eave y 9,
//             ridge y 15; deepslate tiles, dark oak barge boards. The front gable carries a hoist arm and its lamp.
//   Stack     the forge's brick stack outside the east wall (x 12..14, z 6..8): a 3 x 3 breast past the eave, a
//             ledge, a one-flue shaft past the ridge, smoking. The forge mouth (a furnace) opens into the workshop.
//   Store     the lean-to behind (x 2..10, z 12..15): plaster on the stone plinth, a single-pitch roof from the loft's
//             back wall (y 7) down to the eave (y 4, z 16); the smith's chests, two doubles.
//   Band      the jetty's sill beam over the door, with a white boss and a stone button: the ball's button.

export const SIZE = [15, 18, 17] // w, h, d (limit 18 x 28 x 20)

// Palette (60/30/10): white plaster below and red mangrove above (the walls, 60) / dark: deepslate plinth and tiles,
// dark oak frame and band (30) / brick stack, the boss and button, the apricorns' colours on the trays, lanterns
// (10). Why: a ball reads red over black over white; the dark roof and plinth bracket the two coloured halves so
// they read as one object, and the brick stack is the only warm stone, the forge.
export const PALETTE = {
  stone: [['cobbled_deepslate', 6], ['polished_deepslate', 2], ['deepslate_bricks', 2]],
  stoneStairs: 'cobbled_deepslate_stairs',
  plaster: [['white_concrete', 9], ['polished_diorite', 1]],
  frame: 'dark_oak_log',
  trim: 'dark_oak',
  cladding: 'mangrove_planks',
  louvre: 'mangrove_trapdoor',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'polished_andesite', // the workshop and store: stone flags by a forge
  loftFloor: 'spruce_planks', // the workshop's ceiling, the loft's boards
  wood: 'spruce', // doors' frames aside: stairs, slabs, fences, trays
  door: 'dark_oak',
  glass: 'glass_pane',
  brick: { full: 'bricks', stairs: 'brick_stairs', slab: 'brick_slab' },
  bandStone: 'polished_deepslate', // the band round the stack and the flags of the front path
  boss: 'white_concrete',
  button: 'polished_blackstone_button',
  // the seven apricorns of Cobblemon, drying on the trays (carpets): red, yellow, green, blue, pink, black, white
  apricorns: ['red', 'yellow', 'green', 'blue', 'pink', 'black', 'white'],
}

export const MASSES = {
  workshop: {
    posts: { x0: 1, x1: 11, z0: 2, z1: 12 },
    plaster: { x0: 2, x1: 10, z0: 3, z1: 11 },
    room: { x0: 3, x1: 9, z0: 4, z1: 10 },
    wallTop: 4, beamY: 5,
    frontPosts: [1, 5, 7, 11], backPosts: [1, 5, 7, 11], // bays 2..4 | 6 (the door) | 8..10
    sidePosts: [2, 6, 8, 12], // bays 3..5 | 7 (west window, east forge) | 9..11
  },
  loft: {
    walls: { x0: 1, x1: 11, z0: 1, z1: 12 },
    room: { x0: 2, x1: 10, z0: 2, z1: 11 },
    floorY: 5, wallY: [6, 8], plateY: 9,
    frontPosts: [1, 5, 7, 11], backPosts: [1, 5, 7, 11], sidePosts: [1, 6, 8, 12],
    brackets: [1, 5, 7, 11], // under the sill beam at (x, 4, 1)
    ties: [6, 8], // tie beams across at y 9
  },
  roof: { axis: 'z', across: [0, 12], along: [0, 13], eaveY: 9, steep: false },
  stack: { cx: 13, cz: 7, tiers: [{ w: 3, top: 11 }, { w: 1, top: 15 }], band: 5 }, // the band carried round the breast at the beam's height
  store: { x0: 2, x1: 10, z0: 12, z1: 15, room: { x0: 3, x1: 9, z0: 12, z1: 14 }, wallTop: 3, plateY: 4,
    // the lean-to's roof rows: z -> y (stairs rising north to the loft's back wall), x 2..10
    roofRows: [[13, 7], [14, 6], [15, 5], [16, 4]] },
}

export const ROOMS = [
  { name: 'Workshop', use: "the smith's table, the forge, the anvil, the bench and the near chests", box: [3, 1, 4, 9, 4, 10] },
  { name: 'Store', use: 'apricorns in, balls out: two double chests behind the workshop', box: [3, 1, 12, 9, 3, 14] },
  { name: 'Loft', use: "the drying loft: apricorn trays by the warm flue wall and the louvres, a sorting table, the smith's bed and his order desk at the taking-in window", box: [2, 6, 2, 10, 8, 11] },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 3], facing: 'south', step: [6, 0, 2] },
  storeDoorway: { at: [6, 1, 11], height: 2 },
  stairs: { flight: [[3, 1, 5], [3, 2, 6], [3, 3, 7], [3, 4, 8], [3, 5, 9]], rises: 'south',
    well: [[3, 5, 5], [3, 5, 6], [3, 5, 7], [3, 5, 8]], rail: [[2, 6, 9], [2, 6, 4], [4, 6, 4], [4, 6, 9]] },
  // ground windows: two high, a sill one out with a potted flower (red and white, the ball's colours)
  windows: [
    { name: 'front west', cells: [[3, 2, 3], [3, 3, 3]], out: 'north', pot: 'potted_red_tulip' },
    { name: 'front east', cells: [[9, 2, 3], [9, 3, 3]], out: 'north', pot: 'potted_white_tulip' },
    { name: 'west front', cells: [[2, 2, 4], [2, 3, 4]], out: 'west', pot: 'potted_white_tulip' },
    { name: 'west middle', cells: [[2, 2, 7], [2, 3, 7]], out: 'west', pot: 'potted_red_tulip' },
    { name: 'west back', cells: [[2, 2, 10], [2, 3, 10]], out: 'west', pot: 'potted_white_tulip' },
    { name: 'east front', cells: [[10, 2, 4], [10, 3, 4]], out: 'east', pot: 'potted_red_tulip' },
    { name: 'east back', cells: [[10, 2, 10], [10, 3, 10]], out: 'east', pot: 'potted_white_tulip' },
    { name: 'store back west', cells: [[5, 2, 15], [5, 3, 15]], out: 'south', pot: null },
    { name: 'store back east', cells: [[7, 2, 15], [7, 3, 15]], out: 'south', pot: null },
  ],
  // the loft: louvres in the wide bays, glass in the narrow ones (the taking-in window over the door)
  louvres: [
    { cells: 'x', line: 1, from: 2, to: 4, out: 'north' }, { cells: 'x', line: 1, from: 8, to: 10, out: 'north' },
    { cells: 'z', line: 1, from: 2, to: 5, out: 'west' }, { cells: 'z', line: 1, from: 9, to: 11, out: 'west' },
    { cells: 'z', line: 11, from: 2, to: 5, out: 'east' }, { cells: 'z', line: 11, from: 9, to: 11, out: 'east' },
  ],
  loftGlass: [
    [6, 6, 1], [6, 7, 1], // the taking-in window under the hoist
    [1, 6, 7], [1, 7, 7], // west, over the stair
    [6, 8, 12], // back, over the store's roof
  ],
  gableGlass: [[6, 11, 1], [6, 12, 1], [6, 11, 12], [6, 12, 12]], gableStuds: [5, 7],
  hoist: { at: [6, 14, 1], out: 'north', drop: 0 }, // the arm at (6, 14, 0) under the ridge, its lamp hung at (6, 13, 0)
  boss: { at: [6, 5, 1], button: [6, 5, 0], lamp: [6, 4, 1] },
}

// Every piece of furniture, footprint first (the Daub House's conventions: kind, part, pos, facing = the way a person
// using it faces). Fixtures (job block, chests, anvil, forge) are vanilla blocks the game uses as themselves.
const candles = (colour, n) => [`${colour}_candle`, { candles: String(n), lit: 'false', waterlogged: 'false' }]
export const FURNITURE = [
  // Workshop: the smithing table in the middle with room all round; the anvil between it and the forge; the bench
  // (three counters on the back wall, ball blanks on them as red and white candles) and its stool; the near chests
  // under the front-east window; two lamps hung from the loft's joists.
  { room: 'Workshop', kind: 'fixture', part: 'job', pos: [6, 1, 7], block: 'smithing_table' },
  { room: 'Workshop', kind: 'fixture', part: 'anvil', pos: [8, 1, 8], facing: 'north', block: 'anvil' },
  { room: 'Workshop', kind: 'fixture', part: 'forge', pos: [10, 1, 7], facing: 'west', block: 'furnace' },
  { room: 'Workshop', kind: 'counter', part: 'counter', pos: [3, 1, 10], facing: 'south', topSlab: true, wood: 'spruce' },
  { room: 'Workshop', kind: 'counter', part: 'counter', pos: [4, 1, 10], facing: 'south', topSlab: true, wood: 'spruce' },
  { room: 'Workshop', kind: 'counter', part: 'counter', pos: [5, 1, 10], facing: 'south', topSlab: true, wood: 'spruce' },
  { room: 'Workshop', kind: 'fixture', part: 'pot', pos: [3, 3, 10], on: candles('red', 3) },
  { room: 'Workshop', kind: 'fixture', part: 'pot', pos: [5, 3, 10], on: candles('white', 2) },
  { room: 'Workshop', kind: 'chair', part: 'chair', pos: [4, 1, 9], facing: 'south', wood: 'spruce' },
  { room: 'Workshop', kind: 'fixture', part: 'doubleChest', pos: [8, 1, 4], facing: 'south', along: 'east' },
  { room: 'Workshop', kind: 'shelf', part: 'wallShelf', pos: [9, 3, 9], facing: 'west', wood: 'spruce', on: candles('red', 4) },
  { room: 'Workshop', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 5], facing: 'north', wood: 'spruce' },
  { room: 'Workshop', kind: 'lamp', part: 'hangingLamp', pos: [7, 4, 9], facing: 'north', wood: 'spruce' },
  // Store: two double chests against the back wall either side of the walk-in, a lamp hung from the roof
  { room: 'Store', kind: 'fixture', part: 'doubleChest', pos: [3, 1, 14], facing: 'north', along: 'east' },
  { room: 'Store', kind: 'fixture', part: 'doubleChest', pos: [8, 1, 14], facing: 'north', along: 'east' },
  { room: 'Store', kind: 'lamp', part: 'hangingLamp', pos: [6, 5, 14], facing: 'north', wood: 'spruce' },
  // Loft: the bed at the back-east corner, a nightstand; the order desk at the taking-in window and its chair; his
  // own chest by the stairhead; a sorting table for the day's apricorns; apricorn trays by the warm flue wall (east)
  // and behind the front louvres; a rug; two lamps on the ties.
  { room: 'Loft', kind: 'bed', part: 'bed', pos: [9, 6, 10], facing: 'south', colour: 'red' },
  { room: 'Loft', kind: 'nightstand', part: 'nightstand', pos: [10, 6, 11], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'desk', part: 'desk', pos: [6, 6, 2], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'chair', part: 'chair', pos: [6, 6, 3], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'fixture', part: 'chest', pos: [2, 6, 11], facing: 'east', block: 'chest' },
  { room: 'Loft', kind: 'table', part: 'table', pos: [6, 6, 7], facing: 'north', wood: 'spruce', colour: 'red' },
  { room: 'Loft', kind: 'table', part: 'table', pos: [7, 6, 7], facing: 'north', wood: 'spruce', colour: 'yellow' },
  { room: 'Loft', kind: 'chair', part: 'chair', pos: [6, 6, 6], facing: 'south', wood: 'spruce' },
  { room: 'Loft', kind: 'chair', part: 'chair', pos: [7, 6, 8], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'rug', part: 'rug', cellsAbs: [[7, 6, 10], [8, 6, 10], [7, 6, 11], [8, 6, 11]], colour: 'white' },
  { room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [5, 8, 8], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [8, 8, 6], facing: 'north', wood: 'spruce' },
]

// Apricorn trays (fixtures, no spots): trays at waist height on fence legs, a tray a top slab, the apricorns a
// carpet on it (all seven colours in turn). East: against the forge flue's warm wall. Front: behind each louvre.
export const RACKS = [
  { along: 'z', line: 10, from: 6, to: 8, base: 6, posts: [6, 8], tiers: [7], start: 3 },
  { along: 'x', line: 2, from: 2, to: 4, base: 6, posts: [2, 4], tiers: [7] },
  { along: 'x', line: 2, from: 8, to: 10, base: 6, posts: [8, 10], tiers: [7], start: 6 },
]

// The walking line: the Ball Smith's spot in front -> the step -> the door -> the table (stand at 6,1,6) -> the
// forge's front (9,1,7) -> the store doorway -> the chests; and up the flight to the loft, past the sorting table to
// the bed and round to the desk.
export const WALK = [
  [6, 0, -3], [6, 0, 1], [6, 0, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [7, 1, 6], [8, 1, 6], [9, 1, 7],
  [7, 1, 9], [6, 1, 10], [6, 1, 11], [6, 1, 12], [5, 1, 13], [7, 1, 13],
  [4, 1, 4], [3, 1, 4], [3, 1, 5], [3, 2, 6], [3, 3, 7], [3, 4, 8], [3, 5, 9], [3, 6, 10], [4, 6, 10], [5, 6, 9],
  [8, 6, 9], [9, 6, 9], [5, 6, 5], [5, 6, 3], [6, 6, 4],
]

// Outside (not furniture spots): beds of red and white tulips in their own soil under the front windows, a short
// path of low flags (slabs) to the step.
export const OUTSIDE = {
  flowerBeds: [{ x0: 2, x1: 4, z: 1, plants: ['red_tulip', 'white_tulip', 'red_tulip'] },
    { x0: 8, x1: 10, z: 1, plants: ['white_tulip', 'red_tulip', 'white_tulip'] }],
  path: [[6, 0, 0], [6, 0, 1]],
}
