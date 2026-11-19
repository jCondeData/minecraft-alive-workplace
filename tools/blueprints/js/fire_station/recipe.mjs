// Fire Station, the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49) in passes (block-out
// and structure, openings, depth and detail, interior, ground and light) and returns the structure for the
// minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded).
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, WORK, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, clumped, plinth, floor, post, beamRing, wallPanel, window, door, doorway, quoins, gableRoof, gableWall,
  bargeTrim, brackets, ladderShaft, lampPost, furnitureParts,
} from '../parts/index.mjs'
import { pailRack, trough, downpipe, segmentalArch, hoseReel, lookoutGallery } from '../parts/fire_station_watch.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = clumped(mix(P.stone, 11), 2)
  const footing = mix(P.footing, 5)
  const flags = mix(P.floor, 3)
  const E = MASSES.engine, L = MASSES.loft, T = MASSES.tower
  const W = E.walls, B = E.bay, TW = T.walls, TI = T.inside
  const inTower = (x, z) => x >= TW.x0 && x <= TW.x1 && z >= TW.z0 && z <= TW.z1

  // --- Pass 1: block-out and structure ---------------------------------------------------------------------------
  // the footing, a block wider than the walls (sills stand on it), the tower's own
  plinth(c, E.footing.x0, E.footing.z0, E.footing.x1, E.footing.z1, footing)
  plinth(c, T.footing.x0, T.footing.z0, T.footing.x1, T.footing.z1, footing)
  floor(c, B.x0, B.z0, B.x1, B.z1, 0, flags)
  floor(c, TI.x0, TI.z0, TI.x1, TI.z1, 0, flags)

  // Engine house: stone-brick walls y 1..4
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, E.wallTop, stone)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, E.wallTop, stone)
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, E.wallTop, stone)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, E.wallTop, stone)

  // Watch loft: the sill beam ring (the jetty's beam out at z 2), the floor, plank walls between dark posts
  const lb = L.beam
  beamRing(c, lb.x0, lb.z0, lb.x1, lb.z1, lb.y, P.frame)
  floor(c, L.floor.x0, L.floor.z0, L.floor.x1, L.floor.z1, lb.y, P.loftFloor)
  const [wy0, wy1] = L.wallY
  wallPanel(c, [lb.x0, lb.z0], [lb.x0, lb.z1], wy0, wy1, P.infill) // west
  wallPanel(c, [lb.x1, 6], [lb.x1, lb.z1], wy0, wy1, P.infill) // east, behind the tower
  for (const z of L.westPosts) post(c, lb.x0, z, wy0, wy1, P.frame)
  for (const z of L.eastPosts) post(c, lb.x1, z, wy0, wy1, P.frame)

  // Roof: one 45-degree red tile roof, ridge along z; the tower owns its own cells
  const R = MASSES.roof
  const main = gableRoof(c, R, P.roof, (x, y, z) => !inTower(x, z))

  // Gables: plank infill, dark posts in line with the bays, a collar over the watch window, a king post to the ridge
  const gable = posts => (u, y) => {
    if (posts.includes(u) && y <= wy1) return [P.frame, { axis: 'y' }]
    if (y === 8 && u >= 3 && u <= 7) return [P.frame, { axis: 'x' }]
    if (u === 5 && y === 11) return [P.frame, { axis: 'y' }]
    return P.infill
  }
  gableWall(c, 'z', lb.z0, [1, 8], wy0, main.under, gable(L.frontPosts))
  gableWall(c, 'z', lb.z1, [1, 9], wy0, main.under, gable(L.backPosts))

  // Hose tower: the shaft y 1..10 (its west wall is the engine house's east wall), the landing at the loft's floor
  for (let x = TW.x0; x <= TW.x1; x++) for (let z = TW.z0; z <= TW.z1; z++) {
    const wall = x === TW.x0 || x === TW.x1 || z === TW.z0 || z === TW.z1
    for (let y = 1; y <= T.shaftTop; y++) {
      if (wall) c.set(x, y, z, stone)
      else c.clear(x, y, z)
    }
  }
  floor(c, TI.x0, TI.z0, TI.x1, TI.z1, T.landingY, P.loftFloor)
  const [cx, cz] = T.centre
  const lad = OPENINGS.ladder
  const top = lookoutGallery(c, cx, cz, T.half, T.shaftTop, {
    corbel: P.stoneStairs, deck: P.loftFloor, post: P.frame, rail: `${P.wood}_fence`, beam: P.frame,
    ceiling: `${P.trim}_planks`, roof: P.roof, apex: [P.copper, { facing: 'up', powered: 'false', waterlogged: 'false' }],
  }, { height: 2, hole: [lad.x, lad.z] })

  // --- Pass 2: openings ----------------------------------------------------------------------------------------------
  const ed = OPENINGS.engineDoors
  ed.cells.forEach((at, i) => door(c, at, ed.facing, 'north', { wood: P.wood, hinge: ed.hinges[i] }))
  for (const [x] of ed.cells) c.set(x, 0, ed.step, P.stoneStairs, { facing: 'south', half: 'bottom', shape: 'straight', waterlogged: 'false' })
  const ar = OPENINGS.arch
  segmentalArch(c, 'x', ar.line, ar.a0, ar.y, { stairs: P.stoneStairs, slabId: P.stoneSlab, key: P.key })
  doorway(c, OPENINGS.towerDoorway.at, OPENINGS.towerDoorway.height)
  doorway(c, OPENINGS.loftDoorway.at, OPENINGS.loftDoorway.height)
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, sill: P.stoneStairs })
  // the ladder up the tower's back wall, through the landing and the lookout's deck
  c.clear(lad.x, T.landingY, lad.z)
  ladderShaft(c, lad.x, lad.z, lad.y0, lad.y1 - 1, lad.facing)
  // a hatch in the lookout's deck over the ladder's top (shut: the gallery's weather stays out of the tower)
  c.set(lad.x, lad.y1, lad.z, `${P.wood}_trapdoor`, { facing: lad.facing, half: 'top', open: 'false', powered: 'false', waterlogged: 'false' })

  // --- Pass 3: depth and detail -------------------------------------------------------------------------------------
  // dressed corners: deepslate quoins up the engine house's three free corners and the tower's
  quoins(c, [[W.x0, W.z0], [W.x0, W.z1], [W.x1, W.z1]], 1, E.wallTop, P.quoin[0], P.quoin[1])
  quoins(c, [[TW.x0, TW.z0], [TW.x1, TW.z0], [TW.x1, TW.z1]], 1, T.shaftTop, P.quoin[0], P.quoin[1])
  // the jetty: dark brackets under the loft's front beam, at its posts
  brackets(c, L.brackets.map(x => [x, 4, lb.z0]), `${P.trim}_stairs`, 'south')
  // barge boards under the front and back rakes (45 degrees: stairs)
  bargeTrim(c, 'z', R.along[0], R.across, main.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', R.along[1], R.across, main.under, `${P.trim}_stairs`)
  // tie beams across the loft (the lamps hang between them from the ridge)
  for (const z of L.tieBeams.z) for (let x = L.tieBeams.x0; x <= L.tieBeams.x1; x++) c.set(x, L.tieBeams.y, z, P.frame, { axis: 'x' })

  // --- Pass 4: the Firewarden's work and the interior ------------------------------------------------------------------
  c.set(...WORK.grindstone.at, 'grindstone', { face: 'floor', facing: WORK.grindstone.facing })
  trough(c, WORK.trough.cells, WORK.trough.y, P.wetStone)
  const dp = WORK.downpipe
  downpipe(c, dp.x, dp.z, dp.y0, dp.y1, P.copper, WORK.spout, 'east')
  const rk = WORK.racks
  pailRack(c, rk.cells, rk.tiers, { shelf: `${P.wood}_slab`, post: `${P.wood}_log`, ends: rk.ends, postTop: rk.postTop })
  hoseReel(c, WORK.hoseReel.at, WORK.hoseReel.axle, WORK.hoseReel.pull, { coil: `${P.accent}_wool`, wheel: `${P.trim}_trapdoor`, handle: `${P.wood}_fence` })
  for (const f of FURNITURE) furnitureParts[f.part](c, f)

  // --- Pass 5: ground and light ----------------------------------------------------------------------------------------
  for (const [x, y, z] of OUTSIDE.doorLamps) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  const [px, , pz] = OUTSIDE.lampPost
  lampPost(c, px, 0, pz, `${P.wood}_fence`, 2)
  const bn = OUTSIDE.banner
  c.set(...bn.at, `${P.accent}_wall_banner`, { facing: bn.facing })
  return { c, top }
}

export default function build(api) {
  const { c } = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `fire_station_cut${cut}` : 'fire_station' } }
}
