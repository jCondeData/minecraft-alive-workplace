// The Observatory (a Wonder, ROADMAP 52.6), the plan: drawn before any wall. Four stages, each one only adding to the
// last and each standing, grounded and tidy on its own (tools/blueprints/briefs/observatory.md). recipe.mjs draws it
// from the parts library (../parts, with ../parts/observatory_instruments.mjs grown for it) in passes per stage.
//
// Three rough concepts (massing, roof, plan, one line of story each):
//   A. The Tower on the Hill: a square terrace, a round tower in its middle, a copper dome on top. Story: the
//      astronomers climbed as high as they could. The safe one: it reads as "a tower", like every village tower, and
//      stages II-IV are just "taller".
//   B. The Orrery Court: a low walled circle open to the sky, a ring of plinths round a great armillary sphere, no roof
//      at all. Story: the sky is the roof. Beautiful from above, but from 20 blocks away it is a wall: no silhouette.
//   C. The Great Gnomon: a stepped stone monument, a sundial's gnomon as big as a building. One straight stair climbs
//      from the ground at the front up the gnomon's sloping edge, over a terrace and its quadrant scales, into a round
//      drum, and under a copper dome a telescope reaches out sideways. Story: the village first reads the sun's shadow
//      on stone (the gnomon and its scales), then climbs that same shadow-line to read the night (the dome).
//   Strongest: C. Its outline is unlike anything else in a village (a 17-high diagonal wedge leading the eye to a dome,
//   with a tube cutting the skyline), it is a walking line as much as a shape (you climb the instrument), and its four
//   stages are four different things (terrace, gnomon, drum, dome), each a finished monument by itself. Built: C.
//
// Story: the Observatory of the Great Gnomon. The village builds it from the deep stone (deepslate, dark like the
// night it watches), the scales a pale smooth stone so a shadow reads sharp on them, the dome of copper that greens
// with the years, gold where something is measured (the noon marks, the telescope's bands, the star in the floor).
//
// Coordinates: x across the front (west 0 -> east 30), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Plot 31 x 31. Every stage's every block reaches y 0.

export const SIZE = [31, 43, 31]

// Palette (60/30/10): deepslate bricks and tiles (the masses) / polished blackstone and smooth stone (plinths,
// courses, pilasters, the scales) / copper, gold and amethyst (dome, telescope, marks). Why: one dark stone family
// reads as one monument from far off; pale scales and the copper dome are the only light things, so the eye goes to
// the instruments; gold only where something is measured.
export const PALETTE = {
  mass: 'deepslate_bricks',
  massAlt: [['deepslate_bricks', 8], ['cracked_deepslate_bricks', 1]],
  tile: 'deepslate_tiles',
  floor: 'polished_deepslate',
  plinth: 'polished_blackstone_bricks',
  pier: 'polished_blackstone',
  trimStairs: 'polished_blackstone_brick_stairs',
  stairs: 'deepslate_brick_stairs',
  scale: 'smooth_stone',
  curb: 'smooth_stone_slab',
  tick: 'polished_blackstone',
  mark: 'gold_block',
  copper: 'cut_copper',
  lens: 'tinted_glass',
  glass: 'glass',
  pane: 'glass_pane',
  star: 'amethyst_block',
  post: 'polished_blackstone_wall',
  wood: 'dark_oak',
  books: 'bookshelf',
}

// Masses, in the order the stages raise them.
export const MASSES = {
  court: { x0: 0, x1: 30, z0: 0, z1: 30, y: 0 }, // I: the paved court, its border band and four corner gardens
  podium: { x0: 3, x1: 27, z0: 8, z1: 29, top: 5 }, // I: the Star Terrace: walls y 1-4, its deck at y 5, parapet y 6
  hall: { x0: 4, x1: 26, z0: 9, z1: 28, y0: 1, y1: 4 }, // I: the Chart Hall inside the terrace, its walls two thick (deep reveals)
  flights: [{ x0: 4, x1: 7 }, { x0: 23, x1: 26 }], // I: twin flights at the front corners, z 3-7, up to the deck
  gnomon: { x0: 12, x1: 18, z0: 1, z1: 16, y0: 1, landing: 16 }, // II: one block up per block back, z 1 (y 1) to z 16
  arch: { z0: 5, z1: 6, y1: 3 }, // II: a blind arch on each face of the gnomon's foot (tiles framed by a lintel)
  quadrants: { z0: 9, z1: 13, base: 6, R: 6 }, // II: a quarter-circle scale against each side of the gnomon
  tier: { x0: 5, x1: 25, z0: 14, z1: 28, y0: 6, y1: 12 }, // III: the upper terrace the drum stands on
  drum: { cx: 15, cz: 22, inner2: 20, outer2: 42, y0: 13, y1: 31, floor: 16 }, // III: two blocks thick, floor y 17
  dome: { y0: 32, R: 8, t: 2 }, // IV: copper, a glazed shutter strip on the east, the telescope out through it
  telescope: { from: [15, 27, 22], dir: [Math.SQRT1_2, Math.SQRT1_2, 0], t0: -5, t1: 15, radius: 1.4, pier: [17, 25] },
}

export const ROOMS = [
  { name: 'Chart Hall', stage: 1, use: 'the cartographer\'s and the reader\'s hall: charts, books, the star in the floor', box: [5, 1, 10, 25, 4, 27] },
  { name: 'Observing Room', stage: 3, use: 'the drum\'s high room: benches round the wall; in stage IV the telescope and the dome', box: [10, 18, 17, 20, 29, 27] },
]

export const OPENINGS = {
  hallDoors: [{ at: [11, 1, 8], facing: 'south' }, { at: [19, 1, 8], facing: 'south' }],
  drumDoor: { at: [15, 17, 17], facing: 'south' }, // at the gnomon's top landing, through the drum's two-thick wall
  hallWindows: [ // single panes in the long walls, each between wall blocks
    ...[11, 15, 20, 25].flatMap(z => [{ cells: [[3, 2, z], [3, 3, z]] }, { cells: [[27, 2, z], [27, 3, z]] }]),
    { cells: [[9, 2, 8], [9, 3, 8]] }, { cells: [[21, 2, 8], [21, 3, 8]] },
  ],
  drumWindows: { angles: [0, 45, 90, 135, 225, 270, 315], y0: 20, y1: 27 }, // degrees from +z (the back); 180 is the door
}

// Every piece of furniture, footprint first (Wonder parts record no furniture spots; these are the plan, drawn with
// the furniture parts). The lectern and the cartography table are real job sites (a Librarian, a Cartographer).
export const FURNITURE = [
  // Chart Hall: the chart desk at the back (a cartography table between two lecterns), books along the back wall,
  // reading tables in both aisles, lanterns hung from the deck.
  { room: 'Chart Hall', part: 'block', pos: [15, 1, 28], block: 'cartography_table' },
  { room: 'Chart Hall', part: 'block', pos: [13, 1, 28], block: 'lectern', props: { facing: 'north', has_book: 'false', powered: 'false' } },
  { room: 'Chart Hall', part: 'block', pos: [17, 1, 28], block: 'lectern', props: { facing: 'north', has_book: 'false', powered: 'false' } },
  ...[5, 25].flatMap(x => [16, 21].flatMap(z => [
    { room: 'Chart Hall', kind: 'table', part: 'table', pos: [x, 1, z], facing: 'north', wood: 'dark_oak', colour: 'blue' },
    { room: 'Chart Hall', kind: 'table', part: 'table', pos: [x, 1, z + 1], facing: 'north', wood: 'dark_oak', colour: 'blue' },
    { room: 'Chart Hall', kind: 'chair', part: 'chair', pos: [x === 5 ? 6 : 24, 1, z], facing: x === 5 ? 'west' : 'east', wood: 'dark_oak' },
    { room: 'Chart Hall', kind: 'chair', part: 'chair', pos: [x === 5 ? 6 : 24, 1, z + 1], facing: x === 5 ? 'west' : 'east', wood: 'dark_oak' },
  ])),
  ...[9, 21].flatMap(x => [11, 16, 21, 26].map(z => ({ room: 'Chart Hall', kind: 'lamp', part: 'hangingLamp', pos: [x, 4, z], facing: 'north', wood: 'dark_oak' }))),
  ...[13, 17].map(x => ({ room: 'Chart Hall', kind: 'lamp', part: 'hangingLamp', pos: [x, 4, 12], facing: 'north', wood: 'dark_oak' })),
  // Observing Room (stage III): a bench round the back wall, lanterns on posts, a lectern for the night's notes.
  ...[13, 14, 15, 16, 17].map(x => ({ room: 'Observing Room', kind: 'chair', part: 'chair', pos: [x, 17, 26], facing: 'north', wood: 'dark_oak' })),
  { room: 'Observing Room', part: 'block', pos: [11, 17, 22], block: 'lectern', props: { facing: 'east', has_book: 'false', powered: 'false' } },
  ...[[12, 19], [18, 19], [12, 25], [18, 25]].map(([x, z]) => ({ room: 'Observing Room', part: 'postLamp', pos: [x, 17, z] })),
  // Observing Room (stage IV): two chandeliers on chains from the dome
  ...[18, 26].map(z => ({ room: 'Observing Room', stage: 4, kind: 'lamp', part: 'hangingLamp', pos: [15, 28, z], chain: 7, facing: 'north', wood: 'dark_oak' })),
]

// The walking line: the court, up the gnomon's stair from its foot at z 1 to the landing and the drum's door, into the
// observing room; and from the court to either hall door, or up a corner flight to the terrace and the quadrants.
export const WALK = [
  [15, 1, 0], [15, 1, 1], [15, 5, 5], [15, 10, 10], [15, 16, 16], [15, 17, 17], [15, 17, 18], [15, 17, 20],
  [11, 1, 4], [11, 1, 7], [11, 1, 8], [11, 1, 12], [15, 1, 18], [15, 1, 27],
  [5, 1, 3], [5, 4, 7], [5, 5, 8], [5, 5, 11], [9, 5, 11],
]

// Outside the rooms: four corner gardens in their own soil, lamp posts at the court's front, lanterns on the parapet.
export const OUTSIDE = {
  gardens: [[0, 0], [28, 0]], // 3 x 3 each at the front corners, grass with flowers
  lampPosts: [[9, 1, 2], [21, 1, 2], [1, 1, 15], [29, 1, 15]],
  parapetLamps: [[3, 7, 8], [27, 7, 8], [3, 7, 29], [27, 7, 29]],
}
