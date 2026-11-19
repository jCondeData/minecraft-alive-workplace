// Crushing Mill, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out and
// structure, the water and the machine, openings, depth and detail, interior, ground) and returns the structure for
// the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded).
// Build: ./build.sh (a candidate in build/rebuild/crushing_mill/); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, MACHINE, OPENINGS, FURNITURE, FIXTURES, OUTSIDE } from './plan.mjs'
import {
  Canvas, stair, trapdoor, door, floor, post, gableRoof, gableWall, window, lampPost, quoins, fallRace, waterWheel, shaftRun, beltRun,
  furnitureParts, crusherPair, twinDrop,
} from '../parts/index.mjs'
import { fairMix } from '../parts/apiary_skep.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = fairMix(P.stone, 11)
  const towerStone = fairMix(P.tower, 5)
  const H = MASSES.hall, T = MASSES.tower, S = MASSES.stack, B = MASSES.bay
  const inTower = (x, z) => x >= T.x0 && x <= T.x1 && z >= T.z0 && z <= T.z1
  const inStack = (x, z) => x >= S.x0 && x <= S.x1 && z >= S.z0 && z <= S.z1

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  floor(c, H.x0, H.z0, H.x1, H.z1, 0, stone)
  // the hall's four stone walls (openings cut in pass 3)
  for (let y = 1; y <= H.wallTop; y++) {
    for (let x = H.x0; x <= H.x1; x++) { c.set(x, y, H.z0, stone); if (!inTower(x, H.z1)) c.set(x, y, H.z1, stone) }
    for (let z = H.z0; z <= H.z1; z++) { c.set(H.x0, y, z, stone); c.set(H.x1, y, z, stone) }
  }
  quoins(c, [[H.x0, H.z0], [H.x1, H.z0], [H.x0, H.z1], [H.x1, H.z1]], 1, H.wallTop, P.dressed)
  // the dark oak wall plate round the top, the tie beams across the hall
  for (let x = H.x0; x <= H.x1; x++) for (const z of [H.z0, H.z1]) if (!inTower(x, z)) c.set(x, H.plateY, z, P.frame, { axis: 'x' })
  for (let z = H.z0 + 1; z < H.z1; z++) for (const x of [H.x0, H.x1]) if (!inStack(x, z)) c.set(x, H.plateY, z, P.frame, { axis: 'z' })
  for (const z of MASSES.tieBeams) for (let x = H.x0 + 1; x < H.x1; x++) c.set(x, H.plateY, z, P.frame, { axis: 'x' })

  // the fall tower: stone bricks from the ground to the poured source, its shaft left open for the water
  for (let y = 0; y <= T.top; y++) for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
    if (x === T.shaft[0] && z === T.shaft[1] && y >= T.sumpY) continue
    c.set(x, y, z, towerStone)
  }
  quoins(c, [[T.x0, T.z1], [T.x1, T.z1]], 0, T.top, P.dressed)
  quoins(c, [[T.x0, T.z0], [T.x1, T.z0]], H.plateY + 1, T.top, P.dressed) // the front corners above the hall's roof

  // dressed bands round the tower (a course at the hall's plate, one under the hat), and the sight glass: a slot of
  // panes up the shaft's back face, so the falling water shows from the yard (the water still falls beside stone)
  for (const y of T.bands) for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
    const ring = x === T.x0 || x === T.x1 || z === T.z0 || z === T.z1
    if (ring && !(x === T.shaft[0] && z === T.shaft[1]) && !(z === T.z0 && y <= H.plateY)) c.set(x, y, z, P.dressed)
  }
  for (const y of T.sightGlass) c.set(T.shaft[0], y, T.z1, P.glass)

  // the furnace stack up the east wall, its breast inside over the blast furnace
  for (let y = 0; y <= S.top; y++) for (let x = S.x0; x <= S.x1; x++) for (let z = S.z0; z <= S.z1; z++) c.set(x, y, z, P.brick)
  const Br = MASSES.breast
  for (const [x, z] of Br.cells) for (let y = Br.y0; y <= Br.y1; y++) c.set(x, y, z, P.brick)
  for (const [x, z] of [...Br.back, [9, 3]]) c.set(x, 1, z, P.brick)

  // the roof: 45 degrees, ridge along z, around the tower and the stack
  const R = MASSES.roof
  const main = gableRoof(c, R, P.roof, (x, y, z) => !inTower(x, z) && !inStack(x, z))
  const gable = (line, keep) => gableWall(c, 'z', line, R.across, H.plateY + 1, main.under, (u, y) => {
    if (!keep(u)) return null
    if (u === 4 || u === 8) return [P.frame, { axis: 'y' }] // studs over the bay's jambs
    if (y === 9 && u >= 5 && u <= 7) return [P.frame, { axis: 'x' }] // the collar
    return P.boards
  })
  gable(H.z0, u => u >= H.x0 && u <= H.x1)
  gable(H.z1, u => u >= H.x0 && u <= H.x1 && !inTower(u, H.z1))

  // --- Pass 2: the water and the machine ---------------------------------------------------------------------------
  // the fall: one poured source at the tower's top, falling down its shaft past the wheel into the closed sump
  fallRace(c, { col: T.shaft, sumpY: T.sumpY, topY: T.top, cup: [], sumpSides: [] }, { wall: towerStone, floorId: P.dressed, stairs: P.brickStairs })
  const M = MACHINE
  waterWheel(c, M.waterWheel.at, M.waterWheel.facing)
  crusherPair(c, M.gap, M.wheelAxis, { floorId: P.dressed, guard: P.guard, bed: P.dressed, bedCells: M.bedCells })
  for (const [x, z] of M.guardCells) c.set(x, 1, z, P.guard)
  twinDrop(c, M.drops, M.gap[1], 'x')
  for (const s of M.shafts) shaftRun(c, s.cells, s.axis)
  beltRun(c, M.belt.cells, M.belt.facing)
  c.set(...M.drain, 'create:item_drain')

  // --- Pass 3: openings ---------------------------------------------------------------------------------------------
  for (let x = B.x0; x <= B.x1; x++) for (let y = 1; y <= B.top; y++) c.clear(x, y, H.z0)
  for (const x of B.jambs) post(c, x, H.z0, 1, B.lintelY, P.frame)
  for (let x = B.x0; x <= B.x1; x++) c.set(x, B.lintelY, H.z0, P.frame, { axis: 'x' })
  for (const s of OPENINGS.step.cells) stair(c, ...s, P.stepStairs, OPENINGS.step.facing)
  // the bay glazed like a shop window either side of the door, a transom over it: the wheels read from the road
  const D = OPENINGS.door
  door(c, D.at, D.facing, 'north', { wood: P.wood })
  for (const x of [B.x0, B.x1]) for (let y = 1; y <= B.top; y++) c.set(x, y, H.z0, P.glass)
  c.set(D.at[0], B.top, H.z0, P.glass)
  for (const w of OPENINGS.windows) window(c, w.cells, null, null, { glass: P.glass })

  // --- Pass 4: depth and detail -------------------------------------------------------------------------------------
  // the tower's hat: four dark oak posts round the open cistern, a two-step tiled cap
  for (const [x, z] of [[T.x0, T.z0], [T.x1, T.z0], [T.x0, T.z1], [T.x1, T.z1]])
    for (const y of T.hatPosts) c.set(x, y, z, P.frame, { axis: 'y' })
  const cx = T.shaft[0], cz = T.shaft[1]
  // louvres between the posts: open trapdoors, so the tank-house breathes but nothing walks or blows in
  for (const y of T.hatPosts) for (const [dx, dz, f] of [[0, -1, 'north'], [0, 1, 'south'], [-1, 0, 'west'], [1, 0, 'east']])
    trapdoor(c, cx + dx, y, cz + dz, `${P.wood}_trapdoor`, f, 'bottom', true)
  for (let dx = -2; dx <= 2; dx++) for (let dz = -2; dz <= 2; dz++) {
    const ring = Math.max(Math.abs(dx), Math.abs(dz))
    const toward = Math.abs(dz) >= Math.abs(dx) ? (dz < 0 ? 'south' : 'north') : (dx < 0 ? 'east' : 'west')
    if (ring === 2) stair(c, cx + dx, T.capY, cz + dz, P.roof.stairs, toward)
    else {
      c.set(cx + dx, T.capY, cz + dz, P.roof.full)
      if (ring === 1) stair(c, cx + dx, T.capY + 1, cz + dz, P.roof.stairs, toward)
      else c.set(cx, T.capY + 1, cz, P.roof.slab, { type: 'bottom', waterlogged: 'false' })
    }
  }
  // the stack's crown: a brick stair course stepping out at its top
  for (let x = S.x0; x <= S.x1; x++) {
    stair(c, x, S.top, S.z0, P.brickStairs, 'south', 'top')
    stair(c, x, S.top, S.z1, P.brickStairs, 'north', 'top')
  }
  // a little tiled hood over the belt's mouth in the back wall (ore is tipped onto it from the yard)
  stair(c, 5, 4, 9, P.roof.stairs, 'east'); c.set(6, 4, 9, P.roof.full); stair(c, 7, 4, 9, P.roof.stairs, 'west')
  c.set(6, 4, 8, P.frame, { axis: 'z' })

  // --- Pass 5: interior (the plan's furniture and fixtures) ---------------------------------------------------------
  for (const f of FURNITURE) furnitureParts[f.part](c, f)
  c.set(...FIXTURES.blastFurnace.at, 'blast_furnace', { facing: FIXTURES.blastFurnace.facing, lit: 'false' })
  c.set(...FIXTURES.chest.at, 'chest', { facing: FIXTURES.chest.facing, type: 'single', waterlogged: 'false' })
  c.set(...FIXTURES.bench.at, FIXTURES.bench.block)
  for (const [id, p] of FIXTURES.heaps) c.set(...p, id)

  // --- Pass 6: ground -----------------------------------------------------------------------------------------------
  lampPost(c, ...OUTSIDE.lamp, P.fence, 2)
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `crushing_mill_cut${cut}` : 'crushing_mill' } }
}
