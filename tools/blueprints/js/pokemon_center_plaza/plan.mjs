// The Pokémon Center's Sunny Plaza look (looks/plaza/pokemon_center, _2), the plan: drawn before any wall (ROADMAP 49,
// tools/blueprints/briefs/pokemon_center.md, "The looks"). Same contract as the Red Dome (../pokemon_center): machine
// (6, 1, 7), the Nurse's cell (6, 1, 8), the PC (10, 1..2, 7), the camera eye (8.5, 2.7, 4.2) with a clear line to the
// machine, a chest by the machine, II adds a Pasture Block and a Shop Counter. Another shape, not a palette.
//
// Story: "The Sunny Plaza". In a warm town the clinic opens onto the square: a white hall under a low red tile roof
// with a three-arched loggia in front, a roof terrace over the loggia behind a sandstone balustrade, and a bell-cote
// over the middle arch where a lantern hangs instead of a bell (it lights the way in at night). Shade first, then the
// desk. Later (II) the hall opens at the back onto its own paved court: a fountain, a market arcade along the east side
// where the Trader keeps his counter, a lawn bed with the Pasture Block, orange trees in pots, lamps at the corners.
//
// Named parts:
//   Hall      x 1..11, z 3..11, white walls y 1..4 on a sandstone socle, a red band at y 5, a flat ceiling at y 6.
//   Roof      a low hipped roof of brick tiles (half pitch, slabs) over x 0..12, z 2..12, its top at y 8.
//   Loggia    z 0..2 in front: four sandstone piers, a round arch over the door between two square side bays, a sandstone ceiling at y 4, benches.
//   Terrace   the loggia's flat roof, a balustrade of sandstone wall round it.
//   Bell-cote over the middle arch: two piers, an arched opening with its lantern, a small pediment (top y 10, its lightning rod).
//   Hall in   the lobby z 4..6 (settees, lamps), the desk across z 7, the Nurse's side z 8..10.
//   Court (II) z 12..25: paved, a low wall with a gate at the back, the fountain, lamp posts, pots, the lawn bed and
//             the Pasture Block, benches.
//   Market (II) x 14..18, z 12..24: a market hall whose front on the court is an arcade of glazed arches (the middle
//             bay five wide with the door), under a low gable of tiles: a lane for buyers, the counter, the Trader behind.

export const SIZE = { 1: [13, 11, 13], 2: [20, 11, 26] }

// Palette (60/30/10): white plaster (concrete) / red brick tiles and warm sandstone / a red band and red awning cloth.
// Why: a sunny town's whitewash under clay tiles, sandstone for every dressed edge (piers, arches, sills, balustrade).
export const PALETTE = {
  socle: [['smooth_sandstone', 3], ['sandstone', 1]],
  wall: 'white_concrete',
  dress: 'cut_sandstone',
  dressStairs: 'sandstone_stairs',
  dressSlab: 'smooth_sandstone_slab',
  baluster: 'sandstone_wall',
  band: 'red_concrete',
  roof: { full: 'bricks', stairs: 'brick_stairs', slab: 'brick_slab' },
  ceiling: 'smooth_sandstone',
  floor: [['smooth_sandstone', 3], ['terracotta', 1]],
  paving: [['smooth_sandstone', 4], ['sandstone', 1], ['terracotta', 1]],
  wood: 'jungle',
  glass: 'glass_pane',
  key: 'chiseled_sandstone',
}

export const HALL = { x0: 1, x1: 11, z0: 3, z1: 11, top: 4, band: 5, ceiling: 6, roof: { x0: 0, z0: 2, x1: 12, z1: 12, eaveY: 6, low: true } }
export const LOGGIA = { z0: 0, z1: 2, piers: [1, 4, 8, 11], bays: [[2, 3], [5, 7], [9, 10]], ceiling: 4 }

export const FIXED = { machine: [6, 1, 7], nurse: [6, 1, 8], pc: [10, 1, 7], camera: [8.5, 2.7, 4.2], shopCounter: [16, 1, 18], pasture: [4, 1, 21] }

export const OPENINGS = {
  frontDoor: [6, 1, 3],
  windows: [ // [x, z, out]: two-high panes (y 2..3) with a sandstone sill and head
    [3, 3, 'north'], [9, 3, 'north'], [1, 5, 'west'], [1, 9, 'west'], [11, 5, 'east'], [11, 9, 'east'],
    [4, 11, 'south'], [8, 11, 'south'],
  ],
  backWindow: [6, 11, 'south'], // I: a window; II: the door to the court
  loggiaSides: [[1, 1], [11, 1]], // [x, z]: a pane window in each side wall of the loggia
}

export const FURNITURE = [
  // Lobby: settees under the side windows, lamps hung from the ceiling, plants by the door
  { level: 1, room: 'Lobby', kind: 'couch', part: 'couch', pos: [2, 1, 4], facing: 'east', length: 2, wood: 'jungle', colour: 'orange' },
  { level: 1, room: 'Lobby', kind: 'couch', part: 'couch', pos: [10, 1, 5], facing: 'west', length: 2, wood: 'jungle', colour: 'orange' },
  { level: 1, room: 'Lobby', kind: 'fixture', part: 'pot', pos: [5, 1, 4], block: 'potted_flowering_azalea_bush' },
  { level: 1, room: 'Lobby', kind: 'fixture', part: 'pot', pos: [7, 1, 4], block: 'potted_flowering_azalea_bush' },
  { level: 1, room: 'Lobby', kind: 'lamp', part: 'hangingLamp', pos: [4, 5, 5], facing: 'north', wood: 'jungle' },
  { level: 1, room: 'Lobby', kind: 'lamp', part: 'hangingLamp', pos: [8, 5, 5], facing: 'north', wood: 'jungle' },
  { level: 1, room: 'Lobby', kind: 'rug', part: 'rug', cellsAbs: [[6, 1, 5], [6, 1, 6]], colour: 'red' },
  // the desk
  { level: 1, room: 'Desk', kind: 'counter', part: 'desk', cells: [[3, 1, 7], [4, 1, 7], [5, 1, 7], [7, 1, 7], [8, 1, 7], [9, 1, 7]], facing: 'north', wood: 'jungle' },
  // the Nurse's side
  { level: 1, room: 'Back room', kind: 'fixture', part: 'chest', pos: [2, 1, 9], facing: 'east', block: 'chest' },
  { level: 1, room: 'Back room', kind: 'fixture', part: 'chest', pos: [10, 1, 10], facing: 'west', block: 'chest' },
  { level: 1, room: 'Back room', kind: 'cupboard', part: 'cupboard', pos: [5, 1, 10], facing: 'north', wood: 'jungle' },
  { level: 1, room: 'Back room', kind: 'cupboard', part: 'cupboard', pos: [7, 1, 10], facing: 'north', wood: 'jungle' },
  { level: 1, room: 'Back room', kind: 'lamp', part: 'hangingLamp', pos: [6, 5, 9], facing: 'north', wood: 'jungle' },

  // II, the arcade: the Shop Counter between two counters, the Trader's chest behind, crates of berries
  { level: 2, room: 'Market hall', kind: 'counter', part: 'desk', cells: [[16, 1, 17], [16, 1, 19]], facing: 'west', wood: 'jungle' },
  { level: 2, room: 'Market hall', kind: 'fixture', part: 'chest', pos: [17, 1, 21], facing: 'west', block: 'chest' },
  { level: 2, room: 'Market hall', kind: 'cupboard', part: 'cupboard', pos: [17, 1, 15], facing: 'west', wood: 'jungle' },
  { level: 2, room: 'Market hall', kind: 'lamp', part: 'hangingLamp', pos: [17, 4, 18], facing: 'west', wood: 'jungle' },
  { level: 2, room: 'Market hall', kind: 'lamp', part: 'hangingLamp', pos: [15, 4, 14], facing: 'west', wood: 'jungle' },
  { level: 2, room: 'Market hall', kind: 'lamp', part: 'hangingLamp', pos: [15, 4, 22], facing: 'west', wood: 'jungle' },
  { level: 2, room: 'Market hall', kind: 'couch', part: 'couch', pos: [15, 1, 23], facing: 'north', length: 1, wood: 'jungle', colour: 'orange' },
]

// The walking line: under the middle arch to the door, along the runner to the desk's front (6, 1, 6), round its west
// end to the Nurse's cell; II: out the back door into the court, round the fountain, in at the market hall's door to
// the Shop Counter's front (15, 1, 18), and to the lawn bed's Pasture Block.
export const WALK = [
  [6, 1, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [2, 1, 6], [2, 1, 7], [2, 1, 8], [6, 1, 8],
  [6, 1, 11], [6, 1, 12], [6, 1, 14], [12, 1, 15], [13, 1, 18], [14, 1, 18], [15, 1, 18], [15, 1, 16], [16, 1, 16], [17, 1, 16], [17, 1, 18], [6, 1, 21],
]
