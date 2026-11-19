// Daub House, the DESERT look (looks/desert/house_t1_b, ROADMAP 49.10d, as 49.10a): the same building as the shipped
// Daub House (../house_t1_b/plan.mjs: its hall, kitchen and loft, the four beds, the kitchen's chest, furnace and
// crafting table, the loft's chest, the front door, the joined loft railing and every furniture spot in the same cell,
// the same 13 x 14 x 13 plot), drawn again for a hot, dry village. It speaks the desert village's language (the Starter
// Cottage, Stone House and Terrace looks): mud brick and packed mud over a sandstone foot, smooth-sandstone dressings,
// flat roofs behind a level coping, spruce beam ends, dark-oak lattice for shade. "Daub" is mud:
// here the whole house is mud, the way a desert family builds with what the riverbank gives.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The courtyard house": the hall, the kitchen and the sleeping room as three sides of a walled court with a well.
//     The truest desert plan, but the brief pins the bedroom over the hall and the kitchen on its east side: there is
//     no fourth side to close, and a court wall in front would hide the one front door the street must see.
//  2. "The domed house": the hall under a mud dome (Harran's beehive houses), the kitchen under a smaller one. A
//     wonderful outline, but the four beds stand in the loft at y 5 over a 5 x 9 floor: a dome round that floor is
//     too tall for the 14-high plot, and a round shell can't hold the square loft.
//  3. "The Kasbah" (a tighremt of the Draa and Dades valleys): the hall a two-storey block of mud with a square TOWER at
//     each of its four corners standing a storey's half above a level coped parapet, each tower capped in a little
//     stepped crown; a blank street front broken only by a deep arched door niche and a dark-oak lattice oriel over
//     it; beam ends (toron); a cut-sandstone course tying the towers; the kitchen beside it under a low NUBIAN VAULT of mud
//     brick with a flue, the hearth's stack up the back between the back towers. Story: a family on a caravan road
//     builds a little fortified home: thick walls for the heat, towers for the corners, a vault because there is no
//     timber long enough for a roof.
//  Built: 3. It is the strongest: the four corner towers give a shape you know in outline from every side (no other
//  desert look has them: the Terrace and Stone House carry windcatchers and rawshans), the vault is a second, lower,
//  rounder mass beside the tall block (the brief's low kitchen wing), and every face has a proud corner and a recess.
//
// --- Design notes: what is done, what is left ------------------------------------------------------------------------
//  Done (2026-11-19): saved as a candidate (build.sh -> build/rebuild/house_t1_b_desert/house_t1_b.nbt, 1,073 blocks).
//  Checked: look_spots.py house_t1_b_desert house_t1_b: SAME BUILDING (40 shipped spots, 0 missing, 0 beds, containers,
//    job blocks or doors moved; only the lamps' chains differ: the loft's hang straight from the beamed ceiling, the
//    kitchen's 2 links from the vault's crown); rebuild.py: no CHECK line; holes.py (open included): 0; every block id
//    vanilla 1.21.1; the only fences at y 4 or above are the loft railing's five (spruce, its corner (5, 5, 5) joined
//    east and south); the one standing lamp is the shipped one on its side table.
//  Renders: build/rebuild/house_t1_b_desert/renders/ (desert_<front|back|left|right|top>.png; cutaways desert_cut3_* for
//    the hall and kitchen, desert_cut7_* for the loft).
//  Tried and changed: a terracotta-and-sandstone frieze at the deck read as a striped awning: one cut-sandstone course
//    now runs from tower to tower; sills and brows round every window read as floating slabs: sills only; stripped-log
//    toron at the ceiling read as crates: spruce-fence beam ends under the loft floor (y 3, below the railing's height);
//    a date palm behind the kitchen made roof pits with the vault and the towers (holes.py): cacti in a sand strip.
//  Open for the critic: the vault reads stepped (a 45-degree section, the way every Minecraft vault does), its east end
//    rimmed in pale stone to read as an arch; the four tower crowns are four stairs each (finish() makes little stepped
//    caps); the west face between the towers is the plainest (one hall window, two loft windows, the toron, the batter).
//
// --- Named parts (x across the front, west 0 -> east 12; z from the front, z = 0, to the back) ---------------------
//   Hall      the main block: walls x 1..7, z 1..11; the ground storey 2 thick at the front (z 1..2) and back (z 10..11),
//             the upper storey y 5..8 on the ring x 1 / x 7 / z 1 / z 11, a beamed ceiling y 8, the roof deck y 9.
//   Towers    four 2 x 2 towers at the corners (x 0..1 / 7..8, z 0..1 / 11..12), y 0..12, a sandstone foot y 0..2, cut
//             courses at y 4 and y 9, a stepped crown of four stairs at y 13.
//   Parapet   y 10 on the roof's rim between the towers, a smooth-sandstone coping at y 11 (level all round).
//   Course    y 9 on every face between the towers: cut sandstone (the deck's course), level with the towers' own.
//   Toron     spruce-fence beam ends out of the west wall at y 3 (under the loft floor), either side of the hall window.
//   Niche     the arched door niche x 3..5 on z 1 (the door at (4, 1, 2) behind it), a lantern in its arch.
//   Oriel     a lattice oriel x 3..5 on z 0, y 4..7, on corbels, over the niche (the loft's front window behind it).
//   Stack     the hearth's chimney breast x 3..5 on z 12 up the back, the flue x 4 to y 12, smoking at y 13.
//   Vault     the kitchen: mud walls x 8..11, z 3..9 (y 1..4) under a cut course, a barrel vault along x (y 5..7, crown
//             z 6), its east end a lunette rimmed in smooth sandstone, a flue smoking on its crown.
//   Ground    a sandstone plinth and a batter skirt (stairs) along the side walls, cacti in sand behind the kitchen, jars.
export { DAUB_FURNITURE as FURNITURE } from '../parts/daub_looks.mjs'

export const SIZE = [13, 14, 13] // as shipped

// Palette 60/30/10: mud brick and packed mud walls (60) / sandstone: the foot, the towers' courses, the coping, sills and
// dressings (30) / dark-oak lattice, spruce beams and doors, terracotta jars (10). Furniture keeps the shipped spruce.
export const PALETTE = {
  mud: [['mud_bricks', 6], ['packed_mud', 2]],
  foot: [['sandstone', 5], ['cut_sandstone', 2], ['smooth_sandstone', 1]],
  plinth: 'smooth_sandstone', course: 'cut_sandstone', coping: 'smooth_sandstone_slab', dressed: 'smooth_sandstone',
  carved: 'chiseled_sandstone', arch: 'smooth_sandstone_stairs', batter: 'sandstone_stairs', crown: 'smooth_sandstone_stairs',
  sill: 'smooth_sandstone_slab',
  deck: 'packed_mud', ceiling: 'stripped_spruce_log', toron: 'spruce_fence', // toron under y 4: the loft railing is the only fence above
  vault: 'mud_bricks', vaultStairs: 'mud_brick_stairs', vaultSlab: 'mud_brick_slab',
  lattice: 'dark_oak_trapdoor', latticePost: 'dark_oak_log', latticeRoof: 'dark_oak_slab', corbel: 'smooth_sandstone_stairs',
  floor: 'smooth_sandstone', upperFloor: 'spruce_planks', kitchenFloor: 'packed_mud', under: 'mud_bricks',
  step: 'sandstone_stairs', glass: 'glass_pane',
}

export const MASSES = {
  hall: { x0: 1, x1: 7, z0: 1, z1: 11, groundTop: 3, floorY: 4, upperTop: 7, ceilingY: 8, deckY: 9, parapetY: 10, copingY: 11 },
  towers: { at: [[0, 0], [7, 0], [0, 11], [7, 11]], top: 12, foot: 2, courses: [4, 9], crownY: 13 },
  niche: { x0: 3, x1: 5, z: 1, archY: 3 },
  oriel: { x0: 3, x1: 5, z: 0, y0: 4, y1: 7 },
  toron: { y: 3, z: [3, 5, 7, 9] }, // beam ends out of the west wall, under the loft floor, either side of the hall's window
  stack: { x: 4, z: 12, breast: [3, 5], breastTop: 7, top: 12 },
  kitchen: { x0: 8, x1: 11, z0: 3, z1: 9, wallTop: 4, vaultY: 5, crownY: 6, flue: [9, 8] },
}

// Windows: glass in the wall's plane, a smooth-sandstone sill under and a brow over each (every one dressed alike).
export const WINDOWS = [
  { name: 'hall west', cells: [[1, 2, 6], [1, 3, 6]], out: 'west' },
  { name: 'loft front', cells: [[4, 5, 1], [4, 6, 1]], out: 'north', dress: 'oriel' },
  { name: 'loft west front', cells: [[1, 5, 4], [1, 6, 4]], out: 'west' },
  { name: 'loft west back', cells: [[1, 5, 8], [1, 6, 8]], out: 'west' },
  { name: 'loft back west', cells: [[2, 5, 11], [2, 6, 11]], out: 'south' },
  { name: 'loft back east', cells: [[6, 5, 11], [6, 6, 11]], out: 'south' },
  { name: 'loft east front', cells: [[7, 7, 4]], out: 'east', dress: null }, // over the vault: little clerestory panes
  { name: 'loft east back', cells: [[7, 7, 8]], out: 'east', dress: null },
  { name: 'kitchen front', cells: [[10, 3, 3]], out: 'north' },
  { name: 'kitchen east', cells: [[11, 2, 6], [11, 3, 6]], out: 'east' },
  { name: 'kitchen back', cells: [[10, 3, 9]], out: 'south' },
]

export const OUTSIDE = {
  garden: [[10, 11, 'cactus'], [11, 11, 'dead_bush'], [12, 10, 'cactus']],
  jars: [[2, 0, 0], [6, 0, 0], [12, 0, 3]],
  step: { at: [4, 0, 0], id: 'sandstone_stairs' },
}
