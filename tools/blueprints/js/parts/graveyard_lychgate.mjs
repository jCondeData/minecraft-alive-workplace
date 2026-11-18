// Churchyard parts (parts library, tools/blueprints/REBUILD.md; grown from the Graveyard, the Lantern Yard, 49): a
// low coped yard wall with piers, a roofed lychgate on two piers, a parapet gable that stands up past the roof behind
// it with a stair coping, a "lantern of the dead" (a slim stone shaft with an open lantern chamber and a pyramid cap),
// pews along a wall, and a columnar (Irish) yew. Every block comes from the caller's palette; none is picked here.
import { slab, stair } from './canvas.mjs'

/** yardWall: a churchyard wall. `runs` are straight segments [[ax, az], [bx, bz]] drawn as `rubble` (a block or a
 *  mix()) at y0 with a `coping` bottom slab on top (y0 + 1); `piers` are [x, z] cells drawn `pier` from y0 to
 *  y0 + pierTop - 1 and capped with `pierCap` (a bottom slab) when given. Piers are drawn last, so they own the
 *  corners: a run never turns a corner without its post. */
export function yardWall(c, { runs, piers, y0 = 0, pierTop = 2 }, { rubble, coping, pier, pierCap = null }) {
  for (const [[ax, az], [bx, bz]] of runs)
    for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++)
      for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++) {
        c.set(x, y0, z, rubble)
        slab(c, x, y0 + 1, z, coping)
      }
  for (const [x, z] of piers) {
    for (let y = y0; y < y0 + pierTop; y++) c.set(x, y, z, pier)
    if (pierCap) slab(c, x, y0 + pierTop, z, pierCap)
  }
}

/** lychgate: the roofed gate of a churchyard, where the bier rests on the way in. Two posts (`post`, a log) on the
 *  gate's piers at x0 and x1 on the wall line z0, from postY0 up to beamY - 1; a tie beam (`beam`, a log along x)
 *  at beamY on z0 and on each line back to z1 (carried on knee braces, `brace` stairs upside down, against the
 *  posts: nothing stands in the yard behind the wall), and under the front beam beside the posts when `frontBraces`
 *  (they narrow a low gate's headroom to its middle); a 45-degree gable roof (`roof`, {full, stairs}) ridged along z
 *  with a block of eave either side; each gable end a king post, infilled (`infill`) round it when given (null: an
 *  open truss, lighter, the roof's underside seen through it); `lamp` (an id) hung at
 *  each [x, z] of `lamps` a block under the beam's level: from the beam, or from the eave's stair just outside a post. Returns the roof's columns' undersides. */
export function lychgate(c, { x0, x1, z0, z1, postY0, beamY }, { post, beam, brace, roof, infill, kingPost = post, frontBraces = true, lamp = 'lantern', lamps = [] }) {
  const mid = (x0 + x1) / 2
  for (const x of [x0, x1]) for (let y = postY0; y < beamY; y++) c.set(x, y, z0, post, { axis: 'y' })
  for (let z = z0; z <= z1; z++) for (let x = x0; x <= x1; x++) c.set(x, beamY, z, beam, { axis: 'x' })
  // knee braces: under the front beam beside each post, and under each back beam's ends against the posts
  if (frontBraces) {
    stair(c, x0 + 1, beamY - 1, z0, brace, 'west', 'top')
    stair(c, x1 - 1, beamY - 1, z0, brace, 'east', 'top')
  }
  for (let z = z0 + 1; z <= z1; z++) for (const x of [x0, x1]) stair(c, x, beamY - 1, z, brace, 'north', 'top')
  // the roof: eaves a block out past the posts, rising a block a column to the ridge over the gate's middle
  const under = {}
  for (let x = x0 - 1; x <= x1 + 1; x++) {
    const d = Math.abs(x - mid)
    const y = beamY + (mid - x0 + 1) - d
    for (let z = z0; z <= z1; z++) {
      if (d === 0) c.set(x, y, z, roof.full)
      else stair(c, x, y, z, roof.stairs, x < mid ? 'east' : 'west')
    }
    under[x] = y
  }
  // the gable ends: infill over the beam, a king post up the middle
  for (const z of [z0, z1]) for (let x = x0 + 1; x <= x1 - 1; x++) for (let y = beamY + 1; y < under[x]; y++) {
    if (x === mid) c.set(x, y, z, kingPost, { axis: 'y' })
    else if (infill) c.set(x, y, z, infill)
  }
  for (const [x, z] of lamps) c.set(x, beamY - 1, z, lamp, { hanging: 'true', waterlogged: 'false' })
  return under
}

/** parapetGable: a gable wall in the plane `line` (axis 'z': columns are x at z = line) that stands a block up past
 *  the steep roof behind it: each column u from y0 up to tops[u] (the roof's stair height there) in `wall` (may be a
 *  mix()), then a `coping` stair rising toward the middle; the middle column takes a full `apex` block over its top and
 *  a `finial` over that when given. `pick(u, y)` returns another block (an id or [id, props]) for a window or a
 *  dressing, null for air, undefined for the wall. */
export function parapetGable(c, axis, line, [a0, a1], y0, tops, wall, coping, { apex, finial = null, pick = () => undefined }) {
  const mid = (a0 + a1) / 2
  for (let u = a0; u <= a1; u++) {
    const [x, z] = axis === 'z' ? [u, line] : [line, u]
    for (let y = y0; y <= tops[u]; y++) {
      const got = pick(u, y)
      if (got === null) { c.clear(x, y, z); continue }
      const [id, props] = Array.isArray(got) ? got : [got ?? wall, {}]
      c.set(x, y, z, id, props)
    }
    if (u === mid) {
      c.set(x, tops[u] + 1, z, apex)
      if (finial) c.set(x, tops[u] + 2, z, finial)
    } else {
      const up = u < mid ? (axis === 'z' ? 'east' : 'south') : (axis === 'z' ? 'west' : 'north')
      stair(c, x, tops[u] + 1, z, coping, up)
    }
  }
}

/** lanternTower: a "lantern of the dead", the slim stone tower of an old churchyard whose light burns for the dead.
 *  A 3 x 3 shaft at x0..x0+2, z0..z0+2 from y0 to shaftTop (`shaft`, may be a mix()) left solid but for what the
 *  caller cuts; over it a chamber two high, its four corners piers (`pier`), each face named in `open` ('north',
 *  'south', 'west', 'east') left open, a `lamp` hung in the middle from the cap; the cap a ring of `cap` stairs round
 *  a full block, a `crown` block over that and a `finial` on top. Returns the top's y. */
export function lanternTower(c, { x0, z0, y0, shaftTop, open = ['north', 'west', 'east', 'south'] }, { shaft, pier, cap, crown, finial = null, lamp = 'lantern' }) {
  const cx = x0 + 1, cz = z0 + 1
  for (let y = y0; y <= shaftTop; y++) for (let dx = 0; dx < 3; dx++) for (let dz = 0; dz < 3; dz++) c.set(x0 + dx, y, z0 + dz, shaft)
  const ch = shaftTop + 1
  const face = { north: [cx, z0], south: [cx, z0 + 2], west: [x0, cz], east: [x0 + 2, cz] }
  for (let y = ch; y <= ch + 1; y++) {
    for (const dx of [0, 2]) for (const dz of [0, 2]) c.set(x0 + dx, y, z0 + dz, pier)
    for (const [side, [x, z]] of Object.entries(face)) if (!open.includes(side)) c.set(x, y, z, shaft)
  }
  const top = ch + 2
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
    if (!dx && !dz) { c.set(cx, top, cz, cap.full); continue }
    const facing = dz === -1 ? 'south' : dz === 1 ? 'north' : dx === -1 ? 'east' : 'west'
    stair(c, cx + dx, top, cz + dz, cap.stairs, facing)
  }
  c.set(cx, ch + 1, cz, lamp, { hanging: 'true', waterlogged: 'false' })
  c.set(cx, top + 1, cz, crown)
  if (finial) { c.set(cx, top + 2, cz, finial); return top + 2 }
  return top + 1
}

/** pewRun: stair seats at the given [x, y, z] cells, the sitters facing `facing`, each recorded as a 'bench' spot. */
export function pewRun(c, cells, facing, wood) {
  const back = { north: 'south', south: 'north', west: 'east', east: 'west' }[facing]
  for (const [x, y, z] of cells) {
    c.free([x, y, z])
    stair(c, x, y, z, `${wood}_stairs`, back)
    c.spot('bench', [x, y, z], facing, [[0, 0, 0]], wood)
  }
}

/** columnarYew: an Irish yew, the slim dark tree of old churchyards: a trunk (`log`) at [tx, tz] from y0 to
 *  trunkTop, and a clipped column of leaves (`leaf`, persistent) over the box x0..x1, z0..z1 from leafY0 to top, its
 *  corners left off on the lowest and the top two layers so it reads round, a single tip over the trunk at top + 1. */
export function columnarYew(c, { x0, x1, z0, z1, tx, tz, y0 = 0, trunkTop, leafY0, top }, log, leaf) {
  for (let y = y0; y <= trunkTop; y++) c.set(tx, y, tz, log, { axis: 'y' })
  const corner = (x, z) => (x === x0 || x === x1) && (z === z0 || z === z1)
  const narrow = (x, z) => x === x0 || x === x1 || z === z0 || z === z1
  for (let y = leafY0; y <= top; y++) for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (c.get(x, y, z)) continue
    if ((y === leafY0 || y >= top - 1) && corner(x, z)) continue
    if (y === top && narrow(x, z) && x1 - x0 > 1 && z1 - z0 > 1) continue
    c.set(x, y, z, leaf, { persistent: 'true', distance: '1', waterlogged: 'false' })
  }
  if (!c.get(tx, top + 1, tz)) c.set(tx, top + 1, tz, leaf, { persistent: 'true', distance: '1', waterlogged: 'false' })
}
