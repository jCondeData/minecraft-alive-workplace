// Gazebo (49 rebuild), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out and
// structure, openings, depth and detail, furnishing, ground, light) and returns the structure for the minecraft-builder
// skill's runner. A decoration: it records no furniture spots (furniture.json stays empty).
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, CENTRE, CUT, PALETTE, ROOF, POSTS, BAYS, TABLE, LANTERN, BEDS } from './plan.mjs'
import {
  Canvas, stair, post, tentRoof, octLevel, faceRun, sideBench, louvre, railing, hangingLantern, tieredBed, inward,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const [cx, cz] = CENTRE
  const level = octLevel(cx, cz, CUT)
  const [W, , D] = SIZE

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // the dais: a rosette of stone rings, and a skirt of steps climbing in from every side
  const floorOf = { 0: P.dais.heart, 1: P.dais.inner, 2: P.dais.field, 3: P.dais.field, 4: P.dais.rim }
  for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
    const L = level(x, z)
    if (L <= 4) c.set(x, 0, z, floorOf[L])
    else if (L === 5) stair(c, x, 0, z, P.dais.step, inward(x, z, cx, cz))
  }
  // eight white posts and the beam ring over them
  for (const [x, z] of POSTS) post(c, x, z, 1, ROOF.postTop, P.post)
  for (let x = 0; x < W; x++) for (let z = 0; z < D; z++)
    if (level(x, z) === 4) { // a white beam, its grain along the face it spans (upright on the diagonal faces)
      const dx = Math.abs(x - cx), dz = Math.abs(z - cz)
      c.set(x, ROOF.beamY, z, P.beam, { axis: dx === 4 && dz < 3 ? 'z' : dz === 4 && dx < 3 ? 'x' : 'y' })
    }
  // the slate tent: bell-cast, lined with birch boards inside
  tentRoof(c, { cx, cz, cut: CUT, heights: ROOF.heights }, P.roof, { lining: P.lining, keep: (x, y, z) => !(y === ROOF.beamY && level(x, z) === 4) })

  // --- Pass 2: openings (the bays) ----------------------------------------------------------------------------------
  for (const b of BAYS) {
    if (b.fill === 'urn') {
      const [x, z] = b.cell
      c.set(x, 1, z, P.urn.stand, { up: 'true', north: 'none', south: 'none', east: 'none', west: 'none', waterlogged: 'false' })
      c.set(x, 2, z, P.urn.pots[b.pot])
      continue
    }
    const run = faceRun(b.from, b.to)
    if (b.fill === 'bench') {
      railing(c, run.map(([x, z]) => [x, 1, z]), P.wood)
      const seats = run.map(([x, z]) => (b.from[0] === b.to[0] ? [b.bench.line, z] : [x, b.bench.line]))
      sideBench(c, seats, 1, b.bench.facing, `${P.wood}_stairs`)
    }
  }

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------------
  // brackets: on every straight bay, an upside-down stair under the beam beside each post, a shallow arch
  for (const b of BAYS) {
    if (!b.from) continue
    const run = faceRun(b.from, b.to)
    for (const [i, toward] of [[0, b.from], [run.length - 1, b.to]]) {
      const [x, z] = run[i]
      const facing = toward[0] < x ? 'west' : toward[0] > x ? 'east' : toward[1] < z ? 'north' : 'south'
      stair(c, x, ROOF.postTop, z, `${P.wood}_stairs`, facing, 'top')
    }
  }
  // the azalea swags in the corner bays, hung from the beam
  for (const b of BAYS) if (b.fill === 'urn') {
    const [x, z] = b.cell
    c.set(x, ROOF.postTop, z, P.swag, { persistent: 'true', distance: '1', waterlogged: 'false' })
  }
  // the cupola on the apex, its lantern lit inside, a lightning rod on top
  louvre(c, cx, cz, ROOF.cupolaY, { base: P.lining, post: `${P.wood}_fence`, roof: P.cap, lamp: true })
  c.set(cx, ROOF.cupolaY + 3, cz, P.finial, { facing: 'up', powered: 'false', waterlogged: 'false' })

  // --- Pass 4: furnishing ----------------------------------------------------------------------------------------------
  const [tx, ty, tz] = TABLE
  c.set(tx, ty, tz, `${P.wood}_fence`)
  c.set(tx, ty + 1, tz, P.tablecloth)

  // --- Pass 5: ground -----------------------------------------------------------------------------------------------------
  for (const bed of BEDS) {
    const [bx, bz] = bed.corner
    const cells = []
    for (let x = 0; x < W; x++) for (let z = 0; z < D; z++)
      if (level(x, z) >= 6 && Math.abs(x - bx) <= 3 && Math.abs(z - bz) <= 3) cells.push([x, z])
    cells.sort((a, b) => (Math.abs(a[0] - bx) + Math.abs(a[1] - bz)) - (Math.abs(b[0] - bx) + Math.abs(b[1] - bz)))
    const plantAt = new Map(cells.map(([x, z], i) => [`${x},${z}`, bed.plants[i % bed.plants.length]]))
    tieredBed(c, cells, 1, { base: P.soil, soil: P.soil, plants: (x, z) => plantAt.get(`${x},${z}`) })
  }

  // --- Pass 6: light ----------------------------------------------------------------------------------------------------
  hangingLantern(c, ...LANTERN.at, 'lantern', LANTERN.chain)
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": []}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `gazebo_cut${cut}` : 'gazebo' } }
}
