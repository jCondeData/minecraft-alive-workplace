// Starter Cottage, savanna look (ROADMAP 49.10a: looks/savanna/starter_cottage[_n]), the plan. A look is the same
// building drawn again in another shape (BlueprintLooks.java): the rooms, beds, job blocks, containers and every
// furniture spot of the shipped Version B (../starter_cottage_b/plan.mjs) stay in their cells, so every home test holds;
// the shell (massing, roof, openings, materials) is redrawn for a hot, dry grassland.
//
// --- Design notes: what is done, what is left (2026-11-18) -----------------------------------------------------------
//  Done: Level I (the Veranda House), Level II (+ the Mud Tower) and Level III (+ the Cookhouse), and Level IV (+ B's cellar under the hall, in the look's own stone), as candidates (build.sh, build/rebuild/
//  starter_cottage_savanna/). Not yet: the critic's pass (REBUILD.md) and the in-game build-to-DONE test.
//  Critic round 2 (2026-11-18): note 1, the crenellated flat top read as a castle keep: the tower now carries the
//  hall's thatched hip (peak y 11), the stack up its corner; note 2, acacia posts on the upper walls.
//  Own render pass (2026-11-18): a half-pitch slab hip was tried and read as a stacked cake of rings: the 45-degree hip
//  stays. Open for the critic: the hip is truncated against the tower in II (a half-hip, not a valley); the hall's back
//  wall shows little under the deep eave; the hall chimney's campfire (y 7) stays beside the tower's (y 11).
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Thatched rondavel": a round mud hut under a conical thatch-coloured roof, the wing a second rondavel. Strong
//     savanna read, but a circle can't hold B's rectangular rooms and furniture cells, so it would not be a look of B.
//  2. "Ranch house": B's plan under a plain low gable in acacia with terracotta walls. Safe, but it is B in another
//     palette, which a look must not be.
//  3. "The Veranda House and its Mud Tower": Level I is the hall under ONE HIPPED roof (golden thatch, eaves on all
//     four sides, no gables at all) whose front eave runs out two blocks over an open, shaded VERANDA on acacia posts;
//     packed-mud walls between grey acacia posts on a terracotta plinth. Level II raises B's two-storey wing as a
//     square MUD TOWER with a FLAT ROOF TERRACE behind a gapped parapet, acacia beam ends (toron) sticking out of its
//     walls under the parapet, and the chimney stack climbing its corner: the builder steps onto the terrace to light
//     it. Story: a herder's house that sleeps in the cool of the tower and lives in the shade of the veranda.
//  Building 3: it is the least safe (two roof systems that don't share a single gable, a tower that out-tops the hall)
//  and the only one with a silhouette you could name from 20 blocks: a low hip, then a low hip beside a flat-topped
//  tower. Shade is the climate's first need and the veranda and the deep hip eaves give it on every side.
//
// Coordinates as B: x across the front (west 0 -> east), y up, z from the front (z = 0) to the back.
import * as B from '../starter_cottage_b/plan.mjs'

export const SIZE = { 1: [9, 9, 12], 2: [13, 13, 12], 3: [18, 13, 12], 4: [18, 13, 12] }
export const BASEMENT = B.BASEMENT // Level IV's cellar, 4 deep, under the hall: B's room as drawn (it shows nowhere outside)

// Palette (60/30/10): packed mud (the walls' warm light 60) / acacia: grey log posts and orange shingle roof (the 30) /
// terracotta: plinth and deck; a golden bamboo-mosaic thatch over all (the 10, darkest low down). Why: the savanna's own materials,
// mud and acacia; the golden thatch over buff walls and an orange plinth is the biome's colour, the grey bark draws the bays.
export const PALETTE = {
  stone: [['terracotta', 5], ['red_terracotta', 2], ['brown_terracotta', 1]],
  mud: 'packed_mud',
  mudBrick: 'mud_bricks',
  frame: 'acacia_log',
  roof: { full: 'bamboo_mosaic', stairs: 'bamboo_mosaic_stairs', slab: 'bamboo_mosaic_slab' }, // golden, woven: the thatch
  floor: 'acacia_planks',
  deck: 'terracotta',
  wood: 'acacia', // trapdoors, fences, sills (furniture keeps B's spruce: FurnitureSpotGameTests names Level I's wood)
  door: 'oak', // B's door (the brief's tests name an oak door)
  glass: 'glass_pane',
  beam: 'acacia_fence',
}

export const MASSES = {
  cellar: B.MASSES.cellar,
  hall: { walls: { x0: 1, x1: 7, z0: 2, z1: 10 }, room: { x0: 2, x1: 6, z0: 3, z1: 9 }, posts: [1, 3, 5, 7] },
  // the hip: eaves at y 3 one block out at the sides and back, two out at the front (the veranda), its ridge at y 7
  // (a half-pitch slab hip was tried: it read as a stacked cake of rings, not a roof)
  hip: { x0: 0, z0: 0, x1: 8, z1: 11, eaveY: 3 },
  veranda: { x0: 0, x1: 8, z0: 0, z1: 1, posts: [0, 2, 6, 8] },
  chimney: { x: 0, z: 5, top: 6, breastTop: 3 },
  tower: { walls: { x0: 7, x1: 11, z0: 1, z1: 10 }, room: { x0: 8, x1: 10, z0: 2, z1: 9 }, floorY: 4,
    hip: { x0: 6, z0: 0, x1: 12, z1: 11, eaveY: 8 }, // the hall's thatch carried over the tower (critic round 2): peak y 11
    stack: { x: 11, z: 1, top: 11 } }, // the corner stack: its campfire at y 12 (> 10), lit from the roof beside it
  // Level III: the cookhouse, B's kitchen cells (walls x 12..16, z 4..9; the tower's east wall is its west) under a
  // three-sided hip whose fourth side runs into the tower (drawn as one hip from x 8, kept east of the tower)
  cookhouse: { walls: { x0: 12, x1: 16, z0: 4, z1: 9 }, room: { x0: 12, x1: 15, z0: 5, z1: 8 },
    hip: { x0: 8, z0: 3, x1: 17, z1: 10, eaveY: 3 }, flue: { x: 16, z: 6, top: 5 } },
}

const patch = (list, at, change) => list.map(f => (f.pos && f.pos.join() === at.join() ? { ...f, ...change } : f))
// Level II: the tower's flat roof is at y 8
// Level I: the hall's lamp hangs on one link from the hip's ridge (y 7), the outshot's on one from the slope (y 6)
let one = patch(patch(B.FURNITURE[1], [4, 5, 5], { chain: 1 }), [3, 4, 8], { chain: 1 })
// Level II: the chamber's lamps hang on chains from the hip (y 11 over x 9, y 10 over x 8)
let two = patch(patch(B.FURNITURE[2], [9, 7, 3], { chain: 3 }), [8, 7, 8], { chain: 2 })
export const FURNITURE = { 1: one, 2: two, 3: B.FURNITURE[3], 4: B.FURNITURE[4] } // the cookhouse's hip clears B's kitchen cells as drawn

const drop = (ws, names) => ws.filter(w => !names.includes(w.name))
export const OPENINGS = {
  ...B.OPENINGS,
  windows: {
    // no gables to light: the hip has none
    1: drop(B.OPENINGS.windows[1], ['west gable lights', 'west gable lights 2', 'east gable lights', 'east gable lights 2']),
    2: drop(B.OPENINGS.windows[2], ['chamber gable', 'chamber back gable']),
    // the high front light would sit in the cookhouse's eave: a window beside the door instead, over the counter
    3: [...drop(B.OPENINGS.windows[3], ['kitchen front high']), { name: 'kitchen front', cells: [[15, 2, 4], [15, 3, 4]], out: 'north', dress: 'shutters' }],
  },
}

export const ROOMS = B.ROOMS
export const WALK = { 1: B.WALK[1], 2: B.WALK[2], 3: B.WALK[3], 4: B.WALK[4] }
export const OUTSIDE = {
  cellarStores: B.OUTSIDE.cellarStores,
  path: [4, 0, 0],
  doorLamp: [4, 3, 1], // hangs under the veranda's roof (y 4 there) over the step
  pots: [[1, 1, 1, 'potted_acacia_sapling'], [7, 1, 1, 'potted_dead_bush'], [3, 1, 0, 'potted_red_tulip']],
  hay: [[0, 1, 1], [8, 1, 1]],
}
