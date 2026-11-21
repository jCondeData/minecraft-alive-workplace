// Artisan's House, the SAVANNA look (looks/savanna/artisans_house[_2, _3], ROADMAP 49.10g, as 49.10a): the same
// building as the shipped Artisan's House (../artisans_house/plan.mjs: the workroom with the kiln's fire mouth, the hall
// and kitchen, the stair up the east wall, the couple's bedroom over the street and the sitting room behind it, II's
// journeyman's wing and drying yard, III's 4-deep clay cellar under the hatch; every bed, door, kept container, furnace,
// ladder and furniture spot in the same cell, and the same job blocks by type and count), drawn again for a hot
// grassland with a rainy season. It speaks the savanna village's language (golden thatch hips with eaves on every side
// and no gable anywhere, grey acacia posts and beams, packed mud over a red granite storey on a terracotta plinth,
// painted trims, shade at the front), and it is the village's POTTER's house.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The bottle kiln in mud": the shipped massing, the brick bottle redrawn in mud brick with a thatch cap. Massing:
//     the house and a round shouldered kiln. Roof: a hip, the kiln's lip. Plan: as shipped. Story: the potter of any
//     village. A palette swap of the shipped bottle; the savanna has no bottle kilns.
//  2. "The firing ground": the house under one low thatch, no kiln at all, the pots fired in an open pit in the yard
//     under a shade roof, as most savanna potters truly fire. Massing: one long low hip and a pavilion. Story: honest,
//     but the shipped furnaces (the fire mouth in the workroom wall, the stoke hole outside) must stand in their cells,
//     and with nothing behind them they are two furnaces on a lawn; and nothing in the silhouette says "potter".
//  3. "The Mound Kiln": the potter digs her clay from the termite mounds of the grassland, and her kiln is built of
//     that clay in the mound's own shape: a red-earth spire, broad and stepped at the foot, fluted with little turrets,
//     its single flue rising three blocks over the house's ridge with smoke at its tip. Beside it the house: one golden
//     thatch hip, eaves out on every side, over a red granite ground storey and a packed mud upper storey carried a block
//     out over the street on grey acacia posts (the shipped jetty, become the shade of a front stoep where her wares
//     stand), a painted band of red and white over the grey sill beam. A thatched fuel shelter leans on the house behind
//     the kiln; II adds the journeyman's wing under its own half-hip and a drying bench and an open firing pit for the
//     big pots; III the clay cellar. Story: her kiln is the land's own shape, made of the land.
//  Built: 3, the strongest, not the safest. From 30 blocks it is a golden hip with a red spire beside it, a silhouette no
//  other house in the village has and one that names the climate (the termite mound is the savanna's own tower); it
//  keeps the shipped story (the house's fire is the workshop's fire) in a form true to the place. Its risks: a stepped
//  solid mound must read as built, not as terrain, so it is laid in fired-earth colours with a soot-dark foot, the fire
//  mouth bonded to the house by a mud breast, and the flue smokes.
//
// --- Design notes: what is done, what is left (2026-11-21) -----------------------------------------------------------
//  Done: Levels I-III as candidates (build.sh -> build/rebuild/artisans_house_savanna/), never live (SHIP=1 saves them
//  as looks/savanna/artisans_house[_2, _3]). The shipped furniture list drawn as is (../artisans_house/plan.mjs), the
//  three spruce doors' cells, the hatch ladder, both kiln furnaces, the stove, every kept barrel and chest in its cell;
//  job blocks as shipped (I: 3 barrels, 2 cauldrons; II: 4 and 3; III: 16 and 3). The new part: ../parts/mound_kiln.mjs.
//  Checked: look_spots.py artisans_house_savanna artisans_house: SAME BUILDING (29, 33, 34 shipped spots, none missing,
//  nothing moved, job blocks equal; only the journeyman's lamp hangs on 3 links, not 5: the half-hip is lower);
//  rebuild.py no CHECK line; holes.py 0 (with open) at every level; the owner's rules (check.owner_problems) 0.
//  Renders: build/rebuild/artisans_house_savanna/renders/ (artisans_house[_2, _3]_<view>.png; cutaways cut-2-3_top,
//  cut-2-7_top, cut-3-1_top: II at y 3 and y 7, III's cellar).
//  Tried and changed: the fuel shelter first leaned on the house's east wall under the great eave (rebuild's seal_slots
//  thickened the wall there and copied a window out into the slot): it stands clear of the eave now, a lane of stacked
//  logs between; at 5 wide its back was a room to holes.py's open check (now 3 wide, its posts at the outer corners, logs
//  stacked in the kiln's lee); white jambs between the front windows washed the street face out (plain mud now).
//  Open for the critic: the mound is solid (a kiln's chamber is story, its furnaces are its mouths); the stoep's posts
//  are the shipped jetty's two corners, so the door bay is 5 wide without a middle post; the firing pit's campfire is
//  unlit (last night's firing).
//
//  Round 2 (2026-11-21, the critic's notes, BACK narrow 4/4/4/3): the fuel stacks turned to axis z (bark to the yard),
//  one high at the outer end, the thatch lid only over the inner two (they read as crates); the mound plain terracotta,
//  red only in the lip courses y 3 and y 6, orange only at the neck and spire (y 7..12), the brown foot kept (the
//  cell-by-cell mix read as noise); the turrets now little 2-high cones (a block and a mud-brick wall tip) standing on
//  the steps at y 3 and y 6 (the single cubes read as left on the mound). Checks rerun: as above, all clean.
//
// Levels grow out of each other:
//   I    the house, the mound kiln and the fuel shelter (2 beds, the front bedroom upstairs).
//   II   + the journeyman's wing behind (its half-hip runs into the house's back wall under the great eave), the drying
//        bench of fired pots with the quench tub, and an open firing pit for the big water jars.
//   III  + the clay cellar under the house (4 deep), down the ladder under the hatch in the workroom floor.
//
// --- Named parts (template coordinates: x across the front, west 0 -> east 15; z from the front, z = 0, to the back) --
//   House    ground walls x 1..7, z 2..12: red granite y 1..4 on a terracotta plinth, acacia corner posts. The upper
//            storey z 1..12: a grey acacia sill beam ring y 5 carried over the street on two acacia posts (the stoep), a
//            painted band y 6 (red and white), packed mud y 7..8, the acacia top beam ring y 9 with two tie beams.
//   Hip      the great thatch x 0..8, z 0..13, eaves at y 9, ridge y 13 along z (z 4..9).
//   Stoep    the shaded strip under the upper storey (z 1): terracotta floor, the wares barrel, her pots for sale.
//   Kiln     the mound kiln on the east side, centred (11, 4): a soot-dark foot, red-earth courses r 3 to y 3, r 2 to y 6,
//            the square neck to y 9, a round spire to y 12, the flue to y 14, smoke at y 15; plain terracotta banded red at
//            the steps' lips (y 3, y 6), orange at the neck; four little cones standing on its steps.
//            The fire mouth (7, 1, 4) opens into the workroom through a mud breast; the stoke hole (14, 1, 4) faces the
//            yard under a mud hood, acacia fuel stacked either side under thatch lids.
//   Shelter  the fuel shelter behind the kiln (x 9..11, z 8..13): two acacia posts, a thatch hip beside the house's
//            east wall; acacia logs stacked against the wall, clay, the slip cauldron.
//   Wing     (II) the journeyman's room, packed mud on a terracotta plinth, acacia corners, its half-hip (eaves y 5,
//            ridge y 8) running into the house's back wall.
//   Yard     (II) the drying bench (x 10..12, z 15) with the quench tub, the open firing pit (13..15, 15..17).
//   Cellar   (III) the clay cellar under the house (x 1..7, z 2..12, floor y -4), mud and granite.
import {
  SIZE as A_SIZE, BASEMENT as A_BASEMENT, FURNITURE as A_FURNITURE, OPENINGS as A_OPENINGS,
} from '../artisans_house/plan.mjs'

export const SIZE = A_SIZE // I 16x17x14, II and III 16x17x19 (as shipped)
export const BASEMENT = A_BASEMENT // III's cellar, 4 deep

// Palette 60/30/10: red granite and packed mud walls (60) / golden thatch, grey acacia posts and beams, the terracotta
// plinth and stoep (30) / the red-earth kiln, the painted band, acacia shutters, the shipped spruce doors and furniture (10).
export const PALETTE = {
  granite: [['granite', 6], ['polished_granite', 1], ['terracotta', 1]],
  mud: [['packed_mud', 6], ['mud_bricks', 1]],
  plinth: [['terracotta', 5], ['red_terracotta', 2], ['brown_terracotta', 1]],
  frame: 'acacia_log',
  roof: { full: 'bamboo_mosaic', stairs: 'bamboo_mosaic_stairs', slab: 'bamboo_mosaic_slab' },
  paint: ['red_terracotta', 'white_terracotta'], jamb: 'white_terracotta',
  floor: 'spruce_planks', workFloor: 'mud_bricks', stoep: 'terracotta',
  wood: 'spruce', shutter: 'acacia', glass: 'glass_pane', fence: 'acacia',
  kiln: { foot: 'brown_terracotta', body: 'terracotta', lip: 'red_terracotta', neck: 'orange_terracotta', cap: 'mud_brick_wall', hood: 'mud_brick_stairs' },
  pot: 'decorated_pot',
  cellarWall: [['packed_mud', 4], ['granite', 2], ['mud_bricks', 1]], cellarFloor: 'packed_mud',
}

export const HOUSE = {
  walls: { x0: 1, x1: 7, z0: 2, z1: 12 }, // the ground storey
  upper: { x0: 1, x1: 7, z0: 1, z1: 12 }, // the upper storey, a block out over the stoep
  groundTop: 4, sillY: 5, paintY: 6, upperTop: 8, beamY: 9, ties: [4, 9],
  stoepPosts: [1, 7], // acacia posts under the upper storey's front corners (z 1)
  jambs: [], // (x, y) cells painted white beside the front windows: none (white jambs washed the front out; plain mud reads better)
}
export const HIP = { x0: 0, z0: 0, x1: 8, z1: 13, eaveY: 9 }

export const KILN = {
  cx: 11, cz: 4,
  courses: [{ r: 3, top: 3 }, { r: 2, top: 6 }, { r: 1, top: 9 }, { r: 1, top: 12, plus: true }, { r: 0, top: 14 }],
  lips: [3, 6], neckYs: [7, 12], // red courses at the steps' lips, orange from the neck to the spire's top course
  turrets: [[9, 2, 4], [13, 6, 4], [11, 2, 7], [13, 4, 7]], // little cones (a block and a mud-brick wall tip) standing on the steps at y 3 and y 6
  breast: { x: 8, z0: 3, z1: 5, y1: 8 }, // the mud breast bonding the fire mouth's side to the house wall
  hood: [15, 2, 4], // over the stoke hole
  fuel: [{ z: 1, x0: 13 }, { z: 2, x0: 14 }, { z: 6, x0: 14 }, { z: 7, x0: 13 }], // acacia stacks to x 15, bark to the yard (axis z), two high and one at the outer end, thatch lids over the inner two
}

export const SHELTER = {
  roof: { x0: 9, z0: 8, x1: 11, z1: 13, eaveY: 4 }, posts: [[11, 8], [11, 13]], // at its outer corners: the yard side open // clear of the house's eave (x 8): a lane of logs under it
  logs: { x: 8, z0: 7, z1: 12, high: 3 }, nook: [[9, 7], [9, 8]], clay: [[9, 1, 10], [9, 1, 11], [9, 2, 11]], cauldron: [10, 1, 8],
}

export const WING = {
  walls: { x0: 2, x1: 6, z0: 13, z1: 17 },
  roof: { x0: 1, z0: 9, x1: 7, z1: 18, eaveY: 5 }, from: 13, // only z >= 13: the half-hip runs into the house's back wall
  posts: [[2, 17], [6, 17]],
}

// The shipped openings that hold the building together, cell for cell; the windows are this look's own.
export const OPENINGS = {
  frontDoor: A_OPENINGS.frontDoor, doorLamp: A_OPENINGS.doorLamp, stairs: A_OPENINGS.stairs, rail: A_OPENINGS.rail,
  kilnMouth: A_OPENINGS.kilnMouth, stokeHole: A_OPENINGS.stokeHole, wingDoorway: A_OPENINGS.wingDoorway, hatch: A_OPENINGS.hatch,
  windows: [ // [cells], out, dress
    { name: 'shop west', cells: [[2, 2, 2], [2, 3, 2]], out: 'north', dress: null }, // behind the stoep
    { name: 'shop east', cells: [[6, 2, 2], [6, 3, 2]], out: 'north', dress: null },
    { name: 'workroom west', cells: [[1, 2, 3], [1, 3, 3]], out: 'west', dress: 'shutters' },
    { name: 'kitchen west', cells: [[1, 2, 10], [1, 3, 10]], out: 'west', dress: 'shutters' },
    { name: 'hall back', cells: [[4, 2, 12], [4, 3, 12]], out: 'south', dress: 'shutters', level1Only: true },
    { name: 'bedroom front west', cells: [[2, 7, 1], [2, 8, 1]], out: 'north', dress: null },
    { name: 'bedroom front middle', cells: [[4, 7, 1], [4, 8, 1]], out: 'north', dress: null },
    { name: 'bedroom front east', cells: [[6, 7, 1], [6, 8, 1]], out: 'north', dress: null },
    { name: 'bedroom west', cells: [[1, 7, 3], [1, 8, 3]], out: 'west', dress: 'shutters' },
    { name: 'bedroom east', cells: [[7, 7, 3], [7, 8, 3]], out: 'east', dress: null }, // looks onto the kiln's spire
    { name: 'sitting west', cells: [[1, 7, 10], [1, 8, 10]], out: 'west', dress: 'shutters' },
    { name: 'sitting east', cells: [[7, 7, 10], [7, 8, 10]], out: 'east', dress: 'shutters' },
    { name: 'sitting back west', cells: [[2, 7, 12], [2, 8, 12]], out: 'south', dress: null },
    { name: 'sitting back east', cells: [[6, 7, 12], [6, 8, 12]], out: 'south', dress: null },
  ],
  wingWindows: [
    { name: 'wing west', cells: [[2, 2, 14], [2, 3, 14], [2, 2, 15], [2, 3, 15]], out: 'west', dress: 'shutters' },
    { name: 'wing east', cells: [[6, 2, 14], [6, 3, 14], [6, 2, 15], [6, 3, 15]], out: 'east', dress: 'shutters' },
    { name: 'wing back', cells: [[4, 2, 17], [4, 3, 17]], out: 'south', dress: 'shutters' },
  ],
}

// Every furniture piece: the shipped house's, the same parts in the same cells; only the journeyman's lamp hangs on a
// shorter chain (the wing's half-hip has its ridge at y 8, the shipped steep gable a higher one).
export const FURNITURE = {
  1: A_FURNITURE[1],
  2: A_FURNITURE[2].map(f => (f.part === 'hangingLamp' ? { ...f, chain: 3 } : f)),
  3: A_FURNITURE[3],
}

// Furnishings outside the rooms (not furniture spots).
export const OUTSIDE = {
  stoep: { x0: 1, x1: 7, z: 1 },
  wares: [5, 1, 1], // the kept barrel of wares beside the door
  pots: [[2, 1, 1], [3, 1, 1], [6, 1, 1]], // her pots for sale on the stoep
  forecourt: { x0: 0, x1: 8, z: 0 }, // swept earth before the stoep
  bench: { x0: 10, x1: 12, z: 15 }, // II: the drying bench (fence, the kept barrel, the quench tub), pots on its top
  pit: { cx: 14, cz: 16 }, // II: the open firing pit for the big jars
}
