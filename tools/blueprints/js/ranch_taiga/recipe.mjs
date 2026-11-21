// Ranch, the taiga look (ROADMAP 49.10e): assembles plan.mjs from the parts library (../parts) in passes (block-out and
// structure, roofs and gables, openings, depth and detail, interior, ground) for LEVEL = 1 or 2, Level II every cell of
// Level I and then the bunkhouse and the paddock. Writes furniture.json (the furniture spots). Build both: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  SIZE, IDS, PALETTE, BARN, PENS, GALLERY, KONEK, HOIST, STACK, OPENINGS, FURNITURE, YARD, BUNK, PADDOCK,
} from './plan.mjs'
import {
  Canvas, mix, stair, floor, window, door, gableRoof, gableWall, bargeTrim, railingRun, furnitureParts, doubleChest,
  stallFront, horseTrough, paddockFence, logPen, porchGable,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

const pieces = {
  ...furnitureParts,
  doubleChest: (c, f) => doubleChest(c, f.pos, f.facing, f.along),
  shelf: (c, f) => { // the shelf with a pot plant on it: a plant (no spot), not crockery
    furnitureParts.shelf(c, f)
    if (f.plant) { const [x, y, z] = f.pos; c.free([x, y + 1, z]); c.set(x, y + 1, z, f.plant) }
  },
}

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = PALETTE
  const footing = mix(P.footing, 7), stack = mix(P.stack, 11)
  const log = (x, y, z, axis, id = P.log) => c.set(x, y, z, id, { axis })
  const W = BARN.walls, A = BARN.aisle

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // the footing under the whole barn, the aisle's boards, the stalls' bedding
  c.fill(PENS.west.outer, 0, PENS.z0, PENS.east.outer, 0, PENS.z1, footing)
  floor(c, A.x0, A.z0, A.x1, A.z1, 0, P.floor)
  for (const p of [PENS.west, PENS.east]) {
    const [a, b] = p === PENS.west ? [2, 3] : [9, 10]
    floor(c, a, PENS.z0 + 1, b, PENS.z1 - 1, 0, P.bedding)
  }

  // --- Pass 2: the roofs first (the walls are drawn up to them) ----------------------------------------------------
  const west = gableRoof(c, PENS.west.roof, P.roof, x => x !== PENS.west.line)
  const east = gableRoof(c, PENS.east.roof, P.roof, x => x !== PENS.east.line)
  const main = gableRoof(c, BARN.roof, P.roof)
  const wallTop = x => (x <= 3 ? west.under[x] - 1 : x >= 9 ? east.under[x] - 1 : BARN.top)

  // the front and back log walls, the whole width: the pens' gables and the hay barn's wall, corners notched
  for (const z of [PENS.z0, PENS.z1]) for (let x = PENS.west.outer; x <= PENS.east.outer; x++)
    for (let y = 1; y <= wallTop(x); y++) {
      const corner = x === PENS.west.outer || x === PENS.east.outer || ((x === W.x0 || x === W.x1) && y > BARN.band)
      log(x, y, z, corner ? (y % 2 ? 'x' : 'z') : 'x')
    }
  // the pens' outer walls
  for (const x of [PENS.west.outer, PENS.east.outer]) for (let z = PENS.z0 + 1; z < PENS.z1; z++)
    for (let y = 1; y <= PENS.top; y++) log(x, y, z, 'z')
  // the hay barn's long walls over the stall fronts: a post at the middle, the pale band at the loft floor, logs above
  for (const x of [W.x0, W.x1]) {
    for (let y = 1; y < BARN.band; y++) log(x, y, BARN.midPost, 'y')
    for (let z = W.z0 + 1; z < W.z1; z++) {
      log(x, BARN.band, z, 'z', P.band)
      for (let y = BARN.band + 1; y <= BARN.top; y++) log(x, y, z, 'z')
    }
  }
  for (const z of [W.z0, W.z1]) for (let x = W.x0 + 1; x < W.x1; x++) log(x, BARN.band, z, 'x', P.band)
  // the loft floor over the aisle (the ladder's hatch left for the ladder)
  floor(c, A.x0, A.z0, A.x1, A.z1, BARN.loftY, P.floor)
  // the hay barn's log gables
  for (const z of [W.z0, W.z1]) gableWall(c, 'z', z, [W.x0, W.x1], BARN.top + 1, main.under, () => [P.log, { axis: 'x' }])

  // --- Pass 3: openings ----------------------------------------------------------------------------------------------
  const D = OPENINGS.door, H = OPENINGS.hayDoor
  for (const x of [D.at[0] - 1, D.at[0] + 1]) for (let y = 1; y <= 3; y++) log(x, y, D.at[2], 'y', P.frame) // the frame
  log(D.at[0], 3, D.at[2], 'x', P.frame)
  door(c, D.at, D.facing, D.out, { wood: P.door, step: D.step })
  door(c, H.at, H.facing, H.out, { wood: P.door })
  const L = OPENINGS.ladder
  for (let y = L.y0; y <= L.top; y++) c.set(L.x, y, L.z, 'ladder', { facing: L.facing, waterlogged: 'false' })
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
  for (const x of [W.x0, W.x1]) for (const z of BARN.clerestory.zs) for (const y of BARN.clerestory.ys) c.set(x, y, z, P.glass)
  const sb = OPENINGS.stallBars
  for (const x of sb.xs) for (const z of sb.zs) for (const y of [sb.y, sb.y + 1]) c.set(x, y, z, P.bars)
  // the stall fronts off the aisle (half walls, gates, a rail) and the partitions between each side's two stalls
  for (const [p, stalls] of [[PENS.west, PENS.stalls.west], [PENS.east, PENS.stalls.east]]) {
    stallFront(c, p.line, stalls, 1, p.toward, { board: P.log === 'spruce_log' ? 'spruce_planks' : P.floor, gate: `${P.wood}_fence_gate`, rail: `${P.wood}_fence` })
    const [a, b] = p === PENS.west ? [2, 3] : [9, 10]
    for (let x = a; x <= b; x++) { log(x, 1, PENS.partition, 'x'); c.set(x, 2, PENS.partition, `${P.wood}_fence`) }
  }

  // --- Pass 4: depth and detail -----------------------------------------------------------------------------------
  // log ends only at the pens' notched outer corners, every other course: the log that runs through the corner on
  // that course pokes out past it (the front and back walls' on the odd courses, the side walls' on the even ones),
  // on footing stones (round 2, note 1: no more log ends up the hay barn's corners or on every course)
  const ends = [
    [0, 3, 'x'], [1, 2, 'z'], [0, 11, 'x'], [1, 12, 'z'], [12, 3, 'x'], [11, 2, 'z'], [12, 11, 'x'], [11, 12, 'z'],
  ]
  for (const [x, z, axis] of ends) {
    c.set(x, 0, z, footing)
    for (let y = axis === 'x' ? 1 : 2; y <= 4; y += 2) log(x, y, z, axis)
  }
  // the gallery (svalgang): posts on footing stones, a log beam, the deck, brackets, the rail, lanterns under it
  const G = GALLERY
  for (const x of G.posts) { c.set(x, 0, G.z0, footing); for (let y = 1; y < G.y; y++) log(x, y, G.z0, 'y', P.post) }
  for (let x = G.x0; x <= G.x1; x++) { log(x, G.y, G.z0, 'x', P.post); c.set(x, G.y, G.z1, P.deck) }
  stair(c, G.posts[0] + 1, G.y - 1, G.z0, P.bracket, 'west', 'top')
  stair(c, G.posts[1] - 1, G.y - 1, G.z0, P.bracket, 'east', 'top')
  railingRun(c, [[G.x0, G.y + 1, G.z1], [G.x0, G.y + 1, G.z0], [G.x1, G.y + 1, G.z0], [G.x1, G.y + 1, G.z1]], P.wood)
  for (const [x, y, z] of G.lamps) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // the gallery's lamp on a chain from the rake's apex (a hoist beam there read as one log column with the konek)
  for (let y = HOIST.lamp[1] + 1; y < HOIST.y; y++) c.set(HOIST.x, y, HOIST.z0, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(...HOIST.lamp, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // the konek: a thin neck (a fence post) on the ridge's front end and the horse's head on it, a stair turned side-on so
  // its outline (muzzle out to the side, the neck under the back of the head) reads from the road (round 2, note 2)
  c.set(KONEK.x, KONEK.y + 1, KONEK.z, `${P.wood}_fence`)
  stair(c, KONEK.x, KONEK.y + 2, KONEK.z, P.bracket, 'east', 'top')
  // the smoker's stack up the back gable: a broad breast weathered in, the flue through the rake, smoke on top
  const S = STACK
  for (let x = S.breast[0]; x <= S.breast[1]; x++) for (let y = 0; y <= S.breastTop; y++) c.set(x, y, S.z, stack)
  stair(c, S.breast[0], S.breastTop + 1, S.z, P.weather, 'east'); stair(c, S.breast[1], S.breastTop + 1, S.z, P.weather, 'west')
  for (let y = S.breastTop + 1; y <= S.top; y++) c.set(S.x, y, S.z, stack)
  c.set(S.x, S.top + 1, S.z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  // barge boards under every rake
  // (one clean line under each rake, stopping short of the eave ends, where a lone tooth stuck out: round 2, note 1)
  for (const z of [BARN.roof.along[0], BARN.roof.along[1]]) bargeTrim(c, 'z', z, [W.x0, W.x1], main.under, P.barge)
  for (const z of [PENS.west.roof.along[0], PENS.west.roof.along[1]]) {
    bargeTrim(c, 'z', z, [1, 3], west.under, P.barge)
    bargeTrim(c, 'z', z, [9, 11], east.under, P.barge)
  }

  // --- Pass 5: interior (the shipped furniture, cell for cell) ------------------------------------------------------
  for (const f of FURNITURE[1]) pieces[f.part](c, f)
  // hay: a bale in each stall's corner; the loft stacked along both sides, the hatch and the hay door left clear
  for (const [x, z] of [[2, 4], [2, 10], [10, 4], [10, 10]]) c.set(x, 1, z, P.hay, { axis: 'y' })
  for (const x of [A.x0, A.x1]) for (let z = 6; z <= A.z1; z++) {
    c.set(x, BARN.loftY + 1, z, P.hay, { axis: 'y' })
    if (x === A.x0 && z >= 7 && z <= 9) c.set(x, BARN.loftY + 2, z, P.hay, { axis: 'y' })
  }

  // --- Pass 6: ground ------------------------------------------------------------------------------------------------
  for (const [x, z] of YARD.path) c.set(x, 0, z, P.path)
  horseTrough(c, YARD.trough.water, YARD.trough.y, P.rim)
  const hz = YARD.hitch
  for (const [x, z] of hz.posts) for (let y = 0; y <= 1; y++) c.set(x, y, z, `${P.wood}_fence`) // a lantern goes on a fence post
  log(...hz.rail, 'x', P.rim)
  c.set(...hz.lamp, 'lantern', { hanging: 'false', waterlogged: 'false' })
  const fw = YARD.firewood
  for (let z = fw.z0; z <= fw.z1; z++) for (let y = 0; y <= fw.top; y++)
    if (y < fw.top || (z > fw.z0 && z < fw.z1)) log(fw.x, y, z, 'z', P.firewood) // laid along the wall, not end-on
  if (level < 2) for (const [x, y, z] of YARD.hay) c.set(x, y, z, P.hay, { axis: 'y' })
  // ferns at grade: potted ferns on the ground with moss spread round them, no green cubes (round 2, note 3)
  for (const [x, z] of YARD.ferns) c.set(x, 0, z, P.pot)
  for (const [x, z] of YARD.moss) if (!c.get(x, 0, z)) c.set(x, 0, z, P.moss)

  if (level >= 2) drawLevel2(c, P, footing, log)
  for (const [x, y, z] of YARD.keepOpen) if (c.get(x, y, z)) throw new Error(`kept cell ${x},${y},${z} drawn over`)
  return c
}

// Level II: the bunkhouse east of the barn, the firewood in the slot between, the paddock behind; nothing of I moves.
function drawLevel2(c, P, footing, log) {
  const B = BUNK, W = B.walls, R = B.room
  c.fill(W.x0, 0, W.z0, W.x1, 0, W.z1, footing)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  logPen(c, { x0: W.x0, z0: W.z0, x1: W.x1, z1: W.z1, y0: 1, y1: B.top }, P.log)
  const roof = gableRoof(c, B.roof, P.roof, x => x !== B.roof.across[0])
  for (const z of [W.z0, W.z1]) gableWall(c, 'z', z, [W.x0, W.x1], B.top + 1, roof.under, () => [P.log, { axis: 'x' }])
  for (const z of B.roof.along) bargeTrim(c, 'z', z, [W.x0, B.roof.across[1]], roof.under, P.barge)
  // log ends at the outer corners, on footing stones
  for (const [x, z, axis] of [[20, 5, 'x'], [19, 4, 'z'], [20, 11, 'x'], [19, 12, 'z'], [13, 4, 'z'], [13, 12, 'z']]) {
    c.set(x, 0, z, footing); for (let y = axis === 'x' ? 1 : 2; y <= 3; y += 2) log(x, y, z, axis) // every other course
  }
  // the door under a little gabled porch on two log posts, a lantern under its ridge
  door(c, B.door.at, B.door.facing, B.door.out, { wood: P.door })
  const pc = B.porch
  // (round 2, note 4: slim fence posts with stair brackets and a slab lintel, the svalgang's language, not log posts)
  porchGable(c, { cx: pc.cx, half: pc.half, y: pc.y, z0: pc.z0, z1: pc.z1 }, P.roof, { beam: P.log, face: P.floor })
  c.set(pc.cx, pc.y, pc.z0, `${P.wood}_slab`, { type: 'top', waterlogged: 'false' }) // the lintel: a board, not a log
  for (const x of pc.posts) {
    for (let y = 0; y < pc.y; y++) c.set(x, y, pc.z0, `${P.wood}_fence`)
    stair(c, x, pc.y - 1, pc.z0 + 1, P.bracket, 'north', 'top') // a bracket off the post's back, under the porch's eave
  }
  c.set(...pc.lamp, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const w of B.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
  for (const [x, z] of B.path) c.set(x, 0, z, P.path)
  // inside: the shipped furniture, hay by the back wall
  for (const f of FURNITURE[2]) pieces[f.part](c, f)
  for (const [x, y, z] of B.hay) c.set(x, y, z, P.hay, { axis: 'y' })
  // the winter's firewood stacked in the slot between the barn and the bunkhouse, up under the barn's eave
  const fw = B.firewood
  for (let z = fw.z0; z <= fw.z1; z++) for (let y = 0; y <= fw.top; y++) log(fw.x, y, z, 'x', P.firewood)

  // the paddock: split rails round it, a man-gap, the herd's double gate, lanterns on the corner posts
  const D = PADDOCK
  paddockFence(c, { ring: D.ring, manGap: D.manGap, gates: D.gates, gateFacing: 'east', lampPosts: D.lampPosts },
    { fence: `${P.wood}_fence`, gate: `${P.wood}_fence_gate` })
  horseTrough(c, D.trough.water, D.trough.y, P.rim)
  for (let y = 0; y <= 2; y++) log(D.snubbingPost[0], y, D.snubbingPost[1], 'y', P.post)
  // the run-in shed: three log walls open to the barn, its slate lean-to high over the open front, falling to the back
  const sh = D.shed
  const shedRoof = z => 4 - (z - sh.z0) // z 17 -> 4, 18 -> 3, 19 -> 2
  for (let x = sh.x0; x <= sh.x1; x++) for (let z = sh.z0; z <= sh.z1; z++) {
    const side = x === sh.x0 || x === sh.x1, back = z === sh.z1
    if (side || back) for (let y = 0; y < shedRoof(z); y++) log(x, y, z, side && back ? (y % 2 ? 'x' : 'z') : back ? 'x' : 'z')
    stair(c, x, shedRoof(z), z, P.roof.stairs, 'north')
  }
  for (const [x, y, z] of [[9, 0, 18], [10, 0, 18], [9, 1, 18]]) c.set(x, y, z, P.hay, { axis: 'x' })
  // the hesje: two pale posts, hay hung over the rails between them (a full course and a shorter one), a capping rail
  const h = D.hesje
  for (const x of h.posts) for (let y = 0; y < h.top; y++) log(x, y, h.z, 'y', P.rim)
  for (let x = h.x0 + 1; x < h.x1; x++) {
    c.set(x, 1, h.z, P.hay, { axis: 'x' })
    if (x > h.x0 + 1 && x < h.x1 - 1) c.set(x, 2, h.z, P.hay, { axis: 'x' })
  }
  for (let x = h.x0; x <= h.x1; x++) log(x, h.top, h.z, 'x', P.rim)
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
