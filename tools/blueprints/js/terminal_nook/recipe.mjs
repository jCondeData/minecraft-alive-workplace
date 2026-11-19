// Terminal Nook, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out and
// structure, openings, depth and detail, the store, interior, ground) and returns the structure for the
// minecraft-builder skill's runner. One plan, two drawings: TOMS=1 draws the Tom's Simple Storage copy (the hub chest
// becomes the inventory connector, the tally block the storage terminal; every other cell the same), otherwise the
// vanilla build. Also writes furniture.json (every furniture spot) for tools/blueprints/rebuild.py.
// Build: see package.json; CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, STORE, OPENINGS, FURNITURE, HAY } from './plan.mjs'
import {
  Canvas, mix, louvre, floor, post, beamRing, window, door, flaredGableRoof, gableWall, bargeTrim, railing,
  furnitureParts, staddle, sideFlight, storeStack, twin,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(toms = false) {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const stem = mix(P.stem, 5)
  const S = MASSES.store
  const { x0, x1, z0, z1 } = S.frame
  const rm = S.room
  const [w0, w1] = S.wallY

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // the staddles, then the sill beams on their caps and the deck between them
  for (const x of MASSES.staddles.xs) for (const z of MASSES.staddles.zs) staddle(c, x, z, { stem, cap: P.cap })
  beamRing(c, x0, z0, x1, z1, S.deckY, P.frame)
  floor(c, rm.x0, rm.z0, rm.x1, rm.z1, S.deckY, P.floor)
  // the joists under the deck, set in from the sill beams: the dark band between the caps, the crawl under the stems
  for (let x = rm.x0; x <= rm.x1; x++) for (let z = rm.z0; z <= rm.z1; z++) c.set(x, S.deckY - 1, z, P.frame, { axis: 'x' })
  // the frame: posts on every staddle line, a wall plate round the top
  for (const x of S.frontPosts) post(c, x, z0, w0, w1, P.frame)
  for (const x of S.backPosts) post(c, x, z1, w0, w1, P.frame)
  for (const z of S.sidePosts) { post(c, x0, z, w0, w1, P.frame); post(c, x1, z, w0, w1, P.frame) }
  beamRing(c, x0, z0, x1, z1, S.plateY, P.frame)
  // weatherboards between the posts, laid along each wall
  const board = (x, y, z, axis) => { if (!c.get(x, y, z)) c.set(x, y, z, P.boards, { axis }) }
  for (let y = w0; y <= w1; y++) {
    for (let x = x0; x <= x1; x++) { board(x, y, z0, 'x'); board(x, y, z1, 'x') }
    for (let z = z0; z <= z1; z++) { board(x0, y, z, 'z'); board(x1, y, z, 'z') }
  }
  // the truss: a tie beam across the room over the side posts, a king post from it to the ridge
  const T = MASSES.truss
  for (let x = rm.x0; x <= rm.x1; x++) c.set(x, T.y, T.z, P.frame, { axis: 'x' })

  // the roof: 45 degrees and bell-cast, its kick at the wall plate, one block over each gable
  const roof = flaredGableRoof(c, MASSES.roof, P.roof, P.roof.stairs)
  for (let y = T.y + 1; y < roof.ridgeY; y++) c.set(T.kingX, y, T.z, P.frame, { axis: 'y' })
  // the gables: boards between two studs, the vent between them, a king post from the vent to the apex
  const R = MASSES.roof
  for (const line of [z0, z1]) {
    gableWall(c, 'z', line, R.across, S.plateY + 1, roof.under, (u, y) => {
      if (u === 3 || u === 7) return [P.frame, { axis: 'y' }]
      if (u === 5 && y >= 10) return [P.frame, { axis: 'y' }]
      return [P.boards, { axis: 'x' }]
    })
  }

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const d = OPENINGS.door
  door(c, d.at, d.facing, 'north', { wood: P.wood })
  // the proud frame first, so every window's dressing sits in its recess on the rail
  const F = MASSES.proud
  const [py0, py1] = F.y
  for (let x = x0; x <= x1; x++) { c.set(x, py0, F.front.z, P.frame, { axis: 'x' }); c.set(x, py0, F.back.z, P.frame, { axis: 'x' }) }
  for (const sx of F.sides.xs) for (let z = z0; z <= z1; z++) c.set(sx, py0, z, P.frame, { axis: 'z' })
  for (const x of F.front.posts) post(c, x, F.front.z, py0 + 1, py1, P.frame)
  for (const x of F.back.posts) post(c, x, F.back.z, py0 + 1, py1, P.frame)
  for (const sx of F.sides.xs) for (const z of F.sides.posts) post(c, sx, z, py0 + 1, py1, P.frame)
  for (let x = x0; x <= x1; x++) if (!F.back.posts.includes(x)) c.set(x, F.back.girtY, F.back.z, P.frame, { axis: 'x' })
  // under the girt a proud dado of boards, so the back's lower panels close against the frame (the chest wall's side)
  for (let x = x0; x <= x1; x++) for (let y = py0 + 1; y < F.back.girtY; y++) if (!c.get(x, y, F.back.z)) c.set(x, y, F.back.z, P.boards, { axis: 'x' })
  // windows: the front's sills with pots, the sides' shutters over sills, the back vents' shutters over the girt
  for (const w of OPENINGS.windows) {
    if (w.dress === 'sill') { window(c, w.cells, w.out, 'sill', { glass: P.glass, wood: P.wood, pots: w.pots ?? [] }); continue }
    window(c, w.cells, w.out, 'shutters', { glass: P.glass, wood: P.wood })
    if (w.sill !== 'girt') window(c, w.cells, w.out, 'sill', { glass: P.glass, wood: P.wood })
  }
  for (const v of OPENINGS.gableVents) window(c, v.cells, v.z === z0 ? 'north' : 'south', null, { glass: P.glass })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // the perron: a stone flight climbing along the front to a timber landing on two more staddles, a step on the sill
  // rail between the door's proud posts, the lamp on the landing's corner post
  const pr = MASSES.perron
  sideFlight(c, pr.flight, pr.steps, pr.rises, { steps: P.steps, under: stone })
  const L = pr.landing
  for (const x of L.staddles) staddle(c, x, L.z, { stem, cap: P.cap })
  for (let x = L.x0; x <= L.x1; x++) c.set(x, L.y, L.z, P.floor)
  railing(c, [[L.x1, L.y + 1, L.z]], P.wood)
  c.set(L.x1, L.y + 2, L.z, 'lantern', { hanging: 'false', waterlogged: 'false' })
  // an eave course under the kick on both long sides: dark stairs, upside down, between the proud posts
  for (const sx of F.sides.xs) for (let z = R.along[0]; z <= R.along[1]; z++)
    if (!c.get(sx, py1, z)) c.set(sx, py1, z, `${P.trim}_stairs`, { facing: sx === 0 ? 'east' : 'west', half: 'top', shape: 'straight', waterlogged: 'false' })
  // barge boards under both rakes
  for (const line of R.along) bargeTrim(c, 'z', line, [R.across[0] + 1, R.across[1] - 1], roof.under, `${P.trim}_stairs`)
  // the grain louvre astride the ridge over the truss: the store's breath, a lamp in it at night
  const lv = MASSES.louvre
  louvre(c, lv.x, lv.z, roof.ridgeY, { base: P.louvreBase, post: `${P.trim}_fence`, roof: P.roof, finial: 'lightning_rod' })

  // --- Pass 4: the store (the cells the GameTests pin) -------------------------------------------------------------
  const st = STORE
  c.set(...st.storehouse.at, P.job, { facing: st.storehouse.facing })
  const cw = st.chestWall
  for (const x of cw.xs) storeStack(c, [x, cw.z], cw.ys, cw.facing, P.chest)
  twin(c, st.hub.at, toms,
    [P.chest, { facing: st.hub.facing, type: 'single', waterlogged: 'false' }],
    [P.connector, {}])
  twin(c, st.tally.at, toms,
    [P.tally, { axis: 'y' }],
    [P.terminal, { facing: st.tally.reads, pos: 'center' }])
  storeStack(c, st.barrels.at, st.barrels.ys, st.barrels.facing, P.barrel)

  // --- Pass 5: interior (the plan's furniture), hay in the back corners -------------------------------------------
  for (const f of FURNITURE) furnitureParts[f.part](c, f)
  for (const [x, y, z] of HAY) c.set(x, y, z, 'hay_block', { axis: 'y' })

  return c
}

export default function build(api) {
  const toms = process.env.TOMS === '1'
  const c = draw(toms)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const name = (toms ? 'terminal_nook_toms' : 'terminal_nook') + (cut !== undefined ? `_cut${cut}` : '')
  return { structure: canvas.toStructure(api), metadata: { name } }
}
