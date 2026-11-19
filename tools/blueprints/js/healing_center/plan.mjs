// The Healing Center (all four levels), the plan: drawn before any wall (ROADMAP 49, tools/blueprints/briefs/healing_center.md).
// Rooms, every furniture footprint (kind, cells, facing) and the walking line come first; recipe.mjs then assembles
// each level from the parts library (../parts, with ../parts/healing_spring.mjs grown for it) to fit this plan.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Spring Infirmary: a front-gabled dispensary with a little gabled wall dormer over each long side, so the
//      roof is a cross from above (I); a taller cross range behind it, a T, over a warm spring pool, two gables on its
//      long back wall (II); a lower consulting wing beside the front and a walled herb garth behind, entered by a back
//      door under one of those gables (III); the spring's cistern and still room dug under the cross range (IV). One
//      45-degree red roof that grows into valleys. Story: the clinic was raised over a spring the village has always
//      gone to for cures, and each upgrade gives the water more room.
//   B. The Lantern Tower: a square stone tower of three floors with a glass lantern lit at the top as the village's
//      sign of help, lean-to wards round its foot that grow level by level. Pyramid roof. Story: a beacon you walk to
//      when you're sick. (Tall and narrow: a stair in every level, cramped rooms, slow to build; the brief's Level I
//      must be built in 9,000 ticks.)
//   C. The Cloister Infirmary: one low hipped range per level closing round a square herb garth (an L, then a U),
//      an arcade walk inside. Story: a monastery's sick-house. (Calm but low and long: little outline from the
//      street, and the arcade doubles the blocks for little room.)
//   Built: A. It is the strongest because every level has a reason the player can see (the dispensary, the spring
//   hall, the garden of herbs, the source under it), its outline is recognisable from far off (a red cross-gabled
//   house with a gable on every side and a framed window lit in each, a vent on the ridge), and the T of
//   ridges and the dormer gables give every side a gable instead of a bare roof plane.
//   Round 2 (the critic's notes, 2026-11-18): the glazed copper lanterns are gone (one plain vent on the Dispensary's
//   ridge, capped in the roof's own slab); every gable gets one framed window (sill, lintel, king post) instead of a
//   glass cross; pots only on the street front and the garden side; the long sides get dormer gables; the Cistern is
//   furnished as a still room; Level I trimmed (no skirt where no ledge needs one).
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front
// (z = 0, the street) back. Front door on the ground floor at (6, 1, 2), a stair step before it.
//
// Named parts (the owner's feedback can point at them):
//   Dispensary   (I)   the stem: walls x 1..11, z 2..11, plaster between dark posts, ground storey y 1..4, beam y 5;
//                      a 45-degree red roof, ridge along z at x 6 (y 11); the front gable jettied out to z 1 on brackets,
//                      a framed window in it.
//   Dormers      (I)   a gabled wall dormer over the middle bay of each long side (ridge along x at z 6), a framed
//                      window in each; the east one is taken into the Consulting Room's roof in III.
//   Vent         (I)   one fence-and-slab vent on the ridge at (6, 6); the hall's lamp hangs from the ridge under it.
//   Spring Hall  (II)  the cross range: walls x 1..19, z 11..19, a storey taller (beam y 6), ridge along x at z 15 (y 11);
//                      a rimmed pool of warm water with benches round it; framed windows in its west and east gables
//                      and in two gables on its long back wall (x 6 and x 14). The stem's roof runs back into it.
//   Consulting   (III) the east wing: walls x 11..19, z 2..11 (sharing the stem's east wall and the hall's front wall),
//   Room               lower (beam y 4, ridge y 9 at x 15), its front gable flush beside the stem's, a framed window in it.
//   Herb Garth   (III) a walled garden behind the Spring Hall (z 20..25), raised herb beds, a flowering tree, a bench,
//                      a gate at the back; the Spring Hall's back door (6, 1, 19) opens onto it.
//   Cistern      (IV)  a vaulted room 4 deep under the Spring Hall: the spring's source pool, the still room (chests,
//                      herb shelves, lamps on side tables and a post), treatment benches on both end walls, a table,
//                      a bench by the water; down a stone flight from the hall's west end.
// The walking line is one axis: front step (6, 0, 1) -> front door -> waiting room -> between the counters ->
// doorway (6, 1, 11) -> Spring Hall -> back door (6, 1, 19) -> garden path -> garden gate (6, 1, 25).

export const SIZES = { 1: [13, 16, 13], 2: [21, 16, 21], 3: [21, 16, 26], 4: [21, 16, 26] }
export const BASEMENT = { 1: 0, 2: 0, 3: 0, 4: 4 }

// Palette (60/30/10): white plaster walls (60) / a red mangrove-tile roof and a dark oak frame (30) / a stone-brick
// plinth, skirt and window ledges, pots of flowers on the street and garden sides (10). Why: a clinic should read
// clean and bright from across the village, the red roof is its sign, the dark frame draws the bays, and the
// one cool note that marks the light-wells over the rooms.
export const PALETTE = {
  stone: [['stone_bricks', 7], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 2]],
  skirt: 'stone_brick_stairs',
  plaster: 'white_concrete',
  frame: 'dark_oak_log',
  roof: { full: 'mangrove_planks', stairs: 'mangrove_stairs', slab: 'mangrove_slab' },
  floor: 'smooth_stone',
  hallFloor: 'polished_andesite', // the Spring Hall's wet floor
  rim: 'mossy_stone_brick_slab',
  trim: 'dark_oak', // sills, barge boards, railings, tie beams' brackets
  sill: 'stone_brick_slab', // window ledges
  pots: ['potted_lily_of_the_valley', 'potted_fern', 'potted_allium', 'potted_cornflower'], // herbs on the ledges
  door: 'dark_oak',
  wood: 'spruce', // furniture
  glass: 'glass_pane',
  cellarWall: [['stone_bricks', 5], ['mossy_stone_bricks', 2], ['cobblestone', 1]],
  cellarFloor: 'stone_bricks',
  lawn: 'grass_block',
  path: 'packed_mud',
}

export const MASSES = {
  stem: { walls: { x0: 1, x1: 11, z0: 2, z1: 11 }, top: 4, beamY: 5,
    roof: { axis: 'z', across: [0, 12], eaveY: 5 }, alongI: [0, 12], alongII: [0, 15],
    frontPosts: [1, 4, 8, 11], sidePosts: [2, 5, 8, 11], backPosts: [1, 4, 8, 11], vent: [6, 6],
    dormerW: { axis: 'x', across: [2, 10], along: [0, 6], eaveY: 5 }, dormerE: { axis: 'x', across: [2, 10], along: [6, 12], eaveY: 5 } },
  hall: { walls: { x0: 1, x1: 19, z0: 11, z1: 19 }, top: 5, beamY: 6,
    roof: { axis: 'x', across: [10, 20], along: [0, 20], eaveY: 6 },
    frontPosts: [11, 15, 19], backPosts: [1, 4, 8, 12, 16, 19], sidePosts: [11, 15, 19],
    backGables: [{ axis: 'z', across: [2, 10], along: [16, 20], eaveY: 6 }, { axis: 'z', across: [10, 18], along: [16, 20], eaveY: 6 }] },
  wing: { walls: { x0: 11, x1: 19, z0: 2, z1: 11 }, top: 3, beamY: 4,
    roof: { axis: 'z', across: [10, 20], along: [1, 15], eaveY: 4 },
    frontPosts: [11, 15, 19], sidePosts: [2, 5, 8, 11] },
  garth: { x0: 1, x1: 19, z0: 20, z1: 25, gate: [6, 25] },
  cistern: { x0: 1, x1: 19, z0: 11, z1: 19, depth: 4 },
}

export const ROOMS = [
  { name: 'Waiting room', level: 1, use: 'benches either side under the windows, a clock and a bookcase by the door', box: [2, 1, 3, 10, 4, 6] },
  { name: 'Dispensary', level: 1, use: 'the reception desk and counters across the room, the brewing stand and its chest behind them', box: [2, 1, 7, 10, 4, 10] },
  { name: 'Spring Hall', level: 2, use: 'the warm pool with benches round it, a tea table, towel cupboards', box: [2, 1, 12, 18, 5, 18] },
  { name: 'Consulting Room', level: 3, use: "the doctor's desk, a couch for patients, a table, book cupboards", box: [12, 1, 3, 18, 3, 10] },
  { name: 'Herb Garth', level: 3, use: 'raised beds of healing herbs, a flowering tree, a bench, the back door and the gate', box: [2, 1, 20, 18, 1, 24] },
  { name: 'Cistern', level: 4, use: "the spring's source pool, the still room's chests, a bench by the water", box: [2, -3, 12, 18, -1, 18] },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 2], facing: 'south', out: 'north', step: [6, 0, 1] },
  hallDoorway: { at: [6, 1, 11], from: 2 }, // the Dispensary's back window becomes a doorway in II
  backDoor: { at: [6, 1, 19], facing: 'north', out: 'south', step: [6, 0, 20], from: 3 },
  wingDoorways: [[11, 1, 3], [18, 1, 11]], // III: from the waiting room, and from the Spring Hall
  cellarFlight: { steps: [[2, 0, 13], [2, -1, 14], [2, -2, 15], [2, -3, 16]], rises: 'north', well: [[2, 0, 14], [2, 0, 15], [2, 0, 16]] },
  // windows: cells of glass, the side they look out of; every one gets a stone ledge with pots of herbs (one window function)
  windows: [
    { level: 1, name: 'dispensary front west', cells: [[2, 2, 2], [3, 2, 2], [2, 3, 2], [3, 3, 2]], out: 'north' },
    { level: 1, name: 'dispensary front east', cells: [[9, 2, 2], [10, 2, 2], [9, 3, 2], [10, 3, 2]], out: 'north' },
    { level: 1, name: 'dispensary west front', cells: [[1, 2, 3], [1, 2, 4], [1, 3, 3], [1, 3, 4]], out: 'west' },
    { level: 1, name: 'dispensary west back', cells: [[1, 2, 9], [1, 2, 10], [1, 3, 9], [1, 3, 10]], out: 'west' },
    { level: 1, until: 2, name: 'dispensary east front', cells: [[11, 2, 3], [11, 2, 4], [11, 3, 3], [11, 3, 4]], out: 'east' },
    { level: 1, until: 2, name: 'dispensary east back', cells: [[11, 2, 9], [11, 2, 10], [11, 3, 9], [11, 3, 10]], out: 'east' },
    { level: 1, until: 1, name: 'dispensary back west', cells: [[2, 2, 11], [3, 2, 11], [2, 3, 11], [3, 3, 11]], out: 'south' },
    { level: 1, until: 1, name: 'dispensary back middle', cells: [[6, 2, 11], [6, 3, 11]], out: 'south' },
    { level: 1, until: 1, name: 'dispensary back east', cells: [[9, 2, 11], [10, 2, 11], [9, 3, 11], [10, 3, 11]], out: 'south' },
    { level: 2, until: 2, name: 'hall front west', cells: [[12, 2, 11], [13, 2, 11], [14, 2, 11], [12, 3, 11], [13, 3, 11], [14, 3, 11]], out: 'north' },
    { level: 2, until: 2, name: 'hall front east', cells: [[16, 2, 11], [17, 2, 11], [18, 2, 11], [16, 3, 11], [17, 3, 11], [18, 3, 11]], out: 'north' },
    { level: 2, name: 'hall back 1', cells: [[2, 2, 19], [3, 2, 19], [2, 3, 19], [3, 3, 19]], out: 'south' },
    { level: 2, until: 2, name: 'hall back door bay', cells: [[6, 2, 19], [6, 3, 19]], out: 'south' },
    { level: 2, name: 'hall back 3', cells: [[9, 2, 19], [10, 2, 19], [11, 2, 19], [9, 3, 19], [10, 3, 19], [11, 3, 19]], out: 'south' },
    { level: 2, name: 'hall back 4', cells: [[13, 2, 19], [14, 2, 19], [15, 2, 19], [13, 3, 19], [14, 3, 19], [15, 3, 19]], out: 'south' },
    { level: 2, name: 'hall back 5', cells: [[17, 2, 19], [18, 2, 19], [17, 3, 19], [18, 3, 19]], out: 'south' },
    { level: 2, name: 'hall west 1', cells: [[1, 2, 12], [1, 2, 13], [1, 2, 14], [1, 3, 12], [1, 3, 13], [1, 3, 14]], out: 'west' },
    { level: 2, name: 'hall west 2', cells: [[1, 2, 16], [1, 2, 17], [1, 2, 18], [1, 3, 16], [1, 3, 17], [1, 3, 18]], out: 'west' },
    { level: 2, name: 'hall east 1', cells: [[19, 2, 12], [19, 2, 13], [19, 2, 14], [19, 3, 12], [19, 3, 13], [19, 3, 14]], out: 'east' },
    { level: 2, name: 'hall east 2', cells: [[19, 2, 16], [19, 2, 17], [19, 2, 18], [19, 3, 16], [19, 3, 17], [19, 3, 18]], out: 'east' },
    { level: 3, name: 'wing front west', cells: [[12, 2, 2], [13, 2, 2], [14, 2, 2], [12, 3, 2], [13, 3, 2], [14, 3, 2]], out: 'north' },
    { level: 3, name: 'wing front east', cells: [[16, 2, 2], [17, 2, 2], [18, 2, 2], [16, 3, 2], [17, 3, 2], [18, 3, 2]], out: 'north' },
    { level: 3, name: 'wing east front', cells: [[19, 2, 3], [19, 2, 4], [19, 3, 3], [19, 3, 4]], out: 'east' },
    { level: 3, name: 'wing east back', cells: [[19, 2, 9], [19, 2, 10], [19, 3, 9], [19, 3, 10]], out: 'east' },
  ],
  // the gable windows: three panes in a row under a frame lintel, a king post over it, a stone ledge under it:
  // [axis of the wall plane, its line, first u, last u, pane row y, out, first level, last level]
  gableWindows: [['z', 1, 5, 7, 8, 'north', 1], ['x', 1, 5, 7, 6, 'west', 1], ['x', 11, 5, 7, 6, 'east', 1, 2],
    ['x', 1, 14, 16, 8, 'west', 2], ['x', 19, 14, 16, 8, 'east', 2], ['z', 19, 5, 7, 7, 'south', 2], ['z', 19, 13, 15, 7, 'south', 2],
    ['z', 2, 14, 16, 6, 'north', 3]],
}

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a furniture spot) or a fixture
// (a vanilla block the game uses as itself). part: the furnitureParts part that draws it. facing: the way a person
// using it faces. level: the first level it stands in (it stays in every later one unless `until`).
export const FURNITURE = [
  // Waiting room (I): benches along both walls facing each other, lamps on side tables by the door, a cloth over each bench
  { level: 1, room: 'Waiting room', kind: 'bench', part: 'wallBench', cellsXZ: [[2, 4], [2, 5], [2, 6]], y: 1, facing: 'east', wood: 'spruce' },
  { level: 1, room: 'Waiting room', kind: 'bench', part: 'wallBench', cellsXZ: [[10, 4], [10, 5], [10, 6]], y: 1, facing: 'west', wood: 'spruce' },
  { level: 1, room: 'Waiting room', kind: 'clock', part: 'clock', pos: [2, 1, 3], facing: 'east', wood: 'spruce' },
  { level: 1, room: 'Waiting room', kind: 'fixture', part: 'bookcase', pos: [10, 1, 3], facing: 'west', plant: 'potted_azalea_bush' },
  { level: 1, room: 'Waiting room', kind: 'picture', part: 'picture', pos: [2, 3, 5], facing: 'east', wood: 'spruce', colour: 'light_blue' },
  { level: 1, room: 'Waiting room', kind: 'picture', part: 'picture', pos: [10, 3, 5], facing: 'west', wood: 'spruce', colour: 'red' },
  { level: 1, room: 'Waiting room', kind: 'rug', part: 'rug', cellsAbs: [[6, 1, 4], [6, 1, 5], [6, 1, 6]], colour: 'cyan' },
  // Dispensary (I): the reception desk and counters across the room with a gap in the middle; the Nurse's chair
  // behind the desk; the brewing stand and its chest in the east corner, herb shelves over them; book cupboards west
  { level: 1, room: 'Dispensary', kind: 'counter', part: 'counter', pos: [2, 1, 7], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Dispensary', kind: 'desk', part: 'desk', pos: [3, 1, 7], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Dispensary', kind: 'counter', part: 'counter', pos: [4, 1, 7], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Dispensary', kind: 'chair', part: 'chair', pos: [3, 1, 8], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Dispensary', kind: 'counter', part: 'counter', pos: [8, 1, 7], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Dispensary', kind: 'counter', part: 'counter', pos: [9, 1, 7], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Dispensary', kind: 'counter', part: 'counter', pos: [10, 1, 7], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Dispensary', kind: 'fixture', part: 'brewingStand', pos: [10, 1, 9], facing: 'west' },
  { level: 1, room: 'Dispensary', kind: 'fixture', part: 'chest', pos: [10, 1, 10], facing: 'west', block: 'chest' },
  { level: 1, room: 'Dispensary', kind: 'shelf', part: 'herbShelf', pos: [10, 3, 9], facing: 'west', wood: 'spruce', plant: 'potted_fern' },
  { level: 1, room: 'Dispensary', kind: 'shelf', part: 'herbShelf', pos: [10, 3, 10], facing: 'west', wood: 'spruce', plant: 'potted_lily_of_the_valley' },
  { level: 1, room: 'Dispensary', kind: 'cupboard', part: 'cupboard', pos: [2, 1, 10], facing: 'east', wood: 'spruce' },
  { level: 1, room: 'Dispensary', kind: 'cupboard', part: 'cupboard', pos: [3, 1, 10], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Dispensary', kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 7], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Dispensary', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 7], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Waiting room', kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 6], facing: 'north', chain: 6, wood: 'spruce' }, // from the ridge under the vent
  // Spring Hall (II): benches either side of the pool, a tea table with two chairs west, towel cupboards east, a couch
  { level: 2, room: 'Spring Hall', kind: 'bench', part: 'wallBench', cellsXZ: [[12, 12], [13, 12], [14, 12], [15, 12], [16, 12]], y: 1, facing: 'south', wood: 'spruce' },
  { level: 2, room: 'Spring Hall', kind: 'bench', part: 'wallBench', cellsXZ: [[10, 18], [11, 18], [12, 18], [13, 18], [14, 18], [15, 18], [16, 18]], y: 1, facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Spring Hall', kind: 'table', part: 'table', pos: [4, 1, 15], facing: 'north', wood: 'spruce', colour: 'white' },
  { level: 2, room: 'Spring Hall', kind: 'chair', part: 'chair', pos: [4, 1, 14], facing: 'south', wood: 'spruce' },
  { level: 2, room: 'Spring Hall', kind: 'chair', part: 'chair', pos: [4, 1, 16], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Spring Hall', kind: 'couch', part: 'couch', pos: [18, 1, 16], facing: 'west', length: 3, wood: 'spruce', colour: 'white' },
  { level: 2, room: 'Spring Hall', kind: 'cupboard', part: 'cupboard', pos: [18, 1, 18], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Spring Hall', kind: 'cupboard', part: 'cupboard', pos: [2, 1, 18], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Spring Hall', kind: 'picture', part: 'picture', pos: [2, 4, 15], facing: 'east', wood: 'spruce', colour: 'cyan' },
  { level: 2, room: 'Spring Hall', kind: 'picture', part: 'picture', pos: [18, 4, 15], facing: 'west', wood: 'spruce', colour: 'cyan' },
  { level: 2, room: 'Spring Hall', kind: 'lamp', part: 'tableLamp', pos: [5, 1, 18], facing: 'north', wood: 'spruce' }, // lights the tea table and the west cupboard
  { level: 2, room: 'Spring Hall', kind: 'lamp', part: 'hangingLamp', pos: [13, 5, 15], facing: 'north', chain: 5, wood: 'spruce' }, // from the ridge over the Spring
  { level: 2, room: 'Spring Hall', kind: 'lamp', part: 'hangingLamp', pos: [8, 5, 13], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Spring Hall', kind: 'lamp', part: 'hangingLamp', pos: [8, 5, 17], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Spring Hall', kind: 'lamp', part: 'hangingLamp', pos: [17, 5, 13], facing: 'north', wood: 'spruce' },
  { level: 2, room: 'Spring Hall', kind: 'lamp', part: 'hangingLamp', pos: [17, 5, 17], facing: 'north', wood: 'spruce' },
  // Consulting Room (III): the doctor's desk under the front window, a couch for patients on the east wall, a table
  // for two on a rug, book cupboards on the back wall, a cloth over the couch
  { level: 3, room: 'Consulting Room', kind: 'desk', part: 'desk', pos: [14, 1, 3], facing: 'north', wood: 'spruce' },
  { level: 3, room: 'Consulting Room', kind: 'chair', part: 'chair', pos: [14, 1, 4], facing: 'north', wood: 'spruce' },
  { level: 3, room: 'Consulting Room', kind: 'couch', part: 'couch', pos: [18, 1, 7], facing: 'west', length: 3, arms: true, wood: 'spruce', colour: 'light_blue' },
  { level: 3, room: 'Consulting Room', kind: 'table', part: 'table', pos: [15, 1, 7], facing: 'north', wood: 'spruce', colour: 'white' },
  { level: 3, room: 'Consulting Room', kind: 'chair', part: 'chair', pos: [14, 1, 7], facing: 'east', wood: 'spruce' },
  { level: 3, room: 'Consulting Room', kind: 'chair', part: 'chair', pos: [16, 1, 7], facing: 'west', wood: 'spruce' },
  { level: 3, room: 'Consulting Room', kind: 'cupboard', part: 'cupboard', pos: [12, 1, 10], facing: 'north', wood: 'spruce' },
  { level: 3, room: 'Consulting Room', kind: 'cupboard', part: 'cupboard', pos: [13, 1, 10], facing: 'north', wood: 'spruce' },
  { level: 3, room: 'Consulting Room', kind: 'picture', part: 'picture', pos: [18, 3, 6], facing: 'west', wood: 'spruce', colour: 'green' },
  { level: 3, room: 'Consulting Room', kind: 'lamp', part: 'tableLamp', pos: [12, 1, 9], facing: 'east', wood: 'spruce' },
  { level: 3, room: 'Consulting Room', kind: 'lamp', part: 'hangingLamp', pos: [15, 4, 7], facing: 'north', chain: 4, wood: 'spruce' },
  { level: 3, room: 'Consulting Room', kind: 'rug', part: 'rug', cellsAbs: [[14, 1, 6], [15, 1, 6], [16, 1, 6], [14, 1, 8], [15, 1, 8], [16, 1, 8]], colour: 'light_blue' },
  // Herb Garth (III): a bench against the garden wall facing the beds and the tree (a block clear of the pot ledge at
  // (18, 1, 20), which would read as a table at the bench's end)
  { level: 3, room: 'Herb Garth', kind: 'bench', part: 'wallBench', cellsXZ: [[18, 22], [18, 23], [18, 24]], y: 1, facing: 'west', wood: 'spruce' },
  // Cistern (IV): the still room on the north wall (chests, herb shelves, lamps on side tables), treatment benches on
  // the west part's south wall and on the east wall, a table for two, a bench by the source pool, a lamp on a post
  { level: 4, room: 'Cistern', kind: 'fixture', part: 'chest', pos: [6, -3, 12], facing: 'south', block: 'chest' },
  { level: 4, room: 'Cistern', kind: 'fixture', part: 'chest', pos: [8, -3, 12], facing: 'south', block: 'chest' },
  { level: 4, room: 'Cistern', kind: 'shelf', part: 'herbShelf', pos: [5, -2, 12], facing: 'south', wood: 'spruce', plant: 'potted_fern' },
  { level: 4, room: 'Cistern', kind: 'shelf', part: 'herbShelf', pos: [7, -2, 12], facing: 'south', wood: 'spruce', plant: 'potted_red_mushroom' },
  { level: 4, room: 'Cistern', kind: 'shelf', part: 'herbShelf', pos: [12, -2, 12], facing: 'south', wood: 'spruce', plant: 'potted_brown_mushroom' },
  { level: 4, room: 'Cistern', kind: 'shelf', part: 'herbShelf', pos: [14, -2, 12], facing: 'south', wood: 'spruce', plant: 'potted_lily_of_the_valley' },
  { level: 4, room: 'Cistern', kind: 'lamp', part: 'tableLamp', pos: [4, -3, 12], facing: 'south', wood: 'spruce' },
  { level: 4, room: 'Cistern', kind: 'lamp', part: 'tableLamp', pos: [9, -3, 12], facing: 'south', wood: 'spruce' },
  { level: 4, room: 'Cistern', kind: 'lamp', part: 'tableLamp', pos: [18, -3, 12], facing: 'south', wood: 'spruce' },
  { level: 4, room: 'Cistern', kind: 'bench', part: 'wallBench', cellsXZ: [[4, 18], [5, 18], [6, 18], [7, 18], [8, 18]], y: -3, facing: 'north', wood: 'spruce' },
  { level: 4, room: 'Cistern', kind: 'bench', part: 'wallBench', cellsXZ: [[18, 14], [18, 15], [18, 16]], y: -3, facing: 'west', wood: 'spruce' },
  { level: 4, room: 'Cistern', kind: 'bench', part: 'wallBench', cellsXZ: [[11, 18], [12, 18], [13, 18], [14, 18], [15, 18]], y: -3, facing: 'north', wood: 'spruce' },
  { level: 4, room: 'Cistern', kind: 'table', part: 'table', pos: [6, -3, 15], facing: 'north', wood: 'spruce', colour: 'white' },
  { level: 4, room: 'Cistern', kind: 'chair', part: 'chair', pos: [5, -3, 15], facing: 'east', wood: 'spruce' },
  { level: 4, room: 'Cistern', kind: 'chair', part: 'chair', pos: [7, -3, 15], facing: 'west', wood: 'spruce' },
  { level: 4, room: 'Cistern', kind: 'cupboard', part: 'cupboard', pos: [18, -3, 18], facing: 'north', wood: 'spruce' },
  { level: 4, room: 'Cistern', kind: 'picture', part: 'picture', pos: [18, -2, 15], facing: 'west', wood: 'spruce', colour: 'blue' },
  { level: 4, room: 'Cistern', kind: 'picture', part: 'picture', pos: [6, -2, 18], facing: 'north', wood: 'spruce', colour: 'green' },
  { level: 4, room: 'Cistern', kind: 'lamp', part: 'hangingLamp', pos: [13, -1, 15], facing: 'north', wood: 'spruce' },
  { level: 4, room: 'Cistern', kind: 'lamp', part: 'hangingLamp', pos: [5, -1, 16], facing: 'north', wood: 'spruce' },
]

// The walking line (see above), and the cellar's way down from the Spring Hall's west end.
export const WALK = [
  [6, 0, 1], [6, 1, 2], [6, 1, 3], [6, 1, 6], [6, 1, 8], [8, 1, 8], [9, 1, 9], // to the brewing stand (10, 1, 9)
  [6, 1, 10], [6, 1, 11], [6, 1, 12], [6, 1, 18], [6, 1, 19], [6, 0, 20], [6, 0, 24], [6, 1, 25], // through the hall and garden
  [5, 1, 12], [2, 1, 12], [2, 0, 13], [2, -3, 16], [2, -3, 17], [9, -3, 15], // down to the Cistern
]

// Outside (not furniture spots): flower beds in their own soil before the front, lamp posts either side of the step,
// the garden's beds and tree.
export const OUTSIDE = {
  flowerBeds: [{ level: 1, x0: 2, x1: 3, z: 0, plants: ['allium', 'oxeye_daisy'] }, { level: 1, x0: 9, x1: 10, z: 0, plants: ['oxeye_daisy', 'allium'] },
    { level: 3, x0: 12, x1: 14, z: 0, plants: ['cornflower', 'lily_of_the_valley'] }, { level: 3, x0: 16, x1: 18, z: 0, plants: ['lily_of_the_valley', 'cornflower'] }],
  lampPosts: [[4, 0, 0], [8, 0, 0]],
  cellarLampPost: [9, -3, 17],
  herbBeds: [ // raised beds in the garth: rim box and what grows in them
    { x0: 2, x1: 5, z0: 21, z1: 24, plants: ['fern', 'lily_of_the_valley', 'allium', 'fern'] },
    { x0: 7, x1: 11, z0: 21, z1: 24, plants: ['cornflower', 'oxeye_daisy', 'azure_bluet', 'cornflower'] },
    { x0: 13, x1: 17, z0: 21, z1: 24, tree: [15, 22], plants: ['fern', 'lily_of_the_valley', 'fern', 'allium'] },
  ],
}
