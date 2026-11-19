// Train Station (ROADMAP 49/50.13), the recipe: assembles plan.mjs ("the Trainshed") from the shared parts library
// (../parts) in passes (block-out and structure, openings, depth and detail, interior, ground and light) and returns
// the structure for the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot).
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, TRACK, STATION, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, stair, slab, plinth, floor, post, wallPanel, window, door, gableRoof, gableWall, bargeTrim, pyramidRoof,
  quoins, stringCourse, flowerBed, raisedBed, plantFlower, lampPost, furnitureParts,
  trackRun, shedTruss, ridgeLantern, clockFace, waitingBench, pediment,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const base = mix(P.plinth, 3)
  const paving = mix(P.platform, 5)
  const ballast = mix(P.ballast, 9)
  const S = MASSES.shed, H = MASSES.hall, T = MASSES.tower, PL = MASSES.platform
  const dressStairs = `${P.dressing.replace(/s$/, '')}_stairs` // stone_brick_stairs
  const dressSlab = `${P.dressing.replace(/s$/, '')}_slab`
  const inTower = (x, z) => x >= T.x0 && x <= T.x1 && z >= T.z0 && z <= T.z1

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // The line: track the whole width on its ballast, a ballast verge behind it, the platform in front, a back walk
  trackRun(c, TRACK, { track: P.track, ballast })
  floor(c, 0, TRACK.line + 1, 18, TRACK.line + 1, 0, ballast)
  floor(c, 0, PL.z0, 18, PL.z1 - 1, 0, paving)
  floor(c, 0, PL.edgeZ, 18, PL.edgeZ, 0, P.edge)
  floor(c, 0, TRACK.line + 2, 18, S.back - 1, 0, paving)

  // The Shed: brick walls on a dark plinth, timber portal posts at the open ends, a beam ring at y 6
  plinth(c, S.x0, S.front, S.x1, S.front, base)
  plinth(c, S.x0, S.back, S.x1, S.back, base)
  plinth(c, S.x0, S.back + 1, S.x1, S.back + 1, base) // the back skirt the pilasters and sills stand on
  wallPanel(c, [S.x0, S.front], [S.x1, S.front], 1, S.wallTop, P.brick)
  wallPanel(c, [S.x0, S.back], [S.x1, S.back], 1, S.wallTop, P.brick)
  for (const x of S.posts) {
    if (S.portals.includes(x)) { post(c, x, S.front, 1, S.beamY, P.frame); post(c, x, S.back, 1, S.beamY, P.frame) }
    else for (let y = 1; y <= S.wallTop; y++) c.set(x, y, S.back + 1, P.dressing) // stone pilasters proud of the back wall
  }
  for (let x = S.x0 + 1; x < S.x1; x++) for (const z of [S.front, S.back]) c.set(x, S.beamY, z, P.frame, { axis: 'x' })

  // The Tower: brick on a dark plinth, quoins at its free corners, a string course at the hall's eave line
  plinth(c, T.x0, T.z0, T.x1, T.z1, base)
  floor(c, T.office.x0, T.office.z0, T.office.x1, T.office.z1, 0, P.officeFloor)
  for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
    if (x !== T.x0 && x !== T.x1 && z !== T.z0 && z !== T.z1) continue
    for (let y = 1; y <= T.wallTop; y++) c.set(x, y, z, P.brick)
  }
  quoins(c, [[T.x1, T.z0], [T.x1, T.z1], [T.x0, T.z0]], 1, T.wallTop, P.dressing, P.brick) // toothed: stone and brick by turns
  for (const y of T.courses) stringCourse(c, T.x0, T.z0, T.x1, T.z1, y, P.dressing)
  floor(c, T.office.x0, T.office.z0, T.office.x1, T.office.z1, T.ceilingY, P.officeFloor) // the office ceiling
  floor(c, T.office.x0, T.office.z0, T.office.x1, T.office.z1, T.wallTop, P.brick) // the clock room's roof deck

  // The Hall: a dark plinth, brick walls, a stone course at y 5, a checkered floor
  plinth(c, H.x0, H.z0, H.x1, H.z1 - 1, base)
  plinth(c, H.x0, H.z0 - 1, H.x1, H.z0 - 1, base) // the front skirt under the pilasters and sills
  plinth(c, H.x0 - 1, H.z0, H.x0 - 1, H.z1 - 1, base) // and the west one
  plinth(c, 16, 5, 16, 6, base) // the shed's east front and the office's east sill stand on this
  for (let x = H.room.x0; x <= H.room.x1; x++) for (let z = H.room.z0; z <= H.room.z1; z++) c.set(x, 0, z, P.hallFloor[(x + z) % 2])
  wallPanel(c, [H.x0, H.z0], [H.x1, H.z0], 1, H.wallTop, P.brick)
  wallPanel(c, [H.x0, H.z0], [H.x0, H.z1 - 1], 1, H.wallTop, P.brick)
  wallPanel(c, [H.x1, H.z0], [H.x1, T.z0 - 1], 1, H.wallTop, P.brick)
  stringCourse(c, H.x0, H.z0, H.x1, H.z1 - 1, H.courseY, P.dressing, (x, z) => z === H.z1 || inTower(x, z) || (x > H.x0 && x < H.x1 && z > H.z0))
  for (let x = H.room.x0; x <= H.room.x1; x++) c.set(x, H.courseY, 4, P.frame, { axis: 'x' }) // the hall's tie beam
  for (const x of H.frontPilasters) for (let y = 1; y <= (x === H.x0 || x === H.x1 ? H.courseY : H.wallTop); y++) c.set(x, y, H.z0 - 1, P.dressing)
  for (const z of H.westPilasters) for (let y = 1; y <= H.wallTop; y++) c.set(H.x0 - 1, y, z, P.dressing)

  // Roofs: the shed's first (not inside the hall or the tower), then the tower's copper cap, then the hall's, which
  // runs back over the shed's front slope until it dies into it
  const shedRoof = gableRoof(c, S.roof, P.roof, (x, y, z) => !(z === 6 && x >= 2 && x <= 15) && !inTower(x, z))
  ridgeLantern(c, S.lantern, { glass: P.glass, end: P.glass, cap: P.roof }) // glazed all round, so it reads as a lantern, not a chimney
  // the lantern's lid stands on dark oak posts over each truss and at its two ends, so it never looks to float
  // and is glazed two panes deep between them
  const lidPosts = [S.lantern.x0, ...S.trusses, S.lantern.x1]
  for (let x = S.lantern.x0; x <= S.lantern.x1; x++) for (const z of [S.lantern.zc - 1, S.lantern.zc + 1]) {
    if (lidPosts.includes(x)) for (let y = S.lantern.y; y <= S.lantern.y + 1; y++) c.set(x, y, z, P.frame, { axis: 'y' })
    else c.set(x, S.lantern.y, z, P.glass)
  }
  // a cornice of upside-down stone stairs round the top of the tower's three free faces, then the steep copper cap
  const cx = (T.x0 + T.x1) / 2, cz = (T.z0 + T.z1) / 2
  for (let x = T.x0 - 1; x <= T.x1 + 1; x++) for (let z = T.z0 - 1; z <= T.z1 + 1; z++) {
    if (x !== T.x0 - 1 && x !== T.x1 + 1 && z !== T.z0 - 1 && z !== T.z1 + 1) continue
    if (z === T.z1 + 1) continue // not over the shed's slope behind
    const dx = x - cx, dz = z - cz
    const toward = Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? 'east' : 'west') : (dz < 0 ? 'south' : 'north')
    stair(c, x, T.wallTop, z, dressStairs, toward, 'top')
  }
  pyramidRoof(c, cx, cz, T.capR, T.capY, P.copper)
  const hallRoof = gableRoof(c, H.roof, P.roof, (x, y, z) => !c.get(x, y, z) && !inTower(x, z))
  for (let z = H.z1 + 1; z <= H.z1 + 2; z++) for (let x = H.roof.across[0]; x <= H.roof.across[1]; x++) {
    const i = Math.min(x - H.roof.across[0], H.roof.across[1] - x), y = H.roof.eaveY + i
    if (y <= z || inTower(x, z)) continue
    if (x === 7) c.set(x, y, z, P.roof.full)
    else stair(c, x, y, z, P.roof.stairs, x < 7 ? 'east' : 'west')
    for (let yy = z + 1; yy < y; yy++) c.set(x, yy, z, P.roof.full)
  }
  // the hall's front gable round its round window; its back gable closes the vault against the shed roof
  gableWall(c, 'z', H.z0, H.roof.across, H.courseY + 1, hallRoof.under, (u, y) => {
    if (u === 7 && y >= 6 && y <= 8) return P.glass
    if ((u === 6 || u === 8) && y === 7) return P.glass
    if (u === 7 && y === 9) return P.dressing // the keystone over the round window
    return P.brick
  })
  for (let x = H.room.x0; x <= H.room.x1; x++) for (let y = S.beamY + 1; y < hallRoof.under[x]; y++) c.set(x, y, H.z1, P.brick)

  // Trusses across the shed, portals at its open ends with a glazed fan over each tie beam
  const ridgeY = shedRoof.ridgeY
  for (const x of [...S.trusses, ...S.portals])
    shedTruss(c, { x, z0: S.front + 1, z1: S.back - 1, tieY: S.tieY, ridgeZ: S.lantern.zc, kingTop: ridgeY - 1 }, { beam: P.frame, brace: `${P.wood}_stairs` })
  for (const x of S.portals)
    gableWall(c, 'x', x, S.roof.across, S.tieY + 1, shedRoof.under, (u, y) => (u === S.lantern.zc ? null : P.glass))

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: dressStairs })
  const pd = OPENINGS.platformDoor
  door(c, pd.at, pd.facing, 'south', { wood: P.wood })
  const od = OPENINGS.officeDoor
  door(c, od.at, od.facing, 'west', { wood: P.wood })
  c.set(...OPENINGS.ticketWindow.at, 'iron_bars')
  const pots = { 'hall front west': ['potted_red_tulip', 'potted_azure_bluet'], 'hall front east': ['potted_azure_bluet', 'potted_red_tulip'] }
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, sill: dressStairs, pots: pots[w.name] ?? [] })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // the front door's pediment on its two pilasters, a lantern hung under it
  pediment(c, fd.at[0], H.courseY, fd.at[2] - 1, { full: P.dressing, stairs: dressStairs })
  c.set(fd.at[0], H.courseY - 1, fd.at[2] - 1, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // stone lintels over the shed's back windows
  for (const x of [3, 7, 11, 15]) for (let dx = -1; dx <= 1; dx++) c.set(x + dx, S.wallTop, S.back, P.dressing)
  // three clock faces on the tower: the street, the east and over the hall's roof
  clockFace(c, { x: cx, y: T.clockY, z: T.z0, along: 'x' }, P.clock)
  clockFace(c, { x: T.x1, y: T.clockY, z: cz, along: 'z' }, P.clock)
  clockFace(c, { x: T.x0, y: T.clockY, z: cz, along: 'z' }, P.clock)
  // barge boards under the overhanging rakes: the hall's street gable and the shed's two open ends
  bargeTrim(c, 'z', H.roof.along[0], H.roof.across, hallRoof.under, `${P.wood}_stairs`)
  for (const x of [S.roof.along[0], S.roof.along[1]]) bargeTrim(c, 'x', x, S.roof.across, shedRoof.under, `${P.wood}_stairs`)

  // --- Pass 4: interior (the plan's furniture) and the station ---------------------------------------------------
  for (const f of FURNITURE) {
    if (f.part === 'bench') waitingBench(c, f.cells, f.facing, { seat: `${f.wood}_stairs`, back: f.back ?? null, wood: f.wood })
    else furnitureParts[f.part](c, f)
  }
  c.set(...STATION.at, P.station, { assembling: 'false', waterlogged: 'false' })

  // --- Pass 5: ground and light ---------------------------------------------------------------------------------------
  const B = OUTSIDE.bed
  const inside = raisedBed(c, { x0: B.x0, x1: B.x1, z0: B.z0, z1: B.z1 }, { rim: base, cap: dressSlab, soil: 'grass_block' })
  inside.forEach(([x, z], i) => plantFlower(c, x, 1, z, B.plants[i % B.plants.length]))
  const St = OUTSIDE.strip
  for (let z = St.z0; z <= St.z1; z++) flowerBed(c, St.x0, St.x1, z, St.plants.slice(z % St.plants.length).concat(St.plants))
  for (const [x, y, z] of OUTSIDE.lampPosts) lampPost(c, x, y, z, `${P.wood}_fence`, 2)
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `train_station_cut${cut}` : 'train_station' } }
}
