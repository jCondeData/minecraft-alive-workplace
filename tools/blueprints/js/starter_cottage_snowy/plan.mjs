// Starter Cottage, the SNOWY look (looks/snowy/starter_cottage[_n], ROADMAP 49.10a): the same house as the shipped
// Version B (../starter_cottage_b: its rooms, beds, job blocks, containers and every furniture spot in the same cell),
// drawn again for a cold, snowy village: a log cabin on a stone course under a steep slate roof that sheds snow, a
// closed mudroom before the door (two doors: the cold stays out), a heavy stone chimney, small windows, a low ceiling
// with a loft over it to keep the heat in the room.
//
// --- Design notes: what is done, what is left (2026-11-18) -------------------------------------------------------------
//  Done: Level I (the cabin, mudroom and chimney), Level II (the steep cross-wing, stone below and logs above),
//        Level III (a stone lean-to kitchen under the wing's eave, a slate snow hood over its door), Level IV (B's
//        cellar under the hall, cell for cell, in cobble), each saved as a candidate
//        (build/rebuild/starter_cottage_snowy/), checked (rebuild.py: no CHECK line; holes.py: 0) and rendered
//        (renders/ there). II keeps 90 % of I and places 50 %, III keeps 100 % and places 13 % (B: 92/56, 98/17).
//  Left: the critic pass (REBUILD.md); the Java side of 49.10a (culture pick, Table, GameTests, saving as
//        looks/snowy/...). Level II's chimney campfire (y 11) is reached from the hall's roof at x 2 (two across,
//        two up): ChimneyCampfireGameTests should run on the look before it ships. Ideas for the critic: barge boards
//        under the gables' verges, a snow-hood over the wing's ground window.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Snowy saltbox": B's shape in spruce and slate. Fits everything, but the long shallow catslide is exactly the
//     roof that holds a snow load: only a palette.
//  2. "A-frame": the roof comes down to the ground on both sides, the walls are the roof. The strongest silhouette, but
//     B's rooms don't fit under it (the chairs at the walls lose their headroom), and Level II's wing would be a second
//     A-frame glued on, never growing out of the first.
//  3. "Log cabin with a mudroom": Level I is a log cabin on a stone course, gable end to the road under a steep (two
//     up for one across) slate roof whose eaves come down low on both sides, a closed little mudroom with its own
//     steep gable before the door, a heavy stone chimney up the west side; a loft over a low ceiling. Level II's wing
//     crosses the east end, stone below and logs above, its own steep gable to the road, taller than the hall's; III a
//     lean-to; IV the cellar.
//  Built: 3. Its steep gables read "snow country" from any distance, the mudroom and the low insulated ceiling are what
//  a cold house really does, and B's rooms keep every cell. Not the safest: the hall's ridge is turned to face the
//  road (B's runs across it) and the roof is half again as tall.
//
// Coordinates as B's: x across the front (west 0 -> east), y up, z from the front (z = 0) to the back.
//   Mudroom  x 3..5, z 0..1: plank walls, the outer oak door at (4, 1, 0), its own steep little gable (ridge y 5).
//   Cabin    walls x 1..7, z 2..10: a cobble course at y 1, spruce logs lying along the walls y 2..4, log corners,
//            plank gables; a plank ceiling (the loft floor) at y 5 over the room.
//   Roof     steep gable, ridge along z (gable to the road), eaves at x 0 and x 8 (y 4), ridge y 11, overhanging
//            z 1 and z 11; deepslate tiles.
//   Chimney  stone, x 0: three wide (z 4..6) up to y 6, the stack (z 5) up to y 10, the campfire at y 11.
//   Wing     Level II: x 7..11, z 1..10: cobble storey (y 1..3), a log floor beam (y 4), log storey (y 5..7), plate
//            y 8, its steep gable (ridge along z, y 13) to the road.
import { FURNITURE as B_FURNITURE, OPENINGS as B_OPENINGS } from '../starter_cottage_b/plan.mjs'

export const SIZE = { 1: [9, 12, 12], 2: [13, 14, 12], 3: [18, 14, 12], 4: [18, 14, 12] }

// Palette (60/30/10): spruce logs (60) / dark deepslate-tile roof (30) / cobble and stone for the course, chimney and
// the wing's ground storey (10, darkest low down). Furniture stays spruce (the tests read Level I's spots' wood).
export const PALETTE = {
  stone: [['cobblestone', 5], ['stone', 2], ['mossy_cobblestone', 1], ['andesite', 1]],
  chimney: [['stone_bricks', 4], ['cobblestone', 3], ['cracked_stone_bricks', 1]],
  log: 'spruce_log',
  gable: 'spruce_planks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'spruce_planks',
  door: 'oak',
  glass: 'glass_pane',
  wood: 'spruce',
}

export const MASSES = {
  mudroom: { x0: 3, x1: 5, z0: 0, z1: 1, eaveY: 4 },
  cabin: { walls: { x0: 1, x1: 7, z0: 2, z1: 10 }, room: { x0: 2, x1: 6, z0: 3, z1: 9 }, ceilingY: 5,
    roof: { axis: 'z', across: [0, 8], along: [1, 11], eaveY: 4, steep: true } },
  chimney: { x: 0, z: 5, wide: [4, 6], wideTop: 6, top: 10 },
  wing: { walls: { x0: 7, x1: 11, z0: 1, z1: 10 }, room: { x0: 8, x1: 10, z0: 2, z1: 9 }, floorY: 4, plateY: 8,
    roof: { axis: 'z', across: [6, 12], along: [0, 11], eaveY: 8, steep: true } },
  kitchen: { walls: { x0: 12, x1: 16, z0: 4, z1: 9 }, room: { x0: 12, x1: 15, z0: 5, z1: 8 },
    roof: { axis: 'z', across: [12, 17], ridge: 12, along: [3, 10], ridgeY: 7 }, flue: { x: 16, z: 6, top: 5 } },
}

export const OPENINGS = {
  innerDoor: { at: [4, 1, 2], facing: 'south' }, // B's front door's cell: now the mudroom's inner door
  windows: {
    1: [
      { cells: [[2, 2, 2]], out: 'north', dress: null }, { cells: [[6, 2, 2]], out: 'north', dress: null },
      { cells: [[7, 2, 4]], out: 'east', dress: 'shutters' }, { cells: [[7, 2, 8]], out: 'east', dress: 'shutters' },
      { cells: [[1, 2, 9]], out: 'west', dress: 'shutters' },
      { cells: [[2, 2, 10]], out: 'south', dress: null }, { cells: [[6, 2, 10]], out: 'south', dress: null },
      { cells: [[4, 7, 2]], out: 'north', dress: null }, { cells: [[4, 7, 10]], out: 'south', dress: null }, // the loft's
    ],
    2: [
      { cells: [[9, 2, 1]], out: 'north', dress: 'shutters' }, { cells: [[11, 2, 3]], out: 'east', dress: null },
      { cells: [[9, 6, 1]], out: 'north', dress: 'shutters' }, { cells: [[9, 6, 10]], out: 'south', dress: 'shutters' },
      { cells: [[9, 10, 1]], out: 'north', dress: null }, { cells: [[11, 6, 3]], out: 'east', dress: null },
    ],
    3: [{ cells: [[13, 2, 9]], out: 'south', dress: 'shutters' }, { cells: [[16, 2, 7]], out: 'east', dress: null }],
  },
  kitchenDoor: B_OPENINGS.kitchenDoor,
  wingToKitchen: B_OPENINGS.wingToKitchen,
  hallToWing: B_OPENINGS.hallToWing,
  wingStair: B_OPENINGS.wingStair,
}

// B's furniture, cell for cell; only the lamps' chains change: the hall's hangs from the loft floor, the chamber's
// reach up to the steeper roof.
const moved = { '4,5,5': { pos: [4, 4, 5], chain: 0 }, '9,7,3': { pos: [9, 7, 3], chain: 5 }, '8,7,8': { pos: [8, 7, 8], chain: 3 } }
export const FURNITURE = Object.fromEntries(Object.entries(B_FURNITURE).map(([l, list]) =>
  [l, list.map(f => (f.part === 'hangingLamp' && moved[f.pos.join()]) ? { ...f, ...moved[f.pos.join()] } : f)]))

export const OUTSIDE = {
  porchLamps: [[2, 2, 0], [6, 2, 0]], // hung under the mudroom's eaves
  kitchenLamp: [15, 1, 3], // a lantern on a spruce post by the kitchen door
  kitchenHood: { x0: 12, x1: 14, z: 3, y: 4 }, // a snow hood over the kitchen door: a short slate pentice
  woodpile: [[8, 1, 3], [8, 1, 4], [8, 2, 3], [8, 2, 4], [8, 1, 5]], // under the east eave, out of the snow
}
