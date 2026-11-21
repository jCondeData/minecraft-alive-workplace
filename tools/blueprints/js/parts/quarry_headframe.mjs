// Headframes and pits (parts library, grown from the Quarry, ROADMAP 49): a timber gallows frame over a shaft or pit,
// its sheave wheel, raked back-struts, a kerb ring with a gap, and a spoil heap. Every block comes from the caller.
import { stair } from './canvas.mjs'

/** headframe: a tapering gallows frame. Lower legs at the corners of `ring` from y0 to the tie ring at tieY; upper
 *  legs at the corners of `head` (stepped in along x, standing on the tie ring's x beams) up to legTop, a stair
 *  shoulder outside each step; the head ring at headY over `head`, knee braces (upside-down stairs) under both rings
 *  beside every leg, and a sheave beam along x at sheaveZ on the head. Nothing below tieY - 1 between the legs. */
export function headframe(c, { y0 = 0, legTop, tieY, headY, ring, head = ring, sheaveZ }, { log, stairs }) {
  const ringBeams = (r, y) => {
    for (let x = r.x0; x <= r.x1; x++) for (const z of [r.z0, r.z1]) c.set(x, y, z, log, { axis: x === r.x0 || x === r.x1 ? 'y' : 'x' })
    for (let z = r.z0 + 1; z < r.z1; z++) for (const x of [r.x0, r.x1]) c.set(x, y, z, log, { axis: 'z' })
    for (const z of [r.z0, r.z1]) { stair(c, r.x0 + 1, y - 1, z, stairs, 'west', 'top'); stair(c, r.x1 - 1, y - 1, z, stairs, 'east', 'top') }
    for (const x of [r.x0, r.x1]) { stair(c, x, y - 1, r.z0 + 1, stairs, 'north', 'top'); stair(c, x, y - 1, r.z1 - 1, stairs, 'south', 'top') }
  }
  for (const x of [ring.x0, ring.x1]) for (const z of [ring.z0, ring.z1]) for (let y = y0; y < tieY; y++) c.set(x, y, z, log, { axis: 'y' })
  ringBeams(ring, tieY)
  for (const x of [head.x0, head.x1]) for (const z of [head.z0, head.z1]) for (let y = tieY + 1; y < headY; y++) c.set(x, y, z, log, { axis: 'y' })
  if (head.x0 > ring.x0) for (const z of [head.z0, head.z1]) stair(c, head.x0 - 1, tieY + 1, z, stairs, 'east')
  if (head.x1 < ring.x1) for (const z of [head.z0, head.z1]) stair(c, head.x1 + 1, tieY + 1, z, stairs, 'west')
  ringBeams(head, headY)
  for (let x = head.x0 + 1; x < head.x1; x++) c.set(x, headY, sheaveZ, log, { axis: 'x' })
}

/** sheave: a spoked wheel standing in the x-y plane at z, 5 wide from (x0, y0): a rim (a log, laid along it) with its corners left off and
 *  four spokes (fences) to a hub (a log along z) at the middle; its rim's foot sits on the beam under it. Nothing is
 *  drawn beside it, so the sky stays open either side (holes.py reads a boxed-in strip of sky as a hole in a roof). */
export function sheave(c, { x0, y0, z }, { rim, spoke, axle, corner = null }) {
  const cx = x0 + 2, cy = y0 + 2
  for (let i = 1; i <= 3; i++) { c.set(x0 + i, y0, z, rim, { axis: 'x' }); c.set(x0 + i, y0 + 4, z, rim, { axis: 'x' }) }
  for (let j = 1; j <= 3; j++) { c.set(x0, y0 + j, z, rim, { axis: 'y' }); c.set(x0 + 4, y0 + j, z, rim, { axis: 'y' }) }
  for (const [x, y] of [[cx, cy - 1], [cx, cy + 1], [cx - 1, cy], [cx + 1, cy]]) c.set(x, y, z, spoke)
  c.set(cx, cy, z, axle, { axis: 'z' })
  // the rim's corners rounded off: stairs with their missing quarter outward (seen in profile from the front and back)
  if (corner) for (const [x, y, f, half] of [[x0, y0, 'east', 'top'], [x0 + 4, y0, 'west', 'top'], [x0, y0 + 4, 'east', 'bottom'], [x0 + 4, y0 + 4, 'west', 'bottom']])
    stair(c, x, y, z, corner, f, half)
}

/** rakedStrut: a back-leg stepping down `steps` cells from (x, y, z) toward `dx` (+1 east, -1 west): a log (along x)
 *  each step with a stair on top rising back toward the frame, so the strut reads as one raking timber. */
export function rakedStrut(c, [x, y, z], steps, dx, { log, stairs }) {
  for (let i = 0; i < steps; i++) {
    c.set(x + dx * i, y - i, z, log, { axis: 'x' })
    stair(c, x + dx * i, y - i + 1, z, stairs, dx > 0 ? 'west' : 'east')
  }
}

/** kerbRing: a stone kerb on the rectangle's edge at y, skipping the `gap` cells ([[x, z], ...]). */
export function kerbRing(c, { x0, x1, z0, z1 }, y, stone, gap = []) {
  const skip = new Set(gap.map(([x, z]) => `${x},${z}`))
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (x !== x0 && x !== x1 && z !== z0 && z !== z1) continue
    if (!skip.has(`${x},${z}`)) c.set(x, y, z, stone)
  }
}

/** spoilHeap: rubble heaped from y over a box, highest along its `ridge` side (z1 or z0) and toward the middle,
 *  each course stepping in; `ores` ([[x, y, z, id], ...]) set into it. */
export function spoilHeap(c, { x0, x1, z0, z1 }, y, rubble, ores = [], ridge = 'z1') {
  const courses = [[x0, x1, z0, z1], [x0 + 1, x1 - 2, ridge === 'z1' ? z0 + 1 : z0, ridge === 'z1' ? z1 : z1 - 1],
    [x0 + 2, x1 - 4, ridge === 'z1' ? z1 : z0, ridge === 'z1' ? z1 : z0]]
  courses.forEach(([a0, a1, b0, b1], i) => {
    for (let x = a0; x <= a1; x++) for (let z = b0; z <= b1; z++) c.set(x, y + i, z, rubble)
  })
  for (const [x, yy, z, id] of ores) c.set(x, yy, z, id)
}
