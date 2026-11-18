// Inn (Version A), the plan: drawn before any wall. Rooms, every furniture footprint (kind, cells, facing) and the
// walking line come first; recipe.mjs then assembles the three levels from the parts library (../parts) to fit it.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   1. "The Jetty and the Coach Yard": a stone taproom under a timber guest storey jettied out over the street on
//      brackets, one big slate street gable framed in proud timber; a stone outshot under one catslide across the
//      back (the guests' turning stair, the scullery), a flank chimney up the west side. II opens a coach yard to the east: a gallery off
//      the guest floor with its own stair down, a stable range, a gate to the street. III digs the ale cellar under
//      the bar. Story: a market-street inn that grew the way coaching inns did, up over the street, then back round a
//      yard. Plan: taproom with the bar east and the hearth west; two three-bed chambers over it off a back passage.
//   2. "The Long Hall": one long low hall-house with its eaves to the street, a catslide over a rear outshot and
//      three dormers for the guest rooms; II adds a cross-wing. Story: the old manor's hall turned public house.
//      Plan: hall and screens passage, bedrooms in the roof.
//   3. "The Corner Tower": a square stone tower-house at the plot's corner with a timber wing; guests sleep in the
//      tower. Story: a toll keeper's tower that started selling ale. Plan: bar in the wing, beds stacked in the tower.
// Strongest: 1. It reads as an inn from the street at once (the jetty, the gable, the sign post, travellers' bench
// light under the overhang), every part has a job a player can name (Taproom, Bar, Hearth, Stair Outshot, Scullery,
// Chambers), and it has a real growth story: the gallery and stable yard of II are what every coaching inn added. 2
// is safe but flat (beds in dormers under one long roof) and 3 can't hold six beds on one floor in 13 x 13. Built: 1.
//
// Coordinates: x across the front (west 0 -> east 12), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, the street) to the back. The front door is at (6, 1, 2), the walk-in cell behind the counter at
// (10, 1, 6), the guest floor's beds at y 6 (the counter's y 1 + 5).
//
// Named parts (the masses):
//   Taproom      stone ground storey x 1..11, z 2..9, y 0..4 (inside x 2..10, z 3..8, 9 x 6): the bar on its east side,
//                the hearth in its west wall, a long table between them.
//   Jetty        the guest storey oversails the street a block (front wall z 1) on a beam at y 5, carved brackets
//                (upside-down stairs) under it at the posts, a moulding of upside-down stairs along its foot.
//   Chambers     the timber guest storey x 1..11, z 1..9, walls y 6..8, a boarded ceiling at y 9: two three-bed
//                chambers (west, east) off a back passage (z 8).
//   Roof         one 45-degree slate roof, ridge along z (the street gable), eaves at y 8 on x 0 and 12, ridge y 14,
//                a block of overhang at the front (z 0) and the back (z 10).
//   Gables       front and back alike: dark logs on the wall line (posts at x 4 and 8, a collar at y 12, a king
//                post) round a two-high window with a sill, the plaster panels set a block behind the frame.
//   Outshot      one stone lean-to across the back (x 0..12 roofed, z 10..12, a catslide at y 5..7 under the main
//                roof's back overhang): the Stair Outshot (x 1..5: the stair turns inside it up through the back
//                wall onto the passage), the woodpile nook (x 6), the Scullery (x 7..11: smoker, sink, counter).
//   Chimney      a stone stack up the west flank (x 0, z 5) from the hearth past the ridge.
//   Sign post    the red banner on a fence post at the street corner, rotation 8 (facing the street).
// Level II adds the Coach Yard east of the inn (x 12..26): the Gallery (a deck along the inn's east wall at y 5, door
// from the east chamber, a stair down into the yard), the Stable (x 19..25, z 3..12, stalls open to the yard, a hay
// loft and its door on the street gable) and the Gate (piers and a beam at the street).
// Level III adds the Ale Cellar under the taproom (4 deep), down a ladder behind the bar through a hatch.

export const IDS = { 1: 'inn', 2: 'inn_2', 3: 'inn_3' }
export const SIZES = { 1: [13, 16, 13], 2: [27, 16, 13], 3: [27, 16, 13] }
export const BASEMENT = { 3: 4 }

// Palette (60/30/10): cream limewash infill and grey stone (walls) / dark oak frame and slate roof / spruce joinery,
// red cloth. Why: a pale upper storey over a dark stone base reads as two storeys of different jobs (public taproom,
// private chambers); the slate roof contrasts with the cream; the dark oak frame matches the bar's dark oak planks the
// brief pins, so the bar reads as part of the house's own timber.
export const PALETTE = {
  stone: [['stone_bricks', 7], ['cobblestone', 2], ['andesite', 1], ['cracked_stone_bricks', 1]],
  plinth: [['cobblestone', 5], ['mossy_cobblestone', 2], ['stone', 2]],
  quoin: 'polished_andesite',
  frame: 'dark_oak_log',
  infill: [['white_concrete', 3], ['polished_diorite', 1]],
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  trim: 'dark_oak', // brackets, moulding, barge boards (stairs and slabs of it)
  bar: 'dark_oak_planks',
  floor: 'spruce_planks',
  ceiling: 'spruce_planks',
  flags: 'stone_bricks', // the scullery and tower floors
  apron: 'polished_andesite', // the paving under the jetty
  wood: 'spruce', // doors, trapdoors, stairs as seats, fences, slabs
  glass: 'glass_pane',
  yard: [['cobblestone', 4], ['coarse_dirt', 2], ['andesite', 2], ['packed_mud', 1]],
  hay: 'hay_block',
}

export const MASSES = {
  taproom: { walls: { x0: 1, x1: 11, z0: 2, z1: 9 }, room: { x0: 2, x1: 10, z0: 3, z1: 8 }, top: 4 },
  chambers: { walls: { x0: 1, x1: 11, z0: 1, z1: 9 }, room: { x0: 2, x1: 10, z0: 2, z1: 8 }, floorY: 5, top: 8, ceilingY: 9,
    frontPosts: [1, 5, 7, 11], backPosts: [1, 4, 6, 11], sidePosts: [1, 5, 9] },
  roof: { axis: 'z', across: [0, 12], along: [0, 10], eaveY: 8, steep: false },
  tower: { walls: { x0: 1, x1: 6, z0: 9, z1: 11 }, room: { x0: 2, x1: 5, z0: 10, z1: 10 } }, // the stair outshot
  scullery: { walls: { x0: 6, x1: 11, z0: 9, z1: 11 }, room: { x0: 7, x1: 10, z0: 10, z1: 10 }, top: 5 }, // both under one catslide (y 5..7), its eave a block out at z 12
  chimney: { x: 0, z: 7, top: 14 },
  dormers: [{ z: 3, face: 1, eave: 0 }, { z: 4, face: 11, eave: 12 }], // west clear of the chimney; II: the east one is the gallery door
  // Level II
  yard: { x0: 12, x1: 18, z0: 0, z1: 12 },
  gallery: { x0: 12, x1: 13, z0: 1, z1: 9, y: 5, posts: [1, 5, 9],
    stair: { steps: [[15, 1, 4], [15, 2, 5], [15, 3, 6], [15, 4, 7], [15, 5, 8]], landing: [14, 5, 8] } },
  stable: { walls: { x0: 19, x1: 25, z0: 3, z1: 12 }, room: { x0: 20, x1: 24, z0: 4, z1: 11 }, loftY: 4,
    roof: { axis: 'z', across: [18, 26], along: [3, 12], eaveY: 5, steep: false }, stallPosts: [3, 6, 9, 12] },
  gate: { piers: [14, 18], z: 1, beamY: 4 },
  // Level III
  cellar: { walls: { x0: 1, x1: 11, z0: 2, z1: 9 }, depth: 4 },
}

export const ROOMS = [
  { name: 'Taproom', use: 'the bar, the hearth, the long table', box: [2, 1, 3, 10, 4, 8] },
  { name: 'Bar', use: "the Innkeeper's walk-in behind the Shop Counter, the back bar", box: [10, 1, 3, 10, 2, 8] },
  { name: 'Scullery', use: 'cooking and washing up', box: [8, 1, 10, 10, 3, 11] },
  { name: 'Stair Tower', use: 'the guests\' way up (the stair outshot; the room keeps its old name)', box: [2, 1, 10, 4, 5, 11] },
  { name: 'West Chamber', use: 'three beds', box: [2, 6, 2, 5, 8, 6] },
  { name: 'East Chamber', use: 'three beds (II: the gallery door)', box: [7, 6, 2, 10, 8, 6] },
  { name: 'Passage', use: 'the landing from the tower, the chamber doors', box: [2, 6, 8, 10, 8, 8] },
  { name: 'Ale Cellar', use: 'casks (III)', box: [2, -3, 3, 10, -1, 8], level: 3 },
]

export const OPENINGS = {
  frontDoor: { at: [6, 1, 2], facing: 'south', step: [6, 0, 0] },
  towerDoor: { at: [2, 1, 9], height: 3 },
  sculleryDoor: { at: [8, 1, 9], height: 2 },
  backDoor: { at: [9, 1, 11], facing: 'north', step: [9, 0, 12] },
  passageDoor: { at: [5, 6, 9], height: 2 }, // the tower's top step comes through here
  chamberDoors: [{ at: [3, 6, 7], facing: 'north' }, { at: [9, 6, 7], facing: 'north' }],
  galleryDoor: { at: [11, 6, 4], facing: 'west', level: 2 },
  // the stair in the tower: [x, y, z, rises]
  towerStair: [[2, 1, 10, 'east'], [3, 2, 10, 'east'], [4, 3, 10, 'east'], [5, 4, 10, 'east'], [5, 5, 9, 'north']],
  windows: [
    // ground (stone): sills with pots under the jetty
    { name: 'taproom front west', cells: [[3, 2, 2], [4, 2, 2], [3, 3, 2], [4, 3, 2]], out: 'north', dress: 'sill', pots: ['potted_red_tulip', 'potted_cornflower'] },
    { name: 'taproom front east', cells: [[8, 2, 2], [9, 2, 2], [8, 3, 2], [9, 3, 2]], out: 'north', dress: 'sill', pots: ['potted_cornflower', 'potted_red_tulip'] },
    { name: 'taproom west north', cells: [[1, 2, 3], [1, 3, 3]], out: 'west', dress: 'sill', pots: ['potted_red_tulip'] },
    { name: 'taproom west south', cells: [[1, 2, 5], [1, 3, 5]], out: 'west', dress: 'sill', pots: ['potted_cornflower'] },
    { name: 'bar east', cells: [[11, 2, 4], [11, 3, 4]], out: 'east', dress: 'sill', pots: ['potted_red_tulip'] },
    { name: 'bar east south', cells: [[11, 2, 7], [11, 3, 7]], out: 'east', dress: 'sill', pots: ['potted_cornflower'] },
    // the outshot: sills with pots on a skirt round the back
    { name: 'scullery back', cells: [[7, 2, 11], [7, 3, 11]], out: 'south', dress: 'sill', pots: ['potted_red_tulip'] },
    { name: 'scullery east', cells: [[11, 2, 10], [11, 3, 10]], out: 'east', dress: 'sill', pots: ['potted_oxeye_daisy'] },
    { name: 'tower west low', cells: [[1, 2, 10], [1, 3, 10]], out: 'west', dress: 'sill', pots: ['potted_oxeye_daisy'] },
    { name: 'tower back', cells: [[3, 2, 11], [3, 3, 11]], out: 'south', dress: 'sill', pots: ['potted_cornflower'] },
    // guest storey (timber): shutters standing on the jetty's moulding at the front
    { name: 'west chamber front', cells: [[2, 6, 1], [3, 6, 1], [4, 6, 1], [2, 7, 1], [3, 7, 1], [4, 7, 1]], out: 'north', dress: 'shutters' },
    { name: 'east chamber front', cells: [[8, 6, 1], [9, 6, 1], [10, 6, 1], [8, 7, 1], [9, 7, 1], [10, 7, 1]], out: 'north', dress: 'shutters' },
    { name: 'west chamber side', cells: [[1, 6, 6], [1, 7, 6]], out: 'west', dress: null },
    { name: 'east chamber side', cells: [[11, 6, 6], [11, 7, 6]], out: 'east', dress: null },
    { name: 'passage east', cells: [[11, 6, 8], [11, 7, 8]], out: 'east', dress: null },
    { name: 'passage back', cells: [[8, 8, 9], [9, 8, 9]], out: 'south', dress: null }, // over the catslide
    { name: 'passage west', cells: [[1, 6, 8], [1, 7, 8]], out: 'west', dress: null },
  ],
}

const W = 'spruce'
// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a spot) or a fixture (a vanilla
// block the game uses as itself). level: the level that adds it (default 1). cells relative to pos.
export const FURNITURE = [
  // Taproom: a settle facing the hearth, a long table for four, a clock by the tower door, the inn's picture
  { room: 'Taproom', kind: 'couch', part: 'couch', pos: [3, 1, 8], facing: 'west', length: 3, wood: W, colour: 'red' },
  { room: 'Taproom', kind: 'rug', part: 'rug', cellsAbs: [[2, 1, 6], [2, 1, 7], [2, 1, 8]], colour: 'red' },
  { room: 'Taproom', kind: 'fixture', part: 'hearth', pos: [1, 1, 7], facing: 'east' },
  { room: 'Taproom', kind: 'table', part: 'table', pos: [6, 1, 6], facing: 'north', wood: W, colour: 'white' },
  { room: 'Taproom', kind: 'table', part: 'table', pos: [6, 1, 7], facing: 'north', wood: W, colour: 'white' },
  { room: 'Taproom', kind: 'chair', part: 'chair', pos: [5, 1, 6], facing: 'east', wood: W },
  { room: 'Taproom', kind: 'chair', part: 'chair', pos: [5, 1, 7], facing: 'east', wood: W },
  { room: 'Taproom', kind: 'chair', part: 'chair', pos: [7, 1, 6], facing: 'west', wood: W },
  { room: 'Taproom', kind: 'chair', part: 'chair', pos: [7, 1, 7], facing: 'west', wood: W },
  { room: 'Taproom', kind: 'crockery', part: 'plates', pos: [6, 3, 7], facing: 'north', wood: W, block: 'flower_pot' },
  { room: 'Taproom', kind: 'clock', part: 'clock', pos: [5, 1, 8], facing: 'north', wood: W },
  { room: 'Taproom', kind: 'picture', part: 'picture', pos: [6, 3, 8], facing: 'north', wood: W, colour: 'green' },
  { room: 'Taproom', kind: 'lamp', part: 'hangingLamp', pos: [6, 4, 5], facing: 'north', wood: W },
  { room: 'Taproom', kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 7], facing: 'north', wood: W },
  { room: 'Taproom', kind: 'lamp', part: 'hangingLamp', pos: [8, 4, 7], facing: 'north', wood: W },
  // Bar: the back bar (a kitchen counter, a keg, shelves of crockery on the east wall)
  { room: 'Bar', kind: 'counter', part: 'counter', pos: [10, 1, 8], facing: 'west', wood: W },
  { room: 'Bar', kind: 'fixture', part: 'pantry', pos: [10, 1, 7], facing: 'west', block: 'barrel' },
  { room: 'Bar', kind: 'shelf', part: 'shelf', pos: [10, 3, 7], facing: 'west', wood: W, crockery: 'flower_pot' },
  { room: 'Bar', kind: 'shelf', part: 'shelf', pos: [10, 3, 8], facing: 'west', wood: W, crockery: 'potted_brown_mushroom' },
  { room: 'Bar', kind: 'lamp', part: 'hangingLamp', pos: [10, 4, 5], facing: 'west', wood: W },
  // Scullery (x 7..10, z 10): the smoker under the back window's neighbour, a crockery shelf, a barrel
  { room: 'Scullery', kind: 'fixture', part: 'stove', pos: [10, 1, 10], facing: 'west', block: 'smoker' },
  { room: 'Scullery', kind: 'fixture', part: 'pantry', pos: [7, 1, 10], facing: 'north', block: 'barrel' },
  { room: 'Scullery', kind: 'shelf', part: 'shelf', pos: [10, 3, 10], facing: 'west', wood: W, crockery: 'flower_pot' },
  { room: 'Scullery', kind: 'shelf', part: 'shelf', pos: [8, 3, 10], facing: 'north', wood: W, crockery: 'potted_brown_mushroom' },
  { room: 'Scullery', kind: 'lamp', part: 'hangingLamp', pos: [9, 5, 10], facing: 'north', chain: 1, wood: W },
  // Stair Tower
  { room: 'Stair Tower', kind: 'lamp', part: 'hangingLamp', pos: [3, 5, 10], facing: 'north', chain: 1, wood: W },
  // West Chamber (x 2..5, z 2..6): two beds either side of the front window, one along the west wall
  { room: 'West Chamber', kind: 'bed', part: 'bed', pos: [2, 6, 3], facing: 'north', colour: 'red' },
  { room: 'West Chamber', kind: 'bed', part: 'bed', pos: [4, 6, 3], facing: 'north', colour: 'red' },
  { room: 'West Chamber', kind: 'bed', part: 'bed', pos: [2, 6, 5], facing: 'south', colour: 'red' },
  { room: 'West Chamber', kind: 'nightstand', part: 'nightstand', pos: [3, 6, 2], facing: 'south', wood: W },
  { room: 'West Chamber', kind: 'fixture', part: 'chest', pos: [5, 6, 2], facing: 'south', block: 'chest' },
  { room: 'West Chamber', kind: 'rug', part: 'rug', cellsAbs: [[3, 6, 3], [3, 6, 4], [3, 6, 5], [4, 6, 4], [4, 6, 5]], colour: 'red' },
  { room: 'West Chamber', kind: 'table', part: 'table', pos: [5, 6, 6], facing: 'west', wood: W, colour: 'red' },
  { room: 'West Chamber', kind: 'chair', part: 'chair', pos: [4, 6, 6], facing: 'east', wood: W },
  { room: 'West Chamber', kind: 'blinds', part: 'blinds', pos: [3, 8, 2], facing: 'south', wood: W },
  { room: 'West Chamber', kind: 'lamp', part: 'hangingLamp', pos: [4, 8, 4], facing: 'north', wood: W },
  { room: 'West Chamber', kind: 'picture', part: 'picture', pos: [5, 7, 4], facing: 'west', wood: W, colour: 'blue' },
  // East Chamber (x 7..10, z 2..6), the mirror
  { room: 'East Chamber', kind: 'bed', part: 'bed', pos: [10, 6, 3], facing: 'north', colour: 'blue' },
  { room: 'East Chamber', kind: 'bed', part: 'bed', pos: [8, 6, 3], facing: 'north', colour: 'blue' },
  { room: 'East Chamber', kind: 'bed', part: 'bed', pos: [10, 6, 5], facing: 'south', colour: 'blue' },
  { room: 'East Chamber', kind: 'nightstand', part: 'nightstand', pos: [9, 6, 2], facing: 'south', wood: W },
  { room: 'East Chamber', kind: 'fixture', part: 'chest', pos: [7, 6, 2], facing: 'south', block: 'chest' },
  { room: 'East Chamber', kind: 'rug', part: 'rug', cellsAbs: [[9, 6, 3], [9, 6, 4], [9, 6, 5], [8, 6, 4], [8, 6, 5]], colour: 'blue' },
  { room: 'East Chamber', kind: 'table', part: 'table', pos: [7, 6, 6], facing: 'east', wood: W, colour: 'blue' },
  { room: 'East Chamber', kind: 'chair', part: 'chair', pos: [8, 6, 6], facing: 'west', wood: W },
  { room: 'East Chamber', kind: 'blinds', part: 'blinds', pos: [9, 8, 2], facing: 'south', wood: W },
  { room: 'East Chamber', kind: 'lamp', part: 'hangingLamp', pos: [8, 8, 4], facing: 'north', wood: W },
  { room: 'East Chamber', kind: 'picture', part: 'picture', pos: [7, 7, 4], facing: 'east', wood: W, colour: 'yellow' },
  // Passage (z 8): a bench under the back window, the chambers' shelf
  { room: 'Passage', kind: 'couch', part: 'couch', pos: [10, 6, 8], facing: 'west', length: 1, wood: W, colour: 'green' },
  { room: 'Passage', kind: 'shelf', part: 'shelf', pos: [2, 7, 8], facing: 'east', wood: W },
  { room: 'Passage', kind: 'lamp', part: 'hangingLamp', pos: [6, 8, 8], facing: 'north', wood: W },
  // II, the Stable's loft (floor y 4): the grooms' table for four by the hay door, its shelf and picture, a bench, hay at the back
  { level: 2, room: 'Loft', kind: 'table', part: 'table', pos: [22, 5, 5], facing: 'north', wood: W, colour: 'green' },
  { level: 2, room: 'Loft', kind: 'table', part: 'table', pos: [22, 5, 6], facing: 'north', wood: W, colour: 'green' },
  { level: 2, room: 'Loft', kind: 'chair', part: 'chair', pos: [21, 5, 5], facing: 'east', wood: W },
  { level: 2, room: 'Loft', kind: 'chair', part: 'chair', pos: [21, 5, 6], facing: 'east', wood: W },
  { level: 2, room: 'Loft', kind: 'chair', part: 'chair', pos: [23, 5, 5], facing: 'west', wood: W },
  { level: 2, room: 'Loft', kind: 'chair', part: 'chair', pos: [23, 5, 6], facing: 'west', wood: W },
  { level: 2, room: 'Loft', kind: 'crockery', part: 'plates', pos: [22, 7, 6], facing: 'north', wood: W, block: 'flower_pot' },
  { level: 2, room: 'Loft', kind: 'shelf', part: 'shelf', pos: [21, 6, 4], facing: 'south', wood: W },
  { level: 2, room: 'Loft', kind: 'picture', part: 'picture', pos: [24, 6, 4], facing: 'south', wood: W, colour: 'brown' },
  { level: 2, room: 'Loft', kind: 'couch', part: 'couch', pos: [24, 5, 8], facing: 'west', length: 2, wood: W, colour: 'brown' },
  { level: 2, room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [22, 8, 6], facing: 'north', wood: W },
  { level: 2, room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [22, 8, 10], facing: 'north', wood: W },
  // III, the Ale Cellar: a tasting table, the brewer's lit work corner
  { level: 3, room: 'Ale Cellar', kind: 'table', part: 'table', pos: [4, -3, 5], facing: 'north', wood: W, colour: 'brown' },
  { level: 3, room: 'Ale Cellar', kind: 'table', part: 'table', pos: [4, -3, 6], facing: 'north', wood: W, colour: 'brown' },
  { level: 3, room: 'Ale Cellar', kind: 'chair', part: 'chair', pos: [3, -3, 5], facing: 'east', wood: W },
  { level: 3, room: 'Ale Cellar', kind: 'chair', part: 'chair', pos: [5, -3, 6], facing: 'west', wood: W },
  { level: 3, room: 'Ale Cellar', kind: 'lamp', part: 'tableLamp', pos: [10, -3, 7], facing: 'west', wood: W },
]

// The walking line: street step -> front door -> round the long table -> behind the bar (the walk-in cell); and from
// the door past the hearth to the tower door, up the turning stair, along the passage to both chambers.
export const WALK = [
  [6, 0, 0], [6, 1, 1], [6, 1, 2], [6, 1, 3], [7, 1, 3], [8, 1, 3], [9, 1, 3], [10, 1, 3], [10, 1, 4], [10, 1, 5], [10, 1, 6],
  [6, 1, 3], [4, 1, 3], [4, 1, 8], [3, 1, 8], [3, 1, 9], [3, 1, 10], [2, 1, 10], [2, 1, 11], [3, 2, 11], [4, 3, 11],
  [4, 4, 10], [4, 5, 9], [4, 6, 8], [3, 6, 8], [3, 6, 7], [3, 6, 6], [8, 6, 8], [9, 6, 7], [9, 6, 6],
]

// Outside (not furniture spots): flower beds, the sign post, benches under the jetty
export const OUTSIDE = {
  signPost: { at: [1, 0, 0], height: 3, rotation: 8, banner: 'red_banner' },
  flowerBeds: [{ x0: 9, x1: 11, z: 0, plants: ['red_tulip', 'cornflower', 'oxeye_daisy'] }],
  doorLamps: [[4, 4, 1], [8, 4, 1]],
  skirt: [[0, 10], [0, 11], [12, 10], [12, 11]], // plus the row at z 12: the outshot's sills stand on it
}

// The Ale Cellar (III): the hatch, ladder, cask racks and lamps
export const CELLAR = {
  ladder: { x: 10, z: 5, facing: 'west' },
  racks: [{ x: 2, z0: 3, z1: 8, facing: 'east' }, { x: 6, z0: 3, z1: 3, facing: 'west' }, { x: 6, z0: 8, z1: 8, facing: 'west' }],
  chests: [[10, -3, 3, 'west'], [10, -3, 8, 'west']],
  pillars: [[6, 5], [6, 6]],
  backRack: [3, 4],
  workCorner: [['cauldron', 8, 8], ['crafting_table', 9, 8]], // the brewer's vat and bench (z 8, y -3)
  bigCasks: { x: 8, z0: 3, z1: 4, facing: 'east' },
  lamps: [[4, -1, 4], [4, -1, 7], [8, -1, 4], [8, -1, 7]],
}
