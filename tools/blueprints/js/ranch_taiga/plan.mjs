// Ranch, the TAIGA look (looks/taiga/ranch[_2], ROADMAP 49.10e, as 49.10a): the same building as the shipped Ranch
// (../ranch/plan.mjs: the aisle with the Rancher's smoker and two chests at its end, the four box stalls either side,
// the hay loft over the aisle up its ladder, the wicket door and the hay door over it, every lamp in its cell; Level
// II's feed and bunk room east of the barn with its barrel, double chest, bunk, desk, chair, rug and lamp, and the
// paddock behind with a man-gap and a gate; every furniture spot in the same cell and facing at both levels), drawn
// again for a ranch in the cool spruce forest. It speaks the taiga village's language (the Starter Cottage, Stone
// House, Weaver's Cottage, Manor and Daub House looks): dark spruce logs crossing at notched corners on a mossy
// fieldstone footing, broad 45-degree slate roofs gable-on to the road with deep rakes, galleries and porches on log
// posts with brackets, log ends, a fieldstone stack, a pale stripped-log band, firewood under the eaves, ferns in moss.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "One great roof": a single broad 45-degree slate gable over the whole barn (eaves at y 6 at x 0 and 12, ridge
//     y 12), the stalls under its two sweeps, a gallery across the gable. The calmest forest form, but from 20 blocks it
//     is one dark tent: no step in the outline, and the stall lamps would hang on three links of chain in a dark sweep.
//  2. "Dogtrot stable": two log stall pens with a roofed open breezeway between them (the aisle). A true forest plan,
//     but the aisle carries the loft, the smoker and the chests, so it must be a closed room: the trot would be a
//     wall-less hall that holes.py's `open` check refuses, and the hay door would open on nothing.
//  3. "The Konek Barn": three log gables to the road, a tall hay barn in the middle flanked by two low stall pens, each
//     with its own small slate gable (a stepped "nave and aisles" front, not a monitor's lean-tos). Across the tall
//     gable, at the loft floor, a SVALGANG: an open gallery on two log posts with brackets and a rail, the hay door
//     opening on to it and the wicket door in a pale stripped-log frame under it; a lantern on a chain from the rake's apex over it.
//     The ridge's front end carries a KONEK, the carved horse's head the northern forest puts
//     on its ridges: the ranch's sign, where the as-drawn barn has a cupola. A broad mossy fieldstone stack (the
//     smoker's flue) climbs the back gable through the rake; firewood is stacked under the west eave, a hollowed-log
//     trough stands by the gallery post, a hitching rail with a lantern on its post by the way in. Level II: a log
//     bunkhouse east, gable-on to the road like the barn, a tiny gabled porch on posts over its door, firewood stacked
//     in the slot between the two; behind, the paddock in split rails with a log run-in shed and a HESJE (the forest's
//     hay-drying fence: posts, hay hung between them, a stripped capping rail).
//  Built: 3. It is the strongest, not the safest: three stepped gables, a gallery and a horse's head on the ridge read
//  "northern horse barn" from 20 blocks on every side, every part does a ranch's job (stalls, aisle, loft, gallery for
//  pitching hay, trough, hitching rail, bunkhouse, hesje), and it is nothing like the snowy look (pale walls and
//  snow) or the as-drawn barn (board walls under a monitor with lean-tos and a cupola). Its risk: three roofs side by
//  side make two valleys against the hay barn's walls, and the gallery under a two-deep rake must not read as a cave.
//
// --- Design notes ----------------------------------------------------------------------------------------------------
//  Coordinates as the shipped Ranch: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z
//  from the front (z = 0, north) back. Level I 13 x 16 x 13, Level II 21 x 16 x 21 (as shipped).
//  Named parts:
//   Hay barn    the tall middle pen, x 4..8, z 3..11: the front and back log walls to y 9, the long walls (x 4 and 8)
//               logs y 5..9 over the stall fronts on a pale stripped band (y 4, the loft floor's line) and a post at z 7;
//               the loft floor over the aisle (y 4, the ladder's hatch at (7, 4, 4)); a 45-degree slate gable, ridge
//               along z at x 6, y 12, eaves x 3 and 9 at y 9, rakes z 1 (two deep over the gallery) and z 12; log gables;
//               clerestory lights (x 4 and 8, z 5 and 9, y 7..8) over the stall roofs.
//   Stall pens  west x 1..3 and east x 9..11, z 3..11, their log walls y 1..5 crossing at the corners with the front and
//               back walls; each under its own small 45-degree gable (ridge along z at x 2 / x 10, y 7; eaves at x 0 /
//               12 on y 5), so the stall lamps hang straight from the slope at (3|9, 6); barred windows in the outer
//               walls, a sill window in each front gable, two stalls each side (gates off the aisle, a partition at z 7).
//   Gallery     the svalgang: a spruce deck at y 4 (z 1..2, x 4..8) on a log beam and two log posts at z 1 with
//               brackets, a spruce rail round it, lanterns under it either side of the wicket door; the hay door opens on
//               to it. The wicket door sits in a stripped-log frame.
//   Konek       on the ridge's front end: a fence-post neck (6, 13, 1) and the carved head, an upside-down spruce stair
//               turned side-on (round 2; a log pole and a log neck read as a chimney stub).
//   Hoist lamp  a chain from the front rake's apex (6, 11, 1) and a lantern over the gallery (a hoist beam there was
//               tried: from the road it stacked with the konek into one column of log ends).
//   Stack       the smoker's flue: a mossy fieldstone breast three wide (x 5..7, z 12) to y 4, weathered in, the flue
//               (x 6) up through the back rake to y 13, smoking.
//   Yard        a hollowed-log trough by the west gallery post, a hitching rail with a lantern east of the way in, a
//               dirt path to the step, firewood under the west eave, hay and ferns under the east one. The old yard's
//               cauldron cells (9, 0, 2) and (10, 0, 2) stay open ground.
//   Bunkhouse   (II) a log pen x 13..19, z 5..11, walls y 1..4, gable-on to the road: a 45-degree slate gable (ridge along
//               z at x 16, y 8; eave x 20 at y 4, its west slope meeting the barn's east eave in a valley at x 12..13),
//               rakes z 4 and 12; a little gabled porch on two log posts over the door (x 15..17, z 2..4); firewood
//               stacked in the slot between it and the barn (x 12, z 5..11) up under the barn's eave.
//   Paddock     (II) z 13..20: split rails (spruce fence) round it, lanterns on the corner posts, a man-gap on the west a
//               villager walks through and a horse can't, a double gate on the east; a log run-in shed (x 8..12, z 17..19)
//               open to the barn under a slate lean-to; the hesje (x 14..19, z 18); a hollowed-log trough; a snubbing post.
//  Inside   the shipped furniture cell for cell (../ranch/plan.mjs FURNITURE); the shelf's pot is a fern, not a cactus.
//           The back-west stall's gate moved to z 8, clear of the chest at (5, 1, 9).
//
//
// --- Done, checked, renders, tried and changed (2026-11-21) ----------------------------------------------------------
//  Done: both levels saved as candidates (build.sh -> build/rebuild/ranch_taiga/ranch.nbt, ranch_2.nbt; never SHIP=1),
//    sizes as shipped (13 x 16 x 13, 21 x 16 x 21). 872 and 1344 blocks; about 35 and 46 stacks (Level I's limit 108);
//    II keeps 99 % of I's blocks (only the east eave's hay gives way to the firewood slot). Every block id vanilla 1.21.1.
//  Checked: look_spots.py ranch_taiga ranch: SAME BUILDING (8/8 and 13/13 shipped spots, none missing; no bed, chest,
//    barrel, smoker, door or ladder moved; every lamp on its shipped chain); rebuild.py: no CHECK line at either level;
//    holes.py (open included): 0 at both. One smoker (6, 1, 10), no other job block at I; II adds only the kept barrel
//    (14, 1, 10). The old yard's cauldron cells (9, 0, 2) and (10, 0, 2) are open ground (the recipe throws if drawn
//    over); (7, 1, 10) open floor. The way in (path, step, wicket door) has no gate; the paddock's gate is not the only
//    way in (the man-gap).
//  Renders: build/rebuild/ranch_taiga/renders/: taiga_<1|2>_<front|back|left|right|top>.png and the cutaways
//    taiga_<1|2>_cut3_<top|front>.png (aisle, stalls, bunk room) and taiga_<1|2>_cut7_<top|front>.png (the hay loft).
//  Tried and changed: a lantern on the hitching rail's log post broke the owner's lamp rule (the posts are fences now);
//    the run-in shed's lean-to first rose to a blank back wall with an overhang over its open front, which holes.py read
//    as a room the outside gets into and which showed the paddock a box (now high over the open front and falling to a
//    low back, no overhang); the hesje's solid wall of hay read as a crate (now a full course and a shorter one on the
//    rails, open under them); the west woodpile laid end-on read as crates (now laid along the wall); a neck log under
//    the konek and a hoist beam out of the apex stacked into one column of log ends from the road (both dropped: the
//    head sits on the ridge pole, the gallery's lantern hangs from the rake's apex on a chain).
//  Round 2 (2026-11-22, the critic's notes, briefs/ranch_looks_critic.md, BACK narrow on II's Detail):
//    1 log ends: only at the stall pens' (and the bunkhouse's) notched outer corners, every other course (the through
//      log of that course: the front/back wall's on odd courses, the side wall's on even ones); the hay barn's upper
//      corner ends (x 3/9 and the columns at (4|8, 6..9, 2|12)) are gone; the barge stairs run as one line under each
//      rake and stop short of the eave ends (no lone tooth under the eave corners).
//    2 konek: the ridge pole stub is gone; a spruce fence neck stands on the ridge's front end (6, 13, 1) under the head,
//      an upside-down spruce stair turned side-on (6, 14, 1, facing east), one block wide.
//    3 ferns: no moss cubes; potted ferns on the ground with moss carpet spread round them and either side of the path.
//    4 bunkhouse porch: slim spruce fence posts with upside-down stair brackets under the eave and a slab lintel over the
//      door, the svalgang's language; the firewood in the slot now stacks right up under the barn's eave (z 5..11, y 0..4).
//    Checked after round 2: look_spots SAME BUILDING (8/8, 13/13, nothing moved); holes.py 0 at both levels (open
//    included); rebuild.py no CHECK line; 827 / 1298 blocks. Renders overwritten in build/rebuild/ranch_taiga/renders/.
//  Open for the critic: the renderer draws spruce bark warm orange, so the logs read lighter than in game; the stall
//    pens' gables are small (three wide) beside the hay barn's; the konek is a two-block sign, clearest from the sides.
//  Round 3 (2026-11-21, the round-2 critic's notes, BACK on II's Detail 3):
//    1 gable face and rakes: the hay barn's corners over the pens' roofs (x 4 and 8, y 5..9, front and back) were
//      notched, so every other course showed a log end on the gable face beside the window and the hoist lamp; they
//      are upright corner posts now (log on end, bark all round), so the gable face is plain dark bark with no ends in
//      it. The barge under every rake (hay barn, both pens, the bunkhouse) is dark oak stairs, not pale spruce, set
//      flush under the slate, one dark line. At II the bunkhouse's two west corner ends (by the east pen's) are gone:
//      the two pens' ends crowded into one cluster of pale squares in the valley. The pale squares left on the front
//      are the notched outer corners of the pens and the bunkhouse's east corners.
//    2 konek: turned along the ridge, looking out over the road, all dark oak and one block wide: a fence neck on the
//      ridge's front end (6, 13, 1), the skull an upside-down stair (6, 14, 1, facing south: its cheek hangs at the back
//      over the neck), the muzzle a top slab out over the rake (6, 14, 0), a button ear on the crown (6, 15, 1). From the
//      sides a hooked horse's head on its neck; from the road a narrow head end-on.
//    Checked after round 3: look_spots SAME BUILDING (8/8, 13/13, nothing moved); holes.py 0 at both levels (open
//    included); rebuild.py no CHECK line; 829 / 1296 blocks; sizes 13 x 16 x 13 and 21 x 16 x 21. Renders overwritten.

import { FURNITURE as SHIPPED } from '../ranch/plan.mjs'

export const SIZE = { 1: [13, 16, 13], 2: [21, 16, 21] }
export const IDS = { 1: 'ranch', 2: 'ranch_2' }

// PALETTE 60/30/10: 60 dark spruce logs on a mossy fieldstone footing; 30 dark slate (deepslate tiles) and fieldstone
// (the footing, the stack); 10 pale stripped spruce (the band, the door frame, the trough and the rails), hay gold and
// the ferns' green.
export const PALETTE = {
  footing: [['cobblestone', 4], ['mossy_cobblestone', 3]],
  stack: [['cobblestone', 3], ['mossy_cobblestone', 2], ['stone', 1]], weather: 'cobblestone_stairs',
  log: 'spruce_log', band: 'stripped_spruce_log', frame: 'stripped_spruce_log', post: 'spruce_log', bracket: 'spruce_stairs',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  barge: 'dark_oak_stairs', floor: 'spruce_planks', deck: 'spruce_planks', wood: 'spruce', door: 'dark_oak', glass: 'glass_pane',
  bars: 'iron_bars', bedding: 'coarse_dirt', hay: 'hay_block', firewood: 'spruce_log', rim: 'stripped_spruce_log',
  moss: 'moss_carpet', pot: 'potted_fern', path: 'dirt_path',
}

export const BARN = {
  walls: { x0: 4, x1: 8, z0: 3, z1: 11 }, aisle: { x0: 5, x1: 7, z0: 4, z1: 10 }, top: 9, band: 4, loftY: 4, midPost: 7,
  roof: { axis: 'z', across: [3, 9], along: [1, 12], eaveY: 9, steep: false }, // ridge y 12 at x 6
  clerestory: { zs: [5, 9], ys: [7, 8] },
}
export const PENS = {
  west: { outer: 1, x0: 1, x1: 3, line: 4, toward: 'east', roof: { axis: 'z', across: [0, 4], along: [2, 12], eaveY: 5, steep: false } },
  east: { outer: 11, x0: 9, x1: 11, line: 8, toward: 'west', roof: { axis: 'z', across: [8, 12], along: [2, 12], eaveY: 5, steep: false } },
  z0: 3, z1: 11, top: 5, partition: 7,
  stalls: { west: [{ z0: 4, z1: 6, gate: 5 }, { z0: 8, z1: 10, gate: 8 }], east: [{ z0: 4, z1: 6, gate: 5 }, { z0: 8, z1: 10, gate: 9 }] },
}
export const GALLERY = { x0: 4, x1: 8, z0: 1, z1: 2, y: 4, posts: [4, 8], lamps: [[5, 3, 2], [7, 3, 2]] }
export const KONEK = { x: 6, y: 12, z: 1, wood: 'dark_oak' } // on the ridge's front end (the rake at z 1)
export const HOIST = { x: 6, y: 11, z0: 1, z1: 3, lamp: [6, 8, 1] }
export const STACK = { x: 6, z: 12, breast: [5, 7], breastTop: 4, top: 13 }

export const OPENINGS = {
  door: { at: [6, 1, 3], facing: 'south', out: 'north', step: [6, 0, 2] }, // the wicket door (kept cell)
  hayDoor: { at: [6, 5, 3], facing: 'south', out: 'north' }, // on to the gallery (kept cell)
  ladder: { x: 7, z: 4, y0: 1, top: 4, facing: 'south' }, // kept cells
  windows: [ // [cells], the side they look out of, dressing
    { name: 'west pen front', cells: [[2, 2, 3], [2, 3, 3]], out: 'north', dress: 'sill' },
    { name: 'east pen front', cells: [[10, 2, 3], [10, 3, 3]], out: 'north', dress: 'sill' },
    { name: 'west pen back', cells: [[2, 2, 11], [2, 3, 11]], out: 'south', dress: 'sill' },
    { name: 'east pen back', cells: [[10, 2, 11], [10, 3, 11]], out: 'south', dress: 'sill' },
    { name: 'loft front west', cells: [[5, 7, 3]], out: 'north', dress: null },
    { name: 'loft front east', cells: [[7, 7, 3]], out: 'north', dress: null },
    { name: 'loft back west', cells: [[5, 6, 11], [5, 7, 11]], out: 'south', dress: null },
    { name: 'loft back east', cells: [[7, 6, 11], [7, 7, 11]], out: 'south', dress: null },
  ],
  stallBars: { xs: [1, 11], zs: [5, 9], y: 2 },
}

// The shipped furniture, cell for cell; the shelf's pot plant a fern
export const FURNITURE = {
  1: SHIPPED[1].map(f => (f.part === 'shelf' ? { ...f, plant: 'potted_fern' } : f)),
  2: SHIPPED[2],
}

export const YARD = {
  path: [[6, 0], [6, 1]],
  trough: { water: [[2, 1], [3, 1]], y: 0 },
  hitch: { posts: [[9, 0], [11, 0]], rail: [10, 1, 0], lamp: [11, 2, 0] },
  firewood: { x: 0, z0: 6, z1: 8, top: 2 },
  hay: [[12, 0, 6], [12, 0, 7], [12, 1, 7], [12, 0, 8]],
  ferns: [[12, 4], [12, 10], [0, 4], [0, 10], [11, 1]],
  moss: [[12, 1], [12, 2], [11, 0], [12, 5], [12, 9], [0, 5], [0, 9], [5, 0], [7, 0]],
  keepOpen: [[9, 0, 2], [10, 0, 2]],
}

export const BUNK = {
  walls: { x0: 13, x1: 19, z0: 5, z1: 11 }, room: { x0: 14, x1: 18, z0: 6, z1: 10 }, top: 4,
  roof: { axis: 'z', across: [12, 20], along: [4, 12], eaveY: 4, steep: false }, // ridge y 8 at x 16
  door: { at: [16, 1, 5], facing: 'south', out: 'north' }, // kept cell
  porch: { cx: 16, half: 1, y: 3, z0: 2, z1: 4, posts: [15, 17], lamp: [16, 3, 4] },
  windows: [
    { name: 'bunk front west', cells: [[14, 2, 5], [14, 3, 5]], out: 'north', dress: 'sill' },
    { name: 'bunk front east', cells: [[18, 2, 5], [18, 3, 5]], out: 'north', dress: 'sill' },
    { name: 'bunk east', cells: [[19, 2, 8], [19, 3, 8]], out: 'east', dress: 'sill' },
    { name: 'bunk back', cells: [[17, 2, 11], [17, 3, 11]], out: 'south', dress: 'sill' },
    { name: 'bunk back gable', cells: [[16, 5, 11], [16, 6, 11]], out: 'south', dress: null },
  ],
  hay: [[18, 1, 10], [18, 2, 10], [18, 1, 9]],
  firewood: { x: 12, z0: 5, z1: 11, top: 4 }, // stacked right up under the barn's eave
  path: [[16, 0], [16, 1]],
}

export const PADDOCK = {
  ring: [[0, 0, 13], [20, 0, 13], [20, 0, 20], [0, 0, 20], [0, 0, 13]],
  manGap: [0, 0, 16], gates: [[20, 0, 16], [20, 0, 17]], lampPosts: [[0, 13], [20, 13], [20, 20], [0, 20]],
  shed: { x0: 8, x1: 12, z0: 17, z1: 19 }, // three log walls, open to the north under a slate lean-to
  hesje: { z: 18, x0: 14, x1: 19, posts: [14, 19], top: 3 },
  trough: { water: [[3, 15], [4, 15]], y: 0 },
  snubbingPost: [6, 18],
}

// The walking line: up the path, past the trough and the hitching rail, up the step under the gallery, in at the
// wicket door, down the aisle to the smoker (he works it from (6, 1, 9)) and the chests; up the ladder to the loft
// and out of the hay door on to the gallery. Level II: along the front to the bunkhouse porch, in to the barrel; round
// the west side through the man-gap into the paddock.
export const WALK = {
  1: [[6, 0, 0], [6, 0, 1], [6, 0, 2], [6, 1, 3], [6, 1, 4], [6, 1, 9], [7, 1, 5], [7, 5, 4], [6, 5, 4], [6, 5, 2]],
  2: [[16, 0, 0], [16, 0, 3], [16, 1, 5], [16, 1, 8], [14, 1, 9], [-1, 0, 16], [0, 0, 16], [3, 0, 16]],
}
