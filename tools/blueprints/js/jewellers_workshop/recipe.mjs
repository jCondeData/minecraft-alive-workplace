// The Jeweler's Workshop (jewellers_workshop, _2, _3; ROADMAP 49/50.7), the recipe: assembles plan.mjs from the parts
// library (../parts) in passes (block-out and structure, roof and gables, openings, depth and detail, interior, the
// Strong Room, the Deep Vault) for LEVEL = 1, 2 or 3. Each level is the last one grown by the same lines: II adds the
// Strong Room behind the shop (and blocks up the two back windows it covers), III digs the Deep Vault under it and
// opens its hatch (nothing else changes above the ground). Writes furniture.json (the furniture spots) and, for III,
// out/cellar.txt. Build every level: build.sh. CUT=<y> cuts everything above y (the drawing's y); ABOVE=1 drops the
// basement (renders).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, LIGHTS } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, plinth, floor, wallPanel, window, door, gableRoof, crowGable, cellar, hatchLadder,
  furnitureParts, doubleChest, stair,
} from '../parts/index.mjs'
import { jwBarrelVault, jwButtress, jwButton, jwVitrine } from '../parts/jw_vault.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const hang = (c, [x, y, z]) => c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
const on = (level, thing) => (thing.level ?? 1) === -1 ? level === 1 : (thing.level ?? 1) <= level

export function draw(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level])
  const P = PALETTE
  const base = mix(P.plinth, 3), worn = mix(P.wallAlt, 5)
  const S = MASSES.shop, two = level >= 2, three = level >= 3
  const { x0, x1, z0, z1 } = S

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // the Shop: a deepslate plinth, dark oak boards inside, stone-brick side walls up to the eaves
  plinth(c, x0, z0, x1, z1, base)
  floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, P.floor)
  for (const x of [x0, x1]) wallPanel(c, [x, z0 + 1], [x, z1 - 1], 1, S.wallTop, P.wall)

  // --- Pass 2: roof and gables --------------------------------------------------------------------------------------
  // a 45-degree copper roof between the two gables, its eaves a block out over the side walls
  gableRoof(c, S.roof, P.roof)
  // the crow-stepped gables, street and back, with dark long-and-short quoins up their corners
  const quoin = (u, y) => (u === x0 || u === x1) ? ((y % 2) ? P.quoin : P.quoinAlt) : undefined
  for (const z of [z0, z1]) crowGable(c, 'z', z, [x0, x1], 1, MASSES.crow, P.wall, P.coping, quoin)
  // finials: a stone post on each gable's top step
  for (const z of [z0, z1]) c.set(5, MASSES.crow[5] + 1, z, P.finial)
  // the tie beam the stonecutter's lantern hangs from
  const T = MASSES.tieBeam
  for (let x = T.x0; x <= T.x1; x++) c.set(x, T.y, T.z, P.post, { axis: 'x' })

  // --- Pass 3: openings ---------------------------------------------------------------------------------------------
  const O = OPENINGS
  door(c, O.jewelerDoor.at, O.jewelerDoor.facing, O.jewelerDoor.out, { wood: P.wood, step: O.jewelerDoor.step, stepStair: 'deepslate_brick_stairs' })
  door(c, O.shopDoor.at, O.shopDoor.facing, O.shopDoor.out, { wood: 'iron', step: O.shopDoor.step, stepStair: 'deepslate_brick_stairs', hinge: 'right' })
  for (const [x, y, z, f] of O.shopDoor.buttons) jwButton(c, [x, y, z], f)
  if (!two) { // Level I's back plinth course (II builds the Strong Room there)
    const B = MASSES.backCourse
    for (let x = B.x0; x <= B.x1; x++) stair(c, x, 0, B.z, P.rim.stairs, 'north')
  }
  for (const w of O.windows) if (on(level, w)) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, sill: P.vault.stairs })
  // the Vitrine: a glass case out in the street, the amethyst on its plinth in the middle of it
  const V = MASSES.vitrine
  plinth(c, V.x0, V.z, V.x1, V.z, base)
  jwVitrine(c, { x0: V.x0, x1: V.x1, z: V.z }, { ...O.vitrine, gem: V.gem },
    { post: P.post, apron: `${P.wood}_planks`, glass: P.glass, plinth: P.plinthStone, block: 'amethyst_cluster', props: { facing: 'up', waterlogged: 'false' } })

  // --- Pass 4: depth and detail -------------------------------------------------------------------------------------
  // the pentice: a copper skirt along the whole shopfront over the doors and the case, a lantern under it at each door
  const PE = MASSES.pentice
  for (let x = PE.x0; x <= PE.x1; x++) stair(c, x, PE.y, PE.z, P.roof.stairs, 'south')
  // a moulded course across the street gable over the north light (upside-down stairs a block proud), its ends on the
  // quoins; the shop's sign above it, a purple banner high in the gable
  const MC = MASSES.mould
  for (let x = MC.x0; x <= MC.x1; x++) stair(c, x, MC.y, MC.z, P.vault.stairs, 'south', 'top')
  c.set(5, 9, 0, `${P.accent}_wall_banner`, { facing: 'north' })

  // --- Pass 5: interior (the bench, the counter) -------------------------------------------------------------------
  for (const f of FURNITURE) {
    if ((f.level ?? 1) > level) continue
    if (f.part === 'block') { c.free(f.pos); c.set(...f.pos, f.block, f.props ?? {}) }
    else if (f.part === 'doubleChest') doubleChest(c, f.pos, f.facing, f.along)
    else furnitureParts[f.part](c, f)
  }
  for (const l of LIGHTS) if ((l.level ?? 1) <= level) hang(c, l.at)

  // --- Pass 6: the Strong Room (Level II) ---------------------------------------------------------------------------
  if (two) {
    const R = MASSES.strongRoom
    plinth(c, R.x0, R.z0, R.x1, R.z1, base)
    floor(c, R.x0 + 1, R.z0, R.x1 - 1, R.z1 - 1, 0, P.vaultFloor)
    for (const x of [R.x0, R.x1]) wallPanel(c, [x, R.z0], [x, R.z1], 1, R.wallTop, worn)
    wallPanel(c, [R.x0 + 1, R.z1], [R.x1 - 1, R.z1], 1, R.wallTop, worn)
    // the barrel vault: stone, its rim a block proud at the back in the plinth's dark stone
    const under = jwBarrelVault(c, MASSES.vaultSection, R.z0, R.z1, P.vault)
    jwBarrelVault(c, MASSES.vaultSection, R.rimZ, R.rimZ, P.rim)
    c.set(...MASSES.keystone, P.keystone)
    for (let x = R.x0 + 1; x <= R.x1 - 1; x++) for (let y = R.wallTop + 1; y < under[x]; y++) c.set(x, y, R.z1, worn) // the round back's tympanum
    // buttresses down both sides
    // buttresses down both sides, each stepped twice: a deep foot to the wall's mid-height, a shaft to its top
    const pier = (x, y, zz) => (y === 0 ? base(x, y, zz) : worn(x, y, zz))
    for (const z of R.buttressZ) {
      jwButtress(c, R.x0 - 2, z, 0, R.footTop, pier, P.vault.stairs, 'east')
      jwButtress(c, R.x0 - 1, z, 0, R.wallTop, pier, P.vault.stairs, 'east')
      jwButtress(c, R.x1 + 2, z, 0, R.footTop, pier, P.vault.stairs, 'west')
      jwButtress(c, R.x1 + 1, z, 0, R.wallTop, pier, P.vault.stairs, 'west')
    }
    // the iron door in from the shop, the grille with its own iron door, bars to the outside
    door(c, O.vaultDoor.at, O.vaultDoor.facing, 'south', { wood: 'iron' })
    for (const [x, y, z, f] of O.vaultDoor.buttons) jwButton(c, [x, y, z], f)
    for (const [x, y] of O.grille.bars) c.set(x, y, O.grille.z, P.bars)
    door(c, O.cageDoor.at, O.cageDoor.facing, 'south', { wood: 'iron' })
    for (const at of O.cageDoor.floorButtons) jwButton(c, at, null)
    for (const [x, y, z] of [...O.slits, ...O.lunette]) c.set(x, y, z, P.bars)
  }

  // --- Pass 7: the Deep Vault (Level III) ----------------------------------------------------------------------------
  let dug = null
  if (three) {
    const D = MASSES.deepVault
    dug = cellar(c, D.x0, D.z0, D.x1, D.z1, D.depth, mix(P.cellarWall, 7), P.cellarFloor)
    const h = O.hatch
    hatchLadder(c, h.x, h.z, -D.depth + 1, 0, h.facing, P.wood)
  }
  // (the Deep Vault's chests and lantern are drawn with the furniture and lights above, inside the cellar's walls)
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  if (dug) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(','))
  const cut = process.env.CUT
  let canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const b = BASEMENT[level]
  if (process.env.ABOVE && b) { // renders only: the build above the ground
    const a = new Canvas(SIZES[level])
    for (const [k, v] of canvas.cells) { const [x, y, z] = k.split(',').map(Number); if (y >= b) a.cells.set(`${x},${y - b},${z}`, v) }
    canvas = a
  }
  const id = level === 1 ? 'jewellers_workshop' : `jewellers_workshop_${level}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
