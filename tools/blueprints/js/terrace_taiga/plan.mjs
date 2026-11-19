// Terrace, the TAIGA look (looks/taiga/terrace[_2], ROADMAP 49.10c, "as 49.10a"): the same row as the shipped Terrace
// (../terrace: its homes, rooms, beds, doors, containers, ladders and every furniture spot in the same cell at both
// levels, the same 13x?x10 and 18x?x10 plots), drawn again for a village in the cool spruce forest. The taiga cottage,
// stone house and townhouse set the language: dark spruce logs notched at the corners over a mossy fieldstone course,
// broad slate gables with deep rakes, porches on log posts, log ends, fieldstone chimneys, firewood under the eaves.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Gabled log row": every home its own log gable to the street, like the shipped row in spruce. A clear row, but it
//     is the snowy look's shape (../terrace_snowy: a steep gable a home), and log gables to the street a home wide end
//     up as tall narrow towers of logs: the two looks would only differ by colour.
//  2. "Dogtrot pair": two log pens a home each with an open breezeway between under one roof. A true forest form, but
//     the brief's party walls sit on a 5-block pitch with no room for a breezeway, and a third home (II) can't join a
//     dogtrot without a second one.
//  3. "The Logging Row": one long, low log range for all the households, its ridge along the street under one broad
//     45-degree slate roof with deep rakes over both end gables; a slate-roofed veranda the whole length of the front on
//     a spruce log post at every party wall and end, a lantern hung in each home's bay; dark spruce log walls notched at
//     the corners on a mossy fieldstone course, the log ends crossing past every corner; a pale stripped-log band at the
//     bedroom floor; a fieldstone stack through the ridge on every wall line (each household's hearth, so the row reads
//     as homes from behind and above); firewood stacked under the end rakes, ferns in moss along the back. II lengthens
//     the range by one bay: one more post, one more stack, the same roof run on.
//  Built: 3. Long, low, dark and horizontal, it is the opposite of the snowy row's saw of tall white gables, and every
//  piece of it is a logging camp's (logs, posts, a veranda, stacks, firewood). Its roof is one plain range with no
//  valley anywhere, so the rakes are clean 45-degree stair lines. Not the safest: a single long roof risks a barn, so
//  the veranda's posts and the stacks on the ridge must cut it into homes.
//
// --- Design notes (2026-11-19) ---------------------------------------------------------------------------------------
//  Coordinates as the shipped Terrace: x across the front (west 0 -> east), y up, z from the front (z = 0) back. Each
//  home in its own frame u = 0..5 across (x = L + u; L 1, 6, 11; u 0 and 5 its side walls), as ../terrace/plan.mjs.
//  Walls    x L..L+5, z 1..8: a mossy fieldstone plinth (y 0) and course (y 1), spruce logs y 2..8 lying along each wall
//           (x on the front and back, z on the end and party walls), the corners alternating course by course; a
//           stripped-spruce band at the bedroom floor (y 5) on the front and back.
//  Roof     one 45-degree range (evenRoof45), ridge along x: eaves at z 0 and 9 (y 8), the ridge two stairs back to back
//           at z 4..5 (y 12), running x 0..east+1 (a rake one block past each end gable). Gables and party walls: logs
//           (axis z) up to the roof.
//  Stacks   on every wall line (x 1, 6, 11 in I; 16 too in II) at z 4..5, y 12..14 in mossy fieldstone, out of the
//           ridge; a campfire on z 4 (y 15), a cobblestone slab on z 5.
//  Veranda  z 0, the whole front: a slate pent at y 5 rising to the wall, from x 0 to east+1; a spruce log post on every
//           wall line (y 1..4) with brackets, a lantern hung in each home's bay (u 2, y 4); a spruce deck (y 0).
//  Log ends past the row's end corners (x 0 and east+1, z 1 and 8) on the even courses, past the back corners (z 9) on
//           the odd ones, and one over each post above the veranda (y 7).
//  Windows  the shipped windows' cells: the front room (u 1..2, y 2..3) on a spruce sill with potted ferns, the bedroom
//           (u 1..2 and u 4, y 6..7) over the veranda, the kitchen (u 2, y 2..3) and bedroom (u 1..2, y 7..8) at the back,
//           the row's end walls one a storey at z 4. Plain panes in the logs: no shutters (spruce on spruce reads as a
//           checkerboard, the Townhouse's lesson).
//  Inside   the shipped furniture cell for cell (../terrace/plan.mjs FURNITURE); the bedroom lamps keep their 2-link
//           chains (the ridge is at y 12, as the shipped ceiling).
//  Outside  the kept rain barrel at (0, 0, 1); firewood under each end rake (z 3..6); ferns in moss beds along the
//           back (u 1..3 of each home).
//  Own render rounds (2026-11-19): 1 the end walls' stacks rose from y 9 inside the log gables and read as stone
//           patches in the logs (every stack now starts at the ridge, y 12, on the gable or party-wall logs); podzol
//           rendered as holes in the lawn (the fern beds are moss now); 2 log ends past every party wall at the back and
//           the party walls' alternating corner logs read as a grid of squares (now only the row's corners are notched:
//           a party wall butts the long front and back logs, which run on past it, and the band runs unbroken).
//  Done (2026-11-19): both levels saved as candidates (build.sh -> build/rebuild/terrace_taiga/terrace[_2].nbt), no
//           CHECK line from rebuild.py, holes.py 0 (open included), look_spots.py SAME BUILDING (24/36 shipped spots, no
//           bed, container, door or ladder moved, no lamp chain changed). Blocks 824/1153; II keeps 98 % of I. Sizes as
//           shipped (13x16x10, 18x16x10; the stacks' campfires at y 15). Every block a vanilla 1.21.1 id. Not shipped.
//  Open for the critic: the long roof is plain on purpose (no valley, no dormer: the Townhouse's lessons), so the homes
//           are told apart by the posts and the stacks; the ridge is two stairs back to back with no cap. The cobble
//           stacks read lighter than the logs.
import { HOMES, at, OPENINGS as T_OPENINGS, FURNITURE, SIZES, IDS } from '../terrace/plan.mjs'

export { HOMES, at, FURNITURE, SIZES, IDS }
export const LEVELS = [1, 2]

// PALETTE 60/30/10: 60 dark spruce logs on a mossy fieldstone course; 30 dark slate (deepslate tiles: the roof and the
// veranda) and fieldstone (the course, the stacks); 10 pale stripped spruce (the band) and the spruce posts and deck.
export const PALETTE = {
  footing: [['cobblestone', 4], ['mossy_cobblestone', 3]],
  stack: [['cobblestone', 3], ['mossy_cobblestone', 2]], capSlab: 'cobblestone_slab',
  log: 'spruce_log', band: 'stripped_spruce_log', post: 'spruce_log', bracket: 'spruce_stairs',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  firewood: [['spruce_log', 3], ['birch_log', 1], ['oak_log', 1]],
  floor: 'spruce_planks', deck: 'spruce_planks', joist: 'dark_oak_log', wood: 'spruce', door: 'spruce',
  glass: 'glass_pane', sill: 'spruce_stairs', soil: 'moss_block', plant: 'fern', pot: 'potted_fern',
}

export const HOUSE = {
  walls: { z0: 1, z1: 8 }, upperFloor: 5, wallTop: 8, band: 5, footing: 1,
  roof: { across: [0, 9], eaveY: 8 }, // along: x 0 .. the east end + 1
  stack: { zs: [4, 5], y0: 12, y1: 14 }, // from the ridge up: the gable and party-wall logs under it
  veranda: { z: 0, y: 5 },
  logEnds: { side: [2, 8], back: [3, 7], front: 7 },
  firewood: [3, 6],
}

export const OPENINGS = {
  door: T_OPENINGS.door, ladder: T_OPENINGS.ladder, endWindows: T_OPENINGS.endWindows,
  windows: [
    { name: 'front room', us: [1, 2], ys: [2, 3], z: 1, out: 'north', dress: 'sill' },
    { name: 'bedroom front', us: [1, 2], ys: [6, 7], z: 1, out: 'north' },
    { name: 'bedroom over the door', us: [4], ys: [6, 7], z: 1, out: 'north' },
    { name: 'kitchen back', us: [2], ys: [2, 3], z: 8, out: 'south' },
    { name: 'bedroom back', us: [1, 2], ys: [7, 8], z: 8, out: 'south' },
  ],
}

export const OUTSIDE = { rainBarrel: [0, 0, 1], ferns: [1, 3], fernZ: 9 }
