// The Pokémon Center, the plan: drawn before any wall (ROADMAP 49, tools/blueprints/briefs/pokemon_center.md). Rooms,
// every furniture footprint and the walking line come first; recipe.mjs assembles it from the parts library (../parts).
// The two looks (Mountain Lodge, Sunny Plaza) are their own folders beside this one (../pokemon_center_lodge,
// ../pokemon_center_plaza), each with the same contract and its own shape. NOTES.md holds the design notes.
//
// Three rough concepts (drawn on paper first; NOTES.md has them in full):
//   A. "The Red Dome" — an octagonal white drum under a stepped red dome with a glazed eye on top, the Healing Machine
//      right under the eye; a glass shop-front with a canopy at the street. Massing: one round hero (dome 12, eye 15),
//      II adds a gabled trade wing and a garden with a domed pasture pavilion. Roof: a dome. Plan: lobby in front,
//      the desk across the drum, the Nurse's room behind.
//   B. "The Long Porch" — a white hall with a red gable along the street and a veranda on posts. Safe, readable, and
//      the shape of every other house in the village.
//   C. "The Cross Clinic" — a Greek-cross plan, four red gables round a pyramid-capped crossing tower over the desk.
//      Strong from the air, but the arms are 3-block rooms and the crossing tower crowds the 13 x 13 plot.
// A is the strongest: nothing else in a village is round and domed, so the clinic is known from 20 blocks away in
// any light, and the dome's eye puts daylight on the machine: the shape says where the job is.
//
// Story: "The Red Dome". The village's first Nurse asked for a room where every trainer walks in and sees the
// machine at once. The builders gave her a round hall: white plaster on a grey stone skirt, a red band at the lintels,
// a red dome on a white cornice, a little glass eye on top that lets the sun fall on the desk. Later (II) the Pokémon
// Trader rents the gabled wing beside it, and the garden behind gets a domed pavilion for the Pasture Block.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, the street) to the back. Pinned by the brief: the Healing Machine (6, 1, 7), the Nurse's cell (6, 1, 8),
// the PC (10, 1..2, 7), the camera eye (8.5, 2.7, 4.2) with a clear line to the machine.
//
// Named parts:
//   Drum      the octagonal wall (railCells round (6, 6), r 5: x 1..11, z 1..11), white, y 1..3, a red band at y 4.
//   Skirt     a grey stone ring at y 0 one block out from the drum (stairs, the plinth's depth), the sills sit on it.
//   Bays      (round 2) the west, east and back faces stand one proud on the skirt's ring: diorite pilasters at the
//             octagon's vertices from the plinth to the cornice, the band carried round, windows set back behind reveals.
//   Cornice   a white ring at y 5 over the band, its lip (upside-down stairs) the one even overhang; the dome springs from it.
//   Dome      red, on the drum's ring (6, y 6..10, 6), coffered inside (its own stepped courses), an eye (cupola) at y 11..13.
//   Front     the flat front face (z 1, x 3..9): glass either side of the door, a white canopy on two piers at z 0.
//   Lobby     z 2..6: the runner from the door to the desk, two rows of waiting seats facing it, two lamps, plants.
//   Desk      the counter across z 7 (x 3..9), the machine in its middle, the PC at its east end, a gap at x 2.
//   Back room the Nurse's side, z 8..10: her cell (6, 8), two chests, cupboards, her lamp-lit writing table and chair.
//   Wing (II) the Trade Hall, x 12..18, z 2..10, a red 45-degree gable to the street, its own door, the Shop Counter.
//   Garden (II) z 12..25: a fenced garden, walks meeting a spur to the domed pavilion over the Pasture Block, four
//             kerbed beds of soil, planted, benches, lamp posts.

export const SIZE = { 1: [13, 16, 13], 2: [20, 16, 26] }

// Palette (60/30/10): white plaster (concrete) walls / a red dome and wing roof (mangrove: the brightest red wood) /
// a grey stone skirt, a red band, white diorite trim, birch doors. Why: the clinic's red and white, the darkest at the
// ground, the dome the only red mass, the band tying the drum to it.
export const PALETTE = {
  stone: [['polished_andesite', 5], ['stone_bricks', 3], ['andesite', 1]],
  skirt: 'polished_andesite_stairs',
  wall: 'white_concrete',
  trim: 'polished_diorite',
  trimStairs: 'polished_diorite_stairs',
  trimSlab: 'polished_diorite_slab',
  band: 'red_concrete',
  roof: { full: 'mangrove_planks', stairs: 'mangrove_stairs', slab: 'mangrove_slab' },
  lining: 'white_concrete',
  floor: [['polished_diorite', 3], ['smooth_stone', 1]],
  wood: 'birch',
  dark: 'spruce',
  glass: 'glass_pane',
  runner: 'red_carpet',
  fence: 'birch_fence',
  post: 'diorite_wall',
  path: 'gravel',
  paving: [['stone_bricks', 3], ['polished_andesite', 1]],
  bedRim: 'stone_bricks', bedCap: 'stone_brick_slab', soil: 'coarse_dirt', // the garden's beds (II)
}

// dome: each ring's top, crown (0) to the drum's own wall ring (5). Round 2: the dome springs straight off the drum's
// white cornice, whose lip is the one even overhang all round (round 1 hung a red eave ring over the lip that stepped
// unevenly against the next ring up); a steep shoulder (ring 4, two courses) then flatter rings to the eye
export const DRUM = { cx: 6, cz: 6, r: 5, wallTop: 3, bandY: 4, corniceY: 5, domeY: 6,
  dome: (process.env.DOME ?? '10,10,9,9,8,6').split(',').map(Number), eyeY: 11, // the eye's lantern stands on ring 1
  vertices: [[3, 1], [9, 1], [1, 4], [11, 4], [1, 8], [11, 8], [3, 11], [9, 11]] }

// Bays (round 2): the drum's three flat faces (west, east, back) stand one block proud of the diagonals on the skirt's
// ring, a diorite pilaster at each octagon vertex from the plinth to the eave and the windows set back a block in the
// drum's wall behind an open reveal, a moulded sill at its foot. The front's canopy piers are the fourth bay. [x, z]
export const BAYS = [
  { out: 'west', cells: [[0, 4], [0, 5], [0, 6], [0, 7], [0, 8]], pilasters: [[0, 4], [0, 8]], open: [[0, 6]] },
  { out: 'east', cells: [[12, 4], [12, 5], [12, 6], [12, 7], [12, 8]], pilasters: [[12, 4], [12, 8]], open: [[12, 6]] },
  { out: 'south', cells: [[3, 12], [4, 12], [5, 12], [6, 12], [7, 12], [8, 12], [9, 12]], pilasters: [[3, 12], [9, 12]], open: [[4, 12], [6, 12], [8, 12]] },
]

export const FIXED = {
  machine: [6, 1, 7], nurse: [6, 1, 8], pc: [10, 1, 7], camera: [8.5, 2.7, 4.2],
  shopCounter: [15, 1, 7], pasture: [9, 1, 19],
}

export const OPENINGS = {
  frontDoor: { at: [6, 1, 1], step: [6, 0, 0] },
  shopFront: [[4, 1], [5, 1], [7, 1], [8, 1]], // [x, z]: panes y 1..3 either side of the door, a pane over it
  windows: [ // [x, y0, z, out]: two-high windows (y0, y0 + 1) with a diorite sill
    [1, 2, 6, 'west'], [11, 2, 6, 'east'], [4, 2, 11, 'south'], [6, 2, 11, 'south'], [8, 2, 11, 'south'],
  ],
  // II: the arch from the lobby into the Trade Hall (the east window becomes it), the wing's door, the garden doors
  arch: [[11, 6], [12, 6]],
  wingDoor: { at: [15, 1, 2], step: [15, 0, 1] },
  backDoor: { at: [6, 1, 11] }, // the Nurse's door to the garden (II: the middle back window)
  wingBackDoor: { at: [15, 1, 10] },
}

// II, the garden (round 2): the cross walk's row, and four raised beds of soil in the corners, each with its planting
// (a two-high plant as { id, tall }); rims x0..x1, z0..z1, soil inside; each kerbed flush (no coping:
// the soil and the planting read, not a stone box)
const ROSE = { id: 'rose_bush', tall: true }, PEONY = { id: 'peony', tall: true }, LILAC = { id: 'lilac', tall: true }
export const GARDEN = {
  crossZ: 15,
  beds: [
    { x0: 1, x1: 4, z0: 13, z1: 16, plants: [ROSE, 'allium', 'poppy', PEONY] },
    { x0: 16, x1: 18, z0: 13, z1: 16, plants: [LILAC, 'white_tulip'] },
    { x0: 1, x1: 6, z0: 21, z1: 24, plants: ['poppy', 'oxeye_daisy', 'red_tulip', 'white_tulip', ROSE, 'azure_bluet', 'poppy', PEONY] },
    { x0: 12, x1: 18, z0: 21, z1: 24, plants: ['white_tulip', 'poppy', ROSE, 'azure_bluet', 'red_tulip', 'oxeye_daisy', PEONY, 'poppy', 'allium', 'white_tulip'] },
  ],
}

// Every piece of furniture, footprint first (furniture.py kinds; fixtures are blocks the game uses as themselves).
export const FURNITURE = [
  // Lobby (round 2): two rows of waiting seats facing the desk either side of the runner, lamps on side tables in the
  // front corners, ferns by the door, azaleas in the corners behind the seats
  { level: 1, room: 'Lobby', kind: 'couch', part: 'couch', pos: [5, 1, 5], facing: 'south', length: 4, wood: 'birch', colour: 'red' },
  { level: 1, room: 'Lobby', kind: 'couch', part: 'couch', pos: [10, 1, 5], facing: 'south', length: 4, wood: 'birch', colour: 'red' },
  { level: 1, room: 'Lobby', kind: 'lamp', part: 'tableLamp', pos: [3, 1, 3], facing: 'east', wood: 'birch' },
  { level: 1, room: 'Lobby', kind: 'lamp', part: 'tableLamp', pos: [9, 1, 3], facing: 'west', wood: 'birch' },
  { level: 1, room: 'Lobby', kind: 'fixture', part: 'pot', pos: [4, 1, 2], block: 'potted_fern' },
  { level: 1, room: 'Lobby', kind: 'fixture', part: 'pot', pos: [8, 1, 2], block: 'potted_fern' },
  { level: 1, room: 'Lobby', kind: 'fixture', part: 'pot', pos: [2, 1, 4], block: 'potted_flowering_azalea_bush' },
  { level: 1, room: 'Lobby', kind: 'fixture', part: 'pot', pos: [10, 1, 4], block: 'potted_flowering_azalea_bush' },
  { level: 1, room: 'Lobby', kind: 'rug', part: 'rug', cellsAbs: [[6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6]], colour: 'red' },
  // The desk: counters either side of the machine (the Nurse uses them facing north, toward the lobby)
  { level: 1, room: 'Desk', kind: 'counter', part: 'desk', cells: [[3, 1, 7], [4, 1, 7], [5, 1, 7], [7, 1, 7], [8, 1, 7], [9, 1, 7]], facing: 'north', wood: 'birch' },
  // Back room, the Nurse's corner: two chests for the remedies against the drum, a cupboard, a bookcase with her
  // flowers, and her writing table under the east back window with a lamp on it and a chair at it
  { level: 1, room: 'Back room', kind: 'fixture', part: 'chest', pos: [3, 1, 9], facing: 'east', block: 'chest' },
  { level: 1, room: 'Back room', kind: 'fixture', part: 'chest', pos: [9, 1, 9], facing: 'west', block: 'chest' },
  { level: 1, room: 'Back room', kind: 'fixture', part: 'bookcase', pos: [4, 1, 10], plant: 'potted_red_tulip' },
  { level: 1, room: 'Back room', kind: 'cupboard', part: 'cupboard', pos: [5, 1, 10], facing: 'north', wood: 'birch' },
  { level: 1, room: 'Back room', kind: 'cupboard', part: 'cupboard', pos: [7, 1, 10], facing: 'north', wood: 'birch' },
  { level: 1, room: 'Back room', kind: 'lamp', part: 'tableLamp', pos: [8, 1, 10], facing: 'north', wood: 'birch' },
  { level: 1, room: 'Back room', kind: 'chair', part: 'chair', pos: [8, 1, 9], facing: 'south', wood: 'birch' },
  // the hall's lamp: a lantern on a long chain from the eye's cap, over the desk (drawn after the dome)
  { level: 1, room: 'Lobby', kind: 'lamp', part: 'hangingLamp', pos: [6, 5, 6], facing: 'north', chain: 8, wood: 'birch' },

  // II, the Trade Hall: the Shop Counter between two counters, the Trader's chest and cupboard behind, seats for buyers
  { level: 2, room: 'Trade Hall', kind: 'counter', part: 'desk', cells: [[14, 1, 7], [16, 1, 7]], facing: 'north', wood: 'birch' },
  { level: 2, room: 'Trade Hall', kind: 'fixture', part: 'chest', pos: [13, 1, 9], facing: 'east', block: 'chest' },
  { level: 2, room: 'Trade Hall', kind: 'cupboard', part: 'cupboard', pos: [17, 1, 9], facing: 'west', wood: 'birch' },
  { level: 2, room: 'Trade Hall', kind: 'table', part: 'table', pos: [16, 1, 4], facing: 'north', wood: 'birch', colour: 'white' },
  { level: 2, room: 'Trade Hall', kind: 'chair', part: 'chair', pos: [17, 1, 4], facing: 'west', wood: 'birch' },
  { level: 2, room: 'Trade Hall', kind: 'chair', part: 'chair', pos: [16, 1, 5], facing: 'north', wood: 'birch' },
  { level: 2, room: 'Trade Hall', kind: 'lamp', part: 'tableLamp', pos: [13, 1, 3], facing: 'east', wood: 'birch' },
  { level: 2, room: 'Trade Hall', kind: 'lamp', part: 'hangingLamp', pos: [15, 4, 5], facing: 'north', chain: 4, wood: 'birch' },
  { level: 2, room: 'Trade Hall', kind: 'lamp', part: 'hangingLamp', pos: [15, 4, 8], facing: 'north', chain: 4, wood: 'birch' },
]

// The walking line: street -> step -> door -> runner -> the desk's front (6, 1, 6); round the desk's west end to the
// Nurse's cell; II: through the arch into the Trade Hall to the Shop Counter's front (15, 1, 6), out its back door to
// the garden path and the pavilion.
export const WALK = [
  [6, 0, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [5, 1, 6], [4, 1, 6], [3, 1, 6],
  [2, 1, 6], [2, 1, 7], [2, 1, 8], [3, 1, 8], [4, 1, 8], [5, 1, 8], [6, 1, 8],
  [7, 1, 6], [8, 1, 6], [9, 1, 6], [10, 1, 6], [11, 1, 6], [12, 1, 6], [13, 1, 6], [14, 1, 6], [15, 1, 6],
  [15, 1, 8], [15, 1, 9], [15, 1, 10], [15, 1, 11], [15, 1, 12], [15, 1, 15], [9, 1, 15], [9, 1, 16], [9, 1, 18],
]
