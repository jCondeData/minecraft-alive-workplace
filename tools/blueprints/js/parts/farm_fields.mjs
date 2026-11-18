// Farm fields: a field of farmland round its water, a kerb with a fence on it, a scarecrow, the Farmer's composter and
// brackets under a long eave (parts library, tools/blueprints/REBUILD.md; grown from the Farmstead, 49). Every block
// comes from the caller's palette; only the vanilla blocks the game reads as themselves are named here (farmland,
// water, the composter, the crops it is given).

/** fieldPlot: farmland over x0..x1, z0..z1 at y (moist), with water sources where `water(x, z)` and a solid footing
 *  (`footing(x, z)` returns its id, e.g. under a scarecrow) instead of either; a crop on each farmland cell where
 *  `crop(x, z)` returns [id, age]. Vanilla keeps farmland moist within 4 of water (x and z, same level or one up): it
 *  throws when a farmland cell has none. Returns the farmland cells [x, z]. */
export function fieldPlot(c, { x0, x1, z0, z1, y = 0 }, { water, footing = () => null, crop = () => null }) {
  const farm = [], wet = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const foot = footing(x, z)
    if (foot) { c.set(x, y, z, foot); continue }
    if (water(x, z)) { c.set(x, y, z, 'water', { level: '0' }); wet.push([x, z]); continue }
    c.set(x, y, z, 'farmland', { moisture: '7' })
    farm.push([x, z])
    const got = crop(x, z)
    if (got) c.set(x, y + 1, z, got[0], { age: String(got[1]) })
  }
  for (const [x, z] of farm)
    if (!wet.some(([wx, wz]) => Math.abs(wx - x) <= 4 && Math.abs(wz - z) <= 4)) throw new Error(`farmland ${x},${z} is out of water's reach`)
  return farm
}

/** kerbFence: a kerb (`kerb`, may be a mix()) at y along a path of corner points [[x, z], ...] (each leg straight) and
 *  a fence (`fence`) on it at y + 1, every corner included (owner rule 1: a post at each corner). */
export function kerbFence(c, points, y, kerb, fence) {
  for (let i = 0; i + 1 < points.length; i++) {
    const [ax, az] = points[i], [bx, bz] = points[i + 1]
    if (ax !== bx && az !== bz) throw new Error(`kerb leg ${points[i]} -> ${points[i + 1]} isn't straight`)
    for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++)
      for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++) { c.set(x, y, z, kerb); c.set(x, y + 1, z, fence) }
  }
}

/** scarecrow: a fence leg at pos, a body block (`body`, a hay bale) on it with fence arms out to either side across
 *  `facing`, its head (a carved pumpkin or jack o'lantern, facing `facing`) on top and a hat (`hat`, a carpet) on that.
 *  Stand it on a footing, never on farmland. */
export function scarecrow(c, [x, y, z], facing, { leg, body, arms, head = 'carved_pumpkin', hat = null }) {
  const across = facing === 'north' || facing === 'south' ? [1, 0] : [0, 1]
  c.set(x, y, z, leg)
  c.set(x, y + 1, z, body, body.endsWith('hay_block') ? { axis: 'y' } : {})
  for (const d of [-1, 1]) c.set(x + across[0] * d, y + 1, z + across[1] * d, arms)
  c.set(x, y + 2, z, head, { facing })
  if (hat) c.set(x, y + 3, z, hat)
}

/** composter: the Farmer's job block (vanilla's composter, empty) at pos. Keep a chest within 8 across and 4 up or
 *  down, the nearest one where the harvest should go, and the field's nearest farmland within 16. */
export function composter(c, { pos: [x, y, z] }) {
  c.free([x, y, z])
  c.set(x, y, z, 'composter', { level: '0' })
}

/** eaveBrackets: upside-down stairs under a long eave at y, one at each `at` along a wall line (`axis` 'z': cells
 *  (line, y, z) for z in at), each with its back to the wall (`facing`: the way the wall lies). Under a gallery's
 *  eave they stay above head height (y 3 over a deck at 0). */
export function eaveBrackets(c, axis, line, at, y, id, facing) {
  for (const u of at) {
    const [x, z] = axis === 'z' ? [line, u] : [u, line]
    if (!c.get(x, y, z)) c.set(x, y, z, id, { facing, half: 'top', shape: 'straight', waterlogged: 'false' })
  }
}
