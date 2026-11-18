// Lookout Tower (lookout_tower .. lookout_tower_4), the plan: drawn before any wall (ROADMAP 49, brief
// tools/blueprints/briefs/lookout_tower.md). recipe.mjs assembles it from the parts library (../parts) in passes.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   1. The Hoarded Watchtower. A slim 5 x 5 rubble shaft on a battered foot carries a timber watch gallery jettied a
//      block out on stone corbels, open on all four sides under a steep slate hat with a copper rod; II leans a
//      timber gear shed against its flank, tucked under the gallery's jetty, its alarm bell under the eave; III ends
//      the shed against a squat crenellated guardhouse whose flat roof is a second, lower fighting platform; IV digs
//      an armory and lock-up under the guardhouse. Story: a village's first stone tower, grown into a little post.
//   2. The Stair Tower. A square stone tower with a stair winding up its outside on a stepped buttress to a door half
//      way up, a crenellated top with no roof; II and III add rooms at its foot. Story: a raider-proof keep whose
//      ground floor is a store. Strong idea, but 11 x 11 of stair round a 5-wide shaft makes a squat lump at this
//      height, and an open top with merlons hides the archers' near view (merlons read as no-stations).
//   3. The Stilt Lookout. Four spruce legs splayed on stone pads, braced in X, a plank cabin on top under a pyramid
//      roof; II adds a shed between the legs. Story: a forester's fire watch. Light and quick, but not "a stone tower
//      with a lookout platform" (the questbook's words), and a cabin on legs gives nothing for III and IV to grow from.
// Strongest: 1. It has a silhouette you know from 20 blocks away (narrow shaft, wide hat: the classic lookout
// outline, the top heavier than the waist), three materials that each do one job (stone carries, timber looks out,
// slate keeps the rain off), and it grows in a line that reads as a story: tower, then the shed at its foot, then the
// guardhouse that makes the whole a fortified post, its roof a second tier of archers below the first. Every level
// keeps the tower's middle where the guards are.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block; the basement below 0),
// z from the front (z = 0, north) to the back. Every door faces the front.
//
// Masses (named parts the owner's feedback can point at):
//   Shaft       the tower: rubble walls x 2..6, z 2..6, y 0..7 (stone-brick quoins at the corners, a stone-brick
//               band at the mid floor y 4), a battered foot of stairs round it at y 0; arrow loops in every face.
//   Corbels     upside-down stone-brick stairs round the shaft's top at y 7 (x 1..7, z 1..7), carrying the jetty.
//   Gallery     the watch deck at y 8 over x 1..7, z 1..7, open on every side: corner posts, knee braces, a rail
//               at y 9, a beam ring at y 11 and a plank ceiling inside it, a spruce king post in the middle carrying
//               the hat's apex. The archers' stations are the deck inside the rail (x 2..6, z 2..6), round the post.
//               The ladder comes up through a hatch beside the post.
//   Hat         a hipped slate roof from the eave ring (x 0..8, z 0..8) at y 11 to a tiled cap at y 14, a copper
//               lightning rod on it (y 15); two lanterns hung from the gallery ceiling.
//   Gear Shed   (II) a timber lean-to on the east flank: x 7..11, z 2..6, roof falling from under the jetty
//               (x 8, y 7, a flat course of tiles there) to the eave (x 12, y 3); a wall plate at y 3, a gable light
//               over it; its door on the front, the alarm bell under its front eave.
//   Guardhouse  (III) a squat stone block east of the shed: x 11..17, z 1..7 (a block proud of the shed front and
//               back) on a battered foot (stone sills on it under the windows), walls to y 4 with a corbel course, a flat roof deck at y 5 inside a crenellated parapet
//               (merlons on alternate cells, slab caps): the second fighting platform. Bunks, a second Guard Post.
//   Armory      (IV) under the guardhouse, 4 deep: walls x 11..17, z 1..7, floor y -4; a ladder under a hatch; a
//               double chest of arms, an anvil, a barred lock-up cell.

export const IDS = { 1: 'lookout_tower', 2: 'lookout_tower_2', 3: 'lookout_tower_3', 4: 'lookout_tower_4' }
export const SIZES = { 1: [9, 16, 9], 2: [13, 16, 9], 3: [19, 16, 9], 4: [19, 16, 9] } // limit I: 13 x 16 x 13
export const BASEMENT = { 4: 4 }

// Palette (60/30/10): rubble stone (the shaft, the guardhouse) / spruce (gallery, shed, joinery) / deepslate slate
// (the hat, the shed roof). Accents: stone-brick dressings (quoins, corbels, bands, caps), copper on the rod, the bell.
// Why: grey stone a village quarries itself carries the weight, warm timber where the watch lives and looks out, a
// near-black slate hat that reads against the sky; mossy rubble only in the bottom courses (weathering low down).
export const PALETTE = {
  stone: [['cobblestone', 6], ['andesite', 3], ['stone', 1]],
  stoneLow: [['cobblestone', 5], ['stone', 2], ['andesite', 1], ['mossy_cobblestone', 3]],
  dressed: 'stone_bricks', dressedAlt: 'polished_andesite',
  dressedStairs: 'stone_brick_stairs', dressedSlab: 'stone_brick_slab',
  frame: 'spruce_log', boards: 'spruce_planks', wood: 'spruce',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'spruce_planks', groundFloor: 'stone_bricks', cellarFloor: 'cobblestone',
  glass: 'glass_pane', bars: 'iron_bars', rod: 'lightning_rod',
}

export const MASSES = {
  shaft: { x0: 2, x1: 6, z0: 2, z1: 6, top: 7, midY: 4 },
  room: { x0: 3, x1: 5, z0: 3, z1: 5 },
  gallery: { x0: 1, x1: 7, z0: 1, z1: 7, deckY: 8, railY: 9, beamY: 11 },
  hat: { x0: 0, x1: 8, z0: 0, z1: 8, y: 11 },
  shed: { x0: 7, x1: 11, z0: 2, z1: 6, wallTop: 3, eaveX: 12 },
  guardhouse: { x0: 11, x1: 17, z0: 1, z1: 7, deckY: 5 },
  armory: { x0: 11, x1: 17, z0: 1, z1: 7, depth: 4 },
}

export const ROOMS = [
  { level: 1, name: 'Foot', use: 'the way in, the ladder up; II: the watchman\'s Guard Post', box: [3, 1, 3, 5, 3, 5] },
  { level: 1, name: 'Loop room', use: 'a landing half way, a loop to every side', box: [3, 5, 3, 5, 7, 5] },
  { level: 1, name: 'Gallery', use: 'the battle stations: the deck inside the rail, open on every side', box: [2, 9, 2, 6, 10, 6] },
  { level: 2, name: 'Gear Shed', use: 'the watch\'s gear chest, a bench and table', box: [7, 1, 3, 10, 3, 5] },
  { level: 3, name: 'Guardhouse', use: 'two bunks, the second Guard Post, a table, the ladder to the roof', box: [12, 1, 2, 16, 4, 6] },
  { level: 3, name: 'Roof walk', use: 'the second tier of stations inside the parapet', box: [11, 6, 1, 17, 7, 7] },
  { level: 4, name: 'Armory', use: 'the arms chest, an anvil, the lock-up cell', box: [12, -3, 2, 16, -1, 6] },
]

export const OPENINGS = {
  towerDoor: { at: [4, 1, 2], facing: 'south', step: [4, 0, 1] },
  shedDoor: { level: 2, at: [9, 1, 2], facing: 'south', step: [9, 0, 1] },
  guardDoor: { level: 3, at: [14, 1, 1], facing: 'south', step: [14, 0, 0] },
  towerToShed: { level: 2, at: [6, 1, 4] },
  shedToGuard: { level: 3, at: [11, 1, 4] },
  // arrow loops in the shaft: [x, y, z, the way in]; a plunging sill under each
  loops: [
    { at: [2, 2, 4], inward: 'east' }, { at: [4, 2, 6], inward: 'north' },
    { at: [4, 5, 2], inward: 'south' }, { at: [2, 5, 4], inward: 'east' }, { at: [6, 5, 4], inward: 'west' }, { at: [4, 5, 6], inward: 'north' },
  ],
  windows: [
    { level: 2, name: 'shed back', cells: [[9, 2, 6]], out: 'south', dress: 'shutters' },
    { level: 2, until: 2, name: 'shed east', cells: [[11, 2, 4]], out: 'east', dress: 'shutters' },
    { level: 3, name: 'guard front west', cells: [[12, 2, 1], [12, 3, 1]], out: 'north', dress: 'sill', sill: 'stone_brick_stairs' },
    { level: 3, name: 'guard front east', cells: [[16, 2, 1], [16, 3, 1]], out: 'north', dress: 'sill', sill: 'stone_brick_stairs' },
    { level: 3, name: 'guard east', cells: [[17, 2, 4], [17, 3, 4]], out: 'east', dress: 'sill', sill: 'stone_brick_stairs' },
    { level: 3, name: 'guard back', cells: [[14, 2, 7], [14, 3, 7]], out: 'south', dress: 'sill', sill: 'stone_brick_stairs' },
  ],
  ladders: [
    { level: 1, x: 3, z: 5, y0: 1, y1: 8, facing: 'east', hatch: true }, // up the shaft through the mid floor to a hatch in the deck
    { level: 3, x: 16, z: 6, y0: 1, y1: 5, facing: 'north', hatch: true }, // the guardhouse to a hatch in its roof walk
  ],
  hatch: { level: 4, x: 15, z: 6, facing: 'north' }, // down to the armory
}

// Every piece of furniture, footprint first (kind: a furniture.py kind or a fixture; part: the furnitureParts part).
export const FURNITURE = [
  // Foot: a lantern from the mid floor; II: the Guard Post at the ladder's foot
  { level: 1, room: 'Foot', kind: 'lamp', part: 'hangingLamp', pos: [4, 3, 4], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Loop room', kind: 'lamp', part: 'hangingLamp', pos: [4, 7, 4], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Gallery', kind: 'lamp', part: 'hangingLamp', pos: [3, 10, 3], facing: 'north', wood: 'spruce' },
  { level: 1, room: 'Gallery', kind: 'lamp', part: 'hangingLamp', pos: [5, 10, 5], facing: 'north', wood: 'spruce' },
  // Gear Shed (II): the gear chest, a bench at a table, a lantern from the roof
  { level: 2, room: 'Gear Shed', kind: 'fixture', part: 'chest', pos: [7, 1, 5], facing: 'north', block: 'chest' },
  { level: 2, room: 'Gear Shed', kind: 'table', part: 'table', pos: [10, 1, 5], facing: 'north', wood: 'spruce', colour: 'gray' },
  { level: 2, room: 'Gear Shed', kind: 'chair', part: 'chair', pos: [9, 1, 5], facing: 'east', wood: 'spruce' },
  { level: 2, room: 'Gear Shed', kind: 'lamp', part: 'hangingLamp', pos: [9, 5, 4], facing: 'north', wood: 'spruce' },
  // Guardhouse (III): two bunks heads to the back wall, a nightstand, a table and two chairs, a lantern
  { level: 3, room: 'Guardhouse', kind: 'bed', part: 'bed', pos: [12, 1, 5], facing: 'south', colour: 'red' },
  { level: 3, room: 'Guardhouse', kind: 'bed', part: 'bed', pos: [14, 1, 5], facing: 'south', colour: 'red' },
  { level: 3, room: 'Guardhouse', kind: 'nightstand', part: 'nightstand', pos: [13, 1, 6], facing: 'north', wood: 'spruce' },
  { level: 3, room: 'Guardhouse', kind: 'table', part: 'table', pos: [12, 1, 3], facing: 'north', wood: 'spruce', colour: 'red' },
  { level: 3, room: 'Guardhouse', kind: 'chair', part: 'chair', pos: [12, 1, 2], facing: 'south', wood: 'spruce' },
  { level: 3, room: 'Guardhouse', kind: 'chair', part: 'chair', pos: [13, 1, 3], facing: 'west', wood: 'spruce' },
  { level: 3, room: 'Guardhouse', kind: 'fixture', part: 'chest', pos: [16, 1, 4], facing: 'west', block: 'chest' },
  { level: 3, room: 'Guardhouse', kind: 'lamp', part: 'hangingLamp', pos: [14, 4, 4], facing: 'north', wood: 'spruce' },
  { level: 3, room: 'Guardhouse', kind: 'rug', part: 'rug', cellsAbs: [[13, 1, 4], [14, 1, 4], [13, 1, 5]], colour: 'gray' },
  // Armory (IV): lanterns from the floor above (no dark cell)
  { level: 4, room: 'Armory', kind: 'lamp', part: 'hangingLamp', pos: [13, -1, 4], facing: 'north', wood: 'spruce' },
  { level: 4, room: 'Armory', kind: 'lamp', part: 'hangingLamp', pos: [16, -1, 3], facing: 'north', wood: 'spruce' },
]

// Job blocks: a guard's grindstone at the ladder's foot (II), a second in the guardhouse (III). (Drawn as Guard Posts
// until the ship step: the Guard Post has no recipe any more, so a builder could never be stocked with one.)
export const POSTS = [
  { level: 2, at: [5, 1, 5], facing: 'north' },
  { level: 3, at: [16, 1, 2], facing: 'west' },
]

// The armory's fittings (IV): a double chest of arms on the back wall, an anvil, the lock-up cell's bars and door.
export const ARMORY = {
  chest: { at: [12, -3, 6], facing: 'north', along: 'east' },
  anvil: [12, -3, 2],
  cellBars: [[14, -3, 3], [14, -3, 4], [15, -3, 4], [16, -3, 4]], // drawn 3 high
  cellDoor: [14, -3, 2],
  cellStraw: [16, -3, 2],
}

// The walking line: the ground before the tower (4, 0, 0) -> the step -> the door -> the foot; the ladder up through
// the loop room to the gallery. II: the foot -> the shed through its east doorway (6, 1, 4) -> its own door to the
// front. III: the shed -> the guardhouse (11, 1, 4) -> bunks, table, the roof ladder; its own front door (14, 1, 1).
// IV: the guardhouse -> the hatch (15, 0, 6) -> the armory floor (y -3).
export const WALK = [
  [4, 0, 0], [4, 0, 1], [4, 1, 2], [4, 1, 3], [4, 1, 4], [3, 1, 4], [3, 1, 5],
  [5, 1, 4], [6, 1, 4], [7, 1, 4], [8, 1, 4], [9, 1, 3], [9, 1, 2],
  [10, 1, 4], [11, 1, 4], [12, 1, 4], [14, 1, 3], [14, 1, 2], [14, 1, 1], [15, 1, 5], [15, 0, 6], [15, -3, 5],
]

// Outside (not furniture spots): a fence-bracket lantern either side of the tower door; II: the alarm bell under the
// shed's front eave; III: fence-bracket lanterns either side of the guardhouse door.
export const OUTSIDE = {
  doorLamps: [[3, 3, 1], [5, 3, 1]],
  bell: { level: 2, at: [10, 4, 1] },
  guardLamps: { level: 3, cells: [[13, 3, 0], [15, 3, 0]] },
}
