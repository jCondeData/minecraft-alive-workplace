// The Colossus (a Wonder, version B, ROADMAP 52.6), the plan: drawn before any block. Four stages, each only adding to
// the last and each standing and grounded on its own (tools/blueprints/briefs/colossus.md). recipe.mjs draws it in
// passes per stage with the parts library (../parts, grown with ../parts/colossus_b_figure.mjs).
//
// Three rough concepts (pose and massing, base, how the four stages split; one line of story each):
//   A. The Torchbearer: the founder standing upright, one arm raising a caged lantern over the head, a square stepped
//      plinth with the arch through its middle. Story: the founder lights the city. Safe and instantly readable, but
//      it is every harbour statue ever made, the raised arm is a thin stick from the side, and the stages are just
//      "more of the same figure" bottom to top.
//   B. The Seated Lawgiver: the founder enthroned, the charter on the knees, the lantern hung from the throne's high
//      back, the arch running under the seat between the throne's legs. Story: the founder gives the city its law.
//      A strong blocky mass, but a seated figure is wide and squat: it breaks the brief's "under 13 wide from y 14 to 19"
//      at the knees, the face would sit far back from the front, and the throne would eat the copper budget.
//   C. The Wayfarer: the founder as the traveller who first walked this road, caught mid-stride astride it on a
//      two-tier plinth, a long shepherd's staff planted in the right hand whose crook curls out over the shoulder and
//      HANGS the great caged lantern beside the head, the left arm thrown forward pointing down the road ("this way"),
//      a long copper travelling cloak streaming back off the shoulders that weathers green while the stone stays white.
//      Story: the founder did not conquer the place, they found it on foot, and still light and show the way for the
//      travellers the inn draws (the Wonder's own power). Stages: I the plinth, road arch, braziers and plaque; II the
//      robes (boots, striding legs, pleated tunic and robed body to the shoulders); III the arms, staff and the cloak
//      (nearly all the copper); IV the bearded head, the crook and the hanging lantern.
//   Strongest: C. It is a figure in motion (a stride, a pointing arm, a cloak in the wind) where A is a pillar; its
//   silhouette differs on every side (the staff and lantern on the east, the pointing arm on the west, the cloak
//   streaming south), so it reads from any approach; the lantern hangs from a crook rather than being held aloft, so
//   it sits in the top five layers without a stick-arm; and the four stages are four different things that match the
//   four titles. Built: C.
//
// Coordinates: x across the front (west 0 -> east 26, the middle x 13), y up (0 = the layer on the clicked block),
// z from the front (z = 0, north, the road) to the back (z 26). Plot 27 x 47 x 27, top block y 46 (the crook).
// The figure faces north; its right hand (staff) is on the east (+x), its left (pointing) on the west.
//
// Named parts (masses; the critic and the owner can point at them):
//   Step tier   y 0-1, x 0-26, z 0-26 (cut by the road): two courses of dark polished andesite / deepslate, stair
//               treads round its edge; four braziers on its corners (y 2).
//   Plinth      y 2-10, x 2-24, z 2-24: solid stone brick core, weathered low, pilasters a block proud every 4, a
//               cornice of upside-down stairs at y 10. The road arch through it front to back (x 11-15, open y 0-4,
//               the middle three to y 5 under a stepped crown, ceiling y 6), lit by sea lanterns in its side walls.
//   Plaque      seven dark oak wall signs at y 8, x 10-16, z 1 on the plinth's front over the arch, on a sill (y 7) and
//               under a hood (y 9), between the arch's piers (x 8-9 and 17-18).
//   Upper tier  y 11-13, three smooth stone steps (x 4-22, 5-21, 6-20) with stair treads: the plinth's crown (layer
//               13, 15 wide, is the last wide layer), five braziers in its slab rim (four corners and the front middle).
//   Robes       stage II: boots (y 14-15) and striding legs swept hip to ankle (left forward, x 9-11; right back, x 15-17), the
//               pleated tunic flaring from the waist (y 29) to the hem (y 21), the copper belt (y 29), the robed body to
//               the shoulders (y 36) with a smooth stone sash, the wayfarer's satchel on the right hip (y 25-27).
//   Arms        stage III: the right arm down the east side, its hand wrapped round the staff (y 28-30); the left arm
//               thrown forward and down, pointing over the plinth's front edge.
//   Staff       stage III: a dark oak staff at (20, 14-45, 11) with copper bands.
//   Cloak       stage III: copper, from a mantle on the shoulders (y 37) streaming back and down to a hem at y 20,
//               pleated (folds a block proud).
//   Head        stage IV: neck, a bearded head 5 wide (y 38-44), smooth stone hair to the shoulders and beard, emerald pupils at y 40 on the
//               face (z 9), the nose a block in front (z 8, y 38-39), a brow ridge.
//   Lantern     stage IV: the crook (y 46, x 20-23) and the lantern hanging from it: three glowstone (23, 42-44, 11)
//               in glass panes between cut copper posts, copper caps (y 41, y 45) and a copper drip finial.
//
// Walking line: the road runs straight through the arch, z 0 -> 26, between the step tier's stair treads; the plaque
// reads from the road in front, at eye height. Nobody lives or works in it; no furniture.

export const SIZE = [27, 47, 27]
export const STAGES = 4
export const MID = 13
export const TOP = 46

// Palette (60/30/10): stone brick and smooth stone (the plinth) and polished diorite (the figure) / copper (the
// cloak, belt, bands, lantern) / dark oak (the staff, signs), emerald (the eyes), glowstone. Why: one pale stone figure
// on a darker plinth reads at any distance, the darkest stone at the foot; the copper is the only colour and turns
// green with the years as real bronze does on marble.
export const PALETTE = {
  tier: [['polished_andesite', 6], ['polished_deepslate', 2], ['andesite', 1]],
  tierStairs: 'polished_andesite_stairs',
  wall: [['stone_bricks', 12], ['cracked_stone_bricks', 2]],
  wallLow: [['stone_bricks', 5], ['cracked_stone_bricks', 3], ['mossy_stone_bricks', 3]],
  wallStairs: 'stone_brick_stairs',
  dress: 'polished_andesite',
  dressStairs: 'polished_andesite_stairs',
  dressSlab: 'polished_andesite_slab',
  carve: 'chiseled_stone_bricks',
  crown: 'smooth_stone',
  crownSlab: 'smooth_stone_slab',
  light: 'sea_lantern',
  robe: 'polished_diorite',
  robeStairs: 'polished_diorite_stairs',
  fold: 'smooth_stone',
  skin: 'polished_diorite',
  skinStairs: 'polished_diorite_stairs',
  hair: 'smooth_stone',
  hairSlab: 'smooth_stone_slab',
  boot: 'polished_deepslate',
  bootStairs: 'polished_deepslate_stairs',
  copper: 'cut_copper',
  copperStairs: 'cut_copper_stairs',
  copperSlab: 'cut_copper_slab',
  copperFold: 'copper_block',
  copperBrooch: 'chiseled_copper',
  drip: 'lightning_rod',
  staff: 'dark_oak_log',
  staffWood: 'dark_oak_wood',
  sign: 'dark_oak_wall_sign',
  eye: 'emerald_block',
  glow: 'glowstone',
  glass: 'glass_pane',
  fire: 'campfire',
}

export const TIER = { x0: 0, x1: 26, z0: 0, z1: 26, top: 1 }
export const PLINTH = { x0: 2, x1: 24, z0: 2, z1: 24, y0: 2, top: 10 }
export const ROAD = { x0: 11, x1: 15, crown: 5, ceiling: 6 }
export const PIERS = [[8, 9], [17, 18]]
export const PLAQUE = { y: 8, z: 1, xs: [10, 11, 12, 13, 14, 15, 16] }
export const CROWN = { x0: 4, x1: 22, z0: 4, z1: 22, y0: 11, top: 13 }
export const BRAZIERS = [[1, 2, 1], [25, 2, 1], [1, 2, 25], [25, 2, 25], [6, 13, 6], [20, 13, 6], [6, 13, 20], [20, 13, 20], [13, 13, 6]]
export const FIGURE = { cz: 12.5, foot: 14, waist: 29, hem: 21, shoulder: 36 }
export const LEGS = [{ x: 10, ankle: 9, hip: 12, step: -1 }, { x: 16, ankle: 15, hip: 13, step: 1 }] // left forward, right back
export const STAFF = { x: 20, z: 11, y0: 14, y1: 45, bands: [18, 24, 34, 40], hand: [28, 30] }
export const HEAD = { cx: 13, cz: 12, y0: 38, y1: 44, face: 9 }
export const EYES = { y: 40, z: 9, xs: [12, 14] }
export const NOSE = { x: 13, z: 8, ys: [38, 39] }
export const CROOK = { y: 46, x0: 20, x1: 23 }
export const LANTERN = { x: 23, z: 11, glow: [42, 43, 44], capLow: 41, capHigh: 45 }
