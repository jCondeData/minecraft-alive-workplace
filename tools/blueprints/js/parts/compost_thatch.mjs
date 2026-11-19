// Thatched barns and compost bins (parts library, tools/blueprints/REBUILD.md; grown from the Compost Yard, "the Bin
// Barn", 49): steep gable roofs joined into one surface (a T of ridges at the same pitch, valleys where they meet), a
// straw ridge along it, the trim under an overhang, and a compost bin's graded heap behind its front board. Every block
// comes from the caller's palette; none is picked here.
import { stair, trapdoor } from './canvas.mjs'

const AXIS_PROPS = id => (/(_log|_wood|hay_block|_stem|basalt|bone_block)$/.test(id) ? true : false)

/** steepUnionRoof: several steep gable roofs (two up for one across: a stair over a full block in every column) drawn
 *  as one surface. Each mass is {axis, across: [a0, a1], along: [l0, l1], eaveY} with an even span (axis 'z': the
 *  ridge runs along z over the middle of x a0..a1; 'x': along x over z a0..a1). Each column (x, z) takes the highest
 *  mass over it (the first listed on a tie): its ridge cell `roof.ridge` (default `roof.full`; a hay bale or log is
 *  laid along the ridge), any other a `roof.stairs` rising toward that mass's ridge, and under it full blocks down to
 *  one above its lowest neighbour, so a wing's valley stays closed where the two slopes step past each other.
 *  `keep(x, y, z)` returns false for a cell another part owns. Returns { top(x, z), under(x, z) }: the roof's highest
 *  and lowest cell over a column (null where no mass is), for gables and trim. */
export function steepUnionRoof(c, masses, roof, keep = () => true) {
  const one = (m, x, z) => {
    const [u, v] = m.axis === 'z' ? [x, z] : [z, x]
    if (u < m.across[0] || u > m.across[1] || v < m.along[0] || v > m.along[1]) return null
    const half = (m.across[1] - m.across[0]) / 2
    const i = Math.min(u - m.across[0], m.across[1] - u)
    return i === half ? m.eaveY + 2 * half - 1 : m.eaveY + 2 * i
  }
  const pick = (x, z) => {
    let best = null, by = null
    for (const m of masses) {
      const y = one(m, x, z)
      if (y !== null && (best === null || y > best)) { best = y; by = m }
    }
    return [best, by]
  }
  const [w, , d] = c.size
  const tops = new Map(), unders = new Map()
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    const [y] = pick(x, z)
    if (y !== null) tops.set(`${x},${z}`, y)
  }
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    const [y, m] = pick(x, z)
    if (y === null) continue
    let low = y
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const n = tops.get(`${x + dx},${z + dz}`)
      if (n !== undefined) low = Math.min(low, n + 1)
    }
    const u = m.axis === 'z' ? x : z
    const mid = (m.across[0] + m.across[1]) / 2
    if (u === mid) {
      const ridge = roof.ridge ?? roof.full
      if (keep(x, y, z)) c.set(x, y, z, ridge, AXIS_PROPS(ridge) ? { axis: m.axis } : {})
    } else if (keep(x, y, z)) {
      stair(c, x, y, z, roof.stairs, m.axis === 'z' ? (u < mid ? 'east' : 'west') : (u < mid ? 'south' : 'north'))
    }
    for (let yy = y - 1; yy >= low; yy--) if (keep(x, yy, z)) c.set(x, yy, z, roof.full)
    unders.set(`${x},${z}`, Math.min(y, low))
  }
  return {
    top: (x, z) => tops.get(`${x},${z}`) ?? null,
    under: (x, z) => unders.get(`${x},${z}`) ?? null,
  }
}

/** overhangTrim: a dark board under each overhanging column (cells [x, z]) of a roof, one below its lowest cell
 *  (`under` from steepUnionRoof), where nothing is drawn: an upside-down slab (`id` a slab) so the edge reads apart from
 *  the wall under it. */
export function overhangTrim(c, cells, under, id) {
  for (const [x, z] of cells) {
    const y = under(x, z)
    if (y === null || c.get(x, y - 1, z)) continue
    c.set(x, y - 1, z, id, { type: 'top', waterlogged: 'false' })
  }
}

/** compostBin: one bin of a compost row: its heap filling the box {x0, x1, z0, z1, y0, y1} (`heap(x, y, z)` picks
 *  each block: a fairMix of the bin's stage, green to black; leaves are drawn persistent), and its front board, open
 *  trapdoors (`wood`) standing against the heap's front face at `boardY` on the line `boardZ` (or `boardX`), looking
 *  out `out`. A heap's top behind a fascia is out of sight: draw the box no higher than shows. */
export function compostBin(c, { x0, x1, z0, z1, y0, y1 }, heap, { wood, out, boardY = null, boardZ = null, boardX = null }) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) for (let y = y0; y <= y1; y++) {
    const id = typeof heap === 'function' ? heap(x, y, z) : heap
    const props = /leaves$/.test(id) ? { persistent: 'true', distance: '1', waterlogged: 'false' }
      : /(podzol|grass_block|mycelium)$/.test(id) ? { snowy: 'false' }
        : AXIS_PROPS(id) ? { axis: 'y' } : {}
    c.set(x, y, z, id, props)
  }
  if (boardY === null) return
  if (boardZ !== null) for (let x = x0; x <= x1; x++) trapdoor(c, x, boardY, boardZ, `${wood}_trapdoor`, out, 'bottom', true)
  if (boardX !== null) for (let z = z0; z <= z1; z++) trapdoor(c, boardX, boardY, z, `${wood}_trapdoor`, out, 'bottom', true)
}
