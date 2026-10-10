# The Winery and the Tailor's Shop

The buildings of the first two luxury jobs, the Vintner and the Tailor ([Classes](classes.md)): two blueprints with an
upgrade each, and a small house of each that villages grow. Part of Classes and luxuries (1.8).

Roadmap items: 34.13

## What a player sees

Two new buildings in the Blueprint Table, each with a II:

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

A Vintner who reaches Journeyman sells the Winery's blueprint, a Tailor the Tailor's Shop's, for 12 emeralds.

Villages of all five kinds (plains, desert, savanna, snowy, taiga) now and then grow a small Winery (a cauldron, a
chest of fruit and bottles, casks) with a Vintner living in it, and a small Tailor's Shop (a loom, a chest of wool,
string and dyes, bolts of wool) with a Tailor. The hall's house-looks screen names them "Winery" and "Tailor's Shop".

In a village with a Steward, a Vintner or a Tailor who has no workstation makes him plan the building.

## How it works

**The blueprints.** Drawn in `tools/blueprints/workshops.py` to `tools/blueprints/STYLE.md`. Sizes (wide x tall x
deep): Winery 13 x 14 x 12, Winery II 13 x 14 x 20, Tailor's Shop 11 x 11 x 12, Tailor's Shop II 11 x 15 x 12. Each II
keeps its first tier (95% of the Winery's blocks, 79% of the Tailor's Shop's), so a builder upgrading one only builds
what is new. Each building has exactly one job block: the Winery its cauldron, the Tailor's Shop its loom.

**Half-sunk.** A blueprint can't dig below the block it is placed on, so the cellar is the ground storey and the earth
comes up to it: a bank of dirt and grass two blocks high against the walls and one high outside that. Winery II's
garden is the same idea made bigger: the porch stands on a platform as high as the press room's floor, and the two
terraces step down from it.

**The Journeyman's blueprint.** A villager's level shows two of that level's listed trades, picked at random, and the
Vintner and Tailor already have two at Journeyman (their makings bought, Vintage Wine or Noble Robes sold). So the
blueprint is not a listed trade: it is added to the villager's offers the moment they become a Journeyman, and so it
is always there. It can be bought 3 times before they restock.

**In villages.** The same small house as our other village houses, in each kind's own materials, with weight 2 in the
village's house pool (as a vanilla job house has). The villager comes with the job already, because a jobless
villager at a cauldron becomes a Leatherworker and one at a loom a Shepherd. Each house has one job block and no other.

**The Steward's rules.** `aliveworkplace:workplace_winery` and `aliveworkplace:workplace_tailors_shop`, in the same
form as the other workplace rules: a worker of the trade without a workstation (or the job wanted with no free block)
asks for the building, at priority 62, at most once every 2 days. The Winery goes in the Workshops zone, the Tailor's
Shop in the Market zone.

## Switches

| Key | Default | What it does |
|---|---|---|
| `vintners` | on from 1.8 | Off (or 1.8 not yet released): villages don't grow the Winery house. The blueprints stay in the Blueprint Table |
| `tailors` | on from 1.8 | Off (or 1.8 not yet released): villages don't grow the Tailor's Shop house. The blueprints stay in the Blueprint Table |

The house pools are filled when the server starts, so a change takes a restart; houses already grown stay.

## Saved data

none (the Journeyman's blueprint is an ordinary trade, saved with the villager's other offers)

## Items, blocks, jobs, commands

- Blueprints: `aliveworkplace:winery`, `aliveworkplace:winery_2`, `aliveworkplace:tailors_shop`,
  `aliveworkplace:tailors_shop_2`.
- Village houses: `village/<kind>_winery` and `village/<kind>_tailors_shop` for each of the five kinds.
- Loot tables of the houses' chests: `chests/village_winery` (apples, sweet berries, glass bottles, glow berries, now
  and then Cider) and `chests/village_tailors_shop` (white wool, string, leather, dyes, now and then Work Clothes).
- Steward rules: `aliveworkplace:workplace_winery`, `aliveworkplace:workplace_tailors_shop`.
- Jobs they serve: `aliveworkplace:vintner`, `aliveworkplace:tailor`.

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

## Known limits

- One Vintner per Winery and one Tailor per Tailor's Shop, in both tiers.
- A Vintner or Tailor who was set to Journeyman or higher without levelling up (by a command) doesn't sell the
  blueprint; the next level-up to Journeyman is what adds it.
- The Steward doesn't plan Winery II or Tailor's Shop II by a rule of their own.
- The showcase scene has not been filmed yet: `luxury_workshops` is filmed on GitHub after the push.

## Proof

- `LuxuryWorkshopsGameTests`: both Steward rules (`luxuryWorkshopRule_winery`, `luxuryWorkshopRule_tailors_shop`), each
  of the four built by a builder with its worker then taking the vat or the loom (`luxuryWorkshopBuilt_winery` and
  the other three), `luxuryWorkshopsAreInTheBlueprintTable`, `theBuildsHaveWhatTheirTradesNeed`,
  `journeymenSellTheirBuildingsBlueprint`, `villagesGrowTheHousesOnlyWhileTheirJobsAreOn`.
- `VillageGameTests`: `everyVillageTypeCanGrowTheOtherHouses` (both houses in all five kinds, one job block, their
  worker), `theWinerysVintnerTakesItsVat`, `theTailorsShopsTailorTakesItsLoom`.
- Showcase scene `luxury_workshops`: the four builds front and back, and the two plains village houses with their
  workers.
