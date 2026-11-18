// Tents and wayfarers' gear (parts library, tools/blueprints/REBUILD.md; grown from the Traveler's Camp, 49): a conical
// hide tent (a tipi) stepped in round tiers with hems (carpets or inward stairs) softening each step and its poles standing out of the
// smoke hole, and a waymark cairn (stones with a cap block). Every block comes from the caller's palette; none is
// picked here.
import { discCells } from './fires.mjs'
import { DIRS, slab, stair } from './canvas.mjs'

const N4 = [[1, 0], [-1, 0], [0, 1], [0, -1]]
const keyOf = ([x, z]) => `${x},${z}`

/** discEdge: the edge of the disc about (cx, cz) of radius² r2: every disc cell with a side open to the outside. */
export function discEdge(cx, cz, r2) {
  const cells = discCells(cx, cz, r2)
  const inDisc = new Set(cells.map(keyOf))
  return cells.filter(([x, z]) => N4.some(([dx, dz]) => !inDisc.has(`${x + dx},${z + dz}`)))
}

/** coneTent: a tipi about (cx, cz) from y0. `tiers` lists the rings from the ground up, each {r2, height}: a ring of
 *  hide (the disc's edge) `height` tall, each tier stepping in from the last; the last tier is usually r2 0 (the
 *  peak's single column). `hide(tier, x, y, z)` returns each cell's block (paint bands and marks there); `hem(tier,
 *  x, z)` (optional) the block on the ledge each tier leaves outside the next one: a carpet, or with `hemStairs` a
 *  stair climbing toward the peak (its high back to the middle), so the steps read as one slope.
 *  `door`: the side ('north', ...) whose middle cell of the bottom ring is left open `doorHeight` high (the hide above
 *  it is the lintel). `pole`: the fence standing on the last ledge at the peak's four sides, `poleHeight` tall (the
 *  tipi's poles out of the smoke hole), each beside the peak so it is lashed to it at the foot; `poleHeights` (four
 *  numbers, optional) staggers them, a ragged bundle rather than a fork. Returns {inside: the ground ring's inner
 *  cells [x, z], door: [x, y0, z] or null, topY: the highest y drawn}. */
export function coneTent(c, { cx, cz, y0, tiers, door = null, doorHeight = 2, poleHeight = 3, poleHeights = null, hemStairs = false }, { hide, hem = null, pole = null }) {
  const inward = (x, z) => {
    const dx = cx - x, dz = cz - z
    return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 'east' : 'west') : (dz > 0 ? 'south' : 'north')
  }
  let doorCell = null
  if (door) {
    const [dx, dz] = DIRS[door]
    const reach = Math.floor(Math.sqrt(tiers[0].r2))
    doorCell = [cx + dx * reach, cz + dz * reach]
  }
  let y = y0
  let topY = y0
  tiers.forEach((t, i) => {
    const ring = discEdge(cx, cz, t.r2)
    for (let k = 0; k < t.height; k++) for (const [x, z] of ring) {
      if (i === 0 && doorCell && x === doorCell[0] && z === doorCell[1] && k < doorHeight) continue
      c.set(x, y + k, z, hide(i, x, y + k, z))
    }
    y += t.height
    topY = y - 1
    const next = tiers[i + 1]
    if (hem && next) {
      const inNext = new Set(discCells(cx, cz, next.r2).map(keyOf))
      for (const [x, z] of ring) {
        if (inNext.has(keyOf([x, z]))) continue
        if (hemStairs) stair(c, x, y, z, hem(i, x, z), inward(x, z))
        else c.set(x, y, z, hem(i, x, z))
        topY = Math.max(topY, y)
      }
    }
  })
  if (pole && tiers.length > 1) {
    const peakY = y0 + tiers.slice(0, -1).reduce((a, t) => a + t.height, 0)
    N4.forEach(([dx, dz], i) => {
      const tall = poleHeights ? poleHeights[i] : poleHeight
      for (let k = 1; k <= tall; k++) c.set(cx + dx, peakY + k, cz + dz, pole)
    })
    topY = Math.max(topY, peakY + Math.max(poleHeight, ...(poleHeights ?? [0])))
  }
  const edge = new Set(discEdge(cx, cz, tiers[0].r2).map(keyOf))
  const inner = discCells(cx, cz, tiers[0].r2).filter(cell => !edge.has(keyOf(cell)))
  return { inside: inner, door: doorCell ? [doorCell[0], y0, doorCell[1]] : null, topY }
}

/** waymark: a wayfarer's cairn at [x, y, z]: `stones` (a block or a mix()) `height` high with `cap` on top (a
 *  lodestone, a lantern's stone...), and `skirt` slabs ({at: [dx, dz], slab}) fallen round its foot. */
export function waymark(c, [x, y, z], { height = 1, stones, cap, skirt = [] }) {
  for (let k = 0; k < height; k++) c.set(x, y + k, z, stones)
  if (cap) c.set(x, y + height, z, cap)
  for (const s of skirt) slab(c, x + s.at[0], y, z + s.at[1], s.slab)
}
