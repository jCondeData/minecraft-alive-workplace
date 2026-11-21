// Farmstead, the SNOWY look (looks/snowy/farmstead[_2|_3], ROADMAP 49.10e, as 49.10a): the same building as the shipped
// Farmstead (../farmstead/plan.mjs: the hall with its hearth, bed, harvest chest and table, the gallery on the field
// side with the Farmer's composter and chest, the 9 x 5 field round its channel with the scarecrow, Level II's cross
// passage, barn (the second Farmer's chamber, loft, ladder, stalls, wagon and hay doors), gallery, second composter and
// chest and second field, Level III's root cellar down a ladder under a hatch; every bed, chest, door, ladder and
// furniture spot in the same cell and facing at every level, the same 15 x 12 and 15 x 22 plots), drawn again for a farm
// in deep snow. It speaks the snowy village's language (the Starter Cottage, Stone House, Weaver's Cottage, Manor and
// Daub House looks): pale calcite walls on a grey stone-brick foot with long-and-short quoins and a polished-andesite
// band, STEEP (two up for one across) dark slate roofs carrying snow on their ridges and eave lips, white birch in the
// gables, dark spruce doors and shutters, a broad stone stack, one continuous drift round the walls.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The snow longhouse": house and barn in one long range under one steep roof, ridge along z (gable to the road),
//     a snow gallery under the whole east eave. Unity, but it is the shipped Farmstead's own idea (one roof line growing
//     back, a gallery under the east eave) with the pitch raised: a palette, and a long low range is the taiga look's.
//  2. "The A-frame farm": the house an A-frame whose roof reaches the ground on both sides, the gallery inside its east
//     skirt. The strongest outline, but the hall's cupboard, clock and bed stand against walls the A-frame would slope
//     over (no headroom at x 2 and x 6), and Level II's barn would be a second A-frame glued behind, never growing out
//     of the first.
//  3. "The Snowbound Steading": the farmhouse turned to show the road its LONG side under a steep slate roof that is two
//     thirds of it (the snowy village's own house: low calcite walls the drifts lean on, a roof that sheds), a steep
//     birch-faced GABLET rising out of the slope over the door with a stone storm niche under it, a broad stone STACK
//     up the west gable past the ridge, and on the field side the roof run on two blocks past the east gable as a deep
//     GABLE PORCH over the Farmer's gallery, its gable closed in birch boards over a dark beam, so the composter and
//     the harvest chest stand in shelter out of the snow. Level II: a tall white BARN crosses behind at right angles
//     (its ridge along z, two higher than the house's), a T of steep gables that sheds snow every way: calcite below a
//     grey band, birch boards above, wagon doors and a hay door each under a slate snow hood in the back gable, a hoist
//     beam with its lantern, a louvre on the ridge, and its long east eave carrying the gallery on to the second
//     composter. Level III: a root and ice cellar under the hall. The field: the shipped 9 x 5 round its channel,
//     the channel boarded over with spruce hatches against the frost, the scarecrow wearing snow on his head.
//  Built: 3. It is the strongest and not the safest: from 20 blocks it is a big white-ridged slate roof on a pale stone
//  foot with a fat stack and a pointed gablet, and from the field side a deep birch-faced porch gable, nothing like the
//  shipped catslide (a gable to the road, one long sweep east) or the taiga look's long dark log range; Level II's
//  crossing barn makes a T that reads from every side (the barn's peak and louvre rise over the house from the road,
//  its tall birch gable and hoods face the back road). Risks: the turned roof meets the barn's gable at the passage
//  (the house's back eave runs into the barn's front wall), and the gallery under the porch is a deep open space.
//
// --- Coordinates and named parts (x across the front, west 0 -> east 14; y up, 0 = the layer on the clicked block;
//     z from the front, z = 0, north, to the back) ------------------------------------------------------------------
//   Hall        walls x 1..7, z 1..8 (the shipped cells): calcite y 1..3 on a stone-brick plinth, quoins at the four
//               corners, a polished-andesite course at y 4 all round (the windows' heads), the gables x 1 and x 7 up to the roof; tie beams y 5 at
//               z 4 and z 7 carry the shipped hanging lamps (chain 0, as shipped).
//   Roof        steep (2:1), ridge along x over z 4..5 (y 12, two stairs set full with snow on them), eaves at z 0 and
//               z 9 (y 4, snow-laden lips), one snow line across the back slope (y 8), rakes at x 0 (west) and x 9: it runs two blocks past the east gable.
//   Gablet      over the front door: a three-wide steep gable rising out of the slope (x 3..5, face z 1, y 5..8), white
//               birch posts and boards, a tall light over the door, its own slate verge and ridge (y 9) with snow;
//               lanterns hang from its eaves.
//   Storm niche stone-brick cheeks x 3 and x 5 on z 0 (y 1..3) under the eave, an andesite lintel, the step.
//   Stack       on the west gable: a breast of gathered stone x 0, z 3..6 up to y 8, a two-wide stone-brick stack
//               (z 4..5) through the ridge to y 14, a slab cap and its smoke; the hearth inside the wall at (1, 1, 5).
//   Gable porch x 8..9 under the roof's east run-on: the gallery deck x 8 (z 1..9; z 1..20 in II) with its step at
//               (8, 0, 0); its gable closed at x 9 in birch boards with two dark spruce studs over a spruce beam at y 5,
//               a boarded ceiling over the deck at y 5; lanterns hung at (8, 4, 1) and (8, 4, 8); the hall's three east windows look onto it.
//   Field       the shipped field (x 9..13, z 2..10) round its channel x 11 (boarded over with closed spruce trapdoors),
//               the scarecrow on its stone footing at (11, 1, 6) wearing snow; a gathered-stone kerb with a spruce
//               fence (x 9..14, z 1..11), lanterns on three corner posts.
//   Passage (II) the old back porch (flags x 1..7, z 9) closed by doors at x 1 and x 7 under the house's back eave.
//   Barn   (II) walls x 1..7, z 10..20: calcite y 1..4, a polished-andesite band at y 5 (the loft floor), birch boards
//               above (y 6..7 on the long sides), quoins; its steep roof ridge along z at x 4 (y 14), eaves x 0 and x 8 (y 7),
//               a snow line on each slope (y 11), the front rake flush
//               on the z 10 gable, the back rake one out at z 21 (an upside-down verge); a louvre on the ridge at z 15;
//               in the back gable the wagon doors under a slate hood, the hay door under its own hood, a hoist beam
//               with a lantern over it; west windows shuttered in spruce; the gallery runs on under the east eave.
//   Field II    the shipped second field (x 9..13, z 12..20) round its boarded channel, its kerb and fence to z 21.
//   Cellar (III) the shipped root cellar (x 1..7, z 1..8, 4 deep) in gathered stone: its three chests on the west wall,
//               the ladder under the hatch at (2, 0, 2), produce under shelves on the east wall and an ice store of
//               packed ice along the back (a snow country's cold cellar).
//   Snow        on every ridge (the hall's, the gablet's, the barn's), the eave lips, the sills, the niche's tops, the
//               woodpile and the scarecrow's head, and a drift skirt round every wall and kerb; never on a rake, a
//               step, a stair hood or a walk.
//
// --- Design notes: what is done, what is left ------------------------------------------------------------------------
//  Done (2026-11-21): all three levels saved as candidates (build.sh -> build/rebuild/farmstead_snowy/farmstead[_2|_3].nbt,
//    never live; no SHIP=1). Sizes 15 x 16 x 12, 15 x 18 x 22, 15 x 18 x 22 + the 4-deep cellar (shipped 15 x 13 x 12 and
//    15 x 15 x 22: the same plots, taller for the steep roofs and their snow; inside the brief's 18 x 28 x 21 / 26).
//  Checked: look_spots.py farmstead_snowy farmstead: SAME BUILDING (11 / 17 / 19 shipped spots, 0 missing, no bed, chest,
//    door or ladder moved; every lamp on its shipped chain); rebuild.py prints no CHECK line at any level; holes.py 0 at
//    every level (roof, meet, stray, peek, open); every block id vanilla 1.21.1. The brief's counts: composters 1 / 2 at
//    (8, 1, 4) and (8, 1, 13); beds 1 / 2 (the kept green and yellow cells); no barrel, furnace, crafting table or other
//    job block; field I farmland 36 in a 5 x 9 box on y 0 (x 9..13, z 2..10) with 8 water, II 74 farmland, all on y 0,
//    15 water; a carved pumpkin; chests 2 / 4 / 7 in the kept cells. II keeps 98 % of I; III above the ground is II
//    except the hatch at (2, 0, 2). About 47 / 72 / 76 stacks (my count, every item rounded up, snow by its layers;
//    the limit 81 is for I and II).
//  Renders: build/rebuild/farmstead_snowy/renders/ (snowy_<1|2|3>_<front|back|left|right|top>.png; cutaways snowy_1_cut3
//    and snowy_2_cut3 (the hall, the barn floor), snowy_1_cut8 (under the hall's roof), snowy_2_cut7 (the loft),
//    snowy_3_cut-1 (the cellar), each top and front).
//  Tried and changed (own render rounds): 1 the gallery under the porch was open up into the gable's space (holes.py
//    `open`, 24 cells): a board ceiling over the deck at the beam's height and the space above packed; 2 the hoist beam
//    moved up into the empty cell under the barn's back ridge (a `stray`), its lantern on a chain; 3 a snow line on both
//    hall slopes left loose white patches either side of the gablet on the front: now on the back slope only, and one
//    on each barn slope; 4 Level I read top-heavy (a 3-high wall under a 9-high roof): the hall's walls rose a course
//    (calcite y 1..3 under an andesite course at y 4 that heads the windows), the roof, gablet, porch and stack with
//    them, the barn's eave to y 7 so its ridge (14) still clears the hall's (12) with its louvre over it; 5 the
//    passage's ends were open over their doors once the back eave rose (holes.py `open`): andesite heads at y 3;
//    6 the drift is one layer (two cost 17 stacks of snow in II).
//  Open for the critic: the front slope is a big dark face (broken by the gablet and its snow, the eave lip and the
//    stack); the gablet's ridge snow sits two courses above its face and reads as a short bar; the east porch gable's
//    birch screen has only two studs; the field's channel is boarded (no water shows; it keeps the frost off, and the
//    Farmer's field still joins through the water under it).
//  Round 2 (2026-11-21, briefs/farmstead_looks_critic.md, Snowy BACK on Silhouette 3 and Detail 3):
//    1 the hall's walls rose another course with the windows: calcite y 1..4 under the andesite course at y 5, every
//      hall window three panes high (y 2..4), eaves at y 5, ridge y 13 (the barn's ridge 14 and its louvre still over
//      it); the slope is now about half the front, not two thirds; the niche's cheeks rise to y 4 under an andesite
//      lintel with its lantern hung under it, the gablet (face y 6..9), porch beam and ceiling (y 6) and stack (to y 15)
//      rose with them; I is 15 x 17 x 12.
//    2 the west gable: the breast stops at y 4 and steps in with a stone weathering (stairs at y 5 either side), the
//      two-wide stack rising alone above it; the west rake gets a closed barge, two courses of spruce boards under every
//      step of the rake from beside the stack down to the eave lips front and back (the east rake keeps its verge).
//    3 the snow line on the back slope runs rake to rake (x 0..9, y 9); the front has none; the gablet's snow is its
//      ridge itself (packed snow in the ridge's three cells) over a closed slate apex at the verge, not a layer a
//      course above it.
//    4 the drift is varied: three layers piled where two walls meet, two against a wall, one or two along the kerbs
//      with gaps, kept off the gallery step, the passage step and the wagon steps (drift() in recipe.mjs, in place of
//      snowSkirt).
//    Tried: upside-down slate stairs as the west barge (bargeTrim) read as a loose zigzag in the render; now boards.
//    Checked after round 2: look_spots SAME BUILDING (11/17/19, 0 missing, none moved); holes.py 0 at all three levels;
//    no CHECK line; composters 1/2, beds 1/2, field I 36 farmland 5 x 9 on y 0 with 8 water, II 74; II keeps 98 % of I;
//    III = II above the ground but the hatch; about 49 / 73 / 76 stacks. Renders overwritten in renders/ (same names).
//  For the ship step: the snow layers need the builder to carry snow; the sizes above differ from the shipped
//    Farmstead's declared ones; III's cellar box is 2,2,6,7 (out/cellar.txt).
import { FURNITURE as F, CROPS as C } from '../farmstead/plan.mjs'

export const SIZE = { 1: [15, 17, 12], 2: [15, 18, 22], 3: [15, 18, 22] } // w, h, d (plot: 18 x 28 x 21 for I, 26 deep for II)
export const BASEMENT = { 3: 4 }
export const IDS = { 1: 'farmstead', 2: 'farmstead_2', 3: 'farmstead_3' }

// PALETTE 60/30/10: 60 pale calcite walls; 30 dark slate (deepslate tiles) under white snow and the grey dressed stone
// (plinth, quoins, band, stack, kerbs); 10 white birch (the gablet, the porch and barn gables), dark spruce (doors,
// shutters, fences, beams) and the field's greens and the scarecrow's orange.
export const PALETTE = {
  wall: 'calcite', plinth: 'stone_bricks', quoin: 'stone_bricks', quoinAlt: 'polished_andesite',
  course: 'polished_andesite', lintel: 'polished_andesite', sill: 'stone_brick_slab', step: 'stone_brick_stairs',
  stack: 'stone_bricks', capSlab: 'stone_brick_slab',
  field: [['stone', 3], ['andesite', 2], ['cobblestone', 2]], // gathered stone: the breast, kerbs, flags, cellar
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  snow: 'snow',
  post: 'stripped_birch_log', face: 'birch_planks', barge: 'spruce_planks', beam: 'spruce_log', stud: 'spruce_log',
  floor: 'spruce_planks', deck: 'spruce_planks', barnFloor: 'packed_mud', wood: 'spruce', glass: 'glass_pane',
  firewood: 'spruce_log', ice: 'packed_ice', cellarFloor: 'stone_bricks',
}

export const HOUSE = {
  x0: 1, x1: 7, z0: 1, z1: 8, room: { x0: 2, x1: 6, z0: 2, z1: 7 }, wallTop: 4, band: 5, // round 2: a course higher
  roof: { axis: 'x', across: [0, 9], along: [0, 9], eaveY: 5 }, // ridge y 13 over z 4..5
  under: [5, 6, 8, 10, 12, 12, 10, 8, 6, 5], // the roof's lowest cell over each z (0..9)
  ridgeY: 13, ridgeZ: [4, 5], rakes: [0, 9], snowLine: { y: 9, zs: [7] }, // one snow line across the back slope, rake to rake (the gablet breaks the front's)
  backWood: [[1, 1, 9], [2, 1, 9], [1, 2, 9], [6, 1, 9], [7, 1, 9], [7, 2, 9]], // I only: firewood on the back porch (the passage takes it in II)
  tieBeams: [4, 7], beamY: 5,
}
export const GABLET = { cx: 4, face: 1, y0: 6 } // frontDormer: face y 6..9, ridge y 10 over z 0..2 (round 2: the ridge is snow)
export const NICHE = { cheeks: [3, 5], z: 0, top: 4, lintel: [4, 4, 0], lamp: [4, 3, 0], lamps: [[2, 7, 0], [6, 7, 0]] } // under the eave (y 5)
export const STACK = { x: 0, breast: { z0: 3, z1: 6, top: 4 }, weathering: 5, zs: [4, 5], top: 15, fire: [0, 16, 4], slab: [0, 16, 5] } // round 2: the breast steps in at y 5
export const WOODPILE = { x: 0, z0: 7, z1: 8, top: 2 }
export const PORCH = { // the gable porch over the gallery
  deckX: 8, z0: 1, z1: { 1: 9, 2: 20 }, step: [8, 0, 0],
  screenX: 9, beamY: 6, screen: { z0: 2, z1: 7 }, studs: [3, 6], soffitY: 6, // a boarded ceiling over the gallery (x 8) closes the porch's gable space
  lamps: [[8, 5, 1], [8, 5, 8]],
}
export const BARN = {
  x0: 1, x1: 7, z0: 10, z1: 20, room: { x0: 2, x1: 6, z0: 11, z1: 19 }, wallTop: 4, band: 5,
  roof: { axis: 'z', across: [0, 8], along: [10, 21], eaveY: 7, steep: true }, // ridge y 14 at x 4
  under: [7, 8, 10, 12, 14, 12, 10, 8, 7], // the roof's lowest cell over each x (0..8)
  tieBeams: [12, 14], loft: { y: 5, z0: 16, z1: 19 },
  chamber: { wallZ: 16, doorX: 3, wallX: 4, z0: 17, z1: 19 },
  stalls: { front: 5, z0: 11, z1: 15, gates: [12, 14], dividers: [13, 16], troughs: [11, 14], hay: [12, 15] },
  tools: { x: 2, z: [11, 12, 13] },
  louvre: { x: 4, z: 15, y: 14 }, snowLine: { y: 11, xs: [2, 6] },
  wagonHood: { x0: 4, x1: 7, y: 4, z: 21 }, hayHood: { x0: 3, x1: 5, y: 8, z: 21 },
  hoist: { beam: [4, 13, 21], chain: [[4, 12, 21], [4, 11, 21]], lamp: [4, 10, 21] }, // the beam under the ridge's overhang, a chain, its lantern
  eaveLamps: [[8, 6, 11], [8, 6, 18]],
  hay: [[6, 1, 18], [6, 2, 18], [3, 6, 18], [3, 6, 19], [5, 6, 17], [5, 6, 18], [5, 6, 19], [5, 7, 19], [6, 6, 18], [6, 6, 19], [3, 7, 19]],
}
export const FIELD = { x0: 9, x1: 13, z0: 2, z1: 10, channelX: 11, scarecrowZ: 6 }
export const FIELD2 = { x0: 9, x1: 13, z0: 12, z1: 20, channelX: 11, water: [13, 19] }
export const KERB = { 1: [[9, 1], [14, 1], [14, 11], [9, 11]], 2: [[14, 11], [14, 21], [9, 21]] }
export const FIELD_LAMPS = { 1: [[9, 1], [14, 1], [14, 11]], 2: [[14, 21], [9, 21]] }
export const CELLAR = { x0: 1, z0: 1, x1: 7, z1: 8, depth: 4, ladder: { x: 2, z: 2, facing: 'south' } }

export const OPENINGS = {
  frontDoor: { at: [4, 1, 1], facing: 'south', out: 'north', step: [4, 0, 0] },
  backDoor: { at: [4, 1, 8], facing: 'north', out: 'south' },
  passageDoors: [{ at: [1, 1, 9], facing: 'east', out: 'west' }, { at: [7, 1, 9], facing: 'west', out: 'east' }],
  passageStep: [0, 0, 9],
  barnDoor: { at: [4, 1, 10], facing: 'south', out: 'north' },
  barnSideDoor: { at: [7, 1, 17], facing: 'west', out: 'east' },
  wagonDoors: { at: [[5, 1, 20], [6, 1, 20]], facing: 'north', out: 'south', lintel: { x0: 4, x1: 7, y: 3 } },
  hayDoor: { at: [4, 6, 20], facing: 'north', out: 'south' },
  windows: [ // cells, the side they look out of, dressing ('sill': a stone ledge with snow; 'shutters': spruce)
    { name: 'hall front west', cells: [[2, 2, 1], [2, 3, 1], [2, 4, 1]], out: 'north', dress: 'sill' },
    { name: 'hall front east', cells: [[6, 2, 1], [6, 3, 1], [6, 4, 1]], out: 'north', dress: 'sill' },
    { name: 'hall west', cells: [[1, 2, 2], [1, 3, 2], [1, 4, 2]], out: 'west', dress: 'sill' },
    { name: 'hall east front', cells: [[7, 2, 3], [7, 3, 3], [7, 4, 3]], out: 'east', dress: null }, // onto the gallery
    { name: 'hall east', cells: [[7, 2, 5], [7, 3, 5], [7, 4, 5]], out: 'east', dress: null },
    { name: 'hall east back', cells: [[7, 2, 7], [7, 3, 7], [7, 4, 7]], out: 'east', dress: null },
    { name: 'hall back west', cells: [[2, 2, 8], [2, 3, 8], [2, 4, 8]], out: 'south', dress: null }, // the passage takes z 9 in II
    { name: 'hall back east', cells: [[6, 2, 8], [6, 3, 8], [6, 4, 8]], out: 'south', dress: null },
    { name: 'barn front gable', level: 2, cells: [[4, 9, 10], [4, 10, 10]], out: 'north', dress: null },
    { name: 'barn west', level: 2, cells: [[1, 2, 14], [1, 3, 14]], out: 'west', dress: 'shutters' },
    { name: 'barn west back', level: 2, cells: [[1, 2, 18], [1, 3, 18]], out: 'west', dress: 'shutters' },
    { name: 'barn east', level: 2, cells: [[7, 2, 12], [7, 3, 12]], out: 'east', dress: null },
    { name: 'barn east middle', level: 2, cells: [[7, 2, 15], [7, 3, 15]], out: 'east', dress: null },
    { name: 'barn east back', level: 2, cells: [[7, 2, 19], [7, 3, 19]], out: 'east', dress: null },
    { name: 'barn back', level: 2, cells: [[2, 2, 20], [2, 3, 20]], out: 'south', dress: 'shutters' },
  ],
}

// The shipped Farmstead's furniture, cell for cell (kinds, cells and facings; the hall's hearth, chests, composters and
// cellar chests are its fixtures), and its crops.
export const FURNITURE = F
export const CROPS = C

// Root and ice cellar (III): produce under spruce shelves on the east wall, packed ice along the back wall.
export const CELLAR_STORE = {
  shelves: [['pumpkin', 3], ['melon', 4], ['pumpkin', 5], ['melon', 6]], // (6, -3, z) under a slab at y -2
  ice: [[3, -3, 7], [4, -3, 7], [5, -3, 7], [6, -3, 7], [4, -2, 7], [5, -2, 7]],
  hay: [[3, -2, 7]],
}
