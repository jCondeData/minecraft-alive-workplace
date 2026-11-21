// Eggs and nests (parts library, tools/blueprints/REBUILD.md): a house whose body is an egg standing broad end down,
// its shell drawn as a voxel solid of revolution, a zigzag "hatching crack" of glass round it, spots on the shell in
// clusters, and a nest of laid logs and straw round its foot that two eggs can share. Grown from the Daycare (the
// Clutch, 49). Every block comes from the family's palette; none is picked here.
/** eggSolid: the inside test of an egg standing on its broad end, centred on (cx, cz): widest (radius `r`) at height
 *  `yw`, its lower half an ellipse of semi-axis `lo` (rounder), its upper half one of semi-axis `hi` (longer: the
 *  pointed end up). Layers under y 0 are cut off (the egg sits sunk in its nest). Returns inside(x, y, z), the radius
 *  at y (`at(y)`), and the top layer's y. */
export function eggSolid({ cx, cz, r, yw, lo, hi, crown = 1.2 }) {
  const at = y => {
    const dt = y - yw, s = dt < 0 ? lo : hi
    return Math.abs(dt) > s ? -1 : r * Math.sqrt(1 - (dt / s) ** 2)
  }
  // a layer narrower than `crown` is left off, so the egg ends in a small round cap, not a lone block like a stalk
  const inside = (x, y, z) => {
    if (y < 0) return false
    const rr = at(y)
    return rr >= crown && (x - cx) ** 2 + (z - cz) ** 2 <= rr * rr
  }
  let top = 0
  while (at(top + 1) >= crown) top++
  return { inside, at, top, cx, cz, r }
}

/** eggShell: the egg's skin, one block thick: every inside cell with a side (or the cell above) outside, from y 1 up,
 *  in `shell` (a block or a function of (x, y, z)); y 0 inside the egg is its floor (`floorId`, or a function of
 *  (x, z) giving an id or [id, props]).
 *  `keep(x, y, z)` false leaves a cell (a doorway, a porch). Returns {shellCells, roomCells}: the skin and the air
 *  inside it (y >= 1). */
export function eggShell(c, egg, shell, floorId, keep = () => true) {
  const { inside, cx, cz } = egg
  const R = Math.ceil(egg.r) + 1
  const shellCells = [], roomCells = []
  for (let y = 0; y <= egg.top + 1; y++)
    for (let x = cx - R; x <= cx + R; x++) for (let z = cz - R; z <= cz + R; z++) {
      if (!inside(x, y, z)) continue
      if (y === 0) {
        if (!keep(x, y, z)) continue
        const f = typeof floorId === 'function' ? floorId(x, z) : floorId
        const [id, props] = Array.isArray(f) ? f : [f, {}]
        c.set(x, 0, z, id, props); continue
      }
      const skin = [[1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1], [0, 1, 0]].some(([a, b, d]) => !inside(x + a, y + b, z + d))
      if (!skin) { roomCells.push([x, y, z]); continue }
      shellCells.push([x, y, z])
      if (keep(x, y, z)) c.set(x, y, z, shell)
    }
  // a niche one cell deep where the ring steps (an air cell with one open side or none) is filled as skin
  const room = new Set(roomCells.map(p => p.join(',')))
  const niches = roomCells.filter(([x, y, z]) => [[1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1], [0, 1, 0], [0, -1, 0]]
    .filter(([a, b, d]) => room.has([x + a, y + b, z + d].join(','))).length <= 1 && y > 1)
  for (const [x, y, z] of niches) { shellCells.push([x, y, z]); if (keep(x, y, z)) c.set(x, y, z, shell) }
  const nicheSet = new Set(niches.map(p => p.join(',')))
  return { shellCells, roomCells: roomCells.filter(p => !nicheSet.has(p.join(','))) }
}

/** spotted: a shell block with spots of `spot` in clusters (cells of a coarse `size` grid picked by a hash with chance
 *  `share`), so the spots read as blotches on an eggshell, not single-block confetti; none at or over `below`. */
export function spotted(base, spot, { size = 2, share = 0.2, seed = 7, below = Infinity } = {}) {
  return (x, y, z) => {
    if (y >= below) return base // a clean crown
    const gx = Math.floor((x + seed) / size), gy = Math.floor((y + 2 * seed) / size), gz = Math.floor((z + 3 * seed) / size)
    let h = (gx * 73856093) ^ (gy * 19349663) ^ (gz * 83492791) ^ (seed * 2654435761)
    h = Math.abs((h ^ (h >>> 13)) * 1274126177) % 1000
    return h < share * 1000 ? spot : base
  }
}

/** hatchCrack: a zigzag crack of `glass` round an egg's shell: in each column of the skin the `band` cells from
 *  y0 + t up turn to glass, t rising and falling 0..amp `teeth` times round the egg (a triangle wave of the angle), so
 *  the band reads as a crack and lights the room inside. `skip(x, y, z)` keeps a cell shell (behind a porch roof).
 *  Returns the glass cells. */
export function hatchCrack(c, egg, shellCells, y0, { glass, teeth = 5, amp = 2, band = 2, phase = 0, skip = () => false }) {
  const out = []
  for (const [x, y, z] of shellCells) {
    const a = (Math.atan2(z - egg.cz, x - egg.cx) / (2 * Math.PI) + 1 + phase) % 1
    const f = (a * teeth) % 1
    const t = Math.round(amp * (1 - Math.abs(2 * f - 1)))
    if (y < y0 + t || y > y0 + t + band - 1 || skip(x, y, z)) continue
    if (!c.get(x, y, z)) continue
    c.set(x, y, z, glass)
    out.push([x, y, z])
  }
  return out
}

/** nest: a ring of laid logs and straw round one or more eggs' feet. A cell (x, y, z) outside every egg is nest when
 *  it lies within `reach[y]` of some egg's axis and that egg's skin is no further than `reach[y]` - `hug` from it
 *  (so the ring hugs each egg and shares the ground between two). `pick(x, y, z, axis, rim)` gives the block, `axis`
 *  the way a log laid round the egg there runs ('x' or 'z'), `rim` true on the ring's outer block; `keep(x, y, z)` false leaves a gap (a path, a porch).
 *  Returns the nest's cells. */
export function nest(c, eggs, reach, pick, keep = () => true) {
  const cells = []
  const out = e => Math.ceil(e.r + reach[0]) + 1
  const x0 = Math.min(...eggs.map(e => e.cx - out(e))), x1 = Math.max(...eggs.map(e => e.cx + out(e)))
  const z0 = Math.min(...eggs.map(e => e.cz - out(e))), z1 = Math.max(...eggs.map(e => e.cz + out(e)))
  for (let y = 0; y < reach.length; y++) for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (!c.inside(x, y, z) || eggs.some(e => e.inside(x, y, z)) || !keep(x, y, z)) continue
    const near = eggs.find(e => (x - e.cx) ** 2 + (z - e.cz) ** 2 <= (e.r + reach[y]) ** 2)
    if (!near) continue
    if (y > 0 && !c.get(x, y - 1, z)) continue // the ring steps in as it rises: nothing on air
    const axis = Math.abs(x - near.cx) > Math.abs(z - near.cz) ? 'z' : 'x'
    const rim = eggs.every(e => (x - e.cx) ** 2 + (z - e.cz) ** 2 > (e.r + reach[y] - 1) ** 2)
    const got = pick(x, y, z, axis, rim)
    if (!got) continue
    const [id, props] = Array.isArray(got) ? got : [got, {}]
    c.set(x, y, z, id, props)
    cells.push([x, y, z])
  }
  return cells
}

/** eggDoorway: opens an egg's skin behind a porch: clears the cells of `shellCells` in the box x0..x1, y0..y1,
 *  z0..z1 (the porch's inside carried into the egg), and lays `floorId` under them at y 0. */
export function eggDoorway(c, shellCells, [x0, y0, z0, x1, y1, z1], floorId) {
  for (const [x, y, z] of shellCells)
    if (x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1) { c.clear(x, y, z); c.set(x, 0, z, floorId) }
}

/** apexChain: the chain a lantern hung at (x, y, z) needs to reach the first drawn cell over it (an egg's crown):
 *  the chain length for furnitureParts.hangingLamp. */
export function apexChain(c, x, y, z) {
  let top = y + 1
  while (!c.get(x, top, z)) top++
  return top - y - 1
}

/** shellLip: the broken edge of an egg's cap standing out a block where it meets the body: round the shell, in each
 *  column of the skin at the zigzag line y0 + t (t rising and falling 0..amp `teeth` times round the egg, as in
 *  hatchCrack), a block of `lip` (a block or a function) on the skin's outer side (the way the column faces, along
 *  its longer offset), wherever that cell is outside the egg and free. `skip(x, y, z)` leaves a column (behind a
 *  porch). Returns the lip's cells. */
export function shellLip(c, egg, shellCells, y0, { lip, teeth = 5, amp = 2, phase = 0, skip = () => false }) {
  const out = []
  for (const [x, y, z] of shellCells) {
    const a = (Math.atan2(z - egg.cz, x - egg.cx) / (2 * Math.PI) + 1 + phase) % 1
    const t = Math.round(amp * (1 - Math.abs(2 * ((a * teeth) % 1) - 1)))
    if (y !== y0 + t || skip(x, y, z) || !c.get(x, y, z)) continue
    const dx = x - egg.cx, dz = z - egg.cz
    const [ox, oz] = Math.abs(dx) >= Math.abs(dz) ? [Math.sign(dx), 0] : [0, Math.sign(dz)]
    const [lx, lz] = [x + ox, z + oz]
    if (!c.inside(lx, y, lz) || egg.inside(lx, y, lz) || c.get(lx, y, lz)) continue
    c.set(lx, y, lz, typeof lip === 'function' ? lip(lx, y, lz) : lip)
    out.push([lx, y, lz])
  }
  return out
}

/** eggWindow: a window set a block into an egg's skin on one of its four quarters (`side`: 'north', 'south', 'east',
 *  'west'), `rows` tall from y0: on the skin's plane the opening is left open between `frame` logs (jambs), a `frame`
 *  head and a sill of `sill` (an upside-down stair one block out); one block in, `glass` (a pane) between reveals of
 *  `reveal` planks with a head and an inner sill. The skin there must be flat across three (a fractional radius gives
 *  that at each quarter). Returns the glass cells. */
export function eggWindow(c, egg, side, y0, rows, { glass, frame, reveal, sill }) {
  const [ox, oz] = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[side]
  const [lx, lz] = [oz !== 0 ? 1 : 0, ox !== 0 ? 1 : 0] // along the face
  const k = Math.round(Math.min(...Array.from({ length: rows }, (_, i) => egg.at(y0 + i))) + 1e-9) // the skin's reach
  const at = (d, u, y) => [egg.cx + ox * d + lx * u, y, egg.cz + oz * d + lz * u]
  const out = []
  for (let y = y0 - 1; y <= y0 + rows; y++) {
    const inRow = y >= y0 && y < y0 + rows
    if (inRow) {
      c.clear(...at(k, 0, y))
      c.set(...at(k - 1, 0, y), glass); out.push(at(k - 1, 0, y))
      for (const u of [-1, 1]) { c.set(...at(k, u, y), frame, { axis: 'y' }); c.set(...at(k - 1, u, y), reveal) }
    } else {
      c.set(...at(k, 0, y), frame, { axis: lx ? 'x' : 'z' })
      for (const u of [-1, 0, 1]) c.set(...at(k - 1, u, y), reveal) // the reveal's sill and head run under the jambs too
    }
  }
  const facing = { north: 'south', south: 'north', west: 'east', east: 'west' }[side]
  c.set(...at(k + 1, 0, y0 - 1), sill, { facing, half: 'top', shape: 'straight', waterlogged: 'false' })
  return out
}
