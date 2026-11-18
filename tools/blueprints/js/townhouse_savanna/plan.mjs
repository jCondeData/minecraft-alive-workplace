// Burgher's Townhouse, the SAVANNA look (looks/savanna/townhouse[_n], ROADMAP 49.10b, as 49.10a): the same house as the
// shipped Townhouse (../townhouse/plan.mjs: its rooms, beds, job blocks, containers, doors, both flights, the tower's
// ladder and every furniture spot in the same cell at every level, the same 4-deep vault under the shop in IV), drawn
// again for a hot grassland with a rainy season. It speaks the savanna village's language (golden thatch hips with
// eaves on every side and no gables, grey acacia posts and beams, packed mud, a red granite ground storey on a
// terracotta plinth, verandas for shade), but it is the town's grand merchant house: the tallest home, three storeys
// under one great thatch, a veranda all along its street front, and from III a thatched lookout tower.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The trader's compound": the shop, the counting house and the strongroom as three round mud houses under conical
//     thatch round a yard. The strongest savanna picture, but the brief pins square rooms in one tall block: a circle
//     can't hold the shipped house's rooms and furniture cells; it would not be the same building.
//  2. "Acacia gable house": the shipped house's jettied gable and steep tiled roofs in acacia, mud and terracotta.
//     Safe, every cell as drawn, but a palette swap, and a gable end is what a savanna roof doesn't have.
//  3. "The Ivory Trader's House": Level I a three-storey house, a ground storey of red granite behind a VERANDA on grey
//     acacia posts under a low thatch eave, the hall storey of packed mud between acacia posts and beams jettied over it,
//     and the family's top floor tucked under ONE GREAT HIP of thatch with eaves a block out on every side, a clay pot
//     on its short ridge. II adds the counting house as a lower mud wing under its own hip, its veranda
//     carried on along the street a step back; III the strongroom as a TOWER of mud and acacia rising out of the back
//     under a steep pyramid of thatch with deep eaves (a lookout over the grassland, a granary's roof, never a keep's
//     crenels) and an acacia in the yard; IV the vault under the shop. Story: a trader in ivory and hides who built
//     with the village's materials but bigger: a stone storey for the goods, a veranda for the buyers, a lookout to
//     watch the caravans come up the track.
//  Built: 3. It is the strongest: from 20 blocks it is a red stone base, a timber-framed mud storey and a golden hip
//  three times a cottage's, with the veranda's shade line running along the street; the tower (III) is the town's
//  tallest roof. One roof language on every level (thatch hips, eaves on all sides, no gables), the same family as the
//  savanna cottage and stone house, grander.
//
// --- Design notes: what is done, what is left (2026-11-18) -----------------------------------------------------------
//  Done: Levels I-IV as candidates (build.sh -> build/rebuild/townhouse_savanna/). Every furniture spot of the shipped
//  Townhouse in the same cell at every level (look_spots.py: SAME BUILDING, 38/54/65/71 spots). Under the hips the hung
//  lamps take the chain the thatch gives them: the top floor's 1 and 0 links, the wing bedroom's 2, the lookout's 3.
//  Checked: rebuild.py no CHECK line at any level; holes.py 0 (with open); check.py's owner rules 0; II keeps 98 % of
//  I, III 99 % of II; I 53 stacks, II 68, III 79 (of 108). The kit's seal_slots carries the house's east wall out under
//  its eave over the wing's roof (II) and the tower's front wall out under its eave over the wing's (III): the roofs
//  meet at a wall, as it should.
//  Renders (front, back, left, right, top per level): build/rebuild/townhouse_savanna/renders/.
//  Tried and changed: a slab veranda roof read as a thin ledge (now a sloped thatch course with a full one behind,
//  turned round its ends); lamps hung from top slabs would drop (hung from the full course); the wing's veranda three
//  deep read to holes.py as a room open to the street (two deep, a step back from the house's); shutters on all three
//  wing windows and on the house's east hall window left one-wide slots the kit sealed (shutters on the middle one);
//  two pots on the ridge read as chimney pots (one).
//  Open for the critic: the tower's eaves are a block deep (the plot's edge); acacia shutters are bright.
//  Left: the critic's pass (REBUILD.md) against the shipped Townhouse; the Java side (49.10b ship step).
//
// --- Named parts (template coordinates: x across the front, west 0 -> east; z from the front, z = 0, to the back) ---
//   House      ground storey x 1..9, z 2..12, y 1..4 (red granite, acacia corner posts); the beam ring at y 5 out to the
//              jetty line z 1; the hall and chamber y 6..8 in packed mud between acacia posts; the beam ring y 9; the top
//              floor's knee walls y 10..11; the great hip from its eaves at y 11 (x 0..10, z 0..13) to the ridge at y 16
//              (x 5, z 5..8), a clay pot on the ridge.
//   Veranda    the street front: acacia posts at x 1, 4, 6, 9 on z 0, a thatch eave at y 4 over z 0..1 (a sloped
//              course out front, a full one under the jetty), turned round both ends.
//   Stack      the kitchen hearth's flue at x 5, z 13, mud brick, through the back eave.
//   Wing       II: the counting house x 10..16, z 4..12 (granite below, mud and acacia above), under its own hip from
//              eaves at y 8 (x 10..17, z 3..13, kept off the house) to the ridge at y 12; its veranda x 10..16,
//              z 2..3 on posts at x 10, 12, 14, 16 (two deep, like the house's), the same eave at y 4.
//   Tower      III: x 13..17, z 13..17, granite below, mud between acacia corner posts and beam rings above (y 5, 9, 13),
//              a steep thatch pyramid from eaves at y 18 (x 12..18, z 12..18) to its point at y 21, a pot on top.
//   Yard       III: an acacia tree behind the house, hay and pots.
//   Vault      IV: under the shop as shipped (x 1..9, z 2..12, 4 deep, the hatch at (2, 0, 7), ribs at z 5 and 9).
import {
  IDS, BASEMENT, MASSES as TH_MASSES, OPENINGS as TH_OPENINGS, FURNITURE as TH_FURNITURE,
} from '../townhouse/plan.mjs'

export { IDS, BASEMENT }
export const LEVELS = [1, 2, 3, 4]
export const SIZES = { 1: [11, 20, 14], 2: [18, 20, 14], 3: [19, 26, 19], 4: [19, 26, 19] } // shipped: 11x22x14, 18x22x14, 19x26x19

// Palette 60/30/10: golden bamboo-mosaic thatch (60 of what you see from the street) / red granite ground storey and
// packed-mud upper storeys (30) / grey acacia posts, beams and shutters, a terracotta plinth, pots and flowers (10).
// Furniture keeps the shipped house's spruce and dark oak; the doors stay spruce (the shipped cells).
export const PALETTE = {
  stone: [['granite', 6], ['polished_granite', 1], ['terracotta', 1]],
  mud: 'packed_mud', mudBrick: 'mud_bricks', plinth: [['terracotta', 5], ['red_terracotta', 2], ['brown_terracotta', 1]],
  frame: 'acacia_log', roof: { full: 'bamboo_mosaic', stairs: 'bamboo_mosaic_stairs', slab: 'bamboo_mosaic_slab' },
  floor: 'acacia_planks', upperFloor: 'acacia_planks', deck: 'terracotta', partition: 'acacia_planks', kitchenFloor: 'mud_bricks',
  shutter: 'acacia', wood: 'spruce', glass: 'glass_pane', step: 'mud_brick_stairs', sill: 'acacia_stairs',
  tree: { soil: 'coarse_dirt', log: 'acacia_log', leaves: 'acacia_leaves' },
  cellarWall: [['granite', 4], ['cobblestone', 2], ['terracotta', 1]], cellarFloor: 'mud_bricks', rib: 'polished_granite_stairs',
}

export const MASSES = {
  house: { x0: 1, x1: 9, z0: 2, zUp: 1, z1: 12, firstY: 5, atticY: 9, hip: { x0: 0, z0: 0, x1: 10, z1: 13, eaveY: 11 },
    posts: { front: [1, 4, 6, 9], back: [1, 4, 6, 9], side: [1, 4, 8, 12] } },
  veranda: { x0: 1, x1: 9, z0: 0, z1: 1, posts: [1, 4, 6, 9], eaveY: 4 },
  ridgePots: [[5, 17, 6]], // one clay pot on the ridge (two read as chimney pots)
  stack: { x: 5, z: 13, top: 14 },
  wing: { x0: 10, x1: 16, z0: 4, z1: 12, hip: { x0: 9, z0: 3, x1: 17, z1: 13, eaveY: 8 },
    posts: { front: [10, 12, 14, 16], side: [4, 6, 10, 12] },
    veranda: { x0: 10, x1: 16, z0: 2, z1: 3, posts: [10, 12, 14, 16], eaveY: 4 } },
  tower: { x0: 13, x1: 17, z0: 13, z1: 17, rings: [5, 9, 13], floors: [5, 9, 13],
    hip: { x0: 12, z0: 12, x1: 18, z1: 18, eaveY: 18 }, pot: [15, 22, 15], ladder: TH_MASSES.tower.ladder },
  yard: { tree: [4, 0, 16] },
  cellar: TH_MASSES.cellar,
}

// The shipped house's openings that hold the rooms together, cell for cell; the windows are this look's own (sills and
// pots on the ground storey, acacia shutters above, plain lights in the knee walls), kept wherever the shipped house
// rolls a blind at a window's head.
const sh = { dress: 'shutters' }
export const OPENINGS = {
  frontDoor: TH_OPENINGS.frontDoor, wingDoor: TH_OPENINGS.wingDoor, stairs: TH_OPENINGS.stairs, attic: TH_OPENINGS.attic,
  partition: TH_OPENINGS.partition, doorways: TH_OPENINGS.doorways,
  windows: [
    // house, ground storey: the shop front under the veranda, sills with flowers
    { cells: [[2, 2, 2], [3, 2, 2], [2, 3, 2], [3, 3, 2]], out: 'north', dress: 'sill', pots: ['potted_red_tulip', 'potted_orange_tulip'] },
    { cells: [[7, 2, 2], [8, 2, 2], [7, 3, 2], [8, 3, 2]], out: 'north', dress: 'sill', pots: ['potted_orange_tulip', 'potted_red_tulip'] },
    { cells: [[1, 2, 5], [1, 3, 5]], out: 'west', ...sh },
    { cells: [[1, 2, 9], [1, 3, 9]], out: 'west', ...sh },
    { cells: [[9, 2, 10], [9, 3, 10]], out: 'east', ...sh, until: 1 },
    { cells: [[3, 2, 12], [3, 3, 12]], out: 'south', ...sh },
    { cells: [[7, 2, 12], [7, 3, 12]], out: 'south', ...sh },
    // the hall and chamber: shutters all round (the blinds roll at the front's heads)
    { cells: [[2, 6, 1], [3, 6, 1], [2, 7, 1], [3, 7, 1]], out: 'north', ...sh },
    { cells: [[5, 6, 1], [5, 7, 1]], out: 'north', ...sh },
    { cells: [[7, 6, 1], [8, 6, 1], [7, 7, 1], [8, 7, 1]], out: 'north', ...sh },
    { cells: [[1, 6, 2], [1, 7, 2]], out: 'west', ...sh },
    { cells: [[1, 6, 6], [1, 7, 6]], out: 'west', ...sh },
    { cells: [[1, 6, 10], [1, 7, 10]], out: 'west', ...sh },
    { cells: [[9, 6, 2], [9, 7, 2]], out: 'east' }, // plain: from II its shutters stood over the wing's veranda in a pocket
    { cells: [[9, 6, 6], [9, 7, 6]], out: 'east', ...sh, until: 1 },
    { cells: [[9, 6, 10], [9, 7, 10]], out: 'east', ...sh, until: 1 },
    { cells: [[3, 6, 12], [3, 7, 12]], out: 'south', ...sh },
    { cells: [[7, 6, 12], [7, 7, 12]], out: 'south', ...sh },
    // the top floor: lights in the knee walls under the eaves
    { cells: [[3, 10, 1], [3, 11, 1]], out: 'north' },
    { cells: [[7, 10, 1], [7, 11, 1]], out: 'north' },
    { cells: [[1, 10, 5], [1, 11, 5]], out: 'west' },
    { cells: [[1, 10, 10], [1, 11, 10]], out: 'west' },
    { cells: [[9, 10, 5], [9, 11, 5]], out: 'east' },
    { cells: [[9, 10, 9], [9, 11, 9]], out: 'east' },
    { cells: [[3, 10, 12], [3, 11, 12]], out: 'south' },
    { cells: [[7, 10, 12], [7, 11, 12]], out: 'south' },
    // II, the counting house (its street windows on the veranda) and the wing bedroom
    { cells: [[11, 2, 4], [11, 3, 4]], out: 'north', from: 2 },
    { cells: [[15, 2, 4], [15, 3, 4]], out: 'north', from: 2 },
    { cells: [[11, 6, 4], [11, 7, 4]], out: 'north', from: 2 }, // plain: shutters on all three left one-wide slots the kit seals
    { cells: [[13, 6, 4], [13, 7, 4]], out: 'north', ...sh, from: 2 },
    { cells: [[15, 6, 4], [15, 7, 4]], out: 'north', from: 2 },
    { cells: [[16, 2, 8], [16, 3, 8]], out: 'east', ...sh, from: 2 },
    { cells: [[16, 6, 8], [16, 7, 8]], out: 'east', ...sh, from: 2 },
    { cells: [[12, 2, 12], [12, 3, 12]], out: 'south', from: 2 },
    { cells: [[12, 6, 12], [12, 7, 12]], out: 'south', from: 2 },
    // III, the tower: a window a storey on its open faces, the lookout's on three sides
    { cells: [[15, 2, 17], [15, 3, 17]], out: 'south', ...sh, from: 3 },
    { cells: [[17, 6, 15], [17, 7, 15]], out: 'east', ...sh, from: 3 },
    { cells: [[15, 10, 17], [15, 11, 17]], out: 'south', ...sh, from: 3 },
    { cells: [[13, 10, 15], [13, 11, 15]], out: 'west', ...sh, from: 3 },
    { cells: [[13, 14, 15], [13, 15, 15]], out: 'west', ...sh, from: 3 },
    { cells: [[17, 14, 15], [17, 15, 15]], out: 'east', ...sh, from: 3 },
    { cells: [[15, 14, 17], [15, 15, 17]], out: 'south', ...sh, from: 3 },
  ],
}

// The shipped house's furniture, every piece in its cell; the hung lamps take the chain the thatch above them gives.
const chains = { '5,13,4': 1, '5,14,9': 0, '13,9,8': 2, '15,17,15': 3 }
export const FURNITURE = TH_FURNITURE.map(f => (f.part === 'hangingLamp' && f.pos.join() in chains ? { ...f, chain: chains[f.pos.join()] } : f))

export const OUTSIDE = {
  pots: {
    1: [[0, 0, 0, 'potted_acacia_sapling'], [10, 0, 0, 'potted_dead_bush'], [0, 0, 7, 'potted_dead_bush'], [10, 0, 7, 'potted_acacia_sapling'],
      [0, 0, 13, 'potted_acacia_sapling'], [10, 0, 13, 'potted_dead_bush']],
    2: [[17, 0, 0, 'potted_acacia_sapling'], [17, 0, 6, 'potted_dead_bush'], [17, 0, 13, 'potted_acacia_sapling'], [11, 1, 3, 'potted_red_tulip']],
    3: [[18, 0, 12, 'potted_dead_bush'], [18, 0, 18, 'potted_acacia_sapling'], [0, 0, 18, 'potted_dead_bush'], [9, 0, 17, 'potted_orange_tulip']],
  },
  hay: { 1: [], 2: [[15, 1, 3]], 3: [[11, 0, 16], [11, 1, 16], [11, 0, 17]] },
  jars: { 1: [[6, 1, 1]], 2: [], 3: [[1, 0, 14]] },
  lamps: { 1: [[5, 3, 1]], 2: [[13, 3, 3]] }, // hung under the verandas' eaves at the doors (from a full block in the soffit)
}
