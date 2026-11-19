// Berry Nursery (berry_nursery, berry_nursery_2), the plan: drawn before any wall. Story, palette, masses, rooms,
// openings, every furniture footprint and the walking line come first; recipe.mjs then draws both levels from the parts
// library (../parts) in passes. Brief: tools/blueprints/briefs/berry_nursery.md (ROADMAP 49, blind rebuild).
//
// Three rough concepts (massing, roof, plan, story):
//   A. "The Rill Garden behind a Gatehouse." A brick walled garden quartered by a walk and a rill, entered through a
//      timber-and-brick seed house that straddles the way in like a lychgate; the composter stands at the crossing,
//      where the rill meets the walk. Roof: a steep slate roof along the front with a jettied cross gable over the door
//      and a louvred drying vent on the ridge. Level II closes the far end of the walk with a barrel-vaulted
//      glasshouse whose stove and chimney end the axis. Story: a formal breeding garden, every bed read from one walk.
//   B. "The Lath House." One long open-sided shade house (a slatted roof on posts) over every bed, a potting shed at
//      one end; Level II a second, taller bay. Roof: a single long gable of laths. Story: a working plant nursery.
//      Cheap and true to nurseries, but one long box under a roof that reads as unfinished (holes.py's roof pits).
//   C. "The Terraced Berry Banks." Beds on two raised tiers stepping down from a stone cistern tower that waters them
//      by spouts, the composter on the lower terrace; Level II roofs the upper tier with glass. Roof: the tower's
//      pyramid cap. Story: a hillside berry garden. Striking, but the tiers eat the block budget (every bed stands
//      on a filled box) and the beds lose their pairing at each step.
//   Built: A. It is the strongest, not the safest: the gatehouse gives the street a real face (a hero gable, a jetty,
//   a vent on the ridge) while the beds stay where the breeder works them, and every level grows along one axis
//   (gate, walk, composter, glasshouse door, stove) instead of adding a box beside a box. It also fits the rules best:
//   the walk and the rill quarter the beds into 4-wide blocks, paired along x and along z, every one within 4 of water
//   and within 2 of a path, and the glasshouse's beds stand inside the breeder's plot round the same composter.
//
// Story: the "Rill Garden". A breeder who crosses berries needs her rows read at a glance and kept apart, so she walls
// a garden in brick and quarters it: a walk down the middle from the gate to her composter, a rill across it that
// waters all four quarters, the composter at the crossing where she turns her mulch. You come in through her seed
// house, a brick lodge with a timber loft that straddles the way in: her potting bench and chests on one side, her
// desk and her book of crosses on the other, berries drying in the loft under a louvred vent. Level II: the walk runs on
// through the back wall into a glasshouse, a barrel vault of glass on white ribs, where the tender crosses grow under
// cover round a dipping tank, warmed by a stove at the end of the walk whose chimney stands up behind the vault.
//
// Coordinates: x across the front (west 0 -> east 12), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The axis is x = 6. Pins the ship step moves the tests to: the composter (6, 1, 11) in
// both levels; Level I farmland (2, 0, 8) and water (2, 0, 11); Level II farmland (3, 0, 17) and (9, 0, 18). Her plot:
// x -2..14, z 3..19, farmland y -2..2 (BerryBreeders.plot round the composter).
//
// Masses (named parts):
//   Seed House   brick ground storey x 2..10, z 2..6 (y 0..3), dark oak beam ring y 4, a loft under one broad 45-degree
//                slate gable facing the street (ridge along z, y 10), timbered gable faces in white daub front and back.
//   Hoist Gable  the front gable jettied a block out over the porch (face at z 1 on four brackets), the loft window on
//                the axis and a hoist beam with a lantern at its apex.
//   Vent         a louvred drying vent on the ridge over the walk (x 5..7, z 3..5, y 10..13).
//   Rill Garden  the low brick wall x 0 and x 12 (z 6..16) and z 16, piers at the corners, at the rill and at the back
//                of the walk; inside, four quarters of beds round the walk (x 6) and the rill (z 11).
//   Glass Wing   (Level II) x 2..10, z 16..21: the seed house's section again, in glass. A brick kerb and a stone sill
//                course, glass walls between dark oak posts standing proud of them (the corners, z 16 and 21) on brick piers, and the same 45-degree
//                roof (ridge y 10 along z, in line with the seed house's): slate eaves, a three-wide slate ridge cap, dark oak
//                rafters only at the two ends and one unbroken run of glass between; the front end glazed with the door on
//                the axis, the back end boarded like the seed house's gables (white daub, dark oak studs) round the flue.
//   Pergola      (Level II) two dark oak beams over the walk (x 5 and x 7, y 3) from the seed house's back wall to the
//                wing's door, log joists across them: the covered link, the beds either side still under open sky.
//   Stove Stack  (Level II) the stove in the wing's back wall at the end of the walk, its brick stack behind (z 22) on a
//                stepped brick base, up the back gable to a stone cap over the ridge.

export const LEVELS = {
  1: { id: 'berry_nursery', size: [13, 14, 17] },
  2: { id: 'berry_nursery_2', size: [13, 14, 23] },
}

// Palette (60/30/10): red brick (the garden wall and the seed house's ground storey) / slate roof, dark oak frame and
// white daub (the loft) / the glass wing's glass, the walk's stone and the flowers. Why: a walled kitchen garden's
// warm brick; slate contrasts with brick and keeps the roof calm; the glass wing is framed in the same dark oak and slate, so it reads as the seed house's own wing.
export const PALETTE = {
  stone: [['cobblestone', 5], ['stone', 3], ['andesite', 2], ['mossy_cobblestone', 1]],
  brick: 'bricks',
  brickWall: 'brick_wall',
  quoin: 'stone_bricks',
  cap: 'chiseled_stone_bricks', // the piers' caps, a lantern on some
  walk: [['stone_bricks', 6], ['mossy_stone_bricks', 1], ['cracked_stone_bricks', 1]],
  path: 'packed_mud',
  frame: 'dark_oak_log',
  trim: 'dark_oak',
  infill: [['white_concrete', 5], ['polished_diorite', 1]], // plaster: white with a little diorite
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'spruce_planks',
  loftFloor: 'spruce_planks',
  wood: 'spruce', // doors, trapdoors, the desk and chair, the counters
  glass: 'glass', // one glass type for the whole building: a glasshouse roof has to be whole blocks
  sill: 'stone_brick_stairs', // the glass wing's sill course, proud of its brick kerb
  mullion: 'dark_oak_log', // studs in the wing's glazed front end
  hearthStone: 'stone_bricks',
}

export const SEED_HOUSE = {
  walls: { x0: 2, x1: 10, z0: 2, z1: 6 },
  room: { x0: 3, x1: 9, z0: 3, z1: 5 }, // one workroom the walk passes through
  wallTop: 3, beamY: 4,
  roof: { axis: 'z', across: [1, 11], along: [0, 7], eaveY: 5, steep: false }, // one broad gable to the street
  hoist: { face: 1 }, // the front gable jettied out to z 1 over the porch
  vent: { cx: 6, cz: 4, y: 10 },
}

export const GARDEN = {
  wall: [[0, 6], [0, 16], [12, 16], [12, 6]],
  stubs: [[1, 6], [11, 6]], // short runs of wall from the corner piers to the seed house's back corners
  piers: [[0, 11], [12, 11], [6, 16]],
  beds: { x0: 2, x1: 10, z0: 8, z1: 14 }, // farmland round the walk (x 6) and the rill (z 11)
  rill: { z: 11, cells: [[2, 11], [3, 11], [4, 11], [8, 11], [9, 11], [10, 11]] },
  stones: [[5, 11], [7, 11]], // stepping stones either side of the composter
  composter: [6, 1, 11],
  paths: { cross: 7, back: 15, west: 1, east: 11, walk: 6 },
}

export const GLASSHOUSE = {
  walls: { x0: 2, x1: 10, z0: 16, z1: 21 }, // the seed house's width: its roof section repeated in glass
  roof: { axis: 'z', across: [1, 11], along: [15, 22], eaveY: 5, steep: false }, // eaves on every side
  posts: [16, 21], // dark oak corner posts proud of the glass (x 1 and x 11) on brick piers and a stone sill
  beds: { x0: 3, x1: 9, z0: 17, z1: 18 }, // every bed within 3 of the walk
  tank: { z: 19, cells: [[3, 19], [4, 19], [5, 19], [7, 19], [8, 19], [9, 19]] },
  // against the back wall: potting benches either side, the wing's chest by the stove (seed and mulch for the cover beds)
  benches: [[3, 20], [4, 20], [7, 20], [8, 20], [9, 20]],
  benchPots: { 3: 'potted_sweet_berry_bush', 4: 'potted_azalea_bush', 7: 'potted_fern', 9: 'potted_flowering_azalea_bush' },
  chest: [5, 1, 20],
  door: [6, 1, 16],
  stove: [6, 1, 20], // at the end of the walk, under its brick hood, at the foot of the flue
  stack: { x: 6, z: 22, top: 11 },
  pergola: { x: [5, 7], y: 3, z0: 7, z1: 15, joists: [9, 13] }, // joists x 5..7 on the beams, azalea leaves on their ends
}

export const ROOMS = [
  { name: 'Workroom', use: 'potting bench and chests (west), the breeder\'s desk (east), the walk through the middle', box: [2, 1, 3, 10, 3, 5] },
  { name: 'Loft', use: 'drying berries under the vent; up a ladder from the desk corner', box: [2, 5, 3, 10, 8, 5] },
  { name: 'Glass Wing', use: '(Level II) beds under glass either side of the walk, the dipping tank, potting benches and a chest along the back wall, the stove under its hood at the end of the walk', box: [3, 1, 17, 9, 8, 20], level: 2 },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 2], facing: 'south', out: 'north', step: [6, 0, 1] },
  backDoor: { at: [6, 1, 6], facing: 'north', out: 'south' },
  glassDoor: { at: [6, 1, 16], facing: 'south', out: 'north', level: 2 },
  ladder: { x: 7, z: 3, y0: 1, top: 4, facing: 'south' },
  windows: [
    { name: 'front west', cells: [[3, 2, 2], [3, 3, 2]], out: 'north', dress: ['sill'], pots: ['potted_red_tulip'] },
    { name: 'front east', cells: [[9, 2, 2], [9, 3, 2]], out: 'north', dress: ['sill'], pots: ['potted_blue_orchid'] },
    { name: 'west', cells: [[2, 2, 4], [2, 3, 4]], out: 'west', dress: ['shutters'] },
    { name: 'east, over the desk', cells: [[10, 2, 4], [10, 3, 4]], out: 'east', dress: ['shutters'] },
    { name: 'back west, over the bench', cells: [[4, 3, 6]], out: 'south', dress: null },
    { name: 'back east', cells: [[8, 2, 6], [8, 3, 6]], out: 'south', dress: ['shutters'] },
    { name: 'front gable (the hoist)', cells: [[6, 6, 1], [6, 7, 1]], out: 'north', dress: null },
    { name: 'back gable', cells: [[6, 6, 6], [6, 7, 6]], out: 'south', dress: null },
  ],
}

// Every piece of furniture, footprint first (kind: a furniture.py kind recorded as a spot, or a fixture the game uses as
// itself). The breeder's chests: the double chest by the potting bench, 4 across and 7 back from the composter, walked
// to through the front or back door.
export const FURNITURE = [
  // Workroom, west: her chests against the west wall, the potting bench under the back window, pots on it
  { room: 'Workroom', kind: 'fixture', part: 'doubleChest', pos: [3, 1, 4], facing: 'east', along: 'south' },
  { room: 'Workroom', kind: 'counter', part: 'slabTop', pos: [4, 1, 5], facing: 'north', wood: 'spruce' }, // a slab worktop: the room stays open
  { room: 'Workroom', kind: 'counter', part: 'slabTop', pos: [5, 1, 5], facing: 'north', wood: 'spruce' },
  { room: 'Workroom', kind: 'fixture', part: 'pot', pos: [4, 2, 5], block: 'potted_sweet_berry_bush' },
  { room: 'Workroom', kind: 'fixture', part: 'pot', pos: [5, 2, 5], block: 'potted_azalea_bush' },
  { room: 'Workroom', kind: 'lamp', part: 'hangingLamp', pos: [4, 3, 4], facing: 'north', wood: 'spruce' },
  // Workroom, east: the desk under the east window, its chair, a bookcase of breeding notes, a shelf of seed pots
  { room: 'Workroom', kind: 'desk', part: 'desk', pos: [9, 1, 4], facing: 'west', wood: 'spruce' },
  { room: 'Workroom', kind: 'chair', part: 'chair', pos: [8, 1, 4], facing: 'east', wood: 'spruce' },
  { room: 'Workroom', kind: 'fixture', part: 'bookcase', pos: [9, 1, 5], plant: 'potted_fern' },
  { room: 'Workroom', kind: 'shelf', part: 'shelf', pos: [7, 2, 5], facing: 'north', wood: 'spruce' },
  { room: 'Workroom', kind: 'fixture', part: 'pot', pos: [7, 3, 5], block: 'potted_flowering_azalea_bush' },
  { room: 'Workroom', kind: 'lamp', part: 'hangingLamp', pos: [8, 3, 4], facing: 'north', wood: 'spruce' },
  { room: 'Workroom', kind: 'lamp', part: 'hangingLamp', pos: [6, 3, 4], facing: 'north', wood: 'spruce' },
  // Loft: drying bales under the vent
  { room: 'Loft', kind: 'fixture', part: 'bale', pos: [3, 5, 5] },
  { room: 'Loft', kind: 'fixture', part: 'bale', pos: [9, 5, 5] },
  { room: 'Loft', kind: 'fixture', part: 'bale', pos: [9, 5, 4] },
]

// The walking line: the doorstep in front of the gate -> the front step -> through the workroom -> the back door ->
// down the walk to the composter (she stands at (6, 1, 10)), round the stepping stones, on down the walk; in Level II
// through the glasshouse door to the stove. Every bed is within 2 of the walk or a side path.
export const WALK = [
  [6, 0, 0], [6, 0, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [6, 1, 7], [6, 1, 8], [6, 1, 9], [6, 1, 10],
  [5, 1, 11], [6, 1, 12], [6, 1, 13], [6, 1, 14], [6, 1, 15], [6, 1, 16], [6, 1, 17], [6, 1, 18], [6, 1, 19], [6, 1, 20],
]

// Outside the rooms: flower beds either side of the porch, benches against the back wall facing the seed house.
export const OUTSIDE = {
  flowerBeds: [{ x0: 1, x1: 3, z: 0, plants: ['poppy', 'cornflower', 'allium'] }, { x0: 9, x1: 11, z: 0, plants: ['allium', 'cornflower', 'poppy'] }],
  benches: [{ x0: 3, x1: 4, z: 15 }, { x0: 8, x1: 9, z: 15 }],
}
