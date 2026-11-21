// Artisan's House, the snowy look (ROADMAP 49.10g): assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roof, openings, the kiln, interior, ground, snow) for LEVEL = 1..3; each level draws the one
// below first, so II keeps I's cells and III is II block for block above the ground. Writes furniture.json and, for
// III, out/cellar.txt (for rebuild.py --cellar). Build all: build.sh. CUT=<y> builds a cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BASEMENT, IDS, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE, FRONT_GROUND, DRESS } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, stair, slab, floor, post, wallPanel, window, door, quoins, gableRoof, gableWall, flight,
  stairwell, railingRun, cellar, hatchLadder, furnitureParts, inDisc, snowLoad, snowSkirt, rakeVerge, hangTo,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const plinthStone = mix(P.plinth, 11)
const stackStone = mix(P.stack, 13)
const H = MASSES.house, K = MASSES.kiln, ST = MASSES.stack, S = MASSES.shed, W = MASSES.wing, R = MASSES.rack

function sillSnow(c, cells, out) { // a stone slab sill one out under the lowest pane, a skim of snow on it
  const low = Math.min(...cells.map(p => p[1]))
  const d = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[out]
  for (const [x, y, z] of cells.filter(p => p[1] === low)) {
    if (c.get(x + d[0], y - 1, z + d[1])) continue
    slab(c, x + d[0], y - 1, z + d[1], P.sill, 'top')
    c.set(x + d[0], y, z + d[1], P.snow, { layers: '1' })
  }
}

// --- Level I: the Snowbound Kiln House --------------------------------------------------------------------------------
function levelOne(c) {
  const { x0, x1, z0, z1 } = H.walls

  // Pass 1: block-out and structure
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) c.set(x, 0, z, plinthStone)
  floor(c, x0 + 1, H.inner + 1, x1 - 1, 5, 0, P.workFloor) // the workroom's flags
  floor(c, x0 + 1, 6, x1 - 1, z1 - 1, 0, P.floor) // the hall's boards
  wallPanel(c, [x0, z0], [x1, z0], 1, H.wallTop, P.wall) // the front: the skin below, the upper wall above, one plane
  wallPanel(c, [x0, H.inner], [x1, H.inner], 1, H.groundTop, P.wall) // the ground storey's inner front wall
  wallPanel(c, [x0, z1], [x1, z1], 1, H.wallTop, P.wall)
  wallPanel(c, [x0, z0], [x0, z1], 1, H.wallTop, P.wall)
  wallPanel(c, [x1, z0], [x1, z1], 1, H.wallTop, P.wall)
  floor(c, x0 + 1, H.inner, x1 - 1, z1 - 1, H.band, P.floor) // the upper floor
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) // the floor band, read from outside
    if (x === x0 || x === x1 || z === z0 || z === z1) c.set(x, H.band, z, P.band)
  quoins(c, [[x0, z0], [x1, z0], [x0, z1], [x1, z1]], 1, H.wallTop, P.quoin, P.quoinAlt)
  for (const z of H.ties) for (let x = x0 + 1; x < x1; x++) c.set(x, H.tieY, z, P.tie, { axis: 'x' })

  // Pass 2: the steep slate roof, its white gables, one clean verge on each rake
  const main = gableRoof(c, H.roof, P.roof)
  for (const line of [z0, z1]) gableWall(c, 'z', line, H.roof.across, H.wallTop + 1, main.under, () => P.wall)
  for (const z of H.rakes) rakeVerge(c, P.roof, 'z', z, H.roof.across)

  // the storm porch: dressed cheeks, a lintel, its own pointed hood
  const pc = MASSES.porch
  for (const x of [pc.x0, pc.x1]) { c.set(x, 0, pc.z, plinthStone); for (let y = 1; y <= pc.top; y++) c.set(x, y, pc.z, y % 2 ? P.quoin : P.quoinAlt) }
  c.set(4, pc.top, pc.z, P.lintel)
  stair(c, pc.x0, pc.top + 1, pc.z, P.roof.stairs, 'east'); stair(c, pc.x1, pc.top + 1, pc.z, P.roof.stairs, 'west')
  c.set(4, pc.top + 1, pc.z, P.roof.full) // round 2: a dark hood, no white column
  slab(c, 4, pc.top + 2, pc.z, P.roof.slab, 'bottom')
  c.set(4, pc.top - 1, pc.z, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // Pass 3: openings
  const fd = OPENINGS.frontDoor
  c.clear(4, 1, z0); c.clear(4, 2, z0) // the doorway through the skin
  c.set(4, 3, z0, P.lintel)
  door(c, fd.at, fd.facing, 'north', { wood: P.wood })
  stair(c, ...fd.step, 'stone_brick_stairs', 'south')
  c.set(4, 0, z0, plinthStone)
  for (const w of FRONT_GROUND) {
    for (const p of w.cells) c.set(...p, P.glass)
    for (const p of w.reveal) c.clear(...p)
    c.set(w.cells[0][0], 4, z0, P.lintel)
    sillSnow(c, w.cells, 'north')
  }
  for (const w of OPENINGS.windows) {
    if (w.name.startsWith('shop')) continue
    const dress = DRESS[w.name] ?? null
    // the upper side windows a course lower (y 6..7), so their shutters stay under the eave lip at y 8
    const down = ((w.out === 'east' || w.out === 'west') && w.cells[0][1] >= 7) || w.name.includes('gable') // round 2: gable lights under the 45-degree ridge
    const cells = down ? w.cells.map(([x, y, z]) => [x, y - 1, z]) : w.cells
    window(c, cells, w.out, dress, { glass: P.glass, wood: P.wood })
    if (!dress && !w.name.includes('gable') && w.out !== 'north') sillSnow(c, cells, w.out) // round 2: no sills on the upper front (the porch's ears)
  }
  const st = OPENINGS.stairs
  stairwell(c, st.well)
  flight(c, st.flight, st.rises, P.wood, plinthStone)
  railingRun(c, OPENINGS.rail, P.wood)

  // Pass 4: the beehive kiln banked against the east wall, its stack above the eaves
  const { cx, cz } = K
  for (let dx = -3; dx <= 3; dx++) for (let dz = -3; dz <= 3; dz++) {
    if (!inDisc(dx, dz, 3)) continue
    const x = cx + dx, z = cz + dz
    c.set(x, 0, z, P.kiln.base)
    for (let y = 1; y <= K.hovelTop; y++) c.set(x, y, z, P.kiln.body)
    // round 2: the hovel batters in on brick stairs, the crown (r 2) one course over it, the stack set back on it
    const inward = Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? 'east' : 'west') : (dz < 0 ? 'south' : 'north')
    if (inDisc(dx, dz, 2)) c.set(x, K.crownY, z, P.kiln.body)
    else stair(c, x, K.crownY, z, P.kiln.stairs, inward)
  }
  for (let x = ST.x0; x <= ST.x1; x++) for (let z = ST.z0; z <= ST.z1; z++) for (let y = ST.y0; y <= ST.y1; y++) c.set(x, y, z, y === ST.y1 ? P.kiln.body : stackStone) // a brick collar at its head
  for (let x = ST.x0; x <= ST.x1; x++) for (let z = ST.z0; z <= ST.z1; z++) {
    if (x === 11 && z === 4) c.set(x, ST.y1 + 1, z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
    else slab(c, x, ST.y1 + 1, z, P.capSlab)
  }
  const km = OPENINGS.kilnMouth, sh = OPENINGS.stokeHole
  c.set(...km.at, 'furnace', { facing: km.facing, lit: 'false' })
  c.set(...sh.at, 'furnace', { facing: sh.facing, lit: 'false' })
  stair(c, sh.at[0] + 1, sh.at[1] + 1, sh.at[2], P.kiln.stairs, 'west', 'top') // a brick hood over the stoke hole

  // the fuel shed: a slate lean-to off the house's east wall on two posts, running on to the kiln's back
  for (let x = S.x0; x <= S.x1; x++) for (let z = S.z0; z <= S.z1 - 1; z++) c.set(x, 0, z, plinthStone)
  for (const [x, z] of S.posts) post(c, x, z, 1, 3, P.post)
  for (let z = S.z0 - 1; z <= S.z1; z++) {
    if (!c.get(S.x0, 4, z)) c.set(S.x0, 4, z, P.roof.full)
    for (const [x, y] of [[8, 5], [9, 4], [10, 3]]) if (!c.get(x, y, z)) stair(c, x, y, z, P.roof.stairs, 'west')
  }

  // Pass 5: interior (the drawn furniture, cell for cell)
  for (const f of FURNITURE[1]) furnitureParts[f.part](c, f)
  c.set(6, 2, 3, 'decorated_pot', { facing: 'west', cracked: 'false', waterlogged: 'false' }) // wares on the kept barrel

  // Pass 6: ground
  c.set(...OUTSIDE.wares, 'barrel', { facing: 'up', open: 'false' }) // the wares barrel, set in the skin by the door
  for (const p of OUTSIDE.woodpiles) for (const [z, wx] of p.rows) for (let x = wx; x <= 15; x++) { // round 2: along z, bark out, on a footing
    c.set(x, 0, z, plinthStone)
    for (let y = 1; y <= 2; y++) c.set(x, y, z, 'spruce_log', { axis: 'z' })
  }
  const sd = OUTSIDE.shed
  for (let z = sd.logs.z0; z <= sd.logs.z1; z++) for (let y = 1; y <= sd.logs.high; y++) c.set(sd.logs.x, y, z, 'spruce_log', { axis: 'x' })
  for (const p of sd.clay) c.set(...p, 'clay')
  c.set(...sd.cauldron, 'cauldron')
}

// --- Level II: the journeyman's room and the drying rack -------------------------------------------------------------
function levelTwo(c) {
  const { x0, x1, z0, z1 } = W.walls
  const hb = OPENINGS.windows.find(w => w.level1Only)
  for (const [x, y, z] of hb.cells) c.set(x, y, z, P.wall)
  for (const [x, y, z] of hb.cells) c.clear(x, y, z + 1)
  c.clear(hb.cells[0][0], hb.cells[0][1] - 1, hb.cells[0][2] + 1)
  for (const x of [2, 4, 6]) { c.clear(x, 6, 13); c.clear(x, 7, 13) } // the back windows' sills, now under the wing's roof

  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) c.set(x, 0, z, (x === x0 || x === x1 || z === z1) ? plinthStone : P.floor)
  wallPanel(c, [x0, z0], [x0, z1], 1, W.top, P.wall)
  wallPanel(c, [x1, z0], [x1, z1], 1, W.top, P.wall)
  wallPanel(c, [x0, z1], [x1, z1], 1, W.top, P.wall)
  quoins(c, [[x0, z1], [x1, z1]], 1, W.top, P.quoin, P.quoinAlt)
  const wing = gableRoof(c, W.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  gableWall(c, 'z', z1, W.roof.across, W.top + 1, wing.under, () => P.wall)
  rakeVerge(c, P.roof, 'z', W.rake, W.roof.across)

  const wd = OPENINGS.wingDoorway
  for (let y = wd.at[1]; y < wd.at[1] + wd.height; y++) c.clear(wd.at[0], y, wd.at[2])
  c.set(wd.at[0], 0, wd.at[2], P.floor)
  for (const w of OPENINGS.wingWindows) {
    const dress = DRESS[w.name] ?? null
    window(c, w.cells, w.out, dress, { glass: P.glass, wood: P.wood })
    if (!dress && !w.name.includes('gable')) sillSnow(c, w.cells, w.out)
  }
  for (const f of FURNITURE[2]) furnitureParts[f.part](c, f.part === 'hangingLamp' ? hangTo(c, f) : f) // round 2: the wing's ridge is lower

  // the drying rack of fired pots under a slate hood on two posts (wares dry under cover in snow country)
  const r = OUTSIDE.rack
  for (let x = R.x0 - 1; x <= R.x1 + 1; x++) for (let z = r.z - 1; z <= r.z + 1; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, 'gravel')
  for (let x = r.x0; x <= r.x1; x++) {
    if (x === r.x0) c.set(x, 1, r.z, `${P.wood}_fence`)
    else if (x === r.x1) c.set(x, 1, r.z, 'cauldron') // the quench tub
    else c.set(x, 1, r.z, 'barrel', { facing: 'up', open: 'false' })
    slab(c, x, 2, r.z, `${P.wood}_slab`, 'bottom')
  }
  c.set(r.x0, 3, r.z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  c.set(r.x1, 3, r.z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  for (const [x, z] of R.posts) post(c, x, z, 1, R.hoodY - 1, P.post)
  for (let x = R.x0; x <= R.x1; x++) c.set(x, R.hoodY, R.z, P.roof.full)
}

// --- Level III: the clay cellar (the drawn cellar, cell for cell) ------------------------------------------------------
function levelThree(c) {
  const Cl = MASSES.cellar
  const inside = cellar(c, Cl.x0, Cl.z0, Cl.x1, Cl.z1, Cl.depth, mix(P.cellarWall, 17), P.cellarFloor)
  const L = OPENINGS.hatch
  hatchLadder(c, L.x, L.z, -3, 0, L.facing, P.wood)
  for (let x = 2; x <= 6; x++) {
    c.set(x, -3, 3, 'chest', { facing: 'south', type: 'single', waterlogged: 'false' })
    c.set(x, -2, 3, 'barrel', { facing: 'south', open: 'false' })
  }
  for (let z = 6; z <= 10; z++) c.set(6, -3, z, 'barrel', { facing: 'west', open: 'false' })
  for (const z of [7, 9]) c.set(6, -2, z, 'barrel', { facing: 'west', open: 'false' })
  for (const [x, y, z] of [[2, -3, 11], [3, -3, 11], [2, -2, 11], [2, -3, 10]]) c.set(x, y, z, 'clay')
  c.set(4, -3, 8, `${P.wood}_fence`); c.set(4, -2, 8, 'brown_carpet')
  for (const z of [5, 9]) c.set(4, -1, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  return inside
}

// --- Snow: last, over whatever the level drew -------------------------------------------------------------------------
function snow(c, level) {
  const rakeRow = z => H.rakes.includes(z) || (level >= 2 && z === W.rake)
  const shedSlope = (x, y, z) => y <= 5 && x >= S.x0 && x <= S.x1 - 1 && z >= S.z0 - 1 && z <= S.z1
  snowLoad(c, P.roof, P.snow, {
    eaves: true, ridge: '3', tread: () => false,
    eaveSkip: (x, y, z) => rakeRow(z) || z === MASSES.porch.z || shedSlope(x, y, z) || !(y === H.roof.eaveY || (y === W.roof.eaveY && z >= W.walls.z0) || (y === 3 && x === S.x1)),
  })
  // the kiln's two steps: the hovel's shoulder (r 3 ring at y 4) and the crown's (r 2 ring round the stack at y 5)
  const { cx, cz } = K
  for (let dx = -3; dx <= 3; dx++) for (let dz = -3; dz <= 3; dz++) {
    const x = cx + dx, z = cz + dz
    const inStack = x >= ST.x0 && x <= ST.x1 && z >= ST.z0 && z <= ST.z1
    if (inDisc(dx, dz, 2) && !inStack && !c.get(x, K.crownY + 1, z)) c.set(x, K.crownY + 1, z, P.snow, { layers: '3' }) // one drift on the crown round the stack
  }
  for (const p of OUTSIDE.woodpiles) for (const [z, wx] of p.rows) for (let x = wx; x <= 15; x++) c.set(x, 3, z, P.snow, { layers: '2' })
  if (level >= 2) for (let x = R.x0; x <= R.x1; x++) if (!c.get(x, R.hoodY + 1, R.z)) c.set(x, R.hoodY + 1, R.z, P.snow, { layers: '2' })
  snowSkirt(c, P.snow, [[4, 0], [3, 0], [5, 0], [15, 4]])
}

export function draw(level = 1) {
  const c = level === 3 ? new LiftedCanvas(SIZE[3], BASEMENT) : new Canvas(SIZE[level])
  levelOne(c)
  if (level >= 2) levelTwo(c)
  snow(c, level)
  if (level >= 3) c.cellarInside = levelThree(c)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = IDS[level]
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  if (c.cellarInside) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), c.cellarInside.join(','))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
