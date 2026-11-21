// Weaver's Cottage, the SAVANNA look (looks/savanna/weavers_cottage[_2], ROADMAP 49.10d, as 49.10a): the same building
// as the shipped Weaver's Cottage (../weavers_cottage/plan.mjs: the workroom with its loom and chest, the loft over it
// with its chest, the doors, the flight, the fold on the east side with its fence and gate, Level II's dye garden behind,
// and every furniture spot in the same cell at both levels), drawn again for a hot grassland with a rainy season. It
// speaks the savanna village's language the Cottage, the Stone House, the Terrace and the Manor set (golden thatch hips
// with eaves on every side and no gable anywhere, grey acacia posts and beams, packed mud over a red granite storey on a
// terracotta plinth, painted trims, a veranda for shade), and it is the village's HERDER-WEAVER's house: the flock in a
// kraal at its side under a little thatched shelter of its own.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "Loom hut and rondavels": the workroom and the loft as two round mud houses under conical thatch, the kraal a
//     ring of thorn between them. The strongest savanna picture, but the shipped rooms are square and pinned (a loom at
//     (2, 1, 3), a chest at (6, 1, 3), the loft's chest and its tables against straight walls): round huts can't hold
//     them, so it would not be the same building.
//  2. "Thatch on a granary": the loft as a raised mud granary under a tall cone, the workroom a lean-to beside it. A
//     striking outline, but the loft must sit square over the whole workroom (the flight, its chest, two lamps), so the
//     cone becomes a pyramid on a box, and a lean-to over the loom's long window is the dark side of the house.
//  3. "The Weaver's Veranda and the Kraal": ONE golden thatch hip over the whole two-storey house, eaves a block out on
//     every side and no gable; a granite ground storey, a grey acacia beam ring at the loft floor, packed mud above with
//     a painted band of red and white under the eave and acacia shutters; a low thatch VERANDA on acacia posts along the
//     street, shading the weaver's long window and the door, the loom working in its light. The flock lives in a KRAAL
//     of acacia rails with its corners turned (a ring made square), and in it a little SHELTER on four posts under its own
//     pyramid of thatch with a clay pot on its point, a second, smaller roof of the same kind beside the house. Level
//     II walls in a dye garden behind: mud walls with a thatched coping, rows of dye flowers by colour, a drying frame
//     hung with the cloth, and a young acacia. Story: a herder who weaves: the flock at his side, the loom on his
//     veranda, the cloth drying in his garden under the acacia.
//  Built: 3. It is the strongest: from 20 blocks it is a big golden hip with a small golden pyramid beside it (a
//  silhouette you can name, and nothing like the desert look's flat roof and tower); the veranda gives the shade the
//  climate asks for and the long window its light; the kraal's turned corners and its shelter say "herder" before a door
//  is seen. Not the safest: two hips side by side must not touch, and the veranda's eave runs under the hip's.
//
// --- Design notes: what is done, what is left (2026-11-19) -----------------------------------------------------------
//  Done: Levels I and II as candidates (build.sh -> build/rebuild/weavers_cottage_savanna/), never live. The house on the
//  shipped walls (x 1..11, z 2..8), the loft floor at y 4; the shipped furniture list drawn as is (the same spots, kinds,
//  facings and the lamps' chains: the hip's ridge course is at y 11, so the loft lamps hang 2 links as shipped), the loom
//  (2, 1, 3) and chest (6, 1, 3) in the kept cells, the loft's chest (10, 5, 4), the three spruce doors and the flight
//  in their cells. The shipped hearth (a fixture, no spot) is left out: the herder cooks outside.
//  The kraal: 22 acacia rail blocks and its gate; I has no flower, II 44 flower blocks (the code wants II >= I + 8).
//  Checked: look_spots.py weavers_cottage_savanna weavers_cottage: SAME BUILDING (14 and 14 shipped spots, none missing,
//  no bed, container, job block or door moved, no lamp on another chain); rebuild.py no CHECK line at either level;
//  holes.py 0 (with open) at both; every id vanilla 1.21.1; II keeps 100 % of I; one loom each level; about 40 and 60
//  stacks (limit 81).
//  Renders (front, back, left, right, top per level, a low front of I, cutaways at y 3 and 7 per level, top and front):
//  build/rebuild/weavers_cottage_savanna/renders/ (savanna_<L>_<view>.png, savanna_<L>_cut<y>_<view>.png).
//  Tried and changed: the kraal's corners first turned in steps (check.py's owner rule wants a post at every corner: a
//  plain rectangle now); the shelter stood a block from the house and its open roof space reached the house's eave
//  strip (holes.py open: moved east, its eave over the kraal's rail, boarded under its point); the village's big acacia
//  in the garden's walled corner, over the drying frame, was a roofed room to holes.py (a young acacia with one flat
//  crown in the east bed now); two clay pots on the ridge read as chimneys (one, in the middle); the painted band
//  under the eave hides in its shadow from the street, so the loft's front windows got white painted jambs; the drying
//  frame's cloth faced only the garden (now both sides, the street side over the back wall's coping).
//  Open for the critic: the bamboo-mosaic thatch is the village's (saturated beside the earth walls, as the Terrace's
//  critic noted; kept so the house belongs to the same village); the drying frame's rail is a long acacia log on
//  four posts; the shelter's posts stand a block in from its eave (the eave on all four sides).
//// --- Named parts (template coordinates: x across the front, west 0 -> east 17; z from the front, z = 0, to the back) --
//   House       walls x 1..11, z 2..8: red granite y 1..3 on a terracotta plinth, a grey acacia beam ring y 4 (the loft
//               floor), packed mud y 5..7 with a painted band (red and white) at y 7 under the eave; acacia corner posts
//               y 1..7; white jambs painted beside the loft's front windows; acacia shutters at every side and back window.
//   Hip         the great thatch x 0..12, z 1..9, eaves at y 7, ridge y 11 along x (x 4..8), a clay pot on its middle.
//   Veranda     x 1..11, z 0..1: acacia posts at x 1, 4, 7, 11 on z 0, a thatch eave at y 4 back to the wall; lanterns in
//               the window's bay and over the door, clay jars on its floor.
//   Kraal       x 11..17, z 2..11: acacia rails on a terracotta kerb, the gate on the street; grass worn bare at the gate,
//               the fold door and under the shelter.
//   Shelter     in the kraal's east half, x 13..17, z 6..10: four acacia posts, a thatch pyramid (eaves y 4, point y 6)
//               with a clay pot on it, boarded under its point (a loft for fodder); hay by the gate.
//   Garden (II) x 0..11, z 8..16: mud walls with a thatched coping, the back door's walk, rows of dye flowers in dirt by
//               colour, the drying frame (z 15) hung with cloth, a young acacia in the middle of the east beds, dye jars.
import { FURNITURE as W_FURNITURE, OPENINGS as W_OPENINGS } from '../weavers_cottage/plan.mjs'

export const SIZE = { 1: [18, 14, 12], 2: [18, 14, 17] } // as shipped

// Palette 60/30/10: red granite and packed mud walls (60) / golden thatch, grey acacia posts and beams, a terracotta
// plinth and kerb (30) / the painted band, the shutters, the shipped spruce doors and furniture, the cloth (10).
export const PALETTE = {
  granite: [['granite', 6], ['polished_granite', 1], ['terracotta', 1]],
  mud: [['packed_mud', 6], ['mud_bricks', 1]],
  plinth: [['terracotta', 5], ['red_terracotta', 2], ['brown_terracotta', 1]],
  frame: 'acacia_log', roof: { full: 'bamboo_mosaic', stairs: 'bamboo_mosaic_stairs', slab: 'bamboo_mosaic_slab' },
  floor: 'mud_bricks', loftFloor: 'spruce_planks', under: 'spruce_planks', deck: 'packed_mud',
  paint: ['red_terracotta', 'white_terracotta'], jamb: 'white_terracotta', shutter: 'acacia', door: 'spruce', wood: 'spruce', glass: 'glass_pane',
  fence: 'acacia', kerb: 'terracotta', finial: 'decorated_pot',
  gardenWall: 'mud_bricks', soil: 'dirt', path: 'coarse_dirt',
  tree: { soil: 'grass_block', log: 'acacia_log', leaves: 'acacia_leaves' }, // a young acacia: one flat crown
}

export const HOUSE = { x0: 1, x1: 11, z0: 2, z1: 8, beamY: 4, loftY: 4, wallTop: 7, paintY: 7, jambs: [4, 7, 8, 10] } // jambs: the x of the
// painted cells (y 5..6) beside the loft's front windows
export const HIP = { x0: 0, z0: 1, x1: 12, z1: 9, eaveY: 7, finials: [[6, 12, 5]] }
export const VERANDA = { x0: 1, x1: 11, zEave: 0, zWall: 1, y: 4, posts: [1, 4, 7, 11], lamps: [[5, 3, 1], [9, 3, 1]], jars: [[2, 1, 1], [10, 1, 1]] }

// The shipped openings that hold the building together, cell for cell: the doors and the flight to the loft (with its
// well). The windows are this look's own: the weaver's window behind the veranda's first two bays, shuttered lights
// elsewhere.
export const OPENINGS = {
  frontDoor: W_OPENINGS.frontDoor, foldDoor: W_OPENINGS.foldDoor, backDoor: W_OPENINGS.backDoor,
  stairs: W_OPENINGS.stairs,
  weaverWindow: { z: 2, xs: [2, 3, 5, 6], ys: [2, 3] },
  windows: [ // [cells], out, dress
    { name: 'loft front west', cells: [[2, 5, 2], [3, 5, 2], [2, 6, 2], [3, 6, 2]], out: 'north', dress: null },
    { name: 'loft front east', cells: [[5, 5, 2], [6, 5, 2], [5, 6, 2], [6, 6, 2]], out: 'north', dress: null },
    { name: 'loft over the door', cells: [[9, 5, 2], [9, 6, 2]], out: 'north', dress: null },
    { name: 'workroom west', cells: [[1, 2, 5], [1, 3, 5]], out: 'west', dress: 'shutters' },
    { name: 'loft west', cells: [[1, 5, 5], [1, 6, 5]], out: 'west', dress: 'shutters' },
    { name: 'workroom east', cells: [[11, 2, 3], [11, 3, 3]], out: 'east', dress: null }, // over the kraal's rail
    { name: 'loft east', cells: [[11, 5, 6], [11, 6, 6]], out: 'east', dress: 'shutters' },
    { name: 'workroom back west', cells: [[2, 2, 8], [2, 3, 8]], out: 'south', dress: 'shutters' },
    { name: 'workroom back east', cells: [[8, 2, 8], [8, 3, 8]], out: 'south', dress: 'shutters' },
    { name: 'loft back west', cells: [[3, 5, 8], [3, 6, 8]], out: 'south', dress: 'shutters' },
    { name: 'loft back east', cells: [[7, 5, 8], [7, 6, 8]], out: 'south', dress: 'shutters' },
  ],
}

// Every furniture piece: the shipped cottage's, the same parts in the same cells and the lamps on the shipped chains
// (the loft's two reach the thatch's ridge course at y 11), except the hearth (a fixture, not a spot: the herder cooks
// outside; its corner holds fleeces).
export const FURNITURE = W_FURNITURE.filter(f => f.part !== 'hearth')

// Fleeces (wool, not furniture): the flock's own colours, undyed in the workroom, a few dyed bolts in the loft.
export const FLEECES = [
  [2, 1, 6, 'white_wool'], [2, 1, 7, 'brown_wool'], [3, 1, 7, 'white_wool'], [2, 2, 7, 'light_gray_wool'],
  [7, 1, 3, 'brown_wool'], // the back of his chest (the kept chest faces west)
  [2, 5, 4, 'white_wool'], [2, 5, 5, 'brown_wool'], [2, 6, 4, 'white_wool'], [2, 5, 6, 'light_gray_wool'],
  [2, 5, 7, 'red_wool'], [3, 5, 7, 'yellow_wool'], [2, 6, 7, 'white_wool'], [4, 5, 7, 'orange_wool'],
]

// The walking lines: street -> the veranda -> the door -> workroom -> the weaver's seat at the loom; -> the fold door
// and into the kraal by the shelter; -> the flight -> the loft and the wool chest. II: -> the back door -> the garden.
export const WALK = [
  [1, 0, -2], [9, 0, -1], [9, 0, 0], [9, 1, 1], [9, 1, 2], [9, 1, 4], [3, 1, 4], [3, 1, 3],
  [10, 1, 5], [10, 1, 6], [11, 1, 6], [12, 1, 6], [12, 1, 8], [14, 1, 7],
  [10, 1, 7], [9, 2, 7], [8, 3, 7], [7, 4, 7], [7, 5, 6], [10, 5, 5],
  [4, 1, 7], [4, 1, 8], [4, 0, 9], [4, 1, 13],
]

export const KRAAL = {
  rail: [[12, 1, 2], [17, 1, 2], [17, 1, 11], [11, 1, 11], [11, 1, 9]],
  gate: { at: [14, 1, 2], facing: 'north' },
  ground: { x0: 12, x1: 16, z0: 3, z1: 10 },
  worn: [[14, 3], [14, 4], [13, 4], [15, 4], [12, 6], [12, 7], [13, 6], [12, 5], [14, 8], [15, 7], [15, 9], [16, 8], [15, 8]],
  shelter: { x0: 13, z0: 6, x1: 17, z1: 10, eaveY: 4, posts: [[14, 7], [16, 7], [14, 9], [16, 9]], finial: [15, 7, 8] }, // its
  // eave over the kraal's east rail, a lane (x 12) left open under the house's eave to the fold door
  hay: [[12, 1, 3], [12, 1, 4], [12, 2, 3]], // against the house by the gate (the shelter's floor kept clear for the flock)
  lamp: [17, 2, 2], // on the corner post by the gate
}

// Level II: the dye garden behind the house.
export const GARDEN = {
  wall: { x0: 0, x1: 11, z0: 8, z1: 16, eastFrom: 12 }, // mud y 1 with a thatched coping y 2 on x0, z1, and x1 (z >= eastFrom)
  walk: { x: 4, z0: 9, z1: 13 },
  beds: { west: [1, 3], east: [5, 10] },
  rows: {
    10: [['poppy'], ['rose_bush', 'poppy']], // red
    11: [['orange_tulip', 'dandelion'], ['sunflower', 'torchflower']], // orange and yellow
    12: [['cornflower'], ['blue_orchid', 'cornflower']], // blue
    13: [['allium'], ['lilac', 'allium']], // purple
  },
  path: { z: 14, x0: 1, x1: 10 },
  tenter: { z: 15, x0: 1, x1: 10, posts: [1, 4, 7, 10], y: 3, cloth: ['red', 'orange', 'yellow', 'blue', 'light_blue', 'magenta'] }, // hung both sides: the
  // street-side cloth over the back wall's coping
  tree: [7, 0, 12], // in the east bed, its crown over the middle of the garden
  jars: [[9, 1, 14], [10, 1, 14]],
}
