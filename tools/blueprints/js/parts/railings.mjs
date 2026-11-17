// Railings: fence runs along a stairwell, a porch or a balcony (parts library). check.py's owner rule 1 refuses a run
// that turns a corner without its post, so railingRun puts a post at every corner of its path itself.

/** railing: fence posts in the given cells (kit.finish joins them). */
export function railing(c, cells, wood) { for (const [x, y, z] of cells) c.set(x, y, z, `${wood}_fence`) }

/** railingRun: a fence along a path of corner points [[x, y, z], ...] (each leg straight along x or z), every cell
 *  between them included, so each corner has its post. `id` defaults to the wood's fence (a wall or pane id works). */
export function railingRun(c, points, wood, id = `${wood}_fence`) {
  for (let i = 0; i + 1 < points.length; i++) {
    const [ax, ay, az] = points[i], [bx, , bz] = points[i + 1]
    if (ax !== bx && az !== bz) throw new Error(`railing leg ${points[i]} -> ${points[i + 1]} isn't straight`)
    for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++)
      for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++) c.set(x, ay, z, id)
  }
}
