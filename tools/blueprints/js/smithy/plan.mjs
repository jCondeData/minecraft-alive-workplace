// The Smithy (smithy, smithy_2; ROADMAP 49, brief tools/blueprints/briefs/smithy.md), the plan: drawn before any wall.
// recipe.mjs assembles it from the parts library (../parts) for LEVEL 1 or 2.
//
// Design notes. Three rough concepts first:
//   A. "The Forge Hall": one long stone hall, ridge along x, a fat brick chimney up its west gable, a lean-to coal shed
//      on the back. Plan: one room, the three job blocks round the walls, the hearth under the chimney. Story: the
//      village smith's shop, as every village has one. Safe, and reads as any house with a big chimney.
//   B. "The Furnace Stack": a truncated stone pyramid (a cold-blast furnace stack, 7 wide stepping in to 5 and 3 and
//      smoking at the top) with a low stone-and-timber casting shed leaning against its east face, its gable to the
//      street. Plan: the furnace chamber in the stack's foot (the blast furnace in a brick mouth, the coal vault under
//      it), the smiths' shop in the shed through an arch. Story: the stack came first, the smiths built their shop
//      against its warm wall; Level II's works grow a second, smaller stack and a longer shed for the ore.
//   C. "The Open Forge": a cruck-framed open-sided shed under one steep roof with a smoke hood in the middle, a
//      stone coal store at the back. Plan: the hearth in the centre, anvil and trough round it, open to the road.
//      Story: a forge as a market shelter. Open sides fail the open-wall check, and it says nothing of the Miner.
//   B is strongest: its silhouette alone says "iron is made here" (no other build in the library is a tapering
//   furnace stack), the hero mass is the job itself rather than a chimney stuck on a house, it gives the Miner his
//   own room at the furnace's foot with his coal vault under it (the brief's cellar becomes the furnace's coal store,
//   not a basement added to a shop), and Level II reads at once as "the works doubled" (a second stack). Built: B.
//
// Coordinates: x across the front (west 0 -> east 13), y up (0 = the layer on the clicked block; the cellar is
// y -4..-1), z from the front (z = 0, north) to the back.
//
// Masses (named parts the owner's feedback can point at):
//   Stack       the hero: 7 x 7 at x 0..6, z 2..8, dressed stone over a cobble base course with cobble quoins, a
//               string course at the shed's eave line (y 4), a ledge of stairs, 5 x 5 to y 10 with a recessed
//               barred slit in each face, a ledge, a 3 x 3 flue to y 14, a crown of brick slabs and the smoke at
//               y 16 (twice the shed's ridge). Its west face is pilasters (z 2, 5, 8) with recessed barred windows
//               between them over a battered foot; its back has corner buttresses and a stepped buttress behind the
//               furnace. Its foot is hollow: the Forge Hall's west end, the Miner's furnace in its back wall.
//   Forge Hall  one hall: the shed and the stack's foot, open to each other under a dark oak lintel (x 6,
//               z 3..7). The door leads straight to the anvil, the quench tub beside it, the blast furnace across
//               the hall, and along the front a clear path to the vault stair.
//   Shed        the casting shed against the stack's east face: stone walls x 6..12, z 1..8 (II: to z 14), a dark oak
//               beam at y 4, a 45-degree deepslate roof (ridge along z at x 9, y 8) over brick-nogged timber gables,
//               the front gable to the street with a hooded door.
//   Trough      a stone box of water under the stack's barred forge window, on the street; inside, the quench tub.
//   Stack II    (Level II) the second, smaller furnace stack, 5 x 5 at x 2..6, z 9..13, stepping to 3 x 3, smoking at
//               y 11: the Armorer's furnace in its foot, opening into the shed's new back bay.
//   Ore Shed    (Level II) a lean-to against Stack II's west face, its roof tied into the stack, over bins of raw
//               iron and coal waiting to be charged.
//   Coal Vault  the cellar under the stack (walls x 0..6, z 2..8, floor y -4), down a stone stair from the hall: coal and ore bins
//               along its walls, two lamps, and a rail run from a buffer to the chest it unloads into.

export const SIZES = { 1: [14, 17, 10], 2: [14, 17, 16] } // w, h above the ground, d (I: <= 14 x 20 x 10; II: <= 18 x 28 x 22)
export const BASEMENT = 4

// Palette (60/30/10): grey rubble stone (the stacks, the shed's walls, plinths, the vault) / dark oak frame with
// brick nogging in the gables and a dark deepslate roof / fired brick (the flues, the furnace mouths) and iron (bars,
// chains, lanterns). Why: a works of stone that fire can't hurt, darkest at the plinth; the dark roof and frame tie
// the low shed to the soot of the flues, and the warm brick marks every place heat comes from.
export const PALETTE = {
  plinth: [['cobblestone', 5], ['mossy_cobblestone', 1], ['andesite', 2]], // base courses, aprons, yards
  rubble: [['stone', 4], ['cobblestone', 3], ['andesite', 2]], // the shed's walls
  ashlar: [['stone_bricks', 7], ['cracked_stone_bricks', 1]], // the stacks' faces (cracks in patches)
  quoin: ['cobblestone', 'andesite'], // the stacks' corners and pilasters
  course: 'polished_andesite', // the string course
  ledge: 'stone_brick_stairs',
  dressed: 'stone_bricks',
  floor: 'polished_andesite', // the hall's floor (the vault's ceiling)
  rim: 'polished_andesite', // the quench tub's rim
  vaultWall: [['cobblestone', 3], ['stone', 2], ['andesite', 1]],
  vaultFloor: 'cobbled_deepslate',
  vaultStairs: 'cobblestone_stairs',
  brick: 'bricks', brickStairs: 'brick_stairs', brickSlab: 'brick_slab',
  frame: 'dark_oak_log',
  nog: 'bricks', // brick nogging between the gables' timbers
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  trim: 'dark_oak',
  wood: 'spruce', // doors, shutters, fences, counters, shelves
  glass: 'glass_pane',
  bars: 'iron_bars',
}

export const MASSES = {
  stack: { cx: 3, cz: 5, tiers: [{ w: 7, top: 6 }, { w: 5, top: 10 }, { w: 3, top: 14 }], courseY: 4 },
  // the west face: pilasters at z 2, 5, 8 on x 0; between them the wall steps back to x 1 over a batter at y 1,
  // barred windows at y 2..3, the string course across at y 4
  westFace: { panels: [[3, 4], [6, 7]], batterY: 1, windowY: [2, 3] },
  chamber: { x0: 2, x1: 5, z0: 3, z1: 7, corbelY: 4 }, // the hall's west end, in the stack's foot
  shed: { x0: 6, x1: 12, z0: 1, z1: { 1: 8, 2: 14 }, wallTop: 3, beamY: 4,
    roof: { axis: 'z', across: [5, 13], eaveY: 4 }, ties: { 1: [3, 6], 2: [3, 6, 11] } },
  stack2: { cx: 4, cz: 11, tiers: [{ w: 5, top: 5 }, { w: 3, top: 9 }], courseY: 4 },
  chamber2: { x0: 3, x1: 5, z0: 10, z1: 12, top: 3 },
  cellar: { x0: 0, z0: 2, x1: 6, z1: 8, depth: 4 }, // walls; the inside x 1..5, z 3..7 is dug
  track: { rails: [[2, -3, 3], [3, -3, 3], [4, -3, 3]], buffer: [1, -3, 3] }, // a rail run from its buffer to the chest
  buttresses: [ // against the stacks' backs: corners and a stepped one behind each furnace mouth
    { level: 1, only: true, cells: [[2, 9], [3, 9], [4, 9]], top: 3, facing: 'north' },
    { level: 1, only: true, cells: [[6, 9]], top: 2, facing: 'north' },
    { level: 1, cells: [[0, 9], [1, 9]], top: 2, facing: 'north' },
    { level: 2, cells: [[3, 14], [4, 14], [5, 14]], top: 2, facing: 'north' },
  ],
  oreShed: { level: 2, x: [0, 1], z: [10, 13], low: 3, posts: [[0, 13]] }, // a lean-to against Stack II's west face
}

export const OPENINGS = {
  door: { at: [9, 1, 1], facing: 'south', step: [9, 0, 0] },
  hall: { x: 6, z: [3, 7], height: 2 }, // the stack's foot open to the shed under a lintel at y 3
  hall2: { x: 6, z: [10, 12], height: 2 }, // (II) chamber II open to the back bay the same way
  crossWall: { level: 2, z: 8, x: [7, 11] }, // (II) Level I's back wall comes down: one long hall under the old beam
  // the way down: a stone stair along the hall's west wall, the floor open over its three steps (head room)
  cellarStair: { x: 2, steps: [[-1, 4], [-2, 5], [-3, 6]], rises: 'north', open: [4, 5, 6], fill: [[-2, 4], [-3, 4], [-3, 5]] },
  windows: [ // glass with shutters (every shed window alike)
    { cells: [[7, 2, 1], [7, 3, 1]], out: 'north' },
    { cells: [[11, 2, 1], [11, 3, 1]], out: 'north' },
    { cells: [[12, 2, 3], [12, 3, 3]], out: 'east' },
    { cells: [[12, 2, 6], [12, 3, 6]], out: 'east' },
    { cells: [[9, 2, 8], [9, 3, 8]], out: 'south', only: 1 },
    { cells: [[12, 2, 11], [12, 3, 11]], out: 'east', level: 2 },
    { cells: [[9, 2, 14], [9, 3, 14]], out: 'south', level: 2 },
  ],
  slits: [ // the stacks' barred slits (iron bars: the forge glow seen from the street)
    { cells: [[3, 2, 2], [3, 3, 2]] },
    { cells: [[5, 2, 8], [5, 3, 8]], only: 1 },
    { cells: [[2, 2, 11], [2, 3, 11]], level: 2 },
  ],
}

// Job blocks and the forge's fittings (fixtures: vanilla blocks the game uses as themselves).
export const FORGE = {
  blastFurnace: [3, 1, 8], // the Miner's (I) / the Armorer's or the Miner's (II), in the stack's back wall
  blastFurnace2: [4, 1, 13], // (II) in stack II's back wall
  anvil: [9, 1, 4], // straight ahead of the door
  quench: { cells: [[10, 4]], y: 1 }, // the quench tub beside the anvil (rimmed: the smithing table and two stones)
  smithingTable: [11, 1, 4], // the Toolsmith's, the tub's east rim
  grindstone: [11, 1, 6], // the Weaponsmith's
  anvil2: [9, 1, 11], // (II) a chipped one in the back bay
  trough: { cells: [[2, 1], [3, 1], [4, 1]], y: 1 }, // the street trough under the forge slit
}

// Furniture and stores, footprint first. kind: a furniture.py kind or a fixture. Chests stand against a wall.
export const FURNITURE = [
  // the shed end of the hall: a smith's bench along the back wall, shelves, two lamps from the tie beams
  { level: 1, room: 'Hall', kind: 'counter', part: 'counter', pos: [8, 1, 7], facing: 'north', wood: 'spruce', top: 'polished_andesite_slab' },
  { level: 1, room: 'Hall', kind: 'counter', part: 'counter', pos: [9, 1, 7], facing: 'north', wood: 'spruce', top: 'polished_andesite_slab' },
  { level: 1, room: 'Hall', kind: 'shelf', part: 'stockShelf', pos: [9, 3, 7], facing: 'north', wood: 'spruce', on: 'candle' },
  { level: 1, room: 'Hall', kind: 'fixture', part: 'chest', pos: [7, 1, 7], facing: 'north', block: 'chest' },
  { level: 1, room: 'Hall', kind: 'shelf', part: 'stockShelf', pos: [11, 3, 4], facing: 'west', wood: 'spruce', on: 'candle' },
  { level: 1, room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [9, 3, 3], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [9, 3, 6], facing: 'north', wood: 'spruce' },
  // the stack end: the Miner's chest by the furnace, a lamp from the vault
  { level: 1, room: 'Hall', kind: 'fixture', part: 'chest', pos: [5, 1, 7], facing: 'north', block: 'chest' },
  { level: 1, room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 5], facing: 'north', wood: 'spruce' },
  // Coal Vault: chests at the rail's end and the back, two lamps from the floor above
  { level: 1, room: 'Vault', kind: 'fixture', part: 'chest', pos: [5, -3, 7], facing: 'north', block: 'chest' },
  { level: 1, room: 'Vault', kind: 'fixture', part: 'chest', pos: [5, -3, 3], facing: 'west', block: 'chest' },
  { level: 1, room: 'Vault', kind: 'lamp', part: 'hangingLamp', pos: [3, -1, 5], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Vault', kind: 'lamp', part: 'hangingLamp', pos: [1, -1, 5], facing: 'north', wood: 'spruce' },
  // (II) Back Bay and Chamber II: the Armorer's chest by his furnace, the store's chests, a lamp from the tie beam
  { level: 2, room: 'Chamber II', kind: 'fixture', part: 'chest', pos: [3, 1, 12], facing: 'east', block: 'chest' },
  { level: 2, room: 'Chamber II', kind: 'lamp', part: 'hangingLamp', pos: [4, 3, 11], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Back Bay', kind: 'fixture', part: 'chest', pos: [7, 1, 13], facing: 'east', block: 'chest' },
  { level: 2, room: 'Back Bay', kind: 'fixture', part: 'chest', pos: [7, 1, 9], facing: 'east', block: 'chest' },
  { level: 2, room: 'Back Bay', kind: 'lamp', part: 'hangingLamp', pos: [9, 3, 11], facing: 'north', wood: 'spruce' },
]

// The coal and ore store (counted by qaSmithyHoldsItsForge: Level II's must be bigger).
export const STORE = [
  { level: 1, at: [2, 1, 7], block: 'coal_block', h: 1 }, // the coal bin beside the furnace, closing the stair's end
  { level: 1, at: [1, -3, 4], block: 'coal_block', h: 1 }, // the vault: bins along its west wall and its back
  { level: 1, at: [1, -3, 6], block: 'coal_block', h: 2 },
  { level: 1, at: [1, -3, 7], block: 'coal_block', h: 1 },
  { level: 1, at: [4, -3, 7], block: 'raw_iron_block', h: 2 },
  { level: 1, at: [5, -3, 6], block: 'raw_iron_block', h: 1 },
  { level: 2, at: [11, 1, 10], block: 'coal_block', h: 1 }, // the back bay's bins along the east wall
  { level: 2, at: [11, 1, 12], block: 'raw_iron_block', h: 1 },
  { level: 2, at: [11, 1, 13], block: 'raw_copper_block', h: 1 },
  { level: 2, at: [10, 1, 13], block: 'coal_block', h: 1 },
  { level: 2, at: [1, 1, 10], block: 'raw_iron_block', h: 2 }, // the Ore Shed's bins against Stack II
  { level: 2, at: [1, 1, 11], block: 'raw_iron_block', h: 1 },
  { level: 2, at: [1, 1, 12], block: 'coal_block', h: 2 },
  { level: 2, at: [0, 1, 12], block: 'coal_block', h: 1 },
]

// The walking line: the waiting spots (1/4/7/10, 0, -3) -> the step -> the door -> the anvil -> along the front of
// the hall -> the furnace (3, 1, 7) and down the vault stair; in II on into the back bay and chamber II.
export const WALK = [
  [9, 0, -1], [9, 0, 0], [9, 1, 1], [9, 1, 2], [9, 1, 3], [8, 1, 3], [6, 1, 3], [4, 1, 3], [4, 1, 6], [3, 1, 7],
  [2, 1, 3], [2, -1, 4], [2, -2, 5], [2, -3, 6], [2, -3, 7], [3, -3, 7], [3, -3, 5],
  [10, 1, 7], [10, 1, 8], [9, 1, 9], [7, 1, 11], [6, 1, 11], [4, 1, 12],
]

export const OUTSIDE = {
  lamp: [0, 1, 0], // a lamp on a post at the apron's corner
  yardLamp: [5, 1, 15], // (II) a lamp on a post at the yard's back corner
}
