// Hot-country merchant houses (parts library, tools/blueprints/REBUILD.md): the pieces the Burgher's Townhouse's desert
// and savanna looks (49.10b) are known by, each usable by any hot-climate build. Every block comes from the caller's
// palette.
//   rawshan     a timber lattice front (a mashrabiya run the width of a storey) out over the street on corbels: posts on
//               the bay lines, open lattice in front of the windows, a sill course and a little sloped hood
//   qaaLantern  a raised clerestory room on a flat roof: a ring of wall over an opening in the deck, panes in it, its own
//               flat ceiling and a coping (the lantern over a merchant's top-floor hall)
//   badgirHead  the slotted head of a big windcatcher on a square tower: fins and tall slots on every face round a solid
//               core, a flat cap with a projecting cornice
//   datePalm    a date palm in its own soil: a straight trunk and a crown of fronds drooping at their tips
//   acaciaTree  a flat-topped acacia in its own soil: a leaning trunk, a branch, two flat layers of crown
//   slabPent    a low veranda roof of slabs stepping up from its eave to a wall (half a block a row)
//   verandaEave a veranda's thatch eave on its posts: a sloped course out front, a full course behind it under the
//               storey above, a return at each open end
import { slab, stair, trapdoor } from './canvas.mjs'

const leafProps = { persistent: 'true', distance: '1', waterlogged: 'false' }

/** rawshan: a lattice front on line z (the wall at z + 1 behind it, facing north) over x0..x1, from y0 (its sill course
 *  of `sill` top slabs, with `corbel` stairs under each post) up to y1 (a hood of `roof` stairs, back to the wall):
 *  `post` logs at the x of `posts`, open `lattice` trapdoors (their panel on the outer face) in every other cell. */
export function rawshan(c, { x0, x1, z, y0, y1, posts }, { post, lattice, sill, roof, corbel }) {
  for (let x = x0; x <= x1; x++) {
    slab(c, x, y0, z, sill, 'top')
    for (let y = y0 + 1; y < y1; y++) {
      if (posts.includes(x)) c.set(x, y, z, post, { axis: 'y' })
      else trapdoor(c, x, y, z, lattice, 'south', 'bottom', true)
    }
    stair(c, x, y1, z, roof, 'south')
  }
  if (corbel) for (const x of posts) if (!c.get(x, y0 - 1, z)) stair(c, x, y0 - 1, z, corbel, 'south', 'top')
}

/** qaaLantern: a raised clerestory over the room under a flat deck: a wall ring x0..x1, z0..z1 from y0 to y1 (`wall`,
 *  may be a mix()), `glass` panes from y0 to y1 at each [x, z] of `windows` (cells on the ring), the ceiling inside at
 *  y1 + 1 (`ceiling`) and a full coping ring round it (`coping`). The deck inside the ring is the caller's to leave
 *  open (the room rises into the lantern). */
export function qaaLantern(c, { x0, z0, x1, z1, y0, y1 }, { wall, coping, ceiling, glass, windows = [] }) {
  const win = (x, z) => windows.some(([wx, wz]) => wx === x && wz === z)
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    if (!edge) { c.set(x, y1 + 1, z, ceiling); continue }
    for (let y = y0; y <= y1; y++) c.set(x, y, z, win(x, z) ? glass : wall)
    c.set(x, y1 + 1, z, coping)
  }
}

/** badgirHead: the head of a windcatcher on a tower x0..x1, z0..z1 (odd sides), y0..y1: on every face the cells at an
 *  odd step from the corner are tall open slots, the rest fins of `fin`; a solid core of `body` behind them; a flat cap
 *  of `cap` at y1 + 1 and a cornice of upside-down `cornice` stairs a block out round it at the same course. */
export function badgirHead(c, { x0, z0, x1, z1, y0, y1 }, { body, fin, cap, cornice }) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edgeX = x === x0 || x === x1, edgeZ = z === z0 || z === z1
    for (let y = y0; y <= y1; y++) {
      if (!edgeX && !edgeZ) { c.set(x, y, z, body); continue }
      const step = edgeZ && !edgeX ? x - x0 : !edgeZ && edgeX ? z - z0 : 0
      if (step % 2 === 1) c.clear(x, y, z)
      else c.set(x, y, z, fin)
    }
    c.set(x, y1 + 1, z, cap)
  }
  if (!cornice) return
  for (let x = x0; x <= x1; x++) { stair(c, x, y1 + 1, z0 - 1, cornice, 'south', 'top'); stair(c, x, y1 + 1, z1 + 1, cornice, 'north', 'top') }
  for (let z = z0; z <= z1; z++) { stair(c, x0 - 1, y1 + 1, z, cornice, 'east', 'top'); stair(c, x1 + 1, y1 + 1, z, cornice, 'west', 'top') }
}

const putLeaf = (c, x, y, z, id, props) => { if (c.inside(x, y, z) && !c.get(x, y, z)) c.set(x, y, z, id, props) }

/** datePalm: soil at [x, y, z], a trunk `height` high, a crown: a tuft on top, a ring round the trunk's head, a frond
 *  out on each side two from the trunk with its tip hanging a block below it, and the ring's corners hanging a block
 *  too (every leaf face to face with another, so none floats); `reach` 3 runs the side fronds a block further, their
 *  tips hanging two. Cells already drawn or outside the canvas are left,
 *  and those `skip(x, y, z)` names (a frond that would roof a loggia below). */
export function datePalm(c, [x, y, z], { soil, trunk, leaves, height = 4, reach = 2, skip = () => false }) {
  const put = (x, y, z) => { if (!skip(x, y, z)) putLeaf(c, x, y, z, leaves, leafProps) }
  c.set(x, y, z, soil)
  for (let i = 1; i <= height; i++) c.set(x, y + i, z, trunk, { axis: 'y' })
  const t = y + height
  put(x, t + 1, z)
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) if (dx || dz) put(x + dx, t, z + dz)
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    put(x + 2 * dx, t, z + 2 * dz)
    if (reach >= 3) { put(x + 3 * dx, t, z + 3 * dz); put(x + 3 * dx, t - 1, z + 3 * dz); put(x + 3 * dx, t - 2, z + 3 * dz) } // a long frond, its tip hanging two
    else put(x + 2 * dx, t - 1, z + 2 * dz)
  }
  for (const [dx, dz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) put(x + dx, t - 1, z + dz)
}

/** acaciaTree: soil at [x, y, z], a trunk 3 high, leaning a block east above it, a branch west; a flat crown 5 across
 *  (corners off) over the lean, a smaller one over the branch a course lower. */
export function acaciaTree(c, [x, y, z], { soil, log, leaves }) {
  c.set(x, y, z, soil)
  for (let i = 1; i <= 3; i++) c.set(x, y + i, z, log, { axis: 'y' })
  c.set(x + 1, y + 4, z, log, { axis: 'y' })
  c.set(x + 1, y + 5, z, log, { axis: 'y' })
  c.set(x - 1, y + 4, z, log, { axis: 'x' })
  for (let dx = -2; dx <= 2; dx++) for (let dz = -2; dz <= 2; dz++) {
    if (Math.abs(dx) === 2 && Math.abs(dz) === 2) continue
    putLeaf(c, x + 1 + dx, y + 6, z + dz, leaves, leafProps)
  }
  putLeaf(c, x + 1, y + 7, z, leaves, leafProps)
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) putLeaf(c, x - 2 + dx, y + 5, z + dz, leaves, leafProps)
}

/** slabPent: a low roof over x0..x1 from its eave row zEave to the row against the wall (zWall), half a block higher
 *  each row (`id` slabs: bottom, top, bottom, ...) from yEave. */
export function slabPent(c, x0, x1, zEave, zWall, yEave, id) {
  const step = zWall > zEave ? 1 : -1
  for (let z = zEave, r = 0; ; z += step, r++) {
    for (let x = x0; x <= x1; x++) slab(c, x, yEave + Math.floor(r / 2), z, id, r % 2 ? 'top' : 'bottom')
    if (z === zWall) break
  }
}

/** verandaEave: a veranda's roof over x0..x1 from its eave row zEave back to zWall (the wall or jetty behind), at y: a
 *  row of `roof.stairs` along the eave (backs to the wall), `roof.full` in the rows behind it, and at each end in
 *  `returns` ('west', 'east') a stair a block further out turning the eave round the corner (kit.finish shapes the
 *  corners). */
export function verandaEave(c, { x0, x1, zEave, zWall, y, returns = ['west', 'east'] }, roof) {
  const step = zWall > zEave ? 1 : -1
  const back = step > 0 ? 'south' : 'north'
  for (let x = x0; x <= x1; x++) for (let z = zEave; ; z += step) {
    if (z === zEave) stair(c, x, y, z, roof.stairs, back)
    else c.set(x, y, z, roof.full)
    if (z === zWall) break
  }
  for (const side of returns) {
    const x = side === 'west' ? x0 - 1 : x1 + 1
    for (let z = zEave; ; z += step) {
      if (c.inside(x, y, z) && !c.get(x, y, z)) stair(c, x, y, z, roof.stairs, side === 'west' ? 'east' : 'west')
      if (z === zWall) break
    }
  }
}
