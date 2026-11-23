# The luxury workshops

The buildings of the four luxury jobs, the Vintner, the Tailor, the Printer and the Jeweler ([Classes](classes.md)):
the Winery, the Tailor's Shop, the Print Shop and the Jeweler's Workshop, four blueprints with an upgrade each, and a
small house of each that villages grow. Part of Classes and luxuries (1.8).

Roadmap items: 34.13, 34.14, 50.6, 50.7

## What a player sees

Four new buildings in the Blueprint Table, each with a II:

- **Winery** (rebuilt from scratch in 50.4, `tools/blueprints/js/winery`). A gravity-flow press house: the fruit goes
  up, the wine comes down. A double stone perron climbs the front to a landing and the door of the press floor, a white
  timber-framed story in a dark oak frame under a rose granite roof, where the cauldron vat stands on the door's axis by
  a work counter and the chest of bottles. Under it, the stone Cask Hall (its door in the passage under the landing):
  racks of casks (spruce logs on their sides) along the walls, two big chests against the front wall, a stair up to the
  press floor. A stone Tasting Room leans on the east side, a table for two under its lamp. There is no barrel anywhere.
- **Winery II.** The vineyard behind it, through the back door: rows of sweet berries in a raised bed beside
  an arbor of spruce posts hung with glow berries.
- **Tailor's Shop** (rebuilt from scratch in 50.6). A plain white shop between dark posts with a jettied gable over a
  glazed shopfront: bolts of colored cloth on show behind the glass. Inside: the loom and a sewing table, the chests of
  wool beside them, and a pressing stove whose stack rises at the back.
- **Tailor's Shop II.** A slender drying tower at the back corner, its finial well over the shop's ridge, the loft at
  its top hung with dyed cloth in a spectrum. A ladder runs up through both floors.
- **Print Shop** (rebuilt from scratch in 50.6, "the Lantern Press"). One tall brick hall with its gable to the street
  under a dark slate roof, and on the ridge a square brick lantern whose glazed top is the press's skylight. A dark-oak
  shopfront: two display windows either side of the door, flower beds under them, buyers step up onto the stone apron.
  Inside, the counter by the door and behind it the cartography table (the press) alone in the middle of the room in a
  column of light from the lantern, with shelves of paper (white sheets) and ink (black pots). There is no lectern.
- **Print Shop II.** A lower reading room beside the hall, its gable set back from the street and the two roofs meeting
  in a valley, lined with bookshelves, with a reading table.
- **Jeweler's Workshop** (rebuilt from scratch in 50.7, "the Stepped Gable"). A stone-brick shop gable-end to the
  street on a dark deepslate footing, under a roof of green copper, its front a crow-stepped gable. A glass case juts
  out into the street between the two doors, the amethyst cluster on a plinth in the middle of it, under a copper
  pentice. The buyers' door, on the right, is an iron door; a stone button beside it, outside and in, opens it. The
  timber door on the left is the Jeweler's own way in. Inside, one tall room open to the rafters: the stonecutter
  under the side window with its lantern hanging over it, the anvil, a furnace and his chest down the west wall, a
  counter keeping buyers to the front.
- **Jeweler's Workshop II.** The Strong Room behind: a squat stone barrel vault on buttresses, in through a second
  iron door to an anteroom, then a barred grille with a third iron door; behind the bars fourteen chests stand two
  high against the round back wall, and barred slits and a barred lunette let a little light in.

A Vintner who reaches Journeyman sells the Winery's blueprint, a Tailor the Tailor's Shop's, a Printer the Print
Shop's and a Jeweler the Jeweler's Workshop's, for 12 emeralds.

Villages of all five kinds (plains, desert, savanna, snowy, taiga) now and then grow a small Winery (a cauldron, a
chest of fruit and bottles, casks) with a Vintner living in it, and a small Tailor's Shop (a loom, a chest of wool,
string and dyes, bolts of wool) with a Tailor, a small Print Shop (a cartography table, a chest of paper and ink,
bookshelves) with a Printer, and a small Jeweler's Workshop (a stonecutter, a chest of gold and stones, an amethyst
cluster) with a Jeweler. The hall's house-looks screen names them "Winery", "Tailor's Shop", "Print Shop" and
"Jeweler's Workshop".

In a village with a Steward, a Vintner, a Tailor, a Printer or a Jeweler who has no workstation makes him plan the
building.

## How it works

**The blueprints.** Drawn in `tools/blueprints/workshops.py` (the Winery since 50.4, the Tailor's Shop and the Print Shop since 50.6, the Jeweler's Workshop since 50.7 in `tools/blueprints/js/<family>`) to `tools/blueprints/STYLE.md`. Sizes (wide x tall x
deep): Winery 17 x 17 x 15, Winery II 17 x 17 x 22 (50.4), Tailor's Shop 12 x 13 x 12, Tailor's Shop II 15 x 17 x 13 (50.6). Each
II keeps most of its first tier, so a builder upgrading one only builds what is new. Print Shop 11 x 12 x 11, Print Shop
II 17 x 12 x 11 (50.6), Jeweler's Workshop 11 x 13 x 10, Jeweler's Workshop II
11 x 13 x 17 (50.7; its II is the first tier grown backwards, so nearly all of it is kept).
Each building has exactly one job block: the Winery its cauldron, the Tailor's Shop its loom, the Print Shop its
cartography table, the Jeweler's Workshop its stonecutter.

**The iron door.** A villager can't open an iron door, and can't plan a path through a closed one. So the Jeweler's
Workshop has two doors on the street: the iron one for players (press the button), and a timber door beside the glass
case, which is how the Jeweler reaches the stonecutter.

**Casks below the vat.** A blueprint can't dig below the block it is placed on, so the cellar is the ground story:
since 50.4 the stone Cask Hall, with the vat one story up on the press floor (the old drawing banked earth up a
half-sunk cellar instead). The casks and chests under the vat are what the Winery's tests count.

**The Journeyman's blueprint.** A villager's level shows two of that level's listed trades, picked at random, and the
four jobs already have two at Journeyman (their makings bought, their Journeyman luxury sold). So the
blueprint is not a listed trade: it is added to the villager's offers the moment they become a Journeyman, and so it
is always there. It can be bought 3 times before they restock.

**In villages.** The same small house as our other village houses, in each kind's own materials, with weight 2 in the
village's house pool (as a vanilla job house has). The villager comes with the job already, because a jobless
villager at a cauldron becomes a Leatherworker, one at a loom a Shepherd, one at a cartography table a Cartographer
and one at a stonecutter a Mason. Each house has one job block and no other.

**The Steward's rules.** `aliveworkplace:workplace_winery`, `aliveworkplace:workplace_tailors_shop`,
`aliveworkplace:workplace_print_shop` and `aliveworkplace:workplace_jewellers_workshop`, in the same
form as the other workplace rules: a worker of the trade without a workstation (or the job wanted with no free block)
asks for the building, at priority 62, at most once every 2 days. The Winery and the Print Shop go in the Workshops
zone, the Tailor's Shop and the Jeweler's Workshop in the Market zone.

## Switches

| Key | Default | What it does |
|---|---|---|
| `vintners` | on from 1.0 | Off: villages don't grow the Winery house. The blueprints stay in the Blueprint Table |
| `tailors` | on from 1.0 | Off: villages don't grow the Tailor's Shop house. The blueprints stay in the Blueprint Table |
| `printers` | on from 1.0 | Off: villages don't grow the Print Shop house. The blueprints stay in the Blueprint Table |
| `jewellers` | on from 1.0 | Off: villages don't grow the Jeweler's Workshop house. The blueprints stay in the Blueprint Table |

The house pools are filled when the server starts, so a change takes a restart; houses already grown stay.

## Saved data

none (the Journeyman's blueprint is an ordinary trade, saved with the villager's other offers)

## Items, blocks, jobs, commands

- Blueprints: `aliveworkplace:winery`, `aliveworkplace:winery_2`, `aliveworkplace:tailors_shop`,
  `aliveworkplace:tailors_shop_2`, `aliveworkplace:print_shop`, `aliveworkplace:print_shop_2`,
  `aliveworkplace:jewellers_workshop`, `aliveworkplace:jewellers_workshop_2`.
- Village houses: `village/<kind>_winery`, `village/<kind>_tailors_shop`, `village/<kind>_print_shop` and
  `village/<kind>_jewellers_workshop` for each of the five kinds.
- Loot tables of the houses' chests: `chests/village_winery` (apples, sweet berries, glass bottles, glow berries, now
  and then Cider) and `chests/village_tailors_shop` (white wool, string, leather, dyes, now and then Work Clothes), `chests/village_print_shop` (paper, ink sacs, books, feathers,
  leather, now and then a Book and Quill) and `chests/village_jewellers_workshop` (gold nuggets, amethyst shards, gold
  ingots, emeralds, flint, now and then an Amethyst Ring).
- Steward rules: `aliveworkplace:workplace_winery`, `aliveworkplace:workplace_tailors_shop`,
  `aliveworkplace:workplace_print_shop`, `aliveworkplace:workplace_jewellers_workshop`.
- Jobs they serve: `aliveworkplace:vintner`, `aliveworkplace:tailor`, `aliveworkplace:printer`,
  `aliveworkplace:jeweller`.

## Decisions

- 34.13 (lane d): no barrels in the Winery, as the item asks: a barrel is the fisherman's job block. The casks are
  spruce logs on their sides.
- 34.13 (lane d): "half-sunk" is done with banked earth, because a blueprint's lowest layer sits on the ground.
- 34.13 (lane d): the Journeyman's blueprint is added on level-up instead of being a listed trade, so that it is
  always offered and the two Journeyman trades of 34.9 and 34.10 both stay.
- 34.13 (lane d): the village houses are gated by the jobs' own switches. With `vintners` off, a Winery's villager
  would have a job that does nothing.
- 34.13 (lane d): the Winery is planned in Workshops and the Tailor's Shop in Market (it is a shop).
- 34.13 (lane d): each II adds no second job block; the item names none.
- 34.14 (lane d): the Jeweler's Workshop keeps the iron door the item asks for and adds a plank door for the
  Jeweler, because a villager can't open or path through an iron door.
- 34.14 (lane d): the skylight is glass blocks while the windows are panes; a pane can't lie in a roof.
- 34.14 (lane d): paper and ink on the shelves are white carpets and unlit black candles.
- 34.14 (lane d): Print Shop II is a wing behind, not a story, so the skylight over the press stays.
- 34.14 (lane d): the Print Shop is planned in Workshops and the Jeweler's Workshop in Market.
- 34.14 (lane d): the village Print Shop's chest holds a Book and Quill now and then, not a Gazette, which would be
  blank from a chest.

## Known limits

- One worker per building, in both tiers.
- The iron doors open only by their buttons, for a moment; the Jeweler never uses them.
- A worker of the four jobs who was set to Journeyman or higher without leveling up (by a command) doesn't sell the
  blueprint; the next level-up to Journeyman is what adds it.
- The Steward doesn't plan any of the four II by a rule of their own.

## Proof

- `LuxuryWorkshopsGameTests`: both Steward rules (`luxuryWorkshopRule_winery`, `luxuryWorkshopRule_tailors_shop`), each
  of the four built by a builder with its worker then taking the vat or the loom (`luxuryWorkshopBuilt_winery` and
  the other three), `luxuryWorkshopsAreInTheBlueprintTable`, `theBuildsHaveWhatTheirTradesNeed`,
  `journeymenSellTheirBuildingsBlueprint`, `villagesGrowTheHousesOnlyWhileTheirJobsAreOn`; for 34.14 the same
  generators (`luxuryWorkshopRule_print_shop`, `luxuryWorkshopRule_jewellers_workshop`, `luxuryWorkshopBuilt_print_shop`
  and the other three), `thePrintShopAndTheJewellersHaveWhatTheirTradesNeed` and
  `villagesGrowThePrintShopAndTheJewellersOnlyWhileTheirJobsAreOn`.
- `VillageGameTests`: `everyVillageTypeCanGrowTheOtherHouses` (the four houses in all five kinds, one job block, their
  worker), `theWinerysVintnerTakesItsVat`, `theTailorsShopsTailorTakesItsLoom`, `thePrintShopsPrinterTakesItsPress`,
  `theJewellersWorkshopsJewellerTakesItsBench`.
- QA (lane-c-1123-0953): `QaLuxuryWorkshops1123cGameTests` (a second villager turned away at a taken cauldron, cartography
  table or stonecutter, in English; a maker takes from a Vintner's chest but never a builder's; a Novice leaves a Brooch's
  and a Circlet's makings alone and asks for a ring's; the Tailor dyes white wool for Fine Clothes; the cartography table
  broken mid-job loses nothing; a full own chest loses no rings).
- Showcase scene `luxury_workshops`: the eight builds front and back, and the four plains village houses with their
  workers.
