// Storehouse (Version A), the plan: drawn before any wall. Three levels, each the one before plus what it adds; every
// store, the job block, the vault and every lamp first, then recipe.mjs assembles it from the parts library (../parts).
//
// Three rough concepts (ROADMAP 49, REBUILD.md):
//   1. "The Hoist House": a tall, narrow, gable-fronted merchant's warehouse of red brick under a steep dark roof whose
//      ridge runs out two blocks over the street as a hood, a hoist beam and chain under it over a loading hatch.
//      II adds a lower gable-fronted cart shed beside it (a row of two gables, open loading bays); III closes the back
//      with a long range across both, joined by a covered passage. Story: a trading village's warehouse that fills
//      and builds on, gable by gable, the way harbour warehouses grew.
//   2. "The Tithe Barn": a long, low rubble barn with a huge hipped roof and a wagon porch in the middle of its long
//      side; levels add bays along the ridge. Story: the harvest store of a farming village. Reads as a barn, not as
//      the village's store, and a longer ridge each level is the same picture three times.
//   3. "The Granary Tower": a square stone tower on staddles with a pyramid roof, stores floor over floor. Story: the
//      village keeps its goods high and dry. But stores count only 4 up from the job block, so the tower's height would
//      be empty show, and a tower cannot grow sideways into II and III.
// Strongest: 1. The hoist and the hatch say "goods go in here" from the street before anyone reads a sign, the steep
// gable is a silhouette no house in the village has, and its growth (a second, lower gable, then a back range) gives
// each level a new shape instead of a longer one, all of it inside the job block's reach (8 across, 4 up and down).
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block, the vault below it down
// to -4), z from the front (z = 0, north, the street) to the back.
//
// Masses (named parts the owner's feedback can point at):
//   Hoist House  walls x 1..9, z 2..11; ground floor y 1..3, loft floor y 4 (a stone band outside), loft y 5..;
//                steep roof across x 0..10 with its ridge along z from the street hood (z 0..1) to z 12, ridge y 14.
//                Front: a 3-wide cart arch (x 4..6, y 1..3) closed by a board wagon gate with a wicket door, a dark oak lintel, the
//                loading hatch over it (x 4..6, y 5..6) with its doors folded back, a round light, the hoist beam
//                (x 5, y 12) out to z 0 with its chain and hook lantern.
//   Vault        under the Hoist House's back half: walls x 1..9, z 6..11, floor y -4, room y -3..-1 (I, kept).
//   Cart Shed    (II) walls x 9..16 (x 9 shared), z 3..11, set back a block; two open loading bays in front between
//                dark oak posts, its own lower 45-degree gable (across x 9..17, ridge y 8) facing the street.
//   Back Range   (III) walls x 1..16, z 13..17 behind both, a covered passage (z 12) under its eave between them; a
//                45-degree roof with its ridge along x (y 7), gables to the west and east.

export const IDS = { 1: 'storehouse', 2: 'storehouse_2', 3: 'storehouse_3' }
export const SIZES = { 1: [11, 15, 13], 2: [18, 15, 13], 3: [18, 15, 19] }
// Round 2 (the critic): pilasters and a plinth course on the backs and long sides, dormers on both long slopes, louvres
// on the shed's and the range's ridges, a landing ledge under the hoist, a canopy over the shed's gate, a working tally corner. // w, h, d above ground (I: at most 13 x 16 x 13)
export const BASEMENT = 4

// Palette (60/30/10): red brick walls / charcoal deepslate tile roofs and dark oak timber (lintels, posts, the hoist) /
// pale stone dressings (plinth, quoins, the floor band, sills) with spruce doors and shutters. Why: a warehouse of
// fired brick reads as built to last and to keep things dry, unlike the village's plank houses; the dark roof makes the
// tall gable a clean silhouette; the stone picks out the corners and the floor line so the big brick faces have scale.
export const PALETTE = {
  brick: 'bricks',
  stone: [['stone_bricks', 7], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1]],
  dressing: 'stone_bricks', dressingAlt: 'polished_andesite', dressingStairs: 'stone_brick_stairs',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  frame: 'dark_oak_log', trim: 'dark_oak',
  wood: 'spruce', gate: 'stripped_spruce_wood', floor: 'spruce_planks', shedFloor: 'stone_bricks',
  glass: 'glass_pane',
  vaultWall: [['deepslate_bricks', 6], ['cracked_deepslate_bricks', 1]], vaultFloor: 'polished_deepslate',
  store: 'chest', job: 'aliveworkplace:storehouse', lamp: 'lantern', goods: 'hay_block', crate: 'stripped_spruce_wood', sack: 'brown_wool',
}

export const MASSES = {
  hoist: { walls: { x0: 1, x1: 9, z0: 2, z1: 11 }, room: { x0: 2, x1: 8, z0: 3, z1: 10 }, loftY: 4,
    roof: { axis: 'z', across: [0, 10], along: [0, 12], eaveY: 5, steep: true } },
  vault: { x0: 1, z0: 6, x1: 9, z1: 11, depth: 4, ladder: { x: 2, z: 10, facing: 'east' } },
  shed: { walls: { x0: 9, x1: 16, z0: 3, z1: 11 }, room: { x0: 10, x1: 15, z0: 4, z1: 10 }, posts: [13, 16],
    roof: { axis: 'z', across: [9, 17], along: [2, 12], eaveY: 4, steep: false } },
  range: { walls: { x0: 1, x1: 16, z0: 13, z1: 17 }, room: { x0: 2, x1: 15, z0: 14, z1: 16 }, passageZ: 12, posts: [5, 12],
    roof: { axis: 'x', across: [12, 18], along: [0, 17], eaveY: 4, steep: false } },
}

export const ROOMS = [
  { level: 1, name: 'Store floor', use: 'the tally desk (the Storehouse block) and racks of chests', box: [2, 1, 3, 8, 3, 10] },
  { level: 1, name: 'Loft', use: 'more racks under the steep roof, the loading hatch', box: [2, 5, 3, 8, 7, 10] },
  { level: 1, name: 'Vault', use: 'the cellar store, 4 deep, a ladder under a hatch', box: [2, -3, 7, 8, -1, 10] },
  { level: 2, name: 'Cart Shed', use: 'unloading bays and racks', box: [10, 1, 4, 15, 3, 10] },
  { level: 3, name: 'Back Range', use: 'the long store behind, and the passage to it', box: [2, 1, 12, 15, 3, 16] },
]

export const OPENINGS = {
  cartArch: { x0: 4, x1: 6, y0: 1, y1: 3, z: 2, step: 0 /* y of the stair step at z 1 */ },
  hatch: { cells: [[4, 5, 2], [5, 5, 2], [6, 5, 2], [4, 6, 2], [5, 6, 2], [6, 6, 2]] },
  hoist: { at: [5, 12, 2], reach: 2, drop: 3 },
  windows: [
    { level: 1, cells: [[2, 2, 2], [2, 3, 2]], out: 'north', dress: 'sill' },
    { level: 1, cells: [[8, 2, 2], [8, 3, 2]], out: 'north', dress: 'sill' },
    { level: 1, cells: [[5, 9, 2]], out: 'north', dress: null },
    { level: 1, cells: [[1, 2, 6], [1, 3, 6]], out: 'west', dress: 'shutters' },
    { level: 1, until: 1, cells: [[9, 2, 6], [9, 3, 6]], out: 'east', dress: 'shutters' }, // II: the doorway to the shed
    { level: 1, cells: [[5, 6, 11], [5, 7, 11]], out: 'south', dress: null },
    { level: 2, cells: [[13, 6, 3]], out: 'north', dress: null },
    { level: 2, cells: [[16, 2, 7], [16, 3, 7]], out: 'east', dress: 'shutters' },
    { level: 3, cells: [[4, 3, 17]], out: 'south', dress: null },
    { level: 3, cells: [[8, 3, 17]], out: 'south', dress: null },
    { level: 3, cells: [[11, 3, 17]], out: 'south', dress: null },
    { level: 3, cells: [[1, 2, 15], [1, 3, 15]], out: 'west', dress: 'shutters' },
    { level: 3, cells: [[16, 2, 15], [16, 3, 15]], out: 'east', dress: 'shutters' },
  ],
  doorways: [
    { level: 2, at: [9, 1, 6] },   // store floor -> cart shed
    { level: 3, at: [6, 1, 11] },  // store floor -> passage
    { level: 3, at: [15, 1, 11] }, // cart shed -> passage
    { level: 3, at: [6, 1, 13] }, { level: 3, at: [12, 1, 13] }, // passage -> back range
  ],
  loftLadder: { x: 5, z: 10, facing: 'north', y0: 1, y1: 4 }, // up the back wall, through the loft floor
}

// Every store (chests, by rack: cells [x, z], y0..y1, facing into the room). Job block x 8, y 1, z 9: reach x 0..16,
// y -3..5, z 1..17; every store below sits inside it.
export const DESK = { at: [8, 1, 9], facing: 'west', side: 'z', high: 2 } // chests (8, 1..2, 8) and (8, 1..2, 10)
export const STORES = [
  { level: 1, room: 'Store floor', cells: [[2, 4], [2, 5], [2, 7], [2, 8]], y: [1, 2], facing: 'east' },
  { level: 1, room: 'Store floor', cells: [[8, 4], [8, 5]], y: [1, 2], facing: 'west' },
  { level: 1, room: 'Loft', cells: [[2, 4], [2, 5], [2, 7], [2, 8]], y: [5, 5], facing: 'east' },
  { level: 1, room: 'Loft', cells: [[8, 4], [8, 5], [8, 7], [8, 8]], y: [5, 5], facing: 'west' },
  { level: 1, room: 'Vault', cells: [[3, 7], [4, 7], [6, 7], [7, 7]], y: [-3, -2], facing: 'south' },
  { level: 1, room: 'Vault', cells: [[4, 10], [5, 10], [7, 10], [8, 10]], y: [-3, -2], facing: 'north' },
  { level: 1, room: 'Vault', cells: [[8, 8], [8, 9]], y: [-3, -2], facing: 'west' },
  { level: 2, room: 'Cart Shed', cells: [[15, 5], [15, 6], [15, 8], [15, 9]], y: [1, 2], facing: 'west' },
  { level: 2, room: 'Cart Shed', cells: [[11, 10], [12, 10], [13, 10], [14, 10]], y: [1, 2], facing: 'north' },
  { level: 3, room: 'Back Range', cells: [[2, 16], [3, 16], [5, 16], [6, 16], [9, 16], [10, 16], [12, 16], [13, 16]], y: [1, 2], facing: 'north' },
  { level: 3, room: 'Back Range', cells: [[2, 14], [3, 14], [14, 14], [15, 14]], y: [1, 2], facing: 'south' },
]

// Furniture: the lamps (recorded as spots); the stores above are fixtures. Goods: hay bales waiting to go in.
export const FURNITURE = [
  { level: 1, room: 'Store floor', kind: 'lamp', part: 'hangingLamp', pos: [4, 3, 5], facing: 'north', chain: 0, wood: 'spruce' },
  { level: 1, room: 'Store floor', kind: 'lamp', part: 'hangingLamp', pos: [5, 3, 8], facing: 'north', chain: 0, wood: 'spruce' },
  { level: 1, room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [5, 8, 6], facing: 'north', chain: 5, wood: 'spruce' },
  { level: 1, room: 'Vault', kind: 'lamp', part: 'hangingLamp', pos: [5, -1, 8], facing: 'north', chain: 0, wood: 'spruce' },
  { level: 2, room: 'Cart Shed', kind: 'lamp', part: 'hangingLamp', pos: [13, 6, 7], facing: 'north', chain: 1, wood: 'spruce' },
  { level: 3, room: 'Back Range', kind: 'lamp', part: 'hangingLamp', pos: [4, 5, 15], facing: 'north', chain: 1, wood: 'spruce' },
  { level: 3, room: 'Back Range', kind: 'lamp', part: 'hangingLamp', pos: [11, 5, 15], facing: 'north', chain: 1, wood: 'spruce' },
  { level: 3, room: 'Back Range', kind: 'lamp', part: 'hangingLamp', pos: [9, 3, 12], facing: 'north', chain: 0, wood: 'spruce' },
]
export const GOODS = [
  { level: 1, cells: [[2, 1, 3]] },
  { level: 2, cells: [[14, 1, 4], [15, 1, 4], [15, 2, 4]] },
]

// The walking line: street -> step -> cart arch -> aisle -> tally desk; the hatch (2, 0, 10) down to the vault; the
// ladder (5, 1..4, 10) up to the loft aisle; (II) the doorway (9, 1, 6) into the shed; (III) the back doorway (6, 1, 11)
// across the passage into the range. The open floor over the vault: (4, 1, 8).
export const WALK = [
  [5, 0, 0], [5, 0, 1], [5, 1, 2], [5, 1, 3], [5, 1, 6], [5, 1, 9], [7, 1, 9],
  [3, 1, 10], [2, 1, 10], [2, -3, 10], [5, -3, 8],
  [5, 1, 9], [5, 4, 10], [5, 5, 9], [5, 5, 4],
  [8, 1, 6], [10, 1, 6], [13, 1, 7], [13, 1, 3],
  [6, 1, 10], [6, 1, 12], [6, 1, 14], [10, 1, 15],
]
export const OPEN_FLOOR = [4, 1, 8]
