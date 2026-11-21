// Weaver's Cottage, the taiga look (ROADMAP 49.10d): assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roof, openings, depth and detail, interior, ground) for LEVEL = 1 or 2, II all of I in its
// cells and the dye garden behind. Writes furniture.json (the furniture spots). Build both: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, IDS, PALETTE, HOUSE, VERANDA, CHIMNEY, OPENINGS, FURNITURE, FLEECES, FOLD, BACK, GARDEN } from './plan.mjs'
import {
  Canvas, mix, stair, slab, floor, window, door, gableRoof, gableWall, bargeTrim, flight, railing, railingRun,
  furnitureParts, lightBand, plantFlower,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = PALETTE
  const W = HOUSE.walls, R = HOUSE.room
  const footing = mix(P.footing, 5), chimney = mix(P.chimney, 9)
  const log = axis => [P.log, { axis }]
  const put = (x, y, z, [id, props]) => c.set(x, y, z, id, props)

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // a mossy fieldstone plinth and course, the logs over it lying along each wall, the corners alternating
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    const edge = x === W.x0 || x === W.x1 || z === W.z0 || z === W.z1
    c.set(x, 0, z, edge ? footing : P.floor)
    if (edge) c.set(x, 1, z, footing)
  }
  for (let y = 2; y <= HOUSE.wallTop; y++) {
    const corner = y % 2 === 0 ? 'x' : 'z'
    for (let x = W.x0; x <= W.x1; x++) for (const z of [W.z0, W.z1]) put(x, y, z, log((x === W.x0 || x === W.x1) ? corner : 'x'))
    for (let z = W.z0 + 1; z < W.z1; z++) for (const x of [W.x0, W.x1]) put(x, y, z, log('z'))
  }
  floor(c, R.x0, R.z0, R.x1, R.z1, HOUSE.loftY, P.floor)

  // --- Pass 2: the broad roof swept past both walls, the log gables --------------------------------------------------
  const main = gableRoof(c, HOUSE.roof, P.roof, (x, y, z) => !(x === CHIMNEY.x && z === CHIMNEY.fire))
  for (const x of [W.x0, W.x1]) gableWall(c, 'x', x, [W.z0, W.z1], HOUSE.wallTop + 1, main.under, () => log('z'))
  for (const x of HOUSE.rakes) bargeTrim(c, 'x', x, HOUSE.roof.across, main.under, P.barge)

  // --- Pass 3: openings ----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor, od = OPENINGS.foldDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.wood })
  door(c, od.at, od.facing, od.out, { wood: P.wood })
  if (level >= 2) { const bd = OPENINGS.backDoor; door(c, bd.at, bd.facing, bd.out, { wood: P.wood }) }
  const ww = OPENINGS.weaverWindow
  lightBand(c, { axis: 'x', line: ww.z, u0: ww.x0, u1: ww.x1, y0: ww.y0, y1: ww.y1, lights: ww.lights }, { glass: P.glass, mullion: P.mullion })
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, null, { glass: P.glass })
  const st = OPENINGS.stairs
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railing(c, [[8, 5, 6], [9, 5, 6], [10, 5, 6]], P.wood)

  // --- Pass 4: depth and detail ----------------------------------------------------------------------------------
  // the pale band at the loft floor, round the walls
  for (let x = W.x0; x <= W.x1; x++) for (const z of [W.z0, W.z1]) c.set(x, HOUSE.band, z, P.band, { axis: 'x' })
  for (let z = W.z0 + 1; z < W.z1; z++) for (const x of [W.x0, W.x1]) c.set(x, HOUSE.band, z, P.band, { axis: 'z' })
  // log ends past every corner on alternate courses (the front and back logs on the even ones, the gables' on the odd)
  // (saddle notches: both logs cross past the corner on every course, the gables' under the sweep's soffits)
  for (let y = 2; y <= HOUSE.wallTop; y++) {
    for (const x of [W.x0 - 1, W.x1 + 1]) for (const z of [W.z0, W.z1]) put(x, y, z, log('x'))
    if (y < HOUSE.roof.eaveY + 1) for (const x of [W.x0, W.x1]) for (const z of [W.z0 - 1, W.z1 + 1]) put(x, y, z, log('z'))
  }
  // the sweeps' soffits: boards under the roof's first course over the veranda and the back, closing the pocket at the wall head
  for (let x = W.x0; x <= W.x1; x++) for (const z of [W.z0 - 1, W.z1 + 1]) if (!c.get(x, HOUSE.roof.eaveY, z)) c.set(x, HOUSE.roof.eaveY, z, P.floor)
  // the veranda: a deck under the front sweep, log posts on a log plate, brackets, lanterns, the step
  const V = VERANDA
  for (let x = V.x0; x <= V.x1; x++) for (let z = V.z0; z <= V.z1; z++) c.set(x, 0, z, P.deck)
  stair(c, 9, 0, 0, `${P.wood}_stairs`, 'south')
  for (let x = V.x0; x <= V.x1; x++) c.set(x, V.plate, V.z0, P.log, { axis: 'x' })
  for (const px of V.posts) {
    for (let y = 1; y < V.plate; y++) c.set(px, y, V.z0, P.post, { axis: 'y' })
    for (const dx of [-1, 1]) if (px + dx >= V.x0 && px + dx <= V.x1) stair(c, px + dx, V.plate - 1, V.z0, P.bracket, dx < 0 ? 'east' : 'west', 'top')
  }
  for (const lx of V.lamps) c.set(lx, V.plate - 1, V.z0, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const [x, y, z] of V.pots) c.set(x, y, z, P.pot)
  for (const x of [W.x0, W.x1]) c.set(x, 1, V.z1, footing) // the corner notches' footing stones beside the end posts
  for (let x = ww.x0; x <= ww.x1; x++) stair(c, x, 1, 1, P.bracket, 'south', 'top') // the window's sill on the deck
  // the chimney: broad mossy fieldstone up the west gable, through the rake, one fire and slabs on top
  const Ch = CHIMNEY
  // (the breast three wide to the wall head, weathered in on stone stairs, the stack one flue up through the rake)
  for (const z of Ch.zs) for (let y = 0; y <= Ch.breast; y++) c.set(Ch.x, y, z, chimney)
  stair(c, Ch.x, Ch.breast + 1, Ch.zs[0], P.weather, 'south'); stair(c, Ch.x, Ch.breast + 1, Ch.zs[2], P.weather, 'north')
  for (let y = Ch.breast + 1; y <= Ch.y1; y++) c.set(Ch.x, y, Ch.fire, chimney)
  c.set(Ch.x, Ch.y1 + 1, Ch.fire, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })

  // --- Pass 5: interior (the shipped furniture, cell for cell) -----------------------------------------------------
  for (const z of [4, 6]) for (const y of [1, 2]) c.set(2, y, z, chimney) // the hearth's breast and lintel
  for (let z = 4; z <= 6; z++) c.set(2, 3, z, P.log, { axis: 'z' })
  for (const f of FURNITURE) {
    if (f.part === 'loom') { c.free(f.pos); c.set(...f.pos, 'loom', { facing: f.facing }); continue }
    furnitureParts[f.part](c, f)
  }
  for (const [x, y, z, w] of FLEECES) c.set(x, y, z, typeof w === 'number' ? P.wool[w] : w)

  // --- Pass 6: ground: the fold, the back ------------------------------------------------------------------------
  const fg = FOLD.ground
  for (let x = fg.x0; x <= fg.x1; x++) for (let z = fg.z0; z <= fg.z1; z++) {
    const trodden = FOLD.trodden.some(([tx, tz]) => tx === x && tz === z)
    c.set(x, 0, z, trodden ? 'coarse_dirt' : 'grass_block', trodden ? {} : { snowy: 'false' })
  }
  // the lean-to: log end walls, its pent falling east from the gable, hay inside
  const S = FOLD.shed
  for (const z of [S.z0, S.z1]) for (let x = S.x0; x <= S.x1; x++) {
    c.set(x, 0, z, footing)
    for (let y = 1; y < S.topY - (x - S.x0); y++) c.set(x, y, z, P.log, { axis: 'x' })
  }
  for (let x = S.x0; x <= S.x1; x++) for (let z = S.z0; z <= S.z1; z++) stair(c, x, S.topY - (x - S.x0), z, P.roof.stairs, 'west')
  // the gable's logs carried out over the pent to the rake's barge boards (the slot kit.seal_slots would close anyway)
  for (let z = S.z0; z <= S.z1; z++) for (let y = S.topY + 1; y < main.under[z] - 1; y++) c.set(S.x0, y, z, P.log, { axis: 'z' })
  for (const [x, y, z] of FOLD.hay) c.set(x, y, z, 'hay_block', { axis: 'z' })
  railingRun(c, FOLD.fence, P.wood)
  const g = FOLD.gate
  c.set(...g.at, `${P.wood}_fence_gate`, { facing: g.facing, in_wall: 'false', open: 'false', powered: 'false' })
  c.set(...FOLD.cornerLamp, 'lantern', { hanging: 'false', waterlogged: 'false' })
  // under the back sweep: firewood laid along the wall, ferns in moss under the windows, a lantern hung from the eave
  const fw = BACK.firewood
  for (let x = fw.x0; x <= fw.x1; x++) for (let y = 0; y <= fw.top; y++)
    if (y < fw.top || (x > fw.x0 && x < fw.x1)) c.set(x, y, fw.z, P.firewood, { axis: 'x' }) // two high, a shorter course on top
  for (let x = BACK.ferns.x0; x <= BACK.ferns.x1; x++) { c.set(x, 0, BACK.ferns.z, P.soil); c.set(x, 1, BACK.ferns.z, P.fern) }
  c.set(...BACK.lamp, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // --- Level II: the dye garden in its clearing --------------------------------------------------------------------
  if (level >= 2) {
    const G = GARDEN
    for (let x = 1; x <= 10; x++) for (let z = 10; z <= 15; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, 'grass_block', { snowy: 'false' })
    for (let z = G.walk.z0; z <= G.walk.z1; z++) if (z < 16) c.set(G.walk.x, 0, z, P.walk)
    railingRun(c, G.fence, P.wood)
    for (const b of G.beds) {
      for (const [ax, az, bx, bz, axis] of b.kerb)
        for (let x = ax; x <= bx; x++) for (let z = az; z <= bz; z++) c.set(x, 0, z, P.rail, { axis })
      for (let z = b.z0; z <= b.z1; z++) for (let x = b.x0; x <= b.x1; x++) {
        c.set(x, 0, z, 'grass_block', { snowy: 'false' })
        const row = b.rows[z]
        plantFlower(c, x, 1, z, row[(x - b.x0) % row.length])
      }
    }
    // the hesje: log posts, stripped rails between them in two tiers, the dyed cloth hung from both faces of each rail
    const H = G.hesje
    let i = 0
    for (let z = H.z0; z <= H.z1; z++) {
      if (H.posts.includes(z)) { for (let y = 1; y <= Math.max(...H.rails); y++) c.set(H.x, y, z, P.rail, { axis: "y" }); continue } // pale stripped posts (critic polish)
      for (const y of H.rails) {
        c.set(H.x, y, z, P.rail, { axis: 'z' })
        for (const [dx, facing] of [[-1, 'west'], [1, 'east']])
          c.set(H.x + dx, y, z, `${H.cloth[i++ % H.cloth.length]}_wall_banner`, { facing })
      }
    }
  }
  // every fence and gate stands on a footing stone (the ground layer is the build's own)
  for (let x = 0; x < c.size[0]; x++) for (let z = 0; z < c.size[2]; z++)
    if (c.name(x, 1, z).includes('fence') && !c.get(x, 0, z)) c.set(x, 0, z, footing)
  return c
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
