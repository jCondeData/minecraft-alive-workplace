// Statues: figures drawn from a side profile and the antlers, horns or wings that spread out of it (parts library,
// tools/blueprints/REBUILD.md; grown from the Stag Statue, 49). Every block comes from the caller's legend or palette.

/** profileFigure: draw a figure from its side profile. `rows` are strings from the top row (y = y0 + rows.length - 1)
 *  down to y0, one character per x from x0; '.' or ' ' is empty. Each row is swept over every z in `zs` (a figure's
 *  thickness). `legend[ch]` is an id, [id, props], or a function (x, y, z) returning either (a stair that turns on
 *  one side, a two-colored flank). Returns the cells drawn. */
export function profileFigure(c, { rows, x0 = 0, y0, zs }, legend) {
  const drawn = []
  rows.forEach((row, i) => {
    const y = y0 + rows.length - 1 - i
    ;[...row].forEach((ch, dx) => {
      if (ch === '.' || ch === ' ') return
      if (!(ch in legend)) throw new Error(`profileFigure: no legend for '${ch}'`)
      for (const z of zs) {
        const x = x0 + dx
        let v = legend[ch]
        if (typeof v === 'function') v = v(x, y, z)
        if (!v) continue
        const [id, props] = Array.isArray(v) ? v : [v, {}]
        c.set(x, y, z, id, props)
        drawn.push([x, y, z])
      }
    })
  })
  return drawn
}

/** legs: upright legs (a fence or wall id, thin) from y0 to y1 at each [x, z] of `feet`. A walking stance staggers the
 *  near and far pair (no two legs side by side, so the fences never join into a panel). */
export function legs(c, feet, y0, y1, id) {
  for (const [x, z] of feet) for (let y = y0; y <= y1; y++) c.set(x, y, z, id)
}

/** antlerPair: a mirrored pair of antlers (or horns). `beam` lists the left antler's cells as [dx, dy, dz] from the
 *  left burr (x, y, zL), dz <= 0 running outward; the right antler is the same with dz mirrored, its burr at
 *  zL + 1 + gap (gap 0: burrs side by side on a crown; gap 2: one each side of a head two cells wide). Tines are just
 *  cells one above a beam cell (a fence on a fence reads as a spike). `id` (a fence id, pale) for every cell. */
export function antlerPair(c, [x, y, zL], beam, id, gap = 0) {
  const zR = zL + 1 + gap
  for (const [dx, dy, dz] of beam) {
    c.set(x + dx, y + dy, zL + dz, id)
    c.set(x + dx, y + dy, zR - dz, id)
  }
}

/** brimHat: a broad-brimmed hat on a figure's head (grown from the Founder's Statue, 49). The crown sits on the head at
 *  [x, y, z]; the brim is a ring of closed bottom trapdoors round the crown at the same layer, on the four sides
 *  (and the corners too when `corners`), so the brim reads at the hat's foot from every side. `crown` and `brim` are
 *  the caller's ids (a block and a trapdoor). Returns the brim's cells. */
export function brimHat(c, [x, y, z], crown, brim, corners = false) {
  c.set(x, y, z, crown)
  const ring = [[1, 0, 'east'], [-1, 0, 'west'], [0, 1, 'south'], [0, -1, 'north']]
  if (corners) ring.push([1, 1, 'east'], [-1, 1, 'west'], [1, -1, 'east'], [-1, -1, 'west'])
  const drawn = []
  for (const [dx, dz, facing] of ring) {
    c.set(x + dx, y, z + dz, brim, { facing, half: 'bottom', open: 'false', powered: 'false', waterlogged: 'false' })
    drawn.push([x + dx, y, z + dz])
  }
  return drawn
}
