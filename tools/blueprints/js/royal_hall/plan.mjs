// The Royal Hall (realm/royal_hall, realm/royal_hall_2), rebuilt from scratch for ROADMAP 51.9 from the brief
// (tools/blueprints/briefs/royal_hall.md) alone: the plan. recipe.mjs draws it in passes.
//
// Design notes. Three rough concepts:
//   A. "The Crossing": a cross-shaped hall, the seat of state under a square lantern tower at the crossing, the vault in
//      one transept. Strong from above, but from the street a cross reads as a church.
//   B. "The Long Barn of Kings": one long timber hall on a stone undercroft, the vault below, steps up the side. Honest
//      and cheap, but a capital's hall that looks like a barn sells the realm short.
//   C. "The Brick Palace" (chosen): a red-brick Tudor-style palace hall entered between two gate towers with pointed
//      green copper caps on corbelled rings; a proclamation balcony over the porch between them, where the ruler shows
//      himself to the town; the hall a hipped slate box with stone-dressed mullion windows; the throne in its own bay
//      pushed out of the back wall; the vault a battlemented strongroom wing on the west, the steward's room its twin
//      on the east. Strongest: the two caps and the balcony make an outline you know from 20 blocks, brick and stone
//      dressings give every face light and shadow, and the plan reads like a court: gate, hall, dais, throne.
//   Level II grows out of the steward's room: an east range of two storeys (the treasury below with the east door,
//   the council chamber above) ending in a tall belfry tower at its back corner, so the palace becomes an L round the
//   hall and the skyline gets a third, higher point.
//
// Palette 60/30/10: red brick (60, walls), stone brick and smooth stone dressings (30: plinth, quoins, mullions,
// parapets), dark oak and copper (10: timber, doors, the caps). Roof: deepslate tiles on the hall and wings, waxed
// oxidized copper on the towers. One glass type: panes. Walking line: steps -> porch -> hall door (9,1,6) -> the red
// runner -> the dais step (z 15) -> the throne bay; the vault door (3,2,17) and the steward's door (15,2,17) open off
// the dais; II: the steward's room opens east into the treasury (east door (25,1,14)), a stair up to the council.

export const LEVEL = Number(process.env.LEVEL ?? 1)
export const SIZE = LEVEL === 2 ? [27, 25, 25] : [19, 25, 25]

export const PALETTE = {
  brick: [['bricks', 14], ['bricks', 6], ['granite', 1]],
  stone: 'stone_bricks', stoneMix: [['stone_bricks', 8], ['cracked_stone_bricks', 2], ['mossy_stone_bricks', 1]],
  stoneStairs: 'stone_brick_stairs', stoneSlab: 'stone_brick_slab', stoneWall: 'stone_brick_wall',
  dress: 'smooth_stone', dressSlab: 'smooth_stone_slab',
  floor: 'polished_andesite', floorAlt: 'polished_deepslate',
  dais: 'polished_granite', daisStep: 'polished_granite_stairs',
  wood: 'dark_oak', log: 'dark_oak_log', planks: 'dark_oak_planks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  cap: { full: 'waxed_oxidized_cut_copper', stairs: 'waxed_oxidized_cut_copper_stairs', slab: 'waxed_oxidized_cut_copper_slab' },
  glass: 'glass_pane', bars: 'iron_bars',
  royal: 'red', gold: 'yellow',
}

// Masses (x0..x1, z0..z1 are wall lines)
export const MASSES = {
  hall: { x0: 3, x1: 15, z0: 6, z1: 20, wallTop: 10, eaveY: 10 },
  towers: [{ x0: 1, x1: 5, z0: 1, z1: 6 }, { x0: 13, x1: 17, z0: 1, z1: 6 }], // walls to y 15, corbel ring at 16, cap above
  porch: { x0: 6, x1: 12, z0: 1, z1: 5, deckY: 5 },
  throneBay: { x0: 7, x1: 11, z0: 20, z1: 23, top: 8 },
  vault: { x0: 0, x1: 3, z0: 13, z1: 20, top: 5 }, // west wing: the realm's strongroom
  steward: { x0: 15, x1: 18, z0: 13, z1: 20, top: 5 }, // east wing: the steward's room (II's way into the east range)
  // Level II
  range: { x0: 18, x1: 25, z0: 8, z1: 20, upper: 6, top: 11 }, // the east range: treasury below, council above
  belfry: { x0: 20, x1: 25, z0: 20, z1: 24, top: 17 },
}

export const OPENINGS = {
  frontDoor: { at: [9, 1, 6], facing: 'north' },
  vaultDoor: { at: [3, 2, 17], facing: 'west' },
  stewardDoor: { at: [15, 2, 17], facing: 'east' },
  eastDoor: { at: [25, 1, 14], facing: 'east' }, // II
}

const T = (pos, colour = 'white') => ({ kind: 'table', part: 'table', pos, facing: 'north', wood: 'dark_oak', colour })
const C = (pos, facing) => ({ kind: 'chair', part: 'chair', pos, facing, wood: 'dark_oak' })
const L = (pos, chain = 0) => ({ kind: 'lamp', part: 'hangingLamp', pos, facing: 'north', wood: 'dark_oak', chain })

export const FURNITURE = [
  // The hall: two long feast boards down the side aisles, benches of chairs both sides, chandeliers under the tie beams
  ...[8, 9, 10, 11].flatMap(z => [{ room: 'Hall', ...T([6, 1, z]) }, { room: 'Hall', ...C([5, 1, z], 'east') }, { room: 'Hall', ...C([7, 1, z], 'west') },
    { room: 'Hall', ...T([12, 1, z]) }, { room: 'Hall', ...C([11, 1, z], 'east') }, { room: 'Hall', ...C([13, 1, z], 'west') }]),
  ...[[6, 9], [12, 9], [6, 12], [12, 12], [9, 15]].map(([x, z]) => ({ room: 'Hall', ...L([x, 7, z]) })),
  { room: 'Hall', kind: 'rug', part: 'rug', cellsAbs: [8, 9, 10, 11, 12, 13, 14].map(z => [9, 1, z]), colour: 'red' },
  { room: 'Hall', kind: 'picture', part: 'picture', pos: [4, 5, 13], facing: 'east', wood: 'dark_oak', colour: 'red' },
  { room: 'Hall', kind: 'picture', part: 'picture', pos: [14, 5, 13], facing: 'west', wood: 'dark_oak', colour: 'red' },
  { room: 'Hall', kind: 'picture', part: 'picture', pos: [4, 5, 7], facing: 'east', wood: 'dark_oak', colour: 'yellow' },
  { room: 'Hall', kind: 'picture', part: 'picture', pos: [14, 5, 7], facing: 'west', wood: 'dark_oak', colour: 'yellow' },
  { room: 'Hall', kind: 'clock', part: 'clock', pos: [4, 1, 14], facing: 'east', wood: 'dark_oak' },
  { room: 'Hall', kind: 'cupboard', part: 'cupboard', pos: [14, 1, 14], facing: 'west', wood: 'dark_oak' },
  // The dais and the throne bay: the seat of state, the councillors' chairs either side, lamps on side tables
  { room: 'Throne', kind: 'chair', part: 'armchair', pos: [9, 2, 22], facing: 'north', wood: 'dark_oak', colour: 'red' },
  { room: 'Throne', kind: 'chair', part: 'armchair', pos: [6, 2, 18], facing: 'east', wood: 'dark_oak', colour: 'red' },
  { room: 'Throne', kind: 'chair', part: 'armchair', pos: [12, 2, 18], facing: 'west', wood: 'dark_oak', colour: 'red' },
  { room: 'Throne', kind: 'lamp', part: 'tableLamp', pos: [5, 2, 19], facing: 'east', wood: 'dark_oak' },
  { room: 'Throne', kind: 'lamp', part: 'tableLamp', pos: [13, 2, 19], facing: 'west', wood: 'dark_oak' },
  { room: 'Throne', kind: 'rug', part: 'rug', cellsAbs: [[9, 2, 16], [9, 2, 17], [9, 2, 18], [9, 2, 19], [9, 2, 20], [9, 2, 21], [8, 2, 21], [10, 2, 21]], colour: 'red' },
  // The vault: four chests in two pairs on the west wall, a lamp from the ceiling
  ...[14, 15, 18, 19].map(z => ({ room: 'Vault', kind: 'fixture', part: 'chest', pos: [1, 2, z], facing: 'east', block: 'chest' })),
  { room: 'Vault', ...L([2, 5, 16]) },
  // The steward's room: a desk and chair under the window, a cupboard of rolls, a lamp
  { room: 'Steward', kind: 'desk', part: 'desk', pos: [17, 2, 15], facing: 'west', wood: 'dark_oak' },
  { room: 'Steward', kind: 'cupboard', part: 'cupboard', pos: [17, 2, 19], facing: 'west', wood: 'dark_oak' },
  { room: 'Steward', ...L([16, 5, 16]) },
]

// Level II's east range: the treasury (ground) and the council chamber (upper)
export const FURNITURE_2 = [
  ...[10, 11, 12].flatMap(z => [{ room: 'Treasury', kind: 'fixture', part: 'chest', pos: [24, 1, z], facing: 'west', block: 'chest' }]),
  ...[17, 18].map(z => ({ room: 'Treasury', kind: 'fixture', part: 'chest', pos: [24, 1, z], facing: 'west', block: 'chest' })),
  ...[10, 11, 12].map(x => ({ room: 'Treasury', kind: 'fixture', part: 'chest', pos: [x + 10, 1, 9], facing: 'south', block: 'chest' })),
  { room: 'Treasury', ...T([21, 1, 13], 'green') }, { room: 'Treasury', ...T([21, 1, 14], 'green') },
  { room: 'Treasury', ...C([20, 1, 13], 'east') }, { room: 'Treasury', ...C([22, 1, 14], 'west') },
  { room: 'Treasury', ...L([21, 5, 11]) }, { room: 'Treasury', ...L([21, 5, 16]) },
  // the council chamber over it: a long council table, chairs both sides, the realm's banners
  ...[11, 12, 13, 14].flatMap(z => [{ room: 'Council', ...T([22, 7, z], 'red') }, { room: 'Council', ...C([21, 7, z], 'east') }, { room: 'Council', ...C([23, 7, z], 'west') }]),
  { room: 'Council', ...L([22, 9, 12]) }, { room: 'Council', ...L([22, 9, 16]) },
  { room: 'Council', kind: 'picture', part: 'picture', pos: [24, 9, 17], facing: 'west', wood: 'dark_oak', colour: 'red' },
  { room: 'Council', kind: 'cupboard', part: 'cupboard', pos: [24, 7, 18], facing: 'west', wood: 'dark_oak' },
]
