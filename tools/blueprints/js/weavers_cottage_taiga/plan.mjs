// Weaver's Cottage, the TAIGA look (looks/taiga/weavers_cottage[_2], ROADMAP 49.10d, "as 49.10a"): the same workplace
// as the shipped Weaver's Cottage (../weavers_cottage: its workroom and loft, the loom and both chests, its three doors,
// the flight to the loft, the fold through the gable door, Level II's dye garden through the back door, and every
// furniture spot in the same cell at both levels, the same 18x14x12 and 18x14x17 plots), drawn again for a weaver in
// the cool spruce forest. The taiga cottage, stone house, terrace and manor set the language: dark spruce logs notched
// at the corners on a mossy fieldstone course, broad 45-degree slate roofs with deep rakes, porches on log posts with
// brackets, log ends, fieldstone chimneys, a pale stripped-log band, firewood under the eaves, ferns in moss.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Gable-on loom lodge": the house turned gable-on to the road like the taiga cottage, the loom behind a gable
//     window. Its best idea is lost at once: the loom, its chest and the front door are kept cells along a front wall
//     that runs across x, so the gable would face the fold, not the road, and the range would read side-on anyway.
//  2. "The shearing dogtrot": the range run on east under one roof over an open-sided shearing floor among the sheep.
//     A true forest form, but an open room that deep under a roof is what holes.py's `open` check refuses, a long roof
//     over an empty bay reads as a barn, and the fold would lose its sky and its grass.
//  3. "The Deep-Eaved Lodge": a low log range on a mossy fieldstone course, its broad 45-degree slate roof swept down
//     past both long walls (eaves at y 5, two blocks out): on the front the sweep rides on log posts as a veranda the
//     whole length of the house, the loom's long window and the door in its shade; at the back it shelters the
//     firewood and the fern bed. Notched corners with log ends crossing past them, a pale stripped band at the loft
//     floor, a broad fieldstone chimney climbing the west gable through the rake. The fold: a split-rail paddock east,
//     a little log lean-to on the gable over the fold door where the flock sleeps on hay. Level II: the dye garden in
//     a clearing behind, log-kerbed beds either side of a dirt walk inside a rail fence, and a hesje, the forest's
//     drying rack of log posts and a rail, hung with the dyed cloth.
//  Built: 3. It reads "forest lodge" before any detail (long, low, dark, horizontal, the roof two blocks deep over a
//  porch on posts), it is the opposite of the snowy look's white walls under a steep snow-capped roof with a closed
//  porch, and every cell the code reads stays put. Not the safest: a broad roof brought down to y 5 is mostly roof, so
//  the veranda's posts, the log ends, the band and the chimney must break it up.
//
// --- Design notes ----------------------------------------------------------------------------------------------------
//  Coordinates as the shipped cottage: x across the front (west 0 -> east 17), y up (0 = the layer on the clicked
//  block), z from the front (z = 0, north) back. The way in is the veranda step on the front.
//  Named parts:
//   Lodge       walls x 1..11, z 2..8: a mossy fieldstone plinth (y 0) and course (y 1), spruce logs y 2..6 lying along
//               each wall, the corners alternating course by course, a stripped-spruce band at the loft floor (y 4); log
//               gables (logs along z) up to the roof; saddle-notched corners, both logs' ends crossing past every corner.
//   Roof        one 45-degree slate range, ridge along x at z 5, y 10; eaves at z 0 and z 10 on y 5 (two blocks past
//               both walls), rakes x 0 and 12 with spruce barge boards under them; the loft lamps hang from its ridge.
//   Veranda     z 0..1 under the front sweep: a spruce deck, log posts at x 1, 7 and 11 on a log plate (y 4) with brackets,
//               lanterns hung from the plate, the step at (9, 0, 0), potted ferns by the door.
//   Weaver's window  the loom's three lights (x 2..6, y 2..3) between stripped-log mullions, a spruce sill, the band its head.
//   Chimney     mossy fieldstone on the west gable: the breast three wide (x 0, z 4..6) to the wall head (y 6), weathered in
//               on stone stairs, one flue (z 5) up through the rake to y 11, its fire on top.
//   Fold        east of the gable door: a spruce rail paddock (x 12..17, z 2..10, the gate on the front at x 15), a log
//               lean-to on the gable (x 12..13, z 3..7, log end walls, a slate pent) over the fold door with hay in it,
//               a lantern on the corner post; grass to graze.
//   Back        under the back sweep: firewood stacked along the wall (x 7..10), ferns in moss under the windows.
//   Garden (II) x 0..11, z 9..16 inside a spruce rail fence: a dirt walk from the back door, two log-kerbed dye beds
//               either side, the hesje (x 9, z 11..15: log posts and a stripped rail at y 3) hung with dyed cloth on both
//               faces, a lantern under the eave.
//  Inside  the shipped furniture cell for cell (../weavers_cottage/plan.mjs FURNITURE); the hearth in the west wall under
//          a fieldstone breast; the loft lamps one chain link shorter (the ridge is a block lower).
//
// --- Design notes: what is done, what is left ------------------------------------------------------------------------
//  Done (2026-11-19): both levels saved as candidates (build.sh -> build/rebuild/weavers_cottage_taiga/
//        weavers_cottage[_2].nbt, never live), sizes as shipped (18x14x12, 18x14x17). Checks: rebuild.py prints no
//        CHECK line at either level; holes.py 0 findings at both (roof, meet, stray, peek, open); look_spots.py
//        weavers_cottage_taiga weavers_cottage: SAME BUILDING (14/14 shipped spots at each level, 0 missing, no chest,
//        door or loom moved; the two loft lamps on one link, not two: listed as allowed); loom 1 (2, 1, 3) facing east
//        and chest (6, 1, 3) facing west (the kept cells), no other job block; fences 27 / 53 (the paddock, the
//        garden's rails, the tables' legs), gate 1; flowers 0 at I and 25 at II (II >= I + 8); II keeps 100 % of I's
//        blocks; about 40 / 56 stacks of the 81. Every block a vanilla 1.21.1 id. Blocks 815 / 968.
//  Renders: build/rebuild/wc_r2 (round 1), wc_r3 (final): taiga_<1|2>_<front|back|left|right|top>.png and the
//        cutaways taiga-<1|2>-cut3 (the workroom) and -cut7 (the loft), front and top; wc_r3/big has a zoomed front.
//  Own render rounds: 1 holes.py found the corners' alternate log ends as gaps in a frame (now saddle notches: both
//        logs cross past every corner on every course, the gables' up to the soffit), the veranda's and the back
//        sweep's pocket at the wall head as an open room (now boarded soffits at y 5), a gap by the end post (a footing
//        stone); the east loft window sat in the slot over the lean-to that kit.seal_slots closes, so the save turned
//        it into a pane with air beyond (the window is gone, the gable's logs are drawn out over the pent to the barge
//        boards); 2 the chimney rose three wide to y 11 and read as a tower on the gable (now the breast is three wide
//        to the wall head, weathered in on stone stairs, one flue through the rake); the firewood was a dark box (now
//        two courses with a shorter one on top); the hesje's two tiers of cloth read as a solid wall (now one rail).
//  Round 2 (2026-11-19): the critic shipped it; of the optional polish only the hesje's posts changed (pale stripped
//        spruce, not dark logs). Checks unchanged: no CHECK line, holes.py 0, SAME BUILDING. Not re-rendered yet.
//  Open for the critic: the flue above the weathering is one block wide for five courses (a pole?); the loft is lit
//        only by two small west gable windows (the east gable is carried out over the lean-to); the hesje's posts
//        stand free in the garden; Lodestone draws spruce logs warm orange, darker in game; Level I has no flowers,
//        so all of II's dye garden is new.
import { FURNITURE as F, OPENINGS as O } from '../weavers_cottage/plan.mjs'

export const SIZE = { 1: [18, 14, 12], 2: [18, 14, 17] } // as shipped
export const IDS = { 1: 'weavers_cottage', 2: 'weavers_cottage_2' }

// PALETTE 60/30/10: 60 dark spruce logs on a mossy fieldstone course; 30 dark slate (deepslate tiles) and fieldstone
// (the course, the chimney); 10 pale stripped spruce (the band, mullions, rails), the spruce posts and deck, and the
// wool's and dye flowers' colours.
export const PALETTE = {
  footing: [['cobblestone', 4], ['mossy_cobblestone', 3]],
  chimney: [['cobblestone', 3], ['mossy_cobblestone', 2], ['stone', 1]], weather: 'cobblestone_stairs',
  log: 'spruce_log', band: 'stripped_spruce_log', mullion: 'stripped_spruce_log', post: 'spruce_log', bracket: 'spruce_stairs',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  barge: 'spruce_stairs', floor: 'spruce_planks', deck: 'spruce_planks', wood: 'spruce', glass: 'glass_pane',
  firewood: 'spruce_log', soil: 'moss_block', fern: 'fern', pot: 'potted_fern', walk: 'dirt_path', rail: 'stripped_spruce_log',
  wool: ['white_wool', 'light_gray_wool', 'brown_wool'],
}

export const HOUSE = {
  walls: { x0: 1, x1: 11, z0: 2, z1: 8 }, room: { x0: 2, x1: 10, z0: 3, z1: 7 }, wallTop: 6, loftY: 4, band: 4,
  roof: { axis: 'x', across: [0, 10], along: [0, 12], eaveY: 5, steep: false }, // ridge y 10 at z 5
  rakes: [0, 12],
}
export const VERANDA = { z0: 0, z1: 1, x0: 1, x1: 11, posts: [1, 7, 11], plate: 4, lamps: [4, 9], pots: [[8, 1, 1], [10, 1, 1]] }
export const CHIMNEY = { x: 0, zs: [4, 5, 6], breast: 6, y1: 11, fire: 5 }

export const OPENINGS = {
  frontDoor: O.frontDoor, foldDoor: O.foldDoor, backDoor: O.backDoor, stairs: O.stairs,
  weaverWindow: { z: 2, x0: 2, x1: 6, y0: 2, y1: 3, lights: [2, 4, 6] },
  windows: [ // single lights: cells, the side they look out of
    { name: 'loom side light', cells: [[1, 2, 3], [1, 3, 3]], out: 'west' },
    { name: 'workroom back west', cells: [[2, 2, 8], [2, 3, 8]], out: 'south' },
    { name: 'workroom back east', cells: [[6, 2, 8], [6, 3, 8]], out: 'south' },
    { name: 'loft west front', cells: [[1, 5, 3], [1, 6, 3]], out: 'west' },
    { name: 'loft west back', cells: [[1, 5, 7], [1, 6, 7]], out: 'west' },
  ],
}

// every furniture spot as shipped; the loft lamps on one link, not two (the ridge is at y 10, a block lower)
export const FURNITURE = F.map(f => f.room === 'Loft' && f.kind === 'lamp' ? { ...f, chain: 1 } : f)
// fleeces (wool, not furniture): behind the kept chest (it faces west: its back wants a block), by the back wall, in the loft
export const FLEECES = [
  [7, 1, 3, 1], [2, 1, 7, 0], [3, 1, 7, 1], [2, 2, 7, 0],
  [2, 5, 4, 'brown_wool'], [2, 5, 5, 0], [2, 6, 4, 1], [2, 5, 6, 1], [3, 5, 7, 2], [4, 5, 7, 0],
]

export const FOLD = {
  fence: [[12, 1, 2], [17, 1, 2], [17, 1, 10], [12, 1, 10], [12, 1, 8]], gate: { at: [15, 1, 2], facing: 'north' },
  shed: { x0: 12, x1: 13, z0: 3, z1: 7, topY: 4 }, // a log lean-to on the gable: end walls at z 3 and 7, its pent falling east
  hay: [[12, 1, 4], [13, 1, 4], [12, 2, 4]],
  ground: { x0: 12, x1: 16, z0: 3, z1: 9 }, trodden: [[15, 3], [14, 5]],
  cornerLamp: [17, 2, 2],
}
export const BACK = { firewood: { x0: 7, x1: 10, z: 9, top: 2 }, ferns: { x0: 1, x1: 3, z: 9 }, lamp: [8, 4, 9] }

// Level II: the dye garden in a clearing behind
export const GARDEN = {
  fence: [[0, 1, 8], [0, 1, 16], [11, 1, 16], [11, 1, 10]],
  walk: { x: 4, z0: 9, z1: 16 },
  beds: [ // soil cells and their log kerbs; plants by row (z), cycled across
    { x0: 1, x1: 2, z0: 11, z1: 15, kerb: [[3, 11, 3, 15, 'z'], [1, 10, 3, 10, 'x']],
      rows: { 11: ['poppy', 'rose_bush'], 12: ['orange_tulip', 'dandelion'], 13: ['cornflower'], 14: ['allium', 'lilac'], 15: ['lily_of_the_valley'] } },
    { x0: 6, x1: 7, z0: 11, z1: 15, kerb: [[5, 11, 5, 15, 'z'], [5, 10, 7, 10, 'x']],
      rows: { 11: ['rose_bush', 'poppy'], 12: ['dandelion', 'sunflower'], 13: ['blue_orchid', 'cornflower'], 14: ['lilac', 'allium'], 15: ['azure_bluet'] } },
  ],
  hesje: { x: 9, z0: 11, z1: 15, posts: [11, 13, 15], rails: [3], cloth: ['red', 'yellow', 'blue', 'magenta', 'orange', 'light_blue', 'lime', 'purple'] },
}
