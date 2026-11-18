// Warehouse parts (parts library, tools/blueprints/REBUILD.md), grown from the Canal Warehouse storehouse (49, Version
// B): chests paired the way the game pairs them, a counting desk (a job block under its lantern between two chest
// stacks), a crow-stepped gable and a hoist arm over a stack of loading doors. Every block comes from the caller.
import { DIRS } from './canvas.mjs'

const CW = { north: 'east', east: 'south', south: 'west', west: 'north' } // a facing turned clockwise

/** chestStack: plain chests (`id`, default minecraft:chest) facing `facing`, at the cells [x, z] of `cells` and every
 *  y of `ys`. Two neighbouring cells along the wall become one double chest (the game's left/right halves), a lone
 *  cell a single chest; pass cells in pairs along the wall. */
export function chestStack(c, cells, ys, facing, id = 'chest') {
  const [lx, lz] = DIRS[CW[facing]] // the side a 'left' half's partner is on
  const has = new Set(cells.map(([x, z]) => `${x},${z}`))
  for (const [x, z] of cells) {
    const left = has.has(`${x + lx},${z + lz}`)
    const right = has.has(`${x - lx},${z - lz}`)
    const type = left ? 'left' : right ? 'right' : 'single'
    for (const y of ys) c.set(x, y, z, id, { facing, type, waterlogged: 'false' })
  }
}

/** countingDesk: the job block (`block`, facing `facing`) at [x, y, z] against a wall, a double chest stacked two high
 *  either side of it along the wall (`along`: 'x' or 'z'), and one lantern standing on the block, touching the upper
 *  chests: the store's head, where the board is read. */
export function countingDesk(c, [x, y, z], facing, along, block, lantern = 'lantern') {
  c.set(x, y, z, block, { facing })
  const cells = along === 'z'
    ? [[x, z - 2], [x, z - 1], [x, z + 1], [x, z + 2]]
    : [[x - 2, z], [x - 1, z], [x + 1, z], [x + 2, z]]
  chestStack(c, cells, [y, y + 1], facing)
  c.set(x, y + 1, z, lantern, { hanging: 'false', waterlogged: 'false' })
}

/** crowSteps: a stepped gable wall on line z = `line` across x `x0..x1` (odd width, centre m), from y0 up to
 *  tops(d) at each column d from the centre, each step capped with `cap` (a slab id, bottom half) one higher.
 *  `pick(x, y)` chooses the wall block (an id, [id, props], or null to leave an opening). */
export function crowSteps(c, line, x0, x1, y0, tops, pick, cap) {
  const m = (x0 + x1) / 2
  for (let x = x0; x <= x1; x++) {
    const top = tops(Math.abs(x - m))
    for (let y = y0; y <= top; y++) {
      const b = pick(x, y)
      if (!b) continue
      const [name, props] = Array.isArray(b) ? b : [b, {}]
      c.set(x, y, line, name, props)
    }
    c.set(x, top + 1, line, cap, { type: 'bottom', waterlogged: 'false' })
  }
}

/** hoistArm: a beam (`log`) out of a gable at [x, y, z] pointing `out` one block, a `chain` of `drop` links under its
 *  tip and a lantern hanging from the last: the warehouse's crane over its loading doors. */
export function hoistArm(c, [x, y, z], out, log, drop = 2) {
  const [ox, oz] = DIRS[out]
  const axis = ox ? 'x' : 'z'
  c.set(x + ox, y, z + oz, log, { axis })
  for (let i = 1; i <= drop; i++) c.set(x + ox, y - i, z + oz, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(x + ox, y - drop - 1, z + oz, 'lantern', { hanging: 'true', waterlogged: 'false' })
}
