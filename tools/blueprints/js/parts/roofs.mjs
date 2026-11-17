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
