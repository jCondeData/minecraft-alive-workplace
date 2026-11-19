// Stone House, the SAVANNA look (looks/savanna/stone_house[_n], ROADMAP 49.10b, as 49.10a): the same house as the
// shipped Stone House (../stone_house/plan.mjs: its rooms, beds, job blocks, containers, stair and every furniture spot
// in the same cell at every level, the same 4-deep cellar under the hall in IV), drawn again for a hot grassland with a
// rainy season. It speaks the savanna village's language the Starter Cottage's look set (golden thatch hips with eaves
// on every side and no gables, grey acacia posts, packed mud, a terracotta plinth, a veranda for shade), but it is the
// village's STONE house: a ground storey of red granite, the upper storey of mud between acacia posts tucked under one
// great four-sided thatch that rises to a point with a clay pot on it.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Two rondavels": the hall and the parlour as two round mud houses under conical thatch, joined by a covered way.
//     The strongest savanna picture, but a circle can't hold the shipped house's square rooms and furniture cells:
//     it would not be the same building.
//  2. "Acacia ranch house": the shipped house's gable and catslide in acacia and terracotta. Safe, every cell as drawn,
//     but a palette swap; a gable end is what a savanna roof doesn't have.
//  3. "The Granite House under a Pyramid of Thatch": Level I a square two-storey house, a ground storey of coursed
//     red granite (the stone the village brings up from its quarry) and an upper storey of packed mud between grey
//     acacia posts on a log beam ring, under ONE square hipped thatch that rises from eaves a block out on every side
//     to a point, a clay pot as its finial: the roof is two thirds of the house, a rondavel's cone made square. Level
//     II adds the parlour as a mud wing under its own low three-sided hip that runs out over a VERANDA on acacia posts
//     in front; Level III the back room under a second low hip whose ridge runs into the back of the great roof; IV
//     the cellar in granite. Story: a herder's family who could afford a stone ground floor, a cool upper room under
//     the thatch, and the shade of the veranda where the day is spent.
//  Built: 3. It is the strongest: from 20 blocks it is a golden pyramid on a red stone base with two lower hips at its
//  feet, a silhouette no other home has; one roof language (thatch hips, eaves on every side, no gables) on every level;
//  the veranda gives the shade the climate asks for. Not the safest: a square pyramid roof the wing and the back room
//  must run into without a valley, and the upper storey only three blocks of wall under it.
//
// --- Design notes: what is done, what is left (2026-11-18) -----------------------------------------------------------
//  Done: Levels I-IV as candidates (build.sh -> build/rebuild/stone_house_savanna/). Every furniture spot of the
//  shipped Stone House in the same cell at every level (tools/blueprints/look_spots.py), every lamp on the
//  shipped chain (the bedroom's four links reach the point's underside).
//  Checked: rebuild.py no CHECK line at any level; holes.py 0 (with open); check.py's owner rules 0; II keeps 96 % of
//  I and places 17 %, III keeps 96 % of II and places 19 % (shipped: 98/13, 94/19); Level I about 46 stacks, III 55.
//  Renders (front, back, left, right, top per level, IV's cutaways at y -1, 3, 7): build/rebuild/stone_house_savanna/renders/.
//  Tried and changed: the parlour's hip first ran two blocks past its back wall and read as a shelf from behind (now
//  one); a stripped-acacia beam ring read as a second orange plinth (now grey, one frame with the posts).
//  Open for the critic: eaves only a block deep on the house (the plot's edge); the east and back walls of the parlour
//  and the back room stand on the plot's edge, their eaves flush.
//  Left: the critic's pass (REBUILD.md) against the shipped Stone House; the Java side (49.10b ship step).
//
// Coordinates as the shipped house: x across the front (west 0 -> east), y up, z from the front (z = 0) to the back.
//   House     walls x 1..9, z 1..9; the granite ground storey y 1..4, a beam ring and the upper floor at y 5, the mud
//             upper storey y 6..8; the great hip from eaves at y 8 (x 0 / 10, z 0 / 10) to its point at (5, 13, 5).
//   Stack     the hearth's stack on the west wall at x 0, z 3 (breast z 2..4), up past the eave, smoking at y 11.
//   Parlour   II: walls x 9..12, z 2..8 in mud between acacia posts; its hip x 6..12, z 0..9 from eaves at y 5, kept
//             east of the house's wall, its front eave over the veranda x 10..12, z 0..1 on two acacia posts.
//   Back room III: walls x 1..9, z 9..13 in mud between acacia posts; its hip x 0..10, z 7..13 from eaves at y 5, kept
//             behind the house's wall, its ridge (z 10, y 8) running in under the great hip's back eave.
//   Cellar    IV: as the shipped house's (x 1..9, z 1..9, 4 deep, the ladder at (2, z 5), the beam along z 3 on a post).
import {
  IDS, BASEMENT, MASSES as SH_MASSES, OPENINGS as SH_OPENINGS, FURNITURE as SH_FURNITURE,
} from '../stone_house/plan.mjs'

export { IDS, BASEMENT }
export const LEVELS = [1, 2, 3, 4]
export const SIZES = { 1: [11, 16, 11], 2: [13, 16, 11], 3: [13, 16, 14], 4: [13, 16, 14] } // as shipped

// Palette 60/30/10: golden bamboo-mosaic thatch (60 of what you see: the roof is most of the house) / red granite
// ground storey and packed-mud upper storey (30) / grey acacia posts, beam ring and shutters, a terracotta plinth (10).
// Furniture keeps the shipped house's spruce and dark oak (every spot's wood as shipped).
export const PALETTE = {
  stone: [['granite', 6], ['polished_granite', 1], ['terracotta', 1]],
  mud: 'packed_mud', mudBrick: 'mud_bricks', plinth: [['terracotta', 5], ['red_terracotta', 2], ['brown_terracotta', 1]],
  frame: 'acacia_log', beam: 'acacia_log', // the beam ring grey like the posts: one frame
  roof: { full: 'bamboo_mosaic', stairs: 'bamboo_mosaic_stairs', slab: 'bamboo_mosaic_slab' },
  floor: 'acacia_planks', upperFloor: 'acacia_planks', deck: 'terracotta', partition: 'acacia_planks',
  wood: 'acacia', door: 'spruce', glass: 'glass_pane', step: 'mud_brick_stairs', sill: 'acacia_stairs',
  cellarWall: [['granite', 4], ['cobblestone', 2], ['terracotta', 1]], cellarFloor: 'mud_bricks',
}

export const MASSES = {
  house: { x0: 1, z0: 1, x1: 9, z1: 9, upperY: 5, hip: { x0: 0, z0: 0, x1: 10, z1: 10, eaveY: 8 },
    posts: { front: [1, 3, 7, 9], back: [1, 3, 7, 9], sides: [1, 5, 9] } },
  stack: { x: 0, z: 3, top: 10, breastTop: 3 },
  parlour: { x0: 9, z0: 2, x1: 12, z1: 8, hip: { x0: 6, z0: 0, x1: 12, z1: 9, eaveY: 5 }, posts: [2, 4, 6, 8],
    veranda: { x0: 10, x1: 12, z0: 0, z1: 1, posts: [10, 12] } },
  backRoom: { x0: 1, z0: 9, x1: 9, z1: 13, hip: { x0: 0, z0: 7, x1: 10, z1: 13, eaveY: 5 }, posts: [1, 5, 9] },
  cellar: SH_MASSES.cellar,
}

// The shipped house's openings that hold rooms together (the door, the stair, the partition, the doorways knocked
// through in II and III), cell for cell; the windows are this look's own: sills and pots below, shutters above.
export const OPENINGS = {
  frontDoor: SH_OPENINGS.frontDoor, stairs: SH_OPENINGS.stairs, partition: SH_OPENINGS.partition,
  wingDoorway: SH_OPENINGS.wingDoorway, backDoorway: SH_OPENINGS.backDoorway,
  windows: [
    { name: 'hall front west', cells: [[3, 2, 1], [3, 3, 1]], out: 'north', dress: 'sill', pot: 'potted_red_tulip' },
    { name: 'hall front east', cells: [[7, 2, 1], [7, 3, 1]], out: 'north', dress: 'sill', pot: 'potted_orange_tulip' },
    { name: 'box room west', cells: [[1, 2, 7], [1, 3, 7]], out: 'west', dress: 'shutters' },
    { name: 'box room back', cells: [[3, 2, 9], [3, 3, 9]], out: 'south', dress: 'shutters', until: 2 },
    { name: 'kitchen back', cells: [[7, 2, 9], [7, 3, 9]], out: 'south', dress: 'shutters', until: 2 },
    { name: 'hall east', cells: [[9, 2, 7], [9, 3, 7]], out: 'east', dress: 'shutters', until: 1 },
    { name: 'chamber front', cells: [[5, 6, 1], [5, 7, 1]], out: 'north', dress: 'shutters' },
    { name: 'chamber front west', cells: [[2, 6, 1], [2, 7, 1]], out: 'north' },
    { name: 'chamber front east', cells: [[8, 6, 1], [8, 7, 1]], out: 'north' },
    { name: 'chamber west', cells: [[1, 6, 7], [1, 7, 7]], out: 'west', dress: 'shutters' },
    { name: 'chamber east', cells: [[9, 6, 3], [9, 7, 3]], out: 'east', dress: 'shutters', until: 1 },
    { name: 'chamber back', cells: [[5, 6, 9], [5, 7, 9]], out: 'south', dress: 'shutters', until: 2 },
    { name: 'parlour front', cells: [[11, 2, 2], [11, 3, 2]], out: 'north', dress: 'sill', pot: 'potted_acacia_sapling', from: 2 },
    { name: 'parlour east', cells: [[12, 2, 5], [12, 3, 5]], out: 'east', from: 2 },
    { name: 'back room west', cells: [[1, 2, 11], [1, 3, 11]], out: 'west', dress: 'shutters', from: 3 },
    { name: 'back room back west', cells: [[3, 2, 13], [3, 3, 13]], out: 'south', from: 3 },
    { name: 'back room back east', cells: [[7, 2, 13], [7, 3, 13]], out: 'south', from: 3 },
  ],
}

// The shipped house's furniture, every piece in its cell; the bedroom's lamp hangs from under the point.
const chains = { '5,8,5': 4 }
export const FURNITURE = SH_FURNITURE.map(f => (f.part === 'hangingLamp' && f.pos.join() in chains ? { ...f, chain: chains[f.pos.join()] } : f))

export const OUTSIDE = {
  finial: [5, 14, 5], // a clay pot on the thatch's point
  pots: {
    1: [[1, 0, 0, 'potted_acacia_sapling'], [9, 0, 0, 'potted_dead_bush'], [0, 0, 9, 'potted_dead_bush'], [10, 0, 9, 'potted_acacia_sapling']],
    2: [[11, 1, 1, 'potted_red_tulip']],
    3: [[0, 0, 13, 'potted_acacia_sapling'], [10, 0, 13, 'potted_dead_bush']],
  },
  hay: { 2: [[12, 1, 1]] }, // a hay bale to sit on in the veranda's shade
  doorLamp: [5, 3, 0], // under the thatch hood over the door
  verandaLamp: [11, 4, 0],
}
