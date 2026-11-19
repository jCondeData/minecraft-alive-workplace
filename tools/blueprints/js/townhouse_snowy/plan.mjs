// Burgher's Townhouse, the SNOWY look (looks/snowy/townhouse[_n], ROADMAP 49.10b, "as 49.10a"): the same house as the
// shipped Townhouse (../townhouse: its rooms, beds, containers, doors, ladders and every furniture spot in the same cell
// at every level, the same size per level and the same 4-deep vault under IV), drawn again for a merchant town in deep
// snow. The snowy cottage and stone house set the village's language: pale calcite walls with grey dressed stone,
// white birch trim, steep dark slate roofs carrying snow on their ridges and eaves, a closed entry, a heavy chimney, one
// continuous drift round the walls. This is that village's richest house: tall, white and closed against the cold.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Snow manor, long side to the street": the ridge turned along x, a steep roof with a cross gable over the door.
//     A grand face, but a 2:1 roof over the 12-deep house rises to y 21 (no room for the ridge's snow in the 22-high
//     plot), and the attic's beds and picture sit under the front gable: they'd end up under a slope.
//  2. "The shipped house in white": the jettied timber gable in calcite and birch with snow on it. Safe, and only a
//     palette: a jetty is a dry-town idea (it shelters a shop window from rain), and in snow it's a ledge to clear.
//  3. "The Snowbound Merchant's House": a sheer white stone gable to the street, no jetty: the ground storey's front is
//     built out flush with the upper floors and two thick, so the shop windows sit deep in their reveals under birch
//     lintels with snow drifted on the sills; a closed stone storm porch before the door, and on top of it the hall's
//     oriel (a glazed bay on birch posts under a snow-capped cap): the one thing that comes forward from the white
//     front. Over it a steep slate roof with bell-cast eaves (a kick at the foot of each slope), snow along the ridge and
//     both eaves, two clean gabled dormers on the long west slope. At the back a massive three-wide stone stack, two flues
//     smoking. II: the counting-house wing, its own steep bell-cast gable to the street a step back, its own storm porch.
//     III: a tall white stone tower behind, grey dressed bands at its floors, under a steep slate spire whose eaves
//     carry a ring of snow and a lightning rod at the peak: the highest thing in the village. IV: the vault below.
//  Built: 3. It reads "snow country" before any detail (white mass, black roofs, snow lines), it is clearly not the
//  taiga look (dark logs, broad low gables, open porches), and the richest house shows its wealth the cold way: thick
//  walls, deep windows, a glazed bay, a double stack and a spire. Not the safest: the flush front costs the jetty's
//  shadow line, so the front's depth has to come from the reveals, the porch and the oriel.
//
// --- Design notes (2026-11-18) ---------------------------------------------------------------------------------------
//  Coordinates as the shipped house: x across the front (west 0 -> east), y up, z from the front (z = 0) back.
//  House    walls x 1..9, z 1..12; the ground storey's front two thick (z 1 and 2: the door at (5, 1, 2) at the back of
//           its reveal, the shop windows' panes at z 2); grey bands at y 5 (the hall floor) and y 9 (the attic floor);
//           knee walls y 10; calcite gables at z 1 and 12. Roof: bell-cast, ridge along z at x 5, y 18; the kick at
//           x 0 and 10 (y 10), the body's eaves at x 1 and 9 (y 11); overhangs z 0 and 13.
//  Porch    stone-brick cheeks at x 4 and 6 on z 0 (y 1..4), a birch lintel at y 4, a lantern under it.
//  Oriel    on the porch: base y 5, birch posts at x 4 and 6, glass at x 5 (y 6..7), its cap at y 8, snow on it; the
//           hall reaches it through the old centre window's cells (5, 6..7, 1).
//  Stack    x 4..6 on z 13, y 0..19, campfires on x 4 and 6 (y 20): the hearth (5, 1, 12) draws into its middle.
//  Wing     II: walls x 10..16, z 4..12 as the shipped wing; its bell-cast roof: kick x 9 and 17 (y 9), ridge x 13, y 15;
//           a storm porch before its door (cheeks x 12 and 14 on z 3).
//  Tower    III: x 13..17, z 13..17, calcite to y 17 with dressed quoins and bands at y 5, 9, 13; a steep slate spire
//           (pyramid, r 3 from y 18, peak y 24), snow round its eave ring, a lightning rod on the peak.
//  Vault    IV: the shipped vault, cell for cell.
//  Snow     on every ridge (three wide: the courses beside it carry it too), along every outer eave, on the reveals'
//           and sills' tops and the porch caps; never on a tread mid-slope (no stripes), never in a valley, never on a
//           step or a walk to a door; one continuous drift skirt round the walls.
//  Dormers  two on the west slope at z 4 and 9 (faces on the knee line x 1, panes y 11..12): slate cheeks, a birch
//           lintel at the apex, a clean stair rake (sideDormer), no snow.
//  Lamps    the shipped furniture cell for cell; only the chains change to the new roofs: the attic's to the ridge (4
//           and 3), the wing bedroom's (5).
//  Own render rounds (2026-11-18): birch barge boards under the rakes read as a pale zigzag (dropped: the steep rake
//           is clean on its own); the first dormers (the kit's dormer, birch posts) read as stepped white crosses, so
//           sideDormer was drawn (two cheeks, an apex, a stair rake); the wing's body eave and the tower's ring shared an
//           x and lost their snow (the eave skips now name their y); the wing's west kick stood as a lone snow piece and
//           a lump at the back valley (dropped where the house's slope covers it).
//  Done (2026-11-18): all four levels saved as candidates (build.sh -> build/rebuild/townhouse_snowy/), each with no
//           CHECK line from rebuild.py (the owner's rules included), holes.py 0 (open included), and look_spots.py SAME
//           BUILDING (38/54/65/71 shipped spots, no bed, container, door or ladder moved; three lamp chains changed).
//           Blocks per level (round 2) 1284/1852/2375/2613; II keeps 95 % of I, III 99 % of II, IV 99 % of III. Sizes as the
//           shipped house (11x22x14, 18x22x14, 19x26x19, IV + 4 below). Not shipped (no SHIP=1): the critic goes first.
//  Round 2 (2026-11-18, the critic's five notes, briefs/townhouse_looks_critic.md): 1 the dormers start flush with
//           their faces (no overhang hanging over the eave), their cheeks hung with the roof's own slate, a birch
//           lintel over the panes, the little gable joined to the slope; no snow on them; 2 snow only on the main ridges,
//           the outer eaves, the tower's eave ring, the porch caps and the sills: no strips at other heights; 3 one
//           ridge strip (the shoulders' second line dropped); 4 a verge on every gable's overhanging rake row: the block
//           under each roof stair an upside-down stair facing the eave, so the edge runs at the pitch; 5 the plinth one
//           dressed course of stone bricks.
//  Round 3 (2026-11-18, the critic's last round): 1 the dormers dropped (they kept reading as cuts in the steep slope);
//           the attic is lit from its two gables (furniture cells unchanged); 2 the back valley between the house and
//           the wing ends in a short level eave block with no snow on it, no V hanging below the walls; 3 the verge from
//           round 2 carries every rake; the gable walls behind it keep the 2:1 outline.
//  Left for the critic: the white gable walls still step behind the verge (a 2:1 gable's outline).
import {
  FURNITURE as TH_FURNITURE, OPENINGS as TH_OPENINGS, MASSES as TH_MASSES, BASEMENT, SIZES, IDS,
} from '../townhouse/plan.mjs'

export { BASEMENT, SIZES, IDS }
export const LEVELS = [1, 2, 3, 4]

// PALETTE 60/30/10: 60 white calcite walls on a grey stone-brick plinth; 30 dark slate roof (deepslate tiles) under
// white snow; 10 grey dressed quoins and bands, white birch trim (lintels, the oriel's posts), dark
// spruce doors and shutters.
export const PALETTE = {
  wall: [['calcite', 1]],
  plinth: [['stone_bricks', 1]], // round 2: one dressed course (the cobble and andesite read as patches)
  stack: [['stone_bricks', 5], ['cracked_stone_bricks', 1]],
  quoin: 'stone_bricks', quoinAlt: 'polished_andesite', course: 'polished_andesite', cheek: 'stone_bricks',
  trim: 'stripped_birch_log', capSlab: 'stone_brick_slab',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  snow: 'snow',
  wood: 'spruce', floor: 'spruce_planks', partition: 'spruce_planks', glass: 'glass_pane', sill: 'stone_brick_stairs',
  kitchenFloor: 'stone_bricks', cellarFloor: 'cobblestone', cellarWall: [['cobblestone', 3], ['stone', 2], ['diorite', 1]],
  finial: 'lightning_rod',
}

export const MASSES = {
  house: { x0: 1, x1: 9, z0: 1, z1: 12, firstY: 5, atticY: 9, kneeTop: 10,
    roof: { axis: 'z', across: [0, 10], along: [0, 13], eaveY: 10 } },
  dormers: { at: [4, 9], y0: 11 }, // two gabled dormers on the west slope (faces on the knee line x 1)
  porch: { x: 5, z: 0, top: 4 },
  oriel: { x0: 4, x1: 6, z: 0, y0: 5, y1: 7, opening: [[5, 6, 1], [5, 7, 1]] },
  stack: { x0: 4, x1: 6, z: 13, y0: 0, y1: 19, flues: [4, 6] },
  wing: { x0: 10, x1: 16, z0: 4, z1: 12, roof: { axis: 'z', across: [9, 17], along: [3, 13], eaveY: 9 },
    porch: { x: 13, z: 3, top: 4 } },
  tower: { ...TH_MASSES.tower, roof: { cx: 15, cz: 15, r: 3, y0: 18 } },
  cellar: TH_MASSES.cellar,
}

const shut = { dress: 'shutters' }, sill = { dress: 'sill', snow: true }
export const OPENINGS = {
  frontDoor: TH_OPENINGS.frontDoor, wingDoor: TH_OPENINGS.wingDoor, stairs: TH_OPENINGS.stairs, attic: TH_OPENINGS.attic,
  partition: TH_OPENINGS.partition, doorways: TH_OPENINGS.doorways,
  // the shop's two windows, deep in the two-thick front: panes at z 2, the reveal at z 1
  deep: [
    [[2, 2, 2], [3, 2, 2], [2, 3, 2], [3, 3, 2]],
    [[7, 2, 2], [8, 2, 2], [7, 3, 2], [8, 3, 2]],
  ],
  windows: [
    { cells: [[1, 2, 5], [1, 3, 5]], out: 'west', ...shut },
    { cells: [[1, 2, 9], [1, 3, 9]], out: 'west', ...shut },
    { cells: [[9, 2, 10], [9, 3, 10]], out: 'east', ...shut, until: 1 },
    { cells: [[3, 2, 12], [3, 3, 12]], out: 'south', ...sill },
    { cells: [[7, 2, 12], [7, 3, 12]], out: 'south', ...sill },
    // the hall's front pair either side of the oriel, on stone sills
    { cells: [[2, 6, 1], [3, 6, 1], [2, 7, 1], [3, 7, 1]], out: 'north', ...sill },
    { cells: [[7, 6, 1], [8, 6, 1], [7, 7, 1], [8, 7, 1]], out: 'north', ...sill },
    { cells: [[1, 6, 2], [1, 6, 3], [1, 7, 2], [1, 7, 3]], out: 'west', ...shut },
    { cells: [[1, 6, 6], [1, 7, 6]], out: 'west', ...shut },
    { cells: [[1, 6, 10], [1, 7, 10]], out: 'west', ...shut },
    { cells: [[9, 6, 2], [9, 7, 2]], out: 'east', ...shut },
    { cells: [[9, 6, 6], [9, 7, 6]], out: 'east', ...shut, until: 1 },
    { cells: [[9, 6, 10], [9, 7, 10]], out: 'east', ...shut, until: 1 },
    { cells: [[3, 6, 12], [3, 7, 12]], out: 'south', ...shut },
    { cells: [[7, 6, 12], [7, 7, 12]], out: 'south', ...shut },
    // the gables: a pair over the oriel and a light at the apex; at the back either side of the stack
    { cells: [[4, 11, 1], [4, 12, 1]], out: 'north', dress: null },
    { cells: [[6, 11, 1], [6, 12, 1]], out: 'north', dress: null },
    { cells: [[5, 14, 1], [5, 15, 1]], out: 'north', dress: null },
    { cells: [[3, 11, 12], [3, 12, 12]], out: 'south', dress: null },
    { cells: [[7, 11, 12], [7, 12, 12]], out: 'south', dress: null },
    // II, the wing
    { cells: [[11, 2, 4], [11, 3, 4]], out: 'north', ...sill, from: 2 },
    { cells: [[15, 2, 4], [15, 3, 4]], out: 'north', ...sill, from: 2 },
    { cells: [[13, 6, 4], [13, 7, 4]], out: 'north', ...shut, from: 2 },
    { cells: [[13, 11, 4], [13, 12, 4]], out: 'north', dress: null, from: 2 },
    { cells: [[16, 2, 8], [16, 3, 8]], out: 'east', ...shut, from: 2 },
    { cells: [[16, 6, 8], [16, 7, 8]], out: 'east', ...shut, from: 2 },
    { cells: [[12, 2, 12], [12, 3, 12]], out: 'south', dress: null, from: 2 },
    { cells: [[12, 6, 12], [12, 7, 12]], out: 'south', dress: null, from: 2 },
    // III, the tower: a window a storey on its open faces, the lookout's on three sides
    { cells: [[15, 2, 17], [15, 3, 17]], out: 'south', ...shut, from: 3 },
    { cells: [[17, 6, 15], [17, 7, 15]], out: 'east', ...shut, from: 3 },
    { cells: [[15, 10, 17], [15, 11, 17]], out: 'south', ...shut, from: 3 },
    { cells: [[13, 10, 15], [13, 11, 15]], out: 'west', ...shut, from: 3 },
    { cells: [[13, 14, 15], [13, 15, 15]], out: 'west', ...shut, from: 3 },
    { cells: [[17, 14, 15], [17, 15, 15]], out: 'east', ...shut, from: 3 },
    { cells: [[15, 14, 17], [15, 15, 17]], out: 'south', ...shut, from: 3 },
  ],
}

// The shipped furniture, cell for cell; only the hanging lamps' chains change to reach the new roofs.
const chains = { '5,13,4': 4, '5,14,9': 3, '13,9,8': 5 }
export const FURNITURE = TH_FURNITURE.map(f => (f.part === 'hangingLamp' && chains[f.pos.join()] !== undefined) ? { ...f, chain: chains[f.pos.join()] } : f)

export const OUTSIDE = {
  // where the drift stays clear: the porch's mouth, the wing door's step and its walk from the street
  bare: [[5, 0], [13, 3], [13, 2], [13, 1], [13, 0]],
}
