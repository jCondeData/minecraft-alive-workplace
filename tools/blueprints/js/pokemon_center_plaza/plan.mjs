// The Pokémon Center's Sunny Plaza look (looks/plaza/pokemon_center, _2), the plan: drawn before any wall (ROADMAP 49,
// tools/blueprints/briefs/pokemon_center.md, "The looks"). Same contract as the Red Dome (../pokemon_center): machine
// (6, 1, 7), the Nurse's cell (6, 1, 8), the PC (10, 1..2, 7), the camera eye (8.5, 2.7, 4.2) with a clear line to the
// machine, a chest by the machine, II adds a Pasture Block and a Shop Counter. Another shape, not a palette.
//
// Story: "The Sunny Plaza". In a warm town the clinic opens onto the square: a sandstone hall under one low hipped roof
// of red tiles, wrapped on every side by a shallow arcade (round heads on piers, the windows set back in their shade, a
// red frieze running all the way round under the eave), a deeper loggia in front whose flat roof is a terrace, and a
// bell-cote rising over the middle arch where a lantern hangs instead of a bell (it lights the way in at night). Shade
// first, then the desk. Later (II) the hall's back door opens onto its own paved court: a fountain ringed by benches and
// flower beds in soil, a market hall along the east side (a glazed arcade on the court, an open arcade on its back lane)
// where the Trader keeps his counter, a lawn bed with the Pasture Block, lamps at the corners.
//
// Round 2 (the critic's notes, briefs/cobblemon_50_12_critic.md): one clean hip (tileHip: slab and stair courses, a
// continuous eave); the arcade carried round the sides and back (tileArcade); one wall material, sandstone, with the red
// frieze as its single trim; the market hall's back lane side an arcade too; benches and planters in soil round the
// fountain; a furnished Nurse's office behind the desk and table lamps by the waiting couches. See NOTES.md.
//
// Named parts:
//   Hall      x 1..11, z 3..11: sandstone walls y 1..5 on a smooth socle course, open inside to the hip's underside.
//   Arcade    one block proud of the hall on the west (x 0), east (x 12) and back (z 12): a plinth at y 0, piers to y 4,
//             round heads over three-wide bays (keystone at y 4), the red frieze at y 5. Bays line up with the windows.
//   Roof      one low hip of brick tiles over x 0..12, z 2..12 (the arcades' outer line), eave at y 6, ridge at y 8.
//   Loggia    z 0..2 in front: four sandstone piers, a round arch over the door between two square side bays, a sandstone
//             ceiling at y 4 with the red frieze on its front, benches, lanterns.
//   Terrace   the loggia's flat roof (y 5), a balustrade of sandstone wall round it, the roof's eave over its back row.
//   Bell-cote over the middle arch, two deep: piers y 5..8, a tall opening with its lantern, the red frieze at y 9, a cap and a little tile
//             gable, a lightning rod at y 12 (the hero: half again the ridge's height).
//   Hall in   the lobby z 4..6 (couches, a lamp on a low table before each, plants, a runner), the desk across z 7 (the machine in
//             it, the PC at its east end, a two-wide gap at its west end), the Nurse's office z 8..10.
//   Court (II) z 13..25: paved, a low wall with a gate at the back, the fountain ringed by four benches and four soil
//             beds, lamp posts, potted azaleas, the lawn bed with the Pasture Block.
//   Market (II) x 14..18, z 12..24: a market hall whose court front is an arcade of glazed arches (the middle bay five
//             wide with the door), its back lane side an arcade at x 19 like the hall's, under a 45-degree tile gable.

export const SIZE = { 1: [13, 13, 13], 2: [20, 13, 26] }

// Palette (60/30/10): sandstone (walls, piers, arches, paving) / red brick tiles / the red frieze and red cloths.
// Why: a warm town's one stone for every wall and dressed edge, told apart by texture (rough walls, cut piers, smooth
// socle and ceilings, chiseled keys) under clay tiles; a single red frieze ties every face together.
export const PALETTE = {
  socle: 'smooth_sandstone',
  wall: 'sandstone',
  dress: 'cut_sandstone',
  dressStairs: 'sandstone_stairs',
  dressSlab: 'smooth_sandstone_slab',
  baluster: 'sandstone_wall',
  band: 'red_concrete',
  roof: { full: 'bricks', stairs: 'brick_stairs', slab: 'brick_slab' },
  ceiling: 'smooth_sandstone',
  floor: [['smooth_sandstone', 3], ['terracotta', 1]],
  paving: [['smooth_sandstone', 4], ['cut_sandstone', 1], ['terracotta', 1]],
  wood: 'jungle',
  glass: 'glass_pane',
  key: 'chiseled_sandstone',
  soil: 'grass_block',
}

export const HALL = { x0: 1, x1: 11, z0: 3, z1: 11, top: 5, ceiling: 5, roof: { x0: 0, z0: 2, x1: 12, z1: 12, eaveY: 6 } }
export const LOGGIA = { z0: 0, z1: 2, piers: [1, 4, 8, 11], bays: [[2, 3], [5, 7], [9, 10]], ceiling: 4 }
// the arcade round the sides and back: [axis, plane, from, to, piers]
export const ARCADES = [
  ['z', 0, 3, 12, [3, 7, 11, 12]],
  ['z', 12, 3, 12, [3, 7, 11, 12]],
  ['x', 12, 0, 12, [0, 4, 8, 12]],
]
export const COTE = { x0: 5, x1: 7, z0: 0, z1: 1, y0: 5, top: 9 }

export const FIXED = { machine: [6, 1, 7], nurse: [6, 1, 8], pc: [10, 1, 7], camera: [8.5, 2.7, 4.2], shopCounter: [16, 1, 18], pasture: [3, 1, 22] }

export const OPENINGS = {
  frontDoor: [6, 1, 3],
  windows: [ // [x, z, out]: two-high panes (y 2..3) under a sandstone head, a sill stair one out; each in an arcade bay
    [3, 3, 'north'], [9, 3, 'north'], [1, 5, 'west'], [1, 9, 'west'], [11, 5, 'east'], [11, 9, 'east'],
    [2, 11, 'south'], [10, 11, 'south'],
  ],
  backWindow: [6, 11, 'south'], // I: a window; II: the door to the court
  loggiaSides: [[1, 1], [11, 1]], // [x, z]: a pane window in each side wall of the loggia
}

export const FURNITURE = [
  // Lobby: couches under the side windows, a low table with a lamp before each, plants by the door, lamps hung from the
  // roof on chains, a red runner from the door to the desk
  { level: 1, room: 'Lobby', kind: 'couch', part: 'couch', pos: [2, 1, 4], facing: 'east', length: 2, wood: 'jungle', colour: 'orange' },
  { level: 1, room: 'Lobby', kind: 'couch', part: 'couch', pos: [10, 1, 5], facing: 'west', length: 2, wood: 'jungle', colour: 'orange' },
  { level: 1, room: 'Lobby', kind: 'lamp', part: 'tableLamp', pos: [3, 1, 4], facing: 'west', wood: 'jungle' },
  { level: 1, room: 'Lobby', kind: 'lamp', part: 'tableLamp', pos: [9, 1, 4], facing: 'east', wood: 'jungle' },
  { level: 1, room: 'Lobby', kind: 'fixture', part: 'pot', pos: [5, 1, 4], block: 'potted_flowering_azalea_bush' },
  { level: 1, room: 'Lobby', kind: 'fixture', part: 'pot', pos: [7, 1, 4], block: 'potted_flowering_azalea_bush' },
  { level: 1, room: 'Lobby', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 5], facing: 'north', wood: 'jungle' },
  { level: 1, room: 'Lobby', kind: 'lamp', part: 'hangingLamp', pos: [8, 4, 5], facing: 'north', wood: 'jungle' },
  { level: 1, room: 'Lobby', kind: 'rug', part: 'rug', cellsAbs: [[6, 1, 5], [6, 1, 6]], colour: 'red' },
  // the desk: counters either side of the machine, the PC at its east end, a two-wide way through at its west end
  { level: 1, room: 'Desk', kind: 'counter', part: 'desk', cells: [[4, 1, 7], [5, 1, 7], [7, 1, 7], [8, 1, 7], [9, 1, 7]], facing: 'north', wood: 'jungle' },
  // the Nurse's office: her remedies (two chests, two cupboards), a bookcase with a plant under the back window, a
  // lamp table with her chair at it, a rug, a lamp hung over the room
  { level: 1, room: 'Office', kind: 'fixture', part: 'chest', pos: [2, 1, 9], facing: 'east', block: 'chest' },
  { level: 1, room: 'Office', kind: 'fixture', part: 'chest', pos: [10, 1, 10], facing: 'west', block: 'chest' },
  { level: 1, room: 'Office', kind: 'bookcase', part: 'bookcase', pos: [2, 1, 10], plant: 'potted_fern' },
  { level: 1, room: 'Office', kind: 'cupboard', part: 'cupboard', pos: [5, 1, 10], facing: 'north', wood: 'jungle' },
  { level: 1, room: 'Office', kind: 'cupboard', part: 'cupboard', pos: [7, 1, 10], facing: 'north', wood: 'jungle' },
  { level: 1, room: 'Office', kind: 'lamp', part: 'tableLamp', pos: [9, 1, 9], facing: 'west', wood: 'jungle' },
  { level: 1, room: 'Office', kind: 'chair', part: 'chair', pos: [8, 1, 9], facing: 'east', wood: 'jungle' },
  { level: 1, room: 'Office', kind: 'bookcase', part: 'bookcase', pos: [10, 1, 9], plant: 'potted_red_tulip' },
  { level: 1, room: 'Office', kind: 'rug', part: 'rug', cellsAbs: [[3, 1, 9], [4, 1, 9], [3, 1, 10], [4, 1, 10]], colour: 'white' },
  { level: 1, room: 'Office', kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 9], facing: 'north', wood: 'jungle' },

  // II, the market hall: the Shop Counter between two counters, the Trader's chest behind, a cupboard, lamps, a seat
  { level: 2, room: 'Market hall', kind: 'counter', part: 'desk', cells: [[16, 1, 17], [16, 1, 19]], facing: 'west', wood: 'jungle' },
  { level: 2, room: 'Market hall', kind: 'fixture', part: 'chest', pos: [17, 1, 21], facing: 'west', block: 'chest' },
  { level: 2, room: 'Market hall', kind: 'cupboard', part: 'cupboard', pos: [17, 1, 15], facing: 'west', wood: 'jungle' },
  { level: 2, room: 'Market hall', kind: 'lamp', part: 'hangingLamp', pos: [17, 4, 18], facing: 'west', wood: 'jungle' },
  { level: 2, room: 'Market hall', kind: 'lamp', part: 'hangingLamp', pos: [15, 4, 14], facing: 'west', wood: 'jungle' },
  { level: 2, room: 'Market hall', kind: 'lamp', part: 'hangingLamp', pos: [15, 4, 22], facing: 'west', wood: 'jungle' },
  { level: 2, room: 'Market hall', kind: 'couch', part: 'couch', pos: [15, 1, 23], facing: 'north', length: 1, wood: 'jungle', colour: 'orange' },
]

// The court (II): the fountain at (9, 18), a bench of three seats on each side of it (facing it), a 2 x 2 soil bed at
// each diagonal; the lawn bed (x 1..5, z 20..24) round the Pasture Block.
export const COURT = {
  fountain: [9, 18],
  benches: [[8, 10, 15, 'south'], [8, 10, 21, 'north'], [17, 19, 6, 'east'], [17, 19, 12, 'west']], // [a0, a1, line, facing]: along x for N/S, along z for W/E
  beds: [[6, 15], [11, 15], [6, 20], [11, 20]], // each bed's north-west cell
  lawn: { x0: 1, x1: 5, z0: 20, z1: 24 },
}

// The walking line: under the middle arch to the door, along the runner to the desk's front (6, 1, 6), through the gap
// at the desk's west end to the Nurse's cell; II: out the back door into the court, along its north side and down the
// fountain's east side to the market hall's door, to the Shop Counter's front (15, 1, 18), and to the lawn bed.
export const WALK = [
  [6, 1, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [3, 1, 6], [3, 1, 7], [3, 1, 8], [6, 1, 8],
  [6, 1, 11], [6, 1, 12], [6, 1, 14], [13, 1, 14], [13, 1, 18], [14, 1, 18], [15, 1, 18], [15, 1, 16], [16, 1, 16], [17, 1, 16], [17, 1, 18],
  [13, 1, 22], [6, 1, 22], [3, 1, 21],
]
