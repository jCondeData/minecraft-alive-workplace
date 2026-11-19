// The Great Wall (Wonder), the recipe: assembles plan.mjs from the parts library (../parts) in passes (block-out and
// structure, openings, depth and detail, interior, light), every stage in one drawing: each block remembers the stage
// that lays it (parts/great_wall_gate.mjs `staged`), so draw(n), the Wonder as it stands once stage n is done, is the
// blocks of stages 1..n, each exactly as the finished Wonder has it. verify() checks every stage against the brief.
// STAGE=<1..4> picks the stage the runner writes (default 4); CUT=<y> builds a cutaway with everything above y removed.
import {
  SIZE, STAGES, PALETTE, TOWERS, TOWER, RUNS, CURTAIN, GATEHOUSE, PASSAGE, BARBICAN, PAVILION, STAIRS, POSTS,
} from './plan.mjs'
import {
  Canvas, mix, stair, slab, crenels, fenceGates,
  staged, snapshot, wallSection, ridgedHip, pavilionSkirt, archway, portcullis, horseStair, doorIn,
} from '../parts/index.mjs'
import { hipCap } from '../parts/tinker.mjs'

const P = PALETTE
const foot = mix(P.foot, 3), body = mix(P.body, 5), ragged = mix(P.ragged, 7), walk = mix(P.walk, 11), court = mix(P.court, 13)
const hash = (x, z) => ((x * 7349 + z * 9151) ^ (x * z * 31)) & 15
const log = (c, x, y, z, id, axis = 'y') => c.set(x, y, z, id, { axis })
const lantern = (c, x, y, z) => c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
const banner = (c, x, y, z, id, facing) => c.set(x, y, z, id, { facing })
const fence = (c, x, y, z, id, dirs) => c.set(x, y, z, id, { north: 'false', south: 'false', east: 'false', west: 'false', waterlogged: 'false', ...Object.fromEntries(dirs.map(d => [d, 'true'])) })
const ring = (x0, z0, x1, z1) => {
  const out = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) if (x === x0 || x === x1 || z === z0 || z === z1) out.push([x, z])
  return out
}
const STAGE = n => c => { c.stage = n }

// --- the curtain's cross-section (bottom up): a dark foot 11 thick, battered in to 9, then to 7 from y 5 ---
const SECTION = [
  [0, 2, 12, foot], [1, 2, 12, foot],
  [2, 3, 11, foot, { front: P.footStairs, back: P.footStairs }],
  [3, 3, 11, body],
  [4, 4, 10, body, { front: P.bodyStairs, back: P.bodyStairs }],
  [5, 4, 10, body], [6, 4, 10, body], [7, 4, 10, body], [8, 4, 10, body],
]
const LANDINGS = new Set([34, 35, 36, 60, 61, 62]) // where the horse stairs come up through the inner parapet
// buttresses on the runs, two to a run, mirrored about the gate (round 2: the runs as deep as the towers); the village
// face gets them too, but for 35 and 61, where the horse stairs' landings stand
const BUTTRESSES = [13, 17, 31, 35, 61, 65, 79, 83]
const PILASTERS = BUTTRESSES.filter(x => !LANDINGS.has(x))
const dark = (y) => (y % 2 ? P.quoin : 'deepslate_bricks')
const WALK_LAMPS = [12, 18, 30, 38, 58, 66, 78, 84] // lantern posts on the inner parapet, 6 to 8 apart with the towers'

function curtain(c, x0, x1) {
  for (let x = x0; x <= x1; x++) {
    const end = x <= 1 || x >= 95
    // the wall goes on: its ends are broken off, stepped down and mossed over
    const keep = y => !end || y <= (x === 0 || x === 96 ? 4 : 6)
    // (round 2: the broken end keeps no pale stair step of its own, only the dark foot's, so it reads as fallen courses)
    wallSection(c, x, end ? SECTION.map(r => (r[0] >= 3 ? [r[0], r[1], r[2], ragged] : r)) : SECTION, keep)
    if (end) continue
    // spouts under the village edge, every sixth cell; ivy down the field face here and there
    if (x % 6 === 3 && ![x - 1, x, x + 1].some(v => LANDINGS.has(v))) stair(c, x, 8, 3, P.bodyStairs, 'south', 'top')
    // depth: dark buttresses on the field face and pilasters on the village face, from the batter to the walk, and a
    // second ledge (a drip course at y 7) on both faces between them
    if (BUTTRESSES.includes(x)) for (let y = 4; y <= 8; y++) c.set(x, y, 11, dark(y))
    else stair(c, x, 7, 11, P.bodyStairs, 'north', 'top')
    if (PILASTERS.includes(x)) for (let y = 4; y <= 8; y++) c.set(x, y, 3, dark(y))
    // (not beside a tower, where it would sit under the cap's eave as a slot, nor into the landings and galleries)
    else if (!LANDINGS.has(x) && !c.get(x, 7, 3) && !TOWERS.some(t => x === t - 1 || x === t + 9)) stair(c, x, 7, 3, P.bodyStairs, 'south', 'top')
    if (!BUTTRESSES.includes(x) && hash(x, 11) < 4) for (let y = 5; y <= 6 - (hash(x, 3) & 1); y++) c.set(x, y, 11, 'vine', { north: 'true', south: 'false', east: 'false', west: 'false', up: 'false' })
  }
}

// the walk on top of a run (stage IV): paving, a plain inner parapet with lantern posts, and the field parapet carried
// out one block on a corbel course (on the buttresses' heads where they rise), a merlon every other cell
function walkTop(c, x0, x1) {
  const out = CURTAIN.outer + 1
  for (let x = Math.max(x0, 2); x <= Math.min(x1, 94); x++) {
    for (let z = CURTAIN.inner; z <= CURTAIN.outer; z++) c.set(x, CURTAIN.walkY, z, walk)
    if (!LANDINGS.has(x)) c.set(x, 10, CURTAIN.inner, body)
    if (BUTTRESSES.includes(x)) c.set(x, CURTAIN.walkY, out, P.key)
    else stair(c, x, CURTAIN.walkY, out, P.bodyStairs, 'north', 'top')
    c.set(x, 10, out, body)
    if (WALK_LAMPS.includes(x)) { fence(c, x, 11, CURTAIN.inner, P.rail, []); c.set(x, 12, CURTAIN.inner, 'lantern', { hanging: 'false', waterlogged: 'false' }) }
  }
  const cells = []
  for (let x = Math.max(x0, 2); x <= Math.min(x1, 94); x++) cells.push([x, out])
  crenels(c, cells, CURTAIN.merlonY, CURTAIN.merlonY, body, (i, x) => x % 2 === 0)
}

// --- a watchtower's base and guard room (its run's stage) ---
function towerBase(c, t) {
  const { z0, z1 } = TOWER
  const corner = (x, z) => (x === t || x === t + 8) && (z === z0 || z === z1)
  for (let x = t - 1; x <= t + 9; x++) for (let z = 2; z <= 12; z++) {
    if (!c.get(x, 0, z)) c.set(x, 0, z, foot)
    const rim = x === t - 1 || x === t + 9 || z === 2 || z === 12
    if (rim && !c.get(x, 1, z)) {
      // the plinth's step: a dark stair rising to the tower, its corners turned
      const facing = z === 2 ? 'south' : z === 12 ? 'north' : x === t - 1 ? 'east' : 'west'
      stair(c, x, 1, z, P.footStairs, facing)
    }
  }
  for (let x = t; x <= t + 8; x++) for (let z = z0; z <= z1; z++) for (let y = 0; y <= TOWER.solidTop; y++) {
    const shell = x === t || x === t + 8 || z === z0 || z === z1
    c.set(x, y, z, corner(x, z) && y >= 3 ? (y % 2 ? P.quoin : 'deepslate_bricks') : y <= 2 && shell ? foot : body)
  }
  // where the curtain meets the tower, its batter's top step is a full block (a cheek against the tower's corner), so no
  // slot opens between the step and the cap's eave twelve blocks over it (holes.py "meet"; kit.seal_slots would fill it)
  for (const x of [t - 1, t + 9]) for (const z of [z0, z1]) if (c.get(x, 4, z)) c.set(x, 4, z, body)
  // the walk's floor through the tower, and a drip course on both faces at its level
  for (let x = t; x <= t + 8; x++) for (let z = z0; z <= z1; z++) c.set(x, TOWER.floorY, z, x === t || x === t + 8 || z === z0 || z === z1 ? body : walk)
  for (let x = t; x <= t + 8; x++) { stair(c, x, TOWER.floorY, 2, P.bodyStairs, 'south', 'top'); stair(c, x, TOWER.floorY, 12, P.bodyStairs, 'north', 'top') }
  // round 2: on the two end towers the drip course is carried round the open outer side, so it reads as a band, not a
  // stub stuck on the village and field faces
  const side = t === TOWERS[0] ? t - 1 : t === TOWERS[3] ? t + 9 : null
  if (side !== null) for (let z = 2; z <= 12; z++) stair(c, side, TOWER.floorY, z, P.bodyStairs, side < t ? 'east' : 'west', 'top')
  // guard room walls, the walk's arches through the inner sides (never out of the wall's two ends)
  for (let y = 10; y <= TOWER.roomTop; y++) for (const [x, z] of ring(t, z0, t + 8, z1)) c.set(x, y, z, corner(x, z) ? (y % 2 ? P.quoin : 'deepslate_bricks') : body)
  for (const x of [t, t + 8]) {
    if ((x === t && t === TOWERS[0]) || (x === t + 8 && t === TOWERS[3])) { c.set(x, 11, 7, P.bars, { north: 'true', south: 'true', east: 'false', west: 'false', waterlogged: 'false' }); continue }
    // the walk's door through the guard room, under a dark lintel
    doorIn(c, x, 10, 7, P.door, x === t ? 'west' : 'east')
    c.set(x, 12, 7, P.key)
  }
  // windows: a three-light glazed window to the village, three tall barred loops to the field, each on a stair sill
  // (the crown's corbel course over them is their hood)
  for (const x of [t + 3, t + 4, t + 5]) {
    for (const y of [11, 12]) c.set(x, y, z0, P.glass, { east: 'true', west: 'true', north: 'false', south: 'false', waterlogged: 'false' })
    stair(c, x, 10, z0 - 1, P.bodyStairs, 'south')
  }
  for (const x of [t + 2, t + 4, t + 6]) {
    for (const y of [11, 12]) c.set(x, y, z1, P.bars, { east: 'false', west: 'false', north: 'false', south: 'false', waterlogged: 'false' })
    stair(c, x, 10, z1 + 1, P.bodyStairs, 'north')
  }
  // the tower's colours on its village face, and the guard room's grindstone
  banner(c, t + 4, 7, z0 - 1, P.towerBanner, 'north')
  c.set(t + 2, 10, z0 + 1, 'grindstone', { face: 'floor', facing: 'south' })
}

// --- a watchtower's crown (stage IV): the floor, the open lookout on piers, the cornice, the spruce hip cap ---
function towerCrown(c, t) {
  const { z0, z1 } = TOWER
  for (let x = t; x <= t + 8; x++) for (let z = z0; z <= z1; z++) c.set(x, TOWER.crownFloor, z, x === t || x === t + 8 || z === z0 || z === z1 ? 'deepslate_bricks' : walk)
  c.clear(t + 6, TOWER.crownFloor, z0 + 1) // the ladder's hatchway
  for (let y = 10; y <= TOWER.crownFloor; y++) c.set(t + 6, y, z0 + 1, 'ladder', { facing: 'south', waterlogged: 'false' })
  // the guard room's corner under the field loops, furnished once it has its roof and the walk is manned (an unroofed
  // room with seats and no way up is a CHECK): an empty barrel, a slab table with a candle on it and a chair at it
  c.set(t + 1, 10, z1 - 1, 'barrel', { facing: 'up', open: 'false' })
  slab(c, t + 6, 10, z1 - 1, P.roof.slab, 'top'); slab(c, t + 7, 10, z1 - 1, P.roof.slab, 'top')
  c.set(t + 7, 11, z1 - 1, 'candle', { candles: '1', lit: 'true', waterlogged: 'false' })
  stair(c, t + 6, 10, z1 - 2, P.roof.stairs, 'north')
  c.set(t + 4, TOWER.crownFloor, 7, 'hay_block', { axis: 'y' })
  c.set(t + 4, 14, 7, 'campfire', { facing: 'south', lit: 'true', signal_fire: 'true', waterlogged: 'false' })
  lantern(c, t + 4, 12, 9)
  const piers = new Set([`${t},${z0}`, `${t + 8},${z0}`, `${t},${z1}`, `${t + 8},${z1}`, `${t + 4},${z0}`, `${t + 4},${z1}`, `${t},7`, `${t + 8},7`])
  for (const [x, z] of ring(t, z0, t + 8, z1)) {
    if (piers.has(`${x},${z}`)) { for (let y = 14; y <= TOWER.crownTop; y++) c.set(x, y, z, P.pier); continue }
    // a breast wall to lean on, and over it spruce shutters propped open on every side (the fire seen through them)
    c.set(x, 14, z, body)
    const out = z === z0 ? 'north' : z === z1 ? 'south' : x === t ? 'west' : 'east'
    c.set(x, 15, z, P.shutter, { facing: { north: 'south', south: 'north', west: 'east', east: 'west' }[out], half: 'top', open: 'true', powered: 'false', waterlogged: 'false' })
  }
  for (const [x, z] of ring(t, z0, t + 8, z1)) c.set(x, TOWER.cornice, z, P.cornice)
  // depth: a corbel course under the crown on both long faces, and timber brackets under the eave at each pier
  for (let x = t; x <= t + 8; x++) { stair(c, x, TOWER.crownFloor, z0 - 1, P.bodyStairs, 'south', 'top'); stair(c, x, TOWER.crownFloor, z1 + 1, P.bodyStairs, 'north', 'top') }
  for (const x of [t, t + 4, t + 8]) { stair(c, x, TOWER.crownTop, z0 - 1, P.roof.stairs, 'south', 'top'); stair(c, x, TOWER.crownTop, z1 + 1, P.roof.stairs, 'north', 'top') }
  hipCap(c, t + 4, 7, 5, TOWER.capY, P.roof, { apex: ['lightning_rod', { facing: 'up', powered: 'false', waterlogged: 'false' }] })
}

function gate(c) {
  const G = GATEHOUSE, B = BARBICAN
  const quoinAt = (x, z, x0, x1, z0, z1) => (x === x0 || x === x1) && (z === z0 || z === z1)
  // --- Pass 1: block-out: the gatehouse, solid to the walk ---
  for (let x = G.x0; x <= G.x1; x++) for (let z = G.z0; z <= G.z1; z++) for (let y = 0; y <= G.top; y++)
    c.set(x, y, z, quoinAt(x, z, G.x0, G.x1, G.z0, G.z1) && y >= 2 ? (y % 2 ? P.quoin : 'deepslate_bricks') : y <= 2 ? foot : body)
  // the barbican: three thick walls round a court behind the gate
  for (let x = B.x0; x <= B.x1; x++) for (let z = B.z0; z <= B.z1; z++) {
    const wall = x < B.x0 + B.wall || x > B.x1 - B.wall || z > B.z1 - B.wall
    if (!wall) { c.set(x, 0, z, court); continue }
    for (let y = 0; y <= B.top; y++) c.set(x, y, z, quoinAt(x, z, B.x0, B.x1, -1, B.z1) && y >= 2 ? (y % 2 ? P.quoin : 'deepslate_bricks') : y <= 1 ? foot : body)
  }
  // the apron before the gate
  for (let x = PASSAGE.x0; x <= PASSAGE.x1; x++) for (let z = 0; z < G.z0; z++) c.set(x, 0, z, walk)
  // --- Pass 2: openings: the passage through both, the Great Gate, the outer gate, the portcullis teeth ---
  const arch = { stairs: 'deepslate_brick_stairs', voussoir: 'deepslate_bricks', key: P.key }
  archway(c, PASSAGE.x0, PASSAGE.x1, G.z0, G.z1, PASSAGE.y0, PASSAGE.h, arch)
  archway(c, PASSAGE.x0, PASSAGE.x1, B.z1 - B.wall + 1, B.z1, PASSAGE.y0, PASSAGE.h, arch)
  for (let x = PASSAGE.x0; x <= PASSAGE.x1; x++) for (let z = G.z0; z <= B.z1; z++) c.set(x, 0, z, z >= B.z0 && z <= B.z1 - B.wall ? court : 'deepslate_tiles')
  // the Great Gate: a leaf at each end of the passage and the outer gate in the barbican, each two gates high under
  // the portcullis's grille, which fills the arch head (so the passage between is shut in when the gate is)
  for (const z of [G.z0 + 1, G.z1 - 1, B.z1 - 1]) {
    const gateId = z === B.z1 - 1 ? P.outerGate : P.innerGate
    fenceGates(c, [46, 47, 48, 49, 50], 1, z, gateId)
    fenceGates(c, [46, 47, 48, 49, 50], 2, z, gateId)
    portcullis(c, [46, 47, 48, 49, 50], 3, z, P.bars)
    portcullis(c, [47, 48, 49], 4, z, P.bars)
  }
  for (const z of [6, 8, 10, 12, 23]) lantern(c, 48, 4, z) // the passage, lit end to end
  // --- the horse stairs up the village face, either side of the gate ---
  for (const side of [STAIRS.west, STAIRS.east])
    horseStair(c, { x0: side.x0, z0: STAIRS.z0, width: STAIRS.width, steps: STAIRS.steps, rise: side.rise, landing: 2, zTo: 3 }, P.bodyStairs, body, walk)
  // lantern posts up both flights (on the outer edge, two steps up and six up) and on each landing
  for (const [x, y] of [[43, 3], [39, 7], [53, 3], [57, 7], [34, 10], [62, 10]]) { fence(c, x, y, STAIRS.z0, P.rail, []); c.set(x, y + 1, STAIRS.z0, 'lantern', { hanging: 'false', waterlogged: 'false' }) }
  // the barbican yard: a lantern post in each corner of the court
  for (const [x, z] of [[42, 15], [54, 15], [42, 22], [54, 22]]) { fence(c, x, 1, z, P.rail, []); c.set(x, 2, z, 'lantern', { hanging: 'false', waterlogged: 'false' }) }
  // from each landing a gallery back along the wall's foot to the gatehouse top, so the gate stands climbable alone
  for (const x of [36, 37, 38, 58, 59, 60]) { for (let y = 0; y < G.top; y++) c.set(x, y, 3, y <= 1 ? foot : body); c.set(x, G.top, 3, walk) }
  // --- the gatehouse top: parapets round its rim (open where the walk comes in), merlons to the field ---
  const L = PAVILION.lower
  for (let x = G.x0; x <= G.x1; x++) for (let z = G.z0; z <= G.z1; z++) {
    const inPav = x >= L.x0 && x <= L.x1 && z >= L.z0 && z <= L.z1
    if (inPav) continue
    c.set(x, G.top, z, walk)
    const rim = x === G.x0 || x === G.x1 || z === G.z0 || z === G.z1
    const way = ((x === G.x0 || x === G.x1) && z >= 5 && z <= 9) || (z === G.z0 && (x <= G.x0 + 1 || x >= G.x1 - 1))
    if (rim && !way) c.set(x, 10, z, body)
  }
  crenels(c, ring(G.x0, G.z1, G.x1, G.z1), 11, 11, body, (i, x) => x % 2 === 0)
  crenels(c, [...ring(B.x0, B.z0, B.x0, B.z1), ...ring(B.x1, B.z0, B.x1, B.z1), ...ring(B.x0 + 1, B.z1, B.x1 - 1, B.z1)], B.top + 1, B.top + 1, body, (i, x, z) => (x + z) % 2 === 0)
  banner(c, 44, 7, G.z0 - 1, P.gateBanner, 'north'); banner(c, 52, 7, G.z0 - 1, P.gateBanner, 'north')
  banner(c, 44, 5, B.z1 + 1, P.gateBanner, 'south'); banner(c, 52, 5, B.z1 + 1, P.gateBanner, 'south')
  // a timber hood over the outer gate, a lantern under it: the one warm point on the field face at night
  for (let x = 45; x <= 51; x++) stair(c, x, B.top, B.z1 + 1, P.roof.stairs, 'north')
  lantern(c, 48, B.top - 1, B.z1 + 1)

  // --- the Gate Pavilion: lower hall ---
  for (let x = L.x0 + 1; x < L.x1; x++) for (let z = L.z0 + 1; z < L.z1; z++) c.set(x, G.top, z, P.boards)
  const lowerPosts = (x, z) => ((z === L.z0 || z === L.z1) && [41, 45, 51, 55].includes(x)) || ((x === L.x0 || x === L.x1) && [5, 9].includes(z))
  for (const [x, z] of ring(L.x0, L.z0, L.x1, L.z1)) {
    c.set(x, L.y0, z, P.pier)
    for (let y = L.y0 + 1; y < L.y1; y++) {
      if (lowerPosts(x, z)) { log(c, x, y, z, P.frame); continue }
      const front = z === L.z0 || z === L.z1
      const glazed = front && ((x >= 47 && x <= 49) || ((x === 43 || x === 53) && y >= 12))
      c.set(x, y, z, glazed ? P.glass : P.boards)
    }
    log(c, x, L.y1, z, P.beam, z === L.z0 || z === L.z1 ? 'x' : 'z')
  }
  for (const x of [L.x0, L.x1]) { c.clear(x, L.y0, 7); doorIn(c, x, L.y0, 7, P.door, x === L.x0 ? 'west' : 'east') } // the walk's doors
  // the floor between the storeys (and the pentice's bed), the pentice with its corners lifted
  for (let x = L.x0; x <= L.x1; x++) for (let z = L.z0; z <= L.z1; z++) c.set(x, 15, z, P.boards)
  pavilionSkirt(c, L.x0 - 1, L.z0 - 1, L.x1 + 1, L.z1 + 1, 15, 2, P.tile, { flare: true })
  // --- upper room ---
  const U = PAVILION.upper
  for (const [x, z] of ring(U.x0, U.z0, U.x1, U.z1)) {
    const post = ((z === U.z0 || z === U.z1) && [42, 45, 48, 51, 54].includes(x)) || ((x === U.x0 || x === U.x1) && z === 8)
    for (let y = U.y0; y < U.y1; y++) {
      if (post) { log(c, x, y, z, P.frame); continue }
      const byCorner = ((x === U.x0 + 1 || x === U.x1 - 1) && (z === U.z0 || z === U.z1)) || ((z === U.z0 + 1 || z === U.z1 - 1) && (x === U.x0 || x === U.x1))
      c.set(x, y, z, y === U.y0 || byCorner ? P.boards : P.glass)
    }
    log(c, x, U.y1, z, P.beam, z === U.z0 || z === U.z1 ? 'x' : 'z')
  }
  // the bracket course: the upper wall plate carried out one block on every side under the hip's eave, so the eave rests on
  // timber over the pentice instead of leaving a slot between the two roofs (holes.py "meet")
  for (const [x, z] of ring(L.x0, L.z0, L.x1, L.z1)) log(c, x, U.y1, z, P.beam, z === L.z0 || z === L.z1 ? 'x' : 'z')
  const ridge = ridgedHip(c, L.x0, L.z0, L.x1, L.z1, PAVILION.hipY, P.tile, { flare: true })
  // the ridge: a full course (a slab ridge left the horns standing on half a block, floating), its horns on the ends
  for (let x = 45; x <= 51; x++) c.set(x, ridge, 8, P.tile.full)
  stair(c, 45, ridge + 1, 8, P.tile.stairs, 'east'); stair(c, 51, ridge + 1, 8, P.tile.stairs, 'west')
  banner(c, 45, 18, U.z0 - 1, P.pavilionBanner, 'north'); banner(c, 51, 18, U.z0 - 1, P.pavilionBanner, 'north')
  // --- interior: the gate-keeper's hall: the captain's table with benches and chairs and a lantern on it, the portcullis
  // windlass by the village wall over the passage, stores in the corners, an anvil, the ladder up, lanterns ---
  for (let x = 46; x <= 50; x++) {
    slab(c, x, 10, 10, P.roof.slab, 'top')
    stair(c, x, 10, 11, P.roof.stairs, 'south')
  }
  for (const x of [46, 48, 50]) stair(c, x, 10, 9, P.roof.stairs, 'north')
  c.set(48, 11, 10, 'lantern', { hanging: 'false', waterlogged: 'false' })
  c.set(46, 11, 10, 'candle', { candles: '3', lit: 'true', waterlogged: 'false' })
  fence(c, 46, 10, 5, P.rail, []); fence(c, 50, 10, 5, P.rail, [])
  for (let x = 47; x <= 49; x++) log(c, x, 10, 5, P.beam, 'x')
  for (const [x, z] of [[42, 5], [42, 11], [54, 11]]) c.set(x, 10, z, 'barrel', { facing: 'up', open: 'false' })
  c.set(42, 11, 5, 'barrel', { facing: 'up', open: 'false' })
  c.set(54, 10, 5, 'anvil', { facing: 'east' })
  lantern(c, 48, 14, 6); lantern(c, 48, 14, 10)
  for (let y = 10; y <= 14; y++) log(c, 43, y, 11, P.frame)
  for (let y = 10; y <= 15; y++) c.set(43, y, 10, 'ladder', { facing: 'north', waterlogged: 'false' })
  lantern(c, 45, 14, 8); lantern(c, 51, 14, 8)
  c.set(48, 16, 8, 'cartography_table')
  c.set(48, 17, 8, 'lantern', { hanging: 'false', waterlogged: 'false' })
  for (const [x, z] of [[L.x0 - 1, L.z0 - 1], [L.x1 + 1, L.z0 - 1], [L.x0 - 1, L.z1 + 1], [L.x1 + 1, L.z1 + 1]]) lantern(c, x, 14, z)
}

let WHOLE = null
function whole() {
  if (WHOLE) return WHOLE
  const c = staged(new Canvas(SIZE))
  const west = ([a, b]) => b < GATEHOUSE.x0, east = ([a]) => a > GATEHOUSE.x1
  // === Stage I: the Great Gate ===
  STAGE(STAGES.gate)(c); gate(c)
  // === Stage II: the West Run (towers at 2 and 20 to their guard rooms, the curtain to the walk) ===
  STAGE(STAGES.west)(c)
  for (const r of RUNS.filter(west)) curtain(c, ...r)
  for (const t of TOWERS.filter(t => t < 48)) towerBase(c, t)
  // === Stage III: the East Run ===
  STAGE(STAGES.east)(c)
  for (const r of RUNS.filter(east)) curtain(c, ...r)
  for (const t of TOWERS.filter(t => t > 48)) towerBase(c, t)
  // === Stage IV: the Walk and the Towers' Crowns ===
  STAGE(STAGES.walk)(c)
  for (const r of RUNS) walkTop(c, ...r)
  for (const t of TOWERS) towerCrown(c, t)
  WHOLE = c
  return c
}

export function draw(stage = 4) { return snapshot(whole(), stage, Canvas) }

// The brief's checks, run on every stage: each only adds, everything joins the ground; on the whole, the tests' numbers.
export function verify() {
  const seen = new Map()
  const counts = []
  for (let n = 1; n <= 4; n++) {
    const c = draw(n)
    for (const [k, v] of seen) if (!c.cells.has(k)) throw new Error(`stage ${n} takes away ${v.id} at ${k}`)
    for (const [k, v] of seen) if (c.cells.get(k).id !== v.id || JSON.stringify(c.cells.get(k).props) !== JSON.stringify(v.props)) throw new Error(`stage ${n} replaces ${v.id} at ${k} with ${c.cells.get(k).id}`)
    const reach = new Set([...c.cells.keys()].filter(k => k.split(',')[1] === '0'))
    const todo = [...reach]
    while (todo.length) {
      const [x, y, z] = todo.pop().split(',').map(Number)
      for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) for (let dz = -1; dz <= 1; dz++) {
        const k = `${x + dx},${y + dy},${z + dz}`
        if (c.cells.has(k) && !reach.has(k)) { reach.add(k); todo.push(k) }
      }
    }
    const loose = [...c.cells.keys()].filter(k => !reach.has(k))
    if (loose.length) throw new Error(`stage ${n}: ${loose.length} blocks not joined to the ground, e.g. ${loose.slice(0, 3)}`)
    counts.push(c.cells.size - seen.size)
    for (const [k, v] of c.cells) seen.set(k, v)
  }
  const c = draw(4)
  const at = (x, y, z) => c.get(x, y, z)?.id?.replace('minecraft:', '')
  const cells = [...c.cells].map(([k, v]) => [k.split(',').map(Number), v.id.replace('minecraft:', '')])
  const n = (...ids) => cells.filter(([, id]) => ids.includes(id)).length
  const fail = m => { throw new Error(m) }
  const total = c.cells.size
  if (total < 11070 || total > 13530) fail(`block count ${total}, not 11,070 to 13,530`)
  const xs = cells.map(([[x]]) => x), ys = cells.map(([[, y]]) => y), zs = cells.map(([[, , z]]) => z)
  if (Math.min(...xs) !== 0 || Math.max(...xs) !== 96 || Math.min(...ys) !== 0 || Math.min(...zs) !== 0) fail('footprint not 97 long from z 0, y 0')
  const gates = cells.filter(([, id]) => id.endsWith('_fence_gate'))
  if (gates.length < 4 || gates.some(([[x]]) => x < 46 || x > 50)) fail('fence gates')
  if (gates.filter(([, id]) => /^(spruce|oak|dark_oak)_fence_gate$/.test(id)).length < 2) fail('wooden gates')
  const gx = gates.map(([[x]]) => x); if (Math.abs((Math.min(...gx) + Math.max(...gx)) / 2 - 48) > 1) fail('gate middle')
  if (n('iron_bars') < 6 || n('campfire') < 4 || n('grindstone') < 4) fail('bars, campfires or grindstones')
  if (n('blue_wall_banner', 'red_wall_banner', 'white_wall_banner') < 4) fail('banners')
  if (n('deepslate_bricks', 'cracked_deepslate_bricks', 'deepslate_brick_stairs') < 100) fail('deepslate')
  if (n('mossy_stone_bricks', 'cracked_stone_bricks', 'mossy_cobblestone') < 50) fail('aged stone')
  if (n('spruce_stairs') < 100) fail('spruce stairs')
  if (cells.some(([, id]) => /calcite|_bed$/.test(id))) fail('a bed or calcite')
  // every door whole, with feet and head room clear on both sides (qaWonderDoorways)
  for (const [k, v] of c.cells) {
    if (!/_door$/.test(v.id) || v.props.half !== 'lower') continue
    const [x, y, z] = k.split(',').map(Number)
    if (!/_door$/.test(c.get(x, y + 1, z)?.id ?? '')) fail(`door at ${k} not whole`)
    const [dx, dz] = { north: [0, 1], south: [0, 1], east: [1, 0], west: [1, 0] }[v.props.facing]
    for (const sgn of [1, -1]) for (const yy of [y, y + 1]) if (at(x + dx * sgn, yy, z + dz * sgn)) fail(`door at ${k} blocked`)
  }
  const full = id => id && !/stairs|slab|fence|bars|pane|ladder|banner|lantern|grindstone|vine|campfire/.test(id)
  for (const t of TOWERS) {
    for (let x = t + 1; x <= t + 7; x++) for (let z = 4; z <= 10; z++) for (let y = 0; y <= 8; y++) if (!full(at(x, y, z))) fail(`tower ${t} hollow at ${x},${y},${z}`)
    for (const [x, z] of ring(t, 3, t + 8, 11)) for (let y = 0; y <= 2; y++) if (!full(at(x, y, z))) fail(`tower ${t} ring at ${x},${y},${z}`)
    for (let x = t - 1; x <= t + 9; x++) for (let z = 2; z <= 12; z++) if (!at(x, 0, z)) fail(`tower ${t} foot at ${x},${z}`)
    const stoneY = Math.max(...cells.filter(([[x], id]) => x >= t && x <= t + 8 && /stone|cobble/.test(id)).map(([[, y]]) => y))
    if (stoneY < 16 || stoneY > 18) fail(`tower ${t} stone top ${stoneY}`)
    for (const x of [t, t + 8]) if (!cells.some(([[xx, y], id]) => xx === x && y === stoneY && /stone|cobble/.test(id))) fail(`tower ${t} top misses x ${x}`)
    if (cells.filter(([[x, y], id]) => x >= t && x <= t + 8 && y > stoneY && id === 'spruce_stairs').length < 20) fail(`tower ${t} roof`)
  }
  const spans = []
  for (const x of [31, 32, 33, 34, 35, 36, 79, 80, 81, 82, 83, 84]) {
    const top = Math.max(...cells.filter(([[xx]]) => xx === x).map(([[, y]]) => y))
    if (top < 10 || top > 12) fail(`run column ${x} tops at ${top}`)
    const z6 = cells.filter(([[xx, y]]) => xx === x && y === 6).map(([[, , z]]) => z)
    spans.push(Math.max(...z6) - Math.min(...z6) + 1)
  }
  if (Math.min(...spans) !== 7 || Math.min(...spans.slice(0, 6)) !== 7 || Math.min(...spans.slice(6)) !== 7 || spans.some(v => v < 7)) fail(`run spans ${spans}`)
  // the sentry posts: level, on one line, west to east; each post and its walk spot holds a golem on solid floor
  const mid = POSTS.reduce((a, [x]) => a + x, 0) / 4
  for (const [x, y, z] of POSTS) {
    const spot = x + 2 * Math.sign(mid - x)
    for (let xx = Math.min(x, spot) - 1; xx <= Math.max(x, spot) + 1; xx++) for (let zz = z - 1; zz <= z + 1; zz++) {
      if (!full(at(xx, y - 1, zz))) fail(`post ${x}: no floor at ${xx},${zz}`)
      for (let yy = y; yy <= y + 2; yy++) if (at(xx, yy, zz)) fail(`post ${x}: ${at(xx, yy, zz)} in the golem's room at ${xx},${yy},${zz}`)
    }
  }
  return { counts, total }
}

export default function build(api) {
  const { counts, total } = verify()
  console.error(`stage adds: ${counts.join(', ')}; whole ${total}`)
  const stage = Number(process.env.STAGE ?? 4)
  const c = draw(stage)
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const name = `great_wall_s${stage}` + (cut !== undefined ? `_cut${cut}` : '')
  return { structure: canvas.toStructure(api), metadata: { name } }
}
