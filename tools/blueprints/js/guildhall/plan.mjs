// Guildhall, the plan: drawn before any wall (ROADMAP 49, brief tools/blueprints/briefs/guildhall.md).
//
// Three concepts:
//   A. Charter Tower: a plain long hall with a square tower over the door, the charter hung high in the tower room.
//      Strong skyline, but the hall itself is a barn; the tower does all the work.
//   B. Cross Hall: a cruciform plan under four gables meeting at a crossing louvre. Grand from above, muddled at eye
//      level, and nothing about it says "trade".
//   C. Market Guildhall (chosen): the real guildhall type (Thaxted, Lavenham): an open stone arcade on the ground where
//      the village trades out of the rain, and the guild's hall jettied over it on oak corbels, a long open-roofed room
//      under a deep slate roof, a gablet over the entrance and a lantern louvre on the ridge. Strongest because the
//      building tells its story: the guild stands on (and above) the market it governs, and the charter hangs at the
//      head of the hall where the Master sits.
//
// Story: the guild raised its hall over the market square. Stone where carts and weather hit it, white plaster and
// dark oak above, slate tiles. II: the guild grows rich and adds a taller cross-wing at the east (a counting house
// below, a chapter library above, joined to the hall), its steep gable to the street outranking the hall's ridge.
// III: a strongroom dug under the arcade, down a ladder under a hatch in the undercroft floor.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the clicked block's layer), z from the front (0) back.
// Masses:
//   Arcade    ground, open stone piers x 1..15, z 2..11 (back wall solid), beam course y 4; floor y 0.
//   Hall      the jettied upper floor y 5 (x 1..15, z 1..11), walls y 6..9, tie beams y 10, 45-degree slate roof
//             along x (ridge z 6, y 16), gable overhang x 0 and 16.
//   Gablet    a small cross gable over the entrance (x 4..12, z 0..5), its window lighting the hall.
//   Louvre    a lantern louvre on the ridge at x 8.
//   Wing (II) x 16..24: stone counting house y 1..4, jettied chapter room y 6..9, steep gable to the front (ridge y 17).
//   Strongroom (III) under the arcade: walls x 1..15, z 2..11, floor y -4.

export const LEVELS = {
  guildhall: { size: [17, 22, 13], wing: false, cellar: false, base: 0 },
  guildhall_2: { size: [25, 22, 13], wing: true, cellar: false, base: 0 },
  guildhall_3: { size: [25, 22, 13], wing: true, cellar: true, base: 4 },
}

// Palette 60/30/10: stone and white plaster (walls) / dark oak frame and slate roof / red cloth, lanterns, spruce.
export const PALETTE = {
  stone: [['stone_bricks', 7], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1]],
  plinth: [['cobblestone', 3], ['stone', 2], ['andesite', 1]],
  course: 'polished_andesite',
  cap: 'chiseled_stone_bricks',
  arch: 'stone_brick_stairs',
  infill: 'white_concrete',
  frame: 'dark_oak_log',
  trim: 'dark_oak',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'spruce_planks',
  under: 'polished_andesite',
  wood: 'spruce',
  glass: 'glass_pane',
  cloth: 'red',
  vault: [['deepslate_bricks', 4], ['cracked_deepslate_bricks', 1]],
  vaultFloor: 'deepslate_tiles',
}

export const ARCADE = {
  front: { line: 2, piers: [1, 5, 11, 15] },      // openings 3 / 5 (the way in) / 3 wide
  west: { line: 1, piers: [2, 6, 11] },
  east: { line: 15, piers: [2, 6, 11] },
  top: 4,
}
export const HALL = { x0: 1, x1: 15, z0: 1, z1: 11, floorY: 5, wallTop: 9, beamY: 10,
  frontPosts: [1, 4, 6, 8, 10, 12, 15], backPosts: [1, 4, 8, 12, 15], sidePosts: [1, 4, 8, 11],
  roof: { axis: 'x', across: [0, 12], along: [0, 16], eaveY: 10, steep: false },
  gablet: { axis: 'z', across: [4, 12], along: [0, 5], eaveY: 10, steep: false },
  // round 2: a rear gablet over the charter wall breaks the long back slope (its window lights the charter)
  rearGablet: { axis: 'z', across: [4, 12], along: [7, 12], eaveY: 10, steep: false },
  ties: [4, 8, 12] }
export const WING = { x0: 16, x1: 24, z0: 1, z1: 11, roof: { axis: 'z', across: [16, 24], along: [0, 12], eaveY: 10, steep: true },
  // round 2: a steep cross gable on the wing's east side, so its end to the lane is a gable, not a bare slope
  eastGable: { axis: 'x', across: [3, 9], along: [21, 24], eaveY: 10, steep: true } }
export const CELLAR = { x0: 1, z0: 2, x1: 15, z1: 11, depth: 4, hatch: [13, 10] }

export const OPENINGS = {
  stairs: { flight: [[7, 1, 10], [6, 2, 10], [5, 3, 10], [4, 4, 10], [3, 5, 10]], rises: 'west', well: [[5, 5, 10], [4, 5, 10]] },
  rail: [[4, 6, 9], [5, 6, 9], [6, 6, 9], [6, 6, 10]],
  windows: [
    { cells: [[2, 7, 1], [3, 7, 1], [2, 8, 1], [3, 8, 1]], out: 'north', dress: 'shutters' },
    { cells: [[13, 7, 1], [14, 7, 1], [13, 8, 1], [14, 8, 1]], out: 'north', dress: 'shutters' },
    { cells: [[7, 7, 1], [7, 8, 1]], out: 'north' }, { cells: [[9, 7, 1], [9, 8, 1]], out: 'north' },
    { cells: [[7, 11, 1], [8, 11, 1], [9, 11, 1], [8, 12, 1]], out: 'north' },
    { cells: [[2, 7, 11], [3, 7, 11], [2, 8, 11], [3, 8, 11]], out: 'south' },
    { cells: [[13, 7, 11], [14, 7, 11], [13, 8, 11], [14, 8, 11]], out: 'south' },
    { cells: [[1, 7, 5], [1, 8, 5], [1, 7, 6], [1, 8, 6], [1, 7, 7], [1, 8, 7]], out: 'west' },
    { cells: [[1, 12, 6], [1, 13, 6]], out: 'west' },
    { cells: [[15, 7, 5], [15, 8, 5], [15, 7, 7], [15, 8, 7]], out: 'east', level: 1 },
    { cells: [[15, 12, 6], [15, 13, 6]], out: 'east', level: 1 },
    { cells: [[7, 11, 11], [8, 11, 11], [9, 11, 11], [8, 12, 11]], out: 'south' }, // the rear gablet
    { cells: [[1, 7, 9], [1, 8, 9]], out: 'west' }, // the master's end, west
  ],
  wingWindows: [
    { cells: [[18, 2, 2], [18, 3, 2]], out: 'north' }, { cells: [[22, 2, 2], [22, 3, 2]], out: 'north' },
    { cells: [[24, 2, 5], [24, 3, 5], [24, 2, 7], [24, 3, 7]], out: 'east' },
    { cells: [[18, 7, 1], [18, 8, 1]], out: 'north' }, { cells: [[22, 7, 1], [22, 8, 1]], out: 'north' },
    { cells: [[19, 11, 1], [20, 11, 1], [21, 11, 1], [20, 12, 1], [20, 13, 1], [20, 14, 1]], out: 'north' },
    { cells: [[24, 7, 4], [24, 8, 4], [24, 7, 8], [24, 8, 8]], out: 'east' },
    { cells: [[19, 7, 11], [19, 8, 11], [21, 7, 11], [21, 8, 11]], out: 'south' },
    { cells: [[24, 12, 6], [24, 13, 6]], out: 'east' }, // the east cross gable
    { cells: [[19, 2, 11], [19, 3, 11], [21, 2, 11], [21, 3, 11]], out: 'south' }, // the counting house, back
  ],
  wingDoor: [20, 1, 2], wingGround: [16, 1, 8], wingUpper: [[15, 6, 6], [16, 6, 6]],
}

// Charter frames (entities: rebuild.py carries blocks only, so frames.py adds them to the candidate).
// facing: the way the frame looks out (it hangs on the block behind it).
export const FRAMES = [
  { at: [7, 7, 10], facing: 'north' }, { at: [8, 7, 10], facing: 'north' }, { at: [9, 7, 10], facing: 'north' },
  { at: [10, 2, 10], facing: 'north' },
  { at: [23, 7, 6], facing: 'west', wing: true }, { at: [20, 2, 10], facing: 'north', wing: true },
  { at: [8, -2, 10], facing: 'north', cellar: true },
]

export const FURNITURE = [
  // Hall: the guild's long table, ten seats, the Master's chair at its head under the east gable
  ...[6, 7, 8, 9, 10].map(x => ({ room: 'Hall', kind: 'table', part: 'table', pos: [x, 6, 6], facing: 'north', wood: 'spruce', colour: 'red' })),
  ...[6, 7, 8, 9, 10].map(x => ({ room: 'Hall', kind: 'chair', part: 'chair', pos: [x, 6, 5], facing: 'south', wood: 'spruce' })),
  ...[6, 7, 8, 9, 10].map(x => ({ room: 'Hall', kind: 'chair', part: 'chair', pos: [x, 6, 7], facing: 'north', wood: 'spruce' })),
  { room: 'Hall', kind: 'chair', part: 'armchair', pos: [11, 6, 6], facing: 'west', wood: 'dark_oak', colour: 'red' },
  { room: 'Hall', kind: 'picture', part: 'picture', pos: [6, 8, 10], facing: 'north', wood: 'spruce', colour: 'red' },
  { room: 'Hall', kind: 'picture', part: 'picture', pos: [10, 8, 10], facing: 'north', wood: 'spruce', colour: 'red' },
  { room: 'Hall', kind: 'cupboard', part: 'cupboard', pos: [12, 6, 10], facing: 'north', wood: 'spruce' },
  { room: 'Hall', kind: 'cupboard', part: 'cupboard', pos: [13, 6, 10], facing: 'north', wood: 'spruce' },
  { room: 'Hall', kind: 'clock', part: 'clock', pos: [14, 6, 10], facing: 'north', wood: 'spruce' },
  { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [4, 9, 6], facing: 'north', wood: 'spruce' },
  { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [8, 9, 4], facing: 'north', wood: 'spruce' },
  { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [12, 9, 6], facing: 'north', wood: 'spruce' },
  // Arcade: lanterns from the floor beams, a charter cloth either side of the frame on the back wall
  { room: 'Arcade', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 6], facing: 'north', wood: 'spruce' },
  { room: 'Arcade', kind: 'lamp', part: 'hangingLamp', pos: [12, 4, 6], facing: 'north', wood: 'spruce' },
  { room: 'Arcade', kind: 'picture', part: 'picture', pos: [11, 2, 10], facing: 'north', wood: 'spruce', colour: 'red' },
  // Wing (II): the counting house below, the chapter library above
  { room: 'Counting house', kind: 'cupboard', part: 'cupboard', pos: [23, 1, 4], facing: 'west', wood: 'spruce', wing: true },
  { room: 'Counting house', kind: 'lamp', part: 'hangingLamp', pos: [20, 4, 6], facing: 'north', wood: 'spruce', wing: true },
  { room: 'Chapter room', kind: 'cupboard', part: 'cupboard', pos: [23, 6, 3], facing: 'west', wood: 'spruce', wing: true },
  { room: 'Chapter room', kind: 'cupboard', part: 'cupboard', pos: [23, 6, 4], facing: 'west', wood: 'spruce', wing: true },
  { room: 'Chapter room', kind: 'cupboard', part: 'cupboard', pos: [23, 6, 8], facing: 'west', wood: 'spruce', wing: true },
  { room: 'Chapter room', kind: 'cupboard', part: 'cupboard', pos: [23, 6, 9], facing: 'west', wood: 'spruce', wing: true },
  { room: 'Chapter room', kind: 'picture', part: 'picture', pos: [23, 8, 6], facing: 'west', wood: 'spruce', colour: 'red', wing: true },
  { room: 'Chapter room', kind: 'lamp', part: 'hangingLamp', pos: [20, 9, 6], facing: 'north', wood: 'spruce', wing: true },
  // round 2: the counting house (a tally table under its lamp, the strongbox chests, the clerk's lit desk) and the
  // records room above (shelves round the walls, a reading table, a lit writing desk, a lectern for the rolls)
  ...[19, 20, 21].map(x => ({ room: 'Counting house', kind: 'table', part: 'table', pos: [x, 1, 6], facing: 'north', wood: 'spruce', colour: 'green', wing: true })),
  ...[19, 20, 21].map(x => ({ room: 'Counting house', kind: 'chair', part: 'chair', pos: [x, 1, 7], facing: 'north', wood: 'spruce', wing: true })),
  { room: 'Counting house', kind: 'chair', part: 'armchair', pos: [20, 1, 5], facing: 'south', wood: 'dark_oak', colour: 'green', wing: true },
  { room: 'Counting house', kind: 'chest', part: 'chest', pos: [23, 1, 6], facing: 'west', wing: true },
  { room: 'Counting house', kind: 'chest', part: 'pantry', pos: [23, 1, 7], facing: 'west', block: 'barrel', wing: true },
  { room: 'Counting house', kind: 'desk', part: 'desk', pos: [23, 1, 9], facing: 'west', wood: 'spruce', wing: true },
  { room: 'Counting house', kind: 'lamp', part: 'tableLamp', pos: [17, 1, 3], facing: 'south', wood: 'spruce', wing: true },
  { room: 'Counting house', kind: 'cupboard', part: 'cupboard', pos: [17, 1, 10], facing: 'north', wood: 'spruce', wing: true },
  ...[18, 20, 22].map(x => ({ room: 'Chapter room', kind: 'cupboard', part: 'cupboard', pos: [x, 6, 10], facing: 'north', wood: 'spruce', wing: true })),
  ...[19, 20].map(x => ({ room: 'Chapter room', kind: 'table', part: 'table', pos: [x, 6, 6], facing: 'north', wood: 'spruce', colour: 'red', wing: true })),
  ...[19, 20].map(x => ({ room: 'Chapter room', kind: 'chair', part: 'chair', pos: [x, 6, 5], facing: 'south', wood: 'spruce', wing: true })),
  ...[19, 20].map(x => ({ room: 'Chapter room', kind: 'chair', part: 'chair', pos: [x, 6, 7], facing: 'north', wood: 'spruce', wing: true })),
  { room: 'Chapter room', kind: 'desk', part: 'desk', pos: [17, 6, 10], facing: 'north', wood: 'spruce', wing: true },
  { room: 'Chapter room', kind: 'lamp', part: 'tableLamp', pos: [17, 6, 2], facing: 'south', wood: 'spruce', wing: true },
  // the hall table's lamp, at its foot; the arcade's two market counters with their stock barrels behind
  { room: 'Hall', kind: 'lamp', part: 'tableLamp', pos: [5, 6, 6], facing: 'east', wood: 'spruce' },
  ...[4, 5, 6].map(z => ({ room: 'Arcade', kind: 'counter', part: 'counter', pos: [3, 1, z], facing: 'east', wood: 'spruce', top: 'spruce_slab', topSlab: true })),
  ...[5, 6, 7].map(z => ({ room: 'Arcade', kind: 'counter', part: 'counter', pos: [13, 1, z], facing: 'west', wood: 'spruce', top: 'spruce_slab', topSlab: true })),
  { room: 'Arcade', kind: 'chest', part: 'pantry', pos: [2, 1, 4], facing: 'east', block: 'barrel' },
  { room: 'Arcade', kind: 'chest', part: 'pantry', pos: [14, 1, 7], facing: 'west', block: 'barrel' },
]

// The walking line: in under the great arch, across the arcade, up the back stair, along the hall to the charter.
export const WALK = [[8, 0, 0], [8, 1, 2], [8, 1, 6], [7, 1, 9], [6, 1, 10], [3, 4, 10], [2, 5, 10], [2, 6, 8], [5, 6, 8], [8, 6, 8]]
