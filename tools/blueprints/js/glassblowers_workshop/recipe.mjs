// The Glassblower's Workshop (50.7), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground) and returns the structure for the
// minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded) for
// tools/blueprints/rebuild.py. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, JOB, FURNITURE, OUTSIDE, HOT_STOCK } from './plan.mjs'
import {
  Canvas, mix, stair, slab, plinth, floor, post, wallPanel, window, door, doorway, gableRoof, gableWall, bargeTrim,
  hood, furnitureParts, doubleChest, lampOn, clumped,
} from '../parts/index.mjs'
import { glassCone, bullseye, kilnSiege, eyebrow } from '../parts/glass_cone.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = clumped(mix(P.stone, 50), 2)
  const C = MASSES.cone, S = MASSES.shop, D = MASSES.shed
  const { x0, x1, z0, z1 } = S.walls

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // The Cone first: everything else meets it. Its two doorways are kept out of the shell.
  const holes = new Set()
  for (const o of [OPENINGS.coneArch, OPENINGS.shedDoor]) for (let y = o.at[1]; y < o.at[1] + 2; y++) holes.add(`${o.at[0]},${y},${o.at[2]}`)
  const cone = glassCone(c, C.cx, C.cz, { y0: 0, stages: C.stages, baseTop: C.baseTop }, { ...P.cone, plinth: stone },
    (x, y, z) => !holes.has(`${x},${y},${z}`))
  for (let x = C.cx - 3; x <= C.cx + 3; x++) for (let z = C.cz - 3; z <= C.cz + 3; z++)
    if (cone.inside(x, 1, z)) c.set(x, 0, z, P.hotFloor)

  // The Shop: a stone plinth with a skirt a block out at the sides (the sills stand on it), boards inside
  plinth(c, x0, z0, x1, z1, stone)
  for (let z = z0; z <= z1; z++) { c.set(x0 - 1, 0, z, stone); c.set(x1 + 1, 0, z, stone) }
  for (let x = x0; x <= x1; x++) if (x !== OPENINGS.frontDoor.at[0]) c.set(x, 0, 0, stone) // the kerb under the stall boards
  for (let x = x0 + 1; x <= x1 - 1; x++) for (let z = z0 + 1; z <= z1; z++) if (!cone.has(x, 1, z)) c.set(x, 0, z, P.shopFloor)
  // walls: cream panels between dark posts, a beam ring at y 4 round the front and sides (the back is the cone)
  wallPanel(c, [x0, z0], [x1, z0], 1, 3, P.infill)
  wallPanel(c, [x0, z0], [x0, z1], 1, S.kneeY, P.infill)
  wallPanel(c, [x1, z0], [x1, z1], 1, S.kneeY, P.infill)
  for (const x of S.frontPosts) post(c, x, z0, 1, 3, P.frame)
  for (const z of S.sidePosts) { post(c, x0, z, 1, 3, P.frame); post(c, x1, z, 1, 3, P.frame) }
  for (let x = x0; x <= x1; x++) c.set(x, S.beamY, z0, P.frame, { axis: x === x0 || x === x1 ? 'y' : 'x' })
  for (let z = z0 + 1; z <= z1; z++) { c.set(x0, S.beamY, z, P.frame, { axis: 'z' }); c.set(x1, S.beamY, z, P.frame, { axis: 'z' }) }
  // a tie beam across the shop at the side posts' line (the lamp over the walk hangs from it)
  for (let x = x0 + 1; x <= x1 - 1; x++) c.set(x, S.beamY, 4, P.frame, { axis: 'x' })

  // The Shop's roof runs from the front overhang into the cone's flank; nothing of it enters the cone
  const roof = gableRoof(c, S.roof, P.roof, (x, y, z) => !cone.has(x, y, z))
  // the front gable: cream panels, two studs, a king post over the bullseye
  gableWall(c, 'z', z0, [x0, x1], S.kneeY, roof.under, (u, y) => {
    if ((u === 5 || u === 9) && y <= 7) return [P.frame, { axis: 'y' }]
    if (u === 7 && y === 9) return [P.frame, { axis: 'y' }]
    return P.infill
  })
  // the attic between the shop's roof and the cone: its side planes closed in cream up to the roof
  for (let z = z0 + 1; z <= z1; z++) for (const x of [x0, x1]) for (let y = S.kneeY; y < roof.under[x]; y++)
    if (!cone.has(x, y, z) && !c.get(x, y, z)) c.set(x, y, z, P.infill)

  // The Fuel Shed: a stone floor, board ends, posts at the open west face, a slate lean-to up against the cone
  plinth(c, D.x0, D.z0, D.x1, D.z1, stone)
  for (const z of [D.z0, D.z1]) {
    post(c, D.x0, z, 1, D.roofYs[0] - 1, P.frame)
    for (let x = D.x0 + 1; x <= 3; x++) {
      if (cone.has(x, 1, z) || (z === D.z0 && x === x0)) continue
      const top = x <= D.x1 ? D.roofYs[x] - 1 : D.roofYs[D.x1]
      for (let y = 1; y <= top; y++) if (!c.get(x, y, z)) c.set(x, y, z, `${P.joinery}_planks`)
    }
  }
  for (let z = D.z0; z <= D.z1; z++) {
    for (let x = D.x0; x <= D.x1; x++) if (!c.get(x, D.roofYs[x], z)) stair(c, x, D.roofYs[x], z, P.roof.stairs, 'east')
    if (!cone.has(3, 5, z) && !c.get(3, 5, z) && !c.get(3, 6, z)) stair(c, 3, 6, z, P.roof.stairs, 'east') // where the cone curves away
  }

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: 'stone_brick_stairs' })
  doorway(c, OPENINGS.coneArch.at, OPENINGS.coneArch.height)
  const sd = OPENINGS.shedDoor
  door(c, sd.at, sd.facing, 'west', { wood: P.wood })
  for (const w of OPENINGS.shopWindows) {
    window(c, w.cells, w.out, 'sill', { glass: P.glass, wood: P.joinery, sill: `${P.joinery}_slab` }) // the stall board
    for (const [x, y, z] of w.transom) c.set(x, y, z, P.tintA) // the trade's colour in the transom
  }
  for (const w of OPENINGS.sideWindows) window(c, w.cells, w.out, 'sill', { glass: P.glass, wood: P.joinery, pots: [w.pot] })
  for (const w of OPENINGS.coneWindows) {
    for (const [x, y, z] of w.cells) c.set(x, y, z, P.glass)
    eyebrow(c, w.brow, w.out, P.cone.lipStairs)
  }
  for (const [x, y, z] of OPENINGS.benchLight) c.set(x, y, z, P.glass)
  const be = OPENINGS.bullseye
  bullseye(c, 'z', be.line, be.u, be.y, { eye: P.tintB, ring: P.tintA, infill: P.infill })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  hood(c, fd.at[0], S.beamY, 0, P.roof) // a slate hood over the door, its lantern under it
  bargeTrim(c, 'z', S.roof.along[0], S.roof.across, roof.under, `${P.trim}_stairs`)
  // brackets under the shop's eaves at the posts (upside-down stairs, backs to the wall)
  for (const z of S.sidePosts) {
    stair(c, x0 - 1, S.beamY, z, `${P.trim}_stairs`, 'east', 'top')
    stair(c, x1 + 1, S.beamY, z, `${P.trim}_stairs`, 'west', 'top')
  }

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  kilnSiege(c, JOB.at, JOB.facing, { brick: P.cone.body, slabId: P.brickSlab })
  for (const f of FURNITURE) {
    if (f.part === 'job') { c.set(...f.pos, f.block, { facing: f.facing }); continue }
    if (f.part === 'doubleChest') { doubleChest(c, f.pos, f.facing, f.along); continue }
    if (f.part === 'lampOn') { lampOn(c, f); continue }
    furnitureParts[f.part](c, f)
  }
  for (const [at, id] of HOT_STOCK) c.set(...at, id)

  // --- Pass 5: ground ---------------------------------------------------------------------------------------------
  for (const [x, y, z] of OUTSIDE.sand) c.set(x, y, z, P.sand)
  for (const [x, y, z] of OUTSIDE.wood) c.set(x, y, z, P.log, { axis: 'z' })
  c.set(...OUTSIDE.shedLamp, 'lantern', { hanging: 'true', waterlogged: 'false' }) // by the Hot Shop's side door
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `glassblowers_workshop_cut${cut}` : 'glassblowers_workshop' } }
}
