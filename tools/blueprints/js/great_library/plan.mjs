// The Great Library (a Wonder, ROADMAP 52.6), the plan: drawn before any wall. Four stages, each only adding to the
// last and each standing, grounded and tidy on its own (tools/blueprints/briefs/great_library.md). recipe.mjs draws it
// from the parts library (../parts, with ../parts/rotunda_helix.mjs grown for it) in passes per stage.
//
// Three rough concepts (massing, roof, plan, one line of story each):
//   A. The Temple of Books: one long pale hall under a copper gable, a columned portico at the front, a low dome over a
//      crossing at the back. Story: the scholars built the grandest hall they knew. The safe one: from 20 blocks away it
//      is a big house with columns, and its stages are "longer" and "taller".
//   B. The Ziggurat of Shelves: a square stepped tower of open reading terraces, each terrace a ring of book stacks
//      under the sky, a glass lantern on the top step. Story: knowledge climbs. A strong outline, but its rooms are
//      roofless rings (books in the rain), the portico and dome the stage titles name don't belong to it, and every
//      side is the same.
//   C. The Scroll Rotunda: a great round drum in the middle of a cross of four short gabled book wings (the Stacks), a
//      quartz portico on the front arm and a ribbed copper dome with a glass lantern over the drum. Inside the drum a
//      walkway climbs two full turns up a wall of books, and a band of windows rides the same helix just above it, so
//      on the outside the drum carries a spiral of glass winding up toward the dome like a scroll being unrolled.
//      Story: the library is a scroll you walk; the reader climbs the words to the light.
//   Strongest: C. Its outline is a cross of temple fronts round a dome, readable from every side (four gables, the drum
//   between them, the dome over all); the helix is a thing no other village building has, inside (a walk) and outside
//   (the glass spiral in the drum's four open quarters); and its four stages are four different things: the low stacks
//   round an open court, their upper galleries and copper roofs, the drum with its helix and the enchanting table, then
//   the dome and the portico. Built: C.
//
// Story: the Great Library of the Unrolled Scroll. Pale polished diorite and smooth stone walls on a stone-brick
// podium (darkest low), copper roofs and dome that will green together over the years, quartz only where the library
// is ceremonial (the portico, sills of the spiral, capitals), dark oak floors and stairs inside.
//
// Coordinates: x across the front (west 0 -> east 40), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Plot 41 x 41, 34 high. The rotunda's centre is (20, 22); its floor is the podium's top at
// y 2, so the main floor stands at y 3 (the enchanting table at (20, 3, 22), as the showcase scene needs).

export const SIZE = [41, 34, 41]
export const STAGES = 4

// Palette (60/30/10): polished diorite and smooth stone (walls, courses, pilasters, paving) / copper (roofs, dome) and
// dark oak (floors, stairs, furniture) / quartz and glass (portico, sills, the spiral and the lantern). Why: one pale
// stone reads as one monument; the copper is the only colour on the skyline; quartz marks the ceremonial parts.
export const PALETTE = {
  wall: 'polished_diorite',
  course: 'smooth_stone',
  courseSlab: 'smooth_stone_slab',
  podium: 'stone_bricks',
  podiumAlt: [['stone_bricks', 9], ['cracked_stone_bricks', 1]],
  podiumStairs: 'stone_brick_stairs',
  ground: [['polished_andesite', 5], ['andesite', 1]],
  paving: 'smooth_stone',
  floor: 'dark_oak_planks',
  wood: 'dark_oak',
  roof: { full: 'cut_copper', stairs: 'cut_copper_stairs', slab: 'cut_copper_slab' },
  rib: 'copper_block',
  binding: 'cut_copper', // the copper courses over and under the drum's glass spiral
  column: 'quartz_pillar',
  colBase: 'smooth_stone',
  capital: 'chiseled_quartz_block',
  beam: 'quartz_block',
  sill: 'smooth_quartz',
  tympanum: 'smooth_quartz',
  pane: 'glass_pane',
  ribbon: 'glass', // the drum's spiral: a ribbon on a curve, where a pane run can't join from cell to cell diagonally
  books: 'bookshelf',
  walk: 'dark_oak_slab',
  post: 'dark_oak_fence',
  hedge: 'oak_leaves',
  balustrade: 'polished_diorite_wall',
}

// The rotunda: one centre, three rings (the wall, three thick; a lining of books, two; the walkway, about two and a half)
export const ROTUNDA = {
  cx: 20, cz: 22,
  shell: { inner2: 72, outer2: 121, y0: 3, y1: 23 }, // the drum's wall, three thick
  lining: { inner2: 42, outer2: 72, y0: 3, y1: 22 }, // books up the whole inside, two deep
  walk: { inner2: 16, outer2: 42 }, // the helix
  helix: { cx: 20, cz: 22, start: 0, dir: 1, y0: 3, rise: 8, top: 19 }, // from the back door, east first, two turns
  band: [1, 2], // the window band rides 1-2 blocks over the walkway's surface: eye height for the climbing reader
  cornice: { inner2: 121, outer2: 144, y: 23 },
  dome: { y0: 24, R: 9.5, t: 2.2, squash: 0.78, ribs: 12 },
  lantern: { y0: 32, top: 33 },
  table: [20, 3, 22],
  doors: [{ at: [20, 3, 12], facing: 'north', through: [13, 14, 15] }, { at: [20, 3, 32], facing: 'south', through: [29, 30, 31] }],
  ceiling: { y: 23, oculus2: 4 }, // stage III closes the drum with a coffered inner ceiling, a glass oculus in it (the inner shell of a double dome)
}

// The four wings (the Stacks), the cross's arms: each a rectangle of walls, its outer gable end, its door outside, the
// flight inside up to its gallery, its window bays. Ground storey walls y 3-8, deck y 9 (stage I, a terrace with a
// parapet at y 10); gallery storey y 11-12 under an open copper roof from the eave at y 13 to the ridge at y 20 (stage II),
// low enough that the drum stands clear of the wings from y 14 up.
export const WINGS = {
  front: { x0: 14, x1: 26, z0: 6, z1: 12, axis: 'z', outer: 'z0', door: { at: [20, 3, 6], facing: 'north' },
    flight: { cells: [16, 17, 18, 19, 20, 21, 22].map(x => [x, 7]), facing: 'east', hatch: 'south' } },
  back: { x0: 14, x1: 26, z0: 32, z1: 38, axis: 'z', outer: 'z1', door: { at: [20, 3, 38], facing: 'south' },
    flight: { cells: [16, 17, 18, 19, 20, 21, 22].map(x => [x, 37]), facing: 'east', hatch: 'north' } },
  west: { x0: 2, x1: 10, z0: 16, z1: 28, axis: 'x', outer: 'x0', door: { at: [8, 3, 16], facing: 'north' },
    flight: { cells: [26, 25, 24, 23, 22, 21, 20].map(z => [9, z]), facing: 'north', hatch: 'west' } },
  east: { x0: 30, x1: 38, z0: 16, z1: 28, axis: 'x', outer: 'x1', door: { at: [32, 3, 16], facing: 'north' },
    flight: { cells: [26, 25, 24, 23, 22, 21, 20].map(z => [31, z]), facing: 'north', hatch: 'east' } },
}
export const WING_Y = { floor: 2, wall0: 3, wall1: 8, deck: 9, parapet: 10, gallery0: 11, gallery1: 12, eave: 13 }
// window bays: the posts (proud smooth-stone pilasters) and the windows between them, on each face
export const BAYS = {
  '13': { posts: [0, 4, 8, 12], windows: [2, 6, 10] }, // a 13-long face (offsets from its first cell)
  '9': { posts: [0, 4, 8], windows: [2, 6] },
  '7': { posts: [0, 6], windows: [3] },
}

// The podium and the forecourt: the terrace every wing and the drum stand on, steps up at the front
export const PODIUM = { x0: 1, x1: 39, z0: 3, z1: 39, top: 2 }
export const STEPS = { x0: 12, x1: 28 } // two steps at z 1 and 2

// The portico (stage IV): six quartz columns at z 3, an entablature at y 12, a copper gable roof and pediment over it
export const PORTICO = { x0: 12, x1: 28, z0: 3, z1: 5, columns: [12, 15, 18, 22, 25, 28], y0: 3, capital: 11, beam: 12, eave: 13 }

export const ROOMS = [
  { name: 'Map Room', stage: 1, use: 'the front wing: the cartography table, a reading lectern, the stair to the gallery', box: [15, 3, 7, 25, 8, 11] },
  { name: 'West Stacks', stage: 1, use: 'book stacks in bays, desks under the windows, a lectern', box: [3, 3, 17, 9, 8, 27] },
  { name: 'East Stacks', stage: 1, use: 'the same, mirrored', box: [31, 3, 17, 37, 8, 27] },
  { name: 'Back Stacks', stage: 1, use: 'stacks against the rotunda wall, a lectern, the stair up', box: [15, 3, 33, 25, 8, 37] },
  { name: 'Galleries', stage: 2, use: 'each wing\'s upper storey under its copper roof: walls of books, a reading table', box: [3, 10, 7, 37, 14, 37] },
  { name: 'Rotunda', stage: 3, use: 'the enchanting table in its ring of shelves, the centre lectern, the helix walk up the books', box: [12, 3, 14, 28, 22, 30] },
]

// Furniture (Wonder parts record no furniture spots; this is the plan, drawn with the furniture parts). Lecterns hold
// no book; the cartography table is the map room's.
const desk = (room, tx, z, cx, facing) => [
  { room, kind: 'table', part: 'table', pos: [tx, 3, z], facing, wood: 'dark_oak', colour: 'green' },
  { room, kind: 'chair', part: 'chair', pos: [cx, 3, z], facing, wood: 'dark_oak' },
]
export const FURNITURE = [
  { room: 'Map Room', part: 'block', pos: [24, 3, 10], block: 'cartography_table' },
  { room: 'Map Room', part: 'block', pos: [16, 3, 10], block: 'lectern', props: { facing: 'east', has_book: 'false', powered: 'false' } },
  { room: 'West Stacks', part: 'block', pos: [6, 3, 27], block: 'lectern', props: { facing: 'north', has_book: 'false', powered: 'false' } },
  { room: 'East Stacks', part: 'block', pos: [34, 3, 27], block: 'lectern', props: { facing: 'north', has_book: 'false', powered: 'false' } },
  { room: 'Back Stacks', part: 'block', pos: [24, 3, 35], block: 'lectern', props: { facing: 'west', has_book: 'false', powered: 'false' } },
  ...[18, 22, 26].flatMap(z => desk('West Stacks', 3, z, 4, 'west')),
  ...[18, 22, 26].flatMap(z => desk('East Stacks', 37, z, 36, 'east')),
  ...desk('Back Stacks', 16, 35, 17, 'west'),
  { room: 'Rotunda', stage: 3, part: 'block', pos: [20, 3, 26], block: 'lectern', props: { facing: 'north', has_book: 'false', powered: 'false' } },
]

// The walking line: up the steps, through the portico and the front door, the map room, the drum's front door, under
// the helix to the scholar's spot (20-21, 3, 18) facing the table; round the ring to the centre lectern; from the
// back door start of the helix two turns up to its top at y 19. Each wing: its door, its aisle, its flight to the
// gallery. The garden courts: out of the side wings' doors onto the terrace.
export const WALK = [
  [20, 1, 1], [20, 2, 2], [20, 3, 4], [20, 3, 6], [20, 3, 9], [20, 3, 12], [20, 3, 15], [20, 3, 18],
  [17, 3, 22], [20, 3, 25], [20, 3, 30], [21, 4, 29], [26, 7, 21], [20, 11, 28], [14, 15, 23], [19, 19, 27],
  [8, 3, 16], [8, 3, 20], [9, 3, 26], [9, 9, 20], [5, 10, 22],
]

// Outside: garden beds of their own soil in the four corner courts, hedges, lamp posts at the steps and corners
export const OUTSIDE = {
  beds: [[3, 4, 11, 12], [29, 4, 37, 12], [3, 31, 11, 37], [29, 31, 37, 37]], // x0, z0, x1, z1 on the terrace
  lampPosts: [[10, 3, 3], [30, 3, 3], [2, 3, 3], [38, 3, 3], [2, 3, 39], [38, 3, 39]],
}
