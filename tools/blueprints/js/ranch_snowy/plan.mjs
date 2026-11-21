// Ranch, the SNOWY look (looks/snowy/ranch[_2], ROADMAP 49.10e, "as 49.10a"): the same building as the shipped
// as-drawn Ranch (../ranch/plan.mjs: the aisle with the Rancher's smoker and both chests at its end, the four stalls,
// the hay loft up its ladder, the wicket door and the hay door over it, Level II's feed and bunk wing with its barrel,
// double chest, bunk, desk and door, the paddock behind with its man-gap, and every furniture spot in the same cell and
// facing at both levels, the same 13 x 16 x 13 and 21 x 16 x 21 plots), drawn again for a horse ranch in deep snow. The
// snowy cottage, stone house, terrace, manor and weaver's cottage set the village's language: pale calcite walls with
// grey dressed stone, steep dark slate roofs carrying snow on their ridges and eave lips, closed entries, white birch
// trim and dark spruce shutters, one continuous drift round the walls.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The monitor barn in winter": the as-drawn stepped outline (a tall centre, two lean-to sheds, a cupola) in calcite
//     and slate with snow on it. Safe, and only a palette: two shallow lean-tos either side of a clerestory make a
//     trough of snow between the roofs, the cupola has no room over a snow ridge in a 16-tall plot, and a stepped
//     western barn is not a cold country's shape.
//  2. "The longhouse byre": the Rancher's bunk, the tack and the horses under one long low roof, its ridge across the
//     road, a door in the long side. True to a cold country, but the aisle runs from the front to the back (the wicket
//     door and the smoker are kept cells), so the ridge would cross the aisle and the hay door would be a dormer; a long
//     low range is the taiga look's shape, and the two would read as one.
//  3. "The Snow Barn": ONE great steep slate roof (two up for one across) over the aisle and both rows of stalls, its
//     eaves kicked out at the foot (a bell-cast flare) and coming down almost to the ground on both sides, so from 20
//     blocks the barn is a tall dark pointed roof with snow on its ridge and lips. The roof runs three blocks past the
//     front gable as a deep FOREBAY (the "snow porch" of a mountain barn): the doors, a timber hay gallery before the
//     hay door, the hoist beam with its lantern, the firewood and the hay all stand dry and clear of drifts under it,
//     and the recessed white birch gable shows through a great pointed opening. A steep pitching dormer on each slope
//     over the stall partition lights the stalls and breaks the long sides. Level II: the feed and bunk wing as a
//     second, smaller steep gable beside the first (a twin-gabled front), stepped back, calcite with a closed stone
//     storm porch; a valley between the two roofs, the winter's firewood stacked in the slot under it; behind, the
//     paddock, its back closed by a grey fieldstone lee wall with a slate run-in shelter over the hay.
//  Built: 3, the strongest and not the safest. Its outline (one great snow-capped A with a deep dark porch mouth, and at
//  II a lesser A beside it) reads "snow country" from any side and is nothing like the taiga's dark log range with its
//  broad 45-degree roof; every room of the as-drawn barn holds cell for cell (the loft over the aisle, the stalls open to
//  the rafters with their lamps on short chains from the slope). The risk is the side walls: the roof comes so low that
//  the sides are mostly slate, so each side gets a dormer, barred stall lights and a log pilaster under it.
//
// --- Design notes ----------------------------------------------------------------------------------------------------
//  Coordinates as the as-drawn Ranch: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z
//  from the front (z = 0, north) to the back.
//  Named parts:
//   Barn        walls x 1..11, z 3..11: calcite y 1..3 on a stone-brick plinth, stone-brick and andesite quoins; a
//               spruce floor beam (y 4) across both gables; the gables above it white birch boards with spruce studs.
//   Roof        one steep (2:1) slate gable, ridge along z at x 6 (y 13), the body's eaves at x 1 and 11 (y 4), kicked
//               out to x 0 and 12 (y 3, the bell-cast lip, carrying snow); along z 0..12: the rakes at z 0 and 12 one
//               clean upside-down-stair verge each.
//   Forebay     z 0..2 under the roof before the front gable (z 3): a stone-brick walk (x 5..7) with steps at z 0; the
//               hay gallery (y 4, z 1..2, x 4..8) on brackets before the hay door, railed; the hoist beam (y 9) out
//               from the gable with a chain and a lantern; a lantern under the gallery over the doors; firewood stacked
//               to the eave in the west corner, hay in the east, the horse trough sunk by the firewood. Kept cells
//               (9, 0, 2) and (10, 0, 2) (the old drawings' cauldrons) stay open ground.
//   Big doors   the dark oak wicket (6, 1, 3) between two spruce-plank leaves, a spruce lintel; the hay door (6, 5, 3).
//   Stalls      x 2..3 and 9..10, z 4..10, hay-bedded, fronts off the aisle (a board, a gate, a rail), split at z 7;
//               open to the slope above, their lamps on a chain from it; barred lights in the side walls.
//   Hay loft    over the aisle (x 5..7, y 4), on spruce beams at x 4 and 8 carried on posts at z 7, railed along both
//               edges, hay stacked down its sides, the ladder's hatch at (7, 4, 4); its lamp hangs from the ridge.
//   Dormers     a steep pitching dormer on each slope at z 7 (faces x 1 and 11, panes y 4..5), birch faces, slate cheeks
//               of their own roof, snow on their ridges.
//   Wing (II)   walls x 13..19, z 5..11, calcite y 1..4 with quoins, a spruce beam (y 5) and birch gables; its own steep
//               gable (ridge along z at x 16, y 11, eaves x 12 and 20 at y 4), the gable to the road; a boarded ceiling at
//               y 6 over the bunk room; a closed stone storm porch on the door (16, 1, 5) with its own pointed little
//               gable; the valley at x 11..12 against the barn's east slope, the slot under it stacked with firewood.
//   Paddock(II) x 0..20, z 13..20: a spruce fence with corner lamp posts, the man-gap (0, 0, 16) a villager walks
//               through and a horse can't, the double gate on the east; along the back a fieldstone lee wall (x 1..6)
//               under a slate run-in roof over the hay; the trough and a snubbing post.
//  Snow    on every ridge (the barn's capped three wide, the dormers', the wing's, the porch's), on the kicked eave lips,
//          on the lee wall's top, as a drift round the walls; never under the forebay, on a step or a walk.
//  Inside  the as-drawn furniture cell for cell (../ranch/plan.mjs FURNITURE); only the lamps' chains change: the
//          stalls' four hang one link from the slope, the loft's one link from the ridge, the bunk room's straight from
//          its ceiling.
//
// --- Design notes: what is done, what is left ------------------------------------------------------------------------
//  Done (2026-11-21): both levels saved as candidates (build.sh -> build/rebuild/ranch_snowy/ranch.nbt, ranch_2.nbt;
//    887 and 1453 blocks; 13 x 16 x 13 and 21 x 16 x 21, the as-drawn plots).
//  Checked: rebuild.py prints no CHECK line for either level; look_spots.py ranch_snowy ranch: SAME BUILDING (8/8 and
//    13/13 shipped spots, 0 beds, containers, job blocks, doors or ladders moved; only the lamps' chains differ);
//    holes.py (open included) 0 at both levels; every block id vanilla 1.21.1; about 37 stacks for I (limit 108) and 50
//    for II; II keeps 94 % of I's blocks unchanged (60 % needed). Kept cells: smoker (6, 1, 10), chest (5, 1, 10),
//    (7, 1, 10) open floor, (9, 0, 2) and (10, 0, 2) open ground under the forebay, II's barrel (14, 1, 10).
//  Renders: build/rebuild/ranch_snowy/renders/ (snowy_<1|2>_<front|back|left|right|top>.png; cutaways
//    snowy_<L>_cut3_* for the aisle, stalls and bunk room, snowy_<L>_cut7_* for the hay loft).
//  Tried and changed (own render rounds): the kit's sideDormer is drawn for a 45-degree slope and on this 2:1 slope it
//    left open stair halves into the stalls: a new part, ../parts/ranch_snowy_dormer.mjs (steepWallDormer), keeps the
//    main roof's full blocks under its eaves and closes its rake as one verge; snow on the ridge's shoulders read as two
//    stripes from the side: ridge snow only; the back's ground storey was one blank calcite panel: the smoker's flue now
//    rises up it as a stone breast and one flue past the ridge with its fire, barred stall lights either side; the hay
//    in the forebay stepped like a stair: one tidy stack to the eave; the front gable lights' shutters cut into the
//    slope over them: bare lights there; II's east dormer stood half buried in the valley against the wing: II drops it
//    (the main slope runs whole); II's lee wall was a tall blank block from the back: lower (y 0..3), dressed piers at its
//    ends; snow under the run-in shelter left 1.88 headroom (CHECK): kept bare.
//  Round 2 (2026-11-21, briefs/ranch_looks_critic.md, Snowy): 1 the long sides: two steep dormers a slope (z 4 and
//    z 10, one over each stall, barred lights stacked under them; II keeps the west pair only, the wing takes the east
//    slope's foot), the kick left a visible course of stairs, the partition's spruce pilaster on both side walls and
//    spruce posts standing proud under the kicked eave at z 0, 3, 7 and 11 (x 0 and 12), so the ground storey reads in
//    bays; 2 no more snow board: the kick stays stairs and snow lies only on the slate's last course between the dormers
//    (and along the wing's east slope), the drift is one and two layers broken here and there and at the corners, none
//    inside the paddock fence; 3 the tall lee wall is now an open three-sided run-in shelter: dressed stone-brick piers
//    at its back corners, spruce posts in front, a low fieldstone back and sides under a stone-brick coping, a little
//    slate gable with snow on its ridge, so the barn's back gable and stack show over it; 4 the forebay's hay is two
//    high and set back under the roof (x 11, z 1..2); 5 the back: a shuttered window either side of the flue's breast
//    on the ground storey (the gable above already birch boards with spruce studs and two lights). Rechecked: no CHECK
//    line, look_spots SAME BUILDING (8/8, 13/13, 0 moved), holes.py 0 (open included), ~37 / 51 stacks, II keeps 92 %
//    of I; 926 / 1427 blocks. Renders overwritten in build/rebuild/ranch_snowy/renders/ (same names).
//  Round 3 (2026-11-21, round-2 notes): 1 each dormer is now a closed little gable flush with the side wall
//    (../parts/ranch_snowy_dormer.mjs): birch cheeks either side of the panes, slate stairs from eaves at z +- 2 to
//    stairs at z +- 1 meeting a full ridge block over the panes, flush with their tops, two rows deep into the slope;
//    the snow on its ridge two layers lying on it (no slab a block above, no open overhang row); the single snow tile
//    left on the barn's eave between the dormers is gone; 2 the drift is regrouped into runs along the walls (each
//    line cut into pieces of 4 and 3 with one bare block between, two layers in the middle and one at the ends, the
//    corners joined to the longer run, broken at the doors and walks; never a single tile), II's walk and shelter
//    bare cells only at II; 3 the run-in shelter is open on its back and ends (a fieldstone kerb and two spruce rails
//    between the stone-brick piers and the posts) under a real little slate gable whose point faces the back (ridge
//    along z, two full blocks with snow), its back gable boarded. Rechecked: no CHECK line, look_spots SAME BUILDING,
//    holes.py 0 (open included); 873 / 1423 blocks. Renders: build/rebuild/ranch_snowy/renders/; sheets
//    briefs/ranch_looks/snowy_<1|2>_r3_vs_old.png.
//  Open for the critic: the forebay's sides are open under the eaves above the firewood and the hay (on purpose: a
//    porch).
import { FURNITURE as AS_DRAWN } from '../ranch/plan.mjs'

export const IDS = { 1: 'ranch', 2: 'ranch_2' }
export const SIZE = { 1: [13, 16, 13], 2: [21, 16, 21] }

// Palette (60/30/10): steep dark slate and pale calcite / white birch gables over grey dressed stone / dark spruce
// (beams, studs, shutters, doors' leaves, fences), hay gold, snow.
export const PALETTE = {
  wall: 'calcite', plinth: 'stone_bricks', quoin: 'stone_bricks', quoinAlt: 'polished_andesite',
  lintel: 'polished_andesite', step: 'stone_brick_stairs', paving: 'stone_bricks', aisle: 'cobblestone',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  snow: 'snow',
  gable: 'birch_planks', frame: 'spruce_log', wood: 'spruce', boards: 'spruce_planks', glass: 'glass_pane',
  bars: 'iron_bars', bedding: 'hay_block', hay: 'hay_block', loft: 'spruce_planks', firewood: 'spruce_log',
  rim: 'stripped_spruce_log', field: [['stone', 3], ['andesite', 2], ['cobblestone', 2]],
}

export const BARN = {
  walls: { x0: 1, x1: 11, z0: 3, z1: 11 }, wallTop: 3, beamY: 4,
  roof: { axis: 'z', across: [0, 12], along: [0, 12], eaveY: 3, steep: true }, // kick y 3, body eave y 4, ridge y 13
  rakes: [0, 12],
  aisle: { x0: 5, x1: 7, z0: 4, z1: 10 }, loftY: 4, loftBeams: [4, 8], loftPost: 7,
  stalls: [{ z0: 4, z1: 6, gate: 5 }, { z0: 8, z1: 10, gate: 9 }], partition: 7,
  stallSides: [{ x0: 2, x1: 3, front: 4, toward: 'east' }, { x0: 9, x1: 10, front: 8, toward: 'west' }],
  pilaster: 7, // a spruce post up each side wall under the dormer
}

export const FOREBAY = {
  walk: { x0: 5, x1: 7, z0: 0, z1: 2 },
  gallery: { x0: 4, x1: 8, z0: 1, z1: 2, y: 4 },
  hoist: { x: 6, y: 9, z0: 1, z1: 3 },
  lamp: [6, 3, 1],
  firewood: { x: 1, z0: 0, z1: 2, top: 3 },
  hay: [[11, 0, 1], [11, 0, 2], [11, 1, 1], [11, 1, 2]], // round 2 (note 4): two high, back under the forebay's roof
  trough: { water: [[2, 1], [3, 1]], y: 0 },
  keptOpen: [[9, 0, 2], [10, 0, 2]],
}

// the smoker's flue up the back gable: a stone breast three wide to y 3, one flue to y 14 past the ridge, a fire on it
export const STACK = { x0: 5, x1: 7, z: 12, breastTop: 3, flue: 6, top: 14 }

// round 2 (note 1): two dormers a slope, one over each stall (z 4 and z 10), the partition's pilaster between them
export const DORMERS = [
  { z: 4, face: 1, run: 1, y0: 4 }, { z: 10, face: 1, run: 1, y0: 4 },
  { z: 4, face: 11, run: -1, y0: 4, levels: [1] }, { z: 10, face: 11, run: -1, y0: 4, levels: [1] },
]
// round 2 (note 1): spruce posts standing proud under the kicked eave (x 0 and 12) at the bays: the front corner, the
// gable walls and the stall partition, so the low side walls read in bays
export const EAVE_POSTS = { xs: [0, 12], zs: [0, 3, 7, 11], top: 2 }

export const WING = {
  walls: { x0: 13, x1: 19, z0: 5, z1: 11 }, room: { x0: 14, x1: 18, z0: 6, z1: 10 }, wallTop: 4, beamY: 5, ceilingY: 6,
  roof: { axis: 'z', across: [12, 20], along: [4, 12], eaveY: 4, steep: true }, // ridge y 11 at x 16
  porch: { x0: 15, x1: 17, z: 4, top: 3 }, slot: { x: 12, z0: 5, z1: 11, top: 2 },
  lampPost: [14, 0, 3],
}

export const PADDOCK = {
  ring: [[0, 0, 13], [20, 0, 13], [20, 0, 20], [0, 0, 20], [0, 0, 13]],
  manGap: [0, 0, 16], gates: [[20, 0, 16], [20, 0, 17]],
  lampPosts: [[0, 13], [20, 13], [20, 20], [0, 20]],
  // round 2 (note 3): an open three-sided run-in shelter instead of the tall lee wall: a low fieldstone back and sides
  // with a stone-brick coping between dressed piers, spruce posts in front, a little slate gable (ridge along x, y 3)
  shelter: { x0: 1, x1: 6, z0: 18, z1: 20, postTop: 2, roofY: 3 },
  shelterHay: [[2, 0, 19], [3, 0, 19], [4, 0, 19], [5, 0, 19], [3, 1, 19], [4, 1, 19]],
  trough: { water: [[9, 19], [10, 19]], y: 0 }, snubbingPost: [10, 16],
}

export const OPENINGS = {
  barnDoor: { at: [6, 1, 3], facing: 'south', out: 'north', leaves: [5, 7] },
  hayDoor: { at: [6, 5, 3], facing: 'south', out: 'north' },
  ladder: { x: 7, z: 4, y0: 1, top: 4, facing: 'south' },
  gableWindows: [ // the front and back gables' lights over the floor beam (the front's bare: shutters would cut the slope)
    { cells: [[4, 6, 3], [4, 7, 3]], out: 'north', dress: null },
    { cells: [[8, 6, 3], [8, 7, 3]], out: 'north', dress: null },
    { cells: [[5, 6, 11], [5, 7, 11]], out: 'south', dress: 'shutters' },
    { cells: [[7, 6, 11], [7, 7, 11]], out: 'south', dress: 'shutters' },
  ],
  stallLights: { xs: [1, 11], zs: [4, 10], y: 2 }, // under the dormers (round 2)
  backWindows: [ // round 2 (note 5): a window either side of the flue's breast, shuttered like the side's
    { cells: [[3, 2, 11], [3, 3, 11]], out: 'south', dress: 'shutters' },
    { cells: [[9, 2, 11], [9, 3, 11]], out: 'south', dress: 'shutters' },
  ],
  wing: {
    door: { at: [16, 1, 5], facing: 'south', out: 'north' },
    windows: [
      { cells: [[14, 2, 5], [14, 3, 5]], out: 'north', dress: 'shutters' },
      { cells: [[18, 2, 5], [18, 3, 5]], out: 'north', dress: 'shutters' },
      { cells: [[19, 2, 9], [19, 3, 9]], out: 'east', dress: 'shutters' },
      { cells: [[18, 2, 11], [18, 3, 11]], out: 'south', dress: 'shutters' },
    ],
    gableLights: [[16, 7, 5], [16, 8, 5], [16, 7, 11], [16, 8, 11]],
  },
}

// Every furniture spot of the as-drawn Ranch, cell for cell; only the hanging lamps' chains change to reach what is over
// them here (key "x,y,z" -> links).
const CHAINS = { '3,5,5': 1, '3,5,9': 1, '9,5,5': 1, '9,5,9': 1, '6,11,7': 1, '16,5,8': 0 }
const relink = f => (f.part === 'hangingLamp' && CHAINS[f.pos.join(',')] !== undefined ? { ...f, chain: CHAINS[f.pos.join(',')] } : f)
export const FURNITURE = { 1: AS_DRAWN[1].map(relink), 2: AS_DRAWN[2].map(relink) }

// The walking line: up the steps at z 0, through the forebay under the gallery, in at the wicket, down the aisle to the
// smoker (worked from (6, 1, 9)) and the chests; up the ladder to the loft. II: along the front to the wing's porch, in
// to the barrel; round the west side through the man-gap into the paddock.
export const WALK = {
  1: [[6, 0, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 9], [7, 1, 5], [7, 1, 4], [7, 5, 4], [6, 5, 5]],
  2: [[13, 0, 2], [16, 0, 3], [16, 1, 4], [16, 1, 5], [16, 1, 6], [15, 1, 8], [14, 1, 9], [0, 0, 16], [3, 0, 16]],
}
