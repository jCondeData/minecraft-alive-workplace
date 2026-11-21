// The Colossus (a Wonder, 52.6 version A), the plan: drawn before any block. recipe.mjs raises it stage by stage.
//
// Three rough concepts first (the brief fixes: 47 high, a 5 x 6 road arch through the whole depth, a base about 13
// high and nothing 13 or wider above it to y 19, the face near the front with emerald eyes at y >= 34, glowstone caged
// in glass in the top five layers, nine braziers, seven plaque signs in one row facing the road):
//   1. "The Torchbearer": a robed figure standing square on a tall cubic pedestal, right arm up with the lantern, left
//      hand on a tablet. Base a plain die with the arch cut through it, braziers on its corners. Stages: pedestal /
//      robe to the waist / torso and arms / head and lantern. Story: the Founder as a lighthouse keeper. Safe and
//      legible, but it is every harbour statue ever made: a column of robe on a box, the arch just a hole.
//   2. "The Enthroned Founder": seated on a throne on a broad terrace, a lantern on a staff beside the throne, the
//      road arch under the throne's footstool. Stages: terrace / throne and lap / arms and staff / head and lantern.
//      Story: the Founder still presiding over the village. Rich in silhouette low down, but a seated figure gives up
//      half the height the plot pays for, and the staff's lantern would sit beside the head, not raised by it.
//   3. "Astride the Road": the legend of the old Colossus told in this village's terms. The Founder stands with feet
//      planted wide, one each side of the road, so the road arch runs between the statue's own feet; a knee-length
//      tunic, a copper cloak that falls from the shoulders down the back to pool on the deck (the figure's third
//      support, as a marble statue's drapery is), the right arm thrown up with the lantern that lit the first night,
//      the left hand holding the rolled founding plan against the hip. The base is a stepped dark plinth that reads
//      as a triumphal gate: the arch with its keystone, the seven-sign plaque as the gate's inscription over it, the
//      braziers on four corner piers, four on the deck's corners and the ninth as an eternal flame between the feet.
//      Stages: the gate and plinth / the stance and tunic (the robes) / the arms and the cloak / the head and lantern.
//      Story: whoever walks the road passes under the Founder's stride and the Founder's light.
// Strongest: 3. It is the only one where the arch is part of the figure's meaning rather than a tunnel through a box,
// it has the boldest silhouette (a wide-legged triangle under a raised arm, the cloak's long slope behind), and each
// stage adds one whole readable thing: a gate, a standing body, a cloaked body with arms, a face and a light. The
// copper cloak turns green over the years while the marble stays white: the Wonder weathers like a real bronze.
// The cost: the stance must stay inside 12 columns from y 14 to y 19 (feet x 9..19) and the cloak gathers to 11 wide
// at its hem; both are planned below.
//
// Coordinates: x across the front (0 west .. 28 east, the middle x = 14), y up (0 = the layer on the clicked block),
// z from the front (z = 0, the road side, north) to the back (z = 28). The figure faces north (toward z = 0); its right
// hand is east (+x).
//
// Named parts (for the critic and the owner):
//   Steps        stage I. Three stair-edged courses all round (y 0..2), cut by the road.
//   Piers        stage I. Four corner piers x 0..2 / 26..28, z 0..2 / 26..28, y 0..3, a brazier on each.
//   Gate die     stage I. The plinth's body x 3..25, z 3..25, y 3..10: dark brick between proud pilasters, a copper frieze at
//                y 10 all round, the road arch x 12..16 through it (crown y 5 open, ceiling y 6), voussoirs and a
//                keystone at both mouths, sea lanterns let into the vault's crown.
//   Plaque       stage I. Seven dark oak wall signs at y 8, x 11..17, z 2, hung on the die over the arch (the gate's
//                inscription, read from the road).
//   Cornice/deck stage I. An overhanging cornice at y 11, the smooth stone deck at y 12 (x 2..26, z 2..26), a low
//                balustrade at y 13 with braziers at its four corners and the eternal flame at (14, 13, 7).
//   Stance       stage II. Sandalled feet, left foot forward (x 9..11), right foot back (x 17..19), bare shins y 14..21.
//   Tunic        stage II. A pleated knee-length tunic y 22..26, a copper belt y 27 with a chiseled
//                buckle, the torso y 28..33 with a sash across the chest from the left shoulder.
//   Cloak        stage III. A copper mantle over the shoulders (y 34; smooth stone slabs over the tunic in front, chiseled
//                clasps) falling behind as one sheet: out to 15 wide like a bell (y 21..29), its edges curling forward,
//                gathered to 11 wide by y 19, its last rows sweeping back to z 23 as a train that pools on the deck;
//                folds striped in plain copper on the cut copper, a smooth stone lining a block inside.
//   Arms         stage III. The right arm thrown up, elbow out at (22, 37), the fist at y 40..41 under the lantern;
//                the left arm down, the hand at the hip holding the rolled plan (bone, along x).
//   Head         stage IV. Neck, a bearded head y 35..40 facing north, deep-set emerald eyes at y 38 under a brow, the
//                nose standing out to z 7, hair and a copper laurel at y 40..41.
//   Lantern      stage IV. On the fist: a copper base (y 42), three glowstone (y 43..45) caged in glass on all four
//                sides, a copper grate band at the corners of the middle course, a copper hip cap with a chiseled crown at y 46 (the top).

export const SIZE = [29, 47, 29]
export const STAGES = 4
export const MID = 14

// Palette (60/30/10): a dark plinth (deepslate bricks with polished deepslate dressings) on weathered stone-brick
// steps / a white figure (smooth stone robes, polished diorite skin and highlights) / copper (cloak, frieze, belt,
// laurel, lantern) that weathers to green. Why: the white figure reads against the sky and against the dark gate from
// the road; the copper is the one colour, and it changes with the years.
export const PALETTE = {
  step: [['stone_bricks', 7], ['cracked_stone_bricks', 2], ['mossy_stone_bricks', 2]],
  stepStairs: 'stone_brick_stairs',
  die: [['deepslate_bricks', 9], ['cracked_deepslate_bricks', 1]],
  dress: 'polished_deepslate', dressStairs: 'polished_deepslate_stairs', dressWall: 'polished_deepslate_wall',
  voussoir: 'deepslate_tiles', key: 'chiseled_deepslate',
  vaultLight: 'shroomlight',
  deck: 'smooth_stone',
  robe: 'smooth_stone', skin: 'polished_diorite', skinStairs: 'polished_diorite_stairs', robeSlab: 'smooth_stone_slab',
  copper: 'cut_copper', copperStairs: 'cut_copper_stairs', copperSlab: 'cut_copper_slab', copperBlock: 'copper_block',
  copperFancy: 'chiseled_copper', copperGrate: 'copper_grate',
  plan: 'bone_block', eye: 'emerald_block', glow: 'glowstone', glass: 'yellow_stained_glass',
  sign: 'dark_oak_wall_sign', fire: 'campfire', lamp: 'lantern', chain: 'chain',
}

export const MASSES = {
  steps: { y: [0, 1, 2] }, // inset = y
  piers: { size: 3, top: 3, fire: 4 },
  die: { inset: 3, y0: 3, y1: 10, frieze: 10, cornice: 11, deck: 12, balustrade: 13 },
  arch: { x0: 12, x1: 16, crown: 5, ceiling: 6, lights: [4, 9, 14, 19, 24] }, // x 12 and 16: a springer at y 5
  pilasters: { front: [3, 9, 19, 25], side: [3, 9, 14, 19, 25], back: [3, 9, 19, 25] },
  plaque: { y: 8, z: 2, x: [11, 17] },
  feet: { left: { x: 10, z: 11 }, right: { x: 18, z: 13 } },
  shins: [14, 21], // a smooth stone sandal strap at y 14, bare shins to the knee
  tunic: { y0: 22, y1: 26, cz: 11.5 },
  belt: 27, torso: { y0: 28, y1: 33, cz: 11.5 },
  cloak: { top: 34, hem: 13, z0: 15 },
  rightArm: [[19.5, 32, 11.5], [22, 36.5, 11], [21, 40.2, 10]],
  leftArm: [[8.5, 32, 11.5], [7, 28, 12], [8, 25, 9]],
  plan: { x: 7, y: [22, 26], z: 8 }, // the rolled plan, upright in the left fist
  head: { y0: 35, y1: 40, cx: 14, cz: 10, eyes: { y: 38, z: 9, x: [13, 15] }, nose: [[14, 38, 8], [14, 37, 7], [14, 36, 7]] },
  lantern: { cx: 21, cz: 10, base: 42, glow: [43, 45], cap: 46 },
}

export const BRAZIERS = [
  [1, 4, 1], [27, 4, 1], [1, 4, 27], [27, 4, 27], // the corner piers
  [2, 13, 2], [26, 13, 2], [2, 13, 26], [26, 13, 26], // the deck's corners
  [14, 13, 7], // the eternal flame before the Founder's feet
]

// No rooms, beds, storage or furniture (the brief needs none): a monument. The walking line is the road itself.
export const ROOMS = []
export const FURNITURE = []
export const WALK = [[14, 0, -1], [14, 0, 0], [14, 0, 14], [14, 0, 28], [14, 0, 29]]
