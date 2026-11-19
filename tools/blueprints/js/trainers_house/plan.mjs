// The Trainer's House, the plan: drawn before any wall (ROADMAP 49, tools/blueprints/briefs/trainers_house.md). Rooms,
// every furniture footprint and the walking line come first; recipe.mjs assembles it from the parts library (../parts).
//
// Three rough concepts (drawn on paper first):
//   A. "The Battle Court" — a turf court in front of a two-storey clubhouse, on one axis from the gate to the gable:
//      a gateway at the street, the marked field, the Training Post at the field's head, the clubhouse's tall gable
//      (the Trainer's loft window looks down the court), a covered bench stand along one side, dummies on the other.
//      Masses: gate piers (7), lean-to stand (6), clubhouse (gable 12, a pennant above). Plan: open court + 9x6 house.
//   B. "The Arena Barn" — one long barn with a high roof, the battle floor inside on boards, the Trainer's bed on a
//      gallery at the back. Massing one big box with a lean-to; roof one long gable; plan a single hall. Story: training
//      through the winter. Weak: a box from outside, a dark hall, and the biggest roof in the budget.
//   C. "The Lookout Ring" — a three-storey timber tower (bed at the top) beside a round sunken sparring ring, the post
//      on the ring's rim. Massing a tall tower + a flat ring; pyramid roof; plan a tiny tower room per floor. Story: a
//      trainer who watches the road for challengers. Weak: the bed three flights up, almost no room to live in, and
//      a round ring on a block grid that a 4-block stroll would mostly spend on the rim.
// A is the strongest: it is the only one where the job is the whole building's shape. From the street a player sees
// down the axis — gate, field, post, gable — and knows what happens here before he walks in; the post stands where
// battles happen, in the open with room on every side; the Trainer lives above his court and looks down it. B hides
// the job inside a box; C is a tower with a fence ring. A is also the riskiest (an open court reads as unfinished if
// the field is plain), so the field is marked like a real one: a centre line, a centre ring, a box at each end.
//
// Story: "The Battle Court". A trainer who came home a champion lays a turf court at the village edge and raises a
// clubhouse at its head: spruce frame and pale birch boards, a slate roof, his flag on the gable. Neighbours sit on
// the covered bench to watch; challengers come through the gate, stand in the blue box and face him across the ring.
//
// Coordinates: x across the front (west 0 -> east 16), y up (0 = the layer on the clicked block, the court's paving),
// z from the front (z = 0, north, the street) to the back. The court is paved at y 0 (walked at y 1); the yards beside
// it are the ground itself.
//
// Named parts (the owner's feedback can point at them):
//   Gate        a portal of two stone piers (x 6, 10; z 1) with lanterns on their caps, a beam between them, the two
//               trainers' banners on their faces, steps up from the street; open above, so the street sees the court.
//   Court       the paved field x 3..13, z 1..11: curb ring of stone, the turf field x 4..12, z 2..10 with its lines.
//   Post        the Training Post at the field's head (8, 1, 10), facing the gate, in the red home box.
//   Stand       the covered bench stand on the west (x 0..3, z 3..11): seven seats facing the court under a lean-to.
//   Clubhouse   the main mass x 4..12, z 11..17: ground-floor den (z 12..17, walls on a stone skirt), the loft jettied
//               one block forward over the porch (z 11) on brackets, a 45-degree slate gable facing the court.
//   Chimney     a stone stack up the west eave (x 3, z 14) from the den's hearth.
//   Yard        the sparring yard on the east (x 14..16): two dummies, a lamp post, a fence along the edge.

export const SIZE = [17, 15, 19] // w, h, d (limit 18 x 18 x 20)

// Palette (60/30/10): pale birch boards (walls) / spruce frame + deepslate tile roof + stone court / the field's moss
// turf with white lines and the two boxes (red: home, blue: challenger), the banners in the same two colours.
// Why: a sporting pavilion's light boards read at a distance against the dark roof; the field is the accent.
export const PALETTE = {
  stone: [['stone_bricks', 6], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1], ['andesite', 1]],
  curb: 'stone_bricks',
  frame: 'spruce_log',
  infill: 'birch_planks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'spruce_planks',
  wood: 'spruce',
  trim: 'dark_oak',
  glass: 'glass_pane',
  lowWall: 'stone_brick_wall',
  stoneStairs: 'stone_brick_stairs',
  turf: 'moss_block',
  line: 'white_concrete',
  home: 'red_concrete',
  away: 'blue_concrete',
  homeBanner: 'red_banner',
  awayBanner: 'blue_banner',
  homeWallBanner: 'red_wall_banner',
  awayWallBanner: 'blue_wall_banner',
  dummy: { body: 'hay_block', head: 'target' },
  fence: 'spruce_fence',
}

export const MASSES = {
  gate: { piers: [6, 10], z: 1, beamY: 4, pierTop: 4, beam: [5, 11] },
  court: { x0: 3, x1: 13, z0: 1, z1: 11, field: { x0: 4, x1: 12, z0: 2, z1: 10, r: 3, boxDepth: 1, boxHalf: 2 } },
  stand: {
    base: { x0: 0, x1: 2, z0: 3, z1: 11 }, back: 1, seats: 2, front: 3, ends: [3, 11],
    posts: [3, 7, 11], // on the curb (x 3) and in the back wall (x 1)
    roof: { axis: 'z', across: [0, 3], ridge: 1, along: [3, 11], ridgeY: 6 },
  },
  club: {
    walls: { x0: 4, x1: 12, z0: 12, z1: 17 }, // the ground floor
    loft: { x0: 4, x1: 12, z0: 11, z1: 17 }, // jettied one block forward over the porch
    room: { x0: 5, x1: 11, z0: 13, z1: 16 },
    loftRoom: { x0: 5, x1: 11, z0: 12, z1: 16 },
    skirt: { x0: 3, x1: 13, z0: 12, z1: 18 },
    wallTop: 3, beamY: 4, kneeTop: 6,
    frontPosts: [4, 7, 9, 12], backPosts: [4, 7, 9, 12], sidePosts: [12, 15, 17],
    roof: { axis: 'z', across: [3, 13], along: [10, 18], eaveY: 6, steep: false },
    dormer: { z: 14, face: 12, eave: 13, y0: 6 }, // on the east slope, its face on the knee wall's line
  },
  chimney: { x: 3, z: 14, top: 10 },
}

export const ROOMS = [
  { name: 'Court', use: 'battles: the challenger in the blue box, the Trainer at his post in the red', box: [4, 1, 2, 12, 1, 10] },
  { name: 'Den', use: 'the Trainer living room: hearth, armchair, table for two, chest, clock', box: [5, 1, 13, 11, 3, 16] },
  { name: 'Loft', use: 'his bedroom over the court: the bed, a chair at the gable window to watch, a chest', box: [5, 5, 12, 11, 7, 16] },
  { name: 'Stand', use: 'neighbours watching', box: [2, 1, 4, 2, 2, 10] },
]

export const OPENINGS = {
  gate: { opening: [7, 9], steps: [[7, 0, 0], [8, 0, 0], [9, 0, 0]] },
  frontDoor: { at: [8, 1, 12], facing: 'south' },
  stairs: { flight: [[7, 1, 16], [8, 2, 16], [9, 3, 16], [10, 4, 16]], rises: 'east', well: [[7, 4, 16], [8, 4, 16], [9, 4, 16]] },
  windows: [
    { name: 'den front west', cells: [[5, 2, 12], [6, 2, 12], [5, 3, 12], [6, 3, 12]], out: 'north', dress: 'shutters' },
    { name: 'den front east', cells: [[10, 2, 12], [11, 2, 12], [10, 3, 12], [11, 3, 12]], out: 'north', dress: 'shutters' },
    { name: 'den east', cells: [[12, 2, 13], [12, 2, 14], [12, 3, 13], [12, 3, 14]], out: 'east', dress: 'shutters' },
    { name: 'den west', cells: [[4, 2, 16], [4, 3, 16]], out: 'west', dress: 'shutters' },
    { name: 'den back', cells: [[5, 2, 17], [6, 2, 17], [5, 3, 17], [6, 3, 17]], out: 'south', dress: 'shutters' },
    { name: 'loft gable', cells: [[7, 6, 11], [8, 6, 11], [9, 6, 11], [7, 7, 11], [8, 7, 11], [9, 7, 11]], out: 'north', dress: 'sill' },
    { name: 'loft apex', cells: [[8, 9, 11]], out: 'north', dress: null },
    { name: 'loft back', cells: [[8, 6, 17], [8, 7, 17]], out: 'south', dress: null },
    { name: 'stand back north', cells: [[1, 2, 5], [1, 3, 5]], out: 'west', dress: 'shutters' },
    { name: 'stand back south', cells: [[1, 2, 9], [1, 3, 9]], out: 'west', dress: 'shutters' },
  ],
}

// Every piece of furniture, footprint first (kind: furniture.py's; part: the furnitureParts part that draws it).
export const FURNITURE = [
  // Den: the hearth in the west wall with an armchair and a rug before it, a table for two under the east window, the
  // chest in the front corner, a clock under the landing, a lamp from the loft floor
  { room: 'Den', kind: 'fixture', part: 'hearth', pos: [4, 1, 14], facing: 'east' },
  { room: 'Den', kind: 'chair', part: 'armchair', pos: [6, 1, 13], facing: 'west', wood: 'dark_oak', colour: 'red' },
  { room: 'Den', kind: 'rug', part: 'rug', cellsAbs: [[5, 1, 13], [5, 1, 14], [5, 1, 15]], colour: 'red' },
  { room: 'Den', kind: 'table', part: 'table', pos: [10, 1, 14], facing: 'north', wood: 'spruce', colour: 'white' },
  { room: 'Den', kind: 'chair', part: 'chair', pos: [9, 1, 14], facing: 'east', wood: 'spruce' },
  { room: 'Den', kind: 'chair', part: 'chair', pos: [11, 1, 14], facing: 'west', wood: 'spruce' },
  { room: 'Den', kind: 'fixture', part: 'chest', pos: [11, 1, 13], facing: 'west', block: 'chest' },
  { room: 'Den', kind: 'clock', part: 'clock', pos: [11, 1, 16], facing: 'west', wood: 'spruce' },
  { room: 'Den', kind: 'lamp', part: 'hangingLamp', pos: [8, 3, 14], facing: 'north', wood: 'spruce' },
  { room: 'Den', kind: 'picture', part: 'picture', pos: [7, 2, 13], facing: 'south', wood: 'spruce', colour: 'red' },
  // Loft: the Trainer's one bed, head to the gable beside the window; a chair at the window looking down the court;
  // a nightstand, a chest, a bookcase with a plant; a lamp from the ridge
  { room: 'Loft', kind: 'bed', part: 'bed', pos: [5, 5, 13], facing: 'north', colour: 'red' },
  { room: 'Loft', kind: 'nightstand', part: 'nightstand', pos: [6, 5, 12], facing: 'south', wood: 'spruce' },
  { room: 'Loft', kind: 'chair', part: 'armchair', pos: [8, 5, 12], facing: 'north', wood: 'dark_oak', colour: 'blue' },
  { room: 'Loft', kind: 'rug', part: 'rug', cellsAbs: [[7, 5, 13], [8, 5, 13], [9, 5, 13], [7, 5, 14], [8, 5, 14], [9, 5, 14]], colour: 'light_gray' },
  { room: 'Loft', kind: 'fixture', part: 'chest', pos: [11, 5, 12], facing: 'west', block: 'chest' },
  { room: 'Loft', kind: 'fixture', part: 'bookcase', pos: [11, 5, 14], facing: 'west', plant: 'potted_fern' },
  { room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [8, 8, 14], facing: 'north', chain: 2, wood: 'spruce' },
]

// The walking line: the waiting Trainer (6, 0, -3) -> the gate steps -> down the field -> the post (from (8, 1, 9),
// the challenger's side, or (7|9, 1, 10) beside it) -> round it to the porch -> the door -> the den -> the stair foot
// (6, 1, 16) -> up the flight east -> the loft (11, 5, 16) -> the bed's side (6, 5, 13).
export const WALK = [
  [8, 0, -1], [8, 0, 0], [8, 1, 1], [8, 1, 2], [8, 1, 5], [8, 1, 9], [7, 1, 9], [7, 1, 10], [7, 1, 11], [8, 1, 11],
  [8, 1, 12], [8, 1, 13], [8, 1, 14], [7, 1, 14], [7, 1, 15], [6, 1, 15], [6, 1, 16], [7, 1, 16], [8, 2, 16],
  [9, 3, 16], [10, 4, 16], [11, 5, 16], [11, 5, 15], [10, 5, 15], [10, 5, 14], [6, 5, 14], [6, 5, 13],
]

export const POST = { at: [8, 1, 10], facing: 'north' }

// Outside the rooms: flower beds in their own soil before the front wall, a woodpile by the chimney, the yard's
// dummies, lamp post and fence, lanterns under the stand's roof and the gate's beam, the pennant on the gable's peak.
export const OUTSIDE = {
  flowerBeds: [{ x0: 3, x1: 5, z: 0, plants: ['poppy', 'cornflower'] }, { x0: 11, x1: 13, z: 0, plants: ['cornflower', 'poppy'] }],
  woodpile: { x: 3, z0: 15, z1: 17 }, // on the skirt against the west wall, under the eave, behind the chimney
  dummies: [[15, 0, 4], [15, 0, 8]],
  lampPosts: [[15, 0, 6], [14, 0, 12]],
  yardFence: [[16, 0, 1], [16, 0, 11]],
  standLamps: [[2, 4, 5], [2, 4, 9]],
  pennant: { at: [8, 12, 10], top: 13 },
}
