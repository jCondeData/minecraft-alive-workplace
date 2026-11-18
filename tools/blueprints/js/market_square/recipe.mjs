// The Market Square, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out and
// structure: the paving, the undercroft, the hall and its roof; openings; depth and detail: jetty, balcony, cupola,
// stair, shutters, bunting; interior: the Market Court; ground: the stall, the beds, the lamps) and returns the
// structure for the minecraft-builder skill's runner. Writes furniture.json (the Market Court's spots) for
// tools/blueprints/rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE } from './plan.mjs'
import {
  Canvas, mix, stair, slab, post, beamRing, wallPanel, window, door, gableRoof, gableWall, bargeTrim, louvre,
  lampPost, plantFlower, hangingLantern, furnitureParts, arcadeRun, jettyCorbels,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES
  const [cx, cz] = M.middle
  const field = mix(P.paving, 11), pier = mix(P.pier, 5), plaster = mix(P.plaster, 3), base = mix(P.stairBase, 9)
  const [W, , D] = SIZE

  // --- Pass 1: block-out and structure ------------------------------------------------------------------------------
  // Paving: gathered stone, the traders' ring at 4 out, a rosette round the middle, a half step along the road
  const A = M.paving
  for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
    const d = Math.max(Math.abs(x - cx), Math.abs(z - cz)), m = Math.abs(x - cx) + Math.abs(z - cz)
    if (d === 0) c.set(x, 0, z, P.heart)
    else if (d === 1 || (m === 2 && d === 2) || (d <= 3 && (x === cx || z === cz))) c.set(x, 0, z, P.rosette)
    else if (d === A.ringAt) c.set(x, 0, z, P.ring)
    else c.set(x, 0, z, field)
  }
  for (let x = A.frontStep.x0; x <= A.frontStep.x1; x++) slab(c, x, 0, A.frontStep.z, P.step)
  for (const b of A.beds) for (let x = b.x0; x <= b.x1; x++) for (let z = b.z0; z <= b.z1; z++) c.set(x, 0, z, P.bed)

  // Undercroft: stone piers on two lines carrying a girder each, an arch in each 3-wide middle opening
  const U = M.undercroft, H = M.hall
  for (const line of U.lines)
    arcadeRun(c, 'x', line, U.piers, U.top, { pier, course: P.girder, arch: P.arch })
  for (const line of U.lines) for (let x = H.x0; x <= H.x1; x++) c.set(x, U.top, line, P.girder, { axis: 'x' })

  // The hall's floor (y 4), jettied a block out over the front piers: girder sills round it, planks inside
  // (the sill runs a block proud of the front wall, so the shutters stand on it)
  for (let x = H.x0; x <= H.x1; x++) for (let z = H.z0 - 1; z <= H.z1; z++) {
    if (z === U.lines[0] || z === U.lines[1]) continue
    const edge = z < H.z0 ? { axis: 'x' } : (x === H.x0 || x === H.x1) ? { axis: 'z' } : null
    if (edge) c.set(x, H.floorY, z, P.girder, edge)
    else c.set(x, H.floorY, z, P.floor)
  }

  // the sill runs on round the back and the west side too (not the east, the stair's headroom), so every shuttered
  // window stands on it, carried on corbels over the pier lines
  for (let x = H.x0 - 1; x <= H.x1; x++) { c.set(x, H.floorY, H.z1 + 1, P.girder, { axis: 'x' }); if (x < H.x0) c.set(x, H.floorY, H.z0 - 1, P.girder, { axis: 'x' }) }
  for (let z = H.z0; z <= H.z1; z++) c.set(H.x0 - 1, H.floorY, z, P.girder, { axis: 'z' })
  jettyCorbels(c, 'x', H.z1 + 1, H.corbels, H.floorY - 1, P.corbel, 'north')
  jettyCorbels(c, 'z', H.x0 - 1, U.lines, H.floorY - 1, P.corbel, 'east')

  // Walls: plaster panels, dark oak posts proud at the bays and corners, a beam ring at y 8
  wallPanel(c, [H.x0, H.z0], [H.x1, H.z0], H.y0, H.y1, plaster)
  wallPanel(c, [H.x0, H.z1], [H.x1, H.z1], H.y0, H.y1, plaster)
  wallPanel(c, [H.x0, H.z0], [H.x0, H.z1], H.y0, H.y1, plaster)
  wallPanel(c, [H.x1, H.z0], [H.x1, H.z1], H.y0, H.y1, plaster)
  for (const x of H.posts) { post(c, x, H.z0, H.y0, H.y1, P.frame); post(c, x, H.z1, H.y0, H.y1, P.frame) }
  beamRing(c, H.x0, H.z0, H.x1, H.z1, H.beamY, P.frame)
  for (const x of H.tieBeams) for (let z = H.z0 + 1; z < H.z1; z++) c.set(x, H.beamY, z, P.girder, { axis: 'z' })

  // Roof: a steep tile gable, ridge along x, eaves a block out front and back, rakes a block out at each end
  const R = M.roof
  // the front gable's roof meets the main one: in its bay each column keeps whichever slope stands higher
  const F = M.frontGable
  const mainTop = z => { const i = Math.min(z - R.across[0], R.across[1] - z); return R.eaveY + 2 * i }
  const gableTop = x => { const i = Math.min(x - F.across[0], F.across[1] - x); return R.eaveY + 2 * i }
  const inBay = (x, z) => x >= F.across[0] && x <= F.across[1] && z >= F.along[0] && z <= F.along[1]
  const roof = gableRoof(c, { axis: 'x', across: R.across, along: R.along, eaveY: R.eaveY, steep: true }, P.roof,
    (x, y, z) => !(inBay(x, z) && gableTop(x) > mainTop(z)))
  const front = gableRoof(c, { axis: 'z', across: F.across, along: F.along, eaveY: R.eaveY, steep: true }, P.roof,
    (x, y, z) => gableTop(x) >= mainTop(z))
  gableWall(c, 'z', H.z0, [F.across[0] + 1, F.across[1] - 1], H.beamY + 1, front.under, (x, y) => {
    if (x === M.cupola.x) return (y === H.beamY + 2 || y === H.beamY + 3) ? P.glass : [P.frame, { axis: 'y' }]
    return plaster
  })
  bargeTrim(c, 'z', F.along[0], F.across, front.under, P.barge)
  for (const x of [H.x0, H.x1]) {
    gableWall(c, 'x', x, [H.z0, H.z1], H.beamY + 1, roof.under, (z, y) => {
      if (z === M.cupola.z) return (y === H.beamY + 2 || y === H.beamY + 3) ? P.glass : [P.frame, { axis: 'y' }] // king post, slit window
      return plaster
    })
  }
  for (const x of [R.along[0], R.along[1]]) bargeTrim(c, 'x', x, R.across, roof.under, P.barge)

  // --- Pass 2: openings ---------------------------------------------------------------------------------------------
  const O = OPENINGS
  const win = (xs, z, out) => window(c, xs.flatMap(x => [[x, H.y0, z], [x, H.y0 + 1, z]]), out, 'shutters', { glass: P.glass, wood: P.shutter })
  for (const [a, b] of O.front.windows) win(range(a, b), H.z0, 'north')
  for (const [a, b] of O.back.windows) win(range(a, b), H.z1, 'south')
  window(c, [[H.x0, H.y0, O.west.window], [H.x0, H.y0 + 1, O.west.window]], 'west', 'shutters', { glass: P.glass, wood: P.shutter })
  for (const x of O.front.panes) for (const y of [H.y0, H.y0 + 1]) c.set(x, y, H.z0, P.glass)
  door(c, [O.front.door, H.y0, H.z0], 'north', 'north', { wood: P.door })
  door(c, [H.x1, H.y0, O.east.door], 'west', 'east', { wood: P.door })

  // --- Pass 3: depth and detail -------------------------------------------------------------------------------------
  // the jetty's corbels under the front sill, over each front pier's line
  jettyCorbels(c, 'x', H.z0 - 1, H.corbels, H.floorY - 1, P.corbel, 'south')

  // the proclamation balcony over the middle: a floor on two corbels, a railing, the market banner, a lantern under
  const B = M.balcony
  for (let x = B.x0; x <= B.x1; x++) for (let z = B.z0; z < H.z0 - 1; z++) c.set(x, B.y, z, P.floor)
  jettyCorbels(c, 'x', B.z0, B.corbels, B.y - 1, P.corbel, 'south')
  for (let x = B.x0; x <= B.x1; x++) c.set(x, B.y + 1, B.z0, P.rail)
  for (let z = B.z0 + 1; z < H.z0; z++) { c.set(B.x0, B.y + 1, z, P.rail); c.set(B.x1, B.y + 1, z, P.rail) }
  c.set(cx, B.y, B.z0 - 1, P.flag, { facing: 'north' })
  hangingLantern(c, cx, B.y - 1, B.z0 + 1)

  // bunting on the jetty's front face, the market's two colours
  M.bunting.forEach((x, i) => c.set(x, H.floorY, H.z0 - 2, P.banners[i % 2], { facing: 'north' }))

  // the cupola on the ridge's middle
  louvre(c, M.cupola.x, M.cupola.z, roof.ridgeY, { base: P.cupola.base, post: P.cupola.post, roof: P.roof })

  // the outside stair up the east edge: three steps on a stone base, a landing at the floor's level, a lamp at its end
  const S = M.stair
  for (const [y, z] of S.steps) {
    for (let yy = 1; yy < y; yy++) c.set(S.x, yy, z, base)
    stair(c, S.x, y, z, P.stairStone, 'south')
  }
  for (let z = S.landing.z0; z <= S.landing.z1; z++) for (let y = 1; y <= S.landing.y; y++) c.set(S.x, y, z, base)
  lampPost(c, S.lamp[0], S.lamp[1], S.lamp[2], P.lampPost, 1)

  // the undercroft's lanterns, hung from the floor, and its benches in the back's side bays (backs to the back)
  for (const [x, y, z] of U.lamps) hangingLantern(c, x, y, z)
  for (const [x, z] of U.benches) stair(c, x, 1, z, P.bench, 'south')

  // --- Pass 4: interior: the Market Court ---------------------------------------------------------------------------
  for (const f of FURNITURE) furnitureParts[f.part](c, f)

  // --- Pass 5: ground: the stall, the beds, the lamps at the way in -------------------------------------------------
  const T = M.stall
  for (const z of [T.z0, T.z1]) { post(c, T.x0, z, 1, 3, P.rail); post(c, T.x1, z, 1, 2, P.rail) }
  for (let z = T.z0 + 1; z < T.z1; z++) {
    c.set(T.x1, 1, z, P.counter, { axis: 'z' })
    c.set(T.x1, 2, z, P.produce[(z - T.z0 - 1) % P.produce.length])
    c.set(T.x0, 1, z, P.hay, { axis: 'y' })
  }
  for (let z = T.z0; z <= T.z1; z++) {
    c.set(T.x0, 4, z, P.canvas[z % 2])
    c.set(T.x1, 3, z, P.canvas[z % 2])
  }
  for (const b of A.beds) {
    let i = 0
    for (let x = b.x0; x <= b.x1; x++) for (let z = b.z0; z <= b.z1; z++) {
      if (x === b.bush[0] && z === b.bush[1]) c.set(x, 1, z, P.bush)
      else plantFlower(c, x, 1, z, P.flowers[i++ % P.flowers.length])
    }
  }
  for (const [x, z] of M.lamps) lampPost(c, x, 1, z, P.lampPost, 2)
  return c
}

const range = (a, b) => Array.from({ length: b - a + 1 }, (_, i) => a + i)

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `market_square_cut${cut}` : 'market_square' } }
}
