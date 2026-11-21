// The Tailor's Shop (tailors_shop, tailors_shop_2; ROADMAP 49), the plan: drawn before any wall. Rooms, every furniture
// footprint (kind, cells, facing) and the walking line come first; recipe.mjs then assembles both levels from the parts
// library (../parts) to fit it. Requirement: tools/blueprints/briefs/tailors_shop.md (designed blind, REBUILD.md).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. "The Draper's House": a two-storey jettied townhouse, gable to the street, shop below and the tailor's rooms
//      above up a stair; II adds a back wing. Story: the cloth merchant lives over his counter. Safe, and it is every
//      timber townhouse in the library: nothing in its outline says cloth.
//   B. "The North-Light Shed": a long low weaving shed under a saw-tooth roof of three ridges, the steep glazed faces to
//      the north, the loom under the light; II adds a fourth tooth and a loft. Story: a cloth works, not a shop. A
//      striking roof, but a low, flat silhouette, no street face for a shop, and a ladder has nowhere natural to go.
//   C. "The Dyer's Tower": a plain white shop with a jettied gable over a glazed shopfront (bolts of cloth on show
//      behind the glass), the loom and sewing table inside, a pressing stove's stack at the back; II raises a slender
//      drying tower at its back corner, its loft hung with dyed cloths in a spectrum, reached by a ladder through two
//      floors. Story: the tailor dyes and dries his own cloth; the house is a neutral stage and the colour is his trade.
// Strongest: C. The tower gives the family a hero silhouette (its finial 60% over the shop's ridge) that reads from 20
// blocks as "the cloth tower"; the coloured cloth is the brief's wool turned into the building's identity instead of
// blocks hidden in a chest; and Level II is one clear addition growing out of Level I's corner, which is exactly what
// the ladder (9 rungs, II only) and the 12 more wool need. A and B are kept as notes.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front (z = 0,
// north) to the back. The front door is on the north side; the arriving Tailor comes from (1, 0, -3).
//
// Masses (named parts the owner's feedback can point at):
//   Shop        the main mass: walls x 1..9, z 2..10 (posts flush, white plaster panels), storey y 1..4, beam ring y 5,
//               a 45-degree deepslate-tile roof, ridge along z at y 10, overhanging x 0..10 and z 0..11.
//   Jetty       the front gable stands a block out (z 1) on a beam and four carved brackets over a recessed, paved
//               shopfront: two three-high display windows flanking the door, a fanlight, livery banners on the corners.
//   Stack       the pressing stove's stone chimney on the back wall (x 3, z 11), a stepped breast at its foot.
//   Tower (II)  the drying tower astride the back-east corner: x 9..13, z 7..11 (its west wall is the shop's east wall,
//               one bay of it standing out past the back): a stone foot y 0..4 with dark oak corner posts and a pent
//               skirt of tiles at the beam line, a drying shaft open from the foot up to y 8 whose glazed band (y 7..8)
//               shows the cloths hung inside, the cutting room y 10..11 (floor y 9), a steep cap y 12..15 sitting on
//               the wall line (no eave over the shop's roof) and a lightning-rod finial at y 16. Where the shop's east
//               slope meets the tower, a cricket course of tiles rising to the wall makes a valley (the joint).

export const SIZES = { 1: [12, 13, 12], 2: [15, 17, 13] } // w, h, d (plot limit 18 x 18 x 22)

// Palette (60/30/10): white plaster (walls) / dark oak frame + deepslate-tile roof and cobbled-deepslate base / the
// cloth. Why: a cool, neutral, well-kept house (black and white, a tailor's own colours) so the only colour on it is
// the dyed wool, in the windows and on the tower; the dark roof and base bookend the white walls, the dark frame draws
// the bays. Spruce inside for warm floors and joinery.
export const PALETTE = {
  stone: [['cobbled_deepslate', 4], ['polished_deepslate', 1]],
  frame: 'dark_oak_log',
  infill: 'white_concrete',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'spruce_planks',
  wood: 'dark_oak', // doors, shutters, brackets, barge boards, the shopfront's stall boards
  joinery: 'spruce', // tables, chairs, the couch, slab tops, the tower's louvre slats and ladders' floors
  paving: 'polished_deepslate_slab',
  glass: 'glass_pane',
  banner: 'purple', // the tailor's livery
  finial: 'lightning_rod',
}

export const SHOP = {
  walls: { x0: 1, x1: 9, z0: 2, z1: 10 },
  room: { x0: 2, x1: 8, z0: 3, z1: 9 },
  wallTop: 4, beamY: 5,
  frontPosts: [1, 4, 6, 9], backPosts: [1, 4, 6, 9], sidePosts: [2, 6, 10],
  eastStud: 8, // a stud in the east wall's back bay (in II the tower doorway is cut under it)
  tieBeams: [6], // across the room at y 5 over the middle side posts: the lamps hang from it
  roof: { axis: 'z', across: [0, 10], along: [0, 11], eaveY: 5, steep: false },
  jetty: { z: 1, y: 5, brackets: [1, 4, 6, 9] },
}

export const TOWER = {
  walls: { x0: 9, x1: 13, z0: 7, z1: 11 },
  room: { x0: 10, x1: 12, z0: 8, z1: 10 },
  baseTop: 4, // stone to y 4
  floors: [9], // the cutting room's floor; below it the foot and the drying shaft are one tall space
  beams: [5, 9],
  cap: { cx: 11, cz: 9, half: 2, y0: 12 }, // on the cutting room's walls (y 10..11)
  // a pent skirt of tiles round the stone foot at the beam line (y 5), on the three sides clear of the shop
  skirt: {
    north: [[11, 6], [12, 6], [13, 6], [14, 6]],
    east: [[14, 7], [14, 8], [14, 9], [14, 10], [14, 11]],
    south: [[9, 12], [10, 12], [11, 12], [12, 12], [13, 12], [14, 12]],
  },
  ladder: { x: 11, z: 10, y0: 1, y1: 9, facing: 'north' }, // 9 rungs up the shaft's south wall to the cutting room's hatch
  doorway: [9, 1, 8], // from the shop's back-east corner
}

export const ROOMS = [
  { name: 'Shop', use: 'the window display, the loom, the sewing table, the fitting bench, the pressing stove and the bolt rack', box: [2, 1, 3, 8, 4, 9] },
  { name: 'Drying shaft (II)', use: 'the tower foot, open to the cutting room floor: a chest of dyes, bolts waiting, the ladder, and dyed cloths hung from the joists behind the glazed band', box: [10, 1, 8, 12, 8, 10] },
  { name: 'Cutting room (II)', use: 'stock shelves of bolts in the four corners, a cutting bench under the west window and a stool at it, the hatch', box: [10, 10, 8, 12, 12, 10] },
]

export const OPENINGS = {
  frontDoor: { at: [5, 1, 2], facing: 'south', fanlight: [5, 3, 2] },
  // the shopfront's display windows: stall board at y 1, glass y 2..4
  shopWindows: [{ x0: 2, x1: 3 }, { x0: 7, x1: 8 }],
  windows: [
    { name: 'west front', cells: [[1, 2, 3], [1, 2, 4], [1, 2, 5], [1, 3, 3], [1, 3, 4], [1, 3, 5]], out: 'west', dress: 'shutters' },
    { name: 'west back', cells: [[1, 2, 7], [1, 2, 8], [1, 2, 9], [1, 3, 7], [1, 3, 8], [1, 3, 9]], out: 'west', dress: 'shutters' },
    { name: 'east front', cells: [[9, 2, 3], [9, 2, 4], [9, 2, 5], [9, 3, 3], [9, 3, 4], [9, 3, 5]], out: 'east', dress: 'shutters' },
    { name: 'back', cells: [[5, 2, 10], [5, 3, 10]], out: 'south', dress: 'shutters' },
    { name: 'front gable', cells: [[5, 7, 1], [5, 8, 1]], out: 'north', dress: null },
    { name: 'back gable', cells: [[5, 7, 10]], out: 'south', dress: null },
  ],
  towerWindows: [
    { name: 'tower foot east', cells: [[13, 2, 9], [13, 3, 9]] },
    { name: 'tower foot north (the street side)', cells: [[10, 2, 7], [11, 2, 7], [12, 2, 7], [10, 3, 7], [11, 3, 7], [12, 3, 7]] },
    { name: 'drying band north', cells: [[10, 7, 7], [11, 7, 7], [12, 7, 7], [10, 8, 7], [11, 8, 7], [12, 8, 7]] },
    { name: 'drying band east', cells: [[13, 7, 8], [13, 7, 9], [13, 7, 10], [13, 8, 8], [13, 8, 9], [13, 8, 10]] },
    { name: 'top loft north', cells: [[11, 10, 7], [11, 11, 7]] },
    { name: 'top loft east', cells: [[13, 10, 9], [13, 11, 9]] },
    { name: 'top loft south', cells: [[11, 10, 11], [11, 11, 11]] },
    { name: 'top loft west', cells: [[9, 10, 9], [9, 11, 9]] },
  ],
}

// Every piece, footprint first. kind: a furniture.py kind (recorded as a spot) or a fixture (the loom, chests, the
// stove, wool). part: the part that draws it. facing: the way a person using it faces. Level: the first level it is in.
export const FURNITURE = [
  // Shopfront display: bolts on dark stands behind each window (y 2..3: seen through the glass from the street)
  { room: 'Shop', kind: 'fixture', part: 'bolts', cells: [[2, 2, 3, 'red'], [3, 2, 3, 'yellow'], [2, 3, 3, 'orange']], stand: [[2, 1, 3], [3, 1, 3]] },
  { room: 'Shop', kind: 'fixture', part: 'bolts', cells: [[7, 2, 3, 'light_blue'], [8, 2, 3, 'blue'], [8, 3, 3, 'magenta']], stand: [[7, 1, 3], [8, 1, 3]] },
  // the Tailor's corner on the west wall: the loom under the front window, his chests under the back one
  { room: 'Shop', kind: 'fixture', part: 'loom', pos: [2, 1, 5], facing: 'east' },
  { room: 'Shop', kind: 'fixture', part: 'doubleChest', pos: [2, 1, 8], facing: 'east', along: 'south' },
  // the sewing table down the middle, a chair either side facing it
  { room: 'Shop', kind: 'table', part: 'table', pos: [5, 1, 5], facing: 'north', wood: 'spruce', colour: 'white' },
  { room: 'Shop', kind: 'table', part: 'table', pos: [5, 1, 6], facing: 'north', wood: 'spruce', colour: 'white' },
  { room: 'Shop', kind: 'table', part: 'table', pos: [5, 1, 7], facing: 'north', wood: 'spruce', colour: 'white' },
  { room: 'Shop', kind: 'chair', part: 'chair', pos: [4, 1, 6], facing: 'east', wood: 'spruce' },
  { room: 'Shop', kind: 'chair', part: 'chair', pos: [6, 1, 6], facing: 'west', wood: 'spruce' },
  // fitting corner on the east wall: two dress forms in cloth under the window, a bench for the customer
  { room: 'Shop', kind: 'fixture', part: 'dressForm', pos: [8, 1, 4], colour: 'pink', wood: 'dark_oak' },
  { room: 'Shop', kind: 'fixture', part: 'dressForm', pos: [8, 1, 5], colour: 'green', wood: 'dark_oak' },
  { room: 'Shop', kind: 'couch', part: 'couch', pos: [8, 1, 7], facing: 'west', length: 2, wood: 'spruce', colour: 'purple' },
  { room: 'Shop', kind: 'rug', part: 'rug', cellsAbs: [[7, 1, 6], [7, 1, 7]], colour: 'purple' },
  // the back wall: the pressing stove (its flue in the wall), the pressing board, the bolt rack
  { room: 'Shop', kind: 'fixture', part: 'stove', pos: [3, 1, 9], facing: 'north', block: 'furnace' },
  { room: 'Shop', kind: 'counter', part: 'counter', pos: [4, 1, 9], facing: 'north', wood: 'spruce', top: 'spruce_slab' },
  { room: 'Shop', kind: 'fixture', part: 'boltRack', x0: 6, x1: 8, z: 9, colours: [['white', 'light_gray'], ['brown', 'black'], ['cyan', 'gray']], cap: 'spruce_slab' },
  // light: a lantern hung from the tie beam either side of the sewing table
  { room: 'Shop', kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 6], facing: 'north', wood: 'dark_oak' },
  { room: 'Shop', kind: 'lamp', part: 'hangingLamp', pos: [7, 4, 6], facing: 'north', wood: 'dark_oak' },
  // Level II, the top loft: two bolts ready to hang
  // Level II, the drying shaft: dyed cloths hung from the cutting room's joists (y 7..8) behind the glazed band, a
  // chest of dyes against the shop wall (8 across from the loom), bolts waiting, a lantern under the joists
  { level: 2, room: 'Drying shaft (II)', kind: 'fixture', part: 'bolts', cells: [[10, 7, 8, 'red'], [10, 8, 8, 'red'], [11, 7, 8, 'orange'], [11, 8, 8, 'orange'], [12, 7, 8, 'yellow'], [12, 8, 8, 'yellow'], [12, 7, 9, 'cyan'], [12, 8, 9, 'cyan']], stand: [] },
  { level: 2, room: 'Drying shaft (II)', kind: 'fixture', part: 'chest', pos: [10, 1, 9], facing: 'east', block: 'chest' },
  { level: 2, room: 'Drying shaft (II)', kind: 'fixture', part: 'bolts', cells: [[12, 1, 10, 'purple'], [12, 2, 10, 'yellow']], stand: [] },
  { level: 2, room: 'Drying shaft (II)', kind: 'lamp', part: 'hangingLamp', pos: [10, 8, 10], facing: 'north', wood: 'dark_oak' },
  // Level II, the cutting room: stock shelves of bolts two high in the four corners, the cutting bench and its stool
  // between the hatch and the west window, a lantern on a chain from the cap's peak
  { level: 2, room: 'Cutting room (II)', kind: 'fixture', part: 'bolts', cells: [[10, 10, 8, 'white'], [10, 11, 8, 'light_gray'], [12, 10, 8, 'lime'], [12, 11, 8, 'green'], [10, 10, 10, 'pink'], [10, 11, 10, 'magenta'], [12, 10, 10, 'light_blue'], [12, 11, 10, 'blue']], stand: [] },
  { level: 2, room: 'Cutting room (II)', kind: 'table', part: 'table', pos: [10, 10, 9], facing: 'east', wood: 'spruce', colour: 'white' },
  { level: 2, room: 'Cutting room (II)', kind: 'chair', part: 'chair', pos: [11, 10, 9], facing: 'west', wood: 'spruce' },
  { level: 2, room: 'Cutting room (II)', kind: 'lamp', part: 'hangingLamp', pos: [11, 13, 9], facing: 'north', chain: 1, wood: 'dark_oak' },
]

// The walking line: the street -> the paved shop porch -> the door -> the loom (the Tailor stands at (3, 1, 5)) ->
// the chests; and in II on past the bench to the tower doorway and up the ladder.
export const WALK = [
  [1, 0, -3], [5, 0, -1], [5, 0, 0], [5, 0, 1], [5, 1, 2], [5, 1, 3], [4, 1, 4], [3, 1, 5], [3, 1, 6], [3, 1, 7], [3, 1, 8],
  [7, 1, 4], [7, 1, 8], [8, 1, 8], [9, 1, 8], [10, 1, 8], [11, 1, 9], [11, 1, 10], [11, 9, 10], [11, 10, 10],
]

// Outside: raised flower beds in their own soil in front of the display windows; the shop porch's paving; and in
// Level I only, the drying rack by the east wall (two posts and a rail, two cloths on it): where it stands, II raises
// the Dyer's Tower.
export const OUTSIDE = {
  flowerBeds: [{ x0: 2, x1: 3, z: 0, plants: ['allium', 'oxeye_daisy'] }, { x0: 7, x1: 8, z: 0, plants: ['oxeye_daisy', 'allium'] }],
  porch: { x0: 1, x1: 9, z: 1 },
  dryingRack: { x: 11, posts: [7, 10], top: 3, cloths: [[8, 'magenta'], [9, 'cyan']], drop: 2, onlyLevel: 1 },
}
