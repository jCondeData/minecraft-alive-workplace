// The Sundial (`sundial`, one level), the plan: drawn before any block. ROADMAP 49 (rebuild); the brief is
// tools/blueprints/briefs/sundial.md. recipe.mjs assembles it from the parts library (../parts, sundials.mjs).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Sun Cradle. A stone half-cylinder lying north-south on a raised sandstone dais, its inside a white dial
//      face, a chain rod stretched along its axis between two slim pylons with lanterns on top; no roof, the plan a
//      square dais with flower beds in its four corners. Story: Minecraft's sun goes straight overhead from east to
//      west at an even pace, so the village's masons laid the rod north-south in a cradle: its shadow sweeps the
//      curve an even step an hour, and every hour line is a straight line along the rod. The one dial that tells true
//      time under this sky, and you can sit in it.
//   B. The Garden Dial. A round dial plate on a baluster pedestal, a triangular stair gnomon, a ring of twelve hour
//      stones on an octagon of steps. Story: the sundial from every garden in the real world. It reads at once, but
//      it is the generic one, and under Minecraft's overhead sun its slanted gnomon would tell the wrong hour all day.
//   C. The Hour Walk. A flat paved ellipse with hour stones round it and a date path down the middle where a
//      villager stands to be the gnomon (an analemmatic dial). Story: the people are the clock. Charming, but flat:
//      from across a square it is paving, and nothing marks it as a place.
// Strongest: A. It is the only one that is right for this sky (the brief leaves the dial free, so it should work),
// its front view is a sight nobody mistakes for a fountain or a well (a white crescent with dark hour lines, a sun
//  lamp over each end of its rod), and it turns the dial itself into the thing people use: the cradle's floor is
// stepped, so a villager walks in and stands on the noon line. It fits 9 x 8 x 9 exactly.
//
// Coordinates: x across the front (west 0 -> east 8), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, the road) to the back. No door: the lane starts at (4, 0, -1), one block in front of the middle of the
// front edge, onto the front step (4, 0, 0).
//
// Masses (named parts the owner's feedback can point at):
//   Dais      a raised sandstone floor over the whole plot (y 0), a step of stairs along the middle of the front and
//             back edges (x 2..6), the cradle resting on it.
//   Cradle    the dial: a half-cylinder of radius 4.5 round the rod's axis (x 4.5, y 6.5), swept along z 2..6. Its
//             inside is the Face (white polished diorite; the end slices z 2 and z 6 in sandstone, a frame round the
//             white like a cup's lip, and a
//             sandstone rib up the middle of each tall rim, so its outside reads as two framed marble panels, not a slab), its rounded underside the Hull (upside-down sandstone stairs, air under them,
//             so it sits on the dais like a boat on a slip, not a box).
//   Lines     the hour lines, each straight along z (a cylinder dial's lines are): noon the bottom (yellow glazed
//             terracotta, the sun's own pattern), 9 and 3 o'clock dark slabs on the flanks, 6 and 6 the rims' thin
//             dark lips (slabs, their tops level with the rod's axis). 15 degrees an hour round the axis, so 9/3 fall 45 degrees out and 6/6 at 90.
//   Rod       a chain (iron, dark, thin) along the axis at y 6, z 2..6: the gnomon.
//   Pylons    the rod's two holders at x 4, z 1 (front) and z 7 (back): a chiseled foot, a slim sandstone-wall
//             shaft, a cut-sandstone head the chain is fixed to, a lantern on top (the highest point, y 7).
//   Sun beds  four 2 x 2 flower beds in the dais's corners: dandelions and oxeye daisies, a flowering azalea on the
//             outer corner.
//
// The 13 hour cells, worked out (shadow of the axis at 15 degrees a step): noon (4,1); 11/1 (3,1),(5,1); 10/2 the
// slabs (2,2),(6,2); 9/3 the slabs (1,3),(7,3); 8/4, 7/5 the rim's inside (0|8, 4 and 5); 6/6 the rims' lip slabs (0|8, 6).
// A 9-wide cradle has exactly 13 face cells, one per hour: marking all of them would leave no white for the shadow,
// so only the quarters are marked (6, 9, noon, 3, 6), as on a clock face.

export const SIZE = [9, 8, 9] // w, h, d (brief: at most 9 x 8 x 9). Ship step: StarterBlueprints.SUNDIAL (7, 3, 7) becomes
// new Vec3i(9, 8, 9), `sundial` joins generate.py's REBUILT, docs/wiki/more-decorations.md's size row changes.

// Palette (60/30/10): warm sandstone (dais, hull, pylons, the end frames) / white polished diorite (the dial face) /
// accents: the dark hour lines (polished blackstone), the yellow noon line, the iron rod, the flower beds and two
// lanterns. Why: a sunny, southern stone for a sun piece, so it stands apart from the village's gray fountain and
// well; white where the shadow must read, dark only where an hour is marked. Every block is one a survival player
// can get (no copper that would weather into another block).
export const PALETTE = {
  dais: [['cut_sandstone', 4], ['smooth_sandstone', 3], ['sandstone', 1]],
  step: 'smooth_sandstone_stairs',
  hull: { full: 'smooth_sandstone', slab: 'smooth_sandstone_slab', stairs: 'smooth_sandstone_stairs' },
  frame: { full: 'cut_sandstone', slab: 'cut_sandstone_slab', stairs: 'sandstone_stairs' }, // the end slices' face
  face: { full: 'polished_diorite', slab: 'polished_diorite_slab', stairs: 'polished_diorite_stairs' },
  mark: { full: 'polished_blackstone', slab: 'polished_blackstone_slab', stairs: 'polished_blackstone_stairs' },
  noon: { full: 'yellow_glazed_terracotta', props: { facing: 'south' } },
  rod: 'chain',
  pylon: { base: 'chiseled_sandstone', shaft: 'sandstone_wall', head: 'cut_sandstone', lamp: 'lantern' },
  soil: 'grass_block',
  flowers: ['dandelion', 'oxeye_daisy'],
  bush: 'flowering_azalea',
}

export const MASSES = {
  dais: { x0: 0, x1: 8, z0: 0, z1: 8, y: 0, steps: { x0: 2, x1: 6, front: 0, back: 8 } },
  // the cradle's half-profile, from the middle column out: [y, role, shape] (parts/sundials.mjs, cradleProfile)
  cradle: {
    cx: 4, z0: 2, z1: 6, frames: [2, 6], ribs: [4], ribFrom: 4, axisY: 6,
    half: [
      [[1, 'noon', 'full']], // dx 0: noon, on the dais
      [[1, 'face', 'full']], // dx 1: 11 / 1
      [[1, 'hull', 'under'], [2, 'face', 'slab']], // dx 2: 10 / 2
      [[2, 'hull', 'under'], [3, 'mark', 'slab']], // dx 3: 9 / 3
      [[3, 'hull', 'under'], [4, 'face', 'full'], [5, 'face', 'full'], [6, 'mark', 'slab']], // dx 4: 8/4, 7/5, 6/6 (the rim's lip)
    ],
  },
  rod: { x: 4, y: 6, z0: 2, z1: 6 },
  pylons: [{ x: 4, z: 1 }, { x: 4, z: 7 }], // y 1 (foot) .. 6 (head), lantern at 7
  sunBeds: [[0, 0], [7, 0], [0, 7], [7, 7]], // each bed's [x0, z0], 2 x 2
}

// Rooms: none (an open dial). The cradle's inside is the place people stand.
export const ROOMS = [{ name: 'Cradle', use: 'standing on the hour lines, sitting on the stepped sides', box: [1, 2, 2, 7, 4, 6] }]

// Openings: none (no walls). The cradle is open at both ends, so it can be walked into from the front and the back.
export const OPENINGS = { approach: { lane: [4, 0, -1], step: [4, 0, 0], standAt: [3, 1, 1] } }

// Furniture: none (the brief: no job block, no bed, no container, no furniture kind; the cradle's steps are the seats).
export const FURNITURE = []

// The walking line: the lane's end -> the front step -> past the front pylon onto the dais -> up a step into the cradle
// on the 11 o'clock line -> along the noon line -> out the back past the back pylon -> the back step.
export const WALK = [[4, 0, -1], [4, 1, 0], [3, 1, 1], [3, 2, 2], [4, 2, 3], [4, 2, 4], [4, 2, 5], [5, 2, 6], [5, 1, 7], [4, 1, 8]]
