// The Great Cathedral (a Wonder, version B, ROADMAP 49 / 52.6), the plan: drawn before any wall. Four stages, each
// only adding to the last and each standing and grounded on its own (tools/blueprints/briefs/great_cathedral.md).
// recipe.mjs draws it from the parts library (../parts, with ../parts/minster_b.mjs grown for it) in passes per stage.
//
// Three rough concepts (massing, roof, plan, one line of story each):
//   A. The Fortress Hall: one huge aisleless brick-red hall (an Albi), round stair towers at the corners, a single flat
//      roof slab behind a parapet, a plain box plan with the altar at the end. Story: the village's church that is
//      also its last refuge. Strong in mass, but from 20 blocks it is a box, the brief's transepts and choir have
//      nothing to be, and the twin front spires would sit on it like hats.
//   B. The Domed Basilica: a short Latin cross under one great ribbed dome on a drum over the crossing, the west
//      towers small campaniles. Story: the town shows its wealth. Its hero (the dome) is in the middle, but the brief
//      wants the front towers to be the highest thing (51-53), so the dome would have to crouch under them: a design
//      fighting its own rules.
//   C. The Miners' Minster: a French High Gothic cross. Twin west towers carry octagonal needle spires of deepslate
//      tiles to y 52; a tall nave rides on low lean-to aisles held by flying buttresses on both sides; the transepts
//      cross under a slender green-copper fleche; the choir ends in a round apse with chapels clustered round it like
//      a crown. Inside, pale stone vaults over a polished andesite and deepslate floor, lancets of coloured glass, a
//      rose over the west tribune. Story: the village's miners built it from what they brought up: the dark deepslate
//      roofs and spires are the deep, the pale stone and glass the daylight they climbed back to.
//   Strongest: C. It is the only one whose outline steps up in layers from every side (chapels 15, aisles 17, nave 39,
//   fleche 50, spires 52), so it reads as a cathedral in silhouette alone; the flyers give every long side light and
//   shadow; and its four stages are four different, legible things that match the four titles word for word: the
//   floor and west front, the nave with its flyers, the transepts and choir with the altar, then the belfries, spires
//   and the rose. Built: C.
//
// Coordinates: x across the front (west 0 -> east 38, the middle x 19), y up (0 = the floor layer on the clicked block,
// people stand at y 1), z from the front (z = 0, north) to the apse (z 55). Plot 39 x 56, top 52.
//
// Masses (named parts the critic and the owner can point at):
//   Towers     two 11 x 11 west towers (x 2-12 and 26-36, z 1-11) with angle buttresses: stage I to y 22, stage IV
//              y 23-37 (the belfry y 31-35 behind louvres, two bells each), a parapet and corner pinnacles, and an
//              octagonal needle spire y 38-51 with a copper finial at y 52. A ladder climbs each from the ground.
//   West front the screen between the towers (x 13-25, z 3-5): a deep portal of three stepped orders with a pair of
//              doors and a trumeau, a gallery of kings in niches over it (stage I); the gable with the rose window
//              (stage IV) over the tribune.
//   Nave       x 13-25 (11 inside) z 4-28, walls to y 25 under a pointed barrel vault (crown y 25), a steep 2:1
//              deepslate roof (ridge y 39); lean-to aisles x 7-12 / 26-31 (ceilings y 11); arcades of five piers; flying
//              buttresses at z 16, 20 and 24 on both sides.
//   Transepts  x 0-38, z 29-39, walls to y 24, roof ridge y 35 along x running into the nave roof; lancet triplets in
//              the end gables; groin vault over the crossing.
//   Choir      x 13-25, z 40-49 and a round apse (centre (19, 49), radius 6) to z 55; its floor raised a step (y 1);
//              aisles x 7-12 (the Lady chapel) and 26-31 (the sacristy); two transept chapels (x 0-7 and 31-38,
//              z 40-45) under small gabled roofs; a copper fleche over the crossing to y 50.
//
// Rooms and furniture (the walking line: the portal at (18|20, 1, 5) -> the narthex under the tribune -> up the nave's
// carpeted aisle x 18-20 between the pews -> the crossing (the wedding guests' standing room, z 30-38, kept empty) ->
// the choir step at z 40 -> the altar at (19, 2, 49)):
//   Narthex   x 13-25, z 6-11 under the tribune (y 14), towers' ground rooms off it (doors x 12 / 26, z 8).
//   Nave      pews: dark oak stairs facing north at y 1, x 14-17 and 21-24, z 14, 16, ... 26 (7 rows, 56 seats);
//             three chandeliers on chains from the crown (x 19, z 15, 19, 23); banners on the piers.
//   Crossing  empty floor (guests at (16|17|21|22, 1, 36) and (15|23, 1, 34)); one chandelier at (19, ~20, 34).
//   Choir     stalls (dark oak stairs) at x 15 and 23, z 42-46; the altar: the gold block at (19, 2, 50) with candles,
//             andesite stair ends, a reredos at z 52; the couple's cells (19|20, 2-3, 49) open.
//   Chapels   lecterns (no book) at (3, 1, 43) and (35, 1, 43) and in the Lady chapel at (10, 1, 46), each with a
//             candle altar; the sacristy (door (28, 1, 39)) has the brewing stand (no bottles) and a cauldron.
//   Belfries  floor y 30, bells hung from the ceiling y 36 at (5|9, 35, 6) and (29|33, 35, 6), all above y 31.

export const SIZE = [39, 54, 56]
export const STAGES = 4
export const MID = 19

// Palette (60/30/10): stone bricks, weathered low down with cracked and mossy (walls) / deepslate tiles (roofs,
// spires) and polished andesite (piers, buttresses, tracery, the floor) / coloured glass, waxed copper (the fleche,
// finials), gold and dark oak inside. Why: one pale stone body under one dark roof reads at any distance; the copper
// is the only green on the skyline, the glass the only colour on the walls.
export const PALETTE = {
  wall: [['stone_bricks', 12], ['cracked_stone_bricks', 1]],
  plinth: [['stone_bricks', 4], ['cracked_stone_bricks', 3], ['mossy_stone_bricks', 3]],
  dress: 'polished_andesite',
  dressStairs: 'polished_andesite_stairs',
  dressSlab: 'polished_andesite_slab',
  post: 'andesite_wall',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  vault: { full: 'stone_bricks', stairs: 'stone_brick_stairs', rib: 'polished_andesite' },
  wallStairs: 'stone_brick_stairs',
  floor: ['polished_andesite', 'deepslate_tiles'],
  copper: 'waxed_weathered_cut_copper',
  copperStairs: 'waxed_weathered_cut_copper_stairs',
  copperSlab: 'waxed_weathered_cut_copper_slab',
  finial: 'lightning_rod',
  louvre: 'dark_oak_trapdoor',
  wood: 'dark_oak',
  carpet: 'red_carpet',
  glass: ['light_blue_stained_glass_pane', 'blue_stained_glass_pane', 'light_blue_stained_glass_pane', 'white_stained_glass_pane', 'red_stained_glass_pane', 'yellow_stained_glass_pane'],
  rose: { rim: 'red_stained_glass', spoke: 'yellow_stained_glass', eye: 'white_stained_glass', field: 'light_blue_stained_glass' },
}

export const TOWERS = [{ x0: 2, x1: 12, cx: 7, ladder: [3, 10, 'east'], door: [12, 8] }, { x0: 26, x1: 36, cx: 31, ladder: [35, 10, 'west'], door: [26, 8] }]
export const TOWER = { z0: 1, z1: 11, cz: 6, stage1Top: 22, floor: 30, belfry: [31, 35], ceiling: 36, parapet: 37, spire: [38, 51] }
export const FRONT = { x0: 13, x1: 25, z: 3, thick: [3, 5], top: 14, doors: [18, 20], doorZ: 5, rose: { cx: 19, cy: 20, r: 4 } }
export const NAVE = { x0: 13, x1: 25, in: [14, 24], z0: 4, z1: 28, wallTop: 23, piers: [12, 16, 20, 24, 28], bays: [[13, 15], [17, 19], [21, 23], [25, 27]], tribune: 14 }
export const AISLE = { wall: [7, 31], in: [[8, 12], [26, 30]], ceiling: 9 }
export const FLYERS = [16, 20, 24]
export const TRANSEPT = { z0: 29, z1: 39, wallTop: 22 }
export const CHOIR = { z0: 40, z1: 49, apse: { cx: 19, cz: 49, r: 6 }, piers: [40, 44, 48] }
export const CHAPEL = { x0: 0, x1: 7, z0: 40, z1: 45, eave: 9 }
export const ALTAR = { stand: [19, 2, 49], groom: [20, 2, 49], gold: [19, 2, 50] }
export const GUESTS = [[16, 1, 36], [17, 1, 36], [21, 1, 36], [22, 1, 36], [15, 1, 34], [23, 1, 34]]
export const CAMERA = [19, 5, 29]
export const BELLS = [[5, 35, 6], [9, 35, 6], [29, 35, 6], [33, 35, 6]]
export const LECTERNS = [[3, 1, 43], [35, 1, 43], [10, 1, 46]]
export const SACRISTY = { door: [28, 1, 39], brew: [29, 1, 47], cauldron: [30, 1, 47] }
export const PEWS = { xs: [14, 15, 16, 17, 21, 22, 23, 24], zs: [14, 16, 18, 20, 22, 24, 26] }
