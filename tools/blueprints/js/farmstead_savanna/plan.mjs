// Farmstead, the SAVANNA look (looks/savanna/farmstead[_2, _3], ROADMAP 49.10e, as 49.10a): the same building as the
// shipped as-drawn Farmstead (../farmstead/plan.mjs: the hall with its hearth, table, bed and harvest chest, the field
// walk on the east side with both composters and their chests, the fenced field round its channel, the cross passage,
// the barn with the second Farmer's chamber, its ladder, loft and wagon doors, the root cellar under the hall; every
// bed, chest, composter, door, ladder and furniture spot in the same cell and facing at every level), drawn again for
// a hot grassland with a rainy season. It speaks the savanna village's language the Cottage, the Stone House, the
// Weaver's Cottage, the Daub House and the Manor set (golden thatch hips with eaves on every side and no gable anywhere,
// grey acacia posts and beam rings, packed mud on a terracotta plinth, painted trims, a veranda for shade, tall mud
// stacks, a clay pot on the peak, red granite below ground), and it is the village's FARMER's homestead.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Rondavels round a kraal": the hall, the chamber and the barn as round mud huts under conical thatch round a
//     stockade yard, the field outside it. The strongest savanna picture, but the shipped rooms are square and pinned
//     (a box bed at (6, 1, 6), the harvest chest at (6, 1, 3), a table and chairs down the middle, a barn 5 x 9 inside
//     with its ladder, loft and chamber): round huts can't hold them, and the two composters stand in a straight walk
//     along the field. It would not be the same building.
//  2. "Acacia ranch": the as-drawn catslide range with its cross gable, in acacia, terracotta and mud. Every cell as
//     drawn and safe, but it is a palette swap, and a gable and a catslide are exactly what a savanna roof is not.
//  3. "The Painted Homestead and its Grain House": Level I is the farmer's hall as a low painted mud house under ONE
//     golden thatch hip (eaves a block out on every side, a clay pot on the point, no gable), the walls in the
//     homestead way: a red dado, a white-limed face round the door and windows, a red-and-white band under a grey
//     acacia top plate; its east eave is the FIELD VERANDA, where the composter and the harvest chest stand a step from
//     the rows; a tall mud stack stands up the west wall past the ridge. Level II raises the barn behind it as a TALL
//     MUD GRAIN HOUSE, two storeys of mud ringed by acacia beams under a HIGHER hip (its ridge three blocks over the
//     hall's) with the hay door and a hoist in its back wall; the veranda's thatch runs on unbroken along its east side
//     at the hall's eave height to the second composter, a lean-to woodstore shelters its west wall. Level III digs a
//     red granite root cellar under the hall. Story: a farming family whose first house was one thatch over a hearth
//     and a bed; a good harvest bought the grain house, taller than the house, because grain is what the farm lives on.
//  Built: 3. It is the strongest, not the safest: from 20 blocks it is a squat golden pyramid with a tall stack beside
//  it, and at II a second, taller pyramid stepping up behind it, one eave line running along the field under both, a
//  silhouette no other farmstead has and nothing like the desert's flat roofs; the veranda is the climate's shade and
//  the job's place at once; the painted face makes the hall a home and not a shed. Not the safest: two hips one behind
//  the other with the barn's eave over the hall's back slope, a 1-wide veranda under the eave (the field is pinned
//  next to it), and a barn eight blocks of wall high.
//
// --- Design notes: what is done, what is left ------------------------------------------------------------------------
//  Done (2026-11-21): Levels I, II and III as candidates (build.sh -> build/rebuild/farmstead_savanna/), never live. The
//  shipped sizes (15 x 13 x 12, 15 x 15 x 22, III the same plus the 4-deep cellar); every shipped furniture spot drawn
//  from ../farmstead/plan.mjs's list (kinds, cells and facings as shipped, in acacia; the lamps on their shipped chain
//  of 0: the hall's from tie beams at y 5, the barn's from tie beams and the loft floor, the cellar's from the hall's
//  floor); composters (8, 1, 4) and (8, 1, 13), chests (6, 1, 3), (8, 1, 6), (8, 1, 19), (3, 1, 18) and the cellar's
//  three, both beds, the ten spruce doors, the loft ladder and the cellar's ladder and hatch in their cells. The pinned
//  field (farmland x 9..13, z 2..10 at y 0, a channel down x 11, the scarecrow on a terracotta footing at z 6); II's
//  second field z 12..20 (74 farmland in all, every one in reach of water).
//  Checked: look_spots.py farmstead_savanna farmstead: SAME BUILDING (11, 17 and 19 shipped spots, none missing; no bed,
//  container, job block, door or ladder moved; no lamp on another chain); rebuild.py no CHECK line at any level;
//  holes.py 0 (with open) at all three; every id vanilla 1.21.1; II keeps 96.7 % of I's blocks in their cells and III is
//  II above ground but for the hatch at (2, 0, 2); 1 composter and 1 bed in I, 2 composters in II; about 37, 52 and 56
//  stacks (limit 81); I places 518 blocks (at least 206 wanted); no barrel, furnace, crafting table or other job block.
//  Renders: build/rebuild/farmstead_savanna/renders/ (savanna_<L>_<front|back|left|right|top>.png for L 1..3;
//  cutaways savanna_1_cut3, savanna_2_cut3, savanna_2_cut7 and savanna_3_cut-2 (the cellar), each _top and _front).
//  Tried and changed: the chamber's rug ran under its doorway (the 1.95 clearance check: one cell now); the loft's hay
//  stood two high and made a floor nobody could climb to (one high now); the woodstore first ran the whole west wall
//  and hid the grain house's lower lights (now x 0, z 13..17, between them); the grain house's back was one flat mud
//  face over the wagon doors (a thatch hood on brackets over them now, and a light over the chamber bed's head); the
//  hall's front eave got brackets either side of the door.
//  Open for the critic: the painted band at y 4 sits in the hall eave's shadow (the limed front and red dado carry the
//  paint from the street); the veranda is one block deep (the field is pinned beside it), the same as the as-drawn
//  gallery; the clay pots on the points render as plain red blocks in Lodestone (decorated pots in the game); the
//  thatch is the village's bamboo mosaic, as on every savanna look.
//
// --- Named parts (template coordinates: x across the front, west 0 -> east 14; y up, 0 the layer on the clicked block;
//     z from the front, z = 0, to the back) ---------------------------------------------------------------------------
//   Hall        mud walls x 1..7, z 1..8 on a terracotta plinth: y 1..4 mud, a red-and-white painted band at y 4, a grey
//               acacia top plate at y 5 (the eave's height); acacia posts at the four corners; the front face limed
//               white over a red dado with red door and window heads. Tie beams across the room at y 5 (z 4 and 7) for
//               the two hung lamps.
//   Hip         the hall's thatch (bamboo mosaic): x 0..8, z 0..9, eaves y 5, the point a short ridge (x 4, z 4..5) at
//               y 9, a clay pot on it.
//   Stack       the hearth's stack outside the west wall (x 0, z 5) in mud brick, a stepped breast z 4..6, up to y 10
//               with its fire on top; the hearth in the wall at (1, 1, 5).
//   Veranda     the field walk: a mud-brick deck along x 8 (z 1..9 in I, 1..20 in II) under the hip's east eave and, in
//               II, an eave of the same thatch at the same height (y 5) along the grain house; acacia posts off the
//               walking line (z 8, 15, 20), brackets under the eave; the composters and harvest chests stand on it.
//   Stoep       a terracotta step along the front under the eave (z 0), clay pots under the windows, the door lamp.
//   Field       the pinned 9 x 5 of farmland (x 9..13, z 2..10) round a channel down x 11, the scarecrow in a straw hat
//               on a footing in the channel; acacia rails on a terracotta kerb, lanterns on the corner posts. II's second
//               field behind it (z 12..20).
//   Passage     (II) the hall's back stoep (z 9) walled at both ends with a door in each, under the hall's back eave.
//   Grain house (II) mud walls x 1..7, z 10..20, y 1..8: a beam ring at the loft (y 5) and a top plate (y 8), posts at
//               the corners and mid-sides, a painted band at y 7, small high lights; its hip x 0..8, z 9..21 from eaves
//               at y 8 to a ridge at y 12 (z 13..17), a clay pot on it. Inside: the byre and grain jars at the front,
//               the second Farmer's chamber under the loft at the back, the loft on its ladder, the wagon doors and the
//               hay door with a hoist beam and chain over it in the back wall.
//   Woodstore   (II) a thatch lean-to along the grain house's west wall (x 0, z 13..17, y 3) over stacked acacia logs.
//   Cellar      (III) a root cellar under the hall x 1..7, z 1..8, 4 deep, in red granite, down a ladder under a hatch
//               inside the door: the three kept chests on the west wall, grain jars, pumpkins and melons, hay.
import { FURNITURE as SHIPPED_FURNITURE } from '../farmstead/plan.mjs'

export const SIZE = { 1: [15, 13, 12], 2: [15, 15, 22], 3: [15, 15, 22] } // as shipped (w, h, d)
export const BASEMENT = { 3: 4 }
export const IDS = { 1: 'farmstead', 2: 'farmstead_2', 3: 'farmstead_3' }

// Palette (60/30/10): packed mud walls and golden thatch 60/30; grey acacia frame, terracotta plinth and kerb, the lime
// and red paint 10; the fields' greens and the scarecrow's orange the accents. Below ground red granite.
export const PALETTE = {
  mud: [['packed_mud', 7], ['mud_bricks', 1]],
  plinth: [['terracotta', 5], ['red_terracotta', 2], ['brown_terracotta', 1]],
  kerb: [['terracotta', 4], ['brown_terracotta', 1]],
  frame: 'acacia_log',
  roof: { full: 'bamboo_mosaic', stairs: 'bamboo_mosaic_stairs', slab: 'bamboo_mosaic_slab' },
  lime: 'white_terracotta', red: 'red_terracotta', paint: ['red_terracotta', 'white_terracotta'],
  stack: 'mud_bricks', stackStair: 'mud_brick_stairs', stackCap: 'terracotta',
  floor: 'mud_bricks', deck: 'mud_bricks', step: 'mud_brick_stairs', stoep: 'terracotta',
  barnFloor: 'packed_mud', loftFloor: 'acacia_planks', partition: 'stripped_acacia_log',
  door: 'spruce', // the shipped doors, cell for cell and block for block (look_spots.py)
  wood: 'acacia', // furniture, shutters, fences, gates, the loft rail
  glass: 'glass_pane', finial: 'decorated_pot',
  cellarWall: [['granite', 5], ['polished_granite', 1], ['terracotta', 1]], cellarFloor: 'polished_granite',
}

export const HALL = { x0: 1, x1: 7, z0: 1, z1: 8, beamY: 5, paintY: 4, tieBeams: [4, 7], room: { x0: 2, x1: 6, z0: 2, z1: 7 } }
export const HIP = { x0: 0, z0: 0, x1: 8, z1: 9, eaveY: 5, finial: [4, 10, 4] }
export const STACK = { x: 0, z: 5, top: 10, breastTop: 4, hearth: [1, 1, 5] }
export const VERANDA = { x: 8, z0: 1, z1: { 1: 9, 2: 20 }, y: 5, posts: { 1: [8], 2: [8, 15, 20] }, brackets: { 1: [3, 7], 2: [3, 7, 11, 18] } }
export const STOEP = { x0: 1, x1: 7, z: 0, pots: [[2, 1, 0], [6, 1, 0]], lamp: [4, 4, 0], brackets: [3, 5] } // brackets under the eave either side of the door
export const FIELD = { x0: 9, x1: 13, z0: 2, z1: 10, channelX: 11, scarecrowZ: 6 }
export const FIELD2 = { x0: 9, x1: 13, z0: 12, z1: 20, channelX: 11, water: [13, 19] }
export const PASSAGE = { z: 9, lamp: [4, 4, 9] }
export const BARN = {
  x0: 1, x1: 7, z0: 10, z1: 20, wallTop: 8, ringY: 5, paintY: 7, midPosts: [15],
  room: { x0: 2, x1: 6, z0: 11, z1: 19 }, tieBeams: [12, 14],
  loft: { y: 5, z0: 16, z1: 19 },
  chamber: { wallZ: 16, doorX: 3, wallX: 4, z0: 17, z1: 19 },
}
export const BARN_HIP = { x0: 0, z0: 9, x1: 8, z1: 21, eaveY: 8, finial: [4, 13, 15] }
export const WAGON_HOOD = { x0: 4, x1: 7, z: 21, y: 4 } // (II) a thatch hood over the wagon doors
export const WOODSTORE = { x: 0, z0: 13, z1: 17, y: 3 } // between the west lights
export const CELLAR = { x0: 1, z0: 1, x1: 7, z1: 8, depth: 4, ladder: { x: 2, z: 2, facing: 'south' } }

// The shipped openings that hold the building together, cell for cell (doors, steps, the ladder, the hatch); the
// windows are this look's own.
export const OPENINGS = {
  frontDoor: { at: [4, 1, 1], facing: 'south', out: 'north', step: [4, 0, 0] },
  backDoor: { at: [4, 1, 8], facing: 'north', out: 'south' },
  barnDoor: { level: 2, at: [4, 1, 10], facing: 'south', out: 'north' },
  barnSideDoor: { level: 2, at: [7, 1, 17], facing: 'west', out: 'east' },
  passageDoors: { level: 2, at: [[1, 1, 9, 'east', 'west'], [7, 1, 9, 'west', 'east']] },
  chamberDoor: { level: 2, at: [3, 1, 16], facing: 'south', out: 'north' },
  wagonDoors: { level: 2, at: [[5, 1, 20], [6, 1, 20]], facing: 'north', out: 'south' },
  hayDoor: { level: 2, at: [4, 6, 20], facing: 'north', out: 'south' },
  galleryStep: [8, 0, 0],
  passageStep: { level: 2, at: [0, 0, 9] },
  ladder: { level: 2, x: 2, z: 15, y0: 1, y1: 5, facing: 'east' },
  hatch: { level: 3, at: [2, 0, 2] },
  windows: [ // cells, out, dress
    { name: 'hall front west', cells: [[2, 2, 1], [2, 3, 1]], out: 'north', dress: null },
    { name: 'hall front east', cells: [[6, 2, 1], [6, 3, 1]], out: 'north', dress: null },
    { name: 'hall west', cells: [[1, 2, 2], [1, 3, 2]], out: 'west', dress: 'shutters' },
    { name: 'hall west back', cells: [[1, 2, 7], [1, 3, 7]], out: 'west', dress: 'shutters' },
    { name: 'hall east', cells: [[7, 2, 3], [7, 3, 3]], out: 'east', dress: null }, // onto the veranda and the field
    { name: 'hall east back', cells: [[7, 2, 6], [7, 3, 6]], out: 'east', dress: null },
    { name: 'hall back', cells: [[2, 2, 8], [2, 3, 8]], out: 'south', dress: 'shutters', until: 1 },
    { name: 'hall back plain', level: 2, cells: [[2, 2, 8], [2, 3, 8]], out: 'south', dress: null },
    { name: 'hall back east', cells: [[6, 2, 8], [6, 3, 8]], out: 'south', dress: 'shutters', until: 1 },
    { name: 'hall back east plain', level: 2, cells: [[6, 2, 8], [6, 3, 8]], out: 'south', dress: null },
    { name: 'barn west', level: 2, cells: [[1, 2, 12], [1, 3, 12]], out: 'west', dress: null },
    { name: 'barn west back', level: 2, cells: [[1, 2, 18], [1, 3, 18]], out: 'west', dress: null },
    { name: 'barn east', level: 2, cells: [[7, 2, 12], [7, 3, 12]], out: 'east', dress: null },
    { name: 'barn east back', level: 2, cells: [[7, 2, 19], [7, 3, 19]], out: 'east', dress: null },
    // the grain house's high lights, one in each bay of the upper storey
    { name: 'barn high west', level: 2, cells: [[1, 6, 12]], out: 'west', dress: null },
    { name: 'barn high west back', level: 2, cells: [[1, 6, 18]], out: 'west', dress: null },
    { name: 'barn high east', level: 2, cells: [[7, 6, 12]], out: 'east', dress: null },
    { name: 'barn high east back', level: 2, cells: [[7, 6, 18]], out: 'east', dress: null },
    { name: 'barn high front', level: 2, cells: [[4, 7, 10]], out: 'north', dress: null },
    { name: 'chamber back', level: 2, cells: [[2, 2, 20], [2, 3, 20]], out: 'south', dress: null }, // over the bed's head
  ],
}

// Every furniture spot of the shipped Farmstead, cell for cell (kind, pos, facing), drawn in acacia: the shipped hearth
// fixture is this look's own (a mud hearth in the west wall, drawn in the recipe), the shipped rugs too.
export const FURNITURE = SHIPPED_FURNITURE
  .filter(f => f.part !== 'hearth' && f.part !== 'rug')
  .map(f => (f.wood ? { ...f, wood: 'acacia' } : f))
  .concat([
    { room: 'Hall', level: 1, kind: 'rug', part: 'rug', cellsAbs: [[4, 1, 3], [5, 1, 3], [4, 1, 4], [5, 1, 4], [4, 1, 5], [5, 1, 5]], colour: 'orange' },
    { room: 'Barn', level: 2, kind: 'rug', part: 'rug', cellsAbs: [[2, 1, 17]], colour: 'red' },
  ])

// The walking lines (as shipped). The first Farmer from (1, 0, -2): along the front, up the veranda's step, to his
// composter. The second (II, from (4, 0, -2)): in at the front door, through the hall, out the back door, across the
// passage to the veranda, south to the second composter. III: from the front door to the hatch and down.
export const WALK = {
  first: [[1, 0, -1], [8, 0, -1], [8, 1, 0], [8, 1, 1], [8, 1, 2], [8, 1, 3]],
  second: [[4, 0, -1], [4, 0, 0], [4, 1, 1], [4, 1, 2], [4, 1, 7], [4, 1, 8], [4, 1, 9], [8, 1, 9], [8, 1, 12]],
  cellar: [[4, 1, 2], [3, 1, 2], [2, 0, 2], [2, -3, 2], [3, -3, 3], [3, -3, 6]],
}

// Crops (rows by x; ages cycle down z): millet-gold wheat, carrots, potatoes; II's second field the same crops.
export const CROPS = {
  1: { 9: ['carrots', [3, 5, 4, 6]], 10: ['wheat', [5, 6, 4, 7]], 12: ['wheat', [6, 4, 7, 5]], 13: ['potatoes', [4, 6, 3, 5]] },
  2: { 9: ['wheat', [4, 6, 5, 7]], 10: ['wheat', [6, 5, 7, 4]], 11: ['carrots', [4, 5]], 12: ['potatoes', [5, 3, 6, 4]], 13: ['carrots', [3, 6, 4, 5]] },
}
export const OUTSIDE = {
  fieldLamps: { 1: [[14, 1], [14, 11]], 2: [[14, 21], [9, 21]] },
  // on the yard's ground (y 0, as the field and the stack's foot), not a block up in the air
  woodpile: { 1: [[0, 0, 7], [0, 0, 8], [0, 1, 7]] },
  jars: { 1: [[0, 0, 9]], 2: [] },
}
