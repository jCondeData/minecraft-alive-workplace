// Compost Yard (Levels I and II), the plan: drawn before any wall. The bins, the work spot, the storage and the walking
// line come first; recipe.mjs then assembles the yard from the parts library (../parts) to fit this plan.
//
// Three rough concepts (massing, roof, plan, story):
//   A. "The Three-Bin Row": a low open lean-to of three slatted bins along the back of a fenced yard, one pent roof,
//      the composter out in the open. Story: an allotment's three-bin system. Honest but flat: a fence and a shelf,
//      nothing to see from twenty blocks, and nothing a Level II can grow out of but a longer shelf.
//   B. "The Bin Barn": a steep straw-thatched barn whose long front opens on the yard as three bins, the heaps graded
//      fresh green, turning brown and ripe black, behind slatted boards; a tall thatched hoist gable jettied out over
//      the middle bin, its ridge running back into the main ridge in a T; the composter and the scrap chest stand under
//      that gable's overhang, in the dry. Level II grows a riddling shed under the barn's east rake (a pent roof on
//      posts): more chests, the riddle on a pile of fine compost, a potting bench of seedlings, the bone meal's buyers.
//      Story: the Composter turns what the village throws away into black gold, and straw is both his roof and his
//      "browns".
//   C. "The Steaming Court": a walled rubble court round one great heap, a stone vent stack over it smoking on cold
//      mornings, a gate arch to the street. Story: the hot heap that never goes out. Strong at night, but a court is a
//      wall with nothing over it, the stack reads as a forge, and walls eat the block budget.
// Strongest: B. It is the only one with a silhouette (a thatched T, a roof twice the height of its walls), it tells the
// job from the street (the three heaps in a row, green to black), it keeps the worker in the dry under the hero gable,
// and Level II grows out of it the way farm buildings grow, a lean-to under the rake. Built: B.
//
// Coordinates: x across the front (west 0 -> east 12, 14 in Level II), y up (0 = the layer on the clicked block), z
// from the front (z = 0, north) to the back. Kept cells (brief): composter (4, 1, 3), chest (3, 1, 3) facing south;
// old Level II chest (12, 1, 5) and crafting table (12, 1, 7): a chest again, and the riddle pile (no wall, no path).
//
// Masses (named parts the owner's feedback can point at):
//   Barn         the main mass (the nave): frame x 1..11, z 5..9, walls y 1..5 on a rubble plinth, a beam ring at y 6,
//                a steep thatch (ridge along x, y 11) from the front eave (z 4) to the back eave (z 10), overhanging x 0, 12.
//   Turning aisle a lean-to along the back (z 9..11, walls y 1..4) under its own lower 45-degree roof (z 11..12), the
//                nave's back slope breaking onto it: the Composter's way in (a door in the west end, a lantern), where he
//                forks the heaps from behind, the straw stacked by the fresh bin and the bone-meal chest at the far end.
//   Bins         three bins in the barn's front: Fresh (x 2..3), Turning (x 5..7), Ripe (x 9..10), each z 6..8 behind a
//                board at y 1, the heap's face showing above it (y 2..3), mud over the mouth to the beam; each heap slopes
//                down to the aisle behind (y 3, 2, 1), where it is turned; plank dividers x 4 and 8.
//   Hoist gable  the hero: a cross gable over x 3..9 at the same pitch, eave and ridge, jettied out to z 4 on a beam
//                and two brackets over the bins, a loft door (trapdoors) under a king post, a hoist arm with a lantern;
//                its roof runs out to z 3 over the work spot and back into the main ridge in a T with two clean valleys.
//   Work spot    the composter and the scrap chest under the hoist gable's overhang, on a packed yard (y 0).
//   Riddle shed  Level II: its own lower 45-degree pent roof on two corner posts (x 14), leaning on the barn's east wall
//                under the gable's beam (x 12..14, z 4..12): two chests against the barn wall, hay, the pile of riddled fine compost, the potting bench,
//                a bed of finished compost with ferns and lilies along its open side.
//
// Palette 60/30/10: 60 straw (the thatch, bamboo mosaic stairs over full blocks, a hay ridge) and packed mud (wall
// panels, gables); 30 dark oak (frame, beams, trim) and the rubble plinth (cobblestone, moss, andesite, in clusters);
// 10 the heaps themselves (moss and leaves, mud and rooted dirt, podzol) and the flowers grown in the finished compost.
// Why: straw over mud is what a composter has in his own yard; the gold roof reads from far off, the dark frame draws
// the bins, the heaps grade from green to black, which is the job.
export const LEVEL = Number(process.env.LEVEL ?? 1)

export const SIZE = LEVEL === 1 ? [13, 12, 13] : [15, 12, 13] // limits 13 x 16 x 13 and 15 x 16 x 13

export const PALETTE = {
  stone: [['cobblestone', 5], ['mossy_cobblestone', 3], ['andesite', 2]],
  frame: 'dark_oak_log',
  infill: 'packed_mud',
  roof: { full: 'bamboo_mosaic', stairs: 'bamboo_mosaic_stairs', slab: 'bamboo_mosaic_slab', ridge: 'hay_block' },
  trim: 'dark_oak', // brackets, barge boards, the beam's ends
  wood: 'spruce', // bin boards, dividers, trapdoors, fences, lamp posts, the door
  yard: 'coarse_dirt', // the packed yard
  path: 'packed_mud', // the trodden path from the steps to the composter (and the aisle's floor)
  // the bins, green to black
  fresh: [['moss_block', 4], ['oak_leaves', 3], ['azalea_leaves', 2], ['melon', 1]],
  turning: [['mud', 4], ['rooted_dirt', 4], ['moss_block', 2]],
  ripe: [['podzol', 5], ['coarse_dirt', 3], ['dirt', 2]],
}

export const MASSES = {
  barn: {
    frame: { x0: 1, x1: 11, z0: 5, z1: 9 },
    wallTop: 5, beamY: 6,
    frontPosts: [1, 4, 8, 11], arcadePosts: [1, 4, 8, 11],
    roof: { axis: 'x', across: [4, 10], along: [0, 12], eaveY: 6 },
  },
  aisle: {
    back: 11, wallTop: 4, posts: [1, 4, 8, 11],
    roof: [{ z: 11, y: 5 }, { z: 12, y: 4 }], along: [0, 12], // a 45-degree lean-to, rising north to the nave's eave
  },
  hoist: {
    face: { x0: 4, x1: 8, z: 4 }, // the jettied gable face, on a beam at y 6
    brackets: [4, 8],
    roof: { axis: 'z', across: [3, 9], along: [3, 7], eaveY: 6 },
  },
  shed: { // Level II
    posts: [4, 12], postX: 14, z0: 4, z1: 12,
    roof: { x0: 12, y0: 5, x1: 14, falls: 'east' }, // under the east gable's beam (y 6)
  },
}

export const BINS = [
  { name: 'Fresh', x0: 2, x1: 3, heap: 'fresh' },
  { name: 'Turning', x0: 5, x1: 7, heap: 'turning' },
  { name: 'Ripe', x0: 9, x1: 10, heap: 'ripe' },
].map(b => ({ ...b, boardY: 1, boardZ: 5, slope: [{ z: 6, top: 3 }, { z: 7, top: 2 }, { z: 8, top: 1 }] }))

// Where things stand. No furniture spots (the brief asks for none): every piece below is a fixture or a prop.
export const FIXTURES = [
  { name: 'composter', block: 'composter', pos: [4, 1, 3], level: 1 }, // the job block (kept cell)
  { name: 'scrap chest', block: 'chest', pos: [3, 1, 3], facing: 'south', level: 1 }, // kept cell; its back to the street
  { name: 'bone-meal chest', block: 'chest', pos: [10, 1, 10], facing: 'west', level: 1 }, // the aisle's far end
  { name: 'shed chest', block: 'chest', pos: [12, 1, 5], facing: 'east', level: 2 }, // old Level II's chest cell
  { name: 'store chest', block: 'chest', pos: [12, 1, 9], facing: 'east', level: 2 },
]

export const OPENINGS = {
  steps: [[5, 0, 0], [6, 0, 0], [7, 0, 0]], // up onto the packed yard from the street
  binMouths: BINS.map(b => ({ name: b.name, cells: [[b.x0, 2, 5], [b.x1, 3, 5]] })), // the heap seen over the board
  aisleDoor: { at: [1, 1, 10], facing: 'east', out: 'west', step: [0, 0, 10] },
  loftDoors: [[6, 7, 4], [6, 8, 4]], // the hoist gable's doors (trapdoors), closed
  backVents: [[2, 3, 11], [6, 3, 11], [10, 3, 11]], // slatted vents (trapdoors) in the aisle's back wall
}

export const OUTSIDE = {
  yard: { x0: 2, x1: 10, z0: 1, z1: 4 }, // packed y 0 floor (Level II: out to x 13)
  path: [[6, 1], [5, 1], [7, 1], [6, 2], [5, 2], [4, 2], [5, 3]], // steps -> composter
  shedPath: [[8, 2], [9, 3], [10, 4], [11, 4], [12, 4], [13, 4]], // Level II: on to the shed
  lampPosts: [[1, 0, 1], [11, 0, 1], [0, 0, 8]], // the gate, and the aisle door
  hoistLamp: [6, 9, 3], // a fence arm out of the gable, the lantern under it
  jettyLamps: [[5, 4, 4], [7, 4, 4]], // on a chain under the jetty beam, over the work spot
  scraps: [{ at: [9, 1, 2], block: 'pumpkin' }, { at: [10, 1, 2], block: 'hay_block' }, { at: [10, 1, 3], block: 'melon' }],
  straw: [[2, 1, 9], [3, 1, 9]], // in the aisle, by the fresh bin: the browns to layer in
  aisleLamp: [6, 5, 9], // hung from the arcade beam
  beds: [{ x0: 0, x1: 1, z: 3, plants: ['allium', 'cornflower'] }, { x0: 11, x1: 12, z: 3, plants: ['oxeye_daisy', 'poppy'] }],
}

// The walking line: the street -> the steps -> the work spot beside the composter (within 2.5 of it) and the chest;
// round the west end to the aisle door, along the aisle to the bone-meal chest; Level II on to the shed's aisle (x 13).
export const WALK = [
  [6, 0, -1], [6, 0, 0], [6, 1, 1], [6, 1, 2], [5, 1, 2], [5, 1, 3],
  [2, 1, 2], [1, 0, 2], [0, 0, 4], [0, 0, 9], [0, 0, 10], [1, 1, 10], [2, 1, 10], [5, 1, 10], [9, 1, 10],
  [7, 1, 2], [8, 1, 2], [11, 1, 3], [12, 1, 4], [13, 1, 4], [13, 1, 5], [13, 1, 7], [13, 1, 9], [13, 1, 10],
]
