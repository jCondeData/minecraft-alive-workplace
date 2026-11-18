// Waystone Plaza (`waystone_plaza` and its four culture looks, one level), the plan: drawn before any block. ROADMAP 52;
// the brief is tools/blueprints/briefs/waystone_plaza.md. recipe.mjs assembles it from the parts library (../parts,
// wayshrine.mjs grown for it) in passes. Full notes: DESIGN.md.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Stepped Dais: open paving, a two-step dais with the stone on top, corner lamp posts, a bench each side.
//      No roof. Story: the village set its stone on a plinth in the square. Safe; from 20 blocks a flat gray pad.
//   B. The Wayfarers' Screen: a full-height stone screen across the back, a sheltered bench at its foot under a
//      one-block pent, the stone on a low dais before it. Story: a backdrop for the stone, seen from the road. Reads
//      from the front only: the back and sides are a blank 7-wide wall.
//   C. The Wayshrine: a square two-course hipped roof on four posts, its middle open to the sky over the stone, the
//      stone on a stepped collar, a U of armed benches under the eaves facing it, a lantern under each roof corner.
//      Story: a roadside shrine where travelers sit out of the sun and rain until the stone takes them.
// Strongest: C. The only one with a silhouette (a hipped roof reads as a building from across the village), worked on
// every side, an open-ring top view that says "waystone" on the hall's map, and a real place to wait. Built: C.
//
// Coordinates: x across the front (west 0 -> east 6), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, the road) to the back. The stone stands at (3, 2..3, 3), the centre of three square rings:
//   collar (ring 1, x/z 2..4)   stairs at y 1 climbing to the stone's footing (3, 1, 3); standing room at y 2..3, sky over
//   walk   (ring 2, x/z 1..5)   paving at y 0, an open walk all round with three blocks of headroom (y 1..3); the four
//                               posts on its corners, the roof's upper course over it at y 4
//   edge   (ring 3, x/z 0, 6)   a slab step at y 0 (the way on from the ground), the benches west, back and east, flower
//                               beds in the corners, the eaves over it at y 3, a lantern hanging under each roof corner
// The front (z = 0) is open: the road's way in is the middle (3, 0, 0), straight onto the walk and up the collar.
//
// Masses (named parts the owner's feedback can point at):
//   Paving   y 0, all 49 cells: field on the edge and walk, the walk ring lighter, a path up the front axis
//   Collar   the 3 x 3 stepped dais round the stone, its footing block under the stone
//   Posts    four timber posts on stone footings at (1, 1), (5, 1), (1, 5), (5, 5): footing y 1, post y 2..3
//   Roof     two courses climbing inward: the eaves (edge ring, y 3) and the upper course (walk ring, y 4) on the posts'
//            heads; the 3 x 3 over the collar left open. Desert: flat, a top-slab fascia with full corners and a slab lid
//   Benches  west (x 0, z 2..4, facing east), east (x 6, facing west), back (z 6, x 2..4, facing north): three seats
//            each between stone arms with slab caps
//   Beds     the corners: soil in the paving with a flower; the front ones two cells wide, flanking the way in
//   Lanterns hanging under the four roof corners

export const SIZE = [7, 5, 7] // w, h, d (brief: exactly 7 x 5 x 7 for every look)
export const CENTER = [3, 3]
export const STONE = [3, 2, 3] // lower half; upper at y + 1

export const MASSES = {
  posts: [[1, 1], [5, 1], [1, 5], [5, 5]],
  footingY: 1, postY: 2, eaveY: 3, upperY: 4, // posts y 2..3 under the upper course's corners
  benches: [
    { cells: [[0, 2], [0, 3], [0, 4]], facing: 'east', arms: [[0, 1], [0, 5]] },
    { cells: [[6, 2], [6, 3], [6, 4]], facing: 'west', arms: [[6, 1], [6, 5]] },
    { cells: [[2, 6], [3, 6], [4, 6]], facing: 'north', arms: [[1, 6], [5, 6]] },
  ],
  beds: [[0, 0], [1, 0], [5, 0], [6, 0], [0, 6], [6, 6]],
  lanterns: [[0, 2, 0], [6, 2, 0], [0, 2, 6], [6, 2, 6]],
  path: [[3, 0], [3, 1]], // the front axis: the way in from the road to the collar
}

// One palette per culture look (60/30/10: paving and collar / timber and roof / benches' wood, flowers, lanterns).
// Each look: field (edge and walk paving), walk (the walk ring), edgeSlab (the edge's step), path (the front axis,
// [slab at z 0, full at z 1]), collar (stairs) and footing (under the stone and the posts), post (logs), roof
// ({stairs, hip} or {flat, eave, corner, lid}), seat (stairs), arm and cap (bench ends), soil and flowers (the beds), stone (the waystone).
export const LOOKS = {
  plains: {
    field: [['cobblestone', 4], ['stone', 3], ['andesite', 2]],
    walk: [['stone_bricks', 5], ['cracked_stone_bricks', 1]],
    edgeSlab: [['cobblestone_slab', 3], ['stone_slab', 2]],
    path: ['stone_brick_slab', 'polished_andesite'],
    collar: 'stone_brick_stairs', footing: 'chiseled_stone_bricks', postFooting: 'stone_bricks',
    post: 'oak_log',
    roof: { stairs: 'spruce_stairs', hip: 'dark_oak_stairs' },
    seat: 'oak_stairs', arm: 'stone_bricks', cap: 'stone_brick_slab',
    soil: 'grass_block', flowers: ['poppy', 'cornflower', 'cornflower', 'poppy', 'oxeye_daisy', 'oxeye_daisy'],
    stone: 'waystones:waystone',
  },
  desert: {
    field: [['smooth_sandstone', 4], ['sandstone', 3], ['cut_sandstone', 1]],
    walk: [['cut_sandstone', 1]],
    edgeSlab: [['smooth_sandstone_slab', 3], ['sandstone_slab', 2]],
    path: ['cut_sandstone_slab', 'chiseled_sandstone'],
    collar: 'smooth_sandstone_stairs', footing: 'chiseled_sandstone', postFooting: 'cut_sandstone',
    post: 'stripped_jungle_log',
    roof: { flat: true, eave: 'cut_sandstone_slab', corner: 'chiseled_sandstone', lid: 'smooth_sandstone_slab' }, // a flat sunshade, full corners
    seat: 'jungle_stairs', arm: 'cut_sandstone', cap: 'smooth_sandstone_slab',
    soil: 'sand', flowers: ['dead_bush', 'dead_bush', 'dead_bush', 'dead_bush', 'dead_bush', 'dead_bush'],
    stone: 'waystones:sandy_waystone',
  },
  savanna: {
    field: [['packed_mud', 4], ['mud_bricks', 2], ['coarse_dirt', 1]],
    walk: [['mud_bricks', 1]],
    edgeSlab: [['mud_brick_slab', 1]],
    path: ['mud_brick_slab', 'orange_terracotta'],
    collar: 'mud_brick_stairs', footing: 'terracotta', postFooting: 'mud_bricks',
    post: 'acacia_log',
    roof: { stairs: 'acacia_stairs', hip: 'dark_oak_stairs' },
    seat: 'acacia_stairs', arm: 'mud_bricks', cap: 'mud_brick_slab',
    soil: 'grass_block', flowers: ['orange_tulip', 'short_grass', 'short_grass', 'orange_tulip', 'dandelion', 'dandelion'],
    stone: 'waystones:mud_bricks_waystone',
  },
  snowy: {
    field: [['stone_bricks', 5], ['cracked_stone_bricks', 1], ['cobblestone', 1]],
    walk: [['polished_diorite', 1]],
    edgeSlab: [['stone_brick_slab', 1]],
    path: ['polished_diorite_slab', 'polished_diorite'],
    collar: 'polished_diorite_stairs', footing: 'polished_diorite', postFooting: 'stone_bricks',
    post: 'spruce_log',
    roof: { stairs: 'deepslate_tile_stairs', hip: 'polished_deepslate_stairs' },
    seat: 'spruce_stairs', arm: 'stone_bricks', cap: 'stone_brick_slab',
    soil: 'grass_block', flowers: ['azure_bluet', 'lily_of_the_valley', 'lily_of_the_valley', 'azure_bluet', 'lily_of_the_valley', 'lily_of_the_valley'],
    stone: 'waystones:deepslate_waystone',
  },
  taiga: {
    field: [['cobblestone', 4], ['mossy_cobblestone', 2], ['stone', 1]],
    walk: [['mossy_stone_bricks', 2], ['stone_bricks', 3]],
    edgeSlab: [['cobblestone_slab', 2], ['mossy_cobblestone_slab', 1]],
    path: ['mossy_stone_brick_slab', 'stone_bricks'],
    collar: 'mossy_stone_brick_stairs', footing: 'chiseled_stone_bricks', postFooting: 'mossy_cobblestone',
    post: 'stripped_spruce_log',
    roof: { stairs: 'spruce_stairs', hip: 'dark_oak_stairs' },
    seat: 'spruce_stairs', arm: 'mossy_cobblestone', cap: 'cobblestone_slab',
    soil: 'podzol', flowers: ['fern', 'lily_of_the_valley', 'fern', 'fern', 'fern', 'fern'],
    stone: 'waystones:mossy_waystone',
  },
}

// The blueprint each look is saved as (the plains look is the family's own drawing).
export const IDS = {
  plains: 'waystone_plaza',
  desert: 'looks/desert/waystone_plaza',
  savanna: 'looks/savanna/waystone_plaza',
  snowy: 'looks/snowy/waystone_plaza',
  taiga: 'looks/taiga/waystone_plaza',
}

export const ROOMS = [{ name: 'Shrine', use: 'waiting for the stone on the benches; stepping up to it', box: [0, 0, 0, 6, 3, 6] }]

// No door: the way in is the front axis, from the road at (3, 0, -1) onto the walk and up the collar to the stone.
export const OPENINGS = { approach: { from: [3, 0, -1], to: [3, 1, 2] } }

// Furniture: none. A civic piece hands out no seat (no blueprint_furniture/waystone_plaza.json), so its benches are
// drawn as themselves and keep no furniture spots.
export const FURNITURE = []

// The walking line: the road, the front step, the walk, the collar's front step, beside the stone.
export const WALK = [[3, 0, -1], [3, 1, 0], [3, 1, 1], [3, 2, 2]]
