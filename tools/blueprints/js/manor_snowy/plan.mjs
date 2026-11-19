// Noble's Manor, the SNOWY look (looks/snowy/manor[_n], ROADMAP 49.10f, "as 49.10a"): the same house as the shipped
// Manor (../manor: its rooms, beds, containers, doors, the grand stair, the ballroom and its gallery, the cellar hatch and
// every furniture spot in the same cell at every level, the same size per level and the same 4-deep cellar under IV),
// drawn again for the seat of a noble family in deep snow. The snowy cottage, stone house, townhouse and terrace set the
// village's language: pale calcite walls with grey dressed stone, steep dark slate roofs carrying snow on their ridges
// and eaves, closed entries, heavy stacks, one continuous drift round the walls. This is that village's grandest house.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Frost castle": the H's wings as round snow towers with candle-snuffer cones, a battlemented hall between. A
//     strong outline, but round towers can't hold the wings' square chambers cell for cell (the beds, desks and chests
//     sit in their corners), and battlements are a ledge for snow to pile on: a fortress, not a noble's home.
//  2. "The shipped Manor whitewashed": the brick H in calcite, crow-stepped gables capped with snow. Safe, and only a
//     palette: crow steps are a dry, brick country's gable (each step a shelf of snow), and it would read as the plains
//     Manor in winter, not a snowy culture's own house.
//  3. "The White Hall": the same H, every roof turned STEEP (two up for one across) and run together as one slate roof:
//     the two cross-wings stand forward as tall pointed calcite gables, the hall's long roof between them dies into
//     both wings' slopes in clean valleys (no parapet, no step), the porch tower rises white above the hall's eave
//     to a slate pyramid cap with snow on its point. Snow lies only on the ridges, the eaves' lips and the
//     porch's hood; a drift skirt runs round the walls. Grey dressed quoins and floor bands on the white walls, stone-
//     mullioned windows with stone sills and heads, single lights shuttered in dark spruce, a closed storm porch at the
//     tower's foot, a bay of glass for the parlour, a massive kitchen stack up the west flank and the hall's stack at
//     the back. II: a walled winter garden behind, clipped spruce knots under snow round a lamp. III: the ballroom wing,
//     the tallest, widest gable of the three, a great mullioned window to the road, collar beams hung with chandeliers.
//     IV: a vaulted cellar under the kitchen and hall.
//  Built: 3. It is the strongest and not the safest: one pitch, one surface and three pointed gables read "snow
//  country" in outline from any side, it is clearly not the taiga look (dark logs, broad 45-degree roofs, open
//  porches), and the house grows the way the shipped one does (I an L with its tower, III completes the H). The risk is
//  the steep roofs meeting: so every roof is drawn as one surface (unionRoof), its valleys made by the slopes, never by
//  stacked stairs.
//
// --- Design notes (2026-11-19) ---------------------------------------------------------------------------------------
//  Coordinates as the shipped Manor: x across the front (west 0 -> east), y up, z from the front (z = 0) back.
//  Named parts:
//   West wing   walls x 1..7, z 1..15, calcite y 1..8 (the upper floor y 5 a grey band), the chambers' boarded ceiling
//               y 10; its steep roof ridge along z at x 4, y 15, eaves x 0 and 8 at y 8, rakes z 0 and 16; tall white
//               gables front and back to the point (a gable window lights the attic).
//   Hall        walls x 7..19, z 5..13, y 1..5 (the Long Gallery lives in its roof, floor y 5); steep roof ridge along x
//               at z 9, y 14, eaves z 4 and 14 at y 5, running into the wing's slope (x 5) and, at III, the ballroom's
//               (x 22); a white east gable at I-II (x 19, rake x 20). A boarded collar ceiling at y 11 (z 8..10) over
//               the gallery, so its lamps keep the shipped chains.
//   Porch tower x 11..15, z 1..5, calcite to y 12 with quoins (the study's ceiling at y 10, a loft over it), a
//               slate pyramid cap one block out (eave ring y 13, point y 16, snow on the point); a stone storm porch before the door (cheeks x 12, 14 on z 0) under a snow-capped hood.
//   Ballroom    III: walls x 19..27, z 1..15, y 1..7; steep roof ridge along z at x 23, y 16, eaves x 18 and 28 at y 7;
//               collar beams at y 13 (z 5, 9, 13) carry the chandeliers; a great mullioned window in the front gable.
//   Stacks      kitchen: x 0, z 10..12, three wide to y 15, a fire at (0, 16, 11); hall: x 9..11, z 14 to y 15, a fire
//               at (10, 16, 14); slab caps beside each fire.
//               Exactly two campfires on stone columns in I (StyleGameTests); the hearth fires stand on slabs.
//   Garden      II: x 7..19, z 14..24 walled in calcite with stone piers (snow on the copings), four knots of clipped
//               spruce under snow round a lamp, yews at the cross, a bench; III runs it on behind the ballroom.
//   Cellar      IV: the shipped cellar's box (x 1..13, z 8..15, 4 deep), stone-brick vault ribs, its chests and pots.
//  Snow    on every ridge, on the eave lips, on the porch hood and the garden copings, as a drift skirt round the
//          walls; never on a stair, never mid-slope, never on a doorstep or a walk.
//  Inside  the shipped furniture cell for cell (../manor/plan.mjs FURNITURE); hanging lamps reach what is over them here
//          (hangTo: every chain as shipped, the collars and the gallery's ceiling carry them), the shipped cellar's
//          casks and the ballroom's bracket lamps (furniture spots) in their cells.
//  Own render rounds (2026-11-19, build/rebuild/mn_r1/shots/snowy_*): 1 the chambers' west shutters stood in the eave
//          row and broke the eave into notches (the windows are a course lower, y 6..7, under the eave now); the kitchen
//          stack read as a pole (now three wide on the flank, the hall's three wide at the back, one fire each, slabs
//          beside); the garden's piers every four read as battlements (now only at the corners and the gate); 2 the
//          steep spire read as a stepped chimney behind the hall and its kick ring's snow as a brim: the tower walls rise
//          to y 12 and carry a clean pyramid cap one block out (hip45), snow only on its point; the snow lips stop short
//          of every rake row (no shelves seen end-on from the side) and stay off the cap; the cap's overhang over the
//          hall's slope is closed by the tower's walls carried out under it (holes.py's meet).
//  Done (2026-11-19): all four levels saved as candidates (build.sh -> build/rebuild/manor_snowy/), no CHECK line from
//          rebuild.py, holes.py 0 (open included), look_spots.py SAME BUILDING (100/100/123/125 shipped spots, no bed,
//          container, door or ladder moved, no lamp chain changed). Sizes as shipped (21x18x17, 21x18x25, 29x18x25, IV +
//          4 below). Every block a vanilla 1.21.1 id. Not shipped (no SHIP=1): the critic goes first.
//  Open for the critic: the steep gables' white walls step two courses a column behind their verges (a 2:1 gable's
//          outline in blocks); the tower is short of a hero at III, where the ballroom's gable is the tallest thing.
//  Round 2 (2026-11-19, the critic's four notes, briefs/manor_looks_critic.md): 1 no rake overhang: each gable's roof
//          stands in the wall's own plane, a full tile under every stair, so the white wall steps exactly under one solid
//          dark verge; 2 the tower's own steep spire, eaves on its walls (y 12, walls to y 11), point y 16 with snow on
//          it, in waxed verdigris copper so it reads against the slate behind; 3 with no overhang rows the back rakes end
//          on the eave course (no loose stairs past it); 4 the L3 valley is the union's (unchanged).
//  Round 3 (2026-11-19, the critic's round-2 notes): 1 every roof at 45 degrees (one up per one across: eaves y 9 on
//          the wings and ballroom, y 6 on the hall; ridges 13, 11, 14), so each gable's white wall steps one block under
//          one clean stair verge to a single ridge tile with snow on it; the gallery's lamps hang from the ridge on the
//          shipped chains, the ballroom's from a tie under its ridge; 2 the spire a needle: a ring at y 12, a ring at y 14
//          each over a full course, a 1x1 shaft to y 15, a lightning rod finial; 3 the snow lips stop a block short of
//          every gable, so no lip stands out beside a rake's foot; 4 the valleys are the 45-degree union's (as the taiga
//          look's, which shipped). Only the true eave course takes a lip (at 45 degrees no tile stands under a tread).
import {
  FURNITURE as M_FURNITURE, OPENINGS as M_OPENINGS, OUTSIDE as M_OUTSIDE, BRACKET_LAMPS, SIZE, BASEMENT as M_BASEMENT,
} from '../manor/plan.mjs'

export const LEVELS = [1, 2, 3, 4]
export const IDS = { 1: 'manor', 2: 'manor_2', 3: 'manor_3', 4: 'manor_4' }
export const SIZES = SIZE
export const BASEMENT = { 4: M_BASEMENT }
export { BRACKET_LAMPS }

// PALETTE 60/30/10: 60 white calcite walls on a grey stone-brick plinth; 30 dark slate roofs (deepslate tiles) under
// white snow; 10 grey dressed stone (quoins, bands, mullions, sills, the porch) and dark spruce shutters and joinery.
export const PALETTE = {
  wall: 'calcite',
  plinth: [['stone_bricks', 6], ['cracked_stone_bricks', 1]],
  quoin: 'stone_bricks', quoinAlt: 'polished_andesite', band: 'polished_andesite',
  dress: 'stone_bricks', dressStairs: 'stone_brick_stairs', dressSlab: 'stone_brick_slab', dressWall: 'stone_brick_wall',
  stack: [['stone_bricks', 5], ['cracked_stone_bricks', 1]],
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  // round 2: the tower's spire in verdigris copper (waxed: it keeps its colour), so it reads against the slate behind it
  finial: 'lightning_rod',
  spire: { full: 'waxed_oxidized_cut_copper', stairs: 'waxed_oxidized_cut_copper_stairs', slab: 'waxed_oxidized_cut_copper_slab' },
  snow: 'snow',
  door: 'dark_oak', joinery: 'dark_oak', shutter: 'spruce', collar: 'stripped_spruce_log',
  floor: 'spruce_planks', lining: 'spruce_planks', partition: 'dark_oak_planks',
  hallFloor: ['polished_andesite', 'polished_diorite'], ballFloor: ['dark_oak_planks', 'spruce_planks'],
  kitchenFloor: 'stone_bricks', lobbyFloor: 'polished_andesite',
  glass: 'glass_pane', hearthSlab: 'smooth_stone_slab',
  cellarWall: [['stone_bricks', 3], ['cobblestone', 2], ['cracked_stone_bricks', 1]], cellarFloor: 'cobblestone',
  garden: { soil: 'grass_block', hedge: 'spruce_leaves', yew: 'spruce_leaves', path: 'gravel', bench: 'spruce' },
  banner: 'blue',
}

export const MASSES = {
  wing: { walls: { x0: 1, x1: 7, z0: 1, z1: 15 }, wallTop: 9, upper: 5, ceiling: 10,
    roof: { axis: 'z', across: [0, 8], along: [1, 15], eaveY: 9 } },
  hall: { walls: { x0: 7, x1: 19, z0: 5, z1: 13 }, wallTop: 6, room: { x0: 8, x1: 18, z0: 6, z1: 12 },
    roof: { axis: 'x', across: [4, 14], eaveY: 6 }, along: { 1: [6, 19], 3: [6, 20] } },
  tower: { walls: { x0: 11, x1: 15, z0: 1, z1: 5 }, top: 11, ceiling: 10, spire: { cx: 13, cz: 3, y0: 12 } },
  ballroom: { walls: { x0: 19, x1: 27, z0: 1, z1: 15 }, wallTop: 9, room: { x0: 20, x1: 26, z0: 2, z1: 14 },
    roof: { axis: 'z', across: [18, 28], along: [1, 15], eaveY: 9 },
    gallery: { x0: 20, x1: 26, z0: 11, z1: 14, y: 5 }, collars: { y: 13, x0: 23, x1: 23, zs: [5, 9, 13] } }, // a tie under the ridge for each chandelier
  porch: { x: 13, z: 0, top: 4 },
  stacks: { kitchen: { x0: 0, x1: 0, z0: 10, z1: 12, flue: [0, 11], top: 15 }, hall: { x0: 9, x1: 11, z0: 14, z1: 14, flue: [10, 14], top: 15 } },
  hearths: [{ at: [10, 1, 13], facing: 'north', breast: [9, 11] }, { at: [1, 1, 11], facing: 'east' }],
  garden: { x0: 7, x1: 19, z0: 14, z1: 24, wallTop: 2, piers: [7, 11, 15, 19], gate: [13, 24] },
  gardenEast: { x0: 19, x1: 27, z0: 16, z1: 24, piers: [23, 27] },
  cellar: { x0: 1, z0: 8, x1: 13, z1: 15, depth: 4, ribs: [10, 13], hatch: M_OPENINGS.hatch,
    // the wine casks racked against both end walls, two high in the middle (the shipped cellar's barrels, cell for cell)
    casks: [2, 12].flatMap(x => [[x, -3, 10], [x, -3, 11], [x, -3, 12], [x, -3, 13], [x, -2, 11], [x, -2, 12]]) },
}

// Windows: { cells as the shipped lights (axis 'x' = a wall z = line), y range, out, from/until, kind }
// kind 'mullion': stone mullions between the lights, a stone head and sill; 'shutter': one light, dark spruce shutters
// and a stone sill. The bay is the parlour's (its blinds at y 3 in the wall plane).
const W1 = M_OPENINGS.windows[1], W3 = M_OPENINGS.windows[3]
const pick = (list, name) => list.find(w => w.name === name)
export const WINDOWS = [
  ...W1.filter(w => w.name !== 'wing attic front' && w.name !== 'wing attic back' && w.name !== 'hall east'
    && w.name !== 'gallery east' && w.name !== 'study west' && w.name !== 'study east'
    && w.name !== 'front chamber west' && w.name !== 'great chamber west'),
  { ...pick(W1, 'hall east'), until: 2 },
  { ...pick(W1, 'gallery east') },
  { name: 'front chamber west', axis: 'z', line: 1, lights: [4], y: [6, 7], out: 'west' },
  { name: 'great chamber west', axis: 'z', line: 1, lights: [13], y: [6, 7], out: 'west' },
  { name: 'study west', axis: 'z', line: 11, lights: [3], y: [7, 8], out: 'west' },
  { name: 'study east', axis: 'z', line: 15, lights: [3], y: [7, 8], out: 'east' },
  { name: 'wing attic front', axis: 'x', line: 1, lights: [4], y: [11, 11], out: 'north' },
  { name: 'wing attic back', axis: 'x', line: 15, lights: [4], y: [11, 11], out: 'south' },
  ...W3.filter(w => !w.name.startsWith('ballroom east') && w.name !== 'ballroom gable').map(w => ({ ...w, from: 3 })),
  ...[4, 8, 12].map(z => ({ name: `ballroom east ${z}`, axis: 'z', line: 27, lights: [z], y: [2, 6], out: 'east', transom: 5, from: 3 })),
  { name: 'ballroom gable', axis: 'x', line: 1, lights: [23], y: [10, 12], out: 'north', from: 3 },
  { name: 'ballroom back gable', axis: 'x', line: 15, lights: [23], y: [10, 12], out: 'south', from: 3 },
]
export const BAY = { x0: 2, x1: 6, line: 1, y0: 1, g0: 2, g1: 3, top: 4 }

export const OPENINGS = M_OPENINGS
export const FURNITURE = M_FURNITURE

export const OUTSIDE = {
  ...M_OUTSIDE,
  // where the drift stays clear: the porch's step, the walks to the garden doors
  bare: [[13, 0], [13, 15], [14, 14], [14, 15], [23, 16]],
  shrubs: [[8, 3], [10, 3], [16, 3], [18, 3]], // clipped spruce in stone planters either side of the tower
  lampPosts: [[9, 1], [17, 1]],
  firewood: { x: 0, z0: 3, z1: 7 }, // under the wing's west eave, by the kitchen stack
}
