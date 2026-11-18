// The Clinic, the recipe: assembles plan.mjs (the pavilion ward) from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground) and returns the structure for the
// minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded) for rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, WARD, VERANDA, OPENINGS, JOB, BEDS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, gableWall, bargeTrim, hood, flowerBed, louvre,
  railingRun, furnitureParts, cappedGableRoof, wardBeds, lampBeam, benchRecess,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const render = mix(P.render, 5)
  const { walls: W, room: R, plinth: PL } = WARD
  const log = P.frame

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // The plinth a block proud of the walls all round (sills and seats stand on it), the birch floor in it,
  // the veranda's deck of boards beside it.
  plinth(c, PL.x0, PL.z0, PL.x1, PL.z1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  const dk = VERANDA.deck
  floor(c, dk.x0, dk.z0, dk.x1, dk.z1, 0, `${P.wood}_planks`)

  // Ward walls: white render up to the wall plate, log corners, the plate a log ring on top.
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, WARD.wallTop, render)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, WARD.wallTop, render)
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, WARD.wallTop, render)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, WARD.wallTop, render)
  for (const [x, z] of WARD.corners) post(c, x, z, 1, WARD.wallTop, log)
  beamRing(c, W.x0, W.z0, W.x1, W.z1, WARD.plateY, log)

  // The roof: one 45-degree slate gable, its ridge cell closed under a slab cap, the west slope running on as a
  // catslide over the veranda.
  const roof = cappedGableRoof(c, WARD.roof, P.roof)
  const [l0, l1] = WARD.roof.along
  for (let k = 1; k <= WARD.roof.extend.a0; k++) { // the catslide's courses over the veranda, in boards
    const x = WARD.roof.across[0] - k
    for (let z = l0; z <= l1; z++) c.set(x, roof.under[x], z, P.verandaRoof, { facing: 'east', half: 'bottom', shape: 'straight', waterlogged: 'false' })
  }
  // Gables: render in the wall plane up to the roof, a king post round a little window under the ridge.
  for (const z of [W.z0, W.z1]) {
    gableWall(c, 'z', z, [W.x0, W.x1], WARD.plateY + 1, roof.under, (u, y) => {
      if (u === 8 && y !== 7) return [log, { axis: 'y' }] // the king post round the window
      return render
    })
  }
  // The veranda's posts under the catslide, its two ends closed in boards with a pane.
  for (const [x, z] of VERANDA.posts) post(c, x, z, 1, VERANDA.postTop, log)
  for (const z of VERANDA.ends) {
    for (const [x, y] of VERANDA.endCells) c.set(x, y, z, `${P.wood}_planks`)
    c.set(VERANDA.endPane[0], VERANDA.endPane[1], z, P.glass)
  }

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.wood, step: fd.step, stepStair: P.step })
  const vd = OPENINGS.verandaDoor
  door(c, vd.at, vd.facing, vd.out, { wood: P.wood, hinge: 'right' })
  for (const [x, y, z] of vd.transom) c.set(x, y, z, P.glass)
  for (const w of OPENINGS.windows) {
    if (w.dress === 'seat') { // the veranda's windows: a seat in the recess under each instead of a sill
      window(c, w.cells, w.out, null, { glass: P.glass })
      const [x, y, z] = w.cells[0]
      benchRecess(c, [x - 1, y - 1, z], 'west', `${P.wood}_stairs`) // a seat on the plinth under the window
      continue
    }
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: OUTSIDE.pots[w.out] ?? [] })
  }

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // A slate hood over the front door with its lantern; barge boards under both rakes; the ventilator on the ridge.
  hood(c, fd.at[0], WARD.wallTop, fd.step[2], P.roof)
  bargeTrim(c, 'z', WARD.roof.along[0], [1, 13], roof.under, P.trim)
  bargeTrim(c, 'z', WARD.roof.along[1], [1, 13], roof.under, P.trim)
  // The east dormer: log cheeks round a pane on the wall plate, a capped gable over it carried back into the slope.
  const dm = WARD.dormer
  for (const dz of [-1, 1]) c.set(dm.face, dm.y, dm.z + dz, log, { axis: 'y' })
  c.set(dm.face, dm.y, dm.z, P.glass)
  for (const x of [dm.back, dm.face]) {
    c.set(x, dm.y + 1, dm.z - 1, P.roof.stairs, { facing: 'south', half: 'bottom', shape: 'straight', waterlogged: 'false' })
    c.set(x, dm.y + 1, dm.z + 1, P.roof.stairs, { facing: 'north', half: 'bottom', shape: 'straight', waterlogged: 'false' })
    c.set(x, dm.y + 1, dm.z, P.roof.full)
    c.set(x, dm.y + 2, dm.z, P.roof.slab, { type: 'bottom', waterlogged: 'false' })
  }
  louvre(c, WARD.vent.x, WARD.vent.z, roof.capY, { base: P.roof.full, post: `${P.wood}_fence`, roof: P.roof, finial: 'lightning_rod' })
  // The veranda: a rail between its posts (open in the middle bay, opposite the door), a lantern over the door.
  for (const r of VERANDA.rails) railingRun(c, r, P.wood)
  c.set(2, 3, vd.at[2], 'lantern', { hanging: 'true', waterlogged: 'false' })

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  // The Nurse's brewing stand under the west window, a free cell beside it for her.
  const [bx, by, bz] = JOB.brewingStand
  c.free(JOB.brewingStand)
  c.set(bx, by, bz, 'brewing_stand', { has_bottle_0: 'false', has_bottle_1: 'false', has_bottle_2: 'false' })
  for (const f of FURNITURE) furnitureParts[f.part](c, f)
  for (const side of Object.values(BEDS)) wardBeds(c, side.heads, side.facing, { colour: P.bed, wood: P.wood, stands: side.stands })
  for (const b of WARD.beams) lampBeam(c, 'x', b.z, [R.x0, R.x1], WARD.plateY, log, { at: b.lampAt, drop: 1, wood: P.wood })
  c.free(JOB.stands, [JOB.stands[0], JOB.stands[1] + 1, JOB.stands[2]])

  // --- Pass 5: ground -----------------------------------------------------------------------------------------------
  for (const b of OUTSIDE.herbBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `clinic_cut${cut}` : 'clinic' } }
}
