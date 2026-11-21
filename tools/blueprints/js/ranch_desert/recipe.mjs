// Ranch, desert look (49.10e), the recipe: assembles plan.mjs from the parts library (../parts) in passes (block-out and
// structure, roofs, openings, depth and detail, interior, ground) for LEVEL = 1 or 2, Level II all of Level I in its
// cells and then the bunk house, its loggia, the fodder rick and the corral. Writes furniture.json. Build both: build.sh.
// CUT=<y>: a cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, TOWER, RANGES, GATEWAY, YARD, BUNK, CORRAL, OPENINGS, VIGAS, FURNITURE } from './plan.mjs'
import {
  Canvas, mix, slab, stair, plinth, floor, wallPanel, window, door, quoins, furnitureParts, doubleChest, chimney,
  archBay, vigas, merlons, stallFront,
} from '../parts/index.mjs'
import { pierRails, stoneTrough, cornice, slottedCatcher } from '../parts/ranch_desert_yard.mjs'
import { datePalm } from '../parts/hot_townhouse.mjs' // direct: index.mjs exports two datePalms

const here = path.dirname(fileURLToPath(import.meta.url))

const pieces = {
  ...furnitureParts,
  doubleChest: (c, f) => doubleChest(c, f.pos, f.facing, f.along),
  // the shelf with a potted cactus on it (a plant, not crockery)
  shelf: (c, f) => {
    furnitureParts.shelf(c, f)
    if (f.plant) { const [x, y, z] = f.pos; c.free([x, y + 1, z]); c.set(x, y + 1, z, f.plant) }
  },
}

const lantern = (c, x, y, z, hanging) => c.set(x, y, z, 'lantern', { hanging: String(hanging), waterlogged: 'false' })

// the three courses of a desert wall on a rectangle's ring (or one side): coursed sandstone to the course, the cut
// course, mud brick above to `top`
function coursedWall(c, a, b, course, top, ashlar, mud, P) {
  wallPanel(c, a, b, 1, course - 1, ashlar)
  wallPanel(c, a, b, course, course, P.course)
  if (top > course) wallPanel(c, a, b, course + 1, top, mud)
}

// a flat roof's deck over x0..x1, z0..z1 at y: `deck` inside, the wall line in `rim` (so the walls read as one mud
// face up to the coping, not banded); `skip(x, z)` leaves a cell to the caller
function deck(c, x0, z0, x1, z1, y, deckId, rim, skip = () => false) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (skip(x, z)) continue
    c.set(x, y, z, x === x0 || x === x1 || z === z0 || z === z1 ? rim : deckId)
  }
}

// a corner pinnacle: the parapet's corner raised a course in `body` with a `cap` slab on it
function pinnacle(c, x, y, z, body, cap) { c.set(x, y, z, body); slab(c, x, y + 1, z, cap, 'bottom') }

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = PALETTE, T = TOWER, R = RANGES
  const ashlar = mix(P.ashlar, 5), mud = mix(P.mud, 6)
  const { x0, x1, z0, z1 } = T.walls
  const A = T.aisle

  // --- Pass 1: block-out and structure -----------------------------------------------------------------------------
  // the plinth under the whole stable, the aisle's paving, sand bedding in the stalls
  plinth(c, R.west.x0, R.z0, R.east.x1, R.z1, P.plinth)
  for (const side of [R.west, R.east]) for (const s of R.stalls) floor(c, side.inner[0], s.z0, side.inner[1], s.z1, 0, P.stallFloor)
  // the stall ranges: outer, front and back walls up to the deck
  for (const side of [R.west, R.east]) {
    const o = side.outer
    coursedWall(c, [o, R.z0], [o, R.z1], T.course, R.wallTop, ashlar, mud, P)
    for (const z of [R.z0, R.z1]) coursedWall(c, [side.x0, z], [side.x1, z], T.course, R.wallTop, ashlar, mud, P)
    quoins(c, [[o, R.z0], [o, R.z1]], 1, T.course - 1, P.quoin)
  }
  // the tower: its front and back walls the full height; its sides (the stall fronts below) from the course up
  for (const z of [z0, z1]) coursedWall(c, [x0, z], [x1, z], T.course, T.wallTop, ashlar, mud, P)
  for (const x of [x0, x1]) {
    for (const z of T.piers) for (let y = 1; y < T.course; y++) c.set(x, y, z, ashlar)
    wallPanel(c, [x, z0], [x, z1], T.course, T.course, P.course)
    wallPanel(c, [x, z0], [x, z1], T.course + 1, T.wallTop, mud)
  }
  floor(c, A.x0, A.z0, A.x1, A.z1, T.loftY, P.loftFloor) // the hay loft over the aisle

  // --- Pass 2: the roofs: the decks, the parapets, the merlons, the windcatcher ------------------------------------
  for (const side of [R.west, R.east]) {
    deck(c, side.x0, R.z0, side.x1, R.z1, R.deckY, P.deck, mud, (x, z) => x !== side.outer && (x === 3 || x === 9) && z > R.z0 && z < R.z1)
    for (let z = R.z0 + 1; z < R.z1; z++) { const x = side === R.west ? 3 : 9; c.set(x, R.deckY, z, P.deck) } // against the tower
    // the parapet on the outer wall and the two ends (the tower's wall is the fourth side)
    const ring = []
    for (let z = R.z0; z <= R.z1; z++) ring.push([side.outer, z])
    for (let x = side.x0; x <= side.x1; x++) if (x !== side.outer) ring.push([x, R.z0], [x, R.z1])
    for (const [x, z] of ring) { c.set(x, R.parapetY, z, P.parapet); slab(c, x, R.parapetY + 1, z, P.coping, 'bottom') }
    for (const z of [R.z0, R.z1]) pinnacle(c, side.outer, R.parapetY + 1, z, P.parapet, P.pinnacle) // the outer corners
  }
  const K = T.catcher
  deck(c, x0, z0, x1, z1, T.deckY, P.deck, mud)
  const inCatcher = (x, z) => x >= K.x0 && x <= K.x1 && z >= K.z0 && z <= K.z1
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++)
    if ((x === x0 || x === x1 || z === z0 || z === z1) && !inCatcher(x, z)) c.set(x, T.parapetY, z, P.parapet)
  merlons(c, { x0, z0, x1, z1, y: T.merlonY }, P.parapet, P.crenel, inCatcher)
  for (const x of [x0, x1]) for (const z of [z0, z1]) slab(c, x, T.pinnacleY, z, P.pinnacle, 'bottom') // corner pinnacles
  // the windcatcher: the tower's full width over the loft's middle, two tall dark-oak grilles between three sandstone piers on
  // every face, a projecting cap course; its throat open down into the loft (the loft lamp hangs in it)
  slottedCatcher(c, K, { body: mix(P.catcher.body, 9), pier: P.catcher.corner, cap: P.catcher.cap, capTop: P.catcher.capTop, cornice: P.hood, grille: `${P.wood}_fence` },
    (x, y, z) => x === K.throat[0] && z === K.throat[1] && y <= K.y0 + 1)

  // --- Pass 3: openings ----------------------------------------------------------------------------------------------
  const bd = OPENINGS.barnDoor, hd = OPENINGS.hayDoor
  door(c, bd.at, bd.facing, bd.out, { wood: P.door, step: bd.step, stepStair: P.step })
  door(c, hd.at, hd.facing, hd.out, { wood: P.door })
  for (const cells of OPENINGS.stallBars) for (const [x, y, z] of cells) c.set(x, y, z, P.bars)
  const cl = OPENINGS.clerestory
  for (const x of cl.xs) for (const z of cl.zs) {
    for (const y of cl.ys) c.set(x, y, z, P.glass)
    c.set(x, Math.max(...cl.ys) + 1, z, P.brow)
  }
  const lit = ws => {
    for (const w of ws) {
      window(c, w.cells, 'north', null, { glass: P.glass })
      const top = Math.max(...w.cells.map(p => p[1]))
      for (const [x, y, z] of w.cells) if (y === top && top + 1 !== T.course) c.set(x, top + 1, z, P.brow)
    }
  }
  lit(OPENINGS.windows)
  // the stall fronts off the aisle: a mud-brick half wall with a dark-oak gate, a rail on it, open over it to the loft's
  // course; the partition between each side's two stalls
  stallFront(c, x0, R.stalls, 1, 'east', { board: P.half, gate: `${P.wood}_fence_gate`, rail: `${P.wood}_fence` })
  stallFront(c, x1, R.stalls, 1, 'west', { board: P.half, gate: `${P.wood}_fence_gate`, rail: `${P.wood}_fence` })
  for (const side of [R.west, R.east]) for (const x of side.inner) { c.set(x, 1, R.partition, P.half); c.set(x, 2, R.partition, `${P.wood}_fence`) }
  // the ladder up to the loft, against the front wall, through its hatch
  const ld = OPENINGS.ladder
  for (let y = ld.y0; y <= ld.top; y++) c.set(ld.x, y, ld.z, 'ladder', { facing: ld.facing, waterlogged: 'false' })

  // --- Pass 4: depth and detail ----------------------------------------------------------------------------------------
  // the portal: chiseled jambs a block out either side of the wicket door, a keystone over it, a corbelled hood across
  // the tower's front at the course, lanterns hung under the hood's ends
  const Q = OPENINGS.portal
  for (const x of Q.jambs) { c.set(x, 0, Q.z, P.plinth); for (let y = 1; y <= Q.top; y++) c.set(x, y, Q.z, P.carved) }
  c.set(bd.at[0], Q.top, Q.z, P.carved)
  for (let x = Q.hood[0]; x <= Q.hood[1]; x++) {
    if (x === Q.hood[0] || x === Q.hood[1]) c.set(x, Q.hoodY, Q.z, P.course) // the hood's end blocks, the lamps under them
    else stair(c, x, Q.hoodY, Q.z, P.hood, 'south', 'top')
  }
  for (const [x, y, z] of Q.lamps) lantern(c, x, y, z, true)
  // the hoist over the hay door: a dark-oak hood, the spruce beam out of the wall, a chain and a lantern at its end
  const H = OPENINGS.hoist
  for (let x = H.hood.x0; x <= H.hood.x1; x++) slab(c, x, H.hood.y, H.hood.z, P.shade, 'top')
  for (let z = H.z0; z <= H.z1; z++) c.set(H.x, H.y, z, P.hoist, { axis: 'z' })
  c.set(H.x, H.y - 1, H.z0, 'chain', { axis: 'y', waterlogged: 'false' })
  lantern(c, H.x, H.y - 2, H.z0, true)
  // the smoker's flue up the tower's back, its breast stepped in at the course, smoke over the merlons
  const F = T.flue
  chimney(c, F.x, F.z, 0, F.top, ashlar, { breast: { axis: 'x', top: T.course, stair: P.step } })
  // a proud pilaster on each stall range's outer wall at the partition between its stalls, a sloped stone cap on it
  for (const [x, f] of [[0, 'east'], [12, 'west']]) {
    for (let y = 0; y < T.course; y++) c.set(x, y, R.partition, P.pier)
    stair(c, x, T.course, R.partition, P.step, f)
  }
  // spruce beam ends a block out under every deck
  const V = VIGAS
  vigas(c, 'x', 0, V.ranges.west, V.ranges.y, P.beam)
  vigas(c, 'x', 12, V.ranges.east, V.ranges.y, P.beam)
  vigas(c, 'z', R.z0 - 1, V.ranges.front, V.ranges.y, P.beam)
  vigas(c, 'z', R.z1 + 1, V.ranges.back, V.ranges.y, P.beam)
  vigas(c, 'z', z0 - 1, V.tower.front, V.tower.y, P.beam)
  vigas(c, 'z', z1 + 1, V.tower.back, V.tower.y, P.beam)
  vigas(c, 'x', x0 - 1, V.tower.sides, V.tower.y, P.beam)
  vigas(c, 'x', x1 + 1, V.tower.sides, V.tower.y, P.beam)

  // a proud cornice under every parapet, a block out on the beam ends' heads: the tower's at its deck, the stall ranges'
  // at theirs (each range's run dies into the tower's face)
  cornice(c, { x0, z0, x1, z1, y: T.deckY }, P.hood, (x, z) => x === F.x && z === F.z)
  for (const side of [R.west, R.east]) cornice(c, { x0: side.x0, z0: R.z0, x1: side.x1, z1: R.z1, y: R.deckY }, P.hood, x => x >= x0 && x <= x1)

  // --- Pass 5: interior: the shipped Ranch's furniture, cell for cell -------------------------------------------------
  for (const f of FURNITURE[1]) pieces[f.part](c, f)
  // hay: a bale in each stall's back corner for feed; the loft stacked along both sides, the hatch and the hay door clear
  for (const side of [R.west, R.east]) for (const s of R.stalls)
    c.set(side === R.west ? side.inner[0] : side.inner[1], 1, s.z0 === 4 ? s.z0 : s.z1, P.hay, { axis: 'y' })
  for (const x of [A.x0, A.x1]) for (let z = 6; z <= A.z1; z++) {
    c.set(x, T.loftY + 1, z, P.hay, { axis: 'y' })
    if (z >= 8) c.set(x, T.loftY + 2, z, P.hay, { axis: 'y' })
  }
  c.set(A.x0, T.loftY + 3, 10, P.hay, { axis: 'y' })
  // water jars by the tack wall
  c.set(A.x1, 1, 9, 'decorated_pot', { facing: 'west', cracked: 'false', waterlogged: 'false' })

  // --- Pass 6: ground: the gateway, the yard's walls, the trough, the palm ----------------------------------------------
  const G = GATEWAY
  for (const x of G.piers) {
    c.set(x, 0, G.z, P.plinth)
    for (let y = 1; y < G.bandY; y++) c.set(x, y, G.z, P.pier)
  }
  archBay(c, { axis: 'x', line: G.z, a0: G.piers[0] + 1, a1: G.piers[1] - 1, y: G.archY }, P.arch)
  for (let x = G.piers[0]; x <= G.piers[1]; x++) c.set(x, G.bandY, G.z, P.course)
  for (let x = G.piers[0]; x <= G.piers[1]; x++) {
    if (x === G.piers[0] || x === G.piers[1]) c.set(x, G.merlonY, G.z, P.lampPost) // a stone post under each lamp
    else if ((x - G.piers[0]) % 2 === 0) c.set(x, G.merlonY, G.z, P.parapet)
    else slab(c, x, G.merlonY, G.z, P.crenel, 'bottom')
  }
  c.set(...G.keystone, P.carved)
  c.set(...T.banner, P.banner, { rotation: '8' }) // the ranch's banner flies from the tower's front corner
  for (const [x, y, z] of G.lamps) lantern(c, x, y, z, false)
  // the yard's walls: mud brick two high with a smooth coping, from the gateway's piers round to the stall ranges
  for (const run of YARD.walls) for (let i = 0; i + 1 < run.length; i++) {
    const [[ax, az], [bx, bz]] = [run[i], run[i + 1]]
    for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++) {
      c.set(x, 0, z, mud(x, 0, z)); c.set(x, 1, z, mud(x, 1, z)); slab(c, x, 2, z, P.coping, 'bottom')
    }
  }
  stoneTrough(c, YARD.trough.water, YARD.trough.rim, 0, P.rim)
  for (const [x, y, z] of YARD.jars) c.set(x, y, z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  // its east fronds kept clear of the gateway and the portal
  datePalm(c, YARD.palm, { soil: P.soil, trunk: P.palm.trunk, leaves: P.palm.leaves, height: YARD.palmHeight, skip: x => x >= YARD.palmClear })
  for (const [x, y, z] of YARD.hay) c.set(x, y, z, P.hay, { axis: 'y' })

  if (level >= 2) drawLevel2(c, P, ashlar, mud)
  return c
}

// Level II: the bunk house east of the stable behind its loggia, the fodder rick between them, the corral behind.
function drawLevel2(c, P, ashlar, mud) {
  const B = BUNK, W = B.walls, Rm = B.room
  plinth(c, W.x0, W.z0, W.x1, W.z1, P.plinth)
  floor(c, Rm.x0, Rm.z0, Rm.x1, Rm.z1, 0, P.floor)
  for (const [a, b] of [[[W.x0, W.z0], [W.x1, W.z0]], [[W.x0, W.z1], [W.x1, W.z1]], [[W.x0, W.z0], [W.x0, W.z1]], [[W.x1, W.z0], [W.x1, W.z1]]])
    coursedWall(c, a, b, B.course, B.wallTop, ashlar, mud, P)
  quoins(c, [[W.x0, W.z1], [W.x1, W.z1]], 1, B.course - 1, P.quoin)
  deck(c, W.x0, W.z0, W.x1, W.z1, B.deckY, P.deck, mud)
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    if (x !== W.x0 && x !== W.x1 && z !== W.z0 && z !== W.z1) continue
    c.set(x, B.parapetY, z, P.parapet)
  }
  merlons(c, { x0: W.x0, z0: W.z0, x1: W.x1, z1: W.z1, y: B.merlonY }, P.parapet, P.crenel)
  for (const x of [W.x0, W.x1]) for (const z of [W.z0, W.z1]) pinnacle(c, x, B.parapetY + 1, z, P.parapet, P.pinnacle)
  const od = OPENINGS.bunkDoor
  door(c, od.at, od.facing, od.out, { wood: P.door })
  for (const w of OPENINGS.bunkWindows) {
    for (const p of w.cells) c.set(...p, P.glass)
    const top = Math.max(...w.cells.map(p => p[1]))
    for (const [x, y, z] of w.cells) if (y === top && top + 1 !== B.course && top + 1 < B.deckY) c.set(x, top + 1, z, P.brow)
  }
  // the loggia: four piers on the street line, three openings (the middle one on the door), closed ends, a deck at the
  // course with a mud-brick balustrade, a lantern over the door, a step before each opening
  const L = B.loggia
  c.fill(L.x0, 0, L.z0, L.x1, 0, L.z1, P.paving)
  for (const x of L.piers) for (let y = 1; y < L.deckY; y++) c.set(x, y, L.z0, P.pier)
  for (const x of [L.x0, L.x1]) for (let y = 1; y < L.deckY; y++) c.set(x, y, L.z1, ashlar)
  c.fill(L.x0, L.deckY, L.z0, L.x1, L.deckY, L.z1, P.course)
  for (let x = L.x0; x <= L.x1; x++) c.set(x, L.deckY + 1, L.z0, P.balustrade)
  for (const x of [L.x0, L.x1]) c.set(x, L.deckY + 1, L.z1, P.balustrade)
  for (const x of L.steps) stair(c, x, 0, L.z0 - 1, P.step, 'south')
  lantern(c, ...L.lamp, true)
  cornice(c, { x0: W.x0, z0: W.z0, x1: W.x1, z1: W.z1, y: B.deckY }, P.hood, x => x < W.x0)
  // wooden spouts out over the cornice, rain jars on the deck
  for (const [x, y, z, f] of B.spouts) c.set(x, y, z, `${P.wood}_trapdoor`, { facing: f, half: 'bottom', open: 'false', powered: 'false', waterlogged: 'false' })
  for (const [x, y, z] of B.roofJars) c.set(x, y, z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  // beam ends under the bunk house's deck
  vigas(c, 'x', W.x1 + 1, VIGAS.bunk.east, VIGAS.bunk.y, P.beam)
  vigas(c, 'z', W.z1 + 1, VIGAS.bunk.back, VIGAS.bunk.y, P.beam)
  // the bunk room: the shipped pieces in their cells, hay in the corner
  for (const f of FURNITURE[2]) pieces[f.part](c, f)
  for (const [x, y, z] of [[18, 1, 10], [18, 2, 10], [18, 1, 9]]) c.set(x, y, z, P.hay, { axis: 'y' })
  // the fodder rick in the slot between the stall range and the bunk house, stacked to the stall range's deck, which is
  // carried across it with a mud parapet and its coping (no gap between the two roofs)
  const K = B.rick
  for (let z = K.z0; z <= K.z1; z++) {
    for (let y = 0; y <= K.top; y++) c.set(K.x, y, z, P.hay, { axis: 'z' })
    c.set(K.x, K.deckY, z, P.deck); c.set(K.x, K.deckY + 1, z, P.parapet); slab(c, K.x, K.deckY + 2, z, P.coping, 'bottom')
  }
  for (let y = 0; y <= K.top; y++) c.set(K.x, y, K.z1, mud(K.x, y, K.z1)) // its back closed in mud brick

  // the corral: dark-oak rails between mud-brick piers, the man-gap, the herd's double gate, lamps on the corner piers
  const D = CORRAL
  pierRails(c, { x0: D.x0, z0: D.z0, x1: D.x1, z1: D.z1, y: 0, piers: D.piers, gap: D.manGap, gates: D.gates, gateFacing: 'east' },
    { pier: mud, cap: P.coping, rail: `${P.wood}_fence`, gate: `${P.wood}_fence_gate`, courses: 2 })
  for (const [x, z] of D.lampPiers) { c.set(x, 2, z, P.lampPost); lantern(c, x, 3, z, false) } // a stone post on the pier
  // the run-in shed: open to the corral on four stripped posts, a palm-rib roof a block over them on every side, its
  // open edges carried on a pole beam, hay inside against the back rails
  const S = D.shed
  for (const [x, z] of S.posts) for (let y = 0; y < S.roofY; y++) c.set(x, y, z, P.post, { axis: 'y' })
  for (let x = S.x0; x <= S.x1; x++) for (let z = S.z0; z <= S.z1; z++) {
    if (x === S.x1 || z === S.z0) c.set(x, S.roofY, z, P.post, { axis: x === S.x1 && z !== S.z0 ? 'z' : 'x' })
    else slab(c, x, S.roofY, z, P.shade, 'bottom')
  }
  for (const [x, y, z] of S.hay) c.set(x, y, z, P.hay, { axis: 'y' })
  stoneTrough(c, D.trough.water, null, 0, P.rim)
  for (const [at, h] of D.palms) datePalm(c, at, { soil: P.soil, trunk: P.palm.trunk, leaves: P.palm.leaves, height: h })
  for (let y = 0; y <= 2; y++) c.set(D.snubbingPost[0], y, D.snubbingPost[1], P.post, { axis: 'y' })
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const id = level > 1 ? `ranch_${level}` : 'ranch'
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
