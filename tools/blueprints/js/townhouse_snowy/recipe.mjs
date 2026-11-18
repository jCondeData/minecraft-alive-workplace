// Burgher's Townhouse, the snowy look (ROADMAP 49.10b): assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roofs and gables, openings, depth and detail, the tower, interior, vault, snow and ground)
// for LEVEL = 1..4, each level the one before plus what it adds (II the counting-house wing, III the spired tower, IV
// the vault). Writes furniture.json (the furniture spots) and, for IV, out/cellar.txt. Build all four: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  LiftedCanvas, mix, plinth, floor, wallPanel, window, door, doorway, flaredGableRoof, gableWall, sideDormer,
  pyramidRoof, flight, railingRun, furnitureParts, cellar, hatchLadder, quoins, stringCourse, stair, snowLoad, snowSkirt,
  stormPorch, deepWindow, oriel, wideStack,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

function ring(c, x0, z0, x1, z1, y0, y1, id) {
  for (const [a, b] of [[[x0, z0], [x1, z0]], [[x0, z1], [x1, z1]], [[x0, z0], [x0, z1]], [[x1, z0], [x1, z1]]])
    wallPanel(c, a, b, y0, y1, id)
}

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  const P = PALETTE
  const H = MASSES.house, Wg = MASSES.wing, T = MASSES.tower
  const wall = mix(P.wall, 31), base = mix(P.plinth, 7), stack = mix(P.stack, 13)
  const has = o => (o.from ?? 1) <= level && level <= (o.until ?? 9)

  // --- Pass 1: block-out and structure ---------------------------------------------------------------------------
  // the house: one white stone mass from the plinth to the knee walls, the ground storey's front two thick
  plinth(c, H.x0, H.z0, H.x1, H.z1, base)
  floor(c, H.x0 + 1, 3, H.x1 - 1, 7, 0, P.floor) // the shop's boards
  floor(c, H.x0 + 1, 8, H.x1 - 1, H.z1 - 1, 0, P.kitchenFloor) // the kitchen's flags
  ring(c, H.x0, H.z0, H.x1, H.z1, 1, H.kneeTop - 1, wall)
  wallPanel(c, [H.x0 + 1, H.z0 + 1], [H.x1 - 1, H.z0 + 1], 1, 4, wall) // the front's inner skin
  for (const x of [H.x0, H.x1]) wallPanel(c, [x, H.z0], [x, H.z1], H.kneeTop, H.kneeTop, wall)
  floor(c, H.x0 + 1, H.z0 + 1, H.x1 - 1, H.z1 - 1, H.firstY, P.floor)
  floor(c, H.x0 + 1, H.z0 + 1, H.x1 - 1, H.z1 - 1, H.atticY, P.floor)
  if (level >= 2) { // the counting-house wing
    plinth(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, base)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 0, P.floor)
    ring(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, 1, 9, wall)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 5, P.floor)
  }

  // --- Pass 2: roofs and gables ----------------------------------------------------------------------------------
  const roof = flaredGableRoof(c, { ...H.roof, steep: true }, P.roof, P.roof.stairs)
  for (const z of [H.z0, H.z1]) gableWall(c, 'z', z, [H.x0, H.x1], H.kneeTop, roof.under, () => wall)
  // two gabled dormers on the long west slope lighting the attic (calcite faces between birch posts)
  const Dm = MASSES.dormers
  for (const z of Dm.at) {
    sideDormer(c, z, P.roof, { infill: [P.trim, { axis: 'z' }], glass: P.glass, cheek: P.roof.full }, { face: H.x0, y0: Dm.y0, overhang: false })
  }
  let wing = null
  if (level >= 2) {
    wing = flaredGableRoof(c, { ...Wg.roof, steep: true }, P.roof, P.roof.stairs, (x, y, z) => !c.get(x, y, z))
    for (const z of [Wg.z0, Wg.z1]) gableWall(c, 'z', z, [Wg.x0 + 1, Wg.x1 - 1], 10, wing.under, () => wall)
    // the wing's west kick lies under the house's slope but for its back overhang, where it would stand as a lump in
    // the valley: the house's own kick closes the valley there
    for (let z = Wg.roof.along[0]; z <= Wg.roof.along[1]; z++)
      if (c.name(Wg.roof.across[0], Wg.roof.eaveY, z) === `minecraft:${P.roof.stairs}`) c.clear(Wg.roof.across[0], Wg.roof.eaveY, z)
  }

  // --- Pass 3: openings ------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood })
  for (let y = 1; y <= 2; y++) c.clear(fd.at[0], y, fd.at[2] - 1) // the door's reveal through the outer skin
  c.set(fd.at[0], 0, fd.at[2] - 1, P.cheek)
  if (has(OPENINGS.wingDoor)) door(c, OPENINGS.wingDoor.at, 'south', 'north', { wood: P.wood, step: OPENINGS.wingDoor.step, stepStair: P.sill })
  for (const cells of OPENINGS.deep) deepWindow(c, cells, 'north', { glass: P.glass, lintel: P.trim, snow: P.snow })
  for (const w of OPENINGS.windows) if (has(w)) {
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, sill: P.sill })
    if (w.snow) { // snow on the sill: on the upside-down stair under each bottom pane
      const low = Math.min(...w.cells.map(p => p[1]))
      const d = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[w.out]
      for (const [x, y, z] of w.cells) if (y === low) c.set(x + d[0], y, z + d[1], P.snow, { layers: '1' })
    }
  }
  for (const d of OPENINGS.doorways) if (has(d)) for (const at of d.at) doorway(c, at)
  const pt = OPENINGS.partition
  wallPanel(c, [pt.x0, pt.z], [pt.x1, pt.z], H.firstY + 1, H.atticY - 1, P.partition)
  door(c, pt.door, 'south', 'south', { wood: P.wood })
  const st = OPENINGS.stairs, at = OPENINGS.attic
  for (const [x, y, z] of [...st.well, ...at.well]) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  flight(c, at.flight, at.rises, P.wood, P.partition, H.firstY + 1)
  railingRun(c, st.rail, P.wood)
  railingRun(c, at.rail, P.wood)

  // --- Pass 4: depth and detail ----------------------------------------------------------------------------------
  // grey dressed quoins up the corners, bands at the floors
  const corners = [[H.x0, H.z0], [H.x1, H.z0], [H.x0, H.z1], [H.x1, H.z1]]
  if (level >= 2) corners.push([Wg.x0, Wg.z0], [Wg.x1, Wg.z0], [Wg.x1, Wg.z1])
  quoins(c, corners.filter(([x, z]) => !(level >= 2 && x === H.x1 && z === H.z1)), 1, 8, P.quoin, P.quoinAlt)
  stringCourse(c, H.x0, H.z0, H.x1, H.z1, H.firstY, P.course)
  stringCourse(c, H.x0, H.z0, H.x1, H.z1, H.atticY, P.course)
  if (level >= 2) {
    stringCourse(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, 5, P.course)
    stringCourse(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, 9, P.course)
  }
  // the storm porch before the door and the hall's oriel standing on it
  const pc = MASSES.porch
  stormPorch(c, pc.x, pc.z, pc.top, { cheek: P.cheek, lintel: P.trim })
  c.set(pc.x, 0, pc.z, P.cheek)
  const O = MASSES.oriel
  for (const p of O.opening) c.clear(...p)
  oriel(c, O, { base: P.course, post: P.trim, glass: P.glass, roof: P.roof, snow: P.snow })
  if (level >= 2) { // the wing door's own storm porch, a little slate cap over it
    const wp = Wg.porch
    stormPorch(c, wp.x, wp.z, wp.top, { cheek: P.cheek, lintel: P.trim })
    stair(c, wp.x - 1, wp.top + 1, wp.z, P.roof.stairs, 'east')
    stair(c, wp.x + 1, wp.top + 1, wp.z, P.roof.stairs, 'west')
    c.set(wp.x, wp.top + 1, wp.z, P.roof.full)
  }
  // the back stack: three wide, two flues smoking, the hearth drawing into its middle
  const S = MASSES.stack
  wideStack(c, S, stack, { flues: S.flues, cap: P.capSlab })

  // --- Pass 5: the tower (III) -------------------------------------------------------------------------------------
  if (level >= 3) {
    plinth(c, T.x0, T.z0, T.x1, T.z1, base)
    floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, 0, P.kitchenFloor)
    ring(c, T.x0, T.z0, T.x1, T.z1, 1, T.top, wall)
    for (const y of T.floors) floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, y, P.floor)
    quoins(c, [[T.x0, T.z0], [T.x1, T.z0], [T.x0, T.z1], [T.x1, T.z1]], 1, T.top, P.quoin, P.quoinAlt)
    for (const y of T.floors) stringCourse(c, T.x0, T.z0, T.x1, T.z1, y, P.course)
    const peak = pyramidRoof(c, T.roof.cx, T.roof.cz, T.roof.r, T.roof.y0, P.roof)
    c.set(T.roof.cx, peak + 1, T.roof.cz, P.finial, { facing: 'up', powered: 'false', waterlogged: 'false' })
    for (const w of OPENINGS.windows) if (has(w) && w.from === 3) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
    for (const d of OPENINGS.doorways) if (has(d)) for (const a of d.at) doorway(c, a)
    const L = T.ladder
    hatchLadder(c, L.x, L.z, L.y0, L.top, L.facing, P.wood)
  }

  // --- Pass 6: interior (the plan's furniture) ---------------------------------------------------------------------
  for (const f of FURNITURE) if (has(f)) furnitureParts[f.part](c, f)
  for (const f of FURNITURE) if (has(f) && f.throat) c.clear(...f.throat)

  // --- Pass 7: the vault (IV) --------------------------------------------------------------------------------------
  let dug = null
  if (level >= 4) {
    const C = MASSES.cellar
    dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.cellarWall, 3), P.cellarFloor)
    hatchLadder(c, C.ladder.x, C.ladder.z, -C.depth + 1, 0, C.ladder.facing, P.wood)
    for (const z of C.ribs) {
      for (let x = C.x0 + 2; x <= C.x1 - 2; x++) c.set(x, -1, z, 'stone_bricks')
      stair(c, C.x0 + 1, -1, z, P.sill, 'west', 'top')
      stair(c, C.x1 - 1, -1, z, P.sill, 'east', 'top')
    }
  }

  // --- Pass 8: the verges, snow and ground ---------------------------------------------------------------------------
  // round 2: on each gable's overhanging rake row, the full block under every roof stair becomes an upside-down stair
  // facing the eave, so the rake's edge and its soffit run at the roof's pitch instead of stepping a block at a time
  const full = `minecraft:${P.roof.full}`, roofStairs = `minecraft:${P.roof.stairs}`
  const verge = (x0, x1, z) => {
    for (let x = x0; x <= x1; x++) for (let y = 0; y < c.size[1] - 1; y++) {
      const above = c.get(x, y + 1, z)
      const out = { east: 'west', west: 'east' }[above?.props?.facing]
      if (c.name(x, y, z) !== full || above?.id !== roofStairs || above.props.half !== 'bottom' || !out) continue
      stair(c, x, y, z, P.roof.stairs, out, 'top')
    }
  }
  for (const z of H.roof.along) verge(H.roof.across[0], H.roof.across[1], z)
  if (level >= 2) for (const z of Wg.roof.along) verge(Wg.roof.across[0] + 1, Wg.roof.across[1], z)

  // --- Pass 9: snow and ground -------------------------------------------------------------------------------------
  // snow on every ridge (three wide), along the outer eaves (the bell-cast kicks), on the spire's eave ring; never on a
  // tread mid-slope, never on the body's first course over a wall (a second line under the kick's), never in the
  // valley between the house and the wing
  const bodyEave = (x, y, z) => ((x === H.x0 || x === H.x1) && y === H.roof.eaveY + 1) ||
    (level >= 2 && (x === Wg.x0 || x === Wg.x1) && y === Wg.roof.eaveY + 1)
  const D = MASSES.dormers, dormerCell = (x, y, z) => x <= H.x0 + 2 && D.at.some(dz => Math.abs(z - dz) <= 2) && y > D.y0
  const valley = (x, y, z) => level >= 2 && x === Wg.x0 && y === H.roof.eaveY && z >= Wg.roof.along[0] && z <= Wg.roof.along[1] ||
    level >= 2 && x === Wg.roof.across[0] && y === Wg.roof.eaveY // the wing's west kick: only its back corner shows
  snowLoad(c, P.roof, P.snow, { eaves: true, tread: () => false,
    skip: (x, y, z) => dormerCell(x, y, z), eaveSkip: (x, y, z) => bodyEave(x, y, z) || valley(x, y, z) || dormerCell(x, y, z) })
  snowSkirt(c, P.snow, OUTSIDE.bare)
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = drawLevel(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  if (dug) { fs.mkdirSync(path.join(here, 'out'), { recursive: true }); fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(',')) }
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
