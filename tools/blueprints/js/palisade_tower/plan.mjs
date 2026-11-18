// The Palisade Tower (`palisade_tower`, one level), the plan: drawn before any block. ROADMAP 52.3 (the method of
// 49); the brief is tools/blueprints/briefs/palisade_tower.md. recipe.mjs assembles it from the parts library.
// One kit with palisade and palisade_gate (see the kit note below and in their plans).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Hatted Lookout. A square stockade of the wall's own stakes on a stone foot, four pale corner posts running
//      from the ground up through a breastwork to carry a tented spruce hat; a walk-in ground room, a ladder up through
//      a hatch to a lookout deck under the hat, open on all four sides at eye height. Story: the wall's stakes stood up
//      on end into a square and roofed, the hamlet's watchman's box at every corner. The hat is the kit's tallest
//      shape and says "lookout" from across the valley.
//   B. The Open Stage. Four tall log legs and a bare platform on top with a rail, no walls, no roof (a frontier
//      watch stand). Story: built in a day. Light and see-through, but it shelters nothing, its deck stations have
//      no cover, and at a corner it reads as scaffolding left behind.
//   C. The Blockhouse. A two-storey log block with an overhanging upper storey (a frontier blockhouse), arrow slits,
//      a flat top. Story: the American frontier's corner fort. The jetty needs room past 5 x 5 that the plot doesn't
//      have; at 5 x 5 inside the brief's box it is a log cube.
// Strongest: A. It grows straight out of the segment (the same stakes, the same stone foot) and the gate (the same
// pale posts, the same plank-spruce roof), so the three read as one kit; and it gives the wall its hero: a hat that
// stands more than twice the stakes' height at every corner, which is what makes a palisade look like a fort and not
// a fence. Every station is on a deck a guard reaches from the ground room under it.
//
// Coordinates: x across (0..4), y up (0 = the layer on the clicked block), z from the front (z = 0, on the wall line,
// facing the village) to the back (z = 4, the field). Exactly 5 x 5, 10 tall.
//
// The kit: the segment's and gate's walk is stood on at y 3; the tower's lookout deck is two higher, stood on at y 5,
// up its own ladder. Its walls are the segment's stakes (bark spruce, upright) on its cobble foot; its corner posts
// are the gate's pale jambs; its hat is the gate's roof, tented.
//
// Round 3 (the critic's notes, briefs/palisades_52_3_critic.md, round 2): no trapdoor in any wall: every panel is
// a full block, the shadow comes from stairs (the panel's top course a stair whose upper half steps back under the
// girt; each loophole a stair hung upside down, its slot a half-block recess); the doorway is a real spruce door (the
// brief allows it: the tower may then be a breach, its door mended like the gate's); the hat's eave is kicked out over
// the lookout on dark-oak brackets (stairs hung upside down round y 7), and a dark-oak finial post crowns it. The
// hatch keeps a trapdoor over the ladder: a hatch is what a trapdoor is for, and it shuts the ground room off from
// the open lookout (holes.py).
//
// Masses (named parts):
//   Posts      stripped oak logs at the four corners, y 0..7: from the ground to the hat's eaves.
//   Foot       cobblestone round the base between the posts (y 0), but the doorway.
//   Panels     bark spruce stakes upright between the posts, y 1..2; the top course (y 3) a dark-oak stair whose upper
//              half steps back under the girt (the frame's shadow line). The back's middle column (2, 1..5, 4) a whole
//              log the ladder hangs on.
//   Doorway    (2, 0..1, 0), in the front's middle (x 0..1 stay whole: a segment butts there): a spruce door, shut.
//   Loopholes  on the outer faces at y 2: back (1, 2, 4) and (3, 2, 4), sides (0, 2, 2) and (4, 2, 2): a dark-oak stair
//              hung upside down, its foot inside, a half-block slot open to the outside under it.
//   Ground room  x 1..3, z 1..3, y 0..3, hollow: a guard walks in under the deck (every station 1-2 blocks over him);
//              a lantern hung under the deck by the door.
//   Girt       stripped oak logs laid round the top of the panels (y 4): the deck's edge, the pale band.
//   Deck       spruce planks over the ground room (x 1..3, z 1..3, y 4); the hatch at (2, 4, 3) a trapdoor hung open
//              over the ladder (climbable, as the game climbs an open trapdoor over a ladder): eight battle stations.
//   Ladder     on the back wall, (2, 0..3, 3) and (2, 5, 3) over the hatch (the brief's ladder at (2, 5, 3)), facing
//              north, so a player steps off onto the deck.
//   Breastwork bark spruce logs laid round the deck at y 5 (waist-high); the lookout open over it at y 6 between the
//              posts: an archer standing on the deck sees out on all four sides.
//   Eave       dark-oak stairs hung upside down round y 7 between the posts, their foot inside: the hat's eave kicked
//              out over the lookout, a soffit with the opening's shadow under it.
//   Hat        a tented roof of spruce stairs on the posts' heads (a ring at y 8, the inner ring at y 9), a dark-oak
//              fence finial at its middle (2, 9, 2): the centre column's top; a lantern hung from it lights the lookout.
//
// The centre column (2, y, 2): the deck (y 4), the lantern (y 8) and the finial (y 9). The door is the tower's only
// gate block.

export const SIZE = [5, 10, 5] // w, h, d (brief: exactly 5 x 10 x 5)

// Palette (60/30/10): bark spruce (the stakes, breastwork, door) / pale stripped oak posts and girt, a cobble foot /
// accents: the plank-spruce hat, dark-oak trim (eave brackets, the panels' shadow course, loopholes, the finial),
// lanterns. Why: the same dark spruce as the wall, framed by pale posts that
// draw the tower's corners and its deck line from far off; the hat is warm plank spruce, lighter than the walls.
export const PALETTE = {
  post: 'stripped_oak_log',
  foot: 'cobblestone',
  board: 'spruce_trapdoor', // the hatch only
  stake: 'spruce_log',
  girt: 'stripped_oak_log',
  deck: 'spruce_planks',
  roof: { full: 'spruce_planks', stairs: 'spruce_stairs', slab: 'spruce_slab' },
  cap: 'dark_oak_slab',
  trim: 'dark_oak_stairs', // the panels' top course, the loopholes, the eave brackets
  finial: 'dark_oak_fence',
  door: 'spruce_door',
}

export const MASSES = {
  box: { x0: 0, z0: 0, x1: 4, z1: 4 },
  posts: { y0: 0, y1: 7 },
  foot: { y: 0 },
  panels: { y0: 1, y1: 2, shadowY: 3 },
  eave: { y: 7 },
  spine: { x: 2, z: 4, y0: 1, y1: 5 }, // the ladder's backing log
  door: [[2, 0, 0], [2, 1, 0]], doorFacing: 'north',
  loopholes: [[1, 2, 4], [3, 2, 4], [0, 2, 2], [4, 2, 2]],
  girt: { y: 4 },
  deck: { x0: 1, z0: 1, x1: 3, z1: 3, y: 4 },
  hatch: [2, 4, 3],
  ladder: { x: 2, z: 3, ys: [0, 1, 2, 3, 5], facing: 'north' },
  breastwork: { y: 5 },
  hat: { y: 8, top: 9 },
}

export const ROOMS = [
  { name: 'Ground room', use: 'the way in and up; guards stand here to be set on the deck', box: [1, 0, 1, 3, 3, 3] },
  { name: 'Lookout', use: 'archers on the deck under the hat', box: [1, 5, 1, 3, 6, 3] },
]
export const FURNITURE = []
export const LIGHTS = [[1, 3, 1], [2, 8, 2]] // under the deck by the door; hung from the finial

// The walking line: the village ground (2, 0, -1) -> the doorway (2, 0, 0) -> the ground room (2, 0, 1..2) -> up the
// ladder (2, 0..5, 3) -> the deck (feet at y 5), round it.
export const WALK = [[2, 0, -1], [2, 0, 0], [2, 0, 2], [2, 5, 3], [1, 5, 2], [3, 5, 2]]
