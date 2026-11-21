// Pokémon clinics (the Pokémon Center, ROADMAP 49): Cobblemon's own fixtures drawn with their states (the Healing
// Machine, the two-high PC, the two-high Pasture Block) and our Shop Counter; a stepped voxel dome over a round drum
// with a pale lining under it and a glazed eye on top; a desk run of counters with a job block in it. Every block that is
// not a fixed fixture comes from the palette passed in.
import { stair } from './canvas.mjs'

const FACE_IN = (dx, dz) => (Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? 'east' : 'west') : (dz < 0 ? 'south' : 'north'))

/** healingMachine: Cobblemon's Healing Machine at pos (a Nurse's job site), facing the way its user faces it from. */
export function healingMachine(c, [x, y, z], facing = 'north') { c.set(x, y, z, 'cobblemon:healing_machine', { facing }) }

/** pcTower: Cobblemon's PC, two blocks tall, its lower half at pos. */
export function pcTower(c, [x, y, z], facing = 'north') {
  for (const [part, dy] of [['bottom', 0], ['top', 1]]) c.set(x, y + dy, z, 'cobblemon:pc', { facing, part, on: 'false', waterlogged: 'false' })
}

/** pastureBlock: Cobblemon's Pasture Block, two blocks tall, its lower half (the job site) at pos. */
export function pastureBlock(c, [x, y, z], facing = 'north') {
  for (const [part, dy] of [['bottom', 0], ['top', 1]]) c.set(x, y + dy, z, 'cobblemon:pasture', { facing, part, on: 'false', waterlogged: 'false' })
}

/** shopCounter: our Shop Counter (the Pokémon Trader's job site when a Poké Ball picks it) at pos. */
export function shopCounter(c, [x, y, z], facing = 'north') { c.set(x, y, z, 'aliveworkplace:shop_counter', { facing }) }

/** deskRun: a straight desk of counters along one line ([x, y, z] cells), each a counter spot (a base block under a top
 *  slab: `P.base`, `P.top`), facing the visitor (`facing`); a job block may stand in the run (draw it after). */
export function deskRun(c, cells, facing, { base, top, wood }) {
  for (const [x, y, z] of cells) {
    c.set(x, y, z, base)
    c.set(x, y + 1, z, top, { type: 'bottom', waterlogged: 'false' })
    c.spot('counter', [x, y, z], facing, [[0, 0, 0], [0, 1, 0]], wood)
  }
}

/** ringDome: a stepped dome over (cx, cz) built in rings (roundCells' rings: ring k is roundCells(k) less
 *  roundCells(k - 1)), ring k's top at `H[k]` (H[0] the crown, the last the eave ring), a
 *  `roof.stairs` rising toward the centre where the ring inside it sits higher, else `roof.full`. Each ring's red course
 *  runs down to just above the ring outside it (no see-through step). Rings over the room (k <= `lined`) get a second,
 *  inner course of `lining` behind the red one (from just above the ring two out, up to under its own red), so the vault
 *  reads pale from under it and red from outside. `keep(x, y, z)` false leaves a cell (an eye, a cupola). Returns the
 *  cells of each ring (rings[k]: [x, z]). */
export function ringDome(c, { cx, cz, H, lined = -1 }, roof, lining = null, keep = () => true) {
  const R = H.length - 1
  const rings = []
  const seen = new Set()
  for (let k = 0; k <= R; k++) {
    rings[k] = []
    for (let dx = -k; dx <= k; dx++) for (let dz = -k; dz <= k; dz++) {
      if (dx * dx + dz * dz > k * k + k || seen.has(`${dx},${dz}`)) continue
      seen.add(`${dx},${dz}`); rings[k].push([cx + dx, cz + dz])
    }
  }
  const put = (x, y, z, id, props) => { if (keep(x, y, z)) c.set(x, y, z, id, props) }
  for (let k = 0; k <= R; k++) {
    const outH = k < R ? H[k + 1] : H[k] - 1
    const redFrom = Math.min(H[k], outH + 1)
    const inner = k > 0 ? H[k - 1] : H[k]
    for (const [x, z] of rings[k]) {
      for (let y = redFrom; y <= H[k]; y++) {
        const dx = x - cx, dz = z - cz
        if (y === H[k] && inner > H[k] && roof.stairs) put(x, y, z, roof.stairs, { facing: FACE_IN(dx, dz), half: 'bottom', shape: 'straight', waterlogged: 'false' })
        else put(x, y, z, roof.full)
      }
      if (k < R && (k > lined || !lining)) put(x, outH, z, roof.full) // behind the outer ring's stair: closes the step
      if (k > lined || !lining) continue
      const from = k + 2 <= R ? H[k + 2] + 1 : redFrom - 1
      for (let y = Math.min(from, redFrom - 1); y < redFrom; y++) put(x, y, z, lining)
    }
  }
  // a lining cell the sky can see sideways (beside air, or beside a stair the game may turn into an outer corner, which
  // leaves three quarters of its top half open) turns red
  const st = roof.stairs && (roof.stairs.includes(':') ? roof.stairs : `minecraft:${roof.stairs}`)
  const open = (x, y, z) => { if (c.get(x, y, z) && c.name(x, y, z) !== st) return false; for (let yy = y + 1; yy <= H[0] + 1; yy++) if (c.get(x, yy, z)) return false; return true }
  if (lining) for (let k = 0; k <= Math.min(lined, R); k++) for (const [x, z] of rings[k]) for (let y = H[R]; y <= H[0]; y++) {
    if (c.name(x, y, z) !== (lining.includes(':') ? lining : `minecraft:${lining}`)) continue
    if ([[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dz]) => open(x + dx, y, z + dz))) put(x, y, z, roof.full)
  }
  return rings
}
