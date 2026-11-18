// The Palisade Gate (`palisade_gate`, one level), the plan: drawn before any block. ROADMAP 52.3 (the method of 49);
// the brief is tools/blueprints/briefs/palisade_gate.md. recipe.mjs assembles it from the parts library (../parts).
// One kit with palisade and palisade_tower (see the kit note below and in their plans).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Lych Gate. Two heavy jambs of logs on stone feet carry a pale lintel over three fence gates, and a steep
//      gable roof with its gables to the village and to the field stands on the jambs, a fighting loft under it over
//      the lintel. Story: the hamlet's one proud piece of carpentry, the roofed gate every road comes in under, like
//      a churchyard's lych gate or a Japanese village's kabukimon. The only gable in the kit: you find the way in from
//      across the fields.
//   B. The Flanked Gate. Two little log towers either side of the gateway, open-topped, with a plank bridge between
//      them. Story: a gate that defends itself. In 7 x 2 the towers are 2 x 2 stumps, a tower kit beside the tower kit,
//      and the bridge over three gates reads flat.
//   C. The Plain Gap. The segment's stakes run on with a gap closed by three gates and a beam over it. Story: the
//      cheapest way through. A raider can't tell it from the wall; a villager can't find it.
// Strongest: A. It turns the kit's one opening into its landmark: the gable rises a storey over the stake line, so the
// wall's silhouette reads segment, segment, GATE, segment from outside, and its gables face exactly the two ways people
// arrive. Its lintel is the segment's walk carried across (the same pale log at the same height), its jambs and stone
// feet are the tower's corner posts in miniature, and the roof is the tower's hat drawn as a gable: one kit.
//
// Coordinates: x across (0..6), y up (0 = the layer on the clicked block), z from the inside (z = 0, the village) to
// the outside (z = 1). Exactly 7 x 2 (the brief); 9 tall.
//
// The kit: the walk is a sturdy block at y 2 (stood on at y 3): the segment's walk (z 1) and this gate's lintel (z
// 0..1) at the same height. The gate's back row (z 1) is a block behind the segment's stake line (z 2): the gateway
// is recessed between the segments' cut ends, the way a real palisade turns in at its gate.
//
// Masses (named parts):
//   Jambs     2 x 2 log piers, x 0..1 and x 5..6 (z 0..1): a cobblestone foot (y 0), then logs to y 4; the inner
//             columns (x 1, x 5) pale stripped oak framing the way, the outer ones (x 0, x 6) bark spruce like
//             the stakes beside them.
//   Gateway   x 2..4: three spruce fence gates, shut, on the back row (2..4, 0, 1), their panels along x; open over
//             them at y 1 (a pillager walks through once one breaks) and open in front at z 0, y 0..1.
//   Lintel    pale oak top slabs over the gateway at y 2, both rows (z 0..1): the gate's fighting floor, four
//             battle stations (the two under the lanterns are not). At ship (52.3) the logs became top slabs: under a
//             full block the way was 2 high, and a carpet in a broken gate's gap left a pillager (1.95) no room
//             (QaSiegeGatewaySaves1107e); now 2.5, which also lets a ravager (2.2) through, as the old gate did.
//   Loft      open between the jambs at y 3..4 over the lintel, front and back: the archers' slot under the roof.
//   Gable     a 45-degree spruce roof from x 0 to x 6 (eaves y 5 on the jambs' heads, ridge y 8), gables to the
//             front and the back, each filled with bark spruce logs laid along x round a pale king post; a dark slab caps the ridge.
//   Lights    two lanterns hung under the front gable (2, 4, 0) and (4, 4, 0), lighting the way in from the village.
//
// Sealed: every column of the back row but the gateway (x 0, 1, 5, 6) is solid from y 0 to y 4; nothing a mob outside
// can climb (no ladder, no step on the back face). The only gate blocks are the three fence gates.

export const SIZE = [7, 9, 2] // w, h, d (brief: exactly 7 wide, 2 deep; height free)

// Palette (60/30/10): spruce (bark-on jambs, the log gables, the plank roof) / pale stripped oak (the frame round
// the way: inner jambs, lintel, king post) and a cobble foot / accents: the lanterns and a dark-oak ridge. Why: the frame round the
// gateway is the lightest thing on the wall, so the way in shows from the village at dusk; the roof is plank-spruce,
// warmer and lighter than the bark below, so it reads apart from the jambs and not as one dark mass.
export const PALETTE = {
  foot: 'cobblestone',
  jamb: 'spruce_log',
  frame: 'stripped_oak_log',
  lintel: 'oak_slab', // a top slab, the stripped oak's color: the way under it 2.5 high (ship, 52.3)
  gate: 'spruce_fence_gate',
  roof: { full: 'spruce_planks', stairs: 'spruce_stairs', slab: 'spruce_slab' },
  boards: 'spruce_log', // laid along x: a dark log gable under the plank roof
  king: 'stripped_oak_log',
  cap: 'dark_oak_slab',
}

export const MASSES = {
  jambs: [{ x0: 0, x1: 1, frameX: 1 }, { x0: 5, x1: 6, frameX: 5 }], // z 0..1
  jambTop: 4,
  gateway: { xs: [2, 3, 4], z: 1, y: 0, facing: 'south' },
  lintel: { x0: 2, x1: 4, y: 2, zs: [0, 1] },
  gable: { x0: 0, x1: 6, z0: 0, z1: 1, eaveY: 5 },
}

export const ROOMS = [{ name: 'Loft', use: 'archers over the gateway', box: [2, 3, 0, 4, 4, 1] }]
export const FURNITURE = []
export const LIGHTS = [[2, 4, 0], [4, 4, 0]] // hung from the front gable's boards

// The walking line: the road from the village (3, 0, -1) -> through the gateway (3, 0, 0) -> the middle gate (3, 0, 1)
// -> the field (3, 0, 2). Archers are lifted onto the lintel from the road beside it.
export const WALK = [[3, 0, -1], [3, 0, 0], [3, 0, 1], [3, 0, 2]]
