// The Graveyard (graveyard, graveyard_2), the recipe: assembles plan.mjs from the shared parts library (../parts,
// ROADMAP 49) in passes (block-out and structure, openings, depth and detail, interior, ground, light) and returns
// the structure for the minecraft-builder skill's runner. LEVEL=1 (default) draws `graveyard`; LEVEL=2 draws
// `graveyard_2`, the same drawing with Level II's additions over it (every Level I block and every grave slot kept).
// Also writes furniture.json (every furniture spot the parts recorded) for tools/blueprints/rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, SLOTS, LEVEL2 } from './plan.mjs'
import {
  Canvas, mix, slab, stair, wallPanel, window, door, doorway, gableRoof, gableWall, bargeTrim, furnitureParts,
  lampPost, benchRun, buttress, yardWall, lychgate, parapetGable, lanternTower, pewRun, columnarYew,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level = 1) {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const rubble = mix(P.rubble, 51)
  const dressed = mix(P.dressed, 52)
  const foot = mix(P.dressedFoot, 53)
  const flags = mix(P.path, 54)
  const W = MASSES.wall, L = MASSES.lychgate, T = MASSES.tower, C = MASSES.chapel
  const cw = C.walls

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // The yard wall: rubble coped with slabs, dressed piers every four; the lychgate's piers carry its posts
  yardWall(c, { runs: W.runs, piers: W.piers }, { rubble, coping: P.coping, pier: P.pier, pierCap: P.coping })
  for (const [x, z] of W.gatePiers) c.fill(x, 0, z, x, 1, z, P.pier)

  // The Lantern of the Dead: a solid dressed shaft (its damp foot mossier), the lantern chamber open on three sides
  const tower = (x, y, z) => (y <= 2 ? foot(x, y, z) : dressed(x, y, z))
  const towerTop = lanternTower(c, T, { shaft: tower, pier: P.pier, cap: P.roof, crown: P.roof.full, finial: P.finial })

  // The chapel: rubble walls on a rubble foot, dressed quoins and a dressed wall plate at the eaves
  const chapelWall = (x, y, z) => {
    const corner = (x === cw.x0 || x === cw.x1) && (z === cw.z0 || z === cw.z1)
    if (corner) return dressed(x, y, z)
    return y === C.wallTop ? dressed(x, y, z) : rubble(x, y, z)
  }
  const inTower = (x, z) => x >= T.x0 && x <= T.x0 + 2 && z >= T.z0 && z <= T.z0 + 2
  for (let y = 0; y <= C.wallTop; y++) {
    for (let z = cw.z0; z < cw.z1; z++) { c.set(cw.x0, y, z, chapelWall); c.set(cw.x1, y, z, chapelWall) }
    for (let x = cw.x0; x <= cw.x1; x++) if (!inTower(x, cw.z0)) c.set(x, y, cw.z0, chapelWall)
  }
  for (let x = C.room.x0; x <= C.room.x1; x++) for (let z = C.room.z0; z <= C.room.z1; z++) c.set(x, 0, z, P.floor)

  // The roof: steep slate, a block of eave each side, overhanging the front gable beside the tower; the tower owns its cells
  const R = C.roof
  const main = gableRoof(c, R, P.roof, (x, y, z) => !inTower(x, z))
  // the front gable (z 8) under the roof beside the tower, rubble with the wall plate's dressing at the corners
  gableWall(c, 'z', cw.z0, [cw.x0, cw.x1], C.wallTop + 1, main.under, u => (u >= T.x0 && u <= T.x0 + 2 ? null : rubble))
  // the back gable: a stone parapet up past the roof, coped with stairs, a tall lancet over the altar
  const tops = {}
  for (let u = cw.x0; u <= cw.x1; u++) tops[u] = main.under[u] === main.ridgeY ? main.ridgeY : main.under[u] + 1
  parapetGable(c, 'z', cw.z1, [cw.x0, cw.x1], 0, tops, chapelWall, P.copingStairs, { apex: P.keystone, finial: P.finial })
  // the tie beam across the room at the wall plate, the lamp hangs from it
  for (let x = C.room.x0; x <= C.room.x1; x++) c.set(x, C.tieBeam.y, C.tieBeam.z, P.timber, { axis: 'x' })

  // The lychgate: dark oak posts on the piers, braced beams, a slate gable, spruce gables round a king post
  lychgate(c, L, { post: P.timber, beam: P.timber, brace: `${P.wood}_stairs`, roof: P.roof, infill: null, frontBraces: false, lamps: [[L.x0 - 1, L.z0], [L.x1 + 1, L.z0]] })

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  doorway(c, OPENINGS.arch.at, OPENINGS.arch.height)
  const d = OPENINGS.door
  door(c, d.at, d.facing, d.out, { wood: P.wood })
  doorway(c, OPENINGS.chapelArch.at, OPENINGS.chapelArch.height)
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, null, { glass: P.glass })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // the arch: a keystone over it and a drip hood of upside-down stairs; a candle in a niche above, for the dead
  const [ax, , az] = OPENINGS.arch.at
  c.set(ax, 3, az, P.keystone)
  for (let x = ax - 1; x <= ax + 1; x++) stair(c, x, 3, az - 1, P.copingStairs, 'south', 'top')
  c.clear(ax, 5, az)
  c.set(ax, 5, az, 'candle', { candles: '1', lit: 'true', waterlogged: 'false' })
  stair(c, ax, 6, az - 1, P.copingStairs, 'south', 'top') // the niche's little hood
  // a dressed string course round the tower under the lantern chamber, a sill for its openings
  for (let x = T.x0; x <= T.x0 + 2; x++) for (let z = T.z0; z <= T.z0 + 2; z++) {
    if (x === T.x0 + 1 && z === T.z0 + 1) continue
    c.set(x, T.shaftTop, z, P.pier)
  }
  // the windows' heads: a dressed block over each side lancet, chiseled over the altar's
  c.set(3, 4, 10, P.pier); c.set(9, 4, 10, P.pier)
  c.set(6, 8, 12, P.keystone)
  // buttresses either side of each side lancet, weathered with a stair under the eave
  for (const [x, z] of C.buttresses) buttress(c, x, z, 0, 2, rubble, P.copingStairs, x < 6 ? 'east' : 'west')
  // barge boards under the front overhang beside the tower
  bargeTrim(c, 'z', R.along[0], R.across, main.under, `${P.wood}_slab`)
  // kneelers: a dressed stone stair at each foot of the back parapet, carrying the eave's end
  stair(c, 2, 4, 12, P.copingStairs, 'east'); stair(c, 10, 4, 12, P.copingStairs, 'west')

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  const parts = {
    ...furnitureParts,
    jobBlock(c, f) { const [x, y, z] = f.pos; c.free(f.pos); c.set(x, y, z, f.block, { facing: f.facing }) },
    pedestal(c, f) { // a dressed stone pedestal with a cluster of lit candles on it
      const [x, y, z] = f.pos
      c.free([x, y, z], [x, y + 1, z])
      c.set(x, y, z, P.keystone)
      c.set(x, y + 1, z, 'candle', { candles: String(f.candles), lit: 'true', waterlogged: 'false' })
    },
    bier(c, f) { const [x, y, z] = f.pos; c.free(f.pos); slab(c, x, y, z, `${P.wood}_slab`, 'top') },
    pews(c, f) { pewRun(c, f.cells, f.facing, f.wood) },
  }
  for (const f of FURNITURE) parts[f.part](c, f)

  // --- Pass 5: ground ---------------------------------------------------------------------------------------------
  // the path: flagstone slabs from the street to the tower's arch, under the lychgate
  for (let x = 5; x <= 7; x++) for (let z = 0; z <= 5; z++) if (!c.get(x, 0, z)) slab(c, x, 0, z, flags)
  // the yews: young in Level I, grown in Level II
  for (const y of MASSES.yews) {
    const grown = level >= 2
    columnarYew(c, { ...y, trunkTop: grown ? 3 : 2, leafY0: 2, top: grown ? 9 : 6 }, P.yewLog, P.yewLeaf)
  }

  // --- Level II: the same yard a generation on ----------------------------------------------------------------------
  if (level >= 2) {
    const L2 = LEVEL2
    for (const [x, z] of L2.pierLamps) {
      if (W.gatePiers.some(([gx, gz]) => gx === x && gz === z)) continue
      c.set(x, 2, z, P.pier) // the pier rises a block under its lantern (a slab's top can't hold one)
      c.set(x, 3, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
    }
    for (const [[ax, az], [bx]] of L2.railings) for (let x = ax; x <= bx; x++) { c.set(x, 1, az, rubble); c.set(x, 2, az, 'iron_bars') }
    for (const [x, z] of L2.pierPots) c.set(x, 3, z, 'potted_white_tulip')
    for (const b of L2.benches) benchRun(c, { x0: b.x0, x1: b.x1, y: 0, z: b.z, facing: 'north' }, { seat: `${P.wood}_stairs`, spot: true, wood: P.wood })
    for (const [x, y, z] of L2.pathLamps) { c.clear(x, y, z); lampPost(c, x, y, z, `${P.wood}_fence`, 2) }
    for (const X of L2.crosses) { // a stone cross: a chiseled foot, a shaft of wall posts, the arms at the top
      c.set(X.x, 0, X.z, P.keystone)
      for (let y = 1; y <= X.shaftTop + 1; y++) c.set(X.x, y, X.z, P.crossShaft)
      for (const dx of [-1, 1]) c.set(X.x + dx, X.shaftTop, X.z, P.crossShaft)
    }
  }

  // The grave slots: nothing may stand at y 0 or y 1 in any of them (Graveyards.free)
  for (const [x, , z] of SLOTS) for (const y of [0, 1]) if (c.get(x, y, z)) throw new Error(`slot ${x},${z} holds ${c.name(x, y, z)} at y ${y}`)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'graveyard' : `graveyard_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
