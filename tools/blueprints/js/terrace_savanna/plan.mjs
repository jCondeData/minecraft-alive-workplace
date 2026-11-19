// Terrace, the SAVANNA look (looks/savanna/terrace[_2], ROADMAP 49.10c, as 49.10a): the same building as the shipped
// Terrace (../terrace/plan.mjs: its homes on the same party walls, every bed, door, kept container, ladder and every
// furniture spot in the same cell at both levels), drawn again for a hot grassland with a rainy season. It speaks the
// savanna village's language: a golden thatch hip with eaves on every side and no gable anywhere, grey acacia posts and
// beams, packed mud walls on a red granite storey and a terracotta plinth, a veranda for shade, painted trims.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The compound": each home a round mud house under its own conical thatch, the row joined by a mud wall. The
//     strongest savanna picture, but the brief pins square rooms on shared party walls 5 apart: round huts can't share
//     a wall or hold the shipped furniture cells.
//  2. "Hip per home": each house its own little pyramid of thatch, shoulder to shoulder. Reads as several homes, but
//     every pair of hips meets in a valley over the party wall, and the critics have refused ragged valleys every time.
//  3. "The Veranda Row" (the long houses of a Sahel or East African trading village): ONE long golden hip over the whole
//     row, eaves a block out on all four sides and no gable, a low thatch veranda along the street on grey acacia posts
//     that shades every door, each household's front painted in its own colour round its upper window (the painted
//     trims of a savanna village, the colours its beds already have), each household's own mud stack through the back
//     eave. Story: the village thatches one roof for everybody; under it each family paints its own wall.
//  Built: 3. It is the strongest: the simplest roof done cleanly (one hip, no valley, no gable, II the same hip made
//  longer), the veranda line and the posts give the front its depth, and the painted fronts and the row of stacks tell
//  the households apart from 20 blocks. It is the same family as the savanna cottage and townhouse, plainer, as a
//  terrace should be.
//
// --- Design notes: what is done, what is left (2026-11-19) -----------------------------------------------------------
//  Done: Levels I and II as candidates (build.sh -> build/rebuild/terrace_savanna/). Every home on the shipped party walls
//  (x 1, 6, 11, 16), walls z 1..8, the bedroom floor at y 5, bed heads at y 6; the shipped furniture list drawn as is.
//  The bedrooms are open to the thatch (the hip's eaves at y 7, its ridge at y 11); the party walls rise to it, so each
//  household's loft is its own. The bedroom lamps hang from the thatch on the chain it gives them (0 or 1 links where
//  the shipped ones take 2).
//  Checked: look_spots.py terrace_savanna terrace: SAME BUILDING (24 and 36 shipped spots, none missing, no bed, container,
//  door or ladder moved; only the bedroom lamps' chains differ); rebuild.py no CHECK line at either level; holes.py 0
//  (with open) at both; every id vanilla 1.21.1; II keeps 96 % of I.
//  Renders (front, back, left, right, top per level): build/rebuild/terrace_savanna/renders/.
//  Tried and changed: acacia doors (a door is a kept block: the shipped spruce); the stacks four courses over the eave
//  read from the back as free pillars beside the thatch (now two, smoking just over the eave).
//  Open for the critic: the veranda is one row deep (the walls stand on z 1 with the plot's edge at z 0).
//
// --- Named parts (template coordinates: x across the front, west 0 -> east; z from the front, z = 0, to the back) ---
//   Houses      every home u 0..5 (x = L + u), walls z 1..8: red granite y 1..4 on a terracotta plinth, a grey acacia
//               beam ring y 5 (the bedroom floor), packed mud y 6..7 up under the thatch; acacia posts on every wall
//               line's front and back corner.
//   Hip         one thatch hip over x west-1..east+1, z 0..9, eaves at y 7, ridge y 11 along x.
//   Veranda     z 0: posts at each home's u 0 and u 3, y 1..3; a thatch eave at y 4, turned round both ends.
//   Paint       each home's upper front: white and its own colour round the bedroom window (u 1 and u 4, y 6..7).
//   Stacks      each home's own, mud brick at its stove's u on z 9, through the back eave, smoking.
import {
  HOMES as T_HOMES, at, FURNITURE as T_FURNITURE, OPENINGS as T_OPENINGS, OUTSIDE as T_OUTSIDE,
} from '../terrace/plan.mjs'

export const LEVELS = [1, 2]
export const IDS = { 1: 'terrace', 2: 'terrace_2' }
export const SIZES = { 1: [13, 16, 10], 2: [18, 16, 10] } // as shipped

// Palette 60/30/10: packed mud and red granite walls (60) / golden thatch (bamboo mosaic) and grey acacia logs (30) /
// terracotta plinth, the painted trims, the spruce doors (10). Furniture keeps the shipped Terrace's spruce.
export const PALETTE = {
  granite: [['granite', 6], ['polished_granite', 1], ['terracotta', 1]],
  mud: 'packed_mud', mudBrick: 'mud_bricks',
  plinth: [['terracotta', 5], ['red_terracotta', 2], ['brown_terracotta', 1]],
  frame: 'acacia_log', roof: { full: 'bamboo_mosaic', stairs: 'bamboo_mosaic_stairs', slab: 'bamboo_mosaic_slab' },
  floor: 'mud_bricks', upperFloor: 'acacia_planks', deck: 'terracotta', step: 'mud_brick_stairs',
  shutter: 'acacia', door: 'spruce', // the shipped doors' wood (a door is kept block for block)
  glass: 'glass_pane', paint: 'white_terracotta',
}

export const HOUSE = {
  walls: { z0: 1, z1: 8 }, upperFloor: 5, beamY: 5,
  hip: { z0: 0, z1: 9, eaveY: 7 }, // x: a block past the row's ends
  veranda: { z: 0, posts: [0, 3], y1: 3, eaveY: 4 },
  paint: { us: [1, 4], ys: [6, 7] },
  stack: { u: 'furnace', z: 9, top: 9 }, // two courses over the eave: a tall one read as a free pillar (pass 1)
}

export const HOMES = T_HOMES
export { at }

export const OPENINGS = {
  door: T_OPENINGS.door, ladder: T_OPENINGS.ladder,
  windows: [
    { name: 'front room', us: [1, 2], ys: [2, 3], z: 1, out: 'north', dress: null }, // behind the veranda's posts
    { name: 'bedroom front', us: [2, 3], ys: [6, 7], z: 1, out: 'north', dress: null }, // in its painted surround
    { name: 'kitchen back', us: ['sink'], ys: [2, 3], z: 8, out: 'south', dress: 'shutters' },
    { name: 'bedroom back', us: [3, 4], ys: [6, 7], z: 8, out: 'south', dress: null },
  ],
  endWindows: [{ z: 4, ys: [2, 3] }, { z: 4, ys: [6, 7] }],
}

export const FURNITURE = T_FURNITURE
export const OUTSIDE = {
  rainBarrel: T_OUTSIDE.rainBarrel, // the kept barrel (0, 0, 1) at the row's west end
  doorLamp: { u: 4, y: 3, z: 0 }, // hung from the veranda's eave over every door's step
  hay: { u: 1 }, pot: { u: 2 }, // a hay bale to sit on and a water jar under each front-room window
}
