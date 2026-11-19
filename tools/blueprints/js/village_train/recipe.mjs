// The Village Train, the recipe: assembles plan.mjs from the parts library (../parts) in passes (underframe, deck,
// canopy, light), checks it against the brief's rules (trainRules) and returns the structure for the
// minecraft-builder skill's runner. Writes furniture.json (empty: no furniture) and prints the bill of materials.
// Build: ./build.sh (a candidate); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, STOCK, BILL } from './plan.mjs'
import { Canvas } from '../parts/index.mjs'
import { conductorSeat, cabRoof, carLamp, trainRules, trainBill } from '../parts/rail_car.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, U = MASSES.underframe, D = MASSES.deck, Q = MASSES.posts, R = MASSES.canopy

  // --- Pass 1: the underframe: the bogey nearest the station, the iron frame, the goods chest under the rear platform -
  c.set(...U.bogey, P.bogey, { axis: 'x', waterlogged: 'false' })
  for (const f of U.frame) c.set(...f, P.casing)
  c.set(...U.chest, P.chest, { facing: 'south', type: 'single', waterlogged: 'false' })

  // --- Pass 2: the deck: the conductor between controls fore and aft, up on the frame ------------------------------
  conductorSeat(c, ...D.seat, P)

  // --- Pass 3: the canopy: one line of slabs over the whole car, on a post at each end -----------------------------
  for (const q of [Q.fore, Q.aft]) for (let y = q.y0; y <= q.y1; y++) c.set(q.x, y, 0, P.post, { waterlogged: 'false' })
  cabRoof(c, R.x0, R.x1, R.y, 0, P, { type: R.type })

  // --- Pass 4: light: the cab lamp hung under the canopy by the driver's shoulder -----------------------------------
  carLamp(c, ...MASSES.lamp, P, true)
  return c
}

export default function build(api) {
  const c = draw()
  const fit = trainRules(c, { length: SIZE[0], height: SIZE[1], width: SIZE[2] })
  const bill = trainBill(c)
  const keys = new Set([...Object.keys(bill), ...Object.keys(BILL)])
  if ([...keys].some(k => bill[k] !== BILL[k])) throw new Error(`the bill changed: ${JSON.stringify(bill)}; update plan.mjs BILL and the three test barrels`)
  const over = Object.entries(bill).filter(([k, n]) => n > (STOCK[k] ?? 0))
  console.error(`village_train: ${fit.blocks} blocks, ${fit.empty} empty cells in the glue box; bill ${JSON.stringify(bill)}` +
    (over.length ? `; OVER THE TEST STOCK: ${over.map(([k, n]) => `${k} ${n}/${STOCK[k] ?? 0}`).join(', ')}` : '; inside the test stock'))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `village_train_cut${cut}` : 'village_train' } }
}
