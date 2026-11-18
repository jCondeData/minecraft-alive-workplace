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
// it fits 9 x 9 so its height ratio is easy and strong (23 tall: 2.5x), and a ladder shaft leaves every floor clear
// for the rooms. B's round plan wastes the 9 x 9's corners on a 5-wide room and a spiral costs half the floor; C splits
// the build into a hall and a chimney-thin tower that reads as a church. A, the bold version: a crown that overhangs
// on corbels, a compass rose inlaid round a lodestone in the chart-room floor with its north arm pointing at the door
// (the front faces north), and a lightning-rod vane on the apex.
//
// Coordinates: x across the front (west 0 -> east 8), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Door on the north side.
//
// Masses:
//   Foot      a splayed stone skirt round the shaft at y 1 (stairs leaning on the wall), the ring y 0 under it.
//   Shaft     stone x 1..7, z 1..7, y 1..12, dark quoins on its corners; three floors inside (y 0, 5, 10).
//   Corbels   upside-down stairs round the ring x 0..8, z 0..8 at y 12, carrying the deck.
//   Deck      the lookout floor at y 13, x 0..8, z 0..8: one block proud of the shaft all round.
//   Lantern   a glazed lantern room on the deck, x 1..7, z 1..7, y 14..16 (posts at its corners, glass between), the
//             beacon lantern hanging in the middle from the apex; a door out onto the gallery.
//   Gallery   the deck's one-block ring outside the lantern, under the roof's overhang: a fence rail and corner posts.
//   Roof      a tented (pyramid) dark tile roof from y 17 to its apex at (4, 21, 4), a lightning-rod vane on top.

export const SIZE = [9, 23, 9] // w, h, d (plot 16 x 26 x 20; tower rule: 23 >= 9 + 4)

// Palette (60/30/10): stone-brick shaft / spruce joinery + deepslate tiles and quoins / cyan and white rose, blue
// banners, copper-free light. Why: a grey stone shaft reads as old and solid next to the village's wooden houses; the
// dark deepslate crown and quoins draw the eye up to the lookout; the cyan rose is the one bright thing, kept inside.
export const PALETTE = {
  stone: [['stone_bricks', 7], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1], ['andesite', 1]],
  quoin: 'deepslate_bricks', quoinAlt: 'polished_deepslate',
  skirt: 'stone_brick_stairs',
  corbel: 'deepslate_brick_stairs', // a dark stone corbel table under the timber deck
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'spruce_planks',
  deck: 'spruce_planks',
  post: 'stripped_spruce_log',
  wood: 'spruce',
  glass: 'glass_pane',
  rose: { heart: 'lodestone', arm: 'cyan_terracotta', diag: 'white_terracotta' },
  vane: 'lightning_rod',
}

export const MASSES = {
  shaft: { x0: 1, x1: 7, z0: 1, z1: 7, y0: 1, y1: 12 },
  room: { x0: 2, x1: 6, z0: 2, z1: 6 },
  floors: [0, 5, 10], // the floor layers inside
  corbelY: 12, deckY: 13,
  lookout: { y0: 14, y1: 16 }, lantern: { x0: 1, z0: 2, x1: 7, z1: 7 }, // set back on the north: a two-deep balcony at the door
  roof: { x0: 0, z0: 0, x1: 8, z1: 8, y: 17 },
}

export const ROOMS = [
  { name: 'Chart Room', use: 'the cartographer works at his table over the compass rose', box: [2, 1, 2, 6, 4, 6] },
  { name: 'Map Store', use: 'shelves of rolled charts, a drafting desk', box: [2, 6, 2, 6, 9, 6] },
  { name: 'Watch Room', use: 'a low room with a window each way and the log-book desk', box: [2, 11, 2, 6, 12, 6] },
  { name: 'Lantern', use: 'the glazed lookout room under the beacon lantern', box: [2, 14, 2, 6, 16, 6] },
  { name: 'Gallery', use: 'the walk round the lantern, under the eaves', box: [0, 14, 0, 8, 16, 8] },
]

export const OPENINGS = {
  frontDoor: { at: [4, 1, 1], facing: 'south', step: [4, 0, 0] },
  ladder: { x: 6, z: 6, y0: 1, y1: 13, facing: 'west' }, // on the east wall's inside, through every floor and the deck
  galleryDoor: { at: [4, 14, 2], facing: 'south' }, // onto the balcony (z 0..1)
  windows: [
    { name: 'chart west', cells: [[1, 2, 4], [1, 3, 4]], out: 'west', dress: 'shutters' },
    { name: 'chart east', cells: [[7, 2, 3], [7, 3, 3]], out: 'east', dress: 'shutters' },
    { name: 'store north', cells: [[4, 7, 1], [4, 8, 1]], out: 'north', dress: 'shutters' },
    { name: 'store south', cells: [[4, 7, 7], [4, 8, 7]], out: 'south', dress: 'shutters' },
    { name: 'store west', cells: [[1, 7, 4], [1, 8, 4]], out: 'west', dress: 'shutters' },
    { name: 'store east', cells: [[7, 7, 3], [7, 8, 3]], out: 'east', dress: 'shutters' },
    { name: 'watch north', cells: [[3, 11, 1], [5, 11, 1]], out: 'north', dress: null },
    { name: 'watch south', cells: [[3, 11, 7], [5, 11, 7]], out: 'south', dress: null },
    { name: 'watch west', cells: [[1, 11, 3], [1, 11, 5]], out: 'west', dress: null },
    { name: 'watch east', cells: [[7, 11, 3], [7, 11, 5]], out: 'east', dress: null },
  ],
}

// Every piece of furniture, footprint first (the kinds and parts as house_t1_b's plan).
export const FURNITURE = [
  // Chart Room: the cartography table against the back wall, facing the door across the rose; a shelf cupboard each
  // side of it, a blue banner, a lantern from the beam
  { room: 'Chart Room', kind: 'fixture', part: 'jobBlock', pos: [4, 1, 6], facing: 'north', block: 'cartography_table' },
  { room: 'Chart Room', kind: 'cupboard', part: 'cupboard', pos: [2, 1, 6], facing: 'east', wood: 'spruce' },
  { room: 'Chart Room', kind: 'cupboard', part: 'cupboard', pos: [2, 1, 5], facing: 'east', wood: 'spruce' },
  { room: 'Chart Room', kind: 'picture', part: 'picture', pos: [2, 3, 2], facing: 'east', wood: 'spruce', colour: 'blue' },
  { room: 'Chart Room', kind: 'picture', part: 'picture', pos: [6, 3, 2], facing: 'west', wood: 'spruce', colour: 'cyan' },
  { room: 'Chart Room', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 3], facing: 'north', wood: 'spruce' },
  // Map Store: a wall of shelves on the west, the drafting desk under the south window, a lamp by the ladder
  { room: 'Map Store', kind: 'shelf', part: 'shelves', cells: [[2, 2], [2, 3], [2, 5], [2, 6]], y0: 6, y1: 7 },
  { room: 'Map Store', kind: 'desk', part: 'desk', pos: [4, 6, 6], facing: 'south', wood: 'spruce' },
  { room: 'Map Store', kind: 'lamp', part: 'tableLamp', pos: [6, 6, 2], facing: 'west', wood: 'spruce' },
  { room: 'Map Store', kind: 'rug', part: 'rug', cellsAbs: [[3, 6, 3], [4, 6, 3], [5, 6, 3], [3, 6, 4], [4, 6, 4], [5, 6, 4]], colour: 'light_blue' },
  { room: 'Map Store', kind: 'lamp', part: 'hangingLamp', pos: [4, 9, 4], facing: 'north', wood: 'spruce' },
  // Watch Room: the log-book desk under the west window
  { room: 'Watch Room', kind: 'desk', part: 'desk', pos: [2, 11, 4], facing: 'west', wood: 'spruce' },
  { room: 'Watch Room', kind: 'fixture', part: 'chest', pos: [2, 11, 6], facing: 'east', block: 'chest' },
  { room: 'Watch Room', kind: 'lamp', part: 'tableLamp', pos: [5, 11, 2], facing: 'south', wood: 'spruce' },
  { room: 'Watch Room', kind: 'picture', part: 'picture', pos: [4, 12, 6], facing: 'north', wood: 'spruce', colour: 'blue' },
  // Lookout: the beacon lantern hanging from the apex on a long chain
  { room: 'Lantern', kind: 'lamp', part: 'hangingLamp', pos: [4, 16, 4], facing: 'north', chain: 4, wood: 'spruce' },
]

// The walking line: the step -> the door -> across the rose to the table; up the ladder to every floor and the deck.
export const WALK = [
  [4, 0, 0], [4, 1, 1], [4, 1, 2], [4, 1, 3], [4, 1, 4], [4, 1, 5], // at the table
  [5, 1, 5], [6, 1, 6], [6, 6, 6], [5, 6, 5], [6, 11, 6], [5, 11, 5], [6, 14, 6], [4, 14, 3], [4, 14, 2], [4, 14, 1], // up to the lantern and out onto the gallery
]
