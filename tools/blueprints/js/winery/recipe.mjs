// The Winery (winery, winery_2, winery_3; ROADMAP 49), the recipe: assembles plan.mjs from the parts library (../parts)
// in passes (block-out and structure, roofs and gables, openings, depth and detail, interior, ground and light, the
// vault) for LEVEL = 1, 2 or 3. Each level is the last one grown by the same lines: II adds the Vineyard and the Arbour
// behind the house, III digs the Vintage Vault under the Cask Hall and opens its hatch (nothing else changes above the
// ground). Returns the structure for the minecraft-builder skill's runner and writes furniture.json (the furniture
// spots). Build every level: build.sh. CUT=<y> cuts everything above y (the drawing's y); ABOVE=1 drops the vault.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, BASEMENT, PALETTE, MASSES, VAT, OPENINGS, STORE, FURNITURE, FRUIT } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, doorway, quoins, stringCourse,
  gableRoof, gableWall, dormer, catslideRoof, bargeTrim, hood, louvre, flight, railing, hatchLadder, cellar, furnitureParts,
  doubleChest, beamRun, perron, logCaskRack, casks, trellisRow, fruitingVines, slab, stair,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level])
  const P = PALETTE
  const stone = mix(P.stone, 11), base = mix(P.plinth, 5), plaster = mix(P.infill, 3)
  const H = MASSES.house, G = MASSES.gallery, L = MASSES.leanTo
  const { x0, x1, z0, z1 } = H
  const two = level >= 2, three = level >= 3

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // the Cask Hall: a plinth of the darkest stone, rubble walls to the press floor's level, dressed quoins and a band
  plinth(c, x0, z0, x1, z1, base)
  floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, P.hallFloor)
  for (const [a, b] of [[[x0, z0], [x1, z0]], [[x0, z1], [x1, z1]], [[x0, z0], [x0, z1]], [[x1, z0], [x1, z1]]])
    wallPanel(c, a, b, 1, H.stoneTop - 1, stone)
  quoins(c, [[x0, z0], [x1, z0], [x0, z1], [x1, z1]], 1, H.stoneTop - 1, P.dressed, P.dressedAlt)
  stringCourse(c, x0, z0, x1, z1, H.stoneTop, P.dressed)
  // the press floor (a gallery over the front of the hall) and the wall plate round the rest, carrying the plaster
  floor(c, G.x0, G.z0, G.x1, G.z1, G.y, P.floor)
  for (let z = G.z1 + 1; z <= z1 - 1; z++) { c.set(x0 + 1, G.y, z, P.frame, { axis: 'z' }); c.set(x1 - 1, G.y, z, P.frame, { axis: 'z' }) }
  for (let x = x0 + 2; x <= x1 - 2; x++) c.set(x, G.y, z1 - 1, P.frame, { axis: 'x' })
  // the timber storey: dark oak posts on the stone's edge, the plaster a block behind them on every side; at the back
  // the storey is jettied a block out over the stone on a course of brackets (zb = the jetty's post line)
  const top = H.beamY, zb = z1 + 1
  for (const x of MASSES.posts.gable) { post(c, x, z0, top - 3, top - 1, P.frame); post(c, x, zb, top - 3, top - 1, P.frame) }
  for (const z of MASSES.posts.side) { post(c, x0, z, top - 3, top - 1, P.frame); post(c, x1, z, top - 3, top - 1, P.frame) }
  for (let x = x0; x <= x1; x++) stair(c, x, H.stoneTop, zb, `${P.trim}_stairs`, 'north', 'top') // the jetty's brackets
  for (const x of [x0 + 1, x0 + 2, x0 + 3, x1 - 3, x1 - 2, x1 - 1]) for (let y = top - 3; y < top; y++) c.set(x, y, z0 + 1, plaster)
  for (let x = x0 + 1; x <= x1 - 1; x++) for (let y = top - 3; y < top; y++) c.set(x, y, z1, plaster)
  for (let z = z0 + 1; z <= z1 - 1; z++) for (let y = top - 3; y < top; y++) { c.set(x0 + 1, y, z, plaster); c.set(x1 - 1, y, z, plaster) }
  beamRing(c, x0, z0, x1, zb, top, P.frame)
  // tie beams across the hall: over the gallery's edge (the press floor's lamps) and over the aisle (the hall's)
  for (const z of [6]) for (let x = x0 + 2; x <= x1 - 2; x++) c.set(x, top, z, P.frame, { axis: 'x' })
  // where the lean-to meets the house the post line is closed up in plaster
  for (let z = L.z0; z <= L.z1; z++) for (let y = top - 3; y < top; y++) if (!MASSES.posts.side.includes(z)) c.set(x1, y, z, plaster)
  // the front gable jettied a block out over the perron on a bressummer and four brackets (zf = its line)
  const zf = z0 - 1
  for (let x = x0; x <= x1; x++) c.set(x, top, zf, P.frame, { axis: 'x' })
  for (const x of MASSES.posts.gable) stair(c, x, top - 1, zf, `${P.trim}_stairs`, 'south', 'top')

  // --- Pass 2: roofs and gables ----------------------------------------------------------------------------------
  const R = MASSES.roof
  const main = gableRoof(c, R, P.roof)
  const gable = line => gableWall(c, 'z', line, [x0, x1], top + 1, main.under, (u, y) => {
    if (u === 7) return y >= 11 ? [P.frame, { axis: 'y' }] : y === top + 1 ? [P.frame, { axis: 'y' }] : P.glass
    if (u === 4 || u === 10) return [P.frame, { axis: 'y' }]
    return plaster
  })
  gable(zf); gable(zb)
  bargeTrim(c, 'z', R.along[0], R.across, main.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', R.along[1], R.across, main.under, `${P.trim}_stairs`)
  // dormers over the long sides' middle bays (the east one over the Tasting Room), one each side of the louvre
  dormer(c, 7, P.roof, plaster, P.glass, P.frame, { face: x0 + 1, eave: x0, y0: top + 1 })
  dormer(c, 7, P.roof, plaster, P.glass, P.frame, { face: x1 - 1, eave: x1, y0: top + 1 })
  // the Tasting Room: a stone lean-to on the east, its tiled roof falling east from under the main eave to its own
  // eave a block past its wall, its ends closed in stone, an upstand of plaster between it and the main eave
  for (let x = L.x0 + 1; x <= L.x1; x++) for (let z = L.z0; z <= L.z1; z++) {
    const edge = x === L.x1 || z === L.z0 || z === L.z1
    c.set(x, 0, z, edge ? base : P.hallFloor) // inside, the hall's floor runs on through the doorway at its level
  }
  const lean = catslideRoof(c, L.roof, P.roof, x => x > L.x0)
  for (let x = L.x0 + 1; x <= L.x1; x++) for (const z of [L.z0, L.z1]) for (let y = 1; y < lean.under[x]; y++) c.set(x, y, z, stone)
  for (let z = L.z0; z <= L.z1; z++) for (let y = 1; y < lean.under[L.x1]; y++) c.set(L.x1, y, z, stone)
  quoins(c, [[L.x1, L.z0], [L.x1, L.z1]], 1, lean.under[L.x1] - 1, P.dressed, P.dressedAlt)
  for (let z = L.roof.along[0]; z <= L.roof.along[1]; z++) for (let y = lean.ridgeY; y < top; y++) c.set(L.x0 + 1, y, z, plaster)
  c.set(14, 0, L.z0 - 1, base) // under the Tasting Room window's sill

  // --- Pass 3: openings -----------------------------------------------------------------------------------------
  const O = OPENINGS
  door(c, O.hallDoor.at, O.hallDoor.facing, O.hallDoor.out, { wood: P.wood, step: O.hallDoor.step, stepStair: P.step })
  door(c, O.pressDoor.at, O.pressDoor.facing, O.pressDoor.out, { wood: P.wood })
  c.set(7, 6, z0, P.frame, { axis: 'x' }) // the press door's lintel
  door(c, O.backDoor.at, O.backDoor.facing, O.backDoor.out, { wood: P.wood, step: O.backDoor.step, stepStair: P.step })
  doorway(c, O.tastingDoorway.at, O.tastingDoorway.height)
  const pots = {
    // 50.4 ship: the long sides' sills stay bare (their pots, out on ledges five up, overran Level II's build day)
    gable: [], tasting: ['potted_red_tulip'],
  }
  for (const w of O.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: pots[w.name] ?? [], sill: `${P.trim}_stairs` })
  for (const [x, y, z] of O.vents) c.set(x, y, z, P.bars)

  // --- Pass 4: depth and detail ---------------------------------------------------------------------------------
  // the Perron: two flights of dressed steps meeting at the landing, the hall's door in the passage under it
  const PR = MASSES.perron
  perron(c, PR, { step: P.step, fill: stone, landing: P.dressed })
  for (const x of [3, 4, 5, 9, 10, 11]) for (let y = 0; y < PR.top; y++) if (c.name(x, y, PR.z1) !== 'minecraft:air' && !c.name(x, y, PR.z1).endsWith('stairs')) c.clear(x, y, PR.z1) // the steps' back row is hollow behind the cheek
  c.set(7, PR.top + 1, PR.z0, `${P.trim}_fence`) // a lantern post at the landing's front
  c.set(7, PR.top + 2, PR.z0, 'lantern', { hanging: 'false', waterlogged: 'false' })
  c.set(7, PR.top - 1, PR.z0, 'lantern', { hanging: 'true', waterlogged: 'false' }) // under the landing, at the hall's door
  // the Louvre: the fermentation vent on the ridge
  const LV = MASSES.louvre
  louvre(c, LV.cx, LV.cz, LV.y, { base: P.roof.full, post: `${P.trim}_fence`, roof: P.roof, finial: 'lightning_rod' })
  // --- Pass 5: interior (storage, the vat, the stair, the furniture) ----------------------------------------------
  c.set(...VAT, P.vat)
  for (const [x, y, z, facing, along] of STORE.chests) doubleChest(c, [x, y, z], facing, along)
  for (const r of STORE.racks) logCaskRack(c, r, P.cask)
  casks(c, STORE.lampCasks, P.cask, 'z')
  flight(c, O.hallStair, 'west', P.wood, stone)
  railing(c, O.galleryRail, P.wood)
  for (const f of FURNITURE) if ((f.level ?? 1) <= level) furnitureParts[f.part](c, f)
  for (const [x, y, z, id, props] of FRUIT) c.set(x, y, z, id, props)

  // --- Pass 6: the Vineyard and the Arbour (Level II) -------------------------------------------------------------
  if (two) {
    const V = MASSES.vineyard, A = MASSES.arbour
    // the Vineyard: one raised bed of soil, three rows of sweet berries in it (50.4 ship: its kerb and trellis posts left
    // out, with them Level II overran its builder's working day)
    const B = MASSES.bed
    for (let x = B.x0 + 1; x < B.x1; x++) for (let z = B.z0 + 1; z < B.z1; z++) {
      c.set(x, 0, z, P.soil)
      c.set(x, 1, z, 'sweet_berry_bush', { age: '3' })
    }
    // the Arbour: dark oak posts, beams along z from the house's back wall, joists across, glow berries hanging
    for (const z of [A.z0, A.z1 - 1]) for (const x of [A.x0, A.x1]) for (let y = 0; y < A.beamY; y++) c.set(x, y, z, P.arbourPost)
    for (const x of [A.x0, A.x1]) for (let z = A.z0; z <= A.z1; z++) c.set(x, A.beamY, z, P.arbourRail, { axis: 'z' })
    for (const z of [A.z0, A.z1 - 1]) c.set(A.x0 + 1, A.beamY, z, P.arbourRail, { axis: 'x' }) // ties between the beams over each pair of posts
    const vines = []
    for (const x of [A.x0, A.x1]) for (const z of [15, 17, 19]) vines.push([x, A.beamY - 1, z, 1])
    for (const z of [A.z0, A.z1 - 1]) vines.push([A.x0 + 1, A.beamY - 1, z, 1])
    fruitingVines(c, vines, { tip: 'cave_vines', stem: 'cave_vines_plant' })
    // a lantern post at the Arbour's far end, a bench either side of it
    c.set(7, 0, A.z1, `${P.trim}_fence`); c.set(7, 1, A.z1, `${P.trim}_fence`)
    c.set(7, 2, A.z1, 'lantern', { hanging: 'false', waterlogged: 'false' })
  }

  // --- Pass 7: the Vintage Vault (Level III) -----------------------------------------------------------------------
  let dug = null
  if (three) {
    const VV = MASSES.vault
    dug = cellar(c, VV.x0, VV.z0, VV.x1, VV.z1, VV.depth, mix(P.vaultWall, 7), P.vaultFloor)
    for (const r of STORE.vaultRacks) logCaskRack(c, r, P.cask)
    const h = O.hatch
    hatchLadder(c, h.x, h.z, -VV.depth + 1, 0, h.facing, P.wood)
  }
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
  const id = level === 1 ? 'winery' : `winery_${level}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
