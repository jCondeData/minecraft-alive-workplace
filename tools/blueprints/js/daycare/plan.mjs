// The Daycare (daycare, daycare_2), the plan: drawn before any wall. Rooms, every furniture footprint and the walking
// line come first; recipe.mjs assembles both levels from the parts library (../parts) to fit this plan.
//
// Three rough concepts (ROADMAP 49, tools/blueprints/REBUILD.md):
//   A. The Clutch. Massing: the house IS an egg, 11 across and 14 tall, standing broad end down in a nest of laid
//      logs and straw; Level II lays a second, smaller egg of another species in the same nest. Roof: none of its own,
//      the shell curves over into a pointed crown, a zigzag "hatching crack" of glass round each egg is the window;
//      one little timber porch with a dark gable at each egg's door. Plan: one round room per egg, the Pasture Block
//      in the middle on a straw ring, the keeper's desk and a visitors' couch round the curved wall. Story: a
//      keeper's whole trade is pairs and eggs, so she works inside one, and the egg is just starting to hatch.
//   B. The Brood Barn. Massing: a long Dutch barn with a gambrel roof and a lean-to aisle, a fenced paddock in front.
//      Roof: gambrel, ridge along z, a hay hood over the loft door. Plan: the Pasture Block in a box stall off the
//      aisle, Level II opens a second stall. Story: a farmer took in other people's pairs.
//   C. The Nursery Cloister. Massing: a low square cloister of nesting alcoves round a lawn with a pool; Level II
//      closes a second court. Roof: a continuous pent roof on posts round the court. Plan: the Pasture Block under a
//      pavilion in the court. Story: a quiet walled garden where pairs are left in peace.
//   Strongest: A. B is every farm barn in the village already (the Ranch is one), C reads as a garden from the
//   front and its pavilion as a gazebo; only A says "eggs" from across the valley and has a silhouette no other
//   building has. Its risks are the ones to design against: a blob with no depth (answered by the nest ring at the
//   foot, the porch's frame and gable, the crack band, the spots in clusters) and a toy (answered by keeping the
//   porch, floor, furniture and lamps properly built). Built: A.
//
// Story: "The Clutch". The keeper's house is an egg in a nest. Its shell is pale clay (white terracotta) blotched with
// green; round its shoulders it has begun to crack: the crown's broken lip stands out a block in a zigzag, and on its
// three free quarters a window is set a block deep into the shell between dark jambs over a sill (round 2, after the
// critic: depth on every face, the back included). It stands in a nest of dark oak and spruce logs laid round it with straw packed between. Visitors come up a
// path between two flower beds and two lamps to a little timbered porch with a dark gable, and step into one round
// room: the Pasture Block in the middle on a ring of straw with nests at its corners, the keeper's table and chair under
// the west window with a lamp on it, a couch for visiting trainers at the back. Level II: a second, smaller egg settles
// into the same nest behind and to the east, its porch turned east beside a garden with a bench and flower beds.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Level I is Level II's low-x, low-z corner; II adds toward +x and +z only.
//
// Named parts: Big Egg (I), Little Egg (II), Nest, North Porch (I), East Porch (II),
// Front Garden (beds, lamps, II's bench), Lip (each crown's broken edge), Windows (set into the skin).

export const SIZES = { 1: [15, 14, 20], 2: [19, 14, 27] } // w, h, d (II at most 19 x 18 x 27)

// Palette (60/30/10): pale eggshell clay / dark logs and straw of the nest and porch frame / the spots, glass and
// garden. Why: the eggs must read as eggs from far off (pale, smooth, round, against grass), the nest as something
// gathered (logs and straw, the darkest material at the foot), and the porch and roof dark so the pale shell is
// the brightest thing on the plot. The spots, the glass and the garden are the 10 %: green blotches, fewer on the younger egg.
export const PALETTE = {
  shell: 'white_concrete',
  spots: { big: 'lime_terracotta', little: 'lime_terracotta' },
  floor: 'spruce_planks',
  straw: 'hay_block',
  nestLogs: [['dark_oak_log', 4], ['spruce_log', 3], ['stripped_spruce_log', 1]],
  frame: 'dark_oak_log',
  infill: 'spruce_planks',
  roof: { full: 'dark_oak_planks', stairs: 'dark_oak_stairs', slab: 'dark_oak_slab' },
  trim: 'spruce',
  wood: 'spruce', // doors, furniture, fences
  glass: 'glass_pane', // every window is a pane between jambs: set into the skin's flat quarters, and in the porches
  sill: 'dark_oak_stairs',
  path: 'dirt_path',
  step: 'mossy_cobblestone_stairs',
  lampPost: 'dark_oak_fence',
}

// The eggs: centre (cx, cz), widest radius r at height yw, lower and upper semi-axes (parts/daycare_clutch eggSolid).
// lipY: where the cap's broken lip stands out (a zigzag rising 0..2 `teeth` times round); windows: the quarters that
// get a window set into the skin (the porch's quarter has the door), from y `winY`, two rows tall.
export const EGGS = {
  big: { cx: 7, cz: 12, r: 5.3, yw: 3, lo: 4.5, hi: 9.5, lipY: 7, teeth: 6, phase: 0.05, windows: ['west', 'east', 'south'], winY: 4, spotShare: 0.22 }, // top y 12
  little: { cx: 12, cz: 21, r: 4.3, yw: 2.5, lo: 3.5, hi: 7.5, lipY: 5, teeth: 5, phase: 0.3, windows: ['north', 'west', 'south'], winY: 3, spotShare: 0.12 }, // top y 10, Level II
}

// The nest: how far past each egg's widest radius it reaches on each layer (y 0, 1, 2), stepping in as it rises.
export const NEST = { reach: [0.6, 0.9, 0.4] }

// Porches: a little timber vestibule at each egg's door, its inside carried through the shell as the doorway.
export const PORCHES = {
  north: { // Level I: posts x 5 and 9 on z 5, side walls back to the shell (z 8), door in the middle of the front
    posts: [[5, 5], [9, 5]], sides: [[5, 6, 7], [9, 6, 7]], front: { z: 5, x0: 6, x1: 8 },
    door: [7, 1, 5], facing: 'south', out: 'north', step: [7, 0, 4],
    roof: { axis: 'z', across: [4, 10], along: [4, 8], eaveY: 4, steep: false },
    windows: [[[5, 2, 6], [5, 2, 7]], [[9, 2, 6], [9, 2, 7]]],
    porthole: [7, 5, 5],
    floor: [6, 5, 8, 7],          // x0, z0, x1, z1 at y 0
    doorway: [6, 1, 7, 8, 3, 9],  // the shell cleared behind it
    crackSkip: { x0: 3, x1: 11, z0: 0, z1: 9, y1: 8 },
  },
  east: { // Level II: posts z 19 and 23 on x 17, side walls back to the shell (x 15), door in the middle of the front
    posts: [[17, 19], [17, 23]], sides: [[19, 16, 16], [23, 16, 16]], front: { x: 17, z0: 20, z1: 22 },
    door: [17, 1, 21], facing: 'west', out: 'east', step: [18, 0, 21],
    roof: { axis: 'x', across: [18, 24], along: [15, 18], eaveY: 4, steep: false },
    windows: [[[16, 2, 19]], [[16, 2, 23]]],
    porthole: [17, 5, 21],
    floor: [16, 20, 17, 22],
    doorway: [14, 1, 20, 16, 3, 22],
    crackSkip: { x0: 14, x1: 18, z0: 17, z1: 25, y1: 8 },
  },
}

export const ROOMS = [
  { name: 'Brooding Room', level: 1, use: 'the first keeper\'s Pasture Block, her desk, a couch for visitors', egg: 'big' },
  { name: 'Hatchling Room', level: 2, use: 'the second keeper\'s Pasture Block and a bench', egg: 'little' },
]

// The job blocks (cobblemon:pasture, two tall; only the bottom is the job site) and the straw ring round each.
export const PASTURES = [
  { level: 1, at: [7, 1, 12], facing: 'north', stand: [7, 1, 11] }, // stand: the free cell the keeper takes, by the door side
  { level: 2, at: [12, 1, 21], facing: 'east', stand: [13, 1, 21] },
]

// Every piece of furniture, footprint first (furnitureParts; each records its furniture spot). Cells next to each
// Pasture Block stay clear: the keeper's standing spots.
export const FURNITURE = [
  // Brooding Room (Level I), the keeper's room: her table on the west wall under the window (a lamp at one end at eye
  // level, her candle and ledger at the other) with her chair at it, a couch for visiting trainers at the back wall
  // facing the Pasture Block over a rug, a cupboard of care books on the east wall, and straw nests on the four
  // corners of the Pasture Block's straw ring (its four sides stay clear: the keeper's standing cells).
  { level: 1, room: 'Brooding Room', kind: 'lamp', part: 'tableLamp', pos: [4, 1, 11], facing: 'east', wood: 'dark_oak' },
  { level: 1, room: 'Brooding Room', kind: 'desk', part: 'desk', pos: [4, 1, 12], facing: 'west', wood: 'dark_oak' },
  { level: 1, room: 'Brooding Room', kind: 'chair', part: 'chair', pos: [5, 1, 12], facing: 'west', wood: 'spruce' },
  { level: 1, room: 'Brooding Room', kind: 'couch', part: 'couch', pos: [6, 1, 15], facing: 'north', length: 3, arms: true, wood: 'spruce', colour: 'lime' },
  { level: 1, room: 'Brooding Room', kind: 'rug', part: 'rug', cellsAbs: [[6, 1, 14], [7, 1, 14], [8, 1, 14]], colour: 'lime' },
  { level: 1, room: 'Brooding Room', kind: 'cupboard', part: 'cupboard', pos: [10, 1, 12], facing: 'west', wood: 'spruce' },
  { level: 1, room: 'Brooding Room', kind: 'rug', part: 'rug', cellsAbs: [[6, 1, 11], [8, 1, 11], [6, 1, 13], [8, 1, 13]], colour: 'yellow' },
  // Hatchling Room (Level II): a couch on the west wall facing the Pasture Block and the door, a low table before it,
  // a lamp on a side table off its south end, straw nests on the ring's corners.
  { level: 2, room: 'Hatchling Room', kind: 'couch', part: 'couch', pos: [10, 1, 20], facing: 'east', length: 3, arms: false, wood: 'spruce', colour: 'lime' },
  { level: 2, room: 'Hatchling Room', kind: 'table', part: 'table', pos: [11, 1, 21], facing: 'west', wood: 'spruce', colour: 'lime' },
  { level: 2, room: 'Hatchling Room', kind: 'lamp', part: 'tableLamp', pos: [11, 1, 23], facing: 'west', wood: 'dark_oak' },
  { level: 2, room: 'Hatchling Room', kind: 'rug', part: 'rug', cellsAbs: [[13, 1, 20], [13, 1, 22], [11, 1, 20], [11, 1, 22]], colour: 'yellow' },
]

// The walking lines: Level I from the front edge (x 7) up the path, the step, the door, the porch, into the room and
// to the Pasture Block; Level II also from the front walk up the side path to the east porch and its Pasture Block.
export const WALK = {
  1: [[7, 0, 0], [7, 0, 3], [7, 0, 4], [7, 1, 5], [7, 1, 6], [7, 1, 8], [7, 1, 10], [7, 1, 11]],
  2: [[9, 0, -1], [17, 0, 4], [18, 0, 21], [17, 1, 21], [16, 1, 21], [14, 1, 21], [13, 1, 21]], // over the grass
}

// The ground: paths of trodden earth, flower beds in their own soil, lamps on posts at the paths (no lamp stands on
// a room's floor), and Level II's bench on the side path looking at the eggs.
export const GROUND = {
  paths: {
    1: [[6, 0, 8, 3]],                      // x0, z0, x1, z1 at y 0
    2: [],                                   // none: the east porch is reached over the grass past the garden
  },
  stepSides: { 1: [[6, 0, 4], [8, 0, 4]] }, // path cells either side of the step
  beds: {
    1: [{ x0: 2, x1: 4, z: 2, plants: ['poppy', 'oxeye_daisy', 'cornflower'] }, { x0: 10, x1: 12, z: 2, plants: ['cornflower', 'oxeye_daisy', 'poppy'] }],
    2: [{ x0: 12, x1: 14, z: 3, plants: ['allium', 'azure_bluet', 'allium'] }, { x0: 14, x1: 16, z: 11, plants: ['azure_bluet', 'pink_tulip', 'azure_bluet'] }],
  },
  lamps: { 1: [[5, 0, 2], [9, 0, 2]], 2: [[17, 0, 17]] },
  bench: { 2: { x0: 12, x1: 14, z: 6, facing: 'south' } }, // seats looking south at the eggs, a slatted back
}

// For the ship step (the brief's pinned cells): Level I's Pasture Block stays at (7, 1, 12) in both levels; Level II's
// second is at (12, 1, 21), facing its east porch (was (7, 1, 20)): 10 blocks from the first, each with its own room
// and standing cells. Hay to pin: Level I (7, 0, 11) (the straw ring by the Pasture Block), Level II (12, 0, 20).
// Placed blocks: Level I 595, Level II 994; every one of Level I's stays in Level II. Sizes: 15 x 14 x 20 and
// 19 x 14 x 27. No chest, bed, barrel or composter; the only job blocks are the Pasture Blocks.
