// The Observatory (a Wonder), the recipe: assembles plan.mjs stage by stage on a StagedCanvas (a later stage may only
// add), each stage in passes (block-out, openings, depth and detail, interior, ground), and returns the Wonder as it
// stands after STAGE (default 4) for the minecraft-builder skill's runner. CUT=<y> cuts everything above y.
// stages.py runs it once per stage and saves the stage snapshots as candidates (build/rebuild/observatory/).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE as P, MASSES as M, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  mix, stair, door, furnitureParts, lampPost,
  StagedCanvas, roundWall, roundCourse, skyDome, lineTube, quadrantBowl, gnomonStair,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const deg = a => (a * Math.PI) / 180
const angleOf = (x, z, cx, cz) => ((Math.atan2(x - cx, z - cz) * 180) / Math.PI + 360) % 360 // 0 = toward +z

function furnish(c, f) {
  if (f.part === 'block') c.set(...f.pos, f.block, f.props ?? {})
  else if (f.part === 'postLamp') lampPost(c, ...f.pos, `${P.wood}_fence`)
  else furnitureParts[f.part](c, f)
}

export function draw() {
  const c = new StagedCanvas(SIZE)
  const mass = mix(P.massAlt, 52)
  const { podium: pd, hall: hl, gnomon: gn, drum: dr } = M

  // === Stage I: the Star Terrace and the Chart Hall ================================================================
  c.begin(1)
  // Pass 1, block-out: the court, the podium's walls and deck
  for (let x = 0; x <= 30; x++) for (let z = 0; z <= 30; z++) {
    const border = x === 0 || x === 30 || z === 0 || z === 30
    c.set(x, 0, z, border ? P.plinth : (x + z) % 2 ? P.tile : P.floor)
  }
  for (const [gx, gz] of OUTSIDE.gardens) for (let x = gx; x < gx + 3; x++) for (let z = gz; z < gz + 3; z++) {
    c.set(x, 0, z, 'grass_block', { snowy: 'false' })
    const flower = ['allium', 'azure_bluet', 'cornflower', 'oxeye_daisy', 'lily_of_the_valley'][(x * 7 + z * 3) % 5]
    if ((x + z) % 2 === 0) c.set(x, 1, z, flower)
  }
  for (let x = pd.x0; x <= pd.x1; x++) for (let z = pd.z0; z <= pd.z1; z++) {
    const wall = x === pd.x0 || x === pd.x1 || z === pd.z0 || z === pd.z1
    const inner = !wall && (x === pd.x0 + 1 || x === pd.x1 - 1 || z === pd.z0 + 1 || z === pd.z1 - 1)
    if (wall || inner) { c.set(x, 1, z, wall ? P.plinth : mass); for (let y = 2; y < pd.top; y++) c.set(x, y, z, mass) }
    if (!wall) c.set(x, 0, z, P.floor) // the hall's floor, plain where the court is chequered
    c.set(x, pd.top, z, P.tile)
  }
  // the star in the hall floor: a gold heart, amethyst rays, a ring of blackstone
  const sx = 15, sz = 18
  for (let x = sx - 4; x <= sx + 4; x++) for (let z = sz - 4; z <= sz + 4; z++) {
    const r2 = (x - sx) ** 2 + (z - sz) ** 2
    if (r2 > 9 && r2 <= 16) c.set(x, 0, z, P.pier)
  }
  for (let k = 1; k <= 3; k++) for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) c.set(sx + dx * k, 0, sz + dz * k, k === 3 ? P.mark : P.star)
  c.set(sx, 0, sz, P.mark)
  // Pass 2, openings: the hall's two doors, its windows
  for (const d of OPENINGS.hallDoors) { door(c, d.at, d.facing, 'north', { wood: P.wood }); c.clear(d.at[0], 1, d.at[2] + 1); c.clear(d.at[0], 2, d.at[2] + 1) }
  for (const w of OPENINGS.hallWindows) for (const [x, y, z] of w.cells) {
    c.set(x, y, z, P.pane, { waterlogged: 'false' })
    const [ix, iz] = x === pd.x0 ? [x + 1, z] : x === pd.x1 ? [x - 1, z] : [x, z + 1]
    c.clear(ix, y, iz) // the inner layer's reveal
  }
  // Pass 3, depth: proud piers on the podium's faces, a cornice under the deck's edge, the parapet, the corner flights
  for (let z = pd.z0; z <= pd.z1; z += 3) for (const x of [pd.x0 - 1, pd.x1 + 1]) for (let y = 1; y < pd.top; y++) c.set(x, y, z, P.pier)
  for (let x = pd.x0 + 2; x <= pd.x1 - 2; x += 4) for (let y = 1; y < pd.top; y++) c.set(x, y, pd.z1 + 1, P.pier)
  const frontCornice = x => (x >= 9 && x <= 11) || (x >= 19 && x <= 21)
  for (let z = pd.z0 - 1; z <= pd.z1 + 1; z++) {
    stair(c, pd.x0 - 1, pd.top, z, P.trimStairs, 'east', 'top')
    stair(c, pd.x1 + 1, pd.top, z, P.trimStairs, 'west', 'top')
  }
  for (let x = pd.x0; x <= pd.x1; x++) {
    stair(c, x, pd.top, pd.z1 + 1, P.trimStairs, 'north', 'top')
    if (frontCornice(x)) stair(c, x, pd.top, pd.z0 - 1, P.trimStairs, 'south', 'top')
  }
  for (let x = pd.x0; x <= pd.x1; x++) for (let z = pd.z0; z <= pd.z1; z++) {
    const edge = x === pd.x0 || x === pd.x1 || z === pd.z0 || z === pd.z1
    const arrival = z === pd.z0 && M.flights.some(f => x >= f.x0 && x <= f.x1)
    if (edge && !arrival) c.set(x, pd.top + 1, z, P.plinth)
  }
  for (const f of M.flights) for (let z = 8 - pd.top; z <= 7; z++) {
    const step = z - (7 - pd.top)
    for (let x = f.x0; x <= f.x1; x++) {
      for (let y = 1; y < step; y++) c.set(x, y, z, P.mass)
      stair(c, x, step, z, P.stairs, 'south')
    }
    for (const x of [f.x0 - 1, f.x1 + 1]) {
      for (let y = 1; y <= step; y++) c.set(x, y, z, P.mass)
      c.set(x, step + 1, z, P.plinth)
    }
  }
  // Pass 4, interior: piers carrying the deck, the furniture, the lamps
  for (const x of [7, 11, 19, 23]) for (const z of [13, 18, 23]) for (let y = 1; y < pd.top; y++) c.set(x, y, z, y === pd.top - 1 ? 'chiseled_polished_blackstone' : P.pier)
  for (let x = hl.x0 + 1; x <= hl.x1 - 1; x++) {
    if (x >= 12 && x <= 18) continue
    for (let y = 1; y <= 2; y++) c.set(x, y, hl.z1, P.books)
  }
  for (const f of FURNITURE.filter(f => f.room === 'Chart Hall')) furnish(c, f)
  // Pass 5, ground: lamp posts in the court, lanterns on the parapet's corners
  for (const [x, y, z] of OUTSIDE.lampPosts) lampPost(c, x, y, z, P.post, 2)
  for (const [x, y, z] of OUTSIDE.parapetLamps) lampPost(c, x, y, z, P.post, 1) // a lamp on a short post, never on the stone itself

  // === Stage II: the Great Gnomon and its quadrants ================================================================
  c.begin(2)
  const ar = M.arch
  gnomonStair(c, gn, { fill: mass, stairs: P.stairs, cheek: P.mass, cheekCap: P.plinth })
  // a blind arch on both faces of the gnomon's foot: dark tiles framed by blackstone jambs and lintel
  for (const x of [gn.x0, gn.x1]) for (let z = ar.z0 - 1; z <= ar.z1 + 1; z++) for (let y = 1; y <= ar.y1 + 1; y++) {
    c.clear(x, y, z)
    c.set(x, y, z, z === ar.z0 - 1 || z === ar.z1 + 1 || y === ar.y1 + 1 ? P.plinth : P.tile)
  }
  // the top landing: a full block row at z 17 (the door's sill) with cheeks
  for (let x = gn.x0; x <= gn.x1; x++) {
    for (let y = pd.top; y < gn.landing; y++) c.add(x, y, 17, mass)
    if (x === gn.x0 || x === gn.x1) { c.add(x, gn.landing, 17, P.mass); c.add(x, gn.landing + 1, 17, P.plinth) }
    else c.add(x, gn.landing, 17, P.floor)
  }
  // hour marks on the cheek caps: every fourth step a gold mark
  for (let z = gn.z0 + 3; z <= gn.z1; z += 4) for (const x of [gn.x0, gn.x1]) {
    const y = gn.y0 + (z - gn.z0) + 1
    c.clear(x, y, z); c.set(x, y, z, P.mark)
  }
  const q = M.quadrants
  const qm = { fill: mass, scale: P.scale, curb: P.curb, tick: P.tick, mark: P.mark }
  const rimW = quadrantBowl(c, { face: gn.x0, side: -1, z0: q.z0, z1: q.z1, base: q.base, R: q.R }, qm)
  const rimE = quadrantBowl(c, { face: gn.x1, side: 1, z0: q.z0, z1: q.z1, base: q.base, R: q.R }, qm)
  for (const [x, top] of [rimW, rimE]) for (const z of [q.z0, q.z1]) {
    c.clear(x, top + 1, z) // the curb slab at the rim becomes a lamp on a full stone
    c.set(x, top + 1, z, P.plinth); lampPost(c, x, top + 2, z, P.post, 1)
  }

  // === Stage III: the Upper Terrace and the Drum ===================================================================
  c.begin(3)
  const t = M.tier
  for (let x = t.x0; x <= t.x1; x++) for (let z = t.z0; z <= t.z1; z++) for (let y = t.y0; y <= t.y1; y++) {
    const face = x === t.x0 || x === t.x1 || z === t.z0 || z === t.z1
    const strip = face && y > t.y0 && y < t.y1 && ((x === t.x0 || x === t.x1) ? z % 3 === 1 : x % 3 === 1) // flush blackstone strips: the tier's rhythm
    c.add(x, y, z, y === t.y0 && face ? P.plinth : y === t.y1 ? P.tile : strip ? P.pier : mass)
  }
  for (let x = t.x0 - 1; x <= t.x1 + 1; x++) for (let z = t.z0 - 1; z <= t.z1 + 1; z++) {
    if (x >= t.x0 && x <= t.x1 && z >= t.z0 && z <= t.z1) continue
    const facing = x < t.x0 ? 'east' : x > t.x1 ? 'west' : z < t.z0 ? 'south' : 'north'
    if ((x < t.x0 || x > t.x1) && (z < t.z0 || z > t.z1)) continue // corners stay square
    if (!c.earlier(x, t.y1, z) && !c.get(x, t.y1, z)) stair(c, x, t.y1, z, P.trimStairs, facing, 'top')
  }
  const { cx, cz } = dr
  const L = gn.landing
  const doorCells = (x, y, z) => x >= gn.x0 + 1 && x <= gn.x1 - 1 && z <= 17 && y > L && y <= L + 3 && z >= 16
  const winAt = (x, y, z) => y >= OPENINGS.drumWindows.y0 && y <= OPENINGS.drumWindows.y1 &&
    OPENINGS.drumWindows.angles.some(a => Math.abs(((angleOf(x, z, cx, cz) - a + 540) % 360) - 180) <= 9)
  // the drum: two blocks thick; the lower half's core is solid (the telescope's footing), the floor at y 17
  for (let y = dr.y0; y < dr.floor; y++) roundCourse(c, { cx, cz, outer2: dr.inner2, y }, mass)
  roundCourse(c, { cx, cz, outer2: dr.inner2, y: dr.floor }, P.floor)
  roundWall(c, { cx, cz, inner2: dr.inner2, outer2: dr.outer2, y0: dr.y0, y1: dr.y1 }, (x, y, z) => (winAt(x, y, z) ? P.glass : mass(x, y, z)),
    (x, y, z) => !doorCells(x, y, z))
  // the door through the wall: a recess over the landing, the door in the inner layer, glass beside it
  for (let x = gn.x0 + 1; x <= gn.x1 - 1; x++) for (let y = L + 1; y <= L + 3; y++) {
    if (x === 15 && y <= L + 2) continue
    c.add(x, y, 17, y === L + 3 ? P.plinth : P.glass)
  }
  for (let x = gn.x0 + 1; x <= gn.x1 - 1; x++) stair(c, x, L + 4, 16, P.trimStairs, 'south', 'top') // the recess's head
  door(c, OPENINGS.drumDoor.at, OPENINGS.drumDoor.facing, 'north', { wood: P.wood })
  // courses: the floor's band and the cornice, a block proud; pilasters between the windows
  roundCourse(c, { cx, cz, inner2: dr.outer2, outer2: 56, y: dr.floor + 1 }, P.plinth, (x, y, z) => !(z <= 17 && x >= gn.x0 && x <= gn.x1))
  roundCourse(c, { cx, cz, inner2: dr.outer2, outer2: 56, y: dr.y1 }, P.plinth)
  for (let k = 0; k < 8; k++) {
    const a = deg(22.5 + 45 * k)
    const x = Math.round(cx + 7 * Math.sin(a)), z = Math.round(cz + 7 * Math.cos(a))
    for (let y = dr.y0; y < dr.y1; y++) c.add(x, y, z, y === dr.y0 ? P.plinth : P.pier)
  }
  for (const f of FURNITURE.filter(f => f.room === 'Observing Room' && !f.stage)) furnish(c, f)

  // === Stage IV: the Copper Dome and the Great Telescope ===========================================================
  c.begin(4)
  const tel = M.telescope
  lineTube(c, tel, tt => (tt > tel.t1 - 1.2 ? P.lens : tt < tel.t0 + 1 ? P.pier : Math.abs(tt - 9) < 0.7 || Math.abs(tt) < 0.6 ? P.mark : P.copper))
  for (let y = tel.pier[0]; y <= tel.pier[1]; y++) if (!c.get(cx, y, cz)) c.set(cx, y, cz, P.pier)
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) stair(c, cx + dx, tel.pier[0], cz + dz, P.trimStairs, dx > 0 ? 'west' : dx < 0 ? 'east' : dz > 0 ? 'north' : 'south')
  const dm = M.dome
  skyDome(c, { cx, cz, y0: dm.y0, R: dm.R, t: dm.t, seat2: dr.inner2 }, (x, y, z) => (x > cx + 1 && Math.abs(z - cz) <= 1 ? P.lens : P.copper))
  c.set(cx, dm.y0 + Math.floor(dm.R) + 1, cz, P.mark)
  c.set(cx, dm.y0 + Math.floor(dm.R) + 2, cz, 'lightning_rod', { facing: 'up', powered: 'false', waterlogged: 'false' })
  for (const f of FURNITURE.filter(f => f.stage === 4)) furnish(c, f)
  return c
}

// --- The brief's checks, asserted on every stage ---------------------------------------------------------------------
const BANNED = ['calcite', 'bedrock', 'barrier', 'command_block', 'structure_block', 'structure_void', 'jigsaw', 'spawner',
  'trial_spawner', 'vault', 'end_portal_frame', 'reinforced_deepslate', 'budding_amethyst', 'petrified_oak_slab', 'light',
  'player_head', 'dragon_egg', 'fire', 'sand', 'gravel']
const NOT_SOLID = /^minecraft:(air|cave_air)$/ // the game's count (Blueprint.solidBlockCount): every block that isn't air

function assertBrief(c) {
  const stages = Math.max(...c.stageOf.values())
  if (stages !== 4) throw new Error(`four stages, drew ${stages}`)
  let solidAll = 0
  for (let n = 1; n <= 4; n++) {
    const s = c.stageCanvas(n)
    const cells = [...s.cells.keys()].map(k => k.split(',').map(Number))
    const has = new Set(s.cells.keys())
    const seen = new Set(cells.filter(([, y]) => y === 0).map(p => p.join(',')))
    const todo = [...seen].map(k => k.split(',').map(Number))
    while (todo.length) {
      const [x, y, z] = todo.pop()
      for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) for (let dz = -1; dz <= 1; dz++) {
        const m = Math.abs(dx) + Math.abs(dy) + Math.abs(dz)
        if (m < 1 || m > 2) continue
        const k = `${x + dx},${y + dy},${z + dz}`
        if (has.has(k) && !seen.has(k)) { seen.add(k); todo.push([x + dx, y + dy, z + dz]) }
      }
    }
    const floating = cells.filter(p => !seen.has(p.join(',')))
    if (floating.length) throw new Error(`stage ${n}: ${floating.length} blocks not grounded, e.g. ${floating.slice(0, 3).join(' ')}`)
    for (const [k, v] of s.cells) {
      const id = v.id.slice(10)
      if (BANNED.includes(id)) throw new Error(`stage ${n}: ${id} at ${k}`)
      const [x, y, z] = k.split(',').map(Number)
      if (id === 'lantern') {
        const hang = v.props.hanging === 'true'
        const by = hang ? s.get(x, y + 1, z) : s.get(x, y - 1, z)
        if (!by) throw new Error(`stage ${n}: a lantern at ${k} with nothing ${hang ? 'above' : 'under'} it`)
      }
    }
    if (n === 4) {
      const xs = cells.map(p => p[0]), zs = cells.map(p => p[2]), ys = cells.map(p => p[1])
      const w = Math.max(...xs) - Math.min(...xs) + 1, d = Math.max(...zs) - Math.min(...zs) + 1
      if (w < 30 || w > 32 || d < 30 || d > 32 || Math.min(...ys) !== 0) throw new Error(`plot ${w} x ${d}, lowest y ${Math.min(...ys)}`)
      solidAll = [...s.cells.values()].filter(v => !NOT_SOLID.test(v.id)).length
      if (solidAll < 8100 || solidAll > 9900) throw new Error(`block budget: ${solidAll} solid, wants 8,100-9,900`)
    }
  }
  return solidAll
}

export default function build(api) {
  const c = draw()
  const solid = assertBrief(c)
  const n = Number(process.env.STAGE ?? 4)
  const counts = [1, 2, 3, 4].map(k => c.stageCanvas(k).cells.size)
  console.error(`observatory: stage totals ${counts.join(' / ')} blocks drawn, ${solid} solid in all`)
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": []}\n') // a Wonder's parts record no furniture spots
  const s = c.stageCanvas(n)
  const cut = process.env.CUT
  const canvas = cut !== undefined ? s.cutAbove(Number(cut)) : s
  const name = `observatory${n < 4 ? `_stage${n}` : ''}${cut !== undefined ? `_cut${cut}` : ''}`
  return { structure: canvas.toStructure(api), metadata: { name } }
}
