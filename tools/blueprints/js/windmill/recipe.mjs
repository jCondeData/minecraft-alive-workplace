// The Windmill (the Stage Mill), the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49 /
// 50.10) in passes (block-out and structure, the machine, openings, depth and detail, interior, ground) and returns the
// structure for the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts
// recorded) for tools/blueprints/rebuild.py. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, CENTRE, MASSES, MACHINE, OPENINGS, FURNITURE, SACKS, OUTSIDE } from './plan.mjs'
import { Canvas, stair, window, door, lampPost, furnitureParts, composter, doubleChest } from '../parts/index.mjs'
import { fairMix } from '../parts/apiary_skep.mjs'
import {
  octagon, octDrum, octFloor, taperLedge, reefingStage, commonSails, sailPlaneCheck, windshaftDrive, boatCap, tailPole,
} from '../parts/windmill_smock.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const [cx, cz] = CENTRE
  const plinth = fairMix(P.plinth, 11)
  const brick = fairMix(P.brick, 5)
  const boards = fairMix(P.boards, 23)
  const M = MASSES, X = MACHINE
  const drum = octagon(cx, cz, M.drum.half)
  const deck = octagon(cx, cz, M.stage.half)
  const smockA = octagon(cx, cz, M.smock[0].half)
  const smockB = octagon(cx, cz, M.smock[1].half)
  const curb = octagon(cx, cz, M.curb.half)
  const CF = M.curb.floor
  const inCurbFloor = (x, z) => x >= CF.x0 && x <= CF.x1 && z >= CF.z0 && z <= CF.z1

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // the drum: a stone plinth course under the brick, the meal floor's boards inside
  for (const [x, z] of drum.ring()) c.set(x, 0, z, plinth)
  for (const [x, z] of drum.cells()) if (drum.interior(x, z)) c.set(x, 0, z, P.floor)
  octDrum(c, drum, M.drum.y0, M.drum.y1, brick)
  // the stage deck over the drum and the stone floor inside (one plank floor at y 5), open for the stair and the shaft
  reefingStage(c, deck, M.stage.y, { floor: P.deck, fence: P.fence, bracket: P.bracket },
    { every: 2, gaps: [] })
  for (const [x, z] of OPENINGS.stairs.well) c.clear(x, M.stage.y, z)
  // the smock: tarred boards, half 4 then stepping in to half 3, a drip of stairs on each step
  octDrum(c, smockA, M.smock[0].y0, M.smock[0].y1, boards)
  taperLedge(c, smockA, smockB, M.smock[1].y0, P.smockStairs)
  octDrum(c, smockB, M.smock[1].y0, M.smock[1].y1, boards)
  taperLedge(c, smockB, curb, M.curb.y, P.smockStairs, (x, y, z) => !inCurbFloor(x, z))
  // the curb: the white band the cap turns on (the top of the smock only), the cap's own boards out over the nose
  for (let x = CF.x0; x <= CF.x1; x++) for (let z = CF.z0; z <= CF.z1; z++) {
    if (curb.onRing(x, z)) c.set(x, M.curb.y, z, P.trim, { axis: x === cx ? 'z' : 'x' })
    else c.set(x, M.curb.y, z, curb.inside(x, z) ? P.floor : P.cap.board)
  }

  // the cap: a slate boat roof over its boards, the nose open for the windshaft
  const ridgeY = boatCap(c, { ...M.cap, keepOut: [[X.bearing[0], M.cap.y0, M.cap.zg0]] }, P.cap)

  // --- Pass 2: the machine ----------------------------------------------------------------------------------------
  const stone = windshaftDrive(c, { bearing: X.bearing, facing: X.facing, head: X.head, footY: X.footY, out: X.out, run: X.run })
  const sails = commonSails(c, X.hub, X.facing, X.sails, { stock: P.sails.stock, cloth: P.sails.cloth, hub: P.hub, hubProps: { axis: 'z' } })

  // --- Pass 3: openings ---------------------------------------------------------------------------------------------
  const D = OPENINGS.frontDoor, S = OPENINGS.stageDoor
  door(c, D.at, D.facing, D.out, { wood: P.wood, step: D.step, stepStair: 'stone_brick_stairs' })
  door(c, S.at, S.facing, S.out, { wood: P.wood })
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
  OPENINGS.stairs.flight.forEach(([x, y, z]) => stair(c, x, y, z, `${P.wood}_stairs`, OPENINGS.stairs.rises))

  // --- Pass 4: depth and detail ---------------------------------------------------------------------------------
  // the brick drum's top course in plinth stone (a string course under the stage's brackets)
  for (const [x, z] of drum.ring()) c.set(x, M.drum.y1, z, plinth)
  // white frames round the front door (jambs and a lintel) and the stage door's head
  const [dx, dy, dz] = D.at
  for (const x of [dx - 1, dx + 1]) for (let y = dy; y <= dy + 1; y++) c.set(x, y, dz, P.trim, { axis: 'y' })
  for (let x = dx - 1; x <= dx + 1; x++) c.set(x, dy + 2, dz, P.trim, { axis: 'x' })
  c.set(S.at[0], S.at[1] + 2, S.at[2], P.trim, { axis: 'x' })
  // the tail pole raking down from under the cap's back to the stage
  tailPole(c, OUTSIDE.tail, P.pole)
  stair(c, ...OUTSIDE.tailBrace, P.bracket, 'south', 'top') // the knee brace in the pole's elbow

  // --- Pass 5: interior (the plan's furniture) ----------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (f.part === 'composter') { composter(c, f); continue }
    if (f.part === 'doubleChest') { doubleChest(c, f.pos, f.facing, f.along); continue }
    furnitureParts[f.part](c, f)
  }
  for (const p of SACKS) c.set(...p, P.sack, { axis: 'y' })

  // --- Pass 6: ground ----------------------------------------------------------------------------------------------
  lampPost(c, ...OUTSIDE.lampPost, P.fence, 2)
  for (const p of OUTSIDE.hay) c.set(...p, P.sack, { axis: 'y' })

  // the sails carry nothing but themselves and the hub: nothing else touches them edge-on in their plane
  sailPlaneCheck(c, sails, 'z', [X.hub])
  for (const [x, y, z] of sails) if (z !== 0 || y < 5) throw new Error(`sail outside the carried plane at ${x},${y},${z}`)
  if (stone.join(',') !== '9,1,7') throw new Error(`millstone at ${stone}`)
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  // STANDIN=1 (renders only: the renderer has no Create models): Create's blocks drawn as vanilla look-alikes
  if (process.env.STANDIN) for (const [k, v] of canvas.cells) {
    const look = { 'create:white_sail': 'white_wool', 'create:sail_frame': 'stripped_oak_log', 'create:windmill_bearing': 'observer',
      'create:shaft': 'iron_bars', 'create:gearbox': 'stripped_spruce_log', 'create:millstone': 'grindstone' }[v.id]
    if (look) canvas.cells.set(k, { id: `minecraft:${look}`, props: {} })
  }
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `windmill_cut${cut}` : 'windmill' } }
}
