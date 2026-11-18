// The Market Cross (`market_cross`, one level), the plan: drawn before any block. ROADMAP 49 (rebuild); the brief is
// tools/blueprints/briefs/market_cross.md. recipe.mjs assembles it from the parts library (../parts, market_cross.mjs).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Mercat Drum. A squat octagonal stone drum, two blocks high, with a parapet round its flat top and a stair
//      cut into its front; a white shaft rises from the middle of the platform to a capital that hangs four lamps over
//      the square and carries the market flag. No roof; the plan an octagon on a one-step octagonal plinth, benches
//      against three of its faces. Story: the Scottish mercat cross: the platform is where the reeve climbs to read the
//      prices and open the market, the drum is where everyone else sits and waits; the flag goes up on market day.
//   B. The Butter Cross. Eight stone piers carrying a pyramid roof over an open floor, a shaft through the roof's
//      peak. Story: an English market shelter where farmers' wives sold butter out of the rain. It reads as a place,
//      but in 9 x 8 x 9 it is the same open roofed octagon as the Gazebo and the Wayshrine, only smaller.
//   C. The Stepped Calvary. Three octagonal steps to a tall slim shaft and a carved head. Story: the plain churchyard
//      cross moved to the crossroads. Honest and cheap, but from across a square it is a post on a cake: nothing to
//      climb, nowhere to sit, and nothing marks it as a market's.
// Strongest: A. It is the only one that is a place you go up onto, so it does the job the brief gives it (where people
// meet, a landmark) with its body, not a sign; its silhouette (a wide low drum, a bright needle, a flag) is nothing
// else in the village: not a fountain, not a gazebo, not a well. B is the safe one; A is the strong one.
//
// Coordinates: x across the front (west 0 -> east 8), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, the road) to the back. Centre (4, 4). No door: the lane starts at (4, 0, -1) and walks straight up the
// stair.
//
// Masses (named parts the owner's feedback can point at):
//   Plinth    y 0: one step, an octagon |dx| + |dz| <= 7 inside the 9 x 9 (corners cut), gathered stone; its four cut
//             corners bottom slabs (a half step down to the ground), and the four inner corners a grass cell with a
//             flowering azalea (the planting).
//   Drum      y 1..2: an octagon |dx| + |dz| <= 5 inside 7 x 7 (x/z 1..7). Lower course y 1 stone brick with a
//             polished-andesite pilaster at each of its eight vertices (the cut ends of each face), a chiseled
//             medallion in the middle of the east, west and back faces. Upper course y 2 smooth stone: the cap round
//             the edge and the platform's floor in one.
//   Stair     the front cut: three flights of stairs x 3..5, climbed going south: on the plinth (y 0, z 0), in the
//             drum (y 1, z 1) and (y 2, z 2); the drum's x 2 and x 6 columns are its cheeks.
//   Parapet   y 3: on the drum's outer ring (every drum cell with an outside cell among its eight neighbours, so the
//             ring is joined four ways round the cut corners), open over the stair: a polished-andesite pier over
//             every pilaster (the eight face ends: the pilasters run up through the parapet), stone brick wall
//             between them and in each cut corner's inside cell, so no wall run turns a corner without its post.
//   Posts     the parapet's two front ends (2, 1) and (6, 1) are wall posts carrying lanterns (on a post: owner's
//             rule 2); the back two piers (2, 7) and (6, 7) carry yellow banners.
//   Shaft     (4, 4): y 3 a chiseled foot with a flare of four polished andesite stairs round it, y 4..5 quartz pillar,
//             y 6 the head: chiseled quartz with four slim diorite-wall arms out to the four sides (the cross itself,
//             a thin white bar seen from every side; a full-block head read as a mushroom in the first render), a
//             lantern hanging under each arm (y 5), and on top (y 7) the red market flag, a standing banner facing
//             the road.
//   Benches   stair seats against the drum on the plinth: west (x 0, z 3..5), east (x 8, z 3..5), back (z 8, x 3..5),
//             each seat's back to the drum, looking out at the square.
//
// Rooms: none (an open platform). Openings: the stair cut only. Furniture spots: none (no room, nothing the code reads;
// the benches are stairs, recorded as no spot, as the Sundial records none).
// Walking line: (4, 0, -1) ground -> (4, 0, 0) first step -> (4, 1, 1) -> (4, 2, 2) -> platform (4, 3, 3), round the
// shaft on the ring x 2..6, z 3..6 inside the parapet; or round the plinth to the three benches.

export const SIZE = [9, 8, 9] // w, h, d (brief: at most 9 x 8 x 9). Ship step: StarterBlueprints.MARKET_CROSS (5, 8, 5)
// becomes new Vec3i(9, 8, 9), `market_cross` joins generate.py's REBUILT, docs/wiki/more-decorations.md's size row.

// Palette (60/30/10): gray stone brick and gathered stone (plinth, drum) / smooth stone and polished andesite (the cap,
// pilasters, stair treads, the parapet's walls) / accents: the white quartz shaft, the red flag and yellow pennants
// (the market's colours), four hanging lanterns, two azaleas. Why: a civic stone that matches the village's paving
// so the square seems to rise into it; the one bright thing is the shaft, so it reads from across the square.
export const PALETTE = {
  plinth: [['stone_bricks', 5], ['andesite', 2], ['cobblestone', 2], ['mossy_stone_bricks', 1]],
  plinthSlab: 'stone_brick_slab',
  bed: 'grass_block',
  bush: 'flowering_azalea',
  drum: [['stone_bricks', 7], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1]],
  core: 'cobblestone', // the drum's hidden fill
  pilaster: 'polished_andesite',
  pier: 'polished_andesite',
  medallion: 'chiseled_stone_bricks',
  cap: 'smooth_stone',
  tread: 'polished_andesite_stairs',
  parapet: 'stone_brick_wall',
  seat: 'stone_brick_stairs',
  shaft: { foot: 'chiseled_stone_bricks', flare: 'polished_andesite_stairs', pillar: 'quartz_pillar', capital: 'chiseled_quartz_block', arm: 'diorite_wall' },
  lamp: 'lantern',
  flag: 'red_banner',
  pennant: 'yellow_banner',
}

export const MASSES = {
  centre: [4, 4],
  plinth: { y: 0, r: 4, k: 7, slabCorners: true, beds: [[1, 1], [7, 1], [1, 7], [7, 7]] },
  drum: { y0: 1, y1: 2, r: 3, k: 5, medallions: [[1, 4], [7, 4], [4, 7]] },
  stair: { x0: 3, x1: 5, flights: [[0, 0], [1, 1], [2, 2]] }, // [y, z]
  parapet: { y: 3, gap: { x0: 3, x1: 5, z0: 0, z1: 2 } },
  posts: { lamps: [[2, 1], [6, 1]], pennants: [[2, 7, 2], [6, 7, 14]] }, // [x, z, banner rotation]
  shaft: { y0: 3, pillarTo: 5, capital: 6, flag: 7, flagRotation: 8 },
  benches: [
    { cells: [[0, 3], [0, 4], [0, 5]], facing: 'east' }, // stair facing = the side its back is on (the drum)
    { cells: [[8, 3], [8, 4], [8, 5]], facing: 'west' },
    { cells: [[3, 8], [4, 8], [5, 8]], facing: 'north' },
  ],
}
