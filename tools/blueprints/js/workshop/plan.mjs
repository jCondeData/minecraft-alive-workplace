// The Workshop (the Create-powered Armorer's press works, ROADMAP 49 / 50.7), the plan: drawn before any wall. Story,
// palette, masses, the machine line (every Create part, its cell and state, and the water that drives it), rooms,
// openings, every furniture footprint and the walking line; recipe.mjs assembles it from the parts library (../parts)
// in passes.
//
// Three rough concepts (ROADMAP 49, REBUILD.md):
//   1. "Lean-to forge": a long, low stone forge under one 45-degree gable along x, the wheel in a ditch at its west end,
//      the press just inside the door. Massing: one box and a ditch. Roof: one gable. Plan: one room. Story: any
//      village smithy that bought a machine. Quick to read and cheap to build, but from the street it is a shed: the
//      water is a trench, the wheel sits at the ground and the press is hidden behind a wall.
//   2. "Leat arcade": an open-fronted arcade shop with a straight mill leat running across its whole front, an
//      undershot wheel turning in the leat before the arches, the press and the belt under the arcade. Massing: a long
//      hall and its arcade. Roof: a gable with a lean-to over the arches. Plan: the arcade and a back room. Story: a
//      market forge on a stream. The machine is on show, but a 15-long open leat across the way in is water the
//      villagers walk round, it floods and washes out when Create goes, and the low front has no vertical at all.
//   3. "The head-tank works": a timber-framed plate shop on a stone dado, held between two verticals. Astride its west
//      gable a slim stone water tower carries a hooped head tank; from the tank's copper spout ONE poured source
//      falls thirteen blocks in the open down the tower's front corner onto a water wheel beside the press bay, whose
//      axle runs through the wall into the gear stack beside the press, so the whole drive can be followed by eye from
//      the tank to the press, all on the street's left hand. At the
//      east gable the Armorer's blast furnace is set into the foot of a brick furnace stack that climbs the gable and
//      smokes over the ridge. On the street front a cross gable, the "press gable", rises above the eaves over a
//      glazed bay: through it the street watches the press come down on the depot. Massing: hall + press gable +
//      water tower + furnace stack (and a copper smoke louvre on the ridge), four heights (eave 5, press gable 9, ridge 11, stack 14, tower and tank 17).
//      Roof: a 45-degree slate gable along x, the press gable's ridge along z meeting it. Plan: one tall machine hall,
//      open to its tie beams: the line on the west, the furnace and the store on the east, the bench at the back.
// Strongest: 3. It is the only one where the machine makes the silhouette and the story reads from the street in
// order: water up high (the tank), falling (the open fall), turning (the wheel), driving (the belt seen through the
// press gable), pressing (the press in the window), and fire (the stack). Every vertical earns its place, the front
// has a true centre over the press, and nothing of the water crosses the way in. Built here.
//
// Coordinates: x across the front (west 0 -> east 14), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The front is the z = 0 side: the shop door faces it.
//
// The machine (Create; every kinetic part turns, nothing decorative; ids and states checked in
// libs/create-fabric-6.0.10.0+mc1.21.1.jar):
//   water   the builder pours ONE source at (1, 15, 4), last of all, in the tank's copper spout box (copper on its
//           west, north and east, the tank's staves behind it, open above). A source that can fall does not spread
//           sideways, so it only falls: down (1, 14..2, 4), in the open on its north and west sides, the tower's face
//           (1, y, 5) behind it and the shop's west wall (2, 2, 4) beside it at the wheel (Create reads a downward
//           flow only beside a solid face), into a closed sump (1, 1, 4): stone below and on all four sides (the
//           wheel's seat (1, 1, 3) on its north). Nothing water can break stands anywhere on its path (no rake, post
//           or ledge on x 1 from z 1 to z 4); with Create gone (the wheel air) the same water falls into the same
//           sump, and falling water over water never spreads.
//   wheel   create:water_wheel at (1, 2, 3), axle along x (facing east), the fall on its south side: 8 RPM, 256 stress
//           units.
//   in      create:shaft (axis x) at (2, 2, 3) through the west wall's stone dado into the gear stack: a small cogwheel
//           (axis x) at (3, 2, 3) on it.
//   down    it meshes down with a small cogwheel (axis x) at (3, 1, 3), whose axle turns the belt's end pulley.
//   belt    create:belt from (4, 1, 8) (start) to (4, 1, 3) (end), facing north, 6 long, straight, on the machine bed,
//           driven at its end; that pulley stands beside the depot, so the belt carries onto it.
//   up      (3, 2, 3) also meshes up with a small cogwheel (axis x) at (3, 3, 3), and that one with (3, 3, 2), whose
//           axle turns the press (every mesh orthogonal on one axis; no two small cogs diagonal ever mesh, so the stack
//           is one chain).
//   press   create:mechanical_press at (4, 3, 2), facing east (it turns about x); its head works in (4, 2, 2);
//           create:depot at (4, 1, 2). The Armorer stands at (5, 1, 2) (head room (5, 2, 2) kept clear) to feed it.
//   load    the press at 8 stress units per RPM at 8 RPM = 64 of the wheel's 256; shafts, cogs and the belt cost
//           nothing. Mirrored or turned the whole line turns with it; the wheel turns the other way and every part
//           still turns.
// Pinned cells kept from the brief (no test moves): blast furnace (10, 1, 6), depot (4, 1, 2), belt (4, 1, 4), press
// (4, 3, 2). New size for StarterBlueprints.WORKSHOP: new Vec3i(15, 18, 13).

export const SIZE = [15, 18, 13] // w, h, d (limit 20 x 18 x 20)

// Palette (60/30/10): the shop's timber (spruce boards between dark oak posts and plates) / grey stone and slate (the
// dado, the plinth, the tower; a deepslate tile roof) / the hot and the bright: a red-brick furnace stack and weathered
// copper (the tank's hoops and spout, the finial). Why: an armorer's shop is a wooden shed built round fire and water;
// stone where the water and the weight are (the tower, the dado), brick where the fire is (the stack), slate over it
// all so the brown walls stand out, copper for the water's own fittings.
export const PALETTE = {
  stone: [['stone_bricks', 2], ['cobblestone', 1]], // two grey stones, cobble in clumps (the critic's note: four read as patches)
  dressed: 'polished_andesite',
  dressedStairs: 'polished_andesite_stairs',
  quoin: ['stone_bricks', 'polished_andesite'],
  frame: 'dark_oak_log',
  infill: 'spruce_planks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  trim: 'dark_oak',
  brick: [['bricks', 9], ['granite', 1]],
  brickStairs: 'brick_stairs',
  brickSlab: 'brick_slab',
  crown: 'stone_bricks',
  copper: 'waxed_exposed_cut_copper',
  copperStairs: 'waxed_exposed_cut_copper_stairs',
  hoop: 'waxed_cut_copper',
  stave: 'spruce_log',
  finial: 'lightning_rod',
  floor: 'spruce_planks',
  bed: 'polished_andesite', // the machine bed under the line
  wood: 'spruce', // doors, trapdoors, fences, furniture
  glass: 'glass_pane',
  sheet: 'heavy_weighted_pressure_plate', // the pressed iron sheets, stacked on the bench
}

export const MASSES = {
  // the hall: infill walls on x 2..12, z 1..11 (stone dado y 1..2, boards y 3..4, a dark wall plate at y 5); proud
  // frame posts one block out on the ring x 1..13, z 0..12 (y 1..4, the eaves resting on them); the plinth under both
  hall: { x0: 2, x1: 12, z0: 1, z1: 11, ring: { x0: 1, x1: 13, z0: 0, z1: 12 }, dadoTop: 2, plateY: 5,
    frontPosts: [1, 7, 13], backPosts: [1, 4, 7, 10, 13], westPosts: [0, 8, 12], eastPosts: [0, 4, 8, 12],
    roof: { axis: 'x', across: [0, 12], along: [1, 13], eaveY: 5, steep: false }, // ridge y 11 along z = 6
    ties: [6, 9] }, // tie beams across the hall (along z) at y 5
  // the press gable: a cross gable over the front bay x 2..6, its eave a block over the hall's, ridge along z (y 9)
  pressGable: { across: [1, 7], along: [0, 4], eaveY: 6 },
  // the water tower: stone x 0..2, z 5..7 (astride the west gable, its middle on the ridge line) up to y 12; the head
  // tank on it y 13..15 (cap 16, finial 17)
  tower: { x0: 0, x1: 2, z0: 5, z1: 7, top: 12, tank: [13, 15], hoops: [13, 15] },
  // the forge louvre on the ridge over the hall's middle (its 3 x 3 sill on the ridge, a copper cap): the smoke vent
  louvre: { cx: 8, cz: 6, y: 11 },
  // the tower's string courses in the roof's dark tiles, at the hall's eave line, half way up and under the tank (the
  // critic's note: in the dressed stone they melted into the shaft, which read as one flat ten-high face)
  towerBands: [6, 9, 12],
  // the furnace stack: brick, 3 x 3 on x 12..14, z 5..7, from the ground to y 12, a crown and its smoke over it
  stack: { cx: 13, cz: 6, top: 12 },
}

export const MACHINE = {
  pour: [1, 15, 4], // the one source
  fall: { col: [1, 4], sumpY: 1, topY: 15 },
  sumpSides: [[0, 4]], // the rest of the sump's sides are the wheel's seat, the wall and the tower
  spoutBox: [[0, 4], [2, 4], [0, 3], [1, 3], [2, 3]],
  spoutCorbels: [[0, 4], [2, 4], [0, 3], [2, 3]],
  wheel: { at: [1, 2, 3], facing: 'east' }, seat: [1, 1, 3],
  openRake: [1, 4], // no rake over the fall on x 1 from z 1 to z 4
  shafts: [{ cells: [[2, 2, 3]], axis: 'x' }],
  cogs: [[3, 2, 3], [3, 1, 3], [3, 3, 3], [3, 3, 2]], // all small, axis x: the gear stack beside the press
  belt: { cells: [8, 7, 6, 5, 4, 3].map(z => [4, 1, z]), facing: 'north' },
  press: { at: [4, 3, 2], facing: 'east' }, // depot (4, 1, 2)
  guard: [[5, 1, 3], [5, 1, 4], [5, 1, 5], [5, 1, 6], [5, 1, 7], [5, 1, 8]], // a rail along the belt's east side
}

export const ROOMS = [
  { name: 'Machine hall', use: "the press line on the west (belt to the depot under the press), the Armorer's blast furnace in its brick breast on the east with his two store chests beside it, the anvil before it, the sheet bench and the tally desk at the back", box: [3, 1, 2, 11, 10, 10] },
]

export const OPENINGS = {
  door: { at: [10, 1, 1], facing: 'south', out: 'north', step: [10, 0, 0] },
  // windows: two high (y 3..4), every one sitting on the plinth's weathered skirt (no shutters: the proud posts stand where they
  // would hang); the press gable glazed over its bay
  windows: [
    { name: 'press bay', cells: [[3, 3, 1], [4, 3, 1], [5, 3, 1], [3, 4, 1], [4, 4, 1], [5, 4, 1]], out: 'north' },
    { name: 'front west', cells: [[8, 3, 1], [8, 4, 1]], out: 'north' },
    { name: 'front east', cells: [[12, 3, 1], [12, 4, 1]], out: 'north' },
    { name: 'west front', cells: [[2, 3, 2], [2, 4, 2]], out: 'west' },
    { name: 'west back', cells: [[2, 3, 10], [2, 4, 10]], out: 'west' },
    { name: 'east front', cells: [[12, 3, 2], [12, 4, 2]], out: 'east' },
    { name: 'east back', cells: [[12, 3, 10], [12, 4, 10]], out: 'east' },
    { name: 'back west', cells: [[5, 3, 11], [6, 3, 11], [5, 4, 11], [6, 4, 11]], out: 'south' },
    { name: 'back middle', cells: [[8, 3, 11], [9, 3, 11], [8, 4, 11], [9, 4, 11]], out: 'south' },
    { name: 'back east', cells: [[11, 3, 11], [11, 4, 11]], out: 'south' },
  ],
  gableGlass: [[3, 6, 1], [4, 6, 1], [5, 6, 1], [3, 7, 1], [4, 7, 1], [5, 7, 1], [4, 8, 1]], // the press gable's light
  westGableWindow: [[2, 6, 9], [2, 7, 9]],
}

// Every piece of furniture, footprint first (kind: a furniture.py kind, or a fixture the game uses as itself). No job
// block but the blast furnace: no crafting table, barrel, grindstone, smithing table or cauldron; the anvil and the
// chests are not job sites.
export const FURNITURE = [
  // the Armorer's corner: his blast furnace in the breast (set by the recipe's furnaceMouth), a store chest either side
  // of the breast against the east wall, the anvil before the furnace
  { room: 'Machine hall', kind: 'fixture', part: 'chest', pos: [11, 1, 4], facing: 'west', block: 'chest' },
  { room: 'Machine hall', kind: 'fixture', part: 'chest', pos: [11, 1, 8], facing: 'west', block: 'chest' },
  // the sheet bench along the back wall: three counters, iron sheets stacked on two
  { room: 'Machine hall', kind: 'counter', part: 'counter', pos: [6, 1, 10], facing: 'south', topSlab: true, wood: 'spruce' },
  { room: 'Machine hall', kind: 'counter', part: 'counter', pos: [7, 1, 10], facing: 'south', topSlab: true, wood: 'spruce' },
  { room: 'Machine hall', kind: 'counter', part: 'counter', pos: [8, 1, 10], facing: 'south', topSlab: true, wood: 'spruce' },
  // the tally desk in the back east corner and its chair
  { room: 'Machine hall', kind: 'desk', part: 'desk', pos: [11, 1, 10], facing: 'east', wood: 'spruce' },
  { room: 'Machine hall', kind: 'chair', part: 'chair', pos: [10, 1, 10], facing: 'east', wood: 'spruce' },
  // lamps hung from the tie beams (x 6 and 9, y 5)
  { room: 'Machine hall', kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 4], facing: 'north', wood: 'spruce' },
  { room: 'Machine hall', kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 8], facing: 'north', wood: 'spruce' },
  { room: 'Machine hall', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 3], facing: 'north', wood: 'spruce' },
  { room: 'Machine hall', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 9], facing: 'north', wood: 'spruce' },
]
export const FIXTURES = {
  blastFurnace: { at: [10, 1, 6], into: 'west', top: 4 },
  hearthApron: [[9, 5], [9, 6], [9, 7]], // dressed stone flags before the furnace mouth, in the board floor
  anvil: { at: [8, 1, 6], facing: 'north' },
  sheets: [[6, 3, 10], [8, 3, 10]],
  // the annealing oven by the door (a plain furnace: not a job site), facing into the room
  oven: { at: [11, 1, 2], facing: 'west' },
  // the line's feedstock heaped in the back west corner past the belt's start, outside the rail, where the Armorer
  // reaches it from the aisle (raw iron and raw copper)
  stock: [['raw_iron_block', [3, 1, 10]], ['raw_copper_block', [4, 1, 10]], ['raw_iron_block', [3, 2, 10]]],
}

// The walking line: the street -> the step (10, 0, 0) -> the door (10, 1, 1) -> the aisle; west along the front to
// the press's feeding place (5, 1, 2) beside the depot; back down the aisle to the furnace's mouth (9, 1, 6) between
// the hood and the anvil; the chests from (10, 1, 4) and (10, 1, 8); the bench from (7, 1, 9); the desk's chair.
export const WALK = [
  [10, 0, 0], [10, 1, 1], [10, 1, 2], [9, 1, 2], [7, 1, 2], [6, 1, 2], [5, 1, 2],
  [7, 1, 3], [7, 1, 5], [9, 1, 5], [9, 1, 6], [10, 1, 4], [10, 1, 8], [9, 1, 9], [7, 1, 9], [10, 1, 9], [10, 1, 10],
]

// Outside: a lamp post lighting the wheel and the fall from the street, lanterns under the door's hood, coal heaped by the stack's foot.
export const OUTSIDE = {
  wheelLamp: [0, 0, 0],
  coal: [[14, 0, 9], [14, 0, 10], [14, 1, 10]],
}
