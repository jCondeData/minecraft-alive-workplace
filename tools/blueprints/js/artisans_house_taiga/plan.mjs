// Artisan's House I-III, the TAIGA look (looks/taiga/artisans_house[_n], ROADMAP 49.10g, "as 49.10a"): the same house
// as the shipped Artisan's House (../artisans_house: its rooms, beds, containers, doors, the stair, the cellar hatch, the
// kiln's two fire mouths and every furniture spot in the same cell at every level, the same size per level, the same
// 4-deep cellar under III, the same job blocks by type and count) drawn again for a potter in the cool spruce forest.
// The taiga village's language (the cottage, stone house, terrace, manor, farmstead, ranch): dark spruce logs notched
// at the corners on a mossy fieldstone footing, broad 45-degree slate roofs with solid verges, log gables, pale
// stripped-spruce bands, fieldstone stacks, firewood under the eaves, ferns, a konek on the ridge.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The Smoke-Kiln Barn": the house a two-storey log izba, gable to the road, its log upper storey jettied over a
//     fieldstone workroom on proud log-end corbels, under a broad 45-degree slate roof with a konek; beside it, under a
//     second, lower slate gable parallel to the house's, an open kiln barn on log posts sheltering a low fieldstone-and-
//     clay tunnel kiln, its fieldstone stack rising straight through the barn's ridge. Story: the forest potter fires
//     black-smoked pots in a kiln that has to live under a roof half the year, so her kiln has a house of its own.
//  2. "The Clay Pech": one log house, the kiln a great clay stove-kiln (a pech) built into the house's east side with
//     a whitewashed breast and a stack up the slope. Warm and true (the northern stove is the house's heart), but the
//     kiln vanishes into the wall: from the road it is the taiga cottage again.
//  3. "The Smithy Court": the potter becomes a smith, the kiln a stone forge in a log-fenced yard with a lean-to. A
//     clear trade, but a yard of fences and a lean-to is the drawn fuel shed again, and a forge isn't a kiln house.
//  Built: 1. Twin slate gables to the road (the tall closed one where they live, the low open one where the fire
//  lives) is a silhouette no other taiga house has, it tells the story at a glance (the stack comes out of the barn,
//  not the house), and it keeps the drawn house's grammar (a jettied upper storey, the kiln east of it) without copying
//  the bottle. Not the safest: an open barn beside a house must read as built, not missing, so it is framed in posts,
//  a plate and a log gable, and the kiln inside is the warmest thing on the street.
//
// --- Design notes (2026-11-21) ---------------------------------------------------------------------------------------
//  Coordinates as the shipped house: x across the front (west 0 -> east 15), y up, z from the front (z = 0) back.
//  Named parts:
//   Workroom storey  x 1..7, z 2..12, y 1..4: mossy fieldstone (a heavier, mossier course at its foot), the door in a
//                    pale stripped-log frame; the kiln's loading mouth through its east wall (7, 1, 4).
//   Log storey       x 1..7, z 1..12, y 5..9: a pale stripped sill beam (the jetty line, y 5) on four proud log-end
//                    corbels over the stoop, dark spruce logs y 6..8 notched at the corners (the crossing log's end
//                    proud on alternate courses), a dark beam ring and two tie beams at y 9 (the lamps hang from them).
//   Roof             45 degrees, ridge along z at x 4, y 13, eaves x 0 and 8 at y 9, rakes z 0 and 13 (a solid
//                    two-deep slate verge), log gables with their ends proud at the rakes, a konek on the front ridge.
//   Kiln barn        x 8..15, z 1..12: log posts on fieldstone pads, a spruce plate at y 4, a 45-degree slate gable
//                    (ridge x 11..12, y 8) parallel to the house's, its log gable to the road over an open front; the
//                    east side open for stoking with firewood stacked lengthwise under its eave; a log back wall.
//   Kiln             x 9..14, z 2..6: a fieldstone tunnel kiln (y 1..2) under a clay vault (packed mud, mud-brick
//                    stairs, y 3..4); the stoke hole (14, 1, 4) faces east; its stack, fieldstone 2 x 2 (x 11..12,
//                    z 3..4), rises through the barn's ridge to y 10, the campfire on it (the brief's chimney).
//   Fuel store       the barn's back half: split logs, clay, the quench cauldron.
//   Wing             II: the journeyman's room, logs on a fieldstone footing, a 45-degree slate gable (ridge y 8).
//   Drying rack      II: the shipped rack's barrel and cauldron cells, on stripped posts.
//   Cellar           III: the shipped cellar's box, chests and casks, in mossy fieldstone.
//  Inside  the shipped furniture cell for cell (../artisans_house/plan.mjs FURNITURE), the upper rooms open to a
//          spruce-lined 45-degree roof.
//  Round 2 (2026-11-21, briefs/artisans_house_looks_critic.md, Taiga): 1 the kiln barn's front bay is open under the
//          pale plate on a middle post (x 11), no glass: the road looks in at the kiln's fieldstone face and a lantern;
//          a log partition at z 7 shuts the fuel store and a plank ceiling at the plate closes the kiln bay's roof
//          space (holes.py's open check passes with no rail); 2 the east side is open from z 1 to a pale post at
//          z 7, the stoke hole (14, 1, 4) facing out between two lengthwise firewood stacks (z 2 and z 6, slab lids);
//          3 the konek is spruce and a block longer (neck stairs at z 2 and 1, the head at z 0 with its spruce
//          trapdoor ears). It can't reach past the verge: the verge is at z 0, the file's first row (the size is the
//          shipped size), so it was lengthened back along the ridge instead.
import { SIZE, BASEMENT, OPENINGS as A_OPENINGS, FURNITURE as A_FURNITURE, OUTSIDE as A_OUTSIDE, ROOMS, WALK } from '../artisans_house/plan.mjs'

export { SIZE, BASEMENT, ROOMS, WALK }
export const FURNITURE = A_FURNITURE

// PALETTE 60/30/10: 60 dark spruce logs and mossy fieldstone; 30 slate (deepslate tile) roofs; 10 the clay kiln (packed
// mud) and the pale stripped-spruce sill beam, frames and corbels, with ferns at the door.
export const PALETTE = {
  stone: [['cobblestone', 5], ['mossy_cobblestone', 3], ['stone', 1], ['andesite', 1]],
  footStone: [['mossy_cobblestone', 3], ['cobblestone', 2]],
  log: 'spruce_log', pale: 'stripped_spruce_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  konek: 'spruce_stairs', konekEar: 'spruce_trapdoor', // round 2: spruce, not a red-brown stub
  floor: 'spruce_planks', workFloor: 'polished_andesite', lining: 'spruce_planks',
  wood: 'spruce', glass: 'glass_pane',
  clay: 'packed_mud', clayStairs: 'mud_brick_stairs',
  stackCap: 'cobblestone_slab', barnFloor: [['coarse_dirt', 3], ['gravel', 1]],
  cellarWall: [['cobblestone', 3], ['mossy_cobblestone', 2], ['stone', 1]], cellarFloor: 'cobblestone',
}

export const MASSES = {
  house: {
    walls: { x0: 1, x1: 7, z0: 2, z1: 12 }, upper: { x0: 1, x1: 7, z0: 1, z1: 12 },
    groundTop: 4, sillY: 5, upperTop: 8, beamY: 9, ties: [4, 9], corbels: [1, 3, 5, 7],
    roof: { axis: 'z', across: [0, 8], along: [0, 13], eaveY: 9 },
  },
  konek: { x: 4, z: 1 }, // the neck on the ridge at z 1, the head over the front rake (z 0)
  barn: { x0: 8, x1: 15, z0: 1, z1: 12, plate: 4, posts: [[8, 1], [15, 1], [15, 12], [8, 12]],
    roof: { axis: 'z', across: [8, 15], along: [0, 13], eaveY: 5 } },
  kiln: { x0: 9, x1: 14, z0: 2, z1: 6, stack: { x0: 11, x1: 12, z0: 3, z1: 4, top: 10, fire: [11, 3] } },
  firewood: { x0: 9, x1: 11, z: 13, high: 2 }, // under the barn's back rake, lengthwise (bark out)
  partitionZ: 7, bayPost: 11, // round 2: the open kiln bay's partition and middle post
  barnDoor: [13, 1, 12], // the barn's door, in its back wall by the fuel store
  store: { logs: { x: 9, z0: 8, z1: 11, high: 3 }, clay: [[10, 1, 11], [11, 1, 11], [10, 2, 11]], cauldron: [13, 1, 10] },
  wing: { walls: { x0: 2, x1: 6, z0: 13, z1: 17 }, roof: { axis: 'z', across: [1, 7], along: [13, 18], eaveY: 5 } },
  cellar: { x0: 1, z0: 2, x1: 7, z1: 12, depth: 4 },
}

// The shipped openings; the windows keep their cells (the gables' one light each under the 45-degree roof), the pots
// are the forest's.
const POTS = { potted_red_tulip: 'potted_fern', potted_cornflower: 'potted_lily_of_the_valley', potted_oxeye_daisy: 'potted_fern',
  potted_allium: 'potted_spruce_sapling', potted_azure_bluet: 'potted_lily_of_the_valley', potted_blue_orchid: 'potted_fern' }
const pots = w => ({ ...w, pots: (w.pots ?? []).map(p => POTS[p] ?? p) })
export const OPENINGS = {
  ...A_OPENINGS,
  windows: A_OPENINGS.windows.map(pots).map(w => (w.name.endsWith('gable') ? { ...w, cells: [w.cells[0]] } : w)),
  wingWindows: A_OPENINGS.wingWindows.map(pots).map(w => (w.name === 'wing gable' ? { ...w, cells: [w.cells[0]] } : w)),
}

export const OUTSIDE = {
  ...A_OUTSIDE,
  flowerBeds: [{ x0: 1, x1: 3, z: 0, plants: ['fern', 'lily_of_the_valley', 'fern'] }, { x0: 5, x1: 7, z: 0, plants: ['fern', 'cornflower', 'fern'] }],
}
