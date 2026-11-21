// Artisan's House, the SNOWY look (looks/snowy/artisans_house[_n], ROADMAP 49.10g, "as 49.10a"): the same house as the
// shipped Artisan's House (../artisans_house: its workroom, hall, bedroom and sitting room, the journeyman's wing at II,
// the clay cellar under III, every bed, chest, barrel, furnace, door and ladder, the kiln's fire mouth and stoke hole,
// and every furniture spot in the same cell at every level; the same 16x17x14, 16x17x19 and 16x17x19 + 4 below), drawn
// again for a potter in deep snow. The snowy cottage, stone house, terrace, weaver's cottage and manor set the
// village's language: pale calcite walls on a grey stone-brick plinth with dressed quoins, steep dark slate roofs
// carrying snow on their ridges and eave lips, closed entries, broad stone stacks, dark spruce shutters, one drift
// round the walls.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The whitewashed bottle": the drawn house and its brick bottle kiln, calcite for plaster, snow on the tiles. Safe
//     and only a palette: a jettied timber storey is a mild town's house (the jetty's soffit is a snow shelf), and a
//     bottle kiln's flared lip is a dry country's smoke-maker standing alone in the yard.
//  2. "The kiln under the roof": one long steep slate roof over house and kiln together, the kiln walled in as a
//     kiln hall, its stack through the ridge. True to winter (nobody fires a kiln in a blizzard in the open), but from
//     the road it is one long white box under one roof: the taiga look's long low range, and the kiln, the story,
//     vanishes indoors.
//  3. "The Snowbound Kiln House": a tall, narrow white house, its steep (two up for one across) slate gable to the road,
//     the ground storey a thick-walled calcite skin flush with the upper one (no jetty, no ledge for snow), the way in
//     a closed stone storm porch with its own pointed hood; and beside it, banked against its east wall, a squat
//     round BEEHIVE kiln of red brick on a stone footing, its domed crown stepped in two courses with snow lying in
//     each step, and out of the dome a broad stone-brick stack that stands above the house's eaves and smokes: in deep
//     winter the potter fires a low, heavy, heat-holding kiln, banked into her own house so its warmth comes through
//     the workroom wall; the stack is the warmest thing in the village and the one you see from the hill. The fuel shed
//     is a slate lean-to with a snow lip, the woodpiles snow-capped against the kiln's flank; II's journeyman's room a
//     small steep white gable under the house's back gable, the drying rack under a slate hood (in snow country wares
//     dry under cover); III the clay cellar.
//  Built: 3. It is the strongest, not the safest: a white pointed gable beside a red dome under snow is an outline no
//  other house has (the drawn one's tall gable and bottle, translated, not copied), the snow-crusted dome says "cold"
//  in one look, and it is clearly not the taiga look (no logs, no broad roof, no open porch). The risk is the dome: a
//  stepped dome rings itself in white stripes, so it gets only two steps (the hovel's shoulder and the crown's), each
//  a full ring of snow, never a part ring.
//
// --- Design notes (2026-11-21) ---------------------------------------------------------------------------------------
//  Coordinates as the drawn house: x across the front (west 0 -> east 15), y up (0 = the layer on the clicked block),
//  z from the front (z = 0, north) back. The door is (4, 1, 2), behind the thick front skin and the storm porch.
//  Named parts:
//   House     walls x 1..7, z 1..12: the ground storey's inner front wall z 2 with a calcite skin at z 1 (deep window
//             reveals, the wares barrel set in it beside the door), calcite y 1..8, a polished andesite floor band at
//             y 5, long-and-short quoins; steep slate roof ridge along z at x 4, y 15, eaves x 0 and 8 at y 8 (snow
//             lips), rakes z 0 and 13 (one clean verge each); tie beams at y 9 (z 4 and 9) carry the upper lamps.
//   Porch     a closed storm porch before the door: dressed cheeks x 3 and 5 on z 0 (y 1..4), an andesite lintel, a
//             lantern under it, its own pointed slate hood (ridge (4, 6, 0)) with snow on its ridge.
//   Kiln      a beehive kiln centred (11, 4): a stone-brick footing (r 3), red brick hovel y 1..3, the crown stepped
//             in (r 2, y 4..5, the stack's 3 x 3 from y 6), a thin drift on the shoulder, a deep one on the crown; the fire
//             mouth (7, 1, 4) into the workroom, the stoke hole (14, 1, 4) under a brick hood.
//   Stack     the kiln's flue, 3 x 3 stone brick (x 10..12, z 3..5) from y 6 to y 12 (a brick collar at y 12), a slab cap and a smoking fire at
//             (11, 13, 4).
//   Shed      the fuel and clay shed x 8..10, z 7..12, a slate lean-to off the house wall on two spruce posts, a snow lip.
//   Wing      (II) the journeyman's room, calcite walls x 2..6, z 13..17 on a stone-brick plinth, its steep roof ridge
//             along z at y 10 (eaves x 1 and 7 at y 5, rake z 18), a white back gable with a window.
//   Rack      (II) the drying rack (x 10..12, z 15) under a slate hood on spruce posts, snow on the hood.
//   Cellar    (III) the drawn clay cellar, cell for cell.
//  Snow    on every ridge (house, porch, wing), the eave lips, the dome's two steps, the stack's cap ring, the woodpiles
//          and the rack hood, and as one drift round the walls; never on a rake, a step or the way in.
//  Inside  the drawn furniture cell for cell (../artisans_house/plan.mjs FURNITURE), its lamps on the drawn chains.
//  Own render rounds (build/rebuild/artisans_house_snowy/renders): 1 the upper side windows' shutters stood in the eave
//          row and broke the lip (the side windows are a course lower now, y 6..7); the back windows' sills blocked the
//          wing's roof at II (cleared under it); the main east eave lost its lip over the shed (the shed's skip is only
//          its own slope, y <= 5); the kiln read as a striped cake (no dark hoop, the crown two courses, a thin drift on
//          the shoulder and a deep one on the crown), the stack a grey post (a red brick collar at its head).
//  Done (2026-11-21): all three levels saved as candidates, no CHECK line, holes.py 0 at each (open included),
//          look_spots.py SAME BUILDING (29/33/34 drawn spots, no bed, container, furnace, door or ladder moved, job
//          blocks as drawn), owner rules 0. Not shipped (no SHIP=1): the critic goes first.
//  Open for the critic: the steep (2:1) rakes overhang one block, so the white gables step two courses a column behind
//          a dark verge (the weaver's cottage's way; the manor went to 45 degrees for this); the woodpiles' bark faces.
//  Round 2 (2026-11-21, the critic's four notes, briefs/artisans_house_looks_critic.md): 1 the porch hood is all dark:
//          deepslate tiles at (4, 5, 0) between its rake stairs and a bottom slab ridge at (4, 6, 0); the upper front
//          windows lost their sills (the stone "ears" at x 2 and 6); 2 both roofs at 45 degrees (house ridge (4, 12),
//          eaves y 8; wing ridge y 8, eaves y 5), so each white gable steps one block under one clean stair verge; the
//          gable lights a course lower (y 10..11), snow lips on the true eave courses only, the wing's lamp hung to its
//          ridge (hangTo); 3 the woodpiles laid along z, bark out, y 1..2 on a stone footing, snow on top; 4 the kiln's
//          hovel batters in on brick stairs at y 4 round a one-course crown (r 2), one drift on the crown, and the stack
//          (y 5..11, brick collar at 11) set back a block from the crown's edge. Renders: build/rebuild/
//          artisans_house_snowy/renders2. Checks: no CHECK line, holes 0, look_spots SAME BUILDING (job blocks as drawn;
//          the wing lamp's chain listed as allowed).
import { FURNITURE, OPENINGS, OUTSIDE, SIZE, BASEMENT, MASSES as A_MASSES } from '../artisans_house/plan.mjs'

export { FURNITURE, OPENINGS, OUTSIDE, SIZE, BASEMENT }
export const IDS = { 1: 'artisans_house', 2: 'artisans_house_2', 3: 'artisans_house_3' }

// PALETTE 60/30/10: 60 white calcite walls on a grey stone-brick plinth; 30 dark slate (deepslate tiles) under snow;
// 10 the kiln's red brick (the one warm mass) with grey dressed stone and dark spruce joinery.
export const PALETTE = {
  wall: 'calcite',
  plinth: [['stone_bricks', 6], ['cracked_stone_bricks', 1]],
  quoin: 'stone_bricks', quoinAlt: 'polished_andesite', band: 'polished_andesite', lintel: 'polished_andesite',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  snow: 'snow',
  wood: 'spruce', post: 'spruce_log', tie: 'stripped_spruce_log',
  floor: 'spruce_planks', workFloor: 'polished_andesite',
  glass: 'glass_pane', sill: 'stone_brick_slab', capSlab: 'stone_brick_slab',
  kiln: { base: 'stone_bricks', body: 'bricks', band: 'deepslate_bricks', stairs: 'brick_stairs' },
  stack: [['stone_bricks', 5], ['cracked_stone_bricks', 1]],
  cellarWall: [['stone_bricks', 3], ['cobblestone', 2], ['cracked_stone_bricks', 1]], cellarFloor: 'cobblestone',
}

export const MASSES = {
  house: {
    walls: { x0: 1, x1: 7, z0: 1, z1: 12 }, inner: 2, // the skin at z 1, the inner front wall at z 2 (ground storey)
    groundTop: 4, band: 5, wallTop: 8, ties: [4, 9], tieY: 9,
    roof: { axis: 'z', across: [0, 8], along: [0, 13], eaveY: 8, steep: false }, rakes: [0, 13], // round 2: 45 degrees
  },
  porch: { x0: 3, x1: 5, z: 0, top: 4 },
  kiln: { cx: 11, cz: 4, hovelTop: 3, crownY: 4 }, // the crown r 2 at y 4..5
  stack: { x0: 10, x1: 12, z0: 3, z1: 5, y0: 5, y1: 11 }, // round 2: set back a block on the crown
  shed: A_MASSES.shed,
  wing: {
    walls: { x0: 2, x1: 6, z0: 13, z1: 17 }, top: 5,
    roof: { axis: 'z', across: [1, 7], along: [13, 18], eaveY: 5, steep: false }, rake: 18,
  },
  rack: { posts: [[9, 15], [13, 15]], hoodY: 4, x0: 9, x1: 13, z: 15 },
  cellar: A_MASSES.cellar,
}

// Ground windows of the front sit in the skin (z 1), deep reveals behind them; the rest are the drawn cells.
export const FRONT_GROUND = [
  { name: 'shop west', cells: [[2, 2, 1], [2, 3, 1]], reveal: [[2, 2, 2], [2, 3, 2]] },
  { name: 'shop east', cells: [[6, 2, 1], [6, 3, 1]], reveal: [[6, 2, 2], [6, 3, 2]] },
]
// dressing per drawn window: side windows shuttered, front and back on stone sills (shutters there would collide)
export const DRESS = {
  'workroom west': 'shutters', 'kitchen west': 'shutters', 'bedroom west': 'shutters', 'bedroom east': 'shutters',
  'sitting west': 'shutters', 'sitting east': 'shutters', 'wing west': 'shutters', 'wing east': 'shutters',
}
