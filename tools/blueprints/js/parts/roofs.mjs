// Roofs and gables: a gable roof (steep or 45-degree, ridge along x or z), the gable wall under it, barge boards under
// its rake, and a dormer (parts library, tools/blueprints/REBUILD.md). `roof` is the palette's {full, stairs, slab}.
import { slab, stair } from './canvas.mjs'

/** gableRoof: a roof over `across` (the span, eaves at both ends) running `along` (gable ends at both ends).
 *  steep: a full block under each stair (two up per one across). `keep(x,y,z)` returns false for cells another roof
 *  or wall owns. Returns the ridge's y and each column's underside (for gables and trim). */
export function gableRoof(c, { axis, across: [a0, a1], along: [l0, l1], eaveY, steep }, roof, keep = () => true) {
  const half = (a1 - a0) / 2
  const mid = a0 + half
  const put = (u, y, v, id, props) => {
    const [x, z] = axis === 'z' ? [u, v] : [v, u]
    if (keep(x, y, z)) c.set(x, y, z, id, props)
  }
  const ridgeY = steep ? eaveY + 2 * half - 1 : eaveY + half
  for (let v = l0; v <= l1; v++) {
    for (let i = 0; i < half; i++) {
      for (const [u, rise] of [[a0 + i, axis === 'z' ? 'east' : 'south'], [a1 - i, axis === 'z' ? 'west' : 'north']]) {
        if (steep) {
          if (i > 0) put(u, eaveY + 2 * i - 1, v, roof.full)
          put(u, eaveY + 2 * i, v, roof.stairs, { facing: rise, half: 'bottom', shape: 'straight', waterlogged: 'false' })
        } else {
          put(u, eaveY + i, v, roof.stairs, { facing: rise, half: 'bottom', shape: 'straight', waterlogged: 'false' })
        }
      }
    }
    put(mid, ridgeY, v, roof.full)
  }
  const under = {}
  for (let i = 0; i <= half; i++) {
    const y = i === 0 ? eaveY : steep ? (i === half ? ridgeY : eaveY + 2 * i - 1) : eaveY + i
    under[a0 + i] = y; under[a1 - i] = y
  }
  return { ridgeY, under }
}

/** gableWall: fills a gable end under a roof from y0 up to each column's underside, `pick(u, y)` choosing the block
 *  (an id, [id, props], or null to leave it). */
export function gableWall(c, axis, line, [a0, a1], y0, under, pick) {
  for (let u = a0; u <= a1; u++) for (let y = y0; y < under[u]; y++) {
    const id = pick(u, y)
    if (!id) continue
    const [x, z] = axis === 'z' ? [u, line] : [line, u]
    const [name, props] = Array.isArray(id) ? id : [id, {}]
    c.set(x, y, z, name, props)
  }
}

/** bargeTrim: a dark board under a gable's overhanging rake, stepping up with it (an upside-down slab under each
 *  column's lowest roof cell on the overhang line), so the roof's edge reads apart from the gable. Given a `_stairs`
 *  id it lays upside-down stairs instead, each facing up its slope (the ridge column faces a0's way): use that under a
 *  45-degree rake, where a top slab beside the next roof stair down reads as a table with a chair at it (B148's check). */
export function bargeTrim(c, axis, line, [a0, a1], under, id) {
  const mid = (a0 + a1) / 2
  for (let u = a0; u <= a1; u++) {
    const [x, z] = axis === 'z' ? [u, line] : [line, u]
    if (c.get(x, under[u] - 1, z)) continue
    if (id.endsWith('_stairs')) {
      const up = u <= mid ? (axis === 'z' ? 'east' : 'south') : (axis === 'z' ? 'west' : 'north')
      stair(c, x, under[u] - 1, z, id, up, 'top')
    } else slab(c, x, under[u] - 1, z, id, 'top')
  }
}

/** dormer: a three-wide gabled dormer in a slope whose ridge runs along z, centred on z: its face on the knee line
 *  (x = `face`, y0..y0+2, and y0+3 at the apex) of infill round a two-high window, corner posts, its little roof
 *  (ridge along x) running from the eave line (`eave`, outward of the face) into the main slope. The Daub House's
 *  is face 1, eave 0, y0 6 (the west slope); an east-slope dormer passes its own face and eave (eave = face + 1). */
export function dormer(c, z, roof, infill, glass, frame, { face = 1, eave = 0, y0 = 6 } = {}) {
  for (let dz = -1; dz <= 1; dz++) for (let y = y0; y <= y0 + 2; y++) c.set(face, y, z + dz, infill)
  c.set(face, y0 + 3, z, infill)
  c.set(face, y0, z, glass); c.set(face, y0 + 1, z, glass)
  for (const dz of [-1, 1]) for (let y = y0; y <= y0 + 2; y++) c.set(face, y, z + dz, frame, { axis: 'y' })
  const step = face >= eave ? 1 : -1
  const xs = [eave, face]
  for (const x of xs) {
    stair(c, x, y0 + 2, z - 2, roof.stairs, 'south')
    stair(c, x, y0 + 2, z + 2, roof.stairs, 'north')
    stair(c, x, y0 + 3, z - 1, roof.stairs, 'south')
    stair(c, x, y0 + 3, z + 1, roof.stairs, 'north')
  }
  for (const x of [eave, face, face + step]) c.set(x, y0 + 4, z, roof.full)
}

/** flaredGableRoof: a gable roof (gableRoof, steep unless `steep: false`) whose eaves kick out one block further and
 *  one lower in a `kick` stair (the eave line in its own trim; under a steep body it reads bell-cast), over `across`
 *  (the kick columns are its ends) running `along`. Returns gableRoof's {ridgeY, under}, with the kick columns'
 *  undersides added. */
export function flaredGableRoof(c, { axis, across: [a0, a1], along: [l0, l1], eaveY, steep = true }, roof, kick, keep = () => true) {
  const body = gableRoof(c, { axis, across: [a0 + 1, a1 - 1], along: [l0, l1], eaveY: eaveY + 1, steep }, roof, keep)
  for (let v = l0; v <= l1; v++)
    for (const [u, rise] of [[a0, axis === 'z' ? 'east' : 'south'], [a1, axis === 'z' ? 'west' : 'north']]) {
      const [x, z] = axis === 'z' ? [u, v] : [v, u]
      if (keep(x, eaveY, z)) stair(c, x, eaveY, z, kick, rise)
    }
  return { ridgeY: body.ridgeY, under: { ...body.under, [a0]: eaveY, [a1]: eaveY } }
}

/** catslideRoof: a 45-degree roof whose ridge (`ridge`, a cell across the span) need not sit in the middle: the
 *  slope on one side runs further and lower than the other (a saltbox's catslide over its outshot), or, with the ridge
 *  at one end of `across`, a lean-to. y = ridgeY - |u - ridge| across `across` [a0, a1], repeated along `along`. The
 *  ridge cell is `roof.full` unless `ridgeStair` names the way a lean-to's top stair rises. `keep(x, y, z)` as in
 *  gableRoof. Returns {ridgeY, under} (under[u]: the roof's y over column u). */
export function catslideRoof(c, { axis, across: [a0, a1], ridge, along: [l0, l1], ridgeY }, roof, keep = () => true) {
  const put = (u, y, v, id, props) => {
    const [x, z] = axis === 'z' ? [u, v] : [v, u]
    if (keep(x, y, z)) c.set(x, y, z, id, props)
  }
  const under = {}
  for (let u = a0; u <= a1; u++) under[u] = ridgeY - Math.abs(u - ridge)
  for (let v = l0; v <= l1; v++) for (let u = a0; u <= a1; u++) {
    if (u === ridge) { put(u, ridgeY, v, roof.full); continue }
    const rise = u < ridge ? (axis === 'z' ? 'east' : 'south') : (axis === 'z' ? 'west' : 'north')
    put(u, under[u], v, roof.stairs, { facing: rise, half: 'bottom', shape: 'straight', waterlogged: 'false' })
  }
  return { ridgeY, under }
}

/** pentice: a little lean-to roof of stairs along a wall face between storeys (`cells`: [x, z] outside the wall at
 *  y, rising toward the wall `toward`), the way a pent roof sheds rain off a ground-floor window. */
export function pentice(c, cells, y, roof, toward) {
  for (const [x, z] of cells) stair(c, x, y, z, roof.stairs, toward)
}

/** pyramidRoof: a steep four-sided roof over a square tower centred on (cx, cz): a ring of stairs `r` out from the
 *  centre at y0 (the overhang when r is the walls' half-width + 1), each ring in one block and two up from the last
 *  (a full course under each ring of stairs), solid inside, to a full block at the peak (y0 + 2r). Grown from the
 *  Burgher's Townhouse (49), whose stair tower carries one. Returns the peak's y. */
export function pyramidRoof(c, cx, cz, r, y0, roof) {
  const face = (dx, dz) => (Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? 'east' : 'west') : (dz < 0 ? 'south' : 'north'))
  for (let k = 0; k <= r; k++) {
    const rr = r - k, y = y0 + 2 * k
    for (let dx = -rr; dx <= rr; dx++) for (let dz = -rr; dz <= rr; dz++) {
      const ring = Math.max(Math.abs(dx), Math.abs(dz)) === rr && rr > 0
      if (ring) stair(c, cx + dx, y, cz + dz, roof.stairs, face(dx, dz))
      else c.set(cx + dx, y, cz + dz, roof.full)
      if (!ring && k < r) c.set(cx + dx, y + 1, cz + dz, roof.full)
    }
  }
  return y0 + 2 * r
}

/** evenGableRoof: a steep roof over an even span `across` [a0, a1] (no middle column), running `along` [l0, l1], its
 *  ridge two stairs back to back over the two middle columns. Each edge column is an 'eave' (a stair at eaveY rising
 *  inward, and an overhang stair one out and one lower) or a 'gutter' (a full block at eaveY with a `roof.slab` on it:
 *  where two such roofs meet over a party wall, the valley between them). Inward from the edge, each column a full
 *  block and a stair two higher than the last (the steep pitch of a narrow-fronted house). `ends`: {a0, a1}, each
 *  'eave' or 'gutter'; `keep(x, y, z)` as in gableRoof. Grown from the Terrace (49): one per home, side by side, so a
 *  row reads as a saw of gables. Returns {ridgeY, under} (under[u]: the lowest roof cell over column u, overhangs too). */
export function evenGableRoof(c, { axis, across: [a0, a1], along: [l0, l1], eaveY, ends = { a0: 'eave', a1: 'eave' } }, roof, keep = () => true) {
  const put = (u, y, v, id, props) => {
    const [x, z] = axis === 'z' ? [u, v] : [v, u]
    if (c.inside(x, y, z) && keep(x, y, z)) c.set(x, y, z, id, props)
  }
  const n = (a1 - a0 + 1) / 2
  if (!Number.isInteger(n)) throw new Error(`evenGableRoof: span ${a0}..${a1} is odd (use gableRoof)`)
  const st = facing => ({ facing, half: 'bottom', shape: 'straight', waterlogged: 'false' })
  const under = {}
  for (let v = l0; v <= l1; v++) {
    for (const [edge, u0, step, rise] of [['a0', a0, 1, axis === 'z' ? 'east' : 'south'], ['a1', a1, -1, axis === 'z' ? 'west' : 'north']]) {
      for (let i = 0; i < n; i++) {
        const u = u0 + step * i
        if (i > 0) {
          put(u, eaveY + 2 * i - 1, v, roof.full)
          put(u, eaveY + 2 * i, v, roof.stairs, st(rise))
          under[u] = eaveY + 2 * i - 1
        } else if (ends[edge] === 'gutter') {
          put(u, eaveY, v, roof.full)
          put(u, eaveY + 1, v, roof.slab, { type: 'bottom', waterlogged: 'false' })
          under[u] = eaveY
        } else {
          put(u, eaveY, v, roof.stairs, st(rise)); under[u] = eaveY
          put(u - step, eaveY - 1, v, roof.stairs, st(rise)); under[u - step] = eaveY - 1
        }
      }
    }
  }
  return { ridgeY: eaveY + 2 * (n - 1), under }
}

/** crowGable: a crow-stepped parapet gable in the wall plane `line` over columns [a0, a1] (axis 'z': the columns are
 *  x, the plane z = line), from y0 up to each column's step top `tops[u]` in `wall` (may be a mix()), each step capped
 *  by a `coping` slab, so the gable stands up past the roof behind it in steps. `pick(u, y)` returns another block (an
 *  id or [id, props]) for an opening or a lintel, null for air, undefined for the wall. Grown from the Terrace (49). */
export function crowGable(c, axis, line, [a0, a1], y0, tops, wall, coping, pick = () => undefined) {
  for (let u = a0; u <= a1; u++) {
    const [x, z] = axis === 'z' ? [u, line] : [line, u]
    for (let y = y0; y <= tops[u]; y++) {
      const got = pick(u, y)
      if (got === null) { c.clear(x, y, z); continue }
      const [id, props] = Array.isArray(got) ? got : [got ?? wall, {}]
      c.set(x, y, z, id, props)
    }
    slab(c, x, tops[u] + 1, z, coping, 'bottom')
  }
}

/** crossGableRoof: two 45-degree gable roofs of one pitch crossing at right angles over a square-ish plan (x0..x1,
 *  z0..z1 with the overhang), ridges along x at z = cz and along z at x = cx, both at eaveY + half: each column takes
 *  the higher of the two slopes, so every side shows a gable and the corners fold into valleys (kit.finish shapes the
 *  valley stairs). Grown from the Well House (49). Returns {ridgeY, at(x, z)}: the roof's y over each column. */
export function crossGableRoof(c, { x0, x1, z0, z1, eaveY }, roof, keep = () => true) {
  const cx = (x0 + x1) / 2, cz = (z0 + z1) / 2
  const halfX = (x1 - x0) / 2, halfZ = (z1 - z0) / 2
  const hA = x => eaveY + halfX - Math.abs(x - cx) // the ridge along z (slopes falling east and west)
  const hB = z => eaveY + halfZ - Math.abs(z - cz) // the ridge along x (slopes falling north and south)
  const at = (x, z) => Math.max(hA(x), hB(z))
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const a = hA(x), b = hB(z), y = Math.max(a, b)
    if (!keep(x, y, z)) continue
    if ((a >= b && x === cx) || (b > a && z === cz)) { c.set(x, y, z, roof.full); continue }
    const facing = a >= b ? (x < cx ? 'east' : 'west') : (z < cz ? 'south' : 'north')
    stair(c, x, y, z, roof.stairs, facing)
  }
  return { ridgeY: eaveY + Math.max(halfX, halfZ), at }
}

/** louvre: a little open ventilator on a ridge crossing (centre cx, cz, its sill at y): the sill's 3 x 3 made solid
 *  (`base`), four corner posts (`post`, a fence id) one high, a cap of `roof` stairs round a full block, a `finial` on
 *  top, and a lantern hanging inside under the cap (light seen through the open sides at night). */
export function louvre(c, cx, cz, y, { base, post, roof, finial = null, lamp = true }) {
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) c.set(cx + dx, y, cz + dz, base)
  for (const dx of [-1, 1]) for (const dz of [-1, 1]) c.set(cx + dx, y + 1, cz + dz, post)
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
    if (!dx && !dz) { c.set(cx, y + 2, cz, roof.full); continue }
    const facing = dz === -1 ? 'south' : dz === 1 ? 'north' : dx === -1 ? 'east' : 'west'
    stair(c, cx + dx, y + 2, cz + dz, roof.stairs, facing)
  }
  if (lamp) c.set(cx, y + 1, cz, 'lantern', { hanging: 'true', waterlogged: 'false' })
  if (finial) c.set(cx, y + 3, cz, finial)
}
