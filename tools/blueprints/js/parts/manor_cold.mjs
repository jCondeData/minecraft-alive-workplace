// Cold manors: the pieces the snowy and taiga looks of the Noble's Manor are drawn with (ROADMAP 49.10f, as 49.10a).
// Every block comes from the palette passed in; nothing here is hard-coded but the chain under a lamp.
//   unionRoof   several gable ranges (45-degree or steep two-up-one-across) drawn as ONE roof surface: each column the
//               highest range over it, so a lower range dies into a higher one's slope in clean valleys; overhangs
//               never stand inside another range's walls, and a lower range runs on under a higher one's overhang
//   fillUp      a wall carried up from y0 to just under whatever roof is over each of its cells (gables, party walls)
//   hip45       a 45-degree pyramid (hipped) cap over a square tower, its eave ring one block out
//   hangTo      a lantern hung from the first solid block above it on as many chain links as it takes
import { stair } from './canvas.mjs'

/** unionRoof: gable ranges `masses` [{axis, across: [a0, a1] (odd span, the eave columns at both ends), along: [l0, l1]
 *  (the rake rows at both ends), eaveY, walls: {x0, x1, z0, z1} (the range's walls: columns outside it are overhang),
 *  keep(x, z)?}] drawn as one surface at `pitch` 1 (45 degrees) or 2 (two up per one across, a full block under every
 *  stair). A column takes the highest range over it (the first listed on a tie): its ridge a `roof.full`, any other cell
 *  a `roof.stairs` rising to that range's ridge. A range's overhang column inside another range's walls is dropped (a
 *  wing's eave never stands over the hall it meets), and under an overhang column the lower ranges run on (their eave
 *  reaches the wall under the higher one's). Under each stair, full blocks close it down to its lowest neighbour (a
 *  valley's side, the steep pitch's step). `keep(x, y, z)` false leaves a cell (a stack). Returns top(x, z): {y, ridge,
 *  over, d, m} of the column's highest range, or null. */
export function unionRoof(c, masses, roof, { pitch = 1, keep = () => true } = {}) {
  const inBox = (b, x, z) => b && x >= b.x0 && x <= b.x1 && z >= b.z0 && z <= b.z1
  const surf = (m, x, z) => {
    const [u, v] = m.axis === 'z' ? [x, z] : [z, x]
    if (u < m.across[0] || u > m.across[1] || v < m.along[0] || v > m.along[1]) return null
    if (m.keep && !m.keep(x, z)) return null
    const half = (m.across[1] - m.across[0]) / 2
    if (!Number.isInteger(half)) throw new Error(`unionRoof: span ${m.across} is even`)
    const d = Math.min(u - m.across[0], m.across[1] - u)
    const ridge = d === half
    const y = ridge ? m.eaveY + pitch * half - (pitch - 1) : m.eaveY + pitch * d
    const over = !inBox(m.walls, x, z)
    if (over && masses.some(o => o !== m && inBox(o.walls, x, z))) return null
    const mid = (m.across[0] + m.across[1]) / 2
    const rise = m.axis === 'z' ? (u < mid ? 'east' : 'west') : (u < mid ? 'south' : 'north')
    return { y, ridge, d, over, rise, m }
  }
  const all = (x, z) => masses.map(m => surf(m, x, z)).filter(Boolean).sort((a, b) => b.y - a.y)
  const top = (x, z) => all(x, z)[0] ?? null
  const put = (x, y, z, s) => {
    if (!c.inside(x, y + (c.base ?? 0), z) || !keep(x, y, z)) return
    if (s.ridge) c.set(x, y, z, roof.full)
    else stair(c, x, y, z, roof.stairs, s.rise)
  }
  const [w, , d] = c.size
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    const list = all(x, z)
    if (!list.length) continue
    const t = list[0]
    put(x, t.y, z, t)
    if (t.over) for (const s of list.slice(1)) if (s.y < t.y - 1) put(x, s.y, z, s)
  }
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    const t = top(x, z)
    if (!t || t.ridge || (t.over && t.d === 0)) continue
    let lo = t.y - (pitch === 2 && t.d > 0 ? 1 : 0)
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const n = top(x + dx, z + dz)
      if (n && n.m !== t.m) lo = Math.min(lo, n.y + 1)
    }
    for (let y = lo; y < t.y; y++) if (keep(x, y, z) && !c.get(x, y, z)) c.set(x, y, z, roof.full)
  }
  return top
}

/** fillUp: each [x, z] of `cells` from y0 up to just under the first cell `stop(x, y, z)` names (by default any block;
 *  a roof's blocks, to carry a wall up past what stands in it), never past `limit`; `pick(x, y, z)` chooses each block
 *  (an id, [id, props], or null to leave the cell). */
export function fillUp(c, cells, y0, pick, { stop = (x, y, z) => !!c.get(x, y, z), limit = 64 } = {}) {
  for (const [x, z] of cells) {
    let top = y0
    while (top < limit && !stop(x, top, z)) top++
    if (top >= limit) continue
    for (let y = y0; y < top; y++) {
      const got = pick(x, y, z)
      if (!got) continue
      const [id, props] = Array.isArray(got) ? got : [got, {}]
      c.set(x, y, z, id, props)
    }
  }
}

/** hip45: a 45-degree pyramid cap centred on (cx, cz): a ring of `roof.stairs` r out from the centre at y0 (the eave,
 *  one block past the walls when r is their half-width + 1), each ring one in and one up from the last, a `roof.full`
 *  at the peak (y0 + r). Returns the peak's y. */
export function hip45(c, cx, cz, r, y0, roof) {
  const face = (dx, dz) => (Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? 'east' : 'west') : (dz < 0 ? 'south' : 'north'))
  for (let k = 0; k <= r; k++) {
    const rr = r - k, y = y0 + k
    if (rr === 0) { c.set(cx, y, cz, roof.full); continue }
    for (let dx = -rr; dx <= rr; dx++) for (let dz = -rr; dz <= rr; dz++)
      if (Math.max(Math.abs(dx), Math.abs(dz)) === rr) stair(c, cx + dx, y, cz + dz, roof.stairs, face(dx, dz))
  }
  return y0 + r
}

/** hangTo: a hanging-lamp furniture piece `f` ({pos, chain}) with its chain set to reach the first block above the
 *  lantern (so a lamp keeps hanging from whatever roof, collar or floor the look puts over it). */
export function hangTo(c, f, max = 12) {
  const [x, y, z] = f.pos
  let n = 0
  while (n < max && !c.get(x, y + n + 1, z)) n++
  return { ...f, chain: n }
}
