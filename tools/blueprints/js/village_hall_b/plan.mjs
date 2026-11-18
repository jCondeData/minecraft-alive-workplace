// Village Hall (Version B), the plan: drawn before any wall. Rooms, every furniture footprint (kind, cells, facing) and
// the walking line come first; recipe.mjs then assembles the three levels from the parts library (../parts) to fit it.
// Brief: tools/blueprints/briefs/village_hall.md (the hall block at (6, 1, 10) facing north, the meeting bell at
// (6, 4, 0), the front door at (6, 1, 3), Level I within 13 x 16 x 13 and 108 stacks).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. "The Long Hall": one 11 x 9 rectangle, a steep gable to the street with a bell-cote on its peak, benches in rows
//      facing the hall block; II adds a council wing behind, III a corner clock tower. Story: the village's barn of
//      meetings. Safe, legible, and what every village hall in every pack already looks like.
//   B. "The Market Loggia": a stone hall behind an arcade of three arches, the bell in the middle arch, a flat speaking
//      balcony over the arcade; II puts the council room upstairs behind the balcony, III a campanile. Story: the
//      council speaks to the square. Strong front, but a box from the sides and back, and a flat roof in a pitched-roof
//      village.
//   C. "The Moot House": an octagon (stretched one block along x to use the 13-wide plot) under a tall brick tent roof
//      with a little lantern louvre on top, a timber bell porch on the front face, and inside a stone-floored ring
//      where everyone sits on one bench running round the wall, facing each other, the hall block at the head of the
//      ring. II adds a long council wing on the east face (one table, eleven chairs); III lifts the roof's crown into an
//      octagonal lantern drum and a copper spire, so the hall itself becomes the village's landmark and the daylight
//      falls into the ring. Story: a chapter house / moot ring where nobody sits behind anybody.
// Strongest: C. It is the one with a silhouette you can name from 20 blocks away on every side (an octagon and a tent
// roof have no "back"), its plan is its story (a ring of equals round the village's records), and each level grows the
// same shape instead of bolting on a box: the wing reads as the council's arm, the crown as the hall standing up. A is
// the safe one; B has one good side. Built: C.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front (z = 0,
// north) to the back. The octagon: centre (6, 7), stretched e = 1 along x, corners cut 1; ring 4 is its wall (x 1..11,
// z 3..11), ring 5 its eave and stone skirt, rings 0..3 its floor and roof.
//
// Masses (named parts the owner's feedback can point at):
//   Moot ring     the octagon: a rubble plinth and apron, stone-brick walls y 1..5 with dressed quoins up the eight
//                 angles and an angle buttress in each cut corner's notch, a dark oak wall plate at y 6, crossed tie
//                 beams at y 6 (the lamps and pots hang from them), a calm 45-degree brick tent roof rising 6 -> 11
//                 over a dark oak eave kick, a lantern louvre on the ridge (y 11..13).
//   Bell porch    an open timber porch on the front face, x 4..8, z 0..2: two posts, a front beam the meeting bell hangs
//                 from at (6, 4, 0), knee braces either side of the bell, a little brick gable (ridge y 8) running back
//                 into the tent roof. The street gathers under and in front of it.
//   Council wing  (II) x 11..19, z 4..10 on the east face: stone walls y 1..4 with dark oak posts, a beam ring at y 5,
//                 a 45-degree brick gable roof (ridge along x, y 9), an east gable with a window, opened to the ring
//                 through the east face's middle.
//   Crown         (III) the tent roof's top replaced by an octagonal drum (true octagon, ring 2 of centre (6, 7), cut 1:
//                 x 4..8, z 5..9) of stone from the roof up to y 15 with a tall window on each face, a dark oak cornice
//                 at y 16, a green (waxed oxidized) copper spire to y 22 and a lightning rod finial; a great lantern
//                 hangs down the drum, seen through its windows at night and from the ring below.

export const SIZE = { 1: [13, 16, 13], 2: [21, 16, 13], 3: [21, 23, 13] }
export const IDS = { 1: 'village_hall', 2: 'village_hall_2', 3: 'village_hall_3' }

// Palette (60/30/10): stone bricks (walls, gently weathered low down) / red brick tile roof and dark oak timber (plate,
// beams, porch, benches, doors) / cobble plinth, a grey-and-white chequer floor, blue cloth (rug, banners, table baize
// is green), and green copper only on Level III's spire. Why: grey stone and a warm red roof read civic and permanent next
// to the village's timber houses; the dark oak draws the octagon's eave line and the porch; the green copper spire is the one
// thing in the village of its colour, which is what a landmark should be.
export const PALETTE = {
  plinth: [['cobblestone', 6], ['mossy_cobblestone', 2], ['stone', 1]],
  wallLow: [['stone_bricks', 6], ['mossy_stone_bricks', 2], ['cracked_stone_bricks', 1]],
  wall: [['stone_bricks', 9], ['cracked_stone_bricks', 1]],
  dressed: 'polished_andesite', // lintels, quoins and the lamp posts' footings
  pilaster: 'polished_deepslate', // the dark proud columns at the octagon's eight angles, base to eave
  skirt: 'stone_brick_stairs',
  sill: 'stone_brick_stairs',
  floorA: 'polished_andesite',
  floorB: 'polished_diorite',
  wingFloor: 'dark_oak_planks',
  roof: { full: 'bricks', stairs: 'brick_stairs', slab: 'brick_slab', kick: 'dark_oak_stairs' },
  spire: { full: 'waxed_oxidized_cut_copper', stairs: 'waxed_oxidized_cut_copper_stairs', kick: 'dark_oak_stairs' },
  timber: 'dark_oak_log',
  wood: 'dark_oak', // doors, stairs as seats, fences, trapdoors, slabs
  glass: 'glass_pane',
  cloth: 'blue',
  baize: 'green',
  job: 'aliveworkplace:village_hall',
  standin: 'lodestone', // the renderer can't draw the hall block: STANDIN=1 renders draw this in its place
}

export const OCT = { cx: 6, cz: 7, e: 1, cut: 1, wall: 4, wallTop: 5, plateY: 6, heights: [11, 10, 9, 8, 7, 6] }
export const BUTTRESSES = [[2, 4], [10, 4], [2, 10], [10, 10]] // in the notch of each cut corner: y 1..3, a sloped top at 4
// a pilaster a block proud of each of the eight angles (y 1..5, under the eave kick); the two on the front face are
// the porch's back posts, the two on the east face give way to the council wing in Level II
export const PILASTERS = [[4, 2], [8, 2], [0, 6], [0, 8], [12, 6], [12, 8], [4, 12], [8, 12]]
export const PORCH = { x0: 4, x1: 8, z0: 0, z1: 2, zWall: 3, beamY: 5 }
export const WING = { x0: 10, x1: 19, z0: 4, z1: 10, wallTop: 4, beamY: 5, ties: [13, 15, 17],
  posts: [[15, 4], [19, 4], [15, 10], [19, 10]],
  roof: { axis: 'x', across: [3, 11], along: [10, 20], eaveY: 5, steep: false } }
export const CROWN = { cx: 6, cz: 7, cut: 1, ring: 2, top: 15, heights: [21, 18, 16], floorY: 11,
  // the belfry: an open arch in the middle of each face, y 12..13, a lantern hung in its head at 14, a dark oak sill
  arches: [[6, 5, 'north'], [6, 9, 'south'], [4, 7, 'west'], [8, 7, 'east']], archY: [12, 13] } // the spire springs from the drum's wall top, its kick on the wall line

export const ROOMS = [
  { name: 'Moot ring', level: 1, use: 'the village meets here: one bench round the wall, the hall block at its head', box: [2, 1, 4, 10, 5, 10] },
  { name: 'Bell porch', level: 1, use: 'the meeting bell and the street under it', box: [4, 0, 0, 8, 4, 2] },
  { name: 'Council room', level: 2, use: 'the council table: eleven chairs, the records in two cupboards', box: [12, 1, 5, 18, 4, 9] },
  { name: 'Belfry', level: 3, use: 'the open lantern stage under the spire: the village light, seen from every side (lanterns, no bell)', box: [5, 12, 6, 7, 14, 8] },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 3], facing: 'south', out: 'north' }, // lintel (6, 3, 3) is the wall; (6, 1, 2) and (6, 1, 4) stay clear
  councilDoorway: { at: [11, 1, 7], height: 2, level: 2 }, // the east window becomes a doorway with a light over it
  windows: {
    1: [
      { name: 'front west', cells: [[5, 2, 3], [5, 3, 3]], out: 'north' },
      { name: 'front east', cells: [[7, 2, 3], [7, 3, 3]], out: 'north' },
      { name: 'west', cells: [[1, 2, 7], [1, 3, 7], [1, 4, 7]], out: 'west' },
      { name: 'east', cells: [[11, 2, 7], [11, 3, 7], [11, 4, 7]], out: 'east' },
      { name: 'back west', cells: [[5, 2, 11], [5, 3, 11], [5, 4, 11]], out: 'south' },
      { name: 'back', cells: [[6, 2, 11], [6, 3, 11], [6, 4, 11]], out: 'south' }, // the light falls on the hall block
      { name: 'back east', cells: [[7, 2, 11], [7, 3, 11], [7, 4, 11]], out: 'south' },
    ],
    2: [
      { name: 'wing north west', cells: [[13, 2, 4], [13, 3, 4]], out: 'north' },
      { name: 'wing north east', cells: [[17, 2, 4], [17, 3, 4]], out: 'north' },
      { name: 'wing south west', cells: [[13, 2, 10], [13, 3, 10]], out: 'south' },
      { name: 'wing south east', cells: [[17, 2, 10], [17, 3, 10]], out: 'south' },
      { name: 'wing east north', cells: [[19, 2, 6], [19, 3, 6]], out: 'east' },
      { name: 'wing east south', cells: [[19, 2, 8], [19, 3, 8]], out: 'east' },
      { name: 'wing gable', cells: [[19, 6, 7], [19, 7, 7]], out: 'east', bare: true },
    ],
    3: [], // the drum's openings are the belfry's arches (CROWN.arches)
  },
}

// Every piece of furniture, footprint first, by the level that adds it. kind: a furniture.py kind (recorded as a spot)
// or a fixture. part: the part that draws it. facing: the way a person using it faces. Removed by a later level: `gone`.
const W = 'dark_oak'
const bench = (cells, facing) => ({ room: 'Moot ring', kind: 'bench', part: 'bench', cells, facing, wood: W })
export const FURNITURE = {
  1: [
    // the ring bench: west and east walls whole, the front and back walls either side of the door and the hall block
    bench([[3, 5], [4, 4], [5, 4], [7, 4], [8, 4], [9, 5]], 'south'),
    bench([[2, 6], [2, 7], [2, 8]], 'east'),
    bench([[10, 6], [10, 8]], 'west'),
    bench([[10, 7]], 'west'), // the seat Level II takes out for the council doorway
    bench([[3, 9], [9, 9]], 'north'),
    // the head of the ring: a side table with a lamp either side of the banners and the hall block, a red runner
    // up the aisle to it, and a banner on the wall over each corner seat
    { room: 'Moot ring', kind: 'lamp', part: 'tableLamp', pos: [4, 1, 10], facing: 'north', wood: W },
    { room: 'Moot ring', kind: 'lamp', part: 'tableLamp', pos: [8, 1, 10], facing: 'north', wood: W },
    { room: 'Moot ring', kind: 'rug', part: 'rug', colour: 'red', cellsAbs: [[6, 1, 5], [6, 1, 9]] },
    ...[[3, 5, 'south', 'blue'], [9, 5, 'south', 'white'], [3, 9, 'north', 'white'], [9, 9, 'north', 'blue']].map(([x, z, f, col]) =>
      ({ room: 'Moot ring', kind: 'picture', part: 'picture', pos: [x, 3, z], facing: f, wood: W, colour: col })),
    // the hall block at the ring's head, a standing banner either side of it facing the door
    { room: 'Moot ring', kind: 'fixture', part: 'job', pos: [6, 1, 10], facing: 'north' },
    { room: 'Moot ring', kind: 'fixture', part: 'standard', pos: [5, 1, 10], rotation: 8 },
    { room: 'Moot ring', kind: 'fixture', part: 'standard', pos: [7, 1, 10], rotation: 8 },
    // light: a lantern at the beams' crossing and one over each end of the aisle; pots hung from the cross beam
    { room: 'Moot ring', kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 7], chain: 1, facing: 'north', wood: W },
    { room: 'Moot ring', kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 5], chain: 1, facing: 'north', wood: W },
    { room: 'Moot ring', kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 9], chain: 1, facing: 'north', wood: W },
    { room: 'Moot ring', kind: 'hanging_pot', part: 'hangingPot', pos: [3, 5, 7], plant: 'potted_fern', wood: W },
    { room: 'Moot ring', kind: 'hanging_pot', part: 'hangingPot', pos: [9, 5, 7], plant: 'potted_fern', wood: W },
    { room: 'Moot ring', kind: 'rug', part: 'rug', colour: 'blue', cellsAbs: [[5, 1, 6], [6, 1, 6], [7, 1, 6], [4, 1, 7], [5, 1, 7], [6, 1, 7], [7, 1, 7], [8, 1, 7], [5, 1, 8], [6, 1, 8], [7, 1, 8]] },
    // the porch lantern over the door, hung from the porch's boarded ceiling
    { room: 'Bell porch', kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 2], chain: 0, facing: 'north', wood: W },
  ],
  2: [
    { gone: [[10, 1, 7]] }, // the bench seat in front of the new doorway
    // the council table: five tables in a row, five chairs either side, the chair at the head under the banner
    ...[13, 14, 15, 16, 17].map(x => ({ room: 'Council room', kind: 'table', part: 'table', pos: [x, 1, 7], facing: 'north', wood: W, colour: 'green' })),
    ...[13, 14, 15, 16, 17].map(x => ({ room: 'Council room', kind: 'chair', part: 'chair', pos: [x, 1, 6], facing: 'south', wood: W })),
    ...[13, 14, 15, 16, 17].map(x => ({ room: 'Council room', kind: 'chair', part: 'chair', pos: [x, 1, 8], facing: 'north', wood: W })),
    { room: 'Council room', kind: 'chair', part: 'chair', pos: [18, 1, 7], facing: 'west', wood: W },
    { room: 'Council room', kind: 'picture', part: 'picture', pos: [18, 3, 7], facing: 'west', wood: W, colour: 'blue' },
    { room: 'Council room', kind: 'cupboard', part: 'cupboard', pos: [18, 1, 5], facing: 'west', wood: W },
    { room: 'Council room', kind: 'cupboard', part: 'cupboard', pos: [18, 1, 9], facing: 'west', wood: W },
    ...[13, 15, 17].map(x => ({ room: 'Council room', kind: 'lamp', part: 'hangingLamp', pos: [x, 4, 7], chain: 0, facing: 'north', wood: W })),
    { room: 'Council room', kind: 'hanging_pot', part: 'hangingPot', pos: [15, 4, 5], plant: 'potted_azure_bluet', wood: W },
    { room: 'Council room', kind: 'hanging_pot', part: 'hangingPot', pos: [15, 4, 9], plant: 'potted_azure_bluet', wood: W },
  ],
  3: [
    // the great lantern down the drum, on a long chain from the spire's ridge block
    // the belfry's lanterns: a great one in the middle on a chain from the ceiling, one in the head of each arch
    { room: 'Belfry', kind: 'lamp', part: 'hangingLamp', pos: [6, 13, 7], chain: 1, facing: 'north', wood: W },
    ...[[6, 5], [6, 9], [4, 7], [8, 7]].map(([x, z]) => ({ room: 'Belfry', kind: 'lamp', part: 'hangingLamp', pos: [x, 14, z], chain: 0, facing: 'north', wood: W })),
    // the corona: four lanterns let down from the belfry floor into the ring, over the rug and the benches' faces
    ...[[5, 6], [7, 6], [5, 8], [7, 8]].map(([x, z]) => ({ room: 'Moot ring', kind: 'lamp', part: 'hangingLamp', pos: [x, 4, z], chain: 6, facing: 'north', wood: W })),
  ],
}

// The walking line: the street -> the porch (under the bell) -> the door -> up the aisle to the hall block; II: across the
// ring to the council doorway and round the table.
export const WALK = {
  1: [[6, 0, -1], [6, 1, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [6, 1, 7], [6, 1, 8], [6, 1, 9]],
  2: [[6, 1, 7], [7, 1, 7], [8, 1, 7], [9, 1, 7], [10, 1, 7], [11, 1, 7], [12, 1, 7], [12, 1, 6], [12, 1, 5], [15, 1, 5], [18, 1, 6]],
}

// Outside (not furniture spots): flower beds in their own soil against the front corners, a lamp post either side of
// the porch, stepped paving under the porch.
export const OUTSIDE = {
  // raised beds: a rubble rim coped with slabs round grass, flowers in it (the lamp post stands in the rim's corner)
  flowerBeds: [{ x0: 0, x1: 3, z0: 0, z1: 2, plants: ['cornflower', 'oxeye_daisy'] }, { x0: 9, x1: 12, z0: 0, z1: 2, plants: ['oxeye_daisy', 'cornflower'] }],
  lampPosts: [[3, 0], [9, 0]],
}
