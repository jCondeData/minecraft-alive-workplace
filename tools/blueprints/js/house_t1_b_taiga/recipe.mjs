// Daub House, taiga look (49.10d), the recipe: assembles plan.mjs in passes (block-out and structure, roofs and gables,
// openings, depth and detail, interior, ground) on the parts library (../parts), the kept insides from
// ../parts/daub_looks.mjs. Writes furniture.json. Build: build.sh. CUT=<y>: a cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, WINDOWS, OUTSIDE } from './plan.mjs'
import { Canvas, mix, stair, gableRoof, gableWall, bargeTrim, window, pentPorch } from '../parts/index.mjs'
import { daubInterior, daubSpots } from '../parts/daub_looks.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, H = MASSES.hall, K = MASSES.kitchen
  const footing = mix(P.footing, 6), stack = mix(P.stack, 8)
  const log = (x, y, z, axis) => c.set(x, y, z, P.log, { axis })

  // --- Pass 1: block-out and structure: the log pen, its crossing corners, the band, the kitchen wing ------------------
  for (let z = H.z0; z <= H.z1; z++) for (const x of [H.x0, H.x1]) {
    c.set(x, 0, z, footing)
    for (let y = 1; y <= H.groundTop; y++) log(x, y, z, 'z')
  }
  for (const z of [H.front, H.back]) for (let x = H.x0 - 1; x <= H.x1 + 1; x++) {
    if (x > H.x0 && x < H.x1) c.set(x, 0, z, footing)
    for (let y = 1; y <= H.groundTop; y++) {
      const crossing = y % 2 === 1 // the front and back walls' logs cross the side walls on every other course
      if (x === H.x0 - 1 || x === H.x1 + 1) { if (crossing) log(x, y, z, 'x') } // the notched corners' log ends
      else if (x === H.x0 || x === H.x1) { if (crossing) log(x, y, z, 'x') }
      else log(x, y, z, 'x')
    }
  }
  // the band: the loft floor's beam ring, its front and back lintels carrying the gables over the recesses
  for (let z = H.z0; z <= H.z1; z++) for (const x of [H.x0, H.x1]) c.set(x, H.bandY, z, P.band, { axis: 'z' })
  for (let x = H.x0; x <= H.x1; x++) for (const z of [H.z0, H.z1]) c.set(x, H.bandY, z, P.band, { axis: 'x' })
  // the knee walls (logs) under the eaves
  for (let z = H.z0; z <= H.z1; z++) for (const x of [H.x0, H.x1]) for (let y = H.bandY + 1; y <= H.kneeTop; y++) log(x, y, z, 'z')
  // the kitchen wing's log walls, its corners crossing and their ends poking out on every other course
  for (let x = K.x0; x <= K.x1 + 1; x++) for (let z = K.z0 - 1; z <= K.z1 + 1; z++) {
    const wallX = x === K.x1 && z >= K.z0 && z <= K.z1, wallZ = (z === K.z0 || z === K.z1) && x <= K.x1
    const endX = x === K.x1 + 1 && (z === K.z0 || z === K.z1), endZ = x === K.x1 && (z === K.z0 - 1 || z === K.z1 + 1)
    if (!wallX && !wallZ && !endX && !endZ) continue
    if (wallX || wallZ) c.set(x, 0, z, footing)
    for (let y = 1; y <= K.wallTop; y++) {
      const corner = wallX && wallZ
      if (y === H.bandY) { if (wallX || wallZ) c.set(x, y, z, P.band, { axis: wallZ ? 'x' : 'z' }); continue }
      if (corner) log(x, y, z, y % 2 ? 'x' : 'z')
      else if (wallZ) log(x, y, z, 'x')
      else if (wallX) log(x, y, z, 'z')
      else if (endX && y % 2 === 1) log(x, y, z, 'x')
      else if (endZ && y % 2 === 0) log(x, y, z, 'z')
    }
  }
  c.fill(K.x0, K.ceilingY, K.z0 + 1, K.x1 - 1, K.ceilingY, K.z1 - 1, P.ceiling) // the kitchen's ceiling (its lamp's)

  // --- Pass 2: roofs and gables --------------------------------------------------------------------------------------
  const main = gableRoof(c, H.roof, P.roof)
  const studs = MASSES.studs
  const gablePick = (u, y) => (studs.includes(u) ? [P.stud, { axis: 'y' }] : P.boards)
  for (const z of [H.z0, H.z1]) gableWall(c, 'z', z, [H.x0, H.x1], H.bandY + 1, main.under, gablePick)
  bargeTrim(c, 'z', H.roof.along[0], H.roof.across, main.under, P.barge)
  bargeTrim(c, 'z', H.roof.along[1], H.roof.across, main.under, P.barge)
  // the wing's roof: on the hall's side only over its slope (the T's valley), elsewhere where nothing stands yet
  const hallTop = x => main.under[x]
  const wing = gableRoof(c, K.roof, P.roof, (x, y, z) => !c.get(x, y, z) && (x > H.x1 || y > hallTop(x)))
  // the valley: the pocket between the hall's slope and the wing's roof running onto it closed solid
  for (let x = H.x1 - 2; x <= H.x1 + 1; x++) for (let z = K.roof.across[0]; z <= K.roof.across[1]; z++) {
    const top = wing.ridgeY - Math.abs(z - (K.roof.across[0] + K.roof.across[1]) / 2)
    for (let y = hallTop(x) + 1; y < top; y++) if (!c.get(x, y, z)) c.set(x, y, z, P.roof.full)
  }
  gableWall(c, 'x', K.x1, [K.z0, K.z1], K.wallTop + 1, wing.under, (z, y) => (z === 6 ? [P.stud, { axis: 'y' }] : P.boards))
  bargeTrim(c, 'x', K.roof.along[1], K.roof.across, wing.under, P.barge)

  // --- Pass 3: openings -------------------------------------------------------------------------------------------------
  for (const w of WINDOWS) {
    window(c, w.cells, w.out, w.dress === 'shutters' ? 'shutters' : w.dress, { glass: P.glass, wood: P.wood, sill: P.sill })
    if (w.dress === 'shutters') { // and a sill under the shutters
      const [x, y, z] = w.cells[0], d = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[w.out]
      stair(c, x + d[0], y - 1, z + d[1], P.sill, { north: 'south', south: 'north', west: 'east', east: 'west' }[w.out], 'top')
    }
  }

  // --- Pass 4: depth and detail: the porch, the stack, firewood ---------------------------------------------------------
  const Po = MASSES.porch
  pentPorch(c, { x0: Po.x0, x1: Po.x1, z: Po.z, y: Po.y, toward: 'south', posts: Po.posts, lamps: Po.lamps },
    { roof: P.roof, post: P.post, bracket: P.bracket })
  for (let x = Po.x0; x <= Po.x1; x++) for (const z of [0, 1]) if (!c.get(x, 0, z)) c.set(x, 0, z, P.deck)
  const S = MASSES.stack
  for (let x = S.breast[0]; x <= S.breast[1]; x++) for (const z of S.breastZ) for (let y = 0; y <= S.breastTop; y++) c.set(x, y, z, stack)
  stair(c, S.breast[0], S.breastTop + 1, S.z, P.shoulder, 'east')
  stair(c, S.breast[1], S.breastTop + 1, S.z, P.shoulder, 'west')
  for (let y = S.breastTop + 1; y <= S.top; y++) c.set(S.x, y, S.z, stack)
  c.set(S.x, S.top + 1, S.z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  for (const [x, z] of OUTSIDE.firewood) {
    c.set(x, 0, z, footing)
    for (let y = 1; y <= H.groundTop; y++) c.set(x, y, z, P.firewood[(x + y) % P.firewood.length], { axis: 'z' })
  }

  // --- Pass 5: interior: the shipped Daub House's insides, cell for cell ----------------------------------------------
  daubInterior(c, P, { step: OUTSIDE.step })

  // --- Pass 6: ground: ferns in moss beside the porch and the wing ---------------------------------------------------------
  for (const [x, z] of OUTSIDE.ferns) { c.set(x, 0, z, P.soil); c.set(x, 1, z, P.plant) }
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + daubSpots(c).map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `house_t1_b_cut${cut}` : 'house_t1_b' } }
}
