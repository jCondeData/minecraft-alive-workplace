// Flower Planters, the plan (ROADMAP 49, tools/blueprints/briefs/flower_planters.md): drawn before any block.
// recipe.mjs assembles it from the parts library (../parts) in passes.
//
// Three rough concepts:
//   A. The Barrow: a florist's handcart (5 x 3) heaped with boxes under a wool awning, two crates beside it. Roof: the
//      awning. Plan: one low mass on wheels. Story: the flower seller parks at the village edge. Charming, but
//      vanilla wheels read as blobs, the awning is a flat sheet, and it is one small object on a big plot.
//   B. The Ziggurat: four nested square tiers (9, 7, 5, 3) climbing to a lantern on a post at the summit. Roof: none.
//      Plan: concentric rings of beds. Story: the village's first showpiece. A clear outline, but the same from every
//      side, nowhere to stand or sit, and a pile rather than a place.
//   C. The Bower: a horseshoe of beds stepping up in three tiers to the back round a little paved court and a bench,
//      under an open timber pergola whose beams run out over the front boxes to carry two gateway lanterns, a third
//      on a chain over the seat. Roof: open rafters. Plan: a U of beds, a path up the middle. Story: the old couple
//      who planted the village's first flowers sit there at dusk; the boxes at the gate are the children's.
// Strongest: C. It is a place, not a pile: a front (the lantern gateway), a middle (the court and the seat) and a
// back (the tallest bed, a wall of lilacs and peonies), so it reads from the path, from inside and against a house
// wall, and its pergola gives every side a frame, braces and shadows that A and B can't.
//
// Coordinates: x across the front (0..8), y up (0 = the layer on the clicked block), z from the front (0) back (7).
// The builder's way home starts at (4, 0, -1), in front of the path's mouth.
//
// Named parts (for the owner's feedback):
//   Gate boxes   two wooden planter boxes either side of the path at the front (tier 1: soil on the ground layer,
//                spruce boards in front), the children's: tulips and poppies.
//   Side beds    stone-sided beds two deep down each side and into the pergola's side bays (tier 2: soil at y 1).
//   Back bed     the tallest bed along the whole back (tier 3: soil at y 2), lilacs, peonies and rose bushes in two
//                bays between three capped stone piers: a wall of flowers that sits well against a house.
//   Court        a path of slabs up the middle into a 3 x 3 court, the bench at its back.
//   Pergola      four spruce posts on stone plinths (x 2/6, z 2/6), beams along z (y 4) run out front and back with
//                tapered ends, lintels front and back, knee braces in every bay (arches on four sides), three
//                rafters across (y 5) with tapered ends.
//   Lanterns     two hung under the beams' front ends over the gate boxes, one on a chain over the court.

export const SIZE = [9, 6, 8] // w, h, d (limit 9 x 8 x 9)

// Palette (60/30/10, flowers apart): gathered stone for every bed's sides and the plinths (cobble, a little mossy and
// plain stone, so it reads as built by hand) / spruce for the pergola, the boxes and the bench (stripped for the beams
// so the slats across read lighter than the frame under them) / slab paving and the lanterns. Soil: podzol (dug, mulched beds) edged with
// grass; both cost dirt. Why: everything a day-one village has (cobble, spruce, dirt, flowers, one iron's worth of
// lanterns); gray stone and brown wood let the flowers carry all the color.
export const PALETTE = {
  stone: [['cobblestone', 6], ['mossy_cobblestone', 3], ['stone', 1]],
  soil: [['podzol', 3], ['grass_block', 2]],
  paving: [['cobblestone_slab', 3], ['mossy_cobblestone_slab', 1], ['stone_slab', 1]],
  post: 'spruce_log', // posts and the two long beams: dark bark, one frame
  rafter: 'stripped_spruce_log', // the rafters across: pale, so the top reads as slats over a frame, not a lid
  cap: 'cobblestone_slab', // the back bed's piers
  wood: 'spruce', // stairs (braces, beam ends, bench), trapdoors (box boards)
  lantern: 'lantern',
}

// The ground plan, one row per z (front first), one character per x:
//   t  a gate box's board (open trapdoor against the soil behind it)    1  tier 1 soil (y 0), a flower on it
//   2  tier 2: stone y 0, soil y 1, a flower                            3  tier 3: stone y 0..1, soil y 2, a flower
//   P  a post on a stone plinth (stone y 0, log y 1..3)                 p  paving (a bottom slab at y 0)
//   b  the bench (a stair seat at y 0, facing the front)                 #  a pier in the back bed: stone y 0..2, a slab cap
//                                                                          (the back face's rhythm: pier, 3 flowers, pier)
export const PLAN = [
  'tttpppttt',
  '111ppp111',
  '22PpppP22',
  '222ppp222',
  '222ppp222',
  '222bbb222',
  '22P222P22',
  '#333#333#',
]

// Flowers by bed, each a list the recipe deals out with a fixed mix (same cell, same flower). Two-block flowers only
// in the back bed and the outer side cells, never under a beam (the pergola's beam lines x 2 and 6 hold short ones).
export const FLOWERS = {
  gate: [['red_tulip', 2], ['poppy', 2], ['orange_tulip', 1], ['pink_tulip', 1]],
  side: [['allium', 3], ['cornflower', 2], ['oxeye_daisy', 2], ['azure_bluet', 2], ['pink_tulip', 1], ['fern', 1]],
  bay: [['lily_of_the_valley', 2], ['azure_bluet', 2], ['allium', 1]], // under the pergola: small and pale
  back: { 1: 'peony', 3: 'lilac', 5: 'lilac', 7: 'peony' }, // by x, symmetric: pink at the ends, purple either side of the middle pier
  backShort: [['allium', 1], ['oxeye_daisy', 1]], // under the beams' back ends
}

export const PERGOLA = {
  posts: [[2, 2], [6, 2], [2, 6], [6, 6]], // plinth y 0, logs y 1..3
  postTop: 3,
  beams: { y: 4, xs: [2, 6], z0: 0, z1: 7 }, // along z, tapered ends at z 0 and 7
  lintels: { y: 4, zs: [2, 6], x0: 3, x1: 5 }, // along x between the posts
  rafters: { y: 5, zs: [2, 4, 6], x0: 1, x1: 7 }, // along x on the beams, square log ends (stair ends side by side
  // would leave holes.py's 'meet' gaps between them)
  // knee braces: [x, y, z, the side the post is on]
  braces: [
    [3, 3, 2, 'west'], [5, 3, 2, 'east'], [3, 3, 6, 'west'], [5, 3, 6, 'east'],
    [2, 3, 3, 'north'], [2, 3, 5, 'south'], [6, 3, 3, 'north'], [6, 3, 5, 'south'],
  ],
}

// Lanterns (3, well under one stack): hung, never standing on a floor (check.py's lamp rule).
export const LANTERNS = [
  { at: [2, 3, 1], chain: 0, name: 'gate west, under the west beam' },
  { at: [6, 3, 1], chain: 0, name: 'gate east, under the east beam' },
  { at: [4, 3, 4], chain: 1, name: 'over the court, on a chain from the middle rafter' },
]

// The bench: three stair seats at the court's back, looking out of the gateway; the back bed is its backrest.
export const BENCH = { x0: 3, x1: 5, y: 0, z: 5, facing: 'north' }

// The walking line: the path's mouth -> up the path -> the court -> sit on the bench. Furniture: none (a decoration
// hands out no seat), so furniture.json records no spots.
export const WALK = [[4, 0, -1], [4, 0, 0], [4, 0, 1], [4, 0, 2], [4, 0, 3], [4, 0, 4], [4, 0, 5]]
