// Bridges: a stone humpback that steps up over its water and down again, a little roofed lamp gate to stand on one,
// and a stream held in at ground level by its own banks (parts library, tools/blueprints/REBUILD.md). Grown from the
// Footbridge (49). Every block comes from the family's palette; none is picked here.
import { stair, slab } from './canvas.mjs'

const pick = (id, x, y, z) => (typeof id === 'function' ? id(x, y, z) : id)

/** humpback: a stone bridge crossing along z from z0 to z1 (an odd length 2h + 1), x0..x1 wide (its two outer columns
 *  are the parapets, the ones between the walk), standing on y. The walk climbs one stair a block, from a step at y on
 *  each end to the crown (a full block at y + h - 1, walked on at y + h). Under it the span `span` [za, zb] is a
 *  one-course arch over the water at y: upside-down `ring` stairs at the span's two ends (y + 1), open between them
 *  under the crown, the `key` block over the opening's middle on both faces; the rest is `body` (a mix() reads as
 *  rubble). The parapets carry a sloping coping of `coping` stairs one block over the walk (`copingFull` at the
 *  crown) and end in newels of `newel` (a wall id) `newelHeight` tall at each end, with `lamp` on top when given.
 *  Leaves y under the span to the caller (the water). Returns { crownY, walkY(z), newels: [[x, y, z]] }. */
export function humpback(c, { x0, x1, z0, z1, y = 0, span, newelHeight = 2 },
  { body, ring, key, walk, walkFull, coping, copingFull, newel, lamp = null }) {
  const n = z1 - z0 + 1
  if (n % 2 !== 1) throw new Error(`humpback length ${n} isn't odd`)
  const h = (n - 1) / 2, mid = z0 + h
  const rise = z => Math.min(z - z0, z1 - z) // 0 at the ends, h at the crown
  const up = z => (z < mid ? 'south' : 'north') // a stair's high side faces the crown
  const crownY = y + h - 1
  const [za, zb] = span
  const inSpan = z => z >= za && z <= zb
  const newels = []
  for (let z = z0; z <= z1; z++) {
    const d = rise(z)
    const top = z === mid ? crownY : y + d // the walk's block at this z (a stair, or the crown's full block)
    for (let x = x0; x <= x1; x++) {
      const face = x === x0 || x === x1
      // the body under the walk: solid down to y, except over the span (the water and the arch's opening)
      for (let yy = y; yy < top; yy++) {
        if (inSpan(z) && yy === y) continue // the water's cell
        if (inSpan(z) && (z === za || z === zb) && yy === y + 1) {
          stair(c, x, yy, z, ring, z === za ? 'north' : 'south', 'top'); continue // the arch's springers
        }
        if (inSpan(z) && z !== za && z !== zb && yy >= y + 1 && yy < crownY) continue // the opening
        c.set(x, yy, z, body)
      }
      if (face) {
        if (d === 0) { // the newels: a post of wall at each end of each parapet
          c.set(x, top, z, body)
          for (let i = 1; i <= newelHeight; i++) c.set(x, top + i, z, newel)
          if (lamp) c.set(x, top + newelHeight + 1, z, lamp, { hanging: 'false', waterlogged: 'false' })
          newels.push([x, top + newelHeight, z])
        } else {
          c.set(x, top, z, z === mid ? key : body) // the parapet's body, level with the walk; the key at the crown
          if (z === mid) c.set(x, top + 1, z, copingFull)
          else stair(c, x, top + 1, z, coping, up(z))
        }
      } else if (z === mid) c.set(x, top, z, walkFull)
      else stair(c, x, top, z, walk, up(z))
    }
  }
  // the key over the opening on both faces: the course under the crown, when the opening leaves one
  if (zb - za >= 2 && crownY - 1 > y + 1) for (const x of [x0, x1]) c.set(x, crownY - 1, mid, key)
  return { crownY, walkY: z => (z === mid ? crownY + 1 : y + rise(z)), newels }
}

/** lampGate: a lych-gate over a walk, its two `post`s (logs, or fences for a light gate) at x0 and x1 on line z from y
 *  to y + 1, a `beam` log between them at y + 2 with a `lamp` hung under its middle, and a little hipped hat of `roof`
 *  over it: stairs all round the beam at y + 2 (a block out on every side; kit.finish turns the corners) and a ridge
 *  of `ridge` slabs on the beam at y + 3. */
export function lampGate(c, { x0, x1, z, y }, { post, beam, roof, ridge = roof.slab, lamp }) {
  const upright = /(_fence|_wall)$/.test(post) ? {} : { axis: 'y' }
  for (const x of [x0, x1]) for (let yy = y; yy <= y + 1; yy++) c.set(x, yy, z, post, upright)
  for (let x = x0; x <= x1; x++) { c.set(x, y + 2, z, beam, { axis: 'x' }); slab(c, x, y + 3, z, ridge) }
  for (let x = x0 - 1; x <= x1 + 1; x++) {
    stair(c, x, y + 2, z - 1, roof.stairs, 'south')
    stair(c, x, y + 2, z + 1, roof.stairs, 'north')
  }
  stair(c, x0 - 1, y + 2, z, roof.stairs, 'east') // the hipped ends
  stair(c, x1 + 1, y + 2, z, roof.stairs, 'west')
  c.set((x0 + x1) / 2, y + 1, z, lamp, { hanging: 'true', waterlogged: 'false' })
}

/** stream: still water (sources) lying at ground level (y = 0 rests on the ground under the build) in the given
 *  cells [x, z]; throws if a cell's side is left open or lies on the plot's edge, or (above y 0) it has no floor. */
export function stream(c, cells, y, water = 'water') {
  const inIt = new Set(cells.map(([x, z]) => `${x},${z}`))
  const [w, , d] = c.size
  for (const [x, z] of cells) c.set(x, y, z, water, { level: '0' })
  for (const [x, z] of cells) {
    if (x <= 0 || z <= 0 || x >= w - 1 || z >= d - 1) throw new Error(`water at ${x},${y},${z} is on the plot's edge`)
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]])
      if (!inIt.has(`${x + dx},${z + dz}`) && !c.get(x + dx, y, z + dz)) throw new Error(`water at ${x},${y},${z} runs out ${dx},${dz}`)
    if (y > 0 && !c.get(x, y - 1, z)) throw new Error(`water at ${x},${y},${z} has no floor`)
  }
}

/** banks: the ground layer at y over x0..x1, z0..z1 round a stream: every cell next to the water (four ways) is `lip`
 *  (stone, a mix() reads as a worn edge; a mix with the soil in it gives a soft bank), the rest `soil` (any cell that
 *  gets the soil's id gets `soilProps`); cells already drawn, and the `skip` cells [x, z] (a rounded outline's clipped
 *  corners), are left alone. Returns { lip, soil }:
 *  the cells [x, z] each got. */
export function banks(c, { x0, z0, x1, z1, y = 0 }, water, { lip, soil, soilProps = {} }, skip = []) {
  const wet = new Set(water.map(([x, z]) => `${x},${z}`))
  const gone = new Set(skip.map(([x, z]) => `${x},${z}`))
  const out = { lip: [], soil: [] }
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (wet.has(`${x},${z}`) || gone.has(`${x},${z}`) || c.get(x, y, z)) continue
    const edge = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dz]) => wet.has(`${x + dx},${z + dz}`))
    const id = pick(edge ? lip : soil, x, y, z)
    c.set(x, y, z, id, id === pick(soil, x, y, z) ? soilProps : {})
    out[edge ? 'lip' : 'soil'].push([x, z])
  }
  return out
}
