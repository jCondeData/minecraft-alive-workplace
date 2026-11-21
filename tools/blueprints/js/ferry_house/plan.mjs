// The Ferry House, the plan: drawn before any wall. Rooms, every furniture footprint (kind, cells, facing) and the
// walking line come first; recipe.mjs then assembles the house from the parts library's (../parts) named parts.
//
// Three rough concepts (ROADMAP 49, tools/blueprints/REBUILD.md), from the brief only:
//   A. The Toll Light. A narrow three-storey stone tower (5 x 5) with a glazed lantern room on top as the ferry's
//      light, a low waiting hall leaning on its foot; pyramid roof; the post in the hall, the bed half way up. Story:
//      the light on the shore that calls the ferry. A strong vertical, but the library already has map-room and wall
//      towers, and a 5-wide tower spends the brief's 18 of height on stairs, not on passengers.
//   B. The Upturned Ferry. The old ferry, too worn to row, hauled up and turned keel-up on low whitewashed walls as
//      the ferryman's roof, a landing stage beside her. A silhouette you'd know anywhere, but the Fisher's Hut redesign
//      (drawn at the same time, parts/shore.mjs) is already an upturned boat: two of them in one village would read
//      as one idea twice, and the boat belongs to the fisherman. Dropped after a first block-out.
//   C. The Ferry Station. The village's stop on the travel network, built like the little waterside stations of
//      lake steamers: a two-storey house along the shore, its whole waterfront under a deep platform canopy on posts
//      and brackets with a painted valance, the waiting room behind big windows, the Travel Post and the benches out
//      on the platform where passengers watch for the boat; the ferryman lives upstairs; a lookout cupola on the slate
//      ridge carries the ferry light, a signal mast with a pennant stands at the platform's end, and a low parcel
//      store leans on the east gable. Plan: waiting room and stair below, his bedroom above, the platform outside.
// The strongest is C: it is about what the building does (people waiting for a boat), it reads from the water as a
// stop on a line (the long canopy and its fringe, the lit cupola), it gives four masses of clearly different height
// (house, store, canopy, cupola) plus the mast, and every requirement (post by the water, room round it, a waiting
// place, the bed) falls naturally into it. A is a generic tower; B is a good idea already taken. Built: C.
//
// Coordinates: x across the front (west 0 -> east 17), y up (0 = the layer on the clicked block), z from the front
// (z = 0, the waterfront, north) to the back (z = 10). The water lies beyond z = 0 (the Steward's shore rule), so the
// platform, the door and the post face it, and the village's road arrives on the same side.
//
// Masses (named parts the owner's feedback can point at):
//   House     the main mass: x 2..12, z 3..9 (11 x 7). Ground floor y 1..4 in rubble stone with dressed quoins (the
//             waiting room); a spruce floor beam at y 5; the upper floor y 6..8 in white plaster between spruce posts
//             (the ferryman's room); a 45-degree slate roof, ridge along x at y 13, overhanging a block all round.
//   Canopy    the platform canopy along the whole front: x 1..13 over z 0..2, a slate lean-to rising from y 4 at the
//             front to the upper wall at y 6, on four posts (x 2, 5, 9, 12, on the house's bay lines) with brackets,
//             a teal valance hanging from its eave.
//   Cupola    the lookout on the ridge over the door (x 6..8, z 5..7): a louvred lantern with the ferry light.
//   Chimney   the waiting room's stove stack up the east gable (x 12, z 7), past the ridge.
//   (Cut by the ship step, 50.5: the parcel store on the east gable and the signal mast at the platform's west end,
//   the critic's weakest pieces, so the house is built within its builder's working day; the platform now ends a
//   block beyond the canopy at either end, x 0..14.)

export const SIZE = [15, 17, 11] // w, h, d (brief: width <= 18, height <= 18, depth 10 or 11)

// Palette (60/30/10): rubble stone (the ground floor, the platform's footing) and white lime plaster (the upper floor) /
// spruce (frame, floor beams, canopy posts, platform boards) and blue-grey slate (both roofs) / teal paint (warped wood:
// the valance, the shutters, the store's door; a cyan pennant). Why: a lakeside station reads stone below, light walls
// above, a dark roof; the teal is the ferry line's own colour, repeated wherever the company painted.
export const PALETTE = {
  stone: [['cobblestone', 5], ['stone', 3], ['andesite', 2], ['mossy_cobblestone', 1]],
  quoin: 'stone_bricks',
  quoinAlt: 'polished_andesite',
  finial: 'lightning_rod', // the cupola's spike over its hung light
  wall: [['white_concrete', 6], ['polished_diorite', 1]],
  frame: 'spruce_log',
  canopyPost: 'stripped_warped_stem', // the company's teal on the platform's posts
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  deck: 'spruce_planks',
  floor: 'spruce_planks',
  wood: 'spruce', // doors, chairs, tables, benches, fences, stairs
  paint: 'warped', // valance, shutters, the store's door
  glass: 'glass_pane',
  flag: 'cyan',
}

export const MASSES = {
  house: {
    walls: { x0: 2, x1: 12, z0: 3, z1: 9 },
    room: { x0: 3, x1: 11, z0: 4, z1: 8 },
    groundTop: 4, floorY: 5, upperTop: 8, eaveBeamY: 9,
    frontPosts: [2, 5, 9, 12], backPosts: [2, 5, 9, 12], sidePosts: [3, 6, 9],
    roof: { axis: 'x', across: [2, 10], along: [1, 13], eaveY: 9, steep: false },
  },
  canopy: { x0: 1, x1: 13, wallZ: 3, depth: 3, eaveY: 4, posts: [2, 5, 9, 12] },
  cupola: { x: 7, z: 6, y: 13 },
  chimney: { x: 12, z: 7, top: 14 },
  platform: { x0: 0, x1: 14, z0: 0, z1: 2 },
  landingSteps: { x0: 6, x1: 8, z: 0 }, // steps up on to the platform before the door
  sign: [7, 8, 2], // the ferry company's cloth on the upper floor's middle bay, over the door
}

export const ROOMS = [
  { name: 'Platform', use: 'passengers wait on the benches; the ferryman sells tickets at the post', box: [1, 1, 0, 13, 3, 2] },
  { name: 'Waiting room', use: 'a stove, a table, benches and the clock; the stair up', box: [3, 1, 4, 11, 4, 8] },
  { name: 'Bedroom', use: "the ferryman's room under the roof", box: [3, 6, 4, 11, 8, 8] },
]

export const OPENINGS = {
  frontDoor: { at: [7, 1, 3], facing: 'south', transom: [7, 3, 3] },
  stairs: { flight: [[3, 1, 8], [4, 2, 8], [5, 3, 8], [6, 4, 8], [7, 5, 8]], rises: 'east', well: [[4, 5, 8], [5, 5, 8], [6, 5, 8]], rail: [[3, 6, 8], [3, 6, 7], [6, 6, 7]] },
  windows: [
    // ground floor, under the canopy: big plain lights into the waiting room
    { name: 'waiting west', cells: [[3, 2, 3], [4, 2, 3], [3, 3, 3], [4, 3, 3]], out: 'north', dress: null },
    { name: 'waiting east', cells: [[10, 2, 3], [11, 2, 3], [10, 3, 3], [11, 3, 3]], out: 'north', dress: null },
    { name: 'waiting back west', cells: [[3, 2, 9], [4, 2, 9], [3, 3, 9], [4, 3, 9]], out: 'south', dress: 'sill' },
    { name: 'waiting back east', cells: [[10, 2, 9], [11, 2, 9], [10, 3, 9], [11, 3, 9]], out: 'south', dress: 'sill' },
    { name: 'waiting west side', cells: [[2, 2, 5], [2, 3, 5]], out: 'west', dress: 'sill' },
    { name: 'waiting east side', cells: [[12, 2, 5], [12, 3, 5]], out: 'east', dress: null },
    // upper floor: stacked over the ground floor's bays, the two to the water shuttered (the ship step left the back and
    // side ones plain so the house is built within its builder's working day)
    { name: 'bedroom front west', cells: [[3, 7, 3], [4, 7, 3], [3, 8, 3], [4, 8, 3]], out: 'north', dress: 'shutters' },
    { name: 'bedroom front east', cells: [[10, 7, 3], [11, 7, 3], [10, 8, 3], [11, 8, 3]], out: 'north', dress: 'shutters' },
    { name: 'bedroom back east', cells: [[10, 7, 9], [11, 7, 9], [10, 8, 9], [11, 8, 9]], out: 'south', dress: null },
    { name: 'bedroom back west', cells: [[3, 7, 9], [4, 7, 9], [3, 8, 9], [4, 8, 9]], out: 'south', dress: null },
    { name: 'bedroom west', cells: [[2, 7, 5], [2, 8, 5]], out: 'west', dress: null },
    { name: 'bedroom east', cells: [[12, 7, 5], [12, 8, 5]], out: 'east', dress: null },
    { name: 'west gable', cells: [[2, 10, 6], [2, 11, 6]], out: 'west', dress: null },
  ],
}

// Every piece of furniture, footprint first (furnitureParts; see house_t1_b/plan.mjs for the fields).
export const FURNITURE = [
  // Platform: the Travel Post by the door, facing the water (the open cells (8, 1, 2) and (9, 1, 1) beside it); a
  // lantern hung under the canopy either side
  { room: 'Platform', kind: 'fixture', part: 'travelPost', pos: [9, 1, 2], facing: 'north' },
  { room: 'Platform', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 1], facing: 'north', chain: 0, wood: 'spruce' },
  { room: 'Platform', kind: 'lamp', part: 'hangingLamp', pos: [10, 4, 1], facing: 'north', chain: 0, wood: 'spruce' },
  // Waiting room: a bench (couch) along the west wall, a table with chairs for the passengers, the stove by the east
  // gable, the station clock in the corner by the door, the timetable (a cloth) over the bench, lanterns from the beams
  { room: 'Waiting room', kind: 'couch', part: 'couch', pos: [3, 1, 5], facing: 'east', length: 2, wood: 'spruce' },
  { room: 'Waiting room', kind: 'table', part: 'table', pos: [9, 1, 6], facing: 'north', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce', colour: 'cyan' },
  { room: 'Waiting room', kind: 'chair', part: 'chair', pos: [8, 1, 6], facing: 'east', wood: 'spruce' },
  { room: 'Waiting room', kind: 'chair', part: 'chair', pos: [10, 1, 6], facing: 'west', wood: 'spruce' },
  { room: 'Waiting room', kind: 'fixture', part: 'stove', pos: [11, 1, 7], facing: 'west', block: 'furnace' },
  { room: 'Waiting room', kind: 'clock', part: 'clock', pos: [8, 1, 4], facing: 'south', wood: 'spruce' },
  { room: 'Waiting room', kind: 'picture', part: 'picture', pos: [3, 3, 6], facing: 'east', wood: 'spruce', colour: 'cyan' },
  { room: 'Waiting room', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 5], facing: 'north', chain: 0, wood: 'spruce' },
  { room: 'Waiting room', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 5], facing: 'north', chain: 0, wood: 'spruce' },
  { room: 'Waiting room', kind: 'rug', part: 'rug', cellsAbs: [[5, 1, 5], [6, 1, 5], [5, 1, 6], [6, 1, 6]], colour: 'gray' },
  // Bedroom: the bed against the front wall, a nightstand, his sea chest in the back corner, a desk and chair under
  // the west window, a cupboard, a lantern on a chain from the ridge
  { room: 'Bedroom', kind: 'bed', part: 'bed', pos: [10, 6, 5], facing: 'north', colour: 'cyan' },
  { room: 'Bedroom', kind: 'nightstand', part: 'nightstand', pos: [9, 6, 4], facing: 'south', wood: 'spruce' },
  { room: 'Bedroom', kind: 'fixture', part: 'chest', pos: [11, 6, 8], facing: 'west', block: 'chest' },
  { room: 'Bedroom', kind: 'desk', part: 'desk', pos: [3, 6, 5], facing: 'west', wood: 'spruce' },
  { room: 'Bedroom', kind: 'chair', part: 'chair', pos: [4, 6, 5], facing: 'west', wood: 'spruce' },
  { room: 'Bedroom', kind: 'cupboard', part: 'cupboard', pos: [3, 6, 4], facing: 'east', wood: 'spruce' },
  { room: 'Bedroom', kind: 'rug', part: 'rug', cellsAbs: [[8, 6, 5], [8, 6, 6], [9, 6, 5], [9, 6, 6]], colour: 'light_gray' },
  { room: 'Bedroom', kind: 'lamp', part: 'hangingLamp', pos: [7, 10, 6], facing: 'north', chain: 2, wood: 'spruce' },
  // a second lantern from the ridge over the desk end (the ship step: the desk and cupboard stood at light 7)
  { room: 'Bedroom', kind: 'lamp', part: 'hangingLamp', pos: [4, 10, 6], facing: 'north', chain: 2, wood: 'spruce' },
]

// Outdoor seats on the platform, not furniture spots: benches with their backs to the waiting room's front wall.
export const BENCHES = [
  { x0: 3, x1: 4, y: 1, z: 2, facing: 'north' },
  { x0: 10, x1: 11, y: 1, z: 2, facing: 'north' },
]

// The walking line (feet cells; the boards are at y 0): the Ferryman arrives at (6, 0, -3), three in front of the
// front edge, steps onto the platform and stands at the post (9, 1, 1) / (8, 1, 2), where passengers land too. In by
// the front door, round the table to the stair, up to the bedroom.
export const WALK = [
  [6, 1, 0], [7, 1, 0], [8, 1, 0], [8, 1, 1], [9, 1, 1], [8, 1, 2],
  [7, 1, 1], [7, 1, 2], [7, 1, 3], [7, 1, 4], [7, 1, 5], [7, 1, 6], [7, 1, 7], [6, 1, 7], [5, 1, 7], [4, 1, 7], [3, 1, 7],
  [3, 2, 8], [4, 3, 8], [5, 4, 8], [6, 5, 8], [7, 6, 8], [8, 6, 8], [8, 6, 7], [9, 6, 6], [10, 6, 6],
]
