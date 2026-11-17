// Builder's Workshop (ROADMAP 49, designer A), the recipe: assembles plan.mjs from the shared parts library (../parts)
// in passes (block-out and structure, openings, depth and detail, interior, ground and light) and returns the
// structure for the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts
// recorded) for tools/blueprints/rebuild.py. Build: see package.json; CUT=<y> cuts everything above y (cutaways).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, gableRoof, gableWall, catslideRoof, bargeTrim,
  chimney, hearthFire, flight, railingRun, trapdoor, furnitureParts, hoistBeam, jettyBeam, materialStack,
  steppedBreast,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

// the plan's own pieces that are fixtures, not furniture: the job block and the chests (with their double halves)
const fixtures = {
  blueprintTable(c, f) { c.free(f.pos); c.set(...f.pos, 'aliveworkplace:blueprint_table', { facing: f.facing }) },
  chest(c, f) { c.free(f.pos); c.set(...f.pos, 'chest', { facing: f.facing, type: f.type ?? 'single', waterlogged: 'false' }) },
  hearth(c, f) { hearthFire(c, f.pos, f.facing) },
}

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const rubble = mix(P.rubble, 3), stack = mix(P.chimney, 4), ashlar = mix(P.ashlar, 5), yard = mix(P.yard, 9)
  const W = MASSES.workshop, L = MASSES.loft, S = MASSES.shed, CH = MASSES.chimney
  const { x0, x1, z0, z1 } = W.walls
  const frame = P.frame, logY = [frame, { axis: 'y' }], logX = [frame, { axis: 'x' }]

  // --- Pass 1: block-out and structure ---------------------------------------------------------------------------
  // Workshop: a cobble plinth, stone-brick walls, boards inside
  plinth(c, x0, z0, x1, z1, rubble)
  floor(c, W.room.x0, W.room.z0, W.room.x1, W.room.z1, 0, P.floor)
  for (const [a, b] of [[[x0, z0], [x1, z0]], [[x0, z1], [x1, z1]], [[x0, z0], [x0, z1]], [[x1, z0], [x1, z1]]])
    wallPanel(c, a, b, 1, W.wallTop, ashlar)

  // Loft: a beam ring on the stone, the jetty beam one out over the street on corner brackets, the loft's boards
  beamRing(c, x0, z0, x1, z1, W.beamY, frame)
  jettyBeam(c, 'x', L.front, [x0, x1], W.beamY, frame, `${P.trim}_stairs`, [x0, x1], 'south')
  floor(c, W.room.x0, W.room.z0, W.room.x1, W.room.z1, W.beamY, P.floor)
  // knee walls: brick between dark posts
  for (const x of [x0, x1]) {
    wallPanel(c, [x, L.front + 1], [x, L.back - 1], W.beamY + 1, L.kneeTop, P.infill)
    for (const z of L.sidePosts) post(c, x, z, W.beamY + 1, L.kneeTop, frame)
  }

  // Roofs: the steep slate roof first, then the shed's lean-to running on from its east eave (only where empty)
  const main = gableRoof(c, L.roof, P.roof)
  catslideRoof(c, S.roof, P.roof, (x, y, z) => !c.get(x, y, z))

  // Gables: a frame grid. Corner posts (2/10) and tall posts (5/7) to the roof; the loft storey (y 5..7) has two-light
  // windows at 3..4 and 8..9 over a brick course and, on the street, the loading hatch at 6 under a lintel; a tie beam
  // at y 8; the attic triangle in brick with a window at 6 (y 9..10) under a collar (the hoist beam on the street)
  // and a king post. Brick nogging everywhere else.
  const grid = street => (u, y) => {
    if (u === 2 || u === 10 || u === 5 || u === 7) return logY
    if (y === 8) return logX
    if (u === 6) {
      if (y <= 6) return street ? null : y === 6 ? P.glass : P.infill
      if (y === 7) return logX
      if (y === 9 || y === 10) return P.glass
      if (y === 11) return street ? null : logX
      return logY
    }
    if (y >= 6 && y <= 7 && u !== 6) return P.glass
    return P.infill
  }
  gableWall(c, 'z', L.front, [x0, x1], W.beamY + 1, main.under, grid(true))
  gableWall(c, 'z', L.back, [x0, x1], W.beamY + 1, main.under, grid(false))

  // --- Pass 2: openings --------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.step })
  const sd = OPENINGS.sideDoor
  door(c, sd.at, sd.facing, 'east', { wood: P.wood })
  const pots = { 'front west': ['potted_azure_bluet'], 'front east': ['potted_red_tulip'] }
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: pots[w.name] ?? [] })
  // the loading hatch: two leaves of plank (open trapdoors) flush with the gable's face
  const h = OPENINGS.hatch
  for (let y = h.y0; y <= h.y1; y++) trapdoor(c, h.u, y, h.z, `${P.wood}_trapdoor`, 'south', 'bottom', true)
  // the flight to the loft up the east wall, its well and a rail round it
  const st = OPENINGS.stairs
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railingRun(c, [[9, 5, 5], [8, 5, 5], [8, 5, 8]], P.wood)

  // --- Pass 3: depth and detail ------------------------------------------------------------------------------------
  // the hearth in the west wall, its rubble stack and stepped breast outside, a dressed cap
  chimney(c, CH.x, CH.z, 0, CH.top, stack, { breast: { axis: 'z', top: CH.breastTop, stair: 'cobblestone_stairs' } })
  // the shoulder: a second, lower step of the breast, three wide, so the stack grows out of the ground
  steppedBreast(c, [[CH.shoulder.x, CH.z - 1], [CH.shoulder.x, CH.z], [CH.shoulder.x, CH.z + 1]], 0, CH.shoulder.top, stack, 'cobblestone_stairs', 'east')
  c.set(CH.x, CH.breastTop + 1, CH.z, stack) // (the stack's own cell over the breast's stairs)
  c.set(CH.x, CH.top, CH.z, 'stone_bricks')
  for (let y = 2; y <= W.wallTop; y++) c.set(CH.hearth[0], y, CH.hearth[2], stack)
  // the hoist: a beam out of the street gable's apex on a bracket, its chain and hook lantern over the street
  const ho = OPENINGS.hoist
  hoistBeam(c, ho.at, ho.out, { log: frame, bracket: `${P.trim}_stairs`, reach: ho.reach, drop: ho.drop })
  // collar boards over the loft's middle (an attic over them under the ridge)
  const ce = L.ceiling
  floor(c, ce.x0, L.front + 1, ce.x1, L.back - 1, ce.y, P.floor)
  // barge boards under both rakes
  bargeTrim(c, 'z', L.roof.along[0], L.roof.across, main.under, `${P.trim}_slab`)
  bargeTrim(c, 'z', L.roof.along[1], L.roof.across, main.under, `${P.trim}_slab`)
  // lanterns either side of the door, hung from the jetty beam
  for (const x of [fd.at[0] - 1, fd.at[0] + 1]) c.set(x, W.beamY - 1, L.front, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // Shed: a yard floor, posts under the lean-to's low edge
  floor(c, S.floor.x0, S.floor.z0, S.floor.x1, S.floor.z1, 0, yard)
  for (const [x, z] of S.posts) post(c, x, z, 1, S.postTop, frame)
  // the rack: timber lengths laid two high along the wall under the lean-to's top
  for (let z = S.rack.z0; z <= S.rack.z1; z++) {
    for (let x = S.rack.x; x <= S.rack.x1; x++) c.set(x, S.rack.y, z, P.stock.log, { axis: 'z' })
    c.set(S.rack.x, S.rack.y + 1, z, P.stock.log, { axis: 'z' })
  }

  // --- Pass 4: interior (the plan's furniture) -----------------------------------------------------------------------
  for (const f of FURNITURE) (fixtures[f.part] ?? furnitureParts[f.part])(c, f)

  // --- Pass 5: ground ------------------------------------------------------------------------------------------------
  const k = P.stock
  for (const [x, y, z] of OUTSIDE.path) c.set(x, y, z, k.stoneCap, { type: 'bottom', waterlogged: 'false' })
  materialStack(c, OUTSIDE.timber, 1, k.log, { props: { axis: 'z' } })
  materialStack(c, OUTSIDE.stone, 0, k.stone, { cap: k.stoneCap })
  materialStack(c, OUTSIDE.logs, 1, k.log, { props: { axis: 'x' } })
  materialStack(c, OUTSIDE.planks, 1, k.planks, { cap: k.plankCap })
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `builders_workshop_a_cut${cut}` : 'builders_workshop_a' } }
}
