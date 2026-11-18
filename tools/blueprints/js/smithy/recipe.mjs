// The Smithy (smithy, smithy_2; ROADMAP 49), the recipe: assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roofs and gables, openings, depth and detail, interior, cellar, ground and light) for
// LEVEL = 1 or 2 (Level II is Level I grown: every Level I cell it keeps is drawn by the same lines). Returns the
// structure for the minecraft-builder skill's runner and writes furniture.json (the furniture spots). Build both
// levels: build.sh. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FORGE, FURNITURE, STORE, OUTSIDE } from './plan.mjs'
import {
  LiftedCanvas, mix, plinth, floor, wallPanel, window, door, gableRoof, gableWall, bargeTrim, hood, railingRun,
  furnitureParts, cellar, quoins, stair, lampPost, stockShelf, taperedStack, quenchTrough, furnaceMouth, clumped,
  steppedBreast, Canvas,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT)
  const P = PALETTE
  const two = level >= 2
  const base = mix(P.plinth, 5), rubble = clumped(mix(P.rubble, 17), 2), vault = mix(P.vaultWall, 3)
  const S = MASSES.shed, ST = MASSES.stack, CH = MASSES.chamber
  const zBack = S.z1[level]
  const at = l => (l ?? 1) <= level
  const shown = o => (o.only ? o.only === level || (o.only === true && o.level === level) : at(o.level))
  const ashlar = clumped(mix(P.ashlar, 23), 2)
  // the stacks' boxes (x0, x1, z0, z1): the shed's roof and walls leave them alone
  const stacks = [[0, 6, 2, 8], ...(two ? [[2, 6, 9, 13]] : [])]
  const inStack = (x, z) => stacks.some(([a, b, p, q]) => x >= a && x <= b && z >= p && z <= q)

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  plinth(c, 0, 0, 5, 1, base) // the apron in front of the stack, where the trough stands
  plinth(c, S.x0, S.z0, S.x1, zBack, base)
  floor(c, S.x0 + 1, S.z0 + 1, S.x1 - 1, zBack - 1, 0, P.floor)
  if (two) { plinth(c, 0, 9, 1, 15, base); plinth(c, 2, 14, 5, 15, base) } // the yard's paving

  // the shed's stone walls (the west wall only where no stack stands), dressed corners, the beam on top, tie beams
  wallPanel(c, [S.x0, S.z0], [S.x1, S.z0], 1, S.wallTop, rubble)
  wallPanel(c, [S.x1, S.z0], [S.x1, zBack], 1, S.wallTop, rubble)
  wallPanel(c, [S.x0, zBack], [S.x1, zBack], 1, S.wallTop, rubble)
  // (II) the old back wall's west end stays, a pier against the stack: the hall's chest at (7, 1, 7) keeps its back to it
  if (two) wallPanel(c, [S.x0 + 1, 8], [S.x0 + 1, 8], 1, S.wallTop, rubble)
  const corners = [[S.x0, S.z0], [S.x1, S.z0], [S.x1, 8], [S.x0, zBack], [S.x1, zBack]].filter(([x, z]) => !inStack(x, z))
  quoins(c, corners, 1, S.wallTop, P.quoin[0], P.quoin[1])
  for (let x = S.x0; x <= S.x1; x++) for (let z = S.z0; z <= zBack; z++) {
    const ring = x === S.x0 || x === S.x1 || z === S.z0 || z === zBack || (two && z === 8)
    if (!ring) continue
    const corner = (x === S.x0 || x === S.x1) && (z === S.z0 || z === zBack || z === 8)
    c.set(x, S.beamY, z, P.frame, { axis: corner ? 'y' : z === S.z0 || z === zBack || z === 8 ? 'x' : 'z' })
  }
  for (const z of S.ties[level]) for (let x = S.x0 + 1; x < S.x1; x++) c.set(x, S.beamY, z, P.frame, { axis: 'x' })

  // --- Pass 2: roofs and gables ----------------------------------------------------------------------------------
  const roof = gableRoof(c, { ...S.roof, along: [0, zBack + 1], steep: false }, P.roof, (x, y, z) => x >= S.x0 && !inStack(x, z))
  const gable = (line, glass) => gableWall(c, 'z', line, [S.x0, S.x1], S.beamY + 1, roof.under, (u, y) => {
    if (u === 9 && y <= 6) return glass ? P.glass : P.nog
    if (u === 9 || u === 8 || u === 10) return [P.frame, { axis: 'y' }]
    return P.nog
  })
  gable(S.z0, true)
  gable(8, true)
  if (two) gable(zBack, true)
  bargeTrim(c, 'z', 0, [S.x0, S.x1 + 1], roof.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', zBack + 1, [S.x0, S.x1 + 1], roof.under, `${P.trim}_stairs`)

  // The Stack: dressed stone over a cobble base course, cobble quoins, a string course at the shed's eave line, a
  // ledge, the 5 x 5 tier with a recessed slit in each face, a ledge, the flue, the crown and the smoke
  const stackOpts = {
    body: (x, y, z) => (y <= 1 ? base(x, y, z) : ashlar(x, y, z)), ledge: P.ledge, crown: P.brick, crownSlab: P.brickSlab,
    quoin: i => (i < 2 ? P.quoin : null),
  }
  const course = (cx, cz, h, y) => { // a string course round a tier's faces, the quoins left whole
    for (let x = cx - h; x <= cx + h; x++) for (let z = cz - h; z <= cz + h; z++) {
      const edge = Math.abs(x - cx) === h || Math.abs(z - cz) === h, corner = Math.abs(x - cx) === h && Math.abs(z - cz) === h
      if (edge && !corner) c.set(x, y, z, P.course)
    }
  }
  taperedStack(c, ST.cx, ST.cz, 0, ST.tiers, stackOpts)
  course(ST.cx, ST.cz, 3, ST.courseY)
  // the west face: pilasters (z 2, 5, 8) with the wall stepped back between them over a batter, barred windows
  const WF = MASSES.westFace
  for (let y = 1; y <= ST.tiers[0].top; y++) c.set(0, y, 5, (y - 1) % 2 ? P.quoin[1] : P.quoin[0])
  c.set(0, ST.courseY, 5, P.course)
  for (const [z0, z1] of WF.panels) for (let z = z0; z <= z1; z++) {
    stair(c, 0, WF.batterY, z, P.ledge, 'east')
    for (const y of WF.windowY) { c.clear(0, y, z); c.set(1, y, z, P.bars) }
  }
  // the 5 x 5 tier's recessed slits: a reveal one block deep, the bars set back in it
  for (const [dx, dz] of [[-2, 0], [2, 0], [0, -2], [0, 2]]) for (const y of [8, 9]) {
    c.clear(ST.cx + dx, y, ST.cz + dz)
    c.set(ST.cx + dx / 2, y, ST.cz + dz / 2, P.bars)
  }
  for (let x = CH.x0; x <= CH.x1; x++) for (let z = CH.z0; z <= CH.z1; z++) {
    for (let y = 1; y <= CH.corbelY; y++) c.clear(x, y, z)
    c.set(x, 0, z, P.floor)
  }
  if (two) { // Stack II: the Armorer's, smaller, smoking lower
    const S2 = MASSES.stack2, C2 = MASSES.chamber2
    taperedStack(c, S2.cx, S2.cz, 0, S2.tiers, { ...stackOpts, bodyOf: i => (i === 1 ? P.dressed : null), quoin: i => (i < 1 ? P.quoin : null) })
    course(S2.cx, S2.cz, 2, S2.courseY)
    for (let x = C2.x0; x <= C2.x1; x++) for (let z = C2.z0; z <= C2.z1; z++) {
      for (let y = 1; y <= C2.top; y++) c.clear(x, y, z)
      c.set(x, 0, z, P.floor)
    }
  }

  // --- Pass 3: openings -----------------------------------------------------------------------------------------
  // one hall: the stacks' feet open to the shed under a dark oak lintel
  const open = ({ x, z: [z0, z1], height }) => {
    for (let z = z0; z <= z1; z++) {
      for (let y = 1; y <= height; y++) c.clear(x, y, z)
      c.set(x, height + 1, z, P.frame, { axis: 'z' })
    }
  }
  open(OPENINGS.hall)
  if (two) open(OPENINGS.hall2)
  const fd = OPENINGS.door
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step })
  for (const w of OPENINGS.windows) {
    if (shown(w)) window(c, w.cells, w.out, 'shutters', { glass: P.glass, wood: P.wood })
  }
  for (const s of OPENINGS.slits) if (shown(s)) for (const [x, y, z] of s.cells) c.set(x, y, z, P.bars)

  // --- Pass 4: depth and detail ---------------------------------------------------------------------------------
  hood(c, fd.at[0], S.beamY, 0, P.roof)
  furnaceMouth(c, FORGE.blastFurnace, 'north', 'blast_furnace', { brick: P.brick, hoodStairs: P.brickStairs, top: 3 })
  if (two) furnaceMouth(c, FORGE.blastFurnace2, 'north', 'blast_furnace', { brick: P.brick, hoodStairs: P.brickStairs, top: 3 })
  // the chamber's corbelled vault: upside-down stairs round its walls under the stone ceiling
  for (let x = CH.x0; x <= CH.x1; x++) for (let z = CH.z0; z <= CH.z1; z++) {
    const side = z === CH.z0 ? 'north' : z === CH.z1 ? 'south' : x === CH.x0 ? 'west' : x === CH.x1 ? 'east' : null
    if (side) stair(c, x, CH.corbelY, z, P.ledge, side, 'top')
  }
  quenchTrough(c, FORGE.trough.cells, FORGE.trough.y, P.dressed)
  // buttresses against the stacks' backs: a stepped one behind each furnace mouth (its back never seen), corners
  for (const b of MASSES.buttresses) if (shown(b)) steppedBreast(c, b.cells, 0, b.top, base, P.ledge, b.facing)
  if (two) { // the Ore Shed: a lean-to on posts, its roof tied into Stack II's west face
    const O = MASSES.oreShed
    for (let z = O.z[0]; z <= O.z[1]; z++) {
      stair(c, O.x[0], O.low, z, P.roof.stairs, 'east')
      stair(c, O.x[1], O.low + 1, z, P.roof.stairs, 'east')
    }
    for (const z of O.z) for (let y = 1; y < O.low; y++) c.set(O.x[0], y, z, P.frame, { axis: 'y' })
  }

  // --- Pass 5: interior -----------------------------------------------------------------------------------------
  c.set(...FORGE.smithingTable, 'smithing_table')
  c.set(...FORGE.grindstone, 'grindstone', { face: 'floor', facing: 'west' })
  c.set(...FORGE.anvil, 'anvil', { facing: 'north' })
  quenchTrough(c, FORGE.quench.cells, FORGE.quench.y, P.rim) // the quench tub beside the anvil
  if (two) c.set(...FORGE.anvil2, 'chipped_anvil', { facing: 'west' })
  for (const s of STORE) if (at(s.level)) for (let i = 0; i < s.h; i++) c.set(s.at[0], s.at[1] + i, s.at[2], s.block)
  for (const f of FURNITURE) {
    if (!at(f.level)) continue
    if (f.part === 'stockShelf') stockShelf(c, f)
    else furnitureParts[f.part](c, f)
  }

  // --- Pass 6: the Coal Vault (the cellar) and its stair ----------------------------------------------------------
  const V = MASSES.cellar
  const dug = cellar(c, V.x0, V.z0, V.x1, V.z1, V.depth, vault, P.vaultFloor)
  const cs = OPENINGS.cellarStair
  for (const [y, z] of cs.steps) stair(c, cs.x, y, z, P.vaultStairs, cs.rises)
  for (const [y, z] of cs.fill) c.set(cs.x, y, z, vault)
  for (const z of cs.open) c.clear(cs.x, 0, z)
  railingRun(c, [[cs.x + 1, 1, cs.open[0]], [cs.x + 1, 1, cs.open[cs.open.length - 1]]], P.wood) // the coal bin closes its far end
  const T = MASSES.track // the coal tubs' rail run: from a stone buffer to the chest it unloads into
  for (const [x, y, z] of T.rails) c.set(x, y, z, 'rail', { shape: 'east_west', waterlogged: 'false' })
  stair(c, ...T.buffer, P.ledge, 'east')

  // --- Pass 7: ground and light ----------------------------------------------------------------------------------
  lampPost(c, ...OUTSIDE.lamp, 'cobblestone_wall')
  if (two) lampPost(c, ...OUTSIDE.yardLamp, 'cobblestone_wall')
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(','))
  const cut = process.env.CUT
  let canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  if (process.env.ABOVE) { // renders only: the build above the ground (the cellar hangs under a render's grass)
    const a = new Canvas(SIZES[level])
    for (const [k, v] of canvas.cells) { const [x, y, z] = k.split(',').map(Number); if (y >= BASEMENT) a.cells.set(`${x},${y - BASEMENT},${z}`, v) }
    canvas = a
  }
  const id = level === 1 ? 'smithy' : `smithy_${level}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
