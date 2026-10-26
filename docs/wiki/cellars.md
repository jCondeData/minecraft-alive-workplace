# Cellars for the Level IIs and for old builds

Eight builds that stopped at Level II get a Level III that is their Level II with a furnished, lit cellar dug under it;
and a Smithy, Storehouse or Kitchen finished before its blueprint had a cellar (26.15) can have it dug now ("Dig the
cellar"). Built the way the Level 4s' basements are ([Basements and cellars](basements.md)).

Roadmap items: 36.7

## 1. What a player sees

A builder who finishes one of these Level IIs sells its Level III, like every upgrade. Clicked onto the finished
building, it lines up with it; the builder digs the cellar out of the ground, walls and floors it, puts in a ladder
under a hatch in the floor and the furniture, and leaves the building above as it was (only the hatch is new). The
workers' chests and job blocks stay where they were.

| Build | The cellar | Way down |
|---|---|---|
| Inn III | an ale and wine cellar: racks of wine casks along both walls, oak ale casks and sacks of malt (hay) along the front, bottles in chests and pots, a tasting table | ladder in the taproom's north-east corner |
| Winery III | a barrel vault under the barrel cellar: casks two high under a vault of stone-brick stairs, a tasting table, a chest of bottles | ladder in the barrel cellar's corner |
| Farmstead III | a root cellar: bins of pumpkins and melons, hay, sacks (chests), potted mushrooms, a sorting table | ladder by the kitchen |
| Guildhall III | a strongroom: gold, emerald and lapis and the guild's chests caged behind iron bars, ledgers in chests two high, a counting table | ladder by the hall's west wall |
| Barracks III | an armory and lock-up: a cell behind iron bars, an anvil, chests of kit, hay targets | ladder by the east wall |
| Research Lab III | a lower lab: bookshelves, a stone bench of specimens (amethyst, tinted jars, bone, potted cactus and fungus), chests of samples, a work table with candles | ladder by the lab's front wall |
| Jeweler's Workshop III | a vault: gold, amethyst, raw gold and emerald caged behind bars with chests of cut stones on them, chests of rough stones, a sorting table | ladder in the shop's north-west corner |
| Artisan's House III | a storeroom: chests two high, bolts of cloth, crates, a packing table | ladder in the back corner of the hall |

**Dig the cellar.** A finished building whose blueprint now has a cellar, but whose cellar is still mostly ground
(more than half of the spots the drawing leaves open down there are solid), is offered "Dig the cellar" where its
upgrade is offered:

- the village's builders sell it (4 emeralds) as a blueprint called "Dig the cellar: Smithy", already lined up over
  the building; the trade appears when a player opens their trades or they level up, one per building;
- the Village Hall's advice lists it ("Dig the cellar under the Smithy");
- the trade goes again once there is no cellar to dig (dug by the builder or by hand, the building gone, or its dig
  under way), the next time a player opens the builder's trades; one bought before and given to a builder then starts
  nothing ("There's no cellar to dig under the Smithy any more"), and the player keeps it (B386);
- the building's own blueprint clicked onto it lines up over it the same way ("Lined up over the Smithy: give it to a
  builder to dig its cellar").

Given to a builder, that blueprint digs and fits out only the cellar and the way down; nothing above is rebuilt, and a
block a player left in the room above stays. The builder says "… dug the cellar under the Smithy and fitted it out."
Upgrading such a building (a Smithy II over a cellarless Smithy) digs its cellar too, with the rest of the upgrade.

## 2. How it works

- The Level IIIs are drawn in `tools/blueprints/cellars.py` from the Level II (`grow(..., basement=4)`) and the cellar
  helpers of `basements.py`, listed in `StarterBlueprints` with a basement of 4 (`WITH_BASEMENTS`, and
  `LEVEL_3_CELLARS`), so the library moves the cellar under the ground and the upgrade lines up exactly.
- `build/Cellars`: its needsDigging counts the basement's open spots (the drawing's air below y = 0) that are solid in the
  world; more than half solid means the cellar is still to dig. Its offer adds the builder's trades (from
  `ModTrades.builderHomes`, the hook that runs on a level-up and when a player opens the trades). Its toDig feeds the
  hall's advice.
- `Builders.start` and `enqueue`: a site of the very build that stands finished on exactly that spot, whose cellar is
  still to dig, becomes a cellar dig (`BuildSite.setCellarOnly`). Its plan is made from `Cellars.cellarOnly(blueprint)`:
  every block below y = 0, plus the way down in the columns over the basement (the hatch or stair top and the openings
  at y = 0, a ladder running on to y = 1); no foundation and no landscaping. Everything else about the site is as for
  any build, so it stays restartable at any tick.
- A finished dig records the building as finished again, and no blueprint is returned (the job's blueprint was the
  build's own).

## 3. Switches

None new. `blueprintUnlocks` decides whether the Level IIIs start locked, as for every build; the Winery III, the
Jeweler's Workshop III and the Guildhall III are behind their expansions' switches like their other levels; the
Artisan's House III is sold by builders only, like its other levels.

## 4. Saved data

One new field on a build site, `cellar_only` (written only when true; older saves have none and build as before).
Registry ids are new; none renamed.

## 5. Items, blocks, jobs, commands

Blueprints `aliveworkplace:inn_3`, `winery_3`, `farmstead_3`, `guildhall_3`, `barracks_3`, `research_lab_3`,
`jewellers_workshop_3` and `artisans_house_3`, each with a hidden `aliveworkplace:built/<name>` advancement. Lang:
`item.aliveworkplace.blueprint.cellar`, `message.aliveworkplace.blueprint.cellar`, `message.aliveworkplace.cellar_dug`,
`advice.aliveworkplace.cellar` (and `.how`).

## 6. Decisions

- The offer is a builder's trade, like the upgrade's, rather than a new button: the upgrade is offered by the builder
  who finished the building, and an old building's builder may be gone, so any builder of the village (within the
  hall's radius of them) sells it. The hall's advice says where to get it.
- The job is a blueprint of the build itself lined up over it, so it goes to a builder like any blueprint, and the site
  needs only one new saved flag; no new item and no change to the blueprint item's data.
- "Mostly ground" is over half of the basement's open spots solid: a cellar a player half dug by hand is still offered,
  one they dug out isn't.
- The ladder goes under a closed hatch in the floor (as the Level 4s' do since B158) where the floor above is clear two
  blocks up and away from the workers' chests and job blocks.

## 7. Known limits

- A builder sells one "Dig the cellar" per building; once bought out (2 uses) it comes back only when they restock.
- A cellar dug by hand in another shape counts by its open spots only; the dig fills what the drawing wants solid.

## 8. Proof

GameTests: `CellarGameTests` (`levelThreesLineUpOverTheirLevelTwos`: all eight keep every block of their Level II
but the hatch, are listed with their basement and sold by the Level II's builder; `barracksThreeDigsItsArmoury`,
`researchLabThreeDigsItsLowerLab`, `jewellersThreeDigsItsVault`: built over a finished Level II on real ground in
`huge_area`; `oldSmithyDigsOnlyItsCellar`: the offer, the dig of only the cellar, a player's block left alone, and the
Smithy II's plan digging the cellar too, and the trade gone after; `noOfferWhenTheCellarIsThere`; `reloadMidDigFinishesTheCellar`;
`staleDigTradeGoesAndCannotStartABuild`: two Smithies, one dug by hand, a builder saved with its trade), `QaRepairCellarBugTests`, and
`BasementGameTests.basementsLoadUnderTheGround` (all eight load with a way down and a light), and QA's `QaBasementGameTests` (each cellar walked to and back out, its chests within reach, its floor lit everywhere).

Showcase scenes: cellars
