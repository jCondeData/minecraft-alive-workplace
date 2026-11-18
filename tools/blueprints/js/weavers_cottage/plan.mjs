// Weaver's Cottage (weavers_cottage, weavers_cottage_2; ROADMAP 49), the plan: drawn before any wall. The brief is
// tools/blueprints/briefs/weavers_cottage.md. recipe.mjs assembles both levels from the parts library (../parts) to fit
// this plan; LEVEL=2 draws Level II over Level I (everything of Level I stays in its cell).
//
// Three rough concepts first:
//   1. The Laithe Loomshop: a long two-storey stone range across the front (ridge along x, 45-degree stone-slate
//      roof, a gable chimney stack), the Shepherd's loom behind a long mullioned "weaver's window" and a second long
//      band of lights over it, a little gabled stone porch at the door end; the fold built onto the east gable, so the
//      Shepherd walks from his loom through a door the sheep can't open straight in among his flock, and wool is
//      hoisted into the loft through a taking-in door in that gable. Story: a Pennine hill weaver, who keeps his own
//      sheep under his own roofline and weaves their wool by the longest window in the dale.
//   2. The Tall Weaver's House: a narrow three-storey stone house, the loomshop on the top floor under a row of
//      windows, a pen in front. Story: a town weaver's terrace end. The loom must stay on the ground (the kept cell
//      (2, 1, 3)), so its best idea is lost, and it reads as a tower beside a pen.
//   3. The Round Fold: a ring-shaped sheepfold with a one-room bothy set into its rim, a round thatch-like roof.
//      Story: a summer shieling on the high pasture. Charming, but the fold must be fences (walls don't count) and a
//      Shepherd can't get within reach of sheep in a ring he can't enter; a one-room hut is a weak silhouette.
// Strongest: 1. It is the only one whose shape comes out of the job: the loom at the long window, the fold through
// the gable door (the code makes him walk within 2.5 blocks of each sheep, and villagers open doors but not gates),
// the hoist for the fleeces, and Level II's dye garden and tenter frame behind. Its two bands of mullioned lights are
// what real weavers' cottages are known by, so it reads as a weaver's house from the street, not a generic cottage.
//
// Coordinates: x across the front (west 0 -> east 17), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The way in is the porch arch on the front.
//
// Masses (named parts the owner's feedback can point at):
//   Range     the main mass: rubble walls x 1..11, z 2..8, two storeys (workroom y 1..3, loft floor y 4, loft y 5..7),
//             a 45-degree deepslate-slate roof, ridge along x (y 11), one block over every side.
//   Windows   the weaver's window (ground, x 2..6: three lights between stone mullions) and the loft's long band
//             (x 2..10: five lights), on a continuous moulded sill course at y 4 (the ground band's drip).
//   Porch     a little gabled stone porch over the door (x 8..10, z 0..1), its ridge meeting the loft band's mullion.
//   Stack     a wide gable-end chimney stack on the west gable's apex, the hearth inside.
//   Hoist     the taking-in door high in the east gable, a hoist beam over the fold with its hook (a lantern).
//   Fold      the sheep fold on the east gable (x 11..17, z 2..11): fences, a gate to the street, a drystone back wall,
//             an open slate lean-to on the gable's back half (the fold door under it, hay at its back end; the
//             hoist's hook hangs clear of it), grass for
//             the sheep to graze back their wool.
//   Dye garden (Level II) behind the range (x 0..11, z 8..16): a drystone wall, rows of dye flowers by colour (a dye
//             chart), a back door from the workroom down a gravel walk, and a tenter frame across the end with the
//             cloth dyed from those beds hung on it.

export const SIZE = { 1: [18, 14, 12], 2: [18, 14, 17] } // w, h, d per level (plot limit 18 x 28 x 26)

// Palette (60/30/10): gritstone rubble walls (cobble, stone, andesite; moss low down) / dark deepslate slates and
// dressed stone (quoins, mullions, sills, lintels, the stack) / spruce joinery and the wool's colours. Why: a hill
// weaver builds from the field's stone and roofs with heavy dark slates; the dressed stone draws the long windows
// that are the house's whole point, and the only bright colour is what he makes: wool, dye flowers, dyed cloth.
export const PALETTE = {
  stone: [['cobblestone', 5], ['stone', 3], ['andesite', 2]],
  mossy: [['cobblestone', 3], ['mossy_cobblestone', 3], ['stone', 1]], // the lowest course and the fold's dry wall
  dressed: 'stone_bricks',
  dressedAlt: 'polished_andesite', // quoins: long and short stones
  mullion: 'stone_brick_wall',
  course: 'stone_brick_stairs', // the moulded sill/drip course
  sill: 'stone_brick_slab',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'spruce_planks',
  wood: 'spruce', // doors, stairs, fences, gate, trapdoors, barge boards
  log: 'spruce_log',
  glass: 'glass_pane',
  gravel: 'gravel',
  wool: ['white_wool', 'light_gray_wool', 'brown_wool'],
}

export const RANGE = {
  walls: { x0: 1, x1: 11, z0: 2, z1: 8 },
  room: { x0: 2, x1: 10, z0: 3, z1: 7 },
  wallTop: 7, loftY: 4,
  roof: { axis: 'x', across: [1, 9], along: [0, 12], eaveY: 7, steep: false },
}

export const PORCH = { x0: 8, x1: 10, z0: 0, z1: 1, roof: { axis: 'z', across: [7, 11], along: [0, 1], eaveY: 3, steep: false } }

export const ROOMS = [
  { name: 'Workroom', use: 'the loom at the weaver\'s window, his chest, the carding table, the hearth', box: [2, 1, 3, 10, 3, 7] },
  { name: 'Loft', use: 'the wool loft: fleeces hoisted in at the gable, the spinners\' bench at the long band', box: [2, 5, 3, 10, 8, 7] },
]

export const OPENINGS = {
  porchArch: { at: [9, 1, 0], height: 2, step: [9, 0, 0] },
  frontDoor: { at: [9, 1, 2], facing: 'south', out: 'north' },
  foldDoor: { at: [11, 1, 6], facing: 'west', out: 'east' },
  takingIn: { at: [11, 5, 5], facing: 'west', out: 'east' },
  backDoor: { at: [4, 1, 8], facing: 'north', out: 'south', level: 2 },
  // the flight up to the loft, along the back wall, rising west; the well over it
  stairs: { flight: [[10, 1, 7], [9, 2, 7], [8, 3, 7], [7, 4, 7]], rises: 'west', well: [[10, 4, 7], [9, 4, 7], [8, 4, 7]] },
  // the long windows: lights at these x, mullions between them (front wall z 2)
  weaverWindow: { z: 2, x0: 2, x1: 6, y0: 2, y1: 3, lights: [2, 4, 6] },
  loftBand: { z: 2, x0: 2, x1: 10, y0: 5, y1: 6, lights: [2, 4, 6, 8, 10] },
  windows: [ // single lights: cells, the side they look out of
    { name: 'workroom back west', cells: [[2, 2, 8], [2, 3, 8]], out: 'south' },
    { name: 'workroom back east', cells: [[6, 2, 8], [6, 3, 8]], out: 'south' },
    { name: 'loom side light', cells: [[1, 2, 3], [1, 3, 3]], out: 'west' },
    { name: 'fold light', cells: [[11, 2, 3], [11, 3, 3]], out: 'east' },
    { name: 'loft back west', cells: [[4, 5, 8], [4, 6, 8]], out: 'south' },
    { name: 'loft back east', cells: [[6, 5, 8], [6, 6, 8]], out: 'south' },
    { name: 'loft west front', cells: [[1, 5, 3], [1, 6, 3]], out: 'west' },
    { name: 'loft east back', cells: [[11, 5, 7], [11, 6, 7]], out: 'east' },
  ],
}

// Every piece of furniture, footprint first (kind: a furniture.py kind recorded as a spot, or a fixture the game uses as
// itself: the loom, the chests, the hearth). The loom and the first chest stand in the kept cells of every older
// drawing: loom (2, 1, 3) facing east, chest (6, 1, 3) single facing west.
export const FURNITURE = [
  // Workroom: the loom in the window's west light (the weaver sits east of it, at (3, 1, 3)), his chest at the
  // window's east end, a carding table with two stools in the middle, the hearth in the west wall, the fleeces
  { room: 'Workroom', kind: 'fixture', part: 'loom', pos: [2, 1, 3], facing: 'east', block: 'loom' },
  { room: 'Workroom', kind: 'fixture', part: 'chest', pos: [6, 1, 3], facing: 'west', block: 'chest' },
  { room: 'Workroom', kind: 'table', part: 'table', pos: [5, 1, 5], facing: 'north', wood: 'spruce', colour: 'white' },
  { room: 'Workroom', kind: 'chair', part: 'chair', pos: [4, 1, 5], facing: 'east', wood: 'spruce' },
  { room: 'Workroom', kind: 'chair', part: 'chair', pos: [6, 1, 5], facing: 'west', wood: 'spruce' },
  { room: 'Workroom', kind: 'fixture', part: 'hearth', pos: [2, 1, 5], facing: 'east', block: 'campfire' },
  { room: 'Workroom', kind: 'lamp', part: 'hangingLamp', pos: [4, 3, 4], facing: 'north', wood: 'spruce' },
  { room: 'Workroom', kind: 'lamp', part: 'hangingLamp', pos: [8, 3, 5], facing: 'north', wood: 'spruce' },
  { room: 'Workroom', kind: 'picture', part: 'picture', pos: [5, 3, 7], facing: 'north', wood: 'spruce', colour: 'blue' },
  // Loft: the spinners' bench at the long band, the wool chest by the taking-in door, fleeces under the back slope
  { room: 'Loft', kind: 'table', part: 'table', pos: [4, 5, 3], facing: 'north', wood: 'spruce', colour: 'light_gray' },
  { room: 'Loft', kind: 'chair', part: 'chair', pos: [3, 5, 3], facing: 'east', wood: 'spruce' },
  { room: 'Loft', kind: 'chair', part: 'chair', pos: [5, 5, 3], facing: 'west', wood: 'spruce' },
  { room: 'Loft', kind: 'table', part: 'table', pos: [5, 5, 7], facing: 'north', wood: 'spruce', colour: 'white' }, // the cutting table, cloth laid on it
  { room: 'Loft', kind: 'table', part: 'table', pos: [6, 5, 7], facing: 'north', wood: 'spruce', colour: 'light_blue' },
  { room: 'Loft', kind: 'fixture', part: 'chest', pos: [10, 5, 4], facing: 'west', block: 'chest' },
  { room: 'Loft', kind: 'rug', part: 'rug', cellsAbs: [[3, 5, 4], [4, 5, 4], [5, 5, 4], [4, 5, 5]], colour: 'brown' },
  { room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [4, 8, 5], facing: 'north', chain: 2, wood: 'spruce' },
  { room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [8, 8, 5], facing: 'north', chain: 2, wood: 'spruce' },
]

// Fleeces and bolts (wool blocks, not furniture): [x, y, z, which wool (an index into PALETTE.wool, or a colour)]:
// raw fleeces under the loft's back slope and by the workroom's back wall, woven bolts by the loft's west gable
export const FLEECES = [
  [2, 5, 4, 'blue_wool'], [2, 5, 5, 'red_wool'], [2, 6, 4, 'yellow_wool'],
  [2, 1, 7, 0], [3, 1, 7, 1], [3, 2, 7, 0],
  [2, 5, 7, 0], [3, 5, 7, 2], [2, 6, 7, 1], [2, 5, 6, 0], [4, 5, 7, 0],
]

// The walking lines: street -> porch step -> arch -> door -> workroom -> the weaver's seat at the loom; -> the fold
// door and in among the flock; -> the flight -> the loft and the wool chest. Level II: -> the back door -> the garden.
export const WALK = [
  [1, 0, -2], [9, 0, -1], [9, 0, 0], [9, 1, 1], [9, 1, 2], [9, 1, 3], [9, 1, 4], [8, 1, 4], [3, 1, 4], [3, 1, 3],
  [9, 1, 4], [10, 1, 5], [10, 1, 6], [11, 1, 6], [12, 1, 6], [14, 1, 7],
  [10, 1, 6], [10, 1, 7], [9, 2, 7], [8, 3, 7], [7, 4, 7], [7, 5, 6], [9, 5, 5], [10, 5, 5],
  [4, 1, 6], [4, 1, 7], [4, 1, 8], [4, 1, 9], [4, 1, 14],
]

export const FOLD = {
  // the fence: front (z 2) from the range's corner to the east corner, down the east side, the gate on the front
  fence: [[12, 1, 2], [17, 1, 2], [17, 1, 10]],
  westFence: [[11, 1, 9], [11, 1, 10]],
  gate: { at: [14, 1, 2], facing: 'north' },
  backWall: { z: 11, x0: 11, x1: 17, top: 2 }, // drystone, y 1..top
  ground: { x0: 12, x1: 16, z0: 3, z1: 10 }, // grass to graze; trodden bare by the gate and the fold door
  trodden: [[14, 3], [14, 4], [13, 4], [15, 4], [12, 6], [12, 7], [13, 6], [12, 5]],
  hay: [[12, 1, 9], [12, 1, 10], [12, 2, 10], [12, 1, 8]], // under the lean-to's back end
  cornerLamp: [17, 2, 2],
  hoist: { at: [11, 9, 5], reach: 3, drop: 4 },
  shed: { x0: 12, x1: 13, z0: 6, z1: 10, topY: 4, posts: [6, 10] }, // the open lean-to on the gable, over the fold door
  woodpile: { x: 0, z0: 5, z1: 7 }, // logs for the hearth, under the stack
}

export const FRONT = {
  bed: { x0: 2, x1: 6, z: 0, plants: ['allium', 'oxeye_daisy', 'cornflower', 'oxeye_daisy', 'allium'] },
}

// Level II: the dye garden. Rows by the dye each flower gives (a dye chart in flowers), a gravel walk down the middle
// from the back door, the tenter frame across the far end with the cloth dyed from these beds.
export const GARDEN = {
  wall: [[0, 1, 8], [0, 1, 16], [11, 1, 16], [11, 1, 12]], // drystone (walls), y 1
  walk: { x: 4, z0: 9, z1: 14 }, // gravel, down the middle and along the tenter (z 14)
  soil: 'dirt',
  beds: { west: [1, 3], east: [5, 10], z0: 10, z1: 13 },
  rows: { // z: [west plants, east plants (cycled; two-block flowers are tall)]: one dye colour a row
    10: [['poppy'], ['rose_bush', 'poppy']],
    11: [['orange_tulip', 'dandelion'], ['sunflower', 'torchflower']],
    12: [['cornflower'], ['blue_orchid', 'cornflower']],
    13: [['allium'], ['lilac', 'allium']],
  },
  herbs: { z: 9, x0: 7, x1: 10, plants: ['lily_of_the_valley', 'oxeye_daisy', 'azure_bluet'] }, // whites, under the eaves
  path: { z: 14, x0: 1, x1: 10 },
  tenter: { z: 15, x0: 1, x1: 10, posts: [1, 4, 7, 10], y: 3, cloth: ['red', 'orange', 'yellow', 'blue', 'light_blue', 'magenta'] },
}
