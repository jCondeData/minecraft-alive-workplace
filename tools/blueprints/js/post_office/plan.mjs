// Post Office, the plan: drawn before any wall (ROADMAP 49/51, tools/blueprints/briefs/post_office.md). Rooms, every
// furniture footprint (kind, cells, facing) and the walking line come first; recipe.mjs then assembles it from the
// parts library (../parts) in passes.
//
// Three rough concepts, before any block:
//   A. "The Coaching Stop": one long low brick hall (ridge along the street, 15 x 9) with an open timber arcade down its
//      front where the mail coach pulls up and parcels are handed across a counter under the arcade; a low hipped
//      roof. Story: the post as a stage on the road. Weak: flat and horizontal, nothing that reads from a distance,
//      and the open arcade is the Guildhall's move.
//   B. "The Signal Tower": a slender brick tower (5 x 5, a steep slate spire to y 14 with a copper weathervane) standing
//      forward in the middle of a long slate-roofed brick counter hall; the way in is through the tower's foot, the
//      Postman sleeps in the tower room over the door. Story: the round (64 blocks) is measured from this Mailbox, so
//      the building is the mark the round is walked from and back to; the tower is what a postman sees from the far
//      end of his round, and the tower room is where he watches for air mail coming in. Symmetric and civic: a public
//      building, not a house.
//   C. "The Sorting House": a narrow two-storey gable-to-street parcel depot with a hoist beam and a loading door in
//      the gable, the counter below and a sorting loft above. Story: a depot where parcels are lifted in. Weak: a
//      townhouse with a hoist (the Burgher's Townhouse and the canal warehouse already have both), and the Mailbox
//      would sit in a narrow room with little space for a player and a villager beside it.
// Strongest: B. It is the only one with a silhouette a player recognizes from across a village (a tower nearly twice
// the hall's ridge), and every part carries the job: the door in the tower leads straight down one axis to the Mailbox
// in the middle of the counter, the pigeonholes stand behind it, the Postman's bed is in the tower over the door.
//
// Coordinates: x across the front (west 0 -> east 16), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The front door is in the tower's north face, a stair step at y 0 before it.
//
// Masses (named parts the owner's feedback can point at):
//   Tower     the hero: brick walls x 6..10, z 1..5 (lobby below, the Postman's room above, floor y 5), stone quoins up its front corners,
//             a stone band at y 4 (the floor line), a stone doorway (jambs and lintel), a stone corbel cornice at y 9, a steep slate spire on the walls from y 10 to
//             a thin tile post at y 13, a copper weathervane (lightning rod) on it at y 14. No eave over the hall roof: the spire
//             sits on the walls and the cornice runs only round the front and sides.
//   Hall      the counter hall: brick walls x 1..15, z 5..13 (inside x 2..14, z 6..12), 4 high (y 1..4, a stone band at
//             y 4), a 45-degree slate roof with its ridge along x (y 9), eaves level with the wall top, gable ends at
//             x 0 and x 16 with dark barge stairs; exposed dark tie beams inside at y 5 (the lamps hang from them).
//   Chimney   a stone stack proud of the east gable (x 16, z 7), from the stove inside, up past the rake to y 10.
//   Back      the sorting room's long wall: stone piers proud of it between its three windows (x 6, x 10, carrying the
//             eave), a stone ledge proud under each window, the stone band at y 4 run round the whole hall.
//   Street    a red pillar box (decoration, not a Mailbox: the brief allows exactly one) by the west flank, a lamp
//             post by the east flank, a lantern on a bracket over the tower door (no hood: nothing roof-like under
//             the tower's cornice).

export const SIZE = [17, 15, 15] // w, h, d (limit 18 x 18 x 20)

// Palette (60/30/10): red brick (walls, tower) / dark slate (deepslate tiles: the roofs) / dressed stone (plinth,
// quoins, bands, sills, cornice) with white-painted (birch) door and shutters and a red pillar box. Why: a civic
// brick-and-slate post office reads at once as a public building and stands apart from the village's timber houses;
// the stone dressings draw its floor lines and corners, the white joinery lifts the brick.
export const PALETTE = {
  plinth: [['stone_bricks', 6], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1], ['andesite', 1]],
  brick: 'bricks',
  dressing: 'stone_bricks', dressingStairs: 'stone_brick_stairs', dressingSlab: 'stone_brick_slab',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  trim: 'dark_oak', // barge stairs, tie beams
  beam: 'dark_oak_log',
  joinery: 'birch', // the door and shutters, painted white
  floor: 'spruce_planks',
  lobbyFloor: 'polished_andesite',
  counter: 'dark_oak', counterTop: 'smooth_stone_slab',
  furniture: 'spruce', // chairs, tables, nightstand, bench
  pigeonholes: 'chiseled_bookshelf',
  glass: 'glass_pane',
  pillarBox: { body: 'red_concrete', cap: 'deepslate_tile_slab' },
  lampPost: 'dark_oak_fence',
  spirePost: 'deepslate_tile_wall', vane: 'lightning_rod',
}

export const MASSES = {
  tower: { x0: 6, x1: 10, z0: 1, z1: 5, top: 9, floorY: 5, spire: { cx: 8, cz: 3, r: 2, y0: 10 }, bands: [4], cornice: 9 },
  hall: {
    walls: { x0: 1, x1: 15, z0: 5, z1: 13 },
    room: { x0: 2, x1: 14, z0: 6, z1: 12 },
    wallTop: 4, band: 4, // the band runs round all four walls
    roof: { axis: 'x', across: [4, 14], along: [0, 16], eaveY: 4, steep: false }, // ridge y 9 at z 9
    tieBeams: { xs: [4, 12], z0: 6, z1: 12, y: 5 },
    piers: { xs: [6, 10], z: 14, y0: 0, y1: 3 }, // proud of the back wall, under the eave
  },
  chimney: { x: 16, z: 7, top: 10, breastTop: 3 },
}

export const ROOMS = [
  { name: 'Lobby', use: 'the way in through the tower, the ladder up', box: [7, 1, 2, 9, 4, 4] },
  { name: 'Counter hall', use: 'the public side (z 6..8): the Mailbox counter, a writing table, the bench by the stove', box: [2, 1, 6, 14, 4, 8] },
  { name: 'Sorting room', use: 'behind the counter (z 10..12): the pigeonholes, the sorting desk, the parcel chests', box: [2, 1, 10, 14, 4, 12] },
  { name: 'Tower room', use: "the Postman's bedroom over the door", box: [7, 6, 2, 9, 9, 4] },
]

// The job block: the Mailbox in the middle of the counter, its face to the public side; the open cell before it is
// (8, 1, 8), reached straight from the door; the counter's flaps are the open ends x 2..3 and x 13..14.
export const JOB = { block: 'aliveworkplace:mailbox', at: [8, 1, 9], facing: 'north', stand: [8, 1, 8] }

export const OPENINGS = {
  frontDoor: { at: [8, 1, 1], facing: 'south', step: [8, 0, 0] },
  hallDoorway: { at: [8, 1, 5], height: 2 },
  ladder: { x: 7, z: 4, y0: 1, y1: 5, facing: 'east' }, // against the lobby's west wall, up through the tower floor
  // windows: cells of glass, the side they look out of, and their dressing: 'sill' (a stone ledge on the wall face,
  // pots on it), 'shutters' (white, the tower's front), 'stone' (a dressed sill flush in the wall under the glass;
  // the band or a stone lintel over it); 'stone+ledge' that and a stone ledge proud of it (the back, the sorting
  // room's long wall, between the piers)
  windows: [
    { name: 'hall front west', cells: [[2, 2, 5], [3, 2, 5], [4, 2, 5], [2, 3, 5], [3, 3, 5], [4, 3, 5]], out: 'north', dress: 'sill' },
    { name: 'hall front east', cells: [[12, 2, 5], [13, 2, 5], [14, 2, 5], [12, 3, 5], [13, 3, 5], [14, 3, 5]], out: 'north', dress: 'sill' },
    { name: 'tower room front', cells: [[8, 6, 1], [8, 7, 1]], out: 'north', dress: 'shutters' },
    // the tower's side windows: a stone sill flush in the wall, no shutters (they would hang over the hall roof)
    { name: 'tower room west', cells: [[6, 6, 3], [6, 7, 3]], out: 'west', dress: 'stone' },
    { name: 'tower room east', cells: [[10, 6, 3], [10, 7, 3]], out: 'east', dress: 'stone' },
    { name: 'hall west front', cells: [[1, 2, 7], [1, 3, 7]], out: 'west', dress: 'stone' },
    { name: 'hall west back', cells: [[1, 2, 11], [1, 3, 11]], out: 'west', dress: 'stone' },
    { name: 'hall east back', cells: [[15, 2, 11], [15, 3, 11]], out: 'east', dress: 'stone' },
    { name: 'hall back west', cells: [[4, 2, 13], [4, 3, 13]], out: 'south', dress: 'stone+ledge' },
    { name: 'hall back middle', cells: [[8, 2, 13], [8, 3, 13]], out: 'south', dress: 'stone+ledge' },
    { name: 'hall back east', cells: [[12, 2, 13], [12, 3, 13]], out: 'south', dress: 'stone+ledge' },
    { name: 'west gable', cells: [[1, 6, 9], [1, 7, 9]], out: 'west', dress: null },
    { name: 'east gable', cells: [[15, 6, 10], [15, 7, 10]], out: 'east', dress: null },
  ],
}

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a furniture spot) or a fixture
// (a vanilla block the game uses as itself: chest, furnace). part: the furnitureParts part that draws it (or this
// family's own: 'pigeonholes'). facing: the way a person using it faces. Nothing covers the Mailbox.
const C = PALETTE.counter
export const FURNITURE = [
  // Counter hall, the counter: four counters each side of the Mailbox (x 4..7 and 9..12 at z 9), its ends open
  ...[4, 5, 6, 7, 9, 10, 11, 12].map(x => ({ room: 'Counter hall', kind: 'counter', part: 'counter', pos: [x, 1, 9], facing: 'north', wood: C, top: PALETTE.counterTop })),
  // the public side: a writing table with two chairs under the west front window, the bench by the stove, a mat at the
  // counter, the stove on the east wall, two lamps from the tie beams and one from the ridge over the Mailbox
  { room: 'Counter hall', kind: 'table', part: 'table', pos: [3, 1, 7], facing: 'north', wood: 'spruce', colour: 'white' },
  { room: 'Counter hall', kind: 'chair', part: 'chair', pos: [2, 1, 7], facing: 'east', wood: 'spruce' },
  { room: 'Counter hall', kind: 'chair', part: 'chair', pos: [4, 1, 7], facing: 'west', wood: 'spruce' },
  { room: 'Counter hall', kind: 'couch', part: 'couch', pos: [13, 1, 6], facing: 'south', length: 2, wood: 'spruce', colour: 'blue' },
  { room: 'Counter hall', kind: 'fixture', part: 'stove', pos: [14, 1, 8], facing: 'west', block: 'furnace' },
  { room: 'Counter hall', kind: 'rug', part: 'rug', cellsAbs: [[7, 1, 8], [8, 1, 8], [9, 1, 8]], colour: 'blue' },
  { room: 'Counter hall', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 7], facing: 'north', wood: 'spruce' },
  { room: 'Counter hall', kind: 'lamp', part: 'hangingLamp', pos: [12, 4, 7], facing: 'north', wood: 'spruce' },
  { room: 'Counter hall', kind: 'lamp', part: 'hangingLamp', pos: [8, 5, 9], facing: 'north', chain: 3, wood: 'spruce' },
  { room: 'Counter hall', kind: 'picture', part: 'picture', pos: [6, 3, 6], facing: 'south', wood: 'spruce', colour: 'red' },
  { room: 'Counter hall', kind: 'picture', part: 'picture', pos: [10, 3, 6], facing: 'south', wood: 'spruce', colour: 'red' },
  // Sorting room: pigeonholes on the back wall either side of the sorting desk (a table under the back window, its
  // chair facing it), a parcel chest in each back corner, the round's map in banners over the pigeonholes, two lamps
  { room: 'Sorting room', kind: 'fixture', part: 'pigeonholes', cells: [[5, 12], [6, 12], [7, 12], [9, 12], [10, 12], [11, 12]], y0: 1, y1: 2, facing: 'north' },
  { room: 'Sorting room', kind: 'table', part: 'table', pos: [8, 1, 12], facing: 'south', wood: 'spruce', colour: 'light_gray' },
  { room: 'Sorting room', kind: 'chair', part: 'chair', pos: [8, 1, 11], facing: 'south', wood: 'spruce' },
  { room: 'Sorting room', kind: 'fixture', part: 'chest', pos: [2, 1, 12], facing: 'north', block: 'chest' },
  { room: 'Sorting room', kind: 'fixture', part: 'chest', pos: [14, 1, 12], facing: 'north', block: 'chest' },
  { room: 'Sorting room', kind: 'picture', part: 'mapWall', pos: [5, 4, 12], along: 'east', facing: 'north', colours: ['light_blue', 'lime', 'green'], wood: 'spruce' },
  { room: 'Sorting room', kind: 'picture', part: 'mapWall', pos: [9, 4, 12], along: 'east', facing: 'north', colours: ['green', 'lime', 'light_blue'], wood: 'spruce' },
  { room: 'Sorting room', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 11], facing: 'north', wood: 'spruce' },
  { room: 'Sorting room', kind: 'lamp', part: 'hangingLamp', pos: [12, 4, 11], facing: 'north', wood: 'spruce' },
  // Lobby: the notice board (a banner) facing the door, a lamp
  { room: 'Lobby', kind: 'picture', part: 'picture', pos: [9, 3, 4], facing: 'west', wood: 'spruce', colour: 'white' },
  { room: 'Lobby', kind: 'lamp', part: 'hangingLamp', pos: [8, 4, 3], facing: 'north', wood: 'spruce' },
  // Tower room: the Postman's bed against the front wall beside the window, a nightstand, his chest, a lamp
  { room: 'Tower room', kind: 'bed', part: 'bed', pos: [7, 6, 3], facing: 'north', colour: 'red' },
  { room: 'Tower room', kind: 'nightstand', part: 'nightstand', pos: [9, 6, 2], facing: 'south', wood: 'spruce' },
  { room: 'Tower room', kind: 'fixture', part: 'chest', pos: [9, 6, 4], facing: 'west', block: 'chest' },
  { room: 'Tower room', kind: 'lamp', part: 'hangingLamp', pos: [8, 9, 3], facing: 'north', wood: 'spruce' },
]

// The walking line: the waiting Postman at (6, 0, -3) -> the step (8, 0, 0) -> the tower door -> the lobby -> the
// doorway into the hall -> the open cell before the Mailbox; round the counter's west end to the sorting room; up the
// ladder to the tower room.
export const WALK = [
  [8, 0, 0], [8, 1, 1], [8, 1, 2], [8, 1, 3], [8, 1, 4], [8, 1, 5], [8, 1, 6], [8, 1, 7], [8, 1, 8],
  [7, 1, 8], [6, 1, 8], [5, 1, 8], [3, 1, 8], [3, 1, 9], [3, 1, 10], [5, 1, 10], [8, 1, 10],
  [7, 1, 4], [7, 5, 4], [7, 6, 4], [8, 6, 3], [8, 6, 2],
]

// Outside: the pillar box on a stone pad before the west flank, a lamp post before the east flank, pots on the front
// sills.
export const OUTSIDE = {
  pillarBox: [3, 1, 2],
  lampPost: [13, 1, 2],
  pots: { 'hall front west': ['potted_red_tulip', 'potted_azure_bluet', 'potted_red_tulip'], 'hall front east': ['potted_red_tulip', 'potted_azure_bluet', 'potted_red_tulip'] },
}
