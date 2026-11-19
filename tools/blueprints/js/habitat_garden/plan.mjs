// The Habitat Garden, the plan: drawn before any block (tools/blueprints/briefs/habitat_garden.md, REBUILD.md).
// recipe.mjs then assembles both levels from the parts library (../parts) in passes.
//
// Three rough concepts first (massing, roof, plan, one line of story each):
//   A. The Walled Meadow. A square dry-stone wall with a gate, a lawn inside, the pond in one corner, the tree in
//      another, the pasture by the gate under a little gabled shelter; Level II a hide in the far corner. Story: a
//      keeper fenced off a field for her Pokemon. Safe: every corner holds one thing, but it reads as a pen, a box
//      outline with nothing to say about why the habitat wakes in the middle of it.
//   B. The Terraced Wetland. Three grass terraces stepping down to a long pond at the front, the tree on the top
//      terrace, the hide (II) a tall tower at the end of the pond. Story: a keeper shaped a hillside into a marsh.
//      Strong silhouette, but terraces cost blocks and build time (30,000 ticks for Level I), the steps fight the open,
//      level ground the Habitat Block spawns on, and the water would have to be held up a storey off the ground.
//   C. The Stone Ring. An old ring of standing stones in a clearing, with a trilithon (two stones and a lintel) at its
//      back, is the garden's heart: the mossy centre stone sits in the middle of the ring, on the one axis that runs
//      from the hedged gate, over the centre stone, through the trilithon. Round the ring, asymmetric and lived in: a
//      reed-edged pond front-west with a standing stone at its edge, the keeper's bower (a tall lean-to with her chest
//      and potting bench, the pasture under its eave) on the east, and the old honey tree (the Saccharine) spreading
//      its crown over the back-east stone. Level II walks on through the trilithon to a second pond at the back and the
//      keeper's hide on stilts beside it, a hipped cabin with a balcony stepping out over the water's edge. Story:
//      wild Pokemon always gathered at the old ring; a keeper settled beside it, planted honey trees and dug ponds, and
//      one day the ring's heart stone will wake as the village's Habitat.
// Strongest: C. It gives the centre stone a reason to be where it is (the brief's one fixed purpose: the Habitat
// Block's spot, with open ground round it) and makes the axis gate -> stone -> trilithon -> hide the walk every
// visitor takes. Its silhouette has three heights (stones 3-5, bower 8, tree 11; the hide 12 in II) and it is
// balanced, not symmetric: water and stone west, timber and tree east. A is the safe one; C is built.
//
// Coordinates: x across the front (west 0 -> east 18), y up (0 = the garden's ground layer on the clicked block), z
// from the front (z = 0, north) to the back. The gate is the gap in the front hedge at x 8..10.
//
// Named parts (the owner's feedback can point at them):
//   Ground   the garden's own lawn at y 0 (grass, moss round the centre, stepping-stone paths), flowers at y 1.
//   Hedge    a low oak-leaf hedge round the garden (1 high, 2 at its corners and gate), clipped corners.
//   Gate     the gap in the front hedge, a lantern post either side.
//   Ring     six standing stones round the centre stone (radius 4-5), the Trilithon at its back.
//   Centre   the mossy cobblestone at (9, 0, 8), flush in the lawn, a ring of moss round it, nothing above it.
//   Pond     Level I's pond, front-west: 14 water sources at y 1 on a mud bed, a grass bank with boulders, lily pads.
//   Bower    the keeper's lean-to on the east: spruce frame, plank back wall, dark oak roof rising to the east; the
//            chest, a honeycomb crate, a potting bench with saplings, a shelf; the pasture stands under its eave.
//   Tree     the honey tree, back-east: a Saccharine trunk (y 1..5) with two branches at y 5, a broad crown to y 10.
//   Rest     a log bench under the tree; a fallen mossy log and boulders back-west.
//   Pond II  Level II's second pond, back-centre, 22 sources, the hide's balcony legs standing on its west bank.
//   Hide     Level II's keeper's hide: a 5 x 5 spruce cabin on five stilts (stone pads), floor at y 5, a hipped dark
//            oak roof with a lightning rod, windows north and west, a balcony east over the pond, up a ladder through
//            a hatch; inside a window bench, a chest and a hung lantern.

export const LEVELS = {
  1: { id: 'habitat_garden', size: [19, 13, 17] }, // w, h, d (the brief: at most 19 x 18 x 25)
  2: { id: 'habitat_garden_2', size: [19, 13, 25] },
}

// Palette (60/30/10): greens and earth (lawn, moss, hedge, the crown, the mud under the ponds) / field stone (the
// ring, the boulders, the stilts' pads) / spruce and dark oak (bower, hide, posts) with honey (the crate, yellow and
// white flowers). Why: a habitat is a piece of land first; the grey stones are the old thing the keeper found, the
// timber is what she added. Dark oak roofs over spruce walls keep the two timber masses darker on top, the stone
// darkest at the foot of each standing stone (andesite and moss low, plain stone high).
export const PALETTE = {
  lawn: 'grass_block',
  soil: 'dirt', // under every bank, so no grass sits under a block
  moss: 'moss_block',
  centre: 'mossy_cobblestone', // the brief's centre stone: never changed
  bed: [['mud', 3], ['clay', 1]],
  path: [['dirt_path', 4], ['coarse_dirt', 1]],
  steps: [['mossy_stone_bricks', 2], ['stone_bricks', 1], ['cracked_stone_bricks', 1]], // the stepping stones
  stone: [['stone', 4], ['andesite', 2], ['mossy_cobblestone', 2], ['cobblestone', 1]],
  stoneCap: { stairs: 'stone_stairs', slab: 'mossy_cobblestone_slab' },
  lintel: { full: 'stone', stairs: 'stone_stairs' },
  boulder: [['mossy_cobblestone', 3], ['cobblestone', 2], ['andesite', 1]],
  hedge: 'oak_leaves',
  crown: [['flowering_azalea_leaves', 3], ['birch_leaves', 2]],
  sacc: 'cobblemon:saccharine_log',
  trunkSoil: 'rooted_dirt',
  frame: 'spruce_log',
  wall: 'bamboo_planks', // reed-yellow boards: the keeper's screens, honey-coloured between the dark spruce frame
  floor: 'spruce_planks', // the hide's covered wooden floor (the brief's pin; Chipped's herringbone in the modded copy)
  wood: 'spruce',
  roof: { full: 'dark_oak_planks', stairs: 'dark_oak_stairs', slab: 'dark_oak_slab' },
  pad: 'cobblestone', // stilt pads
  glass: 'glass_pane',
  crate: 'honeycomb_block',
  lily: 'lily_pad',
  flowers: [['dandelion', 3], ['oxeye_daisy', 3], ['cornflower', 2], ['azure_bluet', 2], ['orange_tulip', 1], ['allium', 1]],
  tallFlowers: ['peony', 'lilac', 'rose_bush'],
}

export const CENTRE = [9, 0, 8] // StarterBlueprints.HABITAT_GARDEN_CENTRE (moved from (7, 0, 7) by the ship step)

// The outline: every cell of the ground layer, less clipped corners.
export const CORNERS = { 1: [[0, 0], [18, 0], [0, 16], [18, 16]], 2: [[0, 0], [18, 0], [0, 24], [18, 24]] }

// Hedges: [x, z] cells (1 high; `tall` 2 high).
const range = (a, b) => Array.from({ length: b - a + 1 }, (_, i) => a + i)
export const HEDGE = {
  both: [
    ...range(1, 6).map(x => [x, 0]), ...range(12, 17).map(x => [x, 0]), // the front, the gate gap 7..11
    ...range(1, 15).map(z => [0, z]), // the west
    [18, 1], [18, 2], ...range(10, 15).map(z => [18, z]), // the east, either side of the bower's back wall
  ],
  1: range(1, 17).map(x => [x, 16]), // Level I's back (Level II opens it onto the back garden)
  2: [...range(16, 23).map(z => [0, z]), ...range(16, 23).map(z => [18, z]), ...range(1, 17).map(x => [x, 24])],
  tall: [[1, 0], [6, 0], [12, 0], [17, 0], [0, 1], [18, 1], [0, 15], [18, 15], [0, 23], [18, 23], [1, 24], [17, 24]],
}

export const GATE = { lamps: [[7, 0], [11, 0]] }

// Paths: stepping stones (`steps`) and worn ground (`path`), [x, z].
export const PATHS = {
  both: {
    path: [[8, 0], [9, 0], [10, 0], [9, 2], [10, 5], [12, 6], [13, 7], [12, 11]],
    steps: [[9, 1], [9, 3], [9, 4], [11, 6], [13, 6], [11, 10]],
  },
  2: {
    path: [[9, 14], [8, 16], [6, 17], [4, 19]],
    steps: [[9, 15], [7, 16], [5, 18], [3, 19]],
  },
}

// The Ring: standing stones [x, z, height, cap] round the centre; the trilithon at its back.
export const RING = {
  moss: [[8, 7], [9, 7], [10, 7], [8, 8], [10, 8], [8, 9], [9, 9], [10, 9]],
  stones: [
    [6, 4, 2, { stair: 'east' }], [5, 8, 3, { slab: true }], [6, 12, 2, { stair: 'south' }],
    [12, 4, 2, null], [13, 8, 3, { stair: 'west' }], [12, 12, 2, null],
  ],
  trilithon: { xa: 8, xb: 10, z: 13, topY: 4 }, // stones y 1..3, the lintel x 7..11 at y 4, tips at 6 and 12
  lanterns: [[7, 3, 13], [11, 3, 13]], // hung under the lintel's proud ends
}

// Ponds: water sources [x, z] at y 1 (each held by its bank, its bed at y 0); boulders on the bank; lily pads.
export const PONDS = {
  1: {
    water: [[2, 2], [3, 2], [4, 2], [2, 3], [3, 3], [4, 3], [5, 3], [2, 4], [3, 4], [4, 4], [5, 4], [3, 5], [4, 5], [5, 5]],
    boulders: [[1, 3], [1, 4], [6, 5], [2, 6], [6, 2]],
    lily: [[3, 3], [5, 4], [2, 2]],
    pin: [3, 1, 4],
  },
  2: {
    water: [[10, 18], [11, 18], [12, 18], ...range(9, 13).map(x => [x, 19]), ...range(9, 14).map(x => [x, 20]),
      ...range(9, 14).map(x => [x, 21]), ...range(10, 13).map(x => [x, 22])],
    boulders: [[15, 20], [15, 21], [14, 23], [13, 17]],
    lily: [[10, 19], [12, 21], [14, 20], [11, 22]],
    pin: [11, 1, 20],
  },
}

// The Bower: the keeper's lean-to (x 15..18, z 3..9), its roof rising to the east from the eave over the pasture.
export const BOWER = {
  x0: 15, x1: 18, z0: 3, z1: 9,
  roofFrom: 14, eaveY: 3, // half a block a column: a slab eave at x 14 (y 3), the beam at x 15, up to x 18 (y 5)
  sideWindows: [[16, 2], [17, 2]],
  backPosts: [3, 6, 9],
  pasture: { at: [14, 1, 6], facing: 'west' }, // the job block (bottom; top at y 2), under the eave
  chest: { at: [17, 1, 5], facing: 'west' },
  crate: [17, 1, 4],
  bench: [[17, 1, 7], [17, 1, 8]], // top slabs, pots on them
  benchPots: ['potted_oak_sapling', 'potted_flowering_azalea_bush'],
  cratePot: 'potted_dandelion',
  lantern: [16, 3, 6], // hung under the roof's slab
  keeperSpot: [13, 1, 7], // VillageHabitatScene: the pasture + (-1, 0, 1), free to stand in
}

// The Tree: trunk [x, z] y 1..5, branch logs [x, y, z, axis], the crown's rows by radius per layer.
export const TREE = {
  trunk: [14, 12], top: 5,
  roots: [[13, 1, 12, 'x'], [15, 1, 12, 'x'], [14, 1, 11, 'z']], // the flare at its foot
  branches: [[13, 5, 12, 'x'], [12, 5, 12, 'x'], [15, 5, 12, 'x'], [16, 5, 12, 'x'], [14, 5, 13, 'z'], [14, 5, 14, 'z'], [14, 5, 11, 'z']],
  lantern: [14, 4, 14], // hung under the south branch
  layers: [[5, 2.9], [6, 3.3], [7, 4.2], [8, 4.4], [9, 4.0], [10, 3.3], [11, 2.3]], // [y, radius]: over the branches (no y 12 cap: hollowed under it, it floated out of the builder's reach over Level II's pond)
  pin: [14, 1, 12],
}

// Small things: a log bench under the tree, a fallen mossy log and a boulder heap back-west, a lamp post by the tree.
export const REST = {
  bench: { x: 17, z0: 12, z1: 14 }, // a stripped spruce log on its side, axis z
  fallenLog: { z: 13, x0: 2, x1: 4 },
  heap: { cells: [[1, 1, 10], [2, 1, 10], [1, 1, 11], [1, 2, 10]], caps: [[2, 2, 10], [1, 2, 11]] },
  lampPost: [13, 1, 10],
  tall: [[1, 1], [17, 1], [1, 14], [16, 15], [7, 15]], // tall flowers by the hedge
}

// The Hide (Level II): a cabin x 1..5, z 17..21 on stilts, floor y 5, walls y 6..7, beam ring y 8, frieze y 9,
// a hip roof (overhang x 0..6, z 16..22) peaking at y 11 with a lightning rod; balcony x 6..8, z 18..20.
export const HIDE = {
  x0: 1, x1: 5, z0: 17, z1: 21, floorY: 5, wallTop: 7, beamY: 8,
  stilts: [[1, 17], [5, 17], [1, 21], [5, 21], [3, 21]],
  braces: [[1, 4, 18, 'north'], [1, 4, 20, 'south'], [5, 4, 18, 'north'], [5, 4, 20, 'south'], [2, 4, 17, 'west'], [4, 4, 17, 'east']],
  ladder: { x: 3, z: 20, facing: 'north' }, // against the back middle stilt, up to the hatch at (3, 5, 20)
  windows: [[[2, 7, 17], [3, 7, 17], [4, 7, 17]], [[1, 7, 18], [1, 7, 19], [1, 7, 20]], [[3, 7, 21]]],
  doorway: [5, 6, 19], door: { facing: 'west' }, // a spruce door out to the balcony
  bench: [[2, 6, 18], [3, 6, 18], [4, 6, 18]], // bottom slabs under the north window
  chest: { at: [2, 6, 20], facing: 'east' },
  lantern: [3, 8, 19], // on a chain from the roof's peak
  balcony: { x0: 6, x1: 8, z0: 18, z1: 20, legs: [[8, 18], [8, 20]], lamps: [[8, 7, 18], [8, 7, 20]] },
  floorPin: [3, 5, 19], logPin: [1, 6, 17],
}

// The walking line: gate -> stepping stones -> the centre stone -> the pasture and the bower's chest; -> the tree;
// (II) -> through the trilithon -> under the hide -> up the ladder -> the hide's chest -> the balcony.
export const WALK = [
  [9, 1, 0], [9, 1, 2], [9, 1, 4], [9, 1, 7], [10, 1, 6], [12, 1, 6], [13, 1, 6], [13, 1, 7], [15, 1, 6], [16, 1, 5],
  [12, 1, 11], [13, 1, 12],
  [9, 1, 12], [9, 1, 14], [8, 1, 16], [6, 1, 17], [4, 1, 19], [3, 1, 20], [3, 6, 20], [3, 6, 19], [5, 6, 19], [7, 6, 19],
]
