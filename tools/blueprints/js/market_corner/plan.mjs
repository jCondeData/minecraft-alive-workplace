// Market Corner, the plan: drawn before any block. The story, three concepts, the palette, the masses, the openings,
// every piece of goods and furniture with its footprint, and the walking line; recipe.mjs assembles it in passes.
//
// Three concepts (massing, roof, plan; one line of story each):
//   A. The Awning Corner. An L of counters along the two back edges, one striped canvas stepping down from a tall
//      corner mast over both arms, mitred into a chevron at the hip, a banner valance along its edge; an open paved
//      court in front is the browsing floor. Story: a family of traders who claim a street corner every market day
//      and pitch the same red-and-white canvas on the same mast, so the village knows the corner by its stripes.
//   B. The Handcart. A trader's two-wheeled cart and a heap of crates under one parasol on a pole, a 4 x 4 footprint.
//      Story: a peddler passing through. Small and charming close up, but nothing to read from across the square, and
//      a cart says "leaving", not "lived in".
//   C. The Lean-to. A plank shed against a back wall under a shingled pentice, crates stacked under the eaves.
//      Story: a storekeeper's overflow. Safe, but it reads as a tiny house: a roof and a wall, not a market.
//   Strongest: A. It is the only one whose shape says "corner": it turns its back on two streets and opens its arms to
//   the square, so it fits a street corner exactly and works with the player's walls and roads going round it. The
//   striped canvas is colour no other village build carries, the chevron at the hip and the mast give it a silhouette
//   from far off, and the court gives a player (and a villager) a place to step in among the goods. B is cuter and
//   C is safer; A is the one a village would be remembered by.
//
// Coordinates: x across the front (west 0 -> east 7), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back (z = 7). The corner it turns its back on is the south-east one, (7, 7): the build sits in a
// street corner with streets past its back (south) and east sides, and opens to the front and the west.
//
// Masses (named parts the owner's feedback can point at):
//   Mast      the corner post at (7, 7): a dark oak log from the paving up through the canvas, a lantern on its top.
//   Screen    the back walls on the two street sides (z = 7 and x = 7), y 1..4: a cobble footing course, spruce
//             boards between dark oak posts, a dark oak top plate the canvas's top row rests on. They hide the traders' backs from the street.
//   Counters  an L of spruce counters one block in front of the screens (z = 6, x = 6), the goods on top of them.
//   Canvas    the awning: three rows stepping down from y 5 at the screens to y 3 at its edge (depth 0..2 from the
//             nearer back edge), stripes across each arm meeting in a chevron along the diagonal; held at its two outer
//             ends by spruce poles. Under its edge row (depth 2) is the aisle where buyers stand, 2 blocks clear.
//   Rail      a pale stripped-spruce rail along both arms under the canvas's middle row, the frame the cloth is
//             lashed to; two lanterns hang from it just over the goods.
//   Valance   red and white banners hung on the canvas's front edge, alternating, the scalloped hem of the awning.
//   Court     the paved square in front (depth 3 and more), open on the front and west, with a heap of goods by the
//             west pole and a stepped crate pile in the front-west corner.

export const SIZE = [8, 8, 8] // w, h, d (limit 9 x 8 x 9; smaller is better for a corner piece)
export const BACK = { x: 7, z: 7 } // the corner the build turns its back on

// Palette (60/30/10): grey cobbled paving and spruce joinery (base: what a market square is made of) / the red-and-
// white striped canvas and banners (the colour that names the place) / the goods: pumpkin orange, melon green, hay
// gold, cloth colours, lantern light (the accent, all on the counters). Why: grey and brown keep it a quiet street
// piece so the canvas sings; red and white is the oldest market awning there is; the produce does the rest.
export const PALETTE = {
  paving: [['cobblestone', 5], ['stone', 2], ['andesite', 2], ['mossy_cobblestone', 1]],
  kerb: 'stone_bricks', // a dressed edge along the open front and west sides: the court reads as a laid square
  footing: [['cobblestone', 3], ['mossy_cobblestone', 1]], // the screens' bottom course, out of the splash
  deck: 'spruce_planks', // the trading floor under the counters (depth 0..1)
  frame: 'dark_oak_log', // the mast and the screens' posts
  boards: 'spruce_planks', // the screens' panels and the counters
  rail: 'stripped_spruce_log', // the canvas's frame rail over the counters, the wares and lamps hung from it
  wood: 'spruce', // poles (fences), crate lids (trapdoors)
  stripes: ['red_wool', 'white_wool'],
  valance: ['red_wall_banner', 'white_wall_banner'],
}

export const MASSES = {
  mast: { x: 7, z: 7, top: 6 }, // log y 1..6, the lantern at y 7
  screen: { y0: 1, y1: 4, footing: 1, plate: 4, posts: [[0, 7], [3, 7], [7, 3], [7, 0]] }, // the screens' posts (the mast is the corner one)
  counters: { depth: 1 }, // counters at depth 1 from the back edges: z = 6 (x 0..6) and x = 6 (z 0..5)
  rail: { y: 3 }, // the frame rail at depth 1 under the canvas's middle row: it carries the canvas, closes the gap
                  // between the goods and the cloth, and the lamps hang from it over the counters
  canvas: { top: 5, reach: 3 }, // y 5 at depth 0, y 4 at depth 1, y 3 at depth 2
  poles: [[0, 5], [5, 0]], // fence poles under the canvas's two outer front corners, y 1..2
}

// No rooms: one open stall. The "room" is the aisle and the court.
export const ROOMS = [
  { name: 'Stall', use: 'the counters, the aisle under the canvas and the court in front', box: [0, 1, 0, 6, 2, 6] },
]

// No door (a decoration's lane ends one block in front of the middle of the z = 0 edge, Paths.doorstep's fallback).
export const OPENINGS = {
  frontLane: { at: [3, 0, 0], note: 'the court is open on the whole front and west: x 1..4 at z = 0 are bare paving' },
  aisleHeadroom: 2, // under the canvas's edge row (y 3): y 1..2 clear
}

// The goods, cell by cell: [x, z, counter block (y 1), goods (y 2)]. Back arm (z = 6) west to east: the greengrocer
// (hay, melons, pumpkins), the baker (a cake), the potter (decorated pots); the corner; then the east arm (x = 6) back
// to front: the florist (potted flowers) and the draper (folded cloth). Nothing grows, nothing falls, nothing is read.
export const GOODS = [
  [0, 6, 'hay_block', 'hay_block'],
  [1, 6, 'spruce_planks', 'melon'],
  [2, 6, 'spruce_planks', 'pumpkin'],
  [3, 6, 'spruce_planks', null], // a lantern hangs here
  [4, 6, 'spruce_planks', 'cake'],
  [5, 6, 'spruce_planks', 'decorated_pot'],
  [6, 6, 'spruce_planks', 'decorated_pot'],
  [6, 5, 'spruce_planks', 'potted_red_tulip'],
  [6, 4, 'spruce_planks', 'potted_cornflower'],
  [6, 3, 'spruce_planks', 'potted_oxeye_daisy'],
  [6, 2, 'spruce_planks', null], // a lantern hangs here
  [6, 1, 'spruce_planks', 'light_blue_carpet'],
  [6, 0, 'spruce_planks', 'lime_carpet'],
]

// Furniture spots: none (no seats, tables or beds; the brief asks for none). Lamps are fixtures: hung, never standing
// on a floor (the owner's rule 2).
export const FURNITURE = []
export const LAMPS = [
  { at: [3, 2, 6], hung: true }, // from the rail, over the greengrocer's counter
  { at: [6, 2, 2], hung: true }, // from the rail, over the draper's counter
  { at: [5, 2, 5], hung: true }, // in the hip's inside corner, under the chevron
  { at: [7, 7, 7], on: 'mast' }, // on the mast's top, seen from the streets
]

// Outside the stall: a heap of produce at the west pole and a stepped pile of crates in the front-west corner, both off
// the walking line (the front middle, x 2..5, stays open). A crate is spruce planks with a closed trapdoor lid on top (not a barrel: a barrel is a POI).
export const OUTSIDE = {
  heap: [[0, 1, 4, 'hay_block'], [1, 1, 4, 'melon'], [0, 1, 3, 'hay_block'], [0, 2, 3, 'pumpkin'], [4, 1, 0, 'pumpkin']],
  crates: [[0, 1, 0, 'spruce_planks'], [0, 2, 0, 'spruce_planks'], [0, 3, 0, 'lid'], [1, 1, 0, 'spruce_planks'], [1, 2, 0, 'lid'], [0, 1, 1, 'spruce_planks'], [0, 2, 1, 'lid']],
}

// The walking line: in from the lane at the front middle, across the court, under the canvas to each arm's counter.
export const WALK = [
  [3, 1, -1], [3, 1, 0], [3, 1, 1], [3, 1, 2], [3, 1, 3], [3, 1, 4], [3, 1, 5], // to the back arm (greengrocer, baker)
  [4, 1, 4], [5, 1, 4], [5, 1, 3], [5, 1, 2], // along the aisle to the east arm (florist, draper)
]
