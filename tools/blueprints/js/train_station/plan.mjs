// Train Station (ROADMAP 49/50.13), the plan: drawn before any wall. Brief: tools/blueprints/briefs/train_station.md.
// recipe.mjs assembles it from the parts library (../parts) to fit this plan.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Trainshed. A long brick-walled train shed (the main mass) straddles the platform and the track under one
//      wide 45-degree slate roof, ridge along the line, both gable ends open as tall timber portals with glazed fans
//      above them so the track runs in and out under them; a gabled booking hall faces the street, and a square
//      clock tower with a copper cap rises at its corner. Story: the village builds itself a gateway; you arrive
//      under a roof, and the clock is the first thing you see from the road.
//   B. The Signal-Box Halt. A low waiting room on the street, a bare platform with a bracketed canopy over the
//      platform only, and a tall two-storey timber signal box at one end with a hipped roof and windows all round.
//      Story: a country halt where the signalman keeps watch over a line passing through fields.
//   C. The Viaduct Station. The track carried high on a brick viaduct along the back of the plot, a booking hall
//      under the arches and a stair up to a raised platform. Story: the railway arrives over the rooftops.
// Strongest: A. It is the one with a shape you'd know in outline from the road (the long shed roof with its glass
// lantern, the open portals with the track running out of them, the clock tower with its green cap), it puts the
// track and the train under the building's own roof, which is what a station is, and its three masses (shed, hall,
// tower) differ in height and ridge direction. B reads as a hut beside a track; C needs the track high above the road's
// track, which the spur (one Create curve, level) can't climb. A's risk, a roof over the line, is met by keeping
// every beam at least six blocks over the track (the train is four tall).
//
// Coordinates: x across the front (west 0 -> east 18), y up (0 = the layer on the clicked block), z from the front
// (z = 0, the street, north) to the back (z = 16). The track runs along x at z = 12 over the whole width.
//
// Masses (named parts the owner's feedback can point at):
//   Shed      the main mass: brick walls on lines z 7 (front) and z 15 (back), x 1..17, bays of 3 between posts at
//             x 1, 5, 9, 13, 17; beam ring y 6; a 45-degree deepslate-tile roof (ridge along x, y 11) from z 6 to 16,
//             overhanging both gable ends (x 0 and 18). Open portals at x 1 and x 17 (y 1..6) under a tie beam at y 7,
//             a glazed fan above it. Trusses at x 5, 9 and 13. A glazed lantern along the ridge, capped in tiles.
//   Platform  y 0 paving, z 8..11, the whole width; a smooth edge stripe at z 11 where the station stands.
//   Track     Create track along x at z 12 (y 1), on ballast; a back walk at z 13..14 inside the back wall.
//   Hall      the booking hall on the street: brick walls x 3..11, z 1..7, pilasters proud at z 0, a 45-degree roof
//             (ridge along z, y 10) gable to the street with a round window, dying into the shed's front slope.
//   Tower     the clock tower at the hall's east corner, x 11..15, z 3..7: brick with stone quoins, the ticket office
//             on its ground floor, a clock stage over a second
//             course (three faces at y 9..11), a stone cornice at y 12 and a steep copper cap to its tip at y 17.
//   Forecourt a raised flower bed by the tower, a flower strip along the hall's west side, lamp posts on the street.

export const SIZE = [19, 18, 17] // w, h, d (limit 19 x 18 x 19)

// Palette (60/30/10): red brick walls / deepslate-tile roofs and dark oak timber (frame, trusses, doors) / dressed
// stone (plinth, pilasters, quoins, platform) and weathered copper (the tower's cap). Why: a railway
// is a made thing, so the station is fired brick and slate rather than the village's logs and plaster; the dark roof
// over red brick is the railway's own look, and the green copper cap on the tower is the one bright note.
export const PALETTE = {
  plinth: [['deepslate_bricks', 6], ['cracked_deepslate_bricks', 2], ['polished_deepslate', 1]],
  brick: 'bricks',
  dressing: 'stone_bricks', // pilasters, quoins, sills, string courses
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  copper: { full: 'waxed_weathered_cut_copper', stairs: 'waxed_weathered_cut_copper_stairs', slab: 'waxed_weathered_cut_copper_slab' },
  frame: 'dark_oak_log',
  wood: 'dark_oak', // doors, trapdoors, stairs as seats, fences, barge boards
  glass: 'glass_pane',
  platform: [['stone_bricks', 5], ['cracked_stone_bricks', 1], ['andesite', 1]],
  edge: 'smooth_stone',
  ballast: [['andesite', 3], ['tuff', 2], ['cobblestone', 1]],
  hallFloor: ['polished_deepslate', 'polished_andesite'], // a checkered booking-hall floor
  officeFloor: 'spruce_planks',
  clock: { face: 'smooth_quartz', hand: 'polished_deepslate' },
  track: 'create:track',
  station: 'create:track_station',
}

export const TRACK = { axis: 'x', line: 12, a0: 0, a1: 18, y: 1 }
export const STATION = { at: [9, 1, 11] } // beside the track, one block across it (z 11), the platform's edge

export const MASSES = {
  shed: {
    x0: 1, x1: 17, front: 7, back: 15, posts: [1, 5, 9, 13, 17], wallTop: 5, beamY: 6, tieY: 7,
    roof: { axis: 'x', across: [6, 16], along: [0, 18], eaveY: 6, steep: false }, // ridge y 11 at z 11
    trusses: [5, 9, 13], portals: [1, 17], lantern: { x0: 3, x1: 15, zc: 11, y: 11 },
  },
  platform: { z0: 8, z1: 11, edgeZ: 11 },
  hall: {
    x0: 3, x1: 11, z0: 1, z1: 7, wallTop: 4, courseY: 5, room: { x0: 4, x1: 10, z0: 2, z1: 6 },
    frontPilasters: [3, 6, 8, 11], westPilasters: [1, 4, 7],
    roof: { axis: 'z', across: [2, 12], along: [0, 7], eaveY: 5, steep: false }, // ridge y 10 at x 7
  },
  tower: { x0: 11, x1: 15, z0: 3, z1: 7, wallTop: 12, office: { x0: 12, x1: 14, z0: 4, z1: 6 }, ceilingY: 5, capY: 13, capR: 2, clockY: 10, courses: [6, 8] },
}

export const ROOMS = [
  { name: 'Booking hall', use: 'waiting, the timetable and the ticket window', box: [4, 1, 2, 10, 4, 6] },
  { name: 'Ticket office', use: 'the clerk behind his window, his desk and the ledgers', box: [12, 1, 4, 14, 4, 6] },
  { name: 'Platform', use: 'waiting for the train under the shed, the Conductor at his station', box: [0, 1, 8, 18, 6, 11] },
]

export const OPENINGS = {
  frontDoor: { at: [7, 1, 1], facing: 'south', step: [7, 0, 0] },
  platformDoor: { at: [7, 1, 7], facing: 'north' },
  officeDoor: { at: [11, 1, 4], facing: 'east' },
  ticketWindow: { at: [11, 2, 5] },
  windows: [
    { name: 'hall front west', cells: [[4, 2, 1], [5, 2, 1], [4, 3, 1], [5, 3, 1]], out: 'north', dress: 'sill' },
    { name: 'hall front east', cells: [[9, 2, 1], [10, 2, 1], [9, 3, 1], [10, 3, 1]], out: 'north', dress: 'sill' },
    { name: 'hall west front', cells: [[3, 2, 2], [3, 2, 3], [3, 3, 2], [3, 3, 3]], out: 'west', dress: 'sill' },
    { name: 'hall west back', cells: [[3, 2, 5], [3, 2, 6], [3, 3, 5], [3, 3, 6]], out: 'west', dress: 'sill' },
    { name: 'hall to platform west', cells: [[4, 2, 7], [5, 2, 7], [4, 3, 7], [5, 3, 7]], out: 'south', dress: null },
    { name: 'hall to platform east', cells: [[9, 2, 7], [10, 2, 7], [9, 3, 7], [10, 3, 7]], out: 'south', dress: null },
    { name: 'office front', cells: [[13, 2, 3], [13, 3, 3]], out: 'north', dress: 'sill' },
    { name: 'office east', cells: [[15, 2, 5], [15, 3, 5]], out: 'east', dress: 'sill' },
    { name: 'transom', cells: [[7, 3, 1], [7, 4, 1]], out: 'north', dress: null },
    // the shed's back wall: a three-pane window in every bay, under a brick arch
    ...[3, 7, 11, 15].map(x => ({ name: `shed back ${x}`, cells: [[x - 1, 3, 15], [x, 3, 15], [x + 1, 3, 15], [x - 1, 4, 15], [x, 4, 15], [x + 1, 4, 15]], out: 'south', dress: 'sill' })),
    { name: 'shed front west', cells: [[2, 2, 7], [2, 3, 7]], out: 'north', dress: 'sill' },
    { name: 'shed front east', cells: [[16, 2, 7], [16, 3, 7]], out: 'north', dress: 'sill' },
  ],
}

// Every piece of furniture, footprint first (kind: a furniture.py kind; part: the furnitureParts part or a station part).
export const FURNITURE = [
  // Booking hall: two benches against the platform wall either side of its door, a clock by the ticket window, a lamp
  // on a side table, the route map by the door, a red runner down the walking line, lamps hung from the tie beam
  { room: 'Booking hall', kind: 'bench', part: 'bench', cells: [[5, 1, 6], [6, 1, 6]], facing: 'north', wood: 'dark_oak' },
  { room: 'Booking hall', kind: 'bench', part: 'bench', cells: [[8, 1, 6], [9, 1, 6]], facing: 'north', wood: 'dark_oak' },
  { room: 'Booking hall', kind: 'clock', part: 'clock', pos: [10, 1, 3], facing: 'west', wood: 'dark_oak' },
  { room: 'Booking hall', kind: 'lamp', part: 'tableLamp', pos: [4, 1, 4], facing: 'east', wood: 'dark_oak' },
  { room: 'Booking hall', kind: 'picture', part: 'picture', pos: [8, 3, 2], facing: 'south', wood: 'dark_oak', colour: 'green' }, // the route map, on the front wall by the door (not over the table lamp)
  { room: 'Booking hall', kind: 'rug', part: 'rug', cellsAbs: [[7, 1, 3], [7, 1, 4], [7, 1, 5]], colour: 'red' },
  { room: 'Booking hall', kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 4], facing: 'north', wood: 'dark_oak' },
  { room: 'Booking hall', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 4], facing: 'north', wood: 'dark_oak' },
  // Ticket office: the counter at the barred window, the clerk's desk and chair, a cupboard of ledgers, a lamp
  { room: 'Ticket office', kind: 'counter', part: 'counter', pos: [12, 1, 5], facing: 'west', wood: 'dark_oak' },
  { room: 'Ticket office', kind: 'desk', part: 'desk', pos: [14, 1, 6], facing: 'east', wood: 'dark_oak' },
  { room: 'Ticket office', kind: 'chair', part: 'chair', pos: [13, 1, 6], facing: 'east', wood: 'dark_oak' },
  { room: 'Ticket office', kind: 'cupboard', part: 'cupboard', pos: [14, 1, 4], facing: 'west', wood: 'dark_oak' },
  { room: 'Ticket office', kind: 'lamp', part: 'hangingLamp', pos: [13, 4, 5], facing: 'north', wood: 'dark_oak' },
  // Platform: benches with slatted backs facing the track, clear of the station's row; lamps hung from the trusses
  { room: 'Platform', kind: 'bench', part: 'bench', cells: [[2, 1, 9], [3, 1, 9], [4, 1, 9]], facing: 'south', back: 'dark_oak', wood: 'dark_oak' },
  { room: 'Platform', kind: 'bench', part: 'bench', cells: [[14, 1, 9], [15, 1, 9], [16, 1, 9]], facing: 'south', back: 'dark_oak', wood: 'dark_oak' },
  { room: 'Platform', kind: 'lamp', part: 'hangingLamp', pos: [5, 5, 9], facing: 'north', chain: 1, wood: 'dark_oak' },
  { room: 'Platform', kind: 'lamp', part: 'hangingLamp', pos: [9, 5, 9], facing: 'north', chain: 1, wood: 'dark_oak' },
  { room: 'Platform', kind: 'lamp', part: 'hangingLamp', pos: [13, 5, 9], facing: 'north', chain: 1, wood: 'dark_oak' },
]

// The walking line: street step -> front door -> booking hall -> platform door -> the platform -> the station's side
// (the Conductor's cell, 9, 1, 10). The ticket queue stands at (10, 1, 5); the clerk goes (11, 1, 4) -> (13, 1, 5).
export const WALK = [
  [7, 0, 0], [7, 1, 1], [7, 1, 2], [7, 1, 3], [7, 1, 4], [7, 1, 5], [7, 1, 6], [7, 1, 7], [7, 1, 8], [8, 1, 9], [9, 1, 10],
]

// Kept clear for the brief: the 4 track cells each way past the station's (x 5..8, 10..13) and 4 above them (y 2..5),
// and the second station's cells in the station's row (z 11, x 6..8 and 10..12, y 1).
export const KEEP_CLEAR = {
  overTrack: { x0: 0, x1: 18, y0: 2, y1: 6, z: 12 },
  secondStation: [[6, 1, 11], [7, 1, 11], [8, 1, 11], [10, 1, 11], [11, 1, 11], [12, 1, 11]],
}

export const OUTSIDE = {
  bed: { x0: 16, x1: 18, z0: 0, z1: 4, plants: ['allium', 'azure_bluet', 'poppy'] }, // a raised bed by the tower
  strip: { x0: 0, x1: 1, z0: 1, z1: 6, plants: ['poppy', 'cornflower', 'oxeye_daisy'] }, // flowers along the hall's west side
  lampPosts: [[2, 0, 0], [12, 0, 0]],
}
