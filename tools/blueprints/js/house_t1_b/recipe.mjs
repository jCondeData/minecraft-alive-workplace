// House T1 (Version B), the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49.1) in passes (block-out and structure, openings,
// depth and detail, interior, ground, light) and returns the structure for the minecraft-builder skill's runner.
// Also writes furniture.json (every furniture spot the parts recorded) for tools/blueprints/house_t1_b.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, gableRoof, gableWall, chimney, hood, flowerBed,
  woodpile, railing, flight, dormer, bargeTrim, furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 7)
  const H = MASSES.hall, K = MASSES.kitchen
  const { x0, x1, z0, z1 } = H.posts
  const dB = H.daub, rm = H.room

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  plinth(c, x0, z0, x1, z1, stone)
  floor(c, rm.x0, rm.z0, rm.x1, rm.z1, 0, P.floor)
  plinth(c, K.walls.x0, K.walls.z0, K.walls.x1, K.walls.z1, stone)
  floor(c, K.room.x0, K.room.z0, K.room.x1, K.room.z1, 0, P.kitchenFloor)

  // Hall: daub panels set a block back from the frame, posts proud on the plinth, a beam ring tying them
  wallPanel(c, [dB.x0, dB.z0], [dB.x1, dB.z0], 1, 3, P.infill)
  wallPanel(c, [dB.x0, dB.z1], [dB.x1, dB.z1], 1, 3, P.infill)
  wallPanel(c, [dB.x0, dB.z0], [dB.x0, dB.z1], 1, 5, P.infill) // west wall up through the knee wall
  wallPanel(c, [dB.x1, dB.z0], [dB.x1, dB.z1], 1, 5, P.infill) // east wall
  for (const x of H.frontPosts) post(c, x, z0, 1, 3, P.frame)
  for (const x of H.backPosts) post(c, x, z1, 1, 3, P.frame)
  for (const z of H.westPosts) post(c, x0, z, 1, 3, P.frame)
  for (const z of H.eastPosts) post(c, x1, z, 1, 3, P.frame)
  beamRing(c, x0, z0, x1, z1, H.beamY, P.frame)
  floor(c, dB.x0 + 1, dB.z0, dB.x1 - 1, dB.z1, H.beamY, P.floor) // the loft floor, out to the gables

  // Kitchen: rubble walls flush on the plinth, a dark beam on top
  wallPanel(c, [K.walls.x0 + 1, K.walls.z0], [K.walls.x1, K.walls.z0], 1, 3, stone)
  wallPanel(c, [K.walls.x0 + 1, K.walls.z1], [K.walls.x1, K.walls.z1], 1, 3, stone)
  wallPanel(c, [K.walls.x1, K.walls.z0], [K.walls.x1, K.walls.z1], 1, 3, stone)
  beamRing(c, K.walls.x0, K.walls.z0, K.walls.x1, K.walls.z1, K.beamY, P.frame)
  c.set(K.walls.x0, K.beamY, K.walls.z0, P.frame, { axis: 'y' })

  // Roofs: the Hall's steep tile roof first, the Kitchen's lower one only where nothing is drawn yet
  const main = gableRoof(c, H.roof, P.roof)
  const wing = gableRoof(c, K.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  // Under the kitchen roof the Hall's eave stairs become full tiles: no quarter gaps from the kitchen attic
  for (let z = K.room.z0; z <= K.room.z1; z++) c.set(x1, H.roof.eaveY, z, P.roof.full)

  // Gables: the Hall's jetty out to the post line above the beam, studs, a collar, a king post
  const studs = (u, y) => (u === 2 || u === 6) && y <= 7
  gableWall(c, 'z', z0, [x0, x1], H.beamY + 1, main.under, (u, y) => {
    if (studs(u, y)) return [P.frame, { axis: 'y' }]
    if (y === 7 && u >= 3 && u <= 5) return [P.frame, { axis: 'x' }]
    if (u >= 3 && u <= 5 && y >= 8 && !(u === 4 && y === 8)) return [P.frame, { axis: 'y' }] // timbered apex
    if ((u === 1 || u === 7) && y === 5) return [P.frame, { axis: 'z' }] // the rake's foot
    return P.infill
  })
  gableWall(c, 'z', z1, [x0, x1], H.beamY + 1, main.under, (u, y) => {
    if (u === 4) return null // the chimney column
    if (studs(u, y)) return [P.frame, { axis: 'y' }]
    if (y === 7 && (u === 3 || u === 5)) return [P.frame, { axis: 'x' }]
    if ((u === 3 || u === 5) && y >= 8) return [P.frame, { axis: 'y' }]
    if ((u === 1 || u === 7) && y === 5) return [P.frame, { axis: 'z' }]
    return P.infill
  })
  gableWall(c, 'x', K.walls.x1, [K.roof.across[0], K.roof.across[1]], K.beamY + 1, wing.under, () => P.infill)

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step })
  const kd = OPENINGS.kitchenDoorway
  for (let y = kd.at[1]; y < kd.at[1] + kd.height; y++) c.clear(kd.at[0], y, kd.at[2])
  const pots = { 'hall front west': ['potted_poppy', 'potted_azure_bluet'], 'hall front east': ['potted_azure_bluet', 'potted_poppy'], 'hall west': ['potted_oxeye_daisy'] }
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: pots[w.name] ?? [] })
  const st = OPENINGS.stairs
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railing(c, [[5, 5, 5], [5, 5, 6], [5, 5, 7], [5, 5, 8], [6, 5, 5]], P.wood)

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  hood(c, fd.at[0], H.beamY, z0 - 1, P.roof)
  // the hearth: a stone surround in the back wall, the stack up the gable's middle and out past the ridge
  wallPanel(c, [3, dB.z1], [5, dB.z1], 1, 3, stone)
  wallPanel(c, [4, z1], [4, z1], 1, main.ridgeY - 1, stone)
  chimney(c, 4, z1 + 1, 0, main.ridgeY, stone, { breast: { axis: 'x', top: 4 } })
  c.set(4, main.ridgeY, z1 + 1, 'stone_bricks') // a dressed cap under the smoke
  // a dormer on the long west slope over the hall's west window: the loft's window, under its own little gable
  dormer(c, 6, P.roof, P.infill, P.glass, P.frame)

  // the loft's ceiling: pale boards lining the underside of the tiles (stepped with the steep pitch), leaving the
  // dormer's window clear
  for (let z = dB.z0; z <= dB.z1; z++) {
    const dormerBay = z >= 5 && z <= 7
    for (const [x, y] of [[2, 7], [3, 8], [3, 9], [4, 10], [4, 11], [5, 8], [5, 9], [6, 7]]) {
      if (x === 2 && dormerBay) continue
      c.set(x, y, z, P.lining)
    }
  }

  // barge boards under the overhanging rakes: the Hall's front and back, the Kitchen's east gable (upside-down stairs)
  bargeTrim(c, 'z', H.roof.along[0], H.roof.across, main.under, `${P.trim}_slab`)
  bargeTrim(c, 'z', H.roof.along[1], H.roof.across, main.under, `${P.trim}_slab`)
  bargeTrim(c, 'x', K.roof.along[1], K.roof.across, wing.under, `${P.trim}_stairs`)  // 45 degrees: stairs, not slabs

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  for (const f of FURNITURE) furnitureParts[f.part](c, f)

  // --- Pass 5: ground -----------------------------------------------------------------------------------------------
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  woodpile(c, OUTSIDE.woodpile.x, OUTSIDE.woodpile.z0, OUTSIDE.woodpile.z1, 'spruce_log')
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `house_t1_b_cut${cut}` : 'house_t1_b' } }
}
