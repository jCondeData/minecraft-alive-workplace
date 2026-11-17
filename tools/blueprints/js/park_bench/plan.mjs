// Park Bench, the plan (ROADMAP 49, tools/blueprints/briefs/park_bench.md): drawn before any block. recipe.mjs then
// assembles it from the parts library (../parts) in passes.
//
// Three rough concepts:
//   A. "Lamplighter's Bench": a straight five-seat bench on a paved apron between two lamp posts, a clipped hedge
//      behind. Massing: one low bar, 5 x 3 x 3; no roof; plan: seat, hedge, two lamps. Story: the council's bench by
//      the road. Safe and readable, but it is every park bench ever drawn, and flat from every side but the front.
//   B. "The Blossom Seat": a cherry tree grown from a raised stone bed, the bed's front rim the bench's back, the seat
//      facing the road, a lantern hanging from the tree's forward limb over the sitter. Massing: a low stone bed
//      (5 x 3) with a tall, wide, flat-topped pink crown over it, 7 x 6 x 8; the crown is the roof (shade, not
//      shelter); plan: apron, seat, bed, trunk. Story: the village planted a cherry for its first spring and put a
//      bench in its shade.
//   C. "Arbor Seat": a bench under a little timber pergola with a slab roof and vines. Massing: a 5 x 4 x 4 frame;
//      roof: a flat slat roof; plan: seat inside four posts. Story: a lovers' seat. Nice, but it is a small Gazebo
//      (its own family) and vines want a wall to cling to.
// Strongest: B. It has a silhouette (a pink crown on a gray plinth) that reads from across the village and from the
// map's top view; it is one object, not a bench plus props, because the bed is both planter and backrest and the tree
// is both shade and lamp-bracket; and it earns its 1 beauty point with something a villager would actually walk to.
// Built: B.
//
// Coordinates: x across the front (west 0 -> east 6), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, the road) to the back. The seat faces north; the lane starts at (3, -, -1) on the ground and steps
// half a block up onto the apron at (3, 0, 0) and half a block more at z 1. Seat height: the apron's full blocks at z 1
// put the sitter's feet at y 1 and the stair seat (on a plinth, y 1) at y 1.5, half a block up: a real bench's height.
//
// Masses (named parts the owner's feedback can point at):
//   Apron    paving in front of the seat, x 2..4 (y 0): slabs at z 0, full blocks at z 1: where you stand to sit down.
//   Plinth   the stone course the seat stands on, x 1..5, z 2 (y 0), one with the bed's base.
//   Seat     three dark oak stair seats x 2..4, z 2 (y 1), a slatted trapdoor back on the bed's rim behind them
//            (y 2, z 3), chiseled stone arms with slab caps at x 1 and x 5.
//   Bed      the raised stone bed x 1..5, z 3..5: a stone base (y 0), a rim of stone bricks (y 1), slab coping (y 2),
//            soil inside (x 2..4, z 4, y 1): grass with pink petals either side, rooted dirt under the trunk.
//   Tree     a cherry: trunk (3, 2..6, 4), a forward limb (3, 5, 2..3) over the seat with a lantern under it, two short
//            side limbs at y 6, and a wide, flat, drooping crown y 5..8 over x 0..6, z 1..5.

export const SIZE = [7, 9, 6] // w, h, d (brief: at most 7 x 7 footprint)

// Palette (60/30/10): gray stone (bed, apron, arms) / pink cherry crown / dark wood seat and cherry bark, with the
// lantern and petals as the sparks. Why: a cool, quiet base so the blossom is the one loud thing; the dark oak seat
// reads as a bench against the gray; the gold lantern under pink is the evening picture. By count (100 blocks): about
// 48 stone, 31 blossom, 15 wood and 6 lantern, petals and soil, so nearer 50/30/20; the wood is the seat, so it stays.
export const PALETTE = {
  stone: [['stone_bricks', 6], ['mossy_stone_bricks', 3], ['cracked_stone_bricks', 1]],
  paving: [['stone_brick_slab', 3], ['mossy_stone_brick_slab', 1]], // bottom slabs: a half step up off the grass
  cap: 'stone_brick_slab',
  arm: 'chiseled_stone_bricks',
  seat: 'dark_oak_stairs',
  back: 'dark_oak', // its trapdoors
  log: 'cherry_log',
  limb: 'cherry_wood', // bark all round: a limb's end shows no sawn face
  leaf: 'cherry_leaves',
  soil: 'grass_block',
  rootSoil: 'rooted_dirt',
  petals: 'pink_petals',
  lantern: 'lantern',
}

export const MASSES = {
  apron: { x0: 2, x1: 4, z0: 0, z1: 1 },
  plinth: { x0: 1, x1: 5, z: 2 },
  seat: { x0: 2, x1: 4, y: 1, z: 2, facing: 'north' },
  bed: { x0: 1, x1: 5, z0: 3, z1: 5, y: 1 }, // the rim's course; its base is y 0
  trunk: { x: 3, z: 4, y0: 2, y1: 6 },
  // limbs: [x, y, z, axis]
  limbs: [[3, 5, 3, 'z'], [3, 5, 2, 'z'], [2, 6, 4, 'x'], [4, 6, 4, 'x']],
  lantern: [3, 4, 2], // hangs from the forward limb, a block and a half over the sitter's head
  petals: [[2, 2, 4, 'west', 3], [4, 2, 4, 'east', 4]], // [x, y, z, facing, flower_amount] on the grass
  // the crown, as canopy rows [y, z, x0, x1]: flat and wide, drooping at the edges (a cherry's shape)
  crown: [
    [5, 3, 1, 1], [5, 4, 5, 5], // droops below the crown's floor
    [6, 1, 3, 3], [6, 2, 1, 5], [6, 3, 0, 5], [6, 4, 0, 6], [6, 5, 3, 4],
    [7, 2, 3, 3], [7, 3, 2, 4], [7, 4, 2, 5], [7, 5, 3, 3],
    [8, 3, 3, 3], [8, 4, 3, 3],
  ],
}

export const ROOMS = [{ name: 'Seat', use: 'three seats facing the road, in the shade of the tree', box: [2, 0, 2, 4, 2, 2] }]

// No door: the way in is the apron's middle, straight to the seat.
export const OPENINGS = { approach: { from: [3, 0, -1], to: [3, 0, 1] } }

// Furniture: the seat only. A decoration hands out no seat (no blueprint_furniture/park_bench.json), so it records no
// furniture spots (benchRun's `spot` stays off); the seat is drawn as itself.
export const FURNITURE = []

// The walking line: from the lane's end on the ground, onto the apron, and sit.
export const WALK = [[3, 0, -1], [3, 0, 0], [3, 0, 1], [3, 0, 2]]
