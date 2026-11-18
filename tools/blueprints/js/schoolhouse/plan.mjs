// Schoolhouse (ROADMAP 49, tools/blueprints/briefs/schoolhouse.md), the plan: drawn before any wall.
//
// Three rough concepts:
//   A. "Bell-cote Schoolroom": one tall open hall, its steep front gable to the street, a green copper bell-cote on
//      the gable's peak, a little gabled cloak-porch at the door. Plan: porch -> aisle between bench rows -> lectern
//      before a blackboard on the back wall. Story: a village that can afford one good room builds it tall and bright.
//   B. "Two Rooms Under One Ridge": a long low cottage, ridge along x, the schoolroom one end and the teacher's
//      lodging the other, a door in the middle. Story: the teacher lives where he teaches. (Reads as a house.)
//   C. "Octagon School": an eight-sided room under a tent roof, lectern in the middle, benches in a ring. Story: no
//      child sits at the back. (Charming but fights the brief's "benches facing the desk" camera and the lectern rules.)
// Strongest: A. It reads as a school from the street before you see inside: the bell that calls the children, tall
// windows (daylight for reading), and a room open to the rafters with a truss you can count. B is a house; C is a
// pavilion. A's masses also grow cleanly: Level II adds a lower cross wing (the infants' reading room) on the east,
// so the hall stays the hero and the building becomes an L that frames the doorstep.
//
// Story: the "Lantern Schoolroom". Whitewashed board walls (birch) on a stone-brick plinth, a dark-oak frame,
// a steep slate (deepslate tile) roof, and the one bright note the village paid extra for: the green copper cap of
// the bell-cote. Inside, open to the roof, three tall windows a side light two rows of benches facing the lectern
// and the blackboard.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front (0).
// Masses (named parts):
//   Hall      the schoolroom: walls x 1..9, z 3..11, y 1..4, beam ring y 5, a 45-degree roof (ridge along z, y 10)
//             over x 0..10, z 2..12; open inside to the rafters, two tie beams at y 5 (z 6, z 9).
//   Porch     the cloak porch on the front: walls x 3..7, z 1..2, its own gable roof (ridge y 6) to z 0; front door.
//   Bellcote  on the front gable's peak (centre x 5, z 3): a stone base at y 11, four fence posts, a copper cap,
//             a lightning-rod finial, the bell hanging inside.
//   Wing (II) the infants' reading room on the east: walls x 9..15, z 4..10, y 1..3, a lower roof (ridge along x, y 8)
//             to x 16, entered from the Hall through its middle east bay.
export const IDS = { 1: 'schoolhouse', 2: 'schoolhouse_2' }
export const SIZES = { 1: [11, 16, 13], 2: [17, 16, 13] } // limits: I 13 x 16 x 13, II 21 x 18 x 27

// Palette (60/30/10): whitewashed birch boards (walls) / deepslate slate roof + dark oak frame / stone-brick plinth,
// spruce floor, oxidized copper bell-cote cap. Why: a pale room reads as clean and bright (a school, not a barn); the
// dark roof and frame give it weight; the copper is the one accent, high up where children see it from the lanes.
export const PALETTE = {
  stone: [['stone_bricks', 6], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1]],
  wall: 'birch_planks',
  frame: 'dark_oak_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  cap: { full: 'oxidized_cut_copper', stairs: 'oxidized_cut_copper_stairs' },
  floor: 'spruce_planks',
  wood: 'spruce',
  trim: 'dark_oak',
  glass: 'glass_pane',
  board: 'polished_blackstone', // the blackboard
}

export const HALL = {
  walls: { x0: 1, x1: 9, z0: 3, z1: 11 }, room: { x0: 2, x1: 8, z0: 4, z1: 10 },
  wallTop: 4, beamY: 5,
  sidePosts: [3, 6, 9, 11], // posts up the long walls; the bays between hold tall windows
  ties: [6, 9],
  roof: { axis: 'z', across: [0, 10], along: [2, 12], eaveY: 5, steep: false },
}
export const PORCH = {
  walls: { x0: 3, x1: 7, z0: 1, z1: 2 },
  roof: { axis: 'z', across: [3, 7], along: [0, 2], eaveY: 4, steep: false },
}
export const BELLCOTE = { cx: 5, cz: 3, y: 11 }
export const WING = {
  walls: { x0: 9, x1: 15, z0: 4, z1: 10 }, room: { x0: 10, x1: 14, z0: 5, z1: 9 },
  wallTop: 3, beamY: 4,
  roof: { axis: 'x', across: [3, 11], along: [6, 16], eaveY: 4, steep: false },
}

export const ROOMS = [
  { name: 'Porch', use: 'cloaks off, a lantern overhead', box: [4, 1, 2, 6, 3, 2] },
  { name: 'Schoolroom', use: 'two rows of benches either side of an aisle, facing the lectern and blackboard', box: [2, 1, 4, 8, 4, 10] },
  { name: 'Reading room (II)', use: 'the infants: a long table with a bench, a desk, a picture', box: [10, 1, 5, 14, 3, 9], level: 2 },
]

export const OPENINGS = {
  frontDoor: { at: [5, 1, 1], facing: 'south', step: [5, 0, 0] },
  hallDoorway: { at: [5, 1, 3], height: 2 },
  wingDoorway: { at: [9, 1, 8], height: 2, level: 2 },
  windows: [
    // tall schoolroom windows, three high, in each bay of both long walls
    ...[[4, 5], [7, 8], [10]].flatMap((zs, i) => ['west', 'east'].map(side => ({
      name: `hall ${side} ${i}`, out: side, dress: 'sill', bay: i,
      cells: zs.flatMap(z => [2, 3, 4].map(y => [side === 'west' ? 1 : 9, y, z])),
    }))),
    { name: 'front gable', cells: [[5, 7, 3], [5, 8, 3]], out: 'north', dress: null },
    { name: 'back gable', cells: [[5, 7, 11]], out: 'south', dress: null },
    { name: 'porch west', cells: [[4, 2, 1]], out: 'north', dress: null },
    { name: 'porch east', cells: [[6, 2, 1]], out: 'north', dress: null },
    { name: 'wing north', cells: [[11, 2, 4], [12, 2, 4], [13, 2, 4], [11, 3, 4], [12, 3, 4], [13, 3, 4]], out: 'north', dress: 'sill', level: 2 },
    { name: 'wing south', cells: [[11, 2, 10], [12, 2, 10], [13, 2, 10], [11, 3, 10], [12, 3, 10], [13, 3, 10]], out: 'south', dress: 'sill', level: 2 },
    { name: 'wing east south', cells: [[15, 2, 6], [15, 3, 6]], out: 'east', dress: 'shutters', level: 2 },
    { name: 'wing east north', cells: [[15, 2, 8], [15, 3, 8]], out: 'east', dress: 'shutters', level: 2 },
    { name: 'wing gable', cells: [[15, 5, 7], [15, 6, 7]], out: 'east', dress: null, level: 2 },
  ],
}

// Every piece of furniture, footprint first (kind: a furniture.py kind; part: the furnitureParts/schoolParts part).
// The lectern stands at (5, 1, 9) facing north, the Teacher's cell behind it (5, 1, 10); the class stands in the aisle
// and the row in front (z 5..8, all within 5 of the lectern).
export const LECTERN = { pos: [5, 1, 9], facing: 'north' }
export const FURNITURE = [
  // Schoolroom: bench rows facing south (toward the lectern), an aisle at x 5
  { room: 'Schoolroom', kind: 'bench', part: 'bench', at: { x0: 2, x1: 4, y: 1, z: 5 }, facing: 'south', wood: 'spruce' },
  { room: 'Schoolroom', kind: 'bench', part: 'bench', at: { x0: 6, x1: 8, y: 1, z: 5 }, facing: 'south', wood: 'spruce' },
  { room: 'Schoolroom', kind: 'bench', part: 'bench', at: { x0: 2, x1: 4, y: 1, z: 7 }, facing: 'south', wood: 'spruce' },
  { room: 'Schoolroom', kind: 'bench', part: 'bench', at: { x0: 6, x1: 8, y: 1, z: 7 }, facing: 'south', wood: 'spruce' },
  // the teacher's desks either side of the lectern, against the blackboard wall
  // the front of class: two-wide desks either side of the lectern, each with its chair behind it facing the class
  { room: 'Schoolroom', kind: 'desk', part: 'desk', pos: [2, 1, 9], facing: 'north', wood: 'dark_oak' },
  { room: 'Schoolroom', kind: 'desk', part: 'desk', pos: [3, 1, 9], facing: 'north', wood: 'dark_oak' },
  { room: 'Schoolroom', kind: 'chair', part: 'chair', pos: [3, 1, 10], facing: 'north', wood: 'dark_oak' },
  { room: 'Schoolroom', kind: 'desk', part: 'desk', pos: [7, 1, 9], facing: 'north', wood: 'dark_oak' },
  { room: 'Schoolroom', kind: 'desk', part: 'desk', pos: [8, 1, 9], facing: 'north', wood: 'dark_oak' },
  { room: 'Schoolroom', kind: 'chair', part: 'chair', pos: [7, 1, 10], facing: 'north', wood: 'dark_oak' },
  { room: 'Schoolroom', kind: 'picture', part: 'picture', pos: [2, 3, 9], facing: 'east', wood: 'spruce', colour: 'green' },
  { room: 'Schoolroom', kind: 'picture', part: 'picture', pos: [8, 3, 9], facing: 'west', wood: 'spruce', colour: 'orange' },
  { room: 'Schoolroom', kind: 'picture', part: 'picture', pos: [2, 3, 6], facing: 'east', wood: 'spruce', colour: 'light_blue' },
  { room: 'Schoolroom', kind: 'picture', part: 'picture', pos: [8, 3, 6], facing: 'west', wood: 'spruce', colour: 'yellow' },
  { room: 'Schoolroom', kind: 'cupboard', part: 'cupboard', pos: [2, 1, 10], facing: 'east', wood: 'spruce' },
  { room: 'Schoolroom', kind: 'cupboard', part: 'cupboard', pos: [8, 1, 10], facing: 'west', wood: 'spruce' },
  { room: 'Schoolroom', kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 6], facing: 'north', wood: 'spruce' },
  { room: 'Schoolroom', kind: 'lamp', part: 'hangingLamp', pos: [7, 4, 6], facing: 'north', wood: 'spruce' },
  { room: 'Schoolroom', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 9], facing: 'north', wood: 'spruce' },
  { room: 'Schoolroom', kind: 'rug', part: 'rug', cellsAbs: [[4, 1, 10], [5, 1, 10], [6, 1, 10]], colour: 'green' },
  { room: 'Porch', kind: 'lamp', part: 'hangingLamp', pos: [5, 5, 2], facing: 'north', wood: 'spruce' },
  // Reading room (II): a long table with a bench along the south wall, a desk, a picture on the gable wall
  { room: 'Reading room (II)', kind: 'table', part: 'table', pos: [11, 1, 8], facing: 'south', wood: 'spruce', colour: 'white', level: 2 },
  { room: 'Reading room (II)', kind: 'table', part: 'table', pos: [12, 1, 8], facing: 'south', wood: 'spruce', colour: 'white', level: 2 },
  { room: 'Reading room (II)', kind: 'table', part: 'table', pos: [13, 1, 8], facing: 'south', wood: 'spruce', colour: 'white', level: 2 },
  { room: 'Reading room (II)', kind: 'bench', part: 'bench', at: { x0: 11, x1: 13, y: 1, z: 9 }, facing: 'north', wood: 'spruce', level: 2 },
  { room: 'Reading room (II)', kind: 'desk', part: 'desk', pos: [14, 1, 5], facing: 'east', wood: 'dark_oak', level: 2 },
  { room: 'Reading room (II)', kind: 'picture', part: 'picture', pos: [14, 3, 7], facing: 'west', wood: 'spruce', colour: 'red', level: 2 },
  { room: 'Reading room (II)', kind: 'cupboard', part: 'cupboard', pos: [10, 1, 5], facing: 'south', wood: 'spruce', level: 2 },
  { room: 'Reading room (II)', kind: 'lamp', part: 'hangingLamp', pos: [12, 5, 7], facing: 'north', chain: 2, wood: 'spruce', level: 2 },
  { room: 'Reading room (II)', kind: 'rug', part: 'rug', cellsAbs: [[11, 1, 6], [12, 1, 6], [13, 1, 6], [11, 1, 7], [12, 1, 7], [13, 1, 7]], colour: 'light_blue', level: 2 },
]

// The walking line: step -> front door -> porch -> hall doorway -> the aisle -> the class row before the lectern
// (II: on through the east doorway into the reading room).
export const WALK = [
  [5, 0, 0], [5, 1, 1], [5, 1, 2], [5, 1, 3], [5, 1, 4], [5, 1, 5], [5, 1, 6], [5, 1, 7], [5, 1, 8], [4, 1, 8], [6, 1, 8],
  [6, 1, 9], [6, 1, 10], [5, 1, 10],
  [7, 1, 8], [8, 1, 8], [9, 1, 8], [10, 1, 8], [10, 1, 7], [11, 1, 7],
]

// Outside: flower beds either side of the porch, against the hall's front wall.
// Level II's schoolyard, before the wing on the street side: a fence with a gate, a bench against the wing, an azalea.
export const YARD = { fence: { x0: 10, x1: 16, z0: 0, z1: 3 }, gate: [13, 0], bench: { x0: 11, x1: 13, y: 0, z: 3 }, tree: [15, 1] }
// The west dormer: lights the schoolroom's middle bay and breaks the long west slope.
export const DORMER = { face: 1, eave: 0, z: 7, y0: 6 }
export const OUTSIDE = {
  flowerBeds: [{ x0: 1, x1: 2, z: 2, plants: ['cornflower', 'oxeye_daisy'] }, { x0: 8, x1: 9, z: 2, plants: ['poppy', 'allium'] }],
}
