// The Stadium (a Wonder), the recipe: assembles plan.mjs stage by stage on a StagedCanvas (a later stage may only add:
// it throws on any block an earlier stage drew), each stage in passes (block-out, depth and detail, interior, light),
// and returns the Wonder as it stands after STAGE (default 4) for the minecraft-builder skill's runner. verify() checks
// every stage against the brief and refuses a stage that changes an earlier one. CUT=<y> cuts everything above y.
import {
  SIZE, STAGES, PALETTE as P, BOWL, RINGS, HALL_TOP, GAP, WALL_TOP, BAY, POSTS, RING, BOX_SPOTS, PAVILIONS, MAST_FOOT,
  MAST_TOP, FRONT, COVERED, BOARD, GLYPHS,
} from './plan.mjs'
import { mix, stair, slab, trapdoor, StagedCanvas, stagesOnlyAdd, ringOf, spineDistance, floodMast, rakedRow } from '../parts/index.mjs'

const paving = mix(P.paving, 31)
const riser = mix(P.riser, 7)
const ring = (x, z) => ringOf(x, z, BOWL)
const [W, , D] = SIZE
const inPlot = (x, z) => x >= 0 && x < W && z >= 0 && z < D
const inGap = (x, z) => x >= GAP.x0 && x <= GAP.x1 && z < BOWL.cz
const DIRS4 = [['north', 0, -1], ['south', 0, 1], ['west', -1, 0], ['east', 1, 0]]
const OPP = { north: 'south', south: 'north', west: 'east', east: 'west' }
const cellsOfRing = r => {
  const out = []
  for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) if (ring(x, z) === r && !inGap(x, z)) out.push([x, z])
  return out
}
// the angle (degrees, 0..360) of a cell round the bowl, from the nearest spine point
const angleOf = (x, z) => {
  const sx = x - BOWL.cx, dx = Math.sign(sx) * Math.max(0, Math.abs(sx) - BOWL.half)
  return (Math.atan2(z - BOWL.cz, dx) * 180 / Math.PI + 360) % 360
}
// where a cell stands round the bowl, 0..1, from the front middle westward (the straights by the block, the round ends by
// the angle on a ring of 24.5): the same for every ring, so the wall's bays line up from the face ring to the hall
const RP = 24.5, PERIM = 4 * BOWL.half + 2 * Math.PI * RP
function around(x, z) {
  const { cx, cz, half } = BOWL, sx = x - cx
  let p
  if (Math.abs(sx) <= half) p = z < cz ? (sx <= 0 ? -sx : PERIM - sx) : half + Math.PI * RP + (x - (cx - half))
  else if (sx < 0) p = half + RP * Math.atan2(-(x - (cx - half)), -(z - cz))
  else p = 3 * half + Math.PI * RP + RP * Math.atan2(x - (cx + half), z - cz)
  return ((p / PERIM) % 1 + 1) % 1
}
const NBAYS = Math.round(PERIM / BAY)
const lantern = (c, x, y, z, hanging = false) => c.set(x, y, z, 'lantern', { hanging: String(hanging), waterlogged: 'false' })
const doorAt = (c, x, y, z, facing, hinge = 'left') => {
  for (const half of ['lower', 'upper']) c.set(x, y + (half === 'upper' ? 1 : 0), z, P.door, { facing, half, hinge, open: 'false', powered: 'false' })
}
const pane = (c, x, y, z) => c.set(x, y, z, P.glass, { north: 'false', south: 'false', east: 'false', west: 'false', waterlogged: 'false' })
// the side of a cell toward the field (the neighbour nearest the spine): a cornice stair faces it
const inward = (x, z) => DIRS4.map(([f, dx, dz]) => [f, spineDistance(x + dx, z + dz, BOWL)]).sort((a, b) => a[1] - b[1])[0][0]

// --- the plan's parts as cell tests -----------------------------------------------------------------------------------
const inFront = (x, z) => x >= FRONT.x0 && x <= FRONT.x1 && z < FRONT.z1
const pavilionOf = (x, z) => PAVILIONS.find(p => x >= p.x0 && x <= p.x1 && z >= p.z0 && z <= p.z1 && ring(x, z) >= RINGS.face)
const inPavilionBox = (x, z) => PAVILIONS.some(p => x >= p.x0 && x <= p.x1 && z >= p.z0 && z <= p.z1)
const onBoundary = (p, x, z) => x === p.x0 || x === p.x1 || z === p.z0 || z === p.z1
// the concourse's two end walls, either side of the cut
const isEndWall = (x, z) => (x === GAP.x0 - 1 || x === GAP.x1 + 1) && z < BOWL.cz && ring(x, z) >= RINGS.walk && ring(x, z) <= RINGS.upper[1]
const isHall = (x, z) => ring(x, z) >= RINGS.walk && ring(x, z) <= RINGS.upper[1] && !inGap(x, z) && !isEndWall(x, z)
const interiorOf = (p, x, z) => pavilionOf(x, z) === p && !onBoundary(p, x, z)
// a pavilion drawn in the front-left corner's frame (x from the west side, z from the front) and turned into its corner
const frame = p => {
  const mx = p.x0 > BOWL.cx, mz = p.z0 > BOWL.cz
  const X = x => (mx ? W - 1 - x : x), Z = z => (mz ? D - 1 - z : z)
  const F = f => (mx && (f === 'east' || f === 'west') ? OPP[f] : mz && (f === 'north' || f === 'south') ? OPP[f] : f)
  return { X, Z, F }
}

// the aisles: stairs up the rake where the rows would otherwise be a wall of one-block steps. One up the back straight's
// middle, one along each round end's axis, and four on the diagonals (a band two cells wide, so it climbs cell to cell);
// extra aisle steps: a one-cell ring on a curve joins only corner to corner in places, so each run of a single row that
// no aisle reaches gets one stair from the row below (toward the run's middle). Filled in by planExtra() below.
const EXTRA = new Map()
const isAisle = (x, z) => EXTRA.has(`${x},${z}`) || mainAisle(x, z)
const mainAisle = (x, z) => {
  const r = ring(x, z)
  if (r < RINGS.barrier || r >= RINGS.wall || inGap(x, z)) return false
  const ox = x - BOWL.cx, dz = Math.abs(z - BOWL.cz), ex = Math.max(0, Math.abs(ox) - BOWL.half)
  if (ex === 0) return z > BOWL.cz && ox === 0
  if (dz === 0) return true
  return ex - dz === 0 || ex - dz === 1
}
// an aisle stair faces up the rake: toward the neighbour farthest from the spine
const upward = (x, z) => DIRS4
  .map(([f, dx, dz]) => [f, spineDistance(x + dx, z + dz, BOWL) + (isAisle(x + dx, z + dz) ? 0.5 : 0)])
  .sort((a, b) => b[1] - a[1])[0][0]
const aisleSeat = (id, x, z) => (isAisle(x, z) ? null : id)
const stepFacing = (x, z) => EXTRA.get(`${x},${z}`) ?? upward(x, z)
function planExtra() {
  if (EXTRA.size) return
  for (let r = RINGS.walk; r <= RINGS.upper[1]; r++) {
    const cells = new Set(cellsOfRing(r).map(p => p.join(',')))
    const seen = new Set()
    for (const k of cells) {
      if (seen.has(k)) continue
      const comp = [k]; seen.add(k)
      for (let i = 0; i < comp.length; i++) {
        const [x, z] = comp[i].split(',').map(Number)
        for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
          const q = `${x + dx},${z + dz}`
          if (cells.has(q) && !seen.has(q)) { seen.add(q); comp.push(q) }
        }
      }
      if (comp.some(q => isAisle(...q.split(',').map(Number)))) continue
      const pick = comp.map(q => q.split(',').map(Number)).sort((a, b) => angleOf(...a) - angleOf(...b))
      for (const [x, z] of [pick[pick.length >> 1], ...pick]) {
        const step = [['south', 0, -1], ['north', 0, 1], ['east', -1, 0], ['west', 1, 0]]
          .map(([f, dx, dz]) => [f, x + dx, z + dz]).find(([, nx, nz]) => ring(nx, nz) === r - 1 && !inGap(nx, nz) && !isAisle(nx, nz))
        if (step) { EXTRA.set(`${step[1]},${step[2]}`, step[0]); break }
      }
    }
  }
}
// the seats' colours: the west half's rows start red, the east half's white (the home and the away ends)
const seatColour = (row, x) => ((row + (x > BOWL.cx ? 1 : 0)) % 2 === 0 ? P.red : P.white)

// --- the outer wall's rhythm: pilasters on the face ring, one per bay; a window in the middle of each bay -------------
function pickPerBay(cells, offset) {
  const best = new Map()
  for (const [x, z] of cells) {
    const t = around(x, z) * NBAYS - offset
    const k = ((Math.round(t) % NBAYS) + NBAYS) % NBAYS, err = Math.abs(t - Math.round(t))
    if (!best.has(k) || err < best.get(k)[1]) best.set(k, [[x, z], err])
  }
  return new Map([...best].map(([k, [cell]]) => [k, cell]))
}
const END_DOORS = [[BOWL.cx - BOWL.half - RINGS.wall, BOWL.cz, 'west'], [BOWL.cx + BOWL.half + RINGS.wall, BOWL.cz, 'east']]
const faceKey = (x, z) => `${x},${z}`
let LAYOUT = null
function layout() {
  if (LAYOUT) return LAYOUT
  const faceCells = cellsOfRing(RINGS.face).filter(([x, z]) => !inFront(x, z) && !inPavilionBox(x, z))
  const wallCells = cellsOfRing(RINGS.wall)
  const wallSet = new Set(wallCells.map(([x, z]) => faceKey(x, z)))
  const doorFace = new Set(END_DOORS.map(([x, z, f]) => faceKey(x + (f === 'west' ? -1 : 1), z)))
  const pilasters = new Set([...pickPerBay(faceCells, 0).values()].map(([x, z]) => faceKey(x, z)))
  for (const k of doorFace) pilasters.delete(k)
  for (const [x, z, f] of END_DOORS) { const fx = x + (f === 'west' ? -1 : 1); pilasters.add(faceKey(fx, z - 1)); pilasters.add(faceKey(fx, z + 1)) }
  // windows: the bay's middle wall cell, where the wall runs straight past it (the pane joins the brick both sides), the
  // hall inside and the bare face outside
  const windows = new Set()
  const doorCells = new Set(END_DOORS.map(([x, z]) => faceKey(x, z)))
  const frontWindows = new Set([[22, 1], [36, 1]].map(([x, z]) => faceKey(x, z)))
  for (const [x, z] of pickPerBay(wallCells.filter(([x, z]) => !inPavilionBox(x, z) && !inFront(x, z)), 0.5).values()) {
    const run = (wallSet.has(faceKey(x - 1, z)) && wallSet.has(faceKey(x + 1, z))) || (wallSet.has(faceKey(x, z - 1)) && wallSet.has(faceKey(x, z + 1)))
    const sides = DIRS4.map(([, dx, dz]) => [x + dx, z + dz]).filter(([a, b]) => !wallSet.has(faceKey(a, b)))
    const ok = sides.length === 2 && sides.every(([a, b]) => (isHall(a, b) || (ring(a, b) === RINGS.face && !pilasters.has(faceKey(a, b)) && !inPavilionBox(a, b))))
    if (run && ok && !doorCells.has(faceKey(x, z)) && ![...doorCells].some(k => { const [a, b] = k.split(',').map(Number); return Math.abs(a - x) + Math.abs(b - z) <= 2 })) windows.add(faceKey(x, z))
  }
  for (const k of frontWindows) windows.add(k)
  // the pavilions' doors into the concourse: a wall cell with the hall two deep on one side and the room two deep on the other
  const hallDoors = new Map()
  for (const p of PAVILIONS) {
    const inner = [p.x0 === p.corner[0] ? p.x1 : p.x0, p.z0 === p.corner[1] ? p.z1 : p.z0]
    const found = []
    for (const [x, z] of wallCells) {
      if (x < p.x0 || x > p.x1 || z < p.z0 || z > p.z1) continue
      for (const [f, dx, dz] of DIRS4) {
        if (isHall(x + dx, z + dz) && isHall(x + 2 * dx, z + 2 * dz) && interiorOf(p, x - dx, z - dz) && interiorOf(p, x - 2 * dx, z - 2 * dz)
) found.push([x, z, f])
      }
    }
    found.sort((a, b) => Math.abs(Math.abs(a[0] - inner[0]) - Math.abs(a[1] - inner[1])) - Math.abs(Math.abs(b[0] - inner[0]) - Math.abs(b[1] - inner[1])))
    if (!found.length) throw new Error(`no door from the concourse into the ${p.name} pavilion`)
    hallDoors.set(p.name, found[0])
  }
  // the concourse's lamps: one under the ceiling every other bay, on the hall's middle ring
  const lamps = [...pickPerBay(cellsOfRing(RINGS.walk + 2).filter(([x, z]) => isHall(x, z)), 0.5)].filter(([k]) => k % 2 === 0).map(([, cell]) => cell)
  LAYOUT = { pilasters, windows, doorFace, hallDoors, lamps, wallSet }
  return LAYOUT
}

export function draw() {
  planExtra()
  const L = layout()
  const c = new StagedCanvas(SIZE)

  // === Stage I: The Field: the ground, the pitch and its chalk, the two Training Posts ===============================
  c.begin(1)
  const boxLine = (x, z) => {
    // the trainer boxes: chalk round each post's end of the field (x 15-20 and 38-43, z 21-27)
    const ex = x <= BOWL.cx ? x : 2 * BOWL.cx - x
    return (ex === 20 && z >= 21 && z <= 27) || ((z === 21 || z === 27) && ex <= 20)
  }
  for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
    const r = ring(x, z)
    const d = Math.hypot(x - RING[0], z - RING[2])
    if (r <= RINGS.field) {
      const chalk = x === RING[0] || Math.round(d) === 4 || boxLine(x, z)
      c.set(x, 0, z, chalk ? P.chalk : P.grass, chalk ? {} : { snowy: 'false' })
    } else if (r === RINGS.touch) c.set(x, 0, z, P.chalk)
    else c.set(x, 0, z, inGap(x, z) || (z < 3 && x >= GAP.x0 - 1 && x <= GAP.x1 + 1) ? P.path : paving)
  }
  for (const [x, y, z] of POSTS) {
    c.set(x, 0, z, P.chalk) // a chalk mark under each post (no grass under a block)
    c.set(x, y, z, P.post, { facing: x < RING[0] ? 'east' : 'west' })
  }

  // === Stage II: The Lower Stands: the pitch wall, three rows of red and white seats ==================================
  c.begin(2)
  for (const [x, z] of cellsOfRing(RINGS.barrier)) {
    if (isAisle(x, z)) { stair(c, x, 1, z, P.aisle, upward(x, z)); continue }
    c.set(x, 1, z, P.barrier); slab(c, x, 2, z, P.barrierCap)
  }
  for (let r = RINGS.lower[0]; r <= RINGS.lower[1]; r++) {
    const row = (r - RINGS.lower[0]) >> 1
    const seated = (r - RINGS.lower[0]) % 2 === 0
    rakedRow(c, cellsOfRing(r), { top: 1 + row }, { fill: riser, tread: P.tread, seat: seated && ((x, z) => aisleSeat(seatColour(row, x), x, z)) })
    if (!seated) for (const [x, z] of cellsOfRing(r)) if (isAisle(x, z)) stair(c, x, 2 + row, z, P.aisle, stepFacing(x, z))
  }

  // === Stage III: The Upper Stands and the Covered Stand: the concourse under them, the outer wall, the pavilions ====
  c.begin(3)
  // Pass 1, block-out: the walk and the upper rows over the hall (y 1-3 left hollow), the hall's two end walls
  const lastRow = 1 + ((RINGS.lower[1] - RINGS.lower[0]) >> 1) // the top lower row's tread height (3)
  rakedRow(c, cellsOfRing(RINGS.walk), { top: lastRow + 1, hollowBelow: HALL_TOP }, { fill: riser, tread: P.path })
  for (const [x, z] of cellsOfRing(RINGS.walk)) if (isAisle(x, z)) stair(c, x, lastRow + 2, z, P.aisle, stepFacing(x, z))
  for (let r = RINGS.upper[0]; r <= RINGS.upper[1]; r++) {
    const k = r - RINGS.upper[0], top = lastRow + 2 + k
    rakedRow(c, cellsOfRing(r), { top, hollowBelow: HALL_TOP }, { fill: riser, tread: P.tread, seat: (x, z) => aisleSeat(seatColour(k, x), x, z) })
    // the back aisle climbs one more step at the top row, to the standing alcove in the wall behind it (below)
    const steps = r < RINGS.upper[1] ? isAisle : (x, z) => x === BOWL.cx && z > BOWL.cz && isAisle(x, z)
    for (const [x, z] of cellsOfRing(r)) if (steps(x, z)) stair(c, x, top + 1, z, P.aisle, stepFacing(x, z))
  }
  for (let x = 0; x < W; x++) for (let z = 0; z < BOWL.cz; z++) if (isEndWall(x, z)) for (let y = 1; y <= HALL_TOP; y++) c.set(x, y, z, P.plinth)
  for (const x of [GAP.x0 - 1, GAP.x1 + 1]) doorAt(c, x, 1, 3, x < BOWL.cx ? 'east' : 'west')
  // the outer wall: brick to 12 with the coping at 13; doors at both ends, windows in the bays (hall and upper level)
  const ALCOVE = lastRow + 2 + (RINGS.upper[1] - RINGS.upper[0]) + 3 // over the top step's tread (10)
  for (const [x, z] of cellsOfRing(RINGS.wall)) {
    // the standing alcove at the head of the back aisle: two cells left open in the wall over its top step, the
    // pilaster behind closing it (the top of the stands, under the Covered Stand, where a player stands to see it all)
    const alcove = x === BOWL.cx && z > BOWL.cz
    for (let y = 1; y <= WALL_TOP; y++) if (!(alcove && (y === ALCOVE || y === ALCOVE + 1))) c.set(x, y, z, P.brick)
    slab(c, x, WALL_TOP + 1, z, P.coping)
    if (L.windows.has(faceKey(x, z))) for (const y of [2, 3, 9, 10]) pane(c, x, y, z)
  }
  for (const [x, z, f] of END_DOORS) doorAt(c, x, 1, z, f)
  // Pass 2, depth: the face ring, a block proud of the brick: plinth, pilasters, string course, cornice and coping
  for (const [x, z] of cellsOfRing(RINGS.face)) {
    if (inFront(x, z) || inPavilionBox(x, z)) continue
    const k = faceKey(x, z)
    if (!L.doorFace.has(k)) c.set(x, 1, z, P.plinth)
    if (L.pilasters.has(k)) {
      for (let y = 2; y < WALL_TOP; y++) c.set(x, y, z, y === WALL_TOP - 1 || y === 2 ? P.pierCap : P.pier, y === WALL_TOP - 1 || y === 2 ? {} : { axis: 'y' })
    }
    c.set(x, 5, z, P.course)
    stair(c, x, WALL_TOP, z, P.cornice, inward(x, z), 'top')
    slab(c, x, WALL_TOP + 1, z, P.coping)
    // a sill under each upper window (the hall's windows sit on the plinth)
    const win = DIRS4.some(([, dx, dz]) => L.windows.has(faceKey(x + dx, z + dz)) && ring(x + dx, z + dz) === RINGS.wall)
    if (win && !L.pilasters.has(k)) slab(c, x, 8, z, P.sill)
  }
  // Pass 3, the corner pavilions: brick walls with quartz corners, a copper roof inside a quartz parapet, the pier
  for (const p of PAVILIONS) {
    const { X, Z, F } = frame(p)
    const cells = []
    for (let x = p.x0; x <= p.x1; x++) for (let z = p.z0; z <= p.z1; z++) if (pavilionOf(x, z) === p) cells.push([x, z])
    for (const [x, z] of cells) {
      const edge = onBoundary(p, x, z)
      const corner = (x === p.x0 || x === p.x1) && (z === p.z0 || z === p.z1)
      const pier = x === p.corner[0] && z === p.corner[1]
      if (edge) for (let y = 1; y <= HALL_TOP; y++) c.set(x, y, z, pier ? P.collar : corner ? P.pier : P.brick, corner && !pier ? { axis: 'y' } : {})
      c.set(x, HALL_TOP + 1, z, edge ? P.course : P.canopy)
      if (pier) { c.set(x, HALL_TOP + 2, z, P.collar) } else if (edge) slab(c, x, HALL_TOP + 2, z, P.coping)
    }
    doorAt(c, p.door[0], 1, p.door[1], p.facing)
    const [hx, hz, hf] = L.hallDoors.get(p.name)
    doorAt(c, hx, 1, hz, hf)
    // windows: either side of the front door, and (for the nurse and the refreshments) two in the outer side wall
    const win = [[X(4), Z(2)], [X(8), Z(2)]]
    if (p.name === 'nurse' || p.name === 'refresh') win.push([X(2), Z(5)], [X(2), Z(8)])
    for (const [x, z] of win) for (const y of [2, 3]) pane(c, x, y, z)
    lantern(c, X(6), HALL_TOP, Z(6), true)
    // the rooms, drawn in the front-left corner's frame
    if (p.name === 'nurse') {
      // the Nurse's Booth: the Healing Machine facing the door, the brewing stand beside it on the far wall, a counter
      c.set(X(5), 1, Z(8), P.healing, { facing: F('north') })
      c.set(X(3), 1, Z(8), P.brewing, { has_bottle_0: 'false', has_bottle_1: 'false', has_bottle_2: 'false' })
      slab(c, X(4), 1, Z(8), P.nurseCounter, 'top')
      for (const z of [4, 5]) slab(c, X(3), 1, Z(z), P.nurseCounter, 'top')
      c.set(X(3), 2, Z(4), P.fern)
      // the red cross: on a white panel over the front door, and on the roof
      for (let x = 4; x <= 8; x++) for (let y = HALL_TOP + 2; y <= HALL_TOP + 4; y++) {
        const cross = (x === 6) || (y === HALL_TOP + 3 && x >= 5 && x <= 7)
        c.set(X(x), y, Z(2), cross ? P.cross : P.crossField)
      }
      for (let x = 4; x <= 8; x++) slab(c, X(x), HALL_TOP + 5, Z(2), P.coping)
      for (let x = 3; x <= 7; x++) for (let z = 3; z <= 7; z++) {
        if (pavilionOf(X(x), Z(z)) !== p || onBoundary(p, X(x), Z(z))) continue
        c.set(X(x), HALL_TOP + 1, Z(z), x === 5 || z === 5 ? P.cross : P.crossField)
      }
    } else if (p.name === 'refresh') {
      // the Refreshment Room: a counter across the back with cakes and a candle, a menu banner, a striped awning outside
      for (const x of [3, 4, 5]) slab(c, X(x), 1, Z(7), P.counter, 'top')
      c.set(X(3), 2, Z(7), P.cake, { bites: '0' })
      c.set(X(5), 2, Z(7), P.cake, { bites: '0' })
      c.set(X(4), 2, Z(7), P.candle, { candles: '3', lit: 'false', waterlogged: 'false' })
      c.set(X(3), 1, Z(9), P.pot)
      c.set(X(3), 3, Z(4), P.wallBanner, { facing: F('east') })
      for (let x = 4; x <= 8; x++) c.set(X(x), HALL_TOP, Z(1), x % 2 === 0 ? P.awningRed : P.awningWhite)
      // a table for two by the side windows
      c.set(X(8), 1, Z(4), P.tableLeg, { north: 'false', south: 'false', east: 'false', west: 'false', waterlogged: 'false' })
      c.set(X(8), 2, Z(4), P.tableTop, { powered: 'false' })
      stair(c, X(8), 1, Z(5), P.stool, F('south'))
      stair(c, X(9), 1, Z(4), P.stool, F('east'))
    } else {
      // the changing rooms: benches under lockers along the outer wall, a bench in the middle, the team's colours
      const colours = p.name === 'home' ? P.wallBanner : P.awayBanner
      for (const z of [4, 5, 6, 7]) {
        slab(c, X(3), 1, Z(z), P.bench)
        for (const y of [2, 3]) trapdoor(c, X(3), y, Z(z), P.locker, F('east'), 'bottom', true)
      }
      for (const z of [5, 6]) slab(c, X(5), 1, Z(z), P.bench)
      for (const x of [5, 7]) c.set(X(x), HALL_TOP, Z(3), colours, { facing: F('south') })
    }
  }
  // Pass 4, light: the concourse's lanterns, hung from its ceiling
  for (const [x, z] of L.lamps) lantern(c, x, HALL_TOP, z, true)
  // and benches against its inner wall between the lamps, two long, clear of every doorway
  const doors = [...END_DOORS.map(([x, z]) => [x, z]), ...[...L.hallDoors.values()].map(([x, z]) => [x, z]), [GAP.x0 - 1, 3], [GAP.x1 + 1, 3]]
  const hallRing = cellsOfRing(RINGS.walk).filter(([x, z]) => isHall(x, z))
  const onRing = new Set(hallRing.map(([x, z]) => faceKey(x, z)))
  for (const [k, [x, z]] of pickPerBay(hallRing, 0)) {
    if (k % 2 === 0 || doors.some(([a, b]) => Math.abs(a - x) + Math.abs(b - z) <= 4)) continue
    const next = DIRS4.map(([, dx, dz]) => [x + dx, z + dz]).find(([a, b]) => onRing.has(faceKey(a, b)) && !c.get(a, 1, b))
    for (const [a, b] of next ? [[x, z], next] : [[x, z]]) slab(c, a, 1, b, P.bench)
  }
  // Pass 5, the Covered Stand: a copper lean-to over the back's upper rows, rising to the wall, lanterns under it
  for (let x = COVERED.x0; x <= COVERED.x1; x++) for (let z = BOWL.cz + 1; z < D; z++) {
    const r = ring(x, z), y = WALL_TOP + 2
    if (r < RINGS.upper[0] || r > RINGS.face) continue
    if (r === RINGS.upper[0]) { slab(c, x, y, z, P.canopySlab, 'top'); continue }
    c.set(x, y, z, P.canopy)
    if (r === RINGS.upper[0] + 2) stair(c, x, y + 1, z, P.canopyStair, 'south')
    if (r === RINGS.wall) { c.set(x, y + 1, z, P.canopy); stair(c, x, y + 2, z, P.canopyStair, 'south') }
    if (r === RINGS.face) { c.set(x, y + 1, z, P.canopy); c.set(x, y + 2, z, P.canopy); slab(c, x, y + 3, z, P.canopySlab) }
    if (r === RINGS.upper[0] + 1 && (x - COVERED.x0) % 4 === 2) lantern(c, x, y - 1, z, true)
  }
  // the piers the floodlights will stand on are the pavilions' outer corners (drawn above)

  // === Stage IV: The Gate and the Floodlights (and the scoreboard) =================================================
  c.begin(4)
  drawGate(c)
  drawBoard(c)
  for (const p of PAVILIONS) {
    const [x, z] = p.corner
    floodMast(c, [x, MAST_FOOT, z], MAST_TOP, { shaft: P.mastShaft, core: P.core, lamp: P.lamp, detector: P.detector, wire: P.wire, collar: P.collar, bracket: P.bracket })
  }
  return c
}

// the cells in front of the wall at x (z from 0 to the last cell outside the brick)
const frontCells = x => { const out = []; for (let z = 0; z < FRONT.z1 && ring(x, z) >= RINGS.face; z++) out.push(z); return out }

// letters on a face, read from the outside: `dirX` the x step as the reader reads left to right
function letters(c, text, { x0, dirX, y0, z }, id) {
  let col = 0
  for (const ch of text) {
    if (ch === ' ') { col += 2; continue }
    const g = GLYPHS[ch]
    for (let r = 0; r < 5; r++) for (let i = 0; i < g[r].length; i++) if (g[r][i] === 'X') c.set(x0 + dirX * (col + i), y0 + 4 - r, z, id)
    col += g[0].length + 1
  }
  return col - 1
}

// The Gate: two banner pylons, a screen of quartz pilasters between them with the recessed arch over the cut, a lintel
// and keystone, a crest in the niche over it, and the lettered attic
function drawGate(c) {
  const [s0, s1] = FRONT.screen
  const arch0 = GAP.x0 - 1, arch1 = GAP.x1 + 1 // the outer arch (the stepped reveal), x 26-32
  // the pylons: brick towers with quartz quoins and bands, a niche with the village's banner, a cap and a flag over the
  // attic
  for (const [a, b] of FRONT.pylons) {
    const mid = (a + b) >> 1
    for (let x = a; x <= b; x++) for (const z of frontCells(x)) for (let y = 1; y <= 23; y++) {
      const edge = z === 0 && (x === a || x === b)
      if (x === mid && z === 0 && y >= 6 && y <= 12) continue // a tall niche in the pylon's face
      c.set(x, y, z, edge ? P.pier : y === 5 || y === 13 || y === 21 ? P.course : P.brick, edge ? { axis: 'y' } : {})
    }
    for (let x = a; x <= b; x++) for (const z of frontCells(x)) {
      if ((x === mid && z === 1) || (z === 0 && (x === a || x === b))) c.set(x, 24, z, P.pierCap)
      else slab(c, x, 24, z, P.coping)
    }
    c.set(mid, 25, 1, P.banner, { rotation: '8' })
    c.set(mid, 11, 0, P.wallBanner, { facing: 'north' })
  }
  // the screen: z = 0 carries the order; behind it, where the brick stands at z = 2, a panel of brick at z = 1
  for (let x = s0; x <= s1; x++) {
    const zs = frontCells(x)
    for (const z of zs.slice(1)) for (let y = 1; y <= 13; y++) c.set(x, y, z, P.brick)
    if (x >= arch0 && x <= arch1) {
      // the outer arch, the lintel and keystone over it, the niche
      if (x === arch0 || x === arch1) stair(c, x, 7, 0, P.archQuartz, x === arch0 ? 'west' : 'east', 'top')
      c.set(x, 8, 0, x === RING[0] ? P.pierCap : P.course)
      continue
    }
    if (FRONT.pilasters.includes(x)) {
      for (let y = 1; y <= 12; y++) c.set(x, y, 0, P.pier, { axis: 'y' })
      c.set(x, 13, 0, P.pierCap)
      continue
    }
    c.set(x, 1, 0, P.plinth)
    c.set(x, 5, 0, P.course)
    c.set(x, 13, 0, P.course)
    if (x === 22 || x === 36) slab(c, x, 8, 0, P.sill)
  }
  // the inner arch over the cut (z = 1), five wide, and the crest over it
  for (let x = GAP.x0; x <= GAP.x1; x++) {
    if (x === GAP.x0 || x === GAP.x1) stair(c, x, 6, 1, P.arch, x === GAP.x0 ? 'west' : 'east', 'top')
    for (let y = 7; y <= 8; y++) c.set(x, y, 1, P.brick)
  }
  const crest = ['.RRR.', 'RRRRR', 'KKWKK', 'WWWWW', '.WWW.']
  for (let r = 0; r < 5; r++) for (let i = 0; i < 5; i++) {
    const ch = crest[r][i]
    c.set(GAP.x0 + i, 13 - r, 1, ch === 'R' ? P.crestRed : ch === 'K' ? P.crestBand : ch === 'W' ? P.crestWhite : P.crestGround)
  }
  // the attic: STADIUM in blackstone on quartz, read from the road (the reader faces south: east is on the left)
  for (let x = s0; x <= s1; x++) {
    for (let y = 14; y <= 20; y++) {
      c.set(x, y, 0, y === 14 || y === 20 ? P.course : P.attic)
      c.set(x, y, 1, P.brick)
    }
    slab(c, x, 21, 0, P.coping); slab(c, x, 21, 1, P.coping)
  }
  const width = letters({ set() {} }, 'STADIUM', { x0: 0, dirX: 1, y0: 0, z: 0 }, P.letter)
  letters(c, 'STADIUM', { x0: RING[0] + (width >> 1), dirX: -1, y0: 15, z: 0 }, P.letter)
}

// The Scoreboard: on the east end's wall, two blocks thick; each face has its own "2 - 1", so both read the right way
// round (from the stands the reader faces east, north on the left; from the road, west, south on the left)
function drawBoard(c) {
  const [xi, xo] = BOARD.x
  for (const x of [xi, xo]) for (let z = BOARD.z0; z <= BOARD.z1; z++) {
    for (let y = BOARD.y0; y < BOARD.y1; y++) {
      const frame = z === BOARD.z0 || z === BOARD.z1 || y === BOARD.y0 || y === BOARD.y1 - 1
      c.set(x, y, z, frame ? P.boardFrame : P.board)
    }
    slab(c, x, BOARD.y1, z, P.boardCap)
  }
  const mid = (BOARD.z0 + BOARD.z1) >> 1
  const w = letters({ set() {} }, '2-1', { x0: 0, dirX: 1, y0: 0, z: 0 }, P.digit)
  const zLeftIn = mid - (w >> 1), zLeftOut = mid + (w >> 1)
  const draw = (x, z0, dir) => {
    let col = 0
    for (const ch of '2-1') {
      const g = GLYPHS[ch]
      for (let r = 0; r < 5; r++) for (let i = 0; i < 3; i++) if (g[r][i] === 'X') c.set(x, BOARD.y0 + 5 - r, z0 + dir * (col + i), P.digit)
      col += 4
    }
  }
  draw(xi, zLeftIn, 1)
  draw(xo, zLeftOut, -1)
  // the teams' colours over the digits: home (red) over the 2, away (white) over the 1, on each face
  for (let i = 0; i < 3; i++) {
    c.set(xi, BOARD.y0 + 6, zLeftIn + i, P.awningRed); c.set(xi, BOARD.y0 + 6, zLeftIn + 8 + i, P.awningWhite)
    c.set(xo, BOARD.y0 + 6, zLeftOut - i, P.awningRed); c.set(xo, BOARD.y0 + 6, zLeftOut - 8 - i, P.awningWhite)
  }
}

const BANNED = ['calcite', 'bedrock', 'barrier', 'command_block', 'structure_block', 'spawner', 'light', 'carved_pumpkin', 'jack_o_lantern', 'lectern', 'barrel', 'furnace']
const THIN = /pane|door|trapdoor|carpet|lantern|banner|wire|candle|cake|potted|slab|stairs|brewing|healing|detector|training_post/

export function verify(c = draw()) {
  const snaps = [1, 2, 3, 4].map(n => c.stageCanvas(n))
  const changed = stagesOnlyAdd(snaps)
  if (changed) throw new Error(changed)
  const out = []
  for (let n = 1; n <= STAGES; n++) {
    const s = snaps[n - 1]
    // grounded without the Healing Machine (air without Cobblemon)
    const solid = k => s.cells.has(k) && !s.cells.get(k).id.startsWith('cobblemon:')
    const cells = [...s.cells.keys()].filter(solid).map(k => k.split(',').map(Number))
    const seen = new Set(cells.filter(([, y]) => y === 0).map(p => p.join(',')))
    const todo = [...seen].map(k => k.split(',').map(Number))
    while (todo.length) {
      const [x, y, z] = todo.pop()
      for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) for (let dz = -1; dz <= 1; dz++) {
        const k = `${x + dx},${y + dy},${z + dz}`
        if (solid(k) && !seen.has(k)) { seen.add(k); todo.push([x + dx, y + dy, z + dz]) }
      }
    }
    const floating = cells.filter(p => !seen.has(p.join(',')))
    if (floating.length) throw new Error(`stage ${n}: ${floating.length} blocks not grounded, e.g. ${floating.slice(0, 3).join(' ')}`)
    for (const [k, v] of s.cells) {
      const id = v.id.replace(/^minecraft:/, '')
      if (BANNED.includes(id)) throw new Error(`stage ${n}: ${id} at ${k}`)
      const [x, y, z] = k.split(',').map(Number)
      if (id === 'lantern' && !(v.props.hanging === 'true' ? s.get(x, y + 1, z) : s.get(x, y - 1, z))) throw new Error(`stage ${n}: a loose lantern at ${k}`)
      if (id.endsWith('_carpet')) {
        if (!s.get(x, y - 1, z)) throw new Error(`stage ${n}: a carpet over air at ${k}`)
        if (s.get(x, y + 1, z) || s.get(x, y + 2, z)) throw new Error(`stage ${n}: no head room over the seat at ${k}`)
      }
      if ((id === 'redstone_wire' || id === 'cake' || id.endsWith('candle') || id.endsWith('_door') || id === 'brewing_stand' || id.endsWith('_banner') && !id.endsWith('wall_banner')) && !s.get(x, y - 1, z)) throw new Error(`stage ${n}: ${id} over air at ${k}`)
      if (id.endsWith('wall_banner')) {
        const [, dx, dz] = DIRS4.find(([f]) => f === v.props.facing)
        if (!s.get(x - dx, y, z - dz)) throw new Error(`stage ${n}: a wall banner on nothing at ${k}`)
      }
      // glass joins a full block (or glass) on two opposite sides, as the game joins it, never one side and open air
      if (id === 'glass_pane') {
        const full = (a, b) => { const q = s.get(a, y, b); return q && (!THIN.test(q.id) || q.id.endsWith('glass_pane')) }
        if (!((full(x - 1, z) && full(x + 1, z)) || (full(x, z - 1) && full(x, z + 1)))) throw new Error(`stage ${n}: a pane that ends in the air at ${k}`)
      }
      // every door whole, two clear blocks on both sides, feet and head
      if (id.endsWith('_door') && v.props.half === 'lower') {
        const up = s.get(x, y + 1, z)
        if (!up || up.id !== v.id || up.props.half !== 'upper') throw new Error(`stage ${n}: a door not whole at ${k}`)
        const [, dx, dz] = DIRS4.find(([f]) => f === v.props.facing)
        for (const t of [1, 2, -1, -2]) for (const yy of [y, y + 1]) if (s.get(x + t * dx, yy, z + t * dz)) throw new Error(`stage ${n}: the doorway at ${k} is blocked at ${x + t * dx},${yy},${z + t * dz}`)
      }
    }
    // the arena on every stage: exactly two posts, both at y 1, the boxes' four spots room to stand
    const posts = [...s.cells].filter(([, v]) => v.id === 'aliveworkplace:training_post').map(([k]) => k)
    if (posts.join(' ') !== POSTS.map(p => p.join(',')).join(' ') && posts.slice().reverse().join(' ') !== POSTS.map(p => p.join(',')).join(' ')) throw new Error(`stage ${n}: posts ${posts}`)
    for (const [x, y, z] of BOX_SPOTS.concat([RING])) {
      if (s.get(x, y, z) || s.get(x, y + 1, z) || !s.get(x, y - 1, z)) throw new Error(`stage ${n}: no room to stand at ${x},${y},${z}`)
    }
    out.push(s.cells.size)
  }
  const s = snaps[3]
  const all = [...s.cells.keys()].map(k => k.split(',').map(Number))
  const ext = i => Math.max(...all.map(p => p[i])) - Math.min(...all.map(p => p[i])) + 1
  const [w, h, d] = [ext(0), ext(1), ext(2)]
  if (w < 47 || w > 60 || d < 37 || d > 50 || h < 16 || h > 26 || Math.min(...all.map(p => p[1])) !== 0) throw new Error(`size ${w} x ${d} x ${h}`)
  const total = s.cells.size
  if (total < 10890 || total > 13310) throw new Error(`block budget: ${total}, wants 10,890-13,310`)
  const count = (snap, id) => [...snap.cells.values()].filter(v => v.id === (id.includes(':') ? id : `minecraft:${id}`)).length
  const one = { 'cobblemon:healing_machine': 1, brewing_stand: 1, daylight_detector: 4, redstone_lamp: 32, 'aliveworkplace:training_post': 2 }
  for (const [id, want] of Object.entries(one)) if (count(s, id) !== want) throw new Error(`${id}: ${count(s, id)}, wants exactly ${want}`)
  for (const v of s.cells.values()) if (v.id === 'minecraft:daylight_detector' && v.props.inverted !== 'true') throw new Error('a detector not inverted')
  const seats = [...s.cells].filter(([, v]) => v.id.endsWith('_carpet')).map(([k]) => k.split(',').map(Number))
  if (seats.length <= 300) throw new Error(`seats: ${seats.length}`)
  for (const [x, y, z] of seats) if (Math.abs(x - RING[0]) > 40 || Math.abs(z - RING[2]) > 40 || Math.abs(y - RING[1]) > 16) throw new Error(`a seat out of the Cup's reach at ${x},${y},${z}`)
  const [red2, white2] = [count(snaps[1], 'red_carpet'), count(snaps[1], 'white_carpet')]
  if (red2 <= 100 || white2 <= 100) throw new Error(`stage II carpets: ${red2} red, ${white2} white`)
  const chalk1 = count(snaps[0], 'white_concrete')
  if (chalk1 <= 50) throw new Error(`stage I white concrete: ${chalk1}`)
  // the way in: the cut runs open from z = 0 to the field
  for (let z = 0; z < BOWL.cz; z++) for (let x = GAP.x0; x <= GAP.x1; x++) if (s.get(x, 1, z) || s.get(x, 2, z)) throw new Error(`the way in is blocked at ${x},1,${z}`)
  // the Healing Machine and the brewing stand: on the floor, nothing on the machine
  for (const [k, v] of s.cells) if (v.id === 'cobblemon:healing_machine') { const [x, y, z] = k.split(',').map(Number); if (s.get(x, y + 1, z)) throw new Error('something on the Healing Machine') }
  return { perStage: out, total, seats: seats.length, red2, white2, chalk1, size: [w, d, h] }
}

export default function build(api) {
  const c = draw()
  const v = verify(c)
  const L = layout()
  console.error(`stadium: stage totals ${v.perStage.join(' / ')}; ${v.total} blocks; ${v.seats} seats (stage II ${v.red2} red, ${v.white2} white); stage I chalk ${v.chalk1}; size ${v.size.join(' x ')}; hall doors ${[...L.hallDoors].map(([k, d]) => `${k} ${d.join(',')}`).join('; ')}; ${L.windows.size} windows, ${L.pilasters.size} pilasters, ${L.lamps.length} hall lamps`)
  const n = Number(process.env.STAGE ?? STAGES)
  const s = c.stageCanvas(n)
  const cut = process.env.CUT
  let canvas = cut !== undefined ? s.cutAbove(Number(cut)) : s
  // BOX=x0,z0,x1,z1 keeps only those columns (a close-up for a render)
  if (process.env.BOX) {
    const [x0, z0, x1, z1] = process.env.BOX.split(',').map(Number)
    const box = new canvas.constructor([x1 - x0 + 1, SIZE[1], z1 - z0 + 1])
    for (const [k, v] of canvas.cells) { const [x, y, z] = k.split(',').map(Number); if (x >= x0 && x <= x1 && z >= z0 && z <= z1) box.cells.set(`${x - x0},${y},${z - z0}`, v) }
    canvas = box
  }
  const name = `stadium_s${n}${cut !== undefined ? `_cut${cut}` : ''}${process.env.BOX ? '_box' : ''}`
  return { structure: canvas.toStructure(api), metadata: { name } }
}
