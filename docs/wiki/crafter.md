# Crafter

A villager at a Crafter's Workbench who makes what the village's workers are short of, through whole recipe chains:
logs sawn into planks, planks cut into slabs, then the barrel, chair or fence asked for. Part of Furnished homes and
modded storage (2.1), behind its expansion gate (`Expansions.M37`) and its own switch.

Roadmap items: 37.14, 37.15, 37.16

## 1. What a player sees

A jobless villager by a Crafter's Workbench takes it and becomes a Crafter, in a canvas apron and a green cap. When a
builder nearby is waiting for something that can be crafted, the Crafter fetches the makings from the builder's chests
and the village's stores, walks to the workbench and makes it one step after another (the line above their head says
"making 24 Oak Planks (step 1 of 3)", then the slabs, then the barrels), and carries the result to the builder's chests;
the build carries on. A big order is made a bagful at a time until there's enough. When something can't be made, the
Crafter takes nothing and says what's short: "short of 2 Oak Log to make 2 Barrel". After the builders, they fill the
Storehouse's stock orders and a Wonder's goods, then what any other worker nearby is waiting for (a miner's ladders).

With the pack's furniture mods (37.15) the Crafter makes their pieces too: a builder short of Handcrafted Oak Chairs
with only oak logs in the chest gets them sawn, cut and crafted (planks, a slab and sticks, then the chairs), and with
CobbleFurnies' Furni Crafter standing in the workshop, CobbleFurnies' pieces as well.

The Crafter's Workshop is in the Blueprint Table: birch boards in a spruce frame under a dark oak roof, the workbench
facing the door between two chests, and an open store lean-to of logs, planks and slabs against its east wall.

Villages find their own Crafter (37.16). The Steward plans a Crafter's Workshop when the village's builders keep running
short of crafted parts (three or more waited for at once: planks, slabs, stairs, doors), and villages grow one now and
then in every style: a board workshop with its plastered gable to the street, a work hatch with a counter beside the
door and a lean-to parts store of boards and logs, its Crafter already at the workbench. With the `crafters` switch off
(or before 2.1) the workshop isn't in the Blueprint Table, the Steward doesn't plan one and villages don't grow it.

## 2. How it works

Each look (every 5 seconds while there's nothing to do) the Crafter goes down the asks in order: builders within the
village whose site is waiting for materials (their missing list, biggest first), the Storehouse's stock orders and a
Wonder's goods (the same search the Carpenter uses, sharing its claims, so a carpenter and a crafter never both make
the same thing), then the requests other workers post. Each is planned with the game's own recipes, so modded recipes
count: the crafting table's and the stonecutter's, and a furniture mod's bench's recipe type while one of its blocks
stands within 6 blocks of the workbench (`crafter_benches` data files). Chains go 3 steps down (a chair from slabs from
planks from logs, with one to spare for sticks). Repeated steps are merged: planks for the barrel and planks for its
slabs are one step.

A bench recipe that doesn't list its materials as ingredients (CobbleFurnies' `cobblefurnies:furni_crafting`, which
takes so many of each item) is read with its counts from the mod's own recipe files (the Furnicrafter's reader, 38.12,
filled by `compat/cobblefurnies`) and planned the same way, by its recipe type id, only while its bench is there. A
`crafter_benches` file whose recipe type isn't registered (its mod isn't installed) is left out when the data loads.

Which pack furniture mods have a bench (read from their jars, 2026-10-21): only CobbleFurnies 1.2 (the Furni Crafter,
348 recipes). Handcrafted 4.0.3 and Beautify 2.0.0 craft every piece on the crafting table, and Lucky's Cozy Home
1.1.20 on the crafting table and the stonecutter, so the Crafter makes theirs with no bench file. Handcrafted's
"benches" are seats, not workstations.

The whole plan is checked against the stock before anything is taken, so a short raw material wastes nothing. To say
what's short, the plan is worked out again noting each raw material (something no recipe of theirs makes) that runs
out, along the first way down the recipes that nothing else could make.

What may be used: from a builder's own chests and their village-mates' stashes everything the rest of that build
doesn't need; from another builder's chests only what their builds can spare (B33). Never a chest outside the village.
The finished goods go into the asker's chests (the builder's, the Storehouse's, the Wonder's stockpile, the worker's).
A bag's worth at a time: at most 12 stacks of makings, the rest of the 27-slot bag for what's made.

The job in hand is saved on the villager after every step, so a chain half made carries on from the same step after a
restart (the planks already made are in the saved bag). If the makings vanish while fetching, what was fetched goes back
where it came from. A step takes 6 ticks a craft (at least a second), faster with the Crafter's level.

The Steward's rule (37.16) is `steward_rules/workplace_crafters_workshop.json`: `builders_short_of_parts` (at least 3)
and no Crafter's Workshop built yet, to build one in the workshops quarter (priority 60, a 2-day cooldown). The new
condition `builders_short_of_parts {at_least}` counts, over every build site near the hall waiting for materials (the
Steward's and the players'), each crafted part a site misses once; a part is anything the crafting table or the
stonecutter makes (logs, cobblestone and glass don't count). It never holds while Crafters are off. The village house
is `village/<style>_crafters_workshop` in the five vanilla house pools (and Repurposed Structures'), weight 2.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| `crafters` | on from 2.1 | A jobless villager takes a free Crafter's Workbench and becomes a Crafter. Off: no villager takes one, and Crafters already hired stand idle (they keep their job); the workshop leaves the Blueprint Table, the Steward's rule and the village houses (37.16; workshops already built stay). |

## 4. Saved data

- `crafter_job` on the villager: the job in hand (who it's for, the chain's steps, what it takes, the phase and the next
  step). None: no job (an older save, or a Crafter with nothing to do).
- The bag (`builder_bag`) and items made (`items_crafted`) are the ones every crafting job already saves.

## 5. Items, blocks, jobs, commands

- Block: `aliveworkplace:crafters_workbench` (crafted from two stonecutters, a crafting table, two planks and a chest;
  mined with an axe).
- Job: `aliveworkplace:crafter`.
- Blueprint: `aliveworkplace:crafters_workshop`; village houses `aliveworkplace:village/<style>_crafters_workshop` (loot
  `chests/village_crafters_workshop`: planks, logs, sticks, slabs, iron nuggets).
- Steward rule: `aliveworkplace:workplace_crafters_workshop`; condition `builders_short_of_parts`.
- Data: `data/<namespace>/crafter_benches/*.json`, each `{"recipe_type": "...", "blocks": ["..."]}`. Shipped:
  `aliveworkplace:cobblefurnies` (`cobblefurnies:furni_crafting` at `cobblefurnies:furnicrafter`).

## 6. Decisions

- 37.14 (owner, 2026-10-20, "the next build out"): a new job that makes what others need, through whole chains.
- Its own job block rather than a vanilla one: the crafting table is the Carpenter's and the stonecutter the Mason's;
  the Crafter does both, so it gets a workbench of its own (and the workshop has neither, so nobody else moves in).
- The vanilla stand-in for "a chair from slabs and planks" is the barrel (six planks and two slabs): vanilla has no
  chair. With Handcrafted installed its chairs are planned the same way from their own recipes.
- A furniture mod's bench is named by recipe type id and block ids in a data file, never its classes.
- 37.15: one bench file shipped, CobbleFurnies' Furni Crafter, the only bench among the pack's furniture mods. A Furni
  Crafter in the workshop is also the Furnicrafter's job block (38.12); a Crafter and a Furnicrafter there may both make
  CobbleFurnies pieces for the builders.
- 37.15: a bench file is dropped when its recipe type isn't registered, rather than kept and never matched, so a
  server without the mod doesn't scan the workshop for its blocks.

- 37.16: "keep running short" is counted as several crafted parts waited for at once (3 by default), not as days in a
  row: a site's missing list isn't saved, and counting days would need a new saved field on the hall. The rule's cooldown
  and the built-count condition keep it from asking twice.
- 37.16: the workshop is hidden from the Blueprint Table while `crafters` is off (not only while M37 is closed, as B119
  does for other expansion builds): a workshop whose job can't start is no use. Other jobs' builds are unchanged.
- 37.16: the village house comes with its Crafter already in the job, like the luxury houses, rather than a jobless
  villager who'd take the workbench by themselves: the same in every village, and the house test can check it.

## 7. Known limits

- The Crafter doesn't sell its workshop's blueprint; the Blueprint Table has it.
- What's short is found along the first way down the recipes: with oak and spruce logs both short it names oak.
- The showcase scene is filmed on GitHub, not in this session.

## 8. Proof

- `CrafterGameTests.theCrafterMakesTheSlabsTheBuilderNeeds`: one step.
- `CrafterGameTests.theCrafterMakesBarrelsFromLogsForTheBuilder`: the three-step chain and the build finishing.
- `CrafterGameTests.theCrafterSaysWhatItLacksAndWastesNothing`: a raw material short.
- `CrafterGameTests.theLackLineNamesEveryShortThingWithACommaBetween`: the "short of ..." line with two things short.
- `CrafterGameTests.beingShortOfJustOneThingIsStillNamed`: short of a single log, and it's named.
- `CrafterGameTests.theCrafterFetchesTheOneLogItNeeds`: a job that needs exactly one of something.
- `CrafterGameTests.theCrafterMakesAnotherWorkersLaddersFromTheStorehouse`: another worker's request, made from the
  Storehouse's chest.
- `CrafterGameTests.oneCrafterServesTwoBuilders`: two askers at once.
- `CrafterGameTests.aChainHalfMadeCarriesOnAfterAReload`: save and reload mid-chain.
- `CrafterGameTests.withTheSwitchOffTheCrafterMakesNothing`: the switch off.
- `CrafterGameTests.aBenchsRecipesOnlyWhileTheBenchIsThere`: a bench's recipes.
- `CrafterWorkshopGameTests.theWorkshopIsOfferedOnlyWhileCraftersAreOn`: in the Blueprint Table, not with the switch off
  or M37 closed.
- `CrafterWorkshopGameTests.theStewardPlansAWorkshopWhenBuildersKeepRunningShortOfParts`: the rule on three or more
  crafted parts short, not on one, on raw materials, with the switch off or a workshop built.
- `CrafterWorkshopGameTests.aSiteAtTheVillagesEdgeCountsOneBeyondItDoesnt`: a site at the village's edge counts, one a
  block further doesn't.
- `CrafterWorkshopGameTests.villagesGrowTheWorkshopOnlyWhileCraftersAreOn` and
  `VillageGameTests.theCraftersWorkshopsCrafterTakesItsBench`: the village house in every style, with its Crafter.
- `CrafterGameTests.theCobbleFurniesBenchIsIgnoredWithoutCobbleFurnies`: the shipped bench file left out without its mod.
- `CrafterFurnitureCompatTests.theCrafterMakesHandcraftedChairsFromLogsForTheBuilder`: Handcrafted chairs from logs for
  a builder's shortfall, and the build finishing with them (compat suite).
- `CrafterFurnitureCompatTests.theFurniCraftersRecipesOnlyWhileItStandsInTheWorkshop`: CobbleFurnies' bench recipes only
  while the Furni Crafter stands in the workshop (compat suite).
- `CrafterFurnitureCompatTests.aBenchFileForAModThatIsntInstalledIsIgnored`: a bench file for an absent mod (compat suite).

Showcase scenes: `crafter_chain`, `crafter_furniture`
