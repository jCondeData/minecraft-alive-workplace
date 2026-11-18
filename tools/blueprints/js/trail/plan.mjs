// The Trail Marks (story/trail_1 .. story/trail_5), the plan: drawn before any block. One recipe (recipe.mjs, MARK=1..5)
// draws all five from this plan with the shared parts library (../parts; trail_marks.mjs grown for it).
// Brief: tools/blueprints/briefs/trail.md. Nobody lives, works or trades here: no rooms, no furniture spots.
//
// Three rough concepts first:
//   A. "The Strewn Load": each mark a low heap of the caravan's goods (a lantern, a barrel, a banner, ruts, a crate),
//      a bigger heap each time. Plan: 3 x 3 to 5 x 5 patches of trodden earth. Story: the cart shed its load as it was
//      hauled away. Safe and true to the pins, but every mark is knee high: from 25 blocks away, across long grass or
//      a forest edge, there is nothing to see, which is the critic's finding against today's marks.
//   B. "The Raiders' Blazes": the raiders stake their own way home with dark poles; the caravan's spill lies at each
//      pole's foot. Plan: one tall dark pole per mark, the props round it. Story: the trail is the raiders' road.
//      Tall and easy to spot, but every mark looks the same, and the story is told by the villains, not the caravan.
//   C. "The Carter's Rags" (built): the captive carter tears strips off the wagon's white cover and ties them high
//      wherever he can, so his people can follow, while the wagon comes apart along the way. Masses: each mark is a
//      piece of the same wagon standing up tall, with a white rag at its top; the pieces grow, from a lantern pole to
//      a lost wheel, the torn cover, the whole cart reared on its tail, and then the raiders' black flag over his rag,
//      torn down. Plan: a 3 x 5 to a 5 x 9 patch of trodden earth, ruts running through every one.
//   C is the strongest: it gives the player one thing to look for (a white rag held up against the sky, from any
//   side and in any biome), it tells the story in order (a lantern dropped, a wheel lost, the cover torn off, the
//   cart wrecked, the raiders' camp), and the climax, white turning to black, says "the stockade is next" without a
//   word. A and B are each half of it.
//
// Story, mark by mark (each grows out of the last):
//   1 The Lantern Pole: past the wreck the raiders cut the horses loose. The wagon's lantern pole lies wrenched off,
//     stuck upright in the verge; the carter's lantern still hangs on its hook, low, and the first rag is knotted
//     at the pole's top. The ruts of the dragged wagon start here.
//   2 The Lost Wheel: the wagon lurches on three wheels; a barrel bounces off and bursts, its staves round it, a sack
//     of grain split beside it; the lost wheel leans on a stake the carter tied two rags to.
//   3 The Torn Banner: the raiders tear the cover off the hoops. One hoop still stands with the canvas slumped over
//     it like a lean-to; the settlers' white banner is jammed upright on a splintered pole, a jeer.
//   4 The Drag: the axle snaps. The cart is levered up on its tail, shafts to the sky, its goods dragged off on a
//     sledge: the ruts become one wide scar. A rag is tied to the upturned bed, the highest yet.
//   5 The Shot Crate: the raiders' camp edge. The caravan's crates stacked as an archery butt, studded with arrows; a
//     fire still smoking; the raiders' black standard over the carter's last rag, torn down into the dirt; their
//     stakes, one capped with a skull. The stockade lies ahead.
//
// Coordinates: x across (odd widths, so the middle is a true column), y up (y 0 is the ground layer itself: it
// replaces the world's top block), z along (the way the ruts run, from z 0). No front: the arc engine turns a mark a
// random quarter, so every mark must read from all four sides. Pinned cells (brief, "Levels") are in PINS.

// Palette (60/30/10): trodden earth (coarse dirt, rooted dirt, path ruts, podzol, a little grass at the rim) /
// the caravan's pale stripped spruce and the raiders' dark oak / the white canvas (rags, banner, cover) with the
// lantern's and the fire's light and, at the end, the black standard. Why: earth reads as "something passed here"
// in plains, forest, desert and snow alike; the stripped spruce is lighter than any tree trunk, so a post never
// passes for a tree; white against the sky is the sign to look for, and the one black flag is the end of it.
export const PALETTE = {
  ground: {
    trodden: [['coarse_dirt', 5], ['rooted_dirt', 2], ['dirt', 2], ['podzol', 1]],
    rim: [['grass_block', 4], ['coarse_dirt', 2], ['dirt', 1]],
    rut: 'dirt_path',
    under: 'coarse_dirt', // a rut cell with something standing on it (a path block may not be under a solid block)
    raiders: [['podzol', 3], ['coarse_dirt', 3], ['rooted_dirt', 1], ['gravel', 1]],
    ash: 'coarse_dirt',
  },
  caravan: {
    post: 'stripped_spruce_log', log: 'spruce_log', planks: 'spruce_planks', fence: 'spruce_fence',
    slab: 'spruce_slab', stairs: 'spruce_stairs', trapdoor: 'spruce_trapdoor', barrel: 'barrel', sack: 'hay_block',
  },
  raiders: { log: 'dark_oak_log', fence: 'dark_oak_fence', banner: 'black_banner', skull: 'skeleton_skull', curb: 'cobblestone_slab' },
  cloth: { rag: 'white_wall_banner', banner: 'white_banner', canvas: 'white_wool', scrap: 'white_carpet' },
  lantern: 'lantern',
  fire: 'campfire',
  crate: 'composter',
  arrow: 'tripwire_hook',
}

// Every mark's footprint (w x h x d), its masses (named parts), its ruts and the tall thing a player looks for.
// The heights climb with the story: 6, 7, 5 (the banner pin holds it low; the white cover makes up for it), 10, 9.
export const MARKS = {
  1: {
    name: 'The Lantern Pole', size: [3, 7, 5],
    pole: { at: [1, 1, 2], height: 4, tip: 2, rags: ['east', 'north'] }, // the wagon's lantern pole, upright
    arm: [0, 2, 2], lantern: [0, 1, 2], // the hook out of the pole and the carter's lantern hanging low on it
    shaft: { log: [2, 1, 0], splinter: [2, 1, 1] }, // a snapped cart shaft lying along the near rut
    board: [1, 1, 4], // a sideboard knocked off, lying flat
    ruts: { axis: 'z', a: 0, gauge: 2, along: [0, 4] },
    sightline: 'the rag at y 4 and the pole tip at y 6 above anything a 1-block plant hides',
  },
  2: {
    name: 'The Lost Wheel', size: [5, 7, 5],
    barrel: [1, 1, 1], staves: [[0, -1, 'north', true], [-1, 0, 'west', true], [-1, 1, 'south', false], [1, -1, 'east', false]],
    sack: [0, 1, 3], // the split sack of grain (a hay bale lying on its side)
    wheel: { hub: [4, 2, 3], axle: 'x' }, // the lost wheel standing on the outside, propped on the stake, broadside to the east (a pale stripped hub, the rim of planks)
    stake: { at: [3, 1, 3], height: 4, tip: 2, rags: ['north', 'south'] },
    ruts: { axis: 'z', a: 1, gauge: 2, along: [0, 4] },
    sightline: 'the wheel broadside from east and west, the two rags broadside from north and south, tip at y 6',
  },
  3: {
    name: 'The Torn Banner', size: [5, 6, 7],
    pole: { fence: [1, 1, 1], lash: [1, 2, 1], banner: [1, 3, 1], rotation: 6 }, // pins: fence, holder, banner
    hoop: { x0: 0, x1: 4, z: 4, y0: 1, top: 4, slump: 'south', steps: 2, foot: 1 }, // the cover over its one standing hoop, its hem on the ground
    torn: [[1, 3, 5], [3, 1, 6], [4, 1, 6], [4, 2, 6], [0, 1, 6], [2, 1, 6]], // holes hacked in the slumped canvas, [x, y, z]
    ribs: [[0, 1, 3], [4, 1, 2]], // a fallen hoop's broken ribs (slabs lying)
    scraps: [[2, 1, 2], [3, 1, 1], [0, 1, 0]], // cut canvas on the ground
    ruts: { axis: 'z', a: 1, gauge: 2, along: [0, 6] },
    sightline: 'a wall of white canvas 5 wide, 3 tall, from every side; the banner over it from the front',
  },
  4: {
    name: 'The Drag', size: [5, 10, 9],
    bed: { x0: 1, x1: 3, z: 6, y0: 1, top: 6 }, // the cart's bed stood on its tail
    axle: { y: 2, z: 7 }, wheels: [[0, 2, 7], [4, 2, 7]],
    shafts: { xs: [1, 3], z: 6, y0: 7, y1: 9 }, // the shafts to the sky, the swingletree across their tops
    rags: [[2, 6, 5, 'north'], [2, 6, 7, 'south']],
    scar: { x0: 0, x1: 1, z0: 0, z1: 4 }, // where the goods were dragged off on a sledge
    sledge: [1, 1, 3], // a dropped bale off the sledge, in the scar
    boards: [[4, 1, 2, 'east'], [3, 1, 0, 'north']], // planks prised off the bed, lying flat
    path: [0, 0, 1], // pin: a dirt path in the ground layer, nothing solid over it
    sightline: 'two shafts and their crossbar at y 9, a white rag at y 6 from north and south, wheels from east and west',
  },
  5: {
    name: 'The Shot Crate', size: [5, 9, 7],
    crates: [[1, 1, 1], [1, 2, 1]], // pins: the composter at (1, 1, 1); a second crate stacked on it
    arrows: [[[1, 1, 1], ['west', 'north']], [[1, 2, 1], ['west', 'east', 'north']]], // the west one on the lower crate is the pin
    hook: [0, 1, 1], // pin: the tripwire hook, facing west, stuck in the crate
    fire: { at: [3, 1, 1], curb: [[4, 1, 1], [3, 1, 0], [4, 1, 0]] },
    standard: { at: [3, 1, 4], logHeight: 4, fenceHeight: 2, arm: [-1, 0], rotation: 4 },
    rag: { low: [3, 2, 5, 'south'], scraps: [[2, 1, 5], [4, 1, 4], [4, 1, 5]] }, // the carter's last rag, torn down the standard to its foot
    stakes: [[0, 1, 6, 2], [2, 1, 6, 1], [4, 1, 6, 2]], // the raiders' stakes: [x, y, z, log height], a fence tip each
    skull: [0, 4, 6],
    sightline: 'a black flag at y 7 over a dark pole, the fire\'s smoke above everything',
  },
}

// Pins (brief, "Levels": the test asserts each block at exactly this cell, unrotated).
export const PINS = {
  1: [[[0, 1, 2], 'lantern']],
  2: [[[1, 1, 1], 'barrel']],
  3: [[[1, 3, 1], 'white_banner'], [[1, 1, 1], 'spruce_fence']],
  4: [[[0, 0, 1], 'dirt_path']],
  5: [[[1, 1, 1], 'composter'], [[0, 1, 1], 'tripwire_hook']],
}

// No furniture, no rooms. The walking line is the player's: from the last mark, eye on the rag, to within 6 blocks
// of the middle column (CaravanTrail.RADIUS): every mark is under 12 across, so its edge always counts.
export const FURNITURE = []
export const WALK = []
