// The Brass Foundry, the plan: drawn before any wall. Story, palette, masses, the machine line (every Create part, its
// cell and state, and the water that drives it), rooms, openings, every furniture footprint and the walking line;
// recipe.mjs assembles it from the parts library (../parts) in passes.
//
// Three rough concepts (ROADMAP 49, REBUILD.md):
//   1. "Bakehouse with a mill": a long low brick bakehouse, a water wheel in a pit on its side, the mixer at the end of
//      the room under a plain 45-degree gable. Massing: one long box and a pit. Roof: one gable along z. Plan: one
//      room. Story: a bakery that bought a machine. Safe and readable, but the drive hides in the walls, nothing says
//      "brass", and from the street it is any village bakery.
//   2. "Furnace tower": a square brick tower like a smelting stack, the mixer on its ground floor, the wheel high on its
//      face, the Chef's kitchen in a lean-to at its foot. Massing: one tall tower and a shed. Roof: a pyramid cap.
//      Plan: a tower room and a kitchen. Story: an old copper furnace turned to dough. A strong vertical, but a tower
//      hides its gearing behind brick, the machine room is cramped (3 x 3 round the mixer), and the belt has nowhere
//      to run out.
//   3. "The gear lantern": a tall red-brick foundry hall under a weathered-copper gable, and riding its ridge a glazed
//      lantern (a roof monitor) in which the whole gear train turns flat, in daylight, where the street can see it:
//      a water wheel on the back, fed from a copper tank on a brick tank tower, drives two up-gearings in the lantern,
//      and one long shaft drops from the lantern down the middle of the hall to a cogwheel on the mixer's own cog,
//      the mixer over its basin and blaze burner. A second shaft drops behind it to the floor and runs the belt down the west wall to a depot
//      at the front window. The Chef cooks in a low cookhouse under a catslide that sweeps the hall's east slope down
//      over it. Massing: hall + lantern + tank tower + cookhouse, four heights. Roof: a 45-degree gable along z, a
//      second gable on the lantern, a catslide. Plan: the machine hall and the cookhouse, joined by an inner door.
//      Story: the village chef could not knead for everyone, so the smiths built him a brass heart; the lantern on
//      the roof is where they show it off.
// Strongest: 3. It is the only one where the machine makes the silhouette: the glazed lantern (the hero, the highest
// roof), the tank tower and its wheel, and the shaft you can follow from the roof to the basin all say "brass works"
// from the street, while the hall stays big enough to walk round the mixer and the cookhouse gives the Chef his own
// room. Built here.
//
// Coordinates: x across the front (west 0 -> east 18), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The front is the z = 0 side: the hall's door and the cookhouse door face it.
//
// The machine (Create; every kinetic part turns, nothing decorative; ids and states checked in
// libs/create-fabric-6.0.10.0+mc1.21.1.jar):
//   water   the builder pours ONE source, at (9, 14, 13), against the tank's west face where its spout would be, last
//           of all, open above. A source that can fall does not spread sideways (vanilla FlowingFluid.spread), so it
//           only falls: past the wheel's east side at (9, 13, 13) (the tank at x 10 and the lantern's back wall at z 12
//           beside it: solid faces, so Create reads a downward flow) into a closed sump at (9, 12, 13) (a stone floor
//           and four stone sides). Nothing water can break stands on its path; with Create gone (the wheel air) the
//           same water falls into the same sump and spreads nowhere.
//   wheel   create:water_wheel at (8, 13, 13), axle along z (facing south): 8 RPM, 256 stress units.
//   in      shafts (axis z) at (8, 13, 12) through the lantern's back wall and (8, 13, 11); a gearbox (axis x) at (8, 13, 10)
//           turns the drive down.
//   gear 1  a large cogwheel (axis y) at (8, 12, 10) under the gearbox (8 RPM) meshes diagonally with a small cogwheel
//           (axis y) at (9, 12, 9): 16 RPM.
//   gear 2  a large cogwheel (axis y) at (9, 13, 9), on the small one's axle (16 RPM), meshes diagonally with a small
//           cogwheel (axis y) at (8, 13, 8): 32 RPM.
//   mixer   a small cogwheel (axis y) at (7, 13, 8) meshes with (8, 13, 8) (32 RPM, the other way); shafts (axis y)
//           from (7, 12, 8) down to (7, 5, 8) to a small cogwheel at (7, 4, 8) that meshes with the cog of
//           create:mechanical_mixer at (8, 4, 8): 32 RPM. (Create's mixer takes no shaft, not even from above: it
//           turns only through its cog, ROADMAP 50.3's compat build saw it stand still under a shaft.)
//           (8, 3, 8) empty (its head works there); create:basin (facing down: it keeps what it makes) at (8, 2, 8) on
//           create:blaze_burner at (8, 1, 8).
//   belt    from the large cogwheel at (8, 12, 10) a shaft (axis y) drops (8, 11..2, 10) to a gearbox (axis z) at
//           (8, 1, 10), shafts (axis x) at (7..5, 1, 10) turn the belt's start pulley at (4, 1, 10); the belt runs north
//           (4, 1, 10 .. 3) at 8 RPM to create:depot at (4, 1, 2), under the front window.
//   load    the mixer at 4 stress units per RPM at 32 RPM = 128 of the wheel's 256; shafts, gearboxes, cogs and the belt
//           cost nothing. Turned or mirrored, every mesh stays a diagonal of parallel axes, so it turns the same.
//   which way the belt runs (for the ship step to see in game; not settled from the jar): the belt carries toward
//   `facing` for one spin of its pulleys and away for the other, and the spin here follows from the wheel (which side
//   the water falls on) through two gearboxes and a mesh. Option A (as drawn): toward the depot. Option B, if the game
//   shows items riding south: turn the belt end for end (facing south, start (4, 1, 3), end (4, 1, 10)); the drive
//   stays at (5, 1, 10) on what is then the end pulley, every pinned cell stays. Never fix it by moving the water to
//   the wheel's west side.
// Pinned cells kept from the brief (no test moves): mixer (8, 4, 8), basin (8, 2, 8), burner (8, 1, 8), depot (4, 1, 2),
// belt (4, 1, 4). Moved: the smoker, from (9, 1, 2) to (16, 1, 5) in the cookhouse (the ship step moves the pin).
// New size for StarterBlueprints.BRASS_FOUNDRY: new Vec3i(19, 21, 16).

export const SIZE = [19, 21, 16] // w, h, d (limit 20 x 27 x 16)

// Palette (60/30/10): red brick (the hall, the tank tower, the cookhouse) / weathered copper (both roofs, the catslide,
// the lantern's bands and glazed end, the tank's hoops: green-blue over the red) / brass (fresh waxed copper: the
// lantern's mullions, the hood, the barge boards, the gear-window hub, the stock) with andesite-and-stone-brick
// dressings (plinth, pilasters, sills, the wall-top band, the race where the water falls). Why: a brass
// works is a copper works, so its roofs are the metal grown old and its frame is the metal new; the brick is the
// furnace trade's own; andesite is Create's stone.
export const PALETTE = {
  brick: [['bricks', 12], ['granite', 1]],
  stone: [['stone_bricks', 5], ['andesite', 3], ['cracked_stone_bricks', 1]],
  dressed: 'stone_bricks',
  dressedStairs: 'stone_brick_stairs',
  dressedSlab: 'stone_brick_slab',
  key: 'chiseled_stone_bricks',
  roof: { full: 'waxed_weathered_cut_copper', stairs: 'waxed_weathered_cut_copper_stairs', slab: 'waxed_weathered_cut_copper_slab' },
  brass: { full: 'waxed_cut_copper', stairs: 'waxed_cut_copper_stairs', slab: 'waxed_cut_copper_slab', block: 'waxed_cut_copper', grate: 'waxed_copper_grate' },
  tank: 'waxed_cut_copper', // the tank's shell (the one brass tint of every accent), banded in the roofs' weathered copper
  hallFloor: 'polished_andesite',
  cookFloor: 'spruce_planks',
  beam: 'stripped_dark_oak_log',
  wood: 'dark_oak', // doors, trapdoors, the tie beams' braces
  furnitureWood: 'spruce',
  glass: 'glass_pane',
  finial: 'lightning_rod',
}

export const MASSES = {
  hall: { x0: 3, x1: 13, z0: 1, z1: 12, wallTop: 7, roof: { axis: 'z', across: [2, 14], along: [0, 13], eaveY: 7 } },
  lantern: { x0: 5, x1: 11, z0: 4, z1: 12, base: 10, top: 14, posts: [4, 8, 12], glass: [11, 13], roof: { axis: 'z', across: [5, 11], along: [4, 12], eaveY: 15 } }, // flush: an overhang over the hall roof would be walled in (kit.seal_slots)
  cookhouse: { x0: 13, x1: 17, z0: 2, z1: 12, wallTop: 3, slope: [[15, 6], [16, 5], [17, 4], [18, 3]], along: [1, 13] }, // its back flush with the hall's, the catslide ending with the hall roof's back eave
  raceHouse: { x0: 8, x1: 9, z0: 13, z1: 14, top: 12 }, // the wheel's seat and the sump, in dressed stone
  tankTower: { x0: 10, x1: 12, z0: 13, z1: 15, brickTop: 11, cornice: 12, tank: [13, 16], hoops: [13, 16] },
  dormer: { face: 16, zc: 8, y0: 4 }, // a gabled dormer in the catslide over the cookhouse's back half
  flue: { x: 18, z: 5, top: 7 }, // the smoker's chimney, outside the cookhouse's east wall through the catslide's eave
}

export const MACHINE = {
  mixer: [8, 4, 8], basin: [8, 2, 8], burner: [8, 1, 8],
  mixerShaft: { x: 7, z: 8, y0: 5, y1: 12 }, // beside the mixer: its cogwheels at both ends turn it through its cog
  smallCogs: [[9, 12, 9], [8, 13, 8], [7, 13, 8], [7, 4, 8]],
  largeCogs: [[8, 12, 10], [9, 13, 9]],
  gearboxes: [{ at: [8, 13, 10], axis: 'x' }, { at: [8, 1, 10], axis: 'z' }],
  shafts: [
    { cells: [[8, 13, 12], [8, 13, 11]], axis: 'z' },
    { cells: [2, 3, 4, 5, 6, 7, 8, 9, 10, 11].map(y => [8, y, 10]), axis: 'y' },
    { cells: [[7, 1, 10], [6, 1, 10], [5, 1, 10]], axis: 'x' },
  ],
  wheel: { at: [8, 13, 13], facing: 'south' },
  belt: { cells: [10, 9, 8, 7, 6, 5, 4, 3].map(z => [4, 1, z]), facing: 'north' },
  depot: [4, 1, 2],
  // the water, as it settles: the poured source, the cell it runs into, the fall past the wheel, the sump
  water: [{ at: [9, 14, 13], level: 0 }, { at: [9, 13, 13], level: 8 }, { at: [9, 12, 13], level: 8 }],
  // cells that stay empty round the wheel and over the source (no roof, no trim): the wheel's west, top and back
  // sides, the pour over the source
  keepClear: [[7, 13, 13], [8, 13, 14], [9, 15, 13]], // (8, 14, 13), the wheel's top, is closed by the recipe: the source spreads sideways
}

export const ROOMS = [
  { name: 'Machine hall', use: 'the mixer on its burner, the belt to the depot, the store by the mixer', box: [4, 1, 2, 12, 6, 11] },
  { name: 'Gear lantern', use: 'the glazed roof monitor where the gear train turns (seen, not walked)', box: [6, 11, 5, 10, 16, 11] },
  { name: 'Cookhouse', use: "the Chef's kitchen: his smoker, counters, chest, a table for two", box: [14, 1, 3, 16, 3, 9] },
]

export const OPENINGS = {
  hallDoor: { at: [8, 1, 1], facing: 'south', out: 'north', step: [8, 0, 0] },
  cookDoor: { at: [16, 1, 2], facing: 'south', out: 'north', step: [16, 0, 1] },
  innerDoor: { at: [13, 1, 6], height: 2 },
  windows: [
    { name: 'front west', cells: [[4, 2, 1], [5, 2, 1], [4, 3, 1], [5, 3, 1], [4, 4, 1], [5, 4, 1]], out: 'north', dress: 'sill' },
    { name: 'front east', cells: [[11, 2, 1], [12, 2, 1], [11, 3, 1], [12, 3, 1], [11, 4, 1], [12, 4, 1]], out: 'north', dress: 'sill' },
    { name: 'over the door', cells: [[7, 5, 1], [8, 5, 1], [9, 5, 1], [7, 6, 1], [8, 6, 1], [9, 6, 1]], out: 'north', dress: null },
    { name: 'gear window', cells: [[7, 9, 1], [8, 9, 1], [9, 9, 1], [7, 10, 1], [9, 10, 1], [7, 11, 1], [8, 11, 1], [9, 11, 1]], out: 'north', dress: null },
    { name: 'west front', cells: [[3, 2, 6], [3, 3, 6], [3, 4, 6], [3, 2, 7], [3, 3, 7], [3, 4, 7]], out: 'west', dress: 'sill' },
    { name: 'west back', cells: [[3, 2, 9], [3, 3, 9], [3, 4, 9], [3, 2, 10], [3, 3, 10], [3, 4, 10]], out: 'west', dress: 'sill' },
    { name: 'back west', cells: [[5, 2, 12], [6, 2, 12], [5, 3, 12], [6, 3, 12], [5, 4, 12], [6, 4, 12]], out: 'south', dress: 'sill' },
    { name: 'cook front', cells: [[14, 2, 2], [15, 2, 2], [14, 3, 2], [15, 3, 2]], out: 'north', dress: 'shutters' },
    { name: 'cook east', cells: [[17, 2, 8]], out: 'east', dress: 'shutters' },
    { name: 'cook east back', cells: [[17, 2, 11]], out: 'east', dress: 'shutters' },
    { name: 'cook east front', cells: [[17, 2, 3]], out: 'east', dress: 'shutters' },
  ],
}

// No crafting table, barrel, cauldron or other job site but the smoker (a crafting table is this mod's Carpenter's
// job site): the work surfaces are counters.
// Every piece of furniture, footprint first (kind: a furniture.py kind, or a fixture the game uses as itself).
export const FURNITURE = [
  // Cookhouse: the smoker on the east wall, a run of counters beside it (none in the door's line), crockery shelves over, the store
  // chest in the back corner, a table for two by the front window, a lamp over the table and one over the stove
  { room: 'Cookhouse', kind: 'fixture', part: 'jobBlock', pos: [16, 1, 5], facing: 'west', block: 'smoker' },
  { room: 'Cookhouse', kind: 'counter', part: 'counter', pos: [16, 1, 6], facing: 'east', wood: 'spruce' },
  { room: 'Cookhouse', kind: 'counter', part: 'counter', pos: [16, 1, 7], facing: 'east', wood: 'spruce' },
  // (16, 1, 4) stays clear: the cell two in from the cookhouse door, the Chef's way in
  { room: 'Cookhouse', kind: 'counter', part: 'counter', pos: [16, 1, 8], facing: 'east', wood: 'spruce' },
  { room: 'Cookhouse', kind: 'shelf', part: 'shelf', pos: [16, 3, 6], facing: 'west', wood: 'spruce', crockery: 'flower_pot' },
  { room: 'Cookhouse', kind: 'shelf', part: 'shelf', pos: [16, 3, 4], facing: 'west', wood: 'spruce', crockery: 'potted_brown_mushroom' },
  { room: 'Cookhouse', kind: 'fixture', part: 'chest', pos: [16, 1, 9], facing: 'west', block: 'chest' },
  { room: 'Cookhouse', kind: 'counter', part: 'counter', pos: [14, 1, 9], facing: 'west', wood: 'spruce' },
  { room: 'Cookhouse', kind: 'counter', part: 'counter', pos: [14, 1, 10], facing: 'west', wood: 'spruce' },
  { room: 'Cookhouse', kind: 'counter', part: 'counter', pos: [14, 1, 11], facing: 'west', wood: 'spruce' },
  { room: 'Cookhouse', kind: 'table', part: 'table', pos: [14, 1, 4], facing: 'east', wood: 'spruce', colour: 'white' },
  { room: 'Cookhouse', kind: 'chair', part: 'chair', pos: [14, 1, 3], facing: 'south', wood: 'spruce' },
  { room: 'Cookhouse', kind: 'chair', part: 'chair', pos: [14, 1, 5], facing: 'north', wood: 'spruce' },
  { room: 'Cookhouse', kind: 'lamp', part: 'hangingLamp', pos: [15, 4, 6], facing: 'north', chain: 1, wood: 'spruce' },
  { room: 'Cookhouse', kind: 'rug', part: 'rug', cellsAbs: [[15, 1, 4], [15, 1, 5], [15, 1, 6]], colour: 'red' },
  // Machine hall: the dough store (a chest) against the east wall by the mixer, a proving table under the east lamp, an anvil
  // and a crafting bench for the smiths at the front west, hanging lamps from the tie beams
  { room: 'Machine hall', kind: 'fixture', part: 'chest', pos: [12, 1, 9], facing: 'west', block: 'chest' },
  { room: 'Machine hall', kind: 'counter', part: 'counter', pos: [6, 1, 3], facing: 'north', wood: 'spruce' },
  { room: 'Machine hall', kind: 'counter', part: 'counter', pos: [12, 1, 8], facing: 'east', wood: 'spruce' },
  { room: 'Machine hall', kind: 'counter', part: 'counter', pos: [12, 1, 10], facing: 'east', wood: 'spruce' },
  { room: 'Machine hall', kind: 'counter', part: 'counter', pos: [12, 1, 11], facing: 'east', wood: 'spruce' },
  { room: 'Machine hall', kind: 'table', part: 'table', pos: [10, 1, 4], facing: 'east', wood: 'spruce', colour: 'white' },
  { room: 'Machine hall', kind: 'table', part: 'table', pos: [11, 1, 4], facing: 'west', wood: 'spruce', colour: 'white' },
  // the west half: the smiths' table for two by the west windows, between the belt and the mixer's rail
  { room: 'Machine hall', kind: 'table', part: 'table', pos: [6, 1, 5], facing: 'south', wood: 'spruce', colour: 'white' },
  { room: 'Machine hall', kind: 'table', part: 'table', pos: [6, 1, 6], facing: 'north', wood: 'spruce', colour: 'white' },
  { room: 'Machine hall', kind: 'chair', part: 'chair', pos: [5, 1, 5], facing: 'east', wood: 'spruce' },
  { room: 'Machine hall', kind: 'chair', part: 'chair', pos: [5, 1, 6], facing: 'east', wood: 'spruce' },
  { room: 'Machine hall', kind: 'lamp', part: 'hangingLamp', pos: [6, 5, 7], facing: 'north', chain: 1, wood: 'spruce' },
  { room: 'Machine hall', kind: 'lamp', part: 'hangingLamp', pos: [6, 5, 4], facing: 'north', chain: 1, wood: 'spruce' },
  { room: 'Machine hall', kind: 'lamp', part: 'hangingLamp', pos: [11, 5, 4], facing: 'north', chain: 1, wood: 'spruce' },
  { room: 'Machine hall', kind: 'lamp', part: 'hangingLamp', pos: [11, 5, 7], facing: 'north', chain: 1, wood: 'spruce' },
  { room: 'Gear lantern', kind: 'lamp', part: 'hangingLamp', pos: [8, 16, 6], facing: 'north', chain: 1, wood: 'spruce' },
]

// The walking line: the street -> the hall door -> up the middle aisle to the mixer's east side (9, 1, 8), within 3
// of the mixer; the dough store at (12, 1, 9) beside it; through the inner door (13, 1, 6) into the cookhouse, to the
// smoker's front (15, 1, 5) and the chest's (15, 1, 9); out of the cookhouse door to the street.
export const WALK = [
  [8, 0, 0], [8, 1, 1], [8, 1, 2], [8, 1, 4], [8, 1, 6], [9, 1, 7], [9, 1, 8], [10, 1, 8], [11, 1, 9],
  [10, 1, 6], [12, 1, 6], [13, 1, 6], [14, 1, 6], [15, 1, 5], [15, 1, 8], [15, 1, 9], [15, 1, 3], [16, 1, 3], [16, 1, 2], [16, 0, 1],
]

// The yard: lamp posts at the hall's front corners, a pallet of copper stock and a sack pile by the depot window, a
// bench under the cookhouse window.
export const OUTSIDE = {
  lamps: [[1, 0], [18, 0]],
  stock: { cells: [[1, 0, 3], [1, 0, 4]], top: [[1, 1, 3]] },
  sacks: [[1, 0, 6], [1, 0, 7], [1, 1, 7]],
}
