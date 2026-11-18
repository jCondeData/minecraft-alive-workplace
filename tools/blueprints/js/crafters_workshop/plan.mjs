// Crafter's Workshop (crafters_workshop, ROADMAP 50.7), the plan: drawn before any wall. Rooms, every furniture
// footprint (kind, cells, facing) and the walking line come first; recipe.mjs then assembles the shop from the parts
// library's (../parts) named parts to fit this plan. The brief: tools/blueprints/briefs/crafters_workshop.md.
//
// Three rough concepts (drawn on paper first):
//   A  The Saw-Tooth Shop. Massing: one brick hall, 11 x 8, under THREE saw teeth seen end-on from the street, a low
//      pentice porch the full width of the front, a twin-flue forge stack up the west side. Roof: a north-light
//      (saw-tooth) roof, each tooth a 45-degree slope climbing east and a glazed face dropping back to a copper gutter.
//      Plan: one tall workroom lit from above, forge on the west wall, benches round the walls, a work table in the
//      middle. Story: the guild of crafters put up a proper shop with even light from above for fine work.
//   B  The Jettied Shopfront. Massing: a two-storey timber house, gable to the street, its upper floor jettied out
//      over a fold-down shop board, a hoist beam in the gable, a lean-to forge on one side. Roof: one steep gable.
//      Plan: shop and bench below, stock loft above by a ladder. Story: a crafter who sold from his window.
//   C  The Work Yard. Massing: an L of two low sheds round a gated yard. Roof: two lean-tos. Plan: an open bench under
//      the eaves, a store, a small kiln in the yard. Story: a jobbing crafter working outdoors in fair weather.
// Strongest: A. Its roof IS the job: a saw-tooth roof is what a workshop looks like and nothing else does, so it reads
// as "the workshop quarter" from 20 blocks on every side (the zig-zag on the front and back, the glass bands and the
// stack on the sides), where B is the village's generic workshop-house and C spreads thin in a 13 x 11 plot and reads as
// fences and sheds. A is also the one that grows from its section: three teeth, three bays, three porch bays, one rhythm.
//
// Story: the Saw-Tooth Shop. When the village's crafters still had a guild they put up one proper shop: fired brick
// walls on a dressed stone footing with piers at the corners, a spruce frame for the glass, and a roof of three saw teeth tiled in
// deepslate, each tooth's steep face glazed to the east so morning light falls evenly on the benches. Copper lines the
// valleys and caps the teeth. The forge and a stove share a twin-flue stack up the west wall. Today nobody works here
// (the job is retired, 37.16a): the benches, the anvil, the forge and the chests stand as the last crafter left them,
// a plain building of the workshop quarter with no job block in it.
//
// Coordinates: x across the front (west 0 -> east 12), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. 13 x 11 x 11, the size StarterBlueprints.CRAFTERS_WORKSHOP declares.
//
// Masses (named parts the owner's feedback can point at):
//   Hall      the main mass: brick walls x 1..11, z 2..9 (y 1..4), a spruce beam ring at y 5, the workroom x 2..10,
//             z 3..8, tall to the roof: 4 blocks to the beams, 5 to 7 under the teeth.
//   Teeth     the saw-tooth roof: three teeth across x (x 1..3, 4..7, 8..11), each climbing east from y 5 to y 8 and
//             dropping at a glazed face (x 3, 7 inside, x 11 the east wall), running z 1..10 (a block over the front
//             and back gables). Copper gutters in the two valleys (x 4, x 8; tiles at their ends over the gables),
//             copper rods on the three tops at the front. The gables under the teeth are brick, studs and all.
//   Glazing   the clerestories: glass y 6..7, z 3..8 in each face, on a spruce beam at y 5, studs at the gables.
//   Porch     the pentice: the stone apron z 0..1 along the whole front, spruce posts at z 0, a spruce-board lean-to
//             (y 3 at z 0, y 4 at z 1) under the teeth's front overhang, railings in the side bays, the door bay open.
//   Stack     the twin flue up the west side (x 0, z 5..6) from the ground to y 9, two campfires smoking at y 10, the
//             forge breast inside it.
//   Piers     stone-brick piers a block proud of the brick at the corners and under the back gutters.

export const SIZE = [13, 11, 11] // w, h, d: StarterBlueprints.CRAFTERS_WORKSHOP's (13, 11, 11)

// Palette (60/30/10): fired brick (walls) / deepslate tiles and spruce timber (roof, beams, studs, porch) / stone-brick
// footing and piers, waxed copper. Why: the warm red brick against the cool dark tiles makes the teeth read
// sharp on the sky; the spruce ties beams, studs, porch and benches into one frame (a first try in dark oak merged with the tiles
// into one dark mass, and timber posts on every side read heavy: the piers are stone now); copper is the shop's own touch (a
// crafter's metal) and the only bright note, on the gutters and the tooth tops.
export const PALETTE = {
  stone: [['stone_bricks', 7], ['cracked_stone_bricks', 2], ['mossy_stone_bricks', 1]], // the footing and apron
  stack: [['stone_bricks', 5], ['cracked_stone_bricks', 1]],
  dressed: 'chiseled_stone_bricks', // the stack's band
  walls: 'bricks',
  frame: 'spruce_log', // mid-brown: reads apart from the dark tiles above it
  trim: 'spruce',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  gutter: 'waxed_cut_copper_stairs',
  pentice: 'spruce_stairs', // the porch's board roof: warm wood under the dark teeth, a shop's awning
  finial: 'lightning_rod',
  floor: 'spruce_planks',
  wood: 'spruce', // the door, fences, chairs, counters, shelves
  glass: 'glass_pane',
}

export const TEETH = { from: 0, teeth: 3, rise: 3, eaveY: 5, along: [1, 10], rakes: [1, 10] }

export const MASSES = {
  hall: {
    walls: { x0: 1, x1: 11, z0: 2, z1: 9 },
    room: { x0: 2, x1: 10, z0: 3, z1: 8 },
    wallTop: 4, beamY: 5,
    // stone piers a block proud of the brick: at the corners, and on the back under the two valleys (the gutters
    // spill there); the front's are the porch posts
    piers: [[0, 2], [0, 9], [12, 2], [12, 9], [1, 10], [4, 10], [8, 10], [11, 10]], pierTop: 4,
  },
  clerestories: [ // glazed faces: x, glass y 6..7 over z 3..8 (the beam at y 5, studs at z 2 and z 9)
    { u: 3, v0: 3, v1: 8, y0: 6, y1: 7 },
    { u: 7, v0: 3, v1: 8, y0: 6, y1: 7 },
    { u: 11, v0: 3, v1: 8, y0: 6, y1: 7 },
  ],
  porch: { x0: 1, x1: 11, z0: 0, z1: 1, posts: [1, 4, 8, 11], rails: [[2, 3], [9, 10]] },
  stack: { x: 0, z: [5, 6], top: 9, bandY: 6 },
}

export const ROOMS = [
  { name: 'Workroom', use: 'the crafters\' shop: forge and anvil, benches round the walls, a work table, the stores', box: [2, 1, 3, 10, 4, 8] },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 2], facing: 'south', step: [6, 0, 0] },
  // windows: cells of glass, the side they look out of; every ground window gets the same sill and a pot
  windows: [
    { name: 'front west', cells: [[2, 2, 2], [3, 2, 2], [2, 3, 2], [3, 3, 2]], out: 'north' },
    { name: 'front east', cells: [[9, 2, 2], [10, 2, 2], [9, 3, 2], [10, 3, 2]], out: 'north' },
    { name: 'back west', cells: [[2, 2, 9], [3, 2, 9], [2, 3, 9], [3, 3, 9]], out: 'south' },
    { name: 'back east', cells: [[9, 2, 9], [10, 2, 9], [9, 3, 9], [10, 3, 9]], out: 'south' },
    { name: 'west front', cells: [[1, 2, 3], [1, 2, 4], [1, 3, 3], [1, 3, 4]], out: 'west' },
    { name: 'west back', cells: [[1, 2, 7], [1, 2, 8], [1, 3, 7], [1, 3, 8]], out: 'west' },
    { name: 'east front', cells: [[11, 2, 3], [11, 2, 4], [11, 3, 3], [11, 3, 4]], out: 'east' },
    { name: 'east back', cells: [[11, 2, 7], [11, 2, 8], [11, 3, 7], [11, 3, 8]], out: 'east' },
  ],
}

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a furniture spot) or a fixture
// (a vanilla block the game uses as itself: chest, furnace, anvil). part: the part that draws it. facing: the way a
// person using it faces. No job block of any job (the brief): no crafting table, barrel, grindstone, cauldron...
export const FURNITURE = [
  // The forge corner (west wall): two furnaces in the stack's breast under a hood, the anvil by the window
  { room: 'Workroom', kind: 'fixture', part: 'stove', pos: [2, 1, 5], facing: 'east', block: 'furnace' },
  { room: 'Workroom', kind: 'fixture', part: 'stove', pos: [2, 1, 6], facing: 'east', block: 'furnace' },
  { room: 'Workroom', kind: 'fixture', part: 'anvil', pos: [2, 1, 4], facing: 'north', block: 'anvil' },
  { room: 'Workroom', kind: 'fixture', part: 'doubleChest', pos: [2, 1, 7], facing: 'east', along: 'south' },
  // The long bench on the back wall: five counters, a lamp on the middle one, shelves over it
  ...[4, 5, 6, 7, 8].map(x => ({ room: 'Workroom', kind: 'counter', part: 'counter', pos: [x, 1, 8], facing: 'south', wood: 'spruce', topSlab: x === 6 })),
  { room: 'Workroom', kind: 'lamp', part: 'lampOn', pos: [6, 3, 8], facing: 'south', wood: 'spruce' },
  { room: 'Workroom', kind: 'shelf', part: 'wallShelf', pos: [4, 3, 8], facing: 'south', wood: 'spruce', on: 'potted_fern' },
  { room: 'Workroom', kind: 'shelf', part: 'wallShelf', pos: [5, 3, 8], facing: 'south', wood: 'spruce', on: ['candle', { candles: '3', lit: 'false', waterlogged: 'false' }] },
  { room: 'Workroom', kind: 'shelf', part: 'wallShelf', pos: [7, 3, 8], facing: 'south', wood: 'spruce', on: 'flower_pot' },
  { room: 'Workroom', kind: 'shelf', part: 'wallShelf', pos: [8, 3, 8], facing: 'south', wood: 'spruce', on: 'potted_red_mushroom' },
  // The east bench under the windows, the clock and a cupboard on the pier between them
  { room: 'Workroom', kind: 'counter', part: 'counter', pos: [10, 1, 3], facing: 'east', wood: 'spruce' },
  { room: 'Workroom', kind: 'counter', part: 'counter', pos: [10, 1, 4], facing: 'east', wood: 'spruce' },
  { room: 'Workroom', kind: 'clock', part: 'clock', pos: [10, 1, 5], facing: 'west', wood: 'spruce' },
  { room: 'Workroom', kind: 'cupboard', part: 'cupboard', pos: [10, 1, 6], facing: 'west', wood: 'spruce' },
  { room: 'Workroom', kind: 'counter', part: 'counter', pos: [10, 1, 7], facing: 'east', wood: 'spruce' },
  { room: 'Workroom', kind: 'counter', part: 'counter', pos: [10, 1, 8], facing: 'east', wood: 'spruce' },
  // The work table in the middle, a stool-chair either side
  { room: 'Workroom', kind: 'table', part: 'table', pos: [6, 1, 5], facing: 'north', wood: 'spruce', colour: 'brown' },
  { room: 'Workroom', kind: 'table', part: 'table', pos: [6, 1, 6], facing: 'north', wood: 'spruce', colour: 'brown' },
  { room: 'Workroom', kind: 'chair', part: 'chair', pos: [5, 1, 5], facing: 'east', wood: 'spruce' },
  { room: 'Workroom', kind: 'chair', part: 'chair', pos: [7, 1, 6], facing: 'west', wood: 'spruce' },
  // The guild's cloths either side of the door, inside
  { room: 'Workroom', kind: 'picture', part: 'picture', pos: [4, 3, 3], facing: 'south', wood: 'spruce', colour: 'orange' },
  { room: 'Workroom', kind: 'picture', part: 'picture', pos: [8, 3, 3], facing: 'south', wood: 'spruce', colour: 'orange' },
  // Light: lanterns hung from the clerestory beam and the teeth, besides the daylight from above
  { room: 'Workroom', kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 5], facing: 'north', chain: 2, wood: 'spruce' },
  { room: 'Workroom', kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 7], facing: 'north', chain: 0, wood: 'spruce' },
  { room: 'Workroom', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 4], facing: 'north', chain: 1, wood: 'spruce' },
]

// The walking line: step -> porch -> door -> round the table to the forge, the chests, the back bench and the east
// bench.
export const WALK = [
  [6, 0, 0], [6, 0, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [5, 1, 4], [4, 1, 4], [3, 1, 4], [3, 1, 5], [3, 1, 6],
  [3, 1, 7], [4, 1, 7], [5, 1, 7], [6, 1, 7], [7, 1, 7], [8, 1, 7], [9, 1, 7], [9, 1, 6], [9, 1, 5], [9, 1, 4],
  [9, 1, 3], [8, 1, 3], [7, 1, 3],
]

// The timber stock in the middle of the room, off the walking line: stripped logs laid along the floor beside the
// work table, one stacked on them (a fixture, not furniture).
export const MIDDLE = { log: 'stripped_spruce_log', stock: [[8, 1, 5], [8, 1, 6], [8, 2, 5]] }

// Outside (not furniture spots): a pot on every window sill, the woodpile under the back overhang, a lantern under the
// porch at the door, the guild's banner on the middle tooth's front.
export const OUTSIDE = {
  pots: ['potted_red_tulip', 'potted_azure_bluet', 'potted_cornflower', 'potted_oxeye_daisy'],
  woodpile: { x0: 5, x1: 7, z: 10 },
  banner: { at: [6, 6, 1], colour: 'cyan', facing: 'north' },
  doorLamp: [6, 3, 1],
}
