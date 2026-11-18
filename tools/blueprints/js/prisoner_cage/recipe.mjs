// The Prisoner Cage (the Cage Wagon), the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP
// 49; wagons.mjs grown for it) in passes (ground, block-out and structure, cage and roof, depth and detail, camp,
// light) and returns the structure for the minecraft-builder skill's runner. Writes furniture.json (no spots: see
// plan.mjs) for rebuild.py. Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above
// y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, PRISONER } from './plan.mjs'
import {
  Canvas, mix, stair, gableRoof, gableWall, bargeTrim, spit, logStack, hangingLantern,
  cartWheel, cartBed, barCage, cageLid, shafts, ridgeTent, warStandard,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, F = P.frame
  const W = MASSES.wagon, T = MASSES.tent, Fi = MASSES.fire
  const [w, , d] = SIZE

  // --- Pass 1: block-out and structure: the wagon's bed, its wheels, the cage, its lid --------------------------
  cartBed(c, W.bed, { beam: F.log, deck: F.planks })
  for (const z of W.axles.zs) for (let x = W.axles.x0; x <= W.axles.x1; x++) c.set(x, W.axles.y, z, P.hubLog, { axis: 'x' }) // the axles under the deck
  for (let x = W.tail.x0; x <= W.tail.x1; x++) c.set(x, W.tail.y, W.tail.z, F.slab, { type: 'top', waterlogged: 'false' }) // the tail board, the step up
  for (const hub of W.wheels) cartWheel(c, hub, 'x', { rim: { full: F.planks, stairs: F.stairs }, hubLog: P.hubLog })
  barCage(c, W.cage, { post: F.log, bars: P.bars })
  cageLid(c, W.lid, { beam: F.log, deck: F.planks })

  // --- Pass 2: the roof: 45 degrees in pale birch over the cage and the bench, a dark ridge board, barge boards ---
  const roof = gableRoof(c, W.roof, P.roof)
  for (let z = W.roof.along[0]; z <= W.roof.along[1]; z++) c.set(2, roof.ridgeY, z, P.trim, { type: 'bottom', waterlogged: 'false' }) // a thin dark ridge crest
  for (let z = W.lid.z0; z <= W.lid.z1; z++) gableWall(c, 'z', z, W.roof.across, W.lid.y + 1, roof.under, () => F.planks)
  bargeTrim(c, 'z', W.roof.along[0], W.roof.across, roof.under, F.stairs)
  bargeTrim(c, 'z', W.roof.along[1], W.roof.across, roof.under, F.stairs)

  // --- Pass 3: depth and detail: the driver's bench, the dropped shafts, the cage's straw and chain --------------
  for (let x = W.bench.x0; x <= W.bench.x1; x++) stair(c, x, W.bench.y, W.bench.z, F.stairs, 'north') // backs to the cage
  shafts(c, W.shafts, F.slab)
  for (const [x, y, z] of W.straw) c.set(x, y, z, P.straw)
  c.set(...W.chain, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(...MASSES.hay, P.hay, { axis: 'y' })

  // --- Pass 4: the guards' camp: the tent, the cook fire, the woodpile, the seat, the standard -------------------
  ridgeTent(c, T, P.canvas, { back: T.back, hem: P.canvasEdge })
  for (const [x, y, z] of T.poles) c.set(x, y, z, F.fence) // the tent poles' tips through the canvas at each end
  for (const b of T.barrels) c.set(...b, 'barrel', { facing: 'up', open: 'false' })
  c.set(...Fi.campfire, 'campfire', { lit: 'true', facing: 'north', signal_fire: 'false', waterlogged: 'false' })
  spit(c, Fi.spit.xa, Fi.spit.xb, Fi.spit.z, Fi.spit.y0, F.fence)
  logStack(c, Fi.woodpile, P.fuel, 'z')
  logStack(c, Fi.seat, P.seat, 'x')
  const S = MASSES.standard
  warStandard(c, S.at, S, { log: F.log, fence: F.fence, banner: P.banner, lantern: P.lantern })

  // --- Pass 5: light: lanterns under the eaves at the tail and the bench ----------------------------------------
  for (const [x, y, z] of W.lanterns) hangingLantern(c, x, y, z, P.lantern)

  // --- Pass 6: ground: the whole y 0 layer (the template replaces the top ground block), grass at the rim so the
  // camp sits into any ground, dirt under what covers it, mud at the bogged wheels, a trodden path ---------
  const rim = mix(P.ground.rim, 11), camp = mix(P.ground.camp, 13), covered = mix(P.ground.covered, 17)
  const mud = MASSES.ground.mud, trod = MASSES.ground.trod
  const has = (list, x, z) => list.some(([a, , b]) => a === x && b === z)
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    if (c.get(x, 0, z)) continue // a wheel's sunk row
    const over = c.get(x, 1, z) ?? c.get(x, 2, z)
    const solidOver = over && !/carpet|campfire|lantern/.test(over.id)
    if (has(mud, x, z)) c.set(x, 0, z, P.ground.mud)
    else if (has(trod, x, z) && !over) c.set(x, 0, z, P.ground.trod)
    else if (solidOver || over) c.set(x, 0, z, covered)
    else if (x === 0 || z === 0 || x === w - 1 || z === d - 1) c.set(x, 0, z, rim)
    else c.set(x, 0, z, camp)
  }
  // the prisoner's cell and the one over it stay clear; the deck under them holds
  c.free(PRISONER, [PRISONER[0], PRISONER[1] + 1, PRISONER[2]])
  if (!c.get(PRISONER[0], PRISONER[1] - 1, PRISONER[2])) throw new Error('nothing under the prisoner')
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `prisoner_cage_cut${cut}` : 'prisoner_cage' } }
}
