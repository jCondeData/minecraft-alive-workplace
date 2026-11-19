// Gem Grotto (gem_grotto, gem_grotto_2), the plan: drawn before any wall. Brief: tools/blueprints/briefs/gem_grotto.md.
//
// Three rough concepts:
//   A. "The Split Geode": the building IS a geode. One tall egg of dark crust (15 x 11 x 15), its front sliced off so
//      the cut face shows the three bands every geode has (basalt crust, white calcite, purple amethyst) round a
//      glazed crystal window and a nether-wood door. No roof: the egg is the roof. Inside, one domed room round a
//      glowing lava crucible, crystals on the ceiling. Story: the Gem Grower cracked a giant geode open and moved in.
//      Level II: a second, lower egg fused to its back (a geode cluster), the four crystal cores in its round room.
//   B. "The Miner's Adit": a stone portal into a man-made hill, a gabled stone gatehouse in front, the grotto a
//      barrel-vaulted gallery behind it. Story: a gem mine's mouth. Reads well but is a house with a hill behind it,
//      and the hill is a lot of blocks nobody sees from inside.
//   C. "The Kiln Chapel": a tall stone hall with a steep copper roof, the lava crucible in an apse at the back like
//      an altar, lancet windows. Story: gems are grown like a rite. Handsome, but a church that happens to hold lava.
//   A is strongest: its silhouette (an egg with crystals breaking out) can't be mistaken for any other workplace, its
//   section bands say "geode" from across a field, the materials (basalt, calcite, amethyst) are the ones the game's
//   own geodes are made of, and its round room puts the lava at the heart, where the grower works round it on every
//   side. B and C are buildings with a gem room in them; A is the gem. Built: A.
//
// Coordinates: x across the front (west 0 -> east 14), y up (0 = the floor layer on the clicked block), z from the
// front (z = 0, north) back. The pinned cells (the tests name them) are kept: the stonecutter (4, 1, 2), the six lava
// sources x 5-7, z 5-6 at y 1 behind plain glass at (5..7, 1, 4), the amethyst block (0, 1, 6) with its cluster at
// (1, 1, 6) facing east, and Level II's four cores (4, 1, 13), (8, 1, 13), (4, 1, 16), (8, 1, 16).
//
// Named parts (masses):
//   Geode     the main egg: centre (7, 0, 6), radii 7.5 across, 10 up, 8.5 deep; x 0..14, z 0..14, crown y 9.
//             Shell two layers thick (crust, amethyst lining); three at the cut face (crust, calcite band, amethyst).
//   Cut face  the front slice (z <= 0 removed): the bands show round the face at z = 1; the face itself is a crystal
//             window of glass panes round a calcite door case, the door in the middle.
//   Crucible  the lava pool in a deepslate kerb in the middle of the floor (x 4..8, z 4..7, y 1..2), plain glass to
//             the front, the kerb's top at y 2 round every lava cell (the tumblestone ledges).
//   Niche     the amethyst block in the west wall (0, 1, 6) with its cluster, a two-high alcove cut into the lining.
//   Spires    amethyst crystals breaking out of the crust (west shoulder, east flank, crown), calcite collars.
//   Cluster   (Level II) a second, lower egg fused to the back: centre (6, 0, 16), radii 5.5 across, 7 up, 6 deep;
//             z to 21. Its round room holds the four cores; a wide arch where the two eggs meet.

export const SIZE = { 1: [15, 13, 15], 2: [15, 13, 22] }

// Palette (60/30/10): dark crust (smooth basalt with cobbled deepslate and a little tuff, mossier low down) / calcite (the middle band, the
// mullions, the collars, pale floor inlay) / amethyst (the lining, crystals and spires). Why: they are the game's own
// geode layers, so the building reads as one; the crust is dark so the bands and the window glow against it. The
// floor and the crucible are tuff and deepslate (cool grey under the purple). The only wood is nether wood (crimson),
// which never burns, at the door.
export const PALETTE = {
  crust: [['smooth_basalt', 6], ['cobbled_deepslate', 2], ['tuff', 1]], // cool dark grey: the bands glow against it
  crustLow: [['cobbled_deepslate', 4], ['tuff', 2], ['smooth_basalt', 2], ['mossy_cobblestone', 1]], // y 1-2 weathering
  band: [['calcite', 9], ['diorite', 1]],
  lining: [['amethyst_block', 5], ['calcite', 1]],
  crystal: 'amethyst_block',
  bud: 'amethyst_cluster',
  floor: [['polished_tuff', 4], ['tuff_bricks', 3], ['chiseled_tuff', 1]],
  coreFloor: [['deepslate_tiles', 3], ['polished_deepslate', 2]],
  kerb: 'deepslate_tiles',
  kerbCorner: 'chiseled_deepslate',
  core: 'deepslate', // Cobblemon 1.8 swaps these for crystal cores
  lavaGlass: 'glass', // plain glass blocks, the brief's
  glass: 'glass_pane',
  mullion: 'calcite',
  step: 'polished_tuff_stairs',
  wood: 'crimson', // the door: nether wood, never burns
  lamp: 'lantern',
  chain: 'chain',
  bench: 'polished_tuff_slab',
  benchBase: 'polished_deepslate',
  boulder: [['tuff', 2], ['calcite', 1], ['cobbled_deepslate', 1]],
}

export const MASSES = {
  geode: { c: [7, 0, 6], r: [7.5, 10, 8.5] },
  cluster: { c: [6, 0, 16], r: [5.5, 7, 6] }, // Level II
  cutZ: 0, // everything at z <= cutZ above the floor is sliced off: the face is z = 1
  shell: 2, // layers 1..2 are walls (crust, lining); deeper is the room
  faceBand: 2, // ... except at z <= faceBand, where a third layer shows the cut face's three bands
}

export const ROOMS = [
  { name: 'Grotto', use: "the Gem Grower's room: the stonecutter, her chests, the crucible, the niche", level: 1, box: [2, 1, 2, 12, 8, 12] },
  { name: 'Core chamber', use: 'four crystal cores, each with room for its Type Gem Block and cluster', level: 2, box: [2, 1, 12, 10, 6, 20] },
]

export const OPENINGS = {
  door: { at: [7, 1, 1], facing: 'south', step: [[6, 0, 0], [7, 0, 0], [8, 0, 0]] },
  doorCase: 'calcite jambs at x 6 and 8 (y 1..3) and a lintel at (7, 3, 1); glass panes in the rest of the face',
  niche: { block: [0, 1, 6], cluster: [1, 1, 6], facing: 'east', alcove: [[2, 1, 6], [2, 2, 6]], lintel: [1, 2, 6] },
  arch: 'where the eggs meet (Level II): the shells open into one another',
}

export const JOB = {
  stonecutter: [4, 1, 2],
  lava: [[5, 5], [6, 5], [7, 5], [5, 6], [6, 6], [7, 6]], // [x, z] at y 1
  lavaY: 1,
  glass: [[5, 4], [6, 4], [7, 4]],
  cores: [[4, 1, 13], [8, 1, 13], [4, 1, 16], [8, 1, 16]],
}

// Every piece the room holds (fixtures: the game uses each as itself; no furniture spots, so nothing is swapped).
export const FURNITURE = [
  { room: 'Grotto', kind: 'fixture', part: 'stonecutter', pos: [4, 1, 2], facing: 'north', note: 'the job block, by the door' },
  { room: 'Grotto', kind: 'fixture', part: 'chest', pos: [12, 1, 7], facing: 'west', note: "her chest, against the east wall, 8 across of the cutter" },
  { room: 'Grotto', kind: 'fixture', part: 'chest', pos: [12, 1, 8], facing: 'west', note: 'a second chest beside it' },
  { room: 'Grotto', kind: 'fixture', part: 'bench', cells: [[12, 1, 5], [12, 1, 6]], note: 'a sorting bench: polished tuff slabs on deepslate, lantern-lit' },
  { room: 'Grotto', kind: 'lamp', part: 'hanging', pos: [10, 7, 7], chain: 'from the lining', note: 'over the chests' },
  { room: 'Grotto', kind: 'lamp', part: 'hanging', pos: [3, 6, 4], chain: 'from the lining', note: 'over the cutter' },
  { room: 'Core chamber', kind: 'lamp', part: 'hanging', pos: [6, 5, 15], chain: 'from the crown', note: 'between the cores (Level II)' },
]

// The walking line: the step -> the door -> past the cutter -> round the crucible on every side (the grower's ledges)
// -> the chests -> (Level II) through the arch -> round the cores.
export const WALK = [
  [7, 0, 0], [7, 1, 1], [7, 1, 2], [6, 1, 3], [5, 1, 3], [4, 1, 3], [3, 1, 4], [3, 1, 5], [3, 1, 6], [3, 1, 7],
  [4, 1, 8], [6, 1, 8], [8, 1, 8], [9, 1, 7], [9, 1, 6], [9, 1, 5], [10, 1, 6], [11, 1, 7],
  [6, 1, 10], [6, 1, 12], [6, 1, 14], [5, 1, 14], [3, 1, 14], [6, 1, 16], [6, 1, 18],
]
