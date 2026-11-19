// Noble's Manor, the TAIGA look (looks/taiga/manor[_n], ROADMAP 49.10f, "as 49.10a"): the same house as the shipped
// Manor (../manor: its rooms, beds, containers, doors, the grand stair, the ballroom and its gallery, the cellar hatch and
// every furniture spot in the same cell at every level, the same size per level and the same 4-deep cellar under IV),
// drawn again for the seat of a noble family in the cool spruce forest. The taiga cottage, stone house, townhouse and
// terrace set the language: dark spruce logs notched at the corners on a mossy fieldstone footing, broad slate roofs
// with deep rakes, porches on log posts, fieldstone stacks, firewood under the eaves. This is that village's lord's hall.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Stave hall": the hall as a tall stave church of tiered roofs with dragon-head gables, the wings low around it. A
//     strong outline, but stacked roof tiers can't keep the Long Gallery's floor and lamps in their cells, and a stave
//     church is a temple, not a lord's home: the village would read it as a chapel.
//  2. "Log fort": a palisade of logs round a court, corner blockhouses for the wings. Safe from the cold, but the
//     shipped garden and ballroom open to the outside, and a fort reads as the bandits' camp, not a noble's seat.
//  3. "The Boyar's Lodge": the same H in dark spruce logs on a mossy fieldstone footing, every roof a broad 45-degree
//     slate range run together as one roof: the two cross-wings' log gables stand forward to the road with deep rakes,
//     the hall's long range between them dies into both wings' slopes in clean valleys. Notched corners with log ends
//     crossing past them; a pale stripped-log band at the chambers' floor; a porch tower of fieldstone below and logs
//     above under a broad slate pyramid; a slate lean-to veranda on spruce log posts across the hall's front either
//     side of it, a pent on posts over the front door; two great fieldstone stacks (the kitchen's up the west flank,
//     the hall's at the back); firewood stacked under the west eave. II: a log-fenced kitchen garden behind, berry and
//     fern beds round a lantern post, little firs at the cross. III: the ballroom wing, the broadest gable of the
//     three, a great log-mullioned window to the road; IV: the cellar under spruce beams.
//  Built: 3. It reads "forest lord" before any detail (long, low, dark and horizontal, open porches on posts), it is
//  clearly not the snowy look (pale stone, steep pointed gables, closed entries, snow), and it keeps the house's growth
//  (I an L with its tower, III completes the H). Not the safest: a broad 45-degree roof over an H is mostly roof, so
//  the verandas, log ends and stacks must cut it into parts; the roofs are one surface (unionRoof) so its valleys are
//  made by the slopes, never by stacked stairs.
//
// --- Design notes (2026-11-19) ---------------------------------------------------------------------------------------
//  Coordinates as the shipped Manor: x across the front (west 0 -> east), y up, z from the front (z = 0) back.
//  Named parts:
//   West wing   walls x 1..7, z 1..15: a mossy fieldstone footing (y 0..1), spruce logs y 2..9 lying along each wall
//               between upright corner posts; a stripped band at y 5; the chambers'
//               ceiling y 10; a 45-degree roof, ridge along z at x 4, y 13, eaves x 0 and 8 at y 9, rakes z 0 and 16; log
//               gables front and back.
//   Hall        walls x 7..19, z 5..13, logs to y 6 (the Long Gallery in its roof, floor y 5); 45-degree roof, ridge along
//               x at z 9, y 11 (the gallery's lamps keep the shipped chains), eaves z 4 and 14 at y 6, dying into the
//               wing's slope at x 6 and, at III, the ballroom's at x 20; a log east gable at I-II (rake x 20).
//   Verandas    the hall's two front bays (x 8..10, 16..18, z 3..4): a spruce deck, log posts at z 3, a slate lean-to
//               (stairs y 4 at z 3) closed up under the hall's eave; a lantern, fern pots and a bench under it; the
//               hall's front window (y 2..3) looks out under it.
//   Porch tower x 11..15, z 1..5: fieldstone to y 5, logs to y 12 (the study's ceiling y 10, a loft over it), a broad
//               slate pyramid one block out (eave y 13, point y 16); a slate pent on two log posts over the front door.
//   Ballroom    III: walls x 19..27, z 1..15, logs to y 9; 45-degree roof ridge along z at x 23, y 14; a great window of
//               log mullions in the front gable; the chandeliers hang from the ridge.
//   Stacks      fieldstone: kitchen x 0, z 10..12 to y 13, a fire at (0, 14, 11); hall x 9..11, z 14 to y 12, a fire at
//               (10, 13, 14); slab caps beside each fire. Exactly two campfires on stone columns in I; hearths on slabs.
//   Garden      II: x 7..19, z 14..24 fenced in spruce on a mossy footing, a gate on the walk; four beds of sweet berries
//               and ferns kerbed in logs round a lantern post, firs at the cross, a bench, a woodpile; III runs it on.
//   Cellar      IV: the shipped cellar's box, its casks, chests and pots, under spruce beams.
//  Inside  the shipped furniture cell for cell (../manor/plan.mjs FURNITURE); hanging lamps reach what is over them here
//          (hangTo: the ballroom's three chandeliers hang from its ridge on longer chains), the shipped cellar's casks
//          and the ballroom's bracket lamps (furniture spots) in their cells.
//  Own render rounds (2026-11-19, build/rebuild/mn_r1/shots/taiga_*): 1 the verandas three deep were rooms open to the
//          road (holes.py's open): two deep now, the lean-to closed up under the hall's eave, its window a course lower;
//          2 the log ends past every notched corner read as a grid of end grain (and gaps on alternate courses as
//          strays), and the notch's crossing logs as a checkerboard up every corner: the corners are upright posts now,
//          no loose ends; the firewood lies lengthwise (bark, not crates); 3 where the tower's cap overhangs the hall's
//          slope the kit sealed the slot with end-grain logs: the tower's walls are carried out under it in upright
//          logs (behind: lying ones).
//  Done (2026-11-19): all four levels saved as candidates (build.sh -> build/rebuild/manor_taiga/), no CHECK line from
//          rebuild.py, holes.py 0 (open included), look_spots.py SAME BUILDING (100/100/123/125 shipped spots, no bed,
//          container, door or ladder moved; the ballroom's three chandeliers on longer chains, allowed). Sizes as
//          shipped (21x18x17, 21x18x25, 29x18x25, IV + 4 below). Every block a vanilla 1.21.1 id. Not shipped.
//  Open for the critic: corners as posts, not notches (the culture's notch traded for a calm corner); the ballroom's
//          long east side is logs and tall windows under one plain slope.
//  Round 2 (2026-11-19, the critic's notes, briefs/manor_looks_critic.md): 1 every rake row a solid two-deep slate
//          verge (a full tile under each rake stair and the ridge), the log gable stopping under it; 2 the same verge on
//          L3's front gables (the valley into the tower is the union's, unchanged); 3 the tower's log storey framed:
//          stripped bands at its floor (y 6) and ceiling line (y 10), stripped corner posts, a loft window (13, 11, 1);
//          4 (optional) corners stay posts, now pale stripped spruce on every mass, so the dark walls are framed.
import {
  FURNITURE as M_FURNITURE, OPENINGS as M_OPENINGS, OUTSIDE as M_OUTSIDE, BRACKET_LAMPS, SIZE, BASEMENT as M_BASEMENT,
} from '../manor/plan.mjs'

export const LEVELS = [1, 2, 3, 4]
export const IDS = { 1: 'manor', 2: 'manor_2', 3: 'manor_3', 4: 'manor_4' }
export const SIZES = SIZE
export const BASEMENT = { 4: M_BASEMENT }
export { BRACKET_LAMPS }

// PALETTE 60/30/10: 60 dark spruce logs on a mossy fieldstone footing; 30 dark slate roofs (deepslate tiles) and
// fieldstone (footings, the tower's base, the stacks); 10 pale stripped spruce (bands, mullions, lintels), spruce posts
// and decks, ferns and firewood.
export const PALETTE = {
  log: 'spruce_log', band: 'stripped_spruce_log', post: 'spruce_log',
  footing: [['cobblestone', 4], ['mossy_cobblestone', 3], ['stone', 1]],
  stack: [['cobblestone', 4], ['mossy_cobblestone', 3], ['stone', 1]], cap: 'cobblestone_slab', capStairs: 'cobblestone_stairs',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  door: 'dark_oak', joinery: 'dark_oak', sill: 'spruce_stairs', bracket: 'spruce_stairs', deck: 'spruce_planks',
  floor: 'spruce_planks', lining: 'spruce_planks', partition: 'dark_oak_planks',
  hallFloor: ['polished_andesite', 'polished_diorite'], ballFloor: ['dark_oak_planks', 'spruce_planks'],
  kitchenFloor: 'cobblestone', lobbyFloor: 'mossy_stone_bricks',
  glass: 'glass_pane', hearthSlab: 'smooth_stone_slab', fence: 'spruce_fence', gate: 'spruce_fence_gate',
  cellarWall: [['cobblestone', 3], ['mossy_cobblestone', 2], ['stone', 1]], cellarFloor: 'cobblestone', beam: 'spruce_log',
  firewood: 'spruce_log', fernPot: 'potted_fern',
  garden: { soil: 'grass_block', kerb: 'stripped_spruce_log', path: 'coarse_dirt', fir: 'spruce_leaves', bench: 'spruce' },
  banner: 'green',
}

export const MASSES = {
  wing: { walls: { x0: 1, x1: 7, z0: 1, z1: 15 }, wallTop: 9, upper: 5, ceiling: 10,
    roof: { axis: 'z', across: [0, 8], along: [0, 16], eaveY: 9 } },
  hall: { walls: { x0: 7, x1: 19, z0: 5, z1: 13 }, wallTop: 6, room: { x0: 8, x1: 18, z0: 6, z1: 12 },
    roof: { axis: 'x', across: [4, 14], eaveY: 6 }, along: { 1: [6, 20], 3: [6, 20] } },
  tower: { walls: { x0: 11, x1: 15, z0: 1, z1: 5 }, stoneTop: 5, top: 12, ceiling: 10, cap: { cx: 13, cz: 3, r: 3, y0: 13 } },
  ballroom: { walls: { x0: 19, x1: 27, z0: 1, z1: 15 }, wallTop: 9, room: { x0: 20, x1: 26, z0: 2, z1: 14 },
    roof: { axis: 'z', across: [18, 28], along: [0, 16], eaveY: 9 }, gallery: { x0: 20, x1: 26, z0: 11, z1: 14, y: 5 } },
  verandas: [{ x0: 8, x1: 10 }, { x0: 16, x1: 18 }], // z 3..4, posts on z 3
  doorPent: { x0: 11, x1: 15, z: 0, y: 4, posts: [11, 15] },
  stacks: { kitchen: { x0: 0, x1: 0, z0: 10, z1: 12, flue: [0, 11], top: 13 }, hall: { x0: 9, x1: 11, z0: 14, z1: 14, flue: [10, 14], top: 12 } },
  hearths: [{ at: [10, 1, 13], facing: 'north', breast: [9, 11] }, { at: [1, 1, 11], facing: 'east' }],
  garden: { x0: 7, x1: 19, z0: 14, z1: 24, gate: [13, 24] },
  gardenEast: { x0: 19, x1: 27, z0: 16, z1: 24 },
  cellar: { x0: 1, z0: 8, x1: 13, z1: 15, depth: 4, beams: [10, 13], hatch: M_OPENINGS.hatch,
    casks: [2, 12].flatMap(x => [[x, -3, 10], [x, -3, 11], [x, -3, 12], [x, -3, 13], [x, -2, 11], [x, -2, 12]]) },
}

// Windows: the shipped lights in the log walls: panes, stripped-log mullions between lights and a stripped lintel over
// them, a spruce sill under (a ballroom window's transom a stripped log). The bay is the parlour's.
const W1 = M_OPENINGS.windows[1], W3 = M_OPENINGS.windows[3]
const pick = (list, name) => list.find(w => w.name === name)
export const WINDOWS = [
  ...W1.filter(w => !['wing attic front', 'wing attic back', 'hall east', 'study west', 'study east', 'hall front'].includes(w.name)),
  { ...pick(W1, 'hall front'), y: [2, 3] }, // under the veranda's lean-to
  { ...pick(W1, 'hall east'), until: 2 },
  { name: 'study west', axis: 'z', line: 11, lights: [3], y: [7, 8], out: 'west' },
  { name: 'study east', axis: 'z', line: 15, lights: [3], y: [7, 8], out: 'east' },
  { name: 'tower loft front', axis: 'x', line: 1, lights: [13], y: [11, 11], out: 'north' }, // round 2: the tower's upper face
  { name: 'wing attic front', axis: 'x', line: 1, lights: [4], y: [11, 11], out: 'north' },
  { name: 'wing attic back', axis: 'x', line: 15, lights: [4], y: [11, 11], out: 'south' },
  ...W3.filter(w => !w.name.startsWith('ballroom east')).map(w => ({ ...w, from: 3 })),
  ...[4, 8, 12].map(z => ({ name: `ballroom east ${z}`, axis: 'z', line: 27, lights: [z], y: [2, 7], out: 'east', transom: 5, from: 3 })),
]
export const BAY = { x0: 2, x1: 6, line: 1, y0: 1, g0: 2, g1: 3, top: 4 }

export const OPENINGS = M_OPENINGS
export const FURNITURE = M_FURNITURE

export const OUTSIDE = {
  ...M_OUTSIDE,
  firewood: [{ x: 0, z0: 2, z1: 8 }], // under the wing's west eave, up to the kitchen stack
  fernPots: [[8, 4], [10, 4], [16, 4], [18, 4]], // on the verandas' decks, under the hall's windows
}
