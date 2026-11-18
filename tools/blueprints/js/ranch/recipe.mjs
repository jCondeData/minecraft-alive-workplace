// Ranch (ranch, ranch_2; ROADMAP 49), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground, light) and returns the structure for the
// minecraft-builder skill's runner. LEVEL=2 draws Level II: every cell of Level I, then the feed and bunk wing and the
// paddock. Also writes furniture.json (every furniture spot the parts recorded) for tools/blueprints/rebuild.py.
// Build both levels: ./build.sh. CUT=<y> cuts everything above y.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MONITOR, SHEDS, GATE, YARD, WING, PADDOCK, OPENINGS, FURNITURE } from './plan.mjs'
import {
  Canvas, mix, slab, stair, plinth, floor, post, beamRing, wallPanel, window, door, gableRoof, gableWall, catslideRoof,
  bargeTrim, chimney, railingRun, furnitureParts, doubleChest, ranchGate, horseTrough, stallFront, cupola, hayHood,
  paddockFence,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

const pieces = { ...furnitureParts, doubleChest: (c, f) => doubleChest(c, f.pos, f.facing, f.along) }

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = PALETTE
  const stone = mix(P.stone, 5)
  const M = MONITOR, S = SHEDS
  const { x0, x1, z0, z1 } = M.walls
  const A = M.aisle

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // The footing under the whole barn; the aisle's packed floor, hay bedding in the stalls
  plinth(c, S.west.outer, S.z0, S.east.outer, S.z1, stone)
  floor(c, A.x0, A.z0, A.x1, A.z1, 0, P.aisle)
  for (const sh of [S.west, S.east]) floor(c, sh.x0, S.z0 + 1, sh.x1, S.z1 - 1, 0, P.bedding)

  // The sheds' lean-to roofs first (each falls from the monitor's wall to an eave a block past its outer wall)
  const west = catslideRoof(c, S.west.roof, P.roof)
  const east = catslideRoof(c, S.east.roof, P.roof)
  // The sheds' walls: outer walls two high, front and back walls up under the lean-to, frame posts at the bays
  for (const sh of [S.west, S.east]) {
    wallPanel(c, [sh.outer, S.z0], [sh.outer, S.z1], 1, S.wallTop, P.boards)
    const under = sh === S.west ? west.under : east.under
    for (const z of [S.z0, S.z1]) for (let x = Math.min(sh.outer, sh.x0); x <= Math.max(sh.outer, sh.x1); x++)
      for (let y = 1; y < under[x]; y++) c.set(x, y, z, P.boards)
    for (const z of S.posts) post(c, sh.outer, z, 1, S.wallTop, P.frame)
  }

  // The monitor: walls x 4 and 8 the full height to the beam ring, the front and back walls, posts at the bays
  for (const x of [x0, x1]) wallPanel(c, [x, z0], [x, z1], 5, M.wallTop, P.boards) // over the stall fronts
  for (const z of [z0, z1]) wallPanel(c, [x0, z], [x1, z], 1, M.wallTop, P.boards)
  for (const x of [x0, x1]) for (const z of M.posts) post(c, x, z, 1, M.wallTop, P.frame)
  for (const x of [x0, x1]) for (let z = z0 + 1; z < z1; z++) if (!M.posts.includes(z)) c.set(x, M.loftY, z, P.frame, { axis: 'z' })
  beamRing(c, x0, z0, x1, z1, M.beamY, P.frame)
  floor(c, A.x0, A.z0, A.x1, A.z1, M.loftY, P.boardsFloor) // the hay loft over the aisle
  for (const z of [z0, z1]) for (let x = A.x0; x <= A.x1; x++) c.set(x, M.loftY, z, P.frame, { axis: 'x' }) // lintels

  // The monitor's roof over everything, a block past every side, and its gables
  const main = gableRoof(c, M.roof, P.roof)
  for (const z of [z0, z1]) gableWall(c, 'z', z, [x0, x1], M.beamY + 1, main.under, () => P.boards)

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  // The big doors: two birch leaves either side of the wicket door, a dark track over them
  const bd = OPENINGS.barnDoor
  for (const [x, z] of bd.leaves) for (let y = 1; y <= 3; y++) c.set(x, y, z, `${P.accent}_planks`)
  c.set(bd.at[0], 3, bd.at[2], `${P.accent}_planks`) // the leaves' head over the wicket
  door(c, bd.at, bd.facing, bd.out, { wood: P.door, step: bd.step })
  const hd = OPENINGS.hayDoor
  door(c, hd.at, hd.facing, hd.out, { wood: P.door })
  // the clerestory: two-high lights in the monitor's walls above the sheds, a bay each side of the middle post
  const cl = OPENINGS.clerestory
  for (const x of cl.xs) for (const z of cl.zs) for (const y of cl.ys) c.set(x, y, z, P.glass)
  // the stalls' barred windows, one per stall in the sheds' outer walls
  const sw = OPENINGS.stallWindows
  for (const x of sw.xs) for (const z of sw.zs) c.set(x, sw.y, z, P.bars)
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.accent })
  // the stall fronts off the aisle: a half wall with a gate, a rail over it, open above to the loft's edge beam
  stallFront(c, x0, S.stalls, 1, 'east', { board: P.boards, gate: `${P.accent}_fence_gate`, rail: `${P.accent}_fence` })
  stallFront(c, x1, S.stalls, 1, 'west', { board: P.boards, gate: `${P.accent}_fence_gate`, rail: `${P.accent}_fence` })
  // the partitions between each side's two stalls: a board wall with a rail on it
  for (const sh of [S.west, S.east]) for (let x = sh.x0; x <= sh.x1; x++) { c.set(x, 1, S.partition, P.boards); c.set(x, 2, S.partition, `${P.accent}_fence`) }
  // the ladder up to the loft, against the front wall, through its hatch
  const ld = OPENINGS.ladder
  for (let y = ld.y0; y <= ld.top; y++) c.set(ld.x, y, ld.z, 'ladder', { facing: ld.facing, waterlogged: 'false' })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // the hay hood over the loft door, its hoist beam and lantern
  const H = M.hood
  hayHood(c, { x: H.hoist.x, y: main.ridgeY, z0: H.z, hoistY: H.hoist.y, hoistZ1: H.hoist.z1 }, { roof: P.roof, beam: P.frame, barge: `${P.trim}_slab` })
  // barge boards under the main roof's front and back rakes and the sheds' lean-to ends
  bargeTrim(c, 'z', M.roof.along[0], M.roof.across, main.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', M.roof.along[1], M.roof.across, main.under, `${P.trim}_stairs`)
  for (const x of M.roof.across) for (const z of M.roof.along) c.clear(x, main.under[x] - 1, z) // not under the eaves' ends: they'd float over the sheds
  // the door track: a dark beam over the big doors the leaves slide on, a lantern under it at the wicket
  for (let x = x0; x <= x1; x++) c.set(x, M.loftY, z0 - 1, P.frame, { axis: 'x' })
  c.set(bd.at[0], 3, z0 - 1, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // the cupola on the ridge
  cupola(c, M.cupola, { post: P.frame, wall: P.boards, glass: `${P.accent}_planks`, roof: P.roof }) // white sides, dark posts
  // the smoker's flue up the back gable, smoking over the ridge
  const ch = M.chimney
  chimney(c, ch.x, ch.z, 0, ch.top, stone, { breast: { axis: 'x', top: 3, stair: 'cobblestone_stairs' } })

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  for (const f of FURNITURE[1]) pieces[f.part](c, f)
  // hay: a bale in each stall's back corner; the loft stacked along both sides, its middle and the hatch left clear
  for (const sh of [S.west, S.east]) for (const s of S.stalls) c.set(sh === S.west ? sh.x0 : sh.x1, 1, s.z0 === 4 ? s.z0 : s.z1, P.hay, { axis: 'y' })
  for (const x of [A.x0, A.x1]) for (let z = 6; z <= A.z1; z++) {
    c.set(x, M.loftY + 1, z, P.hay, { axis: 'y' })
    if (x === A.x0 && z >= 7 && z <= 9) c.set(x, M.loftY + 2, z, P.hay, { axis: 'y' })
  }

  // --- Pass 5: ground ---------------------------------------------------------------------------------------------
  // the ranch gate over the way in, the yard fence either side of it, the trough against the fence, hay by the shed
  ranchGate(c, GATE, { post: P.frame, bannerId: P.banner, rotation: 8 })
  for (const run of YARD.fence) railingRun(c, run, P.trim)
  horseTrough(c, YARD.trough.water, YARD.trough.y, P.rim)
  for (const [x, y, z] of YARD.hay) c.set(x, y, z, P.hay, { axis: 'y' })

  if (level >= 2) drawLevel2(c, P, stone)
  return c
}

// Level II: the feed and bunk wing east of the barn and the paddock behind; nothing of Level I is moved.
function drawLevel2(c, P, stone) {
  const W = WING.walls, R = WING.room
  plinth(c, W.x0, W.z0, W.x1, W.z1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.boardsFloor)
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, WING.wallTop, P.boards)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, WING.wallTop, P.boards)
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, WING.wallTop, P.boards)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, WING.wallTop, P.boards)
  for (const x of WING.posts.front) for (const z of [W.z0, W.z1]) post(c, x, z, 1, WING.wallTop, P.frame)
  for (const z of WING.posts.side) for (const x of [W.x0, W.x1]) post(c, x, z, 1, WING.wallTop, P.frame)
  beamRing(c, W.x0, W.z0, W.x1, W.z1, WING.beamY, P.frame)
  const wing = gableRoof(c, WING.roof, P.roof)
  for (const x of [W.x0, W.x1]) gableWall(c, 'x', x, [W.z0, W.z1], WING.beamY + 1, wing.under, () => P.boards)
  bargeTrim(c, 'x', WING.roof.along[1], WING.roof.across, wing.under, `${P.trim}_stairs`)

  const o = OPENINGS.wing
  door(c, o.door.at, o.door.facing, o.door.out, { wood: P.door, step: o.door.step })
  c.set(o.door.at[0], 3, o.door.at[2] - 1, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const w of o.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.accent })

  for (const f of FURNITURE[2]) pieces[f.part](c, f)
  for (const [x, y, z] of [[18, 1, 10], [18, 2, 10], [18, 1, 9]]) c.set(x, y, z, P.hay, { axis: 'y' })
  // the slot between the barn's east shed and the wing, under the shed's eave: the winter's firewood, stacked to the eave
  for (let z = W.z0; z <= W.z1; z++) for (const y of [0, 1, 2]) c.set(W.x0 - 1, y, z, P.firewood, { axis: 'z' })

  // the paddock: the fence round it with a man-gap and the herd's double gate, lamp posts at its corners
  const D = PADDOCK
  paddockFence(c, { ring: D.ring, manGap: D.manGap, gates: D.gates, gateFacing: 'east', lampPosts: D.lampPosts },
    { fence: `${P.trim}_fence`, gate: `${P.trim}_fence_gate` })
  horseTrough(c, D.trough.water, D.trough.y, P.rim)
  for (let y = 0; y <= 2; y++) c.set(D.snubbingPost[0], y, D.snubbingPost[1], P.frame, { axis: 'y' })
  const sh = D.shelter
  for (const [x, z] of sh.posts) for (let y = 0; y < sh.y; y++) c.set(x, y, z, `${P.trim}_fence`) // on the ground, like the fence
  for (let x = sh.x0; x <= sh.x1; x++) for (let z = sh.z0; z <= sh.z1; z++) slab(c, x, sh.y, z, P.roof.slab, 'bottom')
  for (const [x, y, z] of sh.hay) c.set(x, y, z, P.hay, { axis: 'y' })
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const id = level > 1 ? `ranch_${level}` : 'ranch'
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
