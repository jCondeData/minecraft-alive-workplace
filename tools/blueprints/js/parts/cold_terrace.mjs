// Cold terraces: the pieces the snowy and taiga looks of the Terrace are drawn with (ROADMAP 49.10c, as 49.10a). Every
// block comes from the palette passed in; nothing here is hard-coded.
//   rakeVerge    a steep gable's overhanging rake row made one clean edge at the pitch (no single blocks stepping)
//   evenRoof45   a 45-degree roof over an even span, its ridge two stairs back to back (a long range along a row)
//   ridgeStack   a fieldstone stack in a wall's plane, through the ridge, smoking on one column
//   skinWall     a second, outer skin before a wall (a thick ground storey): deep reveals for its doors and windows
//   logEnds      log ends poking out past a log wall's corner on alternate courses (a saddle-notched pen)
import { stair, slab } from './canvas.mjs'

const ns = id => (id.includes(':') ? id : `minecraft:${id}`)

/** rakeVerge: on a gable's overhanging rake row (the plane `line`: z = line for axis 'z', x = line for axis 'x'), every
 *  `roof.full` that carries a bottom `roof.stairs` becomes an upside-down stair facing the eave the stair falls to, so
 *  the rake reads as one continuous edge at a steep roof's pitch. `span` [a0, a1]: the columns along the row. */
export function rakeVerge(c, roof, axis, line, [a0, a1]) {
  const full = ns(roof.full), stairs = ns(roof.stairs)
  const flip = { east: 'west', west: 'east', north: 'south', south: 'north' }
  for (let u = a0; u <= a1; u++) for (let y = 0; y < c.size[1] - 1; y++) {
    const [x, z] = axis === 'z' ? [u, line] : [line, u]
    const above = c.get(x, y + 1, z)
    if (c.name(x, y, z) !== full || above?.id !== stairs || above.props.half !== 'bottom') continue
    stair(c, x, y, z, roof.stairs, flip[above.props.facing], 'top')
  }
}

/** evenRoof45: a 45-degree roof over an even span `across` [a0, a1] (the overhang columns included: a0 and a1 are the
 *  eaves at eaveY), running `along` [l0, l1] (the rakes at both ends), each column in one block and one up from the
 *  last, the two middle columns two stairs back to back (the ridge, eaveY + n - 1). `keep(x, y, z)` false leaves a cell
 *  (a stack). Returns {ridgeY, under}: under[u] the roof's y over column u. */
export function evenRoof45(c, { axis, across: [a0, a1], along: [l0, l1], eaveY }, roof, keep = () => true) {
  const n = (a1 - a0 + 1) / 2
  if (!Number.isInteger(n)) throw new Error(`evenRoof45: span ${a0}..${a1} is odd`)
  const under = {}
  for (let i = 0; i < n; i++) {
    for (const [u, rise] of [[a0 + i, axis === 'z' ? 'east' : 'south'], [a1 - i, axis === 'z' ? 'west' : 'north']]) {
      under[u] = eaveY + i
      for (let v = l0; v <= l1; v++) {
        const [x, z] = axis === 'z' ? [u, v] : [v, u]
        if (keep(x, eaveY + i, z)) stair(c, x, eaveY + i, z, roof.stairs, rise)
      }
    }
  }
  return { ridgeY: eaveY + n - 1, under }
}

/** ridgeStack: a stack of `stone` (may be a mix()) in a wall's plane: the cells [x, z] of `cells`, from y0 to y1
 *  (overwriting the roof it passes through), a lit campfire on the first cell at y1 + 1 and a `cap` slab on the rest. */
export function ridgeStack(c, cells, y0, y1, stone, { cap = null } = {}) {
  cells.forEach(([x, z], i) => {
    for (let y = y0; y <= y1; y++) c.set(x, y, z, stone)
    if (i === 0) c.set(x, y1 + 1, z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
    else if (cap) slab(c, x, y1 + 1, z, cap, 'bottom')
  })
}

/** skinWall: an outer skin of `stone` (may be a mix()) on the row z = `z` from x0 to x1, y 1..top, before a wall one
 *  block behind it, capped at top + 1 by `cap` stairs rising toward the wall (`toward`, a weathering that sheds the
 *  snow), with deep reveals: `doors` [x] open y 1..doorTop under a `lintel` log, `windows` [[x0, x1, y0, y1]] open under a
 *  lintel, and `snow` (layers) on each window reveal's sill when given. */
export function skinWall(c, { x0, x1, z, top, toward = 'south', doorTop = 3 }, { stone, cap, lintel, snow = null }, { doors = [], windows = [] } = {}) {
  for (let x = x0; x <= x1; x++) {
    for (let y = 1; y <= top; y++) c.set(x, y, z, stone)
    stair(c, x, top + 1, z, cap, toward)
  }
  for (const dx of doors) {
    for (let y = 1; y <= doorTop; y++) c.clear(dx, y, z)
    c.set(dx, doorTop + 1, z, lintel, lintel.includes('log') ? { axis: 'x' } : {})
  }
  for (const [wa, wb, ya, yb] of windows) for (let x = wa; x <= wb; x++) {
    for (let y = ya; y <= yb; y++) c.clear(x, y, z)
    c.set(x, yb + 1, z, lintel, lintel.includes('log') ? { axis: 'x' } : {})
    if (snow) c.set(x, ya, z, snow, { layers: '1' })
  }
}

/** logEnds: a `log` end poking out past a corner on every other course from y0 to y1 (`odd`: start a course later):
 *  each [x, z, axis] in `at` is the cell outside the corner and the axis the end's log lies along. */
export function logEnds(c, at, y0, y1, log, odd = false) {
  for (let y = y0 + (odd ? 1 : 0); y <= y1; y += 2) for (const [x, z, axis] of at) c.set(x, y, z, log, { axis })
}
