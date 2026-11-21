// Arenas (the Festival Arena, 49 rebuild): a battle pitch marked out in chalk, an arcade face of piers with arch
// springers, a trainer's box under a hip canopy, and a mast whose top cell carries hung banners. Every block comes
// from the caller's palette; nothing is picked here.
import { DIRS, OPP, stair } from './canvas.mjs'
import { hipCap } from './tinker.mjs'

/** battlePitch: a flat pitch at y over x0..x1, z0..z1 (odd spans, so it has a true middle): `field` everywhere, a
 *  halfway `line` across the middle x, a chalk circle of radius `circle` round the centre, the centre `spot`, and an
 *  `edge` ring one block outside the pitch (the boundary line). Only floor blocks: nothing stands on a pitch. */
export function battlePitch(c, { x0, x1, z0, z1, y = 0 }, { field, edge, line, spot, circle = 2 }) {
  const cx = (x0 + x1) / 2, cz = (z0 + z1) / 2
  for (let x = x0 - 1; x <= x1 + 1; x++) for (let z = z0 - 1; z <= z1 + 1; z++) {
    if (x < x0 || x > x1 || z < z0 || z > z1) { c.set(x, y, z, edge); continue }
    const d = Math.round(Math.hypot(x - cx, z - cz))
    c.set(x, y, z, x === cx && z === cz ? spot : d === circle || x === cx ? line : field)
  }
}

/** arcadeFace: a row of piers along x at z = line (each from y0 to y1) with an arch springer (an upside-down stair,
 *  its back to the pier) at y1 either side of every bay at least three wide, so each bay reads as a low arch.
 *  `skip` lists bays (by their first x) left square-headed. */
export function arcadeFace(c, { line, piers, y0 = 1, y1 = 3, skip = [] }, { pier, spring }) {
  for (const x of piers) for (let y = y0; y <= y1; y++) c.set(x, y, line, pier)
  for (let i = 0; i + 1 < piers.length; i++) {
    const a = piers[i], b = piers[i + 1]
    if (b - a - 1 < 3 || skip.includes(a + 1)) continue
    stair(c, a + 1, y1, line, spring, 'west', 'top')
    stair(c, b - 1, y1, line, spring, 'east', 'top')
  }
}

/** trainerBox: a trainer's box round the standing spot [x, y, z] (air, and the cell above): a 3 x 3 dais one block
 *  high under it with `mark` (the corner's colour) under the spot, corner posts, a beam ring and a hip canopy over it,
 *  a lantern hung over the trainer's head, a rail behind (away from `ring`, the side the ring lies on) and on the side
 *  away from `entry`, where a step leads up. */
export function trainerBox(c, [x, y, z], { ring, entry }, { dais, mark, post, beam, ceiling, rail, step, roof }) {
  c.fill(x - 1, y - 1, z - 1, x + 1, y - 1, z + 1, dais)
  c.set(x, y - 1, z, mark)
  for (const dx of [-1, 1]) for (const dz of [-1, 1]) for (let yy = y; yy <= y + 2; yy++) c.set(x + dx, yy, z + dz, post, { axis: 'y' })
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
    if (dx === 0 && dz === 0) { c.set(x, y + 3, z, ceiling); continue }
    const corner = dx !== 0 && dz !== 0
    c.set(x + dx, y + 3, z + dz, beam, { axis: corner ? 'y' : dx === 0 ? 'x' : 'z' })
  }
  hipCap(c, x, z, 2, y + 3, roof)
  c.set(x, y + 2, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  const [bx, bz] = DIRS[OPP[ring]], [ex, ez] = DIRS[entry], [sx, sz] = DIRS[OPP[entry]]
  c.set(x + bx, y, z + bz, rail)
  c.set(x + sx, y, z + sz, rail)
  stair(c, x + 2 * ex, y - 1, z + 2 * ez, step, OPP[entry])
}

/** bannerMast: a pole from y0 up to `top` at (x, z): a `base` block at y0, `pole` (a fence) above it, a `head` block at
 *  the top (the cell banners are hung on, on its free faces) and a `crown` (a lantern) standing on it when given. */
export function bannerMast(c, [x, z], y0, top, { base, pole, head, crown = null }) {
  c.set(x, y0, z, base)
  for (let y = y0 + 1; y < top; y++) c.set(x, y, z, pole)
  c.set(x, top, z, head, /_(log|wood)$/.test(head) ? { axis: 'y' } : {})
  if (crown) c.set(x, top + 1, z, crown, { hanging: 'false', waterlogged: 'false' })
}
