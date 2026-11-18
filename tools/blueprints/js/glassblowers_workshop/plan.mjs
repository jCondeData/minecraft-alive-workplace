// The Glassblower's Workshop (50.7), the plan: drawn before any wall. Rooms, every furniture footprint and the
// walking line come first; recipe.mjs then assembles it from the parts library (../parts) to fit this plan.
//
// Three rough concepts (brief: tools/blueprints/briefs/glassblowers_workshop.md):
//   A. The Cone. Massing: a tall round brick glass cone (9 across, 18 high) over the kiln, a 9-wide timber-framed glass
//      shop in front of it, a low fuel-and-sand lean-to on its flank. Roof: the cone tapers in dressed ledges to a
//      flared lip with smoke; the shop's 45-degree slate gable faces the street and runs into the cone. Plan: street ->
//      shop (cutting bench, his chests) -> round arch in the cone's bulging wall -> the kiln in the middle of the
//      round hot shop. Story: the glass cone of the old glass towns, where the whole cone is the furnace's chimney and
//      the gaffer works in the round under it.
//   B. The Glasshouse. Massing: one long low brick hall with a glazed clerestory lantern down its ridge and a square
//      kiln stack at the back gable. Roof: a gable with a raised glass lantern. Plan: a single hot shop, kiln at the
//      far end, benches down the sides. Story: daylight from above to judge colour by.
//   C. The Kiln Yard. Massing: an L of open timber sheds round a paved yard, the kiln under a hooded brick shelter in
//      the corner. Roof: pent roofs falling outward. Plan: work in the open yard, store in the sheds. Story: a
//      craftsman's yard.
// Strongest: A. Nobody mistakes a glass cone for anything else, from 20 blocks or in outline, from every side; the
// cone *is* the trade (the furnace, its chimney and its workroom in one shape), the shop gable gives the street a
// human scale and a crown-glass bullseye for a sign, and the two masses differ in every way (round vs. square, brick
// vs. timber, 18 vs. 10 high). B is a long box with a hat; C has no interior and nothing tall.
//
// Coordinates: x across the front (west 0 -> east 12), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, the street) to the back. The door is on the z = 0 side, its step at y = 0, z = 0.
//
// Masses (named parts the owner's feedback can point at):
//   Cone      the hero: a hollow brick glass cone centred on (7, 10), radius 4 (x 3..11, z 6..14) on a stone plinth,
//             a dressed stone foot (y 1), narrowing a whole ring every four courses (a brick ledge round it at y 5, 9
//             and 13) to a 3 x 3 neck, a flared lip at y 16 and a rim with the smoking campfire at y 17. Three little
//             windows stand over the y 5 ledge (east, west, south), each under a drip mould; a tall narrow light in
//             the back wall over the marver bench. The Hot Shop is its hollow, stocked with sand, coal and finished glass.
//   Shop      the Glass Shop: a timber-framed front block x 3..11, z 1..8 (walls y 1..3, beam y 4, knee y 5), a
//             45-degree slate gable roof (ridge along z, y 10) from the front overhang (z 0) into the cone's flank. Its
//             front gable carries the bullseye; two shop windows with coloured transoms flank a hooded door.
//   Shed      the Fuel Shed: a slate lean-to on the cone's west flank (x 0..2, z 8..12), open to the west, with sand and
//             split wood; a door from it into the Hot Shop.
//
// Palette (60/30/10): brick (the cone, 60) / dark oak frame + cream terracotta panels + deepslate tiles (the shop,
// 30) / glass: plain panes with light-blue and yellow stained accents, a dressed stone foot and plinth (10). Why:
// fired clay for the hot work, a pale timbered shop for the clean work, dark slate roofs that contrast with both; the
// stone darkest at the foot and in the ledges that ring the cone.

export const SIZE = [13, 18, 15] // w, h, d (footprint 13 x 15, within the brief's ~15 x 15)

export const PALETTE = {
  stone: [['stone_bricks', 6], ['cracked_stone_bricks', 2], ['andesite', 1], ['mossy_stone_bricks', 1]],
  cone: { plinth: 'stone_bricks', base: 'stone_bricks', body: 'bricks', ledge: 'brick_stairs', lipStairs: 'brick_stairs', wall: 'brick_wall' },
  brickSlab: 'brick_slab',
  frame: 'dark_oak_log',
  infill: 'white_terracotta',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  trim: 'dark_oak',
  wood: 'dark_oak', // doors, trapdoors
  joinery: 'spruce', // counters, shelves, the shed's boards
  shopFloor: 'spruce_planks',
  hotFloor: 'polished_andesite',
  glass: 'glass_pane', // the one glass type: panes; stained panes for the trade's colour
  tintA: 'light_blue_stained_glass_pane',
  tintB: 'yellow_stained_glass_pane',
  sand: 'sand',
  log: 'spruce_log',
}

export const MASSES = {
  cone: { cx: 7, cz: 10, baseTop: 1,
    // each course's outline is the disc dx^2 + dz^2 <= t: three even corbel courses, one whole ring in every four
    // courses (17: a round 9 to y 4; 10: a round 7 to y 8; 5: a round 5 to y 12; 2: the 3 x 3 neck to y 15), so every
    // ledge runs right round the cone at one height (y 5, 9 and 13) and reads as a dressed band from every side (the
    // critic's note: half-steps left ragged fins of stairs up the back)
    stages: [{ t: 17, top: 4 }, { t: 10, top: 8 }, { t: 5, top: 12 }, { t: 2, top: 15 }] },
  shop: {
    walls: { x0: 3, x1: 11, z0: 1, z1: 8 },
    wallTop: 3, beamY: 4, kneeY: 5,
    frontPosts: [3, 6, 8, 11], sidePosts: [1, 5, 8],
    roof: { axis: 'z', across: [2, 12], along: [0, 8], eaveY: 5, steep: false },
  },
  shed: { x0: 0, x1: 2, z0: 8, z1: 12, roofYs: [3, 4, 5] }, // the lean-to's stair at x 0, 1, 2
}

export const ROOMS = [
  { name: 'Glass Shop', use: 'cutting and storing finished glass, the Glassblower\'s chests', box: [4, 1, 2, 10, 3, 6] },
  { name: 'Hot Shop', use: 'the Glass Kiln in the round, the marver bench', box: [4, 1, 7, 10, 4, 13] },
  { name: 'Fuel Shed', use: 'sand and firewood by the kiln', box: [0, 1, 9, 2, 2, 11] },
]

export const OPENINGS = {
  frontDoor: { at: [7, 1, 1], facing: 'south', step: [7, 0, 0] },
  coneArch: { at: [7, 1, 6], height: 2 }, // the shop's way into the Hot Shop through the cone's bulge
  shedDoor: { at: [3, 1, 10], facing: 'east' }, // from the Fuel Shed into the Hot Shop
  // the shop's windows: glass cells, the side they look out of, the dressing
  shopWindows: [
    { name: 'shop west', cells: [[4, 2, 1], [5, 2, 1]], transom: [[4, 3, 1], [5, 3, 1]], out: 'north' },
    { name: 'shop east', cells: [[9, 2, 1], [10, 2, 1]], transom: [[9, 3, 1], [10, 3, 1]], out: 'north' },
  ],
  sideWindows: [
    { name: 'shop side west', cells: [[3, 2, 3], [3, 3, 3]], out: 'west', pot: 'potted_cornflower' },
    { name: 'shop side east', cells: [[11, 2, 3], [11, 3, 3]], out: 'east', pot: 'potted_cornflower' },
  ],
  // the cone's three little windows stand over the y 5 ledge (their sill), each under a drip mould at y 7; a tall
  // narrow light in the foot's back wall over the marver bench (the critic's note: the back wall was blind)
  coneWindows: [
    { cells: [[10, 6, 10]], out: 'east', brow: [10, 7, 10] },
    { cells: [[4, 6, 10]], out: 'west', brow: [4, 7, 10] },
    { cells: [[7, 6, 13]], out: 'south', brow: [7, 7, 13] },
  ],
  benchLight: [[7, 2, 14], [7, 3, 14]],
  bullseye: { line: 1, u: 7, y: 7 }, // the front gable's crown-glass window
}

export const JOB = { block: 'aliveworkplace:glass_kiln', at: [7, 1, 10], facing: 'north', stand: [7, 1, 9] }

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a spot) or a fixture.
export const FURNITURE = [
  // Glass Shop: a cutting table and its chair in the west shop window's light, his chests on the west wall, the cutting bench on the east wall with a lamp and a shelf of vases over
  // it, a cloth on the wall, a lamp hung from the tie beam over the walk
  { room: 'Glass Shop', kind: 'fixture', part: 'doubleChest', pos: [4, 1, 5], facing: 'east', along: 'south' },
  { room: 'Glass Shop', kind: 'counter', part: 'counter', pos: [10, 1, 3], facing: 'east', wood: 'spruce', cells: [[0, 0, 0], [0, 1, 0]] },
  { room: 'Glass Shop', kind: 'counter', part: 'counter', pos: [10, 1, 4], facing: 'east', wood: 'spruce', topSlab: true, cells: [[0, 0, 0], [0, 1, 0]] },
  { room: 'Glass Shop', kind: 'counter', part: 'counter', pos: [10, 1, 5], facing: 'east', wood: 'spruce', cells: [[0, 0, 0], [0, 1, 0]] },
  { room: 'Glass Shop', kind: 'lamp', part: 'lampOn', pos: [10, 3, 4], facing: 'east', wood: 'spruce' },
  { room: 'Glass Shop', kind: 'shelf', part: 'shelf', pos: [10, 3, 5], facing: 'west', wood: 'spruce', crockery: 'flower_pot' },
  { room: 'Glass Shop', kind: 'picture', part: 'picture', pos: [4, 3, 4], facing: 'east', wood: 'spruce', colour: 'light_blue' },
  { room: 'Glass Shop', kind: 'table', part: 'table', pos: [5, 1, 3], facing: 'north', wood: 'spruce', colour: 'light_gray', cells: [[0, 0, 0], [0, 1, 0]] },
  { room: 'Glass Shop', kind: 'chair', part: 'chair', pos: [5, 1, 2], facing: 'south', wood: 'spruce' },
  { room: 'Glass Shop', kind: 'lamp', part: 'hangingLamp', pos: [7, 3, 4], facing: 'north', wood: 'spruce' },
  // Hot Shop: the kiln in its brick siege in the middle, facing the arch; the marver bench round the back wall; a
  // chest of sand by the kiln; four lamps hung from the corbelled shell where it first leans in over the room (y 5)
  { room: 'Hot Shop', kind: 'fixture', part: 'job', pos: JOB.at, facing: JOB.facing, block: JOB.block },
  { room: 'Hot Shop', kind: 'counter', part: 'counter', pos: [6, 1, 13], facing: 'south', wood: 'spruce', cells: [[0, 0, 0], [0, 1, 0]] },
  { room: 'Hot Shop', kind: 'counter', part: 'counter', pos: [7, 1, 13], facing: 'south', wood: 'spruce', cells: [[0, 0, 0], [0, 1, 0]] },
  { room: 'Hot Shop', kind: 'counter', part: 'counter', pos: [8, 1, 13], facing: 'south', wood: 'spruce', cells: [[0, 0, 0], [0, 1, 0]] },
  { room: 'Hot Shop', kind: 'fixture', part: 'chest', pos: [10, 1, 11], facing: 'west', block: 'chest' },
  { room: 'Hot Shop', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 8], facing: 'north', chain: 0, wood: 'spruce' },
  { room: 'Hot Shop', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 8], facing: 'north', chain: 0, wood: 'spruce' },
  { room: 'Hot Shop', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 12], facing: 'south', chain: 0, wood: 'spruce' },
  { room: 'Hot Shop', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 12], facing: 'south', chain: 0, wood: 'spruce' },
]

// The walking line: the step -> the shop door -> down the shop -> the arch in the cone -> the kiln's front (where he
// stands); round the kiln to the sand chest and the bench, and out through the Fuel Shed.
export const WALK = [
  [7, 0, 0], [7, 1, 1], [7, 1, 2], [7, 1, 3], [7, 1, 4], [7, 1, 5], [7, 1, 6], [7, 1, 7], [7, 1, 8], [7, 1, 9],
  [8, 1, 9], [9, 1, 10], [9, 1, 11], [9, 1, 12], [8, 1, 12], [7, 1, 12], [6, 1, 12], [5, 1, 11], [5, 1, 10], [4, 1, 10],
  [3, 1, 10], [2, 1, 10], [1, 1, 10], [0, 1, 10],
]

// The Hot Shop's stock (the critic's note: the kiln room was bare): sand heaped either side of the shed door, a block
// of coal and the finished glass stacked by the sand chest, all off the walking line.
export const HOT_STOCK = [
  [[4, 1, 9], 'sand'], [[4, 1, 11], 'sand'], [[10, 1, 9], 'coal_block'], [[10, 1, 10], 'glass'], [[10, 2, 10], 'glass'],
]

// Outside the rooms: the shed's sand and wood, sills with pots under the side windows, the shop's stall boards.
export const OUTSIDE = {
  sand: [[1, 1, 9], [2, 1, 9], [2, 2, 9]],
  wood: [[1, 1, 11], [2, 1, 11], [2, 2, 11], [2, 3, 9], [2, 3, 10], [2, 3, 11], [2, 4, 9], [2, 4, 10], [2, 4, 11]], // stacked to the rafters
  shedLamp: [1, 3, 10], // hung from the lean-to's rafter by the side door
}
