// Farmstead (farmstead, farmstead_2, farmstead_3; ROADMAP 49), the plan: drawn before any wall. The brief is
// tools/blueprints/briefs/farmstead.md. Rooms, every furniture footprint (kind, cells, facing) and the walking line come
// first; recipe.mjs then assembles the farm from the parts library (../parts) to fit this plan.
//
// Three rough concepts (massing / roof / plan / story):
//   1. The Connected Farm. One long range down the west of the plot under ONE catslide roof: a short steep slope to
//      the west, a long low sweep east that comes down to head height over a gallery on the field's edge. II carries
//      the same roof on back over a cross passage into a red barn; III digs a root cellar under the house. Plan: house
//      then cross passage then barn in a line, the gallery along all of it, the fenced field beside it. Story: the farmer
//      works from under his own eave: composter and harvest chest stand on the gallery looking straight onto the rows.
//   2. The Granary Yard. A square two-storey stone house with a hipped roof on the front corner, the field in a walled
//      yard behind, a little granary on staddle stones by the gate; II a second granary and field. Story: a careful
//      farmer guarding his grain from rats.
//   3. The Bank Barn. A timber loft where the farmer sleeps over a stone byre, built into a bank with an earth ramp to
//      the loft door, field at the foot; II a threshing floor and second byre. Story: animals below keep him warm.
// Strongest: 1. It puts the job where it happens (both composters and their chests on the gallery, a step from the
// farmland), the asymmetric catslide gives an outline nobody mistakes for a cottage (tall west wall with a chimney
// stack, a long roof falling almost to the ground on the field side), and the levels grow along one roof line: II
// literally continues the roof, so the upgrade reads as the farm prospering, not a second building dropped beside it.
// 2 is a pleasant house next to a field; 3 can't keep I's kept cells (bed, composter, chests) on one level.
//
// Coordinates: x across the front (west 0 -> east 14), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Kept cells from the old drawings (the brief's "Kept cells"): composters (8, 1, 4) and
// (8, 1, 13), chests (6, 1, 3) W, (8, 1, 6) E, (8, 1, 19) E, bed (6, 1, 6)->(6, 1, 7) S, cellar chests (2, -3, 4..6) E.
// The field stays on its pins: farmland x 9..13, z 2..10 at y 0.
//
// Masses (named parts the owner's feedback can point at):
//   House      whitewashed walls x 1..7, z 1..8 on a cobble plinth, dark oak corner posts; walls y 1..6 on the west,
//              1..4 on the east (the catslide's low side); boarded upper gables over a dark oak beam at y 5, a pent
//              roof across the front gable over the door and the ground-floor windows.
//   Roof       one catslide, ridge along z at x 3 (y 9): west eave x 0 (y 6), east eave x 8 (y 4) over the gallery;
//              I runs z 0..9 (a back porch at z 9), II carries it to z 21 over the cross passage and the barn.
//   Chimney    a cobble stack on the west wall (x 0, z 5) to y 11, a stepped breast, the hearth inside the wall.
//   Gallery    the field walk: a boarded deck x 8 under the east eave (z 1..9 in I, 1..20 in II), upside-down stair
//              brackets under the eave; the composters and harvest chests stand on it.
//   Field      fenced on a cobble kerb (x 9..14, z 1..11), a water channel down x 11, the scarecrow on a footing in the
//              channel at z 6, lanterns on the corner posts. II adds the second field z 12..20 behind its own kerb.
//   Passage    (II) the old back porch, walled at both ends with a door in each: the longhouse's cross passage at
//              z 9 between house and barn, the way through from the west yard and the hall to the gallery.
//   Barn       (II) red boards (mangrove) x 1..7, z 10..20, the second Farmer's bed, a hay loft on a ladder at the
//              back, wagon doors in the back gable with a hay hoist over them, a louvred cupola on the ridge, and a
//              steep cross gable out over the gallery facing the field, over the second composter.
//   Cellar     (III) a root cellar under the house x 1..7, z 1..8, 4 deep, down a ladder under a hatch inside the door.

export const SIZE = { 1: [15, 13, 12], 2: [15, 15, 22], 3: [15, 15, 22] } // w, h, d (limits: 18 x 28 x 21 for I, 26 deep for II)
export const BASEMENT = { 3: 4 }
export const IDS = { 1: 'farmstead', 2: 'farmstead_2', 3: 'farmstead_3' }

// Palette (60/30/10): walls 60 (limewash on the house, red boards on II's barn) / slate roof and boarded
// gables 30 / dark oak frame, cobble plinth and kerb 10, with the field's greens and the scarecrow's orange as the
// accents. Why: a working farm reads in its colours: a white house you can see across the fields, a red barn, a dark
// slate roof; the cobble kerb and plinth are the stones picked out of the field.
export const PALETTE = {
  stone: [['cobblestone', 6], ['stone', 2], ['andesite', 1], ['mossy_cobblestone', 2]],
  kerb: [['cobblestone', 5], ['mossy_cobblestone', 3], ['stone', 1]],
  wall: 'calcite', // limewash
  boards: 'stripped_spruce_log', // the upper gables, set upright like boards
  barn: 'mangrove_planks', // red barn boards
  frame: 'dark_oak_log',
  trim: 'dark_oak',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'spruce_planks',
  deck: 'spruce_planks',
  barnFloor: 'packed_mud', // the threshing floor, beaten earth
  wood: 'spruce', // doors, trapdoors, fences, furniture
  glass: 'glass_pane',
  cellarWall: [['cobblestone', 4], ['stone', 3], ['mossy_cobblestone', 2]],
  cellarFloor: 'stone_bricks',
}

export const MASSES = {
  house: { x0: 1, x1: 7, z0: 1, z1: 8, room: { x0: 2, x1: 6, z0: 2, z1: 7 }, beamY: 5, tieBeams: [4, 7] },
  roof: { axis: 'z', across: [0, 8], ridge: 3, ridgeY: 9, along: [0, 9] }, // the house's (and the passage's)
  barnRoof: { ridgeY: 11, along: [10, 21] }, // II: the barn's, the same line two blocks higher (eave y 6 over the gallery)
  chimney: { x: 0, z: 5, top: 11, breastTop: 5 },
  gallery: { x: 8, z0: 1, z1: { 1: 9, 2: 20 }, posts: { 1: [8], 2: [8, 15, 20] } }, // posts off the walking line
  field: { x0: 9, x1: 13, z0: 2, z1: 10, channelX: 11, scarecrowZ: 6 },
  field2: { x0: 9, x1: 13, z0: 12, z1: 20, channelX: 11, water: [13, 19] }, // channel z 13..19, farmland at its ends
  breezeway: { z: 9 }, // the cross passage (II)
  barn: { x0: 1, x1: 7, z0: 10, z1: 20, room: { x0: 2, x1: 6, z0: 11, z1: 19 }, tieBeams: [12, 14], loft: { y: 5, z0: 16, z1: 19 },
    // the second Farmer's chamber under the loft (x 2..3, z 17..19): a board wall at z 16 with a doorway at x 3, and at x 4
    chamber: { wallZ: 16, doorX: 3, wallX: 4, z0: 17, z1: 19 },
    // two stalls down the east wall (x 6), a fence front at x 5 with a gate to each, a sunken water trough in each
    stalls: { front: 5, z0: 11, z1: 15, gates: [12, 14], dividers: [13, 16], troughs: [11, 14], hay: [12, 15] },
    tools: { x: 2, z: [11, 12] } }, // hooks on the boards over the bench (B1452: the pane at z 13 can't hold one)
  cupola: { x: 3, z: 15 },
  crossGable: { across: [13, 17], along: [5, 8], window: { z: 15, y: [6, 7] } }, // II: over the second composter, eave on the barn's
  // Proud frame: posts and a beam one block out from every face, the infill in the wall plane behind them
  frame: {
    front: { z: 0, posts: [1, 3, 5, 7], top: 4 },
    west: { x: 0, posts: { 1: [1, 8], 2: [1, 8, 10, 15, 20] }, beamY: 5, skip: [4, 5, 6] },
    back: { z: 9, posts: [1, 7] }, // I only: the passage takes z 9 in II
    east: { x: 7, posts: [4, 6] }, // the field side: posts in the wall plane (the gallery's deck takes the block out)
    barnBack: { z: 21, posts: [1, 7], beamY: 5 },
  },
  cellar: { x0: 1, z0: 1, x1: 7, z1: 8, depth: 4, ladder: { x: 2, z: 2, facing: 'south' } },
}

export const ROOMS = [
  { name: 'Hall', use: 'the farmer\'s one room: hearth, table, box bed, harvest chest', box: [2, 1, 2, 6, 4, 7] },
  { name: 'Gallery', use: 'the field walk: composters and harvest chests under the eave', box: [8, 1, 1, 8, 2, 20] },
  { name: 'Barn', level: 2, use: 'the second Farmer\'s bed, hay, a loft on a ladder', box: [2, 1, 11, 6, 4, 19] },
  { name: 'Cellar', level: 3, use: 'the root cellar: three chests, produce racks, hay', box: [2, -3, 2, 6, -1, 7] },
]

export const OPENINGS = {
  frontDoor: { at: [4, 1, 1], facing: 'south', out: 'north', step: [4, 0, 0] },
  backDoor: { at: [4, 1, 8], facing: 'north', out: 'south' }, // onto the back porch (I), the cross passage (II)
  barnDoor: { level: 2, at: [4, 1, 10], facing: 'south', out: 'north' }, // from the cross passage
  barnSideDoor: { level: 2, at: [7, 1, 17], facing: 'west', out: 'east' }, // onto the gallery
  wagonDoors: { level: 2, at: [[5, 1, 20], [6, 1, 20]], facing: 'north', out: 'south' },
  hayDoor: { level: 2, at: [4, 6, 20] },
  galleryStep: [8, 0, 0],
  breezewayStep: { level: 2, at: [0, 0, 9] },
  hatch: { level: 3, at: [2, 0, 2] },
  windows: [
    { name: 'hall front west', cells: [[2, 2, 1], [2, 3, 1]], out: 'north', dress: null }, // between proud posts
    { name: 'hall front east', cells: [[6, 2, 1], [6, 3, 1]], out: 'north', dress: null },
    { name: 'front gable', cells: [[3, 7, 1]], out: 'north', dress: null },
    { name: 'hall west', cells: [[1, 2, 2], [1, 3, 2]], out: 'west', dress: 'sill' },
    { name: 'hall west back', cells: [[1, 2, 7], [1, 3, 7]], out: 'west', dress: 'sill' },
    { name: 'hall east', cells: [[7, 2, 5], [7, 3, 5]], out: 'east', dress: null }, // onto the gallery and the field
    // the field side's rhythm (the critic's note): a window in each bay between the wall-plane posts at z 4 and 6
    { name: 'hall east front', cells: [[7, 2, 3], [7, 3, 3]], out: 'east', dress: null },
    { name: 'hall east back', cells: [[7, 2, 7], [7, 3, 7]], out: 'east', dress: null },
    { name: 'hall back', cells: [[2, 2, 8], [2, 3, 8]], out: 'south', dress: 'sill', until: 1 }, // and a shutter; plain once the passage is built
    { name: 'hall back plain', level: 2, cells: [[2, 2, 8], [2, 3, 8]], out: 'south', dress: null },
    { name: 'back gable', cells: [[3, 7, 8]], out: 'south', dress: null },
    { name: 'barn west', level: 2, cells: [[1, 2, 13], [1, 3, 13]], out: 'west', dress: 'shutters' },
    { name: 'barn west back', level: 2, cells: [[1, 2, 17], [1, 3, 17]], out: 'west', dress: 'shutters' },
    { name: 'barn east', level: 2, cells: [[7, 2, 12], [7, 3, 12]], out: 'east', dress: null },
    { name: 'barn east back', level: 2, cells: [[7, 2, 19], [7, 3, 19]], out: 'east', dress: null },
  ],
}

// Every piece of furniture, footprint first (see house_t1_b/plan.mjs). `level`: the first level it's drawn in.
export const FURNITURE = [
  // Hall: the hearth in the west wall, a table for two by it, the box bed in the south-east corner, the harvest chest
  // on the east wall, a cupboard and a clock, a lamp on a side table by the door, two lamps hung from the tie beams
  { room: 'Hall', kind: 'fixture', part: 'hearth', pos: [1, 1, 5], facing: 'east', block: 'campfire' },
  { room: 'Hall', kind: 'table', part: 'table', pos: [3, 1, 5], facing: 'north', wood: 'spruce', colour: 'red' },
  { room: 'Hall', kind: 'chair', part: 'chair', pos: [3, 1, 4], facing: 'south', wood: 'spruce' },
  { room: 'Hall', kind: 'chair', part: 'chair', pos: [3, 1, 6], facing: 'north', wood: 'spruce' },
  { room: 'Hall', kind: 'bed', part: 'bed', pos: [6, 1, 6], facing: 'south', colour: 'green' }, // kept cells
  { room: 'Hall', kind: 'nightstand', part: 'nightstand', pos: [5, 1, 7], facing: 'north', wood: 'spruce' },
  { room: 'Hall', kind: 'fixture', part: 'chest', pos: [6, 1, 3], facing: 'west', block: 'chest' }, // kept cell
  { room: 'Hall', kind: 'cupboard', part: 'cupboard', pos: [2, 1, 3], facing: 'east', wood: 'spruce' },
  { room: 'Hall', kind: 'clock', part: 'clock', pos: [2, 1, 7], facing: 'east', wood: 'spruce' },
  { room: 'Hall', kind: 'lamp', part: 'tableLamp', pos: [6, 1, 2], facing: 'west', wood: 'spruce' },
  { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 4], facing: 'north', wood: 'spruce' },
  { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 7], facing: 'north', wood: 'spruce' },
  { room: 'Hall', kind: 'rug', part: 'rug', cellsAbs: [[4, 1, 4], [5, 1, 4], [4, 1, 5], [5, 1, 5], [4, 1, 6], [5, 1, 6]], colour: 'brown' },
  // Gallery: the Farmer's composter and his harvest chest (kept cells), II's second pair
  { room: 'Gallery', kind: 'fixture', part: 'composter', pos: [8, 1, 4], facing: 'east' },
  { room: 'Gallery', kind: 'fixture', part: 'chest', pos: [8, 1, 6], facing: 'east', block: 'chest' },
  { room: 'Gallery', level: 2, kind: 'fixture', part: 'composter', pos: [8, 1, 13], facing: 'east' },
  { room: 'Gallery', level: 2, kind: 'fixture', part: 'chest', pos: [8, 1, 19], facing: 'east', block: 'chest' },
  // Barn: the second Farmer's chamber under the loft (bed, nightstand, his own chest, a lamp), lamps over the floor
  { room: 'Barn', level: 2, kind: 'bed', part: 'bed', pos: [2, 1, 18], facing: 'south', colour: 'yellow' },
  { room: 'Barn', level: 2, kind: 'nightstand', part: 'nightstand', pos: [3, 1, 19], facing: 'north', wood: 'spruce' },
  { room: 'Barn', level: 2, kind: 'fixture', part: 'chest', pos: [3, 1, 18], facing: 'north', block: 'chest' },
  { room: 'Barn', level: 2, kind: 'rug', part: 'rug', cellsAbs: [[2, 1, 17]], colour: 'yellow' },
  { room: 'Barn', level: 2, kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 17], facing: 'north', wood: 'spruce' },
  { room: 'Barn', level: 2, kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 12], facing: 'north', wood: 'spruce' },
  { room: 'Barn', level: 2, kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 14], facing: 'north', wood: 'spruce' },
  { room: 'Barn', level: 2, kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 18], facing: 'north', wood: 'spruce' },
  // Cellar: the three kept chests on the west wall, lamps hung from the hall's floor
  { room: 'Cellar', level: 3, kind: 'fixture', part: 'chest', pos: [2, -3, 4], facing: 'east', block: 'chest' },
  { room: 'Cellar', level: 3, kind: 'fixture', part: 'chest', pos: [2, -3, 5], facing: 'east', block: 'chest' },
  { room: 'Cellar', level: 3, kind: 'fixture', part: 'chest', pos: [2, -3, 6], facing: 'east', block: 'chest' },
  { room: 'Cellar', level: 3, kind: 'lamp', part: 'hangingLamp', pos: [4, -1, 3], facing: 'north', wood: 'spruce' },
  { room: 'Cellar', level: 3, kind: 'lamp', part: 'hangingLamp', pos: [4, -1, 6], facing: 'north', wood: 'spruce' },
]

// The walking lines. The first Farmer (spawned at (1, 0, -2)): along the front, up the gallery step, to his composter.
// The second (at (4, 0, -2), II): in at the front door, down the hall's middle aisle, out the back door, across the
// passage to the gallery and south to the second composter. III: from the front door to the hatch and down.
export const WALK = {
  first: [[1, 0, -1], [8, 0, -1], [8, 1, 0], [8, 1, 1], [8, 1, 2], [8, 1, 3]],
  second: [[4, 0, -1], [4, 0, 0], [4, 1, 1], [4, 1, 2], [4, 1, 7], [4, 1, 8], [4, 1, 9], [8, 1, 9], [8, 1, 12]],
  cellar: [[4, 1, 2], [3, 1, 2], [2, 0, 2], [2, -3, 2], [3, -3, 3], [3, -3, 6]],
}

// Outside (not furniture spots): crops on the fields (rows by x: wheat, wheat, -, carrots, potatoes in I), the
// scarecrow, flower beds under the front windows, a woodpile by the chimney, II's hay in the barn.
export const CROPS = {
  1: { 9: ['wheat', [2, 3, 4, 5]], 10: ['wheat', [5, 4, 3, 2]], 12: ['carrots', [3, 4, 5, 2]], 13: ['potatoes', [2, 5, 3, 4]] },
  2: { 9: ['potatoes', [4, 3, 2, 5]], 10: ['potatoes', [3, 5, 4, 2]], 11: ['carrots', [4, 3]], 12: ['wheat', [5, 3, 4, 2]], 13: ['wheat', [2, 4, 5, 3]] },
}
export const OUTSIDE = {
  flowerBeds: [{ x0: 2, x1: 2, z: 0, plants: ['poppy'] }, { x0: 6, x1: 6, z: 0, plants: ['oxeye_daisy'] }], // between the proud posts
  fieldLamps: { 1: [[9, 1], [14, 1], [14, 11]], 2: [[14, 21], [9, 21]] },
}
