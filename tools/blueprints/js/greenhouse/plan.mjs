// The Greenhouse (the Leat House), the plan: drawn before any wall. Story, palette, masses, the machine line (every
// Create part, its cell and state, and the water that drives it), the farm, openings, the walking line; recipe.mjs
// assembles it from the parts library (../parts) in passes. Brief: tools/blueprints/briefs/greenhouse.md.
//
// Three rough concepts (ROADMAP 49 / 50.9, REBUILD.md):
//   1. "Lean-to on a hot wall": a tall brick back wall with a boiler stack, a single glass lean-to sloping down to the
//      street in front of it, the wheel in a pit at the west end and the belt along the foot of the wall. Massing: a
//      wall and a slope. Roof: one long glass lean-to. Plan: one long bed in front of a walk along the wall. Story: the
//      walled kitchen garden's forcing house. Honest, but from the street it is a sloped sheet of glass with a wall
//      behind it: a box cut in half, and its back is a blank brick cliff.
//   2. "The palm house": a curved barrel vault of glass on white ribs over the beds, a brick engine house with a water
//      tower at one end, the wheel fed from the tower's tank. Massing: a vault and a tower. Roof: one barrel vault.
//      Plan: one hall. Story: the village's show conservatory. A strong outline, but the vault is the shape every
//      glasshouse rebuild reaches for (the Berry Nursery already has one), a fed-from-a-tank wheel pours water high
//      over everything, and a tower of brick takes the block budget the beds need.
//   3. "The Leat House": a ridge-and-furrow glasshouse (Paxton's way of roofing glass: a row of small glazed gables
//      side by side, their valleys carrying the gutters), three gables to the street, the middle one raised on glass
//      clerestory walls into a lofty nave over the walk. The village leat runs past its west side in an open stone
//      trough: a dipping tank at the front where the gardener fills his can, the water running back past an undershot
//      wheel, whose shaft runs into the brick engine bay of the west wall and turns a belt along the inside of that
//      wall, under the harvester and the plough hung there like tools on a rack, forward to a hatch in the front wall
//      where the harvest lands on a depot for the street. A brick boiler with a tall stack warms it from behind.
//      Massing: three glass gables (small, tall, small), a boiler and its stack, the leat. Roof: ridge and furrow with
//      a raised nave. Plan: one glass hall of beds round a central walk, the machine aisle along the west wall.
//      Story: a market gardener roofed his beds the modern way and let the leat that waters them do the carrying.
// Strongest: 3. Its outline is a greenhouse you'd know from a block away (a crystal saw of gables, the tall nave in the
// middle, the stack behind) and it is the only one where the water, the wheel, the belt and the beds are one story:
// the same leat waters the garden and turns the wheel, the machine hangs on the wall it drives through, and the belt
// ends at the street. The wheel turns in running water at ground level, so it starts itself and without Create the
// water runs exactly where it ran (nothing to wet). The safest (1) is a lean-to; this is the strongest. Built here.
//
// Coordinates: x across the front (west 0 -> east 17), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The way in is the nave's door on the north side, a stair step at y 0 before it.
//
// Masses (named parts the owner's feedback can point at):
//   Glasshouse  x 5..17, z 2..11: a stone-brick plinth, one course of brick (the dwarf wall), glass above it from
//               corner post to corner post under the gutters, running on up into the gables. The roof: ridge and furrow, valley gutters on
//               the columns 5, 9, 13 and 17 at y 4, every gable's raking edge a white stair.
//     West gable   x 5..9, ridge x 7 at y 6: over the machine aisle and the wheat.
//     Nave         x 9..13: glass clerestory walls (x 10 and 12, y 5..7) and a ridge at x 11, y 8: the hero, over the
//                  walk, two lanterns hanging from its ridge; its two tall white posts frame the door.
//     East gable   x 13..17, ridge x 15 at y 6: over the roots.
//   Engine bay  the west wall's brick carried up a second course (z 6..10): the shaft comes through it at z 9, the
//               harvester and the plough hang on it over the belt.
//   Wheel hood  x 0..2, z 8..10: a little white gable on four slender birch posts over the wheel: it says "machine here" from the
//               street side, the shaft running out from under it to the engine bay.
//   Hatch       the depot in the front wall at x 6, the opening over it left clear and a little hood: the belt's end.
//   Leat        x 0..2, z 2..12: a coped stone dipping tank (two sources) at its front, a flush stone channel running
//               the water back past the wheel at z 9 to a kerb at z 12; stone bearing blocks under the shaft.
//   Boiler      x 10..12, z 12..13: a brick stove house behind the back wall, its stoke-hole (a furnace) facing back,
//               a stack up from it to y 11 with smoke on top: the vertical the silhouette needs, seen over the nave.

export const SIZE = [18, 13, 14] // w, h, d (limit 20 x 18 x 20)

// Palette (60/30/10): glass (the walls and roof: the building IS its glazing) / red brick on a stone-brick plinth
// (dwarf walls, the engine bay, the boiler and its stack) / white-painted frame (stripped birch: posts, gutters,
// rafters, the gables' outline; polished diorite, the nearest a builder can quarry to white paint), birch
// joinery and copper finials. Why: a Victorian glasshouse is a white cage of
// glass on a brick base; the red brick roots it and ties the boiler and the engine bay to it, the pale frame draws
// the bays and the gables against the sky, the stone of the leat is the ground it stands in.
export const PALETTE = {
  glass: 'glass', // one glass type: whole blocks (a roof must be whole blocks, so the walls match it)
  brick: [['bricks', 1]],
  plinth: [['stone_bricks', 7], ['mossy_stone_bricks', 2], ['cracked_stone_bricks', 1]],
  kerb: [['stone_bricks', 5], ['mossy_stone_bricks', 3], ['cobblestone', 2]],
  coping: 'stone_bricks',
  log: 'polished_diorite', // posts, gutters, beams: the white-painted frame
  frame: 'polished_diorite', // the roof's rafters, ridge beams and the gables' outline
  edge: 'polished_diorite_stairs', // the gables' raking edges
  wood: 'birch', // door, shutter, bracket
  finial: 'lightning_rod',
  path: 'gravel',
  bed: 'stone_bricks', // the machine's bed and the footings under the chest and composter
  step: 'stone_brick_stairs',
  boilerTop: 'brick_slab',
  hoodSlab: 'polished_diorite_slab',
}

export const MASSES = {
  glasshouse: {
    x0: 5, x1: 17, z0: 2, z1: 11,
    dwarfTop: 1, glassTop: 3, beamY: 4,
    gutters: [5, 9, 13, 17],
    tops: { 6: 5, 7: 6, 8: 5, 10: 7, 11: 8, 12: 7, 14: 5, 15: 6, 16: 5 },
    // white members only where they carry something: the corners, the long walls' posts, the nave's two tall posts
    // (from the dwarf wall to its eaves, framing the door), the valley gutters and the gables' raking edges
    cornerPosts: [5, 17], navePosts: [10, 12], sidePosts: [2, 11], // the long walls glazed corner to corner
  },
  engineBay: { x: 5, z0: 6, z1: 10, y: 2 },
  boiler: { x0: 10, x1: 12, z0: 12, z1: 13, top: 1, stack: [11, 13], stackTop: 11 },
}

// The machine line (Create, checked in libs/create-fabric-6.0.10.0+mc1.21.1.jar). Every cell here is pinned by the
// tests or kept from a standing old Greenhouse (the brief's "Kept cells").
export const MACHINE = {
  wheel: { at: [1, 1, 9], facing: 'east' }, // axle along x; running water under it at (1, 0, 9)
  shafts: { cells: [[2, 1, 9], [3, 1, 9], [4, 1, 9], [5, 1, 9]], axis: 'x' },
  belt: { cells: [[6, 1, 9], [6, 1, 8], [6, 1, 7], [6, 1, 6], [6, 1, 5], [6, 1, 4], [6, 1, 3]], facing: 'north' }, // start pulley (6,1,9) on the shaft, end (6,1,3)
  depot: [6, 1, 2],
  harvester: { at: [6, 2, 7], facing: 'east' }, // held by the engine bay's brick at (5, 2, 7)
  plough: { at: [6, 2, 9], facing: 'east' }, // held by (5, 2, 9)
  leat: { x: 1, z0: 3, z1: 11, sources: [3, 4], wheelZ: 9, gaps: [[2, 9]], raisedZ: [2, 5] }, // a coped dipping tank, then a flush channel
  // the wheel hood: a little white gable over the wheel on four posts standing on the leat's kerbs (the shaft runs
  // out from under it, in the open, to the engine bay)
  hood: { x0: 0, x1: 2, z: 9, eaveY: 3, posts: [[0, 8], [0, 10], [2, 8], [2, 10]] },
  pads: [[3, 0, 9], [4, 0, 9]], // bearing blocks under the shaft
}

// The farm: one joined field (Fields.farmNear): two beds either side of the walk, joined at the walk's head through
// the dipping well (11..12, 0, 9) at their own level; water channels down each bed keep every cell within 4.
export const FARM = {
  y: 0, z0: 4, z1: 10,
  machineBed: { x: 6, z0: 3, z1: 10 },
  frontWalk: { z: 3, x0: 7, x1: 16 },
  walk: { x: 11, z0: 4, z1: 8 },
  water: [[9, 4], [9, 5], [9, 6], [9, 7], [9, 8], [9, 9], [9, 10], [14, 4], [14, 5], [14, 6], [14, 7], [14, 8], [14, 9], [11, 9], [12, 9]],
  footings: [[11, 10], [12, 10], [13, 10], [14, 10], [15, 10], [16, 10]], // under the chest, the composter, the potting bench
  crops: { 7: 'wheat', 8: 'wheat', 10: 'wheat', 12: 'carrots', 13: 'carrots', 15: 'potatoes', 16: 'potatoes' },
  pinned: { farmland: [7, 0, 5], wheat: [7, 1, 5] },
}

export const OPENINGS = {
  door: { at: [11, 1, 2], facing: 'south', out: 'north', step: [11, 0, 1] },
  transom: [11, 3, 2],
  doorLamp: { bracket: [11, 4, 1], lamp: [11, 3, 1] }, // a lantern on a birch bracket out of the nave's door head
  hatch: { at: [6, 1, 2], out: 'north' },
}

// The Farmer's place: his composter (pinned, the job site) and chest (kept) at the head of the walk, the chest backed by
// the back wall, his potting bench beside them along the back wall. Lamps: two lanterns on chains from the nave's rafters over the walk, one at the door, one over the potting bench.
export const FURNITURE = [
  { kind: 'fixture', part: 'composter', pos: [12, 1, 10] },
  { kind: 'fixture', part: 'chest', pos: [11, 1, 10], facing: 'north' },
  // the potting bench along the back wall beside the composter: four counters, pots of cuttings on its top
  { kind: 'counter', part: 'counter', pos: [13, 1, 10], facing: 'south', wood: 'birch' },
  { kind: 'counter', part: 'counter', pos: [14, 1, 10], facing: 'south', wood: 'birch' },
  { kind: 'counter', part: 'counter', pos: [15, 1, 10], facing: 'south', wood: 'birch' },
  { kind: 'counter', part: 'counter', pos: [16, 1, 10], facing: 'south', wood: 'birch' },
  { kind: 'fixture', part: 'pot', pos: [13, 3, 10], block: 'potted_fern' },
  { kind: 'fixture', part: 'pot', pos: [15, 3, 10], block: 'potted_red_tulip' },
  { kind: 'fixture', part: 'pot', pos: [16, 3, 10], block: 'potted_cactus' },
  { kind: 'fixture', part: 'hangingLamp', pos: [11, 6, 5], chain: 1 },
  { kind: 'fixture', part: 'hangingLamp', pos: [11, 6, 8], chain: 1 },
  { kind: 'fixture', part: 'hangingLamp', pos: [15, 5, 9] }, // ship step: one over the potting bench, hung from the east ridge's glass (its counters were in the dark, so no furniture spots were saved)
]

// The walking line: the street -> the step -> the door -> the front walk -> the central walk to the composter and
// chest; the front walk west to the belt's end by the hatch.
export const WALK = [
  [11, 0, 0], [11, 0, 1], [11, 1, 2], [11, 1, 3], [11, 1, 4], [11, 1, 5], [11, 1, 6], [11, 1, 7], [11, 1, 8],
  [10, 1, 3], [9, 1, 3], [8, 1, 3], [7, 1, 3],
]

// Outside: copper downpipes from the two valley gutters' front ends to the ground, beds of flowers in their own soil
// under the front glass either side (the hatch's front kept clear for whoever collects from it).
export const OUTSIDE = {
  downpipes: [{ x: 9, z: 1 }, { x: 13, z: 1 }],
  flowerBeds: [{ x0: 7, x1: 8, z: 1, plants: ['poppy', 'oxeye_daisy'] }, { x0: 14, x1: 16, z: 1, plants: ['cornflower', 'allium', 'azure_bluet'] }],
}
