// Planters and garden timberwork (parts library, tools/blueprints/REBUILD.md; grown from the Flower Planters, 49).
// Every block comes from the caller: soil, stone, wood and plants are passed in, never picked here.
import { stair, trapdoor } from './canvas.mjs'

const TALL = new Set(['lilac', 'peony', 'rose_bush', 'sunflower', 'tall_grass', 'large_fern', 'pitcher_plant'])

/** plantFlower: one plant on the soil under it; a two-block flower (lilac, peony, rose bush...) gets its upper half too. */
export function plantFlower(c, x, y, z, id) {
  if (!id) return
  if (TALL.has(id.replace('minecraft:', ''))) {
    c.set(x, y, z, id, { half: 'lower' })
    c.set(x, y + 1, z, id, { half: 'upper' })
  } else c.set(x, y, z, id)
}

/** tieredBed: a bed whose soil sits at y = tier - 1 on `base` (y 0 .. tier - 2), planted from plants(x, z) (an id,
 *  or null for bare soil). cells: [[x, z]]. soil and base: an id or a function of (x, y, z) (a mix()). tier 1 is soil
 *  on the ground layer itself. Returns the cells' plant heights. */
export function tieredBed(c, cells, tier, { base, soil, plants }) {
  for (const [x, z] of cells) {
    for (let y = 0; y < tier - 1; y++) c.set(x, y, z, base)
    const s = typeof soil === 'function' ? soil(x, tier - 1, z) : soil
    c.set(x, tier - 1, z, s, s.endsWith('grass_block') || s.endsWith('podzol') ? { snowy: 'false' } : {})
    plantFlower(c, x, tier, z, plants(x, z))
  }
}

/** boxBoards: open trapdoors standing against a bed's open face, as the boards of a planter box. cells: [[x, y, z]]
 *  in front of the soil; out: the side they look out to (the board hugs the soil behind it). */
export function boxBoards(c, cells, wood, out) {
  for (const [x, y, z] of cells) trapdoor(c, x, y, z, `${wood}_trapdoor`, out, 'bottom', true)
}

/** beamRun: a beam of logs along `axis` ('x' or 'z') at height y, from a to b on the line at `at` (the other
 *  coordinate), its two ends cut back as upside-down stairs so it tapers out past its posts. ends: false keeps logs. */
export function beamRun(c, axis, at, a, b, y, log, stairs, ends = true) {
  for (let i = a; i <= b; i++) {
    const [x, z] = axis === 'x' ? [i, at] : [at, i]
    const end = ends && (i === a || i === b)
    if (!end) { c.set(x, y, z, log, { axis }); continue }
    const facing = axis === 'x' ? (i === a ? 'east' : 'west') : (i === a ? 'south' : 'north')
    stair(c, x, y, z, stairs, facing, 'top')
  }
}

/** kneeBrace: an upside-down stair tucked under a beam beside a post, its full side against the post (toward). */
export function kneeBrace(c, x, y, z, stairs, toward) {
  stair(c, x, y, z, stairs, toward, 'top')
}

/** hangingLantern: a lantern hung under the block at y + 1 (+ chain links above it, `chain` long). */
export function hangingLantern(c, x, y, z, lantern = 'lantern', chain = 0) {
  for (let i = 1; i <= chain; i++) c.set(x, y + i, z, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(x, y, z, lantern, { hanging: 'true', waterlogged: 'false' })
}
