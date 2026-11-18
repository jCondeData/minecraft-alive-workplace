// Moot-house parts (parts library, tools/blueprints/REBUILD.md; grown from the Village Hall, Version B, 49): an
// octagon stretched along x (a chapter house's plan on a wider-than-deep plot), the walls and tent roof that follow
// its rings, a stone or timber bench round the inside of a wall, a hanging pot, and a gabled bell porch whose bell
// hangs under the front beam. Every block comes from the caller; none is picked here.
import { OPP, stair } from './canvas.mjs'
import { gableRoof } from './roofs.mjs'

/** stretchedLevel: the ring an [x, z] cell lies on in an octagon round (cx, cz) stretched `e` cells along x each way:
 *  max(dx - e, dz, dx - e + dz - cut) (dx - e floored at 0). e = 0 is pavilions.mjs's octLevel. Ring r is the outline
 *  of a (2r + 2e + 1) x (2r + 1) rectangle with its corners cut back by r - cut cells. */
export const stretchedLevel = (cx, cz, e, cut) => (x, z) => {
  const dx = Math.max(0, Math.abs(x - cx) - e), dz = Math.abs(z - cz)
  return Math.max(dx, dz, dx + dz - cut)
}

/** stretchedInward: the way a roof stair on cell [x, z] of a stretched octagon rises (toward its middle line). */
export function stretchedInward(x, z, cx, cz, e) {
  const dx = Math.max(0, Math.abs(x - cx) - e), dz = Math.abs(z - cz)
  if (dz >= dx) return z < cz ? 'south' : 'north'
  return x < cx ? 'east' : 'west'
}

/** octRing: every [x, z] of ring r of a stretched octagon (stretchedLevel), in no particular order. */
export function octRing(cx, cz, e, cut, r) {
  const level = stretchedLevel(cx, cz, e, cut)
  const out = []
  for (let x = cx - r - e; x <= cx + r + e; x++) for (let z = cz - r; z <= cz + r; z++) if (level(x, z) === r) out.push([x, z])
  return out
}

/** ringFace: which face of a stretched octagon a ring cell sits on: 'north', 'south', 'west', 'east' (the side it
 *  looks out to) or 'corner' for a cell of a cut-back diagonal (or a vertex both faces share). */
export function ringFace(x, z, cx, cz, e, cut, r) {
  const dx = Math.max(0, Math.abs(x - cx) - e), dz = Math.abs(z - cz)
  const onZ = dz === r && dx + dz - cut < r, onX = dx === r && dx + dz - cut < r
  if (onZ && !onX) return z < cz ? 'north' : 'south'
  if (onX && !onZ) return x < cx ? 'west' : 'east'
  return 'corner'
}

/** stretchedTent: a tent roof over a stretched octagon up to ring heights.length - 1, each ring a course of stairs
 *  rising toward the middle at heights[ring] (ring 0, the ridge, full blocks); where a ring stands two or more above
 *  the next ring out, full blocks fill under it (a steep course). `roof.kick` (optional) is the outermost ring's stair
 *  (the eave). keep(x, y, z) returns false for cells another part owns. Returns {level, at(x, z)}. */
export function stretchedTent(c, { cx, cz, e = 0, cut, heights }, roof, { keep = () => true } = {}) {
  const level = stretchedLevel(cx, cz, e, cut)
  const R = heights.length - 1
  for (let x = cx - R - e; x <= cx + R + e; x++) for (let z = cz - R; z <= cz + R; z++) {
    const L = level(x, z)
    if (L > R || !c.inside(x, heights[L], z)) continue
    const y = heights[L]
    if (L < R) for (let yy = heights[L + 1] + 1; yy < y; yy++) if (keep(x, yy, z)) c.set(x, yy, z, roof.full)
    if (L === 0) { if (keep(x, y, z)) c.set(x, y, z, roof.full); continue }
    const id = L === R && roof.kick ? roof.kick : roof.stairs
    if (keep(x, y, z)) stair(c, x, y, z, id, stretchedInward(x, z, cx, cz, e))
  }
  return { level, at: (x, z) => heights[level(x, z)] }
}

/** wallBench: a bench of stair seats along a wall on the given [x, z] cells at y, the sitters facing `facing` (the
 *  stair's back against the wall behind them), each seat a 'bench' furniture spot. */
export function wallBench(c, cells, y, facing, seat, wood) {
  for (const [x, z] of cells) {
    c.free([x, y, z])
    stair(c, x, y, z, seat, OPP[facing])
    c.spot('bench', [x, y, z], facing, [[0, 0, 0]], wood)
  }
}

/** hangingPot: a potted plant on a chain from the beam or ceiling above (f.pos is the chain's cell, the pot under
 *  it), a 'hanging_pot' furniture spot (its support the ceiling). f.plant: the potted plant's id. */
export function hangingPot(c, f) {
  const [x, y, z] = f.pos
  c.free([x, y, z], [x, y - 1, z])
  c.set(x, y, z, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(x, y - 1, z, f.plant)
  c.spot('hanging_pot', f.pos, f.facing ?? 'north', [[0, 0, 0], [0, -1, 0]], f.wood)
}

/** bellPorch: an open gabled porch in front of a wall (the porch spans x0..x1 at the posts, z0 its open front, z1
 *  its last cell before the wall): posts at the front corners up to beamY - 1, beams along the front and both sides at
 *  beamY, a 45-degree gable roof (ridge along z) one block wider each side running from z0 back to the wall line
 *  (zWall), knee braces under the front beam beside each post, and a bell hanging from the front beam's middle (the
 *  village's meeting bell, at (mid, beamY - 1, z0)). `keep` as in gableRoof (the roof stops where the house's own
 *  roof is). Returns gableRoof's {ridgeY, under}. With `low`, the roof stays under the house's eave (see below). */
export function bellPorch(c, { x0, x1, z0, z1, zWall, beamY }, { post, roof, brace, keep = () => true, bell = true, low = false }) {
  const mid = (x0 + x1) / 2
  for (const x of [x0, x1]) for (let y = 1; y < beamY; y++) c.set(x, y, z0, post, { axis: 'y' })
  for (let x = x0; x <= x1; x++) c.set(x, beamY, z0, post, { axis: 'x' })
  for (const x of [x0, x1]) for (let z = z0 + 1; z <= z1; z++) c.set(x, beamY, z, post, { axis: 'z' })
  if (brace) {
    stair(c, x0 + 1, beamY - 1, z0, brace, 'west', 'top')
    stair(c, x1 - 1, beamY - 1, z0, brace, 'east', 'top')
  }
  if (bell) c.set(mid, beamY - 1, z0, 'bell', { facing: 'north', attachment: 'ceiling', powered: 'false' })
  if (!low) return gableRoof(c, { axis: 'z', across: [x0 - 1, x1 + 1], along: [z0, zWall], eaveY: beamY, steep: false }, roof, keep)
  // low: a roof that stays under the house's eave: stairs falling off each side from a flat top one above the beams,
  // and a short gablet over the bell at the front (two deep), the porch's own little gable
  const under = {}
  for (let z = z0; z <= z1; z++) for (let x = x0 - 1; x <= x1 + 1; x++) {
    const y = x === x0 - 1 || x === x1 + 1 ? beamY : beamY + 1
    under[x] = y
    if (!keep(x, y, z)) continue
    if (x <= x0) stair(c, x, y, z, roof.stairs, 'east')
    else if (x >= x1) stair(c, x, y, z, roof.stairs, 'west')
    else c.set(x, y, z, roof.full)
  }
  for (let z = z0; z <= z0 + 1; z++) {
    stair(c, mid - 1, beamY + 1, z, roof.stairs, 'east')
    stair(c, mid + 1, beamY + 1, z, roof.stairs, 'west')
    c.set(mid, beamY + 2, z, roof.slab, { type: 'bottom', waterlogged: 'false' })
  }
  return { ridgeY: beamY + 2, under }
}

