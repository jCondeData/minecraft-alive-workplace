// Artisan's House, the DESERT look (looks/desert/artisans_house[_2,_3], ROADMAP 49.10g, as 49.10a): the same building as
// the shipped Artisan's House (../artisans_house/plan.mjs: the workroom with its bench, worktop and kept barrel, the hall
// with its barrel, chest, table for four and kitchen corner, the flight up the east wall, the couple's bedroom over the
// street and the sitting room behind it, the kiln's fire mouth into the workroom and its stoke hole on the yard side,
// the fuel shed, Level II's journeyman's room and drying yard, Level III's clay cellar under its hatch, and every
// furniture spot in the same cell at every level), drawn again for a hot, dry village. It speaks the desert village's
// language (coursed sandstone below, mud brick above, a cut-sandstone course at the upper floor, flat roofs behind a
// mud parapet and a smooth coping, beam ends, round arches, dark-oak lattice, a windcatcher, palm-rib shades), and it is
// the village's POTTER's house.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The Kiln Court": a blank-walled house round a little court, the kiln in the court's middle, a palm in a corner.
//     The truest desert plan, but the shipped house pins its rooms to x 1..7 and its kiln to the east side, so a court
//     would be a yard behind a wall nobody sees from the street; from outside: a mud box.
//  2. "The Potter's Mound": the Fustat way, kilns dug into a mound of spoil and broken pots, the house built against it,
//     its roof a ramp up the mound. A true picture of a pottery quarter, but the rooms are pinned at y 1 and y 6: the
//     mound would be a heap against one wall, and a heap of sand reads as unfinished, not as a kiln.
//  3. "The Dome and the Shade": a two-storey flat-roofed block of coursed sandstone and mud brick, its upper storey
//     carried over a deep IWAN (a broad round-arched porch) on the street, a lattice oriel over the arch; beside it the
//     potter's KILN in the form hot dry countries fire pots in: a round updraft kiln, a drum of fired terracotta under a
//     stepped mud-brick DOME with the smoke hole at its crown (smoking), the fire mouth below; the house's flat ROOF the
//     drying floor for the unfired pots, under a palm-rib SHADE on posts, because in a hot wind greenware dried in the
//     sun cracks, so the potter dries it in the shade on the roof and fires it in the dome. A windcatcher cools the back
//     rooms; the fuel (palm fronds and brushwood, no logs in a desert) under a palm-rib lean-to behind the kiln.
//     Story: the potter of a caravan town, whose dome smokes beside her house and whose roof is lined with drying jars.
//  Built: 3. It is the strongest: from 20 blocks it is three shapes no other desert house has together (a block, a round
//  dome beside it with a thread of smoke, a shade on the roof over rows of pots), the dome is the drawn house's bottle
//  kiln told in the desert's own words (the same idea, the house's fire is the workshop's fire), and the iwan gives the
//  street front the deep shade and the layers the drawn jetty gave it. Not the safest: a dome that smokes, and a roof
//  nobody climbs to that is the whole workshop's other half.
//
// --- Design notes: what is done, what is left (2026-11-21) -----------------------------------------------------------
//  Done: Levels I-III as candidates (build.sh -> build/rebuild/artisans_house_desert/), never live (SHIP=1 saves them as
//  looks/desert/...). The shipped FURNITURE, the door, the flight, its well and rail, the kiln's fire mouth and stoke
//  hole (furnaces), the wing's doorway and the cellar's hatch imported from ../artisans_house/plan.mjs, so every cell is
//  the drawn house's; the cellar's chests, barrels and clay drawn as the drawn house draws them. The journeyman's lamp
//  hangs on chain 0 from the cell's flat ceiling (the drawn one on 5 from its gable). The drawn gable windows went with
//  the gables; the roof is reached by a ladder under a hatch in the sitting room's back corner (a flat drying roof with
//  no way up failed rebuild.py's reach check, and the potter has to get up there).
//  Checked: look_spots.py artisans_house_desert artisans_house: SAME BUILDING (29, 33, 34 spots, none missing, no bed,
//  container, job block or door moved; job blocks the same by type and count: I barrel 3 cauldron 2, II barrel 4
//  cauldron 3, III barrel 16 cauldron 3); rebuild.py no CHECK line at any level; holes.py 0 (with open) at every level;
//  check.py's owner rules 0; every id vanilla 1.21.1.
//  Tried and changed: the drum 6 high under the dome made the kiln outweigh the house from the street (now 4, the dome
//  y 6..8, the smoke at y 9); a 5x5 roof shade was a roofed room to holes.py's open check (now 3 deep, z 4..6) and at
//  z 3 it made a "gap between two roofs" with the front coping; a pocket between the house wall, the kiln's curve and
//  the fuel shade read as pits in the roof (the drum is bonded to the wall at x 8 and the fuel stacked into z 7);
//  stripped-log beam ends read as blocks (spruce fence vigas, the village's own); hay fuel by the stoke hole read as
//  yellow cubes (brushwood logs there now, the fronds only under the shade).
//  Round 2 (2026-11-21, the critic's notes, briefs/artisans_house_looks_critic.md, Desert BACK): 1 the oriel is a lattice
//        bay on the iwan's ledge: dark-oak fence corner posts and a fence screen between them (bars, light through, no
//        logs, no solid trapdoor), a slab cornice at y 9 with corbelled upside-down stair ends a block out each side;
//        2 the iwan is two deep (piers, lintel and arch at z 0 and z 1, a barrel vault over the door bay), its round head
//        red sandstone (smooth red sandstone stair haunches at y 3, shoulders at y 4, a chiseled red keystone) against
//        the pale stone, a smooth ledge over the front bay at y 5 with slab caps on the piers; the street pots moved
//        off the new piers; 3 the brushwood heaps by the stoke hole are gone (the fuel is all under the fuel shade);
//        4 the dome: a smooth sandstone slab shoulder ring over the band (y 6, r 3), one mud stair course (y 7, r 2), a
//        ring of mud-brick slabs round the crown (y 8), so its outline curves; 5 II's date palm drawn by hand: an 7-high
//        trunk, a tuft, four long fronds four out drooping two courses at their tips, four short diagonal ones, one leaf
//        thick; the yard's coarse-dirt checker is plain sand. Checked after round 2: look_spots SAME BUILDING (job blocks
//        equal), holes.py 0 (with open) at all three levels, no CHECK line, owner rules 0. Renders:
//        build/rebuild/artisans_house_desert/renders_r2/.
//  Open for the critic: the iwan's arch is in the same pale stone as the wall behind it, so from straight on it reads as
//  a shallow porch more than an arch; the stepped domes are ziggurat-like at this scale.
//
// Levels grow out of each other:
//   I    the house, the domed kiln, the fuel shade, the roof's drying shade (2 beds, the bedroom over the street).
//   II   + the journeyman's room: a small domed mud-brick cell on the back (x 2..6, z 13..17), the third bed; and the
//        drying yard behind the fuel shade under a second palm-rib shade, the fired pots on a bench, a date palm.
//   III  + the clay cellar under the house (4 deep), down a ladder under a hatch in the workroom floor.
//
// --- Named parts (template coordinates: x across the front, west 0 -> east 15; z from the front, z = 0, to the back) --
//   House       ground walls x 1..7, z 2..12 (coursed sandstone y 1..4, smooth quoins); the upper storey x 1..7, z 1..12
//               (a cut-sandstone course at y 5, the upper floor; mud brick y 6..8); the deck y 9, the mud parapet y 10,
//               the smooth coping y 11; stripped spruce beam ends a block out at y 8 on the sides and back.
//   Iwan        the street front's porch under the upper storey (x 1..7, z 1, y 1..4): cut-sandstone piers at x 1 and 7, a
//               broad round arch (haunches at x 2 and 6, y 3) under a cut lintel at y 4, the door and the two shop
//               windows in its shade, a lantern hung in the arch.
//   Oriel       a dark-oak lattice bay over the arch (x 3..5, z 0, y 6..9) before the bedroom's middle window, on corbels.
//   Kiln        the domed updraft kiln on the east side (centre x 11, z 4): a 7-round smooth platform (y 0), a terracotta
//               drum (y 1..4) with a bricked-up loading wicket on its back, a smooth sandstone band (y 5), a stepped
//               mud-brick dome (y 6..8), the smoke hole's fire at its crown (y 9); the fire mouth (a furnace) into the
//               workroom at x 7 and the stoke hole at x 14 under a mud-brick hood.
//   Shade       the fuel lean-to behind the kiln (x 8..10, z 8..12): palm posts, a palm beam and palm-rib matting off the
//               house wall at y 4; palm fronds (hay) and brushwood (stripped jungle logs) stacked under it to the roof.
//   Roof        the drying floor: a palm-rib shade (x 2..6, z 4..6, matting at y 12 on four palm posts and two beams)
//               over rows of drying jars and pots, a windcatcher (x 1..3, z 10..12, y 10..14) flush with the back west
//               corner; reached by a ladder from the sitting room's back corner (x 5, z 11) under a hatch in the deck.
//   Cell (II)   the journeyman's room, mud brick on a sandstone plinth, flat-roofed at y 5 behind a coped parapet, a little
//               stepped dome over it (the kiln's dome again, small).
//   Yard (II)   the drying yard behind the shade (x 9..14, z 13..17): a second palm-rib shade over the fired pots on a
//               mud-brick bench (the kept barrel), the quench jar (the cauldron), a date palm in the corner.
//   Cellar (III) the clay cellar under the house (x 1..7, z 2..12, floor y -4), sandstone-walled.
import {
  FURNITURE as A_FURNITURE, OPENINGS as A_OPENINGS, OUTSIDE as A_OUTSIDE, MASSES as A_MASSES,
} from '../artisans_house/plan.mjs'

export const SIZE = { 1: [16, 17, 14], 2: [16, 17, 19], 3: [16, 17, 19] } // as shipped
export const BASEMENT = 4

// Palette 60/30/10: coursed sandstone ground storey and mud brick above (60) / smooth and cut sandstone dressings: the
// plinth, quoins, the upper floor's course, the iwan, the coping, the kiln's band (30) / the kiln's warm terracotta drum,
// dark-oak lattice, palm wood and matting, the shipped spruce doors and furniture (10). Why: the terracotta drum is the
// one warm, fired-clay mass beside a pale house, the potter's material made into the potter's kiln, and its mud dome
// repeats the house's upper storey so the two belong together.
export const PALETTE = {
  ashlar: [['sandstone', 6], ['cut_sandstone', 3], ['smooth_sandstone', 1]],
  mud: [['mud_bricks', 7], ['packed_mud', 2]],
  plinth: 'smooth_sandstone', quoin: 'smooth_sandstone', course: 'cut_sandstone', carved: 'chiseled_sandstone',
  pier: 'cut_sandstone', lintel: 'cut_sandstone', arch: 'smooth_sandstone_stairs', archRed: 'smooth_red_sandstone_stairs',
  keystone: 'chiseled_red_sandstone', ledge: 'smooth_sandstone', corbel: 'smooth_sandstone_stairs',
  deck: 'smooth_sandstone', parapet: 'mud_bricks', coping: 'smooth_sandstone_slab', brow: 'cut_sandstone',
  paving: 'smooth_sandstone', sill: 'smooth_sandstone_slab', step: 'sandstone_stairs',
  floor: 'smooth_sandstone', hallFloor: 'packed_mud', upperFloor: 'spruce_planks', under: 'sandstone',
  viga: 'spruce_fence', wood: 'spruce', glass: 'glass_pane',
  lattice: 'dark_oak_trapdoor', latticePost: 'dark_oak_log', latticeRoof: 'dark_oak_slab', latticeWood: 'dark_oak',
  latticeCorbel: 'dark_oak_stairs',
  kiln: { platform: 'smooth_sandstone', drum: [['terracotta', 6], ['orange_terracotta', 1]], band: 'smooth_sandstone',
    dome: 'mud_bricks', domeStairs: 'mud_brick_stairs', shoulder: 'smooth_sandstone_slab', crown: 'mud_brick_slab', wicket: 'packed_mud', hood: 'mud_brick_stairs' },
  palm: { post: 'jungle_log', beam: 'stripped_jungle_log', mat: 'bamboo_mosaic_slab' },
  catcher: { body: [['mud_bricks', 5], ['packed_mud', 1]], corner: 'smooth_sandstone', cap: 'smooth_sandstone', capTop: 'smooth_sandstone_slab' },
  cellarWall: [['sandstone', 5], ['smooth_sandstone', 2], ['cut_sandstone', 1]], cellarFloor: 'smooth_sandstone',
}

export const HOUSE = {
  walls: { x0: 1, x1: 7, z0: 2, z1: 12 }, upper: { x0: 1, x1: 7, z0: 1, z1: 12 },
  groundTop: 4, floorY: 5, upperTop: 8, deckY: 9, parapetY: 10,
  vigas: { y: 8, back: [2, 4, 6], west: [3, 5, 7, 9, 11], east: [8, 10] }, // the east side's front half is the kiln's
}

export const IWAN = { zs: [0, 1], piers: [1, 7], a0: 2, a1: 6, headY: 3, lintelY: 4, lamp: [4, 3, 1] }
export const ORIEL = { x0: 3, x1: 5, z: 0, y0: 6, y1: 9 } // on the iwan's ledge (y 5): lattice y 6..8, cornice y 9

export const KILN = {
  cx: 11, cz: 4, r: 3, drumTop: 4, bandY: 5, dome: [6, 7, 8], fireY: 9,
  wicket: { x: 11, z: 7, cells: [1, 2], lintelY: 3 },
}

// the drying floor on the roof: four palm posts, two beams along x, matting over them; the pots drying under it
export const ROOF_SHADE = { x0: 2, x1: 6, z0: 4, z1: 6, y: 12, posts: [[2, 4], [6, 4], [2, 6], [6, 6]] }
export const DRYING = [ // greenware on the deck (y 10): jars (decorated pots) and small pots (flower pots)
  [3, 'decorated_pot', 5], [4, 'decorated_pot', 5], [5, 'decorated_pot', 5],
  [3, 'flower_pot', 4], [4, 'flower_pot', 4], [5, 'flower_pot', 4], [3, 'flower_pot', 6], [5, 'flower_pot', 6],
  [2, 'decorated_pot', 8], [3, 'decorated_pot', 8], [4, 'flower_pot', 8], [2, 'flower_pot', 9], [6, 'flower_pot', 8],
  [6, 'decorated_pot', 9], [2, 'flower_pot', 2], [3, 'decorated_pot', 2], [6, 'decorated_pot', 2],
]
export const CATCHER = { x0: 1, z0: 10, y0: 10, y1: 14 } // flush with the back west corner
// the potter's way up to her drying floor: a ladder in the sitting room's back corner under a hatch in the deck
export const ROOF_LADDER = { x: 5, z: 11, y0: 6, facing: 'north' }

export const SHADE = { x0: 8, x1: 10, z0: 8, z1: 12, y: 4, posts: [[10, 9], [10, 12]] }

export const WING = { x0: 2, x1: 6, z0: 13, z1: 17, deckY: 5 }
export const YARD = {
  ground: { x0: 8, x1: 15, z0: 13, z1: 18 },
  shade: { x0: 9, x1: 13, z0: 14, z1: 16, y: 4, posts: [[9, 14], [13, 14], [9, 16], [13, 16]] },
  bench: [[10, 1, 15]], // mud brick beside the kept barrel (11, 1, 15); the quench jar (the kept cauldron) at (12, 1, 15)
  pots: [[10, 2, 15, 'decorated_pot'], [11, 2, 15, 'decorated_pot'], [10, 1, 14, 'decorated_pot'], [11, 1, 14, 'flower_pot'],
    [12, 1, 14, 'decorated_pot'], [10, 1, 16, 'flower_pot'], [11, 1, 16, 'decorated_pot']],
  palm: [14, 0, 17],
}

// The shipped openings that hold the building together, cell for cell: the front door, the flight and its well and
// rail, the kiln's mouth and stoke hole, the wing's doorway, the cellar's hatch. The windows are this look's own (the
// shipped cells kept where a wall still stands there, the gable windows gone with the gables).
export const OPENINGS = {
  frontDoor: A_OPENINGS.frontDoor, stairs: A_OPENINGS.stairs, rail: A_OPENINGS.rail,
  kilnMouth: A_OPENINGS.kilnMouth, stokeHole: A_OPENINGS.stokeHole,
  wingDoorway: A_OPENINGS.wingDoorway, hatch: A_OPENINGS.hatch,
  windows: [
    { name: 'shop west', cells: [[2, 2, 2], [2, 3, 2]], out: 'north', dress: 'sill', pots: ['potted_cactus'] },
    { name: 'shop east', cells: [[6, 2, 2], [6, 3, 2]], out: 'north', dress: 'sill', pots: ['potted_dead_bush'] },
    { name: 'workroom west', cells: [[1, 2, 3], [1, 3, 3]], out: 'west' },
    { name: 'kitchen west', cells: [[1, 2, 10], [1, 3, 10]], out: 'west' },
    { name: 'hall back', cells: [[4, 2, 12], [4, 3, 12]], out: 'south', level1Only: true },
    { name: 'bedroom front west', cells: [[2, 7, 1], [2, 8, 1]], out: 'north', dress: 'sill', pots: ['potted_cactus'] },
    { name: 'bedroom front middle', cells: [[4, 7, 1], [4, 8, 1]], out: 'north', brow: false }, // behind the oriel
    { name: 'bedroom front east', cells: [[6, 7, 1], [6, 8, 1]], out: 'north', dress: 'sill', pots: ['potted_dead_bush'] },
    { name: 'bedroom west', cells: [[1, 7, 3], [1, 8, 3]], out: 'west' },
    { name: 'bedroom east', cells: [[7, 7, 3], [7, 8, 3]], out: 'east' }, // over the kiln's dome
    { name: 'sitting west', cells: [[1, 7, 10], [1, 8, 10]], out: 'west' },
    { name: 'sitting east', cells: [[7, 7, 10], [7, 8, 10]], out: 'east' },
    { name: 'sitting back west', cells: [[2, 7, 12], [2, 8, 12]], out: 'south' },
    { name: 'sitting back east', cells: [[4, 7, 12], [4, 8, 12]], out: 'south' },
  ],
  wingWindows: [
    { name: 'wing west', cells: [[2, 2, 14], [2, 3, 14]], out: 'west' },
    { name: 'wing east', cells: [[6, 2, 15], [6, 3, 15]], out: 'east' },
    { name: 'wing back', cells: [[4, 2, 17], [4, 3, 17]], out: 'south', dress: 'sill', pots: ['potted_cactus'] },
  ],
}

// Every furniture piece: the shipped house's, the same parts in the same cells. The lamps that hung from the drawn
// roof's tie beams hang straight from the flat ceiling here (chain 0), as does the journeyman's (from its flat ceiling
// at y 5, where the drawn one hung five links from its gable).
export const FURNITURE = {
  1: A_FURNITURE[1],
  2: A_FURNITURE[2].map(f => (f.part === 'hangingLamp' ? { ...f, chain: 0 } : f)),
  3: A_FURNITURE[3],
}
export const OUTSIDE = { wares: A_OUTSIDE.wares, shed: A_OUTSIDE.shed, rack: A_OUTSIDE.rack }
export const CELLAR = A_MASSES.cellar

// The walking line: the street -> the step -> the iwan -> the door -> the workroom -> up the east-wall flight to the
// sitting room -> the bedroom; (II) down the hall to the journeyman's cell; (III) down the hatch ladder to the cellar.
export const WALK = [
  [4, 0, -1], [4, 0, 0], [4, 1, 1], [4, 1, 2], [4, 1, 4], [6, 1, 5], [6, 1, 6], [6, 5, 10], [5, 6, 10], [4, 6, 4],
  [5, 1, 10], [4, 1, 12], [4, 1, 14],
  [2, 1, 5], [2, -3, 5], [3, -3, 10],
]
