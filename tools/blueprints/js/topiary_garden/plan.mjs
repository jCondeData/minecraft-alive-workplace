// Topiary Garden, the plan (ROADMAP 49, tools/blueprints/briefs/topiary_garden.md): drawn before any block.
// recipe.mjs assembles it from the parts library (../parts) in passes.
//
// Three rough concepts:
//   A. The Knot: a flat 9 x 9 parterre, one leaf high, its box hedges woven into a knot round a cone in the middle.
//      Roof: none. Plan: a square of interlaced bands. Story: the steward's wife copied it out of a book. Beautiful
//      from above, but from the lane it is a green carpet two blocks high with one cone: no silhouette, no place.
//   B. The Avenue: two rows of four ball-on-stem standards down a paved walk to an urn at the back. Roof: none. Plan:
//      a corridor. Story: a lord's drive in miniature. It reads at once, but it is a row of lollipops, open on all
//      sides, the same from every angle, and it has no inside: you walk through it, never into it.
//   C. The Green Room: a room whose walls are clipped yew. A low hedge across the front, broken by a deep hedge
//      archway you walk through; side hedges that step up 2 - 3 - 4 towards the back; a tall back hedge swept up
//      into an exedra behind a stone bench, lamps either side of it. In the middle, on a paved ring, a three-tier
//      pom-pom standard with a flowering crown, the tallest thing in the garden; pencil yews close the cross axis,
//      white and purple beds fill the margins, urns mark the ring's corners. Roof: open sky, the hedge tops
//      do the work of a roofline. Plan: an axis from the arch, round the standard, to the seat. Story: the village's
//      old gardener clips it every market day; couples sit on the bench at dusk under the yew, facing the arch.
// Strongest: C. It is a place with an inside, not an ornament: a threshold (the arch), a center (the standard) and a
// destination (the seat), and its silhouette climbs from the low front to the exedra and the crown from every side,
// where A is flat and B is a row. Its risk (hedges hide the inside) is met by keeping the front low and the arch
// open, so the lane sees straight down the axis to the standard and the bench.
//
// Coordinates: x across the front (0..8), y up (0 = the layer on the clicked block), z from the front (0) back (8).
// The village lane starts at (4, 0, -1), right in front of the arch.
//
// Named parts (for the owner's feedback):
//   Front hedge   low (2 high) yew across the front, x 0..1 and 7..8, with a clipped ball on each outer corner.
//   Arch          a hedge archway two deep (z 0..1): piers at x 2 and 6 up to y 4, lintel at y 5, rounded shoulders,
//                 a flowering crown at y 6; the opening is three wide, the middle four high.
//   Side hedges   x 0 and x 8, stepping 2 / 3 / 4 high front to back (z 0..2, 3..5, 6..8).
//   Back hedge    z 8, 4 high, swept up behind the bench into an exedra (5 high, 6 in the middle); the back corners
//                 clipped up to 5.
//   Standard      the hero: on the ring's middle (4, 4): a 3 x 3 drum two high, the stem, a cross tier, the stem, a ball
//                 and a flowering crown, top at y 7.
//   Ring          paving round the standard (x 2..6, z 2..6), joined to the arch and the bench by the axis.
//   Urns          a potted flowering azalea on a stone post at each corner of the ring.
//   Pencil yews   two narrow columns at the ends of the cross axis (x 1 and 7, z 4), capped with blossom.
//   Beds          white and purple flowers in grass along x 1 and x 7, and either side of the arch inside.
//   Seat          a stone bench (x 3..5, z 7) facing the arch, a lantern on a post at each end, the exedra behind.
//   Box cubes     a low clipped cube in each back corner of the beds (1, 7) and (7, 7).

export const SIZE = [9, 8, 9] // w, h, d (limit 9 x 8 x 9)

// Palette (60/30/10): clipped greens (dark spruce "yew" for the hedges, bright azalea for the pieces clipped into
// shapes, so the room's walls and its ornaments read apart) / stone (worn stone-brick paving, stone posts, the bench) /
// color (flowering azalea crowns, white and purple flowers, two lanterns). Why: a formal garden is green on gray; the
// two leaf tones give the hedges depth against the standards, and one blossom color ties the crown, the arch and the
// urns together. Every leaf costs shears; nothing grows, decays or needs water.
export const PALETTE = {
  hedge: 'spruce_leaves',
  clipped: 'azalea_leaves',
  blossom: 'flowering_azalea_leaves',
  stem: 'spruce_fence', // the standard's slender stem between its tiers
  lawn: 'grass_block',
  paving: [['stone_bricks', 6], ['mossy_stone_bricks', 2], ['cracked_stone_bricks', 1]],
  post: 'stone_brick_wall',
  seat: 'stone_brick_stairs',
  urn: 'potted_flowering_azalea_bush',
  lamp: 'lantern',
  flowers: ['allium', 'lily_of_the_valley', 'azure_bluet', 'allium', 'oxeye_daisy'],
}

export const MASSES = {
  frontHedge: { runs: [[0, 1], [7, 8]], z: 0, height: 2, balls: [[0, 0], [8, 0]] },
  arch: { xa: 2, xb: 6, z0: 0, z1: 1, spring: 4, crown: 1 },
  sideHedges: { xs: [0, 8], height: z => (z <= 2 ? 2 : z <= 5 ? 3 : 4) },
  backHedge: { z: 8, x0: 0, x1: 8, height: x => (x === 4 ? 6 : x >= 3 && x <= 5 ? 5 : x === 0 || x === 8 ? 5 : 4) },
  standard: { x: 4, z: 4, tiers: ['square', 'square', 'stem', 'plus', 'stem', 'ball', 'ball'] },
}

// The ground layer: which cells are paving (the rest is lawn and bed soil).
export function paved(x, z) {
  if (z <= 1) return x >= 3 && x <= 5 // under the arch
  if (z >= 2 && z <= 6) return x >= 2 && x <= 6 // the ring (and under the standard's drum)
  if (z === 7) return x >= 2 && x <= 6 // the bench row
  return false
}

export const URNS = [[2, 2], [6, 2], [2, 6], [6, 6]]
export const PENCILS = [[1, 4], [7, 4]] // y 1..3, blossom cap at y 4
export const CUBES = [[1, 7], [7, 7]] // y 1..2
export const SEAT = { x0: 3, x1: 5, y: 1, z: 7, facing: 'north', lamps: [[2, 7], [6, 7]] }
export const BEDS = [[1, 1], [1, 2], [1, 3], [1, 5], [1, 6], [7, 1], [7, 2], [7, 3], [7, 5], [7, 6]]

// No furniture spots: a decoration hands out no seat and no job (the bench is ornament).
export const FURNITURE = []

// The walking line: the lane -> under the arch -> round the standard on the ring -> the bench.
export const WALK = [[4, 0, -1], [4, 1, 0], [4, 1, 1], [4, 1, 2], [3, 1, 2], [2, 1, 3], [2, 1, 4], [2, 1, 5], [3, 1, 6], [4, 1, 6], [4, 1, 7]]
