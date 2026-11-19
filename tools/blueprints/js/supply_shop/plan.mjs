// The Supply Shop (supply_shop, _2, _3, _4; ROADMAP 49), the plan: drawn before any wall. Story, palette, masses,
// rooms, openings, every furniture footprint and the walking line; recipe.mjs assembles it from the parts library.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Hoist House: one tall street gable jettied a block out over two glass shop bays, a copper sign band on the
//      jetty, a taking-in door and a hoist beam at the apex; a 45-degree deepslate roof, ridge to the street; one shop
//      room with the counter across it and the stock wall behind. Story: a chandler who hauls his goods up from the
//      street, and the street can see it. Grows backward (a lower house range) then sideways (a lower second gable).
//   B. The Stall That Stayed: a low stone booth under a deep hipped roof whose front lifts on a serving hatch, the
//      counter at the street; side stores under catslides. Story: a market stall that put down walls. Low and wide:
//      it hides in a market, and the pinned door and counter (4 deep) fight a counter at the street.
//   C. The Weigh-House: a squat square stone shop with a pyramid roof and a little lantern, an open arcade on the
//      street. Story: the market's scales. Handsome, but the arcade eats Level I's 11 x 10 plot and its roof can't
//      grow on two sides without a second pyramid.
// Strongest: A. It is the one with a face: a hero gable that says "shop" from down the street (glass bays, a copper
// band, a hoist with its lantern), it fits the pinned door (5, 1, 2) and counter (5, 1, 6) as drawn, and every level
// grows the roofline (II a lower range behind the gable, III a lower second gable beside it, so the street sees the
// big gable and its little sister), where B and C only get wider.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the street
// (z = 0, north) back. The front wall stands at z 2; the jetty and the bays at z 1; the roof's front overhang at z 0.
//
// Masses (named parts the owner's feedback can point at):
//   Shop      (I)   walls x 1..9, z 2..9, ground room y 1..3, beam ring y 4, a 45-degree deepslate roof across
//                   x 0..10 (eaves y 5, ridge y 10) running z 0..9; the street gable jettied to z 1 over the bays.
//   Bays      (I)   two glass shop windows under the jetty (x 2..3 and x 7..8, z 1), their stall boards on the plinth.
//   Sign band (I)   a course of waxed cut copper across the jetty (y 5, z 1): the shop's sign.
//   Hoist     (I)   a spruce beam out of the apex (z 0, y 9) with the shop's lantern hung under its tip, over a
//                   two-pane loft window in the gable.
//   Displays  (I)   two shallow display windows out at z 0 either side of the door, under copper hoods: the shop
//                   read from the street. The frame stands a block proud of the calcite on the sides and back.
// Size note for the ship step: Level I is 11 x 11 x 11 (was 11 x 11 x 10): the extra block at the back carries the
// proud back frame and the roof's overhang past the back gable. Well inside the 13 x 16 x 13 test ceiling.
//   Range     (II)  the shopkeeper's house behind: walls x 2..8, z 9..15, a lower roof (eaves y 4, ridge y 8).
//   Wing      (III) the seed and hardware room east: walls x 9..15, z 3..12, its own small gable to the street (ridge
//                   y 7), a door through from the shop.
//   Cellar    (IV)  under the Wing, 4 deep, a ladder down from the Wing's back corner; the cool store.

export const SIZES = { 1: [11, 11, 11], 2: [11, 11, 16], 3: [17, 11, 16], 4: [17, 11, 16] }
export const BASEMENT = 4 // Level IV only

// Palette (60/30/10): white calcite plaster walls / deepslate (plinth and tiled roof) and dark spruce frame / waxed
// copper accents and spruce joinery. Why: the white face and the dark tiled roof read across a market at a glance, the
// spruce frame draws the bays, and the copper band (the mod's own Copperline colour) marks it as the place you buy.
// Waxed copper never turns green on its own, so the builder's check stays true.
export const PALETTE = {
  stone: [['cobbled_deepslate', 6], ['deepslate_bricks', 3], ['cracked_deepslate_bricks', 1]],
  infill: 'calcite',
  frame: 'spruce_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  copper: 'waxed_cut_copper',
  copperStairs: 'waxed_cut_copper_stairs',
  floor: 'spruce_planks',
  ceiling: 'spruce_planks',
  board: 'stripped_spruce_wood',
  wood: 'spruce',
  glass: 'glass_pane',
  cellarFloor: 'polished_deepslate',
  cask: 'barrel',
}

export const SHOP = {
  walls: { x0: 1, x1: 9, z0: 2, z1: 9 },
  room: { x0: 2, x1: 8, z0: 3, z1: 8 },
  wallTop: 3, beamY: 4,
  frontPosts: [1, 4, 6, 9], sidePosts: [2, 6, 9],
  jetty: { z: 1, y: 4, posts: [1, 4, 6, 9] }, // bay posts up to the jetty beam
  roof: { axis: 'z', across: [0, 10], along: [0, 10], eaveY: 5, steep: false }, // a block past both gables
  // the proud frame: posts and a wall plate a block out from the calcite on both sides (x 0, x 10) and the back (z 10),
  // on a plinth course, so the white panels sit back between dark timbers (critic round 1, depth)
  frame: { sides: [0, 10], sidePosts: [2, 6, 10], back: 10, backPosts: [0, 5, 10] },
  bays: [{ x0: 1, x1: 4 }, { x0: 6, x1: 9 }], // display windows out at z 0 under copper hoods, the jetty bays behind them
  loftWindow: [5, 6, 1], // two panes high in the street gable, under the hoist
  backDoor: [5, 6, 9], // Level I: a loading door in the back gable, a ledge under it, a hoist over it (critic round 2)
  backHoist: { at: [5, 9, 9], drop: 0 },
  dormers: [{ face: 1, dir: -1, zc: 4 }, { face: 9, dir: 1, zc: 4 }], // a gabled dormer in each long slope over the side windows
  hoist: { at: [5, 9, 1], drop: 0 }, // the lantern hangs right under the beam's tip, above the loft window
}

export const RANGE = { // Level II
  walls: { x0: 2, x1: 8, z0: 9, z1: 14 },
  room: { x0: 3, x1: 7, z0: 10, z1: 13 },
  jetty: { z: 15, brackets: [2, 8] }, // the back gable jettied a block out over the yard (critic round 2)
  wallTop: 3, beamY: 4,
  roof: { axis: 'z', across: [1, 9], along: [10, 15], eaveY: 4, steep: false },
}

export const WING = { // Level III
  walls: { x0: 9, x1: 15, z0: 3, z1: 12 },
  room: { x0: 10, x1: 14, z0: 4, z1: 11 },
  wallTop: 3, beamY: 4,
  frontPosts: [9, 15], sidePosts: [3, 8, 12],
  roof: { axis: 'z', across: [10, 16], along: [2, 12], eaveY: 4, steep: false },
  window: { x0: 11, x1: 14 }, // the street window, x 11..14 at z 3
  doorway: [9, 1, 5], // through the shop's east wall, beside I's east window (walled up in III)
  // the shop's ledger shelf, against the shop's wall by the doorway: a chiseled bookshelf facing into the Wing, where
  // 0.151.0's drawing had one (a refresh from that drawing leaves its books and all where they stand: ship step)
  ledgerShelf: { at: [10, 1, 4], facing: 'east' },
}

export const CELLAR = { // Level IV, under the Wing: floor at y -4, room y -3..-1, the Wing's floor its ceiling
  walls: { x0: 9, x1: 15, z0: 3, z1: 12 },
  room: { x0: 10, x1: 14, z0: 4, z1: 11 },
  ladder: { x: 14, z: 4, y0: -3, y1: 0, facing: 'west' }, // against the east wall
  lamps: [[11, -1, 5], [12, -1, 10]],
}

export const ROOMS = [
  { level: 1, name: 'Shop', use: 'customers at the front, the Shop Counter across the room, the stock wall behind', box: [2, 1, 3, 8, 3, 8] },
  { level: 2, name: 'Back room', use: "the shopkeeper's room: bed, desk, his own chest", box: [3, 1, 10, 7, 3, 14] },
  { level: 3, name: 'Wing', use: 'seed and hardware: sacks, a side counter, more stock', box: [10, 1, 4, 14, 3, 11] },
  { level: 4, name: 'Cellar', use: 'the cool store: chests and casks of logs', box: [10, -3, 4, 14, -1, 11] },
]

export const OPENINGS = {
  frontDoor: { at: [5, 1, 2], facing: 'south', step: [5, 0, 1] }, // pinned: QaDoorwayClearanceGameTests
  backDoor: { at: [5, 1, 9], facing: 'south', level: 2 },
  windows: [
    { name: 'shop west', cells: [[1, 2, 4], [1, 3, 4]], out: 'west', dress: 'both' },
    { name: 'shop east', cells: [[9, 2, 4], [9, 3, 4]], out: 'east', dress: 'both', until: 2 },
    { name: 'shop back west', cells: [[3, 2, 9], [3, 3, 9]], out: 'south', dress: 'both', until: 1 },
    { name: 'shop back east', cells: [[7, 2, 9], [7, 3, 9]], out: 'south', dress: 'both', until: 1 },
    { name: 'range west', cells: [[2, 2, 12], [2, 3, 12]], out: 'west', dress: 'both', from: 2 },
    { name: 'range east', cells: [[8, 2, 12], [8, 3, 12]], out: 'east', dress: 'both', from: 2, until: 2 },
    { name: 'range back', cells: [[5, 2, 14], [5, 3, 14]], out: 'south', dress: 'both', from: 2 },
    { name: 'range gable', cells: [[5, 5, 15]], out: 'south', dress: null, from: 2 },
    { name: 'wing east front', cells: [[15, 2, 6], [15, 3, 6]], out: 'east', dress: 'both', from: 3 },
    { name: 'wing east back', cells: [[15, 2, 10], [15, 3, 10]], out: 'east', dress: 'both', from: 3 },
    { name: 'wing gable', cells: [[13, 5, 3]], out: 'north', dress: null, from: 3 },
  ],
}

// Every piece of furniture, footprint first (level: the first level it stands in; it stays in every later one).
export const FURNITURE = [
  // Shop: the Shop Counter between four counters across the room, customers in front, the stock wall behind
  { level: 1, room: 'Shop', kind: 'job', part: 'shopCounter', pos: [5, 1, 6], facing: 'north' },
  ...[3, 4, 6, 7].map(x => ({ level: 1, room: 'Shop', kind: 'counter', part: 'counter', pos: [x, 1, 6], facing: 'south', wood: 'spruce', top: 'waxed_cut_copper_slab' })),
  { level: 1, room: 'Shop', kind: 'stock', part: 'casks', pos: [2, 1, 8], facing: 'north', height: 2 },
  { level: 1, room: 'Shop', kind: 'stock', part: 'casks', pos: [8, 1, 8], facing: 'north', height: 2 },
  // a chest between two casks each side of the aisle, never two chests side by side: a double chest is one store to
  // the counter (SupplyContainers), and the shop's stock must be 8 (ship step, FurnishedWorkplacesCompatTests)
  { level: 1, room: 'Shop', kind: 'stock', part: 'chest', pos: [3, 1, 8], facing: 'north', block: 'chest' },
  { level: 1, room: 'Shop', kind: 'stock', part: 'casks', pos: [4, 1, 8], facing: 'north', height: 1 },
  { level: 1, room: 'Shop', kind: 'stock', part: 'casks', pos: [6, 1, 8], facing: 'north', height: 1 },
  { level: 1, room: 'Shop', kind: 'stock', part: 'chest', pos: [7, 1, 8], facing: 'north', block: 'chest' },
  { level: 1, room: 'Shop', kind: 'shelf', part: 'shelf', pos: [2, 2, 3], facing: 'east', wood: 'spruce', crockery: 'flower_pot' },
  { level: 1, room: 'Shop', kind: 'shelf', part: 'shelf', pos: [8, 2, 3], facing: 'west', wood: 'spruce', crockery: 'potted_cactus' },
  { level: 1, room: 'Shop', kind: 'picture', part: 'picture', pos: [8, 3, 5], facing: 'west', wood: 'spruce', colour: 'cyan' },
  { level: 1, room: 'Shop', kind: 'hanging_pot', part: 'hangingPot', pos: [2, 3, 2], plant: 'potted_fern', wood: 'spruce' },
  { level: 1, room: 'Shop', kind: 'hanging_pot', part: 'hangingPot', pos: [8, 3, 2], plant: 'potted_red_tulip', wood: 'spruce' },
  // display tables either side of the way in, goods laid out on them (no container: Level I keeps its 8)
  { level: 1, room: 'Shop', kind: 'table', part: 'table', pos: [3, 1, 4], facing: 'north', wood: 'spruce', colour: 'cyan' },
  { level: 1, room: 'Shop', kind: 'table', part: 'table', pos: [7, 1, 4], facing: 'north', wood: 'spruce', colour: 'orange' },
  { level: 1, room: 'Shop', kind: 'lamp', part: 'hangingLamp', pos: [5, 3, 4], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Shop', kind: 'lamp', part: 'hangingLamp', pos: [5, 3, 7], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Outside', kind: 'trellis', part: 'trellis', pos: [0, 1, 7], facing: 'west', wood: 'spruce' },
  // Back room (II): the shopkeeper's bed by the back window, a desk and chair, his chest, a lamp
  { level: 2, room: 'Back room', kind: 'bed', part: 'bed', pos: [3, 1, 12], facing: 'south', colour: 'cyan' },
  { level: 2, room: 'Back room', kind: 'nightstand', part: 'nightstand', pos: [4, 1, 13], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Back room', kind: 'desk', part: 'desk', pos: [7, 1, 12], facing: 'east', wood: 'spruce' },
  { level: 2, room: 'Back room', kind: 'chair', part: 'chair', pos: [6, 1, 12], facing: 'east', wood: 'spruce' },
  { level: 2, room: 'Back room', kind: 'stock', part: 'chest', pos: [7, 1, 10], facing: 'west', block: 'chest' },
  { level: 2, room: 'Back room', kind: 'cupboard', part: 'cupboard', pos: [3, 1, 10], facing: 'east', wood: 'spruce' },
  { level: 2, room: 'Back room', kind: 'rug', part: 'rug', cellsAbs: [[4, 1, 11], [5, 1, 11], [4, 1, 12], [5, 1, 12]], colour: 'light_gray' },
  { level: 2, room: 'Back room', kind: 'lamp', part: 'hangingLamp', pos: [5, 3, 11], facing: 'north', wood: 'spruce' },
  // Wing (III): a side counter for weighing out seed, a shelf of pots by it, sacks (hay) on the east wall, stock chests
  { level: 3, room: 'Wing', kind: 'counter', part: 'counter', pos: [12, 1, 8], facing: 'north', wood: 'spruce', top: 'spruce_slab' },
  { level: 3, room: 'Wing', kind: 'counter', part: 'counter', pos: [13, 1, 8], facing: 'north', wood: 'spruce', top: 'spruce_slab' },
  { level: 3, room: 'Wing', kind: 'shelf', part: 'shelf', pos: [14, 2, 9], facing: 'west', wood: 'spruce', crockery: 'flower_pot' },
  { level: 3, room: 'Wing', kind: 'stock', part: 'doubleChest', pos: [12, 1, 11], facing: 'north', along: 'east' },
  { level: 3, room: 'Wing', kind: 'picture', part: 'picture', pos: [10, 3, 11], facing: 'east', wood: 'spruce', colour: 'orange' },
  { level: 3, room: 'Wing', kind: 'hanging_pot', part: 'hangingPot', pos: [12, 3, 4], plant: 'potted_azure_bluet', wood: 'spruce' },
  { level: 3, room: 'Wing', kind: 'lamp', part: 'hangingLamp', pos: [12, 3, 6], facing: 'north', wood: 'spruce' },
  { level: 3, room: 'Outside', kind: 'trellis', part: 'trellis', pos: [16, 1, 8], facing: 'east', wood: 'spruce' },
]

// Sacks of seed and meal along the Wing's east wall (hay bales, two high at the back), the Cellar's stock and casks.
export const WING_SACKS = [[14, 1, 5, 1], [14, 1, 6, 1], [14, 1, 7, 2]]
export const CELLAR_STOCK = [
  { part: 'doubleChest', pos: [10, -3, 9], facing: 'east', along: 'south' },
  { part: 'doubleChest', pos: [11, -3, 11], facing: 'north', along: 'east' },
]
export const CELLAR_LOGS = { x: 14, z0: 8, z1: 11 } // casks of logs lying along the east wall, two high
// the rest of the cellar (critic round 1): a slatted rack of shelves on the east wall by the ladder, a packing table
// in the middle, sacks (hay) stacked by the west wall; no barrels (a Fisherman's block)
export const CELLAR_RACK = { x: 14, z0: 5, z1: 7, legs: [5, 7] }
export const CELLAR_TABLE = [[12, -3, 7], [12, -3, 8]]
export const CELLAR_SACKS = [[10, -3, 4, 2], [11, -3, 4, 1], [10, -3, 5, 1], [13, -3, 11, 1]]

// The walking line: street -> step -> door -> customers -> round the counter's west end -> the aisle -> (II) the back
// door and room -> (III) the Wing's doorway -> the ladder -> (IV) the Cellar.
export const WALK = [
  [5, 0, -1], [5, 1, 1], [5, 1, 2], [5, 1, 3], [5, 1, 5], [4, 1, 5], [3, 1, 5], [2, 1, 6], [2, 1, 7], [5, 1, 7],
  [5, 1, 8], [5, 1, 9], [5, 1, 11], [5, 1, 4], [8, 1, 5], [9, 1, 5], [11, 1, 5], [13, 1, 4], [14, 0, 4], [14, -3, 4],
]

export const OUTSIDE = {
  flowerBeds: [{ x0: 2, x1: 3, z: 0, plants: ['poppy', 'cornflower'] }, { x0: 7, x1: 8, z: 0, plants: ['cornflower', 'poppy'] }],
}
