# Candles and leather goods

Milestone 46, item 46.5 (owner, 2026-11-09: "Leatherworker shouldn't make stained glass"; each job crafts its own kind
of thing). The Beekeeper makes the candles, colored candles and honeycomb blocks builders wait for, from her hives' wax,
and the Leatherworker is no longer her stand-in: he makes leather goods and nothing else.
Roadmap items: 46.5, B1148

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
- A builder asks for his blueprint's item frames, paintings and armor stands like its blocks (B1148): from the start
  they are on his "Needs" list, the requests board and the line the jobs read, so the Leatherworker makes the frames,
  the Shepherd the paintings and the Carpenter the armor stands, into the builder's chest. When the last block is in and
  one is still missing, the builder waits by his bench ("1× Item Frame" on his waiting line) while one is spare in the
  village (he fetches it), while a job is making it, or for two minutes, then hangs what he has. With nobody to make it,
  the build ends without it and the owner is told, as before.

## 2. How it works

- B1148: `Builders.remainingNeed` (what the "Needs" list, the requests board, the jobs and the other builders' reserves
  count) includes `Builders.decorationNeed`, the plan's entities not up yet by the item each takes. At the end
  (`BuilderWork.getDecorations`, before `Builders.finish`), a decoration that the bench's chests and the bag don't
  hold is fetched like a block (from the village's spare stock, else waited for by the bench), while one is spare, a job
  is on it (`JobShortages.coveredFor`) or for `BuilderWork.DECORATION_WAIT` (2400 ticks) since the wait began. Crafters
  and the requests board serve a finished build still getting them (`BuildSite.isGettingDecorations`).
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

Nothing new for the jobs: a Beekeeper's job in hand is the `crafter_job` attachment every crafting job uses, and her bag
the `builder_bag`. B1148: a build site saves `decoration_wait_since`, when its finished build began waiting for an item
frame, painting or armor stand (absent in older saves and while not waiting).

## 5. Items, blocks, jobs, commands

- Data: `data/aliveworkplace/tags/item/crafts/beekeeper.json` (`#minecraft:candles`, honeycomb block) and
  `crafts/leatherworker.json` (item frames, glow item frames, lead, leather, leather armor), unchanged since 46.1.
- Text: `message.aliveworkplace.beekeeper_craft.title` ("Beekeeper · %s made for builders"); the Leatherworker's
  `message.aliveworkplace.dyer.title` now reads "Leatherworker · %s made for builders".
- No new blocks, items, jobs or commands.

## 6. Decisions

- No helping pair (`CrafterWork.helps`): the Florist makes dyes for the Shepherd only. A Beekeeper short of a dye names
  it on the line.
- Leather goods are tested through the Storehouse's stock orders, and since B1148 through a builder's blueprint too.
- B1148: the end wait is bounded (two minutes, longer while a job is on it or one is spare), so a painting nobody in
  the village can make never holds a finished house up for good.
- The README job "Leatherworker (dyer)" is now "Leatherworker", and its showcase scene is `leatherworker_goods`.

## 7. Known limits

- Repairs don't wait for a fallen frame: they put it back only from the chests (B1083).
- The showcase scenes `chandler` and `leatherworker_goods` are filmed on GitHub, not in this session.

## 8. Proof

`ChandlerLeatherGameTests`: both lists are their own (no stand-in; the Leatherworker dyes nothing), a candle from
honeycomb and string for a builder, a red candle step by step, a honeycomb block, the missing string named on the line
word for word, a Beekeeper saved and loaded mid-job finishing the candle, her hive harvested alongside a builder's
candle, and with no Beekeeper the Leatherworker leaving the candle alone and the line saying "Hire a Beekeeper to make
Candles"; item frames from leather and sticks, a glow item frame step by step, leather from rabbit hide for a frame,
leads and a leather helmet for stock orders, and with no Leatherworker the store untouched and the line saying "Hire a
Leatherworker to make Item Frames". Scenes `chandler` and `leatherworker_goods`.
`BuilderAsksForFramesGameTests` (B1148): a blueprint's frame made by the Leatherworker from the chest's leather and
sticks and hung; with nobody to make it, the frame counted from the first tick, "1× Item Frame" on the waiting line and
the board, then the build ending without it; and saved and loaded while waiting, the wait kept and the frame hung.
