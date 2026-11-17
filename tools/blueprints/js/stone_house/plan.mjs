// Stone House (ROADMAP 49, brief tools/blueprints/briefs/stone_house.md): the plan, written before any block.
//
// THREE CONCEPTS
//  A. The Mason's House. A square two-storey rubble block, ridge along the street, one fat stone stack on the west
//     gable with the hearth under it, dressed quoins and a string course at the floor line. Grows sideways: a lean-to
//     parlour wing on the east (II), a catslide outshot carrying the back slope down to the garden (III), a vaulted
//     store dug under it (IV). Story: the village mason built it of what he cut, and the corners show it.
//  B. The Tower House. A narrow three-storey stone tower with a pyramid roof and a timber hall beside it; the stair
//     winds in the tower. Story: a guard's house turned family home. Striking, but a two-bed I can't stand as a tower
//     and the 13 x 13 plot makes the hall a lean-to; the tower's 3x3 rooms can't hold a bed and its nightstand.
//  C. The Longhouse. One long low range, byre end and house end, a thatch-steep roof with the loft over the byre.
//     Story: farmers under one roof. Reads as a barn and fights the brief's "family house", and the 13-wide plot
//     leaves no room for II to grow longer.
//  Strongest: A. It is the one whose growth is the story (each upgrade is a real extension a family adds to a stone
//  house: the side parlour, the catslide, the cellar), the end stack and the quoins give it a face no other home in
//  the village has, and every level stays a believable house rather than a box with bits on. Built: A.
//
// STORY: the mason's house. A plain, solid front, two sash-size windows and a door under a slate hood; everything
// heavy (the chimney, the quoins, the course at the floor) says stone, the slate roof says money well spent.
//
// PALETTE 60/30/10: 60 rubble stone walls (cobble, stone, andesite, a little moss) on a darker cobble plinth;
// 30 dark slate roof (deepslate tiles) and spruce (doors, shutters, floors, furniture); 10 dressed stone bricks
// (quoins, string course, sills, chimney cap) and the flowers in the window pots.
export const LEVELS = [1, 2, 3, 4]
export const IDS = { 1: 'stone_house', 2: 'stone_house_2', 3: 'stone_house_3', 4: 'stone_house_4' }
export const SIZES = { 1: [11, 16, 11], 2: [13, 16, 11], 3: [13, 16, 14], 4: [13, 16, 14] }
export const BASEMENT = { 4: 4 }

export const PALETTE = {
  rubble: [['cobblestone', 5], ['stone', 3], ['andesite', 2]],
  // moss only where water lies: the plinth course, the corners' feet, under the eaves
  weathered: [['mossy_cobblestone', 3], ['cobblestone', 2], ['stone', 1]],
  partition: 'spruce_planks', band: 'stone_brick_slab',
  plinth: [['cobblestone', 3], ['mossy_cobblestone', 1]],
  dressed: 'stone_bricks', dressedAlt: 'polished_andesite', sill: 'stone_brick_stairs',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  wood: 'spruce', floor: 'spruce_planks', trim: 'dark_oak', glass: 'glass_pane',
  cellarFloor: 'cobblestone', cellarWall: [['cobblestone', 3], ['stone', 2], ['mossy_cobblestone', 1]],
}

// MASSES (template coordinates; the front is z = 0)
export const MASSES = {
  // the main block: walls x 1..9, z 1..9; ground floor y 1..4, upper floor at y 5, knee walls to y 8, a 45-degree
  // slate roof eaves at y 8 over z 0 and z 10, ridge at y 13 along x, rakes overhanging x 0 and x 10
  main: { x0: 1, z0: 1, x1: 9, z1: 9, upperY: 5, kneeTop: 8, roof: { axis: 'x', across: [0, 10], along: [0, 10], eaveY: 8 } },
  // the west stack: chimney at x 0, z 4 from the plinth past the ridge; the hearth in the wall at (1, 1, 3)
  chimney: { x: 0, z: 3, top: 14 },
  // II: the parlour wing, a lean-to against the east gable, walls x 9..12, z 2..8, its roof a lean-to from y 7 at
  // x 10 down to y 5 over the x 12 wall, overhanging the front at z 1 and the back at z 9
  wing: { x0: 9, z0: 2, x1: 12, z1: 8, roof: { axis: 'z', across: [10, 12], ridge: 10, along: [1, 9], ridgeY: 7 } },
  // III: the catslide outshot behind, walls x 1..9, z 9..13: the main back slope carried on down to y 5 over z 13
  outshot: { x0: 1, z0: 9, x1: 9, z1: 13, roof: { axis: 'x', across: [0, 13], ridge: 5, along: [0, 10], ridgeY: 13 } },
  // IV: the store under the main block, 4 deep (floor at y -4)
  cellar: { x0: 1, z0: 1, x1: 9, z1: 9, depth: 4, ladder: { x: 2, z: 5, facing: 'east' } },
}

export const ROOMS = [
  { level: 1, name: 'Hall', use: 'hearth, table for four', box: [2, 1, 2, 8, 4, 5] },
  { level: 1, name: 'Box room', use: 'the downstairs bed behind a plank partition', box: [2, 1, 7, 4, 4, 8] },
  { level: 1, name: 'Kitchen', use: 'stove, counter, barrel, open to the hall', box: [6, 1, 6, 8, 4, 8] },
  { level: 1, name: 'Chamber', use: 'the upstairs bedroom under the slates: one bed, a desk, a chest', box: [2, 6, 2, 8, 12, 8] },
  { level: 2, name: 'Parlour', use: 'the east wing: a settle by the window and a bed for a grandparent', box: [10, 1, 3, 11, 6, 7] },
  { level: 3, name: 'Back room', use: 'under the catslide: two beds for the children', box: [2, 1, 10, 8, 6, 12] },
  { level: 4, name: 'Store', use: 'the cellar under the hall: casks and barrels, down a ladder by the hearth', box: [2, -3, 2, 8, -1, 8] },
]

export const OPENINGS = {
  frontDoor: { at: [5, 1, 1], facing: 'south', step: [5, 0, 0] },
  // the stair: a straight flight up the east wall from the front, landing at (8, 5, 6)
  stairs: { flight: [[8, 1, 2], [8, 2, 3], [8, 3, 4], [8, 4, 5], [8, 5, 6]], rises: 'south', well: [[8, 5, 2], [8, 5, 3], [8, 5, 4], [8, 5, 5]], rail: [[7, 6, 2], [7, 6, 5]] },
  // the box room: a plank partition (z 6, x 2..4, its door at x 4; x 5, z 6..8) closes the downstairs bed off from
  // the hall and the kitchen. (Ship, 49.4: the door at x 4, not 3, where every older drawing has a chest that a refresh
  // keeps, so a refreshed house's box room still has its way in.)
  partition: { cells: [[2, 6], [3, 6], [5, 6], [5, 7], [5, 8]], door: [4, 1, 6] },
  wingDoorway: { at: [9, 1, 7], level: 2 }, // I's east window becomes the way into the parlour
  backDoorway: { at: [7, 1, 9], level: 3 }, // I's kitchen back window becomes the way into the back room
  windows: [
    { name: 'hall front west', cells: [[3, 2, 1], [3, 3, 1]], out: 'north', dress: 'sill', pots: ['potted_red_tulip'] },
    { name: 'hall front east', cells: [[7, 2, 1], [7, 3, 1]], out: 'north', dress: 'sill', pots: ['potted_cornflower'] },
    { name: 'box room back', cells: [[3, 2, 9], [3, 3, 9]], out: 'south', dress: 'sill', pots: ['potted_oxeye_daisy'], until: 2 },
    { name: 'box room west', cells: [[1, 2, 7], [1, 3, 7]], out: 'west', dress: 'shutters' },
    { name: 'kitchen back', cells: [[7, 2, 9], [7, 3, 9]], out: 'south', dress: 'sill', pots: ['potted_fern'], until: 2 },
    { name: 'hall east', cells: [[9, 2, 7], [9, 3, 7]], out: 'east', dress: 'shutters', until: 1 },
    { name: 'chamber front', cells: [[5, 6, 1], [5, 7, 1]], out: 'north', dress: 'shutters' },
    { name: 'chamber back', cells: [[5, 6, 9], [5, 7, 9]], out: 'south', dress: 'shutters', until: 2 },
    { name: 'chamber west', cells: [[1, 7, 7], [1, 8, 7]], out: 'west', dress: 'shutters' },
    { name: 'chamber east', cells: [[9, 8, 5], [9, 9, 5]], out: 'east', dress: 'shutters' },
    { name: 'parlour front', cells: [[11, 2, 2], [11, 3, 2]], out: 'north', dress: 'sill', pots: ['potted_allium'], from: 2 },
    { name: 'parlour east', cells: [[12, 2, 5], [12, 3, 5]], out: 'east', dress: null, from: 2 },
    { name: 'back room west', cells: [[1, 2, 11], [1, 3, 11]], out: 'west', dress: 'shutters', from: 3 },
    { name: 'back room back west', cells: [[3, 2, 13], [3, 3, 13]], out: 'south', dress: null, from: 3 },
    { name: 'back room back east', cells: [[6, 2, 13], [6, 3, 13]], out: 'south', dress: null, from: 3 },
  ],
}

// Every furniture piece, footprint first (from: the level that adds it). Facing: the way its user faces.
const W = 'spruce'
export const FURNITURE = [
  // Hall (front half, z 2..5): the hearth in the west wall with a settle-chair either side and a rug before it; the
  // dining table for four by the front east window, lit from above; (ship, 49.4: the clock went up to the
  // chamber, as the hatch to IV's store needs (3, 1, 5) clear and the box room's door (4, 1, 6) its approach). The door's
  // way in (x 4..6, z <= 3) stays clear: door -> (5, 3) -> the table, the fire, or (7, 3) and the stair foot.
  { kind: 'fixture', part: 'hearth', pos: [1, 1, 3], facing: 'east' },
  { kind: 'chair', part: 'armchair', pos: [2, 1, 2], facing: 'south', wood: 'dark_oak', colour: 'red' },
  { kind: 'chair', part: 'armchair', pos: [2, 1, 4], facing: 'north', wood: 'dark_oak', colour: 'red' },
  { kind: 'rug', part: 'rug', cellsAbs: [[2, 1, 3], [3, 1, 2], [3, 1, 3], [3, 1, 4]], colour: 'red' },
  { kind: 'table', part: 'table', pos: [6, 1, 4], facing: 'north', wood: W, colour: 'white' },
  { kind: 'table', part: 'table', pos: [6, 1, 5], facing: 'north', wood: W, colour: 'white' },
  { kind: 'chair', part: 'chair', pos: [5, 1, 4], facing: 'east', wood: W },
  { kind: 'chair', part: 'chair', pos: [5, 1, 5], facing: 'east', wood: W },
  { kind: 'chair', part: 'chair', pos: [7, 1, 4], facing: 'west', wood: W },
  { kind: 'chair', part: 'chair', pos: [7, 1, 5], facing: 'west', wood: W },
  { kind: 'blinds', part: 'blinds', pos: [3, 4, 2], facing: 'south', wood: W },
  { kind: 'blinds', part: 'blinds', pos: [7, 4, 2], facing: 'south', wood: W },
  { kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 3], facing: 'north', wood: W },
  { kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 4], facing: 'north', wood: W },
  // Box room (x 2..4, z 7..8, behind the partition): the downstairs bed, a lamp on a side table, a picture
  { kind: 'bed', part: 'bed', pos: [2, 1, 7], facing: 'south', colour: 'brown' },
  { kind: 'lamp', part: 'tableLamp', pos: [3, 1, 8], facing: 'west', wood: W }, // by the bed's head, clear of the door's way
  { kind: 'picture', part: 'picture', pos: [4, 3, 7], facing: 'west', wood: W, colour: 'green' },
  // Kitchen (x 6..8, z 7..8, open to the hall): the stove, a counter with crockery by it, the barrel with a shelf of
  // crockery over it, the lamp; (7, 1, 8) left clear (III's way to the back room)
  { kind: 'fixture', part: 'stove', pos: [6, 1, 7], facing: 'east', block: 'furnace' },
  { kind: 'counter', part: 'counter', pos: [6, 1, 8], facing: 'east', wood: W, topSlab: true },
  { kind: 'crockery', part: 'plates', pos: [6, 3, 8], facing: 'east', wood: W, block: 'potted_brown_mushroom' },
  { kind: 'fixture', part: 'pantry', pos: [8, 1, 8], facing: 'north', block: 'barrel' },
  { kind: 'shelf', part: 'shelf', pos: [8, 3, 8], facing: 'north', wood: W, crockery: 'flower_pot' },
  { kind: 'lamp', part: 'hangingLamp', pos: [7, 4, 7], facing: 'north', wood: W },
  // Chamber (upper floor, y 5). The walking line: off the stair at (8, 6, 7), west along z 6..7 between the rug and
  // the bed's foot. Sleeping end west: the bed's head to the back wall, its nightstand, its chest at the foot. Working
  // end front: the desk and chair in the front window's light, the cupboard in the corner. One lantern from the ridge.
  { kind: 'bed', part: 'bed', pos: [3, 6, 7], facing: 'south', colour: 'blue' },
  { kind: 'nightstand', part: 'nightstand', pos: [2, 6, 8], facing: 'north', wood: W },
  { kind: 'fixture', part: 'chest', pos: [3, 6, 6], facing: 'south', block: 'chest' },
  { kind: 'desk', part: 'desk', pos: [5, 6, 2], facing: 'north', wood: W },
  { kind: 'chair', part: 'chair', pos: [5, 6, 3], facing: 'north', wood: W },
  { kind: 'cupboard', part: 'cupboard', pos: [2, 6, 2], facing: 'east', wood: W },
  { kind: 'cupboard', part: 'cupboard', pos: [2, 6, 3], facing: 'east', wood: W },
  { kind: 'rug', part: 'rug', cellsAbs: [[4, 6, 4], [5, 6, 4], [6, 6, 4], [4, 6, 5], [5, 6, 5], [6, 6, 5]], colour: 'light_gray' },
  { kind: 'lamp', part: 'hangingLamp', pos: [5, 8, 5], facing: 'north', chain: 4, wood: W },
  { kind: 'clock', part: 'clock', pos: [7, 6, 8], facing: 'north', wood: W }, // against the back wall, off the walking line
  // II, Parlour: a settle (the couch) under the east window, a bed for a grandparent, its nightstand, a lamp
  { from: 2, kind: 'couch', part: 'couch', pos: [11, 1, 6], facing: 'west', length: 2, wood: W, colour: 'green' },
  { from: 2, kind: 'bed', part: 'bed', pos: [11, 1, 4], facing: 'north', colour: 'white' },
  { from: 2, kind: 'nightstand', part: 'nightstand', pos: [10, 1, 3], facing: 'south', wood: W },
  { from: 2, kind: 'lamp', part: 'hangingLamp', pos: [10, 5, 5], facing: 'north', chain: 1, wood: W },
  // III, Back room: two beds heads to the back wall either end, nightstands, a rug, a lamp
  { from: 3, kind: 'bed', part: 'bed', pos: [2, 1, 11], facing: 'south', colour: 'yellow' },
  { from: 3, kind: 'bed', part: 'bed', pos: [8, 1, 11], facing: 'south', colour: 'light_blue' },
  { from: 3, kind: 'nightstand', part: 'nightstand', pos: [3, 1, 12], facing: 'north', wood: W },
  { from: 3, kind: 'nightstand', part: 'nightstand', pos: [7, 1, 12], facing: 'north', wood: W },
  { from: 3, kind: 'rug', part: 'rug', cellsAbs: [[3, 1, 11], [4, 1, 11], [5, 1, 11], [6, 1, 11]], colour: 'yellow' },
  { from: 3, kind: 'lamp', part: 'hangingLamp', pos: [5, 5, 11], facing: 'north', chain: 1, wood: W },
  // IV, Store: casks two high along the east wall, shelves on the west, a workbench and a chest at the back, a
  // sacking runner from the ladder's foot, two lanterns hung from the hall's floor and one on the bench's side table
  ...[[8, -3, 2], [8, -2, 2], [8, -3, 3], [8, -2, 3], [8, -3, 4], [8, -3, 5]].map(pos => ({ from: 4, kind: 'fixture', part: 'pantry', pos, facing: 'north', block: 'barrel' })),
  ...[[2, -2, 2], [2, -2, 3], [3, -2, 8], [4, -2, 8]].map(pos => ({ from: 4, kind: 'shelf', part: 'shelf', pos, facing: pos[0] === 2 ? 'east' : 'north', wood: W })),
  { from: 4, kind: 'fixture', part: 'worktop', pos: [5, -3, 8], facing: 'north', block: 'crafting_table' },
  { from: 4, kind: 'lamp', part: 'tableLamp', pos: [6, -3, 8], facing: 'north', wood: W },
  { from: 4, kind: 'fixture', part: 'chest', pos: [7, -3, 8], facing: 'north', block: 'chest' },
  { from: 4, kind: 'rug', part: 'rug', cellsAbs: [[3, -3, 5], [4, -3, 5], [5, -3, 5], [5, -3, 6], [5, -3, 7]], colour: 'brown' },
  { from: 4, kind: 'lamp', part: 'hangingLamp', pos: [5, -1, 3], facing: 'north', wood: W },
  { from: 4, kind: 'lamp', part: 'hangingLamp', pos: [5, -1, 6], facing: 'north', wood: W },
]

// The walking line (I): step -> door -> hall; west to the fire and the box bed; east and up the flight to the chamber.
export const WALK = [
  [5, 0, 0], [5, 1, 1], [5, 1, 2], [5, 1, 3], [4, 1, 3], [4, 1, 4], [4, 1, 5], [4, 1, 6], [4, 1, 7], [3, 1, 7], // to the box room
  [5, 1, 3], [6, 1, 3], [6, 1, 6], [7, 1, 6], [7, 1, 7], // to the kitchen
  [6, 1, 3], [7, 1, 3], [7, 1, 2], [8, 1, 2], [8, 2, 3], [8, 3, 4], [8, 4, 5], [8, 5, 6], [8, 5, 7], [7, 5, 7], [7, 5, 6], [4, 5, 6], // up
]

export const OUTSIDE = {
  flowerBeds: [{ x0: 1, x1: 2, z: 0, plants: ['poppy', 'cornflower'] }, { x0: 8, x1: 9, z: 0, plants: ['oxeye_daisy', 'allium'] }],
}
