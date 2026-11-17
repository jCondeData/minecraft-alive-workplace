// Builder's Workshop (ROADMAP 49, designer A), the plan: drawn before any wall. Rooms, every furniture footprint and
// the walking line come first; recipe.mjs assembles the building from the parts library (../parts) to fit this plan.
//
// Three rough concepts first:
//   1. The Hoist House. A tall street-gabled hall (stone workshop below, a brick-nogged timber loft jettied out over
//      the street above, a steep slate roof) with a hoist beam out of the gable's apex and a loading hatch under it,
//      a catslide lean-to yard shed down one side full of stock, a stone chimney up the other. Story: the builder
//      hauls stone and timber up into his loft store; the street sees the trade before it sees the door.
//   2. The Mason's Lodge. A long low single-storey stone lodge under a hipped roof with a deep open porch along the
//      street on which the table stands, a squat stair turret at one end for a bedroom over the porch. Story: the
//      lodge on site where the masons draw and eat. Calm and wide, but a hipped bar with a porch is any village house.
//   3. The Drafting Tower. A narrow three-storey tower (store, drawing room, bedroom) with a glazed top floor for the
//      light a draughtsman needs, a single-storey store at its foot. Story: the builder watches the village grow from
//      above. Striking, but the table must sit within 4 blocks of the chests vertically and a villager has to climb to
//      it: the job fights the tower, and a tower's floors are too small for a workshop.
// Strongest: 1. It is the only one whose silhouette says "builder" with no sign on it (the hoist and its hook, the
// loading hatch, stock under the shed roof), it gives the hero gable the street sees (STYLE.md: one mass 25 %
// taller), its three materials step up the way a builder shows his trades (rubble plinth, dressed stone, brick and
// timber, slate), and every piece the job needs (table, chests, bed) falls naturally into the plan. Built: 1.
//
// Coordinates: x across the front (west 0 -> east 13), y up (0 = the layer on the clicked block), z from the front
// (z = 0, the street, north) to the back. Front door on the north side with a stair step at y = 0 before it.
//
// Masses (named parts the owner's feedback can point at):
//   Workshop  the main mass: dressed-stone ground storey x 2..10, z 3..11 (walls y 1..3) on a rubble plinth; the
//             drafting room inside x 3..9, z 4..10 (7 x 7).
//   Loft      a timber-framed storey with brick nogging on a beam ring at y 4, jettied one block out over the street
//             (front wall at z 2) on brackets; knee walls y 5..7 (a full storey at the eaves), then a 45-degree slate roof (ridge along z, y 13).
//             Two beds (the builder at the back, his apprentice at the front) and the loft store.
//   Gable     the street gable at z 2: a frame grid with brick panels, a loading hatch (two leaves of trapdoor doors)
//             at loft level under a hoist beam out of the apex, its chain and hook lantern over the street.
//   Shed      the yard shed: a lean-to on posts down the east side (x 11..13, z 4..10), its slate slope running on
//             under the loft's east knee wall, open to the street end and the east; stone,
//             timber and plank stock and the yard chests under it; a side door from the drafting room.
//   Chimney   a rubble stack up the west wall (x 1, z 7) with its breast, the hearth set into the wall inside.
//
// Village variant ideas (for the ship step, not drawn here): the contract's 7 x 7 shell (x 1..7, z 2..8) is the
// Workshop at a smaller scale; keep the jettied brick gable, the hoist and hook over the door at x 4, and the shed as
// the "outside" part a look change restyles; the room box keeps the table, chest and bed at their fixed cells.

export const SIZE = [14, 14, 13] // w, h, d (limit 18 x 18 x 20)

// Palette (60/30/10): stone (rubble plinth, dressed-stone walls, chimney) / dark oak frame and slate roof / brick
// nogging and spruce joinery. Why: the builder's own house shows his trades course by course, darkest at the ground
// (cobble) to lighter stone, then warm brick between dark timbers, under a cool slate roof that sets the bricks off.
export const PALETTE = {
  // the ground storey keeps to three stones (critic, polish pass): cobble plinth, stone-brick walls with a few cracked
  rubble: [['cobblestone', 1]],
  ashlar: [['stone_bricks', 9], ['cracked_stone_bricks', 1]],
  chimney: [['cobblestone', 5], ['mossy_cobblestone', 1]], // the stack weathers; the walls stay dressed
  frame: 'dark_oak_log',
  infill: 'bricks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'spruce_planks',
  yard: [['cobblestone', 3], ['coarse_dirt', 2]],
  wood: 'spruce', // doors, trapdoors, stairs as chairs, fences, slabs
  trim: 'dark_oak', // brackets, barge boards
  glass: 'glass_pane',
  step: 'stone_brick_stairs',
  stock: { stone: 'stone_bricks', stoneCap: 'stone_brick_slab', log: 'spruce_log', planks: 'spruce_planks', plankCap: 'spruce_slab' },
}

export const MASSES = {
  workshop: { walls: { x0: 2, x1: 10, z0: 3, z1: 11 }, room: { x0: 3, x1: 9, z0: 4, z1: 10 }, wallTop: 3, beamY: 4 },
  loft: {
    front: 2, back: 11, kneeTop: 7,
    sidePosts: [2, 5, 8, 11], // along each knee wall (z)
    roof: { axis: 'z', across: [1, 11], along: [1, 12], eaveY: 8, steep: false },
    ceiling: { x0: 5, x1: 7, y: 10 }, // the collar boards over the loft's middle
  },
  shed: {
    floor: { x0: 11, x1: 13, z0: 4, z1: 10 },
    posts: [[13, 4], [13, 7], [13, 10]], postTop: 2,
    rack: { x: 11, x1: 11, y: 3, z0: 4, z1: 10 }, // timber racked two high under the lean-to's top, against the workshop wall
    roof: { axis: 'z', across: [11, 13], ridge: 11, along: [3, 11], ridgeY: 5 }, // its low edge sits on the posts
  },
  chimney: { x: 1, z: 7, top: 12, breastTop: 5, shoulder: { x: 0, top: 2 }, hearth: [2, 1, 7] }, // a 3-wide breast to y 5, a second step out to y 2
}

export const ROOMS = [
  { name: 'Drafting room', use: 'the Blueprint Table, the builder\'s desk and his stash; the hearth', box: [3, 1, 4, 9, 3, 10] },
  { name: 'Loft', use: 'the builder\'s and his apprentice\'s beds, the hoisted store', box: [3, 5, 3, 9, 8, 10] },
  { name: 'Shed', use: 'the yard: stock and the yard chests under the lean-to', box: [11, 1, 4, 12, 3, 10] },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 3], facing: 'south', step: [6, 0, 2] },
  sideDoor: { at: [10, 1, 5], facing: 'west' }, // from the drafting room into the shed
  hatch: { u: 6, y0: 5, y1: 6, z: 2 }, // the loading hatch in the street gable
  hoist: { at: [6, 11, 2], out: 'north', reach: 2, drop: 3 }, // the hook lantern hangs at (6, 7, 0)
  stairs: { flight: [[9, 1, 6], [9, 2, 7], [9, 3, 8], [9, 4, 9]], rises: 'south', well: [[9, 4, 6], [9, 4, 7], [9, 4, 8]] },
  windows: [
    { name: 'front west', cells: [[4, 2, 3], [4, 3, 3]], out: 'north', dress: 'sill' },
    { name: 'front east', cells: [[8, 2, 3], [8, 3, 3]], out: 'north', dress: 'sill' },
    { name: 'west front', cells: [[2, 2, 5], [2, 3, 5]], out: 'west', dress: 'shutters' },
    { name: 'west back', cells: [[2, 2, 9], [2, 3, 9]], out: 'west', dress: 'shutters' },
    { name: 'east', cells: [[10, 2, 8], [10, 3, 8]], out: 'east', dress: null }, // looks into the shed
    { name: 'back west', cells: [[4, 2, 11], [4, 3, 11]], out: 'south', dress: 'shutters' },
    { name: 'back east', cells: [[8, 2, 11], [8, 3, 11]], out: 'south', dress: 'shutters' },
    { name: 'loft west front', cells: [[2, 6, 4], [2, 7, 4]], out: 'west', dress: null },
    { name: 'loft west back', cells: [[2, 6, 10], [2, 7, 10]], out: 'west', dress: null },
    { name: 'loft east front', cells: [[10, 6, 6], [10, 7, 6]], out: 'east', dress: null },
    { name: 'loft east back', cells: [[10, 6, 9], [10, 7, 9]], out: 'east', dress: null },
    // gables: the loft's windows (glass where the gable's frame grid leaves a pane), drawn by the gable pick
  ],
}

// Every piece of furniture, footprint first (furniture.py kinds; 'fixture' = a vanilla block used as itself).
export const FURNITURE = [
  // Drafting room: the table in the middle facing the door, the builder's stash behind it, a clerk's desk under the
  // west front window, a lamp on a side table under the east one, blueprints pinned up, a rug before the table.
  { room: 'Drafting room', kind: 'fixture', part: 'blueprintTable', pos: [6, 1, 8], facing: 'north' },
  { room: 'Drafting room', kind: 'fixture', part: 'chest', pos: [3, 1, 10], facing: 'north', type: 'left' },
  { room: 'Drafting room', kind: 'fixture', part: 'chest', pos: [4, 1, 10], facing: 'north', type: 'right' },
  { room: 'Drafting room', kind: 'desk', part: 'desk', pos: [4, 1, 4], facing: 'north', wood: 'spruce' },
  { room: 'Drafting room', kind: 'chair', part: 'chair', pos: [4, 1, 5], facing: 'north', wood: 'spruce' },
  { room: 'Drafting room', kind: 'lamp', part: 'tableLamp', pos: [8, 1, 4], facing: 'south', wood: 'spruce' },
  { room: 'Drafting room', kind: 'picture', part: 'picture', pos: [3, 3, 7], facing: 'east', wood: 'spruce', colour: 'blue' },
  { room: 'Drafting room', kind: 'picture', part: 'picture', pos: [6, 3, 10], facing: 'north', wood: 'spruce', colour: 'light_blue' },
  { room: 'Drafting room', kind: 'cupboard', part: 'cupboard', pos: [7, 1, 10], facing: 'north', wood: 'spruce' },
  { room: 'Drafting room', kind: 'rug', part: 'rug', cellsAbs: [[5, 1, 6], [6, 1, 6], [7, 1, 6], [5, 1, 7], [6, 1, 7], [7, 1, 7]], colour: 'brown' },
  { room: 'Drafting room', kind: 'lamp', part: 'hangingLamp', pos: [5, 3, 5], facing: 'north', wood: 'spruce' },
  { room: 'Drafting room', kind: 'lamp', part: 'hangingLamp', pos: [7, 3, 9], facing: 'north', wood: 'spruce' },
  { room: 'Drafting room', kind: 'fixture', part: 'hearth', pos: [2, 1, 7], facing: 'east' },
  // Loft: the builder's bed at the back, the apprentice's at the front by the gable, the hoisted store by the hatch,
  // a writing desk, nightstands, a rug, lamps on chains from the collar boards.
  { room: 'Loft', kind: 'bed', part: 'bed', pos: [4, 5, 9], facing: 'south', colour: 'red' },
  { room: 'Loft', kind: 'nightstand', part: 'nightstand', pos: [3, 5, 10], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'nightstand', part: 'nightstand', pos: [5, 5, 10], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'bed', part: 'bed', pos: [4, 5, 4], facing: 'north', colour: 'red' },
  { room: 'Loft', kind: 'nightstand', part: 'nightstand', pos: [3, 5, 3], facing: 'south', wood: 'spruce' },
  { room: 'Loft', kind: 'fixture', part: 'chest', pos: [7, 5, 3], facing: 'south', type: 'right' },
  { room: 'Loft', kind: 'fixture', part: 'chest', pos: [8, 5, 3], facing: 'south', type: 'left' },
  { room: 'Loft', kind: 'desk', part: 'desk', pos: [3, 5, 7], facing: 'west', wood: 'spruce' },
  { room: 'Loft', kind: 'chair', part: 'chair', pos: [4, 5, 7], facing: 'west', wood: 'spruce' },
  { room: 'Loft', kind: 'rug', part: 'rug', cellsAbs: [[5, 5, 6], [6, 5, 6], [5, 5, 7], [6, 5, 7], [5, 5, 8], [6, 5, 8]], colour: 'gray' },
  { room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [6, 8, 4], facing: 'north', chain: 1, wood: 'spruce' },
  { room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [6, 8, 9], facing: 'north', chain: 1, wood: 'spruce' },
  // Shed: the yard chests against the workshop wall, stock in piles (materialStack, not furniture). Three wide and open
  // along its east side, so the yard reads as a yard (holes.py's open check: no roofed cell four in from open air).
  { room: 'Shed', kind: 'fixture', part: 'chest', pos: [11, 1, 8], facing: 'east', type: 'left' },
  { room: 'Shed', kind: 'fixture', part: 'chest', pos: [11, 1, 9], facing: 'east', type: 'right' },
  { room: 'Shed', kind: 'lamp', part: 'hangingLamp', pos: [12, 3, 7], facing: 'north', wood: 'spruce' },
]

// The walking line: street -> step -> door -> the table's front (6, 1, 7); the stash (3..4, 1, 9); the side door
// (9, 1, 5) -> (10, 1, 5) -> the yard chests' front (12, 1, 8..9); up the flight (9, 1, 5) -> (9, 4, 9) -> the loft
// (9, 5, 10) -> (6, 5, 9) the beds, (6, 5, 4) the hatch and the store.
export const WALK = [
  [6, 0, 0], [6, 0, 1], [6, 0, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [6, 1, 7],
  [5, 1, 8], [4, 1, 9], [3, 1, 9],
  [7, 1, 5], [8, 1, 5], [9, 1, 5], [10, 1, 5], [11, 1, 5], [11, 1, 6], [12, 1, 7], [12, 1, 8], [12, 1, 9],
  [9, 1, 6], [9, 2, 7], [9, 3, 8], [9, 4, 9], [9, 5, 10], [8, 5, 10], [7, 5, 9], [6, 5, 9], [5, 5, 8], [6, 5, 5], [6, 5, 4],
]

// Outside (not furniture spots): the forecourt, and the yard's stock where the street sees it: timber stacked end-on
// in the shed's street end, a delivery of cut stone under its eave.
export const OUTSIDE = {
  // the forecourt: flags (half a step, then the stair) from the street to the door
  path: [[5, 0, 0], [6, 0, 0], [7, 0, 0], [5, 0, 1], [6, 0, 1], [7, 0, 1]],
  timber: [[11, 4, 2], [12, 4, 2]], // lengths stacked end-on to the street in the shed's open end (axis z)
  stone: [[11, 3, 2], [12, 3, 1]], // a delivery of cut stone under the shed's front eave, stepping down to the street
  logs: [[11, 10, 2]], // timber lengths at the shed's back
  planks: [[12, 10, 1]],
}
