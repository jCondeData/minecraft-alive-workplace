// Parts grown from the Builder's Workshop (49, Version B): a catslide's barge trim, jetty brackets, a hoist beam out
// of a gable, timber laid on a rack and a pallet of stock (parts library, tools/blueprints/REBUILD.md). Every block
// comes from the caller.
import { Canvas, slab, stair } from './canvas.mjs'

/** catslideTrim: barge boards under a catslide's rake (catslideRoof's `under`, its ridge column `ridge`), on the
 *  overhang line `line`: an upside-down stair under each column facing up its slope, a top slab under the ridge.
 *  (bargeTrim assumes the ridge is in the middle of the span; a catslide's isn't.) Cells already drawn are kept. */
export function catslideTrim(c, axis, line, [a0, a1], ridge, under, stairs, slabId) {
  for (let u = a0; u <= a1; u++) {
    const [x, z] = axis === 'z' ? [u, line] : [line, u]
    const y = under[u] - 1
    if (y < 0 || c.get(x, y, z)) continue
    if (u === ridge) { slab(c, x, y, z, slabId, 'top'); continue }
    const up = u < ridge ? (axis === 'z' ? 'east' : 'south') : (axis === 'z' ? 'west' : 'north')
    stair(c, x, y, z, stairs, up, 'top')
  }
}

/** jettyBrackets: upside-down stairs under a jettied storey's posts, their backs on the wall below (`toward`: the
 *  side the wall is on), one per [x, y, z]. */
export function jettyBrackets(c, cells, id, toward) {
  for (const [x, y, z] of cells) stair(c, x, y, z, id, toward, 'top')
}

/** hoist: a beam (`log`) run out of a gable at [x, y, z] along `axis` for `reach` blocks (`dir` +1 or -1), a chain
 *  hanging `drop` links from its end and a lantern on the chain: the hoist a builder lifts his stock to the loft by. */
export function hoist(c, [x, y, z], axis, dir, reach, drop, log) {
  let ex = x, ez = z
  for (let i = 0; i < reach; i++) {
    ex = axis === 'x' ? x + dir * i : x
    ez = axis === 'z' ? z + dir * i : z
    c.set(ex, y, ez, log, { axis })
  }
  for (let i = 1; i <= drop; i++) c.set(ex, y - i, ez, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(ex, y - drop - 1, ez, 'lantern', { hanging: 'true', waterlogged: 'false' })
}

/** timberRack: logs laid along `axis` in the given cells (a lumber rack against a wall, or a stack on the ground). */
export function timberRack(c, cells, log, axis) {
  for (const [x, y, z] of cells) c.set(x, y, z, log, { axis })
}

/** pallet: a builder's stock on a pallet: full blocks of `stock` in `cells`, topped where given by a bottom slab
 *  (`top`: [[x, y, z, slabId], ...]). Always on something solid. */
export function pallet(c, cells, stock, top = []) {
  for (const [x, y, z] of cells) c.set(x, y, z, stock)
  for (const [x, y, z, id] of top) slab(c, x, y, z, id, 'bottom')
}

/** slopeDormer: a three-wide gabled dormer standing out of a 45-degree slope whose ridge runs along z (a catslide's or
 *  a gable roof's `under`), centred on z `zc`, its face on wall line x = `face` (y0 .. y0+1, the apex y0+2) with a
 *  pane in the middle, `dir` +1 for an east slope, -1 for a west one. Its own little roof (ridge along x, y0+3) runs
 *  from one past the face back into the main slope; the main roof's cells inside it are cleared, its cheeks are
 *  `cheek`. Breaks a long slope and lights the room under it. */
export function slopeDormer(c, { face, zc, y0, dir = 1 }, under, roof, cheek, glass) {
  const ridgeY = y0 + 3
  // the face: cheek-coloured wall round a pane, an apex
  for (const dz of [-1, 0, 1]) for (let y = y0; y <= y0 + 1; y++) c.set(face, y, zc + dz, dz === 0 ? glass : cheek)
  c.set(face, y0 + 2, zc, cheek)
  // inside: where the main slope cuts through the dormer's two-high window space, clear it and close the cheeks
  for (let x = face - dir; under[x] !== undefined && under[x] <= y0 + 1; x -= dir) {
    for (let y = y0; y <= y0 + 2; y++) c.clear(x, y, zc)
    for (let y = y0; y <= y0 + 1; y++) { c.set(x, y, zc - 1, cheek); c.set(x, y, zc + 1, cheek) }
  }
  // its roof: stairs either side rising to a full ridge, from the overhang back to where it meets the slope
  for (let x = face + dir; ; x -= dir) {
    if (under[x] === undefined || under[x] >= ridgeY + 1) break
    if (under[x] < ridgeY) {
      stair(c, x, ridgeY - 1, zc - 1, roof.stairs, 'south')
      stair(c, x, ridgeY - 1, zc + 1, roof.stairs, 'north')
    }
    c.set(x, ridgeY, zc, roof.full)
  }
}

/** ShiftedCanvas: a canvas drawn in a plan's coordinates and saved moved by `shift` ([dx, dy, dz]): a family whose
 *  plan keeps another drawing's coordinates (the village contract's) but whose file must reach past them. Furniture
 *  spots are recorded in the file's coordinates. */
export class ShiftedCanvas extends Canvas {
  constructor(size, [dx, dy, dz]) { super(size); this.d = [dx, dy, dz] }
  set(x, y, z, id, props = {}) { super.set(x + this.d[0], y + this.d[1], z + this.d[2], typeof id === 'function' ? id(x, y, z) : id, props) }
  get(x, y, z) { return super.get(x + this.d[0], y + this.d[1], z + this.d[2]) }
  clear(x, y, z) { super.clear(x + this.d[0], y + this.d[1], z + this.d[2]) }
  cutAbove(y) { return super.cutAbove(y + this.d[1]) }
  spot(kind, pos, facing, rel, wood, colour) { super.spot(kind, [pos[0] + this.d[0], pos[1] + this.d[1], pos[2] + this.d[2]], facing, rel, wood, colour) }
}

/** stockShelf: an upside-down slab on a wall with a small thing on it (`f.on`: a candle or a lantern, never crockery
 *  outside a kitchen), recorded as a shelf. */
export function stockShelf(c, f) {
  const [x, y, z] = f.pos
  c.free([x, y, z], [x, y + 1, z])
  slab(c, x, y, z, `${f.wood}_slab`, 'top')
  if (f.on === 'lantern') c.set(x, y + 1, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
  else c.set(x, y + 1, z, f.on, f.on === 'candle' ? { candles: '3', lit: 'true', waterlogged: 'false' } : {})
  c.spot('shelf', f.pos, f.facing, [[0, 0, 0]], f.wood)
}

/** mantel: a fireplace's hood over a hearth fire in a wall (`fire`: its cell; `into`: the way the room lies): a full
 *  `stone` block over the fire's front, upside-down `stairs` either side of it as the mantel shelf, its backs to the
 *  wall, and the hood `stone` on up to `top`. */
export function mantel(c, [x, y, z], into, stone, stairs, top) {
  const [dx, dz] = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[into]
  const fx = x + dx, fz = z + dz
  const back = { north: 'south', south: 'north', west: 'east', east: 'west' }[into]
  for (let yy = y + 2; yy <= top; yy++) c.set(fx, yy, fz, stone)
  for (const s of [-1, 1]) {
    const sx = fx + (dz !== 0 ? s : 0), sz = fz + (dx !== 0 ? s : 0)
    stair(c, sx, y + 2, sz, stairs, back, 'top')
  }
}
