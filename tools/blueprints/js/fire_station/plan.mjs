// Fire Station, the plan: drawn before any wall. Rooms, every furniture footprint (kind, cells, facing) and the walking
// line come first; recipe.mjs then assembles the station from the parts library's (../parts) named parts to fit it.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Hose Tower. A stone engine house with its gable to the street and a timber watch loft jettied out over the
//      engine doors, one 45-degree tile roof, and a tall square hose tower at the front corner rising past the ridge
//      to a corbelled lookout gallery under a hip cap; trough, racks and grindstone in the engine bay, the ladder up
//      the tower. Story: the Firewarden needs to SEE smoke over the village's roofs before he can run to it.
//   B. The Cistern Hall. A long, low stone hall over a rain cistern, its eaves spouting into a trough under an open
//      front colonnade, a glazed timber clerestory (the watch loft) riding the ridge like a lantern. Story: water
//      first; a fire station is a store of water. Civic and calm, but it reads flat from 20 blocks and the "watch" is
//      barely above the eaves.
//   C. The Watch Gate. Two stone piers (the grindstone room one side, the cistern the other) with the timber loft
//      bridging over a cart passage between them under a cross gable. Story: the brigade runs out both ways at once.
//      Striking, but its passage is open air through the building, and it splits the Firewarden's work in two rooms.
// Strongest: A. It is the only one whose outline says what the building does (a tower to watch from, a house to run
// out of), from every side and from far off; the tower carries the ladder the brief asks for, the lookout looks over
// every roof the Firewarden guards, and the jettied loft over the doors makes the front read as a station, not a
// house. B is the safest (one box, one roof); C is a gatehouse wearing a fire station's clothes.
//
// Story of A, "the Hose Tower": the village raises it after its first fire. Stone below, where the water and the
// sparks are; timber above, where the watch sits dry; a red tile roof the colour of the alarm. The tower was built to
// hang wet hoses to dry and to keep a man above the roofs; it carries the village's lightning rod, and a copper
// downpipe brings the roof's rain into the trough the buckets are filled at.
//
// Coordinates: x across the front (west 0 -> east 14), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The engine doors face north, three leaves under a segmental arch, a step before them.
//
// Masses (named parts the owner's feedback can point at):
//   Engine house  stone-brick ground floor x 1..9, z 3..13 (walls y 1..4) on a dark footing a block wider, the bay
//                 inside x 2..8, z 4..12. Its floor at y 0.
//   Watch loft    the timber storey over it (floor y 5, plank walls y 6..7 between dark posts), jettied a block out over
//                 the engine doors (front gable at z 2, on brackets), its big watch window in the front gable.
//   Roof          one 45-degree red tile roof, ridge along z (gable to the street), eaves y 7 at x 0 and 10, ridge y 12.
//   Hose tower    x 9..13, z 1..5 (shaft y 1..10, sharing the engine house's east wall), proud of the loft by one and
//                 of the stone front by two; the ladder inside from the ground past the loft's landing (y 5) to the
//                 lookout.
//   Lookout       a gallery on a corbel ring at y 10: deck y 11 (7 x 7), rail, four posts, ceiling y 14, a hip cap to
//                 y 16 with the lightning rod on top at y 17: the hero, 5 blocks over the ridge.
//   Downpipe      copper (lightning rods) down the west wall at z 8 into the trough's spout inside.

export const SIZE = [15, 18, 16] // w, h, d (plot limit 18 x 18 x 22)

// Palette (60/30/10): stone bricks (the engine house and tower, cracked in clusters, mossy at the wet foot) /
// spruce planks between dark oak posts (the loft and its gables) under a red brick-tile roof / accents: red (banners,
// the hose), copper (the downpipe and the rod), iron lanterns. Darkest at the foot (cobbled deepslate footing,
// deepslate-brick quoins), lighter going up, the red roof against grey stone and brown timber.
export const PALETTE = {
  stone: [['stone_bricks', 8], ['cracked_stone_bricks', 3]],
  wetStone: 'mossy_stone_bricks', // the trough's rim and the wall's foot by it
  footing: [['cobbled_deepslate', 5], ['deepslate_bricks', 1]],
  quoin: ['deepslate_bricks', 'polished_deepslate'],
  floor: [['stone', 3], ['andesite', 2], ['polished_andesite', 2]], // the engine bay's flags
  frame: 'dark_oak_log',
  infill: 'spruce_planks', // the loft's walls (the test counts them in y 6..7)
  loftFloor: 'spruce_planks',
  roof: { full: 'bricks', stairs: 'brick_stairs', slab: 'brick_slab' },
  trim: 'dark_oak', // barge boards, brackets, wheels
  wood: 'spruce', // doors, shelves (spruce slabs: the test's racks), fences, chairs
  stoneStairs: 'stone_brick_stairs',
  stoneSlab: 'stone_brick_slab',
  key: 'chiseled_stone_bricks',
  glass: 'glass_pane',
  copper: 'lightning_rod',
  accent: 'red',
}

export const MASSES = {
  engine: {
    walls: { x0: 1, x1: 9, z0: 3, z1: 13 },
    bay: { x0: 2, x1: 8, z0: 4, z1: 12 },
    wallTop: 4,
    footing: { x0: 0, x1: 10, z0: 2, z1: 14 },
  },
  loft: {
    beam: { x0: 1, x1: 9, z0: 2, z1: 13, y: 5 }, // the loft's sill beam ring, the jetty's front beam at z 2
    floor: { x0: 2, x1: 8, z0: 3, z1: 12 },
    wallY: [6, 7],
    // frame posts along each loft wall (the bays between them get planks or glass)
    frontPosts: [1, 3, 7], // x on z 2 (x 9 is the tower)
    backPosts: [1, 3, 7, 9], // x on z 13
    westPosts: [2, 6, 9, 13], // z on x 1
    eastPosts: [6, 9, 13], // z on x 9 (z 2..5 is the tower)
    brackets: [1, 3, 7], // x under the jetty beam (z 2, y 4)
    tieBeams: { z: [5, 10], y: 8, x0: 2, x1: 8 },
  },
  roof: { axis: 'z', across: [0, 10], along: [1, 14], eaveY: 7, steep: false }, // ridge y 12 at x 5
  tower: {
    walls: { x0: 9, x1: 13, z0: 1, z1: 5 }, inside: { x0: 10, x1: 12, z0: 2, z1: 4 },
    centre: [11, 3], half: 2, shaftTop: 10, landingY: 5,
    footing: { x0: 8, x1: 14, z0: 0, z1: 6 },
  },
}

export const ROOMS = [
  { name: 'Engine bay', use: 'the Firewarden\'s grindstone, the trough he fills his buckets at, the pail racks, the hose reel, the crew\'s table', box: [2, 1, 4, 8, 4, 12] },
  { name: 'Tower foot', use: 'the ladder\'s foot, off the bay', box: [10, 1, 2, 12, 4, 4] },
  { name: 'Watch loft', use: 'the night watch: a bench at the front window, the map table, the log desk', box: [2, 6, 3, 8, 11, 12] },
  { name: 'Lookout', use: 'the gallery over the roofs, open on four sides', box: [8, 12, 0, 14, 13, 6] },
]

export const OPENINGS = {
  engineDoors: { cells: [[4, 1, 3], [5, 1, 3], [6, 1, 3]], facing: 'south', hinges: ['right', 'left', 'left'], step: 2 },
  arch: { a0: 4, y: 3, line: 3 },
  towerDoorway: { at: [9, 1, 4], height: 2 }, // the bay to the tower's foot
  loftDoorway: { at: [9, 6, 3], height: 2 }, // the tower's landing to the loft
  ladder: { x: 11, z: 4, y0: 1, y1: 11, facing: 'north' }, // on the tower's back wall (z 5), up to a hatch in the deck at y1
  windows: [
    // ground floor: one-wide, two-high lights with stone sills on the footing
    { name: 'bay front west', cells: [[2, 2, 3], [2, 3, 3]], out: 'north', dress: 'sill' },
    { name: 'bay front east', cells: [[8, 2, 3], [8, 3, 3]], out: 'north', dress: 'sill' },
    { name: 'bay west (grindstone)', cells: [[1, 2, 5], [1, 3, 5]], out: 'west', dress: 'sill' },
    { name: 'bay west (table)', cells: [[1, 2, 11], [1, 3, 11]], out: 'west', dress: 'sill' },
    { name: 'bay east', cells: [[9, 2, 11], [9, 3, 11]], out: 'east', dress: 'sill' },
    { name: 'bay back west', cells: [[3, 2, 13], [3, 3, 13]], out: 'south', dress: 'sill' },
    { name: 'bay back east', cells: [[7, 2, 13], [7, 3, 13]], out: 'south', dress: 'sill' },
    // the loft: the watch window in the front gable, a light in each side's middle bay, the back gable's pair
    { name: 'watch window', cells: [[4, 6, 2], [5, 6, 2], [6, 6, 2], [4, 7, 2], [5, 7, 2], [6, 7, 2]], out: 'north', dress: null },
    { name: 'front gable light', cells: [[5, 9, 2], [5, 10, 2]], out: 'north', dress: null },
    { name: 'loft west', cells: [[1, 6, 7], [1, 7, 7], [1, 6, 8], [1, 7, 8]], out: 'west', dress: null },
    { name: 'loft east', cells: [[9, 6, 7], [9, 7, 7], [9, 6, 8], [9, 7, 8]], out: 'east', dress: null },
    { name: 'back gable', cells: [[4, 6, 13], [4, 7, 13], [6, 6, 13], [6, 7, 13]], out: 'south', dress: null },
    { name: 'back gable light', cells: [[5, 9, 13], [5, 10, 13]], out: 'south', dress: null },
    // the tower: stacked lights on the front and east faces
    { name: 'tower front low', cells: [[11, 2, 1], [11, 3, 1]], out: 'north', dress: 'sill' },
    { name: 'tower front high', cells: [[11, 6, 1], [11, 7, 1]], out: 'north', dress: null },
    { name: 'tower east low', cells: [[13, 2, 3], [13, 3, 3]], out: 'east', dress: 'sill' },
    { name: 'tower east high', cells: [[13, 7, 3], [13, 8, 3]], out: 'east', dress: null },
  ],
}

// The Firewarden's work: the grindstone, the trough (all the drawing's water: the nearest to the grindstone, and he
// stands in the bay beside its rim), the pail racks (empty flower pots on top spruce slabs).
export const WORK = {
  grindstone: { at: [2, 1, 5], facing: 'east' },
  standing: [4, 1, 5], // a free floor cell 2 blocks from the grindstone, at the trough's rim
  trough: { cells: [[2, 7], [3, 7], [2, 8], [3, 8]], y: 1 }, // 4 sources: 4 water buckets on the list
  spout: [2, 3, 8],
  downpipe: { x: 0, z: 8, y0: 1, y1: 6 },
  racks: { cells: [[8, 6], [8, 7], [8, 8]], tiers: [1, 3], ends: [[8, 5], [8, 9]], postTop: 4 }, // 6 pails
  hoseReel: { at: [5, 1, 12], axle: 'x', pull: 'north' },
}

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a furniture spot) or a fixture.
// part: the furnitureParts part that draws it. facing: the way a person using it faces.
export const FURNITURE = [
  // Engine bay: the crew's table in the back west corner (two chairs), lamps from the loft's joists, a banner
  { room: 'Engine bay', kind: 'table', part: 'table', pos: [3, 1, 11], facing: 'north', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce', colour: 'red' },
  { room: 'Engine bay', kind: 'chair', part: 'chair', pos: [2, 1, 11], facing: 'east', wood: 'spruce' },
  { room: 'Engine bay', kind: 'chair', part: 'chair', pos: [3, 1, 12], facing: 'north', wood: 'spruce' },
  { room: 'Engine bay', kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 11], facing: 'north', wood: 'spruce' },
  { room: 'Engine bay', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 6], facing: 'north', wood: 'spruce' },
  { room: 'Engine bay', kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 10], facing: 'north', wood: 'spruce' },
  { room: 'Engine bay', kind: 'lamp', part: 'tableLamp', pos: [8, 1, 12], facing: 'west', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Engine bay', kind: 'picture', part: 'picture', pos: [7, 3, 12], facing: 'north', wood: 'spruce', colour: 'red' },
  { room: 'Tower foot', kind: 'lamp', part: 'hangingLamp', pos: [10, 4, 2], facing: 'north', wood: 'spruce' },
  // Watch loft: the bench under the watch window (sitters look out), the map table, the log desk, lamps from the ridge
  { room: 'Watch loft', kind: 'couch', part: 'couch', pos: [4, 6, 3], facing: 'north', length: 3, wood: 'spruce', colour: 'red' },
  { room: 'Watch loft', kind: 'table', part: 'table', pos: [5, 6, 7], facing: 'north', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce', colour: 'white' },
  { room: 'Watch loft', kind: 'chair', part: 'chair', pos: [4, 6, 7], facing: 'east', wood: 'spruce' },
  { room: 'Watch loft', kind: 'chair', part: 'chair', pos: [6, 6, 7], facing: 'west', wood: 'spruce' },
  { room: 'Watch loft', kind: 'rug', part: 'rug', cellsAbs: [[4, 6, 8], [5, 6, 8], [6, 6, 8], [4, 6, 9], [5, 6, 9], [6, 6, 9]], colour: 'red' },
  { room: 'Watch loft', kind: 'desk', part: 'desk', pos: [2, 6, 11], facing: 'west', wood: 'spruce' },
  { room: 'Watch loft', kind: 'chair', part: 'chair', pos: [3, 6, 11], facing: 'west', wood: 'spruce' },
  { room: 'Watch loft', kind: 'lamp', part: 'tableLamp', pos: [8, 6, 11], facing: 'west', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Watch loft', kind: 'picture', part: 'picture', pos: [2, 7, 4], facing: 'east', wood: 'spruce', colour: 'white' },
  { room: 'Watch loft', kind: 'lamp', part: 'hangingLamp', pos: [5, 9, 7], facing: 'north', chain: 2, wood: 'spruce' },
  { room: 'Watch loft', kind: 'lamp', part: 'hangingLamp', pos: [5, 9, 11], facing: 'north', chain: 2, wood: 'spruce' },
  // the tower's landing and the lookout
  { room: 'Tower foot', kind: 'lamp', part: 'hangingLamp', pos: [10, 10, 2], facing: 'north', wood: 'spruce' },
  { room: 'Lookout', kind: 'lamp', part: 'hangingLamp', pos: [11, 13, 3], facing: 'north', wood: 'spruce' },
]

// The walking line: the step -> the engine doors -> the bay's middle aisle (the grindstone and trough to the west,
// the racks to the east, the reel and table at the back) -> the tower's doorway -> up the ladder -> the landing ->
// the loft's doorway -> the watch window; and on up to the lookout.
export const WALK = [
  [5, 0, 2], [5, 1, 3], [5, 1, 4], [4, 1, 5], [5, 1, 6], [5, 1, 9], [6, 1, 4], [7, 1, 4], [8, 1, 4], [9, 1, 4], [10, 1, 4],
  [11, 1, 4], [11, 6, 4], [10, 6, 4], [10, 6, 3], [9, 6, 3], [8, 6, 3], [7, 6, 4], [5, 6, 5],
  [11, 11, 4], [11, 12, 3],
]

// Outside: the step across the doors, twin lanterns under the jetty over them, a lamp post at the front west corner,
// the company's red banner on the tower's front under the gallery.
export const OUTSIDE = {
  doorLamps: [[5, 4, 2]],
  lampPost: [0, 1, 1],
  banner: { at: [11, 9, 0], facing: 'north' },
  sideBanner: { at: [14, 9, 3], facing: 'east' },
}
