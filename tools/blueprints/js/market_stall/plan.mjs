// The Market Stall (market_stall, market_stall_2, market_stall_3; ROADMAP 49), the plan: drawn before any wall. Masses,
// openings, every furniture and goods footprint and the walking line come first; recipe.mjs then assembles all three
// levels from the parts library (../parts) to fit it. Requirement: tools/blueprints/briefs/market_stall.md (designed
// blind, REBUILD.md).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. "The Fair Canopy": four posts under a striped-cloth pyramid, trestles of goods on all four sides; II and III wall
//      it in. Story: a fair-day tent made permanent. It is every market stall ever drawn, a tent has nothing for a
//      shop to grow out of, and a cloth roof of full wool blocks steps like a ziggurat.
//   B. "The Trader's Cart": a handcart with big wheels parked under a lean-to; II and III build a shop round it. Story:
//      the pedlar who stopped wandering. Charming, but wheels are hard to draw at this scale and a cart cannot become a
//      shop's front: the upgrade would bury Level I instead of growing from it.
//   C. "The Shop Board": a steep little timber booth that turns its gable to the street, its front opening on two
//      hinged boards as medieval shops did: the lower board folds down as the counter ledge (cloth laid out on it), the
//      upper board is propped out as the canopy with a red-and-white valance on two poles, the trader's sign on the
//      gable and a pennant pole at the corner of the pitch. II: the trader takes the next pitch and builds a lock-up
//      shop beside the booth, a lower gable whose roof meets the booth's in a valley, its own hatch with the Shop
//      Counter in it; III runs the lock-up back into a storeroom behind a partition, with a back door for deliveries.
//      Story: a market booth that shuts like a box at night grows into a shop of two gables on the street.
// Strongest: C. The shop board is a real mechanism a player can read (counter down, canopy up: open for business), it
// puts the goods and later the Shop Counter right on the street as the brief wants, the 45-degree gable is a hero
// silhouette on a 10 x 6 plot, and II and III grow out of it sideways and back without touching the booth. A and B
// are kept as notes.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block: no floor, the booth
// stands on the ground like a street stall), z from the front (z = 0, north, the street) to the back.
//
// Masses (named parts the owner's feedback can point at):
//   Booth      (I) the trader's booth, one bay deep: spruce log posts at x 1, 7 and z 2, 4, plates at y 4, a plank
//              ceiling over the trader's strip (z 3), limewashed panels on a plank sill at the sides and back, a
//              45-degree dark-oak roof (ridge along z at y 8) from x 0 to 8 and z 1 to 4; its front gable (king post,
//              the trader's red sign) jettied a block out over the propped canopy board; produce stacked at the back.
//   Shop board (I) the front's two hinged boards: the sill counter (x 2..5, y 0, z 2; the gate x 6), the folded-down
//              ledge (z 1, y 0), the propped canopy (z 1, y 4) and the striped valance (z 0, y 3) on two poles.
//              (II) the Shop Counter takes the sill's middle cell (4, 0, 2), under the sign and the lantern.
//   Side hatch (I) the booth's west bay open over its sill, a ledge of cloth outside it, a lantern on the eave: the
//              stall sells from two faces.
//   Lock-up    (II) the stock shop beside the booth, a storey lower: posts at x 8, 12 and z 2, 7, limewashed walls on a
//              plank sill, a dark-oak gable (ridge along z at y 6, x 7..13) whose west slope runs under the booth's east
//              eave; its front a door between two windows under a gable jettied out on a beam. (III) the same lines run on to a back
//              wall at z 11: a storeroom behind the old back wall (now a partition with a doorway), a back door.

export const SIZES = { 1: [8, 8, 6], 2: [15, 8, 8], 3: [15, 8, 12] } // w, h, d: I at most 10 x 6 (even, B60's 11 x 11)

// Palette (60/30/10): timber (spruce logs and boards for the frame, dark oak for the roofs, trims and the pennant pole)
// / limewash (white terracotta panels between the posts and in the gables) / the trader's red-and-white cloth (the
// valance, the goods, the sign and pennant). Why: the Carpenter's own spruce is the stall's body, the dark roof keeps
// the little gable crisp, the pale panels stop the booth reading as one brown box and frame the red sign, and the only
// bright colour is the cloth, which is what a market stall shows: "open, red and white, cloth for sale".
export const PALETTE = {
  log: 'spruce_log',
  boards: 'spruce_planks',
  infill: 'white_terracotta', // the limewashed panels between the posts and in the gables
  slab: 'spruce_slab',
  board: 'spruce_trapdoor', // the hinged shop boards
  prop: 'spruce_fence',
  gate: 'spruce_fence_gate',
  roof: { full: 'dark_oak_planks', stairs: 'dark_oak_stairs', slab: 'dark_oak_slab' },
  trim: 'dark_oak_stairs',
  cloth: ['red_wool', 'white_wool'],
  carpets: ['white_carpet', 'red_carpet'],
  sign: 'red_wall_banner',
  pennant: 'red_banner',
  pole: 'dark_oak_fence',
  door: 'spruce',
  glass: 'glass_pane',
  counter: 'aliveworkplace:shop_counter',
}

export const BOOTH = {
  posts: { x: [2, 6], z: [2, 4] }, top: 4, // posts y 0..4, plates at y 4, a ceiling over z 3
  roof: { axis: 'z', across: [1, 7], along: [0, 5], eaveY: 4, steep: false }, // a verge a block past each gable
  frontGable: 1, // jettied a block out over the canopy board
  kingPost: 4,
  room: { x0: 3, x1: 5, z0: 3, z1: 3 },
}

export const SHOP_BOARD = { line: 2, a0: 3, a1: 5, gap: [5], boardTo: 4, canopyY: 4, y: 0 } // the gate at x 5

export const LOCKUP = {
  posts: { x: [8, 12], z: [2, 7] }, top: 3, // a storey lower than the booth: its ridge (y 6) stays under the booth's
  back: { 2: 7, 3: 11 }, // the back wall's z by level (III's storeroom runs on to z 11)
  midPost: 7, // III: II's back posts stand on as the partition's posts
  roof: { axis: 'z', across: [7, 13], eaveY: 3, steep: false }, // along [1, back]; x 7 stays the booth's beside it
  room: { x0: 9, x1: 11 },
}

export const OPENINGS = {
  lockupWindows: [[9, 1, 2], [9, 2, 2], [11, 1, 2], [11, 2, 2]], // either side of the door
  lockupDoor: { at: [10, 0, 2], facing: 'south' },
  sideWindow: [[12, 1, 4], [12, 2, 4]],
  boothBack: [[4, 1, 4], [4, 2, 4]], // I: a shuttered window in the booth's back wall, behind the shelf
  sideHatch: [[2, 1, 3], [2, 2, 3]], // I: the booth's west bay opened over its sill (the lintel stays at y 3)
  vestibuleWindow: [[7, 1, 2], [7, 2, 2]], // II: the vestibule's street face
  aisleDoor: { at: [6, 0, 3], facing: 'west', vestibule: 7, through: [8, 0, 3] }, // II: the aisle -> the lock-up
  backWindow: { 2: [[10, 1, 7], [10, 2, 7]] }, // II's back wall; III cuts a doorway there instead
  partitionDoorway: [10, 0, 7], // III
  backDoor: { at: [10, 0, 11], facing: 'north' }, // III
  storeWindows: [[12, 1, 9], [12, 2, 9], [8, 1, 9], [8, 2, 9]], // III
}

// Every piece, footprint first. kind: a furniture.py kind (recorded as a spot) or a fixture/goods. Level: the first
// level it is in. The Shop Counter is the Shopkeeper's job site: the one POI the family draws (no barrel, composter or
// table in Level I, which would hand out a job).
export const FURNITURE = [
  // the booth's counter: a potted poppy and a bolt of white cloth (the cell the Shop Counter takes in II), cloth on the
  // ledge before it (the counter's goods are thin, so the open front stays a booth, not a room: holes.py)
  { room: 'Booth', kind: 'goods', part: 'pot', pos: [3, 1, 2], block: 'potted_poppy' },
  { room: 'Booth', kind: 'goods', part: 'block', pos: [4, 1, 2], block: 'white_carpet', until: 1 },
  // the lantern over the goods, hung from the front plate
  { room: 'Booth', kind: 'lamp', part: 'hangingLamp', pos: [4, 3, 2], facing: 'north', wood: 'spruce' },
  // the back shelf: a plank-slab shelf at head height behind the trader, produce and cloth on it
  { room: 'Booth', kind: 'shelf', part: 'shelf', cells: [[3, 2, 3], [4, 2, 3], [5, 2, 3]] },
  { room: 'Booth', kind: 'goods', part: 'block', pos: [3, 3, 3], block: 'melon' },
  { room: 'Booth', kind: 'goods', part: 'block', pos: [4, 3, 3], block: 'cake' },
  { room: 'Booth', kind: 'goods', part: 'block', pos: [5, 3, 3], block: 'pumpkin' },
  // the side hatch: the booth's west bay open over its sill (kept clear: a villager's head room), a folded-down ledge outside with
  // red cloth, a lantern hung from the eave over it; hay with a potted cornflower and two bolts stacked either side
  { room: 'Side hatch', kind: 'goods', part: 'ledge', pos: [1, 0, 3] },
  { room: 'Side hatch', kind: 'goods', part: 'block', pos: [1, 1, 3], block: 'red_carpet' },
  { room: 'Side hatch', kind: 'lamp', part: 'hangingLamp', pos: [1, 3, 3], facing: 'west', wood: 'spruce' },
  { room: 'Side hatch', kind: 'goods', part: 'block', pos: [1, 0, 2], block: 'hay_block' },
  { room: 'Side hatch', kind: 'goods', part: 'pot', pos: [1, 1, 2], block: 'potted_cornflower' },
  { room: 'Side hatch', kind: 'goods', part: 'block', pos: [1, 0, 4], block: 'white_wool' },
  { room: 'Side hatch', kind: 'goods', part: 'block', pos: [1, 1, 4], block: 'red_wool' },
  // the east side (I only: II's vestibule takes the strip): produce stacked under a lantern on the eave
  { room: 'East side', kind: 'goods', part: 'block', pos: [7, 0, 3], block: 'hay_block', until: 1 },
  { room: 'East side', kind: 'goods', part: 'block', pos: [7, 1, 3], block: 'melon', until: 1 },
  { room: 'East side', kind: 'goods', part: 'block', pos: [7, 0, 4], block: 'pumpkin', until: 1 },
  { room: 'East side', kind: 'lamp', part: 'hangingLamp', pos: [7, 3, 2], facing: 'east', wood: 'spruce', until: 1 },
  // Level II, the booth: the Shop Counter in the counter's east cell (the Shopkeeper stands behind it at (4, 0, 3), the
  // buyer before it at (4, 0, 1), where the ledge gives way to it)
  { level: 2, room: 'Booth', kind: 'job', part: 'counter', pos: [4, 0, 2], facing: 'north' },
  // Level II, the lock-up: a double chest along the west wall and a chest in the back-east corner (stock and takings),
  // the tally table and its stool by the front door, sacks of stock (hay, a pumpkin) in the east corner. The stall has
  // no floor (it stands on the clicked block), so the table and stool stay plain blocks, not furniture spots: a spot needs
  // a floor in the drawing to stand on (rebuild.py's furniture check)
  { level: 2, room: 'Lock-up', kind: 'store', part: 'doubleChest', pos: [9, 0, 5], facing: 'east', along: 'south' },
  { level: 2, room: 'Lock-up', kind: 'store', part: 'chest', pos: [11, 0, 6], facing: 'west' },
  { level: 2, room: 'Lock-up', kind: 'table', part: 'table', pos: [11, 0, 4], facing: 'north', wood: 'spruce', colour: 'white', plain: true },
  { level: 2, room: 'Lock-up', kind: 'chair', part: 'chair', pos: [11, 0, 3], facing: 'south', wood: 'spruce', plain: true },
  { level: 2, room: 'Lock-up', kind: 'goods', part: 'block', pos: [11, 0, 5], block: 'hay_block' },
  // a shelf of stock over the double chest (a top slab: the chests' lids stay clear), goods on it
  { level: 2, room: 'Lock-up', kind: 'shelf', part: 'shelf', cells: [[9, 2, 5], [9, 2, 6]] },
  { level: 2, room: 'Lock-up', kind: 'goods', part: 'block', pos: [9, 3, 5], block: 'melon' },
  { level: 2, room: 'Lock-up', kind: 'goods', part: 'block', pos: [9, 3, 6], block: 'white_wool' },
  { level: 2, room: 'Lock-up', kind: 'lamp', part: 'hangingLamp', pos: [10, 2, 4], facing: 'north', chain: 3, wood: 'spruce' },
  // Level III, the storeroom: a double chest along each side wall, stock stacked at the partition, the walk down the
  // middle to the back door
  { level: 3, room: 'Storeroom', kind: 'store', part: 'doubleChest', pos: [9, 0, 9], facing: 'east', along: 'south' },
  { level: 3, room: 'Storeroom', kind: 'store', part: 'doubleChest', pos: [11, 0, 9], facing: 'west', along: 'south' },
  { level: 3, room: 'Storeroom', kind: 'goods', part: 'block', pos: [9, 0, 8], block: 'hay_block' },
  { level: 3, room: 'Storeroom', kind: 'goods', part: 'block', pos: [9, 1, 8], block: 'pumpkin' },
  { level: 3, room: 'Storeroom', kind: 'goods', part: 'block', pos: [11, 0, 8], block: 'melon' },
  { level: 3, room: 'Storeroom', kind: 'shelf', part: 'shelf', cells: [[9, 2, 9], [9, 2, 10], [11, 2, 9], [11, 2, 10]] },
  ...[[9, 9, 'hay_block'], [9, 10, 'red_wool'], [11, 9, 'pumpkin'], [11, 10, 'hay_block']].map(([x, z, block]) =>
    ({ level: 3, room: 'Storeroom', kind: 'goods', part: 'block', pos: [x, 3, z], block })),
  { level: 3, room: 'Storeroom', kind: 'lamp', part: 'hangingLamp', pos: [10, 2, 9], facing: 'north', chain: 3, wood: 'spruce' },
  // Level III, the street: produce stacked under the lock-up's east eave (the shop's second display)
  { level: 3, room: 'East display', kind: 'goods', part: 'block', pos: [13, 0, 6], block: 'hay_block' },
  { level: 3, room: 'East display', kind: 'goods', part: 'pot', pos: [13, 1, 6], block: 'potted_red_tulip' },
  { level: 3, room: 'East display', kind: 'goods', part: 'block', pos: [13, 0, 7], block: 'pumpkin' },
  { level: 3, room: 'East display', kind: 'goods', part: 'block', pos: [13, 0, 8], block: 'melon' },
]

// The walking line: the street (the doorstep (5, 0, -1)) -> under the valance to the ledge (browse) -> the gate at
// x 6 into the booth (the trader's strip, z 3); in II the buyer at (4, 0, 1) before the Shop Counter, the Shopkeeper
// behind it at (4, 0, 3); the lock-up's door (10, 0, 2) -> the chests; in III on through the partition (10, 0, 7) to the
// storeroom and out of the back door.
export const WALK = [
  [4, 0, -1], [4, 0, 0], [5, 0, 1], [5, 0, 2], [5, 0, 3], [4, 0, 3], [3, 0, 3], [6, 0, 3], [7, 0, 3], [8, 0, 3], [9, 0, 3],
  [10, 0, 0], [10, 0, 1], [10, 0, 2], [10, 0, 3], [10, 0, 4], [10, 0, 6], [10, 0, 7], [10, 0, 9], [10, 0, 11],
]
