// Nether Gate (nether_gate, nether_gate_2), the plan: drawn before any wall. Brief: tools/blueprints/briefs/nether_gate.md.
// Three rough concepts and why B was built: DESIGN.md beside this file (A the Blackstone Gatehouse, B the Ash Gate,
// C the Ruined Portal Shrine).
//
// Story: the "Ash Gate". The village honours its one way to the Nether as a threshold: a roofed blackstone gate on a
// dais you climb two steps to, soul fire either side, the obsidian frame set a block back between two piers under a
// steep crimson roof with flared eaves. Beside it the netherworker's Provisioning Lodge, a small timbered room under
// the gate's eave, where the brazier burns and the rations are packed. Level II grows the Haul House behind the lodge
// for what comes back, and a walled terrace of nether wart behind the gate.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front (z = 0,
// north) to the back. The portal is walked into from the front: steps at z 0-1, floor at y 1 before it (z 2).
//
// Masses (named parts, see DESIGN.md): Dais, Piers, Frame, Head, Eave beams, Lower Eave, Frieze, Gate Roof, Lodge, Haul House (II),
// Wart Terrace (II).

export const LEVELS = {
  1: { size: [13, 16, 9] }, // limit 13 x 16 x 13 (buildsNetherGate at (9, 2, 9) in big_area)
  2: { size: [15, 16, 14] }, // limit 15 x 16 x 14 (buildsNetherGateII at (7, 2, 8))
}

// Palette (60/30/10): blackstone (gate, plinth) / crimson (roof, frame) with quartz infill (lodge) / warped trim and
// gilded blackstone. Why: everything a netherworker brings home, the black gate against a warm red roof, the pale
// lodge reading apart from the gate, a cold teal edge on the roof and soul fire's blue at the threshold.
export const PALETTE = {
  plinth: [['blackstone', 5], ['basalt', 2], ['polished_blackstone', 1]],
  pier: [['polished_blackstone_bricks', 7], ['cracked_polished_blackstone_bricks', 2]],
  pierCorner: 'polished_basalt',
  band: 'gilded_blackstone',
  cap: 'chiseled_polished_blackstone',
  dressed: 'polished_blackstone',
  step: 'polished_blackstone_stairs',
  corbel: 'polished_blackstone_brick_stairs', // the shouldered heads beside the frame's top
  floor: 'polished_basalt',
  court: [['polished_blackstone', 3], ['polished_basalt', 1]],
  frameCorner: 'gilded_blackstone',
  roof: { full: 'crimson_planks', stairs: 'crimson_stairs', slab: 'crimson_slab' },
  kick: 'nether_brick_stairs',
  lodgeRoof: { full: 'nether_bricks', stairs: 'nether_brick_stairs', slab: 'nether_brick_slab' }, // darker than the hero roof
  door: 'crimson',
  stem: 'crimson_stem',
  stripped: 'stripped_crimson_stem',
  infill: 'quartz_bricks',
  gable: 'crimson_planks',
  trim: 'warped',
  glass: 'glass_pane',
  wall: 'polished_blackstone_brick_wall',
  bed: 'nether_bricks',
  soil: 'soul_sand',
  path: 'polished_basalt',
}

export const MASSES = {
  dais: { x0: 0, x1: 12, z0: 1, z1: 7, floorY: 1, frontSteps: [1, 11], backSteps: [1, 8] },
  piers: [{ x0: 1, x1: 2 }, { x0: 7, x1: 8 }], // z 2..4, y 1..8
  pierZ: [2, 4], pierTop: 7,
  frame: { x0: 3, x1: 6, y0: 1, y1: 5, z: 3 }, // inside x 4-5, y 2-4: PortalShape's bottom-left at (4, 2, 3), axis X
  beams: { y: 7, z: [1, 5], x: [0, 9] },
  lowerEave: { y: 7, x: [0, 9] }, // kick at z 0 and 6, a crimson course at z 1 and 5 (y 8)
  frieze: { x0: 1, x1: 8, y0: 8, y1: 10 }, // z 2..4, over both piers and the head
  gateRoof: { axis: 'x', across: [1, 5], along: [0, 9], eaveY: 11, steep: true }, // kick y 11, ridge y 13
  lodge: { x0: 9, x1: 12, z0: 2, z1: 6, wallTop: 4, beamY: 5 },
  lodgeRoof: { axis: 'x', across: [1, 7], along: [9, 12], eaveY: 5, steep: false },
  haul: { x0: 9, x1: 13, z0: 6, z1: 12, wallTop: 7, beamY: 8 }, // Level II
  haulRoof: { axis: 'z', across: [8, 14], along: [6, 13], eaveY: 8, steep: false },
  terrace: { x0: 0, x1: 8, z0: 7, z1: 13 }, // Level II, floor y 0
}

export const ROOMS = [
  { name: 'Lodge', use: 'the netherworker packs rations at the brazier and unpacks the haul', box: [9, 2, 3, 11, 4, 5] },
  { name: 'Haul House', use: 'the netherworker\'s store for the Nether\'s goods (Level II)', box: [10, 2, 7, 12, 6, 11], level: 2 },
]

export const OPENINGS = {
  // the lodge's door faces the street from the top of the steps, between two windows; at II a doorway into the Haul House
  lodgeDoor: { at: [10, 2, 2], facing: 'south', out: 'north' },
  haulDoorway: { at: [11, 2, 6], level: 2 },
  terraceDoor: { at: [9, 2, 9], facing: 'east', out: 'west', step: [8, 1, 9], level: 2 }, // the west wall's middle
  windows: [
    { name: 'lodge front west', cells: [[9, 3, 2]], out: 'north', dress: null },
    { name: 'lodge front east', cells: [[11, 3, 2]], out: 'north', dress: null },
    { name: 'lodge east', cells: [[12, 3, 4]], out: 'east', dress: null }, // the plot's edge: no room for shutters
    { name: 'lodge gable', cells: [[12, 6, 4]], out: 'east', dress: null },
    { name: 'haul east', cells: [[13, 3, 9], [13, 4, 9]], out: 'east', dress: 'shutters', level: 2 },
    { name: 'haul back', cells: [[11, 3, 12], [11, 4, 12]], out: 'south', dress: 'shutters', level: 2 },
    { name: 'haul gable', cells: [[11, 9, 6]], out: 'north', dress: null, level: 2 },
  ],
}

// Furniture and fixtures, footprint first. The Nether Brazier is the netherworker's job site (its own POI); the
// chest and barrel are his supply (SupplyContainers: within 8 across and 4 up of it).
export const FURNITURE = [
  { room: 'Lodge', kind: 'fixture', part: 'brazier', pos: [10, 2, 5], facing: 'north' },
  { room: 'Lodge', kind: 'fixture', part: 'pantry', pos: [11, 2, 3], facing: 'west', block: 'chest' },
  { room: 'Lodge', kind: 'fixture', part: 'pantry', pos: [11, 2, 4], facing: 'west', block: 'barrel' },
  { room: 'Lodge', kind: 'lamp', part: 'hangingLamp', pos: [10, 6, 4], facing: 'north', chain: 1, wood: 'crimson' },
  // Level II: the Haul House. A double chest on the east wall, barrels on their sides in a rack on the back wall,
  // one more barrel standing by the door, a lamp from the ridge.
  { room: 'Haul House', kind: 'fixture', part: 'doubleChest', pos: [12, 2, 8], facing: 'west', along: 'south', level: 2 },
  { room: 'Haul House', kind: 'fixture', part: 'caskRack', pos: [10, 2, 11], level: 2 },
  { room: 'Haul House', kind: 'fixture', part: 'pantry', pos: [10, 2, 7], facing: 'up', block: 'barrel', level: 2 },
  { room: 'Haul House', kind: 'lamp', part: 'hangingLamp', pos: [11, 7, 9], facing: 'north', chain: 0, wood: 'crimson', level: 2 },
]

// The walking line: street -> steps -> the portal's front (4..5, 2, 2); across the dais to the lodge, the brazier's
// side (10, 2, 4); at II through the doorway into the Haul House, and from the back of the portal down to the terrace.
export const WALK = [
  [4, 0, 0], [4, 1, 1], [4, 2, 2], [5, 2, 2], [9, 2, 1], [9, 2, 2], [10, 2, 3], [10, 2, 4],
  [11, 2, 5], [11, 2, 6], [11, 2, 7], [4, 2, 5], [4, 2, 6], [4, 1, 7], [4, 1, 8], [4, 1, 10],
]

// Level II outside: the Wart Terrace's beds (soul sand at y 1 on a nether-brick base, wart at y 2 at its last age),
// corner posts, the path's shroomlights, the lamp pillar at the path's end.
export const TERRACE = {
  beds: [{ x0: 1, x1: 3, z0: 9, z1: 12 }, { x0: 5, x1: 7, z0: 9, z1: 12 }],
  path: { x: 4, z0: 8, z1: 12 },
  lights: [[4, 0, 10], [2, 0, 8], [6, 0, 8]],
  pillar: [4, 13],
}
