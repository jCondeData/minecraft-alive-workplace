// Trade Hall, the recipe (ROADMAP 49): assembles plan.mjs from the parts library in passes (block-out, openings,
// depth and detail, interior, ground) and writes furniture.json. CUT=<y> cuts everything above y (cutaways).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, JOB, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, doorway, gableRoof, gableWall, bargeTrim,
  flowerBed, stringCourse, roundShaft, steppedCap, slab, furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const H = MASSES.hall, T = MASSES.tower, B = MASSES.ball
  const { x0, x1, z0, z1 } = H.frame
  const rm = H.room

  // --- Pass 1: block-out ---------------------------------------------------------------------------------------
  plinth(c, x0, z0, x1, z1, stone)
  floor(c, rm.x0, rm.z0, rm.x1, rm.z1, 0, P.floor)
  wallPanel(c, [x0, z0], [x1, z0], 1, H.wallTop, P.wall)
  wallPanel(c, [x0, z1], [x1, z1], 1, H.wallTop, P.wall)
  wallPanel(c, [x0, z0], [x0, z1], 1, H.wallTop, P.wall)
  wallPanel(c, [x1, z0], [x1, z1], 1, H.wallTop, P.wall)
  for (const x of H.frontPosts) post(c, x, z0, 1, H.wallTop, P.frame)
  for (const x of H.backPosts) post(c, x, z1, 1, H.wallTop, P.frame)
  for (const z of H.westPosts) post(c, x0, z, 1, H.wallTop, P.frame)
  for (const z of H.eastPosts) post(c, x1, z, 1, H.wallTop, P.frame)
  beamRing(c, x0, z0, x1, z1, H.beamY, P.frame)
  for (const x of H.ties) for (let z = z0 + 1; z < z1; z++) c.set(x, H.beamY, z, P.frame, { axis: 'z' })

  // The tower: a stone foot, white shaft, slate bands, its loft floor, then the ball on a slate rim
  for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
    const corner = (x === T.x0 || x === T.x1) && (z === T.z0 || z === T.z1)
    if (!corner) c.set(x, 0, z, stone)
  }
  floor(c, T.room.x0, T.room.z0, T.room.x1, T.room.z1, 0, P.towerFloor)
  roundShaft(c, T.x0, T.z0, T.x1, T.z1, 1, T.top, P.wall)
  for (const y of T.bands) roundShaft(c, T.x0, T.z0, T.x1, T.z1, y, y, P.band)
  floor(c, T.room.x0, T.room.z0, T.room.x1, T.room.z1, T.loftY, P.floor)
  roundShaft(c, T.x0, T.z0, T.x1, T.z1, 1, 1, stone) // the tower's plinth course, as high as the hall's sills
  for (let y = B.red[0]; y <= B.red[1]; y++) roundShaft(c, T.x0, T.z0, T.x1, T.z1, y, y, P.ball.red)
  roundShaft(c, T.x0, T.z0, T.x1, T.z1, T.top, T.top, P.band)
  c.set(...B.button, P.ball.button) // the button in the black band, facing the street
  const [cx0, cz0, cx1, cz1, cy] = B.cap
  steppedCap(c, cx0, cz0, cx1, cz1, cy, P.roof, 'lightning_rod')

  // The hall roof, kept off the tower and ball, then the gables (the east one only where the tower isn't)
  const inTower = (x, z) => x >= T.x0 && z >= T.z0 && z <= T.z1
  const roof = gableRoof(c, H.roof, P.roof, (x, y, z) => !c.get(x, y, z) && !inTower(x, z))
  const gable = line => (u, y) => {
    if (c.get(line, y, u)) return null
    if (u === 7 && y >= 9) return [P.frame, { axis: 'y' }]
    if (y === 6 && (u === 4 || u === 10)) return [P.frame, { axis: 'x' }]
    return P.wall
  }
  gableWall(c, 'x', x0, [z0, z1], H.beamY + 1, roof.under, gable(x0))
  gableWall(c, 'x', x1, [z0, z1], H.beamY + 1, roof.under, gable(x1))

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: 'stone_brick_stairs' })
  doorway(c, OPENINGS.towerDoorway.at, OPENINGS.towerDoorway.height)
  const pots = { 'shop west': ['potted_red_tulip', 'potted_fern'], 'shop east': ['potted_fern', 'potted_red_tulip'] }
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: pots[w.name] ?? [], sill: 'stone_brick_stairs' })
  const L = OPENINGS.ladder
  c.clear(L.x, T.loftY, L.z)
  for (let y = L.y0; y <= L.y1; y++) c.set(L.x, y, L.z, 'ladder', { facing: L.facing, waterlogged: 'false' })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  bargeTrim(c, 'x', H.roof.along[0], H.roof.across, roof.under, `${P.trim}_stairs`)
  bargeTrim(c, 'x', H.roof.along[1], H.roof.across, roof.under, `${P.trim}_stairs`)
  c.clearBox(T.room.x0, T.loftY + 1, T.room.z0, T.room.x1, T.top, T.room.z1) // no barge board inside the tower room
  stringCourse(c, x0, z0, x1, z1, 0, P.band, (x, z) => x === fd.at[0] && z === z0) // a dark plinth course
  for (const z of [z0 - 1, z1 + 1]) for (let x = 0; x <= x1 + 1; x++) // soffits: the two-deep eaves boxed in under the tiles
    if (!c.get(x, H.beamY, z) && !inTower(x, z)) slab(c, x, H.beamY, z, `${P.trim}_slab`, 'top')
  const ch = MASSES.chimney // a stone stack up the back wall, out past the eaves
  for (let y = 1; y <= ch.top; y++) for (const x of [ch.x, ch.x + 1]) c.set(x, y, ch.z + 1, stone)
  c.set(ch.x, ch.top + 1, ch.z + 1, 'stone_brick_wall'); c.set(ch.x + 1, ch.top + 1, ch.z + 1, 'stone_brick_wall')
  for (const x of [4, 6]) c.set(x, 3, z0 - 1, `${P.banner}_wall_banner`, { facing: 'north' }) // banners either side of the door
  c.set(fd.at[0], H.wallTop, z0 - 1, 'lantern', { hanging: 'true', waterlogged: 'false' }) // under the eave
  c.set(fd.at[0], H.beamY, z0 - 1, P.frame, { axis: 'z' }) // the bracket it hangs from

  // --- Pass 4: interior -------------------------------------------------------------------------------------------
  const [jx, jy, jz] = JOB.at
  c.set(jx, jy, jz, JOB.block, { facing: JOB.facing })
  for (const x of JOB.bar) slab(c, x, jy, jz, P.counterTop, 'top')
  for (const f of FURNITURE) furnitureParts[f.part](c, f)

  // --- Pass 5: ground -----------------------------------------------------------------------------------------------
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  for (const [x, y, z] of OUTSIDE.path) c.set(x, y, z, 'gravel')
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `trade_hall_cut${cut}` : 'trade_hall' } }
}
