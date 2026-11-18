// The Workshop (the head-tank works), the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP
// 49) in passes (block-out and structure, the water and the machine, openings, depth and detail, interior, ground) and
// returns the structure for the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the
// parts recorded) for tools/blueprints/rebuild.py. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, MACHINE, OPENINGS, FURNITURE, FIXTURES, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, stair, plinth, floor, posts, beamRing, wallPanel, window, door, gableRoof, gableWall, bargeTrim,
  quoins, hood, railing, lampPost, furnitureParts, clumped, taperedStack, furnaceMouth,
  fallRace, waterWheel, shaftRun, cog, beltRun, louvre, stringCourse,
} from '../parts/index.mjs'
import { pressOverDepot, spoutTank, weatheredSkirt } from '../parts/workshop_press.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = clumped(mix(P.stone, 41), 2)
  const brick = mix(P.brick, 9)
  const H = MASSES.hall, R = H.ring, G = MASSES.pressGable, T = MASSES.tower, S = MASSES.stack, X = MACHINE

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // the plinth under the walls and the proud posts; boards over the hall, the machine bed in andesite under the line
  plinth(c, R.x0, R.z0, R.x1, R.z1, stone)
  floor(c, H.x0 + 1, H.z0 + 1, H.x1 - 1, H.z1 - 1, 0, P.floor)
  floor(c, 3, 2, 5, 9, 0, P.bed)
  // the walls: a stone dado (y 1..2), boards (y 3..4), a dark wall plate (y 5); the proud frame posts on the ring
  const sides = [[[H.x0, H.z0], [H.x1, H.z0]], [[H.x0, H.z1], [H.x1, H.z1]], [[H.x0, H.z0], [H.x0, H.z1]], [[H.x1, H.z0], [H.x1, H.z1]]]
  for (const [a, b] of sides) { wallPanel(c, a, b, 1, H.dadoTop, stone); wallPanel(c, a, b, H.dadoTop + 1, H.plateY - 1, P.infill) }
  beamRing(c, H.x0, H.z0, H.x1, H.z1, H.plateY, P.frame)
  posts(c, 'x', R.z0, H.frontPosts, 1, 4, P.frame)
  posts(c, 'x', R.z1, H.backPosts, 1, 4, P.frame)
  posts(c, 'z', R.x0, H.westPosts, 1, 4, P.frame)
  posts(c, 'z', R.x1, H.eastPosts, 1, 4, P.frame)
  // tie beams across the hall (front wall to back wall), lamps hang from them
  for (const x of H.ties) for (let z = H.z0 + 1; z < H.z1; z++) c.set(x, H.plateY, z, P.frame, { axis: 'z' })

  // Roofs: the press gable first (its eave a block over the hall's, its ridge along z), then the hall's slate gable
  // wherever the press gable's roof isn't higher. The fall's column (x 1, z 9 up) stays open: no rake over it.
  const mainY = z => H.roof.eaveY + Math.min(z - H.roof.across[0], H.roof.across[1] - z)
  const crossY = x => G.eaveY + Math.min(x - G.across[0], G.across[1] - x)
  const inCross = (x, z) => x >= G.across[0] && x <= G.across[1] && z >= G.along[0] && z <= G.along[1]
  const cross = gableRoof(c, { axis: 'z', across: G.across, along: G.along, eaveY: G.eaveY, steep: false }, P.roof,
    (x, y, z) => y >= mainY(z))
  const main = gableRoof(c, H.roof, P.roof,
    (x, y, z) => !(inCross(x, z) && y <= crossY(x)) && !(x === X.fall.col[0] && z >= X.openRake[0] && z <= X.openRake[1]) && !c.get(x, y, z))

  // Gables: the hall's two in boards with a king post and studs, the press gable glazed over its bay
  const glass = new Set(OPENINGS.gableGlass.map(([x, y, z]) => `${x},${y},${z}`))
  const westGlass = new Set(OPENINGS.westGableWindow.map(([x, y, z]) => `${x},${y},${z}`))
  const studs = (u, y, line) => {
    if (westGlass.has(`${line},${y},${u}`)) return P.glass
    if (u === 6) return [P.frame, { axis: 'y' }] // the king post under the ridge
    if ((u === 3 || u === 9) && y <= 7) return [P.frame, { axis: 'y' }]
    return P.infill
  }
  gableWall(c, 'x', H.x0, [H.z0, H.z1], H.plateY + 1, main.under, (u, y) => studs(u, y, H.x0))
  gableWall(c, 'x', H.x1, [H.z0, H.z1], H.plateY + 1, main.under, (u, y) => studs(u, y, H.x1))
  gableWall(c, 'z', H.z0, [G.across[0] + 1, G.across[1] - 1], H.plateY + 1, cross.under, (u, y) =>
    glass.has(`${u},${y},${H.z0}`) ? P.glass : P.infill)

  // --- Pass 2: the water and the machine --------------------------------------------------------------------------
  // the water tower: stone up to its top, dressed corners
  c.fill(T.x0, 0, T.z0, T.x1, T.top, T.z1, stone)
  quoins(c, [[T.x0, T.z0], [T.x0, T.z1], [T.x1, T.z1], [T.x1, T.z0]], 1, T.top, P.quoin[0], P.quoin[1])
  for (const y of MASSES.towerBands) stringCourse(c, T.x0, T.z0, T.x1, T.z1, y, P.roof.full)
  // the head tank on it, the copper spout box at its north face round the pour
  spoutTank(c, {
    x0: T.x0, z0: T.z0, x1: T.x1, z1: T.z1, y0: T.tank[0], y1: T.tank[1], hoops: T.hoops, box: X.spoutBox,
    pour: [X.pour[0], X.pour[2]],
    corbels: X.spoutCorbels.map(at => ({ at, facing: 'south' })),
  }, { stave: P.stave, hoop: P.hoop, cap: P.roof, finial: P.finial, spoutId: P.copper, corbel: P.copperStairs })
  // the fall: the poured source, the water falling past the wheel into the closed sump
  const F = X.fall
  fallRace(c, { col: F.col, sumpY: F.sumpY, topY: F.topY, cup: [], sumpSides: X.sumpSides }, { wall: stone, floorId: stone, stairs: P.dressedStairs })
  c.set(...X.seat, P.dressed) // the wheel's seat, the sump's north side
  waterWheel(c, X.wheel.at, X.wheel.facing)
  for (const s of X.shafts) shaftRun(c, s.cells, s.axis)
  for (const p of X.cogs) cog(c, p, 'x')
  beltRun(c, X.belt.cells, X.belt.facing)
  pressOverDepot(c, X.press.at, X.press.facing)
  railing(c, X.guard, P.wood)

  // the furnace stack up the east gable (brick, stone-crowned, smoking) and its breast inside, the blast furnace in it
  taperedStack(c, S.cx, S.cz, 0, [{ w: 3, top: S.top }], {
    body: brick, ledge: P.brickStairs, crown: P.crown, crownSlab: P.brickSlab, quoin: () => P.quoin,
  })
  for (let z = S.cz - 1; z <= S.cz + 1; z++) for (let y = 1; y < main.under[z]; y++) c.set(H.x1 - 1, y, z, brick)
  const BF = FIXTURES.blastFurnace
  furnaceMouth(c, BF.at, BF.into, 'blast_furnace', { brick, hoodStairs: P.brickStairs, top: BF.top })
  for (let z = S.cz - 1; z <= S.cz + 1; z++) stair(c, BF.at[0], BF.top + 1, z, P.brickStairs, 'east') // the breast's weathering

  // --- Pass 3: openings -------------------------------------------------------------------------------------------
  const d = OPENINGS.door
  door(c, d.at, d.facing, d.out, { wood: P.wood, step: d.step, stepStair: P.dressedStairs })
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, null, { glass: P.glass, wood: P.wood })

  // --- Pass 4: depth and detail -----------------------------------------------------------------------------------
  // the plinth's weathered skirt between the proud posts, every window sitting over it (none by the door, by the
  // wheel and its fall, under the tower or the stack)
  const run = (from, to, f) => { const out = []; for (let u = from; u <= to; u++) if (f(u)) out.push(u); return out }
  const nearFall = z => z <= X.openRake[1]
  const underTower = z => z >= T.z0 && z <= T.z1
  weatheredSkirt(c, run(H.x0, H.x1, x => !H.frontPosts.includes(x) && x !== d.at[0]).map(x => [x, R.z0]), 1, stone, P.dressedStairs, 'south')
  weatheredSkirt(c, run(H.x0, H.x1, x => !H.backPosts.includes(x)).map(x => [x, R.z1]), 1, stone, P.dressedStairs, 'north')
  weatheredSkirt(c, run(H.z0, H.z1, z => !H.westPosts.includes(z) && !nearFall(z) && !underTower(z)).map(z => [R.x0, z]), 1, stone, P.dressedStairs, 'east')
  weatheredSkirt(c, run(H.z0, H.z1, z => !H.eastPosts.includes(z) && Math.abs(z - S.cz) > 1).map(z => [R.x1, z]), 1, stone, P.dressedStairs, 'west')
  // barge boards under the rakes: the hall's east and west gables, the press gable's front
  bargeTrim(c, 'x', R.x0, H.roof.across, main.under, `${P.trim}_stairs`)
  for (let z = X.openRake[0] + 1; z <= X.openRake[1]; z++) // none where the rake is left open over the fall
    if (c.name(R.x0, main.under[z] - 1, z).endsWith('_stairs')) c.clear(R.x0, main.under[z] - 1, z)
  bargeTrim(c, 'x', R.x1, H.roof.across, main.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', G.along[0], G.across, cross.under, `${P.trim}_stairs`)
  // the forge's smoke louvre on the ridge, capped in copper
  const L = MASSES.louvre
  louvre(c, L.cx, L.cz, L.y, { base: P.roof.full, post: `${P.wood}_fence`, roof: { full: P.copper, stairs: P.copperStairs }, finial: P.finial })
  // the door's hood with its lantern
  hood(c, d.at[0], 4, R.z0, P.roof)

  // --- Pass 5: interior (the plan's furniture) --------------------------------------------------------------------
  for (const f of FURNITURE) furnitureParts[f.part](c, f)
  c.set(...FIXTURES.anvil.at, 'anvil', { facing: FIXTURES.anvil.facing })
  for (const p of FIXTURES.sheets) c.set(...p, P.sheet, { power: '0' })
  for (const [x, z] of FIXTURES.hearthApron) c.set(x, 0, z, P.dressed)
  c.set(...FIXTURES.oven.at, 'furnace', { facing: FIXTURES.oven.facing, lit: 'false' })
  for (const [id, p] of FIXTURES.stock) c.set(...p, id)

  // --- Pass 6: ground ---------------------------------------------------------------------------------------------
  lampPost(c, ...OUTSIDE.wheelLamp, `${P.wood}_fence`, 2)
  for (const p of OUTSIDE.coal) c.set(...p, 'coal_block')
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `workshop_cut${cut}` : 'workshop' } }
}
