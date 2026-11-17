// The Fire Pit (`fire_pit`, one level only), the plan: drawn before any block. ROADMAP 49 (lane B); the brief is
// tools/blueprints/briefs/fire_pit.md. recipe.mjs assembles it from the parts library (../parts, fires.mjs grown for it).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Hearthstones. A low round stone dais, the fire sunk in its middle inside a brick curb, a ring of standing
//      stones that climbs from the front to the back, where two tall stones and a lintel (a trilithon) frame the fire
//      like a doorway; no roof, benches between the stones. Story: the oldest place in the village, the ring the first
//      settlers stood up and sat in before there was a single house; the village still gathers there after dark.
//   B. The Watch Shelter. A square timber pavilion, open on all four sides, a hipped shingle roof with a smoke louver
//      over a fire in the middle, benches under the eaves. Story: where the night watch keeps warm out of the rain.
//      A good silhouette, but it is a small house with no walls: under 8 blocks the roof sits on the benchers' heads,
//      and a fire under a roof is a hearth, not a pit.
//   C. The Cook Fire. A square of paving with a fire and a big spit, a woodpile, a chopping stump and a table: a
//      working camp kitchen. Story: where the village roasts the market-day ox. Honest and busy, but flat: nothing
//      stands more than two blocks up, so from across the village it reads as clutter on the ground.
// Strongest: A. It is the only one you know from across the village (a stone ring rising to a gate-like trilithon is
// a shape no other build in the mod has), it is a pit in the true sense (the fire sits half a block under the floor,
// a block under the curb, and the stones and benches all turn toward it), and it borrows C's best parts (the spit over
// the fire, the woodpile that feeds it) without C's flatness. The heights climb on purpose: two-block stones at the
// front so the lane looks straight in at the fire, four-block stones at the sides, and the five-high trilithon at the
// back, the hero, so from the road the fire is framed by the gate behind it.
//
// Coordinates: x across the front (west 0 -> east 8), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, the lane) to the back. 9 x 9, center (4, 4). No door: the lane starts at (4, 0, -1), straight onto
// the front step (4, 0, 0) and in between the two front stones.
//
// Masses (named parts the owner's feedback can point at):
//   Dais        a round stone floor at y 0 (radius about 4.2), its edge a ring of cobble steps climbing inward, so the
//               ring is walked onto from any side; inside, a trodden ring of ash and cobble round the hearth.
//   Pit         the campfire at the center (4, 0, 4), sunk in the dais's layer on its brick hearthstones, a fired-brick
//               curb round it at y 1 (stairs on the sides, slabs on the corners).
//   Spit        dark-oak fence uprights on the pit's west and east sides, a bar across over the fire (y 2).
//   Front stones two short stones at (2, 1) and (6, 1), y 0..2, each with a lantern on its head: the gate posts of
//               the ring, lighting the way in.
//   Side stones two four-block stones at (0, 4) and (8, 4), weathered to a slant (a stair cap): the side benches'
//               backs.
//   Trilithon   the hero: two stones at (2, 7) and (6, 7), y 0..4, under a dressed tuff lintel (y 5, x 1..7) whose proud
//               ends each hang a lantern. The back bench sits under it, facing the lane through the fire.
//   Woodpile    a stack of oak logs in the back west corner outside the ring (the fuel), and a chopping stump in the
//               back east corner.
export const SIZE = [9, 6, 9] // w, h, d (brief: at most 9 x 8 x 9)

// Palette (60/30/10): stone (the dais, the steps, the stones, the lintel) / spruce and dark oak (the benches, the
// spit, the stump) with oak logs in the woodpile / accents: the fire and its fired-brick curb, the lanterns, moss.
// Why: gray, mossy, mixed stone reads as gathered from the field and old; tuff's gray-green in the stones and lintel
// sets them apart from the cobble floor they stand in; the warm wood is all where people sit or work; and everything
// that glows or is red (the fire, the brick round it, the lights) is the 10 % the eye goes to.
export const PALETTE = {
  floor: [['cobblestone', 4], ['stone', 2], ['andesite', 2], ['mossy_cobblestone', 2]],
  ash: [['coarse_dirt', 3], ['cobblestone', 2], ['tuff', 1]], // the trodden ring round the hearth
  hearth: 'bricks', // the hearthstones under the curb
  step: [['cobblestone_stairs', 3], ['mossy_cobblestone_stairs', 2], ['stone_stairs', 1]],
  curb: { stairs: 'brick_stairs', slab: 'brick_slab' },
  stone: [['stone', 3], ['tuff', 2], ['andesite', 1], ['mossy_cobblestone', 1]],
  stoneCap: 'tuff_stairs',
  lintel: { full: 'tuff_bricks', stairs: 'tuff_brick_stairs' },
  bench: 'spruce', // stair seats with trapdoor arms
  spit: 'dark_oak_fence',
  fuel: 'oak_log',
  stump: 'stripped_spruce_log',
}

export const MASSES = {
  dais: { cx: 4, cz: 4, r2: 18, y: 0 },
  pit: { pos: [4, 0, 4], open: ['west', 'east'] }, // the spit's uprights take the west and east curb cells
  spit: { xa: 3, xb: 5, z: 4, y0: 1 },
  frontStones: [[2, 1], [6, 1]], // y 0..2, lantern at y 3
  frontHeight: 3,
  sideStones: [[0, 4, 'west'], [8, 4, 'east']], // y 0..3, a stair cap at y 4 sloping outward
  sideHeight: 4,
  trilithon: { xa: 2, xb: 6, z: 7, y0: 0, topY: 5, over: 1, tips: false }, // lintel x 1..7
  woodpile: [[0, 0, 8], [1, 0, 8], [2, 0, 8], [0, 1, 8], [1, 1, 8], [0, 0, 7]],
  stump: [8, 0, 7],
}

// Rooms: none (an open ring). The space inside the stones is the Ring.
export const ROOMS = [{ name: 'Ring', use: 'sitting round the fire after dark', box: [1, 1, 1, 7, 4, 7] }]

// Openings: none drawn (no walls). The lane comes in at the front between the two front stones.
export const OPENINGS = { approach: { lane: [4, 0, -1], step: [4, 0, 0], standAt: [4, 1, 1] } }

// Every piece of furniture, footprint first. Three stair benches turned to the fire: the west and east ones with
// trapdoor arms, the back one between the trilithon's stones (they are its arms). Recorded as couch spots.
export const FURNITURE = [
  { room: 'Ring', kind: 'couch', part: 'couch', pos: [1, 1, 3], facing: 'east', length: 3, arms: true, wood: 'spruce' },
  { room: 'Ring', kind: 'couch', part: 'couch', pos: [7, 1, 5], facing: 'west', length: 3, arms: true, wood: 'spruce' },
  { room: 'Ring', kind: 'couch', part: 'couch', pos: [3, 1, 7], facing: 'north', length: 3, arms: false, wood: 'spruce' },
]

// Lights: a lantern standing on each front stone's head, one hung under each end of the lintel.
export const LIGHTS = { standing: [[2, 3, 1], [6, 3, 1]], hanging: [[1, 4, 7], [7, 4, 7]] }

// The walking line: the lane -> the front step -> between the front stones -> round the pit on the ash ring (a
// builder stands there and reaches the fire, the spit and every bench) -> out past the back bench's ends to the
// woodpile and the stump.
export const WALK = [[4, 0, -1], [4, 1, 0], [4, 1, 1], [4, 1, 2], [2, 1, 2], [2, 1, 4], [2, 1, 6], [4, 1, 6], [6, 1, 6], [6, 1, 4], [6, 1, 2]]
