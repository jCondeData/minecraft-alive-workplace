// Burgher's Townhouse, the DESERT look (looks/desert/townhouse[_n], ROADMAP 49.10b, as 49.10a): the same house as the
// shipped Townhouse (../townhouse/plan.mjs: its rooms, beds, job blocks, containers, doors, both flights, the tower's
// ladder and every furniture spot in the same cell at every level, the same 4-deep vault under the shop in IV), drawn
// again for a hot, dry town. It speaks the desert village's language (coursed sandstone below, mud brick above, a
// terracotta frieze at the floor line, flat roofs behind a smooth coping, spruce beam ends, arcades and lattice for
// shade), but it is the town's grand merchant house, so it is the tallest home and has what no other home has.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Tower house" (Shibam): the shipped mass as a sheer mud-brick tower block, white-limed at the top, tiny windows,
//     the wing and the strongroom two more towers. Tall and rich, but three sheer blocks side by side is the keep and
//     turret outline the critics sent back on the Stone House, and nothing shades the street.
//  2. "Courtyard merchant house" (a riad): the shop, counting house and strongroom as three sides round a court with a
//     fountain. The truest plan, but the brief pins the rooms (the shop and kitchen in one 9 x 11 block, the wing beside
//     it, the tower behind): there is no court to wall in at I or II, only at III, behind.
//  3. "The Caravan Merchant's House" (Cairo, Jeddah, Yazd): Level I a three-storey house, the shop behind a street
//     ARCADE of two round arches and a carved door bay, the hall's whole front a dark lattice RAWSHAN out over the street
//     on corbels, the family's top floor under a flat roof from which a QA'A LANTERN rises (a clerestory over the top
//     hall: its two hung lamps hang in it). II adds the counting house as a lower block whose street front is a LOGGIA of
//     three narrow arches, its roof a terrace with a date palm; III the strongroom tower behind it, crowned by a tall
//     slotted WINDCATCHER (a badgir), and the back yard walled into a COURT with a basin and two palms; IV the vault
//     under the shop. Story: the merchant shades his shop with an arcade and his hall with a lattice; each fortune
//     adds a cooler room: the loggia, then the tower that pulls the wind down through the house.
//  Built: 3. It is the strongest: from 20 blocks it is a tall sandstone house with a lattice face you can name, its
//  arcade running on into the loggia as one street front, and the windcatcher (III) the town's tallest thing, a hero
//  that reads as a cooling tower (fins and slots, a cornice, no crenels), not a keep. Each level reads as the same
//  family: shop, counting house, tower, vault, as shipped.
//
// --- Design notes: what is done, what is left (2026-11-18) -----------------------------------------------------------
//  Done: Levels I-IV as candidates (build.sh -> build/rebuild/townhouse_desert/). Every furniture spot of the shipped
//  Townhouse in the same cell at every level (look_spots.py: SAME BUILDING, 38/54/65/71 spots). Under flat roofs the hung
//  lamps take shorter chains: the top floor's two hang in the lantern (3 and 2 links), the wing bedroom's from its deck.
//  Checked: rebuild.py no CHECK line at any level; holes.py 0 (with open); check.py's owner rules 0; the kit's
//  seal_slots changes nothing; II keeps 97 % of I, III 99 % of II; I 55 stacks, II 72, III 85 (of 108).
//  Renders (front, back, left, right, top per level): build/rebuild/townhouse_desert/renders/.
//  Tried and changed: a three-deep loggia read to holes.py as a room open to the street (now two deep, a soffit behind
//  the arch heads); brows over the wing's terrace windows met as a roof gap (lattice screens instead); slab brows and
//  sills on every window read as loose ledges (sills on the ground storey only, lattice on the hall storey and the
//  tower, plain lights under the coping); corner pilasters at the back read as stumps round the stack (gone); a red
//  sandstone moulding read as a loose orange ledge (mud brick); a moulding round the tower's foot and a palm crown
//  reaching the tower made pits in the court (none there); a terrace palm's fronds out over the street roofed the
//  loggia (they stop at its line). Vanilla has no terracotta stairs (the Stone House's desert look uses them: a bug for
//  that look's ship step).
//  Open for the critic: the court's palms are compact (a big crown over a walled court splits its sky into pits for
//  holes.py); the windcatcher is the town's tallest thing by design.
//  Round 2 (the critic's round 1, BACK 4/4/4/3, briefs/townhouse_looks_critic.md): 1 the stack one square breast up to
//        the frieze, no stair steps, the back moulding stopping two short of it; 2 the court wall's ends and the gate
//        capped piers (no stub by the house); 3 the tower banded in mud brick at its floors, a latticed window a
//        storey on its east face; 4 the palms two taller, with long fronds drooping at their tips; 5 the pots off
//        the plot's corners: in the arcade, by the door, against the walls.
//  Left: the critic's pass (REBUILD.md) against the shipped Townhouse; the Java side (49.10b ship step).
//
// --- Named parts (template coordinates: x across the front, west 0 -> east; z from the front, z = 0, to the back) ---
//   House      ground storey x 1..9, z 2..12, y 1..4 (coursed sandstone); the floor line y 5 (a terracotta frieze, a
//              proud moulding on the sides and back); the upper storeys x 1..9, z 1..12 in mud brick: the hall and
//              chamber y 6..8, a cut-sandstone course at y 9 (the top floor's floor), the top floor y 10..12, the deck at
//              y 13, a smooth coping at y 14.
//   Arcade     the street front of the shop on z 1 under the upper storeys: piers at x 1, 4, 6 and 9, round arches over
//              x 2..3 and 7..8, the carved door bay x 5 (a keystone, a hung lantern).
//   Rawshan    the hall's front, z 0, y 5..9: dark-oak posts over the piers, open lattice in front of the windows.
//   Lantern    the qa'a lantern on the roof, x 3..7, z 3..10, walls y 14..15, its ceiling and coping y 16; the deck under
//              it open, so the top floor rises into it.
//   Stack      the kitchen hearth's flue up the back wall at x 5, z 13, smoking over the coping.
//   Wing       II: the counting house x 10..16, z 4..12 (sandstone storey, mud bedroom storey), deck y 10, coping y 11;
//              the loggia x 10..16, z 2..3 (piers at x 10, 12, 14, 16 on z 2, a block back from the arcade), its roof a
//              terrace at y 5 with a parapet and a date palm in a planter.
//   Tower      III: x 13..17, z 13..17, sandstone, floors y 5, 9, 13, the lookout's ceiling y 18 under a frieze; the
//              windcatcher's head y 19..22 (fins and slots), the cap and its cornice y 23.
//   Court      III: the yard behind the house walled in (x 0, z 13..17; z 17, x 0..12), a basin of water and two palms.
//   Vault      IV: under the shop as shipped (x 1..9, z 2..12, 4 deep, the hatch at (2, 0, 7), ribs at z 5 and 9).
import {
  IDS, BASEMENT, MASSES as TH_MASSES, OPENINGS as TH_OPENINGS, FURNITURE as TH_FURNITURE,
} from '../townhouse/plan.mjs'

export { IDS, BASEMENT }
export const LEVELS = [1, 2, 3, 4]
export const SIZES = { 1: [11, 20, 14], 2: [18, 20, 14], 3: [19, 26, 19], 4: [19, 26, 19] } // shipped: 11x22x14, 18x22x14, 19x26x19

// Palette 60/30/10: coursed sandstone ground storey and tower, mud brick upper storeys (60) / smooth and cut sandstone
// dressings: plinth, piers, arches, coping, brows, the windcatcher's fins (30) / a terracotta frieze, the dark-oak
// rawshan, spruce beam ends and joinery, palms and flowers (10). Furniture keeps the shipped house's spruce and dark oak.
// (No terracotta stairs: vanilla has none; the proud moulding under the frieze is mud brick, a bright red sandstone one
// read as a loose orange ledge.)
export const PALETTE = {
  ashlar: [['sandstone', 6], ['cut_sandstone', 3], ['smooth_sandstone', 1]],
  mud: [['mud_bricks', 7], ['packed_mud', 2]],
  plinth: 'smooth_sandstone', dressed: 'smooth_sandstone', pier: 'cut_sandstone', carved: 'chiseled_sandstone',
  course: 'cut_sandstone', band: 'mud_bricks', frieze: 'terracotta', moulding: 'mud_brick_stairs', brow: 'smooth_sandstone_slab',
  sillStair: 'smooth_sandstone_stairs', arch: 'smooth_sandstone_stairs', step: 'sandstone_stairs',
  deck: 'smooth_sandstone', floor: 'smooth_sandstone', upperFloor: 'spruce_planks', partition: 'spruce_planks',
  viga: 'spruce_fence', wood: 'spruce', glass: 'glass_pane',
  lattice: 'dark_oak_trapdoor', latticePost: 'dark_oak_log', latticeSill: 'dark_oak_slab', latticeRoof: 'dark_oak_stairs',
  cornice: 'smooth_sandstone_stairs',
  palm: { soil: 'rooted_dirt', trunk: 'jungle_log', leaves: 'jungle_leaves' },
  cellarWall: [['sandstone', 5], ['smooth_sandstone', 2], ['cut_sandstone', 1]], cellarFloor: 'smooth_sandstone',
}

export const MASSES = {
  house: { x0: 1, x1: 9, z0: 2, zUp: 1, z1: 12, firstY: 5, courseY: 9, deckY: 13 },
  arcade: { z: 1, piers: [1, 4, 6, 9], arches: [[2, 3], [7, 8]], door: 5 },
  rawshan: { x0: 1, x1: 9, z: 0, y0: 5, y1: 9, posts: [1, 4, 6, 9] },
  lantern: { x0: 3, z0: 3, x1: 7, z1: 10, y0: 14, y1: 16, windows: [[3, 5], [3, 8], [7, 5], [7, 8], [5, 3], [5, 10]] },
  stack: { x: 5, z: 13, top: 15 },
  wing: { x0: 10, x1: 16, z0: 4, z1: 12, deckY: 10,
    loggia: { x0: 10, x1: 16, z0: 2, z1: 3, piers: [10, 12, 14, 16], deckY: 5, side: null } },
  tower: { x0: 13, x1: 17, z0: 13, z1: 17, floors: [5, 9, 13], deckY: 18, head: [19, 22],
    ladder: TH_MASSES.tower.ladder },
  court: { wallY: 2, west: 0, back: 17, gate: [0, 15], basin: { x0: 4, x1: 8, z0: 14, z1: 16 }, palms: [[2, 15], [9, 15]] },
  cellar: TH_MASSES.cellar,
}

// The shipped house's openings that hold the rooms together (the doors, both flights, the partition, the doorways
// knocked through in II and III), cell for cell; the windows are this look's own (small, under stone brows), kept
// wherever the shipped house rolls a blind at a window's head.
export const OPENINGS = {
  frontDoor: TH_OPENINGS.frontDoor, wingDoor: TH_OPENINGS.wingDoor, stairs: TH_OPENINGS.stairs, attic: TH_OPENINGS.attic,
  partition: TH_OPENINGS.partition, doorways: TH_OPENINGS.doorways,
  windows: [
    // house, the shop front: behind the arcade, no brows
    { cells: [[2, 2, 2], [3, 2, 2], [2, 3, 2], [3, 3, 2]], out: 'north', brow: false },
    { cells: [[7, 2, 2], [8, 2, 2], [7, 3, 2], [8, 3, 2]], out: 'north', brow: false },
    { cells: [[1, 2, 5], [1, 3, 5]], out: 'west' },
    { cells: [[1, 2, 9], [1, 3, 9]], out: 'west' },
    { cells: [[9, 2, 10], [9, 3, 10]], out: 'east', until: 1 },
    { cells: [[3, 2, 12], [3, 3, 12]], out: 'south' },
    { cells: [[7, 2, 12], [7, 3, 12]], out: 'south' },
    // the hall and chamber: the front's behind the rawshan's lattice (the blinds roll at their heads)
    { cells: [[2, 6, 1], [3, 6, 1], [2, 7, 1], [3, 7, 1]], out: 'north', brow: false },
    { cells: [[5, 6, 1], [5, 7, 1]], out: 'north', brow: false },
    { cells: [[7, 6, 1], [8, 6, 1], [7, 7, 1], [8, 7, 1]], out: 'north', brow: false },
    { cells: [[1, 6, 3], [1, 7, 3]], out: 'west', screen: true },
    { cells: [[1, 6, 6], [1, 7, 6]], out: 'west', screen: true },
    { cells: [[1, 6, 10], [1, 7, 10]], out: 'west', screen: true },
    { cells: [[9, 6, 3], [9, 7, 3]], out: 'east', screen: true },
    { cells: [[9, 6, 6], [9, 7, 6]], out: 'east', until: 1, screen: true },
    { cells: [[9, 6, 10], [9, 7, 10]], out: 'east', until: 1, screen: true },
    { cells: [[3, 6, 12], [3, 7, 12]], out: 'south', screen: true },
    { cells: [[7, 6, 12], [7, 7, 12]], out: 'south', screen: true },
    // the top floor
    { cells: [[3, 11, 1], [3, 12, 1]], brow: false, out: 'north' },
    { cells: [[7, 11, 1], [7, 12, 1]], brow: false, out: 'north' },
    { cells: [[1, 11, 4], [1, 12, 4]], brow: false, out: 'west' },
    { cells: [[1, 11, 10], [1, 12, 10]], brow: false, out: 'west' },
    { cells: [[9, 11, 4], [9, 12, 4]], brow: false, out: 'east', until: 1 }, // II's wing parapet stands against them
    { cells: [[9, 11, 9], [9, 12, 9]], brow: false, out: 'east', until: 1 },
    { cells: [[3, 11, 12], [3, 12, 12]], brow: false, out: 'south' },
    { cells: [[7, 11, 12], [7, 12, 12]], brow: false, out: 'south' },
    // II, the counting house (its street windows in the loggia) and the wing bedroom
    { cells: [[11, 2, 4], [11, 3, 4]], out: 'north', brow: false, from: 2 },
    { cells: [[15, 2, 4], [15, 3, 4]], out: 'north', brow: false, from: 2 },
    { cells: [[13, 6, 4], [13, 7, 4]], out: 'north', from: 2, screen: true },
    { cells: [[11, 6, 4], [11, 7, 4]], out: 'north', from: 2, screen: true },
    { cells: [[15, 6, 4], [15, 7, 4]], out: 'north', from: 2, screen: true },
    { cells: [[16, 2, 8], [16, 3, 8]], out: 'east', from: 2 },
    { cells: [[16, 6, 8], [16, 7, 8]], out: 'east', from: 2, screen: true },
    { cells: [[12, 2, 12], [12, 3, 12]], out: 'south', from: 2, brow: false }, // in III's court corner: a brow there read as a roof over a pit
    { cells: [[12, 6, 12], [12, 7, 12]], out: 'south', from: 2, brow: false },
    // III, the tower: a window a storey on its open faces, the lookout's on three sides
    { cells: [[15, 2, 17], [15, 3, 17]], out: 'south', from: 3, screen: true },
    { cells: [[17, 2, 15], [17, 3, 15]], out: 'east', from: 3, screen: true }, // round 2: the east face a window a storey
    { cells: [[17, 10, 15], [17, 11, 15]], out: 'east', from: 3, screen: true },
    { cells: [[17, 6, 15], [17, 7, 15]], out: 'east', from: 3, screen: true },
    { cells: [[15, 10, 17], [15, 11, 17]], out: 'south', from: 3, screen: true },
    { cells: [[13, 10, 15], [13, 11, 15]], out: 'west', from: 3, screen: true },
    { cells: [[13, 14, 15], [13, 15, 15]], out: 'west', from: 3, screen: true },
    { cells: [[17, 14, 15], [17, 15, 15]], out: 'east', from: 3, screen: true },
    { cells: [[15, 14, 17], [15, 15, 17]], out: 'south', from: 3, screen: true },
  ],
}

// The shipped house's furniture, every piece in its cell; the hung lamps take the chain their flat ceiling gives them.
const chains = { '5,13,4': 3, '5,14,9': 2, '13,9,8': 0 }
export const FURNITURE = TH_FURNITURE.map(f => (f.part === 'hangingLamp' && f.pos.join() in chains ? { ...f, chain: chains[f.pos.join()] } : f))

export const OUTSIDE = {
  pots: {
    // round 2: off the plot's corners, against the walls, in the arcade and by the door
    1: [[3, 1, 1, 'potted_cactus'], [7, 1, 1, 'potted_cactus'], [4, 0, 0, 'potted_orange_tulip'], [6, 0, 0, 'potted_orange_tulip'],
      [0, 0, 3, 'potted_dead_bush'], [0, 0, 11, 'potted_cactus'], [10, 0, 6, 'potted_cactus'], [3, 0, 13, 'potted_dead_bush'], [8, 0, 13, 'potted_cactus']],
    2: [[17, 0, 6, 'potted_cactus'], [17, 0, 10, 'potted_dead_bush'],
      [11, 6, 3, 'potted_orange_tulip'], [13, 6, 3, 'potted_flowering_azalea_bush']],
    3: [[18, 0, 14, 'potted_dead_bush'], [18, 0, 16, 'potted_cactus'], [3, 1, 13, 'potted_orange_tulip'], [11, 1, 13, 'potted_red_tulip']],
  },
  jars: { 1: [[2, 1, 1], [8, 1, 1]], 2: [[11, 1, 3], [15, 1, 3]], 3: [[1, 1, 16], [12, 1, 16]] },
  vigas: { front: [2, 5, 8], west: [3, 6, 9], east: [3, 6, 9], back: [2, 8], wingEast: [6, 10], wingBack: [] },
  terracePalm: [15, 6, 3],
}
