// Noble's Manor, the DESERT look (looks/desert/manor[_2.._4], ROADMAP 49.10f, as 49.10a): the same building as the
// shipped Manor (../manor/plan.mjs: the west wing, the hall with the Long Gallery over it, the porch tower, the garden at
// II, the ballroom wing at III, the 4-deep cellar at IV; every bed, door, kept container, the grand stair, the hatch and
// every furniture spot in the same cell), drawn again for a hot, dry town. It speaks the desert village's language the
// Terrace and the Townhouse set (coursed sandstone below, mud brick above, flat roofs behind a coping, lattice for
// shade, windcatchers), raised to a noble's palace: a qasr.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The courtyard palace": the wing, the hall and (III) the ballroom as three closed ranges round a paved court with a
//     pool, blank walls to the street, one gate. The truest desert plan, but the shipped house's front is the hall's
//     recess between the wing and the porch tower: the court would be in front of the hall, in the plot's first four
//     rows, three blocks wide: a light well, not a court. And a closed front reads as a storehouse.
//  2. "The arcaded villa": every mass two storeys behind a ground-floor arcade of round arches, one long flat roof line.
//     Calm and shady, but an arcade four rows deep is a roofed room the outside walks into, and one roof height for
//     every mass is a box from 20 blocks.
//  3. "The Qasr of the Windcatcher": three flat-roofed masses at three heights (the wing 12, the hall 13, at III the
//     ballroom 15), each behind a crenellated parapet with proud corner piers that rise above it like little towers; the
//     porch tower is the house's great WINDCATCHER, a badgir head slotted on every face under a cornice at 17, its front
//     an IWAN: two proud piers to the cornice, a lattice oriel for the study over the door, a pointed arch over the
//     oriel in a band of blue tile. Lattice rawshans run out over the wing's bay window and the hall's two front
//     windows; the garden (II) is a four-part paradise garden of palms round a fountain; the ballroom (III) rises over
//     the rest with a clerestory lantern on its roof and a pointed great window to the street. Story: a caravan
//     family's palace, built to pull the wind down into its rooms and to shade every window from the sun.
//  Built: 3. It is the strongest: the outline is a stepped skyline of crenellations and corner piers climbing to one
//  slotted tower, a shape no other house in the village has; every face has depth (piers, rawshans out on corbels, a
//  cornice under every parapet); and its growth reads: the garden is walled in behind, the ballroom stands up beside
//  the hall, taller and lit from its lantern. Not the safest: three heights of parapet must meet cleanly and the
//  windcatcher's slots are open on every face.
//
// --- Design notes: what is done, what is left (2026-11-19) -----------------------------------------------------------
//  Done: Levels I-IV as candidates (build.sh -> build/rebuild/manor_desert/). The rooms come from the shipped plan
//  through the parts library's manorRooms (noble_house.mjs): floors, partitions, the arch, the grand stair, every
//  door, the hearths, the ballroom's floor and gallery, the cellar and hatch, every furniture piece.
//  Lamps hang on the same cells; their chains are this look's (the Long Gallery's two reach the flat ceiling at 11,
//  the ballroom's middle chandelier the lantern's ceiling at 16).
//  Checked: look_spots.py manor_desert manor: SAME BUILDING (100, 100, 123, 125 shipped spots, none missing; no bed,
//  container, door or ladder moved; the cellar's casks where the shipped ones lie; only the middle chandelier's chain
//  differs); holes.py 0 (with open) at every level; rebuild.py no CHECK line.
//  Renders: build/rebuild/mn_r1/shots/desert_<L>_<view>.png, cutaways desert_4_cut<y>_20_65.png (y -1, 4, 9, 12).
//  Tried and changed: lamps hung under the oriel's corbel stairs (the game drops them: carved corbels now); a sky-blue
//  tile read as a hole through the iwan (cyan glazed now); piers beside the tower crowded the front bays (dropped);
//  four palms roofed the garden into a "room" and left pits of sky by the walls (two now, in the back corners, crowns
//  over the walls; azaleas in the front beds).
//  Open for the critic: the windcatcher's slots are a block deep (the shaft over the study is solid); the roof decks
//  are not reached (decks, not rooms).
//
// --- Named parts (template coordinates: x across the front, west 0 -> east; z from the front, z = 0, to the back) ---
//   West wing   walls x 1..7, z 1..15: coursed sandstone y 1..4, a cut-sandstone course at the chamber floor (y 5),
//               mud brick y 6..9, the roof deck y 10, a mud-brick parapet y 11 and merlons y 12; corner piers proud of
//               the faces to y 12, capped at 13; the parlour's BAY out at z 0 (x 2..6, y 1..4) under the front
//               chamber's RAWSHAN (z 0, y 5..10); the kitchen stack on the west side (x 0, z 11); a small windcatcher
//               over the great chamber (x 3..5, z 12..14, y 11..14).
//   Hall        walls x 7..19, z 5..13: two storeys (the hall y 1..4, the Long Gallery y 6..10), the deck y 11, the
//               parapet y 12, merlons y 13; a rawshan over each front window pair (z 4, x 8..10 and x 16..18); the hall
//               stack on the back wall (x 10, z 14); a cornice of upside-down stairs under every parapet.
//   Tower       x 11..15, z 1..5: the IWAN on its front (piers at x 11 and 15, z 0, to y 16; the study's lattice oriel
//               over the door; a pointed arch at y 10..11 in a band of tile), the BADGIR head y 13..16 slotted on
//               every face, the cap and cornice at 17.
//   Garden (II) walls x 7, x 19 and z 24 in mud brick to y 3 with a coping; four palm beds round a fountain at the cross
//               (x 13, z 19), a stone bench at the end of the walk, lamps on the piers.
//   Ballroom (III) walls x 19..27, z 1..15, one room open to y 12, the deck y 13, parapet y 14, merlons y 15; a
//               clerestory LANTERN x 21..25, z 5..11, y 13..16; the pointed great window on the front between two proud
//               piers; three tall pointed windows on the east between piers at x 28; the east garden behind it.
//   Cellar (IV) under the kitchen and the hall's west end (x 1..13, z 8..15, 4 deep), ribbed with sandstone arches.
import {
  SIZE, BASEMENT, MASSES as M_MASSES, OPENINGS as M_OPENINGS, FURNITURE as M_FURNITURE, BRACKET_LAMPS,
} from '../manor/plan.mjs'

export const LEVELS = [1, 2, 3, 4]
export const IDS = { 1: 'manor', 2: 'manor_2', 3: 'manor_3', 4: 'manor_4' }
export const SIZES = SIZE // as shipped: 21 x 18 x 17, 21 x 18 x 25, 29 x 18 x 25, 29 x 18 x 25
export { BASEMENT }
export const SHIPPED = { MASSES: M_MASSES, OPENINGS: M_OPENINGS, FURNITURE: M_FURNITURE, BRACKET_LAMPS }

// Palette 60/30/10: coursed sandstone ground storey and mud brick above (60) / smooth and cut sandstone dressings:
// piers, courses, copings, merlons, cornices, sills, the windcatcher's fins (30) / dark-oak lattice and joinery and a
// band of blue tile on the iwan and the great window (10). The rooms keep the shipped Manor's dark oak furniture.
export const PALETTE = {
  ashlar: [['sandstone', 6], ['cut_sandstone', 3], ['smooth_sandstone', 1]],
  mud: [['mud_bricks', 7], ['packed_mud', 2]],
  plinth: 'smooth_sandstone', pier: 'smooth_sandstone', course: 'cut_sandstone', carved: 'chiseled_sandstone',
  deck: 'smooth_sandstone', parapet: 'mud_bricks', merlon: 'smooth_sandstone', crenel: 'smooth_sandstone_slab',
  cap: 'smooth_sandstone_slab', cornice: 'smooth_sandstone_stairs', corbel: 'smooth_sandstone_stairs',
  tile: 'cyan_glazed_terracotta', sill: 'smooth_sandstone_slab', paving: 'smooth_sandstone', paving2: 'cut_sandstone',
  step: 'sandstone_stairs', glass: 'glass_pane', lampPost: 'sandstone_wall',
  lattice: 'dark_oak_trapdoor', latticePost: 'dark_oak_log', latticeSill: 'dark_oak_slab', latticeRoof: 'dark_oak_stairs',
  catcher: { body: [['mud_bricks', 5], ['packed_mud', 1]], corner: 'smooth_sandstone', cap: 'smooth_sandstone', capTop: 'smooth_sandstone_slab' },
  badgir: { body: 'mud_bricks', fin: 'smooth_sandstone', cap: 'smooth_sandstone', cornice: 'smooth_sandstone_stairs' },
  stack: 'mud_bricks', stackStair: 'mud_brick_stairs',
  soil: 'grass_block', palmTrunk: 'jungle_log', palmLeaves: 'jungle_leaves', water: 'water',
  // the rooms (noble_house.mjs manorRooms)
  rooms: {
    floor: 'smooth_sandstone', hallFloor: ['smooth_sandstone', 'cut_red_sandstone'], ballFloor: ['smooth_sandstone', 'smooth_red_sandstone'],
    upperFloor: 'spruce_planks', partition: 'smooth_sandstone', wood: 'dark_oak', doorWood: 'dark_oak', frontDoorWood: 'dark_oak',
    stairUnder: 'smooth_sandstone', beam: 'dark_oak_log', step: 'sandstone_stairs', bracket: 'chiseled_sandstone',
    cellarWall: null, cellarFloor: 'smooth_sandstone', // cellarWall: a mix(), set in the recipe
  },
  cellarWall: [['sandstone', 5], ['cut_sandstone', 2], ['mud_bricks', 1]],
}

// Heights of the masses
export const Y = { course: 5, wingDeck: 10, hallDeck: 11, towerTop: 12, badgir: [13, 16], ballDeck: 13 }

export const MASSES = {
  wing: { x0: 1, z0: 1, x1: 7, z1: 15, top: 9, deck: 10 },
  hall: { x0: 7, z0: 5, x1: 19, z1: 13, top: 10, deck: 11 },
  tower: { x0: 11, z0: 1, x1: 15, z1: 5, top: 12 },
  ballroom: { x0: 19, z0: 1, x1: 27, z1: 15, top: 12, deck: 13, lantern: { x0: 21, z0: 5, x1: 25, z1: 11, y0: 13, y1: 15 } },
  // proud piers: [x, z, top]: each runs up to its parapet's course and is capped by a slab at the coping's height,
  // part of the parapet (round 3: a pier above it read as a bare fin)
  piers: {
    1: [[1, 0, 11], [7, 0, 11], [0, 1, 11], [0, 8, 11], [0, 15, 11], [1, 16, 11], [7, 16, 11],
      [19, 4, 12], [20, 5, 12], [20, 9, 12], [20, 13, 12], [19, 14, 12], [11, 0, 12], [15, 0, 12]],
    3: [[20, 0, 14], [26, 0, 14], [28, 1, 14], [28, 6, 14], [28, 10, 14], [28, 15, 14], [27, 16, 14]],
  },

  gonePiersAt3: [[20, 5], [20, 9], [20, 13]], // inside the ballroom
  windcatcher: { x0: 3, z0: 12, y0: 11, y1: 14 },
  stacks: { kitchen: { x: 0, z: 11, top: 13, breastTop: 6 }, hall: { x: 10, z: 14, top: 14, breastTop: 7 } },
  garden: { x0: 7, x1: 19, z0: 14, z1: 24, wallTop: 3, piers: [7, 11, 15, 19] },
  gardenEast: { x0: 19, x1: 27, z0: 16, z1: 24 },
}

// The shipped openings that hold the rooms together come through manorRooms; the windows are this look's own:
// [x, z] cells on a wall line (axis 'x': a z = line wall, cells along x), the rows y, the side they look out of, a
// dressing: 'lattice' (dark-oak lattice shutters either side and a sill), 'sill', or null (behind a rawshan, or inside).
export const WINDOWS = {
  1: [
    { name: 'front chamber', axis: 'x', line: 1, at: [3, 5], y: [7, 8], out: 'north', dress: null }, // behind the rawshan
    { name: 'great chamber', axis: 'x', line: 15, at: [3, 5], y: [7, 8], out: 'south', dress: 'lattice' },
    { name: 'kitchen back', axis: 'x', line: 15, at: [3, 5], y: [2, 3], out: 'south', dress: 'sill' },
    { name: 'kitchen west', axis: 'z', line: 1, at: [13], y: [2, 3], out: 'west', dress: 'sill' },
    { name: 'parlour west', axis: 'z', line: 1, at: [4, 6], y: [2, 3], out: 'west', dress: 'sill' },
    { name: 'front chamber west', axis: 'z', line: 1, at: [4], y: [7, 8], out: 'west', dress: 'lattice' },
    { name: 'great chamber west', axis: 'z', line: 1, at: [13], y: [7, 8], out: 'west', dress: 'lattice' },
    { name: 'hall front', axis: 'x', line: 5, at: [8, 10, 16, 18], y: [2, 4], out: 'north', dress: null },
    { name: 'gallery front', axis: 'x', line: 5, at: [9, 17], y: [6, 8], out: 'north', dress: null }, // behind the rawshans
    { name: 'hall back', axis: 'x', line: 13, at: [12, 15, 17], y: [2, 4], out: 'south', dress: 'sill' },
    { name: 'gallery back', axis: 'x', line: 13, at: [13, 17], y: [7, 8], out: 'south', dress: 'lattice' },
    { name: 'hall east', axis: 'z', line: 19, at: [11], y: [2, 4], out: 'east', dress: null },
    { name: 'hall east front', axis: 'z', line: 19, at: [7], y: [2, 4], out: 'east', dress: null, until: 2 }, // the ballroom doors at III
    { name: 'gallery east', axis: 'z', line: 19, at: [8, 10], y: [7, 8], out: 'east', dress: null },
    { name: 'study front', axis: 'x', line: 1, at: [13], y: [6, 8], out: 'north', dress: null }, // behind the oriel
    { name: 'study west', axis: 'z', line: 11, at: [3], y: [7, 8], out: 'west', dress: null },
    { name: 'study east', axis: 'z', line: 15, at: [3], y: [7, 8], out: 'east', dress: null },
  ],
  3: [
    { name: 'ballroom east 1', axis: 'z', line: 27, at: [4], y: [2, 8], out: 'east', dress: null, arch: true },
    { name: 'ballroom east 2', axis: 'z', line: 27, at: [8], y: [2, 8], out: 'east', dress: null, arch: true },
    { name: 'ballroom east 3', axis: 'z', line: 27, at: [12], y: [2, 8], out: 'east', dress: null, arch: true },
    { name: 'ballroom back west', axis: 'x', line: 15, at: [21], y: [2, 4], out: 'south', dress: 'sill' },
    { name: 'ballroom back east', axis: 'x', line: 15, at: [25], y: [2, 4], out: 'south', dress: 'sill' },
    { name: 'gallery back', axis: 'x', line: 15, at: [22, 24], y: [6, 8], out: 'south', dress: 'lattice' },
    { name: 'ballroom front side', axis: 'z', line: 19, at: [3], y: [2, 4], out: 'west', dress: 'sill' },
  ],
}
// the great window of the ballroom's front (z 1): glass x 21..25 from y 2, a mullion at x 23 to y 8, a pointed head
export const GREAT_WINDOW = { line: 1, x0: 21, x1: 25, y0: 2, spring: 9, mullion: 23 }

// The rawshans and the oriel: lattice fronts out on corbels (z 0 the wing's, z 4 the hall's, the study's on the iwan)
export const RAWSHANS = [
  { x0: 2, x1: 6, z: 0, y0: 5, y1: 10, posts: [2, 4, 6], corbels: null },
  { x0: 8, x1: 10, z: 4, y0: 5, y1: 10, posts: [8, 10], corbels: [9] },
  { x0: 16, x1: 18, z: 4, y0: 5, y1: 10, posts: [16, 18], corbels: [17] },
]
export const ORIEL = { x0: 12, x1: 14, z: 0, y0: 5, y1: 9 }
export const BAY = { x0: 2, x1: 6, z: 0, glass: [3, 5], y: [1, 3], lintel: 4 } // the parlour's bay

// Chains where this look's ceilings differ from the shipped roofs: the ballroom's middle chandelier reaches the
// lantern's ceiling (16); the others hang as shipped.
export const CHAINS = { 3: { '23,6,9': 9 } }

// The fires: the hall's in its back wall under the stack, the kitchen's in the west wall under its stack
export const FIRES = [[10, 1, 13, 'north'], [1, 1, 11, 'east']]

export const OUTSIDE = {
  rainButt: [13, 1, 14], // the kept barrel, on the back terrace by the garden door
  doorLamps: [[12, 3, 0], [14, 3, 0]], // hung under the oriel's corbels, either side of the door
  pots: { 1: [[8, 1, 1], [10, 1, 1], [16, 1, 1], [18, 1, 1], [8, 1, 15], [18, 1, 15]] },
  // II: the paradise garden
  beds: [[8, 16, 10, 18], [16, 16, 18, 18], [8, 20, 10, 23], [16, 20, 18, 23]],
  palms: [[8, 23], [18, 23]], // in the back beds' far corners, their crowns over the walls (crowns over the walks
  // would roof the terrace, and a crown short of a wall leaves a pit of sky between them)
  shrubs: [[9, 17], [17, 17]], // flowering azalea in the front beds' middles
  flowers: ['orange_tulip', 'red_tulip', 'allium', 'orange_tulip'],
  pool: { x0: 11, z0: 17, x1: 15, z1: 21, cx: 13, cz: 19 },
  bench: { x0: 12, x1: 14, z: 23 },
  // III: the east garden behind the ballroom: a long pool on the walk from its door, two palm beds, the gate west
  eastBeds: [[20, 20, 20, 23], [26, 20, 26, 23]],
  eastPalms: [[20, 21], [26, 21]],
  eastPool: { x0: 22, x1: 24, z0: 20, z1: 22 },
  gate: { x: 19, z: 19 },
}
