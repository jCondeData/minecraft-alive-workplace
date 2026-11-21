// Sifting Shed (Levels I and II), the plan: drawn before any wall. The cauldrons, their chests and standing spots, the
// heaps and the walking line come first; recipe.mjs then assembles the shed from the parts library (../parts) to fit it.
//
// Three rough concepts (massing, roof, plan, story):
//   A. "The Riddling Lean-to": a long low open shed against a rubble wall, one pent roof, the cauldron at the middle
//      post and the heaps in bays either side. Story: a cottager's sideline. Cheap and safe, but it is a fence with a
//      lid: no silhouette, nothing to read from the street, and Level II can only make it longer.
//   B. "The Tipple": a small screening tower over the sieve, a stone screen room on dark oak legs with a timber hopper
//      loft on top, a steep slate gable to the street with a hoist beam and its lantern under the ridge, gravel tipped
//      from the loft through a chute and funnel into the cauldron below; the heap bays of gravel and sand under a
//      lean-to on its east side. Level II grows the
//      thing every real screening plant has: a covered incline gallery climbing from the back yard into the loft, the
//      second sieve standing in its shelter at the foot of the legs, red sand and soul sand heaped at its foot.
//      Story: the village's gravel works, where carts are hauled up and the riddled finds come down.
//   C. "The Washhouse": a squat stone washhouse round a sluice, a water channel through the floor, a pyramid roof.
//      Story: the prospector who pans. A strong mood, but the job block is a dry cauldron, water channels are a builder's
//      trap, and the footprint is a closed box like every other workshop.
// Strongest: B. It is the only one with a silhouette no other building in the village has (a tower on legs, twice the
// height of the shed beside it), it shows the job from the street (the chute over the sieve, the heaps in their bays,
// the hoist), the Sifter works in the screen room right under the chute, and Level II grows out of it the way works do,
// with an incline that turns the tower into a machine. Built: B.
//
// Coordinates: x across the front (west 0 -> east 9), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back (8 in Level I, 13 in Level II). Kept cells (brief): cauldron (4, 1, 4), chest (6, 1, 6)
// facing west; old Level II cauldron (4, 1, 9), chest (6, 1, 10) facing west. All four are this drawing's own cells.
//
// Masses (named parts the owner's feedback can point at):
//   Screen room the ground storey: six dark oak legs at x 2, 6 and z 1, 4, 7 on stone footings (y 1), up to y 4, with
//               rubble walls between them; the room inside (x 3..5, z 2..6) floored in trodden gravel, the front door
//               (4, 1, 1) under a slate hood with a lantern, shuttered windows west and east (onto the bays). (Round 1
//               stood the loft on open legs; holes.py's `open` check reads a 3-wide undercroft under a loft as a room
//               open to the street, so the legs got walls and a door: closed, it is also the Sifter's dry room.)
//   Loft        the hopper house on the legs: a beam ring at y 5 (its floor), pale board walls y 6..8 between frame
//               posts, a beam ring at y 9, shuttered windows in each side bay, a hatch door in the front wall between
//               two studs (x 3, 5; the back wall's window between the same two).
//   Roof        a steep slate gable (ridge along z, y 14), a block of overhang all round, the gable to the street with a
//               king post, a little window and the hoist beam's end under the ridge, a lantern on its chain.
//   Chute       the hatch in the loft floor over the cauldron and a funnel of upside-down stairs under it (y 4).
//   Bays        the east lean-to: a slate pent roof from the loft's beam down to its eave at x 8, posts at x 8 (z 1, 4,
//               7) in line with the legs, a gravel heap and a sand heap in board bays, open to the east (two deep, so
//               the street sees the heaps and holes.py doesn't read the bays as a room).
//   Rack        the west side: sieves (open trapdoors) hung to dry on a fence rail between two posts, behind the window.
//   Gallery     Level II: the covered incline, deck x 3..5, from the loft's back wall (z 7, a door at y 6) down to the
//               ground at z 13; stair decking, rails, a slate roof sloping with it (the deck's width: no eaves, so
//               the second sieve's shelter under it stays open), a joist under the landing (z 8), trestle legs at z 10,
//               board cheeks either side of the landing under the loft's eaves.
//   Foot        Level II: the second sieve under the gallery (cauldron (4, 1, 9)), its chest against the gallery's leg,
//               reached through a back door (4, 1, 7) where Level I's back window was,
//               a stone abutment where the incline lands, red sand and soul sand heaped in bays either side.

export const LEVEL = Number(process.env.LEVEL ?? 1)
export const SIZE = LEVEL === 1 ? [10, 15, 9] : [10, 15, 14] // w, h, d (I <= 13 x 16 x 13, II <= 13 x 16 x 14)

// Palette (60/30/10): pale birch boards (the loft and bay boards: the colour of sand, the thing he sifts) / dark oak
// legs and frame with a dark deepslate-tile roof (the heavy, load-bearing works) / cobble-and-tuff footings, the coloured
// heaps (gravel, sand, red sand, soul sand) and the lanterns as the accent. Why: a works building reads by its frame,
// so the frame is dark and the boards light; the slate roof contrasts with both and stays crisp at the steep pitch.
export const PALETTE = {
  stone: [['cobblestone', 5], ['andesite', 2], ['tuff', 2], ['stone', 1]],
  ground: [['gravel', 3], ['coarse_dirt', 3], ['cobblestone', 1]], // the trodden undercroft floor
  frame: 'dark_oak_log',
  brace: 'dark_oak_stairs',
  boards: 'birch_planks',
  floor: 'spruce_planks', // the loft floor and gallery deck
  wood: 'spruce', // trapdoors (shutters, hatch, sieves), fences, deck stairs
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  glass: 'glass_pane',
  heaps: { gravel: 'gravel', sand: 'sand', red: 'red_sand', soul: 'soul_sand' },
}

export const MASSES = {
  legs: { xs: [2, 6], zs: [1, 4, 7], footY: 1, topY: 4 },
  loft: { x0: 2, x1: 6, z0: 1, z1: 7, floorY: 5, wallY0: 6, wallY1: 8, beamY: 9, posts: { side: [1, 4, 7], end: [2, 6] }, studs: [3, 5] },
  roof: { axis: 'z', across: [1, 7], along: [0, 8], eaveY: 9, steep: true },
  bays: { x0: 7, x1: 8, z0: 1, z1: 7, posts: { x: 8, zs: [1, 4, 7], top: 3 }, ridgeY: 6 },
  gallery: { x0: 2, x1: 6, zTop: 8, zFoot: 13, deckTop: 5, legsZ: 10 },
}

// The two workstations: the cauldron, its chest (against a solid block, facing the standing spot) and where he stands.
export const STATIONS = [
  { level: 1, cauldron: [4, 1, 4], chest: [6, 1, 6], chestFacing: 'west', stand: [5, 1, 5], against: [6, 1, 7] },
  { level: 2, cauldron: [4, 1, 9], chest: [6, 1, 10], chestFacing: 'west', stand: [5, 1, 9], against: [5, 1, 10] },
]

export const OPENINGS = {
  frontDoor: { at: [4, 1, 1], facing: 'north', step: [4, 0, 0] },
  backDoor: { at: [4, 1, 7], facing: 'south', level: 2 }, // Level II: the back window becomes a door to the second sieve
  loftHatch: { cells: [[4, 6, 1], [4, 7, 1]], out: 'north' }, // the tipping door in the front gable (closed by trapdoors)
  galleryDoor: { cells: [[4, 6, 7], [4, 7, 7]] }, // Level II: the loft's back window becomes the gallery's doorway
  windows: [
    { name: 'loft west front', cells: [[2, 7, 2], [2, 7, 3]], out: 'west' },
    { name: 'loft west back', cells: [[2, 7, 5], [2, 7, 6]], out: 'west' },
    { name: 'loft east front', cells: [[6, 7, 2], [6, 7, 3]], out: 'east' },
    { name: 'loft east back', cells: [[6, 7, 5], [6, 7, 6]], out: 'east' },
    { name: 'loft back', cells: [[4, 7, 7]], out: 'south', level: 1 },
    { name: 'room west', cells: [[2, 2, 2], [2, 3, 2], [2, 2, 3], [2, 3, 3]], out: 'west' },
    { name: 'room east', cells: [[6, 2, 2], [6, 3, 2], [6, 2, 3], [6, 3, 3]], out: 'east' },
    { name: 'room back', cells: [[4, 2, 7], [4, 3, 7]], out: 'south', level: 1 },
    { name: 'gable front', cells: [[4, 11, 1]], out: 'north' },
    { name: 'gable back', cells: [[4, 11, 7]], out: 'south' },
  ],
}

// No furniture spots (the brief asks for none): the fixtures are the job's own blocks.
export const FURNITURE = []

// The heaps (gravity blocks, each on something solid): cells [x, y, z] and their block.
export const HEAPS = [
  { name: 'gravel bay', block: 'gravel', base: [[7, 2], [8, 2], [7, 3], [8, 3]], crown: [[7, 2, 2]] },
  { name: 'sand bay', block: 'sand', base: [[7, 5], [8, 5], [7, 6], [8, 6]], crown: [[7, 2, 5]] },
  // Level II's heaps lie in the open yard straight on the ground (y 0), tipped from the barrows: low heaps (one block
  // crowning each) in board bays, a row of upright boards (open trapdoors) along each bay's outer side (critic polish)
  { name: 'red sand heap', block: 'red', y: 0, base: [[8, 11], [9, 11], [8, 12], [9, 12], [8, 13]], crown: [[8, 1, 12]], level: 2,
    boards: { cells: [[9, 1, 11], [9, 1, 12], [9, 1, 13]], facing: 'west' } },
  { name: 'soul sand heap', block: 'soul', y: 0, base: [[0, 11], [1, 11], [0, 12], [1, 12], [0, 10]], crown: [[1, 1, 11]], level: 2,
    boards: { cells: [[0, 1, 10], [0, 1, 11], [0, 1, 12]], facing: 'east' } },
]

// The walking line: the street -> the front door -> the standing spot -> the chest; in II on under the back
// door to the second sieve and its chest (and up the incline from the back yard into the loft).
export const WALK = [
  [4, 0, 0], [4, 1, 1], [4, 1, 2], [5, 1, 3], [5, 1, 4], [5, 1, 5], // Level I: stand at (5, 1, 5), chest (6, 1, 6) beside
  [4, 1, 6], [4, 1, 7], [4, 1, 8], [5, 1, 9], // Level II: out the back door, the second spot at (5, 1, 9)
  [4, 0, 13], [4, 1, 12], [4, 2, 11], [4, 3, 10], [4, 4, 9], [4, 5, 8], [4, 6, 7], // up the incline into the loft
]

export const OUTSIDE = {
  lamp: { at: [9, 1, 0], post: 'spruce_fence' }, // a lamp post at the bays' front corner, on its own stone
  hoist: { beam: [4, 13, 0], chain: 2, lantern: [4, 10, 0] }, // the hoist beam's end under the ridge, a lantern on it
  rack: { x: 1, zs: [5, 7], railY: 2, sieves: [[0, 5], [0, 6], [0, 7]] }, // sieves drying on a rail, west side
  pile: [[3, 1, 6], [3, 2, 6], [3, 1, 5]], // gravel waiting by the sieve in the screen room
}
