// Fort walls (ROADMAP 49, grown from the Bandit Fort's second round): what makes a palisade read as a fortification
// rather than a fence. A raised wall-walk inside the stakes on posts and knee braces, a roofed fighting stage over it
// (a little gable on the screen and on posts), a corbelled timber box over a door (a bretesche), a pennant on a post and a bedroll.
// Every block comes from the palette passed in; nothing here picks one.
//   wallWalk      a one- or two-wide plank walk at `y` along a list of cells, on posts every `every` cells with knee
//                 braces either side of each post, the space under it left open (the shadow line inside a palisade)
//   fightingStage a roofed stretch of the walk: the stakes behind it cut level as a screen, posts on the walk's inner
//                 edge, and a little gable across the screen and the posts, its ridge over the walk
//   bretesche     a timber box corbelled out over a door: brackets with a murder hole between, a boarded front with
//                 a slit in the middle, and a slab lid
//   pennant       a post standing on a roof's apex, a fence on it and a banner on that
//   bedroll       a sleeping mat: carpets along a line with a pillow (a different carpet) at the head
import { slab, stair } from './canvas.mjs'

const AX = { x: [1, 0], z: [0, 1] }

/** wallWalk: a walk deck (`deck`) at y on every [x, z] of `cells` (the cells in wall order, one line), plus a second
 *  row `wide(x, z)` returns ([x, z] or null) where it widens. Under it, every `every` cells (and at each end) a `post`
 *  (a log, axis y) from y 1 to y - 1, with knee braces (`brace`, upside-down stairs) at y - 1 on its neighbours along
 *  the line (their full half toward the post). Returns the post cells. */
export function wallWalk(c, cells, y, { deck, post, brace }, { every = 4, wide = () => null, along = 'x' } = {}) {
  const posts = []
  cells.forEach(([x, z], i) => {
    c.set(x, y, z, deck)
    const w = wide(x, z)
    if (w) c.set(w[0], y, w[1], deck)
    if (i % every === 0 || i === cells.length - 1) posts.push([x, z])
  })
  const [dx, dz] = AX[along]
  const on = new Set(cells.map(([x, z]) => `${x},${z}`))
  for (const [x, z] of posts) {
    for (let yy = 1; yy < y; yy++) c.set(x, yy, z, post, { axis: 'y' })
    for (const s of [-1, 1]) {
      const bx = x + s * dx, bz = z + s * dz
      if (!on.has(`${bx},${bz}`) || c.get(bx, y - 1, bz)) continue
      const toward = along === 'x' ? (s < 0 ? 'east' : 'west') : (s < 0 ? 'south' : 'north')
      stair(c, bx, y - 1, bz, brace, toward, 'top')
    }
  }
  return posts
}

/** fightingStage: a roofed stretch of a wall-walk on a wall along `along` ('x' or 'z') at line `wall`, from l0 to l1,
 *  the walk two rows deep inward (`inward` +1 or -1) at deckY. The stakes on the wall line are cut level at screenTop
 *  (`screen`, a log) as the screen; posts (`post`) stand on the inner row at each end and every `bay`; over the three
 *  rows a little gable (`roof.stairs` eaves at screenTop + 1 on the screen and on the posts, a `roof.slab` ridge over
 *  the walk), its two ends boarded with `board` under the ridge. Returns the cell under the ridge's middle, where a
 *  lamp can hang. */
export function fightingStage(c, { along, wall, l0, l1, inward, deckY, screenTop, bay = 4 }, { screen, post, roof, board }) {
  const at = (l, d) => (along === 'x' ? [l, wall + inward * d] : [wall + inward * d, l])
  const toInner = along === 'x' ? (inward > 0 ? 'south' : 'north') : (inward > 0 ? 'east' : 'west')
  const toWall = { north: 'south', south: 'north', east: 'west', west: 'east' }[toInner]
  const eave = screenTop + 1
  for (let l = l0; l <= l1; l++) {
    const [wx, wz] = at(l, 0), [mx, mz] = at(l, 1), [px, pz] = at(l, 2)
    for (let y = 1; y <= screenTop; y++) c.set(wx, y, wz, screen, { axis: 'y' })
    stair(c, wx, eave, wz, roof.stairs, toInner)
    stair(c, px, eave, pz, roof.stairs, toWall)
    slab(c, mx, eave + 1, mz, roof.slab)
    if (l === l0 || l === l1) c.set(mx, eave, mz, board)
    if (l === l0 || l === l1 || (l - l0) % bay === 0) for (let y = deckY + 1; y <= screenTop; y++) c.set(px, y, pz, post, { axis: 'y' })
  }
  const mid = Math.floor((l0 + l1) / 2)
  const [hx, hz] = at(mid, 1)
  return { hang: [hx, eave, hz] }
}

/** bretesche: a timber box over a door in a wall along x at row `z`, `out` the row it hangs in (z +- 1), across
 *  x0..x1 (odd, at least 3) from y: corbels (`corbel`, upside-down stairs) under its two ends at y, the middle left
 *  open over the door (the murder hole); at y + 1 `board` with a `slit` (bars) in the middle; at y + 2 a slab lid. */
export function bretesche(c, { x0, x1, z, out, y }, { corbel, board, slit, lid }) {
  const back = out > z ? 'north' : 'south'
  const mid = (x0 + x1) / 2
  for (let x = x0; x <= x1; x++) {
    if (x === x0 || x === x1) stair(c, x, y, out, corbel, back, 'top')
    c.set(x, y + 1, out, x === mid ? slit : board)
    slab(c, x, y + 2, out, lid)
  }
}

/** pennant: on the block at [x, y, z] (a roof's apex), a `post` log, a `fence` on it and a standing `banner` on that
 *  (rotation 0..15). */
export function pennant(c, [x, y, z], { post, fence, banner, rotation = 0 }) {
  c.set(x, y, z, post, { axis: 'y' })
  c.set(x, y + 1, z, fence)
  c.set(x, y + 2, z, banner, { rotation: String(rotation) })
}

/** bedroll: a sleeping mat of `mat` carpets from [x, y, z] `len` cells along `along` (+x, -x, +z or -z), the first
 *  cell (the head) a `pillow` carpet. */
export function bedroll(c, [x, y, z], along, len, { mat, pillow }) {
  const [dx, dz] = { '+x': [1, 0], '-x': [-1, 0], '+z': [0, 1], '-z': [0, -1] }[along]
  for (let i = 0; i < len; i++) c.set(x + dx * i, y, z + dz * i, i === 0 ? pillow : mat)
}
