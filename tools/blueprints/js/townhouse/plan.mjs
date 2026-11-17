// Burgher's Townhouse (ROADMAP 49, brief tools/blueprints/briefs/townhouse.md): the plan, written before any block.
//
// THREE CONCEPTS
//  A. The Hanse Gable House. A tall, narrow brick house standing with its gable to the street, the gable stepped in
//     crow steps between proud brick piers, a hoist beam at its peak for the goods hauled up to the loft, a raised
//     front terrace with benches either side of the door (a "Beischlag"). Grows the way a merchant's house in a
//     walled town grows: back, not sideways: a brick-nogged timber back house with its own little stepped gable (II),
//     a stair-and-lookout tower in the angle of the yard with a spire over everything (III), a vaulted store dug under
//     the front house for the cellar trade (IV). Story: the burgher made his money in the warehouse at the top of his
//     own house, and the street front is his signboard.
//  B. The Jettied Row House. Ridge along the street, two oversailing timber jetties on carved brackets, a shop window
//     and a stall board on the ground floor, a long catslide behind. Story: a cloth-seller's house over his shop. Pretty,
//     but it reads as the Stone House and the Daub House turned richer (the same ridge to the street, the same frame),
//     a shop is the Burgher's job elsewhere, and the jetties eat the 15-wide plot.
//  C. The Corner Mansion. A squat two-storey block with a round corner turret and a hipped roof, a walled forecourt.
//     Story: a burgher who wants to be a noble. A hip roof and a forecourt make it read as a manor (the Noble's grade),
//     and a turret on a 15 x 16 plot is a toy.
//  Strongest: A. It is the only one with a face no other home in the mod has (a gable to the street, crow steps, the
//  hoist), it is honest to what a burgher was (trade, brick, a deep narrow plot), its height is its status on a small
//  plot, and every upgrade is a real addition a merchant family made: the back house for the children, the tower with
//  the counting room and the lookout over the harbour, the cellar under it all. Built: A.
//
// STORY: the merchant's gable. Street front: brick piers rising to white-capped crow steps, white lintels in rows,
// pots on a stone band, benches on the terrace, the hoist at the peak with its lantern. Behind, plainer: the yard side
// is where the family lives.
//
// PALETTE 60/30/10: 60 red brick (walls, gables, chimney); 30 the dark: deepslate tile roof, deepslate brick plinth
// and terrace (darkest at the foot), dark oak (doors, hoist beam, the back house's frame, barge boards), with spruce
// floors and furniture inside; 10 white polished diorite (lintels, string courses, step copings, pier caps) and the
// flowers in the pots.
export const LEVELS = [1, 2, 3, 4]
export const IDS = { 1: 'townhouse', 2: 'townhouse_2', 3: 'townhouse_3', 4: 'townhouse_4' }
export const SIZES = { 1: [11, 21, 14], 2: [15, 21, 22], 3: [15, 27, 22], 4: [15, 27, 22] }
export const BASEMENT = { 4: 4 }

export const PALETTE = {
  brick: 'bricks',
  plinth: [['deepslate_bricks', 4], ['cracked_deepslate_bricks', 1]],
  step: 'deepslate_brick_stairs',
  dressed: 'polished_diorite', band: 'polished_diorite_slab', sill: 'polished_diorite_stairs',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  timber: 'dark_oak', wood: 'spruce', floor: 'spruce_planks', partition: 'spruce_planks', glass: 'glass_pane',
  bench: 'dark_oak_stairs', corbel: 'brick_stairs', post: 'brick_wall', barge: 'dark_oak_trapdoor',
  cellarWall: [['deepslate_bricks', 3], ['cobbled_deepslate', 2]], cellarFloor: 'cobbled_deepslate',
}

// MASSES (template coordinates; the front is z = 0, north)
export const MASSES = {
  // The front house: brick walls x 1..9, z 2..12 on a deepslate plinth one block wider all round; floors at y 0, 5 and
  // 9 (the loft); a steep (2:1) roof, ridge along z at y 18 over x 5, eaves at y 9 over x 0 and x 10. The street gable
  // (z 2) stands above the tiles in crow steps; I's back gable is plain under a one-block overhang (z 13).
  main: { x0: 1, z0: 2, x1: 9, z1: 12, upperY: 5, loftY: 9, roof: { axis: 'z', across: [0, 10], eaveY: 9, steep: true } },
  // The crow steps: each gable column's top (its coping in diorite). Front: pairs out from the middle.
  frontSteps: { 1: 14, 2: 14, 3: 18, 4: 18, 5: 20, 6: 18, 7: 18, 8: 14, 9: 14 },
  // From II the back gable (z 12) is stepped too, carried across the back house's own little gable (x 10..13).
  backSteps: { 1: 14, 2: 14, 3: 18, 4: 18, 5: 20, 6: 18, 7: 18, 8: 17, 9: 17, 10: 17, 11: 17, 12: 14, 13: 14 },
  // proud brick piers on the street face (z 1), each capped in diorite one above its top: the corners from the
  // terrace, the middle pair framing the hoist bay from the first floor up, on a corbel (an upside-down brick stair)
  piers: [{ x: 1, from: 1, top: 14 }, { x: 4, from: 5, top: 18 }, { x: 6, from: 5, top: 18 }, { x: 9, from: 1, top: 14 }],
  // the terrace (Beischlag): z 1 a deepslate deck, z 0 a full-width step, lamp posts on the corners
  terrace: { x0: 1, x1: 9, benches: [[2, 3], [7, 8]], lamps: [1, 9] },
  // the west stack: a 2 x 2 brick stack (x 0..1, z 9..10) up the side wall past the eaves to y 18, two flues; the
  // kitchen hearth in its foot
  chimney: { x0: 0, x1: 1, z0: 9, z1: 10, top: 18 },
  hoist: { x: 5, beamY: 17 },
  // II: the back house, brick-nogged dark oak frame, walls x 7..13, z 12..20 (z 12 shared with the front house),
  // floors at y 0 and 5, steep roof ridge along z at y 16 over x 10, back overhang z 21
  wing: { x0: 7, z0: 12, x1: 13, z1: 20, roof: { axis: 'z', across: [6, 14], along: [12, 21], eaveY: 9, steep: true },
    posts: [[7, 16], [7, 20], [10, 20], [13, 20], [13, 16], [13, 12]] },
  // III: the tower in the yard's angle, walls x 3..7, z 12..16 (its north wall the front house's back gable, its east
  // wall the back house's west wall), floors at y 5, 9 and 13, a needle spire (eaves y 18, three up a ring) to a rod at y 26
  tower: { x0: 3, z0: 12, x1: 7, z1: 16, floors: [5, 9, 13], top: 18, rise: 3, ladder: { x: 6, z: 15, facing: 'west', y0: 1, top: 13 } },
  // IV: the store under the front house, 4 deep, its ladder up through a hatch in the kitchen
  cellar: { x0: 1, z0: 2, x1: 9, z1: 12, depth: 4, ladder: { x: 7, z: 11, facing: 'north' } },
}

export const ROOMS = [
  { level: 1, name: 'Hall', use: 'the front room: the dining table for four, the clock, the stair up', box: [2, 1, 3, 7, 4, 7] },
  { level: 1, name: 'Kitchen', use: 'the hearth in the stack, the stove, counters and crockery, open to the hall', box: [2, 1, 8, 8, 4, 11] },
  { level: 1, name: 'Parlour', use: 'first floor front: the settle, a card table in the window light, the loft stair', box: [2, 6, 3, 8, 8, 7] },
  { level: 1, name: 'Chamber', use: 'first floor back: the couple\'s two beds, nightstands, a cupboard, a chest', box: [2, 6, 9, 8, 8, 11] },
  { level: 1, name: 'Loft', use: 'the warehouse under the roof, behind the hoist: barrels, a chest, a shelf', box: [2, 10, 3, 8, 17, 11] },
  { level: 2, name: 'Garden room', use: 'the back house below: a bed for the journeyman, a table, the door to the yard', box: [8, 1, 13, 12, 4, 19] },
  { level: 2, name: 'Children\'s room', use: 'the back house above: two beds', box: [8, 6, 13, 12, 8, 19] },
  { level: 3, name: 'Counting room', use: 'the tower foot, off the kitchen: the desk in the window, books, the strongbox', box: [4, 1, 13, 6, 4, 15] },
  { level: 3, name: 'Study', use: 'the tower\'s first floor: a reading chair', box: [4, 6, 13, 6, 8, 15] },
  { level: 3, name: 'Map room', use: 'the tower\'s second floor: a chart table', box: [4, 10, 13, 6, 12, 15] },
  { level: 3, name: 'Lookout', use: 'the tower top under the spire, windows three ways', box: [4, 14, 13, 6, 19, 15] },
  { level: 4, name: 'Store', use: 'the cellar under the front house: casks, chests, a bench', box: [2, -3, 3, 8, -1, 11] },
]

export const OPENINGS = {
  frontDoor: { at: [5, 1, 2], facing: 'south' },
  // the hall stair: up the east wall rising north (to the front), landing at (8, 6, 5)
  stairs: { flight: [[8, 1, 9], [8, 2, 8], [8, 3, 7], [8, 4, 6], [8, 5, 5]], rises: 'north', well: [[8, 5, 6], [8, 5, 7]], rail: [[7, 6, 6], [7, 6, 7]] },
  // the loft stair: up the parlour's west wall rising south, landing at (2, 10, 7)
  loftStairs: { flight: [[2, 6, 4], [2, 7, 5], [2, 8, 6], [2, 9, 7]], rises: 'south', base: 6, well: [[2, 9, 5], [2, 9, 6]], rail: [[2, 10, 4], [3, 10, 4], [3, 10, 6]] },
  // the chamber's partition (z 8, x 2..8) and its door
  partition: { z: 8, x0: 2, x1: 8, door: [4, 6, 8] },
  // II: the back house's stair, up its east wall rising south, landing at (12, 6, 18)
  wingStairs: { flight: [[12, 1, 14], [12, 2, 15], [12, 3, 16], [12, 4, 17], [12, 5, 18]], rises: 'south', well: [[12, 5, 16], [12, 5, 17]], rail: [[12, 6, 15], [11, 6, 15], [11, 6, 17]] },
  wingDoor: { at: [7, 1, 18], facing: 'east' },
  wingDoorway: { at: [8, 1, 12], level: 2 }, // I's kitchen back window by the stair foot becomes the way in
  towerDoorway: { at: [5, 1, 12], level: 3 }, // I's other kitchen back window becomes the tower's door
  // windows: cells, the way out, dress ('sill' ground floor, on the plinth; 'band' first floor, pots on the course)
  windows: [
    // street front (z 2)
    { name: 'hall front west', cells: [[2, 2, 2], [3, 2, 2], [2, 3, 2], [3, 3, 2]], out: 'north' },
    { name: 'hall front east', cells: [[7, 2, 2], [8, 2, 2], [7, 3, 2], [8, 3, 2]], out: 'north' },
    { name: 'parlour front west', cells: [[2, 6, 2], [3, 6, 2], [2, 7, 2], [3, 7, 2]], out: 'north', pots: ['potted_red_tulip', 'potted_red_tulip'] },
    { name: 'parlour front middle', cells: [[5, 6, 2], [5, 7, 2]], out: 'north', pots: ['potted_oxeye_daisy'] },
    { name: 'parlour front east', cells: [[7, 6, 2], [8, 6, 2], [7, 7, 2], [8, 7, 2]], out: 'north', pots: ['potted_red_tulip', 'potted_red_tulip'] },
    { name: 'loft front west', cells: [[3, 10, 2], [3, 11, 2]], out: 'north' },
    { name: 'loft front middle', cells: [[5, 10, 2], [5, 11, 2]], out: 'north' },
    { name: 'loft front east', cells: [[7, 10, 2], [7, 11, 2]], out: 'north' },
    { name: 'hoist', cells: [[5, 14, 2], [5, 15, 2]], out: 'north' },
    // west side (x 1)
    { name: 'hall west', cells: [[1, 2, 4], [1, 3, 4]], out: 'west', dress: 'sill', pots: ['potted_cornflower'] },
    { name: 'kitchen west', cells: [[1, 2, 11], [1, 3, 11]], out: 'west', dress: 'sill', pots: ['potted_fern'] },
    { name: 'parlour west', cells: [[1, 6, 4], [1, 7, 4]], out: 'west', pots: ['potted_red_tulip'] },
    { name: 'chamber west', cells: [[1, 6, 11], [1, 7, 11]], out: 'west', pots: ['potted_allium'] },
    // east side (x 9)
    { name: 'hall east', cells: [[9, 2, 3], [9, 3, 3]], out: 'east', dress: 'sill', pots: ['potted_red_tulip'] },
    { name: 'kitchen east', cells: [[9, 2, 10], [9, 3, 10]], out: 'east', dress: 'sill', pots: ['potted_oxeye_daisy'] },
    { name: 'parlour east', cells: [[9, 6, 3], [9, 7, 3]], out: 'east', pots: ['potted_cornflower'] },
    { name: 'chamber east', cells: [[9, 6, 10], [9, 7, 10]], out: 'east', pots: ['potted_red_tulip'] },
    // back (z 12)
    { name: 'kitchen back tower', cells: [[5, 2, 12], [5, 3, 12]], out: 'south', dress: 'sill', until: 2 },
    { name: 'kitchen back wing', cells: [[8, 2, 12], [8, 3, 12]], out: 'south', dress: 'sill', until: 1 },
    { name: 'chamber back', cells: [[2, 6, 12], [2, 7, 12]], out: 'south' },
    // the back gable's middle column, stacked over the kitchen's (the tower covers it from III)
    { name: 'chamber back middle', cells: [[5, 6, 12], [5, 7, 12]], out: 'south', pots: ['potted_red_tulip'], until: 2 },
    { name: 'loft back middle', cells: [[5, 10, 12], [5, 11, 12]], out: 'south', until: 2 },
    { name: 'gable back', cells: [[5, 14, 12], [5, 15, 12]], out: 'south', until: 2 },
    { name: 'loft back', cells: [[2, 10, 12], [2, 11, 12]], out: 'south' },
    // II, the back house: east (x 13), back (z 20), west (x 7) over the yard door
    { name: 'garden room east', cells: [[13, 2, 14], [13, 3, 14]], out: 'east', dress: 'sill', from: 2 },
    { name: 'garden room east 2', cells: [[13, 2, 18], [13, 3, 18]], out: 'east', dress: 'sill', from: 2 },
    { name: 'garden room back', cells: [[8, 2, 20], [9, 2, 20], [8, 3, 20], [9, 3, 20]], out: 'south', dress: 'sill', from: 2 },
    { name: 'garden room back 2', cells: [[11, 2, 20], [12, 2, 20], [11, 3, 20], [12, 3, 20]], out: 'south', dress: 'sill', from: 2 },
    { name: 'children east', cells: [[13, 6, 14], [13, 7, 14]], out: 'east', from: 2 },
    { name: 'children east 2', cells: [[13, 6, 18], [13, 7, 18]], out: 'east', from: 2 },
    { name: 'children back', cells: [[8, 6, 20], [9, 6, 20], [8, 7, 20], [9, 7, 20]], out: 'south', from: 2 },
    { name: 'children back 2', cells: [[11, 6, 20], [12, 6, 20], [11, 7, 20], [12, 7, 20]], out: 'south', from: 2 },
    { name: 'children west', cells: [[7, 6, 18], [7, 7, 18]], out: 'west', from: 2 },
    // III, the tower: west (x 3) and south (z 16) a window a floor, the lookout glazed three ways
    { name: 'counting room west', cells: [[3, 2, 14], [3, 3, 14]], out: 'west', dress: 'sill', from: 3 },
    { name: 'counting room south', cells: [[5, 2, 16], [5, 3, 16]], out: 'south', dress: 'sill', from: 3 },
    { name: 'study west', cells: [[3, 6, 14], [3, 7, 14]], out: 'west', from: 3 },
    { name: 'study south', cells: [[5, 6, 16], [5, 7, 16]], out: 'south', from: 3 },
    { name: 'map room west', cells: [[3, 10, 14], [3, 11, 14]], out: 'west', from: 3 },
    { name: 'map room south', cells: [[5, 10, 16], [5, 11, 16]], out: 'south', from: 3 },
    { name: 'lookout west', cells: [[3, 15, 13], [3, 15, 14], [3, 15, 15], [3, 16, 13], [3, 16, 14], [3, 16, 15]], out: 'west', from: 3 },
    { name: 'lookout south', cells: [[4, 15, 16], [5, 15, 16], [6, 15, 16], [4, 16, 16], [5, 16, 16], [6, 16, 16]], out: 'south', from: 3 },
    { name: 'lookout east', cells: [[7, 15, 13], [7, 15, 14], [7, 15, 15], [7, 16, 13], [7, 16, 14], [7, 16, 15]], out: 'east', from: 3 },
  ],
}

// Every furniture piece, footprint first (from/until: the levels that have it). Facing: the way its user faces.
const W = 'spruce'
const blinds = (cells, facing, extra = {}) => cells.map(pos => ({ ...extra, kind: 'blinds', part: 'blinds', pos, facing, wood: W }))
export const FURNITURE = [
  // Hall (z 3..7). The walking line: the door (5, 1, 2) straight in down x 5 to the kitchen, the stair foot at
  // (8, 1, 10). The table for four on the west side under its lantern, the clock against the stair's side.
  { kind: 'table', part: 'table', pos: [3, 1, 5], facing: 'north', wood: W, colour: 'white' },
  { kind: 'table', part: 'table', pos: [3, 1, 6], facing: 'north', wood: W, colour: 'white' },
  { kind: 'chair', part: 'chair', pos: [2, 1, 5], facing: 'east', wood: W },
  { kind: 'chair', part: 'chair', pos: [2, 1, 6], facing: 'east', wood: W },
  { kind: 'chair', part: 'chair', pos: [4, 1, 5], facing: 'west', wood: W },
  { kind: 'chair', part: 'chair', pos: [4, 1, 6], facing: 'west', wood: W },
  { kind: 'clock', part: 'clock', pos: [7, 1, 6], facing: 'west', wood: W },
  { kind: 'picture', part: 'picture', pos: [2, 3, 7], facing: 'east', wood: W, colour: 'red' },
  { kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 5], facing: 'north', wood: W },
  { kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 4], facing: 'north', wood: W },
  ...blinds([[2, 4, 3], [3, 4, 3], [7, 4, 3], [8, 4, 3]], 'south'),
  ...blinds([[2, 4, 4]], 'east'),
  // Kitchen (z 8..11): the hearth in the stack's foot, the stove beside it, counters along the back with crockery,
  // the barrel and the chest with shelves of crockery over them; (5, 1, 11), (7, 1, 11) and (8, 1, 11) clear (III's
  // tower door, IV's hatch, II's way into the back house)
  { kind: 'fixture', part: 'hearth', pos: [1, 1, 9], facing: 'east' },
  { kind: 'fixture', part: 'stove', pos: [2, 1, 10], facing: 'east', block: 'furnace' },
  { kind: 'counter', part: 'counter', pos: [2, 1, 11], facing: 'north', wood: W, topSlab: true },
  { kind: 'counter', part: 'counter', pos: [3, 1, 11], facing: 'north', wood: W, topSlab: true },
  { kind: 'crockery', part: 'plates', pos: [3, 3, 11], facing: 'north', wood: W, block: 'flower_pot' },
  { kind: 'fixture', part: 'pantry', pos: [4, 1, 11], facing: 'north', block: 'barrel' },
  { kind: 'shelf', part: 'shelf', pos: [4, 3, 11], facing: 'north', wood: W, crockery: 'flower_pot' },
  { kind: 'fixture', part: 'chest', pos: [6, 1, 11], facing: 'north', block: 'chest' },
  { kind: 'shelf', part: 'shelf', pos: [6, 3, 11], facing: 'north', wood: W, crockery: 'decorated_pot' },
  { kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 9], facing: 'north', wood: W },
  ...blinds([[2, 4, 11]], 'east'), ...blinds([[8, 4, 10]], 'west'),
  ...blinds([[5, 4, 11]], 'north', { until: 2 }), ...blinds([[8, 4, 11]], 'north', { until: 1 }),
  // Parlour (first floor front, z 3..7): up the hall stair at (8, 6, 5); the card table in the front windows' light
  // with a chair each side, the settle against the partition facing it across a rug, the loft stair up the west wall.
  { kind: 'table', part: 'table', pos: [6, 6, 4], facing: 'north', wood: W, colour: 'green' },
  { kind: 'chair', part: 'chair', pos: [5, 6, 4], facing: 'east', wood: W },
  { kind: 'chair', part: 'chair', pos: [7, 6, 4], facing: 'west', wood: W },
  { kind: 'couch', part: 'couch', pos: [5, 6, 7], facing: 'north', length: 2, wood: W, colour: 'green' },
  { kind: 'rug', part: 'rug', cellsAbs: [[5, 6, 6], [6, 6, 6], [5, 6, 5], [6, 6, 5]], colour: 'green' },
  { kind: 'picture', part: 'picture', pos: [4, 7, 3], facing: 'south', wood: W, colour: 'blue' },
  { kind: 'lamp', part: 'hangingLamp', pos: [5, 8, 5], facing: 'north', wood: W },
  ...blinds([[2, 8, 3], [3, 8, 3], [5, 8, 3], [7, 8, 3], [8, 8, 3]], 'south'),
  // Chamber (first floor back, z 9..11): through the partition door (4, 6, 8); the two beds heads to the back wall,
  // a nightstand each side, the cupboard on the west wall, the chest, the picture over the beds.
  { kind: 'bed', part: 'bed', pos: [6, 6, 10], facing: 'south', colour: 'red' },
  { kind: 'bed', part: 'bed', pos: [7, 6, 10], facing: 'south', colour: 'red' },
  { kind: 'nightstand', part: 'nightstand', pos: [5, 6, 11], facing: 'north', wood: W },
  { kind: 'nightstand', part: 'nightstand', pos: [8, 6, 11], facing: 'north', wood: W },
  { kind: 'cupboard', part: 'cupboard', pos: [2, 6, 9], facing: 'east', wood: W },
  { kind: 'cupboard', part: 'cupboard', pos: [2, 6, 10], facing: 'east', wood: W },
  { kind: 'fixture', part: 'chest', pos: [3, 6, 11], facing: 'north', block: 'chest' },
  { kind: 'picture', part: 'picture', pos: [6, 8, 11], facing: 'north', wood: W, colour: 'white' },
  { kind: 'lamp', part: 'hangingLamp', pos: [4, 8, 10], facing: 'north', wood: W },
  ...blinds([[2, 8, 11]], 'north'), ...blinds([[8, 8, 10]], 'west'),
  // Loft (y 10 up): the warehouse behind the hoist. Barrels either side of the hoist window, a chest and a shelf at
  // the back, two lanterns on chains from the ridge.
  { kind: 'fixture', part: 'pantry', pos: [4, 10, 3], facing: 'north', block: 'barrel' },
  { kind: 'fixture', part: 'pantry', pos: [6, 10, 3], facing: 'north', block: 'barrel' },
  { kind: 'fixture', part: 'pantry', pos: [6, 11, 3], facing: 'north', block: 'barrel' },
  { kind: 'fixture', part: 'chest', pos: [7, 10, 11], facing: 'north', block: 'chest' },
  { kind: 'shelf', part: 'shelf', pos: [4, 11, 11], facing: 'north', wood: W },
  ...[[3, 10, 9], [3, 10, 10], [3, 11, 10], [3, 10, 11]].map(pos => ({ kind: 'fixture', part: 'pantry', pos, facing: 'north', block: 'barrel' })),
  { kind: 'lamp', part: 'hangingLamp', pos: [5, 13, 5], facing: 'north', chain: 4, wood: W },
  { kind: 'lamp', part: 'hangingLamp', pos: [5, 13, 9], facing: 'north', chain: 4, wood: W },
  ...blinds([[3, 12, 3], [5, 12, 3], [7, 12, 3]], 'south'),
  // II, Garden room (back house below): in from the kitchen at (8, 1, 13); the journeyman's bed head to the west
  // wall, its nightstand, a table with two chairs by the yard door, the stair up the east wall.
  { from: 2, kind: 'bed', part: 'bed', pos: [9, 1, 14], facing: 'west', colour: 'brown' },
  { from: 2, kind: 'nightstand', part: 'nightstand', pos: [8, 1, 15], facing: 'east', wood: W },
  { from: 2, kind: 'table', part: 'table', pos: [10, 1, 17], facing: 'north', wood: W, colour: 'white' },
  { from: 2, kind: 'chair', part: 'chair', pos: [10, 1, 16], facing: 'south', wood: W },
  { from: 2, kind: 'chair', part: 'chair', pos: [11, 1, 17], facing: 'west', wood: W },
  { from: 2, kind: 'picture', part: 'picture', pos: [10, 3, 19], facing: 'north', wood: W, colour: 'yellow' },
  { from: 2, kind: 'lamp', part: 'hangingLamp', pos: [10, 4, 15], facing: 'north', wood: W },
  ...blinds([[8, 4, 19], [9, 4, 19], [11, 4, 19], [12, 4, 19]], 'north', { from: 2 }),
  // II, Children's room (back house above): two beds heads to the back wall, a nightstand between, a chest, a rug
  { from: 2, kind: 'bed', part: 'bed', pos: [8, 6, 18], facing: 'south', colour: 'light_blue' },
  { from: 2, kind: 'bed', part: 'bed', pos: [10, 6, 18], facing: 'south', colour: 'yellow' },
  { from: 2, kind: 'nightstand', part: 'nightstand', pos: [9, 6, 19], facing: 'north', wood: W },
  { from: 2, kind: 'fixture', part: 'chest', pos: [8, 6, 13], facing: 'east', block: 'chest' },
  { from: 2, kind: 'rug', part: 'rug', cellsAbs: [[9, 6, 16], [10, 6, 16], [9, 6, 15], [10, 6, 15]], colour: 'light_blue' },
  { from: 2, kind: 'picture', part: 'picture', pos: [8, 8, 16], facing: 'east', wood: W, colour: 'light_blue' },
  { from: 2, kind: 'lamp', part: 'hangingLamp', pos: [9, 8, 16], facing: 'north', wood: W },
  // III, Counting room (tower foot): the desk under the west window with its chair, books, the strongbox
  { from: 3, kind: 'desk', part: 'desk', pos: [4, 1, 14], facing: 'west', wood: W },
  { from: 3, kind: 'chair', part: 'chair', pos: [5, 1, 14], facing: 'west', wood: W },
  { from: 3, kind: 'cupboard', part: 'cupboard', pos: [4, 1, 15], facing: 'east', wood: W },
  { from: 3, kind: 'fixture', part: 'chest', pos: [4, 1, 13], facing: 'east', block: 'chest' },
  { from: 3, kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 14], facing: 'north', wood: W },
  // III, Study: a reading chair, its lamp, a bookcase
  { from: 3, kind: 'chair', part: 'armchair', pos: [4, 6, 14], facing: 'east', wood: 'dark_oak', colour: 'red' },
  { from: 3, kind: 'lamp', part: 'tableLamp', pos: [5, 6, 15], facing: 'north', wood: W },
  { from: 3, kind: 'cupboard', part: 'cupboard', pos: [4, 6, 15], facing: 'east', wood: W },
  { from: 3, kind: 'rug', part: 'rug', cellsAbs: [[5, 6, 13], [5, 6, 14]], colour: 'red' },
  // III, Map room: a chart table and two chairs
  { from: 3, kind: 'table', part: 'table', pos: [5, 10, 14], facing: 'north', wood: W, colour: 'blue' },
  { from: 3, kind: 'chair', part: 'chair', pos: [4, 10, 14], facing: 'east', wood: W },
  { from: 3, kind: 'chair', part: 'chair', pos: [5, 10, 13], facing: 'south', wood: W },
  { from: 3, kind: 'lamp', part: 'hangingLamp', pos: [5, 12, 15], facing: 'north', wood: W },
  // III, Lookout: a settle under the north wall facing the south glass, a lantern from the spire
  { from: 3, kind: 'couch', part: 'couch', pos: [5, 14, 13], facing: 'south', length: 2, wood: W, colour: 'blue' },
  { from: 3, kind: 'lamp', part: 'hangingLamp', pos: [5, 17, 14], facing: 'north', chain: 7, wood: W },
  // IV, Store: casks two high down the west wall, more on the east, a bench and a chest at the front, lanterns hung
  // from the hall floor
  ...[[2, -3, 4], [2, -2, 4], [2, -3, 5], [2, -2, 5], [2, -3, 6], [2, -2, 6], [2, -3, 7], [8, -3, 4], [8, -2, 4], [8, -3, 5], [8, -2, 5], [8, -3, 6], [8, -3, 7]]
    .map(pos => ({ from: 4, kind: 'fixture', part: 'pantry', pos, facing: 'north', block: 'barrel' })),
  { from: 4, kind: 'fixture', part: 'worktop', pos: [4, -3, 3], facing: 'north', block: 'crafting_table' },
  { from: 4, kind: 'lamp', part: 'tableLamp', pos: [5, -3, 3], facing: 'north', wood: W },
  { from: 4, kind: 'fixture', part: 'chest', pos: [6, -3, 3], facing: 'south', block: 'chest' },
  ...[[2, -2, 9], [2, -2, 10]].map(pos => ({ from: 4, kind: 'shelf', part: 'shelf', pos, facing: 'east', wood: W })),
  { from: 4, kind: 'rug', part: 'rug', cellsAbs: [[5, -3, 5], [5, -3, 6], [5, -3, 7], [5, -3, 8], [5, -3, 9]], colour: 'brown' },
  { from: 4, kind: 'lamp', part: 'hangingLamp', pos: [5, -1, 5], facing: 'north', wood: W },
  { from: 4, kind: 'lamp', part: 'hangingLamp', pos: [5, -1, 9], facing: 'north', wood: W },
]

// The walking line (I): terrace -> door -> hall down x 5 -> kitchen; the stair foot (8, 1, 10) up to the parlour,
// across z 3 to the loft stair (2, 6, 3) and up; or down x 4 through the partition door to the chamber.
export const WALK = [
  [5, 0, 0], [5, 1, 1], [5, 1, 2], [5, 1, 3], [5, 1, 8], [5, 1, 10], [7, 1, 10], [8, 1, 10],
  [8, 1, 9], [8, 2, 8], [8, 3, 7], [8, 4, 6], [8, 5, 5], [8, 6, 4], [8, 6, 3], [4, 6, 3], [4, 6, 7], [4, 6, 8], [4, 6, 9],
  [3, 6, 3], [2, 6, 3], [2, 6, 4], [2, 7, 5], [2, 8, 6], [2, 9, 7], [2, 10, 8],
]

// Goods in the loft (not furniture): bales of cloth stacked under the east slope, the burgher's trade
export const GOODS = [[7, 10, 6, 'white_wool'], [7, 10, 7, 'light_gray_wool'], [7, 11, 6, 'light_gray_wool'], [7, 10, 8, 'brown_wool']]

export const OUTSIDE = {
  yardBed: { x0: 1, x1: 5, z: 21, plants: ['red_tulip', 'oxeye_daisy', 'cornflower', 'allium'] }, // II: the yard's back edge
  yardLamp: [5, 0, 19],
}
