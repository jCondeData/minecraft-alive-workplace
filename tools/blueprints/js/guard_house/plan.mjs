// Guard House (guard_house, one level), the plan: drawn before any wall. Rooms, every furniture footprint (kind,
// cells, facing) and the walking line come first; recipe.mjs then assembles the house from the parts library (../parts)
// to fit this plan. Brief: tools/blueprints/briefs/guard_house.md (rebuilt from nothing, ROADMAP 49).
//
// Three rough concepts (massing, roof, plan, story):
//   A. Watchtower: a 7 x 7 stone tower of three storeys with a jettied timber watch room and a steep pyramid cap,
//      a low lean-to armory against one side. Story: the village's lookout, the guard's bed at the top.
//   B. Gate Watch: two stone piers with a road arch between them, the watch's timber barrack room bridging over both
//      on a jetty that oversails every side, one steep slate gable facing the road. Story: the watch lives over the
//      village gate; everyone who comes in walks under the guard's window, and the guardroom's own window looks
//      straight into the passage.
//   C. Blockhouse and drill yard: a low log blockhouse with a fenced yard of targets and a covered rack. Story: a
//      frontier post that trains the village's watch.
// Strongest: B. A is the safe answer every guard house gives (a tower and a shed) and C is mostly yard, low and flat
// from 20 blocks away. B has an outline nobody mistakes for a house: a gable on a bridge, light showing through the
// arch underneath; the arch's shadow and the jetty's overhang on all four sides give every face real depth; its rooms
// grow out of its job (the guardroom with the post beside the gate, the lock-up opposite, the barrack room over the
// road); and it keeps to one roof, so no roof, eave or barge hangs over another (the brief's build-time rule).
//
// Coordinates: x across the front (west 0 -> east 16), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The guardroom door faces the front, a stair step at y = 0 before it; the waiting Guard
// at (6, 0, -3) walks straight in.
//
// First drawing (ridge along z, a big timber gable to the road, a 4-course stone storey): rendered, it read as a
// Tudor house with a front door between two banners: the roof was 8 high over a squat stone storey and the closed
// passage disappeared. So the ridge turned across the road (a 5-high roof over the 11-deep plan), the passage got
// its gates and a 3-high arch with a keystone, and the hero became a small cross gable straight over the gate with the watch
// window in it, the alarm bell hung under the jetty in front of the keystone.
//
// Second drawing (ridge across, the same 17 wide with a jetty on all four sides): 1,003 blocks, a hundred over the
// brief's build-time budget, and its back a plain box. The sides went flush with the piers (the jetty kept front and
// back, where the road sees it, the side windows on sills on the edge beam), 15 wide; the back gate got its own
// colours and lantern, so the road side reads as a gate from both ways.
//
// Masses (named parts):
//   Guardroom pier  the west stone pier, walls x 1..5, z 2..8, y 1..4: the post (grindstone), the gear chest, an
//                   anvil, the ladder up; a slit onto the passage.
//   Lock-up pier    the east stone pier, walls x 9..13, z 2..8, y 1..4: a front room with the armory chest and a
//                   barred cell behind an iron door worked by a lever. Its door opens off the passage.
//   Gate passage    x 6..8, the road through, under a stone arch at the front and the back (springing and keystone
//                   at y 4), each closed by an iron grille round a wooden wicket (villagers open the wicket, so the
//                   road and the Lock-up stay walkable), a timber ceiling and a lantern between.
//   Barrack room    the timber storey bridging both piers: floor y 5 over x 1..13, z 1..9 (jettied a block out over
//                   the front and the back on dark corbels, flush over the piers at the sides), frame posts on the
//                   outer line, front and back infill a block back, top plate y 9. Bunks, a mess table, the watch
//                   desk at the window over the gate, three tie beams.
//   Roof            one 45-degree slate roof, ridge along x (y 14), eaves overhanging z 0 and 10, the gables at the
//                   sides (x 1, 13) with rakes at x 0 and 14; the Watch gable, a cross gable 9 wide straight over
//                   the gate (ridge y 13, under the main ridge), whose roof runs back into the front slope.
//   Practice butts  two straw targets against the east wall under the rake.

export const SIZE = [15, 15, 11] // w, h, d (limit 18 x 18 x 20)

// Palette (60/30/10): stone (piers, plinth, arches) / dark oak frame + pale birch infill + slate roof / red (the
// watch's colour: banners, bunks, rug) and iron (grilles, bars, lanterns, bell). Why: the gate piers are what a guard
// trusts, heavy grey stone darkest at the foot; the room he sleeps in is the light timber box on top; the dark slate
// roof contrasts with the pale infill and reads as one calm shape; red marks it as the watch's across the village.
export const PALETTE = {
  plinth: [['cobblestone', 5], ['mossy_cobblestone', 2], ['andesite', 1]],
  stone: [['stone_bricks', 7], ['cracked_stone_bricks', 2]],
  footStone: [['stone_bricks', 5], ['mossy_stone_bricks', 3], ['cracked_stone_bricks', 1]], // the first course: weathered
  setts: [['cobblestone', 4], ['stone_bricks', 2], ['andesite', 2]], // the passage's paving
  keystone: 'chiseled_stone_bricks',
  archStairs: 'stone_brick_stairs',
  frame: 'dark_oak_log',
  trim: 'dark_oak', // corbels, sills and barge boards (stairs)
  infill: 'birch_planks',
  floor: 'spruce_planks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  wood: 'spruce', // doors, shutters, chairs, tables
  glass: 'glass_pane',
  bars: 'iron_bars',
  accent: 'red',
}

export const MASSES = {
  guardroom: { walls: { x0: 1, x1: 5, z0: 2, z1: 8 }, room: { x0: 2, x1: 4, z0: 3, z1: 7 }, top: 4 },
  lockup: { walls: { x0: 9, x1: 13, z0: 2, z1: 8 }, room: { x0: 10, x1: 12, z0: 3, z1: 7 }, top: 4 },
  passage: { x0: 6, x1: 8, z0: 0, z1: 10, springY: 4, arches: [2, 8] },
  barrack: {
    floorY: 5, outer: { x0: 1, x1: 13, z0: 1, z1: 9 }, // the edge beams and the frame's line
    walls: { y0: 6, y1: 8, xs: [5, 9], zs: [5], flush: ['x0', 'x1'] }, // posts at the corners, x 5 and 9, z 5
    room: { x0: 2, x1: 12, z0: 3, z1: 7 },
    tieBeams: { xs: [3, 7, 11], y: 9, z0: 2, z1: 8, kingPost: 7 }, // across the room; a king post on the middle one
  },
  roof: { axis: 'x', across: [0, 10], along: [0, 14], eaveY: 9, steep: false },
  watchGable: { axis: 'z', across: [3, 11], along: [0, 4], eaveY: 9, steep: false, face: 1 },
  corbels: { y: 4, at: [1, 5, 9, 13] }, // under the front and back jetty, at the piers' corners
}

export const ROOMS = [
  { name: 'Guardroom', use: 'the post: grindstone, gear chest, anvil, ladder up', box: [2, 1, 3, 4, 4, 7] },
  { name: 'Lock-up', use: 'armory chest by the door, a barred cell behind an iron door', box: [10, 1, 3, 12, 4, 7] },
  { name: 'Gate passage', use: 'the road through, between the two gates', box: [6, 1, 3, 8, 4, 7] },
  { name: 'Barrack room', use: 'bunks, mess table, the watch desk over the gate', box: [2, 6, 3, 12, 8, 7] },
]

export const OPENINGS = {
  frontDoor: { at: [3, 1, 2], facing: 'south', step: [3, 0, 1] }, // into the Guardroom from the front
  lockupDoor: { at: [9, 1, 4], facing: 'east' }, // into the Lock-up from the passage
  // the gates: an iron grille filling each arch round a wooden wicket (x 7, y 1-2)
  gates: { z: [2, 8], wicket: [7, 1], grille: [[6, 1], [6, 2], [6, 3], [8, 1], [8, 2], [8, 3], [7, 3]] },
  cellDoor: { at: [11, 1, 5], jamb: 12, lever: 'north' }, // the iron door in the bars, its lever on the jamb outside
  cellBars: [[10, 1, 5], [10, 2, 5], [10, 3, 5], [10, 4, 5], [11, 3, 5], [11, 4, 5], [12, 4, 5]],
  ladder: { x: 4, z: 7, y0: 1, y1: 5, facing: 'north' }, // Guardroom up through the floor into the Barrack room
  // ground floor: narrow lights in the stone (one pane wide, two high); barred in the Lock-up's cell
  slits: [
    { cells: [[1, 2, 4], [1, 3, 4]], block: 'glass' }, // Guardroom west
    { cells: [[5, 2, 5], [5, 3, 5]], block: 'glass' }, // Guardroom onto the passage: the guard watches the gate
    { cells: [[3, 2, 8], [3, 3, 8]], block: 'glass' }, // Guardroom back
    { cells: [[11, 2, 2], [11, 3, 2]], block: 'glass' }, // Lock-up front
    { cells: [[13, 2, 6], [13, 3, 6]], block: 'bars' }, // the cell, east
    { cells: [[11, 2, 8], [11, 3, 8]], block: 'bars' }, // the cell, back
  ],
  // Barrack room windows, two high: front and back in the infill a block back, shuttered in the recess (on the edge
  // beam) either side; the sides in the flush wall, each on a sill bracketed off the edge beam
  windows: [
    { name: 'front west', cells: [[3, 6, 2], [3, 7, 2]], out: 'north', dress: 'shutters' },
    { name: 'watch window', cells: [[7, 6, 2], [7, 7, 2]], out: 'north', dress: 'shutters' },
    { name: 'front east', cells: [[11, 6, 2], [11, 7, 2]], out: 'north', dress: 'shutters' },
    { name: 'back west', cells: [[3, 6, 8], [3, 7, 8]], out: 'south', dress: 'shutters' },
    { name: 'back middle', cells: [[7, 6, 8], [7, 7, 8]], out: 'south', dress: 'shutters' },
    { name: 'back east', cells: [[11, 6, 8], [11, 7, 8]], out: 'south', dress: 'shutters' },
    { name: 'west front', cells: [[1, 6, 3], [1, 7, 3]], out: 'west', dress: 'sill' },
    { name: 'west back', cells: [[1, 6, 7], [1, 7, 7]], out: 'west', dress: 'sill' },
    { name: 'east front', cells: [[13, 6, 3], [13, 7, 3]], out: 'east', dress: 'sill' },
    { name: 'east back', cells: [[13, 6, 7], [13, 7, 7]], out: 'east', dress: 'sill' },
  ],
  gableLights: { watch: [[7, 10, 1], [7, 11, 1]], sides: [[1, 11, 5], [1, 12, 5], [13, 11, 5], [13, 12, 5]] },
}

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a furniture spot) or a fixture
// (a vanilla block used as itself). part: the furnitureParts part that draws it. facing: the way a person using it
// faces. Fixtures with no part are drawn by the recipe (grindstone, anvil, hay).
export const FURNITURE = [
  // Guardroom: the post against the west wall with a free 2 x 3 floor in front of it (a player and the villager side
  // by side), the gear chest in the back corner, the anvil by the door, the ladder in the other back corner
  { room: 'Guardroom', kind: 'fixture', block: 'grindstone', pos: [2, 1, 5], facing: 'east' },
  { room: 'Guardroom', kind: 'fixture', part: 'chest', pos: [2, 1, 7], facing: 'east', block: 'chest' },
  { room: 'Guardroom', kind: 'fixture', block: 'anvil', pos: [2, 1, 3], facing: 'south' },
  { room: 'Guardroom', kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 5], facing: 'north', wood: 'spruce' },
  { room: 'Guardroom', kind: 'picture', part: 'picture', pos: [2, 3, 7], facing: 'north', wood: 'spruce', colour: 'red' },
  // Lock-up: the armory chest in the front corner under the lever, a lamp, the cell's straw pallet
  { room: 'Lock-up', kind: 'fixture', part: 'chest', pos: [12, 1, 3], facing: 'west', block: 'chest' },
  { room: 'Lock-up', kind: 'lamp', part: 'hangingLamp', pos: [10, 4, 3], facing: 'north', wood: 'spruce' },
  { room: 'Lock-up', kind: 'fixture', block: 'hay_block', pos: [10, 1, 7] },
  { room: 'Lock-up', kind: 'fixture', block: 'hay_block', pos: [11, 1, 7] },
  // Barrack room, west: the mess table for four by the ladder, the kit chest and a cupboard against the back wall
  { room: 'Barrack room', kind: 'table', part: 'table', pos: [3, 6, 4], facing: 'north', wood: 'spruce', colour: 'red' },
  { room: 'Barrack room', kind: 'table', part: 'table', pos: [3, 6, 5], facing: 'north', wood: 'spruce', colour: 'red' },
  { room: 'Barrack room', kind: 'chair', part: 'chair', pos: [2, 6, 4], facing: 'east', wood: 'spruce' },
  { room: 'Barrack room', kind: 'chair', part: 'chair', pos: [2, 6, 5], facing: 'east', wood: 'spruce' },
  { room: 'Barrack room', kind: 'chair', part: 'chair', pos: [4, 6, 4], facing: 'west', wood: 'spruce' },
  { room: 'Barrack room', kind: 'chair', part: 'chair', pos: [4, 6, 5], facing: 'west', wood: 'spruce' },
  { room: 'Barrack room', kind: 'fixture', part: 'chest', pos: [5, 6, 7], facing: 'north', block: 'chest' },
  { room: 'Barrack room', kind: 'cupboard', part: 'cupboard', pos: [9, 6, 7], facing: 'north', wood: 'spruce' },
  // middle: the watch desk under the window over the gate, its stool, a red rug on the way through
  { room: 'Barrack room', kind: 'desk', part: 'desk', pos: [7, 6, 3], facing: 'north', wood: 'spruce' },
  { room: 'Barrack room', kind: 'chair', part: 'chair', pos: [7, 6, 4], facing: 'north', wood: 'spruce' },
  { room: 'Barrack room', kind: 'rug', part: 'rug', cellsAbs: [[6, 6, 5], [7, 6, 5], [8, 6, 5], [6, 6, 6], [7, 6, 6], [8, 6, 6]], colour: 'red' },
  // east: two bunks heads to the east wall, a nightstand between (the worker's bed is the first)
  { room: 'Barrack room', kind: 'bed', part: 'bed', pos: [11, 6, 4], facing: 'east', colour: 'red' },
  { room: 'Barrack room', kind: 'bed', part: 'bed', pos: [11, 6, 6], facing: 'east', colour: 'red' },
  { room: 'Barrack room', kind: 'nightstand', part: 'nightstand', pos: [12, 6, 5], facing: 'west', wood: 'spruce' },
  // lamps hung from the tie beams, over the table, the rug and the bunks
  { room: 'Barrack room', kind: 'lamp', part: 'hangingLamp', pos: [3, 8, 5], facing: 'north', wood: 'spruce' },
  { room: 'Barrack room', kind: 'lamp', part: 'hangingLamp', pos: [7, 8, 6], facing: 'north', wood: 'spruce' },
  { room: 'Barrack room', kind: 'lamp', part: 'hangingLamp', pos: [11, 8, 5], facing: 'north', wood: 'spruce' },
]

// The walking line: the waiting Guard -> step -> Guardroom door -> the post; up the ladder -> mess table -> rug ->
// bunks. And the road: through the front wicket, the Lock-up door off the passage, out through the back wicket.
export const WALK = [
  [6, 0, -3], [4, 0, 0], [3, 0, 0], [3, 0, 1], [3, 1, 2], [3, 1, 3], [3, 1, 4], [3, 1, 5], [3, 1, 6], [4, 1, 6], [4, 1, 7],
  [3, 6, 7], [4, 6, 6], [5, 6, 6], [6, 6, 6], [7, 6, 6], [8, 6, 6], [9, 6, 5], [10, 6, 5], [11, 6, 5],
  [7, 1, 0], [7, 1, 1], [7, 1, 2], [7, 1, 3], [8, 1, 4], [9, 1, 4], [10, 1, 4], [11, 1, 4], [7, 1, 8], [7, 1, 10],
]

// Outside the rooms: the watch's colours either side of both gates, lanterns at the door and the back gate and in
// the passage, the alarm bell under the jetty in front of the keystone, two straw butts against the east wall.
export const OUTSIDE = {
  banners: [[5, 3, 1, 'north'], [9, 3, 1, 'north'], [5, 3, 9, 'south'], [9, 3, 9, 'south']], // on the pier faces
  lamps: [[3, 4, 1], [7, 4, 9], [7, 4, 5]], // over the step, at the back gate, in the passage (hung from the floor)
  bell: [7, 4, 1], // hung from the jetty's edge beam, in front of the front arch's keystone
  targets: [[14, 0, 3], [14, 0, 7]],
}
