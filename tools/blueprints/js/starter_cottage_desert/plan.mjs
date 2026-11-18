// Starter Cottage, the DESERT look (looks/desert/starter_cottage[_n], ROADMAP 49.10a): the same house as the shipped
// Version B (../starter_cottage_b: its rooms, beds, job blocks, containers and every furniture spot in the same cell),
// drawn again for a hot, dry village: thick mud-brick walls, flat roofs behind parapets, a deep arcaded loggia for
// shade, small windows under stone brows, beam ends (vigas) out of the walls, the wing a two-storey block with a
// roof flue.
//
// --- Design notes: what is done, what is left (2026-11-18) -------------------------------------------------------------
//  Done: Level I (the house and its loggia), Level II (the wing block and its flue), Level III (the low kitchen block
//        east of the wing, its roof a terrace under the wing's windows), Level IV (B's cellar under the hall, cell for
//        cell, in sandstone), each saved as a candidate (build/rebuild/starter_cottage_desert/), checked (rebuild.py:
//        no CHECK line; holes.py: 0) and rendered (renders/ there). Kept cells and placed share as B's: II keeps 93 %
//        of I and places 50 %, III keeps 100 % and places 18 % (B: 92/56, 98/17).
//  Left: the critic pass (REBUILD.md) against the as-drawn cottage; the Java side of 49.10a (culture pick, Table,
//        GameTests, saving as looks/desert/...). Ideas the critic may want: an arched loggia (stairs, not slab
//        lintels), a palm or a well in the yard, a pergola on the kitchen's roof terrace.
//  Round 2 (the critic's notes, briefs/starter_cottage_looks_critic.md): 1 the corner piers stop at the parapet's
//        height (no stumps above it); 2 the beam ends are one even row at the deck's level, every third block, on
//        every block; 3 the door framed in chiseled sandstone, its bay open to the loggia's roof; 4 planting back
//        (tulips flanking the door, cactus and dead bush under the windows and at the corners). Not done: an awning.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Sandstone saltbox": B's shape exactly, roofs in sandstone slabs. Safe and fits every test, but it is only a
//     palette: a pitched roof in a place where it never rains is the tell.
//  2. "Courtyard house": the hall and (II) the wing as two sides of a walled court with a fig tree, rooms facing in.
//     The truest desert plan, but Level I alone is a wall round an empty yard, and a court doubles the footprint.
//  3. "Mud-brick block with a loggia": Level I is a cube of mud brick under a flat roof and a parapet, its front shaded
//     by a two-deep arcade (the loggia) whose roof sits a step lower than the house's; beam ends stick out under the
//     deck. Level II stacks a two-storey block on the east end, a taller cube with its own parapet and a flue, so the
//     house steps up like a pueblo; III adds a low block east of the wing; IV the same cellar.
//  Built: 3. It is the strongest: every level is a stack of flat-roofed cubes you can name from far off (low arcade,
//  house, tall wing), the loggia gives the deep shade a desert front needs and the court feel without doubling the
//  plot, and it keeps B's rooms cell for cell, so every home test still holds. Not the safest: no pitched roof at all.
//
// Coordinates as B's: x across the front (west 0 -> east), y up, z from the front (z = 0) to the back.
//   Loggia   x 1..7, z 0..1: cut sandstone columns at x 1, 3, 5, 7; roof deck y 4, a step below the house's; the door behind it.
//   House    walls x 1..7, z 2..10, y 1..3 mud brick, a terracotta frieze at y 4, deck at y 5, parapet y 6.
//   Flue     Level I: a sandstone stack on the west wall (x 0, z 5) with its breast, campfire at y 8.
//   Wing     Level II: x 7..11, z 1..10, two storeys (floor y 4, deck y 9, parapet y 10), a flue in its east wall
//            (x 11, z 9) up to y 10 with the campfire at y 11, reached from the wing's own roof.
import { FURNITURE as B_FURNITURE, OPENINGS as B_OPENINGS, MASSES as B_MASSES } from '../starter_cottage_b/plan.mjs'

export const SIZE = { 1: [9, 9, 12], 2: [13, 12, 12], 3: [18, 12, 12], 4: [18, 12, 12] }

// Palette (60/30/10): mud brick walls (60) / sandstone dressings: plinth, columns, deck, parapet (30) / terracotta
// frieze and spruce beam ends (10). Furniture stays spruce (every Level I spot is spruce: the tests read it).
export const PALETTE = {
  wall: [['mud_bricks', 7], ['packed_mud', 2]],
  frieze: 'terracotta',
  plinth: 'smooth_sandstone',
  column: 'cut_sandstone',
  deck: 'smooth_sandstone',
  lip: 'smooth_sandstone_slab',
  parapet: 'sandstone_wall',
  pier: 'chiseled_sandstone',
  stack: 'sandstone',
  breast: 'sandstone_stairs',
  viga: 'stripped_spruce_log',
  floor: 'spruce_planks',
  door: 'oak',
  glass: 'glass_pane',
  wood: 'spruce',
}

export const MASSES = {
  loggia: { x0: 1, x1: 7, z0: 0, z1: 1, columns: [1, 3, 5, 7], roofY: 4 },
  house: { walls: { x0: 1, x1: 7, z0: 2, z1: 10 }, room: { x0: 2, x1: 6, z0: 3, z1: 9 }, friezeY: 4, deckY: 5 },
  flue: { x: 0, z: 5, top: 7, breastTop: 3 },
  wing: { walls: { x0: 7, x1: 11, z0: 1, z1: 10 }, room: { x0: 8, x1: 10, z0: 2, z1: 9 }, floorY: 4, deckY: 9,
    flue: { x: 11, z: 9, top: 10 } },
  kitchen: { walls: { x0: 12, x1: 16, z0: 4, z1: 9 }, room: { x0: 12, x1: 15, z0: 5, z1: 8 }, friezeY: 4, deckY: 5,
    flue: { x: 16, z: 6, top: 6 } },
}

export const OPENINGS = {
  frontDoor: B_OPENINGS.frontDoor,
  // small windows: one glass cell each, a stone brow (a top slab) over those in the sun
  windows: {
    1: [
      { cells: [[2, 2, 2]], out: 'north', brow: false }, { cells: [[6, 2, 2]], out: 'north', brow: false },
      { cells: [[7, 2, 4]], out: 'east', brow: true }, { cells: [[7, 2, 8]], out: 'east', brow: true },
      { cells: [[1, 2, 9]], out: 'west', brow: true },
      { cells: [[2, 2, 10]], out: 'south', brow: true }, { cells: [[6, 2, 10]], out: 'south', brow: true },
      { cells: [[1, 3, 3]], out: 'west', brow: true },
    ],
    2: [
      { cells: [[9, 2, 1]], out: 'north', brow: true }, { cells: [[11, 2, 3]], out: 'east', brow: true },
      { cells: [[9, 6, 1]], out: 'north', brow: true }, { cells: [[9, 6, 10]], out: 'south', brow: true },
      { cells: [[11, 6, 3]], out: 'east', brow: true }, { cells: [[11, 6, 6]], out: 'east', brow: true },
      { cells: [[8, 2, 10]], out: 'south', brow: true },
    ],
    3: [{ cells: [[13, 2, 9]], out: 'south', brow: true }, { cells: [[16, 2, 7]], out: 'east', brow: true }],
  },
  kitchenDoor: B_OPENINGS.kitchenDoor,
  wingToKitchen: B_OPENINGS.wingToKitchen,
  hallToWing: B_OPENINGS.hallToWing,
  wingStair: B_OPENINGS.wingStair,
}

// B's furniture, cell for cell; only the lamps move to hang from the flat ceilings (B's chains reached up into its
// pitched roofs).
const moved = { '13,4,6': { pos: [13, 4, 6], chain: 0 }, '4,5,5': { pos: [4, 4, 5], chain: 0 }, '9,7,3': { pos: [9, 8, 3], chain: 0 }, '8,7,8': { pos: [8, 8, 8], chain: 0 } }
export const FURNITURE = Object.fromEntries(Object.entries(B_FURNITURE).map(([l, list]) =>
  [l, list.map(f => (f.part === 'hangingLamp' && moved[f.pos.join()]) ? { ...f, ...moved[f.pos.join()] } : f)]))

export const OUTSIDE = {
  path: [4, 0, 0],
  jars: [[2, 1, 1], [6, 1, 1]], // water jars in the loggia, out of the door's way
  cactus: [[0, 0, 0], [8, 0, 0]], // potted cactus at the loggia's corners
  vigasBack: [2, 4, 6],
  // planting by the door and the windows (the critic: bring it back): flowers flanking the door in the loggia,
  // cactus and dead bush on the plinth under the back windows and at the corners
  planting: {
    1: [[3, 1, 1, 'potted_orange_tulip'], [5, 1, 1, 'potted_orange_tulip'], [3, 1, 11, 'potted_cactus'],
      [5, 1, 11, 'potted_dead_bush'], [0, 0, 10, 'potted_dead_bush'], [8, 0, 10, 'potted_cactus']],
    2: [[12, 0, 1, 'potted_cactus'], [9, 1, 11, 'potted_dead_bush']],
    3: [[15, 1, 10, 'potted_cactus'], [12, 1, 3, 'potted_dead_bush']],
  },
  kitchenLamp: [15, 1, 3], // a lantern on a sandstone post by the kitchen door
}
export { B_MASSES }
