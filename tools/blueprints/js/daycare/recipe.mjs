// The Daycare (the Clutch), the recipe: assembles plan.mjs's level (LEVEL=1|2) from the parts library (../parts) in
// passes (block-out and structure, openings, depth and detail, interior, ground, light) and returns the structure
// for the minecraft-builder skill's runner. Level II is Level I drawn again with the Little Egg added to the nest.
// Also writes furniture.json (every furniture spot the parts recorded) for rebuild.py.
// Build: ./build.sh (both levels as candidates); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, PALETTE, EGGS, NEST, PORCHES, PASTURES, FURNITURE, GROUND } from './plan.mjs'
import {
  Canvas, mix, post, beamRing, window, door, gableRoof, gableWall, bargeTrim, flowerBed, lampPost, benchRun,
  furnitureParts, eggSolid, eggShell, spotted, shellLip, eggWindow, nest, eggDoorway,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

const inBox = (b, x, z) => x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3]

export function draw(level = 1) {
  const P = PALETTE
  const c = new Canvas(SIZES[level])
  const eggKeys = level >= 2 ? ['big', 'little'] : ['big']
  const porchKeys = level >= 2 ? ['north', 'east'] : ['north']
  const eggs = Object.fromEntries(eggKeys.map(k => [k, eggSolid(EGGS[k])]))
  const paths = [...GROUND.paths[1], ...(level >= 2 ? GROUND.paths[2] : [])] // Level II adds none
  const onPath = (x, z) => paths.some(p => inBox(p, x, z))
  const porchBox = k => { const p = PORCHES[k]; const r = p.roof; return r.axis === 'z' ? [r.across[0], r.along[0], r.across[1], r.along[1]] : [r.along[0], r.across[0], r.along[1], r.across[1]] }
  const inPorch = (x, z) => porchKeys.some(k => inBox(porchBox(k), x, z))
  const pastureAt = PASTURES.filter(p => p.level <= level)

  // --- Pass 1: block-out and structure: the eggs' shells and floors, the nest round their feet -----------------------
  const shells = {}
  for (const k of eggKeys) {
    const shell = spotted(P.shell, P.spots[k], { size: 2, share: EGGS[k].spotShare, seed: k === 'big' ? 7 : 3, below: eggs[k].top - 1 })
    // the floor: boards, a ring of straw round the Pasture Block
    const pa = PASTURES.find(p => p.level === (k === 'big' ? 1 : 2)).at
    const floorId = (x, z) => (Math.abs(x - pa[0]) <= 1 && Math.abs(z - pa[2]) <= 1 ? [P.straw, { axis: 'y' }] : P.floor)
    shells[k] = eggShell(c, eggs[k], shell, floorId)
  }
  // the nest: laid logs on the outer rim of every course (with a wisp of straw here and there), straw packed inside
  const logs = mix(P.nestLogs, 11)
  const wisp = mix([['straw', 1], ['log', 4]], 5)
  nest(c, Object.values(eggs), NEST.reach, (x, y, z, axis, rim) => {
    if (!rim || wisp(x, y, z) === 'straw') return [P.straw, { axis: rim ? axis : 'y' }]
    return [logs(x, y, z), { axis }]
  }, (x, y, z) => !onPath(x, z) && !inPorch(x, z))

  // the porches: dark posts proud at the front corners, plank side walls back to the shell, a beam, a dark gable roof
  const roofs = {}
  for (const k of porchKeys) {
    const p = PORCHES[k]
    const [fx0, fz0, fx1, fz1] = p.floor
    c.fill(fx0, 0, fz0, fx1, 0, fz1, P.floor)
    for (const [x, z] of p.posts) post(c, x, z, 0, 3, P.frame)
    if (p.roof.axis === 'z') {
      for (const [x, z0, z1] of p.sides) for (let z = z0; z <= z1; z++) for (let y = 0; y <= 3; y++) c.set(x, y, z, y === 0 ? P.frame : P.infill, y === 0 ? { axis: 'z' } : {})
      for (let x = p.front.x0; x <= p.front.x1; x++) for (let y = 1; y <= 3; y++) c.set(x, y, p.front.z, P.infill)
      beamRing(c, p.posts[0][0], p.posts[0][1], p.posts[1][0], p.sides[0][2], p.roof.eaveY, P.frame)
    } else {
      for (const [z, x0, x1] of p.sides) for (let x = x0; x <= x1; x++) for (let y = 0; y <= 3; y++) c.set(x, y, z, y === 0 ? P.frame : P.infill, y === 0 ? { axis: 'x' } : {})
      for (let z = p.front.z0; z <= p.front.z1; z++) for (let y = 1; y <= 3; y++) c.set(p.front.x, y, z, P.infill)
      beamRing(c, p.sides[0][1], p.posts[0][1], p.posts[0][0], p.posts[1][1], p.roof.eaveY, P.frame)
    }
    const inEgg = (x, y, z) => Object.values(eggs).some(e => e.inside(x, y, z))
    roofs[k] = gableRoof(c, p.roof, P.roof, (x, y, z) => !inEgg(x, y, z))
    // the gable over the door: planks, a king post, a round window
    const gLine = p.roof.axis === 'z' ? p.front.z : p.front.x
    gableWall(c, p.roof.axis, gLine, [p.roof.across[0] + 1, p.roof.across[1] - 1], p.roof.eaveY + 1, roofs[k].under, () => P.infill)
    // the porch's little attic packed solid behind the gable, so no pocket is left between roof, beam and shell
    const r = p.roof
    for (let u = r.across[0] + 1; u <= r.across[1] - 1; u++) for (let v = r.along[0]; v <= r.along[1]; v++)
      for (let y = r.eaveY + 1; y < roofs[k].under[u]; y++) {
        const [x, z] = r.axis === 'z' ? [u, v] : [v, u]
        const behind = r.axis === 'z' ? v > gLine : v < gLine
        if (behind && !c.get(x, y, z) && !inEgg(x, y, z)) c.set(x, y, z, P.infill)
      }
  }

  // --- Pass 2: openings ---------------------------------------------------------------------------------------------
  for (const k of porchKeys) {
    const p = PORCHES[k]
    const egg = k === 'north' ? 'big' : 'little'
    eggDoorway(c, shells[egg].shellCells, p.doorway, P.floor)
    door(c, p.door, p.facing, p.out, { wood: P.wood, step: p.step, stepStair: P.step })
    for (const w of p.windows) window(c, w, p.out === 'north' ? (w[0][0] < p.door[0] ? 'west' : 'east') : (w[0][2] < p.door[2] ? 'north' : 'south'), null, { glass: P.glass })
    c.set(...p.porthole, P.glass)
  }
  // windows set a block into each egg's skin on its flat quarters: dark jambs, head and sill, the pane in a reveal
  for (const k of eggKeys) for (const side of EGGS[k].windows)
    eggWindow(c, eggs[k], side, EGGS[k].winY, 2, { glass: P.glass, frame: P.frame, reveal: P.roof.full, sill: P.sill })

  // --- Pass 3: depth and detail -------------------------------------------------------------------------------------
  // the cap's broken lip standing out a block round each egg where the crown meets the body, a zigzag of light and shadow
  for (const k of eggKeys) {
    const e = EGGS[k]
    const skips = porchKeys.map(pk => PORCHES[pk].crackSkip)
    shellLip(c, eggs[k], shells[k].shellCells, e.lipY, {
      lip: P.shell, teeth: e.teeth, phase: e.phase,
      skip: (x, y, z) => skips.some(s => x >= s.x0 && x <= s.x1 && z >= s.z0 && z <= s.z1 && y <= s.y1),
    })
  }
  for (const k of porchKeys) {
    const p = PORCHES[k]
    const r = p.roof
    bargeTrim(c, r.axis, p.out === 'north' ? r.along[0] : r.along[1], r.across, roofs[k].under, `${P.trim}_stairs`) // under the front rake
  }

  // --- Pass 4: interior: the Pasture Blocks first, then the plan's furniture ------------------------------------------
  for (const p of pastureAt) {
    const [x, y, z] = p.at
    c.set(x, y, z, 'cobblemon:pasture', { facing: p.facing, part: 'bottom', on: 'false', waterlogged: 'false' })
    c.set(x, y + 1, z, 'cobblemon:pasture', { facing: p.facing, part: 'top', on: 'false', waterlogged: 'false' })
  }
  for (const f of FURNITURE.filter(f => f.level <= level)) {
    furnitureParts[f.part](c, f)
  }

  // --- Pass 5: ground -------------------------------------------------------------------------------------------------
  for (const [x0, z0, x1, z1] of paths) for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, P.path)
  for (const [x, y, z] of GROUND.stepSides[1]) c.set(x, y, z, P.path)
  for (let l = 1; l <= level; l++) for (const b of GROUND.beds[l] ?? []) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  if (level >= 2) {
    const b = GROUND.bench[2]
    benchRun(c, { x0: b.x0, x1: b.x1, y: 0, z: b.z, facing: b.facing }, { seat: `${P.wood}_stairs`, back: P.wood, ends: P.frame, endCap: `${P.trim}_slab` })
  }

  // --- Pass 6: light --------------------------------------------------------------------------------------------------
  for (let l = 1; l <= level; l++) for (const [x, y, z] of GROUND.lamps[l] ?? []) lampPost(c, x, y, z, P.lampPost, 2)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const id = level === 1 ? 'daycare' : `daycare_${level}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
