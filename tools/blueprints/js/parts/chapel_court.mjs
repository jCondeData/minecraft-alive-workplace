// Chapel parts (grown from the Chapel's Blossom Court, ROADMAP 49): a coped stone gable that carries a roof's profile
// up past its slates, a coped court wall with piers, belfry louvres, and a pew with an arm at its aisle end. Every
// block comes from the caller's palette; nothing is picked here.
import { DIRS, OPP, slab, stair, trapdoor } from './canvas.mjs'

/** copedGable: a parapet gable in the plane `line` (axis 'z': the plane z = line, columns x) under a roof already
 *  drawn on the row `from` beside it: each column's roof cells on that row (from under[u] up) are copied onto the
 *  gable through `map(id)` (slates to dressed stone: the coping follows the pitch and stands proud of the roof's end),
 *  and the column below them is filled with `wall` (a block or a mix()) down to y0. `pick(u, y)` may return another
 *  block for a cell below the coping (a window), null for air, or undefined for the wall. */
export function copedGable(c, axis, line, from, [a0, a1], under, y0, wall, map, pick = () => undefined) {
  const at = (u, v) => (axis === 'z' ? [u, v] : [v, u])
  for (let u = a0; u <= a1; u++) {
    const [fx, fz] = at(u, from), [x, z] = at(u, line)
    for (let y = under[u]; c.inside(fx, y, fz); y++) {
      const got = c.get(fx, y, fz)
      if (!got) break
      c.set(x, y, z, map(got.id.replace('minecraft:', '')), got.props)
    }
    for (let y = y0; y < under[u]; y++) {
      const p = pick(u, y)
      if (p === null) { c.clear(x, y, z); continue }
      const [id, props] = Array.isArray(p) ? p : [p ?? wall, {}]
      c.set(x, y, z, id, props)
    }
  }
}

/** courtWall: a coped wall along `axis` on the line `at`, from a to b: a plinth course at y0 (`plinth`), the wall
 *  (`wall`) from y0 + 1 to `top`, a coping of bottom slabs (`coping`) on it; at each of `piers` the wall is a pier of
 *  `pier` one course taller under its own coping slab (`pierCap`), so a long wall reads in bays. `skip(u, y)` leaves a
 *  cell for an opening (a grille, a gateway). */
export function courtWall(c, axis, at, a, b, { y0 = 0, top }, { plinth, wall, coping, pier, pierCap = coping, piers = [], skip = () => false }) {
  for (let u = Math.min(a, b); u <= Math.max(a, b); u++) {
    const [x, z] = axis === 'x' ? [u, at] : [at, u]
    const isPier = piers.includes(u)
    c.set(x, y0, z, plinth)
    for (let y = y0 + 1; y <= top + (isPier ? 1 : 0); y++) if (!skip(u, y)) c.set(x, y, z, isPier ? pier : wall)
    slab(c, x, top + (isPier ? 2 : 1), z, isPier ? pierCap : coping)
  }
}

/** louvres: open trapdoors (`id`) filling an opening's cells, slats a belfry's sound comes out through (and its lamp
 *  shows through at night); `facing`: the face's outward side. */
export function louvres(c, cells, id, facing) {
  for (const [x, y, z] of cells) trapdoor(c, x, y, z, id, facing, 'top', true)
}

/** pew: a row of stair seats from x0 to x1 (along x) at y, z, the sitters facing `facing` (north or south), with an
 *  open trapdoor arm at x = arm (the aisle end), each seat a 'bench' spot (the arm goes with the seat beside it). */
export function pew(c, { x0, x1, y, z, facing, arm = null }, wood) {
  for (let x = x0; x <= x1; x++) {
    c.free([x, y, z])
    stair(c, x, y, z, `${wood}_stairs`, OPP[facing])
    const cells = [[0, 0, 0]]
    if (arm !== null && Math.abs(arm - x) === 1) cells.push([arm - x, 0, 0])
    c.spot('bench', [x, y, z], facing, cells, wood)
  }
  if (arm !== null) {
    c.free([arm, y, z])
    trapdoor(c, arm, y, z, `${wood}_trapdoor`, arm > x1 ? 'west' : 'east', 'bottom', true)
  }
}

/** choirStall: stair seats on the given [x, y, z] cells, the sitters facing `facing` (a choir stall against a side wall),
 *  each a 'bench' spot. */
export function choirStall(c, cells, facing, wood) {
  for (const p of cells) {
    c.free(p)
    stair(c, ...p, `${wood}_stairs`, OPP[facing])
    c.spot('bench', p, facing, [[0, 0, 0]], wood)
  }
}
