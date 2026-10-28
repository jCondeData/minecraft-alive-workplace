# The Furnicrafter

With CobbleFurnies installed, a villager by its Furni Crafter becomes the village's Furnicrafter, who makes CobbleFurnies
furniture from the store's materials: the chairs, tables and lamps the builders are waiting for (a furnished home's
pieces, 37.x) and the pieces the storehouse orders. Part of Cobblemon depth (2.2), behind its gate (`Expansions.M38`).
Without CobbleFurnies nothing changes: there is no such job. A Furnicrafter in a world CobbleFurnies was taken off
loads as a jobless Novice with no trades, her name and clothes kept (B542), and takes another job by herself.

Roadmap items: 38.12

## What a player sees

Place CobbleFurnies' Furni Crafter in a village and a jobless villager walks over and takes it: she wears a red bobble
beanie, a walnut leather apron with a Poké Ball stitched on it, a yellow tape measure round her neck and an upholstery
hammer at her belt. When a builder nearby is held up because the build wants a CobbleFurnies piece it doesn't have
(an Oak Chair, a Red Lamp...), the Furnicrafter fetches the materials from the builder's chests and the village's
store, works them at the Furni Crafter and carries the finished piece to the builder's chest, and the builder carries
on. Order a piece at the Storehouse (its stock orders) and she keeps the store stocked with it, from what the store
holds. Over her head: "Furnicrafter · 3 made" and what she's doing, or "no furniture wanted by the builders or the
store". She trades too: planks and wool bought, chairs, lamps, tables, a cabinet, a sofa and a Poké Ball Desk sold.

## How it works

- **The job.** `aliveworkplace:furnicrafter` is registered only when CobbleFurnies is loaded, and its job site
  (`aliveworkplace:furnicrafter`, a POI on `cobblefurnies:furnicrafter`) only once CobbleFurnies registers the block.
  It's an optional entry of the job-site tag, so a jobless villager takes the block by themselves, as with any
  vanilla workstation.
- **The recipes.** CobbleFurnies' own Furni Crafter recipes, every one the server loaded (348 in CobbleFurnies 1.2),
  found by their recipe type `cobblefurnies:furni_crafting`. That type doesn't list its materials the way crafting
  recipes do, so each recipe's file is read again from the data packs (the top pack's, so a data pack's change counts):
  its materials (an item and how many) and the piece it makes. Read once per recipe load; `/reload` reads them again.
- **What she makes.** She works like the Carpenter (the same crafter's shift): first a builder near her Furni Crafter
  whose build is short of a CobbleFurnies piece (since B636 while he builds on, not only once he stands waiting); then a storehouse within 48 blocks with a stock
  order for one that its store is short of. Materials come from what the builder's chests and the village's stashes
  (the store among them) can spare; never what the rest of that build still needs, and never what the storehouse's
  other orders keep.
- **Short materials** are made first with the crafting table's recipes, from what's there, and only when the whole piece can be made (a store one plank short of a chair keeps its planks uncut); what the store already holds is used as it is: an Oak Chair (an oak plank
  and two sticks) from three planks cuts two of them into four sticks, and the two left over go back with the chair.
- **How many.** As many as asked for if the materials are there, or half, down to one (16 Red Lamps ordered and wool
  for 2: she makes 2). One trip carries at most 12 stacks of materials.

## Switches

| Key | Default | What it does |
|---|---|---|
| `furnicrafters` | on (2.2) | With CobbleFurnies, a jobless villager by its Furni Crafter becomes the Furnicrafter, who makes the CobbleFurnies furniture the builders and the store's orders want. Off: no new Furnicrafters (the block is no job), and those hired stand idle. |

## Saved data

Nothing new. The job is a villager profession (saved by the game as `aliveworkplace:furnicrafter`); a Furnicrafter
saved in a world CobbleFurnies was later removed from loads as a jobless villager. The count over her head is the
crafters' existing `items_crafted`. A piece half made when the game stops isn't saved: what she carried goes back to
the chests by her Furni Crafter.

## Items, blocks, jobs, commands

- Job: `aliveworkplace:furnicrafter` (Furnicrafter), at CobbleFurnies' `cobblefurnies:furnicrafter` ("Furni Crafter").
- Job site POI: `aliveworkplace:furnicrafter`.
- Status lines: `message.aliveworkplace.furnicrafter.title`, `message.aliveworkplace.furnicrafter.state.idle`, and the
  crafters' own lines (fetching, making, taking to the builder).
- Trades: oak planks and white wool for emeralds; `cobblefurnies:oak_chair`, `red_lamp`, `oak_table`, `oak_cabinet`,
  `white_sofa` and `poke_ball_desk` for emeralds, by level.

## Decisions

- **Its own job, at CobbleFurnies' block** (38.12, owner 2026-10-20): no new job block of ours; the mod's Furni Crafter
  is the workstation, the way Create's Toolbox is the Mechanic's.
- **Read by data only**: no CobbleFurnies class is touched. The recipes' type id and files are read, the code that does
  it sits in `compat/cobblefurnies/` and fills an extension point (`FurnitureRecipes.SOURCE`), turned on only when
  CobbleFurnies is loaded.
- **Builders and stock orders, not a stockpile of her own**: she makes what someone is waiting for, like the Carpenter,
  so the store's planks aren't turned into furniture nobody asked for.

## Known limits

- Only CobbleFurnies' recipes; another furniture mod's pieces aren't made (they'd need their own filling of the
  extension point).
- A recipe whose materials name something other than a plain item (CobbleFurnies 1.2 has none) is left out, with a line
  in the log.
- Materials are made at most two crafting steps down (planks into sticks; logs into planks into sticks); wool isn't dyed.
- The showcase scene is filmed on GitHub, not yet looked at in game by the owner.

## Proof

- `FurnicrafterCompatTests` (with CobbleFurnies): `FurnicrafterCompatTests.aJoblessVillagerTakesTheFurnicrafter`,
  `FurnicrafterCompatTests.withFurnicraftersOffTheFurniCrafterIsNoJob`,
  `FurnicrafterCompatTests.theRecipesAreReadFromCobbleFurniesFiles`, `FurnicrafterCompatTests.aPieceIsMadeFromTheStoresMaterials`,
  `FurnicrafterCompatTests.aBuilderBuildsTheFurnicraftersPiece`, `FurnicrafterCompatTests.theFurnicrafterTrades`.
- QA: `QaFurnicrafterCompatTests` (a store one plank short makes nothing and cuts no planks, then makes the chair once
  the plank comes; sticks in the store are used as they are; planks another stock order keeps aren't used; 16 Red
  Lamps ordered with wool for 2 makes 2).
- `FurnicrafterGameTests` (without it, nothing changes): `FurnicrafterGameTests.withoutCobbleFurniesThereIsNoFurnicrafter`,
  `FurnicrafterGameTests.aSavedFurnicrafterLoadsJoblessWithoutCobbleFurnies`, `FurnicrafterGameTests.theFurnicraftersSwitchHasItsText`.
- Showcase scene `furnicrafter`: the Furnicrafter makes the Oak Chairs, Red Lamps and Poké Ball Desk a storehouse
  ordered from its store's planks, wool, redstone and concrete.
