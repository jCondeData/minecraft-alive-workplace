// Water: a round curb with a still pool in it, the way a well, a fountain or a cistern holds its water (parts
// library, tools/blueprints/REBUILD.md). Every water cell is a source the builder pours from a bucket, and every one
// is shut in on its four sides and below by the drawing, so nothing flows out (a fluid never leaves the build).

/** roundCurb: a ring of `stone` round a centre (cx, cz) of radius r (a (2r+1)-square with its corners clipped) at y,
 *  leaving the pool cells (`pool`, absolute [x, z]) open. Returns the ring's cells [x, z]. */
export function roundCurb(c, cx, cz, r, y, stone, pool) {
  const open = new Set(pool.map(([x, z]) => `${x},${z}`))
  const ring = []
  for (let x = cx - r; x <= cx + r; x++) for (let z = cz - r; z <= cz + r; z++) {
    if (Math.abs(x - cx) === r && Math.abs(z - cz) === r) continue // the clipped corners: the ring reads round
    if (open.has(`${x},${z}`)) continue
    c.set(x, y, z, stone); ring.push([x, z])
  }
  return ring
}

/** pool: still water (sources) in the given cells at y; throws if any side or the floor under one is left open. */
export function pool(c, cells, y) {
  const inPool = new Set(cells.map(([x, z]) => `${x},${z}`))
  for (const [x, z] of cells) c.set(x, y, z, 'water', { level: '0' })
  for (const [x, z] of cells) {
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]])
      if (!inPool.has(`${x + dx},${z + dz}`) && !c.get(x + dx, y, z + dz)) throw new Error(`water at ${x},${y},${z} runs out ${dx},${dz}`)
    if (!c.get(x, y - 1, z)) throw new Error(`water at ${x},${y},${z} has no floor`)
  }
}
