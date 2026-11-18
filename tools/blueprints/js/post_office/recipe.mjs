// Post Office (the Signal Tower), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground) and returns the structure for the
// minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded) for
// tools/blueprints/rebuild.py. Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything
// above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, JOB, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, wallPanel, window, door, doorway, gableRoof, gableWall, bargeTrim, chimney, hood,
  stringCourse, quoins, quoinsLS, plinthCourse, faceCells, corbelRing, pyramidRoof, ladderShaft, lampPost, mapWall,
  furnitureParts, pigeonholes, tieBeams, pillarBox, spireTip,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.plinth, 11)
  const T = MASSES.tower, H = MASSES.hall, W = H.walls, R = H.room
  const inTower = (x, z) => x >= T.x0 && x <= T.x1 && z >= T.z0 && z <= T.z1

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // the plinth under both masses, a course of it proud of every hall wall, floors on it
  plinth(c, W.x0, W.z0, W.x1, W.z1, stone)
  plinth(c, T.x0, T.z0, T.x1, T.z1, stone)
  // (the front flanks only: the street face, carrying the window ledges)
  const proud = [...faceCells('x', W.z0, W.x0, T.x0 - 1, 'north'), ...faceCells('x', W.z0, T.x1 + 1, W.x1, 'north')]
  plinthCourse(c, proud, 0, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, 0, P.lobbyFloor)

  // Hall: brick walls 4 high, a stone band at the wall top, long-and-short quoins at the corners
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, H.wallTop, P.brick)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, H.wallTop, P.brick)
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, H.wallTop, P.brick)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, H.wallTop, P.brick)
  quoinsLS(c, [W.x0, W.z0], [1, 0], [0, 1], 1, H.band - 1, P.dressing)
  quoinsLS(c, [W.x1, W.z0], [-1, 0], [0, 1], 1, H.band - 1, P.dressing)
  quoinsLS(c, [W.x0, W.z1], [1, 0], [0, -1], 1, H.band - 1, P.dressing)
  quoinsLS(c, [W.x1, W.z1], [-1, 0], [0, -1], 1, H.band - 1, P.dressing)
  stringCourse(c, W.x0, W.z0, W.x1, W.z0, H.band, P.dressing) // the street front's band (the lintel over its windows)

  // Tower: brick walls to the cornice, quoins up the front corners, a stone band at the floor line, the room's floor
  wallPanel(c, [T.x0, T.z0], [T.x1, T.z0], 1, T.top, P.brick)
  wallPanel(c, [T.x0, T.z1], [T.x1, T.z1], 1, T.top, P.brick)
  wallPanel(c, [T.x0, T.z0], [T.x0, T.z1], 1, T.top, P.brick)
  wallPanel(c, [T.x1, T.z0], [T.x1, T.z1], 1, T.top, P.brick)
  quoins(c, [[T.x0, T.z0], [T.x1, T.z0]], 1, T.top, P.dressing) // one column each: the tower's faces stay brick
  for (const y of T.bands) stringCourse(c, T.x0, T.z0, T.x1, T.z1, y, P.dressing)
  floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, T.floorY, P.floor)
  // the cornice: stone corbels round the front and sides (none at the back, over the hall roof), the steep slate
  // spire sitting on the walls, the weathervane on its peak
  corbelRing(c, T.x0 - 1, T.z0 - 1, T.x1 + 1, T.z1 + 1, T.cornice, P.dressingStairs, (x, z) => z >= T.z1 - 1)
  const s = T.spire
  const peak = pyramidRoof(c, s.cx, s.cz, s.r, s.y0, P.roof)
  spireTip(c, s.cx, peak - 1, s.cz, P.spirePost, P.vane) // a thin post and the vane, not a stub of solid tiles

  // Hall roof: a 45-degree slate roof, ridge along x, its eaves level with the wall top; the tower stands through it
  const main = gableRoof(c, H.roof, P.roof, (x, y, z) => !inTower(x, z))
  for (const x of [W.x0, W.x1])
    gableWall(c, 'x', x, H.roof.across, H.wallTop + 1, main.under, u => (u > W.z0 && u < W.z1 ? P.brick : null))
  tieBeams(c, H.tieBeams.xs, H.tieBeams.z0, H.tieBeams.z1, H.tieBeams.y, P.beam)

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  const [dx, dy, dz] = fd.at // a dressed stone doorway, flush: jambs and a lintel round the white door
  for (let y = dy; y <= dy + 2; y++) { c.set(dx - 1, y, dz, P.dressing); c.set(dx + 1, y, dz, P.dressing) }
  c.set(dx, dy + 2, dz, P.dressing)
  door(c, fd.at, fd.facing, 'north', { wood: P.joinery, step: fd.step, stepStair: P.dressingStairs })
  doorway(c, OPENINGS.hallDoorway.at, OPENINGS.hallDoorway.height)
  for (const w of OPENINGS.windows) {
    window(c, w.cells, w.out, { stone: null, 'stone+shutters': 'shutters' }[w.dress] ?? w.dress, { glass: P.glass, wood: P.joinery, sill: P.dressingSlab, pots: OUTSIDE.pots[w.name] ?? [] })
    if (!w.dress?.startsWith('stone')) continue
    const low = Math.min(...w.cells.map(p => p[1])), high = Math.max(...w.cells.map(p => p[1]))
    for (const [x, y, z] of w.cells) {
      if (y === low) c.set(x, y - 1, z, P.dressing) // the sill
      if (y === high && high + 1 < H.band) c.set(x, y + 1, z, P.dressing) // a lintel under the band
    }
  }
  const L = OPENINGS.ladder
  ladderShaft(c, L.x, L.z, L.y0, L.y1, L.facing)

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  hood(c, fd.at[0], T.bands[0], T.z0 - 1, P.roof) // a slate hood on the lower band, its lantern over the door
  const ch = MASSES.chimney
  chimney(c, ch.x, ch.z, 0, ch.top, stone)
  bargeTrim(c, 'x', H.roof.along[0], H.roof.across, main.under, `${P.trim}_stairs`)
  bargeTrim(c, 'x', H.roof.along[1], H.roof.across, main.under, `${P.trim}_stairs`)

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  c.free(JOB.at)
  c.set(...JOB.at, JOB.block, { facing: JOB.facing, has_mail: 'false' })
  for (const f of FURNITURE) {
    if (f.part === 'pigeonholes') pigeonholes(c, f.cells, f.y0, f.y1, f.facing, P.pigeonholes)
    else if (f.part === 'mapWall') mapWall(c, f.pos, f.along, f.facing, f.colours, f.wood)
    else furnitureParts[f.part](c, f)
  }

  // --- Pass 5: ground -----------------------------------------------------------------------------------------------
  pillarBox(c, OUTSIDE.pillarBox, { ...P.pillarBox, pad: P.dressing })
  const lp = OUTSIDE.lampPost
  c.set(lp[0], lp[1] - 1, lp[2], P.dressing)
  lampPost(c, lp[0], lp[1], lp[2], P.lampPost, 2)
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `post_office_cut${cut}` : 'post_office' } }
}
