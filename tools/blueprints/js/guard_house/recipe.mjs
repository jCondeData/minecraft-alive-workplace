// Guard House (the Gate Watch), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground and light) and returns the structure for the
// minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded) for
// tools/blueprints/rebuild.py. Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything
// above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, stair, plinth, floor, beamRing, door, window, gableRoof, gableWall, bargeTrim, jettyCorbels,
  ladderShaft, leverDoor, strawTarget, hangingLantern, furnitureParts, insetStorey, archFace, alarmBell,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

// a ring of wall round a box's edge from y0 to y1 (`id(y)` per course, so the foot can weather)
function ring(c, { x0, x1, z0, z1 }, y0, y1, id) {
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++)
    if (x === x0 || x === x1 || z === z0 || z === z1) c.set(x, y, z, id(y))
}

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const plinthStone = mix(P.plinth, 3), stone = mix(P.stone, 5), foot = mix(P.footStone, 9), setts = mix(P.setts, 11)
  const G = MASSES.guardroom, L = MASSES.lockup, W = MASSES.passage, B = MASSES.barrack
  const o = B.outer

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // the two piers: a rubble plinth, the first course weathered, dressed stone up to the ceiling
  for (const m of [G, L]) {
    plinth(c, m.walls.x0, m.walls.z0, m.walls.x1, m.walls.z1, plinthStone)
    ring(c, m.walls, 1, m.top, y => (y === 1 ? foot : stone))
  }
  floor(c, G.room.x0, G.room.z0, G.room.x1, G.room.z1, 0, P.floor)
  // the passage's paving, a step up at each end
  for (let x = W.x0; x <= W.x1; x++) {
    for (let z = W.z0 + 1; z < W.z1; z++) c.set(x, 0, z, setts)
    stair(c, x, 0, W.z0, P.archStairs, 'south')
    stair(c, x, 0, W.z1, P.archStairs, 'north')
  }
  // the arches at the front and the back of the passage: springing stairs and the keystone at y 4, under the floor
    for (const z of W.arches) {
    archFace(c, z, W.x0, W.x1, W.springY, { stairs: P.archStairs, keystone: P.keystone })
  }

  // the barrack room's floor over both piers and the road, its edge a ring of beams a block out on every side
  floor(c, o.x0, o.z0, o.x1, o.z1, B.floorY, P.floor)
  beamRing(c, o.x0, o.z0, o.x1, o.z1, B.floorY, P.frame)
  // the storey: posts on the jetty's line, infill a block back, the top plate
  insetStorey(c, { ...o, ...B.walls },
    { post: P.frame, infill: P.infill, plate: P.frame })

  // the roof: one slate roof across the road, and the Watch gable over the gate running back into its front slope.
  // Each column belongs to whichever of the two is higher there (the main roof where they tie), so the two meet in
  // valleys and nothing of one hangs over the other.
  const R = MASSES.roof, X = MASSES.watchGable
  const xm = (X.across[0] + X.across[1]) / 2, xh = (X.across[1] - X.across[0]) / 2
  const mainY = z => R.eaveY + Math.min(z - R.across[0], R.across[1] - z)
  const watchY = x => X.eaveY + xh - Math.abs(x - xm)
  const watchOwns = (x, z) => x >= X.across[0] && x <= X.across[1] && z <= X.along[1] && watchY(x) > mainY(z)
  const main = gableRoof(c, R, P.roof, (x, y, z) => !watchOwns(x, z))
  const watch = gableRoof(c, X, P.roof, (x, y, z) => watchOwns(x, z))
  // tie beams across the room, a king post on the middle one up to the ridge
  const T = B.tieBeams, ridgeZ = (R.across[0] + R.across[1]) / 2
  for (const x of T.xs) {
    for (let z = T.z0; z <= T.z1; z++) c.set(x, T.y, z, P.frame, { axis: 'z' })
    if (x === T.kingPost) for (let y = T.y + 1; y < main.ridgeY; y++) c.set(x, y, ridgeZ, P.frame, { axis: 'y' })
  }
  // the side gables (a king post with a light in it, two studs) and the Watch gable's face (the lookout light)
  const lights = new Set([...OPENINGS.gableLights.sides, ...OPENINGS.gableLights.watch].map(p => p.join(',')))
  for (const x of [o.x0, o.x1]) gableWall(c, 'x', x, [o.z0, o.z1], T.y + 1, main.under, (z, y) => {
    if (lights.has([x, y, z].join(','))) return P.glass
    if (z === ridgeZ || z === ridgeZ - 2 || z === ridgeZ + 2) return [P.frame, { axis: 'y' }]
    return P.infill
  })
  gableWall(c, 'z', X.face, [X.across[0] + 1, X.across[1] - 1], T.y + 1, watch.under, (x, y) => {
    if (lights.has([x, y, X.face].join(','))) return P.glass
    if (x === xm) return [P.frame, { axis: 'y' }]
    return P.infill
  })

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: 'cobblestone_stairs' })
  const ld = OPENINGS.lockupDoor
  door(c, ld.at, ld.facing, 'west', { wood: P.wood })
  // the gates: an iron grille filling each arch round a wooden wicket
  const gt = OPENINGS.gates
  for (const z of gt.z) {
    for (const [x, y] of gt.grille) c.set(x, y, z, P.bars)
    door(c, [gt.wicket[0], gt.wicket[1], z], z === gt.z[0] ? 'south' : 'north', 'north', { wood: P.wood })
  }
  for (const s of OPENINGS.slits) for (const [x, y, z] of s.cells) c.set(x, y, z, s.block === 'bars' ? P.bars : P.glass)
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, sill: `${P.trim}_stairs` })
  // the ladder from the Guardroom up through the barrack floor
  const lad = OPENINGS.ladder
  ladderShaft(c, lad.x, lad.z, lad.y0, lad.y1, lad.facing)
  // the cell: bars from the passage wall to an iron door, its lever on the jamb in the front room
  for (const [x, y, z] of OPENINGS.cellBars) c.set(x, y, z, P.bars)
  const cd = OPENINGS.cellDoor
  leverDoor(c, cd.at, { wall: 'x', jambSide: cd.jamb - cd.at[0], out: cd.lever }, { door: 'iron_door', jamb: P.frame })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // dark corbels under the front and back jetty at the piers' corners
  const K = MASSES.corbels, st = `${P.trim}_stairs`
  jettyCorbels(c, 'x', o.z0, K.at, K.y, st, 'south')
  jettyCorbels(c, 'x', o.z1, K.at, K.y, st, 'north')
  // barge boards under the side rakes and the Watch gable's front rake
  bargeTrim(c, 'x', R.along[0], R.across, main.under, st)
  bargeTrim(c, 'x', R.along[1], R.across, main.under, st)
  bargeTrim(c, 'z', X.along[0], [X.across[0] + 1, X.across[1] - 1], watch.under, st)
  // the alarm bell under the jetty in front of the keystone
  alarmBell(c, OUTSIDE.bell, 'north')

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (f.part) { furnitureParts[f.part](c, f); continue }
    const [x, y, z] = f.pos
    c.free(f.pos)
    if (f.block === 'grindstone') c.set(x, y, z, 'grindstone', { face: 'floor', facing: f.facing })
    else if (f.block === 'anvil') c.set(x, y, z, 'anvil', { facing: f.facing })
    else c.set(x, y, z, f.block, f.block === 'hay_block' ? { axis: 'x' } : {})
  }

  // --- Pass 5: ground and light -----------------------------------------------------------------------------------
  for (const [x, y, z, facing] of OUTSIDE.banners) c.set(x, y, z, `${P.accent}_wall_banner`, { facing })
  for (const l of OUTSIDE.lamps) hangingLantern(c, ...l)
  for (const t of OUTSIDE.targets) strawTarget(c, t, { bale: 'hay_block', face: 'target', post: `${P.wood}_fence` })
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `guard_house_cut${cut}` : 'guard_house' } }
}
