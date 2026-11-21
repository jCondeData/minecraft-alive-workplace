// Ball Workshop (the Drying Loft), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground) and returns the structure for the
// minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded) for
// tools/blueprints/rebuild.py. Build: see package.json ("npm run build"); CUT=<y> builds a cutaway (everything above
// y removed).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, RACKS, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, clumped, plinth, floor, post, beamRing, wallPanel, window, door, doorway, gableRoof, gableWall,
  bargeTrim, flight, stairwell, railingRun, furnaceMouth, taperedStack, louvres, hoistArm, jettySill, boss, trayRack,
  doubleChest, wallShelf, flowerBed, furnitureParts, stair,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

// Pieces this family draws beyond furnitureParts: the job block, the anvil, chests, shelves, things on the bench.
const parts = {
  ...furnitureParts,
  job(c, f) { c.free(f.pos); c.set(...f.pos, f.block) },
  anvil(c, f) { c.free(f.pos); c.set(...f.pos, f.block, { facing: f.facing }) },
  forge() {}, // drawn with its breast and stack in pass 3
  doubleChest(c, f) { doubleChest(c, f.pos, f.facing, f.along) },
  wallShelf,
  pot(c, f) { c.free(f.pos); c.set(...f.pos, ...f.on) }, // a small thing on a bench top (f.on: [id, props])
}

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = clumped(mix(P.stone, 5))
  const plaster = mix(P.plaster, 11)
  const W = MASSES.workshop, L = MASSES.loft, S = MASSES.store
  const { x0, x1, z0, z1 } = W.posts
  const pl = W.plaster, rm = W.room
  const log = (axis) => [P.frame, { axis }]

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // Workshop: plinth, flags, plaster a block back from the posts, the beam ring (the ball's black band)
  plinth(c, x0, z0, x1, z1, stone)
  plinth(c, S.x0, S.z0, S.x1, S.z1, stone)
  floor(c, rm.x0, rm.z0, rm.x1, rm.z1, 0, P.floor)
  floor(c, S.room.x0, S.room.z0 + 1, S.room.x1, S.room.z1, 0, P.floor)
  wallPanel(c, [pl.x0, pl.z0], [pl.x1, pl.z0], 1, W.wallTop, plaster)
  wallPanel(c, [pl.x0, pl.z1], [pl.x1, pl.z1], 1, W.wallTop, plaster)
  wallPanel(c, [pl.x0, pl.z0], [pl.x0, pl.z1], 1, W.wallTop, plaster)
  wallPanel(c, [pl.x1, pl.z0], [pl.x1, pl.z1], 1, W.wallTop, plaster)
  for (const x of W.frontPosts) post(c, x, z0, 1, W.wallTop, P.frame)
  for (const x of W.backPosts) post(c, x, z1, 1, W.wallTop, P.frame)
  for (const z of W.sidePosts) { post(c, x0, z, 1, W.wallTop, P.frame); post(c, x1, z, 1, W.wallTop, P.frame) }
  beamRing(c, x0, z0, x1, z1, W.beamY, P.frame)
  floor(c, pl.x0, pl.z0, pl.x1, pl.z1, L.floorY, P.loftFloor) // the loft's boards, the workshop's ceiling

  // Loft: jettied a block out over the front on its sill beam and brackets, red cladding, a frame, the plate
  const lw = L.walls
  jettySill(c, { x0: lw.x0, x1: lw.x1, y: L.floorY, z: lw.z0, at: L.brackets, back: 'south' }, { log: P.frame, bracket: `${P.trim}_stairs` })
  for (let y = L.wallY[0]; y <= L.wallY[1]; y++) {
    for (let x = lw.x0; x <= lw.x1; x++) { c.set(x, y, lw.z0, P.cladding); c.set(x, y, lw.z1, P.cladding) }
    for (let z = lw.z0; z <= lw.z1; z++) { c.set(lw.x0, y, z, P.cladding); c.set(lw.x1, y, z, P.cladding) }
  }
  for (const x of L.frontPosts) post(c, x, lw.z0, L.wallY[0], L.wallY[1], P.frame)
  for (const x of L.backPosts) post(c, x, lw.z1, L.wallY[0], L.wallY[1], P.frame)
  for (const z of L.sidePosts) { post(c, lw.x0, z, L.wallY[0], L.wallY[1], P.frame); post(c, lw.x1, z, L.wallY[0], L.wallY[1], P.frame) }
  beamRing(c, lw.x0, lw.z0, lw.x1, lw.z1, L.plateY, P.frame)
  for (const z of L.ties) for (let x = lw.x0 + 1; x < lw.x1; x++) c.set(x, L.plateY, z, ...log('x'))

  // Store: the lean-to behind, plaster on the plinth under a single pitch from the loft's back wall
  const rowY = Object.fromEntries(S.roofRows)
  for (let z = S.z0; z <= S.z1; z++) {
    const top = z === S.z1 ? S.wallTop : z === S.z0 ? W.wallTop : rowY[z] - 1
    for (const x of [S.x0, S.x1]) for (let y = 1; y <= top; y++) c.set(x, y, z, plaster)
  }
  wallPanel(c, [S.x0, S.z1], [S.x1, S.z1], 1, S.wallTop, plaster)
  for (const x of [S.x0, S.x1]) post(c, x, S.z1, 1, S.wallTop, P.frame)
  for (let x = S.x0; x <= S.x1; x++) c.set(x, S.plateY, S.z1, ...log('x'))
  for (const [z, y] of S.roofRows) for (let x = S.x0; x <= S.x1; x++) stair(c, x, y, z, P.roof.stairs, 'north')

  // Roof: one 45-degree gable to the street, its gables red with two studs, barge boards under the rakes
  const R = MASSES.roof
  const main = gableRoof(c, R, P.roof)
  const studs = OPENINGS.gableStuds
  for (const line of [lw.z0, lw.z1]) gableWall(c, 'z', line, R.across, L.plateY + 1, main.under, (u) => (studs.includes(u) ? log('y') : P.cladding))
  bargeTrim(c, 'z', R.along[0], R.across, main.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', R.along[1], R.across, main.under, `${P.trim}_stairs`)

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.door, step: fd.step, stepStair: P.stoneStairs })
  doorway(c, OPENINGS.storeDoorway.at, OPENINGS.storeDoorway.height)
  for (const w of OPENINGS.windows)
    window(c, w.cells, w.out, w.pot ? 'sill' : null, { glass: P.glass, wood: P.trim, pots: w.pot ? [w.pot] : [] })
  for (const v of OPENINGS.louvres) {
    const cells = []
    const rows = v.rows ?? Array.from({ length: L.wallY[1] - L.wallY[0] + 1 }, (_, i) => L.wallY[0] + i)
    for (let u = v.from; u <= v.to; u++) for (const y of rows)
      cells.push(v.cells === 'x' ? [u, y, v.line] : [v.line, y, u])
    louvres(c, cells, P.louvre, v.out)
  }
  for (const [x, y, z] of [...OPENINGS.loftGlass, ...OPENINGS.gableGlass]) c.set(x, y, z, P.glass)
  const st = OPENINGS.stairs
  stairwell(c, st.well)
  flight(c, st.flight, st.rises, P.wood, P.loftFloor)
  railingRun(c, st.rail, P.wood)

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // The forge: a furnace in the east wall under its brick hood, the brick back between the posts, and the stack
  const forge = FURNITURE.find(f => f.part === 'forge')
  furnaceMouth(c, forge.pos, forge.facing, forge.block, { brick: P.brick.full, hoodStairs: P.brick.stairs, top: W.wallTop })
  for (let y = 0; y <= W.wallTop; y++) c.set(x1, y, forge.pos[2], P.brick.full)
  const K = MASSES.stack
  taperedStack(c, K.cx, K.cz, 0, K.tiers, { body: P.brick.full, ledge: P.brick.stairs, crown: P.brick.full, crownSlab: P.brick.slab })
  // its foot on the plinth's stone and the black band carried round it at the beam's height: one building, not two
  const kh = (K.tiers[0].w - 1) / 2
  for (let x = K.cx - kh; x <= K.cx + kh; x++) for (let z = K.cz - kh; z <= K.cz + kh; z++) {
    c.set(x, 0, z, stone)
    c.set(x, K.band, z, P.bandStone)
  }
  // the band's boss and button over the door, a lantern under it; the hoist arm and its lamp out of the gable
  boss(c, OPENINGS.boss, { face: P.boss, buttonId: P.button })
  const h = OPENINGS.hoist
  hoistArm(c, h.at, h.out, P.frame, h.drop)

  // --- Pass 4: interior (the plan's furniture, the apricorn trays) -----------------------------------------------
  for (const f of FURNITURE) parts[f.part](c, f)
  for (const r of RACKS) trayRack(c, r, { traySlab: `${P.wood}_slab`, fence: `${P.wood}_fence`, colours: P.apricorns })

  // --- Pass 5: ground -----------------------------------------------------------------------------------------------
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  for (const [x, y, z] of OUTSIDE.path) c.set(x, y, z, `${P.bandStone}_slab`, { type: 'bottom', waterlogged: 'false' }) // low flags, a half step up to the stair
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `ball_workshop_cut${cut}` : 'ball_workshop' } }
}
