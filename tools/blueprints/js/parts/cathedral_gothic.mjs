// Parts for gothic churches (the Great Cathedral, 52.6 A): a canvas seen in a mirror (draw one side, get both), a roof
// laid from a height field (one function per mass, valleys where they meet), a pointed opening, an octagonal spire, a
// rose window, a flying buttress, and louvred openings. Every block comes from the palette passed in; none is picked here.
import { stair } from './canvas.mjs'

const FLIP = { east: 'west', west: 'east', north: 'north', south: 'south', up: 'up', down: 'down' }

/** mirrorCanvas: a view of `c` reflected across x = (w - 1) / 2: a part drawn on it lands on the other side, its
 *  facings turned east <-> west and door hinges swapped. Keeps add() (stage-safe) when the canvas has it. */
export function mirrorCanvas(c) {
  const W = c.size[0] - 1
  const flip = (p = {}) => {
    const q = { ...p }
    if (q.facing) q.facing = FLIP[q.facing]
    if (q.hinge) q.hinge = q.hinge === 'left' ? 'right' : 'left'
    return q
  }
  return {
    size: c.size, mirrored: true,
    set: (x, y, z, id, p) => c.set(W - x, y, z, typeof id === 'function' ? (() => id(x, y, z)) : id, flip(p)),
    add: (x, y, z, id, p) => (c.add ? c.add(W - x, y, z, typeof id === 'function' ? id(x, y, z) : id, flip(p)) : c.set(W - x, y, z, id, flip(p))),
    get: (x, y, z) => c.get(W - x, y, z),
    clear: (x, y, z) => c.clear(W - x, y, z),
    inside: (x, y, z) => c.inside(W - x, y, z),
    earlier: (x, y, z) => (c.earlier ? c.earlier(W - x, y, z) : false),
  }
}

/** both: run `draw(canvas)` on the canvas and again on its mirror (a symmetric pair of towers, aisles, arms). */
export function both(c, draw) { draw(c); draw(mirrorCanvas(c)) }

const put = (c, x, y, z, id, p = {}) => (c.add ? c.add(x, y, z, id, p) : c.set(x, y, z, id, p))

/** fieldRoof: a roof laid from a height field. `H(x, z)` is the top cell's y over each column (null: no roof there);
 *  `base` the lowest y a column may fill down to (one over the wall tops). Each column gets its top stair facing up the
 *  steepest way (or a slab where no neighbour is higher: a ridge), and full blocks under it down to its lowest
 *  neighbour's top + 1 (a 2:1 slope two thick, a 45-degree one a single stair), or right down to `base` beside an eave
 *  or an open end (no slot over a wall top, a closing wedge where a range stops). `keep(x, y, z)` false leaves a cell alone.
 *  Uses add(), so it never touches an earlier stage. Returns the field (for gables and copings). */
export function fieldRoof(c, cols, H, base, roof, keep = () => true) {
  const [w, , d] = c.size
  const top = (x, z) => (x < 0 || z < 0 || x >= w || z >= d ? null : H(x, z))
  for (const [x, z] of cols) {
    const h = top(x, z)
    if (h === null) continue
    const n = [['east', 1, 0], ['west', -1, 0], ['south', 0, 1], ['north', 0, -1]].map(([f, dx, dz]) => [f, top(x + dx, z + dz)])
    const higher = n.filter(([, v]) => v !== null && v > h).sort((a, b) => b[1] - a[1])
    const lows = n.map(([, v]) => (v === null ? base - 1 : v))
    const floorY = lows.some(v => v <= base) ? base : Math.max(base, Math.min(...lows) + 1)
    for (let y = floorY; y < h; y++) if (keep(x, y, z)) put(c, x, y, z, roof.full)
    if (!keep(x, h, z)) continue
    if (higher.length) put(c, x, h, z, roof.stairs, { facing: higher[0][0], half: 'bottom', shape: 'straight', waterlogged: 'false' })
    else put(c, x, h, z, roof.slab, { type: 'bottom', waterlogged: 'false' })
  }
}

/** pointedOpening: the cells of a pointed (lancet) opening `a0..a1` wide along a wall, open from y0 to the spring
 *  y1, then narrowing by one cell a side per row to a point. Returns [[a, y], ...] (along, up). */
export function pointedOpening(a0, a1, y0, y1) {
  const cells = []
  for (let y = y0; y <= y1; y++) for (let a = a0; a <= a1; a++) cells.push([a, y])
  let lo = a0 + 1, hi = a1 - 1, y = y1 + 1
  while (lo <= hi) { for (let a = lo; a <= hi; a++) cells.push([a, y]); lo++; hi--; y++ }
  return cells
}

/** octSpire: an octagonal spire's shell round (cx, cz) from y0 (radius r0) tapering to a point at y1, its tip a
 *  `tip` block over the top. `pick(x, y, z)` gives each cell's block (bands, lucarne courses). */
export function octSpire(c, { cx, cz, y0, y1, r0, tip }, pick) {
  const oct = (dx, dz) => Math.max(Math.abs(dx), Math.abs(dz), (Math.abs(dx) + Math.abs(dz)) * 0.72)
  const R = Math.ceil(r0) + 1
  for (let y = y0; y <= y1; y++) {
    const r = r0 * (y1 - y) / (y1 - y0) + 0.3
    for (let dx = -R; dx <= R; dx++) for (let dz = -R; dz <= R; dz++) {
      const o = oct(dx, dz)
      if (o > r || (o <= r - 1.25 && r >= 2.6 && y < y1 && y > y0 + 1)) continue // solid once too slim to be hollow
      put(c, cx + dx, y, cz + dz, pick(cx + dx, y, cz + dz))
    }
  }
  if (tip) put(c, cx, y1 + 1, cz, tip)
}

/** roseCells: the cells of a rose window of radius r round (0, 0) in its own plane, each tagged ring (the stone
 *  frame), hub, spoke (tracery) or glass with its zone (0 inner .. 2 outer) and sector (0..7). */
export function roseCells(r) {
  const out = []
  const R = Math.ceil(r)
  for (let a = -R; a <= R; a++) for (let b = -R; b <= R; b++) {
    const d = Math.hypot(a, b)
    if (d > r) continue
    const sector = Math.floor(((Math.atan2(b, a) + Math.PI) / (2 * Math.PI)) * 8 + 0.5) % 8
    let kind
    if (d > r - 1) kind = 'ring'
    else if (d < 0.9) kind = 'hub'
    else if (a === 0 || b === 0) kind = 'spoke'
    else kind = 'glass'
    out.push({ a, b, d, kind, zone: d < 1.8 ? 0 : d < r - 1.9 ? 1 : 2, sector })
  }
  return out
}

/** flyingButtress: a pier standing out from an aisle wall at (x0..x1 out from the wall, z) and a sloping flyer from its
 *  top to the clerestory wall at xTo, one cell up per cell in, coped with stairs. `side` +1 when the wall is east of the
 *  pier (the left aisle), -1 when it is west. */
export function flyingButtress(c, { xOut, xIn, z, top, flyTo, flyY0 }, P) {
  // the pier: two steps, its outer face set back with a weathering stair
  for (let y = 1; y <= top - 5; y++) put(c, xOut, y, z, y <= 2 ? P.low : P.wall)
  stair(c, xOut, top - 4, z, P.wallStairs, 'east')
  for (let y = 1; y <= top; y++) put(c, xIn, y, z, y <= 2 ? P.low : P.wall)
  // the pinnacle on the pier's head
  put(c, xIn, top + 1, z, P.dress)
  put(c, xIn, top + 2, z, P.wallWall)
  // the flyer: a stone bar rising to the clerestory, a stair coping over each cell
  let y = flyY0
  for (let x = xIn + 1; x <= flyTo; x++, y++) {
    put(c, x, y, z, P.wall)
    stair(c, x, y + 1, z, P.wallStairs, 'east')
    stair(c, x, y - 1, z, P.wallStairs, 'west', 'top')
  }
}

/** louvres: open trapdoors across an opening's cells (a belfry's sound louvres), facing out of the wall. */
export function louvres(c, cells, facing, wood) {
  for (const [x, y, z] of cells) put(c, x, y, z, `${wood}_trapdoor`, { facing, half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })
}
