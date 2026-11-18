// Chapel (ROADMAP 49 rebuild, brief: tools/blueprints/briefs/chapel.md), the plan: drawn before any wall. Story,
// palette, masses, rooms, openings, every furniture footprint and the walking line come first; recipe.mjs then
// assembles the chapel from the parts library (../parts) in passes.
//
// Three rough concepts (massing, roof, plan, story):
//   A. The Hill Chapel: one long box 7 x 13 under a steep slate roof, a bell-cote on the front gable, a lean-to
//      porch. The chapel everyone draws: safe, one mass, no hero.
//   B. The Rotunda: an octagon of white columns under a dome and a lantern, the couple in the middle. A strong
//      outline, but a 13-wide dome in blocks is lumpy, heavy (well past the builder's ~900 blocks) and reads as a
//      bandstand, not a chapel.
//   C. The Sky-Court Chapel: a gate tower with a needle spire at the road, an open walled court for the guests, a
//      steep-roofed sanctuary at the back. Round 1 built C; the critic sent it back: the open middle read as a tower
//      and a separate shed with the congregation sitting outdoors, and the cherry crowns pressed on the tower hid the
//      door.
//   Round 2 (built): C's tower and spire on A's one long nave. The gate tower stands forward on the lawn at the
//   road, and behind it ONE roof runs as a single ridge from the tower's back wall (z 5) to one block past the back
//   gable (z 16): the guests, the vow arch and the altar are all under it. The tower is the hero (spire point y 14,
//   a copper rod at y 15, four blocks over the nave's ridge at y 10); the nave is the second mass; two cherry trees
//   stand free on the lawn either side of the tower, clear of its faces and of the door.
//
// Story: the "Blossom Court". A village that marries its couples under blossom: a limewashed gate tower to ring
// them in (louvred belfry, needle spire, lamp posts at the step), cherry trees on the lawn either side, and inside
// one long white nave: four rows of dark oak pews each side of an andesite aisle, a round stone vow arch wreathed in
// flowering azalea over the inlaid rose where the couple stand (the middle the code reads), a chancel arch, and the
// altar on its dais under the rose window, seen straight down the aisle from the door.
//
// Coordinates: x across the front (west 0 -> east 12), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Size 13 x 16 x 17: the middle the code reads is (13/2, 1, 17/2) = (6, 1, 8), the rose.
//
// Masses (named parts the owner's feedback can point at):
//   Tower      the gate tower x 4..8, z 1..5, walls to y 10, the door at its foot between two lamp posts (no hood:
//              nothing sits under the string course), a vestibule (ceiling y 4), slit lights, a string course (y 7,
//              front and sides) under a belfry of twin louvred openings on the front and side faces (y 8..9; the back
//              face meets the nave roof), and a steep slate broach spire from the wall head (y 11) to its point (y 14),
//              a copper rod on it.
//   Nave       x 1..11, z 5..15: limewashed walls to y 4 on a stone plinth, dressed pilasters at the corners and the
//              chancel line (z 11), paired lights each side; ONE bell-cast slate roof (ridge along z, y 10, kick eaves
//              at x 0 / 12, y 4) from the tower's back wall to one block past the back gable (rows z 5..16; the tower's
//              cells at z 5 are left to the tower). Front gable stubs beside the tower at z 5 (x 1..3, 9..11), the
//              back gable at z 15 with the rose window set one block deep in a dressed frame at z 16 under the
//              overhang, a plinth course at z 16.
//   Lawn       x 0..3 / 9..12, z 0..4 either side of the tower: a cherry tree standing free on each (trunk at x 1 /
//              11, z 2; crown x 0..2 / 10..12, z 1..3, a block clear of the tower), flower beds along the front.

export const SIZE = [13, 16, 17] // w, h, d (plot limit 14 x 16 x 17)
export const MIDDLE = [6, 1, 8]

// Palette (60/30/10): limewashed stone (polished diorite: walls, tower, gables) / stone-brick dressings (plinths,
// pilasters, quoins, arches, floors) and a dark deepslate-tile roof / dark oak joinery (door, pews, louvres), a copper
// rod on the spire, and the blossom: cherry, flowering azalea, white flowers. Why: a wedding chapel should read white
// and bright from the road; the near-black slate gives it an outline against the sky; the pink blossom and the dark
// oak are the festive tenth.
export const PALETTE = {
  plinth: [['stone_bricks', 6], ['mossy_stone_bricks', 2], ['cracked_stone_bricks', 1]],
  wall: 'polished_diorite',
  dress: { full: 'stone_bricks', stairs: 'stone_brick_stairs', slab: 'stone_brick_slab', key: 'chiseled_stone_bricks', alt: 'polished_andesite' },
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'stone_bricks',
  aisle: 'polished_andesite',
  sanctuaryFloor: 'polished_andesite',
  rose: { heart: 'chiseled_stone_bricks', arm: 'polished_diorite', diag: 'polished_andesite' },
  wood: 'dark_oak',
  glass: 'white_stained_glass_pane',
  roseGlass: { heart: 'pink_stained_glass_pane', arm: 'yellow_stained_glass_pane' },
  finial: 'lightning_rod',
  blossom: [['cherry_leaves', 4], ['flowering_azalea_leaves', 1]], trunk: 'cherry_log',
  wreath: [['flowering_azalea_leaves', 3], ['azalea_leaves', 1]],
}

export const MASSES = {
  tower: { x0: 4, x1: 8, z0: 1, z1: 5, top: 10, ceiling: 4, belfry: [8, 9], course: 7, spire: { cx: 6, cz: 3, heights: [14, 13, 11] } },
  nave: {
    x0: 1, x1: 11, z0: 5, z1: 15, wallTop: 4, room: { x0: 2, x1: 10, z0: 6, z1: 14 },
    pilasters: [5, 11, 15], // dressed stone up the wall line at the front corners, the chancel line, the back corners
    roof: { axis: 'z', across: [0, 12], along: [5, 16], eaveY: 4, steep: false }, // kick y 4, 45-degree body x 1 y 5 .. ridge x 6 y 10
    sanctuary: { z0: 12, z1: 14 },
  },
}

export const ROOMS = [
  { name: 'Vestibule', use: 'the way in under the tower', box: [5, 1, 2, 7, 3, 4] },
  { name: 'Nave', use: 'the guests\' pews, the vow arch and the rose at the middle', box: [2, 1, 6, 10, 4, 10] },
  { name: 'Sanctuary', use: 'the altar, the families\' stalls', box: [2, 1, 12, 10, 5, 14] },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 1], facing: 'south', step: [6, 0, 0] },
  towerDoor: { at: [6, 1, 5], facing: 'south' }, // the inner door, into the nave
  // the chancel arch at z 11: a thin segmental arch of dressed stone springing from the wall pilasters (x 1 / 11),
  // the whole nave width open under it to y 3, the middle five cells to y 4
  chancelArch: { z: 11 },
  windows: [
    { name: 'tower west slit', cells: [[4, 5, 3]] },
    { name: 'tower east slit', cells: [[8, 5, 3]] },
    { name: 'tower front lancet', cells: [[6, 5, 1], [6, 6, 1]] }, // over the door's keystone, under the string course
    { name: 'front gable west', cells: [[2, 2, 5], [2, 3, 5]] },
    { name: 'front gable east', cells: [[10, 2, 5], [10, 3, 5]] },
    { name: 'nave west', cells: [[1, 2, 7], [1, 3, 7], [1, 2, 9], [1, 3, 9]] },
    { name: 'nave east', cells: [[11, 2, 7], [11, 3, 7], [11, 2, 9], [11, 3, 9]] },
    { name: 'sanctuary west', cells: [[1, 2, 13], [1, 3, 13]] },
    { name: 'sanctuary east', cells: [[11, 2, 13], [11, 3, 13]] },
  ],
  // the rose over the altar: a cross of panes in the back gable (z 15), its corners stone, set one block deep behind
  // a dressed frame at z 16 (sill, jambs and a keyed head) under the roof's overhang
  rose: { cx: 6, cy: 6, z: 15, frameZ: 16 },
  // the belfry: twin openings either side of a stone mullion on the front and side faces, louvred with open dark oak
  // trapdoors (the back face is under the nave roof's ridge line, so it stays wall)
  louvres: { y0: 8, y1: 9, offsets: [-1, 1], faces: ['north', 'west', 'east'] },
}

// Every piece of furniture, footprint first. Pews and stalls are 'bench' spots (one per seat); lamps and the altar's
// banners are recorded too. Nothing here is a job block or a bed (the brief).
export const FURNITURE = [
  // Nave: four rows of pews each side of the aisle (x 5..7 stays clear), facing the altar; the rows at z 7 stop short
  // of the vow arch's piers (x 4 / 8), a potted flowering azalea in that cell instead
  ...[6, 7, 9, 10].flatMap(z => [
    { room: 'Nave', part: 'pew', x0: 2, x1: z === 7 ? 3 : 4, z, facing: 'south' },
    { room: 'Nave', part: 'pew', x0: z === 7 ? 9 : 8, x1: 10, z, facing: 'south' },
  ]),
  { room: 'Nave', part: 'pot', pos: [4, 1, 7], block: 'potted_flowering_azalea_bush' },
  { room: 'Nave', part: 'pot', pos: [8, 1, 7], block: 'potted_flowering_azalea_bush' },
  // Nave: lanterns on chains either side of the vow arch
  { room: 'Nave', kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 8], facing: 'south', chain: 2, wood: 'dark_oak' },
  { room: 'Nave', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 8], facing: 'south', chain: 2, wood: 'dark_oak' },
  // Vestibule: a lantern from the ceiling
  { room: 'Vestibule', kind: 'lamp', part: 'hangingLamp', pos: [6, 3, 3], facing: 'south', wood: 'dark_oak' },
  // Sanctuary: the families' stalls along the side walls, facing across the aisle
  { room: 'Sanctuary', part: 'stall', cells: [[2, 1, 12], [2, 1, 13]], facing: 'east' },
  { room: 'Sanctuary', part: 'stall', cells: [[10, 1, 12], [10, 1, 13]], facing: 'west' },
  // Sanctuary: the altar on a one-step dais against the back gable, candles and a pot of lilies on it
  { room: 'Sanctuary', part: 'altar', dais: { x0: 4, x1: 8, z: 14, stepZ: 13 }, table: [[5, 2, 14], [6, 2, 14], [7, 2, 14]] },
  { room: 'Sanctuary', kind: 'picture', part: 'picture', pos: [4, 3, 14], facing: 'north', wood: 'dark_oak', colour: 'white' },
  { room: 'Sanctuary', kind: 'picture', part: 'picture', pos: [8, 3, 14], facing: 'north', wood: 'dark_oak', colour: 'white' },
  { room: 'Sanctuary', kind: 'lamp', part: 'hangingLamp', pos: [4, 5, 12], facing: 'south', chain: 2, wood: 'dark_oak' },
  { room: 'Sanctuary', kind: 'lamp', part: 'hangingLamp', pos: [8, 5, 12], facing: 'south', chain: 2, wood: 'dark_oak' },
]

// The walking line: the step -> the door -> the vestibule -> the inner door -> down the aisle (through the vow arch,
// over the rose at the middle) -> under the chancel arch -> the altar step.
export const WALK = [
  [6, 0, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [6, 1, 7], [6, 1, 8], [6, 1, 9],
  [6, 1, 10], [6, 1, 11], [6, 1, 12], [6, 1, 13],
]

// Outside: beds of white flowers in their own soil along the front either side of the step, lamp posts at the step,
// a cherry tree standing free on the lawn either side of the tower (nothing over the front wall or the door).
export const OUTSIDE = {
  flowerBeds: [{ x0: 1, x1: 3, z: 0, plants: ['lily_of_the_valley', 'oxeye_daisy', 'azure_bluet'] }, { x0: 9, x1: 11, z: 0, plants: ['azure_bluet', 'oxeye_daisy', 'lily_of_the_valley'] }],
  lampPosts: [[5, 0, 0], [7, 0, 0]],
  trees: [{ trunk: [1, 2] }, { trunk: [11, 2] }],
}
