// Carpenter's Workshop (49, the rebuild), the recipe: assembles plan.mjs from the shared parts library (../parts) in
// passes (block-out and structure, crucks and roof, openings, depth and detail, interior, ground, light) and returns
// the structure for the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot).
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, doorway, gableRoof, gableWall, bargeTrim,
  catslideRoof, catslideTrim, hatchLadder, flowerBed, timberRack, pallet, stockShelf, jettyBrackets, hoistBeam,
  furnitureParts, slopeDormer, chimney, cruckTruss, cruckFoot, sawhorse,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const H = MASSES.hall, S = MASSES.shed
  const { x0, x1, z0, z1 } = H.posts
  const rm = H.room
  const fz = H.frontWallZ

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  plinth(c, x0, z0, x1, z1, stone) // the whole hall on rubble: porch floor and wall courses
  floor(c, rm.x0, rm.z0, rm.x1, rm.z1, 0, P.floor) // the shop's boards
  const pf = OPENINGS.porchFloor
  plinth(c, pf.x0, pf.z, pf.x1, pf.z, stone)
  plinth(c, S.floor.x0, S.floor.z0, S.floor.x1, S.floor.z1, stone)

  // walls: boards between the frame, the porch recess open to the road
  wallPanel(c, [x0, fz], [x0, z1], 1, H.beamY - 1, P.infill) // west
  wallPanel(c, [x1, fz], [x1, z1], 1, H.beamY - 1, P.infill) // east
  wallPanel(c, [x0 + 1, fz], [x1 - 1, fz], 1, H.beamY - 1, P.infill) // the shop's front, behind the porch
  wallPanel(c, [x0 + 1, z1], [x1 - 1, z1], 1, H.beamY - 1, P.infill) // back
  for (const z of H.sidePosts) { post(c, x0, z, 1, H.beamY - 1, P.frame); post(c, x1, z, 1, H.beamY - 1, P.frame) }
  for (const x of H.frontPosts) post(c, x, fz, 1, H.beamY - 1, P.frame)
  for (const x of H.backPosts) post(c, x, z1, 1, H.beamY - 1, P.frame)
  beamRing(c, x0, z0, x1, z1, H.beamY, P.frame) // the front side of it is the porch's tie beam
  floor(c, rm.x0, z0 + 1, rm.x1, rm.z1, H.beamY, P.loftFloor) // the loft floor, over the porch too

  // --- Pass 2: crucks and roof ---------------------------------------------------------------------------------
  const main = gableRoof(c, H.roof, P.roof)
  const T = H.trusses
  cruckTruss(c, T.front.line, H.roof.across, main.under, H.gableY, P.frame)
  cruckTruss(c, T.inner.line, H.roof.across, main.under, H.gableY, P.frame)
  const back = cruckTruss(c, T.back.line, H.roof.across, main.under, H.gableY, P.frame)
  // the front gable's boards set a block back behind its truss (the truss stands proud); the back gable's between its blades
  gableWall(c, 'z', T.front.infill, H.roof.across, H.gableY, main.under, () => P.infill)
  gableWall(c, 'z', T.back.line, H.roof.across, H.gableY, main.under, (u, y) => (back(u, y) ? null : P.infill))
  const mid = (H.roof.across[0] + H.roof.across[1]) / 2
  bargeTrim(c, 'z', H.roof.along[0], H.roof.across, main.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', H.roof.along[1], H.roof.across, main.under, `${P.trim}_stairs`)
  // the dormer in the west slope (the loft's window over the bed) and the glue stove's flue up the east wall
  slopeDormer(c, H.dormer, main.under, P.roof, P.infill, P.glass)
  chimney(c, H.flue.x, H.flue.z, 1, H.flue.top, stone)
  for (const [x, y, z, f] of OUTSIDE.feet) cruckFoot(c, [x, y, z], P.stoneStairs, f)
  for (const [x, y, z, f] of [...OUTSIDE.braces, ...OUTSIDE.elbows]) jettyBrackets(c, [[x, y, z]], `${P.trim}_stairs`, f)

  // the shed: a lean-to off the hall's east wall, on three posts, under the hall's eave
  const lean = catslideRoof(c, S.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  for (const [x, z] of S.posts) post(c, x, z, 1, lean.under[x] - 1, P.frame)
  catslideTrim(c, 'z', S.roof.along[0], [S.roof.across[0], S.roof.across[1]], S.roof.ridge, lean.under, `${P.trim}_stairs`, `${P.trim}_slab`)
  catslideTrim(c, 'z', S.roof.along[1], [S.roof.across[0], S.roof.across[1]], S.roof.ridge, lean.under, `${P.trim}_stairs`, `${P.trim}_slab`)

  // --- Pass 3: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.stoneStairs })
  door(c, OPENINGS.shedDoor.at, OPENINGS.shedDoor.facing, 'east', { wood: P.wood })
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
  const L = OPENINGS.ladder
  c.clear(L.x, L.top, L.z)
  for (let y = L.y0; y <= L.top; y++) c.set(L.x, y, L.z, 'ladder', { facing: L.facing, waterlogged: 'false' })

  // --- Pass 4: depth and detail ---------------------------------------------------------------------------------
  // the hoist: a beam out through the front gable over the loft window, its chain and lantern hanging before the porch
  hoistBeam(c, [mid, main.ridgeY - 2, T.front.infill], 'north', { log: P.frame, bracket: `${P.trim}_stairs`, reach: 3, drop: 3 })
  // the porch: the sawhorse and a stack of boards for sale
  sawhorse(c, OUTSIDE.sawhorse.a, OUTSIDE.sawhorse.b, `${P.wood}_fence`, P.beam)
  c.set(...OUTSIDE.sawhorse.board, `${P.stock.replace('_planks', '')}_slab`, { type: 'bottom', waterlogged: 'false' }) // a board on the trestle
  timberRack(c, OUTSIDE.porchLogs.cells, P.logs, 'z') // log ends to the street
  pallet(c, OUTSIDE.boards.cells, P.stock, OUTSIDE.boards.top.map(p => [...p, `${P.wood}_slab`]))

  // --- Pass 5: interior (the plan's furniture) ------------------------------------------------------------------
  for (const f of FURNITURE) (f.part === 'stockShelf' ? stockShelf : furnitureParts[f.part])(c, f)
  pallet(c, OUTSIDE.loftStock.cells, P.stock)
  timberRack(c, OUTSIDE.loftStock.logs, P.logs, 'z')
  timberRack(c, OUTSIDE.rack.cells, P.logs, 'z')
  timberRack(c, OUTSIDE.shopLogs.cells, P.logs, 'z')
  for (const [x, y, z] of OUTSIDE.toolRack.cells) c.set(x, y, z, 'tripwire_hook', { facing: OUTSIDE.toolRack.facing, attached: 'false', powered: 'false' })
  pallet(c, OUTSIDE.shopBoards.cells, P.stock, OUTSIDE.shopBoards.top.map(p => [...p, `${P.wood}_slab`]))
  pallet(c, OUTSIDE.shedPile.cells, P.stock, OUTSIDE.shedPile.top.map(p => [...p, `${P.wood}_slab`]))

  // the boards: every infill log laid along its own wall (the side walls run along z, the fronts and gables along x),
  // so the walls read as planed boards between the frame, not as posts
  for (const [k, v] of c.cells) {
    if (v.id !== `minecraft:${P.infill}` || v.props.axis) continue
    const x = Number(k.split(',')[0])
    v.props.axis = x === x0 || x === x1 ? 'z' : 'x'
  }

  // --- Pass 6: ground ---------------------------------------------------------------------------------------------
  const fb = OUTSIDE.flowerBed
  for (let z = fb.z0; z <= fb.z1; z++) flowerBed(c, fb.x, fb.x, z, [fb.plants[(z - fb.z0) % fb.plants.length]])
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  console.error(`blocks: ${canvas.cells.size}`)
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `carpenters_workshop_cut${cut}` : 'carpenters_workshop' } }
}
