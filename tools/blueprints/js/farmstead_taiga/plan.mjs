// Farmstead, the TAIGA look (looks/taiga/farmstead[_2, _3], ROADMAP 49.10e, as 49.10a): the same building as the shipped
// Farmstead (../farmstead/plan.mjs: the hall with its bed, table, hearth and harvest chest, the gallery on the field with
// both composters and their chests, the cross passage, II's barn with the second Farmer's chamber, his bed and chest,
// the loft on its ladder, the side, wagon and hay doors, III's root cellar with its three chests under a hatch, the two
// fields on their pins, and every furniture spot in the same cell and facing at every level, the same 15 x 13 x 12 and
// 15 x 15 x 22 plots), drawn again for a farmer in the cool spruce forest. It speaks the taiga village's language (the
// Cottage, Stone House, Weaver's Cottage, Manor and Daub House looks): dark spruce logs crossing at notched corners on a
// mossy fieldstone footing, broad 45-degree slate gables to the road, pents and galleries on log posts, log ends, a
// fieldstone stack, firewood under the eaves, ferns in moss.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The log longhouse": house, passage and barn as one long log range under one broad slate roof running from the
//     road to the back, the barn's part only a little taller. A true northern farm, but from the road it is a cottage
//     with a tail, and its side is one long dark roof: the as-drawn's range again in logs.
//  2. "The tun": a Norwegian farm cluster, the house, a stabbur (a storehouse on posts with its upper storey jettied out)
//     and a barn as separate log buildings round a yard. The truest Nordic picture, but the brief's kept cells join the
//     hall, the passage and the barn wall to wall (doors at z 8, 9 and 10), so there is no yard to stand them round.
//  3. "The Forest Croft": Level I a low log house GABLE-ON to the road, its broad 45-degree slate gable (ridge y 8) over a
//     slate pent across the front carried on the side walls' crossing log ends, a fieldstone hearth breast on the west
//     with its stack up through the slope, and down the field side a SVALGANG: a boarded gallery under the house's
//     low east eave on a log plate, where the Farmer's composter and harvest chest stand looking onto the rows. Level
//     II sets a tall CROSS BARN behind it, its ridge across the plot (y 12, half again the house's height), a mossy
//     fieldstone byre storey under a log hay storey with a pale stripped band between; its long slope rises behind the
//     house's gable to the road, its two big log gables face the woods (west) and the field (east), the svalgang runs on
//     down the whole field side under one low pent, and at the back a little HOIST GABLET breaks the long eave over the
//     hay door with a lantern under its peak. III digs the root cellar under the hall, as II above the ground.
//  Built: 3, the strongest, not the safest. Its outline is two roofs crossed (a small gable to the road in front of a
//  broad ridge across the plot), which no other Farmstead look and not the as-drawn catslide range has, and it reads
//  "forest farm" from 20 blocks: dark logs, slate, a stone byre, a stack, the gallery's posts. Level I is whole on its
//  own (a lodge and its field) and II grows out of it the way a croft grows: the barn built on behind the house with the
//  gallery carried on along it. The risks: the cross barn's gables are big log walls (they need windows, the band and
//  log ends to break them up), its roof meets the house's in a valley, and the hay door under the long eave needs the
//  gablet.
//
// --- Design notes: done, checked, what is left -----------------------------------------------------------------------
//  Done (2026-11-21): all three levels saved as candidates (build.sh -> build/rebuild/farmstead_taiga/farmstead[_2, _3].nbt,
//    never live), sizes as shipped (15x13x12, 15x15x22, 15x15x22 + the 4-deep cellar). 545 / 1284 / 1443 blocks.
//  Checked: look_spots.py farmstead_taiga farmstead: SAME BUILDING (11 / 17 / 19 shipped spots, 0 missing, 0 beds,
//    containers, doors or ladders moved; every lamp on the shipped chain length, 0); rebuild.py: no CHECK line at any
//    level; holes.py (open included): 0 at all three; every id vanilla 1.21.1; no job block but the composters, at
//    (8, 1, 4) and (8, 1, 13); I: 40 farmland in a 9 x 5 box (x 9..13, z 2..10, all y 0, 4 water in its ditch), 15
//    wheat, 15 carrots, 10 potatoes, one bed, two chests, a carved pumpkin; II: 80 farmland on one level, four chests;
//    III: II above the ground plus the hatch (2, 0, 2), the cellar chests (2, -3, 4..6), a ladder and two lamps down
//    there. About 34 / 55 / 57 stacks of the 81 (counted per item from the .nbt).
//  Renders: build/rebuild/farmstead_taiga/renders/ (taiga_<1|2|3>_<front|back|left|right|top>.png; cutaways
//    taiga_<L>_cut3_<top|front> for the hall, passage and byre, taiga_2_cut7_* for the loft, taiga_3_cut-2_* the cellar).
//  Tried and changed: the barge boards in spruce stairs drew an orange stepped fringe down the barn's big rakes (now
//    dark oak stairs, a dark fascia); a hoist beam out of the gablet's peak read as a loose orange box on the roof
//    (dropped: a lantern hangs under the gablet's peak over the hay door); the stack one block wide read as a pole
//    through the slope (now two wide with a slate cap beside the smoke); a rug through the chamber's doorway left the
//    door 1.94 high (the rug is one cell, by the bed); the passage's crown under the lodge's ridge end was a stray cell
//    for holes.py (closed in slate). The west windows lost their shutters to the log ends and the firewood.
//  Open for the critic: the renderer draws spruce logs warm orange (they are darker in game); the gablet's ridge
//    stands a block proud of the barn's slope at z 19 (a small box from the back); the lodge's west side is mostly
//    roof, stack and log ends; the barn's gables are large log fields with one pair of lights each.
//
// --- Named parts (x across the front, west 0 -> east 14; y up, 0 = the layer on the clicked block; z from the front,
//     z = 0, north, to the back) ---------------------------------------------------------------------------------------
//   Lodge      the house, walls x 1..7, z 1..8: a mossy fieldstone footing (y 0), spruce logs above lying along each
//              wall (y 1..4 on the sides, up to the roof in the gables), the corners alternating course by course; a
//              pale stripped-log band across both gables at y 5 (the tie-beam line); a little window high in the
//              front gable.
//   Roof       a broad 45-degree slate gable, ridge along z at x 4, y 8; eaves at x 0 and x 8 on y 4 (the east one over
//              the svalgang), rakes at z 0 and z 9 with dark barge boards; a spruce finial on each ridge end.
//   Pent       across the front gable at z 0, y 4, between the side walls' log ends (x 1 and 7, crossing out to z 0 as
//              piers), over the door's step and the two front windows; a lantern hung under it by the door, ferns in
//              moss beds under the windows.
//   Hearth     the fire in the west wall (1, 1, 5), a fieldstone breast outside it three wide (x 0, z 4..6) up to the
//              eave, weathered in on stone stairs, the stack two wide up through the west slope (x 1, z 5..6, to y 10), smoke on top.
//   Svalgang   the field walk under the east eave: a spruce deck x 8 (z 1..9 in I, 1..20 in II), a log plate at y 3
//              under the eave on log posts (the house's back corner's log ends at z 8; II posts at z 15 and 20), the
//              step up at the front (8, 0, 0); composters and harvest chests on it in the kept cells.
//   Field      fenced in spruce on a mossy kerb (x 9..14, z 1..11), a gate to the road, farmland round a water ditch
//              ACROSS the field at z 6 (the as-drawn's runs down it), the scarecrow on a footing in the ditch's middle
//              (11, 0, 6) facing the road, lanterns on two corner posts. II: the second field behind (z 12..20), its
//              ditch across at z 16, fenced on.
//   Passage    (II) the old back porch closed in between the house's back gable and the barn: log end walls with a
//              door in each (west to the yard, east to the svalgang), a tie beam with a lantern.
//   Barn       (II) walls x 1..7, z 10..20: a mossy fieldstone byre storey (y 0..3) with small windows, a stripped band at
//              y 4, log walls above; its roof a 45-degree gable ACROSS the plot (ridge along x at z 15, y 12; eaves at
//              z 9 and 21 on y 6, rakes at x 0 and 8) whose front slope takes in the house's back rake in a valley.
//              Big log gables west and east, each with a loft window, log ends at the eave corners, finials.
//   Gablet     (II) over the hay door (4, 6, 20): the back wall raised at x 3..5 into a little gable with its own roof
//              running into the main slope, a lantern hung under its peak over the door.
//   Byre       (II) inside: the threshing floor down the middle, a sheep pen with a gate on the east under the window,
//              hay, the tool wall, the second Farmer's chamber under the loft (bed, chest, nightstand), the loft on the
//              ladder with a rail and hay.
//   Cellar     (III) the root cellar under the hall x 1..7, z 1..8, 4 deep, down the ladder under the hatch (2, 0, 2):
//              the three kept chests on the west wall, produce bins on log shelves along the east, hay at the back.
//   Firewood   split logs stacked end-on under the west eave (x 0, z 2..3), II along the barn's west foot under a slab.

import { FURNITURE as SHIPPED } from '../farmstead/plan.mjs'

export const SIZE = { 1: [15, 13, 12], 2: [15, 15, 22], 3: [15, 15, 22] } // as shipped (w, h, d)
export const BASEMENT = { 3: 4 }
export const IDS = { 1: 'farmstead', 2: 'farmstead_2', 3: 'farmstead_3' }

// Palette (60/30/10): 60 dark spruce logs (and the byre's mossy fieldstone below them) / 30 dark slate (deepslate tiles)
// and fieldstone (footings, the hearth breast and stack, the field's kerb) / 10 pale stripped spruce (the bands, the
// gable finials' posts, the field gate's posts), the spruce deck and the crops' greens and the scarecrow's orange.
export const PALETTE = {
  footing: [['cobblestone', 4], ['mossy_cobblestone', 3], ['stone', 1]],
  byre: [['cobblestone', 5], ['mossy_cobblestone', 4], ['andesite', 1], ['stone', 1]],
  chimney: [['cobblestone', 3], ['mossy_cobblestone', 2], ['stone', 1]], weather: 'cobblestone_stairs',
  kerb: [['mossy_cobblestone', 3], ['cobblestone', 3], ['moss_block', 1]],
  log: 'spruce_log', band: 'stripped_spruce_log', post: 'spruce_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  barge: 'dark_oak_stairs', floor: 'spruce_planks', deck: 'spruce_planks', boards: 'spruce_planks',
  byreFloor: [['coarse_dirt', 3], ['packed_mud', 2]], threshing: 'spruce_planks',
  wood: 'spruce', glass: 'glass_pane', soil: 'moss_block', fern: 'fern', firewood: 'spruce_log',
  cellarWall: [['cobblestone', 4], ['mossy_cobblestone', 3], ['stone', 2]], cellarFloor: 'packed_mud',
}

// The two roof ranges, drawn as one surface (parts/manor_cold.mjs unionRoof): the lodge's gable to the road and II's
// cross barn behind it; the lodge's back rake runs on under the barn's front eave into a valley.
export const ROOFS = {
  lodge: { axis: 'z', across: [0, 8], along: [0, 9], eaveY: 4, walls: { x0: 1, x1: 7, z0: 1, z1: 8 } },
  barn: { axis: 'x', across: [9, 21], along: [0, 8], eaveY: 6, walls: { x0: 1, x1: 7, z0: 10, z1: 20 } },
}

export const HOUSE = { x0: 1, x1: 7, z0: 1, z1: 8, room: { x0: 2, x1: 6, z0: 2, z1: 7 }, band: 5, tieBeams: [4, 7] }
export const BARN = {
  x0: 1, x1: 7, z0: 10, z1: 20, room: { x0: 2, x1: 6, z0: 11, z1: 19 }, stoneTop: 3, band: 4, tieBeams: [12, 14],
  loft: { y: 5, z0: 16, z1: 19, rail: [[3, 6, 16], [6, 6, 16]] },
  ladder: { x: 2, z: 15, y1: 5 }, // the kept ladder (2, 1..5, 15) facing east
  chamber: { wallZ: 16, doorX: 3, wallX: 4, z0: 17, z1: 19 },
  pen: { x0: 5, x1: 6, z0: 11, z1: 14, gateZ: 12 }, // a sheep pen on the east, its fence at x 5
  gablet: { x0: 3, x1: 5, z: 20, eaveY: 8, ridgeY: 9, back: 19 },
  windows: [ // [cells, out]
    [[[1, 2, 12]], 'west'], [[[1, 2, 18]], 'west'], // slits in the byre's stone
    [[[7, 2, 12]], 'east'],
    [[[1, 7, 14], [1, 8, 14], [1, 7, 16], [1, 8, 16]], 'west'], // the west gable's loft lights, a log mullion between
    [[[7, 7, 14], [7, 8, 14], [7, 7, 16], [7, 8, 16]], 'east'], // the east gable's, over the svalgang
    [[[2, 2, 20]], 'south'],
  ],
}
export const PENT = { z: 0, y: 4, x0: 2, x1: 6, lamp: [4, 3, 0] }
export const HEARTH = { x: 0, zs: [4, 5, 6], breastTop: 3, stack: { x: 1, z: 5, top: 10 } }
export const SVALGANG = {
  x: 8, z0: 1, z1: { 1: 9, 2: 20 }, plateY: 3, step: [8, 0, 0],
  posts: { 1: [8], 2: [8, 15, 20] }, pentZ: [10, 20],
}

export const OPENINGS = {
  frontDoor: { at: [4, 1, 1], facing: 'south', out: 'north', step: [4, 0, 0] },
  backDoor: { at: [4, 1, 8], facing: 'north', out: 'south' },
  passageWest: { level: 2, at: [1, 1, 9], facing: 'east', out: 'west', step: [0, 0, 9] },
  passageEast: { level: 2, at: [7, 1, 9], facing: 'west', out: 'east' },
  barnDoor: { level: 2, at: [4, 1, 10], facing: 'south', out: 'north' },
  barnSideDoor: { level: 2, at: [7, 1, 17], facing: 'west', out: 'east' },
  wagonDoors: { level: 2, at: [[5, 1, 20], [6, 1, 20]], facing: 'north', out: 'south' },
  hayDoor: { level: 2, at: [4, 6, 20], facing: 'north', out: 'south' },
  chamberDoor: { level: 2, at: [3, 1, 16], facing: 'south', out: 'north' },
  hatch: { level: 3, at: [2, 0, 2] },
  windows: [ // the lodge's: cells, the side they look out of, dressing
    { name: 'front west', cells: [[2, 2, 1], [2, 3, 1]], out: 'north' },
    { name: 'front east', cells: [[6, 2, 1], [6, 3, 1]], out: 'north' },
    { name: 'front gable', cells: [[4, 6, 1]], out: 'north' },
    { name: 'west front', cells: [[1, 2, 2], [1, 3, 2]], out: 'west' },
    { name: 'west back', cells: [[1, 2, 7], [1, 3, 7]], out: 'west' },
    { name: 'east front', cells: [[7, 2, 3], [7, 3, 3]], out: 'east' },
    { name: 'east middle', cells: [[7, 2, 5], [7, 3, 5]], out: 'east' },
    { name: 'east back', cells: [[7, 2, 7], [7, 3, 7]], out: 'east' },
    { name: 'back', cells: [[2, 2, 8], [2, 3, 8]], out: 'south' },
    { name: 'back gable', cells: [[4, 6, 8]], out: 'south' },
  ],
}

// Every shipped furniture spot, cell for cell (kind, pos, facing): the hall, the gallery's composters and chests, the
// barn's chamber and lamps, the cellar. The lamps keep their chain length (0): each hangs from a tie beam or the loft.
export const FURNITURE = SHIPPED.filter(f => f.kind !== 'rug').concat([
  { room: 'Hall', kind: 'rug', part: 'rug', cellsAbs: [[4, 1, 3], [5, 1, 3], [4, 1, 4], [5, 1, 4], [4, 1, 5], [5, 1, 5], [4, 1, 6], [5, 1, 6]], colour: 'red' },
  { room: 'Barn', level: 2, kind: 'rug', part: 'rug', cellsAbs: [[2, 1, 17]], colour: 'brown' },
])

// The walking lines (as the as-drawn: the brief's Farmers and the hatch)
export const WALK = {
  first: [[1, 0, -1], [8, 0, -1], [8, 1, 0], [8, 1, 1], [8, 1, 2], [8, 1, 3]],
  second: [[4, 0, -1], [4, 0, 0], [4, 1, 1], [4, 1, 2], [4, 1, 7], [4, 1, 8], [4, 1, 9], [8, 1, 9], [8, 1, 12]],
  cellar: [[4, 1, 2], [3, 1, 2], [2, 0, 2], [2, -3, 2], [3, -3, 3], [3, -3, 6]],
}

// The fields: farmland round a ditch across each, crops in rows by z (each row's crop and the ages cycled along x)
export const FIELDS = {
  1: { x0: 9, x1: 13, z0: 2, z1: 10, ditchZ: 6, scarecrow: [11, 6],
    rows: { 2: ['wheat', [7, 5, 6, 7, 4]], 3: ['wheat', [6, 7, 5, 6, 7]], 4: ['carrots', [5, 7, 6, 4, 7]], 5: ['carrots', [7, 6, 7, 5, 6]],
      7: ['potatoes', [6, 7, 4, 7, 5]], 8: ['potatoes', [7, 5, 7, 6, 7]], 9: ['wheat', [3, 4, 2, 4, 3]], 10: ['carrots', [2, 3, 4, 3, 2]] } },
  2: { x0: 9, x1: 13, z0: 12, z1: 20, ditchZ: 16,
    rows: { 12: ['potatoes', [3, 4, 5, 4, 3]], 13: ['potatoes', [5, 6, 7, 6, 5]], 14: ['wheat', [7, 6, 7, 7, 5]], 15: ['wheat', [6, 7, 6, 5, 7]],
      17: ['beetroots', [3, 2, 3, 2, 3]], 18: ['beetroots', [2, 3, 1, 3, 2]], 19: ['carrots', [7, 6, 7, 5, 7]], 20: ['carrots', [5, 7, 6, 7, 6]] } },
}
export const FENCES = {
  1: [[9, 1], [14, 1], [14, 11], [9, 11]], gate1: [12, 1, 1],
  2: [[14, 11], [14, 21], [9, 21]],
  lamps: { 1: [[9, 1], [14, 11]], 2: [[14, 21]] },
}
export const OUTSIDE = {
  ferns: [[2, 0], [6, 0]], // moss beds under the front windows, between the pent's piers
  firewood: { x: 0, z0: 2, z1: 3, top: 1 }, // under the west eave and the front window, end-on
  barnWood: { x: 0, z0: 14, z1: 16, top: 1 }, // II: along the barn's west foot under a slab
}
