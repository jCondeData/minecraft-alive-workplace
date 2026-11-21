// Market awnings (ROADMAP 49, the Market Corner): a striped canvas that steps down from a back corner over an L of
// counters, mitred into a chevron where its two arms meet, with a scalloped valance of wall banners along its edge.
// Every block comes from the caller's palette ({ stripes: [wool, wool], valance: [banner, banner] }).

/** cornerDepth: how far cell (x, z) stands in from the nearer of the two back edges (x = bx, z = bz). */
export const cornerDepth = (x, z, bx, bz) => Math.min(Math.abs(bx - x), Math.abs(bz - z))

/** lAwning: the canvas over an L whose back edges are x = bx and z = bz (the corner at (bx, bz)), spanning x from x0
 *  to bx and z from z0 to bz, `reach` rows deep, its back row at y = top, each row in front one block lower. Stripes
 *  run across each arm (by x on the arm along the z edge, by z on the arm along the x edge), so they meet at the hip
 *  in a chevron. `rows` (optional) gives each row's y from the back row out, in place of `top` and `reach` (a canvas
 *  that runs level for a row or two, then steps). `skip(x, y, z)` keeps a cell for something else (a mast). Returns the canvas cells by (x, z): their y. */
export function lAwning(c, { bx, bz, x0, z0, top, reach = 3, rows = null, skip = () => false }, { stripes }) {
  if (rows) reach = rows.length
  const height = new Map()
  const sx = Math.sign(x0 - bx) || -1, sz = Math.sign(z0 - bz) || -1
  for (let x = x0; x !== bx - sx; x -= sx) for (let z = z0; z !== bz - sz; z -= sz) {
    const d = cornerDepth(x, z, bx, bz)
    if (d >= reach) continue
    const y = rows ? rows[d] : top - d
    height.set(`${x},${z}`, y)
    if (skip(x, y, z)) continue
    const onBackArm = Math.abs(bz - z) <= Math.abs(bx - x) // the arm along the z = bz edge
    const u = onBackArm ? x : z
    c.set(x, y, z, stripes[((u % stripes.length) + stripes.length) % stripes.length])
  }
  return height
}

/** valance: wall banners hung on the awning's front edge, one per cell along each arm, alternating colours; the
 *  inside corner cell is left for a lantern. `edge` = the awning's front row depth (reach - 1). */
export function valance(c, { bx, bz, x0, z0, top, reach = 3, rows = null }, { valance }) {
  const sx = Math.sign(x0 - bx) || -1, sz = Math.sign(z0 - bz) || -1
  if (rows) reach = rows.length
  const y = rows ? rows[reach - 1] : top - (reach - 1)
  const rowZ = bz + sz * reach, rowX = bx + sx * reach // the cells just in front of the edge
  const face = (dz, dx) => (dz ? (dz > 0 ? 'south' : 'north') : (dx > 0 ? 'east' : 'west'))
  let i = 0
  // arm along the back z edge: banners on line z = rowZ, facing away from the canvas (towards the court)
  for (let x = x0; x !== rowX; x -= sx) c.set(x, y, rowZ, valance[i++ % valance.length], { facing: face(sz, 0) })
  i = 1
  for (let z = z0; z !== rowZ; z -= sz) c.set(rowX, y, z, valance[i++ % valance.length], { facing: face(0, sx) })
}
