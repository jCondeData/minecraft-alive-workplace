// Burgher's Townhouse, the taiga look (ROADMAP 49.10b): assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roofs and gables, openings, depth and detail, the tower, interior, vault and ground) for
// LEVEL = 1..4, each level the one before plus what it adds (II the counting-house wing, III the log lookout tower, IV
// the vault). Writes furniture.json (the furniture spots) and, for IV, out/cellar.txt. Build all four: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  LiftedCanvas, mix, plinth, floor, wallPanel, window, door, doorway, gableRoof, gableWall, hipRoof, sideDormer, chimney, flight,
  railingRun, furnitureParts, cellar, hatchLadder, quoins, stair, flowerBed, logPen, pentPorch, porchGable, post,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

function ring(c, x0, z0, x1, z1, y0, y1, id) {
  for (const [a, b] of [[[x0, z0], [x1, z0]], [[x0, z1], [x1, z1]], [[x0, z0], [x0, z1]], [[x1, z0], [x1, z1]]])
    wallPanel(c, a, b, y0, y1, id)
}

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  const P = PALETTE
  const H = MASSES.house, Wg = MASSES.wing, T = MASSES.tower
  const stone = mix(P.stone, 17), base = mix(P.plinth, 5), stack = mix(P.stack, 11)
  const has = o => (o.from ?? 1) <= level && level <= (o.until ?? 9)
  const logX = [P.log, { axis: 'x' }]
  const J = H.z0 - H.jetty // the jettied front line, z 1

  // a band of stripped logs round a pen's faces at y (the corners keep their dark logs): its floor line from outside
  const band = (x0, z0, x1, z1, y) => {
    for (let x = x0 + 1; x < x1; x++) for (const z of [z0, z1]) if (c.name(x, y, z) === `minecraft:${P.log}`) c.set(x, y, z, P.trimLog, { axis: 'x' })
    for (let z = z0 + 1; z < z1; z++) for (const x of [x0, x1]) if (c.name(x, y, z) === `minecraft:${P.log}`) c.set(x, y, z, P.trimLog, { axis: 'z' })
  }

  // --- Pass 1: block-out and structure ---------------------------------------------------------------------------
  // the house: a fieldstone ground storey, a notched spruce log pen over it jettied out to z 1, the attic's knee walls
  plinth(c, H.x0, H.z0, H.x1, H.z1, base)
  floor(c, H.x0 + 1, H.z0 + 1, H.x1 - 1, 7, 0, P.floor)
  floor(c, H.x0 + 1, 8, H.x1 - 1, H.z1 - 1, 0, P.kitchenFloor)
  ring(c, H.x0, H.z0, H.x1, H.z1, 1, H.firstY - 1, stone)
  logPen(c, { x0: H.x0, z0: J, x1: H.x1, z1: H.z1, y0: H.firstY, y1: H.kneeTop }, P.log)
  floor(c, H.x0 + 1, H.z0, H.x1 - 1, H.z1 - 1, H.firstY, P.floor)
  floor(c, H.x0 + 1, J + 1, H.x1 - 1, H.z1 - 1, H.atticY, P.floor)
  band(H.x0, J, H.x1, H.z1, H.firstY) // the jetty's sill
  band(H.x0, J, H.x1, H.z1, H.atticY) // the attic floor's line
  if (level >= 2) { // the counting-house wing: fieldstone below, logs above, open to its roof
    plinth(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, base)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 0, P.floor)
    ring(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, 1, 4, stone)
    logPen(c, { x0: Wg.x0, z0: Wg.z0, x1: Wg.x1, z1: Wg.z1, y0: 5, y1: 9 }, P.log)
    // round 2: the wing's corners against the house lie along its gables (two notched corners side by side read as a
    // checkerboard of log ends)
    for (const z of [Wg.z0, Wg.z1]) for (let y = 5; y <= 9; y++) c.set(Wg.x0, y, z, P.log, { axis: 'x' })
    for (let y = 5; y <= H.kneeTop; y++) c.set(H.x1, y, H.z1, P.log, { axis: 'x' }) // and the house's corner beside it
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 5, P.floor)
    for (const y of [5, 9]) band(Wg.x0, Wg.z0, Wg.x1, Wg.z1, y)
  }

  // --- Pass 2: roofs and gables ----------------------------------------------------------------------------------
  const roof = gableRoof(c, H.roof, P.roof)
  for (const z of [J, H.z1]) gableWall(c, 'z', z, [H.x0 + 1, H.x1 - 1], H.kneeTop + 1, roof.under, () => logX)
  const Dm = MASSES.dormer
  sideDormer(c, Dm.z, P.roof, { infill: [P.trimLog, { axis: 'z' }], glass: P.glass, cheek: P.roof.full }, { face: H.x0, y0: Dm.y0, depth: 4, overhang: false })
  if (level >= 2) {
    const wing = gableRoof(c, Wg.roof, P.roof, (x, y, z) => !c.get(x, y, z))
    // round 2: the wing's west eave lies under the house's wall but for its back overhang, where it hung as a lone
    // stair under the valley: the house's own eave closes the valley there
    for (let z = Wg.roof.along[0]; z <= Wg.roof.along[1]; z++)
      if (c.name(Wg.roof.across[0], Wg.roof.eaveY, z) === `minecraft:${P.roof.stairs}`) c.clear(Wg.roof.across[0], Wg.roof.eaveY, z)
    for (const z of [Wg.z0, Wg.z1]) gableWall(c, 'z', z, [Wg.x0 + 1, Wg.x1 - 1], 10, wing.under, () => logX)
  }

  // --- Pass 3: openings ------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.sill })
  if (has(OPENINGS.wingDoor)) {
    const wd = OPENINGS.wingDoor
    door(c, wd.at, wd.facing, 'north', { wood: P.wood, step: wd.step, stepStair: P.sill })
  }
  for (const w of OPENINGS.windows) if (has(w)) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
  for (const d of OPENINGS.doorways) if (has(d)) for (const at of d.at) doorway(c, at)
  const pt = OPENINGS.partition
  wallPanel(c, [pt.x0, pt.z], [pt.x1, pt.z], H.firstY + 1, H.atticY - 1, P.partition)
  door(c, pt.door, 'south', 'south', { wood: P.wood })
  const st = OPENINGS.stairs, at = OPENINGS.attic
  for (const [x, y, z] of [...st.well, ...at.well]) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  flight(c, at.flight, at.rises, P.wood, P.partition, H.firstY + 1)
  railingRun(c, st.rail, P.wood)
  railingRun(c, at.rail, P.wood)

  // --- Pass 4: depth and detail ----------------------------------------------------------------------------------
  // dressed corner stones up the fieldstone storey
  const corners = [[H.x0, H.z0], [H.x1, H.z0], [H.x0, H.z1], [H.x1, H.z1]]
  if (level >= 2) corners.push([Wg.x1, Wg.z0], [Wg.x1, Wg.z1])
  quoins(c, corners.filter(([x, z]) => !(level >= 2 && x === H.x1 && z === H.z1)), 1, 4, P.quoin, P.quoinAlt)
  // the porch: a slate pent across the front on log posts, log posts under the jetty's corners, ferns either side
  const pc = MASSES.porch
  pentPorch(c, pc, { roof: P.roof, post: P.post, bracket: P.bracket })
  for (const x of pc.jettyPosts) post(c, x, J, 1, H.firstY - 1, P.post)
  for (const b of OUTSIDE.fernBeds) flowerBed(c, b.x0, b.x1, b.z, P.plants, P.bedSoil)
  // the hoist beam under the apex, its chain and lantern
  const ho = MASSES.hoist
  c.set(ho.x, ho.y, ho.z, P.trimLog, { axis: 'z' })
  c.set(ho.x, ho.y - 1, ho.z, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(ho.x, ho.y - 2, ho.z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // the stacks: the hearth's at the back with its breast, the west one on the eave, both past the ridge, smoking
  const bs = MASSES.backStack
  chimney(c, bs.x, bs.z, 0, bs.top, stack)
  // round 2: its breast stands whole to under the gable's sill course and ends in a mossy slab, no stair pieces
  for (const x of [bs.x - 1, bs.x + 1]) {
    for (let y = 0; y <= bs.breastTop; y++) c.set(x, y, bs.z, stack)
    c.set(x, bs.breastTop + 1, bs.z, P.capSlab, { type: 'bottom', waterlogged: 'false' })
  }
  const ss = MASSES.sideStack
  for (const z of ss.zs) for (let y = 0; y <= ss.top; y++) c.set(ss.x, y, z, stack)
  c.set(ss.x, ss.top + 1, ss.zs[0], 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  c.set(ss.x, ss.top + 1, ss.zs[1], P.cap)
  // firewood stacked under the west eave, a slab over it
  const wp = MASSES.woodpile
  for (const z of wp.zs) {
    c.set(wp.x, 0, z, base)
    for (let y = 1; y <= wp.top; y++) c.set(wp.x, y, z, P.firewood[(y + 2 * z) % P.firewood.length], { axis: 'x' })
    c.set(wp.x, wp.top + 1, z, P.woodCap, { type: 'bottom', waterlogged: 'false' })
  }
  if (level >= 2) { // the wing door's little gabled porch on two posts
    const wp2 = Wg.porch
    for (const x of wp2.posts) post(c, x, wp2.z0, 1, wp2.y - 1, P.post)
    porchGable(c, wp2, P.roof, { beam: P.log, face: logX }, (x, y, z) => !c.get(x, y, z) || c.name(x, y, z) === 'minecraft:air')
  }

  // --- Pass 5: the tower (III) -------------------------------------------------------------------------------------
  if (level >= 3) {
    plinth(c, T.x0, T.z0, T.x1, T.z1, base)
    floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, 0, P.kitchenFloor)
    ring(c, T.x0, T.z0, T.x1, T.z1, 1, 4, stone)
    quoins(c, [[T.x0, T.z0], [T.x1, T.z0], [T.x0, T.z1], [T.x1, T.z1]], 1, 4, P.quoin, P.quoinAlt)
    // the log pen with upright corner posts (notched corners on a pen this narrow read as a checkerboard of log ends)
    const tc = (x, z) => (x === T.x0 || x === T.x1) && (z === T.z0 || z === T.z1)
    logPen(c, { x0: T.x0, z0: T.z0, x1: T.x1, z1: T.z1, y0: 5, y1: T.logTop }, P.log, (x, y, z) => tc(x, z))
    for (const x of [T.x0, T.x1]) for (const z of [T.z0, T.z1]) post(c, x, z, 5, T.logTop, P.log)
    for (const y of [5, ...T.floors.slice(1)]) band(T.x0, T.z0, T.x1, T.z1, y) // a pale band at each floor line
    for (const y of [...T.floors, T.logTop]) floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, y, P.floor)
    hipRoof(c, T.roof, P.roof) // its first ring the eaves round the pen's top, the second over the walls
    // round 2: a king post under the peak, so the hip's top block stands on wood, not air
    for (let y = T.logTop + 1; y < T.roof.eaveY + 3; y++) c.set(15, y, 15, P.log, { axis: 'y' })
    for (const w of OPENINGS.windows) if (has(w) && w.from === 3) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
    for (const d of OPENINGS.doorways) if (has(d)) for (const a of d.at) doorway(c, a)
    const L = T.ladder
    hatchLadder(c, L.x, L.z, L.y0, L.top, L.facing, P.wood)
  }

  // --- Pass 6: interior (the plan's furniture) ---------------------------------------------------------------------
  for (const f of FURNITURE) if (has(f)) furnitureParts[f.part](c, f)
  for (const f of FURNITURE) if (has(f) && f.throat) c.clear(...f.throat)

  // --- Pass 7: the vault (IV) --------------------------------------------------------------------------------------
  let dug = null
  if (level >= 4) {
    const C = MASSES.cellar
    dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.cellarWall, 3), P.cellarFloor)
    hatchLadder(c, C.ladder.x, C.ladder.z, -C.depth + 1, 0, C.ladder.facing, P.wood)
    for (const z of C.ribs) {
      for (let x = C.x0 + 2; x <= C.x1 - 2; x++) c.set(x, -1, z, P.log, { axis: 'x' })
      stair(c, C.x0 + 1, -1, z, P.bracket, 'west', 'top')
      stair(c, C.x1 - 1, -1, z, P.bracket, 'east', 'top')
    }
  }
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = drawLevel(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  if (dug) { fs.mkdirSync(path.join(here, 'out'), { recursive: true }); fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(',')) }
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
