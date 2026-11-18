// Camp Kitchen (camp_kitchen, camp_kitchen_2), the plan: drawn before any wall (ROADMAP 49, tools/blueprints/REBUILD.md,
// brief tools/blueprints/briefs/camp_kitchen.md). recipe.mjs assembles it from the parts library (../parts) in passes.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Louvre Cookhouse. One square timber hall (9 x 9) under a tall bell-cast pyramid roof that rises to a copper
//      smoke louvre right over the pot, which stands alone in the middle of the floor like the fire of an old open-hall
//      kitchen; a lean-to of grain bales on its west flank. Level II: a narrow, tall stone smokehouse with a steep gable
//      and its own chimney beside it, and a raised, watered grain bed behind. Story: a drovers' cook settled the camp
//      fire under a roof, and the roof was shaped by the smoke: it climbs to the one place the smoke goes out.
//   B. The Chuckwagon Camp. A covered wagon (wool hoops) parked by an open fire ring under a canvas awning on poles,
//      bales for seats, chests in the wagon bed. Level II: a smoke tent and a fenced grain patch. Story: the cook never
//      stopped travelling. Strong story, weak building: it reads as props on a lawn from 20 blocks, it has no room,
//      and its open awning lets the outside into every bay.
//   C. The Long Kitchen. A long gabled kitchen barn with a stone chimney gable, the pot at a hearth against the back
//      wall, a porch along the front. Level II: a wing and a garden. Story: a farmhouse kitchen. Safe and generic: a
//      house with a pot in it; nothing says "camp" or "this is where the Camp Cook works".
// Strongest: A. Its outline is its job: one hero roof whose peak sits over the pot, so a player sees from across the
// valley where the cooking happens. The pot in the middle of the room (not against a wall) gives the cook a ring to
// walk round and lets diners sit facing the fire. Level II adds a second, contrasting silhouette (the tall, narrow
// smokehouse with a chimney: stone against timber, a steep gable against a pyramid) and a green field behind, so the
// camp grows into a small working yard instead of a bigger box.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Level I sits in Level II's low-x, low-z corner; Level II adds to the east and south.
//
// Masses (named parts the owner's feedback can point at):
//   Cookhouse    the hall: frame posts x 2..10, z 2..10 on a stone terrace (x 1..11, z 1..11); walls y 1..4, beam ring
//                y 5; a square bell-cast pyramid roof (spruce kick at the eaves, deepslate tiles above, steepening to
//                the top) from y 5 to y 11, crowned by the copper Louvre (y 11..14) right over the pot.
//   Bale Lean-to the hearty grain store on the west flank (x 0..1, z 4..8): bales stacked under a pent roof.
//   Smokehouse   (Level II) a stone tower x 14..18, z 4..8, walls y 1..4 and a steep deepslate gable (ridge along z,
//                y 10), the chimney up its back gable (x 16, z 9), the smoker inside against the back wall.
//   Grain Bed    (Level II) a raised stone bed x 1..11, z 13..20 behind the cookhouse: farmland at y 1 round a water
//                channel down its middle (x 6), hearty grains on every farmland cell.

export const LEVELS = {
  1: { size: [13, 15, 12] },
  2: { size: [20, 15, 21] },
}

// Palette (60/30/10): pale birch boards between dark spruce frames over a sooty rubble course (walls) / dark deepslate
// tiles and the spruce frame and terrace (roof, structure) / waxed copper on the louvre and the red pot (accent).
// Why: the dark tile pyramid reads strongly against the pale walls; the copper cap is the one bright thing on the
// skyline and marks where the fire is; stone low and soot-coloured where the fire and feet are.
export const PALETTE = {
  stone: [['cobblestone', 6], ['stone', 2], ['andesite', 2], ['mossy_cobblestone', 1]],
  soot: [['cobblestone', 4], ['cobbled_deepslate', 2], ['stone', 1]], // the low course and the smokehouse walls
  floor: [['stone_bricks', 5], ['cracked_stone_bricks', 1], ['polished_andesite', 2]],
  hearth: 'bricks',
  frame: 'spruce_log',
  infill: 'birch_planks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab', kick: 'spruce_stairs' },
  lining: 'spruce_planks',
  copper: { full: 'waxed_cut_copper', stairs: 'waxed_cut_copper_stairs', finial: 'lightning_rod' },
  wood: 'spruce', // doors, shutters, fences, chairs, counters
  glass: 'glass_pane',
  bale: 'cobblemon:hearty_grain_bale',
  grain: 'cobblemon:hearty_grains',
}

export const MASSES = {
  terrace: { x0: 1, x1: 11, z0: 1, z1: 11 },
  cookhouse: {
    posts: { x0: 2, x1: 10, z0: 2, z1: 10 },
    room: { x0: 3, x1: 9, z0: 3, z1: 9 },
    centre: [6, 6], wallTop: 4, beamY: 5,
    // frame posts along each wall: corners and either side of the 3-wide middle bay (door or window)
    frontPosts: [2, 4, 8, 10], backPosts: [2, 4, 8, 10], westPosts: [2, 4, 8, 10], eastPosts: [2, 4, 8, 10],
    // the pyramid: ring r (r = max(|dx|, |dz|) from the centre) at heights[r]; ring 5 is the overhang, ring 4 the wall line
    roof: { cx: 6, cz: 6, cut: 10, heights: [12, 11, 10, 8, 6, 5] },
    louvreY: 11,
  },
  leanTo: { x0: 0, x1: 1, z0: 4, z1: 8, roofY: 4 },
  smokehouse: {
    plinth: { x0: 13, x1: 19, z0: 3, z1: 9 },
    walls: { x0: 14, x1: 18, z0: 4, z1: 8 },
    room: { x0: 15, x1: 17, z0: 5, z1: 7 },
    wallTop: 4, beamY: 5,
    roof: { axis: 'z', across: [13, 19], along: [3, 9], eaveY: 5, steep: true },
    chimney: [16, 9],
  },
  grainBed: { x0: 1, x1: 11, z0: 13, z1: 20, channelX: 6 },
}

export const ROOMS = [
  { name: 'Cookhouse', use: 'the Camp Cook works round the pot in the middle; diners sit on the bench facing the fire', box: [3, 1, 3, 9, 4, 9] },
  { name: 'Smokehouse', use: 'smoking: the smoker against the back wall, counters either side, hooks overhead', box: [15, 1, 5, 17, 4, 7], level: 2 },
]

export const OPENINGS = {
  door: { at: [6, 1, 2], facing: 'south', step: [6, 0, 1] },
  smokeDoor: { at: [16, 1, 4], facing: 'south', step: [16, 0, 3], level: 2 },
  // every cookhouse window is two panes high (y 2..3, or 3..4 over the back counter) with shutters
  windows: [
    { name: 'front west', cells: [[3, 2, 2], [3, 3, 2]], out: 'north' },
    { name: 'front east', cells: [[9, 2, 2], [9, 3, 2]], out: 'north' },
    { name: 'west', cells: [[2, 2, 6], [2, 3, 6]], out: 'west' },
    { name: 'east', cells: [[10, 2, 6], [10, 3, 6]], out: 'east' },
    { name: 'back west', cells: [[3, 2, 10], [3, 3, 10]], out: 'south' },
    { name: 'back east', cells: [[9, 2, 10], [9, 3, 10]], out: 'south' },
    { name: 'back middle', cells: [[6, 3, 10], [6, 4, 10]], out: 'south' },
  ],
  // the smokehouse breathes through bars, not glass: two vents high in its side walls and one in the front gable
  vents: [[14, 3, 6], [18, 3, 6], [16, 7, 4]],
}

// The pins the GameTests hold (report them to the lane): the job block, a bale, and (Level II) the smoker and the plot.
export const PINS = {
  pot: [6, 1, 6],
  bale: [1, 1, 6],
  smoker: [16, 1, 7],
  grains: [2, 2, 14],
  farmland: [2, 1, 14],
}

// Every piece of furniture, footprint first (kind, part, pos, facing, cells), as house_t1_b's plan.
export const FURNITURE = [
  // Cookhouse, the back wall: the dresser (a counter run under shelves of crockery) between the two stores (chests)
  { room: 'Cookhouse', kind: 'counter', part: 'counter', pos: [5, 1, 9], facing: 'south', wood: 'spruce' },
  { room: 'Cookhouse', kind: 'counter', part: 'counter', pos: [6, 1, 9], facing: 'south', topSlab: true, wood: 'spruce' },
  { room: 'Cookhouse', kind: 'counter', part: 'counter', pos: [7, 1, 9], facing: 'south', wood: 'spruce' },
  { room: 'Cookhouse', kind: 'crockery', part: 'plates', pos: [6, 3, 9], facing: 'south', wood: 'spruce', block: 'flower_pot' },
  { room: 'Cookhouse', kind: 'shelf', part: 'shelf', pos: [5, 3, 9], facing: 'north', wood: 'spruce', crockery: 'flower_pot' },
  { room: 'Cookhouse', kind: 'shelf', part: 'shelf', pos: [7, 3, 9], facing: 'north', wood: 'spruce', crockery: 'potted_brown_mushroom' },
  { room: 'Cookhouse', kind: 'fixture', part: 'pantry', pos: [3, 1, 9], facing: 'north', block: 'chest' },
  { room: 'Cookhouse', kind: 'fixture', part: 'pantry', pos: [9, 1, 9], facing: 'north', block: 'chest' },
  // the east wall: a prep counter under the window, a cauldron (the water pot) at its end
  { room: 'Cookhouse', kind: 'counter', part: 'counter', pos: [9, 1, 5], facing: 'east', topSlab: true, wood: 'spruce' },
  { room: 'Cookhouse', kind: 'counter', part: 'counter', pos: [9, 1, 7], facing: 'east', wood: 'spruce' },
  { room: 'Cookhouse', kind: 'crockery', part: 'plates', pos: [9, 3, 5], facing: 'east', wood: 'spruce', block: 'potted_red_mushroom' },
  // the west wall: the diners' bench facing the fire, a rug at their feet
  { room: 'Cookhouse', kind: 'couch', part: 'couch', pos: [3, 1, 5], facing: 'east', length: 3, wood: 'spruce', colour: 'brown' },
  { room: 'Cookhouse', kind: 'rug', part: 'rug', cellsAbs: [[4, 1, 5], [4, 1, 6], [4, 1, 7]], colour: 'orange' },
  // light: two lanterns on chains from the tiles, a third in the louvre over the pot (drawn with the louvre)
  { room: 'Cookhouse', kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 4], facing: 'north', chain: 2, wood: 'spruce' },
  { room: 'Cookhouse', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 8], facing: 'north', chain: 2, wood: 'spruce' },
  // Smokehouse (Level II): the smoker (drawn as a pin), counters either side, crockery on a shelf, a lamp
  { room: 'Smokehouse', level: 2, kind: 'counter', part: 'counter', pos: [15, 1, 7], facing: 'south', wood: 'spruce' },
  { room: 'Smokehouse', level: 2, kind: 'counter', part: 'counter', pos: [17, 1, 7], facing: 'south', wood: 'spruce' },
  { room: 'Smokehouse', level: 2, kind: 'shelf', part: 'shelf', pos: [15, 3, 7], facing: 'north', wood: 'spruce', crockery: 'flower_pot' },
  { room: 'Smokehouse', level: 2, kind: 'fixture', part: 'pantry', pos: [15, 1, 5], facing: 'east', block: 'chest' },
  { room: 'Smokehouse', level: 2, kind: 'lamp', part: 'hangingLamp', pos: [16, 4, 6], facing: 'north', chain: 0, wood: 'spruce' },
]

// The walking line: ground in front -> the terrace step -> the door -> round the pot (the cook's ring) -> the dresser.
// Level II: along the front of the terrace -> the smokehouse step -> its door -> the smoker; and round the west side
// (past the bales) to the path along the grain bed.
export const WALK = [
  [6, 0, -1], [6, 0, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [5, 1, 5], [5, 1, 6], [5, 1, 7], [5, 1, 8], [6, 1, 8], [7, 1, 8],
  [7, 1, 7], [7, 1, 6], [7, 1, 5],
  [12, 1, 1], [13, 1, 2], [16, 0, 2], [16, 1, 3], [16, 1, 4], [16, 1, 5], [16, 1, 6],
  [12, 0, 2], [12, 0, 9], [12, 0, 12], [1, 0, 12],
]

// Outside (not furniture spots): flower beds either side of the step, lamp posts at the front corners, two woodpiles
// on the east terrace (the fuel), and in Level II a path round the cookhouse to the grain bed and a woodpile by the smokehouse.
export const OUTSIDE = {
  steps: [5, 6, 7],
  flowerBeds: [{ x0: 1, x1: 3, z: 0, plants: ['poppy', 'cornflower', 'oxeye_daisy'] }, { x0: 9, x1: 11, z: 0, plants: ['oxeye_daisy', 'cornflower', 'poppy'] }],
  lampPosts: [[0, 0, 1], [12, 0, 1]],
  woodpiles: [[3, 4], [8, 9]], // on the east terrace (x 11)
  smokeWoodpile: { x: 13, z0: 5, z1: 8 },
  path: { z: 12, x0: 1, x1: 12 },
}
