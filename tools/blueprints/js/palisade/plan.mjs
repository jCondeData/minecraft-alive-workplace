// The Palisade (`palisade`, Level II `palisade_2`), the plan: drawn before any block. ROADMAP 52.3 (the method of 49);
// the brief is tools/blueprints/briefs/palisade.md. recipe.mjs assembles both levels from the parts library (../parts).
// One kit with palisade_gate and palisade_tower (their plans say the same about the kit below).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Rampart. An earth bank faced in mud brick on the village side, a split-log walk along its top, and a row of
//      bark-on spruce stakes dug in on the outside, shod with points. No roof. Story: the
//      hamlet throws the ditch's earth up into a bank and plants the stakes in it: a hill-fort's rampart, built by
//      farmers in a week. Level II cuts the fighting step into the bank and plants dripstone stakes in the ditch.
//   B. The Covered Walk. A tall log wall with a pent roof over the archers' walk (a Wehrgang, as on the old town
//      walls of Rothenburg). Story: a richer village keeping its archers dry. Dies on the kit's rule: a segment must
//      agree with itself at every overlap, so every column is the same: no posts at intervals to carry a roof, and a
//      parapet tall enough to carry one leaves the archers no line out.
//   C. The Wattle Hedge. A low woven fence on a turf bank with a dead-hedge of branches in front. Story: a hamlet's
//      first, cheapest fence. Too low to stand archers on, and from the road it reads as a garden fence.
// Strongest: A. It is the only one where the section itself is the design, which is exactly what an overlapping
// segment allows: every column is the same, so the drawing's richness has to be in how it steps from the village to
// the field (stone toe, mud-brick revetment, pale walk, dark stakes, earthen berm), not in posts along it. It reads as a
// real earthwork from both sides, the stakes stay a clean dark comb from outside (the line raiders see), and Level II
// grows out of it the way earthworks are improved: a wider walk on the bank, a ditch staked in front.
//
// Coordinates: x along the wall (0..6), y up (0 = the layer on the clicked block), z from the inside (z = 0, the
// village) to the outside. Every column x is identical (the overlap rule; `extrude`).
//
// The kit (agreed by the three plans): the walk is a sturdy block at y 2, stood on at y 3, on the segment (z 2),
// over the gate's gateway (z 0..1) and Level II's widened walk (z 0..2); the tower's lookout deck is higher, at y 4
// (stood on at y 5), up its own ladder. The stake line is the segment's z 3; the gate is recessed behind it (its back
// row z 1), flanked by the segments' cut ends; the tower's walls take the same stakes on the same stone foot.
//
// Round 2 (the critic's notes, briefs/palisades_52_3_critic.md): the inside is now a real flight a player and a guard
// walk up (toe, revetment step, walk: three half-steps, each its own row); the stakes carry points, the
// palisade's comb outline, a block over the waist-high parapet; the oak wale is a pale rail through the stakes' outer
// face (the depth cap, 4, leaves no row outside the stakes for one pegged on); Level II's widened walk keeps the
// flight at x 3 as its stair, and its ditch has stakes standing in it.
//
// Round 3 (the critic's round 2): the points are spruce fence posts, not copper rods (a rod draws lightning onto the
// walk, and a struck villager turns witch); the wale is an oak stair hung upside down in the stake line, its lip proud
// over a half-block recess (the outer face's shadow line); Level II's ditch bed is coarse dirt, the ground's own
// colour, so the stakes read as standing in turned earth, not on a curb. Level II's columns differ at x 3 on purpose:
// the upgrade is clicked by a player onto one finished segment (the Steward never builds it), and only two upgrades
// put on overlapping segments would meet; there the later site builds its bank over the other's stair, nothing lost.
//
// B1382 (2026-11-18): the fence points are gone. A fence's collider is a block and a half tall, so the points stood a
// rail from 4 to 5.5 right in front of an archer's eyes (4.62 on the walk): no station on the walk saw a single raider
// and every arrow hit the rail. Any comb on the stake line would do the same (a segment's columns must agree, so it
// can't skip the station's own column, and dripstone tips cost cave stone by the hundred round a village). The stake
// line now ends at y 3, one block over the walk: an archer shoots over it at anything from about six blocks out.
//
// Masses (named parts):
//   Toe       cobblestone stairs along the inside foot (z 0, y 0), rising toward the wall: the first step.
//   Revetment a mud-brick stair on a cobble foot (z 1, y 0..1): the second step, the bank's faced front.
//   Bank      a cobble foot and packed mud (z 2, y 0..1): the rammed-earth core under the walk.
//   Walk      stripped oak logs laid along the wall (z 2, y 2): seven battle stations, the pale line along the top.
//   Stakes    bark-on spruce logs upright, z 3, y 0..3: the palisade, waist-high (one block) over the walk.
//   Wale      an oak stair hung upside down in the stake line (z 3, y 2), its foot on the inner side: a pale rail lip
//             proud over a half-block recess, the outer face's shadow line (a stake over it: no station).
//   Level II (Palisade II) adds, keeping 84 % of Level I (the flight at x 3 untouched):
//   Fighting step  the bank built out over the toe and the revetment (z 0..1) on every column but x 3: a cobble foot,
//                  packed mud, and spruce top slabs at y 2: a walk three wide, twelve more stations. x 3 keeps Level I's
//                  toe and revetment: the stair up through the widened bank.
//   Stake ditch    turned earth outside the stakes (coarse dirt, z 4, y 0), a pointed dripstone stake standing in it
//                  on every column, points up.
//
// Heights: a raider's ladder or ramp meets the stakes' top at 4 (the brief: at most 8). Depth:
// Level I 4 (z 0..3; the witches' spawn cells at z 4 stay free), Level II 5 (stakes at z 4, never past the tower's 5).

export const SIZE = { 1: [7, 4, 4], 2: [7, 4, 5] } // w, h, d

// Palette (60/30/10): wood (dark bark spruce stakes; pale stripped oak walk and wale, the kit's frame) / earth (mud
// bricks, packed mud, a cobble foot and toe) / accents: Level II's dripstone teeth. Why: dark
// stakes over a tan earthen bank read as dug and raised by hand, not bought; the stone kit that replaces it later is
// grey, so a Town's upgrade shows. Single blocks only, never a mix: a mix varies by x, and copies must agree where they
// overlap.
export const PALETTE = {
  toe: 'cobblestone_stairs',
  revetment: 'mud_brick_stairs',
  foot: 'cobblestone',
  bank: 'packed_mud',
  walk: 'stripped_oak_log',
  wale: 'oak_stairs',
  stake: 'spruce_log',
  fightingStep: 'spruce_slab',
  ditch: 'coarse_dirt',
  spike: 'pointed_dripstone',
}

export const MASSES = {
  run: { x0: 0, x1: 6 },
  toe: { z: 0, y: 0, facing: 'south' }, // high side toward the wall
  revetment: { z: 1, footY: 0, y: 1, facing: 'south' },
  bank: { z: 2, footY: 0, y: 1 },
  walk: { z: 2, y: 2 },
  stakes: { z: 3, y0: 0, y1: 3 },
  wale: { z: 3, y: 2, facing: 'north' }, // half top, its full side inward
  fightingStep: { zs: [0, 1], y: 2, xs: [0, 1, 2, 4, 5, 6] }, // Level II; x 3 keeps the flight
  ditch: { z: 4, y: 0, xs: [0, 1, 2, 3, 4, 5, 6] }, // Level II; spikes at y 1
}

// Rooms: none (a wall). Openings: none (no way through: that is the gate's). The way up is the section's flight:
// from the village ground (z -1) up the toe (y 0), the revetment stair (y 1) onto the walk (y 2), half a block at a
// time; on Level II the same flight at x 3, cut through the widened bank.
export const ROOMS = []
export const FURNITURE = { 1: [], 2: [] }
// No lights: dozens of segments round a village would be a lantern string; the gate and the towers carry the light.
export const LIGHTS = { 1: [], 2: [] }

// The walking line (feet): village ground (x, 0, -1) -> the toe (x, 1, 0) -> the revetment (x, 2, 1) -> the walk
// (x, 3, 2), along x. Guards walk up the flight or are lifted from the ground (BattleStations.CLIMB_REACH).
export const WALK = [[3, 0, -1], [3, 1, 0], [3, 2, 1], [3, 3, 2], [0, 3, 2], [6, 3, 2]]
