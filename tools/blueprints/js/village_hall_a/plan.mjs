// Village Hall (Version A), the plan: drawn before any wall. Masses, rooms, every furniture footprint (kind, cells,
// facing) and the walking line come first; recipe.mjs then assembles the three levels from the parts library
// (../parts) to fit this plan. Brief: tools/blueprints/briefs/village_hall.md.
//
// Three rough concepts (massing / roof / plan / story):
//   1. "The Long Moot": one long low hall, ridge along x across the whole front, a lean-to arcade along the street with
//      the bell in it; II adds an upper council loft, III a cupola on the ridge. Story: a market-house where the
//      village meets out of the rain. Calm, but the front is one long flat wall and the cupola is a small landmark.
//   2. "The Bell Porch" (BUILT): a brick hall with its gable to the street and a lower gabled porch on stone pillars in
//      front of it, the meeting bell hanging from the porch's tie beam over the forecourt. II sets a lower council
//      wing on its east side (ridge across, an L round the forecourt's corner); III raises the porch itself into a
//      bell tower: the stone pillars carry on up as the tower's quoins, a timber belfry and a needle spire on top.
//      Story: the bell never moves. The village first hangs it under a porch; as the village grows the porch grows
//      into the tower everyone can see, and the bell still rings from the same beam at its foot.
//   3. "The Courtyard Hall": hall and wings round a small walled yard with a freestanding bell gate on the street;
//      III closes the yard. Story: a manor turned public. Too much wall for 13 x 13, and the yard eats the hall.
//   Strongest: 2. Its three levels tell one story (porch -> wing -> tower), every level has a stacked-gable front
//   that reads as civic from the street, and the tower is a real landmark grown out of what Level I already drew,
//   not bolted on: the pinned bell at (6, 4, 0) is the reason for the porch and then for the tower.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The hall block is pinned at (6, 1, 10) facing north, the front door at (6, 1, 3), the
// bell at (6, 4, 0).
//
// Masses (named parts the owner's feedback can point at):
//   Hall      the main mass: brick walls x 1..11, z 3..11 (y 1..4), a dark oak beam ring at y 5, open to a 45-degree
//             slate roof (ridge along z, y 11) with a tie beam and king post at z 7, a louvre on the ridge. Stone
//             buttresses at the middle of each long side.
//   Porch     the Bell Porch: two stone pillars (3, 0) and (9, 0), a tie beam at y 5 the bell hangs from, its own
//             lower gable (ridge y 9) to the street; benches either side of the door under it. (Level I and II.)
//   Council   Level II: the lower east wing, walls x 11..18, z 4..10, ridge along x at y 9, a hearth in its back wall
//             with the stack outside, the council table under its tie beam.
//   Tower     Level III: the porch raised into a tower x 3..9, z 0..4 (its back third rising out of the hall's roof):
//             brick shaft y 6..12 with stone quoins, a stone cornice and parapet at y 13 with lamps on the corners, a
//             timber belfry x 4..8 y 14..17 with arched openings and a great lantern, a needle spire to y 24; on
//             its street face a lancet and the village clock.

export const IDS = { 1: 'village_hall', 2: 'village_hall_2', 3: 'village_hall_3' }
export const SIZES = { 1: [13, 15, 13], 2: [20, 15, 13], 3: [20, 25, 13] } // w, h, d (I <= 13 x 16 x 13)

// Palette (60/30/10): red brick walls (60) / dark oak timber + deepslate-tile roof (30) / stone-brick dressings,
// spruce floors and joinery, red cloth, green council baize, lantern light (10). Why: brick and dressed stone are the
// materials of a public building, set apart from the village's plank houses; the slate roof is the darkest, calmest
// mass and contrasts with the warm walls; the stone pillars, quoins and lintels carry the eye up to the tower.
export const PALETTE = {
  stone: [['stone_bricks', 7], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1]], // the plinth (weathered low)
  dressed: 'stone_bricks', // pillars, quoins, lintels, cornice
  dressedStairs: 'stone_brick_stairs',
  dressedWall: 'stone_brick_wall',
  paving: [['stone_bricks', 3], ['polished_andesite', 2], ['andesite', 1]],
  wall: 'bricks',
  frame: 'dark_oak_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'spruce_planks',
  councilFloor: 'dark_oak_planks',
  wood: 'spruce', // pews, cupboards, joinery
  trim: 'dark_oak', // doors, barge boards, braces, chairs, belfry rails
  glass: 'glass_pane',
  cloth: 'red', // banners, the aisle runner
  baize: 'green', // the council table's cloth
}

export const HALL = {
  walls: { x0: 1, x1: 11, z0: 3, z1: 11 }, room: { x0: 2, x1: 10, z0: 4, z1: 10 },
  wallTop: 4, beamY: 5,
  roof: { axis: 'z', across: [0, 12], along: [2, 12], eaveY: 5, steep: false }, // ridge y 11
  tieBeamZ: 7,
  buttresses: [[0, 7, 'east'], [12, 7, 'west']], // [x, z, the wall it leans on]
  louvre: { cx: 6, cz: 7, y: 11 },
  dormers: [{ face: 1, eave: 0, z: 7, y0: 6 }, { face: 11, eave: 12, z: 7, y0: 6, levels: [1] }], // on the slopes' middle bay
}
// the hall roof's height over column x (its stairs; the ridge at x 6)
export const hallRoofY = x => 5 + Math.min(x, 12 - x)

export const PORCH = {
  pillars: [[3, 0], [9, 0]], beamY: 5, // the tie beam x 4..8 at z 0, side beams along x 3 and x 9
  roof: { axis: 'z', across: [2, 10], along: [0, 2], eaveY: 5, steep: false }, // ridge y 9
}

export const COUNCIL = {
  walls: { x0: 11, x1: 18, z0: 4, z1: 10 }, room: { x0: 12, x1: 17, z0: 5, z1: 9 },
  beamY: 5, tieBeam: { z: 7, x0: 12, x1: 17 },
  roof: { axis: 'x', across: [3, 11], along: [8, 19], eaveY: 5, steep: false }, // ridge y 9
  hearth: { x: 15, z: 10, stackZ: 11, stackTop: 10 },
}

export const TOWER = {
  shaft: { x0: 3, x1: 9, z0: 0, z1: 4, y0: 6, y1: 12 },
  corniceY: 13,
  belfry: { x0: 4, x1: 8, z0: 0, z1: 4, y0: 14, top: 17 },
  spire: { cx: 6, cz: 2, heights: [23, 20, 18] }, // ring 0 (the needle's top) .. ring 2 (the eave); a finial at y 24
  clock: { ring: 'polished_diorite', hub: 'polished_deepslate' }, // the dial, x 5..7 y 10..12: a pale ring round a dark hub
  towerBell: [6, 16, 2], // hangs from the belfry's ceiling, 17 blocks from the hall block (the porch bell: 10.4)
}

export const ROOMS = [
  { name: 'Hall', use: 'the meeting room: pews facing the Village Hall block, the Steward\'s place at the back', box: [2, 1, 4, 10, 4, 10], levels: [1, 2, 3] },
  { name: 'Porch', use: 'the bell, the forecourt\'s shelter, benches by the door', box: [4, 1, 0, 8, 4, 2], levels: [1, 2, 3] },
  { name: 'Council', use: 'the council table and its chairs by the hearth', box: [12, 1, 5, 17, 4, 9], levels: [2, 3] },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 3], facing: 'south', lintel: [6, 3, 3] }, // a 2-high dark oak door under a stone lintel
  bell: { at: [6, 4, 0], hangsFrom: [6, 5, 0] },
  councilDoorway: { at: [11, 1, 5], height: 2, levels: [2, 3] }, // the hall's east window at z 5 becomes the doorway
  // windows: glass cells, the side they look out of, and the level that draws them
  windows: [
    { name: 'hall west front', cells: [[1, 2, 5], [1, 3, 5]], out: 'west', sill: true },
    { name: 'hall west back', cells: [[1, 2, 9], [1, 3, 9]], out: 'west', sill: true },
    { name: 'hall east front', cells: [[11, 2, 5], [11, 3, 5]], out: 'east', sill: true, levels: [1] },
    { name: 'hall east back', cells: [[11, 2, 9], [11, 3, 9]], out: 'east', sill: true },
    { name: 'hall back west', cells: [[4, 2, 11], [4, 3, 11]], out: 'south', sill: true },
    { name: 'hall back middle', cells: [[6, 2, 11], [6, 3, 11]], out: 'south', sill: true },
    { name: 'hall back east', cells: [[8, 2, 11], [8, 3, 11]], out: 'south', sill: true },
    { name: 'porch west', cells: [[4, 2, 3], [4, 3, 3]], out: 'north', sill: false }, // over the porch benches
    { name: 'porch east', cells: [[8, 2, 3], [8, 3, 3]], out: 'north', sill: false },
    { name: 'hall back gable', cells: [[6, 8, 11]], out: 'south', sill: false },
    { name: 'council front 1', cells: [[13, 2, 4], [13, 3, 4]], out: 'north', sill: true, levels: [2, 3] },
    { name: 'council front 2', cells: [[15, 2, 4], [15, 3, 4]], out: 'north', sill: true, levels: [2, 3] },
    { name: 'council front 3', cells: [[17, 2, 4], [17, 3, 4]], out: 'north', sill: true, levels: [2, 3] },
    { name: 'council back west', cells: [[13, 2, 10], [13, 3, 10]], out: 'south', sill: true, levels: [2, 3] },
    { name: 'council back east', cells: [[17, 2, 10], [17, 3, 10]], out: 'south', sill: true, levels: [2, 3] },
    { name: 'council east', cells: [[18, 2, 7], [18, 3, 7]], out: 'east', sill: true, levels: [2, 3] },
    { name: 'council gable', cells: [[18, 7, 7]], out: 'east', sill: false, levels: [2, 3] },
    { name: 'tower front', cells: [[6, 7, 0], [6, 8, 0]], out: 'north', sill: false, levels: [3] },
    { name: 'tower west', cells: [[3, 8, 2], [3, 9, 2]], out: 'west', sill: false, levels: [3] },
    { name: 'tower east', cells: [[9, 8, 2], [9, 9, 2]], out: 'east', sill: false, levels: [3] },
  ],
}

// Every piece of furniture, footprint first (kind: a furniture.py kind; part: the part that draws it; facing: the way
// a person using it faces). `levels` defaults to all three.
export const FURNITURE = [
  // Porch: a bench either side of the door, backs to the hall's front wall, looking out at the bell and the street
  { room: 'Porch', kind: 'bench', part: 'bench', at: { x0: 4, x1: 5, y: 1, z: 2 }, facing: 'north', wood: 'dark_oak' },
  { room: 'Porch', kind: 'bench', part: 'bench', at: { x0: 7, x1: 8, y: 1, z: 2 }, facing: 'north', wood: 'dark_oak' },
  // Hall: two rows of pews either side of the aisle, everyone facing the Steward's place (the hall block)
  { room: 'Hall', kind: 'bench', part: 'bench', at: { x0: 3, x1: 5, y: 1, z: 5 }, facing: 'south', wood: 'spruce' },
  { room: 'Hall', kind: 'bench', part: 'bench', at: { x0: 7, x1: 9, y: 1, z: 5 }, facing: 'south', wood: 'spruce' },
  { room: 'Hall', kind: 'bench', part: 'bench', at: { x0: 3, x1: 5, y: 1, z: 7 }, facing: 'south', wood: 'spruce' },
  { room: 'Hall', kind: 'bench', part: 'bench', at: { x0: 7, x1: 9, y: 1, z: 7 }, facing: 'south', wood: 'spruce' },
  { room: 'Hall', kind: 'rug', part: 'rug', cellsAbs: [[6, 1, 5], [6, 1, 6], [6, 1, 7], [6, 1, 8]], colour: 'red' }, // never at (6, 1, 4) or (6, 1, 9)
  { room: 'Hall', kind: 'cupboard', part: 'cupboard', pos: [2, 1, 10], facing: 'east', wood: 'spruce' }, // the records
  { room: 'Hall', kind: 'cupboard', part: 'cupboard', pos: [10, 1, 10], facing: 'west', wood: 'spruce' },
  { room: 'Hall', kind: 'lamp', part: 'tableLamp', pos: [5, 1, 10], facing: 'north', wood: 'spruce' }, // lamps either side of the hall block
  { room: 'Hall', kind: 'lamp', part: 'tableLamp', pos: [7, 1, 10], facing: 'north', wood: 'spruce' },
  { room: 'Hall', kind: 'picture', part: 'picture', pos: [4, 7, 10], facing: 'north', colour: 'red', wood: 'spruce' }, // banners on the back gable
  { room: 'Hall', kind: 'picture', part: 'picture', pos: [8, 7, 10], facing: 'north', colour: 'red', wood: 'spruce' },
  { room: 'Hall', kind: 'clock', part: 'clock', pos: [2, 1, 4], facing: 'east', wood: 'spruce' },
  { room: 'Hall', kind: 'lamp', part: 'tableLamp', pos: [10, 1, 4], facing: 'west', wood: 'spruce' },
  { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [4, 3, 7], chain: 1, facing: 'north', wood: 'spruce' }, // from the tie beam
  { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [8, 3, 7], chain: 1, facing: 'north', wood: 'spruce' },
  { room: 'Hall', kind: 'hanging_pot', part: 'hangingPot', pos: [2, 4, 7], plant: 'potted_fern', wood: 'spruce' },
  { room: 'Hall', kind: 'hanging_pot', part: 'hangingPot', pos: [10, 4, 7], plant: 'potted_fern', wood: 'spruce' },
  // Council (II, III): a long table under green baize, four chairs a side and the head's chair by the east window
  ...[13, 14, 15, 16].map(x => ({ room: 'Council', kind: 'table', part: 'table', pos: [x, 1, 7], facing: 'north', wood: 'dark_oak', colour: 'green', levels: [2, 3] })),
  ...[13, 14, 15, 16].map(x => ({ room: 'Council', kind: 'chair', part: 'chair', pos: [x, 1, 6], facing: 'south', wood: 'dark_oak', levels: [2, 3] })),
  ...[13, 14, 15, 16].map(x => ({ room: 'Council', kind: 'chair', part: 'chair', pos: [x, 1, 8], facing: 'north', wood: 'dark_oak', levels: [2, 3] })),
  { room: 'Council', kind: 'chair', part: 'armchair', pos: [17, 1, 7], facing: 'west', wood: 'dark_oak', colour: 'red', levels: [2, 3] },
  { room: 'Council', kind: 'cupboard', part: 'cupboard', pos: [17, 1, 5], facing: 'west', wood: 'spruce', levels: [2, 3] },
  { room: 'Council', kind: 'cupboard', part: 'cupboard', pos: [17, 1, 9], facing: 'west', wood: 'spruce', levels: [2, 3] },
  { room: 'Council', kind: 'lamp', part: 'hangingLamp', pos: [13, 4, 7], facing: 'north', wood: 'dark_oak', levels: [2, 3] },
  { room: 'Council', kind: 'lamp', part: 'hangingLamp', pos: [16, 4, 7], facing: 'north', wood: 'dark_oak', levels: [2, 3] },
  { room: 'Council', kind: 'hanging_pot', part: 'hangingPot', pos: [12, 4, 7], plant: 'potted_azalea_bush', wood: 'dark_oak', levels: [2, 3] },
]

// The walking line: the street -> the forecourt under the bell -> the door -> up the aisle to the Steward's place at
// (6, 1, 9); and, from Level II, along the east aisle to the council doorway and round the table.
export const WALK = [
  [6, 0, -1], [6, 1, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [6, 1, 7], [6, 1, 8], [6, 1, 9],
  [7, 1, 4], [8, 1, 4], [9, 1, 4], [10, 1, 4], [10, 1, 5], [11, 1, 5], [12, 1, 5], [12, 1, 6], [12, 1, 7], [12, 1, 8], [12, 1, 9], [16, 1, 9],
]

// Outside (not furniture spots): a paved forecourt with a step along the street, lamp piers at its corners, flower
// beds in their own soil against the front wall either side of the porch.
export const OUTSIDE = {
  lampPiers: [[0, 0, 0], [12, 0, 0]],
  flowerBeds: [{ x0: 1, x1: 2, z: 2, plants: ['poppy', 'azure_bluet'] }, { x0: 10, x1: 11, z: 2, plants: ['azure_bluet', 'poppy'] }],
}
