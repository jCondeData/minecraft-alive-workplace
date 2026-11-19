// The Great Wall (Wonder, ROADMAP 35.15 / rebuild 52.6), the plan: drawn before any wall. recipe.mjs assembles it from
// the parts library (../parts) in passes, every stage at once: each block remembers the stage that lays it, and draw(n)
// is the whole Wonder as it stands once stage n is done (stages only add; verify() refuses one that changes another).
//
// Three rough concepts, before choosing:
//   A. "The Pass Gate" (a frontier pass like the wall's great gates). Massing: one long battered curtain, low and
//      level, broken by four square watchtowers and a deep gatehouse in the middle that carries a two-storey timber
//      gate pavilion and pushes a walled barbican court out into the field behind. Roof: hipped spruce caps on the
//      towers; on the gate a pavilion with a flared lower eave and a flared upper hip, the one tall thing on the line.
//      Plan: village side (front) plain, with two broad horse stairs climbing the face either side of the gate; field
//      side crenellated, the gate passage running through the gatehouse and on through the barbican's own gate.
//      Story: the town shut the road with a gate first, then ran the wall out to the hills both ways, then walked it.
//   B. "The Dragon's Back". Massing: the curtain rises and falls in steps between the towers like the wall on a ridge.
//      Roof: crenels all the way, small stone caps. Plan: steps up the walk. Story: a wall following the land. (The tests
//      pin the runs at 12 high and the four posts level on one line, so the stepping would live only between them:
//      a picture fighting its own rules.)
//   C. "The Bulwark". Massing: a thick European curtain with round-faced bastions and a twin-towered gate. Roof:
//      none, all battlements. Plan: casemates in the wall. Story: a castle's outer ward. (Generic; the towers must be
//      9 square with spruce-stair roofs anyway.)
// Strongest: A. It is the only one that reads "Great Wall" and not "castle wall" from 30 blocks: one long horizontal
// line with a battered dark foot, pierced by square towers wearing timber pavilion roofs, and in the middle one hero
// gate pavilion (25 high, the towers 21) on a mass deep enough to stand in front of and behind the line. It gives the
// four stages a real build order (the gate that shuts the road, the west run, the east run, then the walk, the
// battlements and the towers' crowns), puts the "two stairs on the village side by the gate" the guide already names,
// and the barbican gives the Great Gate its second, outer line of gates without a single gate outside x 46..50.
//
// Coordinates: x along the wall (west 0 -> east 96), y up (0 = the layer on the clicked block), z from the village
// (z = 0, the front) out into the field (the back). The gate's middle is x 48.
//
// Masses (named parts the owner's feedback can point at):
//   Great Gate    the gatehouse x 39..57, z 3..14, solid to y 9 but for its passage; its Horse Stairs on the front
//                 (z 0..2), landing on the walk at x 34..35 and 61..62; the Barbican, a walled court behind it
//                 (x 39..57, z 15..25) with the outer gate; the Gate Pavilion on top (x 41..55, z 4..12, lower hall
//                 y 10..14, a flared pentice at y 15..16, the upper room x 42..54 z 5..11 y 16..19, a flared hip to y 24).
//   Curtain       the runs x 0..1 (a ragged end: the wall goes on), 11..19, 29..38 | 58..67, 77..85, 95..96: a dark
//                 deepslate foot (y 0..2, 11 thick) battered in to 9 then 7 (z 4..10 from y 5), the walk on top at y 9.
//   Towers        four watchtowers at x 2, 20, 68, 86 (each x t..t+8, z 3..11), solid to y 8, a guard room y 10..12
//                 the walk runs through, and the Crown: a lookout y 14..15 on stone piers, a breast wall and propped spruce shutters, a stone cornice at
//                 y 16 and a spruce hip cap y 16..20 with a lightning rod; the signal fire in the middle.
//   Round 2 (critic BACK): buttresses (field) and pilasters (village) on the runs at x 13, 17, 31, 35, 61, 65, 79, 83
//                 (no pilaster at the landings 35, 61), a drip course at y 7 on both faces, the field parapet carried
//                 out to z 11 on corbels; lantern posts on the walk, both flights, the landings, the passage and the
//                 barbican yard; three-light windows and tall loops on sills in every guard room; the gate hall
//                 furnished (table with chairs and benches, windlass, stores, anvil); the broken ends lose their pale
//                 stair step and the end towers' drip course runs round their open side.
//   Walk          floor y 9 between a plain inner parapet (z 4, y 10) and the field parapet (z 11 on corbels, y 10) with merlons
//                 (y 11) on every other cell; spouts under the village edge.
export const SIZE = [97, 26, 27] // w, h, d
export const STAGES = { gate: 1, west: 2, east: 3, walk: 4 }
export const GATE_X = 48

// Palette (60/30/10): grey stone bricks, aged (mossy, cracked, andesite) for the curtain and towers / dark deepslate
// for the foot, quoins and the gate's dressings, spruce for every roof and the pavilion's frame / red and blue banners,
// fire and lanterns. Why: the wall reads as one long grey line on a dark footing, the timber roofs mark the towers and
// the gate from far off, and the colour is kept for the few things that are about people (the gate, the posts).
export const PALETTE = {
  foot: [['deepslate_bricks', 7], ['cracked_deepslate_bricks', 3], ['deepslate_tiles', 1]],
  footStairs: 'deepslate_brick_stairs',
  body: [['stone_bricks', 11], ['mossy_stone_bricks', 3], ['cracked_stone_bricks', 3], ['andesite', 1]],
  bodyStairs: 'stone_brick_stairs',
  ragged: [['mossy_cobblestone', 4], ['mossy_stone_bricks', 3], ['cracked_stone_bricks', 2], ['cobblestone', 1]],
  quoin: 'polished_deepslate',
  walk: [['polished_andesite', 3], ['stone_bricks', 2], ['andesite', 1]],
  cornice: 'stone_bricks',
  pier: 'deepslate_bricks',
  key: 'chiseled_stone_bricks',
  court: [['gravel', 2], ['cobblestone', 2], ['andesite', 1]],
  frame: 'spruce_log',
  beam: 'stripped_spruce_log',
  boards: 'spruce_planks',
  roof: { stairs: 'spruce_stairs', full: 'spruce_planks', slab: 'spruce_slab' },
  // the Gate Pavilion's two roofs: dark tile over the timber hall, so the hero roof reads apart from its own walls and
  // the towers' timber caps, and ties back to the dark foot (polish pass, 52.6)
  tile: { stairs: 'deepslate_tile_stairs', full: 'deepslate_tiles', slab: 'deepslate_tile_slab' },
  rail: 'spruce_fence',
  shutter: 'spruce_trapdoor',
  innerGate: 'spruce_fence_gate',
  outerGate: 'dark_oak_fence_gate',
  bars: 'iron_bars',
  door: 'spruce_door',
  glass: 'glass_pane',
  gateBanner: 'red_wall_banner',
  towerBanner: 'blue_wall_banner',
  pavilionBanner: 'white_wall_banner',
}

export const TOWERS = [2, 20, 68, 86] // each x t..t+8, z 3..11 (WonderBlueprintGameTests.WALL_TOWERS)
export const TOWER = { z0: 3, z1: 11, solidTop: 8, floorY: 9, roomTop: 12, crownFloor: 13, crownTop: 15, cornice: 16, capY: 16 }
export const RUNS = [[0, 1], [11, 19], [29, 38], [58, 67], [77, 85], [95, 96]]
export const CURTAIN = { walkY: 9, inner: 4, outer: 10, merlonY: 11 }
export const GATEHOUSE = { x0: 39, x1: 57, z0: 3, z1: 14, top: 9 }
export const PASSAGE = { x0: 46, x1: 50, y0: 1, h: 4 } // through the gatehouse and the barbican's back wall
export const BARBICAN = { x0: 39, x1: 57, z0: 15, z1: 25, wall: 3, top: 6 }
export const PAVILION = { lower: { x0: 41, x1: 55, z0: 4, z1: 12, y0: 10, y1: 14 }, upper: { x0: 42, x1: 54, z0: 5, z1: 11, y0: 16, y1: 19 }, hipY: 20 }
export const STAIRS = { west: { x0: 45, rise: 'west' }, east: { x0: 51, rise: 'east' }, z0: 0, width: 3, steps: 10 }

// Sentry posts (GreatWallSentries): on the walk, level (golems stand at y 10 on the y 9 floor), on one line (z 7, the
// walk's middle, two clear cells either side), listed west to east. Each walk spot is 2 along the wall toward the
// posts' middle (x 48): 17, 36, 60, 79, all on open walk.
export const POSTS = [[15, 10, 7], [34, 10, 7], [62, 10, 7], [81, 10, 7]]

export const OPENINGS = {
  gatePassage: 'x 46..50, y 1..3 (4 under the middle three), z 3..14; the Great Gate, two leaves (spruce gates two high, x 46..50) at z 4 and z 13 under the portcullis grille (iron bars, y 3..4)',
  barbicanGate: 'x 46..50 through the back wall z 23..25; the outer gate (dark oak, two high) at z 24 under its grille',
  towerDoors: 'x t and x t+8 (inner sides only), z 7, y 10..11, a spruce door under a chiseled lintel: the walk runs through each guard room',
  pavilionDoors: 'x 41 and x 55, z 7, spruce doors: the walk runs through the lower hall',
  loops: 'iron-barred loops in each tower\'s field face (y 11) and glazed windows on its village face',
}

export const ROOMS = [
  { name: 'Gate passage', use: 'the road through; the Great Gate (five spruce gates) and the outer gate (five dark oak)', box: [46, 1, 3, 50, 4, 25] },
  { name: 'Barbican court', use: 'the killing ground between the gates', box: [42, 1, 15, 54, 6, 22] },
  { name: 'Gate hall', use: 'the pavilion\'s lower hall: the captain\'s table, the walk passing through', box: [42, 10, 5, 54, 14, 11] },
  { name: 'Lantern room', use: 'the upper room: windows all round over both fields', box: [43, 16, 6, 53, 19, 10] },
  ...TOWERS.map((t, i) => ({ name: `Guard room ${i + 1}`, use: 'a grindstone, a barrel, a table and a chair by the loops, the walk through, a ladder to the crown', box: [t + 1, 10, 4, t + 7, 12, 10] })),
  ...TOWERS.map((t, i) => ({ name: `Crown ${i + 1}`, use: 'the lookout and its signal fire', box: [t + 1, 14, 4, t + 7, 15, 10] })),
]

// Every furniture footprint (none is read by a job; recorded so the plan holds the rooms' contents).
export const FURNITURE = [
  ...TOWERS.map(t => ({ kind: 'grindstone', cells: [[t + 2, 10, 4]], facing: 'south' })),
  ...TOWERS.map(t => ({ kind: 'ladder', cells: [[t + 6, 10, 4], [t + 6, 13, 4]], facing: 'south' })),
  ...TOWERS.map(t => ({ kind: 'campfire', cells: [[t + 4, 14, 7]], facing: 'south' })),
  ...TOWERS.map(t => ({ kind: 'lantern', cells: [[t + 4, 12, 9]], hanging: true })),
  ...TOWERS.map(t => ({ kind: 'barrel', cells: [[t + 1, 10, 10]], facing: 'up' })),
  ...TOWERS.map(t => ({ kind: 'table', cells: [[t + 6, 10, 10], [t + 7, 10, 10]] })),
  ...TOWERS.map(t => ({ kind: 'chair', cells: [[t + 6, 10, 9]], facing: 'north' })),
  ...TOWERS.map(t => ({ kind: 'candle', cells: [[t + 7, 10, 10]] })),
  { kind: 'table', cells: [[46, 10, 10], [47, 10, 10], [48, 10, 10], [49, 10, 10], [50, 10, 10]], facing: 'north' },
  { kind: 'bench', cells: [[46, 10, 11], [47, 10, 11], [48, 10, 11], [49, 10, 11], [50, 10, 11]], facing: 'south' },
  { kind: 'ladder', cells: [[54, 10, 11], [54, 15, 11]], facing: 'west' },
  { kind: 'lantern', cells: [[45, 14, 8], [51, 14, 8], [48, 4, 6], [48, 4, 12]], hanging: true },
]

// The walking line: village road -> apron (x 46..50, z 0..2) -> the passage under the pavilion, through the Great
// Gate's two leaves (z 4, z 13) -> the barbican court -> the outer gate (z 24) -> the field. Up: either horse stair (from x 45 / 51 at
// the gate's cheeks) -> landing (x 34..35 / 61..62, y 9) -> the walk (z 5..9) -> west through the guard rooms of the
// towers at 20 and 2, east through 68 and 86; inward through the pavilion's side doors into the gate hall, the ladder
// to the lantern room; in each guard room the ladder up to the crown.
