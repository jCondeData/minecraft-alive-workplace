// Burgher's Townhouse (ROADMAP 49), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, roofs and gables, openings, depth and detail, interior, cellar and ground) for LEVEL = 1..4,
// each level the one before plus what it adds (II the counting-house wing, III the strongroom tower, IV the vault).
// Writes furniture.json (the furniture spots) and, for IV, out/cellar.txt. Build all four: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  LiftedCanvas, mix, plinth, floor, posts, beamRing, brackets, wallPanel, window, door, doorway, gableRoof, gableWall,
  bargeTrim, pyramidRoof, dormer, chimney, hood, flowerBed, flight, railingRun, furnitureParts, cellar, hatchLadder, quoins,
  stringCourse, stair,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

// a rectangle's wall ring (x0..x1, z0..z1) from y0 to y1
function ring(c, x0, z0, x1, z1, y0, y1, id) {
  for (const [a, b] of [[[x0, z0], [x1, z0]], [[x0, z1], [x1, z1]], [[x0, z0], [x0, z1]], [[x1, z0], [x1, z1]]])
    wallPanel(c, a, b, y0, y1, id)
}

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  const P = PALETTE
  const H = MASSES.house, Wg = MASSES.wing, T = MASSES.tower
  const base = mix(P.plinth, 7), clean = mix(P.stone, 31), worn = mix(P.weathered, 13)
  const stone = (x, y, z) => (y <= 1 ? worn : clean)(x, y, z) // weathering in a band at the foot, not confetti
  const has = o => (o.from ?? 1) <= level && level <= (o.until ?? 9)
  const log = P.frame

  // --- Pass 1: block-out and structure ---------------------------------------------------------------------------
  // House: the stone ground storey, then the jettied timber first floor and attic knee walls over it
  plinth(c, H.x0, H.z0, H.x1, H.z1, base)
  floor(c, H.x0 + 1, H.z0 + 1, H.x1 - 1, 7, 0, P.floor) // the shop's boards
  floor(c, H.x0 + 1, 8, H.x1 - 1, H.z1 - 1, 0, P.kitchenFloor) // the kitchen's flags
  ring(c, H.x0, H.z0, H.x1, H.z1, 1, 4, stone)
  const J = H.z0 - H.jetty // the jettied front line, z 1
  beamRing(c, H.x0, J, H.x1, H.z1, H.firstY, log)
  floor(c, H.x0 + 1, H.z0, H.x1 - 1, H.z1 - 1, H.firstY, P.floor)
  ring(c, H.x0, J, H.x1, H.z1, H.firstY + 1, H.atticY - 1, P.plaster)
  beamRing(c, H.x0, J, H.x1, H.z1, H.atticY, log)
  floor(c, H.x0 + 1, J + 1, H.x1 - 1, H.z1 - 1, H.atticY, P.floor)
  for (const x of [H.x0, H.x1]) wallPanel(c, [x, J], [x, H.z1], H.kneeTop, H.kneeTop, P.plaster) // knee walls
  // the frame: posts up the first floor and knee walls, a block of dark timber at every bay line
  posts(c, 'x', J, H.posts.front, H.firstY + 1, H.atticY - 1, log)
  posts(c, 'x', H.z1, H.posts.front, H.firstY + 1, H.atticY - 1, log)
  for (const x of [H.x0, H.x1]) posts(c, 'z', x, H.posts.side, H.firstY + 1, H.kneeTop, log)
  if (level >= 2) { // the counting-house wing: stone below, timber above, open to its roof
    plinth(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, base)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 0, P.floor)
    ring(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, 1, 4, stone)
    beamRing(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, 5, log)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 5, P.floor)
    ring(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, 6, 8, P.plaster)
    beamRing(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, 9, log)
    posts(c, 'x', Wg.z0, Wg.posts.front, 6, 8, log)
    posts(c, 'z', Wg.x1, Wg.posts.side, 6, 8, log)
  }

  // --- Pass 2: roofs and gables ----------------------------------------------------------------------------------
  const roof = gableRoof(c, H.roof, P.roof)
  // the gables: plaster between two studs, a king post over the top window carrying the hoist beam
  const gablePick = (x, y) => (x === 3 || x === 7) && y <= 14 ? [log, { axis: 'y' }] : x === 5 && y >= 15 ? [log, { axis: 'y' }] : P.plaster
  for (const z of [J, H.z1]) gableWall(c, 'z', z, H.roof.across, H.kneeTop, roof.under, gablePick)
  const Dm = MASSES.dormers
  for (const z of Dm.at) dormer(c, z, P.roof, P.plaster, P.glass, log, { face: Dm.face, eave: Dm.eave, y0: Dm.y0 })
  let wing = null
  if (level >= 2) {
    wing = gableRoof(c, Wg.roof, P.roof, (x, y, z) => !c.get(x, y, z))
    const wingPick = (x, y) => x === 13 && y >= 13 ? [log, { axis: 'y' }] : P.plaster
    for (const z of [Wg.z0, Wg.z1]) gableWall(c, 'z', z, [Wg.x0, Wg.x1], 10, wing.under, wingPick)
  }
  // the stack up the back gable: a stepped breast, past the ridge, smoke from a campfire on its solid column
  const ch = MASSES.chimney
  chimney(c, ch.x, ch.z, 0, ch.top, clean, { breast: { axis: 'x', top: 4, stair: P.sill } })
  for (const z of H.roof.along) bargeTrim(c, 'z', z, H.roof.across, roof.under, `${P.trim}_stairs`)
  if (wing) for (const z of Wg.roof.along) bargeTrim(c, 'z', z, Wg.roof.across, wing.under, `${P.trim}_stairs`)

  // --- Pass 3: openings ------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.sill })
  if (has(OPENINGS.wingDoor)) {
    const wd = OPENINGS.wingDoor
    door(c, wd.at, wd.facing, 'north', { wood: P.wood, step: wd.step, stepStair: P.sill })
  }
  for (const w of OPENINGS.windows) if (has(w))
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: w.pots ?? [], sill: P.sill })
  for (const d of OPENINGS.doorways) if (has(d)) for (const at of d.at) doorway(c, at)
  // the hall/chamber partition, a door in it
  const pt = OPENINGS.partition
  wallPanel(c, [pt.x0, pt.z], [pt.x1, pt.z], H.firstY + 1, H.atticY - 1, P.partition)
  door(c, pt.door, 'south', 'south', { wood: P.wood })
  // the two flights: the shop's up the east wall, the chamber's up the west wall to the attic
  const st = OPENINGS.stairs, at = OPENINGS.attic
  for (const [x, y, z] of [...st.well, ...at.well]) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  flight(c, at.flight, at.rises, P.wood, P.partition, H.firstY + 1)
  railingRun(c, st.rail, P.wood)
  railingRun(c, at.rail, P.wood)

  // --- Pass 4: depth and detail ----------------------------------------------------------------------------------
  // dressed quoins up the stone storey's corners
  quoins(c, [[H.x0, H.z0], [H.x1, H.z0], [H.x0, H.z1], [H.x1, H.z1]], 1, 4, P.quoin, 'stone_bricks')
  if (level >= 2) quoins(c, [[Wg.x1, Wg.z0], [Wg.x1, Wg.z1]], 1, 4, P.quoin, 'stone_bricks')
  // the jetty carried on dark brackets, lanterns hung from its beam either side of the door
  brackets(c, H.brackets.map(x => [x, H.firstY - 1, J]), `${P.trim}_stairs`, 'south')
  for (const x of [fd.at[0] - 1, fd.at[0] + 1]) c.set(x, H.firstY - 1, J, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // a step of plinth under each ground-floor sill, so no sill floats
  for (const [x, z] of [[2, 1], [3, 1], [7, 1], [8, 1], [3, 13], [7, 13]]) c.set(x, 0, z, base)
  if (level >= 2) for (const [x, z] of [[11, 3], [15, 3]]) c.set(x, 0, z, base)
  // the hoist beam at the gable's apex, a chain and a lantern hung from it
  const ho = MASSES.hoist
  c.set(ho.x, ho.y, ho.z, log, { axis: 'z' })
  c.set(ho.x, ho.y - 1, ho.z, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(ho.x, ho.y - 2, ho.z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  if (level >= 2) hood(c, OPENINGS.wingDoor.at[0], 4, OPENINGS.wingDoor.at[2] - 1, P.roof)

  // --- Pass 5: the tower (III) -------------------------------------------------------------------------------------
  if (level >= 3) {
    plinth(c, T.x0, T.z0, T.x1, T.z1, base)
    floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, 0, P.kitchenFloor)
    ring(c, T.x0, T.z0, T.x1, T.z1, 1, T.top, stone)
    for (const y of T.floors) floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, y, P.floor)
    quoins(c, [[T.x0, T.z0], [T.x1, T.z0], [T.x0, T.z1], [T.x1, T.z1]], 1, T.top, P.quoin, 'stone_bricks')
    stringCourse(c, T.x0, T.z0, T.x1, T.z1, T.floors[2], P.quoin)
    for (const y of T.floors) for (let x = T.x0 - 1; x <= T.x1 + 1; x++) for (let z = T.z0 - 1; z <= T.z1 + 1; z++) {
      const edge = x === T.x0 - 1 || x === T.x1 + 1 || z === T.z0 - 1 || z === T.z1 + 1
      if (edge && !c.get(x, y, z)) c.set(x, y, z, T.ledge, { type: 'top', waterlogged: 'false' })
    }
    const peak = pyramidRoof(c, T.roof.cx, T.roof.cz, T.roof.r, T.roof.y0, P.roof)
    c.set(T.roof.cx, peak + 1, T.roof.cz, P.finial, { facing: 'up', powered: 'false', waterlogged: 'false' })
    for (const w of OPENINGS.windows) if (has(w) && w.from === 3)
      window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
    for (const d of OPENINGS.doorways) if (has(d)) for (const a of d.at) doorway(c, a)
    const L = T.ladder
    hatchLadder(c, L.x, L.z, L.y0, L.top, L.facing, P.wood)
  }

  // --- Pass 6: interior (the plan's furniture) ---------------------------------------------------------------------
  for (const f of FURNITURE) if (has(f)) furnitureParts[f.part](c, f)
  // the fireplace's throat: the cell over the hearth fire open into the back wall, the stack behind it the flue
  for (const f of FURNITURE) if (has(f) && f.throat) c.clear(...f.throat)

  // --- Pass 7: the vault (IV) and the ground -----------------------------------------------------------------------
  let dug = null
  if (level >= 4) {
    const C = MASSES.cellar
    dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.cellarWall, 3), P.cellarFloor)
    hatchLadder(c, C.ladder.x, C.ladder.z, -C.depth + 1, 0, C.ladder.facing, P.wood)
    // two stone ribs across the vault under the shop's floor, springing from upside-down stairs at the walls
    for (const z of C.ribs) {
      for (let x = C.x0 + 2; x <= C.x1 - 2; x++) c.set(x, -1, z, 'stone_bricks')
      stair(c, C.x0 + 1, -1, z, P.sill, 'west', 'top')
      stair(c, C.x1 - 1, -1, z, P.sill, 'east', 'top')
    }
  }
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
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
