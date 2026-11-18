// Fletcher's Lodge (fletchers_lodge, fletchers_lodge_2), the plan: drawn before any wall (ROADMAP 49, brief
// tools/blueprints/briefs/fletchers_lodge.md). Rooms, every furniture footprint and the walking line come first;
// recipe.mjs assembles the lodge from the parts library (../parts) to fit this plan, both levels from one drawing.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Dogtrot. Level I is one log pen (7 x 7) under a long 45-degree roof whose ridge runs along the front, a
//      stone chimney on its outer gable and a porch the whole width; Level II adds the open breezeway and a second pen
//      under the SAME roof carried on along the ridge, a second chimney on the far gable and a little gable over the
//      breezeway's mouth (round 2: the second pen a front-gabled wing instead, and the entry gable on Level I). Story: the two trades that share the fletching table get a pen each, and the open passage
//      between them is where logs come in and arrows go out.
//   B. The Drying Tower. A narrow three-storey timber tower whose louvred top is the loft where shafts and feathers
//      dry, on a low log workroom with a lean-to roof; Level II adds a log crib and a lumber lean-to. Story: the
//      fletcher's craft is patience, and the tower is where it waits.
//   C. The Butts. A long low shooting shed open along one side to a straw target at the end of a short range, the
//      range along x under a pent roof; Level II lengthens the range and adds a covered log store. Story: a bowyer's
//      yard where the guards' bows are proven before they leave.
// The strongest is A. A dogtrot is a real frontier form that reads at a glance (a long low roof, a chimney at each
// end, a dark passage through the middle), and it is the only one of the three whose Level II is the *meaning* of
// the building, not an add-on: the second trade gets the second pen, and Level I's every cell stays where it is
// because the roof simply runs on along its ridge. B is taller but tall eats the block budget and fails the renewal
// plot's spirit; C is a shed whose story lives in the yard, not the house.
// Round 1 built A as drawn, with the breezeway closed front and back by board walls (an open 7-deep roofed passage is
// holes.py's `open` room). Round 2 answers the critic (BACK: silhouette, depth, interior, detail all level or close):
// the East pen became a FRONT-GABLED WING (its ridge along z, a block under the main ridge), so Level II reads as a
// side-gabled pen and a gable-fronted pen, not one 17-long box; Level I's door got its own entry gable over the porch
// (the straw target in its pediment); every gable is boarded and set a block back behind the top log course, so the
// plate throws a shadow line round the house; the ridge is the roof's own planks; the breezeway narrowed to two
// blocks to pay for it, and the Wood room is furnished along the walk from its door (table, counter, hearth, double
// chest, log store, saplings, lamp). The ~650-block budget holds.
//
// Story: the "Twin Pen Lodge". A woodsman cuts the logs and lays them up in courses, notched at the corners, on a
// rubble sill; a fletcher works by the front window with the hearth at his back, and a straw target in the gable over
// his door tells the street what is made here. When the village grows the woodsman raises a second pen a passage's
// width along and turns its gable to the street, so the two trades stand side by side under two roofs, the passage
// between them (the "dogtrot") their shared back way for logs.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back.
//
// Named parts (the owner's feedback can point at them):
//   West pen     Level I's pen: oak log walls x 1..7, z 2..8, y 1..4 (the top course is the plate), a cobble sill at
//                y 0; the Fletcher's room inside (x 2..6, z 3..7), open to the rafters, a tie beam at y 4.
//   Main roof    a 45-degree dark oak roof, ridge along x at y 8 (z 5), eaves at y 4 on z 1 and z 9, over x 0..8
//                (Level I) and x 0..9 (Level II, where it dies into the wing). Its gables are boards set a block back
//                behind the plate (x 2 and, on Level I, x 6), so the gable overhang is two deep.
//   Entry gable  a little gable over the West pen's door (x 2..6, z 0..2) on the porch, a target in its pediment.
//   Porch        a plank deck along the West pen (z 0..1), stripped-log posts, its own eave row at y 4 on z 0 (a
//                flatter kick under the main slope); a woodpile and the straw target on it.
//   West chimney rubble stack up the west gable (x 0, z 5) with a stepped breast, the hearth in the wall inside.
//   Breezeway    Level II: the passage x 8..9, z 2..8 between the pens, closed front and back by board walls with a door
//                in each (old dogtrots were closed in just so).
//   Wing         Level II: the East pen x 10..14, z 2..8 (the Lumberjack's Wood room inside x 11..13, z 3..7) under
//                its own front-gabled roof, ridge along z at y 7 (x 12), eaves at y 4 on x 9 and x 15; its gables
//                boarded and set back (z 3 and z 7) with a log end at the apex.
//   East chimney Level II: its stack up the wing's east side (x 15, z 5) through the eave, above the wing's ridge.
//   Rick         split logs stacked under the back eave (z 9) behind the West pen.

export const SIZES = { 1: [9, 11, 10], 2: [16, 11, 10] }

// Palette (60/30/10): oak logs (the pens' walls) / dark oak roof and spruce boards (gables, porch deck, floors, doors,
// shutters) / cobble rubble (sill and chimneys) with stripped spruce for the posts, beams and plates that carry
// things. Why: warm round oak under a dark roof (contrast by value), spruce boards in the shaded gables, grey rubble
// at the foot and up the chimneys; the stripped posts and beams are the lighter timber that does the work.
export const PALETTE = {
  log: 'oak_log',
  stone: [['cobblestone', 6], ['mossy_cobblestone', 2], ['stone', 1], ['andesite', 1]],
  roof: { full: 'dark_oak_planks', stairs: 'dark_oak_stairs', slab: 'dark_oak_slab' },
  trim: 'dark_oak_slab', // barge boards under the gable overhangs
  beam: 'stripped_spruce_log', // porch posts, tie beams, the breezeway's plates
  floor: 'spruce_planks',
  boards: 'spruce_planks', // the gables and the breezeway's end walls
  deck: 'spruce_planks',
  wood: 'spruce', // doors, shutters, fences, stairs as chairs, sills
  glass: 'glass_pane',
  hay: 'hay_block',
  target: 'target',
  rick: 'oak_log',
  stoneStair: 'cobblestone_stairs', // the chimney breasts' shoulders
}

export const MASSES = {
  westPen: { x0: 1, x1: 7, z0: 2, z1: 8, y0: 1, y1: 4, room: { x0: 2, x1: 6, z0: 3, z1: 7 }, ties: [4] },
  eastPen: { x0: 10, x1: 14, z0: 2, z1: 8, y0: 1, y1: 4, room: { x0: 11, x1: 13, z0: 3, z1: 7 }, ties: [12], level: 2 },
  breezeway: { x0: 8, x1: 9, z0: 2, z1: 8, tie: 5, level: 2 },
  roof: { axis: 'x', across: [1, 9], along: { 1: [0, 8], 2: [0, 9] }, eaveY: 4, steep: false },
  // the main roof's gables, set back a block behind the plate: [x, level only]
  gables: [{ x: 2 }, { x: 6, only: 1 }, { x: 9, only: 2, flush: true }], // x 9: where the main roof dies into the wing
  wing: { axis: 'z', across: [9, 15], along: [1, 9], eaveY: 4, steep: false, gables: [3, 7], level: 2 },
  porch: { z0: 0, z1: 1, x0: 1, x1: { 1: 7, 2: 8 }, kick: 8, eave: 4, posts: [1, 7] },
  entryGable: { cx: 4, half: 2, y: 4, z0: 0, z1: 2 },
  chimneys: [{ x: 0, z: 5, top: 9, breast: 2, hearth: 1, level: 1 }, { x: 15, z: 5, top: 8, breast: 2, hearth: 14, level: 2 }],
}

export const ROOMS = [
  { name: 'Workroom', use: "the Fletcher's (Level I: whoever takes the table) bench, hearth and table", box: [2, 1, 3, 6, 3, 7] },
  { name: 'Breezeway', use: 'the passage between the pens, closed by board walls with a door each end: the way in to the Wood room', box: [8, 1, 3, 9, 3, 7], level: 2 },
  { name: 'Wood room', use: "the Lumberjack's table and counter, his hearth, double chest and log store", box: [11, 1, 3, 13, 3, 7], level: 2 },
]

export const OPENINGS = {
  doors: [
    { at: [4, 1, 2], facing: 'south', out: 'north', level: 1 }, // the West pen's front door, under the entry gable
    { at: [8, 1, 2], facing: 'south', out: 'north', level: 2 }, // the breezeway's front door, off the porch's end
    { at: [8, 1, 8], facing: 'north', out: 'south', level: 2 }, // its back door, where the logs come in
    { at: [10, 1, 4], facing: 'east', out: 'west', level: 2, hinge: 'right' }, // the Wood room's door, off the breezeway
  ],
  // windows: one function dresses them all (shutters both sides, one block out).
  // `dressOnly`: shutters on that level only (the West pen's east window looks into the breezeway on Level II).
  windows: [
    { name: 'west pen front west', cells: [[2, 2, 2], [2, 3, 2]], out: 'north', level: 1 },
    { name: 'west pen front east', cells: [[6, 2, 2], [6, 3, 2]], out: 'north', level: 1 },
    { name: 'west pen back', cells: [[4, 2, 8], [4, 3, 8]], out: 'south', level: 1 },
    { name: 'west pen east', cells: [[7, 2, 5], [7, 3, 5]], out: 'east', level: 1, dressOnly: 1 },
    { name: 'west pen gable', cells: [[2, 6, 4]], out: 'west', level: 1, bare: true },
    { name: 'west pen east gable', cells: [[6, 6, 5]], out: 'east', level: 1, only: 1, bare: true },
    { name: 'wing front', cells: [[12, 2, 2], [12, 3, 2]], out: 'north', level: 2 },
    { name: 'wing back', cells: [[12, 2, 8], [12, 3, 8]], out: 'south', level: 2 },
    { name: 'wing front gable', cells: [[12, 5, 3]], out: 'north', level: 2, bare: true },
  ],
}

// Every piece, footprint first. kind: a furniture.py kind (a spot) or a fixture (a vanilla block the game uses as
// itself). part: the furnitureParts part that draws it, or a block for fixtures.
export const FURNITURE = [
  // Workroom: the fletching table under the front-east window, the bench (a desk) under the east window, the chest in
  // the back corner; a table for two under the back window, a cupboard, the hearth in the west wall with a rug.
  { room: 'Workroom', kind: 'fixture', block: 'fletching_table', pos: [6, 1, 3], level: 1 },
  { room: 'Workroom', kind: 'desk', part: 'desk', pos: [6, 1, 5], facing: 'west', wood: 'spruce', level: 1 },
  { room: 'Workroom', kind: 'fixture', part: 'chest', block: 'chest', pos: [6, 1, 7], facing: 'west', level: 1 },
  { room: 'Workroom', kind: 'cupboard', part: 'cupboard', pos: [2, 1, 7], facing: 'east', wood: 'spruce', level: 1 },
  { room: 'Workroom', kind: 'table', part: 'table', pos: [3, 1, 7], facing: 'north', wood: 'spruce', colour: 'white', level: 1 },
  { room: 'Workroom', kind: 'table', part: 'table', pos: [4, 1, 7], facing: 'north', wood: 'spruce', colour: 'white', level: 1 },
  { room: 'Workroom', kind: 'chair', part: 'chair', pos: [3, 1, 6], facing: 'south', wood: 'spruce', level: 1 },
  { room: 'Workroom', kind: 'chair', part: 'chair', pos: [4, 1, 6], facing: 'south', wood: 'spruce', level: 1 },
  { room: 'Workroom', kind: 'rug', part: 'rug', cellsAbs: [[2, 1, 4], [2, 1, 5], [3, 1, 4], [3, 1, 5]], colour: 'red', level: 1 },
  { room: 'Workroom', kind: 'lamp', part: 'hangingLamp', pos: [4, 3, 4], facing: 'north', wood: 'spruce', level: 1 },
  { room: 'Workroom', kind: 'lamp', part: 'hangingLamp', pos: [4, 3, 6], facing: 'north', wood: 'spruce', level: 1 },
  { room: 'Workroom', kind: 'fixture', part: 'hearth', pos: [1, 1, 5], facing: 'east', level: 1 },
  // Breezeway: a lamp under the tie beam.
  { room: 'Breezeway', kind: 'lamp', part: 'hangingLamp', pos: [8, 3, 5], facing: 'north', wood: 'spruce', level: 2 },
  // Wood room, planned along the walk from its door (10, 1, 4): straight ahead the fletching table under the front
  // window with the counter (his work surface) beside it; to the right the hearth with a rug before it; the double
  // chest in the back corner and the log store against the back wall; by the door a shelf of potted saplings; a lamp
  // hung from the tie beam over the middle.
  { room: 'Wood room', kind: 'fixture', block: 'fletching_table', pos: [12, 1, 3], level: 2 },
  { room: 'Wood room', kind: 'counter', part: 'counter', pos: [13, 1, 3], facing: 'north', wood: 'spruce', top: 'spruce_slab', level: 2 },
  { room: 'Wood room', kind: 'fixture', part: 'doubleChest', pos: [13, 1, 6], facing: 'west', along: 'south', level: 2 },
  { room: 'Wood room', kind: 'shelf', part: 'shelf', pos: [11, 1, 5], facing: 'east', wood: 'spruce', level: 2, plant: 'potted_spruce_sapling' },
  { room: 'Wood room', kind: 'rug', part: 'rug', cellsAbs: [[13, 1, 4], [13, 1, 5]], colour: 'green', level: 2 },
  { room: 'Wood room', kind: 'lamp', part: 'hangingLamp', pos: [12, 3, 5], facing: 'north', wood: 'spruce', level: 2 },
  { room: 'Wood room', kind: 'fixture', part: 'hearth', pos: [14, 1, 5], facing: 'west', level: 2 },
]

// The log store inside the Wood room (logs on their sides against the back wall) and the straw target on the porch.
export const STORE = { logs: [[11, 1, 7], [12, 1, 7], [11, 1, 6], [11, 2, 7]], level: 2 }
export const TARGET = { at: [1, 1, 1] } // a hay bale with a target on it at the porch's west end, by the corner post

// The walking line: Level I's worker from the street (1, 0, -3) onto the porch, through the front door to his table;
// Level II's second worker from (4, 0, -3) along the porch to the breezeway's door and through the Wood room's door.
export const WALK = {
  1: [[1, 0, -3], [2, 0, -1], [3, 1, 0], [4, 1, 1], [4, 1, 2], [4, 1, 3], [5, 1, 3], [5, 1, 4]],
  2: [[4, 0, -3], [7, 0, -1], [8, 1, 0], [8, 1, 1], [8, 1, 2], [8, 1, 3], [9, 1, 3], [9, 1, 4], [10, 1, 4], [11, 1, 4], [12, 1, 4]],
}

// Outside the rooms: the rick of split logs under the back eave, the porch woodpile, the back skirt, a lantern over
// each door.
export const OUTSIDE = {
  ricks: [{ x0: 3, x1: 5, z: 9, level: 1 }],
  porchPile: [[6, 1, 1], [7, 1, 1]],
  // a rubble skirt (stairs leaning on the sill) along the back, the rick filling its middle behind the West pen; a
  // step at the breezeway's back door; a sill with a potted fern under the wing's back window, standing on the skirt
  skirt: [{ cells: [[1, 0, 9], [2, 0, 9], [6, 0, 9], [7, 0, 9]], level: 1 }, { cells: [[10, 0, 9], [11, 0, 9], [12, 0, 9], [13, 0, 9], [14, 0, 9]], level: 2 }],
  backStep: { at: [8, 0, 9], level: 2 },
  sills: [{ cells: [[12, 2, 8], [12, 3, 8]], out: 'south', pots: ['potted_fern'], level: 2 }],
  lanterns: [{ at: [4, 3, 1], level: 1 }, { at: [8, 3, 1], level: 2 }, { at: [8, 3, 9], level: 2 }], // under the eave stairs
}
