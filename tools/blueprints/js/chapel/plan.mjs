// Chapel (ROADMAP 49 rebuild, brief: tools/blueprints/briefs/chapel.md), the plan: drawn before any wall. Story,
// palette, masses, rooms, openings, every furniture footprint and the walking line come first; recipe.mjs then
// assembles the chapel from the parts library (../parts) in passes.
//
// Three rough concepts (massing, roof, plan, story):
//   A. The Hill Chapel: one long box 7 x 13 under a steep slate roof, a bell-cote on the front gable, a lean-to
//      porch. The chapel everyone draws: safe, one mass, and the wedding happens indoors where the fireworks the
//      code launches from the middle's columns go off on the roof.
//   B. The Rotunda: an octagon of white columns under a dome and a lantern, the couple in the middle. A strong
//      outline, but a 13-wide dome in blocks is lumpy, heavy (well past the builder's ~900 blocks) and reads as a
//      bandstand, not a chapel.
//   C. The Sky-Court Chapel: a gate tower with a slate needle spire at the road, a walled court open to the sky in
//      the middle where the guests sit, and a steep-roofed sanctuary at the back with the altar under a rose window.
//      The vows are said under a flowering stone arch in the open court, so the wedding fireworks (launched by the
//      code from the ground within 3 blocks of the middle) go up from among the guests, and the Seer arrives on the
//      inlaid rose under that arch. Two tall ends tied by a low wall: a real silhouette from every side.
//   C is the strongest: it is built round what the mod does at a chapel (the middle, the fireworks, the Seer), it
//   has a hero (the spire, 15 high and a rod, a third taller than the sanctuary) and two other masses of different heights,
//   and leaving the court unroofed spends the ~900-block budget on detail instead of a big plain roof. Built: C.
//
// Story: the "Blossom Court". A village that marries its couples out of doors walls in a little court for it: a
// limewashed gate tower to ring them in (louvred belfry, needle spire), cherry trees in the corners between the
// tower and the wall, two rows of dark oak pews, a vow arch wreathed in flowering azalea over the inlaid rose where
// the couple stand, and the sanctuary opposite, its coped gable open to the court in one wide arch, the altar
// candles and the rose window seen straight down the aisle from the road.
//
// Coordinates: x across the front (west 0 -> east 12), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Size 13 x 16 x 17: the middle the code reads is (13/2, 1, 17/2) = (6, 1, 8), the rose.
//
// Masses (named parts the owner's feedback can point at):
//   Tower      the gate tower x 4..8, z 1..5, walls to y 9, the door at its foot under a slate hood and lantern, a
//              vestibule (ceiling y 4), slit lights, a string course (y 6) under a belfry of twin louvred openings on
//              each face (y 7..8), and a steep slate broach spire from the wall head (y 10) to its point (y 13), a copper rod on it.
//   Court      the walled court x 2..10, z 2..10, open to the sky: the cherry gardens x 2..3 / 9..10, z 2..5 either
//              side of the tower, then the pews, the vow arch (z 8) and the rose at the middle.
//   Wall       the court wall x 1 / 11 (z 1..10) and the front z 1 (x 1..3, 9..11): limewash on a stone plinth, coped at y 3
//              (level with the sanctuary's eaves),
//              stone piers at z 1, 4, 8 with stepped buttresses outside them, grilles in the court bays.
//   Sanctuary  x 2..10, z 11..16: side walls to y 5 with paired lights, under a bell-cast slate roof (ridge along z,
//              y 10),
//              coped stone gables at z 11 (the chancel arch, 5 wide, closed by a wrought screen and door) and z 16 (the rose window over the altar).

export const SIZE = [13, 16, 17] // w, h, d (plot limit 14 x 16 x 17)
export const MIDDLE = [6, 1, 8]

// Palette (60/30/10): limewashed stone (polished diorite: walls, tower, gables) / stone-brick dressings (plinths,
// piers, quoins, arches, coping, floors) and a dark deepslate-tile roof / dark oak joinery (door, pews, louvres,
// ceiling), a copper rod on the spire, and the blossom: cherry, flowering azalea, white flowers. Why: a wedding chapel should read
// white and bright from the road; the near-black slate gives it an outline against the sky; the pink blossom and the
// dark oak are the festive tenth.
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
  bars: 'iron_bars',
  finial: 'lightning_rod',
  blossom: 'cherry_leaves', trunk: 'cherry_log',
  wreath: [['flowering_azalea_leaves', 3], ['azalea_leaves', 1]],
  soil: 'grass_block',
  flowers: ['lily_of_the_valley', 'oxeye_daisy', 'azure_bluet', 'pink_tulip'],
}

export const MASSES = {
  tower: { x0: 4, x1: 8, z0: 1, z1: 5, top: 9, ceiling: 4, belfry: [7, 8], spire: { cx: 6, cz: 3, heights: [13, 12, 10] } },
  wall: { west: 1, east: 11, z0: 1, z1: 10, top: 2, piers: [1, 4, 8], buttresses: [4, 8], grilles: [6] },
  gardens: [{ x0: 2, x1: 3, z0: 2, z1: 5 }, { x0: 9, x1: 10, z0: 2, z1: 5 }],
  court: { x0: 2, x1: 10, z0: 6, z1: 10 },
  sanctuary: {
    walls: { x0: 2, x1: 10, z0: 11, z1: 16 }, room: { x0: 3, x1: 9, z0: 12, z1: 15 }, wallTop: 5,
    roof: { axis: 'z', across: [1, 11], along: [12, 15], eaveY: 5, steep: false }, // bell-cast: kick at y 5, 45-degree body from y 6, ridge y 10
  },
}

export const ROOMS = [
  { name: 'Vestibule', use: 'the way in under the tower', box: [5, 1, 2, 7, 3, 4] },
  { name: 'Court', use: 'the guests\' pews, the vow arch and the rose (open to the sky)', box: [2, 1, 6, 10, 3, 10] },
  { name: 'Sanctuary', use: 'the altar, the families\' stalls', box: [3, 1, 12, 9, 6, 15] },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 1], facing: 'south', step: [6, 0, 0] },
  towerDoor: { at: [6, 1, 5], facing: 'south' }, // the inner door, into the court
  chancelArch: { cx: 6, z: 11, y0: 0, spring: 3, r: 3 }, // x 4..8 y 1..3, x 5..7 y 4 under the arch
  // the arch's wrought screen: bars either side between the pier and a jamb, over the door, and across the head
  chancelScreen: { jambs: [5, 7], bars: [[4, 1], [4, 2], [4, 3], [8, 1], [8, 2], [8, 3], [6, 3], [5, 4], [6, 4], [7, 4]] },
  windows: [
    { name: 'tower west slit', cells: [[4, 5, 3]] },
    { name: 'tower east slit', cells: [[8, 5, 3]] },
    { name: 'court gable oculus', cells: [[6, 7, 11], [6, 8, 11]] },
    { name: 'sanctuary west', cells: [[2, 2, 13], [2, 2, 14], [2, 3, 13], [2, 3, 14]] },
    { name: 'sanctuary east', cells: [[10, 2, 13], [10, 2, 14], [10, 3, 13], [10, 3, 14]] },
  ],
  // the rose over the altar (back gable, z 16): a cross of panes, its corners stone
  rose: { cx: 6, cy: 6, z: 16 },
  // the belfry: twin openings on each face either side of a stone mullion, louvred with open dark oak trapdoors
  louvres: { y0: 7, y1: 8, offsets: [-1, 1] },
}

// Every piece of furniture, footprint first. Pews and stalls are 'bench' spots (one per seat); lamps and the altar's
// banners are recorded too. Nothing here is a job block or a bed (the brief).
export const FURNITURE = [
  // Court: two rows of pews each side of the aisle, facing the sanctuary, an arm at the aisle end
  { room: 'Court', part: 'pew', x0: 2, x1: 3, z: 6, facing: 'south', arm: 4 },
  { room: 'Court', part: 'pew', x0: 2, x1: 3, z: 7, facing: 'south', arm: 4 },
  { room: 'Court', part: 'pew', x0: 9, x1: 10, z: 6, facing: 'south', arm: 8 },
  { room: 'Court', part: 'pew', x0: 9, x1: 10, z: 7, facing: 'south', arm: 8 },
  // Court: lamp posts at the front of the guests' side bays, potted azaleas beside them
  { room: 'Court', part: 'lampPost', pos: [2, 1, 10] },
  { room: 'Court', part: 'lampPost', pos: [10, 1, 10] },
  { room: 'Court', part: 'pot', pos: [3, 1, 10], block: 'potted_flowering_azalea_bush' },
  { room: 'Court', part: 'pot', pos: [9, 1, 10], block: 'potted_flowering_azalea_bush' },
  { room: 'Court', part: 'pot', pos: [2, 1, 9], block: 'potted_azalea_bush' },
  { room: 'Court', part: 'pot', pos: [10, 1, 9], block: 'potted_azalea_bush' },
  // Vestibule: a lantern from the ceiling
  { room: 'Vestibule', kind: 'lamp', part: 'hangingLamp', pos: [6, 3, 3], facing: 'south', wood: 'dark_oak' },
  // Sanctuary: the families' stalls along the side walls, facing across the aisle
  { room: 'Sanctuary', part: 'stall', cells: [[3, 1, 12], [3, 1, 13]], facing: 'east' },
  { room: 'Sanctuary', part: 'stall', cells: [[9, 1, 12], [9, 1, 13]], facing: 'west' },
  // Sanctuary: the altar on a one-step dais against the back gable, candles and a pot of lilies on it
  { room: 'Sanctuary', part: 'altar', dais: { x0: 4, x1: 8, z: 15, stepZ: 14 }, table: [[5, 2, 15], [6, 2, 15], [7, 2, 15]] },
  { room: 'Sanctuary', kind: 'picture', part: 'picture', pos: [4, 3, 15], facing: 'north', wood: 'dark_oak', colour: 'white' },
  { room: 'Sanctuary', kind: 'picture', part: 'picture', pos: [8, 3, 15], facing: 'north', wood: 'dark_oak', colour: 'white' },
  { room: 'Sanctuary', kind: 'lamp', part: 'hangingLamp', pos: [4, 5, 13], facing: 'south', chain: 2, wood: 'dark_oak' },
  { room: 'Sanctuary', kind: 'lamp', part: 'hangingLamp', pos: [8, 5, 13], facing: 'south', chain: 2, wood: 'dark_oak' },
]

// The walking line: the step -> the door -> the vestibule -> the inner door -> down the aisle (through the vow arch,
// over the rose at the middle) -> the screen's door -> the altar step.
export const WALK = [
  [6, 0, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [6, 1, 7], [6, 1, 8], [6, 1, 9],
  [6, 1, 10], [6, 1, 11], [6, 1, 12], [6, 1, 13], [6, 1, 14],
]

// Outside the walls: beds of white flowers in their own soil along the front either side of the step, the cherry
// trees in the corner gardens (their crowns spilling over the wall).
export const OUTSIDE = {
  flowerBeds: [{ x0: 1, x1: 3, z: 0, plants: ['lily_of_the_valley', 'oxeye_daisy', 'azure_bluet'] }, { x0: 9, x1: 11, z: 0, plants: ['azure_bluet', 'oxeye_daisy', 'lily_of_the_valley'] }],
  trees: [{ trunk: [2, 3], lean: 'west' }, { trunk: [10, 3], lean: 'east' }],
}
