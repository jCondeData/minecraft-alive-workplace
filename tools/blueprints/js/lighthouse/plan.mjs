// The Lighthouse (Wonder, ROADMAP 35 / rebuild 49), the plan: drawn before any wall. recipe.mjs assembles it from the
// parts library (../parts) in passes, one call per stage: draw(n) is the whole Wonder as it stands once stage n is done.
//
// Three rough concepts, before choosing:
//   A. "The Mole Light". Massing: a long, low stone mole (causeway) runs from the village quay straight out to sea and
//      ends in a round bastion; the tower rises from the bastion at the far end, the keeper's cottage sits at the root
//      on the quay. Roof: a verdigris copper cap over a glazed lantern, a crown of lamps under it; the cottage under
//      dark slate. Plan: quay -> mole -> bastion -> tower, a ferry landing off one flank of the mole and a fish stage
//      off the other. Story: the town built a harbour arm against the swell and put its light where ships need it, at
//      the very end, so the whole Wonder is a walk out to sea.
//   B. "Keeper's Hall". Massing: a two-storey keeper's house with the tower rising out of its back gable, all on one
//      stone platform. Roof: a slate hall roof, the tower's lantern above it. Plan: house rooms round the tower's foot.
//      Story: the family that keeps the light lives under it. (The usual coastal lighthouse picture.)
//   C. "Rock Stair". Massing: a stepped granite outcrop filling the plot, a squat tower on top, a stair winding up the
//      rock. Roof: a conical stone cap. Plan: terraces stepping down to a jetty. Story: the light on the headland.
// Strongest: A. It is the only one whose silhouette says "harbour" from 20 blocks: a low horizontal arm against one
// tall vertical at the far end, the eye led along the mole to the light. It uses the deep plot (33) as a walk instead
// of filling it, puts the Travel Post and the fisherman's barrel at the sea's edge where the brief wants them, keeps the
// land side (front, z = 0) for the quay and the Foundation Stone, and lets the four stages read as a real build order
// (the arm, then the house and the foot, then the shaft, then the light). B is the safe picture; C fills the plot with
// rock the sea would hide.
//
// Round 2 (the critic's BACK, five notes; the concept kept):
//   1. Ladder shaft -> a winding stair. A spruce stair climbs the tower's inside wall one step a cell round a 24-cell ring
//      (wallRing/spiralStair in parts/lighthouse_stair.mjs), from the door (y 4) to the gallery (y 37), each floor pierced
//      only over the two steps under it. The landings now have jobs: Entry (rope chest), Oil store (racked lamp-oil casks,
//      chest), Chart room (chart table, chair, cupboard, the flag), and under the gallery the keeper's Watch room (his bunk,
//      the one bed, moved up from the cottage; desk and chair at the sea window; the coast map on the wall; a cupboard).
//      Windows on all four sides at every landing, and a second row in the watch room.
//   2. Travel Post at the sea edge: a spruce Ferry Jetty on log piles runs from the landing to the plot's east edge
//      (x 24); the post stands at its end (24, 2, 13) with water on three sides once the sea is in, deck cells beside it.
//   3. The empty pad: its east end is grassed with a dirt path from the front to the landing (one stair up) and an open
//      Net Shed (posts, slab roof, boarded back, chest, hay); the cottage moved west (x 2..8) so the front view sees the
//      mole's mouth and the tower door straight on. The front-left corner (0, 0) stays paved dry quay for the Stone.
//   4. The top: the lantern grows from 5 x 5 to 7 x 7, the gallery from 11 to 13 across on two corbel courses, and a
//      hood crowns it: an eave of 28 night lamps one cell outside the glass (detectors on top, open to the sky), then a
//      stepped verdigris copper roof and a lightning rod. It reads as a cap, not a cube, from 20 blocks.
//   5. The plain shaft: quartz bricks (coursed) in place of white concrete, dark polished-andesite string courses at each
//      landing (the y 28 floor course, and y 29 and 35 under the watch room's windows), and window rows on all four faces, the back (sea) side included.
//
// Coordinates: x across the front (0 -> 24), y up (0 = the layer on the clicked block), z from the front (land, the
// quay, z = 0) out to sea at the back (z = 32). The sea is behind and to the sides of the mole.
//
// Masses (named parts the owner's feedback can point at):
//   Quay       the village's paved landing across the front, z 0..8, walked at y 1.
//   Mole       the stone arm, x 9..15, z 9..17: solid to its deck (top y 3, walked at y 4, three steps up from the quay), parapets each side with
//              gaps where steps go down to the Ferry Landing (east) and the Fish Stage (west), a lamp post every 5.
//   Bastion    the round end of the mole, radius 8 round the tower (12, 24): the same deck and a parapet all round,
//              rip-rap (tumbled boulders) round its foot and along the mole's flanks where the sea breaks.
//   Foot       the tower's thick lower drum, radius 6, y 4..13, mossy sea stone, a weathering ledge on top (y 14).
//   Waist      radius 5, y 14..26, plain stone bricks, a second weathering ledge (y 27): the tower telescopes in.
//   Shaft      the pale drum, radius 4, y 27..36, quartz bricks, dark string courses at the landings.
//   Gallery    a ring of floor out to radius 6 at y 37 on two corbel courses, iron rail all round.
//   Lantern    a 7 x 7 glazed room on copper posts (y 38..41), a copper door out to the gallery, the sea lantern on a pedestal.
//   Hood       the night light: an eave of 28 redstone lamps one cell outside the glass (y 42), an inverted daylight
//              detector on each, a stepped copper roof over the glass and a lightning rod on top (y 45).
//   Cottage    the keeper's day house on the quay, x 2..8, z 2..8 (round 2: moved west): stone plinth, spruce frame, limewash infill, slate roof.
//   Green      the quay's east end, x 16..24, z 0..8: grass, a path to the landing, the Net Shed (x 20..24, z 3..7).
//   Jetty      the ferry jetty, x 21..24, z 12..14, out from the landing to the plot's east edge: the Travel Post at its end.
//   Landing    the ferry landing off the mole's east flank, x 16..20, z 10..15, a step below the mole, the jetty off its east side.
//   FishStage  the fisherman's timber stage off the west flank, x 4..8, z 10..15: his barrel and a drying rack.

export const SIZE = [25, 46, 33] // w, h, d (limit 25 x 46 x 33)
export const TOWER = { cx: 12, cz: 24, foot: 6, waist: 5, shaft: 4, gallery: 6, lantern: 3, bastion: 8, riprap: 10 }
export const LEVELS = {
  deck: 3, // the mole's and bastion's deck top (walked at y 4)
  footTop: 13, waistTop: 26, ledges: [14, 27], shaftTop: 36, galleryY: 37, lanternY: 38, glassH: 3, crownY: 42,
  floors: [12, 20, 28], // the tower's inside floors over the ground floor (walked at y 4)
  stairFrom: 4, // the winding stair's first step (y 4, by the door), one step a cell round the 24-cell wall ring
  windows: [[6, 7], [14, 15], [22, 23], [30, 31], [33, 34]], // window pane rows: one pair per storey, two in the watch room
  bands: [21, 29, 35], // string courses on the shaft at each landing's sill
}

// Palette (60/30/10): sea-wall stone (stone bricks, mossy and cracked low down, limewash on the shaft above) /
// spruce and deepslate tiles (the cottage, the decks, the rails) / verdigris copper (the lantern, the cap). Why: the
// mole and the foot are dark and weathered where the sea hits them, the shaft goes pale so it reads against the sky by
// day; the copper is the one bright note, at the top, where the eye ends.
export const PALETTE = {
  sea: [['stone_bricks', 6], ['mossy_stone_bricks', 3], ['cracked_stone_bricks', 1]], // mole body, bastion, foot
  quay: [['stone_bricks', 5], ['polished_andesite', 2], ['andesite', 1], ['cobblestone', 1]],
  deck: [['stone_bricks', 4], ['polished_andesite', 2]],
  coping: 'stone_brick_wall',
  waist: [['stone_bricks', 5], ['polished_andesite', 1]],
  limewash: [['quartz_bricks', 1]], // round 2: a pale coursed block, not flat concrete; one block, so it never reads as confetti
  band: 'polished_andesite', // the dark string course on the pale shaft
  stair: 'spruce_stairs', // the winding stair
  riprap: [['cobblestone', 3], ['mossy_cobblestone', 3], ['andesite', 2], ['stone', 2]],
  course: 'polished_andesite', // string courses
  ledge: 'stone_brick_stairs',
  corbel: 'stone_brick_stairs',
  galleryFloor: 'smooth_stone',
  rail: 'iron_bars',
  copper: { post: 'waxed_oxidized_copper', cut: 'waxed_oxidized_cut_copper', stairs: 'waxed_oxidized_cut_copper_stairs', door: 'waxed_oxidized_copper' },
  glass: 'glass_pane',
  pedestal: 'chiseled_stone_bricks',
  wood: 'spruce', frame: 'spruce_log', floor: 'spruce_planks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  plinth: [['cobblestone', 3], ['stone_bricks', 3], ['mossy_cobblestone', 1]],
}

export const MASSES = {
  quay: { x0: 0, x1: 24, z0: 0, z1: 8 },
  mole: { x0: 9, x1: 15, z0: 9, z1: 17, deckY: 3, gaps: { east: [12, 13], west: [12, 13] } },
  cottage: { x0: 2, x1: 8, z0: 2, z1: 8, beamY: 4, topY: 8, ladder: [4, 7], roof: { axis: 'x', across: [1, 9], along: [1, 9], eaveY: 8, steep: false } },
  landing: { x0: 16, x1: 20, z0: 10, z1: 15 },
  jetty: { x0: 21, x1: 24, z0: 12, z1: 14, post: [24, 2, 13] }, // the ferry jetty out to the plot's east (sea) edge
  green: { x0: 16, x1: 24, z0: 0, z1: 8, path: 18 }, // the quay's east end, grassed, a gravel path to the landing
  netShed: { x0: 20, x1: 24, z0: 3, z1: 7 }, // an open net and boat shed on the green, by the landing
  fishStage: { x0: 4, x1: 8, z0: 10, z1: 15 },
}

export const ROOMS = [
  { name: 'Cottage', use: "the keeper's day room on the quay: table, chairs, cupboard, his chest", box: [3, 1, 3, 7, 3, 7] },
  { name: 'Loft', use: "the log room over it, up a ladder: a desk at the window", box: [3, 5, 3, 7, 7, 7] },
  { name: 'Entry', use: 'the tower ground floor: the stair starts by the door, an empty chest for rope', box: [9, 4, 21, 15, 11, 27] },
  { name: 'Oil store', use: 'the first floor: lamp-oil casks racked on the east wall, a chest', box: [9, 13, 21, 15, 19, 27] },
  { name: 'Chart room', use: 'the second floor: a chart table and chair, a cupboard of logs, the flag on the wall', box: [9, 21, 21, 15, 27, 27] },
  { name: 'Watch room', use: "under the gallery: the keeper's bunk, desk at the sea window, the coast map, two window rows", box: [9, 29, 21, 15, 36, 27] },
  { name: 'Lantern', use: 'the glazed room: the sea lantern on its pedestal, a door out onto the gallery', box: [10, 38, 22, 14, 41, 26] },
  { name: 'Gallery', use: 'the walk round the lantern under the lamp eave', box: [6, 38, 18, 18, 39, 30] },
]

export const OPENINGS = {
  towerDoor: { at: [12, 4, 18], facing: 'south', out: 'north' },
  cottageDoor: { at: [5, 1, 2], facing: 'south', out: 'north' },
  lanternDoor: { at: [12, 38, 21], facing: 'south', out: 'north' }, // out onto the gallery's land side
  moleStep: { z: 9, x0: 10, x1: 14 }, // the stairs up from the quay onto the mole
  towerWindows: ['north', 'west', 'south', 'east'],
  cottageWindows: [ // [dx, y, dz] from the cottage's front-left corner
    { cells: [[1, 2, 0], [1, 3, 0]], out: 'north' },
    { cells: [[5, 2, 0], [5, 3, 0]], out: 'north' },
    { cells: [[6, 2, 3], [6, 3, 3]], out: 'east' },
    { cells: [[0, 2, 3], [0, 3, 3]], out: 'west' },
    { cells: [[3, 2, 6], [3, 3, 6]], out: 'south' },
    { cells: [[1, 6, 0], [1, 7, 0]], out: 'north' },
    { cells: [[5, 6, 0], [5, 7, 0]], out: 'north' },
    { cells: [[3, 6, 6], [3, 7, 6]], out: 'south' },
  ],
}

// Every piece of furniture, footprint first (the Wonder keeps no furniture file, but the pieces are the kit's own).
// Tower rooms: the stair winds up the wall ring (24 cells, |dx|+|dz| ~ 3 round (12, 24)); furniture stands in the
// middle 13 cells or on ring cells the stair passes only above head height in that storey (plan checked in recipe).
export const FURNITURE = [
  // Cottage: a table for two under the west window, a cupboard, the keeper's chest, a lamp, a rug
  { room: 'Cottage', kind: 'table', part: 'table', pos: [3, 1, 5], facing: 'east', wood: 'spruce', colour: 'white' },
  { room: 'Cottage', kind: 'chair', part: 'chair', pos: [3, 1, 4], facing: 'south', wood: 'spruce' },
  { room: 'Cottage', kind: 'chair', part: 'chair', pos: [3, 1, 6], facing: 'north', wood: 'spruce' },
  { room: 'Cottage', kind: 'cupboard', part: 'cupboard', pos: [3, 1, 7], facing: 'east', wood: 'spruce' },
  { room: 'Cottage', kind: 'cupboard', part: 'cupboard', pos: [7, 1, 7], facing: 'west', wood: 'spruce' },
  { room: 'Cottage', kind: 'fixture', part: 'chest', pos: [7, 1, 6], facing: 'west', block: 'chest' },
  { room: 'Cottage', kind: 'nightstand', part: 'nightstand', pos: [7, 1, 3], facing: 'west', wood: 'spruce' },
  { room: 'Cottage', kind: 'lamp', part: 'hangingLamp', pos: [5, 3, 5], facing: 'north', wood: 'spruce' },
  { room: 'Cottage', kind: 'rug', part: 'rug', cellsAbs: [[5, 1, 4], [5, 1, 5], [6, 1, 4], [6, 1, 5]], colour: 'light_blue' },
  // Cottage loft
  { room: 'Loft', kind: 'desk', part: 'desk', pos: [7, 5, 3], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'chair', part: 'chair', pos: [7, 5, 4], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'cupboard', part: 'cupboard', pos: [3, 5, 3], facing: 'east', wood: 'spruce' },
  { room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [5, 7, 5], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'rug', part: 'rug', cellsAbs: [[5, 5, 4], [6, 5, 4], [5, 5, 5], [6, 5, 5]], colour: 'blue' },
  // Entry (ground floor, walked at y 4): a rope chest on the west wall
  { room: 'Entry', kind: 'fixture', part: 'chest', pos: [9, 4, 24], facing: 'east', block: 'chest' },
  // Oil store (walked at y 13): casks on the east ring (the stair passes there only below and far above), a chest
  { room: 'Oil store', kind: 'fixture', part: 'chest', pos: [14, 13, 22], facing: 'west', block: 'chest' },
  // Chart room (walked at y 21): a chart table and its chair, a cupboard, the village flag
  { room: 'Chart room', kind: 'table', part: 'table', pos: [13, 21, 26], facing: 'west', wood: 'spruce', colour: 'light_blue' },
  { room: 'Chart room', kind: 'chair', part: 'chair', pos: [12, 21, 26], facing: 'east', wood: 'spruce' },
  { room: 'Chart room', kind: 'cupboard', part: 'cupboard', pos: [14, 21, 25], facing: 'west', wood: 'spruce' },
  { room: 'Chart room', kind: 'picture', part: 'picture', pos: [13, 23, 27], facing: 'north', wood: 'spruce', colour: 'red' },
  // Watch room (walked at y 29): the bunk under the west window, a nightstand, the desk at the south (sea) window and its
  // chair, the coast map on the north wall over a cupboard, a rug
  { room: 'Watch room', kind: 'bed', part: 'bed', pos: [9, 29, 25], facing: 'north', colour: 'blue' },
  { room: 'Watch room', kind: 'nightstand', part: 'nightstand', pos: [10, 29, 26], facing: 'west', wood: 'spruce' },
  { room: 'Watch room', kind: 'desk', part: 'desk', pos: [12, 29, 27], facing: 'south', wood: 'spruce' },
  { room: 'Watch room', kind: 'chair', part: 'chair', pos: [12, 29, 26], facing: 'south', wood: 'spruce' },
  { room: 'Watch room', kind: 'cupboard', part: 'cupboard', pos: [10, 29, 22], facing: 'south', wood: 'spruce' },
  { room: 'Watch room', kind: 'picture', part: 'picture', pos: [11, 31, 21], facing: 'south', wood: 'spruce', colour: 'light_blue' },
  { room: 'Watch room', kind: 'rug', part: 'rug', cellsAbs: [[11, 29, 24], [11, 29, 25], [12, 29, 24], [12, 29, 25]], colour: 'blue' },
]

// The walking line: quay -> step onto the mole -> along the mole (off it east to the Ferry Landing and out along the
// jetty to the Travel Post at the plot's east edge, west to the Fish Stage and the barrel) -> the bastion -> the tower
// door -> round and up the winding stair (entry, oil store, chart room, watch room) -> the lantern -> the gallery.
export const WALK = [
  [12, 1, 2], [12, 1, 8], [12, 2, 9], [12, 3, 10], [12, 4, 11], [12, 4, 12], [15, 4, 12], [16, 3, 12], [20, 2, 13], [23, 2, 13],
  [12, 4, 12], [9, 4, 12], [8, 3, 12], [6, 2, 12],
  [18, 1, 1], [18, 1, 8], [18, 2, 10],
  [12, 4, 17], [12, 4, 18], [12, 4, 21], [13, 4, 21], [13, 13, 26], [11, 21, 27], [9, 29, 23], [13, 38, 26], [12, 38, 22], [12, 38, 19],
  [5, 1, 1], [5, 1, 2], [5, 1, 4], [4, 1, 6], [4, 5, 6],
]
