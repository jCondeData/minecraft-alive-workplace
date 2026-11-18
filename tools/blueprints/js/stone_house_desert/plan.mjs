// Stone House, the DESERT look (looks/desert/stone_house[_n], ROADMAP 49.10b, as 49.10a): the same house as the shipped
// Stone House (../stone_house/plan.mjs: its rooms, beds, job blocks, containers, stair and every furniture spot in the
// same cell at every level, the same 4-deep cellar under the hall in IV), drawn again for a hot, dry village. It speaks
// the desert village's language the Starter Cottage's look set (mud brick, sandstone dressings, a terracotta frieze,
// flat roofs behind parapets, spruce beam ends, arcades for shade), but it is the village's STONE house: walls of
// coursed sandstone, a slotted windcatcher over the bedroom, a lattice oriel over the door.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Sandstone mason's house": the shipped house's gable and catslide in sandstone and smooth-sandstone slabs.
//     Safe (every cell as drawn), but it is a palette swap, and a pitched roof where it never rains is the tell.
//  2. "Courtyard house": the hall, the parlour (II) and the back room (III) as three sides of a walled court with a
//     fig tree, rooms facing in. The truest desert plan, but the brief pins the rooms in a 13 x 14 plot round the
//     hall: there is no court to wall without moving rooms, and Level I would be a wall round nothing.
//  3. "The Windcatcher House": Level I is a two-storey cube of coursed sandstone under a flat roof behind a coped
//     parapet, a terracotta frieze at the floor line, spruce beam ends at the roof line. Its hero is a WINDCATCHER, a
//     slotted square tower rising from the back corner over the bedroom, half again the house's height; its face is
//     a carved portal with a dark lattice ORIEL over the door (the bedroom's window looks out through it). Level II
//     adds the parlour as a lower mud-brick block, its front an arcaded LOGGIA of two round arches; Level III the back
//     room as a lower mud-brick block whose roof is a TERRACE under a pergola, a door out from the bedroom; IV the
//     cellar under the hall in sandstone. Story: a caravan trader built the stone house and his family added the
//     mud-brick rooms round it as it grew, the way desert houses grow.
//  Built: 3. It is the strongest: from 20 blocks it is a cube with a tower you can name (no other home in the village
//  has a windcatcher), the oriel and the arches give the front deep shade and a carved face, and each level visibly
//  grows out of the last in cheaper material, stepping down from the stone core. Not the safest: no pitched roof at
//  all, and a tower that out-tops everything in a desert village.
//
// --- Design notes: what is done, what is left (2026-11-18) -----------------------------------------------------------
//  Done: Levels I-IV as candidates (build.sh -> build/rebuild/stone_house_desert/). Every furniture spot of the
//  shipped Stone House in the same cell at every level (tools/blueprints/look_spots.py); the three hung lamps under a flat roof
//  hang on shorter chains (the bedroom's, the parlour's and the back room's 0, from their flat ceilings).
//  Checked: rebuild.py no CHECK line at any level; holes.py 0 (with open); check.py's owner rules 0; II keeps 100 % of
//  I and places 18 %, III keeps 98 % of II and places 20 % (shipped: 98/13, 94/19); Level I about 45 stacks, III 57.
//  Renders (front, back, left, right, top per level, IV's cutaways at y -1, 3, 7): build/rebuild/stone_house_desert/renders/.
//  Open for the critic: the back room's back
//  wall is the plot's edge, so its windows have no brows; the main roof is a bare deck (no way up, so nothing on it).
//  Round 2 (the critic's notes, briefs/stone_house_looks_critic.md, round 1 BACK 4/3/4/3): 1 the walls given depth:
//        proud ground-storey corner pilasters (cut sandstone, smooth caps), the frieze carried round as a proud
//        terracotta moulding, a proud sill under every side and back window below its brow; 2 a home, not a watchtower:
//        the upper storey in mud brick over the stone ground storey (the cottage's material), a shallow arcade of two
//        round arches along the front either side of the door's bay, jars and pots in its shade; 3 the door's bay
//        carved full height in chiseled sandstone, its step, the lantern and the oriel over it; 4 the flue one stack
//        with a breast stepped in at y 4, the moulding kept clear of it, no pilaster beside it; 5 the beam ends thin
//        spruce poles (fences) instead of logs, more planting (cactus and dead bush at every corner and side, tulips in
//        the arcade, an azalea on the terrace). Full-height corner piers were tried and read as a keep's towers.
//  Left: the critic's pass (REBUILD.md) against the shipped Stone House; the Java side (49.10b ship step).
//
// Coordinates as the shipped house: x across the front (west 0 -> east), y up, z from the front (z = 0) to the back.
//   House     walls x 1..9, z 1..9; ground floor y 1..4, the upper floor at y 5 (a terracotta frieze outside), the
//             bedroom y 6..8, the roof deck at y 9, the parapet y 10 with a smooth coping course at y 11.
//   Portal    piers at x 4 and 6 on z 0 round the door, a lintel course at y 5, the oriel x 4..6 at z 0 above (y 5..8).
//   Flue      the hearth's stack on the west wall at x 0, z 3 (breast z 2..4), its campfire over the coping.
//   Tower     the windcatcher x 1..3, z 7..9 from the parapet (y 10) to its cap at y 15, slots at y 12..14.
//   Parlour   II: walls x 9..12, z 2..8 in mud brick, deck y 6, parapet y 7, coping y 8 (a full smooth course: a slab lip read as a roof round a pit to holes.py); the loggia x 9..12, z 0..1:
//             piers at x 9 and 12 on z 0, two round arches, its roof deck at y 5.
//   Back room III: walls x 1..9, z 9..13 in mud brick, deck y 6, parapet y 7, coping y 8 (a full smooth course: a slab lip read as a roof round a pit to holes.py); the roof a terrace under a
//             pergola of fence rails (posts at the back corners, rails from the house's back wall), its door (5, 7, 9) up a step
//             from the bedroom at (5, 6, 8).
//   Cellar    IV: as the shipped house's (x 1..9, z 1..9, 4 deep, the ladder at (2, z 5), the beam along z 3 on a post).
import {
  IDS, BASEMENT, MASSES as SH_MASSES, OPENINGS as SH_OPENINGS, FURNITURE as SH_FURNITURE,
} from '../stone_house/plan.mjs'

export { IDS, BASEMENT }
export const LEVELS = [1, 2, 3, 4]
export const SIZES = { 1: [11, 16, 11], 2: [13, 16, 11], 3: [13, 16, 14], 4: [13, 16, 14] } // as shipped

// Palette 60/30/10: coursed sandstone house (60; mud brick for the later rooms) / smooth sandstone dressings: plinth,
// coping, piers, brows, the tower's cap (30) / a terracotta frieze, spruce beam ends and pergola, a dark-oak lattice (10).
// Furniture keeps the shipped house's spruce and dark oak (every spot's wood as shipped).
export const PALETTE = {
  ashlar: [['sandstone', 6], ['cut_sandstone', 3], ['smooth_sandstone', 1]],
  mud: [['mud_bricks', 7], ['packed_mud', 2]],
  plinth: 'smooth_sandstone', coping: 'smooth_sandstone_slab', dressed: 'smooth_sandstone', pier: 'cut_sandstone',
  carved: 'chiseled_sandstone', frieze: 'terracotta', brow: 'smooth_sandstone_slab', step: 'sandstone_stairs',
  arch: 'smooth_sandstone_stairs', deck: 'smooth_sandstone', floor: 'smooth_sandstone', upperFloor: 'spruce_planks',
  viga: 'spruce_fence', beam: 'stripped_spruce_log', moulding: 'terracotta_stairs', sillStair: 'smooth_sandstone_stairs', lattice: 'dark_oak_trapdoor', latticePost: 'dark_oak_log', latticeRoof: 'dark_oak_slab',
  wood: 'spruce', glass: 'glass_pane', partition: 'spruce_planks',
  cellarWall: [['sandstone', 5], ['smooth_sandstone', 2], ['cut_sandstone', 1]], cellarFloor: 'smooth_sandstone',
}

export const MASSES = {
  house: { x0: 1, z0: 1, x1: 9, z1: 9, upperY: 5, deckY: 9 },
  arcade: { z: 0, piers: [1, 9], arches: [[2, 3], [7, 8]], roofY: 5 }, // round 2: a shallow arcade along the front
  pilasters: [[1, 0], [9, 0], [0, 9], [1, 10], [9, 10], [10, 1], [10, 9]], // proud corners (none at x 0, z 1: the flue's breast)
  portal: { piers: [4, 6], z: 0, top: 4, lintelY: 5, oriel: { x0: 4, x1: 6, z: 0, y0: 5, y1: 8 } },
  flue: { x: 0, z: 3, top: 12, breastTop: 4 },
  tower: { x0: 1, z0: 7, y0: 10, y1: 15, slot: 3 },
  parlour: { x0: 9, z0: 2, x1: 12, z1: 8, deckY: 6, loggia: { x0: 9, x1: 12, z0: 0, z1: 1, piers: [9, 12], deckY: 5 } },
  backRoom: { x0: 1, z0: 9, x1: 9, z1: 13, deckY: 6,
    pergola: { posts: [[2, 12], [8, 12]], rafters: [2, 5, 8], y: 9 }, door: [5, 7, 9], step: [5, 6, 8] },
  cellar: SH_MASSES.cellar,
}

// The shipped house's openings that hold rooms together (the door, the stair, the partition, the doorways knocked
// through in II and III), cell for cell; the windows are this look's own: small, deep, under stone brows.
export const OPENINGS = {
  frontDoor: SH_OPENINGS.frontDoor, stairs: SH_OPENINGS.stairs, partition: SH_OPENINGS.partition,
  wingDoorway: SH_OPENINGS.wingDoorway, backDoorway: SH_OPENINGS.backDoorway,
  windows: [
    { name: 'hall front west', cells: [[3, 2, 1], [3, 3, 1]], out: 'north', brow: false }, // under the arcade
    { name: 'hall front east', cells: [[7, 2, 1], [7, 3, 1]], out: 'north', brow: false },
    { name: 'box room west', cells: [[1, 2, 7], [1, 3, 7]], out: 'west' },
    { name: 'box room back', cells: [[3, 2, 9], [3, 3, 9]], out: 'south', until: 2 },
    { name: 'kitchen back', cells: [[7, 2, 9], [7, 3, 9]], out: 'south', until: 2 },
    { name: 'hall east', cells: [[9, 2, 7], [9, 3, 7]], out: 'east', until: 1 },
    { name: 'chamber front', cells: [[5, 6, 1], [5, 7, 1]], out: 'north', brow: false }, // behind the oriel
    { name: 'chamber front west', cells: [[3, 6, 1], [3, 7, 1]], out: 'north' },
    { name: 'chamber front east', cells: [[7, 6, 1], [7, 7, 1]], out: 'north' },
    { name: 'chamber west', cells: [[1, 6, 5], [1, 7, 5]], out: 'west' },
    { name: 'chamber east', cells: [[9, 6, 4], [9, 7, 4]], out: 'east' },
    { name: 'chamber east back', cells: [[9, 7, 7]], out: 'east' },
    { name: 'chamber back', cells: [[5, 6, 9], [5, 7, 9]], out: 'south', until: 2 }, // III: the terrace door
    { name: 'chamber back west', cells: [[3, 6, 9], [3, 7, 9]], out: 'south' },
    { name: 'parlour front', cells: [[11, 2, 2], [11, 3, 2]], out: 'north', from: 2, brow: false }, // in the loggia
    { name: 'parlour east', cells: [[12, 2, 5], [12, 3, 5]], out: 'east', from: 2 },
    { name: 'parlour back', cells: [[11, 2, 8]], out: 'south', from: 2 },
    { name: 'back room west', cells: [[1, 2, 11], [1, 3, 11]], out: 'west', from: 3 },
    { name: 'back room back west', cells: [[3, 2, 13], [3, 3, 13]], out: 'south', from: 3 },
    { name: 'back room back east', cells: [[7, 2, 13], [7, 3, 13]], out: 'south', from: 3 },
    { name: 'back room east', cells: [[9, 2, 11]], out: 'east', from: 3 },
  ],
}

// The shipped house's furniture, every piece in its cell; the hung lamps take the chain their ceiling gives them.
const chains = { '5,8,5': 0, '10,5,5': 0, '5,5,11': 0 }
export const FURNITURE = SH_FURNITURE.map(f => (f.part === 'hangingLamp' && f.pos.join() in chains ? { ...f, chain: chains[f.pos.join()] } : f))

export const OUTSIDE = {
  // potted desert plants on the plinth's corners and by the portal, water jars (decorated pots) under the windows
  pots: {
    1: [[0, 0, 0, 'potted_cactus'], [10, 0, 0, 'potted_dead_bush'], [0, 0, 10, 'potted_cactus'], [10, 0, 10, 'potted_dead_bush'],
      [0, 0, 6, 'potted_dead_bush'], [10, 0, 5, 'potted_cactus'], [3, 1, 0, 'potted_orange_tulip'], [7, 1, 0, 'potted_orange_tulip']],
    2: [[12, 0, 9, 'potted_dead_bush']],
    3: [[0, 0, 13, 'potted_dead_bush'], [10, 0, 13, 'potted_cactus']],
  },
  jars: { 1: [[2, 1, 0], [8, 1, 0]], 2: [[10, 1, 1]], 3: [] },
  terrace: { pots: [[2, 7, 10, 'potted_cactus'], [8, 7, 10, 'potted_orange_tulip'], [2, 7, 11, 'potted_flowering_azalea_bush']], jars: [[8, 7, 11]] },
  vigas: { front: [2, 8], back: [2, 5, 8], west: [5, 8], east: [2, 5, 8], parlourBack: [] },
}
