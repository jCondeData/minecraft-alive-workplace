// The Sawmill, the plan: drawn before any wall. Story, palette, masses, the machine line (every Create part, its
// cell and state), rooms, openings, every furniture footprint and the walking line; recipe.mjs assembles it from the
// parts library (../parts) in passes.
//
// Three rough concepts (ROADMAP 49, REBUILD.md):
//   1. "Saw barn": one long gabled timber barn over the whole line, the wheel in a lean-to on its side, the lumberjack
//      at a bench inside. Massing: one box. Roof: one 45-degree gable along z. Plan: one room. Story: a farm barn that
//      got a saw. Safe, and reads like any barn: the machine and the water are hidden, nothing says "mill".
//   2. "Mill tower": a two-storey stone mill house, the wheel in a pit at its foot, the saw line in the undercroft,
//      the lumberjack's room upstairs. Massing: a tall cube. Roof: a steep hipped cap. Plan: two stacked rooms. Story: a
//      grain mill turned to timber. Strong silhouette, but the line is buried, the job block would sit upstairs away
//      from the trees, and a tower is the wrong shape for a belt that wants length.
//   3. "Race-and-tank mill": the water is the hero. A hooped water tank on log legs stands at the back corner; a
//      wooden flume runs from it along the top of a stone race wall and drops through a chute onto the wheel, whose
//      axle goes through the wall into the shed: you can follow the water and the drive from the street. The saw shed
//      is a long gable over the line (logs in at a back door from the log yard, planks out at the front onto the
//      depot behind the front window, under a hoist beam), and the lumberjack's lodge is a lower cross-gabled wing on the east with its
//      own door, chest and rack. Massing: tall tank + long shed + low wing, three heights stepping down west to east.
//      Roof: a 45-degree gable along z, a cross gable on the wing. Plan: the shed (machine room) and the lodge,
//      joined by an inner doorway. Story: a lumberjack and his partner dammed nothing; they built a tank, let the
//      water fall once, and the mill runs on that drop forever.
// Strongest: 3. It is the only one where the working parts make the silhouette (tank, flume, chute, wheel, the depot's
// window and hoist all read from the street), it keeps the whole line on one floor and in view, and its three stepped masses
// give the depth 1 lacks without 2's buried machine. Built here.
//
// Coordinates: x across the front (west 0 -> east 14), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The front is the z = 0 side: the shed's double doors and the lodge's door face it.
//
// The machine (Create; every kinetic part turns, nothing decorative):
//   water   the builder pours ONE source, the flume's head at (1, 5, 9) in a notch cut in the tank's face (open above,
//           so the water reads as coming out of the tank), last of all. It runs four cells to the lip
//           (1, 5, 5), falls down the chute (1, 4..2, 5) past the wheel's south face, and lands in a closed sump at
//           (1, 1, 5) (stone floor, four stone sides). Falling water touches a stone face all the way down (Create reads
//           its flow), and nothing water can break stands on its path: with Create gone (the wheel air) the same water
//           falls into the same closed sump and spreads nowhere.
//   wheel   create:water_wheel at (1, 3, 4), axle along x (facing east): 8 RPM and 256 stress units (32 per RPM).
//   drop    in the west wall x = 2: a gearbox (axis z) at (2, 3, 4) on the axle, a shaft (axis y) at (2, 2, 4), a
//           gearbox (axis z) at (2, 1, 4) turning the drive east onto the floor.
//   cogs    three cogwheels (axis x) at (3, 1, 3..5) meshing in a row: the middle one takes the drive from the gearbox,
//           the front one turns the saw at (4, 1, 3), the back one the belt's end pulley at (4, 1, 5).
//   saws    create:mechanical_saw facing up, about x (axis_along_first true), at (4, 1, 4) (not flipped; its cog is the
//           driven one) and (4, 1, 3) (flipped: geared through one more cog it turns the other way).
//   belt    create:belt facing north, start pulley (4, 1, 8), middle (4, 1, 7) and (4, 1, 6), end pulley (4, 1, 5):
//           logs go on at the back door and ride north into the saws.
//   which way the belt runs (for the ship step to see in game; not settled from the jar): Create's belt carries items
//   toward `facing` when its pulleys turn one way and away from it when they turn the other, and which way they turn
//   here depends on the wheel's spin (which side the water falls on), the two gearboxes (a gearbox reverses the
//   direction it hands on round a corner) and the cogs (each mesh reverses). Count from the wheel: the gearbox on
//   the axle, the shaft, the bottom gearbox, cog (3,1,4), cog (3,1,5) -> the belt's end pulley: two reversals by mesh
//   and corners I could not prove, so either is possible. Option A (as drawn): logs ride north into the saws.
//   Option B, if the game shows them riding south: turn the belt end for end (facing south, start (4,1,5), middle
//   (4,1,6) and (4,1,7), end (4,1,8)), which reverses its travel with the same drive and keeps every pinned cell and
//   every part turning; nothing else changes (the saws cut whichever way they turn, `flipped` keeps the two alike).
//   Do NOT fix it by moving the water to the wheel's other face: the chute, sump and Create-absent safety hang on it.
//   depot   create:depot at (4, 1, 2), at the shed's mouth: planks come off here.
//   load    two saws at 4 stress units per RPM at 8 RPM = 64 of the wheel's 256; shafts, gearboxes, cogs and the belt
//           cost nothing. Mirrored left to right everything mirrors together (the water stays beside the wheel, the
//           cogs on the same row), so the flipped build turns the same.
// Pinned cells kept from the old drawing (no test moves): fletching table (10, 1, 2), saws (4, 1, 3) and (4, 1, 4),
// depot (4, 1, 2), belt (4, 1, 6). New size for StarterBlueprints.SAWMILL: new Vec3i(15, 11, 14).
// Belt block-entity data: drawn without it (as every Create build in create_builds.py); Create joins the segments
// from their states. The ship step may add Index/Length/IsController/Controller (controller = the start, (4, 1, 8),
// Length 4, Index 0..3 from the start) if the compat test wants it in the file.

export const SIZE = [15, 11, 14] // w, h, d (limit 20 x 26 x 16)

// Palette (60/30/10): spruce boards and logs (walls, gables, wing) / a stone race of stone bricks and cobble (the wheel
// wall, chute, plinth) under a dark oak shingle roof / oak and copper-free accents: stripped logs, the tank's dark hoops,
// lanterns. Why: the mill's wood is its own product (spruce boards it sawed itself), the stone is only where the water
// is, and the dark roof makes the pale tank and the boards read.
export const PALETTE = {
  stone: [['stone_bricks', 5], ['cobblestone', 3], ['mossy_stone_bricks', 2], ['andesite', 1]],
  race: 'stone_bricks', // the chute, sump and trough walls: dressed, so the water's path reads as built
  frame: 'spruce_log',
  beam: 'stripped_spruce_log',
  boards: 'spruce_planks',
  floor: 'spruce_planks',
  lodgeFloor: 'oak_planks',
  roof: { full: 'dark_oak_planks', stairs: 'dark_oak_stairs', slab: 'dark_oak_slab' },
  trim: 'dark_oak',
  wood: 'spruce',
  glass: 'glass_pane',
  tank: { stave: 'stripped_oak_log', leg: 'dark_oak_log', hoop: 'dark_oak_log', cap: { full: 'dark_oak_planks', stairs: 'dark_oak_stairs' } },
  bed: 'spruce_planks', // the flume's bed
  logs: ['oak_log', 'spruce_log', 'birch_log'],
}

export const MACHINE = {
  wheel: { at: [1, 3, 4], facing: 'east' },
  drop: { x: 2, z: 4, top: 3, bottom: 1, axis: 'z' },
  cogs: { cells: [[3, 1, 3], [3, 1, 4], [3, 1, 5]], axis: 'x' },
  saws: [{ at: [4, 1, 4], flipped: false }, { at: [4, 1, 3], flipped: true }],
  belt: { cells: [[4, 1, 8], [4, 1, 7], [4, 1, 6], [4, 1, 5]], facing: 'north' },
  depot: [4, 1, 2],
  flume: { run: [[1, 9], [1, 8], [1, 7], [1, 6], [1, 5]], y: 5, side: [[0, 5], [0, 6], [0, 7], [0, 8], [2, 5], [2, 6], [2, 7], [2, 8]], end: [1, 10], notch: [1, 6, 9] },
  chute: { at: [1, 5], top: 5, bottom: 1, walls: [[0, 5], [2, 5], [1, 6]], sump: [[1, 4]] },
}

export const MASSES = {
  shed: { x0: 2, x1: 8, z0: 1, z1: 10, wallTop: 3, beamY: 4, roof: { axis: 'z', across: [2, 8], along: [0, 11], eaveY: 5 } },
  lodge: { x0: 8, x1: 13, z0: 1, z1: 7, wallTop: 3, beamY: 4, roof: { axis: 'x', across: [0, 8], along: [8, 14], eaveY: 4 } },
  tank: { x0: 0, z0: 9, w: 3, d: 3, y0: 5, y1: 8, hoops: [5, 8] },
  yard: { z0: 11, z1: 13 },
}

export const ROOMS = [
  { name: 'Saw shed', use: 'the machine line: belt, two saws, depot', box: [3, 1, 2, 7, 3, 9] },
  { name: 'Lodge', use: "the lumberjack's room: his fletching table, chests, axe bench and rack", box: [9, 1, 2, 12, 3, 6] },
]

export const OPENINGS = {
  mouth: { doors: [[5, 1, 1, 'left'], [6, 1, 1, 'right']], facing: 'south' }, // the shed's double doors, the depot just inside under the front window
  intake: { at: [4, 1, 10], facing: 'north' }, // the log door: logs in from the yard at the back, onto the belt's start
  lodgeDoor: { at: [11, 1, 1], facing: 'south' },
  innerDoor: { at: [8, 1, 4], height: 2 },
  windows: [
    { name: 'shed front', cells: [[3, 2, 1], [4, 2, 1], [3, 3, 1], [4, 3, 1]], out: 'north', dress: 'sill' },
    { name: 'lodge front', cells: [[9, 2, 1]], out: 'north', dress: 'shutters' },
    { name: 'lodge east', cells: [[13, 2, 3], [13, 2, 5]], out: 'east', dress: null },
    { name: 'shed east', cells: [[8, 2, 9]], out: 'east', dress: 'shutters' },
    { name: 'shed gable', cells: [[5, 6, 1]], out: 'north', dress: null },
    { name: 'shed back', cells: [[6, 2, 10], [7, 2, 10]], out: 'south', dress: 'shutters' },
  ],
}

// Every piece of furniture, footprint first (kind: a furniture.py kind, or a fixture the game uses as itself).
export const FURNITURE = [
  { room: 'Lodge', kind: 'fixture', part: 'jobBlock', pos: [10, 1, 2], facing: 'south', block: 'fletching_table' },
  { room: 'Lodge', kind: 'fixture', part: 'chest', pos: [12, 1, 2], facing: 'south', block: 'chest' },
  { room: 'Lodge', kind: 'fixture', part: 'chest', pos: [12, 1, 6], facing: 'west', block: 'chest' },
  { room: 'Lodge', kind: 'fixture', part: 'worktop', pos: [9, 1, 6], facing: 'north', block: 'crafting_table' },
  { room: 'Lodge', kind: 'table', part: 'table', pos: [11, 1, 5], facing: 'west', wood: 'spruce', colour: 'brown' },
  { room: 'Lodge', kind: 'chair', part: 'chair', pos: [10, 1, 5], facing: 'east', wood: 'spruce' },
  { room: 'Lodge', kind: 'lamp', part: 'hangingLamp', pos: [11, 3, 4], facing: 'north', chain: 4, wood: 'spruce' },
  { room: 'Saw shed', kind: 'lamp', part: 'hangingLamp', pos: [6, 3, 4], facing: 'north', wood: 'spruce' },
  { room: 'Saw shed', kind: 'lamp', part: 'hangingLamp', pos: [6, 3, 7], facing: 'north', wood: 'spruce' },
]

// The walking line: the street -> the shed's mouth beside the depot -> along the line to the intake; and the street ->
// the lodge door -> the fletching table and the chests; and through the inner door between them.
export const WALK = [
  [6, 1, 0], [6, 1, 1], [6, 1, 2], [6, 1, 4], [5, 1, 6], [5, 1, 9], [5, 1, 10], [5, 1, 11],
  [11, 1, 0], [11, 1, 1], [11, 1, 2], [11, 1, 3], [10, 1, 3], [12, 1, 3], [12, 1, 5], [9, 1, 4], [8, 1, 4], [7, 1, 4],
]

// Outside: the log yard behind (stacks of the three woods, a sawhorse), a board stack by the depot's mouth, lamp
// posts at the front corners.
// The offcut rack in the shed: logs laid along z against the east wall, east of the walking line (x 5-6), clear of
// the inner door (8, 1, 4) and the shed's east window (8, 2, 9).
export const RACK = [[7, 1, 5], [7, 1, 6], [7, 1, 7], [7, 1, 8], [7, 2, 6], [7, 2, 7]]

export const OUTSIDE = {
  logStacks: [{ x0: 7, x1: 12, z: 12, log: 'oak_log', high: 2 }, { x0: 8, x1: 12, z: 11, log: 'spruce_log', high: 1 }, { x0: 9, x1: 13, z: 13, log: 'birch_log', high: 1 }],
  boards: [[8, 0], [9, 0]], // a stack of sawn boards (slabs) by the mouth, on the path
  lamps: [[2, 0], [13, 0]],
}
