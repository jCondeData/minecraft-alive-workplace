// Parts for a cruciform Gothic minster (the Great Cathedral, version B, ROADMAP 49 / 52.6): roofs drawn from one
// height field (so a nave, its transepts, lean-to aisles, chapels and a round apse meet as the game would lay them, the
// higher roof winning at every column), a vault drawn the same way from below, a flying buttress, an octagonal
// needle spire with corner pinnacles, a rose window, and a lancet. Every block comes from the palette passed in.
import { stair } from './canvas.mjs'

/** put: lay a block only where nothing is yet (a later pass resting on an earlier one, in any stage). */
export const put = (c, x, y, z, id, props = {}) => { if (c.inside(x, y, z) && !c.get(x, y, z)) c.set(x, y, z, id, props) }

const NEIGH = [['east', 1, 0], ['west', -1, 0], ['south', 0, 1], ['north', 0, -1]]

/** roofField: roofs as height functions over plan cells. Each roof is {cells(x, z) -> bool, h(x, z) -> top y}; the
 *  field's top at a column is the highest roof covering it. Returns {top(x, z) (or null), covered(x, z)}. */
export function roofField(roofs) {
  const top = (x, z) => {
    let best = null
    for (const r of roofs) if (r.cells(x, z)) { const h = Math.round(r.h(x, z)); if (best === null || h > best) best = h }
    return best
  }
  return { top, covered: (x, z) => top(x, z) !== null }
}

/** drawRoof: lays the field's columns that `mine(x, z)` picks: a stair at the top facing up the slope (toward its
 *  highest neighbour), full blocks under it down to the lowest neighbour's top (no gap shows between steps), and a
 *  ridge (a column with no higher neighbour) as a full block capped with a slab. Uses put(): never over a wall. */
export function drawRoof(c, field, [x0, x1, z0, z1], mine, { full, stairs, slab }) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const t = field.top(x, z)
    if (t === null || !mine(x, z)) continue
    let up = null, upH = t, low = t - 1
    for (const [dir, dx, dz] of NEIGH) {
      const n = field.top(x + dx, z + dz)
      if (n === null) continue
      if (n > upH) { upH = n; up = dir }
      low = Math.min(low, n)
    }
    for (const [dx, dz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) { // a diagonal step (a cone, a valley) too: no peek
      const n = field.top(x + dx, z + dz)
      if (n !== null) low = Math.min(low, n)
    }
    if (up) stair(c, x, t, z, stairs, up)
    else { c.set(x, t, z, full); put(c, x, t + 1, z, slab, { type: 'bottom', waterlogged: 'false' }) }
    for (let y = t - 1; y > low; y--) put(c, x, y, z, full)
  }
}

/** drawVault: the ceiling from below. `v(x, z)` gives each vessel cell's ceiling height (null outside); a cell lower
 *  than a neighbour is an upside-down stair stepping down toward the wall, the crown full blocks (`rib(x, z)` true:
 *  the rib block). Uses put(). */
export function drawVault(c, v, [x0, x1, z0, z1], mine, { full, stairs, rib }, isRib = () => false) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const y = v(x, z)
    if (y === null || !mine(x, z)) continue
    let down = null, downH = y
    for (const [dir, dx, dz] of NEIGH) {
      const n = v(x + dx, z + dz)
      if (n !== null && n < downH) { downH = n; down = dir }
    }
    if (isRib(x, z)) put(c, x, y, z, rib)
    else if (down) put(c, x, y, z, stairs, { facing: down, half: 'top', shape: 'straight', waterlogged: 'false' })
    else put(c, x, y, z, full)
  }
}

/** flyingButtress: a pier standing off a wall and an arch flying from its top to the clerestory. `dir` +1 flies toward
 *  +x, -1 toward -x. The pier is two thick from y 1 to `step` (its foot), one thick to `top`, capped by a pinnacle
 *  (`pin`: a wall post, then `cap`). The flyer climbs one block a column from (x+dir, top-1) to the wall at `wallX`,
 *  a stair on top (rising toward the wall) over an upside-down stair (its soffit). */
export function flyingButtress(c, { x, z, dir, step, top, wallX }, { pier, stairs, pin, cap }) {
  for (let y = 1; y <= top; y++) { put(c, x, y, z, pier); if (y <= step) put(c, x + dir, y, z, pier) }
  put(c, x + dir, step + 1, z, stairs, { facing: dir > 0 ? 'east' : 'west', half: 'bottom', shape: 'straight', waterlogged: 'false' })
  put(c, x, top + 1, z, pin, {}); put(c, x, top + 2, z, pin, {}); put(c, x, top + 3, z, cap, {})
  const toward = dir > 0 ? 'east' : 'west', away = dir > 0 ? 'west' : 'east'
  for (let i = 1, y = top - 1; x + i * dir !== wallX; i++, y++) {
    const fx = x + i * dir
    put(c, fx, y, z, stairs, { facing: toward, half: 'bottom', shape: 'straight', waterlogged: 'false' })
    put(c, fx, y - 1, z, stairs, { facing: away, half: 'top', shape: 'straight', waterlogged: 'false' })
  }
}

/** octagon metric: how far (x, z) is from (cx, cz) on an octagon of "radius" r (flat faces, cut corners). */
export const oct = (dx, dz) => Math.max(Math.abs(dx), Math.abs(dz), (Math.abs(dx) + Math.abs(dz)) * 0.72)

/** needleSpire: an octagonal shell from y0 (radius r0) tapering to one block at y1, `finial` on top at y1 + 1, lucarne
 *  slits (`slit`) in the four main faces at `lucarneY`. */
export function needleSpire(c, { cx, cz, y0, y1, r0, lucarneY }, { tile, finial, slit }) {
  for (let y = y0; y <= y1; y++) {
    const r = r0 * (y1 - y) / (y1 - y0)
    const R = Math.ceil(r0) + 1
    for (let dx = -R; dx <= R; dx++) for (let dz = -R; dz <= R; dz++) {
      const m = oct(dx, dz)
      if (m > r + 0.01 || m <= r - 1.0) continue
      const face = (dx === 0 || dz === 0) && y >= lucarneY && y <= lucarneY + 1 && Math.round(m) === Math.round(r)
      c.set(cx + dx, y, cz + dz, face ? slit : tile, face ? {} : {})
    }
    if (r < 0.5) c.set(cx, y, cz, tile)
  }
  c.set(cx, y1 + 1, cz, finial, { facing: 'up', powered: 'false', waterlogged: 'false' })
}

/** roseWindow: a disc of glass blocks in the plane z at (cx, cy), radius r, framed by a ring of `frame`: `pick(dx, dy,
 *  d)` gives each glass cell's block (rings, spokes, the eye). */
export function roseWindow(c, { cx, cy, z, r }, pick, frame) {
  for (let dx = -r - 1; dx <= r + 1; dx++) for (let dy = -r - 1; dy <= r + 1; dy++) {
    const d = Math.hypot(dx, dy)
    if (d <= r) c.set(cx + dx, cy + dy, z, pick(dx, dy, d))
    else if (d <= r + 1) c.set(cx + dx, cy + dy, z, frame)
  }
}

/** lancet: a column of panes in a wall from y0 to y1 at (x, z) (the wall's cell), the pane chosen by `pick(y)`. */
export function lancet(c, x, z, y0, y1, pick) {
  for (let y = y0; y <= y1; y++) c.set(x, y, z, pick(y), { waterlogged: 'false' })
}
