// The Pump House, the plan (ROADMAP 49/50.13, brief tools/blueprints/briefs/pump_house.md): drawn before any wall.
// Rooms, every furniture footprint, the machine's cells and the walking line come first; recipe.mjs assembles it from
// the parts library (../parts) in passes.
//
// Three rough concepts:
//   A. "The Engine Shed": one long, low stone shed (13 x 7), a 45-degree slate roof with a ridge vent, the wheel in
//      an open race along one long side, pool, pump and tank in a row inside, the Leatherworker's bench at one end.
//      Story: a works building put up by the village. Safe, but it reads as a barn from 20 blocks: one box, one roof.
//   B. "The Tanner's Water Tower": a slim stone water tower (5 x 5, 18 tall) under a steep copper cap, a corbelled
//      gallery round its top where the tank stands in the open, a waterfall spouting from its flank onto the wheel,
//      the pump in an arch at its foot dipping into a cistern; a low timber-framed tannery (the Leatherworker's
//      workroom) tucked behind it under a tiled gable, a path joining his door to the tower's. Story: tanning drinks
//      water, so the tanner raised a tower to keep a tank full above his vats.
//   C. "The Sluice House": a building astride a sunken stone channel, the wheel in the channel under an arch through
//      the house, a hipped roof, the tank in a dormer. Story: a mill-race house. Strong idea, but the channel runs
//      out of the plot (open water at the edges) and the tank hides in the roof.
// B is the strongest: a water tower is a shape anyone names in outline, it is 2x the height of its wing (one hero
// mass), and it puts all three Create pieces where the street sees them (the wheel and its waterfall on the tower's
// face, the pump through the arch, the tank in the open belvedere at the top), while the tannery gives the
// Leatherworker a real room. It is the riskiest (height, a 7-block fall), so the plan checks every cell of the water.
//
// Coordinates: x across the front (west 0 -> east 17), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back.
//
// Masses (named parts):
//   Tower     the hero: stone shaft x 11..15, z 1..5, y 0..6 (inside x 12..14, z 2..4); a door in its front at x 14
//             onto the one dry floor cell beside the cistern, a ladder from there up the east wall to a hatch in the
//             gallery deck (y 7, x 10..16, z 0..6, on corbels; where it meets the tannery it rests on its roof); the
//             belvedere (corner posts y 8..9, glazed between them, a fence railing round the deck) with the tank in
//             its middle; a steep hollow copper cap (y 10..16) with a lightning rod for a finial. Windows at the
//             pump's height look in from the street and from the tannery's door.
//   Race      the waterfall on the tower's east flank: a poured source in a stone cup at (16, 5, 3), falling down the
//             tower's face past the wheel (16, 2, 4) into a closed stone trough at (16, 1, 3).
//   Pump room the tower's foot: a 2 x 2 cistern of four poured sources (it refills itself), the pump over it, the
//             riser pipe up the inside of the shaft to the tank.
//   Tannery   the Leatherworker's workroom, set back 4 behind the tower's face and leaning on its west flank (its
//             east wall carries on the tower's west wall line): mud-brick walls x 3..11, z 5..11 between spruce posts,
//             a beam ring at y 4, a 45-degree deepslate-tiled gable roof (ridge along z at x 7, y 9) overhanging a
//             block all round, its gable to the street over a hooded door. One storey: the tower is twice its height.
//   Yard      a path from the street to the tannery's door, branching along the frontage to the tower's door: the
//             Leatherworker's walk between his bench and his machine.

export const SIZE = [18, 17, 13] // w, h, d (limit 20 x 18 x 20)

// Palette (60/30/10): masonry, stone bricks and mud brick (60: the tower and the tannery's panels) / spruce frame and a
// deepslate-tile roof (30) / weathered copper cap and Create's brass and copper, with tanned-leather browns on the
// hides (10). Why: the cool grey tower and green copper cap stand apart from the warm brown tannery; the mossy stone
// gathers only where the water runs (the trough, the tower's foot, the cistern).
export const PALETTE = {
  stone: [['stone_bricks', 7], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1]],
  wetStone: [['mossy_stone_bricks', 3], ['stone_bricks', 2], ['mossy_cobblestone', 1]],
  plinth: [['cobblestone', 4], ['mossy_cobblestone', 2], ['stone', 1]],
  dressed: 'chiseled_stone_bricks', // keystone, quoin caps
  stoneStairs: 'stone_brick_stairs',
  stoneSlab: 'stone_brick_slab',
  frame: 'spruce_log',
  beam: 'stripped_spruce_log',
  infill: 'mud_bricks',
  floor: 'spruce_planks',
  deck: 'spruce_planks',
  wood: 'spruce',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  cap: { full: 'waxed_weathered_cut_copper', stairs: 'waxed_weathered_cut_copper_stairs', slab: 'waxed_weathered_cut_copper_slab' },
  glass: 'glass_pane',
  grille: 'iron_bars', // across the pump room's arch
}

export const MASSES = {
  tower: { x0: 11, x1: 15, z0: 1, z1: 5, top: 6, inside: { x0: 12, x1: 14, z0: 2, z1: 4 },
    gallery: { x0: 10, x1: 16, z0: 0, z1: 6, y: 7 }, beltTop: 10, cap: { cx: 13, cz: 3, r: 3, y0: 10 },
    door: { at: [14, 1, 1], step: [14, 0, 0] }, // the way in: onto the one dry floor cell (14, 1, 2), the ladder's foot
    galleryDoor: [13, 8, 1], // the critic's polish (50.13 ship): from the belvedere out onto the gallery, to the street
    // B1405: the rail panel in front of that door is an open trapdoor flat against the deck's outer edge, not a fence,
    // so the door opens onto a railed landing (a fence there put a villager's feet 1.5 up, under the belvedere's head)
    galleryLanding: [13, 8, 0],
    ladder: { x: 14, z: 2, y0: 1, top: 7, facing: 'west' }, // up the east wall to a hatch in the gallery deck
    // corbels left out: over the cup's source, and where the gallery rests on the tannery's roof
    corbelSkip: [[16, 6, 3], [10, 6, 4], [10, 6, 5], [10, 6, 6]] },
  tannery: { posts: { x0: 3, x1: 11, z0: 5, z1: 11 }, panels: { x0: 3, x1: 11, z0: 5, z1: 11 },
    room: { x0: 4, x1: 10, z0: 6, z1: 10 }, wallTop: 3, beamY: 4,
    frontPosts: [3, 6, 8], backPosts: [3, 6, 8, 11], westPosts: [5, 8, 11], eastPosts: [8, 11],
    roof: { axis: 'z', across: [2, 12], along: [4, 12], eaveY: 4, steep: false }, tieBeamZ: 8 },
}

// The machine, cell by cell (template coordinates; the ship step pins these in the tests).
export const MACHINE = {
  pool: [[12, 1, 3], [13, 1, 3], [12, 1, 4], [13, 1, 4]], // four poured sources, a 2 x 2 that refills itself
  curb: [[12, 1, 2], [13, 1, 2], [14, 1, 3], [14, 1, 4]], // (14, 1, 2) is left as floor: the walked spot by the pool
  intake: [[12, 2, 3]], // open end down into the pool, east into the pump
  pump: { at: [13, 2, 3], facing: 'east' }, // pushes east, its axle along x
  // up the shaft, into the tank's foot. It steps west at y 4 (50.13 ship): a dry waterloggable block (a pipe) two cells
  // beside the cup's source at its own height makes the builder pour that source quietly (BuilderWork.keepsDrawnDry,
  // B1377), and quiet water never falls, so the wheel stood still
  outlet: [[14, 2, 3], [14, 3, 3], [14, 4, 3], [13, 4, 3], [13, 5, 3], [13, 6, 3], [13, 7, 3]],
  tank: [13, 8, 3], // and [13, 9, 3]
  cog: { at: [13, 2, 4], axis: 'x' }, // meshes with the pump beside it (both on axis x)
  shafts: [[14, 2, 4], [15, 2, 4]], // through the east wall
  wheel: { at: [16, 2, 4], facing: 'east' },
  // the race: the source in its cup, the fall down the tower's face (beside the wheel at y 2) into the trough
  race: { col: [16, 3], sumpY: 1, topY: 5, cup: [[16, 2], [17, 3], [16, 4]], sumpSides: [[16, 2], [17, 3], [16, 4]] },
}

export const ROOMS = [
  { name: 'Workroom', use: "the Leatherworker's tannery: the soaking vat (his cauldron) on the back wall between his two chests, a cutting table under the west window, a stitching desk under the east window, hides on the walls", box: [4, 1, 6, 10, 3, 10] },
  { name: 'Pump room', use: 'the cistern, the pump and its cog, seen through the arch (no floor to walk: the cistern fills it)', box: [12, 1, 2, 14, 7, 4] },
  { name: 'Belvedere', use: 'the tank in the open at the top of the tower, under the cap; a door out onto the gallery', box: [12, 9, 2, 14, 10, 4] },
]

export const OPENINGS = {
  frontDoor: { at: [7, 1, 5], facing: 'south', step: [7, 0, 4] },
  windows: [
    { name: 'front west', cells: [[4, 2, 5], [5, 2, 5], [4, 3, 5], [5, 3, 5]], out: 'north', dress: 'sill' },
    { name: 'front east', cells: [[9, 2, 5], [10, 2, 5], [9, 3, 5], [10, 3, 5]], out: 'north', dress: 'sill' },
    { name: 'west', cells: [[3, 2, 6], [3, 2, 7], [3, 3, 6], [3, 3, 7]], out: 'west', dress: 'sill' },
    { name: 'back', cells: [[7, 2, 11], [7, 3, 11]], out: 'south', dress: 'sill' },
    { name: 'east', cells: [[11, 2, 6], [11, 2, 7], [11, 3, 6], [11, 3, 7]], out: 'east', dress: 'sill' },
    { name: 'gable', cells: [[7, 7, 5]], out: 'north', dress: null },
    { name: 'back gable', cells: [[7, 6, 11]], out: 'south', dress: 'sill' },
    { name: 'pump room front', cells: [[12, 2, 1], [13, 2, 1]], out: 'north', dress: null }, // the pump, seen from the street
    { name: 'pump room west', cells: [[11, 2, 3], [11, 3, 3]], out: 'west', dress: null }, // the pump, seen from the tannery's door
    { name: 'tower front', cells: [[13, 4, 1], [13, 5, 1]], out: 'north', dress: null },
    { name: 'tower back', cells: [[13, 4, 5]], out: 'south', dress: null },
  ],
}

// Every piece of furniture, footprint first (kinds as furniture.py has them; fixtures are vanilla blocks used as
// themselves). The cauldron is the Leatherworker's job site and the only job block; the chests are his makings and
// his deliveries (within 8 across and 4 up of the cauldron).
export const FURNITURE = [
  { room: 'Workroom', kind: 'fixture', part: 'vat', pos: [7, 1, 10], facing: 'north', block: 'cauldron' },
  { room: 'Workroom', kind: 'fixture', part: 'chest', pos: [5, 1, 10], facing: 'north', block: 'chest' },
  { room: 'Workroom', kind: 'fixture', part: 'chest', pos: [9, 1, 10], facing: 'north', block: 'chest' },
  // the cutting table under the west window, two stools at it
  { room: 'Workroom', kind: 'table', part: 'table', pos: [4, 1, 6], facing: 'east', wood: 'spruce', colour: 'brown' },
  { room: 'Workroom', kind: 'table', part: 'table', pos: [4, 1, 7], facing: 'east', wood: 'spruce', colour: 'brown' },
  { room: 'Workroom', kind: 'chair', part: 'chair', pos: [5, 1, 6], facing: 'west', wood: 'spruce' },
  { room: 'Workroom', kind: 'chair', part: 'chair', pos: [5, 1, 7], facing: 'west', wood: 'spruce' },
  // the stitching desk under the east window and its chair
  { room: 'Workroom', kind: 'desk', part: 'desk', pos: [10, 1, 7], facing: 'east', wood: 'spruce' },
  { room: 'Workroom', kind: 'chair', part: 'chair', pos: [9, 1, 7], facing: 'east', wood: 'spruce' },
  // hides pinned to the walls (wall banners) and a hide rug before the vat
  { room: 'Workroom', kind: 'picture', part: 'picture', pos: [10, 2, 9], facing: 'west', wood: 'spruce', colour: 'brown' },
  { room: 'Workroom', kind: 'picture', part: 'picture', pos: [4, 2, 9], facing: 'east', wood: 'spruce', colour: 'white' },
  { room: 'Workroom', kind: 'rug', part: 'rug', cellsAbs: [[6, 1, 9], [7, 1, 9], [8, 1, 9]], colour: 'brown' },
  // lamps hung from the tie beam across the room
  { room: 'Workroom', kind: 'lamp', part: 'hangingLamp', pos: [5, 3, 8], facing: 'north', wood: 'spruce' },
  { room: 'Workroom', kind: 'lamp', part: 'hangingLamp', pos: [9, 3, 8], facing: 'north', wood: 'spruce' },
  // the critic's polish (50.13 ship): a lamp hung under the gallery deck in the pump room, clear of the ladder
  { room: 'Pump room', kind: 'lamp', part: 'hangingLamp', pos: [12, 6, 2], facing: 'north', wood: 'spruce' },
]

// The walking line: the street -> the step -> the door -> down the room to the vat; the chests from (5, 1, 9) and
// (9, 1, 9), the cauldron from (7, 1, 9).
// and the branch to the tower: (8..13, 0, 0) -> step (14, 0, 0) -> door (14, 1, 1) -> (14, 1, 2), the ladder to the gallery
export const WALK = [[7, 0, 1], [7, 0, 2], [7, 0, 3], [7, 0, 4], [7, 1, 5], [7, 1, 6], [7, 1, 7], [7, 1, 8], [7, 1, 9], [6, 1, 9], [5, 1, 9], [8, 1, 9], [9, 1, 9]]

export const OUTSIDE = {
  // the yard path: from the street (z = 0) to the tannery's step, and along the frontage to the tower's step
  path: [[7, 0, 0], [7, 0, 1], [7, 0, 2], [7, 0, 3], [8, 0, 0], [9, 0, 0], [10, 0, 0], [11, 0, 0], [12, 0, 0], [13, 0, 0]],
  lamps: [[10, 6, 0], [16, 6, 0], [16, 6, 6]], // hung under the gallery's open corners
}
