// Parts for staged monuments and sky instruments (the Observatory Wonder, ROADMAP 49 / 52.6): a canvas that draws a
// Wonder stage by stage and refuses to touch an earlier stage, a round drum with its string courses and pilasters, a
// voxel dome with a glazed shutter strip, a tube along any line (a telescope), a quadrant bowl (a sundial's scale), and
// the great gnomon's stair. Every block comes from the palette passed in; none is picked here.
import { Canvas, stair, slab } from './canvas.mjs'

/** StagedCanvas: begin(n) before drawing stage n. A later stage may not change or clear a block an earlier stage drew
 *  (the builders would have to break finished work). add() draws only where no earlier stage drew (a later part resting
 *  on an earlier one). stageCells(n) gives the Wonder as it stands after stage n. */
export class StagedCanvas extends Canvas {
  constructor(size) { super(size); this.stage = 1; this.stageOf = new Map() }
  begin(n) { this.stage = n }
  earlier(x, y, z) { const s = this.stageOf.get(this.key(x, y, z)); return s !== undefined && s < this.stage }
  set(x, y, z, id, props = {}) {
    if (this.earlier(x, y, z)) {
      const had = this.get(x, y, z)
      throw new Error(`stage ${this.stage} draws over stage ${this.stageOf.get(this.key(x, y, z))}'s ${had.id} at ${x},${y},${z} (${id})`)
    }
    super.set(x, y, z, id, props)
    this.stageOf.set(this.key(x, y, z), this.stage)
  }
  add(x, y, z, id, props = {}) { if (!this.earlier(x, y, z)) this.set(x, y, z, id, props) }
  clear(x, y, z) {
    if (this.earlier(x, y, z)) throw new Error(`stage ${this.stage} clears an earlier stage's block at ${x},${y},${z}`)
    super.clear(x, y, z); this.stageOf.delete(this.key(x, y, z))
  }
  stageCanvas(n) {
    const c = new Canvas(this.size)
    for (const [k, v] of this.cells) if (this.stageOf.get(k) <= n) c.cells.set(k, v)
    c.spots = this.spots
    return c
  }
}

/** r2 of a cell from a centre (cell centres; cx, cz may be halves). */
const d2 = (x, z, cx, cz) => (x - cx) ** 2 + (z - cz) ** 2

/** roundWall: a round wall of cells with inner2 < r2 <= outer2 round (cx, cz) from y0 to y1, `keep(x, y, z)` false for an
 *  opening. Uses add(), so it rests on (and around) earlier work. */
export function roundWall(c, { cx, cz, inner2, outer2, y0, y1 }, wall, keep = () => true) {
  const R = Math.ceil(Math.sqrt(outer2))
  for (let x = Math.floor(cx - R); x <= cx + R; x++) for (let z = Math.floor(cz - R); z <= cz + R; z++) {
    const r = d2(x, z, cx, cz)
    if (r <= inner2 || r > outer2) continue
    for (let y = y0; y <= y1; y++) if (keep(x, y, z) && c.inside(x, y, z)) add(c, x, y, z, wall)
  }
}

/** roundCourse: every cell with r2 <= outer2 at height y (a floor, a course). */
export function roundCourse(c, { cx, cz, outer2, inner2 = -1, y }, id, keep = () => true) {
  const R = Math.ceil(Math.sqrt(outer2))
  for (let x = Math.floor(cx - R); x <= cx + R; x++) for (let z = Math.floor(cz - R); z <= cz + R; z++) {
    const r = d2(x, z, cx, cz)
    if (r <= outer2 && r > inner2 && keep(x, y, z) && c.inside(x, y, z)) add(c, x, y, z, id)
  }
}

const add = (c, x, y, z, id, props = {}) => (c.add ? c.add(x, y, z, id, props) : c.set(x, y, z, id, props))

/** skyDome: a shell of cells with (R - t)^2 < r3^2 <= R^2 above y0, centred at (cx, y0, cz); `pick(x, y, z)` gives each
 *  cell's block (a glazed strip, a different course), null to leave it open. seat2: the bottom course is filled in to
 *  that r2 (it sits whole on its drum, no gaps under the shell). Returns the cells drawn. */
export function skyDome(c, { cx, cz, y0, R, t = 1.05, squash = 1, seat2 = null }, pick) {
  const cells = []
  for (let x = Math.floor(cx - R); x <= Math.ceil(cx + R); x++)
    for (let z = Math.floor(cz - R); z <= Math.ceil(cz + R); z++)
      for (let y = y0; y <= y0 + Math.ceil(R * squash); y++) {
        const r = Math.sqrt(d2(x, z, cx, cz) + ((y - y0) / squash) ** 2)
        const seat = seat2 !== null && y === y0 && d2(x, z, cx, cz) > seat2 // the bottom course is a full ring on its drum
        if (r > R || (r <= R - t && !seat)) continue
        const id = pick(x, y, z)
        if (id && c.inside(x, y, z)) { add(c, x, y, z, id); cells.push([x, y, z]) }
      }
  return cells
}

/** lineTube: every cell within `radius` of the line from `from` along `dir` (unit) for t in [t0, t1]; `pick(t)` gives the
 *  block at that length (bands, a lens). Cells an earlier stage drew are left as they are. */
export function lineTube(c, { from, dir, t0, t1, radius }, pick) {
  const [px, py, pz] = from, [dx, dy, dz] = dir
  const lo = [0, 1, 2].map(i => Math.floor(Math.min(from[i] + dir[i] * t0, from[i] + dir[i] * t1) - radius - 1))
  const hi = [0, 1, 2].map(i => Math.ceil(Math.max(from[i] + dir[i] * t0, from[i] + dir[i] * t1) + radius + 1))
  for (let x = lo[0]; x <= hi[0]; x++) for (let y = lo[1]; y <= hi[1]; y++) for (let z = lo[2]; z <= hi[2]; z++) {
    const vx = x - px, vy = y - py, vz = z - pz
    const t = vx * dx + vy * dy + vz * dz
    if (t < t0 || t > t1) continue
    const q = Math.sqrt(Math.max(0, vx * vx + vy * vy + vz * vz - t * t))
    if (q <= radius && c.inside(x, y, z)) add(c, x, y, z, pick(t))
  }
}

/** quadrantBowl: a sundial's quadrant scale on one side of a gnomon: a quarter circle of radius R in the x-y plane
 *  whose bottom touches the gnomon's face at x = face (side -1 west, +1 east), its surface rising to the outer rim at
 *  y = base + R; masonry fills under it from `base`, a channel between curbs (z0 and z1) is the walked scale. Returns
 *  the outer rim's [x, topY]. */
export function quadrantBowl(c, { face, side, z0, z1, base, R }, { fill, scale, curb, tick, mark }) {
  let rim = null
  for (let k = 0; k <= R; k++) {
    const x = face + side * (k + 1)
    const top = base + Math.round(R - Math.sqrt(R * R - k * k))
    for (let z = z0; z <= z1; z++) {
      for (let y = base; y < top; y++) add(c, x, y, z, fill)
      const edge = z === z0 || z === z1
      if (edge) { add(c, x, top, z, fill); add(c, x, top + 1, z, curb) } // the curbs stand a block proud of the scale
      else add(c, x, top, z, k === R ? mark : k % 2 === 0 ? tick : scale)
    }
    rim = [x, top]
  }
  return rim
}

/** gnomonStair: the great sundial's hypotenuse: a solid triangle x0..x1 rising one block per block along z from
 *  (z0, y0), a stair up its middle between cheek walls two blocks high. `hole(x, y, z)` keeps a passage open under it. */
export function gnomonStair(c, { x0, x1, z0, z1, y0, base = 1 }, { fill, stairs, cheek, cheekCap }, hole = () => false) {
  for (let z = z0; z <= z1; z++) {
    const step = y0 + (z - z0)
    for (let x = x0; x <= x1; x++) {
      const isCheek = x === x0 || x === x1
      for (let y = base; y < step; y++) if (!hole(x, y, z)) add(c, x, y, z, fill)
      if (isCheek) { add(c, x, step, z, cheek); add(c, x, step + 1, z, cheekCap) }
      else if (!c.earlier || !c.earlier(x, step, z)) stair(c, x, step, z, stairs, 'south')
    }
  }
}
