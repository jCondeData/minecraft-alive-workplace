// Great Wall parts (ROADMAP 52.6, grown from the Great Wall Wonder; any long wall, gate tower or pavilion can use them).
// Every block comes from the palette the caller passes.
import { stair, slab } from './canvas.mjs'

/** StagedCanvas helper: wraps a Canvas so every block it lays remembers the stage that first laid it (`c.stage`). A
 *  later write keeps the earliest stage but takes the later block, so each stage's snapshot shows the block the finished
 *  work has there: a stage never changes what an earlier one built. `snapshot(c, n, Canvas)` copies the blocks of
 *  stages 1..n into a fresh canvas. */
export function staged(c) {
  c.stage = 1
  const set = c.set.bind(c)
  c.set = (x, y, z, id, props = {}) => {
    const prev = c.get(x, y, z)
    set(x, y, z, id, props)
    c.get(x, y, z).stage = prev?.stage ?? c.stage
  }
  return c
}
export function snapshot(c, n, Canvas) {
  const s = new Canvas(c.size)
  for (const [k, v] of c.cells) if (v.stage <= n) s.cells.set(k, { id: v.id, props: v.props })
  return s
}

/** wallSection: one cross-section of a battered curtain wall, laid at column x. `rows` lists, bottom up, each course as
 *  [y, z0, z1, block, {front, back}] where front/back (optional) are stair ids put one cell outside z0 / z1 as the
 *  batter's step (rising toward the wall). `keep(y)` drops courses (a ragged, half-fallen end). Cells already drawn
 *  (a stair built against the wall first) are left as they are. */
export function wallSection(c, x, rows, keep = () => true) {
  const put = (y, z, f) => { if (!c.get(x, y, z)) f() }
  for (const [y, z0, z1, block, edge = {}] of rows) {
    if (!keep(y)) continue
    for (let z = z0; z <= z1; z++) put(y, z, () => c.set(x, y, z, block))
    if (edge.front) put(y, z0 - 1, () => stair(c, x, y, z0 - 1, edge.front, 'south'))
    if (edge.back) put(y, z1 + 1, () => stair(c, x, y, z1 + 1, edge.back, 'north'))
  }
}

/** ridgedHip: a hipped roof over the rectangle x0..x1, z0..z1 (eaves on its rim at y), courses of `roof.stairs` rising
 *  inward one a block, closed by a ridge of `roof.slab` along the long side when the short side runs out (or a single
 *  `roof.full` when both do). `flare`: each eave corner gets one more stair a block up, facing out along the long side,
 *  so the corners lift like a gate pavilion's. Returns the ridge's y. */
export function ridgedHip(c, x0, z0, x1, z1, y, roof, { flare = false } = {}) {
  let k = 0
  for (; x0 + k < x1 - k && z0 + k < z1 - k; k++) {
    const a = x0 + k, b = x1 - k, f = z0 + k, g = z1 - k, yy = y + k
    const put = (x, z, facing, shape = 'straight') => c.set(x, yy, z, roof.stairs, { facing, half: 'bottom', shape, waterlogged: 'false' })
    for (let x = a + 1; x < b; x++) { put(x, f, 'south'); put(x, g, 'north') }
    for (let z = f + 1; z < g; z++) { put(a, z, 'east'); put(b, z, 'west') }
    put(a, f, 'south', 'outer_right'); put(b, f, 'south', 'outer_left')
    put(a, g, 'north', 'outer_left'); put(b, g, 'north', 'outer_right')
  }
  const a = x0 + k, b = x1 - k, f = z0 + k, g = z1 - k, top = y + k
  if (a === b && f === g) c.set(a, top, f, roof.full)
  else for (let x = a; x <= b; x++) for (let z = f; z <= g; z++) slab(c, x, top, z, roof.slab)
  if (flare) {
    for (const [x, z, facing] of [[x0, z0, 'east'], [x1, z0, 'west'], [x0, z1, 'east'], [x1, z1, 'west']])
      if (!c.get(x, y + 1, z)) stair(c, x, y + 1, z, roof.stairs, facing, 'top')
  }
  return top
}

/** pavilionSkirt: one or more courses of a skirt roof round a box (a pavilion's lower eave): course i is the ring i blocks in
 *  from x0..x1, z0..z1, at y + i, stairs rising inward. Corners flare like ridgedHip's when asked. */
export function pavilionSkirt(c, x0, z0, x1, z1, y, courses, roof, { flare = false } = {}) {
  for (let i = 0; i < courses; i++) {
    const a = x0 + i, b = x1 - i, f = z0 + i, g = z1 - i, yy = y + i
    const put = (x, z, facing, shape = 'straight') => c.set(x, yy, z, roof.stairs, { facing, half: 'bottom', shape, waterlogged: 'false' })
    for (let x = a + 1; x < b; x++) { put(x, f, 'south'); put(x, g, 'north') }
    for (let z = f + 1; z < g; z++) { put(a, z, 'east'); put(b, z, 'west') }
    put(a, f, 'south', 'outer_right'); put(b, f, 'south', 'outer_left')
    put(a, g, 'north', 'outer_left'); put(b, g, 'north', 'outer_right')
  }
  if (flare) for (const [x, z, facing] of [[x0, z0, 'east'], [x1, z0, 'west'], [x0, z1, 'east'], [x1, z1, 'west']])
    if (!c.get(x, y + 1, z)) stair(c, x, y + 1, z, roof.stairs, facing, 'top')
}

/** archway: a passage through a wall along z (z0..z1), x0..x1 wide (odd), open from y0 to y0 + h - 1 with the top
 *  course's two end cells filled by upside-down `stairs` (a rounded head); `voussoir` rings both faces one cell round
 *  the opening, `key` the keystone over the middle. */
export function archway(c, x0, x1, z0, z1, y0, h, { stairs, voussoir, key }) {
  const top = y0 + h - 1, mid = (x0 + x1) / 2
  for (const z of [z0, z1]) {
    for (let y = y0; y <= top + 1; y++) { c.set(x0 - 1, y, z, voussoir); c.set(x1 + 1, y, z, voussoir) }
    for (let x = x0; x <= x1; x++) c.set(x, top + 1, z, x === mid ? key : voussoir)
  }
  for (let z = z0; z <= z1; z++) {
    for (let x = x0; x <= x1; x++) for (let y = y0; y <= top; y++) c.clear(x, y, z)
    stair(c, x0, top, z, stairs, 'west', 'top')
    stair(c, x1, top, z, stairs, 'east', 'top')
  }
}

/** portcullis: a raised grille's teeth hanging in an arch head (bars along x at row z, at y). */
export function portcullis(c, xs, y, z, bars) {
  for (const x of xs) c.set(x, y, z, bars, { north: 'false', south: 'false', east: 'true', west: 'true', waterlogged: 'false' })
}

/** horseStair: a broad flight up the face of a wall (the Great Wall's ramps), `width` cells deep in z from z0, from
 *  (x0, y0) rising one a block toward `rise`, `steps` long, filled solid below with `fill`; it ends on a landing of
 *  full blocks `landing` wide (in x) and reaching back to zTo, level with the top step. Returns the landing's y. */
export function horseStair(c, { x0, z0, width, steps, rise, y0 = 0, landing = 2, zTo = z0 + width - 1 }, stairs, fill, top) {
  const dx = rise === 'east' ? 1 : -1
  for (let i = 0; i < steps; i++) {
    const x = x0 + dx * i, y = y0 + i
    for (let z = z0; z < z0 + width; z++) {
      for (let yy = 0; yy < y; yy++) c.set(x, yy, z, fill)
      stair(c, x, y, z, stairs, rise)
    }
  }
  const yTop = y0 + steps - 1
  for (let j = 0; j < landing; j++) {
    const x = x0 + dx * (steps + j)
    for (let z = z0; z <= zTo; z++) { for (let yy = 0; yy < yTop; yy++) c.set(x, yy, z, fill); c.set(x, yTop, z, top) }
  }
  return yTop
}

/** doorIn: a whole two-high door at (x, y, z) (lower half at y), facing `facing` (the side it opens away from). */
export function doorIn(c, x, y, z, id, facing, hinge = 'left') {
  for (const half of ['lower', 'upper']) c.set(x, y + (half === 'upper' ? 1 : 0), z, id, { facing, half, hinge, open: 'false', powered: 'false' })
}
