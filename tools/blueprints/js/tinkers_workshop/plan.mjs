// Tinker's Workshop (tinkers_workshop, tinkers_workshop_2), the plan: drawn before any wall. Rooms, every furniture
// footprint (kind, cells, facing) and the walking line come first; recipe.mjs then assembles both levels from the parts
// library (../parts) to fit this plan. Rebuilt blind for ROADMAP 50.3 (tools/blueprints/REBUILD.md, brief
// tools/blueprints/briefs/tinkers_workshop.md).
//
// Three rough concepts:
//   A. The Forge Gable: one long brick-and-timber shop, gable to the street, a wide open forge bay in that gable and a
//      brick stack up its side. Story: a smith's shed that took on a tinker. Plain to read, but it is every smithy.
//   B. The Rail Shed: a low, long open-fronted shed under one catslide roof, a spur of track running in from the street
//      to a cart stop, benches along the back wall. Story: the tinker who lays the builders' rails. Strong idea, weak
//      silhouette (one slope, no height), and an open front fails the open-panel check.
//   C. The Tinker's Tower: a squat brick tower on the street with a verdigris copper hip cap, standing in front of a
//      low workshop hall that runs across the plot under a long copper roof; the forge stack rises behind the ridge.
//      The door is in the tower's foot, so you walk in under it and out into the hall, to the bench before the forge.
//      Level II lifts the tower a storey, keeps the old cap's eave as a skirt at the floor line and puts a stores loft
//      in it, with a lightning rod on the new top. Story: the village's parts maker, whose tower grows with the village.
// C is the strongest: a hero mass on the street (a tower, not another gable), two contrasting masses and a third (the
// stack), a plan whose walk goes through the tower into the work, and a Level II that grows upward out of Level I and
// adds the storage the Tinkerer's job wants. It is also the least safe (a cap on an 11-high plot, a tower over a hall),
// which the renders have to prove.
//
// Coordinates: x across the front (west 0 -> east 12), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Front door in the tower's foot, a stair step at y = 0 before it.
//
// Masses (named parts the owner's feedback can point at):
//   Tower     brick, 5 x 5 (x 4..8, z 1..5), dark deepslate quoins, a polished deepslate band at y 4 (the floor line),
//             a dark oak band at y 8 under a copper hip cap (7 x 7 eave, rings at y 8, 9, 10). Level II: walls on to
//             y 11, the y 8 eave kept as a skirt, a new cap at y 12..14 and a lightning rod at y 15.
//   Hall      the workshop: brick walls x 1..11, z 5..11 (room x 2..10, z 6..10), dark oak posts standing a block
//             proud of the walls on the plinth and carrying the eaves, a beam ring at y 4, a long copper roof with its
//             ridge along x (eaves to the front and back, y 4; ridge y 8), small gables jettied out over the posts at
//             x 0 and x 12.
//   Stack     the forge chimney: brick up the back wall's middle (x 6, z 12) to y 9, smoke at y 10; the hearth inside
//             is a fire in a niche of the back wall under a brick hood, between two furnaces.
//   Siding    the yard east of the tower: a short spur of rail on dark paving running up to the hall's front post, a
//             lamp at its head: the tinker's rails, read from the street.
//   Woodpile  the forge's fuel, split logs against the tower's west foot.

export const LEVELS = {
  1: { size: [13, 11, 13] }, // limit 13 x 11 x 13
  2: { size: [13, 16, 13] }, // limit 13 x 16 x 14; at least Level I in every axis
}

// Palette (60/30/10): fired brick walls / verdigris copper roofs and dark oak frame / deepslate plinth and quoins,
// iron and copper (lanterns, chain, anvil, rail, lightning rod). Why: brick is what a forge's own kiln makes, and a
// green copper roof over red brick is the look of a works building (no other family in the library has it); the
// darkest stone at the foot and corners holds the tower down; copper is waxed so it stays green and every block
// matches after the build.
export const PALETTE = {
  stone: [['cobbled_deepslate', 6], ['deepslate_bricks', 2], ['cracked_deepslate_bricks', 1]],
  quoin: 'deepslate_bricks', quoinAlt: 'polished_deepslate',
  band: 'polished_deepslate',
  walls: 'bricks', wallStairs: 'brick_stairs',
  frame: 'dark_oak_log',
  trim: 'dark_oak',
  roof: { full: 'waxed_oxidized_cut_copper', stairs: 'waxed_oxidized_cut_copper_stairs', slab: 'waxed_oxidized_cut_copper_slab' },
  hallFloor: 'stone_bricks',
  towerFloor: 'spruce_planks',
  wood: 'spruce', // shelves, counters, sills, shutters
  door: 'dark_oak',
  glass: 'glass_pane',
  paving: 'polished_deepslate',
  fuel: 'spruce_log',
  post: 'dark_oak_fence',
}

export const MASSES = {
  tower: {
    walls: { x0: 4, x1: 8, z0: 1, z1: 5 }, room: { x0: 5, x1: 7, z0: 2, z1: 4 },
    bandY: 4, topY: { 1: 7, 2: 11 }, beamY: 8, // walls y 1..topY; dark oak band at y 8 (Level II: also y 12)
    cap: { cx: 6, cz: 3, half: 3, y: { 1: 8, 2: 12 } }, skirtY: 8, rodY: 15,
  },
  hall: {
    walls: { x0: 1, x1: 11, z0: 5, z1: 11 }, room: { x0: 2, x1: 10, z0: 6, z1: 10 },
    plinth: { x0: 0, x1: 12, z0: 4, z1: 12 },
    wallTop: 3, beamY: 4,
    frontPosts: [1, 3, 9, 11], backPosts: [1, 3, 9, 11], sidePosts: [5, 8, 11], // proud: z 4, z 12, x 0 / x 12
    roof: { axis: 'x', across: [4, 12], along: [0, 12], eaveY: 4, steep: false }, // ridge z 8, y 8
    ties: [4, 8], // tie beams across the room at y 4 (along z), the lamps hang from them
  },
  stack: { x: 6, z: 12, top: 9, breastTop: 3, hearth: [6, 1, 11] },
}

export const ROOMS = [
  { name: 'Lobby', use: "the tower's foot: the way in, the tinker's lanterns on shelves either side", box: [5, 1, 2, 7, 7, 4] },
  { name: 'Workshop', use: 'the forge wall, the smithing table before it, the anvil, the stock chest and the benches', box: [2, 1, 6, 10, 3, 10] },
  { name: 'Lamp room', level: 2, use: "Level II: the tower's top storey, the tinker's bench for fine work in the light of four windows", box: [5, 9, 2, 7, 11, 4] },
  { name: 'Stores loft', level: 2, use: "Level II: the tower's first floor, two double chests of stock up a ladder", box: [5, 5, 2, 7, 7, 4] },
]

export const OPENINGS = {
  door: { at: [6, 1, 1], facing: 'south', step: [6, 0, 0] }, // door facing: the way it faces when shut (into the tower)
  arch: { x0: 5, x1: 7, z: 5, y0: 1, y1: 3 }, // the tower's back opening into the hall
  hood: { x: 6, y: 4, z: 0 }, // a copper hood over the door on the band, its lantern at y 3
  windows: [
    // tower
    { name: 'tower foot west', cells: [[4, 2, 3]], out: 'west', dress: null },
    { name: 'tower foot east', cells: [[8, 2, 3]], out: 'east', dress: null },
    { name: 'tower front', cells: [[6, 5, 1], [6, 6, 1]], out: 'north', dress: 'shutters' },
    { name: 'tower west', cells: [[4, 5, 3], [4, 6, 3]], out: 'west', dress: null },
    { name: 'tower east', cells: [[8, 5, 3], [8, 6, 3]], out: 'east', dress: null },
    // hall
    { name: 'hall front west', cells: [[2, 2, 5], [2, 3, 5]], out: 'north', dress: 'sill' },
    { name: 'hall front east', cells: [[10, 2, 5], [10, 3, 5]], out: 'north', dress: 'sill' },
    { name: 'hall west front bay', cells: [[1, 2, 6], [1, 2, 7], [1, 3, 6], [1, 3, 7]], out: 'west', dress: 'sill' },
    { name: 'hall west back bay', cells: [[1, 2, 9], [1, 2, 10], [1, 3, 9], [1, 3, 10]], out: 'west', dress: 'sill' },
    { name: 'hall east front bay', cells: [[11, 2, 6], [11, 2, 7], [11, 3, 6], [11, 3, 7]], out: 'east', dress: 'sill' },
    { name: 'hall east back bay', cells: [[11, 2, 9], [11, 2, 10], [11, 3, 9], [11, 3, 10]], out: 'east', dress: 'sill' },
    { name: 'hall back west', cells: [[2, 2, 11], [2, 3, 11]], out: 'south', dress: 'sill' },
    { name: 'hall back east', cells: [[10, 2, 11], [10, 3, 11]], out: 'south', dress: 'sill' },
    { name: 'gable west', cells: [[0, 6, 8]], out: 'west', dress: null },
    { name: 'gable east', cells: [[12, 6, 8]], out: 'east', dress: null },
  ],
  windows2: [ // Level II's new storey of the tower
    { name: 'tower top front', cells: [[6, 9, 1], [6, 10, 1]], out: 'north', dress: 'shutters' },
    { name: 'tower top west', cells: [[4, 9, 3], [4, 10, 3]], out: 'west', dress: 'shutters' },
    { name: 'tower top east', cells: [[8, 9, 3], [8, 10, 3]], out: 'east', dress: 'shutters' },
    { name: 'tower top back', cells: [[6, 9, 5], [6, 10, 5]], out: 'south', dress: null },
  ],
  ladder: { x: 7, z: 4, y0: 1, y1: 9, facing: 'west' }, // Level II: up the lobby's east wall through both floors
  loftFloorY: 4, topFloorY: 8, // Level II: the stores loft's floor, and the lamp room's floor on the y 8 band
}

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a furniture spot) or a fixture (a
// vanilla block the game uses as itself). part: what draws it (furnitureParts, or the recipe's own for fixtures).
// facing: the way a person using it faces. level: the first level that has it.
export const FURNITURE = [
  // Workshop, the forge wall (back, z 10..11): a fire in the wall's niche under a brick hood, a furnace either side
  { room: 'Workshop', kind: 'fixture', part: 'hearth', pos: [6, 1, 11], facing: 'north', block: 'campfire' },
  { room: 'Workshop', kind: 'fixture', part: 'furnace', pos: [5, 1, 10], facing: 'north', block: 'furnace' },
  { room: 'Workshop', kind: 'fixture', part: 'furnace', pos: [7, 1, 10], facing: 'north', block: 'furnace' },
  // the job block: the smithing table on the floor before the forge (the tinker stands between it and the fire)
  { room: 'Workshop', kind: 'fixture', part: 'smithing', pos: [6, 1, 8], facing: 'south', block: 'smithing_table' },
  { room: 'Workshop', kind: 'fixture', part: 'anvil', pos: [4, 1, 8], facing: 'east', block: 'anvil' },
  // stock: the Tinkerer's own double chest on the west wall (within 8 across and 4 up or down of the table)
  { room: 'Workshop', kind: 'fixture', part: 'doubleChest', pos: [2, 1, 8], facing: 'east', cells: [[0, 0, 0], [0, 0, 1]], block: 'chest' },
  // benches: the back corners either side of the forge, and the east bench under the windows with its lamp
  { room: 'Workshop', kind: 'counter', part: 'counter', pos: [3, 1, 10], facing: 'south', wood: 'spruce' },
  { room: 'Workshop', kind: 'counter', part: 'counter', pos: [9, 1, 10], facing: 'south', wood: 'spruce' },
  { room: 'Workshop', kind: 'counter', part: 'counter', pos: [10, 1, 7], facing: 'east', wood: 'spruce' },
  { room: 'Workshop', kind: 'counter', part: 'counter', pos: [10, 1, 8], facing: 'east', wood: 'spruce', topSlab: true },
  { room: 'Workshop', kind: 'counter', part: 'counter', pos: [10, 1, 9], facing: 'east', wood: 'spruce' },
  { room: 'Workshop', kind: 'lamp', part: 'lampOn', pos: [10, 3, 8], facing: 'east', wood: 'spruce' },
  // the fitting bench in the open floor between the anvil and the chest wall, its back to the front wall
  { room: 'Workshop', kind: 'counter', part: 'counter', pos: [3, 1, 6], facing: 'north', wood: 'spruce', topSlab: true },
  { room: 'Workshop', kind: 'counter', part: 'counter', pos: [4, 1, 6], facing: 'north', wood: 'spruce', topSlab: true },
  { room: 'Workshop', kind: 'lamp', part: 'candleOn', pos: [3, 3, 6], facing: 'north', wood: 'spruce' },
  { room: 'Workshop', kind: 'shelf', part: 'stockShelf', pos: [2, 2, 10], facing: 'east', wood: 'spruce', on: 'candle' },
  { room: 'Workshop', kind: 'lamp', part: 'hangingLamp', pos: [4, 3, 7], facing: 'north', wood: 'spruce' },
  { room: 'Workshop', kind: 'lamp', part: 'hangingLamp', pos: [8, 3, 7], facing: 'north', wood: 'spruce' },
  // Lobby: the tinker's lanterns on a shelf either side, a lantern hung high in the shaft
  { room: 'Lobby', kind: 'shelf', part: 'stockShelf', pos: [5, 2, 2], facing: 'east', wood: 'spruce', on: 'lantern' },
  { room: 'Lobby', kind: 'shelf', part: 'stockShelf', pos: [7, 2, 2], facing: 'west', wood: 'spruce', on: 'lantern' },
  { room: 'Lobby', kind: 'lamp', part: 'hangingLamp', pos: [6, 7, 3], facing: 'north', wood: 'spruce' },
  // Stores loft (Level II): a double chest on each side wall, the ladder's head between them
  { room: 'Stores loft', level: 2, kind: 'fixture', part: 'doubleChest', pos: [5, 5, 2], facing: 'east', cells: [[0, 0, 0], [0, 0, 1]], block: 'chest' },
  { room: 'Stores loft', level: 2, kind: 'fixture', part: 'doubleChest', pos: [7, 5, 2], facing: 'west', cells: [[0, 0, 0], [0, 0, 1]], block: 'chest' },
  // Lamp room (Level II): the tower's top storey, a lantern-lit bench under the west window, a candle shelf opposite
  { room: 'Lamp room', level: 2, kind: 'counter', part: 'counter', pos: [5, 9, 2], facing: 'west', wood: 'spruce', topSlab: true },
  { room: 'Lamp room', level: 2, kind: 'counter', part: 'counter', pos: [5, 9, 4], facing: 'west', wood: 'spruce', topSlab: true },
  { room: 'Lamp room', level: 2, kind: 'lamp', part: 'lampOn', pos: [5, 11, 2], facing: 'west', wood: 'spruce' },
  { room: 'Lamp room', level: 2, kind: 'shelf', part: 'stockShelf', pos: [7, 10, 2], facing: 'west', wood: 'spruce', on: 'candle' },
]

// The walking line: step -> door -> lobby -> under the arch -> the hall -> the smithing table, the chest and the bench;
// Level II: from the lobby up the ladder to the loft's aisle between its chests.
export const WALK = [
  [6, 0, 0], [6, 1, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [6, 1, 7], [5, 1, 7],
  [5, 1, 8], [5, 1, 9], [6, 1, 9], // the table's working side, before the fire
  [3, 1, 7], [3, 1, 8], [3, 1, 9], // the chest
  [7, 1, 7], [8, 1, 8], [9, 1, 8], // the bench
  [6, 1, 4], [7, 1, 4], [7, 5, 4], [6, 5, 4], [6, 5, 3], [6, 5, 2], [7, 9, 4], [6, 9, 4], [6, 9, 3], // Level II
]

export const OUTSIDE = {
  woodpile: { x: 3, z0: 1, z1: 3 },
  siding: { paving: { x0: 10, x1: 12, z0: 0, z1: 3 }, rail: { x: 11, z0: 0, z1: 3 } },
  lamps: [[12, 1, 0], [0, 1, 3]], // lamp posts: the siding's head, the west corner
  flowerBed: { x0: 1, x1: 2, z: 3, plants: ['red_tulip', 'cornflower'] },
}
