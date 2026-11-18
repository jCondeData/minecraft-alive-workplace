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
// Round 2 changes (the critic's five notes, markets_51_7_critic.md):
//   1. Back and right were flat walls. The plot grows to 9 x 9 so the street sides get a layer: dark oak posts stand
//      proud on the plot's edge (x = 8, z = 8) one block out from the board infill, a cobble splash course of stairs
//      runs between them, and the canvas's back row now runs on past the walls as an eave over the posts. Each street
//      face gets a serving hatch with its shutters folded back against the boards (the traders sell to the street too)
//      and a red banner of the stall's colours in its other bay.
//   2. The front and west goods stood under open sky: they now sit on their own counters under a second, lower canvas
//      (an L mirrored on the front-west corner) carried on fence posts at its corners and ends. The main canvas is
//      thinner: two rows level on the wall, then one step down (y 5, 5, 4, 4), in place of a three-step wool wedge.
//   3. The lone log and lantern became the mast: the corner post itself runs up past the eave and flies a banner.
//   4. One paving (stone bricks, with a few mossy and cracked ones) over the whole plot and one base (cobblestone)
//      under every wall; the empty middle is gone (the second stall fills the front-west quarter, a 3 x 3 browsing
//      court is left between the two), and every melon, hay bale and pumpkin is on a counter, none on the ground.
//   5. Lanterns hang from the canvas over the aisle the buyers walk along, and one under the small canvas.
//
// Coordinates: x across the front (west 0 -> east 8), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back (z = 8). The corner it turns its back on is the south-east one, (8, 8): the build sits in a
// street corner with streets past its back (south) and east sides, and opens to the front and the west.
// d = the depth of a cell in from the nearer back edge: 0 the posts and eave, 1 the screens, 2 the counters, 3 the
// aisle, 4 the valance line; the browsing court is the rest.
//
// Masses (named parts the owner's feedback can point at):
//   Mast      the corner post at (8, 8): a dark oak log from the paving up past the eave to y 7, two banners flying from
//             its head (to the front and to the west, the ways buyers come from).
//   Posts     dark oak posts on the plot's edge (d 0) at x 0, 4 / z 0, 4, y 1..4, proud of the screens; a cobble stair
//             splash course between them; the eave on their heads.
//   Screens   the back walls on the two street sides (d 1), y 1..4: a cobble footing course, spruce boards, a dark oak
//             top plate; dark oak end and inside-corner posts. A serving hatch in each (x 2 / z 2), shutters open.
//   Counters  an L of spruce counters in front of the screens (d 2), the goods on top of them.
//   Canvas    the main awning: four rows (d 0..3) at y 5, 5, 4, 4, stripes across each arm meeting in a chevron along
//             the diagonal, held at its outer ends by fence poles at (0, 5) and (5, 0). The aisle (d 3) under its edge
//             row has 3 blocks clear; lanterns hang over it.
//   Valance   red and white banners hung on the canvas's front edge, alternating, the scalloped hem.
//   Booth     the small front-west stall: an L of counters on the front and west edges with a two-row canvas (y 4 at
//             the edge, y 3 inside) on fence posts at its corner, ends and inner ends.
//   Court     the stone-brick floor between the two stalls; the lane comes in at the front middle (x 4).

export const SIZE = [9, 8, 9] // w, h, d (limit 9 x 8 x 9)
export const BACK = { x: 8, z: 8 } // the corner the build turns its back on

// Palette (60/30/10): grey stone-brick paving, cobble footings and spruce joinery (base: what a market square is made
// of) / the red-and-white striped canvas and banners (the colour that names the place) / the goods: pumpkin orange,
// melon green, hay gold, cloth colours, lantern light (the accent, all on the counters).
export const PALETTE = {
  paving: [['stone_bricks', 8], ['mossy_stone_bricks', 1], ['cracked_stone_bricks', 1]],
  base: 'cobblestone', // every footing: the screens' bottom course and the splash course
  splash: 'cobblestone_stairs',
  frame: 'dark_oak_log', // the mast, the posts, the plates
  boards: 'spruce_planks', // the screens' panels and the counters
  wood: 'spruce', // poles (fences), shutters (trapdoors)
  stripes: ['red_wool', 'white_wool'],
  valance: ['red_wall_banner', 'white_wall_banner'],
  flag: 'red_wall_banner',
}

export const MASSES = {
  mast: { x: 8, z: 8, top: 7, flags: [[7, 7, 8, 'west'], [8, 7, 7, 'north']] }, // log y 1..7, banners flown from its top
  posts: { y1: 4, at: [[0, 8], [4, 8], [8, 4], [8, 0]] }, // proud posts on the plot's edge (d 0)
  screen: { y0: 1, y1: 4, footing: 1, plate: 4, posts: [[7, 7]] }, // the screens' inside corner post (their ends show
  // their boards behind the proud end posts, so the front and west read the layer too)
  hatches: [{ at: [2, 7], face: 'south' }, { at: [7, 2], face: 'east' }], // y 2..3 open, shutters on either side at d 0
  streetBanners: [[6, 4, 8, 'south'], [8, 4, 6, 'east']], // hung under the eave from the plate, in the hatchless bay
  counters: { depth: 2 },
  canvas: { rows: [5, 5, 4, 4] }, // d 0 (the eave), d 1 (on the plate), d 2 (over the counters), d 3 (over the aisle)
  poles: [[0, 5], [5, 0]], // fence poles under the canvas's two outer front corners, y 1..3
  booth: { rows: [4, 3], x1: 3, z1: 3, posts: [[0, 0, 3], [3, 0, 3], [0, 3, 3], [3, 1, 2], [1, 3, 2]] }, // [x, z, top]
}

// No rooms: one open market. The "room" is the aisle and the court.
export const ROOMS = [
  { name: 'Market', use: 'the counters, the aisle under the canvas, the booth and the court', box: [0, 1, 0, 6, 3, 6] },
]

// No door (a decoration's lane ends one block in front of the middle of the z = 0 edge, Paths.doorstep's fallback).
export const OPENINGS = {
  frontLane: { at: [4, 0, 0], note: 'x 4 at z = 0 is bare paving under the valance, the way into the court' },
  aisleHeadroom: 3, // under the canvas's edge row (y 4): y 1..3 clear, a hung lantern leaves 2
  hatches: 'one per street face, 1 x 2, into the back of a counter',
}

// The goods, cell by cell: [x, z, counter block (y 1), goods (y 2), and a second layer (y 3) where one is stacked]. Back arm (z = 6) west to east: the greengrocer
// (melons, pumpkins), the baker (a cake, behind the hatch), the potter (decorated pots), the florist (potted flowers);
// the corner; then the east arm (x = 6) back to front: the draper (folded cloth), the grocer again (behind the east
// hatch), the farmer (hay). The booth: produce. Nothing grows, nothing falls, nothing is read.
export const GOODS = [
  [0, 6, 'spruce_planks', 'melon'],
  [1, 6, 'spruce_planks', 'pumpkin'],
  [2, 6, 'spruce_planks', 'cake'],
  [3, 6, 'spruce_planks', 'decorated_pot'],
  [4, 6, 'spruce_planks', 'decorated_pot'],
  [5, 6, 'spruce_planks', 'potted_cornflower'],
  [6, 6, 'spruce_planks', 'hay_block', 'hay_block'], // the corner stack: two bales up to the canvas in the hip
  [6, 5, 'spruce_planks', 'potted_red_tulip'],
  [6, 4, 'spruce_planks', 'light_blue_carpet'],
  [6, 3, 'spruce_planks', 'lime_carpet'],
  [6, 2, 'spruce_planks', 'melon'],
  [6, 1, 'spruce_planks', 'pumpkin'],
  [6, 0, 'hay_block', 'hay_block'],
  // the booth
  [1, 0, 'spruce_planks', 'melon'],
  [2, 0, 'spruce_planks', 'pumpkin'],
  [0, 1, 'hay_block', 'hay_block'],
  [0, 2, 'spruce_planks', 'pumpkin'],
]

// Furniture spots: none (no seats, tables or beds; the brief asks for none). Lamps are fixtures: hung, never standing
// on a floor (the owner's rule 2).
export const FURNITURE = []
export const LAMPS = [
  { at: [2, 3, 5], hung: true }, // under the canvas's edge row, over the aisle by the back counters
  { at: [5, 3, 2], hung: true }, // the same by the east counters
  { at: [5, 3, 5], hung: true }, // the aisle's inside corner, under the chevron
  { at: [1, 2, 1], hung: true }, // under the booth's canvas, in its inside corner (off the walking line)
]

// The walking line: in from the lane at the front middle, up the court, into the aisle and along both arms.
export const WALK = [
  [4, 1, -1], [4, 1, 0], [4, 1, 1], [4, 1, 2], [4, 1, 3], [4, 1, 4], [4, 1, 5], // to the back arm
  [3, 1, 5], [2, 1, 5], [1, 1, 5], // along the back counters
  [5, 1, 5], [5, 1, 4], [5, 1, 3], [5, 1, 2], [5, 1, 1], // along the east counters
  [2, 1, 2], [1, 1, 2], [2, 1, 1], // to the booth's counters from the court
]
