// Physician's House (ROADMAP 49, one level), the plan: drawn before any wall. Rooms, every furniture footprint and the
// walking line come first; recipe.mjs then assembles the house from the parts library (../parts) to fit this plan.
// Requirement: tools/blueprints/briefs/physicians_house.md (a brewing stand and a chest by it, a ward of exactly four
// beds each screened with white wool north and south of its foot, eight or more flowers or ferns, about 800 blocks).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. "Still-tower and ward" (built). An L: a square stone still-room tower at the front-east corner, open inside to
//      a vented pyramid cap, and a long white timber-framed ward behind it, its gable to the street, the physic garden
//      in the crook of the L. Roofs: one 45-degree slate pitch, a gable over the ward, a hollow pyramid with an open
//      louvre over the tower. Story: the physician distils by day in the old stone tower, whose fumes leave by the lit
//      vent at the top, and nurses the sick in the bright ward his village built against it.
//   B. "Cloister infirmary". A U of three low ranges round a square herb garth, the four beds two and two in the
//      arms, the still-room in the middle range under a hipped roof. Story: a monastic infirmary, the garden at its
//      heart. It needs three roofs and two valleys over a small plan: well over the block budget, and from the street
//      it reads as a wall.
//   C. "Dispensary townhouse". Two storeys under one steep roof with dormers: the stand and a counter on the ground
//      floor, the ward in the attic, the garden behind. Story: a town doctor living over his shop. Stairs and a second
//      floor eat the budget, the beds go where only a staircase reaches, and it is a house anyone could have drawn.
//   A is strongest: two masses that differ in material, height and roof (a tower whose cap stands 30% above the ward's
//   ridge), a silhouette you know from a field away (the lantern in the vent at night), and its structure tells the
//   job: the ward's frame bays ARE the bed bays (a post outside, a tie beam overhead and a linen screen under it on
//   every bay line), and the garden sits where both doors open onto it. It is also the cheapest of the three.
//
// Coordinates: x across the front (west 0 -> east 15), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Two doors on the front: the tower's (the Physician's way to his stand, nearest the
// edge, so the builder's path starts there) and the ward's, behind the garden path.
//
// Masses (named parts the owner's feedback can point at):
//   Ward       white plaster panels (x 1..9, z 5..11) set a block back inside a spruce frame (post line x 0..10,
//              z 4..12: posts at the corners and mid-side between the bed windows, a beam ring at y 4); ground room
//              y 1..3, three tie beams across it at y 4 (z 6, 8, 10) over the screens, two carrying the lamps; a 45-degree slate roof, ridge along z at y 10,
//              its gables on the post line (front gable over the door, with a window and studs).
//   Tower      the still-room: stone brick walls x 10..14, z 1..5 (interior 3 x 3) up to y 8, quoined corners, a
//              hollow slate pyramid (overhang ring at y 9) to an open louvre at y 11 with a lantern in it, capped at y 13;
//              its eave pulled in on the west, where the ward's roof runs into it.
//              Open inside to the vent: one tie beam at y 5 holds the still-room lamp.
//   Garden     the physic garden x 0..9, z 0..3: two raised beds boarded with spruce either side of a sunken path to the
//              ward door, twelve herbs and ferns.
//   Lamps      no hoods (both doors are under eaves): a lantern hung from the beam before the ward door, one on a
//              post beside the tower's step.
//   Skirt      a battered stone stair foot along the tower's east face; the street-face tower windows (front, east)
//              get stone drip moulds, the east one a sill on the skirt, the front one shutters.
// No roof hangs over another: the tower's eave is pulled in over the ward's roof on its west side.

export const SIZE = [16, 14, 14] // w, h, d (plot limit 18 x 18 x 22)

// Palette (60/30/10): white plaster and stone brick (the two masses) / spruce frame and deepslate-tile roof /
// cobble plinth, spruce boards inside, the white linen screens and the herbs' greens and blues. Why: a clean, light
// house for the sick, a cool dark slate roof over both masses (one roof material, one pitch), warm spruce drawing the
// bays, the old stone tower set apart from the newer plaster ward.
export const PALETTE = {
  plinth: [['cobblestone', 5], ['stone', 2], ['andesite', 1], ['mossy_cobblestone', 2]],
  plaster: [['white_concrete', 3], ['polished_diorite', 1]], // the first is the clean limewash; the mix only on the bottom course
  tower: [['stone_bricks', 7], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1]],
  quoin: 'polished_andesite',
  frame: 'spruce_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  floor: 'spruce_planks',
  stillFloor: 'smooth_stone',
  wood: 'spruce', // doors, trapdoors, fences, the desk and chair
  trim: 'spruce', // barge boards
  stoneStep: 'stone_brick_stairs',
  glass: 'glass_pane',
  linen: 'white_wool', // the ward's screens (the brief's white wool)
  soil: 'grass_block',
}

export const MASSES = {
  ward: {
    posts: { x0: 0, x1: 10, z0: 4, z1: 12 },
    infill: { x0: 1, x1: 9, z0: 5, z1: 11 },
    room: { x0: 2, x1: 8, z0: 6, z1: 10 },
    wallTop: 3, beamY: 4,
    frontPosts: [0, 4, 6, 10], backPosts: [0, 4, 6, 10],
    westPosts: [8], eastPosts: [8], // and the corners (the tower stands on the east front one): a post between each side's two bed windows
    ties: [6, 8, 10], // tie beams at y 4, over the bay lines (the screens hang under them)
    roof: { axis: 'z', across: [0, 10], along: [3, 13], eaveY: 5, steep: false },
  },
  tower: {
    walls: { x0: 10, x1: 14, z0: 1, z1: 5 },
    room: { x0: 11, x1: 13, z0: 2, z1: 4 },
    centre: [12, 3], wallTop: 8,
    roof: { r: 3, y0: 9, top: 1 }, // overhang ring at y 9, the louvre on the 3 x 3 at y 11
    tie: { y: 5, z: 3 },
  },
}

export const ROOMS = [
  { name: 'Still-room', use: 'the Physician brews and keeps Remedies: the stand, the chest, his recipes', box: [11, 1, 2, 13, 8, 4] },
  { name: 'Ward', use: 'four screened beds, two each side of a three-wide aisle; the night desk at the end', box: [2, 1, 6, 8, 3, 10] },
]

export const OPENINGS = {
  towerDoor: { at: [12, 1, 1], facing: 'south', step: [12, 0, 0] },
  wardDoor: { at: [5, 1, 5], facing: 'south' },
  // windows: the cells of glass, the side they look out of, their dressing (ward: sills with potted herbs; tower:
  // shutters below, plain lights above)
  windows: [
    { name: 'ward front west', cells: [[2, 2, 5], [2, 3, 5]], out: 'north', dress: 'sill', pots: ['potted_fern'] },
    { name: 'ward front east', cells: [[8, 2, 5], [8, 3, 5]], out: 'north', dress: 'sill', pots: ['potted_fern'] },
    { name: 'ward west 1', cells: [[1, 2, 7], [1, 3, 7]], out: 'west', dress: 'sill', pots: ['potted_allium'] },
    { name: 'ward west 2', cells: [[1, 2, 9], [1, 3, 9]], out: 'west', dress: 'sill', pots: ['potted_azure_bluet'] },
    { name: 'ward east 1', cells: [[9, 2, 7], [9, 3, 7]], out: 'east', dress: 'sill', pots: ['potted_cornflower'] },
    { name: 'ward east 2', cells: [[9, 2, 9], [9, 3, 9]], out: 'east', dress: 'sill', pots: ['potted_oxeye_daisy'] },
    { name: 'ward back', cells: [[5, 2, 11], [5, 3, 11]], out: 'south', dress: 'sill', pots: ['potted_fern'] },
    { name: 'ward front gable', cells: [[5, 6, 4], [5, 7, 4]], out: 'north', dress: null },
    { name: 'ward back gable', cells: [[5, 6, 12], [5, 7, 12]], out: 'south', dress: null },
    { name: 'tower west', cells: [[10, 2, 3], [10, 3, 3]], out: 'west', dress: 'shutters' },
    { name: 'tower east', cells: [[14, 2, 3], [14, 3, 3]], out: 'east', dress: 'shutters' },
    { name: 'tower front', cells: [[12, 5, 1], [12, 6, 1]], out: 'north', dress: null },
    { name: 'tower back', cells: [[12, 5, 5], [12, 6, 5]], out: 'south', dress: null },
    { name: 'tower west light', cells: [[10, 6, 3]], out: 'west', dress: null },
    { name: 'tower east light', cells: [[14, 6, 3]], out: 'east', dress: null },
  ],
}

// The ward's beds (foot at pos, head one block toward facing, against the side wall) and the linen screens: white wool
// two high across each bed's length on the bay lines z 6, 8, 10, so every foot has wool one north and one south.
export const SCREENS = [
  { x0: 2, x1: 3, z: 6 }, { x0: 2, x1: 3, z: 8 }, { x0: 2, x1: 3, z: 10 },
  { x0: 7, x1: 8, z: 6 }, { x0: 7, x1: 8, z: 8 }, { x0: 7, x1: 8, z: 10 },
]

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a furniture spot) or a fixture
// (a vanilla block the game uses as itself). part: the furnitureParts part that draws it. facing: the way a person
// using it faces.
export const FURNITURE = [
  // Still-room: the brewing stand on the back wall facing the door, the chest at its right hand, recipes at its left
  { room: 'Still-room', kind: 'fixture', part: 'brewingStand', pos: [12, 1, 4], facing: 'north', block: 'brewing_stand' },
  { room: 'Still-room', kind: 'fixture', part: 'chest', pos: [13, 1, 4], facing: 'north', block: 'chest' },
  { room: 'Still-room', kind: 'fixture', part: 'bookcase', pos: [11, 1, 4], facing: 'north', plant: 'potted_fern' },
  // ...and his writing table under the garden window, its chair facing it, the room's lamp hung over it from the tie
  { room: 'Still-room', kind: 'table', part: 'table', pos: [11, 1, 3], facing: 'north', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce', colour: 'green' },
  { room: 'Still-room', kind: 'chair', part: 'chair', pos: [11, 1, 2], facing: 'south', wood: 'spruce' },
  { room: 'Still-room', kind: 'lamp', part: 'hangingLamp', pos: [11, 4, 3], facing: 'north', wood: 'spruce' },
  // Ward: four beds, heads to the windows, the night station across the end: the linen press, the desk under the back window, the clock
  { room: 'Ward', kind: 'bed', part: 'bed', pos: [3, 1, 7], facing: 'west', colour: 'light_blue' },
  { room: 'Ward', kind: 'bed', part: 'bed', pos: [3, 1, 9], facing: 'west', colour: 'light_blue' },
  { room: 'Ward', kind: 'bed', part: 'bed', pos: [7, 1, 7], facing: 'east', colour: 'light_blue' },
  { room: 'Ward', kind: 'bed', part: 'bed', pos: [7, 1, 9], facing: 'east', colour: 'light_blue' },
  { room: 'Ward', kind: 'cupboard', part: 'cupboard', pos: [4, 1, 10], facing: 'north', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Ward', kind: 'clock', part: 'clock', pos: [6, 1, 10], facing: 'north', cells: [[0, 0, 0], [0, 1, 0]], wood: 'spruce' },
  { room: 'Ward', kind: 'desk', part: 'desk', pos: [5, 1, 10], facing: 'south', wood: 'spruce' },
  { room: 'Ward', kind: 'chair', part: 'chair', pos: [5, 1, 9], facing: 'south', wood: 'spruce' },
  { room: 'Ward', kind: 'lamp', part: 'hangingLamp', pos: [5, 3, 6], facing: 'north', wood: 'spruce' },
  { room: 'Ward', kind: 'lamp', part: 'hangingLamp', pos: [5, 3, 10], facing: 'north', wood: 'spruce' },
]

// The walking line: in at the tower door to the stand (the villager's standing cell (12, 1, 3), the stand 1 south of
// it), and up the garden path into the ward, down its aisle past every bed head to the desk.
export const STAND = { stand: [12, 1, 4], standing: [12, 1, 3], chest: [13, 1, 4] }
export const WALK = [
  [12, 0, 0], [12, 1, 1], [12, 1, 2], [12, 1, 3],
  [5, -1, 0], [5, -1, 1], [5, -1, 2], [5, 0, 3], [5, 1, 4], [5, 1, 5], [5, 1, 6], [4, 1, 7], [4, 1, 8], [4, 1, 9],
  [6, 1, 9], [6, 1, 7],
]

// The physic garden: two raised beds of soil at y 0 boarded with spruce, either side of a sunken path (the site's own
// ground) to a stone step up onto the ward's plinth.
export const GARDEN = {
  path: { x: 5, z0: 0, z1: 3, step: [5, 0, 3] },
  beds: [
    { x0: 1, x1: 3, z0: 1, z1: 2, plants: [['fern', 'lily_of_the_valley', 'fern'], ['allium', 'oxeye_daisy', 'azure_bluet']] },
    { x0: 7, x1: 9, z0: 1, z1: 2, plants: [['cornflower', 'fern', 'poppy'], ['dandelion', 'fern', 'blue_orchid']] },
  ],
}

// Depth and light on the outside (recipe pass 4).
export const DETAIL = {
  wardDoorLamp: [5, 3, 4], // hung from the beam over the ward's doorstep
  towerDoorPosts: [[13, 0, 0]], // a lantern on a spruce post beside the tower step
  skirt: [
    { cells: [[15, 0], [15, 1], [15, 2], [15, 3], [15, 4], [15, 5], [15, 6]], facing: 'west' }, // the east face
  ],
  // the tower's street-face windows: a drip mould over (at: the cell above the glass, out: the face), a sill under (its y)
  dressed: [
    { at: [12, 7, 1], out: 'north', sill: null }, // front, upper (shuttered, so no sill)
    { at: [14, 4, 3], out: 'east', sill: 1 }, // east, on the skirt
  ],
  frontShutters: { cells: [[12, 5, 1], [12, 6, 1]] },
}
