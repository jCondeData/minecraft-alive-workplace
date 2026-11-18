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
  Canvas, mix, plinth, floor, stair, slab, door, quoins, lancet, flaredGableRoof, needleSpire, corbelRing, roundArch,
  buttress, compassRose, hood, clip, tree, canopy, flowerBed, lampPost, furnitureParts, copedGable, courtWall, louvres, pew,
  choirStall,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, D = P.dress
  const stone = mix(P.plinth, 11)
  const T = MASSES.tower, W = MASSES.wall, C = MASSES.court, S = MASSES.sanctuary
  const [mx, , mz] = MIDDLE

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // Floors: the court's stone-brick field with an andesite aisle and the inlaid rose at the middle (where the couple
  // stand and the Seer arrives), the vestibule's flags, the sanctuary's andesite, soil in the corner gardens
  floor(c, C.x0, C.z0, C.x1, C.z1, 0, P.floor)
  floor(c, 5, C.z0, 7, C.z1, 0, P.aisle)
  compassRose(c, mx, 0, mz, { ...P.rose, reach: 2 })
  floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, 0, P.floor)
  floor(c, S.room.x0, S.room.z0, S.room.x1, S.room.z1, 0, P.sanctuaryFloor)
  for (const g of MASSES.gardens) for (let x = g.x0; x <= g.x1; x++) for (let z = g.z0; z <= g.z1; z++) c.set(x, 0, z, P.soil, { snowy: 'false' })

  // The court wall: limewash on a plinth course, coped, piers at the corners and bay lines, a grille in each court bay
  const grille = (u, y) => W.grilles.includes(u) && y >= 2
  for (const x of [W.west, W.east])
    courtWall(c, 'z', x, W.z0, W.z1, { top: W.top }, { plinth: stone, wall: P.wall, coping: D.slab, pier: D.full, piers: W.piers, skip: grille })
  courtWall(c, 'x', W.z0, W.west, T.x0 - 1, { top: W.top }, { plinth: stone, wall: P.wall, coping: D.slab, pier: D.full, piers: [W.west] })
  courtWall(c, 'x', W.z0, T.x1 + 1, W.east, { top: W.top }, { plinth: stone, wall: P.wall, coping: D.slab, pier: D.full, piers: [W.east] })
  for (const x of [W.west, W.east]) for (const z of W.grilles) for (let y = 2; y <= W.top; y++) c.set(x, y, z, P.bars)
  // stepped buttresses outside the bay piers, weathered toward the wall
  for (const z of W.buttresses) {
    buttress(c, W.west - 1, z, 0, 2, D.full, D.stairs, 'east')
    buttress(c, W.east + 1, z, 0, 2, D.full, D.stairs, 'west')
  }

  // The gate tower: a plinth ring, limewashed walls to y 10, dressed quoins at the corners
  for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
    if (x !== T.x0 && x !== T.x1 && z !== T.z0 && z !== T.z1) continue
    c.set(x, 0, z, stone)
    for (let y = 1; y <= T.top; y++) c.set(x, y, z, P.wall)
  }
  quoins(c, [[T.x0, T.z0], [T.x1, T.z0], [T.x0, T.z1], [T.x1, T.z1]], 1, T.top, D.full, D.alt)
  floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, T.ceiling, `${P.wood}_planks`) // the vestibule's ceiling, the belfry's floor

  // The sanctuary: low side walls on a plinth (the steep roof comes down to them), dressed corners
  for (const x of [S.walls.x0, S.walls.x1]) for (let z = S.room.z0; z <= S.room.z1; z++) {
    c.set(x, 0, z, stone)
    for (let y = 1; y <= S.wallTop; y++) c.set(x, y, z, P.wall)
  }

  // --- Pass 2: roofs and gables ----------------------------------------------------------------------------------
  // The spire: a steep slate broach spire straight off the wall head (two up for one in), to a point and a copper rod
  needleSpire(c, T.spire, P.roof, P.finial)
  // The sanctuary roof: 45 degrees, bell-cast at the eaves (a kick stair a block out and down), between the two gables
  const R = flaredGableRoof(c, S.roof, P.roof, P.roof.stairs)
  // Coped gables: the stone carries the roof's profile past the slates at both ends; the corner columns and the kneelers
  // under the kicks are dressed stone, the faces limewash
  const toStone = id => (id === P.roof.full ? P.wall : id === P.roof.stairs ? D.stairs : id) // a stone edge on a limewashed face
  // the kneelers (under the kicks) dressed stone, the wall corners quoined (stone on alternate courses)
  const corner = (u, y) => u < S.walls.x0 || u > S.walls.x1 || ((u === S.walls.x0 || u === S.walls.x1) && y % 2 === 1)
  const rose = OPENINGS.rose
  copedGable(c, 'z', S.walls.z0, S.roof.along[0], S.roof.across, R.under, 0, P.wall, toStone,
    (u, y) => (y === 0 ? stone : corner(u, y) ? D.full : undefined))
  copedGable(c, 'z', S.walls.z1, S.roof.along[1], S.roof.across, R.under, 0, P.wall, toStone, (u, y) => {
    if (y === 0) return stone
    if (corner(u, y)) return D.full
    const du = u - rose.cx, dy = y - rose.cy
    if (Math.abs(du) <= 1 && Math.abs(dy) <= 1) {
      if (du === 0 && dy === 0) return P.roseGlass.heart
      if (du === 0 || dy === 0) return P.roseGlass.arm
      return D.full // the rose's corners: a dressed ring round the cross of glass
    }
    return undefined
  })

  // --- Pass 3: openings ------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  for (const y of [1, 2]) { c.set(fd.at[0] - 1, y, fd.at[2], D.full); c.set(fd.at[0] + 1, y, fd.at[2], D.full) } // jambs
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: D.stairs })
  stair(c, fd.at[0] - 1, 3, fd.at[2], D.stairs, 'east', 'top') // the door's round head: shoulders and a keystone
  stair(c, fd.at[0] + 1, 3, fd.at[2], D.stairs, 'west', 'top')
  c.set(fd.at[0], 3, fd.at[2], D.key)
  // the inner door from the vestibule into the court, dressed the same way (a porch with a door at each end)
  const td = OPENINGS.towerDoor
  for (const y of [1, 2]) { c.set(td.at[0] - 1, y, td.at[2], D.full); c.set(td.at[0] + 1, y, td.at[2], D.full) }
  door(c, td.at, td.facing, 'south', { wood: P.wood })
  stair(c, td.at[0] - 1, 3, td.at[2], D.stairs, 'east', 'top'); stair(c, td.at[0] + 1, 3, td.at[2], D.stairs, 'west', 'top')
  c.set(td.at[0], 3, td.at[2], D.key)
  // the chancel arch: the court gable opened in one wide round arch of dressed stone
  const ch = OPENINGS.chancelArch
  roundArch(c, { cx: ch.cx, z: ch.z, y0: 1, spring: ch.spring, r: ch.r }, { full: D.full, stairs: D.stairs, key: D.key })
  for (let x = ch.cx - ch.r + 1; x <= ch.cx + ch.r - 1; x++) {
    for (let y = 1; y <= ch.spring; y++) c.clear(x, y, ch.z)
    c.set(x, 0, ch.z, P.sanctuaryFloor) // the threshold
  }
  for (let x = ch.cx - ch.r + 2; x <= ch.cx + ch.r - 2; x++) c.clear(x, ch.spring + 1, ch.z)
  // ... and closed by a wrought screen: bars between the piers and two dressed jambs, a door in the middle (the altar
  // stays in sight from the court through the bars)
  const sc = OPENINGS.chancelScreen
  for (const x of sc.jambs) for (let y = 1; y <= ch.spring; y++) c.set(x, y, ch.z, D.full)
  for (const [x, y] of sc.bars) c.set(x, y, ch.z, P.bars)
  door(c, [ch.cx, 1, ch.z], 'south', 'north', { wood: P.wood })
  // windows: the tower's slit lights, the court gable's oculus, the sanctuary's paired side lights
  for (const w of OPENINGS.windows) {
    for (const [x, y, z] of w.cells) c.set(x, y, z, P.glass)
  }
  // the belfry: louvres in twin openings on each face
  const L = OPENINGS.louvres
  for (let y = L.y0; y <= L.y1; y++) for (const i of L.offsets) {
    louvres(c, [[T.spire.cx + i, y, T.z0]], `${P.wood}_trapdoor`, 'north')
    louvres(c, [[T.spire.cx + i, y, T.z1]], `${P.wood}_trapdoor`, 'south')
    louvres(c, [[T.x0, y, T.spire.cz + i]], `${P.wood}_trapdoor`, 'west')
    louvres(c, [[T.x1, y, T.spire.cz + i]], `${P.wood}_trapdoor`, 'east')
  }

  // --- Pass 4: depth and detail ----------------------------------------------------------------------------------
  // a string course round the tower under the belfry
  corbelRing(c, T.x0 - 1, T.z0 - 1, T.x1 + 1, T.z1 + 1, L.y0 - 1, D.stairs)
  // a slate hood over the door with a lantern under it; one in the belfry on a chain from the needle
  hood(c, fd.at[0], 4, T.z0 - 1, P.roof)
  for (let y = L.y1 + 1; y <= T.spire.heights[1]; y++) c.set(T.spire.cx, y, T.spire.cz, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(T.spire.cx, L.y1, T.spire.cz, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // the vow arch over the rose: a round stone arch across the aisle, wreathed in flowering azalea, a lantern under it
  roundArch(c, { cx: mx, z: mz, y0: 1, spring: 3, r: 2 }, { full: D.full, stairs: D.stairs, key: D.key })
  clip(c, [[mx - 2, 5, mz], [mx - 1, 6, mz], [mx, 6, mz], [mx + 1, 6, mz], [mx + 2, 5, mz], [mx, 7, mz]], mix(P.wreath, 5))
  c.set(mx, 4, mz, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // --- Pass 5: interior (the plan's furniture) ---------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (f.part === 'pew') pew(c, { x0: f.x0, x1: f.x1, y: 1, z: f.z, facing: f.facing, arm: f.arm }, P.wood)
    else if (f.part === 'stall') choirStall(c, f.cells, f.facing, P.wood)
    else if (f.part === 'lampPost') lampPost(c, ...f.pos, `${P.wood}_fence`)
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
  // cherry trees in the corner gardens, their crowns spilling over the wall
  for (const { trunk: [tx, tz], lean } of OUTSIDE.trees) {
    const g = MASSES.gardens.find(gg => tx >= gg.x0 && tx <= gg.x1)
    const out = lean === 'west' ? W.west : W.east, inner = lean === 'west' ? g.x1 : g.x0
    const logs = [[tx, 1, tz], [tx, 2, tz], [tx, 3, tz], [tx, 4, tz]]
    // the crown over the garden's front half (the back half stays open to the sky beside the pews), spilling out over
    // the wall's coping, a bough drooping into the corner by the front wall
    const o = lean === 'west' ? -1 : 1, z0 = g.z0, z1 = g.z0 + 1
    const crown = [[tx, 3, z0], [tx, 6, z1], [inner, 6, z1], [out + o, 5, z1]]
    for (let z = z0; z <= z1; z++) for (const x of [g.x0, g.x1, out]) crown.push([x, 4, z], [x, 5, z])
    tree(c, logs, crown, P.trunk, P.blossom)
    // the garden's flowers, in its own soil
    let i = 0
    for (let x = g.x0; x <= g.x1; x++) for (let z = g.z0; z <= g.z1; z++) {
      if (x === tx && z === tz) continue
      if (!c.get(x, 1, z)) c.set(x, 1, z, P.flowers[i++ % P.flowers.length])
    }
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
