// The Footbridge (`footbridge`, one tier), the plan: drawn before any block. ROADMAP 49 (lane B); the brief is
// tools/blueprints/briefs/footbridge.md. recipe.mjs assembles it from the shared parts library (../parts) in passes.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Lamp Gate Humpback. One steep rubble arch, a stair a block, springing from the banks of a bending stream
//      and climbing to a crown three steps up; sloping stone coping for parapets; at the crown a little roofed lych-gate
//      of spruce with a lantern hung over the walk; newel posts with lanterns at both ends. Plan: the bridge a narrow
//      3-wide spine across a 9 x 7 plot, the 15 sources an S-bend running under it from west to east, banks of grass,
//      fern and moss. Story: the crossing the village lights at dusk, so nobody misses the top step in the dark.
//   B. The Covered Footbridge. A flat timber deck on trestles over a straight stone leat, under a gabled shingle roof
//      the whole length, railings and a bench between the posts. Story: a kissing bridge where you shelter from rain.
//      A small house on stilts: in 9 x 7 x 8 the roof hides the water, and from the front it is a dark doorway.
//   C. The Stepping-Stone Ford. A round lily pond, a path of flat stones and a single plank across it, reeds and a
//      boulder heap. Story: the old way over before anyone built. Lovely from above, but a flat silhouette nobody
//      sees from across the village, and no crossing a villager walks with dry feet.
// Strongest: A. Its outline is the one you know a bridge by from twenty blocks away: the arc of the parapets with the
// water showing through the arch from both sides, and one vertical (the gate's peaked hat and its lantern) exactly at
// the crown, where the walk is highest. Every part says what it is for: the newels mark where to step up, the coping is
// what you lean on to look down at the water, the gate lights the top. B hides the water; C has no shape.
//
// Coordinates: x across the front (west 0 -> east 8), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, the lane) to the back. 9 x 8 x 7 (brief: at most 9 x 8 x 9). Crossed from the front (z 0) to the
// back (z 6) over water running side to side under rows z 2..4, as the stream test lays its stream. No door: the
// lane arrives at (4, 0, -1), one block in front of the middle of the z = 0 edge, onto the bridge's first step.
//
// Masses (named parts the owner's feedback can point at):
//   Stream     15 still sources at y 0 in an S-bend: row z 2 x 2..5 (from the west), z 3 x 1..7 (the full width),
//              z 4 x 3..6 (out to the east). Under the arch all of x 3..5, z 2..4 is water, so the pinned three
//              (3, 0, 2..4) lie under the bridge's west face. Every source is shut in by the banks and the bridge's
//              abutments at y 0; none touches the plot's edge; nothing stands on the three pinned cells.
//   Banks      the rest of the y 0 layer: grass, worn to mossy rubble here and there where it meets the water.
//   Bridge     x 3..5, z 0..6, a humpback: walk on x 4 (stone-brick stairs, one a block: y 0, 1, 2, crown block y 2
//              walked on at y 3, down again the same way); parapets on x 3 and x 5 of rubble, coped with sloping stone
//              brick stairs one block over the walk; a one-course arch over z 2..4 (upside-down stone-brick springers
//              at y 1 on z 2 and z 4, open at z 3 under the crown, a chiselled key at the crown on both faces).
//   Newels     the parapets' four ends (x 3 / x 5 at z 0 and z 6): rubble foot, two courses of stone-brick wall,
//              a lantern on top (y 3): light at both ends.
//   Lamp Gate  at the crown (z 3): slender spruce fence posts on the coping (x 3 and x 5, y 4..5), a stripped spruce
//              beam between them (y 6), a little hipped dark-oak hat (stairs all round at y 6, x 2..6, z 2..4; a slab
//              ridge at y 7) and a lantern hung under the beam over the walk (4, 5, 3): the one tall thing, the hero.
//   Plantings  ferns, blue orchids (a waterside flower), oxeye daisies and grass in clusters on the banks, flowering
//              azaleas at the two back corners, a mossy boulder heap at the front east corner, two lily pads.

export const SIZE = [9, 8, 7] // w, h, d (brief: at most 9 x 8 x 9)

// Palette (60/30/10): grey stone (the rubble body and banks' lip 60 %, dressed stone-brick trim: walk, springers,
// coping, newels) / green banks (grass, moss, ferns 30 %) / accents: the spruce-and-dark-oak gate, lantern light, the
// water's blue and the flowers (10 %). Why: rubble with moss reads as old and local, a bridge the village dug its own
// stone for; the dressed stone draws the two lines that matter (the arch and the coping's arc) a shade lighter than
// the rubble; the one warm wooden thing sits at the crown so the eye goes there.
export const PALETTE = {
  rubble: [['cobblestone', 5], ['mossy_cobblestone', 3], ['stone', 1], ['andesite', 1]],
  lip: [['grass_block', 4], ['mossy_cobblestone', 3], ['cobblestone', 1]], // a soft bank, stone where the water wears it
  soil: 'grass_block',
  walk: 'stone_brick_stairs',
  walkFull: 'stone_bricks',
  ring: 'stone_brick_stairs',
  key: 'chiseled_stone_bricks',
  coping: 'stone_brick_stairs',
  copingFull: 'stone_bricks',
  newel: 'stone_brick_wall',
  lamp: 'lantern',
  post: 'spruce_fence', // slender: the gate is a lamp standard, not a doorway
  beam: 'stripped_spruce_log',
  hat: { stairs: 'dark_oak_stairs', slab: 'dark_oak_slab' },
  pad: 'lily_pad',
  boulder: [['mossy_cobblestone', 2], ['cobblestone', 1]],
  boulderCap: 'mossy_cobblestone_slab',
}

export const MASSES = {
  plot: { x0: 0, z0: 0, x1: 8, z1: 6 },
  stream: { y: 0, rows: [[2, 2, 5], [3, 1, 7], [4, 3, 6]] }, // [z, x0, x1]
  bridge: { x0: 3, x1: 5, z0: 0, z1: 6, y: 0, span: [2, 4], newelHeight: 2 },
  gate: { x0: 3, x1: 5, z: 3, y: 4 },
  pads: [[1, 3], [6, 4]], // [x, z] on the water, clear of the bridge
  boulders: { cells: [[8, 1, 1]], caps: [[7, 1, 0], [8, 1, 2]] }, // one field stone, two slabs heaped against it
  corners: [[0, 0], [8, 0], [0, 6], [8, 6]], // left out: the banks read rounded, not a square cake
  // [x, z, plant] on the grass (y 1); clusters, never confetti, the lane's step (x 4) left clear
  plants: [
    [2, 0, 'fern'], [1, 0, 'short_grass'], [0, 1, 'oxeye_daisy'], [1, 1, 'oxeye_daisy'], // front west: a daisy patch
    [6, 1, 'blue_orchid'], [7, 1, 'blue_orchid'], [6, 0, 'short_grass'], // front east, by the field stone
    [0, 2, 'fern'], [8, 4, 'fern'], [0, 4, 'short_grass'], // the side banks
    [1, 5, 'blue_orchid'], [1, 6, 'fern'], [2, 6, 'short_grass'], // back west, by its azalea
    [7, 5, 'oxeye_daisy'], [7, 6, 'fern'], [6, 6, 'short_grass'], // back east
  ],
  shrubs: [[0, 5, 'flowering_azalea'], [8, 5, 'flowering_azalea']], // [x, z, block] at y 1 on grass
}

// Rooms: none (an open crossing). The space under the gate is the Crown.
export const ROOMS = [
  { name: 'Crown', use: 'stop at the top, lean on the coping, look down at the water', box: [4, 3, 3, 4, 4, 3] },
]

// Openings: no walls, no door. The lane meets the first step at the front; the back is a way down the same.
export const OPENINGS = { approach: { lane: [4, 0, -1], step: [4, 0, 0], back: [4, 0, 6] } }

// Furniture: none (a decoration; nobody sits or works at it, and no furniture spot asks to be filmed).
export const FURNITURE = []

// Lights: the four newel lanterns (drawn by the humpback part) and the gate's lantern over the crown.
export const LIGHTS = { newels: 4, gate: [4, 5, 3] }

// The walking line: the lane -> the first step -> up three stairs -> the crown under the gate (head clear: the lantern
// hangs at y 5, a walker's head is at y 4) -> down three stairs -> the back bank. Each step rises half a block over
// the last stair's tread (stairs, then the crown's full block), so a villager walks it without jumping.
export const WALK = [[4, 0, -1], [4, 0, 0], [4, 1, 1], [4, 2, 2], [4, 3, 3], [4, 2, 4], [4, 1, 5], [4, 0, 6], [4, 0, 7]]
