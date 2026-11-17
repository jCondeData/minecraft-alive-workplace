// The Fountain (`fountain`, one level), the plan: drawn before any block. ROADMAP 49 (rebuild); the brief is
// tools/blueprints/briefs/fountain.md. recipe.mjs assembles it from the parts library (../parts).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Brimming Tazza. An octagonal stone basin on a ring of steps, a slim column rising out of its water to a
//      wide white bowl and, above that, a small one: three tiers narrowing 9 -> 5 -> 3, no roof, the plan a rounded
//      octagon with green planters in its four cut corners. Story: the masons' showpiece, raised once the village has
//      water to spare: every bowl is filled to the brim, so the still water stands proud of each slab rim, the
//      nearest a builder's fountain comes to spilling over.
//   B. The Wall Spring. A back wall of stone with an arched niche and three spout heads over a long trough, a bench
//      either side, a small tiled coping on the wall. Story: a hillside spring piped through the garden wall. Pretty
//      from the road, but a decoration the Steward turns to the road and walls round reads as a slab from three of
//      its four sides, and spouts with no falling water (no flowing water may be drawn) look dry.
//   C. The Fountain House. A square basin under an open canopy on four posts, a copper-capped hip roof. Story: shade
//      over the village's water. It shelters a pool well, but it is the Well's roofed silhouette again and walks onto
//      the Gazebo's ground (a canopy on posts, built next in the same Gardens zone).
// Strongest: A. It is the only one that stands free and reads the same from every side of a garden, its stacked
// bowls are the one silhouette nobody mistakes for a well, a gazebo or a pond, and it turns the brief's hardest rule
// (only still sources) into its look: three brimming bowls with slab rims, water standing above the stone. It also
// spends its plot well: 9 x 9 (the old one was 7 x 7, the limit is 13), the smallest square that gives three tiers a
// true centre and a step all round.
//
// Coordinates: x across the front (west 0 -> east 8), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, the road) to the back. Exactly 9 x 9, centre (4, 4). No door: the lane starts at (4, 0, -1), one
// block in front of the middle of the front edge, straight onto the front step (4, 0, 0).
//
// Masses (named parts the owner's feedback can point at):
//   Steps     a stair on each of the four straight sides of the octagon (5 a side, y 0), rising toward the basin: the
//             way up to the rim from any side. Stone bricks, mossy ones mixed in low down.
//   Basin     the bottom tier: a rounded octagon of stone at y 0 (its floor and the curb's foot), the curb ring at y 1
//             capped with white slabs (polished diorite), 20 water sources inside it at y 1 round the column's foot.
//             The rim stands 1.5 blocks over the ground: a child can lean on it, and a man on the step looks down into it.
//   Column    a chiseled stone foot in the water (y 1), a slim stone-brick wall shaft (y 2), the bowls' bosses, and a
//             second shaft between the bowls (y 5).
//   Tazza     the middle tier: a 5-wide round (diamond) white bowl (y 3..4) flaring out from the column on
//             upside-down stairs, its 4 sources at y 4 brimming over a slab rim; its four tips are full bosses, and a
//             lantern hangs from each over the basin's water (at night the bowl glows from underneath).
//   Crown     the top tier: a 3-wide white rosette (y 6..7), one source at y 7 brimming over four slab lips: the
//             highest water in the village.
//   Planters  the octagon's four cut corners (2 x 2 each): moss with flowers and a flowering azalea, and a lamp post
//             (a stone-brick wall post, a lantern on it) on the corner nearest the basin.
//   Lighting: the four corner lamps and the tazza's four hanging lanterns (a lantern over a source is the non-water
//   block a Well Keeper looks for, so they don't stop him dipping). 25 sources in all (20 + 4 + 1): 25 buckets, 25
//   of the 108 stacks; the whole list is about 42 stacks.

export const SIZE = [9, 8, 9] // w, h, d (brief: at most 13 x 16 x 13). Ship step: StarterBlueprints.FOUNTAIN (7, 5, 7)
// becomes new Vec3i(9, 8, 9), and generate.py's ("fountain", fountain) entry stops overwriting the .nbt.

// Palette (60/30/10): grey stone bricks (steps, basin, column) / white polished diorite (the rims and both bowls,
// "marble") / accents: the water, the planters' green and flowers, lantern light. Why: grey and weathered at the
// ground, lighter as it rises (STYLE.md: darkest at the foundation), so the eye climbs the tiers to the top bowl;
// every block is one a miner brings from an ordinary cave (no quartz, prismarine or copper that would weather and
// come back as a different block on a take-down).
export const PALETTE = {
  base: [['stone_bricks', 5], ['mossy_stone_bricks', 3], ['cracked_stone_bricks', 1]], // the basin's foot and floor
  step: [['stone_brick_stairs', 3], ['mossy_stone_brick_stairs', 2]],
  rim: 'polished_diorite_slab', // every rim: the bottom tier's curb cap and both bowls' lips
  flare: 'polished_diorite_stairs', // the bowls' undersides, upside down
  boss: 'polished_diorite', // the bowls' centres
  foot: 'chiseled_stone_bricks', // the column's foot in the water
  shaft: 'stone_brick_wall',
  soil: 'moss_block', // the planters: green on every face (a grass block on the plinth showed bare dirt sides)
  lampPost: 'stone_brick_wall',
}

const C = [4, 4] // the centre [x, z]
export const MASSES = {
  centre: C,
  steps: { y: 0, r: 4, half: 2 }, // the four straight sides: |d| = 4 across, |d| <= 2 along
  basin: { y: 0, outer: [3, 5], water: [2, 3], waterY: 1 }, // octCells(r, k) of the curb's outside and the water
  column: { foot: 1, shafts: [[2, 2], [5, 5]] }, // the foot's y, the shafts' [y0, y1]
  tazza: { y: 4, water: [1, 1], core: true }, // water: octCells(1, 1) = the four cells round the column, a diamond bowl
  crown: { y: 7, water: [0, 0], core: false }, // one source on the centre
  planters: [[0, 0, -1, -1], [8, 0, 1, -1], [0, 8, -1, 1], [8, 8, 1, 1]], // outer corner [x, z] and its sign [sx, sz]
}

// Plants per planter, by the cell's place in its 2 x 2: the outer corner, the two side cells, and the inner corner
// carries the lamp post. Each corner has its own pair of flowers, so the four read as planted, not printed.
export const PLANTS = {
  bush: 'flowering_azalea',
  flowers: [['cornflower', 'oxeye_daisy'], ['allium', 'lily_of_the_valley'], ['oxeye_daisy', 'cornflower'], ['lily_of_the_valley', 'allium']],
}

// Rooms: none (an open fountain). The space round the basin on its steps is the Rim.
export const ROOMS = [{ name: 'Rim', use: 'looking in, sitting on the step, dipping a bucket', box: [0, 0, 0, 8, 2, 8] }]

// Openings: none (no walls). The steps are open on all four sides.
export const OPENINGS = { approach: { lane: [4, 0, -1], step: [4, 0, 0], standAt: [4, 1, 0] } }

// Furniture: none (the brief: no job block, no bed, no container, no furniture kind; the step ring is the seat).
export const FURNITURE = []

// Lights: the four lamp posts (post y 1..2, lantern y 3), on each planter's inner corner; the tazza's four lanterns
// (y 2, under its tips) are drawn with the bowl in recipe.mjs.
export const LAMP = { postY: [1, 2], lanternY: 3 }

// The walking line: the lane's end -> the front step -> the rim (a Well Keeper on the step at (4, 1, 0) is two blocks
// from the basin's front source (4, 1, 2), with only air above it) -> along the front step -> round the outside of
// the planters (outside the plot) to the next side's step, and so on round all four sides.
export const WALK = [[4, 0, -1], [4, 1, 0], [2, 1, 0], [6, 1, 0], [9, 0, 1], [8, 1, 2], [8, 1, 6], [9, 0, 7], [4, 1, 8], [-1, 0, 7], [0, 1, 4]]
