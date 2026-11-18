// The Mega Shrine (`mega_shrine`, one level), the plan: drawn before any block. ROADMAP 52 (rebuild); the brief is
// tools/blueprints/briefs/mega_shrine.md, the three concepts and the choice are in DESIGN.md beside this file.
// recipe.mjs assembles it from the parts library (../parts; shrines.mjs: roundArch, twinFlight).
//
// Three rough concepts (massing, roof, plan; one line of story each; DESIGN.md has them in full):
//   A. The Keystone Arch. A stepped deepslate dais, a round-headed arch the plot's whole width and height across its
//      middle with a chiseled meteorite keystone, the pedestal framed under it, twin flights up the front. Story: the
//      Key Stone wakes Mega Evolution, so the village made the meteorite's stone the keystone of the arch the show is
//      held under.
//   B. The Fallen Star. A meteorite half sunk in a crater, split open like a geode, the pedestal in the split. Story:
//      the shrine is where the star fell. Reads as a pile from 20 blocks at 7 x 7.
//   C. The Canopy Altar. A square stepped dais, four columns and a flat canopy at y 5. Story: a covered altar. Safe;
//      the canopy squashes the show and the columns cut the crowd's view.
// Strongest: A (a shape known in outline, it frames exactly the trainer's stand and the pedestal, no roof over the
// Mega Evolution's light, every side worked).
//
// Coordinates: x across the front (west 0 -> east 6), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, where the crowd stands) to the back.
//
// Masses (named parts):
//   Altar Stair   x 2-4, z 0-2: a straight, 3-wide flight from the ground to the trainer's stand, one stair a step
//                 (z 0 at y 0, z 1 at y 1, z 2 at y 2); its top step (3, 2, 2) is the stand's footing, so the trainer
//                 walks straight up the crowd's line of sight and stops facing the pedestal (critic, round 1, note 1).
//   Cheeks        x 1 and x 5, z 1: the flight's cheek blocks, two high, each with a wall post and a soul lantern on top,
//                 a plinth step (a stair) proud of them at z 0 and a low stair at x 0|6, z 1: the front steps down to
//                 the corners in shadow lines instead of a flat wall (note 3).
//   Forecourt     z 0, y 0: the flight's foot, the cheeks' plinth steps, tile slabs at the corners.
//   Back Stair    z 5: twin flights from x 0 and x 6 (y 0) climbing to a landing (3, 2, 5) behind the pedestal; the
//                 Back Court (z 6) has tile slabs and two bollards (a wall post, a soul lantern) at the flights' feet.
//   Plinth        x 1-5, z 1-5, y 0-1: deepslate bricks, cracked ones and tiles mixed into the bottom course.
//   Terrace       x 2-4, z 3-4, y 2 (+ the back landing): meteorite brick round the pedestal.
//   Keystone Arch z 3 and z 4: two rings, so the arch is two blocks deep (note 2): piers x 0 and x 6 (y 0-3), the
//                 stair-rounded head (y 4-5), the meteorite keystone (3, 5, 3) in front, a chiseled deepslate one behind.
//   Pedestal      (3, 3, 3) on the chiseled meteorite block (3, 2, 3).
//   Crystals      amethyst clusters beside the pedestal (2|4, 3, 3) and on four amethyst geode beds on the plinth's
//                 shoulders (1|5, 1, 2|4).
//   Buttresses    x 0|6, z 2: a block and a stair leaning on the front of each pier.

export const SIZE = [7, 6, 7] // exactly (StarterBlueprints.MEGA_SHRINE, QaMegaShrine1104d4CompatTests)

// Palette (60/30/10): deepslate (bricks, cracked bricks low, tiles; their stairs and walls) / Mega Showdown's meteorite
// (bricks for the terrace and the ring's floor, the chiseled block under the pedestal and as the keystone) / amethyst
// and soul lanterns. Why: a dark, night-sky stone so the meteorite and the crystals read as the bright things, and the
// soul lanterns' blue light matches the Mega Evolution's glow.
export const PALETTE = {
  plinth: [['deepslate_bricks', 6], ['cracked_deepslate_bricks', 2], ['deepslate_tiles', 1]],
  plinthTop: 'deepslate_bricks', // the plinth's upper course and the stepped gables (the mix is the bottom course only)
  arch: [['deepslate_bricks', 5], ['deepslate_tiles', 1]],
  stairs: 'deepslate_brick_stairs',
  wall: 'deepslate_brick_wall',
  apron: 'deepslate_tile_slab',
  panel: 'chiseled_deepslate',
  terrace: 'mega_showdown:mega_meteorid_brick',
  chiseled: 'mega_showdown:chiseled_mega_meteorid_block',
  pedestal: 'mega_showdown:pedestal',
  geode: 'amethyst_block',
  cluster: 'amethyst_cluster',
  lamp: 'soul_lantern',
}

export const PEDESTAL = [3, 3, 3] // MegaShrines.PEDESTAL_SPOT
export const STAND = [3, 3, 2] // MegaShrines.TRAINER_SPOT (feet); (3, 2, 2) under it is the flight's top stair (solid)

export const MASSES = {
  altar: { x0: 2, x1: 4, z0: 0, n: 3 }, // stairs at (x, i, z0 + i), facing south (climbed going south)
  cheeks: [1, 5], corners: [0, 6], // cheek posts at z 1; low corner stairs at z 1; plinth steps at z 0
  backFlight: { cx: 3, z: 5, y0: 0, n: 3 }, backCourt: { z: 6, bollards: [1, 5] },
  plinth: { x0: 1, x1: 5, z0: 1, z1: 5, top: 1 },
  terrace: { x0: 2, x1: 4, z0: 3, z1: 4, y: 2 },
  arch: { cx: 3, z: 3, y0: 0, spring: 3, r: 3 }, archBack: 4, // the second ring
  buttresses: [[0, 2], [6, 2]],
  geodes: [[1, 2], [5, 2], [1, 4], [5, 4]],
}

// Rooms: none (an open stage). Furniture: none (the brief: no job block, bed, storage or furniture kind).
export const ROOMS = [{ name: 'Terrace', use: 'the trainer stands at (3, 3, 2) and Mega Evolves at the pedestal', box: [2, 3, 1, 4, 4, 4] }]
export const FURNITURE = []

// The walking line (feet cells): the crowd's spot -> straight up the Altar Stair -> the stand, facing the pedestal.
export const WALK = [[3, 0, -4], [3, 0, -1], [3, 1, 0], [3, 2, 1], [3, 3, 2]]
