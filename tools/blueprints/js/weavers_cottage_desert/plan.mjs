// Weaver's Cottage, the DESERT look (looks/desert/weavers_cottage[_2], ROADMAP 49.10d, as 49.10a): the same building as
// the shipped Weaver's Cottage (../weavers_cottage/plan.mjs: the workroom with its loom and chest, the loft over it with
// its chest, the doors, the flight, the fold on the east side with its fence and gate, Level II's dye garden behind, and
// every furniture spot in the same cell at both levels), drawn again for a hot, dry village. It speaks the desert
// village's language the Cottage, the Stone House, the Terrace and the Manor set (coursed sandstone below, mud brick
// above, a cut-sandstone course at the loft floor, flat roofs behind a smooth coping, spruce beam ends, lattice for
// shade, arches, a windcatcher), and it is the village's DYER-WEAVER's house: its roof is where the cloth dries.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The dye-works court": the workroom, a dye shed and the fold as three closed sides of a walled court, blank walls
//     to the street. The truest desert plan, but the brief pins the range on z 2..8 with its door and its long window on
//     the street side, and the fold must be fences (walls don't count): the court would be the pen, in front of the door.
//  2. "The black tent": the loom under a Bedouin goat-hair tent pitched against a low mud house, the flock in a thorn
//     ring. A herder's picture, but the loft over the workroom (a chest at y 5, lamps at y 8) needs a real upper storey,
//     and a tent over two storeys is a box with a hat.
//  3. "The Dyer's Roof": a two-storey block of coursed sandstone and mud brick under a flat roof behind a coped parapet,
//     the weaver's long window shaded by a two-bay ARCADE of round arches along the street (its roof a little terrace
//     under the loft's windows), the door in a carved PORTAL under a dark lattice ORIEL, a slotted WINDCATCHER over the
//     back corner, and on the roof two DRYING RACKS hung with the dyed cloth, the colours showing over the coping to the
//     street. The fold beside it under a palm-rib SHADE on posts; Level II walls in an irrigated dye garden behind, with
//     a rill of water, rows of dye flowers by colour and a date palm. Story: the dyer-weaver of a caravan town works in
//     the shade of his arcade and spreads his indigo and madder cloth on the roof to dry in the sun.
//  Built: 3. It is the strongest: from 20 blocks it is a flat block with a tower and a row of bright cloth over the
//  parapet, a skyline no other desert house has (the cloth says "weaver" before a door is seen); the arcade and the
//  oriel give the front deep shade and two layers; and Level II reads as the garden the dyes come from. Not the safest:
//  cloth on a roof nobody climbs to, and the arcade's arches only two blocks high.
//
// --- Design notes: what is done, what is left (2026-11-19) -----------------------------------------------------------
//  Done: Levels I and II as candidates (build.sh -> build/rebuild/weavers_cottage_desert/), never live. The range on the
//  shipped walls (x 1..11, z 2..8), the loft floor at y 4; the shipped furniture list drawn as is (the same spots, kinds
//  and facings), the loom (2, 1, 3) and chest (6, 1, 3) in the kept cells, the loft's chest (10, 5, 4), the three spruce
//  doors and the flight in their cells; the loft's two lamps hang straight from the flat deck (chain 0 where the
//  shipped ones hang 2 links). The shipped hearth (a fixture, no spot) is left out: no fire in a desert workroom.
//  The fold keeps 13 fence blocks and its gate (the shipped west rails are a mud-brick wall here); I has no flower,
//  II 45 flower blocks (the code wants II >= I + 8).
//  Checked: look_spots.py weavers_cottage_desert weavers_cottage: SAME BUILDING (14 and 14 shipped spots, none missing,
//  no bed, container, job block or door moved; only the loft lamps' chains differ); rebuild.py no CHECK line at either
//  level; holes.py 0 (with open) at both; every id vanilla 1.21.1; II keeps 100 % of I; one loom each level; about 53
//  and 65 stacks (limit 81).
//  Renders (front, back, left, right, top per level, a low front of I, cutaways at y 3 and 7 per level, top and front):
//  build/rebuild/weavers_cottage_desert/renders/ (desert_<L>_<view>.png, desert_<L>_cut<y>_<view>.png).
//  Tried and changed: the racks first ran to the parapet and their ends read to holes.py as pits in the roof (now a
//  block in from the west parapet and clear of the windcatcher); full-height smooth quoins and a smooth deck rim framed
//  the upper storey as a pale box (quoins on the ground storey only, the rim mud brick); the windcatcher stood in the
//  roof's middle like a box on a box (now flush with the back wall); a moulding added round the back and ends (the back
//  was one flat mud panel); a 4-high palm read as a bush (6 high, long fronds); the fold's short west rail made a false
//  "gap between roofs" under the moulding (a mud wall there now).
//  Round 2 (the critic's notes, briefs/weavers_cottage_looks_critic.md, BACK narrow on Detail 3): 1 the oriel is a bay
//        hung out two deep (z 0..1, y 5..7) on a row of upside-down sandstone corbels at y 4, dark-oak lattice on its
//        front (between two slim fence corner posts) and on both side faces (no logs), a dark-oak cornice a block out
//        on every side at y 8; 2 each rack's posts rise to the rail's height and the rail runs between them, its log
//        along it, so no log end shows from the sides; 3 the date palm drawn by hand: a 6-high bare trunk and a thin
//        crown (a tuft, four long fronds drooping two courses to their tips, four short ones); optional polish: the
//        loft-floor moulding turned round the back west and front east corners and carried along the arcade's west end.
//        Checked after round 2: look_spots SAME BUILDING (14/14 spots each level, nothing moved; loft lamps on chain 0,
//        allowed); holes.py 0 (with open) at both levels (a back east corner moulding was tried and made a "meet" with
//        the fold wall's coping: left out); rebuild.py no CHECK line. Renders for round 2 (build/rebuild/
//        weavers_cottage_desert/renders_r2/) NOT taken: the render lock (/tmp/claude-0/render.ok) never appeared in 40
//        minutes while a Gradle build ran.
//  Open for the critic: the arcade's arches are two blocks high (the loft floor's course is at y 4), so they read small
//  from a distance; the racks' spruce rail ends show as log ends from the sides; the roof is a deck nobody climbs to.
//
// --- Named parts (template coordinates: x across the front, west 0 -> east 17; z from the front, z = 0, to the back) --
//   Range       walls x 1..11, z 2..8: coursed sandstone y 1..3 on a smooth plinth, a cut-sandstone course y 4 (the
//               loft floor), mud brick y 5..8, the roof deck y 9 (its rim mud brick: one mud storey up to the
//               parapet y 10), a smooth coping y 11; smooth sandstone quoins up the ground storey's corners; spruce beam ends (vigas) a block out at y 8.
//   Arcade      x 1..7, z 0..1: piers at x 1, 4, 7 (z 0), two round arches (x 2..3, 5..6, heads at y 3) before the
//               weaver's window, a deck at y 4 with a mud-brick balustrade on its edge (a little terrace under the
//               loft's windows); lanterns hung in both bays.
//   Portal      x 8..10, z 1: chiseled jambs and lintel round the door's reveal, the step, lamps on two short posts.
//   Oriel       over the portal, a bay hung out two deep (x 8..10, z 0..1, y 5..7) on a row of corbels at y 4: dark-oak
//               lattice on its front and both sides between slim fence corner posts, a projecting cornice at y 8.
//   Windcatcher x 8..10, z 6..8, its back face flush with the back wall, y 10..13 (slots y 11..12, two over the coping).
//   Racks       two drying racks on the deck (z 3, x 3..7; z 6, x 3..6, clear of the windcatcher): fence posts up to y 12, a spruce rail between them (no log end shows), the dyed
//               cloth hung on both sides; the front rack's street-side cloth hangs over the coping.
//   Fold        x 12..17, z 2..11: dark-oak fence on a sandstone kerb (the gate on the street), a mud-brick back wall
//               with a coping turning up to the range's back corner, grass for grazing worn bare at the gate and the fold door, a palm-rib SHADE (x 12..14,
//               z 5..8) on two posts and a beam, against the range over the fold door, hay under it.
//   Garden (II) x 0..11, z 8..16: mud-brick walls with a smooth coping, the back door's walk, rows of dye flowers in
//               dirt by colour (madder red, saffron yellow, indigo blue, purple), a rill of water across the middle,
//               the dye jars and a date palm at the far end.
import { FURNITURE as W_FURNITURE, OPENINGS as W_OPENINGS } from '../weavers_cottage/plan.mjs'

export const SIZE = { 1: [18, 14, 12], 2: [18, 14, 17] } // as shipped

// Palette 60/30/10: coursed sandstone ground storey and mud brick above (60) / smooth and cut sandstone dressings: the
// plinth, quoins, the loft-floor course, the arcade, the coping, the windcatcher's corners (30) / dark-oak lattice,
// posts and fence, spruce beam ends and the shipped spruce doors and furniture, and the cloth (10).
export const PALETTE = {
  ashlar: [['sandstone', 6], ['cut_sandstone', 3], ['smooth_sandstone', 1]],
  mud: [['mud_bricks', 7], ['packed_mud', 2]],
  plinth: 'smooth_sandstone', quoin: 'smooth_sandstone', course: 'cut_sandstone', carved: 'chiseled_sandstone',
  pier: 'cut_sandstone', moulding: 'sandstone_stairs', corbel: 'sandstone_stairs', arch: 'sandstone_stairs', deck: 'smooth_sandstone', coping: 'smooth_sandstone_slab',
  parapet: 'mud_bricks', balustrade: 'mud_brick_wall', brow: 'cut_sandstone', paving: 'smooth_sandstone',
  floor: 'smooth_sandstone', loftFloor: 'spruce_planks', under: 'spruce_planks', step: 'sandstone_stairs',
  beam: 'spruce_fence', rail: 'spruce_log', door: 'spruce', wood: 'spruce', glass: 'glass_pane',
  lattice: 'dark_oak_trapdoor', latticePost: 'dark_oak_log', latticeRoof: 'dark_oak_slab',
  fence: 'dark_oak', kerb: 'cut_sandstone', lampPost: 'sandstone_wall',
  catcher: { body: [['mud_bricks', 5], ['packed_mud', 1]], corner: 'smooth_sandstone', cap: 'smooth_sandstone', capTop: 'smooth_sandstone_slab' },
  shade: { post: 'dark_oak_fence', beam: 'dark_oak_log', slab: 'dark_oak_slab' },
  soil: 'dirt', water: 'water',
}

export const RANGE = { x0: 1, x1: 11, z0: 2, z1: 8, course: 4, loftY: 4, wallTop: 8, deckY: 9, parapetY: 10 }

export const ARCADE = { x0: 1, x1: 7, z0: 0, z1: 1, piers: [1, 4, 7], arches: [[2, 3], [5, 6]], headY: 3, deckY: 4, lamps: [[2, 3, 1], [6, 3, 1]] }
export const PORTAL = { x0: 8, x1: 10, z: 1, top: 3, lampPosts: [[8, 0], [10, 0]] }
export const ORIEL = { x0: 8, x1: 10, z: 1, y0: 4, y1: 8 } // the bay's back row (z 1) and its floor (y 4); it hangs out to z 0
export const CATCHER = { x0: 8, z0: 6, y0: 10, y1: 13 } // flush with the back wall
export const RACKS = [
  { z: 3, x0: 3, x1: 7, cloth: { north: ['blue', 'red', 'yellow'], south: ['orange', 'light_blue', 'magenta'] } },
  { z: 6, x0: 3, x1: 6, cloth: { north: ['purple', 'red'], south: ['blue', 'yellow'] } },
]
export const VIGAS = { y: 8, back: [2, 4, 6, 8, 10], front: [2, 4, 6], west: [3, 5, 7], east: [3, 5, 7] }

// The shipped openings that hold the building together, cell for cell: the doors and the flight to the loft (with its
// well). The windows are this look's own: the weaver's window behind the arcade's two bays, small deep lights elsewhere
// under cut-sandstone brows.
export const OPENINGS = {
  frontDoor: W_OPENINGS.frontDoor, foldDoor: W_OPENINGS.foldDoor, backDoor: W_OPENINGS.backDoor,
  stairs: W_OPENINGS.stairs,
  weaverWindow: { z: 2, xs: [2, 3, 5, 6], ys: [2, 3] }, // the two arches' lights; the pier line x 4 between them
  windows: [ // [cells], out
    { name: 'loft front west', cells: [[2, 5, 2], [3, 5, 2], [2, 6, 2], [3, 6, 2]], out: 'north' },
    { name: 'loft front east', cells: [[5, 5, 2], [6, 5, 2], [5, 6, 2], [6, 6, 2]], out: 'north' },
    { name: 'loft behind the oriel', cells: [[9, 5, 2], [9, 6, 2]], out: 'north', brow: false },
    { name: 'workroom west', cells: [[1, 2, 5], [1, 3, 5]], out: 'west' },
    { name: 'loft west', cells: [[1, 5, 5], [1, 6, 5]], out: 'west' },
    { name: 'workroom east', cells: [[11, 2, 3], [11, 3, 3]], out: 'east' },
    { name: 'loft east', cells: [[11, 5, 6], [11, 6, 6]], out: 'east' },
    { name: 'workroom back west', cells: [[2, 2, 8], [2, 3, 8]], out: 'south' },
    { name: 'workroom back east', cells: [[8, 2, 8], [8, 3, 8]], out: 'south' },
    { name: 'loft back west', cells: [[3, 5, 8], [3, 6, 8]], out: 'south' },
    { name: 'loft back east', cells: [[7, 5, 8], [7, 6, 8]], out: 'south' },
  ],
}

// Every furniture piece: the shipped cottage's, the same parts in the same cells, except the hearth (a fixture, not a
// spot: a desert workroom has no fire; its corner holds fleeces) and the loft's two lamps, which hang straight from the
// flat deck (chain 0 where the shipped ones hang 2 links from the ridge).
export const FURNITURE = W_FURNITURE.filter(f => f.part !== 'hearth')
  .map(f => (f.part === 'hangingLamp' && f.pos[1] > RANGE.loftY ? { ...f, chain: RANGE.deckY - f.pos[1] - 1 } : f))

// Fleeces and bolts (wool, not furniture): undyed fleeces by the workroom's west wall and behind his chest; dyed bolts
// stacked in the loft's west end.
export const FLEECES = [
  [2, 1, 6, 'white_wool'], [2, 1, 7, 'white_wool'], [3, 1, 7, 'light_gray_wool'], [2, 2, 7, 'brown_wool'],
  [7, 1, 3, 'white_wool'], // the back of his chest (the kept chest faces west)
  [2, 5, 4, 'blue_wool'], [2, 5, 5, 'red_wool'], [2, 6, 4, 'yellow_wool'], [2, 5, 6, 'white_wool'],
  [2, 5, 7, 'blue_wool'], [3, 5, 7, 'orange_wool'], [2, 6, 7, 'red_wool'], [4, 5, 7, 'white_wool'],
]

// The walking lines: street -> the step -> the portal -> the door -> workroom -> the weaver's seat at the loom; -> the
// fold door and in among the flock under the shade; -> the flight -> the loft and the wool chest. II: -> the back door
// -> the garden's walk.
export const WALK = [
  [1, 0, -2], [9, 0, -1], [9, 0, 0], [9, 1, 1], [9, 1, 2], [9, 1, 4], [3, 1, 4], [3, 1, 3],
  [10, 1, 5], [10, 1, 6], [11, 1, 6], [12, 1, 6], [14, 1, 7],
  [10, 1, 7], [9, 2, 7], [8, 3, 7], [7, 4, 7], [7, 5, 6], [10, 5, 5],
  [4, 1, 7], [4, 1, 8], [4, 0, 9], [4, 1, 14],
]

export const FOLD = {
  fence: [[12, 1, 2], [17, 1, 2], [17, 1, 10]],
  gate: { at: [14, 1, 2], facing: 'north' },
  backWall: { z: 11, x0: 11, x1: 17, top: 2, west: [9, 10] }, // mud brick y 1..top, the coping over it; it turns up
  // x 11 to the range's back corner (z 9..10)
  ground: { x0: 12, x1: 16, z0: 3, z1: 10 },
  worn: [[14, 3], [14, 4], [13, 4], [15, 4], [12, 6], [12, 7], [13, 6], [12, 5], [13, 7]],
  sand: [[16, 3], [16, 10], [15, 10]],
  shade: { x0: 12, x1: 14, z0: 5, z1: 8, y: 4, posts: [5, 8] },
  hay: [[12, 1, 8], [13, 1, 8], [12, 2, 8]],
  cornerLamp: [17, 2, 2],
}

// Level II: the dye garden. Rows of dye flowers in dirt by the colour they give, a rill of water across the middle, the
// walk from the back door down to a paved strip with the dye jars, a date palm in the far corner.
export const GARDEN = {
  wall: { x0: 0, x1: 11, z0: 8, z1: 16, eastFrom: 12, top: 1 }, // mud brick y 1..top on x0, z1 and x1 (z >= eastFrom)
  walk: { x: 4, z0: 9, z1: 15 },
  apron: { z: 9, x0: 1, x1: 10 }, // paving under the back wall's beam ends
  beds: { west: [1, 3], east: [5, 10] },
  rows: {
    10: [['poppy'], ['rose_bush', 'poppy']], // madder red
    11: [['dandelion'], ['sunflower', 'dandelion']], // saffron and weld yellow
    13: [['cornflower'], ['blue_orchid', 'cornflower']], // indigo blue
    14: [['allium'], ['lilac', 'allium']], // purple
  },
  rill: { z: 12 }, // water across both beds, the walk crossing it on paving
  end: { z: 15, x0: 1, x1: 10 },
  jars: [[1, 1, 15], [2, 1, 15], [3, 1, 15]],
  palm: [9, 0, 15], palmHeight: 6,
}
