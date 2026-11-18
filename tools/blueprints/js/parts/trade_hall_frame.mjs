// Proud frames and banded towers (parts library, grown from the Trade Hall's round 3, ROADMAP 49): a timber frame
// standing one block out from its panels, a corbelled band round a chamfered shaft, and the solid fill that seats a
// cross-gable's ridge on the slope it runs out of. Every block comes from the caller's palette.
import { stair } from './canvas.mjs'

/** proudFrame: posts and a beam on the plane one block out from a wall line. side: 'north' | 'south' (a line of
 *  constant z, posts at each x of `at`) or 'west' | 'east' (constant x, posts at each z). `line` is the proud plane,
 *  [a0, a1] the beam's run along it, posts from y0 to y1, the beam at beamY (corners upright). keep(x, z) false
 *  skips a cell (another mass owns it). */
export function proudFrame(c, side, line, [a0, a1], at, y0, y1, beamY, log, keep = () => true) {
  const ns = side === 'north' || side === 'south'
  const xz = u => (ns ? [u, line] : [line, u])
  for (const u of at) {
    const [x, z] = xz(u)
    if (keep(x, z)) for (let y = y0; y <= beamY; y++) c.set(x, y, z, log, { axis: 'y' })
  }
  for (let u = a0; u <= a1; u++) {
    const [x, z] = xz(u)
    if (!keep(x, z) || at.includes(u)) continue
    c.set(x, beamY, z, log, { axis: ns ? 'x' : 'z' })
  }
}

/** shaftBand: a corbelled band round a chamfered shaft (x0..x1, z0..z1 with its corners cut): the shaft's ring at y in
 *  `band` (corners included, so the band reads round), and upside-down `stairs` one out from each straight face,
 *  their backs to the shaft. skip(x, z) leaves a cell (where a roof runs into the shaft). */
export function shaftBand(c, x0, z0, x1, z1, y, band, stairs, skip = () => false) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++)
    if (x === x0 || x === x1 || z === z0 || z === z1) c.set(x, y, z, band)
  const put = (x, z, facing) => { if (!skip(x, z) && !c.get(x, y, z)) stair(c, x, y, z, stairs, facing, 'top') }
  for (let x = x0 + 1; x < x1; x++) { put(x, z0 - 1, 'south'); put(x, z1 + 1, 'north') }
  for (let z = z0 + 1; z < z1; z++) { put(x0 - 1, z, 'east'); put(x1 + 1, z, 'west') }
}

/** footSkirt: a battered stair skirt round a chamfered shaft's foot at y: one out from each straight face and round
 *  each cut corner, high side to the shaft. Cells already set are left (a wall or plinth owns them). */
export function footSkirt(c, x0, z0, x1, z1, y, stairs) {
  const put = (x, z, facing, shape = 'straight') => {
    if (c.inside(x, y, z) && !c.get(x, y, z)) c.set(x, y, z, stairs, { facing, half: 'bottom', shape, waterlogged: 'false' })
  }
  for (let x = x0 + 1; x < x1; x++) { put(x, z0 - 1, 'south'); put(x, z1 + 1, 'north') }
  for (let z = z0 + 1; z < z1; z++) { put(x0 - 1, z, 'east'); put(x1 + 1, z, 'west') }
  put(x0, z0, 'south', 'outer_right'); put(x1, z0, 'south', 'outer_left')
  put(x0, z1, 'north', 'outer_left'); put(x1, z1, 'north', 'outer_right')
}

/** seatCrossGable: fills solid (`full`) every empty cell between a main slope's surface (mainY(z), its stair's y in
 *  that row) and a cross-gable's underside (crossY(x), its stair's y in that column) over the rows z0..z1 and columns
 *  x0..x1, so the cross ridge sits on the slope with no pocket wedged under it. */
export function seatCrossGable(c, x0, x1, z0, z1, mainY, crossY, full) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++)
    for (let y = mainY(z) + 1; y < crossY(x); y++) if (!c.get(x, y, z)) c.set(x, y, z, full)
}
