// Map Room, the plan: drawn before any wall (tools/blueprints/briefs/map_room.md, REBUILD.md "The kit").
//
// Three rough concepts first:
//   A. "Lantern Tower": a square stone shaft, a timber lookout jettied out on corbels at the top under a dark tented
//      roof with a beacon lantern hanging in it; ladder up the inside. Story: the cartographer keeps a light burning
//      so travelers find the village, and charts the land from the deck by day.
//   B. "Round Beacon": a round tower (octagon in plan) with a conical roof and a spiral stair round a core, a lighthouse
//      on dry land. Story: a surveyor's beacon turned into a home for maps.
//   C. "Drafting Wing": a low chart-room hall with a slim stair tower rising off one corner to a belvedere. Story: the
//      office came first and the tower was added to see further.
// Strongest: A. The brief's soul is "a tall narrow tower with a lookout at the top": A is the only one where the
// lookout is the hero (a jettied crown wider than the shaft reads as "look out from here" from across the village),
// and a ladder shaft leaves every floor clear for the rooms. B's round plan wastes the corners on a 5-wide room and a
// spiral costs half the floor; C splits the build into a hall and a chimney-thin tower that reads as a church.
//
// Round 3 (the critic's notes, all applied): the shaft gets depth (2 x 2 dark corner piers standing one proud of each
// face, a corbelled belt course at each floor line, deep stone sills under the windows, a battered foot between the
// piers); the entrance is a feature (a three-wide, two-step stair plinth with cheeks up to a porch nestled between the
// front piers, under a tiled lean-to on posts); every floor is furnished for the cartographer (a map wall of banners
// over the table with a seat facing it, shelves of charts, a drafting desk and chair, the log-book desk and chair);
// and the lookout gets its chart table and timber brackets: a second tier under the jetty and knee braces under the
// eave beside every gallery post. The crown now overhangs two blocks past the wall, one past the piers.
//
// Coordinates: x across the front (west 0 -> east 10), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Door on the north side.
//
// Masses:
//   Plinth    y 0..1, the stair flight at z 1..2 (x 4..6), the porch deck x 3..7, z 3..4 at y 1; flower beds either side.
//   Footing   y 0 under the shaft and piers, x 1..9, z 4..12.
//   Shaft     stone walls x 2..8, z 5..11, y 1..13; inside x 3..7, z 6..10; floors at y 1, 6, 11.
//   Piers     2 x 2 dark piers at the four corners (x 1..2 / 8..9, z 4..5 / 11..12), y 1..13: one proud of each face.
//   Belts     upside-down stairs one proud of the wall between the piers at y 6 and y 11 (the floor lines).
//   Corbels   y 13 on the pier line between the piers; timber brackets one further out at y 13 under the deck's edge.
//   Deck      y 14, x 0..10, z 3..13: two proud of the wall all round, a two-deep balcony over the porch.
//   Lantern   glazed room x 1..9, z 5..12, y 15..17 (log posts and mullions, glass between), a door out to the balcony.
//   Gallery   the deck's ring outside the lantern: fence rail, log posts at corners and mid-sides, knee braces.
//   Roof      a tented deepslate roof over x 0..10, z 3..13 from y 18 to its apex (5, 23, 8), a lightning-rod vane.

export const SIZE = [11, 25, 14] // w, h, d (plot 16 x 26 x 20; tower rule: 25 >= 14 + 7; top quarter y >= 19: roof)

// Palette (60/30/10): stone-brick shaft / spruce joinery + deepslate piers, belts and tiles / blue, cyan and green
// banners, the lodestone rose. Why: a grey stone shaft reads as old and solid next to the village's wooden houses; the
// dark deepslate piers frame it and carry the eye up to the dark crown; the map colours are the one bright thing, kept
// inside where the cartographer works.
export const PALETTE = {
  stone: [['stone_bricks', 7], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1], ['andesite', 1]],
  pier: 'deepslate_bricks', pierAlt: 'polished_deepslate',
  belt: 'deepslate_brick_stairs', band: 'polished_deepslate',
  skirt: 'stone_brick_stairs', sill: 'stone_brick_stairs', cheek: 'mossy_stone_bricks', fill: 'stone_bricks',
  corbel: 'deepslate_brick_stairs',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'spruce_planks', deck: 'spruce_planks', porchDeck: 'polished_andesite',
  post: 'stripped_spruce_log', beam: 'stripped_spruce_log',
  wood: 'spruce',
  glass: 'glass_pane',
  rose: { heart: 'lodestone', arm: 'cyan_terracotta', diag: 'white_terracotta' },
  map: ['blue', 'light_blue', 'green', 'lime'], // sea, shallows, lowland, meadow: the map wall over the table
  vane: 'lightning_rod',
}

export const MASSES = {
  footing: { x0: 1, z0: 4, x1: 9, z1: 12 },
  shaft: { x0: 2, x1: 8, z0: 5, z1: 11, y0: 1, y1: 13 },
  room: { x0: 3, x1: 7, z0: 6, z1: 10 },
  piers: { x0: 1, z0: 4, x1: 9, z1: 12, y0: 1, y1: 13 },
  floors: [1, 6, 11], // the floor layers inside
  belts: [6, 11],
  corbelY: 13, deckY: 14,
  deck: { x0: 0, z0: 3, x1: 10, z1: 13 },
  lookout: { y0: 15, y1: 17 }, lantern: { x0: 1, z0: 5, x1: 9, z1: 12 },
  roof: { x0: 0, z0: 3, x1: 10, z1: 13, y: 18 },
  porch: { x0: 3, x1: 7, z0: 3, z1: 4, y: 1, topY: 4 },
  plinth: { x0: 4, width: 3, z: 1, steps: 2 },
}

export const ROOMS = [
  { name: 'Porch', use: 'the way in: a stair plinth up to a covered porch between the front piers', box: [3, 2, 3, 7, 4, 4] },
  { name: 'Chart Room', use: 'the cartographer works at his table under the map wall, over the compass rose', box: [3, 2, 6, 7, 5, 10] },
  { name: 'Map Store', use: 'a wall of chart shelves, the drafting desk and its chair', box: [3, 7, 6, 7, 10, 10] },
  { name: 'Watch Room', use: 'a window each way, the log-book desk and chair, the chest of instruments', box: [3, 12, 6, 7, 13, 10] },
  { name: 'Lantern', use: 'the glazed lookout under the beacon lantern, with the chart table and its chair', box: [2, 15, 6, 8, 17, 11] },
  { name: 'Gallery', use: 'the walk round the lantern under the eaves, a balcony over the porch', box: [0, 15, 3, 10, 17, 13] },
]

export const OPENINGS = {
  frontDoor: { at: [5, 2, 5], facing: 'south' },
  ladder: { x: 7, z: 10, y0: 2, y1: 14, facing: 'west' }, // on the east wall's inside, through every floor and the deck
  galleryDoor: { at: [5, 15, 5], facing: 'south' }, // out onto the balcony (z 3..4)
  windows: [
    { name: 'chart west', cells: [[2, 3, 7], [2, 4, 7]], out: 'west', dress: 'sill' },
    { name: 'chart east', cells: [[8, 3, 7], [8, 4, 7]], out: 'east', dress: 'sill' },
    { name: 'store north', cells: [[5, 8, 5], [5, 9, 5]], out: 'north', dress: 'shutters' },
    { name: 'store south', cells: [[5, 8, 11], [5, 9, 11]], out: 'south', dress: 'shutters' },
    { name: 'store west', cells: [[2, 8, 8], [2, 9, 8]], out: 'west', dress: 'shutters' },
    { name: 'store east', cells: [[8, 8, 7], [8, 9, 7]], out: 'east', dress: 'shutters' },
    { name: 'watch north', cells: [[5, 12, 5], [5, 13, 5]], out: 'north', dress: 'sill' },
    { name: 'watch south', cells: [[5, 12, 11], [5, 13, 11]], out: 'south', dress: 'sill' },
    { name: 'watch west', cells: [[2, 12, 8], [2, 13, 8]], out: 'west', dress: 'sill' },
    { name: 'watch east', cells: [[8, 12, 7], [8, 13, 7]], out: 'east', dress: 'sill' },
  ],
}

// Every piece of furniture, footprint first (the kinds and parts as house_t1_b's plan).
export const FURNITURE = [
  // Porch: a lantern hanging under the lean-to
  { room: 'Porch', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 4], facing: 'north', wood: 'spruce' },
  // Chart Room: the cartography table against the back wall under the map wall, a chair facing it; two chart
  // cupboards on the west wall; a lantern from the beam over the rose
  { room: 'Chart Room', kind: 'fixture', part: 'jobBlock', pos: [5, 2, 10], facing: 'north', block: 'cartography_table' },
  { room: 'Chart Room', kind: 'chair', part: 'chair', pos: [5, 2, 9], facing: 'south', wood: 'spruce' },
  { room: 'Chart Room', kind: 'picture', part: 'mapWall', pos: [3, 4, 10], along: 'east', facing: 'north', wood: 'spruce' },
  { room: 'Chart Room', kind: 'cupboard', part: 'cupboard', pos: [3, 2, 9], facing: 'east', wood: 'spruce' },
  { room: 'Chart Room', kind: 'cupboard', part: 'cupboard', pos: [3, 2, 8], facing: 'east', wood: 'spruce' },
  { room: 'Chart Room', kind: 'lamp', part: 'tableLamp', pos: [7, 2, 6], facing: 'west', wood: 'spruce' },
  { room: 'Chart Room', kind: 'lamp', part: 'hangingLamp', pos: [5, 5, 7], facing: 'north', wood: 'spruce' },
  // Map Store: a wall of chart shelves on the west either side of its window, the drafting desk under the south window with its chair, banners
  // either side of the north window, a rug, a lamp
  { room: 'Map Store', kind: 'shelf', part: 'shelves', cells: [[3, 6], [3, 7], [3, 9], [3, 10]], y0: 7, y1: 8 },
  { room: 'Map Store', kind: 'desk', part: 'desk', pos: [5, 7, 10], facing: 'north', wood: 'spruce' },
  { room: 'Map Store', kind: 'chair', part: 'chair', pos: [5, 7, 9], facing: 'south', wood: 'spruce' },
  { room: 'Map Store', kind: 'picture', part: 'picture', pos: [4, 9, 6], facing: 'south', wood: 'spruce', colour: 'cyan' },
  { room: 'Map Store', kind: 'picture', part: 'picture', pos: [6, 9, 6], facing: 'south', wood: 'spruce', colour: 'blue' },
  { room: 'Map Store', kind: 'rug', part: 'rug', cellsAbs: [[4, 7, 7], [5, 7, 7], [6, 7, 7], [4, 7, 8], [5, 7, 8], [6, 7, 8]], colour: 'light_blue' },
  { room: 'Map Store', kind: 'lamp', part: 'tableLamp', pos: [7, 7, 6], facing: 'west', wood: 'spruce' },
  { room: 'Map Store', kind: 'lamp', part: 'hangingLamp', pos: [5, 10, 8], facing: 'north', wood: 'spruce' },
  // Watch Room: the log-book desk under the west window with its chair, the instrument chest, a lamp, a banner
  { room: 'Watch Room', kind: 'desk', part: 'desk', pos: [3, 12, 7], facing: 'east', wood: 'spruce' },
  { room: 'Watch Room', kind: 'chair', part: 'chair', pos: [4, 12, 7], facing: 'west', wood: 'spruce' },
  { room: 'Watch Room', kind: 'fixture', part: 'chest', pos: [3, 12, 10], facing: 'east', block: 'chest' },
  { room: 'Watch Room', kind: 'lamp', part: 'tableLamp', pos: [7, 12, 6], facing: 'west', wood: 'spruce' },
  { room: 'Watch Room', kind: 'picture', part: 'picture', pos: [3, 13, 6], facing: 'south', wood: 'spruce', colour: 'blue' },
  // Lantern: the chart table by the west glass with its chair, the beacon lantern from the apex on a long chain
  { room: 'Lantern', kind: 'table', part: 'table', pos: [2, 15, 8], facing: 'east', wood: 'spruce', colour: 'white' },
  { room: 'Lantern', kind: 'chair', part: 'chair', pos: [3, 15, 8], facing: 'west', wood: 'spruce' },
  { room: 'Lantern', kind: 'lamp', part: 'hangingLamp', pos: [5, 17, 8], facing: 'north', chain: 5, wood: 'spruce' },
]

// The walking line: up the plinth -> the porch -> the door -> to the table's chair; up the ladder to every floor and
// the lantern, out onto the balcony.
export const WALK = [
  [5, 0, 0], [5, 0, 1], [5, 1, 2], [5, 2, 3], [5, 2, 4], [5, 2, 5], [5, 2, 6], [5, 2, 7], [5, 2, 8], // at the table
  [6, 2, 9], [7, 2, 10], [7, 7, 10], [6, 7, 9], [7, 12, 10], [6, 12, 9], [7, 15, 10], [5, 15, 6], [5, 15, 5], [5, 15, 4],
]
