// Trade Hall, the recipe (ROADMAP 49): assembles plan.mjs from the parts library in passes (block-out, openings,
// depth and detail, interior, ground) and writes furniture.json. CUT=<y> cuts everything above y (cutaways).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, JOB, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, wallPanel, window, door, doorway, gableRoof, gableWall, bargeTrim,
  flowerBed, roundShaft, steppedCap, furnitureParts, slab,
} from '../parts/index.mjs'
import { proudFrame, shaftBand, footSkirt, seatCrossGable } from '../parts/trade_hall_frame.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const H = MASSES.hall, Bay = MASSES.bay, T = MASSES.tower
  const W = H.panels, F = H.frame, rm = H.room
  const inTower = (x, z) => x >= T.x0 && x <= T.x1 && z >= T.z0 && z <= T.z1
  const [ba0, ba1] = Bay.roof.across
  const bayU = x => Bay.roof.eaveY + Math.min(x - ba0, ba1 - x) // the bay roof's stair y in column x
  const inBay = (x, y, z) => x >= ba0 && x <= ba1 && z >= F.z1 && y <= bayU(x)

  // --- Pass 1: block-out ---------------------------------------------------------------------------------------
  // The hall: a stone plinth under the whole frame, boards inside, white panels, the frame one block proud of them
  plinth(c, F.x0, F.z0, F.x1, F.z1, stone)
  plinth(c, Bay.frame.x0, F.z1, Bay.frame.x1, Bay.frame.z1, stone)
  floor(c, rm.x0, rm.z0, rm.x1, rm.z1, 0, P.floor)
  floor(c, Bay.room.x0, W.z1, Bay.room.x1, Bay.room.z1, 0, P.floor)
  for (const [a, b] of [[[W.x0, W.z0], [W.x1, W.z0]], [[W.x0, W.z1], [W.x1, W.z1]], [[W.x0, W.z0], [W.x0, W.z1]], [[W.x1, W.z0], [W.x1, W.z1]]])
    wallPanel(c, a, b, 1, H.beamY, P.wall)
  const bp = Bay.panels
  for (const [a, b] of [[[bp.x0, W.z1 + 1], [bp.x0, bp.z1]], [[bp.x1, W.z1 + 1], [bp.x1, bp.z1]], [[bp.x0, bp.z1], [bp.x1, bp.z1]]])
    wallPanel(c, a, b, 1, H.beamY, P.wall)
  const keepT = (x, z) => !inTower(x, z)
  proudFrame(c, 'north', F.z0, [F.x0, 11], H.frontPosts, 1, H.wallTop, H.beamY, P.frame, keepT)
  proudFrame(c, 'south', F.z1, [F.x0, F.x1], H.backPosts, 1, H.wallTop, H.beamY, P.frame)
  proudFrame(c, 'west', F.x0, [F.z0, F.z1], H.westPosts, 1, H.wallTop, H.beamY, P.frame)
  proudFrame(c, 'east', F.x1, [6, F.z1], H.eastPosts, 1, H.wallTop, H.beamY, P.frame)
  const bf = Bay.frame
  proudFrame(c, 'west', bf.x0, [F.z1, bf.z1], [F.z1, bf.z1], 1, H.wallTop, H.beamY, P.frame)
  proudFrame(c, 'east', bf.x1, [F.z1, bf.z1], [F.z1, bf.z1], 1, H.wallTop, H.beamY, P.frame)
  proudFrame(c, 'south', bf.z1, [bf.x0, bf.x1], [bf.x0, bf.x1], 1, H.wallTop, H.beamY, P.frame)
  for (const x of H.ties) for (let z = W.z0; z <= W.z1; z++) c.set(x, H.beamY, z, P.frame, { axis: 'z' })

  // The tower: a stone foot and plinth course, white shaft, the loft floor, timber posts up the drum, a beam ring
  for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
    const corner = (x === T.x0 || x === T.x1) && (z === T.z0 || z === T.z1)
    if (!corner) c.set(x, 0, z, stone)
  }
  floor(c, T.room.x0, T.room.z0, T.room.x1, T.room.z1, 0, P.towerFloor)
  roundShaft(c, T.x0, T.z0, T.x1, T.z1, 1, T.top, P.wall)
  roundShaft(c, T.x0, T.z0, T.x1, T.z1, 1, 1, stone)
  for (let y = 1; y <= T.top; y++) c.set(T.x0, y, T.z1, P.wall) // the corner the hall's gable meets, closed
  floor(c, T.room.x0, T.room.z0, T.room.x1, T.room.z1, T.loftY, P.floor)
  // the chamfers filled out as corner columns: stone quoins under the band, dark-oak posts above it
  for (const [x, z] of [[T.x0, T.z0], [T.x1, T.z0], [T.x1, T.z1]]) {
    for (let y = 1; y < T.bandY; y++) c.set(x, y, z, stone)
    for (let y = T.postsFrom; y < T.top; y++) c.set(x, y, z, P.frame, { axis: 'y' })
  }
  for (let y = T.postsFrom; y < T.top; y++) c.set(T.x0, y, T.z1, P.frame, { axis: 'y' })
  for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
    const edge = x === T.x0 || x === T.x1 || z === T.z0 || z === T.z1
    const corner = (x === T.x0 || x === T.x1) && (z === T.z0 || z === T.z1)
    if (edge) c.set(x, T.top, z, P.frame, { axis: corner ? 'y' : z === T.z0 || z === T.z1 ? 'x' : 'z' })
  }
  const [cx0, cz0, cx1, cz1, cy] = T.cap
  steppedCap(c, cx0, cz0, cx1, cz1, cy, P.roof, 'lightning_rod')

  // The chimney: a stone stack up the east gable, through the rake
  const ch = MASSES.chimney
  for (let y = 0; y <= ch.top; y++) for (const x of [ch.x, ch.x + 1]) for (const z of [ch.z, ch.z + 1]) c.set(x, y, z, stone)
  for (const x of [ch.x, ch.x + 1]) c.set(x, ch.top + 1, ch.z, 'stone_brick_wall')

  // The hall roof (kept off the tower, the chimney and the bay), then the ledger bay's cross-gable out of its back slope
  const roof = gableRoof(c, H.roof, P.roof, (x, y, z) => !c.get(x, y, z) && !inTower(x, z) && !inBay(x, y, z))
  const mainY = z => roof.under[z]
  const bayRoof = gableRoof(c, Bay.roof, P.roof, (x, y, z) => !c.get(x, y, z) && (z > F.z1 - 1 || y > mainY(z)))
  seatCrossGable(c, Bay.roof.across[0], Bay.roof.across[1], Bay.roof.along[0], F.z1 - 1, mainY, x => bayRoof.under[x], P.roof.full)
  // the panel lines between the beam and the roof, closed
  for (let x = W.x0; x <= W.x1; x++) for (const z of [W.z0, W.z1])
    for (let y = H.beamY; y < roof.under[z]; y++) if (!c.get(x, y, z)) c.set(x, y, z, P.wall)
  // gables: white panels, with the frame's posts, collar and king post one block proud of them
  const gable = (u, y) => P.wall
  gableWall(c, 'x', W.x0, [W.z0, W.z1], H.beamY + 1, roof.under, gable)
  gableWall(c, 'x', W.x1, [T.z1 + 1, W.z1], H.beamY + 1, roof.under, gable)
  gableWall(c, 'z', bp.z1, [bp.x0, bp.x1], H.beamY + 1, bayRoof.under, gable)
  for (const line of [F.x0, F.x1]) {
    for (const z of H.gablePosts) for (let y = H.beamY + 1; y < roof.under[z]; y++)
      if (!inTower(line, z) && !c.get(line, y, z)) c.set(line, y, z, P.frame, { axis: 'y' })
    for (let z = H.gablePosts[0] + 1; z < H.gablePosts[1]; z++) if (!c.get(line, H.collarY, z)) c.set(line, H.collarY, z, P.frame, { axis: 'z' })
    for (let y = H.collarY + 1; y < roof.under[H.kingZ]; y++) if (!c.get(line, y, H.kingZ)) c.set(line, y, H.kingZ, P.frame, { axis: 'y' })
  }

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.stoneStairs })
  doorway(c, OPENINGS.towerDoorway.at, OPENINGS.towerDoorway.height)
  const A = OPENINGS.bayArch
  for (let x = A.x0; x <= A.x1; x++) for (let y = 1; y <= A.y1; y++) c.clear(x, y, A.z)
  slab(c, A.x0, A.y1, A.z, `${P.trim}_slab`, 'top'); slab(c, A.x1, A.y1, A.z, `${P.trim}_slab`, 'top') // the arch's shoulders
  const pots = {
    'shop west': ['potted_red_tulip', 'potted_fern', 'potted_red_tulip'], 'shop east': ['potted_red_tulip', 'potted_fern', 'potted_red_tulip'],
    'bay back': ['potted_fern', null, 'potted_fern'],
  }
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: pots[w.name] ?? [], sill: P.stoneStairs })
  const L = OPENINGS.ladder
  c.clear(L.x, T.loftY, L.z)
  for (let y = L.y0; y <= L.y1; y++) c.set(L.x, y, L.z, 'ladder', { facing: L.facing, waterlogged: 'false' })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  bargeTrim(c, 'x', H.roof.along[0], H.roof.across, roof.under, `${P.trim}_stairs`)
  bargeTrim(c, 'x', H.roof.along[1], H.roof.across, roof.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', Bay.roof.along[1], Bay.roof.across, bayRoof.under, `${P.trim}_stairs`)
  for (let x = T.room.x0; x <= T.room.x1; x++) for (let z = T.room.z0; z <= T.room.z1; z++) for (let y = 1; y <= T.top; y++)
    if (c.name(x, y, z).endsWith(`${P.trim}_stairs`)) c.clear(x, y, z) // no barge board inside the tower
  // the tower's band: a corbelled dark ring at the loft floor with the white boss facing the street, its battered foot
  shaftBand(c, T.x0, T.z0, T.x1, T.z1, T.bandY, P.band, P.bandStairs, (x, z) => x < T.x0)
  for (const [x, z] of [[T.x0, T.z0], [T.x0, T.z1]]) c.set(x, T.bandY, z, P.band)
  c.set(...T.button, P.button)
  footSkirt(c, T.x0, T.z0, T.x1, T.z1, 0, P.stoneStairs)
  // the porch: banners on the posts either side of the door, a lantern hung from the beam over it
  for (const x of [5, 7]) c.set(x, 3, F.z0 - 1, `${P.banner}_wall_banner`, { facing: 'north' })
  c.set(fd.at[0], H.wallTop, F.z0, 'lantern', { hanging: 'true', waterlogged: 'false' })

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
