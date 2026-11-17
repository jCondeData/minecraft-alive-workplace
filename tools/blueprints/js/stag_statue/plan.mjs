// The Stag Statue (`stag_statue`, one level), the plan: drawn before any block. ROADMAP 49 (rebuild); the brief is
// tools/blueprints/briefs/stag_statue.md. recipe.mjs assembles it from the parts library (../parts, statues.mjs).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Watcher on the Crag. A carved stag standing broadside to the road on a low mossy crag, head up and turned
//      east, a wide spread of pale antlers; no roof, the plan an oval of rock edged with moss, ferns and a cut-stone
//      curb at the front, a lantern on a rubble post at each front corner. Story: on the founders' first morning a
//      red stag stood on the crag above the valley and watched them, then walked away east; the carver set him on his
//      rock just as he stood, mid-stride, the white of his rump to the west where they came from.
//   B. The Lodged Stag. A stag lying down with his legs folded, head high, on a round stone dais in a ring of clipped
//      azalea hedges, the heraldic "lodged" pose of a hundred inn signs. Story: the stag at rest, the village at peace.
//      Calm, and it leaves the antlers three layers to grow, but at block scale a folded body is a brown lump: with no
//      legs, from across a square it could be any animal, or a bench.
//   C. The Stag Plinth. A tall cut-stone pedestal with a cornice and steps, the stag small on top, hedged square
//      round it. Story: the park statue from every town square. It is the safe one, and the wrong one under an 8-block
//      ceiling: the pedestal eats three layers, the stag shrinks to a toy whose antlers are one fence high.
// Strongest: A. A quadruped reads from its side profile (four legs, back line, neck, head, antlers), so the stag stands
// broadside to the road and the front view is his profile; a walking stance staggers his legs so all four show
// between body and rock; the crag is only one layer, so every other layer goes to the animal (legs 2, body 1.5, neck
// rising to a head at y 5, antlers growing beside it and two layers above it). The rock, ferns and moss make it a moment (a stag
// on a hillside) rather than an object on a box.
//
// Coordinates: x across the front (west 0 -> east 8), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, the road) to the back (z = 6). The stag faces east (+x). No door: the lane starts at (4, 0, -1),
// one block in front of the middle of the front edge, onto the front curb step (4, 0, 0).
//
// Masses (named parts the owner's feedback can point at):
//   Crag      an oval of gathered rock at y 0 (x 0..8, z 0..6, corners cut), the stag standing on its spine (z 3..4);
//             three boulders a layer up: two under his muzzle (he steps toward higher ground), one behind his rump.
//   Curb      a cut-stone step along the middle of the front (x 2..6, z 0): where a villager stands to look.
//   Bracken   moss round the rim with ferns, two large ferns at the back, low flowers at the front (never in front of
//             his legs above y 1).
//   Stag      body x 1..6, z 3..4 (two thick), y 3 with a back line of slabs at y 4; the white rump (x 1); dark mane
//             and neck (x 6) rising to the head (x 7, y 5) and muzzle (x 8); four legs, 2 tall, staggered in a walk:
//             near side (z 3) at x 2 and 6, far side (z 4) at x 1 and 5. 
//   Antlers   pale branching beams from two burrs beside the head (x 7, y 5, z 2 and 5): a brow tine forward past the
//             muzzle, the beam up a layer, out and back, two tines up (y 7). Spread z 1..6, the widest thing on him.
//   Lamps     a lantern on a rubble post at each front corner of the oval (x 1 and 7, z 1).

export const SIZE = [9, 8, 7] // w, h, d (limit 9 x 8 x 9)

// Palette (60/30/10): gathered rock of the crag / the stag's woods (spruce body, dark oak mane, legs and muzzle) /
// pale birch antlers and rump, moss and greenery. Why: a red deer is a mid brown with a darker neck and legs, a pale
// rump and pale antlers; wood carries those colors with stairs, slabs and thin fences (terracotta has no stairs), and
// a carved and oiled wooden stag on gray rock reads from across a square.
export const PALETTE = {
  rock: [['cobblestone', 5], ['mossy_cobblestone', 3], ['stone', 2], ['andesite', 2]],
  rockSlab: 'mossy_cobblestone_slab',
  cut: 'stone_brick', // the curb: stone_brick_stairs
  post: 'mossy_cobblestone_wall',
  soil: 'moss_block',
  body: 'spruce', // planks, stairs, slabs
  dark: 'dark_oak', // mane, legs (fences), muzzle
  pale: 'birch', // rump planks, antler fences
  eye: 'polished_blackstone_button',
  fern: 'fern', bigFern: 'large_fern',
  flowers: ['cornflower', 'oxeye_daisy', 'lily_of_the_valley', 'azure_bluet'],
}

// The stag's side profile (z 3 and 4 alike), top row y 5 down to y 3, x 0..8. Legend in recipe.mjs:
//   N nape (dark stair rising to the head)  H head  S muzzle (dark stair, the nose tip)
//   R rump (body stair falling to the tail)  B back (body slab)  W withers  M mane (dark)  T throat (dark, upside down)
//   P the white rump  C body
export const PROFILE = {
  x0: 0, y0: 3, zs: [3, 4],
  rows: [
    '......NHS', // y 5
    '.RBBBWMT.', // y 4
    '.PCCCCC..', // y 3
  ],
}

// Legs: [x, z] feet, from y 1 to y 2 (on the crag at y 0). Near side z 3, far side z 4, staggered (walking).
export const LEGS = { feet: [[2, 3], [6, 3], [1, 4], [5, 4]], y0: 1, y1: 2 }

// Antlers: the left one's cells from its burr (7, 5, 2), beside the head; the right one mirrors it (burr z 5, gap 2).
// Each climbs: the burr at eye level with a brow tine pointing forward past the muzzle, the beam straight up a layer,
// out and back along the top of the head, two tines up at the front and the back.
export const ANTLERS = {
  burr: [7, 5, 2], gap: 2,
  beam: [
    [0, 0, 0], [1, 0, 0], // the burr and the brow tine forward
    [0, 1, 0], // the beam rising
    [0, 1, -1], [-1, 1, -1], [-2, 1, -1], // out, and sweeping back
    [0, 2, -1], [-2, 2, -1], // two tines up
  ],
}

// No eyes: the burrs sit where the eyes would be; at this scale the brow tine reads as the face's edge.
export const EYES = []

// The crag: an oval (center x 4, z 3, half-axes 4.6 and 3.4). Rim cells (outer ring) are moss unless curb or spine.
export const CRAG = { cx: 4, cz: 3, rx: 4.6, rz: 3.4, rim: 0.5 }
export const CURB = { x0: 2, x1: 6, z: 0 }
export const SPINE = { z0: 2, z1: 4 } // always rock under and beside the stag's feet
export const BOULDERS = [[8, 1, 3], [0, 1, 4]]
export const BOULDER_SLABS = [[8, 1, 4]] // a half boulder beside the muzzle's rock, so the two read as stones, not a cube

// Plants on the moss rim: [x, z, plant]; flowers only at the front, ferns at the sides and back.
export const PLANTS = [
  [2, 1, 'cornflower'], [3, 1, 'oxeye_daisy'], [6, 1, 'azure_bluet'],
  [0, 2, 'fern'], [8, 2, 'fern'],
  [1, 5, 'large_fern'], [3, 6, 'fern'], [5, 6, 'lily_of_the_valley'], [7, 5, 'large_fern'],
]

export const LAMPS = [[1, 1, 1], [7, 1, 1]].map(([x, y, z]) => ({ x, y, z })) // a post at y 1, the lantern on it

// Walking line: from the lane (4, 0, -1) up onto the curb (4, 0, 0) and along it (x 2..6) to look at him. No room,
// no furniture: the statue has no furniture spots.
export const WALK = [[4, 0, -1], [4, 0, 0], [2, 0, 0], [6, 0, 0]]
