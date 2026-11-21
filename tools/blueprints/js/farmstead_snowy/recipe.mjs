// Farmstead, the snowy look (ROADMAP 49.10e): assembles plan.mjs from the parts library (../parts) in passes (block-out
// and structure, roofs, openings, depth and detail, interior, cellar, ground and field, snow) for LEVEL = 1..3, each
// level the one before in its cells plus what it adds (II the passage, the barn and the second field; III the cellar
// and its hatch, nothing else above the ground). Writes furniture.json (the furniture spots) and, for III,
// out/cellar.txt. Build all three: build.sh. CUT=<y> cuts everything above y (a floor's cutaway).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  SIZE, BASEMENT, IDS, PALETTE, HOUSE, GABLET, NICHE, STACK, WOODPILE, PORCH, BARN, FIELD, FIELD2, KERB, FIELD_LAMPS,
  CELLAR, CELLAR_STORE, OPENINGS, FURNITURE, CROPS, WEST_GABLE,
} from './plan.mjs'
import {
  LiftedCanvas, mix, stair, slab, trapdoor, floor, window, door, quoins, gableRoof, frontDormer,
  snowLoad, rakeVerge, furnitureParts, cellar, hatchLadder, louvre, railingRun, fieldPlot, kerbFence,
  scarecrow, composter,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const hang = { hanging: 'true', waterlogged: 'false' }

export function draw(level) {
  const c = new LiftedCanvas(SIZE[level], BASEMENT[level] ?? 0)
  const P = PALETTE, H = HOUSE, B = BARN
  const two = level >= 2
  const field = mix(P.field, 7)
  const has = o => (o.level ?? 1) <= level
  const log = (id, axis) => [id, { axis }]
  const galleryEnd = PORCH.z1[two ? 2 : 1]

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // The hall: a stone-brick plinth ring, spruce boards inside, the hearthstone
  for (let x = H.x0; x <= H.x1; x++) for (let z = H.z0; z <= H.z1; z++) {
    const edge = x === H.x0 || x === H.x1 || z === H.z0 || z === H.z1
    c.set(x, 0, z, edge ? P.plinth : P.floor)
  }
  for (let z = 4; z <= 6; z++) c.set(2, 0, z, P.plinth) // the hearthstone
  // the low eave walls (front z 1, back z 8): calcite y 1..3, the andesite course at y 4 under the roof's first course
  for (const z of [H.z0, H.z1]) for (let x = H.x0; x <= H.x1; x++) for (let y = 1; y <= H.band; y++) c.set(x, y, z, y === H.band ? P.course : P.wall)
  // the gables x 1 and x 7 up to the roof, the band at y 4
  for (const x of [H.x0, H.x1]) for (let z = H.z0 + 1; z < H.z1; z++)
    for (let y = 1; y < H.under[z]; y++) c.set(x, y, z, y === H.band ? P.course : P.wall)
  // the hearth's dressed surround in the west wall (the fire itself is the shipped fixture at (1, 1, 5))
  for (const z of [4, 6]) for (let y = 1; y <= 3; y++) c.set(1, y, z, P.quoin)
  for (let y = 2; y <= 3; y++) c.set(1, y, 5, P.lintel)
  // tie beams across the hall at y 5 (the shipped lamps hang from them)
  for (const z of H.tieBeams) for (let x = H.room.x0; x <= H.room.x1; x++) c.set(x, H.beamY, z, ...log(P.beam, 'x'))

  // The back porch (I) / cross passage (II): flags at z 9 under the back eave, a step from the west
  for (let x = H.x0; x <= H.x1; x++) c.set(x, 0, 9, field)
  stair(c, ...OPENINGS.passageStep, P.step, 'east')
  // The gallery: a boarded deck along the east gable under the porch (on under the barn's eave in II), a step up
  for (let z = PORCH.z0; z <= galleryEnd; z++) c.set(PORCH.deckX, 0, z, P.deck)
  stair(c, ...PORCH.step, `${P.wood}_stairs`, 'south')

  // The barn (II): a plinth ring, a beaten-earth floor, calcite walls with the loft floor's band and birch boards above
  if (two) {
    for (let x = B.x0; x <= B.x1; x++) for (let z = B.z0; z <= B.z1; z++) {
      const edge = x === B.x0 || x === B.x1 || z === B.z0 || z === B.z1
      c.set(x, 0, z, edge ? P.plinth : P.barnFloor)
    }
    const barnWall = y => (y <= B.wallTop ? P.wall : y === B.band ? P.course : P.face)
    for (const x of [B.x0, B.x1]) for (let z = B.z0 + 1; z < B.z1; z++) for (let y = 1; y < B.under[x]; y++) c.set(x, y, z, barnWall(y))
    for (const z of [B.z0, B.z1]) for (let x = B.x0; x <= B.x1; x++) for (let y = 1; y < B.under[x]; y++) c.set(x, y, z, barnWall(y))
    for (const z of B.tieBeams) for (let x = B.room.x0; x <= B.room.x1; x++) c.set(x, B.band, z, ...log(P.beam, 'x'))
    // the hay loft at the back on a ladder up the west wall (the shipped ladder's cells), a rail along its edge
    const L = B.loft
    floor(c, B.room.x0, L.z0, B.room.x1, L.z1, L.y, P.floor)
    for (let y = 1; y <= L.y; y++) c.set(2, y, L.z0 - 1, 'ladder', { facing: 'east', waterlogged: 'false' })
    railingRun(c, [[3, L.y + 1, L.z0], [6, L.y + 1, L.z0]], P.wood)
    // the second Farmer's chamber under the loft: board walls, its door from the barn floor
    const K = B.chamber
    for (let x = B.room.x0; x <= K.doorX; x++) for (let y = 1; y < L.y; y++) c.set(x, y, K.wallZ, P.floor)
    for (let z = K.z0; z <= K.z1; z++) for (let y = 1; y < L.y; y++) c.set(K.wallX, y, z, P.floor)
    door(c, [K.doorX, 1, K.wallZ], 'south', 'north', { wood: P.wood })
    // two stalls down the east wall: a fence front with a gate to each, dividers, a sunken trough and hay in each
    const S = B.stalls
    for (let z = S.z0; z <= S.z1; z++) {
      if (S.gates.includes(z)) c.set(S.front, 1, z, `${P.wood}_fence_gate`, { facing: 'east', in_wall: 'false', open: 'false', powered: 'false' })
      else c.set(S.front, 1, z, `${P.wood}_fence`)
    }
    for (const z of S.dividers) c.set(B.room.x1, 1, z, `${P.wood}_fence`)
    for (const z of S.troughs) c.set(B.room.x1, 0, z, 'water', { level: '0' })
    for (const z of S.hay) c.set(B.room.x1, 1, z, 'hay_block', { axis: 'y' })
    // the tool wall: hooks on the west wall over a board bench
    for (const z of B.tools.z) c.set(B.tools.x, 2, z, 'tripwire_hook', { facing: 'east', attached: 'false', powered: 'false' })
    for (const z of B.tools.z.slice(0, 2)) slab(c, B.tools.x, 1, z, `${P.wood}_slab`, 'top')
  }

  // --- Pass 2: the roofs --------------------------------------------------------------------------------------------
  // The hall's steep roof, ridge along x (the long side to the road), run on two blocks past the east gable as the
  // gable porch; the stack takes its cells at x 0, z 4..5
  const stackCell = (x, y, z) => x === STACK.x && STACK.zs.includes(z) && y >= STACK.weathering
  hallRoof(c, H.roof, P.roof, (x, y, z) => !stackCell(x, y, z))
  // the gablet over the door: birch posts and boards round a tall light, its own steep slate gable; round 3 (note 3):
  // no snow on it at all, its little ridge stays slate (snowLoad skips it), so no white piece floats mid-slope
  frontDormer(c, GABLET, P.roof, { cheek: P.post, infill: P.face, glass: P.glass, verge: P.roof.stairs, vergeTop: P.roof.full })
  // and the verge's apex notch under it closed in slate, so the snow cap sits on a whole little roof, not over a gap
  if (!c.get(GABLET.cx, GABLET.y0 + 3, GABLET.face - 1)) c.set(GABLET.cx, GABLET.y0 + 3, GABLET.face - 1, P.roof.full)
  // The barn (II): its steep roof, ridge along z, the front rake flush on its gable, the back rake one out
  if (two) {
    gableRoof(c, B.roof, P.roof)
    const lv = B.louvre
    louvre(c, lv.x, lv.z, lv.y, { base: P.roof.full, post: `${P.wood}_fence`, roof: P.roof })
    c.set(lv.x, lv.y + 3, lv.z, 'lightning_rod', { facing: 'up', powered: 'false', waterlogged: 'false' })
  }

  // --- Pass 3: openings ----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor, bd = OPENINGS.backDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.wood, step: fd.step, stepStair: P.step })
  door(c, bd.at, bd.facing, bd.out, { wood: P.wood })
  if (two) {
    for (const o of [...OPENINGS.passageDoors, OPENINGS.barnDoor, OPENINGS.barnSideDoor, OPENINGS.hayDoor]) door(c, o.at, o.facing, o.out, { wood: P.wood })
    for (const o of OPENINGS.passageDoors) for (let y = 3; y < H.roof.eaveY; y++) c.set(o.at[0], y, o.at[2], y === 3 ? P.lintel : P.wall) // the passage's ends closed over its doors, under the back eave
    const wd = OPENINGS.wagonDoors
    wd.at.forEach((at, i) => door(c, at, wd.facing, wd.out, { wood: P.wood, hinge: i ? 'right' : 'left' }))
    for (let x = wd.lintel.x0; x <= wd.lintel.x1; x++) c.set(x, wd.lintel.y, B.z1, ...log(P.beam, 'x'))
    for (const [x, , z] of wd.at) stair(c, x, 0, z + 1, P.step, 'north')
  }
  for (const w of OPENINGS.windows) if (has(w)) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, sill: P.sill })

  // --- Pass 4: depth and detail ---------------------------------------------------------------------------------
  // long-and-short quoins at the hall's corners (and the barn's)
  quoins(c, [[H.x0, H.z0], [H.x1, H.z0], [H.x0, H.z1], [H.x1, H.z1]], 1, H.wallTop, P.quoin, P.quoinAlt)
  if (two) quoins(c, [[B.x0, B.z0], [B.x1, B.z0], [B.x0, B.z1], [B.x1, B.z1]], 1, B.wallTop, P.quoin, P.quoinAlt)
  // the storm niche before the door: stone cheeks, an andesite lintel, lanterns hung from the gablet's eaves
  for (const x of NICHE.cheeks) for (let y = 1; y <= NICHE.top; y++) c.set(x, y, NICHE.z, P.quoin)
  c.set(...NICHE.lintel, P.lintel)
  c.set(...NICHE.lamp, 'lantern', hang) // hung under the lintel, over the doorway's two clear blocks
  for (const at of NICHE.lamps) c.set(...at, 'lantern', hang)
  // the stack on the west gable: a broad breast of gathered stone, a two-wide stack through the ridge, its cap and smoke
  const S = STACK
  for (let z = S.breast.z0; z <= S.breast.z1; z++) for (let y = 0; y <= S.breast.top; y++) c.set(S.x, y, z, field)
  for (const z of S.zs) for (let y = S.breast.top + 1; y <= S.top; y++) c.set(S.x, y, z, P.stack)
  // round 2 (note 2): the breast steps in once, a weathering at y 5 on either side of the stack
  stair(c, S.x, S.weathering, S.breast.z0, P.step, 'south'); stair(c, S.x, S.weathering, S.breast.z1, P.step, 'north')
  // round 3 (note 2): the west gable closed in the rake's plane, calcite up to the slate on both sides of the stack over
  // the andesite course (carried on round from the walls) on the breast and two stone corbels, so the slate ends on a
  // whole wall and the grey stack stands against it
  const WG = WEST_GABLE
  for (const z of WG.beamZ) c.set(WG.x, WG.beamY, z, P.course)
  for (const z of WG.corbels) stair(c, WG.x, WG.beamY - 1, z, P.step, 'east', 'top')
  // the slate's own blocks in that plane under each rake stair are calcite too, so only the stairs' edge shows: a thin verge
  for (const z of WG.boards) for (let y = WG.beamY + 1; c.name(WG.x, y, z) !== `minecraft:${P.roof.stairs}`; y++) c.set(WG.x, y, z, P.wall)
  c.set(...S.fire, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  slab(c, ...S.slab, P.capSlab)
  // the woodpile under the west rake, split logs laid along the wall
  for (let z = WOODPILE.z0; z <= WOODPILE.z1; z++) for (let y = 0; y < WOODPILE.top; y++) c.set(WOODPILE.x, y, z, ...log(P.firewood, 'z'))
  if (!two) for (const at of H.backWood) c.set(...at, ...log(P.firewood, 'x')) // firewood on the back porch (I)
  // the gable porch: its gable closed in birch boards over a dark beam at y 4, two dark studs, lanterns hung in it
  const sx = PORCH.screenX
  for (let z = PORCH.screen.z0; z <= PORCH.screen.z1; z++) {
    c.set(sx, PORCH.beamY, z, ...log(P.beam, 'z'))
    for (let y = PORCH.beamY + 1; y < H.under[z]; y++) c.set(sx, y, z, ...(PORCH.studs.includes(z) ? log(P.stud, 'y') : [P.face]))
  }
  for (let z = PORCH.screen.z0; z <= PORCH.screen.z1; z++) // the gallery's board ceiling, the gable's space over it packed
    for (let y = PORCH.soffitY; y < H.under[z]; y++) c.set(PORCH.deckX, y, z, P.floor)
  for (const at of PORCH.lamps) c.set(...at, 'lantern', hang)
  if (two) {
    // the back gable: a slate snow hood over the wagon doors and one over the hay door, the hoist beam and its lantern
    const wh = B.wagonHood, hh = B.hayHood
    for (let x = wh.x0; x <= wh.x1; x++) stair(c, x, wh.y, wh.z, P.roof.stairs, 'north')
    for (let x = hh.x0; x <= hh.x1; x++) stair(c, x, hh.y, hh.z, P.roof.stairs, 'north')
    c.set(...B.hoist.beam, ...log(P.beam, 'z'))
    for (const at of B.hoist.chain) c.set(...at, 'chain', { axis: 'y', waterlogged: 'false' })
    c.set(...B.hoist.lamp, 'lantern', hang)
    for (const at of B.eaveLamps) c.set(...at, 'lantern', hang)
  }

  // --- Pass 5: interior (the shipped furniture, cell for cell) -----------------------------------------------------
  for (const f of FURNITURE) {
    if (!has(f)) continue
    if (f.part === 'composter') composter(c, f)
    else furnitureParts[f.part](c, f)
  }
  if (two) {
    for (const [x, y, z] of B.hay) c.set(x, y, z, 'hay_block', { axis: 'y' })
    c.set(2, 1, 14, 'pumpkin')
  }

  // --- Pass 6: the root and ice cellar (III) --------------------------------------------------------------------
  let dug = null
  if (level >= 3) {
    const C = CELLAR
    dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, field, P.cellarFloor)
    hatchLadder(c, C.ladder.x, C.ladder.z, -C.depth + 1, 0, C.ladder.facing, P.wood)
    for (const [id, z] of CELLAR_STORE.shelves) { c.set(6, -3, z, id); slab(c, 6, -2, z, `${P.wood}_slab`, 'top') }
    for (const at of CELLAR_STORE.ice) c.set(...at, P.ice)
    for (const at of CELLAR_STORE.hay) c.set(...at, 'hay_block', { axis: 'y' })
  }

  // --- Pass 7: the fields -----------------------------------------------------------------------------------------
  // farmland round a channel down x 11 (boarded over with closed spruce hatches against the frost), the scarecrow's
  // stone footing in the channel, crops in the shipped rows
  const cropAt = rows => (x, z) => { const r = rows[x]; return r ? [r[0], r[1][z % r[1].length]] : null }
  const hatch = (x, z) => trapdoor(c, x, 1, z, `${P.wood}_trapdoor`, 'north', 'bottom', false)
  fieldPlot(c, FIELD, {
    water: (x, z) => x === FIELD.channelX && z !== FIELD.scarecrowZ,
    footing: (x, z) => (x === FIELD.channelX && z === FIELD.scarecrowZ ? P.plinth : null),
    crop: cropAt(CROPS[1]),
  })
  for (let z = FIELD.z0; z <= FIELD.z1; z++) if (z !== FIELD.scarecrowZ) hatch(FIELD.channelX, z)
  kerbFence(c, KERB[1], 0, field, `${P.wood}_fence`)
  scarecrow(c, [FIELD.channelX, 1, FIELD.scarecrowZ], 'west', { leg: `${P.wood}_fence`, body: 'hay_block', arms: `${P.wood}_fence` })
  if (two) {
    fieldPlot(c, FIELD2, {
      water: (x, z) => x === FIELD2.channelX && z >= FIELD2.water[0] && z <= FIELD2.water[1],
      crop: (x, z) => (x === FIELD2.channelX ? ['carrots', 4] : cropAt(CROPS[2])(x, z)),
    })
    for (let z = FIELD2.water[0]; z <= FIELD2.water[1]; z++) hatch(FIELD2.channelX, z)
    kerbFence(c, KERB[2], 0, field, `${P.wood}_fence`)
  }
  for (const [x, z] of [...FIELD_LAMPS[1], ...(two ? FIELD_LAMPS[2] : [])]) c.set(x, 2, z, 'lantern', { hanging: 'false', waterlogged: 'false' })

  // --- Pass 8: snow ---------------------------------------------------------------------------------------------------
  // on the ridges (the hall's two back-to-back stairs set full), the eave lips, never on a rake, the gablet's verge,
  // a hood or the louvre
  const rake = (x, z) => z >= 1 && z <= 8 && H.rakes.includes(x) // round 3: the eave lips run on over the corners, edge to edge
  const gablet = (x, y, z) => x >= GABLET.cx - 2 && x <= GABLET.cx + 2 && z <= GABLET.face + 1 && y >= GABLET.y0 + 2
  const lv = B.louvre
  const louvreCell = (x, y, z) => two && Math.abs(x - lv.x) <= 1 && Math.abs(z - lv.z) <= 1 && y >= lv.y
  snowLoad(c, P.roof, P.snow, {
    eaves: true, ridge: '2',
    tread: (x, y, z) => (y === H.ridgeY && H.ridgeZ.includes(z) && x <= 9)
      || (y === H.snowLine.y && H.snowLine.zs.includes(z) && x <= 9) // the hall's snow line, rake to rake (round 2)
      || (two && y === B.snowLine.y && B.snowLine.xs.includes(x) && z >= B.z0 && z <= B.z1), // the barn's, off its back verge
    skip: (x, y, z) => louvreCell(x, y, z) || gablet(x, y, z),
    eaveSkip: (x, y, z) => rake(x, z) || gablet(x, y, z) || louvreCell(x, y, z) || (two && z === B.z1 + 1 && (y === B.wagonHood.y || y === B.hayHood.y)),
  })
  // the rakes: one clean steep verge each (the hall's at x 0 and x 9, the barn's back one at z 21)
  rakeVerge(c, P.roof, 'x', 9, [0, 9])
  if (two) rakeVerge(c, P.roof, 'z', B.roof.along[1], [0, 8])
  // snow on the sills, the niche's tops, the woodpile, the scarecrow's head
  const dust = (x, y, z, n) => { if (c.name(x, y, z) === 'minecraft:air') c.set(x, y, z, P.snow, { layers: String(n) }) }
  for (const w of OPENINGS.windows) if (has(w) && w.dress === 'sill') {
    const [x, y, z] = w.cells[0]; const o = { north: [0, -1], west: [-1, 0] }[w.out]
    dust(x + o[0], y, z + o[1], 1)
  }
  for (const x of NICHE.cheeks) dust(x, NICHE.top + 1, NICHE.z, 2)
  dust(NICHE.lintel[0], NICHE.lintel[1] + 1, NICHE.lintel[2], 1)
  for (let z = WOODPILE.z0; z <= WOODPILE.z1; z++) dust(WOODPILE.x, WOODPILE.top, z, 2)
  dust(FIELD.channelX, 4, FIELD.scarecrowZ, 2)
  // the drift round every wall and kerb, off the doorsteps
  drift(c, P.snow, two)
  return { c, dug }
}

// round 3 (note 1): the hall's roof, blocks and stairs alternating up each slope (H.roof.steps: each column's stair y,
// full blocks under it down to the column before's stair, so every stair sits on slate), the two ridge stairs back to
// back; a 3:2 pitch that keeps the snowy village's steep look with the ridge two courses lower than 2:1
function hallRoof(c, { across: [a0, a1], along: [l0, l1], steps }, roof, keep) {
  const n = (a1 - a0 + 1) / 2
  for (let x = l0; x <= l1; x++) for (const [z0, dir, rise] of [[a0, 1, 'south'], [a1, -1, 'north']]) for (let i = 0; i < n; i++) {
    const z = z0 + dir * i, from = i === 0 ? steps[0] : i === 1 ? steps[0] + 1 : steps[i - 1]
    for (let y = from; y < steps[i]; y++) if (keep(x, y, z)) c.set(x, y, z, roof.full)
    if (keep(x, steps[i], z)) stair(c, x, steps[i], z, roof.stairs, rise)
  }
}

// the drift (round 2, note 4; round 3, note 4): the free ground cells banked against a wall (the house's, the barn's,
// the breast, the woodpile), four deep where two walls meet, two or three along one; none on open grass, along the
// kerbs or by the flags, kept off the steps and doorsteps.
function drift(c, snow, two) {
  const [w, , d] = c.size
  const solid = (x, z) => x >= 0 && x < w && z >= 0 && z < d && (c.name(x, 0, z) !== 'minecraft:air' || c.name(x, 1, z) !== 'minecraft:air')
  // round 3: something standing a block up to lean on (a wall, the kerb's fence, a step's riser), not flags or a deck
  const upright = (x, z) => x >= 0 && x < w && z >= 0 && z < d && !/air|snow|wheat|carrots|potatoes|beetroots|lantern|torch/.test(c.name(x, 1, z))
  const wall = (x, z) => x >= 0 && x < w && z >= 0 && z < d && /calcite|stone_bricks|polished_andesite|_log|cobblestone|stone$|andesite/.test(c.name(x, 1, z)) && !/stairs|slab/.test(c.name(x, 1, z))
  const bare = new Set(['7,0', '9,0', '0,8', '0,10', '4,21', '7,21', '3,1', '5,1'].filter(k => two || !['0,8', '0,10', '4,21', '7,21'].includes(k)))
  const hash = (x, z) => { let h = (x * 73856093) ^ (z * 19349663) ^ 0x5bd1e995; h = Math.abs((h ^ (h >>> 13)) * 1274126177); return h % 100 }
  const add = []
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    if (solid(x, z) || bare.has(`${x},${z}`)) continue
    // round 3 (note 4): only where it is banked against a wall (the house's, the barn's, the breast, the woodpile), never
    // loose on the grass or along the kerbs, deeper where two walls meet
    const sides = [[1, 0], [-1, 0], [0, 1], [0, -1]]
    if (!sides.some(([dx, dz]) => upright(x + dx, z + dz))) continue
    const walls = sides.filter(([dx, dz]) => wall(x + dx, z + dz)).length
    const h = hash(x, z)
    let n = walls >= 2 ? 4 : walls === 1 ? (h < 55 ? 3 : 2) : 0
    if (n) add.push([x, z, n])
  }
  for (const [x, z, n] of add) c.set(x, 0, z, snow, { layers: String(n) })
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  if (dug) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(','))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
