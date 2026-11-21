import { stair } from './canvas.mjs'
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

/** wellShaft: a one-block well shaft at (x, z): a water source at y0 (resting on the ground under the build when y0 is
 *  0), lined on four sides by `lining` from y0 up to mouthY - 1 and open (air) above the water up to mouthY - 1, so a
 *  bucket on a chain drops straight to it. The kerb round the mouth (at mouthY) is the family's own. Grown from the
 *  Well House (49). */
export function wellShaft(c, x, z, y0, mouthY, lining) {
  for (let y = y0; y < mouthY; y++) {
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) c.set(x + dx, y, z + dz, lining)
    if (y > y0) c.clear(x, y, z)
  }
  c.set(x, y0, z, 'water', { level: '0' })
}

/** aqueduct: a stone channel carried on arches along x from xa to xb (xb the far end, which closes the channel), its
 *  water line at z = cz and y (sources from `waterFrom` to xb - 1, through any cells already open on the way in), its
 *  bed at y - 1 standing on piers every `bay` blocks (a pier at xb too) with corbelled arches between (upside-down
 *  stairs of `arch` against each pier), its side walls at y in `coping`. `stone` may be a mix(). Grown from the Well
 *  House (49). Returns the water cells [x, z]. */
export function aqueduct(c, { xa, xb, cz, y, bay = 3, waterFrom = xa }, { stone, coping, arch }) {
  const dir = Math.sign(xb - xa) || 1
  const pierAt = x => x === xb || ((x - xa) * dir) % bay === bay - 1
  for (let x = xa; x !== xb + dir; x += dir) {
    for (let z = cz - 1; z <= cz + 1; z++) {
      c.set(x, y - 1, z, stone)
      if (pierAt(x)) for (let yy = 0; yy < y - 1; yy++) c.set(x, yy, z, stone)
      else if (x === xa || pierAt(x - dir)) stair(c, x, y - 2, z, arch, dir > 0 ? 'west' : 'east', 'top')
      else if (pierAt(x + dir)) stair(c, x, y - 2, z, arch, dir > 0 ? 'east' : 'west', 'top')
    }
    c.set(x, y, cz - 1, coping); c.set(x, y, cz + 1, coping)
  }
  c.set(xb, y, cz, coping)
  const cells = []
  for (let x = waterFrom; x !== xb; x += dir) cells.push([x, cz])
  pool(c, cells, y)
  return cells
}

/** octCells: the cells [x, z] of a rounded (octagonal) disc round (cx, cz): |dx| <= r, |dz| <= r and |dx| + |dz| <= k.
 *  k = 2r is a square, k = r a diamond; between them the corners are cut, the way a round basin reads in blocks. */
export function octCells(cx, cz, r, k) {
  const out = []
  for (let dx = -r; dx <= r; dx++) for (let dz = -r; dz <= r; dz++)
    if (Math.abs(dx) + Math.abs(dz) <= k) out.push([cx + dx, cz + dz])
  return out
}

/** ringCells: the cells of `outer` not in `inner` (both lists of [x, z]): a curb, a lip or a step ring. */
export function ringCells(outer, inner) {
  const skip = new Set(inner.map(([x, z]) => `${x},${z}`))
  return outer.filter(([x, z]) => !skip.has(`${x},${z}`))
}

/** inward: the facing that points from (x, z) toward the centre (cx, cz) along the longer offset (ties go along x). */
export function inward(x, z, cx, cz) {
  const dx = cx - x, dz = cz - z
  if (dx === 0 && dz === 0) return 'north'
  return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 'east' : 'west') : (dz > 0 ? 'south' : 'north')
}

/** bowl: a raised, brimming fountain bowl round (cx, cz) whose water lies at y. `water` are the source cells [x, z];
 *  `core` the cells the column passes through (drawn with `boss` at y - 1 and y, never water); the lip is every cell
 *  next to the water (eight ways, or only four with `reach: 4`, which rounds the bowl's corners) that is neither, drawn as bottom slabs at y so the still water stands proud of the
 *  rim; under the water and the lip at y - 1, upside-down stairs face the centre, so the bowl's underside flares out
 *  from the column like a cup. Blocks: `{ boss, flare (stairs), lip (slab) }`, ids or mix() functions. Returns the
 *  lip cells. Every source is shut in (pool() checks it). */
export function bowl(c, { cx, cz, y, water, core = [], reach = 8 }, { boss, flare, lip }) {
  const pick = (id, x, yy, z) => (typeof id === 'function' ? id(x, yy, z) : id)
  const taken = new Set([...water, ...core].map(([x, z]) => `${x},${z}`))
  const lipCells = []
  for (const [x, z] of water) for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
    const k = `${x + dx},${z + dz}`
    if (taken.has(k) || (reach === 4 && dx !== 0 && dz !== 0)) continue
    taken.add(k); lipCells.push([x + dx, z + dz])
  }
  for (const [x, z] of core) { c.set(x, y - 1, z, pick(boss, x, y - 1, z)); c.set(x, y, z, pick(boss, x, y, z)) }
  for (const [x, z] of [...water, ...lipCells])
    c.set(x, y - 1, z, pick(flare, x, y - 1, z), { facing: inward(x, z, cx, cz), half: 'top', shape: 'straight', waterlogged: 'false' })
  for (const [x, z] of lipCells) c.set(x, y, z, pick(lip, x, y, z), { type: 'bottom', waterlogged: 'false' })
  pool(c, water, y)
  return lipCells
}
