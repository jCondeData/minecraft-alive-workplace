// The Leader's Hall (49 rebuild), the plan: drawn before any wall. Rooms, every furniture footprint and the walking
// line come first; recipe.mjs then assembles the hall from the parts library (../parts) in passes.
//
// Three rough concepts:
//   A. "The Challenge Hall": one tall stone nave (11 x 13, ridge along z, 45-degree tiled roof to y 13) with a gabled
//      gate-porch on the front. Plan: door -> a sparring ring set into the floor -> a full-width step -> the Leader's
//      dais at the back, the Training Post on it under a round window. Story: the challenger walks the whole length of
//      the hall, past the benches, across the ring, up to the Leader. The building IS the challenge.
//   B. "The Open Court": a U of low cloisters round an outdoor battle court, the post in a loggia at the back, lean-to
//      roofs. Story: a sports ground. Reads as a yard, not a hall; low and flat, the post far from any shelter.
//   C. "The Leader's Keep": a square tower (7 x 7, three floors, pyramid roof) with the post on the top floor's
//      balcony. Story: the champion above the village. Tall, but the challenge happens up stairs a villager must climb
//      and the plan is cramped; the ground floor has nothing to do.
// Strongest: A. One axis does everything: the walk from the door to the post is the hall's whole length, the ring
// marks where battles happen, the dais and its round window make the Leader the focus from the doorway. B is a yard,
// C hides the job upstairs. A is built here, as the hero gable and the walk it frames.
//
// Coordinates: x across the front (west 0 -> east 14), y up (0 = on the clicked block), z from the front (0) back.
//
// Masses (named parts):
//   Nave     the hall: walls x 2..12, z 3..15, y 1..7 (a dark beam at y 6), 45-degree deepslate-tile roof over
//            x 1..13 (a block of eave each side), ridge y 13, along z 2..16 (a block of overhang at each gable).
//   Piers    polished deepslate piers a block proud of the long walls at z 3, 6, 9, 12, 15 (four bays a side), capped.
//   Porch    the gate-porch: x 5..9, z 0..2, on two dark posts, its own small gable (ridge y 7) under the nave's front
//            gable; three-wide steps at z 0, a lantern under its ridge.
//   Ring     a sparring ring set into the nave floor, centred (7, 8), radius 2, a red border.
//   Dais     the Leader's platform x 3..11, z 12..14, one block up, a full-width step at z 11; the Training Post at
//            its centre, her seat behind it, banners on the back wall, two lamp posts.
//   Windows  four tall bays a side, two windows each side of the porch, round windows in both gables.

export const SIZE = [15, 15, 17] // w, h, d (plot limit 16 x 26 x 18)

// Palette (60/30/10): pale stone-brick walls / dark deepslate tile roof, piers and dais with dark oak beams and joinery /
// red and gold (banners, the ring's border, the dais carpet). Why: a pale hall under a dark roof reads civic and
// strong, the dark piers draw its bays, and the red is the Leader's colour, used only where the challenge happens.
export const PALETTE = {
  wall: [['stone_bricks', 8], ['cracked_stone_bricks', 1], ['tuff_bricks', 2]],
  plinth: [['deepslate_bricks', 5], ['cobbled_deepslate', 2], ['cracked_deepslate_bricks', 1]],
  pier: 'polished_deepslate', pierCap: 'polished_deepslate_stairs',
  frame: 'dark_oak_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  paving: [['polished_andesite', 3], ['stone_bricks', 2]],
  ringInner: 'smooth_stone', ringBorder: 'red_terracotta',
  daisTop: 'polished_deepslate', daisStep: 'polished_deepslate_stairs',
  wood: 'dark_oak', trim: 'dark_oak',
  glass: 'glass_pane', roundFrame: 'polished_deepslate',
  accent: 'red', gold: 'yellow',
}

export const MASSES = {
  nave: {
    walls: { x0: 2, x1: 12, z0: 3, z1: 15 }, room: { x0: 3, x1: 11, z0: 4, z1: 14 },
    wallTop: 7, beamY: 6,
    roof: { axis: 'z', across: [1, 13], along: [2, 16], eaveY: 7, steep: false },
    piers: { at: [3, 6, 9, 12, 15], y1: 5 },
  },
  porch: { x0: 5, x1: 9, z0: 0, z1: 2, postTop: 3, beamY: 4, roof: { axis: 'z', across: [4, 10], along: [0, 2], eaveY: 4, steep: false } },
  ring: { cx: 7, cz: 8, r: 2, x0: 3, x1: 11, z0: 4, z1: 11 },
  dais: { x0: 3, x1: 11, z0: 12, z1: 14, y: 1, stepZ: 11 },
}

export const ROOMS = [
  { name: 'Hall', use: 'the challenge: benches either side of the sparring ring', box: [3, 1, 4, 11, 6, 11] },
  { name: 'Dais', use: "the Leader's post, her seat and her banners", box: [3, 2, 12, 11, 6, 14] },
]

// The job block: exactly one Training Post, on the dais, facing the door.
export const JOB = { block: 'aliveworkplace:training_post', at: [7, 2, 13], facing: 'north' }

export const OPENINGS = {
  frontDoor: { at: [7, 1, 3], facing: 'south', out: 'north' },
  steps: [[6, 0, 0], [7, 0, 0], [8, 0, 0]],
  windows: [
    ...[4, 7, 10, 13].flatMap(z => [
      { name: `west bay ${z}`, cells: [[2, 2, z], [2, 3, z], [2, 4, z], [2, 2, z + 1], [2, 3, z + 1], [2, 4, z + 1]], out: 'west' },
      { name: `east bay ${z}`, cells: [[12, 2, z], [12, 3, z], [12, 4, z], [12, 2, z + 1], [12, 3, z + 1], [12, 4, z + 1]], out: 'east' },
    ]),
    { name: 'front west', cells: [[3, 2, 3], [4, 2, 3], [3, 3, 3], [4, 3, 3]], out: 'north' },
    { name: 'front east', cells: [[10, 2, 3], [11, 2, 3], [10, 3, 3], [11, 3, 3]], out: 'north' },
  ],
  roundWindows: [{ line: 3, cy: 10 }, { line: 15, cy: 9 }],
}

export const FURNITURE = [
  // Hall: benches facing the ring down both long walls, lanterns from the roof
  ...[5, 6, 7, 9, 10].map(z => ({ room: 'Hall', kind: 'chair', part: 'chair', pos: [3, 1, z], facing: 'east', wood: 'dark_oak' })),
  ...[5, 6, 7, 9, 10].map(z => ({ room: 'Hall', kind: 'chair', part: 'chair', pos: [11, 1, z], facing: 'west', wood: 'dark_oak' })),
  { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [7, 9, 6], facing: 'north', chain: 3, wood: 'dark_oak' },
  { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [7, 9, 10], facing: 'north', chain: 3, wood: 'dark_oak' },
  { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [4, 7, 8], facing: 'north', chain: 2, wood: 'dark_oak' },
  { room: 'Hall', kind: 'lamp', part: 'hangingLamp', pos: [10, 7, 8], facing: 'north', chain: 2, wood: 'dark_oak' },
  { room: 'Hall', kind: 'picture', part: 'picture', pos: [3, 4, 8], facing: 'east', wood: 'dark_oak', colour: 'red' },
  { room: 'Hall', kind: 'picture', part: 'picture', pos: [11, 4, 8], facing: 'west', wood: 'dark_oak', colour: 'red' },
  // Dais: the Leader's seat behind her post, her banners, a red carpet she stands on, two lamp posts
  { room: 'Dais', kind: 'chair', part: 'armchair', pos: [7, 2, 14], facing: 'north', wood: 'dark_oak', colour: 'red' },
  { room: 'Dais', kind: 'picture', part: 'picture', pos: [5, 4, 14], facing: 'north', wood: 'dark_oak', colour: 'red' },
  { room: 'Dais', kind: 'picture', part: 'picture', pos: [9, 4, 14], facing: 'north', wood: 'dark_oak', colour: 'red' },
  { room: 'Dais', kind: 'picture', part: 'picture', pos: [7, 5, 14], facing: 'north', wood: 'dark_oak', colour: 'yellow' },
  { room: 'Dais', kind: 'rug', part: 'rug', cellsAbs: [[5, 2, 12], [6, 2, 12], [7, 2, 12], [8, 2, 12], [9, 2, 12], [6, 2, 13], [8, 2, 13]], colour: 'red' },
]
export const LAMP_POSTS = [[4, 2, 13], [10, 2, 13]]

// The walking line: up the steps, through the porch and the door, down the hall across the ring, up the step to the post.
export const WALK = [[7, 0, -1], [7, 0, 0], [7, 0, 1], [7, 0, 2], [7, 1, 3], [7, 0, 4], [7, 0, 6], [7, 0, 8], [7, 0, 10], [7, 1, 11], [7, 1, 12]]

export const OUTSIDE = {
  flowerBeds: [{ x0: 1, x1: 2, z: 2, plants: ['red_tulip', 'poppy'] }, { x0: 12, x1: 13, z: 2, plants: ['poppy', 'red_tulip'] }],
  standards: [[3, 0, 1], [11, 0, 1]],
}
