// Mason's Yard (masons_yard, masons_yard_2), the plan: drawn before any wall. Brief: tools/blueprints/briefs/masons_yard.md.
//
// Three rough concepts (ROADMAP 49, REBUILD.md):
//   A. "Quarry Court": a square yard walled all round in rubble, a flat-roofed stone store in one corner, the
//      stonecutter out in the open on a raised banker. Plan: courtyard; roof: flat slab on the store. Story: a mason
//      who works under the sky. Weak: from the street it is a wall with a box behind it; no roof to read.
//   B. "Banker Shed under a Catslide" (built): a stone lodge whose roof runs on down at the front in one long low
//      slope over an open banker shed on timber posts, the stonecutters in the shed's bays facing a walled yard with a
//      jib crane and stock. Level II puts a tall timber-topped "tracing house" (the master's drawing loft) on the east
//      end, jettied over its door, its steep gable to the street. Plan: yard -> shed -> lodge, front to back; roof:
//      catslide (saltbox) plus, at II, a steep cross gable. Story: the medieval masons' lodge, where the bankers stood
//      in a lean-to out of the rain and the master drew templates on the tracing floor upstairs.
//   C. "Mason's Tower": a narrow stone tower with a hoist out of its top over a yard. Strong outline, but a tower
//      is a bad workplace: the stonecutters would sit in a tight base, and the 12,000-tick build is mostly scaffold.
// B is strongest: its catslide is a silhouette nobody mistakes for a house (tall at the back, low and open at the
// front), the open shed shows the work from the street, and Level II's tower-like gable gives the hero mass a real
// reason (the tracing floor) instead of being decoration. A is the safest (a wall and a box); C the showiest but worst
// to work in.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front (z = 0,
// north, the street) to the back. The way in is the gateway in the front wall (x 5..7, z 0).
//
// Masses (named parts):
//   Yard          x 1..11, z 0..4: a rubble wall (cobblestone wall) along the front and both sides, a 3-wide gateway
//                 between two lamp piers; gravel and chippings underfoot, a flagged path to the shed; the Jib Crane
//                 in its east corner with a block slung under the hook; a pile of rough stone in the west corner.
//   Banker Shed   the open lean-to, x 2..10, z 5..6: timber posts at x 4 and 8 on z 5, stone end walls; the Masons'
//                 stonecutters stand in its bays against the lodge wall (z 6), each Mason working from the bay (z 5).
//                 A purlin beam and soffit under the slope at z 6 keep it a shallow porch.
//   Lodge         the closed stone room behind, walls x 1..11, z 7..11: the Masons' chests, finished ashlar on stacks,
//                 a ledger desk; door in the middle of the shed's back wall.
//   Buttresses   three stepped rubble buttresses on the lodge's back (x 2, 6, 10, z 12), two on the tracing house's
//                 (x 13, 15, z 9): the back faces get light and shadow, the long back wall its bays.
//   Yard story    a mason's trestle (two spruce legs, a stone slab on them) by the crane, chippings (slabs) on
//                 the ground round the rough pile, a chiseled sample block on the gate's west pier.
//   Catslide      one deepslate-tile roof over shed and lodge: ridge along x over z 9 (y 8), the long front slope down
//                 to y 3 over the yard's edge (z 4), the short back slope to y 5 (z 12); stone gables with a window.
//   Tracing House (Level II) x 12..16, z 3..8 (its back stepped forward of the lodge's, its gable over the lodge roof): rubble ground floor (a stone store, door to the street), a jettied
//                 timber upper storey (the tracing floor, up a ladder), a steep tiled gable to the front, ridge y 14.

export const LEVELS = {
  1: { size: [13, 9, 13] },
  2: { size: [18, 15, 13] },
}

// Palette (60/30/10): grey rubble walls (cobble, stone, andesite, a little tuff) / dark deepslate tiles and spruce
// timber / dressed stone bricks at corners, piers and the path, polished andesite for finished stock. Why: a mason
// builds his own yard from offcuts, so the walls are mixed rubble and only the corners and openings are dressed; the
// dark stone roof is what masons roof with, and it reads clearly against pale grey walls. Spruce for the posts, crane
// and the tracing house's upper storey, the only warm colour.
export const PALETTE = {
  rubble: [['cobblestone', 5], ['stone', 3], ['andesite', 2], ['tuff', 1]],
  ground: [['gravel', 4], ['coarse_dirt', 2], ['cobblestone', 1]],
  dressed: 'stone_bricks',
  dressedWall: 'stone_brick_wall',
  dressedStairs: 'stone_brick_stairs', // buttress caps
  sample: 'chiseled_stone_bricks', // the mason's sample in the gate pier
  chips: 'cobblestone_slab', // chippings on the yard floor
  fence: 'cobblestone_wall', // the yard's wall (#minecraft:walls)
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  post: 'stripped_spruce_log',
  beam: 'spruce_log',
  boards: 'spruce_planks',
  wood: 'spruce',
  floor: 'stone_bricks',
  ashlar: 'polished_andesite', // finished stock
  ashlarCap: 'smooth_stone_slab',
  rough: 'stone', // stock as it comes from the quarry
  glass: 'glass_pane',
}

export const MASSES = {
  yard: { x0: 1, x1: 11, z0: 0, z1: 4, gate: [5, 7] },
  shed: { x0: 1, x1: 11, z0: 5, z1: 6, posts: [4, 8] },
  lodge: { x0: 1, x1: 11, z0: 7, z1: 11, wallTop: 5 },
  catslide: { axis: 'x', across: [4, 12], ridge: 9, along: [0, 12], ridgeY: 8 },
  tracing: { x0: 12, x1: 16, z0: 3, z1: 8, jettyZ: 2, floorY: 5, upperTop: 9,
    roof: { axis: 'z', across: [11, 17], along: [1, 9], eaveY: 9, steep: true } },
}

export const ROOMS = [
  { name: 'Banker Shed', use: 'the Masons cut stone at their stonecutters', box: [2, 1, 5, 10, 2, 6] },
  { name: 'Lodge', use: 'stores: the chests, finished stone, the ledger', box: [2, 1, 8, 10, 4, 10] },
  { name: 'Stone Store', level: 2, use: 'the tracing house\'s ground floor: stock, the ladder up', box: [13, 1, 4, 15, 4, 10] },
  { name: 'Tracing Floor', level: 2, use: 'the master draws templates', box: [13, 6, 3, 15, 8, 10] },
]

export const OPENINGS = {
  gateway: { cells: [[5, 1, 0], [6, 1, 0], [7, 1, 0]] }, // open, no gate: the only way in
  lodgeDoor: { at: [6, 1, 7], facing: 'south' },
  windows: [
    { name: 'lodge back west', cells: [[4, 2, 11], [4, 3, 11]], out: 'south', dress: 'shutters' },
    { name: 'lodge back east', cells: [[8, 2, 11], [8, 3, 11]], out: 'south', dress: 'shutters' },
    { name: 'lodge gable west', cells: [[1, 5, 9], [1, 6, 9]], out: 'west', dress: 'shutters' },
    { name: 'lodge gable east', cells: [[11, 5, 9], [11, 6, 9]], out: 'east', dress: 'shutters', until: 1 }, // inside the tracing house at II
    { name: 'shed light west', cells: [[3, 2, 7]], out: 'north', dress: null },
    { name: 'shed light east', cells: [[9, 2, 7]], out: 'north', dress: null },
  ],
  tracing: {
    door: { at: [14, 1, 3], facing: 'south', step: [14, 0, 2] },
    windows: [
      { name: 'store east', cells: [[16, 2, 5], [16, 3, 5]], out: 'east', dress: 'shutters' },
      { name: 'tracing front', cells: [[13, 6, 2], [14, 6, 2], [15, 6, 2], [13, 7, 2], [14, 7, 2], [15, 7, 2]], out: 'north', dress: null },
      { name: 'tracing east', cells: [[16, 6, 4], [16, 7, 4], [16, 6, 6], [16, 7, 6]], out: 'east', dress: null },
      { name: 'tracing west', cells: [[12, 6, 4], [12, 7, 4]], out: 'west', dress: null },
      { name: 'tracing back', cells: [[14, 6, 8], [14, 7, 8]], out: 'south', dress: null },
      { name: 'tracing gable', cells: [[14, 10, 2]], out: 'north', dress: null },
    ],
    ladder: { x: 15, z: 7, y0: 1, top: 5, facing: 'north' },
  },
}

// Every piece, footprint first. kind: a furniture.py kind or a fixture (a vanilla block used as itself). level: drawn
// from that level up (default 1); `until`: the last level it stands in (Level II puts something else there).
export const FURNITURE = [
  // Banker Shed: two bankers' bays either side of the walkway to the lodge door
  { room: 'Banker Shed', kind: 'fixture', block: 'stonecutter', pos: [3, 1, 6], facing: 'north' }, // Mason 1, works from (3, 1, 5)
  { room: 'Banker Shed', kind: 'fixture', block: 'stonecutter', pos: [9, 1, 6], facing: 'north', level: 2 }, // Mason 2, from (9, 1, 5)
  { room: 'Banker Shed', kind: 'stock', block: 'polished_andesite', pos: [9, 1, 6], until: 1 }, // the second banker's place, waiting
  { room: 'Banker Shed', kind: 'lamp', part: 'tableLamp', pos: [2, 1, 6], facing: 'east', wood: 'spruce' },
  { room: 'Banker Shed', kind: 'lamp', part: 'tableLamp', pos: [10, 1, 6], facing: 'west', wood: 'spruce' },
  // Lodge: the chests against the back wall, stock on stacks, the ledger desk and its chair, lamps from the tie beams
  { room: 'Lodge', kind: 'fixture', part: 'chest', block: 'chest', pos: [2, 1, 10], facing: 'north' },
  { room: 'Lodge', kind: 'fixture', part: 'chest', block: 'chest', pos: [10, 1, 10], facing: 'north', level: 2 },
  { room: 'Lodge', kind: 'stock', block: 'polished_andesite', pos: [10, 1, 10], until: 1 },
  { room: 'Lodge', kind: 'desk', part: 'desk', pos: [10, 1, 8], facing: 'east', wood: 'spruce' },
  { room: 'Lodge', kind: 'chair', part: 'chair', pos: [9, 1, 8], facing: 'east', wood: 'spruce' },
  { room: 'Lodge', kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 9], facing: 'north', wood: 'spruce' },
  { room: 'Lodge', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 9], facing: 'north', wood: 'spruce' },
  // Tracing Floor (Level II): the drawing desk at the big front window, a pale floor cloth to draw on, a template
  // hung on the back wall, a lamp from the collar
  { room: 'Tracing Floor', level: 2, kind: 'desk', part: 'desk', pos: [13, 6, 3], facing: 'west', wood: 'spruce' },
  { room: 'Tracing Floor', level: 2, kind: 'chair', part: 'chair', pos: [14, 6, 3], facing: 'west', wood: 'spruce' },
  { room: 'Tracing Floor', level: 2, kind: 'rug', part: 'rug', cellsAbs: [[13, 6, 5], [14, 6, 5], [13, 6, 6], [14, 6, 6]], colour: 'white' },
  { room: 'Tracing Floor', level: 2, kind: 'picture', part: 'picture', pos: [13, 7, 7], facing: 'north', wood: 'spruce', colour: 'light_gray' },
  { room: 'Tracing Floor', level: 2, kind: 'lamp', part: 'hangingLamp', pos: [14, 9, 5], facing: 'north', wood: 'spruce' },
  { room: 'Stone Store', level: 2, kind: 'lamp', part: 'hangingLamp', pos: [14, 4, 5], facing: 'north', wood: 'spruce' },
]

// Stock piles (not furniture spots): [x, z, height] columns, capped with a slab.
// Buttresses: [x, z, top] (rubble from y 0 to top, a stair cap above it rising toward the wall, `toward`).
export const BUTTRESSES = [
  { cells: [[2, 12], [6, 12], [10, 12]], top: 3, toward: 'north' },
  { level: 2, cells: [[13, 9], [15, 9]], top: 4, toward: 'north' },
]

// Yard story: the trestle the Mason dresses a stone on, the chippings round it.
export const YARD = {
  trestle: { legs: [[9, 1, 1], [10, 1, 1]], top: 'smooth_stone_slab' }, // by the crane, where it sets a block down
  chippings: [[4, 1, 1], [4, 1, 2], [3, 1, 2]],
  sample: [4, 1, 0], // the west gate pier's stone: a chiseled sample of the mason's work
}

export const STOCK = [
  { name: 'shed ashlar behind the posts', block: 'ashlar', cap: null, y: 1, cells: [[4, 6, 2], [8, 6, 2]] }, // the bays' dividers (and they keep the shed shallow)
  { name: 'lodge ashlar', block: 'ashlar', cap: 'ashlarCap', y: 1, cells: [[2, 8, 2], [3, 8, 1], [2, 9, 1]] },
  { name: 'yard rough stone', block: 'rough', cap: null, y: 1, cells: [[2, 1, 2], [3, 1, 1], [2, 2, 1]] },
  { name: 'store ashlar', level: 2, block: 'ashlar', cap: 'ashlarCap', y: 1, cells: [[13, 7, 2], [13, 6, 1]] },
]

// The walking lines: street -> gateway -> yard path -> each banker's bay, and on to the lodge door; (II) the street
// -> the tracing house door -> the ladder -> the tracing floor.
export const WALK = [
  [6, 1, -3], [6, 1, 0], [6, 1, 3], [6, 1, 5], [3, 1, 5], // Mason 1 at his stonecutter (3, 1, 6)
  [6, 1, 5], [9, 1, 5], // Mason 2 at his (9, 1, 6)
  [6, 1, 6], [6, 1, 7], [6, 1, 8], [3, 1, 9], [9, 1, 9], // the lodge: chests at (2, 1, 10), (10, 1, 10)
  [14, 1, 2], [14, 1, 3], [14, 1, 5], [15, 1, 6], [15, 5, 7], [14, 6, 7], [14, 6, 4], // the tracing house
]
