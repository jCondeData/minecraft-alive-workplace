// Gazebo (49 rebuild), the plan: drawn before any post. Concepts, story, palette, masses, the bays, every furnishing's
// footprint and the walking line; recipe.mjs then assembles it from the parts library (../parts).
//
// Three concepts (one level only, 13 x 16 x 13 at most, nothing a villager takes a job or a bed from):
//   A. The Lantern Tent. An 11 x 11 octagon: a stepped stone dais, eight white posts, a bell-cast slate tent roof
//      (shallow at the eaves, steep at the top) rising to a little open cupola with a lantern in it and a lightning-rod
//      finial. Benches round three faces look in at a table; the front face is open, up a step from the lane.
//      Story: the Steward's last garden wish is a dry seat in the rain, and a light the whole village sees at night.
//   B. The Pond Tea-house. 9 x 11, a timber deck on stilts over a small lily pool, a two-tier hipped roof; you cross
//      a plank to sit over the water. Story: a quiet place by the water. Weak here: it competes with the Fountain (the
//      wish before it) and its water and stilts eat a plot for a rectangle that reads like a small house.
//   C. The Rose Arbor. 7 x 9, an open pergola of beams under azalea, benches facing a sundial. Story: a walk under
//      flowers. Weak here: leaves are no shelter, it is a Lantern Pergola again, and from 20 blocks it is a lattice.
// A is the strongest: a gazebo stands alone in a park and is seen from every side, and only the octagon gives every
// side the same worked face; the tent roof with its cupola is an outline you know at a glance, and the lantern in the
// cupola makes it a landmark after dark. It costs 11 x 11, two more than the minimum, and earns it with eight bays.
//
// Coordinates: x across the front (west 0 -> east 10), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Centre (5, 5). Ring numbers come from octLevel(5, 5, 2): ring 4 is the post line, ring 5
// the step skirt and the eave.
//
// Masses (named parts the owner's feedback can point at):
//   Dais      the stone floor, rings 0..4 at y 0, laid as a rosette; ring 5 a skirt of steps climbing in all round,
//             so the lane's first block (in front of the middle of the z = 0 edge) is a step.
//   Posts     eight white posts on the ring-4 corners, y 1..3; a white beam ring (ring 4) at y 4 ties them.
//   Bays      eight: the front one open (the way in), the back and side ones a railing with a bench inside it, the four
//             corner (diagonal) ones a flower urn on a pedestal under a swag of azalea.
//   Brackets  on every straight bay, two upside-down stairs under the beam beside the posts: a shallow arch.
//   Roof      a stone-slate tent (tuff tiles, a dark deepslate eave line): ring 5 a flared kick at y 4, ring 4 y 5, ring 3 y 6 (45 degrees), then ring 2 y 8 and
//             ring 1 y 10 (steep, full tiles under each), so the outline is bell-cast; birch boards line the shallow
//             courses inside, so the ceiling reads light.
//   Cupola    an open louver on the apex (3 x 3 pale birch sill at y 10, four fence posts, a slate cap at y 12), a lantern
//             hung inside it, a lightning rod on top at y 13.
//   Gardens   the four corners of the plot outside the octagon: low flowers at the front, tall ones at the back.

export const SIZE = [11, 14, 11] // w, h, d (limit 13 x 16 x 13)
export const CENTRE = [5, 5]
export const CUT = 2

// Palette (60/30/10): stone (the tuff-tile roof, the andesite dais) / pale birch (posts, beam, rails, benches, the
// cupola's sill) / green and flowers (azalea swags, urns, corner beds) with lantern brass, the dark deepslate eave
// and cap as the line that draws it. Why: a pale garden frame under a stone hat is the gazebo's own look, cool and
// light against a village of brown houses (a deepslate roof all over read as one dark mass in the first render); the
// gray stone grounds it and the planting ties it to the Gardens zone it stands in.
export const PALETTE = {
  roof: { full: 'tuff_bricks', stairs: 'tuff_brick_stairs', kick: 'deepslate_tile_stairs' }, // a dark eave line under a lighter slate
  cap: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs' }, // the cupola's hat matches the eave line
  post: 'stripped_birch_log',
  beam: 'stripped_birch_log', // laid along each face
  lining: 'birch_planks',
  wood: 'birch', // fences, stairs (benches and brackets), trapdoors
  dais: { rim: 'stone_bricks', field: 'polished_andesite', inner: 'andesite', heart: 'chiseled_stone_bricks', step: 'stone_brick_stairs' },
  urn: { stand: 'stone_brick_wall', pots: ['potted_red_tulip', 'potted_azure_bluet', 'potted_pink_tulip', 'potted_cornflower'] },
  swag: 'flowering_azalea_leaves',
  soil: 'grass_block',
  finial: 'lightning_rod',
  tablecloth: 'white_carpet',
}

// Ring heights of the roof, ring 0 (apex) to ring 5 (the kick)
export const ROOF = { heights: [10, 10, 8, 6, 5, 4], beamY: 4, postTop: 3, cupolaY: 10 }

// The eight posts (ring 4's corners), clockwise from the front west
export const POSTS = [[3, 1], [7, 1], [9, 3], [9, 7], [7, 9], [3, 9], [1, 7], [1, 3]]

// The bays between them: what fills each (the sitters face into the gazebo)
export const BAYS = [
  { name: 'front', from: [3, 1], to: [7, 1], fill: 'entrance' },
  { name: 'east', from: [9, 3], to: [9, 7], fill: 'bench', bench: { line: 8, facing: 'west' } },
  { name: 'back', from: [7, 9], to: [3, 9], fill: 'bench', bench: { line: 8, facing: 'north' } },
  { name: 'west', from: [1, 7], to: [1, 3], fill: 'bench', bench: { line: 2, facing: 'east' } },
  { name: 'front east', cell: [8, 2], fill: 'urn', pot: 0 },
  { name: 'back east', cell: [8, 8], fill: 'urn', pot: 1 },
  { name: 'back west', cell: [2, 8], fill: 'urn', pot: 2 },
  { name: 'front west', cell: [2, 2], fill: 'urn', pot: 3 },
]

// Furnishings (a decoration: none is a furniture spot, so nothing hands out a seat, a job or a bed)
//   table      a birch fence leg under a white cloth at the center (5, 1..2, 5), the lantern hanging over it
//   benches    9 stair seats, three on each of the east, back and west faces, inside the railings (cells from BAYS)
//   lantern    on a chain from the cupola's sill, down the open apex to y 5 over the table
//   urns       a stone pedestal (wall) with a potted flower on it in each corner bay, azalea swagged above at y 3
export const TABLE = [5, 1, 5]
export const LANTERN = { at: [5, 5, 5], chain: 4 }

// Corner beds: the plot's corners outside the octagon (ring 6+ at y 0), grass with flowers in it
export const BEDS = [
  { corner: [0, 0], plants: ['poppy', 'oxeye_daisy', 'cornflower'] },
  { corner: [10, 0], plants: ['cornflower', 'allium', 'poppy'] },
  { corner: [0, 10], plants: ['lilac', 'peony', 'rose_bush'] },
  { corner: [10, 10], plants: ['peony', 'rose_bush', 'lilac'] },
]

// The walking line: the lane's spot -> the front step -> the open front bay -> round the table to each bench
export const WALK = [[5, 0, -1], [5, 0, 0], [5, 1, 1], [5, 1, 2], [5, 1, 3], [4, 1, 4], [3, 1, 5], [5, 1, 7], [7, 1, 5]]
