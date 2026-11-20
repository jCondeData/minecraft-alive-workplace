// The Great Cathedral (a Wonder, 52.6 version A), the plan: drawn before any wall. recipe.mjs raises it stage by stage.
//
// Three rough concepts first (the brief fixes the plot: 39 across the arms, 55 long, two west towers topping out at
// y 51-53, the body 25-29 wide at z 25, the altar pinned at (19, 2, 49)):
//   1. "The Hall Church": one huge hipped roof over nave and aisles of equal height, a single broad westwork block
//      carrying two stubby towers, a flat east wall. Story: a plain mendicant church, a roof like an upturned hull.
//      Calm, cheap in blocks, but it reads as a barn with towers; the arms vanish under the one roof.
//   2. "The Lantern Crossing": a squat Romanesque cross, round arches, the crossing crowned by a big octagonal lantern
//      that is the hero; west towers as short twin turrets. Story: a pilgrimage church built round a relic under the
//      lantern. Striking, but the brief wants the WEST towers to be the tallest things (51-53) and their spires and bells
//      the last stage's crown, so the lantern would fight them.
//   3. "The Ship of Stone": a true gothic basilica. A tall nave stands over lower aisles, held up by flying buttresses
//      that leap the aisle roofs, a steep slate-dark roof, a twin-towered west front with three portals and a rose, and
//      a stepped chevet at the east end: a half-octagon apse over the altar, the aisles ending in their own little
//      chapels, two more chapels set against the transepts' east walls, a slender lead fleche on the crossing. Story:
//      the village's masons raise it over a generation, each stage a campaign: the front first (a church is begun at
//      the door), then the nave's ship, then the crossing and the holy east end, last the bells and the rose.
// Strongest: 3. Its silhouette is the most varied (three heights of roof: aisle, arm, nave; towers, fleche, chevet),
// every stage adds a whole recognisable mass, and the buttresses give the long sides real depth instead of a flat wall.
// It is also the hardest to keep in budget (14,000 blocks) and in the plot, which the plan below solves.
//
// Coordinates: x across the front (0 west .. 38 east, the middle x = 19), y up (0 = the floor layer on the clicked
// block), z from the front (z = 0, the west front, toward the player) to the east end (z = 55).
//
// Masses (named parts the critic and the owner can point at):
//   West Front   stage I. The central bay x 13..25 at z 2: a gabled triple portal (doors at x 18 and 20 round a stone
//                trumeau, a gablet over them on z 0..1), the rose's stone ring (centre (19, 17), radius 5.6), the
//                gable stepping up the nave roof's line to a pinnacle at y 38.
//   Towers       stage I raises the shafts (x 4..12 and 26..34, z 2..10) to y 26 with clasping buttresses, string
//                courses, three floors joined by a ladder under a hatch, a side portal each (the towers' own way in); stage IV adds the belfries (y 31..37,
//                louvred twin openings, two bells each on a beam), a corbelled cornice, a parapet with corner pinnacles
//                and a slim octagonal deepslate spire, a third of the tower's height, to y 51 with its tip at y 52.
//   Nave         stage II. Clerestory walls x 13 and 25 (z 3..31) to y 20, a pointed arcade (piers every 4 from z 11),
//                lancets over the aisle roofs, a stone vault inside with polished ribs at each pier, the roof (ridge y 35)
//                at a 2:1 pitch; aisles x 8..12 / 26..30 (outer walls x 7 / 31 to y 8) under lean-to roofs; four flying
//                buttresses a side; pews either side of a carpet aisle.
//   Transepts    stage III. Arms x 0..12 and 26..38, z 31..39, walls to y 20, coped end gables with a tall twin lancet
//                and a pointed oculus, the arm roofs (ridge y 31, along x) meeting the main roof in valleys.
//   Choir        stage III. z 40..47 raised a step (floor y 1), choir stalls, an arcade to the choir aisles; the apse a
//                half octagon (z 48..54) round the altar, its roof hipped down from the ridge; the fleche on the crossing.
//   Chapels      stage III. The two choir aisles end in chapels (x 8..12 / 26..30, z 48..51), and the transepts carry a
//                chapel each on their east wall (x 1..6, z 40..43): the sacristy (west, with its brewing stand) and the
//                Lady chapel (east). Three chapels have a lectern and a candle altar.
//   Rose         stage IV. The glass and tracery inside the stone ring: four spokes, a hub, yellow round the hub, white
//                in the middle zone, red and light blue alternating by sector in the outer zone.

export const SIZE = [39, 53, 56]
export const STAGES = 4
export const MID = 19

// Palette (60/30/10): pale stone bricks for every wall, weathered (mossy and cracked) in a band low down / a deepslate
// tile roof and spires, the darkest thing on the build / polished andesite dressings (piers, ribs, string courses,
// floors) and dark oak joinery, with jewel glass. Why: a church of the village's own quarry, light walls under a dark
// roof so the silhouette reads from far off, the chequered floor and the gold altar the only rich things inside.
export const PALETTE = {
  wall: [['stone_bricks', 14], ['cracked_stone_bricks', 1]],
  low: [['stone_bricks', 5], ['mossy_stone_bricks', 3], ['cracked_stone_bricks', 2]],
  wallStairs: 'stone_brick_stairs', wallSlab: 'stone_brick_slab', wallWall: 'stone_brick_wall',
  carve: 'chiseled_stone_bricks',
  dress: 'polished_andesite', dressStairs: 'polished_andesite_stairs', dressSlab: 'polished_andesite_slab',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab', wall: 'deepslate_tile_wall' },
  spireBand: 'deepslate_bricks',
  floorA: 'polished_andesite', floorB: 'deepslate_tiles', floorEdge: 'stone_bricks',
  wood: 'dark_oak',
  glass: 'light_blue_stained_glass_pane', glassHead: 'blue_stained_glass_pane', glassApse: ['red_stained_glass_pane', 'yellow_stained_glass_pane'],
  rose: { hub: 'yellow_stained_glass_pane', inner: 'yellow_stained_glass_pane', mid: 'white_stained_glass_pane', outer: ['red_stained_glass_pane', 'light_blue_stained_glass_pane'] },
  carpet: 'red_carpet',
  gold: 'gold_block', candle: 'white_candle',
}

export const MASSES = {
  front: { x0: 13, x1: 25, z: 2, portal: { doors: [18, 20], trumeau: 19, gablet: { x0: 15, x1: 23, z0: 0, z1: 1, peak: 11 } } },
  rose: { cx: 19, cy: 17, z: 2, r: 5.6 },
  tower: { x0: 4, x1: 12, z0: 2, z1: 10, cx: 8, cz: 6, shaftTop: 26, floors: [10, 18, 26], belfry: [27, 33], cornice: 34,
    parapet: 35, spire: { y0: 35, y1: 51, r0: 3.2 }, ladder: { x: 5, z: 9 }, sideDoor: 8, bells: [[7, 31, 6], [9, 31, 6]], beamY: 32 },
  nave: { wallX: 13, z0: 3, z1: 31, eave: 20, roofBase: 21, piers: [11, 15, 19, 23, 27, 31], bays: [13, 17, 21, 25, 29] },
  aisle: { wallX: 7, z0: 11, z1: 30, wallTop: 8, roofBase: 9, buttresses: [15, 19, 23, 27] },
  transept: { x0: 0, z0: 31, z1: 39, cz: 35, eave: 20 },
  choir: { z0: 40, z1: 47, piers: [39, 43, 47], floorY: 1 },
  apse: { cz: 48, wall: 6 }, // octagon: m = max(|x - 19|, z - 48, |x - 19| + z - 48 - 3); interior m <= 5, wall m = 6
  chapels: { transept: { x0: 0, x1: 7, z0: 40, z1: 44 }, aisle: { x0: 7, x1: 13, z0: 48, z1: 52 } },
  fleche: { x0: 17, x1: 21, z0: 33, z1: 37, y0: 35, top: 39, spire: [40, 48] },
}

// The ceremony, pinned by the brief and its tests (never move these)
export const CEREMONY = {
  altar: [19, 2, 49], groom: [20, 2, 49], gold: [19, 2, 50],
  guests: [[16, 1, 36], [17, 1, 36], [21, 1, 36], [22, 1, 36], [15, 1, 34], [23, 1, 34]],
  camera: [19, 5, 29],
}

export const ROOMS = [
  { name: 'Narthex', use: 'the west bay between the towers, under the rose', box: [14, 1, 3, 24, 19, 10] },
  { name: 'Nave', use: 'the congregation: pews either side of the carpet aisle', box: [14, 1, 11, 24, 19, 30] },
  { name: 'Aisles', use: 'the side walks under the lean-tos, lit by lancets', box: [8, 1, 11, 12, 8, 30] },
  { name: 'Crossing', use: 'the wedding guests stand here, facing the altar', box: [14, 1, 32, 24, 19, 38] },
  { name: 'Transepts', use: 'the arms, each with a chapel on its east wall', box: [1, 1, 32, 12, 19, 38] },
  { name: 'Choir', use: 'stalls either side, a step up from the crossing', box: [14, 2, 40, 24, 19, 47] },
  { name: 'Sanctuary', use: 'the altar in the apse, the couple standing before it', box: [14, 2, 48, 24, 19, 53] },
  { name: 'Chapels', use: 'the sacristy (brewing stand), the Lady chapel and two aisle chapels (lecterns)', box: [1, 1, 40, 12, 8, 51] },
  { name: 'Towers', use: 'three floors up a ladder to the belfry and its bells', box: [5, 1, 3, 11, 33, 9] },
]

export const OPENINGS = {
  doors: [
    { name: 'west portal, left leaf', at: [18, 1, 2], facing: 'north', hinge: 'left' },
    { name: 'west portal, right leaf', at: [20, 1, 2], facing: 'north', hinge: 'right' },
    { name: 'north tower portal', at: [8, 1, 2], facing: 'north', hinge: 'left' },
    { name: 'south tower portal', at: [30, 1, 2], facing: 'north', hinge: 'right' },
  ],
  aisleLancets: { y: [3, 6], head: 7 }, // three wide at each bay centre, the middle cell carried up a row
  clerestory: { y: [16, 18], head: 19 },
  arcade: { y: [1, 7] }, // pointed, three wide between piers
  transeptWindow: { x: 0, z: [33, 37], mullion: 35, y: [4, 13] },
  apseWindows: { east: { z: 54, x: [18, 20], y: [6, 14] }, sides: { x: 13, z: [49, 50], y: [7, 14] } },
}

// Furniture: no furniture spots (the brief needs none); every piece is a vanilla fixture.
export const FURNITURE = [
  { room: 'Nave', kind: 'pews', rows: [12, 14, 16, 18, 20, 22, 24, 26, 28], x: [[15, 18], [20, 23]], facing: 'north', block: 'dark_oak_stairs' },
  { room: 'Nave', kind: 'aisle carpet', x: 19, z: [3, 39] },
  { room: 'Choir', kind: 'stalls', x: [15, 23], z: [42, 46], block: 'dark_oak_stairs' },
  { room: 'Sanctuary', kind: 'altar', cells: [[17, 2, 50], [18, 2, 50], [19, 2, 50], [20, 2, 50], [21, 2, 50]], candles: [[17, 3, 50], [21, 3, 50]] },
  { room: 'Sanctuary', kind: 'reredos', z: 52, x: [16, 22] },
  { room: 'Sanctuary', kind: 'banners', at: [[14, 6, 48, 'east'], [24, 6, 48, 'west']], block: 'red_wall_banner' },
  { room: 'Transepts', kind: 'banners', at: [[1, 9, 32, 'east'], [37, 9, 32, 'west']], block: 'white_wall_banner' },
  { room: 'Chapels', kind: 'lectern', at: [[10, 1, 50, 'south'], [28, 1, 50, 'south'], [34, 1, 42, 'south']] },
  { room: 'Chapels', kind: 'brewing stand', at: [4, 1, 42] },
  { room: 'Chapels', kind: 'candle altar', at: [[10, 1, 51], [28, 1, 51], [3, 1, 43], [35, 1, 43]] },
]

// The walking line: up the steps, through the west portal, down the carpet aisle past the pews, across the crossing
// (the guests' floor), up the choir step to the altar; and in at a tower's side portal and up its ladder through the
// hatches of its three floors to the belfry.
export const WALK = [
  [19, 1, 0], [18, 1, 1], [18, 1, 2], [18, 1, 3], [19, 1, 4], [19, 1, 20], [19, 1, 35], [19, 2, 40], [19, 2, 45], [19, 2, 49],
  [8, 1, 1], [8, 1, 2], [8, 1, 4], [5, 1, 9], [5, 25, 9], [5, 26, 9], [6, 27, 8],
]
