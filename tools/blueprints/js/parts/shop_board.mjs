// Shop boards (ROADMAP 49, the Market Stall): the medieval shopfront that opens on two hinged boards. The lower board
// folds down onto the sill as a counter ledge, the upper board is propped up and out as a canopy, and a striped cloth
// valance hangs from its edge on two poles. At night both boards shut the opening. Every block comes from the caller's
// palette ({ board, sill, cloth: [wool, wool], prop }); nothing is picked here.
import { DIRS, OPP, trapdoor } from './canvas.mjs'

/** shopBoard: the opening is the frame line `z` (or `x` with axis 'z') between a0 and a1. `sill` (the counter) fills
 *  the opening's cells at y (except `gap` cells: a way in behind the counter); the lower board lies flat one block out
 *  at y, its top flush with the sill (a top-half trapdoor); the upper board lies flat one out at `canopyY`; the valance
 *  hangs two out at canopyY - 1 from v0 to v1 (default a0 - 1 .. a1 + 1), striped by cell, on a pole of `prop` at each
 *  end. Returns { ledge, valance }: the ledge's cells (for goods laid on it) and the valance's. */
export function shopBoard(c, { axis = 'x', line, a0, a1, y = 0, canopyY = 4, out = 'north', gap = [], v0 = null, v1 = null, boardTo = null },
  { board, sill, cloth, prop }) {
  const [ox, oz] = DIRS[out]
  const at = (u, k) => (axis === 'x' ? [u + ox * k, line + oz * k] : [line + ox * k, u + oz * k])
  const ledge = [], valance = []
  for (let u = a0; u <= a1; u++) {
    const [sx, sz] = at(u, 0)
    if (gap.includes(u)) continue
    c.set(sx, y, sz, sill)
    if (boardTo !== null && u > boardTo) continue
    const [bx, bz] = at(u, 1)
    trapdoor(c, bx, y, bz, board, OPP[out], 'top') // folded down, hinged at the sill
    ledge.push([bx, y + 1, bz])
  }
  for (let u = a0; u <= a1; u++) { const [bx, bz] = at(u, 1); trapdoor(c, bx, canopyY, bz, board, OPP[out], 'top') } // propped up
  const lo = v0 ?? a0 - 1, hi = v1 ?? a1 + 1
  for (let u = lo; u <= hi; u++) {
    const [vx, vz] = at(u, 2)
    c.set(vx, canopyY - 1, vz, cloth[((u - lo) % cloth.length + cloth.length) % cloth.length])
    valance.push([vx, canopyY - 1, vz])
  }
  for (const u of [lo, hi]) { const [px, pz] = at(u, 2); for (let yy = y; yy < canopyY - 1; yy++) c.set(px, yy, pz, prop) }
  return { ledge, valance }
}

/** flagPole: a pole of `log` (a log stands on end; a fence or wall as it is) from y0 to y1 with a standing banner on top, turned to face `out` (a market
 *  pitch's pennant, seen over the stalls). */
export function flagPole(c, [x, z], y0, y1, log, banner, out = 'north') {
  const props = /_(log|wood|stem)$/.test(log) ? { axis: 'y' } : {}
  for (let y = y0; y <= y1; y++) c.set(x, y, z, log, props)
  const rot = { south: 0, west: 4, north: 8, east: 12 }[out]
  c.set(x, y1 + 1, z, banner, { rotation: String(rot) })
}
