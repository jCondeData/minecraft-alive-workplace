// Fletcher's Lodge, the recipe: assembles plan.mjs from the parts library (../parts) in passes (block-out and
// structure, roof, openings, depth and detail, interior, ground and light) and returns the structure for the
// minecraft-builder skill's runner. LEVEL = 1 or 2: Level II is Level I grown (every Level I cell it keeps is drawn by
// the same lines; the roof runs on along its ridge). Writes furniture.json beside it. Both levels: build.sh.
// CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, PALETTE, MASSES, OPENINGS, FURNITURE, STORE, TARGET, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, post, wallPanel, window, door, gableRoof, gableWall, bargeTrim, chimney, strawTarget,
  logStack, furnitureParts, logPen, porchGable,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level) {
  const c = new Canvas(SIZES[level])
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const at = l => (l ?? 1) <= level
  const pens = [MASSES.westPen, MASSES.eastPen].filter(p => at(p.level))
  const R = MASSES.roof

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // each pen: a rubble sill ring at y 0, plank floor inside, log courses y 1..4 notched at the corners
  for (const p of pens) {
    plinth(c, p.x0, p.z0, p.x1, p.z1, stone)
    floor(c, p.room.x0, p.room.z0, p.room.x1, p.room.z1, 0, P.floor)
    logPen(c, p, P.log)
    for (const x of p.ties) for (let z = p.room.z0; z <= p.room.z1; z++) c.set(x, p.y1, z, P.beam, { axis: 'z' }) // tie beams
  }
  if (at(2)) { // the breezeway: a plank floor between the pens, the plates carried across on log beams
    const B = MASSES.breezeway
    floor(c, B.x0, B.z0, B.x1, B.z1, 0, P.deck)
    for (let x = B.x0; x <= B.x1; x++) for (const z of [B.z0, B.z1, B.tie]) c.set(x, 4, z, P.beam, { axis: 'x' })
    for (const z of [B.z0, B.z1]) wallPanel(c, [B.x0, z], [B.x1, z], 1, 3, P.boards) // board walls, a door in each (pass 3)
  }
  // the porch: a deck the whole front, stripped posts
  const PO = MASSES.porch
  floor(c, PO.x0, PO.z0, PO.x1[level], PO.z1, 0, P.deck)
  for (const x of PO.posts[level]) post(c, x, PO.z0, 1, PO.eave - 1, P.beam)

  // --- Pass 2: roof ---------------------------------------------------------------------------------------------
  const roof = gableRoof(c, { ...R, along: R.along[level] }, P.roof)
  const ridgeZ = (R.across[0] + R.across[1]) / 2 // the ridge pole: a light log along the top, the dark roof's one line
  for (let x = R.along[level][0]; x <= R.along[level][1]; x++) c.set(x, roof.ridgeY, ridgeZ, P.beam, { axis: 'x' })
  for (let x = 0; x <= PO.kick[level]; x++) c.set(x, PO.eave, PO.z0, P.roof.stairs, { facing: 'south', half: 'bottom', shape: 'straight', waterlogged: 'false' }) // the porch's kick
  // gables: the pens' end walls carried up in logs under the roof (on Level II the West pen's east gable and the East
  // pen's west gable stand in the breezeway's attic)
  for (const p of pens) for (const x of p.gables[level])
    gableWall(c, 'x', x, R.across, p.y1 + 1, roof.under, (u) => (u > p.z0 && u < p.z1 ? [P.log, { axis: 'z' }] : null))
  // barge boards under the overhanging rakes at the two outer ends
  for (const x of [R.along[level][0], R.along[level][1]]) bargeTrim(c, 'x', x, R.across, roof.under, P.trim)
  if (at(2)) { // the mouth gable over the breezeway, a target in its pediment
    const G = MASSES.mouthGable
    const walls = (x, y, z) => !(c.name(x, y, z).endsWith('_log'))
    porchGable(c, G, P.roof, { beam: P.beam, face: [P.target, { power: '0' }] }, walls)
  }

  // --- Pass 3: openings -----------------------------------------------------------------------------------------
  for (const d of OPENINGS.doors) if (at(d.level)) door(c, d.at, d.facing, d.out, { wood: P.wood, hinge: d.hinge ?? 'left' })
  for (const w of OPENINGS.windows) if (at(w.level) && (!w.only || w.only === level)) window(c, w.cells, w.out, w.bare ? null : 'shutters', { glass: P.glass, wood: P.wood })

  // --- Pass 4: depth and detail: the chimneys and their hearths -------------------------------------------------
  for (const ch of MASSES.chimneys) {
    if (!at(ch.level)) continue
    chimney(c, ch.x, ch.z, 0, ch.top, stone, { breast: { axis: 'z', top: ch.breast, stair: P.stoneStair } })
    wallPanel(c, [ch.hearth, ch.z - 1], [ch.hearth, ch.z + 1], 1, 3, stone) // the fireplace's stone surround in the log wall
    c.set(ch.hearth, 4, ch.z, stone)
  }

  // --- Pass 5: interior (the plan's furniture) ---------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (!at(f.level)) continue
    if (f.kind === 'fixture' && !f.part) { c.set(...f.pos, f.block); continue }
    furnitureParts[f.part](c, f)
    if (f.plant) c.set(f.pos[0], f.pos[1] + 1, f.pos[2], f.plant)
  }
  if (at(STORE.level)) logStack(c, STORE.logs, P.rick, 'x')

  // --- Pass 6: ground and light -------------------------------------------------------------------------------------
  for (const r of OUTSIDE.ricks) if (at(r.level)) {
    const cells = []
    for (let x = r.x0; x <= r.x1; x++) { cells.push([x, 0, r.z]); if (x > r.x0 && x < r.x1) cells.push([x, 1, r.z]) }
    logStack(c, cells, P.rick, 'x')
  }
  logStack(c, OUTSIDE.porchPile, P.rick, 'z')
  strawTarget(c, TARGET.at, { bale: P.hay, face: P.target, post: `${P.wood}_fence` })
  for (const l of OUTSIDE.lanterns) if (at(l.level)) c.set(...l.at, 'lantern', { hanging: 'true', waterlogged: 'false' })
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const id = level === 1 ? 'fletchers_lodge' : `fletchers_lodge_${level}`
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  if (process.env.COUNT) console.error(`${id}: ${c.cells.size} blocks`)
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
