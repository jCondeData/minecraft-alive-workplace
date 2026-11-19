// Create rail cars (the Village Train, 49/50.13): a conductor's seat between two controls that face it (Create drives
// the car both ways from it), a cab roof of slabs on posts, a short funnel, and trainRules(), which checks a drawing
// against what Create and CreateTrains need before it is saved. Every block comes from the palette passed in.
//
// The car is drawn along +x (the track), z = 0 is the track line, y = 0 the layer standing on the track.

// The driver's place: the seat at (x, y, z), a controls at x - 1 facing east and one at x + 1 facing west (both face
// the seat, so CarriageContraption.inControl holds from either end). The cell over the seat stays open for his head.
export function conductorSeat(c, x, y, z, P) {
  c.set(x - 1, y, z, P.controls, { facing: 'east', open: 'false', virtual: 'false', waterlogged: 'false' })
  c.set(x, y, z, P.seat, { waterlogged: 'false' })
  c.set(x + 1, y, z, P.controls, { facing: 'west', open: 'false', virtual: 'false', waterlogged: 'false' })
  c.headroom = [...(c.headroom ?? []), [x, y + 1, z]]
}

// A cab roof: a run of slabs from x0 to x1 at y (top slabs sit high in the cell, clear of a seated head below), on
// posts at the listed x from postY up to y - 1.
export function cabRoof(c, x0, x1, y, z, P, { posts = [], postY = y - 1, type = 'top' } = {}) {
  for (const x of posts) for (let py = postY; py < y; py++) c.set(x, py, z, P.post, { waterlogged: 'false' })
  for (let x = x0; x <= x1; x++) c.set(x, y, z, P.roofSlab, { type, waterlogged: 'false' })
}

// A lamp standing on a slab or post at (x, y, z) (the owner's rule 2: a short lamp stands on a slab, a post or hangs).
export function carLamp(c, x, y, z, P, hanging = false) {
  c.set(x, y, z, P.lamp, { hanging: String(hanging), waterlogged: 'false' })
}

// Checks a drawing against the Village Train brief's rules; throws with every broken one.
//   limits: { length, height, width } (cells); container ids that would add storage; the single chest id.
export function trainRules(c, { length = 4, height = 4, width = 1, chest = 'minecraft:chest' } = {}) {
  const errs = []
  const cells = [...c.cells].map(([k, v]) => [k.split(',').map(Number), v.id, v.props])
  const id = s => (s.includes(':') ? s : `minecraft:${s}`)
  const at = (x, y, z) => c.name(x, y, z)
  for (const [[x, y, z], b] of cells)
    if (x < 0 || x >= length || y < 0 || y >= height || z < 0 || z >= width) errs.push(`${b} at ${x},${y},${z} is outside ${length}x${height}x${width}`)
  const bogeys = cells.filter(([, b]) => b.endsWith('bogey'))
  if (bogeys.length !== 1 || bogeys[0][1] !== 'create:small_bogey' || bogeys[0][0].join() !== '0,0,0')
    errs.push(`exactly one create:small_bogey at 0,0,0 (found ${bogeys.map(([p, b]) => b + '@' + p).join(' ') || 'none'})`)
  const seats = cells.filter(([, b]) => /^create:.*_seat$/.test(b))
  if (seats.length !== 1) errs.push(`exactly one seat (found ${seats.length})`)
  for (const [[x, y, z]] of seats) {
    const w = c.get(x - 1, y, z), e = c.get(x + 1, y, z)
    if (w?.id !== 'create:controls' || w.props.facing !== 'east') errs.push('controls at x - 1 facing east')
    if (e?.id !== 'create:controls' || e.props.facing !== 'west') errs.push('controls at x + 1 facing west')
    if (c.get(x, y + 1, z)) errs.push(`the cell over the seat (${x},${y + 1},${z}) must stay open`)
  }
  for (const [p, b, props] of cells) if (b === 'create:controls' && !['east', 'west'].includes(props.facing)) errs.push(`controls at ${p} face across the track`)
  const stores = cells.filter(([, b]) => /chest|barrel|shulker|vault|toolbox|hopper|dropper|dispenser/.test(b))
  if (stores.length !== 1 || stores[0][1] !== id(chest) || (stores[0][2].type ?? 'single') !== 'single')
    errs.push(`exactly one single ${chest} and no other container (found ${stores.map(([, b]) => b).join(', ')})`)
  if (cells.length <= 6) errs.push(`more than 6 blocks (found ${cells.length})`)
  // every block joined to the bogey face to face
  const seen = new Set(['0,0,0']), todo = [[0, 0, 0]]
  while (todo.length) {
    const [x, y, z] = todo.pop()
    for (const [dx, dy, dz] of [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]]) {
      const k = `${x + dx},${y + dy},${z + dz}`
      if (!seen.has(k) && c.cells.has(k)) { seen.add(k); todo.push([x + dx, y + dy, z + dz]) }
    }
  }
  for (const [p, b] of cells) if (!seen.has(p.join())) errs.push(`${b} at ${p} doesn't touch the bogey through the car`)
  if (errs.length) throw new Error('train rules:\n  ' + errs.join('\n  '))
  return { blocks: cells.length, empty: length * height * width - cells.length }
}

// The bill of materials: block id -> count, with the bogey's railway casing counted (a builder places the bogey with one).
export function trainBill(c, placedWith = { 'create:small_bogey': 'create:railway_casing' }) {
  const bill = {}
  for (const { id } of c.cells.values()) {
    const k = placedWith[id] ?? id
    bill[k] = (bill[k] ?? 0) + 1
  }
  return bill
}
