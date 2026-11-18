// The Clock Tower, the plan: drawn before any wall (ROADMAP 51.6, brief tools/blueprints/briefs/clock_tower.md). Story,
// palette, masses, the machine cell by cell (and the water that drives it), rooms, openings, every furniture footprint
// and the walking line; recipe.mjs then assembles it from the parts library (../parts) in passes.
//
// Three rough concepts:
//   1. "Gatehouse clock": a squat tower straddling a road on an arch, the wheel in a millrace under the arch, a
//      pyramid roof. Story: the town gate that rings the hours. Strong street presence, but the arch eats the plot
//      and the job block, and the wheel hides in shadow under the vault: nobody sees what turns the clock.
//   2. "Cistern tower": a tall plain shaft carrying a copper tank on top; the water falls inside to a wheel high under
//      the tank, a short drive to one face. Story: the water tower that keeps the time. Honest, but the machine is all
//      hidden inside, and a tank on top reads as a water tower, not a clock.
//   3. "The Spout Clock" (BUILT): a slender stone shaft, a jettied timber clock chamber with a face on every side, a
//      bell-cast copper spire. At the front corner a stone spout pier pours a ribbon of water down the tower's quoin
//      past a water wheel set beside the door; through a slit in the front wall you watch the drive climb from the
//      wheel to the chamber, where a gearbox sends it out to the four faces. Story: the cartographers who survey
//      the valley keep the town's time, and they show you how: the stream turns the wheel, the wheel turns the clock.
//   The third is strongest: the whole machine reads from the street (water, wheel, rising shaft, four turning
//   faces), the silhouette is a real clock tower (shaft, overhanging chamber, flared needle) and not a box, and the
//   map room at its foot gives the Cartographer a room worth walking into. It is also the least safe: water on the
//   front, a jetty, four driven faces and a steep spire, each drawn to the rules below.
//
// Coordinates: x across the front (west 0 -> east 14), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The front is the z = 0 side: the door, the wheel and the north clock face it.
//
// Masses (named parts the owner's feedback can point at):
//   Shaft     the stone tower, walls x 4..10, z 4..10, y 1..12 on a deepslate plinth (y 0) with a battered foot,
//             dressed quoins, a string course at the map room's ceiling (y 5).
//   Spout     the race at the front-west corner: a deepslate pier (x 3, z 2..3, y 0..7) with a cup on top; the
//             water falls down (4, 6..2, 3) against the shaft's corner into a closed sump at (4, 1, 3).
//   Wheel     create:water_wheel at (5, 2, 3) beside the fall, its axle into the front wall (axis z).
//   Chamber   the jettied clock chamber x 3..11, z 3..11, y 13..17 (sill y 13, walls y 14..17): dark oak frame,
//             calcite infill, a cuckoo clock in a dark dial surround centred on every face at y 15, corbels under it
//             (y 12).
//   Spire     a bell-cast copper spire: three 45-degree courses from the eave (y 17, a block proud of the chamber),
//             then a steep needle, a lightning rod on the tip (y 25); a king post on two crossed tie beams carries the tip.
//
// The machine (Create; every kinetic part turns, nothing decorative; ids and states checked in
// libs/create-fabric-6.0.10.0+mc1.21.1.jar via ../parts/foundry.mjs):
//   water   the builder pours ONE source at (4, 7, 3), last of all, in the pier's cup: open above, closed on all four
//           sides (pier west, cup north and east, the shaft's corner south), because a source whose fall is already
//           full spreads sideways. It falls (4, 6..2, 3) against the shaft's corner (a solid face, so Create reads a
//           downward flow) past the wheel's west side into the sump (4, 1, 3): a stone floor and four stone sides.
//           Falling water never spreads sideways, so with Create gone (the wheel air) the same water falls into the
//           same sump; nothing it can break stands beside the fall.
//   wheel   create:water_wheel at (5, 2, 3), axle along z (facing south): 8 RPM, 256 stress units.
//   in      a shaft (axis z) at (5, 2, 4) through the front wall; a gearbox (axis x) at (5, 2, 5) in the map room's
//           corner, on a polished andesite bearing block, turns the drive up.
//   up      shafts (axis y) from (5, 3, 5) to (5, 14, 5) up the corner, through the floors at y 5 and y 13, seen from
//           the street through the slit (5, 8..11, 4).
//   head    a gearbox (axis x) at (5, 15, 5) turns it south: a shaft (axis z) at (5, 15, 6) to a gearbox (axis y) at
//           (5, 15, 7): west a shaft (4, 15, 7) to the WEST clock (3, 15, 7); east a shaft (6, 15, 7) to the centre
//           gearbox (axis y) at (7, 15, 7), which sends three shafts out: north (7, 15, 6..4) to the NORTH clock
//           (7, 15, 3), south (7, 15, 8..10) to the SOUTH clock (7, 15, 11), east (8..10, 15, 7) to the EAST clock
//           (11, 15, 7). Each clock takes its drive through its back (the cell opposite its facing). A tree with no
//           loop, so no two paths can fight; gearboxes keep the speed, so every face turns at the wheel's 8 RPM.
//   load    four cuckoo clocks at 1 stress unit per RPM at 8 RPM = 32 of the wheel's 256; shafts and gearboxes cost
//           nothing.
//   turned  turned or mirrored at the Table the whole drawing turns with it: the water stays beside the wheel and every
//           gearbox still meets its shafts (no cogwheel mesh to come apart).

export const SIZE = [15, 26, 15] // w, h, d (limit 20 x 27 x 24; taller than 18: the ship step moves the build test to TALL_AREA)

// Palette (60/30/10): grey stone-brick shaft / the head: dark oak frame, calcite infill and a verdigris copper spire /
// deepslate dressings (plinth, quoins, string course, the spout pier) with spruce joinery. Why: a quiet grey shaft
// lets the head carry the colour, so the eye climbs to the clocks: white plaster makes the dark dials and the cuckoo
// clocks' faces read from across the village, green copper is what a town clock's spire weathers to, and the near-black
// deepslate frames the water at the foot.
export const PALETTE = {
  shaft: [['stone_bricks', 8], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 2]],
  dressing: 'deepslate_bricks',
  dressingAlt: 'polished_deepslate',
  dressingStairs: 'deepslate_brick_stairs',
  bearing: 'polished_andesite',
  frame: 'dark_oak_log',
  frameStairs: 'dark_oak_stairs',
  planks: 'dark_oak_planks',
  infill: 'calcite',
  dial: 'stripped_dark_oak_wood',
  hood: 'dark_oak_slab',
  roof: { full: 'waxed_oxidized_cut_copper', stairs: 'waxed_oxidized_cut_copper_stairs', slab: 'waxed_oxidized_cut_copper_slab' },
  floor: 'spruce_planks',
  rose: { heart: 'chiseled_stone_bricks', arm: 'stripped_spruce_wood', diag: 'polished_andesite' },
  wood: 'spruce',
  glass: 'glass_pane',
  apex: 'lightning_rod',
}

export const MASSES = {
  shaft: { x0: 4, x1: 10, z0: 4, z1: 10, y0: 1, y1: 12, room: { x0: 5, x1: 9, z0: 5, z1: 9 }, floors: [5], course: 5 },
  spout: {
    pier: { x: 3, z0: 2, z1: 3, y0: 0, y1: 7 },
    race: { col: [4, 3], sumpY: 1, topY: 7, cup: [[3, 3], [4, 2], [5, 3]], sumpSides: [[3, 3], [4, 2], [5, 3]],
      corbels: [{ at: [4, 2], facing: 'west' }, { at: [5, 3], facing: 'south' }] },
    footing: [[4, 0, 2], [5, 0, 2], [5, 0, 3], [5, 1, 2]], // the race's stone floor round the sump, the wheel's lip
  },
  chamber: { x0: 3, x1: 11, z0: 3, z1: 11, sill: 13, y0: 14, y1: 17, corbelY: 12, dialY: 15 },
  spire: { cx: 7, cz: 7, half: 5, y0: 17, flare: 3, kingPost: [18, 23] },
}

export const MACHINE = {
  wheel: { at: [5, 2, 3], facing: 'south' },
  bearing: [5, 1, 5],
  shafts: [
    { cells: [[5, 2, 4]], axis: 'z' },
    { cells: Array.from({ length: 12 }, (_, i) => [5, 3 + i, 5]), axis: 'y' },
    { cells: [[5, 15, 6]], axis: 'z' },
    { cells: [[4, 15, 7], [6, 15, 7]], axis: 'x' },
    { cells: [[7, 15, 6], [7, 15, 5], [7, 15, 4]], axis: 'z' },
    { cells: [[7, 15, 8], [7, 15, 9], [7, 15, 10]], axis: 'z' },
    { cells: [[8, 15, 7], [9, 15, 7], [10, 15, 7]], axis: 'x' },
  ],
  gearboxes: [
    { at: [5, 2, 5], axis: 'x' }, { at: [5, 15, 5], axis: 'x' }, { at: [5, 15, 7], axis: 'y' }, { at: [7, 15, 7], axis: 'y' },
  ],
  clocks: [
    { at: [7, 15, 3], facing: 'north', along: 'x' }, { at: [7, 15, 11], facing: 'south', along: 'x' },
    { at: [3, 15, 7], facing: 'west', along: 'z' }, { at: [11, 15, 7], facing: 'east', along: 'z' },
  ],
}

export const ROOMS = [
  { name: 'Map Room', use: "the Cartographer's room: the table, a compass rose in the floor, a cupboard of charts", box: [5, 1, 5, 9, 4, 9] },
  { name: 'Chart Loft', use: 'the shaft hall: shelves of charts, a desk under the slit, the ladder up', box: [5, 6, 5, 9, 12, 9] },
  { name: 'Clock Chamber', use: 'the works: the drive splits to the four faces', box: [4, 14, 4, 10, 16, 10] },
]

export const OPENINGS = {
  frontDoor: { at: [7, 1, 4], facing: 'south', step: [7, 0, 3] },
  ladder: { x: 9, z: 9, y0: 1, y1: 13, facing: 'west' },
  windows: [
    { name: 'map room west', cells: [[4, 2, 7], [4, 3, 7]], out: 'west', dress: 'sill' },
    { name: 'map room east', cells: [[10, 2, 7], [10, 3, 7]], out: 'east', dress: 'sill' },
    { name: 'map room back', cells: [[7, 2, 10], [7, 3, 10]], out: 'south', dress: 'sill' },
    { name: 'map room front', cells: [[9, 2, 4], [9, 3, 4]], out: 'north', dress: 'sill' },
    { name: 'drive slit', cells: [[5, 8, 4], [5, 9, 4], [5, 10, 4], [5, 11, 4]], out: 'north', dress: null },
    { name: 'loft front', cells: [[8, 7, 4], [8, 8, 4], [8, 9, 4]], out: 'north', dress: null },
    { name: 'loft west', cells: [[4, 8, 7], [4, 9, 7], [4, 10, 7]], out: 'west', dress: null },
    { name: 'loft east', cells: [[10, 8, 7], [10, 9, 7], [10, 10, 7]], out: 'east', dress: null },
    { name: 'loft back', cells: [[7, 8, 10], [7, 9, 10], [7, 10, 10]], out: 'south', dress: null },
    // the chamber's lights either side of each dial
    ...[[5, 3], [9, 3], [5, 11], [9, 11]].map(([x, z]) => ({ name: 'chamber', cells: [[x, 15, z]], out: z === 3 ? 'north' : 'south', dress: null })),
    ...[[3, 5], [3, 9], [11, 5], [11, 9]].map(([x, z]) => ({ name: 'chamber', cells: [[x, 15, z]], out: x === 3 ? 'west' : 'east', dress: null })),
  ],
}

// Every piece of furniture, footprint first (as house_t1_b/plan.mjs). The job block (a cartography table) is a
// fixture the recipe draws at JOB.
export const JOB = [7, 1, 9]
export const FURNITURE = [
  // Map Room: the cartography table on the back wall, seen through the door, its stool; a cupboard of charts, a tall
  // case clock, a hung map; a lamp hung from the loft floor and one on a side table
  { room: 'Map Room', kind: 'chair', part: 'chair', pos: [7, 1, 8], facing: 'south', wood: 'spruce' },
  { room: 'Map Room', kind: 'cupboard', part: 'cupboard', pos: [5, 1, 9], facing: 'east', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Map Room', kind: 'clock', part: 'clock', pos: [5, 1, 6], facing: 'east', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Map Room', kind: 'picture', part: 'picture', pos: [8, 3, 9], facing: 'north', wood: 'spruce', colour: 'light_blue' },
  { room: 'Map Room', kind: 'lamp', part: 'hangingLamp', pos: [7, 4, 7], facing: 'north', wood: 'spruce' },
  { room: 'Map Room', kind: 'lamp', part: 'tableLamp', pos: [9, 1, 6], facing: 'west', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  // Chart Loft: shelves along the back wall, a desk and chair on the east wall, a lamp hung from the chamber floor
  { room: 'Chart Loft', kind: 'fixture', part: 'bookcase', pos: [6, 6, 9], facing: 'north' },
  { room: 'Chart Loft', kind: 'fixture', part: 'bookcase', pos: [7, 6, 9], facing: 'north', plant: 'potted_fern' },
  { room: 'Chart Loft', kind: 'fixture', part: 'bookcase', pos: [8, 6, 9], facing: 'north' },
  { room: 'Chart Loft', kind: 'desk', part: 'desk', pos: [9, 6, 6], facing: 'east', wood: 'spruce' },
  { room: 'Chart Loft', kind: 'chair', part: 'chair', pos: [8, 6, 6], facing: 'east', wood: 'spruce' },
  { room: 'Chart Loft', kind: 'lamp', part: 'hangingLamp', pos: [7, 11, 7], facing: 'north', chain: 1, wood: 'spruce' },
  // Clock Chamber: two lamps hung from the tie beams, over the shafts to the east and south faces
  { room: 'Clock Chamber', kind: 'lamp', part: 'hangingLamp', pos: [9, 16, 7], facing: 'north', wood: 'spruce' },
  { room: 'Clock Chamber', kind: 'lamp', part: 'hangingLamp', pos: [7, 16, 9], facing: 'north', wood: 'spruce' },
]

// The walking line: front path -> step -> door -> the map room's middle -> the cartography table; the ladder up the
// back-east corner through the loft to the chamber.
export const WALK = [
  [7, 0, 0], [7, 0, 1], [7, 0, 2], [7, 0, 3], [7, 1, 4], [7, 1, 5], [7, 1, 6], [7, 1, 7], [8, 1, 8], [9, 1, 8],
  [9, 1, 9], [9, 6, 9], [9, 13, 9], [8, 14, 9],
]
