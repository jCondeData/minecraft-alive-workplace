// Ranch, the snowy look (ROADMAP 49.10e): assembles plan.mjs from the parts library (../parts) in passes (block-out and
// structure, the roof, openings, depth and detail, interior, ground, snow) for LEVEL = 1 or 2, II all of I in its cells
// plus the feed and bunk wing and the paddock. Writes furniture.json (the furniture spots). Build both: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, IDS, PALETTE, BARN, FOREBAY, STACK, DORMERS, EAVE_POSTS, WING, PADDOCK, OPENINGS, FURNITURE } from './plan.mjs'
import {
  Canvas, mix, stair, slab, post, floor, wallPanel, window, door, quoins, gableRoof, flaredGableRoof, gableWall,
  railingRun, furnitureParts, doubleChest, stallFront, horseTrough, paddockFence, lampPost, snowLoad, snowSkirt,
  rakeVerge,
} from '../parts/index.mjs'
import { steepWallDormer } from '../parts/ranch_snowy_dormer.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

const pieces = {
  ...furnitureParts,
  doubleChest: (c, f) => doubleChest(c, f.pos, f.facing, f.along),
  shelf: (c, f) => { // the as-drawn shelf with a pot plant on it (a plant, not crockery)
    furnitureParts.shelf(c, f)
    const [x, y, z] = f.pos; c.free([x, y + 1, z]); c.set(x, y + 1, z, 'potted_spruce_sapling')
  },
}

const log = (axis) => ({ axis })

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = PALETTE
  const B = BARN, W = B.walls, A = B.aisle
  const field = mix(P.field, 7)

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // the plinth under every wall line and the stall fronts; the aisle's setts, the stalls' hay bedding
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    const edge = x === W.x0 || x === W.x1 || z === W.z0 || z === W.z1 || B.loftBeams.includes(x)
    c.set(x, 0, z, edge ? P.plinth : x >= A.x0 && x <= A.x1 ? P.aisle : P.bedding, edge || (x >= A.x0 && x <= A.x1) ? {} : log('y'))
  }
  // the low calcite walls: sides to y 3 under the roof's eave, front and back to y 3 under the floor beam
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, B.wallTop, P.wall)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, B.wallTop, P.wall)
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, B.wallTop, P.wall)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, B.wallTop, P.wall)

  // --- Pass 2: the great roof, the gables ------------------------------------------------------------------------
  const R = B.roof
  const main = flaredGableRoof(c, R, P.roof, P.roof.stairs)
  // the floor beam across both gables (y 4), white birch boards above it to the slate
  for (const z of [W.z0, W.z1]) {
    for (let x = W.x0 + 1; x < W.x1; x++) c.set(x, B.beamY, z, P.frame, log('x'))
    gableWall(c, 'z', z, [W.x0 + 1, W.x1 - 1], B.beamY + 1, main.under, () => P.gable)
    // spruce studs at the gable's quarter points, a king post over the hay door / the back's middle
    for (const x of [3, 9]) for (let y = B.beamY + 1; y < main.under[x]; y++) c.set(x, y, z, P.frame, log('y'))
  }
  for (let y = 7; y < main.under[6]; y++) c.set(6, y, W.z1, P.frame, log('y'))
  for (const x of [W.x0, W.x1]) for (const z of [W.z0, W.z1]) if (!c.get(x, B.beamY, z)) c.set(x, B.beamY, z, P.frame, log('x'))

  // --- Pass 3: openings -----------------------------------------------------------------------------------------
  const bd = OPENINGS.barnDoor
  for (const x of bd.leaves) for (let y = 1; y <= 3; y++) c.set(x, y, W.z0, P.boards)
  c.set(bd.at[0], 3, W.z0, P.frame, log('x')) // the wicket's lintel
  door(c, bd.at, bd.facing, bd.out, { wood: 'dark_oak' })
  const hd = OPENINGS.hayDoor
  door(c, hd.at, hd.facing, hd.out, { wood: 'dark_oak' })
  c.set(hd.at[0], hd.at[1] + 2, W.z0, P.frame, log('x')) // the hay door's lintel
  for (const w of OPENINGS.gableWindows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
  const sl = OPENINGS.stallLights
  for (const x of sl.xs) for (const z of sl.zs) c.set(x, sl.y, z, P.bars)
  for (const w of OPENINGS.backWindows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
  // the pitching dormers, one on each slope over the stall partition
  for (const d of DORMERS) {
    if (d.levels && !d.levels.includes(level)) continue // II's wing takes the east slope's foot: no dormer in its valley
    steepWallDormer(c, d, P.roof, { infill: P.gable, glass: P.glass })
  }
  // the partition's pilaster up each side wall to the eave, and the posts proud under the kicked eave
  for (const x of [W.x0, W.x1]) for (let y = 1; y <= B.wallTop; y++) c.set(x, y, B.partition, P.frame, log('y'))
  for (const x of EAVE_POSTS.xs) for (const z of EAVE_POSTS.zs) for (let y = 0; y <= EAVE_POSTS.top; y++) c.set(x, y, z, P.frame, log('y'))
  // the ladder up to the loft, against the front wall, through its hatch
  const ld = OPENINGS.ladder
  for (let y = ld.y0; y <= ld.top; y++) c.set(ld.x, y, ld.z, 'ladder', { facing: ld.facing, waterlogged: 'false' })

  // --- Pass 4: depth and detail ---------------------------------------------------------------------------------
  quoins(c, [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]], 1, B.wallTop, P.quoin, P.quoinAlt)
  for (const z of B.rakes) rakeVerge(c, P.roof, 'z', z, [0, 12])
  // the smoker's flue up the back gable (round 1: the back's ground storey was one blank calcite panel)
  const K = STACK
  for (let x = K.x0; x <= K.x1; x++) for (let y = 0; y <= K.breastTop; y++) c.set(x, y, K.z, P.plinth)
  stair(c, K.x0, K.breastTop + 1, K.z, P.step, 'east'); stair(c, K.x1, K.breastTop + 1, K.z, P.step, 'west')
  for (let y = K.breastTop + 1; y <= K.top; y++) c.set(K.flue, y, K.z, P.plinth)
  c.set(K.flue, K.top + 1, K.z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  // the hay gallery before the hay door, on brackets, railed
  const G = FOREBAY.gallery
  for (let x = G.x0; x <= G.x1; x++) for (let z = G.z0; z <= G.z1; z++) c.set(x, G.y, z, P.boards)
  for (const x of [G.x0, G.x1]) stair(c, x, G.y - 1, G.z1, `${P.wood}_stairs`, 'south', 'top')
  railingRun(c, [[G.x0, G.y + 1, G.z1], [G.x0, G.y + 1, G.z0], [G.x1, G.y + 1, G.z0], [G.x1, G.y + 1, G.z1]], P.wood)
  // the hoist beam out of the gable over the gallery, its chain and lantern
  const H = FOREBAY.hoist
  for (let z = H.z0; z <= H.z1; z++) c.set(H.x, H.y, z, P.frame, log('z'))
  c.set(H.x, H.y - 1, H.z0, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(H.x, H.y - 2, H.z0, 'lantern', { hanging: 'true', waterlogged: 'false' })
  c.set(...FOREBAY.lamp, 'lantern', { hanging: 'true', waterlogged: 'false' }) // under the gallery, over the doors

  // --- Pass 5: interior: the stalls, the loft, the as-drawn furniture ------------------------------------------------
  for (const s of B.stallSides) {
    stallFront(c, s.front, B.stalls, 1, s.toward, { board: P.boards, gate: `${P.wood}_fence_gate`, rail: `${P.wood}_fence` })
    for (let x = s.x0; x <= s.x1; x++) { c.set(x, 1, B.partition, P.boards); c.set(x, 2, B.partition, `${P.wood}_fence`) }
    post(c, s.front, B.loftPost, 1, B.loftY - 1, P.frame)
    for (const st of B.stalls) c.set(s.toward === 'east' ? s.x0 : s.x1, 1, st.z0 === 4 ? st.z0 : st.z1, P.hay, log('y'))
  }
  // the loft over the aisle: its beams, boards, rails, hay down both sides
  for (const x of B.loftBeams) for (let z = A.z0; z <= A.z1; z++) {
    c.set(x, B.loftY, z, P.frame, log('z'))
    c.set(x, B.loftY + 1, z, `${P.wood}_fence`)
  }
  for (let x = A.x0; x <= A.x1; x++) for (let z = A.z0; z <= A.z1; z++) if (!c.get(x, B.loftY, z)) c.set(x, B.loftY, z, P.loft)
  for (const x of [A.x0, A.x1]) for (let z = 6; z <= A.z1; z++) {
    c.set(x, B.loftY + 1, z, P.hay, log('y'))
    if (x === A.x0 && z >= 7 && z <= 9) c.set(x, B.loftY + 2, z, P.hay, log('y'))
  }
  for (const f of FURNITURE[1]) pieces[f.part](c, f)

  // --- Pass 6: ground: the forebay ------------------------------------------------------------------------------------
  const F = FOREBAY
  for (let x = F.walk.x0; x <= F.walk.x1; x++) {
    stair(c, x, 0, F.walk.z0, P.step, 'south')
    for (let z = F.walk.z0 + 1; z <= F.walk.z1; z++) c.set(x, 0, z, P.paving)
  }
  for (let z = F.firewood.z0; z <= F.firewood.z1; z++) for (let y = 0; y <= F.firewood.top; y++) c.set(F.firewood.x, y, z, P.firewood, log('z'))
  for (const [x, y, z] of F.hay) c.set(x, y, z, P.hay, log('y'))
  horseTrough(c, F.trough.water, F.trough.y, P.rim)

  if (level >= 2) drawLevel2(c, P, field)

  // --- Pass 7: snow -----------------------------------------------------------------------------------------------
  // ridges (capped three wide), the kicked eave lips (y 3; and the wing's free eave at x 20), never under the forebay's
  // other eaves, a dormer's, the porch's, the shelter's
  snowLoad(c, P.roof, P.snow, { eaves: false, tread: () => false })
  // round 2 (note 2): the kick stays a course of stairs; the snow lies on the slate's last course above it (the body's
  // eave stair made a full tile with snow on it), so it sits on the roof, not a block out like a board
  const lie = (x, y, z) => {
    const got = c.get(x, y, z)
    if (got?.id !== `minecraft:${P.roof.stairs}` || got.props.half !== 'bottom' || c.name(x, y + 1, z) !== 'minecraft:air') return
    c.set(x, y, z, P.roof.full); c.set(x, y + 1, z, P.snow, { layers: '2' })
  }
  // round 3 (note 1): the dormers' ridges carry a thin line of snow (two layers) flush on the slate
  for (const d of DORMERS) if (!d.levels || d.levels.includes(level))
    for (const x of [d.face, d.face + d.run]) if (c.name(x, d.y0 + 3, d.z) === `minecraft:${P.snow}`) c.set(x, d.y0 + 3, d.z, P.snow, { layers: '2' })
  // round 3: no single tile of snow left on the barn's eave between its dormers (one white square read as litter);
  // only the wing's free eave carries a run of it
  if (level >= 2) for (let z = WING.roof.along[0]; z <= WING.roof.along[1]; z++) lie(19, 6, z)
  const bare = []
  for (let x = 0; x <= 12; x++) for (let z = 0; z <= 2; z++) bare.push([x, z]) // the forebay stays dry
  if (level >= 2) { // the walk to the wing's porch, under the run-in shelter, by the paddock's gates
    for (let x = 12; x <= 18; x++) for (let z = 0; z <= 4; z++) bare.push([x, z])
    for (let x = 1; x <= 6; x++) for (let z = 17; z <= 19; z++) bare.push([x, z])
    bare.push([1, 16], [19, 16], [19, 17], [13, 12], [12, 12])
  }
  snowSkirt(c, P.snow, bare)
  // round 3 (note 2): the drift in runs against the walls, never single tiles: each line of drift along a wall is cut
  // into runs of four and three (a block's gap between), two layers in a run's middle and one at its ends, the corners
  // joined to the longer run; broken at the doors and walks (the bare cells above); none inside the paddock
  drift(c, P.snow)
  return c
}

// The drift's runs (round 3): the `snow` cells at y 0 that snowSkirt laid round the walls are regrouped into runs.
// Each cell belongs to the line (along x or along z) in which it has the longer unbroken stretch of drift; each line is
// cut into pieces of 4 and 3 with one bare cell between (a last single cell joins the piece before it), two layers in a
// piece's middle and one at its ends. A cell with no neighbour in drift either way is dropped (no litter); none lies
// inside the paddock (z 13 on).
function drift(c, snow) {
  const id = `minecraft:${snow}`, [w, , d] = c.size
  const is = (x, z) => x >= 0 && z >= 0 && x < w && z < d && z < 13 && c.name(x, 0, z) === id
  const cells = []
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) if (c.name(x, 0, z) === id) cells.push([x, z])
  const stretch = (x, z, dx, dz) => { let n = 1; for (let k = 1; is(x + k * dx, z + k * dz); k++) n++; for (let k = 1; is(x - k * dx, z - k * dz); k++) n++; return n }
  const kind = new Map()
  for (const [x, z] of cells) {
    if (!is(x, z)) continue
    const ax = stretch(x, z, 1, 0), az = stretch(x, z, 0, 1)
    kind.set(`${x},${z}`, ax === 1 && az === 1 ? null : ax >= az ? 'x' : 'z')
  }
  const layer = new Map(), seen = new Set()
  for (const [x, z] of cells) {
    const k = kind.get(`${x},${z}`)
    if (!k || seen.has(`${x},${z}`)) continue
    const [dx, dz] = k === 'x' ? [1, 0] : [0, 1]
    const same = (px, pz) => is(px, pz) && kind.get(`${px},${pz}`) === k
    let sx = x, sz = z
    while (same(sx - dx, sz - dz)) { sx -= dx; sz -= dz }
    const line = []
    for (let px = sx, pz = sz; same(px, pz); px += dx, pz += dz) { line.push([px, pz]); seen.add(`${px},${pz}`) }
    const pieces = []
    for (let i = 0, n = 0; i < line.length; n++) {
      const len = Math.min(n % 2 ? 3 : 4, line.length - i)
      pieces.push(line.slice(i, i + len)); i += len
      if (line.length - i <= 2) { pieces[pieces.length - 1].push(...line.slice(i)); i = line.length } else i += 1 // the gap
    }
    for (const pc of pieces) pc.forEach(([px, pz], j) => {
      layer.set(`${px},${pz}`, pc.length === 1 ? null : j === 0 || j === pc.length - 1 ? '1' : '2')
    })
  }
  for (const [x, z] of cells) {
    const n = layer.get(`${x},${z}`)
    if (!n) c.clear(x, 0, z)
    else c.set(x, 0, z, snow, { layers: n })
  }
}

// Level II: the feed and bunk wing east of the barn and the paddock behind; nothing of Level I is moved.
function drawLevel2(c, P, field) {
  const W = WING.walls, R = WING.room
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    const edge = x === W.x0 || x === W.x1 || z === W.z0 || z === W.z1
    c.set(x, 0, z, edge ? P.plinth : P.loft)
  }
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, WING.wallTop, P.wall)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, WING.wallTop, P.wall)
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, WING.wallTop, P.wall)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, WING.wallTop, P.wall)
  quoins(c, [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]], 1, WING.wallTop, P.quoin, P.quoinAlt)
  const wing = gableRoof(c, WING.roof, P.roof, (x, y) => !(x === 12 && y < 4))
  for (const z of [W.z0, W.z1]) {
    for (let x = W.x0 + 1; x < W.x1; x++) c.set(x, WING.beamY, z, P.frame, log('x'))
    gableWall(c, 'z', z, [W.x0 + 1, W.x1 - 1], WING.beamY + 1, wing.under, () => P.gable)
  }
  for (const z of WING.roof.along) rakeVerge(c, P.roof, 'z', z, [12, 20])
  floor(c, R.x0, R.z0, R.x1, R.z1, WING.ceilingY, P.loft) // the bunk room's boarded ceiling

  const o = OPENINGS.wing
  door(c, o.door.at, o.door.facing, o.door.out, { wood: 'dark_oak' })
  for (const w of o.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
  for (const [x, y, z] of o.gableLights) c.set(x, y, z, P.glass)
  // the closed storm porch: dressed cheeks, a lintel, its own pointed little gable with snow on its ridge
  const pc = WING.porch, mid = (pc.x0 + pc.x1) / 2
  for (const x of [pc.x0, pc.x1]) { c.set(x, 0, pc.z, P.plinth); for (let y = 1; y <= pc.top; y++) c.set(x, y, pc.z, P.quoin) }
  c.set(mid, 0, pc.z, P.paving)
  stair(c, mid, 0, pc.z - 1, P.step, 'south')
  c.set(mid, pc.top, pc.z, P.lintel)
  stair(c, pc.x0, pc.top + 1, pc.z, P.roof.stairs, 'east'); stair(c, pc.x1, pc.top + 1, pc.z, P.roof.stairs, 'west')
  c.set(mid, pc.top + 1, pc.z, P.wall)
  c.set(mid, pc.top + 2, pc.z, P.roof.full)
  lampPost(c, ...WING.lampPost, `${P.wood}_fence`, 2)

  for (const f of FURNITURE[2]) pieces[f.part](c, f)
  for (const [x, y, z] of [[18, 1, 10], [18, 2, 10], [18, 1, 9]]) c.set(x, y, z, P.hay, log('y'))
  // the slot between the barn and the wing, under the valley: the winter's firewood, stacked to the kick
  const s = WING.slot
  for (let z = s.z0; z <= s.z1; z++) for (let y = 0; y <= s.top; y++) c.set(s.x, y, z, P.firewood, log('z'))

  // the paddock: the fence round it, the man-gap, the herd's double gate, lamp posts at its corners
  const D = PADDOCK
  paddockFence(c, { ring: D.ring, manGap: D.manGap, gates: D.gates, gateFacing: 'east', lampPosts: D.lampPosts },
    { fence: `${P.wood}_fence`, gate: `${P.wood}_fence_gate` })
  // the run-in shelter, open to the paddock: dressed piers at its back corners, spruce posts in front, a low fieldstone
  // back and sides under a stone-brick coping, a little slate gable over it
  const S = D.shelter
  for (const x of [S.x0, S.x1]) for (let y = 0; y <= S.postTop; y++) {
    c.set(x, y, S.z1, P.plinth)
    c.set(x, y, S.z0, P.frame, log('y'))
  }
  // round 3 (note 3): open on every side but its roof: a fieldstone kerb, then two spruce rails between the piers and
  // the posts (the back and both ends), a spruce plate along the back under the eave
  for (let x = S.x0 + 1; x < S.x1; x++) {
    c.set(x, 0, S.z1, field)
    for (let y = 1; y <= S.postTop; y++) c.set(x, y, S.z1, `${P.wood}_fence`)
  }
  for (const x of [S.x0, S.x1]) for (let z = S.z0 + 1; z < S.z1; z++) {
    c.set(x, 0, z, field)
    for (let y = 1; y <= S.postTop; y++) c.set(x, y, z, `${P.wood}_fence`)
  }
  // a real little slate gable, its ridge running from the paddock to the back (along z) so its pointed end faces the
  // back like the barn's: eaves on the piers' heads (roofY), a stair and a full course in, the ridge two full blocks
  // wide at roofY + 2 carrying the snow; the back gable boarded under it, the front open to the paddock
  const rx = [S.x0, S.x1], ridge = [(S.x0 + S.x1 - 1) / 2, (S.x0 + S.x1 + 1) / 2]
  for (let z = S.z0; z <= S.z1; z++) {
    stair(c, rx[0], S.roofY, z, P.roof.stairs, 'east'); stair(c, rx[1], S.roofY, z, P.roof.stairs, 'west')
    c.set(rx[0] + 1, S.roofY, z, P.roof.full); c.set(rx[1] - 1, S.roofY, z, P.roof.full)
    stair(c, rx[0] + 1, S.roofY + 1, z, P.roof.stairs, 'east'); stair(c, rx[1] - 1, S.roofY + 1, z, P.roof.stairs, 'west')
    for (const x of ridge) { c.set(x, S.roofY + 1, z, P.roof.full); c.set(x, S.roofY + 2, z, P.roof.full) }
  }
  for (const x of ridge) c.set(x, S.roofY, S.z1, P.boards) // the back gable's boards under the ridge
  for (const [x, y, z] of D.shelterHay) c.set(x, y, z, P.hay, log('y'))
  horseTrough(c, D.trough.water, D.trough.y, P.rim)
  for (let y = 0; y <= 2; y++) c.set(D.snubbingPost[0], y, D.snubbingPost[1], P.frame, log('y'))
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
