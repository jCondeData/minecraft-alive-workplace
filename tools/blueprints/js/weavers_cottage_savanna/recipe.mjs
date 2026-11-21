// Weaver's Cottage, savanna look (49.10d), the recipe: assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roof, openings, depth and detail, interior, ground) for LEVEL = 1 or 2, Level II all of
// Level I in its cells and the dye garden behind it. Writes furniture.json. Build both: build.sh. CUT=<y>: a cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  SIZE, PALETTE, HOUSE, HIP, VERANDA, OPENINGS, FURNITURE, FLEECES, KRAAL, GARDEN,
} from './plan.mjs'
import {
  Canvas, mix, slab, plinth, floor, wallPanel, window, door, post, beamRing, flight, railing, railingRun, furnitureParts,
  hipRoof, verandaEave, dyeRows, tenterFrame,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = PALETTE, H = HOUSE
  const granite = mix(P.granite, 5), mud = mix(P.mud, 6), terra = mix(P.plinth, 7)
  const { x0, x1, z0, z1 } = H

  // --- Pass 1: block-out and structure -----------------------------------------------------------------------------
  // the house: a terracotta plinth, the workroom's floor, red granite to the loft floor, the grey acacia beam ring at
  // it, packed mud up under the thatch, acacia posts at the corners, the loft's boards
  plinth(c, x0, z0, x1, z1, terra)
  floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, P.floor)
  const sides = [[[x0, z0], [x1, z0]], [[x0, z1], [x1, z1]], [[x0, z0], [x0, z1]], [[x1, z0], [x1, z1]]]
  for (const [a, b] of sides) {
    wallPanel(c, a, b, 1, H.beamY - 1, granite)
    wallPanel(c, a, b, H.beamY + 1, H.wallTop, mud)
  }
  beamRing(c, x0, z0, x1, z1, H.beamY, P.frame)
  for (const [x, z] of [[x0, z0], [x1, z0], [x0, z1], [x1, z1]]) post(c, x, z, 1, H.wallTop, P.frame)
  floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, H.loftY, P.loftFloor)
  for (const x of H.jambs) for (const y of [H.beamY + 1, H.beamY + 2]) c.set(x, y, z0, P.jamb)
  // the painted band under the eave: red and white in turn between the corner posts
  for (const [[ax, az], [bx, bz]] of sides)
    for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++)
      if (!((x === x0 || x === x1) && (z === z0 || z === z1))) c.set(x, H.paintY, z, P.paint[(x + z) % 2])

  // --- Pass 2: the roof: one golden hip, eaves a block out on every side, a clay pot at each end of the ridge -------------
  hipRoof(c, { x0: HIP.x0, z0: HIP.z0, x1: HIP.x1, z1: HIP.z1, eaveY: HIP.eaveY }, P.roof)
  for (const [x, y, z] of HIP.finials) c.set(x, y, z, P.finial, { facing: 'north', cracked: 'false', waterlogged: 'false' })

  // --- Pass 3: openings ----------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor, od = OPENINGS.foldDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.door })
  door(c, od.at, od.facing, od.out, { wood: P.door })
  if (level >= 2) { const bd = OPENINGS.backDoor; door(c, bd.at, bd.facing, bd.out, { wood: P.door, step: [bd.at[0], 0, bd.at[2] + 1], stepStair: 'mud_brick_stairs' }) }
  const ww = OPENINGS.weaverWindow
  window(c, ww.xs.flatMap(x => ww.ys.map(y => [x, y, ww.z])), 'north', null, { glass: P.glass })
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.shutter })
  const st = OPENINGS.stairs
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.under)
  railing(c, [[8, 5, 6], [9, 5, 6], [10, 5, 6]], P.wood)

  // --- Pass 4: depth and detail: the veranda along the street ----------------------------------------------------------
  const V = VERANDA
  c.fill(V.x0, 0, V.zEave, V.x1, 0, V.zWall, P.deck)
  for (const x of V.posts) post(c, x, V.zEave, 1, V.y - 1, P.frame)
  verandaEave(c, { x0: V.x0, x1: V.x1, zEave: V.zEave, zWall: V.zWall, y: V.y, returns: [] }, P.roof)
  for (const [x, y, z] of V.lamps) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const [x, y, z] of V.jars) c.set(x, y, z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })

  // --- Pass 5: interior: the shipped cottage's furniture, cell for cell --------------------------------------------------
  for (const f of FURNITURE) {
    if (f.part === 'loom') { c.free(f.pos); c.set(...f.pos, 'loom', { facing: f.facing }); continue }
    furnitureParts[f.part](c, f)
  }
  for (const [x, y, z, w] of FLEECES) c.set(x, y, z, w)

  // --- Pass 6: ground: the kraal and its shelter -----------------------------------------------------------------------
  const K = KRAAL, g = K.ground
  for (let x = g.x0; x <= g.x1; x++) for (let z = g.z0; z <= g.z1; z++) {
    const worn = K.worn.some(([a, b]) => a === x && b === z)
    c.set(x, 0, z, worn ? 'coarse_dirt' : 'grass_block', worn ? {} : { snowy: 'false' })
  }
  railingRun(c, K.rail, P.fence)
  for (let i = 0; i + 1 < K.rail.length; i++) {
    const [ax, , az] = K.rail[i], [bx, , bz] = K.rail[i + 1]
    for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++) c.set(x, 0, z, P.kerb)
  }
  c.set(...K.gate.at, `${P.fence}_fence_gate`, { facing: K.gate.facing, in_wall: 'false', open: 'false', powered: 'false' })
  const S = K.shelter
  for (const [x, z] of S.posts) post(c, x, z, 1, S.eaveY, P.frame)
  hipRoof(c, { x0: S.x0, z0: S.z0, x1: S.x1, z1: S.z1, eaveY: S.eaveY }, P.roof)
  // boarded under the point (a fodder loft over the flock), the point filled
  for (let x = S.x0 + 1; x <= S.x1 - 1; x++) for (let z = S.z0 + 1; z <= S.z1 - 1; z++)
    if (!c.get(x, S.eaveY, z)) slab(c, x, S.eaveY, z, `${P.fence}_slab`, 'top')
  c.set(S.finial[0], S.eaveY + 1, S.finial[2], P.roof.full)
  c.set(...S.finial, P.finial, { facing: 'north', cracked: 'false', waterlogged: 'false' })
  for (const [x, y, z] of K.hay) c.set(x, y, z, 'hay_block', { axis: 'z' })
  c.set(...K.lamp, 'lantern', { hanging: 'false', waterlogged: 'false' })

  // --- Level II: the dye garden ------------------------------------------------------------------------------------------
  if (level >= 2) {
    const G = GARDEN, Wl = G.wall
    const wallAt = (x, z) => x === Wl.x0 || z === Wl.z1 || (x === Wl.x1 && z >= Wl.eastFrom)
    for (let x = Wl.x0; x <= Wl.x1; x++) for (let z = Wl.z0; z <= Wl.z1; z++) {
      if (!wallAt(x, z) || (x >= x0 && z <= z1)) continue
      c.set(x, 0, z, terra(x, 0, z))
      c.set(x, 1, z, P.gardenWall)
      slab(c, x, 2, z, P.roof.slab, 'bottom')
    }
    for (let x = 1; x <= 10; x++) for (let z = 9; z <= 15; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, 'grass_block', { snowy: 'false' })
    for (let z = G.walk.z0; z <= G.walk.z1; z++) c.set(G.walk.x, 0, z, P.path)
    const west = {}, east = {}
    for (const [z, [w, e]] of Object.entries(G.rows)) { west[z] = w; east[z] = e }
    dyeRows(c, G.beds.west[0], G.beds.west[1], west, P.soil)
    dyeRows(c, G.beds.east[0], G.beds.east[1], east, P.soil)
    for (let x = G.path.x0; x <= G.path.x1; x++) if (x !== G.tree[0]) c.set(x, 0, G.path.z, P.path)
    const t = G.tenter
    tenterFrame(c, { axis: 'x', line: t.z, u0: t.x0, u1: t.x1, posts: t.posts, y: t.y, sides: ['north', 'south'] }, { post: `${P.fence}_fence`, rail: P.frame, cloth: t.cloth })
    for (const [x, y, z] of G.jars) c.set(x, y, z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
    // a young acacia: a trunk leaning a block east, one flat crown three across with a frond out each side and a tuft
    // (small enough that no room is roofed under it)
    const [tx, ty, tz] = G.tree, T = P.tree, leaf = { persistent: 'true', distance: '1', waterlogged: 'false' }
    c.set(tx, ty, tz, T.soil, { snowy: 'false' })
    for (let y = 1; y <= 3; y++) c.set(tx, y, tz, T.log, { axis: 'y' })
    c.set(tx + 1, 4, tz, T.log, { axis: 'y' })
    for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) c.set(tx + 1 + dx, 5, tz + dz, T.leaves, leaf)
    for (const [dx, dz] of [[-2, 0], [2, 0], [0, -2], [0, 2]]) c.set(tx + 1 + dx, 5, tz + dz, T.leaves, leaf)
    c.set(tx + 1, 6, tz, T.leaves, leaf)
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
