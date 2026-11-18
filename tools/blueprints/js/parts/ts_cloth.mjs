// Cloth parts (parts library, tools/blueprints/REBUILD.md; grown from the Tailor's Shop, ROADMAP 49): bolts of cloth on
// a stand or a rack, a dress form, and a drying bay of hung cloths in a tower's wall. The cloth is wool blocks (the
// game's #minecraft:wool, which the luxury workshops' tests count); every other block comes from the family's palette.
import { DIRS, slab, stair, trapdoor } from './canvas.mjs'

/** clothBolts: wool bolts on show, each [x, y, z, colour], on dark stands (`stand` cells, a `standId` block each). */
export function clothBolts(c, f, standId) {
  for (const [x, y, z] of f.stand ?? []) { c.free([x, y, z]); c.set(x, y, z, standId) }
  for (const [x, y, z, colour] of f.cells) { c.free([x, y, z]); c.set(x, y, z, `${colour}_wool`) }
}

/** boltRack: a rack of bolts against a wall along x (x0..x1 on line z) two high from y, `colours[i]` the [lower, upper]
 *  pair of column i, under a cap of `cap` slabs (a shelf top). */
export function boltRack(c, { x0, x1, z, y = 1, colours, cap }) {
  for (let x = x0; x <= x1; x++) {
    const [lo, hi] = colours[x - x0]
    c.free([x, y, z], [x, y + 1, z], [x, y + 2, z])
    c.set(x, y, z, `${lo}_wool`)
    c.set(x, y + 1, z, `${hi}_wool`)
    slab(c, x, y + 2, z, cap, 'bottom')
  }
}

/** dressForm: a fitting dummy: a fence post with a cloth bodice (a wool block) on it. A fixture, not a furniture spot. */
export function dressForm(c, { pos: [x, y, z], colour, wood }) {
  c.free([x, y, z], [x, y + 1, z])
  c.set(x, y, z, `${wood}_fence`)
  c.set(x, y + 1, z, `${colour}_wool`)
}

/** clothBay: a drying bay in a wall plane (axis 'x': the cells x of `at` on line z = `line`; 'z': z of `at` on line x),
 *  each column a cloth (`colours[i]`, wool) hung `drop` high from under the beam at `top`, over a louvre slat (an open
 *  `slat` trapdoor facing `out`) that fills the bay below it, so the bay is closed to the eye and reads as cloth hung in
 *  an open frame. */
export function clothBay(c, { axis, line, at, top, drop = 2, slatRows = 1, out, colours, slat }) {
  at.forEach((u, i) => {
    const [x, z] = axis === 'x' ? [u, line] : [line, u]
    for (let k = 1; k <= drop; k++) c.set(x, top - k, z, `${colours[i]}_wool`)
    for (let k = 1; k <= slatRows; k++) trapdoor(c, x, top - drop - k, z, slat, out, 'bottom', true)
  })
}

/** postBanner: a cloth sign hung on the face of a post (the cell in front of it, facing `out`). */
export function postBanner(c, [x, y, z], out, colour) {
  const [dx, dz] = DIRS[out]
  c.set(x + dx, y, z + dz, `${colour}_wall_banner`, { facing: out })
}

/** steepCap: a steep, hollow four-sided cap on a square tower centred on (cx, cz) whose walls are 2 * half + 1 wide:
 *  its eave ring of `roof` stairs sits on the wall line at y0 (no overhang, so it never hangs over a lower roof beside
 *  the tower), and each ring in rises two (a full block under a stair, the steep pitch), to a full block at the peak
 *  (y0 + 2 * half - 1); hollow inside, a lantern can hang from the peak. `apex`: [id, props] on top. Returns the peak's y. */
export function steepCap(c, cx, cz, half, y0, roof, { apex = null } = {}) {
  for (let s = half; s >= 1; s--) {
    const i = half - s, y = y0 + 2 * i
    for (let dx = -s; dx <= s; dx++) for (let dz = -s; dz <= s; dz++) {
      if (Math.max(Math.abs(dx), Math.abs(dz)) !== s) continue
      const x = cx + dx, z = cz + dz
      const facing = Math.abs(dz) >= Math.abs(dx) ? (dz < 0 ? 'south' : 'north') : (dx < 0 ? 'east' : 'west')
      if (i > 0) c.set(x, y - 1, z, roof.full)
      stair(c, x, y, z, roof.stairs, facing)
    }
  }
  const peak = y0 + 2 * half - 1
  c.set(cx, peak, cz, roof.full)
  if (apex) c.set(cx, peak + 1, cz, apex[0], apex[1] ?? {})
  return peak
}
