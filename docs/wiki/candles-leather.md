# Candles and leather goods

Milestone 46, item 46.5 (owner, 2026-11-09: "Leatherworker shouldn't make stained glass"; each job crafts its own kind
of thing). The Beekeeper makes the candles, colored candles and honeycomb blocks builders wait for, from her hives' wax,
and the Leatherworker is no longer her stand-in: he makes leather goods and nothing else.
Roadmap items: 46.5

## 1. What a player sees

- A builder near a Beekeeper who is short of candles, colored candles or honeycomb blocks gets them from her: she
  fetches the makings from the builder's chests (and her own), makes them at her hive or Apiary and puts them in the
  builder's chest.
  - A candle from a honeycomb and a string.
  - A red candle from a honeycomb, a string and a red dye, step by step: the candle first, then the color.
  - A honeycomb block from four honeycomb.
- Over her head while making something: "Beekeeper · 3 made for builders" and what she's making. Between jobs she keeps
  her hives: harvesting, planting flowers and breeding bees wait while she makes something, and the making waits while
  she is at a hive.
- A missing string is named on the builder's line: "Can't be made yet: short of 1 String to make 1 Candle". A missing
  dye is named as the dye ("short of 1 Red Dye"), as a player hands it over.
- A builder with no Beekeeper nearby reads "Hire a Beekeeper to make Candles"; the Storehouse's board says "Made by:
  the Beekeeper (name)" or "Hire a Beekeeper to make it".
- The Leatherworker makes item frames, glow item frames, leads, leather from rabbit hide and leather armor, for builders
  and the Storehouse's stock orders, and no longer candles. Over his head: "Leatherworker · 3 made for builders" (it
  said "Dyer" before). With no Leatherworker, the line reads "Hire a Leatherworker to make Item Frames".

## 2. How it works

- `craft/BeekeeperCraftWork` is a `CrafterWork` (the crafting table's recipes) in the Beekeeper's work package
  (`bee/BeekeeperPackages`) next to `bee/BeekeeperWork`; it takes what's on her list
  (`data/aliveworkplace/tags/item/crafts/beekeeper.json`, `CraftJobs.Job.BEEKEEPER`, whose stand-in is gone). The two
  share her bag, so each waits while the other is busy (`BeekeeperWork.isBusy`, `CrafterWork.isBusy`; the hives also
  wait while a crafting job is saved on her), or the hive work would put a job's makings away as harvest.
- What's short is planned with every other job's thing counted raw (as the Florist's and Cleric's are), so the line
  names what a player hands over: a dye, not the flower.
- The Leatherworker keeps `craft/DyerWork` (its name and its "dyer" status key stay, so saves and lines don't change),
  which takes only the `crafts/leatherworker` list.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of their own) | | The making is a crafting job's: the builder's line comes with the job chains (Milestone 37's gate). Packs change what each makes with the `crafts/beekeeper` and `crafts/leatherworker` tags. |

## 4. Saved data

Nothing new: a Beekeeper's job in hand is the `crafter_job` attachment every crafting job uses, and her bag the
`builder_bag`.

## 5. Items, blocks, jobs, commands

- Data: `data/aliveworkplace/tags/item/crafts/beekeeper.json` (`#minecraft:candles`, honeycomb block) and
  `crafts/leatherworker.json` (item frames, glow item frames, lead, leather, leather armor), unchanged since 46.1.
- Text: `message.aliveworkplace.beekeeper_craft.title` ("Beekeeper · %s made for builders"); the Leatherworker's
  `message.aliveworkplace.dyer.title` now reads "Leatherworker · %s made for builders".
- No new blocks, items, jobs or commands.

## 6. Decisions

- No helping pair (`CrafterWork.helps`): the Florist makes dyes for the Shepherd only. A Beekeeper short of a dye names
  it on the line.
- Leather goods are tested through the Storehouse's stock orders: a builder never asks a crafter for a blueprint's item
  frames (see Known limits).
- The README job "Leatherworker (dyer)" is now "Leatherworker", and its showcase scene is `leatherworker_goods`.

## 7. Known limits

- A builder's shortage list counts blocks only: a blueprint's item frames go up last from the chests, and with none
  there they are left out, so no Leatherworker is asked for them (filed as a bug).
- The showcase scenes `chandler` and `leatherworker_goods` are filmed on GitHub, not in this session.

## 8. Proof

`ChandlerLeatherGameTests`: both lists are their own (no stand-in; the Leatherworker dyes nothing), a candle from
honeycomb and string for a builder, a red candle step by step, a honeycomb block, the missing string named on the line
word for word, a Beekeeper saved and loaded mid-job finishing the candle, her hive harvested alongside a builder's
candle, and with no Beekeeper the Leatherworker leaving the candle alone and the line saying "Hire a Beekeeper to make
Candles"; item frames from leather and sticks, a glow item frame step by step, leather from rabbit hide for a frame,
leads and a leather helmet for stock orders, and with no Leatherworker the store untouched and the line saying "Hire a
Leatherworker to make Item Frames". Scenes `chandler` and `leatherworker_goods`.
