// Ranch, the SAVANNA look (looks/savanna/ranch[_2], ROADMAP 49.10e, as 49.10a): the same building as the shipped Ranch
// (../ranch/plan.mjs: the aisle with the Rancher's smoker and the two tack chests at its end, the four stalls, the
// wicket door and the hay door over it, the ladder to the hay loft, every lamp and the shelf; Level II's feed and bunk
// room with its barrel, double chest, bed, desk and door; every furniture spot in the same cell at both levels), drawn
// again for a hot grassland with a rainy season. It speaks the savanna village's language the Cottage, the Stone House,
// the Weaver's Cottage, the Daub House and the Manor set (golden thatch hips with eaves on every side and no gable
// anywhere, grey acacia posts and beams, packed mud over a red granite storey on a terracotta plinth, a painted band
// under the eave, a clay pot on the peak), and it is the village's HERDER's stable.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Boma and rondavels": a ring of thorn (acacia rails) with the stalls as round mud huts under conical thatch round
//     its inside and the tack hut at the back. The strongest savanna picture, but the shipped stable is one building
//     (the aisle, the stalls either side, the loft over the aisle, a ladder and a hay door on its front wall): round
//     huts can't hold its square cells, so it would not be the same building.
//  2. "Open pole shed": one long thatch on acacia poles with no walls, the stalls fenced under it, the cattle-shade of
//     a dry country. Light and true, but the hay door at y 5 and the loft need walls to stand in, the clerestory lamps
//     hang off a wall, and from 20 blocks it is a flat lid: the weakest outline of the three.
//  3. "The Two-Tier Thatch": the monitor barn turned savanna. The stable range (the aisle and both rows of stalls) in red
//     granite on a terracotta plinth under ONE SKIRT of golden thatch hipped on all four sides (eaves at y 3 all round,
//     a pent over the wicket door, no gable anywhere); out of it rises the hay loft as a packed-mud CLERESTORY between
//     grey acacia posts (a painted band under its eave, the high windows lighting the loft) under a SECOND, higher thatch
//     hip with a clay pot on its ridge: a tiered roof, the savanna's hip twice. A thatch hood and an acacia hoist beam
//     over the hay door, a tall mud stack up the back for the smoker. In front, a KRAAL GATE (two acacia poles, a beam,
//     a little thatch cap with a pot) into a yard railed in acacia on a terracotta kerb, a log trough and the hay.
//     Level II adds the hand's BUNK HOUSE beside it as a square mud house under a thatch PYRAMID (a rondavel made square,
//     its own pot on the point) with the firewood stacked in the slot between, and the KRAAL behind: acacia rails on a
//     kerb, a man-gap a horse can't pass, a double gate to drive the herd, an acacia shade tree, a thatched shelter
//     on four poles with hay under it, a trough and a snubbing post.
//     Story: a cattle-and-horse herder's stable: stone below where the horses kick, cool mud above where the hay keeps,
//     and every roof the village's thatch.
//  Built: 3. It is the strongest: from 20 blocks it is a tiered golden roof (a wide low skirt, a tall narrow crown, a
//  pot on top) beside a pyramid, an outline no other savanna building has and still the monitor barn a rider knows;
//  every face has two eaves of shadow; Level II grows out of it as the village grows (a smaller roof of the same kind
//  beside it, the kraal behind). Not the safest: a hip skirt has to run round a wall that rises out of it on all four
//  sides, the upper hip's eave hangs over the skirt, and the bunk house's eave meets the skirt in a valley.
//
// --- Design notes: what is done, what is left (2026-11-21) -----------------------------------------------------------
//  Done: Levels I and II as candidates (build.sh -> build/rebuild/ranch_savanna/ranch.nbt, ranch_2.nbt), never live.
//  The shipped furniture list drawn as is (../ranch/plan.mjs's FURNITURE: the same parts, kinds, facings and chains;
//  the shelf carries a potted acacia sapling); the smoker (6, 1, 10) and the chests (5, 1, 9) and (5, 1, 10) in the kept
//  cells, (7, 1, 10) open floor, the old yard cauldron cells (9, 0, 2) and (10, 0, 2) open ground; II's barrel
//  (14, 1, 10), double chest, bed, desk and the bunk house's door in their cells; the dark oak doors and the ladder as
//  shipped. The way in has no gate (the kraal gate is two poles and a beam).
//  Checked: look_spots.py ranch_savanna ranch: SAME BUILDING (8 and 13 shipped spots, none missing; no bed, container,
//  job block, door or ladder moved; no lamp on another chain); rebuild.py no CHECK line at either level; holes.py 0
//  (with open) at both; every id vanilla 1.21.1; II keeps 99 % of I (749 of 758 cells, by block); about 35 stacks (I,
//  limit 108) and 51 (II); 13 x 14 x 13 and 21 x 14 x 21 (I under its 13 x 16 x 13 plot; II at least I each way).
//  Renders: build/rebuild/ranch_savanna/renders/ (savanna_<L>_<front|back|left|right|top>.png; cutaways
//  savanna_<L>_cut3_<top|front>.png for the aisle, stalls and bunk room, savanna_<L>_cut8_<top|front>.png for the loft).
//  Tried and changed: the shelter's thatch pyramid left an empty cell under its point (holes.py stray: filled); the
//  kraal gate first stood 5 high with its beam at y 5 and hid the stable's front from the street (now 4 high, beam at
//  y 4, lanterns at y 3, the cap a block lower, the hay door, its white jambs and hood seen over it).
//  Open for the critic: the bamboo-mosaic thatch is the village's (as saturated as on the other savanna looks); the
//  painted band reads mostly red from a distance; the kraal's terracotta kerb reads as a low wall under the rails.
//  Round 2 (2026-11-21, briefs/ranch_looks_critic.md, BACK on Detail 3/3):
//    1 the kraal's and the yard's rails stand on the ground with no terracotta kerb (the man-gap and the double gate on
//      the ground too), and the stable's plinth is dull brown terracotta with mud bricks and packed mud;
//    2 the kraal gate's poles are acacia fences (not logs) under the beam, its cap a row of slabs with the pot on one
//      thatch block, so the wicket door and the stall windows read through it from the road;
//    3 the skirt is no longer a 45-degree stair hip (a ziggurat with jagged hips): it is a LOW hip of thatch slabs, half
//      a block a ring (eave a top slab at y 4, then y 5 bottom, y 5 top, y 6 bottom against the loft, where the stalls'
//      lamps hang from it as shipped), so its hips have no stair corners; the stalls' walls carry the band a course
//      higher (granite y 1..3, band y 4). Tried first: a slab eave lip with slab rolls on the hip lines (read as loose
//      slats, and the top slabs dropped the stalls' lamps: CHECK lines), and the stair hip with only a slab eave lip
//      (still stepped);
//    4 the painted band runs unbroken round every face (the loft's at y 8 over its posts, which now stop at y 7; the
//      stable's at y 4 round all four sides; the bunk house's at y 4 over its posts); the clerestory is one high now
//      (y 7) so the band runs over it.
//    Also: the two lanterns under the front pent are gone (a top-slab eave holds no lantern); the hoist lantern lights
//    the wicket door. Rechecked: look_spots SAME BUILDING (8/8, 13/13, 0 moved), holes.py 0, no CHECK line, II keeps
//    99 % of I, about 35 and 49 stacks. Renders overwritten in build/rebuild/ranch_savanna/renders/ (same names).
//  Round 3 (2026-11-21, BACK on I's Silhouette 3): 1 the skirt falls 2.5 blocks again (eave stairs y 4, a bottom slab
//    and a full block at y 5, stairs against the loft at y 6; no top slabs, which rendered flat and let seal_slots carry
//    the loft wall out as a ledge), the stable's band down to y 3 under the eave stairs; 2 the hoist beam comes out
//    through the hay hood at y 7, so the loft's band at y 8 runs unbroken. Rechecked: look_spots SAME BUILDING, holes.py
//    0, no CHECK line, 737 and 1181 blocks, II keeps 99 % of I.
//  Left: the critic's pass (REBUILD.md) against the as-drawn Ranch; the Java side (the ship step).
//
// Coordinates as the shipped Ranch: x across the front (west 0 -> east), y up (0 = the layer on the clicked block: the
// plinth, the kerbs and anything standing on the ground; the ground outside is walked at y 0), z from the front (z = 0,
// north) to the back. Level I is 13 x 14 x 13; Level II keeps Level I (but the skirt's east eave, where the bunk house
// meets it) and adds the bunk house (x 12..20) and the kraal (z 13..20): 21 x 14 x 21.
//
// --- Named parts -------------------------------------------------------------------------------------------------------
//   Stable      the ground storey: the stalls' outer walls x 1 and 11, the front and back walls z 3 and 11 (x 1..11), red
//               granite y 1..3 on a terracotta plinth, grey acacia posts at z 3, 7, 11; barred stall windows; the
//               painted band (red and white terracotta) at y 3 across the stalls' front.
//   Skirt       the lower thatch: one low hip x 0..12, z 2..12 from its eave (stairs at y 4, up to stairs at y 6), cut where the
//               loft rises out of it (x 4..8, z 3..11): the stalls' lean-tos (y 6 against the loft), and a pent over the
//               front and the back of the loft's walls; the wicket door under the front pent.
//   Loft        the clerestory: walls x 4 and 8, z 3 and 11; the stall fronts at y 1..2 (acacia boards, gates and rails),
//               an acacia beam at the loft floor (y 4), packed mud y 5..8 with the painted band at y 8, acacia posts at
//               z 3, 7, 11 y 1..9, the beam ring at y 9; two-high windows at y 7..8 over the skirt.
//   Crown       the upper thatch: a hip x 3..9, z 2..12 from eaves at y 9 (a block out over the loft's walls), ridge y 12
//               along z, a clay pot on its middle.
//   Hood        over the hay door: a thatch hood at y 7 on the front wall, an acacia hoist beam at y 8 out to z 1 with a
//               chain and lantern.
//   Stack       the smoker's flue: a mud-brick stack up the back (x 6, z 12) through both eaves, smoking at y 12.
//   Kraal gate  two acacia poles at x 4 and 8 on z 0 (y 0..3), an acacia beam at y 4 (x 3..9), a thatch cap with a pot,
//               two lanterns hung under the beam; the way between stays open (no gate on the way in).
//   Yard        acacia rails on the ground from the gate poles to the stalls' corners (z 0..2), a log trough (west),
//               hay and a water jar (east); the old cauldron cells (9, 0, 2) and (10, 0, 2) stay open ground.
//   Bunk house (II)  packed mud walls x 13..19, z 5..11 (y 1..4) on a terracotta plinth, acacia posts at the corners and
//               beside the door, the painted band at y 4; a thatch pyramid x 12..20, z 4..12 from eaves at y 4 to its
//               point (16, 8, 8) with a clay pot on it; the room open to the thatch.
//   Woodpile (II) the slot between the stalls' east wall and the bunk house (x 12, z 5..11), acacia logs to y 3 under
//               the valley where the two eaves meet.
//   Kraal (II)  x 0..20, z 13..20: acacia rails on the ground, a man-gap at (0, z 16), a double gate at x 20 (z 16,
//               17), lamp posts at the corners; an acacia shade tree (west), a thatched shelter on four poles (east, a
//               pyramid x 15..19, z 15..19 from eaves at y 3, boarded under its point, a pot on it), a log trough, a
//               snubbing post.
import { FURNITURE as R_FURNITURE, OPENINGS as R_OPENINGS } from '../ranch/plan.mjs'

export const SIZE = { 1: [13, 14, 13], 2: [21, 14, 21] }

// Palette 60/30/10: golden bamboo-mosaic thatch (the 60 of what you see: two tiers of roof) / red granite stalls and
// packed mud loft on a terracotta plinth (30) / grey acacia posts, beams and gate poles, the painted band, acacia
// shutters, stall boards and rails, the shipped dark oak doors and spruce furniture (10). Why: the savanna village's
// own materials; a stable gets stone where the horses are and mud where the hay is.
export const PALETTE = {
  granite: [['granite', 6], ['polished_granite', 1], ['terracotta', 1]],
  mud: [['packed_mud', 6], ['mud_bricks', 1]],
  plinth: [['brown_terracotta', 4], ['mud_bricks', 2], ['packed_mud', 1]], // dull and dark at the foot (round 2, note 1)
  frame: 'acacia_log', // grey bark: posts, beams, poles
  roof: { full: 'bamboo_mosaic', stairs: 'bamboo_mosaic_stairs', slab: 'bamboo_mosaic_slab' },
  paint: ['red_terracotta', 'white_terracotta'], jamb: 'white_terracotta',
  shutter: 'acacia', fence: 'acacia', board: 'acacia_planks', leaf: 'acacia_planks', // the big doors' leaves
  door: 'dark_oak', // as shipped (look_spots: the same door in the same cell)
  glass: 'glass_pane', bars: 'iron_bars',
  aisle: 'mud_bricks', stallFloor: 'coarse_dirt', loftFloor: 'spruce_planks', bunkFloor: 'mud_bricks',
  kerb: 'terracotta', rim: 'stripped_acacia_log', hay: 'hay_block', firewood: 'acacia_log',
  stack: 'mud_bricks', step: 'mud_brick_stairs', finial: 'decorated_pot',
  tree: { soil: 'grass_block', log: 'acacia_log', leaves: 'acacia_leaves' },
}

// round 3: granite y 1..2 and the band y 3 right under the skirt's eave stairs (y 4, which hide the course behind them)
export const STABLE = { x0: 1, x1: 11, z0: 3, z1: 11, wallTop: 4, postTop: 2, posts: [3, 7, 11], paintY: 3 }
// round 3: the skirt is a real thatch slope again, falling 2.5 blocks from the loft wall to the eave: eave stairs at
// y 4, a bottom slab at y 5, a full block at y 5, stairs at y 6 against the loft (where the stalls' lamps hang as shipped)
export const SKIRT = { x0: 0, z0: 2, x1: 12, z1: 12, eaveY: 4 }
export const LOFT = {
  x0: 4, x1: 8, z0: 3, z1: 11, aisle: { x0: 5, x1: 7, z0: 4, z1: 10 }, posts: [3, 7, 11],
  beamY: 4, mudY: [5, 8], paintY: 8, ringY: 9, floorY: 4,
}
export const CROWN = { x0: 3, z0: 2, x1: 9, z1: 12, eaveY: 9, finial: [6, 13, 7] }
// round 3: the hoist beam comes out through the hood at y 7 (between its two stairs), so the band at y 8 runs unbroken
export const HOOD = { stairs: [[5, 7, 2], [7, 7, 2]], beam: { x: 6, y: 7, z0: 1, z1: 3 }, chain: [6, 6, 1], lamp: [6, 5, 1] }
export const STACK = { x: 6, z: 12, top: 11 }
export const STALLS = { list: [{ z0: 4, z1: 6, gate: 5 }, { z0: 8, z1: 10, gate: 9 }], partition: 7, west: [2, 3], east: [9, 10] }
export const GATE = { poles: [4, 8], z: 0, top: 3, beamY: 4, beam: [3, 9], lamps: [[5, 3, 0], [7, 3, 0]], finial: [6, 6, 0] }
export const YARD = {
  rails: [[[3, 0, 0], [0, 0, 0], [0, 0, 2]], [[9, 0, 0], [12, 0, 0], [12, 0, 2]]], // on the ground, no kerb (round 2)
  trough: [[1, 1], [2, 1]],
  hay: [[11, 0, 1], [11, 0, 2], [10, 0, 1], [11, 1, 1]],
  jar: [9, 0, 1],
  lamps: [], // round 2: the low skirt's eave is a top slab, which holds no lantern; the hoist lamp lights the door
}

export const BUNK = {
  walls: { x0: 13, x1: 19, z0: 5, z1: 11 }, room: { x0: 14, x1: 18, z0: 6, z1: 10 }, wallTop: 4, paintY: 4,
  posts: [[13, 5], [15, 5], [17, 5], [19, 5], [13, 11], [19, 11], [13, 8], [19, 8]],
  roof: { x0: 12, z0: 4, x1: 20, z1: 12, eaveY: 4 }, finial: [16, 9, 8],
  lamp: [17, 3, 4], hay: [[18, 1, 10], [18, 1, 9]],
}
export const WOODPILE = { x: 12, z0: 5, z1: 11, y1: 3 }

export const KRAAL = {
  ring: [[0, 0, 13], [20, 0, 13], [20, 0, 20], [0, 0, 20], [0, 0, 13]], // rails on the ground, no kerb (round 2)
  manGap: [0, 0, 16], // one block wide: a villager walks through, a horse (1.4 wide) can't
  gates: [[20, 0, 16], [20, 0, 17]], // a double gate to drive the herd in and out
  lampPosts: [[0, 13], [20, 13], [20, 20], [0, 20]],
  tree: [4, 0, 17],
  shelter: { x0: 15, z0: 15, x1: 19, z1: 19, eaveY: 3, posts: [[16, 16], [18, 16], [16, 18], [18, 18]], finial: [17, 6, 17],
    hay: [[17, 0, 18], [18, 0, 17]] },
  trough: [[9, 19], [10, 19]],
  snubbingPost: [11, 16],
}

// The shipped openings that hold the building together, cell for cell: the wicket and hay doors, the ladder, the bunk
// house's door. The windows are this look's own.
export const OPENINGS = {
  barnDoor: { ...R_OPENINGS.barnDoor, leaves: [[5, 3], [7, 3]] }, hayDoor: R_OPENINGS.hayDoor, ladder: R_OPENINGS.ladder,
  clerestory: { xs: [4, 8], zs: [5, 9], ys: [7] }, // under the unbroken band at y 8
  stallWindows: { xs: [1, 11], zs: [5, 9], y: 2 },
  windows: [
    { name: 'stalls front west', cells: [[2, 2, 3]], out: 'north', dress: 'shutters' },
    { name: 'stalls front east', cells: [[10, 2, 3]], out: 'north', dress: 'shutters' },
    { name: 'stalls back west', cells: [[2, 2, 11]], out: 'south', dress: 'shutters' },
    { name: 'stalls back east', cells: [[10, 2, 11]], out: 'south', dress: 'shutters' },
    { name: 'loft back west', cells: [[5, 6, 11], [5, 7, 11]], out: 'south', dress: 'shutters' },
    { name: 'loft back east', cells: [[7, 6, 11], [7, 7, 11]], out: 'south', dress: 'shutters' },
  ],
  bunk: {
    door: R_OPENINGS.wing.door,
    windows: [
      { name: 'bunk front west', cells: [[14, 2, 5], [14, 3, 5]], out: 'north', dress: 'shutters' },
      { name: 'bunk front east', cells: [[18, 2, 5], [18, 3, 5]], out: 'north', dress: 'shutters' },
      { name: 'bunk east front', cells: [[19, 2, 6], [19, 3, 6]], out: 'east', dress: 'shutters' },
      { name: 'bunk east back', cells: [[19, 2, 10], [19, 3, 10]], out: 'east', dress: 'shutters' },
      { name: 'bunk back', cells: [[16, 2, 11], [16, 3, 11]], out: 'south', dress: 'shutters' },
    ],
  },
}

// Every furniture piece: the shipped Ranch's, the same parts in the same cells, the lamps on the shipped chains (the
// loft's lamp hangs from the crown's ridge at y 12, the stalls' from the skirt's top course at y 6, the bunk room's two
// links from the pyramid's point at y 8). Chests and the smoker are fixtures (no spot), in the kept cells.
export const FURNITURE = R_FURNITURE

// The walking lines: through the kraal gate, across the yard, up the step, in at the wicket door, down the aisle to the
// smoker (he works it from (6, 1, 9)) and the tack chests; up the ladder to the loft. II: along the front to the bunk
// house's door, in to the barrel; round the west side through the man-gap into the kraal, under the acacia.
export const WALK = {
  1: [[6, 0, 0], [6, 0, 1], [6, 0, 2], [6, 1, 3], [6, 1, 4], [6, 1, 6], [6, 1, 9], [7, 1, 5], [7, 1, 4], [7, 5, 4], [6, 5, 5]],
  2: [[13, 0, 2], [16, 0, 3], [16, 0, 4], [16, 1, 5], [16, 1, 6], [15, 1, 8], [14, 1, 9], [-1, 0, 16], [0, 0, 16], [3, 0, 16]],
}
