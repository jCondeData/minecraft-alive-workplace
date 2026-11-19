// The Hanging Gardens (Wonder, ROADMAP 35.19 / rebuild 52.6), the plan: drawn before any wall, from the brief
// (tools/blueprints/briefs/hanging_gardens.md) only. recipe.mjs assembles it from the parts library (../parts) in passes,
// one stage after another on a StagedCanvas: a later stage may only add (it throws on drawing over an earlier stage's
// block), and verify() checks every stage as it stands.
//
// Three rough concepts, before choosing:
//   A. "The Terraced Mount" (the gardens as a man-made mountain). Massing: four stepped terraces, each a vaulted hall
//      under a garden, set back toward the rear so the front falls in deep green steps to the forecourt and the back
//      rises nearly sheer (a hillside built of brick). Roof: none but gardens: each terrace's top is its roof, crowned by
//      an open Water Pavilion with an oculus over the cistern. Plan: a processional stair up the axis to the first
//      terrace, then a stair climbing each terrace's face in a zig-zag (right, left, right) to the crown, one farm
//      corner at each turn; green spills over every terrace edge down glazed arcades. Story: the king's gardens rise
//      tier on tier on vaults, watered by a shaft that lifts the river to a cistern at the top.
//   B. "The Ring Gardens". Massing: a low round-cornered amphitheatre of terraces round a sunken water court open to
//      the front. Roof: none; a colonnade on the top ring. Plan: ramps round the ring. Story: a garden court. (It reads
//      as a stadium of hedges, has no top to climb to, and the four farms sit on one ring: no growth upward by stage.)
//   C. "The Garden Bridge". Massing: two garden towers joined by an arched aqueduct carrying a hanging garden between
//      them. Roof: flat tower tops with pergolas. Plan: stairs in the towers. Story: water carried over the land.
//      (Strong from the front, a thin slab from the side, and 15,000 blocks would make the towers squat boxes.)
// Strongest: A. It is the one people mean by "Hanging Gardens" (green hanging over the edges of tiers that climb), it
// grows by stages the way it was built (each stage raises a terrace and turns the stair once more), the climb from the
// ground to the crown is the walk the players take, and setting the tiers back toward the rear gives it two different
// faces: a cascade of gardens to the front and a sheer, vine-hung brick cliff behind, so it is not a symmetric cake.
//
// Coordinates: x across (0..46, the axis x 23), y up (0 = the layer on the clicked block), z from the front (z 0) to
// the back (z 43).
//
// Named parts (the owner's feedback can point at them):
//   Forecourt          z 0..3 at ground: the Processional Stair up the axis (x 21..25, cheek walls x 20 and 26) and its
//                      two Reflecting Pools (x 12..18 and 28..34), the two Vault doors (x 9 and 37).
//   The Vaults         Terrace I's hall (y 1..4, under the Lower Terrace): brick piers with capitals, hanging lamps, the
//                      nursery (potted saplings), the seed store (hay and sacks), the Well of the Shaft.
//   Lower Terrace      Terrace I's top (y 6): Farm I (wheat, front left), the Palm Basin (front right), the side walks.
//   Gallery Stair      climbs Terrace II's face rising east (x 27..32, z 12..13) to Farm II.
//   The Gallery        Terrace II's hall (y 7..10), glazed to the front: the orangery of potted azaleas.
//   Middle Terrace     Terrace II's top (y 12): Farm II (carrots, front right), the Glow-Berry Pergola (front left).
//   Cistern Stair      climbs Terrace III's face rising west (x 30..25, z 20..21).
//   Upper Hall         Terrace III's hall (y 13..16), glazed to the front.
//   Upper Terrace      Terrace III's top (y 18): Farm III (beetroots, front left), the Cypress Walk (right).
//   Crown Stair        climbs Terrace IV's face rising east (x 25..30, z 28..29).
//   Crown Garden       Terrace IV's top (y 24): Farm IV (all three crops, left), the Water Pavilion over the Cistern
//                      (an oculus over the water), crown palms (right).
//   The Shaft          a column of still water from the Vaults' floor to the Cistern (x 23, z 36): the river lifted to
//                      the top, seen through a window in every hall.
//   Hanging Curtains   on every terrace face: leaves spill over the edge in every other bay and vines hang below them;
//                      the bays between hold the arcade's windows under a hood.
export const SIZE = [47, 33, 44] // w, h, d
export const AX = 23

// Palette (60/30/10): sun-dried mud brick for the terraces' walls (60), sandstone for every dressing a hand touches or
// the eye follows (piers, cornices, parapets, floors, the pavilion) (30), and the glaze of the old gates, a band of blue
// tiles under every cornice, with the greens and the water (10). Darkest at the foot: a baked-brick plinth.
export const PALETTE = {
  wall: [['mud_bricks', 9], ['packed_mud', 2]],
  wallStairs: 'mud_brick_stairs',
  pier: 'mud_bricks',
  plinth: 'bricks',
  plinthStairs: 'brick_stairs',
  dress: 'cut_sandstone',
  dressStairs: 'sandstone_stairs',
  cap: 'chiseled_sandstone',
  floor: [['smooth_sandstone', 5], ['cut_sandstone', 2], ['sandstone', 1]],
  hallFloor: [['smooth_sandstone', 3], ['bricks', 1]],
  parapet: 'sandstone_wall',
  coping: 'smooth_sandstone_slab',
  frieze: 'blue_glazed_terracotta',
  glass: 'glass_pane',
  door: 'spruce_door',
  beam: 'stripped_spruce_log',
  post: 'stripped_spruce_log',
  soil: 'grass_block',
  root: 'rooted_dirt',
  kerb: 'packed_mud',
  drapeLeaves: [['azalea_leaves', 3], ['flowering_azalea_leaves', 2], ['oak_leaves', 2]],
  hedge: [['azalea_leaves', 2], ['flowering_azalea_leaves', 1]],
  vine: 'vine',
  palm: { log: 'jungle_log', fronds: 'jungle_leaves', dates: 'cocoa' },
  cypress: { log: 'spruce_log', leaves: 'spruce_leaves' },
  berries: 'cave_vines',
  berriesPlant: 'cave_vines_plant',
}

// The four terraces: their rectangles (walls included), the hall inside (iy0..iy1), the ceiling (F - 1) and the top (F).
export const TIERS = [
  { n: 1, x0: 1, x1: 45, z0: 4, z1: 42, base: 0, iy0: 1, F: 6 },
  { n: 2, x0: 6, x1: 40, z0: 14, z1: 41, base: 7, iy0: 7, F: 12 },
  { n: 3, x0: 10, x1: 36, z0: 22, z1: 40, base: 13, iy0: 13, F: 18 },
  { n: 4, x0: 14, x1: 32, z0: 30, z1: 39, base: 19, iy0: 19, F: 24 },
]

// The way up: the Processional Stair, then a stair along each terrace's face, its last stair level with the top.
export const STAIR0 = { x0: 21, x1: 25, cheeks: [20, 26], steps: 7 } // stair y k at z k, k 0..6
export const FLIGHTS = [
  { stage: 2, x0: 27, along: 'east', zs: [12, 13], from: 6, steps: 6, gap: [31, 33] }, // Gallery Stair: to Terrace II
  { stage: 3, x0: 30, along: 'west', zs: [20, 21], from: 12, steps: 6, gap: [24, 26] }, // Cistern Stair: to Terrace III
  { stage: 4, x0: 25, along: 'east', zs: [28, 29], from: 18, steps: 6, gap: [29, 31] }, // Crown Stair: to Terrace IV
]

// The four farm corners (one a stage): field (farmland round a row or a column of water), composter, its chest beside it
// (Manhattan 1; the brief allows 2). Each on its own terrace: six apart in height, so no field joins another and no
// composter reaches another's farmland (4 up or down).
export const FARMS = [
  { stage: 1, x0: 4, x1: 14, z0: 7, z1: 11, y: 6, water: 'row', crops: ['wheat', 'wheat', 'carrots', 'wheat'], composter: [16, 7, 9], chest: [16, 7, 10] },
  { stage: 2, x0: 34, x1: 38, z0: 16, z1: 20, y: 12, water: 'row', crops: ['carrots', 'carrots', 'beetroots', 'carrots'], composter: [32, 13, 18], chest: [32, 13, 19] },
  { stage: 3, x0: 13, x1: 19, z0: 24, z1: 28, y: 18, water: 'row', crops: ['beetroots', 'beetroots', 'wheat', 'beetroots'], composter: [21, 19, 26], chest: [21, 19, 27] },
  { stage: 4, x0: 15, x1: 17, z0: 31, z1: 38, y: 24, water: 'column', crops: ['wheat', 'carrots', 'beetroots'], composter: [18, 25, 31], chest: [19, 25, 31] },
]

export const SHAFT = { x: 23, z: 36 } // the column of water, ringed by a 3x3 of wall, from y 1 to the cistern at y 24
export const CISTERN = { x0: 21, x1: 25, z0: 35, z1: 36, y: 24 }
export const PAVILION = { x0: 19, x1: 27, z0: 33, z1: 38, y0: 25, y1: 28, roof: 29 }
export const POOLS = [{ x0: 12, x1: 18 }, { x0: 28, x1: 34 }] // z 0..2 at ground: water at y 1 in a curb
export const BASIN = { x0: 30, x1: 40, z0: 8, z1: 9, y: 6 } // the Palm Basin on the Lower Terrace
export const VAULT_DOORS = [9, 37]
export const HALL_DOORS = [{ tier: 2, x: 17 }, { tier: 3, x: 17 }] // from the terrace below, on the hall's front

export const ROOMS = [
  { name: 'The Vaults', use: 'the gardens\' undercroft: seed store, nursery, the Well of the Shaft', box: [2, 1, 5, 44, 4, 41] },
  { name: 'The Gallery', use: 'the orangery: potted azaleas along the glazed arcade', box: [7, 7, 15, 39, 10, 40] },
  { name: 'Upper Hall', use: 'the gardeners\' hall under the Upper Terrace', box: [11, 13, 23, 35, 16, 39] },
  { name: 'Water Pavilion', use: 'the crown: a colonnade round the cistern, open to the sky over the water', box: [19, 25, 33, 27, 28, 38] },
]

// Furniture footprints (none read by a job but the composters and chests; recorded so the plan holds the rooms).
export const FURNITURE = [
  ...FARMS.map(f => ({ kind: 'composter', cells: [f.composter] })),
  ...FARMS.map(f => ({ kind: 'chest', cells: [f.chest] })),
  { kind: 'lanterns', where: 'hung from the halls\' ceilings between the piers; on the parapet piers; under the pavilion' },
]

// The walking line: ground -> Processional Stair (x 21..25, z 0..6) -> Lower Terrace (feet y 7): Farm I left, the Palm
// Basin right; or through a Vault door (x 9 / 37) into the Vaults -> Gallery Stair (x 27..32) -> Middle Terrace (feet
// 13), Farm II on the right, the Gallery's door (x 17) from the Lower Terrace -> Cistern Stair (x 30 down to 25) -> Upper
// Terrace (feet 19), Farm III on the left, the Upper Hall's door (x 17) from the Middle Terrace -> Crown Stair (x 25..30)
// -> the Crown Garden (feet 25): Farm IV, the Water Pavilion round the Cistern.
export const STAGE_TITLES = [
  'The Vaults and the Lower Terrace',
  'The Gallery and the Middle Terrace',
  'The Upper Terrace and its Hall',
  'The Crown Garden and the Water Pavilion',
]
