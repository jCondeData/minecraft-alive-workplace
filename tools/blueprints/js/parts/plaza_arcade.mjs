// Plaza arcades and tile roofs (parts library, tools/blueprints/REBUILD.md; grown from the Pokémon Center's Sunny Plaza
// look, 50.12): a low hipped roof of tiles laid in alternate courses of slabs and stairs (one clean even slope with a
// continuous eave, half a block up per ring), and a shallow arcade standing one block proud of a wall (piers, round
// arch heads, a frieze course on top) so a plain side reads as a loggia carried round the house. Every block comes
// from the caller's palette.
import { stair } from './canvas.mjs'

/** tileHip: a low hipped roof over x0..x1, z0..z1 whose outer ring (the eave) is a bottom slab at eaveY; each ring
 *  inward rises half a block, laid slab, stair, slab, stair... (a slab at eaveY + k/2 for even rings, a stair at
 *  eaveY + (k-1)/2 rising inward for odd ones); the ridge (the innermost ring) is `roof.full`. rebuild.py's finish()
 *  turns the hip corners' stairs. `keep(x, y, z)` false leaves a cell to the caller. Returns at(x, z): the y of the
 *  roof's cell over each column. */
export function tileHip(c, { x0, z0, x1, z1, eaveY }, roof, keep = () => true) {
  const top = Math.min(Math.floor((x1 - x0) / 2), Math.floor((z1 - z0) / 2))
  const ring = (x, z) => Math.min(x - x0, x1 - x, z - z0, z1 - z, top)
  const at = (x, z) => eaveY + Math.floor(ring(x, z) / 2)
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const d = ring(x, z), y = at(x, z)
    if (!keep(x, y, z)) continue
    if (d === top) { c.set(x, y, z, roof.full); continue }
    if (d % 2 === 0) { c.set(x, y, z, roof.slab, { type: 'bottom', waterlogged: 'false' }); continue }
    const face = d === x - x0 ? 'east' : d === x1 - x ? 'west' : d === z - z0 ? 'south' : 'north'
    stair(c, x, y, z, roof.stairs, face)
  }
  return at
}

/** tileArcade: a shallow arcade on one plane (`axis` 'x': the plane is the line z = `at`, running along x; 'z': the
 *  line x = `at`, running along z) from a0 to a1: a plinth course at y0 under all of it, `piers` (positions along the
 *  run) from y0 + 1 up to `headY`, a round head in each bay between two piers (an upside-down stair against each
 *  pier at headY - 1, the bay's top course at headY, `key` over a bay's middle when it has one), and a frieze course
 *  (`frieze`) over the whole run at headY + 1. Two-wide bays get a flat head (no stairs). P: {plinth, pier, head,
 *  stairs, key, frieze}; each an id or a function of (x, y, z). `skip(u, y)` leaves a cell to the caller. */
export function tileArcade(c, { axis, at, a0, a1, piers, y0 = 0, headY = 4 }, P, skip = () => false) {
  const put = (u, y, id, props) => {
    if (skip(u, y) || !id) return
    const [x, z] = axis === 'x' ? [u, at] : [at, u]
    c.set(x, y, z, id, props)
  }
  const ps = [...piers].sort((a, b) => a - b)
  for (let u = a0; u <= a1; u++) { put(u, y0, P.plinth); put(u, headY + 1, P.frieze) }
  for (const u of ps) for (let y = y0 + 1; y <= headY; y++) put(u, y, P.pier)
  const [lo, hi] = axis === 'x' ? ['west', 'east'] : ['north', 'south']
  for (let i = 0; i + 1 < ps.length; i++) {
    const b0 = ps[i] + 1, b1 = ps[i + 1] - 1
    if (b1 < b0) continue
    for (let u = b0; u <= b1; u++) put(u, headY, P.head)
    if (b1 - b0 >= 2) {
      put(b0, headY - 1, P.stairs, { facing: lo, half: 'top', shape: 'straight', waterlogged: 'false' })
      put(b1, headY - 1, P.stairs, { facing: hi, half: 'top', shape: 'straight', waterlogged: 'false' })
      if ((b1 - b0) % 2 === 0 && P.key) put((b0 + b1) / 2, headY, P.key)
    }
  }
}
