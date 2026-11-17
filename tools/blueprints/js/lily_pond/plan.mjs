// The Lily Pond, the plan: drawn before any block (tools/blueprints/briefs/lily_pond.md, REBUILD.md). recipe.mjs then
// assembles it from the parts library (../parts) in passes.
//
// Three rough concepts first (massing, roof, plan, one line of story each):
//   A. The Market Basin. One low mass: a dressed stone basin with clipped corners, a kerb seat all round, an urn of
//      flowers on a plinth in the middle; no roof; a symmetrical cross plan with a path to each side. Story: the
//      square's tidy pond, kept by the council. Safe and neat, but flat: nothing rises above 2 blocks, and a
//      symmetrical stone tray reads as a fountain without water jets, not as a pond.
//   B. The Twin Pools. Two small pools at different levels joined by a spill of stones, a little arched plank crossing
//      between them; no roof; a figure-of-eight plan. Story: a scholar's garden. Too much for 9 x 7 (each pool ends up
//      3 x 3), the crossing steals the Footbridge's identity, and the drop between pools fights the rule that no water
//      may flow.
//   C. The Kingfisher Pond. A natural pond set into a low knoll: a ring of lawn and flowers at grade, a grassy bank, a
//      dressed stone kerb at the front where people sit, mossy field boulders heaped at the back-east, and an old
//      flowering azalea on the back-west bank leaning its crown out over the water (the "roof": a canopy, not a
//      building's). A timber dipping stage on the east with a lantern post; a second lantern hangs from the branch over
//      the lily pads. Story: the old field pond the first villagers watered their beasts at, which the village kept
//      when the field became a green: the kingfisher still perches on that branch.
// Strongest: C. It has a silhouette (the tree is the hero at 8 blocks, three times the pond's height, against a low
// front), asymmetric balance (canopy back-west, boulders and stage east), real depth in layers from the lawn up to the
// crown, and parts with a use (the kerb to sit on, the stage to dip a bucket from). A is the safe one; C is built.
//
// Coordinates: x across the front (west 0 -> east 8), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back (z = 6). The builder's path starts at (4, -, -1): the path stones at z = 0 meet it.
//
// Masses (named parts the owner's feedback can point at):
//   Lawn     the ring at grade (y 0): grass with blue and white flowers, path stones at the front middle.
//   Bank     the knoll's rim (y 1): grass on the west and between kerb and water, moss at the back.
//   Kerb     the dressed front edge (x 2..6, z 1, y 1): stone bricks, worn and mossy, a seat facing the water.
//   Pond     15 still water sources at y 1 on a mud bed (y 0): a kidney shape, deepest-looking in the dark mud.
//   Stage    the dipping stage on the east (x 6..7, z 2..4, y 2): spruce slabs on log posts standing in the water and a
//            plank pier, a step up from the east lawn, a lantern on a two-high post at its front corner.
//   Boulders a low heap of field stones at the back-east (x 6..7, z 5..6) stepping up to y 3, capped with slabs.
//   Tree     the old azalea on the back-west bank (trunk at x 1, z 5): a trunk to y 5, a branch reaching east over the
//            water at y 4, a ragged crown y 3..7 flowering pink, a lantern hung from the branch over the pads.

export const SIZE = [9, 8, 7] // w, h, d (the brief: at most 9 x 8 x 7)

// Palette (60/30/10): greens and earth (lawn, bank, moss, leaves, the mud bed under the water) / field stone (rubble
// base, dressed kerb, boulders) / spruce and flowers (the stage, blue and white flowers, the pink blossom). Why: a pond
// should read as a piece of landscape first; the stone says someone keeps it, the spruce says someone uses it, and the
// pink azalea over blue water is the one bright note. Darkest at the base (cobble, mud), lighter going up.
export const PALETTE = {
  base: [['cobblestone', 5], ['mossy_cobblestone', 3], ['andesite', 1]],
  kerb: [['stone_bricks', 5], ['mossy_stone_bricks', 3], ['cracked_stone_bricks', 1]],
  rock: [['mossy_cobblestone', 4], ['cobblestone', 3], ['stone', 2], ['andesite', 1]],
  bed: [['mud', 4], ['clay', 1]],
  lawn: 'grass_block',
  moss: 'moss_block',
  path: [['mossy_stone_bricks', 2], ['stone_bricks', 1]], // full blocks: a one-block step up to the kerb, not a dead 1.5
  cap: 'mossy_cobblestone_slab',
  wood: 'spruce', // the stage: logs, planks, slabs, the step, the lamp post
  log: 'oak_log', // azalea trees grow oak wood
  leaves: [['flowering_azalea_leaves', 3], ['azalea_leaves', 2]],
  flowers: ['cornflower', 'azure_bluet', 'lily_of_the_valley'],
  pad: 'lily_pad',
}

// The ground plan, one row per z (front first), one letter per x. Every column's blocks from y 0 up:
//   .  nothing                         f  lawn: grass (y 0), a flower (y 1)       g  lawn: grass only
//   p  path stone: a block (y 0)        s  stone under the stage's step (y 0)      r  a low field stone (y 0)
//   K  kerb: base (y 0), dressed stone (y 1)                                     G  bank: base, grass (y 1)
//   M  bank: base, moss (y 1)          R  rock bank: base, field stone (y 1)      W  pond: mud bed, water (y 1)
//   L  a stage post in the pond: mud bed, spruce log (y 1)                       D  stage pier: base, spruce (y 1)
//   T  the tree's trunk: base, logs from y 1
export const PLAN = [
  '.ffpppff.', // z 0: the front lawn, the path stones in the middle
  'fGKKKKKGf', // z 1: the kerb between grassy shoulders
  'fGGWWWLDg', // z 2
  'gGWWWWWDs', // z 3: the stage's step comes up from the east lawn here
  'fGWWWWLDg', // z 4
  'gTGWWWRRr', // z 5: the trunk, the boulders' foot
  '.rRMMMRRr', // z 6: the back bank, moss under the crown
]

// The water: 15 sources (the brief asks at least 10, and 5+ for the take-down tests), every one shut in by water or a
// full drawn block on its four sides and over a mud bed: the drawing holds it on any ground.
export const WATER_Y = 1

export const STAGE = {
  pier: { 2: 'log', 3: 'planks', 4: 'log' }, // x 7, y 1: by z
  deck: [[6, 2], [6, 3], [6, 4], [7, 3], [7, 4]], // [x, z] at y 2: spruce slabs
  step: [8, 1, 3, 'west'], // a spruce stair up from the lawn
  lamp: [7, 2, 2, 2], // x, y, z, post height: a lantern on top
}

export const BOULDERS = {
  // a low heap: one full stone with a slab on it, slabs stepping down round it to the pond and the corner
  stones: [[6, 2, 6]],
  caps: [[6, 3, 6], [6, 2, 5], [7, 2, 5], [7, 2, 6], [2, 2, 6]],
}

export const TREE = {
  logs: [[1, 1, 5], [1, 2, 5], [1, 3, 5], [1, 4, 5], [1, 5, 5], [2, 4, 5, 'x'], [3, 4, 5, 'x']],
  // the crown, as rows [y, z, x0, x1]: ragged, heavier over the water than over the bank, two leaves hanging lower
  crown: [
    [3, 4, 2, 2], [3, 5, 4, 4],
    [4, 3, 3, 3], [4, 4, 0, 3], [4, 5, 0, 0], [4, 5, 4, 4], [4, 6, 0, 2],
    [5, 3, 2, 3], [5, 4, 0, 4], [5, 5, 0, 5], [5, 6, 0, 3],
    [6, 4, 2, 3], [6, 5, 0, 3], [6, 6, 1, 2],
    [7, 5, 1, 2], // the crown's top tuft: a domed top, not a flat lid
  ],
  lantern: [3, 3, 5], // hung under the branch, over the pads
}

export const PADS = [[2, 3], [4, 4], [5, 2], [3, 5]] // [x, z] on the water

// Plants on the bank (y 2), each in its own soil (grass or moss under it): ferns and lilies of the valley under the
// tree, an azalea bush on the moss, a cornflower on the east shoulder.
export const PLANTS = [
  [1, 2, 1, 'fern'], [7, 2, 1, 'cornflower'], [2, 2, 2, 'azure_bluet'], [1, 2, 3, 'lily_of_the_valley'],
  [1, 2, 4, 'fern'], [2, 2, 5, 'fern'], [3, 2, 6, 'fern'], [4, 2, 6, 'flowering_azalea'], [5, 2, 6, 'moss_carpet'], [8, 1, 4, 'fern'],
]

// No rooms and no furniture (the brief: no job block, no storage, no furniture kinds needed). The walking line: in from
// the builder's path start at the front, along the path stones to the kerb; round the east lawn and up the step onto
// the stage; round the west lawn under the crown.
export const ROOMS = []
export const FURNITURE = []
export const WALK = [
  [4, 0, -1], [4, 0, 0], // the kerb at (4, 1, 1): sit facing the water
  [7, 0, 0], [8, 0, 1], [8, 0, 2], [8, 1, 3], [7, 2, 3], [6, 2, 3], // the stage
  [1, 0, 0], [0, 0, 1], [0, 0, 3], [0, 0, 5], // the west lawn, under the crown
]
