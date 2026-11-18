// The Jeweler's Workshop (jewellers_workshop, _2, _3; ROADMAP 49/50.7), the plan: drawn before any wall
// (tools/blueprints/briefs/jewellers_workshop.md). recipe.mjs assembles it from the parts library (../parts) for
// LEVEL 1, 2 or 3; each level is the last one grown by the same lines.
//
// Three rough concepts
//   A. The Counting Tower: a square stone strong-tower at the back corner, five storeys under a pyramid cap, and the
//      shop a low lean-to against its foot. Plan: shop in the lean-to, the vault the tower's ground floor. Story: a
//      jeweler who trusts stone more than people and lives up his tower. Out: a tower worth the name blows the
//      750-block day and the 13-block height, and it leaves the shop (the building's whole job) a shed.
//   B. The Stepped Gable: a narrow stone-brick merchant's house end-on to the street under a steep copper roof,
//      its front a crow-stepped gable; a glass shopfront projects from it under a pentice, the one amethyst set in
//      the middle of the glass, a timber door for the Jeweler on one side, an iron door for buyers on the other.
//      Inside: one tall room open to the rafters, the cutter's bench under the side window. II builds a squat
//      barrel-vaulted strong room behind, on buttresses, its chests behind a barred grille; III digs a deep vault
//      under it. Story: a gem-cutter from a city of cutters builds the way they do there.
//   C. The Arcade Atelier: a long low shop with an open arcade on the street, the Jeweler cutting in public under
//      it, the vault a separate stone block across a yard. Story: a craftsman who sells by being watched. Out: an
//      open arcade can't hold a glass case or a locked door, the vault across a yard falls out of the stonecutter's
//      reach, and it reads as any market stall.
// The strongest is B. Each of its three parts says what it is for at a glance: glass for showing (the vitrine with the
// gem in it), height and light for working (a tall north-lit room with the bench under the window), mass for keeping
// (a windowless stone vault with a round back and bars). The crow-stepped gable gives the street a silhouette no other
// workshop has, and the strong room's curved stone roof gives the back one too. A spends its blocks on height the
// job doesn't need; C has nowhere to put what the tests and the trade need.
//
// Story (B): "The Stepped Gable". The Jeweler comes from a city of gem-cutters, where the merchants' houses stand
// gable-end to the street with stepped fronts of dressed stone. He builds one: grey stone bricks on a dark deepslate
// plinth, dark quoins, a roof of copper gone green (jewelers work metal; it is the one roof in the village that
// isn't wood or tile). His shop faces north on purpose: the steady north light through the tall gable window is the
// light a cutter wants. Buyers step up to the iron door on the right; he goes in by his own timber door on the left;
// between them the glass case sticks out into the street with his best stone in it, an amethyst on a plinth, lit
// at night from the lanterns under the pentice. Inside the bench runs down the west wall: the stonecutter under the
// window with its lantern hanging over it from the tie beam, the anvil, the melting furnace, his chest. A counter
// keeps buyers to the front. Level II: the strong room behind, a stone barrel vault on buttresses (stone doesn't
// burn), an iron door in from the shop, an anteroom, and a barred grille with a third iron door; behind the bars the
// chests stand two high against the round back wall. The two back windows of the shop are blocked up where the vault
// meets them. Level III: a hatch in the anteroom's floor and a ladder down to the Deep Vault, four blocks under it.
//
// Coordinates: x across the front (west 0 -> east 10), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The doors are on the north side with a stair step at y = 0 before each.
//
// Masses (named parts the owner's feedback can point at):
//   Shop        the main mass: stone-brick walls x 1..9, z 1..8, y 1..4 on a deepslate plinth (y 0); a 45-degree
//               copper roof, ridge along z at x 5 (y 9), its eaves a block out at x 0 and 10, running z 2..7
//               between the two gables.
//   Gables      crow-stepped stone gables at z 1 (the street front) and z 8 (the back), steps of 2-2-1-2-2 at
//               y 6 / 8 / 10, each step capped with a stone-brick slab; dark quoins up the four corners.
//   Shopfront   the ground floor of the front gable: the Vitrine (a glass case projecting a block, x 3..7 at z 0,
//               dark-oak posts, the amethyst on a chiseled plinth in its middle), the Jeweler's timber door at x 2,
//               the buyers' iron door at x 8 with its buttons, a copper pentice along y 4 over all of it with a
//               lantern over each door. Above: the north-light window (x 4..6, y 5..6) and a purple banner, the
//               shop's sign, high in the gable.
//   Strong Room (II) x 2..8, z 9..15: stone-brick walls y 1..3, a stone barrel vault (crown y 6), its rim standing
//               a block proud at z 16, two buttresses down each side, barred slits and a barred lunette in the back.
//   Deep Vault  (III) dug under the Strong Room, walls x 2..8, z 9..15, floor y -4, reached by a ladder under a
//               hatch in the anteroom's floor at (5, 0, 10).

export const SIZES = { 1: [11, 13, 10], 2: [11, 13, 17], 3: [11, 13, 17] } // w, h above ground, d (<= 18 x 13 x 21)
export const BASEMENT = { 1: 0, 2: 0, 3: 4 }

// Palette (60/30/10): grey stone bricks (walls, gables, vault: 60) / green copper roof and dark-oak joinery (30) /
// dark deepslate plinth and quoins, purple (the banner, the rug, the gem) (10). Why: the test's stone bricks become the
// whole body, light and dressed; the dark plinth and quoins frame it; the weathered copper contrasts in hue, not just
// in value, and says "metal-worker"; purple is the amethyst's own colour, used sparingly so the gem stays the brightest
// thing on the front.
export const PALETTE = {
  wall: 'stone_bricks',
  wallAlt: [['stone_bricks', 9], ['cracked_stone_bricks', 1]], // the vault's walls and buttresses: a little worn
  plinth: [['deepslate_bricks', 5], ['cracked_deepslate_bricks', 1], ['polished_deepslate', 1]],
  quoin: 'polished_deepslate', quoinAlt: 'deepslate_bricks', keystone: 'chiseled_deepslate',
  coping: 'stone_brick_slab',
  finial: 'stone_brick_wall',
  roof: { full: 'waxed_weathered_cut_copper', stairs: 'waxed_weathered_cut_copper_stairs', slab: 'waxed_weathered_cut_copper_slab' },
  vault: { full: 'stone_bricks', stairs: 'stone_brick_stairs', slab: 'stone_brick_slab' },
  rim: { full: 'deepslate_bricks', stairs: 'deepslate_brick_stairs', slab: 'deepslate_brick_slab' },
  floor: 'dark_oak_planks',
  vaultFloor: 'polished_andesite',
  plinthStone: 'chiseled_stone_bricks', // the gem's plinth
  wood: 'dark_oak', // doors, shutters, counter, beams
  post: 'dark_oak_log',
  glass: 'glass_pane',
  bars: 'iron_bars',
  accent: 'purple',
  cellarWall: [['deepslate_bricks', 4], ['cracked_deepslate_bricks', 1], ['deepslate_tiles', 1]],
  cellarFloor: 'polished_deepslate',
}

export const MASSES = {
  shop: { x0: 1, x1: 9, z0: 1, z1: 8, wallTop: 4, roof: { axis: 'z', across: [0, 10], along: [2, 7], eaveY: 4, steep: false } },
  // crow steps: each gable column's top (the coping slab sits one above)
  crow: { 1: 6, 2: 6, 3: 8, 4: 8, 5: 10, 6: 8, 7: 8, 8: 6, 9: 6 },
  vitrine: { x0: 3, x1: 7, z: 0, gem: [5, 2, 1] },
  pentice: { x0: 1, x1: 9, y: 4, z: 0 },
  mould: { x0: 3, x1: 7, y: 7, z: 0 }, // the course over the north light, across the gable at its first step
  tieBeam: { z: 4, y: 5, x0: 2, x1: 8 },
  // Level I's back: a plinth course of dark stairs along the foot of the back gable, the sills of its two windows on it
  // (II builds the Strong Room there instead)
  backCourse: { z: 9, x0: 1, x1: 9 },
  strongRoom: { x0: 2, x1: 8, z0: 9, z1: 15, wallTop: 3, footTop: 1, rimZ: 16, buttressZ: [11, 14] },
  // the barrel vault's section across x (every slice z 9..15, and the rim at z 16 in the rim stone): [x, y, shape]
  vaultSection: [
    [2, 4, 'stairs-east'], [3, 4, 'full'], [3, 5, 'stairs-east'], [4, 5, 'full'], [4, 6, 'slab'], [5, 5, 'full'],
    [5, 6, 'slab'], [6, 5, 'full'], [6, 6, 'slab'], [7, 4, 'full'], [7, 5, 'stairs-west'], [8, 4, 'stairs-west'],
  ],
  keystone: [5, 5, 16], // the rim's crown
  deepVault: { x0: 2, z0: 9, x1: 8, z1: 15, depth: 4 },
}

export const ROOMS = [
  { name: 'Shop', use: 'the bench (stonecutter, anvil, furnace, chest) down the west wall, a counter keeping buyers to the front, the vitrine in the front wall', box: [2, 1, 2, 8, 4, 7] },
  { name: 'Anteroom', level: 2, use: 'between the shop and the grille; the hatch to the Deep Vault (III) in its floor', box: [3, 1, 9, 7, 3, 10] },
  { name: 'Strong Room', level: 2, use: 'fourteen chests behind the barred grille', box: [3, 1, 12, 7, 3, 14] },
  { name: 'Deep Vault', level: 3, use: 'nine more chests four blocks down', box: [3, -3, 10, 7, -1, 14] },
]

export const OPENINGS = {
  jewelerDoor: { at: [2, 1, 1], facing: 'south', out: 'north', step: [2, 0, 0] }, // dark oak: the Jeweler's way in
  shopDoor: { at: [8, 1, 1], facing: 'south', out: 'north', step: [8, 0, 0], buttons: [[9, 2, 0, 'north'], [7, 2, 2, 'south']] },
  vaultDoor: { level: 2, at: [5, 1, 8], facing: 'south', buttons: [[6, 2, 7, 'north'], [4, 2, 9, 'south']] },
  cageDoor: { level: 2, at: [5, 1, 11], facing: 'south', floorButtons: [[4, 1, 10], [6, 1, 12]] },
  hatch: { level: 3, x: 5, z: 10, facing: 'south' },
  // windows: cells of glass, the side they look out of, and their dressing
  windows: [
    { name: 'north light', cells: [[4, 5, 1], [5, 5, 1], [6, 5, 1], [4, 6, 1], [5, 6, 1], [6, 6, 1]], out: 'north', dress: null },
    { name: 'bench', cells: [[1, 2, 4], [1, 3, 4]], out: 'west', dress: 'shutters' },
    { name: 'furnace', cells: [[1, 2, 6], [1, 3, 6]], out: 'west', dress: 'shutters' },
    { name: 'counter', cells: [[9, 2, 4], [9, 3, 4]], out: 'east', dress: 'shutters' },
    { name: 'east back', cells: [[9, 2, 6], [9, 3, 6]], out: 'east', dress: 'shutters' },
    { name: 'back gable', cells: [[5, 7, 8], [5, 8, 8]], out: 'south', dress: null },
    // Level I only (blocked up by II): stone sills on the back's plinth course (the critic's note: the back was flat)
    { name: 'back west', level: -1, cells: [[3, 2, 8], [3, 3, 8]], out: 'south', dress: 'sill' },
    { name: 'back east', level: -1, cells: [[7, 2, 8], [7, 3, 8]], out: 'south', dress: 'sill' },
  ],
  vitrine: { front: [[4, 2, 0], [5, 2, 0], [6, 2, 0], [4, 3, 0], [5, 3, 0], [6, 3, 0]], sides: [[4, 2, 1], [6, 2, 1]], open: [[4, 3, 1], [5, 3, 1], [6, 3, 1]] },
  // the strong room's grille (z 11) and its bars to the outside
  grille: { z: 11, bars: [[3, 1], [3, 2], [3, 3], [4, 1], [4, 2], [4, 3], [5, 3], [6, 1], [6, 2], [6, 3], [7, 1], [7, 2], [7, 3]] },
  slits: [[2, 2, 12], [2, 3, 12], [8, 2, 12], [8, 3, 12]],
  lunette: [[4, 2, 15], [5, 2, 15], [6, 2, 15], [4, 3, 15], [5, 3, 15], [6, 3, 15], [5, 4, 15]], // the chests show through it
}

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a furniture spot) or a fixture
// (a vanilla block the game uses as itself: chest, furnace, anvil, the stonecutter). part: the part that draws it.
export const FURNITURE = [
  // Shop: the bench down the west wall
  { room: 'Shop', kind: 'fixture', part: 'block', pos: [2, 1, 4], block: 'stonecutter', props: { facing: 'east' } }, // the job block
  { room: 'Shop', kind: 'fixture', part: 'block', pos: [2, 1, 5], block: 'anvil', props: { facing: 'north' } },
  { room: 'Shop', kind: 'fixture', part: 'block', pos: [2, 1, 6], block: 'furnace', props: { facing: 'east', lit: 'false' } },
  { room: 'Shop', kind: 'fixture', part: 'chest', pos: [2, 1, 7], facing: 'east', block: 'chest' }, // the Jeweler's chest
  // the counter across the east half, buyers in front of it, a cupboard of ledgers behind
  { room: 'Shop', kind: 'counter', part: 'counter', pos: [6, 1, 4], facing: 'south', wood: 'dark_oak', top: 'polished_deepslate_slab' },
  { room: 'Shop', kind: 'counter', part: 'counter', pos: [7, 1, 4], facing: 'south', wood: 'dark_oak', top: 'polished_deepslate_slab' },
  { room: 'Shop', kind: 'counter', part: 'counter', pos: [8, 1, 4], facing: 'south', wood: 'dark_oak', top: 'polished_deepslate_slab' },
  { room: 'Shop', kind: 'cupboard', part: 'cupboard', pos: [8, 1, 7], facing: 'west', wood: 'dark_oak' },
  { room: 'Shop', kind: 'lamp', part: 'hangingLamp', pos: [7, 4, 4], facing: 'north', wood: 'dark_oak' }, // over the counter
  { room: 'Shop', kind: 'rug', part: 'rug', cellsAbs: [[3, 1, 5], [4, 1, 5], [5, 1, 5], [3, 1, 6], [4, 1, 6], [5, 1, 6]], colour: 'purple' },
  // Strong Room (II): five chests long against the back, two high; a double chest down each side
  { room: 'Strong Room', level: 2, kind: 'fixture', part: 'doubleChest', pos: [3, 1, 14], facing: 'north', along: 'east' },
  { room: 'Strong Room', level: 2, kind: 'fixture', part: 'chest', pos: [5, 1, 14], facing: 'north', block: 'chest' },
  { room: 'Strong Room', level: 2, kind: 'fixture', part: 'doubleChest', pos: [6, 1, 14], facing: 'north', along: 'east' },
  { room: 'Strong Room', level: 2, kind: 'fixture', part: 'doubleChest', pos: [3, 2, 14], facing: 'north', along: 'east' },
  { room: 'Strong Room', level: 2, kind: 'fixture', part: 'chest', pos: [5, 2, 14], facing: 'north', block: 'chest' },
  { room: 'Strong Room', level: 2, kind: 'fixture', part: 'doubleChest', pos: [6, 2, 14], facing: 'north', along: 'east' },
  { room: 'Strong Room', level: 2, kind: 'fixture', part: 'doubleChest', pos: [3, 1, 12], facing: 'east', along: 'south' },
  { room: 'Strong Room', level: 2, kind: 'fixture', part: 'doubleChest', pos: [7, 1, 12], facing: 'west', along: 'south' },
  // Deep Vault (III): double chests down both sides, a strongbox against the back
  { room: 'Deep Vault', level: 3, kind: 'fixture', part: 'doubleChest', pos: [3, -3, 11], facing: 'east', along: 'south' },
  { room: 'Deep Vault', level: 3, kind: 'fixture', part: 'doubleChest', pos: [3, -3, 13], facing: 'east', along: 'south' },
  { room: 'Deep Vault', level: 3, kind: 'fixture', part: 'doubleChest', pos: [7, -3, 11], facing: 'west', along: 'south' },
  { room: 'Deep Vault', level: 3, kind: 'fixture', part: 'doubleChest', pos: [7, -3, 13], facing: 'west', along: 'south' },
  { room: 'Deep Vault', level: 3, kind: 'fixture', part: 'chest', pos: [5, -3, 14], facing: 'north', block: 'chest' },
]

// Lights that aren't furniture spots: the stonecutter's lantern (the test's, in its column, hung from the tie beam),
// the doors' lanterns under the pentice, a lantern under the ridge in the back gable's window (the critic's note: the
// window looked into an empty roof), and the vaults' lanterns hung from their crowns.
export const LIGHTS = [
  { at: [2, 4, 4] }, { at: [2, 3, 0] }, { at: [8, 3, 0] },
  { at: [5, 8, 7] }, // hung from the ridge just inside the back gable's window, so the window shows a lamp at night
  { level: 2, at: [5, 4, 9] }, { level: 2, at: [5, 4, 13] },
  { level: 3, at: [5, -1, 12] },
]

// The walking lines. The Jeweler: street (1, 0, -3) -> his step -> his timber door -> the bench (stands at 3, 1, 4).
// Buyers: their step -> the iron door -> in front of the counter -> (II) round its west end -> the vault door ->
// the anteroom (over the hatch, III) -> the cage door -> between the chests. (III) Down the ladder to the Deep Vault.
export const WALK = {
  jeweler: [[1, 0, -3], [2, 0, -1], [2, 0, 0], [2, 1, 1], [2, 1, 2], [3, 1, 3], [3, 1, 4]],
  buyer: [[8, 0, -1], [8, 0, 0], [8, 1, 1], [8, 1, 2], [7, 1, 3], [5, 1, 3], [5, 1, 4], [5, 1, 7], [5, 1, 8], [5, 1, 9], [5, 1, 10], [5, 1, 11], [5, 1, 12], [5, 1, 13]],
  cellar: [[5, 1, 10], [5, -1, 10], [5, -3, 10], [5, -3, 11], [5, -3, 13]],
}
