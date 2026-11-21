// Hearths: the chimney stack with its breast and smoke, and the hearth fire (parts library, REBUILD.md).
import { stair } from './canvas.mjs'

/** chimney: a stone stack from y0 to y1 with a lit campfire on top for smoke, and a stepped breast at its foot
 *  (`breast`: {axis, top, stair}). */
export function chimney(c, x, z, y0, y1, stone, { breast = null, smoke = true } = {}) {
  for (let y = y0; y <= y1; y++) c.set(x, y, z, stone)
  if (smoke) c.set(x, y1 + 1, z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  if (breast) {
    const { axis, top, stair: id = 'cobblestone_stairs' } = breast
    for (const d of [-1, 1]) {
      const [bx, bz] = axis === 'x' ? [x + d, z] : [x, z + d]
      for (let y = y0; y < top; y++) c.set(bx, y, bz, stone)
      const f = axis === 'x' ? (d < 0 ? 'east' : 'west') : (d < 0 ? 'south' : 'north')
      stair(c, bx, top, bz, id, f)
    }
  }
}

/** hearthFire: the fire in the hearth (a lit campfire facing into the room). */
export function hearthFire(c, [x, y, z], facing) {
  c.set(x, y, z, 'campfire', { facing, lit: 'true', signal_fire: 'false', waterlogged: 'false' })
}
