// Burgher's Townhouse, the TAIGA look (looks/taiga/townhouse[_n], ROADMAP 49.10b, "as 49.10a"): the same house as the
// shipped Townhouse (../townhouse: its rooms, beds, containers, doors, ladders and every furniture spot in the same cell
// at every level, the same size per level and the same 4-deep vault under IV), drawn again for a merchant town in the
// cool spruce forest. The taiga cottage and stone house set the village's language: dark spruce logs notched at the
// corners over a mossy fieldstone course, broad slate gables to the road with deep eaves, porches on log posts, log
// ends, fieldstone chimneys, firewood stacked under the eaves. This is that village's richest house: a merchant's
// lodge on the market street, like the gabled wharf houses of a northern trading town.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Stave hall": stacked steep roofs one over another, carved ridge ends, a stave church's outline. Striking, but its
//     tiers would cut through the hall and the attic at fixed heights, and steep tiers are the snowy look's language.
//  2. "Long log house": the ridge along the street, one long veranda under a low eave. A lodge, but it puts the attic's
//     front beds and picture under a slope, and the snowy look already shows the street a tall front: two long faces
//     side by side would blur where the cultures part.
//  3. "The Merchant's Lodge": the jettied gable kept and made broad: a 45-degree slate roof (ridge y 15, not the plains
//     house's steep 19) over a gable of horizontal spruce logs, deep rakes, a hoist beam with its
//     lantern under the apex; the ground storey mossy fieldstone, the hall and attic storeys a notched spruce log pen
//     jettied out over a full-width pent porch on log posts (ferns in moss beds, a lantern by the door). A two-wide
//     fieldstone chimney climbs the west eave past the ridge with firewood stacked beside it; the hearth's stack rises
//     at the back. II: the counting-house wing, a lower broad log gable beside the first (a pair of gables to the
//     street, like a wharf row), its own little gabled porch on posts. III: a square lookout tower behind, fieldstone
//     below and a log pen above, under a broad hipped roof with deep eaves, the highest roof of the three, its broad hip a hat over the lookout.
//     IV: the vault below.
//  Built: 3. A row of broad, low, dark gables reads "taiga" at once and is the opposite of the snowy look's tall white
//  front and needle spire; every piece is a forest-town merchant's (logs, a hoist, a porch, firewood, two stacks), and
//  each level adds one more gable to the row. Not the safest: the low roof leaves the attic little height, so its lamps
//  hang on short chains and its light comes only from the gables.
//
// --- Design notes (2026-11-18) ---------------------------------------------------------------------------------------
//  Coordinates as the shipped house: x across the front (west 0 -> east), y up, z from the front (z = 0) back.
//  House    fieldstone ground storey x 1..9, z 2..12 (y 1..4); a notched spruce log pen x 1..9, z 1..12 from the sill
//           course (y 5, jettied a block out over the porch at z 1) to the knee walls (y 10); log gables at z 1 and
//           12; a 45-degree roof, ridge along z at x 5, y 15, eaves at x 0 and 10 (y 10), rakes over z 0 and 13;
//           pale stripped-log bands at the floor lines (y 5, the jetty's sill, and y 9); the hoist beam under the apex at (5, 14, 0), its chain and lantern.
//  Porch    a slate pent across the front (z 0, y 5) on log posts at x 0, 3, 7 and 10 with brackets, a lantern over
//           the door; log posts under the jetty's corners (x 1 and 9 on z 1); ferns in moss beds either side.
//  Stacks   the hearth's at the back (x 5, z 13, breast x 4..6 to y 4), to y 16, smoking; a two-wide fieldstone stack
//           on the west eave (x 0, z 7..8) to y 16, smoking; firewood stacked under the west eave (x 0, z 2..3).
//  Wing     II: fieldstone x 10..16, z 4..12 (y 1..4), a log pen y 5..9, a 45-degree roof (ridge x 13, y 13; eaves
//           x 9 and 17), log gables at z 4 and 12; a little gabled porch on posts before its door (z 2..3).
//  Tower    III: x 13..17, z 13..17: fieldstone y 1..4, a log pen y 5..18 (a ceiling at y 18 over the lookout), a
//           broad 45-degree hipped roof with deep eaves all round (x 12..18, z 12..18 at y 18, peak y 21).
//  Dormer   one gabled dormer in the west slope at z 4 (face x 1, panes y 11..12), log cheeks, a clean stair rake.
//  Windows  shutters on the fieldstone storeys only; in the logs plain panes (spruce shutters on spruce logs read as a
//           checkerboard of squares).
//  Vault    IV: the shipped vault, cell for cell.
//  Lamps    the shipped furniture cell for cell; only the chains change to the lower roofs: the attic's (1 and 0), the
//           wing bedroom's (3).
//  Own render rounds (2026-11-18): spruce barge boards read as an orange zigzag under the rakes (dropped); spruce
//           shutters on spruce logs and the tower's notched corners read as a checkerboard of squares (shutters only on
//           the fieldstone now, the tower on upright corner posts with pale bands at its floors); the tower's first gable
//           roof read as a tall barn behind the house (now a broad hip, a hat over the lookout); one dormer on the long
//           west slope; the woodpile in mixed woods so it reads as firewood, not a crate.
//  Done (2026-11-18): all four levels saved as candidates (build.sh -> build/rebuild/townhouse_taiga/), each with no
//           CHECK line from rebuild.py (the owner's rules included), holes.py 0 (open included), and look_spots.py SAME
//           BUILDING (38/54/65/71 shipped spots, no bed, container, door or ladder moved; three lamp chains changed).
//           Blocks per level 1067/1604/2054/2292; II keeps 99 % of I, III 99 % of II, IV 100 % of III. Sizes as the
//           shipped house. Not shipped (no SHIP=1): the critic goes first.
//  Left for the critic: the porch's four log posts are heavy; the tower's faces are plain logs between the bands.
import {
  FURNITURE as TH_FURNITURE, OPENINGS as TH_OPENINGS, MASSES as TH_MASSES, BASEMENT, SIZES, IDS,
} from '../townhouse/plan.mjs'

export { BASEMENT, SIZES, IDS }
export const LEVELS = [1, 2, 3, 4]

// PALETTE 60/30/10: 60 dark spruce logs over mossy fieldstone (cobble, mossy cobble, stone, andesite); 30 dark slate
// roof (deepslate tiles); 10 stripped spruce trim (lintels, the hoist), spruce joinery, ferns and firewood.
export const PALETTE = {
  stone: [['cobblestone', 5], ['mossy_cobblestone', 3], ['stone', 1], ['andesite', 1]],
  plinth: [['mossy_cobblestone', 2], ['cobblestone', 2], ['mossy_stone_bricks', 1]],
  stack: [['cobblestone', 4], ['mossy_cobblestone', 3], ['stone', 1]],
  quoin: 'stone_bricks', quoinAlt: 'mossy_stone_bricks', cap: 'mossy_stone_bricks',
  log: 'spruce_log', post: 'spruce_log', trimLog: 'stripped_spruce_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  bracket: 'spruce_stairs', woodCap: 'spruce_slab',
  wood: 'spruce', floor: 'spruce_planks', partition: 'spruce_planks', glass: 'glass_pane', sill: 'cobblestone_stairs',
  kitchenFloor: 'stone_bricks', cellarFloor: 'cobblestone', cellarWall: [['cobblestone', 3], ['stone', 2], ['mossy_cobblestone', 1]],
  firewood: ['spruce_log', 'oak_log', 'spruce_log', 'birch_log'], bedSoil: 'moss_block', plants: ['fern'], // split rounds end-on, mixed woods
}

export const MASSES = {
  house: { x0: 1, x1: 9, z0: 2, z1: 12, jetty: 1, firstY: 5, atticY: 9, kneeTop: 10,
    roof: { axis: 'z', across: [0, 10], along: [0, 13], eaveY: 10, steep: false } },
  porch: { x0: 0, x1: 10, z: 0, y: 5, posts: [0, 3, 7, 10], lamps: [5], jettyPosts: [1, 9] },
  hoist: { x: 5, y: 14, z: 0 },
  backStack: { x: 5, z: 13, top: 16 },
  sideStack: { x: 0, zs: [7, 8], top: 16 },
  woodpile: { x: 0, zs: [2, 3], top: 3 },
  wing: { x0: 10, x1: 16, z0: 4, z1: 12, roof: { axis: 'z', across: [9, 17], along: [3, 13], eaveY: 9, steep: false },
    porch: { cx: 13, half: 2, y: 3, z0: 2, z1: 3, posts: [11, 15] } },
  tower: { ...TH_MASSES.tower, logTop: 18, roof: { x0: 12, z0: 12, x1: 18, z1: 18, eaveY: 18 } },
  dormer: { z: 4, y0: 11 }, // a gabled dormer on the west slope over the hall's couch, lighting the attic's front
  cellar: TH_MASSES.cellar,
}

const shut = { dress: 'shutters' }
export const OPENINGS = {
  frontDoor: TH_OPENINGS.frontDoor, wingDoor: TH_OPENINGS.wingDoor, stairs: TH_OPENINGS.stairs, attic: TH_OPENINGS.attic,
  partition: TH_OPENINGS.partition, doorways: TH_OPENINGS.doorways,
  windows: [
    // house, ground storey (fieldstone), under the porch
    { cells: [[2, 2, 2], [3, 2, 2], [2, 3, 2], [3, 3, 2]], out: 'north', ...shut },
    { cells: [[7, 2, 2], [8, 2, 2], [7, 3, 2], [8, 3, 2]], out: 'north', ...shut },
    { cells: [[1, 2, 5], [1, 3, 5]], out: 'west', ...shut },
    { cells: [[1, 2, 9], [1, 3, 9]], out: 'west', dress: null },
    { cells: [[9, 2, 10], [9, 3, 10]], out: 'east', ...shut, until: 1 },
    { cells: [[3, 2, 12], [3, 3, 12]], out: 'south', ...shut },
    { cells: [[7, 2, 12], [7, 3, 12]], out: 'south', ...shut },
    // house, the hall storey (logs)
    { cells: [[2, 6, 1], [3, 6, 1], [2, 7, 1], [3, 7, 1]], out: 'north', dress: null },
    { cells: [[5, 6, 1], [5, 7, 1]], out: 'north', dress: null },
    { cells: [[7, 6, 1], [8, 6, 1], [7, 7, 1], [8, 7, 1]], out: 'north', dress: null },
    { cells: [[1, 6, 2], [1, 6, 3], [1, 7, 2], [1, 7, 3]], out: 'west', dress: null },
    { cells: [[1, 6, 10], [1, 7, 10]], out: 'west', dress: null },
    { cells: [[9, 6, 2], [9, 7, 2]], out: 'east', dress: null },
    { cells: [[9, 6, 6], [9, 7, 6]], out: 'east', dress: null, until: 1 },
    { cells: [[9, 6, 10], [9, 7, 10]], out: 'east', dress: null, until: 1 },
    { cells: [[3, 6, 12], [3, 7, 12]], out: 'south', dress: null },
    { cells: [[7, 6, 12], [7, 7, 12]], out: 'south', dress: null },
    // the gables: a pair either side of the hoist at the front, either side of the stack at the back
    { cells: [[3, 11, 1], [3, 12, 1]], out: 'north', dress: null },
    { cells: [[7, 11, 1], [7, 12, 1]], out: 'north', dress: null },
    { cells: [[3, 11, 12], [3, 12, 12]], out: 'south', dress: null },
    { cells: [[7, 11, 12], [7, 12, 12]], out: 'south', dress: null },
    // II, the wing
    { cells: [[11, 2, 4], [11, 3, 4]], out: 'north', ...shut, from: 2 },
    { cells: [[15, 2, 4], [15, 3, 4]], out: 'north', ...shut, from: 2 },
    { cells: [[13, 6, 4], [13, 7, 4]], out: 'north', dress: null, from: 2 },
    { cells: [[13, 10, 4], [13, 11, 4]], out: 'north', dress: null, from: 2 },
    { cells: [[16, 2, 8], [16, 3, 8]], out: 'east', ...shut, from: 2 },
    { cells: [[16, 6, 8], [16, 7, 8]], out: 'east', dress: null, from: 2 },
    { cells: [[12, 2, 12], [12, 3, 12]], out: 'south', dress: null, from: 2 },
    { cells: [[12, 6, 12], [12, 7, 12]], out: 'south', dress: null, from: 2 },
    // III, the tower: a window a storey on its open faces, the lookout's on three sides, one in its back gable
    { cells: [[15, 2, 17], [15, 3, 17]], out: 'south', ...shut, from: 3 },
    { cells: [[17, 6, 15], [17, 7, 15]], out: 'east', dress: null, from: 3 },
    { cells: [[15, 10, 17], [15, 11, 17]], out: 'south', dress: null, from: 3 },
    { cells: [[13, 10, 15], [13, 11, 15]], out: 'west', dress: null, from: 3 },
    { cells: [[13, 14, 15], [13, 15, 15]], out: 'west', dress: null, from: 3 },
    { cells: [[17, 14, 15], [17, 15, 15]], out: 'east', dress: null, from: 3 },
    { cells: [[15, 14, 17], [15, 15, 17]], out: 'south', dress: null, from: 3 },
  ],
}

// The shipped furniture, cell for cell; only the hanging lamps' chains change to reach the lower roofs.
const chains = { '5,13,4': 1, '5,14,9': 0, '13,9,8': 3 }
export const FURNITURE = TH_FURNITURE.map(f => (f.part === 'hangingLamp' && chains[f.pos.join()] !== undefined) ? { ...f, chain: chains[f.pos.join()] } : f)

export const OUTSIDE = {
  fernBeds: [{ x0: 1, x1: 2, z: 0 }, { x0: 8, x1: 9, z: 0 }], // under the porch, either side of the windows
}
