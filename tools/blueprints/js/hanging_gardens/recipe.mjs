// The Hanging Gardens (Wonder), the recipe: assembles plan.mjs from the parts library (../parts) in passes, stage by
// stage on a StagedCanvas (a later stage may only add: drawing over or clearing an earlier stage's block throws), so
// draw(n), the Wonder as it stands once stage n is done, is exactly stages 1..n. Each stage's passes: block-out and
// structure (the terrace's shell, its hall, its top), the way up, the faces' depth (piers, windows, cornice, parapet),
// the hanging curtains, the farm corner, the gardens, the hall's interior, light. verify() checks every stage against
// the brief. STAGE=<1..4> picks the stage the runner writes (default 4); CUT=<y> cuts everything above y away.
import {
  SIZE, AX, PALETTE as P, TIERS, STAIR0, FLIGHTS, FARMS, SHAFT, CISTERN, PAVILION, POOLS, BASIN, VAULT_DOORS, HALL_DOORS,
} from './plan.mjs'
import { Canvas, mix, StagedCanvas, datePalm, cypress, drape, faceFlight } from '../parts/index.mjs'

const wall = mix(P.wall, 3), floor = mix(P.floor, 5), hallFloor = mix(P.hallFloor, 7)
const drapeLeaves = mix(P.drapeLeaves, 9), hedge = mix(P.hedge, 11)
const hash = (a, b, s = 1) => { let h = (a * 73856093) ^ (b * 19349663) ^ (s * 83492791); h = (h ^ (h >>> 13)) * 1274126177; return Math.abs(h ^ (h >>> 16)) }
const OPP = { north: 'south', south: 'north', west: 'east', east: 'west' }
const LEAF = { persistent: 'true', distance: '1', waterlogged: 'false' }
const NOWALL = { up: 'true', north: 'none', south: 'none', east: 'none', west: 'none', waterlogged: 'false' }
const NOPANE = { north: 'false', south: 'false', east: 'false', west: 'false', waterlogged: 'false' }

const upStair = (c, x, y, z, id, facing) => c.set(x, y, z, id, { facing, half: 'top', shape: 'straight', waterlogged: 'false' })
const lowStair = (c, x, y, z, id, facing) => c.set(x, y, z, id, { facing, half: 'bottom', shape: 'straight', waterlogged: 'false' })
const hang = (c, x, y, z) => c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
const stand = (c, x, y, z) => c.set(x, y, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
const leaf = (c, x, y, z, id) => c.set(x, y, z, typeof id === 'function' ? id(x, y, z) : id, LEAF)
const water = (c, x, y, z) => c.set(x, y, z, 'water', { level: '0' })
const door = (c, x, y, z, facing) => {
  for (const half of ['lower', 'upper']) c.set(x, y + (half === 'upper' ? 1 : 0), z, P.door, { facing, half, hinge: 'left', open: 'false', powered: 'false' })
}

// A Canvas view for one stage's gardens that leaves alone every cell a later stage will build in (the next terraces'
// walls, piers, cornices and curtains, and the stairs up their faces with head room), so nothing planted now is in the
// way of the stages after it.
function future(stage) {
  const boxes = []
  for (const T of TIERS.filter(t => t.n > stage)) boxes.push([T.x0 - 1, T.base, T.z0 - 1, T.x1 + 1, SIZE[1] - 1, T.z1 + 1])
  for (const f of FLIGHTS.filter(f => f.stage > stage)) {
    const xs = [f.x0, f.x0 + (f.along === 'east' ? 1 : -1) * (f.steps - 1)]
    boxes.push([Math.min(...xs) - 1, f.from + 1, Math.min(...f.zs) - 1, Math.max(...xs) + 1, f.from + f.steps + 3, Math.max(...f.zs)])
  }
  return (x, y, z) => boxes.some(([a, b, c, d, e, f]) => x >= a && x <= d && y >= b && y <= e && z >= c && z <= f)
}
function guarded(c, stage) {
  const later = future(stage)
  return new Proxy(c, {
    get(t, k) {
      if (k === 'set') return (x, y, z, id, props) => { if (!later(x, y, z)) t.set(x, y, z, id, props) }
      const v = t[k]
      return typeof v === 'function' ? v.bind(t) : v
    },
  })
}

// --- the terrace's faces: [out, line, u0, u1] (u along the face) and the cell one out from a face cell ---
function faces(T) {
  return [['north', T.z0, T.x0, T.x1], ['south', T.z1, T.x0, T.x1], ['west', T.x0, T.z0, T.z1], ['east', T.x1, T.z0, T.z1]]
}
const onFace = (out, line, u, y) => (out === 'north' || out === 'south' ? [u, y, line] : [line, y, u])
const outOf = (out, line, u, y) => {
  const d = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[out]
  const [x, yy, z] = onFace(out, line, u, y)
  return [x + d[0], yy, z + d[1]]
}
// piers every four along a face (on the axis' rhythm across, from the front corner along the sides) and at its corners
const pierAt = (T, out, u) => {
  const [u0, u1] = out === 'north' || out === 'south' ? [T.x0, T.x1] : [T.z0, T.z1]
  if (u === u0 || u === u1) return true
  return out === 'north' || out === 'south' ? (u - AX) % 4 === 0 : (u - T.z0) % 4 === 0
}

// ======================= structure: the terrace's shell, its hall and its top =======================
function shell(c, T) {
  const edge = (x, z) => x === T.x0 || x === T.x1 || z === T.z0 || z === T.z1
  const outward = (x, z) => (z === T.z0 ? 'north' : z === T.z1 ? 'south' : x === T.x0 ? 'west' : 'east')
  for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
    for (let y = T.base; y <= T.F; y++) {
      if (y === T.F) c.set(x, y, z, floor)
      else if (edge(x, z)) {
        if (y === T.F - 1) c.set(x, y, z, P.frieze, { facing: outward(x, z) })
        else if (T.n === 1 && y <= 1) c.set(x, y, z, P.plinth)
        else c.set(x, y, z, wall)
      } else if (y === T.F - 1) c.set(x, y, z, wall)
      else if (T.n === 1 && y === 0) c.set(x, y, z, VAULT_LANES.xs.includes(x) || VAULT_LANES.zs.includes(z) ? P.plinth : hallFloor)
    }
  }
}

// the shaft: a 3x3 ring of wall round a column of still water the height of the hall, under the hall's ceiling, with a
// window onto the water; each terrace's hall has its length of it, one over the other up the middle of the mount
function shaft(c, T) {
  const { x, z } = SHAFT
  const ring = [[-1, -1], [0, -1], [1, -1], [-1, 0], [1, 0], [-1, 1], [0, 1], [1, 1]]
  for (let y = T.iy0; y <= T.F - 2; y++) {
    for (const [dx, dz] of ring) c.set(x + dx, y, z + dz, wall)
    water(c, x, y, z)
  }
  for (const y of [T.iy0 + 1, T.iy0 + 2]) c.set(x, y, z - 1, P.glass, { ...NOPANE, east: 'true', west: 'true' })
}

// ======================= depth: piers, windows, doors, frieze, cornice, parapet =======================
function facade(c, T, { skip = () => false, doors = [], parapetOn = () => true, gaps = [], windows = true, lamps = true }) {
  for (const [out, line, u0, u1] of faces(T)) {
    const toWall = OPP[out]
    const piers = []
    for (let u = u0; u <= u1; u++) if (pierAt(T, out, u)) piers.push(u)
    // the bays between the piers: windows (or a door) and curtains of green, by turns
    for (let i = 0; i + 1 < piers.length; i++) {
      const a = piers[i], b = piers[i + 1]
      if (b - a < 2) continue
      const mid = Math.floor((a + b) / 2)
      const isDoor = out === 'north' && doors.includes(mid)
      const curtain = !isDoor && (i % 2 === 1 || !windows || b - a !== 4)
      const us = []
      for (let u = a + 1; u < b; u++) us.push(u)
      if (isDoor) {
        const [x, , z] = onFace(out, line, mid, T.iy0)
        c.clear(x, T.iy0, z); c.clear(x, T.iy0 + 1, z)
        door(c, x, T.iy0, z, 'north')
        const [hx, hy, hz] = outOf(out, line, mid, T.iy0 + 2)
        if (!skip(hx, hz)) upStair(c, hx, hy, hz, P.dressStairs, toWall)
        continue
      }
      if (curtain) {
        const live = us.filter(u => { const [x, , z] = outOf(out, line, u, T.F); return !skip(x, z) })
        drape(c, { out, line, us: live, top: T.F, bottom: T.base }, drapeLeaves, u => 2 + (hash(u, T.F, line) % 3), u => 3 + (hash(u, line, T.F) % 5), P)
        continue
      }
      // a window: two panes in the wall under a sandstone hood one block out
      const [x0, , z0] = outOf(out, line, mid, 0)
      if (skip(x0, z0)) continue
      for (const y of [T.iy0 + 1, T.iy0 + 2]) {
        const [x, , z] = onFace(out, line, mid, y)
        c.set(x, y, z, P.glass, out === 'north' || out === 'south' ? { ...NOPANE, east: 'true', west: 'true' } : { ...NOPANE, north: 'true', south: 'true' })
      }
      const [hx, hy, hz] = outOf(out, line, mid, T.iy0 + 3)
      upStair(c, hx, hy, hz, P.dressStairs, toWall)
    }
    // the piers: brick proud of the wall, on a sandstone base (baked brick at Terrace I's foot), to a carved capital at
    // the frieze
    for (const u of piers) {
      const [x, , z] = outOf(out, line, u, 0)
      if (skip(x, z)) continue
      for (let y = T.base; y <= T.F - 1; y++) {
        if (c.get(x, y, z)) continue
        c.set(x, y, z, y === T.F - 1 ? P.cap : T.n === 1 && y === 0 ? P.plinth : y === T.base ? P.dress : P.pier)
      }
    }
    // the cornice: upside-down stairs one out under the parapet, wherever no curtain hangs
    for (let u = u0 - 1; u <= u1 + 1; u++) {
      const [x, y, z] = outOf(out, line, u, T.F)
      if (x < 0 || z < 0 || x >= SIZE[0] || z >= SIZE[2] || skip(x, z) || c.get(x, y, z)) continue
      upStair(c, x, y, z, P.dressStairs, toWall)
    }
    // the parapet on the edge: a sandstone pier at the corners and over every other pier, a hedge over the curtains and a
    // low wall over the windows between, lamps on the front's piers
    for (let u = u0; u <= u1; u++) {
      const [x, , z] = onFace(out, line, u, T.F + 1)
      if (!parapetOn(out, x, z) || gaps.some(([gx0, gx1, gz]) => x >= gx0 && x <= gx1 && z === gz) || c.get(x, T.F + 1, z)) continue
      const curtained = (() => { const [ox, oy, oz] = outOf(out, line, u, T.F); const b = c.get(ox, oy, oz); return b && b.id.includes('leaves') })()
      if (pierAt(T, out, u) && (u === u0 || u === u1 || (((u - (out === 'north' || out === 'south' ? AX : T.z0)) % 8) + 8) % 8 === 4)) {
        c.set(x, T.F + 1, z, P.dress)
        if (lamps && out === 'north' && (((u - AX) % 8) + 8) % 8 === 4) { c.set(x, T.F + 2, z, P.parapet, NOWALL); stand(c, x, T.F + 3, z) }
      } else if (curtained) leaf(c, x, T.F + 1, z, hedge)
      else c.set(x, T.F + 1, z, P.parapet, NOWALL)
    }
  }
}

// ======================= interior: a hall's piers, lamps and its things =======================
function hallPiers(c, T, xs, zs) {
  for (const px of xs) for (const pz of zs) {
    for (let y = T.iy0; y <= T.F - 2; y++) c.set(px, y, pz, wall)
    for (const [dx, dz, f] of [[1, 0, 'west'], [-1, 0, 'east'], [0, 1, 'north'], [0, -1, 'south']]) upStair(c, px + dx, T.F - 2, pz + dz, P.dressStairs, f)
  }
  for (let i = 0; i + 1 < xs.length; i++) for (let j = 0; j + 1 < zs.length; j++) {
    const lx = Math.floor((xs[i] + xs[i + 1]) / 2), lz = Math.floor((zs[j] + zs[j + 1]) / 2)
    if (!c.get(lx, T.F - 2, lz) && c.get(lx, T.F - 1, lz)) hang(c, lx, T.F - 2, lz)
  }
}

// the nursery: raised beds of rooted earth between a hall's piers, young trees, ferns and azaleas waiting to be planted
// out on the terraces
const SAPLINGS = ['oak_sapling', 'birch_sapling', 'jungle_sapling', 'cherry_sapling', 'fern', 'azalea', 'flowering_azalea', 'acacia_sapling']
function nursery(c, y, centres) {
  for (const [cx, cz] of centres) for (let x = cx - 1; x <= cx + 1; x++) for (let z = cz - 1; z <= cz + 1; z++) {
    c.set(x, y, z, P.root)
    const h = hash(x, z, y + 3)
    if (h % 3 !== 0) c.set(x, y + 1, z, SAPLINGS[h % SAPLINGS.length], /_sapling$/.test(SAPLINGS[h % SAPLINGS.length]) ? { stage: '0' } : {})
  }
}
const VAULT_LANES = { xs: [7, 13, 19, 27, 33, 39], zs: [11, 17, 23, 29] }

// ======================= the farm corner: field, water, composter, chest =======================
function farm(c, f) {
  const ages = { wheat: [2, 3, 4, 5], carrots: [2, 3, 4, 5], beetroots: [0, 1, 2] }
  for (let x = f.x0; x <= f.x1; x++) for (let z = f.z0; z <= f.z1; z++) {
    const wet = f.water === 'row' ? z === Math.floor((f.z0 + f.z1) / 2) : x === Math.floor((f.x0 + f.x1) / 2)
    if (wet) { water(c, x, f.y, z); continue }
    c.set(x, f.y, z, 'farmland', { moisture: '7' })
    const k = f.water === 'row' ? z - f.z0 : x - f.x0
    const crop = f.crops[Math.min(k, f.crops.length - 1)]
    const a = ages[crop]
    c.set(x, f.y + 1, z, crop, { age: String(a[hash(x, z, 5) % a.length]) })
  }
  c.set(...f.composter, 'composter', { level: '0' })
  const [cx, , cz] = f.composter, [hx, , hz] = f.chest
  c.set(...f.chest, 'chest', { facing: hx > cx ? 'west' : hz > cz ? 'west' : 'east', type: 'single', waterlogged: 'false' })
}

// a bed of soil in the top layer with flowers or bushes on it
const FLOWERS = ['poppy', 'cornflower', 'allium', 'oxeye_daisy', 'azure_bluet', 'red_tulip', 'lily_of_the_valley', 'dandelion']
function bed(c, cells, y, { plants = FLOWERS, every = 1, soil = P.soil, bush = 0 } = {}) {
  for (const [x, z] of cells) {
    c.set(x, y, z, soil, soil === P.soil ? { snowy: 'false' } : {})
    const h = hash(x, z, y)
    if (h % every) continue
    if (bush && h % bush === 0) c.set(x, y + 1, z, h % 2 ? 'flowering_azalea' : 'azalea')
    else c.set(x, y + 1, z, plants[h % plants.length])
  }
}
// a lawn: every plain paved cell of the terrace's top that no path, no later stage and nothing standing on it needs
// becomes grass, with flowers, grass, bushes and a few tall flowers on it
const TALL = ['lilac', 'peony', 'rose_bush']
function lawn(c, T, stage, paved) {
  const later = future(stage)
  const floors = new Set(P.floor.map(([id]) => `minecraft:${id}`))
  for (let x = T.x0 + 1; x < T.x1; x++) for (let z = T.z0 + 1; z < T.z1; z++) {
    if (paved(x, z) || !floors.has(c.get(x, T.F, z)?.id) || c.get(x, T.F + 1, z) || later(x, T.F + 1, z)) continue
    c.set(x, T.F, z, P.soil, { snowy: 'false' })
    const h = hash(x, z, T.F + 7) % 100
    if (h < 28) c.set(x, T.F + 1, z, FLOWERS[h % FLOWERS.length])
    else if (h < 42) c.set(x, T.F + 1, z, 'short_grass')
    else if (h < 50) c.set(x, T.F + 1, z, h % 2 ? 'flowering_azalea' : 'azalea')
    else if (h < 55 && !later(x, T.F + 2, z) && !c.get(x, T.F + 2, z)) {
      const t = TALL[h % 3]
      c.set(x, T.F + 1, z, t, { half: 'lower' }); c.set(x, T.F + 2, z, t, { half: 'upper' })
    }
  }
}
const rect = (x0, z0, x1, z1) => { const o = []; for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) o.push([x, z]); return o }

// ======================= Stage I: the Vaults and the Lower Terrace =======================
function stage1(c) {
  const T = TIERS[0]
  shell(c, T)
  // the Processional Stair: cut into the terrace's front and run out over the forecourt, stair y k at z k, on its
  // cheek walls (stepped, capped with sandstone), the terrace's top level with its last stair
  for (let k = 0; k < STAIR0.steps; k++) {
    for (let x = STAIR0.x0; x <= STAIR0.x1; x++) {
      for (let y = k + 1; y <= T.F + 1; y++) c.clear(x, y, k)
      for (let y = 0; y < k; y++) if (!c.get(x, y, k)) c.set(x, y, k, wall)
      lowStair(c, x, k, k, P.dressStairs, 'south')
    }
    for (const x of STAIR0.cheeks) {
      if (k >= T.z0) continue
      for (let y = 0; y <= k; y++) c.set(x, y, k, wall)
      c.set(x, k + 1, k, P.coping, { type: 'bottom', waterlogged: 'false' })
    }
  }
  const stairCols = (x, z) => x >= STAIR0.cheeks[0] && x <= STAIR0.cheeks[1] && z <= STAIR0.steps - 1
  facade(c, T, {
    skip: stairCols, doors: VAULT_DOORS,
    parapetOn: (out, x, z) => out !== 'south' && !(x >= STAIR0.x0 && x <= STAIR0.x1 && z === T.z0),
  })
  // the plinth: a step of baked brick round the foot, rising to the wall (the piers stand on full blocks)
  for (const [out, line, u0, u1] of faces(T)) for (let u = u0 - 1; u <= u1 + 1; u++) {
    const [x, , z] = outOf(out, line, u, 0)
    if (stairCols(x, z) || c.get(x, 0, z)) continue
    lowStair(c, x, 0, z, P.plinthStairs, OPP[out])
  }
  // the stair head: two sandstone posts carrying lamps
  for (const x of STAIR0.cheeks) { c.set(x, T.F + 1, T.z0, P.dress); stand(c, x, T.F + 2, T.z0) }
  // Forecourt: two raised reflecting pools either side of the stair, lamp posts at the stair's foot
  for (const p of POOLS) for (let x = p.x0; x <= p.x1; x++) for (let z = 0; z <= 2; z++) {
    c.set(x, 0, z, P.plinth)
    const rim = x === p.x0 || x === p.x1 || z === 0 || z === 2
    if (rim) c.set(x, 1, z, P.dress)
    else water(c, x, 1, z)
  }
  for (const p of POOLS) for (const x of [p.x0 + 2, p.x1 - 2]) c.set(x, 2, 1, 'lily_pad')
  for (const x of [STAIR0.cheeks[0] - 1, STAIR0.cheeks[1] + 1]) { c.set(x, 0, 0, P.dress); c.set(x, 1, 0, P.parapet, NOWALL); stand(c, x, 2, 0) }

  // The Vaults: piers on a grid, their capitals, lamps between; the shaft's well with its window; the nursery and the
  // seed store
  hallPiers(c, T, VAULT_LANES.xs, VAULT_LANES.zs)
  nursery(c, 1, [10, 16, 30, 36].flatMap(x => [14, 20, 26].map(z => [x, z])))
  shaft(c, T)
  for (const [x0, x1] of [[3, 12], [34, 43]]) for (let x = x0; x <= x1; x += 1) for (const z of [33, 35, 37, 39]) {
    if (x % 3 === 0) continue
    c.set(x, 1, z, ['potted_oak_sapling', 'potted_jungle_sapling', 'potted_azalea_bush', 'potted_flowering_azalea_bush', 'potted_birch_sapling'][hash(x, z, 2) % 5])
  }
  for (const [x, z, h] of [[3, 6, 2], [4, 6, 1], [3, 7, 1], [43, 6, 2], [42, 6, 1], [43, 7, 1], [18, 41, 2], [19, 41, 1], [28, 41, 2], [27, 41, 1]])
    for (let y = 1; y <= h; y++) c.set(x, y, z, 'hay_block', { axis: 'y' })
  for (const [x, z] of [[5, 6], [41, 6], [20, 41], [26, 41]]) c.set(x, 1, z, 'brown_wool')
  for (const [x, z] of [[21, 34], [25, 34], [21, 38], [25, 38]]) hang(c, x, 4, z)

  // The Lower Terrace (only where Stage II won't build)
  const g = guarded(c, 1)
  farm(c, FARMS[0])
  for (let x = BASIN.x0; x <= BASIN.x1; x++) for (let z = BASIN.z0; z <= BASIN.z1; z++) water(c, x, BASIN.y, z)
  for (const x of [BASIN.x0 + 2, BASIN.x0 + 6, BASIN.x1 - 1]) c.set(x, BASIN.y + 1, BASIN.z0 + (x % 2), 'lily_pad')
  bed(g, rect(BASIN.x0 - 1, BASIN.z0 - 1, BASIN.x1 + 1, BASIN.z0 - 1).concat(rect(BASIN.x0 - 1, BASIN.z1 + 1, BASIN.x1 + 1, BASIN.z1 + 1),
    [[BASIN.x0 - 1, BASIN.z0], [BASIN.x0 - 1, BASIN.z1], [BASIN.x1 + 1, BASIN.z0], [BASIN.x1 + 1, BASIN.z1]]), T.F, { every: 2 })
  // a flower border along the front parapet and the side walks; palms and cypresses planted through it
  bed(g, rect(2, 5, 17, 5).concat(rect(29, 5, 44, 5)), T.F, { bush: 5 })
  for (const [x, z, h] of [[31, 5, 7], [39, 5, 8], [43, 10, 6]]) { g.set(x, T.F, z, P.root); g.clear(x, T.F + 1, z); datePalm(g, [x, T.F + 1, z], h, P.palm) }
  for (const x of [STAIR0.cheeks[0] - 2, STAIR0.cheeks[1] + 2]) { g.set(x, T.F, 8, P.root); cypress(g, [x, T.F + 1, 8], 8, P.cypress) }
  bed(g, rect(2, 14, 2, 40).filter(([, z]) => z % 4 !== 1), T.F, { bush: 3, every: 2 })
  bed(g, rect(44, 14, 44, 40).filter(([, z]) => z % 4 !== 1), T.F, { bush: 3, every: 2 })
  // a scarecrow at the field's corner, on the paving
  g.set(3, T.F + 1, 6, 'spruce_fence'); g.set(3, T.F + 2, 6, 'hay_block', { axis: 'y' }); g.set(3, T.F + 3, 6, 'carved_pumpkin', { facing: 'north' })
  // lawns everywhere but the walks: the field's border, the axis up from the stair, the way to the farm and the
  // Gallery Stair
  lawn(c, T, 1, (x, z) => (x >= 3 && x <= 15 && z >= 6 && z <= 12) || (x >= 20 && x <= 26 && z >= 7) || (z >= 9 && z <= 10 && x >= 16 && x <= 20)
    || z === 12)
}

// ======================= Stages II-IV: a terrace on the one before, its stair, its farm, its gardens =======================
function upperTier(c, T, flight, hall) {
  shell(c, T)
  const steps = faceFlight(c, { x0: flight.x0, along: flight.along, zs: flight.zs, y0: flight.from, steps: flight.steps }, P.dressStairs, wall)
  // the landing at its head, level with the terrace's top
  const lx = flight.x0 + (flight.along === 'east' ? 1 : -1) * flight.steps
  for (const z of flight.zs) for (let y = flight.from + 1; y <= T.F; y++) { c.set(lx, y, z, y === T.F ? floor : wall); steps.push([lx, y, z]) }
  const flightCols = new Set(steps.map(([x, , z]) => `${x},${z}`))
  // the stair's cells and, at its head, the out row over the gap (no cornice beside the last stair)
  const skip = (x, z) => flightCols.has(`${x},${z}`) || (z === Math.max(...flight.zs) && x >= flight.gap[0] && x <= flight.gap[1])
  const below = TIERS[T.n - 2]
  facade(c, T, {
    skip, doors: HALL_DOORS.filter(d => d.tier === T.n).map(d => d.x), windows: T.n < 4,
    parapetOn: out => T.n === 4 || out !== 'south',
    gaps: [[flight.gap[0], flight.gap[1], T.z0]],
  })
  if (hall) hall(c, T)
  if (T.n < 4) shaft(c, T)
  return below
}

function stage2(c) {
  const T = TIERS[1]
  upperTier(c, T, FLIGHTS[0], (c, T) => {
    hallPiers(c, T, [11, 17, 29, 35], [20, 27, 34])
    nursery(c, T.iy0, [[14, 23], [32, 23], [14, 30], [32, 30], [23, 23], [23, 30]])
    // the orangery: potted azaleas along the glazed arcade and the back wall
    for (let x = 8; x <= 38; x++) for (const z of [15, 40]) {
      if (x === 17 || x % 2) continue
      if (!c.get(x, T.iy0, z)) c.set(x, T.iy0, z, x % 4 === 0 ? 'potted_flowering_azalea_bush' : 'potted_azalea_bush')
    }
  })
  farm(c, FARMS[1])
  const g = guarded(c, 2)
  // the Glow-Berry Pergola: posts, beams along the front, rafters across, glow berries hanging from the rafters, a
  // bench of stairs under it and beds at its feet
  const y = T.F
  for (const x of [8, 12, 16]) for (const z of [15, 19]) for (let yy = y + 1; yy <= y + 3; yy++) g.set(x, yy, z, P.post, { axis: 'y' })
  for (let x = 8; x <= 16; x++) for (const z of [15, 19]) if (!c.get(x, y + 4, z)) g.set(x, y + 4, z, P.beam, { axis: 'x' })
  for (const x of [9, 11, 13, 15]) for (let z = 15; z <= 19; z++) if (!c.get(x, y + 4, z)) g.set(x, y + 4, z, P.beam, { axis: 'z' })
  for (const x of [9, 11, 13, 15]) for (const z of [16, 18]) {
    const len = 1 + (hash(x, z, 3) % 2)
    for (let i = 0; i < len; i++) g.set(x, y + 3 - i, z, i === len - 1 ? P.berries : P.berriesPlant, i === len - 1 ? { age: '0', berries: 'false' } : { berries: 'false' })
  }
  for (const x of [10, 11, 13, 14]) lowStair(g, x, y + 1, 17, P.dressStairs, 'south')
  bed(g, rect(7, 15, 7, 19).concat(rect(17, 15, 17, 19).filter(([, z]) => z !== 19)), y, { bush: 2 })
  // the Lily Basin in the middle of the terrace, ringed with flowers
  for (let x = 20; x <= 22; x++) for (const z of [17, 18]) water(g, x, y, z)
  g.set(21, y + 1, 17, 'lily_pad')
  bed(g, rect(19, 16, 23, 16).concat(rect(19, 19, 23, 19), [[19, 17], [19, 18], [23, 17], [23, 18]]), y, { every: 2 })
  // the side walks and the front border by the farm
  bed(g, rect(7, 23, 7, 39).filter(([, z]) => z % 4 !== 3), y, { bush: 3, every: 2 })
  bed(g, rect(39, 23, 39, 39).filter(([, z]) => z % 4 !== 3), y, { bush: 3, every: 2 })
  bed(g, rect(34, 15, 38, 15), y, { every: 1 })
  for (const [x, z, h] of [[39, 22, 7], [8, 22, 6]]) { g.set(x, y, z, P.root); datePalm(g, [x, y + 1, z], h, P.palm, { dates: 1 }) }
  // lawns but for the walk from the Gallery Stair past Farm II to the Cistern Stair and the Upper Hall's door, the
  // pergola's floor and the field's far side
  lawn(c, T, 2, (x, z) => (x >= 31 && x <= 33 && z <= 20) || (z >= 19 && z <= 20 && x >= 24 && x <= 31) || (z === 20 && x >= 17 && x <= 24)
    || (x >= 8 && x <= 16 && z <= 19) || (x === 39 && z <= 20))
}

function stage3(c) {
  const T = TIERS[2]
  upperTier(c, T, FLIGHTS[1], (c, T) => {
    hallPiers(c, T, [15, 23, 31], [27, 33])
    nursery(c, T.iy0, [[19, 30], [27, 30]])
    for (const [x, z] of [[11, 23], [12, 23], [35, 23], [34, 23], [11, 39], [35, 39]]) c.set(x, T.iy0, z, 'hay_block', { axis: 'y' })
  })
  farm(c, FARMS[2])
  const g = guarded(c, 3)
  const y = T.F
  // the Cypress Walk on the right: two spires with open sky between them, beds along the sides
  for (const z of [23, 27]) { g.set(34, y, z, P.root); cypress(g, [34, y + 1, z], 9, P.cypress) }
  bed(g, rect(11, 30, 11, 39).filter(([, z]) => z % 3), y, { bush: 2, every: 2 })
  bed(g, rect(35, 30, 35, 39).filter(([, z]) => z % 3), y, { bush: 2, every: 2 })
  bed(g, rect(12, 23, 20, 23), y, { every: 1 })
  bed(g, rect(28, 23, 31, 23), y, { bush: 2 })
  lawn(c, T, 3, (x, z) => (x >= 24 && x <= 26 && z <= 27) || (x === 24 && z === 28) || (z >= 26 && z <= 27 && x >= 20 && x <= 23) || (x === 20 && z <= 28))
}

function stage4(c) {
  const T = TIERS[3]
  // Terrace IV is the cistern's body: rammed brick under the crown, the shaft rising through it
  upperTier(c, T, FLIGHTS[2], (c, T) => {
    for (let x = T.x0 + 1; x < T.x1; x++) for (let z = T.z0 + 1; z < T.z1; z++) for (let y = T.iy0; y < T.F - 1; y++) c.add(x, y, z, wall)
  })
  // the Crown Garden: the Cistern at the top of the shaft, the Water Pavilion round it, Farm IV, crown palms
  const { x0, x1, z0, z1, y0, y1, roof } = PAVILION
  for (let x = CISTERN.x0; x <= CISTERN.x1; x++) for (let z = CISTERN.z0; z <= CISTERN.z1; z++) {
    for (let y = T.iy0; y < CISTERN.y; y++) c.add(x, y, z, wall) // the cistern stands on brick down to the hall's floor
    water(c, x, CISTERN.y, z)
  }
  for (let x = CISTERN.x0 - 1; x <= CISTERN.x1 + 1; x++) for (let z = CISTERN.z0 - 1; z <= CISTERN.z1 + 1; z++) {
    const rim = x === CISTERN.x0 - 1 || x === CISTERN.x1 + 1 || z === CISTERN.z0 - 1 || z === CISTERN.z1 + 1
    if (rim) c.set(x, CISTERN.y + 1, z, P.coping, { type: 'bottom', waterlogged: 'false' })
  }
  c.set(22, CISTERN.y + 1, 35, 'lily_pad'); c.set(25, CISTERN.y + 1, 36, 'lily_pad')
  // columns: a sandstone shaft on a carved base, a carved capital with bull brackets along the architrave
  const cols = [[19, 33], [23, 33], [27, 33], [19, 38], [23, 38], [27, 38], [19, 35], [27, 35], [19, 36], [27, 36]]
  for (const [x, z] of cols) {
    c.set(x, y0, z, P.cap)
    for (let y = y0 + 1; y < y1; y++) c.set(x, y, z, P.dress)
    c.set(x, y1, z, P.cap)
  }
  for (const [x, z] of cols.filter(([, z]) => z === z0 || z === z1)) for (const dx of [-1, 1]) {
    if (x + dx < x0 || x + dx > x1) continue
    upStair(c, x + dx, y1, z, P.dressStairs, dx < 0 ? 'east' : 'west')
  }
  // the roof: a deck with the blue band round its edge, open over the water (the oculus), stepped merlons round it
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (x >= CISTERN.x0 && x <= CISTERN.x1 && z >= CISTERN.z0 && z <= CISTERN.z1) continue
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    const out = z === z0 ? 'north' : z === z1 ? 'south' : x === x0 ? 'west' : 'east'
    c.set(x, roof, z, edge ? P.frieze : P.dress, edge ? { facing: out } : {})
  }
  // a solid parapet round the roof and merlons on it, every other block, carved at the corners
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    if (!edge) continue
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    c.set(x, roof + 1, z, P.dress)
    if (corner) c.set(x, roof + 2, z, P.cap)
    else if ((x === x0 || x === x1 ? z - z0 : x - x0) % 2 === 0) c.set(x, roof + 2, z, P.dress)
  }
  for (let x = CISTERN.x0 - 1; x <= CISTERN.x1 + 1; x++) for (let z = CISTERN.z0 - 1; z <= CISTERN.z1 + 1; z++) {
    const rim = x === CISTERN.x0 - 1 || x === CISTERN.x1 + 1 || z === CISTERN.z0 - 1 || z === CISTERN.z1 + 1
    if (rim) c.set(x, roof + 1, z, P.coping, { type: 'bottom', waterlogged: 'false' })
  }
  for (const [x, z] of [[21, 34], [25, 34], [21, 37], [25, 37]]) hang(c, x, y1, z)
  // the lantern over the oculus: six short sandstone posts round the opening carrying a coping ring, open to the sky
  for (const [x, z] of [[20, 34], [23, 34], [26, 34], [20, 37], [23, 37], [26, 37]]) { c.set(x, roof + 1, z, P.dress); c.set(x, roof + 2, z, P.dress) }
  for (let x = CISTERN.x0 - 1; x <= CISTERN.x1 + 1; x++) for (let z = CISTERN.z0 - 1; z <= CISTERN.z1 + 1; z++) {
    const rim = x === CISTERN.x0 - 1 || x === CISTERN.x1 + 1 || z === CISTERN.z0 - 1 || z === CISTERN.z1 + 1
    if (rim) c.set(x, roof + 3, z, P.coping, { type: 'bottom', waterlogged: 'false' })
  }
  farm(c, FARMS[3])
  // a crown palm at the stair's head (clear of the pavilion, whose sides stay open to the sky), beds along the front
  for (const [x, z, h] of [[31, 31, 7]]) { c.set(x, T.F, z, P.root); datePalm(c, [x, T.F + 1, z], h, P.palm) }
  bed(c, rect(20, 31, 28, 31).filter(([x]) => x !== 23), T.F, { bush: 3 })
  lawn(c, T, 4, (x, z) => z === 32 || (x >= 19 && x <= 27 && z >= 33) || z === 31)
}

let WHOLE = null
function whole() {
  if (WHOLE) return WHOLE
  const c = new StagedCanvas(SIZE)
  c.begin(1); stage1(c)
  c.begin(2); stage2(c)
  c.begin(3); stage3(c)
  c.begin(4); stage4(c)
  WHOLE = c
  return c
}

export function draw(stage = 4) { return whole().stageCanvas(stage) }

// ======================= verify: the brief's checks on every stage =======================
const REPLACEABLE = /^(air|short_grass|tall_grass|fern|large_fern|vine|cave_vines|cave_vines_plant|poppy|dandelion|allium|azure_bluet|oxeye_daisy|cornflower|lily_of_the_valley|red_tulip|lilac|peony|rose_bush|wheat|carrots|beetroots|torch|lantern|lily_pad|.*_carpet|.*_sapling|.*_button)$/
const JOB = /(barrel|lectern|bell$|smoker|blast_furnace|cartography_table|fletching_table|grindstone|loom|stonecutter|smithing_table|brewing_stand|cauldron|_bed$|calcite|beehive|bee_nest)/
const SOLIDISH = id => id && !/(air|water|lantern|torch|flower|sapling|grass$|fern|tulip|poppy|dandelion|allium|bluet|daisy|cornflower|lily|wheat|carrots|beetroots|vine|cave_vines|cocoa|fence_gate|potted_|flower_pot|composter|chest)/.test(id)

export function verify() {
  const W = whole()
  const counts = []
  let prev = null
  for (let n = 1; n <= 4; n++) {
    const c = draw(n)
    if (prev) for (const [k, v] of prev.cells) {
      const now = c.cells.get(k)
      if (!now) throw new Error(`stage ${n} takes away ${v.id} at ${k}`)
      if (now.id !== v.id || JSON.stringify(now.props) !== JSON.stringify(v.props)) throw new Error(`stage ${n} changes ${v.id} at ${k} to ${now.id}`)
    }
    // everything joins the ground (y 0) through blocks touching it by a face or an edge (check.py's rule; the test also
    // counts corners, so this is the stricter one)
    const reach = new Set([...c.cells.keys()].filter(k => k.split(',')[1] === '0'))
    const todo = [...reach]
    while (todo.length) {
      const [x, y, z] = todo.pop().split(',').map(Number)
      for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) for (let dz = -1; dz <= 1; dz++) {
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 2) continue
        const k = `${x + dx},${y + dy},${z + dz}`
        if (c.cells.has(k) && !reach.has(k)) { reach.add(k); todo.push(k) }
      }
    }
    const loose = [...c.cells.keys()].filter(k => !reach.has(k))
    if (loose.length) throw new Error(`stage ${n}: ${loose.length} blocks not joined to the ground, e.g. ${loose.slice(0, 3)}`)
    const at = (x, y, z) => c.get(x, y, z)?.id?.replace('minecraft:', '')
    for (const [k, v] of c.cells) {
      const id = v.id.replace('minecraft:', '')
      const [x, y, z] = k.split(',').map(Number)
      // water stays put: never beside or over anything it would run into
      if (id === 'water') {
        for (const [dx, dy, dz] of [[1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1], [0, -1, 0]]) {
          const nb = at(x + dx, y + dy, z + dz)
          if ((y + dy >= 0 && !nb) || (nb && REPLACEABLE.test(nb) && nb !== 'water')) throw new Error(`stage ${n}: water at ${k} runs into ${nb ?? 'air'} at ${[x + dx, y + dy, z + dz]}`)
        }
      }
      if (id === 'farmland' && at(x, y + 1, z) && SOLIDISH(at(x, y + 1, z))) throw new Error(`stage ${n}: farmland at ${k} under ${at(x, y + 1, z)}`)
      if (/_door$/.test(id) && v.props.half === 'lower') {
        if (!/_door$/.test(at(x, y + 1, z) ?? '')) throw new Error(`door at ${k} not whole`)
        const [dx, dz] = { north: [0, 1], south: [0, 1], east: [1, 0], west: [1, 0] }[v.props.facing]
        for (const s of [1, -1]) for (const yy of [y, y + 1]) if (at(x + dx * s, yy, z + dz * s)) throw new Error(`stage ${n}: door at ${k} blocked by ${at(x + dx * s, yy, z + dz * s)}`)
      }
      if (/lantern/.test(id)) {
        if (v.props.hanging === 'true' ? !SOLIDISH(at(x, y + 1, z)) : !SOLIDISH(at(x, y - 1, z)) || /slab|leaves|chest|composter/.test(at(x, y - 1, z)))
          throw new Error(`stage ${n}: lantern at ${k} not held (${at(x, y + 1, z)} / ${at(x, y - 1, z)})`)
      }
      if (JOB.test(id)) throw new Error(`stage ${n}: ${id} at ${k} (a job block, bed, hive or calcite)`)
      if (/^(poppy|cornflower|allium|oxeye_daisy|azure_bluet|red_tulip|lily_of_the_valley|dandelion|azalea|flowering_azalea|short_grass|lilac|peony|rose_bush|fern|(?!potted_)[a-z_]+_sapling)$/.test(id) && v.props.half !== 'upper' && !/(grass_block|rooted_dirt|moss_block|dirt)/.test(at(x, y - 1, z) ?? ''))
        throw new Error(`stage ${n}: ${id} at ${k} on ${at(x, y - 1, z)}`)
      if (/^(wheat|carrots|beetroots)$/.test(id) && at(x, y - 1, z) !== 'farmland') throw new Error(`stage ${n}: ${id} at ${k} not on farmland`)
    }
    counts.push(c.cells.size - (prev ? prev.cells.size : 0))
    prev = c
  }
  const c = prev
  const at = (x, y, z) => c.get(x, y, z)?.id?.replace('minecraft:', '')
  const cells = [...c.cells].map(([k, v]) => [k.split(',').map(Number), v.id.replace('minecraft:', ''), v.props])
  const fail = m => { throw new Error(m) }
  const total = c.cells.size
  if (total <= 15000 || total > 16500) fail(`block count ${total}, not 15,001 to 16,500`)
  const comps = cells.filter(([, id]) => id === 'composter')
  if (comps.length !== 4) fail(`${comps.length} composters, not 4`)
  const chests = cells.filter(([, id]) => id === 'chest')
  if (chests.length < 4) fail(`${chests.length} chests`)
  if (cells.filter(([, id]) => id === 'water').length < 20) fail('fewer than 20 water')
  for (const crop of ['wheat', 'carrots', 'beetroots']) if (!cells.some(([, id]) => id === crop)) fail(`no ${crop}`)
  // the fields: farmland joined side by side, one up or down, or across water at the same level
  const farmland = cells.filter(([, id]) => id === 'farmland').map(([p]) => p)
  const fk = p => p.join(',')
  const fieldOf = new Map()
  let nf = 0
  for (const p of farmland) {
    if (fieldOf.has(fk(p))) continue
    nf++
    const todo = [p]; fieldOf.set(fk(p), nf)
    while (todo.length) {
      const [x, y, z] = todo.pop()
      for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) for (const dy of [-1, 0, 1]) {
        let q = [x + dx, y + dy, z + dz]
        if (dy === 0 && at(...q) === 'water') q = [x + 2 * dx, y, z + 2 * dz]
        if (at(...q) === 'farmland' && !fieldOf.has(fk(q))) { fieldOf.set(fk(q), nf); todo.push(q) }
      }
    }
  }
  const used = new Set()
  for (const [[x, y, z]] of comps) {
    const near = chests.filter(([[a, b, cz]]) => Math.abs(a - x) + Math.abs(b - y) + Math.abs(cz - z) <= 2)
    if (!near.length) fail(`composter ${x},${y},${z} has no chest within 2`)
    const inReach = farmland.filter(([a, b, cz]) => Math.abs(a - x) <= 16 && Math.abs(cz - z) <= 16 && Math.abs(b - y) <= 4)
    if (!inReach.length) fail(`composter ${x},${y},${z} has no farmland in reach`)
    const d2 = ([a, b, cz]) => (a - x) ** 2 + (b - y) ** 2 + (cz - z) ** 2
    const nearest = inReach.reduce((m, p) => (d2(p) < d2(m) ? p : m))
    const f = fieldOf.get(fk(nearest))
    const fields = new Set(inReach.map(p => fieldOf.get(fk(p))))
    if (fields.size !== 1) fail(`composter ${x},${y},${z} reaches ${fields.size} fields`)
    if (used.has(f)) fail(`two composters share field ${f}`)
    used.add(f)
  }
  if (nf !== 4) fail(`${nf} fields, not 4`)
  const xs = cells.map(([[x]]) => x), ys = cells.map(([[, y]]) => y), zs = cells.map(([[, , z]]) => z)
  if (Math.max(...xs) > 46 || Math.max(...zs) > 43 || Math.max(...ys) > 32) fail('outside the plot')
  if (Math.max(...ys) < 25) fail('nothing to stand on at 24')
  for (const [x, y, z] of [[-1, 0, -1], [0, 0, 0], [0, 1, 0], [1, 0, 0], [0, 0, 1]]) if (at(x, y, z)) fail(`the stone's corner is not clear at ${x},${y},${z}`)
  return { counts, total, stats: { composters: comps.length, chests: chests.length, water: cells.filter(([, id]) => id === 'water').length, farmland: farmland.length } }
}

export default function build(api) {
  const { counts, total, stats } = process.env.NOVERIFY ? { counts: [], total: draw(4).cells.size, stats: {} } : verify()
  console.error(`stage adds: ${counts.join(', ')}; whole ${total}; ${JSON.stringify(stats)}`)
  const stage = Number(process.env.STAGE ?? 4)
  const c = draw(stage)
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const name = `hanging_gardens_s${stage}` + (cut !== undefined ? `_cut${cut}` : '')
  return { structure: canvas.toStructure(api), metadata: { name } }
}
