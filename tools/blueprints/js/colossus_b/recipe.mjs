// The Colossus (a Wonder, version B: the Wayfarer), the recipe: assembles plan.mjs stage by stage on a StagedCanvas
// (a later stage may only add: it throws on any block an earlier stage drew), each stage in passes (block-out,
// openings, depth and detail, light), and returns the Wonder as it stands after STAGE (default 4) for the
// minecraft-builder skill's runner. verify() checks every stage against the brief (briefs/colossus.md) and prints its
// numbers. CUT=<y> cuts everything above y (a cutaway). stages.py saves the candidates.
import {
  SIZE, STAGES, MID, TOP, PALETTE as P, TIER, PLINTH as PL, ROAD, PIERS, PLAQUE, CROWN, BRAZIERS, FIGURE as F, LEGS,
  STAFF, HEAD, EYES, NOSE, CROOK, LANTERN,
} from './plan.mjs'
import { mix, stair, slab } from '../parts/canvas.mjs'
import { StagedCanvas } from '../parts/observatory_instruments.mjs'
import { stagesOnlyAdd } from '../parts/rotunda_helix.mjs'
import { figureSection, figureLimb, cagedLantern } from '../parts/colossus_b_figure.mjs'

const [W, H, D] = SIZE
const tierMix = mix(P.tier, 3)
const wallMix = mix(P.wall, 5)
const lowMix = mix(P.wallLow, 9)
const stone = (x, y, z) => (y <= 4 ? lowMix(x, y, z) : wallMix(x, y, z))
const inRoad = x => x >= ROAD.x0 && x <= ROAD.x1
const lerp = (a, b, t) => a + (b - a) * Math.max(0, Math.min(1, t))
const log = (axis = 'y') => [P.staff, { axis }]

export function draw() {
  const c = new StagedCanvas(SIZE)

  // ==== Stage I: The Plinth and the Road Arch ======================================================================
  c.begin(1)
  // Pass 1, the step tier (y 0-1): two dark courses, stair treads round the edge, cut through by the road
  for (let x = TIER.x0; x <= TIER.x1; x++) for (let z = TIER.z0; z <= TIER.z1; z++) {
    if (inRoad(x)) continue
    c.set(x, 0, z, tierMix(x, 0, z))
    const edge = z === 0 ? 'south' : z === D - 1 ? 'north' : x === 0 ? 'east' : x === W - 1 ? 'west' : null
    if (edge) stair(c, x, 1, z, P.tierStairs, edge)
    else c.set(x, 1, z, tierMix(x, 1, z))
  }
  // Pass 2, the plinth's core (y 2-10), the road arch through it (open y 0-4, the middle three to y 5, ceiling y 6)
  for (let x = PL.x0; x <= PL.x1; x++) for (let y = PL.y0; y <= PL.top; y++) for (let z = PL.z0; z <= PL.z1; z++) {
    if (inRoad(x) && y < ROAD.crown) continue
    if (inRoad(x) && y === ROAD.crown) {
      if (x === ROAD.x0) stair(c, x, y, z, P.dressStairs, 'west', 'top')
      else if (x === ROAD.x1) stair(c, x, y, z, P.dressStairs, 'east', 'top')
      continue
    }
    const face = x === PL.x0 || x === PL.x1 || z === PL.z0 || z === PL.z1
    if (face && y === 6) c.set(x, y, z, P.carve) // a carved band round the plinth
    else if ((x === ROAD.x0 - 1 || x === ROAD.x1 + 1) && y === 3 && z % 6 === 5) c.set(x, y, z, P.light) // the arch's lamps
    else c.set(x, y, z, stone(x, y, z))
  }
  // Pass 3, depth: pilasters a block proud (y 2-9), the arch's piers and voussoirs, the cornice (y 10)
  const FRONT_PIL = [2, 5, ...PIERS.flat(), 21, 24]
  const SIDE_PIL = [2, 7, 13, 19, 24]
  for (let y = 2; y <= 9; y++) {
    for (const x of FRONT_PIL) for (const z of [PL.z0 - 1, PL.z1 + 1]) c.set(x, y, z, P.dress)
    for (const z of SIDE_PIL) for (const x of [PL.x0 - 1, PL.x1 + 1]) c.set(x, y, z, P.dress)
  }
  for (const z of [PL.z0 - 1, PL.z1 + 1]) {
    for (let y = 2; y <= 5; y++) for (const x of [ROAD.x0 - 1, ROAD.x1 + 1]) c.set(x, y, z, P.dress) // jambs
    stair(c, ROAD.x0, ROAD.crown, z, P.dressStairs, 'west', 'top')
    stair(c, ROAD.x1, ROAD.crown, z, P.dressStairs, 'east', 'top')
    for (let x = ROAD.x0 - 1; x <= ROAD.x1 + 1; x++) c.set(x, ROAD.ceiling, z, x === MID ? P.carve : P.dress) // voussoirs, keystone
    for (let x = ROAD.x0 - 1; x <= ROAD.x1 + 1; x++) {
      slab(c, x, PLAQUE.y - 1, z, P.dressSlab, 'top') // the plaque's sill
      stair(c, x, PLAQUE.y + 1, z, P.dressStairs, z < MID ? 'south' : 'north', 'top') // its hood
    }
  }
  // the cornice: upside-down stairs round the plinth's top, over the pilasters
  for (let x = PL.x0 - 1; x <= PL.x1 + 1; x++) for (let z = PL.z0 - 1; z <= PL.z1 + 1; z++) {
    const ex = x === PL.x0 - 1 || x === PL.x1 + 1, ez = z === PL.z0 - 1 || z === PL.z1 + 1
    if (!(ex || ez)) continue
    if (ex && ez) { c.set(x, 10, z, P.dress); continue }
    stair(c, x, 10, z, P.wallStairs, ez ? (z < MID ? 'south' : 'north') : (x < MID ? 'east' : 'west'), 'top')
  }
  // Pass 4, the Founder's plaque: seven dark oak wall signs over the arch's front, facing the road
  for (const x of PLAQUE.xs) c.set(x, PLAQUE.y, PLAQUE.z, P.sign, { facing: 'north', waterlogged: 'false' })
  // the back's blank tablet in the same frame
  for (const x of PLAQUE.xs) c.set(x, PLAQUE.y, PL.z1 + 1, P.carve)
  // Pass 5, the upper tier (y 11-13): three smooth stone steps, each a block in from the last, treads of stairs
  // and a slab rim on the top (a full block under the staff's foot)
  const fires = new Set(BRAZIERS.map(p => p.join(',')))
  for (let y = CROWN.y0; y <= CROWN.top; y++) {
    const i = y - CROWN.y0
    for (let x = CROWN.x0 + i; x <= CROWN.x1 - i; x++) for (let z = CROWN.z0 + i; z <= CROWN.z1 - i; z++) {
      const ex = x === CROWN.x0 + i || x === CROWN.x1 - i, ez = z === CROWN.z0 + i || z === CROWN.z1 - i
      if (fires.has(`${x},${y},${z}`)) continue
      if (!(ex || ez) || (x === STAFF.x && z === STAFF.z)) c.set(x, y, z, P.crown)
      else if (y === CROWN.top) slab(c, x, y, z, P.crownSlab)
      else if (ex && ez) c.set(x, y, z, P.dress)
      else stair(c, x, y, z, P.dressStairs, ez ? (z < MID ? 'south' : 'north') : (x < MID ? 'east' : 'west'))
    }
  }
  // Pass 6, light: nine lit braziers (the step tier's corners, the upper tier's corners and its front middle)
  for (const [x, y, z] of BRAZIERS) c.set(x, y, z, P.fire, { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })

  // ==== Stage II: The Robes ========================================================================================
  c.begin(2)
  // Pass 1, boots and striding legs: the left thrown forward, the right pushing off behind (legs swept from hip to ankle)
  for (const L of LEGS) {
    const toe = L.step < 0 ? L.ankle - 2 : L.ankle - 1 // the toe of the boot, toward the front
    for (let x = L.x - 1; x <= L.x + 1; x++) {
      stair(c, x, F.foot, toe, P.bootStairs, 'south')
      for (let z = toe + 1; z <= L.ankle + 1; z++) c.set(x, F.foot, z, P.boot)
      for (let z = L.ankle - 1; z <= L.ankle + 1; z++) c.set(x, F.foot + 1, z, P.boot)
    }
    figureLimb(c, [L.x, F.foot + 2, L.ankle], [L.x, F.hem + 0.5, L.hip], 1.5, () => P.fold)
  }
  // Pass 2, the tunic: flaring from the waist to the hem, pleated (smooth stone folds a block proud on the rim)
  for (let y = F.hem; y < F.waist; y++) {
    const t = (y - F.hem) / (F.waist - 1 - F.hem)
    const shape = { cx: MID, cz: F.cz, rx: lerp(5.9, 4.4, t), rz: lerp(4.4, 3.1, t), p: 2.4 }
    figureSection(c, y, shape, (x, yy, z, { edge }) => (edge && (x + z) % 3 === 0 ? P.fold : P.robe))
  }
  // the belt (copper, a chiseled buckle at the front)
  figureSection(c, F.waist, { cx: MID, cz: F.cz, rx: 4.4, rz: 3.1, p: 2.4 }, (x, y, z, { edge }) =>
    edge ? (x === MID && z < F.cz ? P.copperBrooch : P.copper) : P.robe)
  // Pass 3, the robed body to the shoulders, a smooth stone sash from the left shoulder to the right hip
  for (let y = F.waist + 1; y <= F.shoulder; y++) {
    const sh = y >= F.shoulder - 1
    const shape = sh ? { cx: MID, cz: F.cz, rx: 6.6, rz: 3.0, p: 3 } : { cx: MID, cz: F.cz, rx: lerp(4.6, 5.8, (y - F.waist) / 5), rz: lerp(3.1, 3.3, (y - F.waist) / 5), p: 2.6 }
    figureSection(c, y, shape, (x, yy, z, { edge }) => (edge && Math.abs((x - MID) + (yy - 33)) <= 0.5 ? P.fold : P.robe))
  }

  // the wayfarer's satchel on the right hip, hung from the sash
  for (let y = 25; y <= 27; y++) for (let z = 11; z <= 14; z++) for (const x of [18, 19]) c.add(x, y, z, y === 27 ? P.robe : P.fold)

  // ==== Stage III: The Arms and the Cloak ==========================================================================
  c.begin(3)
  // Pass 1, the staff, planted on the upper tier, copper-banded
  for (let y = STAFF.y0; y <= STAFF.y1; y++) c.set(STAFF.x, y, STAFF.z, ...(STAFF.bands.includes(y) ? [P.copper] : log()))
  // Pass 2, the right arm down the east side, the hand wrapped round the staff
  for (let y = STAFF.hand[0]; y <= STAFF.hand[1]; y++) for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
    if (dx || dz) c.add(STAFF.x + dx, y, STAFF.z + dz, P.skin)
  }
  figureLimb(c, [19, 35.5, 12.5], [19.5, 31, 12], 1.3, () => P.robe)
  // the left arm thrown forward and down, pointing down the road
  figureLimb(c, [7.5, 35, 12.5], [6.5, 33.2, 9], 1.3, () => P.robe)
  figureLimb(c, [6.5, 33.2, 9], [6.5, 32.6, 6], 1.15, (x, y, z, t) => (t > 0.75 ? P.skin : P.robe))
  for (let x = 5; x <= 7; x++) for (let y = 32; y <= 33; y++) for (let z = 4; z <= 5; z++) c.add(x, y, z, P.skin) // the fist
  c.add(6, 33, 3, P.skin); c.add(6, 33, 2, P.skin) // the pointing finger
  c.add(7, 32, 3, P.skin) // the thumb
  // Pass 3, the cloak: a copper mantle on the shoulders, streaming back and down to a hem at y 20, pleated
  for (const x of [7, 8, 18, 19]) c.add(x, F.shoulder, 15, P.robe) // the shoulders' back corners, under the mantle
  c.add(19, 34, 11, P.robe) // the right arm's inside, against the staff
  for (let x = 7; x <= 19; x++) for (let z = 11; z <= 16; z++) {
    if (Math.abs(x - MID) <= 2 && z <= 14) continue // the neck and beard (stage IV)
    if (c.get(x, F.shoulder, z) || z === 16) slab(c, x, F.shoulder + 1, z, P.copperSlab)
  }
  c.set(8, F.shoulder + 1, 10, P.copperBrooch) // the brooch on the left shoulder
  c.set(18, F.shoulder + 1, 10, P.copperBrooch)
  for (let y = F.shoulder; y >= 20; y--) {
    const k = F.shoulder - y
    const zc = 16 + Math.floor(k / 4)
    const xl = Math.round(MID - 4.6 - k / 5), xr = Math.round(MID + 4.6 + k / 10)
    for (let x = xl; x <= xr; x++) {
      if (y === 20 && (x - xl) % 2 && x > xl + 1 && x < xr - 1) continue // the hem hangs in scallops
      c.add(x, y, zc, ...(y === 20 ? [P.copperStairs, { facing: 'north', half: 'top', shape: 'straight', waterlogged: 'false' }] : [P.copper, {}]))
      if ((x - xl) % 4 === 1 && y > 21 && y < F.shoulder) c.add(x, y, zc + 1, P.copperFold) // a fold a block proud
    }
    if (y <= 29) for (const [x, dz] of [[xl, -1], [xr, -1]]) c.add(x, y, zc + dz, P.copper) // the edges wrap forward
  }

  // ==== Stage IV: The Head and the Lantern =========================================================================
  c.begin(4)
  // Pass 1, the face first (the eyes, the nose a block proud, the brow), then the head round it
  for (const x of EYES.xs) c.set(x, EYES.y, EYES.z, P.eye)
  for (const y of NOSE.ys) c.set(NOSE.x, y, NOSE.z, P.skin)
  for (let x = MID - 1; x <= MID + 1; x++) stair(c, x, EYES.y + 1, EYES.z - 1, P.skinStairs, 'south', 'top') // the brow
  for (const x of [MID - 1, MID + 1]) slab(c, x, NOSE.ys[0], NOSE.z, P.hairSlab, 'top') // the moustache
  // the neck, then the beard down the chest
  for (let x = MID - 1; x <= MID + 1; x++) for (let z = 10; z <= 13; z++) c.add(x, F.shoulder + 1, z, P.skin)
  const BEARD = { 37: [[11, 15, 9], [12, 14, 8]], 36: [[11, 15, 9], [12, 14, 8]], 35: [[12, 14, 9], [13, 13, 8]], 34: [[12, 14, 9]], 33: [[13, 13, 9]] }
  for (const [y, rows] of Object.entries(BEARD)) for (const [a, b, z] of rows) for (let x = a; x <= b; x++) c.add(x, Number(y), z, P.hair)
  for (let y = HEAD.y0; y <= HEAD.y1; y++) {
    const r = y === HEAD.y1 ? [1.9, 2.5] : y === HEAD.y0 || y === HEAD.y1 - 1 ? [2.3, 3.0] : [2.6, 3.3]
    figureSection(c, y, { cx: HEAD.cx, cz: HEAD.cz, rx: r[0], rz: r[1], p: 4 }, (x, yy, z) =>
      yy === HEAD.y1 || z >= 14 || (yy >= 42 && z >= 11) || ((x === 11 || x === 15) && z >= 12 && yy >= 40) || (yy === HEAD.y0 && z >= 12) ? P.hair : P.skin)
  }
  c.add(10, EYES.y, 12, P.skin); c.add(16, EYES.y, 12, P.skin) // the ears
  for (const [x, y, z] of [[12, 38, 9], [14, 38, 9]]) c.add(x, y, z, P.hair) // the beard's roots
  for (const x of [MID - 2, MID + 2]) for (let z = 10; z <= 14; z++) c.add(x, F.shoulder + 1, z, P.hair) // the hair beside the neck
  // the hair falling to the shoulders behind
  for (let y = F.shoulder + 1; y < HEAD.y0; y++) for (let x = MID - 2; x <= MID + 2; x++) for (const z of [14, 15]) c.add(x, y, z, P.hair)
  // Pass 2, the crook over the staff's top, the lantern hung from it beside the head
  c.set(CROOK.x0, CROOK.y, STAFF.z, P.staffWood, { axis: 'y' })
  for (let x = CROOK.x0 + 1; x < CROOK.x1; x++) c.set(x, CROOK.y, STAFF.z, ...log('x'))
  c.set(CROOK.x1, CROOK.y, STAFF.z, P.staffWood, { axis: 'y' })
  cagedLantern(c, { x: LANTERN.x, z: LANTERN.z, ys: LANTERN.glow }, { glow: P.glow, glass: P.glass, post: P.copper, capSlab: P.copperSlab, drip: P.drip })
  return c
}

// --- verify: the brief's rules, with the numbers --------------------------------------------------------------------
const COPPER = ['cut_copper', 'cut_copper_stairs', 'cut_copper_slab', 'copper_block', 'chiseled_copper', 'copper_grate']
const CUT = ['cut_copper', 'cut_copper_stairs', 'cut_copper_slab']
const AGED = ['waxed', 'exposed', 'weathered', 'oxidized']
const FULL = id => !/(stairs|slab|pane|bars|sign|campfire|lightning_rod|wall|fence|chain|lantern|candle|door|trapdoor|carpet)$/.test(id)

export function verify(c = draw()) {
  const snaps = [1, 2, 3, 4].map(n => c.stageCanvas(n))
  const changed = stagesOnlyAdd(snaps)
  if (changed) throw new Error(changed)
  const perStage = []
  let before = 0
  for (let n = 1; n <= STAGES; n++) {
    const s = snaps[n - 1]
    const cells = [...s.cells.keys()].map(k => k.split(',').map(Number))
    const seen = new Set(cells.filter(([, y]) => y === 0).map(p => p.join(',')))
    const todo = [...seen].map(k => k.split(',').map(Number))
    while (todo.length) {
      const [x, y, z] = todo.pop()
      for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) for (let dz = -1; dz <= 1; dz++) {
        const k = `${x + dx},${y + dy},${z + dz}`
        if (s.cells.has(k) && !seen.has(k)) { seen.add(k); todo.push([x + dx, y + dy, z + dz]) }
      }
    }
    const floating = cells.filter(p => !seen.has(p.join(',')))
    if (floating.length) throw new Error(`stage ${n}: ${floating.length} blocks not grounded, e.g. ${floating.slice(0, 3).join(' ')}`)
    // standing by vanilla rules: wall signs on a backing, nothing that needs a floor on air
    for (const [k, v] of s.cells) {
      const [x, y, z] = k.split(',').map(Number)
      if (v.id.endsWith('_wall_sign')) {
        const [dx, dz] = { north: [0, 1], south: [0, -1], west: [1, 0], east: [-1, 0] }[v.props.facing]
        if (!s.get(x + dx, y, z + dz)) throw new Error(`stage ${n}: the wall sign at ${k} hangs on nothing`)
      }
      if (/(lantern|torch|candle|carpet|pressure_plate)$/.test(v.id) && v.id !== 'minecraft:sea_lantern' && !s.get(x, y - 1, z)) throw new Error(`stage ${n}: ${v.id} on air at ${k}`)
    }
    perStage.push(cells.length - before)
    before = cells.length
  }
  const s = snaps[3]
  const all = [...s.cells].map(([k, v]) => [...k.split(',').map(Number), v.id.slice(10), v.props])
  const top = Math.max(...all.map(p => p[1]))
  if (top !== TOP || Math.min(...all.map(p => p[1])) !== 0) throw new Error(`height: top ${top}, wants ${TOP}`)
  // the road arch: per x column of the whole box, the cells y 0..5 with no block front to back
  const openAt = (x, y) => { for (let z = 0; z < D; z++) if (s.get(x, y, z)) return false; return true }
  const passage = [], full = []
  for (let x = 0; x < W; x++) {
    const open = [0, 1, 2, 3, 4, 5].filter(y => openAt(x, y)).length
    if (open >= 5) passage.push(x)
    if (open === 6) full.push(x)
  }
  const contiguous = passage.length && passage[passage.length - 1] - passage[0] === passage.length - 1
  if (passage.length !== 5 || !contiguous || !full.length) throw new Error(`the arch: passage columns ${passage}, fully open ${full}`)
  const crown = Math.max(...full.map(x => { let h = 0; while (h < H && openAt(x, h)) h++; return h }))
  if (crown !== 6) throw new Error(`the arch's crown is ${crown} high, wants 6`)
  // the base's layer spans
  const span = y => { const xs = all.filter(p => p[1] === y).map(p => p[0]); return xs.length ? Math.max(...xs) - Math.min(...xs) + 1 : 0 }
  const spans = Array.from({ length: 20 }, (_, y) => span(y))
  const wide = spans.map((v, y) => (v >= 13 ? y : -1)).filter(y => y >= 0)
  const baseTop = Math.max(...wide)
  if (baseTop < 11 || baseTop > 13) throw new Error(`the base's highest wide layer is y ${baseTop}`)
  for (let y = 14; y <= 19; y++) if (spans[y] >= 13) throw new Error(`layer y ${y} spans ${spans[y]}`)
  // the plaque
  const signs = all.filter(p => p[3].endsWith('_wall_sign'))
  if (signs.length !== 7 || signs.some(p => p[3] !== 'dark_oak_wall_sign' || p[4].facing !== 'north' || p[1] !== signs[0][1])) throw new Error(`the plaque: ${signs.length} wall signs`)
  // the lantern
  const glow = all.filter(p => p[3] === 'glowstone')
  const glassy = id => id.includes('glass')
  for (const [x, y, z] of glow) for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    let k = 1
    while (s.name(x + k * dx, y, z + k * dz) === 'minecraft:glowstone') k++
    if (!glassy(s.name(x + k * dx, y, z + k * dz))) throw new Error(`glowstone at ${x},${y},${z} not caged toward ${dx},${dz}`)
  }
  const eyes = all.filter(p => p[3] === 'emerald_block')
  if (eyes.length < 2 || eyes.some(p => p[1] !== eyes[0][1] || p[2] !== eyes[0][2]) || eyes[0][1] < 34) throw new Error('the eyes')
  const [ey, ez] = [eyes[0][1], eyes[0][2]]
  const lowGlow = Math.min(...glow.map(p => p[1]))
  if (lowGlow < ey) throw new Error(`the lowest glowstone (y ${lowGlow}) is under the eyes (y ${ey})`)
  const topGlow = glow.filter(p => p[1] >= TOP - 4).length
  if (topGlow < 3) throw new Error(`${topGlow} glowstone in the top five layers`)
  const glassCount = all.filter(p => glassy(p[3])).length
  if (glassCount < 4) throw new Error(`${glassCount} glass`)
  // the nose: toward the nearer edge (the front), 1-3 blocks in front of the eyes, within a column of their middle
  const emid = (Math.min(...eyes.map(p => p[0])) + Math.max(...eyes.map(p => p[0]))) / 2
  if (ez > D - 1 - ez) throw new Error('the face is nearer the back')
  const nose = all.filter(p => Math.abs(p[0] - emid) <= 1 && p[1] <= ey && p[1] >= ey - 8 && ez - p[2] >= 1 && ez - p[2] <= 3).length
  if (nose < 2) throw new Error(`the nose: ${nose}`)
  // braziers, copper, stone, total
  const fires = all.filter(p => p[3] === 'campfire')
  if (fires.length !== 9 || fires.some(p => p[4].lit !== 'true')) throw new Error(`${fires.length} campfires`)
  if (all.some(p => p[3].includes('copper') && AGED.some(a => p[3].includes(a)))) throw new Error('waxed or aged copper')
  const copper = all.filter(p => COPPER.includes(p[3])).length
  const cut = all.filter(p => CUT.includes(p[3])).length
  if (copper < 333 || copper > 407 || cut < 150) throw new Error(`copper ${copper} (cut ${cut}), wants 333-407 (150 cut)`)
  const pale = all.filter(p => p[3] === 'polished_diorite' || p[3] === 'smooth_stone').length
  if (pale < 500) throw new Error(`polished diorite + smooth stone: ${pale}`)
  const total = all.length
  if (total < 8100 || total > 9900) throw new Error(`block count ${total}, wants 8,100-9,900`)
  if (all.some(p => p[3] === 'calcite')) throw new Error('calcite')
  // panes meet a block at both ends (a run along x or z, never ending in air)
  for (const [x, y, z, id] of all) {
    if (!id.endsWith('_pane') && id !== 'iron_bars') continue
    const solid = (a, b) => { const n = s.name(a, y, b).slice(10); return n !== 'air' && (FULL(n) || n.endsWith('_pane')) }
    const ns = solid(x, z - 1) && solid(x, z + 1), ew = solid(x - 1, z) && solid(x + 1, z)
    if (!ns && !ew) throw new Error(`a pane at ${x},${y},${z} ends in air`)
  }
  return { perStage, total, top, passage, crown, baseTop, spans, signs: signs.length, signY: signs[0][1], glow: glow.length, topGlow, lowGlow, glassCount, eyes: [ey, ez, emid], nose, fires: fires.length, copper, cut, pale }
}

export default function build(api) {
  const c = draw()
  const v = verify(c)
  console.error(`colossus_b: ${JSON.stringify(v)}`)
  const n = Number(process.env.STAGE ?? STAGES)
  const s = c.stageCanvas(n)
  const cut = process.env.CUT
  const canvas = cut !== undefined ? s.cutAbove(Number(cut)) : s
  const name = `colossus_b_s${n}${cut !== undefined ? `_cut${cut}` : ''}`
  return { structure: canvas.toStructure(api), metadata: { name } }
}
