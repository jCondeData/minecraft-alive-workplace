// Farmstead, the DESERT look (looks/desert/farmstead[_2, _3], ROADMAP 49.10e, as 49.10a): the same building as the
// shipped as-drawn Farmstead (../farmstead/plan.mjs: the hall with its bed, table, hearth and harvest chest, the gallery
// on the field with both composters and their chests, II's cross passage and barn with the second Farmer's chamber, the
// stalls and the loft on its ladder, III's root cellar under the hatch; every bed, chest, door, ladder and furniture spot
// in the same cell and facing, the fields on their pins), drawn again for a hot, dry village. It speaks the desert
// village's language the Cottage, Stone House, Weaver's Cottage, Daub House and Manor looks set (mud brick over a
// sandstone foot, smooth-sandstone dressings, flat roofs behind a coped parapet, spruce beam ends, dark-oak lattice for
// shade), and it is the village's FARM: an oasis farm of the Nile and Siwa kind, with its dovecote and its well sweep.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The walled farm court": house, passage and barn as the west side of a mud-walled court that takes in the field,
//     one gate to the street. The truest plan for a desert farm, but the field must be open to the gallery and its
//     corners are pinned at the plot's edge: the court wall would only be the field's kerb raised, and from the street
//     the farm would read as a wall.
//  2. "The flat-roofed range": the shipped range as one mud block under one flat roof, house, passage and barn at one
//     height, a palm-rib shade along the field. Safe and calm, but one roof line over 20 blocks is a box from 20
//     blocks, and nothing says "farm" before the field is seen.
//  3. "The Dovecote Farm": the hall a mud-brick block under a flat roof behind a coped parapet (corner horns at
//     first) and a stepped crown over a carved door, its back crowned by a DOVECOTE (a pigeon tower: perches out on every
//     face, clay pots set in its faces for nests, a whitewashed band and dome cap), the field side under a PALM-RIB
//     SHADE on palm-trunk posts where the composters and harvest chests stand, a SHADUF (well sweep) over the channel at
//     the field's end, a date palm at the field's corner, latticed windows over mud benches (mastabas) along the street
//     front. Level II links a low roofed passage to a GRANARY BARN two storeys high (sandstone below, mud above, a
//     loft floor through it, beam ends at its upper ceiling) with two DOMED GRAIN BINS on its roof, the hay door and its
//     hoist at the back; the shade runs on along the second field. III digs a cool store under the hall.
//     Story: an oasis farmer keeps pigeons for the dung that feeds his field, lifts its water with a sweep, and as the
//     farm prospers builds a granary whose domed bins stand on its roof like loaves.
//  Built: 3. It is the strongest: from 20 blocks it is a stepped skyline you can name (the low shade, the passage, the
//  house's parapet, the granary and its domes, the dovecote over all of them), the dovecote and the shaduf say "farm"
//  before a crop is seen and no other desert look has either, and the levels grow out of each other (II's passage and
//  granary step up behind the house, the shade carries on along the new field). Not the safest: a tower on a one-room
//  house, and no pitched roof anywhere.
//
// --- Design notes: what is done, what is left (2026-11-21) -----------------------------------------------------------
//  Done: Levels I-III as candidates (build.sh -> build/rebuild/farmstead_desert/), never live. The shipped plan's rooms,
//  doors, ladder, hatch, beds, chests, composters, fields, crops and every furniture spot drawn in their cells (the
//  furniture list is the shipped one; the five hung lamps hang straight from the flat ceilings, chain 0, as shipped).
//  Checked: look_spots.py farmstead_desert farmstead: SAME BUILDING (11, 17 and 19 shipped spots, none missing; no bed,
//  container, job block, door or ladder moved); rebuild.py no CHECK line at any level; holes.py 0 (open included) at
//  every level; every id vanilla 1.21.1; composters 1 / 2 / 2, beds 1 / 2 / 2, chests 2 / 4 / 7, farmland 36 (9 x 5) /
//  74, one carved pumpkin, no barrel, furnace, crafting table or other job block; sizes as shipped (15 x 13 x 12,
//  15 x 15 x 22, III the same over 4); II keeps 99 % of I; about 46 / 66 / 68 stacks (limit 81); I places 710 blocks.
//  Renders: build/rebuild/farmstead_desert/renders/ (desert_<1|2|3>_<front|back|left|right|top>.png; cutaways
//  desert_1_cut3_*, desert_2_cut3_* (hall, barn floor), desert_2_cut7_* (the loft), desert_3_cut-1_* (the cool store)).
//  Tried and changed: whitewashed corner horns on every parapet and merlons on the dovecote read as a castle's crenels
//  (the copings are level now, the only step the crown over the door, the dovecote capped with a little dome); the
//  dovecote flush with the back parapet and the grain bins in the roof's middle left gaps holes.py reads as roofs
//  failing to meet (the dovecote a block in, two spurs tie it to the side parapets, the bins against the parapets);
//  perches over an empty deck cell read as stray gaps (moved up to the upper pots, a battered foot of stairs round the
//  tower); viga ends beside the flue's breast made a wedged gap (left out there); the granary's back was one flat panel
//  (a drip moulding at the loft floor now, turned round the west side); a long-frond palm read as vines (shorter fronds).
//  Round 2 (the critic's notes, briefs/farmstead_looks_critic.md, round 1 BACK narrow 4/4/4/3, Detail): 1 the shaduf
//        is a lever, not masonry: thin jungle-fence uprights (10, 11) and (12, 11) y 1..3, a thin fence bar at y 4, a
//        spruce-fence sweep tilted over it from its low front end (11, 4, 8), a chain and a clay bucket (a decorated pot)
//        under that end over the channel, the counterweight a mud-brick wall lashed on the short end (I over the bar,
//        II out over the second field at (11, 6, 13)); the scarecrow moved a block forward to z 5, clear of the bucket;
//        2 the grain bins stand as bins: a round drum (mud brick, mud-brick wall corners) y 10..12, a course over the
//        coping, then a dome of smooth stairs round a smooth core with one smooth block on top (y 13..14); 3 the
//        granary's back carries the beam ends through at the upper ceiling (y 8, x 2, 3, 5, 6), a cut lintel over the hay
//        door, the hoist beam out over it with a lantern on its chain; 4 the date palm leans a block in over the kerb
//        (trunk (14, 1..3, 0), (13, 4..6, 1)) under a crown of long fronds (three out, dropping two at the tips; the
//        diagonals two out, dropping one) over the field's corner; 5 the dovecote's cap narrows a step (stairs round a
//        smooth core at y 11, one smooth block on top at y 12) over its band (y 10); its pots now y 8..9, perches at y 8.
//        Also: the hall's water jar left out (a decorated pot holds items, and the Farmer carries his harvest to the
//        store nearest his composter: the kept chest at (8, 1, 6) stays the only one that near).
//        Checked after round 2: look_spots SAME BUILDING (11/17/19 spots, nothing moved); holes.py 0 (open included) at
//        every level; rebuild.py no CHECK line (a diagonal frond first floated: its tip now hangs under its level leaf);
//        composters 1 / 2 / 2, farmland 36 / 74, one carved pumpkin; about 46 / 65 / 68 stacks.
//        Renders for round 2 NOT taken: BUILD_DONE never appeared in 60 minutes of polling while a Gradle build ran;
//        renders/ still holds round 1's. The round-2 drawings to render are in the scratchpad's fsd/ (desert_<L>.nbt,
//        c/desert_<L>_cut<y>.nbt).
//  Open for the critic: the house's front face above the door hood is a plain mud panel under the crown; the roofs are
//  decks nobody climbs to; the shaduf stands on the shipped kerb's line.
//
// --- Named parts (template coordinates as the shipped plan: x across the front, west 0 -> east 14; y up, 0 the layer on
//     the clicked block; z from the front, z = 0, to the back) --------------------------------------------------------
//   House      walls x 1..7, z 1..8 in mud brick on a smooth-sandstone plinth, y 1..5 (the ceiling y 5: spruce vigas
//              across x with cane (bamboo-mosaic) matting between), the roof deck y 6, a mud parapet y 7 with a smooth
//              coping y 8 (level all round: corner horns were tried and read as a castle's crenels); a sandstone batter at y 0
//              along the west wall; spruce beam ends a block out of both side walls at y 5.
//   Portal     the front door's frame proud at z 0: cut-sandstone jambs x 3 and 5 y 1..3, a chiseled lintel, a hood of
//              upside-down stairs at y 4, the parapet stepped up over it to a crown at y 10.
//   Mastabas   mud benches (top slabs) along the front at x 1..2 and 6..7, the latticed windows over them, lanterns on
//              the bench ends.
//   Windows    glass in the wall, a dark-oak lattice screen one out on a sill slab under a hood slab (street, west and
//              back faces); the field side's lights plain under the shade.
//   Flue       the hearth's mud stack on the west wall (x 0, z 5) with its oven breast, smoking over the coping.
//   Dovecote   x 3..5, z 5..7 on the roof (a block in from the back parapet), y 7..12: pots y 8..9 with a perch out
//              before each face's lower pot, the white band y 10, a whitewashed dome y 11..12 (stairs round a core, one
//              block on top).
//   Shade      the palm-rib shade along the field side: a jungle-log beam at y 3 over the gallery (x 8), rafters out to
//              x 9, bamboo-mosaic matting at y 4, palm-trunk posts at z 8 (and z 15, 20 in II), off the walking line.
//   Field      as shipped (x 9..13, z 2..10, channel x 11, the scarecrow in a robe on its footing at z 6), its kerb a
//              cut-sandstone course with a mud-brick wall on it, lanterns on the corners; II's second field z 12..20.
//   Shaduf     on the kerb between the fields: thin posts (10, 11) and (12, 11) y 1..3, a thin bar y 4, the sweep
//              tilted over it from (11, 4, 8), its chain and clay bucket over the channel, a lashed counterweight.
//   Palm       a date palm in sand at the field's front corner (14, 0, 0), leaning in, a crown of long drooping fronds.
//   Passage    (II) z 9 between house and barn, mud end walls at x 1 and 7 with their doors, a low roof deck at y 5
//              under a coping (below both neighbours), a lantern hung in it.
//   Granary    (II) x 1..7, z 10..20: coursed sandstone y 1..4, a cut course y 5 (the loft floor, full but for the
//              ladder's hole), mud brick y 6..9, the upper ceiling y 8 (vigas and matting), the roof deck y 9, parapet y 10, coping y 11; the stalls,
//              the chamber and the tool wall below as shipped; hay and grain jars on the loft; the hay door and its hoist
//              beam at the back, the wagon doors under a lintel; two DOMED BINS on the roof (x 2..4,
//              z 11..13 and 17..19, against the parapet: round mud drums y 10..12, white domes y 13..14).
//   Cellar     (III) the cool store under the hall, x 1..7, z 1..8, 4 deep, sandstone walls, the ladder under the hatch
//              (2, 0, 2), the three kept chests on the west wall, jars and melons on a shelf down the east wall.
import { SIZE as F_SIZE, BASEMENT as F_BASEMENT, IDS as F_IDS, MASSES as F_MASSES, OPENINGS as F_OPENINGS, FURNITURE as F_FURNITURE, CROPS as F_CROPS } from '../farmstead/plan.mjs'

export const SIZE = F_SIZE // as shipped: 15 x 13 x 12, 15 x 15 x 22, III 15 x 15 x 22 over a 4-deep cellar
export const BASEMENT = F_BASEMENT
export const IDS = F_IDS
export const CROPS = F_CROPS
export const FIELD = F_MASSES.field // x 9..13, z 2..10, channel x 11, scarecrow z 6 (the pins)
export const FIELD2 = F_MASSES.field2

// Palette 60/30/10: mud brick and packed mud walls, the granary's coursed sandstone ground storey (60) / smooth and cut
// sandstone dressings: plinth, batter, coping, the door's crown, portal, sills, the dovecote's band (30) / spruce beam ends and the
// shipped spruce doors and joinery, dark-oak lattice, palm-trunk jungle posts and cane matting (10), with the field's
// greens, the palm and the scarecrow's orange as the accents.
export const PALETTE = {
  mud: [['mud_bricks', 7], ['packed_mud', 2]],
  ashlar: [['sandstone', 6], ['cut_sandstone', 3], ['smooth_sandstone', 1]],
  plinth: 'smooth_sandstone', course: 'cut_sandstone', carved: 'chiseled_sandstone', jamb: 'cut_sandstone',
  hood: 'sandstone_stairs', batter: 'sandstone_stairs', step: 'sandstone_stairs',
  deck: 'smooth_sandstone', coping: 'smooth_sandstone_slab', horn: 'smooth_sandstone', parapet: 'mud_bricks',
  floor: 'smooth_sandstone', barnFloor: 'packed_mud', loftFloor: 'spruce_planks', gallery: 'packed_mud',
  viga: 'stripped_spruce_log', vigaEnd: 'spruce_fence', matting: 'bamboo_mosaic', mattingSlab: 'bamboo_mosaic_slab',
  wood: 'spruce', glass: 'glass_pane', lattice: 'dark_oak_trapdoor', sill: 'smooth_sandstone_slab', bench: 'smooth_sandstone_slab',
  palmPost: 'jungle_log', palmBeam: 'stripped_jungle_log', rafter: 'jungle_fence',
  kerb: 'cut_sandstone', kerbWall: 'mud_brick_wall', partition: 'bamboo_mosaic',
  pot: 'flower_pot', band: 'smooth_sandstone', domeStairs: 'smooth_sandstone_stairs', binCorner: 'mud_brick_wall',
  cellarWall: [['sandstone', 5], ['cut_sandstone', 2], ['smooth_sandstone', 1]], cellarFloor: 'smooth_sandstone',
  palm: { soil: 'sand', trunk: 'jungle_log', leaves: 'jungle_leaves' },
}

export const HOUSE = { x0: 1, x1: 7, z0: 1, z1: 8, ceilingY: 5, deckY: 6, parapetY: 7, vigaZ: [2, 4, 6] }
export const PORTAL = { x0: 3, x1: 5, z: 0, top: 3, hoodY: 4, crown: [10] }
export const BENCHES = [1, 2, 6, 7] // x on z 0
export const FLUE = { x: 0, z: 5, top: 8, breastTop: 3 }
export const DOVECOTE = { x0: 3, z0: 5, y0: 7 }
export const GALLERY = { x: 8, z0: 1, z1: { 1: 9, 2: 20 }, posts: { 1: [8], 2: [8, 15, 20] }, shadeY: 4 }
// the sweep's pole cells [dz, dy] from (11, 5, 11), front (low, the bucket's) end first; the lashed counterweight
export const SHADUF = { x: 10, z: 11, y0: 1, sweep: {
  1: { cells: [[-3, -1], [-2, 0], [-1, 0]], weight: [0, 0] }, // I: the stone lashed on the short end over the bar
  2: { cells: [[-3, -1], [-2, 0], [-1, 0], [0, 0], [1, 1]], weight: [2, 1] }, // II: the short end runs on over the second field
} }
export const SCARECROW_Z = 5 // a block nearer the front than shipped, clear of the sweep's bucket at z 8
export const PASSAGE = { z: 9, roofY: 5 }
export const GRANARY = { x0: 1, x1: 7, z0: 10, z1: 20, course: 5, ceilingY: 8, deckY: 9, parapetY: 10, vigaZ: [11, 13, 15, 17, 19], backVigas: [2, 3, 5, 6], bins: [[2, 11], [2, 17]], binDrum: 3 }
export const BARN = F_MASSES.barn // the shipped interior: chamber, stalls, tools, loft line
export const CELLAR = F_MASSES.cellar

// The shipped openings that hold the building together, cell for cell (doors and their facings, the hatch); the
// windows keep the shipped cells, dressed this look's way.
export const OPENINGS = {
  frontDoor: F_OPENINGS.frontDoor, backDoor: F_OPENINGS.backDoor, barnDoor: F_OPENINGS.barnDoor,
  barnSideDoor: F_OPENINGS.barnSideDoor, wagonDoors: F_OPENINGS.wagonDoors, hayDoor: F_OPENINGS.hayDoor,
  galleryStep: F_OPENINGS.galleryStep,
  windows: [ // screen: a lattice screen out on that face; until: drawn only up to that level
    { cells: [[2, 2, 1], [2, 3, 1]], out: 'north', screen: true },
    { cells: [[6, 2, 1], [6, 3, 1]], out: 'north', screen: true },
    { cells: [[1, 2, 2], [1, 3, 2]], out: 'west', screen: true },
    { cells: [[1, 2, 7], [1, 3, 7]], out: 'west', screen: true },
    { cells: [[7, 2, 3], [7, 3, 3]], out: 'east' }, { cells: [[7, 2, 5], [7, 3, 5]], out: 'east' },
    { cells: [[7, 2, 7], [7, 3, 7]], out: 'east' },
    { cells: [[2, 2, 8], [2, 3, 8]], out: 'south', screen: true, until: 1 },
    { cells: [[2, 2, 8], [2, 3, 8]], out: 'south', level: 2 }, // inside the passage from II
    { level: 2, cells: [[1, 2, 13], [1, 3, 13]], out: 'west', screen: true },
    { level: 2, cells: [[1, 2, 17], [1, 3, 17]], out: 'west', screen: true },
    { level: 2, cells: [[7, 2, 12], [7, 3, 12]], out: 'east' }, { level: 2, cells: [[7, 2, 19], [7, 3, 19]], out: 'east' },
    // the loft's vents: small lights high in the mud storey
    { level: 2, cells: [[1, 7, 12]], out: 'west' }, { level: 2, cells: [[1, 7, 18]], out: 'west' },
    { level: 2, cells: [[7, 7, 14]], out: 'east' }, { level: 2, cells: [[7, 7, 18]], out: 'east' },
    { level: 2, cells: [[2, 7, 10]], out: 'north' }, { level: 2, cells: [[6, 7, 10]], out: 'north' },
    { level: 2, cells: [[2, 7, 20]], out: 'south' }, { level: 2, cells: [[6, 7, 20]], out: 'south' },
  ],
}

// Every piece of furniture: the shipped list, the same parts, cells and facings. Lamps hang straight from the flat
// ceilings (chain 0: the hall's vigas at y 5, the granary's loft floor at y 5, the cellar's ceiling at y 0); the rugs
// are kilims (red in the hall, orange in the chamber).
export const FURNITURE = F_FURNITURE.map(f => {
  if (f.part === 'hangingLamp') return { ...f, chain: 0 }
  if (f.part === 'rug') return { ...f, colour: f.room === 'Hall' ? 'red' : 'orange' }
  return f
})

// The walking lines (as shipped): the first Farmer along the front, up the gallery step to his composter under the
// shade; the second (II) in at the front door, down the hall's aisle, out the back door, across the passage, out its
// east door onto the gallery and south to the second composter; III from the front door to the hatch and down.
export const WALK = {
  first: [[1, 0, -1], [8, 0, -1], [8, 1, 0], [8, 1, 1], [8, 1, 2], [8, 1, 3]],
  second: [[4, 0, -1], [4, 0, 0], [4, 1, 1], [4, 1, 2], [4, 1, 7], [4, 1, 8], [4, 1, 9], [8, 1, 9], [8, 1, 12]],
  cellar: [[4, 1, 2], [3, 1, 2], [2, 0, 2], [2, -3, 2], [3, -3, 3], [3, -3, 6]],
}

export const OUTSIDE = {
  fieldLamps: { 1: [[9, 1], [14, 1], [14, 11]], 2: [[14, 21], [9, 21]] },
  // the date palm in sand at the field's front corner, its trunk leaning a block in over the kerb, its crown over the field
  palm: { soil: [14, 0, 0], trunk: [[14, 1, 0], [14, 2, 0], [14, 3, 0], [13, 4, 1], [13, 5, 1], [13, 6, 1]], crown: [13, 7, 1] },
  jars: { 1: [[6, 1, 9], [7, 1, 9]], 2: [[0, 1, 18], [0, 1, 19]] }, // I: water jars on the back step; II: by the granary's west wall
}
