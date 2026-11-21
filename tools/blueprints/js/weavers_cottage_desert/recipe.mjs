// Weaver's Cottage, desert look (49.10d), the recipe: assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roof, openings, depth and detail, interior, ground) for LEVEL = 1 or 2, Level II all of
// Level I in its cells and the dye garden behind it. Writes furniture.json. Build both: build.sh. CUT=<y>: a cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  SIZE, PALETTE, RANGE, ARCADE, PORTAL, ORIEL, CATCHER, RACKS, VIGAS, OPENINGS, FURNITURE, FLEECES, FOLD, GARDEN,
} from './plan.mjs'
import {
  Canvas, mix, slab, stair, plinth, floor, wallPanel, window, door, quoins, flight, railing, railingRun, furnitureParts,
  windcatcher, archBay, vigas, dyeRows, rowParapet, DIRS,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = PALETTE, R = RANGE
  const ashlar = mix(P.ashlar, 5), mud = mix(P.mud, 6)
  const { x0, x1, z0, z1 } = R

  // --- Pass 1: block-out and structure -----------------------------------------------------------------------------
  // the range: a smooth plinth, the workroom's paving, coursed sandstone to the loft floor, the cut course at it, mud
  // brick up to the deck
  plinth(c, x0, z0, x1, z1, P.plinth)
  floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, P.floor)
  const sides = [[[x0, z0], [x1, z0]], [[x0, z1], [x1, z1]], [[x0, z0], [x0, z1]], [[x1, z0], [x1, z1]]]
  for (const [a, b] of sides) {
    wallPanel(c, a, b, 1, R.course - 1, ashlar)
    wallPanel(c, a, b, R.course, R.course, P.course)
    wallPanel(c, a, b, R.course + 1, R.wallTop, mud)
  }
  floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, R.loftY, P.loftFloor)
  quoins(c, [[x0, z0], [x1, z0], [x0, z1], [x1, z1]], 1, R.course - 1, P.quoin)

  // --- Pass 2: the roof: the deck, the coped parapet, the windcatcher ------------------------------------------------
  c.fill(x0, R.deckY, z0, x1, R.deckY, z1, P.deck)
  wallPanel(c, [x0, z0], [x1, z0], R.deckY, R.deckY, mud); wallPanel(c, [x0, z1], [x1, z1], R.deckY, R.deckY, mud)
  wallPanel(c, [x0, z0], [x0, z1], R.deckY, R.deckY, mud); wallPanel(c, [x1, z0], [x1, z1], R.deckY, R.deckY, mud)
  const K = CATCHER
  const inCatcher = (x, z) => x >= K.x0 && x <= K.x0 + 2 && z >= K.z0 && z <= K.z0 + 2
  rowParapet(c, { x0, z0, x1, z1, y: R.parapetY }, { wall: P.parapet, coping: P.coping }, inCatcher)
  windcatcher(c, K.x0, K.z0, K.y0, K.y1, { body: mix(P.catcher.body, 9), corner: P.catcher.corner, cap: P.catcher.cap, capTop: P.catcher.capTop })

  // --- Pass 3: openings ----------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor, od = OPENINGS.foldDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.door })
  door(c, od.at, od.facing, od.out, { wood: P.door })
  if (level >= 2) { const bd = OPENINGS.backDoor; door(c, bd.at, bd.facing, bd.out, { wood: P.door, step: [bd.at[0], 0, bd.at[2] + 1], stepStair: P.step }) }
  const ww = OPENINGS.weaverWindow
  window(c, ww.xs.flatMap(x => ww.ys.map(y => [x, y, ww.z])), 'north', null, { glass: P.glass })
  for (const w of OPENINGS.windows) {
    window(c, w.cells, w.out, null, { glass: P.glass })
    if (w.brow === false) continue
    const top = Math.max(...w.cells.map(p => p[1]))
    for (const [x, y, z] of w.cells) if (y === top && top + 1 !== R.course) c.set(x, top + 1, z, P.brow) // a cut brow
  }
  // the flight up to the loft (the shipped cells), its well, the rail along it
  const st = OPENINGS.stairs
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.under)
  railing(c, [[8, 5, 6], [9, 5, 6], [10, 5, 6]], P.wood)

  // --- Pass 4: depth and detail --------------------------------------------------------------------------------------
  // the arcade: piers on the street line, two round arches before the weaver's window, a deck over it at the loft floor
  // with a mud-brick balustrade (a little terrace under the loft's windows), a closed west end
  const A = ARCADE
  c.fill(A.x0, 0, A.z0, A.x1, 0, A.z1, P.paving)
  for (const x of A.piers) for (let y = 1; y < A.deckY; y++) c.set(x, y, A.z0, P.pier)
  for (let y = 1; y < A.deckY; y++) c.set(A.x0, y, A.z1, P.pier)
  for (const [a0, a1] of A.arches) archBay(c, { axis: 'x', line: A.z0, a0, a1, y: A.headY }, P.arch)
  c.fill(A.x0, A.deckY, A.z0, A.x1, A.deckY, A.z1, P.deck)
  for (let x = A.x0; x <= A.x1; x++) c.set(x, A.deckY + 1, A.z0, P.balustrade)
  c.set(A.x0, A.deckY + 1, A.z1, P.balustrade)
  for (const [x, y, z] of A.lamps) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // the portal: chiseled jambs and lintel round the door's reveal, the step before it, lamps on two short posts
  const Q = PORTAL
  for (let x = Q.x0; x <= Q.x1; x++) c.set(x, 0, Q.z, P.paving)
  for (const x of [Q.x0, Q.x1]) for (let y = 1; y <= Q.top; y++) c.set(x, y, Q.z, P.carved)
  c.set(fd.at[0], Q.top, Q.z, P.carved)
  for (let x = Q.x0; x <= Q.x1; x++) c.set(x, 0, 0, P.paving)
  stair(c, fd.at[0], 0, 0, P.step, 'south')
  for (const [x, z] of Q.lampPosts) { c.set(x, 1, z, P.lampPost); c.set(x, 2, z, 'lantern', { hanging: 'false', waterlogged: 'false' }) }
  // the lattice oriel over the portal: a bay two deep hung out over the street (z 0..1, y 5..7) before the loft's
  // window, its floor the portal's sill (z 1) and a row of corbels (z 0) at y 4, a lattice front between two slim
  // corner posts, lattice on both side faces, a projecting dark-oak cornice a block out round it at y 8
  const O = ORIEL, tdoor = (x, y, z, facing) => c.set(x, y, z, P.lattice, { facing, half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })
  for (let x = O.x0; x <= O.x1; x++) { c.set(x, O.y0, O.z, P.deck); stair(c, x, O.y0, O.z - 1, P.corbel, 'south', 'top') }
  for (let y = O.y0 + 1; y < O.y1; y++) {
    for (const x of [O.x0, O.x1]) c.set(x, y, O.z - 1, `${P.fence}_fence`) // the bay's slim corner posts
    tdoor((O.x0 + O.x1) / 2, y, O.z - 1, 'south') // the front lattice, its panel on the street face
    tdoor(O.x0, y, O.z, 'east') // the side lattices, their panels on the outer faces
    tdoor(O.x1, y, O.z, 'west')
  }
  for (let x = O.x0 - 1; x <= O.x1 + 1; x++) for (const z of [O.z - 1, O.z]) slab(c, x, O.y1, z, P.latticeRoof, 'bottom')
  // spruce beam ends a block out under the deck: the back, the ends, the front over the arcade's terrace
  const V = VIGAS
  vigas(c, 'z', z1 + 1, V.back, V.y, P.beam)
  vigas(c, 'z', z0 - 1, V.front, V.y, P.beam)
  vigas(c, 'x', x0 - 1, V.west, V.y, P.beam)
  vigas(c, 'x', x1 + 1, V.east, V.y, P.beam)
  // a proud cut-sandstone moulding at the loft floor round every face the arcade and the oriel leave clear: the drip
  // over the ground storey's lights and the sill line of the loft's
  for (const [side, cells] of [
    ['south', Array.from({ length: x1 - x0 + 1 }, (_, i) => [x0 + i, z1])],
    ['west', Array.from({ length: z1 - z0 + 1 }, (_, i) => [x0, z0 + i])],
    ['east', Array.from({ length: z1 - z0 + 1 }, (_, i) => [x1, z0 + i])],
    ['north', [[x1, z0]]],
  ]) {
    const [ox, oz] = DIRS[side]
    for (const [x, z] of cells) if (!c.get(x + ox, R.course, z + oz)) stair(c, x + ox, R.course, z + oz, P.moulding, { south: 'north', north: 'south', west: 'east', east: 'west' }[side], 'top')
  }
  // turned round the back west corner and the front east corner (the back east one is over the fold's wall and its
  // shade) (finish() shapes the outer corners), and carried along the
  // arcade's closed west end, so no run stops in a bare stair end at a corner
  for (const [x, z, f] of [[x0 - 1, z1 + 1, 'north'], [x1 + 1, z0 - 1, 'south'], [x0 - 1, 1, 'east'], [x0 - 1, 0, 'east']])
    if (!c.get(x, R.course, z)) stair(c, x, R.course, z, P.moulding, f, 'top')
  // the drying racks on the deck: posts, a rail, the dyed cloth hung on both sides
  for (const r of RACKS) {
    // posts up past the rail at each end, the rail (its log along it) between them: no log end shows
    for (const x of [r.x0, r.x1]) for (let y = R.parapetY; y <= R.parapetY + 2; y++) c.set(x, y, r.z, `${P.fence}_fence`)
    for (let x = r.x0 + 1; x < r.x1; x++) c.set(x, R.parapetY + 2, r.z, P.rail, { axis: 'x' })
    for (const [side, dz] of [['north', -1], ['south', 1]])
      r.cloth[side].forEach((col, i) => c.set(r.x0 + 1 + i, R.parapetY + 2, r.z + dz, `${col}_wall_banner`, { facing: side }))
  }

  // --- Pass 5: interior: the shipped cottage's furniture, cell for cell --------------------------------------------------
  for (const f of FURNITURE) {
    if (f.part === 'loom') { c.free(f.pos); c.set(...f.pos, 'loom', { facing: f.facing }); continue }
    furnitureParts[f.part](c, f)
  }
  for (const [x, y, z, w] of FLEECES) c.set(x, y, z, w)

  // --- Pass 6: ground: the fold, its shade, the front --------------------------------------------------------------------
  const F = FOLD, g = F.ground
  for (let x = g.x0; x <= g.x1; x++) for (let z = g.z0; z <= g.z1; z++) {
    const worn = F.worn.some(([a, b]) => a === x && b === z), sand = F.sand.some(([a, b]) => a === x && b === z)
    c.set(x, 0, z, worn ? 'coarse_dirt' : sand ? 'sand' : 'grass_block', worn || sand ? {} : { snowy: 'false' })
  }
  const bw = F.backWall
  for (let x = bw.x0; x <= bw.x1; x++) {
    c.set(x, 0, bw.z, P.plinth)
    for (let y = 1; y <= bw.top; y++) c.set(x, y, bw.z, mud(x, y, bw.z))
    slab(c, x, bw.top + 1, bw.z, P.coping, 'bottom')
  }
  for (const z of bw.west) {
    c.set(bw.x0, 0, z, P.plinth)
    for (let y = 1; y <= bw.top; y++) c.set(bw.x0, y, z, mud(bw.x0, y, z))
    slab(c, bw.x0, bw.top + 1, z, P.coping, 'bottom')
  }
  railingRun(c, F.fence, P.fence)
  for (const [x, , z] of F.fence) c.set(x, 0, z, P.kerb)
  for (let x = 12; x <= 17; x++) c.set(x, 0, 2, P.kerb)
  for (let z = 2; z <= 10; z++) c.set(17, 0, z, P.kerb)
  c.set(...F.gate.at, `${P.fence}_fence_gate`, { facing: F.gate.facing, in_wall: 'false', open: 'false', powered: 'false' })
  // the palm-rib shade against the range over the fold door: two posts, a beam along their heads, slabs over the rest
  const S = F.shade
  for (const z of S.posts) for (let y = 1; y < S.y; y++) c.set(S.x1, y, z, P.shade.post)
  for (let z = S.z0; z <= S.z1; z++) {
    c.set(S.x1, S.y, z, P.shade.beam, { axis: 'z' })
    for (let x = S.x0; x < S.x1; x++) slab(c, x, S.y, z, P.shade.slab, 'top')
  }
  for (const [x, y, z] of F.hay) c.set(x, y, z, 'hay_block', { axis: 'x' })
  c.set(...F.cornerLamp, 'lantern', { hanging: 'false', waterlogged: 'false' })
  // potted cacti by the portal's lamp posts, on the paving
  c.set(11, 0, 0, P.paving); c.set(11, 0, 1, P.paving)
  c.set(11, 1, 1, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })

  // --- Level II: the dye garden ------------------------------------------------------------------------------------------
  if (level >= 2) {
    const G = GARDEN, Wl = G.wall
    const wallAt = (x, z) => x === Wl.x0 || z === Wl.z1 || (x === Wl.x1 && z >= Wl.eastFrom)
    for (let x = Wl.x0; x <= Wl.x1; x++) for (let z = Wl.z0; z <= Wl.z1; z++) {
      if (!wallAt(x, z) || (x >= x0 && z <= z1)) continue
      c.set(x, 0, z, P.plinth)
      for (let y = 1; y <= Wl.top; y++) c.set(x, y, z, mud(x, y, z))
      slab(c, x, Wl.top + 1, z, P.coping, 'bottom')
    }
    for (let x = G.apron.x0; x <= G.apron.x1; x++) c.set(x, 0, G.apron.z, P.paving)
    for (let z = G.walk.z0; z <= G.walk.z1; z++) c.set(G.walk.x, 0, z, P.paving)
    const west = {}, east = {}
    for (const [z, [w, e]] of Object.entries(G.rows)) { west[z] = w; east[z] = e }
    dyeRows(c, G.beds.west[0], G.beds.west[1], west, P.soil)
    dyeRows(c, G.beds.east[0], G.beds.east[1], east, P.soil)
    for (const [b0, b1] of [G.beds.west, G.beds.east]) for (let x = b0; x <= b1; x++) c.set(x, 0, G.rill.z, P.water, { level: '0' })
    for (let x = G.end.x0; x <= G.end.x1; x++) if (x !== G.palm[0]) c.set(x, 0, G.end.z, P.paving)
    for (const [x, y, z] of G.jars) c.set(x, y, z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
    // the date palm: a tall bare trunk and a thin crown of long drooping fronds (a tuft, four long fronds, four short)
    const [px, py, pz] = G.palm, top = py + G.palmHeight, leaf = { persistent: 'true', distance: '1', waterlogged: 'false' }
    const put = (x, y, z) => { if (c.inside(x, y, z) && !c.get(x, y, z)) c.set(x, y, z, 'jungle_leaves', leaf) }
    c.set(px, py, pz, P.soil)
    for (let y = py + 1; y <= top; y++) c.set(px, y, pz, 'jungle_log', { axis: 'y' })
    put(px, top + 1, pz)
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]])
      for (const [k, dy] of [[1, 1], [2, 1], [2, 0], [3, 0], [3, -1]]) put(px + k * dx, top + dy, pz + k * dz)
    for (const [dx, dz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) { put(px + dx, top, pz + dz); put(px + dx, top - 1, pz + dz) }
  }
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const id = level === 1 ? 'weavers_cottage' : `weavers_cottage_${level}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
