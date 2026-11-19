// The Great Library (a Wonder), the recipe: assembles plan.mjs stage by stage on a StagedCanvas (a later stage may only
// add: it throws on any block an earlier stage drew), each stage in passes (block-out, openings, depth and detail,
// interior, ground and light), and returns the Wonder as it stands after STAGE (default 4) for the minecraft-builder
// skill's runner. verify() checks every stage against the brief and refuses a stage that changes an earlier one.
// CUT=<y> cuts everything above y (a floor's cutaway). stages.py runs it once per stage and saves the candidates.
import {
  SIZE, STAGES, PALETTE as P, ROTUNDA as R, WINGS, WING_Y as WY, BAYS, PODIUM, STEPS, PORTICO, FURNITURE, OUTSIDE,
} from './plan.mjs'
import {
  mix, stair, slab, furnitureParts, lampPost, gableRoof, gableWall,
  StagedCanvas, skyDome, enchantRing, helixWalk, helixBand, helixHeights, helixAngle, pediment, columnRow,
  stagesOnlyAdd,
} from '../parts/index.mjs'

const d2 = (x, z) => (x - R.cx) ** 2 + (z - R.cz) ** 2
const podiumStone = mix(P.podiumAlt, 17)
const groundStone = mix(P.ground, 23)
const leaves = { distance: '1', persistent: 'true', waterlogged: 'false' }
const doorAt = (c, [x, y, z], facing) => {
  for (const half of ['lower', 'upper']) c.set(x, y + (half === 'upper' ? 1 : 0), z, `${P.wood}_door`, { facing, half, hinge: 'left', open: 'false', powered: 'false' })
}
const lantern = (c, x, y, z, hanging = false) => c.set(x, y, z, 'lantern', { hanging: String(hanging), waterlogged: 'false' })
function furnish(c, f) {
  if (f.part === 'block') c.set(...f.pos, f.block, f.props ?? {})
  else furnitureParts[f.part](c, f)
}

// --- a wing's faces: each rim cell, the face it is on and its offset along the face ---------------------------------
const INNER = { front: 'z1', back: 'z0', west: 'x1', east: 'x0' }
function faces(w) {
  const out = []
  for (let x = w.x0; x <= w.x1; x++) { out.push({ face: 'z0', x, z: w.z0, off: x - w.x0, len: w.x1 - w.x0 + 1, out: [0, -1] }); out.push({ face: 'z1', x, z: w.z1, off: x - w.x0, len: w.x1 - w.x0 + 1, out: [0, 1] }) }
  for (let z = w.z0 + 1; z < w.z1; z++) { out.push({ face: 'x0', x: w.x0, z, off: z - w.z0, len: w.z1 - w.z0 + 1, out: [-1, 0] }); out.push({ face: 'x1', x: w.x1, z, off: z - w.z0, len: w.z1 - w.z0 + 1, out: [1, 0] }) }
  // the corners: on both faces; count them on the face along x, posts either way
  return out
}
const bay = f => BAYS[String(f.len)] ?? { posts: [0, f.len - 1], windows: [] }
const isPost = f => f.off === 0 || f.off === f.len - 1 || bay(f).posts.includes(f.off)
const isWindow = f => bay(f).windows.includes(f.off)
const doorCol = (w, x, z) => w.door.at[0] === x && w.door.at[2] === z
const innerDoor = (name, x, z) => (name === 'front' && x === 20 && z === 12) || (name === 'back' && x === 20 && z === 32)

// the wedge between a wing's flat inner end and the drum's round wall, filled so the two meet with no slot
function wedge(name, w) {
  const cells = []
  const inner = INNER[name]
  if (inner === 'z1' || inner === 'z0') {
    const dz = inner === 'z1' ? 1 : -1
    for (let x = w.x0; x <= w.x1; x++) for (let z = w[inner] + dz; d2(x, z) > R.shell.outer2 && Math.abs(z - R.cz) > 0; z += dz) cells.push([x, z])
  } else {
    const dx = inner === 'x1' ? 1 : -1
    for (let z = w.z0; z <= w.z1; z++) for (let x = w[inner] + dx; d2(x, z) > R.shell.outer2 && Math.abs(x - R.cx) > 0; x += dx) cells.push([x, z])
  }
  return cells
}

// the flight's cells and what must stay clear round it (the stairs, the deck's hole over the top steps, the landing)
function flightOf(w) {
  const cells = w.flight.cells.map(([x, z], i) => [x, 3 + i, z])
  const [lx, lz] = w.flight.cells[w.flight.cells.length - 1], [px, pz] = w.flight.cells[w.flight.cells.length - 2]
  const exit = [2 * lx - px, 2 * lz - pz]
  const [ax, az] = w.flight.cells[0], [bx, bz] = w.flight.cells[1]
  const entry = [2 * ax - bx, 2 * az - bz]
  return { cells, holes: cells.filter(([, y]) => y === 7 || y === 8).map(([x, , z]) => [x, z]), exit, entry }
}

// --- hidden by a wing (no spiral window behind a roof) ----------------------------------------------------------------
const nearWing = (x, z, pad = 1) => Object.values(WINGS).some(w => x >= w.x0 - pad && x <= w.x1 + pad && z >= w.z0 - pad && z <= w.z1 + pad)
const inWing = (x, z) => Object.values(WINGS).some(w => x > w.x0 && x < w.x1 && z > w.z0 && z < w.z1)
const clerestory = (x, y, z) => {
  if (y < R.clerestory[0] || y > R.clerestory[1]) return false
  const a = helixAngle(x, z, { cx: R.cx, cz: R.cz }) % 30
  return a >= 6 && a < 24
}
const inDoorway = (x, y, z) => R.doors.some(d => x === d.at[0] && d.through.includes(z) && y >= 3 && y <= 4)

export function draw() {
  const c = new StagedCanvas(SIZE)
  const rawBand = helixBand(R.helix, R.band[0], R.band[1])
  const band = (x, y, z) => rawBand(x, y, z) && (rawBand(x, y - 1, z) || rawBand(x, y + 1, z)) // never a one-high slot

  // === Stage I: The Stacks: the podium, the four wings' ground storeys round an open court ==========================
  c.begin(1)
  // Pass 1, block-out: the ground, the podium, its steps
  for (let x = 0; x < SIZE[0]; x++) for (let z = 0; z < SIZE[2]; z++) c.set(x, 0, z, groundStone)
  for (let x = PODIUM.x0; x <= PODIUM.x1; x++) for (let z = PODIUM.z0; z <= PODIUM.z1; z++) {
    const rim = x === PODIUM.x0 || x === PODIUM.x1 || z === PODIUM.z0 || z === PODIUM.z1
    c.set(x, 1, z, podiumStone)
    const r = d2(x, z)
    const court = r <= R.shell.outer2
    c.set(x, 2, z, rim ? P.course : court ? (Math.floor(Math.sqrt(r) / 2) % 2 ? P.paving : P.wall) : P.paving)
  }
  // a skirt of steps round the podium's foot, the front steps across the portico's width
  for (let z = PODIUM.z0; z <= PODIUM.z1; z++) { stair(c, PODIUM.x0 - 1, 1, z, P.podiumStairs, 'east'); stair(c, PODIUM.x1 + 1, 1, z, P.podiumStairs, 'west') }
  for (let x = PODIUM.x0; x <= PODIUM.x1; x++) stair(c, x, 1, PODIUM.z1 + 1, P.podiumStairs, 'north')
  for (let x = PODIUM.x0; x <= PODIUM.x1; x++) {
    if (x >= STEPS.x0 && x <= STEPS.x1) { c.set(x, 1, 2, podiumStone); stair(c, x, 2, 2, P.podiumStairs, 'south'); stair(c, x, 1, 1, P.podiumStairs, 'south') }
    else stair(c, x, 1, 2, P.podiumStairs, 'south')
  }
  // the wings: floors, walls y 3-8 with their bays, the deck at y 9, the parapet at y 10
  for (const [name, w] of Object.entries(WINGS)) {
    const fl = flightOf(w)
    for (let x = w.x0 + 1; x < w.x1; x++) for (let z = w.z0 + 1; z < w.z1; z++) c.set(x, WY.floor, z, P.floor)
    for (const f of faces(w)) {
      if (c.get(f.x, 3, f.z)) continue // a corner already drawn by its other face
      const inner = f.face === INNER[name]
      for (let y = WY.wall0; y <= WY.parapet; y++) {
        if ((doorCol(w, f.x, f.z) || innerDoor(name, f.x, f.z)) && y <= 4) continue
        let id = y === WY.wall0 || y === WY.deck ? P.course : P.wall
        if (!inner && isWindow(f) && !isPost(f) && y >= 4 && y <= 6) id = P.pane
        if (doorCol(w, f.x, f.z) && (y === 5 || y === 6)) id = P.pane // a transom over the door
        if (!inner && isWindow(f) && y === 7) id = P.course // a lintel over each window
        c.set(f.x, y, f.z, id, id === P.pane ? { waterlogged: 'false' } : {})
      }
    }
    for (const [x, z] of wedge(name, w)) for (let y = WY.wall0; y <= WY.parapet; y++) c.set(x, y, z, y === WY.wall0 || y === WY.deck ? P.course : P.wall)
    // the deck: dark oak over the stacks, open over the flight's top steps
    for (let x = w.x0 + 1; x < w.x1; x++) for (let z = w.z0 + 1; z < w.z1; z++) {
      if (fl.holes.some(([hx, hz]) => hx === x && hz === z)) continue
      if (fl.cells.some(([fx, fy, fz]) => fx === x && fz === z && fy === WY.deck)) continue
      c.set(x, WY.deck, z, P.floor)
    }
  }
  // Pass 2, openings: the doors (out to the terrace, and the court's two doors into the future rotunda)
  for (const w of Object.values(WINGS)) doorAt(c, w.door.at, w.door.facing)
  for (const d of R.doors) doorAt(c, d.at, d.facing)
  // Pass 3, depth: proud pilasters at every post of every outer face, y 3-10 (a stone pier that the gallery continues)
  for (const [name, w] of Object.entries(WINGS)) for (const f of faces(w)) {
    if (f.face === INNER[name] || !isPost(f)) continue
    const [ox, oz] = f.out
    for (let y = WY.wall0; y <= WY.parapet; y++) c.add(f.x + ox, y, f.z + oz, P.course)
  }
  // round 2: every ground-floor window gets a projecting sill and hood, and a trim band runs round every outer face at
  // the upper-floor line (y 9), between the pilasters
  for (const [name, w] of Object.entries(WINGS)) for (const f of faces(w)) {
    if (f.face === INNER[name]) continue
    const [ox, oz] = f.out, x = f.x + ox, z = f.z + oz
    const toward = ox ? (ox < 0 ? 'east' : 'west') : (oz < 0 ? 'south' : 'north')
    if (!c.get(x, WY.deck, z)) stair(c, x, WY.deck, z, P.corniceStairs, toward, 'top')
    if (isWindow(f) && !isPost(f) && !doorCol(w, f.x, f.z)) {
      if (!c.get(x, 3, z)) slab(c, x, 3, z, P.sillSlab, 'top')
      if (!c.get(x, 7, z)) stair(c, x, 7, z, P.corniceStairs, toward, 'top')
    }
  }
  // Pass 4, interior: each wing's flight, its stacks, desks and lecterns, lanterns
  for (const w of Object.values(WINGS)) for (const [x, y, z] of flightOf(w).cells) stair(c, x, y, z, `${P.wood}_stairs`, w.flight.facing)
  // the deck's stair hole: hatches folded back against the wall (open trapdoors), so the terrace is never an open pit
  for (const w of Object.values(WINGS)) for (const [x, z] of flightOf(w).holes) c.set(x, WY.deck, z, `${P.wood}_trapdoor`, { facing: w.flight.hatch, half: 'top', open: 'true', powered: 'false', waterlogged: 'false' })
  const shelves = (x0, x1, z0, z1, y1 = 6) => { for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) for (let y = 3; y <= y1; y++) c.set(x, y, z, P.books) }
  shelves(3, 5, 19, 20); shelves(3, 5, 23, 24); shelves(35, 37, 19, 20); shelves(35, 37, 23, 24) // the side stacks, in bays
  shelves(15, 18, 11, 11); shelves(22, 25, 11, 11) // the map room's back wall
  shelves(15, 18, 33, 33); shelves(22, 25, 33, 33) // the back stacks against the court
  for (const f of FURNITURE.filter(f => !f.stage)) furnish(c, f)
  // books along every wall between the windows, up to the lintels, where nothing else stands
  for (const [name, w] of Object.entries(WINGS)) {
    const fl = flightOf(w)
    const keepOpen = new Set([...fl.cells.map(([x, , z]) => `${x},${z}`), `${fl.entry[0]},${fl.entry[1]}`])
    const [dx, , dz] = w.door.at
    keepOpen.add(dx === w.x0 ? `${dx + 1},${dz}` : dx === w.x1 ? `${dx - 1},${dz}` : dz === w.z0 ? `${dx},${dz + 1}` : `${dx},${dz - 1}`)
    if (name === 'front') keepOpen.add('20,11')
    if (name === 'back') keepOpen.add('20,33')
    for (let x = w.x0 + 1; x < w.x1; x++) for (let z = w.z0 + 1; z < w.z1; z++) {
      const edge = x === w.x0 + 1 || x === w.x1 - 1 || z === w.z0 + 1 || z === w.z1 - 1
      if (!edge || keepOpen.has(`${x},${z}`) || c.get(x, 3, z) || c.get(x, 4, z)) continue
      const behind = [[x - 1, z], [x + 1, z], [x, z - 1], [x, z + 1]].filter(([bx, bz]) => bx === w.x0 || bx === w.x1 || bz === w.z0 || bz === w.z1)
      if (behind.some(([bx, bz]) => c.name(bx, 5, bz).includes('pane'))) continue
      for (let y = 3; y <= 6; y++) c.set(x, y, z, P.books)
    }
  }
  for (const [x, z] of [[4, 19], [4, 24], [36, 19], [36, 24]]) lantern(c, x, 7, z) // lamps on the stacks
  for (const [x, z] of [[18, 9], [23, 9], [6, 21], [6, 25], [34, 21], [34, 25], [18, 35], [23, 35]]) lantern(c, x, 8, z, true)
  // Pass 5, ground: raised garden beds (a smooth-stone kerb round their own soil), flowers and a small tree in each,
  // a balustrade round the terrace's edge, lamp posts at the corners and steps
  const FLOWERS = ['allium', 'azure_bluet', 'oxeye_daisy', 'cornflower', 'lily_of_the_valley']
  for (const [x0, z0, x1, z1] of OUTSIDE.beds) {
    const mx = (x0 + x1) >> 1, mz = (z0 + z1) >> 1
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
      const rim = x === x0 || x === x1 || z === z0 || z === z1
      c.set(x, 3, z, rim ? P.course : 'grass_block', rim ? {} : { snowy: 'false' })
      if (!rim && (x + z) % 2 === 0 && Math.max(Math.abs(x - mx), Math.abs(z - mz)) > 1) c.set(x, 4, z, FLOWERS[(x * 3 + z * 7) % FLOWERS.length])
    }
    for (let y = 4; y <= 6; y++) c.set(mx, y, mz, 'dark_oak_log', { axis: 'y' })
    for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) for (let y = 6; y <= 8; y++) if ((dx || dz || y > 6) && !(y === 8 && dx && dz)) c.add(mx + dx, y, mz + dz, P.hedge, leaves)
  }
  const lampAt = new Set(OUTSIDE.lampPosts.map(([x, , z]) => `${x},${z}`))
  for (let x = PODIUM.x0; x <= PODIUM.x1; x++) for (let z = PODIUM.z0; z <= PODIUM.z1; z++) {
    const rim = x === PODIUM.x0 || x === PODIUM.x1 || z === PODIUM.z0 || z === PODIUM.z1
    if (!rim || c.get(x, 3, z) || lampAt.has(`${x},${z}`)) continue
    if (z === PODIUM.z0 && x >= PORTICO.x0 && x <= PORTICO.x1) continue // the portico's front
    if (z === PODIUM.z1 && x === WINGS.back.door.at[0]) continue // the way out of the back door
    c.set(x, 3, z, P.balustrade)
  }
  for (const [x, y, z] of OUTSIDE.lampPosts) lampPost(c, x, y, z, P.post, 2)

  // === Stage II: The Galleries: each wing's upper storey and its copper roof =======================================
  c.begin(2)
  for (const [name, w] of Object.entries(WINGS)) {
    const fl = flightOf(w)
    // Pass 1, walls y 11-14 with the same bays; the frieze at y 15 under the eaves
    for (const f of faces(w)) {
      if (c.get(f.x, WY.gallery0, f.z)) continue
      const inner = f.face === INNER[name]
      for (let y = WY.gallery0; y <= WY.gallery1; y++) {
        let id = P.wall
        if (!inner && isWindow(f) && !isPost(f)) id = P.pane // a low window under the eaves frieze
        c.set(f.x, y, f.z, id, id === P.pane ? { waterlogged: 'false' } : {})
      }
    }
    for (const [x, z] of wedge(name, w)) for (let y = WY.gallery0; y <= WY.gallery1 + 1; y++) c.set(x, y, z, P.wall)
    for (const f of faces(w)) {
      if (f.face === INNER[name] || !isPost(f)) continue
      const [ox, oz] = f.out
      for (let y = WY.gallery0; y <= WY.gallery1; y++) c.add(f.x + ox, y, f.z + oz, P.course)
    }
    // Pass 2, the roof: copper, a 45-degree gable, eaves a block out, the outer gable a pediment with a rose window
    const axisZ = w.axis === 'z'
    const across = axisZ ? [w.x0 - 1, w.x1 + 1] : [w.z0 - 1, w.z1 + 1]
    const outerEnd = w.outer, along = axisZ
      ? (outerEnd === 'z0' ? [w.z0 - 1, w.z1] : [w.z0, w.z1 + 1])
      : (outerEnd === 'x0' ? [w.x0 - 1, w.x1] : [w.x0, w.x1 + 1])
    const { under } = gableRoof(c, { axis: w.axis, across, along, eaveY: WY.eave }, P.roof)
    const lo = axisZ ? w.x0 : w.z0, hi = axisZ ? w.x1 : w.z1
    // the long sides' frieze under the eave course
    for (let v = along[0]; v <= along[1]; v++) {
      const onWall = axisZ ? (v >= w.z0 && v <= w.z1) : (v >= w.x0 && v <= w.x1)
      if (!onWall) continue
      for (const u of [lo, hi]) { const [x, z] = axisZ ? [u, v] : [v, u]; c.add(x, WY.eave, z, P.course) }
    }
    const mid = (lo + hi) / 2
    for (const end of [w[outerEnd], w[INNER[name]]]) {
      const outer = end === w[outerEnd]
      gableWall(c, axisZ ? 'z' : 'x', end, [lo, hi], WY.eave, under, (u, y) => {
        if (outer && ((u === mid && y >= WY.eave + 2 && y <= WY.eave + 4) || (Math.abs(u - mid) === 1 && y === WY.eave + 3))) return [P.pane, { waterlogged: 'false' }]
        if (outer && y === WY.eave) return P.course
        return outer ? P.course : P.wall
      })
    }
    // round 2, depth on the outer gable: a projecting cornice across its base (the pilasters run into it) and a
    // raking edge of quartz under the roof's overhang along both slopes: upside-down stairs like the cornice's (ship
    // step, 52.6: a top-half slab there sat level with the roof's lowest step beside it, which reads as a chair at a
    // table, QaChairFacingGameTests)
    {
      const oLine = w[outerEnd], step = outerEnd.endsWith('0') ? -1 : 1, line = oLine + step
      const face = axisZ ? (step < 0 ? 'south' : 'north') : (step < 0 ? 'east' : 'west')
      for (let u = lo - 1; u <= hi + 1; u++) {
        const [x, z] = axisZ ? [u, line] : [line, u]
        if (!c.get(x, WY.eave, z)) stair(c, x, WY.eave, z, P.corniceStairs, face, 'top')
        const top = (under[u] ?? WY.eave + 1) - 1
        if (u >= lo && u <= hi && top > WY.eave && !c.get(x, top, z)) stair(c, x, top, z, P.corniceStairs, face, 'top')
      }
    }
    // Pass 4, interior: walls of books between the windows, a lamp from the ridge, a reading table
    const clear = new Set([...fl.cells.map(([x, , z]) => `${x},${z}`), `${fl.exit[0]},${fl.exit[1]}`])
    for (let x = w.x0 + 1; x < w.x1; x++) for (let z = w.z0 + 1; z < w.z1; z++) {
      const edge = x === w.x0 + 1 || x === w.x1 - 1 || z === w.z0 + 1 || z === w.z1 - 1
      if (!edge || clear.has(`${x},${z}`)) continue
      // the wall cell behind: no books in front of a window
      const behind = [[x - 1, z], [x + 1, z], [x, z - 1], [x, z + 1]].filter(([bx, bz]) => bx === w.x0 || bx === w.x1 || bz === w.z0 || bz === w.z1)
      if (behind.some(([bx, bz]) => c.name(bx, 12, bz).includes('pane'))) continue
      for (let y = WY.gallery0 - 1; y <= WY.gallery0 + 2; y++) c.add(x, y, z, P.books)
    }
    const [cxw, czw] = axisZ ? [mid, (w.z0 + w.z1) >> 1] : [(w.x0 + w.x1) >> 1, mid]
    const ridge = WY.eave + (across[1] - across[0]) / 2
    furnitureParts.hangingLamp(c, { pos: [cxw, WY.eave + 2, czw], chain: ridge - WY.eave - 3, facing: 'north', wood: P.wood })
  }
  // reading tables in the galleries (side wings: under the lamp; front and back: by the windows)
  for (const [tx, tz, cx2, cz2, f] of [[5, 22, 6, 22, 'west'], [35, 22, 34, 22, 'east'], [24, 9, 24, 10, 'north'], [24, 35, 24, 34, 'south']]) {
    furnitureParts.table(c, { pos: [tx, 10, tz], facing: f, wood: P.wood, colour: 'green' })
    furnitureParts.chair(c, { pos: [cx2, 10, cz2], facing: f, wood: P.wood })
  }
  // round 2, the galleries furnished: shelf rows across each room (lamps standing on their tops), more desks
  const row = (x0, x1, z0, z1) => { for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) for (let y = 10; y <= 12; y++) c.add(x, y, z, P.books) }
  row(4, 6, 19, 19); row(4, 6, 25, 25); row(34, 36, 19, 19); row(34, 36, 25, 25) // side galleries, across the wing
  row(17, 17, 9, 10); row(22, 22, 9, 10); row(17, 17, 34, 35); row(22, 22, 34, 35) // front and back galleries
  for (const [x, z] of [[5, 19], [5, 25], [35, 19], [35, 25], [17, 9], [22, 10], [17, 35], [22, 34]]) lantern(c, x, 13, z)
  for (const [tx, tz, cx2, cz2, f] of [[5, 23, 6, 23, 'west'], [35, 23, 34, 23, 'east'], [19, 10, 19, 9, 'south'], [19, 34, 19, 35, 'north'], [5, 21, 6, 21, 'west'], [35, 21, 34, 21, 'east']]) {
    furnitureParts.table(c, { pos: [tx, 10, tz], facing: f, wood: P.wood, colour: 'green' })
    furnitureParts.chair(c, { pos: [cx2, 10, cz2], facing: f, wood: P.wood })
  }

  // === Stage III: The Rotunda: the drum, its wall of books, the helix walk and its spiral of windows, the table ======
  c.begin(3)
  const { shell, lining } = R
  const Rr = Math.ceil(Math.sqrt(R.cornice.outer2))
  for (let x = R.cx - Rr; x <= R.cx + Rr; x++) for (let z = R.cz - Rr; z <= R.cz + Rr; z++) {
    const r = d2(x, z)
    // Pass 1 and 2: the shell with its doorways and the spiral of windows (glass in the outer course, a reveal inside)
    if (r > shell.inner2 && r <= shell.outer2) for (let y = shell.y0; y <= shell.y1; y++) {
      if (inDoorway(x, y, z) || (inWing(x, z) && y <= WY.eave + 7)) continue // never into a wing's rooms (under its ridge)
      const hidden = [[0, 0], [1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dz]) => (c.stageOf.get(c.key(x + dx, y, z + dz)) ?? 9) < 3)
      // round 2: a full ring of clerestory windows above the wing ridges, glass through the wall's whole depth
      if (!hidden && clerestory(x, y, z)) { c.add(x, y, z, P.ribbon); continue }
      if (!hidden && band(x, y, z) && y < shell.y1 - 1) { if (r > 100) c.add(x, y, z, P.ribbon); continue }
      c.add(x, y, z, y === shell.y0 || y === WY.eave || y === shell.y1 || y === R.clerestory[0] - 1 ? P.course : P.wall)
    }
    if (r > lining.inner2 && r <= lining.outer2) for (let y = lining.y0; y <= lining.y1; y++) {
      if (inDoorway(x, y, z)) continue
      if (band(x, y, z) && y < shell.y1 - 1) continue // the spiral's reveal (a niche where a wing stands outside)
      if (clerestory(x, y, z)) continue // the clerestory's reveal
      c.add(x, y, z, P.books)
    }
    // Pass 3, depth: the cornice ring a block out at the drum's top; pilasters on the four open quarters
    if (r > R.cornice.inner2 && r <= R.cornice.outer2) {
      c.add(x, R.cornice.y, z, P.course)
      if (!nearWing(x, z, 0)) c.add(x, WY.eave, z, P.course) // the string course a block proud, level with the wings' eaves
      const a = helixAngle(x, z, { cx: R.cx, cz: R.cz })
      if (!nearWing(x, z, 2) && Math.abs(((a + 15) % 30) - 15) < 4) for (let y = shell.y0; y < R.cornice.y; y++) c.add(x, y, z, P.course)
    }
  }
  // a reveal only behind glass: an inner course left open where the course outside it stayed solid (a wing's wall
  // or roof hid the window) is filled, outermost first, so no slot is shut in between blocks
  const reveals = []
  for (let x = R.cx - 11; x <= R.cx + 11; x++) for (let z = R.cz - 11; z <= R.cz + 11; z++) {
    const r = d2(x, z)
    if (r > lining.inner2 && r <= 100) for (let y = shell.y0; y < shell.y1 - 1; y++) if (!c.get(x, y, z) && band(x, y, z) && !inDoorway(x, y, z)) reveals.push([x, y, z, r])
  }
  reveals.sort((a, b) => b[3] - a[3])
  for (const [x, y, z, r] of reveals) {
    const out = [[1, 0], [-1, 0], [0, 1], [0, -1]].map(([dx, dz]) => [x + dx, z + dz]).filter(([ox, oz]) => d2(ox, oz) > r)
    const lit = out.some(([ox, oz]) => c.name(ox, y, oz) === `minecraft:${P.ribbon}` || (!c.get(ox, y, oz) && d2(ox, oz) <= shell.outer2))
    const has = (a, b, e) => !!c.get(a, b, e)
    const wedged = has(x, y - 1, z) && has(x, y + 1, z) && ((has(x - 1, y, z) && has(x + 1, y, z)) || (has(x, y, z - 1) && has(x, y, z + 1)))
    if (!lit || wedged) c.add(x, y, z, r > shell.inner2 ? P.wall : P.books)
  }
  // the inner ceiling at the drum's top: coffers of stone round a glass oculus (the dome's lantern lights it in stage IV)
  for (let x = R.cx - 9; x <= R.cx + 9; x++) for (let z = R.cz - 9; z <= R.cz + 9; z++) {
    const r = d2(x, z)
    if (r > shell.inner2) continue
    c.add(x, R.ceiling.y, z, r <= R.ceiling.oculus2 ? P.ribbon : (x + z) % 2 ? P.course : P.wall)
  }
  // chandeliers on chains from the ceiling
  for (const [dx, dz] of [[4, 0], [-4, 0], [0, 4], [0, -4]]) furnitureParts.hangingLamp(c, { pos: [R.cx + dx, 15, R.cz + dz], chain: R.ceiling.y - 16, facing: 'north', wood: P.wood })
  // Pass 4, interior: the helix walk, the enchanting table in its ring, the centre lectern, lamps on the ring
  helixWalk(c, R.helix, R.walk, P.walk)
  const ringShelves = enchantRing(c, R.table, P.books, 'enchanting_table', [[-1, -2], [0, -2], [1, -2]])
  if (ringShelves < 15) throw new Error(`the ring holds ${ringShelves} shelves`)
  for (const f of FURNITURE.filter(f => f.stage === 3)) furnish(c, f)
  for (const [dx, dz] of [[-2, -2], [2, -2], [-2, 2], [2, 2]]) lantern(c, R.cx + dx, 5, R.cz + dz)
  // lamps on the walk: standing on its slabs at the inner edge, one every quarter turn
  for (const phi of [45, 135, 225, 315, 405, 495, 585, 675]) {
    const a = ((R.helix.start + phi) * Math.PI) / 180
    const x = Math.round(R.cx + Math.sin(a) * 5.4), z = Math.round(R.cz + Math.cos(a) * 5.4)
    const s = helixHeights(x, z, R.helix).find(h => Math.abs(h - (R.helix.y0 + (R.helix.rise * phi) / 360)) < 1)
    if (s === undefined) continue
    const y = Number.isInteger(s) ? s : s - 0.5
    if (!c.get(x, y, z) && c.get(x, y - 1, z)?.id.endsWith('slab')) lantern(c, x, y, z)
  }

  // === Stage IV: The Dome and the Portico =========================================================================
  c.begin(4)
  // Pass 1, the dome: copper on the drum, ribbed, a glass lantern over the oculus; a parapet round its foot
  const dm = R.dome
  for (let x = R.cx - Rr; x <= R.cx + Rr; x++) for (let z = R.cz - Rr; z <= R.cz + Rr; z++) {
    const r = d2(x, z)
    if (r > R.cornice.inner2 && r <= R.cornice.outer2) slab(c, x, R.cornice.y + 1, z, P.courseSlab)
  }
  skyDome(c, { cx: R.cx, cz: R.cz, y0: dm.y0, R: dm.R, t: dm.t, squash: dm.squash }, (x, y, z) => {
    if (x === R.cx && z === R.cz && y >= 29) return null // the oculus
    const a = helixAngle(x, z, { cx: R.cx, cz: R.cz })
    const rib = Math.abs(((a + 180 / dm.ribs) % (360 / dm.ribs)) - 180 / dm.ribs) < 4
    return rib ? P.rib : P.roof.full
  })
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
    if (!dx && !dz) continue
    for (let y = R.lantern.y0; y < R.lantern.top; y++) c.set(R.cx + dx, y, R.cz + dz, dx && dz ? P.rib : P.pane, dx && dz ? {} : { waterlogged: 'false' })
  }
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) c.set(R.cx + dx, R.lantern.top, R.cz + dz, dx || dz ? P.roof.full : P.rib)
  // Pass 2, the portico: six quartz columns, the entablature, a coffered soffit, the copper gable and its pediment
  const pt = PORTICO
  columnRow(c, pt.columns.map(x => [x, pt.z0]), pt.y0, pt.capital, { base: P.colBase, shaft: P.column, capital: P.capital })
  // round 2: each column's base and capital spread a block on three sides (never forward: the porch stays shallow)
  for (const x of pt.columns) for (const [dx, dz, f] of [[-1, 0, 'east'], [1, 0, 'west'], [0, 1, 'north']]) {
    if (!c.get(x + dx, pt.y0, pt.z0 + dz)) stair(c, x + dx, pt.y0, pt.z0 + dz, P.baseStairs, f)
    if (!c.get(x + dx, pt.capital, pt.z0 + dz)) stair(c, x + dx, pt.capital, pt.z0 + dz, P.baseStairs, f, 'top')
  }
  for (let x = pt.x0; x <= pt.x1; x++) for (let z = pt.z0; z <= pt.z1; z++) {
    const beam = z === pt.z0 || x === pt.x0 || x === pt.x1
    c.add(x, pt.beam, z, beam ? P.beam : (x + z) % 2 ? P.course : P.wall)
  }
  for (const x of [pt.x0, pt.x1]) for (let z = pt.z0 + 1; z <= pt.z1 + 1; z++) c.add(x, pt.beam, z, P.beam)
  // the pronaos wall: the front wall thickened to the portico's back line, the door and windows in deep reveals
  for (let x = pt.x0 + 1; x < pt.x1; x++) for (let y = pt.y0; y < pt.beam; y++) {
    const door = x === WINGS.front.door.at[0] && y <= 6
    const win = (x === 16 || x === 24) && y >= 4 && y <= 6
    if (!door && !win) c.add(x, y, pt.z1, y === pt.y0 ? P.course : P.wall)
  }
  const portRoof = gableRoof(c, { axis: 'z', across: [pt.x0 - 1, pt.x1 + 1], along: [pt.z0, pt.z1 + 1], eaveY: pt.eave }, P.roof, (x, y, z) => !c.get(x, y, z))
  // the attic behind the pediment, solid to the roof (no void under the slopes)
  for (let x = pt.x0; x <= pt.x1; x++) for (let z = pt.z0 + 1; z <= pt.z1 + 1; z++) for (let y = pt.eave; y < portRoof.under[x]; y++) c.add(x, y, z, P.wall)
  gableWall(c, 'z', pt.z0, [pt.x0, pt.x1], pt.eave, portRoof.under, (u, y) => {
    if (y === pt.eave) return P.beam
    if (Math.abs(u - 20) <= 1 && (y === 16 || y === 17)) return P.capital // an open book in the tympanum
    if (u === 20 && y === 18) return P.capital
    return P.tympanum
  })
  return c
}

// --- The brief's checks, asserted on every stage ---------------------------------------------------------------------
const BANNED = ['calcite', 'bedrock', 'barrier', 'command_block', 'structure_block', 'structure_void', 'jigsaw', 'spawner',
  'trial_spawner', 'vault', 'end_portal_frame', 'reinforced_deepslate', 'budding_amethyst', 'petrified_oak_slab', 'light',
  'player_head', 'dragon_egg', 'fire', 'sand', 'gravel']
const COUNTS = [
  ['bookshelf', 200, ['bookshelf']], ['copper', 100, ['copper_block', 'cut_copper', 'cut_copper_stairs']],
  ['quartz', 20, ['quartz_block', 'quartz_pillar', 'chiseled_quartz_block', 'smooth_quartz']],
  ['diorite and smooth stone', 500, ['polished_diorite', 'smooth_stone']], ['dark oak planks', 200, ['dark_oak_planks']],
  ['glass', 1, ['glass', 'glass_pane']], ['lecterns', 5, ['lectern']], ['cartography table', 1, ['cartography_table']],
  ['enchanting table', 1, ['enchanting_table']],
]
const OFFSETS = [] // vanilla's BOOKSHELF_OFFSETS: |dx| or |dz| == 2, dy 0..1
for (let dx = -2; dx <= 2; dx++) for (let dz = -2; dz <= 2; dz++) if (Math.abs(dx) === 2 || Math.abs(dz) === 2) for (const dy of [0, 1]) OFFSETS.push([dx, dy, dz])

export function verify(c = draw()) {
  const snaps = [1, 2, 3, 4].map(n => c.stageCanvas(n))
  const changed = stagesOnlyAdd(snaps)
  if (changed) throw new Error(changed)
  const out = []
  for (let n = 1; n <= STAGES; n++) {
    const s = snaps[n - 1]
    const cells = [...s.cells.keys()].map(k => k.split(',').map(Number))
    const seen = new Set(cells.filter(([, y]) => y === 0).map(p => p.join(',')))
    const todo = [...seen].map(k => k.split(',').map(Number))
    while (todo.length) {
      const [x, y, z] = todo.pop()
      for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) for (let dz = -1; dz <= 1; dz++) {
        if (!dx && !dy && !dz) continue
        const k = `${x + dx},${y + dy},${z + dz}`
        if (s.cells.has(k) && !seen.has(k)) { seen.add(k); todo.push([x + dx, y + dy, z + dz]) }
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
        if (!(hang ? s.get(x, y + 1, z) : s.get(x, y - 1, z))) throw new Error(`stage ${n}: a lantern at ${k} with nothing ${hang ? 'above' : 'under'} it`)
      }
      if (id === 'lectern' && v.props.has_book !== 'false') throw new Error(`stage ${n}: a lectern with a book at ${k}`)
      if (id.endsWith('_door') && v.props.half === 'lower') {
        const up = s.get(x, y + 1, z)
        if (!up || up.id !== v.id || up.props.facing !== v.props.facing) throw new Error(`stage ${n}: a door at ${k} without its upper half`)
        const [fx, fz] = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] }[v.props.facing]
        for (const sgn of [1, -1]) for (const dy of [0, 1]) if (s.get(x + sgn * fx, y + dy, z + sgn * fz)) throw new Error(`stage ${n}: the door at ${k} is blocked at ${x + sgn * fx},${y + dy},${z + sgn * fz}`)
      }
    }
    out.push(cells.length)
  }
  const s = snaps[3]
  const cells = [...s.cells.keys()].map(k => k.split(',').map(Number))
  const ext = i => Math.max(...cells.map(p => p[i])) - Math.min(...cells.map(p => p[i])) + 1
  if (ext(0) !== 41 || ext(2) !== 41 || ext(1) !== 34 || Math.min(...cells.map(p => p[1])) !== 0) throw new Error(`size ${ext(0)} x ${ext(2)} x ${ext(1)}`)
  const total = s.cells.size
  if (total < 14400 || total > 17600) throw new Error(`block budget: ${total}, wants 14,400-17,600`)
  const count = ids => [...s.cells.values()].filter(v => ids.includes(v.id.slice(10))).length
  const counts = {}
  for (const [what, min, ids] of COUNTS) { counts[what] = count(ids); if (counts[what] < min) throw new Error(`${what}: ${counts[what]}, wants ${min}`) }
  if (count(['enchanting_table']) !== 1) throw new Error('exactly one enchanting table')
  // the table, its shelves by vanilla's rule, the scholar's spot and his line to the table
  const [tx, ty, tz] = R.table
  if (s.name(tx, ty, tz) !== 'minecraft:enchanting_table' || !s.get(tx, ty - 1, tz)) throw new Error('the table is not at (20, 3, 22) on a floor')
  const power = OFFSETS.filter(([dx, dy, dz]) => s.name(tx + dx, ty + dy, tz + dz) === 'minecraft:bookshelf'
    && !s.get(tx + dx / 2, ty + dy, tz + dz / 2) && !s.get(tx + Math.trunc(dx / 2), ty + dy, tz + Math.trunc(dz / 2))).length
  if (power < 15) throw new Error(`the table counts ${power} shelves`)
  for (const [x, y, z] of [[21, 3, 18], [21, 4, 18], [20, 3, 18], [20, 4, 18], [20, 4, 19], [20, 4, 20], [20, 4, 21]]) if (s.get(x, y, z)) throw new Error(`the scholar's spot is blocked at ${x},${y},${z}`)
  if (!s.get(21, 2, 18) || !s.get(20, 2, 18)) throw new Error('no floor under the scholar')
  return { perStage: out, total, counts, power }
}

export default function build(api) {
  const c = draw()
  const v = verify(c)
  console.error(`great_library: stage totals ${v.perStage.join(' / ')}; ${v.total} blocks; table power ${v.power}; ${JSON.stringify(v.counts)}`)
  const n = Number(process.env.STAGE ?? STAGES)
  const s = c.stageCanvas(n)
  const cut = process.env.CUT
  const canvas = cut !== undefined ? s.cutAbove(Number(cut)) : s
  const name = `great_library_s${n}${cut !== undefined ? `_cut${cut}` : ''}`
  return { structure: canvas.toStructure(api), metadata: { name } }
}
