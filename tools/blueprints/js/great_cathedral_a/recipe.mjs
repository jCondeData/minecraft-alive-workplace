// The Great Cathedral (a Wonder, 52.6 version A, "the Ship of Stone"), the recipe: assembles plan.mjs stage by stage on a
// StagedCanvas (a later stage may only add: it throws on any block an earlier stage drew), each stage in passes
// (block-out and structure, openings, depth and detail, interior, light), and returns the Wonder as it stands after
// STAGE (default 4) for the minecraft-builder skill's runner. verify() checks every stage against the brief and refuses a
// stage that changes an earlier one. CUT=<y> cuts everything above y (a cutaway). stages.py saves the candidates.
import {
  SIZE, STAGES, MID, PALETTE as P, MASSES as M, OPENINGS as O, FURNITURE, CEREMONY,
} from './plan.mjs'
import { mix, stair, StagedCanvas, stagesOnlyAdd, hatchLadder } from '../parts/index.mjs'
import { both, fieldRoof, pointedOpening, octSpire, roseCells, flyingButtress, louvres } from '../parts/cathedral_gothic.mjs'

const W = mix(P.wall, 31), L = mix(P.low, 37)
const wallAt = (x, y, z) => (y <= 3 ? L(x, y, z) : W(x, y, z))
const pane = { waterlogged: 'false' }
const put = (c, x, y, z, id, p = {}) => c.add(x, y, z, typeof id === 'function' ? id(x, y, z) : id, p)
const glassMix = (x, y) => (y % 5 === 0 ? P.glassHead : P.glass)

// --- the heights of every roof (one field per range; the main one runs nave, crossing, choir and apse) -------------
const apseM = (x, z) => Math.max(Math.abs(x - MID), z - M.apse.cz, Math.abs(x - MID) + z - M.apse.cz - 3)
const inApseWall = (x, z) => {
  if (z < M.apse.cz) return false
  const m = apseM(x, z)
  if (m === 6) return true
  if (m !== 7) return false
  const six = [[1, 0], [-1, 0], [0, 1], [0, -1]].filter(([dx, dz]) => z + dz >= M.apse.cz && apseM(x + dx, z + dz) === 6).length
  return six >= 2
}
const inApse = (x, z) => z >= M.apse.cz && apseM(x, z) <= 5
const mainH = (x, z) => {
  const d = Math.abs(x - MID)
  if (d === 7 && z >= 11) return null // no eave over the aisles' lean-tos: the roofs meet at the clerestory wall
  if (z >= 2 && z <= 47 && d <= 7) return 21 + 2 * (7 - d) // z 2: the front gable, so the roof needs no wedge behind it
  if (z >= 48 && z < SIZE[2] && apseM(x, z) <= 7 && !(apseM(x, z) === 7 && (x <= 12 || x >= 26))) return 21 + 2 * (7 - apseM(x, z))
  return null
}
const transeptH = (x, z) => (x >= 0 && x <= 38 && z >= 31 && z <= 39 ? 21 + 2 * (5 - Math.abs(z - 35)) : null)
const upperH = (x, z) => {
  const a = mainH(x, z), b = transeptH(x, z)
  return a === null ? b : b === null ? a : Math.max(a, b)
}
const aisleH = (x, z) => (x >= 6 && x <= 13 && z >= 11 && z <= 53 && !(z >= 31 && z <= 39) && !(x === 6 && z >= 40 && z <= 44) ? 9 + (x - 6) + (x === 13 ? 1 : 0) : null) // x 13: the clerestory wall it leans on
const chapelH = (x, z) => (x >= 0 && x <= 6 && z >= 40 && z <= 45 ? 10 + (45 - z) : null)
const mirrorH = h => (x, z) => h(x <= MID ? x : 2 * MID - x, z)
const cols = (x0, x1, z0, z1, H) => {
  const out = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) if (H(x, z) !== null) out.push([x, z])
  return out
}

function door(c, [x, y, z], facing, hinge) {
  for (const half of ['lower', 'upper']) c.set(x, y + (half === 'upper' ? 1 : 0), z, `${P.wood}_door`, { facing, half, hinge, open: 'false', powered: 'false' })
}
const lantern = (c, x, y, z) => c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
function chainLamp(c, x, yLamp, yTop, z) {
  yTop = yLamp + 1
  while (!c.get(x, yTop, z)) yTop++ // up to whatever is overhead (a vault's crown, a roof's underside)
  for (let y = yLamp + 1; y < yTop; y++) c.set(x, y, z, 'chain', { axis: 'y', waterlogged: 'false' })
  lantern(c, x, yLamp, z)
}
// a stone vault under a roof: a pointed barrel along `axis`, its haunches upside-down stairs, its crown flat, a polished
// rib wherever `rib(t)` holds (t the coordinate along the axis)
function vault(c, axis, a0, a1, t0, t1, rib) {
  const mid = (a0 + a1) / 2
  for (let t = t0; t <= t1; t++) for (let a = a0; a <= a1; a++) {
    const k = Math.min(a - a0, a1 - a)
    const y = 19 + Math.min(k, 3)
    const [x, z] = axis === 'z' ? [a, t] : [t, a]
    const isRib = rib(t)
    if (k < 3 || (k === 3 && Math.abs(a - mid) > 1.5)) {
      const out = a < mid ? (axis === 'z' ? 'west' : 'north') : (axis === 'z' ? 'east' : 'south')
      put(c, x, y, z, isRib ? P.dressStairs : P.wallStairs, { facing: out, half: 'top', shape: 'straight', waterlogged: 'false' })
    } else put(c, x, y, z, isRib ? P.dress : W)
  }
}

// the paving under the walls and inside, found from a first drawing of the whole (no apron of bare floor round it)
let standing = null
export function draw() {
  if (!standing) {
    standing = new Set()
    const first = drawWith(() => true)
    for (const k of first.cells.keys()) { const [x, y, z] = k.split(',').map(Number); if (y >= 1) standing.add(`${x},${z}`) }
  }
  return drawWith((x, z) => standing.has(`${x},${z}`) || (z <= 1 && x >= 13 && x <= 25))
}

function drawWith(paved) {
  const c = new StagedCanvas(SIZE)
  const T = M.tower, N = M.nave, A = M.aisle

  // ================= Stage I: The Foundations and the West Front ===================================================
  c.begin(1)
  // Pass 1, block-out: the floor over the whole footprint, a chequer inside, rubble-weathered paving round it
  const footprint = (x, z) =>
    (z <= 1 && x >= 13 && x <= 25) || (z >= 1 && z <= 11 && x >= 3 && x <= 35) || (z >= 11 && z <= 30 && x >= 5 && x <= 33) ||
    (z >= 30 && z <= 39) || (z >= 40 && z <= 45 && (x <= 7 || x >= 31)) || (z >= 40 && z <= 53 && x >= 7 && x <= 31) || (z >= 48 && apseM(x, z) <= 7)
  // (the chequer stops at the walls' inside faces: the chapels' outer walls (x 0, 38) and the choir aisles' east walls
  // (z 52) stand on the plain paving, so no striped plinth shows outside)
  const inside = (x, z) => (x >= 8 && x <= 30 && z >= 3 && z <= 51) || inApse(x, z) || (z >= 32 && z <= 38 && x >= 1 && x <= 37) ||
    (z >= 40 && z <= 43 && ((x >= 1 && x <= 6) || (x >= 32 && x <= 37)))
  for (let x = 0; x < SIZE[0]; x++) for (let z = 0; z < SIZE[2]; z++) {
    if (!footprint(x, z) || !(paved(x, z) || inside(x, z))) continue
    c.set(x, 0, z, inside(x, z) ? ((x + z) % 2 ? P.floorA : P.floorB) : L(x, 0, z))
  }

  // the towers' shafts, both sides from one drawing
  both(c, t => {
    // clasping buttresses at the outer corners: a tall pair and a short pair, each capped
    const butt = (cells, top, facing) => {
      for (const [x, z] of cells) { for (let y = 1; y <= top; y++) t.set(x, y, z, wallAt); stair(t, x, top + 1, z, P.wallStairs, facing) }
    }
    butt([[3, 1], [3, 2], [4, 1]], 23, 'east'); butt([[3, 3]], 13, 'east'); butt([[5, 1]], 13, 'south')
    butt([[3, 10], [3, 11], [4, 11]], 23, 'east'); butt([[3, 9]], 13, 'east'); butt([[5, 11]], 13, 'north')
    butt([[12, 1]], 23, 'south'); butt([[11, 1]], 13, 'south')
    for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
      const rim = x === T.x0 || x === T.x1 || z === T.z0 || z === T.z1
      for (let y = 1; y <= T.shaftTop; y++) {
        if (rim) t.set(x, y, z, (y === T.floors[0] || y === T.floors[1]) ? P.dress : wallAt)
        else if (T.floors.includes(y) && !(x === T.ladder.x && z === T.ladder.z)) t.set(x, y, z, P.floorEdge)
      }
    }
    // string courses: a polished drip round the outer faces at each floor
    for (const y of T.floors.slice(0, 2)) {
      for (let z = 2; z <= 10; z++) put(t, 3, y, z, P.dressStairs, { facing: 'east', half: 'top', shape: 'straight', waterlogged: 'false' })
      for (let x = 4; x <= 12; x++) put(t, x, y, 1, P.dressStairs, { facing: 'south', half: 'top', shape: 'straight', waterlogged: 'false' })
      for (let x = 4; x <= 5; x++) put(t, x, y, 11, P.dressStairs, { facing: 'north', half: 'top', shape: 'straight', waterlogged: 'false' })
    }
    // the ladder up the west wall's inside, through each floor's hatch, under a trapdoor in the top floor (the
    // tower's roof until the belfry goes up)
    hatchLadder(t, T.ladder.x, T.ladder.z, 1, T.shaftTop, 'east', P.wood)
    // Pass 2, openings: the side portal (the tower's own way in), twin lancets over it, slits higher up
    t.clear(T.sideDoor, 1, 2); t.clear(T.sideDoor, 2, 2)
    for (let y = 3; y <= 4; y++) t.set(T.sideDoor, y, 2, P.carve) // a little tympanum
    for (let y = 12; y <= 15; y++) { t.set(7, y, 2, glassMix(7, y), pane); t.set(9, y, 2, glassMix(9, y), pane); t.set(8, y, 2, P.dress) }
    t.set(8, 16, 2, P.glassHead, pane)
    for (let y = 12; y <= 16; y++) { t.set(4, y, 6, glassMix(4, y), pane); t.set(8, y, 10, glassMix(8, y), pane) }
    for (let y = 20; y <= 24; y++) { t.set(8, y, 2, P.glass, pane); t.set(4, y, 6, P.glass, pane); t.set(8, y, 10, P.glass, pane) }
    // a hood on brackets over the side portal
    stair(t, 7, 4, 1, P.wallStairs, 'east', 'top'); stair(t, 9, 4, 1, P.wallStairs, 'west', 'top')
    for (const x of [7, 8, 9]) t.set(x, 5, 1, P.dressSlab, { type: 'bottom', waterlogged: 'false' })
  })
  for (const d of O.doors.slice(2)) door(c, d.at, d.facing, d.hinge)

  // the central bay of the front: wall, portal, the rose's ring, the gable on the roof's line
  const F = M.front, R = M.rose
  const gableTop = x => 21 + 2 * (7 - Math.abs(x - MID))
  const rose = new Map(roseCells(R.r).map(r => [`${R.cx + r.a},${R.cy + r.b}`, r]))
  for (let x = F.x0; x <= F.x1; x++) {
    for (let y = 1; y <= gableTop(x); y++) {
      const r = rose.get(`${x},${y}`)
      if (r && r.kind !== 'ring') continue // the rose's glass and tracery come in stage IV
      c.set(x, y, F.z, r ? P.dress : (y === 21 ? P.dress : wallAt))
    }
    if (x !== MID) stair(c, x, gableTop(x) + 1, F.z, P.wallStairs, x < MID ? 'east' : 'west')
  }
  for (const [y, id] of [[36, P.carve], [37, P.dress], [38, P.wallWall]]) c.set(MID, y, F.z, id)
  // the portal: two leaves round a trumeau under a carved tympanum, a gablet of stone in front on z 0..1
  for (const x of F.portal.doors) { c.clear(x, 1, F.z); c.clear(x, 2, F.z) }
  for (let x = 18; x <= 20; x++) for (let y = 3; y <= 6; y++) c.set(x, y, F.z, y === 6 ? P.dress : P.carve)
  for (const d of O.doors.slice(0, 2)) door(c, d.at, d.facing, d.hinge)
  c.set(F.portal.trumeau, 1, F.z, P.dress); c.set(F.portal.trumeau, 2, F.z, P.dress)
  const G = F.portal.gablet
  for (let z = G.z0; z <= G.z1; z++) for (let x = G.x0; x <= G.x1; x++) {
    const top = G.peak - Math.abs(x - MID)
    for (let y = 1; y <= top; y++) {
      const open = Math.abs(x - MID) <= 1 && (y <= 6 || (y === 7 && x === MID))
      if (open) continue
      if (y === 7 && Math.abs(x - MID) === 1) { stair(c, x, y, z, P.dressStairs, x < MID ? 'west' : 'east', 'top'); continue }
      c.set(x, y, z, (Math.abs(x - MID) === 2 && y <= 6) ? P.dress : (y === 8 && x === MID ? P.carve : wallAt))
    }
    if (z === G.z0) {
      if (x !== MID) stair(c, x, top + 1, z, P.wallStairs, x < MID ? 'east' : 'west')
      else { c.set(x, top + 1, z, P.carve); c.set(x, top + 2, z, P.wallWall) }
    } else c.set(x, top + 1, z, W)
  }

  // ================= Stage II: The Nave ===============================================================================
  c.begin(2)
  both(c, t => {
    // the clerestory wall over the arcade, z 3..31: piers, pointed arches, lancets
    const arch = new Set()
    for (const b of N.bays) for (const [z, y] of pointedOpening(b - 1, b + 1, 1, 6)) arch.add(`${z},${y}`)
    for (let z = 3; z <= 10; z++) for (let y = 1; y <= 4; y++) if (z >= 5 && z <= 7) arch.add(`${z},${y}`)
    for (let z = N.z0; z <= N.z1; z++) for (let y = 1; y <= N.eave; y++) {
      if (arch.has(`${z},${y}`) || (z < 11 && y < N.eave)) continue // the west bay: the tower's wall is the nave's
      const pier = N.piers.includes(z)
      const clere = N.bays.some(b => Math.abs(z - b) <= 1) && y >= O.clerestory.y[0] && y <= O.clerestory.y[1]
      if (clere) { t.set(13, y, z, glassMix(13, y), pane); continue }
      t.set(13, y, z, pier && y <= 8 ? P.dress : (y === 8 || y === N.eave ? P.dress : wallAt))
    }
    for (const b of N.bays) lantern(t, 13, 7, b)
    // the aisle: its outer wall, lancets at each bay, the lean-to over it
    for (let z = A.z0; z <= A.z1; z++) for (let y = 1; y <= A.wallTop; y++) {
      const lancet = z < A.z1 && N.bays.some(b => Math.abs(z - b) <= 1) && y >= O.aisleLancets.y[0] && y <= O.aisleLancets.y[1]
      const head = N.bays.includes(z) && y === O.aisleLancets.head
      t.set(A.wallX, y, z, lancet || head ? glassMix(7, y) : wallAt, lancet || head ? pane : {})
    }
    // flying buttresses at the inner piers, leaping the aisle roof to the clerestory
    for (const z of A.buttresses) flyingButtress(t, { xOut: 5, xIn: 6, z, top: 12, flyTo: 12, flyY0: 13 }, { ...P, wall: W, low: L })
  })
  fieldRoof(c, cols(6, 32, 11, 30, mirrorH(aisleH)).filter(([x]) => x !== 13 && x !== 25), mirrorH(aisleH), A.roofBase, P.roof)
  fieldRoof(c, cols(12, 26, 3, 30, mainH), mainH, N.roofBase, P.roof)
  vault(c, 'z', 14, 24, 3, 31, t => N.piers.includes(t) || t === 3)
  // Pass 4, interior: pews either side of the carpet aisle, lamps down the crown
  const pews = FURNITURE[0]
  for (const z of pews.rows) for (const [x0, x1] of pews.x) for (let x = x0; x <= x1; x++) stair(c, x, 1, z, `${P.wood}_stairs`, pews.facing)
  for (let z = 3; z <= 30; z++) c.set(MID, 1, z, P.carpet)
  for (const z of [17, 25]) chainLamp(c, MID, 14, 22, z)

  // ================= Stage III: The Transepts and the Choir ==========================================================
  c.begin(3)
  both(c, t => {
    // the crossing's east pier and the choir's clerestory wall, z 32..47: the arms' great arch, the choir arcade
    const open = new Set()
    for (const [z, y] of pointedOpening(32, 38, 1, 12)) open.add(`${z},${y}`)
    for (const b of [41, 45]) for (const [z, y] of pointedOpening(b - 1, b + 1, 1, 6)) open.add(`${z},${y}`)
    for (let z = 32; z <= 47; z++) for (let y = 1; y <= 20; y++) {
      if (open.has(`${z},${y}`)) continue
      const clere = [41, 45].some(b => Math.abs(z - b) <= 1) && y >= 16 && y <= 18
      if (clere) { t.set(13, y, z, glassMix(13, y), pane); continue }
      t.set(13, y, z, ([39, 43, 47].includes(z) && y <= 8) || y === 8 || y === 20 ? P.dress : wallAt)
    }
    for (const b of [41, 45]) lantern(t, 13, 7, b)
    // the arm: west wall z 31 (open to the aisle under y 9), east wall z 39 (open to the choir aisle and the chapel)
    for (const z of [31, 39]) for (let x = 0; x <= 12; x++) for (let y = 1; y <= 20; y++) {
      if (x >= 8 && y <= 8) continue
      if (z === 39 && pointedOpening(2, 5, 1, 5).some(([a, b]) => a === x && b === y)) continue
      if (z === 31 && (x === 3 || x === 4) && y >= 4 && y <= 13) { t.set(x, y, z, glassMix(x, y), pane); continue }
      t.set(x, y, z, y === 20 ? P.dress : wallAt)
    }
    // the end gable, x 0, coped on the arm roof's line, a twin lancet and an oculus
    const armTop = z => 21 + 2 * (5 - Math.abs(z - 35))
    for (let z = 32; z <= 38; z++) {
      for (let y = 1; y <= armTop(z); y++) {
        const lancet = (z === 33 || z === 34 || z === 36 || z === 37) && y >= O.transeptWindow.y[0] && y <= O.transeptWindow.y[1]
        const head = (z === 34 || z === 36) && y === 14
        const ocul = z === 35 && (y === 17 || y === 18)
        if (lancet || head || ocul) t.set(0, y, z, ocul ? P.rose.outer[0] : glassMix(0, y), pane)
        else t.set(0, y, z, y === 20 ? P.dress : wallAt)
      }
    }
    for (const z of [31, 39]) for (let y = 21; y <= armTop(z); y++) t.set(0, y, z, W)
    for (let z = 31; z <= 39; z++) if (z !== 35) stair(t, 0, armTop(z) + 1, z, P.wallStairs, z < 35 ? 'south' : 'north')
    t.set(0, 32, 35, P.carve); t.set(0, 33, 35, P.wallWall)
    // a corner buttress on the arm's west face
    for (const x of [0, 1]) { for (let y = 1; y <= 15; y++) t.set(x, y, 30, wallAt); stair(t, x, 16, 30, P.wallStairs, 'south') }
    // the transept chapel against the arm's east wall (x 0..7, z 40..44)
    const CH = M.chapels.transept
    for (let z = CH.z0; z <= CH.z1; z++) for (let x = CH.x0; x <= CH.x1; x++) {
      const rim = x === CH.x0 || x === CH.x1 || z === CH.z1
      if (!rim) continue
      for (let y = 1; y <= 9; y++) {
        const win = (z === CH.z1 && (x === 3 || x === 4) && y >= 3 && y <= 6) || (x === 0 && z === 42 && y >= 3 && y <= 6)
        t.set(x, y, z, win ? glassMix(x, y) : wallAt, win ? pane : {})
      }
    }
    // the choir aisle's outer wall (x 7, z 40..52) and the aisle chapel's east wall (z 52)
    for (let z = 40; z <= 52; z++) for (let y = 1; y <= 8; y++) {
      const win = z === 46 && y >= 3 && y <= 6
      if (z <= CH.z1 && t.get(7, y, z)) continue
      t.set(7, y, z, win ? glassMix(7, y) : wallAt, win ? pane : {})
    }
    const AC = M.chapels.aisle
    for (let x = AC.x0 + 1; x <= AC.x1; x++) for (let y = 1; y <= x + 2; y++) { // up under the lean-to
      if (x === 13 && y > 8) break
      const win = x >= 9 && x <= 11 && y >= 3 && y <= 6
      t.set(x, y, AC.z1, win ? glassMix(x, y) : wallAt, win ? pane : {})
    }
  })
  // the apse: a half octagon round the altar, tall windows east and on its two straight sides
  for (let x = 12; x <= 26; x++) for (let z = 48; z < SIZE[2]; z++) {
    if (!inApseWall(x, z)) continue
    for (let y = 1; y <= 20; y++) {
      const east = z === 54 && x >= 18 && x <= 20 && y >= 6 && y <= 13
      const eastHead = z === 54 && x === MID && y === 14
      const side = (x === 13 || x === 25) && (z === 49 || z === 50) && y >= 7 && y <= 14
      if (east || eastHead || side) { c.set(x, y, z, P.glassApse[y % 2], pane); continue }
      c.set(x, y, z, y === 20 || y === 8 ? P.dress : wallAt)
    }
  }
  // roofs: the arms and the main range as one field (valleys where they meet), the choir aisles' lean-tos, the chapels'
  fieldRoof(c, [...cols(1, 37, 30, 40, upperH), ...cols(12, 26, 31, SIZE[2] - 1, upperH)].filter(([, z]) => z >= 30), upperH, 21, P.roof)
  fieldRoof(c, cols(6, 32, 40, 53, mirrorH(aisleH)).filter(([x]) => x !== 13 && x !== 25), mirrorH(aisleH), 9, P.roof)
  fieldRoof(c, cols(0, 38, 40, 45, mirrorH(chapelH)), mirrorH(chapelH), 10, P.roof)
  // vaults: the main one on through the crossing and choir, the arms' across them, the apse's crown flat
  vault(c, 'z', 14, 24, 32, 47, t => [35, 39, 43, 47].includes(t))
  vault(c, 'x', 32, 38, 1, 12, t => t === 7 || t === 12)
  vault(c, 'x', 32, 38, 26, 37, t => t === 26 || t === 31)
  for (let x = 14; x <= 24; x++) for (let z = 48; z <= 53; z++) {
    if (!inApse(x, z)) continue
    const m = apseM(x, z)
    if (m >= 3) put(c, x, 19 + Math.min(5 - m, 3), z, P.wallStairs, { facing: x < MID - 1 ? 'west' : x > MID + 1 ? 'east' : 'south', half: 'top', shape: 'straight', waterlogged: 'false' })
    else put(c, x, 22, z, m === 0 ? P.dress : W)
  }
  // the fleche on the crossing: a pedestal on the ridge, a louvred lantern, a lead-dark spire
  const FL = M.fleche
  for (let x = FL.x0; x <= FL.x1; x++) for (let z = FL.z0; z <= FL.z1; z++) {
    for (let y = 30; y <= FL.y0; y++) if (!c.get(x, y, z)) c.set(x, y, z, P.roof.full)
    const rim = x === FL.x0 || x === FL.x1 || z === FL.z0 || z === FL.z1
    const corner = (x === FL.x0 || x === FL.x1) && (z === FL.z0 || z === FL.z1)
    for (let y = FL.y0 + 1; y <= FL.top; y++) {
      if (!rim) { if (y === FL.top) c.set(x, y, z, P.dress); continue }
      const mid = (x === MID || z === 35) && y >= 37 && y <= 38 && !corner
      if (mid) continue
      c.set(x, y, z, corner || y === FL.top || y === FL.y0 + 1 ? P.dress : W)
    }
  }
  louvres(c, [[MID, 37, FL.z0], [MID, 38, FL.z0]], 'north', P.wood); louvres(c, [[MID, 37, FL.z1], [MID, 38, FL.z1]], 'south', P.wood)
  louvres(c, [[FL.x0, 37, 35], [FL.x0, 38, 35]], 'west', P.wood); louvres(c, [[FL.x1, 37, 35], [FL.x1, 38, 35]], 'east', P.wood)
  octSpire(c, { cx: MID, cz: 35, y0: FL.spire[0], y1: FL.spire[1], r0: 2.4, tip: P.roof.wall }, () => P.roof.full)
  // Pass 4, interior: the raised choir and sanctuary, stalls, the altar and its reredos, chapels, banners, lamps
  for (let x = 14; x <= 24; x++) {
    stair(c, x, 1, 40, P.dressStairs, 'south')
    for (let z = 41; z <= 53; z++) if (z < 48 || inApse(x, z)) c.set(x, 1, z, (x + z) % 2 ? P.floorA : P.floorB)
  }
  for (let z = 32; z <= 39; z++) c.set(MID, 1, z, P.carpet)
  for (let z = 42; z <= 46; z++) { stair(c, 15, 2, z, `${P.wood}_stairs`, 'west'); stair(c, 23, 2, z, `${P.wood}_stairs`, 'east') }
  for (const [x, y, z] of FURNITURE[3].cells) c.set(x, y, z, x === MID ? P.gold : P.dress)
  for (const [x, y, z] of FURNITURE[3].candles) c.set(x, y, z, P.candle, { candles: '3', lit: 'true', waterlogged: 'false' })
  for (let x = 16; x <= 22; x++) for (let y = 2; y <= 7; y++) {
    const edge = x === 16 || x === 22
    if (!edge && y > 5 + (Math.abs(x - MID) <= 1 ? 1 : 0) + (x === MID ? 1 : 0)) continue
    c.set(x, y, 52, edge ? (y === 7 ? P.wallWall : P.dress) : (y === 2 ? P.dress : P.carve))
  }
  for (const [x, y, z, f] of FURNITURE[5].at) c.set(x, y, z, FURNITURE[5].block, { facing: f })
  for (const [x, y, z, f] of FURNITURE[6].at) c.set(x, y, z, FURNITURE[6].block, { facing: f })
  for (const [x, y, z, f] of FURNITURE[7].at) c.set(x, y, z, 'lectern', { facing: f, has_book: 'false', powered: 'false' })
  c.set(...FURNITURE[8].at, 'brewing_stand', { has_bottle_0: 'false', has_bottle_1: 'false', has_bottle_2: 'false' })
  for (const [x, y, z] of FURNITURE[9].at) { c.set(x, y, z, P.dress); c.set(x, y + 1, z, P.candle, { candles: '2', lit: 'true', waterlogged: 'false' }) }
  for (const z of [44]) chainLamp(c, MID, 14, 22, z)
  chainLamp(c, MID, 15, 22, 35)
  both(c, t => { chainLamp(t, 6, 14, 22, 35); chainLamp(t, 3, 8, 11, 42); chainLamp(t, 10, 7, 11, 50) })

  // ================= Stage IV: The Towers and the Rose Window ========================================================
  c.begin(4)
  both(c, t => {
    // the belfry: twin louvred openings on every face, a beam across with two bells
    const [b0, b1] = T.belfry
    for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
      const rim = x === T.x0 || x === T.x1 || z === T.z0 || z === T.z1
      if (!rim) continue
      const corner = (x === T.x0 || x === T.x1) && (z === T.z0 || z === T.z1)
      const a = (z === T.z0 || z === T.z1) ? x - T.x0 : z - T.z0
      for (let y = b0; y <= b1; y++) {
        const opening = !corner && [2, 3, 5, 6].includes(a) && y >= b0 + 1 && y <= b1 - 1
        if (opening) {
          const f = z === T.z0 ? 'north' : z === T.z1 ? 'south' : x === T.x0 ? 'west' : 'east'
          louvres(t, [[x, y, z]], f, P.wood)
        } else t.set(x, y, z, y === b0 || y === b1 ? P.dress : W)
      }
    }
    for (let x = T.x0 + 1; x <= T.x1 - 1; x++) t.set(x, T.beamY, T.cz, `${P.wood}_log`, { axis: 'x' })
    for (const [x, y, z] of T.bells) t.set(x, y, z, 'bell', { attachment: 'ceiling', facing: 'north', powered: 'false' })
    // a corbelled cornice, the belfry's stone ceiling, a parapet with corner pinnacles
    for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) t.set(x, T.cornice, z, (x === T.x0 || x === T.x1 || z === T.z0 || z === T.z1) ? P.dress : W)
    for (let z = T.z0; z <= T.z1; z++) { stair(t, T.x0 - 1, T.cornice, z, P.dressStairs, 'east', 'top'); stair(t, T.x1 + 1, T.cornice, z, P.dressStairs, 'west', 'top') }
    for (let x = T.x0; x <= T.x1; x++) { stair(t, x, T.cornice, T.z0 - 1, P.dressStairs, 'south', 'top'); if (x < 6) stair(t, x, T.cornice, T.z1 + 1, P.dressStairs, 'north', 'top') }
    for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
      const rim = x === T.x0 || x === T.x1 || z === T.z0 || z === T.z1
      if (!rim) continue
      const corner = (x === T.x0 || x === T.x1) && (z === T.z0 || z === T.z1)
      t.set(x, T.parapet, z, P.dress)
      const py = T.parapet + 1
      if (corner) { t.set(x, py, z, W); t.set(x, py + 1, z, P.carve); t.set(x, py + 2, z, P.dress); t.set(x, py + 3, z, P.wallWall) }
      else if ((x + z) % 2 === 0) t.set(x, py, z, P.wallWall)
    }
    // the spire: an octagon of deepslate tiles, banded twice, a tile wall for its tip
    octSpire(t, { cx: T.cx, cz: T.cz, y0: T.spire.y0, y1: T.spire.y1, r0: T.spire.r0, tip: P.roof.wall },
      (x, y) => (y === 40 || y === 45 ? P.spireBand : P.roof.full))
  })
  // the rose: glass and tracery inside the stone ring
  for (const r of roseCells(R.r)) {
    if (r.kind === 'ring') continue
    const x = R.cx + r.a, y = R.cy + r.b
    if (r.kind === 'hub') c.set(x, y, R.z, P.carve)
    else if (r.kind === 'spoke') c.set(x, y, R.z, P.dress)
    else c.set(x, y, R.z, r.zone === 0 ? P.rose.inner : r.zone === 1 ? P.rose.mid : P.rose.outer[r.sector % 2], pane)
  }
  return c
}

// ---------------------------------------------------------------------------------------------------------------------
const BANNED = ['calcite', 'bedrock', 'barrier', 'command_block', 'structure_block', 'structure_void', 'jigsaw', 'spawner',
  'fire', 'sand', 'gravel', 'water', 'lava']
const COUNTS = [
  ['white glass', 1, ['white_stained_glass', 'white_stained_glass_pane']], ['light blue glass', 1, ['light_blue_stained_glass', 'light_blue_stained_glass_pane']],
  ['yellow glass', 1, ['yellow_stained_glass', 'yellow_stained_glass_pane']], ['red glass', 1, ['red_stained_glass', 'red_stained_glass_pane']],
  ['pews', 10, ['dark_oak_stairs']], ['gold', 1, ['gold_block']], ['candles', 1, ['white_candle', 'candle']],
  ['banners', 2, ['red_wall_banner', 'white_wall_banner', 'blue_wall_banner']], ['lecterns', 3, ['lectern']],
  ['brewing stand', 1, ['brewing_stand']], ['polished andesite', 50, ['polished_andesite']],
  ['deepslate tiles', 200, ['deepslate_tile_stairs', 'deepslate_tiles']], ['weathered bricks', 50, ['cracked_stone_bricks', 'mossy_stone_bricks']],
]

export function verify(c = draw()) {
  const snaps = [1, 2, 3, 4].map(n => c.stageCanvas(n))
  const changed = stagesOnlyAdd(snaps)
  if (changed) throw new Error(changed)
  const out = []
  for (let n = 1; n <= STAGES; n++) {
    const s = snaps[n - 1]
    const cells = [...s.cells.keys()].map(k => k.split(',').map(Number))
    const seen = new Set(cells.filter(([, y]) => y === 0).map(p => p.join(',')))
    const todo = [...seen].map(k => k.split(',').map(Number))
    while (todo.length) {
      const [x, y, z] = todo.pop()
      for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) for (let dz = -1; dz <= 1; dz++) {
        const k = `${x + dx},${y + dy},${z + dz}`
        if (s.cells.has(k) && !seen.has(k)) { seen.add(k); todo.push([x + dx, y + dy, z + dz]) }
      }
    }
    const floating = cells.filter(p => !seen.has(p.join(',')))
    if (floating.length) throw new Error(`stage ${n}: ${floating.length} blocks not grounded, e.g. ${floating.slice(0, 3).join(' ')}`)
    for (const [k, v] of s.cells) {
      const id = v.id.slice(10)
      if (BANNED.includes(id)) throw new Error(`stage ${n}: ${id} at ${k}`)
      const [x, y, z] = k.split(',').map(Number)
      if (id === 'lantern' && !(v.props.hanging === 'true' ? s.get(x, y + 1, z) : s.get(x, y - 1, z))) throw new Error(`stage ${n}: a lantern at ${k} hangs from nothing`)
      if (id === 'bell' && !s.get(x, y + 1, z)) throw new Error(`stage ${n}: a bell at ${k} hangs from nothing`)
      if (id === 'ladder' && !s.get(x - 1, y, z) && !s.get(x + 1, y, z)) throw new Error(`stage ${n}: a ladder at ${k} without its wall`)
      if (id.endsWith('_door') && v.props.half === 'lower') {
        const up = s.get(x, y + 1, z)
        if (!up || up.id !== v.id || up.props.facing !== v.props.facing) throw new Error(`stage ${n}: a door at ${k} without its upper half`)
        const [fx, fz] = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] }[v.props.facing]
        for (const sgn of [1, -1]) for (const dy of [0, 1]) if (s.get(x + sgn * fx, y + dy, z + sgn * fz)) throw new Error(`stage ${n}: the door at ${k} is blocked at ${x + sgn * fx},${y + dy},${z + sgn * fz}`)
      }
    }
    out.push(cells.length)
  }
  const s = snaps[3]
  const cells = [...s.cells.keys()].map(k => k.split(',').map(Number))
  const lo = i => Math.min(...cells.map(p => p[i])), hi = i => Math.max(...cells.map(p => p[i]))
  const wide = hi(0) - lo(0) + 1, long = hi(2) - lo(2) + 1
  if (wide < 37 || wide > 41 || long < 53 || long > 57 || hi(1) < 51 || hi(1) > 53 || lo(1) !== 0) throw new Error(`size ${wide} x ${long}, top ${hi(1)}`)
  const midX = (lo(0) + hi(0)) / 2
  for (const side of [-1, 1]) {
    const top = Math.max(...cells.filter(p => p[2] <= 12 && Math.sign(p[0] - midX) === side).map(p => p[1]))
    if (top < 51 || top > 53) throw new Error(`front tower ${side}: top ${top}`)
  }
  const row = cells.filter(p => p[2] === 25 && p[1] === 2).map(p => p[0])
  const span = Math.max(...row) - Math.min(...row) + 1
  if (span < 25 || span > 29) throw new Error(`body at z 25 spans ${span}`)
  const total = s.cells.size
  if (total < 12600 || total > 15400) throw new Error(`block budget: ${total}, wants 12,600-15,400`)
  const count = ids => [...s.cells.values()].filter(v => ids.includes(v.id.slice(10))).length
  const counts = {}
  for (const [what, min, ids] of COUNTS) { counts[what] = count(ids); if (counts[what] < min) throw new Error(`${what}: ${counts[what]}, wants ${min}`) }
  const bells = cells.filter(p => s.name(...p) === 'minecraft:bell')
  if (bells.length < 3 || bells.some(p => p[1] < 31)) throw new Error(`bells: ${bells.map(p => p.join(',')).join(' ')}`)
  const [ax, ay, az] = CEREMONY.altar
  if (!s.get(ax, ay - 1, az) || s.get(ax, ay, az) || s.get(ax, ay + 1, az)) throw new Error('the altar spot is not open on a floor')
  if (s.name(...CEREMONY.gold) !== 'minecraft:gold_block') throw new Error('no gold block behind the altar spot')
  const [gx, gy, gz] = CEREMONY.groom
  if (!s.get(gx, gy - 1, gz) || s.get(gx, gy, gz) || s.get(gx, gy + 1, gz)) throw new Error('the groom has no place')
  for (const [x, y, z] of CEREMONY.guests) if (s.get(x, y, z) || s.get(x, y + 1, z) || !s.get(x, y - 1, z)) throw new Error(`guest ${x},${y},${z} has no place`)
  const [cx, cy, cz] = CEREMONY.camera
  for (let i = 0; i <= 40; i++) {
    const t = i / 40, x = Math.round(cx + (ax - cx) * t), y = Math.round(cy + (3 - cy) * t), z = Math.round(cz + (az - cz) * t)
    if (s.get(x, y, z)) throw new Error(`the camera's line is blocked at ${x},${y},${z} (${s.name(x, y, z)})`)
  }
  return { perStage: out.map((v, i) => (i ? v - out[i - 1] : v)), total, counts }
}

export default function build(api) {
  const c = draw()
  const v = verify(c)
  console.error(`great_cathedral_a: stage adds ${v.perStage.join(' / ')}; ${v.total} blocks; ${JSON.stringify(v.counts)}`)
  const n = Number(process.env.STAGE ?? STAGES)
  const s = c.stageCanvas(n)
  const cut = process.env.CUT
  const canvas = cut !== undefined ? s.cutAbove(Number(cut)) : s
  return { structure: canvas.toStructure(api), metadata: { name: `great_cathedral_a_s${n}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
