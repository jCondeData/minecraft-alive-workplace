// Library parts (the Lamplit Nave, ROADMAP 49 library): a stepped buttress, a wall of book stacks, the enchanting
// ring vanilla counts, a pyramid (tented) roof over a square tower, and a lancet window. Every block comes from the
// caller's palette.
import { stair } from './canvas.mjs'

/** buttress: a pier against a wall at (x, z) from y0 up to `top` (`stone`, may be a mix()), weathered at the top
 *  with a stair (`cap`) rising toward the wall it props (`toward`: the wall's side). */
export function buttress(c, x, z, y0, top, stone, cap, toward) {
  for (let y = y0; y <= top; y++) c.set(x, y, z, stone)
  stair(c, x, top + 1, z, cap, toward)
}

/** shelfWall: book stacks over a rectangle of cells ([x, z] list) from y0 to y1 (`shelf`: the bookshelf block). */
export function shelfWall(c, cells, y0, y1, shelf) {
  for (const [x, z] of cells) for (let y = y0; y <= y1; y++) c.set(x, y, z, shelf)
}

/** enchantRing: an enchanting table at (x, y, z) inside a ring of shelves two blocks out on its own level and the
 *  one above (vanilla's BOOKSHELF_OFFSETS), the 3 x 3 between left as air; `gap` lists ring cells ([dx, dz]) left
 *  open on both levels as the way in. Returns how many shelves vanilla will count. */
export function enchantRing(c, [x, y, z], shelf, table, gap = []) {
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) for (const dy of [0, 1]) c.clear(x + dx, y + dy, z + dz)
  let n = 0
  for (let dx = -2; dx <= 2; dx++) for (let dz = -2; dz <= 2; dz++) {
    if (Math.abs(dx) !== 2 && Math.abs(dz) !== 2) continue
    if (gap.some(([gx, gz]) => gx === dx && gz === dz)) { c.clear(x + dx, y, z + dz); c.clear(x + dx, y + 1, z + dz); continue }
    for (const dy of [0, 1]) { c.set(x + dx, y + dy, z + dz, shelf); n++ }
  }
  c.set(x, y, z, table)
  return n
}

/** pyramidRoof: a tented roof over x0..x1, z0..z1 (a square), one ring of stairs per course rising inward from y,
 *  a full `roof.full` block at the apex; returns the apex [x, y, z]. `keep(x,y,z)` as gableRoof's. */
export function boxPyramidRoof(c, { x0, z0, x1, z1, y }, roof, keep = () => true) {
  let k = 0
  for (; x0 + k < x1 - k && z0 + k < z1 - k; k++) {
    const a = x0 + k, b = x1 - k, f = z0 + k, g = z1 - k, yy = y + k
    const put = (x, z, facing, shape = 'straight') => {
      if (keep(x, yy, z)) c.set(x, yy, z, roof.stairs, { facing, half: 'bottom', shape, waterlogged: 'false' })
    }
    for (let x = a + 1; x < b; x++) { put(x, f, 'south'); put(x, g, 'north') }
    for (let z = f + 1; z < g; z++) { put(a, z, 'east'); put(b, z, 'west') }
    put(a, f, 'south', 'outer_right'); put(b, f, 'south', 'outer_left')
    put(a, g, 'north', 'outer_left'); put(b, g, 'north', 'outer_right')
  }
  const apex = [x0 + k, y + k, z0 + k]
  c.set(...apex, roof.full)
  return apex
}

/** lancet: a tall narrow window in a wall, panes from y0 to y1 at each [x, z] of `cells`, the head closed by an
 *  upside-down stair pair or a single `head` block over it. */
export function lancet(c, cells, y0, y1, glass, head = null) {
  for (const [x, z] of cells) {
    for (let y = y0; y <= y1; y++) c.set(x, y, z, glass)
    if (head) c.set(x, y1 + 1, z, head)
  }
}
