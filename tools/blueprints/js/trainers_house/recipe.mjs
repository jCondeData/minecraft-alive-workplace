// The Trainer's House (the Battle Court), the recipe: assembles plan.mjs from the shared parts library (../parts) in
// passes (block-out and structure, openings, depth and detail, interior, ground and light) and returns the structure
// for the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded) for
// tools/blueprints/rebuild.py. Build: see package.json; CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE, POST } from './plan.mjs'
import {
  Canvas, mix, stair, plinth, floor, post, posts, beamRing, brackets, wallPanel, window, door, gableRoof, gableWall,
  bargeTrim, catslideRoof, dormer, chimney, flowerBed, railingRun, flight, stairwell, lampPost, beamRun, hangingLantern,
  flagPole, furnitureParts, courtField, benchRow, sparringDummy,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const G = MASSES.gate, CT = MASSES.court, S = MASSES.stand, K = MASSES.club
  const W = K.walls, L = K.loft

  // --- Pass 1: block-out and structure ------------------------------------------------------------------------------
  // Court: stone paving out to the curb and the porch apron, the marked field on it
  plinth(c, CT.x0, CT.z0, CT.x1, CT.z1, stone)
  courtField(c, CT.field, { turf: P.turf, line: P.line, near: P.home, far: P.away })

  // Gate: a log beam with its ends cut back, run through two stone piers that rise past it to capped tops, a board
  // coping on the beam; open above, so the street looks over it down the court
  beamRun(c, 'x', G.z, G.beam[0], G.beam[1], G.beamY, P.frame, `${P.wood}_stairs`)
  for (const x of G.piers) {
    post(c, x, G.z, 1, G.pierTop, P.curb)
    c.set(x, G.pierTop + 1, G.z, P.lowWall) // a short wall post: the lantern's stand
    c.set(x, G.pierTop + 2, G.z, 'lantern', { hanging: 'false', waterlogged: 'false' })
  }
  for (let x = G.piers[0] + 1; x < G.piers[1]; x++) c.set(x, G.beamY + 1, G.z, `${P.trim}_slab`, { type: 'bottom', waterlogged: 'false' })
  for (const [a, b] of [[CT.x0, G.piers[0] - 1], [G.piers[1] + 1, CT.x1]]) for (let x = a; x <= b; x++) c.set(x, 1, G.z, P.lowWall)

  // Stand: a stone base, a boarded back wall framed in spruce, closed ends, posts on the curb, a lean-to over the seats
  plinth(c, S.base.x0, S.base.z0, S.base.x1, S.base.z1, stone)
  const sTop = S.roof.ridgeY - 1 // the back wall's top, under the ridge; the curb posts stop under the low eave
  wallPanel(c, [S.back, S.base.z0], [S.back, S.base.z1], 1, sTop, P.infill)
  for (const z of S.ends) wallPanel(c, [S.seats, z], [S.seats, z], 1, sTop - 1, P.infill)
  posts(c, 'z', S.back, S.posts, 1, sTop, P.frame)
  posts(c, 'z', S.front, S.posts, 1, sTop - 2, P.frame)
  catslideRoof(c, S.roof, P.roof)

  // Clubhouse: the stone skirt, the den's board floor, the ground walls framed in spruce
  plinth(c, K.skirt.x0, K.skirt.z0, K.skirt.x1, K.skirt.z1, stone)
  floor(c, K.room.x0, K.room.z0, K.room.x1, K.room.z1, 0, P.floor)
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, K.wallTop, P.infill)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, K.wallTop, P.infill)
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, K.wallTop, P.infill)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, K.wallTop, P.infill)
  posts(c, 'x', W.z0, K.frontPosts, 1, K.wallTop, P.frame)
  posts(c, 'x', W.z1, K.backPosts, 1, K.wallTop, P.frame)
  posts(c, 'z', W.x0, K.sidePosts, 1, K.wallTop, P.frame)
  posts(c, 'z', W.x1, K.sidePosts, 1, K.wallTop, P.frame)
  // the porch under the jetty: corner posts down to the apron, brackets under the beam either side of the door
  for (const x of [L.x0, L.x1]) post(c, x, L.z0, 1, K.wallTop, P.frame)
  brackets(c, [[7, K.wallTop, L.z0], [9, K.wallTop, L.z0]], `${P.wood}_stairs`, 'south')
  // the loft: a beam ring jettied one block forward, its floor, knee walls to the eaves with posts at corners and middle
  beamRing(c, L.x0, L.z0, L.x1, L.z1, K.beamY, P.frame)
  floor(c, K.loftRoom.x0, K.loftRoom.z0, K.loftRoom.x1, K.loftRoom.z1, K.beamY, P.floor)
  wallPanel(c, [L.x0, L.z0], [L.x0, L.z1], K.beamY + 1, K.kneeTop, P.infill)
  wallPanel(c, [L.x1, L.z0], [L.x1, L.z1], K.beamY + 1, K.kneeTop, P.infill)
  for (const x of [L.x0, L.x1]) posts(c, 'z', x, [L.z0, 14, L.z1], K.beamY + 1, K.kneeTop, P.frame)

  // Roof: the clubhouse's 45-degree slate gable, ridge down the court's axis
  const main = gableRoof(c, K.roof, P.roof)
  // Gables: the hero gable over the court (studs, a collar, a king post round the loft's big window), the back one plainer
  const gable = (window) => (u, y) => {
    if (u === L.x0 || u === L.x1) return [P.frame, { axis: 'y' }]
    if ((u === 6 || u === 10) && y <= 8) return [P.frame, { axis: 'y' }]
    if (y === 8 && u >= 7 && u <= 9) return [P.frame, { axis: 'x' }]
    if (u === 8 && y === 10) return [P.frame, { axis: 'y' }]
    return P.infill
  }
  gableWall(c, 'z', L.z0, [L.x0, L.x1], K.beamY + 1, main.under, gable(true))
  gableWall(c, 'z', L.z1, [L.x0, L.x1], K.beamY + 1, main.under, gable(false))

  // --- Pass 2: openings ---------------------------------------------------------------------------------------------
  for (const [x, y, z] of OPENINGS.gate.steps) stair(c, x, y, z, P.stoneStairs, 'south')
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood })
  const pots = {
    'loft gable': ['potted_red_tulip', 'potted_cornflower', 'potted_red_tulip'],
  }
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: pots[w.name] ?? [] })
  const st = OPENINGS.stairs
  stairwell(c, st.well)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railingRun(c, [[6, 5, 16], [6, 5, 15], [9, 5, 15]], P.wood)

  // --- Pass 3: depth and detail -------------------------------------------------------------------------------------
  // a gabled dormer on the east slope over the den's table, lighting the loft (the west slope has the chimney)
  const dm = MASSES.club.dormer
  dormer(c, dm.z, P.roof, P.infill, P.glass, P.frame, { face: dm.face, eave: dm.eave, y0: dm.y0 })
  // the hearth's stone surround in the west wall, the stack up the eave past it
  const ch = MASSES.chimney
  wallPanel(c, [W.x0, 13], [W.x0, 13], 1, K.wallTop, stone)
  wallPanel(c, [W.x0, ch.z], [W.x0, ch.z], 2, K.wallTop, stone)
  chimney(c, ch.x, ch.z, 0, ch.top, stone)
  // barge boards under both rakes, the gate's piers dressed with the two trainers' colours, lights at every way in
  bargeTrim(c, 'z', K.roof.along[0], K.roof.across, main.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', K.roof.along[1], K.roof.across, main.under, `${P.trim}_stairs`)
  c.set(G.piers[0], 3, G.z - 1, P.homeWallBanner, { facing: 'north' })
  c.set(G.piers[1], 3, G.z - 1, P.awayWallBanner, { facing: 'north' })
  for (const x of [6, 10]) hangingLantern(c, x, K.wallTop, L.z0)
  for (const [x, y, z] of OUTSIDE.standLamps) hangingLantern(c, x, y, z)
  // the home trainer's pennant on the gable's peak, turned to the street
  const pn = OUTSIDE.pennant
  flagPole(c, [pn.at[0], pn.at[2]], pn.at[1], pn.top, P.fence, P.homeBanner, 'north')

  // --- Pass 4: interior (the plan's furniture), the stand's seats, the Training Post ---------------------------------
  for (const f of FURNITURE) furnitureParts[f.part](c, f)
  benchRow(c, Array.from({ length: 7 }, (_, i) => [S.seats, 1, S.base.z0 + 1 + i]), `${P.wood}_stairs`, 'east')
  c.free(POST.at)
  c.set(...POST.at, 'aliveworkplace:training_post', { facing: POST.facing })

  // --- Pass 5: ground and light -------------------------------------------------------------------------------------
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  const wp = OUTSIDE.woodpile
  for (let z = wp.z0; z <= wp.z1; z++) c.set(wp.x, 1, z, `${P.wood}_log`, { axis: 'x' })
  c.set(wp.x, 2, wp.z0 + 1, `${P.wood}_log`, { axis: 'x' })
  for (const d of OUTSIDE.dummies) sparringDummy(c, d, P.dummy)
  for (const [x, y, z] of OUTSIDE.lampPosts) lampPost(c, x, y, z, P.fence, 2)
  railingRun(c, OUTSIDE.yardFence, P.wood)

  const n = id => [...c.cells.values()].filter(v => v.id === id).length
  if (n('aliveworkplace:training_post') !== 1) throw new Error('not exactly one Training Post')
  if (n('minecraft:red_bed') !== 2) throw new Error('not exactly one bed')
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `trainers_house_cut${cut}` : 'trainers_house' } }
}
