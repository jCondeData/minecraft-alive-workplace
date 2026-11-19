// Starter Cottage, taiga look (ROADMAP 49.10a: looks/taiga/starter_cottage[_n]), the plan. A look is the same building
// drawn again in another shape (BlueprintLooks.java): the rooms, beds, job blocks, containers and every furniture spot
// of the shipped Version B (../starter_cottage_b/plan.mjs) stay in their cells, so every home test holds; the shell
// (massing, roof, openings, materials) is redrawn for a cold, wet spruce forest.
//
// --- Design notes: what is done, what is left (2026-11-18) -----------------------------------------------------------
//  Done: Level I (the Log Cabin), Level II (+ the Loft Pen) and Level III (+ the Cook Shed), and Level IV (+ B's cellar under the hall, in the look's own stone), as candidates (build.sh, build/rebuild/
//  starter_cottage_taiga/). Not yet: the critic's pass (REBUILD.md) and the in-game build-to-DONE test.
//  Critic round 2 (2026-11-18): note 1, the low side walls get flower boxes and shutters (east, under the eave);
//  and a gabled dormer in the west slope (MASSES.dormer). Note 2, the east wall's foot: the woodpile squared off into one stack between the boxes.
//  Own render pass (2026-11-18): a dark oak roof merged into the logs (now slate); the open porch gable left a cave and an
//  open pocket (now a closed pediment and ceiling). Open for the critic: the back reads busier than the front (the pen's
//  back gable, its log ends and the shed's verge all meet at x 11..12); the cabin's west side is mostly stone and roof.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Stave house": a tall timber hall with stacked steep roofs and carved ridge ends, like a stave church. Striking,
//     but its tiers fight B's one-storey hall and its bed nook; the tests' cells would end up in a tower.
//  2. "Saltbox in spruce": B's own saltbox in spruce logs and a darker roof. Safe, and exactly what a look must not be:
//     B in another palette.
//  3. "The Log Cabin and its Loft Pen": Level I turns B's hall round to face the road GABLE-ON: a notched spruce log
//     pen on a mossy fieldstone course, under one STEEP slate gable (two up per one across) whose front runs out two
//     blocks over a PORCH on log posts with a fence rail, the eaves deep on every side for the snow, a fieldstone
//     chimney climbing the west eave, the log ends crossing out at every corner and a woodpile under the east eave.
//     Level II sets a taller second pen beside it (B's wing), also gable-on, its ridge three courses over the cabin's: a
//     pair of steep gables to the road, the loft lit through its gable, a pent roof over the parlour window.
//  Building 3: the strongest silhouette (two steep, staggered gables facing you; B shows you its long side), every
//  piece a cold climate wants (steep roofs to shed snow, deep eaves, a raised stone course against the wet, wood stacked
//  for winter) and the riskiest: a steep roof is tall, so II's chimney has to clear it and the builder must reach it.
//
// Coordinates as B: x across the front (west 0 -> east), y up, z from the front (z = 0) to the back.
import * as B from '../starter_cottage_b/plan.mjs'

export const SIZE = { 1: [9, 11, 12], 2: [13, 14, 12], 3: [18, 14, 12], 4: [18, 14, 12] }
export const BASEMENT = B.BASEMENT // Level IV's cellar, 4 deep, under the hall: B's room as drawn (it shows nowhere outside)

// Palette (60/30/10): spruce logs (the walls' brown 60) / deepslate-tile roof (the 30, the darkest mass on top, slate
// grey as a snowy forest's roofs read; a dark oak roof was tried and merged into the logs) / mossy fieldstone: plinth, base course, hearth and chimney (the 10). Spruce planks fill the
// gables so the log courses read below them.
export const PALETTE = {
  stone: [['cobblestone', 5], ['mossy_cobblestone', 3], ['stone', 1], ['andesite', 1]],
  log: 'spruce_log',
  gable: 'spruce_planks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  trim: 'spruce_stairs', // upside-down under the verges
  floor: 'spruce_planks',
  deck: 'spruce_planks',
  wood: 'spruce',
  door: 'oak', // B's door (the brief's tests name an oak door)
  glass: 'glass_pane',
}

export const MASSES = {
  cellar: B.MASSES.cellar,
  hall: { walls: { x0: 1, x1: 7, z0: 2, z1: 10 }, room: { x0: 2, x1: 6, z0: 3, z1: 9 }, logs: [2, 3] },
  // steep, gable-on: ridge along z at x 4, y 10; eaves at x 0 and 8 (y 3), the front runs out to z 0 over the porch
  roof: { axis: 'z', across: [0, 8], along: [0, 11], eaveY: 3, steep: true },
  tie: { y: 6, z: 5, x0: 3, x1: 5 }, // the tie beam the hall's lamp hangs from
  porch: { x0: 1, x1: 7, z0: 0, z1: 1, posts: [1, 7], beamY: 4, rails: [[1, 3], [5, 7]] }, // its pediment closes the
  // gable over the beam, flush with the roof's front: the cabin shows the road a whole gable, the porch tucked under it
  chimney: { x: 0, z: 5, top: { 1: 9, 2: 10 }, breastTop: 3 },
  woodpile: { x: 8, z0: 5, z1: 7 },
  dormer: { z: 7, face: 2, eave: 1, y0: 6 }, // in the west slope: face on x 2 (window y 6..7 over the eave), roof to y 10
  pen: { walls: { x0: 7, x1: 11, z0: 1, z1: 10 }, room: { x0: 8, x1: 10, z0: 2, z1: 9 }, floorY: 4, plateY: 8,
    roof: { axis: 'z', across: [6, 12], along: [0, 11], eaveY: 8, steep: true } },
  // Level III: the cook shed, B's kitchen cells (walls x 12..16, z 4..9) as a low log pen under a slate lean-to that
  // tucks under the Loft Pen's east eave; the woodpile moves out under its low eave
  shed: { walls: { x0: 12, x1: 16, z0: 4, z1: 9 }, room: { x0: 12, x1: 15, z0: 5, z1: 8 },
    roof: { axis: 'z', across: [12, 17], ridge: 12, along: [3, 10], ridgeY: 7 }, flue: { x: 16, z: 6, top: 5 },
    woodpile: { x: 17, z0: 5, z1: 7 } },
}

const patch = (list, at, change) => list.map(f => (f.pos && f.pos.join() === at.join() ? { ...f, ...change } : f))
// Level I: the hall's lamp hangs from the tie beam; the outshot's on a chain from the slope's planks (y 8)
const one = patch(patch(B.FURNITURE[1], [4, 5, 5], { chain: 0 }), [3, 4, 8], { chain: 3 })
// Level II: the loft's lamps hang on chains from the ridge (y 13) and the slope (y 11)
const two = patch(patch(B.FURNITURE[2], [9, 7, 3], { chain: 5 }), [8, 7, 8], { chain: 3 })
export const FURNITURE = { 1: one, 2: two, 3: B.FURNITURE[3], 4: B.FURNITURE[4] } // the shed's roof is B's kitchen roof: its cells hold

const drop = (ws, names) => ws.filter(w => !names.includes(w.name))
export const OPENINGS = {
  ...B.OPENINGS,
  windows: {
    1: [...drop(B.OPENINGS.windows[1], ['west gable lights', 'west gable lights 2', 'east gable lights', 'east gable lights 2']),
      { name: 'front gable', cells: [[4, 5, 2], [4, 6, 2]], out: 'north', dress: null },
      { name: 'back gable', cells: [[4, 5, 10], [4, 6, 10]], out: 'south', dress: null }],
    2: B.OPENINGS.windows[2],
    3: B.OPENINGS.windows[3],
  },
}

export const ROOMS = B.ROOMS
export const WALK = { 1: B.WALK[1], 2: B.WALK[2], 3: B.WALK[3], 4: B.WALK[4] }
export const OUTSIDE = {
  cellarStores: B.OUTSIDE.cellarStores,
  doorLamp: [4, 3, 0], // hangs from the porch's beam over the step
  pots: [[2, 1, 1, 'potted_spruce_sapling'], [6, 1, 1, 'potted_fern']],
  kitchenLamp: [15, 1, 3],
  // flower boxes and one-high shutters on the low east wall's windows, under the eave (critic round 2, note 1)
  sideBoxes: [{ at: [8, 4], pot: 'potted_cornflower' }, { at: [8, 8], pot: 'potted_lily_of_the_valley' }],
}
