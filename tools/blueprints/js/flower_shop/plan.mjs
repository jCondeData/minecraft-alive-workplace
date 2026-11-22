// The Flower Shop (flower_shop, flower_shop_2; ROADMAP 49), the plan: drawn before any wall. Rooms, every furniture
// footprint (kind, cells, facing) and the walking line come first; recipe.mjs then assembles both levels from the parts
// library (../parts) to fit it. Requirement: tools/blueprints/briefs/flower_shop.md (designed blind, REBUILD.md).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. "The Corner Kiosk": a timber shop with its eaves to the street under a striped awning, flower buckets and stalls
//      spilling onto the pavement, a garden plot behind; II adds a room upstairs. Story: a market florist. Safe, and it
//      is every market stall in the library with a roof on: nothing in the outline says "grown here".
//   B. "The Walled Cutting Garden": a tall brick garden wall round her beds, the shop a tiny gate lodge set in the wall;
//      II adds a second walled court. Story: the old kitchen garden of a big house. Strong story, but from the street it
//      is a wall: a low, flat silhouette and the flowers hidden.
//   C. "The Glasshouse": the shop IS a glasshouse. A tall glazed gable to the street on a brick dwarf wall, glazing bars
//      of green copper, her raised beds under the glass either side of one aisle that runs from the door to the composter;
//      a solid brick back wall with the boiler's chimney (the stove that keeps the glass warm), a small brick parlour
//      (the shop counter) tucked against its front corner, and a fenced cutting yard behind the parlour. II adds a second,
//      lower glass range across the yard, closing it into a courtyard of beds, and a vented lantern on the big ridge.
//      Story: a nurserywoman who sells what she grows; you walk into her greenhouse to buy.
// Strongest: C. From the street it reads in one look as "plants are grown here" (the glass gable lit from inside, the
// copper bars, the tall flowers showing through), which neither a kiosk nor a wall can say; the garden the code gives
// her is the building itself rather than a lawn beside it; the chimney and parlour give it a second and third mass of a
// different material, so it is never one box; and Level II is one clear addition growing out of Level I's yard.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Doors on the north side with a step at y = 0 before the main one.
//
// Masses (named parts the owner's feedback can point at):
//   Glasshouse  the hero: dwarf brick wall x 1..7, z 1..10, dark oak posts, glass panes y 2..3, a log ring at y 4, a
//               45-degree glazed roof (ridge along z, eaves x 0 and 8, ridge y 8) with copper glazing bars over every
//               post line; the street gable glazed round a king post, the back gable solid brick.
//   Boiler      the stove in the back wall (a furnace facing in) and its chimney stack up the back gable, out past the
//               ridge, a breast stepped either side.
//   Parlour     the shop room: brick, x 7..11, z 1..5 (sharing the glasshouse's east wall), its own small gable to the
//               street beside the glass one, a copper roof (ridge y 6, well under the glass), a shop window with a sill of pots.
//   Yard        the cutting yard behind the parlour: grass x 8..10, z 6..11, a fence on a stone kerb, a gate east.
//   Range (II)  a lower glass range x 11..15, z 7..13 across the yard's east side (ridge y 7), the yard's fence replaced
//               by its glass wall with a door where the gate was; rows of tulips and alliums under the glass.
//   Lantern (II) a glazed vent lantern on the glasshouse ridge at z 5, its centre open to the house below.

export const SIZE = { 1: [13, 12, 13], 2: [17, 12, 15] } // w, h, d (Level I limit 13 x 16 x 13)

// Palette (60/30/10): brick (dwarf walls, parlour, back wall) and glass / waxed verdigris copper (roofs, glazing bars) /
// dark oak (posts, ring beam, joinery) with stone-brick footings and spruce floors. Why: the red brick and the
// blue-green copper are opposites, so the roof never merges with the walls; the glass is the 60% you see through to the
// flowers; dark oak draws the bays; copper is waxed so it never weathers after the builder is done.
export const PALETTE = {
  stone: [['stone_bricks', 6], ['mossy_stone_bricks', 2], ['cracked_stone_bricks', 1]],
  stoneStairs: 'stone_brick_stairs',
  brick: 'bricks',
  glassBlock: 'glass', // the roof's glazing (full blocks stepping up the slope)
  glass: 'glass_pane', // every wall
  frame: 'dark_oak_log',
  wood: 'dark_oak', // doors, sills, shutters, fences, staging
  floorWood: 'spruce_planks',
  floor: [['stone_bricks', 5], ['polished_andesite', 2]], // the glasshouse aisle: worn tiles
  roof: { full: 'waxed_oxidized_cut_copper', stairs: 'waxed_oxidized_cut_copper_stairs', slab: 'waxed_oxidized_cut_copper_slab' },
  soil: 'grass_block',
  under: 'dirt',
}

export const MASSES = {
  glasshouse: {
    walls: { x0: 1, x1: 7, z0: 1, z1: 10 },
    inside: { x0: 2, x1: 6, z0: 2, z1: 9 },
    sidePosts: [1, 4, 7, 10], // on x 1 and x 7
    frontPosts: [1, 3, 5, 7], // on z 1 (the door between 3 and 5)
    beamY: 4,
    roof: { axis: 'z', across: [0, 8], along: [1, 10], eaveY: 4 }, // no overhang at the gables: glass right to the end frames
    ribs: [4, 7], // copper glazing bars over the side posts, the hung pots' chains from them
  },
  boiler: { stove: [4, 1, 10], stack: [4, 11], top: 10 },
  parlour: {
    walls: { x0: 7, x1: 11, z0: 1, z1: 5 },
    inside: { x0: 8, x1: 10, z0: 2, z1: 4 },
    roof: { axis: 'z', across: [7, 11], along: [0, 6], eaveY: 4 }, // a small brick gable to the street beside the glass one, its west slope tucked under the glass
  },
  yard: { x0: 8, x1: 10, z0: 6, z1: 11, fence: [[11, 6], [11, 12], [7, 12], [7, 11]], gate: [11, 1, 8] },
  range: { // Level II
    walls: { x0: 11, x1: 15, z0: 7, z1: 13 },
    inside: { x0: 12, x1: 14, z0: 8, z1: 12 },
    sidePosts: [7, 10, 13],
    roof: { axis: 'z', across: [10, 16], along: [7, 13], eaveY: 4 },
    ribs: [10],
  },
  lantern: { cx: 4, cz: 5, y0: 8 }, // Level II
}

export const ROOMS = [
  { name: 'Glasshouse', use: 'her beds under glass, the shop at the door end, the composter and potting bench at the back', box: [2, 1, 2, 6, 3, 9] },
  { name: 'Parlour', use: 'the counter where flowers are sold, her chest', box: [8, 1, 2, 10, 3, 4] },
  { name: 'Range', use: 'Level II: cut-flower beds under a second roof of glass', box: [12, 1, 8, 14, 3, 12], level: 2 },
]

export const OPENINGS = {
  frontDoor: { at: [4, 1, 1], facing: 'south', step: [4, 0, 0] }, // the door's lower half, opening into the glasshouse
  transom: [4, 3, 1],
  parlourDoorway: { at: [7, 1, 3], height: 2 },
  yardDoor: { at: [7, 1, 8], facing: 'west' },
  rangeDoors: { yard: { at: [11, 1, 8], facing: 'east' }, front: { at: [13, 1, 7], facing: 'south' } }, // Level II
  windows: [
    { name: 'shop window', cells: [[8, 2, 1], [9, 2, 1], [10, 2, 1], [8, 3, 1], [9, 3, 1], [10, 3, 1]], out: 'north', dress: 'sill' },
    { name: 'parlour east', cells: [[11, 2, 3], [11, 3, 3]], out: 'east', dress: 'shutters' },
    { name: 'parlour back', cells: [[9, 2, 5], [9, 3, 5]], out: 'south', dress: 'shutters' },
  ],
}

// Her garden (FloristWork: grass with air above within 5 across and 2 up or down of the composter at (5, 1, 9)): the
// two raised beds under the glass and the yard's grass. Only tall flowers are planted in it (she bone-meals them and
// never picks them); the rest is bare grass for her to bring flowers up on. Small flowers stand only in pots, and in
// Level II's range, which is outside her garden's reach.
export const BEDS = {
  west: { cells: [[2, 4], [3, 4], [2, 5], [3, 5], [2, 6], [3, 6], [2, 7], [3, 7], [2, 8], [3, 8]], plants: [[2, 5, 'peony'], [3, 7, 'rose_bush'], [2, 8, 'lilac']] },
  east: { cells: [[5, 4], [6, 4], [5, 5], [6, 5], [5, 6], [6, 6], [5, 7], [6, 7]], plants: [[6, 4, 'lilac'], [5, 6, 'peony'], [6, 7, 'rose_bush']] },
  yardPlants: [[8, 7, 'peony'], [10, 7, 'rose_bush'], [10, 10, 'lilac'], [9, 11, 'sunflower'], [8, 10, 'rose_bush']],
  // Level II's range: two beds of small flowers either side of its aisle, no reach of hers
  range: {
    cells: [[12, 9], [12, 10], [12, 11], [12, 12], [14, 8], [14, 9], [14, 10], [14, 11], [14, 12]],
    plants: [[12, 9, 'red_tulip'], [12, 10, 'allium'], [12, 11, 'pink_tulip'], [12, 12, 'white_tulip'],
      [14, 8, 'allium'], [14, 9, 'pink_tulip'], [14, 10, 'red_tulip'], [14, 11, 'cornflower'], [14, 12, 'orange_tulip']],
  },
}

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a furniture spot) or a fixture (a
// vanilla block the game uses as itself: the composter, chests, the stove, pots on staging). cells: relative to pos.
export const FURNITURE = [
  // Glasshouse, the shop end: staging of potted flowers down both side walls, decorated pots on the floor
  { room: 'Glasshouse', kind: 'fixture', part: 'staging', cellsAbs: [[2, 1, 2], [2, 1, 3]], wood: 'dark_oak', pots: ['potted_red_tulip', 'potted_oxeye_daisy'] },
  // (east: one cell only, so the parlour doorway at (7, 1, 3) stays open from the aisle; B1454)
  { room: 'Glasshouse', kind: 'fixture', part: 'staging', cellsAbs: [[6, 1, 2]], wood: 'dark_oak', pots: ['potted_blue_orchid'] },
  // the working end: her composter at the aisle's end, a chest beside it, the potting bench of empty pots she fills
  { room: 'Glasshouse', kind: 'fixture', part: 'composter', pos: [5, 1, 9] },
  { room: 'Glasshouse', kind: 'fixture', part: 'chest', pos: [6, 1, 9], facing: 'west', block: 'chest' },
  { room: 'Glasshouse', kind: 'counter', part: 'counter', pos: [2, 1, 9], facing: 'north', wood: 'spruce', base: 'spruce_planks', top: 'spruce_slab', topSlab: true },
  { room: 'Glasshouse', kind: 'counter', part: 'counter', pos: [3, 1, 9], facing: 'north', wood: 'spruce', base: 'spruce_planks', top: 'spruce_slab', topSlab: true },
  { room: 'Glasshouse', kind: 'fixture', part: 'stove', pos: [4, 1, 10], facing: 'north', block: 'furnace' },
  // light: two lanterns on chains down the aisle, four hanging baskets on the glazing bars over the beds
  { room: 'Glasshouse', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 3], facing: 'north', chain: 3, wood: 'dark_oak' },
  { room: 'Glasshouse', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 7], facing: 'north', chain: 3, wood: 'dark_oak' },
  { room: 'Glasshouse', kind: 'hanging_pot', part: 'hangingPot', pos: [2, 5, 4], plant: 'potted_fern', wood: 'dark_oak' },
  { room: 'Glasshouse', kind: 'hanging_pot', part: 'hangingPot', pos: [6, 5, 4], plant: 'potted_flowering_azalea_bush', wood: 'dark_oak' },
  { room: 'Glasshouse', kind: 'hanging_pot', part: 'hangingPot', pos: [2, 5, 7], plant: 'potted_azalea_bush', wood: 'dark_oak' },
  { room: 'Glasshouse', kind: 'hanging_pot', part: 'hangingPot', pos: [6, 5, 7], plant: 'potted_fern', wood: 'dark_oak' },
  // Parlour: the counter against the back wall (she stands at it facing the shop), a chest in the corner, a lamp
  { room: 'Parlour', kind: 'counter', part: 'counter', pos: [9, 1, 4], facing: 'north', wood: 'dark_oak', base: 'dark_oak_planks', top: 'polished_andesite_slab', topSlab: true },
  { room: 'Parlour', kind: 'counter', part: 'counter', pos: [10, 1, 4], facing: 'north', wood: 'dark_oak', base: 'dark_oak_planks', top: 'polished_andesite_slab', topSlab: true },
  { room: 'Parlour', kind: 'fixture', part: 'pot', pos: [10, 3, 4], block: 'potted_cornflower' },
  { room: 'Parlour', kind: 'fixture', part: 'chest', pos: [10, 1, 2], facing: 'west', block: 'chest' },
  { room: 'Parlour', kind: 'fixture', part: 'decoratedPot', pos: [8, 1, 2] },
  { room: 'Parlour', kind: 'fixture', part: 'decoratedPot', pos: [8, 1, 4] },
  { room: 'Parlour', kind: 'lamp', part: 'hangingLamp', pos: [9, 3, 3], facing: 'north', chain: 2, wood: 'dark_oak' },
  // Range (II): a lantern down its aisle, staging at its back gable
  { room: 'Range', level: 2, kind: 'lamp', part: 'hangingLamp', pos: [13, 4, 10], facing: 'north', chain: 2, wood: 'dark_oak' },
  { room: 'Range', level: 2, kind: 'fixture', part: 'staging', cellsAbs: [[13, 1, 12]], wood: 'dark_oak', pots: ['potted_lily_of_the_valley'] },
]

// The walking line: front step -> door -> aisle to the composter; the parlour off the shop end; the yard door.
export const WALK = [
  [4, 1, 0], [4, 1, 1], [4, 1, 2], [4, 1, 3], [4, 1, 4], [4, 1, 5], [4, 1, 6], [4, 1, 7], [4, 1, 8], [4, 1, 9], // composter (5,1,9), chest (6,1,9)
  [5, 1, 3], [6, 1, 3], [7, 1, 3], [8, 1, 3], [9, 1, 3], // parlour: chest (10,1,2), counter (9..10,1,4)
  [5, 1, 8], [6, 1, 8], [7, 1, 8], [8, 1, 8], [9, 1, 8], [10, 1, 8], [11, 1, 8], // the yard and its gate (II: the range)
]

// Outside the rooms: the front apron with lamp posts and pots either side of the door; the yard's kerb and fence.
export const OUTSIDE = {
  apron: { x0: 0, x1: 11, z: 0 },
  lampPosts: [[2, 1, 0], [6, 1, 0]],
  frontPots: [[1, 1, 0, 'decorated'], [3, 1, 0, 'potted_poppy'], [5, 1, 0, 'potted_dandelion'], [7, 1, 0, 'decorated']],
  dippingTank: [8, 0, 11],
}
