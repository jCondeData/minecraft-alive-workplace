// The Founder's Statue (`legend/founder_statue`, one level), the plan: drawn before any block. ROADMAP 49 (rebuild);
// the brief is tools/blueprints/briefs/founder_statue.md. recipe.mjs assembles it from the parts library (../parts,
// statues.mjs: profileFigure, brimHat).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Lantern-Bearer. A standing bronze (waxed copper) Founder in a traveler's broad-brimmed hat and a cloak, a
//      tall iron staff in his right hand with a lantern burning on top of it above his hat, his left hand resting on a
//      rough mossy stone; on a two-step stone plinth with a bronze plate in the front step. No roof; the plan a 7 x 7
//      stepped square with a lamp post on each front corner and a flowering azalea on each back corner. Story: the night
//      the first settlers came over the hill, the Founder stood on the empty field with his lantern up on its staff so
//      they could find the place; the stone under his hand is the first stone he set, and the village built the
//      pedestal round it.
//   B. The Surveyor. The Founder striding forward with a measuring rod held level across his body, pointing down the
//      street he laid out, on a long low plinth. Story: he walked out the first street. But a level rod across a
//      figure is exactly the grave cross the last critic saw (briefs/triage_52_5): a horizontal bar at shoulder height
//      on an upright reads as a cross, not a man.
//   C. The Founder's Bench. The Founder seated on a stone bench under a little stone canopy, an empty seat beside him
//      for whoever comes next. Story: he still sits with his village. Warm, but a seated figure at block scale loses
//      its legs into the bench and reads as a lump on a seat, and the canopy's roof would be the top layer, not his head.
// Strongest: A. What makes a block figure read as a person, not a post or a cross, is the outline: two legs with
// daylight between them, a head on narrower, sloping shoulders, a broad hat brim, arms hanging at the sides, and
// clothes in different colors; nothing horizontal at shoulder height. The staff gives the statue its tallest point and
// its purpose (the light over the square) in a line one pixel-thin, so it never competes with the head, and the hand on
// the founding stone ties the figure to its base. It is also the riskiest: a person in 7 x 10 blocks, where a
// one-block arm is as thick as the body.
// First drawing, rejected from its renders: the right arm raised straight up with the lantern on the fist. A full-block
// arm taller than the head read as a second neck: the figure became an animal (a llama) with its head held high. The
// staff keeps the light high and lets both arms hang like a man's.
//
// Coordinates: x across the front (west 0 -> east 6), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, toward the Village Hall) to the back (z = 6). The Founder faces north. No door: the builder's path
// starts at (3, 0, -1), the ground in front of the front step's middle.
// The head check (Founder.stands): the middle column is (3, z 3); the top three layers are y 7, 8, 9. The head is at
// (3, 7, 3) and the hat's crown at (3, 8, 3); (3, 9, 3) is open sky beside the lantern. Knocking those three out
// leaves no head: the beauty and mood stop.
//
// Masses (named parts the owner's feedback can point at):
//   Plinth     two steps of dressed stone: y 0 the 7 x 7 square (an edge of stairs stepping up from every side), y 1
//              the 5 x 5 (an edge of stairs again, a 3 x 3 die in the middle with chiseled corners). In the upper
//              step's front middle, a bronze plate: the waxed cut copper the colony tests look for.
//   Founding stone  a rough mossy cobblestone pillar (x 1, z 3, y 1..3) rising through the upper step, older than the
//              dressed stone round it. His left hand rests on top of it (y 4).
//   Figure     waxed copper, facing north on the die: boots (y 2) with toes stepping forward (stairs at z 2), legs
//              (y 3) at x 2 and x 4 with daylight between them, the coat's skirt (y 4), the body (y 5..6) with a
//              chiseled-copper clasp on the chest, both shoulders sloping (stairs) down to the sleeves (y 5); the
//              head (y 7) above, under a broad-brimmed hat (crown y 8, a full ring of trapdoor brim).
//   Cloak      down his back (z 4): a collar behind his neck with sloping shoulders (y 6), the back (y 5), its hem flaring out (stairs, y 4) above the backs of his knees, so the gap
//              between his legs stays open from the front and the sides read two deep.
//   Staff      an iron rod (chain) a block in front of his right sleeve (x 5, z 2, y 2..8), gripped by his right hand
//              at elbow height (y 5, the forearm forward), the lantern standing on its top (y 9): the top of the
//              statue and its light.
//   Corners    a lamp post on each front corner (stone brick wall, lantern), a flowering azalea on moss on each back
//              corner: the square reads as a small garden, lit from the front.
//
// Palette (60/30/10): dressed stone (stone bricks, chiseled corners, the stepped edges) / waxed copper in four ages,
// the figure / the mossy founding stone, the azaleas, the iron staff and the lanterns.
// Why: a bronze figure on a gray plinth is how every town square's founder looks, and copper is the one bronze a player
// can make; waxed, it never turns. Real bronze statues green from the top down where rain runs and stay bright where
// hands touch them: the hat and boots are oxidized (teal), the cloak weathered (green), the coat exposed (brown), and
// the face, the hands, the toes and the plate, rubbed for luck by every villager who passes, bright new copper. The
// copper's ages are the statue's clothes, so it reads as a man in a hat, coat and cloak, not a copper post.
export const SIZE = [7, 10, 7] // w, h, d: both sides odd (the middle column turns with the statue), height in 4..10

export const PALETTE = {
  paving: [['stone_bricks', 5], ['cracked_stone_bricks', 1]], // the lower step's hidden middle
  die: 'stone_bricks',
  dieCorner: 'chiseled_stone_bricks',
  steps: 'stone_brick_stairs',
  rough: 'mossy_cobblestone',
  post: 'stone_brick_wall',
  plate: 'waxed_cut_copper', // the colony tests' marker: only the statue has it
  skin: 'waxed_copper_block', // face and hands: smooth, rubbed bright
  bright: 'waxed_cut_copper_stairs', // the toes, rubbed bright
  coat: 'waxed_exposed_cut_copper', // the coat, sleeves and legs
  coatStairs: 'waxed_exposed_cut_copper_stairs',
  clasp: 'waxed_chiseled_copper',
  cloak: 'waxed_weathered_cut_copper',
  cloakStairs: 'waxed_weathered_cut_copper_stairs',
  patina: 'waxed_oxidized_cut_copper', // hat crown, boots
  brim: 'waxed_oxidized_copper_trapdoor',
  staff: 'chain',
  soil: 'moss_block',
  bush: 'flowering_azalea',
}

// The figure, front elevation (looking south at his face), one row per layer from the top (y 9) down to y 2,
// x 0..6. The body is one block deep at z 3; what lies in front (toes) and behind (cloak) is in DEPTH below.
// Legend (recipe.mjs): F hand (skin), h hat crown, b brim (trapdoor), H head (skin), s and t the sloping shoulders
// (coat stairs), C coat, X chest clasp, a sleeve, K coat skirt, k the skirt's split (an upside-down stair, its
// lower half at the back, so the daylight between the legs runs half a block higher), l leg, B boot.
export const FRONT = {
  rows: [
    '.......', // y 9  (the staff's lantern is a block in front: STAFF)
    '..bhb..', // y 8
    '...H...', // y 7
    '..sCt..', // y 6
    '.aCXCa.', // y 5
    '.FKkK..', // y 4  (his left hand, F, on the founding stone; k the coat's split, over the legs' gap)
    '..l.l..', // y 3
    '..B.B..', // y 2
  ],
  z: 3,
  y0: 2,
}

// Depth: the cells in front of and behind the body plane.
export const DEPTH = {
  toes: [[2, 2, 2], [4, 2, 2]], // stairs, high side against the boot, rubbed bright
  collar: [3, 6, 4], // the cloak's collar behind his neck, its shoulders sloping out (stairs) like his own
  cloak: { x0: 2, x1: 4, z: 4, y0: 5, y1: 5 }, // full blocks down the back
  hem: { x0: 2, x1: 4, z: 4, y: 4 }, // stairs flaring out at the back of the knees
}

// The staff: an iron rod (chain) a block in front of his right sleeve, gripped by his right hand at elbow height
// (the forearm comes forward), from the upper step to above his hat, the lantern standing on its top.
export const STAFF = { x: 5, z: 2, y0: 2, y1: 8, hand: 5, lantern: 9 }

export const PLINTH = {
  lower: { x0: 0, z0: 0, x1: 6, z1: 6, y: 0 },
  upper: { x0: 1, z0: 1, x1: 5, z1: 5, y: 1 },
  plate: [3, 1, 1], // the bronze plate in the upper step's front
}

export const FOUNDING_STONE = { x: 1, z: 3, y0: 1, y1: 3 }

export const CORNERS = {
  lamps: [[0, 0, 0], [6, 0, 0]], // stone posts y 0..1, lantern on top
  bushes: [[0, 0, 6], [6, 0, 6]], // moss at y 0, azalea at y 1
}

// No rooms, no furniture: nobody lives or works here.
export const FURNITURE = []

// The walking line: from the ground in front (3, 0, -1) up the lower step (3, 0, 0) onto the bronze plate at his
// feet (3, 1, 1), where a villager stands to read it and look up at the lantern; the treads of both steps run round
// the statue from there.
export const WALK = [[3, 0, -1], [3, 0, 0], [3, 1, 1]]
