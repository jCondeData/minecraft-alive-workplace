// Ranch, the DESERT look (looks/desert/ranch[_2], ROADMAP 49.10e, as 49.10a): the same building as the shipped Ranch
// (../ranch/plan.mjs: the aisle with the Rancher's smoker and his two chests at its end, the four box stalls either side
// of it, the hay loft over the aisle up its ladder, the wicket door and the hay door, Level II's feed and bunk room with
// its barrel, double chest, bed, desk and chair, and every furniture spot in the same cell and facing at both levels),
// drawn again for a hot, dry village. It speaks the desert village's language the Cottage, the Stone House, the Weaver's
// Cottage, the Daub House and the Manor set (coursed sandstone below, a cut-sandstone course at the loft floor, mud
// brick above, flat roofs behind a smooth coping, spruce beam ends, merlons, arches, a windcatcher, date palms), and it
// is a caravan town's HORSE AND CAMEL STABLE: a fortified hay tower over two stall ranges, the stock's water by the gate.
//
// --- Three rough concepts --------------------------------------------------------------------------------------------
//  1. "The caravanserai": the stalls as arcaded ranges round a square court with the trough in its middle, one gate
//     tower in a blank front. The truest desert stable, but the shipped aisle, loft and stall cells pin a long barn
//     (x 1..11, z 3..11) with its door on the street: the court would be the barn itself, roofed, and the herd's ground
//     is outside it anyway. A court of 13 x 3 in front is a corridor, not a court.
//  2. "The ramada barn": the shipped barn's shape as a long low adobe under one flat roof, a palm-rib ramada (a shade of
//     poles and fronds) across the whole yard, the stalls opening onto it. Shady and plain, a ranch of the American
//     Southwest; but one roof height for the aisle, the stalls and the loft (the loft lamp hangs at y 11) is a 12-high
//     box from 20 blocks, and a ramada over a 3-deep yard hides the gate.
//  3. "The Hay Tower" (a kasbah stable of the Draa valley): the aisle and its loft a mud-brick TOWER (the shipped
//     monitor's cells) standing over two low flat-roofed STALL RANGES of coursed sandstone; the tower crowned with
//     MERLONS and corner pinnacles, a slotted WINDCATCHER rising from the middle of its roof (it pulls the wind down
//     into the loft and the stalls: horses suffer the heat), the hay door under a timber hoist beam on its face, the
//     smoker's flue up its back. Before it a walled YARD entered through an ARCHED GATEWAY carrying the ranch's banner
//     on its merlons, a stone trough under a date palm. Level II: the ranch hand's BUNK HOUSE east of the stable, a
//     lower block behind an ARCADED LOGGIA, a fodder rick in the slot between them, and behind everything an ADOBE-PIER
//     CORRAL (dark-oak rails between mud-brick piers with lamps on the corner piers), a man-gap, the herd's double
//     gate, a run-in shed of mud brick under a palm-rib roof, a trough and two palms.
//     Story: on the caravan road the horse-and-camel breeder keeps his stock in the shade of thick walls, his hay high
//     and dry in a tower the windcatcher cools, his water by the gate where every traveller sees his banner.
//  Built: 3. It is the strongest: from 20 blocks it is a three-step skyline you can name on every side (low stall
//  ranges, the crenellated tower, the windcatcher over it, the flue at the back), the same "tall middle, low sheds"
//  outline a rider knows a stable by, said in mud and stone; the gateway, the portal, the hoist and the beam ends give
//  every face a layer; and Level II grows out of it the way desert farms grow, a lower block and a walled corral.
//  Not the safest: a tower with merlons can read as a fort (answered by the hay door, the hoist and the windcatcher),
//  and nothing is pitched.
//
// --- Design notes: what is done, what is left ----------------------------------------------------------------------
//  Done (2026-11-21): Levels I and II as candidates (build.sh -> build/rebuild/ranch_desert/ranch.nbt, ranch_2.nbt),
//  never live. The shipped furniture list drawn as is (../ranch/plan.mjs FURNITURE: the same parts, cells and facings);
//  the smoker (6, 1, 10), the chests (5, 1, 10) and (5, 1, 9), the shelf with its potted cactus, the wicket and hay
//  doors, the ladder, II's barrel (14, 1, 10), double chest, bed, desk, chair, nightstand and rug in their cells; the
//  bunk room's lamp hangs on one link from the flat deck (two in the shipped drawing). (7, 1, 10) stays open floor and
//  the old cauldron cells (9, 0, 2), (10, 0, 2) open ground. The way in is the gateway's open arch: no gate on it.
//  Checked: look_spots.py ranch_desert ranch: SAME BUILDING (8 and 13 shipped spots, none missing; no bed, container,
//  job block, door or ladder moved; only the bunk lamp's chain differs, allowed); rebuild.py --candidate: no CHECK line
//  at either level; holes.py (open included): 0 at both; every id vanilla 1.21.1; Level I 984 blocks, about 42 stacks
//  (limit 108), II 1,619 blocks, about 58; II keeps 98 % of I's blocks unchanged; sizes as shipped (13 x 16 x 13,
//  21 x 16 x 21), the windcatcher's cap at y 15, the top course.
//  Renders: build/rebuild/ranch_desert/renders/ (desert_<L>_<front|back|left|right|top>.png; cutaways
//  desert_<L>_cut3_<top|front>.png for the aisle, stalls and bunk room, desert_<L>_cut8_<top|front>.png for the loft).
//  Tried and changed: lanterns under the portal's hood stairs (the game drops them: the hood's ends are full blocks
//  now) and on bare merlons and slab caps (owner rule; they stand on sandstone-wall posts now); a pale deck rim round
//  every wall striped the stall ranges sand-mud-sand-mud (the rims are mud, a proud cornice of upside-down stairs
//  carries the line instead, on the beam ends); the banner stood on the gateway in front of the hay door's hoist (it
//  flies from the tower's front-west corner now, a keystone on the gate's arch); the yard palm's west fronds closed the
//  gap over the portal hood into a roof pit (holes.py; they stop at x 10); the slot between the stall range and the bunk
//  house was a gap between two roofs (the range's deck and parapet carried over a fodder rick now, its back closed);
//  windcatcher slots two courses read as a notch behind the merlons (three now, y 12..14); flat range sides (a pilaster
//  at each stall partition, smooth corner pinnacles on every parapet).
//  Round 2 (the critic's notes, briefs/ranch_looks_critic.md, BACK narrow on Detail 3): 1 the run-in shed opened: four
//        stripped posts (x 1..5, z 17..19) under a palm-rib slab roof a block over them on every side (x 1..6, z 16..20),
//        its open edges on a pole beam, hay against the back rails; no mud walls; 2 the corral's rails two courses high
//        (as tall as the piers), the piers one height, every 5 along the long sides and one mid-way up each short side,
//        lamps on the four corner piers only (the herd's double gate moved to z 15..16 beside the east pier); 3 the
//        windcatcher now the tower's full width (x 4..8, z 5..9, a new part slottedCatcher): every face two tall
//        dark-oak grilles (y 12..14) between three smooth piers, a projecting cap course of upside-down stairs at y 15,
//        the throat still open over the loft lamp; 4 the yard palm moved to the west corner (1, 0, 1), closing the
//        trough's west end (trough water at (2..3, 0, 1)), its fronds kept off x >= 4 so the portal and the hay door are
//        clear; hay now in the east corner; 5 the bunk house's parapet crenellated like the tower's (merlons y 9, corner
//        pinnacles), four dark-oak spouts out over its cornice (back and east), two rain jars on its deck.
//        Checked after round 2: look_spots SAME BUILDING (8/8, 13/13; only the bunk lamp's chain differs); holes.py 0
//        (open included) at both levels; rebuild.py no CHECK line; Level I 1,090 blocks, II 1,775. Renders retaken
//        (same names) in build/rebuild/ranch_desert/renders/.
//  Open for the critic: the bunk house's roof is a deck nobody climbs to; the yard is three deep (the shipped aisle and door pin the barn's front at z 3).
//
// --- Named parts (template coordinates: x across the front, west 0 -> east; z from the front, z = 0, to the back) --
//   Hay tower   the shipped monitor's cells: walls x 4..8, z 3..11. The aisle (x 5..7) on the ground, the loft (floor
//               y 4, spruce) over it; coursed sandstone y 1..3 on the front and back, the stall fronts (mud-brick half
//               walls, dark-oak gates and rails, sandstone piers at z 3, 7, 11) on the sides, a cut-sandstone course at
//               y 4 all round, mud brick y 5..9, the roof deck y 10, a mud parapet y 11, merlons y 12 (smooth-sandstone
//               crenels), the corners raised as pinnacles to y 13; clerestory lights over the stall ranges (y 7..8).
//   Windcatcher x 5..7, z 6..8 on the tower's roof, y 10..15: its throat open into the loft (the loft lamp hangs in it),
//               slots y 13..14 on every face, a smooth cap at y 15.
//   Stall ranges x 1..3 and x 9..11, z 3..11: coursed sandstone y 1..3 with smooth quoins, the course y 4, mud brick
//               y 5, the deck y 6, a mud parapet y 7 with a smooth coping; barred stall windows; sand-bedded stalls.
//   Portal      the wicket door (6, 1, 3) in a carved frame a block out (chiseled jambs at x 5 and 7, z 2, a keystone),
//               a corbelled hood at y 4 with two lanterns hung under its ends, the step.
//   Hoist       the hay door (6, 5, 3) under a dark-oak hood, a spruce hoist beam at y 8 out to z 1, a chain and lantern.
//   Flue        the smoker's sandstone stack up the tower's back (x 6, z 12) to y 12, its breast to y 4, smoke on top.
//   Beam ends   spruce poles a block out: under the stall decks (y 5), under the tower's deck (y 9).
//   Gateway     x 4..8, z 0: two cut-sandstone piers, a round arch (haunches at y 4), a band at y 5, merlons at y 6, the
//               ranch's red banner on the middle merlon, lanterns on the end merlons. No gate: the way in stays open.
//   Yard        z 0..2: mud-brick walls with a coping from the gateway to the stall ranges' corners; the stone trough
//               (water (2..3, 0, 1)) with jars on its rim, a date palm (1, 0, 1) at its west end; hay in the east corner; the old
//               cauldron cells (9, 0, 2), (10, 0, 2) left open ground.
//   Bunk house  (II) walls x 13..19, z 5..11: the same courses, deck y 7, parapet y 8, coping y 9 (a step over the
//               stall ranges, under the tower); the shipped room inside, smooth-sandstone floor.
//   Loggia      (II) x 13..19, z 3..4 before the bunk house: four cut-sandstone piers, three openings (the middle one
//               on the door), a deck at y 4 with a mud-brick balustrade, a lantern over the door, three steps.
//   Fodder rick (II) the slot x 12, z 5..11 between the stall range and the bunk house: hay stacked to y 5 under the
//               stall range's deck carried across it (y 6), a mud parapet and coping over it.
//   Corral      (II) x 0..20, z 13..20: dark-oak rails between mud-brick piers with smooth caps, lanterns on the
//               corner piers, a man-gap (0, 0, 16), the double gate (20, 0, 16..17); a mud-brick run-in shed under a
//               palm-rib roof (x 1..5, z 18..20) with hay, a stone trough, two date palms, a snubbing post.
import { FURNITURE as R_FURNITURE, OPENINGS as R_OPENINGS } from '../ranch/plan.mjs'

export const SIZE = { 1: [13, 16, 13], 2: [21, 16, 21] } // as shipped

// Palette 60/30/10: coursed sandstone ground storeys and mud brick above (60) / smooth and cut sandstone dressings: the
// plinth, quoins, the course at the loft floor, the portal, the gateway, the copings, crenels and the windcatcher (30) /
// dark oak (doors, gates, rails, the hoods and the palm-rib roofs), spruce beam ends, the red banner, palms and hay (10).
export const PALETTE = {
  ashlar: [['sandstone', 6], ['cut_sandstone', 3], ['smooth_sandstone', 1]],
  mud: [['mud_bricks', 7], ['packed_mud', 2]],
  plinth: 'smooth_sandstone', quoin: 'smooth_sandstone', course: 'cut_sandstone', carved: 'chiseled_sandstone',
  pier: 'cut_sandstone', deck: 'smooth_sandstone', coping: 'smooth_sandstone_slab', parapet: 'mud_bricks',
  crenel: 'smooth_sandstone_slab', pinnacle: 'smooth_sandstone_slab', brow: 'cut_sandstone', hood: 'sandstone_stairs',
  arch: 'sandstone_stairs', step: 'sandstone_stairs', balustrade: 'mud_brick_wall', paving: 'smooth_sandstone',
  floor: 'smooth_sandstone', stallFloor: 'sand', loftFloor: 'spruce_planks', half: 'mud_bricks',
  beam: 'spruce_fence', hoist: 'spruce_log', wood: 'dark_oak', door: 'dark_oak', glass: 'glass_pane', bars: 'iron_bars',
  shade: 'dark_oak_slab', rim: 'cut_sandstone', hay: 'hay_block', banner: 'red_banner', soil: 'dirt',
  catcher: { body: [['mud_bricks', 5], ['packed_mud', 1]], corner: 'smooth_sandstone', cap: 'smooth_sandstone', capTop: 'smooth_sandstone_slab' },
  palm: { trunk: 'jungle_log', leaves: 'jungle_leaves' },
  post: 'stripped_spruce_log', lampPost: 'sandstone_wall',
}

export const TOWER = {
  walls: { x0: 4, x1: 8, z0: 3, z1: 11 }, aisle: { x0: 5, x1: 7, z0: 4, z1: 10 },
  piers: [3, 7, 11], course: 4, loftY: 4, wallTop: 9, deckY: 10, parapetY: 11, merlonY: 12, pinnacleY: 13,
  catcher: { x0: 4, z0: 5, x1: 8, z1: 9, y0: 10, slotY0: 12, slotY1: 14, capY: 15, throat: [6, 7] }, // full width (round 2)
  banner: [4, 13, 3], // on the front-west corner merlon, in place of its pinnacle
  flue: { x: 6, z: 12, top: 12 },
}

export const RANGES = {
  west: { outer: 1, x0: 1, x1: 3, inner: [2, 3] },
  east: { outer: 11, x0: 9, x1: 11, inner: [9, 10] },
  z0: 3, z1: 11, wallTop: 5, deckY: 6, parapetY: 7,
  stalls: [{ z0: 4, z1: 6, gate: 5 }, { z0: 8, z1: 10, gate: 9 }], partition: 7,
}

export const GATEWAY = { piers: [4, 8], z: 0, archY: 4, bandY: 5, merlonY: 6, keystone: [6, 4, 0], lamps: [[4, 7, 0], [8, 7, 0]] }

export const YARD = {
  walls: [[[3, 0], [0, 0], [0, 3]], [[9, 0], [12, 0], [12, 3]]], // mud brick y 0..1, coping y 2
  trough: { water: [[2, 1], [3, 1]], rim: [[2, 2], [3, 2], [4, 1]] }, // the palm's soil (1, 0, 1) closes its west end
  jars: [[2, 1, 2], [4, 1, 1]],
  palm: [1, 0, 1], palmHeight: 7, palmClear: 4, // in the west corner by the trough, its fronds clear of the portal (round 2)
  hay: [[9, 0, 1], [10, 0, 1], [9, 1, 1], [11, 0, 1], [11, 0, 2]],
  open: [[9, 0, 2], [10, 0, 2]], // the old drawings' cauldron cells: open ground
}

export const BUNK = {
  walls: { x0: 13, x1: 19, z0: 5, z1: 11 }, room: { x0: 14, x1: 18, z0: 6, z1: 10 },
  course: 4, wallTop: 6, deckY: 7, parapetY: 8,
  loggia: { x0: 13, x1: 19, z0: 3, z1: 4, piers: [13, 15, 17, 19], deckY: 4, lamp: [16, 3, 4], steps: [14, 16, 18] },
  merlonY: 9, // a crenellated parapet like the tower's (round 2)
  spouts: [[15, 8, 12, 'south'], [17, 8, 12, 'south'], [20, 8, 7, 'east'], [20, 8, 9, 'east']], // wooden canales on the cornice
  roofJars: [[14, 8, 10], [15, 8, 10]], // rain jars on the deck
  rick: { x: 12, z0: 5, z1: 11, top: 5, deckY: 6 }, // hay to the deck; the stall range's deck and parapet carried over it
}

export const CORRAL = {
  x0: 0, x1: 20, z0: 13, z1: 20,
  // one height, every 5 along the long sides and mid-way up the short ones, rails two courses between them (round 2)
  piers: [[0, 13], [5, 13], [10, 13], [15, 13], [20, 13], [0, 20], [5, 20], [10, 20], [15, 20], [20, 20], [0, 17], [20, 17]],
  lampPiers: [[0, 13], [20, 13], [0, 20], [20, 20]],
  manGap: [0, 0, 16], // one block between two piers: a villager walks through, a horse can't
  gates: [[20, 0, 15], [20, 0, 16]], // the herd's double gate
  shed: { x0: 1, x1: 6, z0: 16, z1: 20, roofY: 3, posts: [[1, 17], [5, 17], [1, 19], [5, 19]], hay: [[2, 0, 19], [3, 0, 19], [2, 1, 19], [4, 0, 19]] }, // open on posts, its roof overhanging (round 2)
  trough: { water: [[10, 19], [11, 19]] },
  palms: [[[15, 0, 16], 6], [[8, 0, 15], 5]],
  snubbingPost: [12, 16],
}

export const ROOMS = [
  { name: 'Aisle', use: 'the walk from the wicket door to the tack wall: the Rancher\'s smoker and chests at its end', box: [5, 1, 4, 7, 3, 10] },
  { name: 'Stalls', use: 'four sand-bedded box stalls, gated off the aisle, cooled from the windcatcher', box: [2, 1, 4, 10, 5, 10] },
  { name: 'Hay loft', use: 'hay stacked in the tower, up the ladder from the aisle, the hay door on the street', box: [5, 5, 4, 7, 9, 10] },
  { name: 'Feed and bunk room (II)', use: 'the feed barrel, the double chest, hay, the ranch hand\'s bunk and his ledger desk', box: [14, 1, 6, 18, 6, 10] },
]

// The shipped openings that hold the building together, cell for cell: the two doors of the tower, the ladder, the
// bunk house's door. The windows are this look's own.
export const OPENINGS = {
  barnDoor: R_OPENINGS.barnDoor, hayDoor: R_OPENINGS.hayDoor, ladder: R_OPENINGS.ladder, bunkDoor: R_OPENINGS.wing.door,
  portal: { jambs: [5, 7], z: 2, top: 3, hoodY: 4, hood: [4, 8], lamps: [[4, 3, 2], [8, 3, 2]] },
  hoist: { x: 6, y: 8, z0: 1, z1: 3, hood: { x0: 5, x1: 7, y: 7, z: 2 } },
  clerestory: { xs: [4, 8], zs: [5, 9], ys: [7, 8] },
  stallBars: [ // iron bars: the stall ranges' own lights
    [[1, 2, 5], [1, 3, 5]], [[1, 2, 9], [1, 3, 9]], [[11, 2, 5], [11, 3, 5]], [[11, 2, 9], [11, 3, 9]],
    [[2, 2, 3], [2, 3, 3]], [[10, 2, 3], [10, 3, 3]], [[2, 2, 11], [2, 3, 11]], [[10, 2, 11], [10, 3, 11]],
  ],
  windows: [ // glass panes with a cut-sandstone brow over each
    { name: 'loft back west', cells: [[5, 6, 11], [5, 7, 11]] },
    { name: 'loft back east', cells: [[7, 6, 11], [7, 7, 11]] },
  ],
  bunkWindows: [
    { name: 'bunk front west', cells: [[14, 2, 5], [14, 3, 5]] },
    { name: 'bunk front east', cells: [[18, 2, 5], [18, 3, 5]] },
    { name: 'bunk east', cells: [[19, 2, 8], [19, 3, 8]] },
    { name: 'bunk back', cells: [[14, 2, 11], [14, 3, 11]] },
    { name: 'bunk back high', cells: [[16, 5, 11]] },
  ],
}

export const VIGAS = {
  ranges: { y: 5, west: [4, 6, 8, 10], east: [4, 6, 8, 10], front: [2, 10], back: [2, 10] }, // x 0 and 12; z 2 and 12
  tower: { y: 9, front: [5, 7], back: [5, 7], sides: [4, 6, 8, 10] }, // z 2 and 12; x 3 and 9
  bunk: { y: 6, east: [6, 8, 10], back: [14, 16, 18] }, // x 20; z 12
}

// Every furniture piece: the shipped Ranch's, the same parts in the same cells and facings. The bunk room's lamp hangs
// on one link from the flat deck at y 7 (two from the shipped beam ring).
export const FURNITURE = {
  1: R_FURNITURE[1],
  2: R_FURNITURE[2].map(f => (f.part === 'hangingLamp' ? { ...f, chain: BUNK.deckY - f.pos[1] - 1 } : f)),
}

// The walking lines: the road -> through the gateway's arch -> across the yard -> the step -> the wicket door -> down
// the aisle to the smoker (he works it from (6, 1, 9)) and the chests; up the ladder to the loft. Level II: along the
// front to the loggia's middle step -> the bunk house's door -> the barrel; round the west side through the man-gap
// into the corral.
export const WALK = {
  1: [[6, 0, -1], [6, 0, 0], [6, 0, 1], [6, 0, 2], [6, 1, 3], [6, 1, 4], [6, 1, 6], [6, 1, 9], [7, 1, 5], [7, 5, 4], [6, 5, 5]],
  2: [[13, 0, 1], [16, 0, 2], [16, 1, 3], [16, 1, 4], [16, 1, 5], [16, 1, 6], [15, 1, 8], [14, 1, 9], [-1, 0, 16], [0, 0, 16], [3, 0, 16]],
}
