// The Stadium (a Wonder), the recipe: assembles plan.mjs stage by stage on a StagedCanvas (a later stage may only add:
// it throws on any block an earlier stage drew), each stage in passes (block-out, depth and detail, interior, light),
// and returns the Wonder as it stands after STAGE (default 4) for the minecraft-builder skill's runner. verify() checks
// every stage against the brief and refuses a stage that changes an earlier one. CUT=<y> cuts everything above y.
import {
  SIZE, STAGES, PALETTE as P, BOWL, RINGS, GAP, WALL_TOP, POSTS, RING, BOX_SPOTS, MASTS, MAST_TOP, BOOTH,
} from './plan.mjs'
import { mix, stair, slab, StagedCanvas, stagesOnlyAdd, ringOf, spineDistance, floodMast, rakedRow } from '../parts/index.mjs'

const paving = mix(P.paving, 31)
const riser = mix(P.riser, 7)
const ring = (x, z) => ringOf(x, z, BOWL)
const inGap = (x, z) => x >= GAP.x0 && x <= GAP.x1 && z < BOWL.cz
const cellsOfRing = r => {
  const out = []
  for (let x = 0; x < SIZE[0]; x++) for (let z = 0; z < SIZE[2]; z++) if (ring(x, z) === r && !inGap(x, z)) out.push([x, z])
  return out
}
// the angle (degrees, 0..360) of a cell round the bowl, from the nearest spine point
const angleOf = (x, z) => {
  const sx = x - BOWL.cx, dx = Math.sign(sx) * Math.max(0, Math.abs(sx) - BOWL.half)
  return (Math.atan2(z - BOWL.cz, dx) * 180 / Math.PI + 360) % 360
}
const lantern = (c, x, y, z, hanging = false) => c.set(x, y, z, 'lantern', { hanging: String(hanging), waterlogged: 'false' })
// the outer wall's ground arches: on the round ends every third 12-degree step, on the straights one in the middle
const isArch = (x, z) => {
  if (Math.abs(x - BOWL.cx) <= BOWL.half) return z > BOWL.cz && Math.abs(x - BOWL.cx) <= 1
  return Math.floor(angleOf(x, z) / 12) % 3 === 1
}
const isPier = (x, z) => !isArch(x, z) && (isArch(x + 1, z) || isArch(x - 1, z) || isArch(x, z + 1) || isArch(x, z - 1)) && ring(x, z) === RINGS.wall

// the aisles: stairs up the rake where the rows would otherwise be a wall of one-block steps. One up the back straight's
// middle, one along each round end's axis, and four on the diagonals (a band two cells wide, so it climbs cell to cell)
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
const upward = (x, z) => [['north', 0, -1], ['south', 0, 1], ['west', -1, 0], ['east', 1, 0]]
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
      // the run's middle cell, and a cell of the row below beside it
      const pick = comp.map(q => q.split(',').map(Number)).sort((a, b) => angleOf(...a) - angleOf(...b))
      for (const [x, z] of [pick[pick.length >> 1], ...pick]) {
        const step = [['south', 0, -1], ['north', 0, 1], ['east', -1, 0], ['west', 1, 0]]
          .map(([f, dx, dz]) => [f, x + dx, z + dz]).find(([, nx, nz]) => ring(nx, nz) === r - 1 && !inGap(nx, nz) && !isAisle(nx, nz))
        if (step) { EXTRA.set(`${step[1]},${step[2]}`, step[0]); break }
      }
    }
  }
}

export function draw() {
  planExtra()
  const c = new StagedCanvas(SIZE)

  // === Stage I: The Field: the ground, the pitch and its chalk, the two Training Posts ===============================
  c.begin(1)
  for (let x = 0; x < SIZE[0]; x++) for (let z = 0; z < SIZE[2]; z++) {
    const r = ring(x, z)
    const d = Math.hypot(x - RING[0], z - RING[2])
    if (r <= RINGS.field) {
      const chalk = x === RING[0] || Math.round(d) === 4 || (x === RING[0] && z === RING[2])
      c.set(x, 0, z, chalk ? P.chalk : P.grass, chalk ? {} : { snowy: 'false' })
    } else if (r === RINGS.touch) c.set(x, 0, z, P.chalk)
    else c.set(x, 0, z, inGap(x, z) || (z < 3 && x >= GAP.x0 - 1 && x <= GAP.x1 + 1) ? P.path : paving)
  }
  for (const [x, y, z] of POSTS) {
    c.set(x, 0, z, P.chalk) // a chalk mark under each post (no grass under a block)
    c.set(x, y, z, P.post, { facing: x < RING[0] ? 'east' : 'west' })
  }

  // === Stage II: The Lower Stands: the pitch wall, four rows of red and white seats, the walk ========================
  c.begin(2)
  // Pass 1, block-out: the pitch wall and the rake
  for (const [x, z] of cellsOfRing(RINGS.barrier)) {
    if (isAisle(x, z)) { stair(c, x, 1, z, P.aisle, upward(x, z)); continue }
    c.set(x, 1, z, P.barrier); slab(c, x, 2, z, P.barrierCap)
  }
  for (let r = RINGS.lower[0]; r <= RINGS.lower[1]; r++) {
    const row = (r - RINGS.lower[0]) >> 1
    const seat = (r - RINGS.lower[0]) % 2 === 0 ? (row % 2 === 0 ? P.red : P.white) : null
    rakedRow(c, cellsOfRing(r), { top: 1 + row }, { fill: riser, tread: P.tread, seat: seat && ((x, z) => aisleSeat(seat, x, z)) })
    if (seat === null) for (const [x, z] of cellsOfRing(r)) if (isAisle(x, z)) stair(c, x, 2 + row, z, P.aisle, stepFacing(x, z))
  }
  // the walk: one block over the top row's tread, its inner edge the concourse's wall
  rakedRow(c, cellsOfRing(RINGS.walk), { top: 5 }, { fill: riser, tread: P.path })
  // Pass 2, detail: the gap's cheeks dressed with stone-brick steps up to each row's end (the rows climb from the gap)
  for (const [x, z] of cellsOfRing(RINGS.walk)) if (inGap(x + 1, z) || inGap(x - 1, z)) c.set(x, 5, z, P.course)

  // === Stage III: The Upper Stands and the Concourse: the upper rake on a vault, the brick outer wall ===============
  c.begin(3)
  for (const [x, z] of cellsOfRing(RINGS.walk)) if (isAisle(x, z)) stair(c, x, 6, z, P.aisle, stepFacing(x, z))
  for (let r = RINGS.upper[0]; r <= RINGS.upper[1]; r++) {
    const k = r - RINGS.upper[0]
    const seat = () => (k % 2 ? P.white : P.red) // rows of the village's colours, red first as below
    rakedRow(c, cellsOfRing(r), { top: 6 + k }, { fill: riser, tread: P.tread, seat: (x, z) => aisleSeat(seat(x, z), x, z) })
    if (r < RINGS.upper[1]) for (const [x, z] of cellsOfRing(r)) if (isAisle(x, z)) stair(c, x, 7 + k, z, P.aisle, stepFacing(x, z))
  }
  // the outer wall: brick y 1-11 with ground arches and quartz piers, a quartz course at 5, a cornice at 12, coping 13
  for (const [x, z] of cellsOfRing(RINGS.wall)) {
    const arch = isArch(x, z), pier = isPier(x, z)
    for (let y = 1; y <= WALL_TOP; y++) {
      if (arch && y <= 3) continue
      let id = P.brick
      if (pier && y !== 5 && y < WALL_TOP) id = y === 3 || y === WALL_TOP - 1 ? P.pierCap : P.pier // pilasters with base and capital
      if (arch && y === 4) id = P.course
      if (y === 5 || y === WALL_TOP) id = P.course
      if (y >= 8 && y <= 9 && id === P.brick && Math.floor(angleOf(x, z) / 6) % 2 === 0) id = P.panel // a frieze of stone panels between the pilasters
      c.set(x, y, z, id, id === P.pier ? { axis: 'y' } : {})
    }
    slab(c, x, WALL_TOP + 1, z, P.coping)
  }

  // === Stage IV: The Canopy, the Gate and the Floodlights ==========================================================
  c.begin(4)
  // the copper canopy over the back straight and the back of the curves: from the wall top inward over the upper rake
  for (let x = 0; x < SIZE[0]; x++) for (let z = BOWL.cz + 12; z < SIZE[2]; z++) {
    const r = ring(x, z)
    if (r < 22 || r > RINGS.wall) continue
    if (r === RINGS.wall) { c.set(x, WALL_TOP + 2, z, P.canopy); continue }
    if (r === 22) stair(c, x, WALL_TOP + 2, z, P.canopyEdge, 'north', 'top')
    else slab(c, x, WALL_TOP + 2, z, P.canopySlab, 'top')
  }
  // the gate: a brick tower front over the cut, quartz-framed, the opening five wide and five high
  for (let x = GAP.x0 - 2; x <= GAP.x1 + 2; x++) for (let y = 6; y <= 16; y++) {
    const z = 0
    if (c.get(x, y, z)) continue
    let id = P.brick
    if (y === 6) id = P.course
    if (x === GAP.x0 - 2 || x === GAP.x1 + 2) id = P.pier
    if (y === 15) id = P.course
    if (y === 16) id = (x - GAP.x0) % 2 === 0 ? P.pierCap : null
    if (id === null) continue
    c.set(x, y, z, id, id === P.pier ? { axis: 'y' } : {})
  }
  // the village's colours flying over the gate: a banner on each corner cap, facing out to the gate spot
  for (const x of [GAP.x0 - 2, GAP.x1 + 2]) c.set(x, 17, 0, P.banner, { rotation: '8' })
  stair(c, GAP.x0, 5, 0, P.arch, 'east', 'top')
  stair(c, GAP.x1, 5, 0, P.arch, 'west', 'top')
  // the floodlight masts in the four corners, each its own detector over its own eight lamps
  for (const [x, z] of MASTS) {
    c.set(x, 1, z, P.collar)
    floodMast(c, [x, 2, z], MAST_TOP, { shaft: P.mastShaft, core: P.core, lamp: P.lamp, detector: P.detector, wire: P.wire, collar: P.collar, bracket: P.bracket })
  }
  // the nurse's booth: an open kiosk in the front-left corner, three spruce posts and the wall, a copper roof
  const B = BOOTH
  for (const [x, z] of B.posts) for (let y = 1; y <= 3; y++) c.set(x, y, z, P.boothPost, { axis: 'y' })
  for (let x = B.x0; x <= B.x1; x++) for (let z = B.z0; z <= B.z1; z++) {
    if (c.get(x, 4, z)) continue
    const edge = x === B.x0 || x === B.x1 || z === B.z0 || z === B.z1
    if (edge) slab(c, x, 4, z, P.canopySlab)
    else c.set(x, 4, z, P.canopy)
  }
  c.set(...B.healing, P.healing, { facing: 'south' })
  c.set(...B.brewing, P.brewing, { has_bottle_0: 'false', has_bottle_1: 'false', has_bottle_2: 'false' })
  lantern(c, B.lamp[0], B.lamp[1], B.lamp[2], true)
  for (const [x, z] of B.counter) slab(c, x, 1, z, P.counter, 'top')
  return c
}

const BANNED = ['calcite', 'bedrock', 'barrier', 'command_block', 'structure_block', 'spawner', 'light', 'carved_pumpkin', 'jack_o_lantern', 'lectern', 'barrel', 'furnace']

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
      if (id === 'redstone_wire' && !s.get(x, y - 1, z)) throw new Error(`stage ${n}: dust over air at ${k}`)
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
  // the way in: the gate spot's column in front of the plot is open, and the cut runs from z = 0 to the field
  for (let z = 0; z < BOWL.cz; z++) for (let x = GAP.x0; x <= GAP.x1; x++) if (s.get(x, 1, z) || s.get(x, 2, z)) throw new Error(`the way in is blocked at ${x},1,${z}`)
  return { perStage: out, total, seats: seats.length, red2, white2, chalk1, size: [w, d, h] }
}

export default function build(api) {
  const c = draw()
  const v = verify(c)
  console.error(`stadium: stage totals ${v.perStage.join(' / ')}; ${v.total} blocks; ${v.seats} seats (stage II ${v.red2} red, ${v.white2} white); stage I chalk ${v.chalk1}; size ${v.size.join(' x ')}`)
  const n = Number(process.env.STAGE ?? STAGES)
  const s = c.stageCanvas(n)
  const cut = process.env.CUT
  const canvas = cut !== undefined ? s.cutAbove(Number(cut)) : s
  const name = `stadium_s${n}${cut !== undefined ? `_cut${cut}` : ''}`
  return { structure: canvas.toStructure(api), metadata: { name } }
}
