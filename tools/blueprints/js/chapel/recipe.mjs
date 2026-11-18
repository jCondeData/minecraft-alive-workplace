// Chapel (the Blossom Court, ROADMAP 49), the recipe: assembles plan.mjs from the shared parts library (../parts) in
// passes (block-out and structure, roofs and gables, openings, depth and detail, interior, ground and light) and
// returns the structure for the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the
// parts recorded) for tools/blueprints/rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, MIDDLE, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, floor, stair, door, quoins, flaredGableRoof, needleSpire, corbelRing, roundArch,
  compassRose, clip, tree, flowerBed, lampPost, furnitureParts, louvres, pew, choirStall,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, D = P.dress
  const stone = mix(P.plinth, 11)
  const T = MASSES.tower, N = MASSES.nave, R = N.room
  const [mx, , mz] = MIDDLE
  const inTower = (x, z) => x >= T.x0 && x <= T.x1 && z >= T.z0 && z <= T.z1

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // Floors: the nave's stone-brick field with an andesite aisle and the inlaid rose at the middle (where the couple
  // stand and the Seer arrives), the vestibule's flags, the sanctuary's andesite
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  floor(c, 5, R.z0, 7, 10, 0, P.aisle)
  compassRose(c, mx, 0, mz, { ...P.rose, reach: 2 })
  floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, 0, P.floor)
  floor(c, R.x0, OPENINGS.chancelArch.z, R.x1, R.z1, 0, P.sanctuaryFloor)

  // The gate tower: a plinth ring, limewashed walls to the wall head, dressed quoins at the corners
  for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
    if (x !== T.x0 && x !== T.x1 && z !== T.z0 && z !== T.z1) continue
    c.set(x, 0, z, stone)
    for (let y = 1; y <= T.top; y++) c.set(x, y, z, P.wall)
  }
  quoins(c, [[T.x0, T.z0], [T.x1, T.z0], [T.x0, T.z1], [T.x1, T.z1]], 1, T.top, D.full, D.alt)
  floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, T.ceiling, `${P.wood}_planks`) // the vestibule's ceiling, the belfry's floor

  // The nave: limewashed side walls on a plinth course, to the eaves (the roof's kick sits beside the wall head)
  for (const x of [N.x0, N.x1]) for (let z = N.z0; z <= N.z1; z++) {
    c.set(x, 0, z, stone)
    for (let y = 1; y <= N.wallTop; y++) c.set(x, y, z, P.wall)
  }

  // --- Pass 2: roofs and gables ----------------------------------------------------------------------------------
  // The spire: a steep slate broach spire straight off the tower's wall head, to a point and a copper rod
  needleSpire(c, T.spire, P.roof, P.finial)
  // The nave roof: ONE bell-cast roof (45-degree body, a kick stair a block out and down at the eaves) as a single
  // ridge from the tower's back wall to one block past the back gable; the tower keeps its own cells on row z 5
  const roof = flaredGableRoof(c, N.roof, P.roof, P.roof.stairs, (x, y, z) => !inTower(x, z))
  // Gables under the roof's end rows: the front stubs beside the tower (z 5) and the back gable (z 15), limewash on a
  // plinth, with the rose's cross of glass (its corners dressed stone) in the back one
  const rose = OPENINGS.rose
  for (const z of [N.z0, N.z1]) for (let x = N.x0; x <= N.x1; x++) {
    if (inTower(x, z)) continue
    c.set(x, 0, z, stone)
    for (let y = 1; y < roof.under[x]; y++) {
      const du = x - rose.cx, dy = y - rose.cy
      if (z === rose.z && Math.abs(du) <= 1 && Math.abs(dy) <= 1)
        c.set(x, y, z, du === 0 && dy === 0 ? P.roseGlass.heart : du === 0 || dy === 0 ? P.roseGlass.arm : D.full)
      else c.set(x, y, z, P.wall)
    }
  }
  // dressed pilasters up the wall line at the front corners, the chancel line and the back corners (long and short
  // stones), and the gable corners quoined the same way
  quoins(c, N.pilasters.flatMap(z => [[N.x0, z], [N.x1, z]]), 1, N.wallTop, D.full, D.alt)

  // --- Pass 3: openings ------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  for (const y of [1, 2]) { c.set(fd.at[0] - 1, y, fd.at[2], D.full); c.set(fd.at[0] + 1, y, fd.at[2], D.full) } // jambs
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: D.stairs })
  stair(c, fd.at[0] - 1, 3, fd.at[2], D.stairs, 'east', 'top') // the door's round head: shoulders and a keystone
  stair(c, fd.at[0] + 1, 3, fd.at[2], D.stairs, 'west', 'top')
  c.set(fd.at[0], 3, fd.at[2], D.key)
  // the inner door from the vestibule into the nave, dressed the same way on its nave face
  const td = OPENINGS.towerDoor
  door(c, td.at, td.facing, 'south', { wood: P.wood })
  stair(c, td.at[0] - 1, 3, td.at[2], D.stairs, 'east', 'top'); stair(c, td.at[0] + 1, 3, td.at[2], D.stairs, 'west', 'top')
  c.set(td.at[0], 3, td.at[2], D.key)
  // windows: the tower's slit lights, the front gable stubs' lights, the nave's and sanctuary's side lights
  for (const w of OPENINGS.windows) for (const [x, y, z] of w.cells) c.set(x, y, z, P.glass)
  // the belfry: louvres in twin openings on the front and side faces
  const L = OPENINGS.louvres, S = T.spire
  for (let y = L.y0; y <= L.y1; y++) for (const i of L.offsets) {
    if (L.faces.includes('north')) louvres(c, [[S.cx + i, y, T.z0]], `${P.wood}_trapdoor`, 'north')
    if (L.faces.includes('south')) louvres(c, [[S.cx + i, y, T.z1]], `${P.wood}_trapdoor`, 'south')
    if (L.faces.includes('west')) louvres(c, [[T.x0, y, S.cz + i]], `${P.wood}_trapdoor`, 'west')
    if (L.faces.includes('east')) louvres(c, [[T.x1, y, S.cz + i]], `${P.wood}_trapdoor`, 'east')
  }

  // --- Pass 4: depth and detail ----------------------------------------------------------------------------------
  // a string course on the tower's front and sides under the belfry, stopping a block short of the nave roof's front
  // row (the back run would meet the roof)
  corbelRing(c, T.x0 - 1, T.z0 - 1, T.x1 + 1, T.z1 + 1, T.course, D.stairs, (x, z) => z >= T.z1 - 1)
  // the belfry's lantern on a chain from the needle
  for (let y = L.y1 + 1; y <= S.heights[1]; y++) c.set(S.cx, y, S.cz, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(S.cx, L.y1, S.cz, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // the rose set one block deep: a dressed frame standing proud of the back gable under the roof's overhang (sill,
  // jambs, a keyed head; its corners open so it reads round), and a plinth course along the gable's foot
  const fz = rose.frameZ
  for (let x = rose.cx - 1; x <= rose.cx + 1; x++) { c.set(x, rose.cy - 2, fz, D.full); c.set(x, rose.cy + 2, fz, x === rose.cx ? D.key : D.full) }
  for (let y = rose.cy + 3; y < roof.under[rose.cx]; y++) c.set(rose.cx, y, fz, D.full) // the head tied up to the ridge
  for (let y = rose.cy - 1; y <= rose.cy + 1; y++) { c.set(rose.cx - 2, y, fz, D.full); c.set(rose.cx + 2, y, fz, D.full) }
  for (let x = N.x0; x <= N.x1; x++) c.set(x, 0, fz, stone)
  // the chancel arch at z 11: a thin segmental arch of dressed stone across the nave from the wall pilasters, the
  // whole width open under it to y 3
  const cz = OPENINGS.chancelArch.z
  for (const [s, x0] of [[-1, R.x0], [1, R.x1]]) {
    const inward = s < 0 ? 'west' : 'east' // an upside-down stair's low half toward the pier: the arch's curve
    c.set(x0, 4, cz, D.full); stair(c, x0 - s, 4, cz, D.stairs, inward, 'top')
    c.set(x0, 5, cz, D.full); c.set(x0 - s, 5, cz, D.full); stair(c, x0 - 2 * s, 5, cz, D.stairs, inward, 'top')
    c.set(x0 - 3 * s, 5, cz, D.full)
  }
  c.set(mx, 5, cz, D.key)
  // the vow arch over the rose: a round stone arch across the aisle, wreathed in flowering azalea, a lantern under it
  roundArch(c, { cx: mx, z: mz, y0: 1, spring: 3, r: 2 }, { full: D.full, stairs: D.stairs, key: D.key })
  clip(c, [[mx - 2, 5, mz], [mx - 1, 6, mz], [mx, 6, mz], [mx + 1, 6, mz], [mx + 2, 5, mz], [mx, 7, mz]], mix(P.wreath, 5))
  c.set(mx, 4, mz, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // --- Pass 5: interior (the plan's furniture) ---------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (f.part === 'pew') pew(c, { x0: f.x0, x1: f.x1, y: 1, z: f.z, facing: f.facing }, P.wood)
    else if (f.part === 'stall') choirStall(c, f.cells, f.facing, P.wood)
    else if (f.part === 'pot') { c.free(f.pos); c.set(...f.pos, f.block) }
    else if (f.part === 'altar') {
      const d = f.dais
      for (let x = d.x0; x <= d.x1; x++) { c.set(x, 1, d.z, D.full); stair(c, x, 1, d.stepZ, D.stairs, 'south') }
      const [a, b, e] = f.table
      c.set(...a, D.alt); c.set(...b, D.key); c.set(...e, D.alt)
      for (const p of [a, e]) c.set(p[0], p[1] + 1, p[2], 'white_candle', { candles: '3', lit: 'true', waterlogged: 'false' })
      c.set(b[0], b[1] + 1, b[2], 'potted_lily_of_the_valley')
    } else furnitureParts[f.part](c, f)
  }

  // --- Pass 6: ground ------------------------------------------------------------------------------------------------
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  for (const [x, y, z] of OUTSIDE.lampPosts) lampPost(c, x, y, z, `${P.wood}_fence`, 2)
  // a cherry tree standing free on the lawn either side of the tower: a trunk from the ground, a round crown
  // (a ring at y 3, a full 3 x 3 at y 4, a cross at y 5), a block clear of the tower's face
  const blossom = mix(P.blossom, 7)
  for (const { trunk: [tx, tz] } of OUTSIDE.trees) {
    const logs = [0, 1, 2, 3].map(y => [tx, y, tz])
    const crown = []
    for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
      if (tx + dx < 0 || tx + dx >= SIZE[0]) continue
      if (dx === 0 || dz === 0) crown.push([tx + dx, 3, tz + dz], [tx + dx, 5, tz + dz])
      crown.push([tx + dx, 4, tz + dz])
    }
    crown.push([tx, 6, tz])
    tree(c, logs, crown, P.trunk, blossom)
  }
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `chapel_cut${cut}` : 'chapel' } }
}
