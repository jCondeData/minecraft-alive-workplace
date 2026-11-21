// The Quarry, the plan: drawn before any block. Story, palette, masses, the pit, openings, every furniture footprint
// and the walking line come first; recipe.mjs then assembles it from the parts library (../parts) in passes.
//
// Three rough concepts (brief: tools/blueprints/briefs/quarry.md):
//   A. "The Headframe": a tall timber gallows frame straddling the pit on its curb, a big sheave wheel on its head and a
//      chain hanging a skip into the hole; two raked back-struts run down onto the roof of a low stone hoist-and-smelt
//      house whose brick stack rises out of its ridge right over the blast furnace. Massing: one hero frame (17 high)
//      over an open hole, one long low gable (9) and a stack (15) between. Story: a claim that hauls its rock up by
//      rope and burns its ore on the spot.
//   B. "The Terraced Cut": no frame; the rim is built up as stepped courses of dressed blocks like a cut face, a squat
//      flat-roofed stone lodge with a parapet beside it, a spoil terrace. Massing: low and wide, nothing tall. Story: a
//      mason's cut worked by hand from the edge.
//   C. "The Pit Barn": a broad open-sided timber roof on posts over the whole pit (its eaves above y 2), the furnace and
//      chests under one end. Massing: one big gable. Story: a covered dig that works in any weather.
// Strongest: A. It is the only one with a silhouette you name from 20 blocks away (a mine headframe and a smelter
// stack), the hole stays open to the sky so the pit reads as the building's heart rather than as an empty yard, and
// the struts tie the frame to the house so the two masses are one machine. B is the safest (all low curb, no risk) but
// reads as a fenced hole; C hides the pit and needs open wall panels (holes.py's `open`). Building A.
//
// Coordinates: x across the front (west 0 -> east 18), y up (0 = the layer on the clicked block; the ground is y -1),
// z from the front (z = 0, north) to the back. No basement: the pit is below y 0 and so outside the file entirely (the
// ship step sets StarterBlueprints.QUARRY's basement to 0 and takes QUARRY out of WITH_BASEMENTS and the two skips).
//
// Pinned (saves): the pit stays at x 1-8, z 2-10, y -1 to -10 (QuarryBuilds.PIT_MIN / PIT_MAX: 8 x 9 x 10 = 720
// blocks), the blast furnace stays at (14, 1, 7) (QuarryBuilds.FURNACE). Nothing moves; no code change needed.
// The test ground lays its own chest at FURNACE two blocks north, (14, 1, 5); the drawing's drop-off double chest stands
// against the east wall at (16, 1, 4-5) (ship step: furniture.py wants a chest's back to a wall).
//
// Masses (named parts the owner's feedback can point at):
//   Pit         the ground left as it lies, x 1-8, z 2-10: nothing drawn at or below y 1 over it (air at y 0-1).
//   Curb        a stone kerb ring at y 0 round the pit (x 0 and 9, z 1 and 11), a spruce fence rail on it with log
//               posts and lanterns at the four corners, open on the east between the Headframe's legs (the Gap).
//   Headframe   four dark oak legs on the curb (x 0 and 9, z 4 and 8) up to a tie ring at y 6, then stepped in a block
//               (x 1 and 8, a stair shoulder outside each step) up to a head ring at y 11: it tapers like a real
//               headframe. Knee braces under both rings, a sheave beam along z 6, and on it the Sheave: a 5-wide spoked
//               wheel (y 12-16) with a spruce rim.
//               A chain hangs a skip (a hopper) over the pit; a second hangs a lantern.
//   Struts      two raked dark oak back-legs from the head (x 9, y 10-11) down into the Hoist House roof (x 12, y 7).
//   Hoist House the stone shed x 11-17, z 3-11, walls y 1-3, spruce beam ring y 4, a deepslate tile gable roof (ridge
//               along z at x 14, y 9) with timber-boarded gables; a stone plinth course one block out all round (y 0).
//   Stack       a brick flue straight up from the blast furnace (14, 2..14, 7) through the ridge, a corbelled cap and
//               smoke; a stone hood round it inside.
//   Spoil Heap  the rubble the Miner throws up, piled behind the pit (z 12-13), with an ore or two in it.

export const SIZE = [19, 17, 14] // w, h, d (plot limit 28 x 18 x 28; was 17 x 9 x 12 + 5 basement)

// Palette (60/30/10): the quarry's own rock (cobble, andesite, stone, tuff) for kerb, plinth and walls / dark oak
// headframe and spruce joinery with a deepslate tile roof / brick stack, chains, lanterns and the skip. Why: everything
// here came out of the hole or was cut in the valley; the dark frame and roof read against the pale rock, and the warm
// brick stack is the one hot colour, where the fire is.
export const PALETTE = {
  rock: [['cobblestone', 5], ['andesite', 3], ['stone', 2], ['tuff', 2], ['mossy_cobblestone', 1]],
  kerb: [['stone_bricks', 5], ['cracked_stone_bricks', 2], ['andesite', 1]],
  floor: [['polished_andesite', 3], ['andesite', 1], ['stone', 1]],
  spoil: [['gravel', 4], ['cobblestone', 3], ['andesite', 2], ['coarse_dirt', 2], ['tuff', 1]],
  frame: 'dark_oak_log', frameStairs: 'dark_oak_stairs', frameSlab: 'dark_oak_slab', frameFence: 'dark_oak_fence',
  joinery: 'spruce_log', wood: 'spruce', boards: 'spruce_planks', stud: 'stripped_spruce_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  stack: 'bricks', stackStairs: 'brick_stairs', hood: 'stone_brick_stairs',
  path: ['cobblestone_slab', 'andesite_slab'], lip: 'cobblestone_slab',
  glass: 'glass_pane',
}

export const PIT = { x0: 1, x1: 8, z0: 2, z1: 10, y0: -10, y1: -1 } // QuarryBuilds.PIT_MIN..PIT_MAX: never drawn
export const FURNACE = [14, 1, 7] // QuarryBuilds.FURNACE

export const MASSES = {
  curb: { x0: 0, x1: 9, z0: 1, z1: 11, gap: { x: 9, z0: 5, z1: 7 } },
  headframe: {
    legs: [[0, 4], [0, 8], [9, 4], [9, 8]], legTop: 10, tieY: 6, headY: 11,
    ring: { x0: 0, x1: 9, z0: 4, z1: 8 }, head: { x0: 1, x1: 8, z0: 4, z1: 8 }, sheaveZ: 6,
    sheave: { x0: 2, x1: 6, y0: 12, z: 6 }, // a 5-wide wheel: rim, four spokes, the axle at (4, 14, 6)
    skip: { x: 7, z: 6, y: 3 }, // a hopper on a chain from the sheave beam
    lamp: { x: 1, z: 6, y: 5 },
    struts: [4, 8], // the back-legs' z: from the head's east corner (9, 10) down to (12, 7) on the roof
  },
  house: {
    walls: { x0: 11, x1: 17, z0: 3, z1: 11 }, room: { x0: 12, x1: 16, z0: 4, z1: 10 },
    wallTop: 3, beamY: 4, plinth: { x0: 10, x1: 18, z0: 2, z1: 12 },
    roof: { axis: 'z', across: [10, 18], along: [2, 12], eaveY: 5, steep: false }, // ridge y 9 at x 14
    ties: [5, 9], // collar ties across the room at y 4 (lamps hang from them)
  },
  stack: { x: 14, z: 7, y0: 2, y1: 14 },
  spoil: { x0: 0, x1: 8, z0: 12, z1: 13 },
}

export const ROOMS = [
  { name: 'Hoist House', use: 'the Miner\'s shed: the blast furnace under its stack, the drop-off chests, the winch', box: [12, 1, 4, 16, 3, 10] },
]

export const OPENINGS = {
  frontDoor: { at: [13, 1, 3], facing: 'south', out: 'north', step: [13, 0, 2] }, // from the z = 0 side
  pitDoor: { at: [11, 1, 7], facing: 'east', out: 'west' }, // the Miner's door to the pit, in line with the furnace
  windows: [
    { name: 'front', cells: [[15, 2, 3], [15, 3, 3]], out: 'north' },
    { name: 'pit north', cells: [[11, 2, 5], [11, 3, 5]], out: 'west' },
    { name: 'pit south', cells: [[11, 2, 9], [11, 3, 9]], out: 'west' },
    { name: 'east north', cells: [[17, 2, 5], [17, 3, 5]], out: 'east' },
    { name: 'east south', cells: [[17, 2, 9], [17, 3, 9]], out: 'east' },
    { name: 'back', cells: [[14, 2, 11], [14, 3, 11]], out: 'south' },
  ],
  gableWindows: [[14, 6, 3], [14, 6, 11]],
}

// Furniture: chests and the furnace are fixtures (no spot); the lamps and the foreman's side table are spots.
export const CHESTS = [
  // the drop-off by the furnace, its back to the east wall (furniture.py: a chest stands against a wall; at x 14 it stood
  // loose in the room); the test ground keeps its own chest at (14, 1, 5)
  { cells: [[16, 4], [16, 5]], facing: 'west' },
  { cells: [[12, 9], [12, 10]], facing: 'east' }, // stores: pickaxes, torches, ladders (against the pit wall)
]
export const WINCH = { x0: 14, x1: 16, y: 1, z: 10, crank: [13, 1, 10] } // the hoist drum in the back corner, its crank
// post at the west end (a lever on it); its rope runs up to the Sheave
export const FURNITURE = [
  { room: 'Hoist House', kind: 'lamp', part: 'hangingLamp', pos: [15, 3, 5], facing: 'west', wood: 'spruce' },
  { room: 'Hoist House', kind: 'lamp', part: 'hangingLamp', pos: [13, 3, 9], facing: 'west', wood: 'spruce' },
  { room: 'Hoist House', kind: 'counter', part: 'counter', pos: [12, 1, 4], facing: 'east', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' }, // the assay bench by the door
  { room: 'Hoist House', kind: 'lamp', part: 'tableLamp', pos: [16, 1, 7], facing: 'west', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
]

// The walking line: the front (z 0) -> the grass -> the step (13, 0, 2) -> the front door (13, 1, 3)
// -> along x 13 (z 4-6), the drop-off chest at hand to the east -> (13, 1, 7), the cell west of the furnace where the Miner stands
// (JobScenes spawns there) -> (12, 1, 7) -> the pit door (11, 1, 7) -> the plinth course (10, 0, 7) -> the lip slab in
// the Gap (9, 0, 7) -> the pit's top, edge cell (8, -1, 7), the cell nearest the furnace, where the stairs start.
// Every step is level or one down; nothing over 1 high, no gate. The stores chest is reached from (13, 1, 9).
export const WALK = [[13, 1, 0], [13, 1, 3], [13, 1, 5], [13, 1, 7], [12, 1, 7], [11, 1, 7], [10, 1, 7], [9, 1, 7], [8, 0, 7]]
