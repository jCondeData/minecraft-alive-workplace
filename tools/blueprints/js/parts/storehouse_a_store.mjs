// Store parts (Storehouse, Version A, ROADMAP 49): chest racks paired into doubles, the tally desk (the Storehouse
// block between two stacks of chests with its one lantern on top) (the hoist beam is workshop_a_yard's). Every block comes
// from the caller's palette: `store` (a chest id), `job` (the job block), `lamp`.

const CW = { north: 'east', east: 'south', south: 'west', west: 'north' }
const STEP = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }

/** chestRack: chests along a wall, `facing` into the room, at each of `cells` [x, z] on y0..y1 (a rack `high` tall).
 *  Neighbouring cells pair into double chests (the pair's left half first along the facing's clockwise side), a lone
 *  one stays single. Returns the chest cells. */
export function chestRack(c, cells, y0, y1, facing, store) {
  const [cx, cz] = STEP[CW[facing]]
  const key = ([x, z]) => `${x},${z}`
  const set = new Set(cells.map(key))
  const done = new Set()
  const placed = []
  const order = [...cells].sort((a, b) => (a[0] * cx + a[1] * cz) - (b[0] * cx + b[1] * cz)) // left halves first
  for (const [x, z] of order) {
    if (done.has(key([x, z]))) continue
    const mate = [x + cx, z + cz]
    const pair = set.has(key(mate)) && !done.has(key(mate))
    for (let y = y0; y <= y1; y++) {
      c.set(x, y, z, store, { facing, type: pair ? 'left' : 'single', waterlogged: 'false' })
      placed.push([x, y, z])
      if (pair) { c.set(mate[0], y, mate[1], store, { facing, type: 'right', waterlogged: 'false' }); placed.push([mate[0], y, mate[1]]) }
    }
    done.add(key([x, z])); if (pair) done.add(key(mate))
  }
  return placed
}

/** tallyDesk: the job block at [x, y, z] facing `facing`, a single chest either side (along `side`: 'x' or 'z'),
 *  stacked `high` tall, and the lamp standing on the job block between the upper two (so it touches both). */
export function tallyDesk(c, [x, y, z], facing, side, high, { job, store, lamp }) {
  c.set(x, y, z, job, { facing })
  const [dx, dz] = side === 'x' ? [1, 0] : [0, 1]
  for (const s of [-1, 1]) for (let h = 0; h < high; h++)
    c.set(x + s * dx, y + h, z + s * dz, store, { facing, type: 'single', waterlogged: 'false' })
  c.set(x, y + 1, z, lamp, { hanging: 'false', waterlogged: 'false' })
}
