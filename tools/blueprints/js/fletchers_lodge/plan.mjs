// Fletcher's Lodge (fletchers_lodge, fletchers_lodge_2), the plan: drawn before any wall (ROADMAP 49, brief
// tools/blueprints/briefs/fletchers_lodge.md). Rooms, every furniture footprint and the walking line come first;
// recipe.mjs assembles the lodge from the parts library (../parts) to fit this plan, both levels from one drawing.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Dogtrot. Level I is one log pen (7 x 7) under a long 45-degree roof whose ridge runs along the front, a
//      stone chimney on its outer gable and a porch the whole width; Level II adds the open breezeway and a second pen
//      under the SAME roof carried on along the ridge, a second chimney on the far gable and a little gable over the
//      breezeway's mouth. Story: the two trades that share the fletching table get a pen each, and the open passage
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
// Built as drawn, with two changes the checks forced: the breezeway is closed front and back by board walls with a
// door each (an open 7-deep roofed passage is holes.py's `open` room), and Level II's porch stops at the mouth gable
// so the second chimney fits the ~650-block budget (Level I 353 blocks, Level II 650; Level II keeps 331 of Level I's
// 353 cells). A light log ridge pole runs the length of the dark roof.
//
// Story: the "Twin Pen Lodge". A woodsman cuts the logs and lays them up in courses, notched at the corners, on a
// rubble sill; a fletcher works by the front window with the hearth at his back. When the village grows the woodsman
// raises a second pen a breezeway's width along, carries the roof over both, and the passage between them (the
// "dogtrot") becomes the lodge's front door: a straw target in its gable tells the street what is made here.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The ridge runs along x.
//
// Named parts (the owner's feedback can point at them):
//   West pen     Level I's pen: oak log walls x 1..7, z 2..8, y 1..4 (the top course is the plate), a cobble sill at
//                y 0; the Fletcher's room inside (x 2..6, z 3..7), open to the rafters, two tie beams at y 4.
//   Roof         one 45-degree spruce roof, ridge along x at y 8 (z 5), eaves at y 4 on z 1 and z 9; it runs x 0..8
//                (Level I) and on to x 0..16 (Level II) without a cell of Level I's roof changing.
//   Porch        a plank deck the whole front (z 0..1), stripped-log posts, its own eave row at y 4 on z 0 (a flatter
//                kick under the main slope); a woodpile and the straw target on it.
//   West chimney rubble stack up the west gable (x 0, z 5) with a stepped breast, the hearth in the wall inside.
//   Breezeway    Level II: the passage x 8..10, z 2..8 between the pens, the plate carried across on log beams, closed
//                front and back by board walls with a door in each (old dogtrots were closed in just so; an open
//                one would let the weather into a 7-deep roofed space, holes.py's `open` check).
//   Mouth gable  Level II: a little gable over the breezeway's mouth (x 7..11, z 0..2) on the porch posts, a target
//                in its pediment.
//   East pen     Level II: the Lumberjack's pen x 11..15, z 2..8, its door off the breezeway, a log store inside.
//   East chimney Level II: its stack up the east gable (x 16, z 5), so the long roof ends in a chimney both ways.
//   Rick         split logs stacked under the back eave (z 9) behind the West pen.

export const SIZES = { 1: [9, 11, 10], 2: [17, 11, 10] }

// Palette (60/30/10): oak logs (the pens' walls and gables) / spruce (roof, porch deck, floors, doors and shutters) /
// cobble rubble (sill and chimneys) with stripped oak for the posts, beams and plates that carry things. Why: warm
// round oak under a darker spruce roof (contrast, and a roof a lumberjack makes himself), grey rubble at the foot and
// up the chimneys; the stripped posts and beams are the lighter timber that does the work.
export const PALETTE = {
  log: 'oak_log',
  stone: [['cobblestone', 6], ['mossy_cobblestone', 2], ['stone', 1], ['andesite', 1]],
  roof: { full: 'dark_oak_planks', stairs: 'dark_oak_stairs', slab: 'dark_oak_slab' },
  trim: 'dark_oak_slab', // barge boards under the gable overhangs
  beam: 'stripped_spruce_log', // porch posts, tie beams, the breezeway's plates
  floor: 'spruce_planks',
  boards: 'spruce_planks', // the breezeway's end walls
  deck: 'spruce_planks',
  wood: 'spruce', // doors, shutters, fences, stairs as chairs
  glass: 'glass_pane',
  hay: 'hay_block',
  target: 'target',
  rick: 'oak_log',
  stoneStair: 'cobblestone_stairs', // the chimney breast's shoulders
}

export const MASSES = {
  westPen: { x0: 1, x1: 7, z0: 2, z1: 8, y0: 1, y1: 4, room: { x0: 2, x1: 6, z0: 3, z1: 7 }, ties: [4], gables: { 1: [1, 7], 2: [1] } }, // Level II opens its east gable to the breezeway's attic
  eastPen: { x0: 11, x1: 15, z0: 2, z1: 8, y0: 1, y1: 4, room: { x0: 12, x1: 14, z0: 3, z1: 7 }, ties: [13], gables: { 2: [15] }, level: 2 }, // its west gable is the breezeway's attic: left open
  breezeway: { x0: 8, x1: 10, z0: 2, z1: 8, tie: 5, level: 2 }, // closed front and back by board walls with a door each
  roof: { axis: 'x', across: [1, 9], along: { 1: [0, 8], 2: [0, 16] }, eaveY: 4, steep: false },
  porch: { z0: 0, z1: 1, x0: 1, x1: { 1: 7, 2: 11 }, kick: { 1: 8, 2: 11 }, eave: 4, posts: { 1: [1, 7], 2: [1, 7, 11] } }, // Level II's porch ends at the mouth gable; the East pen stands plain under its eave
  mouthGable: { cx: 9, half: 2, y: 4, z0: 0, z1: 2, level: 2 },
  chimneys: [{ x: 0, z: 5, top: 9, breast: 2, hearth: 1, level: 1 }, { x: 16, z: 5, top: 9, breast: 2, hearth: 15, level: 2 }],
}

export const ROOMS = [
  { name: 'Workroom', use: "the Fletcher's (Level I: whoever takes the table) bench, hearth and table", box: [2, 1, 3, 6, 3, 7] },
  { name: 'Breezeway', use: 'the passage between the pens, closed by board walls with a door each end: the way in to the East pen', box: [8, 1, 3, 10, 3, 7], level: 2 },
  { name: 'Wood room', use: "the Lumberjack's table, his chest and the log store", box: [12, 1, 3, 14, 3, 7], level: 2 },
]

export const OPENINGS = {
  doors: [
    { at: [4, 1, 2], facing: 'south', out: 'north', level: 1 }, // the West pen's front door, off the porch
    { at: [9, 1, 2], facing: 'south', out: 'north', level: 2 }, // the breezeway's front door, under the mouth gable
    { at: [9, 1, 8], facing: 'north', out: 'south', level: 2 }, // its back door, where the logs come in
    { at: [11, 1, 4], facing: 'east', out: 'west', level: 2, hinge: 'right' }, // the East pen's door, off the breezeway
  ],
  // windows: one function dresses them all (shutters both sides, one block out)
  windows: [
    { name: 'west pen front west', cells: [[2, 2, 2], [2, 3, 2]], out: 'north', level: 1 },
    { name: 'west pen front east', cells: [[6, 2, 2], [6, 3, 2]], out: 'north', level: 1 },
    { name: 'west pen back', cells: [[4, 2, 8], [4, 3, 8]], out: 'south', level: 1 },
    { name: 'west pen east', cells: [[7, 2, 5], [7, 3, 5]], out: 'east', level: 1 },
    { name: 'west pen gable', cells: [[7, 6, 5]], out: 'east', level: 1, only: 1, bare: true },
    { name: 'east pen front', cells: [[13, 2, 2], [13, 3, 2]], out: 'north', level: 2 },
    { name: 'east pen back', cells: [[13, 2, 8], [13, 3, 8]], out: 'south', level: 2 },
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
  { room: 'Breezeway', kind: 'lamp', part: 'hangingLamp', pos: [9, 3, 5], facing: 'north', wood: 'spruce', level: 2 },
  // Wood room: the second fletching table under the front window, the chest in
  // the back corner, the log store along the back wall, a shelf with a potted sapling by the door.
  { room: 'Wood room', kind: 'fixture', block: 'fletching_table', pos: [13, 1, 3], level: 2 },
  { room: 'Wood room', kind: 'fixture', part: 'chest', block: 'chest', pos: [14, 1, 7], facing: 'west', level: 2 },
  { room: 'Wood room', kind: 'shelf', part: 'shelf', pos: [12, 1, 6], facing: 'east', wood: 'spruce', level: 2, plant: 'potted_spruce_sapling' },
  { room: 'Wood room', kind: 'lamp', part: 'hangingLamp', pos: [13, 3, 5], facing: 'north', wood: 'spruce', level: 2 },
  { room: 'Wood room', kind: 'fixture', part: 'hearth', pos: [15, 1, 5], facing: 'west', level: 2 },
]

// The log store inside the Wood room (logs on their sides against the back wall) and the straw target on the porch.
export const STORE = { logs: [[12, 1, 7], [13, 1, 7], [12, 2, 7]], level: 2 }
export const TARGET = { at: [1, 1, 1] } // a hay bale with a target on it at the porch's west end, by the corner post

// The walking line: Level I's worker from the street (1, 0, -3) onto the porch, through the front door to his table;
// Level II's second worker from (4, 0, -3) under the mouth gable, up the breezeway to the East pen's door.
export const WALK = {
  1: [[1, 0, -3], [2, 0, -1], [3, 1, 0], [4, 1, 1], [4, 1, 2], [4, 1, 3], [5, 1, 3], [5, 1, 4]],
  2: [[4, 0, -3], [6, 0, -1], [8, 1, 0], [9, 1, 1], [9, 1, 2], [9, 1, 3], [10, 1, 4], [11, 1, 4], [12, 1, 4], [12, 1, 3]],
}

// Outside the rooms: ricks of split logs under the back eave, the porch woodpile, a lantern over each door.
export const OUTSIDE = {
  ricks: [{ x0: 3, x1: 5, z: 9, level: 1 }],
  porchPile: [[6, 1, 1], [7, 1, 1]],
  lanterns: [{ at: [4, 3, 1], level: 1 }, { at: [9, 3, 1], level: 2 }, { at: [9, 3, 9], level: 2 }], // under the eave stairs, by each door
}
