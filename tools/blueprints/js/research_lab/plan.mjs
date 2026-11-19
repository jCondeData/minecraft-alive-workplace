// Research Lab (research_lab, _2, _3), the plan: drawn before any wall. Story, palette, masses, rooms, openings, every
// furniture footprint and the walking line come first; recipe.mjs then assembles the three levels from the parts
// library (../parts) in passes. The brief: tools/blueprints/briefs/research_lab.md.
//
// Three rough concepts (massing, roof, plan, story):
//   A. The Rotunda: a round stone drum under a stepped dome with a glazed oculus over the analyzer; II adds two short
//      wings either side of it, III a ring cellar. Story: a learned society's lecture theatre. Weak: a dome 11 wide is
//      lumpy in blocks, the wings make it a symmetrical town-hall shape anyone would draw for "museum".
//   B. The Field Station: a timber dig shed on stilts beside a sieve yard and a crane; II a long canvas-and-plank
//      finds shed, III the trench under it. Story: the scientist lives at the dig. Weak: it reads as a workshop or
//      a quarry shed (the village already has both), and nothing says "science" from the street.
//   C. The Strata Lab: a tall, narrow, steep-gabled stone lab whose walls are laid in bands like a cliff's section
//      (dark deepslate at the foot, grey-green tuff, a rust band of terracotta, pale sandstone at the top), a great
//      pointed window in its front gable over the analyzer and lightning rods on its ridge; II grows a glass-vaulted
//      museum hall on copper ribs along the street with a fossil skeleton standing under the glass; III digs the real
//      ground under the hall: a sunken dig with a skeleton still lying in it, the strata in the cellar walls.
//      Story: everything the scientist knows is in the layers of the ground, so the building is built in them.
// Strongest: C. It is the only one whose look says what the job is (the rock it digs, the bones it finds, the
// lightning that wakes them) from twenty blocks away, and each level adds a new chapter (the lab, the museum, the dig)
// with a different silhouette: a hero gable, then a long glass vault beside it, then a pit under the floor. The Rotunda
// is safer, the Field Station cheaper; neither tells the story.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front (z = 0,
// north) to the back. The front doors open north.
//
// Masses (named parts the owner's feedback can point at):
//   Lab       (I) the hero: stone walls x 1..7, z 2..10, four courses high (y 1..4), a steep deepslate-tile roof (ridge
//             along z, y 11) between two parapet gables coped in tuff with kneelers at their feet; the Great Window (a
//             three-light pointed window) in the front gable over the door's hood; buttresses at the corners and
//             mid-side; lightning rods on the two gables' apexes; a stepped skylight in the long west slope. Inside: one tall room open to the roof, tie
//             beams at y 4, the analyzer on the back wall's axis.
//   Prep Room (I) the low wing east of the Lab: walls x 7..11, z 4..10, three courses, a 45-degree tile roof (ridge
//             along x, y 8) set back two from the Lab's front, its east gable coped like the Lab's; the sorting
//             bench, the finds chest.
//   Gallery   (II) the museum hall east of the Prep Room: walls x 11..21, z 3..13, five courses, a glass barrel vault
//             on weathered copper ribs (crown y 11) over a tuff cornice and tuff end arches, lightning rods on the
//             ribs; buttresses between its bays; a frontispiece (a deep stone arch under a little coped gable) before
//             its own door on the street; a rose window in its east lunette. Inside: the skeleton on a stepped dais,
//             specimens on stands, seats facing the skeleton.
//   Dig       (III) a cellar 4 deep under the Gallery (walls x 11..21, z 3..13): a boardwalk round a sunken pit whose bed
//             holds a skeleton lying where it was found, down a ladder under a hatch in the Gallery's floor.

export const SIZE = { 1: [13, 13, 13], 2: [23, 13, 15], 3: [23, 13, 15] } // w, h, d (limits: I 13 x 13 x 13, II 24 x 13 x 21)
export const BASEMENT = 4

// Palette (60/30/10): banded stone (walls and piers: deepslate, tuff, terracotta, sandstone, darkest at the foot) /
// deepslate-tile roofs and dark oak joinery / weathered copper (rods, ribs), bone and glass. Why: a cliff
// face is the scientist's whole subject; the dark tile roof closes the pale top band like a cap of slate, the dark oak
// draws the door and beams, and the copper turns green with the years as a lab's instruments do.
export const PALETTE = {
  plinth: [['cobbled_deepslate', 3], ['deepslate_bricks', 4], ['cracked_deepslate_bricks', 1]],
  // the strata: [from y, block or mix]
  strata: [
    [0, [['cobbled_deepslate', 3], ['deepslate_bricks', 4], ['cracked_deepslate_bricks', 1]]],
    [1, [['deepslate_bricks', 6], ['cracked_deepslate_bricks', 1], ['polished_deepslate', 2]]],
    [2, [['tuff_bricks', 6], ['polished_tuff', 2], ['tuff', 1]]],
    [4, 'terracotta'],
    [5, 'smooth_sandstone'],
    [8, 'terracotta'],
    [9, 'smooth_sandstone'],
  ],
  // the cellar's walls: the real ground, darkest at the bottom
  cellarStrata: [
    [-4, 'cobbled_deepslate'],
    [-3, [['deepslate', 3], ['cobbled_deepslate', 1]]],
    [-2, [['tuff', 4], ['andesite', 1]]],
    [-1, [['packed_mud', 3], ['terracotta', 1]]],
  ],
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  capStair: 'tuff_brick_stairs', // buttress weathering
  sill: 'smooth_sandstone_stairs', // window sills, the Great Window's heads
  coping: { stairs: 'tuff_brick_stairs', full: 'tuff_bricks', apex: 'chiseled_tuff_bricks' }, // the gables' coping
  cornice: 'tuff_bricks', // the vault's feet and its two end arches
  key: 'chiseled_tuff_bricks', // keystones, the carved panel over the Gallery door
  wood: 'dark_oak', // doors, beams, barge boards, furniture
  floor: 'spruce_planks', // the Lab and the Prep Room
  hallFloor: 'polished_tuff', // the Gallery
  dais: 'polished_deepslate',
  daisStep: 'polished_deepslate_stairs',
  stepStair: 'polished_deepslate_stairs',
  copper: 'waxed_weathered_cut_copper',
  rod: 'lightning_rod',
  glass: 'glass_pane', // windows
  vaultGlass: 'glass', // the vault (a roof is whole blocks)
  bone: 'bone_block',
  thinBone: 'diorite_wall', // the skeleton's legs, ribs and tail
  stand: 'polished_tuff', // specimen stands
  walk: 'spruce_planks', // the Dig's boardwalk
  digBed: [['gravel', 3], ['coarse_dirt', 3], ['packed_mud', 1]],
  ground: 'coarse_dirt',
  path: 'gravel',
}

export const MASSES = {
  lab: {
    walls: { x0: 1, x1: 7, z0: 2, z1: 10 }, room: { x0: 2, x1: 6, z0: 3, z1: 9 }, plinth: { x0: 0, x1: 8, z0: 1, z1: 11 },
    wallTop: 4, ties: { zs: [4, 8], y: 4 },
    roof: { axis: 'z', across: [0, 8], along: [3, 9], eaveY: 4, steep: true }, // ridge y 11; the gables (z 2, 10) coped
    buttresses: [ // [x, z, top, toward]
      [1, 1, 3, 'south'], [7, 1, 3, 'south'], [0, 6, 2, 'east'], [1, 11, 3, 'north'], [7, 11, 3, 'north'],
    ],
    rods: [[4, 12, 2], [4, 12, 10]],
    skylight: { x0: 1, x1: 3, z0: 5, z1: 7 }, // the west slope glazed in steps over the analyzer's half of the room
  },
  prep: {
    walls: { x0: 7, x1: 11, z0: 4, z1: 10 }, room: { x0: 8, x1: 10, z0: 5, z1: 9 }, plinth: { x0: 8, x1: 12, z0: 3, z1: 11 },
    wallTop: 3,
    roof: { axis: 'x', across: [3, 11], along: [8, 10], eaveY: 4, steep: false }, // ridge y 8 at z 7; the east gable (x 11) coped
  },
  gallery: {
    walls: { x0: 11, x1: 21, z0: 3, z1: 13 }, room: { x0: 12, x1: 20, z0: 4, z1: 12 }, plinth: { x0: 11, x1: 22, z0: 2, z1: 14 },
    wallTop: 5,
    // the glass vault across z (feet on the side walls' tops), running along x; copper ribs at the piers, stone arches at both ends
    vault: { axis: 'x', across: [3, 13], along: [11, 21], profile: [6, 8, 9, 10, 10, 11, 10, 10, 9, 8, 6], ribs: [14, 18], ends: [11, 21] },
    piersFront: [11, 14, 18, 21], piersBack: [11, 14, 18, 21], pierEast: 8, pierTop: 4,
    rods: [[11, 12, 8], [14, 12, 8], [18, 12, 8], [21, 12, 8]],
    // the frontispiece: a tall stone arch two deep before the door (piers x 15 and 17, z 1..2, to y 5), under its own
    // little coped gable (apex y 7) standing up against the vault's foot
    portal: { x0: 15, x1: 17, z0: 1, z1: 2, top: 5 },
    dais: { x0: 13, x1: 19, z0: 6, z1: 10, y: 1 },
  },
  dig: { walls: { x0: 11, x1: 21, z0: 3, z1: 13 }, depth: 4, pit: { x0: 14, x1: 20, z0: 7, z1: 12 } },
}

export const ROOMS = [
  { name: 'Lab', level: 1, use: "the Fossil Analyzer and the scientist's desk, open to the roof", box: [2, 1, 3, 6, 10, 9] },
  { name: 'Prep Room', level: 1, use: 'sorting bench, finds chest, worktop', box: [8, 1, 5, 10, 3, 9] },
  { name: 'Gallery', level: 2, use: 'the museum hall: the skeleton on its dais, specimens, seats', box: [12, 1, 4, 20, 9, 12] },
  { name: 'Dig', level: 3, use: 'the lower lab: a boardwalk round a sunken dig, the finds bench and chests', box: [12, -3, 4, 20, -1, 12] },
]

// The analyzer: the job block, in its cell from I to III, facing the room; its worker stands at (4, 1, 7).
export const ANALYZER = { at: [4, 1, 8], facing: 'north' }

export const OPENINGS = {
  labDoor: { at: [4, 1, 2], facing: 'south', out: 'north', step: [4, 0, 1] },
  labHood: { x: 4, y: 3, z: 1, lamps: [[3, 2, 1], [5, 2, 1]] }, // lanterns hung either side, clear of the door's path
  greatWindow: { axis: 'x', line: 2, cu: 4, y0: 4, y1: 7 }, // panes 4..7 (8 in the middle), keystone at 9
  prepDoorway: { at: [7, 1, 6] },
  galleryDoorway: { at: [11, 1, 7], level: 2 }, // the Prep Room's east window in I
  galleryDoor: { at: [16, 1, 3], facing: 'south', out: 'north', step: [16, 0, 0], level: 2 },
  hatch: { x: 12, z: 4, facing: 'south', level: 3 }, // ladder against the cellar's front wall; (12, 1, 4) stays open in II
  // windows: cells of glass, the side they look out of, and whether they get a stone sill
  windows: [
    { level: 1, cells: [[2, 2, 2], [2, 3, 2]], out: 'north', sill: true },
    { level: 1, cells: [[6, 2, 2], [6, 3, 2]], out: 'north', sill: true },
    { level: 1, cells: [[1, 2, 4], [1, 3, 4]], out: 'west', sill: true },
    { level: 1, cells: [[1, 2, 8], [1, 3, 8]], out: 'west', sill: true },
    { level: 1, cells: [[7, 2, 3], [7, 3, 3]], out: 'east', sill: true },
    { level: 1, cells: [[2, 2, 10], [2, 3, 10]], out: 'south', sill: true },
    { level: 1, cells: [[6, 2, 10], [6, 3, 10]], out: 'south', sill: true },
    { level: 1, cells: [[4, 5, 10], [4, 6, 10], [4, 7, 10]], out: 'south', sill: false }, // the back gable's lancet
    { level: 1, cells: [[9, 2, 4], [9, 3, 4]], out: 'north', sill: true },
    { level: 1, cells: [[9, 2, 10], [9, 3, 10]], out: 'south', sill: true },
    { level: 1, only: 1, cells: [[11, 2, 7], [11, 3, 7]], out: 'east', sill: true }, // becomes the Gallery's doorway
    { level: 1, cells: [[11, 5, 7], [11, 6, 7]], out: 'east', sill: false }, // the Prep Room's gable light
    { level: 2, cells: [[12, 2, 3], [13, 2, 3], [12, 3, 3], [13, 3, 3], [12, 4, 3], [13, 4, 3]], out: 'north', sill: true },
    { level: 2, cells: [[19, 2, 3], [20, 2, 3], [19, 3, 3], [20, 3, 3], [19, 4, 3], [20, 4, 3]], out: 'north', sill: true },
    { level: 2, cells: [[12, 2, 13], [13, 2, 13], [12, 3, 13], [13, 3, 13], [12, 4, 13], [13, 4, 13]], out: 'south', sill: true },
    { level: 2, cells: [[15, 2, 13], [16, 2, 13], [17, 2, 13], [15, 3, 13], [16, 3, 13], [17, 3, 13], [15, 4, 13], [16, 4, 13], [17, 4, 13]], out: 'south', sill: true },
    { level: 2, cells: [[19, 2, 13], [20, 2, 13], [19, 3, 13], [20, 3, 13], [19, 4, 13], [20, 4, 13]], out: 'south', sill: true },
    { level: 2, cells: [[21, 2, 5], [21, 2, 6], [21, 3, 5], [21, 3, 6], [21, 4, 5], [21, 4, 6]], out: 'east', sill: true },
    { level: 2, cells: [[21, 2, 10], [21, 2, 11], [21, 3, 10], [21, 3, 11], [21, 4, 10], [21, 4, 11]], out: 'east', sill: true },
  ],
  rose: { level: 2, axis: 'x', line: 21, cu: 8, cy: 9 }, // a glass cross in the east lunette
}

// The skeleton in the Gallery (II): slender legs on the dais's top (y 1), bone girdles and spine at y 4, the rib cage
// in slender bones at x 15 and 17, a slender tail drooping west, the neck rising east to a skull looking at the rose
// window. [x, y, z, axis]; 'thin' is a slender bone (P.thinBone, a wall that joins its neighbours).
export const SKELETON = [
  [14, 2, 7, 'thin'], [14, 3, 7, 'thin'], [14, 2, 9, 'thin'], [14, 3, 9, 'thin'], // hind legs
  [18, 2, 7, 'thin'], [18, 3, 7, 'thin'], [18, 2, 9, 'thin'], [18, 3, 9, 'thin'], // fore legs
  [14, 4, 7, 'z'], [14, 4, 8, 'z'], [14, 4, 9, 'z'], [18, 4, 7, 'z'], [18, 4, 8, 'z'], [18, 4, 9, 'z'], // girdles
  [15, 4, 8, 'x'], [16, 4, 8, 'x'], [17, 4, 8, 'x'], // spine
  [15, 4, 7, 'thin'], [15, 3, 7, 'thin'], [15, 4, 9, 'thin'], [15, 3, 9, 'thin'], // ribs
  [17, 4, 7, 'thin'], [17, 3, 7, 'thin'], [17, 4, 9, 'thin'], [17, 3, 9, 'thin'],
  [13, 4, 8, 'thin'], [12, 4, 8, 'thin'], [12, 3, 8, 'thin'], // tail
  [19, 4, 8, 'x'], [19, 5, 8, 'y'], [19, 6, 8, 'y'], [20, 6, 8, 'x'], [20, 7, 8, 'x'], // neck and skull
]

// The skeleton lying in the Dig's bed (III), at the bed's level (y -4) with its skull and a rib a block proud.
export const BURIED = [
  [15, -4, 9, 'x'], [16, -4, 9, 'x'], [17, -4, 9, 'x'], [18, -4, 9, 'x'], [19, -4, 9, 'x'],
  [16, -4, 8, 'z'], [16, -4, 10, 'z'], [18, -4, 8, 'z'], [18, -4, 10, 'z'], [14, -4, 10, 'z'],
  [20, -4, 8, 'x'], [20, -3, 8, 'x'], [16, -3, 10, 'y'],
]

// Every piece of furniture, footprint first (furniture.py kinds; fixtures are blocks the game uses as themselves).
export const FURNITURE = [
  // Lab (I): a clock by the door, the desk under the west window, specimen cupboards, specimens on stands, lamps hung
  // from the tie beams and a long chain from the ridge, a rug before the analyzer
  { level: 1, room: 'Lab', kind: 'clock', part: 'clock', pos: [2, 1, 3], facing: 'east', wood: 'dark_oak' },
  { level: 1, room: 'Lab', kind: 'lamp', part: 'tableLamp', pos: [2, 1, 4], facing: 'east', wood: 'dark_oak' },
  { level: 1, room: 'Lab', kind: 'desk', part: 'desk', pos: [2, 1, 5], facing: 'west', wood: 'dark_oak' },
  { level: 1, room: 'Lab', kind: 'chair', part: 'chair', pos: [3, 1, 5], facing: 'west', wood: 'dark_oak' },
  { level: 1, room: 'Lab', kind: 'cupboard', part: 'cupboard', pos: [2, 1, 7], facing: 'east', wood: 'dark_oak' },
  { level: 1, room: 'Lab', kind: 'cupboard', part: 'cupboard', pos: [2, 1, 9], facing: 'east', wood: 'dark_oak' },
  { level: 1, room: 'Lab', kind: 'cupboard', part: 'cupboard', pos: [6, 1, 9], facing: 'west', wood: 'dark_oak' },
  { level: 1, room: 'Lab', kind: 'fixture', part: 'specimen', pos: [6, 1, 3], piece: 'bone_block', props: { axis: 'x' } },
  { level: 1, room: 'Lab', kind: 'fixture', part: 'specimen', pos: [6, 1, 4], piece: 'honey_block' }, // a lump of amber
  { level: 1, room: 'Lab', kind: 'fixture', part: 'specimen', pos: [6, 1, 8], piece: 'decorated_pot', props: { facing: 'west', cracked: 'false', waterlogged: 'false' } },
  { level: 1, room: 'Lab', kind: 'rug', part: 'rug', cellsAbs: [[3, 1, 7], [4, 1, 7], [5, 1, 7], [3, 1, 6], [4, 1, 6], [5, 1, 6]], colour: 'cyan' },
  { level: 1, room: 'Lab', kind: 'lamp', part: 'hangingLamp', pos: [4, 3, 4], facing: 'north', wood: 'dark_oak' },
  { level: 1, room: 'Lab', kind: 'lamp', part: 'hangingLamp', pos: [3, 3, 8], facing: 'north', wood: 'dark_oak' },
  { level: 1, room: 'Lab', kind: 'lamp', part: 'hangingLamp', pos: [5, 3, 8], facing: 'north', wood: 'dark_oak' },
  { level: 1, room: 'Lab', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 6], facing: 'north', chain: 6, wood: 'dark_oak' },
  // Prep Room (I): the worktop and counters under the front window, the sorting desk and the finds chest at the back,
  // a lamp from the ridge; the way through to the Gallery (II) stays clear down the middle
  { level: 1, room: 'Prep Room', kind: 'fixture', part: 'worktop', pos: [8, 1, 5], facing: 'north' },
  { level: 1, room: 'Prep Room', kind: 'counter', part: 'counter', pos: [9, 1, 5], facing: 'north', wood: 'dark_oak' },
  { level: 1, room: 'Prep Room', kind: 'counter', part: 'counter', pos: [10, 1, 5], facing: 'north', wood: 'dark_oak' },
  { level: 1, room: 'Prep Room', kind: 'fixture', part: 'chest', pos: [8, 1, 9], facing: 'north', block: 'chest' },
  { level: 1, room: 'Prep Room', kind: 'desk', part: 'desk', pos: [9, 1, 9], facing: 'south', wood: 'dark_oak' },
  { level: 1, room: 'Prep Room', kind: 'chair', part: 'chair', pos: [9, 1, 8], facing: 'south', wood: 'dark_oak' },
  { level: 1, room: 'Prep Room', kind: 'fixture', part: 'specimen', pos: [10, 1, 9], piece: 'bone_block', props: { axis: 'y' } },
  { level: 1, room: 'Prep Room', kind: 'lamp', part: 'hangingLamp', pos: [9, 3, 7], facing: 'north', chain: 4, wood: 'dark_oak' },
  // Gallery (II): seats against the front wall facing the skeleton, specimens on stands along the back wall,
  // cupboards in the far corners, a banner on the east wall, lanterns hung from the copper ribs
  { level: 2, room: 'Gallery', kind: 'couch', part: 'couch', pos: [14, 1, 4], facing: 'south', length: 2, wood: 'dark_oak', colour: 'cyan' },
  { level: 2, room: 'Gallery', kind: 'couch', part: 'couch', pos: [20, 1, 4], facing: 'south', length: 2, wood: 'dark_oak', colour: 'cyan' },
  { level: 2, room: 'Gallery', kind: 'fixture', part: 'specimen', pos: [13, 1, 12], piece: 'decorated_pot', props: { facing: 'north', cracked: 'false', waterlogged: 'false' } },
  { level: 2, room: 'Gallery', kind: 'fixture', part: 'specimen', pos: [15, 1, 12], piece: 'honey_block' },
  { level: 2, room: 'Gallery', kind: 'fixture', part: 'specimen', pos: [17, 1, 12], piece: 'bone_block', props: { axis: 'x' } },
  { level: 2, room: 'Gallery', kind: 'fixture', part: 'specimen', pos: [19, 1, 12], piece: 'decorated_pot', props: { facing: 'north', cracked: 'false', waterlogged: 'false' } },
  { level: 2, room: 'Gallery', kind: 'cupboard', part: 'cupboard', pos: [12, 1, 12], facing: 'east', wood: 'dark_oak' },
  { level: 2, room: 'Gallery', kind: 'cupboard', part: 'cupboard', pos: [20, 1, 12], facing: 'west', wood: 'dark_oak' },
  { level: 2, room: 'Gallery', kind: 'picture', part: 'picture', pos: [20, 3, 8], facing: 'west', wood: 'dark_oak', colour: 'blue' },
  { level: 2, room: 'Gallery', kind: 'lamp', part: 'hangingLamp', pos: [14, 6, 5], facing: 'north', chain: 2, wood: 'dark_oak' },
  { level: 2, room: 'Gallery', kind: 'lamp', part: 'hangingLamp', pos: [18, 6, 5], facing: 'north', chain: 2, wood: 'dark_oak' },
  { level: 2, room: 'Gallery', kind: 'lamp', part: 'hangingLamp', pos: [14, 6, 11], facing: 'north', chain: 2, wood: 'dark_oak' },
  { level: 2, room: 'Gallery', kind: 'lamp', part: 'hangingLamp', pos: [18, 6, 11], facing: 'north', chain: 2, wood: 'dark_oak' },
  // Dig (III): the finds desk, two chests and a lamp on the boardwalk; lanterns hung over the pit
  { level: 3, room: 'Dig', kind: 'fixture', part: 'chest', pos: [12, -2, 8], facing: 'east', block: 'chest' },
  { level: 3, room: 'Dig', kind: 'fixture', part: 'chest', pos: [12, -2, 10], facing: 'east', block: 'chest' },
  { level: 3, room: 'Dig', kind: 'desk', part: 'desk', pos: [12, -2, 12], facing: 'west', wood: 'dark_oak' },
  { level: 3, room: 'Dig', kind: 'chair', part: 'chair', pos: [13, -2, 12], facing: 'west', wood: 'dark_oak' },
  { level: 3, room: 'Dig', kind: 'lamp', part: 'tableLamp', pos: [12, -2, 6], facing: 'east', wood: 'dark_oak' },
  { level: 3, room: 'Dig', kind: 'lamp', part: 'hangingLamp', pos: [16, -1, 9], facing: 'north', wood: 'dark_oak' },
  { level: 3, room: 'Dig', kind: 'lamp', part: 'hangingLamp', pos: [19, -1, 11], facing: 'north', wood: 'dark_oak' },
]

// The walking line: the Lab's step -> door -> the analyzer's worker cell; through the Prep Room to the Gallery (II);
// the Gallery's frontispiece -> door -> round the dais; down the hatch to the boardwalk and into the pit (III).
export const WALK = [
  [4, 0, 0], [4, 0, 1], [4, 1, 2], [4, 1, 3], [4, 1, 4], [4, 1, 5], [4, 1, 6], [4, 1, 7],
  [5, 1, 6], [6, 1, 6], [7, 1, 6], [8, 1, 6], [9, 1, 7], [10, 1, 7], [11, 1, 7], [12, 1, 7],
  [16, 0, 0], [16, 0, 1], [16, 1, 2], [16, 1, 3], [16, 1, 4], [16, 1, 5], [12, 1, 5], [12, 1, 11], [20, 1, 11], [20, 1, 5],
  [12, 1, 4], [12, 0, 4], [12, -1, 4], [12, -2, 4], [13, -2, 5], [16, -2, 6], [16, -3, 7], [16, -3, 9],
]

// Outside: fern beds in their own soil either side of the Lab's path, a boulder with a bone in it by the Prep Room,
// lamp posts either side of the Gallery's frontispiece (II).
export const OUTSIDE = {
  path: [[4, 0, 0]],
  fernBeds: [{ x0: 2, x1: 3, z: 0 }, { x0: 5, x1: 6, z: 0 }],
  boulder: { stone: [[9, 0, 1], [10, 0, 1], [10, 0, 0], [9, 0, 2], [9, 1, 1]], bone: [10, 1, 1] },
  lampPosts: { level: 2, at: [[13, 0, 0], [19, 0, 0]] },
}
