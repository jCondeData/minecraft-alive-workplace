// Terrace, the DESERT look (looks/desert/terrace[_2], ROADMAP 49.10c, as 49.10a): the same building as the shipped
// Terrace (../terrace/plan.mjs: its homes on the same party walls, every bed, door, kept container, ladder and every
// furniture spot in the same cell at both levels), drawn again for a hot, dry town. It speaks the desert village's
// language: coursed sandstone below, mud brick above, a cut-sandstone course at the bedroom floor, a flat roof behind a
// smooth coping, beam ends under it, lattice for shade, and a windcatcher over every household.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The arcaded row": one long two-storey block behind a street arcade of round arches, one flat roof. Story: a
//     caravan town's lodging row. Shady and calm, but the plot is 10 deep with the walls on z 1..8: an arcade needs two
//     rows in front of the doors, and a long arcade hides the homes (one building, not several households).
//  2. "The courtyard pair": each home a closed cube round a light well, blank walls to the street. The truest desert
//     plan, but the brief pins every room in its cell: the bedroom fills the whole upper floor, so there is no well to
//     open, and a blank street wall reads as a store, not homes.
//  3. "The Windcatcher Row" (Yazd, Siwa, Jeddah): narrow two-storey houses shoulder to shoulder, each framed by proud
//     sandstone pilasters on its party walls, its bedroom behind a dark lattice RAWSHAN out over the street on
//     corbels (which shades the door below it), its own flat roof terrace behind the coping, and its own slotted
//     WINDCATCHER rising from the back of its roof. Story: every household that settles here builds the same house
//     against the last one: a lattice to sit behind, a tower to pull the wind down into the sleeping room.
//  Built: 3. It is the strongest: the outline is a saw of towers, one per household, so the row says "several homes"
//  from 20 blocks the way the shipped Terrace's gables do, but in the desert's own shapes (flat roofs, slots, lattice,
//  no pitched roof anywhere); the rawshans give the front its depth and its shade; II is one more house, one more tower.
//
// --- Design notes: what is done, what is left (2026-11-19) -----------------------------------------------------------
//  Done: Levels I and II as candidates (build.sh -> build/rebuild/terrace_desert/). Every home on the shipped party walls
//  (x 1, 6, 11, 16), walls z 1..8, the bedroom floor at y 5, bed heads at y 6; the shipped furniture list drawn as is
//  (the same spots, kinds and facings); the bedroom lamp hangs straight from the deck (chain 0 where the shipped one
//  hangs 2 links from its ridge).
//  Checked: look_spots.py terrace_desert terrace: SAME BUILDING (24 and 36 shipped spots, none missing, no bed, container,
//  door or ladder moved; only the bedroom lamps' chains differ); rebuild.py no CHECK line at either level; holes.py 0
//  (with open) at both; every id vanilla 1.21.1; II keeps 99 % of I.
//  Renders (front, back, left, right, top per level): build/rebuild/terrace_desert/renders/.
//  Tried and changed: a lantern hung under each rawshan's sill dropped (a top slab holds no hung lamp): it stands on
//  the front-room window's sill by the door instead, beside a potted cactus.
//  Open for the critic: no palm on the plot (13 x 10 with the walls on z 1..8 leaves one row front and back, and a
//  crown there reaches into the bedrooms or the windcatchers); the roof terraces are not reachable (decks, not rooms:
//  nothing stands on them).
//
// --- Named parts (template coordinates: x across the front, west 0 -> east; z from the front, z = 0, to the back) ---
//   Houses      every home u 0..5 (x = L + u), walls z 1..8: coursed sandstone y 1..4, a cut-sandstone course y 5, mud
//               brick y 6..9, the deck y 10.
//   Pilasters   smooth sandstone, a block proud of the front (z 0) and back (z 9) on every wall line, y 0..11, the
//               coping slab on top at y 12.
//   Rawshan     each home's bedroom front, u 1..4 on z 0, y 5..9: a sill course on corbels at y 4, dark-oak posts at
//               u 1 and u 4, lattice before the windows (u 2..3), a hood at y 9.
//   Parapet     y 11 in mud brick on the roof's rim and every party wall, a smooth sandstone coping at y 12.
//   Windcatcher each home's own, u 1..3 x z 6..8 (flush with the back wall), y 11..15, slots y 13..14, cap y 15.
//   Beam ends   spruce under the deck (y 9) on the back and the end walls.
import {
  HOMES as T_HOMES, at, FURNITURE as T_FURNITURE, OPENINGS as T_OPENINGS, OUTSIDE as T_OUTSIDE,
} from '../terrace/plan.mjs'

export const LEVELS = [1, 2]
export const IDS = { 1: 'terrace', 2: 'terrace_2' }
export const SIZES = { 1: [13, 16, 10], 2: [18, 16, 10] } // as shipped

// Palette 60/30/10: coursed sandstone ground storey and mud brick above (60) / smooth and cut sandstone dressings:
// pilasters, the course at the bedroom floor, the coping, sills, the windcatchers' corners and caps (30) / dark-oak
// lattice, spruce beam ends and doors, pots (10). Furniture keeps the shipped Terrace's spruce.
export const PALETTE = {
  ashlar: [['sandstone', 6], ['cut_sandstone', 3], ['smooth_sandstone', 1]],
  mud: [['mud_bricks', 7], ['packed_mud', 2]],
  plinth: 'smooth_sandstone', pilaster: 'smooth_sandstone', course: 'cut_sandstone', carved: 'chiseled_sandstone',
  coping: 'smooth_sandstone_slab', deck: 'smooth_sandstone', parapet: 'mud_bricks',
  floor: 'smooth_sandstone', upperFloor: 'spruce_planks', joist: 'stripped_spruce_log',
  sill: 'smooth_sandstone_slab', step: 'sandstone_stairs', stoop: 'smooth_sandstone',
  lattice: 'dark_oak_trapdoor', latticePost: 'dark_oak_log', latticeSill: 'dark_oak_slab', latticeRoof: 'dark_oak_stairs',
  corbel: 'smooth_sandstone_stairs', beam: 'spruce_fence', door: 'spruce', glass: 'glass_pane',
  catcher: { body: [['mud_bricks', 5], ['packed_mud', 1]], corner: 'smooth_sandstone', cap: 'smooth_sandstone', capTop: 'smooth_sandstone_slab' },
}

export const HOUSE = {
  walls: { z0: 1, z1: 8 }, upperFloor: 5, course: 5, deckY: 10, parapetY: 11,
  pilasterZ: [0, 9],
  rawshan: { u0: 1, u1: 4, y0: 5, y1: 9, posts: [1, 4] },
  catcher: { u0: 1, z0: 6, y0: 11, y1: 15 },
  beams: { y: 9, back: [2, 4], ends: [2, 4, 6] }, // back: the u of each home's beam ends; ends: the z on the end walls
}

export const HOMES = T_HOMES
export { at }

// The shipped openings that hold the homes together (the doors and the ladders), cell for cell; the windows are this
// look's own: small and deep under the lattice, sills on the ground storey.
export const OPENINGS = {
  door: T_OPENINGS.door, ladder: T_OPENINGS.ladder,
  windows: [
    { name: 'front room', us: [1, 2], ys: [2, 3], z: 1, out: 'north', dress: 'sill' },
    { name: 'bedroom front', us: [2, 3], ys: [6, 7], z: 1, out: 'north', dress: null }, // behind the rawshan's lattice
    { name: 'kitchen back', us: ['sink'], ys: [2, 3], z: 8, out: 'south', dress: 'sill' },
    { name: 'bedroom back', us: [4], ys: [7, 8], z: 8, out: 'south', dress: null }, // beside the windcatcher's foot
  ],
  endWindows: [{ z: 4, ys: [2, 3], dress: 'sill' }, { z: 4, ys: [6, 7], dress: null }],
}

// Every furniture piece: the shipped Terrace's, the same parts in the same cells.
export const FURNITURE = T_FURNITURE
export const OUTSIDE = {
  rainBarrel: T_OUTSIDE.rainBarrel, // the kept barrel (0, 0, 1): a water jar's barrel at the row's west end
  pots: { 'front room': ['potted_cactus'], 'kitchen back': ['potted_cactus'] },
  jars: [[12, 0, 1]], // decorated pots at the row's east end in I (the third home's in II takes the next)
  doorLamp: { u: 2, y: 2, z: 0 }, // standing on the front-room window's sill, by the door (a top slab holds no hung lamp)
}
