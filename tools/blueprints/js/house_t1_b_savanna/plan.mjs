// Daub House, the SAVANNA look (looks/savanna/house_t1_b, ROADMAP 49.10d, as 49.10a): the same building as the shipped
// Daub House (../house_t1_b/plan.mjs: its hall, kitchen and loft, the four beds, the kitchen's chest, furnace and
// crafting table, the loft's chest, the front door, the joined loft railing and every furniture spot in the same cell,
// the same 13 x 14 x 13 plot), drawn again for a hot grassland with a rainy season. It speaks the savanna village's
// language (the Starter Cottage, Stone House and Terrace looks): golden thatch hips with eaves on every side and no
// gable anywhere, grey acacia posts and beams, packed mud walls on a terracotta plinth, a veranda for shade, painted
// trims, tall mud stacks, a clay pot on the peak. "Daub" is mud on a frame: here the frame is grey acacia and the mud is
// the savanna's own.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Two rondavels": the hall and the kitchen as two round mud houses under conical thatch, joined by a covered way.
//     The strongest savanna picture, but the hall's 5 x 7 room, the stair and the loft's square floor can't sit in a
//     circle: it would not be the same building.
//  2. "Sahel mud tower": a two-storey flat-roofed mud house with pinnacles and beam ends (toron). True to the Sahel,
//     but it is the desert look's language (flat roofs, parapets): the two villages would share a face.
//  3. "The Veranda House": a two-storey mud house in a grey acacia frame under ONE great golden thatch hip (eaves on all
//     four sides, a clay pot on its ridge), a thatched veranda across the front on acacia posts at its corners,
//     the front wall set back under it; a painted band under the beam; the kitchen beside it under a
//     LOWER thatch hip that rises from its own eaves to meet the hall's eave (a half-hip against the house); a tall mud
//     stack up the back past the ridge. Story: a herding family builds high and cool: thick mud to hold the night's
//     cool through the day, a deep thatch that sheds the rains, the veranda where the day's work is done in shade.
//  Built: 3. It is the strongest: one big hip over a lower hip, no gable anywhere, reads as savanna from any side and
//  is nothing like the desert look's towers and parapets; the veranda gives the front its shade and depth.
//
// --- Design notes: what is done, what is left ------------------------------------------------------------------------
//  Done (2026-11-19): saved as a candidate (build.sh -> build/rebuild/house_t1_b_savanna/house_t1_b.nbt, 818 blocks).
//  Checked: look_spots.py house_t1_b_savanna house_t1_b: SAME BUILDING (40 shipped spots, 0 missing, 0 beds, containers,
//    job blocks or doors moved; only the lamps' chains differ: the loft's reach the thatch, the kitchen's hangs from its
//    ceiling); rebuild.py: no CHECK line; holes.py (open included): 0; every block id vanilla 1.21.1; the only fences
//    at y 4 or above are the loft railing's five (spruce, its corner (5, 5, 5) joined east and south).
//  Renders: build/rebuild/house_t1_b_savanna/renders/ (savanna_<front|back|left|right|top>.png; cutaways
//    savanna_cut3_* for the hall and kitchen, savanna_cut7_* for the loft).
//  Tried and changed: shutters on the three upper front windows covered the frame's posts and doubled at x 3 and 5:
//    the front's windows stand bare on the veranda's thatch (the sides and back keep shutters and sills).
//  Round 2 (2026-11-19, briefs/house_t1_b_looks_critic.md): 1 every window has shutters OR a sill, never both (the
//    sides and back keep their shutters, the sills under them gone, so the upper west window no longer sits on the lower
//    one's head); 2 the veranda pent stops on its corner posts (0, 3, 0) and (8, 3, 0), no return round the corners;
//    3 the paint band is one continuous course across the front under the veranda (x 1..7, y 3, over the posts too),
//    gone from the side walls. Not done: note 4 (optional, the thatch's colour). Rechecked: look_spots SAME BUILDING
//    (40/40, 0 moved), holes.py 0, no CHECK line (809 blocks). Renders: build/rebuild/house_t1_b_savanna/renders_r2/.
//  Open for the critic: the thatch is the village's bamboo mosaic, as saturated as the Terrace's (the critic's optional
//    note there): no duller vanilla straw has stairs; the west face's mud panels between the posts are plain.
//
// --- Named parts (x across the front, west 0 -> east 12; z from the front, z = 0, to the back) ---------------------
//   House     packed mud walls in a grey acacia frame: the side walls x 1 and 7 from z 1 to 11, the front wall z 2 and
//             the back wall z 10 between them (the door at (4, 1, 2), the hearth at (4, 1, 10)), the back recess filled
//             (a thick mud back); a beam ring at y 4 (acacia), the upper storey y 5..7 on the ring x 1 / x 7 / z 1 / z 11,
//             acacia posts at the corners and between the bays; a terracotta plinth at y 0.
//   Paint     a band at y 3 under the beam across the front (x 1..7), white and orange terracotta in turn.
//   Hip       the house's thatch (bamboo mosaic): a 45-degree hip over x 0..8, z 0..12, eaves y 7, ridge y 11 along z
//             at x 4, a decorated pot standing on its middle.
//   Veranda   a thatch pent across the front (x 0..8, z 0, y 4) stopping on acacia posts at x 0
//             and 8, the beam (y 4, z 1) over the recess, a terracotta deck, a lantern hung under the pent.
//   Wing      the kitchen: mud walls x 8..11, z 3..9 (y 1..5) on its plinth, an acacia beam on top, a ceiling at y 5;
//             its thatch hip from eaves at y 5 rising westward to meet the house's eave (peak y 9 at x 8).
//   Stack     a tall mud stack x 4 on z 12 up the back through the eave to y 12, a breast x 3..5 at its foot.
//   Ground    clay pots on the veranda, hay by the kitchen.
export { DAUB_FURNITURE as FURNITURE } from '../parts/daub_looks.mjs'

export const SIZE = [13, 14, 13] // as shipped

// Palette 60/30/10: 60 packed mud walls; 30 golden thatch (bamboo mosaic); 10 grey acacia frame, terracotta plinth
// and deck, the painted band, mud-brick stack.
export const PALETTE = {
  mud: 'packed_mud', frame: 'acacia_log', plinth: [['terracotta', 5], ['red_terracotta', 2], ['brown_terracotta', 1]],
  paint: ['white_terracotta', 'orange_terracotta'], deck: 'terracotta',
  roof: { full: 'bamboo_mosaic', stairs: 'bamboo_mosaic_stairs', slab: 'bamboo_mosaic_slab' },
  stack: 'mud_bricks', shoulder: 'mud_brick_stairs', pot: 'decorated_pot',
  floor: 'acacia_planks', upperFloor: 'acacia_planks', kitchenFloor: 'mud_bricks', under: 'packed_mud',
  ceiling: 'acacia_planks', wood: 'acacia', glass: 'glass_pane', sill: 'acacia_stairs',
}

export const MASSES = {
  house: { x0: 1, x1: 7, z0: 1, z1: 11, front: 2, back: 10, groundTop: 3, paintY: 3, beamY: 4, upperTop: 7,
    hip: { x0: 0, z0: 0, x1: 8, z1: 12, eaveY: 7 },
    posts: { front: [1, 3, 5, 7], back: [1, 3, 5, 7], sides: [1, 4, 8, 11], door: [3, 5] } },
  wing: { x0: 8, x1: 11, z0: 3, z1: 9, wallTop: 4, beamY: 5,
    hip: { x0: 4, z0: 2, x1: 12, z1: 10, eaveY: 5 } }, // drawn east of the house's wall only: a half-hip against it
  veranda: { x0: 0, x1: 8, z: 0, y: 4, posts: [0, 8], lamp: [4, 3, 0] },
  stack: { x: 4, z: 12, breast: [3, 5], breastTop: 3, top: 12 },
}

// Windows: glass with acacia shutters and an acacia sill on the sides and back; the front's have no shutters (under
// the veranda a sill, over it the thatch itself: the frame's posts stand where shutters would).
export const WINDOWS = [
  { name: 'hall front west', cells: [[2, 2, 2], [2, 3, 2]], out: 'north', dress: 'sill' },
  { name: 'hall front east', cells: [[6, 2, 2], [6, 3, 2]], out: 'north', dress: 'sill' },
  { name: 'hall west', cells: [[1, 2, 6], [1, 3, 6]], out: 'west', dress: 'shutters' },
  { name: 'loft front west', cells: [[2, 5, 1], [2, 6, 1]], out: 'north', dress: null }, // the front's windows stand on the veranda's thatch
  { name: 'loft front', cells: [[4, 5, 1], [4, 6, 1]], out: 'north', dress: null },
  { name: 'loft front east', cells: [[6, 5, 1], [6, 6, 1]], out: 'north', dress: null },
  { name: 'loft west', cells: [[1, 5, 6], [1, 6, 6]], out: 'west', dress: 'shutters' },
  { name: 'loft back west', cells: [[2, 5, 11], [2, 6, 11]], out: 'south', dress: 'shutters' },
  { name: 'loft back east', cells: [[6, 5, 11], [6, 6, 11]], out: 'south', dress: 'shutters' },
  { name: 'kitchen front', cells: [[10, 2, 3], [10, 3, 3]], out: 'north', dress: 'shutters' },
  { name: 'kitchen east', cells: [[11, 2, 6], [11, 3, 6]], out: 'east', dress: 'shutters' },
  { name: 'kitchen back', cells: [[10, 2, 9], [10, 3, 9]], out: 'south', dress: 'shutters' },
]

export const OUTSIDE = {
  step: { at: [4, 0, 0], id: 'mud_brick_stairs' },
  pots: [[1, 1, 0], [7, 1, 0]],
  hay: [[12, 0, 2], [12, 0, 10]],
}
