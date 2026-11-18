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
//   Shaft      the limewashed drum, radius 4, y 27..37, a stone string course at each floor.
//   Gallery    a ring of floor out to radius 5 at y 38 on a corbel course, iron rail all round.
//   Lantern    a 5 x 5 glazed room on copper posts (y 39..42), the sea lantern on a pedestal inside.
//   Crown      the night light: 12 redstone lamps round the lantern's head (y 43), an inverted daylight detector on
//              each, and a copper cap and rod over them.
//   Cottage    the keeper's house on the quay, x 16..22, z 2..8: stone plinth, spruce frame, limewash infill, slate roof.
//   Landing    the ferry landing off the mole's east flank, x 16..20, z 10..15, a step below the mole: the Travel Post at its sea corner.
//   FishStage  the fisherman's timber stage off the west flank, x 4..8, z 10..15: his barrel and a drying rack.

export const SIZE = [25, 46, 33] // w, h, d (limit 25 x 46 x 33)
export const TOWER = { cx: 12, cz: 24, foot: 6, waist: 5, shaft: 4, gallery: 5, lantern: 2, bastion: 8, riprap: 10 }
export const LEVELS = {
  deck: 3, // the mole's and bastion's deck top (walked at y 4)
  footTop: 13, waistTop: 26, ledges: [14, 27], shaftTop: 37, galleryY: 38, lanternY: 39, glassH: 3, crownY: 43,
  floors: [11, 19, 28], // the tower's inside floors over the ground floor (walked at y 3)
  windows: [[5, 6], [13, 14], [21, 22], [30, 31]], // window pane rows, one pair per storey
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
  limewash: [['white_concrete', 1]], // one clean coat: speckled stone read as confetti in the render
  riprap: [['cobblestone', 3], ['mossy_cobblestone', 3], ['andesite', 2], ['stone', 2]],
  course: 'polished_andesite', // string courses
  ledge: 'stone_brick_stairs',
  corbel: 'stone_brick_stairs',
  galleryFloor: 'smooth_stone',
  rail: 'iron_bars',
  copper: { post: 'waxed_oxidized_copper', cut: 'waxed_oxidized_cut_copper', stairs: 'waxed_oxidized_cut_copper_stairs' },
  glass: 'glass_pane',
  pedestal: 'chiseled_stone_bricks',
  wood: 'spruce', frame: 'spruce_log', floor: 'spruce_planks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  plinth: [['cobblestone', 3], ['stone_bricks', 3], ['mossy_cobblestone', 1]],
}

export const MASSES = {
  quay: { x0: 0, x1: 24, z0: 0, z1: 8 },
  mole: { x0: 9, x1: 15, z0: 9, z1: 17, deckY: 3, gaps: { east: [12, 13], west: [12, 13] } },
  cottage: { x0: 16, x1: 22, z0: 2, z1: 8, beamY: 4, topY: 8, ladder: [18, 7], roof: { axis: 'x', across: [1, 9], along: [15, 23], eaveY: 8, steep: false } },
  landing: { x0: 16, x1: 20, z0: 10, z1: 15 },
  fishStage: { x0: 4, x1: 8, z0: 10, z1: 15 },
}

export const ROOMS = [
  { name: 'Cottage', use: "the keeper's one room: his bed, a table by the window, a lamp", box: [17, 1, 3, 21, 3, 7] },
  { name: 'Loft', use: "the keeper's log room over it, up a ladder: a desk at the window", box: [17, 5, 3, 21, 7, 7] },
  { name: 'Store', use: 'the tower ground floor: oil and rope, an empty chest', box: [9, 4, 21, 15, 10, 27] },
  { name: 'Watch room', use: 'the first floor: a bench at the sea window', box: [9, 12, 21, 15, 18, 27] },
  { name: 'Tower rooms', use: 'two more floors on the ladder, a lamp each', box: [9, 20, 21, 15, 37, 27] },
  { name: 'Gallery', use: 'the walk round the lantern, reached by the ladder', box: [7, 39, 19, 17, 39, 29] },
]

export const OPENINGS = {
  towerDoor: { at: [12, 4, 18], facing: 'south', out: 'north' },
  cottageDoor: { at: [19, 1, 2], facing: 'south', out: 'north' },
  ladder: { x: 15, z: 24, facing: 'west' }, // up the east wall from the ground floor to the gallery
  moleStep: { z: 9, x0: 10, x1: 14 }, // the stairs up from the quay onto the mole
  towerWindows: ['north', 'west', 'south'], // the east side carries the ladder
  cottageWindows: [
    { cells: [[17, 2, 2], [17, 3, 2]], out: 'north' },
    { cells: [[21, 2, 2], [21, 3, 2]], out: 'north' },
    { cells: [[22, 2, 5], [22, 3, 5]], out: 'east' },
    { cells: [[16, 2, 5], [16, 3, 5]], out: 'west' },
    { cells: [[19, 2, 8], [19, 3, 8]], out: 'south' },
    { cells: [[17, 6, 2], [17, 7, 2]], out: 'north' },
    { cells: [[21, 6, 2], [21, 7, 2]], out: 'north' },
    { cells: [[19, 6, 8], [19, 7, 8]], out: 'south' },
  ],
}

// Every piece of furniture, footprint first (the Wonder keeps no furniture file, but the pieces are the kit's own).
export const FURNITURE = [
  // Cottage: the keeper's bed in the back east corner, a nightstand, a table for two under the west window, a lamp
  { room: 'Cottage', kind: 'bed', part: 'bed', pos: [21, 1, 6], facing: 'south', colour: 'blue' },
  { room: 'Cottage', kind: 'nightstand', part: 'nightstand', pos: [20, 1, 7], facing: 'north', wood: 'spruce' },
  { room: 'Cottage', kind: 'table', part: 'table', pos: [17, 1, 5], facing: 'east', wood: 'spruce', colour: 'white' },
  { room: 'Cottage', kind: 'chair', part: 'chair', pos: [17, 1, 4], facing: 'south', wood: 'spruce' },
  { room: 'Cottage', kind: 'chair', part: 'chair', pos: [17, 1, 6], facing: 'north', wood: 'spruce' },
  { room: 'Cottage', kind: 'cupboard', part: 'cupboard', pos: [17, 1, 7], facing: 'east', wood: 'spruce' },
  { room: 'Cottage', kind: 'lamp', part: 'hangingLamp', pos: [19, 3, 5], facing: 'north', wood: 'spruce' },
  { room: 'Cottage', kind: 'rug', part: 'rug', cellsAbs: [[19, 1, 4], [19, 1, 5], [20, 1, 4], [20, 1, 5]], colour: 'light_blue' },
  // Cottage loft (the keeper's log room, up a ladder by the back wall): a desk at the front window, its chair, a lamp
  { room: 'Loft', kind: 'desk', part: 'desk', pos: [21, 5, 3], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'chair', part: 'chair', pos: [21, 5, 4], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'cupboard', part: 'cupboard', pos: [17, 5, 3], facing: 'east', wood: 'spruce' },
  { room: 'Loft', kind: 'lamp', part: 'hangingLamp', pos: [19, 7, 5], facing: 'north', wood: 'spruce' },
  { room: 'Loft', kind: 'rug', part: 'rug', cellsAbs: [[19, 5, 4], [20, 5, 4], [19, 5, 5], [20, 5, 5]], colour: 'blue' },
  // Tower ground floor (the Store): an empty chest against the west wall, a hanging lamp
  { room: 'Store', kind: 'fixture', part: 'chest', pos: [9, 4, 24], facing: 'east', block: 'chest' },
  // Watch room: a bench at the west sea window
  { room: 'Watch room', kind: 'chair', part: 'chair', pos: [9, 12, 23], facing: 'west', wood: 'spruce' },
  { room: 'Watch room', kind: 'chair', part: 'chair', pos: [9, 12, 25], facing: 'west', wood: 'spruce' },
]

// The walking line: quay -> step onto the mole -> along the mole (off it east to the Ferry Landing and the Travel Post,
// west to the Fish Stage and the barrel) -> the bastion -> the tower door -> the ladder -> the gallery.
export const WALK = [
  [12, 1, 2], [12, 1, 8], [12, 2, 9], [12, 3, 10], [12, 4, 11], [12, 4, 12], [15, 4, 12], [16, 3, 12], [18, 2, 12],
  [12, 4, 12], [9, 4, 12], [8, 3, 12], [6, 2, 12],
  [12, 4, 17], [12, 4, 18], [12, 4, 21], [15, 4, 24], [15, 38, 24], [16, 39, 24],
  [19, 1, 1], [19, 1, 2], [19, 1, 4], [18, 1, 6], [18, 5, 6],
]
