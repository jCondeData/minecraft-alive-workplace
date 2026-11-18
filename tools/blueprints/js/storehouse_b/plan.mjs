// Storehouse (Version B), the plan: drawn before any wall (brief: tools/blueprints/briefs/storehouse.md).
//
// Three rough concepts:
//   A. The Tithe Barn: one long low box, gable to the street, huge cart doors, a hipped roof; chests in the bays.
//      Story: the harvest goes in at the door. The obvious storehouse, and from the street a barn like any other.
//   B. The Strongroom Tower: a squat stone keep on its vault, pyramid roof, an arrow-slit look; it grows lean-tos.
//      Story: the village keeps its wealth safe. Strong, but a tower says "fort", and the store can't stack higher
//      than y 5, so its height would be empty.
//   C. The Canal Warehouse: a tall, narrow brick merchant's house with a crow-stepped gable to the street, its loading
//      doors stacked up the middle bay under a hoist beam at the peak; it grows a younger, lower twin gable beside it
//      (II) and a long low rack hall across the back (III).
//      Story: a trading village's store: goods go up by the hoist and down by the hatch into the vault.
// Strongest: C. It is the only one that says "goods are stored and moved here" from the street (doors stacked one
// above another, a crane arm, a counting desk inside), its stepped gable is a silhouette no other build of ours has,
// and it grows the way real warehouse streets did: gable by gable, then a hall behind. It suits the 4-up / 4-down
// reach too: two storeys of store and a vault, nothing wasted up high (the hero gable is all roof and hoist).
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The file holds the vault under y 0 (LiftedCanvas, basement 4).
//
// Masses (named parts):
//   Warehouse  Level I, the hero: brick walls x 1..9, z 1..11, y 1..7; floor at y 4; 45-degree roof (ridge along z,
//              y 12) with a 1-block overhang at the sides and back; the Crow Gable on the front up to y 13, capped.
//   Hoist      a dark oak arm out of the Crow Gable's top step at y 12, a chain and a lantern over the Loading Doors.
//   Loading Doors  the stacked middle bay: the street door (y 1-2), the loft door (y 5-6), the round light (y 9-10).
//   Vault      under the Warehouse, inside x 2..8, z 2..10, floor y -4: stone walls, a central pier, chests on three
//              walls, two lanterns hung from the floor joists, a ladder down under a hatch at (2, 0, 10).
//   Twin       Level II: a lower brick bay east of the first, walls x 9..15 (sharing x 9), its own Crow Gable to y 12,
//              a cart arch on the street, its own hoist; two storeys of chests; a valley between the two roofs.
//   Rack Hall  Level III: one tall storey across the back of both, walls x 1..15, z 11..17, a low roof (ridge along
//              x, y 7), free-standing back-to-back chest racks, a back door to the yard.
//
// The Storehouse block (the Counting Desk) stands at (8, 1, 7) facing west on the Warehouse's east wall, a double
// chest two high each side and the lantern on top. Reach (8 across, 4 up or down): x 0..16, z -1..15, y -3..5. Every
// store of every level is inside it: no chest above y 5, the vault's at y -3 and -2, the Rack Hall's at z <= 15.

export const BASEMENT = 4
export const SIZE = { 1: [11, 15, 13], 2: [17, 15, 13], 3: [17, 15, 19] } // w, h (above ground), d

// Palette (60/30/10): red brick walls (60) / dark oak joinery and deepslate tiles (30) / stone-brick dressings
// (quoins, string course, step caps; 10). Why: a merchant's brick, sober dark woodwork and a dark roof that lets the
// crow steps' pale caps read against the sky; spruce boards inside keep the store rooms light.
export const PALETTE = {
  wall: 'bricks',
  dress: 'stone_bricks', dressAlt: 'polished_andesite', cap: 'stone_brick_slab',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  trim: 'dark_oak', frame: 'dark_oak_log', post: 'stripped_dark_oak_log',
  floor: 'spruce_planks',
  plinth: [['stone_bricks', 5], ['cracked_stone_bricks', 1], ['andesite', 2]],
  vaultWall: [['stone_bricks', 6], ['cracked_stone_bricks', 2], ['mossy_stone_bricks', 1]],
  vaultFloor: 'polished_deepslate', pier: 'deepslate_bricks',
  glass: 'glass_pane',
  job: 'aliveworkplace:storehouse',
}

export const MASSES = {
  warehouse: { x0: 1, x1: 9, z0: 1, z1: 11, top: 7, floorY: 4, room: [2, 2, 8, 10],
    roof: { axis: 'z', across: [0, 10], along: [2, 12], eaveY: 7, steep: false },
    crow: { tops: d => (d <= 1 ? 13 : d <= 3 ? 11 : 9) }, hoist: [5, 12, 1] },
  vault: { x0: 1, x1: 9, z0: 1, z1: 11, depth: 4, inside: [2, 2, 8, 10], pier: [5, 6] },
  twin: { x0: 9, x1: 15, z0: 1, z1: 11, top: 7, floorY: 4, room: [10, 2, 14, 10],
    roof: { axis: 'z', across: [8, 16], along: [2, 12], eaveY: 7, steep: false },
    crow: { tops: d => (d <= 1 ? 12 : 10) }, hoist: [12, 11, 1] },
  rackHall: { x0: 1, x1: 15, z0: 11, z1: 17, top: 3, room: [2, 12, 14, 16],
    roof: { axis: 'x', across: [10, 18], along: [0, 16], eaveY: 3, steep: false } },
}

export const ROOMS = [
  { name: 'Store Room', level: 1, use: 'the Counting Desk and the first chests', box: [2, 1, 2, 8, 3, 10] },
  { name: 'Loft', level: 1, use: 'chests along three walls, the loft door', box: [2, 5, 2, 8, 7, 10] },
  { name: 'Vault', level: 1, use: 'the strong store under the floor', box: [2, -3, 2, 8, -1, 10] },
  { name: 'Twin Store', level: 2, use: 'the cart arch and chests', box: [10, 1, 2, 14, 3, 10] },
  { name: 'Twin Loft', level: 2, use: 'chests, from the Loft through the party wall', box: [10, 5, 2, 14, 7, 10] },
  { name: 'Rack Hall', level: 3, use: 'back-to-back chest racks', box: [2, 1, 12, 14, 3, 16] },
]

export const OPENINGS = {
  frontDoor: { at: [5, 1, 1], facing: 'south', step: [5, 0, 0] },
  loftDoor: { at: [5, 5, 1] }, roundLight: [[5, 9, 1], [5, 10, 1]],
  flight: { steps: [[2, 1, 5], [2, 2, 6], [2, 3, 7], [2, 4, 8]], rises: 'south', well: [[2, 4, 5], [2, 4, 6], [2, 4, 7]],
    rail: [[3, 5, 5], [3, 5, 6], [3, 5, 7], [2, 5, 4]] },
  hatch: { x: 2, z: 10, facing: 'east' }, // ladder against the vault's west wall, under a hatch in the floor
  openFloor: [5, 1, 6], // the open cell over the vault (QaBuilderGaps1105b's gold block)
  windows1: [
    { cells: [[3, 2, 1], [3, 3, 1]], out: 'north', dress: 'sill' }, { cells: [[7, 2, 1], [7, 3, 1]], out: 'north', dress: 'sill' },
    { cells: [[3, 5, 1], [3, 6, 1]], out: 'north', dress: 'shutters' }, { cells: [[7, 5, 1], [7, 6, 1]], out: 'north', dress: 'shutters' },
    { cells: [[1, 2, 4], [1, 3, 4]], out: 'west' }, { cells: [[1, 2, 9], [1, 3, 9]], out: 'west' },
    { cells: [[1, 6, 4]], out: 'west' }, { cells: [[1, 6, 9]], out: 'west' },
    { cells: [[9, 2, 3], [9, 3, 3]], out: 'east', twinDoor: true }, { cells: [[9, 6, 4]], out: 'east' }, { cells: [[9, 6, 8]], out: 'east' },
    { cells: [[3, 6, 11]], out: 'south' }, { cells: [[7, 6, 11]], out: 'south' }, { cells: [[5, 6, 11]], out: 'south' },
  ],
  // Level II
  partyDoors: [[[9, 1, 3], [9, 2, 3]], [[9, 5, 10], [9, 6, 10]]],
  cartArch: { x0: 11, x1: 13, z: 1 },
  twinLoftDoor: { at: [12, 5, 1] },
  windows2: [
    { cells: [[15, 2, 4], [15, 3, 4]], out: 'east' }, { cells: [[15, 2, 8], [15, 3, 8]], out: 'east' },
    { cells: [[15, 6, 4]], out: 'east' }, { cells: [[15, 6, 8]], out: 'east' },
    { cells: [[10, 5, 1], [10, 6, 1]], out: 'north', dress: 'shutters' }, { cells: [[14, 5, 1], [14, 6, 1]], out: 'north', dress: 'shutters' },
    { cells: [[12, 9, 1]], out: 'north' }, { cells: [[12, 6, 11]], out: 'south' },
  ],
  // Level III
  hallDoors: [[5, 1, 11], [12, 1, 11]], backDoor: { at: [8, 1, 17], facing: 'north', step: [8, 0, 18] },
  windows3: [
    { cells: [[4, 2, 17], [4, 3, 17]], out: 'south' }, { cells: [[12, 2, 17], [12, 3, 17]], out: 'south' },
    { cells: [[1, 2, 14], [1, 3, 14]], out: 'west' }, { cells: [[15, 2, 14], [15, 3, 14]], out: 'east' },
    { cells: [[1, 5, 14]], out: 'west' }, { cells: [[15, 5, 14]], out: 'east' },
  ],
}

// Stores, by level: [cells [x, z] in pairs along the wall, ys, facing]. Every one is within the desk's reach.
export const STORES = {
  1: [
    [[[2, 2], [2, 3]], [1], 'east'], [[[3, 10], [4, 10]], [1, 2], 'north'], [[[6, 10], [7, 10]], [1, 2], 'north'],
    [[[2, 2], [2, 3]], [5], 'east'], [[[2, 9], [2, 10]], [5], 'east'], [[[3, 10], [4, 10]], [5], 'north'],
    [[[6, 10], [7, 10]], [5], 'north'], [[[8, 2], [8, 3]], [5], 'west'], [[[8, 5], [8, 6]], [5], 'west'], [[[8, 8]], [5], 'west'], // single: the way to the party door stays open
    // the vault, two high
    [[[8, 2], [8, 3]], [-3, -2], 'west'], [[[8, 5], [8, 6]], [-3, -2], 'west'], [[[8, 8], [8, 9]], [-3, -2], 'west'],
    [[[2, 2], [2, 3]], [-3, -2], 'east'], [[[2, 5], [2, 6]], [-3, -2], 'east'],
    [[[4, 10], [5, 10]], [-3, -2], 'north'], [[[6, 10], [7, 10]], [-3, -2], 'north'],
  ],
  2: [
    [[[14, 2], [14, 3]], [1, 2], 'west'], [[[14, 5], [14, 6]], [1, 2], 'west'], [[[14, 8], [14, 9]], [1, 2], 'west'],
    [[[10, 10], [11, 10]], [1, 2], 'north'], [[[13, 10], [14, 10]], [1], 'north'],
    [[[10, 5], [10, 6]], [1], 'east'],
    [[[14, 2], [14, 3]], [5], 'west'], [[[14, 5], [14, 6]], [5], 'west'], [[[14, 8], [14, 9]], [5], 'west'],
    [[[12, 10], [13, 10]], [5], 'north'], [[[10, 2], [10, 3]], [5], 'east'], [[[10, 6], [10, 7]], [5], 'east'],
  ],
  3: [
    // racks back to back along x: the north faces open to the aisle at z 12, the south faces to z 15
    [[[3, 13], [4, 13]], [1, 2], 'north'], [[[3, 14], [4, 14]], [1, 2], 'south'],
    [[[6, 13], [7, 13]], [1, 2], 'north'], [[[6, 14], [7, 14]], [1, 2], 'south'],
    [[[9, 13], [10, 13]], [1, 2], 'north'], [[[9, 14], [10, 14]], [1, 2], 'south'],
    [[[13, 13], [14, 13]], [1, 2], 'north'], [[[13, 14], [14, 14]], [1, 2], 'south'],
    [[[2, 12], [2, 13]], [1, 2], 'east'], // against the west gable wall
  ],
}

// Lamps: hung on chains (furniture spots). Ground floors from the joists of the floor above, lofts from the tie
// beams at y 8, the vault from the ground floor's joists, the Rack Hall from its ridge.
export const LAMPS = {
  1: [[5, 3, 4, 0], [5, 3, 9, 0], [5, 7, 3, 0], [5, 7, 8, 0], [4, -1, 3, 0], [6, -1, 9, 0]],
  2: [[12, 3, 4, 0], [12, 3, 8, 0], [12, 7, 4, 0], [12, 7, 8, 0]],
  3: [[5, 4, 14, 2], [8, 4, 14, 2], [12, 4, 14, 2]],
}
export const TIES = { 1: { x0: 2, x1: 8, zs: [3, 8], y: 8 }, 2: { x0: 10, x1: 14, zs: [4, 8], y: 8 } }

// The walking line: step -> street door -> Store Room (the desk on the left) -> up the flight along the west wall ->
// the Loft -> (II) through the party door at (9, 5, 10) into the Twin Loft; the cart arch -> the Twin Store ->
// (III) through the back doors into the Rack Hall's aisles -> the back door to the yard. Down: the hatch at
// (2, 0, 10), the ladder to the Vault.
export const WALK = [[5, 0, 0], [5, 1, 1], [5, 1, 3], [4, 1, 4], [2, 1, 4], [2, 1, 5], [2, 2, 6], [2, 3, 7], [2, 4, 8],
  [5, 5, 8], [8, 5, 10], [10, 5, 10], [12, 1, 2], [12, 1, 10], [12, 1, 12], [5, 1, 12], [8, 1, 16], [8, 0, 18]]

export const OUTSIDE = { hay: [[15, 0, 0], [16, 0, 0], [16, 1, 0]], kneelers: true }
