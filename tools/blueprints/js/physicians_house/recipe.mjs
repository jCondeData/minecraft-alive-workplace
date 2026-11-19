// Physician's House (ROADMAP 49), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, roofs, openings, depth and detail, interior, ground) and returns the structure for the
// minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded) for
// tools/blueprints/rebuild.py. Build: see package.json; CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, SCREENS, FURNITURE, GARDEN, DETAIL } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, gableRoof, gableWall,
  quoins, louvre, boxBoards, plantFlower, furnitureParts, hollowPyramid, wardScreens, bayTies, brewingStand,
  hangingLantern, lampPost, stair, eyebrow,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.plinth, 11)
  const weathered = mix(P.plaster, 5)
  const plaster = (x, y, z) => (y <= 1 ? weathered(x, y, z) : P.plaster[0][0]) // clean limewash, weathered only at the foot
  const towerStone = mix(P.tower, 3)
  const W = MASSES.ward, T = MASSES.tower
  const { x0, x1, z0, z1 } = W.posts
  const inf = W.infill, rm = W.room
  const tw = T.walls, [cx, cz] = T.centre

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // Ward: a cobble plinth under the whole frame, spruce boards inside, plaster panels a block back from the posts
  plinth(c, x0, z0, x1, z1, stone)
  floor(c, rm.x0, rm.z0, rm.x1, rm.z1, 0, P.floor)
  wallPanel(c, [inf.x0, inf.z0], [inf.x1, inf.z0], 1, W.wallTop, plaster)
  wallPanel(c, [inf.x0, inf.z1], [inf.x1, inf.z1], 1, W.wallTop, plaster)
  wallPanel(c, [inf.x0, inf.z0], [inf.x0, inf.z1], 1, W.wallTop, plaster)
  wallPanel(c, [inf.x1, inf.z0], [inf.x1, inf.z1], 1, W.wallTop, plaster)
  for (const x of W.frontPosts) post(c, x, z0, 1, W.wallTop, P.frame)
  for (const x of W.backPosts) post(c, x, z1, 1, W.wallTop, P.frame)
  for (const z of W.westPosts) post(c, x0, z, 1, W.wallTop, P.frame)
  for (const z of W.eastPosts) post(c, x1, z, 1, W.wallTop, P.frame)
  beamRing(c, x0, z0, x1, z1, W.beamY, P.frame)
  bayTies(c, W.ties, inf.x0, inf.x1, W.beamY, P.frame) // the bay lines: a post outside, a tie overhead, a screen under it

  // Tower: the still-room, stone brick on its own plinth, quoined corners, open inside up to the cap
  plinth(c, tw.x0, tw.z0, tw.x1, tw.z1, stone)
  floor(c, T.room.x0, T.room.z0, T.room.x1, T.room.z1, 0, P.stillFloor)
  for (let x = tw.x0; x <= tw.x1; x++) for (let z = tw.z0; z <= tw.z1; z++) {
    if (x !== tw.x0 && x !== tw.x1 && z !== tw.z0 && z !== tw.z1) continue
    for (let y = 1; y <= T.wallTop; y++) c.set(x, y, z, towerStone)
  }
  quoins(c, [[tw.x0, tw.z0], [tw.x1, tw.z0], [tw.x0, tw.z1], [tw.x1, tw.z1]], 1, T.wallTop, P.quoin, 'stone_bricks')
  bayTies(c, [T.tie.z], T.room.x0, T.room.x1, T.tie.y, P.frame)

  // --- Pass 2: roofs ---------------------------------------------------------------------------------------------
  // The ward's 45-degree roof first, only where the tower isn't (it dies into the tower's west wall)
  const main = gableRoof(c, W.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  // The tower's hollow slate pyramid to an open louvre with its lamp: the vent the still-room's fumes leave by. Its
  // eave is pulled in wherever it would hang over the ward's roof (no roof over a roof: the builder can't stand there)
  const overRoof = (x, y, z) => {
    for (let yy = 0; yy < T.roof.y0; yy++) if (c.name(x, yy, z).includes(P.roof.full.replace(/s$/, ''))) return true
    return false
  }
  const capY = hollowPyramid(c, cx, cz, T.roof.r, T.roof.y0, P.roof, T.roof.top, (x, y, z) => !overRoof(x, y, z))
  louvre(c, cx, cz, capY, { base: P.roof.full, post: `${P.wood}_fence`, roof: P.roof, })
  // Gables on the post line, jettied over the panels: two studs over the door posts framing a window over the door
  const pick = back => (u, y) => {
    if (u === 4 || u === 6) return [P.frame, { axis: 'y' }] // studs over the door posts, framing the gable window
    return plaster
  }
  gableWall(c, 'z', z0, [x0 + 1, x1 - 1], W.beamY + 1, main.under, pick(false))
  gableWall(c, 'z', z1, [x0 + 1, x1 - 1], W.beamY + 1, main.under, pick(true))

  // --- Pass 3: openings ------------------------------------------------------------------------------------------
  const td = OPENINGS.towerDoor, wd = OPENINGS.wardDoor
  door(c, td.at, td.facing, 'north', { wood: P.wood, step: td.step, stepStair: P.stoneStep })
  door(c, wd.at, wd.facing, 'north', { wood: P.wood })
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: w.pots ?? [] })

  // --- Pass 4: depth and detail ----------------------------------------------------------------------------------
  // No hoods (they would sit under the eaves): the ward door's lamp hangs from the beam in front of it, the tower
  // door's stands on a post beside its step
  const L = DETAIL
  hangingLantern(c, ...L.wardDoorLamp)
  for (const [x, y, z] of L.towerDoorPosts) lampPost(c, x, y, z, `${P.wood}_fence`, 2)
  // The tower's foot: a battered stone skirt along its east face (the east sill stands on it)
  for (const { cells, facing } of L.skirt) for (const [x, z] of cells) stair(c, x, 0, z, P.stoneStep, facing)
  // Its street windows dressed: a drip mould over each, a stone sill under the east one, shutters on the front one
  for (const { at, out, sill } of L.dressed) {
    const [x, y, z] = at
    if (sill) eyebrow(c, [x, sill, z], out, P.stoneStep)
    eyebrow(c, [x, y, z], out, P.stoneStep)
  }
  window(c, L.frontShutters.cells, 'north', 'shutters', { glass: P.glass, wood: P.wood })

  // --- Pass 5: interior (the plan's furniture) -------------------------------------------------------------------
  wardScreens(c, SCREENS, P.linen)
  for (const f of FURNITURE) {
    if (f.part === 'brewingStand') brewingStand(c, f.pos)
    else furnitureParts[f.part](c, f)
  }

  // --- Pass 6: ground: the physic garden -------------------------------------------------------------------------
  const pth = GARDEN.path
  c.set(...pth.step, P.stoneStep, { facing: 'south', half: 'bottom', shape: 'straight', waterlogged: 'false' }) // up onto the plinth
  for (const b of GARDEN.beds) {
    for (let x = b.x0; x <= b.x1; x++) for (let z = b.z0; z <= b.z1; z++) {
      c.set(x, 0, z, P.soil, { snowy: 'false' })
      plantFlower(c, x, 1, z, b.plants[z - b.z0][x - b.x0])
    }
    const boards = []
    // boards on the faces a visitor sees: the street side and the path's two sides
    for (let x = b.x0; x <= b.x1; x++) boards.push([[x, 0, b.z0 - 1], 'north'])
    for (let z = b.z0; z <= b.z1; z++) {
      if (b.x0 - 1 === pth.x + 1) boards.push([[b.x0 - 1, 0, z], 'west'])
      if (b.x1 + 1 === pth.x - 1) boards.push([[b.x1 + 1, 0, z], 'east'])
    }
    for (const [cell, out] of boards) if (!c.get(...cell)) boxBoards(c, [cell], P.wood, out)
  }
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `physicians_house_cut${cut}` : 'physicians_house' } }
}
