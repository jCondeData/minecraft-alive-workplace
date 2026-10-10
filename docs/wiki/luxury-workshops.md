# The luxury workshops

The buildings of the four luxury jobs, the Vintner, the Tailor, the Printer and the Jeweller ([Classes](classes.md)):
the Winery, the Tailor's Shop, the Print Shop and the Jeweller's Workshop, four blueprints with an upgrade each, and a
small house of each that villages grow. Part of Classes and luxuries (1.8).

Roadmap items: 34.13, 34.14

## What a player sees

Four new buildings in the Blueprint Table, each with a II:

- **Winery.** A stone press house over a half-sunk cellar. Earth is banked up the cellar's sides and back, so only a
  course of rough stone and two barred slits show. A stone stair climbs along the front to the press room; the cellar
  door is at the ground, in the passage under the stair's landing. Up in the press room the cauldron vat stands by the
  door, with a press on a chain and a hatch down. In the cellar: racks of casks (spruce logs laid so their ends show)
  and two big chests. There is no barrel anywhere.
- **Winery II.** A back door onto a tasting porch (a table, stools, a cask) under a lean-to roof, then two terraces of
  sweet berry rows stepping down to a pergola hung with glow berries, a lamp at each foot of the garden.
- **Tailor's Shop.** A timber shop, dark oak and white plaster under a spruce roof. The door is between two banners;
  beside it a bay window shows two dress forms and a bolt of cloth. Inside: the loom, a counter, bolts of wool in seven
  colours, and a fitting room in a lower wing behind.
- **Tailor's Shop II.** A cutting-room storey above (a cutting table, bolts of cloth on racks) and a drying loft in
  the roof, open at the front behind a rail, with dyed wool hung from the rafters and a bale on the hoist. A ladder
  runs up through both floors.
- **Print Shop.** A brick shop in a spruce frame, its plastered gable to the street under a dark oak roof. The
  cartography table is the press: it stands free in the room under a skylight of four glass blocks in the roof. By the
  door a counter for buyers, and behind it shelves of paper (white sheets) and ink (black pots). There is no lectern.
- **Print Shop II.** A lower wing behind the shop: the bindery (a bench with sheets laid out, a chest of paper) and a
  reading room lined with bookshelves three high, with a chair on a red carpet under a lantern.
- **Jeweller's Workshop.** A small shop of cut stone on a dark footing under a slate roof, with iron shutters. The
  street door is an iron door; a stone button beside it, outside and in, opens it. Inside: the stonecutter at a bench
  by the window under a lantern, a counter, and by the front window an amethyst cluster in a glass case. A plank door
  in the east wall is the Jeweller's own way in.
- **Jeweller's Workshop II.** A strong room behind, lower and windowless, through a second iron door: a vault of ten
  chests behind iron bars, with an iron door in the bars.

A Vintner who reaches Journeyman sells the Winery's blueprint, a Tailor the Tailor's Shop's, a Printer the Print
Shop's and a Jeweller the Jeweller's Workshop's, for 12 emeralds.

Villages of all five kinds (plains, desert, savanna, snowy, taiga) now and then grow a small Winery (a cauldron, a
chest of fruit and bottles, casks) with a Vintner living in it, and a small Tailor's Shop (a loom, a chest of wool,
string and dyes, bolts of wool) with a Tailor, a small Print Shop (a cartography table, a chest of paper and ink,
bookshelves) with a Printer, and a small Jeweller's Workshop (a stonecutter, a chest of gold and stones, an amethyst
cluster) with a Jeweller. The hall's house-looks screen names them "Winery", "Tailor's Shop", "Print Shop" and
"Jeweller's Workshop".

In a village with a Steward, a Vintner, a Tailor, a Printer or a Jeweller who has no workstation makes him plan the
building.

## How it works

**The blueprints.** Drawn in `tools/blueprints/workshops.py` to `tools/blueprints/STYLE.md`. Sizes (wide x tall x
deep): Winery 13 x 14 x 12, Winery II 13 x 14 x 20, Tailor's Shop 11 x 11 x 12, Tailor's Shop II 11 x 15 x 12. Each II
keeps its first tier (95% of the Winery's blocks, 79% of the Tailor's Shop's), so a builder upgrading one only builds
what is new. Print Shop 11 x 11 x 9, Print Shop II 11 x 11 x 16, Jeweller's Workshop 9 x 10 x 9, Jeweller's Workshop II
9 x 10 x 14 (both II are the first tier grown backwards, so nearly all of it is kept).
Each building has exactly one job block: the Winery its cauldron, the Tailor's Shop its loom, the Print Shop its
cartography table, the Jeweller's Workshop its stonecutter.

**The iron door.** A villager can't open an iron door, and can't plan a path through a closed one. So the Jeweller's
Workshop has two doors: the iron one on the street for players (press the button), and a plank door in the east wall,
which is how the Jeweller reaches the stonecutter.

**Half-sunk.** A blueprint can't dig below the block it is placed on, so the cellar is the ground storey and the earth
comes up to it: a bank of dirt and grass two blocks high against the walls and one high outside that. Winery II's
garden is the same idea made bigger: the porch stands on a platform as high as the press room's floor, and the two
terraces step down from it.

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
zone, the Tailor's Shop and the Jeweller's Workshop in the Market zone.

## Switches

| Key | Default | What it does |
|---|---|---|
| `vintners` | on from 1.8 | Off (or 1.8 not yet released): villages don't grow the Winery house. The blueprints stay in the Blueprint Table |
| `tailors` | on from 1.8 | Off (or 1.8 not yet released): villages don't grow the Tailor's Shop house. The blueprints stay in the Blueprint Table |
| `printers` | on from 1.8 | Off (or 1.8 not yet released): villages don't grow the Print Shop house. The blueprints stay in the Blueprint Table |
| `jewellers` | on from 1.8 | Off (or 1.8 not yet released): villages don't grow the Jeweller's Workshop house. The blueprints stay in the Blueprint Table |

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
- 34.14 (lane d): the Jeweller's Workshop keeps the iron door the item asks for and adds a plank door for the
  Jeweller, because a villager can't open or path through an iron door.
- 34.14 (lane d): the skylight is glass blocks while the windows are panes; a pane can't lie in a roof.
- 34.14 (lane d): paper and ink on the shelves are white carpets and unlit black candles.
- 34.14 (lane d): Print Shop II is a wing behind, not a storey, so the skylight over the press stays.
- 34.14 (lane d): the Print Shop is planned in Workshops and the Jeweller's Workshop in Market.
- 34.14 (lane d): the village Print Shop's chest holds a Book and Quill now and then, not a Gazette, which would be
  blank from a chest.

## Known limits

- One worker per building, in both tiers.
- The iron doors open only by their buttons, for a moment; the Jeweller never uses them.
- A worker of the four jobs who was set to Journeyman or higher without levelling up (by a command) doesn't sell the
  blueprint; the next level-up to Journeyman is what adds it.
- The Steward doesn't plan any of the four II by a rule of their own.
- The showcase scene has not been filmed yet: `luxury_workshops` is filmed on GitHub after the push.

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
- Showcase scene `luxury_workshops`: the eight builds front and back, and the four plains village houses with their
  workers.
