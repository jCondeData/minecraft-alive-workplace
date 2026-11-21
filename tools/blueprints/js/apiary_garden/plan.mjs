// The Apiary Garden, the plan (ROADMAP 49; the brief: tools/blueprints/briefs/apiary_garden.md). Drawn before any
// block: the story, the palette, the masses, the ground map with every bed and path, the hives, the chests and the
// walking line. recipe.mjs then assembles it from the parts library (../parts) in passes. LEVEL=1 or 2.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Bee Pavilion: one open timber pavilion (a square of posts under a steep pyramid roof with a stepped cap) on
//      a round dais, hives on a stand in the middle, four quartered flower beds round it, a fenced square plot.
//      Story: a village's tidy show apiary. Reads as a gazebo with boxes in it; the hives hide under a roof.
//   B. The Terraced Bank: three stepped terraces of raised beds climbing to the back, hives set in each retaining wall
//      facing the front, a little lean-to shed with the chest on the top tier. No real roof. Story: a hillside
//      apiary cut into a slope. Strong in section, but on flat ground it is a staircase of boxes from 20 blocks away.
//   C. The Skep Wall: a thick stone wall across the back of the plot, its face pierced by bee boles (each a hive in
//      a niche, smoked by a campfire glowing in the niche's foot), and at its heart a giant straw skep, a coiled hay
//      dome as tall as a house, which is the Beekeeper's honey store. The wall cups a flower garden with a stone bee
//      font in the middle, a low flowering hedge round the front. Level II: the colony swarms. A daughter skep, lower,
//      settles at the wall's east end with two more smoked hives either side of its door, the mother skep takes two
//      hives by its own door, and the hedge opens east onto a bee meadow under a blossoming cherry.
//   C is the strongest: its outline is the one thing in the village that could only be an apiary (a skep, the
//   oldest picture of beekeeping there is), seen from anywhere; the boles give the wall real depth, light and shadow
//   in every niche and a fire's glow at night; the smoke under every hive is the game's own rule doing work; and
//   Level II's story (a swarm, a daughter skep) grows straight out of Level I instead of bolting a shed on.
//   A is a gazebo; B needs a slope the plot doesn't have. Building C.
//
// Story: "the Skep Wall". Before box hives, bees lived in straw skeps set into niches of a sun-warmed garden wall
// (bee boles). This Beekeeper keeps the old wall and its rhythm, but her niches hold wooden hives over little smoke
// fires so she can take the honey without the bees pouring out, and her honey, comb and bottles live inside the
// one great straw skep at the wall's heart. Bees need flowers and water: the garden in front is all bee flowers,
// round a stone font with a lily pad.
//
// Coordinates: x across the front (west 0 -> east 12; 16 in Level II), y up (0 = the layer on the clicked block: the
// garden's ground), z from the front (z = 0, north) to the back (12). The way in is the gap in the front hedge at
// x 6, between two lantern piers.
//
// Masses (named parts the owner's feedback can point at):
//   Mother skep   the hero: a hay dome on a stone stand, centre (6, 10), five wide, 9 tall (y 0..8): four hollow
//                 courses (the store room, 3 x 3 inside), a full ceiling course, then three stepping in to a
//                 blunt crown (a plus of straw). A dark oak door with a log lintel across its face; inside, the double chest and comb.
//   Bole walls    west x 0..3 and east x 9..12 (I) / 9..11 (II), z 9..11, three thick: body y 1..3, a saddleback
//                 coping at y 4 with a ridge slab at y 5; two boles in each (x 1, 3, 9, 11), a sloped buttress at
//                 each outer end in front and two at the back, the end piers carrying a lantern on a wall post.
//   Garden        beds of bee flowers each side of a path that rings the bee font (x 5..7, z 3..5), a wall walk
//                 along the boles' feet (z 8), a low hedge of azalea (flowering in places) round three sides.
//   Daughter skep (II) the swarm: a lower dome (y 0..6), centre (14, 10), its door between two smoked hives.
//   Bee meadow    (II) x 12..16 in front of the daughter skep, shade flowers; a young cherry in blossom in the west
//                 front corner balances the new east end.

export const LEVELS = {
  1: { size: [13, 9, 13] },
  2: { size: [17, 9, 13] },
}

// Palette (60/30/10): grey stone (the wall, the stands, the font) / golden straw (the skeps) / dark oak (doors,
// lintels) with the garden's green and the flowers' blue, purple and white. Why: the straw has to glow against
// something cool and plain, so the wall is grey, darkest at its cobbled foot, mossy low down where the beds keep it
// damp; dark oak is the only wood, so the doors and lintels read as one hand's work; the flowers are the bees'
// colours (blues and purples first, white and yellow between), not a rainbow.
export const PALETTE = {
  wall: [['stone_bricks', 7], ['mossy_stone_bricks', 3], ['cracked_stone_bricks', 1]],
  wallHigh: [['stone_bricks', 8], ['cracked_stone_bricks', 1]], // the upper courses: moss stays low
  plinth: [['cobblestone', 4], ['mossy_cobblestone', 3], ['stone', 1]],
  stand: 'polished_andesite', // the skeps' round stones and the font's floor
  coping: { full: 'stone_bricks', stairs: 'stone_brick_stairs', slab: 'stone_brick_slab', wall: 'stone_brick_wall' },
  straw: 'hay_block',
  wood: 'dark_oak', // doors
  lintel: 'dark_oak_log',
  soil: 'grass_block',
  path: 'dirt_path',
  hedge: [['azalea_leaves', 3], ['flowering_azalea_leaves', 2]],
  tree: { log: 'cherry_log', leaf: 'cherry_leaves' },
  hive: 'beehive',
  fire: 'campfire',
  chest: 'chest',
  store: 'honeycomb_block', // comb stacked by the chests
  pad: 'lily_pad',
}

// The ground, row by row (z = 0 the front row), one letter per x. Every cell is the y 0 layer with what stands on it:
//   .  grass, bare (soil the Beekeeper can plant)      p  path           H  grass and a hedge leaf on it
//   G  plinth and a gate pier with its lantern         W  plinth under a bole wall   B  plinth under a buttress
//   S  a skep's stone stand                            F  the font's stone floor     T  grass under the cherry's trunk
//   flowers on grass: a allium, c cornflower, b azure bluet, o oxeye daisy, d dandelion, u blue orchid,
//   y lily of the valley, f fern; two-high: l lilac, e peony
export const FLOWERS = {
  a: 'allium', c: 'cornflower', b: 'azure_bluet', o: 'oxeye_daisy', d: 'dandelion', u: 'blue_orchid',
  y: 'lily_of_the_valley', f: 'fern', l: 'lilac', e: 'peony',
}
export const GROUND = {
  1: [
    'HHHHHGpGHHHHH', // z 0  the hedge, the gate piers and the way in
    'HlodobpbodolH', // z 1  a front border, lilac at the corners
    'HacapppppacaH', // z 2  the ring path round the font
    'HcecpFFFpcecH', // z 3  peonies in the side beds
    'HacupFFFpucaH', // z 4
    'HbacpFFFpcabH', // z 5
    'Ho.apppppa.oH', // z 6
    'H.c.pypyp.c.H', // z 7  bare soil left at the boles' feet; spurs up to the wall walk
    'BpppSSSSSpppB', // z 8  the wall walk, the buttresses
    'WWWWSSSSSWWWW', // z 9  the bole walls and the mother skep
    'WWWWSSSSSWWWW', // z 10
    'WWWWSSSSSWWWW', // z 11
    'f.B.SSSSS.B.f', // z 12 behind the wall: back buttresses, ferns
  ],
  2: [
    'HHHHHGpGHHHHHHHHH',
    'HTodobpbodoloybyH', // the cherry at (1, 1); the east hedge (x 12) opens into the meadow
    'HacapppppacacybyH',
    'HcecpFFFpcecabybH',
    'HacupFFFpucacybyH',
    'HbacpFFFpcabacacH',
    'Ho.apppppa.o.u.uH',
    'H.c.pypyp.c.ppppH', // the walk on to the daughter skep's door
    'BpppSSSSSpppSSSSS',
    'WWWWSSSSSWWWSSSSS',
    'WWWWSSSSSWWWSSSSS',
    'WWWWSSSSSWWWSSSSS',
    'f.B.SSSSS.B.SSSSS',
  ],
}

export const WALLS = {
  1: [{ name: 'west bole wall', x0: 0, x1: 3, end: 0 }, { name: 'east bole wall', x0: 9, x1: 12, end: 12 }],
  2: [{ name: 'west bole wall', x0: 0, x1: 3, end: 0 }, { name: 'east bole wall', x0: 9, x1: 11, end: null }],
}
export const WALL = { z0: 9, z1: 11, top: 3, coping: 4 } // front face z 9, core z 10, back face z 11
export const BOLES = [[1, 2, 10], [3, 2, 10], [9, 2, 10], [11, 2, 10]] // hives in the core, facing north, fires under
export const FRONT_BUTTRESSES = { 1: [0, 12], 2: [0] } // at z 8, sloped tops at y 3
export const BACK_BUTTRESSES = [2, 10] // at z 12

// Skeps: courses from y 1 up ({ r2, hollow }: see parts/apiary_skep.mjs's skepDome)
export const SKEPS = {
  mother: {
    centre: [6, 10],
    courses: [{ r2: 5, hollow: true }, { r2: 5, hollow: true }, { r2: 5, hollow: true }, { r2: 5, hollow: true },
      { r2: 5 }, { r2: 4 }, { r2: 2 }, { r2: 1 }], // straight sides, then a round shoulder and a blunt crown
    door: [6, 1, 8], lintel: [[5, 3, 8], [6, 3, 8], [7, 3, 8]],
    chest: { pos: [5, 1, 11], facing: 'north', along: 'east' },
    comb: [[7, 1, 11], [7, 2, 11]],
    lamp: [6, 4, 10], // hung from the ceiling course
    hives: { 2: [[5, 2, 8], [7, 2, 8]] }, // Level II: smoked hives either side of the door
    backing: { 2: [[5, 1, 9], [7, 1, 9]] }, // comb stacked inside behind their fires, so the room stays shut
  },
  daughter: { // Level II
    centre: [14, 10],
    courses: [{ r2: 5, hollow: true }, { r2: 5, hollow: true }, { r2: 5 }, { r2: 4 }, { r2: 2 }, { r2: 1 }],
    door: [14, 1, 8], lintel: [[13, 3, 8], [14, 3, 8], [15, 3, 8]],
    chest: { pos: [13, 1, 11], facing: 'north', along: 'east' },
    comb: [[15, 1, 11]],
    lamp: [15, 2, 10], // hung from the ceiling course, clear of the comb (not over it, where it read as standing)
    hives: { 2: [[13, 2, 8], [15, 2, 8]] },
    backing: { 2: [[13, 1, 9], [15, 1, 9]] },
  },
}

export const GATE_PIERS = [[5, 0], [7, 0]] // stone, a wall post, a lantern on it
export const FONT = { centre: [6, 4], y: 1 }

// The cherry (II): trunk and crown, drawn by hand (logs and persistent leaves)
export const CHERRY = {
  // a young tree in the west front corner (where Level I's lilac stood), weighing against the daughter skep at the
  // east end, and leaving the meadow open so her door and hives are in view from the way in
  logs: [[1, 1, 1], [1, 2, 1], [1, 3, 1]],
  crown: [ // [y, z, x0, x1] rows: a small round crown over the hedge corner
    [3, 0, 0, 0], [3, 1, 0, 0], [3, 2, 0, 0], [3, 1, 3, 3],
    [4, 0, 0, 2], [4, 1, 0, 3], [4, 2, 0, 3], [4, 3, 1, 1],
    [5, 0, 1, 1], [5, 1, 0, 2], [5, 2, 1, 1],
  ],
}

// Rooms: the skeps' insides are stores, not furnished rooms (no furniture spots in this family).
export const ROOMS = [
  { name: 'Mother skep', use: 'the honey store: double chest, comb, a hung lantern', box: [5, 1, 9, 7, 4, 11] },
  { name: 'Daughter skep (II)', use: 'the east hives\' store: double chest, comb, a hung lantern', box: [13, 1, 9, 15, 2, 11] },
]

// The walking line: in at the hedge gap, round the font, up a spur to the wall walk past every bole, into the skep
// to the chest; in II on along the walk to the daughter skep's door.
export const WALK = [
  [6, 1, 0], [6, 1, 1], [6, 1, 2], [5, 1, 2], [4, 1, 2], [4, 1, 6], [4, 1, 7], [3, 1, 8], [1, 1, 8], [3, 1, 8], [4, 1, 7],
  [5, 1, 6], [6, 1, 6], [6, 1, 7], [6, 1, 8], [6, 1, 10], // at the mother skep's chest
  [7, 1, 6], [8, 1, 7], [9, 1, 8], [11, 1, 8], [12, 1, 7], [14, 1, 7], [14, 1, 10], // II: the daughter skep's chest
]
