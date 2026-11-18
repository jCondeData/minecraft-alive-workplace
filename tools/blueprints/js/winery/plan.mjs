// The Winery (winery, winery_2, winery_3; ROADMAP 49, brief tools/blueprints/briefs/winery.md), the plan: drawn before
// any wall. recipe.mjs assembles it from the parts library (../parts) for LEVEL 1, 2 or 3.
//
// Design notes. Three rough concepts first:
//   A. "The Perron Press House": a gravity-flow winery. A stone cask hall at the ground, a white timber-framed press
//      floor over it, one 45-degree gable roof (ridge along z) with its gable to the street, a fermentation louvre on
//      the ridge; a double stone perron climbs the front to a landing and the press-floor door, the cellar door in the
//      passage under the landing; a low stone lean-to tasting room on the east. Plan: the press floor is a gallery
//      over the front third of the hall, the vat on the door's axis, a rail looking down on the cask racks.
//      Story: fruit goes up the steps, the must comes down to the casks: the building is the process.
//   B. "The Cave Bodega": a long, low stone vault (ridge along x) half hidden under a turf-and-vine roof, a little vat
//      tower over its door. Plan: one tunnel of casks, the vat up the tower's stair. Story: a hillside cellar dug into
//      the slope. It needs a slope our flat plots don't have: on flat ground it reads as a shed with a tower.
//   C. "The Vat Tower": a round stone tower (the vat on its top floor under a conical roof) with casks ringing its foot
//      in a lean-to. Plan: a winding stair up the tower. Story: a lookout over the vines. Striking, but a round tower
//      wastes the plot, the stair eats the floor, and a tower says "watch", not "wine".
//   A is strongest: its silhouette (a perron of two flights meeting under a hooded door on the gable's axis) is a
//   shape no other build in the library has, and it is true to how wineries were built (crush on top, casks below,
//   gravity between); the brief's "vat three up, chests and casks two below" becomes the building's whole idea, not a
//   cellar bolted under a shop. Level II plants its vineyard behind it (trellised rows of sweet berries, an arbour of
//   glow berries leading from the back door), Level III digs the vintage vault under the hall. Built: A.
//
// Coordinates: x across the front (west 0 -> east 16), y up (0 = the layer on the clicked block; III's vault is
// y -4..-1), z from the front (z = 0, north) to the back.
//
// Masses (named parts the owner's feedback can point at):
//   Press House  the main mass: stone walls x 2..12, z 2..12, y 0..3 (the Cask Hall), a white timber-framed storey
//                y 4..7 over it (dark oak posts on the stone's edge, plaster set a block back on the long sides, flush
//                on the gables), a 45-degree granite-tile roof, ridge along z at x 7, y 13, overhanging x 1..13, z 1..13.
//   Perron       the double flight of stone steps across the front (x 2..5 rising east, x 9..12 rising west, z 0..1)
//                to a landing at y 3 (x 6..8) carried on two piers; under it the passage to the Cask Hall's door.
//   Door hood    a little tiled hood over the press-floor door, the gable window sitting on it, a lantern post on
//                the landing.
//   Louvre       the fermentation vent on the ridge (x 6..8, z 6..8), a lantern inside, a lightning rod on top.
//   Tasting Room the stone lean-to on the east side (walls x 12..15, z 5..9), its roof falling east from y 6.
//   Vineyard     (Level II) behind the house, z 13..21: four trellised rows of sweet berries (x 2, 4, 10, 12) either
//                side of the Arbour, a dark oak pergola down the middle (x 6..8) hung with glow berries.
//   Vintage Vault (Level III) under the Cask Hall: stone walls x 3..11, z 3..11, floor y -4, down a ladder from a hatch.

export const SIZES = { 1: [17, 17, 15], 2: [17, 17, 22], 3: [17, 17, 22] } // w, h above ground, d (<= 18 x 18 x 22)
export const BASEMENT = { 1: 0, 2: 0, 3: 4 }

// Palette (60/30/10): grey rubble stone (the Cask Hall, the perron, the lean-to, the vault) / white plaster in a dark
// oak frame and a rose granite tile roof / mangrove (doors, shutters, chairs: the colour of the wine) and the spruce
// casks. Why: the cool stone storey is where wine keeps, the light storey over it where the fruit is worked, the warm
// tiles say "south-facing slope"; the dark frame ties roof to stone, and the wine-red wood marks every door.
export const PALETTE = {
  stone: [['cobblestone', 5], ['stone', 3], ['andesite', 2], ['mossy_cobblestone', 1]],
  plinth: [['cobblestone', 3], ['mossy_cobblestone', 2], ['andesite', 1]],
  dressed: 'stone_bricks', // quoins, the landing, the string course
  dressedAlt: 'polished_andesite',
  step: 'stone_brick_stairs',
  frame: 'dark_oak_log',
  infill: [['white_concrete', 5], ['polished_diorite', 1]], // plaster
  roof: { full: 'polished_granite', stairs: 'polished_granite_stairs', slab: 'polished_granite_slab' },
  trim: 'dark_oak',
  hallFloor: 'polished_andesite',
  floor: 'spruce_planks', // the press floor
  wood: 'mangrove', // doors, shutters, chairs, rails
  cask: 'spruce_log', // casks: spruce logs on their sides (a test counts them; a barrel is a job block)
  glass: 'glass_pane',
  bars: 'iron_bars',
  vat: 'cauldron', // the one job block: a plain empty cauldron
  soil: 'coarse_dirt',
  vineyardPost: 'dark_oak_fence',
  arbourPost: 'spruce_fence',
  arbourRail: 'stripped_spruce_log',
  kerb: 'stone_bricks',
  cloth: 'red', // table cloths: the mangrove's wine red
  vaultWall: [['stone_bricks', 5], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1]],
  vaultFloor: 'cobbled_deepslate',
}

export const MASSES = {
  house: { x0: 2, x1: 12, z0: 2, z1: 12, stoneTop: 3, beamY: 7, eaveY: 7 },
  // posts of the timber storey: front and back gables (x), long sides (z)
  posts: { gable: [2, 6, 8, 12], side: [2, 5, 9, 12] },
  roof: { axis: 'z', across: [1, 13], along: [0, 14], eaveY: 7, steep: false }, // ridge y 13, over both jettied gables
  perron: { x0: 2, x1: 12, lx0: 6, lx1: 8, z0: 0, z1: 1, top: 3 },
  gallery: { x0: 3, x1: 11, z0: 3, z1: 6, y: 3 }, // the press floor (its floor layer), open to the hall behind z 6
  louvre: { cx: 7, cz: 7, y: 13 },
  // the Tasting Room: a lean-to at the main roof's pitch, from y 5 under the main eave down to its eave at x 16, y 2
  leanTo: { x0: 12, x1: 15, z0: 5, z1: 9, roof: { axis: 'z', across: [12, 16], ridge: 12, along: [4, 10], ridgeY: 6 } },
  vineyard: { rows: [2, 4, 10, 12], z0: 14, z1: 19 },
  arbour: { x0: 6, x1: 8, z0: 14, z1: 21, posts: [14, 17, 20], beamY: 4 }, // fence posts, pale log rails: a trellis
  bed: { x0: 1, x1: 5, z0: 13, z1: 20 }, // the Vineyard: a raised bed (inside this box) west of the Arbour, three rows of six bushes
  vault: { x0: 3, x1: 11, z0: 3, z1: 11, depth: 4 },
}

// The job block and the storage the tests count (the vat at y 4: casks and chests at y <= 2 count as "below").
export const VAT = [7, 4, 4] // the press floor's vat, on the door's axis (where the luxury_workshops scene looks)

export const OPENINGS = {
  hallDoor: { at: [7, 1, 2], facing: 'south', out: 'north', step: [7, 0, 1] }, // under the landing
  pressDoor: { at: [7, 4, 2], facing: 'south', out: 'north' }, // off the landing
  backDoor: { at: [7, 1, 12], facing: 'north', out: 'south', step: [7, 0, 13] }, // to the yard (II: the Arbour)
  tastingDoorway: { at: [12, 1, 7], height: 2 },
  // the stair from the Cask Hall up to the press floor, along the gallery's edge, rising west
  hallStair: [[10, 1, 7], [9, 2, 7], [8, 3, 7]],
  galleryRail: [[4, 4, 6], [5, 4, 6], [6, 4, 6], [7, 4, 6], [9, 4, 6], [10, 4, 6]], // a gap at x 8 for the stair
  windows: [
    // the press floor's gables: tall single lights, no dressing (the perron runs under them)
    // the gables' storey, set back behind the posts (front) or on the jetty (back): sills on the stone or the brackets
    { name: 'front west', cells: [[4, 5, 3], [4, 6, 3]], out: 'north', dress: 'sill' },
    { name: 'front east', cells: [[10, 5, 3], [10, 6, 3]], out: 'north', dress: 'sill' },
    { name: 'gable', cells: [[7, 9, 1], [7, 10, 1]], out: 'north', dress: null }, // in the jettied gable
    { name: 'back west', cells: [[4, 5, 12], [4, 6, 12]], out: 'south', dress: 'sill' },
    { name: 'back middle', cells: [[7, 5, 12], [7, 6, 12]], out: 'south', dress: 'sill' },
    { name: 'back east', cells: [[10, 5, 12], [10, 6, 12]], out: 'south', dress: 'sill' },
    { name: 'back gable', cells: [[7, 9, 13], [7, 10, 13]], out: 'south', dress: null },
    // the long sides, set back between the posts: sills with pots on the stone's top
    { name: 'west front', cells: [[3, 5, 3], [3, 5, 4], [3, 6, 3], [3, 6, 4]], out: 'west', dress: 'sill' },
    { name: 'west middle', cells: [[3, 5, 7], [3, 6, 7]], out: 'west', dress: 'sill' },
    { name: 'west back', cells: [[3, 5, 10], [3, 5, 11], [3, 6, 10], [3, 6, 11]], out: 'west', dress: 'sill' },
    { name: 'east front', cells: [[11, 5, 3], [11, 5, 4], [11, 6, 3], [11, 6, 4]], out: 'east', dress: 'sill' },
    { name: 'east back', cells: [[11, 5, 10], [11, 5, 11], [11, 6, 10], [11, 6, 11]], out: 'east', dress: 'sill' },
    { name: 'tasting', cells: [[14, 2, 5]], out: 'north', dress: 'sill' },
  ],
  // cellar vents: iron bars in the stone, under the press floor
  vents: [[2, 2, 4], [12, 2, 4], [12, 2, 10]],
  hatch: { x: 4, z: 8, facing: 'east' }, // III: the vault's hatch in the Cask Hall's floor, the ladder against x 3
}

// Storage. Every chest stays in its cell in every level. Casks lie on their sides (axis x on the side walls, z on the
// back), heads to the aisle.
// the press floor's fruit: a straw bale (packing for bottles) and a melon waiting for the vat, beside the bottle chest
export const FRUIT = [[4, 4, 5, 'hay_block', { axis: 'y' }], [4, 5, 5, 'melon', {}]]

export const STORE = {
  chests: [ // [x, y, z, facing, along] double chests (tinker's doubleChest)
    [3, 1, 3, 'south', 'east'], [10, 1, 3, 'south', 'east'], // under the press floor, against the front wall
    [4, 4, 4, 'south', 'east'], // on the press floor beside the vat: the bottles
  ],
  racks: [
    { x0: 3, x1: 3, z0: 7, z1: 11, y0: 1, y1: 2, axis: 'x' }, // west wall
    { x0: 4, x1: 6, z0: 11, z1: 11, y0: 1, y1: 1, axis: 'z' }, // back wall, west of the door
    { x0: 8, x1: 10, z0: 11, z1: 11, y0: 1, y1: 1, axis: 'z' }, // back wall, east of the door
  ],
  lampCasks: [[3, 1, 6], [11, 1, 6]], // single casks under the press floor's corners, a lantern hung over each
  vaultRacks: [ // III
    { x0: 4, x1: 4, z0: 4, z1: 6, y0: -3, y1: -2, axis: 'x' },
    { x0: 10, x1: 10, z0: 4, z1: 10, y0: -3, y1: -2, axis: 'x' },
    { x0: 5, x1: 9, z0: 10, z1: 10, y0: -3, y1: -1, axis: 'z', taper: true },
  ],
}

// Every piece of furniture, footprint first (kind: a furniture.py kind recorded as a spot; part: the furnitureParts
// part that draws it; facing: the way its user faces).
export const FURNITURE = [
  // Press floor: the vat (a fixture, VAT) in the middle; the bottle chest on the west and straw bales with fruit on them
  // beside it; the work counter down the east wall under shelves of bottles; lamps over the rail
  // a lamp over the rail lighting the floor and the casks below
  { room: 'Press floor', kind: 'counter', part: 'counter', pos: [10, 4, 4], facing: 'east', topSlab: true, wood: 'spruce' },
  { room: 'Press floor', kind: 'counter', part: 'counter', pos: [10, 4, 5], facing: 'east', topSlab: true, wood: 'spruce' },
  { room: 'Press floor', kind: 'crockery', part: 'plates', pos: [10, 6, 4], facing: 'east', wood: 'spruce', block: 'flower_pot' },
  { room: 'Press floor', kind: 'shelf', part: 'shelf', pos: [10, 6, 5], facing: 'west', wood: 'spruce', crockery: 'flower_pot' },
  { room: 'Press floor', kind: 'lamp', part: 'hangingLamp', pos: [7, 5, 6], facing: 'north', chain: 1, wood: 'dark_oak' },
  { room: 'Press floor', kind: 'lamp', part: 'hangingLamp', pos: [10, 6, 6], facing: 'north', chain: 0, wood: 'dark_oak' },
  // Cask Hall: two lamps down the aisle from the tie beam, two over the corner casks under the press floor
  { room: 'Cask Hall', kind: 'lamp', part: 'hangingLamp', pos: [3, 2, 6], facing: 'north', chain: 0, wood: 'dark_oak' },
  { room: 'Cask Hall', kind: 'lamp', part: 'hangingLamp', pos: [11, 2, 6], facing: 'north', chain: 0, wood: 'dark_oak' },
  // Tasting Room: a table for two under its lamp
  { room: 'Tasting Room', kind: 'table', part: 'table', pos: [14, 1, 7], facing: 'north', wood: 'dark_oak', colour: 'red' },
  { room: 'Tasting Room', kind: 'chair', part: 'chair', pos: [14, 1, 6], facing: 'south', wood: 'mangrove' },
  { room: 'Tasting Room', kind: 'chair', part: 'chair', pos: [14, 1, 8], facing: 'north', wood: 'mangrove' },
  { room: 'Tasting Room', kind: 'lamp', part: 'hangingLamp', pos: [14, 3, 7], facing: 'north', chain: 0, wood: 'dark_oak' },
  // Vintage Vault (III): a tasting table among the old casks, two lamps from the hall's floor
  { room: 'Vintage Vault', level: 3, kind: 'table', part: 'table', pos: [7, -3, 6], facing: 'north', wood: 'dark_oak', colour: 'red' },
  { room: 'Vintage Vault', level: 3, kind: 'chair', part: 'chair', pos: [6, -3, 6], facing: 'east', wood: 'mangrove' },
  { room: 'Vintage Vault', level: 3, kind: 'chair', part: 'chair', pos: [8, -3, 6], facing: 'west', wood: 'mangrove' },
  { room: 'Vintage Vault', level: 3, kind: 'lamp', part: 'hangingLamp', pos: [7, -1, 5], facing: 'north', wood: 'dark_oak' },
  { room: 'Vintage Vault', level: 3, kind: 'lamp', part: 'hangingLamp', pos: [7, -1, 9], facing: 'north', wood: 'dark_oak' },
]

// The walking line: the street -> the west flight -> the landing -> the press-floor door -> round the vat; the passage
// under the landing -> the Cask Hall -> up the hall stair; the tasting doorway; the back door (II: down the Arbour);
// III: the hatch and the ladder to the vault floor.
export const WALK = [
  [1, 0, -3], [2, 0, 0], [3, 1, 0], [4, 2, 0], [5, 3, 0], [6, 3, 1], [7, 4, 1], [7, 4, 2], [7, 4, 3], [6, 4, 4],
  [7, 0, 0], [7, 0, 1], [7, 1, 2], [7, 1, 3], [7, 1, 7], [11, 1, 7], [10, 1, 7], [9, 2, 7], [8, 3, 7], [8, 4, 6],
  [12, 1, 7], [13, 1, 7], [7, 1, 11], [7, 1, 12], [7, 0, 13], [7, 0, 21], [4, 1, 8], [4, -3, 8],
]
