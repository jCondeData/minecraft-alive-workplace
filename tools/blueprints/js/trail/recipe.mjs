// The Trail Marks (story/trail_1 .. story/trail_5), the recipe: draws one mark of plan.mjs (MARK=1..5, default 1)
// from the shared parts library (../parts; trail_marks.mjs grown for it) in passes (block-out: the tall piece and its
// rag; depth and detail: what lies round it; ground: the whole y 0 layer; light) and returns the structure for the
// minecraft-builder skill's runner. Writes furniture.json (no spots: nobody uses a mark) for rebuild.py.
// Build: see package.json ("npm run build" draws and saves all five as candidates); CUT=<y> cuts above y.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { PALETTE, MARKS, PINS } from './plan.mjs'
import {
  Canvas, mix, slab, trapdoor, hangingLantern, cartWheel, warStandard,
  trailGround, wheelRuts, ragPost, burstBarrel, arrowStuds, coverHoop,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE, C = P.caravan, R = P.raiders, K = P.cloth

// --- Mark 1: the Lantern Pole --------------------------------------------------------------------------------------
function mark1(c, M) {
  // block-out: the lantern pole upright, its rags at the top, a splintered tip
  ragPost(c, M.pole.at, { height: M.pole.height, tip: M.pole.tip, rags: M.pole.rags }, { post: C.post, fence: C.fence, rag: K.rag })
  // the hook out of the pole, low, and the carter's lantern hanging on it
  c.set(...M.arm, C.fence)
  hangingLantern(c, ...M.lantern, P.lantern)
  // detail: a snapped shaft lying along the rut, its splinter; a sideboard knocked flat
  c.set(...M.shaft.log, C.post, { axis: 'z' })
  slab(c, ...M.shaft.splinter, C.slab)
  trapdoor(c, ...M.board, C.trapdoor, 'south')
}

// --- Mark 2: the Lost Wheel ----------------------------------------------------------------------------------------
function mark2(c, M) {
  // the cross-tree: the post, a spar lashed across its top (a pale log along x), a rag hung from each end of the spar
  const st = M.stake
  ragPost(c, st.at, { height: st.height, tip: 0 }, { post: C.post, rag: K.rag })
  for (let x = st.spar.x0; x <= st.spar.x1; x++) c.set(x, st.spar.y, st.at[2], C.post, { axis: 'x' })
  for (let i = 0; i < st.tip; i++) c.set(st.at[0], st.spar.y + 1 + i, st.at[2], C.fence)
  for (const [x, y, z, f] of st.rags) c.set(x, y, z, K.rag, { facing: f })
  cartWheel(c, M.wheel.hub, M.wheel.axle, { rim: { full: C.planks, stairs: C.stairs }, hubLog: C.post }) // a pale hub against the darker rim
  burstBarrel(c, M.barrel, M.staves, { barrel: C.barrel, stave: C.trapdoor })
  c.set(...M.sack, C.sack, { axis: 'x' })
}

// --- Mark 3: the Torn Banner ---------------------------------------------------------------------------------------
function mark3(c, M) {
  // the splintered pole: a fence foot, a lashed log, the settlers' banner jammed on top
  c.set(...M.pole.fence, C.fence)
  c.set(...M.pole.lash, C.post, { axis: 'y' })
  c.set(...M.pole.banner, K.banner, { rotation: String(M.pole.rotation) })
  // the cover thrown over its one standing hoop, slumped to the ground behind, hacked through
  const torn = new Set(M.torn.map(t => t.join(',')))
  coverHoop(c, M.hoop, { rib: C.fence, cloth: K.canvas }, (x, y, z) => torn.has([x, y, z].join(',')))
  // a fallen hoop's broken ribs and the cut canvas on the ground
  for (const r of M.ribs) slab(c, ...r, C.slab)
  for (const s of M.scraps) c.set(...s, K.scrap)
}

// --- Mark 4: the Drag ----------------------------------------------------------------------------------------------
function mark4(c, M) {
  const b = M.bed
  // the bed stood on its tail: its two long beams upright, the boards between
  for (let y = b.y0; y <= b.top; y++) for (let x = b.x0; x <= b.x1; x++)
    c.set(x, y, b.z, x === b.x0 || x === b.x1 ? C.post : C.planks, x === b.x0 || x === b.x1 ? { axis: 'y' } : {})
  // the axle across its back, both wheels still on it
  for (let x = b.x0; x <= b.x1; x++) c.set(x, M.axle.y, M.axle.z, C.log, { axis: 'x' })
  for (const hub of M.wheels) cartWheel(c, hub, 'x', { rim: { full: C.planks, stairs: C.stairs }, hubLog: C.post }) // a pale hub against the darker rim
  // the shafts to the sky and the swingletree across their tips
  const s = M.shafts
  for (const x of s.xs) for (let y = s.y0; y <= s.y1; y++) c.set(x, y, s.z, C.fence)
  for (let x = s.xs[0] + 1; x < s.xs[1]; x++) c.set(x, s.y1, s.z, C.fence)
  // the rags tied to the top of the bed, front and back
  for (const [x, y, z, f] of M.rags) c.set(x, y, z, K.rag, { facing: f })
  // detail: a bale fallen off the sledge, boards prised off the bed
  c.set(...M.sledge, C.sack, { axis: 'z' })
  for (const [x, y, z, f] of M.boards) trapdoor(c, x, y, z, C.trapdoor, f)
}

// --- Mark 5: the Shot Crate ----------------------------------------------------------------------------------------
function mark5(c, M) {
  for (const cr of M.crates) c.set(...cr, P.crate, { level: '0' })
  for (const [at, faces] of M.arrows) arrowStuds(c, at, faces, P.arrow)
  // the raiders' fire, still smoking, in a ring of slabs
  c.set(...M.fire.at, P.fire, { lit: 'true', signal_fire: 'false', facing: 'west', waterlogged: 'false' })
  for (const cb of M.fire.curb) slab(c, ...cb, R.curb)
  // their black standard, a lantern on its arm; the carter's last rag torn down it
  const st = M.standard
  warStandard(c, st.at, st, { log: R.log, fence: R.fence, banner: R.banner, lantern: P.lantern })
  const [lx, ly, lz, lf] = M.rag.low
  c.set(lx, ly, lz, K.rag, { facing: lf })
  for (const s of M.rag.scraps) c.set(...s, K.scrap)
  // their stakes, one capped with a skull
  for (const [x, y, z, h] of M.stakes) {
    for (let i = 0; i < h; i++) c.set(x, y + i, z, R.log, { axis: 'y' })
    c.set(x, y + h, z, R.fence)
  }
  c.set(...M.skull, R.skull, { rotation: '8' })
}

const DRAW = { 1: mark1, 2: mark2, 3: mark3, 4: mark4, 5: mark5 }

export function draw(n) {
  const M = MARKS[n]
  const c = new Canvas(M.size)
  const [w, , d] = M.size

  // --- Passes 1-2: block-out and detail (the mark's own drawing) --------------------------------------------------
  DRAW[n](c, M)

  // --- Pass 3: ground: every cell of y 0 drawn (it replaces the world's top block). Ruts of dirt path where nothing
  // stands over them; the drag scar on mark 4; trodden earth inside, a ragged grass rim, the raiders' darker ground.
  const trodden = mix(n === 5 ? P.ground.raiders : P.ground.trodden, 30 + n), rim = mix(P.ground.rim, 40 + n)
  const ruts = new Set((M.ruts ? wheelRuts(M.ruts) : []).map(r => r.join(',')))
  if (M.scar) for (let x = M.scar.x0; x <= M.scar.x1; x++) for (let z = M.scar.z0; z <= M.scar.z1; z++) ruts.add(`${x},${z}`)
  trailGround(c, w, d, (x, z, over) => {
    if (ruts.has(`${x},${z}`)) return over ? P.ground.under : P.ground.rut
    const edge = x === 0 || z === 0 || x === w - 1 || z === d - 1
    const corner = (x === 0 || x === w - 1) && (z === 0 || z === d - 1)
    if (!over && (corner || (edge && rim(x, 0, z) === 'grass_block'))) return ['grass_block', { snowy: 'false' }]
    const t = trodden(x, 0, z)
    return t === 'grass_block' || t === 'podzol' ? [t, { snowy: 'false' }] : t
  })

  // the pins hold, or the drawing is wrong
  for (const [[x, y, z], id] of PINS[n]) {
    if (c.name(x, y, z) !== `minecraft:${id}`) throw new Error(`mark ${n}: ${id} pinned at ${x},${y},${z}, drawn ${c.name(x, y, z)}`)
  }
  for (let x = 0; x < 3; x++) for (let z = 0; z < 3; z++)
    if (c.name(x, 1, z) === 'minecraft:grass_block') throw new Error(`mark ${n}: grass over the ground at ${x},1,${z}`)
  if (n === 4 && c.get(0, 1, 1)) throw new Error('mark 4: something over the pinned path')
  return c
}

export default function build(api) {
  const n = Number(process.env.MARK ?? 1)
  const c = draw(n)
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `trail_${n}_cut${cut}` : `trail_${n}` } }
}
