// The Colossus (a Wonder, 52.6 version A, "Astride the Road"), the recipe: assembles plan.mjs stage by stage on a
// StagedCanvas (a later stage may only add: it throws on any block an earlier stage drew, and stagesOnlyAdd checks the
// snapshots again), each stage in passes (block-out, structure, depth and detail, light), and returns the Wonder as it
// stands after STAGE (default 4) for the minecraft-builder skill's runner. verify() checks every stage against the brief
// and prints its numbers. CUT=<y> cuts everything above y (a cutaway). stages.py saves the candidates.
import { SIZE, STAGES, MID, PALETTE as P, MASSES as M, BRAZIERS } from './plan.mjs'
import { mix, stair, slab, StagedCanvas, stagesOnlyAdd } from '../parts/index.mjs'
import { sculptOval, sculptBody, sculptLimb, sculptDrape } from '../parts/colossus_a_sculpt.mjs'

const STEP = mix(P.step, 11), DIE = mix(P.die, 23)
const [W, , D] = SIZE
const X1 = W - 1, Z1 = D - 1
const inArch = x => x >= M.arch.x0 && x <= M.arch.x1
const ring = (x, z, i) => x === i || x === X1 - i || z === i || z === Z1 - i
const inward = (x, z, i) => (z === i ? 'south' : z === Z1 - i ? 'north' : x === i ? 'east' : 'west')
const outward = (x, z, i) => ({ south: 'north', north: 'south', east: 'west', west: 'east' })[inward(x, z, i)]
const usd = (c, x, y, z, id, facing) => stair(c, x, y, z, id, facing, 'top')
const fire = (c, x, y, z, facing = 'north') => c.set(x, y, z, P.fire, { facing, lit: 'true', signal_fire: 'false', waterlogged: 'false' })
const neckCells = sculptOval(MID, 11, 1.6, 1.6).map(({ x, z }) => `${x},${z}`)

export function draw() {
  const c = new StagedCanvas(SIZE)
  const A = M.arch, Dm = M.die, di = Dm.inset

  // ================= Stage I: The Plinth and the Road Arch ==========================================================
  c.begin(1)
  // Pass 1, block-out: three stair-edged courses, the corner piers, the gate die, the cornice and the deck
  for (const y of M.steps.y) for (let x = y; x <= X1 - y; x++) for (let z = y; z <= Z1 - y; z++) {
    if (inArch(x)) continue
    if (ring(x, z, y)) stair(c, x, y, z, P.stepStairs, inward(x, z, y))
    else c.set(x, y, z, STEP)
  }
  for (const [px, pz] of [[0, 0], [X1 - 2, 0], [0, Z1 - 2], [X1 - 2, Z1 - 2]])
    for (let x = px; x < px + M.piers.size; x++) for (let z = pz; z < pz + M.piers.size; z++)
      for (let y = 0; y <= M.piers.top; y++) c.set(x, y, z, y === M.piers.top ? P.dress : STEP)
  for (let y = Dm.y0; y <= Dm.cornice; y++) for (let x = di; x <= X1 - di; x++) for (let z = di; z <= Z1 - di; z++) {
    if (inArch(x) && y <= A.ceiling) continue
    c.set(x, y, z, DIE)
  }
  // the road arch: springers along both walls at y 5, the crown open to y 5 and vaulted at y 6, sea lanterns let in
  for (let z = 0; z <= Z1; z++) {
    if (z < di || z > Z1 - di) continue
    usd(c, A.x0, A.crown, z, P.dressStairs, 'west'); usd(c, A.x1, A.crown, z, P.dressStairs, 'east')
    for (let x = A.x0; x <= A.x1; x++) c.set(x, A.ceiling, z, A.lights.includes(z) && x === MID ? P.vaultLight : P.voussoir)
  }
  // Pass 2, structure: the cornice (upside-down stairs, back to the die), the deck, the balustrade
  for (let x = di - 1; x <= X1 - di + 1; x++) for (let z = di - 1; z <= Z1 - di + 1; z++) {
    if (ring(x, z, di - 1)) usd(c, x, Dm.cornice, z, P.dressStairs, inward(x, z, di - 1))
    c.set(x, Dm.deck, z, ring(x, z, di) ? P.dress : ring(x, z, di + 3) ? P.skin : P.deck) // a dark border and a white frame in the paving
    if (ring(x, z, di - 1)) c.set(x, Dm.balustrade, z, P.dressWall)
  }
  // Pass 3, depth: proud pilasters with copper capitals, recessed panels between them, the arch's voussoirs and keystone
  const pil = (x, z, cap = P.copper) => { for (let y = Dm.y0; y < Dm.frieze; y++) c.set(x, y, z, P.dress); c.set(x, Dm.frieze, z, cap) }
  for (const x of M.pilasters.front) pil(x, di - 1)
  for (const x of M.pilasters.back) pil(x, Z1 - di + 1)
  for (const z of M.pilasters.side) { pil(di - 1, z, P.key); pil(X1 - di + 1, z, P.key) } // copper capitals front and back only
  for (let x = di; x <= X1 - di; x++) for (let z = di; z <= Z1 - di; z++) if (ring(x, z, di)) c.set(x, Dm.frieze, z, P.dress)
  const panel = (x, y, z) => c.clear(x, y, z)
  // recesses: a block deep, y 4..8, a block in from each pilaster (the front ones flank the arch)
  const spans = (list, lo, hi, m = 2) => { const out = []; const ps = [lo - 1, ...list, hi + 1]; for (let i = 0; i < ps.length - 1; i++) if (ps[i + 1] - ps[i] >= 2 * m + 1) out.push([ps[i] + m, ps[i + 1] - m]); return out }
  const recessY = [4, 8]
  for (const [a, b] of spans(M.pilasters.front, di, X1 - di)) for (let x = a; x <= b; x++) if (x < A.x0 - 2 || x > A.x1 + 2)
    for (let y = recessY[0]; y <= recessY[1]; y++) { panel(x, y, di); panel(x, y, Z1 - di) }
  for (const [a, b] of spans(M.pilasters.side, di, Z1 - di, 1)) for (let z = a; z <= b; z++)
    for (let y = recessY[0]; y <= recessY[1]; y++) { panel(di, y, z); panel(X1 - di, y, z) }
  for (const zf of [di, Z1 - di]) {
    for (let y = Dm.y0; y <= A.ceiling; y++) { c.set(A.x0 - 1, y, zf, P.voussoir); c.set(A.x1 + 1, y, zf, P.voussoir) }
    for (let x = A.x0 - 1; x <= A.x1 + 1; x++) c.set(x, A.ceiling + 1, zf, x === MID ? P.key : P.voussoir)
    c.set(MID, A.ceiling, zf, P.key)
  }
  // Pass 4, detail: the plaque (seven dark oak wall signs over the arch, facing the road), the braziers, lamps in the
  // front recesses
  for (let x = M.plaque.x[0]; x <= M.plaque.x[1]; x++) c.set(x, M.plaque.y, M.plaque.z, P.sign, { facing: 'north', waterlogged: 'false' })
  for (const [x, y, z] of BRAZIERS) fire(c, x, y, z, z > MID ? 'south' : 'north')
  for (const [a, b] of spans(M.pilasters.front, di, X1 - di)) if (b < A.x0 - 2 || a > A.x1 + 2) {
    const x = Math.round((a + b) / 2)
    for (const zf of [di, Z1 - di]) c.set(x, recessY[1], zf, P.lamp, { hanging: 'true', waterlogged: 'false' })
  }
  for (const [a, b] of spans(M.pilasters.side, di, Z1 - di, 1)) if (b - a === 4) { // a lamp in each long side bay
    const z = (a + b) / 2
    for (const xf of [di, X1 - di]) c.set(xf, recessY[1], z, P.lamp, { hanging: 'true', waterlogged: 'false' })
  }

  // ================= Stage II: The Robes (the stance, the tunic, the torso) =========================================
  c.begin(2)
  const F = M.feet
  for (const [f, toeZ] of [[F.left, F.left.z - 2], [F.right, F.right.z - 2]]) {
    for (let x = f.x - 1; x <= f.x + 1; x++) {
      stair(c, x, 13, toeZ, P.skinStairs, 'south')
      for (let z = toeZ + 1; z <= f.z + 1; z++) c.set(x, 13, z, P.skin)
      for (let z = f.z - 1; z <= f.z + 1; z++) for (let y = M.shins[0]; y <= M.shins[1]; y++) c.set(x, y, z, y === M.shins[0] ? P.robe : P.skin)
    }
  }
  const tunicR = y => ({ rx: 5.7 - (y - M.tunic.y0) * 0.25, rz: 3.4 - (y - M.tunic.y0) * 0.12 })
  sculptBody(c, M.tunic.y0, M.tunic.y1, y => ({ cx: MID, cz: M.tunic.cz, ...tunicR(y) }), (x, y, z, { edge }) =>
    edge && x % 2 === 0 ? P.skin : P.robe)
  // pleats: every other column of the tunic's front stands a block proud
  for (let y = M.tunic.y0; y <= M.tunic.y1 - 1; y++) {
    const { rx, rz } = tunicR(y)
    for (const { x, z, edge } of sculptOval(MID, M.tunic.cz, rx, rz)) if (edge && z < M.tunic.cz - 1 && x % 2 === 0 && !c.get(x, y, z - 1))
      c.set(x, y, z - 1, P.skin)
  }
  sculptBody(c, M.belt, M.belt, () => ({ cx: MID, cz: M.torso.cz, rx: 4.7, rz: 2.9 }), (x, y, z, { edge, front }) =>
    !edge ? P.robe : x === MID && front ? P.copperFancy : P.copper)
  const torsoR = y => ({ rx: 4.8 + (y - M.torso.y0) * 0.17, rz: y >= 30 && y <= 32 ? 3.1 : 2.9 })
  const sash = (x, y) => Math.abs(x - (9 + (33 - y) * 10 / 6)) <= 1
  sculptBody(c, M.torso.y0, M.torso.y1, y => ({ cx: MID, cz: M.torso.cz, ...torsoR(y) }), (x, y, z, { edge }) =>
    edge && sash(x, y) ? P.skin : P.robe)

  // ================= Stage III: The Arms and the Cloak ==============================================================
  c.begin(3)
  // the right arm thrown up (sleeve to the elbow, bare forearm, the fist under the lantern)
  sculptLimb(c, M.rightArm, 1.3, (x, y, z, t) => (t === 0 && y <= 35 ? P.robe : P.skin))
  sculptBody(c, 40, 41, () => ({ cx: M.lantern.cx, cz: M.lantern.cz, rx: 1.5, rz: 1.5 }), () => P.skin)
  // the left arm down, the hand at the hip holding the rolled plan
  sculptLimb(c, M.leftArm, 1.3, (x, y, z, t) => (t === 0 ? P.robe : P.skin))
  for (let y = M.plan.y[0]; y <= M.plan.y[1]; y++) c.set(M.plan.x, y, M.plan.z, P.plan, { axis: 'y' })
  // the mantle over the shoulders (round the neck's place) with a chiseled clasp each side
  for (const { x, z, edge } of sculptOval(MID, M.torso.cz, 5.7, 3.1)) {
    if (neckCells.includes(`${x},${z}`)) continue
    if (z >= M.cloak.z0 - 1) c.add(x, M.cloak.top, z, P.copper)
    else if (edge && z <= M.torso.cz - 1 && (x === MID - 3 || x === MID + 3)) c.add(x, M.cloak.top, z, P.copperFancy)
    else slab(c, x, M.cloak.top, z, z < M.torso.cz ? P.robeSlab : P.copperSlab) // the tunic's shoulder in front, the mantle behind
  }
  // the cloak: a sheet falling behind, leaning back to z 20, flaring to 15 wide, gathered to 11 by y 19, its folds
  // striped in plain copper on the cut copper, pooling on the deck
  const back = y => M.cloak.z0 + Math.round((33 - y) * 0.22) + Math.max(0, 17 - y) // the last rows sweep back as a train
  const half = y => (y >= 33 ? 5 : y >= 30 ? 6 : y >= 21 ? 7 : y === 20 ? 6 : 5) // a bell: out from the shoulders, gathered again by y 19
  const pleat = (x, y) => (x - MID) % 3 === 0 && Math.abs(x - MID) < half(y) - 1 && y <= 31 && y > M.cloak.hem
  sculptDrape(c, { y0: M.cloak.hem, y1: 33, back: (y, x) => back(y), left: y => MID - half(y), right: y => MID + half(y), thick: () => 1 },
    (x, y) => (pleat(x, y) ? P.copperBlock : P.copper))
  for (const x of [MID - 3, MID + 3]) c.add(x, 33, M.cloak.z0 - 1, P.robe) // the shoulders' backs under the mantle
  // the cloak's marble lining, a block inside the copper (it shows at the edges and from under the hem)
  sculptDrape(c, { y0: M.cloak.hem + 1, y1: 32, back: (y, x) => back(y) - 1, left: y => MID - half(y), right: y => MID + half(y), thick: () => 1 },
    () => P.robe)
  // the cloak's edges curl forward round the figure where it flares widest (copper over the lining)
  for (let y = 22; y <= 30; y++) for (const x of [MID - half(y), MID + half(y)]) c.set(x, y, back(y) - 1, P.copper)
  // the hem pools on the deck behind (slabs, a block lower each step back)
  for (let x = MID - 5; x <= MID + 5; x++) slab(c, x, M.cloak.hem, back(M.cloak.hem) + 1, P.copperSlab)

  // ================= Stage IV: The Head and the Lantern =============================================================
  c.begin(4)
  const H = M.head
  for (const k of neckCells) { const [x, z] = k.split(',').map(Number); c.set(x, 34, z, P.skin) }
  const headR = y => (y === H.y0 ? { rx: 2.3, rz: 2.5 } : y === H.y1 ? { rx: 2.2, rz: 2.4 } : { rx: 2.6, rz: 2.8 })
  sculptBody(c, H.y0, H.y1, y => ({ cx: H.cx, cz: H.cz, ...headR(y) }), (x, y, z) =>
    z >= H.cz || y === H.y1 ? P.robe : y <= H.y0 + 1 && !(y === H.y0 + 1 && x === H.cx) ? P.robe : P.skin)
  for (let y = H.y0; y < H.y1; y++) for (const x of [H.cx - 2, H.cx + 2]) c.add(x, y, H.cz + 2, P.robe) // the hair full at the back
  for (let y = H.y0; y < H.y1; y++) for (const x of [H.cx - 3, H.cx + 3]) c.add(x, y, H.cz, y === 37 ? P.skin : P.robe) // the ears in the hair
  // the face: deep-set emerald eyes under the brow, the nose standing out to z 7, the beard under the chin
  for (const x of H.eyes.x) { c.clear(x, H.eyes.y, H.eyes.z - 1); c.set(x, H.eyes.y, H.eyes.z, P.eye) }
  for (const [x, y, z] of H.nose) c.set(x, y, z, P.skin)
  slab(c, H.cx, 35, 7, P.robeSlab, 'top') // the beard's point
  // hair and the copper laurel
  for (const { x, z } of sculptOval(H.cx, H.cz + 0.3, 1.6, 1.8)) c.set(x, 41, z, P.robe)
  const crown = new Set(sculptOval(H.cx, H.cz, 2.2, 2.4).map(({ x, z }) => `${x},${z}`))
  for (const { x, z } of sculptOval(H.cx, H.cz, 3.0, 3.2)) if (!crown.has(`${x},${z}`)) {
    if (x === H.cx && z < H.cz) c.set(x, 40, z, P.copperFancy) // the laurel's boss over the brow
    else slab(c, x, 40, z, P.copperSlab)
  }
  // the lantern on the fist: copper base, three glowstone caged in glass, copper grate posts, a copper hip cap
  const L = M.lantern
  for (let x = L.cx - 1; x <= L.cx + 1; x++) for (let z = L.cz - 1; z <= L.cz + 1; z++) {
    const centre = x === L.cx && z === L.cz, corner = x !== L.cx && z !== L.cz
    const dir = x < L.cx ? 'east' : x > L.cx ? 'west' : z < L.cz ? 'south' : 'north'
    if (centre) c.set(x, L.base, z, P.copper)
    else if (corner) c.set(x, L.base, z, P.copper)
    else usd(c, x, L.base, z, P.copperStairs, dir)
    for (let y = L.glow[0]; y <= L.glow[1]; y++) c.set(x, y, z, centre ? P.glow : corner && y === L.glow[0] + 1 ? P.copperGrate : P.glass)
    if (centre) c.set(x, L.cap, z, P.copperFancy)
    else if (corner) slab(c, x, L.cap, z, P.copperSlab)
    else stair(c, x, L.cap, z, P.copperStairs, dir)
  }
  return c
}

// ---------------------------------------------------------------------------------------------------------------------
const COPPER = ['cut_copper', 'cut_copper_stairs', 'cut_copper_slab', 'copper_block', 'chiseled_copper', 'copper_grate']
const CUT = ['cut_copper', 'cut_copper_stairs', 'cut_copper_slab']
const BANNED = ['calcite', 'bedrock', 'barrier', 'fire', 'sand', 'gravel', 'water', 'lava', 'scaffolding']

function connected(s) {
  const cells = [...s.cells.keys()].map(k => k.split(',').map(Number))
  const seen = new Set(cells.filter(([, y]) => y === 0).map(p => p.join(',')))
  const todo = [...seen].map(k => k.split(',').map(Number))
  while (todo.length) {
    const [x, y, z] = todo.pop()
    for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) for (let dz = -1; dz <= 1; dz++) {
      if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 2) continue // face or edge, as check.py floods
      const k = `${x + dx},${y + dy},${z + dz}`
      if (s.cells.has(k) && !seen.has(k)) { seen.add(k); todo.push([x + dx, y + dy, z + dz]) }
    }
  }
  return cells.filter(p => !seen.has(p.join(',')))
}

export function verify(c = draw()) {
  const snaps = [1, 2, 3, 4].map(n => c.stageCanvas(n))
  const changed = stagesOnlyAdd(snaps)
  if (changed) throw new Error(changed)
  const fail = m => { throw new Error(`colossus_a: ${m}`) }
  const perStage = []
  for (let n = 1; n <= STAGES; n++) {
    const s = snaps[n - 1]
    const floating = connected(s)
    if (floating.length) fail(`stage ${n}: ${floating.length} blocks not grounded, e.g. ${floating.slice(0, 3).join(' ')}`)
    for (const [k, v] of s.cells) {
      const id = v.id.slice(10)
      if (BANNED.includes(id) || id.includes('waxed') || /exposed|weathered|oxidized/.test(id)) fail(`stage ${n}: ${id} at ${k}`)
      const [x, y, z] = k.split(',').map(Number)
      if (id === 'lantern' && !(v.props.hanging === 'true' ? s.get(x, y + 1, z) : s.get(x, y - 1, z))) fail(`stage ${n}: lantern at ${k} on nothing`)
      if (id.endsWith('_wall_sign')) { const [fx, fz] = { north: [0, 1], south: [0, -1], east: [-1, 0], west: [1, 0] }[v.props.facing]; if (!s.get(x + fx, y, z + fz)) fail(`sign at ${k} has no backing`) }
      if (id === 'campfire' && !s.get(x, y - 1, z)) fail(`campfire at ${k} on air`)
    }
    perStage.push(s.cells.size)
  }
  const s = snaps[3]
  const cells = [...s.cells.keys()].map(k => k.split(',').map(Number))
  const ids = [...s.cells.values()].map(v => v.id.slice(10))
  const count = list => ids.filter(i => list.includes(i)).length
  const lo = i => Math.min(...cells.map(p => p[i])), hi = i => Math.max(...cells.map(p => p[i]))
  const top = hi(1)
  if (top !== 46 || lo(1) !== 0) fail(`height: y ${lo(1)}..${top}, wants 0..46`)
  // the road arch: per x column of the whole box, how many of y 0..5 are open from the front edge to the back edge
  const [x0, x1, z0, z1] = [lo(0), hi(0), lo(2), hi(2)]
  const openRows = x => { let n = 0; for (let y = 0; y <= 5; y++) { let open = true; for (let z = z0; z <= z1 && open; z++) if (s.get(x, y, z)) open = false; if (open) n++ } return n }
  const passage = [], crown = []
  for (let x = x0; x <= x1; x++) { const n = openRows(x); if (n >= 5) passage.push(x); if (n === 6) crown.push(x) }
  if (passage.length !== 5 || passage[4] - passage[0] !== 4 || !crown.length) fail(`road arch columns ${passage} (crown ${crown})`)
  for (const x of passage) for (let z = z0; z <= z1; z++) if (s.get(x, 0, z)) fail(`arch floor drawn at ${x},0,${z}`)
  // the base: the highest layer y 0..19 spanning >= 13 in x is 11..13; above 13 every layer < 13 wide to y 19
  const span = y => { const xs = cells.filter(p => p[1] === y).map(p => p[0]); return xs.length ? Math.max(...xs) - Math.min(...xs) + 1 : 0 }
  let baseTop = -1
  for (let y = 0; y <= 19; y++) if (span(y) >= 13) baseTop = y
  if (baseTop < 11 || baseTop > 13) fail(`base top ${baseTop}`)
  const upper = []; for (let y = 14; y <= 19; y++) upper.push(span(y))
  // signs: exactly seven dark oak wall signs, one row (same y and facing), facing north; no other wall sign
  const signs = [...s.cells].filter(([, v]) => v.id.endsWith('_wall_sign'))
  if (signs.length !== 7 || signs.some(([, v]) => v.id !== 'minecraft:dark_oak_wall_sign' || v.props.facing !== 'north')) fail(`signs: ${signs.length}`)
  if (new Set(signs.map(([k]) => k.split(',')[1])).size !== 1) fail('the signs are not in one row')
  // the lantern: glowstone caged in glass, at or above the eyes, three in the top five layers
  const glow = cells.filter(p => s.name(...p) === 'minecraft:glowstone')
  const eyes = cells.filter(p => s.name(...p) === 'minecraft:emerald_block')
  if (eyes.length < 2) fail('fewer than two emerald eyes')
  const firstEye = [...s.cells.keys()].map(k => k.split(',').map(Number)).find(p => s.name(...p) === 'minecraft:emerald_block')
  if (eyes.some(p => p[1] !== eyes[0][1] || p[2] !== eyes[0][2]) || eyes[0][1] < 34) fail(`eyes ${eyes.join(' ')}`)
  const eyeY = eyes[0][1], eyeZ = eyes[0][2], eyeX = (Math.min(...eyes.map(p => p[0])) + Math.max(...eyes.map(p => p[0]))) / 2
  for (const [x, y, z] of glow) {
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      let k = 1
      while (s.name(x + dx * k, y, z + dz * k) === 'minecraft:glowstone') k++
      if (!s.name(x + dx * k, y, z + dz * k).includes('glass')) fail(`glowstone at ${x},${y},${z} not caged`)
    }
  }
  const lowGlow = Math.min(...glow.map(p => p[1])), topGlow = glow.filter(p => p[1] >= top - 4).length
  if (!glow.length || lowGlow < eyeY || topGlow < 3) fail(`glowstone: ${glow.length}, lowest ${lowGlow}, top five ${topGlow}`)
  const glass = ids.filter(i => i.includes('glass')).length
  if (glass < 4) fail(`glass ${glass}`)
  // the nose: >= 2 blocks within a column of the eyes' middle, eye height down to 8 below, 1-3 in front (toward z 0)
  const front = eyeZ <= (z0 + z1) / 2 ? -1 : 1
  if (front !== -1) fail('the face is nearer the back')
  const nose = cells.filter(([x, y, z]) => Math.abs(x - eyeX) <= 1 && y <= eyeY && y >= eyeY - 8 && (z - eyeZ) * front >= 1 && (z - eyeZ) * front <= 3)
  if (nose.length < 2) fail(`nose ${nose.length}`)
  // braziers: exactly nine campfires, all lit
  const fires = [...s.cells.values()].filter(v => v.id === 'minecraft:campfire')
  if (fires.length !== 9 || fires.some(v => v.props.lit !== 'true')) fail(`campfires ${fires.length}`)
  const copper = count(COPPER), cut = count(CUT), white = count(['polished_diorite', 'smooth_stone'])
  if (copper < 333 || copper > 407) fail(`copper ${copper}, wants 333-407`)
  if (cut < 150) fail(`cut copper ${cut}`)
  if (white < 500) fail(`polished diorite + smooth stone ${white}`)
  const total = s.cells.size
  if (total < 8100 || total > 9900) fail(`total ${total}, wants 8,100-9,900`)
  // the copper per stage (the cloak's stage should carry the most)
  const copperBy = snaps.map(sn => [...sn.cells.values()].filter(v => COPPER.includes(v.id.slice(10))).length)
  return {
    perStage: perStage.map((v, i) => (i ? v - perStage[i - 1] : v)), total, top, size: [x1 - x0 + 1, top + 1, z1 - z0 + 1],
    arch: { columns: passage, crown }, baseTop, upperSpans: upper,
    signs: signs.length, signRow: signs.map(([k]) => k).join(' '), campfires: fires.length,
    copper, cut, copperByStage: copperBy.map((v, i) => (i ? v - copperBy[i - 1] : v)), dioriteSmooth: white,
    glowstone: glow.length, glowTop5: topGlow, lowestGlow: lowGlow, glass, eyes: eyes.map(p => p.join(',')), firstEye: firstEye.join(','), nose: nose.length,
  }
}

export default function build(api) {
  const c = draw()
  const v = verify(c)
  console.error(`colossus_a: ${JSON.stringify(v)}`)
  const n = Number(process.env.STAGE ?? STAGES)
  const s = c.stageCanvas(n)
  const cut = process.env.CUT
  const canvas = cut !== undefined ? s.cutAbove(Number(cut)) : s
  return { structure: canvas.toStructure(api), metadata: { name: `colossus_a_s${n}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
