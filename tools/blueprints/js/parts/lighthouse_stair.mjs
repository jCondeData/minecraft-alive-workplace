// A tower's winding stair and a lantern's hooded lamp eave (the Lighthouse, 49, round 2). Every block from the palette
// passed in.
//   wallRing    the closed loop of cells hugging a round room's wall, face to face, for a stair to wind up
//   spiralStair a stair winding up a wallRing, one step a cell, floors pierced only where the climb needs headroom
//   lampHood    a lantern's roof: an eave ring of night lamps (each under an inverted daylight detector open to the sky)
//               one cell outside the glass, and a stepped cap over the glass that never covers a detector
import { stair, slab } from './canvas.mjs'

/** wallRing: the cells of `room` ([[x, z]...], a round room's floor) that touch a cell outside it, walked in one loop
 *  face to face (counter-clockwise seen from above, starting nearest `start` [x, z]). */
export function wallRing(room, start) {
  const has = new Set(room.map(([x, z]) => `${x},${z}`))
  const edge = room.filter(([x, z]) => [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]].some(([a, b]) => !has.has(`${x + a},${z + b}`)))
  const isEdge = new Set(edge.map(([x, z]) => `${x},${z}`))
  let cur = edge.reduce((b, p) => ((p[0] - start[0]) ** 2 + (p[1] - start[1]) ** 2 < (b[0] - start[0]) ** 2 + (b[1] - start[1]) ** 2 ? p : b))
  const out = [cur], seen = new Set([`${cur}`])
  for (;;) {
    const next = [[1, 0], [0, 1], [-1, 0], [0, -1]].map(([a, b]) => [cur[0] + a, cur[1] + b]).filter(p => isEdge.has(`${p}`) && !seen.has(`${p}`))
    if (!next.length) break
    // keep turning the same way round: prefer the step that keeps the wall on the same hand (largest cross product)
    const cx = room.reduce((s, p) => s + p[0], 0) / room.length, cz = room.reduce((s, p) => s + p[1], 0) / room.length
    next.sort((p, q) => cross(cur, q, cx, cz) - cross(cur, p, cx, cz))
    cur = next[0]; out.push(cur); seen.add(`${cur}`)
  }
  return out
}
const cross = ([x, z], [nx, nz], cx, cz) => (x - cx) * (nz - z) - (z - cz) * (nx - x)

/** spiralStair: a stair up `ring` from y0 (step k at ring[k % n], height y0 + k) to y1, each step facing the next cell,
 *  and floors at `floors` (y list, `floorBlock` over `room`), each pierced over the two steps below it for headroom.
 *  Only the courses from..to are laid (a tower raised stage by stage). Returns at(y) -> the ring index at y, or -1. */
export function spiralStair(c, ring, room, y0, y1, floors, { stairs, floorBlock }, from = y0, to = y1) {
  const n = ring.length
  const at = y => (y >= y0 && y <= y1 ? ((y - y0) % n + n) % n : -1)
  for (let y = from; y <= to; y++) {
    if (floors.includes(y)) {
      const holes = new Set([at(y - 1), at(y - 2)].filter(i => i >= 0).map(i => `${ring[i]}`))
      for (const [x, z] of room) if (!holes.has(`${x},${z}`)) c.set(x, y, z, floorBlock)
    }
    const i = at(y)
    if (i < 0) continue
    const [x, z] = ring[i], [nx, nz] = ring[(i + 1) % n]
    const facing = nx > x ? 'east' : nx < x ? 'west' : nz > z ? 'south' : 'north'
    stair(c, x, y, z, stairs, facing)
  }
  return at
}

/** lampHood: over a square lantern of half-width h whose glass ends below y: at y a cap course (pal.cap) over the glass
 *  and an eave ring at half-width h + 1 of redstone lamps (pal.corner at its corners), each under an inverted daylight
 *  detector; then the cap steps in, a ring of pal.stairs sloping out each course (pal.slab at the corners), up to a
 *  `finial` block on top. Nothing ever stands over a detector. Returns the lamps' cells. */
export function lampHood(c, cx, cz, h, y, pal, finial) {
  const lamps = []
  const e = h + 1
  for (let dx = -e; dx <= e; dx++) for (let dz = -e; dz <= e; dz++) {
    const x = cx + dx, z = cz + dz
    const ring = Math.max(Math.abs(dx), Math.abs(dz))
    if (ring < e) { c.set(x, y, z, pal.cap); continue }
    if (Math.abs(dx) === e && Math.abs(dz) === e) { c.set(x, y, z, pal.corner); slab(c, x, y + 1, z, pal.slab); continue }
    c.set(x, y, z, 'redstone_lamp', { lit: 'false' })
    c.set(x, y + 1, z, 'daylight_detector', { inverted: 'true', power: '0' })
    lamps.push([x, y, z])
  }
  let top = y
  for (let r = h; r >= 1; r--) {
    top++
    for (let dx = -r; dx <= r; dx++) for (let dz = -r; dz <= r; dz++) {
      const x = cx + dx, z = cz + dz
      const ring = Math.max(Math.abs(dx), Math.abs(dz))
      if (ring < r) { c.set(x, top, z, pal.cap); continue }
      if (Math.abs(dx) === r && Math.abs(dz) === r) { slab(c, x, top, z, pal.slab); continue }
      const facing = Math.abs(dx) === r ? (dx > 0 ? 'west' : 'east') : (dz > 0 ? 'north' : 'south')
      stair(c, x, top, z, pal.stairs, facing)
    }
    if (top + 1 >= c.size[1] - 1) break
  }
  if (finial && top + 1 < c.size[1]) c.set(cx, top + 1, cz, ...finial)
  return lamps
}
