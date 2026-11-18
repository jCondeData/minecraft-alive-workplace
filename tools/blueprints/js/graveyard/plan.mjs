// The Graveyard (graveyard, graveyard_2), the plan: drawn before any wall (ROADMAP 49, brief
// tools/blueprints/briefs/graveyard.md). recipe.mjs assembles it from the parts library (../parts) in passes.
//
// Three rough concepts, before choosing:
//   A. The Lychgate Chapel: a coped rubble wall round the plot, a roofed timber lychgate in the front wall, a path up
//      the middle between two grave plots, and a small steep-roofed stone chapel across the back where the Undertaker
//      keeps his table: the long axis of a village churchyard. Roof: one steep slate gable along the axis, a 45-degree
//      one on the gate. Story: every funeral comes in under the gate's roof and ends at the chapel's altar.
//   B. The Cloister Garth: lean-to walks on posts round three sides of a square of graves, the Undertaker's table in
//      the back walk. Roof: a ring of lean-tos. Story: a monastery's burial garth. It hides the graves from the street,
//      spends half the budget on corners where lean-tos meet, and puts roofs over the very cells the graves need.
//   C. The Lantern of the Dead: a slim stone tower at the back of an open yard with a light burning in an open
//      chamber at its top (the medieval French "lanterne des morts", lit for the dead each night), the Undertaker's
//      table in a little room at its foot. Roof: a stone pyramid cap. Story: the village sees the dead are watched
//      over from every street. Strong at night, but a lone tower over a bare yard reads as a lighthouse, not a
//      graveyard.
// Built: A and C together, A's axis ending in C's tower. The lychgate frames the tower; the tower is the chapel's
// porch and its belfry with no bell (a bell is a meeting point: it would gather the village) but a light; the chapel
// behind it holds the Undertaker's brewing stand as its altar, the bier in the middle under the lamp, pews along the walls for
// the mourners. A, alone, is the safe churchyard; C's lit tower is what makes it the village's graveyard from a
// distance, day and night (it is the tallest thing in it: 16 high against the chapel's 14 and the gate's 7).
// Two young Irish yews stand either side of the chapel, the churchyard tree.
//
// Level II (graveyard_2) is the same yard a generation on, with no new function (the code treats both alike): the
// yews have grown tall, the wall's piers rise a block and carry lanterns (the back two the mourners' flowers), a
// stone cross stands in the middle of each plot, the front wall is raised and railed in iron, benches face the graves from the cross walk, and the path gets lamp
// posts. It keeps every grave slot and all but a handful of Level I's blocks (the raised piers' caps).
//
// Coordinates: x across the front (west 0 -> east 12), y up (0 = the layer on the clicked block, the world's own ground
// under it), z from the front (z = 0, north, the street) to the back.
//
// Masses (named parts the owner's feedback can point at):
//   Yard wall  rubble round the plot (x 0 and 12, z 0 and 12), coped, piers every 4; the chapel closes the back.
//   Lychgate   the roofed gate in the front wall over x 4..8, z 0..1: posts on stone piers, a slate gable to y 6, lanterns
//              hung under its eaves either side.
//   Plots      the two grave plots either side of the path, x 1..4 and x 8..11, z 1..6: the 32 grave slots (kept
//              exactly as the code's YARD_SLOTS; nothing drawn at y 0 or 1 in a slot).
//   Path       flagstone slabs up the middle, x 5..7, z 0..5, to the tower's arch.
//   Tower      the Lantern of the Dead, x 5..7, z 6..8, to y 15: an arch at its foot, the door, the lantern chamber.
//   Chapel     x 3..9, z 8..12: rubble walls to y 4, a steep slate roof (ridge along z, y 11) with a block of eave
//              both sides and a front overhang at z 7, a stone parapet gable at the back with a tall lancet.
//   Yews       Irish yews in the strips beside the chapel, x 0..1 and x 11..12, z 8..11.

export const SIZE = [13, 16, 13] // w, h, d: the brief's 13 x 13, at most 16 tall; both levels the same

// Palette (60/30/10): grey field rubble and dressed stone (walls, tower, wall) / dark slate roofs (deepslate tiles) /
// dark oak timber (lychgate, door, tie beam), candles, lanterns, the yews' dark green. Why: a light grey yard under
// dark roofs reads solemn and old without going black; the dark oak gate and door are the warm notes; the tower's
// dressed stone stands apart from the rubble so the eye goes up it to the light.
export const PALETTE = {
  rubble: [['cobblestone', 5], ['stone', 3], ['andesite', 2], ['mossy_cobblestone', 2]],
  dressed: [['stone_bricks', 7], ['mossy_stone_bricks', 2], ['cracked_stone_bricks', 1]],
  dressedFoot: [['stone_bricks', 4], ['mossy_stone_bricks', 4], ['cracked_stone_bricks', 1]], // the tower's damp foot
  pier: 'stone_bricks',
  coping: 'stone_brick_slab',
  copingStairs: 'stone_brick_stairs',
  keystone: 'chiseled_stone_bricks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  finial: 'deepslate_tile_wall',
  crossShaft: 'stone_brick_wall',
  timber: 'dark_oak_log',
  wood: 'dark_oak', // door, braces, pews, bier
  infill: 'spruce_planks', // the lychgate's gables
  floor: 'polished_andesite',
  path: [['stone_brick_slab', 3], ['cobblestone_slab', 2], ['andesite_slab', 2]],
  glass: 'glass_pane',
  yewLog: 'dark_oak_log',
  yewLeaf: 'spruce_leaves',
}

export const MASSES = {
  wall: {
    runs: [
      [[1, 0], [3, 0]], [[9, 0], [11, 0]], // the front, either side of the lychgate
      [[0, 1], [0, 3]], [[0, 5], [0, 7]], [[0, 9], [0, 11]], // west
      [[12, 1], [12, 3]], [[12, 5], [12, 7]], [[12, 9], [12, 11]], // east
      [[1, 12], [2, 12]], [[10, 12], [11, 12]], // the back, either side of the chapel
    ],
    piers: [[0, 0], [12, 0], [0, 4], [12, 4], [0, 8], [12, 8], [0, 12], [12, 12]],
    gatePiers: [[4, 0], [8, 0]],
  },
  lychgate: { x0: 4, x1: 8, z0: 0, z1: 1, postY0: 2, beamY: 3 }, // low: the tower's arch shows over it
  tower: { x0: 5, z0: 6, y0: 0, shaftTop: 10, open: ['north', 'west', 'east'] }, // the back face looks onto the ridge
  chapel: {
    walls: { x0: 3, x1: 9, z0: 8, z1: 12 }, room: { x0: 4, x1: 8, z0: 9, z1: 11 }, wallTop: 4,
    roof: { axis: 'z', across: [2, 10], along: [7, 11], eaveY: 4, steep: true }, // ridge y 11
    tieBeam: { z: 10, y: 4 },
    buttresses: [[2, 9], [10, 9], [2, 11], [10, 11]], // either side of each side lancet, under the eave
  },
  yews: [
    { x0: 0, x1: 1, z0: 8, z1: 11, tx: 1, tz: 9 },
    { x0: 11, x1: 12, z0: 8, z1: 11, tx: 11, tz: 9 },
  ],
}

// The grave slots: the code's Graveyards.YARD_SLOTS, unchanged (fill order). Nothing is drawn at y 0 or y 1 in them.
export const SLOTS = [
  ...[1, 2, 3, 4, 8, 9, 10, 11].map(x => [x, 0, 6]),
  ...[1, 2, 3, 4, 8, 9, 10, 11].map(x => [x, 0, 1]),
  ...[2, 3, 4, 5].flatMap(z => [1, 4, 8, 11].map(x => [x, 0, z])),
]

export const ROOMS = [
  { name: 'Chapel', use: 'the Undertaker\'s altar (his brewing stand), the bier, the mourners\' pews', box: [4, 1, 9, 8, 4, 11] },
]

export const OPENINGS = {
  arch: { at: [6, 1, 6], height: 2 }, // the tower's open arch, a drip hood over it
  door: { at: [6, 1, 7], facing: 'south', out: 'north' }, // inside the arch: the tower's door into the chapel
  chapelArch: { at: [6, 1, 8], height: 2 },
  windows: [
    { name: 'chapel west', cells: [[3, 2, 10], [3, 3, 10]], out: 'west' },
    { name: 'chapel east', cells: [[9, 2, 10], [9, 3, 10]], out: 'east' },
    { name: 'altar lancet', cells: [[6, 2, 12], [6, 3, 12], [6, 4, 12], [6, 5, 12], [6, 6, 12], [6, 7, 12]], out: 'south' },
  ],
}

// Every piece of furniture, footprint first. The Undertaker's job block is the altar at the back of the chapel, facing
// the door: a brewing stand, his workstation since the Undertaker's Table stopped being craftable (the Table is kept only
// for old worlds, so a drawing that needs one could never be stocked in survival); the chest he takes golden apples, potions and totems from stands beside it (2 blocks, within the 8 his search
// needs). Candles on dressed stone pedestals flank the altar (the chandler scene needs plain candles in Level I).
export const FURNITURE = [
  { room: 'Chapel', kind: 'fixture', part: 'jobBlock', pos: [6, 1, 11], block: 'brewing_stand', props: { has_bottle_0: 'false', has_bottle_1: 'false', has_bottle_2: 'false' } },
  { room: 'Chapel', kind: 'fixture', part: 'chest', pos: [8, 1, 11], facing: 'north', block: 'chest' },
  { room: 'Chapel', kind: 'fixture', part: 'pedestal', pos: [5, 1, 11], candles: 3 },
  { room: 'Chapel', kind: 'fixture', part: 'pedestal', pos: [7, 1, 11], candles: 3 },
  { room: 'Chapel', kind: 'fixture', part: 'bier', pos: [6, 1, 10] }, // where the dead lie in state, under the lamp
  { room: 'Chapel', kind: 'cupboard', part: 'cupboard', pos: [4, 1, 11], facing: 'north', wood: 'dark_oak' }, // shrouds
  { room: 'Chapel', kind: 'bench', part: 'pews', cells: [[4, 1, 9], [4, 1, 10]], facing: 'east', wood: 'dark_oak' },
  { room: 'Chapel', kind: 'bench', part: 'pews', cells: [[8, 1, 9], [8, 1, 10]], facing: 'west', wood: 'dark_oak' },
  { room: 'Chapel', kind: 'lamp', part: 'hangingLamp', pos: [6, 3, 10], facing: 'north', wood: 'dark_oak' },
]

// The walking line: the street, under the lychgate, up the path, through the arch and the door, to the altar.
// From the path every slot is within 2 of a cell a villager stands on: the plots' inner cells (x 2..3, z 2..5) and
// the path's edge (x 5 and 7), and the cross walk at z 7 behind the back row.
export const WALK = [
  [6, 0, -1], [6, 0, 0], [6, 0, 1], [6, 0, 2], [6, 0, 3], [6, 0, 4], [6, 0, 5], [6, 1, 6], [6, 1, 7], [6, 1, 8],
  [6, 1, 9], [7, 1, 10], // the brewing stand (6, 1, 11) and the chest (8, 1, 11) are within reach of these
]

// Level II's additions (nothing at y 0..1 on a slot).
export const LEVEL2 = {
  pierLamps: [[0, 0], [12, 0], [0, 4], [12, 4]], // a lantern on each front pier (the z 8 piers stand in the yews)
  pierPots: [[0, 12], [12, 12]],
  railings: [[[1, 0], [3, 0]], [[9, 0], [11, 0]]], // the front wall raised a course and railed with iron between its piers
  benches: [{ x0: 1, x1: 3, z: 7 }, { x0: 9, x1: 11, z: 7 }], // facing the graves (north), on the cross walk
  crosses: [{ x: 2, z: 3, shaftTop: 3 }, { x: 10, z: 3, shaftTop: 3 }], // in each plot's inner walk (not a slot), arms high over the graves
  pathLamps: [[5, 0, 3], [7, 0, 3]], // lamp posts at the path's edge, half way up
}
