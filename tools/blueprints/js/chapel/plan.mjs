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
//   Round 2: C's tower and spire on A's one long nave. The gate tower stands forward on the lawn at the
//   road, and behind it ONE roof runs as a single ridge from the tower's back wall (z 5) to one block past the back
//   gable (z 16): the guests, the vow arch and the altar are all under it. The tower is the hero (spire point y 14,
//   a copper rod at y 15, four blocks over the nave's ridge at y 10); the nave is the second mass; two cherry trees
//   stand free on the lawn either side of the tower, clear of its faces and of the door. The critic (round 2): from
//   the back and the side it read as a house: a wide low box under a big roof, the side walls flat, the cherry
//   crowns crowding the tower's corners and the door, the belfry's trapdoors reading as house shutters.
//   Round 3 (built): the nave two narrower (walls x 2 / 10) and two taller (eaves y 6, flush ridge y 10), so the side
//   walls stand tall under the roof; stepped buttresses at the vow arch (z 8) and the chancel arch (z 11) on both
//   sides, their weathering stairs out past the eave line; the side lights three tall, set back behind a dressed
//   frame with a sill; the tower two taller (wall head y 12) with twin belfry openings on all four faces at
//   y 11..12 (iron grilles from mullion to corner, no trapdoors) over a sill course (y 10), wholly above the ridge,
//   under a slate spire to y 15 (no room left for the rod); the cherry trees swapped for low flowering bushes at the
//   lawn's front corners; the ridge dropped into line with the top stairs (B1384: no ridge block over air).
//
// Story: the "Blossom Court". A village that marries its couples under blossom: a limewashed gate tower to ring
// them in (an open belfry, a slate spire, lamp posts at the step), blossom bushes on the lawn either side, and inside
// one long white nave: four rows of dark oak pews each side of an andesite aisle, a round stone vow arch wreathed in
// flowering azalea over the inlaid rose where the couple stand (the middle the code reads), a chancel arch, and the
// altar on its dais under the rose window, seen straight down the aisle from the door.
//
// Coordinates: x across the front (west 0 -> east 12), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Size 13 x 16 x 17: the middle the code reads is (13/2, 1, 17/2) = (6, 1, 8), the rose.
//
// Masses (named parts the owner's feedback can point at):
//   Tower      the gate tower x 4..8, z 1..5, walls to y 12, the door at its foot between two lamp posts, a
//              vestibule (ceiling y 4), slit lights, a sill course of corbels (y 10, front and sides) under a belfry
//              of twin openings either side of a stone mullion on all four faces (y 11..12, above the nave's
//              ridge; the back pair looks out over the roof), grilled with iron bars from mullion to corner, its
//              lantern on a chain from the needle's core, and a slate spire from the
//              wall head (eave ring y 13) to its point (y 15).
//   Nave       x 2..10, z 5..15: limewashed walls to y 6 on a stone plinth, dressed pilasters at the corners; ONE
//              bell-cast slate roof (a flush ridge along z at y 10, in line with the top stairs; kick eaves at x 1 / 11,
//              y 6) from the tower's back wall to one block past the back gable (rows z 5..16; the tower's cells at
//              z 5 are left to the tower). Front gable stubs beside the tower at z 5 (x 2..3, 9..10), the back gable
//              at z 15 with the rose window set one block deep under a dressed sill and head at z 16 under the overhang.
//   Buttresses stepped stone buttresses at z 8 (the vow arch) and z 11 (the chancel arch), both sides: a full pier
//              at x 1 / 11 up to the eave (the kick rests on it), a lower stage at x 0 / 12 to y 2 weathered with a
//              stair at y 3, outside the eave line (no roof over it).
//   Side lights three tall (y 2..4) in the wall at z 7, 10, 13, set back one block behind a dressed frame at x 1 /
//              11 (jambs up to the eave kick, which heads them, and a sill), the buttresses standing as a jamb where they meet.
//   Lawn       x 0..3 / 9..12, z 0..4 either side of the tower: a low bush of blossom at each front corner (no
//              higher than y 1, clear of the tower and the door), flower beds along the front.

export const SIZE = [13, 16, 17] // w, h, d (plot limit 14 x 16 x 17)
export const MIDDLE = [6, 1, 8]

// Palette (60/30/10): limewashed stone (polished diorite: walls, tower, gables) / stone-brick dressings (plinths,
// pilasters, quoins, arches, floors) and a dark deepslate-tile roof / dark oak joinery (door, pews, stalls),
// and the blossom: cherry, flowering azalea, white flowers. Why: a wedding chapel should read white and bright from
// the road; the near-black slate gives it an outline against the sky; the pink blossom and the dark oak are the
// festive tenth.
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
  tower: { x0: 4, x1: 8, z0: 1, z1: 5, top: 12, ceiling: 4, belfry: [11, 12], course: 10, spire: { cx: 6, cz: 3, heights: [15, 14, 13] } },
  nave: {
    x0: 2, x1: 10, z0: 5, z1: 15, wallTop: 6, room: { x0: 3, x1: 9, z0: 6, z1: 14 },
    pilasters: [5, 15], // dressed stone up the wall line at the front and back corners
    roof: { axis: 'z', across: [1, 11], along: [5, 16], eaveY: 6, steep: false }, // kick y 6, 45-degree body x 2 y 7 .. x 5 y 10; the ridge (x 6) is dropped to y 10 in recipe.mjs
    buttresses: [8, 11], // at the vow arch's piers and the chancel arch
    sanctuary: { z0: 12, z1: 14 },
  },
}

export const ROOMS = [
  { name: 'Vestibule', use: 'the way in under the tower', box: [5, 1, 2, 7, 3, 4] },
  { name: 'Nave', use: 'the guests\' pews, the vow arch and the rose at the middle', box: [3, 1, 6, 9, 6, 10] },
  { name: 'Sanctuary', use: 'the altar, the families\' stalls', box: [3, 1, 12, 9, 7, 14] },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 1], facing: 'south', step: [6, 0, 0] },
  towerDoor: { at: [6, 1, 5], facing: 'south' }, // the inner door, into the nave
  // the chancel arch at z 11: a thin segmental arch of dressed stone springing from the side walls (x 2 / 10), the
  // whole nave width open under it to y 4, the middle three cells to y 5
  chancelArch: { z: 11 },
  windows: [
    { name: 'tower west slits', cells: [[4, 5, 3], [4, 8, 3]] },
    { name: 'tower east slits', cells: [[8, 5, 3], [8, 8, 3]] },
    { name: 'tower front lancet', cells: [[6, 5, 1], [6, 6, 1]] }, // over the door's keystone
    { name: 'tower front light', cells: [[6, 8, 1]] },
    { name: 'front gable west', cells: [[3, 2, 5], [3, 3, 5], [3, 4, 5]] },
    { name: 'front gable east', cells: [[9, 2, 5], [9, 3, 5], [9, 4, 5]] },
  ],
  // the side lights: three tall in the wall plane, set back behind a dressed frame one block out
  sideLights: { zs: [7, 10, 13], y0: 2, y1: 4 },
  // the rose over the altar: a cross of panes in the back gable (z 15), its corners stone, set one block deep behind
  // a dressed sill and keyed head at z 16 under the roof's overhang
  rose: { cx: 6, cy: 7, z: 15, frameZ: 16 },
  // the belfry: twin openings either side of a stone mullion on all four faces, the sill course under them, the
  // spire's eave over them, each grilled with iron bars from the mullion to the corner (no trapdoors: they read as
  // house shutters; left empty, holes.py reads the tower's shaft as a room open to the weather)
  belfry: { y0: 11, y1: 12, offsets: [-1, 1] },
}

// Every piece of furniture, footprint first. Pews and stalls are 'bench' spots (one per seat); lamps and the altar's
// banners are recorded too. Nothing here is a job block or a bed (the brief).
export const FURNITURE = [
  // Nave: four rows of pews each side of the aisle (x 5..7 stays clear), facing the altar; the rows at z 7 stop short
  // of the vow arch's piers (x 4 / 8), a potted flowering azalea in that cell instead
  ...[6, 7, 9, 10].flatMap(z => [
    { room: 'Nave', part: 'pew', x0: 3, x1: z === 7 ? 3 : 4, z, facing: 'south' },
    { room: 'Nave', part: 'pew', x0: z === 7 ? 9 : 8, x1: 9, z, facing: 'south' },
  ]),
  { room: 'Nave', part: 'pot', pos: [4, 1, 7], block: 'potted_flowering_azalea_bush' },
  { room: 'Nave', part: 'pot', pos: [8, 1, 7], block: 'potted_flowering_azalea_bush' },
  // Nave: lanterns on chains either side of the vow arch
  { room: 'Nave', kind: 'lamp', part: 'hangingLamp', pos: [3, 5, 8], facing: 'south', chain: 2, wood: 'dark_oak' },
  { room: 'Nave', kind: 'lamp', part: 'hangingLamp', pos: [9, 5, 8], facing: 'south', chain: 2, wood: 'dark_oak' },
  // Vestibule: a lantern from the ceiling
  { room: 'Vestibule', kind: 'lamp', part: 'hangingLamp', pos: [6, 3, 3], facing: 'south', wood: 'dark_oak' },
  // Sanctuary: the families' stalls along the side walls, facing across the aisle
  { room: 'Sanctuary', part: 'stall', cells: [[3, 1, 12], [3, 1, 13]], facing: 'east' },
  { room: 'Sanctuary', part: 'stall', cells: [[9, 1, 12], [9, 1, 13]], facing: 'west' },
  // Sanctuary: the altar on a one-step dais against the back gable, candles and a pot of lilies on it
  { room: 'Sanctuary', part: 'altar', dais: { x0: 4, x1: 8, z: 14, stepZ: 13 }, table: [[5, 2, 14], [6, 2, 14], [7, 2, 14]] },
  { room: 'Sanctuary', kind: 'picture', part: 'picture', pos: [4, 3, 14], facing: 'north', wood: 'dark_oak', colour: 'white' },
  { room: 'Sanctuary', kind: 'picture', part: 'picture', pos: [8, 3, 14], facing: 'north', wood: 'dark_oak', colour: 'white' },
  { room: 'Sanctuary', kind: 'lamp', part: 'hangingLamp', pos: [4, 6, 12], facing: 'south', chain: 2, wood: 'dark_oak' },
  { room: 'Sanctuary', kind: 'lamp', part: 'hangingLamp', pos: [8, 6, 12], facing: 'south', chain: 2, wood: 'dark_oak' },
]

// The walking line: the step -> the door -> the vestibule -> the inner door -> down the aisle (through the vow arch,
// over the rose at the middle) -> under the chancel arch -> the altar step.
export const WALK = [
  [6, 0, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [6, 1, 7], [6, 1, 8], [6, 1, 9],
  [6, 1, 10], [6, 1, 11], [6, 1, 12], [6, 1, 13],
]

// Outside: beds of white flowers in their own soil along the front either side of the step, lamp posts at the step,
// a low bush of blossom at each of the lawn's front corners (cells [x, y, z], nothing above y 1).
export const OUTSIDE = {
  flowerBeds: [{ x0: 2, x1: 3, z: 0, plants: ['lily_of_the_valley', 'oxeye_daisy'] }, { x0: 9, x1: 10, z: 0, plants: ['oxeye_daisy', 'lily_of_the_valley'] }],
  lampPosts: [[5, 0, 0], [7, 0, 0]],
  bushes: [
    [[0, 0, 0], [1, 0, 0], [0, 0, 1], [1, 0, 1], [0, 0, 2], [0, 1, 0]],
    [[12, 0, 0], [11, 0, 0], [12, 0, 1], [11, 0, 1], [12, 0, 2], [12, 1, 0]],
  ],
}
