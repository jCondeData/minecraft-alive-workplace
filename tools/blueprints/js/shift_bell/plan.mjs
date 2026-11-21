// The Shift Bell (`story/shift_bell`, one level), the plan: drawn before any block. ROADMAP 49 (rebuild); the brief is
// tools/blueprints/briefs/shift_bell.md. recipe.mjs assembles it from the parts library (../parts, bells.mjs).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Anvil Lantern. A stone lantern on a single stem, and the stem is the smith's own anvil: a stepped dark
//      dais (y 0), the anvil alone in the pinched waist (y 1) with four lanterns hanging round it from the head's
//      corners, a flared head of corbels (y 2) carrying a four-window bell chamber (y 3), a weathered copper pyramid
//      cap (y 4) and a copper spire (y 5). Plan: a 3 x 3 square, every face the same. Story: the smith who asked for
//      "a bell to ring the work in and out" set it on what he works on all day, so the shifts ring out of an anvil.
//   B. The Copper Campanile. A little square tower: four stone piers, a two-tall open belfry, a copper pyramid roof.
//      Story: a chapel bell-cote set down in the yard. It reads at once, but it is the belfry every village already
//      has (the hall's own, the schoolhouse's), and in 3 x 3 it is a stone box with holes: no waist, no shadow.
//   C. The Forge Bell. A blackstone hearth box with a campfire under an iron grate and the bell hung in the smoke over
//      it, a chimney cap. Story: the bell rings over a fire the smith never lets go out. Strong at night, but it reads as
//      a brazier, not a bell, its smoke hides the bell from the hall, and a player who steps up to ring it stands in fire.
// Strongest: A. Its outline is the one thing in the village shaped like it (wide foot, a waist pinched to one block,
// a wide head, a point: an hourglass, which is what a shift bell is), it tells the story of who asked for it without
// a word, it is the same on all four sides (the template is never turned, and the hall may be on any side) and from
// above (a copper pyramid round a spire), and at night its four lanterns light the yard round it. It fills the 3 x 6 x 3
// plot exactly.
//
// Coordinates: x west 0 -> east 2, y up (0 = the first air layer on the natural ground; nothing below it), z north
// (z = 0) -> south 2. Never rotated or mirrored, so no front: all four faces are drawn alike.
//
// Masses (named parts the owner's feedback can point at), bottom up:
//   Dais      y 0: dark stone corner blocks, a stair step in the middle of each face rising toward the anvil (a
//             player steps up on any side to ring the bell by hand), the anvil's footing block in the middle.
//   Waist     y 1: the smith's anvil alone in the middle (its horn east-west), the four corners open: from every side
//             you look through under the head. A lantern hangs in each corner from the head's corner block.
//   Head      y 2: the chamber's floor. A ring of upside-down stairs flares out from the waist (corbels), corners
//             full dark blocks the lanterns hang from; a gold block in the middle under the bell (the village's 128
//             gold ingots, glinting through the windows).
//   Chamber   y 3: four slim stone posts at the corners, the four face windows open, the bell in the middle at (1, 3, 1)
//             hanging from the cap's middle block (attachment ceiling), the cell the code rings.
//   Cap       y 4: a weathered copper pyramid (stairs rising inward round a full copper block that carries the bell),
//             y 5: a copper lightning rod as the spire, the highest point.

export const SIZE = [3, 6, 3] // w, h, d: the brief's hard limit (ReformStories.fits checks a 3 x 3 x 6 of air)

// Palette (60/30/10): dark forged stone (deepslate: dais, corbels, posts) / weathered copper (cap and spire, waxed so
// it never turns) / gold and iron (the bell over its gold block, the anvil, the lanterns). Why: the smith's colours,
// iron-dark at the foot and green copper at the top (darkest low, lighter going up), so the gold bell is the one warm
// thing in the middle and the eye goes to it from any side.
export const PALETTE = {
  dais: [['polished_deepslate', 5], ['deepslate_tiles', 3], ['cracked_deepslate_tiles', 1]],
  step: 'polished_deepslate_stairs',
  footing: 'deepslate_tiles',
  stem: 'anvil',
  corbel: 'deepslate_brick_stairs',
  corbelCorner: 'deepslate_bricks',
  post: ['polished_deepslate_wall', { up: 'true', north: 'none', south: 'none', east: 'none', west: 'none', waterlogged: 'false' }], // a lone post (finish() would say the same)
  floor: 'gold_block',
  roof: { full: 'waxed_weathered_cut_copper', stairs: 'waxed_weathered_cut_copper_stairs', slab: 'waxed_weathered_cut_copper_slab' },
  spire: 'lightning_rod',
  lamp: 'lantern',
}

export const MASSES = {
  dais: { x0: 0, z0: 0, x1: 2, z1: 2, y: 0 },
  waist: { at: [1, 1, 1], facing: 'north' }, // anvil facing north: its horn points east-west
  head: { x0: 0, z0: 0, x1: 2, z1: 2, y: 2 },
  chamber: { x0: 0, z0: 0, x1: 2, z1: 2, y: 3 },
  cap: { cx: 1, cz: 1, half: 1, y: 4 },
}

// The cell the code reads: ReformStories.placeBell returns origin.offset(1, 3, 1). Never move it.
export const BELL = { at: [1, 3, 1], attachment: 'ceiling', facing: 'north' }

export const ROOMS = [] // none: a yard piece, no rooms

export const OPENINGS = {
  // the four chamber windows, one in the middle of each face at the bell's height: the bell seen (and rung) from any side
  windows: [[1, 3, 0], [1, 3, 2], [0, 3, 1], [2, 3, 1]],
  // the four steps of the dais, one per face
  steps: [[1, 0, 0, 'south'], [1, 0, 2, 'north'], [0, 0, 1, 'east'], [2, 0, 1, 'west']],
}

export const FURNITURE = [] // none (the brief: no furniture spots; furniture.json is {"spots": []})

// The walking line: none needed (rung by code). A player walks up on any side onto a step at y 0 and rings the bell
// through the window above it, about two blocks from their eyes.
export const WALK = [[1, 0, -1], [1, 0, 0]]

// Lanterns hang under the head's four corners (the head's corner blocks are full, so a hanging lantern holds).
export const LIGHTS = [[0, 1, 0], [2, 1, 0], [0, 1, 2], [2, 1, 2]]
