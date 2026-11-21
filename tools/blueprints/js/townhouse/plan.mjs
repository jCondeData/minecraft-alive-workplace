// Burgher's Townhouse (ROADMAP 49, brief tools/blueprints/briefs/townhouse.md): the plan, written before any block.
//
// THREE CONCEPTS
//  A. The Gabled Merchant's House. A tall, narrow three-storey house standing gable-end to the street, the way a
//     burgher builds where frontage is dear: a stone ground storey (the shop and the kitchen), a timber-framed first
//     floor jettied out over the street on brackets, a steep tiled gable with the attic store and a hoist beam at its
//     apex, the chimney stack up the back gable. Grows like a town plot fills: II a lower side wing with its own small
//     street gable (the counting house below, two bedrooms above); III a square stone tower behind it, the family's
//     strongroom and lookout, its pyramid roof the highest point of the house; IV a vaulted cellar under the shop.
//     Story: the burgher made his money in trade and every upgrade shows it, the shop first, then the office, then
//     the tower from which he watches the road for his carts.
//  B. The Row Pair. Two narrow houses under one long roof, ridge along the street, a shared chimney on the party wall
//     and two front doors: literally the two households the Steward builds it for. II adds a back range across both,
//     III a gallery over a yard. Honest to the brief's "two households", but from the street it is a terrace with a
//     piece missing at each end, and the silhouette is one long box.
//  C. The Courtyard House. A low L of stone round a walled front yard with a gate, hip roofs, the hall across the back.
//     Story: a burgher who would rather be a squire. Calm, but low, wide and inward-looking: it spends the 15-wide plot
//     on a yard, fights the brief's town character, and an L under hips has no hero.
//  Strongest: A. It is the one with a face you'd know in outline (the tall jettied gable and, from III, the tower),
//  each level reads as the same family adding to the same house (shop, office, tower, cellar), and the jetty, the
//  brackets, the hoist beam and the stone-under-timber split give every face depth. B is the safe one. Built: A.
//
// STORY: the burgher's house on the market street. Below, the shop: stone, two wide windows with flower sills either
// side of the door, lanterns hung under the jetty. Above, the best room over the street (the hall, with the clock and
// the settle) and the parents' chamber behind it. Under the steep tiles, the attic: the children's beds at the front,
// the goods hauled up by the hoist at the back.
//
// PALETTE 60/30/10: 60 white plaster (white terracotta) panels on the upper storeys and stone bricks (a few cracked) on
// the ground storey; 30 dark oak frame (posts, beams, brackets, barge boards) and the terracotta-red roof (polished
// granite tiles: warm and light, so the steep roof never reads as one dark mass); 10 spruce joinery (doors, shutters,
// floors, furniture) and the flowers in the sills. Darkest at the foot (cobble plinth), lightest at the gable.
export const LEVELS = [1, 2, 3, 4]
export const IDS = { 1: 'townhouse', 2: 'townhouse_2', 3: 'townhouse_3', 4: 'townhouse_4' }
export const SIZES = { 1: [11, 22, 14], 2: [18, 22, 14], 3: [19, 26, 19], 4: [19, 26, 19] }
export const BASEMENT = { 4: 4 }

export const PALETTE = {
  plinth: [['cobblestone', 3], ['mossy_cobblestone', 1]],
  stone: [['stone_bricks', 7], ['cracked_stone_bricks', 1]],
  weathered: [['stone_bricks', 4], ['mossy_stone_bricks', 2], ['cracked_stone_bricks', 1]],
  quoin: 'polished_andesite',
  plaster: 'white_terracotta',
  frame: 'dark_oak_log',
  trim: 'dark_oak', // brackets, barge boards (stairs)
  roof: { full: 'polished_granite', stairs: 'polished_granite_stairs', slab: 'polished_granite_slab' },
  wood: 'spruce', floor: 'spruce_planks', partition: 'spruce_planks', glass: 'glass_pane', sill: 'stone_brick_stairs',
  kitchenFloor: 'stone_bricks', cellarFloor: 'cobblestone', cellarWall: [['cobblestone', 3], ['stone', 2], ['mossy_cobblestone', 1]],
  finial: 'lightning_rod',
}

// MASSES (template coordinates; the front is z = 0, x runs west 0 -> east)
export const MASSES = {
  // House: ground storey walls x 1..9, z 2..12 (stone, y 1..4); the first floor (beam y 5, timber y 6..8) and the attic
  // (beam y 9, knee wall y 10) jettied a block out over the street to z 1; a steep gable roof, ridge along z (the gable
  // to the street), eaves at y 10 over x 0 and x 10, ridge y 19, overhanging the front at z 0 and the back at z 13.
  house: { x0: 1, x1: 9, z0: 2, z1: 12, jetty: 1, firstY: 5, atticY: 9, kneeTop: 10,
    roof: { axis: 'z', across: [0, 10], along: [0, 13], eaveY: 10, steep: true },
    posts: { front: [1, 4, 6, 9], side: [1, 4, 8, 12] }, brackets: [1, 3, 7, 9] },
  // two gabled dormers on the long west slope (the east one is the wing's side from II), lighting the attic's beds
  // and its stair head: faces on the knee line x 1, windows y 11..12
  dormers: { at: [4, 9], face: 1, eave: 0, y0: 11 },
  // the stack up the back gable, from the plinth past the ridge; the hearth set into the kitchen's back wall at (5, 1, 12),
  // in front of the breast: a two-high fireplace opening (fire at y 1, the throat at y 2) under a stone lintel, the stack
  // its flue
  chimney: { x: 5, z: 13, top: 20 },
  // the hoist beam at the gable's apex, a chain and a lantern hanging from it over the street
  hoist: { x: 5, y: 17, z: 0 },
  // II: the counting-house wing on the east, its own small street gable set back to z 4: walls x 10..16, z 4..12,
  // stone y 1..4, timber y 6..9 (beams y 5 and y 9), a steep gable roof (ridge along z, y 16) over x 9..17, z 3..13
  wing: { x0: 10, x1: 16, z0: 4, z1: 12, posts: { front: [10, 12, 14, 16], side: [4, 6, 10, 12] },
    roof: { axis: 'z', across: [9, 17], along: [3, 13], eaveY: 9, steep: true } },
  // III: the strongroom tower behind the wing: stone walls x 13..17, z 13..17 up to y 17, floors y 5, 9 and 13, a steep
  // pyramid roof from y 18 (overhang x 12..18) to its peak at y 24, a copper finial on top
  // a ledge of dressed slabs a block out at each floor (y 5, 9, 13) breaks the shaft into storeys
  tower: { x0: 13, x1: 17, z0: 13, z1: 17, top: 17, floors: [5, 9, 13], ledge: 'stone_brick_slab', roof: { cx: 15, cz: 15, r: 3, y0: 18 },
    ladder: { x: 16, z: 16, y0: 6, top: 13, facing: 'west' } },
  // IV: the vault under the house, 4 deep (floor y -4), two stone ribs across it at z 5 and z 9, the hatch at (2, 0, 7)
  cellar: { x0: 1, z0: 2, x1: 9, z1: 12, depth: 4, ladder: { x: 2, z: 7, facing: 'east' }, ribs: [5, 9] },
}

export const ROOMS = [
  { level: 1, name: 'Shop', use: 'the counting table by the front window, a chest of stock, barrels of goods', box: [2, 1, 3, 7, 4, 7] },
  { level: 1, name: 'Kitchen', use: 'hearth in the back wall, stove, counters, a table for four, crockery', box: [2, 1, 8, 8, 4, 11] },
  { level: 1, name: 'Hall', use: 'the best room over the street: a settle, tea table, the clock, a picture', box: [2, 6, 2, 8, 8, 6] },
  { level: 1, name: 'Chamber', use: "the parents' bedroom behind the hall, the attic stair along its west wall", box: [3, 6, 8, 8, 8, 11] },
  { level: 1, name: 'Attic', use: "the children's two beds under the front gable, the hoist store at the back", box: [2, 10, 2, 8, 16, 11] },
  { level: 2, name: 'Counting house', use: "the wing's ground floor: the clerks' table, ledgers, its own street door", box: [11, 1, 5, 15, 4, 11] },
  { level: 2, name: 'Wing bedroom', use: 'two beds under the open roof, for the second household', box: [11, 6, 5, 15, 12, 11] },
  { level: 3, name: 'Strongroom', use: "the tower's ground floor: the chest and barrels that matter", box: [14, 1, 14, 16, 4, 16] },
  { level: 3, name: 'Tower rooms', use: 'a library room, a bedroom, the lookout study at the top, up a ladder', box: [14, 6, 14, 16, 16, 16] },
  { level: 4, name: 'Vault', use: 'the cellar under the shop: casks, a chest, a bench', box: [2, -3, 3, 8, -1, 11] },
]

const shut = { dress: 'shutters' }
const sill = (pots) => ({ dress: 'sill', pots })
export const OPENINGS = {
  frontDoor: { at: [5, 1, 2], facing: 'south', step: [5, 0, 1] },
  wingDoor: { at: [13, 1, 4], facing: 'south', step: [13, 0, 3], from: 2 },
  // the lower flight up the east wall from the shop, rising south, its top step in the first floor (landing (8, 6, 8))
  stairs: { flight: [[8, 1, 4], [8, 2, 5], [8, 3, 6], [8, 4, 7], [8, 5, 8]], rises: 'south', well: [[8, 5, 4], [8, 5, 5], [8, 5, 6], [8, 5, 7]],
    rail: [[7, 6, 5], [7, 6, 3], [8, 6, 3]] },
  // the attic flight up the chamber's west wall, rising north, its top step in the attic floor (landing (2, 10, 7)); the
  // well open over the foot step too, so the head clears the attic floor on the first step up (QaBuildReachGameTests)
  attic: { flight: [[2, 6, 10], [2, 7, 9], [2, 8, 8], [2, 9, 7]], rises: 'north', well: [[2, 9, 8], [2, 9, 9], [2, 9, 10]],
    rail: [[3, 10, 8], [3, 10, 10], [2, 10, 10]] },
  // the hall/chamber partition at z 7 (x 2..6), a door at x 4; the landing (x 7..8) open to both
  partition: { z: 7, x0: 2, x1: 6, door: [4, 6, 7] },
  doorways: [
    { at: [[9, 1, 10], [10, 1, 10]], from: 2 }, // kitchen -> counting house (I's kitchen east window)
    { at: [[9, 6, 10], [10, 6, 10]], from: 2 }, // chamber -> wing bedroom (I's chamber east window)
    { at: [[14, 1, 12], [14, 1, 13]], from: 3 }, // counting house -> strongroom
    { at: [[14, 6, 12], [14, 6, 13]], from: 3 }, // wing bedroom -> tower
  ],
  windows: [
    // house, ground storey (stone): sills with flowers on the plinth's step
    { cells: [[2, 2, 2], [3, 2, 2], [2, 3, 2], [3, 3, 2]], out: 'north', ...sill(['potted_red_tulip', 'potted_cornflower']) },
    { cells: [[7, 2, 2], [8, 2, 2], [7, 3, 2], [8, 3, 2]], out: 'north', ...sill(['potted_cornflower', 'potted_red_tulip']) },
    { cells: [[1, 2, 5], [1, 3, 5]], out: 'west', ...shut },
    { cells: [[1, 2, 9], [1, 3, 9]], out: 'west', ...shut },
    { cells: [[9, 2, 10], [9, 3, 10]], out: 'east', ...shut, until: 1 },
    { cells: [[3, 2, 12], [3, 3, 12]], out: 'south', ...sill(['potted_fern']) },
    { cells: [[7, 2, 12], [7, 3, 12]], out: 'south', ...sill(['potted_fern']) },
    // house, first floor (timber): shutters all round, the front's in one row on the jetty
    { cells: [[2, 6, 1], [3, 6, 1], [2, 7, 1], [3, 7, 1]], out: 'north', ...shut },
    { cells: [[5, 6, 1], [5, 7, 1]], out: 'north', ...shut },
    { cells: [[7, 6, 1], [8, 6, 1], [7, 7, 1], [8, 7, 1]], out: 'north', ...shut },
    { cells: [[1, 6, 2], [1, 6, 3], [1, 7, 2], [1, 7, 3]], out: 'west', ...shut },
    { cells: [[1, 6, 6], [1, 7, 6]], out: 'west', ...shut },
    { cells: [[1, 6, 10], [1, 7, 10]], out: 'west', ...shut },
    { cells: [[9, 6, 2], [9, 7, 2]], out: 'east', ...shut },
    { cells: [[9, 6, 6], [9, 7, 6]], out: 'east', ...shut, until: 1 },
    { cells: [[9, 6, 10], [9, 7, 10]], out: 'east', ...shut, until: 1 },
    { cells: [[3, 6, 12], [3, 7, 12]], out: 'south', ...shut },
    { cells: [[7, 6, 12], [7, 7, 12]], out: 'south', ...shut },
    // house, the gables
    { cells: [[4, 11, 1], [4, 12, 1]], out: 'north', ...shut },
    { cells: [[6, 11, 1], [6, 12, 1]], out: 'north', ...shut },
    { cells: [[5, 13, 1], [5, 14, 1]], out: 'north', dress: null },
    { cells: [[3, 11, 12], [3, 12, 12]], out: 'south', ...shut }, // either side of the stack
    { cells: [[7, 11, 12], [7, 12, 12]], out: 'south', ...shut },
    // II, the wing
    { cells: [[11, 2, 4], [11, 3, 4]], out: 'north', ...sill(['potted_allium']), from: 2 },
    { cells: [[15, 2, 4], [15, 3, 4]], out: 'north', ...sill(['potted_allium']), from: 2 },
    { cells: [[13, 6, 4], [13, 7, 4]], out: 'north', ...shut, from: 2 },
    { cells: [[13, 11, 4], [13, 12, 4]], out: 'north', ...shut, from: 2 },
    { cells: [[16, 2, 8], [16, 3, 8]], out: 'east', ...shut, from: 2 },
    { cells: [[16, 6, 8], [16, 7, 8]], out: 'east', ...shut, from: 2 },
    { cells: [[12, 2, 12], [12, 3, 12]], out: 'south', dress: null, from: 2 },
    { cells: [[12, 6, 12], [12, 7, 12]], out: 'south', dress: null, from: 2 },
    // III, the tower: a window a storey on its open faces, the lookout's on three sides
    { cells: [[15, 2, 17], [15, 3, 17]], out: 'south', ...shut, from: 3 },
    { cells: [[17, 6, 15], [17, 7, 15]], out: 'east', ...shut, from: 3 },
    { cells: [[15, 10, 17], [15, 11, 17]], out: 'south', ...shut, from: 3 },
    { cells: [[13, 10, 15], [13, 11, 15]], out: 'west', ...shut, from: 3 },
    { cells: [[13, 14, 15], [13, 15, 15]], out: 'west', ...shut, from: 3 },
    { cells: [[17, 14, 15], [17, 15, 15]], out: 'east', ...shut, from: 3 },
    { cells: [[15, 14, 17], [15, 15, 17]], out: 'south', ...shut, from: 3 },
  ],
}

// Every furniture piece, footprint first (from: the level that adds it). Facing: the way its user faces (a bed: foot
// to head; a chair: where the sitter looks, at its table).
const W = 'spruce', D = 'dark_oak'
export const FURNITURE = [
  // Shop (z 3..7): in by the door at (5, 1, 3); the counting table by the west window, a chest of stock on the west
  // wall, barrels of goods against the stair's side, a lamp over the table. (8, 1, 3) clear: the stair's foot.
  { kind: 'table', part: 'table', pos: [3, 1, 4], facing: 'north', wood: W, colour: 'green' },
  { kind: 'chair', part: 'chair', pos: [2, 1, 4], facing: 'east', wood: W },
  { kind: 'chair', part: 'chair', pos: [4, 1, 4], facing: 'west', wood: W },
  { kind: 'fixture', part: 'chest', pos: [2, 1, 6], facing: 'east', block: 'chest' },
  { kind: 'fixture', part: 'pantry', pos: [7, 1, 6], facing: 'west', block: 'barrel' },
  { kind: 'fixture', part: 'pantry', pos: [7, 1, 7], facing: 'west', block: 'barrel' },
  { kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 5], facing: 'north', wood: W },
  // Kitchen (z 8..11): the hearth in the back wall, in front of the stack's breast (its flue); the stove and a counter
  // with plates east of it, the pantry and a counter west, a shelf of crockery on the west wall; the table for four in
  // the middle. The walk from the shop runs down x 6..7 to (8, 1, 10) (II's doorway east).
  { kind: 'fixture', part: 'hearth', pos: [5, 1, 12], facing: 'north', throat: [5, 2, 12] },
  { kind: 'fixture', part: 'stove', pos: [7, 1, 11], facing: 'north', block: 'furnace' },
  { kind: 'counter', part: 'counter', pos: [6, 1, 11], facing: 'north', wood: W, topSlab: true },
  { kind: 'crockery', part: 'plates', pos: [6, 3, 11], facing: 'north', wood: W, block: 'flower_pot' },
  { kind: 'counter', part: 'counter', pos: [3, 1, 11], facing: 'north', wood: W },
  { kind: 'fixture', part: 'pantry', pos: [2, 1, 11], facing: 'north', block: 'barrel' },
  { kind: 'shelf', part: 'shelf', pos: [2, 3, 10], facing: 'east', wood: W, crockery: 'flower_pot' },
  { kind: 'table', part: 'table', pos: [4, 1, 9], facing: 'north', wood: W, colour: 'white' },
  { kind: 'chair', part: 'chair', pos: [3, 1, 9], facing: 'east', wood: W },
  { kind: 'chair', part: 'chair', pos: [5, 1, 9], facing: 'west', wood: W },
  { kind: 'chair', part: 'chair', pos: [4, 1, 8], facing: 'south', wood: W },
  { kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 9], facing: 'north', wood: W },
  // Hall (first floor, z 2..6): up the stair to the landing (8, 6, 8), north past the rail to (7, 6, 6). The settle on
  // the west wall facing the tea table, a picture over it, the clock on the front wall between the windows, a chair at
  // the table, blinds rolled at every front window's head, a lamp from the attic beams.
  { kind: 'couch', part: 'couch', pos: [2, 6, 4], facing: 'east', length: 2, arms: true, wood: D, colour: 'green' },
  { kind: 'rug', part: 'rug', cellsAbs: [[3, 6, 3], [3, 6, 4], [3, 6, 5], [4, 6, 3], [4, 6, 5]], colour: 'green' },
  { kind: 'table', part: 'table', pos: [4, 6, 4], facing: 'north', wood: D, colour: 'white' },
  { kind: 'chair', part: 'chair', pos: [5, 6, 4], facing: 'west', wood: D },
  { kind: 'picture', part: 'picture', pos: [2, 8, 4], facing: 'east', wood: W, colour: 'green' },
  { kind: 'clock', part: 'clock', pos: [6, 6, 2], facing: 'south', wood: D },
  ...[[2, 8, 2], [3, 8, 2], [5, 8, 2], [7, 8, 2], [8, 8, 2]].map(pos => ({ kind: 'blinds', part: 'blinds', pos, facing: 'south', wood: W })),
  { kind: 'lamp', part: 'hangingLamp', pos: [4, 8, 5], facing: 'north', wood: W },
  // Chamber (z 8..11): the parents' bed head to the back wall, its nightstand and a chest beside it, a lamp; the attic
  // stair's foot at (2, 6, 11) reached along z 10..11, kept clear.
  { kind: 'bed', part: 'bed', pos: [5, 6, 10], facing: 'south', colour: 'red' },
  { kind: 'nightstand', part: 'nightstand', pos: [6, 6, 11], facing: 'north', wood: W },
  { kind: 'fixture', part: 'chest', pos: [4, 6, 11], facing: 'north', block: 'chest' },
  { kind: 'blinds', part: 'blinds', pos: [8, 8, 10], facing: 'west', wood: W, until: 1 },
  { kind: 'lamp', part: 'hangingLamp', pos: [6, 8, 9], facing: 'north', wood: W },
  // Attic: off the stair at (2, 10, 7); the two children's beds heads to the front gable either side, nightstands
  // outside them, a chest between under the picture; the store at the back by the hoist (barrels, a cupboard); lamps
  // on chains from the ridge.
  { kind: 'bed', part: 'bed', pos: [3, 10, 3], facing: 'north', colour: 'light_blue' },
  { kind: 'bed', part: 'bed', pos: [7, 10, 3], facing: 'north', colour: 'yellow' },
  { kind: 'nightstand', part: 'nightstand', pos: [2, 10, 2], facing: 'east', wood: W },
  { kind: 'nightstand', part: 'nightstand', pos: [8, 10, 2], facing: 'west', wood: W },
  { kind: 'fixture', part: 'chest', pos: [5, 10, 2], facing: 'south', block: 'chest' },
  { kind: 'picture', part: 'picture', pos: [5, 11, 2], facing: 'south', wood: W, colour: 'yellow' },
  { kind: 'rug', part: 'rug', cellsAbs: [[4, 10, 3], [5, 10, 3], [6, 10, 3], [4, 10, 4], [5, 10, 4], [6, 10, 4]], colour: 'light_blue' },
  { kind: 'fixture', part: 'pantry', pos: [7, 10, 11], facing: 'north', block: 'barrel' },
  { kind: 'fixture', part: 'pantry', pos: [8, 10, 11], facing: 'north', block: 'barrel' },
  { kind: 'cupboard', part: 'cupboard', pos: [3, 10, 11], facing: 'north', wood: W },
  { kind: 'lamp', part: 'hangingLamp', pos: [5, 13, 4], facing: 'north', chain: 5, wood: W }, // low enough to light the gable's nightstands
  { kind: 'lamp', part: 'hangingLamp', pos: [5, 14, 9], facing: 'north', chain: 4, wood: W },
  // II, Counting house (x 11..15, z 5..11): in by the wing door (13, 1, 5) or from the kitchen (11, 1, 10); the clerks'
  // table of two in the middle with four chairs, ledgers (cupboards) on the east wall, the chest in the back corner,
  // the clock by the door, a picture, a lamp.
  { from: 2, kind: 'table', part: 'table', pos: [13, 1, 8], facing: 'north', wood: W, colour: 'green' },
  { from: 2, kind: 'table', part: 'table', pos: [13, 1, 9], facing: 'north', wood: W, colour: 'green' },
  ...[[12, 8], [12, 9]].map(([x, z]) => ({ from: 2, kind: 'chair', part: 'chair', pos: [x, 1, z], facing: 'east', wood: W })),
  ...[[14, 8], [14, 9]].map(([x, z]) => ({ from: 2, kind: 'chair', part: 'chair', pos: [x, 1, z], facing: 'west', wood: W })),
  { from: 2, kind: 'cupboard', part: 'cupboard', pos: [15, 1, 5], facing: 'west', wood: W },
  { from: 2, kind: 'cupboard', part: 'cupboard', pos: [15, 1, 6], facing: 'west', wood: W },
  { from: 2, kind: 'fixture', part: 'chest', pos: [15, 1, 11], facing: 'west', block: 'chest' },
  { from: 2, kind: 'clock', part: 'clock', pos: [11, 1, 5], facing: 'east', wood: W },
  { from: 2, kind: 'picture', part: 'picture', pos: [11, 3, 8], facing: 'east', wood: W, colour: 'blue' },
  { from: 2, kind: 'lamp', part: 'hangingLamp', pos: [13, 4, 7], facing: 'north', wood: W },
  // II, Wing bedroom (open to the roof): two beds heads to the street gable, nightstands outside them, the window and
  // its blinds between, a chest in the back corner, a rug, a lamp on a long chain from the ridge.
  { from: 2, kind: 'bed', part: 'bed', pos: [12, 6, 6], facing: 'north', colour: 'blue' },
  { from: 2, kind: 'bed', part: 'bed', pos: [14, 6, 6], facing: 'north', colour: 'blue' },
  { from: 2, kind: 'nightstand', part: 'nightstand', pos: [11, 6, 5], facing: 'east', wood: W },
  { from: 2, kind: 'nightstand', part: 'nightstand', pos: [15, 6, 5], facing: 'west', wood: W },
  { from: 2, kind: 'blinds', part: 'blinds', pos: [13, 8, 5], facing: 'south', wood: W },
  { from: 2, kind: 'fixture', part: 'chest', pos: [15, 6, 11], facing: 'west', block: 'chest' },
  { from: 2, kind: 'rug', part: 'rug', cellsAbs: [[12, 6, 8], [13, 6, 8], [14, 6, 8], [12, 6, 9], [13, 6, 9], [14, 6, 9]], colour: 'blue' },
  { from: 2, kind: 'lamp', part: 'hangingLamp', pos: [13, 9, 8], facing: 'north', chain: 6, wood: W }, // lights both nightstands
  // III, Strongroom (tower ground, x 14..16, z 14..16): a chest against the back wall, barrels in the corner, a lamp
  { from: 3, kind: 'fixture', part: 'chest', pos: [14, 1, 16], facing: 'north', block: 'chest' },
  { from: 3, kind: 'fixture', part: 'pantry', pos: [16, 1, 16], facing: 'north', block: 'barrel' },
  { from: 3, kind: 'fixture', part: 'pantry', pos: [16, 1, 15], facing: 'north', block: 'barrel' },
  { from: 3, kind: 'lamp', part: 'hangingLamp', pos: [15, 4, 15], facing: 'north', wood: W },
  // III, the library (y 6..8): ledgers on the west and back walls, an armchair, the ladder up in the south-east corner
  { from: 3, kind: 'cupboard', part: 'cupboard', pos: [14, 6, 16], facing: 'east', wood: W },
  { from: 3, kind: 'cupboard', part: 'cupboard', pos: [14, 6, 15], facing: 'east', wood: W },
  { from: 3, kind: 'lamp', part: 'hangingLamp', pos: [15, 8, 15], facing: 'north', wood: W },
  // III, the tower bedroom (y 10..12): one bed, its nightstand, a lamp
  { from: 3, kind: 'bed', part: 'bed', pos: [15, 10, 14], facing: 'west', colour: 'green' },
  { from: 3, kind: 'nightstand', part: 'nightstand', pos: [14, 10, 15], facing: 'north', wood: W },
  { from: 3, kind: 'lamp', part: 'hangingLamp', pos: [15, 12, 15], facing: 'north', wood: W },
  // III, the lookout (y 14..17): the burgher's desk and chair at the west window, a map (picture), a lamp
  { from: 3, kind: 'desk', part: 'desk', pos: [14, 14, 14], facing: 'west', wood: W },
  { from: 3, kind: 'chair', part: 'chair', pos: [15, 14, 14], facing: 'west', wood: W },
  { from: 3, kind: 'picture', part: 'picture', pos: [14, 15, 16], facing: 'north', wood: W, colour: 'brown' },
  { from: 3, kind: 'lamp', part: 'hangingLamp', pos: [15, 17, 15], facing: 'north', wood: W },
  // IV, Vault: casks along the east wall, a chest and a bench at the back, lamps hung from the ribs
  ...[[8, -3, 4], [8, -2, 4], [8, -3, 5], [8, -3, 6], [8, -2, 6], [8, -3, 7]].map(pos => ({ from: 4, kind: 'fixture', part: 'pantry', pos, facing: 'west', block: 'barrel' })),
  { from: 4, kind: 'fixture', part: 'chest', pos: [3, -3, 11], facing: 'north', block: 'chest' },
  { from: 4, kind: 'fixture', part: 'worktop', pos: [5, -3, 11], facing: 'north' },
  { from: 4, kind: 'lamp', part: 'tableLamp', pos: [6, -3, 11], facing: 'north', wood: W },
  { from: 4, kind: 'shelf', part: 'shelf', pos: [2, -2, 4], facing: 'east', wood: W },
  { from: 4, kind: 'shelf', part: 'shelf', pos: [2, -2, 9], facing: 'east', wood: W },
  { from: 4, kind: 'lamp', part: 'hangingLamp', pos: [5, -2, 5], facing: 'north', wood: W },
  { from: 4, kind: 'lamp', part: 'hangingLamp', pos: [5, -2, 9], facing: 'north', wood: W },
]

// The walking line (I): step -> door -> shop -> kitchen; up the east flight to the landing, the hall and the chamber;
// up the west flight to the attic.
export const WALK = [
  [5, 0, 1], [5, 1, 2], [5, 1, 3], [5, 1, 5], [6, 1, 7], [6, 1, 8], [7, 1, 9], [8, 1, 10], // to the kitchen
  [5, 1, 3], [6, 1, 3], [7, 1, 3], [8, 1, 3], [8, 1, 4], [8, 2, 5], [8, 3, 6], [8, 4, 7], [8, 5, 8], // up
  [7, 5, 7], [7, 5, 6], [6, 5, 5], // to the hall
  [7, 5, 9], [5, 5, 9], [3, 5, 10], [3, 5, 11], [2, 5, 11], [2, 6, 10], [2, 7, 9], [2, 8, 8], [2, 9, 7], [3, 9, 6], [5, 9, 5], // the attic
]

export const OUTSIDE = {
  // flower beds in their own soil either side of the front step, under the jetty's sills
  flowerBeds: [{ x0: 2, x1: 3, z: 0, plants: ['poppy', 'cornflower'] }, { x0: 7, x1: 8, z: 0, plants: ['cornflower', 'poppy'] }],
}
