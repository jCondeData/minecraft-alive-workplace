// Noble's Manor, the SAVANNA look (looks/savanna/manor[_2.._4], ROADMAP 49.10f, as 49.10a): the same building as the
// shipped Manor (../manor/plan.mjs: the west wing, the hall with the Long Gallery over it, the porch tower, the garden at
// II, the ballroom wing at III, the 4-deep cellar at IV; every bed, door, kept container, the grand stair, the hatch and
// every furniture spot in the same cell), drawn again for a hot grassland with a rainy season. It speaks the savanna
// village's language the Cottage, the Stone House and the Terrace set (golden thatch hips with eaves on every side and
// no gable anywhere, grey acacia posts and beams, packed mud over a red granite storey on a terracotta plinth, painted
// trims, verandas for shade), raised to a chief's great house.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The royal compound": the wing, the hall and the ballroom as separate round houses under conical thatch inside a
//     ring wall, joined by covered ways. The strongest savanna picture, but the shipped house is one building: its hall,
//     wing and tower share walls and its rooms are square, so round houses can't hold its cells.
//  2. "A hip per mass": the shipped massing in mud and thatch, each wing under its own small hip, the hall under a
//     third. Every cell as shipped, but three hips side by side meet in valleys at every junction and read as three huts
//     pushed together, not one noble house.
//  3. "The Great House under One Thatch": ONE broad golden hip over the whole house, eaves a block out on every side,
//     its outline following the plan (the wing's front and back, the hall's long ridge), the valleys only where the hall
//     meets the wing; under the eaves a colonnade of grey acacia posts all round, so every face reads as a veranda; the
//     porch tower rising through the thatch under its own steep pyramid with a clay pot on its point (the hero); low
//     thatch verandas on acacia posts in the two front bays either side of the tower; a painted band under the eaves.
//     The garden (II) is a walled yard round the family's acacia, its walls thatched; the ballroom (III) is drawn into the
//     same hip, which grows into a T and is as tall over the dance floor as over the hall; the cellar (IV) under it.
//     Story: the chief's household roofs everything it owns with one thatch, and only the tower of the family's
//     authority stands above it.
//  Built: 3. It is the strongest: from 20 blocks it is one great golden roof with a tower through it, a silhouette no
//  other house has; the post colonnade gives every face its depth and its shade; and its growth reads (II walls in the
//  yard, III makes the roof a T). Not the safest: one roof over an L (and a T) has valleys, the tower must stand out of
//  a slope cleanly, and the roof's underside meets three wall heights.
//
// --- Design notes: what is done, what is left (2026-11-19) -----------------------------------------------------------
//  Done: Levels I-IV as candidates (build.sh -> build/rebuild/manor_savanna/). The rooms come from the shipped plan
//  through the parts library's manorRooms (noble_house.mjs): floors, partitions, the arch, the grand stair, every door,
//  the hearths, the ballroom's floor and gallery, the cellar and hatch, every furniture piece. The roof is the parts
//  library's unionHip (noble_house.mjs): one 45-degree hip over the union of the masses' rectangles.
//  Lamps hang on the same cells; their chains reach this look's ceilings (the ballroom's open thatch).
//  Checked: look_spots.py manor_savanna manor: SAME BUILDING (100, 100, 123, 125 shipped spots, none missing; no bed,
//  container, door or ladder moved; only the musicians' lamp's chain differs: the thatch is lower over it); holes.py 0
//  (with open) at every level, the union hip's valleys included; rebuild.py no CHECK line.
//  Renders: build/rebuild/mn_r1/shots/savanna_<L>_<view>.png, cutaways savanna_4_cut<y>_20_65.png (y -1, 4, 9).
//  Tried and changed: the verandas first read as rooms to holes.py's open check (roofed four deep by the wing's eave
//  and the tower's side eaves): the tower's pyramid has no lip over the bays' sides now, and the hall's windows on
//  the verandas are glazed a block out between acacia jamb posts (a deep reveal inside); veranda lamps hang from the
//  pent's bottom-slab eave (a top slab doesn't hold one).
//  Open for the critic: the bake (kit finish) seals two mud cells over the west veranda's pent where the wing's eave
//  meets it; the ballroom is open to the thatch (no ceiling), as the shipped one is to its roof.
//
// --- Named parts (template coordinates: x across the front, west 0 -> east; z from the front, z = 0, to the back) ---
//   West wing   walls x 1..7, z 1..15: red granite y 1..4 on a terracotta plinth, a grey acacia beam ring at the chamber
//               floor (y 5), packed mud y 6..8 with a painted band at 8 under the eave; the parlour's BAY out at z 0
//               under a thatch hood; the kitchen stack up the west side (x 0, z 11).
//   Hall        walls x 7..19, z 5..13 as the wing; the Long Gallery under a flat ceiling at 11; the hall stack on the back
//               wall (x 10, z 14); a VERANDA in each front bay (x 8..10 and 16..18, z 2..4) on acacia posts.
//   Roof        the GREAT THATCH: bamboo-mosaic hip over the wing (x 0..8, z 0..16) and the hall (x 6..20, z 4..14), eaves
//               at y 8, the hall's ridge at 13, the wing's at 12; at III it takes in the ballroom (x 18..28, z 0..16).
//   Colonnade   grey acacia posts under the eave ring, a block out of the walls, at the corners and between the windows.
//   Tower       x 11..15, z 1..5, walls to 13 (granite, the beam ring, mud, a painted band), its own steep pyramid of
//               thatch (x 10..16, z 0..6, eaves at 13, point at 16) with a clay pot on it; a thatch hood over the door.
//   Garden (II) mud walls with a thatched coping, the family's acacia at the cross with a seat round it, four flower beds,
//               lamp posts, a bench at the end of the walk.
//   Ballroom (III) walls x 19..27, z 1..15 under the T of the great thatch, open to it inside; the great window between
//               acacia posts on the front; the east garden with a lily pond.
//   Cellar (IV) under the kitchen and the hall's west end (x 1..13, z 8..15, 4 deep), ribbed in mud brick.
import {
  SIZE, BASEMENT, MASSES as M_MASSES, OPENINGS as M_OPENINGS, FURNITURE as M_FURNITURE, BRACKET_LAMPS,
} from '../manor/plan.mjs'

export const LEVELS = [1, 2, 3, 4]
export const IDS = { 1: 'manor', 2: 'manor_2', 3: 'manor_3', 4: 'manor_4' }
export const SIZES = SIZE // as shipped: 21 x 18 x 17, 21 x 18 x 25, 29 x 18 x 25, 29 x 18 x 25
export { BASEMENT }
export const SHIPPED = { MASSES: M_MASSES, OPENINGS: M_OPENINGS, FURNITURE: M_FURNITURE, BRACKET_LAMPS }

// Palette 60/30/10: golden bamboo-mosaic thatch (60 of what you see: the roof is most of the house) / red granite ground
// storey and packed-mud upper storey (30) / grey acacia posts and beams, acacia shutters, a terracotta plinth and the
// painted band of orange and white (10). The rooms keep the shipped Manor's dark oak furniture.
export const PALETTE = {
  roof: { full: 'bamboo_mosaic', stairs: 'bamboo_mosaic_stairs', slab: 'bamboo_mosaic_slab' },
  stone: [['granite', 6], ['polished_granite', 1], ['terracotta', 1]],
  mud: 'packed_mud', mudBrick: 'mud_bricks', mudStair: 'mud_brick_stairs',
  plinth: [['terracotta', 5], ['red_terracotta', 2], ['brown_terracotta', 1]],
  quoin: 'polished_granite', post: 'acacia_log', beam: 'acacia_log', paint: ['orange_terracotta', 'white_terracotta'],
  glass: 'glass_pane', wood: 'acacia', sill: 'acacia_stairs', step: 'mud_brick_stairs', deck: 'terracotta',
  paving: 'terracotta', paving2: 'packed_mud', fence: 'acacia_fence',
  soil: 'grass_block', treeLog: 'acacia_log', treeLeaves: 'acacia_leaves',
  rooms: {
    floor: 'acacia_planks', hallFloor: ['polished_granite', 'smooth_sandstone'], ballFloor: ['acacia_planks', 'dark_oak_planks'],
    upperFloor: 'acacia_planks', partition: 'acacia_planks', wood: 'dark_oak', doorWood: 'dark_oak', frontDoorWood: 'dark_oak',
    stairUnder: 'packed_mud', beam: 'acacia_log', step: 'mud_brick_stairs', bracket: 'polished_granite',
    cellarWall: null, cellarFloor: 'mud_bricks',
  },
  cellarWall: [['granite', 4], ['mud_bricks', 2], ['terracotta', 1]],
  ceiling: 'stripped_acacia_wood', // the Long Gallery's flat ceiling at 11 and the chambers' at 10, under the thatch
}

export const MASSES = {
  wing: { x0: 1, z0: 1, x1: 7, z1: 15 },
  hall: { x0: 7, z0: 5, x1: 19, z1: 13 },
  tower: { x0: 11, z0: 1, x1: 15, z1: 5, top: 13, hip: { x0: 10, z0: 0, x1: 16, z1: 6, eaveY: 13 }, finial: [13, 17, 3] },
  ballroom: { x0: 19, z0: 1, x1: 27, z1: 15 },
  eave: 8, // the great thatch's eave course
  roof: { 1: [{ x0: 0, z0: 0, x1: 8, z1: 16 }, { x0: 6, z0: 4, x1: 20, z1: 14 }], 3: [{ x0: 18, z0: 0, x1: 28, z1: 16 }] },
  // the colonnade: acacia posts under the eave ring, a block out of the walls ([x, z] each, from y 1 to 7)
  posts: {
    1: [[0, 0], [0, 3], [0, 7], [0, 9], [0, 16], [8, 0], [8, 16], [20, 4], [20, 9], [20, 14], [16, 14], [12, 14]],
    tower: [[11, 0], [15, 0]], // up to the tower's eave (y 12)
    3: [[18, 0], [20, 0], [26, 0], [28, 0], [28, 2], [28, 6], [28, 10], [28, 14], [28, 16], [20, 16], [26, 16]],
  },
  gonePostsAt3: [[20, 4], [20, 9], [20, 14]],
  // z 2..4, one flat awning at 5, the tower's side carrying one end, an acacia post the other (clear of the wing's
  // eave over x 8: an awning under it leaves a slot the bake walls up)
  verandas: [{ x0: 9, x1: 10, post: 9 }, { x0: 16, x1: 17, post: 17 }],
  jambs: [9, 17], // acacia posts between the hall's two glazed front windows in each bay
  bay: { x0: 2, x1: 6, z: 0, glass: [3, 5], y: [1, 3], lintel: 4 },
  stacks: { kitchen: { x: 0, z: 11, top: 14, breastTop: 6 }, hall: { x: 10, z: 14, top: 15, breastTop: 7 } },
  garden: { x0: 7, x1: 19, z0: 14, z1: 24, wallTop: 3 },
  gardenEast: { x0: 19, x1: 27, z0: 16, z1: 24 },
}

// The windows: [x or z] cells on a wall line (axis 'x': a z = line wall), rows y, the side they look out of, a dressing
// ('shutters': acacia shutters; 'sill': an acacia sill with a pot; null)
export const WINDOWS = {
  1: [
    { name: 'front chamber', axis: 'x', line: 1, at: [3, 5], y: [7, 8], out: 'north', dress: 'sill' },
    { name: 'great chamber', axis: 'x', line: 15, at: [3, 5], y: [7, 8], out: 'south', dress: 'sill' },
    { name: 'kitchen back', axis: 'x', line: 15, at: [3, 5], y: [2, 3], out: 'south', dress: 'sill', pots: ['potted_red_tulip', 'potted_orange_tulip'] },
    { name: 'kitchen west', axis: 'z', line: 1, at: [13], y: [2, 3], out: 'west', dress: 'sill', pots: ['potted_acacia_sapling'] },
    { name: 'parlour west', axis: 'z', line: 1, at: [5], y: [2, 4], out: 'west', dress: 'shutters' },
    { name: 'front chamber west', axis: 'z', line: 1, at: [5], y: [7, 8], out: 'west', dress: 'sill' },
    { name: 'great chamber west', axis: 'z', line: 1, at: [13], y: [7, 8], out: 'west', dress: 'sill' },
    { name: 'hall front', axis: 'x', line: 4, at: [8, 10, 16, 18], y: [2, 4], out: 'north', dress: null, reveal: true }, // glazed a block out onto the verandas, a deep reveal inside
    { name: 'gallery front', axis: 'x', line: 5, at: [9, 17], y: [7, 8], out: 'north', dress: null },
    { name: 'hall back', axis: 'x', line: 13, at: [13, 15, 18], y: [2, 4], out: 'south', dress: null },
    { name: 'gallery back', axis: 'x', line: 13, at: [14, 18], y: [7, 8], out: 'south', dress: null },
    { name: 'hall east', axis: 'z', line: 19, at: [11], y: [2, 4], out: 'east', dress: null },
    { name: 'hall east front', axis: 'z', line: 19, at: [7], y: [2, 4], out: 'east', dress: null, until: 2 },
    { name: 'gallery east', axis: 'z', line: 19, at: [8, 10], y: [7, 8], out: 'east', dress: null },
    { name: 'study front', axis: 'x', line: 1, at: [13], y: [6, 8], out: 'north', dress: 'shutters' },
    { name: 'study west', axis: 'z', line: 11, at: [3], y: [7, 8], out: 'west', dress: null },
    { name: 'study east', axis: 'z', line: 15, at: [3], y: [7, 8], out: 'east', dress: null },
  ],
  3: [
    { name: 'ballroom great window', axis: 'x', line: 1, at: [21, 22, 24, 25], y: [2, 7], out: 'north', dress: null },
    { name: 'ballroom east 1', axis: 'z', line: 27, at: [4], y: [2, 7], out: 'east', dress: null },
    { name: 'ballroom east 2', axis: 'z', line: 27, at: [8], y: [2, 7], out: 'east', dress: null },
    { name: 'ballroom east 3', axis: 'z', line: 27, at: [12], y: [2, 7], out: 'east', dress: null },
    { name: 'ballroom back west', axis: 'x', line: 15, at: [21], y: [2, 4], out: 'south', dress: null },
    { name: 'ballroom back east', axis: 'x', line: 15, at: [25], y: [2, 4], out: 'south', dress: null },
    { name: 'gallery back', axis: 'x', line: 15, at: [22, 24], y: [6, 8], out: 'south', dress: null },
    { name: 'ballroom front side', axis: 'z', line: 19, at: [3], y: [2, 4], out: 'west', dress: null },
  ],
}

// The fires: the hall's in its back wall under the stack, the kitchen's in the west wall under its stack
export const FIRES = [[10, 1, 13, 'north'], [1, 1, 11, 'east']]

export const OUTSIDE = {
  rainButt: [13, 1, 14], // the kept barrel, on the back terrace by the garden door
  doorHood: [13, 5, 0], // a thatch hood over the front door at the verandas' height, its lantern under it
  verandaLamps: [[10, 4, 2], [16, 4, 2]], // hung from the pent's eave (a bottom slab holds a lamp; a top one doesn't)
  pots: { 1: [[1, 1, 0, 'potted_acacia_sapling'], [7, 1, 0, 'potted_dead_bush'], [20, 1, 3, 'potted_acacia_sapling'], [12, 1, 0, 'potted_red_tulip'], [14, 1, 0, 'potted_orange_tulip']] },
  hay: [[19, 1, 2]],
  // II: the yard
  beds: [[8, 16, 11, 17], [15, 16, 18, 17], [8, 21, 11, 22], [15, 21, 18, 22]],
  flowers: ['orange_tulip', 'red_tulip', 'poppy', 'dandelion'],
  tree: [13, 0, 19], // the family's acacia (its trunk leans a block east)
  seat: [[12, 1, 19, 'east'], [14, 1, 19, 'west'], [13, 1, 18, 'south'], [13, 1, 20, 'north']], // a ring seat round the trunk: each stair's back to it
  lamps: [[8, 23], [18, 23]],
  bench: { x0: 12, x1: 14, z: 23 },
  // III: the east yard: a lily pond on the walk from the ballroom's door, beds either side
  eastPond: { x0: 22, x1: 24, z0: 19, z1: 22 },
  eastBeds: [[20, 20, 20, 23], [26, 18, 26, 23]],
  eastLamps: [[21, 17], [25, 17]],
  gate: { x: 19, z: 19 },
}
