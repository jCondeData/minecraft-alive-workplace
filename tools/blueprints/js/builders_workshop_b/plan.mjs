// Builder's Workshop (Version B), the plan: drawn before any wall. Rooms, every furniture footprint (kind, cells,
// facing) and the walking line come first; recipe.mjs then assembles the building from the parts library (../parts).
//
// Three rough concepts (ROADMAP 49, tools/blueprints/REBUILD.md):
//   1. "The Yard": a low one-storey shed round three sides of a fenced work yard, a 45-degree roof, the bench in the
//      open yard under a lean-to. Story: the builder works outdoors and only sleeps in. Reads as a farm, not a trade,
//      and a yard is ground the Steward can't promise is level.
//   2. "The Tower Office": a tall narrow stone tower (5x5) with a pyramid roof, the drafting table at the top, a
//      lower wing for stock. Story: he plans the village from above. Striking, but a tower is a lookout or a mage's,
//      and the bed, table and chest end up on three floors joined by ladders.
//   3. "The Hoist House": a gable-fronted two-storey workshop whose upper storey is jettied out over the street on
//      brackets, a hoist beam run out of the gable's peak with its chain, and one long catslide roof that sweeps down
//      the east side over a low timber store with cart doors. Story: a builder's house is his sample book: a rubble
//      ground storey (the mason's work), a dark oak frame with brick nogging above (the carpenter's and bricklayer's),
//      slate on top, and the stock lifted by the hoist and stacked in the store.
// Strongest: 3. It is the only one whose outline says "builder" from the street (the hoist and the lopsided saltbox
// roof are unlike any house in a village), it shows three crafts in one facade, and its plan puts the table, the stash
// and the bed one ladder apart. It is not the safe one: an asymmetric roof and a jetty are harder to draw well.
//
// Coordinates: the PLAN's coordinates, which are the village contract's: x across the front (west -1 -> east 14), y up
// (0 = the layer on the clicked block), z from the front (z = 0, north) to the back (z 10). The file is the plan moved
// one block east (file x = plan x + 1, recipe.mjs's ShiftedCanvas), because the west jetty and its eave reach to plan
// x -1 and 0. The front door is on the north side at plan (4, 1, 2), file (5, 1, 2), with a step before it.
//
// Masses (named parts the owner's feedback can point at):
//   Workshop   the main mass: rubble stone ground storey x 1..7, z 2..8, y 1..4 (a tall working room), floor beam
//              ring y 5. Jetty: the upper storey x 1..7, z 1..8, y 6..9, brick nogging in a dark oak frame. On the
//              front it is jettied a block out (z 1) on four brackets; on the west and the back its posts and beams
//              stand a block proud of the brick (x 0, z 9) on brackets, so every panel is set back behind its frame.
//   Roof       one catslide roof of slate (deepslate tiles), ridge along z at x 4, y 13: a short slope west to the
//              eave at x -1 (y 8) and a long one east over the Store to x 12 (y 5); overhangs z 0 and z 10.
//   Gable      the hero: the front gable over the jetty, a king post, two little panes, the Hoist under its peak.
//   Hoist      a dark oak beam out of the king post at (4, 11, 0) with a chain and a lantern: lights the front.
//   Store      the low timber store on the east, x 8..11, z 2..8, plank walls on dark oak posts, cart doors at the
//              front (x 9..10), open to the rafters inside; the builder's stockroom inside (three chests, a workbench with
//              shelves over it, the lumber rack); its east wall framed with proud corner posts and a mid-rail round a
//              shuttered window, the little gabled dormer (z 4..6) right over it; a second hoist over the cart doors.
//   Chimney    a stone stack up the back gable (x 4, z 10, its flue at z 9) past the ridge; the hearth in the back
//              wall at (4, 1, 8) under a stone mantel and hood.
//   Yard       the strip in front (z 0..1): the step to the door, flower beds under the windows, a lamp post by the
//              cart doors. East of the Store (x 12..16, z 2..8), the materials yard: a fenced pen with a gate, the
//              woodpile under the eave, stacks of cobble, stone bricks and logs, and the Crane over them.
//
// The village variant (village.py's builders_workshop_village, ROADMAP 50.1): in PLAN coordinates the Workshop's ground
// storey is drawn on the village contract's room (walls x 1/7, z 2/8, door (4, 1, 2), table (2, 1, 7) facing east,
// chest (2, 1, 6)), so the village piece is this drawing moved back one block west (the west eave's column, plan
// x -1, left off), restyled per village style round the same room. (3, 1, 5) and (4, 1, 5) stay clear (the spawn
// villager stands at (4, 1, 5) on the floor jigsaw (4, 0, 5); the mover at (3, 1, 5)): here they are a rug, walkable.
// The village bed (foot (6, 1, 6), head (6, 1, 7), facing south: VillagePieces' reference cell) stands on the ground
// floor's free east end, so the ladder climbs the east wall at (6, 1..5, 4) instead, and the village piece leaves the
// loft's bed out (one bed, the reference one). The front row z 0 at y 0, x 0..8 places nothing (the step and the
// flower beds are at z 1; the lamp post and the store apron start east of x 8).
//
// The Crane (ROADMAP 50.1, the critic's 'a real hoist or gantry so the front says builder'): a timber jib crane
// stands in the materials yard by the front, its mast as tall as the jetty's top with a counterweighted jib run out
// west over the stacks, the hook hanging over the cobble it lifts: the one outline in a village that says 'builder'
// from down the street, beside the gable's small loading hoist.

export const SIZE = [18, 16, 11] // the file's w, h, d (limit 18 x 18 x 20)
export const SHIFT = [1, 0, 0] // file = plan + SHIFT

// Palette (60/30/10): masonry walls (rubble stone below, brick nogging above) / a slate roof and a dark oak frame /
// spruce joinery, store boards and floors, with stone-brick dressings. Why: a builder's own house shows each craft
// he sells; the grey stone and red brick under dark slate read light-to-dark going up the wrong way, so the frame and
// slate are kept to lines and planes and the brick carries the warmth.
export const PALETTE = {
  stone: [['cobblestone', 6], ['stone', 3], ['andesite', 2], ['mossy_cobblestone', 1]],
  dressing: 'stone_bricks', // quoins, lintels, the chimney cap
  dressingAlt: 'polished_andesite',
  frame: 'dark_oak_log',
  infill: 'bricks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  trim: 'dark_oak', // barge boards, brackets, lintels
  floor: 'spruce_planks',
  storeFloor: 'stone',
  boards: 'spruce_planks', // the Store's walls
  wood: 'spruce', // doors, ladder, chairs, slabs
  glass: 'glass_pane',
  log: 'spruce_log', // stock: timber on the rack and the woodpile
  job: 'aliveworkplace:blueprint_table',
}

export const MASSES = {
  workshop: {
    ground: { x0: 1, x1: 7, z0: 2, z1: 8, y0: 1, y1: 4 },
    room: { x0: 2, x1: 6, z0: 3, z1: 7 },
    floorY: 5,
    upper: { x0: 1, x1: 7, z0: 1, z1: 8, y0: 6, y1: 8, plate: 9 },
    upRoom: { x0: 2, x1: 6, z0: 2, z1: 7 },
    // frame posts of the upper storey: front and back at x 1, 4, 7 (two bays of two, the middle post running on up
    // as the gable's king post to the Hoist), the east side at z 1, 3, 6, 8 in its wall
    frontPosts: [1, 4, 7], sidePosts: [1, 3, 6, 8],
    brackets: [[1, 4, 1], [3, 4, 1], [5, 4, 1], [7, 4, 1]],
    // the proud frame: the west jetty (beam x 0, y 5 on brackets, posts x 0) and the back's posts (z 9) and tie beam
    west: { x: 0, posts: [1, 3, 6, 8], brackets: [1, 3, 6, 8] },
    back: { z: 9, posts: [1, 7] },
  },
  roof: { axis: 'z', across: [-1, 12], ridge: 4, along: [0, 10], ridgeY: 13 },
  store: { x0: 8, x1: 11, z0: 2, z1: 8, room: { x0: 8, x1: 10, z0: 3, z1: 7 }, posts: [2, 5, 8], lintelY: 3, railY: 4, eastPosts: [3, 7], // proud posts at x 12 flanking the window bay
    dormer: { face: 11, zc: 5, y0: 6, dir: 1 } },
  chimney: { x: 4, z: 10, flue: 9, top: 14 },
}

export const ROOMS = [
  { name: 'Workshop', use: "the Builder's drafting room: his Blueprint Table, his stash, the hearth", box: [2, 1, 3, 6, 4, 7] },
  { name: 'Store', use: 'timber and stock, a second chest, the cart doors', box: [8, 1, 3, 10, 5, 7] },
  { name: 'Loft', use: 'his bedroom and a drawing desk under the hoist window', box: [2, 6, 2, 6, 9, 7] },
]

export const OPENINGS = {
  frontDoor: { at: [4, 1, 2], facing: 'south', step: [4, 0, 1] },
  storeDoorway: { at: [7, 1, 5], height: 2 },
  cartDoors: { cells: [[9, 1, 2], [10, 1, 2]], facing: 'south' },
  ladder: { x: 6, z: 4, y0: 1, y1: 5, facing: 'west' }, // against the east wall, up through the loft floor (off the village bed's cells)
  windows: [
    // ground storey: two-high panes in the stone, a flower bed under each front one
    { name: 'front west', cells: [[2, 2, 2], [2, 3, 2]], out: 'north', dress: 'shutters' },
    { name: 'front east', cells: [[6, 2, 2], [6, 3, 2]], out: 'north', dress: 'shutters' },
    { name: 'west', cells: [[1, 2, 4], [1, 3, 4], [1, 2, 5], [1, 3, 5]], out: 'west', dress: 'shutters' },
    { name: 'back', cells: [[2, 2, 8], [2, 3, 8]], out: 'south', dress: 'shutters' },
    // the jetty: a two-by-two window filling each bay over a brick apron
    { name: 'jetty west', cells: [[2, 7, 1], [3, 7, 1], [2, 8, 1], [3, 8, 1]], out: 'north' },
    { name: 'jetty east', cells: [[5, 7, 1], [6, 7, 1], [5, 8, 1], [6, 8, 1]], out: 'north' },
    { name: 'loft west', cells: [[1, 7, 4], [1, 8, 4], [1, 7, 5], [1, 8, 5]], out: 'west' },
    { name: 'loft back west', cells: [[2, 7, 8], [3, 7, 8], [2, 8, 8], [3, 8, 8]], out: 'south' },
    { name: 'loft back east', cells: [[5, 7, 8], [6, 7, 8], [5, 8, 8], [6, 8, 8]], out: 'south' },
    { name: 'gable', cells: [[3, 10, 1], [5, 10, 1]], out: 'north' },
    { name: 'store east', cells: [[11, 2, 5], [11, 3, 5]], out: 'east', dress: 'shutters' }, // under the dormer
    { name: 'store front', cells: [[9, 5, 2]], out: 'north' },
  ],
}

// Every piece of furniture, footprint first (see house_t1_b/plan.mjs for the fields).
export const FURNITURE = [
  // Workshop: the table against the west wall with its chest beside it, a rug where he paces, a lamp from the joists,
  // a standing desk by the door with a plan pinned over it
  { room: 'Workshop', kind: 'fixture', part: 'job', pos: [2, 1, 7], facing: 'east' }, // the Blueprint Table
  { room: 'Workshop', kind: 'fixture', part: 'chest', pos: [2, 1, 6], facing: 'east', block: 'chest' }, // his stash
  { room: 'Workshop', kind: 'fixture', part: 'hearth', pos: [4, 1, 8], facing: 'north', block: 'campfire' },
  { room: 'Workshop', kind: 'cupboard', part: 'cupboard', pos: [2, 1, 3], facing: 'east', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Workshop', kind: 'rug', part: 'rug', cellsAbs: [[3, 1, 5], [4, 1, 5], [5, 1, 5], [3, 1, 6], [4, 1, 6], [5, 1, 6]], colour: 'brown' },
  { room: 'Workshop', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 5], facing: 'north', wood: 'spruce' },
  { room: 'Workshop', kind: 'desk', part: 'desk', pos: [6, 1, 3], facing: 'east', wood: 'spruce' }, // a standing desk for the day's sheet
  { room: 'Workshop', kind: 'picture', part: 'picture', pos: [6, 3, 3], facing: 'west', wood: 'spruce', colour: 'blue' }, // a plan pinned up over it
  // Store, the stockroom: three chests in the corners, a workbench (a counter, no job block) on the back wall with
  // shelves of candles and a lantern over it, the lumber rack on the east wall, a lamp from the rafters
  { room: 'Store', kind: 'fixture', part: 'chest', pos: [8, 1, 7], facing: 'north', block: 'chest' },
  { room: 'Store', kind: 'fixture', part: 'chest', pos: [8, 1, 3], facing: 'east', block: 'chest' },
  { room: 'Store', kind: 'fixture', part: 'chest', pos: [10, 1, 7], facing: 'north', block: 'chest' },
  { room: 'Store', kind: 'counter', part: 'counter', pos: [9, 1, 7], facing: 'north', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Store', kind: 'shelf', part: 'stockShelf', pos: [9, 3, 7], facing: 'north', wood: 'spruce', on: 'candle' },
  { room: 'Store', kind: 'shelf', part: 'stockShelf', pos: [10, 3, 7], facing: 'north', wood: 'spruce', on: 'lantern' },
  { room: 'Store', kind: 'shelf', part: 'stockShelf', pos: [8, 3, 3], facing: 'east', wood: 'spruce', on: 'candle' },
  { room: 'Store', kind: 'lamp', part: 'hangingLamp', pos: [9, 6, 5], facing: 'north', chain: 1, wood: 'spruce' },
  // Loft: the bed under the west window, a nightstand, the drawing desk and its chair under the front window, a chest
  { room: 'Loft', kind: 'bed', part: 'bed', pos: [2, 6, 6], facing: 'south', colour: 'red' },
  { room: 'Loft', kind: 'nightstand', part: 'nightstand', pos: [3, 6, 7], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'desk', part: 'desk', pos: [3, 6, 2], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'chair', part: 'chair', pos: [3, 6, 3], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'fixture', part: 'chest', pos: [6, 6, 2], facing: 'south', block: 'chest' },
  { room: 'Loft', kind: 'cupboard', part: 'cupboard', pos: [2, 6, 2], facing: 'east', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Loft', kind: 'rug', part: 'rug', cellsAbs: [[3, 6, 4], [4, 6, 4], [5, 6, 4], [3, 6, 5], [4, 6, 5], [5, 6, 5]], colour: 'gray' },
  { room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [4, 10, 5], facing: 'north', chain: 2, wood: 'spruce' },
]

// The walking line: the front step -> the door -> the table (stand at 3,1,7) and his chest -> the store doorway -> the
// store's chest; and up the ladder to the loft's bed and desk.
export const WALK = [
  [4, 0, 0], [4, 0, 1], [4, 1, 2], [4, 1, 3], [4, 1, 4], [3, 1, 5], [3, 1, 6], [3, 1, 7],
  [5, 1, 5], [6, 1, 5], [7, 1, 5], [8, 1, 5], [8, 1, 6], [9, 1, 6], [9, 1, 4],
  [5, 1, 4], [6, 2, 4], [6, 5, 4], [6, 6, 4], [5, 6, 5], [3, 6, 5], [4, 6, 3], [3, 6, 4],
]

// Outside the rooms (not furniture spots)
export const OUTSIDE = {
  apron: { x0: 9, x1: 11, z0: 0, z1: 1 }, // stone before the cart doors (east of x 8: the village front row stays the street's)
  flowerBeds: [{ x0: 2, x1: 2, z: 1, plants: ['poppy'] }, { x0: 6, x1: 6, z: 1, plants: ['cornflower'] }],
  lampPost: [12, 1, 1],
  woodpile: { x: 12, z0: 4, z1: 6 }, // under the east window's shutters
  rack: [[10, 1, 4], [10, 1, 5], [10, 1, 6], [10, 2, 4], [10, 2, 6]], // the lumber rack inside the store, along its east wall
  // the materials yard east of the Store: a fence from the store's corner posts round to x 14, a gate in the middle
  pen: { fence: [[12, 1, 2], [16, 1, 2], [16, 1, 8], [12, 1, 8]], gate: [16, 1, 5] },
  stacks: [[13, 1, 3, 'cobblestone'], [13, 1, 4, 'cobblestone'], [13, 1, 6, 'stone_bricks'],
    [13, 1, 7, 'stone_bricks'], [13, 2, 7, 'stone_bricks']],
  logs: [[15, 1, 6], [15, 1, 7], [15, 2, 7]], // a stack of logs by the gate
  // the Crane: a mast in the yard's front corner, a jib out west over the cobble with its hook, a short counter-jib east
  // with a block of stone hung under it as the weight, a slab cap
  crane: { mast: [15, 1, 3], height: 11, reach: 2, drop: 4, counterweight: 'cobblestone' },
  cartHoist: { at: [10, 6, 1], reach: 2, drop: 1 }, // a beam out of the store's front over the cart doors
}
