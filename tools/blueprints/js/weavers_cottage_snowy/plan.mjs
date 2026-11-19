// Weaver's Cottage, the SNOWY look (looks/snowy/weavers_cottage[_2], ROADMAP 49.10d, "as 49.10a"): the same workplace
// as the shipped Weaver's Cottage (../weavers_cottage: its workroom and loft, the loom and both chests, its three doors,
// the flight to the loft, the fold through the gable door, Level II's dye garden through the back door, and every
// furniture spot in the same cell at both levels, the same 18x14x12 and 18x14x17 plots), drawn again for a weaver in
// deep snow. The snowy cottage, stone house, terrace and manor set the village's language: pale calcite walls with grey
// dressed stone, steep dark slate roofs carrying snow on their ridges and eave lips, closed entries, broad stacks,
// white birch trim, dark spruce shutters, one continuous drift round the walls.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The shipped laithe in winter": the two-storey range with its long weaver's bands, whitewashed, snow on its
//     45-degree roof. Safe, and only a palette: a 45-degree roof over two full storeys is the plains house, and the
//     loft's long band of lights would sit under a snow-laden eave a block off its head.
//  2. "Snow longhouse": the range run on east under one long roof over a byre, the sheep wintered under the weaver's
//     own roof behind the gable door, hurdle pens inside. True to a cold country, but the fold would vanish indoors
//     (nothing of the pen shows from the road), a long low range is the taiga look's shape, and a byre deep under a
//     roof is the open room holes.py's `open` check refuses wherever the sheep get in.
//  3. "The Snowbound Loomhouse": one storey of white calcite under a STEEP slate roof (two up for one across) that comes
//     down to the workroom's head, so the wool loft lives in the roof and the roof is two thirds of the house; one
//     steep birch-faced dormer over the loom lights the spinners' bench; a closed stone storm porch with its own
//     steep little gable before the door; a broad two-flue stack astride the ridge over the hearth; snow along the
//     ridge, on the eave lips and on the sills, a drift round the walls. The fold beside the east gable keeps its fences
//     and gate but backs onto a grey fieldstone lee wall with a slate snow hood over the hay, where the flock
//     shelters out of the wind. Level II: the walled sun-trap garden behind, calcite walls with snowy copings, dye
//     borders along both walls round a lamp, and the dyed cloth hung to dry under a slate hood on the tall back wall
//     (in snow country cloth dries under cover, not on an open tenter).
//  Built: 3. Its outline says "snow country" from any side (a steep white-gabled slate mass, one dormer, a pointed porch,
//  a fat stack), it is the opposite of the taiga look's long low log range with its open veranda, and every cell the
//  code reads stays put: the loom by the front window, the chests, the three doors, the flight. Not the safest: the
//  steep roof leaves the loft no knee wall, so its light comes from the dormer and the east gable only.
//
// --- Design notes ----------------------------------------------------------------------------------------------------
//  Coordinates as the shipped cottage: x across the front (west 0 -> east 17), y up (0 = the layer on the clicked
//  block), z from the front (z = 0, north) back. The way in is the storm porch on the front.
//  Named parts:
//   Loomhouse   walls x 1..11, z 2..8: calcite y 1..4 on a stone-brick plinth, long-and-short grey quoins, a polished
//               andesite course at the loft floor (y 4) across both gables; the loft floor y 4.
//   Roof        steep (2:1) slate, ridge along x at z 5, y 11; eaves z 1 and 9 at y 4 (a snow-laden lip), rakes x 0 and 12
//               (stair over upside-down stair: one clean steep verge); the loft lamps hang from its ridge (chains 2, as
//               shipped).
//   Dormer      over the weaver's window (x 3..5 face on z 2, y 5..8), birch posts and boards, a dark spruce verge, snow
//               along its ridge: it lights the spinners' bench (4, 5, 3).
//   Weaver's window  the loom's three lights (x 2..6, y 2..3) between stone mullions under an andesite head, on a deep
//               stone sill banked with snow.
//   Storm porch x 8..10, z 0..1: dressed stone cheeks, an andesite lintel over the arch, its own steep gable (ridge y 6)
//               rising above the eave into the main slope, a lantern hung inside, a stone step.
//   Stack       a broad stone-brick stack astride the ridge over the hearth (x 2..3, z 4..6, up to y 12), one fire, slabs.
//   Fold        east of the gable door (x 12..17, z 2..11): a spruce fence along the front with the gate and down the
//               east side to a grey fieldstone lee wall along the back (three high, short returns at x 12 and 17), its
//               snowy coping, a slate snow hood along it over the hay, a lantern on the corner post; grass to graze,
//               trodden by the gate and the door.
//   Woodpile    split rounds under the west rake (x 0, z 5..7), snow on top.
//   Garden (II) x 0..11, z 9..16: calcite walls with stone-brick copings and snow, piers at the back corners; a gravel
//               walk from the back door to the drying hood; dye borders along the west and east walls and before the
//               hood; a lamp post in the middle; the drying rail of dyed cloth under the slate hood on the back wall.
//  Snow    on every ridge (the main, the porch's, the dormer's), on the main eave lips and copings, the sills and the
//          woodpile, as a drift round the walls; never on a rake (each rake's foot stays a stair), a step or a walk.
//  Inside  the shipped furniture cell for cell (../weavers_cottage/plan.mjs FURNITURE; the loft rug, not a spot, moves a
//          block east off the stack's foot); the hearth in the west wall under its breast; fleeces behind the chest.
//
// --- Design notes: what is done, what is left ------------------------------------------------------------------------
//  Done (2026-11-19): both levels saved as candidates (build.sh -> build/rebuild/weavers_cottage_snowy/
//        weavers_cottage[_2].nbt, never live), sizes as shipped (18x14x12, 18x14x17). Checks: rebuild.py prints no
//        CHECK line at either level; holes.py 0 findings at both (roof, meet, stray, peek, open); look_spots.py
//        weavers_cottage_snowy weavers_cottage: SAME BUILDING (14/14 shipped spots at each level, 0 missing, no chest,
//        door or loom moved, every lamp on its shipped chain); loom 1 (2, 1, 3) facing east and chest (6, 1, 3) facing
//        west (the kept cells), no other job block; fences 19 / 21 (the fold's 12 and the tables' legs), gate 1;
//        flowers 0 at I and 23 at II (II >= I + 8); II keeps 98 % of I's blocks; about 48 / 69 stacks of the 81. Every
//        block a vanilla 1.21.1 id. Blocks 839 / 1094.
//  Renders: build/rebuild/wc_r1 (round 1), wc_r2, wc_r3 (final): snowy_<1|2>_<front|back|left|right|top>.png and the
//        cutaways snowy-<1|2>-cut3 (the workroom) and -cut7 (the loft), front and top; wc_r1/big has a zoomed front.
//  Own render rounds: 1 the fold's lee walls stood four high on the east and back and read as a roofless stone box
//        (now one back wall three high with short returns closing the hood's ends, the fence carried down the east
//        side); the dormer's spruce verge rendered bright orange (now dark oak); the weaver's window sat behind a
//        two-high snowy ledge (now a slab sill with a skim of snow); the drying wall behind the garden was a blank
//        calcite slab twelve wide (now grey stone-brick piers at x 0, 4, 7 and 11); 2 the west gable was a bare white
//        triangle (two loft lights now flank the stack under the rakes); five cloth colours instead of eight (stacks).
//  Round 2 (2026-11-19, the critic's notes, briefs/weavers_cottage_looks_critic.md): 1 the storm porch comes to a point:
//        one rake stair a side at y 4 on the cheeks, the ridge one deepslate course at (9, 5, 0..1) with snow on it,
//        calcite only at (9, 4, 0) under it, the lantern rehung at (9, 4, 1) under the ridge (the doorway's two blocks
//        stay clear); 2 the woodpile is one wood (spruce), laid along the wall (axis z), its snow kept; 3 no loose snow
//        layers at (11..12, 0, 0..1) (kept bare); 4 the drying wall is opened by slits under the hood (x 2, 5, 6, 9, y 1..2)
//        so the cloth and the house's ground storey show from the back; optional: the dormer's verge is the main roof's
//        deepslate tiles. Checks after it: rebuild.py no CHECK line, holes.py 0 at both levels, look_spots SAME
//        BUILDING, II keeps 98 % of I, about 46 / 67 stacks, blocks 831 / 1078. Renders for build/rebuild/wc_r4 NOT
//        yet taken (waiting for the Gradle build's render go-ahead): the round-2 changes are unseen.
//  Open for the critic: the stack clears the ridge by one course only (the 14-high plot: the ridge is at y 11, its
//        snow at y 12, the fire at y 13); the west gable's loft lights sit right under the rake with no head; the storm
//        porch is a narrow pointed gable (3 wide) that may read as a white column; the drying wall is four high and
//        blank from the back road; Level I has no flowers (snow), so all of II's dye garden is new.
import { FURNITURE as F, OPENINGS as O } from '../weavers_cottage/plan.mjs'

export const SIZE = { 1: [18, 14, 12], 2: [18, 14, 17] } // as shipped
export const IDS = { 1: 'weavers_cottage', 2: 'weavers_cottage_2' }

// PALETTE 60/30/10: 60 pale calcite walls; 30 dark slate (deepslate tiles) under white snow and grey dressed stone (the
// plinth, quoins, heads, the stack, the fold's lee walls); 10 white birch (the dormer), dark spruce (doors, shutters,
// fences) and the wool's and dye flowers' colours.
export const PALETTE = {
  wall: 'calcite', plinth: 'stone_bricks', quoin: 'stone_bricks', quoinAlt: 'polished_andesite',
  course: 'polished_andesite', lintel: 'polished_andesite', mullion: 'stone_brick_wall', step: 'stone_brick_stairs',
  stack: [['stone_bricks', 1]], capSlab: 'stone_brick_slab',
  field: [['stone', 3], ['andesite', 2], ['cobblestone', 2]], coping: 'stone_bricks', // the lee walls, grey and gathered
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  snow: 'snow',
  post: 'birch_log', dormerFace: 'birch_planks', verge: 'deepslate_tile_stairs', vergeTop: 'deepslate_tiles', // round 2: the main roof's tiles
  floor: 'spruce_planks', wood: 'spruce', glass: 'glass_pane', gravel: 'gravel',
  firewood: 'spruce_log', // round 2: one wood
  wool: ['white_wool', 'light_gray_wool', 'brown_wool'],
}

export const HOUSE = {
  walls: { x0: 1, x1: 11, z0: 2, z1: 8 }, room: { x0: 2, x1: 10, z0: 3, z1: 7 }, wallTop: 4, loftY: 4,
  roof: { axis: 'x', across: [1, 9], along: [0, 12], eaveY: 4, steep: true }, // ridge y 11 at z 5
  rakes: [0, 12],
}
export const DORMER = { cx: 4, face: 2, y0: 5 }
export const PORCH = { x0: 8, x1: 10, z0: 0, z1: 1, top: 3, eaveY: 4 } // cheeks y 1..3, a rake stair a side at y 4, the ridge (9, 5) (round 2)
export const STACK = { xs: [2, 3], zs: [4, 5, 6], y0: 5, y1: 12, fire: [2, 5] }

export const OPENINGS = {
  frontDoor: O.frontDoor, foldDoor: O.foldDoor, backDoor: O.backDoor, stairs: O.stairs,
  weaverWindow: { z: 2, x0: 2, x1: 6, y0: 2, y1: 3, lights: [2, 4, 6] },
  windows: [ // single lights: cells, the side they look out of, dressing
    { name: 'loom side light', cells: [[1, 2, 3], [1, 3, 3]], out: 'west', dress: 'shutters' },
    { name: 'fold light', cells: [[11, 2, 3], [11, 3, 3]], out: 'east', dress: null },
    { name: 'workroom back west', cells: [[2, 2, 8], [2, 3, 8]], out: 'south', dress: 'shutters' },
    { name: 'workroom back east', cells: [[6, 2, 8], [6, 3, 8]], out: 'south', dress: 'shutters' },
    { name: 'loft east gable', cells: [[11, 6, 5], [11, 7, 5]], out: 'east', dress: 'shutters' },
    { name: 'loft west front', cells: [[1, 5, 3], [1, 6, 3]], out: 'west', dress: null }, // under the rake: no shutters, no head
    { name: 'loft west back', cells: [[1, 5, 7], [1, 6, 7]], out: 'west', dress: null },
  ],
}

// Every furniture spot as shipped (cell, kind and facing); the loft rug moves off the stack's foot.
export const FURNITURE = F.map(f => f.part === 'rug' ? { ...f, cellsAbs: [[4, 5, 4], [5, 5, 4], [4, 5, 5], [5, 5, 5]] } : f)
// fleeces (wool, not furniture): behind the kept chest (it faces west: its back wants a block), by the back wall, in the loft
export const FLEECES = [[7, 1, 3, 1], [2, 1, 7, 1], [3, 1, 7, 0], [2, 2, 7, 0], [3, 5, 7, 2], [3, 6, 7, 0], [4, 5, 7, 0]]

export const FOLD = {
  fence: [[12, 1, 2], [17, 1, 2], [17, 1, 9]], gate: { at: [14, 1, 2], facing: 'north' },
  lee: { back: { z: 11, x0: 12, x1: 17 }, returns: [[12, 10], [17, 10]], top: 3 },
  hood: { z: 10, x0: 13, x1: 16, y: 3 }, hay: [[13, 1, 10], [14, 1, 10], [13, 2, 10]],
  ground: { x0: 12, x1: 16, z0: 3, z1: 10 }, trodden: [[14, 3], [12, 6]],
  cornerLamp: [17, 2, 2],
}
export const WOODPILE = { x: 0, z0: 5, z1: 7, top: 2 }

// Level II: the walled sun-trap dye garden behind
export const GARDEN = {
  walls: { x0: 0, x1: 11, z0: 9, z1: 16, top: 2 }, // calcite y 1, coping y 2, snow on it
  piers: [[0, 15], [0, 16], [11, 15], [11, 16], [4, 16], [7, 16]], back: { z: 16, x0: 1, x1: 10, top: 4 }, // the drying wall, grey piers in it
  hood: { z: 15, x0: 1, x1: 10, y: 4 }, slits: [2, 5, 6, 9], rail: { z: 15, x0: 1, x1: 10, y: 3 },
  cloth: ['red', 'yellow', 'blue', 'magenta', 'light_blue'], // five dyes (the 81-stack supply budget: each colour is a stack)
  walk: { x: 4, z0: 9, z1: 14 }, cross: { z: 14, x0: 1, x1: 10 },
  borders: [ // [x0, x1, z0, z1, plants by row or column]: one dye colour a row
    { x0: 1, x1: 2, z0: 10, z1: 13, along: 'z', plants: { 1: ['poppy', 'rose_bush'], 2: ['cornflower', 'allium'] } },
    { x0: 9, x1: 10, z0: 10, z1: 13, along: 'z', plants: { 9: ['dandelion', 'orange_tulip'], 10: ['lilac', 'blue_orchid'] } },
    { x0: 6, x1: 8, z0: 10, z1: 10, along: 'x', plants: { 10: ['lily_of_the_valley', 'oxeye_daisy', 'azure_bluet'] } },
  ],
  lampPost: [6, 12], court: { x0: 5, x1: 8, z0: 11, z1: 13 },
}
