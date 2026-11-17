// Walls: panels, windows with their dressing, and doors (parts library, tools/blueprints/REBUILD.md).
import { DIRS, OPP, stair, trapdoor } from './canvas.mjs'

/** wallPanel: a straight wall from a to b (same x or same z) between y0 and y1. `id` may be a mix(). */
export function wallPanel(c, [ax, az], [bx, bz], y0, y1, id) {
  for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++)
    for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++)
      for (let y = y0; y <= y1; y++) c.set(x, y, z, id)
}

/** window: glass panes in the given cells; dressing 'sill' (an upside-down stair one block out under the lowest
 *  pane, with a potted flower on it), or 'shutters' (open trapdoors flat on the wall either side, one block out). */
export function window(c, cells, out, dress, { glass = 'glass_pane', wood = 'spruce', pots = [], sill = null } = {}) {
  for (const [x, y, z] of cells) c.set(x, y, z, glass)
  if (!dress) return
  const [ox, oz] = DIRS[out]
  const low = Math.min(...cells.map(p => p[1]))
  const high = Math.max(...cells.map(p => p[1]))
  const bottoms = cells.filter(p => p[1] === low)
  if (dress === 'sill') {
    bottoms.forEach(([x, y, z], i) => {
      stair(c, x + ox, y - 1, z + oz, sill ?? `${wood}_stairs`, OPP[out], 'top')
      if (pots[i]) c.set(x + ox, y, z + oz, pots[i])
    })
  } else if (dress === 'shutters') {
    const along = out === 'north' || out === 'south' ? [1, 0] : [0, 1]
    const xs = bottoms.map(p => p[0] * along[0] + p[2] * along[1])
    const lo = Math.min(...xs) - 1, hi = Math.max(...xs) + 1
    const [bx, , bz] = bottoms[0]
    for (const t of [lo, hi]) for (let y = low; y <= high; y++) {
      const x = along[0] ? t : bx, z = along[1] ? t : bz
      trapdoor(c, x + ox, y, z + oz, `${wood}_trapdoor`, out, 'bottom', true)
    }
  }
}

/** door: a two-high door, with a stair step (`stepStair`) at `step` one block out when given. */
export function door(c, [x, y, z], facing, out, { wood = 'spruce', step = null, stepStair = 'cobblestone_stairs', hinge = 'left' } = {}) {
  for (const half of ['lower', 'upper'])
    c.set(x, y + (half === 'upper' ? 1 : 0), z, `${wood}_door`, { facing, half, hinge, open: 'false', powered: 'false' })
  if (step) stair(c, step[0], step[1], step[2], stepStair, OPP[out])
}

/** doorway: an open doorway `height` high (clears what the walls drew). */
export function doorway(c, [x, y, z], height = 2) { for (let yy = y; yy < y + height; yy++) c.clear(x, yy, z) }
